"""국가 팩 검증 → 서명 → 배포 폴더로 복사.

입력: packs/src/index.json, packs/src/<CC>/pack.json, packs/src/recipes/<FORM_ID>.json (사람이 읽는 원본, 서명 없음)
출력: 같은 구조로 두 곳에 쓴다
  - app/src/main/assets/packs/   : 설치 파일에 내장하는 기본 팩 (서버가 막혀도 앱이 동작, ARCHITECTURE 9.2)
  - hosting/public/packs/         : Firebase Hosting 배포본
각 파일 옆에 <파일>.sig = {"kid", "alg": "Ed25519", "sig": base64} (파일 바이트 전체에 대한 서명)

검사: JSON Schema, source 가 sources[].id 에 있는지, "[확인 필요]"·"[재확인]" 같은 미확정 표시가 남았는지.
비밀키: 환경변수 READYPORT_PACK_KEY(PEM 내용) 또는 --key 파일. kid: READYPORT_PACK_KID 또는 --kid.

사용:
  python tools/packs/build_packs.py --check                    # 검증만 (키 불필요, CI용)
  python tools/packs/build_packs.py --kid rp-2026-1 --key ~/.readyport/keys/pack_signing_rp-2026-1.pem
"""
import argparse
import io
import base64
import json
import os
import pathlib
import sys

import jsonschema

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8")

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "packs" / "src"
SCHEMA = ROOT / "packs" / "schema"
OUTPUTS = [ROOT / "app" / "src" / "main" / "assets" / "packs", ROOT / "hosting" / "public" / "packs"]
UNSETTLED = ["[확인 필요]", "[재확인]", "TODO"]


def load(path: pathlib.Path):
    return json.loads(path.read_text(encoding="utf-8"))


def walk_strings(obj, path=""):
    if isinstance(obj, str):
        yield path, obj
    elif isinstance(obj, dict):
        for k, v in obj.items():
            yield from walk_strings(v, f"{path}.{k}")
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            yield from walk_strings(v, f"{path}[{i}]")


def check_sources(doc, label, errors):
    ids = {s["id"] for s in doc.get("sources", [])}
    for key in ("requirements", "forms", "sections", "emergency", "procedures", "common_emergency", "transport_apps"):
        for i, item in enumerate(doc.get(key, [])):
            if item.get("source") not in ids:
                errors.append(f"{label}: {key}[{i}].source '{item.get('source')}' 가 sources 에 없음")
    emb = doc.get("embassy")
    if emb and emb.get("source") not in ids:
        errors.append(f"{label}: embassy.source 가 sources 에 없음")
    for key in ("power", "home_power"):
        if doc.get(key) and doc[key].get("source") not in ids:
            errors.append(f"{label}: {key}.source 가 sources 에 없음")
    for i, item in enumerate(doc.get("shopping", [])):
        for k in ("source", "import_source"):
            if item.get(k) not in ids:
                errors.append(f"{label}: shopping[{i}].{k} '{item.get(k)}' 가 sources 에 없음")
    for i, fact in enumerate(doc.get("return_facts", [])):
        if fact.get("source") not in ids:
            errors.append(f"{label}: return_facts[{i}].source 가 sources 에 없음")
    for i, rule in enumerate(doc.get("essentials", [])):
        if rule.get("source") and rule["source"] not in ids:
            errors.append(f"{label}: essentials[{i}].source 가 sources 에 없음")
        link = rule.get("link") or {}
        # 보험·환전·카드 같은 금융 상품에는 제휴 링크를 넣지 않는다 (작업 규칙 11)
        if link.get("type") == "affiliate" and any(w in rule.get("name_ko", "") for w in ("보험", "환전", "카드", "금융")):
            errors.append(f"{label}: essentials[{i}] 금융 상품에 제휴 링크 금지")
    check_passport_validity(doc, label, ids, errors)
    check_checklist(doc, label, errors)
    check_airports(doc, label, ids, errors)


AIRPORT_STEP_KINDS = ("deplane", "health", "immigration", "egate", "form_check", "baggage", "customs", "transfer", "exit")


