"""외교부 '국가·지역별 입국허가요건' (공공데이터포털) 전날 대비 변경 감지.

- 인증키: 환경변수 MOFA_SERVICE_KEY. 주소: MOFA_API_URL (기본값은 config.py, [재확인]).
- 응답 형식(JSON/XML, 항목 이름)은 서비스마다 조금씩 다르다. 그래서 흔한 형태를 모두 받아 주고,
  나라 코드 칸 이름도 후보 목록에서 찾는다 [재확인: 실제 응답 칸 이름].
- 날짜별 스냅샷을 디스크에 두고, 오늘 것과 그 전 가장 최근 것을 비교한다.
- 나라마다 Change 하나(지문 + 증거 파일).
"""
from __future__ import annotations

import datetime as _dt
import html
import json
import pathlib
import re
import urllib.parse
import xml.etree.ElementTree as ET
from typing import Any, Iterable, Optional

from ..evidence import save_evidence
from ..models import (STATUS_BASELINE, STATUS_CHANGED, STATUS_ERROR, STATUS_NOT_CONFIGURED,
                      STATUS_UNCHANGED, Change, UnitResult)
from ..net import NetworkError, Response, redact_url

DETECTOR = "mofa_entry_diff"

# [재확인] 요청 인자 이름. 공공데이터포털 서비스마다 returnType/_type/type 이 다르다.
EXTRA_PARAMS = {"returnType": "JSON"}
PAGE_SIZE = 300
MAX_PAGES = 20

# [재확인] 나라 코드·이름 칸 이름 후보
ISO_FIELDS = ["country_iso_alp2", "iso_alp2", "countryIsoAlp2", "country_iso", "iso2", "iso_code"]
NAME_FIELDS = ["country_nm", "countryName", "country_name", "country_eng_nm"]
# 비교에서 빼는 칸 (행 번호 같은 매번 바뀌는 값)
IGNORE_FIELDS = {"rnum", "rn", "row_num", "numOfRows", "pageNo"}

_TAG_RE = re.compile(r"<[^>]+>")
_WS_RE = re.compile(r"\s+")


class MofaError(Exception):
    pass


def normalize_value(v: Any) -> str:
    if v is None:
        return ""
    s = str(v)
    s = _TAG_RE.sub(" ", s)
    s = html.unescape(s)
    return _WS_RE.sub(" ", s).strip()


def _find_item_list(obj: Any) -> Optional[list]:
    """JSON 안에서 항목 목록을 찾는다. 흔한 경로를 먼저 본다."""
    if isinstance(obj, list):
        return obj
    if not isinstance(obj, dict):
        return None
    # 공공데이터포털 표준: response.header.resultCode, response.body.items.item
    resp = obj.get("response")
    if isinstance(resp, dict):
        header = resp.get("header") or {}
        code = str(header.get("resultCode", "00"))
        if code not in ("00", "0", "INFO-000"):
            raise MofaError(f"API 오류 resultCode={code} {header.get('resultMsg', '')}")
        body = resp.get("body") or {}
        items = body.get("items")
        if isinstance(items, dict):
            items = items.get("item")
        if isinstance(items, dict):
            items = [items]
        if items in (None, ""):
            return []
        return items
    for key in ("data", "items", "item"):
        v = obj.get(key)
        if isinstance(v, list):
            return v
        if isinstance(v, dict):
            inner = _find_item_list(v)
            if inner is not None:
                return inner
    return None


def _total_count(obj: Any) -> Optional[int]:
    try:
        if "response" in obj:
            return int(obj["response"]["body"].get("totalCount"))
        for k in ("totalCount", "matchCount"):
            if k in obj:
                return int(obj[k])
    except (KeyError, TypeError, ValueError, AttributeError):
        return None
    return None


