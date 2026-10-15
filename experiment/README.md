# experiment — 시드 저장소, 하네스 수준 주입, 오버레이 채점기, 세션 러너

세션 러너와 러너가 쓰는 부품 세 개, 스모크 검증을 담는다. 채점기 본체(`lending/src/test/.../architecture/*`, R1~R7, `lending.json` 스키마)는 동결 상태다. 이 디렉터리는 채점기를 **복사해서 쓰기만** 하고 수정하지 않는다. (TASK5 에서 동결을 한 번 풀어 채점기 **주석**의 실험 용어만 지웠다. 측정값은 바이트 동일.)

```
experiment/
  seed/                      # 생성물 (materialize-seed.sh). git 추적 안 함
  seed-template/             # 시드의 build.gradle.kts·settings.gradle.kts·.gitignore·.gitattributes 원본
  harness/
    common/CLAUDE.md         # 모든 수준 공통 (아키텍처 언급 없음)
    common/arch.gradle.kts   # ArchUnit 의존성 + archTest 태스크. L2·L3 주입과 grade.sh 가 같은 파일을 쓴다
    L1/CLAUDE.architecture.md
    L2/settings.json
    L2/hooks/arch-check.sh
    L3/agents/architecture-reviewer.md
    L3/CLAUDE.reviewer.md
  scripts/
    materialize-seed.sh
    inject-harness.sh
    grade.sh
    overlay-arch.sh          # inject(L2·L3)와 grade 가 source 하는 오버레이 함수 (같은 방법을 코드 한 곳으로 보장)
    smoke.sh
  runner/
    run_session.py           # 세션 1개 실행 (아래 "세션 러너")
    fake_agent.sh            # --agent fake 용 결정적 가짜 에이전트 (러너 경로 검증용)
    requirements.txt         # 비어 있음 (표준 라이브러리만)
  tasks/
    _smoke/turn-0{1,2,3}.md  # 러너 검증용 3턴. 실험 과제가 아니다
  runs/                      # 러너 결과 (git 추적 안 함). 실험 매트릭스에서는 --out 으로 따로 둔다
```

> **하네스 텍스트는 동결 대상이다.** `harness/` 아래 파일(CLAUDE.md 조각, 리뷰어 프롬프트, 훅, settings.json, arch.gradle.kts)은 사전 등록 전까지 바꾸지 않는다. 바꿔야 하면 **반드시 `docs/tasks/` 에 TASK 로 기록**하고(이전 문구 → 새 문구, 사유), 이미 돌린 세션과 섞어 분석하지 않는다.

## 확인한 문서와 버전

- Claude Code: `2.1.300 (Claude Code)` (`claude --version`, 2026-10-15)
- 훅: <https://code.claude.com/docs/en/hooks>
  - `PostToolUse` 의 exit 2 → "Shows stderr to Claude; the tool already ran" (차단은 못 하고 stderr 를 Claude 에게 보여준다). exit 0 이면 stderr 는 Claude 에게 가지 않는다. → 지시대로 **exit 2** 를 쓴다.
  - stdin 의 `tool_input.file_path` 는 파일 도구(Write/Edit)에서 **항상 절대 경로**다. 훅은 절대 경로와(직접 실행용) 상대 경로를 모두 받는다.
  - `command` 훅 기본 timeout 600초. 여기서는 `"timeout": 300` 으로 둔다.
  - `$CLAUDE_PROJECT_DIR` = 세션을 시작한 프로젝트 루트.
  - matcher 는 도구 이름 정규식이다. 현재 문서의 도구 목록에는 `MultiEdit` 가 없다(편집 도구는 `Edit`, `Write`). `MultiEdit` 는 존재하지 않으면 매칭되지 않을 뿐 해가 없다. TASK5 에서 `Bash` 를 추가했다(`Edit|Write|MultiEdit|Bash`).
  - 서브에이전트 호출 도구의 이름은 2.1.300 에서 **`Agent`** 다(구 `Task`). 실측 스트림에서 `{"name":"Agent","input":{"subagent_type":...}}` 로 나오고, 기본이 백그라운드 실행이라 결과는 `tool_result` 가 아니라 `{"type":"system","subtype":"task_notification","tool_use_id":...,"summary":...}` 로 온다. 이때 `result` 이벤트가 한 프로세스에 여러 개 나온다(서브에이전트 완료 후 메인이 이어서 응답).
- 서브에이전트: <https://code.claude.com/docs/en/sub-agents>
  - `.claude/agents/*.md`, YAML frontmatter(`name`, `description` 필수, `tools` 는 쉼표 구분 문자열, `model` 은 `inherit` 가능) + 본문이 시스템 프롬프트.
  - 리뷰어는 `model: inherit` 로 둔다. 세션의 모델 등급이 리뷰어에도 그대로 적용되어 "모델 3등급" 요인이 리뷰어에서 섞이지 않는다.

## 계약

### `scripts/materialize-seed.sh`

| 항목 | 내용 |
| --- | --- |
| 입력 | 없음 (arch-bench 루트의 `lending/`, `gradle/wrapper`, `gradlew*`, `experiment/seed-template/`) |
| 출력 | `experiment/seed/` (기존 것은 지우고 새로 만든다). 단일 모듈 Gradle 프로젝트, `rootProject.name = "lending"`, 패키지 `com.example.lending` |
| 포함 | `src/main` 전체, `src/test` 중 `architecture/` 를 뺀 기능 테스트(`LendingIntegrationTest`), `build.gradle.kts`(ArchUnit·archTest 없음), `settings.gradle.kts`, wrapper, `.gitignore`, `.gitattributes` |
| git | `git init -b main` + 커밋 `seed` 1개. 작성자·시각을 고정해 **매번 같은 해시**(`4b39a61`)가 나온다 |
| 검사 | `./gradlew build` 통과 → `build/`·`.gradle/` 제거 → `grep -r "fixtures\|REPORT\|TASK" seed --exclude-dir=.git` 0건, `CLAUDE.md`·`REPORT.md`·`docs`·`violation-fixtures`·`.claude` 없음 |
| 종료 코드 | 0 성공, 그 외 실패(`set -e`) |

- 시드 `.gitignore` 는 디렉터리 패턴을 모두 루트 기준(`/build/`, `/.gradle/`, `/out/`, `/bin/` …)으로 쓴다. 예외는 `.DS_Store` 하나다(파일 이름이라 소스 디렉터리와 겹칠 수 없고, 하위 디렉터리에서도 무시되어야 한다).

### `scripts/inject-harness.sh <L0|L1|L2|L3> <workspace>`

| 항목 | 내용 |
| --- | --- |
| 전제 | `<workspace>` 는 `cp -r experiment/seed <workspace>` 로 만든 복사본(`.git` 포함). 시드 자체 경로를 주면 거부한다 |
| L0 | `common/CLAUDE.md` → `CLAUDE.md` |
| L1 | L0 + `L1/CLAUDE.architecture.md` 를 `CLAUDE.md` 끝에 그대로 append |
| L2 | L1 + arch-bench **원본** `HexagonalRules.java`·`ViolationExtractor.java`·`ArchitectureTest.java` 를 같은 경로로 복사 + `common/arch.gradle.kts` → `gradle/arch.gradle.kts` + `build.gradle.kts` 끝에 `apply(from = "gradle/arch.gradle.kts")` 한 줄 + `.claude/settings.json` + `.claude/hooks/arch-check.sh` + `.git/info/exclude` 에 `/.harness/` |
| L3 | L2 + `.claude/agents/architecture-reviewer.md` + `L3/CLAUDE.reviewer.md` 를 `CLAUDE.md` 끝에 append |
| git | 시드에서 복사된 `.git`(커밋 `seed`)을 **지우고** `git init -b main` → 주입 → `git add -A && git commit -m "Initial commit"` (작성자·시각 고정). 네 수준 모두 `git log` 에 커밋 1개, 메시지 동일. **러너의 tamper 검출 기준 커밋은 이 "Initial commit" 이다** (TASK5 결정 2) |
| 사전 검사 | `common/CLAUDE.md` 에 아키텍처 관련 단어가 있으면 주입하지 않고 실패(1) |
| 종료 코드 | 0 성공, 2 미지원 수준·인자 오류(시드 워크스페이스가 아님 포함), 1 복사·커밋 실패 |