def check_airports(doc, label, ids, errors):
    """airports[]: 출처가 sources 에 있는지(공항·단계·입국 카드 줄), 코드가 겹치지 않는지, 단계 kind, https 링크, 확인 날짜 (작업 규칙 6).
    JSON Schema 가 모양을 보고, 여기서는 스키마로 못 보는 것(출처 연결·겹침·실제 날짜)을 본다. 스키마 없이도(merge_airports.py) 같은 검사를 한다."""
    import datetime

    seen = set()
    for i, ap in enumerate(doc.get("airports", [])):
        where = f"{label}: airports[{i}]"
        code = ap.get("code")
        if code in seen:
            errors.append(f"{where}.code '{code}' 가 겹침")
        seen.add(code)
        for key in ("code", "name_ko", "name_en", "city_ko", "steps", "map_url", "source", "last_verified"):
            if not ap.get(key):
                errors.append(f"{where}.{key} 가 비어 있음")
        if "egate_kr" not in ap:
            errors.append(f"{where}.egate_kr 가 없음 (모르면 null)")
        elif ap["egate_kr"] is not None and not isinstance(ap["egate_kr"], bool):
            errors.append(f"{where}.egate_kr 는 true/false/null")
        if ap.get("source") not in ids:
            errors.append(f"{where}.source '{ap.get('source')}' 가 sources 에 없음")
        if "form_check_source" in ap and ap["form_check_source"] not in ids:
            errors.append(f"{where}.form_check_source '{ap['form_check_source']}' 가 sources 에 없음")
        if not str(ap.get("map_url", "")).startswith("https://"):
            errors.append(f"{where}.map_url 은 https:// 로 시작")
        try:
            datetime.date.fromisoformat(str(ap.get("last_verified")))
        except ValueError:
            errors.append(f"{where}.last_verified '{ap.get('last_verified')}' 는 YYYY-MM-DD 날짜")
        steps = ap.get("steps") or []
        if not 3 <= len(steps) <= 7:
            errors.append(f"{where}.steps 는 3~7개 (지금 {len(steps)}개)")
        for j, st in enumerate(steps):
            if st.get("kind") not in AIRPORT_STEP_KINDS:
                errors.append(f"{where}.steps[{j}].kind '{st.get('kind')}' 는 {'/'.join(AIRPORT_STEP_KINDS)} 중 하나")
            for key in ("title_ko", "body_ko"):
                if not st.get(key):
                    errors.append(f"{where}.steps[{j}].{key} 가 비어 있음")
            if "source" in st and st["source"] not in ids:
                errors.append(f"{where}.steps[{j}].source '{st['source']}' 가 sources 에 없음")


def check_optional_forms(docs, errors, recipes=None):
    """forms[].optional(의무가 아닌 권장 신고 — 베트남 PAI)에는 자동 입력 레시피를 두지 않는다.

    앱이 '칸을 채워 드려요'라고 말하면 꼭 내야 하는 서류처럼 읽힌다 (작업 규칙 6·7).
    기한(window_days_including_arrival)도 두지 않는다 — 오늘 단계·알림이 급한 할 일로 만들지 않게.
    [recipes]: 있는 레시피 form_id 집합 (없으면 packs/src/recipes 폴더를 본다 — 테스트에서 넣어 쓴다)
    """
    if recipes is None:
        recipes = {p.stem for p in (SRC / "recipes").glob("*.json")}
    for label, doc in docs.items():
        for i, form in enumerate(doc.get("forms", [])):
            if not form.get("optional"):
                continue
            if form["id"] in recipes:
                errors.append(f"{label}: forms[{i}] '{form['id']}' 는 optional 인데 레시피가 있음 — 레시피를 두지 않는다")
            if form.get("window_days_including_arrival") is not None:
                errors.append(f"{label}: forms[{i}] '{form['id']}' 는 optional 인데 기간 일수가 있음 — null 로 둔다")


def check_passport_validity(doc, label, ids, errors):
    """requirements[].passport_validity: 값이 있으면 출처가 sources 에 있어야 하고, 근거 없는 숫자를 막는다 (작업 규칙 6)."""
    for i, req in enumerate(doc.get("requirements", [])):
        pv = req.get("passport_validity")
        if pv is None:
            continue
        if pv.get("source") not in ids:
            errors.append(f"{label}: requirements[{i}].passport_validity.source '{pv.get('source')}' 가 sources 에 없음")
        if pv.get("basis") not in ("arrival", "departure", "stay_end"):
            errors.append(f"{label}: requirements[{i}].passport_validity.basis '{pv.get('basis')}' 는 arrival/departure/stay_end 중 하나")
        months = pv.get("months")
        if not isinstance(months, int) or isinstance(months, bool) or not 1 <= months <= 24:
            errors.append(f"{label}: requirements[{i}].passport_validity.months 는 1~24 정수")


def check_checklist(doc, label, errors):
    """체크리스트: 나라 팩 항목 문장은 같은 팩 섹션 문장 그대로, 색인 틀의 essential:<id> 는 essentials 에 있어야 한다."""
    sections = {s["id"]: s for s in doc.get("sections", [])}
    seen = set()
    for i, item in enumerate(doc.get("checklist", [])):
        if item.get("id") in seen:
            errors.append(f"{label}: checklist[{i}].id '{item.get('id')}' 가 겹침")
        seen.add(item.get("id"))
        if "section" in item:
            sec = sections.get(item["section"])
            if sec is None:
                errors.append(f"{label}: checklist[{i}].section '{item['section']}' 가 sections 에 없음")
            elif item.get("text_ko") not in sec.get("body_ko", []):
                errors.append(f"{label}: checklist[{i}].text_ko 가 sections['{item['section']}'] 문장과 다름 — 팩 문장을 그대로 쓴다")
        frm = item.get("from") or ""
        if frm.startswith("essential:"):
            if frm.split(":", 1)[1] not in {e["id"] for e in doc.get("essentials", [])}:
                errors.append(f"{label}: checklist[{i}].from '{frm}' 가 essentials 에 없음")
        if item.get("kind") in ("generic", "auto", "pack") and not item.get("title_ko") and "section" not in item:
            errors.append(f"{label}: checklist[{i}] title_ko 없음")


