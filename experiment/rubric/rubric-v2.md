# 의미론적 아키텍처 위반 루브릭 v2

- 출처: 실험 설계 문서 "의미론적 위반 평가 프로토콜" 절(2026-10-13 작성, 2026-10-16 D1 증상 추가). v1 = `rubric-v1.md`(TASK10).
- **v1 → v2 변경(TASK11):** 차원 정의와 점수 척도(0~3)는 유지한다. 바꾼 것은 아래 다섯 가지다. 무엇을 왜 바꿨는지는 끝의 "변경 이력" 표에 있다.
  1. 예시 출처를 바꿨다.
     - 채점 대상 세션 코드에서 뽑은 예시를 모두 지웠다.
     - 0점 예시는 참조 구현, 1~2점 예시는 침식 시드의 내장 위반 E1~E5, 3점 예시는 합성 코드다.
  2. D1 1점 문구에서 "존재 여부"를 지우고, 조회 실패 처리는 비즈니스 규칙이 아니라고 명시했다.
  3. D1 에 "특정 ID 집합이 규칙의 분기 조건 = 2점"을 확정 기재했다(v1 의 잠정 판정).
  4. 잠정 점수 표시와 "판단이 갈리는 사례" 예시를 지웠다. v1 사전 채점에서 채점기가 예시의 점수를 그대로 가져갔기 때문이다.
  5. v1 모호 지점 9개의 처리를 변경 이력 표로 정리했다.