### `scripts/grade.sh <workspace> <out.json>`

| 항목 | 내용 |
| --- | --- |
| 동작 | `<workspace>` 를 `mktemp -d` 에 복사(원본 불변) → `build/`·`.gradle/`·`.harness/`·**`src/test/` 전체** 삭제 → `overlay_arch`(L2 주입과 같은 함수)로 원본 ArchUnit 테스트 3개 + `arch.gradle.kts` + apply 줄을 덮어씀 → `compileJava` → `compileTestJava` → `archTest -q` → `build/arch/lending.json` 을 `<out.json>` 으로 |
| 변조 대응 | 워크스페이스에 ArchUnit 테스트·`gradle/arch.gradle.kts` 가 있어도(L2·L3, 에이전트가 고쳤어도) **무조건 원본으로 덮어쓴다**. 변조 여부는 러너가 `git diff <harness 커밋>` 으로 따로 센다 |
| 종료 0 | JSON 생성 + `tests_executed == 7` + `by_rule` 키 = R1~R7 + `primary_rules == ["R1","R2","R3","R6","R7"]`. stdout 에 `{"violating_classes":n,"tests_executed":7}` |
| 종료 3 | 컴파일 실패. `<out.json>` = `{"error":"compile_failed","stage":"main"}` (에이전트가 main 을 깨뜨림) 또는 `"stage":"test"` (오버레이 테스트가 컴파일되지 않음 — 예: 에이전트가 Jackson 이 딸린 web 의존성을 지움) |
| 종료 1 | 그 외 측정 실패(JSON 없음, 7개 미만 실행, 키 누락, `primary_rules` 변경). 검사에 실패한 JSON 이 있으면 `<out.json>` 에 그대로 남긴다 |
| 종료 2 | 인자 오류 |

- `src/test/` 를 지우는 이유: 측정 대상은 main 코드뿐이다(`ArchitectureTest` 는 `DoNotIncludeTests`). 에이전트 테스트의 컴파일 오류가 구조 측정을 막지 않게 한다. 실측: ws-L1 에 컴파일 안 되는 테스트를 넣어도 exit 0, main 에 문법 오류를 넣으면 exit 3 + `{"error":"compile_failed","stage":"main"}`.
- Gradle 데몬과 `~/.gradle` 캐시는 공유한다. 한 번 채점에 약 4~5초(데몬 워밍 후).

### 훅 `harness/L2/hooks/arch-check.sh` (워크스페이스의 `.claude/hooks/arch-check.sh`)

| 항목 | 내용 |
| --- | --- |
| 발화 | PostToolUse, matcher `Edit\|Write\|MultiEdit\|Bash`, 명령 `bash "$CLAUDE_PROJECT_DIR/.claude/hooks/arch-check.sh"` |
| 대상 | 도구 종류와 무관하게 **지난 검사 이후 `src/main/java` 아래 내용이 바뀌었을 때만** Gradle 을 돌린다. 바뀌지 않았으면 즉시 exit 0 (`action: skip`, 실측 40~75ms) |
| 변경 감지 | 내용 지문 = `src/main/java` 의 (추적 + 무시되지 않은 새 파일) 각각의 `git hash-object` 와 경로를 정렬해 `git hash-object --stdin` 으로 다시 해시. Gradle 을 돌린 뒤(통과·실패·컴파일 실패 모두) `.harness/last-main-hash` 에 저장. 파일이 없으면(세션 시작) HEAD 트리(`git ls-tree`)의 지문을 검사된 상태로 본다. 커밋 여부와 무관하게 **내용**만 보므로 `git commit` 만 하는 Bash 호출은 발화하지 않는다 |
| 통과 | `./gradlew archTest -q` 성공 → exit 0 (`pass`) |
| 실패 | stderr 에 실패한 ArchUnit 테스트·규칙 이름·위반 상세(스택 트레이스·Gradle 안내문 제거, 최대 150줄) → **exit 2** (`fail`), `.harness/retry-count` +1 |
| 상한 | `retry-count` ≥ 3 이면 Gradle 을 돌리지 않고 stderr 에 `architecture check still failing; limit reached` 한 줄, exit 0 (`limit`). 즉 한 턴에 최대 3번 막는다 |
| 컴파일 실패 | `:compileJava`/`:compileTestJava` 실패는 구조 검사 결과가 아니므로 막지 않고 횟수도 세지 않는다(exit 0, `compile_failed`). 여러 파일을 고치는 도중의 중간 상태가 재시도 횟수를 소모하지 않게 하기 위함 |
| 기록 | `.harness/hook-log.jsonl` 에 호출마다 `{"turn_file","tool","elapsed_ms","exit_code","action"}` 한 줄 append. `turn_file` 은 받은 `tool_input.file_path` 그대로(Claude Code 에선 절대 경로, Bash 이벤트는 빈 문자열), `tool` 은 `tool_name`. `action` ∈ `skip`(변경 없음)·`pass`·`fail`·`limit`·`compile_failed` |
| 의존 | `bash`, `jq`, `git`, `paste`, 그리고 bash 5 가 아니면 ms 측정용 `perl`(macOS·Linux 기본 탑재) |
| 한계 | 훅 설정 자체를 고치거나(`.claude/settings.json`, `arch-check.sh`) `.claude/` 를 지우면 훅이 무력화된다. 막지 않고 러너가 **Config_tamper** 로 집계한다(`config_tamper_files` 에 `.claude/...` 가 나온다) |

### 러너가 지켜야 할 전제

1. **턴 시작마다 `<workspace>/.harness/retry-count` 를 지운다.** 훅의 "턴당 3회"는 이 삭제로만 성립한다. `hook-log.jsonl` 은 지우지 않고 턴 경계를 러너가 따로 기록하는 것을 권한다(또는 턴마다 옮겨 둔다).
2. **워크스페이스를 arch-bench 저장소 밖에 둔다.** 이 머신의 워크스페이스 루트는 `/Users/kyeom/Project/arch-bench-ws` 다(2026-10-15 결정, 환경 변수 `ARCH_WS_ROOT` 로 넘긴다). `/Users/kyeom/Project`·`/Users/kyeom`·`/Users`·`/` 에 `CLAUDE.md`·`CLAUDE.local.md`·`.claude/` 가 없음을 확인했다(`~/.claude` 는 사용자 설정 위치라 별도 — 3번 참조). Claude Code 는 상위 디렉터리의 `CLAUDE.md`·`.claude/` 를 거슬러 올라가며 읽으므로, `experiment/ws-*` 안에서 에이전트를 돌리면 arch-bench 의 CLAUDE.md(실험 설계 전체)가 에이전트에게 보인다. smoke 1~7단계는 에이전트를 띄우지 않아서 `experiment/ws-*` 를 써도 되지만, 8단계는 `$ARCH_WS_ROOT/smoke-live.XXXXXX/ws-L2` 에서 돌리며(미지정 시 `$TMPDIR`), 루트부터 `/` 까지 위 파일이 하나라도 있으면 실행을 거부한다. 러너도 같은 검사를 하기를 권한다.
3. 사용자 수준 설정(`~/.claude/CLAUDE.md`, 사용자 훅·플러그인·스킬)이 섞이지 않게 한다. 이 머신의 `~/.claude/settings.json` 에는 PostToolUse 를 포함한 사용자 훅 13종과 플러그인이 있고, `~/.claude/skills/` 에 사용자 스킬 8개가 있다. 8단계는 `--setting-sources project` 로 사용자 settings 를 뺐다. 실제 러너는 전용 `CLAUDE_CONFIG_DIR` 이나 깨끗한 계정 홈을 쓰는 편이 안전하다(`--bare` 는 훅을 끄므로 L2·L3 에 쓸 수 없다).
4. tamper 기준은 `inject-harness.sh` 가 만든 유일한 커밋 **"Initial commit"** 이다(TASK5 결정 2, 이전에는 `harness <level>`). 러너는 이 커밋 해시를 `base_commit` 으로 기록하고 Test_tamper = `src/test/java/com/example/lending/architecture/` diff, Config_tamper = `.claude/`·`CLAUDE.md`·`build.gradle.kts`·`gradle/arch.gradle.kts` diff 로 나눠 센다. `.harness/` 는 `.git/info/exclude` 로 빠져 diff 에 나오지 않는다.
5. 훅 상태 디렉터리 `.harness/` 와 훅의 Gradle 실행으로 생기는 `build/` 는 grade.sh 가 복사본에서 지우므로 채점에 영향이 없다.

