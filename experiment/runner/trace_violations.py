#!/usr/bin/env python3
"""침식 시드 세션의 위반 추적: 턴별 arch.json 을 기준선과 비교해 내장 위반 E1~E5 의 운명과 새 위반을 분류한다.

사용:
  trace_violations.py --baseline experiment/seed-template/eroded/baseline.json --session $OUT/<session-id> \\
                      [--out <session>/violations-trace.json] [--workspace <dir>]

분류
- 기준선 원인 클래스(주 지표 `violating_classes_list`)마다: 마지막 턴에도 목록에 있으면 `persisted`, 없으면 `healed`.
  처음 사라진 턴(`healed_at`)과, 사라졌다가 다시 나타났는지(`reappeared`)를 남긴다.
- 기준선에 없던 원인 클래스가 어느 턴에든 나타나면 `new`.
  그 클래스의 (규칙, 계층) 쌍 중 하나라도 기준선의 (규칙, 계층) 쌍과 같으면 `spread`(같은 유형의 위반이 다른 클래스로 번짐,
  예: 다른 도메인 클래스에도 @Entity → (R1, domain)), 아니면 `novel`. 규칙은 주 지표 규칙 + R5(어노테이션) 를 본다.
  계층 = base 아래 첫 패키지(domain·application.port·application.service·adapter.in·adapter.out, 그 밖의 최상위는 `toplevel`).
- 내장 위반 E2~E5: 기준선의 `embedded.<E>.classes` 가 그 위반의 규칙(`rules`) 목록에 남아 있는지로 턴마다 판정.
- E1(구조 규칙에 안 잡힘): 턴 끝 커밋의 `application/service/LoanBookService.java` 에 `getStatus() ==` 가 있는지
  `git show <head_after>:<path>` 로 본다(파일이 없으면 없음). 조잡한 탐침이다.
- arch.json 이 없는 턴(채점 실패·컴파일 실패)은 `unknown` 으로 두고 운명 판정에서 건너뛴다.
종료 코드: 0 성공, 2 입력 오류
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

BASE = "com.example.lending"
TRACE_RULES = ("R1", "R2", "R3", "R5", "R6", "R7")  # 주 지표(R1·R2·R3·R6·R7) + R5. R4 는 순환 양끝을 다 잡아 과대 집계라 뺀다
E1_PATH = "src/main/java/com/example/lending/application/service/LoanBookService.java"
E1_PATTERN = "getStatus() =="


def layer(fqn: str) -> str:
    rest = fqn[len(BASE) + 1:] if fqn.startswith(BASE + ".") else fqn
    parts = rest.split(".")
    if len(parts) == 1:
        return "root"
    if parts[0] in ("application", "adapter") and len(parts) > 2:
        return f"{parts[0]}.{parts[1]}"
    return parts[0] if parts[0] in ("domain", "application", "adapter") else "toplevel"


def rules_of(arch: dict, cls: str) -> list[str]:
    return [r for r in TRACE_RULES if cls in ((arch.get("by_rule") or {}).get(r) or {}).get("classes", [])]


def patterns(arch: dict, classes) -> set[tuple[str, str]]:
    return {(r, layer(c)) for c in classes for r in rules_of(arch, c)}


def e1_present(ws: Path | None, head: str | None) -> bool | None:
    if ws is None or not head or not (ws / ".git").exists():
        return None
    p = subprocess.run(["git", "show", f"{head}:{E1_PATH}"], cwd=ws, capture_output=True, text=True)
    return E1_PATTERN in p.stdout if p.returncode == 0 else False


def fate(series: list[tuple[int, bool | None]]) -> dict:
    """series: (턴, 있음?) — None 은 모름. 기준선(턴 0)에는 있다고 본다."""
    known = [(t, v) for t, v in series if v is not None]
    if not known:
        return {"status": "unknown", "healed_at": None, "reappeared": False}
    healed_at = next((t for t, v in known if not v), None)
    reappeared = healed_at is not None and any(v for t, v in known if t > healed_at)
    final = known[-1][1]
    # healed_at: 그 뒤로 끝까지 계속 없는 첫 턴 (중간에 사라졌다 다시 나타난 경우는 first_absent 와 다르다)
    healed_for_good = None if final else next(t for t, v in known if not v and all(not w for u, w in known if u >= t))
    return {"status": "persisted" if final else "healed", "healed_at": healed_for_good,
            "first_absent": healed_at, "reappeared": reappeared}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--baseline", required=True, type=Path)
    ap.add_argument("--session", required=True, type=Path)
    ap.add_argument("--out", type=Path, default=None, help="기본: <session>/violations-trace.json")
    ap.add_argument("--workspace", type=Path, default=None, help="E1 판정용 워크스페이스(기본: session.json 의 workspace)")
    args = ap.parse_args()
    base = json.loads(args.baseline.read_text())
    sj = args.session / "session.json"
    if not sj.exists():
        print(f"trace_violations: {sj} not found", file=sys.stderr)
        return 2
    session = json.loads(sj.read_text())
    ws = args.workspace or (Path(session["workspace"]) if session.get("workspace") else None)
    if ws is not None and not ws.exists():
        ws = None
    base_classes = list(base["violating_classes_list"])
    base_patterns = patterns(base, set(base_classes) | {c for r in TRACE_RULES for c in base["by_rule"].get(r, {}).get("classes", [])})

    turns = []
    for tj in sorted(args.session.glob("turn-*/turn.json")):
        t = json.loads(tj.read_text())
        aj = tj.parent / "arch.json"
        arch = json.loads(aj.read_text()) if aj.exists() and t.get("grade_exit") == 0 else None
        turns.append({"turn": t["turn"], "arch": arch, "head_after": t.get("head_after")})

    # 기준선 클래스
    classes_out = {}
    for c in base_classes:
        series = [(x["turn"], None if x["arch"] is None else c in x["arch"]["violating_classes_list"]) for x in turns]
        classes_out[c] = {"rules": rules_of(base, c), **fate(series)}

    # 내장 위반 E1~E5
    embedded = {}
    for e, spec in base["embedded"].items():
        if e == "E1":
            series = [(x["turn"], e1_present(ws, x["head_after"])) for x in turns]
        else:
            series = [(x["turn"], None if x["arch"] is None else
                       any(c in (x["arch"]["by_rule"].get(r) or {}).get("classes", []) for r in spec["rules"] for c in spec["classes"]))
                      for x in turns]
        embedded[e] = {"rules": spec["rules"], "classes": spec["classes"], **fate(series),
                       "by_turn": {t: v for t, v in series}}

    # 새 위반
    new = {}
    for x in turns:
        if x["arch"] is None:
            continue
        for c in x["arch"]["violating_classes_list"]:
            if c in base_classes:
                continue
            pats = patterns(x["arch"], [c])
            kind = "spread" if pats & base_patterns else "novel"
            entry = new.setdefault(c, {"kind": kind, "first_turn": x["turn"], "rules": sorted({r for r, _ in pats}),
                                       "layer": layer(c), "turns": []})
            entry["turns"].append(x["turn"])
            if kind == "spread":
                entry["kind"] = "spread"
    last_known = next((x for x in reversed(turns) if x["arch"] is not None), None)
    final_list = last_known["arch"]["violating_classes_list"] if last_known else None
    for c, v in new.items():
        v["at_final"] = final_list is not None and c in final_list

    summary = {
        "turns": len(turns), "unknown_turns": [x["turn"] for x in turns if x["arch"] is None],
        "baseline_violating_classes": base["violating_classes"],
        "final_violating_classes": None if last_known is None else last_known["arch"]["violating_classes"],
        "persisted_count": sum(v["status"] == "persisted" for v in classes_out.values()),
        "healed_count": sum(v["status"] == "healed" for v in classes_out.values()),
        "spread_count": sum(v["kind"] == "spread" for v in new.values()),
        "novel_count": sum(v["kind"] == "novel" for v in new.values()),
        "spread_at_final": sum(v["kind"] == "spread" and v["at_final"] for v in new.values()),
        "novel_at_final": sum(v["kind"] == "novel" and v["at_final"] for v in new.values()),
        "embedded_persisted": sorted(e for e, v in embedded.items() if v["status"] == "persisted"),
        "embedded_healed": sorted(e for e, v in embedded.items() if v["status"] == "healed"),
        "e1_persisted": embedded.get("E1", {}).get("status") == "persisted" if embedded.get("E1", {}).get("status") != "unknown" else None,
    }
    if summary["final_violating_classes"] is not None:
        summary["delta"] = summary["final_violating_classes"] - base["violating_classes"]
    out = {"session_id": session["session_id"], "seed": session.get("seed"), "baseline": str(args.baseline),
           "baseline_seed_commit": base.get("seed_commit"), "session_seed_commit": session.get("seed_commit"),
           "workspace_used_for_e1": str(ws) if ws else None, "summary": summary,
           "baseline_classes": classes_out, "embedded": embedded, "new_classes": new}
    if base.get("seed_commit") and session.get("seed_commit") and base["seed_commit"] != session["seed_commit"]:
        out["warning"] = "session seed_commit differs from baseline seed_commit"
    dest = args.out or (args.session / "violations-trace.json")
    dest.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
    print(f"{session['session_id']}: " + " ".join(f"{k}={summary[k]}" for k in
          ("persisted_count", "healed_count", "spread_count", "novel_count", "delta", "embedded_persisted", "embedded_healed")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
