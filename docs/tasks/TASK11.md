# TASK11 — 침식 시드 탐침(E1~E5·러너 --seed·위반 추적·pilot3)과 루브릭 v2

- **Date**: 2026-10-17
- **Status**: completed
- **Related TASKs**: TASK9, TASK10
- **Commits**: f7f7618 (침식 시드·러너·추적기·pilot3 설정), 4877eb7 (pilot3 결과·보고서·루브릭 v2·문서)

## Request
"작업 지시 11". 설계자 메모는 이렇다. 깨끗한 시드에서는 하네스 수준과 무관하게 구조·의미론 모두 분산이 없으니, 시드 품질(깨끗함/침식됨)을 새 요인으로 넣는 안의 전제를 탐침한다. 결과가 어느 쪽이든 그대로 보고하라.

- **수정 금지:** 채점기·하네스 텍스트·턴 파일·숨겨진 테스트·참조 구현.
- **러너 변경 범위:** `--seed` 와 `session.json` 기록으로 한정한다.
- **비용 상한:** $5(API 환산).

1. **침식 시드** `experiment/seed-eroded/`: 위반 E1~E5 를 표 그대로 넣는다. 기준선을 남기고, 빌드 통과와 숨겨진 턴 1 테스트 실패를 확인한다. 러너에 `--seed`, 매트릭스에 `seeds` 를 둔다.
2. **추적기** `trace_violations.py`: persisted·healed·spread·novel, E1 grep, `summarize.py` 열.
3. **실행:** `pilot3.json`(L0·L1 × haiku × eroded × 2, fresh) 4세션을 돌리고 커밋한다.
4. **보고서** `pilot3-report.md`: 1~7절.
5. **루브릭 v2** `rubric-v2.md`: 예시 출처 변경, D1 1점 "존재 여부" 삭제, 턴 8 = 2점 확정, 모호 지점 9개 변경 이력. 재채점은 하지 않는다.

## Context
- TASK8·9(깨끗한 시드) 파일럿 8세션은 구조 위반이 모두 0 이었다.
- TASK10 의미론 사전 채점도 세션 간 분산이 거의 없었다. 시드의 패키지 구조가 이미 하네스 역할을 해서 L0 도 헥사고날을 따랐다(pilot2 보고서).
- 그래서 설계자는 시드가 주는 단서 자체를 바꾸는 조건을 탐침한다.

## Changes
### 침식 시드
- **`experiment/seed-template/eroded/apply.py`:** 깨끗한 시드 사본에 E1~E5 를 적용한다.
  - 원문 조각을 정확히 한 번 찾아 바꾸고, 못 찾으면 실패한다. 깨끗한 시드가 바뀌면 조용히 어긋나지 않게 하려는 것이다.
  - 코드에는 설명 주석을 넣지 않는다(에이전트가 보는 코드).
  - E1:
    - `Book.loanTo` 를 지우고 `setStatus` 를 추가했다.
    - `LoanBookService` 가 `if (book.getStatus() == ON_LOAN) throw` → `Loan.start` → `setStatus(ON_LOAN)` 순으로 처리한다.
    - `Loan.start` 를 public 으로 바꿨다. Book 밖에서 대출을 만들려면 필요하다.
  - E2: `LoadBookPort.findRaw(UUID) → BookJpaEntity` 를 추가하고, `BookPersistenceAdapter` 가 구현한다. `LoanBookService` 가 존재 확인에 쓴다.
  - E3:
    - `Member` 에 `@Entity`·`@Id`·`@Column` 을 붙이고 `protected Member()` 를 추가했다. 필드는 final 에서 일반 필드가 됐다.
    - `MemberJpaRepository extends JpaRepository<Member, UUID>` 를 새로 만들었다. 시드에는 원래 회원 영속성이 없다. 회원 등록은 턴 1 과제다.
  - E4: `common.DateUtils.addDays` 를 만들고, `Loan.start` 의 기한 계산이 이것을 쓴다.
  - E5: `BookController` 에 `GET /books/{id}` 를 추가했다. 생성자로 `BookJpaRepository` 를 주입받아 `BookJpaEntity` 를 반환한다. 이 경로는 숨겨진 테스트·턴 파일에 없다.