### 리뷰어 출력 파싱

`architecture-reviewer` 의 응답 첫 줄은 정확히 `VERDICT: APPROVE` 또는 `VERDICT: REJECT` 다. 러너(`parse_stream`)는 메인 에이전트(`parent_tool_use_id == null`)의 `tool_use` 중 이름이 `Agent`(또는 구 `Task`)이고 `input.subagent_type == "architecture-reviewer"` 인 것을 리뷰어 호출로 센다. 판정 텍스트는 그 `tool_use.id` 에 대응하는 (a) `tool_result`(동기 실행) 또는 (b) `system/task_notification` 의 `summary`(백그라운드 실행, 2.1.300 기본) 에서 읽는다. 첫 줄이 `VERDICT: X` 면 X, 첫 줄은 아니지만 본문에 있으면 X 로 기록하고 `reviewer_verdict_not_first_line` 을 올린다. 둘 다 없으면 `UNPARSED`.

## 세션 러너 `runner/run_session.py`

```
CLAUDE_CONFIG_DIR=~/.claude-arch-bench \
python3 experiment/runner/run_session.py --level L2 --model haiku --domain _smoke --rep 1 \
    --ws-root $ARCH_WS_ROOT --out $OUT_ROOT [--agent claude|fake] [--max-turns N] \
    [--agent-max-turns N] [--skip-tests]
```

Python 3.11+ 표준 라이브러리만 쓴다(`requirements.txt` 는 비어 있다). 세션 1개만 다룬다. 매트릭스 실행기는 다음 TASK.

| 항목 | 내용 |
| --- | --- |
| 세션 ID | `<domain>-<level>-<model>-r<rep>` (예: `_smoke-L2-haiku-r1`). 워크스페이스 `$ARCH_WS_ROOT/<id>/`, 결과 `$OUT_ROOT/<id>/` |
| 덮어쓰기 | 둘 중 하나라도 이미 있으면 거부, **종료 4** |
| 시작 전 검사 (**종료 5**) | ① `--ws-root` 부터 `/` 까지 `CLAUDE.md`·`CLAUDE.local.md`·`.claude` 없음(`~/.claude` 제외, smoke 8단계와 같은 검사) ② `CLAUDE_CONFIG_DIR` 설정됨, 디렉터리 존재, `~/.claude` 자신이 아님 ③ 그 안 `settings.json` 이 없거나 `hooks` 가 비어 있음 ④ 그 안에 `CLAUDE.md` 없음, `agents/`·`skills/`·`commands/` 비어 있음 ⑤ (`--agent claude`) `claude` 가 PATH 에 있음 |
| 준비 | `experiment/seed` 가 없으면 `materialize-seed.sh` → `cp -R seed <ws>` → `inject-harness.sh <level> <ws>` → `base_commit` = "Initial commit" 해시 |
| 턴 순서 | `tasks/<domain>/turn-*.md` 를 이름순. `--max-turns N` 이면 앞 N개만 |
| 프롬프트 | 턴 파일 본문. **맨 앞의 HTML 주석 줄**(`<!-- smoke only ... -->` 같은 메타데이터)은 빼고 보낸다 |
| 에이전트 | `claude -p <prompt> --model <m> --output-format stream-json --verbose --permission-mode bypassPermissions --setting-sources project [--resume <id>]`, `cwd=<ws>`, stdin 없음. 첫 턴은 새 세션, 이후 턴은 **직전 턴 init 이벤트의 `session_id` 로 `--resume`**. 타임아웃 20분(프로세스 그룹 SIGTERM → 30초 뒤 SIGKILL, `agent_status: timeout`, 세션은 계속) |
| 환경 | 부모 환경에서 `CLAUDECODE`·`CLAUDE_CODE_*`·`CLAUDE_PID`·`CLAUDE_EFFORT`·`CLAUDE_PROJECT_DIR` 를 빼고(러너를 Claude Code 안에서 띄워도 부모 세션 정보가 새지 않게) `DISABLE_AUTOUPDATER=1` 을 더한다 |
| 턴 단계 | ① `.harness/retry-count` 삭제(남은 `hook-log.jsonl` 이 있으면 `hook-log.pre-turn.jsonl` 로 치움) ② 에이전트 실행 → `stream.jsonl`·`stderr.txt`, 끝나면 `.harness/hook-log.jsonl` 을 `turn-NN/hook-log.jsonl` 로 **이동**(없으면 빈 파일) ③ 스트림 파싱 ④ 커밋 확인 ⑤ tamper ⑥ `grade.sh <ws> turn-NN/arch.json` ⑦ 복사본에서 `./gradlew test -q` ⑧ `turn-NN/turn.json` |
| 커밋 확인 | 턴 전후 `HEAD` 가 같으면 `agent_committed: false`. 작업 트리가 더러우면(에이전트가 커밋 안 했거나, 커밋 뒤에 남긴 변경) 러너가 `git add -A && git commit -m "turn NN (runner)"` (`runner_committed: true`). 둘 다 아니면 `no_change: true`. 그래서 턴 끝 상태 = `HEAD` 다 |
| tamper | `git diff --name-only <base_commit> HEAD -- src/test/java/com/example/lending/architecture/` 가 비어 있지 않으면 `test_tamper: true`(L0·L1 은 `null`, 파일 목록만 기록). `.claude/ CLAUDE.md build.gradle.kts gradle/arch.gradle.kts` 중 변경 파일이 있으면 `config_tamper: true` + `config_tamper_files`(모든 수준에서 계산. **`build.gradle.kts` 는 정상적인 의존성 추가로도 바뀌므로** 분석 시 파일 목록으로 거른다) |
| 채점 | `grade_exit`(0/1/3), `violating_classes`, `r1_count`~`r7_count`. 3(컴파일 실패)이어도 다음 턴 진행 |
| 기능 테스트 | 워크스페이스 복사본(`build`·`.gradle`·`.harness` 제거)에서 `./gradlew test -q`, `build/test-results/test/*.xml` 로 건수. `tests_status` ∈ `passed`·`failed`·`compile_failed`·`timeout`. 기본은 턴마다, `--skip-tests` 면 마지막 턴만 |
| 중단 | 에이전트 `agent_status: error`(0 아닌 종료, `result` 이벤트 없음, `is_error: true`) 또는 다음 턴에 쓸 session_id 가 없으면 그 턴까지 기록하고 **종료 1**. Ctrl-C 도 1 |
| 결과 | `$OUT/session.json`(평탄한 키 + `turns` 요약 배열), `$OUT/turn-NN/{turn.json,stream.jsonl,stderr.txt,hook-log.jsonl,arch.json,grade.log,test.log}`. 워크스페이스는 지우지 않는다 |
| 종료 코드 | 0 전 턴 완료, 1 중단, 2 인자 오류(argparse·턴 파일 없음), 4 덮어쓰기 거부, 5 시작 전 검사 실패 |

