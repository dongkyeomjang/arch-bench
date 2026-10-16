# 의미론적 아키텍처 위반 루브릭 v1

- **출처:** 실험 설계 문서 "의미론적 위반 평가 프로토콜" 절(2026-10-13 작성, 2026-10-16 D1 증상 추가).
- **이 파일에서 덧붙인 것(TASK10):** 차원 정의와 점수 기준은 원문 그대로다. 덧붙인 것은 세 가지다.
  - 평가 단위·익명화 절차 명시
  - 차원별 코드 예시
  - 턴 8 하드코딩 예시의 잠정 점수(설계자 확인 필요)

## 평가 대상과 단위

- **대상:** 세션 종료 시점(턴 15 후)의 `src/main/java` 전체.
  - 사람 평가는 턴 8 후 중간 상태도 본다. LLM 보조 채점은 최종 상태만 본다.
- **평가자가 보는 것:** 익명화된 사본만 본다(`experiment/scripts/anonymize.sh <workspace> <out-dir> <blind-id>`).
  - 하네스 수준·모델·반복 번호를 모른다.
  - 사본에서는 `.claude/`·`CLAUDE.md`·하네스 상태·ArchUnit 채점기 파일을 지우고, git 이력을 커밋 1개로 합친다.
  - `blind-id ↔ session-id` 대응은 `$OUT_ROOT/blind-map.json` 에 둔다.
- **점수:** 차원당 0~3점이다. 세션 점수는 4차원 합(0~12)이고, 차원별 점수도 따로 보고한다.
- **점수의 뜻:** 점수는 "가장 심한 증상"이 아니라 "전체 코드에서 그 차원의 침식 정도"다. 한 곳의 심각한 증상과 여러 곳의 가벼운 증상은 같은 점수가 될 수 있다.

## 네 차원

### D1 — 도메인 빈약화 (Anemic Domain)

**정의:** 비즈니스 규칙(상태 전이 조건, 금액·기간 계산, 허용 여부 판단, 한도)이 도메인 객체의 메서드가 아니라 서비스·컨트롤러·어댑터에 있는 정도.

**증상:**
- 서비스나 컨트롤러의 `if`/`switch` 가 도메인 상태를 꺼내 판단한다
- 도메인 객체가 getter/setter 묶음으로만 쓰인다
- 같은 규칙이 여러 서비스에 중복된다
- **도메인 객체 안의 매직 ID·상수 리스트** (예: 특정 회원 ID 를 코드에 박아 예외 처리) — 규칙이 데이터가 아니라 리터럴에 의존한다 (2026-10-16 추가)

| 점수 | 기준 |
| --- | --- |
| 0 | 모든 비즈니스 규칙이 도메인 객체의 메서드에 있다. 서비스는 포트 호출과 도메인 메서드 호출을 조율만 한다 |
| 1 | 사소한 검증 1~2건(null 체크, 존재 여부)이 서비스에 있다. 또는 매직 값 1건이 도메인에 있다 |
| 2 | 핵심 규칙 일부(한도, 상태 전이, 계산)가 서비스·컨트롤러에 있다. 또는 비즈니스 규칙이 매직 값에 의존한다 |
| 3 | 도메인 객체가 getter/setter 뿐이고 모든 규칙이 바깥에 있다. 또는 규칙이 ID 비교로만 구성된다 |

**코드 예시**

- **0 수준** — 한도 규칙이 도메인 객체의 메서드에 있고, 한도는 회원의 속성(등급)이다. (출처: 참조 구현)

  ```java
  // domain/Member.java
  public void ensureCanBorrow(long activeLoanCount) {
      if (suspended) {
          throw new MemberSuspendedException(id);
      }
      if (!tier.allowsAnother(activeLoanCount)) {
          throw new LoanLimitExceededException(id, tier.maxActiveLoans());
      }
  }
  // domain/LoanTier.java
  STANDARD(5),
  SPONSOR(Integer.MAX_VALUE);
  ```

  같은 회원의 중복 예약 금지도 도메인 `ReservationQueue.place` 안에 있다. (출처: 참조 구현)

  ```java
  // domain/ReservationQueue.java
  if (waiting.stream().anyMatch(r -> r.getMemberId().equals(memberId))) {
      throw new AlreadyReservedException(bookId, memberId);
  }
  ```

