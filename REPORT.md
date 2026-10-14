# arch-bench REPORT — 헥사고날 ArchUnit 규칙 R1~R6 구현 및 양방향 검증

- 환경: Java 21 (Temurin 21.0.8), Gradle 8.14.3 (wrapper), Spring Boot 3.5.3, ArchUnit 1.3.2, JUnit 5.12, H2
> **2차 변경(R7 추가, R1 확장, JSON 스키마 변경, 0개 가드, git) 이후의 현재 상태는 맨 아래 "7. 2차 변경" 절을 보라.**
> 1~6절은 1차 작업 당시의 기록이며, 2차에서 바뀐 사항은 7절에 적었다(1차 서술은 지우지 않음).

- 최종 상태(1차): `./gradlew clean build archTest` → **BUILD SUCCESSFUL**
  - `lending/build/arch/lending.json` = `{"R1": 0, "R2": 0, "R3": 0, "R4": 0, "R5": 0, "R6": 0}`
  - `ViolationDetectionTest` 6/6 통과 (6개 픽스처 모두 기대 집합 및 기대 건수와 일치)
  - `LendingIntegrationTest` 1/1 통과 (책 등록 → 대출 201 → 재대출 409)

---

## 6. 실행 방법과 출력 경로 (먼저 적음)

```bash
cd arch-bench
./gradlew archTest                           # 양방향 검증 (lending 양성 + 픽스처 음성)
./gradlew build archTest                     # 기능 테스트 + 양방향 검증
./gradlew :violation-fixtures:r4Investigation  # 단계 4 R4 슬라이스 패턴 조사 (측정기 아님)
```

| 산출물 | 경로 | 비고 |
| --- | --- | --- |
| 측정값 JSON | `lending/build/arch/lending.json` | `{"R1": n, ..., "R6": n}`. 위반이 있어도 테스트 실패 **전에** 기록됨 |
| 양성 테스트 결과 | `lending/build/test-results/archTest/` | |
| 음성 테스트 결과 | `violation-fixtures/build/test-results/archTest/` | 콘솔에 `[matrix]` 행 출력 |

- `archTest`는 루트 `build.gradle.kts`의 `subprojects {}`에서 두 모듈에 각각 등록되며, `@Tag("arch")`(+`@ArchTag("arch")`) 테스트만 실행한다.
- 일반 `test` 태스크는 `arch`, `investigation` 태그를 **제외**한다(기능 테스트와 측정을 분리).
- `archTest`는 매번 재실행(`upToDateWhen { false }`)되고, 시작 시 `build/arch/`를 지운다. 테스트가 실행되지 않으면 JSON이 아예 없게 되어, 지난 실행의 값이 측정값으로 읽히지 않는다.
- 위반이 있을 때의 동작 확인: `domain.Member`에 임시로 `@Component`를 붙여 실행 → 테스트 FAILED, JSON = `{"R1": 1, "R2": 0, "R3": 0, "R4": 0, "R5": 1, "R6": 0}` (확인 후 원복).

---

## 1. 규칙별 구현

모든 규칙은 `lending/src/test/java/com/example/lending/architecture/HexagonalRules.java`의
`HexagonalRules.forBasePackage(String basePackage)`가 반환하는 `List<NamedRule>`(`record NamedRule(String id, String name, ArchRule rule)`)로 제공된다.
공통 변수:

```java
String domain      = basePackage + ".domain..";
String application = basePackage + ".application..";
String adapter     = basePackage + ".adapter..";
String adapterIn   = basePackage + ".adapter.in..";
String adapterOut  = basePackage + ".adapter.out..";
String port        = basePackage + ".application.port..";
String portOut     = basePackage + ".application.port.out..";
```

**모든 규칙 공통 변경: `allowEmptyShould(true)`**
ArchUnit 1.x는 검사 대상이 0개이면 기본적으로 실패한다. 이 실패는 위반 1건으로 집계된다. 축소 픽스처(예: v3에는 `application.port`가 없음)나 특정 레이어가 없는 저장소에서 이것이 오탐이 되므로 끈다. 기준 패키지 오타 같은 설정 오류는 테스트가 따로 막는다. 양성 테스트는 `domain` 클래스가 import됐는지 확인하고, 음성 테스트는 import 결과가 비어 있지 않은지 확인한다.

### R1 `domain-no-outward-deps`

- 원래 문장: `domain`은 `..application..`, `..adapter..`, `org.springframework..`, `jakarta.persistence..`을 의존하지 않는다

```java
ArchRule r1 = noClasses().that().resideInAPackage(domain)
        .should().dependOnClassesThat().resideInAnyPackage(
                application, adapter, "org.springframework..", "jakarta.persistence..")
        .because(r1Reason)
        .allowEmptyShould(true);
```

- 바뀐 점: `..application..`, `..adapter..`를 `basePackage + ".application.."`, `basePackage + ".adapter.."`로 **기준 패키지 아래로 한정**했다.
  - 이유: `..application..`은 어느 위치든 `application`이라는 패키지 세그먼트가 있으면 매칭된다. 도메인이 의존하는 서드파티 라이브러리에 우연히 `application`이나 `adapter` 세그먼트가 있으면 오탐이 된다(`lending`에서는 결과 차이 없음. 실험 중 에이전트가 라이브러리를 추가하는 경우에 대비). 의도는 "이 앱의 application/adapter 레이어"이므로 한정하는 편이 정확하다.
- 블랙리스트 방식이므로 `java..`는 허용된다(요구대로).
- 참고: ArchUnit 1.x는 **어노테이션도 의존으로 본다**. 따라서 도메인의 `@Entity`, `@Component`는 R1에도 걸린다. 이 때문에 R5와 겹친다(5절 참조).

### R2 `application-no-adapter-deps`

- 원래 문장: `application`은 `..adapter..`를 의존하지 않는다

```java
ArchRule r2 = noClasses().that().resideInAPackage(application)
        .should().dependOnClassesThat().resideInAPackage(adapter)
        .because("application은 ..adapter..를 의존하지 않는다")
        .allowEmptyShould(true);
```

- 바뀐 점: R1과 같은 이유로 `..adapter..`를 기준 패키지 아래로 한정했다.

### R3 `adapters-independent`

- 원래 문장: `adapter.in`과 `adapter.out`은 서로 의존하지 않는다

```java
ArchRule r3 = CompositeArchRule
        .of(noClasses().that().resideInAPackage(adapterIn)
                .should().dependOnClassesThat().resideInAPackage(adapterOut)
                .because(r3Reason).allowEmptyShould(true))
        .and(noClasses().that().resideInAPackage(adapterOut)
                .should().dependOnClassesThat().resideInAPackage(adapterIn)
                .because(r3Reason).allowEmptyShould(true));
```

- 바뀐 점: 없음. "서로"를 양방향 두 규칙으로 풀었다.

### R4 `no-layer-cycles`

- 원래 문장: 레이어 간 순환 의존이 없다

```java
ArchRule r4 = slices().matching(basePackage + ".(*)..")
        .should().beFreeOfCycles()
        .because("레이어 간 순환 의존이 없다")
        .allowEmptyShould(true);
```

- 바뀐 점: "레이어"를 기준 패키지 바로 아래 최상위 패키지(`domain`/`application`/`adapter`)로 정의했다(패턴 A). 근거는 4절.

### R5 `domain-no-framework-annotations`

- 원래 문장: `domain` 클래스에 `@Entity`, `@Component`, `@Service`, `@JsonProperty`가 없다

