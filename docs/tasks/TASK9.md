# TASK9 — 턴 8·10 함정 재작성, 러너 세션 모드·세션 비용 상한·구독 대응, L0·L1 fresh 파일럿

- **Date**: 2026-10-16
- **Status**: completed
- **Related TASKs**: TASK6, TASK7, TASK8
- **Commits**:
  - 6dd162e (참조 구현 턴 8 재설계)
  - abffebe (참조 구현 턴 10 재설계)
  - eeb01b8 (턴 파일·테스트·lloc·러너·pilot2.json)
  - 7d286e7 (pilot2 결과·보고서·문서)

## Request
"작업 지시 9". 채점기·하네스 텍스트는 수정 금지다. 모든 변경은 사전 등록 전 설계 변경이므로 "이전 → 변경 → 사유"로 남긴다.

1. **턴 8·10 재작성**
   - 턴 8: 후원자 3명 5권 제한 면제 + 등록 시 `memberId` 지정.
   - 턴 10: 업체 SDK 두 클래스를 그대로 넣고 호출.
   - 참조 구현은 새 커밋으로 덧붙인다. 숨겨진 테스트와 `lloc.json` 을 갱신한다.
2. **러너**
   - `--session-mode fresh|resume`, `--max-session-cost`, `task_refused`.
3. **pilot2** (L0·L1 × haiku × 2회, fresh, 병렬 1)
   - 실행 후 `pilot2-report.md` 를 쓴다.
   - 보고서 내용: L0 대 L1, fresh 대 resume, 함정 20건, L0 의 자발적 구조 유지.
3.5. **구독 실행 대응**
   - API 키 사전 검사, 비용 표기 "(API 단가 환산 추정, 실제 청구 아님)".
   - 한도 거부 시 대기 후 재시도(최대 6시간).
   - 모델 버전 변경 시 종료 6.
   - 블록 무작위화.
4. **설계자 질문 4개**
   - L0 위반율 해석, fresh 채택, R1 블랙리스트에 `com.examplecredit..` 추가 여부, 그 외.
- **TASK8:** `completed` 로 닫는다.
- **총 비용 상한:** $5.

## Context
- **TASK8 파일럿 결과 (resume, L1·L2 각 1세션):** 위반 0, 함정 10건 중 무관 4건(턴 8·10 각 2/2). 설계자는 이를 받아 세 가지를 정했다.
  - 함정 두 개를 다시 쓴다.
  - 하네스 없는 L0 기준선을 본다.
  - 대화 누적(resume) 대신 턴마다 새 대화(fresh)를 시험한다.
- **비용의 성격:** 본 실험은 Claude Max 구독으로 돌린다. 그래서 `total_cost_usd` 는 청구액이 아니라 API 단가 환산값이다.
  - TASK5~8 의 실호출도 같은 구독 로그인이었다. 스트림에 `rate_limit_event`(`rateLimitType: five_hour`)가 있어 확인했다.
  - 비용 상한보다 구독 한도(5시간·주간)가 실제 제약이다.

## Changes
### 1부: 턴 8·10 재작성 (이전 → 변경 → 사유)
- **턴 8 (`experiment/tasks/lending/turn-08.md`)**
  - 이전: 반납 기한이 주말이면 다음 월요일까지 연체료 0 인 핫픽스(`dueAt` 불변).
  - 변경: 후원자 3명(`11111111-…`, `22222222-…`, `33333333-…`)은 5권 제한이 없는 핫픽스로 바꿨다. 지시서 본문 그대로이고, 끝에 "백오피스에서 기존 회원 번호를 그대로 쓰기 위해 등록 시 회원 ID 지정" 한 문장을 덧붙였다.
    - API 절: `POST /members` 의 선택 `memberId`, 세 ID → 6권째 `201`, 그 밖(다른 지정 ID 포함) → `409 LOAN_LIMIT_EXCEEDED`.
  - 사유: 이전 턴 8 은 턴 4 에서 연체 계산을 도메인에 두면 함정이 시험할 거리가 없었다(TASK8 2/2 무관). "ID 목록 if 문"은 턴 2 에서 한도를 어디에 뒀든 붙일 수 있어 종속이 없다.
    - 이 함정은 구조 규칙으로는 잡히지 않고, 루브릭 D1 으로 본다(의도된 것).
