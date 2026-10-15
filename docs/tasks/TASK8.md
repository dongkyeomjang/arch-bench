# TASK8 — 파일럿 1차 실행(2세션)과 1차 분석, 세션당 $1 초과로 2차 보류

- **Date**: 2026-10-15
- **Status**: in-progress (1차 완료·보고, 2차 8세션은 설계자 결정 대기)
- **Related TASKs**: TASK6, TASK7
- **Commits**: 9d474e6 (파일럿 1차 설정·`skip_agent_tests`), 86df9f6 (결과·summarize.py·보고서)

## Request
"작업 지시 8: 파일럿 실행과 1차 분석". 채점기·하네스·턴 파일·숨겨진 테스트·참조 구현은 수정하지 않는다.

1. **실행**
   - `pilot.json` 에 `skip_agent_tests` 를 추가하고, `max_cost_usd` 2.0, `reps: 1` 로 L1·L2 각 1세션을 먼저 돌린다.
   - 세션당 $1 초과 또는 `failed` 면 멈추고 보고한다. 러너 버그만 수정을 허용한다.
   - 정상이면 2차 8세션을 돌린다(총 상한 $15).
2. **`experiment/runner/summarize.py`**: 세션 요약·히트맵·규칙별·수준 비교·실패 목록 표를 만든다.
3. **질적 관찰**: 함정 50건을 분류(복종·거부·우회·무관)하고, 턴 12·턴 8×4 를 따로 보고, L2 훅 exit 2 반응을 기록한다.
4. **보고서** `experiment/runs/pilot-report.md`: 설계자 질문 목록과 300세션 예산 재산정을 담는다. `experiment/runs/pilot/` 를 커밋한다.

## Context
- **2차를 보류한 이유:** 1차 2세션의 비용이 L1 $0.873, L2 $1.097(14턴 시점)이었다. 지시의 "$1 초과 시 멈추고 보고" 조건에 걸렸다.
- **비용이 오른 원인:** Haiku 5.5 의 프롬프트 >100k 단가 구간(5배)이다. 버그가 아니다.
  - `--resume` 으로 대화를 이어 가므로 턴 4~5 부터 맥락이 100k 를 넘는다.
  - TASK7 의 $0.5 추정은 `_smoke` 3턴(구간 진입 전)만 보고 늘려 잡은 값이라 이 계단을 놓쳤다.
- **에이전트 행동:** 두 세션 모두 15턴(L2 14턴) 내내 `violating_classes 0`, 숨겨진 테스트 100% 통과였다.
  - 함정 10건은 복종 0, 거부 2(턴 12), 우회 4(턴 5·14), 무관 4(턴 8·10)였다.
  - 목적 2 의 "L1 위반율 20~60%" 는 바닥(0%)이다.
- 그래서 진행·구성 변경(L0 추가)·함정 문구 수정은 설계자가 정하도록 보고서 5절에 질문으로 남겼다.

## Changes
- **매트릭스 옵션 (`experiment/runner/run_matrix.py`)**
  - 설정 키 `skip_agent_tests`(기본 `false`)를 추가했다. `true` 면 `run_session.py` 에 `--skip-tests` 를 넘긴다.
  - dry-run 머리줄에 표시한다.
  - fake `_smoke` 로 확인했다: `runner.log` 명령줄에 `--skip-tests` 가 있고, 턴 1·2 는 `tests_status` 가 없으며 마지막 턴만 있다.
- **파일럿 설정 (`experiment/runner/pilot.json`)**
  - 1차용으로 `reps: 1`, `max_cost_usd: 2.0`, `skip_agent_tests: true` 로 바꿨다.
  - 실측 후 `est_cost_per_session_usd.haiku` 를 0.5 에서 1.1 로 고쳤다.
- **1차 실행**
  - 명령: `run_matrix.py --config experiment/runner/pilot.json --ws-root ~/Project/arch-bench-ws --out experiment/runs/pilot`. 실행 전 ws 루트가 비어 있었다.
  - 결과: L1-r1 completed, L2-r1 failed(의도적 중단).
  - **중단 방법:** 매트릭스 비용 가드는 새 세션만 막는다. 그래서 대화 세션에서 임시 감시 스크립트를 붙였다.
    - 조건은 세션 누적 비용 > $1 또는 합계 > $2 이고, 걸리면 `run_session.py` 에 SIGINT 를 보낸다.
    - 이 스크립트는 저장소에 넣지 않았다.
  - **기록 밖 비용:** L2 턴 15 는 에이전트 실행 도중 멈춰서 `turn-15/stream.jsonl` 만 남았다. 이 턴 비용(usage 재계산 ≈ $0.056)은 `session.json` 밖이다.