```java
private static final String ENTITY = "jakarta.persistence.Entity";
private static final String COMPONENT = "org.springframework.stereotype.Component";
private static final String SERVICE = "org.springframework.stereotype.Service";
private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";

ArchRule r5 = CompositeArchRule
        .of(noClasses().that().resideInAPackage(domain)
                .should().beAnnotatedWith(ENTITY)
                .orShould().beAnnotatedWith(COMPONENT)
                .orShould().beAnnotatedWith(SERVICE)
                .orShould().beAnnotatedWith(JSON_PROPERTY)
                .because(r5Reason).allowEmptyShould(true))
        .and(noFields().that().areDeclaredInClassesThat().resideInAPackage(domain)
                .should().beAnnotatedWith(JSON_PROPERTY)
                .because(r5Reason).allowEmptyShould(true))
        .and(noCodeUnits().that().areDeclaredInClassesThat().resideInAPackage(domain)
                .should().beAnnotatedWith(JSON_PROPERTY)
                .because(r5Reason).allowEmptyShould(true))
        .and(codeUnits().that().areDeclaredInClassesThat().resideInAPackage(domain)
                .should(notHaveParametersAnnotatedWith(JSON_PROPERTY))   // 파라미터 어노테이션용 커스텀 조건
                .because(r5Reason).allowEmptyShould(true));
```

- 어노테이션은 요구대로 **FQN 문자열**로 매칭한다. 테스트 코드는 JPA, Spring, Jackson에 컴파일 의존하지 않는다.
- 바뀐 점: `@JsonProperty`는 **클래스뿐 아니라 필드, 메서드, 생성자, 파라미터까지** 검사한다.
  - 이유: `@JsonProperty`의 `@Target`은 `ANNOTATION_TYPE, FIELD, METHOD, PARAMETER`다. 클래스(TYPE)에는 붙일 수 없다. 원래 문장대로 "클래스에"만 검사하면 이 항목은 **절대 걸리지 않는 빈 검사**가 된다. ArchUnit에는 파라미터용 fluent API가 없어서, 파라미터는 `HexagonalRules` 안의 private `ArchCondition` 하나로 검사한다.
- 메타 어노테이션(`beMetaAnnotatedWith`)은 쓰지 않았다. 직접 붙은 어노테이션만 본다. 원래 문장이 4개를 명시했으므로 그대로 따랐다(판단 사항 참조).

### R6 `ports-are-interfaces-implemented-in-adapters`

- 원래 문장: `application.port` 아래 타입은 interface이며, `port.out` 인터페이스의 구현체는 `adapter.out`에만 있다

```java
ArchRule r6 = CompositeArchRule
        .of(classes().that().resideInAPackage(port)
                .and().areNotAssignableTo(Record.class)
                .should().beInterfaces()
                .because(r6Reason).allowEmptyShould(true))
        .and(classes().that().areAssignableTo(resideInAPackage(portOut))
                .and().resideOutsideOfPackage(portOut)
                .should().resideInAPackage(adapterOut)
                .because(r6Reason).allowEmptyShould(true));
```

- 바뀐 점 1: **record는 interface 요구에서 제외**한다.
  - 이유: 작업 지시의 구조("`port/in`: `LoanBookUseCase` (interface, command record 포함)")와 R6 문장("port 아래 타입은 interface")이 서로 모순된다. 제외하지 않으면 `lending`에서 R6가 2건 실패한다(`LoanBookCommand`, `RegisterBookCommand`; 민감도 표 S3에서 실제 확인). `lending`이 지시대로 만들어졌으므로 규칙 쪽을 고쳤다. record는 상속도 구현도 할 수 없으므로 포트 역할을 할 수 없다. 따라서 이 예외로 v6 같은 "abstract class 포트"가 빠져나가지는 않는다.
- 바뀐 점 2: "구현체"를 `implements`가 아니라 **할당 가능성(assignability)**으로 정의했다. port.out 타입을 간접적으로 구현하거나(구현 클래스의 하위 클래스) 상속하는 클래스(abstract class 포트의 하위 클래스)도 포함한다. 같은 port.out 패키지 안의 타입(포트 자신, 포트를 확장하는 포트)은 제외한다.

---

## 2. 양성 검증 결과 (`lending`)

`./gradlew archTest` 출력 발췌:

```
> Task :lending:archTest
ArchitectureTest > hexagonal_rules_hold STANDARD_OUT
    [arch] R1 domain-no-outward-deps                        PASS (0)
    [arch] R2 application-no-adapter-deps                   PASS (0)
    [arch] R3 adapters-independent                          PASS (0)
    [arch] R4 no-layer-cycles                               PASS (0)
    [arch] R5 domain-no-framework-annotations               PASS (0)
    [arch] R6 ports-are-interfaces-implemented-in-adapters  PASS (0)
    [arch] wrote .../arch-bench/lending/build/arch/lending.json
ArchitectureTest > hexagonal_rules_hold PASSED
```

`lending.json`: `{"R1": 0, "R2": 0, "R3": 0, "R4": 0, "R5": 0, "R6": 0}`

`lending` 구성 (Lombok, MapStruct 없음):

| 레이어 | 클래스 |
| --- | --- |
| `domain` | `Book`, `BookStatus`, `Loan`, `Member`, `BookAlreadyOnLoanException`, `BookNotFoundException` (java.* 만 import) |
| `application.port.in` | `LoanBookUseCase`(+`LoanBookCommand` record), `RegisterBookUseCase`(+`RegisterBookCommand` record) |
| `application.port.out` | `LoadBookPort`, `SaveBookPort`, `SaveLoanPort` |
| `application.service` | `LoanBookService`, `RegisterBookService` (`@Service @Transactional`, 포트만 의존) |
| `adapter.in.web` | `LoanController`, `BookController`, `WebExceptionHandler`, 요청/응답 record 4개 |
| `adapter.out.persistence` | `BookJpaEntity`, `LoanJpaEntity`, `BookJpaRepository`, `LoanJpaRepository`, `BookMapper`, `LoanMapper`, `BookPersistenceAdapter`, `LoanPersistenceAdapter` |
| (루트) | `LendingApplication` |

---

## 3. 음성 검증 결과 (픽스처 × 규칙)

`FAIL(n)` = 규칙이 AssertionError를 던짐 (n = `getFailureReport().getDetails().size()`), `pass` = 통과.
굵게 표시한 칸이 기대 집합이다.

| 픽스처 | R1 | R2 | R3 | R4 | R5 | R6 | 기대 집합 (규칙: 건수) | 일치 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v1 | **FAIL(3)** | pass | pass | pass | pass | pass | R1: 3 | ✅ |
| v2 | pass | **FAIL(5)** | pass | pass | pass | pass | R2: 5 | ✅ |
| v3 | pass | pass | **FAIL(7)** | pass | pass | pass | R3: 7 | ✅ |
| v4 | **FAIL(2)** | pass | pass | **FAIL(1)** | pass | pass | R1: 2, R4: 1 | ✅ |
| v5 | **FAIL(3)** | pass | pass | pass | **FAIL(5)** | pass | R1: 3, R5: 5 | ✅ |
| v6 | pass | pass | pass | pass | pass | **FAIL(2)** | R6: 2 | ✅ |

픽스처 내용과 기대 집합의 이유 (`ViolationDetectionTest` 클래스 주석의 표와 같음):

