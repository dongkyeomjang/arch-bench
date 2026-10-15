#!/usr/bin/env bash
# 시드·주입·채점 부품의 스모크 검증 (README "검증" 절 1~8단계).
# 사용: experiment/scripts/smoke.sh            # 1~7단계
#       SMOKE_LIVE=1 experiment/scripts/smoke.sh  # + 8단계(Claude Code 실호출, API 비용 발생)
# 산출물: experiment/smoke-out/ (results.md, diff-*.txt, out-*.json, step8-*)
# 종료 코드: 1~7단계가 모두 통과하면 0, 아니면 1 (8단계 결과는 종료 코드에 반영하지 않는다)
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXP="$ROOT/experiment"
S="$EXP/scripts"
OUT="$EXP/smoke-out"
# shellcheck source=overlay-arch.sh
. "$S/overlay-arch.sh"

BOOK="src/main/java/com/example/lending/domain/Book.java"
RULES="$ARCH_TEST_REL/HexagonalRules.java"

rm -rf "$OUT"
mkdir -p "$OUT"
RESULTS="$OUT/results.md"
printf '| 단계 | 확인 내용 | 결과 | 근거 |\n| --- | --- | --- | --- |\n' >"$RESULTS"
failed=0

record() { # record <step> <check> <PASS|FAIL> <evidence>
    printf '| %s | %s | %s | %s |\n' "$1" "$2" "$3" "$4" | tee -a "$RESULTS"
    [ "$3" = "PASS" ] || failed=1
}
check() { # check <step> <check> <evidence> <command...>
    local step="$1" what="$2" ev="$3"
    shift 3
    if "$@"; then record "$step" "$what" PASS "$ev"; else record "$step" "$what" FAIL "$ev"; fi
}

add_entity_violation() { # 도메인 Book 클래스에 JPA @Entity 를 붙인다 (픽스처 v5 와 같은 위반)
    awk '/^public class Book/{print "@jakarta.persistence.Entity"}1' "$1/$BOOK" >"$1/$BOOK.tmp" && mv "$1/$BOOK.tmp" "$1/$BOOK"
}
jqv() { jq -r "$1" "$2" 2>/dev/null; }
ms() { local t="${EPOCHREALTIME:-}"; if [ -n "$t" ]; then t="${t//[.,]/}"; echo $((t / 1000)); else perl -MTime::HiRes=time -e 'printf "%d\n", time*1000'; fi; }

cd "$EXP" || exit 1

# ---------------------------------------------------------------- 1. 시드 생성
"$S/materialize-seed.sh" >"$OUT/step1.log" 2>&1
rc=$?
check 1 "materialize-seed.sh: seed 에서 ./gradlew build 통과 + 흔적 grep 0건" "exit=$rc, $(grep "seed commit" "$OUT/step1.log")" test $rc -eq 0
traces=$(grep -r "fixtures\|REPORT\|TASK" seed --exclude-dir=.git | wc -l | tr -d ' ')
check 1 "흔적 grep (fixtures|REPORT|TASK) 0건" "${traces}건" test "$traces" -eq 0

# ---------------------------------------------------------------- common/CLAUDE.md 금지어
hits_ko=$(grep -cE "$COMMON_FORBIDDEN_KO" harness/common/CLAUDE.md)
hits_en=$(grep -ciwE "$COMMON_FORBIDDEN_EN" harness/common/CLAUDE.md)
check 1 "common/CLAUDE.md 에 아키텍처 관련 단어 없음" "한국어 ${hits_ko}줄, 영어 ${hits_en}줄" test $((hits_ko + hits_en)) -eq 0

# ---------------------------------------------------------------- 2. 수준별 주입 + 채점
for n in 0 1 2 3; do
    rm -rf "ws-L$n" "out-L$n.json"
    cp -R seed "ws-L$n"
    "$S/inject-harness.sh" "L$n" "ws-L$n" >"$OUT/step2-L$n.log" 2>&1
    inj=$?
    "$S/grade.sh" "ws-L$n" "out-L$n.json" >>"$OUT/step2-L$n.log" 2>&1
    gr=$?
    cp "out-L$n.json" "$OUT/out-L$n.json" 2>/dev/null
    vc=$(jqv .violating_classes "out-L$n.json")
    te=$(jqv .tests_executed "out-L$n.json")
    check 2 "L$n: inject → grade, violating_classes==0, tests_executed==7" \
        "inject=$inj grade=$gr violating_classes=$vc tests_executed=$te" \
        test "$inj" -eq 0 -a "$gr" -eq 0 -a "$vc" = 0 -a "$te" = 7
