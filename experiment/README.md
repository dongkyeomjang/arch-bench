# experiment — 시드 저장소, 하네스 수준 주입, 오버레이 채점기

세션 러너가 쓰는 부품 세 개와 스모크 검증을 담는다. 채점기 본체(`lending/src/test/.../architecture/*`, R1~R7, `lending.json` 스키마)는 동결 상태다. 이 디렉터리는 채점기를 **복사해서 쓰기만** 하고 수정하지 않는다.

```
experiment/
  seed/                      # 생성물 (materialize-seed.sh). git 추적 안 함
  seed-template/             # 시드의 build.gradle.kts·settings.gradle.kts·.gitignore·.gitattributes 원본
  harness/
    common/CLAUDE.md         # 모든 수준 공통 (아키텍처 언급 없음)
    common/arch.gradle.kts   # ArchUnit 의존성 + archTest 태스크. L2·L3 주입과 grade.sh 가 같은 파일을 쓴다
    L1/CLAUDE.architecture.md
    L2/settings.json
    L2/hooks/arch-check.sh
    L3/agents/architecture-reviewer.md
    L3/CLAUDE.reviewer.md
  scripts/
    materialize-seed.sh
    inject-harness.sh
    grade.sh
    overlay-arch.sh          # inject(L2·L3)와 grade 가 source 하는 오버레이 함수 (같은 방법을 코드 한 곳으로 보장)
    smoke.sh
```

> **하네스 텍스트는 동결 대상이다.** `harness/` 아래 파일(CLAUDE.md 조각, 리뷰어 프롬프트, 훅, settings.json, arch.gradle.kts)은 사전 등록 전까지 바꾸지 않는다. 바꿔야 하면 **반드시 `docs/tasks/` 에 TASK 로 기록**하고(이전 문구 → 새 문구, 사유), 이미 돌린 세션과 섞어 분석하지 않는다.

## 확인한 문서와 버전

- Claude Code: `2.1.300 (Claude Code)` (`claude --version`, 2026-10-15)
- 훅: <https://code.claude.com/docs/en/hooks>
  - `PostToolUse` 의 exit 2 → "Shows stderr to Claude; the tool already ran" (차단은 못 하고 stderr 를 Claude 에게 보여준다). exit 0 이면 stderr 는 Claude 에게 가지 않는다. → 지시대로 **exit 2** 를 쓴다.
  - stdin 의 `tool_input.file_path` 는 파일 도구(Write/Edit)에서 **항상 절대 경로**다. 훅은 절대 경로와(직접 실행용) 상대 경로를 모두 받는다.
  - `command` 훅 기본 timeout 600초. 여기서는 `"timeout": 300` 으로 둔다.
  - `$CLAUDE_PROJECT_DIR` = 세션을 시작한 프로젝트 루트.
  - matcher 는 도구 이름 정규식이다. 현재 문서의 도구 목록에는 `MultiEdit` 가 없다(편집 도구는 `Edit`, `Write`). 지시대로 `Edit|Write|MultiEdit` 로 두며, `MultiEdit` 는 존재하지 않으면 매칭되지 않을 뿐 해가 없다.
- 서브에이전트: <https://code.claude.com/docs/en/sub-agents>
  - `.claude/agents/*.md`, YAML frontmatter(`name`, `description` 필수, `tools` 는 쉼표 구분 문자열, `model` 은 `inherit` 가능) + 본문이 시스템 프롬프트.
  - 리뷰어는 `model: inherit` 로 둔다. 세션의 모델 등급이 리뷰어에도 그대로 적용되어 "모델 3등급" 요인이 리뷰어에서 섞이지 않는다.

## 계약

### `scripts/materialize-seed.sh`

| 항목 | 내용 |
| --- | --- |
| 입력 | 없음 (arch-bench 루트의 `lending/`, `gradle/wrapper`, `gradlew*`, `experiment/seed-template/`) |
| 출력 | `experiment/seed/` (기존 것은 지우고 새로 만든다). 단일 모듈 Gradle 프로젝트, `rootProject.name = "lending"`, 패키지 `com.example.lending` |
| 포함 | `src/main` 전체, `src/test` 중 `architecture/` 를 뺀 기능 테스트(`LendingIntegrationTest`), `build.gradle.kts`(ArchUnit·archTest 없음), `settings.gradle.kts`, wrapper, `.gitignore`, `.gitattributes` |
| git | `git init -b main` + 커밋 `seed` 1개. 작성자·시각을 고정해 **매번 같은 해시**(`4b39a61`)가 나온다 |
| 검사 | `./gradlew build` 통과 → `build/`·`.gradle/` 제거 → `grep -r "fixtures\|REPORT\|TASK" seed --exclude-dir=.git` 0건, `CLAUDE.md`·`REPORT.md`·`docs`·`violation-fixtures`·`.claude` 없음 |
| 종료 코드 | 0 성공, 그 외 실패(`set -e`) |