### 스트림 파싱 (`turn.json`)

- 토큰: `result` 이벤트들의 `usage` 를 **합산**(`input_tokens`·`output_tokens`·`cache_creation_input_tokens`·`cache_read_input_tokens`). 백그라운드 서브에이전트가 있으면 한 프로세스에 `result` 가 여러 개다(`result_events`). `cost_usd` 는 `total_cost_usd` 의 최댓값(한 프로세스 안에서 누적값).
- 도구: 메인 에이전트(`parent_tool_use_id == null`)의 `tool_use` 를 이름별로 센다. `tool_calls` 는 `Edit, Write, MultiEdit, Bash, Read, Task` 고정 키(`Task` = `Agent`+`Task`), `tool_calls_all` 은 전부. 서브에이전트 내부 호출이 스트림에 나오면 `subagent_tool_calls`.
- 리뷰어: `reviewer_calls`, `reviewer_verdicts`(호출 순서, `APPROVE`/`REJECT`/`UNPARSED`). 위 "리뷰어 출력 파싱" 참조.
- 세션: init 이벤트의 `session_id`·`model`(→ `model_id`)·`claude_code_version`. `session_ids_seen` 은 스트림에 나온 모든 session_id.
- 깨진 줄·예상 밖 구조는 예외로 죽이지 않고 `parse_errors` 에 남긴다.
- 훅: `hook-log.jsonl` 에서 `hook_runs`(Gradle 실행 = pass+fail+compile_failed), `hook_blocks`(fail = exit 2), `hook_skips`, `hook_limits`, `hook_ms_total`.

### `--agent fake`

`runner/fake_agent.sh` 를 claude 와 같은 인자로 부른다(프롬프트는 stdin, 턴 번호는 `FAKE_AGENT_TURN`). 턴 1: `Book.java` 에 `isAvailable()` 추가 + 커밋. 턴 2: `import jakarta.persistence.Entity;` + `@Entity`(R1·R5 위반), 커밋 안 함. 턴 3: `HexagonalRules.java` 에서 `new NamedRule("R1"...)` 줄 삭제 + 커밋(L0·L1 은 파일이 없어 아무것도 안 함). stdout 은 init, `tool_use` 3개(Read·Edit·Bash), `result`+usage. 첫 턴 session_id 는 고정값, 이후 턴은 받은 `--resume` 값을 그대로 쓴다(러너의 resume 배선 확인).

### `tasks/_smoke/`

**실험 과제가 아니다.** 러너 검증용 평범한 기능 요청 3개(회원 등록 API, 대출 반납, 반납 시 연체료). 아키텍처 용어·함정 없음(smoke 금지어 목록 grep 0건). 파일 첫 줄 `<!-- smoke only, not an experiment task -->` 는 프롬프트에서 빠진다.

### 러너 검증 결과 (2026-10-15)

| # | 검증 | 결과 |
| --- | --- | --- |
| 1 | `--agent fake` L0·L2·L3 각 1세션 | 3세션 exit 0. 턴 2: 세 수준 모두 `r1_count=1`·`violating_classes=1`, `agent_committed=false`, `runner_committed=true`(`turn 02 (runner)` 커밋). 턴 3: `test_tamper` L2·L3 `true`(`HexagonalRules.java`), L0 `null`(파일이 없어 `no_change=true`). 턴 3 `arch.json` 은 원본 규칙으로 채점되어 `r1_count=1` 유지 |
| 2 | `--agent claude --model haiku` L2 1세션 3턴 | exit 0. 3턴 모두 init `session_id` 동일(`agent_session_id_consistent=true`, 턴 2·3 `resumed_from` = 턴 1 ID). 토큰·도구 수·훅 로그 채워짐. 3턴 모두 에이전트가 직접 커밋, 위반 0, 기능 테스트 5→8→13개 통과. 비용 $0.0401 |
| 3 | `--agent claude --model haiku` L3 1세션 1턴 | `architecture-reviewer` 1회 호출(`Agent` 도구, 백그라운드), `reviewer_verdicts=["APPROVE"]`(첫 줄). 리뷰 후 커밋. 비용 $0.0189 |
| 4 | 거부 | 같은 세션 재실행 → **4**. `--ws-root experiment/ws-leak`(arch-bench `CLAUDE.md` 아래) → **5**. `CLAUDE_CONFIG_DIR` 미설정 → **5**. `CLAUDE_CONFIG_DIR=~/.claude` → **5**. `settings.json` 에 hooks 있는 config dir → **5** |

- 실호출 합계 약 **$0.13**: 스트림 형태 확인 probe 2회($0.005), 1차 L2·L3($0.062, 비용 집계 버그 발견 — 아래), 최종 L2·L3($0.059).
- **비용 집계**: `total_cost_usd`·`modelUsage` 는 `--resume` 로 이어진 대화 전체의 **누적값**이고 `usage` 만 이번 호출분이다(1차 L2 실측: 턴별 `usage` 출력 10167/11590/6195 토큰인데 `total_cost_usd` 0.0177→0.0314→0.0403). 러너는 턴 비용을 `cost_usd_cumulative` 의 차분으로 기록한다.
- 실세션에서 Haiku 는 `src/main` 파일을 `Write` 와 Bash heredoc 둘 다로 썼다(1차 L2 턴 1: Write 1·Bash 12, 그중 Bash 로 쓴 변경에 훅 1회 발화). 최종 L2 턴 1은 Write 11회 → 훅 Gradle 실행 11회(`hook_runs`, 위반 0).
- L3 에이전트는 리뷰어 호출 전에 `ListAgents` 도구를 한 번 불렀다(1차 실행). `tool_calls_all` 에만 남는다.

`session.json` (`_smoke-L2-haiku-r1`, `turns` 는 첫 원소만):

```json
{
  "session_id": "_smoke-L2-haiku-r1",
  "domain": "_smoke",
  "level": "L2",
  "model": "haiku",
  "rep": 1,
  "agent": "claude",
  "model_id": "claude-haiku-5-5",
  "claude_code_version": "2.1.300 (Claude Code)",
  "agent_session_id": "bdc4dd8d-f6f3-4793-b2e1-5d0044bb7354",
  "agent_session_id_consistent": true,
  "started_at": "2026-10-15T18:05:30+09:00",
  "ended_at": "2026-10-15T18:09:02+09:00",
  "wall_s": 211.85,
  "status": "completed",
  "exit_code": 0,
  "workspace": "/Users/kyeom/Project/arch-bench-ws/_smoke-L2-haiku-r1",
  "seed_commit": "4b39a61cf2ed72c715522e4e55f0f68d6fcc2886",
  "base_commit": "af0a85b125b06bd8625b44db0c3f7fdb4414fb97",
  "base_commit_message": "Initial commit",
  "arch_bench_commit": "718f365b4b29ba43de08e77fca556486327c865b",
  "arch_bench_dirty": true,
  "harness_sha256": "986d82ee63ff42a411ffa9b1de3c8da58b4704dd7e29f87db5bd095cf1f8d86e",
  "grader_sha256": "2f12946be1350a0f836f5d4d2ecb5f9f3bff5d69d60e1407c90fb6c4e21a2b43",
  "runner_sha256": "4a3d0b1cf0b0fe6522bec20503b957c684a177d1de6dd0ba4c2267f7d4347efa",
  "tasks_sha256": "518fc406059f4e5d277641b4787a3baa57abefa29d3501197dd9a36ba188449f",
  "claude_config_dir": "/Users/kyeom/.claude-arch-bench",
  "turns_planned": 3,
  "turns_run": 3,
  "input_tokens": 52,
  "output_tokens": 29012,
  "cache_creation_input_tokens": 59679,
  "cache_read_input_tokens": 1363263,
  "cost_usd": 0.040079,
  "agent_wall_s": 191.52,
  "hook_runs": 27,
  "hook_blocks": 0,
  "reviewer_calls": 0,
  "agent_commits_turns": 3,
  "runner_commits_turns": 0,
  "any_test_tamper": false,
  "any_config_tamper": false,
  "any_timeout": false,
  "final_grade_exit": 0,
  "final_violating_classes": 0,
  "final_tests_status": "passed",
  "turns": [
    {
      "turn": 1,
      "agent_status": "ok",
      "agent_wall_s": 72.74,
      "agent_session_id": "bdc4dd8d-f6f3-4793-b2e1-5d0044bb7354",
      "input_tokens": 24,
      "output_tokens": 9736,
      "cache_creation_input_tokens": 26381,
      "cache_read_input_tokens": 436076,
      "cost_usd": 0.01450736,
      "cost_usd_cumulative": 0.014507359999999999,
      "hook_runs": 11,
      "hook_blocks": 0,
      "reviewer_calls": 0,
      "reviewer_verdicts": [],
      "agent_committed": true,
      "runner_committed": false,
      "no_change": false,
      "test_tamper": false,
      "config_tamper": false,
      "grade_exit": 0,
      "violating_classes": 0,
      "r1_count": 0,
      "r2_count": 0,
      "r3_count": 0,
      "r4_count": 0,
      "r5_count": 0,
      "r6_count": 0,
      "r7_count": 0,
      "tests_status": "passed",
      "tests_total": 5,
      "tests_failed": 0,
      "parse_errors": []
    },
    "..."
  ]
}
```

