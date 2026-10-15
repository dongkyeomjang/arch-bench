# TASK7 — 도서 대출 15턴 과제·숨겨진 기능 테스트·참조 구현·러너 FT 연동

- **Date**: 2026-10-15
- **Status**: completed
- **Related TASKs**: TASK4, TASK5, TASK6
- **Commits**: e7451c3 d41e792 4e03805 e4db1d5 05218c9 c80f402 5ba26e8 a5aef69 584297a 7ac48ba 34132c2 b4f34a1 dfffd56 3e3c0b6 c008247 (참조 구현 턴별), (이 TASK 마무리 커밋)

## Request
"작업 지시 7". 시드에 순서대로 줄 15개 요청을 실험 과제로 만든다. 10개는 평범한 기능 요청이고, 5개(턴 5·8·10·12·14)는 아키텍처를 깨는 쪽이 쉬워 보이게 쓴 함정이다.

1. **턴 파일:** `experiment/tasks/lending/turn-01..15.md` 를 만든다.
   - 본문은 원문 그대로 두고 `## API` 절에 HTTP 계약을 적는다.
   - 함정 표시와 아키텍처 용어는 쓰지 않는다(grep 0건).
2. **숨겨진 기능 테스트:** `experiment/tests/lending/turn-NN/` 에 둔다.
   - 턴별 `TurnNNTest`, HTTP 만 쓰고, 러너가 누적 실행한다.
   - 참조 구현 `experiment/reference/lending-full/` 로 검증한다.
     - 15턴을 헥사고날대로 구현하고, 턴별 커밋 15개를 둔다.
     - 함정 턴 커밋 본문에 설계와 이유를 적는다.
     - 결과: `violating_classes 0`, 테스트 전부 통과, 시드에서는 턴 1 테스트 실패.
     - `lloc.json` 을 남긴다.
3. **러너 연동 (`run_session.py`):** 턴 N 뒤에 턴 1~N 숨겨진 테스트를 돌려 `ft_*` 를 기록한다.
   - 테스트 디렉터리는 `experiment/tests/<domain>/` 로 일반화한다.
   - fake 로 경로를 확인한다.
4. **파일럿 준비:** `pilot.json` 과 dry-run 출력을 README 에 남긴다. 실행은 하지 않는다.

## Context
- **FT_pass 의 필요성:** 실험의 기능 지표 FT_pass 는 에이전트가 보지 못하는 테스트로 재야 한다. 에이전트가 테스트를 볼 수 있으면 테스트에 맞춰 짜거나 테스트를 고칠 수 있다.
- **HTTP 만 쓰는 이유:** 테스트는 에이전트가 어떤 구조로 짜든 컴파일되어야 한다. 내부 클래스·패키지는 에이전트마다 다르므로 HTTP 계약만 본다.
- **참조 구현의 역할:** 두 가지를 겸한다.
  - 테스트가 맞는지 확인하는 정답이다.
  - 함정을 "올바르게" 푼 예시다. 루브릭 교정 라운드의 기준이 된다.
- **커밋 방식:** 참조 구현은 별도 git 저장소로 두면 arch-bench 에 커밋할 수 없다(중첩 저장소). 그래서 arch-bench 의 턴별 커밋 15개로 만들었다.

## Changes
- **턴 파일 (`experiment/tasks/lending/turn-01.md` ~ `turn-15.md`)**
  - 본문은 지시서 원문 그대로이고, 그 뒤에 `## API` 절을 붙였다.
  - 공통 약속(JSON, UUID, `YYYY-MM-DD`, 409+error, 404, 기존 API 유지)은 턴 1 에만 둔다.
  - API 절에는 테스트가 확인하는 세부 계약을 빠짐없이 적었다.
    - 턴 3: 반납한 대출은 5권 제한에 세지 않는다.
    - 턴 9: 소진된 예약은 목록에 나오지 않는다.
    - 턴 14: 작업별 `targetId` 를 정했다.
    - 턴 15: 활동 없는 달은 0 이다.
  - 아키텍처 용어 grep 0건이다.
- **숨겨진 테스트 (`experiment/tests/lending/`)**
  - 턴별 `TurnNNTest`(턴 12 는 `.gitkeep`)와 공용 도우미 `turn-01/Api.java` 로 이루어지고, 합계 44개다.
  - 패키지는 `com.example.lending.hidden` 이고 `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` 을 쓴다.
  - 애플리케이션 빈 주입은 없다.
  - 격리는 테스트마다 고유 데이터를 만드는 방식이다(`@DirtiesContext` 대신, 속도).
- **참조 구현 (`experiment/reference/lending-full/`)**
  - 시드 작업 트리를 복사해 출발했고, 턴별로 구현해 커밋했다.
  - 공통 장치: 도메인 `RuleViolationException(code)` 와 `WebExceptionHandler` 로 409 + `error` 를 낸다.
  - 함정 풀이(커밋 본문에 상세):
    - **턴 5:** Spring `Page`/`Pageable` 은 영속성 어댑터 안에만 둔다. 포트는 `LoanSlice`, 유스케이스는 `CurrentLoansPage` record 를 쓴다.
    - **턴 8:** `Loan.effectiveDueAt()` 을 두고, 연체 계산이 도메인 안에서 바뀐다. `dueAt` 은 그대로다.
    - **턴 10:** 출력 포트 `CreditCheckPort` 가 도메인 값 `CreditCheck(approved, score, checkedAt)` 를 돌려준다.
      - 외부 응답 DTO 는 `adapter.out.credit` 의 package-private record 에만 있다.
      - 거절 규칙은 `Loan.recordCreditCheck` 가 가진다.
    - **턴 12:** 도메인과 JPA 엔티티를 합치지 않는다.
      - 매퍼 4개를 없애고 변환을 엔티티의 `from`/`toDomain` 으로 옮겼다.
      - 영속성 패키지가 497줄에서 385줄로 줄었다.
    - **턴 14:** 출력 포트 `RecordAuditPort` 를 두고, 상태를 바꾸는 유스케이스 4개가 한 줄씩 기록한다. JPA 리스너·AOP 는 쓰지 않는다.
  - 그 밖의 턴:
    - **턴 9:** 대기열 규칙은 도메인 `ReservationQueue` 에 둔다.
    - **턴 15:** 연체료가 도메인 계산이라 통계도 도메인 `MonthlyLoanStatistics.compute` 로 센다.
  - 시드의 통합 테스트는 턴 2 에서 회원 등록 후 대출하도록 고쳤다.
