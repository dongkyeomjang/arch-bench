# TASK13 — 본 실험 전 마지막 정비: 리뷰 집계·모델별 상한·치유 근거·사용률 기록 + pilot5(Sonnet·Opus L3 한도 실측)

- **Date**: 2026-10-17
- **Status**: completed
- **Related TASKs**: TASK12 (pilot4, 리뷰 집계 빈틈, main.json 초안), TASK11 (침식 시드·pilot3), TASK9 (구독 한도 대기·재시도)
- **Commits**: e3e60f9, e81ad92

## Request
작업 지시 13. 본 실험 전 마지막 정비다. 이 작업이 끝나면 사전 등록 전까지 코드 변경이 없다.

1. **러너 수정**
   - `SendMessage` 재검토도 리뷰 호출로 센다. pilot4 L3 가 18회로 나오는지 확인한다.
   - 모델별 세션 상한을 받는다.
   - 치유 근거 `healing_context` 를 추출한다. 분류는 사람이 한다.
   - 사용률 `quota_before`·`quota_after` 를 기록한다.
   - 사전 검사에 `ENABLE_CLAUDEAI_MCP_SERVERS=false` 확인을 넣는다.
2. **`main.json`:** 68세션 기본안으로 바꾸고 dry-run 을 남긴다. 실행하지 않는다.
3. **pilot5:** Sonnet·Opus L3 를 1세션씩 돌린다. 사용률 전후와 한도 응답 실물을 본다.
4. **보고서:** `pilot5-report.md` 에 한도 소모 표, 예상 소요 주 수, 치유 근거 표, 설계자 결정 사항을 쓴다.
5. **README:** 루트 README 에 연구 개요를 쓴다.

제약: 비용은 $50 이내(API 환산)이고, 러너는 1부와 3부(한도 감지) 범위에서만 바꾼다.

## Context
- **리뷰 집계 빈틈:** 2.1.301 은 리뷰어 서브에이전트를 비동기로 띄운다. 에이전트가 `SendMessage(to=<agentId>)` 로 재검토를 보내면 TASK12 까지의 파서는 그것을 세지 못했다. pilot4 L3 에서 17 로 셌지만 실제는 18 이었다.
- **본 실험 규모:** pilot4 재추정(Opus ≈ Haiku × 39.6 토큰 단가)으로 84세션 단일 상한안을 돌릴 수 없음을 확인했다. 설계자가 68세션 + 모델별 상한으로 바꿨다. Opus 반복 수는 한도 소모 실측으로 정하기로 했다.
- **구독 사용률 읽기:** `/status`·`/usage` 는 대화형 명령이라 `-p` 출력으로 파싱할 수 없다. 대신 stream 의 `rate_limit_event.unifiedWindows` 가 창별 사용률을 소수 둘째 자리(1%p)로 준다.

## Changes
- **리뷰 집계** (`run_session.py` `parse_stream`)
  - 리뷰어 결과의 `agentId: <id>` 를 모으고, 그 id 로 가는 `SendMessage` 를 리뷰 호출로 센다(`reviewer_resend_calls`).
  - 판정은 같은 `tool_use_id` 의 `task_notification` 요약에서 읽고, 호출 순서는 stream 줄 순서로 정렬한다.
  - pilot4 L3 재집계: 18회(APPROVE 15, REJECT 3). pilot4 의 `session.json` 은 다시 쓰지 않았다.
- **모델별 세션 상한** (`run_matrix.py`)
  - `max_session_cost_usd` 를 단일 값 또는 `{모델: 값}` dict 로 받는다(`session_cap()`).
  - dict 에 설정의 모델이 빠지면 종료 2 다.
  - fake 매트릭스로 확인했다: haiku 0.5 가 넘어가 턴 1 뒤 `session_cost_cap` 으로 멈췄다.
- **사용률 기록** (`run_session.py`)
  - `quota_probe()` 가 세션 직전·직후에 짧은 haiku `-p` 호출을 한다. 저장소 밖 임시 디렉터리에서 같은 에이전트 환경으로 부르고, `rate_limit_event` 의 창별 utilization·resets_at 을 `quota_before`·`quota_after` 에 남긴다.
  - 탐침 비용은 세션 비용에 넣지 않는다. 수동 절차(claude.ai Usage 화면)는 README 에 적었다.
