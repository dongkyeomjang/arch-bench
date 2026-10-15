#!/usr/bin/env python3
"""세션 매트릭스 실행: levels × models × domains × reps 를 시드 고정 무작위 순서로 run_session.py 에 넘긴다.

사용:
  run_matrix.py --config matrix.json --ws-root $ARCH_WS_ROOT --out $OUT_ROOT [--dry-run] [--resume]

종료 코드: 0 전 세션 completed, 1 실패 세션 있음, 2 설정 오류, 3 비용 가드로 멈춤,
          4 결과 디렉터리가 이미 있음(--resume 없이), 5 시작 전 검사 실패(run_session 과 같은 검사,
          agent=claude 면 workspace-index.jsonl 에 없는 워크스페이스 잔여물)
계약 전문은 experiment/README.md "매트릭스 실행기" 절.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import random
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_session as rs  # noqa: E402 — 같은 디렉터리의 러너 (precheck·write_json 재사용)

RUN_SESSION = Path(__file__).resolve().parent / "run_session.py"
DEFAULT_SEED = 20261215
DEFAULTS = {"reps": 1, "agent": "claude", "parallel": 2, "max_cost_usd": None, "shuffle_seed": DEFAULT_SEED,
            "est_cost_per_session_usd": {}}
REQUIRED = ("levels", "models", "domains")


def load_config(path: Path) -> dict:
    cfg = {**DEFAULTS, **json.loads(path.read_text())}
    missing = [k for k in REQUIRED if not cfg.get(k)]
    if missing:
        raise ValueError(f"config needs non-empty {missing}")
    bad = [lv for lv in cfg["levels"] if lv not in rs.LEVELS]
    if bad:
        raise ValueError(f"unknown levels {bad}")
    if cfg["agent"] not in ("claude", "fake"):
        raise ValueError(f"agent must be claude|fake, got {cfg['agent']!r}")
    if int(cfg["parallel"]) < 1 or int(cfg["reps"]) < 1:
        raise ValueError("parallel and reps must be >= 1")
    return cfg


def expand(cfg: dict) -> list[dict]:
    """조합 전개 후 시드 고정 셔플. 정렬된 목록에서 섞으므로 config 의 나열 순서와 무관하게 같은 집합 → 같은 순서."""
    sessions = [{"level": lv, "model": m, "domain": d, "rep": r,
                 "session_id": f"{d}-{lv}-{m}-r{r}"}  # run_session.py 의 session_id 규칙과 같아야 한다
                for d in cfg["domains"] for lv in cfg["levels"] for m in cfg["models"]
                for r in range(1, int(cfg["reps"]) + 1)]
    sessions.sort(key=lambda s: s["session_id"])
    random.Random(int(cfg["shuffle_seed"])).shuffle(sessions)
    return sessions


def read_session_json(path: Path) -> dict | None:
    try:
        return json.loads(path.read_text())
    except (OSError, json.JSONDecodeError):
        return None


class Matrix:
    def __init__(self, cfg: dict, sessions: list[dict], ws_root: Path, out_root: Path):
        self.cfg, self.ws_root, self.out_root = cfg, ws_root, out_root
        self.sessions = sessions
        self.state = {s["session_id"]: {"session_id": s["session_id"], "status": "pending", "cost_usd": None,
                                        "wall_s": None, "exit_code": None} for s in sessions}
        self.queue = list(sessions)
        self.lock = threading.Lock()
        self.stop_reason: str | None = None
        self.started_at = rs.now_iso()
        self.t0 = time.monotonic()
        self.durations: list[float] = []  # 이번 실행에서 끝난 세션의 wall 시간 (남은 시간 추정용)

    # ------------------------------------------------------------ 재개
    def prepare_resume(self) -> None:
        """completed 는 건너뛰고, 그 밖의 흔적(failed·running·session.json 없음)은 _failed-<ts> 로 옮겨 다시 돌린다."""
        ts = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
        for s in list(self.queue):
            sid = s["session_id"]
            out = self.out_root / sid
            if not out.exists():
                continue
            data = read_session_json(out / "session.json")
            if data and data.get("status") == "completed":
                self.state[sid].update(status="skipped", cost_usd=data.get("cost_usd"), wall_s=data.get("wall_s"),
                                       exit_code=data.get("exit_code"))
                self.queue.remove(s)
                continue
            ws = Path(data["workspace"]) if data and data.get("workspace") else None
            if ws is not None and ws.parent.parent == self.ws_root and ws.parent.exists():
                ws.parent.rename(ws.parent.with_name(f"{ws.parent.name}_failed-{ts}"))  # <token>_failed-<ts>
            out.rename(out.with_name(f"{sid}_failed-{ts}"))
            self.state[sid]["previous_attempt"] = f"{sid}_failed-{ts}"

    # ------------------------------------------------------------ 실행
    def cost_total(self) -> float:
        return round(sum(v["cost_usd"] or 0.0 for v in self.state.values()), 6)

    def write_status(self) -> None:
        counts = {k: 0 for k in ("pending", "running", "completed", "skipped", "failed")}
        for v in self.state.values():
            counts[v["status"]] += 1
        elapsed = round(time.monotonic() - self.t0, 1)
        avg = round(sum(self.durations) / len(self.durations), 1) if self.durations else None
        remaining = counts["pending"] + counts["running"]
        par = int(self.cfg["parallel"])
        eta = None
        if avg is not None and not self.stop_reason:
            eta = round(avg * (counts["pending"] + counts["running"] / 2) / par, 1) if remaining else 0.0
        status = {
            "state": ("stopped" if self.stop_reason and not counts["running"] else
                      "stopping" if self.stop_reason else
                      "finished" if not remaining else "running"),
            "stop_reason": self.stop_reason,
            "total": len(self.sessions), "done": counts["completed"] + counts["skipped"], **counts,
            "cost_usd": self.cost_total(), "max_cost_usd": self.cfg["max_cost_usd"],
            "started_at": self.started_at, "updated_at": rs.now_iso(), "elapsed_s": elapsed,
            "avg_session_s": avg, "eta_s": eta, "parallel": par, "agent": self.cfg["agent"],
            "shuffle_seed": self.cfg["shuffle_seed"],
            "sessions": [self.state[s["session_id"]] for s in self.sessions],
        }
        tmp = self.out_root / "matrix-status.json.tmp"
        rs.write_json(tmp, status)
        tmp.replace(self.out_root / "matrix-status.json")

    def run_one(self, s: dict) -> None:
        sid = s["session_id"]
        log_tmp = self.out_root / ".matrix-logs" / f"{sid}.log"  # run_session 은 결과 디렉터리가 있으면 거부하므로 끝나고 옮긴다
        cmd = [sys.executable, str(RUN_SESSION), "--level", s["level"], "--model", s["model"], "--domain", s["domain"],
               "--rep", str(s["rep"]), "--ws-root", str(self.ws_root), "--out", str(self.out_root),
               "--agent", self.cfg["agent"]]
        t0 = time.monotonic()
        with log_tmp.open("w") as log:
            log.write(f"$ {' '.join(cmd)}\n")
            log.flush()
            rc = subprocess.run(cmd, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL).returncode
        wall = round(time.monotonic() - t0, 1)
        out = self.out_root / sid
        if out.is_dir():
            shutil.move(log_tmp, out / "runner.log")
        data = read_session_json(out / "session.json") or {}
        with self.lock:
            st = self.state[sid]
            st.update(status="completed" if rc == 0 and data.get("status") == "completed" else "failed",
                      exit_code=rc, wall_s=wall, cost_usd=data.get("cost_usd"),
                      workspace_token=data.get("workspace_token"))
            if st["status"] == "completed":
                self.durations.append(wall)
            limit = self.cfg["max_cost_usd"]
            if limit is not None and not self.stop_reason and self.cost_total() > float(limit):
                self.stop_reason = (f"cost guard: cumulative cost_usd {self.cost_total()} > max_cost_usd {limit} "
                                    f"after {sid}; no new sessions started")
            self.write_status()
        print(f"[matrix] {sid}: {self.state[sid]['status']} rc={rc} {wall}s cost={data.get('cost_usd')} "
              f"total={self.cost_total()}", flush=True)

    def worker(self) -> None:
        while True:
            with self.lock:
                if self.stop_reason or not self.queue:
                    return
                s = self.queue.pop(0)
                self.state[s["session_id"]]["status"] = "running"
                self.write_status()
            try:
                self.run_one(s)
            except Exception as e:  # noqa: BLE001 — 한 세션의 러너 오류로 매트릭스 전체를 죽이지 않는다
                with self.lock:
                    self.state[s["session_id"]].update(status="failed", error=f"{type(e).__name__}: {e}")
                    self.write_status()

    def run(self) -> int:
        (self.out_root / ".matrix-logs").mkdir(exist_ok=True)
        limit = self.cfg["max_cost_usd"]
        if limit is not None and self.cost_total() > float(limit):  # 재개 시 이미 쓴 비용
            self.stop_reason = f"cost guard: cost_usd of skipped sessions {self.cost_total()} > max_cost_usd {limit}"
        self.write_status()
        threads = [threading.Thread(target=self.worker, daemon=True) for _ in range(int(self.cfg["parallel"]))]
        for t in threads:
            t.start()
        try:
            for t in threads:
                t.join()
        except KeyboardInterrupt:  # 자식 run_session 도 같은 프로세스 그룹이라 SIGINT 를 받아 failed 로 끝난다
            with self.lock:
                self.stop_reason = self.stop_reason or "interrupted"
            for t in threads:
                t.join()
        with self.lock:
            self.write_status()
        shutil.rmtree(self.out_root / ".matrix-logs", ignore_errors=True)
        if self.stop_reason:
            print(f"[matrix] stopped: {self.stop_reason}", file=sys.stderr)
            return 3 if self.stop_reason.startswith("cost guard") else 1
        return 1 if any(v["status"] == "failed" for v in self.state.values()) else 0


def precheck_ws_leftovers(ws_root: Path, out_root: Path) -> None:
    """ws_root 의 디렉터리는 모두 이 결과 루트의 workspace-index.jsonl 에 있는 토큰이어야 한다(`<token>_failed-<ts>` 포함).
    다른 실행·옛 형식(조건이 든 이름)의 잔여물은 에이전트가 ls ../.. 로 볼 수 있으므로 본 실행 전에 막는다."""
    index = out_root / "workspace-index.jsonl"
    known = set()
    if index.exists():
        for line in index.read_text().splitlines():
            try:
                known.add(json.loads(line)["token"])
            except (json.JSONDecodeError, KeyError, TypeError):
                raise rs.PrecheckError(f"{index} has a malformed line: {line[:80]!r}") from None
    stray = sorted(d.name for d in ws_root.iterdir() if d.is_dir() and d.name.split("_failed-")[0] not in known)
    if stray:
        raise rs.PrecheckError(f"{len(stray)} dirs in {ws_root} are not in {index} (e.g. {', '.join(stray[:5])}); "
                               f"empty --ws-root or use a new one")


def dry_run(cfg: dict, sessions: list[dict]) -> None:
    est = cfg.get("est_cost_per_session_usd") or {}
    total = 0.0
    unknown = set()
    print(f"# {len(sessions)} sessions, shuffle_seed={cfg['shuffle_seed']}, agent={cfg['agent']}, parallel={cfg['parallel']}")
    for i, s in enumerate(sessions, 1):
        c = est.get(s["model"])  # agent=fake 여도 추정치를 그대로 보인다(실제 비용은 0)
        if c is None:
            unknown.add(s["model"])
        else:
            total += float(c)
        print(f"{i:4d}  {s['session_id']:<32} est_usd={'?' if c is None else c}")
    note = f" (no estimate for {sorted(unknown)})" if unknown else ""
    fake = " (agent=fake: actual cost is 0)" if cfg["agent"] == "fake" else ""
    print(f"# estimated cost: ${total:.2f}{note}{fake}; max_cost_usd={cfg['max_cost_usd']}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--config", required=True, type=Path)
    ap.add_argument("--ws-root", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--dry-run", action="store_true", help="전개된 세션 목록·순서·예상 비용만 출력")
    ap.add_argument("--resume", action="store_true", help="completed 세션은 건너뛰고 나머지는 _failed-<ts> 로 옮겨 다시 실행")
    args = ap.parse_args()
    try:
        cfg = load_config(args.config)
    except (OSError, ValueError, json.JSONDecodeError) as e:
        print(f"run_matrix: bad config: {e}", file=sys.stderr)
        return 2
    sessions = expand(cfg)
    if args.dry_run:
        dry_run(cfg, sessions)
        return 0

    args.ws_root.mkdir(parents=True, exist_ok=True)
    ws_root = args.ws_root.resolve()
    out_root = args.out.expanduser().resolve()
    try:  # 세션마다 run_session 이 다시 검사하지만, 매트릭스를 시작하기 전에 한 번 막는다
        rs.precheck_ws_root(ws_root)
        rs.precheck_config_dir()
        if cfg["agent"] == "claude":
            if shutil.which("claude") is None:
                raise rs.PrecheckError("claude CLI not found on PATH")
            precheck_ws_leftovers(ws_root, out_root)
    except rs.PrecheckError as e:
        print(f"run_matrix: precheck failed: {e}", file=sys.stderr)
        return 5
    out_root.mkdir(parents=True, exist_ok=True)
    existing = [s["session_id"] for s in sessions if (out_root / s["session_id"]).exists()]
    if existing and not args.resume:
        print(f"run_matrix: {len(existing)} session dirs already exist in {out_root} (e.g. {existing[0]}); "
              f"use --resume", file=sys.stderr)
        return 4
    m = Matrix(cfg, sessions, ws_root, out_root)
    if args.resume:
        m.prepare_resume()
    return m.run()


if __name__ == "__main__":
    sys.exit(main())