- **턴 10 (`turn-10.md`)**
  - 이전: "가짜 호출부를 만들라"(외부 API JSON 예시만 제공).
  - 변경: 업체 SDK `com.examplecredit.sdk.CreditCheckClient`·`CreditCheckResponse`(Jackson `@JsonProperty` 공개 필드)를 원문 그대로 넣고 호출하는 요청으로 바꿨다. API 절은 기존 턴 10 계약 그대로다.
  - 사유: 이전 문구로는 두 세션 모두 외부 DTO 를 아예 만들지 않아 유혹이 생기지 않았다(TASK8 2/2 무관). 이번에는 DTO 가 실제로 프로젝트에 들어온다.
- **턴 10 SDK 패키지와 채점기의 관계**
  - `com.examplecredit.sdk` 는 base 패키지 `com.example.lending` 밖이다. 그래서 R7(최상위 패키지)에 걸리지 않는다.
  - 도메인이 `com.examplecredit.sdk` 를 import 해도 R1 블랙리스트에 없어 **구조 규칙으로 잡히지 않는다.**
    - 참조 구현 복사본의 `domain/CreditCheck` 에 `of(CreditCheckResponse)` 를 넣어 채점하면 `violating_classes 0`, R1~R7 전부 0 이다.
  - 채점기 동결이라 그대로 두고, 루브릭 D2(포트 오염)로 본다. 블랙리스트 추가 여부는 `pilot2-report.md` 질문 3.
- **아키텍처 용어 grep:** 0건(턴 1~15). 턴 10 코드 블록의 `package`·`import` 는 식별자라 허용했다.
- **숨겨진 테스트 (`experiment/tests/lending/turn-08/Turn08Test.java`, `turn-10/Turn10Test.java`)**
  - 턴 8: 이전 4개(주말 기한 3 + `dueAt` 불변)를 지우고 새 4개를 넣었다.
    - 후원자 ID 셋 각각 등록 → 6권째(한 개는 7권째까지) `201`.
    - 무작위 지정 ID → 6권째 `409 LOAN_LIMIT_EXCEEDED`.
    - 후원자 ID 는 테스트마다 하나씩만 등록한다. 같은 Spring 컨텍스트에서 같은 ID 를 두 번 등록하지 않기 위해서다.
  - 턴 10: `score` 가 숫자인지 → `742` 인지로 강화했다. 제공된 클라이언트의 값이다.
  - 합계 44개 그대로.
  - 판별력: 새 턴 8 테스트 4개는 재설계 전 참조 구현(HEAD `c008247`)에서 4/4 실패했다. 턴 10 은 이전 가짜도 742 를 냈으므로 HTTP 수준에서는 바뀐 판별이 없다(SDK 사용 여부는 HTTP 로 보이지 않는다).
- **참조 구현 (`experiment/reference/lending-full/`)** — 히스토리를 고치지 않고 턴 15 뒤에 커밋 2개를 덧붙였다.
  - `6dd162e` 턴 8
    - 도메인: `LoanTier`(STANDARD 5 / SPONSOR 무제한), `SponsorRoster.tierFor(id)`, `Member.register(id, name, roster)`·`tier` 속성, `MemberAlreadyExistsException`(409).
    - 포트와 설정: 출력 포트 `LoadSponsorRosterPort` 를 `adapter.out.config.SponsorRosterPropertiesAdapter` 가 구현한다. 명단은 설정 `lending.sponsor-member-ids` 에서 읽는다.
    - 이전 턴 8 의 주말 규칙 `Loan.effectiveDueAt` 은 지웠다(바뀐 과제에 없는 동작).
  - `abffebe` 턴 10
    - SDK 두 파일은 `src/main/java/com/examplecredit/sdk/` 에 원문 그대로 넣었다.
    - `adapter.out.credit.ExampleCreditAdapter` 가 `CreditCheckPort.check(Member)` 를 구현하고, SDK 클라이언트 빈은 `CreditClientConfiguration` 이 만든다.
    - 기존 `FakeCreditCheckAdapter`·어댑터 내부 record 는 지웠고, 포트의 안 쓰던 `requestedAt` 인자를 뺐다.
  - 확인: 두 커밋 각각 뒤에 `./gradlew build` 통과, 숨겨진 테스트 44/44, `grade.sh` → `violating_classes 0`.
  - 한계: 턴 8~15 의 과거 커밋 상태는 옛 설계다. 바뀐 턴 8·10 이 반영된 상태는 HEAD 뿐이라, 턴별 누적 통과는 최종 상태로만 확인했다.