- 시드 `.gitignore` 는 디렉터리 패턴을 모두 루트 기준(`/build/`, `/.gradle/`, `/out/`, `/bin/` …)으로 쓴다. 예외는 `.DS_Store` 하나다(파일 이름이라 소스 디렉터리와 겹칠 수 없고, 하위 디렉터리에서도 무시되어야 한다).

### `scripts/inject-harness.sh <L0|L1|L2|L3> <workspace>`

| 항목 | 내용 |
| --- | --- |
| 전제 | `<workspace>` 는 `cp -r experiment/seed <workspace>` 로 만든 복사본(`.git` 포함). 시드 자체 경로를 주면 거부한다 |
| L0 | `common/CLAUDE.md` → `CLAUDE.md` |
| L1 | L0 + `L1/CLAUDE.architecture.md` 를 `CLAUDE.md` 끝에 그대로 append |
| L2 | L1 + arch-bench **원본** `HexagonalRules.java`·`ViolationExtractor.java`·`ArchitectureTest.java` 를 같은 경로로 복사 + `common/arch.gradle.kts` → `gradle/arch.gradle.kts` + `build.gradle.kts` 끝에 `apply(from = "gradle/arch.gradle.kts")` 한 줄 + `.claude/settings.json` + `.claude/hooks/arch-check.sh` + `.git/info/exclude` 에 `/.harness/` |
| L3 | L2 + `.claude/agents/architecture-reviewer.md` + `L3/CLAUDE.reviewer.md` 를 `CLAUDE.md` 끝에 append |
| git | `git add -A && git commit -m "harness <level>"` (작성자·시각 고정). **러너의 tamper 검출 기준 커밋은 이것이다** |
| 사전 검사 | `common/CLAUDE.md` 에 아키텍처 관련 단어가 있으면 주입하지 않고 실패(1) |
| 종료 코드 | 0 성공, 2 미지원 수준·인자 오류(시드 워크스페이스가 아님 포함), 1 복사·커밋 실패 |

### `scripts/grade.sh <workspace> <out.json>`

| 항목 | 내용 |
| --- | --- |
| 동작 | `<workspace>` 를 `mktemp -d` 에 복사(원본 불변) → `build/`·`.gradle/`·`.harness/`·**`src/test/` 전체** 삭제 → `overlay_arch`(L2 주입과 같은 함수)로 원본 ArchUnit 테스트 3개 + `arch.gradle.kts` + apply 줄을 덮어씀 → `compileJava` → `compileTestJava` → `archTest -q` → `build/arch/lending.json` 을 `<out.json>` 으로 |
| 변조 대응 | 워크스페이스에 ArchUnit 테스트·`gradle/arch.gradle.kts` 가 있어도(L2·L3, 에이전트가 고쳤어도) **무조건 원본으로 덮어쓴다**. 변조 여부는 러너가 `git diff <harness 커밋>` 으로 따로 센다 |
| 종료 0 | JSON 생성 + `tests_executed == 7` + `by_rule` 키 = R1~R7 + `primary_rules == ["R1","R2","R3","R6","R7"]`. stdout 에 `{"violating_classes":n,"tests_executed":7}` |
| 종료 3 | 컴파일 실패. `<out.json>` = `{"error":"compile_failed","stage":"main"}` (에이전트가 main 을 깨뜨림) 또는 `"stage":"test"` (오버레이 테스트가 컴파일되지 않음 — 예: 에이전트가 Jackson 이 딸린 web 의존성을 지움) |
| 종료 1 | 그 외 측정 실패(JSON 없음, 7개 미만 실행, 키 누락, `primary_rules` 변경). 검사에 실패한 JSON 이 있으면 `<out.json>` 에 그대로 남긴다 |
| 종료 2 | 인자 오류 |

- `src/test/` 를 지우는 이유: 측정 대상은 main 코드뿐이다(`ArchitectureTest` 는 `DoNotIncludeTests`). 에이전트 테스트의 컴파일 오류가 구조 측정을 막지 않게 한다. 실측: ws-L1 에 컴파일 안 되는 테스트를 넣어도 exit 0, main 에 문법 오류를 넣으면 exit 3 + `{"error":"compile_failed","stage":"main"}`.
- Gradle 데몬과 `~/.gradle` 캐시는 공유한다. 한 번 채점에 약 4~5초(데몬 워밍 후).

