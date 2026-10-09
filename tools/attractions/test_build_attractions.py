"""관광지 빌드 도구 테스트 (SPEC_v5 §12 '도구'). 표준 라이브러리 unittest + 도구 의존성(jsonschema·cryptography)만.

실행: python -m unittest tools/attractions/test_build_attractions.py
픽스처의 이름·좌표·문장은 모두 가짜다(§4.9). 게이트 픽스처는 실제 후보 목록의 '지역별 곳 수·경보 구조'만 따른다.
"""
import base64
import copy
import datetime as dt
import json
import pathlib
import shutil
import sys
import tempfile
import unicodedata
import unittest

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build_attractions as b  # noqa: E402

TODAY = dt.date(2026, 10, 9)
D = "2026-10-01"
CATS = ["heritage", "nature", "city_view", "museum"]


def fake_pack(cc="JP", airports=("AAA", "BBB"), safety=None):
    return {
        "country": cc,
        "version": "2026.10.01-1",
        "airports": [{"code": c} for c in airports],
        "sections": [{
            "id": "safety",
            "body_ko": safety or ["가짜 나라 전역에 여행경보 1단계(여행유의)가 내려져 있어요.", "가짜 시장에서는 소매치기를 조심하세요."],
            "source": "mofa_xx",
            "last_verified": D,
        }],
    }


def region(rid, order, kind="base", airports=(), hub=(35.0, 135.0), level="1", **extra):
    r = {
        "id": rid, "order": order, "name_ko": f"샘플 지역 {order}", "name_en": f"Sample Region {order}",
        "aliases_ko": [], "group_ko": "샘플 권역", "kind": kind,
        "hub": {"name_ko": "샘플 역", "lat": hub[0], "lng": hub[1], "qid": "Q1"},
        "airports": list(airports), "advisory": {"level": level, "source": "mofa_xx", "last_verified": D},
    }
    r.update(extra)
    return r


def place(pid, rid, n, category="heritage", hub=(35.0, 135.0), dlat=0.01, level="1"):
    return {
        "id": pid,
        "qid": "Q2",
        "names": {"ko": f"샘플 장소 {n}", "ko_basis": "editorial", "en": f"Sample Place {n}", "local": None,
                  "local_short": "サンプル", "local_lang": "ja", "source": "wd"},
        "aliases_ko": [], "aliases_en": [], "mentions_ko": [],
        "region": rid, "area_ko": None, "category": category,
        "tags": [{"id": "indoor", "source": "official", "last_verified": D}],
        "geo": {"kind": "entrance", "lat": hub[0] + dlat, "lng": hub[1], "source": "wd", "qid": "Q3", "osm": None, "last_verified": D},
        "address_local": None,
        "access": {"modes": ["metro"], "nearest_ko": "샘플역", "nearest_local": None, "source": "official", "last_verified": D},
        "official_url": "https://example.org/sample",
        "summary_ko": "샘플 지역에 있는 샘플 장소예요.",
        "body_ko": ["샘플 장소는 오래된 건물이 있는 곳이에요.", "안쪽에는 넓은 마당이 있어요."],
        "claims": [{"id": "c1", "text_ko": "샘플 장소에는 오래된 건물이 있다.", "source": "official", "last_verified": D}],
        "tips_ko": [{"text": "신발을 벗고 들어가요.", "source": "official", "last_verified": D}],
        "facts": {"kind": "facility", "entry": "paid", "booking": "none", "booking_note_ko": None,
                  "regular_closed": ["mon"], "closed_note_ko": None, "visit_note_ko": None,
                  "source": "official", "last_verified": D},
        "volatile": None, "seasonal": [], "risk": [],
        "status": {"value": "open", "note_ko": None, "source": "official", "last_verified": D},
        "advisory": {"level": level, "source": "mofa_xx", "last_verified": D},
        "rank": {"order": n, "designations": []},
        "photo": None, "photo_link": None,
        "source": "wd", "last_verified": D,
    }


def sources():
    return [
        {"id": "wd", "name": "Wikidata", "url": "https://www.wikidata.org/", "use": "skeleton", "license": "CC0"},
        {"id": "official", "name": "샘플 공식 사이트", "url": "https://example.org/", "use": "facts"},
        {"id": "hazard", "name": "샘플 화산 기관", "url": "https://example.org/hazard", "use": "hazard"},
        {"id": "mofa_xx", "name": "외교부 해외안전여행", "url": "https://www.0404.go.kr/", "use": "advisory"},
    ]


def doc(cc="JP", per_region=(4, 4, 2), release="published", airports=("AAA", "BBB")):
    """base 2곳(각 4곳) + daytrip 1곳(2곳) = 10곳, 종류 4개를 돌려 쓴다"""
    regions = [
        region(f"{cc.lower()}_one", 10, airports=[airports[0]]),
        region(f"{cc.lower()}_two", 20, airports=list(airports[1:]), hub=(35.5, 135.5)),
        region(f"{cc.lower()}_day", 30, kind="daytrip", base_regions=[f"{cc.lower()}_one"], hub=(35.3, 135.3), note_ko="하루 다녀오는 곳"),
    ]
    places, n = [], 0
    for r, count in zip(regions, per_region):
        for _ in range(count):
            n += 1
            hub = (r["hub"]["lat"], r["hub"]["lng"])
            places.append(place(f"sample-place-{n}", r["id"], n, CATS[n % len(CATS)], hub=hub))
    return {
        "doc_type": "attractions", "schema_version": 1, "country": cc, "version": "2026.10.01-1", "release": release,
        "editorial_rules": "2026-10", "compact_country": False,
        "unmapped_airports": [], "upcoming_regions": [], "excluded_areas": [],
        "regions": regions, "attractions": places, "retired": [], "sources": sources(),
    }