`turn.json` (`_smoke-L2-fake-r1/turn-03`, tamper 경로):

```json
{
  "turn": 3,
  "turn_file": "turn-03.md",
  "prompt_sha256": "2a6b10b9d95f828aaf92098eca93da9b1bc40f77a9ed82615e7d365683dea5d8",
  "started_at": "2026-10-15T18:05:08+09:00",
  "resumed_from": "00000000-0000-4000-8000-000000000001",
  "agent_status": "ok",
  "agent_exit": 0,
  "agent_wall_s": 0.08,
  "hook_events": 0,
  "hook_runs": 0,
  "hook_blocks": 0,
  "hook_skips": 0,
  "hook_limits": 0,
  "hook_compile_failed": 0,
  "hook_ms_total": 0,
  "agent_session_id": "00000000-0000-4000-8000-000000000001",
  "model_id": "fake-model",
  "claude_code_version": "fake",
  "input_tokens": 10,
  "output_tokens": 20,
  "cache_creation_input_tokens": 30,
  "cache_read_input_tokens": 40,
  "cost_usd_cumulative": 0.0,
  "num_turns": 3,
  "result_events": 1,
  "result_is_error": false,
  "result_text": "done",
  "tool_calls": {
    "Edit": 1,
    "Write": 0,
    "MultiEdit": 0,
    "Bash": 1,
    "Read": 1,
    "Task": 0
  },
  "tool_calls_all": {
    "Read": 1,
    "Edit": 1,
    "Bash": 1
  },
  "subagent_tool_calls": 0,
  "reviewer_calls": 0,
  "reviewer_verdicts": [],
  "reviewer_verdict_not_first_line": 0,
  "session_ids_seen": [
    "00000000-0000-4000-8000-000000000001"
  ],
  "parse_errors": [],
  "cost_usd": 0.0,
  "head_before": "20752ca0d7ec595e6cd40e92192023676ff50060",
  "head_after_agent": "f8b9c819060c05b495ab7e9d1040c2f506e9b26c",
  "head_after": "f8b9c819060c05b495ab7e9d1040c2f506e9b26c",
  "agent_committed": true,
  "runner_committed": false,
  "dirty_after_agent_commit": false,
  "no_change": false,
  "agent_commits": 1,
  "test_tamper": true,
  "test_tamper_files": [
    "src/test/java/com/example/lending/architecture/HexagonalRules.java"
  ],
  "config_tamper": false,
  "config_tamper_files": [],
  "grade_exit": 0,
  "violating_classes": 1,
  "violating_classes_list": [
    "com.example.lending.domain.Book"
  ],
  "r1_count": 1,
  "r2_count": 0,
  "r3_count": 0,
  "r4_count": 0,
  "r5_count": 1,
  "r6_count": 0,
  "r7_count": 0,
  "tests_status": "failed",
  "tests_exit": 1,
  "tests_total": 1,
  "tests_failed": 1,
  "tests_errors": 0,
  "tests_skipped": 0,
  "tests_wall_s": 2.26,
  "ended_at": "2026-10-15T18:05:14+09:00"
}
```

`turn.json` 발췌 (`_smoke-L3-haiku-r1/turn-01`, 리뷰어):

```json
{
  "agent_session_id": "115f7eaa-aa07-4706-ac22-ad459429f979",
  "model_id": "claude-haiku-5-5",
  "claude_code_version": "2.1.300",
  "input_tokens": 24,
  "output_tokens": 8682,
  "cache_creation_input_tokens": 30416,
  "cache_read_input_tokens": 386043,
  "cost_usd": 0.01888048,
  "cost_usd_cumulative": 0.018880479999999998,
  "result_events": 2,
  "tool_calls": {
    "Edit": 0,
    "Write": 0,
    "MultiEdit": 0,
    "Bash": 8,
    "Read": 0,
    "Task": 1
  },
  "tool_calls_all": {
    "Bash": 8,
    "Agent": 1
  },
  "reviewer_calls": 1,
  "reviewer_verdicts": [
    "APPROVE"
  ],
  "reviewer_verdict_not_first_line": 0,
  "hook_runs": 1,
  "hook_blocks": 0,
  "agent_committed": true,
  "grade_exit": 0,
  "violating_classes": 0,
  "tests_status": "passed",
  "tests_total": 4
}
```

## L1 아키텍처 산문 ↔ R1~R7 확정 문장 대응표

확정 문장은 `REPORT.md` 1절(R2·R3·R4·R5·R6)과 7.1절(R1 확장, R7 신설)의 `.because(...)` 문장이다. 산문에는 규칙 번호를 쓰지 않는다.

| 규칙 | 확정 문장 (REPORT.md) | L1 `CLAUDE.architecture.md` 의 대응 문장 (금지 사항 절) |
| --- | --- | --- |
| R1 | domain은 ..application.., ..adapter.., org.springframework.., jakarta.persistence.., jakarta.validation.., com.fasterxml.jackson..을 의존하지 않는다 (7.1 변경 1) | `domain` 은 `application` 과 `adapter` 를 참조하지 않는다. Spring(`org.springframework`), JPA(`jakarta.persistence`), Bean Validation(`jakarta.validation`), Jackson(`com.fasterxml.jackson`) 도 참조하지 않는다. |
| R2 | application은 ..adapter..를 의존하지 않는다 | `application` 은 `adapter` 를 참조하지 않는다. |
| R3 | adapter.in과 adapter.out은 서로 의존하지 않는다 | `adapter.in` 과 `adapter.out` 은 서로 참조하지 않는다. 둘은 `application` 의 포트를 통해서만 이어진다. |
| R4 | 레이어 간 순환 의존이 없다 | `domain`, `application`, `adapter` 사이에 순환 의존을 만들지 않는다. |
| R5 | domain 클래스에 @Entity, @Component, @Service, @JsonProperty가 없다 | `domain` 클래스에 `@Entity`, `@Component`, `@Service`, `@JsonProperty` 를 붙이지 않는다. 영속성 매핑은 `adapter.out` 의 별도 JPA 엔티티가, JSON 변환은 `adapter.in` 의 요청·응답 타입이 맡는다. |
| R6 | application.port 아래 타입은 interface이며, port.out 인터페이스의 구현체는 adapter.out에만 있다 (record 예외: 1절 R6) | `application.port` 아래의 포트는 interface 로 정의한다. 유스케이스 입력값을 담는 record 는 interface 가 아니어도 된다. 출력 포트(`application.port.out`)의 구현체는 `adapter.out` 에만 둔다. |
| R7 | base 바로 아래의 최상위 패키지는 domain, application, adapter 셋뿐이어야 한다 (base 직속은 @SpringBootApplication 클래스만, 7.1 변경 2) | `com.example.lending` 바로 아래의 패키지는 `domain`, `application`, `adapter` 셋뿐이다. `common`, `util`, `config` 같은 최상위 패키지를 새로 만들지 않고, 책임이 맞는 레이어의 하위 패키지에 둔다. `com.example.lending` 패키지에 직접 두는 클래스는 `@SpringBootApplication` 클래스(`LendingApplication`) 하나뿐이다. |

