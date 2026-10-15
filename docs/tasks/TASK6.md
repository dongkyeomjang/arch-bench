# TASK6 — 워크스페이스 누설 차단 + 매트릭스 실행기

- **Date**: 2026-10-15
- **Status**: completed
- **Related TASKs**: TASK4, TASK5
- **Commits**: 90a2002

## Request
"작업 지시 6". TASK5 남은 숙제에 대한 결정:
- `ArchitectureTest.REPORT` 상수명은 바꾸지 않는다(종결).
- `ls ..` 로 다른 세션이 보이는 문제와 `turn NN (runner)` 커밋 메시지는 고친다.

1부(워크스페이스 누설 차단):
- 워크스페이스를 `$ARCH_WS_ROOT/<무작위 토큰>/lending` 으로 바꾼다.
- 토큰 → 세션 역추적표 `$OUT_ROOT/workspace-index.jsonl` 을 만든다.
- 에이전트가 워크스페이스 위로 나가는 Bash 를 세어 `escaped_cwd_count` 에 기록한다(측정만).
- 러너 커밋 메시지를 `Save work in progress` 로 바꾼다.

2부(매트릭스 실행기):
- `run_matrix.py` 와 `matrix.example.json` 을 만든다.
- 시드 고정 무작위 순서, `--dry-run`, 병렬 워커, `--resume`, 비용 가드, `matrix-status.json` 을 갖춘다.
- 검증 1~4(fake)는 필수, 5(haiku 실호출 $0.15 이내)는 선택이다.

제약:
- `run_session.py` 변경은 1부 범위로 한정한다.
- 표준 라이브러리만 쓴다.
- 실호출 합계는 $0.2 이내다.

## Context
- **누설 경로:** TASK5 러너의 워크스페이스는 `$ARCH_WS_ROOT/_smoke-L2-haiku-r1` 이었다. 에이전트가 `pwd` 나 `ls ..` 만 해도 자기 하네스 수준과 형제 세션의 수준을 읽을 수 있었다.
- **커밋 메시지:** 러너 커밋이 다음 턴부터 에이전트의 `git log` 에 `turn 02 (runner)` 로 보였다.
- **매트릭스 순서:** 매트릭스는 조건 수 × 반복만큼 세션을 돌린다. 같은 조건이 연달아 돌면 API 상태·시간대 효과가 조건과 교락되므로 순서를 섞어야 한다.

## Changes
- **워크스페이스 경로 무작위화 (`run_session.py`)**
  - `ws = ws_root / secrets.token_hex(6) / "lending"` 으로 만든다. 에이전트 cwd 는 `.../lending` 이고 `ls ..` 에는 `lending` 하나만 보인다.
  - `session.json` 에 `workspace_token` 을 남기고, `$OUT_ROOT/workspace-index.jsonl` 에 `{token, session_id, created_at}` 를 append 한다. 한 줄 append 라 병렬 세션도 안전하다.
  - 덮어쓰기 거부(종료 4)는 이제 결과 디렉터리 기준이다. 토큰 디렉터리가 우연히 겹치는 경우도 거부한다.
- **러너 커밋 메시지 (`run_session.py`, `commit_check`)**
  - 메시지를 `RUNNER_COMMIT_MESSAGE = "Save work in progress"` 로 바꿨다. 턴 번호를 쓰지 않으므로 `turn_name` 인자를 없앴다.
- **`escaped_cwd_count` 측정 (`run_session.py`, `escapes_cwd`·`parse_stream`)**
  - Bash `tool_use` 의 `command` 를 어휘 분석한다:
    1. heredoc 본문을 제거한다.
    2. 셸 구분자로 나눈다.
    3. `cd` 로 cwd 를 추적한다. 턴 시작 cwd 는 ws 이고, cwd 는 셸별(메인·서브에이전트)로 따로 둔다.
  - 아래 둘 중 하나면 1건으로 센다:
    - `..` 경로를 풀었더니 ws 밖이다.
    - 절대 경로가 `ws_root` 아래인데 ws 밖이다.
  - `turn.json` 에 `escaped_cwd_count` 와 `escaped_cwd_commands`(앞 500자)를 남기고, `session.json` 의 `turns` 요약에도 count 를 넣었다.
- **fake 에이전트 (`fake_agent.sh`)**
  - 턴 1 에 Bash `ls ..; ls ../..` tool_use 를 추가했다.
  - `FAKE_AGENT_COST_USD`(기본 0)를 받아 `total_cost_usd = 턴 번호 × 값`(claude 처럼 누적값)을 낸다. 비용 가드 검증용이다.