- **사전 검사 ⑨** (`run_session.py`): `precheck_agent_env()` 가 `EXTRA_ENV`, 특히 `ENABLE_CLAUDEAI_MCP_SERVERS=false` 를 확인한다. `EXTRA_ENV` 에서 빼면 거부되는 것을 확인했다.
- **치유 근거** (`trace_violations.py`)
  - `healing_context` 는 치유 턴마다 다음을 모은다.

    | 필드 | 내용 |
    |---|---|
    | `hook_exit2` | 그 턴 훅 exit 2 가 있었는가 |
    | `mentions` + 발췌 | archTest·build·CLAUDE.md·리뷰어 언급 |
    | `request_exposed` | 요청이 그 파일을 건드리게 하는가 |
    | `reviewer_reject` | 그 턴 리뷰어 REJECT 가 있었는가 |

  - `request_exposed` 는 참조 구현의 같은 턴 커밋으로 근사한다(턴 1 은 lending 원본과 비교, 턴 8·10 은 재설계 커밋, E4 는 DateUtils 를 import 하는 파일).
    - 치유 턴의 세션 diff 는 치유 자체가 그 파일을 바꾸므로 이 질문에 쓸 수 없어서 이 방법을 택했다.
    - 한계: 참조 구현은 깨끗한 시드에서 출발했으므로 E1 같은 침식 시드 특유의 경로를 놓친다.
  - pilot3·4 6세션에 다시 적용했다. 워크스페이스는 보관소 경로를 `--workspace` 로 넘겼다. 기존 필드 값은 바뀌지 않았다.
- **`main.json`:** 68세션, `max_cost_usd 800`, 모델별 상한 `{"haiku": 1.5, "sonnet": 15, "opus": 30}`. dry-run 을 README 에 넣었다. 무작위화 블록 크기는 24·20·8·8·8 이다.
- **pilot5**
  - 실행: `pilot5.json`. 실행 전에 pilot4 워크스페이스를 `~/Project/arch-bench-ws-pilot4` 로 옮겼다(본 루트 잔여물 검사 때문).
  - 결과:

    | | Opus | Sonnet |
    |---|---|---|
    | 비용 (API 단가 환산 추정, 실제 청구 아님) | $10.73 | $3.95 |
    | 시간 | 36분 | 20분 |
    | 5시간 창 소모 | +12%p | +7%p |

    - 주간 소모는 두 세션 합이 0.26 → 0.27 이다.
    - 두 모델 모두 턴 1 에 훅 exit 2 한 번 뒤 기준선을 모두 고쳤고, FT 는 100% 였다.
    - 리뷰어 REJECT: Opus 2, Sonnet 4. Sonnet 은 2턴에서 2회 REJECT 뒤 그대로 커밋했다.
    - 함정 턴: Sonnet 이 턴 12 를 거부했다. 거부는 pilot 통틀어 처음이다.
    - 실제 한도 거부는 없었다.
  - 보고서: `experiment/runs/pilot5-report.md`.
- **summarize:** 결과 루트에 모델이 둘 이상이면 세션 이름에 모델을 넣는다(`MULTI_MODEL`). pilot5 에서 두 세션이 `L3-r1` 로 겹쳐서 고쳤다. pilot3·4 보고서는 다시 생성해도 바이트 단위로 같다.
- **문서**
  - 루트 `README.md` 에 연구 개요를 썼다: 질문, 요인 표, 하네스 4수준, 저장소 지도, 파일럿 1~5 와 설계 변경 이력, 재현. 운영 절을 그 아래에 두었다.
  - `experiment/README.md`: 사전 검사 ⑨, 리뷰 집계, 사용률 탐침과 수동 절차, pilot5 한도 관찰, `healing_context`, main.json 절, pilot5 항목.
  - `.gitignore` 에서 pilot5 결과를 추적하도록 풀었다.

## Notes
- **비용:** pilot5 $14.69 + 탐침 약 $0.003 이다(API 단가 환산 추정, 실제 청구 아님).
- **측정 위생:** 사용률은 계정 전체 값이라, pilot5 가 도는 동안 조율 대화는 아무 작업도 하지 않았다.
- **주간 사용률 해상도는 1%p 다.** 세션당 주간 소모는 5시간 창 소모 × (주간/5시간 비율 약 0.05, 상한 0.11)로 추정했다.
  - 68세션: 1주 미만(순수 실행 약 32시간).
  - Opus 5반복(84세션): 약 1주.
  - Sonnet·Opus 의 L0~L2 는 실측하지 않았다. 계산에는 L3 값을 썼다.
- **한도 거부 경로는 여전히 실물 미검증이다.** 본 실험의 첫 거부를 기록으로 확인하고, 감지가 틀리면 그 세션만 다시 돌리는 규칙을 사전 등록 문서에 두기를 권했다.
- **설계자 결정 대기:**
  - Opus 반복 수(2 / 5). 한도 측면 제약은 작다.
  - 리뷰어 비동기·`ScheduleWakeup` 을 그대로 둘지. 모델마다 대기 방식이 다르다(Haiku 는 wakeup, Sonnet 은 알림 대기, Opus 는 동기).
  - 훅 지문 미갱신 동작을 알려진 동작으로 등록할지.
- **필수 코드 변경은 남지 않았다.**
