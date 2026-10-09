"""관광지 '공식 안내가 바뀌었어요 — 확인 중' 표시 (안전한 방향 예외, ARIA 자동 쓰기 허용 목록).

ARIA 가 쓸 수 있는 Firestore 문서는 `attraction_flags/{CC}` 하나뿐이고, 모양도 정해져 있다:
    {ids: [관광지 id …], kind: "check_in_progress", at: <timestamp>}
- 공식 페이지에 휴관·공사·폐쇄 같은 말이 **새로** 나타나면 그 관광지 id 를 넣는다(사람 승인 전에도 — kill-switch 와 같은 원칙).
- 그 말이 다시 사라지면(감지 기준선 이하) 뺀다. 목록이 같으면 쓰지 않는다.
- 앱은 이 문서를 읽기만 한다(규칙: get 만, 쓰기 불가). 서비스 계정만 쓴다.
"""
from __future__ import annotations

import datetime as _dt
import re
from dataclasses import dataclass, field

COLLECTION = "attraction_flags"
KIND = "check_in_progress"
COUNTRIES = ("TH", "JP", "VN", "PH", "TW", "SG", "MY", "ID", "CN")
ID_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
MAX_IDS = 200


class NotAllowed(Exception):
    pass


def flag_doc_path(cc: str) -> str:
    if cc not in COUNTRIES:
        raise NotAllowed(f"모르는 나라: {cc!r}")
    return f"{COLLECTION}/{cc}"


def build_doc(ids, at: _dt.datetime) -> dict:
    ids = sorted(set(ids))
    if len(ids) > MAX_IDS or any(not isinstance(i, str) or not ID_RE.match(i) or len(i) > 80 for i in ids):
        raise NotAllowed("관광지 id 형식이 아님")
    if at.tzinfo is None:
        raise NotAllowed("시간대 없는 시각")
    return {"ids": ids, "kind": KIND, "at": at}


@dataclass
class FlagResult:
    cc: str
    ids: list[str]
    changed: bool
    dry_run: bool
    notes: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return dict(self.__dict__)


def sync_flags(firestore, cc: str, ids, now: _dt.datetime, *, dry_run: bool = False) -> FlagResult:
    """문서를 ids 로 맞춘다. 지금과 같으면 쓰지 않는다. firestore 가 없으면 아무것도 안 한다."""
    path = flag_doc_path(cc)
    doc = build_doc(ids, now)
    if firestore is None:
        return FlagResult(cc, doc["ids"], False, dry_run, ["Firestore 연결 없음"])
    current = firestore.get_document(path) or {}
    if not current and not doc["ids"]:
        return FlagResult(cc, [], False, dry_run, ["문서 없음·표시할 곳 없음"])
    if sorted(current.get("ids") or []) == doc["ids"] and current.get("kind") == KIND:
        return FlagResult(cc, doc["ids"], False, dry_run, ["이미 같음"])
    if dry_run:
        return FlagResult(cc, doc["ids"], True, True, ["시험 실행 — 쓰지 않음"])
    firestore.replace_document(path, doc)
    return FlagResult(cc, doc["ids"], True, False)