- **1 수준** — 존재 여부 검증이 서비스에 있다. (출처: 참조 구현)

  ```java
  // application/service/ListCurrentLoansService.java
  if (loadMemberPort.loadMember(query.memberId()).isEmpty()) {
      throw new MemberNotFoundException(query.memberId());
  }
  ```

- **2 수준 (잠정)** — 비즈니스 규칙(5권 한도 면제)이 회원 ID 리터럴에 의존한다. (출처: 파일럿 세션 코드, 턴 8)
  - 파일럿 4개 세션이 거의 같은 형태였다.
  - 1점("매직 값 1건이 도메인에 있다")과 2점("비즈니스 규칙이 매직 값에 의존한다") 중 2점을 잠정으로 골랐다. 상수 집합이 한도 규칙의 분기 조건으로 쓰이기 때문이다.
  - **설계자 확정 필요**(`experiment/runs/semantic-pretest.md` 질문 5).

  ```java
  // domain/Member.java
  private static final Set<UUID> UNLIMITED_LOAN_MEMBER_IDS = Set.of(
          UUID.fromString("11111111-1111-1111-1111-111111111111"),
          UUID.fromString("22222222-2222-2222-2222-222222222222"),
          UUID.fromString("33333333-3333-3333-3333-333333333333"));
  ...
  public void ensureCanBorrow(long currentLoanCount) {
      ...
      if (UNLIMITED_LOAN_MEMBER_IDS.contains(id)) {
          return;
      }
      if (currentLoanCount >= MAX_CONCURRENT_LOANS) {
          throw new LoanLimitExceededException(id);
      }
  ```

  허용 여부 판단(같은 회원의 중복 예약 금지)이 도메인이 아니라 서비스에 있다. 위 0 수준의 `ReservationQueue.place` 와 같은 규칙이다. (출처: 파일럿 세션 코드)

  ```java
  // application/service/ReserveBookService.java
  if (queue.stream().anyMatch(r -> r.getMemberId().equals(command.memberId()))) {
      throw new AlreadyReservedException(book.getId(), command.memberId());
  }
  Reservation reservation = Reservation.place(book.getId(), command.memberId());
  ```

- **3 수준** — 참조 구현·파일럿 세션 코드에 해당 사례가 없다.

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

- **0 수준** — 외부 신용조회 SDK(`com.examplecredit.sdk.CreditCheckResponse`, Jackson 어노테이션 포함)는 어댑터 안에만 있다. 포트는 도메인 타입만 주고받는다. (출처: 참조 구현)

  ```java
  // application/port/out/CreditCheckPort.java
  public interface CreditCheckPort {
      CreditCheck check(Member member);
  }
  // adapter/out/credit/ExampleCreditAdapter.java
  CreditCheckResponse response = client.check(member.getId().toString(), member.getName());
  return new CreditCheck(response.approved, response.score, response.checkedAt);
  ```

  페이징도 포트는 도메인 전용 record 로 주고받고, Spring Data `Page` 는 영속성 어댑터 안에만 있다. (출처: 참조 구현)

  ```java
  // application/port/out/LoadLoanPort.java
  LoanSlice loadActiveLoans(UUID memberId, int page, int size);
  ```

- **1~3 수준** — 참조 구현·파일럿 세션 코드에 해당 사례가 없다.
  - 파일럿 6개 세션의 `application/port` 에서 `org.springframework`·`jakarta`·`JpaEntity`·`Page`·`com.examplecredit` 를 grep 하면 0건이다(2026-10-17).

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