| 픽스처 | 위반 내용 | 기대 집합 | 왜 이 집합인가 |
| --- | --- | --- | --- |
| v1 | `domain.Book.saveWith(BookJpaRepository)`가 `new BookJpaEntity(..)`를 만들어 `repository.save(..)` 호출 | {R1} | adapter.out은 domain을 참조하지 않게 축소했으므로 순환이 없다 |
| v2 | `application.service.LoanBookService`가 `BookJpaRepository`를 생성자 주입받아 `findById`, 엔티티 getter 사용 | {R2} | adapter.out은 application을 참조하지 않게(포트 구현 없음) 축소했으므로 순환이 없다 |
| v3 | `adapter.in.web.LoanController.getBook()`이 `BookJpaEntity`를 반환 (+ 역방향: `BookJpaEntity.from(BookRequest)`) | {R3} | in과 out은 둘 다 `adapter` 슬라이스 안에 있으므로 R4(패턴 A)와 무관하다 |
| v4 | `domain.Book.loan()`이 `application.service.LoanPolicy`를 호출하고, `LoanPolicy.canLoan(Book)`이 다시 domain 참조 | {R1, R4} | domain → application은 그 자체로 R1 위반이다. 작업 지시대로 정의상 둘 다 걸린다 |
| v5 | `domain.Book`에 `@Entity`(+필드 `@JsonProperty`), `domain.Member`에 `@Component`(+생성자 파라미터 `@JsonProperty`), `domain.Loan`에 `@Service` | {R1, R5} | ArchUnit은 어노테이션을 의존으로 본다. `@Entity`, `@Component`, `@Service`는 R1의 금지 패키지에 속한다. `@JsonProperty`(Jackson)는 R1의 금지 목록에 없어서 R5만 잡는다 |
| v6 | `port.out.LoadBookPort`가 abstract class(구현은 `adapter.out`), `port.out.SaveBookPort`의 구현체 `InMemoryBookStore`가 `application.service`에 있음 | {R6} | 지시의 "이거나"를 **둘 다** 넣어 R6의 두 하위 규칙을 각각 1건씩 확인한다 |

건수 상세 (실제로 집계된 항목. 모두 직접 확인했고 오탐은 0건):

- v1 R1 (3): `BookJpaEntity` 생성자 호출, `BookJpaRepository.save` 호출, `BookJpaRepository` 타입 파라미터
- v2 R2 (5): 생성자 파라미터 타입, 필드 타입, `findById` 호출, `getId()` 호출, `getTitle()` 호출
- v3 R3 (7): in→out 4건(생성자 파라미터, 필드, `findById` 호출, 반환 타입) + out→in 3건(`from()` 파라미터 타입, `id()` 호출, `title()` 호출)
- v4 R1 (2): `LoanPolicy` 생성자 호출, `canLoan` 호출 / R4 (1): 순환 `application → domain → application` 1개
- v5 R1 (3): 클래스 어노테이션 `@Entity`, `@Component`, `@Service` / R5 (5): 그 3개 + `Book.title` 필드의 `@JsonProperty` + `Member.<init>` 파라미터 0의 `@JsonProperty`
- v6 R6 (2): `LoadBookPort is no interface`, `InMemoryBookStore does not reside in ..adapter.out..`

**건수까지 고정한 이유 (지시보다 강화한 부분).** 지시는 "던지느냐/안 던지느냐"만 assert하라고 했다. 하지만 그것만으로는 규칙을 **일부만** 느슨하게 했을 때 감지하지 못한다(아래 민감도 표 S1, S4, S8, S9). 실험의 측정값이 건수(JSON)이므로 건수도 assert한다.

### 채점기 민감도 확인 (규칙을 하나씩 느슨하게 → `./gradlew archTest`)

`HexagonalRules.java`를 하나씩 수정해 실행하고 매번 원본으로 복구했다(복구 후 `cmp`로 동일함 확인).

| # | 느슨하게 한 내용 | 결과 | 감지 수단 |
| --- | --- | --- | --- |
| S1 | R5에서 `@Entity` 검사 제거 (지시의 예시) | **v5 실패**: `v5 R5: expected 5 violations but got 4` | 건수 (R5는 다른 어노테이션 때문에 여전히 던지므로, 던짐 여부만으로는 **놓친다**) |
| S2 | R1에서 `..adapter..` 제거 | **v1 실패**: `v1 R1: expected violation but rule passed (missed)` | 던짐 여부 |
| S3 | R6에서 record 예외 제거 | **lending 실패**: R6 FAIL(2) (`LoanBookCommand`, `RegisterBookCommand`) | 양성 테스트 (record 예외가 필요하다는 근거) |
| S4 | R6에서 "구현체는 adapter.out" 하위 규칙 제거 | **v6 실패**: `expected 2 violations but got 1` | 건수 |
| S5 | R3에서 in→out 방향 제거 | **v3 실패**: `expected 7 violations but got 3` | 건수 |
| S6 | R3에서 out→in 방향 제거 | **v3 실패**: `expected 7 violations but got 4` | 건수 (※ 처음 만든 v3에는 이 방향이 없어서 **감지되지 않았다** → v3을 보강함. 판단 사항 참조) |
| S7 | R4를 패턴 C로 교체 | **v4 실패**: `v4 R4: expected violation but rule passed (missed)` | 던짐 여부 |
| S8 | R1에서 `jakarta.persistence..` 제거 | **v5 실패**: `v5 R1: expected 3 violations but got 2` | 건수 |
| S9 | R5에서 파라미터 `@JsonProperty` 검사 제거 | **v5 실패**: `v5 R5: expected 5 violations but got 4` | 건수 |
| S10 | R2를 무력화 (`dependOnClassesThat` 대상을 존재하지 않는 패키지로) | **v2 실패**: `v2 R2: expected violation but rule passed (missed)` | 던짐 여부 |

모든 완화가 정확히 해당 픽스처(또는 S3은 양성 테스트)의 실패로 이어졌고, 다른 픽스처의 결과는 바뀌지 않았다.

---

## 4. R4 슬라이스 패턴 조사

실행: `./gradlew :violation-fixtures:r4Investigation` (`R4SlicePatternInvestigationTest`, `@Tag("investigation")`)

| 대상 | 패턴 A `base.(*)..` | 패턴 B `base.(**)` | 패턴 C `base.adapter.(*)..` + `base.application.(*)..` |
| --- | --- | --- | --- |
| **lending** (통과해야 함) | pass | pass | pass |
| v1 | pass | pass | pass |
| v2 | pass | pass | pass |
| v3 | pass | pass | pass |
| **v4** (실패해야 함) | **FAIL(1)** `application → domain → application` | **FAIL(1)** `application.service → domain → application.service` | **pass ← 놓침** |
| v5 | pass | pass | pass |
| v6 | pass | pass | pass |

관찰:

- `lending`에서 실패한 패턴은 **없다**. 따라서 오탐으로 잡힌 클래스 쌍도 없다.
- 패턴별 슬라이스:
  - A: `domain`, `application`, `adapter` 3개. 루트의 `LendingApplication`은 어느 슬라이스에도 속하지 않는다.
  - B: `domain`, `application.port.in`, `application.port.out`, `application.service`, `adapter.in.web`, `adapter.out.persistence`처럼 패키지마다 1개씩.
  - C: `adapter.in`, `adapter.out`, `application.port`, `application.service`. **`domain`은 어느 슬라이스에도 속하지 않는다.**
- 패턴 C는 domain이 슬라이스에서 빠지므로 `domain ↔ application` 순환(v4)을 **원리적으로 볼 수 없다**. 탈락시켰고, 민감도 S7에서도 확인했다.
- 패턴 B는 A보다 촘촘하다. A가 보지 못하는 **레이어 내부** 순환(예: `application.port ↔ application.service`)을 잡는다. 반면 같은 레이어 안의 하위 패키지 분리(예: `adapter.in.web` ↔ `adapter.in.web.dto`)도 순환으로 보므로, 실험 중 에이전트가 패키지를 쪼개는 리팩터링을 하면 "레이어 간"이 아닌 순환까지 R4로 집계된다. 이것은 원래 문장(레이어 **간**)의 범위를 넘는다.

**채택: 패턴 A.** 이유:
1. 원래 문장 "레이어 **간** 순환"의 정의와 정확히 일치한다(레이어 = domain/application/adapter).
2. `lending`에서 오탐이 없고 v4를 잡는다.
3. B는 문장 범위 밖의 레이어 내부 순환까지 세므로 측정값이 원래 정의와 달라진다. C는 v4를 놓친다.

