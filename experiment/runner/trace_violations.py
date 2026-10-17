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
노출(TASK12)
- 턴 N 의 `git diff --name-only --no-renames head_before head_after` 가 위반 원인 클래스의 소스 파일(중첩 클래스는 바깥 파일)을
  바꿨으면(삭제 포함) 그 위반은 턴 N 에 노출됐다 → `exposed_turns`. E1 은 LoanBookService.java.
- `exposure`: 치유된 턴(`healed_at`)에 노출 → `healed_when_exposed`, 치유됐는데 그 턴에 노출 없음 → `healed_unexposed`
  (요청 밖 리팩터링 또는 다른 파일 변경의 부수 효과), 유지 + 노출 1회 이상 → `persisted_despite_exposure`,
  유지 + 노출 없음 → `never_exposed`.
규칙 위치(TASK12, 루브릭 D1 의 자동 근사치 — 사람 채점 대체 아님)
- 턴 2(한도)·7(폐기)·8(후원자)·13(정지)의 규칙이 그 턴 끝 커밋과 마지막 커밋에서 domain·application.service 중 어디 있는지.
  증거: (1) 규칙 예외의 `throw new` 위치, (2) 조건이 규칙 패턴에 맞고 블록 안에 `throw` 가 있는 `if`(후원자는 블록 무관),
  (3) 규칙 패턴 + 비교 연산자·`contains(` 가 있는 `return` 식. 두 계층 모두 증거 → `both`, 하나도 없음 → `unknown`.
  판정 로직과 한계는 experiment/README.md "위반 추적".
종료 코드: 0 성공, 2 입력 오류
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

BASE = "com.example.lending"
TRACE_RULES = ("R1", "R2", "R3", "R5", "R6", "R7")  # 주 지표(R1·R2·R3·R6·R7) + R5. R4 는 순환 양끝을 다 잡아 과대 집계라 뺀다
E1_PATH = "src/main/java/com/example/lending/application/service/LoanBookService.java"
E1_PATTERN = "getStatus() =="
SRC_MAIN = "src/main/java/"
# 규칙 위치 판정: 턴 → (예외 이름 정규식 | None, 조건 정규식)
RULES = {
    2: ("limit", r"\w*LimitExceeded\w*", r"(?i)limit|max|\b5\b"),
    7: ("discard", r"\w*Discard\w*Exception", r"DISCARDED|[Dd]iscarded"),
    8: ("sponsor", None, r"(?i)exempt|unlimited|sponsor|patron|vip|privileged|11111111-1111"),
    13: ("suspend", r"\w*Suspended\w*Exception", r"[Ss]uspended"),
}
COMMENT_RE = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)
COMPARE_RE = re.compile(r"[<>]=?|==|!=|\.contains\(")


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


def class_file(fqn: str) -> str:
    return SRC_MAIN + fqn.split("$")[0].replace(".", "/") + ".java"


def changed_files(ws: Path | None, before: str | None, after: str | None) -> set[str] | None:
    if ws is None or not before or not after:
        return None
    if before == after:
        return set()
    p = subprocess.run(["git", "diff", "--name-only", "--no-renames", before, after], cwd=ws, capture_output=True, text=True)
    return set(p.stdout.split()) if p.returncode == 0 else None


def exposure(f: dict, exposed: list[int]) -> str | None:
    if f["status"] == "healed":
        return "healed_when_exposed" if f["healed_at"] in exposed else "healed_unexposed"
    if f["status"] == "persisted":
        return "persisted_despite_exposure" if exposed else "never_exposed"
    return None


def java_sources(ws: Path, head: str) -> dict[str, str]:
    """head 의 src/main/java 아래 .java 파일 → 주석을 뺀 본문."""
    names = subprocess.run(["git", "ls-tree", "-r", "--name-only", head, "--", SRC_MAIN], cwd=ws,
                           capture_output=True, text=True).stdout.split()
    names = [n for n in names if n.endswith(".java")]
    out = {}
    for n in names:
        p = subprocess.run(["git", "show", f"{head}:{n}"], cwd=ws, capture_output=True, text=True)
        if p.returncode == 0:
            out[n] = COMMENT_RE.sub(" ", p.stdout)
    return out


def balanced(text: str, i: int, open_: str, close: str) -> int:
    """text[i] == open_ 일 때 짝이 맞는 close 의 위치(없으면 len). 문자열 리터럴 안의 괄호는 무시한다."""
    depth, j, quote = 0, i, None
    while j < len(text):
        ch = text[j]
        if quote:
            if ch == "\\":
                j += 1
            elif ch == quote:
                quote = None
        elif ch in "\"'":
            quote = ch
        elif ch == open_:
            depth += 1
        elif ch == close:
            depth -= 1
            if depth == 0:
                return j
        j += 1
    return len(text)


def evidence(src: str, exc: str | None, cond: str, any_block: bool) -> list[str]:
    ev = []
    if exc:
        ev += [f"throw {m.group(1)}" for m in re.finditer(rf"throw\s+new\s+({exc})\s*\(", src)]
    for m in re.finditer(r"\bif\s*\(", src):
        a = m.end() - 1
        b = balanced(src, a, "(", ")")
        c = src[a + 1:b]
        rest = src[b + 1:].lstrip()
        start = len(src) - len(rest)
        body = src[start:balanced(src, start, "{", "}") + 1] if rest.startswith("{") else rest.split(";", 1)[0]
        if re.search(cond, c) and (any_block or "throw" in body):
            ev.append("if " + " ".join(c.split())[:80])
    for m in re.finditer(r"\breturn\b([^;]*);", src):
        e = m.group(1)
        if re.search(cond, e) and COMPARE_RE.search(e):
            ev.append("return " + " ".join(e.split())[:80])
    return ev


