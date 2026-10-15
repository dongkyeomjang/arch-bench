# TASK4 — 실험 시드 저장소, 하네스 수준(L0~L3) 주입, 오버레이 채점기

- **Date**: 2026-10-15
- **Status**: completed
- **Related TASKs**: TASK3 (채점기 동결 상태, `.gitignore` 무기준 패턴 사고, PRIMARY_RULES)
- **Commits**: 이 문서와 같은 커밋

## Request
작업 지시 4: 세션 러너가 쓸 부품 세 개. (1) `lending` 에서 실험 흔적 없는 독립 Spring 시드 저장소를 만드는 `materialize-seed.sh`, (2) Galster et al. 스펙트럼의 하네스 수준 L0(없음)·L1(CLAUDE.md 아키텍처 산문)·L2(+ArchUnit 테스트+PostToolUse 훅)·L3(+리뷰어 서브에이전트)를 덧씌우는 `inject-harness.sh`, (3) ArchUnit 이 없는 L0·L1 워크스페이스도 원본 규칙으로 채점하는 `grade.sh`. 하네스 외 모든 것은 수준 간 바이트 동일. `smoke.sh` 1~7단계(+선택 8단계)로 완료 판정. 채점기 코드·픽스처는 수정 금지.

## Context
- 채점기(R1~R7, `ViolationExtractor`, `lending.json`)는 TASK3 Follow-up 이후 동결. 이 작업은 원본 3개 파일을 **복사만** 한다.
- 작업 시작 시 브랜치가 dfa1bef 였고 origin/main 은 173af84(README 추가 머지). 지시 전제대로 173af84 로 fast-forward 후 시작.
- Claude Code 2.1.300 문서 확인: PostToolUse exit 2 = stderr 를 Claude 에게 보여줌(차단 불가), `tool_input.file_path` 는 항상 절대 경로, 서브에이전트는 `.claude/agents/*.md` + frontmatter.

## Changes
- **시드 생성** `experiment/scripts/materialize-seed.sh` + `experiment/seed-template/`(단일 모듈 `build.gradle.kts`, `settings.gradle.kts`, 루트 기준 `.gitignore`, `.gitattributes`)
  - `src/main` 전체 + `architecture/` 를 뺀 `src/test`, wrapper 복사 → `./gradlew build` → `build/`·`.gradle/` 삭제 → 흔적 grep 0건 확인 → `git init -b main` + `seed` 커밋.
  - WHY 작성자·시각 고정: 시드 커밋 해시(4b39a61)가 매번 같아 러너가 기준을 해시로 고정할 수 있다.
  - WHY `.gitignore` 를 전부 루트 기준: TASK3 의 `out/` 사고(헥사고날 `adapter/out` 무시) 재발 방지. `.DS_Store` 만 예외.
- **공용 오버레이** `experiment/scripts/overlay-arch.sh` 의 `overlay_arch()`: 원본 ArchUnit 테스트 3개 + `harness/common/arch.gradle.kts` → `gradle/arch.gradle.kts` + `build.gradle.kts` 끝에 `apply(from=...)` 한 줄(멱등). inject(L2·L3)와 grade 가 같은 함수를 source → "같은 방법" 을 코드로 보장.
  - WHY 시드에 조건부 apply 를 두지 않음: L0·L1 에 "arch" 흔적이 생긴다. 대가는 L1↔L2 diff 의 한 줄(smoke 3단계가 정확히 그 한 줄인지 검사).
  - `arch.gradle.kts` = 루트 `archTest` 정의(0개 가드 포함) + `test` 에서 `arch` 태그 제외 + `testLogging.quiet`(‑q 에서도 규칙 이름·위반 상세 출력, 훅용).
- **주입** `experiment/scripts/inject-harness.sh`: L0 common CLAUDE.md, L1 산문 append, L2 오버레이 + `.claude/settings.json` + `.claude/hooks/arch-check.sh` + `.git/info/exclude` 에 `/.harness/`, L3 리뷰어 + 지시 append, `harness <level>` 커밋. 종료 0/1/2. 주입 전 common CLAUDE.md 금지어 검사.
- **하네스 텍스트** `experiment/harness/`
  - `common/CLAUDE.md`: 빌드·테스트 명령, 요청마다 커밋, 테스트 삭제·`@Disabled` 금지, Lombok/MapStruct 금지, 한국어 응답. 아키텍처 관련 단어 0건(한국어 부분 문자열, 영어 `-w`).
  - `L1/CLAUDE.architecture.md`: 레이어와 책임 / 의존 방향 / 금지 사항(R1~R7 확정 문장과 1:1, 번호 없음) / 예시. 대응표는 README.
  - `L2/settings.json` + `L2/hooks/arch-check.sh`: `src/main/java` 밖이면 즉시 exit 0, `archTest -q` 실패 시 stderr + exit 2, `.harness/retry-count` 3회 상한, `.harness/hook-log.jsonl` 에 `{turn_file, elapsed_ms, exit_code, action}`.
    - WHY 컴파일 실패는 막지 않음(exit 0, `compile_failed`): 여러 파일 수정 도중의 중간 상태가 턴당 3회를 소모하지 않게.
  - `L3/agents/architecture-reviewer.md`(tools Read·Grep·Glob·Bash, `model: inherit`, 루브릭 4차원, 첫 줄 `VERDICT:` 고정, R1~R7 문장·규칙 이름 미포함), `L3/CLAUDE.reviewer.md`(최대 2회, REJECT 면 사유 요약 후 커밋).
