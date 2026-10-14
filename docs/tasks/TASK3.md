# TASK3 — 규칙 보강(R1 확장·R7), 측정 JSON 집계 단위 변경, archTest 가드, git 기준 커밋

- **Date**: 2026-10-14
- **Status**: completed
- **Related TASKs**: TASK1 (규칙·검증 체계), TASK2 (CLAUDE.md)
- **Commits**: e628a41 (1차 기준 커밋), 2차 변경 커밋 (이 문서와 같은 커밋)

## Request
작업 지시 2: REPORT.md 5절의 발견을 반영해 R1 블랙리스트에 `jakarta.validation..`·`com.fasterxml.jackson..` 추가(v8), R7 `only-three-top-level-packages` 신설(v7), `lending.json` 을 "위반 고유 클래스 수" 중심 스키마로 변경(원인 클래스 추출기), archTest 0개 실행 가드, git 초기화 + 1차/2차 커밋, REPORT.md "2차 변경" 절. 1차의 양성·음성·건수 고정·민감도 체계를 새 규칙에도 적용.

## Context
- 규칙별 `count` 는 단위가 달라(의존 엣지/순환/어노테이션 요소/클래스) 합산할 수 없어서 주 지표를 원인 클래스 기준으로 바꿨다.
- 실험 파이프라인이 `git diff` 로 Test_tamper 를 검출하므로 1차 상태의 기준 커밋이 필요했다. 1차 상태는 2차 변경 전에 그대로 커밋했다(재구성 불필요).
- 상세 근거·표는 `REPORT.md` 7절이 정본.

## Changes
- **git**: `.gitignore`, `.gitattributes` 추가 후 1차 상태 커밋(e628a41).
  - WHY `.gitignore` 를 `/out/`, `/*/out/` 로 쓴 이유: `out/` 패턴이 `adapter/out`, `port/out` 의 java 20개를 무시하는 것을 커밋 직전에 발견. 추적 java 수 = 디스크 java 수(55) 확인 후 커밋.
- **R1 확장** (`HexagonalRules`): 블랙리스트에 `jakarta.validation..`, `com.fasterxml.jackson..`. 실험 설계자 결정으로 토글 불가.
  - 부작용: v5 R1 기대 건수 3 → 9 (`@JsonProperty` 1개 = 어노테이션 + 멤버 타입 `JsonProperty$Access`, `OptBoolean` 3 의존 × 2곳).
- **R7 신설** (`HexagonalRules`): `classes().that().resideInAPackage(base..).and(not(base 직속 @SpringBootApplication)).should().resideInAnyPackage(domain, application, adapter)`.
- **픽스처**: v7(`V7Application` 허용, `AppConstants`·`common.DateUtils` 위반, `adapter.common.Mapper` 허용, `domain.Book` → `DateUtils`), v8(`domain.Member` 필드 `@NotNull`). `violation-fixtures` 에 `jakarta.validation-api` 추가.
  - WHY v7 에 `AppConstants`: 지시된 클래스만으로는 R7 1건이라 기대 "2건"과 불일치. 기대를 낮추지 않고 "base 직속 비부트 클래스" 위반 경로를 덮었다.
- **`ViolationExtractor`** (신규, `HexagonalRules` 옆): `violatingClasses(EvaluationResult)` — `Object` 핸들러 + `instanceof` 로 `Dependency`/`JavaAccess`/`Cycle`/`JavaMember`/`JavaClass` 분기, 모르는 타입·원인 0개면 예외. `report(Map<ruleId, EvaluationResult>)` — JSON 구조.
  - WHY 단일 Object 핸들러: 타입별 등록은 미등록 타입 위반을 조용히 버린다.
  - R4 는 `Cycle.convertTo(Dependency.class)` 로 실제 순환 참여 클래스만 집계(슬라이스 전체 근사 불필요).
- **`ArchitectureTest`**: 1개 → 규칙당 `@ArchTest` 7개. 실행 시마다 실행된 규칙만으로 `lending.json` 재작성(Jackson). 규칙 ID 집합 대조로 테스트 메서드 누락 감지.
- **`ViolationDetectionTest`**: 7규칙 × v1~v8, 기대 건수 + 기대 원인 클래스(상대 이름) 고정, 픽스처별 JSON 출력.
- **archTest 가드** (루트 `build.gradle.kts`): `TestListener` 로 루트 suite 실행 수 집계, 0 이면 `GradleException`.
- **문서**: `REPORT.md` 7절(2차 변경), `CLAUDE.md`(R7·JSON 스키마·가드·Git 절).

