# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Task Logging Workflow (필수)

모든 작업은 `docs/tasks/` 디렉토리에 TASK 번호 단위로 기록한다. 이 규칙은 Claude Code 가 이 저장소에서 수행하는 모든 작업에 적용된다.

**디렉토리 구조**
```
docs/tasks/
├── INDEX.md           # 전체 TASK 목록 (번호, 제목, 날짜, 상태, 요약)
└── TASK{N}.md         # 개별 작업 상세 (N = 1, 2, 3, ...)
```

**번호 확정 시점 — 종료 직전**
TASK 번호는 **작업 종료 시점**에 확정한다. 시작 시점엔 "다음 번호가 대략 TASK{N+1} 일 것" 정도로만 잠정 식별하고, 실제 파일 생성과 번호 기재는 저장 직전에 한 번 더 INDEX 를 확인해서 수행한다. 이렇게 두면 여러 세션이 동시에 TASK 를 만들더라도 충돌 창이 초 단위로 줄고, 확정 직전 `git pull` 로 다른 세션의 최신 TASK 를 반영할 수 있다.

**작업 시작 전**
1. `docs/tasks/INDEX.md` 를 읽어 마지막 TASK 번호를 파악한다. 이 번호는 **잠정치** 일 뿐이며 대화 중 "이번은 TASK{N+1} 예정" 식의 호칭 편의용이다.
2. 다음 두 경우는 신규 번호를 쓰지 않는다.
   - **이전 작업의 연속·수정**: 같은 TASK 번호를 재사용하고, 해당 `TASK{N}.md` 하단에 `### Follow-up — YYYY-MM-DD` 섹션으로 이어 기록한다.
   - **단순 질문/조회**: 코드 변경을 수반하지 않는 순수 질문은 로깅하지 않는다.
3. 사용자가 이전 작업의 결정·컨텍스트를 참조해야 한다고 판단되면 **INDEX.md 의 요약에서 관련 TASK 번호를 찾아 해당 `TASK{N}.md` 를 스캔**한다. 추측하지 않는다.

**작업 종료 직전 (번호 확정 & 기록)**
1. 가능하면 `git pull` 로 원격의 최신 TASK 들을 받아 `INDEX.md` 를 최신화한다.
2. INDEX 의 마지막 행 번호를 다시 확인해 이번 작업의 **최종 번호 N** 을 확정한다. 시작 시 잠정 번호와 달라졌다면 확정 번호를 따른다.
3. `docs/tasks/TASK{N}.md` 파일을 아래 템플릿으로 작성한다.
4. `docs/tasks/INDEX.md` 의 표 마지막에 한 줄을 추가한다. (기존 행의 순서 변경·삭제 금지.)
5. 동일 대화 내 Follow-up 인 경우, 새 파일을 만들지 않고 기존 파일 하단에 Follow-up 섹션을 append 한다. INDEX 에는 새 row 를 추가하지 않고, 기존 row 의 "Last Updated" 열만 갱신한다.
6. 커밋 단계에서 파일명·내용의 TASK 번호 충돌이 감지되면(이미 같은 번호의 TASK 파일이 원격에 있음) 자기 파일명과 내부 번호를 **한 칸 올려 rename** 후 INDEX 행을 재작성한다. 번호를 양보하는 쪽이 늦게 커밋하는 쪽이다.

**TASK{N}.md 템플릿**
```markdown
# TASK{N} — {한 줄 제목}

- **Date**: YYYY-MM-DD
- **Status**: completed | in-progress | abandoned
- **Related TASKs**: {참조한 이전 TASK 번호들, 없으면 —}
- **Commits**: {관련 git 커밋 해시들, 없으면 —}

## Request
{사용자가 요청한 내용 요약 — 원문 인용이 짧으면 원문, 길면 요지}

## Context
{이 작업이 왜 필요했는지, 어떤 기존 상태 위에서 진행했는지 — 코드를 다시 읽어도 알 수 없는 배경만 적는다}

## Changes
{변경 사항을 변경 의도 단위로 그룹화해 bullet 로 기술. 파일명과 함수명/클래스명을 포함해 6개월 뒤 읽어도 재현 가능하게}

## Notes
{향후 유의 사항, 남은 숙제, 트레이드오프 — 없으면 생략}
```

**INDEX.md 행 포맷**
```
| TASK{N} | YYYY-MM-DD | completed | {한 줄 요약} | [상세](./TASK{N}.md) |
```

