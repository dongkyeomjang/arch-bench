#!/usr/bin/env bash
# 오버레이 채점: workspace 스냅샷을 임시 디렉터리에 복사하고, arch-bench 원본 ArchUnit 테스트 3개 + arch.gradle.kts 를
# 덮어씌운 뒤 ./gradlew archTest 를 돌려 build/arch/lending.json 을 <out.json> 으로 옮긴다. workspace 원본은 건드리지 않는다.
# 사용: experiment/scripts/grade.sh <workspace> <out.json>
# 종료 코드: 0 측정 성공, 3 컴파일 실패({"error":"compile_failed",...} 기록), 1 그 외 측정 실패, 2 인자 오류
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=overlay-arch.sh
. "$ROOT/experiment/scripts/overlay-arch.sh"

ws="${1:-}"
out="${2:-}"
if [ -z "$ws" ] || [ -z "$out" ] || [ ! -d "$ws" ]; then
    echo "usage: grade.sh <workspace> <out.json>" >&2
    exit 2
fi
out_dir="$(cd "$(dirname "$out")" 2>/dev/null && pwd)" || {
    echo "grade: output directory for '$out' does not exist" >&2
    exit 2
}
out="$out_dir/$(basename "$out")"

tmp="$(mktemp -d "${TMPDIR:-/tmp}/arch-grade.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT
copy="$tmp/ws"
log="$tmp/gradle.log"

fail() {
    echo "grade: $1" >&2
    [ -f "$log" ] && tail -n 40 "$log" >&2
    exit 1
}

cp -R "$ws" "$copy" || fail "copy workspace"
# 이전 빌드 산출물·훅 상태가 측정에 섞이지 않게 지운다
rm -rf "$copy/build" "$copy/.gradle" "$copy/.harness"
# test 소스셋은 오버레이 3개만 남긴다: 에이전트 테스트의 컴파일 오류가 구조 측정을 막지 않게 하고,
# 측정 대상은 main 코드뿐이다(ArchitectureTest 는 DoNotIncludeTests).
rm -rf "$copy/src/test"
overlay_arch "$ROOT" "$copy" || fail "overlay ArchUnit tests and gradle fragment"
[ -x "$copy/gradlew" ] || chmod +x "$copy/gradlew" 2>/dev/null || fail "gradlew missing"

write_compile_failed() {
    jq -n --arg stage "$1" '{error: "compile_failed", stage: $stage}' >"$out"
    echo "grade: compile failed ($1)" >&2
    grep -E 'error:|warning:' "$log" | head -n 20 >&2
    exit 3
}

(cd "$copy" && ./gradlew compileJava -q --console=plain) >"$log" 2>&1 || write_compile_failed main
(cd "$copy" && ./gradlew compileTestJava -q --console=plain) >"$log" 2>&1 || write_compile_failed test

# 위반이 있으면 archTest 는 실패로 끝난다. 종료 코드가 아니라 JSON 으로 판정한다.
(cd "$copy" && ./gradlew archTest -q --console=plain) >"$log" 2>&1
json="$copy/build/arch/lending.json"
[ -f "$json" ] || fail "no build/arch/lending.json produced"

jq -e '
    .tests_executed == 7
    and .primary_rules == ["R1","R2","R3","R6","R7"]
    and ((.by_rule | keys) == ["R1","R2","R3","R4","R5","R6","R7"])
' "$json" >/dev/null || {
    cp "$json" "$out"
    fail "measurement incomplete or primary_rules changed (tests_executed/by_rule/primary_rules check failed)"
}

cp "$json" "$out" || fail "write $out"
jq -c '{violating_classes, tests_executed}' "$out"
