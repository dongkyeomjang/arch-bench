# arch-bench REPORT — 헥사고날 ArchUnit 규칙 R1~R6 구현 및 양방향 검증

- 환경: Java 21 (Temurin 21.0.8), Gradle 8.14.3 (wrapper), Spring Boot 3.5.3, ArchUnit 1.3.2, JUnit 5.12, H2
- 최종 상태: `./gradlew clean build archTest` → **BUILD SUCCESSFUL**
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
