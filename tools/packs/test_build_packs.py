"""팩 검증 테스트 — 여권 남은 기간(passport_validity)·체크리스트 필드 (2026-10-02).

실행: python -m unittest tools/packs/test_build_packs.py
"""
import copy
import json
import pathlib
import sys
import unittest

import jsonschema

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build_packs  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]


def load(rel):
    return json.loads((ROOT / rel).read_text(encoding="utf-8"))


PACK_SCHEMA = load("packs/schema/pack.schema.json")
INDEX_SCHEMA = load("packs/schema/index.schema.json")


def schema_errors(schema, doc):
    return [e.message for e in jsonschema.Draft202012Validator(schema).iter_errors(doc)]


def check(doc):
    errors = []
    build_packs.check_sources(doc, "t", errors)
    return errors


class PassportValidityTest(unittest.TestCase):
    def setUp(self):
        self.pack = load("packs/src/TH/pack.json")

    def req(self, pack):
        return pack["requirements"][0]

    def test_real_packs_pass(self):
        for cc in ["TH", "JP", "SG", "MY", "ID", "TW", "CN", "PH", "VN"]:
            pack = load(f"packs/src/{cc}/pack.json")
            self.assertEqual([], schema_errors(PACK_SCHEMA, pack), cc)
            self.assertEqual([], check(pack), cc)
            self.assertIn("passport_validity", self.req(pack), f"{cc}: 값이 없으면 null 로 밝힌다")

    def test_null_means_not_stated(self):
        pack = copy.deepcopy(self.pack)
        self.req(pack)["passport_validity"] = None
        self.assertEqual([], schema_errors(PACK_SCHEMA, pack))
        self.assertEqual([], check(pack))

    def test_unknown_source_rejected(self):
        pack = copy.deepcopy(self.pack)
        self.req(pack)["passport_validity"]["source"] = "made_up"
        self.assertTrue(any("passport_validity.source" in e for e in check(pack)))

    def test_bad_basis_rejected(self):
        pack = copy.deepcopy(self.pack)
        self.req(pack)["passport_validity"]["basis"] = "booking"
        self.assertTrue(schema_errors(PACK_SCHEMA, pack))
        self.assertTrue(any("basis" in e for e in check(pack)))

    def test_months_must_be_small_int(self):
        for bad in [0, 36, "6", 6.5, True]:
            pack = copy.deepcopy(self.pack)
            self.req(pack)["passport_validity"]["months"] = bad
            self.assertTrue(any("months" in e for e in check(pack)), bad)

    def test_missing_last_verified_rejected_by_schema(self):
        pack = copy.deepcopy(self.pack)
        del self.req(pack)["passport_validity"]["last_verified"]
        self.assertTrue(schema_errors(PACK_SCHEMA, pack))


class ChecklistTest(unittest.TestCase):
    def test_country_item_text_must_match_section(self):
        pack = load("packs/src/CN/pack.json")
        self.assertEqual([], check(pack))
        bad = copy.deepcopy(pack)
        bad["checklist"][0]["text_ko"] = bad["checklist"][0]["text_ko"] + " 지어낸 말"
        self.assertTrue(any("문장과 다름" in e for e in check(bad)))
        bad = copy.deepcopy(pack)
        bad["checklist"][0]["section"] = "nowhere"
        self.assertTrue(any("sections 에 없음" in e for e in check(bad)))

    def test_cn_rules_section_has_its_own_source(self):
        pack = load("packs/src/CN/pack.json")
        rules = next(s for s in pack["sections"] if s["id"] == "rules")
        src = next(s for s in pack["sources"] if s["id"] == rules["source"])
        self.assertIn("0404.go.kr/bbs/safetyNtc", src["url"])
        self.assertTrue(any("전자데이터" in b for b in rules["body_ko"]))

    def test_index_template_valid_and_refs_exist(self):
        index = load("packs/src/index.json")
        self.assertEqual([], schema_errors(INDEX_SCHEMA, index))
        self.assertEqual([], check(index))
        bad = copy.deepcopy(index)
        bad["checklist"].append({"id": "x", "phase": "week", "icon": "card", "kind": "essential", "from": "essential:nope"})
        self.assertTrue(any("essentials 에 없음" in e for e in check(bad)))
        bad = copy.deepcopy(index)
        bad["checklist"][0]["phase"] = "someday"
        self.assertTrue(schema_errors(INDEX_SCHEMA, bad))


if __name__ == "__main__":
    unittest.main()
