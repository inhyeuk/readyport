"""관광지 공식 출처 주간 확인 (LLM 없음, 결정적). 사장님 결정 2026-10-09 ①.

나라마다 packs/src·packs/drafts 의 attractions.json 에서 관광지가 쓰는 **사실 출처**(sources.use = facts·hazard·heritage_registry)
주소와 official_url 을 모은다(같은 주소는 한 번만). 주소 하나 = 단위 하나.

단위마다
  1. robots.txt 확인(사이트마다 한 번). 막혀 있거나 확인 못 하면 받지 않고 manual_check_needed.
  2. GET 한 번(재시도·우회 없음). 봇 차단(403·429·503·캡차 화면)이면 manual_check_needed.
  3. 보이는 글만 뽑아 공백 정리(notice_watch.normalize_text) → sha256 을 지난 값과 비교.
  4. 바뀌면 그 주소를 쓰는 관광지마다 증거 스냅샷을 저장소 밖에 저장:
       ~/.readyport/evidence/<CC>/<id>/aria_<출처id>_<해시12>.txt  (+ 같은 이름 .json 메타: 주소·받은 시각·해시)
     build_attractions 의 인용 대조가 이 .txt 를 '스냅샷'으로 쓴다. Change(지문 = 정규화 diff) 하나를 돌려준다.
  5. 휴관·공사·폐쇄 같은 말(CLOSURE_KEYWORDS)의 개수가 늘었으면 closure_new 에 담는다 → attraction_flags 로 '확인 중' 표시.

같은 주소 첫 실행은 기준만 저장(baseline). 페이지 글 전문은 ARIA SQLite(kv)에만 둔다(저장소 아님).
"""
from __future__ import annotations

import datetime as _dt
import difflib
import hashlib
import json
import pathlib
import re
from dataclasses import dataclass, field
from typing import Optional

from ..detectors.notice_watch import normalize_text, text_hash
from ..models import (STATUS_BASELINE, STATUS_CHANGED, STATUS_ERROR, STATUS_MANUAL, STATUS_UNCHANGED, Change,
                      UnitResult)
from ..net import NetworkError, looks_like_bot_challenge

DETECTOR = "attractions_watch"
WATCH_USES = {"facts", "hazard", "heritage_registry"}
MAX_DIFF_LINES = 120
COUNTRY_RE = re.compile(r"^[A-Z]{2}$")
ID_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")

# 공식 안내에서 '닫힘' 쪽 변화를 알리는 말 (나라 말 + 영어). 늘어나기만 보면 된다(원래 있던 '월요일 휴관'은 기준선).
CLOSURE_KEYWORDS = [
    # 한국어
    # ('공사'만 쓰면 '관광공사' 같은 이름에도 걸린다 — 공사 중·공사로만)
    "임시 휴관", "임시휴관", "휴관", "임시 휴업", "휴업", "폐쇄", "공사 중", "공사로", "출입 통제", "운영 중단",
    # 일본어
    "臨時休館", "休館", "臨時休業", "休業", "工事", "閉鎖", "休園", "通行止め", "閉園", "立入禁止",
    # 중국어
    "闭馆", "閉館", "暂停开放", "暫停開放", "关闭", "關閉", "施工",
    # 영어
    "temporarily closed", "temporary closure", "closed until", "closed for renovation", "closure", "closed",
    "under renovation", "under construction", "suspended",
    # 태국어·베트남어·인도네시아어·말레이어
    "ปิดชั่วคราว", "ปิดปรับปรุง", "tạm đóng cửa", "tạm ngừng", "đóng cửa", "tutup sementara", "ditutup",
]


@dataclass
class WatchTarget:
    cc: str
    url: str
    source_id: str
    ids: list[str] = field(default_factory=list)

    @property
    def unit(self) -> str:
        return f"att:{self.cc}:{self.source_id}"

    @property
    def key(self) -> str:
        return f"att_text:{self.cc}:{hashlib.sha1(self.url.encode('utf-8')).hexdigest()[:16]}"


def _load(path: pathlib.Path) -> Optional[dict]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def _sources_used(obj, out: set[str]) -> None:
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k == "source" and isinstance(v, str):
                out.add(v)
            else:
                _sources_used(v, out)
    elif isinstance(obj, list):
        for v in obj:
            _sources_used(v, out)