GATES = {"default": {
    "min_places": 10, "min_base_regions": 2, "min_per_base_region": 3, "min_per_daytrip_region": 1,
    "min_categories": 4, "min_per_category": 2, "category_share_warn": 0.5, "max_thin_detail_ratio": 0.10,
    "max_bytes_per_place": 4608, "max_bytes_per_file": 256000,
}, "countries": {}}


def run_check(d, cc="JP", pack=None, mode="published", strict=True, curation=None, gates=GATES, today=TODAY):
    r = b.Report(cc)
    b.check_doc(d, cc, pack if pack is not None else fake_pack(cc), mode=mode, strict=strict, report=r,
                curation=curation, today=today, gates=gates)
    return r


def has(report, needle, warnings=False):
    return any(needle in m for m in (report.warnings if warnings else report.errors))


class ValidDocTest(unittest.TestCase):
    def test_valid_published_doc_passes(self):
        r = run_check(doc())
        self.assertEqual([], r.errors)

    def test_valid_draft_passes_with_wave(self):
        d = doc(release="draft")
        d["regions"][1]["wave"] = 2
        r = run_check(d, mode="draft", strict=False)
        self.assertEqual([], r.errors)

    def test_wave_in_published_fails(self):
        d = doc()
        d["regions"][0]["wave"] = 1
        self.assertTrue(has(run_check(d), "wave"))


class SchemaEnumTest(unittest.TestCase):
    def test_unknown_key_fails(self):
        d = doc()
        d["surprise"] = 1
        self.assertTrue(has(run_check(d), "스키마"))

    def test_unknown_category_fails(self):
        d = doc()
        d["attractions"][0]["category"] = "beach_club"
        self.assertTrue(has(run_check(d), "category"))

    def test_unknown_tag_mode_day_fail(self):
        d = doc()
        d["attractions"][0]["tags"][0]["id"] = "romantic"
        d["attractions"][1]["access"]["modes"] = ["teleport"]
        d["attractions"][2]["facts"]["regular_closed"] = ["funday"]
        r = run_check(d)
        self.assertTrue(has(r, "tag 'romantic'"))
        self.assertTrue(has(r, "teleport"))
        self.assertTrue(has(r, "funday"))

    def test_doc_type_mismatch_fails(self):
        d = doc()
        d["doc_type"] = "pack"
        self.assertTrue(has(run_check(d), "doc_type"))

    def test_nfd_string_fails(self):
        d = doc()
        d["attractions"][0]["summary_ko"] = unicodedata.normalize("NFD", "샘플 장소예요.")
        self.assertTrue(has(run_check(d), "NFC"))

    def test_unknown_source_fails(self):
        d = doc()
        d["attractions"][0]["tips_ko"][0]["source"] = "blog"
        self.assertTrue(has(run_check(d), "source 'blog'"))

    def test_license_required(self):
        d = doc()
        d["sources"].append({"id": "osm", "name": "OSM", "url": "https://www.openstreetmap.org/", "use": "coords_osm"})
        self.assertTrue(has(run_check(d), "license"))

    def test_sample_source_rejected_in_published(self):
        d = doc()
        d["sources"].append({"id": "sample", "name": "샘플", "url": "https://example.org/", "use": "sample"})
        self.assertTrue(has(run_check(d), "use=sample"))

    def test_unsettled_mark_fails(self):
        d = doc()
        d["attractions"][0]["body_ko"][0] = "[확인 필요] 샘플 문장이에요."
        self.assertTrue(has(run_check(d), "미확정"))


class AdvisoryTest(unittest.TestCase):
    def test_level3_and_special_fail(self):
        for lv in ("3", "4", "special"):
            d = doc()
            d["attractions"][0]["advisory"]["level"] = lv
            d["regions"][0]["advisory"]["level"] = lv
            self.assertTrue(has(run_check(d), "싣지 않는다"), lv)

    def test_level2_allowed_d15b(self):
        d = doc()
        d["regions"][0]["advisory"]["level"] = "2"
        for a in d["attractions"]:
            if a["region"] == d["regions"][0]["id"]:
                a["advisory"]["level"] = "2"
        self.assertEqual([], run_check(d).errors)

    def test_region_lower_than_attraction_fails(self):
        d = doc()
        d["attractions"][0]["advisory"]["level"] = "2"
        self.assertTrue(has(run_check(d), "지역 경보 단계"))

    def test_tw_must_be_none(self):
        d = doc("TW")
        self.assertTrue(has(run_check(d, "TW"), "advisory none"))

    def test_source_must_match_pack(self):
        d = doc()
        d["attractions"][0]["advisory"]["source"] = "official"
        self.assertTrue(has(run_check(d), "pack safety 출처"))

    def test_watch_baseline_fails(self):
        d = doc()
        d["regions"][0]["advisory_watch_ko"] = ["샘플 해안"]
        pack = fake_pack(safety=["샘플 해안은 여행경보 3단계(출국권고)예요."])
        self.assertTrue(has(run_check(d, pack=pack), "advisory_watch_ko"))

    def test_watch_ok_when_only_level1(self):
        d = doc()
        d["regions"][0]["advisory_watch_ko"] = ["샘플 해안"]
        pack = fake_pack(safety=["샘플 해안은 여행경보 1단계(여행유의)예요."])
        self.assertFalse(has(run_check(d, pack=pack), "advisory_watch_ko"))


