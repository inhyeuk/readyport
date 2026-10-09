"""관광지(attractions.json) 검증 → 공개 차수 고르기 → 서명 (SPEC_v5 §4·§5·§10, 사장님 결정 2026-10-09).

파일 세 곳 (§4.1)
  - 작업본  packs/drafts/<CC>/attractions.json   release=draft, 지역마다 wave(공개 차수)를 둘 수 있다. 몇 주씩 편집하며 커밋한다.
  - 원본    packs/src/<CC>/attractions.json      마지막으로 서명한 원본(사람이 읽는 들여쓰기). promote·sign·retire만 쓴다.
  - 서명본  app/src/main/assets/packs/<CC>/attractions.json(+.sig)   앱 내장본(커밋). Hosting 으로는 stage_hosting.py 가 복사한다.

서명 키는 국가 팩 키(rp-2026-1)와 **따로**인 관광지 전용 키(kid rp-att-*)만 받는다. 비밀키는 저장소 밖(~/.readyport/keys/)에만 둔다.
암호를 건 PEM이면 실행할 때 암호를 묻는다(getpass). 환경 변수 RP_ATT_KEY_PASS 가 있으면 그것을 쓴다(⟦결정 D22⟧).

사실 검증
  - 인용 대조: 사실(claims·tips·tags·facts·access·status·seasonal·address_local)마다 ~/.readyport/evidence/<CC>/<id>/extract.json 에
    {field, value, quote, source, snapshot} 이 있어야 하고, quote 가 snapshot 텍스트에 **글자 그대로**(공백만 정규화) 들어 있어야 한다.
    통과한 인용의 sha256 만 packs/curation/<CC>.quotes.json 에 남긴다(인용 원문은 저장소에 넣지 않는다).
  - copycheck: 설명 글(summary·body·tips·visit_note)을 ~/.readyport/copycheck_cache/<CC>/<id>/*.txt(+evidence 스냅샷)와 비교한다.
    8어절 연속 일치 1곳 이상, 또는 8글자 n-gram 겹침 30% 초과면 실패. 결과 해시는 packs/curation/<CC>.copycheck.json.
    글이 그대로인 항목(해시 같음)은 캐시 없이 통과한다.

사용 (PowerShell·bash 공통)
  python tools/attractions/build_attractions.py check [CC ...]                 # CI·ARIA: 스키마·lint (게이트·날짜·대조는 경고만)
  python tools/attractions/build_attractions.py promote JP --wave 1 --kid rp-att-2026-1 --key ~/.readyport/keys/attractions_signing_rp-att-2026-1.pem
  python tools/attractions/build_attractions.py sign JP --kid rp-att-2026-1 --key …   # 원본 다시 서명(경보 기준 갱신 등)
  python tools/attractions/build_attractions.py retire JP --ids a,b --reason safety --kid rp-att-2026-1 --key …   # 긴급 제외
  python tools/attractions/build_attractions.py verify-committed               # CI: 커밋된 서명본 = 원본, 서명·kid·NFC
  python tools/attractions/build_attractions.py verify-quotes JP               # 인용 대조만
  python tools/attractions/build_attractions.py keygen --kid rp-att-2026-1     # 관광지 전용 키 만들기(공개키만 출력)
  python tools/attractions/build_attractions.py protect-key --key …            # 비밀키 PEM 에 암호 걸기
  python tools/attractions/build_attractions.py record JP --ids a,b            # ARIA 주간 갱신: 바뀐 곳만 인용 대조·copycheck 하고 기록(packs/curation)만 갱신
  python tools/attractions/build_attractions.py verify-quotes JP --ids a,b --use-record   # CI: 기록(facts 해시)으로 대조를 마쳤는지 확인(증거 폴더 없이)
  python tools/attractions/build_attractions.py apply-drafts JP --ids a,b --kid rp-att-2026-1 --key …   # CI: 작업본의 그 곳만 원본에 옮겨 다시 서명
  python tools/attractions/build_attractions.py place-ids JP [--ids a,b] [--write]   # Google place ID 채우기(Places API Text Search, 필드 places.id 만)
"""
from __future__ import annotations

import argparse
import base64
import copy
import datetime as dt
import getpass
import hashlib
import io
import json
import math
import os
import pathlib
import re
import sys
import unicodedata

import jsonschema

if __name__ == "__main__":  # 테스트에서 import 할 때는 표준 출력을 건드리지 않는다
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8")

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "packs" / "schema"
DRAFTS = ROOT / "packs" / "drafts"
SRC = ROOT / "packs" / "src"
CURATION = ROOT / "packs" / "curation"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "packs"
PACK_KEYS_KT = ROOT / "app" / "src" / "main" / "java" / "com" / "readyport" / "pack" / "PackVerifier.kt"
STRINGS_DIR = ROOT / "app" / "src" / "main" / "res" / "values"
HOME = pathlib.Path(os.path.expanduser("~")) / ".readyport"
EVIDENCE_DIR = HOME / "evidence"
COPYCHECK_DIR = HOME / "copycheck_cache"
KEYS_DIR = HOME / "keys"

FILE_NAME = "attractions.json"
ATT_KID = re.compile(r"^rp-att-[0-9a-z][0-9a-z-]*$")
UNSETTLED = ["[확인 필요]", "[재확인]", "TODO"]
# 편집 원칙 7 (§5.2): 평가어 금지
FORBIDDEN_WORDS = ["가장 아름다운", "최고의", "꼭 가야 할", "필수 코스", "숨은 명소", "현지인이 사랑하는", "인생샷", "대표적"]
# 유래·배경 표지어 (§10.2 claims ⑤) — 이런 말이 든 글은 claims 근거가 있어야 한다
ORIGIN_MARKERS = ["유래", "전설", "이름은", "불린다", "불려요", "세워졌", "세웠", "지어졌", "지었", "건립", "만들어졌"]
SUPERLATIVE_MARKERS = ["최초", "유일"]
NUMBER_RE = re.compile(r"\d[\d,.]*")
KO_NUMBER_RE = re.compile(r"(?<![가-힣])[일이삼사오육칠팔구십백천만]+\s?(?:세기|년|층|미터|킬로미터)")
SENTENCE_END = re.compile(r"[.!?。](?=\s|$)")
WIKI_DOMAINS = ("wikipedia.org", "wikimedia.org", "wikidata.org")
INVALID_ONLY_REF_PROPS = {"P143", "P4656", "P887", "P3452"}
COPYCHECK_VIA = {"mediawiki_api", "dump", "manual_browser_save"}
COPYCHECK_WORDS = 8
COPYCHECK_CHARS = 8
COPYCHECK_SHARE = 0.30
NEAR_KM, FAR_KM, PAIR_KM = 40.0, 70.0, 60.0
OSM_WARN = 50
# 확인 주기 (§4.4 등급): (경고 일수, 실패 일수)
FRESH_STABLE = (365, 540)
FRESH_SOMETIMES = (180, 365)
FRESH_OFTEN = (30, 60)


def load_json(path: pathlib.Path):
    return json.loads(path.read_text(encoding="utf-8"))


def enums():
    return load_json(SCHEMA_DIR / "attractions.enums.json")


def rules():
    return load_json(SCHEMA_DIR / "advisory_rules.json")


def schema():
    return load_json(SCHEMA_DIR / "attractions.schema.json")


def gates_for(cc: str, gates: dict | None = None) -> dict:
    gates = gates if gates is not None else load_json(CURATION / "gates.json")
    merged = dict(gates.get("default", {}))
    merged.update(gates.get("countries", {}).get(cc, {}))
    return merged


class Report:
    """나라 하나의 검사 결과 — 실패(errors)와 경고(warnings)"""

    def __init__(self, label: str):
        self.label = label
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def error(self, msg: str):
        self.errors.append(f"{self.label}: {msg}")

    def warn(self, msg: str):
        self.warnings.append(f"{self.label}: {msg}")

    def soft(self, strict: bool, msg: str):
        """서명 때만 실패, --check 에서는 경고"""
        (self.error if strict else self.warn)(msg)

    @property
    def ok(self) -> bool:
        return not self.errors


# ======================= 공용 계산 =======================

def walk_strings(obj, path=""):
    if isinstance(obj, str):
        yield path, obj
    elif isinstance(obj, dict):
        for k, v in obj.items():
            yield from walk_strings(k, f"{path}.<key>")
            yield from walk_strings(v, f"{path}.{k}")
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            yield from walk_strings(v, f"{path}[{i}]")


def non_nfc(doc) -> list[str]:
    return [p for p, s in walk_strings(doc) if unicodedata.normalize("NFC", s) != s]


def haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    r = 6371.0088
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def ws(text: str) -> str:
    """공백 정규화(연속 공백 → 하나, 앞뒤 자르기) + NFC — 인용 대조와 해시에 쓴다"""
    return re.sub(r"\s+", " ", unicodedata.normalize("NFC", text)).strip()


