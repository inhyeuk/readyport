"""packs/src/**/*.json 안의 모든 https 주소 점검 (제휴·essentials·return_links 포함).

HEAD 를 먼저 보내고, HEAD 를 안 받는 서버(405/501 등)면 GET 한 번. 입력·제출은 없다.
결과: ok / broken(404·410·주소 없음) / manual_check_needed(봇 차단·403·429·503) / error(그 밖)
요청 사이 간격을 둔다(sleep 주입 가능).
"""
from __future__ import annotations

import pathlib
import time
from collections import defaultdict
from typing import Callable, Optional

from ..net import NetworkError, looks_like_bot_challenge
from ..packs_util import iter_pack_files, load_json, walk

BROKEN_STATUS = {404, 410}


def collect_urls(repo_root: pathlib.Path) -> dict[str, list[str]]:
    """{주소: [나온 곳 "파일#경로", ...]}. 와일드카드(*)나 미확정 표시가 든 주소는 뺀다."""
    found: dict[str, list[str]] = defaultdict(list)
    root = pathlib.Path(repo_root)
    for f in iter_pack_files(root):
        rel = f.relative_to(root).as_posix()
        for path, v in walk(load_json(f)):
            if isinstance(v, str) and v.startswith("https://") and "*" not in v and " " not in v and "[" not in v:
                found[v].append(f"{rel}#{path}")
    return dict(found)


def check_url(url: str, fetcher, timeout: float = 15.0, user_agent: str = "") -> dict:
    headers = {"User-Agent": user_agent} if user_agent else {}
    try:
        resp = fetcher("HEAD", url, headers=headers, timeout=timeout)
        if resp.status in (400, 403, 405, 501) or resp.status >= 500:
            # HEAD 를 싫어하는 서버가 많다. GET 한 번만.
            resp = fetcher("GET", url, headers=headers, timeout=timeout)
    except NetworkError as e:
        return {"url": url, "result": "broken" if "Name or service" in str(e) or "getaddrinfo" in str(e) else "error",
                "detail": str(e)[:200]}
    if resp.status in BROKEN_STATUS:
        return {"url": url, "result": "broken", "status": resp.status}
    if looks_like_bot_challenge(resp) and resp.status >= 400:
        return {"url": url, "result": "manual_check_needed", "status": resp.status}
    if 200 <= resp.status < 400:
        return {"url": url, "result": "ok", "status": resp.status}
    return {"url": url, "result": "error", "status": resp.status}


def run(repo_root: pathlib.Path, fetcher, interval_sec: float = 2.0, timeout: float = 15.0,
        user_agent: str = "", sleep: Callable[[float], None] = time.sleep,
        only: Optional[set[str]] = None) -> dict:
    urls = collect_urls(repo_root)
    results = []
    for i, (url, where) in enumerate(sorted(urls.items())):
        if only is not None and url not in only:
            continue
        if i and interval_sec:
            sleep(interval_sec)
        r = check_url(url, fetcher, timeout, user_agent)
        r["where"] = where
        results.append(r)
    summary = defaultdict(int)
    for r in results:
        summary[r["result"]] += 1
    return {"checked": len(results), "summary": dict(summary),
            "problems": [r for r in results if r["result"] != "ok"], "results": results}