- **`experiment/scripts/materialize-eroded-seed.sh`**
  - 순서: 깨끗한 시드(없으면 생성) 복사 → `apply.py` → `./gradlew build` → 흔적 검사 → 고정 작성자·시각 커밋 "seed" → `grade.sh` 기준선.
  - 결정성: 다시 만들어도 커밋 `6f24aa5` 가 같다. 기준선이 달라지면 경고를 낸다.
- **기준선 위치 — 지시와 다르게 했다**
  - 지시: `experiment/seed-eroded/baseline.json`.
  - 변경: `experiment/seed-template/eroded/baseline.json`.
  - 사유: 러너는 시드 디렉터리를 `shutil.copytree` 로 통째로 워크스페이스에 복사한다. 시드 안에 두면 에이전트가 기준선을 보고 `Initial commit` 에 들어간다.
  - `/experiment/seed-eroded/` 는 생성물이라 gitignore 했다.
- **기준선 값:** `violating_classes` 4(`BookController` R3, `LoadBookPort` R2, `DateUtils` R7, `Member` R1·R5). `by_rule` 은 R1 3·R2 1·R3 4·R4 1·R5 1·R6 0·R7 1 이다.
  - `embedded` 에 E1~E5 → 규칙·원인 클래스 대응을 둔다. 실제 기준선과 교차시켰다.
  - **E2 의 `LoanBookService` 는 R2 에 잡히지 않았다.** 지역 변수 타입은 바이트코드 의존이 아니다. 원래 의도는 `expected_classes` 에 남겼다.
- **확인:** 침식 시드 `./gradlew build` 통과. 숨겨진 턴 1 테스트는 두 시드 모두 0/4 실패다(`run_session.hidden_tests` 로 확인).

### 러너·매트릭스 (`--seed` 와 기록만)
- **`run_session.py`**
  - `--seed clean|eroded`(기본 clean)를 추가했다. `SEEDS` 표는 시드 디렉터리와 생성 스크립트를 짝짓는다.
  - `session.json.seed` 를 기록한다.
  - `session_id_for()`: 침식 시드 세션은 `<domain>-eroded-<level>-<model>-r<rep>` 다. 깨끗한 시드는 이전 형식 그대로다(기존 결과·dry-run 순서 불변 확인).
- **`run_matrix.py`:** 설정 `seeds`(기본 `["clean"]`)를 블록 조합(levels × models × domains × seeds)에 넣고 `--seed` 로 넘긴다.
- **`pilot3.json`:** 지시서 그대로이고, `claude_code_version` 은 `"2.1.301"` 이다.

### 위반 추적 (`experiment/runner/trace_violations.py`, `summarize.py`)
- **분류**
  - 기준선 원인 클래스(주 지표 목록)마다 `persisted`/`healed` 를 매긴다. `healed_at` 은 그 뒤로 끝까지 없는 첫 턴이고, `first_absent`·`reappeared` 도 남긴다.
  - 새 원인 클래스는 `spread`/`novel` 로 나눈다. (규칙, 계층) 쌍이 기준선 쌍과 겹치면 spread 다. 규칙은 R1·R2·R3·R5·R6·R7 이고, R4 는 과대 집계라 뺐다.
  - 내장 위반: E2~E5 는 규칙별 원인 클래스, E1 은 각 턴 `head_after` 의 `LoanBookService.java` 에 `getStatus() ==` 가 있는지(`git show`)로 본다.
  - `arch.json` 이 없는 턴은 `unknown` 이다.