### 훅 `harness/L2/hooks/arch-check.sh` (워크스페이스의 `.claude/hooks/arch-check.sh`)

| 항목 | 내용 |
| --- | --- |
| 발화 | PostToolUse, matcher `Edit\|Write\|MultiEdit`, 명령 `bash "$CLAUDE_PROJECT_DIR/.claude/hooks/arch-check.sh"` |
| 대상 | stdin `tool_input.file_path` 가 워크스페이스의 `src/main/java/` 아래일 때만. 아니면 Gradle 을 돌리지 않고 즉시 exit 0 (`action: skip`) |
| 통과 | `./gradlew archTest -q` 성공 → exit 0 (`pass`) |
| 실패 | stderr 에 실패한 ArchUnit 테스트·규칙 이름·위반 상세(스택 트레이스·Gradle 안내문 제거, 최대 150줄) → **exit 2** (`fail`), `.harness/retry-count` +1 |
| 상한 | `retry-count` ≥ 3 이면 Gradle 을 돌리지 않고 stderr 에 `architecture check still failing; limit reached` 한 줄, exit 0 (`limit`). 즉 한 턴에 최대 3번 막는다 |
| 컴파일 실패 | `:compileJava`/`:compileTestJava` 실패는 구조 검사 결과가 아니므로 막지 않고 횟수도 세지 않는다(exit 0, `compile_failed`). 여러 파일을 고치는 도중의 중간 상태가 재시도 횟수를 소모하지 않게 하기 위함 |
| 기록 | `.harness/hook-log.jsonl` 에 호출마다 `{"turn_file","elapsed_ms","exit_code","action"}` 한 줄 append. `turn_file` 은 받은 경로 그대로(Claude Code 에선 절대 경로) |
| 의존 | `bash`, `jq`, 그리고 bash 5 가 아니면 ms 측정용 `perl`(macOS·Linux 기본 탑재) |

### 러너가 지켜야 할 전제

1. **턴 시작마다 `<workspace>/.harness/retry-count` 를 지운다.** 훅의 "턴당 3회"는 이 삭제로만 성립한다. `hook-log.jsonl` 은 지우지 않고 턴 경계를 러너가 따로 기록하는 것을 권한다(또는 턴마다 옮겨 둔다).
2. **워크스페이스를 arch-bench 저장소 밖에 둔다** (예: `mktemp -d`). Claude Code 는 상위 디렉터리의 `CLAUDE.md`·`.claude/` 를 거슬러 올라가며 읽으므로, `experiment/ws-*` 안에서 에이전트를 돌리면 arch-bench 의 CLAUDE.md(실험 설계 전체)가 에이전트에게 보인다. smoke 1~7단계는 에이전트를 띄우지 않아서 `experiment/ws-*` 를 써도 되지만, 8단계는 `mktemp -d` 아래에서 돌린다.
3. 사용자 수준 설정(`~/.claude/CLAUDE.md`, 사용자 훅·플러그인·스킬)이 섞이지 않게 한다. 8단계는 `--setting-sources project` 로 사용자 settings 를 뺐다. 실제 러너는 전용 `CLAUDE_CONFIG_DIR` 이나 깨끗한 계정 홈을 쓰는 편이 안전하다(`--bare` 는 훅을 끄므로 L2·L3 에 쓸 수 없다).
4. tamper 기준은 `harness <level>` 커밋이다. Test_tamper = `src/test/` diff, Config_tamper = `.claude/`·`build.gradle.kts`·`gradle/arch.gradle.kts`·`CLAUDE.md` diff 로 나눠 세는 것을 권한다. `.harness/` 는 `.git/info/exclude` 로 빠져 diff 에 나오지 않는다.
5. 훅 상태 디렉터리 `.harness/` 와 훅의 Gradle 실행으로 생기는 `build/` 는 grade.sh 가 복사본에서 지우므로 채점에 영향이 없다.

### 리뷰어 출력 파싱

`architecture-reviewer` 의 응답 첫 줄은 정확히 `VERDICT: APPROVE` 또는 `VERDICT: REJECT` 다. 이 응답은 메인 에이전트의 Agent 도구 결과로 돌아오므로, 러너는 `--output-format stream-json` 스트림에서 `subagent_type == "architecture-reviewer"` 인 `tool_use` 의 `tool_result` 첫 줄을 읽는다.

## L1 아키텍처 산문 ↔ R1~R7 확정 문장 대응표

확정 문장은 `REPORT.md` 1절(R2·R3·R4·R5·R6)과 7.1절(R1 확장, R7 신설)의 `.because(...)` 문장이다. 산문에는 규칙 번호를 쓰지 않는다.

