"""관광지 주간 자동 갱신 PR 의 '바뀐 범위' 검사 (사장님 결정 2026-10-09: 검사 통과하면 자동 반영).

ARIA(ops/aria/jobs/attractions_update.py)가 PR 을 만들기 전에, GitHub Actions(attractions-auto.yml)가 서명·머지하기 전에
같은 규칙으로 확인한다. 자동 경로가 고칠 수 있는 것은 **이미 있는 관광지의 사실 칸**뿐이다.

허용
  - 바뀐 파일: packs/drafts/<CC>/attractions.json, packs/curation/<CC>.quotes.json, packs/curation/<CC>.copycheck.json (나라 하나)
  - 관광지 칸: ALLOWED_KEYS (영업·쉬는 날·예약·상태·계절·팁·태그·가는 법·주소·근거 claims·확인일)
  - sources: 기존 항목은 그대로, 새 항목은 use=facts·https 만 덧붙이기
막음
  - 관광지 추가·삭제·순서 바꾸기, 이름·지역·종류·좌표·설명 글(summary·body)·경보 단계·google_place_id 바꾸기
  - 지역·공항·경보 기준 등 문서의 다른 칸 바꾸기

사용 (CI):
  python tools/attractions/auto_update_guard.py --base origin/main [--head HEAD]
  → 표준 출력 한 줄 JSON {"cc": "JP", "ids": [...], "published": true|false}, 문제가 있으면 exit 1
"""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import subprocess
import sys

ALLOWED_KEYS = {"facts", "status", "seasonal", "tips_ko", "tags", "access", "address_local", "claims", "risk",
                "last_verified"}
DOC_KEYS_MAY_CHANGE = {"attractions", "sources", "version"}
COUNTRIES = {"TH", "JP", "VN", "PH", "TW", "SG", "MY", "ID", "CN"}
DRAFT_RE = re.compile(r"^packs/drafts/([A-Z]{2})/attractions\.json$")
CURATION_RE = re.compile(r"^packs/curation/([A-Z]{2})\.(quotes|copycheck)\.json$")
BRANCH_RE = re.compile(r"^aria/attractions-[a-z]{2}-[0-9]{8}-[0-9a-f]{8}$")


def check_files(files: list[str]) -> tuple[str | None, list[str]]:
    """(나라, 문제). 허용된 파일만, 나라 하나만."""
    problems, ccs = [], set()
    files = [f.strip().replace("\\", "/") for f in files if f.strip()]
    if not files:
        return None, ["바뀐 파일이 없음"]
    for f in files:
        m = DRAFT_RE.match(f) or CURATION_RE.match(f)
        if not m:
            problems.append(f"자동 갱신이 바꿀 수 없는 파일: {f}")
            continue
        ccs.add(m.group(1))
    if len(ccs) > 1:
        problems.append(f"나라가 둘 이상: {sorted(ccs)}")
    cc = next(iter(ccs)) if len(ccs) == 1 else None
    if cc and cc not in COUNTRIES:
        problems.append(f"모르는 나라: {cc}")
    if cc and f"packs/drafts/{cc}/attractions.json" not in files:
        problems.append("작업본(packs/drafts)이 바뀌지 않았음")
    return cc, problems


def compare_docs(base: dict, head: dict) -> tuple[list[str], list[str]]:
    """(바뀐 관광지 id, 문제)."""
    problems = []
    for k in sorted(set(base) | set(head)):
        if k in DOC_KEYS_MAY_CHANGE:
            continue
        if base.get(k) != head.get(k):
            problems.append(f"문서 칸 '{k}' 는 자동 갱신이 바꿀 수 없음")
    # 출처: 기존 그대로 + 사실 출처(https)만 덧붙이기
    b_src = {s.get("id"): s for s in base.get("sources", [])}
    h_src = {s.get("id"): s for s in head.get("sources", [])}
    for sid, s in b_src.items():
        if h_src.get(sid) != s:
            problems.append(f"기존 출처 '{sid}' 가 바뀌거나 빠짐")
    for sid, s in h_src.items():
        if sid not in b_src and (s.get("use") != "facts" or not str(s.get("url", "")).startswith("https://")):
            problems.append(f"새 출처 '{sid}' 는 use=facts·https 만")
    b_ids = [a.get("id") for a in base.get("attractions", [])]
    h_ids = [a.get("id") for a in head.get("attractions", [])]
    if b_ids != h_ids:
        problems.append("관광지를 더하거나 빼거나 순서를 바꿀 수 없음")
        return [], problems
    changed = []
    for a, b in zip(base.get("attractions", []), head.get("attractions", [])):
        keys = {k for k in set(a) | set(b) if a.get(k) != b.get(k)}
        if not keys:
            continue
        bad = sorted(keys - ALLOWED_KEYS)
        if bad:
            problems.append(f"{a.get('id')}: 자동 갱신이 바꿀 수 없는 칸 {bad}")
        changed.append(a.get("id"))
    if not changed and not problems:
        problems.append("바뀐 관광지가 없음")
    return changed, problems


def _git(args: list[str], cwd: pathlib.Path) -> str:
    return subprocess.run(["git", *args], cwd=str(cwd), capture_output=True, text=True, encoding="utf-8",
                          check=True).stdout


def main(argv=None) -> int:  # pragma: no cover - CI 에서 git 으로 부르는 얇은 CLI (판단은 위 함수들)
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", required=True, help="기준(예: origin/main)")
    ap.add_argument("--head", default="HEAD")
    ap.add_argument("--branch", help="PR 브랜치 이름(aria/attractions-<cc>-<날짜>-<지문8> 인지 확인)")
    args = ap.parse_args(argv)
    root = pathlib.Path(__file__).resolve().parents[2]
    problems = []
    if args.branch is not None and not BRANCH_RE.match(args.branch):
        problems.append(f"브랜치 이름이 자동 갱신 형식이 아님: {args.branch}")
    files = _git(["diff", "--name-only", f"{args.base}...{args.head}"], root).splitlines()
    cc, p = check_files(files)
    problems += p
    ids: list[str] = []
    if cc:
        path = f"packs/drafts/{cc}/attractions.json"
        try:
            base = json.loads(_git(["show", f"{args.base}:{path}"], root))
        except subprocess.CalledProcessError:
            base = None
            problems.append("기준 브랜치에 작업본이 없음 — 새 나라는 사람 절차로만")
        head = json.loads(_git(["show", f"{args.head}:{path}"], root))
        if base is not None:
            ids, p = compare_docs(base, head)
            problems += p
    for pr in problems:
        print("::error::" + pr, file=sys.stderr)
    if problems:
        return 1
    published = (root / "packs" / "src" / str(cc) / "attractions.json").is_file()
    print(json.dumps({"cc": cc, "ids": ids, "published": published}, ensure_ascii=False))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
