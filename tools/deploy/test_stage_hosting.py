"""stage_hosting 테스트: 커밋된 관광지 서명본 복사·검증, --check-staged 누락 감지.

실행: python -m unittest tools/deploy/test_stage_hosting.py
"""
import base64
import json
import pathlib
import shutil
import sys
import tempfile
import unittest

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import stage_hosting as sh  # noqa: E402


class StageHostingTest(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.assets = self.tmp / "assets"
        self.hosting = self.tmp / "hosting"
        key = Ed25519PrivateKey.generate()
        self.keys = {"rp-att-test-1": key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)}
        data = json.dumps({"doc_type": "attractions", "country": "JP"}, separators=(",", ":")).encode()
        (self.assets / "JP").mkdir(parents=True)
        (self.assets / "JP" / "attractions.json").write_bytes(data)
        sig = {"kid": "rp-att-test-1", "alg": "Ed25519", "sig": base64.b64encode(key.sign(data)).decode()}
        (self.assets / "JP" / "attractions.json.sig").write_text(json.dumps(sig), encoding="utf-8")
        # 국가 팩은 build_packs 가 이미 올려 둔 상태 — 지우지 않는다
        (self.hosting / "JP").mkdir(parents=True)
        (self.hosting / "JP" / "pack.json").write_text("{}", encoding="utf-8")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_missing_staged_fails(self):
        self.assertTrue(sh.check_staged(self.assets, self.hosting))

    def test_stage_then_check(self):
        self.assertEqual([], sh.stage(self.assets, self.hosting, self.keys))
        self.assertEqual([], sh.check_staged(self.assets, self.hosting))
        self.assertTrue((self.hosting / "JP" / "pack.json").is_file())

    def test_bad_signature_reported(self):
        self.assertTrue(sh.stage(self.assets, self.hosting, {"rp-att-test-1": b"\x01" * 32}))

    def test_changed_staged_bytes_fail(self):
        sh.stage(self.assets, self.hosting, self.keys)
        (self.hosting / "JP" / "attractions.json").write_text("{}", encoding="utf-8")
        self.assertTrue(sh.check_staged(self.assets, self.hosting))


if __name__ == "__main__":
    unittest.main()
