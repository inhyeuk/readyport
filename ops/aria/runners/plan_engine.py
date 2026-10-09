"""여행 계획 생성 엔진 — **바꿔 끼우는 곳은 이 파일의 generate() 하나뿐**.

사장님 결정(2026-10-09 ②): 엔진은 ARIA 가 지금 Claude 를 쓰는 방식(Claude Code 헤드리스) 그대로.
⚠ 검토 메모: Anthropic 소비자 약관상 구독 계정을 타인 대상 서비스 엔진으로 쓰는 것은 문제 될 소지가 있다고 보고했고,
사장님이 그대로 진행을 결정. 나중에 API 키 방식으로 바꿀 때는 generate() 안만 바꾸면 된다(PLAN_ENGINE=anthropic_api 등).

- 빈 임시 폴더에서 실행한다(저장소·개인 파일을 읽지 않게). 도구는 모두 막는다 — 지시문 안의 자료만 쓴다.
- 지시문은 표준 입력으로 넘긴다(명령줄·프로세스 목록에 요청 내용이 보이지 않게, 길이 제한 회피).
- 지시문·응답은 어디에도 기록하지 않는다(부르는 쪽도 id·상태만 남긴다).
"""
from __future__ import annotations

import json
import re
import subprocess
import tempfile
from dataclasses import dataclass
from typing import Any, Callable, Optional

from . import claude_headless

ENGINE_NAME = "claude_code_headless"
DISALLOWED_TOOLS = ["Bash", "Edit", "Write", "Read", "Glob", "Grep", "WebFetch", "WebSearch", "NotebookEdit", "Task"]


@dataclass
class EngineResult:
    status: str                  # ok / error / timeout / refused / bad_output
    data: Optional[dict] = None
    reason: str = ""


def extract_json_object(text: str) -> Optional[dict]:
    """응답 글에서 JSON 객체 하나. ```json 울타리나 앞뒤 말이 섞여도 첫 '{' ~ 짝 맞는 '}' 를 찾는다."""
    text = text or ""
    m = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, re.S)
    candidates = [m.group(1)] if m else []
    start = text.find("{")
    if start >= 0:
        depth, in_str, esc = 0, False, False
        for i in range(start, len(text)):
            ch = text[i]
            if in_str:
                if esc:
                    esc = False
                elif ch == "\\":
                    esc = True
                elif ch == '"':
                    in_str = False
                continue
            if ch == '"':
                in_str = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    candidates.append(text[start:i + 1])
                    break
    for c in candidates:
        try:
            obj = json.loads(c)
        except ValueError:
            continue
        if isinstance(obj, dict):
            return obj
    return None


def generate(prompt: str, *, claude_bin: str = "claude", timeout_sec: int = 600,
             runner: Callable[..., Any] = subprocess.run) -> EngineResult:
    """지시문 → 계획 JSON(dict). 엔진을 바꾸려면 이 함수만 바꾼다."""
    with tempfile.TemporaryDirectory(prefix="rp-plan-") as empty:
        res = claude_headless.run_prompt(prompt, cwd=empty, claude_bin=claude_bin, timeout_sec=timeout_sec,
                                         allowed_tools=[], disallowed_tools=DISALLOWED_TOOLS, via_stdin=True,
                                         runner=runner)
    if res.status != "ok":
        return EngineResult(res.status, None, res.reason)
    data = extract_json_object(res.text)
    if data is None:
        return EngineResult("bad_output", None, "JSON 없음")
    return EngineResult("ok", data)