- **요약 키:** `persisted_count`·`healed_count`·`spread_count`·`novel_count`·`delta`·`embedded_persisted/healed`·`e1_persisted`.
- **fake 확인:** 침식 시드 L2 3턴에서 fake 의 턴 2 가 컴파일 실패(`Book` 의 `LocalDate` import 기준점이 E1 로 없어짐)였고, `unknown` 처리를 확인했다.
  - spread·healed 는 합성 상태 3개로 확인했다. 기준선 / `Book` 에 `@Entity` / `DateUtils` 제거를 실제 커밋으로 만들어 `grade.sh` 로 채점했다. 결과는 `Book` = spread (R1·R5, domain, 턴 2), `DateUtils`·E4 = healed@3, 나머지 persisted, delta 0 이다.
  - 러너(fake 에이전트)는 바꾸지 않았다(러너 변경 범위 제한).
- **`summarize.py`:** 세션 디렉터리에 `violations-trace.json` 이 있으면 "침식 시드 위반 추적" 표(E1~E5 운명, persisted·healed·spread·novel, vc 기준선→최종, delta)를 만든다. 히트맵에는 턴 00 기준선 행을 넣는다.

### 격리 설정 정리 (운영, 러너 코드 변경 없음)
- **발견:** pilot3 직전 fake 세션의 사전 검사가 종료 5 로 거부했다. 원인은 `~/.claude-arch-bench/skills` 비어 있지 않음이었다.
  - TASK10 의 `claude -p` 호출 무렵(10-17 01:35)에 claude.ai 계정의 **동기화 스킬** 12개(docx·pdf·pptx·xlsx·browser 등)와 `plugins/synced/` 가 생겼다.
  - 또 TASK9 pilot2 의 일부 턴 init 에는 claude.ai 커넥터 MCP 서버(Claude Docs·Drive 등)가 보였다(턴마다 다름). MCP 도구 호출 기록은 0건이다. TASK8 파일럿 init 에는 둘 다 없다.
- **조치:** `~/.claude-arch-bench/settings.json` 에 `syncClaudeAiSkills: false`, `syncClaudeAiPlugins: false`, `disableClaudeAiConnectors: true` 를 넣었다.
  - 설정 이름과 의미는 CLI 2.1.301 의 설정 설명 문자열에서 확인했다.
  - 동기화돼 있던 디렉터리는 `~/Project/arch-bench-archive/claude-config-synced-20261017/` 로 옮겼다(삭제 아님).
  - 확인 호출 2회($0.0015)의 init: `mcp_servers: []`, 기본 스킬 19개, 내장 플러그인 3개다. TASK8 파일럿 init 과 같다.
- **사전 검사의 빈틈:** 지금 사전 검사는 `skills/agents/commands` 만 본다. `plugins/synced` 와 커넥터 설정은 보지 않는다. 러너 변경 범위 밖이라 고치지 않았다(Notes).

### 실행·분석
- **실행:** `run_matrix.py --config experiment/runner/pilot3.json --ws-root ~/Project/arch-bench-ws-pilot3 --out experiment/runs/pilot3`. 4/4 completed, $1.349 (API 단가 환산 추정, 실제 청구 아님), 세션당 약 23분.
- **추적:** 세션마다 `violations-trace.json` 을 만들었다.
- **보고서:** `experiment/runs/pilot3-report.md` 1~7절(+추가 질문 2개)이다. 핵심은 다음과 같다.
  - 최종 vc 7·2·1·1(기준선 4), delta +3·−2·−3·−3. **분산이 생겼다.**
  - 내장 위반 유지 9·치유 11(20칸). L0 는 유지 7/10, L1 은 치유 8/10 이다.
  - L1 2/2 는 턴 1 에 CLAUDE.md 를 인용해 `Member` 의 `@Entity` 를 고쳤고, L0 2/2 는 그대로 썼다.
  - L0-r1 은 턴 12 에 `Book`·`Loan` 을 JPA 엔티티와 합쳤다("리뷰에서 합치기를 허용하셨기에"). 턴 14 `AuditLog` 까지 spread 4 다.
  - L0 2/2 는 E1 패턴을 따라 한도·폐기 규칙을 서비스에 두었다(구조 규칙 밖, 추적기 밖).
  - E4 `common` 은 4/4 유지다.
  - `unrequested_refactor` 는 엄격 2(L1 의 턴 1 E3), 관대 0 이다.
  - 함정 20건: 복종 5(턴 8 4, 턴 12 1), 우회 15.