def attraction_countries(repo_root: pathlib.Path) -> list[str]:
    root = pathlib.Path(repo_root) / "packs"
    found = set()
    for base in ("src", "drafts"):
        found |= {p.parent.name for p in (root / base).glob("*/attractions.json")}
    return sorted(c for c in found if COUNTRY_RE.match(c))


def collect_targets(repo_root: pathlib.Path, cc: str) -> list[WatchTarget]:
    """원본(공개본) + 작업본의 관광지가 쓰는 사실 출처 주소. 같은 주소는 하나로 합치고 쓰는 관광지 id 를 모은다."""
    docs = [d for d in (_load(pathlib.Path(repo_root) / "packs" / base / cc / "attractions.json")
                        for base in ("src", "drafts")) if d]
    by_url: dict[str, WatchTarget] = {}
    for doc in docs:
        sources = {s.get("id"): s for s in doc.get("sources", [])}
        for att in doc.get("attractions", []):
            aid = att.get("id")
            if not isinstance(aid, str) or not ID_RE.match(aid):
                continue
            used: set[str] = set()
            _sources_used(att, used)
            pairs = []
            for sid in sorted(used):
                s = sources.get(sid) or {}
                if s.get("use") in WATCH_USES and str(s.get("url", "")).startswith("https://"):
                    pairs.append((s["url"], sid))
            if str(att.get("official_url") or "").startswith("https://"):
                pairs.append((att["official_url"], f"official-{aid}"))
            for url, sid in pairs:
                t = by_url.get(url)
                if t is None:
                    t = by_url[url] = WatchTarget(cc, url, re.sub(r"[^a-z0-9_-]", "_", sid.lower())[:60])
                if aid not in t.ids:
                    t.ids.append(aid)
    # 출처 id 가 겹치면(같은 id·다른 주소) 단위 이름을 구분한다
    seen: dict[str, int] = {}
    out = []
    for url in sorted(by_url):
        t = by_url[url]
        n = seen.get(t.source_id, 0)
        seen[t.source_id] = n + 1
        if n:
            t.source_id = f"{t.source_id}-{n + 1}"
        t.ids.sort()
        out.append(t)
    return out


def closure_counts(text: str) -> dict[str, int]:
    low = text.lower()
    return {k: low.count(k.lower()) for k in CLOSURE_KEYWORDS if k.lower() in low}


def closure_increase(before: dict[str, int], after: dict[str, int]) -> list[str]:
    return sorted(k for k, n in after.items() if n > int((before or {}).get(k, 0)))


def snapshot_name(source_id: str, text_sha: str) -> str:
    return f"aria_{source_id}_{text_sha[:12]}.txt"


def save_snapshots(evidence_dir: pathlib.Path, target: WatchTarget, text: str, text_sha: str,
                   now: _dt.datetime) -> list[str]:
    """그 주소를 쓰는 관광지마다 같은 스냅샷을 둔다(인용 대조는 관광지 폴더 안의 파일만 본다)."""
    paths = []
    name = snapshot_name(target.source_id, text_sha)
    meta = {"url": target.url, "source": target.source_id, "fetched_at": now.isoformat(timespec="seconds"),
            "sha256": text_sha, "collected_via": "aria_attractions_watch"}
    for aid in target.ids:
        folder = pathlib.Path(evidence_dir) / target.cc / aid
        folder.mkdir(parents=True, exist_ok=True)
        (folder / name).write_text(text, encoding="utf-8", newline="\n")
        (folder / (name[:-4] + ".json")).write_text(json.dumps(meta, ensure_ascii=False, indent=1), encoding="utf-8",
                                                     newline="\n")
        paths.append(str(folder / name))
    return paths


