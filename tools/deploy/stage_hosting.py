"""커밋된 관광지 서명본을 Hosting 배포 폴더로 옮긴다 (SPEC_v5 §10.1).

hosting/public/packs/ 는 커밋하지 않고 build_packs.py 가 배포 직전에 만든다. 관광지 서명본은 운영자 PC에서만 서명하므로
(키가 CI에 없다) 커밋된 app/src/main/assets/packs/<CC>/attractions.json(+.sig)을 그대로 복사하고, 복사본의 서명·kid·doc_type 을 다시 확인한다.

사용:
  python tools/deploy/stage_hosting.py                 # 복사 + 검증 (firebase deploy --only hosting 직전에)
  python tools/deploy/stage_hosting.py --check-staged  # 복사돼 있는지만 확인(빠졌거나 바이트가 다르면 exit 1)

참고: 국가 팩 서명(build_packs.py)은 이 스크립트가 하지 않는다 — 배포 순서는 build_packs.py → stage_hosting.py → firebase deploy.
"""
import argparse
import io
import json
import pathlib
import shutil
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "attractions"))
import build_attractions as ba  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "packs"
HOSTING = ROOT / "hosting" / "public" / "packs"
NAME = "attractions.json"


def committed(assets: pathlib.Path) -> list[pathlib.Path]:
    return sorted(assets.glob(f"*/{NAME}"))


def stage(assets: pathlib.Path = ASSETS, hosting: pathlib.Path = HOSTING, keys: dict | None = None) -> list[str]:
    keys = keys if keys is not None else ba.trusted_attraction_keys()
    problems = []
    for src in committed(assets):
        cc = src.parent.name
        dest = hosting / cc
        dest.mkdir(parents=True, exist_ok=True)
        for name in (NAME, NAME + ".sig"):
            shutil.copyfile(src.parent / name, dest / name)
        data = (dest / NAME).read_bytes()
        problem = ba.verify_signature(data, (dest / (NAME + ".sig")).read_bytes(), keys)
        if problem:
            problems.append(f"{cc}: {problem}")
        elif json.loads(data.decode("utf-8")).get("doc_type") != "attractions":
            problems.append(f"{cc}: doc_type 이 attractions 가 아님")
        else:
            print(f"올림 준비: packs/{cc}/{NAME} ({len(data)} bytes)")
    return problems


def check_staged(assets: pathlib.Path = ASSETS, hosting: pathlib.Path = HOSTING) -> list[str]:
    problems = []
    for src in committed(assets):
        cc = src.parent.name
        for name in (NAME, NAME + ".sig"):
            staged = hosting / cc / name
            if not staged.is_file():
                problems.append(f"{cc}/{name} 가 hosting 에 없음 — stage_hosting.py 를 먼저 실행")
            elif staged.read_bytes() != (src.parent / name).read_bytes():
                problems.append(f"{cc}/{name} 가 커밋된 서명본과 다름")
    return problems


def main(argv=None) -> int:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--check-staged", action="store_true")
    args = ap.parse_args(argv)
    problems = check_staged() if args.check_staged else stage()
    for p in problems:
        print("실패: " + p, file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
