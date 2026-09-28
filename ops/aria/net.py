"""작은 HTTP 도구 (표준 라이브러리 urllib 만 쓴다).

모든 모듈은 `fetcher` 를 주입받는다. 테스트는 가짜 fetcher 를 넣어 네트워크를 쓰지 않는다.
fetcher(method, url, headers=None, data=None, timeout=20.0) -> Response
"""
from __future__ import annotations

import gzip
import re
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Callable, Optional, Protocol


@dataclass
class Response:
    status: int
    body: bytes = b""
    headers: dict[str, str] = field(default_factory=dict)
    url: str = ""

    def header(self, name: str) -> str | None:
        low = name.lower()
        for k, v in self.headers.items():
            if k.lower() == low:
                return v
        return None

    @property
    def text(self) -> str:
        charset = "utf-8"
        ctype = self.header("content-type") or ""
        m = re.search(r"charset=([\w-]+)", ctype, re.I)
        if m:
            charset = m.group(1)
        try:
            return self.body.decode(charset, errors="replace")
        except LookupError:
            return self.body.decode("utf-8", errors="replace")


class Fetcher(Protocol):
    def __call__(self, method: str, url: str, headers: Optional[dict] = None,
                 data: Optional[bytes] = None, timeout: float = 20.0) -> Response: ...


class NetworkError(Exception):
    """연결 실패·시간 초과 같은 네트워크 오류 (다시 시도해도 되는 실패)."""


def decode_body(body: bytes, headers: dict) -> bytes:
    """Content-Encoding: gzip 이면 푼다 (urllib은 스스로 풀지 않는다)."""
    enc = next((v for k, v in headers.items() if k.lower() == "content-encoding"), "")
    if "gzip" in (enc or "").lower() and body:
        try:
            return gzip.decompress(body)
        except OSError:
            return body
    return body


def urllib_fetch(method: str, url: str, headers: Optional[dict] = None,
                 data: Optional[bytes] = None, timeout: float = 20.0) -> Response:
    """실제 네트워크 요청. HTTP 오류 코드도 Response 로 돌려준다(예외 아님)."""
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            h = dict(r.headers.items())
            return Response(r.status, decode_body(r.read(), h), h, r.geturl())
    except urllib.error.HTTPError as e:
        body = b""
        try:
            body = e.read()
        except Exception:  # noqa: BLE001 — 본문을 못 읽어도 상태 코드는 쓴다
            pass
        h = dict(e.headers.items()) if e.headers else {}
        return Response(e.code, decode_body(body, h), h, url)
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        raise NetworkError(f"{type(e).__name__}: {e}") from None


# 봇 차단(사람 확인) 화면 표시. 이런 화면이면 우회하지 않고 "수동 확인 필요"로 끝낸다.
CHALLENGE_STATUS = {403, 429, 503}
CHALLENGE_MARKERS = [
    "challenges.cloudflare.com",
    "cf-turnstile",
    "cf_chl_",
    "cf-challenge",
    "just a moment...",
    "attention required! | cloudflare",
    "g-recaptcha",
    "recaptcha/api",
    "hcaptcha",
    "captcha",
    "are you a robot",
    "verify you are human",
]


def looks_like_bot_challenge(resp: Response) -> bool:
    if resp.status in CHALLENGE_STATUS:
        return True
    low = resp.text[:200_000].lower()
    return any(m in low for m in CHALLENGE_MARKERS)


def redact_url(url: str) -> str:
    """로그·증거에 남길 주소에서 인증키 값을 지운다."""
    return re.sub(r"(?i)(servicekey|key|token|secret)=[^&]*", r"\1=***", url)


FetcherFn = Callable[..., Response]