- **LLOC (`experiment/scripts/lloc.py`, `experiment/reference/lloc.json`)**
  - 셈 규칙: 빈 줄, 주석 줄, `}` 단독 줄을 뺀 줄 수.
  - 범위는 `src/main/java` 이고, 턴별 누적값이다(`turn-00` 시드 373 → `turn-15` 1624).
- **러너 (`experiment/runner/run_session.py`)**
  - 상수 `HIDDEN_TESTS = experiment/tests` 를 두고 함수 `hidden_test_files` 와 `hidden_tests` 를 추가했다.
  - 턴 단계 ⑦ 에서 숨겨진 테스트를 돌린다. 원래의 에이전트 자체 테스트는 ⑧ 로 유지했다.
  - `turn.json` 에 `ft_status`, `ft_total`, `ft_passed`, `ft_failed_names`, `ft_compile_error`, `ft_pass`, `ft_exit`, `ft_wall_s` 를 쓴다.
  - `session.json` 에 `ft_pass_by_turn`, `final_ft_*`, `hidden_tests_sha256` 를 쓴다. 턴 요약에도 `ft_*` 를 넣었다.
- **파일럿 (`experiment/runner/pilot.json`)**
  - 설정: L1·L2 × haiku × lending × 5회, 병렬 2, `max_cost_usd` 8.
  - `est_cost_per_session_usd` 는 haiku 0.5 로 잡았다(어림값).
  - dry-run 결과(10세션)는 README 에 있다.
- **문서·기타**
  - `experiment/README.md`: 디렉터리 트리, 러너 표(⑦ 단계·숨겨진 테스트 행·결과 파일)를 갱신했고 "도서 대출 과제" 절을 추가했다.
    - 그 절에는 턴 파일, 테스트 규약, 참조 구현, LLOC, 검증 표, 파일럿이 있다.
  - `CLAUDE.md`: 실험 절에 과제·테스트 규칙 두 줄을 추가했다.
  - `.gitignore`: `/experiment/scripts/__pycache__/` 를 추가했다.

## Notes
- **검증 결과**
  - **턴별 누적 통과:** 턴 N 커밋 상태에 테스트 1~N 을 돌리면 15개 상태 모두 전부 통과한다(최종 44/44).
  - **판별력:** 턴 N 테스트를 턴 N−1 상태에 돌리면 전부 실패한다. 예외는 턴 8 의 `dueAt` 불변 테스트 하나다(회귀 방지용이라 의도대로).
    - 처음에는 "없는 ID → 404" 테스트가 엔드포인트가 아예 없어도 Spring 기본 404 로 통과했다(턴 1·3·5·7·9·10·11·13).
    - 아무것도 안 한 에이전트가 점수를 얻지 않도록, 404 를 보기 전에 같은 엔드포인트를 정상 ID 로 먼저 성공시키게 고쳤다.
  - **채점:** 턴 5·10·12·14·15 직후 `grade.sh` 결과는 모두 `violating_classes 0` 이었다.
- **지시와 다르게 한 점**
  - **에이전트 자체 테스트:** 지시서의 3부 문구가 기존 단계를 대체하라는 뜻인지 모호했다. 러너 변경 범위를 넓히지 않는 쪽을 택해 기존 단계를 지우지 않고 ⑧ 로 남겼다.
    - 그 대가로 턴당 Gradle 실행이 하나 늘어 세션 시간이 길어진다.
    - `run_matrix.py` 는 `--skip-tests` 를 넘기지 않는다.
  - **컴파일 실패 표시:** `ft_compile_error` 는 테스트 컴파일 실패뿐 아니라 본문 컴파일 실패에서도 `true` 다(둘 다 XML 이 없다). 테스트는 HTTP 만 쓰므로 실제로는 거의 본문 쪽이다.
  - **`ft_total` 기준:** XML 이 아니라 테스트 소스의 `@Test` 수(정적)로 센다. 그래야 컨텍스트 실패·컴파일 실패여도 분모가 같다.
  - **턴 14 채점 범위:** 계약은 "대출·반납 대상 = 대출 ID" 지만, 테스트는 대출 ID 와 책 ID 조회 결과를 합쳐서 본다(지시서의 "loanId 와 bookId 로 조회"). 대상 선택은 엄격히 채점하지 않는다.
  - **테스트 수:** 턴당 2~4개다(지시서는 2~5개).
- **파일럿 주의**
  - 세션당 비용 $0.5 는 검증되지 않은 어림값이다. 파일럿 첫 세션 비용으로 `est_cost_per_session_usd` 를 고칠 것.
  - 파일럿 실행 시 `--ws-root` 는 비어 있는 `~/Project/arch-bench-ws` 를 쓴다. fake 확인은 `~/Project/arch-bench-ws-fake` 에서 했다.
- **함정 턴 정보의 위치:** 함정 턴 번호와 의도는 이 파일, 참조 구현 커밋 메시지, README 에만 있다. 모두 에이전트 워크스페이스로 복사되지 않는다.
