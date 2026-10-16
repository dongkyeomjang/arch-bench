#!/usr/bin/env python3
"""LLM 보조 의미론 채점: 익명 사본의 src/main/java 전체와 루브릭을 주고 D1~D4(각 0~3) 점수와 근거를 JSON 으로 받는다.

사용:
  rate_semantic.py --repo <anonymized-dir> --rubric experiment/rubric/rubric-v1.md --model opus --out <score.json>
                   [--runs 3] [--effort medium] [--max-cost-usd 1.0]

- 구독 `claude -p` 로 부른다(러너와 같은 CLAUDE_CONFIG_DIR). 도구 없음(`--tools ""`), 기본 시스템 프롬프트를 짧은 평가자 지시로
  바꾼다(`--system-prompt`, Claude Code 의 코딩 지침이 채점 기준에 섞이지 않게). 출력은 `--json-schema` 로 고정한다.
- 같은 입력을 --runs 번 따로 부른다(대화 공유 없음). 차원별 평균·범위·분산을 score_variance 로 남긴다.
- temperature·seed: claude CLI 2.1.301 에는 둘 다 지정하는 옵션이 없다. 프롬프트에 적어도 샘플링에 영향이 없으므로 쓰지 않고,
  대신 반복 채점의 분산으로 안정성을 잰다(README "의미론 채점").
- 스키마 검증 실패(structured_output 없음·범위 밖)는 그 회차만 1회 다시 부른다.
- 비용은 claude 가 보고한 total_cost_usd(API 단가 환산 추정, 실제 청구 아님). 누적이 --max-cost-usd 를 넘으면 남은 회차를 부르지 않는다.
종료 코드: 0 성공(모든 회차 유효), 1 일부 회차 실패, 2 인자 오류, 3 비용 상한으로 중단
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import statistics
import subprocess
import sys
import tempfile
import time
from pathlib import Path

DIMS = ("D1", "D2", "D3", "D4")
MAX_EVIDENCE = 2  # 차원당 근거 수 상한(출력 토큰 = 채점 비용의 큰 몫)
SYSTEM_PROMPT = ("너는 코드 평가자다. 사용자가 준 루브릭의 정의와 점수 기준만으로 코드를 채점한다. "
                 "루브릭에 없는 기준을 더하지 않는다. 근거는 주어진 코드에서 파일·클래스·메서드를 들어 인용한다. "
                 "지정된 JSON 형식으로만 답한다.")
EVIDENCE = {"type": "object", "additionalProperties": False,
            "required": ["file", "symbol", "quote", "reason"],
            "properties": {"file": {"type": "string"}, "symbol": {"type": "string"},
                           "quote": {"type": "string"}, "reason": {"type": "string"}}}
DIM_SCHEMA = {"type": "object", "additionalProperties": False, "required": ["score", "rationale", "evidence"],
              "properties": {"score": {"type": "integer", "minimum": 0, "maximum": 3},
                             "rationale": {"type": "string"},
                             "evidence": {"type": "array", "items": EVIDENCE, "maxItems": MAX_EVIDENCE}}}
SCHEMA = {"type": "object", "additionalProperties": False, "required": list(DIMS) + ["ambiguities"],
          "properties": {**{d: DIM_SCHEMA for d in DIMS},
                         "ambiguities": {"type": "array", "items": {"type": "string"}}}}


def sha256(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def collect_code(repo: Path) -> tuple[str, int]:
    root = repo / "src" / "main" / "java"
    files = sorted(root.rglob("*.java"))
    if not files:
        raise FileNotFoundError(f"no *.java under {root}")
    parts = [f"### FILE: src/main/java/{f.relative_to(root)}\n```java\n{f.read_text(errors='replace').rstrip()}\n```"
             for f in files]
    return "\n\n".join(parts), len(files)


def build_prompt(rubric: str, code: str) -> str:
    return (
        "아래 루브릭으로 이 저장소의 `src/main/java` 전체를 채점하라.\n\n"
        "## 출력 지침\n"
        "- D1~D4 각각 0~3 정수 점수(`score`), 그 점수를 준 이유(`rationale`, 3문장 이내), 근거 목록(`evidence`)을 준다.\n"
        f"- 근거는 차원당 최대 {MAX_EVIDENCE}개, 점수를 정한 데 가장 중요한 것부터 적는다. 근거마다 파일 경로(`file`), 클래스·메서드(`symbol`), 코드 인용(`quote`, 3줄 이내 그대로), 그 인용이 루브릭의 어느 증상·기준에 해당하는지(`reason`)를 적는다.\n"
        "- 점수가 0 이면 0 기준을 충족한다고 본 대표 근거를 적는다.\n"
        "- 루브릭 문구만으로 점수를 정하기 어려웠던 지점은 `ambiguities` 에 문장으로 적는다(없으면 빈 배열).\n\n"
        "## 루브릭\n\n" + rubric.strip() + "\n\n"
        "## 채점할 코드\n\n" + code + "\n"
    )


def call_claude(prompt: str, model: str, effort: str | None, timeout: int = 1800) -> dict:
    cmd = ["claude", "-p", prompt, "--model", model, "--output-format", "json", "--tools", "",
           "--system-prompt", SYSTEM_PROMPT, "--json-schema", json.dumps(SCHEMA, ensure_ascii=False)]
    if effort:
        cmd += ["--effort", effort]
    env = {k: v for k, v in os.environ.items() if not k.startswith("CLAUDE_CODE_") and k != "CLAUDECODE"}
    env["DISABLE_AUTOUPDATER"] = "1"
    t0 = time.monotonic()
    with tempfile.TemporaryDirectory(prefix="rate-sem.") as cwd:  # 빈 cwd: 프로젝트 CLAUDE.md·설정이 섞이지 않게
        p = subprocess.run(cmd, cwd=cwd, env=env, capture_output=True, text=True, timeout=timeout)
    wall = round(time.monotonic() - t0, 1)
    try:
        out = json.loads(p.stdout)
    except json.JSONDecodeError:
        return {"ok": False, "error": f"exit {p.returncode}: non-JSON stdout {p.stdout[:300]!r} {p.stderr[:300]!r}",
                "wall_s": wall, "cost_usd": 0.0}
    so = out.get("structured_output")
    ok = isinstance(so, dict) and all(isinstance((so.get(d) or {}).get("score"), int)
                                      and 0 <= so[d]["score"] <= 3 for d in DIMS)
    usage = out.get("modelUsage") or {}
    return {"ok": ok and not out.get("is_error"), "error": None if ok else f"invalid structured_output: {str(so)[:300]}",
            "result": so, "cost_usd": float(out.get("total_cost_usd") or 0.0), "wall_s": wall,
            "model_id": next(iter(usage), None), "usage": usage, "session_id": out.get("session_id")}


def summarize(runs: list[dict]) -> dict:
    ok = [r for r in runs if r["ok"]]
    summ = {}
    for d in DIMS + ("total",):
        xs = [sum(r["result"][k]["score"] for k in DIMS) if d == "total" else r["result"][d]["score"] for r in ok]
        summ[d] = {"scores": xs, "mean": round(statistics.mean(xs), 3) if xs else None,
                   "min": min(xs) if xs else None, "max": max(xs) if xs else None,
                   "variance": round(statistics.pvariance(xs), 4) if xs else None}
    return summ


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--repo", required=True, type=Path)
    ap.add_argument("--rubric", required=True, type=Path)
    ap.add_argument("--model", required=True, help="claude --model 에 그대로 넘긴다(작업 지시 10: opus. 실험이 세 모델을 다 쓰므로 Opus 세션은 자기 채점이 된다)")
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--effort", default="medium", help="claude --effort (회차 간 같게 고정)")
    ap.add_argument("--max-cost-usd", type=float, default=1.0, help="이 채점(전 회차)의 비용 상한, API 단가 환산")
    args = ap.parse_args()
    if not os.environ.get("CLAUDE_CONFIG_DIR"):
        print("rate_semantic: CLAUDE_CONFIG_DIR is not set (use the runner's config dir)", file=sys.stderr)
        return 2
    if any(os.environ.get(k) for k in ("ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN")):
        print("rate_semantic: API key env is set; refusing (subscription only)", file=sys.stderr)
        return 2
    rubric = args.rubric.read_text()
    code, n_files = collect_code(args.repo)
    prompt = build_prompt(rubric, code)
    cli = subprocess.run(["claude", "--version"], capture_output=True, text=True).stdout.strip()
    record = {"repo": str(args.repo), "rubric": str(args.rubric), "rubric_sha256": sha256(rubric),
              "code_sha256": sha256(code), "prompt_sha256": sha256(prompt), "files": n_files,
              "prompt_chars": len(prompt), "model": args.model, "effort": args.effort, "claude_code_version": cli,
              "temperature": None, "seed": None, "sampling_note": "claude CLI exposes no temperature/seed option",
              "started_at": dt.datetime.now().astimezone().isoformat(timespec="seconds"), "runs": []}
    total_cost, rc = 0.0, 0
    for i in range(1, args.runs + 1):
        attempts = []
        for attempt in (1, 2):  # 스키마 실패만 1회 재시도
            if total_cost > args.max_cost_usd:
                break
            r = call_claude(prompt, args.model, args.effort)
            total_cost += r["cost_usd"]
            attempts.append({k: r.get(k) for k in ("ok", "error", "cost_usd", "wall_s", "model_id", "session_id")})
            if r["ok"]:
                break
        if not attempts:
            rc = 3
            break
        record["runs"].append({"run": i, "ok": r["ok"], "result": r.get("result") if r["ok"] else None,
                               "attempts": attempts, "cost_usd": round(sum(a["cost_usd"] for a in attempts), 6),
                               "model_id": r.get("model_id"), "usage": r.get("usage")})
        print(f"[rate] {args.repo.name} run {i}: ok={r['ok']} "
              + (" ".join(f"{d}={r['result'][d]['score']}" for d in DIMS) if r["ok"] else str(r["error"])[:120])
              + f" cost={round(r['cost_usd'], 4)} total={round(total_cost, 4)}", flush=True)
        if not r["ok"]:
            rc = rc or 1
    record["cost_usd"] = round(total_cost, 6)
    record["score_variance"] = summarize(record["runs"])
    record["ended_at"] = dt.datetime.now().astimezone().isoformat(timespec="seconds")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n")
    if total_cost > args.max_cost_usd and len(record["runs"]) < args.runs:
        rc = 3
    return rc


if __name__ == "__main__":
    sys.exit(main())