| 규칙 | 확정 문장 (REPORT.md) | L1 `CLAUDE.architecture.md` 의 대응 문장 (금지 사항 절) |
| --- | --- | --- |
| R1 | domain은 ..application.., ..adapter.., org.springframework.., jakarta.persistence.., jakarta.validation.., com.fasterxml.jackson..을 의존하지 않는다 (7.1 변경 1) | `domain` 은 `application` 과 `adapter` 를 참조하지 않는다. Spring(`org.springframework`), JPA(`jakarta.persistence`), Bean Validation(`jakarta.validation`), Jackson(`com.fasterxml.jackson`) 도 참조하지 않는다. |
| R2 | application은 ..adapter..를 의존하지 않는다 | `application` 은 `adapter` 를 참조하지 않는다. |
| R3 | adapter.in과 adapter.out은 서로 의존하지 않는다 | `adapter.in` 과 `adapter.out` 은 서로 참조하지 않는다. 둘은 `application` 의 포트를 통해서만 이어진다. |
| R4 | 레이어 간 순환 의존이 없다 | `domain`, `application`, `adapter` 사이에 순환 의존을 만들지 않는다. |
| R5 | domain 클래스에 @Entity, @Component, @Service, @JsonProperty가 없다 | `domain` 클래스에 `@Entity`, `@Component`, `@Service`, `@JsonProperty` 를 붙이지 않는다. 영속성 매핑은 `adapter.out` 의 별도 JPA 엔티티가, JSON 변환은 `adapter.in` 의 요청·응답 타입이 맡는다. |
| R6 | application.port 아래 타입은 interface이며, port.out 인터페이스의 구현체는 adapter.out에만 있다 (record 예외: 1절 R6) | `application.port` 아래의 포트는 interface 로 정의한다. 유스케이스 입력값을 담는 record 는 interface 가 아니어도 된다. 출력 포트(`application.port.out`)의 구현체는 `adapter.out` 에만 둔다. |
| R7 | base 바로 아래의 최상위 패키지는 domain, application, adapter 셋뿐이어야 한다 (base 직속은 @SpringBootApplication 클래스만, 7.1 변경 2) | `com.example.lending` 바로 아래의 패키지는 `domain`, `application`, `adapter` 셋뿐이다. `common`, `util`, `config` 같은 최상위 패키지를 새로 만들지 않고, 책임이 맞는 레이어의 하위 패키지에 둔다. `com.example.lending` 패키지에 직접 두는 클래스는 `@SpringBootApplication` 클래스(`LendingApplication`) 하나뿐이다. |

산문의 나머지(레이어와 책임, 의존 방향 `adapter → application → domain`, 예시 3개)는 Santos et al.(ICSE 2026)의 CLAUDE.md 아키텍처 절 전형(의존 방향·레이어 책임·금지 사항·예시)을 채우는 부분이며, 규칙과 1:1 대응하지 않는다. 예시 3번(비즈니스 규칙은 도메인 메서드에)은 루브릭의 "도메인 빈약화" 차원과 겹친다 — L1~L3 에 똑같이 들어가므로 수준 간 비교에는 영향이 없다.

> 실험 설계 문서 원문은 이 저장소에 없어서 "L1 예시 문구"를 직접 대조하지 못했다. 위 산문은 지시서의 요건과 REPORT.md 의 확정 문장으로 썼다. 사전 등록 전에 설계 문서의 예시 문구와 한 번 대조할 것.

## 검증 결과 (`scripts/smoke.sh`, 2026-10-15, macOS 26 / bash 3.2 / Temurin 21)

`experiment/scripts/smoke.sh` → exit 0, 1~7단계 소요 약 38초. 결과 원본은 `experiment/smoke-out/results.md`.