def validate_all():
    errors = []
    index_schema = load(SCHEMA / "index.schema.json")
    pack_schema = load(SCHEMA / "pack.schema.json")
    docs = {}

    index = load(SRC / "index.json")
    for e in jsonschema.Draft202012Validator(index_schema).iter_errors(index):
        errors.append(f"index.json: {'/'.join(map(str, e.path))}: {e.message}")
    check_sources(index, "index.json", errors)
    docs["index.json"] = index

    for entry in index.get("countries", []):
        cc = entry["code"]
        path = SRC / cc / "pack.json"
        if not entry["pack"]:
            continue
        if not path.exists():
            errors.append(f"{cc}: index 에 pack=true 인데 {path} 없음")
            continue
        pack = load(path)
        for e in jsonschema.Draft202012Validator(pack_schema).iter_errors(pack):
            errors.append(f"{cc}/pack.json: {'/'.join(map(str, e.path))}: {e.message}")
        if pack.get("country") != cc:
            errors.append(f"{cc}/pack.json: country 가 폴더 이름과 다름")
        check_sources(pack, f"{cc}/pack.json", errors)
        docs[f"{cc}/pack.json"] = pack

    for name, doc in docs.items():
        for p, s in walk_strings(doc):
            for mark in UNSETTLED:
                if mark in s:
                    errors.append(f"{name}{p}: 미확정 표시 '{mark}' — 확인 후 지우고 게시한다 (작업 규칙 6)")
    check_optional_forms(docs, errors)

    recipe_schema = load(SCHEMA / "recipe.schema.json")
    for path in sorted((SRC / "recipes").glob("*.json")):
        recipe = load(path)
        label = f"recipes/{path.name}"
        for e in jsonschema.Draft202012Validator(recipe_schema).iter_errors(recipe):
            errors.append(f"{label}: {'/'.join(map(str, e.path))}: {e.message}")
        if recipe.get("form_id") != path.stem:
            errors.append(f"{label}: form_id 가 파일 이름과 다름")
        if recipe.get("source") not in {s["id"] for s in recipe.get("sources", [])}:
            errors.append(f"{label}: source 가 sources 에 없음")
        opts = recipe.get("options", {})
        for step in recipe.get("steps", []):
            for f in step.get("fields", []):
                if f.get("widget") == "text" and not f.get("selector"):
                    errors.append(f"{label}: {f['key']} text 칸인데 selector 없음")
                if f.get("options_ref") and f["options_ref"] not in opts:
                    errors.append(f"{label}: {f['key']} options_ref '{f['options_ref']}' 없음")
        if not recipe.get("labels_reviewed"):
            print(f"경고: {label} 현지어 라벨 원어민 검수 전")
        docs[label] = recipe

    for name, doc in docs.items():
        unreviewed = [ph["id"] for ph in doc.get("phrases", []) if not ph.get("reviewed")]
        if unreviewed:
            # 원어민 검수 전 문장은 앱에 '검수 전'으로 표시된다. 출시 전 검수 필수 (ROADMAP 14장)
            print(f"경고: {name} 원어민 검수 전 문장 {len(unreviewed)}개: {', '.join(unreviewed)}")
    return docs, errors


def load_key(args):
    from cryptography.hazmat.primitives import serialization

    pem = os.environ.get("READYPORT_PACK_KEY")
    if not pem and args.key:
        pem = pathlib.Path(os.path.expanduser(args.key)).read_text()
    if not pem:
        sys.exit("비밀키가 없다: READYPORT_PACK_KEY 또는 --key")
    kid = args.kid or os.environ.get("READYPORT_PACK_KID")
    if not kid:
        sys.exit("kid 가 없다: READYPORT_PACK_KID 또는 --kid")
    return serialization.load_pem_private_key(pem.encode(), password=None), kid


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="검증만 하고 서명·복사하지 않는다")
    ap.add_argument("--key")
    ap.add_argument("--kid")
    args = ap.parse_args()

    docs, errors = validate_all()
    if errors:
        print("검증 실패:", file=sys.stderr)
        for e in errors:
            print("  - " + e, file=sys.stderr)
        return 1
    print(f"검증 통과: {', '.join(docs)}")
    if args.check:
        return 0

    key, kid = load_key(args)
    for name, doc in docs.items():
        # 공백 없는 UTF-8. 서명은 이 바이트 그대로에 대해 한다
        data = json.dumps(doc, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        sig = {"kid": kid, "alg": "Ed25519", "sig": base64.b64encode(key.sign(data)).decode()}
        for out_root in OUTPUTS:
            out = out_root / name
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(data)
            (out.parent / (out.name + ".sig")).write_text(json.dumps(sig), encoding="utf-8")
        print(f"서명: {name} ({len(data)} bytes, kid={kid})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