def parse_payload(text: str) -> tuple[list[dict], Optional[int]]:
    """응답 글 → (항목 목록, 전체 개수). JSON 이 아니면 XML 로 읽어 본다."""
    text = text.strip()
    if text.startswith("{") or text.startswith("["):
        obj = json.loads(text)
        items = _find_item_list(obj)
        if items is None:
            raise MofaError("응답에서 항목 목록을 찾지 못했다 [재확인: 응답 형식]")
        return [i for i in items if isinstance(i, dict)], (_total_count(obj) if isinstance(obj, dict) else None)
    try:
        root = ET.fromstring(text)
    except ET.ParseError as e:
        raise MofaError(f"JSON·XML 모두 아님: {e}") from None
    code = root.findtext(".//resultCode")
    if code and code not in ("00", "0"):
        raise MofaError(f"API 오류 resultCode={code} {root.findtext('.//resultMsg') or ''}")
    items = [{c.tag: (c.text or "") for c in item} for item in root.iter("item")]
    total = root.findtext(".//totalCount")
    return items, (int(total) if total and total.isdigit() else None)


def fetch_items(api_url: str, service_key: str, fetcher, timeout: float = 20.0,
                user_agent: str = "") -> list[dict]:
    """모든 쪽을 받아 합친다. 인증키는 오류 글에 남기지 않는다."""
    if not service_key:
        raise MofaError("MOFA_SERVICE_KEY 없음")
    out: list[dict] = []
    for page in range(1, MAX_PAGES + 1):
        params = {"serviceKey": service_key, "pageNo": str(page), "numOfRows": str(PAGE_SIZE), **EXTRA_PARAMS}
        url = api_url + ("&" if "?" in api_url else "?") + urllib.parse.urlencode(params)
        headers = {"User-Agent": user_agent} if user_agent else {}
        resp: Response = fetcher("GET", url, headers=headers, timeout=timeout)
        if resp.status != 200:
            raise MofaError(f"HTTP {resp.status} ({redact_url(url)})")
        items, total = parse_payload(resp.text)
        out.extend(items)
        if not items or total is None or len(out) >= total:
            break
    return out


def country_key(item: dict) -> str:
    for f in ISO_FIELDS:
        v = normalize_value(item.get(f))
        if v:
            return v.upper()
    for f in NAME_FIELDS:
        v = normalize_value(item.get(f))
        if v:
            return v
    return ""


def normalize_items(items: Iterable[dict], countries: Optional[Iterable[str]] = None) -> dict[str, dict[str, str]]:
    """{나라키: {칸: 정규화한 값}}. countries 를 주면 그 나라(ISO2)만 남긴다.

    한 나라에 항목이 여러 개면 칸 이름 뒤에 #2, #3 을 붙여 모두 남긴다(정렬해서 순서 영향 없앰).
    """
    wanted = {c.upper() for c in countries} if countries else None
    grouped: dict[str, list[dict[str, str]]] = {}
    for item in items:
        key = country_key(item)
        if not key:
            continue
        if wanted is not None and len(key) == 2 and key not in wanted:
            continue
        norm = {k: normalize_value(v) for k, v in item.items() if k not in IGNORE_FIELDS}
        grouped.setdefault(key, []).append(norm)
    out: dict[str, dict[str, str]] = {}
    for key, rows in grouped.items():
        rows.sort(key=lambda r: json.dumps(r, ensure_ascii=False, sort_keys=True))
        merged: dict[str, str] = {}
        for i, row in enumerate(rows):
            suffix = "" if i == 0 else f"#{i + 1}"
            for k, v in row.items():
                merged[k + suffix] = v
        out[key] = merged
    return out


def diff_snapshots(old: dict[str, dict[str, str]], new: dict[str, dict[str, str]]) -> dict[str, dict]:
    """나라별 차이. {나라: {"added_country"|"removed_country"|"changed": ...}}"""
    result: dict[str, dict] = {}
    for key in sorted(set(old) | set(new)):
        if key not in old:
            result[key] = {"added_country": new[key]}
        elif key not in new:
            result[key] = {"removed_country": old[key]}
        else:
            changed = {}
            for f in sorted(set(old[key]) | set(new[key])):
                a, b = old[key].get(f), new[key].get(f)
                if a != b:
                    changed[f] = {"before": a, "after": b}
            if changed:
                result[key] = {"changed": changed}
    return result


