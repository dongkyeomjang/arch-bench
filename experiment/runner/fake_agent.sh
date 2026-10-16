#!/usr/bin/env bash
# run_session.py --agent fake 가 claude 대신 호출하는 결정적 가짜 에이전트.
# 러너의 파싱·커밋 확인·tamper·채점 경로를 API 비용 없이 검증하는 용도다. 실험 데이터가 아니다.
#
# 호출: cwd=<workspace>, stdin=프롬프트, 인자는 claude 와 같은 목록(--resume <id> 만 해석한다),
#       환경 변수 FAKE_AGENT_TURN=<턴 번호(1부터)>, FAKE_AGENT_COST_USD=<턴당 가짜 비용(기본 0)>
#       FAKE_AGENT_RATE_LIMIT_TURN=<턴 번호> 면 그 턴의 첫 호출은 구독 한도 거부(rate_limit_event status=rejected +
#       is_error result, exit 1)로 끝난다. FAKE_AGENT_RATE_LIMIT_RESET_S=<초>(기본 2, "none" 이면 resetsAt 없음),
#       FAKE_AGENT_RATE_LIMIT_REPEAT=1 이면 매 호출 거부(대기 시간 초과 확인용). 러너의 한도 대기·재시도 확인용
#       FAKE_AGENT_MODEL=<이름>(기본 fake-model): init 의 model (매트릭스 model_id 변경 감지 확인용)
# 동작:
#   턴 1: domain/Book.java 에 메서드 하나 추가 + 커밋, Bash `ls ..; ls ../..` 기록(escaped_cwd_count 확인용, 실행은 안 함)
#   턴 2: domain/Book.java 에 @Entity import 와 어노테이션 추가(R1·R5 위반) + 커밋 안 함
#   턴 3: src/test/.../architecture/HexagonalRules.java 에서 R1 항목 삭제 + 커밋 (파일이 없는 L0·L1 은 아무것도 안 함)
#   그 외 턴: 아무것도 안 함
# stdout: 최소한의 stream-json (system/init, tool_use 3~4개, result + usage)
#   result.total_cost_usd 는 claude 처럼 대화 누적값: --resume 이면 턴 번호 × FAKE_AGENT_COST_USD, 새 세션이면 FAKE_AGENT_COST_USD
#   session_id: --resume 이면 받은 값, 아니면 새 UUID (fresh 모드에서 턴마다 달라지는지 확인용)
set -euo pipefail

turn="${FAKE_AGENT_TURN:?FAKE_AGENT_TURN is required}"
session_id=""
resumed=0
while [ $# -gt 0 ]; do
    case "$1" in
        --resume) session_id="$2"; resumed=1; shift 2 ;;
        *) shift ;;
    esac
done
# --resume 이 없으면 새 세션, 있으면 받은 ID 를 그대로 쓴다(러너의 --resume 배선 확인용)
[ -n "$session_id" ] || session_id="$(python3 -c 'import uuid; print(uuid.uuid4())')"
cat >/dev/null # 프롬프트는 읽고 버린다
cost_mult="$turn"
[ "$resumed" = 1 ] || cost_mult=1

marker="$(git rev-parse --git-dir)/fake-rate-limited-$turn"
if [ "${FAKE_AGENT_RATE_LIMIT_TURN:-}" = "$turn" ] && { [ "${FAKE_AGENT_RATE_LIMIT_REPEAT:-0}" = 1 ] || [ ! -e "$marker" ]; }; then
    touch "$marker"
    reset_s="${FAKE_AGENT_RATE_LIMIT_RESET_S:-2}"
    resets_at="null"
    [ "$reset_s" = none ] || resets_at=$(( $(date +%s) + reset_s ))
    jq -cn --arg s "$session_id" --arg m "${FAKE_AGENT_MODEL:-fake-model}" '{type: "system", subtype: "init", session_id: $s, model: $m, claude_code_version: "fake", tools: []}'
    jq -cn --arg s "$session_id" --argjson r "$resets_at" \
        '{type: "rate_limit_event", session_id: $s,
          rate_limit_info: {status: "rejected", resetsAt: $r, rateLimitType: "five_hour", unifiedWindows: {five_hour: {utilization: 1.0, resetsAt: $r}}}}'
    jq -cn --arg s "$session_id" \
        '{type: "result", subtype: "success", is_error: true, session_id: $s, num_turns: 1, total_cost_usd: 0,
          usage: {input_tokens: 0, output_tokens: 0, cache_creation_input_tokens: 0, cache_read_input_tokens: 0},
          result: "You have hit your limit (fake)"}'
    exit 1