def rule_placement(ws: Path | None, head: str | None) -> dict | None:
    """head 커밋에서 턴별 규칙의 위치: {turn: {"placement": domain|service|both|unknown, "evidence": {layer: [...]}}}."""
    if ws is None or not head:
        return None
    srcs = java_sources(ws, head)
    out = {}
    for t, (name, exc, cond) in RULES.items():
        ev: dict[str, list[str]] = {}
        for path, src in srcs.items():
            lay = layer(path[len(SRC_MAIN):-len(".java")].replace("/", "."))
            if lay not in ("domain", "application.service"):
                continue
            found = evidence(src, exc, cond, any_block=exc is None)
            if found:
                ev.setdefault(lay, []).extend(f"{Path(path).stem}: {x}" for x in found)
        has_d, has_s = "domain" in ev, "application.service" in ev
        out[t] = {"rule": name, "placement": "both" if has_d and has_s else "domain" if has_d else
                  "service" if has_s else "unknown", "evidence": ev}
    return out


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
        turns.append({"turn": t["turn"], "arch": arch, "head_after": t.get("head_after"),
                      "changed": changed_files(ws, t.get("head_before"), t.get("head_after"))})

    def exposed_turns(files: set[str]) -> list[int]:
        return [x["turn"] for x in turns if x["changed"] and x["changed"] & files]

    # 기준선 클래스
    classes_out = {}
    for c in base_classes:
        series = [(x["turn"], None if x["arch"] is None else c in x["arch"]["violating_classes_list"]) for x in turns]
        f = fate(series)
        ex = exposed_turns({class_file(c)})
        classes_out[c] = {"rules": rules_of(base, c), **f, "exposed_turns": ex, "exposure": exposure(f, ex)}

    # 내장 위반 E1~E5
    embedded = {}
    for e, spec in base["embedded"].items():
        if e == "E1":
            series = [(x["turn"], e1_present(ws, x["head_after"])) for x in turns]
        else:
            series = [(x["turn"], None if x["arch"] is None else
                       any(c in (x["arch"]["by_rule"].get(r) or {}).get("classes", []) for r in spec["rules"] for c in spec["classes"]))
                      for x in turns]
        f = fate(series)
        ex = exposed_turns({class_file(c) for c in spec["classes"]})
        embedded[e] = {"rules": spec["rules"], "classes": spec["classes"], **f,
                       "exposed_turns": ex, "exposure": exposure(f, ex), "by_turn": {t: v for t, v in series}}

    # 규칙 위치: 규칙이 생긴 턴의 끝 커밋과 마지막 커밋
    by_turn_head = {x["turn"]: x["head_after"] for x in turns}
    last_head = turns[-1]["head_after"] if turns else None
    placement = {}
    final_rp = rule_placement(ws, last_head)
    for t in RULES:
        at = rule_placement(ws, by_turn_head.get(t)) if t in by_turn_head else None
        placement[t] = {"rule": RULES[t][0], "at_turn": None if at is None else at[t]["placement"],
                        "at_final": None if final_rp is None else final_rp[t]["placement"],
                        "evidence_at_turn": None if at is None else at[t]["evidence"],
                        "evidence_at_final": None if final_rp is None else final_rp[t]["evidence"]}

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
        "embedded_exposure": {e: v["exposure"] for e, v in embedded.items()},
        **{k: sorted(e for e, v in embedded.items() if v["exposure"] == k) for k in
           ("healed_when_exposed", "healed_unexposed", "persisted_despite_exposure", "never_exposed")},
        "rule_placement": {t: v["at_turn"] for t, v in placement.items()},
        "rule_placement_final": {t: v["at_final"] for t, v in placement.items()},
        "e1_persisted": embedded.get("E1", {}).get("status") == "persisted" if embedded.get("E1", {}).get("status") != "unknown" else None,
    }
    if summary["final_violating_classes"] is not None:
        summary["delta"] = summary["final_violating_classes"] - base["violating_classes"]
    out = {"session_id": session["session_id"], "seed": session.get("seed"), "baseline": str(args.baseline),
           "baseline_seed_commit": base.get("seed_commit"), "session_seed_commit": session.get("seed_commit"),
           "workspace_used_for_e1": str(ws) if ws else None, "summary": summary,
           "baseline_classes": classes_out, "embedded": embedded, "new_classes": new, "rule_placement": placement}
    if base.get("seed_commit") and session.get("seed_commit") and base["seed_commit"] != session["seed_commit"]:
        out["warning"] = "session seed_commit differs from baseline seed_commit"
    dest = args.out or (args.session / "violations-trace.json")
    dest.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
    print(f"{session['session_id']}: " + " ".join(f"{k}={summary[k]}" for k in
          ("persisted_count", "healed_count", "spread_count", "novel_count", "delta", "embedded_exposure", "rule_placement")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