def _snapshot_dir(snapshot_root: pathlib.Path) -> pathlib.Path:
    return pathlib.Path(snapshot_root) / "mofa"


def load_previous_snapshot(snapshot_root: pathlib.Path, today: _dt.date) -> tuple[Optional[str], Optional[dict]]:
    """오늘보다 앞선 가장 최근 스냅샷."""
    d = _snapshot_dir(snapshot_root)
    if not d.exists():
        return None, None
    files = sorted(p for p in d.glob("*.json") if p.stem < today.isoformat())
    if not files:
        return None, None
    p = files[-1]
    return p.stem, json.loads(p.read_text(encoding="utf-8"))


def save_snapshot(snapshot_root: pathlib.Path, today: _dt.date, data: dict) -> str:
    d = _snapshot_dir(snapshot_root)
    d.mkdir(parents=True, exist_ok=True)
    p = d / f"{today.isoformat()}.json"
    p.write_text(json.dumps(data, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")
    return str(p)


def summarize(key: str, d: dict) -> str:
    if "added_country" in d:
        return f"[외교부 입국요건] {key}: 새 항목이 생겼어요"
    if "removed_country" in d:
        return f"[외교부 입국요건] {key}: 항목이 사라졌어요"
    fields = ", ".join(list(d["changed"])[:5])
    return f"[외교부 입국요건] {key}: 바뀐 칸 {len(d['changed'])}개 ({fields})"


def run(cfg, fetcher, today: Optional[_dt.date] = None, countries: Optional[list[str]] = None,
        persist: bool = True) -> UnitResult:
    """한 번 실행. persist=False(시험 실행)면 스냅샷·증거를 쓰지 않는다."""
    unit = "mofa"
    today = today or _dt.datetime.now().astimezone().date()
    if not cfg.mofa_service_key:
        return UnitResult(unit, STATUS_NOT_CONFIGURED, message="MOFA_SERVICE_KEY 없음")
    try:
        items = fetch_items(cfg.mofa_api_url, cfg.mofa_service_key, fetcher, cfg.http_timeout_sec, cfg.user_agent)
    except (MofaError, NetworkError, ValueError) as e:
        return UnitResult(unit, STATUS_ERROR, message=str(e))
    if not items:
        # 빈 응답을 '모두 사라짐'으로 읽으면 안 된다
        return UnitResult(unit, STATUS_ERROR, message="항목 0개 — 응답 이상, 비교하지 않음")
    current = normalize_items(items, countries)
    prev_day, previous = load_previous_snapshot(cfg.snapshot_dir, today)
    snap_path = save_snapshot(cfg.snapshot_dir, today, current) if persist else ""
    if previous is None:
        return UnitResult(unit, STATUS_BASELINE, message=f"기준 스냅샷 저장 ({len(current)}개 나라)",
                          evidence_path=snap_path)
    diffs = diff_snapshots(previous, current)
    changes = []
    for key, d in diffs.items():
        diff = {"country": key, "since": prev_day, "diff": d}
        ch = Change(DETECTOR, key, summarize(key, d), diff)
        if persist:
            ch.evidence_path = save_evidence(cfg.evidence_dir, DETECTOR, key,
                                             {"before_day": prev_day, "after_day": today.isoformat(),
                                              "before": previous.get(key), "after": current.get(key), "diff": d},
                                             ch.fingerprint)
        changes.append(ch)
    status = STATUS_CHANGED if changes else STATUS_UNCHANGED
    return UnitResult(unit, status, changes, message=f"{len(current)}개 나라 비교, 변경 {len(changes)}",
                      evidence_path=snap_path)