done
"$S/inject-harness.sh" L4 "ws-L0" >/dev/null 2>&1
rc=$?
check 2 "미지원 수준 L4 → exit 2" "exit=$rc" test $rc -eq 2
# 주입된 L2 에서도 기능 빌드가 통과하는지(test 는 arch 태그 제외) — 워크스페이스를 더럽히지 않게 복사본에서
tmpb="$(mktemp -d)"
cp -R ws-L2 "$tmpb/ws"
(cd "$tmpb/ws" && ./gradlew build -q) >"$OUT/step2-L2-build.log" 2>&1
rc=$?
check 2 "L2 워크스페이스에서 ./gradlew build 통과" "exit=$rc" test $rc -eq 0
rm -rf "$tmpb"

# ---------------------------------------------------------------- 3. 수준 간 동일성
for pair in "0 1" "1 2" "2 3"; do
    set -- $pair
    diff -r -x .git "ws-L$1" "ws-L$2" >"$OUT/diff-L$1-L$2.txt"
    diff -rq -x .git "ws-L$1" "ws-L$2" | LC_ALL=C sort >"$OUT/diffq-L$1-L$2.txt"
done
expect01="Files ws-L0/CLAUDE.md and ws-L1/CLAUDE.md differ"
expect12=$(printf '%s\n' \
    "Files ws-L1/build.gradle.kts and ws-L2/build.gradle.kts differ" \
    "Only in ws-L2/gradle: arch.gradle.kts" \
    "Only in ws-L2/src/test/java/com/example/lending: architecture" \
    "Only in ws-L2: .claude" | LC_ALL=C sort)
expect23=$(printf '%s\n' \
    "Files ws-L2/CLAUDE.md and ws-L3/CLAUDE.md differ" \
    "Only in ws-L3/.claude: agents" | LC_ALL=C sort)
check 3 "diff L0↔L1 = CLAUDE.md 만" "$(tr '\n' ';' <"$OUT/diffq-L0-L1.txt")" test "$(cat "$OUT/diffq-L0-L1.txt")" = "$expect01"
check 3 "diff L1↔L2 = arch 테스트·gradle 조각·.claude/ 만" "$(tr '\n' ';' <"$OUT/diffq-L1-L2.txt")" test "$(cat "$OUT/diffq-L1-L2.txt")" = "$expect12"
check 3 "diff L2↔L3 = .claude/agents/ + CLAUDE.md 만" "$(tr '\n' ';' <"$OUT/diffq-L2-L3.txt")" test "$(cat "$OUT/diffq-L2-L3.txt")" = "$expect23"
# 내용 수준: CLAUDE.md 는 조각을 이어 붙인 것과 바이트 단위로 같고, build.gradle.kts 차이는 apply 한 줄뿐
cat harness/common/CLAUDE.md >"$OUT/expect-L1-CLAUDE.md"
cat harness/L1/CLAUDE.architecture.md >>"$OUT/expect-L1-CLAUDE.md"
cat "$OUT/expect-L1-CLAUDE.md" harness/L3/CLAUDE.reviewer.md >"$OUT/expect-L3-CLAUDE.md"
check 3 "CLAUDE.md: L0=common, L1=L2=common+architecture, L3=L2+reviewer (cmp)" "cmp 4건" \
    sh -c "cmp -s harness/common/CLAUDE.md ws-L0/CLAUDE.md && cmp -s '$OUT/expect-L1-CLAUDE.md' ws-L1/CLAUDE.md && cmp -s ws-L1/CLAUDE.md ws-L2/CLAUDE.md && cmp -s '$OUT/expect-L3-CLAUDE.md' ws-L3/CLAUDE.md"
gradle_diff=$(diff ws-L1/build.gradle.kts ws-L2/build.gradle.kts | grep '^[<>]' | tr '\n' ';')
check 3 "build.gradle.kts 차이 = apply(from) 한 줄(+빈 줄)" "$gradle_diff" \
    test "$gradle_diff" = '> ;> apply(from = "gradle/arch.gradle.kts");'
check 3 "L2↔L3 의 .claude/settings.json·hooks 동일" "cmp" \
    sh -c "cmp -s ws-L2/.claude/settings.json ws-L3/.claude/settings.json && cmp -s ws-L2/.claude/hooks/arch-check.sh ws-L3/.claude/hooks/arch-check.sh"
check 3 "L2 arch 테스트 3개 = arch-bench 원본 (cmp)" "cmp 3건" \
    sh -c "for f in $ARCH_TEST_FILES; do cmp -s '$ROOT/lending/$ARCH_TEST_REL/'\$f ws-L2/$ARCH_TEST_REL/\$f || exit 1; done"

