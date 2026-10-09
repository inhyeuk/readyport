"""AI 계획 신고 확인 (1시간마다, run_hourly 가 부른다 — Play 'AI 생성 콘텐츠' 정책: 앱 안 신고).

앱 → Firestore plan_flags/{요청 id} {uid, reason, note?, at} (규칙: 내 결과에만 한 번, 운영자만 읽음)
→ 이 작업: 지난번 확인 뒤(at > 마지막 시각) 새로 생긴 신고 수와 요청 id 만 센다.
- 마지막 시각은 FingerprintStore 값(LAST_KEY)에 ISO 문자열로 둔다. 시험(dry_run)이면 옮기지 않는다.
- 요약·알림에는 **id 와 개수만** — 신고 이유·메모·계획 내용은 담지 않는다(운영자는 콘솔/관리 도구에서 직접 본다).
- 신고 문서는 요청·결과와 함께 plan_cleanup 이 지운다(30일 정리, 이용자가 요청을 지운 뒤의 정리).
"""
from __future__ import annotations

import datetime as _dt
from typing import Optional

from ..gcp import doc_id

FLAGS = "plan_flags"
LAST_KEY = "plan_flags:last_at"
PAGE = 200
MAX_IDS = 50
_EPOCH = _dt.datetime(2000, 1, 1, tzinfo=_dt.timezone.utc)


def _ts(dt: _dt.datetime) -> dict:
    return {"timestampValue": dt.astimezone(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")}


def newer_than_query(after: _dt.datetime, limit: int = PAGE) -> dict:
    """at > after (at 오름차순). 내용 칸은 고르지 않는다(at 만)."""
    return {
        "from": [{"collectionId": FLAGS}],
        "select": {"fields": [{"fieldPath": "at"}]},
        "where": {"fieldFilter": {"field": {"fieldPath": "at"}, "op": "GREATER_THAN", "value": _ts(after)}},
        "orderBy": [{"field": {"fieldPath": "at"}, "direction": "ASCENDING"}],
        "limit": limit,
    }


def _parse(v) -> Optional[_dt.datetime]:
    if not isinstance(v, str):
        return None
    try:
        t = _dt.datetime.fromisoformat(v)
    except ValueError:
        return None
    return t if t.tzinfo else t.replace(tzinfo=_dt.timezone.utc)


def check(firestore, store, *, dry_run: bool = True, max_rounds: int = 20) -> dict:
    """{"new": n, "ids": [...]} — 지난 확인 뒤 새 신고. firestore 가 없으면 status=not_configured."""
    if firestore is None:
        return {"status": "not_configured", "new": 0, "ids": []}
    last = _parse(store.get_value(LAST_KEY)) or _EPOCH
    after, newest = last, None
    ids: list[str] = []
    seen: set[str] = set()
    for _ in range(max_rounds):
        rows = [r for r in firestore.run_query(newer_than_query(after)) if r.get("_name") not in seen]
        if not rows:
            break
        for r in rows:
            seen.add(r["_name"])
            ids.append(doc_id(r["_name"]))
            t = r.get("at")
            if isinstance(t, _dt.datetime) and (newest is None or t > newest):
                newest = t
        t = rows[-1].get("at")
        if len(rows) < PAGE or not isinstance(t, _dt.datetime):
            break
        after = t
    if not dry_run and newest is not None and newest > last:
        store.set_value(LAST_KEY, newest.astimezone(_dt.timezone.utc).isoformat())
    out = {"new": len(ids), "ids": ids[:MAX_IDS]}
    if len(ids) > MAX_IDS:
        out["more"] = len(ids) - MAX_IDS
    return out


def notice(flags: dict) -> Optional[str]:
    """알림 한 줄 (id 만). 새 신고가 없으면 None."""
    n = int(flags.get("new") or 0)
    if n <= 0:
        return None
    ids = ", ".join(flags.get("ids", [])[:10])
    more = n - min(n, 10)
    return f"[레디포트] AI 계획 신고 {n}건: {ids}" + (f" 외 {more}건" if more > 0 else "")
