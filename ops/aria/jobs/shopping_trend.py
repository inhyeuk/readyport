"""쇼핑 리스트 검색 추이 (네이버 데이터랩 검색어 트렌드, 상대값).

키(NAVER_CLIENT_ID/SECRET)가 없으면 {"status": "not_configured"} 를 돌려주고 끝낸다. 값을 지어내지 않는다.
API: POST https://openapi.naver.com/v1/datalab/search [재확인: 주소·요청 형식·한 번에 5묶음 제한]
결과의 ratio 는 요청 안에서의 상대값(최댓값=100)이라 요청이 다르면 서로 비교할 수 없다.
"""
from __future__ import annotations

import json
from typing import Optional

from ..net import NetworkError

DATALAB_URL = "https://openapi.naver.com/v1/datalab/search"  # [재확인]
MAX_GROUPS = 5


def fetch_relative_trend(groups: dict[str, list[str]], start_date: str, end_date: str, *,
                         client_id: str = "", client_secret: str = "", fetcher=None,
                         time_unit: str = "month", timeout: float = 20.0) -> dict:
    """groups: {항목 id: [검색어, ...]}. 5묶음씩 나눠 요청한다."""
    if not client_id or not client_secret:
        return {"status": "not_configured", "reason": "NAVER_CLIENT_ID/SECRET 없음"}
    if fetcher is None:
        raise ValueError("fetcher 가 필요하다")
    items: dict[str, Optional[float]] = {}
    series: dict[str, list] = {}
    names = list(groups)
    for i in range(0, len(names), MAX_GROUPS):
        chunk = names[i:i + MAX_GROUPS]
        body = {"startDate": start_date, "endDate": end_date, "timeUnit": time_unit,
                "keywordGroups": [{"groupName": n, "keywords": groups[n][:20]} for n in chunk]}
        try:
            resp = fetcher("POST", DATALAB_URL, headers={
                "X-Naver-Client-Id": client_id, "X-Naver-Client-Secret": client_secret,
                "Content-Type": "application/json"}, data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                timeout=timeout)
        except NetworkError as e:
            return {"status": "error", "reason": str(e)}
        if resp.status != 200:
            return {"status": "error", "reason": f"HTTP {resp.status}"}
        data = json.loads(resp.text)
        for r in data.get("results", []):
            pts = r.get("data", [])
            series[r.get("title")] = pts
            items[r.get("title")] = pts[-1]["ratio"] if pts else None
    return {"status": "ok", "source": "naver_datalab", "period": f"{start_date}~{end_date}",
            "time_unit": time_unit, "latest_ratio": items, "series": series,
            "note": "요청 묶음 안에서의 상대값(최댓값 100)"}
