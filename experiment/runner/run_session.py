#!/usr/bin/env python3
"""세션 1개 실행: 시드 복사 → 하네스 주입 → 턴 루프(에이전트 → 커밋 확인 → tamper → 채점 → 기능 테스트) → session.json.

사용:
  run_session.py --level L2 --model haiku --domain _smoke --rep 1 \
                 --ws-root $ARCH_WS_ROOT --out $OUT_ROOT [--agent claude|fake] [--max-turns N]
                 [--session-mode resume|fresh] [--max-session-cost USD]

종료 코드: 0 전 턴 완료, 1 중단(session.json 의 fail_reason: agent_error·no_session_id·session_cost_cap·
          rate_limit_timeout·interrupted), 2 인자 오류, 4 워크스페이스·결과 디렉터리가 이미 있음,
          5 시작 전 검사 실패(상위 CLAUDE.md·.claude 누출, CLAUDE_CONFIG_DIR 미설정·오염, ANTHROPIC_API_KEY 설정,
          구독 로그인 아님)
계약 전문은 experiment/README.md "세션 러너" 절.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import secrets
import shlex
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]  # arch-bench
EXP = ROOT / "experiment"
SCRIPTS = EXP / "scripts"
SEED = EXP / "seed"
FAKE_AGENT = EXP / "runner" / "fake_agent.sh"
HIDDEN_TESTS = EXP / "tests"  # <domain>/turn-NN/*.java, 에이전트에게 보이지 않는 HTTP 수준 기능 테스트
HIDDEN_TEST_DIR = "src/test/java/com/example/lending/hidden"
TEST_METHOD_RE = re.compile(r"@Test\s+(?:public\s+)?void\s+(\w+)\s*\(")

LEVELS = ("L0", "L1", "L2", "L3")
AGENT_TIMEOUT_S = 20 * 60
GRADE_TIMEOUT_S = 10 * 60
TEST_TIMEOUT_S = 15 * 60
BASE_COMMIT_MESSAGE = "Initial commit"
RUNNER_COMMIT_MESSAGE = "Save work in progress"  # 턴 번호·수준을 넣지 않는다(에이전트의 git log 에 보인다)
ARCH_TEST_DIR = "src/test/java/com/example/lending/architecture/"
CONFIG_PATHS = (".claude/", "CLAUDE.md", "build.gradle.kts", "gradle/arch.gradle.kts")
TRACKED_TOOLS = ("Edit", "Write", "MultiEdit", "Bash", "Read", "Task")
SUBAGENT_TOOLS = ("Task", "Agent")  # Claude Code 2.1.x 에서 서브에이전트 도구 이름은 Agent (구 Task)
REVIEWER = "architecture-reviewer"
VERDICT_RE = re.compile(r"VERDICT:\s*(APPROVE|REJECT)\b")
# 에이전트 프로세스에 넘기지 않는 환경 변수: 러너를 Claude Code 안에서 띄웠을 때 부모 세션 정보가 새지 않게
STRIPPED_ENV_PREFIXES = ("CLAUDE_CODE_",)
STRIPPED_ENV = {"CLAUDECODE", "CLAUDE_PID", "CLAUDE_EFFORT", "CLAUDE_PROJECT_DIR"}
EXTRA_ENV = {"DISABLE_AUTOUPDATER": "1"}  # 매트릭스 도중 Claude Code 버전이 바뀌지 않게
LEAK_NAMES = ("CLAUDE.md", "CLAUDE.local.md", ".claude")
# Bash 명령 분석: 명령 구분자, `..` 경로 성분(`HEAD~1..HEAD` 같은 git 범위는 성분이 아니라 제외)
SHELL_SEP_RE = re.compile(r"&&|\|\||[;\n|]")
PARENT_COMPONENT_RE = re.compile(r"(?:^|/)\.\.(?:/|$)")
RATE_LIMIT_TEXT_RE = re.compile(r"usage limit|rate limit|hit your .{0,20}limit|limit reached", re.I)
API_KEY_ENV = ("ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN")  # 있으면 구독이 아니라 API 과금으로 나간다
RATE_LIMIT_POLL_S = 15 * 60  # 재시도 시각을 모를 때 대기 간격
RATE_LIMIT_MAX_WAIT_S = 6 * 60 * 60  # 한 턴의 한도 대기 상한
HEREDOC_RE = re.compile(r"<<-?\s*(['\"]?)(\w+)\1[^\n]*\n.*?^\s*\2\s*$", re.S | re.M)  # 본문은 파일 내용이지 경로가 아니다
RUNNER_GIT = ["-c", "user.name=runner", "-c", "user.email=runner@example.com", "-c", "commit.gpgsign=false"]


class PrecheckError(Exception):
    pass


def now_iso() -> str:
    return dt.datetime.now(dt.timezone.utc).astimezone().isoformat(timespec="seconds")


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sha256_tree(paths: list[Path], base: Path) -> str:
    """파일 목록의 (상대 경로, 내용 해시)를 정렬해 하나의 해시로 만든다."""
    h = hashlib.sha256()
    for p in sorted(paths, key=lambda x: str(x.relative_to(base))):
        h.update(f"{p.relative_to(base)}\0{sha256_file(p)}\n".encode())
    return h.hexdigest()


def run(cmd: list[str], cwd: Path | None = None, timeout: float | None = None, env: dict | None = None,
        check: bool = False) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, cwd=cwd, timeout=timeout, env=env, check=check, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def git(ws: Path, *args: str, check: bool = True) -> str:
    return run(["git", *args], cwd=ws, check=check).stdout.strip()


def write_json(path: Path, data) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


# ---------------------------------------------------------------- 시작 전 검사

def precheck_ws_root(ws_root: Path) -> None:
    """ws_root 부터 / 까지 CLAUDE.md·CLAUDE.local.md·.claude 가 없어야 한다 (smoke 8단계와 같은 검사, ~/.claude 제외)."""
    home_claude = Path.home() / ".claude"
    d = ws_root
    while True:
        for name in LEAK_NAMES:
            p = d / name
            if p.exists() and p != home_claude:
                raise PrecheckError(f"{p} would leak into the agent session; choose another --ws-root")
        if d.parent == d:
            break
        d = d.parent


def precheck_config_dir() -> Path:
    """CLAUDE_CONFIG_DIR 이 설정되어 있고, 그 안에 훅·사용자 메모리·사용자 에이전트/스킬/명령이 없어야 한다."""
    raw = os.environ.get("CLAUDE_CONFIG_DIR", "")
    if not raw:
        raise PrecheckError("CLAUDE_CONFIG_DIR is not set; use a dedicated config dir to isolate user settings")
    cfg = Path(raw).expanduser().resolve()
    if not cfg.is_dir():
        raise PrecheckError(f"CLAUDE_CONFIG_DIR={cfg} is not a directory")
    if cfg == (Path.home() / ".claude").resolve():
        raise PrecheckError("CLAUDE_CONFIG_DIR points to the user's own ~/.claude")
    settings = cfg / "settings.json"
    if settings.exists():
        try:
            data = json.loads(settings.read_text() or "{}")
        except json.JSONDecodeError as e:
            raise PrecheckError(f"{settings} is not valid JSON: {e}") from e
        if data.get("hooks"):
            raise PrecheckError(f"{settings} defines hooks; user-level hooks would mix into the session")
    if (cfg / "CLAUDE.md").exists():
        raise PrecheckError(f"{cfg / 'CLAUDE.md'} exists; user memory would mix into the session")
    for sub in ("agents", "skills", "commands"):
        d = cfg / sub
        if d.is_dir() and any(d.iterdir()):
            raise PrecheckError(f"{d} is not empty; user {sub} would mix into the session")
    return cfg


def precheck_billing(env: dict) -> dict:
    """구독 실행 전제: API 키 환경 변수가 없고, CLAUDE_CONFIG_DIR 이 claude.ai(구독) 로그인이다. 반환: 기록용 인증 요약."""
    leaked = [k for k in API_KEY_ENV if os.environ.get(k)]
    if leaked:
        raise PrecheckError(f"{', '.join(leaked)} is set; the agent would bill the API instead of the subscription")
    p = run(["claude", "auth", "status"], env=env, timeout=60)
    try:
        st = json.loads(p.stdout)
    except json.JSONDecodeError:
        raise PrecheckError(f"claude auth status did not return JSON (exit {p.returncode}): {p.stderr[:200]}") from None
    if not st.get("loggedIn") or st.get("authMethod") != "claude.ai":
        raise PrecheckError(f"CLAUDE_CONFIG_DIR is not logged in with a claude.ai subscription "
                            f"(loggedIn={st.get('loggedIn')}, authMethod={st.get('authMethod')})")
    return {"auth_method": st.get("authMethod"), "subscription_type": st.get("subscriptionType"),
            "api_provider": st.get("apiProvider")}


# ---------------------------------------------------------------- 스트림 파싱

def escapes_cwd(command: str, cwd: str, ws: Path, ws_root: Path) -> tuple[bool, str]:
    """Bash 명령이 워크스페이스 밖을 가리키는가 (측정용, 차단하지 않음). 반환: (밖으로 나감, 명령 뒤 cwd).

    명령을 구분자로 나눠 `cd` 로 cwd 를 따라가며, `..` 성분이 있는 경로는 cwd 기준으로 풀어 ws 밖이면,
    절대 경로는 ws_root 아래인데 ws 밖이면 1건으로 본다. `cd src && ...; cd ..` 처럼 안에서 오르내리는 것은 세지 않는다.
    어휘 분석이라 변수·서브셸·`~` 는 따라가지 않는다."""
    ws_s, root_s = str(ws), str(ws_root)

    def inside(p: str, base: str) -> bool:
        return p == base or p.startswith(base + "/")

    escaped = False
    command = HEREDOC_RE.sub(lambda m: m.group(0).split("\n", 1)[0], command)
    for seg in SHELL_SEP_RE.split(command):
        try:
            toks = shlex.split(seg)
        except ValueError:  # 따옴표가 짝이 안 맞는 조각(heredoc 본문 등)
            toks = seg.split()
        toks = [t.lstrip("(").rstrip(")") for t in toks]
        toks = [t for t in toks if t]
        if not toks:
            continue
        if toks[0] == "cd":
            target = toks[1] if len(toks) > 1 else ""
            if not target or target.startswith(("~", "$", "-")):
                continue
            new = os.path.normpath(os.path.join(cwd, target))
            if (PARENT_COMPONENT_RE.search(target) or target.startswith("/")) and \
                    not inside(new, ws_s) and (inside(new, root_s) or PARENT_COMPONENT_RE.search(target)):
                escaped = True
            cwd = new
            continue
        for t in toks:
            t = t.split("=", 1)[1] if t.startswith("-") and "=" in t else t
            if PARENT_COMPONENT_RE.search(t):
                if not inside(os.path.normpath(os.path.join(cwd, t)), ws_s):
                    escaped = True
            elif t.startswith("/") and inside(os.path.normpath(t), root_s) and not inside(os.path.normpath(t), ws_s):
                escaped = True
    return escaped, cwd


def parse_stream(path: Path, ws: Path, ws_root: Path) -> dict:
    """stream-json 을 읽어 토큰·도구 호출·리뷰어 판정·워크스페이스 밖 Bash 를 뽑는다. 이상한 줄은 예외 대신 parse_errors 에 남긴다."""
    out = {
        "agent_session_id": None, "model_id": None, "claude_code_version": None,
        "input_tokens": 0, "output_tokens": 0, "cache_creation_input_tokens": 0, "cache_read_input_tokens": 0,
        "cost_usd_cumulative": None, "num_turns": None, "result_events": 0, "result_is_error": None, "result_text": None,
        "tool_calls": {t: 0 for t in TRACKED_TOOLS}, "tool_calls_all": {}, "subagent_tool_calls": 0,
        "reviewer_calls": 0, "reviewer_verdicts": [], "reviewer_verdict_not_first_line": 0,
        "escaped_cwd_count": 0, "escaped_cwd_commands": [],
        "rate_limit_rejected": False, "rate_limit_resets_at": None, "rate_limit_utilization": None,
        "session_ids_seen": [], "parse_errors": [],
    }
    reviewer_ids: list[str] = []
    cwds: dict[str | None, str] = {}  # 셸별(메인·서브에이전트) cwd. 턴마다 새 프로세스라 ws 에서 시작
    texts: dict[str, list[str]] = {}  # tool_use_id → 결과 텍스트 후보 (동기 tool_result, 비동기 task_notification)
    if not path.exists():
        out["parse_errors"].append("stream file missing")
        return out
    for lineno, line in enumerate(path.read_text(errors="replace").splitlines(), 1):
        if not line.strip():
            continue
        try:
            e = json.loads(line)
        except json.JSONDecodeError as ex:
            out["parse_errors"].append(f"line {lineno}: invalid JSON ({ex.msg})")
            continue
        try:
            sid = e.get("session_id")
            if sid and sid not in out["session_ids_seen"]:
                out["session_ids_seen"].append(sid)
            t, st = e.get("type"), e.get("subtype")
            if t == "system" and st == "init":
                out["agent_session_id"] = out["agent_session_id"] or sid
                out["model_id"] = out["model_id"] or e.get("model")
                out["claude_code_version"] = out["claude_code_version"] or e.get("claude_code_version")
            elif t == "rate_limit_event":
                info = e.get("rate_limit_info") or {}
                if info.get("status") == "rejected":
                    out["rate_limit_rejected"] = True
                    out["rate_limit_resets_at"] = info.get("resetsAt") or out["rate_limit_resets_at"]
                windows = info.get("unifiedWindows") or {}
                if windows:  # 마지막 값: 창별 사용률(0~1). 병렬 수를 정할 때 본다
                    out["rate_limit_utilization"] = {k: (v or {}).get("utilization") for k, v in windows.items()}
            elif t == "system" and st == "task_notification":
                tid = e.get("tool_use_id")
                if tid:
                    texts.setdefault(tid, []).append(str(e.get("summary") or ""))
            elif t in ("assistant", "user"):
                content = (e.get("message") or {}).get("content")
                if not isinstance(content, list):
                    continue
                for c in content:
                    if not isinstance(c, dict):
                        continue
                    if c.get("type") == "tool_use":
                        name = c.get("name") or "?"
                        cmd = (c.get("input") or {}).get("command") if name == "Bash" else None
                        if isinstance(cmd, str):  # 서브에이전트의 Bash 도 센다
                            shell = e.get("parent_tool_use_id")
                            esc, cwds[shell] = escapes_cwd(cmd, cwds.get(shell, str(ws)), ws, ws_root)
                            if esc:
                                out["escaped_cwd_count"] += 1
                                out["escaped_cwd_commands"].append(cmd[:500])
                        if e.get("parent_tool_use_id"):
                            out["subagent_tool_calls"] += 1
                            continue
                        out["tool_calls_all"][name] = out["tool_calls_all"].get(name, 0) + 1
                        key = "Task" if name in SUBAGENT_TOOLS else name
                        if key in out["tool_calls"]:
                            out["tool_calls"][key] += 1
                        if name in SUBAGENT_TOOLS and (c.get("input") or {}).get("subagent_type") == REVIEWER:
                            reviewer_ids.append(c.get("id"))
                    elif c.get("type") == "tool_result" and not e.get("parent_tool_use_id"):
                        tc = c.get("content")
                        if isinstance(tc, list):
                            tc = "\n".join(x.get("text", "") for x in tc if isinstance(x, dict))
                        texts.setdefault(c.get("tool_use_id"), []).append(str(tc or ""))
            elif t == "result":
                out["result_events"] += 1
                u = e.get("usage") or {}
                for k in ("input_tokens", "output_tokens", "cache_creation_input_tokens", "cache_read_input_tokens"):
                    out[k] += int(u.get(k) or 0)
                # total_cost_usd 는 --resume 로 이어진 대화 전체의 누적값이다(usage 는 이번 호출분). 턴 비용은 main 에서 차분으로 구한다
                if e.get("total_cost_usd") is not None:
                    out["cost_usd_cumulative"] = max(out["cost_usd_cumulative"] or 0.0, float(e["total_cost_usd"]))
                out["num_turns"] = (out["num_turns"] or 0) + int(e.get("num_turns") or 0)
                out["result_is_error"] = bool(e.get("is_error")) or bool(out["result_is_error"])
                out["result_text"] = e.get("result")
        except Exception as ex:  # noqa: BLE001 — 파싱 실패로 세션을 죽이지 않는다
            out["parse_errors"].append(f"line {lineno}: {type(ex).__name__}: {ex}")
    out["reviewer_calls"] = len(reviewer_ids)
    for rid in reviewer_ids:
        verdict = None
        for text in texts.get(rid, []):
            first = text.strip().splitlines()[0] if text.strip() else ""
            m = VERDICT_RE.match(first.strip())
            if m:
                verdict = m.group(1)
                break
            m = VERDICT_RE.search(text)
            if m:
                verdict = m.group(1)
                out["reviewer_verdict_not_first_line"] += 1
                break
        out["reviewer_verdicts"].append(verdict or "UNPARSED")
    return out


# ---------------------------------------------------------------- 턴 단계

def run_agent(args, ws: Path, prompt: str, turn: int, resume_id: str | None, stream: Path, stderr: Path,
              env: dict) -> dict:
    cmd = ["--model", args.model, "--output-format", "stream-json", "--verbose",
           "--permission-mode", "bypassPermissions", "--setting-sources", "project"]
    if args.agent_max_turns:
        cmd += ["--max-turns", str(args.agent_max_turns)]
    if resume_id:
        cmd += ["--resume", resume_id]
    if args.agent == "claude":
        full = ["claude", "-p", prompt, *cmd]
        stdin = subprocess.DEVNULL
    else:
        full = ["bash", str(FAKE_AGENT), *cmd]
        env = {**env, "FAKE_AGENT_TURN": str(turn)}
        stdin = subprocess.PIPE
    t0 = time.monotonic()
    status = "ok"
    with stream.open("w") as so, stderr.open("w") as se:
        proc = subprocess.Popen(full, cwd=ws, env=env, stdin=stdin, stdout=so, stderr=se, text=True,
                                start_new_session=True)
        try:
            if stdin is subprocess.PIPE:
                proc.stdin.write(prompt)
                proc.stdin.close()
            rc = proc.wait(timeout=AGENT_TIMEOUT_S)
        except subprocess.TimeoutExpired:
            os.killpg(proc.pid, signal.SIGTERM)
            try:
                proc.wait(timeout=30)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGKILL)
                proc.wait()
            rc = proc.returncode
            status = "timeout"
    if status == "ok" and rc != 0:
        status = "error"
    shown = [x if x != prompt else "<prompt>" for x in full]
    return {"agent_status": status, "agent_exit": rc, "agent_wall_s": round(time.monotonic() - t0, 2),
            "agent_cmd": " ".join(shown)}


def commit_check(ws: Path, head_before: str) -> dict:
    head_after_agent = git(ws, "rev-parse", "HEAD")
    agent_committed = head_after_agent != head_before
    dirty = bool(git(ws, "status", "--porcelain"))
    runner_committed = False
    if dirty:  # 에이전트가 커밋하지 않았거나, 커밋 뒤에 남긴 변경 → 러너가 커밋해 턴 끝 상태 = HEAD 로 맞춘다
        git(ws, "add", "-A")
        run(["git", *RUNNER_GIT, "commit", "-q", "-m", RUNNER_COMMIT_MESSAGE], cwd=ws, check=True)
        runner_committed = True
    return {
        "head_before": head_before, "head_after_agent": head_after_agent, "head_after": git(ws, "rev-parse", "HEAD"),
        "agent_committed": agent_committed, "runner_committed": runner_committed,
        "dirty_after_agent_commit": agent_committed and dirty,
        "no_change": not agent_committed and not dirty,
        "agent_commits": int(git(ws, "rev-list", "--count", f"{head_before}..{head_after_agent}")),
    }


def tamper_check(ws: Path, base: str, level: str) -> dict:
    def changed(*paths: str) -> list[str]:
        out = git(ws, "diff", "--name-only", base, "HEAD", "--", *paths)
        return [x for x in out.splitlines() if x]

    config_files = changed(*CONFIG_PATHS)
    if level in ("L2", "L3"):
        test_files = changed(ARCH_TEST_DIR)
        test_tamper = bool(test_files)
    else:  # L0·L1 에는 ArchUnit 테스트가 없다 (에이전트가 같은 경로에 새로 만든 경우는 파일 목록에만 남긴다)
        test_files = changed(ARCH_TEST_DIR)
        test_tamper = None
    return {"test_tamper": test_tamper, "test_tamper_files": test_files,
            "config_tamper": bool(config_files), "config_tamper_files": config_files}


def grade(ws: Path, out_json: Path, log: Path) -> dict:
    try:
        p = run(["bash", str(SCRIPTS / "grade.sh"), str(ws), str(out_json)], timeout=GRADE_TIMEOUT_S)
        rc = p.returncode
        log.write_text(p.stdout + p.stderr)
    except subprocess.TimeoutExpired:
        rc = None
        log.write_text("grade.sh timeout\n")
    res = {"grade_exit": rc, "violating_classes": None, "violating_classes_list": None}
    res.update({f"{r.lower()}_count": None for r in ("R1", "R2", "R3", "R4", "R5", "R6", "R7")})
    if rc == 0 and out_json.exists():
        data = json.loads(out_json.read_text())
        res["violating_classes"] = data.get("violating_classes")
        res["violating_classes_list"] = data.get("violating_classes_list")
        for rid, v in (data.get("by_rule") or {}).items():
            res[f"{rid.lower()}_count"] = v.get("count")
    return res


def functional_tests(ws: Path, log: Path) -> dict:
    """워크스페이스 복사본에서 ./gradlew test -q. 결과 XML 로 건수를 센다."""
    res = {"tests_status": None, "tests_exit": None, "tests_total": None, "tests_failed": None,
           "tests_errors": None, "tests_skipped": None, "tests_wall_s": None}
    t0 = time.monotonic()
    with tempfile.TemporaryDirectory(prefix="arch-test.") as tmp:
        copy = Path(tmp) / "ws"
        shutil.copytree(ws, copy, symlinks=True)
        for d in ("build", ".gradle", ".harness"):
            shutil.rmtree(copy / d, ignore_errors=True)
        try:
            p = run(["./gradlew", "test", "-q", "--console=plain"], cwd=copy, timeout=TEST_TIMEOUT_S)
            res["tests_exit"] = p.returncode
            log.write_text((p.stdout + p.stderr)[-20000:])
        except subprocess.TimeoutExpired:
            res["tests_status"] = "timeout"
            res["tests_wall_s"] = round(time.monotonic() - t0, 2)
            return res
        total = failed = errors = skipped = 0
        xmls = list((copy / "build" / "test-results" / "test").glob("TEST-*.xml"))
        for x in xmls:
            try:
                r = ET.parse(x).getroot()
                total += int(r.get("tests", 0))
                failed += int(r.get("failures", 0))
                errors += int(r.get("errors", 0))
                skipped += int(r.get("skipped", 0))
            except ET.ParseError:
                pass
        if xmls:
            res.update(tests_total=total, tests_failed=failed, tests_errors=errors, tests_skipped=skipped)
        if res["tests_exit"] == 0:
            res["tests_status"] = "passed"
        elif not xmls and re.search(r"Execution failed for task ':compile(Test)?Java'", log.read_text()):
            res["tests_status"] = "compile_failed"
        else:
            res["tests_status"] = "failed"
    res["tests_wall_s"] = round(time.monotonic() - t0, 2)
    return res


def hidden_test_files(domain: str, upto: int) -> list[Path]:
    """턴 1~upto 의 숨겨진 테스트 파일(누적). 디렉터리가 없거나 비어 있는 턴은 건너뛴다."""
    files: list[Path] = []
    for i in range(1, upto + 1):
        files += sorted((HIDDEN_TESTS / domain / f"turn-{i:02d}").glob("*.java"))
    return files


def hidden_tests(ws: Path, domain: str, upto: int, log: Path) -> dict:
    """워크스페이스 복사본의 src/test 를 지우고 턴 1~upto 숨겨진 테스트만 넣어 ./gradlew test.

    ft_total 은 테스트 소스의 @Test 메서드 수(정적)라서, 컴파일 실패·컨텍스트 실패로 XML 이 덜 나와도 분모가 같다.
    통과 = XML 에 failure·error·skipped 없이 나온 테스트. 나머지는 전부 ft_failed_names."""
    files = hidden_test_files(domain, upto)
    expected = [f"{f.stem}.{m}" for f in files for m in TEST_METHOD_RE.findall(f.read_text())]
    res = {"ft_status": None, "ft_total": len(expected), "ft_passed": 0, "ft_failed_names": [],
           "ft_compile_error": False, "ft_pass": None, "ft_exit": None, "ft_wall_s": None}
    if not expected:
        res["ft_status"] = "no_tests"
        log.write_text(f"no hidden tests for {domain} turn-01..{upto:02d}\n")
        return res
    t0 = time.monotonic()
    with tempfile.TemporaryDirectory(prefix="arch-ft.") as tmp:
        copy = Path(tmp) / "ws"
        shutil.copytree(ws, copy, symlinks=True)
        for d in ("build", ".gradle", ".harness", "src/test"):
            shutil.rmtree(copy / d, ignore_errors=True)
        dest = copy / HIDDEN_TEST_DIR
        dest.mkdir(parents=True)
        for f in files:
            shutil.copy2(f, dest / f.name)
        try:
            p = run(["./gradlew", "test", "-q", "--console=plain"], cwd=copy, timeout=TEST_TIMEOUT_S)
            res["ft_exit"] = p.returncode
            out = p.stdout + p.stderr
            log.write_text(out[-20000:])
        except subprocess.TimeoutExpired:
            res.update(ft_status="timeout", ft_failed_names=expected, ft_pass=0.0,
                       ft_wall_s=round(time.monotonic() - t0, 2))
            return res
        passed: set[str] = set()
        xmls = list((copy / "build" / "test-results" / "test").glob("TEST-*.xml"))
        for x in xmls:
            try:
                root = ET.parse(x).getroot()
            except ET.ParseError:
                continue
            for tc in root.iter("testcase"):
                if any(c.tag in ("failure", "error", "skipped") for c in tc):
                    continue
                cls = (tc.get("classname") or "").rsplit(".", 1)[-1]
                passed.add(f"{cls}.{(tc.get('name') or '').split('(')[0]}")
    res["ft_passed"] = sum(1 for n in expected if n in passed)
    res["ft_failed_names"] = [n for n in expected if n not in passed]
    res["ft_pass"] = round(res["ft_passed"] / res["ft_total"], 4)
    if res["ft_exit"] == 0 and not res["ft_failed_names"]:
        res["ft_status"] = "passed"
    elif not xmls and re.search(r"Execution failed for task ':compile(Test)?Java'", out):
        # 에이전트가 API 를 다르게 만들어도 테스트는 HTTP 만 쓰므로, 테스트 컴파일 실패는 보통 본문 컴파일 실패다
        res["ft_compile_error"] = True
        res["ft_status"] = "compile_failed"
    else:
        res["ft_status"] = "failed"
    res["ft_wall_s"] = round(time.monotonic() - t0, 2)
    return res


def hook_summary(log: Path) -> dict:
    counts = {"hook_events": 0, "hook_runs": 0, "hook_blocks": 0, "hook_skips": 0, "hook_limits": 0,
              "hook_compile_failed": 0, "hook_ms_total": 0}
    for line in log.read_text().splitlines() if log.exists() else []:
        try:
            e = json.loads(line)
        except json.JSONDecodeError:
            continue
        counts["hook_events"] += 1
        a = e.get("action")
        counts["hook_runs"] += a in ("pass", "fail", "compile_failed")
        counts["hook_blocks"] += a == "fail"
        counts["hook_skips"] += a == "skip"
        counts["hook_limits"] += a == "limit"
        counts["hook_compile_failed"] += a == "compile_failed"
        counts["hook_ms_total"] += int(e.get("elapsed_ms") or 0)
    return counts


def precheck_cli_version(expected: str, env: dict) -> str:
    """`claude --version` 의 버전 번호(첫 토큰)가 expected 와 같아야 한다(TASK10, 버전 고정). 반환: 실제 출력."""
    out = run(["claude", "--version"], env=env, timeout=60).stdout.strip()
    actual = out.split()[0] if out else ""
    if actual != expected.strip():
        raise PrecheckError(f"claude --version is {out!r}, expected {expected!r} (claude_code_version); "
                            f"pin the CLI or update the config")
    return out


def agent_env() -> dict:
    env = {k: v for k, v in os.environ.items()
           if k not in STRIPPED_ENV and not k.startswith(STRIPPED_ENV_PREFIXES)}
    env.update(EXTRA_ENV)
    return env


def is_rate_limited(turn: dict) -> bool:
    """구독 한도로 거부된 호출인가: 스트림의 rate_limit_event status=rejected 가 있고 호출이 실패로 끝났거나,
    실패한 결과 텍스트·stderr 가 한도 문구다."""
    failed = turn["agent_status"] != "ok" or bool(turn["result_is_error"]) or turn["result_events"] == 0
    if not failed:
        return False
    return turn["rate_limit_rejected"] or bool(RATE_LIMIT_TEXT_RE.search(f"{turn.get('result_text') or ''}\n"
                                                                       f"{turn.get('_stderr') or ''}"))


def read_prompt(path: Path) -> str:
    """턴 파일 본문. 맨 앞의 HTML 주석 줄(메타데이터, 예: smoke 표시)은 프롬프트에서 뺀다."""
    lines = path.read_text().splitlines()
    while lines and re.fullmatch(r"\s*<!--.*-->\s*", lines[0]):
        lines.pop(0)
    return "\n".join(lines).strip() + "\n"


# ---------------------------------------------------------------- 세션

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--level", required=True, choices=LEVELS)
    ap.add_argument("--model", required=True, help="claude --model 에 그대로 넘긴다 (예: haiku)")
    ap.add_argument("--domain", required=True, help="experiment/tasks/<domain>/turn-NN.md")
    ap.add_argument("--rep", required=True, type=int)
    ap.add_argument("--ws-root", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path, help="결과 루트. 세션 결과는 <out>/<session-id>/")
    ap.add_argument("--agent", choices=("claude", "fake"), default="claude")
    ap.add_argument("--max-turns", type=int, default=None, help="앞에서부터 N개의 턴 파일만 실행 (기본: 전부)")
    ap.add_argument("--agent-max-turns", type=int, default=None, help="claude -p 의 --max-turns (기본: 지정 안 함)")
    ap.add_argument("--skip-tests", action="store_true", help="턴마다 기능 테스트를 돌리지 않는다(마지막 턴만)")
    ap.add_argument("--session-mode", choices=("resume", "fresh"), default="resume",
                    help="resume: 턴 2 부터 claude --resume 로 대화를 잇는다. fresh: 턴마다 새 대화(코드·CLAUDE.md 만 이어진다)")
    ap.add_argument("--max-session-cost", type=float, default=None,
                    help="턴이 끝날 때 세션 누적 cost_usd 가 이 값을 넘으면 멈춘다(fail_reason session_cost_cap)")
    ap.add_argument("--rate-limit-poll-s", type=float, default=RATE_LIMIT_POLL_S, help="재시도 시각을 모를 때 대기 간격")
    ap.add_argument("--claude-code-version", default=None,
                    help="claude --version 이 이 버전이 아니면 종료 5 (run_matrix 가 설정의 claude_code_version 을 넘긴다)")
    ap.add_argument("--rate-limit-max-wait-s", type=float, default=RATE_LIMIT_MAX_WAIT_S, help="한 턴의 한도 대기 상한")
    args = ap.parse_args()

    session_id = f"{args.domain}-{args.level}-{args.model}-r{args.rep}"
    task_dir = EXP / "tasks" / args.domain
    turn_files = sorted(task_dir.glob("turn-*.md"))
    if not turn_files:
        print(f"run_session: no turn files in {task_dir}", file=sys.stderr)
        return 2
    if args.max_turns is not None:
        turn_files = turn_files[: args.max_turns]

    args.ws_root.mkdir(parents=True, exist_ok=True)
    ws_root = args.ws_root.resolve()
    # 워크스페이스 경로에 조건(수준·모델·도메인)을 넣지 않는다: 에이전트가 pwd·ls .. 로 읽을 수 있다
    token = secrets.token_hex(6)
    ws = ws_root / token / "lending"
    out_root = args.out.expanduser().resolve()
    out = out_root / session_id
    if ws.parent.exists() or out.exists():
        print(f"run_session: {ws.parent if ws.parent.exists() else out} already exists; refusing to overwrite", file=sys.stderr)
        return 4
    try:
        precheck_ws_root(ws_root)
        cfg = precheck_config_dir()
        if args.agent == "claude" and shutil.which("claude") is None:
            raise PrecheckError("claude CLI not found on PATH")
        billing = precheck_billing(agent_env()) if args.agent == "claude" else None
        if args.agent == "claude" and args.claude_code_version:
            precheck_cli_version(args.claude_code_version, agent_env())
    except PrecheckError as e:
        print(f"run_session: precheck failed: {e}", file=sys.stderr)
        return 5

    started = now_iso()
    t_session = time.monotonic()
    out.mkdir(parents=True)
    # 토큰 → 세션 역추적표. 에이전트가 ls ../.. 로 읽지 못하게 ws_root 밖(결과 루트)에 둔다. 한 줄 append 라 병렬 세션도 안전
    with (out_root / "workspace-index.jsonl").open("a") as f:
        f.write(json.dumps({"token": token, "session_id": session_id, "created_at": started}) + "\n")

    # 시드: 없으면 만든다 (결정적: 같은 해시)
    if not (SEED / ".git").is_dir():
        run(["bash", str(SCRIPTS / "materialize-seed.sh")], check=True)
    seed_commit = git(SEED, "rev-parse", "HEAD")
    shutil.copytree(SEED, ws, symlinks=True)
    p = run(["bash", str(SCRIPTS / "inject-harness.sh"), args.level, str(ws)])
    if p.returncode != 0:
        print(f"run_session: inject-harness failed ({p.returncode}): {p.stderr}", file=sys.stderr)
        return 1
    base = git(ws, "rev-parse", "HEAD")

    env = agent_env()
    cli_version = None
    if args.agent == "claude":
        cli_version = run(["claude", "--version"], env=env).stdout.strip()

    harness_files = [p for p in (EXP / "harness").rglob("*") if p.is_file()]
    grader_files = [ROOT / "lending" / ARCH_TEST_DIR / f
                    for f in ("HexagonalRules.java", "ViolationExtractor.java", "ArchitectureTest.java")]
    grader_files += [SCRIPTS / "grade.sh", SCRIPTS / "overlay-arch.sh"]
    session = {
        "session_id": session_id, "domain": args.domain, "level": args.level, "model": args.model,
        "rep": args.rep, "agent": args.agent, "session_mode": args.session_mode, "model_id": None,
        "claude_code_version": cli_version, "claude_code_version_expected": args.claude_code_version,
        "billing": billing, "max_session_cost": args.max_session_cost,
        "fail_reason": None,
        "agent_session_id": None, "agent_session_id_consistent": None,
        "started_at": started, "ended_at": None, "wall_s": None, "status": "running", "exit_code": None,
        "workspace": str(ws), "workspace_token": token, "seed_commit": seed_commit, "base_commit": base,
        "base_commit_message": git(ws, "log", "-1", "--format=%s"),
        "arch_bench_commit": git(ROOT, "rev-parse", "HEAD"),
        "arch_bench_dirty": bool(git(ROOT, "status", "--porcelain")),
        "harness_sha256": sha256_tree(harness_files, EXP / "harness"),
        "grader_sha256": sha256_tree(grader_files, ROOT),
        "runner_sha256": sha256_tree([Path(__file__).resolve(), FAKE_AGENT], EXP / "runner"),
        "tasks_sha256": sha256_tree(turn_files, task_dir),
        "hidden_tests_sha256": (sha256_tree(hidden_test_files(args.domain, len(turn_files)), HIDDEN_TESTS / args.domain)
                                if hidden_test_files(args.domain, len(turn_files)) else None),
        "claude_config_dir": str(cfg),
        "turns_planned": len(turn_files), "turns_run": 0,
        "input_tokens": 0, "output_tokens": 0, "cache_creation_input_tokens": 0, "cache_read_input_tokens": 0,
        "cost_usd": 0.0, "agent_wall_s": 0.0, "hook_runs": 0, "hook_blocks": 0, "reviewer_calls": 0,
        "agent_commits_turns": 0, "runner_commits_turns": 0, "any_test_tamper": None, "any_config_tamper": False,
        "any_timeout": False, "final_grade_exit": None, "final_violating_classes": None,
        "final_tests_status": None, "ft_pass_by_turn": [], "final_ft_pass": None, "final_ft_total": None,
        "final_ft_passed": None, "final_ft_compile_error": None, "turns_refused": 0, "rate_limited_turns": 0,
        "rate_limit_wait_s": 0.0, "turns": [],
    }
    write_json(out / "session.json", session)

    exit_code = 0
    resume_id: str | None = None
    prev_cost_cumulative = 0.0
    session_ids: list[str] = []
    try:
        for i, tf in enumerate(turn_files, 1):
            turn_name = tf.stem  # turn-NN
            tdir = out / turn_name
            tdir.mkdir()
            prompt = read_prompt(tf)
            harness_state = ws / ".harness"
            # 1) 턴 시작: 훅 재시도 횟수 초기화. 남아 있는 훅 로그(있으면)는 이 턴 기록과 섞이지 않게 따로 둔다
            (harness_state / "retry-count").unlink(missing_ok=True)
            if (harness_state / "hook-log.jsonl").exists():
                shutil.move(harness_state / "hook-log.jsonl", tdir / "hook-log.pre-turn.jsonl")
            head_before = git(ws, "rev-parse", "HEAD")
            resume_from = resume_id if args.session_mode == "resume" else None
            turn = {"turn": i, "turn_file": tf.name, "prompt_sha256": hashlib.sha256(prompt.encode()).hexdigest(),
                    "started_at": now_iso(), "session_mode": args.session_mode, "resumed_from": resume_from,
                    "rate_limited": False, "rate_limit_attempts": 0, "rate_limit_wait_s": 0.0,
                    "rate_limit_resets_at_attempts": [], "rate_limited_cost_usd": 0.0}
            # 2) 에이전트. 구독 한도로 거부되면 그 시도의 기록을 따로 두고, 대기 후 같은 턴을 다시 시작한다
            #    (워크스페이스는 되돌리지 않는다. 거부는 보통 첫 API 호출에서 나므로 변경이 없다 — 있으면 다음 시도가 이어받는다)
            while True:
                turn.update(run_agent(args, ws, prompt, i, resume_from, tdir / "stream.jsonl", tdir / "stderr.txt", env))
                parsed = parse_stream(tdir / "stream.jsonl", ws, ws_root)
                probe = {**turn, **parsed, "_stderr": (tdir / "stderr.txt").read_text(errors="replace")[-5000:]}
                if not is_rate_limited(probe):
                    break
                n = turn["rate_limit_attempts"] = turn["rate_limit_attempts"] + 1
                turn["rate_limited"] = True
                for name in ("stream.jsonl", "stderr.txt"):
                    shutil.move(tdir / name, tdir / f"{Path(name).stem}.rate-limited-{n}{Path(name).suffix}")
                if args.session_mode == "fresh" and parsed["cost_usd_cumulative"]:
                    turn["rate_limited_cost_usd"] = round(turn["rate_limited_cost_usd"] + parsed["cost_usd_cumulative"], 8)
                resets = parsed["rate_limit_resets_at"]
                turn["rate_limit_resets_at_attempts"].append(resets)
                wait = (resets - time.time() + 60) if isinstance(resets, (int, float)) and resets > time.time() else args.rate_limit_poll_s
                if turn["rate_limit_wait_s"] + wait > args.rate_limit_max_wait_s:
                    turn["agent_status"] = "rate_limit_timeout"
                    break
                print(f"[{session_id}] {turn_name}: rate limited (attempt {n}); waiting {round(wait)}s", flush=True)
                time.sleep(wait)
                turn["rate_limit_wait_s"] = round(turn["rate_limit_wait_s"] + wait, 1)
            # 이 턴의 훅 로그를 결과로 옮긴다(없으면 빈 파일)
            if (harness_state / "hook-log.jsonl").exists():
                shutil.move(harness_state / "hook-log.jsonl", tdir / "hook-log.jsonl")
            else:
                (tdir / "hook-log.jsonl").touch()
            turn.update(hook_summary(tdir / "hook-log.jsonl"))
            # 3) 스트림 파싱
            turn.update(parsed)
            cum = parsed["cost_usd_cumulative"]
            if cum is None:
                turn["cost_usd"] = None
            elif args.session_mode == "fresh":  # 턴마다 새 대화: 누적값이 곧 이번 턴 비용
                turn["cost_usd"] = round(cum + turn["rate_limited_cost_usd"], 8)
            else:
                # 새 대화로 시작했으면(첫 턴, resume 실패) 누적값이 줄어든다 → 그 자체가 이번 턴 비용
                turn["cost_usd"] = round(cum - prev_cost_cumulative if cum >= prev_cost_cumulative else cum, 8)
                prev_cost_cumulative = cum
            if parsed["agent_session_id"]:
                session_ids.append(parsed["agent_session_id"])
                resume_id = parsed["agent_session_id"]
            if turn["agent_status"] == "ok" and (parsed["result_is_error"] or parsed["result_events"] == 0):
                turn["agent_status"] = "error"
            # 4) 커밋 확인  5) tamper
            turn.update(commit_check(ws, head_before))
            # 거절 판정은 자동으로 no_change 만 본다. 최종 응답 앞부분을 남겨 사람이 거절·대안 제시인지 확인한다
            turn["task_refused"] = turn["no_change"] and turn["agent_status"] == "ok"
            turn["refusal_text"] = (parsed["result_text"] or "")[:500] if turn["task_refused"] else None
            turn.update(tamper_check(ws, base, args.level))
            # 6) 채점
            turn.update(grade(ws, tdir / "arch.json", tdir / "grade.log"))
            # 7) 숨겨진 기능 테스트(턴 1~i 누적)  8) 에이전트 워크스페이스 자체 테스트
            turn.update(hidden_tests(ws, args.domain, i, tdir / "ft.log"))
            if not args.skip_tests or i == len(turn_files):
                turn.update(functional_tests(ws, tdir / "test.log"))
            turn["ended_at"] = now_iso()
            # 9) 기록
            write_json(tdir / "turn.json", turn)

            session["turns_run"] = i
            for k in ("input_tokens", "output_tokens", "cache_creation_input_tokens", "cache_read_input_tokens",
                      "hook_runs", "hook_blocks", "reviewer_calls"):
                session[k] += turn[k] or 0
            session["cost_usd"] = round(session["cost_usd"] + (turn["cost_usd"] or 0.0), 6)
            session["agent_wall_s"] = round(session["agent_wall_s"] + turn["agent_wall_s"], 2)
            session["agent_commits_turns"] += turn["agent_committed"]
            session["turns_refused"] += turn["task_refused"]
            session["rate_limited_turns"] += turn["rate_limited"]
            session["rate_limit_wait_s"] = round(session["rate_limit_wait_s"] + turn["rate_limit_wait_s"], 1)
            session["runner_commits_turns"] += turn["runner_committed"]
            if turn["test_tamper"] is not None:
                session["any_test_tamper"] = bool(session["any_test_tamper"]) or turn["test_tamper"]
            session["any_config_tamper"] = session["any_config_tamper"] or turn["config_tamper"]
            session["any_timeout"] = session["any_timeout"] or turn["agent_status"] == "timeout"
            session["model_id"] = session["model_id"] or turn["model_id"]
            if turn["claude_code_version"] and not session["claude_code_version"]:
                session["claude_code_version"] = turn["claude_code_version"]
            session["final_grade_exit"] = turn["grade_exit"]
            session["final_violating_classes"] = turn["violating_classes"]
            session["final_tests_status"] = turn.get("tests_status", session["final_tests_status"])
            session["ft_pass_by_turn"].append(turn["ft_pass"])
            session["final_ft_pass"] = turn["ft_pass"]
            session["final_ft_total"] = turn["ft_total"]
            session["final_ft_passed"] = turn["ft_passed"]
            session["final_ft_compile_error"] = turn["ft_compile_error"]
            session["turns"].append({k: turn.get(k) for k in (
                "turn", "session_mode", "agent_status", "agent_wall_s", "agent_session_id", "rate_limited",
                "rate_limit_wait_s", "rate_limit_utilization", "task_refused", "input_tokens", "output_tokens",
                "cache_creation_input_tokens", "cache_read_input_tokens", "cost_usd", "cost_usd_cumulative", "hook_runs", "hook_blocks",
                "reviewer_calls", "reviewer_verdicts", "escaped_cwd_count", "agent_committed", "runner_committed", "no_change",
                "test_tamper", "config_tamper", "grade_exit", "violating_classes", "r1_count", "r2_count",
                "r3_count", "r4_count", "r5_count", "r6_count", "r7_count", "ft_status", "ft_total", "ft_passed", "ft_pass",
                "ft_compile_error", "ft_failed_names", "tests_status", "tests_total",
                "tests_failed", "parse_errors")})
            write_json(out / "session.json", session)
            print(f"[{session_id}] {turn_name}: agent={turn['agent_status']} {turn['agent_wall_s']}s "
                  f"committed={turn['agent_committed']} grade={turn['grade_exit']} vc={turn['violating_classes']} "
                  f"r1={turn['r1_count']} test_tamper={turn['test_tamper']} ft={turn['ft_passed']}/{turn['ft_total']} "
                  f"tests={turn.get('tests_status')} "
                  f"cost={turn['cost_usd']}", flush=True)

            if turn["agent_status"] == "rate_limit_timeout":
                print(f"run_session: usage limit wait on {turn_name} would exceed {args.rate_limit_max_wait_s}s; stopping",
                      file=sys.stderr)
                session["fail_reason"], exit_code = "rate_limit_timeout", 1
                break
            if turn["agent_status"] == "error":
                print(f"run_session: agent error on {turn_name}; stopping (see {tdir}/stderr.txt)", file=sys.stderr)
                session["fail_reason"], exit_code = "agent_error", 1
                break
            if args.session_mode == "resume" and resume_id is None and i < len(turn_files):
                print("run_session: no session id from agent; cannot resume next turn", file=sys.stderr)
                session["fail_reason"], exit_code = "no_session_id", 1
                break
            if args.max_session_cost is not None and session["cost_usd"] > args.max_session_cost and i < len(turn_files):
                print(f"run_session: session cost {session['cost_usd']} > {args.max_session_cost} after {turn_name}; stopping",
                      file=sys.stderr)
                session["fail_reason"], exit_code = "session_cost_cap", 1
                break
    except KeyboardInterrupt:
        session["fail_reason"], exit_code = "interrupted", 1

    session["agent_session_id"] = session_ids[0] if session_ids else None
    session["agent_session_id_consistent"] = (len(set(session_ids)) == 1) if session_ids else None
    # fresh 모드의 기대값: 턴마다 다른 ID (같은 ID 가 다시 나오면 대화가 이어졌다는 뜻)
    session["agent_session_ids_distinct"] = (len(set(session_ids)) == len(session_ids)) if session_ids else None
    session["status"] = "completed" if exit_code == 0 else "failed"  # 상태는 running·completed·failed 셋뿐
    session["exit_code"] = exit_code
    session["ended_at"] = now_iso()
    session["wall_s"] = round(time.monotonic() - t_session, 2)
    write_json(out / "session.json", session)
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
