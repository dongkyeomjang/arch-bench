#!/usr/bin/env bash
# run_session.py --agent fake 가 claude 대신 호출하는 결정적 가짜 에이전트.
# 러너의 파싱·커밋 확인·tamper·채점 경로를 API 비용 없이 검증하는 용도다. 실험 데이터가 아니다.
#
# 호출: cwd=<workspace>, stdin=프롬프트, 인자는 claude 와 같은 목록(--resume <id> 만 해석한다),
#       환경 변수 FAKE_AGENT_TURN=<턴 번호(1부터)>
# 동작:
#   턴 1: domain/Book.java 에 메서드 하나 추가 + 커밋
#   턴 2: domain/Book.java 에 @Entity import 와 어노테이션 추가(R1·R5 위반) + 커밋 안 함
#   턴 3: src/test/.../architecture/HexagonalRules.java 에서 R1 항목 삭제 + 커밋 (파일이 없는 L0·L1 은 아무것도 안 함)
#   그 외 턴: 아무것도 안 함
# stdout: 최소한의 stream-json (system/init, tool_use 3개, result + usage)
set -euo pipefail

turn="${FAKE_AGENT_TURN:?FAKE_AGENT_TURN is required}"
session_id=""
while [ $# -gt 0 ]; do
    case "$1" in
        --resume) session_id="$2"; shift 2 ;;
        *) shift ;;
    esac
done
# 첫 턴은 새 세션, 이후는 받은 ID 를 그대로 쓴다(러너의 --resume 배선 확인용)
[ -n "$session_id" ] || session_id="00000000-0000-4000-8000-000000000001"
cat >/dev/null # 프롬프트는 읽고 버린다

BOOK="src/main/java/com/example/lending/domain/Book.java"
RULES="src/test/java/com/example/lending/architecture/HexagonalRules.java"
commit() { git add -A && git -c user.name=fake-agent -c user.email=fake@example.com -c commit.gpgsign=false commit -q -m "$1"; }

emit_init() {
    jq -cn --arg s "$session_id" --arg cwd "$PWD" \
        '{type: "system", subtype: "init", session_id: $s, cwd: $cwd, model: "fake-model", claude_code_version: "fake", tools: []}'
}
emit_tool_use() { # emit_tool_use <n> <name> <input-json>
    jq -cn --arg s "$session_id" --arg id "toolu_fake_${turn}_$1" --arg name "$2" --argjson input "$3" \
        '{type: "assistant", session_id: $s, parent_tool_use_id: null,
          message: {role: "assistant", content: [{type: "tool_use", id: $id, name: $name, input: $input}]}}'
}
emit_result() {
    jq -cn --arg s "$session_id" \
        '{type: "result", subtype: "success", is_error: false, session_id: $s, num_turns: 3, total_cost_usd: 0,
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