- **이 판으로 다시 채점하지 않았다.** 채점은 분산이 확인된 뒤에 한다(작업 지시 11).
- **v2 수정(TASK12, 판 번호는 그대로):**
  - E5 를 D2 예시에서 뺐다. E5 는 포트 우회(R3)이며 루브릭 차원에 해당하지 않는다.
  - D1 2점 예시에 "한도·폐기·특정 ID 예외를 모두 서비스에 둔 패턴"을 합성 코드로 더했다. 파일럿 3 L0 세션에서 관찰된 유형이지만, 세션 코드를 인용하지 않고 다른 도메인(장비 대여)으로 새로 썼다(예시 순환 방지, 변경 이력 #1).
  - 그래서 1~2점 예시의 출처는 "침식 시드 E1·E2·E4 와 합성 코드"다.

## 평가 대상과 단위

- **대상:** 세션 종료 시점(턴 15 후)의 `src/main/java` 전체.
  - 사람 평가는 턴 8 후 중간 상태도 본다. LLM 보조 채점은 최종 상태만 본다.
- **평가자가 보는 것:** 익명화된 사본만 본다(`experiment/scripts/anonymize.sh <workspace> <out-dir> <blind-id>`).
  - 하네스 수준·모델·반복 번호·시드 종류를 모른다.
  - 사본에서는 `.claude/`·`CLAUDE.md`·하네스 상태·ArchUnit 채점기 파일을 지우고, git 이력을 커밋 1개로 합친다.
  - `blind-id ↔ session-id` 대응은 `$OUT_ROOT/blind-map.json` 에 둔다.
- **점수:** 차원당 0~3점이다. 세션 점수는 4차원 합(0~12)이고, 차원별 점수도 따로 보고한다.
- **점수의 뜻:** 점수는 "가장 심한 증상"이 아니라 "전체 코드에서 그 차원의 침식 정도"다. 한 곳의 심각한 증상과 여러 곳의 가벼운 증상은 같은 점수가 될 수 있다.
- **예시의 뜻:** 아래 코드 예시는 "그 증상이 그 하나만 있을 때의 수준"을 보여 준다. 실제 점수는 전체 코드를 보고 정한다.
  - 예시와 같은 코드를 찾았다고 그 점수를 그대로 주지 않는다.
  - 참조 구현은 모든 차원 0점의 기준점이다.

## 네 차원

### D1 — 도메인 빈약화 (Anemic Domain)

**정의:** 비즈니스 규칙(상태 전이 조건, 금액·기간 계산, 허용 여부 판단, 한도)이 도메인 객체의 메서드가 아니라 서비스·컨트롤러·어댑터에 있는 정도.

**증상:**
- 서비스나 컨트롤러의 `if`/`switch` 가 도메인 상태를 꺼내 판단한다
- 도메인 객체가 getter/setter 묶음으로만 쓰인다
- 같은 규칙이 여러 서비스에 중복된다
- **도메인 객체 안의 매직 ID·상수 리스트** (예: 특정 회원 ID 를 코드에 박아 예외 처리) — 규칙이 데이터가 아니라 리터럴에 의존한다 (2026-10-16 추가)

**비즈니스 규칙이 아닌 것 (v2 명시):**
- 없는 ID 를 조회했을 때의 처리(`loadX(id).isEmpty()` → NotFound 예외 → HTTP 404)는 정상 조회 처리이며 비즈니스 규칙이 아니다.
- 요청 형식 검증(필수 값 누락·형식 오류 → 400)도 같다.
- 둘 다 서비스·컨트롤러에 있어도 D1 증상으로 세지 않는다.

| 점수 | 기준 |
| --- | --- |
| 0 | 모든 비즈니스 규칙이 도메인 객체의 메서드에 있다. 서비스는 포트 호출과 도메인 메서드 호출을 조율만 한다 |
| 1 | 사소한 검증 1~2건(null 체크 등, 비즈니스 규칙이 아닌 조회 실패 처리는 세지 않는다)이 서비스에 있다. 또는 매직 값 1건이 도메인에 있다 |
| 2 | 핵심 규칙 일부(한도, 상태 전이, 계산)가 서비스·컨트롤러에 있다. 또는 비즈니스 규칙이 매직 값에 의존한다 |
| 3 | 도메인 객체가 getter/setter 뿐이고 모든 규칙이 바깥에 있다. 또는 규칙이 ID 비교로만 구성된다 |

**확정 기재 (v2):**
- **특정 ID 집합이 한도 규칙의 분기 조건이면 2점 수준이다.** 예: 도메인 객체가 회원 UUID 리터럴 집합을 상수로 들고, 그 집합에 속하면 한도 검사를 건너뛴다(도서 대출 과제 턴 8 유형).
  - 이유: 그 집합은 규칙의 분기 조건으로만 존재하므로 "비즈니스 규칙이 매직 값에 의존한다"에 해당한다.
  - 일반 한도 검사가 따로 있으므로 "규칙이 ID 비교로만 구성된다"(3점)는 아니다.
- 1점의 "매직 값 1건"은 규칙의 분기 조건에 쓰이지 않는 리터럴이다(예: 기본 표시 문자열, 로그 메시지의 고정 코드).

**코드 예시**

- **0 수준** — 대출 가능 여부와 상태 전이가 도메인 객체의 메서드에 있다. 서비스는 그 메서드를 부르고 저장을 조율한다. (출처: 참조 구현)

  ```java
  // domain/Book.java
  public Loan loanTo(UUID memberId, LocalDate loanedAt) {
      if (status == BookStatus.DISCARDED) {
          throw new BookDiscardedException(id);
      }
      if (status == BookStatus.ON_LOAN) {
          throw new BookAlreadyOnLoanException(id);
      }
      ...
  }
  // domain/Member.java
  public void ensureCanBorrow(long activeLoanCount) {
      ...
      if (!tier.allowsAnother(activeLoanCount)) {
          throw new LoanLimitExceededException(id, tier.maxActiveLoans());
      }
  }
  ```

- **2 수준** — 대출 가능 여부(상태 전이 조건)를 서비스가 도메인 상태를 꺼내 판단하고 상태를 직접 바꾼다. 도메인 `Book` 에는 `getStatus()`·`setStatus()` 만 있다. (출처: 침식 시드 E1)

  ```java
  // application/service/LoanBookService.java
  if (book.getStatus() == BookStatus.ON_LOAN) {
      throw new BookAlreadyOnLoanException(book.getId());
  }
  Loan loan = Loan.start(book.getId(), command.memberId(), LocalDate.now());
  book.setStatus(BookStatus.ON_LOAN);
  ```

- **2 수준** — 도메인 객체에 상태가 있지만, 새로 생긴 규칙(동시 대여 한도, 퇴역 장비 대여 금지, 특정 ID 의 한도 면제)이 모두 서비스의 `if`/`throw` 로 들어가 있다. 도메인은 상태를 꺼내 주는 getter 만 쓰인다. (출처: 합성 예시 — 파일럿 3 L0 에서 관찰된 유형을 다른 도메인으로 새로 씀)

  ```java
  // application/service/RentEquipmentService.java
  private static final Set<UUID> LIMIT_FREE_CUSTOMERS = Set.of(
          UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"));

  public Rental rent(UUID equipmentId, UUID customerId) {
      Equipment eq = loadEquipmentPort.load(equipmentId).orElseThrow(...);
      if (eq.getCondition() == Condition.RETIRED) {
          throw new EquipmentRetiredException(equipmentId);
      }
      long open = loadRentalPort.countOpenRentals(customerId);
      if (!LIMIT_FREE_CUSTOMERS.contains(customerId) && open >= Rental.MAX_OPEN_PER_CUSTOMER) {
          throw new RentalLimitExceededException(customerId);
      }
      ...
  }
  ```

  - 기존 규칙 하나(E1)만 서비스에 있는 경우와 같은 수준이다. 규칙이 여러 개라도 도메인의 다른 규칙(예: 반납 상태 전이)이 도메인 메서드에 있으면 3점이 아니다.

- **3 수준** — 도메인 객체가 getter/setter 뿐이고 모든 규칙이 서비스에 있다. (출처: 합성 예시)

  ```java
  public class Loan {            // 도메인
      private UUID id; private LocalDate dueAt; private LocalDate returnedAt;
      public LocalDate getDueAt() { return dueAt; }
      public void setReturnedAt(LocalDate d) { this.returnedAt = d; }
      // 그 밖의 메서드 없음
  }
  public class ReturnService {   // 서비스가 규칙을 모두 가짐
      public long returnLoan(Loan loan, LocalDate today) {
          if (loan.getReturnedAt() != null) throw new IllegalStateException("already returned");
          loan.setReturnedAt(today);
          long late = ChronoUnit.DAYS.between(loan.getDueAt(), today);
          return late > 0 ? late * 100 : 0;
      }
  }
  ```

### D2 — 포트 오염 (Port Contamination)

**정의:** `application.port` 인터페이스의 시그니처가 특정 어댑터 기술에 종속된 정도.

**증상:**
- 포트 메서드가 JPA 엔티티, HTTP 요청·응답 타입, 외부 SDK DTO 를 받거나 돌려준다
- Spring Data 의 `Page`/`Pageable`/`Sort` 가 포트 시그니처에 있다
- 포트가 사실상 리포지토리 인터페이스를 복제한다 (`findById`, `save`, `deleteAll` 등 CRUD 나열)
- 포트가 한 어댑터 구현에만 맞는 형태다 (다른 저장소로 바꾸면 포트도 바꿔야 한다)

| 점수 | 기준 |
| --- | --- |
| 0 | 모든 포트가 도메인 타입과 단순 값(ID, 날짜, 문자열, 도메인 전용 record)만 주고받는다 |
| 1 | 포트 1개가 기술 타입을 노출한다 |
| 2 | 포트 여러 개가 기술 종속이거나, 포트 하나가 CRUD 리포지토리를 그대로 복제한다 |
| 3 | 포트가 사실상 어댑터 인터페이스의 복제다. 포트와 어댑터를 바꿔 끼울 수 없다 |

**코드 예시**

- **0 수준** — 외부 SDK 응답(Jackson 어노테이션 포함)과 Spring Data `Page` 는 어댑터 안에만 있고, 포트는 도메인 타입·도메인 전용 record 만 주고받는다. (출처: 참조 구현)

  ```java
  // application/port/out/CreditCheckPort.java
  CreditCheck check(Member member);
  // application/port/out/LoadLoanPort.java
  LoanSlice loadActiveLoans(UUID memberId, int page, int size);
  ```

- **1 수준** — 포트 1개가 JPA 엔티티를 돌려준다. (출처: 침식 시드 E2)

  ```java
  // application/port/out/LoadBookPort.java
  Optional<Book> loadBook(UUID bookId);
  BookJpaEntity findRaw(UUID id);
  ```

- **3 수준** — 포트가 리포지토리 인터페이스를 그대로 복제하고 JPA 엔티티를 주고받는다. (출처: 합성 예시)

  ```java
  public interface BookPort {
      BookJpaEntity save(BookJpaEntity entity);
      Optional<BookJpaEntity> findById(UUID id);
      List<BookJpaEntity> findAll();
      Page<BookJpaEntity> findAll(Pageable pageable);
      void deleteById(UUID id);
  }
  ```

### D3 — 애그리거트 경계 (Aggregate Boundary)

**정의:** 트랜잭션·불변식 경계가 애그리거트 루트와 일치하는 정도.

**증상:**
- 하나의 유스케이스(서비스 메서드 한 번)가 서로 다른 애그리거트 루트 여러 개의 상태를 직접 바꾼다
- 다른 애그리거트를 ID 가 아니라 객체 그래프로 붙잡고 있다
- 불변식이 도메인 객체가 아니라 DB 제약(unique, FK)이나 어댑터 검사에만 존재한다
- 애그리거트 개념이 없고 서비스가 테이블 단위로 조작한다

| 점수 | 기준 |
| --- | --- |
| 0 | 유스케이스마다 애그리거트 루트 1개만 수정한다. 다른 애그리거트는 ID 로 참조한다. 불변식은 도메인 안에 있다 |
| 1 | 1곳에서 애그리거트 2개를 직접 수정한다 (예: 반납 시 Book 과 Loan 을 한 서비스가 각각 바꿈 — 단, 도메인 이벤트나 명시적 조율 없이) |
| 2 | 불변식이 DB 제약이나 어댑터에만 존재한다. 또는 여러 유스케이스가 애그리거트 경계를 넘는다 |
| 3 | 애그리거트 개념 부재. 서비스가 테이블 단위로 엔티티를 조작한다 |

**참고:** 이 도메인에서 Book·Loan·Member·Reservation 은 각각 별도 애그리거트 루트로 본다.
- 반납이 Book 상태와 Loan 상태를 함께 바꾸는 것은 피할 수 없다.
- 그래서 둘을 **명시적으로 조율하는 코드**가 있으면 1점이 아니라 0점이다. 명시적 조율의 예: 도메인 서비스, 도메인 이벤트, 또는 `Loan.return()` 이 Book 을 ID 로만 참조하고 서비스가 두 저장을 순서대로 호출하는 것.

**코드 예시**

- **0 수준** — 반납이 Loan 과 Book 을 함께 바꾸지만, Loan 은 Book 을 ID 로만 참조하고 서비스가 두 저장을 순서대로 부른다(위 참고의 명시적 조율). (출처: 참조 구현)

  ```java
  // application/service/ReturnBookService.java
  loan.returnOn(...);
  Book book = loadBookPort.loadBook(loan.getBookId()).orElseThrow(...);
  book.receiveReturn();
  saveLoanPort.saveLoan(loan);
  saveBookPort.saveBook(book);
  ```

- **1·2 수준** — 침식 시드·참조 구현에 해당 사례가 없다. 세션 코드에서 뽑은 예시는 v2 에서 쓰지 않는다.
- **3 수준** — 서비스가 테이블 단위로 엔티티를 조작하고, 애그리거트 개념이 없다. (출처: 합성 예시)

  ```java
  public void loan(UUID bookId, UUID memberId) {
      BookRow book = bookRepo.findById(bookId).get();
      MemberRow member = memberRepo.findById(memberId).get();
      book.status = "ON_LOAN";
      member.loanCount++;
      loanRepo.save(new LoanRow(bookId, memberId, LocalDate.now()));
      bookRepo.save(book);
      memberRepo.save(member);
  }
  ```

### D4 — 응집도 붕괴 (Cohesion Collapse)

**정의:** 모듈 경계는 지키지만 그 안에서 책임이 뒤섞인 정도.

**증상:**
- 한 서비스가 서로 무관한 유스케이스 여러 개를 맡는다 (예: 대출 처리 + 월별 통계 + 회원 정지)
- 만능 유틸 클래스가 도메인 로직을 담는다
- 새 기능이 기존 클래스에 억지로 덧붙여져 클래스 이름과 책임이 안 맞는다
- 패키지 경계는 맞는데 패키지 안 클래스들이 서로 무관하다

| 점수 | 기준 |
| --- | --- |
| 0 | 서비스당 유스케이스 1~2개, 클래스 이름이 책임을 정확히 말한다 |
| 1 | 무관한 책임 1건이 어느 클래스에 혼재한다 |
| 2 | 만능 서비스 1개가 존재한다 (유스케이스 4개 이상, 또는 이름과 무관한 책임) |
| 3 | 패키지 경계만 있고 내부는 뒤섞여 있다. 클래스 이름으로 책임을 예측할 수 없다 |

**코드 예시**

- **0 수준** — 서비스 하나가 서로 짝인 유스케이스 2개(정지·해제)를 맡는다. 나머지 서비스는 유스케이스 1개다. (출처: 참조 구현)

  ```java
  // application/service/SuspendMemberService.java
  public Member suspendMember(UUID memberId) { ... }
  public Member unsuspendMember(UUID memberId) { ... }
  ```

- **1 수준** — 도메인의 기간 계산(대출 기한)이 도메인 밖의 범용 유틸 패키지로 나가 있고, 도메인이 그것을 쓴다. (출처: 침식 시드 E4)

  ```java
  // common/DateUtils.java  (최상위 common 패키지)
  public static LocalDate addDays(LocalDate date, int days) { return date.plusDays(days); }
  // domain/Loan.java
  return new Loan(UUID.randomUUID(), bookId, memberId, loanedAt, DateUtils.addDays(loanedAt, LOAN_PERIOD_DAYS));
  ```

- **3 수준** — 한 클래스가 무관한 유스케이스를 모두 맡아 이름으로 책임을 예측할 수 없다. (출처: 합성 예시)

  ```java
  @Service
  public class LibraryManager {
      public Loan loan(UUID bookId, UUID memberId) { ... }
      public void suspend(UUID memberId) { ... }
      public MonthlyStats stats(YearMonth month) { ... }
      public List<Book> search(String title) { ... }
      public void sendOverdueEmails() { ... }
      public String formatReceipt(Loan loan) { ... }
  }
  ```

- 침식 시드 E3(도메인 `Member` 에 `@Entity`·`@Id`·`@Column`, 별도 JPA 엔티티 없음)는 구조 규칙(R1·R5)으로 잡히고, 네 차원 어디의 예시로도 쓰지 않는다(지시서 표의 루브릭 열 "—").
- **침식 시드 E5(인바운드 어댑터가 포트를 거치지 않고 리포지토리를 직접 써서 JPA 엔티티를 응답으로 냄)는 포트 우회(R3)이며 루브릭 차원에 해당하지 않는다.** 포트 시그니처가 오염된 것이 아니므로 D2 로 채점하지 않는다(TASK12 에서 D2 예시에서 뺌). 구조 지표 R3 로만 센다.

## 평가 절차 (요약)

1. **교정 라운드:** 파일럿 세션으로 평가자 2인이 독립 채점 → 불일치 토론 → 루브릭 문구 수정 → 재채점 → Cohen's κ ≥ 0.6 확인. 미달이면 반복.
2. **본 평가:** 층화 표본(조건당 5세션)을 2인이 독립 채점한다. 차원별 가중 κ 와 ICC(2,1) 를 보고한다. 불일치는 토론 후 합의 점수.
3. **LLM 보조 채점:** 사람 채점 결과로 교정한다.
   - Spearman ρ ≥ 0.7 이면 나머지를 LLM 으로 채우고 "LLM 보조"로 명시 보고한다.
   - 미달이면 사람 평가만으로 검정한다.
   - 도구: `experiment/runner/rate_semantic.py`. 같은 입력을 3회 채점해 분산을 기록한다.

## 근거 문헌

- Evans, *Domain-Driven Design* (2003) — 애그리거트, 빈약한 도메인 모델
- Vernon, *Implementing Domain-Driven Design* (2013) — 애그리거트 설계 규칙(한 트랜잭션에 하나의 애그리거트, ID 참조)
- Cockburn, *Hexagonal Architecture* (2005) — 포트는 애플리케이션의 언어로 말한다
- Fowler, *AnemicDomainModel* (2003) — D1 의 명칭 출처

## 변경 이력 (v1 → v2)

v1 사전 채점 보고서(`experiment/runs/semantic-pretest.md` 5절 질문 4·2·5)의 모호 지점 9개와 그 밖의 변경이다. "미처리"는 작업 지시 11 의 v2 범위(예시 출처, D1 1점 문구, 턴 8 확정)를 넘는 것이다. 교정 라운드 안건으로 남긴다.

| # | v1 의 모호 지점 | v2 처리 |
|---|---|---|
| 1 | 예시가 채점 대상 세션에서 뽑혀 채점기가 예시 점수를 그대로 가져감(순환). D3 "판단이 갈리는 사례"도 같은 효과 | **처리.** 세션 코드 예시를 모두 지우고 참조 구현(0)·침식 시드(1~2)·합성(3)으로 바꿨다. "잠정" 표시와 D3 "판단이 갈리는 사례" 예시를 지웠다. "예시와 같은 코드를 찾았다고 그 점수를 그대로 주지 않는다"를 평가 단위에 추가했다 |
| 2 | D1 "사소한 검증 1~2건"의 상한, 건수 대 성격 | **일부 처리.** 참조 구현에서 3건 이상이 된 원인(조회 실패 처리)을 비즈니스 규칙에서 뺐다(#2·질문 2). 3건 이상인 진짜 사소한 검증의 점수는 미처리 |
| 3 | D1 "이미 그 상태면 아무것도 안 함"(서비스의 멱등 처리)이 상태 전이 규칙인지 | 미처리 |
| 4 | D1 상수 한도(`MAX_CONCURRENT_LOANS = 5`)가 매직 값인지 | **일부 처리.** 1점의 "매직 값 1건"을 "규칙의 분기 조건에 쓰이지 않는 리터럴"로 한정했다. 규칙 안의 상수 한도(데이터화되지 않은 값)가 2점 "매직 값에 의존"인지는 미처리 |
| 5 | D1 서비스의 허용 판단(`if (!creditCheck.approved())`, `fee > 0` 집계)이 사소한지 핵심인지 | 미처리 |
| 6 | D3 "명시적 조율"이 애그리거트 셋 이상·조율 객체 없는 삭제에도 적용되는지, `Book.loanTo` 가 `Loan` 을 만드는 팩토리인 점 | 미처리. 이 경계 사례를 0점 쪽으로 기울이던 v1 의 "판단이 갈리는 사례" 예시는 #1 로 지웠다 |
| 7 | D3 순서 불변식이 DB 채번에만 있을 때 | 미처리 |
| 8 | D2 "한 어댑터 구현에만 맞는 형태"(`loadXxxForUpdate`), 도메인 record 가 SDK 필드를 그대로 옮긴 경우 | 미처리 |
| 9 | D4 횡단 관심사(감사 기록 호출을 서비스마다 덧붙임) | 미처리 |
| — | (질문 2) 참조 구현 D1=1 의 원인: "존재 여부" 문구 | **처리.** D1 1점에서 "존재 여부"를 지우고 "없는 ID → 404 는 정상 조회 처리이며 비즈니스 규칙이 아니다"를 명시했다 |
| — | (질문 5) 턴 8 하드코딩의 점수 | **처리.** "특정 ID 집합이 한도 규칙의 분기 조건 = 2점"을 확정 기재했다. 1점의 "매직 값 1건"은 분기에 쓰이지 않는 리터럴로 좁혔다(#4) |
| — | 침식 시드 E5 의 차원 | **추가.** 지시서대로 D2=1 예시로 넣었다. 포트 시그니처 문제가 아니라 포트 우회라서 D2 정의("포트 인터페이스의 시그니처")와 어긋난다. 교정 라운드 안건 → **TASK12 처리:** 이전 결정: D2=1 예시 → 변경: 예시에서 빼고 "포트 우회(R3), 루브릭 차원 아님" 명시, 사유: D2 정의와 어긋남(작업 지시 12) |
| — | D1 2점의 "여러 규칙이 서비스에" 유형 | **TASK12 추가.** 파일럿 3 L0 의 한도·폐기·특정 ID 서비스 배치를 합성 코드(장비 대여)로 재작성해 2점 예시로 넣었다. 세션 코드 직접 인용은 하지 않았다(#1 순환 방지) |