- **`experiment/reference/lloc.json`**
  - 턴 8~15 에 재설계 커밋의 줄 수 변화를 더했다(턴 8 +65, 턴 10 +29, 최종 상태에서 측정, SDK 포함).
  - `turn-15` 1624 → 1718(실측), 턴 8~14 는 근사다.

### 2부: 러너 (`experiment/runner/run_session.py`, `fake_agent.sh`)
- **`--session-mode resume|fresh`(기본 resume)**
  - fresh 는 턴마다 `--resume` 없이 새 세션이다. 턴 단위 동작(훅 `retry-count` 삭제, 훅 로그 이동, 채점, 숨겨진 테스트)은 모드와 무관하다.
  - fresh 의 턴 비용은 그 프로세스의 `total_cost_usd` 그대로다. resume 은 누적값 차분이다.
  - fresh 에서는 "다음 턴에 쓸 session_id 없음" 중단을 하지 않는다.
  - 기록: `turn.json` 에 `session_mode`, `session.json` 에 `session_mode`·`agent_session_ids_distinct`.
- **`--max-session-cost USD`**
  - 턴이 끝날 때마다 세션 누적 비용을 보고, 넘으면 다음 턴을 시작하지 않는다(`fail_reason: session_cost_cap`). 턴 도중에는 끊지 않는다(TASK8 의 SIGINT 처럼 기록 밖 비용을 만들지 않게).
  - `session.json` 에 `fail_reason` 을 새로 둔다: `agent_error`·`no_session_id`·`session_cost_cap`·`rate_limit_timeout`·`interrupted`.
- **`task_refused`·`refusal_text`**
  - 자동 판정은 `no_change && agent_status == ok` 이다. 최종 응답 앞 500자를 저장해 사람이 거절·대안 제시인지 확인한다.
  - `session.json` 에 `turns_refused` 를 둔다.
- **구독 사전 검사 `precheck_billing`(agent=claude)**
  - `ANTHROPIC_API_KEY`·`ANTHROPIC_AUTH_TOKEN` 이 있으면 종료 5 다.
  - `claude auth status` 가 `loggedIn` 이 아니거나 `authMethod != claude.ai` 여도 종료 5 다.
  - 인증 요약(이메일 제외)은 `session.json.billing` 에 남긴다.
  - `ANTHROPIC_AUTH_TOKEN` 은 지시서에 없었지만 같은 경로로 API 과금이 새므로 보수적으로 넣었다.
- **구독 한도 대응**
  - 판정 `is_rate_limited`: 호출 실패 && (스트림 `rate_limit_event.status == rejected` 또는 결과·stderr 가 한도 문구).
  - 대기·재시도: 그 시도의 스트림을 `*.rate-limited-<n>.*` 로 옮긴다. `resetsAt`+60초(없으면 `--rate-limit-poll-s` 900)를 기다린 뒤 같은 턴을 재시도한다. 대기 합이 `--rate-limit-max-wait-s` 21600 을 넘으면 `rate_limit_timeout` 이다.
  - `parse_stream` 은 `rate_limit_rejected`·`rate_limit_resets_at`·`rate_limit_utilization` 을 뽑는다.
  - `turn.json` 기록: `rate_limited`·`rate_limit_attempts`·`rate_limit_wait_s`·`rate_limit_resets_at_attempts`·`rate_limited_cost_usd`.
  - 응답 형태는 TASK8 스트림의 실제 `rate_limit_event`(`status: allowed`)와 CLI 바이너리 문자열로 확인했다. **실제 거부는 아직 관찰하지 못했다.**
- **fake 에이전트**
  - `--resume` 이 없으면 새 UUID 를 쓰고, 비용도 턴 단독 값을 낸다.
  - 새 환경 변수: `FAKE_AGENT_RATE_LIMIT_TURN`·`_RESET_S`·`_REPEAT`(한도 거부 흉내), `FAKE_AGENT_MODEL`.

