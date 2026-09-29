"""Google API(Firestore·Remote Config) REST 접근 도우미.

- 토큰은 TokenProvider 인터페이스로 받는다. 실제 운영은 서비스 계정 JSON(google-auth, 선택 설치).
- 테스트는 가짜 토큰과 가짜 fetcher 를 넣는다.
서비스 계정 키 파일은 저장소 밖에 둔다(GOOGLE_APPLICATION_CREDENTIALS).
"""
from __future__ import annotations

import datetime as _dt
import json
import urllib.parse
from typing import Any, Optional, Protocol

from .net import NetworkError, Response

SCOPE_DATASTORE = "https://www.googleapis.com/auth/datastore"
SCOPE_REMOTE_CONFIG = "https://www.googleapis.com/auth/firebase.remoteconfig"


class TokenProvider(Protocol):
    def get_token(self) -> str: ...


class StaticTokenProvider:
    """테스트용."""

    def __init__(self, token: str = "test-token"):
        self._t = token

    def get_token(self) -> str:
        return self._t


class ServiceAccountTokenProvider:
    """google-auth 로 서비스 계정 토큰을 만든다. google-auth 가 없으면 만들 때 알려 준다."""

    def __init__(self, credentials_path: str, scopes: list[str]):
        try:
            from google.oauth2 import service_account  # type: ignore
            from google.auth.transport.requests import Request  # type: ignore  # noqa: F401
        except ImportError as e:  # pragma: no cover - 환경에 따라 다름
            raise RuntimeError("google-auth 가 필요하다: pip install google-auth requests") from e
        if not credentials_path:
            raise RuntimeError("GOOGLE_APPLICATION_CREDENTIALS 가 비어 있다")
        self._creds = service_account.Credentials.from_service_account_file(credentials_path, scopes=scopes)

    def get_token(self) -> str:  # pragma: no cover - 실제 네트워크
        from google.auth.transport.requests import Request  # type: ignore

        if not self._creds.valid:
            self._creds.refresh(Request())
        return self._creds.token


class GcpApiError(Exception):
    def __init__(self, status: int, message: str):
        super().__init__(f"HTTP {status}: {message[:300]}")
        self.status = status


def _json_or_error(resp: Response) -> Any:
    if resp.status < 200 or resp.status >= 300:
        raise GcpApiError(resp.status, resp.text)
    return json.loads(resp.text) if resp.body else None


# ---------- Firestore 값 변환 ----------

def to_fs_value(v: Any) -> dict:
    if v is None:
        return {"nullValue": None}
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, int):
        return {"integerValue": str(v)}
    if isinstance(v, float):
        return {"doubleValue": v}
    if isinstance(v, _dt.datetime):
        if v.tzinfo is None:
            raise ValueError("시간대 없는 datetime 은 쓰지 않는다")
        return {"timestampValue": v.astimezone(_dt.timezone.utc).isoformat().replace("+00:00", "Z")}
    if isinstance(v, dict):
        return {"mapValue": {"fields": {k: to_fs_value(x) for k, x in v.items()}}}
    if isinstance(v, (list, tuple)):
        return {"arrayValue": {"values": [to_fs_value(x) for x in v]}}
    return {"stringValue": str(v)}


def parse_timestamp(s: str) -> _dt.datetime:
    s = s.strip()
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    # 소수점 아래 9자리(나노초)는 파이썬이 못 읽으니 6자리로 자른다
    if "." in s:
        head, rest = s.split(".", 1)
        frac = ""
        i = 0
        while i < len(rest) and rest[i].isdigit():
            frac += rest[i]
            i += 1
        s = f"{head}.{frac[:6].ljust(6, '0')}{rest[i:]}"
    dt = _dt.datetime.fromisoformat(s)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=_dt.timezone.utc)
    return dt


def from_fs_value(v: dict) -> Any:
    if "nullValue" in v:
        return None
    if "booleanValue" in v:
        return bool(v["booleanValue"])
    if "integerValue" in v:
        return int(v["integerValue"])
    if "doubleValue" in v:
        return float(v["doubleValue"])
    if "timestampValue" in v:
        return parse_timestamp(v["timestampValue"])
    if "stringValue" in v:
        return v["stringValue"]
    if "mapValue" in v:
        return {k: from_fs_value(x) for k, x in v["mapValue"].get("fields", {}).items()}
    if "arrayValue" in v:
        return [from_fs_value(x) for x in v["arrayValue"].get("values", [])]
    return None


class FirestoreRest:
    """Firestore REST (v1) 의 필요한 부분만."""

    def __init__(self, project_id: str, token_provider: TokenProvider, fetcher, timeout: float = 20.0):
        if not project_id:
            raise ValueError("FIREBASE_PROJECT_ID 가 필요하다")
        self.base = f"https://firestore.googleapis.com/v1/projects/{project_id}/databases/(default)/documents"
        self.tokens = token_provider
        self.fetch = fetcher
        self.timeout = timeout

    def _headers(self) -> dict:
        return {"Authorization": f"Bearer {self.tokens.get_token()}", "Content-Type": "application/json"}

    def run_query(self, structured_query: dict) -> list[dict]:
        """문서 목록(필드는 파이썬 값으로 바꿔서)."""
        body = json.dumps({"structuredQuery": structured_query}).encode("utf-8")
        resp = self.fetch("POST", self.base + ":runQuery", headers=self._headers(), data=body, timeout=self.timeout)
        rows = _json_or_error(resp) or []
        out = []
        for r in rows:
            doc = r.get("document")
            if not doc:
                continue
            fields = {k: from_fs_value(v) for k, v in doc.get("fields", {}).items()}
            fields["_name"] = doc.get("name", "")
            out.append(fields)
        return out

    def set_document(self, path: str, data: dict) -> dict:
        """문서 전체 쓰기(PATCH, 없으면 만든다). 필드 목록을 updateMask 로 준다."""
        mask = "&".join("updateMask.fieldPaths=" + urllib.parse.quote(k) for k in data)
        url = f"{self.base}/{path}" + (f"?{mask}" if mask else "")
        body = json.dumps({"fields": {k: to_fs_value(v) for k, v in data.items()}}).encode("utf-8")
        resp = self.fetch("PATCH", url, headers=self._headers(), data=body, timeout=self.timeout)
        return _json_or_error(resp)

    def delete_document(self, name_or_path: str) -> None:
        """문서 삭제. runQuery 결과의 _name(전체 이름)이나 컬렉션/문서 경로를 받는다."""
        if name_or_path.startswith("projects/"):
            url = "https://firestore.googleapis.com/v1/" + name_or_path
        else:
            url = f"{self.base}/{name_or_path}"
        resp = self.fetch("DELETE", url, headers=self._headers(), timeout=self.timeout)
        if resp.status not in (200, 404):
            _json_or_error(resp)

    def get_document(self, path: str) -> Optional[dict]:
        resp = self.fetch("GET", f"{self.base}/{path}", headers=self._headers(), timeout=self.timeout)
        if resp.status == 404:
            return None
        doc = _json_or_error(resp)
        return {k: from_fs_value(v) for k, v in doc.get("fields", {}).items()}


__all__ = ["TokenProvider", "StaticTokenProvider", "ServiceAccountTokenProvider", "FirestoreRest",
           "GcpApiError", "NetworkError", "to_fs_value", "from_fs_value", "parse_timestamp",
           "SCOPE_DATASTORE", "SCOPE_REMOTE_CONFIG"]
