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
    for key in ("requirements", "forms", "sections", "emergency", "procedures", "common_emergency"):
        for i, item in enumerate(doc.get(key, [])):
            if item.get("source") not in ids:
                errors.append(f"{label}: {key}[{i}].source '{item.get('source')}' 가 sources 에 없음")
    emb = doc.get("embassy")
    if emb and emb.get("source") not in ids:
        errors.append(f"{label}: embassy.source 가 sources 에 없음")


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
