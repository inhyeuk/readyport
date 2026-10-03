"""공항 도착 순서 조사 파일(<CC>.json)을 나라 팩 원본(packs/src/<CC>/pack.json)에 합친다.

조사 파일 모양 (docs/design/AIRPORT_GUIDE_REPORT.md):
  {"country": "TH", "airports": [...], "sources": [{"id", "name", "url"}, ...]}

하는 일
  1. airports 를 팩의 airports 로 통째로 바꾼다(다시 돌려도 같은 결과 — 공항을 빼면 팩에서도 빠진다).
  2. sources 는 id 로 겹침을 없애 더한다. 같은 id 인데 url 이 다르면 멈춘다(다른 출처를 같은 이름표로 덮지 않는다).
     같은 id·같은 url 이면 팩에 있던 이름을 그대로 둔다.
  3. 검증: pack.schema.json + build_packs.check_sources(공항·단계·입국 카드 줄 출처, kind, https, 날짜) + 미확정 표시.
     하나라도 틀리면 그 나라 팩은 쓰지 않는다.
  4. 바뀐 것이 있으면 팩 version 을 올린다(오늘 날짜면 -N 을 하나 올리고, 아니면 오늘-1).
서명·내장본 갱신은 하지 않는다 — 끝나면 build_packs.py 를 돌린다(아래 안내가 출력된다).

사용:
  python tools/packs/merge_airports.py --dir <scratchpad>/airports            # 폴더 안 모든 <CC>.json
  python tools/packs/merge_airports.py --dir <scratchpad>/airports JP SG     # 고른 나라만
  python tools/packs/merge_airports.py --dir <scratchpad>/airports --check   # 쓰지 않고 검증만
"""
import argparse
import copy
import datetime
import io
import json
import pathlib
import sys

import jsonschema

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build_packs  # noqa: E402

if not isinstance(sys.stdout, io.TextIOWrapper) or sys.stdout.encoding.lower() != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "packs" / "src"
SCHEMA = ROOT / "packs" / "schema" / "pack.schema.json"


class MergeError(Exception):
    pass


def bump_version(version: str, today: datetime.date) -> str:
    """yyyy.MM.dd-N → 오늘 날짜면 N+1, 아니면 오늘-1 (더 낮아지지 않게: 옛 버전 날짜가 오늘보다 뒤면 그 날짜에서 N+1)"""
    stamp = today.strftime("%Y.%m.%d")
    date, _, n = version.partition("-")
    if date >= stamp:
        return f"{date}-{int(n or 0) + 1}"
    return f"{stamp}-1"


def merge_sources(pack_sources: list, new_sources: list, label: str) -> list:
    by_id = {s["id"]: s for s in pack_sources}
    out = list(pack_sources)
    for s in new_sources:
        for key in ("id", "name", "url"):
            if not s.get(key):
                raise MergeError(f"{label}: sources 항목에 {key} 가 없음 — {s}")
        old = by_id.get(s["id"])
        if old is None:
            out.append({"id": s["id"], "name": s["name"], "url": s["url"]})
            by_id[s["id"]] = s
        elif old["url"].rstrip("/") != s["url"].rstrip("/"):
            raise MergeError(f"{label}: 출처 id '{s['id']}' 가 팩에 다른 주소로 있음 ({old['url']} ≠ {s['url']}) — 다른 id 를 쓴다")
    return out


def with_airports(pack: dict, airports: list) -> dict:
    """airports 를 checklist 앞(없으면 끝)에 둔 새 팩 — 원본 키 순서를 지킨다"""
    out = {}
    placed = False
    for k, v in pack.items():
        if k == "airports":
            continue
        if k == "checklist" and not placed:
            out["airports"] = airports
            placed = True
        out[k] = v
    if not placed:
        out["airports"] = airports
    return out


def validate(pack: dict, label: str) -> list:
    errors = []
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    for e in jsonschema.Draft202012Validator(schema).iter_errors(pack):
        errors.append(f"{label}: {'/'.join(map(str, e.path))}: {e.message}")
    build_packs.check_sources(pack, label, errors)
    for p, s in build_packs.walk_strings(pack.get("airports", [])):
        for mark in build_packs.UNSETTLED:
            if mark in s:
                errors.append(f"{label}: airports{p}: 미확정 표시 '{mark}'")
    return errors


def merge_one(data: dict, pack: dict, today: datetime.date) -> tuple[dict, bool]:
    cc = pack["country"]
    label = f"{cc}/pack.json"
    if data.get("country") != cc:
        raise MergeError(f"{label}: 조사 파일 country '{data.get('country')}' 가 팩 나라와 다름")
    airports = data.get("airports")
    if not isinstance(airports, list) or not airports:
        raise MergeError(f"{label}: 조사 파일에 airports 가 없음")
    merged = with_airports(copy.deepcopy(pack), copy.deepcopy(airports))
    merged["sources"] = merge_sources(pack["sources"], data.get("sources", []), label)
    changed = merged.get("airports") != pack.get("airports") or merged["sources"] != pack["sources"]
    if changed:
        merged["version"] = bump_version(pack["version"], today)
    errors = validate(merged, label)
    if errors:
        raise MergeError("검증 실패:\n  - " + "\n  - ".join(errors))
    return merged, changed


def write_json(path: pathlib.Path, doc: dict):
    path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="공항 도착 순서 조사 파일을 나라 팩에 합친다")
    ap.add_argument("countries", nargs="*", help="합칠 나라 코드(없으면 --dir 안 모든 <CC>.json)")
    ap.add_argument("--dir", required=True, help="<CC>.json 이 있는 폴더")
    ap.add_argument("--check", action="store_true", help="쓰지 않고 검증만")
    ap.add_argument("--today", help="버전 날짜(YYYY-MM-DD, 테스트용). 기본은 오늘")
    args = ap.parse_args(argv)

    folder = pathlib.Path(args.dir)
    today = datetime.date.fromisoformat(args.today) if args.today else datetime.date.today()
    files = [folder / f"{cc.upper()}.json" for cc in args.countries] if args.countries else sorted(folder.glob("[A-Z][A-Z].json"))
    if not files:
        print(f"{folder}: 합칠 <CC>.json 이 없음")
        return 1
    failed = 0
    written = []
    for f in files:
        cc = f.stem.upper()
        target = SRC / cc / "pack.json"
        if not f.exists():
            print(f"{cc}: 조사 파일 없음 ({f})")
            failed += 1
            continue
        if not target.exists():
            print(f"{cc}: 팩 원본 없음 ({target})")
            failed += 1
            continue
        try:
            data = json.loads(f.read_text(encoding="utf-8"))
            pack = json.loads(target.read_text(encoding="utf-8"))
            merged, changed = merge_one(data, pack, today)
        except (MergeError, json.JSONDecodeError) as e:
            print(f"{cc}: {e}")
            failed += 1
            continue
        codes = ", ".join(a["code"] for a in merged["airports"])
        if not changed:
            print(f"{cc}: 바뀐 것 없음 ({codes}, version {merged['version']})")
            continue
        if args.check:
            print(f"{cc}: 검증 통과 ({codes}) — --check 라 쓰지 않음 (쓰면 version {pack['version']} → {merged['version']})")
            continue
        write_json(target, merged)
        written.append(cc)
        print(f"{cc}: 합침 ({codes}) version {pack['version']} → {merged['version']}")
    if written:
        print("다음: python tools/packs/build_packs.py --kid rp-2026-1 --key ~/.readyport/keys/pack_signing_rp-2026-1.pem")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
