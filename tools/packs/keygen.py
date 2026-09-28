"""팩 서명용 Ed25519 키를 만든다.

비밀키는 저장소 밖에만 둔다(기본: ~/.readyport/keys/). 출력되는 공개키(base64)를
app/src/main/java/com/readyport/pack/PackKeys.kt 의 TRUSTED 에 kid 와 함께 넣는다.
운영에서는 비밀키 PEM 내용을 GitHub Actions secret READYPORT_PACK_KEY 로만 등록한다 (ARIA에 주지 않음).

사용: python tools/packs/keygen.py --kid rp-2026-1
"""
import argparse
import base64
import os
import pathlib
import sys

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--kid", required=True, help="키 이름 (예: rp-2026-1)")
    ap.add_argument("--out-dir", default=str(pathlib.Path.home() / ".readyport" / "keys"))
    args = ap.parse_args()

    out = pathlib.Path(args.out_dir) / f"pack_signing_{args.kid}.pem"
    if out.exists():
        print(f"이미 있음: {out} — 덮어쓰지 않는다", file=sys.stderr)
        return 1
    repo = pathlib.Path(__file__).resolve().parents[2]
    if repo in out.resolve().parents:
        print("비밀키를 저장소 안에 둘 수 없다", file=sys.stderr)
        return 1

    key = Ed25519PrivateKey.generate()
    pem = key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
    )
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(pem)
    try:
        os.chmod(out, 0o600)
    except OSError:
        pass

    pub = key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
    print(f"비밀키: {out}")
    print(f"kid: {args.kid}")
    print(f"공개키(base64): {base64.b64encode(pub).decode()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