class AdvisoryHashTest(unittest.TestCase):
    def test_shared_vectors(self):
        vectors = json.loads((b.SCHEMA_DIR / "advisory_hash_vectors.json").read_text(encoding="utf-8"))["vectors"]
        self.assertEqual(3, len(vectors))
        for v in vectors:
            self.assertEqual(v["advisory_sha256"], b.advisory_hash(v["pack"]), v["name"])

    def test_life_paragraph_change_keeps_hash(self):
        a = fake_pack()
        c = copy.deepcopy(a)
        c["sections"][0]["body_ko"][1] = "가짜 시장에서는 날치기를 특히 조심하세요."
        self.assertEqual(b.advisory_hash(a), b.advisory_hash(c))

    def test_advisory_change_changes_hash(self):
        a = fake_pack()
        c = copy.deepcopy(a)
        c["sections"][0]["body_ko"][0] = "가짜 나라 전역에 여행경보 2단계(여행자제)가 내려져 있어요."
        self.assertNotEqual(b.advisory_hash(a), b.advisory_hash(c))

    def test_no_safety_section(self):
        self.assertIsNone(b.advisory_hash({"sections": [{"id": "entry", "body_ko": []}]}))

    def test_real_pack_paragraph_counts(self):
        expected = {"TW": 1, "SG": 1, "VN": 1, "JP": 1, "CN": 2, "TH": 3, "MY": 2, "ID": 2, "PH": 4}
        for cc, n in expected.items():
            pack = json.loads((b.SRC / cc / "pack.json").read_text(encoding="utf-8"))
            self.assertEqual(n, len(b.advisory_paragraphs(pack)), cc)


class StructureTest(unittest.TestCase):
    def test_daytrip_base_must_be_base(self):
        d = doc()
        d["regions"][2]["base_regions"] = ["jp_day"]
        self.assertTrue(has(run_check(d), "base 지역이 아님"))

    def test_daytrip_needs_base(self):
        d = doc()
        d["regions"][2]["base_regions"] = []
        self.assertTrue(has(run_check(d), "base_regions 가 1개 이상"))

    def test_airport_not_in_pack(self):
        d = doc()
        d["regions"][0]["airports"] = ["ZZZ"]
        r = run_check(d)
        self.assertTrue(has(r, "ZZZ"))
        self.assertTrue(has(r, "팩 공항 AAA"))

    def test_unmapped_airport_ok(self):
        d = doc()
        d["regions"][1]["airports"] = []
        d["unmapped_airports"] = [{"code": "BBB", "reason": "upcoming", "reason_ko": "준비 중인 지역"}]
        self.assertEqual([], run_check(d).errors)

    def test_hub_qid_required(self):
        d = doc()
        d["regions"][0]["hub"]["qid"] = ""
        self.assertTrue(has(run_check(d), "hub"))

    def test_unknown_region(self):
        d = doc()
        d["attractions"][0]["region"] = "jp_nowhere"
        self.assertTrue(has(run_check(d), "regions 에 없음"))

    def test_duplicate_id(self):
        d = doc()
        d["attractions"][1]["id"] = d["attractions"][0]["id"]
        self.assertTrue(has(run_check(d), "겹침"))

    def test_public_space_category_limited(self):
        d = doc()
        a = d["attractions"][0]
        a["category"] = "museum"
        a["facts"] = {"kind": "public_space"}
        self.assertTrue(has(run_check(d), "public_space"))

    def test_volcano_needs_hazard(self):
        d = doc()
        d["attractions"][0]["risk"] = ["volcano"]
        self.assertTrue(has(run_check(d), "hazard"))
        d["attractions"][0]["status"]["source"] = "hazard"
        self.assertFalse(has(run_check(d), "hazard"))

    def test_merged_rules(self):
        d = doc()
        d["retired"] = [{"id": "old-one", "reason": "merged", "replaced_by": "nope", "note_ko": None, "source": "editorial", "date": D}]
        self.assertTrue(has(run_check(d), "replaced_by 'nope'"))
        d["retired"] = [
            {"id": "old-one", "reason": "merged", "replaced_by": "old-two", "note_ko": None, "source": "editorial", "date": D},
            {"id": "old-two", "reason": "closed", "note_ko": None, "source": "editorial", "date": D},
        ]
        self.assertTrue(has(run_check(d), "retired 를 가리킴"))
        d["retired"] = [{"id": "old-one", "reason": "merged", "replaced_by": "sample-place-1", "note_ko": None, "source": "editorial", "date": D}]
        self.assertEqual([], run_check(d).errors)

    def test_retired_id_reuse_fails(self):
        d = doc()
        d["retired"] = [{"id": "sample-place-1", "reason": "closed", "note_ko": None, "source": "editorial", "date": D}]
        self.assertTrue(has(run_check(d), "다시 쓸 수 없음"))

    def test_disappeared_id_fails(self):
        d = doc()
        prev = copy.deepcopy(d)
        d["attractions"].pop()
        r = b.Report("JP")
        b.check_doc(d, "JP", fake_pack(), mode="published", strict=False, report=r, gates=GATES, previous=prev)
        self.assertTrue(has(r, "사라짐"))

    def test_cn_address_and_th_local_short(self):
        d = doc("CN")
        for rg in d["regions"]:
            rg["advisory"]["source"] = "mofa_xx"
        self.assertTrue(has(run_check(d, "CN", pack=fake_pack("CN")), "address_local"))
        t = doc("TH")
        t["attractions"][0]["names"]["local_short"] = None
        self.assertTrue(has(run_check(t, "TH", pack=fake_pack("TH")), "local_short"))
        cur = {"attractions": {"sample-place-1": {"no_local_short_ok": True}}}
        self.assertFalse(has(run_check(t, "TH", pack=fake_pack("TH"), curation=cur), "local_short"))


