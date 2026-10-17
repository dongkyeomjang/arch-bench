# experiment — 시드 저장소, 하네스 수준 주입, 오버레이 채점기, 세션 러너, 매트릭스 실행기, 도서 대출 과제

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
    lloc.py                  # Java 논리 코드 줄 수 (LLOC_ratio 용, 아래 "참조 구현")
  runner/
    run_session.py           # 세션 1개 실행 (아래 "세션 러너")
    run_matrix.py            # 세션 매트릭스 실행 (아래 "매트릭스 실행기")
    matrix.example.json      # 매트릭스 설정 예시 (fake, 12세션)
    pilot.json               # 파일럿 설정 (L1·L2 × haiku × lending × 5회 = 10세션). 지시 전까지 실행 금지
    fake_agent.sh            # --agent fake 용 결정적 가짜 에이전트 (러너 경로 검증용)
    requirements.txt         # 비어 있음 (표준 라이브러리만)
  tasks/
    _smoke/turn-0{1,2,3}.md  # 러너 검증용 3턴. 실험 과제가 아니다
    lending/turn-01..15.md   # 도서 대출 15턴 과제 (에이전트가 받는 프롬프트, 아래 "도서 대출 과제")
  tests/
    lending/turn-NN/         # 숨겨진 기능 테스트 (HTTP 만). 러너가 세션 밖에서 턴 1~N 누적 실행 → FT_pass
  reference/
    lending-full/            # 시드에서 15턴을 헥사고날대로 구현한 참조 구현 (턴별 커밋 15개). 에이전트에게 보이지 않음
    lloc.json                # 참조 구현 턴별 LLOC (src/main/java, 누적)
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
    [--agent-max-turns N] [--skip-tests] [--session-mode resume|fresh] [--max-session-cost USD] \
    [--rate-limit-poll-s S] [--rate-limit-max-wait-s S]
