#!/usr/bin/env bash
# lending 모듈 → experiment/seed/ (독립 실행형 단일 모듈 Gradle 프로젝트 + git 첫 커밋 "seed")
# 사용: experiment/scripts/materialize-seed.sh
# 종료 코드: 0 성공, 1 실패(복사·빌드·흔적 검사)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SEED="$ROOT/experiment/seed"
TEMPLATE="$ROOT/experiment/seed-template"
LENDING="$ROOT/lending"

rm -rf "$SEED"
mkdir -p "$SEED"

# 1) 소스: main 전체, test 는 architecture/ 를 뺀 기능 테스트만
mkdir -p "$SEED/src"
cp -R "$LENDING/src/main" "$SEED/src/main"
cp -R "$LENDING/src/test" "$SEED/src/test"
rm -rf "$SEED/src/test/java/com/example/lending/architecture"

# 2) 빌드 스크립트(단일 모듈용으로 재작성한 템플릿), wrapper, git 속성
cp "$TEMPLATE/build.gradle.kts" "$TEMPLATE/settings.gradle.kts" "$SEED/"
cp "$TEMPLATE/gitignore" "$SEED/.gitignore"
cp "$TEMPLATE/gitattributes" "$SEED/.gitattributes"
mkdir -p "$SEED/gradle/wrapper"
cp "$ROOT/gradle/wrapper/gradle-wrapper.jar" "$ROOT/gradle/wrapper/gradle-wrapper.properties" "$SEED/gradle/wrapper/"
cp "$ROOT/gradlew" "$ROOT/gradlew.bat" "$SEED/"
chmod +x "$SEED/gradlew"

# 3) 빌드 검증 후 산출물 제거(워크스페이스로 복사될 때 build/·.gradle/ 이 따라가지 않게)
(cd "$SEED" && ./gradlew build -q)
rm -rf "$SEED/build" "$SEED/.gradle"

# 4) 흔적 검사: arch-bench 의 CLAUDE.md·REPORT.md·docs·violation-fixtures·TASK 기록이 없어야 한다
if grep -r "fixtures\|REPORT\|TASK" "$SEED" --exclude-dir=.git; then
    echo "materialize-seed: experiment traces found in seed" >&2
    exit 1
fi
for p in CLAUDE.md REPORT.md docs violation-fixtures .claude; do
    if [ -e "$SEED/$p" ]; then
        echo "materialize-seed: unexpected $p in seed" >&2
        exit 1
    fi
done

# 5) git 기준 커밋. 작성자·시각을 고정해 매번 같은 커밋 해시가 나오게 한다(재현성).
(
    cd "$SEED"
    export GIT_AUTHOR_NAME=seed GIT_AUTHOR_EMAIL=seed@example.com GIT_COMMITTER_NAME=seed GIT_COMMITTER_EMAIL=seed@example.com
    export GIT_AUTHOR_DATE="2026-01-01T00:00:00Z" GIT_COMMITTER_DATE="2026-01-01T00:00:00Z"
    git init -q -b main
    git add -A
    git -c commit.gpgsign=false commit -q -m "seed"
    echo "seed commit: $(git rev-parse HEAD)"
)
