# TASK5 — TASK4 열린 결정 3건 반영(채점기 주석 중립화·단일 Initial commit·Bash 훅 변경 감지) + 세션 러너

- **Date**: 2026-10-15
- **Status**: completed
- **Related TASKs**: TASK4 (README "알려진 한계" 1~3, 러너 전제, 워크스페이스 루트 Follow-up), TASK3 (채점기 동결, PRIMARY_RULES)
- **Commits**: 이 문서와 같은 커밋

## Request
작업 지시 5. 1부: TASK4 가 남긴 열린 결정 3건을 지시된 결정대로 반영 — (1) 채점기 3개 파일의 주석·Javadoc 에서 실험 용어 제거(동결의 유일한 예외, 측정값 바이트 동일 증명), (2) `seed` + `harness <level>` 두 커밋을 단일 `"Initial commit"` 으로, (3) 훅 매처에 `Bash` 추가 + `src/main/java` 변경 감지. 2부: 세션 1개를 돌리는 `experiment/runner/run_session.py`(+ `fake_agent.sh`, `tasks/_smoke/` 3턴)와 그 검증(fake L0·L2·L3, 실호출 L2 3턴·L3 1턴, 거부 종료 코드 4·5). 실호출 합계 $0.5 이하.

## Context
- 시작 상태 main 718f365, 작업 트리 clean. TASK4 Notes 의 권고 (a)(동결을 한 번 풀어 주석만 수정)가 결정 1 로 채택됐다.
- 러너용 격리 설정 디렉터리 `~/.claude-arch-bench`(`CLAUDE_CONFIG_DIR`)를 새로 만들었다. 처음엔 "Not logged in" 이라 사용자가 별도 터미널에서 `/login` 했다. 로그인 후 그 안에 생긴 `settings.json` 은 `{"theme":"dark"}` 뿐(훅 없음).
- Claude Code 2.1.300 실측(소액 probe 호출, $0.003): 서브에이전트 도구 이름은 `Agent`(구 `Task`), 기본이 백그라운드 실행이라 결과가 `tool_result` 가 아니라 `system/task_notification.summary` 로 오고 `result` 이벤트가 한 프로세스에 여러 개 나온다. 지시서의 "Task 호출·리뷰어 응답 텍스트" 파싱을 이 형태에 맞췄다.

## Changes
### 1부 결정 1 — 채점기 주석 중립화
- `HexagonalRules.java`·`ArchitectureTest.java`·`ViolationExtractor.java` 의 Javadoc/주석만 수정(코드 줄 diff 0, `git diff -U0` 에서 주석 외 `+/-` 줄 0건 확인). 지운 표현: `violation-fixtures`, `양성/음성 검증`, `실험 결과 JSON의 키`, `축소 픽스처`, `2차 변경`, `실험 설계자 결정`, `REPORT.md 단계 4`, `패턴 A`, `파이프라인`, `주 지표`, `v4의 LoanPolicy`, `측정 JSON`. 남은 서술은 "이 프로젝트의 아키텍처 규칙 R1~R7을 검사한다", 규칙별 기술적 이유(@JsonProperty 대상 등) 수준.
- 검증: 변경 전·후 `./gradlew clean archTest` 의 `lending/build/arch/lending.json` + 픽스처 8개의 `[json] vN {...}` 출력(테스트 XML system-out)을 저장해 `diff -r before after | wc -l` → **0줄**. 픽스처 테스트 `tests="8" failures="0"` 전후 동일. `./gradlew :lending:test` 통과.
- 지시 grep `grep -rn "fixtures\|REPORT\|실험\|TASK\|설계자" lending/src/test/java/com/example/lending/architecture/` → **3건, 모두 코드 식별자** `ArchitectureTest.REPORT`(37·107·109행, `Path.of("build","arch","lending.json")` 상수). 주석 아님. "주석 외 변경 금지"가 우선이라 이름을 바꾸지 않았다(→ Notes). `REPORT\.md`·음성·양성·픽스처·파이프라인 등 확장 grep 은 0건.
- 시드 `.gitignore` 에 `/build/` 가 있어 L2·L3 의 훅·에이전트가 만드는 `build/arch/lending.json` 은 커밋되지 않음을 확인(템플릿 `experiment/seed-template/gitignore`).

