# TASK1 — 헥사고날 ArchUnit 규칙 R1~R6 구현 및 양방향 검증

- **Date**: 2026-10-14
- **Status**: completed
- **Related TASKs**: —
- **Commits**: — (git 저장소 아님)

## Request
실험용 측정기로 쓸 ArchUnit 규칙 6개(R1~R6)를 구현하고, 올바른 헥사고날 저장소(`lending`)에서 전부 통과하는지(양성)와 일부러 깨뜨린 픽스처 v1~v6에서 각 규칙이 자기 위반만 잡는지(음성, 오탐 검증)를 `./gradlew archTest` 한 번으로 검증. R4 슬라이스 패턴 조사, 민감도 확인, REPORT.md 작성 포함.

## Context
이 저장소는 이후 실험의 "초기 저장소"이자 "채점기"다. 규칙이 놓치거나 오탐하면 실험 전체가 오염되므로 정밀도가 최우선이었다. 상세 결과·근거는 `REPORT.md` 가 정본이며 이 문서는 그 요약이다.

## Changes
- **빌드**: 루트 `build.gradle.kts` 의 `subprojects {}` 에서 Java 21 toolchain, Spring Boot BOM, ArchUnit 1.3.2, `archTest` 태스크(`includeTags("arch")`, 매번 재실행, 시작 시 `build/arch` 삭제)를 등록. `test` 는 `arch`/`investigation` 태그 제외.
  - WHY: 측정과 기능 테스트를 분리하고, 테스트 미실행 시 이전 JSON 이 측정값으로 읽히지 않게.
- **lending 참조 구현**: domain(`Book`, `Loan`, `Member`, 예외) / port.in(`LoanBookUseCase`, `RegisterBookUseCase` + 중첩 command record) / port.out(`LoadBookPort`, `SaveBookPort`, `SaveLoanPort`) / service / web(`LoanController`, `BookController`, `WebExceptionHandler`) / persistence(JPA 엔티티·리포지토리·매퍼·`BookPersistenceAdapter`, `LoanPersistenceAdapter`). `LendingIntegrationTest` 로 등록→대출→재대출 409 검증.
  - 지시에 없던 `RegisterBook*`, `LoanPersistenceAdapter` 는 통합 시나리오와 앱 기동에 필요해서 추가.
- **규칙 팩토리** `HexagonalRules.forBasePackage(base)` → `List<NamedRule(id, name, rule)>`.
  - R1/R2: `..application..`/`..adapter..` 를 base 아래로 한정 (서드파티 세그먼트 오탐 방지).
  - R5: `@JsonProperty` 는 TYPE 에 붙을 수 없어 필드·코드유닛·파라미터(커스텀 `ArchCondition`)까지 검사.
  - R6: port 아래 record 는 interface 요구에서 제외 (지시된 command record 구조와 문장의 모순 해소), 구현체는 assignability 로 판정.
  - 전 규칙 `allowEmptyShould(true)` — 축소 픽스처에서 "대상 없음"이 위반 1건으로 잡히는 오탐 방지.
- **양성 테스트** `ArchitectureTest`: `@Tag("arch")` + `@ArchTag("arch")`, 규칙별 건수를 `lending/build/arch/lending.json` 에 먼저 쓰고 위반 시 실패.
- **음성 테스트** `ViolationDetectionTest`: 픽스처별 기대 집합 **+ 기대 건수** 검증 (v1 R1:3, v2 R2:5, v3 R3:7, v4 R1:2+R4:1, v5 R1:3+R5:5, v6 R6:2).
- **픽스처 강화**: v3 에 out→in 역방향, v5 에 `@Service`·`@JsonProperty`(필드·파라미터), v6 에 abstract class 포트와 service 내 구현체 둘 다.
- **R4 조사** `R4SlicePatternInvestigationTest` (`r4Investigation` 태스크): 패턴 A/B/C 비교 → 패턴 A 채택.

## Notes
- `@Tag` 만으로는 ArchUnit JUnit5 엔진이 필터되지 않아 `archTest` 가 테스트 0개로 BUILD SUCCESSFUL 이 된다 → `@ArchTag` 필수.
- 지시대로 던짐 여부만 assert 하면 규칙의 부분 완화(예: R5 에서 `@Entity` 제거)를 못 잡는다 → 건수까지 고정. 규칙 변경 시 건수는 상세를 확인한 뒤 갱신할 것.
- 패턴 A 의 R4 는 R1 ∪ R2 의 부분집합(단독 발생 불가). 실제 저장소에선 R1/R2 위반 시 R4 동반 → 이중 집계. 실험 설계 수정 입력으로 REPORT.md 5절에 12개 모호 지점 정리.
- v1/v2 가 단독 위반으로 나오는 건 역방향 엣지를 뺀 축소 때문이며 실제 저장소와 다르다.
