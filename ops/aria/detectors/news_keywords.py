"""뉴스 제목에서 입국정책 키워드 찾기 (순수 함수, 네트워크 없음).

ARIA 기존 뉴스 파이프라인 연결 방법(어댑터 설명)은 아래 ADAPTER_DOC 참고.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any, Iterable, Optional

from ..models import Change

DETECTOR = "news_keywords"

# 키워드 → 관련 양식/나라 (없으면 None). 영문 약어는 단어 경계로, 한글은 부분 일치로 찾는다.
DEFAULT_KEYWORDS: dict[str, Optional[str]] = {
    "TDAC": "TH_TDAC",
    "Thailand Digital Arrival Card": "TH_TDAC",
    "태국 입국신고": "TH_TDAC",
    "SG Arrival Card": "SG_SGAC",
    "SGAC": "SG_SGAC",
    "싱가포르 입국신고": "SG_SGAC",
    "MDAC": "MY_MDAC",
    "Malaysia Digital Arrival Card": "MY_MDAC",
    "말레이시아 입국신고": "MY_MDAC",
    "All Indonesia": "ID_ALL_INDONESIA",
    "e-CD": "ID_ALL_INDONESIA",
    "인도네시아 입국신고": "ID_ALL_INDONESIA",
    "Visit Japan Web": "JP_VJW",
    "비지트 재팬 웹": "JP_VJW",
    "ETIAS": None,
    "EES": None,
    "K-ETA": None,
    "arrival card": None,
    "e-Arrival": None,
    "entry requirement": None,
    "visa-free": None,
    "visa exemption": None,
    "입국신고": None,
    "입국신고서": None,
    "무비자": None,
    "입국 요건": None,
    "입국요건": None,
    "전자여행허가": None,
}

ADAPTER_DOC = """
ARIA 뉴스 파이프라인 어댑터 (운영자가 ARIA 쪽에 연결):
1. ARIA 가 이미 모은 뉴스 목록을 [{"title": str, "url": str, "ts": ISO8601 문자열}] 로 바꾼다.
2. matches = match_entry_policy_news(items) 를 부른다. (네트워크·LLM 없음)
3. changes = to_changes(matches) → 각 Change 를 fingerprint_store.register() 로 한 번만 처리.
4. 새 것만 approvals.telegram_cmd.format_news_alert(...) 로 텔레그램에 보낸다.
뉴스 본문은 저장하지 않는다(콘텐츠 복제 금지). 제목·주소·시각만 쓴다.
"""


@dataclass
class NewsMatch:
    title: str
    url: str
    ts: str
    keywords: list[str]
    form_ids: list[str]


def _compile(keywords: Iterable[str]) -> list[tuple[str, re.Pattern]]:
    out = []
    for kw in keywords:
        if re.fullmatch(r"[A-Za-z0-9 .\-]+", kw):
            pat = re.compile(r"(?<![A-Za-z0-9])" + re.escape(kw) + r"(?![A-Za-z0-9])", re.I)
        else:
            pat = re.compile(re.escape(kw), re.I)
        out.append((kw, pat))
    return out


def _item_fields(item: Any) -> tuple[str, str, str]:
    if isinstance(item, dict):
        return str(item.get("title", "")), str(item.get("url", "")), str(item.get("ts", ""))
    title, url, ts = item
    return str(title), str(url), str(ts)


def match_entry_policy_news(items: Iterable[Any], keywords: Optional[dict[str, Optional[str]]] = None) -> list[NewsMatch]:
    """뉴스 목록(dict 또는 (title,url,ts) 튜플) 중 키워드가 제목에 있는 것만. 같은 url 은 한 번."""
    kws = keywords if keywords is not None else DEFAULT_KEYWORDS
    compiled = _compile(kws)
    seen_urls: set[str] = set()
    out = []
    for item in items:
        title, url, ts = _item_fields(item)
        if not title or url in seen_urls:
            continue
        hit = [kw for kw, pat in compiled if pat.search(title)]
        if not hit:
            continue
        seen_urls.add(url)
        forms = sorted({kws[k] for k in hit if kws.get(k)})
        out.append(NewsMatch(title, url, ts, hit, forms))
    return out


def to_changes(matches: Iterable[NewsMatch]) -> list[Change]:
    """지문은 url 기준(같은 기사는 한 번만)."""
    out = []
    for m in matches:
        unit = m.form_ids[0] if len(m.form_ids) == 1 else "news"
        diff = {"url": m.url}
        summary = f"[뉴스] {m.title[:120]} (키워드: {', '.join(m.keywords[:4])})"
        ch = Change(DETECTOR, unit, summary, diff)
        ch.diff = {"url": m.url, "title": m.title, "ts": m.ts, "keywords": m.keywords, "form_ids": m.form_ids}
        out.append(ch)
    return out
