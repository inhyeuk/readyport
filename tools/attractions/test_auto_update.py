"""관광지 주간 자동 갱신 도구 테스트: 범위 검사(auto_update_guard), record·apply-drafts·verify-quotes --use-record, place-ids.

실행: python -m unittest tools/attractions/test_auto_update.py
픽스처는 test_build_attractions 의 가짜 문서를 쓴다(이름·좌표·문장 모두 가짜). 네트워크 없음(Places 는 가짜 fetch).
"""
import copy
import json
import pathlib
import shutil
import sys
import tempfile
import unittest

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import auto_update_guard as g  # noqa: E402
import build_attractions as b  # noqa: E402
from test_build_attractions import GATES, TODAY, doc, fake_pack, has, write_cache, write_evidence  # noqa: E402


class GuardTest(unittest.TestCase):
    def test_files(self):
        ok = ["packs/drafts/JP/attractions.json", "packs/curation/JP.quotes.json", "packs/curation/JP.copycheck.json"]
        self.assertEqual(g.check_files(ok), ("JP", []))
        self.assertEqual(g.check_files(ok[:1])[1], [])
        cc, p = g.check_files(ok + ["packs/src/JP/attractions.json"])
        self.assertTrue(any("packs/src" in x for x in p))
        self.assertTrue(g.check_files(ok + ["packs/drafts/VN/attractions.json"])[1])
        self.assertTrue(g.check_files(["packs/curation/JP.quotes.json"])[1])          # 작업본 없이 기록만
        self.assertTrue(g.check_files(["app/src/main/assets/packs/JP/attractions.json"])[1])
        self.assertTrue(g.check_files([])[1])
        self.assertTrue(g.BRANCH_RE.match("aria/attractions-jp-20261012-0123abcd"))
        self.assertFalse(g.BRANCH_RE.match("claude/anything"))

    def test_fields(self):
        base = doc(release="draft")
        head = copy.deepcopy(base)
        head["attractions"][0]["facts"]["regular_closed"] = ["tue"]
        head["attractions"][2]["status"] = {"value": "temp_closed", "note_ko": "공사로 쉬어요.", "source": "official", "last_verified": "2026-10-12"}
        head["sources"].append({"id": "new_official", "name": "새 공식", "url": "https://example.org/new", "use": "facts"})
        ids, problems = g.compare_docs(base, head)
        self.assertEqual(problems, [])
        self.assertEqual(ids, ["sample-place-1", "sample-place-3"])
        for mutate, needle in (
            (lambda d: d["attractions"][0].__setitem__("summary_ko", "다른 요약이에요."), "summary_ko"),
            (lambda d: d["attractions"][0]["geo"].__setitem__("lat", 1.0), "geo"),
            (lambda d: d["attractions"][0].__setitem__("google_place_id", "ChIJxxxxxxxxxxxx"), "google_place_id"),
            (lambda d: d["attractions"].pop(), "더하거나 빼거나"),
            (lambda d: d["attractions"].reverse(), "순서"),
            (lambda d: d["regions"][0].__setitem__("name_ko", "x"), "regions"),
            (lambda d: d["sources"][1].__setitem__("url", "https://evil.example/"), "기존 출처"),
            (lambda d: d["sources"].append({"id": "x", "name": "x", "url": "https://x.example/", "use": "photo"}), "새 출처"),
        ):
            h = copy.deepcopy(base)
            mutate(h)
            _, p = g.compare_docs(base, h)
            self.assertTrue(any(needle in x for x in p), (needle, p))
        self.assertEqual(g.compare_docs(base, copy.deepcopy(base))[1], ["바뀐 관광지가 없음"])