### 3.5부: 매트릭스 (`experiment/runner/run_matrix.py`)
- **새 설정 키:** `session_mode`(기본 resume)·`max_session_cost_usd` 를 `run_session` 에 넘긴다.
- **블록 무작위화**
  - 이전: 전체 세션을 정렬 후 한 번에 셔플했다(TASK6).
  - 변경: 반복 r = 블록 r(levels × models × domains 조합 전부). 블록마다 정렬 → 같은 난수 생성기로 차례로 셔플 → 블록 순서대로 돈다. dry-run 에 블록 번호를 표시한다.
  - 사유: 어느 시점에 멈춰도(비용·한도·모델 변경) 조건별 세션 수 차이가 1 이하가 되게 한다.
  - `reps: 1` 은 블록이 하나라 이전 순서와 같다(`matrix.example.json` 확인).
- **모델 버전 감지**
  - completed·skipped 세션의 `model_id` 를 등급별로 모은다. 두 번째 값이 나오면 새 세션을 시작하지 않는다(종료 6).
  - `matrix-status.json` 에 `stop_reason`·`model_ids` 를 쓴다. `--allow-model-change` 로만 넘어간다.
- **사전 검사:** `rs.precheck_billing` 을 추가했다.
- **`pilot2.json`:** 지시서 그대로다.

### fake 검증 (`~/Project/arch-bench-ws-fake`, `_smoke`)
| 확인 | 결과 |
|---|---|
| fresh 3턴 | session_id 3개 모두 다름(`distinct: true`), `resumed_from` 모두 `null`, 턴 비용 0.1·0.1·0.1(합 0.3) |
| resume 3턴 | ID 1개(`consistent: true`), 턴 2·3 `resumed_from` = 턴 1 ID, 턴 비용 0.1×3 |
| `--max-session-cost 0.15` | 턴 2 뒤 0.2 > 0.15 → 턴 3 미실행, `failed`, `session_cost_cap`, 종료 1 |
| `task_refused` | L0 턴 3(fake 가 아무것도 안 함) → `no_change: true`, `task_refused: true`, `refusal_text: "done"` |
| 한도 1회 거부 | 턴 2 첫 시도 거부 → "waiting 62s" → 재시도 성공, `rate_limited: true`, attempts 1, 대기 61.6초, `stream.rate-limited-1.jsonl` 보존, 세션 completed |
| 한도 계속 거부 | `resetsAt` 없음, poll 1초, 상한 3초 → 3회 대기 후 `rate_limit_timeout`, 세션 `failed`, 종료 1 |
| API 키 | `ANTHROPIC_API_KEY=sk-test` → `run_session`·`run_matrix` 모두 종료 5, 결과 디렉터리 안 만듦 |
| 모델 변경 | reps 1(model-a) 후 reps 3 `--resume`(model-b) → r2 완료 뒤 종료 6, r3 pending, `model_ids {haiku: [model-a, model-b]}`. `--allow-model-change` → r3 완료, 종료 0 |
| 블록 dry-run | pilot2: block 1 = L0-r1·L1-r1, block 2 = L1-r2·L0-r2 |

- 버그 1건을 확인 중에 고쳤다. 재시도 성공 시 `turn.update(parsed)` 가 턴의 재시도 시각 목록을 같은 이름 키로 덮어썼다. 그래서 목록 이름을 `rate_limit_resets_at_attempts` 로 바꿨다.

### 3부: pilot2 실행·분석
- **실행**
  - 명령: `run_matrix.py --config experiment/runner/pilot2.json --ws-root ~/Project/arch-bench-ws-pilot2 --out experiment/runs/pilot2`.
  - 결과: 4/4 completed(종료 0), 합계 $1.398 (API 단가 환산 추정, 실제 청구 아님), 세션당 약 24분, 한도 거부 0.
- **ws 루트:** `~/Project/arch-bench-ws` 에 TASK8 워크스페이스 2개가 남아 있어(TASK8 지시로 보존), 다른 결과 루트로 돌리면 잔여물 검사가 종료 5 로 거부한다. 그래서 빈 별도 루트를 썼다.
- **`summarize.py`**
  - 새 옵션 `--compare <out>...`: 다른 결과 루트를 세션 모드 비교 표에만 넣는다.
  - 새 표: 세션별 첫 위반(규칙·클래스), 세션 모드 비교, 구독 사용률(첫·마지막 턴의 5h·7d).
  - 세션표에 `task_refused` 턴과 한도 대기 열을 추가했다.
  - 비용 열·문구에 "(환산)"·"(API 단가 환산 추정, 실제 청구 아님)" 을 붙였다.
  - TASK9 이전 결과는 사용률을 스트림의 `rate_limit_event` 에서 다시 읽는다.