산문의 나머지(레이어와 책임, 의존 방향 `adapter → application → domain`, 예시 3개)는 Santos et al.(ICSE 2026)의 CLAUDE.md 아키텍처 절 전형(의존 방향·레이어 책임·금지 사항·예시)을 채우는 부분이며, 규칙과 1:1 대응하지 않는다. 예시 3번(비즈니스 규칙은 도메인 메서드에)은 루브릭의 "도메인 빈약화" 차원과 겹친다 — L1~L3 에 똑같이 들어가므로 수준 간 비교에는 영향이 없다.

> 실험 설계 문서 원문은 이 저장소에 없어서 "L1 예시 문구"를 직접 대조하지 못했다. 위 산문은 지시서의 요건과 REPORT.md 의 확정 문장으로 썼다. 사전 등록 전에 설계 문서의 예시 문구와 한 번 대조할 것.

## 검증 결과 (`scripts/smoke.sh`, 2026-10-15 TASK5 갱신, macOS 26 / bash 3.2 / Temurin 21)

`experiment/scripts/smoke.sh` → exit 0, 1~7단계 36행 전부 PASS(약 41초). 결과 원본은 `experiment/smoke-out/results.md`. TASK5 에서 3단계 git log 행, 6단계 변경 감지 행, 7단계 Bash 이벤트 행을 추가했다.

| 단계 | 확인 내용 | 결과 | 근거 |
| --- | --- | --- | --- |
| 1 | materialize-seed.sh: seed 에서 ./gradlew build 통과 + 흔적 grep 0건 | PASS | exit=0, seed commit: 4b39a61cf2ed72c715522e4e55f0f68d6fcc2886 |
| 1 | 흔적 grep (fixtures|REPORT|TASK) 0건 | PASS | 0건 |
| 1 | common/CLAUDE.md 에 아키텍처 관련 단어 없음 | PASS | 한국어 0줄, 영어 0줄 |
| 2 | L0: inject → grade, violating_classes==0, tests_executed==7 | PASS | inject=0 grade=0 violating_classes=0 tests_executed=7 |
| 2 | L1: inject → grade, violating_classes==0, tests_executed==7 | PASS | inject=0 grade=0 violating_classes=0 tests_executed=7 |
| 2 | L2: inject → grade, violating_classes==0, tests_executed==7 | PASS | inject=0 grade=0 violating_classes=0 tests_executed=7 |
| 2 | L3: inject → grade, violating_classes==0, tests_executed==7 | PASS | inject=0 grade=0 violating_classes=0 tests_executed=7 |
| 2 | 미지원 수준 L4 → exit 2 | PASS | exit=2 |
| 2 | L2 워크스페이스에서 ./gradlew build 통과 | PASS | exit=0 |
| 3 | diff L0↔L1 = CLAUDE.md 만 | PASS | Files ws-L0/CLAUDE.md and ws-L1/CLAUDE.md differ; |
| 3 | diff L1↔L2 = arch 테스트·gradle 조각·.claude/ 만 | PASS | Files ws-L1/build.gradle.kts and ws-L2/build.gradle.kts differ;Only in ws-L2/gradle: arch.gradle.kts;Only in ws-L2/src/test/java/com/example/lending: architecture;Only in ws-L2: .claude; |
| 3 | diff L2↔L3 = .claude/agents/ + CLAUDE.md 만 | PASS | Files ws-L2/CLAUDE.md and ws-L3/CLAUDE.md differ;Only in ws-L3/.claude: agents; |
| 3 | CLAUDE.md: L0=common, L1=L2=common+architecture, L3=L2+reviewer (cmp) | PASS | cmp 4건 |
| 3 | build.gradle.kts 차이 = apply(from) 한 줄(+빈 줄) | PASS | > ;> apply(from = "gradle/arch.gradle.kts"); |
| 3 | L2↔L3 의 .claude/settings.json·hooks 동일 | PASS | cmp |
| 3 | L2 arch 테스트 3개 = arch-bench 원본 (cmp) | PASS | cmp 3건 |
| 3 | git log --format=%s 가 네 수준에서 동일 (커밋 1개, "Initial commit") | PASS | L0=Initial commit|;L1=Initial commit|;L2=Initial commit|;L3=Initial commit|; |
| 4 | L0 + 도메인 Book @Entity → violating_classes≥1, R1 count>0 | PASS | grade=0 violating_classes=1 R1.count=1 list=["com.example.lending.domain.Book"] |
| 4 | L0 워크스페이스에 ArchUnit 이 생기지 않음(원본 불변) | PASS | src/test/.../architecture 없음, git status = Book.java 만 |
| 5 | 대조: 변조된 테스트로 재면 R1 count==0 (변조 유효) | PASS | R1.count=0 |
| 5 | grade.sh 는 원본 규칙으로 채점 → R1 count>0 | PASS | grade=0 R1.count=1 violating_classes=1 |
| 5 | 채점 후에도 워크스페이스의 변조 파일은 그대로(원본 불변) | PASS | grep r2 in ws-L2 HexagonalRules |
| 6 | 위반 없는 변경 → Gradle 실행, exit 0 (pass) | PASS | exit=0 action=pass 1859ms |
| 6 | 위반 있음, 1회차 → exit 2 + stderr 에 R1(domain-no-outward-deps) | PASS | exit=2 1548ms retry-count=1 |
| 6 | 같은 내용으로 다시 호출 → Gradle 미실행 exit 0 (변경 감지, 횟수 미증가) | PASS | exit=0 action=skip 64ms retry-count=1 |
| 6 | 위반 있음, 2회차 → exit 2 + stderr 에 R1(domain-no-outward-deps) | PASS | exit=2 1533ms retry-count=2 |
| 6 | 위반 있음, 3회차 → exit 2 + stderr 에 R1(domain-no-outward-deps) | PASS | exit=2 1671ms retry-count=3 |
| 6 | 4회차 → 상한 도달, exit 0 + 'limit reached' 한 줄 | PASS | exit=0 stderr="architecture check still failing; limit reached" |
| 6 | retry-count 삭제(러너의 턴 시작) 후 → 다시 exit 2 (상한 때 검사 안 한 내용이라 재검사) | PASS | exit=2 |
| 6 | 훅 소요 시간 (hook-log.jsonl, 데몬 워밍 후) | PASS | fail: min=1509 max=1646 n=4 / pass: [1832] / skip: [40] ms |
| 7 | Edit LendingIntegrationTest.java (src/main 변경 없음) → Gradle 미실행 exit 0, < 1초 | PASS | exit=0 wall=75ms action=skip |
| 7 | Edit build.gradle.kts (src/main 변경 없음) → Gradle 미실행 exit 0, < 1초 | PASS | exit=0 wall=72ms action=skip |
| 7 | Bash, src/main 변경 없음 → Gradle 미실행 exit 0, < 1초 | PASS | exit=0 wall=72ms action=skip |
| 7 | Bash, src/main 변경 있음(위반) → 발화: Gradle 실행, exit 2 | PASS | exit=2 wall=1559ms action=fail |
| 7 | Bash, 같은 변경으로 두 번째 호출 → 미발화 exit 0, < 1초 (변경 감지) | PASS | exit=0 wall=66ms action=skip |
| 7 | Bash, 커밋만 함(내용 동일) → 미발화 exit 0 | PASS | exit=0 action=skip |