**기록 품질 원칙**
- 변경한 WHAT 만 나열하지 말고, WHY 를 항상 함께 남긴다. 커밋 메시지와 중복되지 않는 배경·트레이드오프를 담는다.
- 결정이 뒤집히면 Follow-up 섹션에 "이전 결정: X → 변경: Y, 사유: Z" 형식으로 남기고 원래 서술은 지우지 않는다.
- 민감 정보(크레덴셜, 내부 URL, 미공개 API 키 등) 는 기록하지 않는다.

## Build & Run Commands

```bash
./gradlew archTest                              # 양방향 아키텍처 검증 (lending 양성 + 픽스처 음성) — 측정기 본체
./gradlew build archTest                        # 기능 테스트 + 양방향 검증 (완료 기준)
./gradlew :lending:test                         # lending 기능 테스트 (통합 테스트, arch 태그 제외)
./gradlew :lending:bootRun                      # lending 로컬 실행 (H2 인메모리)
./gradlew :violation-fixtures:r4Investigation   # R4 슬라이스 패턴 A/B/C 비교 출력 (측정기 아님)
./gradlew :lending:test --tests "클래스명"       # 단일 테스트 실행
```

- Java 21, Spring Boot 3.5.3, Gradle 8.14.3 (Kotlin DSL, wrapper), JUnit 5, ArchUnit 1.3.2, H2 인메모리
- 측정 출력: `lending/build/arch/lending.json` (스키마는 아래 "Measurement JSON"). `archTest` 시작 시 `build/arch/` 를 지우므로, 파일이 없으면 테스트가 실행되지 않은 것이다
- `archTest` 가드: 모듈별로 실행된 arch 테스트가 0개면 `GradleException` 으로 실패한다 (`executed N arch tests` 로그 확인)
- `test` 태스크는 `arch`, `investigation` 태그를 제외한다. 아키텍처 측정은 반드시 `archTest` 로 돌린다

## Experiment Harness (`experiment/`)

```bash
experiment/scripts/materialize-seed.sh                  # lending → experiment/seed/ (독립 Gradle 프로젝트 + git "seed")
experiment/scripts/inject-harness.sh <L0|L1|L2|L3> <ws>  # 시드 복사본에 하네스 수준 주입 + git 새로 init, "Initial commit" 1개
experiment/scripts/grade.sh <ws> <out.json>             # 오버레이 채점 (원본 ArchUnit 덮어쓰기 → lending.json)
experiment/scripts/smoke.sh                             # 1~7단계 검증 (SMOKE_LIVE=1 이면 8단계 Claude Code 실호출)
CLAUDE_CONFIG_DIR=~/.claude-arch-bench python3 experiment/runner/run_session.py \
  --level L2 --model haiku --domain _smoke --rep 1 --ws-root ~/Project/arch-bench-ws-fake --out experiment/runs --agent fake  # 러너 디버깅용
CLAUDE_CONFIG_DIR=~/.claude-arch-bench python3 experiment/runner/run_matrix.py \
  --config experiment/runner/matrix.example.json --ws-root $ARCH_WS_ROOT --out $OUT_ROOT [--dry-run] [--resume]
python3 experiment/runner/summarize.py --out $OUT_ROOT --report experiment/runs/pilot-report.md  # 결과 표 (보고서의 summarize 블록만 갱신)
```

- 계약(입력·출력·종료 코드), 러너 전제, 검증 결과는 `experiment/README.md` 가 정본.
- `experiment/harness/` 텍스트는 사전 등록 전까지 동결. 바꾸면 TASK 로 기록한다.
- 러너는 격리 설정 디렉터리 `CLAUDE_CONFIG_DIR`(이 머신: `~/.claude-arch-bench`, 별도 로그인)가 없으면 실행을 거부한다(종료 5). `tasks/_smoke/` 는 러너 검증용이며 실험 과제가 아니다.
- 과제 `experiment/tasks/lending/turn-NN.md`(에이전트가 받는 프롬프트)에는 함정 표시·아키텍처 용어·구현 방식을 쓰지 않는다. 계약은 `## API` 절의 HTTP 수준만.
- 숨겨진 기능 테스트 `experiment/tests/lending/turn-NN/` 는 HTTP 만 쓴다(애플리케이션 빈 주입·내부 클래스 참조 금지). 바꾸면 참조 구현 `experiment/reference/lending-full/` 에서 턴별 누적 통과를 다시 확인한다(`experiment/README.md` "도서 대출 과제").
- 채점기 3개 파일의 주석에는 실험 용어를 쓰지 않는다(L2·L3 워크스페이스로 복사되어 에이전트에게 보인다). 원래 동결 대상이며 TASK5 에서 주석만 한 번 바꿨다.
- 에이전트 세션 워크스페이스는 **이 저장소 밖**, `/Users/kyeom/Project/arch-bench-ws` 아래에 둔다(`ARCH_WS_ROOT`). Claude Code 가 상위 디렉터리의 이 CLAUDE.md 를 읽기 때문이다.
- **본 실험은 `run_matrix.py` 로만, fake(`--agent fake`/`agent: fake`)는 별도 루트 `~/Project/arch-bench-ws-fake` 에서만 돌린다.** 본 실험 루트에 잔여물이 있으면 claude 매트릭스가 종료 5 로 거부한다(`experiment/README.md` "운영 원칙").
- 워크스페이스 경로는 `$ARCH_WS_ROOT/<무작위 토큰>/lending` 이다. 경로·커밋 메시지(`Initial commit`, `Save work in progress`)에 수준·모델·턴 번호를 넣지 않는다(에이전트가 `pwd`·`ls ..`·`git log` 로 읽는다). 토큰 → 세션 대응은 `$OUT_ROOT/workspace-index.jsonl`(워크스페이스 루트 밖).