**중요한 부수 발견. 패턴 A에서 R4는 R1 ∪ R2의 부분집합이다.** 허용 방향은 `adapter → application → domain`(그리고 `adapter → domain`)뿐이다. 따라서 3개 슬라이스 사이의 순환은 반드시 역방향 엣지 하나를 포함해야 한다. 가능한 역방향 엣지는 `domain → application`, `domain → adapter`(둘 다 R1 위반)와 `application → adapter`(R2 위반)뿐이다. 그러므로 **패턴 A의 R4는 R1이나 R2 없이 단독으로 걸릴 수 없다.** 같은 이유로, 실제 저장소(`lending`처럼 `adapter.out → domain`, `adapter.out → application` 엣지가 원래 있는 구조)에 v1·v2 유형의 위반이 생기면 R4도 **함께** 걸린다. v1·v2가 {R1}, {R2} 단독으로 나오는 것은 픽스처를 축소하면서 역방향 엣지를 빼 두었기 때문이다. 이 결론은 의존 그래프에서 도출한 것이며, `lending` 기반의 별도 픽스처를 만들어 돌려보지는 않았다. 실험 해석에 미치는 영향은 5절 4번 항목에 적었다.

---

## 5. 원래 문장이 모호했던 지점 (실험 설계 문서 수정 입력)

1. **R1/R2의 `..application..`, `..adapter..` 범위.** 와일드카드 패턴은 기준 패키지 밖(서드파티 등)의 같은 이름 세그먼트도 매칭한다. → 기준 패키지 아래로 한정할지 문장에 명시해야 한다. (구현: 한정함)
2. **R5 `@JsonProperty`는 "클래스에" 붙을 수 없다.** 문장 그대로 구현하면 이 항목은 절대 걸리지 않는다. → "domain 타입과 그 멤버/파라미터에"로 고쳐야 한다. 또 record 컴포넌트에 붙인 `@JsonProperty` 1개는 필드, 접근자, 정규 생성자 파라미터로 전파되어 **최대 3건**으로 집계된다.
3. **R5와 R1이 겹친다.** `@Entity`, `@Component`, `@Service`는 R1이 금지한 패키지의 타입이고, ArchUnit은 어노테이션을 의존으로 센다. 따라서 이 3개는 항상 R1과 R5에 **이중 집계**된다. R5만 단독으로 잡는 것은 `@JsonProperty`(Jackson)뿐이다. 거꾸로 R5 목록에 없는 `@Repository`, `@Configuration`, `@Table`, `@Id`, `@Column` 등은 R1만 잡는다. 메타 어노테이션을 볼지(예: `@Service`를 `@Component`로 간주할지)도 정해져 있지 않다. → R5의 목적이 "프레임워크 어노테이션 일반"인지 "이 4개"인지, R1과 독립적인 지표여야 하는지 정해야 한다.
4. **R4(레이어 순환)는 R1 ∪ R2에 포함된다** (4절 증명). 실제 저장소에서는 R1·R2 위반이 생기면 R4도 거의 항상 함께 걸린다. → R4를 독립 지표로 쓰면 같은 결함이 2번 집계된다. 독립 지표가 필요하면 R4의 범위를 "레이어 내부 하위 패키지 간 순환"(패턴 B 계열)으로 재정의하는 것을 검토해야 한다.
5. **R6 문장과 지시된 구조의 모순.** "port 아래 타입은 interface"인데, 구조는 port.in에 command record를 두라고 한다. → "port 아래의 record를 제외한 타입"으로 고쳐야 한다. enum이나 예외 클래스를 port에 둘 수 있는지도 정해야 한다(현재 규칙에서는 위반).
6. **R6 "구현체"의 범위.** 직접 `implements`인가, 간접(하위 클래스) 구현과 abstract class 포트 상속까지 포함하는가. (구현: assignability = 모두 포함) port.in 구현체의 위치는 아무 제약이 없다(예: 컨트롤러가 유스케이스를 구현해도 통과).
7. **레이어 밖 패키지는 어떤 규칙도 적용받지 않는다.** `base.common`, `base.config`, `base.shared` 같은 4번째 최상위 패키지나, `adapter.in`/`adapter.out` 이외의 `adapter.*` 하위 패키지(예: `adapter.common`)에 둔 클래스는 R1~R6 어디에도 걸리지 않는다. 에이전트가 공유 DTO를 `adapter.common`에 두면 R3을 우회한다. → 허용 패키지를 화이트리스트로 정하고 그 밖의 패키지를 위반으로 볼지 정해야 한다.
8. **R1의 금지 목록이 좁다.** `com.fasterxml.jackson..`, `org.hibernate..`, `javax.persistence..`, `jakarta.validation..`은 R1에 걸리지 않는다. 예를 들어 도메인에 `jakarta.validation.constraints.NotNull`을 붙이면 **어느 규칙도 잡지 않는다.**
9. **위반 "건수"의 단위가 규칙마다 다르다.** R1~R3은 의존 엣지마다(필드 타입, 생성자 파라미터, 메서드 호출, 반환 타입이 각각 1건), R4는 순환마다, R5는 어노테이션이 붙은 요소마다, R6은 클래스마다 1건이다. 사람이 보기에 "한 번의 잘못"이 R1에서는 3~5건이 된다. → JSON 값을 규칙 간에 비교하거나 합산하면 안 된다. 실험 지표를 이진값(위반 여부)으로 할지, 위반 클래스 수로 할지, 엣지 수로 할지 정해야 한다.
10. **빈 레이어는 공허하게 통과한다.** `allowEmptyShould(true)` 때문에, 에이전트가 레이어 패키지를 지우거나 이름을 바꾸면(예: `domain` → `model`) 관련 규칙이 전부 0건이 된다. → 레이어 패키지명 고정 여부와, 사라진 경우의 처리를 정해야 한다.
11. **테스트 코드는 측정 대상이 아니다.** `DoNotIncludeTests`에 따라 `src/test`의 위반은 집계되지 않는다. 문장에는 이 범위가 없다.
12. **루트 패키지의 클래스**(`LendingApplication` 등)는 어느 레이어에도 속하지 않는다. 그래서 루트에서 어디로든 의존해도 규칙 밖이다.

---

## 판단 사항