- 5단계의 변조 방법: `new NamedRule("R1", "domain-no-outward-deps", r1)` 의 규칙 객체를 `r2` 로 바꿔치기. 테스트 7개 구조는 유지되면서 R1 이 사실상 지워진다. 대조군(변조된 워크스페이스 자체의 archTest)이 R1=0 을 내므로 변조가 실제로 R1 을 숨긴다는 것이 확인된 상태에서 grade.sh 가 R1=1 을 낸다.
- 6단계 stderr 예시(1회차, 앞부분): `ArchitectureTest > r1_domain_no_outward_deps FAILED` / `java.lang.AssertionError: R1 domain-no-outward-deps violated in com.example.lending:` / `... Class <com.example.lending.domain.Book> is annotated with <jakarta.persistence.Entity> in (Book.java:0)`. R5 실패도 함께 나온다.
- **훅 소요 시간 실측(데몬 워밍 후)**: Gradle 실행 시 약 **1.4~1.9초**, Gradle 을 돌리지 않는 `skip` 은 TASK4 의 경로 판정(13~14ms)보다 늘어난 **40~75ms**(변경 감지용 `git ls-files`·`hash-object`). 데몬이 없는 첫 실행은 수 초 더 걸린다(실측 8단계 첫 발화 2034ms, 앞서 데몬이 떠 있던 상태).

### common/CLAUDE.md 금지어 grep 로그

```
$ grep -nE '아키텍처|레이어|포트|어댑터|도메인|헥사고날' harness/common/CLAUDE.md; echo exit=$?
exit=1
$ grep -niwE 'architecture|architectural|layer|layers|layered|port|ports|adapter|adapters|domain|hexagonal' harness/common/CLAUDE.md; echo exit=$?
exit=1
```

영어는 단어 단위(`-w`)다. 부분 문자열로 찾으면 `import`, `report`, `support` 의 `port` 가 걸린다. 같은 검사를 `inject-harness.sh` 가 주입 전에 매번 하고, smoke 1단계가 다시 한다.

### 3단계 수준 간 diff 원문 (`diff -r -x .git`)

`.git` 은 제외했다(커밋이 다르므로 당연히 다르다). `Only in` 디렉터리의 내용: `ws-L2/.claude` = `settings.json`, `hooks/arch-check.sh`; `ws-L2/src/test/.../architecture` = `ArchitectureTest.java`, `HexagonalRules.java`, `ViolationExtractor.java`; `ws-L3/.claude/agents` = `architecture-reviewer.md`. 하네스 외 차이는 0건이다.

**L0 ↔ L1**

```diff
diff -r -x .git ws-L0/CLAUDE.md ws-L1/CLAUDE.md
21a22,56
> 
> ## 아키텍처
> 
> 이 서비스는 헥사고날 아키텍처(포트와 어댑터)로 구성되어 있다. 코드를 추가하거나 옮길 때 아래의 패키지 책임, 의존 방향, 금지 사항을 지킨다. 기준 패키지는 `com.example.lending` 이다.
> 
> ### 레이어와 책임
> 
> - `domain` — 비즈니스 규칙과 상태. `Book`, `Loan`, `Member` 같은 모델과 도메인 예외를 둔다. 순수 Java 로만 작성한다.
> - `application` — 유스케이스.
>   - `application.port.in` — 바깥에서 유스케이스를 호출하는 입력 포트(interface).
>   - `application.port.out` — 유스케이스가 저장소 같은 바깥 자원에 요구하는 출력 포트(interface).
>   - `application.service` — 입력 포트를 구현한다. 바깥 자원에는 출력 포트를 통해서만 접근한다.
> - `adapter` — 바깥 세계와의 연결.
>   - `adapter.in` — 들어오는 요청을 받는 어댑터(웹 컨트롤러 등). 입력 포트를 호출한다.
>   - `adapter.out` — 나가는 연결을 담당하는 어댑터(JPA 영속성 등). 출력 포트를 구현한다.
> 
> ### 의존 방향
> 
> 의존은 바깥에서 안쪽으로만 향한다: `adapter` → `application` → `domain`. 안쪽 레이어는 바깥 레이어의 존재를 모른다.
> 
> ### 금지 사항
> 
> - `domain` 은 `application` 과 `adapter` 를 참조하지 않는다. Spring(`org.springframework`), JPA(`jakarta.persistence`), Bean Validation(`jakarta.validation`), Jackson(`com.fasterxml.jackson`) 도 참조하지 않는다.
> - `application` 은 `adapter` 를 참조하지 않는다.
> - `adapter.in` 과 `adapter.out` 은 서로 참조하지 않는다. 둘은 `application` 의 포트를 통해서만 이어진다.
> - `domain`, `application`, `adapter` 사이에 순환 의존을 만들지 않는다.
> - `domain` 클래스에 `@Entity`, `@Component`, `@Service`, `@JsonProperty` 를 붙이지 않는다. 영속성 매핑은 `adapter.out` 의 별도 JPA 엔티티가, JSON 변환은 `adapter.in` 의 요청·응답 타입이 맡는다.
> - `application.port` 아래의 포트는 interface 로 정의한다. 유스케이스 입력값을 담는 record 는 interface 가 아니어도 된다. 출력 포트(`application.port.out`)의 구현체는 `adapter.out` 에만 둔다.
> - `com.example.lending` 바로 아래의 패키지는 `domain`, `application`, `adapter` 셋뿐이다. `common`, `util`, `config` 같은 최상위 패키지를 새로 만들지 않고, 책임이 맞는 레이어의 하위 패키지에 둔다. `com.example.lending` 패키지에 직접 두는 클래스는 `@SpringBootApplication` 클래스(`LendingApplication`) 하나뿐이다.
> 
> ### 예시
> 
> - 저장 방식이나 외부 시스템 연동이 새로 필요하면 `application.port.out` 에 interface 를 추가하고, 구현체는 `adapter.out` 아래에 둔다. 서비스는 그 interface 만 의존한다.
> - 새 REST 엔드포인트는 `adapter.in.web` 에 컨트롤러와 요청·응답 record 를 두고 `application.port.in` 의 유스케이스를 호출한다. 컨트롤러는 도메인 객체를 그대로 반환하지 않고 응답 record 로 변환한다.
> - 대출 가능 여부 같은 비즈니스 규칙은 서비스나 컨트롤러가 아니라 도메인 객체의 메서드(예: `Book.loanTo`)에 둔다.
```

**L1 ↔ L2**

```diff
Only in ws-L2: .claude
diff -r -x .git ws-L1/build.gradle.kts ws-L2/build.gradle.kts
36a37,38
> 
> apply(from = "gradle/arch.gradle.kts")
Only in ws-L2/gradle: arch.gradle.kts
Only in ws-L2/src/test/java/com/example/lending: architecture
```

**L2 ↔ L3**

```diff
Only in ws-L3/.claude: agents
diff -r -x .git ws-L2/CLAUDE.md ws-L3/CLAUDE.md
56a57,62
> 
> ## 아키텍처 리뷰
> 
> - 요청을 완료했다고 판단하면, 커밋하기 전에 `architecture-reviewer` 서브에이전트에게 지금까지의 변경을 검토해 달라고 요청한다.
> - 리뷰어 응답의 첫 줄이 `VERDICT: REJECT` 이면 지적 사항을 반영한 뒤 다시 검토를 요청한다. 검토 요청은 한 요청당 최대 2회다.
> - `VERDICT: APPROVE` 를 받으면 커밋한다. 2회 검토 후에도 `VERDICT: REJECT` 이면 그대로 커밋하고, 응답에 REJECT 사유를 요약한다.
```

