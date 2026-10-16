# TASK10 — 의미론 측정 준비: 루브릭 v1·익명화·LLM 보조 채점기·파일럿 사전 채점, 운영 정리(버전 고정)

- **Date**: 2026-10-17
- **Status**: completed (사전 채점은 예산 상한으로 일부만 — Notes)
- **Related TASKs**: TASK8, TASK9
- **Commits**: dfb4ec4

## Request
"작업 지시 10". 설계자가 주 종속변수를 구조 위반에서 의미론 루브릭 점수로 바꾸는 안을 검토 중이다(설계자 발언: "아직 전환을 확정하지 않았다. 전제(의미론 점수에 분산이 있는가)를 확인하는 것이며, 결과가 어느 쪽이든 그대로 보고하라"). ArchUnit 채점기·하네스·턴 파일·참조 구현은 수정 금지이고, 채점 비용 상한은 $5 (API 환산)다.
1. **운영:** TASK8 워크스페이스를 보관소로 옮기고 ws 루트를 비운다. Claude Code 버전을 고정하고 버전 사전 검사(종료 5)를 넣는다. `http.postBuffer` 를 README 에 적는다.
2. **루브릭:** `experiment/rubric/rubric-v1.md`(D1 증상 추가, 점수별 코드 예시, 평가 단위, 익명화)와 `anonymize.sh`.
3. **채점기:** `rate_semantic.py`(Opus, 3회, `score_variance`, 근거 인용).
4. **사전 채점:** 파일럿 6세션 + 참조 구현을 3회씩 채점해 `semantic-pretest.md` 를 쓴다.
5. **설계자 질문 5개.**

## Context
- TASK8·9 파일럿 8세션의 구조 위반은 모두 0 이었다. 바닥이라 구조 지표로는 조건 간 차이를 볼 수 없다.
- 의미론 루브릭 원문(D1~D4 정의·0~3 기준)은 저장소에 없었다. 작업 중에 설계자에게 물어 설계 문서 원문을 받았다(2026-10-13 작성, 2026-10-16 D1 증상 추가). 그 전에 저장소에 있던 것은 L3 리뷰어 하네스의 네 관점 이름뿐이다.

## Changes
### 1부: 운영 정리
- **워크스페이스 보관소** `~/Project/arch-bench-archive/`
  - 옮기기 전 확인: 각 워크스페이스 HEAD 와 마지막 턴 `turn.json` 의 `head_after` 를 대조했다. 6개 중 5개가 일치했다.
  - **불일치 1건:** TASK8 L2-r1(`ea8aa393f496`)이다. 턴 15 도중 SIGINT 로 멈추기 전에 에이전트가 커밋 `837384f`("월별 통계 API 추가")를 남겼고, 러너 기록의 마지막은 턴 14 `4dc5bd9` 다. 옮긴 사본에는 그대로 두었다.
  - 이동(삭제 아님): `pilot1/`(TASK8 2개)와 `pilot2/`(TASK9 4개)로 옮겼다. 지시서는 `-pilot2` 를 "비운다"고만 했지만, 사전 채점의 코드 스냅샷이므로 지우지 않고 함께 옮겼다.
  - `INDEX.md` 에 토큰 ↔ 세션 ↔ HEAD ↔ 원래 경로를 적었다.
  - `~/Project/arch-bench-ws`·`~/Project/arch-bench-ws-pilot2` 는 비었다.
  - `session.json` 의 `workspace` 경로는 결과 데이터라 고치지 않았다.
