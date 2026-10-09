"""레디포트 이용자 관광지 평점 주간 집계 (사장님 결정 2026-10-09 ①: 자체 평점, ARIA 주간 집계).

읽는 곳: attraction_ratings/{CC}_{관광지 id}/votes/{uid}  {stars 1–5, at, visited: true}  (앱이 씀, 한 사람 한 표)
쓰는 곳: attraction_rating_stats/{CC}  {<관광지 id>: {avg, n}, …, _meta: {updated_at, min_n}}  (서비스 계정만 씀, 누구나 읽음)

- 평가가 MIN_N(기본 5, RATINGS_MIN_N) 개보다 적은 관광지는 통계 문서에 **넣지 않는다**(숨김).
- uid 는 세기만 하고 어디에도 남기지 않는다. 결과에는 숫자(평균·개수)만.
- 관광지 목록은 저장소의 attractions.json(원본·작업본)에서 — 목록에 없는 문서는 보지 않는다.
- 같은 결과면 쓰지 않는다. dry_run 이면 읽기만.
"""
from __future__ import annotations

import datetime as _dt
import json
import pathlib
import re
from typing import Optional

STATS_COLLECTION = "attraction_rating_stats"
VOTES_PARENT = "attraction_ratings"
COUNTRIES = ("TH", "JP", "VN", "PH", "TW", "SG", "MY", "ID", "CN")
ID_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
PAGE = 500


def rating_targets(repo_root: pathlib.Path) -> dict[str, list[str]]:
    out: dict[str, set[str]] = {}
    for base in ("src", "drafts"):
        for p in sorted((pathlib.Path(repo_root) / "packs" / base).glob("*/attractions.json")):
            cc = p.parent.name
            if cc not in COUNTRIES:
                continue
            try:
                doc = json.loads(p.read_text(encoding="utf-8"))
            except (OSError, ValueError):
                continue
            ids = {a.get("id") for a in doc.get("attractions", []) if ID_RE.match(str(a.get("id", "")))}
            out.setdefault(cc, set()).update(ids)
    return {cc: sorted(ids) for cc, ids in sorted(out.items())}


def votes_query(after_name: Optional[str] = None, limit: int = PAGE) -> dict:
    q = {
        "from": [{"collectionId": "votes"}],
        "select": {"fields": [{"fieldPath": "stars"}, {"fieldPath": "visited"}]},
        "orderBy": [{"field": {"fieldPath": "__name__"}, "direction": "ASCENDING"}],
        "limit": limit,
    }
    if after_name:
        q["startAt"] = {"values": [{"referenceValue": after_name}], "before": False}
    return q


def fetch_stars(firestore, cc: str, aid: str, max_pages: int = 200) -> list[int]:
    """그 관광지의 유효한 별점 목록(1~5 정수, visited=true 만)."""
    stars: list[int] = []
    after = None
    for _ in range(max_pages):
        rows = firestore.run_query(votes_query(after), parent=f"{VOTES_PARENT}/{cc}_{aid}")
        for r in rows:
            s = r.get("stars")
            if isinstance(s, int) and not isinstance(s, bool) and 1 <= s <= 5 and r.get("visited") is True:
                stars.append(s)
        if len(rows) < PAGE:
            break
        after = rows[-1].get("_name")
        if not after:
            break
    return stars


def aggregate(stars_by_id: dict[str, list[int]], min_n: int) -> dict[str, dict]:
    out = {}
    for aid, stars in sorted(stars_by_id.items()):
        n = len(stars)
        if n >= max(1, min_n):
            out[aid] = {"avg": round(sum(stars) / n, 1), "n": n}
    return out


def _same(current: Optional[dict], stats: dict, min_n: int) -> bool:
    if current is None:
        return False
    cur = {k: v for k, v in current.items() if k != "_meta"}
    return cur == stats and (current.get("_meta") or {}).get("min_n") == min_n


def run(cfg, firestore, *, dry_run: bool = True, now: Optional[_dt.datetime] = None,
        countries: Optional[list[str]] = None) -> dict:
    if firestore is None:
        return {"status": "not_configured", "countries": {}}
    now = now or _dt.datetime.now(_dt.timezone.utc)
    min_n = int(getattr(cfg, "ratings_min_n", 5))
    report = {}
    for cc, ids in rating_targets(cfg.repo_root).items():
        if countries and cc not in countries:
            continue
        stars_by_id = {aid: fetch_stars(firestore, cc, aid) for aid in ids}
        stats = aggregate(stars_by_id, min_n)
        hidden = sum(1 for aid, s in stars_by_id.items() if s and aid not in stats)
        path = f"{STATS_COLLECTION}/{cc}"
        current = firestore.get_document(path)
        changed = not _same(current, stats, min_n)
        if changed and not dry_run:
            firestore.replace_document(path, {**stats, "_meta": {"updated_at": now, "min_n": min_n}})
        report[cc] = {"shown": len(stats), "hidden_below_min": hidden, "changed": changed, "written": changed and not dry_run}
    return {"status": "ok", "countries": report, "dry_run": dry_run}