fi

BOOK="src/main/java/com/example/lending/domain/Book.java"
RULES="src/test/java/com/example/lending/architecture/HexagonalRules.java"
commit() { git add -A && git -c user.name=fake-agent -c user.email=fake@example.com -c commit.gpgsign=false commit -q -m "$1"; }

emit_init() {
    jq -cn --arg s "$session_id" --arg cwd "$PWD" --arg m "${FAKE_AGENT_MODEL:-fake-model}" \
        '{type: "system", subtype: "init", session_id: $s, cwd: $cwd, model: $m, claude_code_version: "fake", tools: []}'
}
emit_tool_use() { # emit_tool_use <n> <name> <input-json>
    jq -cn --arg s "$session_id" --arg id "toolu_fake_${turn}_$1" --arg name "$2" --argjson input "$3" \
        '{type: "assistant", session_id: $s, parent_tool_use_id: null,
          message: {role: "assistant", content: [{type: "tool_use", id: $id, name: $name, input: $input}]}}'
}
emit_result() {
    jq -cn --arg s "$session_id" --argjson c "${FAKE_AGENT_COST_USD:-0}" --argjson t "$cost_mult" \
        '{type: "result", subtype: "success", is_error: false, session_id: $s, num_turns: 3, total_cost_usd: ($c * $t),
          usage: {input_tokens: 10, output_tokens: 20, cache_creation_input_tokens: 30, cache_read_input_tokens: 40},
          result: "done"}'
}

emit_init
case "$turn" in
    1)
        emit_tool_use 1 Read "$(jq -cn --arg f "$PWD/$BOOK" '{file_path: $f}')"
        awk '/^    public UUID getId\(\) \{/ && !done {
                 print "    public boolean isAvailable() {"
                 print "        return status == BookStatus.AVAILABLE;"
                 print "    }"
                 print ""
                 done = 1
             } 1' "$BOOK" >"$BOOK.tmp" && mv "$BOOK.tmp" "$BOOK"
        emit_tool_use 2 Edit "$(jq -cn --arg f "$PWD/$BOOK" '{file_path: $f, old_string: "x", new_string: "y"}')"
        commit "Add Book.isAvailable"
        emit_tool_use 3 Bash '{"command":"git commit -m \"Add Book.isAvailable\""}'
        emit_tool_use 4 Bash '{"command":"ls ..; ls ../.."}'
        ;;
    2)
        emit_tool_use 1 Read "$(jq -cn --arg f "$PWD/$BOOK" '{file_path: $f}')"
        awk '/^import java.time.LocalDate;/ { print "import jakarta.persistence.Entity;" }
             /^public class Book/ { print "@Entity" }
             1' "$BOOK" >"$BOOK.tmp" && mv "$BOOK.tmp" "$BOOK"
        emit_tool_use 2 Edit "$(jq -cn --arg f "$PWD/$BOOK" '{file_path: $f, old_string: "x", new_string: "y"}')"
        emit_tool_use 3 Bash '{"command":"./gradlew compileJava -q"}'
        ;;
    3)
        emit_tool_use 1 Read "$(jq -cn --arg f "$PWD/$RULES" '{file_path: $f}')"
        if [ -f "$RULES" ]; then
            grep -v 'new NamedRule("R1"' "$RULES" >"$RULES.tmp" && mv "$RULES.tmp" "$RULES"
            commit "Simplify rules"
        fi
        emit_tool_use 2 Edit "$(jq -cn --arg f "$PWD/$RULES" '{file_path: $f, old_string: "x", new_string: "y"}')"
        emit_tool_use 3 Bash '{"command":"git commit -m \"Simplify rules\""}'
        ;;
    *)
        emit_tool_use 1 Read '{"file_path":"README"}'
        emit_tool_use 2 Bash '{"command":"ls"}'
        emit_tool_use 3 Bash '{"command":"git status"}'
        ;;
esac
emit_result