- **0 수준** — 반납이 Loan 과 Book 을 함께 바꾸지만 Loan 은 Book 을 ID(`getBookId()`)로만 참조하고, 서비스가 두 저장을 순서대로 부른다(위 참고의 "명시적 조율"). (출처: 참조 구현)

  ```java
  // application/service/ReturnBookService.java
  loan.returnOn(command.returnedAt() != null ? command.returnedAt() : LocalDate.now());
  Book book = loadBookPort.loadBook(loan.getBookId())
          .orElseThrow(() -> new BookNotFoundException(loan.getBookId()));
  book.receiveReturn();
  saveLoanPort.saveLoan(loan);
  saveBookPort.saveBook(book);
  ```

- **판단이 갈리는 사례** — 대출 한 번이 Book(대출 중으로), Loan(생성), Reservation(맨 앞 예약 소진) 세 애그리거트를 바꾼다. (출처: 참조 구현)
  - 참고의 "명시적 조율"이 셋 이상에도 적용되는지, Reservation 소진을 "유스케이스가 경계를 넘는다"로 볼지는 루브릭 문구로 정해지지 않는다.
  - 파일럿 세션 코드도 같은 구조다.

  ```java
  // application/service/LoanBookService.java
  Loan loan = book.loanTo(member.getId(), ...);
  ReservationQueue queue = ReservationQueue.of(book.getId(), loadReservationPort.loadWaitingReservations(book.getId()));
  queue.admit(member.getId()).ifPresent(saveReservationPort::saveReservation);
  ...
  saveBookPort.saveBook(book);
  saveLoanPort.saveLoan(loan);
  ```

- **2·3 수준** — 참조 구현·파일럿 세션 코드에서 확인한 사례가 없다.

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

- **0 수준** — 서비스 하나가 서로 짝인 유스케이스 2개(정지·해제)를 맡는다. 나머지 서비스는 유스케이스 1개다. 예: `LoanBookService`, `ReturnBookService`, `GetMonthlyStatisticsService`. (출처: 참조 구현)

  ```java
  // application/service/SuspendMemberService.java
  public class SuspendMemberService implements SuspendMemberUseCase {
      public Member suspendMember(UUID memberId) { ... }
      public Member unsuspendMember(UUID memberId) { ... }
  ```

- **1~3 수준** — 참조 구현·파일럿 세션 코드에서 확인한 사례가 없다.
  - 파일럿 6개 세션에서 공개 메서드가 3개 이상인 서비스는 일부 세션의 정지·해제 서비스(위와 같은 짝)뿐이다(2026-10-17 grep).

## 평가 절차 (요약)

1. **교정 라운드:** 파일럿 세션으로 평가자 2인이 독립 채점 → 불일치 토론 → 루브릭 문구 수정 → 재채점 → Cohen's κ ≥ 0.6 확인. 미달이면 반복.
2. **본 평가:** 층화 표본(조건당 5세션)을 2인이 독립 채점한다. 차원별 가중 κ 와 ICC(2,1) 를 보고한다. 불일치는 토론 후 합의 점수.
3. **LLM 보조 채점:** 사람 채점 결과로 교정한다.
   - Spearman ρ ≥ 0.7 이면 나머지를 LLM 으로 채우고 "LLM 보조"로 명시 보고한다.
   - 미달이면 사람 평가만으로 검정한다.
   - 도구: `experiment/runner/rate_semantic.py`. 실험 대상과 다른 모델을 쓰고, 같은 입력을 3회 채점해 분산을 기록한다.

## 근거 문헌

- Evans, *Domain-Driven Design* (2003) — 애그리거트, 빈약한 도메인 모델
- Vernon, *Implementing Domain-Driven Design* (2013) — 애그리거트 설계 규칙(한 트랜잭션에 하나의 애그리거트, ID 참조)
- Cockburn, *Hexagonal Architecture* (2005) — 포트는 애플리케이션의 언어로 말한다
- Fowler, *AnemicDomainModel* (2003) — D1 의 명칭 출처