| # | 지점 | 선택 | 이유 |
| --- | --- | --- | --- |
| J1 | R6 문장과 port.in command record의 모순 | record를 interface 요구에서 제외 | 둘 다 지시 사항이다. 포트 역할이 불가능한 record만 빼는 것이 가장 좁은 완화다. 중첩 record에만 한정하면 Buckpal식 최상위 `XxxCommand` record가 오탐이 된다 |
| J2 | `..application..` 등 와일드카드 범위 | 기준 패키지로 한정 | 의도에 맞고, 서드파티 오탐을 차단한다 |
| J3 | R5 메타 어노테이션 | 쓰지 않음(직접 어노테이션만) | 문장이 4개를 명시했다. 그 외 Spring 스테레오타입은 R1이 잡는다 |
| J4 | R4 패턴 | 패턴 A | 4절 |
| J5 | 빈 검사 대상 | `allowEmptyShould(true)` + 테스트에서 import가 비어 있지 않은지 확인 | 축소 픽스처에서 "대상 없음"이 위반 1건으로 집계되는 오탐을 막는다 |
| J6 | 음성 테스트 assert 범위 | 기대 집합 + **기대 건수** | 지시(던짐 여부)만으로는 S1, S4, S5, S6, S8, S9의 부분 완화를 놓친다 |
| J7 | v3 픽스처 | 지시(in→out 반환) + **out→in 역방향** 추가 | 처음에는 R3의 out→in 하위 규칙을 지워도 아무 테스트도 실패하지 않았다(검증 공백). 픽스처를 강화한 것이지 약화한 것이 아니다 |
| J8 | v5 픽스처 | 지시(`@Entity`, `@Component`) + `@Service`(`Loan`) + `@JsonProperty`(필드, 생성자 파라미터) 추가 | R5의 4개 항목과 R5만 단독으로 잡는 경로(`@JsonProperty`)를 모두 픽스처로 덮기 위해 |
| J9 | v6의 "abstract class **이거나** 구현체가 service에" | 둘 다 포함(서로 다른 포트) | R6의 두 하위 규칙을 각각 검증한다 |
| J10 | v1, v2 축소 범위 | adapter → domain/application 역방향 엣지를 넣지 않음 | 지시의 "정확히 한 규칙"을 지키기 위해서다. 넣으면 R4가 함께 걸린다(4절). 이것이 실제 저장소와 다르다는 점은 5절 4번에 적었다 |
| J11 | `@Tag("arch")`만으로는 ArchUnit JUnit5 엔진 테스트가 필터되지 않음 | `@Tag("arch")` + `@ArchTag("arch")` 병기 | **실측**: `@Tag`만 두면 `archTest`가 테스트 0개로 **BUILD SUCCESSFUL**이 된다(조용한 거짓 통과). `@ArchTag`만 두면 정상 실행된다. 지시대로 `@Tag`는 유지했다 |
| J12 | 지난 측정값이 남는 문제 | `archTest` 시작 시 `build/arch` 삭제 | J11 같은 상황에서 이전 JSON이 측정값으로 읽히지 않게 |
| J13 | `lending`의 "책 등록" | `RegisterBookUseCase`, `RegisterBookService`, `BookController`(`POST /books`) 추가 | 통합 테스트 시나리오(등록 → 대출 → 재대출 409)를 HTTP로 수행하기 위해 |
| J14 | `SaveLoanPort` 구현 | `LoanPersistenceAdapter`, `LoanJpaRepository`, `LoanMapper` 추가 | 지시에는 `BookPersistenceAdapter`만 있었지만, 포트에 구현체가 없으면 앱이 기동되지 않는다 |
| J15 | 도메인 예외 위치 | `BookAlreadyOnLoanException`, `BookNotFoundException`을 `domain`에 둠. HTTP 매핑은 `adapter.in.web.WebExceptionHandler` | port에 두면 R6(interface 아님) 위반이고, service에 두면 컨트롤러가 service에 의존하게 된다 |
| J16 | ID 타입 | `UUID` (도메인에서 생성) | 도메인이 영속성의 ID 생성 전략에 의존하지 않게 |
| J17 | `test`와 `archTest` 분리 | `test`는 `arch`, `investigation` 태그 제외 | 같은 테스트의 중복 실행을 막고, 측정과 기능 검증을 분리 |
| J18 | 컴파일 옵션 | `-parameters` 전역 적용 | Spring 6.1+의 `@PathVariable` 이름 해석에 필요하다(픽스처 모듈에는 Boot 플러그인이 없음). ArchUnit 측정에는 영향 없음 |

## 의존성 추가 내역과 이유

| 모듈 | 의존성 | 이유 |
| --- | --- | --- |
| 공통(test) | `archunit-junit5:1.3.2`, `junit-jupiter`, `junit-platform-launcher` | 테스트 전용 |
| `lending` | `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `h2`(runtime), `spring-boot-starter-test`(test) | 지시된 스택 |
| `violation-fixtures` | `spring-boot-starter-web`, `spring-boot-starter-data-jpa` (main) | 픽스처가 Spring/JPA/Jackson 어노테이션과 타입을 실제로 써야 컴파일된다(지시 사항) |
| `violation-fixtures`(test) | `project(":lending", configuration = "archRules")` | 양성·음성 테스트가 **같은 규칙 객체**를 쓰도록 한다. `lending`의 test 출력 중 `HexagonalRules*` 클래스만 담은 jar를 노출한다 |
| `violation-fixtures`(test) | `project(":lending")` | R4 패턴 조사 테스트가 `lending` 클래스를 import하기 위함. 측정(`archTest`)에는 쓰이지 않는다 |

---

## 7. 2차 변경 (2026-10-14)

현재 상태: `./gradlew clean build archTest` → **BUILD SUCCESSFUL**
- `:lending:archTest executed 7 arch tests`, `lending.json`: `tests_executed` = 7, `violating_classes` = 0
- `:violation-fixtures:archTest executed 8 arch tests`: 7규칙 × 8픽스처의 걸림 여부, 건수, 원인 클래스가 모두 기대와 일치
- `LendingIntegrationTest` 통과
- git: 기준 커밋(1차 상태) + 2차 변경 커밋

### 7.1 변경 요약과 근거

**변경 1 — R1 블랙리스트 확장.** `jakarta.validation..`과 `com.fasterxml.jackson..`을 추가했다. 최종 목록은 `base.application..`, `base.adapter..`, `org.springframework..`, `jakarta.persistence..`, `jakarta.validation..`, `com.fasterxml.jackson..`이다.
- **"도메인에 validation/Jackson 어노테이션 금지"는 실험 설계자의 결정이다.** 5절 8번 항목(도메인의 `@NotNull`을 어느 규칙도 잡지 못함)에 대한 대응이다. 코드에서 토글할 수 없게 고정했다.
- `lending` 도메인에는 validation 어노테이션이 없음을 확인했다(`grep jakarta.validation|NotNull|NotBlank` 결과 0건). `lending`에는 validation 의존성 자체가 없다. 값 검증은 원래 생성자에서 한다.
- 새 픽스처 **v8**: `domain.Member` 필드에 `@NotNull`이 있다. R1에만 1건 걸린다. R5는 validation을 검사하지 않으므로 통과한다.
- **이 변경으로 기존 테스트가 깨진 지점**: v5의 R1 기대 건수가 3에서 9로 바뀌었다. 이유는 v5의 `@JsonProperty` 2곳(필드, 생성자 파라미터)이 이제 R1의 금지 대상이 되었고, ArchUnit이 어노테이션 하나를 **3개의 의존**으로 세기 때문이다. 어노테이션 자체 1건에, 멤버 기본값의 타입인 `JsonProperty$Access`와 `OptBoolean`이 1건씩 더해진다. 9건을 모두 상세로 확인했으며, 전부 실제 Jackson 의존이다. 기대값을 9로 고쳤다. 원인 클래스(`Book`, `Loan`, `Member`)는 그대로다.

**변경 2 — R7 `only-three-top-level-packages` 신설.**

```java
DescribedPredicate<JavaClass> springBootMainClass = DescribedPredicate.describe(
        "@SpringBootApplication class directly in " + basePackage,
        javaClass -> javaClass.getPackageName().equals(basePackage)
                && javaClass.isAnnotatedWith(SPRING_BOOT_APPLICATION));
ArchRule r7 = classes().that().resideInAPackage(basePackage + "..")
        .and(not(springBootMainClass))
        .should().resideInAnyPackage(domain, application, adapter)
        .because("base 바로 아래의 최상위 패키지는 domain, application, adapter 셋뿐이어야 한다")
        .allowEmptyShould(true);