## Architecture

이 저장소는 실험의 **초기 저장소**(`lending`)이자 **채점기**(ArchUnit 규칙 R1~R6)다. 규칙의 정밀도가 최우선이다 — 잡아야 할 것을 못 잡거나 잡지 말아야 할 것을 잡으면 실험 전체가 오염된다. 설계 결정과 근거는 `REPORT.md` 에 있다.

```
arch-bench/
├── build.gradle.kts             # subprojects 공통 설정 + archTest 태스크 등록
├── lending/                     # 올바른 헥사고날 참조 구현 (도서 대출)
│   ├── src/main/java/com/example/lending/
│   │   ├── domain/              # 순수 Java (java.* 만 import). Book, Loan, Member, 도메인 예외
│   │   ├── application/
│   │   │   ├── port/in/         # UseCase 인터페이스 + 중첩 command record
│   │   │   ├── port/out/        # 아웃바운드 포트 인터페이스 (도메인 타입만 사용)
│   │   │   └── service/         # UseCase 구현 (@Service, @Transactional, 포트만 의존)
│   │   └── adapter/
│   │       ├── in/web/          # @RestController, 요청/응답 record, WebExceptionHandler
│   │       └── out/persistence/ # JPA 엔티티(도메인과 별개), Spring Data 리포지토리, 매퍼, 포트 구현체
│   └── src/test/java/com/example/lending/architecture/
│       ├── HexagonalRules.java     # 규칙 팩토리: forBasePackage(base) → List<NamedRule>
│       ├── ViolationExtractor.java # 위반 객체 → 원인 클래스 FQN, 측정 JSON 구조(report)
│       └── ArchitectureTest.java   # 양성 검증: 규칙당 @ArchTest 1개(7개) + lending.json 기록
├── violation-fixtures/          # 음성 검증용 축소 픽스처
│   ├── src/main/java/com/example/fixtures/v1 ... v8/
│   └── src/test/java/com/example/fixtures/
│       ├── ViolationDetectionTest.java          # 픽스처별 기대 집합 + 기대 건수 + 기대 원인 클래스 검증
│       └── R4SlicePatternInvestigationTest.java # 단계 4 조사용 (@Tag("investigation"))
└── REPORT.md                    # 규칙별 구현·변경 이유, 검증 결과, 모호 지점, 판단 사항
```

`violation-fixtures` 는 `lending` 의 `archRules` configuration(test 출력 중 `HexagonalRules*`, `ViolationExtractor*` 만 담은 jar)을 통해 **같은 규칙 객체와 추출기**를 쓴다.

## Rules (R1~R7)

| ID | 이름 | 요지 |
| --- | --- | --- |
| R1 | `domain-no-outward-deps` | domain → base.application / base.adapter / org.springframework / jakarta.persistence / jakarta.validation / com.fasterxml.jackson 금지 |
| R2 | `application-no-adapter-deps` | application → base.adapter 금지 |
| R3 | `adapters-independent` | adapter.in ↔ adapter.out 양방향 금지 |
| R4 | `no-layer-cycles` | `slices().matching(base + ".(*)..")` 순환 금지 (패턴 A) |
| R5 | `domain-no-framework-annotations` | domain 의 @Entity/@Component/@Service(클래스), @JsonProperty(클래스·필드·메서드·생성자·파라미터) 금지 |
| R6 | `ports-are-interfaces-implemented-in-adapters` | port 아래 비-record 타입은 interface, port.out 구현체는 adapter.out 에만 |
| R7 | `only-three-top-level-packages` | base 아래 최상위 패키지는 domain/application/adapter 뿐. base 직속은 `@SpringBootApplication` 클래스만 허용 |

