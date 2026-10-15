#!/usr/bin/env python3
"""매트릭스 결과 요약: $OUT_ROOT/*/session.json 과 turn-NN/turn.json 을 읽어 마크다운 표를 만든다.

사용:
  summarize.py --out $OUT_ROOT --report experiment/runs/pilot-report.md [--stdout]

보고서 파일이 이미 있으면 `<!-- summarize:begin -->` ~ `<!-- summarize:end -->` 사이만 바꾼다
(그 밖의 사람이 쓴 절은 그대로 둔다). 없으면 표만 담은 새 파일을 만든다. 표준 라이브러리만 쓴다.
"""
from __future__ import annotations

import argparse
import json
import statistics
import sys
from pathlib import Path

TRAP_TURNS = (5, 8, 10, 12, 14)  # 함정 턴 (docs/tasks/TASK7.md). 에이전트에게 보이지 않는 정보
RULES = ("R1", "R2", "R3", "R4", "R5", "R6", "R7")
REFERENCE_RULES = ("R4", "R5")  # 주 지표 밖, 참고 열
BEGIN, END = "<!-- summarize:begin -->", "<!-- summarize:end -->"


def load(out_root: Path) -> list[dict]:
    sessions = []
    for sj in sorted(out_root.glob("*/session.json")):
        if "_failed-" in sj.parent.name:  # --resume 으로 옮겨 둔 이전 시도는 집계에서 뺀다
            continue
        s = json.loads(sj.read_text())
        turns = []
        for tj in sorted(sj.parent.glob("turn-*/turn.json")):
            turns.append(json.loads(tj.read_text()))
        s["_turns"] = turns
        s["_dir"] = sj.parent
        sessions.append(s)
    sessions.sort(key=lambda s: (s["level"], s["model"], s["rep"]))
    return sessions


def short(s: dict) -> str:
    return f"{s['level']}-r{s['rep']}"


def fmt(v, nd=2) -> str:
    if v is None:
        return "–"
    if isinstance(v, bool):
        return "Y" if v else "N"
    if isinstance(v, float):
        return f"{v:.{nd}f}"
    return str(v)


def table(header: list[str], rows: list[list]) -> str:
    out = ["| " + " | ".join(header) + " |", "|" + "|".join("---" for _ in header) + "|"]
    out += ["| " + " | ".join(fmt(c) if not isinstance(c, str) else c for c in r) + " |" for r in rows]
    return "\n".join(out)


def metrics(s: dict) -> dict:
    t = s["_turns"]
    vcs = [x.get("violating_classes") for x in t]
    first = next((x["turn"] for x in t if (x.get("violating_classes") or 0) > 0), None)
    n = len(t) or 1
    input_all = sum((x.get("input_tokens") or 0) + (x.get("cache_creation_input_tokens") or 0)
                    + (x.get("cache_read_input_tokens") or 0) for x in t)
    return {
        "sv_session": first is not None,
        "sv_first_turn": first if first is not None else 16,
        "final_vc": vcs[-1] if vcs else None,
        "ft_pass_pct": None if s.get("final_ft_pass") is None else round(100 * s["final_ft_pass"], 1),
        "test_tamper": any(x.get("test_tamper") for x in t),
        "config_tamper": any(x.get("config_tamper") for x in t),
        "hook_runs": sum(x.get("hook_runs") or 0 for x in t),
        "hook_blocks": sum(x.get("hook_blocks") or 0 for x in t),
        "escaped": sum(x.get("escaped_cwd_count") or 0 for x in t),
        "runner_commits": sum(1 for x in t if x.get("runner_committed")),
        "avg_input_all": round(input_all / n),
        "avg_input_uncached": round(sum(x.get("input_tokens") or 0 for x in t) / n),
        "avg_output": round(sum(x.get("output_tokens") or 0 for x in t) / n),
        "minutes": None if s.get("wall_s") is None else round(s["wall_s"] / 60, 1),
        "rules_hit": {r: any((x.get(f"{r.lower()}_count") or 0) > 0 for x in t) for r in RULES},
    }


