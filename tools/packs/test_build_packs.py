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


class JourneyStageTest(unittest.TestCase):
    """여행 과정 8단계 stage (2026-10-03) — 묶는 축. 기한 축 phase 와 따로다(DESIGN_SPEC 부록 H)"""

    STAGES = {"plan", "book", "docs", "pack", "departure", "arrival", "during", "return"}

    def setUp(self):
        self.index = load("packs/src/index.json")

    def test_every_template_item_has_a_known_stage(self):
        self.assertEqual(29, len(self.index["checklist"]))
        for item in self.index["checklist"]:
            self.assertIn(item.get("stage"), self.STAGES, item["id"])
        self.assertEqual([], schema_errors(INDEX_SCHEMA, self.index))

    def test_every_country_pack_item_has_a_stage(self):
        for cc in ("TH", "JP", "SG", "MY", "ID", "TW", "CN", "PH", "VN"):
            pack = load(f"packs/src/{cc}/pack.json")
            for item in pack.get("checklist", []):
                self.assertIn(item.get("stage"), self.STAGES, f"{cc} {item['id']}")

    def test_missing_stage_is_an_error(self):
        bad = copy.deepcopy(self.index)
        del bad["checklist"][0]["stage"]
        errors = []
        build_packs.check_checklist(bad, "index.json", errors)
        self.assertTrue(any("stage 없음" in e for e in errors), errors)

    def test_unknown_stage_rejected_by_schema(self):
        bad = copy.deepcopy(self.index)
        bad["checklist"][0]["stage"] = "someday"
        self.assertTrue(schema_errors(INDEX_SCHEMA, bad))

    def test_old_packs_without_stage_still_pass_the_schema(self):
        """예전 서명 팩(단계 값 없음)도 스키마는 통과한다 — 앱이 phase 에서 옮겨 온다"""
        old = copy.deepcopy(self.index)
        for item in old["checklist"]:
            item.pop("stage", None)
        self.assertEqual([], schema_errors(INDEX_SCHEMA, old))


