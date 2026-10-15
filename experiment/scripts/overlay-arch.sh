# shellcheck shell=bash
# inject-harness.sh(L2·L3)와 grade.sh 가 공유하는 ArchUnit 오버레이. source 해서 쓴다.
# "같은 방법으로 덮어씌운다"를 코드 한 곳으로 보장하기 위해 분리했다.

ARCH_TEST_REL="src/test/java/com/example/lending/architecture"
ARCH_TEST_FILES="HexagonalRules.java ViolationExtractor.java ArchitectureTest.java"
ARCH_GRADLE_REL="gradle/arch.gradle.kts"
ARCH_APPLY_LINE='apply(from = "gradle/arch.gradle.kts")'

# common/CLAUDE.md 금지어 (inject-harness.sh, smoke.sh 가 같은 목록으로 검사)
COMMON_FORBIDDEN_KO='아키텍처|레이어|포트|어댑터|도메인|헥사고날'
COMMON_FORBIDDEN_EN='architecture|architectural|layer|layers|layered|port|ports|adapter|adapters|domain|hexagonal'

# overlay_arch <arch-bench root> <workspace>
#   - arch-bench 원본 ArchUnit 테스트 3개를 workspace 의 같은 경로로 복사(있으면 무조건 덮어씀)
#   - harness/common/arch.gradle.kts 를 workspace/gradle/arch.gradle.kts 로 복사(덮어씀)
#   - build.gradle.kts 에 apply 줄이 정확히 한 줄로 없으면 끝에 붙인다(멱등)
# 실패 시 1 을 돌려준다.
overlay_arch() {
    local root="$1" ws="$2" f
    mkdir -p "$ws/$ARCH_TEST_REL" "$ws/gradle" || return 1
    for f in $ARCH_TEST_FILES; do
        cp "$root/lending/$ARCH_TEST_REL/$f" "$ws/$ARCH_TEST_REL/$f" || return 1
    done
    cp "$root/experiment/harness/common/arch.gradle.kts" "$ws/$ARCH_GRADLE_REL" || return 1
    [ -f "$ws/build.gradle.kts" ] || return 1
    if ! grep -qxF "$ARCH_APPLY_LINE" "$ws/build.gradle.kts"; then
        # 파일이 개행으로 끝나지 않으면 먼저 개행을 넣는다
        if [ -n "$(tail -c 1 "$ws/build.gradle.kts")" ]; then
            printf '\n' >>"$ws/build.gradle.kts" || return 1
        fi
        printf '\n%s\n' "$ARCH_APPLY_LINE" >>"$ws/build.gradle.kts" || return 1
    fi
}