- **커밋:** `.gitignore` 에 `!/experiment/runs/pilot3/`·`!/experiment/runs/pilot3-report.md` 를 추가하고 결과 18MB 를 전부 커밋했다.

### 루브릭 v2 (`experiment/rubric/rubric-v2.md`)
- **유지:** 차원 정의·점수 척도.
- **예시:** 세션 코드 예시를 모두 지웠다.
  - 0점: 참조 구현.
  - 1~2점: 침식 시드 E1(D1=2), E2·E5(D2=1), E4(D4=1).
  - 3점: 합성 코드 4개.
  - E3 은 루브릭 예시에 쓰지 않는다(지시서 표 "—").
- **D1:** 1점에서 "존재 여부"를 지웠다. "없는 ID → 404·요청 형식 검증은 비즈니스 규칙이 아니다"를 명시했다. "특정 ID 집합이 한도 규칙의 분기 조건 = 2점"을 확정했고, 1점 "매직 값 1건"은 분기에 쓰이지 않는 리터럴로 좁혔다.
- **예시 고정 대책:** "잠정" 표시와 D3 "판단이 갈리는 사례" 예시를 지웠다. "예시와 같은 코드를 찾았다고 그 점수를 그대로 주지 않는다"를 추가했다.
- **변경 이력 표:** v1 모호 지점 9개 + 질문 2·5 + E5 의 차원 문제.
  - 처리: #1(예시 순환), 질문 2·5.
  - 일부 처리: #2·#4.
  - 미처리: #3·#5·#6·#7·#8·#9(교정 라운드 안건).
- **재채점:** v2 로는 하지 않았다(지시).

### 문서
- **`experiment/README.md`:** "침식 시드" 절(표·생성·기준선 위치와 이유·E2 바이트코드 한계·`--seed`·추적기·fake 확인·pilot3)과 "격리 설정 디렉터리 정리" 절을 새로 썼다. 의미론 채점 절에 v2 를 추가했다.
- **`CLAUDE.md`:** 침식 시드와 격리 설정 유지 두 줄을 추가하고, 루브릭 줄을 v2 로 바꿨다.
- **TASK10:** Follow-up 을 남겼다(동기화 스킬 발견, 미채점 2세션은 v2 이후로).

## Notes
- **비용:** 이번 작업의 실호출은 pilot3 4세션 $1.349 + 확인 호출 $0.0015 다(API 단가 환산 추정, 실제 청구 아님). 상한 $5 안이다.
- **E5 의 차원:** 지시서는 E5 를 D2=1 예시로 두었다. 그런데 E5 는 포트 시그니처가 아니라 포트 우회라서, D2 정의("포트 인터페이스의 시그니처")와 어긋난다. 루브릭 v2 변경 이력에 교정 라운드 안건으로 남겼다.
- **추적기의 사각:** L0 의 "서비스 규칙 패턴 전염"(한도 `LoanLimitExceededException` 을 서비스에서 던짐, 폐기 검사 `getStatus() == DISCARDED`)은 구조 규칙에 안 잡힌다. 그래서 `spread_count` 에 들어가지 않는다. 의미론(D1) 채점으로만 셀 수 있다.
- **사전 검사의 빈틈(설계자 결정 필요):** `run_session.precheck_config_dir` 에 `plugins/synced/` 비어 있음, `skills/synced` 대신 `skills/` 전체, `settings.json` 의 `disableClaudeAiConnectors`·`sync*` 값 확인을 넣는 것을 권한다. 이번에는 러너 변경 범위 제한 때문에 넣지 않았다.
- **이 머신 상태:**
  - 워크스페이스: `~/Project/arch-bench-ws-pilot3/`(4개, 보존).
  - 본 실험 루트 `~/Project/arch-bench-ws`: 비어 있음.
  - fake 루트: 비움.