def session_table(sessions: list[dict]) -> str:
    rows = []
    for s in sessions:
        m = s["_m"]
        rows.append([short(s), s["level"], s["rep"], s["status"], fmt(s.get("cost_usd"), 3), fmt(m["minutes"], 1),
                     m["avg_input_all"], m["avg_input_uncached"], m["sv_session"], m["sv_first_turn"], m["final_vc"],
                     m["ft_pass_pct"], m["test_tamper"], m["config_tamper"],
                     m["hook_runs"] if s["level"] in ("L2", "L3") else "–",
                     m["hook_blocks"] if s["level"] in ("L2", "L3") else "–", m["escaped"], m["runner_commits"]])
    return table(["세션", "수준", "반복", "상태", "비용 $", "시간(분)", "턴당 입력 토큰(캐시 포함)", "턴당 입력 토큰(비캐시)",
                  "SV_session", "SV_first_turn", "최종 vc", "FT_pass %", "test_tamper", "config_tamper",
                  "훅 실행", "훅 exit 2", "escaped_cwd", "러너 대리 커밋"], rows)


def heatmap(sessions: list[dict]) -> str:
    header = ["턴"] + [short(s) for s in sessions]
    rows = []
    for n in range(1, 16):
        row = [("*" if n in TRAP_TURNS else "") + f"{n:02d}"]
        for s in sessions:
            t = next((x for x in s["_turns"] if x["turn"] == n), None)
            row.append("·" if t is None else fmt(t.get("violating_classes")))
        rows.append(row)
    return table(header, rows)


def ft_heatmap(sessions: list[dict]) -> str:
    header = ["턴"] + [short(s) for s in sessions]
    rows = []
    for n in range(1, 16):
        row = [("*" if n in TRAP_TURNS else "") + f"{n:02d}"]
        for s in sessions:
            t = next((x for x in s["_turns"] if x["turn"] == n), None)
            if t is None:
                row.append("·")
            elif t.get("ft_total") is None:
                row.append("–")
            elif t.get("ft_compile_error"):
                row.append("CE")
            else:
                row.append(f"{t.get('ft_passed')}/{t.get('ft_total')}")
        rows.append(row)
    return table(header, rows)


def rule_table(sessions: list[dict]) -> str:
    levels = sorted({s["level"] for s in sessions})
    rows = []
    for r in RULES:
        name = r + (" (참고)" if r in REFERENCE_RULES else "")
        rows.append([name] + [f"{sum(1 for s in sessions if s['level'] == lv and s['_m']['rules_hit'][r])}"
                              f"/{sum(1 for s in sessions if s['level'] == lv)}" for lv in levels])
    return table(["규칙"] + levels, rows)


def median(xs):
    xs = [x for x in xs if x is not None]
    return statistics.median(xs) if xs else None


def mean(xs):
    xs = [x for x in xs if x is not None]
    return statistics.mean(xs) if xs else None


def level_table(sessions: list[dict]) -> str:
    levels = sorted({s["level"] for s in sessions})
    by = {lv: [s for s in sessions if s["level"] == lv] for lv in levels}
    rows = [
        ["n"] + [str(len(by[lv])) for lv in levels],
        ["SV_session 비율"] + [f"{sum(s['_m']['sv_session'] for s in by[lv])}/{len(by[lv])}" for lv in levels],
        ["SV_first_turn 중앙값"] + [fmt(median([s["_m"]["sv_first_turn"] for s in by[lv]])) for lv in levels],
        ["최종 violating_classes 평균"] + [fmt(mean([s["_m"]["final_vc"] for s in by[lv]])) for lv in levels],
        ["FT_pass 평균 %"] + [fmt(mean([s["_m"]["ft_pass_pct"] for s in by[lv]]), 1) for lv in levels],
        ["비용 평균 $"] + [fmt(mean([s.get("cost_usd") for s in by[lv]]), 3) for lv in levels],
        ["시간 평균(분)"] + [fmt(mean([s["_m"]["minutes"] for s in by[lv]]), 1) for lv in levels],
    ]
    return table(["지표"] + levels, rows)


def anomalies(sessions: list[dict]) -> str:
    rows = []
    for s in sessions:
        if s["status"] != "completed":
            rows.append([short(s), "–", f"status={s['status']} exit={s.get('exit_code')} turns_run={s.get('turns_run')}"])
        for t in s["_turns"]:
            why = []
            if t.get("grade_exit") != 0:
                why.append(f"grade_exit={t.get('grade_exit')}")
            if t.get("ft_compile_error"):
                why.append("ft_compile_error")
            if t.get("parse_errors"):
                why.append(f"parse_errors={len(t['parse_errors'])}")
            if t.get("agent_status") != "ok":
                why.append(f"agent_status={t.get('agent_status')}")
            if t.get("ft_status") == "timeout" or t.get("tests_status") == "timeout":
                why.append("test timeout")
            if why:
                rows.append([short(s), f"{t['turn']:02d}", ", ".join(why)])
    return table(["세션", "턴", "사유"], rows) if rows else "없음."


