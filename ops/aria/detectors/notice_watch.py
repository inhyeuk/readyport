"""각국 공지 페이지 글 변경 감지 (GET 만).

설정 NOTICE_URLS = "ID|https://...,ID|https://..." 목록의 페이지를 받아 보이는 글만 뽑고,
공백을 정리한 뒤 해시를 비교한다. 바뀌면 앞뒤 글 차이(unified diff)를 증거로 남긴다.
봇 차단 화면이면 우회하지 않고 manual_check_needed.
"""
from __future__ import annotations

import difflib
import hashlib
import re
from html.parser import HTMLParser

from ..evidence import save_evidence
from ..models import (STATUS_BASELINE, STATUS_CHANGED, STATUS_ERROR, STATUS_MANUAL, STATUS_UNCHANGED, Change,
                      UnitResult)
from ..net import NetworkError, looks_like_bot_challenge

DETECTOR = "notice_watch"
MAX_DIFF_LINES = 200


class _TextParser(HTMLParser):
    SKIP = {"script", "style", "noscript", "template", "svg", "head"}
    BLOCK = {"p", "div", "li", "tr", "br", "h1", "h2", "h3", "h4", "h5", "h6", "section", "article", "td", "th"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []
        self._skip = 0

    def handle_starttag(self, tag, attrs):
        if tag in self.SKIP:
            self._skip += 1
        elif tag in self.BLOCK:
            self.parts.append("\n")

    def handle_endtag(self, tag):
        if tag in self.SKIP and self._skip:
            self._skip -= 1
        elif tag in self.BLOCK:
            self.parts.append("\n")

    def handle_data(self, data):
        if not self._skip:
            self.parts.append(data)


def normalize_text(html_text: str) -> str:
    p = _TextParser()
    p.feed(html_text)
    p.close()
    lines = []
    for line in "".join(p.parts).splitlines():
        line = re.sub(r"\s+", " ", line).strip()
        if line:
            lines.append(line)
    return "\n".join(lines)


def text_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def check_notice(notice_id: str, url: str, store, fetcher, cfg=None, persist: bool = True,
                 timeout: float = 20.0, user_agent: str = "") -> UnitResult:
    unit = f"notice:{notice_id}"
    headers = {"User-Agent": user_agent} if user_agent else {}
    try:
        resp = fetcher("GET", url, headers=headers, timeout=timeout)
    except NetworkError as e:
        return UnitResult(unit, STATUS_ERROR, message=str(e))
    if looks_like_bot_challenge(resp):
        return UnitResult(unit, STATUS_MANUAL, message=f"봇 차단 또는 HTTP {resp.status} — 수동 확인 필요")
    if resp.status != 200:
        return UnitResult(unit, STATUS_ERROR, message=f"HTTP {resp.status}")
    text = normalize_text(resp.text)
    h = text_hash(text)
    key = f"notice_text:{notice_id}"
    prev = store.get_value(key)
    if prev is None:
        if persist:
            store.set_value(key, {"hash": h, "text": text, "url": url})
        return UnitResult(unit, STATUS_BASELINE, message="기준 글 저장")
    if prev.get("hash") == h:
        return UnitResult(unit, STATUS_UNCHANGED)
    diff_lines = list(difflib.unified_diff(prev.get("text", "").splitlines(), text.splitlines(),
                                           "before", "after", lineterm="", n=1))
    truncated = len(diff_lines) > MAX_DIFF_LINES
    diff = {"notice_id": notice_id, "url": url, "hash_before": prev.get("hash"), "hash_after": h,
            "unified_diff": diff_lines[:MAX_DIFF_LINES], "truncated": truncated}
    added = sum(1 for d in diff_lines if d.startswith("+") and not d.startswith("+++"))
    removed = sum(1 for d in diff_lines if d.startswith("-") and not d.startswith("---"))
    ch = Change(DETECTOR, unit, f"[공지] {notice_id}: 글이 바뀜 (+{added} / -{removed}줄)", diff)
    if persist and cfg is not None:
        ch.evidence_path = save_evidence(cfg.evidence_dir, DETECTOR, notice_id,
                                         {**diff, "text_before": prev.get("text", ""), "text_after": text},
                                         ch.fingerprint)
    if persist:
        store.set_value(key, {"hash": h, "text": text, "url": url})
    return UnitResult(unit, STATUS_CHANGED, [ch], message=ch.summary, evidence_path=ch.evidence_path)
