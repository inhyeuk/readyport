"""Remote Config 스위치 변경 (REST). 바꿀 수 있는 키는 허용 목록뿐.

- `kill_autofill_{FORM_ID}`: 켜기(true, 자동 입력 끔)는 자동으로 해도 안전하다.
  끄기(false, 자동 입력 다시 켬)는 위험한 방향이라 approved_by_human=True 가 있어야 한다.
- `stale_banner`: 하트비트 감시(watchdog)만 쓰는 별도 함수. 켜기는 자동, 끄기는 '다시 살아남' 확인 또는 사람 승인.
- 그 밖의 키(pack_version_*, min_app_version 등)는 절대 건드리지 않는다. 바꾼 뒤 템플릿 전체를
  비교해 허용 키 말고 다른 곳이 바뀌었으면 PUT 하지 않는다(이중 확인).

흐름: GET 템플릿(ETag) → 허용 키만 수정 → PUT (If-Match: ETag). 이미 원하는 값이면 PUT 하지 않는다.
API: https://firebaseremoteconfig.googleapis.com/v1/projects/{id}/remoteConfig [재확인]
"""
from __future__ import annotations

import copy
import json
import re
from dataclasses import dataclass, field
from typing import Optional

from ..gcp import GcpApiError

RC_URL = "https://firebaseremoteconfig.googleapis.com/v1/projects/{project}/remoteConfig"
KILL_KEY_RE = re.compile(r"^kill_autofill_[A-Z]{2}_[A-Z0-9_]{2,40}$")
STALE_KEY = "stale_banner"


class NotAllowed(Exception):
    """허용 목록 밖의 키이거나 사람 승인이 필요한 방향."""


def kill_key(form_id: str) -> str:
    key = f"kill_autofill_{form_id}"
    if not KILL_KEY_RE.match(key):
        raise NotAllowed(f"양식 id 형식이 아님: {form_id!r}")
    return key


def assert_allowed_key(key: str, allow_stale: bool = False) -> None:
    if KILL_KEY_RE.match(key):
        return
    if allow_stale and key == STALE_KEY:
        return
    raise NotAllowed(f"이 키는 바꿀 수 없다: {key!r}")


@dataclass
class SwitchResult:
    key: str
    desired: bool
    changed: bool
    dry_run: bool
    before: Optional[str] = None
    notes: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return self.__dict__.copy()


def _find_param(template: dict, key: str) -> Optional[dict]:
    params = template.get("parameters") or {}
    if key in params:
        return params[key]
    for group in (template.get("parameterGroups") or {}).values():
        gp = group.get("parameters") or {}
        if key in gp:
            return gp[key]
    return None


def _strip_keys(template: dict, keys: set[str]) -> dict:
    """비교용: 허용 키를 뺀 템플릿 사본."""
    t = copy.deepcopy(template)
    for k in keys:
        (t.get("parameters") or {}).pop(k, None)
        for group in (t.get("parameterGroups") or {}).values():
            (group.get("parameters") or {}).pop(k, None)
    return t


def apply_bool(template: dict, key: str, value: bool, description: str = "",
               set_conditionals: bool = False) -> tuple[dict, Optional[str], list[str]]:
    """템플릿 사본에 key=value 를 적용. (새 템플릿, 이전 기본값, 메모)."""
    assert_allowed_key(key, allow_stale=True)
    t = copy.deepcopy(template)
    notes: list[str] = []
    p = _find_param(t, key)
    before = None
    sval = "true" if value else "false"
    if p is None:
        t.setdefault("parameters", {})[key] = {"defaultValue": {"value": sval}, "valueType": "BOOLEAN",
                                               "description": description or "ARIA 가 추가"}
        notes.append("키가 없어 새로 만듦")
    else:
        before = (p.get("defaultValue") or {}).get("value")
        p["defaultValue"] = {"value": sval}
        conds = p.get("conditionalValues") or {}
        if conds:
            if set_conditionals:
                for name in conds:
                    conds[name] = {"value": sval}
                notes.append(f"조건 값 {len(conds)}개도 {sval}")
            else:
                notes.append(f"조건 값 {len(conds)}개는 그대로 둠: {', '.join(conds)}")
    # 이중 확인: 허용한 키 말고 바뀐 곳이 없어야 한다
    if _strip_keys(t, {key}) != _strip_keys(template, {key}):
        raise NotAllowed("허용 키 밖이 바뀌었다 — 중단")
    return t, before, notes