### 1부 결정 2 — 단일 "Initial commit"
- `experiment/scripts/inject-harness.sh`: 주입 전에 복사본의 `.git` 을 지우고 `git init -q -b main` → 주입(L2 의 `.git/info/exclude` 도 새 저장소에) → `git add -A` → `commit -m "Initial commit"`(작성자 `seed`, 시각 2026-01-01T00:00:00Z 고정 유지).
- `materialize-seed.sh` 의 `seed` 커밋(4b39a61)은 시드 저장소 자체에 그대로 둔다 — 러너가 `seed_commit` 으로 기록하는 재현성 기준. 워크스페이스에는 보이지 않는다.
- smoke 3단계에 `git log --format=%s` 가 네 수준 모두 `Initial commit` 한 줄임을 검사하는 행 추가 → PASS.
- README 러너 전제 4번·inject 계약 갱신(tamper 기준 = "Initial commit").

### 1부 결정 3 — 훅 매처 Bash + 변경 감지
- `experiment/harness/L2/settings.json`: matcher `Edit|Write|MultiEdit` → `Edit|Write|MultiEdit|Bash`.
- `experiment/harness/L2/hooks/arch-check.sh`: `tool_input.file_path` 기반 경로 판정을 없애고 **내용 지문** 비교로 바꿈. 지문 = `src/main/java` 아래 (추적 + 무시되지 않은 새 파일)의 `git hash-object --stdin-paths` 결과와 경로를 정렬해 `git hash-object --stdin`. Gradle 을 돌린 뒤 `.harness/last-main-hash` 에 저장, 같으면 `skip`(exit 0). 파일이 없으면 HEAD 트리(`git ls-tree -r HEAD`)의 지문을 검사된 상태로 본다. 로그에 `tool` 필드 추가. exit 2 + stderr, 턴당 3회, 컴파일 실패 미포함은 그대로.
- smoke 6단계: 회차마다 주석 한 줄을 붙여 새 내용을 만들고, "같은 내용 재호출 → skip, 횟수 미증가" 행 추가. 7단계: Edit(src/test·build.gradle.kts) skip, Bash 변경 없음 skip(<1초), Bash + src/main 위반 변경 → 발화 exit 2, 같은 변경 두 번째 Bash → skip, 커밋만 한 Bash → skip. 전부 PASS.
- README 훅 계약에 "한계: 훅 설정 수정·`.claude/` 삭제는 러너의 Config_tamper 로 집계" 명시.

### 2부 — 세션 러너
- `experiment/runner/run_session.py`(표준 라이브러리만), `fake_agent.sh`, 빈 `requirements.txt`, `experiment/tasks/_smoke/turn-0{1,2,3}.md`(회원 등록 API / 대출 반납 / 연체료, 첫 줄 `<!-- smoke only, not an experiment task -->`, 금지어 grep 0건). 계약 전문·예시는 `experiment/README.md` "세션 러너".
- `.gitignore`: `/experiment/runs/`(러너 결과), `/experiment/runner/__pycache__/`.

## Verification
- **결정 1**: 변경 전·후 `lending.json` + 픽스처 8개 `[json]` 출력 `diff -r` → **0줄**. 지시 grep 은 코드 식별자 `REPORT` 3건(주석 0건).
- **결정 2·3 / smoke**: `experiment/scripts/smoke.sh` exit 0, 1~7단계 36행 전부 PASS(3단계 git log 네 수준 `Initial commit` 1개, 6단계 같은 내용 재호출 skip, 7단계 Bash 변경 없음 skip 64~73ms·변경 있음 발화 exit 2 1714ms·두 번째 호출 skip·커밋만 한 호출 skip). 표는 `experiment/README.md`.
- **러너 검증 1 (fake L0·L2·L3)**: 3세션 exit 0. 턴 2: R1=1, `agent_committed=false`, 러너 커밋 `turn 02 (runner)`. 턴 3: `test_tamper` L2·L3 true / L0 null, 원본 규칙 채점으로 R1=1 유지.
- **러너 검증 2 (haiku L2 3턴)**: exit 0, init `session_id` 3턴 동일(`agent_session_id_consistent=true`), 토큰·도구 수·훅 로그 기록, 위반 0, 기능 테스트 5→8→13 통과, $0.0401.
- **러너 검증 3 (haiku L3 1턴)**: `architecture-reviewer` 1회(`Agent` 도구, 백그라운드), `reviewer_verdicts=["APPROVE"]`, $0.0189.
- **러너 검증 4**: 재실행 → 4, ws-root 가 arch-bench 아래 → 5, `CLAUDE_CONFIG_DIR` 미설정 → 5, `~/.claude` 지정 → 5, hooks 있는 settings.json → 5.
- 실호출 합계 약 **$0.13** (probe $0.005 + 1차 L2·L3 $0.062 + 최종 L2·L3 $0.059). 상한 $0.5 이내.