- **Claude Code 2.1.301 고정**
  - `~/.local/bin/claude` 를 설치기 심볼릭 링크에서 사용자 런처 스크립트(`exec ~/.local/share/claude/versions/2.1.301 "$@"`)로 바꿨다.
    - 근거: 공식 문서 setup "Auto-updates" 는 "launcher 를 직접 두면 auto-update 가 그대로 두고 새 버전은 versions/ 에만 설치"라고 한다.
    - 설치기가 관리하는 심볼릭 링크는 자동 업데이트가 옮긴다(TASK9 의 2.1.300 → 2.1.301).
    - 원래 링크는 대화 scratchpad 에 백업했다.
  - `~/.claude-arch-bench/settings.json` 의 `env.DISABLE_AUTOUPDATER = "1"`. 확인: `claude doctor` → `Auto-updates: disabled (set by env: DISABLE_AUTOUPDATER)`, "launcher … auto-update leaves it untouched".
  - **부수 효과:** 같은 머신의 대화형 `claude` 도 2.1.301 로 고정된다(런처 하나). 되돌리는 방법은 README "운영".
- **버전 사전 검사**
  - `run_session.precheck_cli_version(expected)`: `claude --version` 첫 토큰이 다르면 `PrecheckError`. 새 인자 `--claude-code-version`, `session.json.claude_code_version_expected`.
  - `run_matrix`: 새 설정 키 `claude_code_version`. `agent: claude` 면 키가 **없어도** 종료 5 다(본 실험은 버전 고정이 전제라 보수적으로 필수로 했다). 있으면 비교하고 `run_session` 에도 넘긴다.
  - 확인 결과:

    | 경우 | 결과 |
    |---|---|
    | `pilot2.json`(키 없음) | 종료 5 |
    | 키 `2.1.300` 로 `run_matrix` | 종료 5 |
    | `run_session --claude-code-version 2.1.300` | 종료 5 |
    | `2.1.301` 로 함수 직접 호출 | 통과 |

    종료 5 인 경우 모두 결과 디렉터리·워크스페이스를 만들지 않았다.
  - TASK8·9 의 `pilot.json`·`pilot2.json` 은 고치지 않았다(그때 설정의 기록).
- **README "운영" 절:** 버전 고정 방법·현재 버전·되돌리기·다른 머신 설치, 버전 사전 검사, `git config http.postBuffer 157286400`(TASK9 push 실패 원인), 워크스페이스 보관소.

### 2부: 루브릭·익명화
- **`experiment/rubric/rubric-v1.md`**
  - 설계 문서 원문의 정의·증상·점수표·평가 절차·근거 문헌을 그대로 옮겼다. 원문이 이미 D1 매직 ID 증상과 1·2·3점 문구를 담고 있었다.
  - 덧붙인 것 1 — 평가 단위: 최종 상태 `src/main/java`, 사람만 턴 8 중간 평가.
  - 덧붙인 것 2 — 익명화 절차.
  - 덧붙인 것 3 — 차원별 코드 예시.
    - D1: 0(참조 `Member.ensureCanBorrow`+`LoanTier`, `ReservationQueue.place`), 1(참조 서비스의 존재 여부 검증), **2 잠정**(파일럿 `Member` 후원자 ID 집합, 파일럿 `ReserveBookService` 중복 예약 검사).
    - D2: 0(참조 `CreditCheckPort`, `LoadLoanPort.loadActiveLoans → LoanSlice`).
    - D3: 0(참조 `ReturnBookService`)과 "판단이 갈리는 사례"(참조 `LoanBookService` 의 Book·Loan·Reservation).
    - D4: 0(참조 `SuspendMemberService`).
    - 사례가 없는 점수는 "해당 사례 없음"이라고 적었다(지어낸 코드 없음).
  - 예시 출처는 "참조 구현"/"파일럿 세션 코드"로만 적었다. 세션 이름(L0-r1 등)을 쓰면 채점 프롬프트로 수준이 새기 때문이다. 예시 ↔ 세션 대응은 Notes.
  - 턴 8 하드코딩은 2점(잠정)으로 적고 설계자 질문으로 남겼다.