class RemoteConfigClient:
    def __init__(self, project_id: str, token_provider, fetcher, timeout: float = 20.0):
        if not project_id:
            raise ValueError("FIREBASE_PROJECT_ID 가 필요하다")
        self.url = RC_URL.format(project=project_id)
        self.tokens = token_provider
        self.fetch = fetcher
        self.timeout = timeout

    def get(self) -> tuple[dict, str]:
        resp = self.fetch("GET", self.url, headers={"Authorization": f"Bearer {self.tokens.get_token()}",
                                                    "Accept-Encoding": "identity"}, timeout=self.timeout)
        if resp.status != 200:
            raise GcpApiError(resp.status, resp.text)
        etag = resp.header("etag")
        if not etag:
            raise GcpApiError(resp.status, "ETag 없음 — 덮어쓰기 위험이 있어 중단")
        return json.loads(resp.text), etag

    def put(self, template: dict, etag: str) -> str:
        # [재확인] 받은 템플릿의 version 칸을 그대로 보내도 서버가 무시한다고 알려져 있다.
        body = json.dumps(template, ensure_ascii=False).encode("utf-8")
        resp = self.fetch("PUT", self.url, headers={
            "Authorization": f"Bearer {self.tokens.get_token()}",
            "Content-Type": "application/json; UTF-8",
            "If-Match": etag,
        }, data=body, timeout=self.timeout)
        if resp.status != 200:
            raise GcpApiError(resp.status, resp.text)
        return resp.header("etag") or ""


def _set(client: RemoteConfigClient, key: str, value: bool, dry_run: bool, description: str,
         set_conditionals: bool, allow_stale: bool) -> SwitchResult:
    assert_allowed_key(key, allow_stale=allow_stale)
    template, etag = client.get()
    p = _find_param(template, key)
    current = (p.get("defaultValue") or {}).get("value") if p else None
    desired = "true" if value else "false"
    conds_ok = True
    if p and set_conditionals and p.get("conditionalValues"):
        conds_ok = all((c or {}).get("value") == desired for c in p["conditionalValues"].values())
    if current == desired and conds_ok:
        return SwitchResult(key, value, False, dry_run, current, ["이미 원하는 값"])
    new_t, before, notes = apply_bool(template, key, value, description, set_conditionals)
    if dry_run:
        return SwitchResult(key, value, True, True, before, notes + ["시험 실행 — PUT 안 함"])
    client.put(new_t, etag)
    return SwitchResult(key, value, True, False, before, notes)


def kill_autofill_on(client: RemoteConfigClient, form_id: str, dry_run: bool = False) -> SwitchResult:
    """자동 입력 끄기(안전한 방향). 조건 값까지 모두 true 로."""
    return _set(client, kill_key(form_id), True, dry_run,
                "true면 자동 입력 끄고 수동 모드", set_conditionals=True, allow_stale=False)


def kill_autofill_off(client: RemoteConfigClient, form_id: str, *, approved_by_human: bool = False,
                      dry_run: bool = False) -> SwitchResult:
    """자동 입력 다시 켜기(위험한 방향). 사람이 승인했을 때만. 기본값만 false 로(조건 값은 그대로)."""
    if approved_by_human is not True:
        raise NotAllowed("자동 입력 다시 켜기는 사람 승인(approved_by_human=True)이 있어야 한다")
    return _set(client, kill_key(form_id), False, dry_run, "", set_conditionals=False, allow_stale=False)


def set_stale_banner(client: RemoteConfigClient, on: bool, *, heartbeat_recovered: bool = False,
                     approved_by_human: bool = False, dry_run: bool = False) -> SwitchResult:
    """watchdog 전용. 켜기는 자동. 끄기는 하트비트가 다시 살아났거나(check_stale=False) 사람이 승인했을 때만."""
    if not on and not (heartbeat_recovered or approved_by_human):
        raise NotAllowed("stale_banner 끄기는 하트비트 회복 확인 또는 사람 승인이 필요하다")
    return _set(client, STALE_KEY, on, dry_run, "", set_conditionals=on, allow_stale=True)