def sha256_hex(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def safety_section(pack: dict) -> dict | None:
    return next((s for s in pack.get("sections", []) if s.get("id") == "safety"), None)


def advisory_paragraphs(pack: dict, advisory_rules: dict | None = None) -> list[str] | None:
    """§5.7: safety 섹션(첫 항목)의 문단 가운데 경보 정규식에 걸리는 것만. safety 가 없으면 None"""
    advisory_rules = advisory_rules or rules()
    sec = safety_section(pack)
    if sec is None:
        return None
    rx = re.compile(advisory_rules["advisory_paragraph_regex"])
    return [p for p in sec.get("body_ko", []) if rx.search(p)]


def advisory_hash(pack: dict, advisory_rules: dict | None = None) -> str | None:
    """§5.7 경보 문단 해시 — 앱 Advisory.advisoryHash 와 같은 계산(공용 벡터 packs/schema/advisory_hash_vectors.json)"""
    paras = advisory_paragraphs(pack, advisory_rules)
    if paras is None:
        return None
    return sha256_hex("\n".join(ws(p) for p in paras))


def is_high_advisory(paragraph: str, advisory_rules: dict | None = None) -> bool:
    advisory_rules = advisory_rules or rules()
    return any(w in paragraph for w in advisory_rules["high_words"])


def sentences(text: str) -> int:
    t = text.strip()
    if not t:
        return 0
    n = len(SENTENCE_END.findall(t))
    return n if n > 0 else 1


def canonical_bytes(doc: dict) -> bytes:
    """서명하는 바이트: 공백 없는 UTF-8, 키 순서는 원문 그대로(결정적 — 생성 시각 없음)"""
    return json.dumps(doc, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def pretty_text(doc: dict) -> str:
    return json.dumps(doc, ensure_ascii=False, indent=2) + "\n"


def parse_date(s) -> dt.date | None:
    try:
        return dt.date.fromisoformat(str(s))
    except ValueError:
        return None


def next_version(previous: str | None, today: dt.date) -> str:
    stem = today.strftime("%Y.%m.%d")
    if previous and previous.startswith(stem + "-"):
        try:
            return f"{stem}-{int(previous.split('-', 1)[1]) + 1}"
        except ValueError:
            pass
    return f"{stem}-1"


def norm_alias(s: str) -> str:
    """중복 별칭 판정용 — 앱 KoreanNormalize 와 같은 방향(소문자·공백·기호 제거). 정확히 같을 필요는 없다(경고만)"""
    t = unicodedata.normalize("NFC", s).lower()
    return re.sub(r"[\s·ㆍ・/\-_.,()]+", "", t)


# ======================= 문서 검사 =======================

def schema_errors(doc: dict) -> list[str]:
    return [f"{'/'.join(map(str, e.path)) or '(최상위)'}: {e.message}" for e in jsonschema.Draft202012Validator(schema()).iter_errors(doc)]


def pack_airport_codes(pack: dict | None) -> list[str]:
    return [a["code"] for a in (pack or {}).get("airports", [])]


def text_fields(att: dict) -> list[str]:
    """claims 검사·copycheck 대상 글: summary·body·tips·visit_note"""
    out = [att.get("summary_ko", "")] + list(att.get("body_ko", []))
    out += [t.get("text", "") for t in att.get("tips_ko", [])]
    vn = (att.get("facts") or {}).get("visit_note_ko")
    if vn:
        out.append(vn)
    return [t for t in out if t]


def know_lines(att: dict) -> int:
    """상세 '가기 전에 알아 둘 것' 줄 수 (§5.2-13 ②)"""
    facts = att.get("facts") or {}
    n = 0
    if facts.get("kind") == "public_space":
        n += 1
    else:
        if facts.get("regular_closed") and facts["regular_closed"] != ["unknown"]:
            n += 1
        if facts.get("entry") in ("free", "paid"):
            n += 1
    if facts.get("booking") in ("recommended", "required"):
        n += 1
    if facts.get("visit_note_ko"):
        n += 1
    n += len(att.get("seasonal", []))
    n += len(att.get("tips_ko", []))
    n += sum(1 for t in att.get("tags", []) if t.get("id") in ("dress_code", "step_free"))
    return n


def is_thin(att: dict) -> bool:
    """상세 최소 충실도 미달 (§5.2-13)"""
    body_sentences = sum(sentences(b) for b in att.get("body_ko", []))
    access = att.get("access") or {}
    return (
        body_sentences < 2
        or know_lines(att) < 2
        or not access.get("modes")
        or not access.get("nearest_ko")
        or not att.get("official_url")
    )


def check_claims(att: dict, where: str, report: Report, curation_att: dict):
    """§10.2 claims 검사 ①~⑤ (⑥ 유효 참조는 인용 대조에서)"""
    claims_text = " ".join(c.get("text_ko", "") for c in att.get("claims", []))
    tag_ids = {t.get("id") for t in att.get("tags", [])}
    for text in text_fields(att):
        for tok in NUMBER_RE.findall(text) + KO_NUMBER_RE.findall(text):
            tok = tok.rstrip(".,")
            if tok and tok not in claims_text:
                report.error(f"{where}: 글의 숫자·수량 '{tok}' 가 claims 에 없음 (claims ①)")
        if "세계유산" in text and "unesco" not in tag_ids:
            report.error(f"{where}: '세계유산' 이라고 썼는데 tags 에 unesco 가 없음 (claims ②)")
        for w in FORBIDDEN_WORDS:
            if w in text:
                report.error(f"{where}: 금지어 '{w}' (편집 원칙 7)")
        if "가장 " in text and "가장" not in claims_text:
            report.error(f"{where}: '가장 …' 은 claims 근거가 있어야 함 (claims ④)")
        if any(m in text for m in ORIGIN_MARKERS) and not att.get("claims"):
            report.error(f"{where}: 유래·배경 글은 claims 근거가 있어야 함 (claims ⑤)")
        for m in SUPERLATIVE_MARKERS:
            if m in text and not curation_att.get("superlative_basis"):
                report.error(f"{where}: '{m}' 은 curation superlative_basis(근거 문구 위치)가 있어야 함 (편집 원칙 11)")


def check_doc(doc: dict, cc: str, pack: dict | None, *, mode: str, strict: bool, report: Report,
              curation: dict | None = None, today: dt.date | None = None, gates: dict | None = None,
              previous: dict | None = None):
    """
    문서 하나 검사. mode: draft(작업본) / published(원본·서명본) / sample(debug 샘플: 모양·값·NFC만) / retire(긴급 제외: 무결성만).
    strict=True 이면 서명 시점 — 게이트·날짜·근거 누락을 실패로, 아니면 경고로.
    """
    e = enums()
    curation = curation or {}
    today = today or dt.date.today()
    for msg in schema_errors(doc):
        report.error(f"스키마 {msg}")
    if doc.get("doc_type") != "attractions":
        report.error("doc_type 이 attractions 가 아님")
    if doc.get("country") != cc:
        report.error(f"country '{doc.get('country')}' 가 폴더 이름 {cc} 와 다름")
    for p in non_nfc(doc):
        report.error(f"NFC 가 아닌 문자열 {p}")
    release = doc.get("release")
    if mode == "draft" and release != "draft":
        report.error("작업본(drafts)은 release=draft 만")
    if mode in ("published", "retire") and release != "published":
        report.error("원본·서명본은 release=published 만 (draft 는 서명하지 않는다)")
    if mode != "sample" and doc.get("sample"):
        report.error("sample=true 문서는 서명·공개하지 않는다")

    # ---- 출처 ----
    sources = {}
    for i, s in enumerate(doc.get("sources", [])):
        if s.get("id") in sources:
            report.error(f"sources[{i}].id '{s.get('id')}' 가 겹침")
        sources[s.get("id")] = s
        if s.get("use") not in e["source_uses"]:
            report.error(f"sources[{i}].use '{s.get('use')}' 는 {e['source_uses']} 중 하나")
        if s.get("use") in e["license_required_uses"] and not s.get("license"):
            report.error(f"sources[{i}] use={s.get('use')} 은 license 필수")
        if s.get("use") == "sample" and mode in ("published", "retire"):
            report.error(f"sources[{i}] use=sample 은 서명할 수 없음")

    def need_source(sid, where):
        if sid not in sources:
            report.error(f"{where}: source '{sid}' 가 sources 에 없음")

    # ---- 지역 ----
    regions = {}
    pack_codes = pack_airport_codes(pack)
    safety = safety_section(pack) if pack else None
    airport_owner: dict[str, str] = {}
    for i, r in enumerate(doc.get("regions", [])):
        where = f"regions[{i}]({r.get('id')})"
        if r.get("id") in regions:
            report.error(f"{where}: id 가 겹침")
        regions[r.get("id")] = r
        if not str(r.get("id", "")).startswith(cc.lower() + "_"):
            report.error(f"{where}: id 는 '{cc.lower()}_' 로 시작")
        if r.get("kind") not in e["region_kinds"]:
            report.error(f"{where}: kind '{r.get('kind')}' 는 {e['region_kinds']} 중 하나")
        if "wave" in r and release != "draft":
            report.error(f"{where}: wave 는 작업본에만 (promote 가 지운다)")
        for code in r.get("airports", []):
            if pack is not None and code not in pack_codes:
                report.error(f"{where}: 공항 {code} 가 pack.json airports 에 없음")
            if code in airport_owner:
                report.error(f"{where}: 공항 {code} 가 {airport_owner[code]} 에도 있음")
            airport_owner[code] = r.get("id")
        check_advisory(r.get("advisory") or {}, cc, safety, where, report, mode)
        if mode not in ("sample",) and not r.get("hub", {}).get("qid"):
            report.error(f"{where}: hub.qid 필수 (교통 거점 Wikidata 항목)")
    for i, r in enumerate(doc.get("regions", [])):
        where = f"regions[{i}]({r.get('id')})"
        if r.get("kind") == "daytrip":
            bases = r.get("base_regions") or []
            if not bases:
                report.error(f"{where}: daytrip 은 base_regions 가 1개 이상")
            for b in bases:
                if regions.get(b, {}).get("kind") != "base":
                    report.error(f"{where}: base_regions '{b}' 가 base 지역이 아님")
        elif r.get("base_regions"):
            report.error(f"{where}: base 지역에는 base_regions 를 두지 않는다")

    # ---- 공항 매핑 (빠짐 0) ----
    unmapped = {}
    for i, u in enumerate(doc.get("unmapped_airports", [])):
        if u.get("reason") not in e["unmapped_reasons"]:
            report.error(f"unmapped_airports[{i}].reason '{u.get('reason')}' 는 {e['unmapped_reasons']} 중 하나")
        if u.get("code") in airport_owner or u.get("code") in unmapped:
            report.error(f"unmapped_airports[{i}]: 공항 {u.get('code')} 가 지역에도 있거나 겹침")
        if pack is not None and u.get("code") not in pack_codes:
            report.error(f"unmapped_airports[{i}]: 공항 {u.get('code')} 가 pack.json airports 에 없음")
        unmapped[u.get("code")] = u
    if pack is not None and mode != "sample":
        for code in pack_codes:
            if code not in airport_owner and code not in unmapped:
                report.error(f"팩 공항 {code} 가 어느 지역에도, unmapped_airports 에도 없음")

    # ---- 관광지 ----
    retired_ids = {r.get("id") for r in doc.get("retired", [])}
    seen = set()
    osm = 0
    for i, a in enumerate(doc.get("attractions", [])):
        aid = a.get("id")
        where = f"attractions[{i}]({aid})"
        cur_att = curation.get("attractions", {}).get(aid, {})
        if aid in seen:
            report.error(f"{where}: id 가 겹침")
        seen.add(aid)
        if aid in retired_ids:
            report.error(f"{where}: retired 에 있는 id 를 다시 쓸 수 없음")
        if a.get("region") not in regions:
            report.error(f"{where}: region '{a.get('region')}' 이 regions 에 없음")
        if a.get("category") not in e["categories"]:
            report.error(f"{where}: category '{a.get('category')}' 는 {e['categories']} 중 하나")
        need_source(a.get("source"), where)
        need_source((a.get("names") or {}).get("source"), f"{where}.names")
        for t in a.get("tags", []):
            if t.get("id") not in e["tags"]:
                report.error(f"{where}: tag '{t.get('id')}' 는 enums 에 없음")
            need_source(t.get("source"), f"{where}.tags[{t.get('id')}]")
        geo = a.get("geo") or {}
        if geo.get("kind") not in e["geo_kinds"]:
            report.error(f"{where}: geo.kind '{geo.get('kind')}' 는 {e['geo_kinds']} 중 하나")
        need_source(geo.get("source"), f"{where}.geo")
        if geo.get("osm"):
            osm += 1
        if a.get("category") in ("nature", "sea_island") and geo.get("kind") == "site":
            report.warn(f"{where}: nature·sea_island 는 방문 지점(geo.kind) 권장")
        access = a.get("access")
        if access:
            for m in access.get("modes", []):
                if m not in e["access_modes"]:
                    report.error(f"{where}: access.modes '{m}' 는 enums 에 없음")
            need_source(access.get("source"), f"{where}.access")
            if access.get("nearest_local"):
                need_source(access["nearest_local"].get("source"), f"{where}.access.nearest_local")
        if a.get("address_local"):
            need_source(a["address_local"].get("source"), f"{where}.address_local")
        facts = a.get("facts") or {}
        if facts.get("kind") not in e["facts_kinds"]:
            report.error(f"{where}: facts.kind '{facts.get('kind')}' 는 {e['facts_kinds']} 중 하나")
        if facts.get("kind") == "public_space":
            if a.get("category") not in e["public_space_categories"]:
                report.error(f"{where}: public_space 는 {e['public_space_categories']} 종류만")
            if "free_entry" in {t.get("id") for t in a.get("tags", [])}:
                report.error(f"{where}: public_space 에는 free_entry 태그를 붙이지 않는다")
        else:
            for k in ("entry", "regular_closed", "source", "last_verified"):
                if k not in facts:
                    report.error(f"{where}: 시설(facility)은 facts.{k} 필수")
            # 예약 안내는 공식 문구가 있을 때만 싣는다 — 없다고 'none'을 지어 넣지 않는다(2026-10-09 일본 시범: 센소지·후시미)
            if "booking" not in facts:
                report.warn(f"{where}: facts.booking 없음 — 공식 예약 안내를 못 찾았으면 비워 둔다(화면에 안 보임)")
        if facts.get("source"):
            need_source(facts["source"], f"{where}.facts")
        if "entry" in facts and facts["entry"] not in e["entry"]:
            report.error(f"{where}: facts.entry '{facts['entry']}' 는 {e['entry']} 중 하나")
        if "booking" in facts and facts["booking"] not in e["booking"]:
            report.error(f"{where}: facts.booking '{facts['booking']}' 는 {e['booking']} 중 하나")
        for d in facts.get("regular_closed", []):
            if d not in e["regular_closed"]:
                report.error(f"{where}: facts.regular_closed '{d}' 는 enums 에 없음")
        for j, s in enumerate(a.get("seasonal", [])):
            if s.get("kind") not in e["seasonal_kinds"]:
                report.error(f"{where}: seasonal[{j}].kind '{s.get('kind')}'")
            need_source(s.get("source"), f"{where}.seasonal[{j}]")
        for rk in a.get("risk", []):
            if rk not in e["risk"]:
                report.error(f"{where}: risk '{rk}' 는 enums 에 없음")
        status = a.get("status") or {}
        if status.get("value") not in e["status"]:
            report.error(f"{where}: status.value '{status.get('value')}' 는 {e['status']} 중 하나")
        need_source(status.get("source"), f"{where}.status")
        if "volcano" in a.get("risk", []):
            hazard_ids = {sid for sid, s in sources.items() if s.get("use") == "hazard"}
            if status.get("source") not in hazard_ids and not any(t.get("source") in hazard_ids for t in a.get("tips_ko", [])):
                report.error(f"{where}: risk volcano 는 hazard 출처의 status 나 tip 이 필수")
        check_advisory(a.get("advisory") or {}, cc, safety, where, report, mode)
        if a.get("region") in regions:
            reg_level = (regions[a["region"]].get("advisory") or {}).get("level")
            if level_rank(reg_level) < level_rank((a.get("advisory") or {}).get("level")) and mode != "sample":
                report.error(f"{where}: 지역 경보 단계가 관광지 단계보다 낮음 (지역 = 그 지역 관광지의 최고 단계)")
        for c in a.get("claims", []):
            need_source(c.get("source"), f"{where}.claims[{c.get('id')}]")
        for j, t in enumerate(a.get("tips_ko", [])):
            need_source(t.get("source"), f"{where}.tips_ko[{j}]")
        for d in (a.get("rank") or {}).get("designations", []):
            need_source(d.get("source"), f"{where}.rank.designations")
        # 글 길이 (§4.4, §10.2)
        summary = a.get("summary_ko", "")
        if len(summary) > 40 or sentences(summary) > 1:
            report.error(f"{where}: summary_ko 는 한 문장 40자 이하 (지금 {len(summary)}자)")
        body = a.get("body_ko", [])
        if sum(len(b) for b in body) > 300 or sum(sentences(b) for b in body) > 4:
            report.error(f"{where}: body_ko 는 4문장·300자 이하")
        if len(a.get("tips_ko", [])) > 4:
            report.error(f"{where}: tips_ko 는 4개 이하")
        for p, s in walk_strings(a):
            for mark in UNSETTLED:
                if mark in s:
                    report.error(f"{where}{p}: 미확정 표시 '{mark}'")
        if mode != "sample":
            check_claims(a, where, report, cur_att)
        # 별칭 (§4.4)
        aliases = a.get("aliases_ko", []) + a.get("aliases_en", [])
        mentions = set(a.get("mentions_ko", []))
        for al in aliases:
            if al in mentions:
                report.error(f"{where}: '{al}' 가 aliases 와 mentions_ko 에 둘 다 있음")
        names = a.get("names") or {}
        title_norms = {norm_alias(x) for x in (names.get("ko"), names.get("en")) if x}
        alias_norms = set()
        for al in aliases:
            n = norm_alias(al)
            if n in title_norms or n in alias_norms:
                report.warn(f"{where}: 중복 별칭 '{al}' (정규화하면 이름·다른 별칭과 같음)")
            alias_norms.add(n)
        if mode in ("published",):
            if cc == "CN" and not a.get("address_local"):
                report.error(f"{where}: CN 은 address_local(공식 중문 주소) 필수")
            if cc == "TH" and not names.get("local_short") and not cur_att.get("no_local_short_ok"):
                report.error(f"{where}: TH 는 names.local_short 필수 (없으면 curation no_local_short_ok)")
        if mode == "published" and strict:
            size = len(canonical_bytes(a))
            limit = gates_for(cc, gates).get("max_bytes_per_place", 4608)
            if size > limit:
                report.error(f"{where}: 크기 {size}B > {limit}B")
        if mode == "published":
            check_freshness(a, where, report, strict, today)
    if osm > OSM_WARN:
        report.warn(f"OSM 좌표 {osm}개 > {OSM_WARN} (비실질 추출 범위 확인)")
    if osm > 0 and not string_key_exists("settings_credit_osm"):
        report.error("osm 좌표가 있는데 settings_credit_osm 문자열이 없음")
    for sid, s in sources.items():
        if s.get("attribution_required") and not string_key_exists(f"settings_credit_{sid}"):
            report.error(f"출처 '{sid}' 는 attribution_required 인데 settings_credit_{sid} 문자열이 없음")

    # ---- 빠진 항목 ----
    att_ids = seen
    for i, r in enumerate(doc.get("retired", [])):
        if r.get("reason") not in e["retired_reasons"]:
            report.error(f"retired[{i}].reason '{r.get('reason')}' 는 {e['retired_reasons']} 중 하나")
        if r.get("reason") == "merged":
            target = r.get("replaced_by")
            if not target:
                report.error(f"retired[{i}]: merged 는 replaced_by 필수")
            elif target in retired_ids:
                report.error(f"retired[{i}]: replaced_by '{target}' 가 retired 를 가리킴(연쇄는 1단계까지)")
            elif target not in att_ids:
                report.error(f"retired[{i}]: replaced_by '{target}' 가 attractions 에 없음")
    if previous is not None:
        old_ids = {a.get("id") for a in previous.get("attractions", [])} | {r.get("id") for r in previous.get("retired", [])}
        for gone in sorted(old_ids - att_ids - retired_ids):
            report.error(f"이전 서명본의 '{gone}' 가 사라짐 — 조용히 지우지 않는다. retired 에 사유를 적는다")

    if mode in ("sample", "retire"):
        return
    check_regions_geo(doc, regions, report, strict, curation)
    check_group_aliases(doc, regions, report, curation)
    if pack is not None and mode == "published":
        check_watch(doc, pack, report)
    check_gates(doc, cc, report, strict, gates, curation)
    if mode == "published" and strict:
        limit = gates_for(cc, gates).get("max_bytes_per_file", 256000)
        if len(canonical_bytes(doc)) > limit:
            report.error(f"파일 크기 {len(canonical_bytes(doc))}B > {limit}B")


LEVEL_ORDER = {"none": 0, "1": 1, "2": 2, "special": 3, "3": 3, "4": 4}


def level_rank(level) -> int:
    return LEVEL_ORDER.get(level, -1)


def check_advisory(adv: dict, cc: str, safety: dict | None, where: str, report: Report, mode: str):
    e = enums()
    level = adv.get("level")
    if level not in e["advisory_levels"]:
        report.error(f"{where}: advisory.level '{level}' 는 {e['advisory_levels']} 중 하나")
    if level in e["advisory_hidden_levels"]:
        report.error(f"{where}: 여행경보 {level} 단계 지역·관광지는 싣지 않는다 (편집 원칙 3)")
    if cc in ("TW", "SG") and level != "none":
        report.error(f"{where}: {cc} 는 advisory none 만")
    if safety is not None and mode != "sample" and adv.get("source") != safety.get("source"):
        report.error(f"{where}: advisory.source '{adv.get('source')}' 가 pack safety 출처 '{safety.get('source')}' 와 다름")


def check_freshness(a: dict, where: str, report: Report, strict: bool, today: dt.date):
    def judge(date_str, grade, label):
        d = parse_date(date_str)
        if d is None:
            return
        age = (today - d).days
        warn_days, fail_days = grade
        if age > fail_days:
            report.soft(strict, f"{where}: {label} 확인일 {date_str} — {age}일 지남(실패 기준 {fail_days}일)")
        elif age > warn_days:
            report.warn(f"{where}: {label} 확인일 {date_str} — {age}일 지남(경고 기준 {warn_days}일)")

    judge(a.get("last_verified"), FRESH_STABLE, "항목")
    judge((a.get("geo") or {}).get("last_verified"), FRESH_STABLE, "좌표")
    for c in a.get("claims", []):
        judge(c.get("last_verified"), FRESH_STABLE, f"claims[{c.get('id')}]")
    for key in ("facts", "address_local", "access"):
        if a.get(key):
            judge(a[key].get("last_verified"), FRESH_SOMETIMES, key)
    for t in a.get("tips_ko", []) + a.get("tags", []) + a.get("seasonal", []):
        judge(t.get("last_verified"), FRESH_SOMETIMES, "tips·tags·seasonal")
    judge((a.get("advisory") or {}).get("last_verified"), FRESH_SOMETIMES, "advisory")
    status = a.get("status") or {}
    often = status.get("value") != "open" or bool(a.get("risk"))
    judge(status.get("last_verified"), FRESH_OFTEN if often else FRESH_SOMETIMES, "status")


def check_regions_geo(doc: dict, regions: dict, report: Report, strict: bool, curation: dict):
    """§3.1 lint: base 지역 관광지–hub 40km 경고(근거 없으면 서명 실패)·70km 실패, daytrip 70km 경고, 지역 안 두 곳 60km 경고"""
    by_region: dict[str, list[dict]] = {}
    for a in doc.get("attractions", []):
        by_region.setdefault(a.get("region"), []).append(a)
    for rid, places in by_region.items():
        r = regions.get(rid)
        if not r or "hub" not in r:
            continue
        hub = r["hub"]
        keep = (curation.get("regions", {}).get(rid, {}) or {}).get("keep_over_40km", {})
        for a in places:
            g = a.get("geo") or {}
            if "lat" not in g:
                continue
            km = haversine_km(hub["lat"], hub["lng"], g["lat"], g["lng"])
            if r.get("kind") == "base":
                if km > FAR_KM:
                    report.error(f"{rid}/{a.get('id')}: hub 에서 {km:.0f}km > {FAR_KM:.0f}km — daytrip 지역으로 나눈다")
                elif km > NEAR_KM:
                    if keep.get(a.get("id")):
                        report.warn(f"{rid}/{a.get('id')}: hub 에서 {km:.0f}km > {NEAR_KM:.0f}km (유지 근거 있음)")
                    else:
                        report.soft(strict, f"{rid}/{a.get('id')}: hub 에서 {km:.0f}km > {NEAR_KM:.0f}km — curation keep_over_40km 근거 필요")
            elif km > FAR_KM:
                report.warn(f"{rid}/{a.get('id')}: daytrip hub 에서 {km:.0f}km")
        for i in range(len(places)):
            for j in range(i + 1, len(places)):
                a, b = places[i].get("geo") or {}, places[j].get("geo") or {}
                if "lat" in a and "lat" in b and haversine_km(a["lat"], a["lng"], b["lat"], b["lng"]) > PAIR_KM:
                    report.warn(f"{rid}: {places[i].get('id')}–{places[j].get('id')} 거리 {PAIR_KM:.0f}km 초과")


def check_group_aliases(doc: dict, regions: dict, report: Report, curation: dict):
    """§3.1 묶음 이름 별칭(예: 예스진지)은 구성 지명이 모두 그 지역 관광지 이름·별칭에 있어야 한다"""
    for rid, cur in (curation.get("regions") or {}).items():
        for alias, parts in (cur or {}).get("group_alias_parts", {}).items():
            words = []
            for a in doc.get("attractions", []):
                if a.get("region") == rid:
                    n = a.get("names") or {}
                    words += [n.get("ko", ""), n.get("en", "")] + a.get("aliases_ko", [])
            text = " ".join(words)
            for part in parts:
                if part not in text:
                    report.error(f"{rid}: 묶음 별칭 '{alias}' 의 구성 지명 '{part}' 가 그 지역 관광지에 없음")


def check_watch(doc: dict, pack: dict, report: Report):
    """§5.6 watch 기준선: 지금 pack 경보 문단 중 3단계 이상 문단에 advisory_watch_ko 가 이미 나오면 실패"""
    paras = advisory_paragraphs(pack) or []
    high = [p for p in paras if is_high_advisory(p)]
    for r in doc.get("regions", []):
        for w in r.get("advisory_watch_ko", []):
            if any(w in p for p in high):
                report.error(f"{r.get('id')}: advisory_watch_ko '{w}' 가 지금 팩 경보(3단계 이상) 문단에 이미 있음")


def visible_attractions(doc: dict) -> list[dict]:
    e = enums()
    regions = {r.get("id") for r in doc.get("regions", [])}
    return [
        a for a in doc.get("attractions", [])
        if a.get("category") in e["categories"]
        and a.get("region") in regions
        and (a.get("advisory") or {}).get("level") not in e["advisory_hidden_levels"]
    ]


def gate_failures(doc: dict, cc: str, gates: dict | None = None, curation: dict | None = None) -> tuple[list[str], list[str]]:
    """§1 출시 게이트. (실패, 경고)"""
    g = gates_for(cc, gates)
    curation = curation or {}
    fails, warns = [], []
    places = visible_attractions(doc)
    if len(places) < g["min_places"]:
        fails.append(f"관광지 {len(places)}곳 < {g['min_places']}곳")
    regions = {r["id"]: r for r in doc.get("regions", [])}
    count: dict[str, int] = {}
    for a in places:
        count[a["region"]] = count.get(a["region"], 0) + 1
    base_visible = [rid for rid, n in count.items() if regions[rid].get("kind") == "base" and n > 0]
    if len(base_visible) < g["min_base_regions"]:
        fails.append(f"보이는 base 지역 {len(base_visible)}곳 < {g['min_base_regions']}곳")
    for rid, n in sorted(count.items()):
        need = g["min_per_base_region"] if regions[rid].get("kind") == "base" else g["min_per_daytrip_region"]
        if n < need:
            fails.append(f"지역 {rid} 관광지 {n}곳 < {need}곳")
    cats: dict[str, int] = {}
    for a in places:
        cats[a["category"]] = cats.get(a["category"], 0) + 1
    shown = {c: n for c, n in cats.items() if n >= g["min_per_category"]}
    if len(shown) < g["min_categories"]:
        fails.append(f"보이는 종류(2곳 이상) {len(shown)}개 < {g['min_categories']}개")
    if places:
        top, n = max(cats.items(), key=lambda kv: kv[1])
        if n / len(places) > g["category_share_warn"]:
            if curation.get("category_over_cap_reason"):
                warns.append(f"종류 {top} 가 {n}/{len(places)} (상한 초과 사유 있음)")
            else:
                fails.append(f"종류 {top} 가 {n}/{len(places)} > {g['category_share_warn']:.0%} — curation category_over_cap_reason 필요")
        thin = [a["id"] for a in places if is_thin(a)]
        if thin:
            ratio = len(thin) / len(places)
            msg = f"상세 최소 충실도 미달 {len(thin)}곳({ratio:.0%}): {', '.join(thin)}"
            (fails if ratio > g["max_thin_detail_ratio"] else warns).append(msg)
    return fails, warns


def check_gates(doc: dict, cc: str, report: Report, strict: bool, gates: dict | None, curation: dict):
    fails, warns = gate_failures(doc, cc, gates, curation)
    for f in fails:
        report.soft(strict and doc.get("release") == "published", f"게이트: {f}")
    for w in warns:
        report.warn(f"게이트: {w}")


_STRINGS_CACHE: set[str] | None = None


def string_key_exists(key: str) -> bool:
    global _STRINGS_CACHE
    if _STRINGS_CACHE is None:
        _STRINGS_CACHE = set()
        for f in STRINGS_DIR.glob("strings*.xml"):
            _STRINGS_CACHE.update(re.findall(r'<string name="([^"]+)"', f.read_text(encoding="utf-8")))
    return key in _STRINGS_CACHE


# ======================= 인용 대조 (§5.3-3) =======================

def required_fact_fields(att: dict) -> dict[str, str]:
    """사실을 담은 필드 → 그 필드의 출처 id. 이 필드마다 인용이 있어야 한다"""
    out: dict[str, str] = {}
    for c in att.get("claims", []):
        out[f"claims.{c.get('id')}"] = c.get("source")
    for i, t in enumerate(att.get("tips_ko", [])):
        out[f"tips_ko.{i}"] = t.get("source")
    for t in att.get("tags", []):
        out[f"tags.{t.get('id')}"] = t.get("source")
    facts = att.get("facts") or {}
    if facts.get("kind") != "public_space" or facts.get("source"):
        if facts.get("source"):
            out["facts"] = facts.get("source")
    if att.get("access"):
        out["access"] = att["access"].get("source")
    if att.get("address_local"):
        out["address_local"] = att["address_local"].get("source")
    status = att.get("status") or {}
    if status.get("value") != "open":
        out["status"] = status.get("source")
    for i, s in enumerate(att.get("seasonal", [])):
        out[f"seasonal.{i}"] = s.get("source")
    return out


def facts_fingerprint(att: dict) -> str:
    """인용이 필요한 필드의 내용 해시 — 그대로면 다시 서명할 때 evidence 없이 통과"""
    fields = {k: None for k in required_fact_fields(att)}
    snap = {
        "claims": att.get("claims", []), "tips": att.get("tips_ko", []), "tags": att.get("tags", []),
        "facts": att.get("facts"), "access": att.get("access"), "address": att.get("address_local"),
        "status": att.get("status"), "seasonal": att.get("seasonal", []), "fields": sorted(fields),
    }
    return sha256_hex(json.dumps(snap, ensure_ascii=False, sort_keys=True))


def wd_reference_valid(refs: list[dict]) -> bool:
    """§5.1 Wikidata 유효 참조: P248(위키미디어 프로젝트가 아닌 대상) 또는 P854(위키 도메인이 아닌 URL)가 하나라도 있어야"""
    for ref in refs or []:
        prop = ref.get("property")
        if prop == "P248" and ref.get("target") and not ref.get("target_is_wikimedia", False):
            return True
        if prop == "P854":
            host = re.sub(r"^https?://", "", str(ref.get("target", ""))).split("/")[0].lower()
            if host and not any(host == d or host.endswith("." + d) for d in WIKI_DOMAINS):
                return True
    return False


def verify_quotes(doc: dict, cc: str, evidence_dir: pathlib.Path, report: Report,
                  record: dict | None = None, ids: set[str] | None = None) -> dict:
    """인용 대조. 통과한 항목의 {facts_sha256, quotes:[sha256…]} 기록을 돌려준다.
    [record]의 facts_sha256 이 지금과 같은 항목은 evidence 없이 통과(이미 대조함)."""
    record = record or {}
    sources = {s.get("id"): s for s in doc.get("sources", [])}
    out = {}
    for att in doc.get("attractions", []):
        aid = att.get("id")
        if ids is not None and aid not in ids:
            continue
        fp = facts_fingerprint(att)
        prev = record.get(aid)
        if prev and prev.get("facts_sha256") == fp:
            out[aid] = prev
            continue
        base = evidence_dir / cc / aid
        extract_path = base / "extract.json"
        if not extract_path.is_file():
            report.error(f"{aid}: 인용 대조 기록 없음 ({extract_path}) — 사실이 바뀌었거나 처음 서명")
            continue
        try:
            entries = load_json(extract_path)
        except (ValueError, OSError) as ex:
            report.error(f"{aid}: extract.json 을 읽지 못함 ({ex})")
            continue
        verified: dict[str, list[str]] = {}
        for k, entry in enumerate(entries):
            field = entry.get("field")
            quote = ws(str(entry.get("quote", "")))
            snap = base / str(entry.get("snapshot", ""))
            if not quote:
                report.error(f"{aid}: extract[{k}] quote 비어 있음")
                continue
            if not entry.get("snapshot") or not snap.is_file():
                report.error(f"{aid}: extract[{k}] 스냅샷 파일 없음 ({entry.get('snapshot')})")
                continue
            if quote not in ws(snap.read_text(encoding="utf-8")):
                report.error(f"{aid}: extract[{k}]({field}) 인용이 스냅샷에 글자 그대로 없음 — 이 사실 행을 거부")
                continue
            verified.setdefault(field, []).append(entry.get("source"))
            verified.setdefault(f"__wd__{field}", []).append(json.dumps(entry.get("wd_reference") or []))
            verified.setdefault("__quotes__", []).append(sha256_hex(quote))
        ok = True
        for field, sid in required_fact_fields(att).items():
            srcs = verified.get(field, [])
            if not srcs:
                report.error(f"{aid}: {field} 의 인용이 없음 (출처 {sid})")
                ok = False
            elif sid not in srcs:
                report.error(f"{aid}: {field} 인용의 출처 {srcs} 가 필드 출처 '{sid}' 와 다름")
                ok = False
            if sources.get(sid, {}).get("use") == "skeleton" and field.startswith("claims."):
                refs = [r for js in verified.get(f"__wd__{field}", []) for r in json.loads(js)]
                if not wd_reference_valid(refs):
                    report.error(f"{aid}: {field} 는 Wikidata statement 인데 유효 참조(P248·P854)가 없음 (claims ⑥)")
                    ok = False
        if ok:
            out[aid] = {"facts_sha256": fp, "quotes": sorted(set(verified.get("__quotes__", [])))}
    return out


# ======================= copycheck (§5.4) =======================

def copy_text(att: dict) -> str:
    return "\n".join(text_fields(att))


def copycheck_text(ours: str, theirs: str) -> list[str]:
    """실패 이유 목록 (빈 목록 = 통과)"""
    problems = []
    ow = ours.split()
    tw = theirs.split()
    their_grams = {tuple(tw[i:i + COPYCHECK_WORDS]) for i in range(len(tw) - COPYCHECK_WORDS + 1)}
    for i in range(len(ow) - COPYCHECK_WORDS + 1):
        if tuple(ow[i:i + COPYCHECK_WORDS]) in their_grams:
            problems.append(f"{COPYCHECK_WORDS}어절 연속 일치: '{' '.join(ow[i:i + COPYCHECK_WORDS])}'")
            break
    oc = re.sub(r"\s+", "", ours)
    tc = re.sub(r"\s+", "", theirs)
    ours_grams = {oc[i:i + COPYCHECK_CHARS] for i in range(len(oc) - COPYCHECK_CHARS + 1)}
    if ours_grams:
        theirs_grams = {tc[i:i + COPYCHECK_CHARS] for i in range(len(tc) - COPYCHECK_CHARS + 1)}
        share = len(ours_grams & theirs_grams) / len(ours_grams)
        if share > COPYCHECK_SHARE:
            problems.append(f"{COPYCHECK_CHARS}글자 겹침 {share:.0%} > {COPYCHECK_SHARE:.0%}")
    return problems


def read_cache_file(path: pathlib.Path) -> tuple[dict | None, str]:
    """캐시 파일 첫 줄은 JSON 메타 {collected_via, url, date}"""
    text = path.read_text(encoding="utf-8")
    first, _, rest = text.partition("\n")
    try:
        meta = json.loads(first)
        if isinstance(meta, dict) and meta.get("collected_via") in COPYCHECK_VIA:
            return meta, rest
    except ValueError:
        pass
    return None, text


def copycheck(doc: dict, cc: str, cache_dir: pathlib.Path, evidence_dir: pathlib.Path, report: Report,
              record: dict | None, today: dt.date, ids: set[str] | None = None) -> dict:
    record = record or {}
    out = {}
    for att in doc.get("attractions", []):
        aid = att.get("id")
        if ids is not None and aid not in ids:
            continue
        text = copy_text(att)
        text_sha = sha256_hex(text)
        prev = record.get(aid)
        if prev and prev.get("text_sha256") == text_sha:
            out[aid] = prev
            continue
        files = sorted((cache_dir / cc / aid).glob("*.txt")) if (cache_dir / cc / aid).is_dir() else []
        if not files:
            report.error(f"{aid}: 글이 바뀌었는데 copycheck 캐시가 없음 ({cache_dir / cc / aid})")
            continue
        snaps = sorted(p for p in (evidence_dir / cc / aid).glob("*.txt")) if (evidence_dir / cc / aid).is_dir() else []
        digest = hashlib.sha256()
        failed = False
        for f in files + snaps:
            meta, body = read_cache_file(f) if f in files else ({"collected_via": "evidence"}, f.read_text(encoding="utf-8"))
            if meta is None:
                report.warn(f"{aid}: copycheck 캐시 메타 없는 파일 {f.name}")
            digest.update(f.name.encode("utf-8"))
            digest.update(body.encode("utf-8"))
            for problem in copycheck_text(text, body):
                report.error(f"{aid}: copycheck 실패({f.name}) — {problem}")
                failed = True
        if not failed:
            out[aid] = {"text_sha256": text_sha, "checked_at": today.isoformat(), "cache_set_sha256": digest.hexdigest()}
    return out


# ======================= 서명 =======================

def load_key(path: str):
    from cryptography.hazmat.primitives import serialization

    p = pathlib.Path(os.path.expanduser(path))
    pem = p.read_bytes()
    try:
        return serialization.load_pem_private_key(pem, password=None)
    except TypeError:
        password = os.environ.get("RP_ATT_KEY_PASS")
        if password is None:
            password = getpass.getpass("관광지 서명 키 암호: ")
        return serialization.load_pem_private_key(pem, password=password.encode("utf-8"))


def sign_doc(doc: dict, key, kid: str) -> tuple[bytes, bytes]:
    if not ATT_KID.match(kid):
        raise ValueError(f"kid '{kid}' 는 관광지 키(rp-att-*)가 아님")
    data = canonical_bytes(doc)
    sig = {"kid": kid, "alg": "Ed25519", "sig": base64.b64encode(key.sign(data)).decode()}
    return data, json.dumps(sig).encode("utf-8")


def trusted_attraction_keys(kt_path: pathlib.Path = PACK_KEYS_KT) -> dict[str, bytes]:
    """앱 PackKeys.ATTRACTIONS 에 내장한 공개키 (단일 출처 — 앱 소스를 읽는다)"""
    src = kt_path.read_text(encoding="utf-8")
    pairs = re.findall(r'"(rp-att-[^"]+)"\s+to\s+Base64\.getDecoder\(\)\.decode\("([^"]+)"\)', src)
    return {kid: base64.b64decode(b64) for kid, b64 in pairs}


def verify_signature(data: bytes, sig_bytes: bytes, keys: dict[str, bytes]) -> str | None:
    """문제가 있으면 이유, 맞으면 None"""
    from cryptography.exceptions import InvalidSignature
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

    try:
        sig = json.loads(sig_bytes.decode("utf-8"))
    except ValueError:
        return "서명 파일 형식 오류"
    if sig.get("alg") != "Ed25519":
        return "alg 가 Ed25519 가 아님"
    kid = sig.get("kid", "")
    if not ATT_KID.match(kid):
        return f"kid '{kid}' 가 관광지 키가 아님"
    if kid not in keys:
        return f"kid '{kid}' 공개키가 앱에 없음"
    try:
        Ed25519PublicKey.from_public_bytes(keys[kid]).verify(base64.b64decode(sig.get("sig", "")), data)
    except (InvalidSignature, ValueError):
        return "서명 불일치"
    return None


# ======================= 명령 =======================

class Paths:
    """테스트에서 바꿔 끼우는 경로 묶음"""

    def __init__(self, root: pathlib.Path = ROOT, evidence: pathlib.Path = EVIDENCE_DIR, cache: pathlib.Path = COPYCHECK_DIR):
        self.root = root
        self.drafts = root / "packs" / "drafts"
        self.src = root / "packs" / "src"
        self.curation = root / "packs" / "curation"
        self.assets = root / "app" / "src" / "main" / "assets" / "packs"
        self.evidence = evidence
        self.cache = cache

    def pack(self, cc: str) -> dict | None:
        p = self.src / cc / "pack.json"
        return load_json(p) if p.is_file() else None

    def curation_of(self, cc: str) -> dict:
        p = self.curation / f"{cc}.curation.json"
        return load_json(p) if p.is_file() else {}

    def record(self, cc: str, kind: str) -> dict:
        p = self.curation / f"{cc}.{kind}.json"
        return load_json(p) if p.is_file() else {}

    def countries(self) -> list[str]:
        found = set()
        for base in (self.drafts, self.src):
            if base.is_dir():
                found |= {p.parent.name for p in base.glob(f"*/{FILE_NAME}")}
        return sorted(found)


def advisory_basis(pack: dict) -> dict:
    sec = safety_section(pack)
    return {
        "pack_version": pack.get("version"),
        "safety_last_verified": sec.get("last_verified") if sec else None,
        "advisory_sha256": advisory_hash(pack),
    }


def check_basis_drift(doc: dict, pack: dict, report: Report, reviewed: bool):
    """§5.6 교차 검사: 해시가 바뀌었으면 경고 후 자동 갱신. pack safety 가 basis 보다 30일 넘게 새롭고 해시가 다르면 재대조 강제"""
    old = doc.get("advisory_basis") or {}
    new = advisory_basis(pack)
    if new["advisory_sha256"] is None:
        report.error("pack 에 id=='safety' 섹션이 없음")
        return
    if old and old.get("advisory_sha256") != new["advisory_sha256"]:
        report.warn("pack 경보 문단 해시가 basis 와 다름 — 서명하면서 갱신")
        a, b = parse_date(old.get("safety_last_verified")), parse_date(new["safety_last_verified"])
        if a and b and (b - a).days > 30 and not reviewed:
            report.error("pack 안전 정보가 basis 보다 30일 넘게 새롭고 경보 문단이 바뀜 — 지역·관광지 경보 단계를 다시 대조한 뒤 --advisory-reviewed")


def write_outputs(paths: Paths, cc: str, doc: dict, data: bytes, sig: bytes, quotes: dict | None, copy: dict | None):
    src = paths.src / cc / FILE_NAME
    src.parent.mkdir(parents=True, exist_ok=True)
    src.write_text(pretty_text(doc), encoding="utf-8")
    out = paths.assets / cc / FILE_NAME
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(data)
    (out.parent / (FILE_NAME + ".sig")).write_bytes(sig)
    paths.curation.mkdir(parents=True, exist_ok=True)
    if quotes is not None:
        (paths.curation / f"{cc}.quotes.json").write_text(json.dumps(quotes, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if copy is not None:
        (paths.curation / f"{cc}.copycheck.json").write_text(json.dumps(copy, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def finalize_and_sign(paths: Paths, cc: str, doc: dict, key, kid: str, today: dt.date, *, previous: dict | None,
                      reviewed: bool = False, gates: dict | None = None) -> Report:
    """서명 시점 검사(엄격) → 통과하면 원본·서명본·기록을 쓴다. 실패하면 아무것도 쓰지 않는다"""
    report = Report(cc)
    if not ATT_KID.match(kid or ""):
        report.error(f"kid '{kid}' 는 관광지 키(rp-att-*)가 아님")
        return report
    pack = paths.pack(cc)
    if pack is None:
        report.error(f"packs/src/{cc}/pack.json 이 없음")
        return report
    check_basis_drift(doc, pack, report, reviewed)
    doc["advisory_basis"] = advisory_basis(pack)
    curation = paths.curation_of(cc)
    check_doc(doc, cc, pack, mode="published", strict=True, report=report, curation=curation, today=today, gates=gates, previous=previous)
    quotes = verify_quotes(doc, cc, paths.evidence, report, paths.record(cc, "quotes"))
    copy_rec = copycheck(doc, cc, paths.cache, paths.evidence, report, paths.record(cc, "copycheck"), today)
    if not report.ok:
        return report
    data, sig = sign_doc(doc, key, kid)
    write_outputs(paths, cc, doc, data, sig, quotes, copy_rec)
    return report


def promote(paths: Paths, cc: str, wave: int, key, kid: str, today: dt.date, version: str | None = None,
            reviewed: bool = False, gates: dict | None = None) -> Report:
    """§4.7: 작업본에서 wave ≤ N 지역만 골라 wave 를 지우고 published 로 서명한다"""
    draft_path = paths.drafts / cc / FILE_NAME
    if not draft_path.is_file():
        r = Report(cc)
        r.error(f"작업본 {draft_path} 가 없음")
        return r
    draft = load_json(draft_path)
    pre = Report(cc)
    check_doc(draft, cc, paths.pack(cc), mode="draft", strict=False, report=pre, curation=paths.curation_of(cc), today=today)
    if not pre.ok:
        return pre
    src_path = paths.src / cc / FILE_NAME
    previous = load_json(src_path) if src_path.is_file() else None
    doc = copy.deepcopy(draft)
    kept, dropped = [], []
    for r in doc.get("regions", []):
        (kept if int(r.get("wave", 1)) <= wave else dropped).append(r)
    kept_ids = {r["id"] for r in kept}
    for r in kept:
        r.pop("wave", None)
    doc["regions"] = kept
    doc["attractions"] = [a for a in doc.get("attractions", []) if a.get("region") in kept_ids]
    upcoming = list(doc.get("upcoming_regions", []))
    names = {u["name_ko"] for u in upcoming}
    for r in dropped:
        if r["name_ko"] not in names:
            upcoming.append({"name_ko": r["name_ko"], "aliases_ko": list(r.get("aliases_ko", []))})
    doc["upcoming_regions"] = upcoming
    unmapped = list(doc.get("unmapped_airports", []))
    for r in dropped:
        for code in r.get("airports", []):
            unmapped.append({"code": code, "reason": "upcoming", "reason_ko": "준비 중인 지역"})
    doc["unmapped_airports"] = unmapped
    # 빠진 항목은 영구히 남긴다 — 원본에서 retire 로 뺀 항목을 이어받는다
    retired = {r["id"]: r for r in doc.get("retired", [])}
    for r in (previous or {}).get("retired", []):
        retired.setdefault(r["id"], r)
    doc["retired"] = list(retired.values())
    doc["release"] = "published"
    doc["version"] = version or next_version((previous or {}).get("version"), today)
    return finalize_and_sign(paths, cc, doc, key, kid, today, previous=previous, reviewed=reviewed, gates=gates)


def sign_existing(paths: Paths, cc: str, key, kid: str, today: dt.date, reviewed: bool = False, gates: dict | None = None) -> Report:
    src_path = paths.src / cc / FILE_NAME
    if not src_path.is_file():
        r = Report(cc)
        r.error(f"원본 {src_path} 가 없음 — 먼저 promote")
        return r
    doc = load_json(src_path)
    previous = copy.deepcopy(doc)
    doc["version"] = next_version(doc.get("version"), today)
    return finalize_and_sign(paths, cc, doc, key, kid, today, previous=previous, reviewed=reviewed, gates=gates)


RETIRE_REASON_KO = {"safety": "안전 문제로 뺀 지역"}


def retire(paths: Paths, cc: str, ids: list[str], reason: str, note_ko: str | None, key, kid: str, today: dt.date) -> Report:
    """§10.1 긴급 제외: 게이트·copycheck·확인일을 건너뛰고 무결성만 보고 서명한다"""
    report = Report(cc)
    if reason not in ("safety", "closed", "long_closure", "editorial"):
        report.error(f"retire --reason '{reason}' 는 safety|closed|long_closure|editorial")
        return report
    if not ATT_KID.match(kid or ""):
        report.error(f"kid '{kid}' 는 관광지 키(rp-att-*)가 아님")
        return report
    src_path = paths.src / cc / FILE_NAME
    if not src_path.is_file():
        report.error(f"원본 {src_path} 가 없음")
        return report
    doc = load_json(src_path)
    present = {a["id"] for a in doc.get("attractions", [])}
    missing = [i for i in ids if i not in present]
    if missing:
        report.error(f"attractions 에 없는 id: {', '.join(missing)}")
        return report
    doc["attractions"] = [a for a in doc["attractions"] if a["id"] not in ids]
    doc.setdefault("retired", [])
    for i in ids:
        doc["retired"].append({"id": i, "reason": reason, "replaced_by": None, "note_ko": note_ko, "source": "editorial", "date": today.isoformat()})
    used = {a["region"] for a in doc["attractions"]}
    removed = [r for r in doc.get("regions", []) if r["id"] not in used]
    removed_ids = {r["id"] for r in removed}
    doc["regions"] = [r for r in doc.get("regions", []) if r["id"] in used]
    for r in doc["regions"]:
        if r.get("kind") == "daytrip":
            r["base_regions"] = [b for b in r.get("base_regions", []) if b not in removed_ids]
            if not r["base_regions"]:
                r["kind"] = "base"
                r.pop("base_regions", None)
                report.warn(f"{r['id']}: 거점 지역이 빠져 base 로 바꿈")
    for r in removed:
        for code in r.get("airports", []):
            doc.setdefault("unmapped_airports", []).append(
                {"code": code, "reason": "safety_retired" if reason == "safety" else "upcoming",
                 "reason_ko": RETIRE_REASON_KO.get(reason, "준비 중인 지역")})
    bases = [r for r in doc["regions"] if r.get("kind") == "base"]
    if len(bases) <= 1:
        report.warn(f"retire 뒤 base 지역 {len(bases)}곳")
    doc["version"] = next_version(doc.get("version"), today)
    check_doc(doc, cc, paths.pack(cc), mode="retire", strict=False, report=report)
    if not report.ok:
        return report
    data, sig = sign_doc(doc, key, kid)
    write_outputs(paths, cc, doc, data, sig, None, None)
    return report


def split_ids(text: str | None) -> list[str]:
    return [i.strip() for i in (text or "").split(",") if i.strip()]


def working_doc_path(paths: Paths, cc: str) -> pathlib.Path:
    """작업본이 있으면 작업본, 없으면 원본"""
    p = paths.drafts / cc / FILE_NAME
    return p if p.is_file() else paths.src / cc / FILE_NAME


def write_record(paths: Paths, cc: str, kind: str, rec: dict):
    paths.curation.mkdir(parents=True, exist_ok=True)
    (paths.curation / f"{cc}.{kind}.json").write_text(
        json.dumps(rec, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")


def record_checks(paths: Paths, cc: str, ids: list[str], today: dt.date) -> Report:
    """ARIA 주간 갱신(사장님 결정 2026-10-09): 바뀐 곳(ids)만 인용 대조·copycheck 를 하고, 통과하면 기록
    (packs/curation/<CC>.quotes·copycheck.json)의 그 곳 줄만 갱신한다. 인용 원문·증거는 저장소 밖(~/.readyport)에만 있다.
    CI 는 이 기록(facts·글 해시)으로 '대조를 마쳤다'를 확인하고 서명한다. 하나라도 실패하면 기록을 쓰지 않는다."""
    report = Report(cc)
    path = working_doc_path(paths, cc)
    if not path.is_file():
        report.error(f"{cc} 관광지 파일이 없음")
        return report
    doc = load_json(path)
    present = {a.get("id") for a in doc.get("attractions", [])}
    missing = [i for i in ids if i not in present]
    if missing or not ids:
        report.error(f"attractions 에 없는 id: {', '.join(missing) or '(비어 있음)'}")
        return report
    want = set(ids)
    old_q, old_c = paths.record(cc, "quotes"), paths.record(cc, "copycheck")
    quotes = verify_quotes(doc, cc, paths.evidence, report, record=old_q, ids=want)
    copy_rec = copycheck(doc, cc, paths.cache, paths.evidence, report, old_c, today, ids=want)
    if not report.ok:
        return report
    new_q, new_c = dict(old_q), dict(old_c)
    new_q.update({k: v for k, v in quotes.items() if k in want})
    new_c.update({k: v for k, v in copy_rec.items() if k in want})
    write_record(paths, cc, "quotes", new_q)
    write_record(paths, cc, "copycheck", new_c)
    return report


def apply_drafts(paths: Paths, cc: str, ids: list[str], key, kid: str, today: dt.date, reviewed: bool = False,
                 gates: dict | None = None) -> Report:
    """주간 자동 갱신(CI): 작업본의 ids 관광지만 원본으로 옮기고 다시 서명한다(지역·순서·다른 곳은 원본 그대로).
    원본에 없는 곳(새 관광지)이나 지역이 바뀐 곳은 사람 절차(promote)로만 — 여기서는 실패."""
    report = Report(cc)
    src_path, draft_path = paths.src / cc / FILE_NAME, paths.drafts / cc / FILE_NAME
    if not src_path.is_file():
        report.error(f"원본 {src_path} 가 없음 — 아직 공개 전인 나라는 서명하지 않는다(작업본만 갱신)")
        return report
    if not draft_path.is_file():
        report.error(f"작업본 {draft_path} 가 없음")
        return report
    if not ids:
        report.error("--ids 가 비어 있음")
        return report
    src, draft = load_json(src_path), load_json(draft_path)
    src_index = {a.get("id"): i for i, a in enumerate(src.get("attractions", []))}
    draft_by = {a.get("id"): a for a in draft.get("attractions", [])}
    doc = copy.deepcopy(src)
    have_sources = {x.get("id") for x in doc.get("sources", [])}
    draft_sources = {x.get("id"): x for x in draft.get("sources", [])}
    for aid in ids:
        if aid not in src_index:
            report.error(f"{aid}: 원본(공개본)에 없는 곳 — 새 관광지는 promote 로만")
            continue
        if aid not in draft_by:
            report.error(f"{aid}: 작업본에 없음")
            continue
        new = copy.deepcopy(draft_by[aid])
        if new.get("region") != doc["attractions"][src_index[aid]].get("region"):
            report.error(f"{aid}: 지역이 바뀜 — 사람 절차(promote)로만")
            continue
        doc["attractions"][src_index[aid]] = new
        for p, v in walk_strings(new):
            if p.endswith(".source") and v not in have_sources and v in draft_sources:
                doc.setdefault("sources", []).append(copy.deepcopy(draft_sources[v]))
                have_sources.add(v)
    if not report.ok:
        return report
    previous = copy.deepcopy(src)
    doc["version"] = next_version(src.get("version"), today)
    return finalize_and_sign(paths, cc, doc, key, kid, today, previous=previous, reviewed=reviewed, gates=gates)


# ======================= Google place ID (사장님 결정 2026-10-09) =======================
# 구글 별점은 앱 상세에서 그때그때 조회('Google 제공' 표시, 저장 안 함). 파일에는 place ID 만 둔다 —
# Google Maps Platform 약관상 place ID 는 기간 제한 없이 저장해도 되는 값이다 [재확인].

PLACES_TEXT_SEARCH = "https://places.googleapis.com/v1/places:searchText"
PLACES_FIELD_MASK = "places.id"          # IDs Only — 이름·별점 등 다른 필드는 받지 않는다 [재확인: SKU·요금]
MAPS_PROPERTIES = KEYS_DIR / "maps.properties"
PLACE_ID_RE = re.compile(r"^[A-Za-z0-9_-]{10,300}$")
PLACE_BOX_DEG = 0.01                    # 좌표 둘레 약 1km 사각형 안에서만 찾는다(엉뚱한 곳 방지)


def read_maps_key(path: pathlib.Path = MAPS_PROPERTIES) -> str | None:
    """~/.readyport/keys/maps.properties 의 MAPS_API_KEY. 없으면 None. 키는 화면·파일 어디에도 다시 쓰지 않는다."""
    if not path.is_file():
        return None
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        if k.strip() == "MAPS_API_KEY" and v.strip():
            return v.strip()
    return None


def place_query(att: dict) -> dict:
    """Text Search 요청 본문: 현지 이름(없으면 영어·한국어 이름) + 좌표 둘레 사각형 제한"""
    names = att.get("names") or {}
    geo = att.get("geo") or {}
    lat, lng = geo["lat"], geo["lng"]
    return {
        "textQuery": names.get("local") or names.get("en") or names.get("ko"),
        "pageSize": 1,
        "locationRestriction": {"rectangle": {
            "low": {"latitude": round(lat - PLACE_BOX_DEG, 6), "longitude": round(lng - PLACE_BOX_DEG, 6)},
            "high": {"latitude": round(lat + PLACE_BOX_DEG, 6), "longitude": round(lng + PLACE_BOX_DEG, 6)},
        }},
    }


def find_place_ids(doc: dict, api_key: str, fetch, ids: set[str] | None = None,
                   overwrite: bool = False) -> tuple[dict, list[str]]:
    """({id: place_id}, 문제 목록). fetch(method, url, headers, data, timeout) -> (status, body bytes). 테스트는 가짜 fetch."""
    found, problems = {}, []
    for att in doc.get("attractions", []):
        aid = att.get("id")
        if ids is not None and aid not in ids:
            continue
        if att.get("google_place_id") and not overwrite:
            continue
        if "lat" not in (att.get("geo") or {}):
            problems.append(f"{aid}: 좌표 없음")
            continue
        body = json.dumps(place_query(att), ensure_ascii=False).encode("utf-8")
        headers = {"Content-Type": "application/json", "X-Goog-Api-Key": api_key, "X-Goog-FieldMask": PLACES_FIELD_MASK}
        status, data = fetch("POST", PLACES_TEXT_SEARCH, headers, body, 20)
        if status != 200:
            problems.append(f"{aid}: Places API HTTP {status}")
            continue
        try:
            places = json.loads((data or b"{}").decode("utf-8") or "{}").get("places") or []
        except ValueError:
            problems.append(f"{aid}: 응답 형식 오류")
            continue
        pid = (places[0] or {}).get("id") if places else None
        if not pid or not PLACE_ID_RE.match(pid):
            problems.append(f"{aid}: 후보 없음(좌표 1km 안)")
            continue
        found[aid] = pid
    return found, problems


def _urllib_fetch(method, url, headers, data, timeout):  # pragma: no cover - 실제 네트워크
    import urllib.error
    import urllib.request
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, b""


def place_ids_command(paths: Paths, cc: str, ids: set[str] | None, write: bool, key_file: pathlib.Path,
                      fetch=None, out=print) -> int:
    api_key = read_maps_key(key_file)
    if not api_key:
        out(f"MAPS_API_KEY 가 없어요: {key_file} 에 'MAPS_API_KEY=…' 한 줄을 넣어 주세요"
            "(Places API (New) 를 켠 키). 아무것도 하지 않았어요.")
        return 2
    path = working_doc_path(paths, cc)
    if not path.is_file():
        out(f"{cc} 관광지 파일이 없어요")
        return 1
    doc = load_json(path)
    found, problems = find_place_ids(doc, api_key, fetch or _urllib_fetch, ids)
    for aid, pid in sorted(found.items()):
        out(f"{aid}: {pid}")
    for pr in problems:
        out("확인 필요: " + pr)
    if write and found:
        for att in doc.get("attractions", []):
            if att.get("id") in found:
                att["google_place_id"] = found[att["id"]]
        path.write_text(pretty_text(doc), encoding="utf-8", newline="\n")
        out(f"{path.name} 에 {len(found)}곳 썼어요 — 앱 상세의 구글 별점이 맞는 곳인지 확인하세요")
    elif found:
        out("시험 실행이라 파일은 그대로예요. 쓰려면 --write")
    return 0 if not problems else 1


def check_all(paths: Paths, countries: list[str] | None, today: dt.date) -> list[Report]:
    reports = []
    for cc in countries or paths.countries():
        pack = paths.pack(cc)
        curation = paths.curation_of(cc)
        for base, mode in ((paths.drafts, "draft"), (paths.src, "published")):
            p = base / cc / FILE_NAME
            if not p.is_file():
                continue
            r = Report(f"{p.relative_to(paths.root).as_posix()}")
            try:
                doc = load_json(p)
            except ValueError as ex:
                r.error(f"JSON 형식 오류: {ex}")
                reports.append(r)
                continue
            check_doc(doc, cc, pack, mode=mode, strict=False, report=r, curation=curation, today=today)
            reports.append(r)
    samples = paths.root / "app" / "src" / "debug" / "assets" / "attractions_samples"
    for p in sorted(samples.glob("*.json")) if samples.is_dir() else []:
        r = Report(p.relative_to(paths.root).as_posix())
        doc = load_json(p)
        if not doc.get("sample"):
            r.error("debug 샘플은 sample=true")
        check_doc(doc, p.stem, paths.pack(p.stem), mode="sample", strict=False, report=r)
        reports.append(r)
    return reports


def verify_committed(paths: Paths, keys: dict[str, bytes] | None = None) -> list[Report]:
    """§10.1 CI: 커밋된 서명본의 서명·kid·doc_type·스키마·NFC + 원본 정규화 바이트 == 서명본 바이트"""
    keys = keys if keys is not None else trusted_attraction_keys()
    reports = []
    for out in sorted(paths.assets.glob(f"*/{FILE_NAME}")):
        cc = out.parent.name
        r = Report(f"assets/{cc}/{FILE_NAME}")
        data = out.read_bytes()
        sig_path = out.parent / (FILE_NAME + ".sig")
        if not sig_path.is_file():
            r.error("서명 파일 없음")
        else:
            problem = verify_signature(data, sig_path.read_bytes(), keys)
            if problem:
                r.error(problem)
        try:
            doc = json.loads(data.decode("utf-8"))
        except ValueError:
            r.error("JSON 형식 오류")
            reports.append(r)
            continue
        check_doc(doc, cc, paths.pack(cc), mode="retire", strict=False, report=r)
        src = paths.src / cc / FILE_NAME
        if not src.is_file():
            r.error(f"원본 packs/src/{cc}/{FILE_NAME} 이 없음")
        elif canonical_bytes(load_json(src)) != data:
            r.error("원본(packs/src)과 서명본 바이트가 다름 — 원본을 고쳤으면 sign 으로 다시 서명")
        today = dt.date.today()
        for a in doc.get("attractions", []):
            check_freshness(a, a.get("id"), r, False, today)
        reports.append(r)
    return reports


def keygen(kid: str, out_dir: pathlib.Path = KEYS_DIR) -> int:
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

    if not ATT_KID.match(kid):
        print(f"kid '{kid}' 는 rp-att-* 형식이어야 한다", file=sys.stderr)
        return 1
    out = pathlib.Path(out_dir) / f"attractions_signing_{kid}.pem"
    if out.exists():
        print(f"이미 있음: {out} — 덮어쓰지 않는다", file=sys.stderr)
        return 1
    if ROOT in out.resolve().parents:
        print("비밀키를 저장소 안에 둘 수 없다", file=sys.stderr)
        return 1
    key = Ed25519PrivateKey.generate()
    pem = key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(pem)
    try:
        os.chmod(out, 0o600)
    except OSError:
        pass
    pub = key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
    print(f"비밀키: {out}")
    print(f"kid: {kid}")
    print(f"공개키(base64): {base64.b64encode(pub).decode()}")
    print("→ app/src/main/java/com/readyport/pack/PackVerifier.kt 의 PackKeys.ATTRACTIONS 에 넣는다")
    return 0


def protect_key(path: str) -> int:
    from cryptography.hazmat.primitives import serialization

    p = pathlib.Path(os.path.expanduser(path))
    key = load_key(str(p))
    pw = getpass.getpass("새 암호: ")
    if not pw or pw != getpass.getpass("새 암호 다시: "):
        print("암호가 비었거나 서로 다름", file=sys.stderr)
        return 1
    pem = key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                            serialization.BestAvailableEncryption(pw.encode("utf-8")))
    p.write_bytes(pem)
    print(f"암호를 걸었다: {p}")
    return 0


def print_reports(reports: list[Report]) -> bool:
    ok = True
    for r in reports:
        for w in r.warnings:
            print("경고: " + w)
        for e in r.errors:
            print("실패: " + e, file=sys.stderr)
        if r.errors:
            ok = False
        else:
            print(f"통과: {r.label}")
    return ok


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="관광지 검증·서명 (SPEC_v5 §10)")
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("check")
    c.add_argument("countries", nargs="*")
    p = sub.add_parser("promote")
    p.add_argument("country")
    p.add_argument("--wave", type=int, required=True)
    p.add_argument("--version")
    s = sub.add_parser("sign")
    s.add_argument("countries", nargs="+")
    rt = sub.add_parser("retire")
    rt.add_argument("country")
    rt.add_argument("--ids", required=True)
    rt.add_argument("--reason", required=True)
    rt.add_argument("--note-ko")
    for sp in (p, s, rt):
        sp.add_argument("--kid", required=True)
        sp.add_argument("--key", required=True)
    for sp in (p, s):
        sp.add_argument("--advisory-reviewed", action="store_true", help="경보 문단이 크게 바뀐 뒤 지역·관광지 단계를 다시 대조했음")
    for sp in (c, p, s):
        sp.add_argument("--evidence-dir", default=str(EVIDENCE_DIR))
        sp.add_argument("--copycheck-dir", default=str(COPYCHECK_DIR))
    sub.add_parser("verify-committed")
    vq = sub.add_parser("verify-quotes")
    vq.add_argument("country")
    vq.add_argument("--evidence-dir", default=str(EVIDENCE_DIR))
    vq.add_argument("--ids", help="쉼표 목록 — 이 곳만 확인")
    vq.add_argument("--use-record", action="store_true",
                    help="packs/curation/<CC>.quotes.json 의 facts 해시가 같으면 증거 없이 통과(CI — 증거는 운영자 PC 에만 있다)")
    rc = sub.add_parser("record")
    rc.add_argument("country")
    rc.add_argument("--ids", required=True)
    rc.add_argument("--evidence-dir", default=str(EVIDENCE_DIR))
    rc.add_argument("--copycheck-dir", default=str(COPYCHECK_DIR))
    ad = sub.add_parser("apply-drafts")
    ad.add_argument("country")
    ad.add_argument("--ids", required=True)
    ad.add_argument("--kid", required=True)
    ad.add_argument("--key", required=True)
    ad.add_argument("--advisory-reviewed", action="store_true")
    pi = sub.add_parser("place-ids")
    pi.add_argument("country")
    pi.add_argument("--ids")
    pi.add_argument("--write", action="store_true")
    pi.add_argument("--key-file", default=str(MAPS_PROPERTIES))
    kg = sub.add_parser("keygen")
    kg.add_argument("--kid", required=True)
    kg.add_argument("--out-dir", default=str(KEYS_DIR))
    pk = sub.add_parser("protect-key")
    pk.add_argument("--key", required=True)
    args = ap.parse_args(argv)
    today = dt.date.today()

    if args.cmd == "keygen":
        return keygen(args.kid, pathlib.Path(args.out_dir))
    if args.cmd == "protect-key":
        return protect_key(args.key)
    paths = Paths(evidence=pathlib.Path(getattr(args, "evidence_dir", EVIDENCE_DIR)),
                  cache=pathlib.Path(getattr(args, "copycheck_dir", COPYCHECK_DIR)))
    if args.cmd == "check":
        return 0 if print_reports(check_all(paths, args.countries or None, today)) else 1
    if args.cmd == "verify-committed":
        return 0 if print_reports(verify_committed(paths)) else 1
    if args.cmd == "verify-quotes":
        cc = args.country
        r = Report(cc)
        ids = split_ids(args.ids) if args.ids else None
        verify_quotes(load_json(working_doc_path(paths, cc)), cc, paths.evidence, r,
                      record=paths.record(cc, "quotes") if args.use_record else None,
                      ids=set(ids) if ids is not None else None)
        return 0 if print_reports([r]) else 1
    if args.cmd == "record":
        return 0 if print_reports([record_checks(paths, args.country, split_ids(args.ids), today)]) else 1
    if args.cmd == "place-ids":
        ids = set(split_ids(args.ids)) if args.ids else None
        return place_ids_command(paths, args.country, ids, args.write, pathlib.Path(os.path.expanduser(args.key_file)))
    key = load_key(args.key)
    if args.cmd == "apply-drafts":
        r = apply_drafts(paths, args.country, split_ids(args.ids), key, args.kid, today, args.advisory_reviewed)
        return 0 if print_reports([r]) else 1
    if args.cmd == "promote":
        r = promote(paths, args.country, args.wave, key, args.kid, today, args.version, args.advisory_reviewed)
        return 0 if print_reports([r]) else 1
    if args.cmd == "sign":
        # 나라마다 따로 — 한 나라가 실패해도 다른 나라는 계속, 실패가 하나라도 있으면 exit 1
        reports = [sign_existing(paths, cc, key, args.kid, today, args.advisory_reviewed) for cc in args.countries]
        return 0 if print_reports(reports) else 1
    if args.cmd == "retire":
        ids = [i.strip() for i in args.ids.split(",") if i.strip()]
        r = retire(paths, args.country, ids, args.reason, args.note_ko, key, args.kid, today)
        return 0 if print_reports([r]) else 1
    return 2


if __name__ == "__main__":
    sys.exit(main())