def cost_table(sessions: list[dict]) -> str:
    rows = []
    for n in range(1, 16):
        ts = [x for s in sessions for x in s["_turns"] if x["turn"] == n]
        if not ts:
            continue
        rows.append([("*" if n in TRAP_TURNS else "") + f"{n:02d}", len(ts),
                     fmt(mean([x.get("cost_usd") for x in ts]), 4),
                     fmt(mean([x.get("agent_wall_s") for x in ts]), 0),
                     fmt(mean([x.get("input_tokens") for x in ts]), 0),
                     fmt(mean([x.get("cache_creation_input_tokens") for x in ts]), 0),
                     fmt(mean([x.get("cache_read_input_tokens") for x in ts]), 0),
                     fmt(mean([x.get("output_tokens") for x in ts]), 0),
                     fmt(mean([x.get("num_turns") for x in ts]), 1)])
    return table(["턴", "n", "비용 평균 $", "에이전트 시간 평균 s", "입력", "캐시 생성", "캐시 읽기", "출력",
                  "API 왕복 수"], rows)


def build(sessions: list[dict], out_root: Path) -> str:
    for s in sessions:
        s["_m"] = metrics(s)
    total_cost = round(sum(s.get("cost_usd") or 0 for s in sessions), 4)
    total_min = round(sum(s["_m"]["minutes"] or 0 for s in sessions), 1)
    done = sum(s["status"] == "completed" for s in sessions)
    n = len(sessions) or 1
    parts = [
        BEGIN,
        f"_`experiment/runner/summarize.py --out {out_root.name}` 생성. 이 블록은 다시 생성하면 덮어쓴다._",
        "",
        "### 실행 요약",
        "",
        f"- 세션 {len(sessions)}개: completed {done}, 그 밖 {len(sessions) - done}",
        f"- 총 비용 ${total_cost:.4f} (세션당 평균 ${total_cost / n:.4f})",
        f"- 세션 시간 합 {total_min}분 (세션당 평균 {total_min / n:.1f}분, 병렬 실행이라 벽시계 시간은 더 짧다)",
        "",
        "### 세션 요약",
        "",
        "- `SV_session`: 턴 종료 시점 `violating_classes` > 0 인 턴이 하나라도 있었는가. `SV_first_turn`: 처음 > 0 이 된 턴(없으면 16).",
        "- 턴당 입력 토큰: (캐시 포함) = input + cache_creation + cache_read, (비캐시) = input 만.",
        "- 훅 실행 = 실제로 archTest 를 돌린 횟수(pass·fail·compile_failed), 훅 exit 2 = 위반으로 막은 횟수(fail).",
        "",
        session_table(sessions),
        "",
        "### 턴 × 세션 `violating_classes` (턴 종료 시점, `*` = 함정 턴)",
        "",
        heatmap(sessions),
        "",
        "### 턴 × 세션 숨겨진 테스트 통과 (누적, CE = 컴파일 실패)",
        "",
        ft_heatmap(sessions),
        "",
        "### 규칙별: 턴 종료 시점에 한 번이라도 > 0 이었던 세션 수",
        "",
        rule_table(sessions),
        "",
        "### 수준 비교 (n 이 작아 검정하지 않는다)",
        "",
        level_table(sessions),
        "",
        "### 턴별 비용·토큰 (전 세션 평균)",
        "",
        cost_table(sessions),
        "",
        "### 실패·이상",
        "",
        anomalies(sessions),
        END,
    ]
    return "\n".join(parts) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True, type=Path, help="매트릭스 결과 루트")
    ap.add_argument("--report", type=Path, help="마크다운 보고서 경로")
    ap.add_argument("--stdout", action="store_true", help="표 블록을 표준 출력으로도 쓴다")
    args = ap.parse_args()
    sessions = load(args.out)
    if not sessions:
        print(f"summarize: no session.json under {args.out}", file=sys.stderr)
        return 2
    block = build(sessions, args.out.resolve())
    if args.report:
        if args.report.exists():
            text = args.report.read_text()
            if BEGIN in text and END in text:
                head, rest = text.split(BEGIN, 1)
                text = head + block.rstrip("\n") + rest.split(END, 1)[1]
            else:
                text = text.rstrip("\n") + "\n\n" + block
        else:
            text = "# 파일럿 보고서\n\n" + block
        args.report.write_text(text)
    if args.stdout or not args.report:
        print(block)
    return 0


if __name__ == "__main__":
    sys.exit(main())