- **ID 와 이름은 실험 결과 JSON 의 키다. 바꾸지 않는다.**
- 건수(`count`) 단위는 규칙마다 다르다(R1~R3 의존 엣지, R4 순환, R5 어노테이션 요소, R6·R7 클래스). 규칙 간 합산·비교 금지. 주 지표는 `violating_classes`.
- "도메인에 validation/Jackson 금지"(R1)는 실험 설계자의 결정이다. 토글 가능하게 만들지 않는다.
- 규칙을 추가하면 `ArchitectureTest` 에 규칙당 `@ArchTest` 메서드와 `RULE_IDS_COVERED_BY_THIS_CLASS` 를 함께 갱신한다(안 하면 전 테스트 실패).

## Measurement JSON

```json
{
  "tests_executed": 7,
  "primary_rules": ["R1", "R2", "R3", "R6", "R7"],
  "violating_classes": 0,
  "violating_classes_list": [],
  "by_rule": { "R1": {"count": 0, "classes": []}, "...": "...", "R7": {"count": 0, "classes": []} }
}
```

- **주 지표 `violating_classes` = `primary_rules`(R1·R2·R3·R6·R7) 의 `classes` 합집합 크기.** R4·R5 는 `by_rule` 에만 남고 합집합에서 제외한다 (TASK3 Follow-up):
  - R4 제외: 순환의 정상 방향 쪽 클래스까지 원인으로 잡아 과대 집계한다(v4 의 `LoanPolicy` 는 application → domain 정방향 의존만 가짐). 또 패턴 A 의 R4 는 R1 ∪ R2 에 포함되어, 잘못된 엣지의 출발 클래스는 이미 R1/R2 로 집계된다.
  - R5 제외: 4개 어노테이션이 모두 R1 블랙리스트 패키지라 R5 원인 클래스 ⊂ R1 원인 클래스(R5 ⊂ R1). 넣어도 합집합이 변하지 않지만, 정의상 중복이므로 범위에서 뺀다.
  - 합집합 범위는 `ViolationExtractor.PRIMARY_RULES` 한 곳에서 정하고 JSON 의 `primary_rules` 로 선언한다. 바꾸면 `ViolationDetectionTest` 의 기대 `violating_classes` 와 `primary_rules` 기대값도 함께 바꾼다.
- `classes` = 위반 원인 클래스 FQN(정렬·중복 제거): R1~R3 의존 출발 클래스, R4 순환 참여 클래스 전부(`Cycle` → `Dependency` 출발·도착), R5 어노테이션이 붙은 멤버의 소유 클래스, R6·R7 해당 클래스.
- `by_rule` 에는 **실행된 규칙만** 들어간다(0 으로 채우지 않음). 측정 성공 판정: 파일 존재 && `tests_executed == 7` && R1~R7 키 모두 존재.
- 원인 클래스는 실패 리포트 문자열을 파싱하지 않고 `ViolationExtractor` 가 위반 객체 타입으로 뽑는다. 모르는 타입이 오면 예외를 던진다 — 새 규칙이 새 위반 객체 타입을 만들면 추출기 분기를 추가한다.

## Key Conventions

**규칙·픽스처 수정 원칙**
- 규칙이 픽스처를 못 잡으면 **규칙을 고친다**. 픽스처를 약하게 만들지 않는다.
- `lending` 에서 규칙이 실패하면 먼저 `lending` 이 올바른지 의심한다. 그래도 오탐이면 규칙을 고치되 이유를 `REPORT.md` 에 적는다.
- 규칙을 바꾸면 `ViolationDetectionTest` 의 기대 건수와 기대 원인 클래스를 **위반 상세를 한 건씩 확인한 뒤** 갱신한다. 숫자만 맞추지 않는다.
- 규칙 변경 후에는 해당 규칙을 일부 느슨하게 해서 픽스처가 실패하는지(민감도) 확인한다. `REPORT.md` 의 민감도 표 참조.
- 판단이 필요한 지점은 멈추지 말고 가장 보수적인 쪽을 택한 뒤 `REPORT.md` "판단 사항" 에 적는다.