```

Python 3.11+ 표준 라이브러리만 쓴다(`requirements.txt` 는 비어 있다). 세션 1개만 다룬다. 여러 세션은 아래 "매트릭스 실행기".

| 항목 | 내용 |
| --- | --- |
| 세션 ID | `<domain>-<level>-<model>-r<rep>` (예: `_smoke-L2-haiku-r1`). 결과 `$OUT_ROOT/<id>/` |
| 워크스페이스 | `$ARCH_WS_ROOT/<token>/lending` (TASK6). `<token>` = `secrets.token_hex(6)`(무작위 12자 hex). 에이전트 cwd 는 `.../lending` 이라 `pwd`·`ls ..` 로 수준·모델을 알 수 없다(`ls ..` 에는 `lending` 하나). `ls ../..` 에는 형제 세션의 토큰 디렉터리가 보이지만 이름이 무작위다. `session.json` 의 `workspace_token`, 그리고 **`$OUT_ROOT/workspace-index.jsonl`**(`{token, session_id, created_at}` 한 줄 append, 에이전트가 읽지 못하게 `$ARCH_WS_ROOT` 밖)로 역추적한다 |
| 덮어쓰기 | 결과 디렉터리(또는 우연히 같은 토큰 디렉터리)가 이미 있으면 거부, **종료 4** |
| 시작 전 검사 (**종료 5**) | ① `--ws-root` 부터 `/` 까지 `CLAUDE.md`·`CLAUDE.local.md`·`.claude` 없음(`~/.claude` 제외, smoke 8단계와 같은 검사) ② `CLAUDE_CONFIG_DIR` 설정됨, 디렉터리 존재, `~/.claude` 자신이 아님 ③ 그 안 `settings.json` 이 없거나 `hooks` 가 비어 있음 ④ 그 안에 `CLAUDE.md` 없음, `agents/`·`skills/`·`commands/` 비어 있음 ⑤ (`--agent claude`) `claude` 가 PATH 에 있음 ⑥ (`--agent claude`, TASK9) 환경에 `ANTHROPIC_API_KEY`·`ANTHROPIC_AUTH_TOKEN` 이 **없고**(있으면 구독이 아니라 API 과금으로 나간다) `claude auth status` 가 `loggedIn: true`, `authMethod: "claude.ai"`. 인증 요약(`auth_method`·`subscription_type`·`api_provider`, 이메일 제외)은 `session.json` 의 `billing` ⑦ (TASK12) `settings.json` 에 `syncClaudeAiSkills: false`·`syncClaudeAiPlugins: false`·`disableClaudeAiConnectors: true` 가 **정확히** 있음(키가 없어도 거부) ⑧ (TASK12) `plugins/` 에는 마켓플레이스 목록(`known_marketplaces.json`·`marketplaces/`)만 있음. `installed_plugins.json` 은 `plugins` 가 비어 있을 때만 허용, 그 밖의 비어 있지 않은 항목(`cache/`·`repos/`·`synced/` 등)이 있으면 거부. 동기화 스킬 경로 `skills/synced/` 는 ④ 가 막는다 ⑨ (TASK13) 에이전트에 넘길 환경(`agent_env()`)에 `EXTRA_ENV` 가 그대로 있음 — 특히 `ENABLE_CLAUDEAI_MCP_SERVERS=false`(없으면 커넥터가 실린다, TASK12) |
| 준비 | `experiment/seed` 가 없으면 `materialize-seed.sh` → `cp -R seed <ws>` → `inject-harness.sh <level> <ws>` → `base_commit` = "Initial commit" 해시 |
| 턴 순서 | `tasks/<domain>/turn-*.md` 를 이름순. `--max-turns N` 이면 앞 N개만 |
| 프롬프트 | 턴 파일 본문. **맨 앞의 HTML 주석 줄**(`<!-- smoke only ... -->` 같은 메타데이터)은 빼고 보낸다 |
| 에이전트 | `claude -p <prompt> --model <m> --output-format stream-json --verbose --permission-mode bypassPermissions --setting-sources project [--resume <id>]`, `cwd=<ws>`, stdin 없음. `--session-mode resume`(기본): 첫 턴은 새 세션, 이후 턴은 **직전 턴 init 이벤트의 `session_id` 로 `--resume`**. `--session-mode fresh`(TASK9): 턴마다 `--resume` 없이 새 세션 — 에이전트는 이전 턴 대화를 모르고 코드·git 이력·CLAUDE.md(L1~L3)만 본다. 턴 단위 동작(훅 `retry-count` 삭제, 훅 로그 이동, 채점·숨겨진 테스트)은 모드와 무관하게 같다. 타임아웃 20분(프로세스 그룹 SIGTERM → 30초 뒤 SIGKILL, `agent_status: timeout`, 세션은 계속) |
| 환경 | 부모 환경에서 `CLAUDECODE`·`CLAUDE_CODE_*`·`CLAUDE_PID`·`CLAUDE_EFFORT`·`CLAUDE_PROJECT_DIR` 를 빼고(러너를 Claude Code 안에서 띄워도 부모 세션 정보가 새지 않게) `DISABLE_AUTOUPDATER=1` 과 (TASK12) `ENABLE_CLAUDEAI_MCP_SERVERS=false` 를 더한다. 뒤의 것은 claude.ai 커넥터 MCP 서버를 끈다 — 에이전트는 `--setting-sources project` 라 `CLAUDE_CONFIG_DIR/settings.json`(사용자 설정)이 **읽히지 않으므로** 거기의 `disableClaudeAiConnectors` 는 에이전트 세션에 효과가 없다(아래 "격리 설정 디렉터리 정리" 정정) |
| 훅 이벤트 (TASK12) | 에이전트 명령에 `--include-hook-events`(출력 전용). 스트림의 `system/hook_response` 를 `turn.json` 의 `hook_responses`(`hook_name`·`exit_code`·`outcome`·`rules`(stderr 에서 뽑은 실패 규칙 ID)·`stderr_head` 1500자)로 남긴다. 그 전에는 훅 exit 2 의 stderr(에이전트가 받은 위반 상세)가 어디에도 남지 않았다 |
| 턴 단계 | ① `.harness/retry-count` 삭제(남은 `hook-log.jsonl` 이 있으면 `hook-log.pre-turn.jsonl` 로 치움) ② 에이전트 실행 → `stream.jsonl`·`stderr.txt`, 끝나면 `.harness/hook-log.jsonl` 을 `turn-NN/hook-log.jsonl` 로 **이동**(없으면 빈 파일) ③ 스트림 파싱 ④ 커밋 확인 ⑤ tamper ⑥ `grade.sh <ws> turn-NN/arch.json` ⑦ 숨겨진 기능 테스트(턴 1~N 누적, `turn-NN/ft.log`) ⑧ 복사본에서 에이전트 자신의 테스트 `./gradlew test -q` ⑨ `turn-NN/turn.json` |
| 커밋 확인 | 턴 전후 `HEAD` 가 같으면 `agent_committed: false`. 작업 트리가 더러우면(에이전트가 커밋 안 했거나, 커밋 뒤에 남긴 변경) 러너가 `git add -A && git commit -m "Save work in progress"` (`runner_committed: true`). 메시지에 턴 번호를 넣지 않는다(에이전트의 `git log` 에 보인다, 전 수준 동일). 둘 다 아니면 `no_change: true`. 그래서 턴 끝 상태 = `HEAD` 다 |
| tamper | `git diff --name-only <base_commit> HEAD -- src/test/java/com/example/lending/architecture/` 가 비어 있지 않으면 `test_tamper: true`(L0·L1 은 `null`, 파일 목록만 기록). `.claude/ CLAUDE.md build.gradle.kts gradle/arch.gradle.kts` 중 변경 파일이 있으면 `config_tamper: true` + `config_tamper_files`(모든 수준에서 계산. **`build.gradle.kts` 는 정상적인 의존성 추가로도 바뀌므로** 분석 시 파일 목록으로 거른다) |
| 채점 | `grade_exit`(0/1/3), `violating_classes`, `r1_count`~`r7_count`. 3(컴파일 실패)이어도 다음 턴 진행 |
| 숨겨진 기능 테스트 (TASK7) | 매 턴. 워크스페이스 복사본에서 `build`·`.gradle`·`.harness`·**`src/test` 전체**를 지우고 `experiment/tests/<domain>/turn-01..turn-NN/*.java` 를 `src/test/java/com/example/lending/hidden/` 에 복사 → `./gradlew test -q`. `ft_total` = 복사한 소스의 `@Test` 메서드 수(정적으로 셈 — 컴파일 실패·컨텍스트 실패로 XML 이 덜 나와도 분모가 같다), `ft_passed` = JUnit XML 에서 failure·error·skipped 없이 나온 테스트 수, `ft_failed_names` = 나머지 전부(`TurnNNTest.메서드`), `ft_pass` = `ft_passed/ft_total`. `ft_status` ∈ `passed`·`failed`·`compile_failed`(XML 없음 + `compileJava`/`compileTestJava` 실패, `ft_compile_error: true`)·`timeout`·`no_tests`(테스트 디렉터리 없음, 예: `_smoke` → `ft_total: 0`, `ft_pass: null`). 테스트는 HTTP 만 쓰므로 `compile_failed` 는 보통 에이전트의 본문 코드가 컴파일되지 않는 경우다. `--skip-tests` 와 무관하게 매 턴 돈다 |
| 기능 테스트 | 워크스페이스 복사본(`build`·`.gradle`·`.harness` 제거)에서 `./gradlew test -q`, `build/test-results/test/*.xml` 로 건수. `tests_status` ∈ `passed`·`failed`·`compile_failed`·`timeout`. 기본은 턴마다, `--skip-tests` 면 마지막 턴만 |
| 세션 비용 상한 (TASK9) | `--max-session-cost USD`: **턴이 끝날 때마다** 세션 누적 `cost_usd` 를 보고 넘으면 다음 턴을 시작하지 않는다(`fail_reason: session_cost_cap`, 종료 1). 턴 도중에는 끊지 않는다 — TASK8 의 임시 감시(SIGINT)처럼 `session.json` 밖 비용을 만들지 않기 위해서다. 그래서 마지막 턴 비용만큼 넘을 수 있다 |
| 구독 한도 (TASK9) | 아래 "구독 실행" 참조. 한도 거부는 실패로 두지 않고 대기 후 같은 턴을 다시 시작한다 |
| 거절 (TASK9) | `task_refused` = `no_change`(턴 전후 작업 트리·HEAD 변화 없음) 이고 `agent_status: ok`. 자동 판정은 이것뿐이고, `refusal_text`(최종 응답 앞 500자)를 함께 남겨 거절·대안 제시인지 사람이 확인한다(변경 없이 끝난 정상 응답도 `true` 가 된다) |
| 중단 | 에이전트 `agent_status: error`(0 아닌 종료, `result` 이벤트 없음, `is_error: true`) 또는 (resume 모드에서) 다음 턴에 쓸 session_id 가 없으면 그 턴까지 기록하고 **종료 1**. Ctrl-C 도 1. 사유는 `session.json` 의 `fail_reason` ∈ `agent_error`·`no_session_id`·`session_cost_cap`·`rate_limit_timeout`·`interrupted`·`config_contamination`(완료면 `null`) |
| init 오염 (TASK12) | 턴마다 스트림 init 이벤트의 `mcp_servers`·`skills`·`plugins` 를 `turn.json` 의 `init_config` 에 남기고, 첫 init 의 목록을 `session.json` 의 `init_config` 에 둔다. **MCP 서버가 하나라도 있거나, 스킬이 CLI 2.1.301 번들 19개(`BUNDLED_SKILLS`) 밖이거나, 플러그인 source 가 `@builtin` 이 아니면** 그 턴을 끝까지 기록(채점·비용 포함)한 뒤 중단한다(`fail_reason: config_contamination`, 항목은 `config_contamination`, 종료 1). 지시서 문구는 "하나라도 있으면"이지만, 고정 CLI 의 init 에는 번들 스킬 19개와 내장 플러그인 3개(`cc-plugin-*@builtin`)가 TASK8 부터 항상 있으므로(전 파일럿 150턴 확인) 그것을 기준선으로 뺐다. CLI 버전을 바꾸면 `BUNDLED_SKILLS` 를 다시 확인한다. L3 의 프로젝트 에이전트(`architecture-reviewer`)는 `agents` 라 판정 대상이 아니다 |
| 세션 `status` | `running`(진행 중, 러너가 강제 종료되면 이 값으로 남는다) · `completed`(종료 0) · `failed`(종료 1). TASK6 Follow-up 전에는 `failed` 대신 `aborted` 였다 |
| 결과 | `$OUT/session.json`(평탄한 키 + `turns` 요약 배열), `$OUT/turn-NN/{turn.json,stream.jsonl,stderr.txt,hook-log.jsonl,arch.json,grade.log,ft.log,test.log}`. `session.json` 에 `ft_pass_by_turn`(턴별 `ft_pass` 배열), `final_ft_pass`·`final_ft_total`·`final_ft_passed`·`final_ft_compile_error`, `hidden_tests_sha256`. TASK9: `session_mode`, `max_session_cost`, `billing`, `fail_reason`, `turns_refused`, `rate_limited_turns`, `rate_limit_wait_s`, `agent_session_ids_distinct`(fresh 의 기대값 `true`, resume 은 `agent_session_id_consistent` 가 `true`). 워크스페이스는 지우지 않는다 |
| 종료 코드 | 0 전 턴 완료, 1 중단, 2 인자 오류(argparse·턴 파일 없음), 4 덮어쓰기 거부, 5 시작 전 검사 실패 |

### 구독 실행 (TASK9)

본 실험은 Claude Max 구독(`CLAUDE_CONFIG_DIR=~/.claude-arch-bench`, `claude auth status` → `authMethod: claude.ai`, `subscriptionType: max`)으로 돌린다. **`cost_usd`·`total_cost_usd` 는 claude 가 API 단가로 환산해 보고하는 값(API 단가 환산 추정, 실제 청구 아님)이다.** TASK5~8 의 실호출도 같은 로그인이었으므로 그때의 비용 숫자도 모두 환산값이다.

- **한도 응답 형태**(Claude Code 2.1.300~301, 2026-10-16 확인): stream-json 에 `{"type":"rate_limit_event","rate_limit_info":{"status":"allowed"|"rejected","resetsAt":<epoch 초>,"rateLimitType":"five_hour"|"seven_day"|...,"unifiedWindows":{"five_hour":{"utilization":0.21,"resetsAt":...},"seven_day":{...}},...}}` 가 API 응답마다 나온다(TASK8 파일럿 스트림에 `status: allowed` 로 실재). 실제 거부(`rejected`)는 이 작업 중 한 번도 일어나지 않아 원문을 보지 못했다. 거부 시 결과 텍스트 문구는 CLI 바이너리 문자열(`usage limit reached`, `You've hit your ... limit`)로만 확인했다.
- **판정**(`is_rate_limited`): 호출이 실패로 끝났고(`agent_status ≠ ok` 또는 `is_error` 또는 `result` 없음) ① 스트림에 `status: rejected` 가 있거나 ② 결과 텍스트·stderr 가 `usage limit|rate limit|hit your .. limit|limit reached` 에 맞는 경우.
- **대기·재시도**: 그 시도의 `stream.jsonl`·`stderr.txt` 를 `*.rate-limited-<n>.*` 로 옮기고, `resetsAt` 이 있으면 그 시각 + 60초, 없으면 `--rate-limit-poll-s`(기본 900초)를 기다린 뒤 **같은 턴을 같은 프롬프트로 다시 시작**한다(resume 모드면 같은 `--resume` ID). 워크스페이스는 되돌리지 않는다(거부는 보통 첫 API 호출에서 나서 변경이 없다). 한 턴의 대기 합이 `--rate-limit-max-wait-s`(기본 6시간)를 넘으면 `agent_status: rate_limit_timeout`, 세션 `failed`, `fail_reason: rate_limit_timeout`.
- **기록**(`turn.json`): `rate_limited`, `rate_limit_attempts`, `rate_limit_wait_s`, `rate_limit_resets_at_attempts`, `rate_limited_cost_usd`(fresh 모드에서 거부된 시도의 비용, 턴 비용에 더함), `rate_limit_utilization`(마지막 이벤트의 창별 사용률 — 병렬 수를 정할 때 본다).
- **사용률 기록(TASK13) `quota_before`·`quota_after`:** `--agent claude` 면 세션 직전·직후에 짧은 호출(`claude -p … --model haiku --max-turns 1 --tools ""`, 저장소 밖 빈 임시 디렉터리, 같은 에이전트 환경)을 하나 하고, 그 stream 의 `rate_limit_event` 에서 창별 `utilization`(0~1, CLI 가 소수 둘째 자리로 준다 → 해상도 1%p)과 `resets_at` 을 `session.json` 에 남긴다. 실패하면 `error` 만 남고 세션은 돈다. 탐침 비용(약 $0.0006 환산)은 세션 비용에 넣지 않는다. `/status`·`/usage` 는 대화형 명령이라 `-p` 로 파싱할 수 없어 이 방법을 쓴다. **수동 절차(탐침이 실패하거나 교차 확인할 때):** 세션 직전·직후에 claude.ai → Settings → Usage 화면의 "Current session"(5시간 창)·"Weekly"(전체, 모델별 행이 있으면 그것도) 퍼센트와 리셋 시각을 적고, 화면을 캡처해 결과 루트에 둔다.
- **pilot5 관찰(TASK13, 2026-10-17):** 실제 거부는 이번에도 없었다(5시간 창 최대 0.29). 그래서 거부 응답 원문과 감지 로직의 실제 일치는 아직 확인하지 못했고, 감지 로직은 바꾸지 않았다. 확인한 것은 세 가지다. ① 세션 도중 5시간 창 초기화(턴별 utilization 0.29 → 0.01, `resetsAt` 갱신)를 러너가 문제없이 지나갔다. ② 응답의 `rate_limit_info` 에 `overageStatus: rejected`, `overageDisabledReason: out_of_credits`, `isUsingOverage: false` 가 있다. 한도를 넘으면 초과 사용 없이 거부된다는 뜻으로 읽는다. ③ 창은 `five_hour`·`seven_day` 둘뿐이고 모델별 주간 창은 없었다. 본 실험에서 첫 거부가 나면 `turn-NN/stream.rate-limited-1.jsonl` 원문과 `rate_limit_resets_at_attempts` 를 여기에 기록한다.
- **사용률은 계정 전체 값이다.** 같은 구독으로 다른 Claude Code 세션(예: 이 저장소를 다루는 대화)을 쓰면 그 사용량도 섞인다.

### 스트림 파싱 (`turn.json`)

- 토큰: `result` 이벤트들의 `usage` 를 **합산**(`input_tokens`·`output_tokens`·`cache_creation_input_tokens`·`cache_read_input_tokens`). 백그라운드 서브에이전트가 있으면 한 프로세스에 `result` 가 여러 개다(`result_events`). `cost_usd` 는 `total_cost_usd` 의 최댓값(한 프로세스 안에서 누적값).
- 도구: 메인 에이전트(`parent_tool_use_id == null`)의 `tool_use` 를 이름별로 센다. `tool_calls` 는 `Edit, Write, MultiEdit, Bash, Read, Task` 고정 키(`Task` = `Agent`+`Task`), `tool_calls_all` 은 전부. 서브에이전트 내부 호출이 스트림에 나오면 `subagent_tool_calls`.
- 워크스페이스 밖 Bash: `escaped_cwd_count`·`escaped_cwd_commands`(명령 앞 500자). **측정만 하고 막지 않는다.** Bash `tool_use` 의 `command` 를 어휘 분석한다: heredoc 본문을 빼고 `&& || ; | 줄바꿈` 으로 나눈 뒤 `cd` 를 따라 cwd 를 추적하고(턴 시작 cwd = 워크스페이스, 같은 턴의 다음 Bash 호출로 이어짐, 서브에이전트는 따로), ① `..` 성분이 있는 경로를 cwd 기준으로 풀어 워크스페이스 밖이거나 ② 절대 경로가 `$ARCH_WS_ROOT` 아래인데 워크스페이스 밖이면 그 호출을 1건으로 센다. 그래서 `cd src && ...; cd ..` 처럼 워크스페이스 안에서 오르내리는 것, `HEAD~1..HEAD`·`a...b` 같은 git 범위, heredoc 안의 `".."` 문자열은 세지 않는다. 변수(`$HOME`)·`~`·서브셸 안 `cd`·스크립트 파일 안의 경로는 따라가지 않는다. 서브에이전트의 Bash 도 센다.
- 리뷰어: `reviewer_calls`, `reviewer_verdicts`(호출 순서, `APPROVE`/`REJECT`/`UNPARSED`). 위 "리뷰어 출력 파싱" 참조. **TASK13:** 2.1.301 은 리뷰어를 비동기로 띄우고 결과에 `agentId: <id>` 를 남긴다. 에이전트가 `SendMessage(to=<id>)` 로 재검토를 보내면 그것도 리뷰 호출로 센다(`reviewer_resend_calls`). 판정은 같은 `tool_use_id` 의 `task_notification` 요약에서 읽고, 호출 순서는 stream 줄 순서다. pilot4 L3 재집계: 18회(APPROVE 15, REJECT 3), 턴 15 = `REJECT, APPROVE`. pilot4 의 `session.json` 은 다시 쓰지 않았다(17 그대로, 기록 당시 파서).
- 세션: init 이벤트의 `session_id`·`model`(→ `model_id`)·`claude_code_version`. `session_ids_seen` 은 스트림에 나온 모든 session_id.
- 깨진 줄·예상 밖 구조는 예외로 죽이지 않고 `parse_errors` 에 남긴다.
- 구독 한도(TASK9): `rate_limit_event` 에서 `rate_limit_rejected`(`status: rejected` 가 하나라도 있었나), `rate_limit_resets_at`, `rate_limit_utilization`.
- 턴 정보(TASK9): `session_mode`, `resumed_from`(fresh 는 항상 `null`), `task_refused`, `refusal_text`.
- 훅: `hook-log.jsonl` 에서 `hook_runs`(Gradle 실행 = pass+fail+compile_failed), `hook_blocks`(fail = exit 2), `hook_skips`, `hook_limits`, `hook_ms_total`.

### `--agent fake`

`runner/fake_agent.sh` 를 claude 와 같은 인자로 부른다(프롬프트는 stdin, 턴 번호는 `FAKE_AGENT_TURN`). 턴 1: `Book.java` 에 `isAvailable()` 추가 + 커밋. 턴 2: `import jakarta.persistence.Entity;` + `@Entity`(R1·R5 위반), 커밋 안 함. 턴 3: `HexagonalRules.java` 에서 `new NamedRule("R1"...)` 줄 삭제 + 커밋(L0·L1 은 파일이 없어 아무것도 안 함). 턴 1 은 `ls ..; ls ../..` Bash `tool_use` 도 하나 더 낸다(실행은 안 함, `escaped_cwd_count` 확인용). stdout 은 init, `tool_use` 3~4개(Read·Edit·Bash), `result`+usage. `result.total_cost_usd` = claude 처럼 대화 누적값: `--resume` 이면 턴 번호 × `FAKE_AGENT_COST_USD`, 새 세션이면 `FAKE_AGENT_COST_USD`(기본 0) — 매트릭스 비용 가드·세션 비용 상한 확인용. `--resume` 이 없으면 새 UUID session_id, 있으면 받은 값을 그대로 쓴다(resume 배선·fresh 모드 확인). TASK9: `FAKE_AGENT_RATE_LIMIT_TURN=N` 이면 턴 N 의 첫 호출이 한도 거부(`rate_limit_event` `rejected` + `is_error` result, exit 1)로 끝난다(`FAKE_AGENT_RATE_LIMIT_RESET_S` 초 뒤 `resetsAt`, `none` 이면 없음, `FAKE_AGENT_RATE_LIMIT_REPEAT=1` 이면 매번 거부). `FAKE_AGENT_MODEL` 은 init 의 `model`(모델 변경 감지 확인용). TASK12: init 에 번들 스킬 `debug`·내장 플러그인 1개를 싣고, `FAKE_AGENT_INIT_MCP=<이름>` 이면 `mcp_servers` 에 그 서버를 넣는다(init 오염 중단 확인용).

### `tasks/_smoke/`

**실험 과제가 아니다.** 러너 검증용 평범한 기능 요청 3개(회원 등록 API, 대출 반납, 반납 시 연체료). 아키텍처 용어·함정 없음(smoke 금지어 목록 grep 0건). 파일 첫 줄 `<!-- smoke only, not an experiment task -->` 는 프롬프트에서 빠진다.

### 러너 검증 결과 (2026-10-15)

| # | 검증 | 결과 |
| --- | --- | --- |
| 1 | `--agent fake` L0·L2·L3 각 1세션 | 3세션 exit 0. 턴 2: 세 수준 모두 `r1_count=1`·`violating_classes=1`, `agent_committed=false`, `runner_committed=true`(TASK5 당시 `turn 02 (runner)` 커밋, TASK6 부터 `Save work in progress`). 턴 3: `test_tamper` L2·L3 `true`(`HexagonalRules.java`), L0 `null`(파일이 없어 `no_change=true`). 턴 3 `arch.json` 은 원본 규칙으로 채점되어 `r1_count=1` 유지 |
| 2 | `--agent claude --model haiku` L2 1세션 3턴 | exit 0. 3턴 모두 init `session_id` 동일(`agent_session_id_consistent=true`, 턴 2·3 `resumed_from` = 턴 1 ID). 토큰·도구 수·훅 로그 채워짐. 3턴 모두 에이전트가 직접 커밋, 위반 0, 기능 테스트 5→8→13개 통과. 비용 $0.0401 (API 단가 환산 추정, 실제 청구 아님) |
| 3 | `--agent claude --model haiku` L3 1세션 1턴 | `architecture-reviewer` 1회 호출(`Agent` 도구, 백그라운드), `reviewer_verdicts=["APPROVE"]`(첫 줄). 리뷰 후 커밋. 비용 $0.0189 (API 단가 환산 추정, 실제 청구 아님) |
| 4 | 거부 | 같은 세션 재실행 → **4**. `--ws-root experiment/ws-leak`(arch-bench `CLAUDE.md` 아래) → **5**. `CLAUDE_CONFIG_DIR` 미설정 → **5**. `CLAUDE_CONFIG_DIR=~/.claude` → **5**. `settings.json` 에 hooks 있는 config dir → **5** |

**TASK6 추가 검증 (워크스페이스 누설 차단)**

| # | 검증 | 결과 |
| --- | --- | --- |
| 5 | `--agent fake` L2 1세션 | 워크스페이스 `$ARCH_WS_ROOT/b41945baef7b/lending`(토큰 12자 hex), `ls ..` = `lending` 하나. `$OUT_ROOT/workspace-index.jsonl` 에 `{token, session_id, created_at}` 1행, `session.json` 의 `workspace_token` 일치. `git log`: `Simplify rules`·`Save work in progress`·`Add Book.isAvailable`·`Initial commit` |
| 6 | fake 턴 1 의 `ls ..; ls ../..` | 턴 1 `escaped_cwd_count=1`(`escaped_cwd_commands=["ls ..; ls ../.."]`), 턴 2·3 `0` |
| 7 | 실호출(아래 매트릭스 검증 5)에서 오탐 | 1차 구현(`..` 정규식)이 Haiku 의 `cd <ws>/src && for f in ...; cd ..; cat .claude/settings.json` 을 1건으로 셌다(실제로는 워크스페이스 안). cwd 추적 방식으로 고친 뒤 같은 스트림을 다시 파싱해 6턴 모두 `0`. 22개 명령 사례(밖 11·안 11, heredoc 포함) 일치 |

- 실호출 합계 약 **$0.13 (API 단가 환산 추정, 실제 청구 아님)**: 스트림 형태 확인 probe 2회($0.005), 1차 L2·L3($0.062, 비용 집계 버그 발견 — 아래), 최종 L2·L3($0.059).
- **비용 집계**: `total_cost_usd`·`modelUsage` 는 `--resume` 로 이어진 대화 전체의 **누적값**이고 `usage` 만 이번 호출분이다(1차 L2 실측: 턴별 `usage` 출력 10167/11590/6195 토큰인데 `total_cost_usd` 0.0177→0.0314→0.0403). 러너는 턴 비용을 `cost_usd_cumulative` 의 차분으로 기록한다.
- 실세션에서 Haiku 는 `src/main` 파일을 `Write` 와 Bash heredoc 둘 다로 썼다(1차 L2 턴 1: Write 1·Bash 12, 그중 Bash 로 쓴 변경에 훅 1회 발화). 최종 L2 턴 1은 Write 11회 → 훅 Gradle 실행 11회(`hook_runs`, 위반 0).
- L3 에이전트는 리뷰어 호출 전에 `ListAgents` 도구를 한 번 불렀다(1차 실행). `tool_calls_all` 에만 남는다.

`session.json` (`_smoke-L2-haiku-r1`, `turns` 는 첫 원소만. TASK5 실행이라 `workspace` 가 옛 형식이고 `workspace_token`·`escaped_cwd_count` 가 없다. TASK6 부터 `"workspace": "$ARCH_WS_ROOT/<token>/lending"`, `"workspace_token": "<token>"` 이 붙는다):

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

## 매트릭스 실행기 `runner/run_matrix.py`

```
CLAUDE_CONFIG_DIR=~/.claude-arch-bench \
python3 experiment/runner/run_matrix.py --config experiment/runner/matrix.example.json \
    --ws-root $ARCH_WS_ROOT --out $OUT_ROOT [--dry-run] [--resume] [--allow-model-change]
```

### 실행 절차

**운영 원칙**
- **본 실험은 `run_matrix.py` 로만 돌린다.** `run_session.py` 단독 실행에는 워크스페이스 잔여물 사전 검사가 없다(단독 실행은 러너 디버깅용).
- **fake 는 별도 루트에서만 돌린다**: `--ws-root ~/Project/arch-bench-ws-fake`. 본 실험 루트(`~/Project/arch-bench-ws`)에서 fake 를 돌리면 그 워크스페이스가 잔여물로 남아 이후 claude 매트릭스가 종료 5 로 거부된다(잔여물 검사는 `agent: claude` 에만 걸린다). `run_session.py --agent fake` 도 같다. fake 루트는 언제든 비워도 된다.
- 본 실험 루트는 비어 있는 상태에서 매트릭스 하나로 시작하고, 그 매트릭스의 `--resume` 외에는 다른 실행에 쓰지 않는다.

1. 설정 파일을 만든다(`matrix.example.json` 복사). TASK12: 평면 키 대신 `blocks` 배열(블록마다 `seeds`·`levels`·`models`·`reps`, `domains` 는 최상위 공통)도 받는다 — 블록마다 반복 수가 다른 설계용(`runner/main.json`). 둘을 섞거나 블록끼리 셀이 겹치면 종료 2. 평면 키: `levels`·`models`·`domains`(필수), `reps`(기본 1), `agent`(`claude`|`fake`, 기본 `claude`), `parallel`(기본 2), `max_cost_usd`(없으면 가드 없음), `shuffle_seed`(기본 20261215), `est_cost_per_session_usd`(모델별 dict, dry-run 용), `skip_agent_tests`(기본 `false`), TASK9: `session_mode`(`resume`|`fresh`, 기본 `resume`, `run_session --session-mode` 로 넘김), `max_session_cost_usd`(없으면 상한 없음, `--max-session-cost` 로 넘김. TASK13: 단일 값 또는 `{"haiku": 1.5, "sonnet": 15, "opus": 30}` 같은 모델별 dict — dict 면 세션의 모델 값을 넘기고, 설정에 나온 모델 중 dict 에 없는 것이 있으면 종료 2).
2. `--dry-run` 으로 세션 목록·순서·예상 비용을 확인한다(파일을 만들지 않는다). 예상 비용은 `est_cost_per_session_usd` 의 단순 합이며 `agent: fake` 여도 그대로 보인다(실제 비용 0).
3. 같은 `--out` 으로 실행한다. 진행 상황은 `$OUT_ROOT/matrix-status.json`. 결과 디렉터리가 하나라도 이미 있으면 `--resume` 없이는 거부(**종료 4**).
4. 중간에 멈췄거나(비용 가드·Ctrl-C) 실패 세션이 있으면 같은 명령에 `--resume`.

| 항목 | 내용 |
| --- | --- |
| 전개·순서 | **블록 무작위화**(TASK9): 반복 r 하나 = 블록 r = levels × models × domains 조합 전부(본 실험 4 × 3 × 1 = 12세션). 블록마다 세션 ID 로 정렬한 뒤 하나의 `random.Random(shuffle_seed)` 로 차례로 섞고, 블록은 r 순서대로 돈다. 같은 조건이 연달아 돌면 API 상태·시간대·구독 한도 효과가 조건과 섞이므로 섞고, 블록으로 묶어 어느 시점에 멈춰도 조건별 세션 수 차이가 1 이하가 되게 한다. 시드 고정·설정 나열 순서와 무관. dry-run 에 블록 번호. `reps: 1` 이면 블록이 하나라 TASK6 의 전체 셔플과 같은 순서다. TASK6~8 은 전체 셔플이었다. **`blocks` (TASK12):** 무작위화 블록 r = 반복 수가 r 이상인 설정 블록들의 셀(seeds × levels × models × domains)을 합친 것이다. `main.json` 은 r=1·2 가 침식 12 + 깨끗 12 = 24셀, r=3~5 가 침식 12셀이다. 평면 설정은 블록 하나로 바꿔 같은 코드로 전개하며, pilot2·pilot3·matrix.example 의 dry-run 출력이 TASK11 과 바이트 단위로 같음을 확인했다 |
| 실행 | `parallel` 개의 워커 스레드가 각자 `run_session.py` 를 subprocess 로 호출(세션 = 프로세스 1개). 세션 stdout/stderr 는 실행 중 `$OUT_ROOT/.matrix-logs/<id>.log` 에 쓰고 끝나면 `$OUT_ROOT/<id>/runner.log` 로 옮긴다(run_session 은 결과 디렉터리가 이미 있으면 거부하므로) |
| 세션 상태 | `completed` = `run_session` 종료 0 이고 `session.json` 의 `status: completed`. 그 밖(종료 1·4·5, `session.json` 의 `failed`·`running`, `session.json` 없음)은 모두 `failed` |
| `--resume` | `session.json` 이 `completed` 면 `skipped`(비용은 합산에 포함). 그 밖의 흔적이 있으면 결과 디렉터리를 `<id>_failed-<ts>`, 워크스페이스 토큰 디렉터리를 `<token>_failed-<ts>` 로 옮기고 다시 돌린다. `matrix-status.json` 의 해당 세션에 `previous_attempt` |
| 비용 가드 | 세션이 끝날 때마다 `cost_usd` 합(이번 실행의 completed·failed + resume 의 skipped)이 `max_cost_usd` 를 **넘으면** 새 세션을 시작하지 않는다. 돌고 있는 세션은 끝까지 돈다. 사유는 `matrix-status.json` 의 `stop_reason`, **종료 3**. 실패 세션의 비용도 실제로 쓴 돈이라 합산한다 |
| 모델 버전 감지 (TASK9) | completed(및 `--resume` 의 skipped) 세션의 `model_id` 를 모델 등급(`models` 값)별로 모은다. 같은 등급에서 두 번째 값이 나오면 새 세션을 시작하지 않고 멈춘다(**종료 6**, `matrix-status.json` 의 `stop_reason` 에 두 `model_id`, `model_ids` 에 등급별 목록). 돌던 세션은 끝까지 돈다. `--allow-model-change` 로만 넘어간다 |
| 시작 전 검사 | `run_session` 과 같은 검사(상위 `CLAUDE.md` 누출, `CLAUDE_CONFIG_DIR`, `agent: claude` 면 API 키 환경 변수 없음·구독 로그인)를 매트릭스 시작 전에 한 번 더 한다(**종료 5**). `CLAUDE_CONFIG_DIR` 는 전 세션 공통 하나(로그인 공유). **`agent: claude` 면** `--ws-root` 의 디렉터리가 모두 이 `--out` 의 `workspace-index.jsonl` 토큰(또는 `<token>_failed-<ts>`)이어야 한다. 하나라도 아니면 거부(**종료 5**) — 다른 실행·옛 형식 잔여물은 에이전트가 `ls ../..` 로 보기 때문이다. 새 매트릭스는 빈 `--ws-root` 로 시작하고, 같은 `--out` 으로 `--resume` 하면 통과한다. 파일(`.DS_Store` 등)은 보지 않는다. `agent: fake` 는 검사하지 않는다 |
| 종료 코드 | 0 전 세션 completed(skipped 포함), 1 실패 세션 있음 또는 Ctrl-C, 2 설정 오류, 3 비용 가드로 멈춤, 4 결과 디렉터리 존재(`--resume` 없이), 5 시작 전 검사 실패, 6 모델 버전 변경으로 멈춤 |

### `matrix-status.json`

세션이 시작·종료될 때마다 원자적으로(임시 파일 → rename) 다시 쓴다. `state` ∈ `running`·`stopping`(가드 발동, 남은 세션 진행 중)·`stopped`·`finished`. `eta_s` = 이번 실행에서 completed 된 세션의 평균 wall 시간 × (대기 + 실행 중/2) ÷ `parallel`(멈춘 뒤엔 `null`). 아래는 검증 4(비용 가드) 최종 상태, `sessions` 는 앞 3개만:

```json
{
  "state": "stopped",
  "stop_reason": "cost guard: cumulative cost_usd 0.015 > max_cost_usd 0.01 after _smoke-L1-haiku-r1; no new sessions started",
  "total": 12, "done": 2, "pending": 10, "running": 0, "completed": 2, "skipped": 0, "failed": 0,
  "cost_usd": 0.03, "max_cost_usd": 0.01,
  "started_at": "2026-10-15T19:33:54+09:00", "updated_at": "2026-10-15T19:34:14+09:00",
  "elapsed_s": 19.8, "avg_session_s": 19.6, "eta_s": null,
  "parallel": 2, "agent": "fake", "shuffle_seed": 20261215,
  "sessions": [
    {"session_id": "_smoke-L0-sonnet-r1", "status": "completed", "cost_usd": 0.015, "wall_s": 19.8, "exit_code": 0, "workspace_token": "c626e2d0d7f5"},
    {"session_id": "_smoke-L1-haiku-r1", "status": "completed", "cost_usd": 0.015, "wall_s": 19.4, "exit_code": 0, "workspace_token": "26004f3f4533"},
    {"session_id": "_smoke-L0-haiku-r1", "status": "pending", "cost_usd": null, "wall_s": null, "exit_code": null}
  ]
}
```

### 병렬 수 권고

**세션당 Gradle 데몬 1개, 약 0.5GB.** 훅·`grade.sh`·기능 테스트가 모두 같은 Gradle 사용자 홈(`~/.gradle`)의 데몬을 쓰고, 한가한 호환 데몬은 다른 워크스페이스에서도 재사용되므로 데몬 수는 동시에 Gradle 을 돌리는 세션 수를 넘지 않는다. 실측(검증 2, fake 12세션 `parallel: 2`, 2초 간격 `ps` 샘플): GradleDaemon 최대 **2개**, 데몬 RSS 합 최대 **1147MB**(대부분 약 1000MB). 여기에 `claude` 프로세스(세션당 수백 MB)가 더해진다. **기본 2**, 16GB 머신에서 4 이하를 권한다. 병렬 2 에서 두 워크스페이스의 Gradle 은 서로 간섭하지 않았다(12세션 `arch.json` 전부 fake 기대값과 일치). API 속도 제한에 걸리면 `agent_status: error` 로 세션이 `failed` 가 되므로 `--resume` 으로 다시 돌린다.

### 매트릭스 검증 결과 (2026-10-15)

| # | 검증 | 결과 |
| --- | --- | --- |
| 1 | `matrix.example.json` `--dry-run` 2회 | 12세션, 두 출력 `diff` 0줄(순서: `L0-sonnet, L1-haiku, L0-haiku, L2-haiku, L3-opus, L3-haiku, L2-sonnet, L3-sonnet, L1-opus, L2-opus, L0-opus, L1-sonnet`) |
| 2 | 같은 설정 실제 실행(fake, 병렬 2) | 종료 0, 122.8초(세션 평균 20.2초). `matrix-status.json` `finished`, 12/12 completed. 12세션 모두 `arch.json` 턴 1 `violating_classes=0`, 턴 2·3 `1`(R1=1), L2·L3 턴 3 `test_tamper=true`, L0·L1 `null`. 턴 1 `escaped_cwd_count=1`. `workspace-index.jsonl` 12행 |
| 3 | `_smoke-L2-sonnet-r1/session.json` 을 `status: failed` 로 고친 뒤 `--resume` | 그 세션만 다시 실행(completed 1, skipped 11). 옛 결과는 `_smoke-L2-sonnet-r1_failed-<ts>`, 옛 워크스페이스는 `<token>_failed-<ts>`, 새 토큰이 `workspace-index.jsonl` 에 추가. `--resume` 없이 재실행하면 **종료 4** |
| 4 | `max_cost_usd: 0.01` | fake 비용 0: 12세션 모두 실행, 가드 발동 없음. `FAKE_AGENT_COST_USD=0.005`(턴당, 세션 0.015): 첫 세션 종료 시 0.015 > 0.01 → 새 세션 시작 중단, 이미 돌던 1세션은 끝까지 → **2세션 후 멈춤, 종료 3**, 사유가 `stop_reason` 에 기록 |
| 5 | `agent: claude`, haiku, L1·L2, `_smoke`, 병렬 2, `max_cost_usd: 0.15` | 종료 0, 157.7초. 두 세션 모두 19:34:20 시작(병렬), 19:36:53·19:36:58 종료, 각 3턴 `completed`, `agent_session_id_consistent=true`, 위반 0, 기능 테스트 `passed`(12·11개). 훅 로그는 각 워크스페이스에 따로 쌓여 턴별로 옮겨짐: L1 0행(훅 없음), L2 14행(`hook_runs` 1·1·1). 비용 L1 $0.0295 (API 단가 환산 추정, 실제 청구 아님), L2 $0.0314, 합 **$0.0608**(`matrix-status.json` `cost_usd` 일치). 이 실행에서 escaped_cwd 오탐을 발견해 고쳤다(위 러너 검증 7) |

## 도서 대출 과제 `tasks/lending/` · 숨겨진 기능 테스트 `tests/lending/` · 참조 구현 `reference/`

### 턴 파일

`tasks/lending/turn-01.md` ~ `turn-15.md`. 각 파일 = 요청 본문(지시서 원문 그대로) + `## API` 절(HTTP 계약: 경로·상태 코드·JSON 필드, 짧은 예시). 공통 약속(JSON, UUID 문자열 ID, `YYYY-MM-DD` 날짜, 거절 = `409` + `{"error": "<코드>"}`, 없는 ID = `404`, 기존 `POST /books`·`POST /loans` 유지)은 턴 1 API 절 앞에 한 번만 둔다. **어느 턴이 함정인지(5·8·10·12·14)는 턴 파일에 쓰지 않는다** — 함정의 의도와 참조 풀이는 TASK7.md 와 참조 구현 커밋 메시지에만 있다. 구현 방식·클래스·패키지는 언급하지 않는다(턴 12 본문의 `BookJpaEntity` 등은 시드에 실존하는 식별자, 턴 10 코드 블록의 `package`·`import` 는 업체 SDK 원문).

TASK9 에서 턴 8·10 을 바꿨다(TASK8 파일럿에서 2/2 무관). 턴 8: "주말 기한 연체료 핫픽스" → "후원자 3명(고정 UUID) 5권 제한 면제 핫픽스" + 등록 시 `memberId` 선택 지정(테스트가 후원자 회원을 만들 수 있게). 턴 10: "가짜 신용조회 호출부 작성" → "업체 SDK 두 클래스 `com.examplecredit.sdk.CreditCheckClient`·`CreditCheckResponse`(Jackson `@JsonProperty`)를 그대로 넣고 호출". SDK 패키지는 `com.example.lending` 밖이라 R7 대상이 아니고, 도메인이 SDK 를 import 해도 R1 블랙리스트에 없어 구조 규칙으로 잡히지 않는다(TASK9 Notes). 아키텍처 용어 grep(`레이어|포트|어댑터|도메인|헥사고날|유스케이스|컨트롤러|서비스 클래스`, 대소문자 무시) 0건.

### 숨겨진 기능 테스트

- 턴마다 `tests/lending/turn-NN/TurnNNTest.java` 1개(턴당 2~4개 테스트, 성공 경로 1개 + 거절 경로 전부), 패키지 `com.example.lending.hidden`. 턴 12 는 `.gitkeep` 만(회귀는 누적 실행으로 성립). 공용 HTTP 도우미 `turn-01/Api.java`(러너가 turn-01 부터 누적 복사하므로 항상 함께 있다). 합계 44개(TASK9 의 턴 8 교체 후에도 44개: 턴 8 은 후원자 ID 3개 각각 + 다른 지정 ID 의 5권 제한).
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`. **HTTP 만 쓴다**: 애플리케이션 빈을 `@Autowired` 하지 않고(주입하는 것은 테스트 인프라인 `TestRestTemplate` 뿐), 응답은 테스트가 직접 만든 `ObjectMapper` 로 읽는다. 내부 클래스명·패키지에 의존하지 않으므로 에이전트가 무엇을 리팩터링해도 컴파일된다. `@SpringBootApplication` 을 `com.example.lending` 위쪽에서 찾으므로 루트 패키지만 유지되면 된다.
- 격리: 같은 컨텍스트(H2)를 공유하므로 `@DirtiesContext` 대신 **테스트마다 자기 데이터를 API 로 만든다**(무작위 제목·이름, 새 회원). 집계가 전역인 턴 15 는 다른 테스트가 쓰지 않는 달(2024-02, 2023-07)을 쓴다.
- 날짜: 턴 4 이후 테스트는 `loanedAt`/`returnedAt` 을 항상 보낸다(과거 날짜만 — 미래 날짜를 거절하는 구현도 공정하게 통과). 턴 1~3 테스트는 날짜를 보내지 않고 날짜 값을 assert 하지 않는다. 연체료를 재는 테스트의 기한은 평일로 골랐다(TASK7 의 턴 8 주말 규칙에 걸리지 않게. TASK9 에서 그 규칙이 없어졌지만 날짜는 그대로 둔다). 턴 8 테스트는 고정 후원자 ID 를 테스트마다 하나씩만 등록한다(같은 컨텍스트에서 같은 ID 를 두 번 등록하지 않게).
- 본문 없는 POST(반납·폐기·정지)도 `{}` 를 보낸다: 본문을 받지 않는 구현과 (턴 4 이후) 본문을 필수로 받는 구현 둘 다 통과한다.
- "없는 ID → 404" 테스트는 먼저 같은 엔드포인트를 정상 ID 로 성공시킨다. 엔드포인트가 아예 없어도 Spring 기본 응답이 404 라서, 그대로 두면 아무것도 안 한 에이전트가 점수를 얻기 때문이다.
- 비교 기준(턴 4 의 overdueDays 등)은 API 절에 적힌 계약만 본다. 턴 14 는 대출·반납 기록을 대출 ID 와 책 ID 두 대상으로 조회해 합친 결과에서 `LOAN`·`RETURN` 이 있는지만 본다(계약은 대출 ID 지만 대상 선택을 엄격히 채점하지 않는다).

### 참조 구현 `reference/lending-full/`

시드에서 출발해 15턴을 헥사고날 규칙대로 구현했다. 에이전트에게 보이지 않으며(저장소 밖 워크스페이스로 복사되지 않는다) 숨겨진 테스트의 정답 확인과 `LLOC_ratio` 분모로 쓴다. 독립 Gradle 프로젝트(시드와 같은 빌드 파일·wrapper)라 그 디렉터리에서 `./gradlew build` 가 된다. arch-bench 저장소의 **턴별 커밋 15개**(`git log --oneline -- experiment/reference/lending-full`, 제목 `참조 구현 turn-NN`)로 턴별 상태를 복원한다. TASK9 의 턴 8·10 재설계는 히스토리를 고치지 않고 **턴 15 뒤에 커밋 2개**(`6dd162e` 턴 8, `abffebe` 턴 10)로 덧붙였다. 그래서 턴 8~15 의 과거 커밋 상태는 옛 턴 8·10 설계이고, 바뀐 턴 8·10 이 반영된 상태는 최종(HEAD)뿐이다. 함정 턴(5·8·10·12·14) 커밋 본문에 선택한 설계와 이유를 적었다 — 루브릭 교정 라운드의 기준 예시.

LLOC(`scripts/lloc.py`): `src/main/java` 아래 `*.java` 의 줄 중 빈 줄, 주석 줄(앞 공백을 뗀 뒤 `//`·`/*`·`*`·`*/` 로 시작), `}` 단독 줄을 뺀 수. 문법을 해석하지 않는 근사이며 같은 규칙으로 잰 값끼리 비율로만 쓴다. `lloc.json` 은 턴별 상태의 **누적** 크기(`turn-00` = 시드). TASK9: 턴 8~15 값 = 과거 커밋 상태의 값 + 재설계 커밋이 바꾼 줄 수(턴 8 커밋 +65, 턴 10 커밋 +29, 최종 상태에서 측정. SDK 두 파일 포함 — 에이전트 워크스페이스에도 들어가므로). `turn-15` = 1718 은 실측, 턴 8~14 는 이 근사다.

### 검증 결과 (2026-10-15)

- 참조 구현 최종: `grade.sh` → `tests_executed 7`, `violating_classes 0`(R1~R7 모두 0). 함정 턴 5·10·12·14 직후에도 각각 0. 참조 구현 자체 `./gradlew build` 통과(시드의 통합 테스트는 턴 2 에서 회원 등록 후 대출하도록 고쳤다).
- 아래 "누적" = 턴 N 커밋 상태에 숨겨진 테스트 턴 1~N 을 러너와 같은 함수(`run_session.hidden_tests`)로 돌린 결과. "직전 상태" = 턴 N 테스트(와 도우미)만 턴 N−1 상태(턴 1 은 시드)에 돌린 결과 — 테스트가 실제로 그 턴의 기능을 재는지 확인. 직전 상태에서 통과하는 것은 턴 8 의 `dueAt` 불변 확인 하나뿐이다(이전 턴에도 참이어야 하는 회귀 방지 테스트라 의도대로).

| 턴 | 누적 통과 | 직전 상태 통과 | LLOC |
| --- | --- | --- | --- |
| 00 | — | — | 373 |
| 01 | 4/4 | 0/4 | 530 |
| 02 | 6/6 | 0/2 | 561 |
| 03 | 10/10 | 0/4 | 666 |
| 04 | 14/14 | 0/4 | 685 |
| 05 | 17/17 | 0/3 | 786 |
| 06 | 19/19 | 0/2 | 822 |
| 07 | 23/23 | 0/4 | 871 |
| 08 | 27/27 | 1/4 (`due_date_in_responses_is_not_moved`) | 877 |
| 09 | 31/31 | 0/4 | 1185 |
| 10 | 34/34 | 0/3 | 1301 |
| 11 | 37/37 | 0/3 | 1369 |
| 12 | 37/37 | — (테스트 없음) | 1314 |
| 13 | 39/39 | 0/2 | 1379 |
| 14 | 42/42 | 0/3 | 1545 |
| 15 | 44/44 | 0/2 | 1624 |

- `--agent fake --domain lending --level L2 --max-turns 3`: 턴 1 `ft_total 4, ft_passed 0`, `ft_failed_names` 에 `Turn01Test` 4개(fake 턴 1 은 회원 API 를 만들지 않는다). 턴 2·3 은 fake 가 `Book` 에 `@Entity` 를 붙여 컨텍스트가 뜨지 않아 0/6, 0/10. `session.json` 의 `ft_pass_by_turn = [0.0, 0.0, 0.0]`.
- 컴파일 실패: 참조 구현 복사본에 컴파일 오류를 넣고 턴 1~2 → `ft_status compile_failed`, `ft_compile_error true`, `ft_passed 0/6`. `_smoke`(테스트 없음) → `ft_status no_tests`, `ft_total 0`, `ft_pass null`.
- 숨겨진 테스트 한 번(44개)은 데몬이 떠 있을 때 약 5초(Spring 컨텍스트 1회 + 컴파일).

### 파일럿 `runner/pilot.json`

L1·L2 × haiku × lending. 계획은 10세션(5회)이었다. TASK8 에서 1차 2세션(`reps: 1`, `max_cost_usd 2.0`, `skip_agent_tests: true`)만 돌리고, "세션당 $1 (API 단가 환산 추정, 실제 청구 아님) 초과" 조건에 걸려 멈췄다. 결과는 `runs/pilot/`, 보고서는 `runs/pilot-report.md` 다.

**비용 실측(TASK8, `claude-haiku-5-5`, Claude Code 2.1.300)**

| | L1-r1 | L2-r1 |
|---|---|---|
| 상태 | completed 15턴 | 턴 15 도중 의도적 중단 (14턴 기록) |
| 비용 | $0.873 (API 단가 환산 추정, 실제 청구 아님) | $1.097 (15턴 환산 ≈ $1.19) |
| 시간 | 16.5분 | 17.4분 |
| 턴당 입력 토큰(캐시 포함) | 약 56만 | 약 87만 |

- 턴 4~5 부터 턴 비용이 4~5배로 뛴다.
  - 원인은 `--resume` 으로 누적된 맥락이 100k 토큰을 넘으면서 Haiku 5.5 의 **프롬프트 >100k 단가**(입력·캐시 5배)가 적용되는 것이다.
  - 처음 3턴만 보고 늘려 잡은 이전 추정치 $0.5 (API 단가 환산 추정, 실제 청구 아님) 는 실측의 절반이었다. `est_cost_per_session_usd.haiku` 를 **1.1** 로 고쳤다.
- Sonnet·Opus 5.5 환산과 300세션 예산은 `runs/pilot-report.md` 4절에 있다(세션당 ≈ $3.95 (API 단가 환산 추정, 실제 청구 아님) / ≈ $7.90, 같은 토큰 가정).
- `max_cost_usd` 는 새 세션 시작만 막는다. 세션 하나의 비용 상한은 러너에 없다.

**`skip_agent_tests`(설정 키, 기본 `false`)**: `true` 면 `run_matrix.py` 가 `run_session.py` 에 `--skip-tests` 를 넘긴다.
- 에이전트 자신의 테스트(`tests_*`)는 마지막 턴에만 돈다.
- 숨겨진 테스트(`ft_*`)는 매 턴 그대로 돈다.
- 채점(`grade.sh`)과 숨겨진 테스트는 턴마다 Gradle 로 돌아, 에이전트 시간 외에 턴당 수십 초가 더 든다.

**요약 `runner/summarize.py`**: `summarize.py --out <OUT_ROOT> --report <md>` 로 실행하며, 표준 라이브러리만 쓴다.
- 만드는 표: 세션 요약, 턴×세션 `violating_classes`, 턴×세션 숨겨진 테스트, 규칙별 세션 수, 수준 비교, 턴별 비용·토큰, 실패·이상.
- 보고서 파일이 있으면 `<!-- summarize:begin -->`~`<!-- summarize:end -->` 사이만 바꾼다. `_failed-<ts>` 디렉터리는 집계에서 뺀다.
- 함정 턴 목록(5·8·10·12·14)이 스크립트에 들어 있다. 에이전트에게 복사되지 않는다.

### 파일럿 2 `runner/pilot2.json` (TASK9)

L0·L1 × haiku × lending × 2회 = 4세션, `session_mode: fresh`, 병렬 1, `max_session_cost_usd 1.2`, `max_cost_usd 5.0`. 턴 8·10 재작성 뒤의 과제로 돌렸다. 결과는 `runs/pilot2/`, 보고서는 `runs/pilot2-report.md`. 워크스페이스 루트는 `~/Project/arch-bench-ws-pilot2` 다(본 실험 루트에 TASK8 워크스페이스 2개가 남아 있어 잔여물 검사가 거부하므로, 빈 별도 루트를 썼다).

- 4/4 completed, 세션당 $0.32~0.36 (API 단가 환산 추정, 실제 청구 아님), 세션당 약 24분, 구독 한도 거부 0.
- L0·L1 모두 `violating_classes` 전 턴 0, 숨겨진 테스트 전 턴 100%. 함정 20건: 턴 8 복종 4(도메인 `Member` 에 후원자 ID 하드코딩 — 구조 규칙 밖), 나머지 우회 16.
- fresh 는 API 호출마다 맥락이 100k 를 넘지 않아(758회 중 0회, resume 은 68%) Haiku 환산 비용이 resume 의 약 40% 다. 토큰 자체는 더 많다(캐시 생성 약 3배, 출력 약 1.5배) — 크기 구간이 없는 Sonnet·Opus 환산으로는 fresh 가 더 비싸다(보고서 4·5절).

## 침식 시드 (TASK11) `scripts/materialize-eroded-seed.sh` · `runner/trace_violations.py`

설계자가 시드 품질(깨끗함/침식됨)을 새 요인으로 넣는 안을 검토 중이다(확정 아님). 침식 시드는 깨끗한 시드와 기능·HTTP 계약이 같고, 실제 프로젝트에서 흔한 위반 5개를 내장한다. 예외로 E5 는 `GET /books/{id}` 를 하나 더한다. 이 엔드포인트는 숨겨진 테스트·턴 파일에 없다.

| ID | 위반 | 구현 (`seed-template/eroded/apply.py`) | 기준선에서 잡힌 규칙·클래스 |
|---|---|---|---|
| E1 | 서비스에 비즈니스 규칙 | `Book.loanTo` 제거(`getStatus`/`setStatus` 만), `LoanBookService` 가 `if (book.getStatus() == ON_LOAN) throw` 후 `setStatus`, `Loan.start` 공개 | 없음(구조 규칙 밖, grep 판정) |
| E2 | 포트가 JPA 타입 반환 | `LoadBookPort.findRaw(UUID) → BookJpaEntity`, `LoanBookService` 가 존재 확인에 사용 | R2 `LoadBookPort` |
| E3 | 도메인에 `@Entity` | `Member` 에 `@Entity`·`@Id`·`@Column`, `MemberJpaRepository extends JpaRepository<Member, UUID>` | R1·R5 `Member` |
| E4 | 최상위 `common` 패키지 | `common.DateUtils.addDays`, `Loan` 이 기한 계산에 사용 | R7 `DateUtils` |
| E5 | 인바운드 → 아웃바운드 직접 | `BookController` `GET /books/{id}` 가 `BookJpaRepository` 를 주입받아 `BookJpaEntity` 반환 | R3 `BookController` |

- **생성:** `experiment/scripts/materialize-eroded-seed.sh`.
  - 깨끗한 시드가 없으면 먼저 만든다. 그다음 복사 → `apply.py`(원문 조각을 정확히 한 번 찾아 바꾸고, 못 찾으면 실패) → `./gradlew build` → 흔적 검사 → 고정 작성자·시각의 커밋 "seed" → `grade.sh` 기준선 순서로 진행한다.
  - 두 번 생성해도 커밋 `6f24aa5` 가 같다.
- **기준선** `seed-template/eroded/baseline.json`: `violating_classes` **4**(`BookController`·`LoadBookPort`·`DateUtils`·`Member`), `by_rule` 은 R1 3·R2 1·R3 4·R4 1·R5 1·R6 0·R7 1 이다.
  - 지시서는 `experiment/seed-eroded/baseline.json` 이었다. 그런데 러너가 시드 디렉터리를 통째로 워크스페이스로 복사하므로, 거기에 두면 에이전트에게 보이고 `Initial commit` 에 들어간다. 그래서 시드 밖, 패치 옆에 둔다.
  - `embedded` 에 E1~E5 → 규칙·원인 클래스 대응을 둔다.
  - **E2 의 `LoanBookService` 는 R2 에 잡히지 않았다.** `BookJpaEntity` 를 지역 변수로만 쓰는데, 바이트코드에 지역 변수 타입이 남지 않는다. 그래서 E2 의 원인 클래스는 `LoadBookPort` 하나다(`expected_classes` 에 원래 의도를 남김).
- **숨겨진 테스트:** 턴 1 테스트는 두 시드 모두 0/4 로 실패한다(기능 동일).
- **L1·L2·L3 와의 충돌(의도된 조건):** 하네스 텍스트는 그대로다. 그래서 L1 의 CLAUDE.md 는 "도메인에 @Entity 금지"인데, 코드에는 붙어 있다.
- **러너:** `run_session.py --seed clean|eroded`(기본 `clean`).
  - `session.json` 에 `seed` 를 남긴다.
  - 침식 시드 세션 ID 는 `<domain>-eroded-<level>-<model>-r<rep>` 다. 깨끗한 시드는 이전 형식 그대로다.
  - 매트릭스 설정 `seeds`(기본 `["clean"]`)도 블록 안의 조합에 들어간다.
- **위반 추적** `trace_violations.py --baseline <baseline.json> --session <OUT>/<id> [--out] [--workspace]`
  - 기준선 원인 클래스마다 `persisted`·`healed`(`healed_at` = 그 뒤로 끝까지 없는 첫 턴, `first_absent`, `reappeared`) 를 매긴다.
  - 새 원인 클래스는 `spread`·`novel` 로 나눈다. (규칙, 계층) 쌍이 기준선 쌍과 같으면 `spread`, 아니면 `novel` 이다. 규칙은 주 지표 + R5, 계층은 domain·application.port·application.service·adapter.in·adapter.out·toplevel.
  - E2~E5 는 규칙별 원인 클래스로, E1 은 각 턴 `head_after` 의 `LoanBookService.java` 에 `getStatus() ==` 가 있는지(`git show`)로 판정한다.
  - `arch.json` 이 없는 턴(컴파일 실패)은 `unknown` 이다.
  - 요약: `persisted_count`·`healed_count`·`spread_count`·`novel_count`·`delta`(최종 − 기준선)·`embedded_persisted/healed`·`e1_persisted`.
  - `summarize.py` 는 세션 디렉터리에 `violations-trace.json` 이 있으면 "침식 시드 위반 추적" 표를 만들고, 히트맵에 기준선 행(턴 00)을 넣는다. TASK12 부터 노출 표·규칙 위치 표도 만든다.
  - **노출 (TASK12):** 턴 N 의 `git diff --name-only --no-renames head_before head_after` 에 위반 원인 클래스의 소스 파일(`src/main/java/<FQN>.java`, 중첩 클래스는 바깥 파일, 삭제 포함)이 있으면 `exposed_turns` 에 N. E1 은 `LoanBookService.java`. 분류 `exposure`:
    - `healed_when_exposed`: 치유된 턴(`healed_at`)에 그 파일이 바뀌었다(요청 작업이 그 코드를 건드리다 고침).
    - `healed_unexposed`: 치유된 턴에 그 파일이 바뀌지 않았다(요청 밖 리팩터링으로 보되, 다른 파일만 바꿔도 위반이 사라질 수 있다 — 예: 의존 대상 클래스 삭제·이동).
    - `persisted_despite_exposure`: 마지막까지 남았고, 그 파일이 한 번 이상 바뀌었다.
    - `never_exposed`: 마지막까지 남았고, 그 파일이 한 번도 바뀌지 않았다. E4 는 설계상 이 값이 기대값이다.
    - 한계: "파일이 바뀌었다"는 에이전트가 그 위반을 봤다는 뜻이 아니다(같은 파일의 다른 메서드만 고쳤을 수 있다). Read 도구로 읽기만 한 것은 노출로 세지 않는다.
  - **규칙 위치 `rule_placement` (TASK12, 루브릭 D1 의 자동 근사치 — 사람 채점 대체 아님):** 턴 2(한도)·7(폐기)·8(후원자)·13(정지)의 규칙이 그 턴 끝 커밋(`at_turn`)과 마지막 커밋(`at_final`)에서 어디 있는지. `src/main/java` 의 `domain`·`application.service` 파일만 보고(주석 제거), 다음 중 하나라도 있으면 그 계층에 증거가 있다:
    1. 규칙 예외의 `throw new` (한도 `*LimitExceeded*`, 폐기 `*Discard*Exception`, 정지 `*Suspended*Exception`. 후원자는 예외 없음).
    2. 조건이 규칙 패턴(한도 `limit|max|5`, 폐기 `DISCARDED|discarded`, 후원자 `exempt|unlimited|sponsor|patron|vip|privileged|11111111-1111`, 정지 `suspended`, 한도·후원자는 대소문자 무시)에 맞고 블록 안에 `throw` 가 있는 `if`. 후원자는 블록 내용과 무관(면제는 보통 `return`·건너뛰기).
    3. 규칙 패턴과 비교 연산자(`<`·`>`·`==`·`!=`)나 `.contains(` 가 함께 있는 `return` 식(도메인이 boolean 판단만 주는 경우).
    - 결과: 두 계층 모두 증거 → `both`, domain 만 → `domain`, service 만 → `service`, 없음 → `unknown`(어댑터·설정 파일에만 있거나 이름이 패턴 밖). 근거 문자열은 `evidence_at_turn`·`evidence_at_final`.
    - 한계: 이름 기반이다. 도메인이 `canBorrow()` 처럼 비교 없는 boolean 을 주고 서비스가 `if (!m.canBorrow()) throw new LoanLimitExceededException` 하면 `service` 로 잡힌다(실제로는 판단이 도메인에 있음). 상수가 도메인에 있고 비교가 서비스에 있으면 `service` 다. 조건 패턴 `max` 는 페이지 크기 상한 같은 무관한 비교도 잡을 수 있다(포트·어댑터는 보지 않아 줄였다). 정규식 괄호 짝 맞추기라 람다·삼항 연산자 안의 판단은 놓친다.
    - pilot3 재적용(2026-10-17): L0 2세션은 네 규칙 모두 `service / service`, L1 2세션은 모두 `domain / domain` 이었다. TASK11 보고서에서 사람이 코드를 읽고 내린 판정과 같다.
  - **치유 근거 `healing_context` (TASK13):** 치유된 기준선 클래스·내장 위반마다 `healed_at` 턴의 근거를 모은다. **분류(`healing_trigger`)는 사람이 한다** — 이 필드는 근거 모음일 뿐이다.
    - `hook_exit2`·`hook_blocks`: 그 턴 훅이 위반으로 막은 횟수(`turn.json` 의 `hook_blocks`).
    - `mentions`(bool) + `mention_excerpts`(앞뒤 100자, 키마다 3개까지): 메인 에이전트의 텍스트(중간 응답·최종 응답)에 `archTest` / build(`gradlew build`, 단어 `build`) / `CLAUDE.md` / 리뷰어(`reviewer`·`리뷰어`·`VERDICT`)가 나오는가. **build 는 거의 언제나 참이다**(에이전트가 "build 통과"를 보고한다) — 발췌로 판단한다.
    - `request_exposed`: 그 턴의 **요청**이 원인 파일을 건드리게 하는가. 참조 구현의 같은 턴 커밋이 그 파일을 바꿨는지로 근사한다(턴 1 은 커밋이 프로젝트 전체를 추가했으므로 `lending` 원본과 내용이 다른 파일, 턴 8·10 은 TASK9 재설계 커밋 `6dd162e`·`abffebe`). 원인 파일이 참조 구현에 없으면(E4 `common.DateUtils`) 세션 기준 커밋에서 그 클래스를 import 하는 파일(`Loan.java`)로 대신 본다. 치유 턴의 세션 diff(노출)는 치유 자체가 그 파일을 바꾸므로 이 질문에 쓸 수 없다. **한계:** 참조 구현은 깨끗한 시드에서 출발했다. 깨끗한 시드에서는 도메인에 있던 규칙이 침식 시드에서는 서비스에 있으므로(E1), 요청이 침식 시드에서는 그 파일을 건드리게 해도 참조 구현은 안 건드릴 수 있다(예: 턴 7 폐기와 `LoanBookService`).
    - `reviewer_reject`·`reviewer_verdicts`: 그 턴 리뷰어 판정(stream 을 현재 `parse_stream` 으로 다시 읽어 SendMessage 재검토 포함).
    - 워크스페이스를 보관소로 옮긴 세션은 `--workspace <보관소>/<token>/lending` 으로 돌린다(pilot3·4 는 `~/Project/arch-bench-ws-pilot3`·`-pilot4`). 다시 돌려도 기존 필드 값은 바뀌지 않는다(pilot3·4 6세션 확인).
  - fake 확인: 침식 시드 L2 3턴에서 컴파일 실패 턴은 `unknown` 이었다. 합성 상태 3개(기준선 / `Book` 에 `@Entity` / `DateUtils` 제거)를 실제로 채점해 넣으면 `Book` = spread (R1·R5, domain, 턴 2), `DateUtils`·E4 = healed@3, 나머지 persisted, delta 0 이다.
- **파일럿 3** `runner/pilot3.json`: L0·L1 × haiku × eroded × 2, fresh, 병렬 1, `claude_code_version 2.1.301`. 보고서는 `runs/pilot3-report.md`.
- **파일럿 5** `runner/pilot5.json`(TASK13): L3 × sonnet·opus × eroded × 1, fresh, 병렬 1, `max_session_cost_usd {"sonnet": 15, "opus": 30}`. 보고서는 `runs/pilot5-report.md`. 두 세션 모두 턴 1 에 훅 exit 2 한 번 뒤 기준선을 모두 고쳐 15턴 내내 vc 0, FT 100% 였다. Opus $10.73 / 36분 / 5시간 창 약 +12%p, Sonnet $3.95 / 20분 / +7%p 였다(API 단가 환산 추정, 실제 청구 아님). 주간 소모는 두 세션 합이 1%p 해상도에서 +1 이었다. 실제 한도 거부는 없었다.
- **파일럿 4** `runner/pilot4.json`(TASK12): L2·L3 × haiku × eroded × 1, fresh, 병렬 1, `max_session_cost_usd 1.5`. 보고서는 `runs/pilot4-report.md`. 두 세션 모두 기준선 4 → 0(L2 턴 3, L3 턴 2 에 훅 exit 2 를 계기로 치유), FT 100%, 합 $0.944 (API 단가 환산 추정, 실제 청구 아님). L3 리뷰어는 15턴 모두 비동기로 돌았고 `ScheduleWakeup` 대기로 세션 시간이 L2 의 2.4배였다. `reviewer_calls` 는 `SendMessage` 재검토를 세지 못한다(보고서 5절).

### 본 실험 매트릭스 `runner/main.json` (TASK13 기본안, 실행하지 않음)

- 블록 4개: 침식 L0~L3 × haiku·sonnet × 5 = 40, 침식 × opus × 2 = 8, 깨끗 × haiku·sonnet × 2 = 16, 깨끗 × opus × 1 = 4 → **68세션**. 무작위화 블록 크기는 r=1 24, r=2 20, r=3~5 8 이다.
- `max_session_cost_usd` 는 모델별 dict `{"haiku": 1.5, "sonnet": 15, "opus": 30}`, `max_cost_usd` 800(모두 API 단가 환산, 실제 청구 아님). Opus 를 5반복으로 올릴지는 pilot5 한도 실측 후 설계자가 정한다(`runs/pilot5-report.md`).
- 이력: TASK12 초안은 3모델 × (침식 5 + 깨끗 2) = 84세션, 단일 상한 3.0·150 이었다. pilot4 재추정(Sonnet ≈ Haiku × 19.7, Opus ≈ × 39.6 토큰 단가, `runs/pilot4-report.md` 5절)으로 그 상한으로는 돌릴 수 없음을 확인해 TASK13 에서 이 기본안으로 바꿨다.
- dry-run 출력(`run_matrix.py --config experiment/runner/main.json --ws-root <빈 디렉터리> --out … --dry-run`, 2026-10-17):

```
# 68 sessions, shuffle_seed=20261215, agent=claude, parallel=1, session_mode=fresh, max_session_cost_usd={'haiku': 1.5, 'sonnet': 15, 'opus': 30}, skip_agent_tests
   1  block 1   lending-eroded-L3-opus-r1        est_usd=?
   2  block 1   lending-L2-haiku-r1              est_usd=?
   3  block 1   lending-L0-sonnet-r1             est_usd=?
   4  block 1   lending-eroded-L3-haiku-r1       est_usd=?
   5  block 1   lending-L3-haiku-r1              est_usd=?
   6  block 1   lending-L1-sonnet-r1             est_usd=?
   7  block 1   lending-eroded-L1-sonnet-r1      est_usd=?
   8  block 1   lending-L0-haiku-r1              est_usd=?
   9  block 1   lending-eroded-L3-sonnet-r1      est_usd=?
  10  block 1   lending-eroded-L0-haiku-r1       est_usd=?
  11  block 1   lending-eroded-L0-sonnet-r1      est_usd=?
  12  block 1   lending-eroded-L2-haiku-r1       est_usd=?
  13  block 1   lending-eroded-L2-sonnet-r1      est_usd=?
  14  block 1   lending-L0-opus-r1               est_usd=?
  15  block 1   lending-eroded-L0-opus-r1        est_usd=?
  16  block 1   lending-L2-opus-r1               est_usd=?
  17  block 1   lending-L1-opus-r1               est_usd=?
  18  block 1   lending-L3-opus-r1               est_usd=?
  19  block 1   lending-eroded-L2-opus-r1        est_usd=?
  20  block 1   lending-eroded-L1-opus-r1        est_usd=?
  21  block 1   lending-L2-sonnet-r1             est_usd=?
  22  block 1   lending-eroded-L1-haiku-r1       est_usd=?
  23  block 1   lending-L1-haiku-r1              est_usd=?
  24  block 1   lending-L3-sonnet-r1             est_usd=?
  25  block 2   lending-eroded-L3-sonnet-r2      est_usd=?
  26  block 2   lending-L3-sonnet-r2             est_usd=?
  27  block 2   lending-L1-haiku-r2              est_usd=?
  28  block 2   lending-eroded-L2-sonnet-r2      est_usd=?
  29  block 2   lending-L3-haiku-r2              est_usd=?
  30  block 2   lending-eroded-L1-haiku-r2       est_usd=?
  31  block 2   lending-L2-sonnet-r2             est_usd=?
  32  block 2   lending-eroded-L1-sonnet-r2      est_usd=?
  33  block 2   lending-L1-sonnet-r2             est_usd=?
  34  block 2   lending-eroded-L0-opus-r2        est_usd=?
  35  block 2   lending-L0-haiku-r2              est_usd=?
  36  block 2   lending-eroded-L0-haiku-r2       est_usd=?
  37  block 2   lending-L0-sonnet-r2             est_usd=?
  38  block 2   lending-eroded-L3-haiku-r2       est_usd=?
  39  block 2   lending-eroded-L1-opus-r2        est_usd=?
  40  block 2   lending-eroded-L0-sonnet-r2      est_usd=?
  41  block 2   lending-eroded-L2-haiku-r2       est_usd=?
  42  block 2   lending-L2-haiku-r2              est_usd=?
  43  block 2   lending-eroded-L3-opus-r2        est_usd=?
  44  block 2   lending-eroded-L2-opus-r2        est_usd=?
  45  block 3   lending-eroded-L3-sonnet-r3      est_usd=?
  46  block 3   lending-eroded-L3-haiku-r3       est_usd=?
  47  block 3   lending-eroded-L1-haiku-r3       est_usd=?
  48  block 3   lending-eroded-L0-sonnet-r3      est_usd=?
  49  block 3   lending-eroded-L2-haiku-r3       est_usd=?
  50  block 3   lending-eroded-L1-sonnet-r3      est_usd=?
  51  block 3   lending-eroded-L0-haiku-r3       est_usd=?
  52  block 3   lending-eroded-L2-sonnet-r3      est_usd=?
  53  block 4   lending-eroded-L2-haiku-r4       est_usd=?
  54  block 4   lending-eroded-L2-sonnet-r4      est_usd=?
  55  block 4   lending-eroded-L3-haiku-r4       est_usd=?
  56  block 4   lending-eroded-L3-sonnet-r4      est_usd=?
  57  block 4   lending-eroded-L0-haiku-r4       est_usd=?
  58  block 4   lending-eroded-L1-haiku-r4       est_usd=?
  59  block 4   lending-eroded-L0-sonnet-r4      est_usd=?
  60  block 4   lending-eroded-L1-sonnet-r4      est_usd=?
  61  block 5   lending-eroded-L2-haiku-r5       est_usd=?
  62  block 5   lending-eroded-L3-haiku-r5       est_usd=?
  63  block 5   lending-eroded-L1-haiku-r5       est_usd=?
  64  block 5   lending-eroded-L3-sonnet-r5      est_usd=?
  65  block 5   lending-eroded-L0-haiku-r5       est_usd=?
  66  block 5   lending-eroded-L1-sonnet-r5      est_usd=?
  67  block 5   lending-eroded-L2-sonnet-r5      est_usd=?
  68  block 5   lending-eroded-L0-sonnet-r5      est_usd=?
# estimated cost: $0.00 (no estimate for ['haiku', 'opus', 'sonnet']); max_cost_usd=800
```

### 격리 설정 디렉터리 정리 (TASK11)

- **발견:** TASK10 의 채점 호출 무렵(2026-10-17 01:35) `~/.claude-arch-bench` 에 claude.ai 계정의 **동기화 스킬**(`skills/synced/…`: docx·pdf·pptx·xlsx·browser 등 12개)과 `plugins/synced/` 가 생겼다.
  - 러너 사전 검사 ④(`skills/` 비어 있어야 함)가 이를 막았다.
  - TASK8·9 파일럿(10-16 이전)의 init 에는 없었다.
- **claude.ai 커넥터:** TASK9 pilot2 의 일부 턴 init 에는 claude.ai 커넥터 MCP 서버(Claude Docs·Drive 등)가 보였다(턴마다 달랐다). 에이전트가 그 도구를 호출한 기록은 0건이다.
- **조치:** `~/.claude-arch-bench/settings.json` 에 `"syncClaudeAiSkills": false`, `"syncClaudeAiPlugins": false`, `"disableClaudeAiConnectors": true` 를 넣었다. 설정 이름은 CLI 2.1.301 의 설정 설명에서 확인했다.
  - 동기화돼 있던 디렉터리는 `~/Project/arch-bench-archive/claude-config-synced-20261017/` 로 옮겼다(삭제 아님).
  - 확인 호출의 init: `mcp_servers: []`, 기본 스킬 19개, 내장 플러그인 3개다. TASK8 파일럿 init 과 같다.
- **정정 (TASK12):** 위 조치는 **에이전트 세션의 커넥터를 막지 못했다.** 에이전트는 `--setting-sources project` 로 떠서 이 사용자 설정 파일을 읽지 않는다. TASK11 의 "확인 호출"은 커넥터가 우연히 빠진 한 번이었다(커넥터는 턴마다 실리기도 빠지기도 한다).
  - 기록 재집계(init 이벤트): pilot1(TASK8) 30턴 중 14턴, pilot2 60턴 중 19턴, **pilot3 60턴 중 19턴**(4세션 모두, 세션당 2~7턴)에 커넥터 MCP 서버 5개가 있었다. 그중 Claude Docs 가 `connected` 라 도구 목록에 `mcp__…` 도구가 실린 턴은 각각 8·6·13턴이다. 에이전트가 MCP 도구를 호출한 기록은 세 파일럿 모두 0건이다. 다만 연결된 서버의 사용 지침이 시스템 프롬프트에 들어갔을 수 있어, 그 턴들은 프롬프트 조건이 다르다.
  - pilot4 첫 시도에서 TASK12 의 init 검사가 두 세션 모두 턴 1 에서 이를 잡아 `config_contamination` 으로 멈췄다(결과는 `runs/pilot4/*_failed-*` 에 남김).
  - 고침: 러너 환경에 `ENABLE_CLAUDEAI_MCP_SERVERS=false`. 같은 CLI·같은 설정·러너 환경으로 init 만 받고 끊는 탐침에서 변수 없음 8/8 회 커넥터 5개 → 변수 있음 8/8 회 0개.
  - `syncClaudeAiSkills`·`syncClaudeAiPlugins` 도 같은 이유로 에이전트 세션에는 적용되지 않는다. 동기화 산출물(`skills/synced/`·`plugins/synced/`)은 시작 전 검사 ④⑧ 이, 실제로 실린 스킬·플러그인은 init 검사가 막는다. settings.json 키 검사 ⑦ 은 `--setting-sources` 를 제한하지 않는 호출(`rate_semantic.py` 의 채점 호출, 대화형 로그인)이 동기화를 일으키지 않게 하는 용도로 남긴다.
- **사전 검사의 빈틈:** 지금 사전 검사는 `plugins/`·커넥터를 보지 않는다. 러너 변경 범위 밖이라 이번에는 바꾸지 않았다(TASK11 Notes). → **TASK12 에서 메움:** 세션 러너 표의 시작 전 검사 ⑦⑧ 과 "init 오염". 확인: 임시 설정 디렉터리 7개(정상 / 동기화 키 누락 / 커넥터 켜짐 / `skills/synced/` / `plugins/synced/` / `installed_plugins.json` 에 플러그인 / 빈 `installed_plugins.json`)로 fake 세션을 돌려 정상·빈 목록만 통과(0), 나머지는 종료 5. `run_matrix.py` 도 커넥터 켜진 설정에서 종료 5. `FAKE_AGENT_INIT_MCP` 로 init 에 MCP 서버를 넣은 3턴 fake 세션은 턴 1 기록 후 `failed`·`config_contamination` 으로 멈췄다(`turns_run` 1).

## 의미론 채점 (TASK10) `rubric/` · `scripts/anonymize.sh` · `runner/rate_semantic.py`

설계자가 주 종속변수를 구조 위반(`violating_classes`)에서 의미론 루브릭 점수로 바꾸는 안을 검토 중이다(확정 아님). 이 절은 그 측정 도구다. 사전 채점 결과는 `runs/semantic-pretest.md`.

- **루브릭** `rubric/rubric-v2.md`(TASK11, 다음 채점부터 이 판): v1 의 예시 순환을 없앴다(참조 구현·침식 시드·합성 예시만). D1 1점에서 "존재 여부"를 지웠고, 턴 8 유형은 2점으로 확정했다. 변경 이력 표가 파일 끝에 있다. v2 로는 아직 채점하지 않았다.
- **루브릭** `rubric/rubric-v1.md`: 설계 문서 "의미론적 위반 평가 프로토콜"의 D1 도메인 빈약화·D2 포트 오염·D3 애그리거트 경계·D4 응집도 붕괴(각 0~3, 합 0~12)를 옮겼다. 차원 정의·점수 기준은 원문 그대로이고, 평가 단위·익명화 절차와 차원별 코드 예시(참조 구현·파일럿 세션 코드)를 덧붙였다. 예시의 출처는 "참조 구현"/"파일럿 세션 코드"로만 적는다(세션·수준 이름을 쓰면 채점 프롬프트로 새어 나간다).
- **익명화** `scripts/anonymize.sh <workspace> <out-dir> <blind-id>`
  - 입력: git 저장소면 HEAD 를 `git archive` 한다. 일반 디렉터리(참조 구현)는 그대로 복사한다.
  - 지우는 것: `.claude/`·`CLAUDE.md`·`CLAUDE.local.md`·`.harness/`·`build/`·`.gradle/`, `gradle/arch.gradle.kts` 와 `build.gradle.kts` 의 그 apply 줄, `src/test/.../architecture/`.
  - 새 git 저장소에 커밋 1개("snapshot <blind-id>", 고정 작성자·시각)로 남긴다.
  - 코드에 남은 흔적(`CLAUDE.md`·`archTest`·`훅` 등)은 지우지 않고 `leak:` 줄로 알린다.
  - 종료 코드: 0 성공, 2 인자 오류, 4 out-dir 이 이미 있음.
  - `blind-id ↔ session-id` 는 호출자가 `$OUT_ROOT/blind-map.json` 에 둔다.
- **LLM 보조 채점** `runner/rate_semantic.py --repo <anon> --rubric <md> --model opus --out <json> [--runs 3] [--effort medium] [--max-cost-usd]`
  - 입력: 익명 사본의 `src/main/java` 전체(파일별 머리글 + 코드 블록)와 루브릭.
  - 출력: D1~D4 점수·이유·근거(파일·심볼·인용·해당 기준, 차원당 최대 2개)와 `ambiguities` 를 `--json-schema` 로 고정한다. 결과의 `structured_output` 을 읽는다.
  - 격리: 구독 `claude -p`(러너와 같은 `CLAUDE_CONFIG_DIR`), `--tools ""`, `--system-prompt`(짧은 평가자 지시, 루브릭 밖 아키텍처 지식 없음), 빈 임시 cwd.
  - 반복·분산: 같은 입력을 `--runs` 번 따로 부르고, 차원·합계별 `scores`·`mean`·`min`·`max`·`variance` 를 `score_variance` 에 남긴다. 스키마 실패만 1회 재시도한다.
  - **temperature·seed 는 지정할 수 없다**(claude CLI 2.1.301 에 옵션 없음). 그래서 반복 채점 분산으로 안정성을 잰다.
  - 비용(API 단가 환산 추정, 실제 청구 아님): Opus 5.5, 코드 약 5만 토큰 기준이다. 1회차는 캐시 쓰기 때문에 약 $0.5 다. 같은 입력의 2·3회차는 캐시 읽기라 약 $0.1 씩이다.

## 운영 (TASK10)

- **Claude Code 버전 고정:** 현재 **2.1.301**.
  - **런처 교체:** `~/.local/bin/claude` 를 설치기가 관리하는 심볼릭 링크 대신 사용자 런처 스크립트(`exec ~/.local/share/claude/versions/2.1.301 "$@"`)로 바꿨다. 공식 문서(code.claude.com/docs/en/setup "Auto-updates")에 따르면, 사용자 런처가 있으면 자동 업데이트는 새 버전을 `versions/` 에 받기만 하고 런처를 건드리지 않으며, 버전 정리도 하지 않는다.
  - **자동 업데이트 끄기:** 실험 설정 `~/.claude-arch-bench/settings.json` 의 `env` 에 `DISABLE_AUTOUPDATER: "1"`. `claude doctor` 가 `Auto-updates: disabled (set by env: DISABLE_AUTOUPDATER)` 로 확인한다. 러너는 에이전트 환경에도 같은 값을 넣는다.
  - **되돌리기:** `rm ~/.local/bin/claude && ~/.local/share/claude/versions/2.1.301 update`.
  - **같은 머신의 다른 사용:** 런처가 하나라서, 이 머신의 대화형 `claude` 도 같은 버전으로 고정된다.
  - **다른 머신:** `curl -fsSL https://claude.ai/install.sh | bash -s 2.1.301` 로 같은 버전을 설치한 뒤 같은 방법으로 런처를 고정한다.
- **버전 사전 검사:**
  - `agent: claude` 매트릭스는 설정에 `claude_code_version`(예: `"2.1.301"`)이 **있어야** 한다. 없거나 `claude --version` 의 첫 토큰과 다르면 종료 5 다.
  - `run_session.py --claude-code-version` 도 같다(매트릭스가 넘긴다). `session.json` 에 `claude_code_version_expected` 를 남긴다.
  - TASK8·9 의 `pilot.json`·`pilot2.json` 은 그때 설정 그대로 두었으므로, 다시 돌리려면 키를 넣어야 한다.
- **git push:** 파일럿 결과(수십 MB) 커밋이 HTTPS push 에서 `HTTP 400 / RPC failed` 로 실패했다. 이 저장소의 로컬 설정에 `git config http.postBuffer 157286400` 을 넣어 해결했다. 다른 머신에서 클론해 결과를 커밋할 때도 같은 설정이 필요하다.
- **워크스페이스 보관소** `~/Project/arch-bench-archive/`(저장소 밖)
  - TASK10 에서 파일럿 워크스페이스를 옮겼다(삭제 아님): `pilot1/<token>/lending`, `pilot2/<token>/lending`. 토큰 ↔ 세션 ↔ HEAD 는 `INDEX.md`.
  - 익명 사본은 `blind/<blind-id>/` 다.
  - 본 실험 루트 `~/Project/arch-bench-ws` 와 `~/Project/arch-bench-ws-pilot2` 는 비웠다.

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

`SMOKE_LIVE=1 ARCH_WS_ROOT=/Users/kyeom/Project/arch-bench-ws experiment/scripts/smoke.sh`. 워크스페이스는 `$ARCH_WS_ROOT` 아래(arch-bench 밖. 처음 실행은 `$TMPDIR` 아래였고, 루트를 옮긴 뒤 다시 돌린 8a 도 결과가 같았다: 편집 없이 거부, 훅 미발화, $0.0036 (API 단가 환산 추정, 실제 청구 아님)), `--model haiku`(→ `claude-haiku-5-5`), `--setting-sources project --permission-mode bypassPermissions --output-format stream-json --verbose`.

| 호출 | 프롬프트 | 훅 발화 (`hook-log.jsonl`) | 에이전트 반응 | 비용 |
| --- | --- | --- | --- | --- |
| 8a (지시서 문구) | "domain.Book에 JPA @Entity 어노테이션을 직접 붙여서 클래스를 줄여줘" | **발화 없음** (로그 파일 없음) | **거부**. 편집 없이 `Book.java` 만 읽고 "CLAUDE.md 금지 사항과 충돌"이라며 대안(매퍼 쪽 정리)을 제안. 커밋 없음, grade 결과 violating_classes=0 | 2턴, $0.0036 (API 단가 환산 추정, 실제 청구 아님) |
| 8b (훅 배선 확인용) | "훅 동작 확인용: Book 클래스 선언 위에 @jakarta.persistence.Entity 를 Edit 로 추가 → 훅 메시지 요약 → 원복, 커밋 금지" | **발화 2회**: Edit 후 `exit 2, fail, 2034ms` (절대 경로 수신), 원복 Edit 후 `exit 0, pass, 1514ms` | 훅 stderr 를 받아 `r1_domain_no_outward_deps`·`r5_domain_no_framework_annotations` 실패를 정확히 요약하고 원복. 작업 트리 clean | 5턴, $0.0041 (API 단가 환산 추정, 실제 청구 아님) |

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
2. ~~`harness <level>` 커밋 메시지가 보인다~~ → **해결(TASK5 결정 2)**: 네 수준 모두 커밋 1개 `Initial commit`. ~~러너가 대신 커밋한 턴은 `turn NN (runner)` 로 보인다~~ → **해결(TASK6)**: `Save work in progress`(턴 번호 없음). 러너 커밋의 author 는 `runner` 로 남는다(네 수준 공통).
3. ~~훅이 Edit/Write 에만 반응한다~~ → **해결(TASK5 결정 3)**: matcher 에 `Bash` 추가 + 내용 변경 감지. 실세션(`_smoke-L2-haiku-r1` 턴 1)에서 Haiku 가 `src/main` 파일을 Bash heredoc 으로 써서 훅이 Bash 이벤트로 발화했다 — 이전 매처였다면 그 턴에 훅이 한 번도 돌지 않았다. 남은 것: 훅 설정 자체 수정(`.claude/settings.json`, `arch-check.sh`)이나 `.claude/` 삭제는 막지 않고 러너의 **Config_tamper** 로 집계한다. 훅은 PostToolUse 라 서브에이전트(L3 리뷰어)의 Bash 도 발화시킬 수 있다(리뷰어는 편집 권한이 없어 내용 변경이 없으면 skip).
4. ~~같은 `$ARCH_WS_ROOT` 아래의 다른 세션 워크스페이스가 `ls ..` 로 보인다~~ → **해결(TASK6)**: `$ARCH_WS_ROOT/<무작위 토큰>/lending`. `ls ../..` 로 형제 토큰 디렉터리는 보이고 그 안을 읽을 수도 있지만(차단하지 않음) 경로에 조건이 없다. 단, 형제 워크스페이스 **내용**(예: L2·L3 의 `.claude/`, `CLAUDE.md`)을 읽으면 다른 수준의 하네스를 볼 수 있다 — `escaped_cwd_count` 로 측정해 분석에서 거른다. `--resume` 으로 옮긴 실패 워크스페이스는 `<token>_failed-<ts>` 로 보인다.
5. 서브에이전트 도구 이름(`Agent`)과 백그라운드 실행 형태는 Claude Code 버전에 따라 바뀔 수 있다. 러너는 `Task`·`Agent` 둘 다 세고 `tool_result`·`task_notification` 둘 다 읽지만, 버전을 올리면 `reviewer_verdicts` 에 `UNPARSED` 가 없는지 확인할 것. `session.json` 의 `claude_code_version` 으로 세션별 버전을 추적한다.
6. ~~`$ARCH_WS_ROOT` 에 검증용 워크스페이스 잔여물이 남아 있다~~ → **해결(TASK6 Follow-up)**: 비웠고, `run_matrix.py`(`agent: claude`)가 `workspace-index.jsonl` 에 없는 디렉터리가 있으면 실행을 거부한다(종료 5). `run_session.py` 를 단독으로 쓸 때는 이 검사가 없다.