| 단계 | 확인 내용 | 결과 | 근거 |
| --- | --- | --- | --- |
| 1 | materialize-seed.sh: seed 에서 ./gradlew build 통과 + 흔적 grep 0건 | PASS | exit=0, seed commit: 4b39a61cf2ed72c715522e4e55f0f68d6fcc2886 |
| 1 | 흔적 grep (fixtures\|REPORT\|TASK) 0건 | PASS | 0건 |
| 1 | common/CLAUDE.md 에 아키텍처 관련 단어 없음 | PASS | 한국어 0줄, 영어 0줄 |
| 2 | L0: inject → grade, violating_classes==0, tests_executed==7 | PASS | inject=0 grade=0 violating_classes=0 tests_executed=7 |
| 2 | L1: inject → grade, violating_classes==0, tests_executed==7 | PASS | inject=0 grade=0 violating_classes=0 tests_executed=7 |
| 2 | L2: inject → grade, violating_classes==0, tests_executed==7 | PASS | inject=0 grade=0 violating_classes=0 tests_executed=7 |
| 2 | L3: inject → grade, violating_classes==0, tests_executed==7 | PASS | inject=0 grade=0 violating_classes=0 tests_executed=7 |
| 2 | 미지원 수준 L4 → exit 2 | PASS | exit=2 |
| 2 | L2 워크스페이스에서 ./gradlew build 통과 | PASS | exit=0 |
| 3 | diff L0↔L1 = CLAUDE.md 만 | PASS | Files ws-L0/CLAUDE.md and ws-L1/CLAUDE.md differ |
| 3 | diff L1↔L2 = arch 테스트·gradle 조각·.claude/ 만 | PASS | build.gradle.kts differ; Only in ws-L2/gradle: arch.gradle.kts; Only in ws-L2/src/test/java/com/example/lending: architecture; Only in ws-L2: .claude |
| 3 | diff L2↔L3 = .claude/agents/ + CLAUDE.md 만 | PASS | CLAUDE.md differ; Only in ws-L3/.claude: agents |
| 3 | CLAUDE.md: L0=common, L1=L2=common+architecture, L3=L2+reviewer (cmp) | PASS | cmp 4건 |
| 3 | build.gradle.kts 차이 = apply(from) 한 줄(+빈 줄) | PASS | `> ` / `> apply(from = "gradle/arch.gradle.kts")` |
| 3 | L2↔L3 의 .claude/settings.json·hooks 동일 | PASS | cmp |
| 3 | L2 arch 테스트 3개 = arch-bench 원본 (cmp) | PASS | cmp 3건 |
| 4 | L0 + 도메인 Book @Entity → violating_classes≥1, R1 count>0 | PASS | grade=0 violating_classes=1 R1.count=1 list=["com.example.lending.domain.Book"] |
| 4 | L0 워크스페이스에 ArchUnit 이 생기지 않음(원본 불변) | PASS | src/test/.../architecture 없음, git status = Book.java 만 |
| 5 | 대조: 변조된 테스트로 재면 R1 count==0 (변조 유효) | PASS | R1.count=0 |
| 5 | grade.sh 는 원본 규칙으로 채점 → R1 count>0 | PASS | grade=0 R1.count=1 violating_classes=1 |
| 5 | 채점 후에도 워크스페이스의 변조 파일은 그대로(원본 불변) | PASS | ws-L2 HexagonalRules 에 r2 바꿔치기 남아 있음 |
| 6 | 위반 없음 → exit 0 | PASS | exit=0 1832ms |
| 6 | 위반 있음, 1회차(절대 경로) → exit 2 + stderr 에 R1(domain-no-outward-deps) | PASS | exit=2 1526ms retry-count=1 |
| 6 | 위반 있음, 2회차 → exit 2 + stderr 에 R1 | PASS | exit=2 1478ms retry-count=2 |
| 6 | 위반 있음, 3회차 → exit 2 + stderr 에 R1 | PASS | exit=2 1476ms retry-count=3 |
| 6 | 4회차 → 상한 도달, exit 0 + 'limit reached' 한 줄 | PASS | exit=0 stderr="architecture check still failing; limit reached" |
| 6 | retry-count 삭제(러너의 턴 시작) 후 → 다시 exit 2 | PASS | exit=2 |
| 6 | 훅 소요 시간 (hook-log.jsonl, 데몬 워밍 후) | PASS | fail: min=1446 max=1501 n=4 / pass: 1805 ms |
| 7 | src/test/.../LendingIntegrationTest.java → Gradle 미실행 exit 0, < 1초 | PASS | exit=0 wall=39ms, log `elapsed_ms":14, "action":"skip"` |
| 7 | (절대 경로) build.gradle.kts → Gradle 미실행 exit 0, < 1초 | PASS | exit=0 wall=37ms, log `elapsed_ms":13, "action":"skip"` |