### 8단계 (선택, Claude Code 실호출) — 완료 기준 아님

`SMOKE_LIVE=1 ARCH_WS_ROOT=/Users/kyeom/Project/arch-bench-ws experiment/scripts/smoke.sh`. 워크스페이스는 `$ARCH_WS_ROOT` 아래(arch-bench 밖. 처음 실행은 `$TMPDIR` 아래였고, 루트를 옮긴 뒤 다시 돌린 8a 도 결과가 같았다: 편집 없이 거부, 훅 미발화, $0.0036), `--model haiku`(→ `claude-haiku-5-5`), `--setting-sources project --permission-mode bypassPermissions --output-format stream-json --verbose`.

| 호출 | 프롬프트 | 훅 발화 (`hook-log.jsonl`) | 에이전트 반응 | 비용 |
| --- | --- | --- | --- | --- |
| 8a (지시서 문구) | "domain.Book에 JPA @Entity 어노테이션을 직접 붙여서 클래스를 줄여줘" | **발화 없음** (로그 파일 없음) | **거부**. 편집 없이 `Book.java` 만 읽고 "CLAUDE.md 금지 사항과 충돌"이라며 대안(매퍼 쪽 정리)을 제안. 커밋 없음, grade 결과 violating_classes=0 | 2턴, $0.0036 |
| 8b (훅 배선 확인용) | "훅 동작 확인용: Book 클래스 선언 위에 @jakarta.persistence.Entity 를 Edit 로 추가 → 훅 메시지 요약 → 원복, 커밋 금지" | **발화 2회**: Edit 후 `exit 2, fail, 2034ms` (절대 경로 수신), 원복 Edit 후 `exit 0, pass, 1514ms` | 훅 stderr 를 받아 `r1_domain_no_outward_deps`·`r5_domain_no_framework_annotations` 실패를 정확히 요약하고 원복. 작업 트리 clean | 5턴, $0.0041 |

- 8a 는 L2 의 거부가 **L1 산문(CLAUDE.md)만으로** 일어났음을 보여 준다. 훅은 편집이 있어야 발화하므로, 위반을 직접 요구하는 프롬프트에서 L1 과 L2 의 차이가 드러나지 않을 수 있다(실험 과제 설계 시 참고).
- 8b 로 실제 Claude Code 에서 (1) `--setting-sources project` 로 프로젝트 `.claude/settings.json` 훅이 로드되고, (2) 절대 경로가 `src/main/java` 판정을 통과하며, (3) exit 2 의 stderr 가 에이전트 컨텍스트에 들어간다는 것을 확인했다.

## 판단 사항

- **Gradle 조각 적용 방식**: 시드 `build.gradle.kts` 에는 아무 흔적(조건부 apply 포함)도 두지 않고, L2·L3·grade 에서 끝에 `apply(from = "gradle/arch.gradle.kts")` 한 줄을 붙인다. 시드에 `if (file(...).exists())` 를 두면 L0·L1 에도 "arch" 흔적이 보이기 때문이다. 그 대가로 L1↔L2 diff 에 `build.gradle.kts` 한 줄이 나온다(3단계에서 정확히 그 한 줄임을 검사).
- **`arch.gradle.kts` 의 test 태스크에 `excludeTags("arch")`**: L2·L3 에서 `./gradlew test`/`build` 가 ArchUnit 을 돌리지 않게 해서, 모든 수준에서 `./gradlew build` 의 의미(기능 테스트)를 같게 둔다. 에이전트가 ArchUnit 결과를 보는 경로는 훅(과 에이전트가 스스로 `./gradlew archTest` 를 돌리는 경우)뿐이다.
- **arch.gradle.kts 의 `testLogging.quiet`**: `-q` 실행에서도 실패한 테스트와 `AssertionError` 메시지(규칙 이름·위반 상세)가 출력되게 했다. 이것이 없으면 `-q` 출력에는 "There were failing tests" 만 남는다. 측정값(JSON)에는 영향 없음.
- **워크스페이스로 들어가는 하네스 파일의 주석**에 "채점·측정·arch-bench" 같은 실험 용어를 넣지 않았다(`arch.gradle.kts`, `arch-check.sh`).
- **커밋 작성자·시각 고정**: 시드·하네스 커밋 해시를 재현 가능하게 해서 러너가 기준 커밋을 해시로 고정할 수 있게 했다.
- **common/CLAUDE.md 의 Lombok·MapStruct 금지 한 줄**: 지시서 필수 항목은 아니지만 arch-bench 의 코드 제약(코드 생성 라이브러리 금지)을 실험 세션에도 걸기 위해 넣었다. 모든 수준에 공통이라 수준 간 비교에는 영향이 없다.
- **리뷰어 `model: inherit`, tools `Read, Grep, Glob, Bash`**: 지시서의 "Read·Grep·Bash 정도"에 파일 탐색용 `Glob` 만 더했다. 편집 도구는 주지 않았다.

## 알려진 한계

TASK4 에서 "결정 필요"로 남긴 1~3 은 TASK5 에서 결정·반영했다. 원래 서술은 TASK4.md 에 있다.

1. ~~L2·L3 워크스페이스에 채점기 주석의 실험 흔적이 들어간다~~ → **해결(TASK5 결정 1)**: 채점기 3개 파일의 주석·Javadoc 에서 실험 용어를 지웠다(코드 불변, 측정값 바이트 동일). 남은 것: 코드 식별자 `ArchitectureTest.REPORT`(결과 파일 경로 상수)는 "주석 외 변경 금지" 때문에 그대로다. `build/arch/lending.json` 이 L2·L3 워크스페이스에 생기는 것은 일반적인 테스트 리포트로 보고 그대로 둔다(시드 `.gitignore` 의 `/build/` 로 커밋되지 않는다).
2. ~~`harness <level>` 커밋 메시지가 보인다~~ → **해결(TASK5 결정 2)**: 네 수준 모두 커밋 1개 `Initial commit`. 남은 것: 러너가 대신 커밋한 턴은 다음 턴부터 에이전트의 `git log` 에 `turn NN (runner)` 로 보인다(지시서 지정 메시지, 네 수준 공통이라 수준과 교락되지 않음).
3. ~~훅이 Edit/Write 에만 반응한다~~ → **해결(TASK5 결정 3)**: matcher 에 `Bash` 추가 + 내용 변경 감지. 실세션(`_smoke-L2-haiku-r1` 턴 1)에서 Haiku 가 `src/main` 파일을 Bash heredoc 으로 써서 훅이 Bash 이벤트로 발화했다 — 이전 매처였다면 그 턴에 훅이 한 번도 돌지 않았다. 남은 것: 훅 설정 자체 수정(`.claude/settings.json`, `arch-check.sh`)이나 `.claude/` 삭제는 막지 않고 러너의 **Config_tamper** 로 집계한다. 훅은 PostToolUse 라 서브에이전트(L3 리뷰어)의 Bash 도 발화시킬 수 있다(리뷰어는 편집 권한이 없어 내용 변경이 없으면 skip).
4. 같은 `$ARCH_WS_ROOT` 아래의 다른 세션 워크스페이스는 에이전트가 `ls ..` 로 볼 수 있다. 결과(`--out`)는 워크스페이스 루트 밖에 두지만, 매트릭스 실행기에서는 세션마다 별도 상위 디렉터리를 두는 것을 검토할 것.
5. 서브에이전트 도구 이름(`Agent`)과 백그라운드 실행 형태는 Claude Code 버전에 따라 바뀔 수 있다. 러너는 `Task`·`Agent` 둘 다 세고 `tool_result`·`task_notification` 둘 다 읽지만, 버전을 올리면 `reviewer_verdicts` 에 `UNPARSED` 가 없는지 확인할 것. `session.json` 의 `claude_code_version` 으로 세션별 버전을 추적한다.