- **`experiment/scripts/anonymize.sh <workspace> <out-dir> <blind-id>`**
  - 입력: git 이면 `git archive HEAD`, 아니면 복사한다.
  - 지우는 것: `.claude/`·`CLAUDE.md`·`CLAUDE.local.md`·`.harness/`·`build/`·`.gradle/`·`gradle/arch.gradle.kts`(+`build.gradle.kts` 의 apply 줄)·`src/test/.../architecture/`.
  - 새 저장소에 고정 작성자·시각 커밋 1개를 남긴다. 남은 흔적은 `leak:` 로 알린다. 종료 코드는 0/2/4.
  - 확인: L2 워크스페이스에서 ArchUnit 테스트·오버레이·apply 줄이 빠지고 커밋 1개만 남았다. 7개 사본 모두 `leak:` 0건이다.

### 3부: `experiment/runner/rate_semantic.py`
- **입력:** 익명 사본 `src/main/java` 전체(파일 머리글 + 코드 블록)와 루브릭 전문을 사용자 프롬프트로 준다.
- **호출:** `claude -p --model opus --output-format json --tools "" --system-prompt <짧은 평가자 지시> --json-schema <스키마> --effort medium`, 빈 임시 cwd.
  - 기본 시스템 프롬프트를 바꾼 것은 Claude Code 의 코딩 지침이 채점 기준으로 섞이지 않게 하려는 것이다(지시서의 "루브릭 외 아키텍처 지식 주입 금지").
- **출력:** 결과의 `structured_output` 을 읽는다. D1~D4 마다 `score` 0~3, `rationale`, `evidence[{file, symbol, quote, reason}]`(차원당 최대 2개)이고, 그 밖에 `ambiguities` 가 있다. 스키마 실패만 1회 재시도한다.
- **회차와 기록:** 같은 입력을 `--runs` 번 따로 부른다. `score_variance` 에 차원·합계별 `scores`·`mean`·`min`·`max`·`variance` 를 남긴다. `rubric`·`code`·`prompt` sha256, 모델 ID, usage, 비용(API 환산)도 기록한다. 저장소당 `--max-cost-usd` 상한(초과 시 종료 3)을 둔다.
- **지시와 다른 점 1 — temperature 0·seed:** 지시는 이 둘을 "명시"하라고 했지만, claude CLI 2.1.301 에 옵션이 없다. 프롬프트에 적어도 샘플링에 효과가 없어 적지 않았다. `temperature: null`, `seed: null`, `sampling_note` 로 기록하고, 반복 분산으로 안정성을 쟀다.
- **지시와 다른 점 2 — 채점 모델:** 설계 원칙은 "실험과 다른 채점 모델"이지만, 지시서 결정대로 Opus 다. 본 실험의 Opus 세션은 자기 채점이 된다(CLAUDE.md·README 에 표기).
- **출력 길이:** 비용 때문에 근거 상한을 4개 → 2개로, 이유를 3문장 이내로 줄였다. 참조 구현만 4개 판이다.

### 4부: 사전 채점 (`experiment/runs/semantic-pretest.md`, 원자료 `experiment/runs/semantic-pretest/`)
- **익명화:** 7개를 무작위 blind-id 로 익명화했다. `blind-map.json` 에 blind-id·세션·원본 경로·원본 HEAD·leak 을 남겼다.
- **채점 결과:** 참조 구현 3회, 파일럿 4세션(3·2·3·2회)을 채점했다. **pilot1 L1-r1·pilot2 L1-r2 는 미채점**이다.
  - 예산 가드: 시험 1회 + 참조 3회 후 누적 > $4.3 이면 다음 저장소를 시작하지 않는다.
  - 원인: 두 저장소에서 2회차 캐시 미적중으로 1회 비용이 약 $0.5 가 되어 예상($0.75/저장소)을 넘었다.