```

- 단위: 위반 클래스 1개당 1건(`JavaClass` 위반 객체).
- 예외 범위: base 패키지에 **직접** 있고, `@SpringBootApplication`이 **직접** 붙은 클래스만이다. 메타 어노테이션과 하위 패키지는 예외가 아니다. 그 밖의 base 직속 클래스는 위반이다.
- 새 픽스처 **v7**:

  | 클래스 | 위치 | R7 |
  | --- | --- | --- |
  | `V7Application` (`@SpringBootApplication`) | base 직속 | 허용 (예외 경로 검증) |
  | `AppConstants` | base 직속 | **위반** |
  | `common.DateUtils` | 비허용 최상위 패키지 | **위반** |
  | `adapter.common.Mapper` | `adapter..` 하위 | 허용 (의도된 범위) |
  | `domain.Book` → `common.DateUtils` 사용 | domain | 허용 |

  - `domain → base.common` 의존은 **R1에 걸리지 않는다.** R1 블랙리스트에 `base.common`이 없기 때문이다. 이 결함은 `DateUtils`가 놓인 위치를 통해 R7로만 잡힌다. `adapter.common`은 `adapter..` 아래라서 R7 범위 밖이며, 이것이 의도된 범위다.
  - 지시와 다른 점(판단 사항 J19): 지시에 적힌 클래스(`common.DateUtils`, `adapter.common.Mapper`, `domain.Book`)만으로는 R7이 **1건**(`DateUtils`)만 나온다. 기대값 "R7 2건"과 맞지 않는다. 기대값을 1로 낮추는 대신, 지시의 R7 문장에 있는 "그 외 base 직속 클래스도 위반" 경로를 덮는 `AppConstants`를 추가해 2건으로 맞췄다. 예외 경로를 음성 쪽에서도 검증하려고 `V7Application`도 함께 넣었다.
- 기존 픽스처 v1~v6은 R7에 걸리지 않음을 **실측으로 확인**했다(7.2 표의 R7 열이 모두 pass). 각 픽스처의 base가 `com.example.fixtures.vN`이고, 그 아래에는 domain/application/adapter만 있기 때문이다.
- `lending`에서도 통과한다. `LendingApplication`은 예외로 허용된다. `LendingIntegrationTest`(base 직속)와 `architecture.*`(비허용 최상위)는 테스트 코드라서 `DoNotIncludeTests`로 제외된다. **R7은 테스트 코드 제외를 전제로 한다**(7.5의 N4).

**변경 3 — 측정 JSON 스키마.** 주 지표를 "위반이 하나라도 있는 고유 클래스 수"(`violating_classes`)로 바꿨다. 규칙별 `count`는 1차와 같은 단위로 유지한다.
- 추출기 `ViolationExtractor`(`HexagonalRules` 옆, 같은 test 패키지)를 두었다. `EvaluationResult.handleViolations`로 받은 **위반 객체의 타입**으로 원인 클래스를 정한다. 실패 리포트 문자열은 파싱하지 않는다.

  | 위반 객체 타입 | 해당 규칙 | 원인 클래스 |
  | --- | --- | --- |
  | `Dependency` | R1~R3 | `getOriginClass()` |
  | `JavaAccess` | (방어용) | `getOriginOwner()` |
  | `Cycle` | R4 | `cycle.convertTo(Dependency.class)`의 출발 클래스와 도착 클래스 **전부** |
  | `JavaMember` | R5의 필드, 메서드, 생성자, 파라미터 조건 | `getOwner()` |
  | `JavaClass` | R5 클래스, R6, R7 | 그 클래스 |

  - 지시는 "타입별 핸들러 등록"이었다. 하지만 `handleViolations`를 타입별로 여러 번 등록하면 **어느 핸들러에도 맞지 않는 타입의 위반이 조용히 버려진다.** 그래서 `T=Object` 핸들러 하나에서 `instanceof`로 타입별 분기하고, 모르는 타입이 오면 `IllegalStateException`을 던진다. 위반이 있는데 원인 클래스가 0개인 경우에도 예외를 던진다. 타입별로 처리한다는 점은 같고, 누락을 감지할 수 있다는 점이 다르다.
  - R4: 지시가 우려한 `Slice` 단위 근사(순환에 포함된 슬라이스의 클래스 전부)를 쓰지 않았다. ArchUnit 1.3.2의 순환 위반 객체는 `Cycle`이며 `Convertible`이다. `convertTo(Dependency.class)`가 순환을 이루는 실제 클래스 간 의존을 돌려주므로, **순환에 실제로 참여한 클래스만** 집계한다. 민감도 M3(`Cycle` 분기를 지우면 "unhandled violation object types" 예외 발생)로 R4 위반이 실제로 `Cycle` 객체로 들어옴을 확인했다.
- `ArchitectureTest`의 구조를 바꿨다. **기존 테스트 1개를 규칙당 1개, 총 7개로 나눴다**(`r1_domain_no_outward_deps` … `r7_only_three_top_level_packages`). 각 테스트는 실행될 때마다, 지금까지 실행된 규칙 결과로 `lending.json`을 다시 쓴 뒤 assert한다.
  - 이유: 완료 기준 "`tests_executed` 7 이상"과 맞추고, 규칙별 성공/실패를 Gradle 결과에서 따로 볼 수 있게 하기 위함이다.
  - `by_rule`에는 **실행된 규칙만** 들어간다. 실행되지 않은 규칙을 0으로 적으면 "위반 없음"과 구분할 수 없기 때문이다. `tests_executed`는 실행된 규칙 테스트 수와 같다.
  - `HexagonalRules`에 규칙이 추가되었는데 테스트 메서드를 빠뜨리면, 모든 테스트가 실패하도록 ID 집합을 대조한다.
- 음성 검증은 같은 추출기로 **기대 원인 클래스 집합**까지 고정한다(7.2).

**변경 4 — archTest 가드.** 루트 `build.gradle.kts`의 `archTest`에 `TestListener`를 달아 루트 suite의 `testCount - skippedTestCount`를 센다. 0이면 `doLast`에서 `GradleException`을 던진다. 민감도 M4에서, `@ArchTag`를 지우자 `:lending:archTest executed 0 @Tag("arch") tests`로 빌드가 실패하고 `lending.json`이 생성되지 않음을 확인했다. 시작 시 `build/arch`를 비우는 동작은 유지했다.
- **파이프라인 판정 규칙(권고)**: 아래 셋을 모두 만족할 때만 측정 성공으로 본다. `tests_executed == 7`(0보다 큰 것만으로는 부족하다. 테스트 하나가 예외로 중단되면 일부 규칙만 기록될 수 있다), `by_rule`에 R1~R7 키가 모두 있음, 파일이 존재함.

**변경 5 — git.**
- 순서: `git init` → `.gitignore`(Gradle, IDE, `build/`, `.DS_Store`)와 `.gitattributes`(`gradlew` LF, `*.bat` CRLF, `*.jar` binary) 추가 → 1차 완료 상태를 첫 커밋으로 → 이번 변경을 두 번째 커밋으로.
- 1차 상태는 2차 작업을 시작하기 전에 커밋했으므로 재구성할 필요가 없었다. 커밋 전에 `clean build archTest` 통과를 다시 확인했다.
- **커밋 직전에 발견한 문제**: IDE 출력 폴더용으로 넣은 `out/` 패턴이 `adapter/out/`와 `application/port/out/`까지 무시해서, **포트와 영속성 어댑터, 픽스처 일부(java 20개)가 커밋에서 빠질 뻔했다.** `/out/`, `/*/out/`로 루트 기준 매칭만 하게 고쳤다. 추적된 java 파일 수와 디스크의 java 파일 수가 같은지(55 = 55) 확인한 뒤 커밋했다. Test_tamper 검출의 기준 커밋에 소스가 빠지면 변조를 판정할 수 없으므로 치명적인 문제였다.
- 커밋 메시지: 지시는 "영어 한 줄 + 본문"이고, `CLAUDE.md`의 규칙은 `<emoji> <Type>: <설명(한국어)>`다. 둘을 함께 만족하도록 **`<emoji> <Type>:` 형식에 영어 설명**을 썼다(판단 사항 J20).

### 7.2 음성 검증 7×8 (걸림 여부, 건수, 원인 클래스)

원인 클래스는 픽스처 base(`com.example.fixtures.vN`) 기준의 상대 이름이다. `FAIL(n)[…]` = 규칙이 AssertionError를 던짐, n = 건수, […] = 추출된 원인 클래스. 모든 칸이 `ViolationDetectionTest.fixtures()`의 기대값과 일치한다.

| 픽스처 | R1 | R2 | R3 | R4 | R5 | R6 | R7 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| v1 | **FAIL(3)** [domain.Book] | pass | pass | pass | pass | pass | pass |
| v2 | pass | **FAIL(5)** [application.service.LoanBookService] | pass | pass | pass | pass | pass |
| v3 | pass | pass | **FAIL(7)** [adapter.in.web.LoanController, adapter.out.persistence.BookJpaEntity] | pass | pass | pass | pass |
| v4 | **FAIL(2)** [domain.Book] | pass | pass | **FAIL(1)** [application.service.LoanPolicy, domain.Book] | pass | pass | pass |
| v5 | **FAIL(9)** [domain.Book, domain.Loan, domain.Member] | pass | pass | pass | **FAIL(5)** [domain.Book, domain.Loan, domain.Member] | pass | pass |
| v6 | pass | pass | pass | pass | pass | **FAIL(2)** [application.port.out.LoadBookPort, application.service.InMemoryBookStore] | pass |
| v7 | pass | pass | pass | pass | pass | pass | **FAIL(2)** [AppConstants, common.DateUtils] |
| v8 | **FAIL(1)** [domain.Member] | pass | pass | pass | pass | pass | pass |

픽스처별 `violating_classes`: v1=1, v2=1, v3=2, v4=2, v5=3, v6=2, v7=2, v8=1.
1차 대비 바뀐 칸은 v5의 R1(3 → 9, 7.1 변경 1)과 새 열 R7, 새 행 v7·v8뿐이다.

### 7.3 새 JSON 예시

`lending` (`lending/build/arch/lending.json`, 실제 출력):

```json
{
  "tests_executed" : 7,
  "violating_classes" : 0,
  "violating_classes_list" : [ ],
  "by_rule" : {
    "R1" : { "count" : 0, "classes" : [ ] },
    "R2" : { "count" : 0, "classes" : [ ] },
    "R3" : { "count" : 0, "classes" : [ ] },
    "R4" : { "count" : 0, "classes" : [ ] },
    "R5" : { "count" : 0, "classes" : [ ] },
    "R6" : { "count" : 0, "classes" : [ ] },
    "R7" : { "count" : 0, "classes" : [ ] }
  }
}
```

v7 (`ViolationDetectionTest`가 출력하는 `[json] v7` 행을 정렬한 것):

```json
{
  "tests_executed": 7,
  "violating_classes": 2,
  "violating_classes_list": [
    "com.example.fixtures.v7.AppConstants",
    "com.example.fixtures.v7.common.DateUtils"
  ],
  "by_rule": {
    "R1": {"count": 0, "classes": []},
    "R2": {"count": 0, "classes": []},
    "R3": {"count": 0, "classes": []},
    "R4": {"count": 0, "classes": []},
    "R5": {"count": 0, "classes": []},
    "R6": {"count": 0, "classes": []},
    "R7": {"count": 2, "classes": ["com.example.fixtures.v7.AppConstants", "com.example.fixtures.v7.common.DateUtils"]}
  }
}
```

### 7.4 민감도 확인

각 변형은 원본을 수정해 `./gradlew archTest --continue`로 실행한 뒤 원복했다(원복 후 `cmp`로 동일함 확인).

| # | 변형 | 결과 | 판정 |
| --- | --- | --- | --- |
| M1 | R7에서 `@SpringBootApplication` 예외 제거 | `lending` R7 **FAIL(1) [com.example.lending.LendingApplication]**, JSON `violating_classes`=1 / v7 `expected 2 violations but got 3` | ✅ 잡힘 (필수 1) |
| M2 | R1에서 `jakarta.validation..` 제거 | `v8 R1: expected violation but rule passed (missed)` | ✅ 잡힘 (필수 2) |
| M3 | `ViolationExtractor`에서 `Cycle` 분기 제거 | `IllegalStateException: unhandled violation object types` (v4 R4) | ✅ 추출기 누락이 조용히 지나가지 않음 |
| M4 | `ArchitectureTest`에서 `@ArchTag` 제거 | `:lending:archTest executed 0 @Tag("arch") tests` → BUILD FAILED, `lending.json` 없음 | ✅ 0개 가드 동작 |

1차의 S1~S10은 R1~R6 정의가 바뀌지 않았으므로(R1은 확장만 됨) 그대로 유효하다. 단, S8(R1에서 `jakarta.persistence..` 제거 → v5 R1 건수 감소)의 기대 건수는 이제 9 → 8이다.

### 7.5 새로 발견한 모호한 지점

- **N1. R5는 이제 R1에 완전히 포함된다.** R5의 4개 어노테이션(`@Entity`, `@Component`, `@Service`, `@JsonProperty`)이 모두 R1 블랙리스트 패키지에 속하게 되었다. R5의 원인 클래스(어노테이션이 붙은 멤버의 소유 클래스)는 항상 R1의 원인 클래스(그 어노테이션 의존의 출발 클래스)와 같다. 따라서 **주 지표 `violating_classes`에 R5가 더하는 클래스는 항상 0개다.** R5를 남길지, 아니면 R1이 잡지 못하는 어노테이션(예: 다른 프레임워크)용으로 재정의할지 정해야 한다.
- **N2. R4의 원인 클래스에는 "피해자" 쪽 클래스도 들어간다.** v4에서 `LoanPolicy`는 올바른 방향(application → domain)의 의존만 가졌지만, 순환 참여자로서 원인 클래스에 포함된다. 그 결과 v4의 `violating_classes`는 실제로 잘못 작성된 클래스 수(1, `Book`)보다 1 많은 2다. 지시의 정의("순환에 참여한 모든 클래스")를 따랐다. 하지만 "잘못된 엣지의 출발 클래스"로 볼지는 설계상 결정이 필요하다.
- **N3. `count`가 더 부풀었다.** R1에서 `@JsonProperty` 하나가 3건이 된다(어노테이션 + 멤버 타입 2개). 어노테이션 종류에 따라 1건당 무게가 다르다(`@NotNull`은 멤버 타입이 `java.*`뿐이라 1건). `count`를 주 지표에서 뺀 2차 결정을 뒷받침하는 사례다.
- **N4. R7은 테스트 코드 제외를 전제로 한다.** `DoNotIncludeTests`가 없으면 `lending`의 `LendingIntegrationTest`(base 직속)와 `architecture.*`(비허용 최상위)가 R7 위반이 된다. 실험 중 에이전트가 main 소스에 base 직속 클래스를 추가하면 R7 위반이고, 테스트 소스에 추가하면 위반이 아니다.
- **N5. R7의 화이트리스트는 최상위 패키지에만 적용된다.** `domain.common`, `application.util`, `adapter.common` 같은 하위 패키지는 자유롭다. 따라서 v7의 `DateUtils`를 `domain.common`으로 옮기기만 하면 R1~R7이 모두 통과한다. `adapter.common`을 통한 R3 우회(5절 7번 항목)도 그대로 남아 있다.
- **N6. 중첩·익명 클래스는 별개 클래스로 센다.** `violating_classes_list`에는 FQN이 그대로 들어간다. `Book`과 `Book$1`(익명 클래스)이 각각 1개로 집계된다. 바깥 클래스로 정규화할지 정해야 한다. (현재 픽스처와 `lending`에는 해당 사례가 없다.)
- **N7. R7 예외의 판정 기준.** 예외는 `@SpringBootApplication`이 "직접" 붙은 경우만이다. 메타 어노테이션으로 감싼 커스텀 부트 어노테이션이나, 부트 클래스의 중첩 클래스(`LendingApplication$Config`)는 위반이다. base 직속 부트 클래스가 여러 개여도 모두 허용된다.

### 7.6 2차 판단 사항

| # | 지점 | 선택 | 이유 |
| --- | --- | --- | --- |
| J19 | v7 기대 "R7 2건" vs 지시 클래스로는 1건 | `AppConstants`(base 직속, 비부트) 추가로 2건. `V7Application`(부트 예외)도 추가 | 기대값을 낮추지 않고, R7 문장의 두 위반 경로와 예외 경로를 모두 픽스처로 덮는다 |
| J20 | 커밋 메시지 언어 (지시: 영어 / CLAUDE.md: 한국어) | `<emoji> <Type>: <English>` | 이번 작업 지시를 우선하되 형식 규칙은 지킨다 |
| J21 | `handleViolations` 등록 방식 | `Object` 핸들러 1개 + `instanceof` 분기 + 모르는 타입이면 예외 | 타입별 등록은 누락이 조용히 지나간다(7.1 변경 3) |
| J22 | R4 원인 클래스 | `Cycle.convertTo(Dependency.class)`의 출발·도착 클래스 | 슬라이스 전체 근사보다 정확하다. ArchUnit 1.3.2 API로 가능함을 확인했다 |
| J23 | `ArchitectureTest` 구조 | 규칙당 `@ArchTest` 1개(7개), 실행 시마다 JSON 재작성 | `tests_executed`가 실제 실행된 측정 수를 뜻하게 하고, 규칙별 결과를 따로 보이게 한다 |
| J24 | `by_rule`에 미실행 규칙 | 넣지 않음(0으로 채우지 않음) | 미실행과 무위반을 구분하기 위함. 파이프라인은 `tests_executed == 7`과 R1~R7 키 존재를 확인해야 한다 |
| J25 | `.gitignore`의 `out/`, `bin/` | `/out/`, `/*/out/`, `/bin/`, `/*/bin/`로 루트 기준 매칭 | 헥사고날의 `adapter/out`, `port/out` 소스가 무시되는 문제를 막는다 |
| J26 | 추출기와 JSON 구조의 위치 | `ViolationExtractor.violatingClasses()`와 `report()` 둘 다 같은 클래스에 둠. `archRules` jar에 `ViolationExtractor*` 포함 | 양성·음성 테스트가 같은 추출 로직과 같은 JSON 구조를 쓰게 한다. 지시대로 클래스 하나만 둔다 |

### 7.7 2차 의존성 추가

| 모듈 | 의존성 | 이유 |
| --- | --- | --- |
| `violation-fixtures` (main) | `jakarta.validation:jakarta.validation-api` (BOM 관리 버전) | v8의 도메인 `@NotNull`을 컴파일하기 위함. `spring-boot-starter-web`과 `data-jpa`에는 포함되지 않는다 |
| `lending` (test) | 추가 없음 | `ArchitectureTest`의 JSON 직렬화에는 `spring-boot-starter-web`이 이미 가져오는 Jackson `ObjectMapper`를 쓴다 |

---

## 8. 3차 변경 — 주 지표 합집합 범위 (2026-10-15)

**변경**: `violating_classes` / `violating_classes_list` = **R1·R2·R3·R6·R7** 의 `classes` 합집합. R4·R5 는 `by_rule` 에만 남는다. `lending.json` 에 `"primary_rules": ["R1","R2","R3","R6","R7"]` 를 추가해 JSON 자체가 범위를 선언한다. 범위는 `ViolationExtractor.PRIMARY_RULES` 한 곳에서 정한다.

**근거**
- **R4 과대 집계** (7.5 N2): R4 원인 클래스는 순환 참여 클래스 전부라서, 정방향 의존만 가진 클래스(v4 의 `LoanPolicy`)까지 들어간다. 또 패턴 A 의 R4 ⊆ R1 ∪ R2 (4절) 이므로, 잘못된 역방향 엣지의 출발 클래스는 이미 R1/R2 로 집계된다. R4 를 합집합에 넣으면 피해자 쪽 클래스만 더해진다.
- **R5 ⊂ R1** (7.5 N1): R5 의 4개 어노테이션이 모두 R1 블랙리스트 패키지(2차 변경 이후)에 속하므로 R5 원인 클래스는 항상 R1 원인 클래스의 부분집합이다.

**갱신된 음성 검증 7×8** (규칙별 칸은 7.2 와 동일. 바뀐 것은 마지막 열)

| 픽스처 | R1 | R2 | R3 | R4 | R5 | R6 | R7 | violating_classes (주 지표) | 7.2 대비 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| v1 | **FAIL(3)** [domain.Book] | pass | pass | pass | pass | pass | pass | 1 [domain.Book] | 1 → 1 |
| v2 | pass | **FAIL(5)** [application.service.LoanBookService] | pass | pass | pass | pass | pass | 1 [application.service.LoanBookService] | 1 → 1 |
| v3 | pass | pass | **FAIL(7)** [adapter.in.web.LoanController, adapter.out.persistence.BookJpaEntity] | pass | pass | pass | pass | 2 [adapter.in.web.LoanController, adapter.out.persistence.BookJpaEntity] | 2 → 2 |
| v4 | **FAIL(2)** [domain.Book] | pass | pass | **FAIL(1)** [application.service.LoanPolicy, domain.Book] | pass | pass | pass | **1** [domain.Book] | **2 → 1** (LoanPolicy 제외) |
| v5 | **FAIL(9)** [domain.Book, domain.Loan, domain.Member] | pass | pass | pass | **FAIL(5)** [domain.Book, domain.Loan, domain.Member] | pass | pass | 3 [domain.Book, domain.Loan, domain.Member] (= R1 원인 클래스) | 3 → 3 |
| v6 | pass | pass | pass | pass | pass | **FAIL(2)** [application.port.out.LoadBookPort, application.service.InMemoryBookStore] | pass | 2 [application.port.out.LoadBookPort, application.service.InMemoryBookStore] | 2 → 2 |
| v7 | pass | pass | pass | pass | pass | pass | **FAIL(2)** [AppConstants, common.DateUtils] | 2 [AppConstants, common.DateUtils] | 2 → 2 |
| v8 | **FAIL(1)** [domain.Member] | pass | pass | pass | pass | pass | pass | 1 [domain.Member] | 1 → 1 |

`ViolationDetectionTest` 는 이제 픽스처별로 `primary_rules` 값, `violating_classes_list`, `violating_classes` 까지 assert 한다.

**`lending.json` (실제 출력, 발췌)**

```json
{
  "tests_executed" : 7,
  "primary_rules" : [ "R1", "R2", "R3", "R6", "R7" ],
  "violating_classes" : 0,
  "violating_classes_list" : [ ],
  "by_rule" : { "R1" : { "count" : 0, "classes" : [ ] }, "...": "...", "R7" : { "count" : 0, "classes" : [ ] } }
}
```

**민감도**: `PRIMARY_RULES` 에 R4 를 다시 넣으면 8개 픽스처 모두 `primary_rules expected [R1, R2, R3, R6, R7] but was [R1, R2, R3, R4, R6, R7]` 로 실패하고, v4 는 추가로 `expected violating_classes 1 [domain.Book] but got 2 [application.service.LoanPolicy, domain.Book]` 로 실패한다. 확인 후 원복했다.

**남은 질문**: R4 를 레이어 내부 순환(패턴 B 계열)으로, R5 를 R1 이 못 잡는 어노테이션용으로 재정의하면 다시 주 지표에 넣을 수 있다. 현재는 미결.