class TextRulesTest(unittest.TestCase):
    def test_number_must_be_in_claims(self):
        d = doc()
        d["attractions"][0]["body_ko"][0] = "샘플 장소는 1603년에 지은 건물이에요."
        self.assertTrue(has(run_check(d), "'1603'"))
        d["attractions"][0]["claims"][0]["text_ko"] = "샘플 장소는 1603년에 지었다."
        self.assertFalse(has(run_check(d), "'1603'"))

    def test_world_heritage_needs_unesco_tag(self):
        d = doc()
        d["attractions"][0]["body_ko"][1] = "세계유산에 들어 있어요."
        self.assertTrue(has(run_check(d), "unesco"))

    def test_forbidden_word(self):
        d = doc()
        d["attractions"][0]["summary_ko"] = "샘플 지역의 대표적 장소예요."
        self.assertTrue(has(run_check(d), "대표적"))

    def test_superlative_needs_basis(self):
        d = doc()
        d["attractions"][0]["body_ko"][1] = "이 나라 최초의 샘플 건물이에요."
        self.assertTrue(has(run_check(d), "최초"))
        cur = {"attractions": {"sample-place-1": {"superlative_basis": "공식 안내 2문단"}}}
        self.assertFalse(has(run_check(d, curation=cur), "최초"))

    def test_lengths(self):
        d = doc()
        d["attractions"][0]["summary_ko"] = "가" * 41
        d["attractions"][1]["tips_ko"] = d["attractions"][1]["tips_ko"] * 5
        d["attractions"][2]["body_ko"] = ["한 문장이에요."] * 5
        r = run_check(d)
        self.assertTrue(has(r, "summary_ko"))
        self.assertTrue(has(r, "tips_ko 는 4개"))
        self.assertTrue(has(r, "body_ko"))

    def test_alias_mention_duplicate_and_dup_alias(self):
        d = doc()
        d["attractions"][0]["aliases_ko"] = ["샘플장소 1", "옛 이름"]
        d["attractions"][0]["mentions_ko"] = ["옛 이름"]
        r = run_check(d)
        self.assertTrue(has(r, "mentions_ko 에 둘 다"))
        self.assertTrue(has(r, "중복 별칭", warnings=True))

    def test_group_alias_parts(self):
        d = doc()
        d["attractions"][0]["aliases_ko"] = ["가지"]
        cur = {"regions": {"jp_one": {"group_alias_parts": {"가나다": ["가지", "나비", "다리"]}}}}
        r = run_check(d, curation=cur)
        self.assertTrue(has(r, "'나비'"))
        self.assertFalse(has(r, "'가지'"))


class DistanceTest(unittest.TestCase):
    def test_haversine(self):
        self.assertAlmostEqual(111.2, b.haversine_km(0, 0, 1, 0), delta=0.2)

    def test_base_far_fails(self):
        d = doc()
        d["attractions"][0]["geo"]["lat"] = 35.0 + 0.8  # 약 89km
        self.assertTrue(has(run_check(d), "70km"))

    def test_base_40km_needs_reason(self):
        d = doc()
        d["attractions"][0]["geo"]["lat"] = 35.0 + 0.45  # 약 50km
        self.assertTrue(has(run_check(d), "keep_over_40km"))
        self.assertTrue(has(run_check(d, strict=False), "keep_over_40km", warnings=True))
        cur = {"regions": {"jp_one": {"keep_over_40km": {"sample-place-1": "같은 노선 종점"}}}}
        self.assertFalse(has(run_check(d, curation=cur), "keep_over_40km"))