- **비용:** 합계 $4.78 (API 단가 환산 추정, 실제 청구 아님)이다.
- **결과 요지:** 질문 5개 답은 보고서 5절이다.
  - 파일럿 4세션 D1=2(모두), D2=0, D3=0·1.33·0·0, D4=0 이다. 세션 간 분산은 거의 없다.
  - 참조 구현은 D1=1, 나머지 0 이다. 서비스의 존재 여부 검증을 루브릭 1점 문구가 세기 때문이고, **루브릭 쪽 문제로 판단**했다.
  - 3회 반복 안정성은 52칸 중 1칸만 흔들렸다. 다만 예시 고정(anchoring)이 큰 원인이다.

### 문서
- **`experiment/README.md`:** "의미론 채점" 절과 "운영" 절을 새로 썼다.
- **`CLAUDE.md`:** 실험 절에 버전 고정과 의미론 채점 도구 두 줄을 추가했다.
- **`.gitignore`:** `!/experiment/runs/semantic-pretest/`, `!/experiment/runs/semantic-pretest.md` 를 추가했다.

## Notes
- **루브릭 예시 ↔ 세션** (루브릭 파일에는 쓰지 않은 대응)
  - D1 2점 잠정 후원자 ID 집합: pilot2 L0-r1(`36982b26a8ad`) `Member.java`. 파일럿 4개 세션이 거의 같은 형태다.
  - D1 2점 중복 예약 검사: pilot1 L1-r1(`41af13f81a84`) `ReserveBookService.java`.
- **예시 순환 문제(지시를 따른 결과):** 예시를 채점 대상 세션 코드에서 뽑았기 때문에, 채점기가 예시의 잠정 점수를 그대로 가져갔다("잠정 예시와 같은 형태라 2점"). 교정 라운드 전에 예시 출처를 바꾸거나 점수를 떼는 것을 보고서 질문 4-1 에서 권했다.
- **루브릭과 참조 구현 중 어느 쪽 문제인가 — 루브릭이라고 판단한 근거:**
  - 참조 구현의 서비스에 있는 것은 `loadX(...).isEmpty()` → `NotFoundException`(없는 ID → 404 계약)과 등록 시 중복 ID 확인이다. 도메인 규칙이 새어 나간 것이 아니다.
  - 루브릭 1점 문구가 "존재 여부"를 증상으로 명시하고, 0점 문구는 이를 허용하는지 말하지 않는다. 참조 구현을 고칠 이유는 없다(수정도 금지).
- **채점하지 못한 2세션:** 추가 예산 결정이 필요하다. rubric-v2(예시·문구 수정) 뒤 7개를 다시 채점하기를 권했다.
- **비용 위험:** 캐시 적중이 보장되지 않는다. 같은 입력을 연속으로 불러도 2회차가 캐시를 못 읽은 경우가 2/5였다. 본 실험 규모(세션 수백 × 3회)에서는 1회차 비용(약 $0.5)을 기준으로 잡아야 한다.

### Follow-up — 2026-10-17
- **TASK11 과 함께 닫는다(Status completed 유지).**
- **미채점 세션:** 미채점 2세션(pilot1 L1-r1, pilot2 L1-r2)은 채점하지 않는다. 이전 결정은 "추가 예산으로 채울지 설계자 결정"이었다. 변경: 루브릭 v2(TASK11) 뒤, 분산이 확인된 조건에서 다시 채점한다. 사유: 작업 지시 11 의 "v2 로 재채점은 하지 않는다, 채점은 분산이 확인된 뒤에".
- **발견(TASK11):** 이 작업의 `claude -p` 채점 호출 무렵(10-17 01:35) 격리 설정 `~/.claude-arch-bench` 에 claude.ai 동기화 스킬·플러그인이 생겼다.
  - 채점 호출은 `--tools ""`·`--system-prompt` 로 돌았으므로, 채점 결과에는 영향이 없다고 본다.
  - 에이전트 세션은 TASK11 에서 설정(`syncClaudeAiSkills`·`syncClaudeAiPlugins: false`, `disableClaudeAiConnectors: true`)으로 막았다.

