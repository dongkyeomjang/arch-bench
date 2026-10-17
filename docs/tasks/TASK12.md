# TASK12 — 사전 검사 확장·노출 추적·매트릭스 blocks + 침식 시드 L2·L3 파일럿(pilot4)

- **Date**: 2026-10-17
- **Status**: completed
- **Related TASKs**: TASK11 (침식 시드·pilot3·격리 설정 정리), TASK10 (루브릭·버전 고정), TASK9 (fresh 모드·블록 무작위화)
- **Commits**: 927bf0a, fbcf614, 5fff5ad, 1eee376

## Request
작업 지시 12. 다음 여섯 가지를 한다.

1. 사전 검사를 확장하고, init 에 오염이 보이면 세션을 멈춘다.
2. `trace_violations.py` 에 노출 추적(`exposed_turns`, 4분류)과 `rule_placement` 를 넣고 pilot3 에 다시 적용한다.
3. `pilot4.json` 을 만든다: L2·L3 × haiku × eroded × 1, fresh.
4. `pilot4-report.md` 를 쓴다: L2 턴 1 집중 분석, L2 턴 2~15, L3 리뷰어, 설계자 질문 5개.
5. 루브릭 v2 를 고친다: D2 에서 E5 를 빼고, 합성 D1 2점 예시를 넣는다.
6. `main.json` 의 blocks 를 지원하고 84세션 dry-run 을 남긴다. **실행하지 않는다.**

제약은 다음과 같다.

- 채점기·하네스 텍스트·턴 파일·숨겨진 테스트·참조 구현·침식 패치는 수정하지 않는다.
- 러너는 1·2·6부 범위에서만 바꾼다.
- 총 비용은 API 환산 $3 이내다.
- "결과가 어느 쪽이든 그대로 보고하라."

## Context
- **TASK11 은 격리 설정에서 claude.ai 커넥터를 껐다고 보고했지만 틀렸다.**
  - 러너는 `--setting-sources project` 로 에이전트를 띄운다. 그래서 사용자 설정(`CLAUDE_CONFIG_DIR/settings.json`)의 `disableClaudeAiConnectors` 가 에이전트에 적용되지 않는다.
  - TASK11 의 확인 탐침은 우연히 커넥터가 없던 1회였다.
  - 기록을 다시 세어 보니 pilot1·2·3 에서 각각 14/30·19/60·19/60턴에 커넥터 MCP 서버가 실려 있었다. 호출은 0건이다.
  - pilot4 1차 시도가 새 init 검사에 걸리면서 드러났다.
- **훅 stderr 는 `--include-hook-events` 없이는 스트림에 없다.** 4부의 "exit 2 stderr 에 나온 규칙" 분석에 필요해 추가했다. 출력 전용 플래그이며, 지시서의 러너 범위를 문자 그대로 보면 벗어난다.
- **2.1.301 의 init 은 언제나 번들 스킬 19개와 `@builtin` 플러그인 3개를 싣는다.** "스킬·플러그인이 하나라도 있으면 실패"를 문자 그대로 적용하면 모든 세션이 실패한다. 그래서 허용 목록을 두었다. 이는 지시서와 다른 결정이다.

## Changes
- **1부: 사전 검사와 init 오염 중단** (`experiment/runner/run_session.py`)
  - `precheck_config_dir` 에 세 가지를 확인하게 했다. 하나라도 어기면 종료 5 다.
    - `settings.json` 의 `syncClaudeAiSkills`·`syncClaudeAiPlugins` 는 false, `disableClaudeAiConnectors` 는 true 여야 한다. 키가 없어도 거부한다.
    - `plugins/` 에는 `known_marketplaces.json`·`marketplaces` 와 비어 있는 `installed_plugins.json` 만 허용한다.
    - `skills/` 는 비어 있어야 한다.
  - `init_contamination()` 이 init 이벤트의 mcp_servers·skills·plugins 를 `session.json` 의 `init_config` 에 기록한다.
    - 오염으로 보는 것: MCP 서버, `BUNDLED_SKILLS` 밖의 스킬, `@builtin` 이 아닌 플러그인.
    - 오염이 있으면 턴 1 비용을 기록한 뒤 `failed` / `config_contamination` 으로 멈춘다.
  - `EXTRA_ENV` 에 `ENABLE_CLAUDEAI_MCP_SERVERS=false` 를 넣었다(`fbcf614`). 탐침에서 커넥터가 있던 실행이 8/8 에서 0/8 이 됐다.
  - `--include-hook-events` 를 켜고 `hook_responses`(exit_code, rules, stderr_head)를 파싱한다(`5fff5ad`).
  - `fake_agent.sh` 에 `FAKE_AGENT_INIT_MCP` 를 추가했다.
  - 검증:
    - 임시 설정 7개에서 정상 2개는 0, 오염 5개는 종료 5 였다.
    - fake 세션은 턴 1 뒤 `config_contamination` 으로 멈췄다.