class AutoPipelineTest(unittest.TestCase):
    """promote 로 공개한 나라 → ARIA 가 작업본 사실을 고치고 record → CI 가 증거 없이 apply-drafts 로 서명"""

    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.paths = b.Paths(root=self.tmp, evidence=self.tmp / "evidence", cache=self.tmp / "cache")
        self.ci_paths = b.Paths(root=self.tmp, evidence=self.tmp / "no-evidence-in-ci", cache=self.tmp / "no-cache-in-ci")
        self.key = Ed25519PrivateKey.generate()
        self.keys = {"rp-att-test-1": self.key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)}
        (self.paths.src / "JP").mkdir(parents=True)
        (self.paths.src / "JP" / "pack.json").write_text(json.dumps(fake_pack("JP"), ensure_ascii=False), encoding="utf-8")
        self.d = doc("JP", release="draft")
        self.save_draft()
        write_evidence(self.paths.evidence, "JP", self.d)
        write_cache(self.paths.cache, "JP", self.d)
        r = b.promote(self.paths, "JP", 1, self.key, "rp-att-test-1", TODAY, gates=GATES)
        self.assertEqual([], r.errors)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def save_draft(self):
        (self.paths.drafts / "JP").mkdir(parents=True, exist_ok=True)
        (self.paths.drafts / "JP" / "attractions.json").write_text(json.dumps(self.d, ensure_ascii=False), encoding="utf-8")

    def src(self):
        return json.loads((self.paths.src / "JP" / "attractions.json").read_text(encoding="utf-8"))

    def test_record_then_ci_signs_only_those_ids_without_evidence(self):
        before = self.src()
        self.d["attractions"][0]["facts"]["regular_closed"] = ["tue"]
        self.d["attractions"][1]["facts"]["regular_closed"] = ["wed"]     # 다른 곳도 작업본에서 바뀌었지만 ids 에 없다
        self.save_draft()
        write_evidence(self.paths.evidence, "JP", self.d)                 # 새 사실 인용(ARIA 쪽 증거)
        # CI 는 증거가 없다: 기록 없이 서명하면 실패
        r = b.apply_drafts(self.ci_paths, "JP", ["sample-place-1"], self.key, "rp-att-test-1", TODAY, gates=GATES)
        self.assertTrue(has(r, "인용 대조 기록 없음"))
        # ARIA: record (바뀐 곳만)
        r = b.record_checks(self.paths, "JP", ["sample-place-1"], TODAY)
        self.assertEqual([], r.errors)
        q = b.Report("JP")
        b.verify_quotes(self.d, "JP", self.ci_paths.evidence, q, record=self.ci_paths.record("JP", "quotes"), ids={"sample-place-1"})
        self.assertEqual([], q.errors)
        # CI: 증거 없이 기록으로 서명
        r = b.apply_drafts(self.ci_paths, "JP", ["sample-place-1"], self.key, "rp-att-test-1", TODAY, gates=GATES)
        self.assertEqual([], r.errors)
        after = self.src()
        self.assertEqual(after["attractions"][0]["facts"]["regular_closed"], ["tue"])
        self.assertEqual(after["attractions"][1], before["attractions"][1])
        self.assertEqual(after["version"], "2026.10.09-2")
        data = (self.paths.assets / "JP" / "attractions.json").read_bytes()
        self.assertIsNone(b.verify_signature(data, (self.paths.assets / "JP" / "attractions.json.sig").read_bytes(), self.keys))
        self.assertEqual([], [e for rep in b.verify_committed(self.paths, self.keys) for e in rep.errors])

    def test_record_refuses_bad_quote_and_writes_nothing(self):
        rec_before = (self.paths.curation / "JP.quotes.json").read_text(encoding="utf-8")
        self.d["attractions"][0]["facts"]["regular_closed"] = ["tue"]
        self.save_draft()
        write_evidence(self.paths.evidence, "JP", self.d, bad="sample-place-1")
        r = b.record_checks(self.paths, "JP", ["sample-place-1"], TODAY)
        self.assertTrue(has(r, "글자 그대로 없음"))
        self.assertEqual(rec_before, (self.paths.curation / "JP.quotes.json").read_text(encoding="utf-8"))
        self.assertTrue(has(b.record_checks(self.paths, "JP", ["no-such"], TODAY), "없는 id"))

    def test_apply_drafts_refuses_new_place_region_move_and_unpublished(self):
        self.d["attractions"][0]["region"] = "jp_two"
        self.save_draft()
        self.assertTrue(has(b.apply_drafts(self.paths, "JP", ["sample-place-1"], self.key, "rp-att-test-1", TODAY), "지역이 바뀜"))
        self.assertTrue(has(b.apply_drafts(self.paths, "JP", ["brand-new"], self.key, "rp-att-test-1", TODAY), "promote"))
        self.assertTrue(has(b.apply_drafts(self.paths, "VN", ["x"], self.key, "rp-att-test-1", TODAY), "공개 전"))
        self.assertTrue(has(b.apply_drafts(self.paths, "JP", [], self.key, "rp-att-test-1", TODAY), "비어"))


class PlaceIdsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.paths = b.Paths(root=self.tmp, evidence=self.tmp / "e", cache=self.tmp / "c")
        (self.paths.drafts / "JP").mkdir(parents=True)
        self.d = doc("JP", release="draft")
        (self.paths.drafts / "JP" / "attractions.json").write_text(json.dumps(self.d, ensure_ascii=False), encoding="utf-8")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_no_key_clear_message(self):
        out = []
        rc = b.place_ids_command(self.paths, "JP", None, True, self.tmp / "missing.properties", fetch=lambda *a: 1 / 0, out=out.append)
        self.assertEqual(rc, 2)
        self.assertIn("MAPS_API_KEY", out[0])

    def test_ids_only_field_mask_and_write(self):
        keyfile = self.tmp / "maps.properties"
        keyfile.write_text("# 가짜\nMAPS_API_KEY=test-key-not-real\n", encoding="utf-8")
        calls = []

        def fetch(method, url, headers, data, timeout):
            calls.append((method, url, headers, json.loads(data)))
            if json.loads(data)["textQuery"] == "Sample Place 2":
                return 200, b'{}'                                         # 후보 없음
            return 200, json.dumps({"places": [{"id": f"ChIJfakePlaceId{len(calls):04d}"}]}).encode()
        out = []
        rc = b.place_ids_command(self.paths, "JP", {"sample-place-1", "sample-place-2"}, True, keyfile, fetch=fetch, out=out.append)
        self.assertEqual(rc, 1)                                              # 한 곳은 후보 없음 → 확인 필요
        method, url, headers, body = calls[0]
        self.assertEqual(url, b.PLACES_TEXT_SEARCH)
        self.assertEqual(headers["X-Goog-FieldMask"], "places.id")
        self.assertEqual(headers["X-Goog-Api-Key"], "test-key-not-real")
        self.assertEqual(body["pageSize"], 1)
        self.assertIn("rectangle", body["locationRestriction"])
        self.assertFalse(any("test-key-not-real" in line for line in out))   # 키를 화면에 찍지 않는다
        saved = json.loads((self.paths.drafts / "JP" / "attractions.json").read_text(encoding="utf-8"))
        got = {a["id"]: a.get("google_place_id") for a in saved["attractions"]}
        self.assertEqual(got["sample-place-1"], "ChIJfakePlaceId0001")
        self.assertIsNone(got["sample-place-2"])
        # 스키마가 받는다
        r = b.Report("JP")
        b.check_doc(saved, "JP", None, mode="draft", strict=False, report=r, gates=GATES, today=TODAY)
        self.assertFalse(any("google_place_id" in e for e in r.errors), r.errors)
        bad = copy.deepcopy(saved)
        bad["attractions"][0]["google_place_id"] = "x y"
        self.assertTrue(b.schema_errors(bad))
        # 이미 있으면 다시 찾지 않는다
        calls.clear()
        b.find_place_ids(saved, "k", fetch, {"sample-place-1"})
        self.assertEqual(calls, [])


if __name__ == "__main__":
    unittest.main()