class AirportsTest(unittest.TestCase):
    """공항 도착 순서 airports[] (2026-10-03): 스키마 + 출처 연결·kind·https·날짜 검사"""

    def setUp(self):
        self.pack = load("packs/src/TH/pack.json")

    def test_th_airports_valid(self):
        self.assertEqual(["BKK", "DMK", "HKT"], [a["code"] for a in self.pack["airports"]])
        self.assertEqual([], schema_errors(PACK_SCHEMA, self.pack))
        self.assertEqual([], check(self.pack))

    def test_pack_without_airports_still_valid(self):
        pack = copy.deepcopy(self.pack)
        del pack["airports"]
        self.assertEqual([], schema_errors(PACK_SCHEMA, pack))
        self.assertEqual([], check(pack))

    def test_unknown_sources_rejected(self):
        for where in ("airport", "step", "form"):
            pack = copy.deepcopy(self.pack)
            ap = pack["airports"][0]
            if where == "airport":
                ap["source"] = "made_up"
            elif where == "step":
                ap["steps"][0]["source"] = "made_up"
            else:
                ap["form_check_source"] = "made_up"
            self.assertTrue(any("made_up" in e for e in check(pack)), where)

    def test_bad_kind_url_date_count_rejected(self):
        pack = copy.deepcopy(self.pack)
        pack["airports"][0]["steps"][0]["kind"] = "lounge"
        self.assertTrue(schema_errors(PACK_SCHEMA, pack))
        self.assertTrue(any("kind" in e for e in check(pack)))
        pack = copy.deepcopy(self.pack)
        pack["airports"][0]["map_url"] = "http://example.org"
        self.assertTrue(schema_errors(PACK_SCHEMA, pack))
        self.assertTrue(any("https" in e for e in check(pack)))
        pack = copy.deepcopy(self.pack)
        pack["airports"][0]["last_verified"] = "2026-13-40"
        self.assertTrue(any("last_verified" in e for e in check(pack)))
        pack = copy.deepcopy(self.pack)
        pack["airports"][0]["steps"] = pack["airports"][0]["steps"][:2]
        self.assertTrue(schema_errors(PACK_SCHEMA, pack))
        self.assertTrue(any("3~7" in e for e in check(pack)))

    def test_required_fields_and_egate(self):
        pack = copy.deepcopy(self.pack)
        del pack["airports"][0]["egate_kr"]
        self.assertTrue(schema_errors(PACK_SCHEMA, pack))
        self.assertTrue(any("egate_kr" in e for e in check(pack)))
        pack = copy.deepcopy(self.pack)
        pack["airports"][0]["egate_kr"] = "yes"
        self.assertTrue(schema_errors(PACK_SCHEMA, pack))
        pack = copy.deepcopy(self.pack)
        pack["airports"][1]["code"] = pack["airports"][0]["code"]
        self.assertTrue(any("겹침" in e for e in check(pack)))

    def test_index_airport_checklist_item(self):
        index = load("packs/src/index.json")
        item = next(i for i in index["checklist"] if i["id"] == "airport_steps")
        self.assertEqual(("arrival", "airports", "open_airport"), (item["phase"], item["from"], item["action"]))

    def test_all_nine_packs_airports_valid(self):
        """아홉 나라 팩 전부 (2026-10-03 합침): 스키마 + 출처 연결, 공항 코드가 나라 안에서 겹치지 않는다"""
        expected = {
            "TH": ["BKK", "DMK", "HKT"], "JP": ["NRT", "HND", "KIX", "FUK"], "SG": ["SIN"],
            "MY": ["KUL", "BKI"], "ID": ["DPS", "CGK"], "TW": ["TPE", "TSA", "KHH"],
            "CN": ["PVG", "PEK", "PKX"], "PH": ["MNL", "CEB"], "VN": ["SGN", "HAN", "DAD", "CXR"],
        }
        for cc, codes in expected.items():
            pack = load(f"packs/src/{cc}/pack.json")
            self.assertEqual(codes, [a["code"] for a in pack["airports"]], cc)
            self.assertEqual([], schema_errors(PACK_SCHEMA, pack), cc)
            self.assertEqual([], check(pack), cc)
            for ap in pack["airports"]:
                self.assertTrue(ap.get("form_check_ko"), f"{cc} {ap['code']} 입국 카드 줄")
                self.assertIn(ap["egate_kr"], (True, False, None), f"{cc} {ap['code']}")
                # 판정이 모름(null)인데 메모만 있는 것은 괜찮다(앱이 `분명하지 않아요`로 보인다).
                # 판정이 true/false 면 그 근거를 메모나 egate 단계가 말해야 한다
                if ap["egate_kr"] is not None:
                    has_step = any(s["kind"] == "egate" for s in ap["steps"])
                    self.assertTrue(ap.get("egate_note_ko") or has_step, f"{cc} {ap['code']} 근거 없음")


class OptionalFormTest(unittest.TestCase):
    """forms[].optional — 의무가 아닌(권장) 신고. 베트남 사전 입국 정보(PAI)"""

    def test_vn_pai_is_optional_and_has_no_recipe(self):
        vn = load("packs/src/VN/pack.json")
        pai = vn["forms"][0]
        self.assertEqual("VN_PAI", pai["id"])
        self.assertIs(True, pai["optional"])
        self.assertIsNone(pai["window_days_including_arrival"], "기한을 만들지 않는다")
        self.assertFalse((ROOT / "packs/src/recipes/VN_PAI.json").exists())

    def test_optional_defaults_to_absent_for_other_packs(self):
        for cc in ["TH", "JP", "SG", "MY", "ID", "TW", "CN", "PH"]:
            for f in load(f"packs/src/{cc}/pack.json")["forms"]:
                self.assertNotIn("optional", f, f"{cc} {f['id']}")

    def test_real_packs_pass_optional_check(self):
        docs = {f"{cc}/pack.json": load(f"packs/src/{cc}/pack.json") for cc in
                ["TH", "JP", "SG", "MY", "ID", "TW", "CN", "PH", "VN"]}
        errors = []
        build_packs.check_optional_forms(docs, errors)
        self.assertEqual([], errors)

    def test_optional_form_with_recipe_rejected(self):
        """레시피가 있는 양식(TH_TDAC)을 optional 로 바꾸면 검증이 막는다 — 스키마는 모양만 보므로 통과한다"""
        pack = load("packs/src/TH/pack.json")
        pack["forms"][0]["optional"] = True
        self.assertEqual([], schema_errors(PACK_SCHEMA, pack), "스키마는 모양만 본다")
        errors = []
        build_packs.check_optional_forms({"TH/pack.json": pack}, errors)
        self.assertTrue(any("레시피가 있음" in e for e in errors), errors)

    def test_optional_form_with_window_days_rejected(self):
        pack = load("packs/src/VN/pack.json")
        pack["forms"][0]["window_days_including_arrival"] = 3
        errors = []
        build_packs.check_optional_forms({"VN/pack.json": pack}, errors, recipes=set())
        self.assertTrue(any("기간 일수가 있음" in e for e in errors), errors)

    def test_bad_optional_type_rejected(self):
        pack = load("packs/src/VN/pack.json")
        pack["forms"][0]["optional"] = "yes"
        self.assertTrue(schema_errors(PACK_SCHEMA, pack))