class GateTest(unittest.TestCase):
    def test_min_places(self):
        r = run_check(doc(per_region=(3, 3, 1)))
        self.assertTrue(has(r, "관광지 7곳"))

    def test_check_mode_gates_are_warnings(self):
        r = run_check(doc(per_region=(3, 3, 1)), strict=False)
        self.assertEqual([], r.errors)
        self.assertTrue(has(r, "관광지 7곳", warnings=True))

    def test_base_region_min(self):
        r = run_check(doc(per_region=(6, 2, 2)))
        self.assertTrue(has(r, "jp_two 관광지 2곳"))

    def test_category_cap(self):
        d = doc()
        for a in d["attractions"][:6]:
            a["category"] = "heritage"
        r = run_check(d)
        self.assertTrue(has(r, "category_over_cap_reason"))
        self.assertFalse(has(run_check(d, curation={"category_over_cap_reason": "사원이 많은 나라"}), "category_over_cap_reason"))

    def test_thin_detail_ratio(self):
        d = doc()
        d["attractions"][0]["official_url"] = None
        d["attractions"][1]["official_url"] = None
        self.assertTrue(has(run_check(d), "충실도"))

    def test_sg_compact_passes(self):
        cc = "SG"
        regions = [region("sg_central", 10, airports=[]), region("sg_sentosa", 20, hub=(35.2, 135.2)), region("sg_north", 30, hub=(35.4, 135.4))]
        for r in regions:
            r["advisory"]["level"] = "none"
        places, n = [], 0
        for r in regions:
            for _ in range(4):
                n += 1
                p = place(f"sample-place-{n}", r["id"], n, CATS[n % 4], hub=(r["hub"]["lat"], r["hub"]["lng"]), level="none")
                places.append(p)
        d = doc(cc)
        d.update(regions=regions, attractions=places, compact_country=True,
                 unmapped_airports=[{"code": "AAA", "reason": "compact", "reason_ko": "나라 전체가 가까워요"}])
        r = run_check(d, cc, pack=fake_pack(cc, airports=("AAA",)), gates={"default": GATES["default"] | {"min_places": 12}})
        self.assertEqual([], r.errors)

    def ph_fixture(self, merged_cebu: bool):
        """실제 후보 구조만 따른다: 보라카이 4·보홀 5·막탄 2·세부시 4 (이름은 가짜)"""
        regions = [region("ph_boracay", 10, level="1", hub=(11.0, 122.0)), region("ph_bohol", 20, level="1", hub=(10.0, 124.0))]
        counts = {"ph_boracay": 4, "ph_bohol": 5}
        if merged_cebu:
            regions.append(region("ph_cebu", 30, level="2", airports=["CEB"], hub=(10.3, 123.9)))
            counts["ph_cebu"] = 6
        regions[0]["airports"] = ["MNL"] if merged_cebu else []
        places, n = [], 0
        for r in regions:
            for _ in range(counts[r["id"]]):
                n += 1
                places.append(place(f"sample-place-{n}", r["id"], n, CATS[n % 4], hub=(r["hub"]["lat"], r["hub"]["lng"]), level=r["advisory"]["level"]))
        d = doc("PH")
        unmapped = [] if merged_cebu else [
            {"code": "MNL", "reason": "advisory", "reason_ko": "여행경보 때문에 싣지 않은 지역"},
            {"code": "CEB", "reason": "advisory", "reason_ko": "여행경보 때문에 싣지 않은 지역"},
        ]
        d.update(regions=regions, attractions=places, unmapped_airports=unmapped)
        return d

    def test_ph_d15a_fails_default_gates(self):
        d = self.ph_fixture(merged_cebu=False)
        r = run_check(d, "PH", pack=fake_pack("PH", airports=("MNL", "CEB")), gates=json.loads((b.CURATION / "gates.json").read_text(encoding="utf-8")))
        self.assertTrue(has(r, "관광지 9곳 < 12곳"))

    def test_ph_d15b_passes_default_gates(self):
        d = self.ph_fixture(merged_cebu=True)
        r = run_check(d, "PH", pack=fake_pack("PH", airports=("MNL", "CEB")), gates=json.loads((b.CURATION / "gates.json").read_text(encoding="utf-8")))
        self.assertEqual([], r.errors)

    def test_freshness_fail_only_when_signing(self):
        d = doc()
        d["attractions"][0]["status"]["last_verified"] = "2025-01-01"
        self.assertTrue(has(run_check(d), "확인일"))
        self.assertTrue(has(run_check(d, strict=False), "확인일", warnings=True))
        self.assertFalse(has(run_check(d, strict=False), "확인일"))


class WikidataRefTest(unittest.TestCase):
    def test_rules(self):
        self.assertFalse(b.wd_reference_valid([{"property": "P143", "target": "Q328"}]))
        self.assertTrue(b.wd_reference_valid([{"property": "P248", "target": "Q999", "target_is_wikimedia": False}]))
        self.assertFalse(b.wd_reference_valid([{"property": "P248", "target": "Q328", "target_is_wikimedia": True}]))
        self.assertFalse(b.wd_reference_valid([{"property": "P854", "target": "https://ko.wikipedia.org/wiki/x"}]))
        self.assertTrue(b.wd_reference_valid([{"property": "P854", "target": "https://www.example.go.jp/page"}]))


def write_evidence(base: pathlib.Path, cc: str, d: dict, *, bad: str | None = None):
    """관광지마다 스냅샷 1개 + 필드별 인용. bad = 인용을 스냅샷에 없게 만들 관광지 id"""
    for a in d["attractions"]:
        folder = base / cc / a["id"]
        folder.mkdir(parents=True, exist_ok=True)
        entries, lines = [], []
        for field, sid in b.required_fact_fields(a).items():
            quote = f"{a['id']} {field} 공식 안내 문장"
            lines.append(f"머리말   {quote}   꼬리말")
            if a["id"] == bad:
                quote = "스냅샷에 없는 문장"
            entry = {"field": field, "value": "x", "quote": quote, "source": sid, "snapshot": "official-2026-10-01.txt"}
            if field.startswith("claims.") and sid == "wd":
                entry["wd_reference"] = [{"property": "P248", "target": "Q999", "target_is_wikimedia": False}]
            entries.append(entry)
        (folder / "official-2026-10-01.txt").write_text("\n".join(lines), encoding="utf-8")
        (folder / "extract.json").write_text(json.dumps(entries, ensure_ascii=False), encoding="utf-8")


def write_cache(base: pathlib.Path, cc: str, d: dict, text_for: dict | None = None):
    for a in d["attractions"]:
        folder = base / cc / a["id"]
        folder.mkdir(parents=True, exist_ok=True)
        body = (text_for or {}).get(a["id"], "전혀 다른 내용의 백과사전 문장이 여기에 있어요. 샘플과 겹치지 않아요.")
        meta = json.dumps({"collected_via": "mediawiki_api", "url": "https://ko.wikipedia.org/", "date": D})
        (folder / "kowiki.txt").write_text(meta + "\n" + body, encoding="utf-8")


class QuotesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_quotes_pass_and_record(self):
        d = doc()
        write_evidence(self.tmp, "JP", d)
        r = b.Report("JP")
        rec = b.verify_quotes(d, "JP", self.tmp, r)
        self.assertEqual([], r.errors)
        self.assertEqual(len(d["attractions"]), len(rec))
        self.assertTrue(all(len(v["quotes"]) > 0 for v in rec.values()))

    def test_quote_not_in_snapshot_rejected(self):
        d = doc()
        write_evidence(self.tmp, "JP", d, bad="sample-place-1")
        r = b.Report("JP")
        rec = b.verify_quotes(d, "JP", self.tmp, r)
        self.assertTrue(has(r, "글자 그대로 없음"))
        self.assertNotIn("sample-place-1", rec)

    def test_missing_extract_rejected(self):
        r = b.Report("JP")
        b.verify_quotes(doc(), "JP", self.tmp, r)
        self.assertTrue(has(r, "인용 대조 기록 없음"))

    def test_unchanged_facts_pass_without_evidence(self):
        d = doc()
        write_evidence(self.tmp, "JP", d)
        rec = b.verify_quotes(d, "JP", self.tmp, b.Report("JP"))
        r = b.Report("JP")
        b.verify_quotes(d, "JP", self.tmp / "nothing", r, record=rec)
        self.assertEqual([], r.errors)
        d["attractions"][0]["facts"]["regular_closed"] = ["tue"]
        r2 = b.Report("JP")
        b.verify_quotes(d, "JP", self.tmp / "nothing", r2, record=rec)
        self.assertTrue(has(r2, "sample-place-1"))

    def test_wd_claim_needs_valid_reference(self):
        d = doc()
        d["attractions"][0]["claims"][0]["source"] = "wd"
        write_evidence(self.tmp, "JP", d)
        extract = self.tmp / "JP" / "sample-place-1" / "extract.json"
        entries = json.loads(extract.read_text(encoding="utf-8"))
        for e in entries:
            if e["field"] == "claims.c1":
                e["wd_reference"] = [{"property": "P143", "target": "Q328"}]
        extract.write_text(json.dumps(entries, ensure_ascii=False), encoding="utf-8")
        r = b.Report("JP")
        b.verify_quotes(d, "JP", self.tmp, r)
        self.assertTrue(has(r, "유효 참조"))


class CopycheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_text_rules(self):
        ours = "하나 둘 셋 넷 다섯 여섯 일곱 여덟 아홉"
        self.assertTrue(b.copycheck_text(ours, "앞말 하나 둘 셋 넷 다섯 여섯 일곱 여덟 뒷말"))
        self.assertFalse(b.copycheck_text("완전히 다른 문장이에요", "전혀 겹치지 않는 원문이에요"))
        self.assertTrue(any("겹침" in p for p in b.copycheck_text("가나다라마바사아자차카타", "가나다라마바사아자차카타파하")))

    def test_cache_required_for_changed_text(self):
        d = doc()
        r = b.Report("JP")
        b.copycheck(d, "JP", self.tmp / "cache", self.tmp / "ev", r, {}, TODAY)
        self.assertTrue(has(r, "copycheck 캐시가 없음"))

    def test_pass_records_and_unchanged_skips_cache(self):
        d = doc()
        write_cache(self.tmp / "cache", "JP", d)
        r = b.Report("JP")
        rec = b.copycheck(d, "JP", self.tmp / "cache", self.tmp / "ev", r, {}, TODAY)
        self.assertEqual([], r.errors)
        r2 = b.Report("JP")
        b.copycheck(d, "JP", self.tmp / "nocache", self.tmp / "ev", r2, rec, TODAY)
        self.assertEqual([], r2.errors)

    def test_copied_text_fails(self):
        d = doc()
        copied = " ".join(b.copy_text(d["attractions"][0]).split()[:10])
        write_cache(self.tmp / "cache", "JP", d, {"sample-place-1": "다른 말 " + copied + " 끝"})
        r = b.Report("JP")
        b.copycheck(d, "JP", self.tmp / "cache", self.tmp / "ev", r, {}, TODAY)
        self.assertTrue(has(r, "sample-place-1: copycheck 실패"))

    def test_meta_missing_warns(self):
        d = doc()
        write_cache(self.tmp / "cache", "JP", d)
        (self.tmp / "cache" / "JP" / "sample-place-1" / "kowiki.txt").write_text("메타 없는 파일이에요.", encoding="utf-8")
        r = b.Report("JP")
        b.copycheck(d, "JP", self.tmp / "cache", self.tmp / "ev", r, {}, TODAY)
        self.assertTrue(has(r, "메타 없는", warnings=True))