## Notes
- 민감도: M1(R7 부트 예외 제거 → lending R7 1건 실패), M2(R1 validation 제거 → v8 missed), M3(추출기 Cycle 분기 제거 → 예외), M4(@ArchTag 제거 → 가드 실패, JSON 없음) 모두 잡힘.
- 파이프라인은 `tests_executed == 7` 과 R1~R7 키 존재를 확인해야 한다(>0 만으로는 부분 기록을 못 거름).
- 열린 설계 질문(REPORT 7.5): R5 가 R1 에 완전 포함되어 `violating_classes` 에 기여 0, R4 원인 클래스에 피해자 쪽 클래스 포함, R7 은 하위 패키지(`domain.common` 등)를 막지 못함, 중첩/익명 클래스 정규화 여부.
- 커밋 메시지: 지시(영어)와 CLAUDE.md(한국어 설명) 충돌 → `<emoji> <Type>: <English>` 로 절충.

### Follow-up — 2026-10-15

**Request**: 작업 지시 3 — 주 지표 합집합 범위 변경. `violating_classes_list` 를 R1·R2·R3·R6·R7 의 `classes` 합집합으로, R4·R5 는 `by_rule` 에만 유지. `lending.json` 에 `"primary_rules"` 필드 추가. 음성 검증의 기대 `violating_classes` 재계산·고정. 커밋 1개.

**이전 결정: X → 변경: Y, 사유: Z**
- 이전 결정: `violating_classes` = 모든 규칙(R1~R7) `classes` 의 합집합.
- 변경: `violating_classes` = `PRIMARY_RULES`(R1·R2·R3·R6·R7) `classes` 의 합집합. R4·R5 는 `by_rule` 에만 남김. JSON 에 `primary_rules` 로 범위를 선언.
- 사유:
  - **R4 과대 집계**: 순환 참여 클래스 전부를 원인으로 잡아, 정방향 의존만 가진 "피해자" 쪽 클래스(v4 의 `application.service.LoanPolicy`)까지 센다(REPORT 7.5 N2). 게다가 패턴 A 의 R4 는 R1 ∪ R2 의 부분집합이라(REPORT 4절), 잘못된 역방향 엣지의 출발 클래스는 이미 R1/R2 원인 클래스로 들어간다. R4 를 합집합에 넣으면 순수하게 피해자 클래스만 더해진다.
  - **R5 ⊂ R1**: 2차에서 R1 에 `com.fasterxml.jackson..` 이 추가되어 R5 의 4개 어노테이션(@Entity, @Component, @Service, @JsonProperty)이 모두 R1 블랙리스트 패키지에 속한다. R5 원인 클래스(어노테이션 붙은 멤버의 소유 클래스) = 해당 어노테이션 의존의 출발 클래스 ⊆ R1 원인 클래스(REPORT 7.5 N1). 합집합에 기여 0 이므로 정의상 중복을 제거.

**Changes**
- `ViolationExtractor`: `public static final List<String> PRIMARY_RULES = List.of("R1", "R2", "R3", "R6", "R7")` 추가(근거 javadoc 포함). `report()` 가 `PRIMARY_RULES` 에 속한 규칙의 `classes` 만 합집합에 넣고, `tests_executed` 다음에 `primary_rules` 필드를 기록.
- `ViolationDetectionTest`: 파라미터에 픽스처별 기대 주 지표 원인 클래스 집합(`primary(...)`) 추가. `report()` 결과의 `primary_rules` == [R1, R2, R3, R6, R7], `violating_classes_list`(상대 이름) == 기대 집합, `violating_classes` == 기대 크기를 assert. `[matrix]` 행에 `violating_classes` 표시.
  - 재계산 결과: v1=1, v2=1, v3=2, **v4=1 (2 → 1, LoanPolicy 빠짐)**, **v5=3 (= R1 원인 클래스 Book·Loan·Member, 변화 없음)**, v6=2, v7=2, v8=1.
- `CLAUDE.md` "Measurement JSON", `REPORT.md` 8절에 근거·표 기록.

**Notes**
- 민감도: `PRIMARY_RULES` 에 R4 를 다시 넣으면 전 픽스처의 `primary_rules` 검사와 v4 의 `violating_classes`(1 → 2) 검사가 실패함을 확인 후 원복.
- R4·R5 의 규칙 자체(위반 판정, `count`, `classes`)와 7×8 걸림/건수/원인 클래스 칸은 바뀌지 않았다. 바뀐 것은 주 지표 정의뿐.
- 남은 질문: R5 를 R1 이 못 잡는 어노테이션용으로 재정의할지, R4 를 레이어 내부 순환(패턴 B 계열) 지표로 재정의해 주 지표에 다시 넣을지는 미결.
