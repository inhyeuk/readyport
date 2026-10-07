"""공지 카드 그림을 Hosting 배포본으로 줄여 복사 (docs/NOTICES_PUSH.md).

  python tools/notices/pack_images.py app/build/notice-cards/about-readyport-1.png app/build/notice-cards/about-readyport-2.png

256색 팔레트 PNG(오차 확산)로 줄여 hosting/public/notices/ 에 같은 이름으로 쓴다 — 카드뉴스(평평한 색·글자)는 눈으로 차이가 없고
크기는 약 3분의 1(무료 Hosting 전송 한도: 하루 360MB). 300KB를 넘으면 실패한다(build_notices.py 검사와 같은 한도).
필요: pip install pillow (운영자 PC에서만 — CI는 커밋한 그림을 그대로 올린다)
"""
from __future__ import annotations

import io
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "hosting" / "public" / "notices"
LIMIT = 300_000


def pack(src: pathlib.Path) -> bytes:
    from PIL import Image

    im = Image.open(src).convert("RGB")
    q = im.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
    buf = io.BytesIO()
    q.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def main(argv: list[str]) -> int:
    if not argv:
        print(__doc__)
        return 1
    OUT.mkdir(parents=True, exist_ok=True)
    for arg in argv:
        src = pathlib.Path(arg)
        data = pack(src)
        if len(data) > LIMIT:
            print(f"{src.name}: {len(data)} bytes — {LIMIT} 이하로 줄여 주세요", file=sys.stderr)
            return 1
        (OUT / src.name).write_bytes(data)
        print(f"{src.name}: {src.stat().st_size} → {len(data)} bytes → hosting/public/notices/{src.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
