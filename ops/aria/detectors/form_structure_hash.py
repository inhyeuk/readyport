"""양식 공식 페이지의 '구조 해시' 변경 감지. 조회(GET)만 한다.

절대 하지 않는 것: 입력, 제출, 버튼 누르기, 로그인, 사람 확인(캡차·Turnstile) 풀기·우회, 반복 재시도.
봇 차단 화면이거나 403/429/503 이면 바로 "manual_check_needed" 로 끝낸다(운영자가 직접 확인).

구조 = 태그 이름 순서 + 입력 칸(input/select/textarea/button/form)의 name·id·type·formcontrolname.
글자 내용, 스크립트·스타일, nonce·csrf 같은 값(value), 숫자만 바뀌는 자동 id 는 무시한다.
주의: 화면을 스크립트로 그리는 사이트(SPA)는 GET 으로 껍데기만 보인다. 이때 해시는 껍데기 변경만 잡는다.
"""
from __future__ import annotations

import hashlib
import re
from html.parser import HTMLParser
from typing import Optional

from ..evidence import save_evidence
from ..models import (STATUS_BASELINE, STATUS_CHANGED, STATUS_ERROR, STATUS_MANUAL, STATUS_UNCHANGED, Change,
                      UnitResult)
from ..net import NetworkError, looks_like_bot_challenge

DETECTOR = "form_structure_hash"

SKIP_CONTENT = {"script", "style", "noscript", "template", "svg"}
SKIP_TAGS = SKIP_CONTENT | {"meta", "link", "base"}
CONTROL_TAGS = {"form", "input", "select", "textarea", "button", "fieldset"}
CONTROL_ATTRS = ("name", "id", "type", "formcontrolname", "role")
# option·optgroup 은 보지 않는다 (서버가 선택 목록을 바꿔도 구조 변경으로 치지 않음)
_DIGITS = re.compile(r"\d+")
_VOLATILE_NAME = re.compile(r"(csrf|xsrf|token|nonce|__requestverification|captcha|turnstile|recaptcha)", re.I)


def _norm_ident(v: str) -> str:
    return _DIGITS.sub("#", v.strip())


class _StructureParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.tokens: list[str] = []
        self.controls: list[dict[str, str]] = []
        self._skip_depth = 0

    def handle_starttag(self, tag, attrs):
        tag = tag.lower()
        if self._skip_depth:
            if tag in SKIP_CONTENT:
                self._skip_depth += 1
            return
        if tag in SKIP_CONTENT:
            self._skip_depth = 1
            return
        if tag in SKIP_TAGS or tag in ("option", "optgroup"):
            return
        self.tokens.append(tag)
        if tag in CONTROL_TAGS:
            a = {k.lower(): (v or "") for k, v in attrs}
            ctl = {"tag": tag}
            for k in CONTROL_ATTRS:
                if a.get(k):
                    val = _norm_ident(a[k]) if k in ("id", "name", "formcontrolname") else a[k].lower()
                    ctl[k] = val
            if any(_VOLATILE_NAME.search(ctl.get(k, "")) for k in ("name", "id")):
                ctl = {"tag": tag, "volatile": "1", "type": ctl.get("type", "")}
            self.controls.append(ctl)

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        if tag.lower() in SKIP_CONTENT and self._skip_depth:
            self._skip_depth -= 1

    def handle_endtag(self, tag):
        if self._skip_depth and tag.lower() in SKIP_CONTENT:
            self._skip_depth -= 1


def extract_structure(html_text: str) -> dict:
    p = _StructureParser()
    p.feed(html_text)
    p.close()
    return {"tags": p.tokens, "controls": p.controls}


def structure_hash(structure: dict) -> str:
    parts = ["T:" + ",".join(structure["tags"])]
    for c in structure["controls"]:
        parts.append("C:" + ";".join(f"{k}={c[k]}" for k in sorted(c)))
    return hashlib.sha256("\n".join(parts).encode("utf-8")).hexdigest()


def controls_diff(before: list[dict], after: list[dict]) -> dict:
    def key(c):
        return ";".join(f"{k}={c[k]}" for k in sorted(c))
    b = {key(c) for c in before}
    a = {key(c) for c in after}
    return {"controls_added": sorted(a - b), "controls_removed": sorted(b - a)}


def check_form(form_id: str, url: str, store, fetcher, cfg=None, persist: bool = True,
               timeout: float = 20.0, user_agent: str = "") -> UnitResult:
    """양식 하나 확인. GET 한 번만. 재시도는 호출하는 쪽이 error 일 때만 한다(차단이면 안 함)."""
    if not url.startswith("https://"):
        return UnitResult(form_id, STATUS_ERROR, message="https 주소가 아님")
    headers = {"User-Agent": user_agent} if user_agent else {}
    try:
        resp = fetcher("GET", url, headers=headers, timeout=timeout)
    except NetworkError as e:
        return UnitResult(form_id, STATUS_ERROR, message=str(e))
    if looks_like_bot_challenge(resp):
        # 우회하지 않는다. 운영자가 직접 열어 보고 판단한다.
        return UnitResult(form_id, STATUS_MANUAL,
                          message=f"봇 차단/사람 확인 화면 또는 HTTP {resp.status} — 수동 확인 필요")
    if resp.status != 200:
        return UnitResult(form_id, STATUS_ERROR, message=f"HTTP {resp.status}")

    structure = extract_structure(resp.text)
    h = structure_hash(structure)
    key = f"form_hash:{form_id}"
    prev: Optional[dict] = store.get_value(key)
    if prev is None:
        if persist:
            store.set_value(key, {"hash": h, "controls": structure["controls"], "url": url})
        return UnitResult(form_id, STATUS_BASELINE, message=f"기준 해시 저장 {h[:12]}")
    if prev.get("hash") == h:
        return UnitResult(form_id, STATUS_UNCHANGED, message=f"해시 같음 {h[:12]}")

    cdiff = controls_diff(prev.get("controls", []), structure["controls"])
    diff = {"form_id": form_id, "url": url, "hash_before": prev.get("hash"), "hash_after": h, **cdiff}
    if cdiff["controls_added"] or cdiff["controls_removed"]:
        summary = (f"[양식 구조] {form_id}: 입력 칸 변화 (+{len(cdiff['controls_added'])}"
                   f" / -{len(cdiff['controls_removed'])}) — 레시피 선택자 확인 필요")
    else:
        summary = f"[양식 구조] {form_id}: 페이지 구조가 바뀜(입력 칸 이름은 같음)"
    ch = Change(DETECTOR, form_id, summary, diff)
    if persist and cfg is not None:
        ch.evidence_path = save_evidence(cfg.evidence_dir, DETECTOR, form_id,
                                         {**diff, "structure_after": structure}, ch.fingerprint)
        save_evidence(cfg.evidence_dir, DETECTOR, form_id, resp.text, ch.fingerprint, ext="html")
    if persist:
        # 새 해시를 기준으로 삼는다. 같은 변경은 지문으로 한 번만 처리된다.
        store.set_value(key, {"hash": h, "controls": structure["controls"], "url": url})
    return UnitResult(form_id, STATUS_CHANGED, [ch], message=summary, evidence_path=ch.evidence_path)