## Notes
- **판단: 변경 감지 방식(지시서와 다름)**. 지시서는 "`git status --porcelain -- src/main/java` + 해당 파일들의 hash-object" 지문이었다. 이 방식은 커밋하면 porcelain 이 비어 지문이 바뀌므로 (1) 에이전트의 `git commit` Bash 호출, (2) 러너가 턴 끝에 커밋한 뒤 다음 턴의 첫 Bash 호출에서 **내용 변화 없이** Gradle 이 다시 돌고 위반이 있으면 exit 2 를 또 보낸다(턴당 3회 소모). 그래서 `src/main/java` 전체 파일의 `hash-object` 지문(커밋 여부 무관)으로 바꿨고, 기준 상태가 없을 때는 HEAD 트리 지문을 쓴다. "변경이 있을 때만 Gradle" 이라는 결정 내용은 같다.
- **판단: `ArchitectureTest.REPORT` 상수명**: 완료 기준 grep 0건과 "주석 외 변경 금지" 가 충돌. 보수적으로 코드를 두었다. 바꾸려면 상수명만 바꾸는 별도 결정이 필요하다(측정값 영향 없음).
- **판단: 훅 로그 이동 시점**: 지시서 1단계는 턴 시작에 `hook-log.jsonl` 을 `turn-NN/` 으로 옮기라고 했지만, 그러면 직전 턴의 로그가 이번 턴 디렉터리에 들어간다(한 칸 밀림). 에이전트 실행 **직후** 이번 턴 로그를 `turn-NN/hook-log.jsonl` 로 옮기고, 턴 시작에 남아 있는 것이 있으면 `hook-log.pre-turn.jsonl` 로 따로 둔다.
- **판단: `--max-turns`** = 실행할 턴 파일 수(검증 3의 "1턴" 용). claude 의 `--max-turns` 는 `--agent-max-turns` 로 분리(기본 미지정).
- **판단: 프롬프트에서 맨 앞 HTML 주석 줄 제거**: `<!-- smoke only ... -->` 를 에이전트에게 보내지 않는다.
- **판단: 에이전트가 커밋한 뒤 남긴 변경도 러너가 커밋**(`runner_committed`, `dirty_after_agent_commit`) → 턴 끝 상태 = HEAD, tamper diff(`base..HEAD`)가 미커밋 변경을 놓치지 않는다.
- **판단: config_tamper 는 전 수준 계산**. L0·L1 에도 `CLAUDE.md`·`build.gradle.kts` 가 있다. `build.gradle.kts` 는 정상적인 의존성 추가로도 바뀌므로 분석 시 `config_tamper_files` 로 거를 것.
- **버그 수정(러너 개발 중)**: 1차 실호출에서 `total_cost_usd` 가 resume 세션 누적값임을 발견(턴별 usage 와 비용이 어긋남) → 턴 비용 = 누적값 차분. 1차 결과는 버리고 최종 러너로 다시 돌렸다.
- Claude Code 2.1.300: 서브에이전트 도구 = `Agent`, 백그라운드 기본, `result` 이벤트 복수. 러너는 `Task`·`Agent`, `tool_result`·`task_notification` 모두 처리.
- 남은 숙제: 매트릭스 실행기(300세션), 같은 `ARCH_WS_ROOT` 아래 다른 세션 워크스페이스가 `ls ..` 로 보이는 문제, 러너 커밋 메시지 `turn NN (runner)` 가 다음 턴 git log 에 보이는 점(수준 공통).
- `/Users/kyeom/Project/arch-bench-ws/_smoke-*` 는 이번 검증 세션의 워크스페이스(사후 분석용으로 남김), 결과는 `experiment/runs/`(git 미추적). `smoke-live.*` 는 TASK4 잔여물.