- **2부: 노출 추적과 규칙 위치** (`trace_violations.py`, `summarize.py`)
  - `exposed_turns` 는 원인 파일이 `git diff --name-only` 에 나온 턴이다.
  - 분류는 `healed_when_exposed` / `healed_unexposed` / `persisted_despite_exposure` / `never_exposed` 네 가지다.
  - `rule_placement` 는 턴 2·7·8·13 의 규칙이 domain / service / both / unknown 중 어디에 있는지 판정한다.
    - 근거는 정규식 증거 세 가지다: throw, 조건문 안의 throw, 비교가 들어간 return.
    - domain 과 `application.service` 만 본다.
  - pilot3 의 trace 를 다시 생성했다.
    - L0 는 4/4 service, L1 은 4/4 domain 이다. TASK11 의 사람 판정과 같다.
    - `healed_unexposed` 는 0 이다.
- **3·4부: pilot4**
  - 실행: `pilot4.json`, 세 번 시도(1차 오염 중단, 2차 의도적 중지, 3차 완료).
  - 보고서: `experiment/runs/pilot4-report.md`.
  - 결과(두 세션 모두):
    - 구조: vc 4 → 0. E1~E5 가 모두 `healed_when_exposed` 다.
    - FT 100%, tamper 0.
    - L2 는 턴 1·2 에서 exit 2 를 "pre-existing" 으로 무시하다가 턴 3 에서 모두 고쳤다. L3 는 턴 2 에서 고쳤다.
    - 고친 이유는 두 세션 모두 "build 가 통과해야 커밋" 조건을 archTest 로 잘못 읽은 것이었다.
    - L3 리뷰어: 18회 호출, REJECT 3회. 세 번 모두 새 diff 의 D1 유형이었다.
    - 리뷰어는 E1 을 보고도 "기존 문제"로 판정에서 뺐다.
    - 턴 1 에는 에이전트가 리뷰 요청문으로 기준선 실패를 범위 밖으로 지정했다. 리뷰어는 archTest 가 실패하는데도 APPROVE 했다.
  - 단가 탐침: 토큰당 Sonnet 은 Haiku 의 약 19.7배, Opus 는 약 39.6배다. 따라서 `main.json` 의 상한 3.0·150 으로는 돌릴 수 없다. 84세션 합계는 약 $680 으로 추정된다(API 단가 환산 추정, 실제 청구 아님).
  - `.gitignore` 에서 pilot4 결과와 보고서를 추적하도록 풀었다.
- **5부: 루브릭** (`experiment/rubric/rubric-v2.md`)
  - D2 예시에서 E5 를 뺐다. "E5는 포트 우회(R3)이며 루브릭 차원에 해당하지 않음" 을 명시했다.
  - 장비 대여 도메인의 합성 D1 2점 예시를 추가했다. 세션 코드는 인용하지 않았다.
- **6부: 매트릭스** (`run_matrix.py`, `main.json`)
  - `blocks` 배열을 지원한다. 무작위화 블록 r 은 reps ≥ r 인 설정 블록의 셀을 합친 것이다.
  - `main.json` 은 84세션이고, 블록 크기는 24·24·12·12·12 다. dry-run 출력은 README 에 있다.
  - 평면 설정(pilot2·pilot3·matrix.example)의 dry-run 순서는 바이트 단위로 그대로다.
- **문서**
  - README:
    - 사전 검사 ⑦⑧, init 오염 항목.
    - 환경 변수, 훅 이벤트.
    - 노출 추적의 논리와 한계.
    - main.json 절과 pilot4 재추정.
    - TASK11 정정.
  - CLAUDE.md: 커넥터 환경 변수와 사전 검사 한 줄.

## Notes
- **비용:** TASK12 합계는 약 $1.04 다(API 단가 환산 추정, 실제 청구 아님).
  - pilot4 3차 시도: $0.944
  - 1차 시도: $0.0575
  - 탐침: 약 $0.034
- **미해결 1 — `reviewer_calls` 누락:** `SendMessage` 로 보낸 재검토를 세지 않는다. L3 턴 15 에서 17 로 셌지만 실제는 18 이다. 본 실험 전에 고칠 것을 권한다.
- **미해결 2 — 리뷰어 비동기 대기:** 2.1.301 에서 리뷰어 서브에이전트가 기본으로 비동기로 돈다. 에이전트는 `ScheduleWakeup` 으로 기다렸고, 그래서 L3 세션 시간이 L2 의 2.4배다. `--disallowedTools ScheduleWakeup` 은 하네스 조건을 바꾸는 결정이라 설계자에게 넘겼다.
- **미해결 3 — 훅 지문:** `arch-check.sh` 는 `limit` 으로 끝날 때 지문을 갱신하지 않는다. 그래서 한도에 닿은 다음 턴의 첫 도구 호출에서 archTest 가 돈다. 훅 텍스트는 동결이라 기록만 했다.
- **노출 판정의 한계:** 치유한 턴은 원인 파일을 바꾸므로 언제나 노출 턴이 된다. 따라서 `healed_unexposed` 는 다른 파일이 바뀌어 위반이 사라진 경우만 남는다.
- **설계자 결정 대기:**
  - Sonnet·Opus L3 를 1세션씩 실측할지(추정 약 $34).
  - 모델별 세션 상한을 둘지.
  - `max_cost_usd` 를 얼마로 둘지.
