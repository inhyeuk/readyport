"""robots.txt 지키기 (관광지 공식 페이지 주간 확인용).

- 사이트마다 robots.txt 를 한 번만 받는다(같은 실행 안에서 캐시).
- 404·410 등 '없음'이면 모두 허용, 401·403 이면 모두 금지(표준 robotparser 관례와 같음).
- 봇 차단 화면(Cloudflare 등)이나 네트워크 오류면 판단하지 않고 None — 호출한 쪽이 manual_check_needed / error 로 끝낸다.
- 받는 것은 fetcher(주입)로만. 테스트는 가짜 fetcher.
"""
from __future__ import annotations

import urllib.parse
import urllib.robotparser
from typing import Optional

from .net import NetworkError, looks_like_bot_challenge

ALLOW_ALL = "allow_all"
DISALLOW_ALL = "disallow_all"


class RobotsCache:
    def __init__(self, fetcher, user_agent: str, timeout: float = 20.0):
        self.fetch = fetcher
        self.user_agent = user_agent
        self.timeout = timeout
        self._cache: dict[str, object] = {}
        self.fetches = 0

    def _origin(self, url: str) -> str:
        p = urllib.parse.urlsplit(url)
        return f"{p.scheme}://{p.netloc}"

    def _load(self, origin: str):
        if origin in self._cache:
            return self._cache[origin]
        self.fetches += 1
        headers = {"User-Agent": self.user_agent} if self.user_agent else {}
        try:
            resp = self.fetch("GET", origin + "/robots.txt", headers=headers, timeout=self.timeout)
        except NetworkError:
            self._cache[origin] = None
            return None
        if resp.status in (401, 403):
            entry: object = DISALLOW_ALL
        elif resp.status >= 400 and resp.status not in (429, 503):
            entry = ALLOW_ALL
        elif looks_like_bot_challenge(resp):
            entry = None          # 사람 확인 화면 — 판단하지 않는다(우회 금지)
        elif 200 <= resp.status < 300:
            rp = urllib.robotparser.RobotFileParser()
            rp.parse(resp.text.splitlines())
            entry = rp
        else:
            entry = None
        self._cache[origin] = entry
        return entry

    def allowed(self, url: str) -> Optional[bool]:
        """True=허용, False=금지, None=판단 못 함(봇 차단·네트워크 오류)."""
        entry = self._load(self._origin(url))
        if entry is None:
            return None
        if entry == ALLOW_ALL:
            return True
        if entry == DISALLOW_ALL:
            return False
        return entry.can_fetch(self.user_agent or "*", url)  # type: ignore[union-attr]
