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
