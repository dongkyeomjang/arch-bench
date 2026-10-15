#!/usr/bin/env bash
# PostToolUse 훅 (Edit|Write|MultiEdit). src/main/java 아래 파일이 바뀌면 ./gradlew archTest 를 돌리고,
# 실패하면 실패한 규칙과 위반 상세를 stderr 로 쓰고 exit 2 로 끝낸다(Claude Code 가 stderr 를 Claude 에게 전달한다).
# 한 턴에 막는 횟수는 최대 3회다. 횟수는 .harness/retry-count 에 기록한다.
# 실행 기록은 .harness/hook-log.jsonl 에 한 줄씩 남긴다.
set -u

MAX_BLOCKS=3

ws="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
state_dir="$ws/.harness"
counter="$state_dir/retry-count"
log="$state_dir/hook-log.jsonl"
mkdir -p "$state_dir"

now_ms() {
    if [ -n "${EPOCHREALTIME:-}" ]; then
        local t="${EPOCHREALTIME//[.,]/}"
        echo $((t / 1000))
    else
        perl -MTime::HiRes=time -e 'printf "%d\n", time * 1000'
    fi
}

start=$(now_ms)
input=$(cat)
file=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null)

finish() {
    local code="$1" action="$2"
    local elapsed=$(($(now_ms) - start))
    jq -cn --arg f "$file" --argjson ms "$elapsed" --argjson code "$code" --arg action "$action" \
        '{turn_file: $f, elapsed_ms: $ms, exit_code: $code, action: $action}' >>"$log"
    exit "$code"
}

# 작업 공간 기준 상대 경로로 바꾼다 (Claude Code 는 절대 경로를 준다. 직접 실행할 때는 상대 경로도 받는다)
rel="$file"
ws_physical="$(cd "$ws" && pwd -P)"
case "$rel" in
    "$ws"/*) rel="${rel#"$ws"/}" ;;
    "$ws_physical"/*) rel="${rel#"$ws_physical"/}" ;;
esac
rel="${rel#./}"

case "$rel" in
    src/main/java/*) ;;
    *) finish 0 skip ;;
esac

count=0
if [ -f "$counter" ]; then
    count=$(cat "$counter" 2>/dev/null)
    case "$count" in '' | *[!0-9]*) count=0 ;; esac
fi
if [ "$count" -ge "$MAX_BLOCKS" ]; then
    echo "architecture check still failing; limit reached" >&2
    finish 0 limit
fi

out=$(cd "$ws" && ./gradlew archTest -q 2>&1)
status=$?
if [ "$status" -eq 0 ]; then
    finish 0 pass
fi

# 컴파일 실패는 구조 검사 결과가 아니다(여러 파일을 고치는 도중일 수 있다). 막지 않고 횟수도 세지 않는다.
if printf '%s\n' "$out" | grep -q "Execution failed for task ':compile\(Test\)\?Java'"; then
    finish 0 compile_failed
fi

echo $((count + 1)) >"$counter"
{
    echo "Architecture check failed after editing $rel (./gradlew archTest)."
    echo "Fix the violations below. Re-run ./gradlew archTest to check."
    echo
    # 스택 트레이스와 Gradle 안내문은 빼고 규칙 이름·위반 상세만 남긴다
    printf '%s\n' "$out" \
        | grep -v -E '^[[:space:]]+at |^[[:space:]]+\.\.\. [0-9]+ more|^\* Try:|^> Run with|^> Get more help|^BUILD FAILED|^\* Get more help|^OpenJDK .*warning|^$' \
        | head -n 150
} >&2
finish 2 fail