class MergeAirportsTest(unittest.TestCase):
    """merge_airports.py: 조사 파일을 팩에 합치기 — 출처 겹침 없애기, 다른 주소 같은 id 막기, 버전 올리기, 다시 돌려도 같음"""

    def setUp(self):
        import merge_airports

        self.m = merge_airports
        self.pack = load("packs/src/TH/pack.json")
        self.data = {
            "country": "TH",
            "airports": copy.deepcopy(self.pack["airports"]),
            "sources": [s for s in self.pack["sources"] if s["id"] in {"aot_bkk_arrival", "aot_dmk_arrival", "aot_hkt_arrival", "mofa_th"}],
        }
        self.base = copy.deepcopy(self.pack)
        del self.base["airports"]
        self.base["sources"] = [s for s in self.base["sources"] if not s["id"].startswith("aot_")]
        self.base["version"] = "2026.10.02-1"

    def test_merge_adds_airports_sources_and_bumps(self):
        import datetime

        merged, changed = self.m.merge_one(self.data, self.base, datetime.date(2026, 10, 3))
        self.assertTrue(changed)
        self.assertEqual("2026.10.03-1", merged["version"])
        self.assertEqual(["BKK", "DMK", "HKT"], [a["code"] for a in merged["airports"]])
        ids = [s["id"] for s in merged["sources"]]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertIn("aot_bkk_arrival", ids)
        # airports 는 checklist 앞에
        keys = list(merged)
        self.assertLess(keys.index("airports"), keys.index("checklist"))
        # 다시 합치면 바뀐 것 없음(버전 그대로)
        again, changed2 = self.m.merge_one(self.data, merged, datetime.date(2026, 10, 3))
        self.assertFalse(changed2)
        self.assertEqual(merged, again)

    def test_same_day_bumps_counter(self):
        self.assertEqual("2026.10.03-2", self.m.bump_version("2026.10.03-1", __import__("datetime").date(2026, 10, 3)))
        self.assertEqual("2026.10.04-1", self.m.bump_version("2026.10.03-5", __import__("datetime").date(2026, 10, 4)))

    def test_conflicting_source_url_rejected(self):
        import datetime

        data = copy.deepcopy(self.data)
        next(s for s in data["sources"] if s["id"] == "mofa_th")["url"] = "https://example.org/other"
        with self.assertRaises(self.m.MergeError):
            self.m.merge_one(data, self.base, datetime.date(2026, 10, 3))

    def test_invalid_data_rejected(self):
        import datetime

        data = copy.deepcopy(self.data)
        data["airports"][0]["source"] = "nowhere"
        with self.assertRaises(self.m.MergeError):
            self.m.merge_one(data, self.base, datetime.date(2026, 10, 3))
        data = copy.deepcopy(self.data)
        data["country"] = "JP"
        with self.assertRaises(self.m.MergeError):
            self.m.merge_one(data, self.base, datetime.date(2026, 10, 3))


if __name__ == "__main__":
    unittest.main()
