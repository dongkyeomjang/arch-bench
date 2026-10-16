#!/usr/bin/env bash
# 깨끗한 시드(experiment/seed) → 침식 시드 experiment/seed-eroded/ (기능·HTTP 계약 동일 + 내장 위반 E1~E5)
# 사용: experiment/scripts/materialize-eroded-seed.sh
#   1) experiment/seed 가 없으면 materialize-seed.sh 로 만든다
#   2) 작업 트리를 복사하고 experiment/seed-template/eroded/apply.py 로 E1~E5 를 적용한다
#   3) ./gradlew build 로 확인하고 산출물을 지운다, 흔적 검사(깨끗한 시드와 같은 검사)
#   4) git 첫 커밋 "seed"(작성자·시각 고정 → 같은 해시)
#   5) grade.sh 로 기준선을 experiment/seed-template/eroded/baseline.json 에 쓴다
#      (시드 디렉터리 안에 두면 러너가 시드를 통째로 복사할 때 에이전트 워크스페이스로 들어간다)
# 종료 코드: 0 성공, 1 실패
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SEED="$ROOT/experiment/seed"
ERODED="$ROOT/experiment/seed-eroded"
BASELINE="$ROOT/experiment/seed-template/eroded/baseline.json"

[ -d "$SEED/.git" ] || bash "$ROOT/experiment/scripts/materialize-seed.sh"

baseline_backup=""
if [ -f "$BASELINE" ]; then  # 커밋된 기준선은 다시 만든 값과 비교한다
    baseline_backup="$(mktemp)"
    cp "$BASELINE" "$baseline_backup"
fi
rm -rf "$ERODED"
mkdir -p "$ERODED"
(cd "$SEED" && tar -c --exclude ./.git .) | tar -x -C "$ERODED"
python3 "$ROOT/experiment/seed-template/eroded/apply.py" "$ERODED"

(cd "$ERODED" && ./gradlew build -q)
rm -rf "$ERODED/build" "$ERODED/.gradle"

if grep -r "fixtures\|REPORT\|TASK" "$ERODED" --exclude-dir=.git; then
    echo "materialize-eroded-seed: experiment traces found in seed" >&2
    exit 1
fi

(
    cd "$ERODED"
    export GIT_AUTHOR_NAME=seed GIT_AUTHOR_EMAIL=seed@example.com GIT_COMMITTER_NAME=seed GIT_COMMITTER_EMAIL=seed@example.com
    export GIT_AUTHOR_DATE="2026-01-01T00:00:00Z" GIT_COMMITTER_DATE="2026-01-01T00:00:00Z"
    git init -q -b main
    git add -A
    git -c commit.gpgsign=false commit -q -m "seed"
    echo "eroded seed commit: $(git rev-parse HEAD)"
)

# 기준선: 시드 그대로를 채점한다(grade.sh 는 임시 복사본에서 오버레이하므로 시드를 바꾸지 않는다)
tmp_grade="$(mktemp)"
bash "$ROOT/experiment/scripts/grade.sh" "$ERODED" "$tmp_grade" >/dev/null || true
python3 - "$ERODED" "$tmp_grade" "$BASELINE" <<'EOF'
import json, subprocess, sys
from pathlib import Path
d = Path(sys.argv[1])
g = json.loads(Path(sys.argv[2]).read_text())
base = {"seed": "eroded", "seed_commit": subprocess.run(["git", "rev-parse", "HEAD"], cwd=d, capture_output=True, text=True).stdout.strip(),
        "violating_classes": g["violating_classes"], "violating_classes_list": g["violating_classes_list"],
        "by_rule": g["by_rule"], "primary_rules": g["primary_rules"], "tests_executed": g["tests_executed"],
        "embedded": {  # 내장 위반 → 기준선에서 그 위반의 원인 클래스·규칙 (trace_violations.py 가 쓴다)
            "E1": {"rules": [], "classes": ["com.example.lending.application.service.LoanBookService"],
                   "check": "grep 'getStatus() ==' application/service/LoanBookService.java"},
            "E2": {"rules": ["R2"], "classes": ["com.example.lending.application.port.out.LoadBookPort",
                                                "com.example.lending.application.service.LoanBookService"]},
            "E3": {"rules": ["R1", "R5"], "classes": ["com.example.lending.domain.Member"]},
            "E4": {"rules": ["R7"], "classes": ["com.example.lending.common.DateUtils"]},
            "E5": {"rules": ["R3"], "classes": ["com.example.lending.adapter.in.web.BookController"]}}}
for e, v in base["embedded"].items():  # 실제 기준선에서 그 규칙으로 잡힌 클래스만 남긴다(E1 은 grep 판정)
    if v["rules"]:
        hit = {c for r in v["rules"] for c in g["by_rule"][r]["classes"]}
        v["expected_classes"] = v["classes"]
        v["classes"] = [c for c in v["classes"] if c in hit]
Path(sys.argv[3]).write_text(json.dumps(base, ensure_ascii=False, indent=2) + "\n")
Path(sys.argv[2]).unlink()
print(f"baseline: violating_classes={g['violating_classes']} " +
      " ".join(f"{r}={v['count']}" for r, v in g["by_rule"].items()))
EOF
if [ -n "$baseline_backup" ]; then
    if ! cmp -s "$baseline_backup" "$BASELINE"; then
        echo "materialize-eroded-seed: WARNING baseline.json differs from the previous one" >&2
        diff "$baseline_backup" "$BASELINE" >&2 || true
    fi
    rm -f "$baseline_backup"
fi