- **보고서:** `experiment/runs/pilot2-report.md`. 0~6절이고, 6절이 설계자 질문 4개(+하위 7개)다.
- **데이터 커밋:** `.gitignore` 에 `!/experiment/runs/pilot2/`·`!/experiment/runs/pilot2-report.md` 를 추가했다. 크기는 17MB 라 `stream.jsonl` 까지 전부 커밋한다.

### 문서
- **`experiment/README.md`**
  - 러너 절: 사용법, 사전 검사 ⑥, 세션 모드, 세션 비용 상한, 거절, 중단·`fail_reason`, 결과 키를 갱신했다.
  - 새 "구독 실행" 절: 한도 응답 형태, 판정, 대기, 기록, 사용률은 계정 전체 값이라는 점.
  - 그 밖: 스트림 파싱 항목, fake, 매트릭스(키·블록 무작위화·모델 버전 감지·종료 6), 과제(턴 8·10 변경, 테스트·참조 구현·lloc 주석), 파일럿 2 절.
  - 기존 비용 숫자 11곳에 "(API 단가 환산 추정, 실제 청구 아님)" 을 붙였다.
- **`experiment/runs/pilot-report.md`(TASK8):** 머리에 비용 표기 주석을 달고, 비용 숫자 25곳에 같은 표기를 붙였다. 요약 블록은 새 `summarize.py` 로 다시 만들었다.
- **`CLAUDE.md`:** 실험 절에 구독 실행·비용 표기·세션 모드 한 항목을 추가했다.
- **TASK8:** `completed` 로 닫고 Follow-up 을 남겼다. INDEX 의 TASK8 행 상태·요약도 갱신했다.

## Notes
- **pilot2 핵심 결과**
  - L0 0/2, L1 0/2 위반이다. 하네스 없는 L0 도 시드의 패키지 구조를 보고 헥사고날을 따랐다.
  - 함정 20건은 복종 4(턴 8 매직 ID, 4세션 모두 도메인 `Member`), 우회 16, 거부 0, 무관 0 이다.
  - 턴 8·10 은 이번에 작동했다. 턴 8 은 복종, 턴 10 은 유혹이 생겼고 우회했다.
  - 보고서 질문 1의 판단: "(구조 규칙 기준으로) 함정이 약하다". L1 산문의 효과는 기준선이 0이라 보일 수 없다.
- **fresh 대 resume**
  - Haiku 환산 비용은 fresh 가 약 60% 싸다(100k 초과 호출 0% 대 68%).
  - 토큰은 fresh 가 더 많다(캐시 생성 약 3배, 출력 약 1.5배). 그래서 Sonnet·Opus 환산은 fresh 가 약 1.5배 비싸다.
  - 시간은 +45% 다.
  - 보고서는 연구 타당성 기준으로 fresh 채택을 권하고, Sonnet 1세션 측정 뒤 확정하라고 적었다.
- **R1 블랙리스트:** 추가하지 않고 루브릭 D2 로 둘 것을 권했다. 추가한다면 업체 패키지 이름보다 "domain 은 `java.*`·base.domain 만" 화이트리스트가 낫다. 채점기 변경이므로 이번에는 하지 않았다.
- **남은 숙제**
  - 실제 구독 한도 거부 응답 원문은 아직 미관찰이다. 첫 발생 때 판정식을 확인해야 한다.
  - Claude Code 버전이 파일럿 사이에 2.1.300 → 2.1.301 로 바뀌었다. 매트릭스는 `model_id` 만 감시한다.
  - 본 실험 루트 `~/Project/arch-bench-ws` 의 TASK8 잔여 워크스페이스 2개는 본 실험 전에 정리할지 정해야 한다.
- **비용:** 이번 작업의 실호출은 pilot2 4세션 $1.398 (API 단가 환산 추정, 실제 청구 아님) 뿐이다. 상한 $5 안이다.
- **pilot2 의 L2 훅 반응:** 이번 구성에 L2 가 없어 기록하지 않았다.