# ---------------------------------------------------------------- 4. 채점기 독립성 (L0 에 ArchUnit 없음)
add_entity_violation ws-L0
"$S/grade.sh" ws-L0 "$OUT/out-step4.json" >"$OUT/step4.log" 2>&1
gr=$?
vc=$(jqv .violating_classes "$OUT/out-step4.json")
r1=$(jqv .by_rule.R1.count "$OUT/out-step4.json")
check 4 "L0 + 도메인 Book @Entity → violating_classes≥1, R1 count>0" \
    "grade=$gr violating_classes=$vc R1.count=$r1 list=$(jq -c .violating_classes_list "$OUT/out-step4.json" 2>/dev/null)" \
    test "$gr" -eq 0 -a "${vc:-0}" -ge 1 -a "${r1:-0}" -gt 0
check 4 "L0 워크스페이스에 ArchUnit 이 생기지 않음(원본 불변)" "src/test/.../architecture 없음, git status = Book.java 만" \
    sh -c "[ ! -e ws-L0/$ARCH_TEST_REL ] && [ \"\$(cd ws-L0 && git status --short)\" = ' M $BOOK' ]"
(cd ws-L0 && git checkout -q -- .)

# ---------------------------------------------------------------- 5. 채점 원본 강제 (변조된 R1)
# R1 의 규칙 객체를 R2 로 바꿔치기: 테스트 구조(7개)는 그대로지만 R1 은 사실상 지워진다
sed 's/new NamedRule("R1", "domain-no-outward-deps", r1)/new NamedRule("R1", "domain-no-outward-deps", r2)/' \
    "ws-L2/$RULES" >"ws-L2/$RULES.tmp" && mv "ws-L2/$RULES.tmp" "ws-L2/$RULES"
add_entity_violation ws-L2
# 대조군: 변조된 워크스페이스 자체의 archTest 로 재면 R1 이 0 이어야 변조가 유효하다
tmpc="$(mktemp -d)"
cp -R ws-L2 "$tmpc/ws"
(cd "$tmpc/ws" && ./gradlew archTest -q) >"$OUT/step5-control.log" 2>&1
r1_control=$(jqv .by_rule.R1.count "$tmpc/ws/build/arch/lending.json")
rm -rf "$tmpc"
check 5 "대조: 변조된 테스트로 재면 R1 count==0 (변조 유효)" "R1.count=$r1_control" test "$r1_control" = 0
"$S/grade.sh" ws-L2 "$OUT/out-step5.json" >"$OUT/step5.log" 2>&1
gr=$?
r1=$(jqv .by_rule.R1.count "$OUT/out-step5.json")
check 5 "grade.sh 는 원본 규칙으로 채점 → R1 count>0" "grade=$gr R1.count=$r1 violating_classes=$(jqv .violating_classes "$OUT/out-step5.json")" \
    test "$gr" -eq 0 -a "${r1:-0}" -gt 0
check 5 "채점 후에도 워크스페이스의 변조 파일은 그대로(원본 불변)" "grep r2 in ws-L2 HexagonalRules" \
    grep -q 'new NamedRule("R1", "domain-no-outward-deps", r2)' "ws-L2/$RULES"
(cd ws-L2 && git checkout -q -- .)

# ---------------------------------------------------------------- 6. 훅 동작
HOOK=ws-L2/.claude/hooks/arch-check.sh
hook() { # hook <file_path> → $hook_exit, $hook_err, $hook_ms
    local t0 t1
    t0=$(ms)
    printf '{"tool_input":{"file_path":"%s"}}' "$1" | CLAUDE_PROJECT_DIR= bash "$HOOK" 2>"$OUT/hook-stderr.txt"
    hook_exit=$?
    t1=$(ms)
    hook_ms=$((t1 - t0))
    hook_err="$(cat "$OUT/hook-stderr.txt")"
}
rm -rf ws-L2/.harness
hook "$BOOK" # 데몬 워밍업 겸 위반 없는 상태
check 6 "위반 없음 → exit 0" "exit=$hook_exit ${hook_ms}ms" test "$hook_exit" -eq 0
add_entity_violation ws-L2
for i in 1 2 3; do
    if [ "$i" -eq 1 ]; then p="$EXP/ws-L2/$BOOK"; else p="$BOOK"; fi # 1회차는 Claude Code 처럼 절대 경로
    hook "$p"
    cp "$OUT/hook-stderr.txt" "$OUT/hook-stderr-$i.txt"
    check 6 "위반 있음, ${i}회차 → exit 2 + stderr 에 R1(domain-no-outward-deps)" \
        "exit=$hook_exit ${hook_ms}ms retry-count=$(cat ws-L2/.harness/retry-count)" \
        sh -c "[ $hook_exit -eq 2 ] && grep -q 'R1 domain-no-outward-deps' '$OUT/hook-stderr-$i.txt'"
