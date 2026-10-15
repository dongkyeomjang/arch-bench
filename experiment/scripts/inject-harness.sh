#!/usr/bin/env bash
# 시드 복사본(workspace)에 하네스 수준 L0~L3 를 덧씌우고, git 이력을 새로 만들어 "Initial commit" 하나로 커밋한다.
# 시드의 "seed" 커밋은 지운다: 네 수준 모두 git log 에 같은 메시지의 커밋 1개만 보이게 하기 위함.
# 사용: experiment/scripts/inject-harness.sh <L0|L1|L2|L3> <workspace>
# 종료 코드: 0 성공, 1 복사·커밋 실패, 2 미지원 수준(또는 인자 오류)
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HARNESS="$ROOT/experiment/harness"
# shellcheck source=overlay-arch.sh
. "$ROOT/experiment/scripts/overlay-arch.sh"

level="${1:-}"
ws="${2:-}"

case "$level" in
    L0) n=0 ;;
    L1) n=1 ;;
    L2) n=2 ;;
    L3) n=3 ;;
    *)
        echo "inject-harness: unsupported level '$level' (L0|L1|L2|L3)" >&2
        exit 2
        ;;
esac
if [ -z "$ws" ] || [ ! -d "$ws/.git" ] || [ ! -f "$ws/build.gradle.kts" ]; then
    echo "inject-harness: '$ws' is not a seed workspace (cp -r experiment/seed <workspace> first)" >&2
    exit 2
fi
ws="$(cd "$ws" && pwd)"
if [ "$ws" = "$ROOT/experiment/seed" ]; then
    echo "inject-harness: refusing to modify the seed itself; copy it first" >&2
    exit 2
fi

fail() {
    echo "inject-harness: $1" >&2
    exit 1
}

# 공통 CLAUDE.md 에 아키텍처 관련 단어가 있으면 L0 가 L1 을 흉내 내게 되므로 주입하지 않는다.
# 한국어는 부분 문자열, 영어는 단어 단위(-w: import·report 의 "port" 오탐 방지)로 찾는다.
if grep -nE "$COMMON_FORBIDDEN_KO" "$HARNESS/common/CLAUDE.md" >&2 \
    || grep -niwE "$COMMON_FORBIDDEN_EN" "$HARNESS/common/CLAUDE.md" >&2; then
    fail "common/CLAUDE.md mentions architecture terms (see lines above)"
fi

# 시드 이력을 버리고 새 저장소로 시작한다 (exclude 설정보다 먼저)
rm -rf "$ws/.git" || fail "remove seed .git"
(cd "$ws" && git init -q -b main) || fail "git init"

# L0: 공통 CLAUDE.md
cp "$HARNESS/common/CLAUDE.md" "$ws/CLAUDE.md" || fail "copy common/CLAUDE.md"

# L1: 아키텍처 산문을 CLAUDE.md 끝에 그대로 덧붙인다 (L1·L2·L3 바이트 동일)
if [ "$n" -ge 1 ]; then
    cat "$HARNESS/L1/CLAUDE.architecture.md" >>"$ws/CLAUDE.md" || fail "append L1/CLAUDE.architecture.md"
fi

# L2: arch-bench 원본 ArchUnit 테스트 + Gradle 조각 + PostToolUse 훅
if [ "$n" -ge 2 ]; then
    overlay_arch "$ROOT" "$ws" || fail "overlay ArchUnit tests and gradle fragment"
    mkdir -p "$ws/.claude/hooks" || fail "mkdir .claude/hooks"
    cp "$HARNESS/L2/settings.json" "$ws/.claude/settings.json" || fail "copy L2/settings.json"
    cp "$HARNESS/L2/hooks/arch-check.sh" "$ws/.claude/hooks/arch-check.sh" || fail "copy L2/hooks/arch-check.sh"
    chmod +x "$ws/.claude/hooks/arch-check.sh" || fail "chmod arch-check.sh"
    # 훅 상태 파일(.harness/)이 에이전트 커밋에 섞이지 않게 한다. .git 안이라 수준 간 트리 diff 에 나타나지 않는다.
    grep -qxF "/.harness/" "$ws/.git/info/exclude" 2>/dev/null || echo "/.harness/" >>"$ws/.git/info/exclude" || fail "git exclude"
fi

# L3: 리뷰어 서브에이전트 + 호출 지시
if [ "$n" -ge 3 ]; then
    mkdir -p "$ws/.claude/agents" || fail "mkdir .claude/agents"
    cp "$HARNESS/L3/agents/architecture-reviewer.md" "$ws/.claude/agents/architecture-reviewer.md" || fail "copy reviewer"
    cat "$HARNESS/L3/CLAUDE.reviewer.md" >>"$ws/CLAUDE.md" || fail "append L3/CLAUDE.reviewer.md"
fi

(
    cd "$ws" || exit 1
    export GIT_AUTHOR_NAME=seed GIT_AUTHOR_EMAIL=seed@example.com GIT_COMMITTER_NAME=seed GIT_COMMITTER_EMAIL=seed@example.com
    export GIT_AUTHOR_DATE="2026-01-01T00:00:00Z" GIT_COMMITTER_DATE="2026-01-01T00:00:00Z"
    git add -A && git -c commit.gpgsign=false commit -q -m "Initial commit"
) || fail "git commit"