- **매트릭스 실행기 (`runner/run_matrix.py`, `runner/matrix.example.json`)**
  - **순서:** 세션 ID 로 정렬한 뒤 `random.Random(shuffle_seed)` 로 섞는다(기본 20261215).
  - **`--dry-run`:** 목록, 순서, `est_cost_per_session_usd` 합을 출력한다.
  - **실행:** 워커 스레드 `parallel` 개가 각자 `run_session.py` 를 subprocess 로 호출한다.
    - 로그는 실행 중 `.matrix-logs/<id>.log` 에 쓰고, 끝나면 `<id>/runner.log` 로 옮긴다.
  - **`--resume`:**
    - completed 세션은 skipped 로 두고 비용은 합산한다.
    - 그 밖의 흔적은 결과 디렉터리를 `<id>_failed-<ts>`, 워크스페이스를 `<token>_failed-<ts>` 로 옮긴 뒤 다시 돌린다.
  - **비용 가드:** 세션이 끝날 때마다 합계가 `max_cost_usd` 를 넘었는지 본다. 넘으면 새 세션을 시작하지 않고 종료 3 으로 끝낸다.
  - **`matrix-status.json`:** 원자적으로 갱신한다(임시 파일 → rename). 남은 시간(ETA)도 기록한다.
  - **시작 전 검사:** `run_session` 의 precheck 를 import 해서 시작 전에 한 번 돈다.
  - **종료 코드:** 0, 1, 2, 3, 4, 5.
- **문서**
  - `experiment/README.md` 갱신:
    - 러너 표(워크스페이스·커밋 확인)
    - 스트림 파싱(escaped_cwd)과 fake 설명
    - "매트릭스 실행기" 절: 절차, 계약 표, `matrix-status.json` 예시, 병렬 수 권고, 검증 표
    - 러너 검증 5~7 과 알려진 한계 2·4·6
  - `CLAUDE.md` 실험 절에 run_matrix 명령과 워크스페이스 경로 규칙을 추가했다.

## Notes
- **지시와 다른 점**
  - **escaped_cwd 판정 방식**
    - 1차 구현은 지시서 예시대로 `..` 와 `$ARCH_WS_ROOT` 를 문자열 정규식으로 검사했다.
    - 실호출(haiku L2 턴 1)에서 `cd <ws>/src && for f in ...; cd ..; cat .claude/settings.json` 를 1건으로 셌다. 실제로는 워크스페이스 안으로 돌아온 것이다.
    - 이 오탐은 수준 간에 고르게 나지 않을 수 있으므로 cwd 추적 방식으로 바꿨다. 같은 스트림을 재파싱하니 0건이었고, 명령 사례 22개가 모두 일치했다.
    - 남은 한계: 어휘 분석이라 `$VAR`·`~`·스크립트 파일 안의 경로는 따라가지 못한다(과소 집계 쪽).
  - **세션 상태 이름**
    - 지시서는 실패 세션을 `status: failed|timeout` 으로 표현했지만, `run_session.py` 는 `completed`·`aborted` 만 쓴다(1부 범위 밖이라 그대로 뒀다).
    - 매트릭스는 completed 가 아닌 모든 흔적(aborted, running, session.json 없음)을 실패로 보고 다시 돌린다.
  - **비용 합산 범위**
    - 지시서는 "완료된 세션"의 비용을 합산하라고 했다.
    - 실패 세션의 비용도 실제로 쓴 돈이므로 합산에 넣었다(보수적).
    - `--resume` 으로 건너뛴 completed 세션도 매트릭스 전체 예산에 포함한다.
  - **`runner.log` 위치**
    - `run_session` 이 결과 디렉터리가 이미 있으면 거부하므로, 실행 중에는 `$OUT_ROOT/.matrix-logs/` 에 쓰고 끝난 뒤 옮긴다.
  - **Gradle 데몬 메모리**
    - 지시서는 "세션당 약 1GB"로 예상했다.
    - 실측(fake 12세션, 병렬 2)은 데몬 최대 2개, RSS 합 최대 1147MB 였다. 그래서 README 에는 세션당 약 0.5GB 로 적었다.
    - 한가한 데몬은 워크스페이스를 가리지 않고 재사용된다.
- **비용 가드 동작**
  - 가드는 세션 단위로 걸리므로 이미 돌고 있는 세션 때문에 최대 `parallel - 1` 세션분 초과할 수 있다. 검증 4 에서는 한도 0.01 에 0.03 을 썼다.
  - 실험 예산은 이 여유를 빼고 잡아야 한다.