def check_target(target: WatchTarget, store, fetcher, robots, *, evidence_dir: pathlib.Path, persist: bool,
                 timeout: float = 20.0, user_agent: str = "", now: Optional[_dt.datetime] = None) -> UnitResult:
    unit = target.unit
    extra = {"cc": target.cc, "ids": list(target.ids), "url": target.url}
    allowed = robots.allowed(target.url)
    if allowed is None:
        return UnitResult(unit, STATUS_MANUAL, message="robots.txt 를 확인하지 못함(봇 차단·연결 실패) — 수동 확인 필요",
                          extra=extra)
    if allowed is False:
        return UnitResult(unit, STATUS_MANUAL, message="robots.txt 가 막음 — 받지 않음(수동 확인)", extra=extra)
    headers = {"User-Agent": user_agent} if user_agent else {}
    try:
        resp = fetcher("GET", target.url, headers=headers, timeout=timeout)
    except NetworkError as e:
        return UnitResult(unit, STATUS_ERROR, message=str(e)[:200], extra=extra)
    if looks_like_bot_challenge(resp):
        return UnitResult(unit, STATUS_MANUAL, message=f"봇 차단 또는 HTTP {resp.status} — 수동 확인 필요", extra=extra)
    if resp.status != 200:
        return UnitResult(unit, STATUS_ERROR, message=f"HTTP {resp.status}", extra=extra)
    ctype = (resp.header("content-type") or "text/html").lower()
    if "html" not in ctype and "text/plain" not in ctype:
        return UnitResult(unit, STATUS_MANUAL, message=f"HTML 이 아님({ctype[:40]}) — 수동 확인", extra=extra)
    text = normalize_text(resp.text)
    h = text_hash(text)
    counts = closure_counts(text)
    prev = store.get_value(target.key)
    state = {"hash": h, "text": text, "url": target.url, "closure": counts}
    if prev is None:
        if persist:
            store.set_value(target.key, state)
        return UnitResult(unit, STATUS_BASELINE, message="기준 글 저장", extra={**extra, "closure_now": counts})
    if prev.get("hash") == h:
        return UnitResult(unit, STATUS_UNCHANGED, extra={**extra, "closure_now": counts,
                                                          "closure_base": prev.get("closure") or {}})
    diff_lines = list(difflib.unified_diff(prev.get("text", "").splitlines(), text.splitlines(),
                                           "before", "after", lineterm="", n=1))
    new_closure = closure_increase(prev.get("closure") or {}, counts)
    diff = {"cc": target.cc, "url": target.url, "source": target.source_id, "ids": list(target.ids),
            "hash_before": prev.get("hash"), "hash_after": h, "unified_diff": diff_lines[:MAX_DIFF_LINES],
            "truncated": len(diff_lines) > MAX_DIFF_LINES, "closure_new": new_closure}
    added = sum(1 for d in diff_lines if d.startswith("+") and not d.startswith("+++"))
    removed = sum(1 for d in diff_lines if d.startswith("-") and not d.startswith("---"))
    summary = (f"[관광지] {target.cc} {target.source_id}: 공식 페이지 글이 바뀜 (+{added}/-{removed}줄, "
               f"관광지 {', '.join(target.ids)})" + (f" — 휴관·공사 표현 늘어남: {', '.join(new_closure)}" if new_closure else ""))
    ch = Change(DETECTOR, unit, summary, diff)
    snaps: list[str] = []
    if persist:
        snaps = save_snapshots(evidence_dir, target, text, h, now or _dt.datetime.now().astimezone())
        ch.evidence_path = snaps[0] if snaps else ""
        store.set_value(target.key, state)
    return UnitResult(unit, STATUS_CHANGED, [ch], message=summary, evidence_path=ch.evidence_path,
                      extra={**extra, "closure_new": new_closure, "closure_now": counts,
                             "closure_base": prev.get("closure") or {}, "snapshots": snaps})


# ---------------- 휴관·공사 '확인 중' 표시 상태 ----------------

def flags_key(cc: str) -> str:
    return f"att_flags:{cc}"


def update_flag_state(state: dict, results: list[UnitResult]) -> dict:
    """state = {id: {unit: 기준 개수 합}}. 늘어난 단위가 있으면 id 를 표시하고, 표시한 단위의 개수가 기준 이하로
    돌아오면(그 id 의 모든 표시 단위가) 표시를 뗀다. 확인 못 한 단위(차단·오류)는 판단하지 않는다."""
    state = {k: dict(v) for k, v in (state or {}).items()}
    for r in results:
        if r.status == STATUS_CHANGED and r.extra.get("closure_new"):
            base_total = sum((r.extra.get("closure_base") or {}).values())
            for aid in r.extra.get("ids", []):
                state.setdefault(aid, {}).setdefault(r.unit, base_total)
    for r in results:
        if r.status not in (STATUS_CHANGED, STATUS_UNCHANGED):
            continue
        now_total = sum((r.extra.get("closure_now") or {}).values())
        for aid in list(state):
            if r.unit in state[aid] and not (r.status == STATUS_CHANGED and r.extra.get("closure_new")):
                if now_total <= state[aid][r.unit]:
                    del state[aid][r.unit]
            if not state[aid]:
                del state[aid]
    return state