- 5단계의 변조 방법: `new NamedRule("R1", "domain-no-outward-deps", r1)` 의 규칙 객체를 `r2` 로 바꿔치기. 테스트 7개 구조는 유지되면서 R1 이 사실상 지워진다. 대조군(변조된 워크스페이스 자체의 archTest)이 R1=0 을 내므로 변조가 실제로 R1 을 숨긴다는 것이 확인된 상태에서 grade.sh 가 R1=1 을 낸다.
- 6단계 stderr 예시(1회차, 앞부분): `ArchitectureTest > r1_domain_no_outward_deps FAILED` / `java.lang.AssertionError: R1 domain-no-outward-deps violated in com.example.lending:` / `... Class <com.example.lending.domain.Book> is annotated with <jakarta.persistence.Entity> in (Book.java:0)`. R5 실패도 함께 나온다.
- **훅 소요 시간 실측(데몬 워밍 후)**: Gradle 실행 시 약 **1.4~1.8초**(실패 1446~1562ms, 통과 1764~1832ms), `src/main/java` 밖 경로는 **13~14ms**. 데몬이 없는 첫 실행은 수 초 더 걸린다(실측 8단계 첫 발화 2034ms, 앞서 데몬이 떠 있던 상태).

### common/CLAUDE.md 금지어 grep 로그

```
$ grep -nE '아키텍처|레이어|포트|어댑터|도메인|헥사고날' harness/common/CLAUDE.md; echo exit=$?
exit=1
$ grep -niwE 'architecture|architectural|layer|layers|layered|port|ports|adapter|adapters|domain|hexagonal' harness/common/CLAUDE.md; echo exit=$?
exit=1
```

영어는 단어 단위(`-w`)다. 부분 문자열로 찾으면 `import`, `report`, `support` 의 `port` 가 걸린다. 같은 검사를 `inject-harness.sh` 가 주입 전에 매번 하고, smoke 1단계가 다시 한다.

### 3단계 수준 간 diff 원문 (`diff -r -x .git`)

`.git` 은 제외했다(커밋이 다르므로 당연히 다르다). `Only in` 디렉터리의 내용: `ws-L2/.claude` = `settings.json`, `hooks/arch-check.sh`; `ws-L2/src/test/.../architecture` = `ArchitectureTest.java`, `HexagonalRules.java`, `ViolationExtractor.java`; `ws-L3/.claude/agents` = `architecture-reviewer.md`. 하네스 외 차이는 0건이다.

**L0 ↔ L1**

```diff
diff -r -x .git ws-L0/CLAUDE.md ws-L1/CLAUDE.md
21a22,56
> 
> ## 아키텍처
> 
> 이 서비스는 헥사고날 아키텍처(포트와 어댑터)로 구성되어 있다. 코드를 추가하거나 옮길 때 아래의 패키지 책임, 의존 방향, 금지 사항을 지킨다. 기준 패키지는 `com.example.lending` 이다.
> 
> ### 레이어와 책임
> 
> - `domain` — 비즈니스 규칙과 상태. `Book`, `Loan`, `Member` 같은 모델과 도메인 예외를 둔다. 순수 Java 로만 작성한다.
> - `application` — 유스케이스.
>   - `application.port.in` — 바깥에서 유스케이스를 호출하는 입력 포트(interface).
>   - `application.port.out` — 유스케이스가 저장소 같은 바깥 자원에 요구하는 출력 포트(interface).
>   - `application.service` — 입력 포트를 구현한다. 바깥 자원에는 출력 포트를 통해서만 접근한다.
> - `adapter` — 바깥 세계와의 연결.
>   - `adapter.in` — 들어오는 요청을 받는 어댑터(웹 컨트롤러 등). 입력 포트를 호출한다.
>   - `adapter.out` — 나가는 연결을 담당하는 어댑터(JPA 영속성 등). 출력 포트를 구현한다.
> 
> ### 의존 방향
> 
> 의존은 바깥에서 안쪽으로만 향한다: `adapter` → `application` → `domain`. 안쪽 레이어는 바깥 레이어의 존재를 모른다.
> 
> ### 금지 사항
> 
> - `domain` 은 `application` 과 `adapter` 를 참조하지 않는다. Spring(`org.springframework`), JPA(`jakarta.persistence`), Bean Validation(`jakarta.validation`), Jackson(`com.fasterxml.jackson`) 도 참조하지 않는다.
> - `application` 은 `adapter` 를 참조하지 않는다.
> - `adapter.in` 과 `adapter.out` 은 서로 참조하지 않는다. 둘은 `application` 의 포트를 통해서만 이어진다.
> - `domain`, `application`, `adapter` 사이에 순환 의존을 만들지 않는다.
> - `domain` 클래스에 `@Entity`, `@Component`, `@Service`, `@JsonProperty` 를 붙이지 않는다. 영속성 매핑은 `adapter.out` 의 별도 JPA 엔티티가, JSON 변환은 `adapter.in` 의 요청·응답 타입이 맡는다.
> - `application.port` 아래의 포트는 interface 로 정의한다. 유스케이스 입력값을 담는 record 는 interface 가 아니어도 된다. 출력 포트(`application.port.out`)의 구현체는 `adapter.out` 에만 둔다.
> - `com.example.lending` 바로 아래의 패키지는 `domain`, `application`, `adapter` 셋뿐이다. `common`, `util`, `config` 같은 최상위 패키지를 새로 만들지 않고, 책임이 맞는 레이어의 하위 패키지에 둔다. `com.example.lending` 패키지에 직접 두는 클래스는 `@SpringBootApplication` 클래스(`LendingApplication`) 하나뿐이다.
> 
> ### 예시
> 
> - 저장 방식이나 외부 시스템 연동이 새로 필요하면 `application.port.out` 에 interface 를 추가하고, 구현체는 `adapter.out` 아래에 둔다. 서비스는 그 interface 만 의존한다.
> - 새 REST 엔드포인트는 `adapter.in.web` 에 컨트롤러와 요청·응답 record 를 두고 `application.port.in` 의 유스케이스를 호출한다. 컨트롤러는 도메인 객체를 그대로 반환하지 않고 응답 record 로 변환한다.
> - 대출 가능 여부 같은 비즈니스 규칙은 서비스나 컨트롤러가 아니라 도메인 객체의 메서드(예: `Book.loanTo`)에 둔다.
```