**코드 제약**
- Lombok, MapStruct 등 코드 생성 라이브러리 금지 (AST 기반 측정 단순화).
- 규칙 팩토리(`HexagonalRules`, `NamedRule`)와 추출기(`ViolationExtractor`) 외에 공통 베이스 클래스·유틸리티·설정 프레임워크·인터페이스/전략 패턴을 만들지 않는다.
- 기존 테스트가 깨지면 `@Disabled` 하지 말고, 무엇이 왜 바뀌어 깨졌는지 `REPORT.md` 에 적고 고친다.
- 테스트 외 의존성을 추가하면 `REPORT.md` "의존성 추가 내역" 에 이유를 적는다.
- 규칙의 어노테이션은 FQN 문자열로 매칭한다 (테스트 코드가 JPA/Spring/Jackson 에 컴파일 의존하지 않게).
- 모든 규칙은 `.because(원래 문장)` + `.allowEmptyShould(true)`. 빈 import 방어는 테스트에서 한다.
- ArchUnit 엔진 테스트에는 `@Tag("arch")` 와 **`@ArchTag("arch")` 를 둘 다** 단다. `@Tag` 만 있으면 `archTest` 가 테스트 0개로 조용히 통과한다.

**lending 도메인/어댑터 관례**
- 도메인 모델: public setter 없음. `register()`/정적 팩토리로 생성, `reconstitute()` 로 영속성에서 복원, 값 검증은 생성자에서. ID 는 도메인에서 생성하는 `UUID`.
- 비즈니스 규칙은 도메인 메서드로 (`Book.loanTo`, `Loan.isOverdue`). `Loan.start` 는 package-private — 대출은 `Book.loanTo` 로만 생성.
- 도메인 예외는 `domain` 에 두고, HTTP 매핑은 `adapter.in.web.WebExceptionHandler` 에서 (`ProblemDetail`).
- 컨트롤러는 유스케이스 인터페이스만 의존하고 도메인 객체를 직접 반환하지 않는다 (응답 record 의 `from()` 으로 변환).
- JPA 엔티티는 도메인과 별개 클래스. 도메인↔엔티티 변환은 어노테이션 없는 package-private 매퍼 클래스 (`BookMapper`, `LoanMapper`).

## Git

- 이 저장소의 커밋 이력은 실험 파이프라인이 `git diff` 로 에이전트의 ArchUnit 테스트 변조(Test_tamper)를 검출하는 기준이다. 기준 커밋을 rewrite(amend/rebase/force push) 하지 않는다.
- `.gitignore` 에 `out/` 같은 이름만의 패턴을 넣지 않는다 — 헥사고날의 `adapter/out`, `port/out` 소스가 무시된다. 루트 기준(`/out/`)으로 쓴다.

## Commit & PR Convention

커밋 제목과 PR 제목은 **항상** 아래 형식을 따른다: `<emoji> <Type>: <설명(한국어)>`

- `Type` 은 첫 글자 대문자 (예: `Feat`, `Fix`, `Docs`, `Setting`, `Refactor`).
- 제목은 명령형/요약형 한 줄. 본문(선택)은 제목과 빈 줄로 구분하고 변경 의도·배경(WHY)을 적는다.
- PR 본문에서는 GitHub PR Emoji Code(`:sparkles:` 등)를 써도 된다.

| Type | Emoji | PR Emoji Code | 설명 |
|------|-------|---------------|------|
| `feat` | ✨ | `:sparkles:` | 새로운 기능 추가 |
| `fix` | 🐛 | `:bug:` | 버그 수정 |
| `docs` | 📝 | `:memo:` | 문서 수정 |
| `style` | 🎨 | `:art:` | 코드 스타일 변경 (동작에 영향 없음) |
| `refactor` | 🔄 | `:recycle:` | 코드 리팩토링 |
| `test` | ✅ | `:white_check_mark:` | 테스트 코드 추가/수정 |
| `chore` | 🔧 | `:wrench:` | 기타 변경 (빌드 스크립트, 패키지 등) |
| `setting` | ⚙️ | `:gear:` | 설정 관련 변경 (CI/CD, 환경설정 등) |
| `design` | 💄 | `:lipstick:` | UI/스타일 변경 |
| `comment` | 💬 | `:bulb:` | 주석 추가/수정 |
| `rename` | ✏️ | `:truck:` | 파일/폴더 이름 변경 |
| `remove` | 🗑️ | `:fire:` | 코드/파일 삭제 |
| `hotfix` | 🚑 | `:ambulance:` | 긴급 수정 |
| `merge` | 🔀 | `:twisted_rightwards_arrows:` | 브랜치 병합 |
| `rollback` | ⏪ | `:rewind:` | 변경 사항 되돌리기 |
| `db` | 🗃 | `:card_file_box:` | 데이터베이스 관련 변경 |

예시:

```
✨ Feat: R6 port.in 구현체 위치 검사 추가
🐛 Fix: @ArchTag 누락으로 archTest가 테스트 0개로 통과하던 문제 수정
✅ Test: v3 픽스처에 adapter.out → adapter.in 역방향 위반 추가
```
