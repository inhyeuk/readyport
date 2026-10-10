"""Claude Code 헤드리스 실행 래퍼 (ARIA_OPS 12.2-4).

    claude -p "<작업 지시>" --allowedTools "<필요 도구만>" --output-format json

- `--bare` 는 CLAUDE.md 를 읽지 않으므로 절대 쓰지 않는다(넣으면 예외) [재확인: CLI 동작].
- 작업 폴더 = 저장소 루트. 브랜치 이름 `claude/<slug>`.
- 같은 지문은 한 번만, 하루 상한(CLAUDE_DAILY_CAP) — fingerprint_store.claim_claude_run.
- 사람이 텔레그램으로 approve(review=accepted) 한 지문만 실행한다.
- subprocess 실행 함수는 주입할 수 있다(테스트는 가짜).
"""
from __future__ import annotations

import json
import re
import shutil
import subprocess
from dataclasses import dataclass, field
from typing import Any, Callable, Optional

# 필요한 도구만. Bash 는 명령 앞부분을 좁혀서 허용한다 [재확인: --allowedTools 규칙 문법]
DEFAULT_ALLOWED_TOOLS = [
    "Read", "Edit", "Write", "Glob", "Grep",
    "Bash(python tools/packs/build_packs.py --check)",
    "Bash(git status*)", "Bash(git diff*)", "Bash(git switch -c claude/*)", "Bash(git checkout -b claude/*)",
    "Bash(git add *)", "Bash(git commit *)", "Bash(git push -u origin claude/*)",
    "Bash(gh pr create *)",
]

FORBIDDEN_ARGS = {"--bare", "--dangerously-skip-permissions"}
PR_URL_RE = re.compile(r"https://github\.com/[\w.-]+/[\w.-]+/pull/\d+")

PROMPT_TEMPLATE = """레디포트 저장소 작업 (ARIA 가 요청, 사람 승인 받음 — 지문 {fp}).

변경 감지 요약: {summary}
감지기/범위: {detector} / {unit}
증거 파일(읽기 전용): {evidence_path}
수정 범위: {scope} — 이 국가·양식 밖은 고치지 않는다.

할 일:
1. 증거 파일과 해당 국가 팩(packs/src/…)·레시피(packs/src/recipes/…)를 읽고, 공식 출처로 확인되는 부분만 고친다.
   확인 안 되는 값은 지어내지 말고 PR 설명에 '[확인 필요]'로 적는다. 바꾼 항목은 source·last_verified 를 채운다.
2. `python tools/packs/build_packs.py --check` 를 돌려 통과해야만 다음으로 간다. 실패하면 PR 을 만들지 말고 이유를 보고한다.
3. 브랜치 `{branch}` 를 만들어 커밋하고 `git push -u origin {branch}` 후 `gh pr create` 로 PR 을 연다.
   PR 설명에 변경 이유·출처·지문을 적는다.

반드시 지킬 규칙 (CLAUDE.md 작업 규칙):
- 정부 사이트에 어떤 것도 제출·입력하지 않는다. 구조 확인은 조회까지만.
- 사람 확인(캡차·Turnstile)을 우회하거나 대신 풀지 않는다.
- 여권·예약 등 개인정보를 코드·로그·PR 에 넣지 않는다.
- main 브랜치에 직접 푸시하지 않는다. claude/ 브랜치와 PR 만.
- 비자 필요 여부·기한·수수료처럼 정책 의미가 바뀌는 수정은 PR 설명 맨 위에 '정책 의미 변경 — 사람 확인 필수'라고 적는다.
- 서명 키·배포(firebase deploy)·Remote Config 는 건드리지 않는다.
마지막 응답에 PR 주소를 한 줄로 적는다.
"""


@dataclass
class ClaudeRunResult:
    status: str                 # ok / error / timeout / refused / not_run
    reason: str = ""
    pr_url: Optional[str] = None
    branch: str = ""
    raw: Any = None
    argv: list[str] = field(default_factory=list)


def slugify(text: str, fp: str) -> str:
    base = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")[:40] or "change"
    return f"{base}-{fp[:8]}"


def branch_name(unit: str, fp: str) -> str:
    return f"claude/{slugify(unit, fp)}"


def build_prompt(change: dict, scope: str, branch: str) -> str:
    return PROMPT_TEMPLATE.format(
        fp=change["fingerprint"], summary=str(change.get("summary", ""))[:300],
        detector=change.get("detector", ""), unit=change.get("unit", ""),
        evidence_path=change.get("evidence_path") or "(없음)", scope=scope, branch=branch,
    )


def _check_tools(tools: list[str]) -> None:
    if any(t.strip().startswith("-") or "," in t for t in tools):
        raise ValueError("도구 목록에 옵션·쉼표를 넣을 수 없다")


def build_argv(claude_bin: str, prompt: Optional[str], allowed_tools: list[str], *,
               disallowed_tools: Optional[list[str]] = None, add_dirs: Optional[list[str]] = None) -> list[str]:
    """prompt=None 이면 지시문을 표준 입력으로 넘긴다(긴 지시문: 윈도 명령줄 길이 제한 회피) [재확인: claude -p 가 stdin 을 읽음]."""
    _check_tools(allowed_tools)
    _check_tools(disallowed_tools or [])
    argv = [claude_bin, "-p"] + ([prompt] if prompt is not None else [])
    if allowed_tools:
        argv += ["--allowedTools", ",".join(allowed_tools)]
    if disallowed_tools:
        argv += ["--disallowedTools", ",".join(disallowed_tools)]
    for d in add_dirs or []:
        if str(d).startswith("-"):
            raise ValueError("폴더 이름이 옵션처럼 보인다")
        argv += ["--add-dir", str(d)]
    argv += ["--output-format", "json"]
    bad = FORBIDDEN_ARGS & set(argv)
    if bad:
        raise ValueError(f"금지 인자: {bad}")
    return argv