class PipelineTest(unittest.TestCase):
    """promote·sign·retire·verify-committed 를 임시 저장소에서 (진짜 키 대신 테스트 키)"""

    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.paths = b.Paths(root=self.tmp, evidence=self.tmp / "evidence", cache=self.tmp / "cache")
        self.key = Ed25519PrivateKey.generate()
        pub = self.key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        self.keys = {"rp-att-test-1": pub}
        for cc in ("JP", "VN"):
            (self.paths.src / cc).mkdir(parents=True)
            (self.paths.src / cc / "pack.json").write_text(json.dumps(fake_pack(cc), ensure_ascii=False), encoding="utf-8")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def draft(self, cc="JP", extra_wave2=True):
        d = doc(cc, release="draft")
        if extra_wave2:
            d["regions"].append(region(f"{cc.lower()}_later", 40, airports=["BBB"], hub=(36.0, 136.0)))
            d["regions"][1]["airports"] = []
            d["regions"][-1]["wave"] = 2
            d["regions"][-1]["aliases_ko"] = ["나중 지역"]
            d["attractions"].append(place("sample-place-99", f"{cc.lower()}_later", 99, "museum", hub=(36.0, 136.0)))
        (self.paths.drafts / cc).mkdir(parents=True, exist_ok=True)
        (self.paths.drafts / cc / "attractions.json").write_text(json.dumps(d, ensure_ascii=False), encoding="utf-8")
        write_evidence(self.paths.evidence, cc, d)
        write_cache(self.paths.cache, cc, d)
        return d

    def promote(self, cc="JP", kid="rp-att-test-1"):
        return b.promote(self.paths, cc, 1, self.key, kid, TODAY, gates=GATES)

    def test_promote_filters_wave_and_signs(self):
        self.draft()
        r = self.promote()
        self.assertEqual([], r.errors)
        src = json.loads((self.paths.src / "JP" / "attractions.json").read_text(encoding="utf-8"))
        self.assertEqual("published", src["release"])
        self.assertEqual("2026.10.09-1", src["version"])
        self.assertNotIn("jp_later", [x["id"] for x in src["regions"]])
        self.assertNotIn("sample-place-99", [x["id"] for x in src["attractions"]])
        self.assertTrue(all("wave" not in x for x in src["regions"]))
        self.assertIn({"name_ko": "샘플 지역 40", "aliases_ko": ["나중 지역"]}, src["upcoming_regions"])
        self.assertIn({"code": "BBB", "reason": "upcoming", "reason_ko": "준비 중인 지역"}, src["unmapped_airports"])
        self.assertEqual(b.advisory_hash(fake_pack()), src["advisory_basis"]["advisory_sha256"])
        data = (self.paths.assets / "JP" / "attractions.json").read_bytes()
        sig = (self.paths.assets / "JP" / "attractions.json.sig").read_bytes()
        self.assertIsNone(b.verify_signature(data, sig, self.keys))
        self.assertEqual(b.canonical_bytes(src), data)
        self.assertTrue((self.paths.curation / "JP.quotes.json").is_file())
        self.assertTrue((self.paths.curation / "JP.copycheck.json").is_file())
        self.assertEqual([], [x for rep in b.verify_committed(self.paths, self.keys) for x in rep.errors])

    def test_promote_output_is_deterministic(self):
        self.draft()
        self.promote()
        first = (self.paths.assets / "JP" / "attractions.json").read_bytes()
        (self.paths.src / "JP" / "attractions.json").unlink()
        self.promote()
        self.assertEqual(first, (self.paths.assets / "JP" / "attractions.json").read_bytes())

    def test_promote_failure_leaves_src_untouched(self):
        self.draft()
        self.promote()
        before = (self.paths.src / "JP" / "attractions.json").read_bytes()
        d = json.loads((self.paths.drafts / "JP" / "attractions.json").read_text(encoding="utf-8"))
        d["attractions"] = d["attractions"][:3]
        (self.paths.drafts / "JP" / "attractions.json").write_text(json.dumps(d, ensure_ascii=False), encoding="utf-8")
        r = self.promote()
        self.assertTrue(r.errors)
        self.assertEqual(before, (self.paths.src / "JP" / "attractions.json").read_bytes())

    def test_wrong_kid_rejected(self):
        self.draft()
        r = self.promote(kid="rp-2026-1")
        self.assertTrue(has(r, "관광지 키"))
        self.assertFalse((self.paths.assets / "JP" / "attractions.json").exists())
        with self.assertRaises(ValueError):
            b.sign_doc(doc(), self.key, "rp-2026-1")

    def test_pack_key_signature_rejected_by_verifier(self):
        data = b.canonical_bytes(doc())
        sig = {"kid": "rp-2026-1", "alg": "Ed25519", "sig": base64.b64encode(self.key.sign(data)).decode()}
        self.assertIn("관광지 키", b.verify_signature(data, json.dumps(sig).encode(), {"rp-2026-1": b"x" * 32}))

    def test_draft_never_signed(self):
        self.draft()
        d = json.loads((self.paths.drafts / "JP" / "attractions.json").read_text(encoding="utf-8"))
        r = b.Report("JP")
        b.check_doc(d, "JP", fake_pack(), mode="published", strict=True, report=r, gates=GATES)
        self.assertTrue(has(r, "release=published"))

    def test_sign_unchanged_needs_no_evidence_or_cache(self):
        self.draft()
        self.promote()
        shutil.rmtree(self.paths.evidence)
        shutil.rmtree(self.paths.cache)
        r = b.sign_existing(self.paths, "JP", self.key, "rp-att-test-1", TODAY, gates=GATES)
        self.assertEqual([], r.errors)
        src = json.loads((self.paths.src / "JP" / "attractions.json").read_text(encoding="utf-8"))
        self.assertEqual("2026.10.09-2", src["version"])

    def test_sign_changed_text_needs_cache(self):
        self.draft()
        self.promote()
        shutil.rmtree(self.paths.cache)
        src_path = self.paths.src / "JP" / "attractions.json"
        src = json.loads(src_path.read_text(encoding="utf-8"))
        src["attractions"][0]["summary_ko"] = "샘플 지역의 다른 샘플 장소예요."
        src_path.write_text(json.dumps(src, ensure_ascii=False), encoding="utf-8")
        r = b.sign_existing(self.paths, "JP", self.key, "rp-att-test-1", TODAY, gates=GATES)
        self.assertTrue(has(r, "copycheck 캐시가 없음"))

    def test_one_country_failure_does_not_stop_others(self):
        self.draft("JP")
        self.draft("VN")
        self.promote("JP")
        self.promote("VN")
        bad = self.paths.src / "JP" / "attractions.json"
        d = json.loads(bad.read_text(encoding="utf-8"))
        d["attractions"][0]["category"] = "beach_club"
        bad.write_text(json.dumps(d, ensure_ascii=False), encoding="utf-8")
        before = (self.paths.assets / "JP" / "attractions.json").read_bytes()
        reports = [b.sign_existing(self.paths, cc, self.key, "rp-att-test-1", TODAY, gates=GATES) for cc in ("JP", "VN")]
        self.assertTrue(reports[0].errors)
        self.assertEqual([], reports[1].errors)
        self.assertEqual(before, (self.paths.assets / "JP" / "attractions.json").read_bytes())

    def test_retire_ignores_gates_and_moves_airports(self):
        self.draft()
        self.promote()
        ids = [a["id"] for a in json.loads((self.paths.src / "JP" / "attractions.json").read_text(encoding="utf-8"))["attractions"] if a["region"] == "jp_one"]
        r = b.retire(self.paths, "JP", ids, "safety", "안전 문제로 확인 중이에요.", self.key, "rp-att-test-1", TODAY)
        self.assertEqual([], r.errors)
        src = json.loads((self.paths.src / "JP" / "attractions.json").read_text(encoding="utf-8"))
        self.assertNotIn("jp_one", [x["id"] for x in src["regions"]])
        self.assertIn({"code": "AAA", "reason": "safety_retired", "reason_ko": "안전 문제로 뺀 지역"}, src["unmapped_airports"])
        self.assertEqual(set(ids), {x["id"] for x in src["retired"]})
        self.assertTrue(has(r, "거점 지역이 빠져", warnings=True))
        self.assertEqual([], [x for rep in b.verify_committed(self.paths, self.keys) for x in rep.errors])

    def test_retired_survive_next_promote(self):
        self.draft()
        self.promote()
        b.retire(self.paths, "JP", ["sample-place-1"], "closed", None, self.key, "rp-att-test-1", TODAY)
        d = json.loads((self.paths.drafts / "JP" / "attractions.json").read_text(encoding="utf-8"))
        d["attractions"] = [a for a in d["attractions"] if a["id"] != "sample-place-1"]
        (self.paths.drafts / "JP" / "attractions.json").write_text(json.dumps(d, ensure_ascii=False), encoding="utf-8")
        r = b.promote(self.paths, "JP", 1, self.key, "rp-att-test-1", TODAY, gates=GATES | {"default": GATES["default"] | {"min_places": 9}})
        self.assertEqual([], r.errors)
        src = json.loads((self.paths.src / "JP" / "attractions.json").read_text(encoding="utf-8"))
        self.assertIn("sample-place-1", [x["id"] for x in src["retired"]])

    def test_verify_committed_detects_unsigned_src_change_and_ignores_drafts(self):
        self.draft()
        self.promote()
        dpath = self.paths.drafts / "JP" / "attractions.json"
        d = json.loads(dpath.read_text(encoding="utf-8"))
        d["attractions"][0]["summary_ko"] = "작업본만 바꿨어요."
        dpath.write_text(json.dumps(d, ensure_ascii=False), encoding="utf-8")
        self.assertEqual([], [x for rep in b.verify_committed(self.paths, self.keys) for x in rep.errors])
        spath = self.paths.src / "JP" / "attractions.json"
        s = json.loads(spath.read_text(encoding="utf-8"))
        s["attractions"][0]["summary_ko"] = "원본을 서명 없이 바꿨어요."
        spath.write_text(json.dumps(s, ensure_ascii=False), encoding="utf-8")
        self.assertTrue(any("바이트가 다름" in x for rep in b.verify_committed(self.paths, self.keys) for x in rep.errors))

    def test_verify_committed_rejects_tampered_bytes(self):
        self.draft()
        self.promote()
        out = self.paths.assets / "JP" / "attractions.json"
        out.write_bytes(out.read_bytes().replace("샘플".encode(), "가짜".encode(), 1))
        self.assertTrue(any("서명 불일치" in x for rep in b.verify_committed(self.paths, self.keys) for x in rep.errors))

    def test_check_all_reports_drafts_and_src(self):
        self.draft()
        self.promote()
        reports = b.check_all(self.paths, None, TODAY)
        labels = [r.label for r in reports]
        self.assertTrue(any("drafts" in x for x in labels))
        self.assertTrue(any("src" in x for x in labels))

    def test_next_version(self):
        self.assertEqual("2026.10.09-1", b.next_version(None, TODAY))
        self.assertEqual("2026.10.09-3", b.next_version("2026.10.09-2", TODAY))
        self.assertEqual("2026.10.09-1", b.next_version("2026.10.01-5", TODAY))


class AppContractTest(unittest.TestCase):
    def test_app_has_att_key(self):
        keys = b.trusted_attraction_keys()
        self.assertTrue(keys)
        self.assertTrue(all(b.ATT_KID.match(k) for k in keys))

    def test_repo_check_passes(self):
        """저장소의 작업본·원본·debug 샘플이 지금 규칙을 통과한다(CI --check 와 같은 검사)"""
        reports = b.check_all(b.Paths(), None, TODAY)
        self.assertEqual([], [e for r in reports for e in r.errors])

    def test_committed_signed_files_verify(self):
        self.assertEqual([], [e for r in b.verify_committed(b.Paths()) for e in r.errors])


if __name__ == "__main__":
    unittest.main()