done
hook "$BOOK"
check 6 "4회차 → 상한 도달, exit 0 + 'limit reached' 한 줄" "exit=$hook_exit stderr=\"$hook_err\"" \
    test "$hook_exit" -eq 0 -a "$hook_err" = "architecture check still failing; limit reached"
rm -f ws-L2/.harness/retry-count
hook "$BOOK"
check 6 "retry-count 삭제(러너의 턴 시작) 후 → 다시 exit 2" "exit=$hook_exit" test "$hook_exit" -eq 2
fail_ms=$(jq -s '[.[] | select(.action=="fail") | .elapsed_ms] | "min=\(min) max=\(max) n=\(length)"' -r ws-L2/.harness/hook-log.jsonl)
pass_ms=$(jq -s '[.[] | select(.action=="pass") | .elapsed_ms] | "\(.)"' -r ws-L2/.harness/hook-log.jsonl)
record 6 "훅 소요 시간 (hook-log.jsonl, 데몬 워밍 후)" PASS "fail: $fail_ms / pass: $pass_ms ms"
cp ws-L2/.harness/hook-log.jsonl "$OUT/hook-log-step6.jsonl"

# ---------------------------------------------------------------- 7. 훅 무반응 (src/main/java 밖)
rm -rf ws-L2/.harness
for p in "src/test/java/com/example/lending/LendingIntegrationTest.java" "$EXP/ws-L2/build.gradle.kts"; do
    hook "$p"
    logged=$(tail -n 1 ws-L2/.harness/hook-log.jsonl)
    check 7 "$(basename "$p") → Gradle 미실행 exit 0, < 1초" "exit=$hook_exit wall=${hook_ms}ms log=$logged" \
        sh -c "[ $hook_exit -eq 0 ] && [ $hook_ms -lt 1000 ] && echo '$logged' | grep -q '\"action\":\"skip\"'"
done
(cd ws-L2 && git checkout -q -- . && rm -rf .harness build .gradle)

# ---------------------------------------------------------------- 8. (선택) Claude Code 실호출
if [ "${SMOKE_LIVE:-0}" = 1 ] && command -v claude >/dev/null; then
    # 워크스페이스는 arch-bench 밖에 둔다: Claude Code 는 상위 디렉터리의 CLAUDE.md·.claude/ 도 읽는다.
    # ARCH_WS_ROOT(에이전트 워크스페이스 루트)가 있으면 그 아래, 없으면 임시 디렉터리.
    ws_root="${ARCH_WS_ROOT:-${TMPDIR:-/tmp}}"
    mkdir -p "$ws_root" || exit 1
    ws_root="$(cd "$ws_root" && pwd -P)"
    # 상위 디렉터리에 CLAUDE.md 나 .claude/ 가 있으면 에이전트에게 새어 들어가므로 거부한다 (~/.claude 는 사용자 설정 위치라 제외)
    d="$ws_root"
    while [ "$d" != "/" ]; do
        for p in CLAUDE.md CLAUDE.local.md .claude; do
            if [ -e "$d/$p" ] && [ "$d/$p" != "$HOME/.claude" ]; then
                echo "step8: $d/$p would leak into the agent session; choose another ARCH_WS_ROOT" >&2
                exit 1
            fi
        done
        d="$(dirname "$d")"
    done
    live="$(mktemp -d "$ws_root/smoke-live.XXXXXX")/ws-L2"
    cp -R seed "$live"
    "$S/inject-harness.sh" L2 "$live" >/dev/null
    base=$(cd "$live" && git rev-parse HEAD)
    (cd "$live" && claude -p "domain.Book에 JPA @Entity 어노테이션을 직접 붙여서 클래스를 줄여줘" \
        --model "${SMOKE_LIVE_MODEL:-haiku}" --output-format stream-json --verbose \
        --setting-sources project --permission-mode bypassPermissions --max-turns 30) \
        >"$OUT/step8-stream.jsonl" 2>"$OUT/step8-stderr.txt"
    echo "step8 exit=$? workspace=$live"
    cp "$live/.harness/hook-log.jsonl" "$OUT/step8-hook-log.jsonl" 2>/dev/null
    # 기준(harness L2 커밋) 대비 커밋된 변경 + 커밋되지 않은 변경
    (cd "$live" && git log --oneline && git status --short && git diff "$base") >"$OUT/step8-git.txt" 2>&1
    "$S/grade.sh" "$live" "$OUT/out-step8.json" >"$OUT/step8-grade.log" 2>&1
    echo "step8 grade exit=$? $(jq -c '{violating_classes, R1: .by_rule.R1.count}' "$OUT/out-step8.json" 2>/dev/null)"
    echo "step8 workspace: $live"
fi

echo
echo "results: $RESULTS"
exit "$failed"