- **채점** `experiment/scripts/grade.sh`: 임시 복사 → `build`·`.gradle`·`.harness`·`src/test` 삭제 → 오버레이 → `compileJava`(실패 3, stage main) → `compileTestJava`(실패 3, stage test) → `archTest` → JSON 검사(`tests_executed==7`, R1~R7 키, `primary_rules`) → 0/1.
  - WHY `src/test` 삭제: 측정 대상은 main 뿐(`DoNotIncludeTests`). 에이전트 테스트 컴파일 오류가 구조 측정을 막지 않게. 실측으로 확인.
- **검증** `experiment/scripts/smoke.sh`: 1~7단계 전부 PASS(38초), 결과 표·diff 원문·grep 로그·훅 시간은 `experiment/README.md`. 8단계는 `SMOKE_LIVE=1` 일 때만.
- `.gitignore`: `/experiment/seed/`, `/experiment/ws-*/`, `/experiment/out-*.json`, `/experiment/smoke-out/`. `CLAUDE.md`: "Experiment Harness" 절.

## Notes
- **채점기 동결과 충돌하는 발견 (결정 필요)**: L2·L3 에 복사되는 원본 `HexagonalRules.java` 주석에 `violation-fixtures`, `실험 결과 JSON의 키`, `실험 설계자 결정`, `REPORT.md` 가 있다. L2·L3 에이전트만 실험 맥락을 알게 되어 하네스 수준과 교락된다. 동결 상태라 수정하지 않았다. 권고: 동결을 한 번 풀어 주석의 실험 용어만 지우는 커밋(규칙 로직 불변, `ViolationDetectionTest` 로 회귀 확인).
- `harness <level>` 커밋 메시지(지시서 지정)가 에이전트 `git log` 에 보인다. 러너 단계에서 중립 메시지로 바꿀지 결정 필요.
- 8단계 실측(Haiku): 지시서 프롬프트에는 에이전트가 **L1 산문만 보고 편집 없이 거부** → 훅 미발화. 별도 확인 호출(편집 후 원복)에서 훅 발화·절대 경로 처리·exit 2 stderr 전달을 실제 Claude Code 로 확인. 합계 비용 약 $0.008.
- 훅은 Edit/Write 에만 반응한다. `Bash` 로 파일을 쓰면 우회된다(L2 의 측정상 한계, README 알려진 한계 3).
- 실험 설계 문서의 "L1 예시 문구" 원문이 저장소에 없어 대조하지 못했다. 사전 등록 전 대조 필요.
- 러너 전제(README): 턴 시작마다 `.harness/retry-count` 삭제, 워크스페이스는 arch-bench 밖(상위 CLAUDE.md 로딩), 사용자 수준 설정 격리.

### Follow-up — 2026-10-15

**Request**: 에이전트 워크스페이스는 `~/Project` 아래에 디렉터리를 하나 만들어 거기서 작업한다(사용자 메시지의 "Proejct" 는 `/Users/kyeom/Project` 의 오타로 판단).

**이전 결정: X → 변경: Y, 사유: Z**
- 이전 결정: 워크스페이스는 "arch-bench 밖, 예: `mktemp -d`". 8단계는 `$TMPDIR` 아래에서 돌림.
- 변경: 워크스페이스 루트 = `/Users/kyeom/Project/arch-bench-ws` (생성함, git 저장소 아님). 스크립트에는 경로를 하드코딩하지 않고 환경 변수 `ARCH_WS_ROOT` 로 받는다(미지정 시 `$TMPDIR`).
- 사유: 사용자 결정. 이 경로의 상위(`~/Project`, `~`, `/Users`, `/`)에는 `CLAUDE.md`·`CLAUDE.local.md`·`.claude/` 가 없음을 확인했다(`~/.claude` 는 사용자 설정 위치라 별도).

**Changes**
- `experiment/scripts/smoke.sh` 8단계: `ARCH_WS_ROOT` 아래 `smoke-live.XXXXXX/ws-L2` 를 쓴다. 루트부터 `/` 까지 `CLAUDE.md`·`CLAUDE.local.md`·`.claude`(`$HOME/.claude` 제외)가 하나라도 있으면 실행을 거부한다.
  - 확인: `ARCH_WS_ROOT=<repo>/experiment/ws-bad` → "…/encantado/CLAUDE.md would leak…" 로 거부. `ARCH_WS_ROOT=/Users/kyeom/Project/arch-bench-ws` → 1~7단계 PASS, 8a 결과는 이전과 같음(편집 없이 거부, 훅 미발화, $0.0036). init 이벤트의 cwd 가 새 루트 아래임을 확인.
- `experiment/README.md` 러너 전제 2·3번과 8단계 절, `CLAUDE.md` Experiment Harness 절에 워크스페이스 루트를 적었다.

**Notes**
- 사용자 수준 설정은 여전히 격리가 필요하다. `~/.claude/settings.json` 에 사용자 훅 13종(PostToolUse 포함)·플러그인, `~/.claude/skills/` 에 스킬 8개가 있다. `--setting-sources project` 는 settings 만 뺀다. 러너는 전용 `CLAUDE_CONFIG_DIR` 사용을 검토할 것.
- `/Users/kyeom/Project/arch-bench-ws/smoke-live.*` 는 smoke 8단계가 남긴 것으로, 지워도 된다.