- **`ls ../..` 로 보이는 것**
  - 형제 토큰 디렉터리가 보이고, 그 안을 읽으면 다른 수준의 하네스(`.claude/`, CLAUDE.md)를 볼 수 있다. 지시서 판단대로 막지 않고 `escaped_cwd_count` 로 측정한다.
  - 현재 `$ARCH_WS_ROOT` 에는 TASK5 이전 형식(조건이 들어간 이름)의 검증 워크스페이스가 남아 있다. 본 실험 전에 비워야 한다(README 알려진 한계 6).
- **실호출 비용:** 합계 $0.0608(haiku L1·L2 병렬 3턴). 지시 한도 $0.2 이내다.
- **verification 출력 위치:** 세션 결과는 scratchpad 에 두고 커밋하지 않았다. 숫자는 README 검증 표에 남겼다.

### Follow-up — 2026-10-15

**Request**: 다음 세 가지를 하고 TASK6 Follow-up 으로 기록한다.
- `~/Project/arch-bench-ws` 를 비운다(`experiment/runs/` 는 둔다).
- `run_matrix.py` 에 잔여물 사전 검사를 추가한다(agent=claude 일 때 `workspace-index.jsonl` 에 없는 디렉터리가 있으면 종료 5).
- `run_session.py` 의 실패 상태 이름을 `aborted` 에서 `failed` 로 통일한다.

**Changes**
- **워크스페이스 루트 정리**
  - `/Users/kyeom/Project/arch-bench-ws` 아래 49개 항목(252MB)을 지웠다. 모두 TASK5·TASK6 검증에서 생긴 것이다.
    - TASK5 형식 이름 `_smoke-*` 5개
    - 토큰 디렉터리 42개
    - `_failed` 디렉터리 1개
    - `smoke-live.*` 1개
  - 아래 실패 상태 검증에서 생긴 1개도 다시 지웠다. 지금은 비어 있다.
- **잔여물 사전 검사 (`run_matrix.py`, `precheck_ws_leftovers`)**
  - `agent: claude` 일 때 `--ws-root` 직속 디렉터리마다 이름(`_failed-` 앞부분)이 `$OUT_ROOT/workspace-index.jsonl` 의 토큰인지 본다. 하나라도 아니면 `PrecheckError` 로 종료 5 를 낸다.
  - 인덱스 줄이 깨져 있어도 종료 5 다.
  - 파일은 보지 않는다(`.DS_Store`).
  - 기준을 "이 `--out` 의 인덱스"로 잡았다. 같은 매트릭스를 `--resume` 하면 통과하고, 다른 실행이나 옛 형식의 잔여물은 거부된다.
  - `agent: fake` 는 검사하지 않는다. fake 검증이 실험 루트를 쓰는 일을 막지는 않으므로, fake 를 돌린 뒤에는 루트를 비워야 claude 실행이 통과한다.
- **세션 상태 이름 (`run_session.py`)**
  - `session["status"]` 는 이제 `running`·`completed`·`failed` 셋뿐이다(이전 결정: 실패 시 `aborted` → 변경: `failed`, 사유: 매트릭스의 `failed` 와 이름이 둘이면 분석 코드에서 실수가 난다).
  - `run_matrix.py` 의 주석과 README 의 세션 상태 표를 맞췄다.
  - TASK6 본문 Notes 의 "`completed`·`aborted` 만 쓴다"는 이 변경 전 서술이다.
- **문서**
  - README 의 매트릭스 "시작 전 검사" 행에 잔여물 검사를 적었다.
  - 러너 표에 "세션 `status`" 행을 추가했다.
  - 알려진 한계 6 은 해결로 표시했다.

**검증**
- **잔여물 검사 함수:** 빈 루트는 통과했다.
  - 디렉터리 2개, 인덱스 없음 → 거부
  - 두 토큰을 인덱스에 넣음(`bbbbbbbbbbbb_failed-...` 포함) → 통과
  - 옛 형식 `_smoke-L2-haiku-r1` 추가 → 거부
- **CLI:** `agent: claude` 설정으로 잔여물이 있는 루트를 주면 세션을 하나도 시작하지 않고 종료 5 로 끝났다. 비용은 0 이다.
- **실패 상태:** PATH 앞에 항상 실패하는 `jq` 를 둬서 fake 에이전트를 실패시켰다. `session.json` 이 `{"status": "failed", "exit_code": 1}` 로 기록됐다.

**Notes**
- `run_session.py` 를 단독으로 실행할 때는 잔여물 검사가 없다. 본 실험은 `run_matrix.py` 로만 돌린다.