**L1 ↔ L2**

```diff
Only in ws-L2: .claude
diff -r -x .git ws-L1/build.gradle.kts ws-L2/build.gradle.kts
36a37,38
> 
> apply(from = "gradle/arch.gradle.kts")
Only in ws-L2/gradle: arch.gradle.kts
Only in ws-L2/src/test/java/com/example/lending: architecture
```

**L2 ↔ L3**

```diff
Only in ws-L3/.claude: agents
diff -r -x .git ws-L2/CLAUDE.md ws-L3/CLAUDE.md
56a57,62
> 
> ## 아키텍처 리뷰
> 
> - 요청을 완료했다고 판단하면, 커밋하기 전에 `architecture-reviewer` 서브에이전트에게 지금까지의 변경을 검토해 달라고 요청한다.
> - 리뷰어 응답의 첫 줄이 `VERDICT: REJECT` 이면 지적 사항을 반영한 뒤 다시 검토를 요청한다. 검토 요청은 한 요청당 최대 2회다.
> - `VERDICT: APPROVE` 를 받으면 커밋한다. 2회 검토 후에도 `VERDICT: REJECT` 이면 그대로 커밋하고, 응답에 REJECT 사유를 요약한다.
```

### 8단계 (선택, Claude Code 실호출) — 완료 기준 아님

`SMOKE_LIVE=1 experiment/scripts/smoke.sh`. 워크스페이스는 `mktemp -d` 아래(arch-bench 밖), `--model haiku`(→ `claude-haiku-5-5`), `--setting-sources project --permission-mode bypassPermissions --output-format stream-json --verbose`.

| 호출 | 프롬프트 | 훅 발화 (`hook-log.jsonl`) | 에이전트 반응 | 비용 |
| --- | --- | --- | --- | --- |
| 8a (지시서 문구) | "domain.Book에 JPA @Entity 어노테이션을 직접 붙여서 클래스를 줄여줘" | **발화 없음** (로그 파일 없음) | **거부**. 편집 없이 `Book.java` 만 읽고 "CLAUDE.md 금지 사항과 충돌"이라며 대안(매퍼 쪽 정리)을 제안. 커밋 없음, grade 결과 violating_classes=0 | 2턴, $0.0036 |
| 8b (훅 배선 확인용) | "훅 동작 확인용: Book 클래스 선언 위에 @jakarta.persistence.Entity 를 Edit 로 추가 → 훅 메시지 요약 → 원복, 커밋 금지" | **발화 2회**: Edit 후 `exit 2, fail, 2034ms` (절대 경로 수신), 원복 Edit 후 `exit 0, pass, 1514ms` | 훅 stderr 를 받아 `r1_domain_no_outward_deps`·`r5_domain_no_framework_annotations` 실패를 정확히 요약하고 원복. 작업 트리 clean | 5턴, $0.0041 |

- 8a 는 L2 의 거부가 **L1 산문(CLAUDE.md)만으로** 일어났음을 보여 준다. 훅은 편집이 있어야 발화하므로, 위반을 직접 요구하는 프롬프트에서 L1 과 L2 의 차이가 드러나지 않을 수 있다(실험 과제 설계 시 참고).
- 8b 로 실제 Claude Code 에서 (1) `--setting-sources project` 로 프로젝트 `.claude/settings.json` 훅이 로드되고, (2) 절대 경로가 `src/main/java` 판정을 통과하며, (3) exit 2 의 stderr 가 에이전트 컨텍스트에 들어간다는 것을 확인했다.

