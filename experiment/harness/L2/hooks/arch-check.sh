#!/usr/bin/env bash
# PostToolUse 훅 (Edit|Write|MultiEdit|Bash). 도구 종류와 무관하게, 지난 검사 이후 src/main/java 아래 내용이 바뀌었을 때만
# ./gradlew archTest 를 돌리고,
# 실패하면 실패한 규칙과 위반 상세를 stderr 로 쓰고 exit 2 로 끝낸다(Claude Code 가 stderr 를 Claude 에게 전달한다).
# 한 턴에 막는 횟수는 최대 3회다. 횟수는 .harness/retry-count 에 기록한다.
# 마지막으로 검사한 src/main/java 의 내용 지문은 .harness/last-main-hash 에 둔다.
# 실행 기록은 .harness/hook-log.jsonl 에 한 줄씩 남긴다.
set -u

MAX_BLOCKS=3

ws="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
state_dir="$ws/.harness"
counter="$state_dir/retry-count"
last_hash_file="$state_dir/last-main-hash"
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
tool=$(printf '%s' "$input" | jq -r '.tool_name // empty' 2>/dev/null)

finish() {
    local code="$1" action="$2"
    local elapsed=$(($(now_ms) - start))
    jq -cn --arg f "$file" --arg tool "$tool" --argjson ms "$elapsed" --argjson code "$code" --arg action "$action" \
        '{turn_file: $f, tool: $tool, elapsed_ms: $ms, exit_code: $code, action: $action}' >>"$log"
    exit "$code"
}

# src/main/java 의 내용 지문: (blob 해시, 경로) 목록을 정렬해 다시 해시한다. 커밋 여부와 무관하게 내용만 본다.
# 작업 트리: 추적 중인 파일 + 무시되지 않은 새 파일 (삭제된 파일은 목록에서 빠진다)
worktree_fingerprint() {
    local paths
    paths=$(git ls-files -co --exclude-standard -- src/main/java | LC_ALL=C sort -u | while IFS= read -r p; do
        [ -f "$p" ] && printf '%s\n' "$p"
    done)
    if [ -n "$paths" ]; then
        paste -d ' ' <(printf '%s\n' "$paths" | git hash-object --stdin-paths) <(printf '%s\n' "$paths")
    fi | git hash-object --stdin
}
# 아직 검사한 적이 없으면 HEAD 의 내용을 검사된 상태로 본다 (커밋된 시작 상태에서 아무것도 바꾸지 않은 도구 호출은 건너뛴다)
head_fingerprint() {
    git ls-tree -r HEAD -- src/main/java | while IFS=$'\t' read -r meta p; do
        printf '%s %s\n' "${meta##* }" "$p"
    done | LC_ALL=C sort -k2 | git hash-object --stdin
}

cd "$ws" || finish 0 skip
current=$(worktree_fingerprint)
if [ -f "$last_hash_file" ]; then
    previous=$(cat "$last_hash_file" 2>/dev/null)
else
    previous=$(head_fingerprint)
fi
if [ "$current" = "$previous" ]; then
    finish 0 skip
fi

count=0
if [ -f "$counter" ]; then
    count=$(cat "$counter" 2>/dev/null)
    case "$count" in '' | *[!0-9]*) count=0 ;; esac
fi
if [ "$count" -ge "$MAX_BLOCKS" ]; then
    echo "architecture check still failing; limit reached" >&2
    finish 0 limit
fi

out=$(./gradlew archTest -q 2>&1)
status=$?
# 이 내용은 검사했다. 같은 내용으로 다시 호출되면 Gradle 을 돌리지 않는다(통과·실패·컴파일 실패 모두).
printf '%s\n' "$current" >"$last_hash_file"
if [ "$status" -eq 0 ]; then
    finish 0 pass
fi

# 컴파일 실패는 구조 검사 결과가 아니다(여러 파일을 고치는 도중일 수 있다). 막지 않고 횟수도 세지 않는다.
if printf '%s\n' "$out" | grep -q "Execution failed for task ':compile\(Test\)\?Java'"; then
    finish 0 compile_failed
fi

echo $((count + 1)) >"$counter"
{
    echo "Architecture check failed after changes under src/main/java (./gradlew archTest)."
    echo "Fix the violations below. Re-run ./gradlew archTest to check."
    echo
    # 스택 트레이스와 Gradle 안내문은 빼고 규칙 이름·위반 상세만 남긴다
    printf '%s\n' "$out" \
        | grep -v -E '^[[:space:]]+at |^[[:space:]]+\.\.\. [0-9]+ more|^\* Try:|^> Run with|^> Get more help|^BUILD FAILED|^\* Get more help|^OpenJDK .*warning|^$' \
        | head -n 150
} >&2
finish 2 fail