- **요약 스크립트 (`experiment/runner/summarize.py`, 신규, 표준 라이브러리만)**
  - `load`: `*/session.json` 과 `turn-*/turn.json` 을 읽는다. `_failed-<ts>` 는 제외한다.
  - `metrics`: SV_session, SV_first_turn(없으면 16), 최종 vc, FT_pass %, tamper, 훅 실행/exit 2, escaped_cwd, 러너 대리 커밋, 턴당 입력 토큰(캐시 포함/비캐시), 규칙별 hit 를 계산한다.
  - 만드는 표: 세션표, `violating_classes` 히트맵(함정 턴 `*`), 숨겨진 테스트 히트맵(CE 표시), 규칙별 세션 수(L1/L2, R4·R5 참고), 수준 비교, 턴별 비용·토큰, 실패·이상.
  - **보고서 갱신 방식:** `<!-- summarize:begin/end -->` 블록만 바꾼다. 사람이 쓴 절을 보존하려는 것이다. 두 번 실행해 md5 가 같은 것을 확인했다.
- **보고서 (`experiment/runs/pilot-report.md`)**
  - 0 절: 중단 사유와 2차 예상 비용.
  - 1 절: 요약표.
  - 2 절: 함정 10건 분류표, 턴 12, 턴 8×4 표, L2 훅(231회 중 exit 2 0회, `compile_failed` 35%), 기타(L2 의 자체 브랜치 `feat/member-api` 등).
  - 3 절: 비용 계단 분석.
  - 4 절: 300세션 예산. 공개 단가(2026-10-15 platform.claude.com pricing)와 세션 합계 토큰으로 Sonnet·Opus 를 환산했다.
  - 5 절: 설계자 질문 11개.
  - 6 절: 남은 일.
- **데이터 커밋**
  - `.gitignore` 의 `/experiment/runs/` 를 `/experiment/runs/*` 와 예외 `!/experiment/runs/pilot/`·`!/experiment/runs/pilot-report.md` 로 바꿨다.
  - `runs/pilot/` 는 4.5MB 라 `stream.jsonl` 까지 전부 커밋한다.
- **문서**
  - `experiment/README.md`: 파일럿 절을 실측 비용 표, `skip_agent_tests`, `summarize.py` 설명으로 바꿨다.
  - `CLAUDE.md`: 실험 명령에 `summarize.py` 한 줄을 추가했다.

## Notes
- **러너 버그:** 없음. 러너 수정도 없다(`run_session.py` 무변경).
- **실호출 비용:** 기록된 값 $1.970 에 기록 밖 ≈ $0.056 을 더해 **≈ $2.03** 이다. 1차 상한 $2 를 약 $0.03 넘었는데, 감시가 턴 경계에서만 비용을 볼 수 있어서다.
- **예산 환산의 방법과 한계**
  - 스트림의 메시지 id 별 usage 로 Haiku 비용을 다시 계산하면 보고값의 약 60% 만 나온다. 스트림에 모든 API 호출이 남지 않기 때문이다(예: 턴 1 의 `num_turns` 25 인데 메시지 id 9개).
  - 그래서 예산 환산은 `modelUsage` 합계 토큰(= `session.json` 토큰 합)에 단가를 곱했다.
  - Sonnet·Opus 5.5 는 크기 구간이 없어 합계로 정확하지만, "같은 토큰을 쓴다"는 가정이 남는다.
- **2차를 그대로 진행할 경우**
  - `--resume` 은 L2-r1(failed)을 `_failed-<ts>` 로 옮기고 처음부터 다시 돌린다. 보고서의 L2 관찰은 그 디렉터리에 남지만, `summarize.py` 는 `_failed-` 를 집계에서 뺀다.
  - 지시서 공식의 `max_cost_usd` = 실측 세션당 × 10 × 1.5 ≈ $16.5 는 총 상한 $15 를 넘는다. 상한은 $15 로 잡아야 한다.
- **워크스페이스:** 지우지 않았다(`~/Project/arch-bench-ws/41af13f81a84`, `ea8aa393f496`). 다음 claude 매트릭스는 같은 `--out experiment/runs/pilot` 이면 `workspace-index.jsonl` 에 있어 사전 검사를 통과한다. 다른 결과 루트를 쓰면 종료 5 다.
- **함정 턴 정보의 위치:** 함정 턴 번호가 `summarize.py`·`pilot-report.md` 에 있다. 둘 다 에이전트 워크스페이스로 복사되지 않는다(시드·하네스 밖).
