#!/usr/bin/env bash
# 세션 워크스페이스(또는 참조 구현 디렉터리)를 의미론 평가용 익명 사본으로 만든다.
#
# 사용: anonymize.sh <workspace> <out-dir> <blind-id>
#   <workspace>  에이전트 워크스페이스(git 저장소면 HEAD 의 커밋된 상태) 또는 일반 디렉터리
#   <out-dir>    만들 사본 경로(이미 있으면 거부)
#   <blind-id>   사본의 식별자. 커밋 메시지에만 쓴다. blind-id ↔ session-id 대응은 호출자가 $OUT_ROOT/blind-map.json 에 둔다
#
# 사본에서 빼는 것(하네스 수준·모델·반복을 알 수 있는 흔적):
#   .claude/  CLAUDE.md  CLAUDE.local.md  .harness/  build/  .gradle/
#   gradle/arch.gradle.kts 와 build.gradle.kts 의 그 apply 줄 (L2·L3 오버레이)
#   src/test/java/com/example/lending/architecture/ (L2·L3 에 복사된 ArchUnit 채점기)
#   git 이력·브랜치 → 새 저장소에 커밋 1개("snapshot <blind-id>", 고정 작성자·시각)
# 코드 안의 흔적(주석의 CLAUDE.md 언급 등)은 지우지 않고 stdout 에 `leak: <파일>:<줄>: <내용>` 으로 알린다.
#
# 종료 코드: 0 성공, 2 인자 오류, 4 out-dir 이 이미 있음
set -euo pipefail

if [ $# -ne 3 ]; then
    sed -n '2,15p' "$0" >&2
    exit 2
fi
src="$1"; out="$2"; blind="$3"
[ -d "$src" ] || { echo "anonymize: $src is not a directory" >&2; exit 2; }
[ ! -e "$out" ] || { echo "anonymize: $out already exists" >&2; exit 4; }
mkdir -p "$out"

if git -C "$src" rev-parse --is-inside-work-tree >/dev/null 2>&1 && [ "$(git -C "$src" rev-parse --show-toplevel)" = "$(cd "$src" && pwd -P)" ]; then
    git -C "$src" archive HEAD | tar -x -C "$out"   # 커밋된 상태만 (작업 트리의 미추적 파일·.harness 제외)
else
    (cd "$src" && tar -c --exclude ./.git --exclude ./build --exclude ./.gradle .) | tar -x -C "$out"
fi

rm -rf "$out/.claude" "$out/CLAUDE.md" "$out/CLAUDE.local.md" "$out/.harness" "$out/build" "$out/.gradle" \
       "$out/gradle/arch.gradle.kts" "$out/src/test/java/com/example/lending/architecture"
if [ -f "$out/build.gradle.kts" ]; then
    grep -v 'gradle/arch.gradle.kts' "$out/build.gradle.kts" >"$out/build.gradle.kts.tmp" || true
    mv "$out/build.gradle.kts.tmp" "$out/build.gradle.kts"
fi

# 남은 흔적 알림 (평가자가 수준을 추측할 수 있는 단어)
grep -rnE 'CLAUDE\.md|archTest|ArchUnit|HexagonalRules|architecture-reviewer|arch-check|\.harness|하네스|훅' "$out" 2>/dev/null \
    | sed "s|^$out/|leak: |" | cut -c1-200 || true

(
    cd "$out"
    git init -q
    git add -A
    GIT_AUTHOR_DATE="2026-01-01T00:00:00Z" GIT_COMMITTER_DATE="2026-01-01T00:00:00Z" \
        git -c user.name=anonymous -c user.email=anonymous@example.com -c commit.gpgsign=false \
        commit -q -m "snapshot $blind"
)