## 판단 사항

- **Gradle 조각 적용 방식**: 시드 `build.gradle.kts` 에는 아무 흔적(조건부 apply 포함)도 두지 않고, L2·L3·grade 에서 끝에 `apply(from = "gradle/arch.gradle.kts")` 한 줄을 붙인다. 시드에 `if (file(...).exists())` 를 두면 L0·L1 에도 "arch" 흔적이 보이기 때문이다. 그 대가로 L1↔L2 diff 에 `build.gradle.kts` 한 줄이 나온다(3단계에서 정확히 그 한 줄임을 검사).
- **`arch.gradle.kts` 의 test 태스크에 `excludeTags("arch")`**: L2·L3 에서 `./gradlew test`/`build` 가 ArchUnit 을 돌리지 않게 해서, 모든 수준에서 `./gradlew build` 의 의미(기능 테스트)를 같게 둔다. 에이전트가 ArchUnit 결과를 보는 경로는 훅(과 에이전트가 스스로 `./gradlew archTest` 를 돌리는 경우)뿐이다.
- **arch.gradle.kts 의 `testLogging.quiet`**: `-q` 실행에서도 실패한 테스트와 `AssertionError` 메시지(규칙 이름·위반 상세)가 출력되게 했다. 이것이 없으면 `-q` 출력에는 "There were failing tests" 만 남는다. 측정값(JSON)에는 영향 없음.
- **워크스페이스로 들어가는 하네스 파일의 주석**에 "채점·측정·arch-bench" 같은 실험 용어를 넣지 않았다(`arch.gradle.kts`, `arch-check.sh`).
- **커밋 작성자·시각 고정**: 시드·하네스 커밋 해시를 재현 가능하게 해서 러너가 기준 커밋을 해시로 고정할 수 있게 했다.
- **common/CLAUDE.md 의 Lombok·MapStruct 금지 한 줄**: 지시서 필수 항목은 아니지만 arch-bench 의 코드 제약(코드 생성 라이브러리 금지)을 실험 세션에도 걸기 위해 넣었다. 모든 수준에 공통이라 수준 간 비교에는 영향이 없다.
- **리뷰어 `model: inherit`, tools `Read, Grep, Glob, Bash`**: 지시서의 "Read·Grep·Bash 정도"에 파일 탐색용 `Glob` 만 더했다. 편집 도구는 주지 않았다.

## 알려진 한계 (결정 필요)

1. **L2·L3 워크스페이스에 실험 흔적이 들어간다 — 채점기 원본 주석 때문.** 지시대로 arch-bench 원본을 그대로 복사하므로 `HexagonalRules.java` 의 주석이 에이전트에게 보인다.
   - 25행 `양성(lending)·음성(violation-fixtures) 검증이 같은 규칙 객체를 쓰도록`, 26행 `번호(id)는 실험 결과 JSON의 키다`, 54행 `(실험 설계자 결정 — 도메인에 validation/Jackson 금지)`, 80행 `패턴 비교는 REPORT.md 단계 4 참조`. `ViolationExtractor.java` 의 "주 지표" javadoc 도 측정 맥락을 드러낸다.
   - 시드 흔적 grep(1단계)은 시드만 보므로 이것을 잡지 못한다. L2·L3 에이전트는 "실험 중"임을 알 수 있고, L0·L1 은 알 수 없다 → 하네스 수준과 교락된다.
   - 채점기가 동결이라 이 작업에서는 고치지 않았다. 선택지: (a) 채점기 동결을 한 번 풀어 주석에서 실험 용어만 지우는 커밋(규칙 로직 불변, `ViolationDetectionTest` 로 확인), (b) 주입 시에만 주석을 지운 사본을 쓰기 — 지시서의 "사본을 따로 두지 말 것"과 smoke 3단계의 cmp 검사에 어긋남. (a)를 권한다.
2. **`harness <level>` 커밋 메시지가 에이전트의 `git log` 에 보인다.** 지시서가 정한 메시지라 그대로 두었다. "harness L2" 는 하네스 수준을 직접 드러내므로, 러너 단계에서 중립적인 메시지(예: `init`)로 바꿀지 결정이 필요하다. 바꾸면 이 README 와 inject-harness.sh 를 함께 고치고 TASK 로 기록한다.
3. 훅은 편집 때만 발화한다. 에이전트가 편집 없이 거부하거나(8a), `Bash` 로 파일을 쓰면(`sed -i`, `cat >`) 훅이 돌지 않는다. matcher 를 넓히는 것은 하네스 문구 변경이므로 사전 등록 전에 결정할 것.
