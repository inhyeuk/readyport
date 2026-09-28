"""인기 순위 계산 → rankings/latest.json (ARCHITECTURE 10.3).

입력은 이미 받아 둔 데이터(이 모듈은 네트워크를 쓰지 않는다):
  air      : {city_id: 이번 달 여객 수}          — 필수. 없으면 만들지 않는다(순위를 지어내지 않음)
  air_prev : {city_id: 지난달 여객 수}          — 선택 (passenger_up 이유)
  search   : {city_id: 이번 주 검색 상대값}      — 선택
  search_prev : {city_id: 지난주 검색 상대값}    — 선택 (search_rising 이유)
  favorites: {city_id: 찜 수}                   — 선택 (Firestore favorite_counts)
  meta     : {city_id: {"visa_free_kr": bool|None, "flight_hours": float|None}}
점수 = 0.5×항공(최댓값 대비) + 0.3×검색(최댓값 대비) + 0.2×찜(최댓값 대비).
항공 자료에 없는 도시는 순위에 넣지 않는다.
배포(Hosting)는 ARIA 가 하지 않는다 — 파일을 PR 로 올리면 GitHub Actions 가 배포한다.
"""
from __future__ import annotations

import datetime as _dt
import json
import pathlib
from typing import Optional

WEIGHTS = {"air": 0.5, "search": 0.3, "favorites": 0.2}
RISE_RATIO = 1.05


class RankingRefused(Exception):
    """필수 자료가 없어 순위를 만들지 않음."""


def iso_week(d: _dt.date) -> str:
    y, w, _ = d.isocalendar()
    return f"{y}-W{w:02d}"


def _norm(values: dict[str, float]) -> dict[str, float]:
    if not values:
        return {}
    top = max(values.values())
    if top <= 0:
        return {k: 0.0 for k in values}
    return {k: max(0.0, float(v)) / top for k, v in values.items()}


def compute_ranking(air: Optional[dict[str, float]], *, air_month: str, search_week: Optional[str] = None,
                    air_prev: Optional[dict[str, float]] = None, search: Optional[dict[str, float]] = None,
                    search_prev: Optional[dict[str, float]] = None, favorites: Optional[dict[str, float]] = None,
                    meta: Optional[dict[str, dict]] = None, prev_ranking: Optional[dict] = None,
                    version: Optional[str] = None, today: Optional[_dt.date] = None) -> dict:
    if not air:
        raise RankingRefused("항공 자료가 없어 순위를 만들지 않는다")
    today = today or _dt.date.today()
    # 검색·찜은 항공 자료에 있는 도시끼리만 비교한다
    na = _norm(air)
    ns = _norm({k: v for k, v in (search or {}).items() if k in air})
    nf = _norm({k: v for k, v in (favorites or {}).items() if k in air})
    prev_ranks = {i["city_id"]: i["rank"] for i in (prev_ranking or {}).get("items", [])}
    meta = meta or {}
    scored = []
    for city in air:
        score = WEIGHTS["air"] * na.get(city, 0.0) + WEIGHTS["search"] * ns.get(city, 0.0) \
            + WEIGHTS["favorites"] * nf.get(city, 0.0)
        reasons = []
        if air_prev and air_prev.get(city) and air[city] > air_prev[city] * RISE_RATIO:
            reasons.append("passenger_up")
        if search and search_prev and search_prev.get(city) and search.get(city, 0) > search_prev[city] * RISE_RATIO:
            reasons.append("search_rising")
        m = meta.get(city, {})
        scored.append({"city_id": city, "score": round(score, 4), "reasons": reasons,
                       "visa_free_kr": m.get("visa_free_kr"), "flight_hours": m.get("flight_hours")})
    scored.sort(key=lambda x: (-x["score"], x["city_id"]))
    items = []
    for i, it in enumerate(scored, 1):
        items.append({"city_id": it["city_id"], "rank": i, "prev_rank": prev_ranks.get(it["city_id"]),
                      "score": it["score"], "reasons": it["reasons"], "visa_free_kr": it["visa_free_kr"],
                      "flight_hours": it["flight_hours"]})
    return {
        "version": version or iso_week(today),
        "basis": {"air_month": air_month, "search_week": search_week},
        "weights": dict(WEIGHTS),
        "items": items,
    }


def write_ranking(ranking: dict, out_path: pathlib.Path) -> str:
    out_path = pathlib.Path(out_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(ranking, ensure_ascii=False, indent=2), encoding="utf-8")
    return str(out_path)