def resolve_bin(claude_bin: str) -> tuple[Optional[str], str]:
    """(실행 파일, 거절 이유). .cmd/.bat 은 cmd.exe 를 거쳐 여러 줄 지시문이 깨지므로 거절한다."""
    exe = shutil.which(claude_bin) or claude_bin
    if exe.lower().endswith((".cmd", ".bat")):
        return None, f"{exe}: .cmd/.bat 은 쓰지 않음 — CLAUDE_BIN 을 claude.exe 경로로"
    return exe, ""


@dataclass
class PromptResult:
    status: str                 # ok / error / timeout / refused
    text: str = ""              # Claude 의 마지막 응답 글(result)
    reason: str = ""
    data: Any = None
    argv: list[str] = field(default_factory=list)


def run_prompt(prompt: str, *, cwd, claude_bin: str = "claude", timeout_sec: int = 1800,
               allowed_tools: Optional[list[str]] = None, disallowed_tools: Optional[list[str]] = None,
               add_dirs: Optional[list[str]] = None, via_stdin: bool = True,
               runner: Callable[..., Any] = subprocess.run) -> PromptResult:
    """Claude Code 헤드리스 한 번 실행(상한 확인은 부르는 쪽). 지시문·결과 글은 로그에 남기지 않는다."""
    exe, why = resolve_bin(claude_bin)
    if exe is None:
        return PromptResult("refused", reason=why)
    argv = build_argv(exe, None if via_stdin else prompt, allowed_tools or [],
                      disallowed_tools=disallowed_tools, add_dirs=add_dirs)
    kw = dict(cwd=str(cwd), capture_output=True, text=True, encoding="utf-8", timeout=timeout_sec, shell=False)
    if via_stdin:
        kw["input"] = prompt
    try:
        proc = runner(argv, **kw)
    except subprocess.TimeoutExpired:
        return PromptResult("timeout", reason=f"{timeout_sec}초 초과", argv=argv)
    except OSError as e:
        return PromptResult("error", reason=f"실행 실패: {type(e).__name__}", argv=argv)
    try:
        data = parse_output(proc.stdout)
    except ValueError as e:
        return PromptResult("error", reason=f"{e}; 종료코드 {proc.returncode}", argv=argv)
    text = str(data.get("result", ""))
    is_error = bool(data.get("is_error")) or proc.returncode != 0
    if is_error and ("Not logged in" in text or "/login" in text):
        return PromptResult("unavailable", text[:80], "Claude CLI 로그인 안 됨 — 사람이 `claude` 를 열어 /login 해야 함", data, argv)
    return PromptResult("error" if is_error else "ok", text, "" if not is_error else "Claude 오류 응답", data, argv)


def parse_output(stdout: str) -> dict:
    """--output-format json 결과. 앞뒤에 다른 줄이 섞여도 마지막 JSON 객체를 찾는다."""
    stdout = (stdout or "").strip()
    try:
        return json.loads(stdout)
    except json.JSONDecodeError:
        pass
    for line in reversed(stdout.splitlines()):
        line = line.strip()
        if line.startswith("{"):
            try:
                return json.loads(line)
            except json.JSONDecodeError:
                continue
    raise ValueError("Claude 출력에서 JSON 을 찾지 못함")


def run_for_change(change: dict, store, repo_root, scope: str, *, claude_bin: str = "claude",
                   daily_cap: int = 3, timeout_sec: int = 1800,
                   allowed_tools: Optional[list[str]] = None,
                   runner: Callable[..., Any] = subprocess.run, today=None) -> ClaudeRunResult:
    """승인된 변경 하나에 대해 Claude 를 한 번 실행한다."""
    fp = change["fingerprint"]
    branch = branch_name(change.get("unit", "change"), fp)
    prompt = build_prompt(change, scope, branch)
    exe, why = resolve_bin(claude_bin)
    if exe is None:
        # cmd.exe 를 거치면 여러 줄 지시문이 깨진다. CLAUDE_BIN 에 claude.exe 경로를 준다. (상한은 쓰지 않음)
        return ClaudeRunResult("refused", why, branch=branch)
    argv = build_argv(exe, prompt, allowed_tools or DEFAULT_ALLOWED_TOOLS)
    ok, why = store.claim_claude_run(fp, daily_cap, today=today)
    if not ok:
        return ClaudeRunResult("not_run", why, branch=branch)
    try:
        proc = runner(argv, cwd=str(repo_root), capture_output=True, text=True, encoding="utf-8",
                      timeout=timeout_sec, shell=False)
    except subprocess.TimeoutExpired:
        store.record_claude_result(fp, "timeout")
        return ClaudeRunResult("timeout", f"{timeout_sec}초 초과", branch=branch, argv=argv)
    except OSError as e:
        store.record_claude_result(fp, "error")
        return ClaudeRunResult("error", f"실행 실패: {e}", branch=branch, argv=argv)
    try:
        data = parse_output(proc.stdout)
    except ValueError as e:
        store.record_claude_result(fp, "error")
        return ClaudeRunResult("error", f"{e}; 종료코드 {proc.returncode}", branch=branch, argv=argv,
                               raw=(proc.stderr or "")[-1000:])
    text = str(data.get("result", ""))
    m = PR_URL_RE.search(text)
    pr = m.group(0) if m else None
    is_error = bool(data.get("is_error")) or proc.returncode != 0
    status = "error" if is_error else "ok"
    store.record_claude_result(fp, status if pr or is_error else "ok_no_pr", pr)
    return ClaudeRunResult(status, "" if not is_error else text[:500], pr, branch, data, argv)
