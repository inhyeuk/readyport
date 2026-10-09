"""평점 집계·여행 계획 요청·정리 테스트 (메모리 Firestore·가짜 엔진 — 네트워크·Claude 없음, 가짜 자료만)."""
import datetime as dt
import json
import pathlib
import sys
import unittest
from types import SimpleNamespace

from ops.aria import run_hourly as rh
from ops.aria.fingerprint_store import FingerprintStore
from ops.aria.jobs import plan_cleanup as pc
from ops.aria.jobs import plan_flags as pf
from ops.aria.jobs import plan_requests as pr
from ops.aria.jobs import ratings_weekly as rt
from ops.aria.runners import plan_engine
from ops.aria.tests.helpers import MemFirestore, TempDirCase

NOW = dt.datetime(2026, 10, 12, 3, 0, tzinfo=dt.timezone.utc)
TODAY = dt.date(2026, 10, 12)


def write_attractions(root: pathlib.Path, cc="JP", ids=("sample-one", "sample-two"), signed=True):
    doc = {"doc_type": "attractions", "country": cc, "version": "2026.10.10-1", "release": "published",
           "regions": [{"id": "zz_one", "name_ko": "샘플 지역", "kind": "base", "advisory": {"level": "1"}}],
           "attractions": [{"id": i, "names": {"ko": f"샘플 {n}"}, "region": "zz_one", "category": "heritage",
                            "summary_ko": "가짜 장소예요.", "facts": {"kind": "facility", "entry": "paid", "regular_closed": ["mon"]},
                            "tags": [{"id": "stairs"}], "access": {"modes": ["metro"], "nearest_ko": "샘플역"},
                            "tips_ko": [{"text": "가짜 팁이에요."}], "status": {"value": "open"}, "advisory": {"level": "1"}}
                           for n, i in enumerate(ids)]}
    (root / "packs" / "src" / cc).mkdir(parents=True, exist_ok=True)
    (root / "packs" / "src" / cc / "attractions.json").write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
    assets = root / "app" / "src" / "main" / "assets" / "packs" / cc
    assets.mkdir(parents=True, exist_ok=True)
    pack = {"country": cc, "version": "2026.10.03-2", "names": {"ko": "샘플나라"},
            "sections": [{"title_ko": "안전", "body_ko": ["가짜 안전 안내예요."]}],
            "airports": [{"code": "AAA", "name_ko": "샘플 공항", "city_ko": "샘플시"}],
            "emergency": [{"label_ko": "경찰", "number": "000"}]}
    (assets / "pack.json").write_text(json.dumps(pack, ensure_ascii=False), encoding="utf-8")
    (assets / "pack.json.sig").write_text("{}", encoding="utf-8")
    if signed:
        (assets / "attractions.json").write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
        (assets / "attractions.json.sig").write_text("{}", encoding="utf-8")


# ---------------- 평점 ----------------

class RatingsTest(unittest.TestCase, TempDirCase):
    def setUp(self):
        self.tmp = self.make_tmp()
        write_attractions(self.tmp)
        self.cfg = self.make_cfg(self.tmp, ratings_min_n=3)

    def votes(self, aid, stars, cc="JP"):
        return {f"attraction_ratings/{cc}_{aid}/votes/uid{i:03d}": {"stars": s, "at": NOW, "visited": True}
                for i, s in enumerate(stars)}

    def test_aggregate_hides_small_samples_and_ignores_bad_votes(self):
        docs = {**self.votes("sample-one", [5, 4, 4, 3]), **self.votes("sample-two", [5, 5])}
        docs["attraction_ratings/JP_sample-one/votes/bad1"] = {"stars": 9, "visited": True}
        docs["attraction_ratings/JP_sample-one/votes/bad2"] = {"stars": True, "visited": True}
        docs["attraction_ratings/JP_sample-one/votes/bad3"] = {"stars": 1, "visited": False}
        fs = MemFirestore(docs)
        out = rt.run(self.cfg, fs, dry_run=False, now=NOW)
        stats = fs.docs["attraction_rating_stats/JP"]
        self.assertEqual(stats["sample-one"], {"avg": 4.0, "n": 4})
        self.assertNotIn("sample-two", stats)                 # 2명 < 3명 → 숨김
        self.assertEqual(stats["_meta"], {"updated_at": NOW, "min_n": 3})
        self.assertEqual(out["countries"]["JP"], {"shown": 1, "hidden_below_min": 1, "changed": True, "written": True})
        # 그대로면 쓰지 않는다
        n = len(fs.writes)
        self.assertFalse(rt.run(self.cfg, fs, dry_run=False, now=NOW)["countries"]["JP"]["changed"])
        self.assertEqual(len(fs.writes), n)
        # uid 는 어디에도 남지 않는다
        self.assertNotIn("uid", json.dumps(stats, default=str))

    def test_pagination_and_dry_run(self):
        fs = MemFirestore(self.votes("sample-one", [4] * 1203))
        self.assertEqual(len(rt.fetch_stars(fs, "JP", "sample-one")), 1203)
        self.assertEqual(len([q for q in fs.queries]), 3)
        fs2 = MemFirestore(self.votes("sample-one", [4] * 5))
        rt.run(self.cfg, fs2, dry_run=True, now=NOW)
        self.assertEqual(fs2.writes, [])
        self.assertEqual(rt.run(self.cfg, None)["status"], "not_configured")


# ---------------- 계획 요청 ----------------

def req(**kw):
    d = {"uid": "testuid01", "country": "JP", "purposes": ["sightseeing", "food"], "purpose_note": "가짜 메모",
         "travelers": {"adults": 2, "seniors": 1, "teens": 0, "children": 1, "genders": {"female": 2, "male": 2}},
         "mobility": ["long_walk_hard"], "sensitive_consent": True, "days": 2, "budget_band": "standard",
         "currency": "KRW", "status": "queued", "createdAt": NOW - dt.timedelta(minutes=30)}
    d.update(kw)
    return {k: v for k, v in d.items() if v is not None}


GOOD_PLAN = {
    "days": [{"day": 1, "title": "첫날", "items": [{"time_hint": "morning", "place_id": "sample-one", "title": "샘플 1 보기", "note": "월요일은 쉬어요."},
                                                 {"time_hint": "afternoon", "place_id": None, "title": "숙소 근처 쉬기", "note": "2시간쯤 쉬어요."}]},
             {"day": 2, "items": [{"time_hint": "late_morning", "place_id": "sample-two", "title": "샘플 2 보기", "note": ""}]}],
    "tips": ["계단이 많은 곳은 천천히 다녀요."], "budget_notes": ["표준 예산이면 대중교통을 써요."], "caveats": [],
}


class FakeEngine:
    def __init__(self, result=None, on_call=None):
        self.result = result or plan_engine.EngineResult("ok", json.loads(json.dumps(GOOD_PLAN)))
        self.prompts = []
        self.on_call = on_call

    def __call__(self, prompt):
        self.prompts.append(prompt)
        if self.on_call:
            self.on_call()
        return self.result


class PlanValidationTest(unittest.TestCase):
    def test_request_rules(self):
        self.assertIsNone(pr.validate_request(req()))
        self.assertIsNone(pr.validate_request(req(mobility=[], sensitive_consent=None)))
        self.assertIsNone(pr.validate_request(req(days=None, start_date="2026-11-01", end_date="2026-11-05")))
        bad = [req(country="KR"), req(purposes=[]), req(purposes=["x"]), req(purpose_note="가" * 201),
               req(travelers={"adults": 0, "seniors": 0, "teens": 0, "children": 0}),
               req(travelers={"adults": 2, "seniors": 0, "teens": 0}),
               req(travelers={"adults": 2, "seniors": 0, "teens": 0, "children": 0, "genders": {"x": 1}}),
               req(mobility=["blind"]), req(sensitive_consent=None), req(mobility=[], sensitive_consent=True),
               req(days=31), req(start_date="2026-11-01", end_date="2026-11-02"),
               req(days=None, start_date="2026-11-05", end_date="2026-11-01"),
               req(budget_band="cheap"), req(currency="USD"), req(passport_no="X")]
        for b in bad:
            self.assertIsNotNone(pr.validate_request(b), b)
        self.assertEqual(pr.trip_days(req(days=None, start_date="2026-11-01", end_date="2026-11-05")), 5)

    def test_plan_rules(self):
        ids = {"sample-one", "sample-two"}
        self.assertIsNone(pr.validate_plan(GOOD_PLAN, 2, ids))
        self.assertEqual(pr.validate_plan(GOOD_PLAN, 3, ids), "days_count")
        for text, code in (("오전 9시에 열어요", "numbers"), ("10:00 개장", "numbers"), ("입장료 1,000엔", "numbers"),
                           ("$15 정도", "numbers"), ("9am 출발", "numbers")):
            p = json.loads(json.dumps(GOOD_PLAN))
            p["days"][0]["items"][0]["note"] = text
            self.assertEqual(pr.validate_plan(p, 2, ids), code, text)
        p = json.loads(json.dumps(GOOD_PLAN))
        p["days"][0]["items"][0]["place_id"] = "made-up-place"
        self.assertEqual(pr.validate_plan(p, 2, ids), "unknown_place")
        p = json.loads(json.dumps(GOOD_PLAN))
        p["days"][0]["items"][0]["time_hint"] = "09:00"
        self.assertEqual(pr.validate_plan(p, 2, ids), "time_hint")
        p = json.loads(json.dumps(GOOD_PLAN))
        p["extra"] = 1
        self.assertEqual(pr.validate_plan(p, 2, ids), "shape")
        clean = pr.sanitize_plan(GOOD_PLAN)
        self.assertTrue(any("출발 전에 공식 안내" in c for c in clean["caveats"]))
        self.assertEqual(clean["days"][1]["day"], 2)

    def test_extract_json(self):
        self.assertEqual(plan_engine.extract_json_object('설명\n```json\n{"a": "}"}\n```'), {"a": "}"})
        self.assertEqual(plan_engine.extract_json_object('앞말 {"a": {"b": 1}} 뒷말'), {"a": {"b": 1}})
        self.assertIsNone(plan_engine.extract_json_object("없음"))

    def test_engine_runs_in_empty_dir_with_no_tools_and_stdin_prompt(self):
        calls = []

        def fake_run(argv, **kw):
            calls.append((argv, kw))
            return SimpleNamespace(stdout=json.dumps({"is_error": False, "result": json.dumps(GOOD_PLAN)}), stderr="", returncode=0)
        res = plan_engine.generate("비밀 요청 내용", claude_bin=sys.executable, timeout_sec=9, runner=fake_run)
        self.assertEqual(res.status, "ok")
        self.assertEqual(res.data["days"][0]["day"], 1)
        argv, kw = calls[0]
        self.assertEqual(kw["input"], "비밀 요청 내용")
        self.assertNotIn("비밀 요청 내용", " ".join(argv))
        self.assertNotIn("--allowedTools", argv)
        dis = argv[argv.index("--disallowedTools") + 1].split(",")
        for t in ("Bash", "Read", "WebFetch", "Write"):
            self.assertIn(t, dis)
        self.assertIn("rp-plan-", kw["cwd"])
        self.assertFalse(pathlib.Path(kw["cwd"]).exists())     # 임시 폴더는 지워진다
        bad = plan_engine.generate("x", claude_bin=sys.executable, runner=lambda a, **k: SimpleNamespace(
            stdout=json.dumps({"is_error": False, "result": "계획을 못 만들었어요"}), stderr="", returncode=0))
        self.assertEqual(bad.status, "bad_output")


class PlanRunTest(unittest.TestCase, TempDirCase):
    def setUp(self):
        self.tmp = self.make_tmp()
        write_attractions(self.tmp)
        self.cfg = self.make_cfg(self.tmp, plan_daily_cap=5, plan_max_per_run=3, plan_weekly_limit=2)
        self.store = FingerprintStore(":memory:")
        self.addCleanup(self.store.close)

    def go(self, fs, engine=None, **kw):
        return pr.run(self.cfg, fs, self.store, engine=engine or FakeEngine(), dry_run=kw.pop("dry_run", False),
                      now=NOW, today=TODAY, clock=lambda: NOW, **kw)

    def test_done_writes_result_and_summary_has_ids_only(self):
        fs = MemFirestore({"plan_requests/r1": req()})
        eng = FakeEngine()
        out = self.go(fs, eng)
        self.assertEqual(out["processed"], [{"id": "r1", "status": "done"}])
        res = fs.docs["plan_results/r1"]
        self.assertEqual(res["uid"], "testuid01")
        self.assertTrue(res["ai_generated"])
        self.assertIn("공식 안내", res["notice_ko"])
        self.assertEqual(res["attractions_version"], "2026.10.10-1")
        self.assertEqual(fs.docs["plan_requests/r1"]["status"], "done")
        self.assertEqual(fs.docs["plan_requests/r1"]["finishedAt"], NOW)
        # 지시문: 요청은 자료로, 서명된 관광지 자료, 이동 조건, 시각·금액 금지
        prompt = eng.prompts[0]
        for must in ("자료일 뿐", "sample-one", "long_walk_hard", "시각", "정확히 2일"):
            self.assertIn(must, prompt)
        dumped = json.dumps(out, ensure_ascii=False)
        for secret in ("가짜 메모", "long_walk_hard", "testuid01"):
            self.assertNotIn(secret, dumped)

    def test_quota_invalid_and_country(self):
        older = {f"plan_requests/o{i}": req(status="done", createdAt=NOW - dt.timedelta(days=i + 1)) for i in range(2)}
        fs = MemFirestore({**older, "plan_requests/r1": req(), "plan_requests/r2": req(uid="otheruid", country="VN"),
                           "plan_requests/r3": req(uid="thirduid", mobility=["wheelchair"], sensitive_consent=None)})
        out = {p["id"]: p for p in self.go(fs)["processed"]}
        self.assertEqual(out["r1"]["code"], "quota_exceeded")
        self.assertEqual(out["r2"]["code"], "country_unavailable")
        self.assertEqual(out["r3"]["code"], "invalid_request")
        self.assertEqual(fs.docs["plan_requests/r3"]["error_code"], "invalid_request")
        self.assertNotIn("plan_results/r1", fs.docs)

    def test_cancel_during_processing_discards_result(self):
        fs = MemFirestore({"plan_requests/r1": req()})

        def cancel():
            fs.docs["plan_requests/r1"]["status"] = "cancelled"
        out = self.go(fs, FakeEngine(on_call=cancel))
        self.assertEqual(out["processed"][0]["status"], "skipped")
        self.assertNotIn("plan_results/r1", fs.docs)
        self.assertEqual(fs.docs["plan_requests/r1"]["status"], "cancelled")

    def test_engine_failures_and_bad_output(self):
        fs = MemFirestore({"plan_requests/r1": req(), "plan_requests/r2": req(uid="uid2"), "plan_requests/r3": req(uid="uid3")})
        bad = json.loads(json.dumps(GOOD_PLAN))
        bad["tips"] = ["입장료는 500엔이에요"]
        engines = iter([FakeEngine(plan_engine.EngineResult("timeout", None, "x")),
                        FakeEngine(plan_engine.EngineResult("ok", bad)),
                        FakeEngine(plan_engine.EngineResult("error", None, "x"))])
        out = self.go(fs, lambda p: next(engines)(p))
        self.assertEqual([p["code"] for p in out["processed"]], ["engine_timeout", "invalid_output", "engine_error"])
        self.assertFalse(any(k.startswith("plan_results/") for k in fs.docs))

    def test_daily_cap_budget_and_dry_run(self):
        self.cfg.plan_daily_cap = 1
        fs = MemFirestore({"plan_requests/r1": req(), "plan_requests/r2": req(uid="uid2")})
        out = self.go(fs)
        self.assertTrue(out["cap_reached"])
        self.assertEqual(sum(1 for d in fs.docs.values() if d.get("status") == "queued"), 1)
        fs2 = MemFirestore({"plan_requests/r1": req()})
        t = iter([0.0, 100.0, 100.0])
        out = self.go(fs2, budget_sec=10, monotonic=lambda: next(t))
        self.assertTrue(out["budget_stop"])
        self.assertEqual(fs2.docs["plan_requests/r1"]["status"], "queued")
        fs3 = MemFirestore({"plan_requests/r1": req()})
        out = self.go(fs3, dry_run=True)
        self.assertEqual(out["processed"], [{"id": "r1", "status": "would_process"}])
        self.assertEqual(fs3.writes, [])


class CleanupTest(unittest.TestCase, TempDirCase):
    def test_retention_and_stuck(self):
        cfg = self.make_cfg(self.make_tmp(), plan_retention_days=30)
        old, recent = NOW - dt.timedelta(days=31), NOW - dt.timedelta(days=3)
        fs = MemFirestore({
            "plan_requests/done_old": req(status="done", createdAt=old, finishedAt=old),
            "plan_results/done_old": {"uid": "u", "createdAt": old},
            "plan_requests/done_new": req(status="done", createdAt=recent, finishedAt=recent),
            "plan_results/done_new": {"uid": "u", "createdAt": recent},
            "plan_requests/cancel_old": req(status="cancelled", createdAt=old, finishedAt=old),
            "plan_requests/queued_old": req(createdAt=old),
            "plan_requests/finished_late": req(status="done", createdAt=old, finishedAt=recent),
            "plan_requests/stuck": req(status="processing", processingAt=NOW - dt.timedelta(hours=7)),
            "plan_requests/working": req(status="processing", processingAt=NOW - dt.timedelta(minutes=5)),
            "plan_results/orphan": {"uid": "u", "createdAt": old},
            "plan_quota/u_old": {"last": old, "prev": None},
            "plan_quota/u_new": {"last": recent, "prev": None},
        })
        dry = pc.run(cfg, fs, dry_run=True, now=NOW)
        self.assertEqual(fs.writes, [])
        out = pc.run(cfg, fs, dry_run=False, now=NOW)
        self.assertEqual(dry["finished_deleted"], out["finished_deleted"])
        for gone in ("plan_requests/done_old", "plan_results/done_old",
                     "plan_requests/queued_old", "plan_results/orphan", "plan_quota/u_old"):
            self.assertNotIn(gone, fs.docs)
        # 취소한 요청은 오래돼도 이용자가 지울 때까지 남는다(2026-10-09)
        for kept in ("plan_requests/cancel_old", "plan_requests/done_new", "plan_results/done_new", "plan_requests/finished_late",
                     "plan_requests/working", "plan_quota/u_new"):
            self.assertIn(kept, fs.docs)
        # 신고 칸이 없던 정리에서도 신고 0건
        self.assertEqual(out["flags_deleted"], 0)
        self.assertEqual(fs.docs["plan_requests/stuck"]["status"], "failed")
        self.assertEqual(fs.docs["plan_requests/stuck"]["error_code"], "engine_timeout")
        self.assertEqual(out["stuck_failed"], 1)


    def test_flags_go_with_their_request(self):
        cfg = self.make_cfg(self.make_tmp(), plan_retention_days=30)
        old, recent = NOW - dt.timedelta(days=31), NOW - dt.timedelta(days=3)
        flag = {"uid": "u", "reason": "unsafe", "note": "가짜 메모", "at": recent}
        fs = MemFirestore({
            # 30일 정리로 요청·결과가 지워지면 신고도 함께
            "plan_requests/done_old": req(status="done", createdAt=old, finishedAt=old),
            "plan_results/done_old": {"uid": "u", "createdAt": old},
            "plan_flags/done_old": dict(flag),
            # 아직 보관 중인 계획의 신고는 남는다
            "plan_requests/done_new": req(status="done", createdAt=recent, finishedAt=recent),
            "plan_results/done_new": {"uid": "u", "createdAt": recent},
            "plan_flags/done_new": dict(flag),
            # 이용자가 앱에서 요청·결과를 지운 뒤 남은 신고 → 다음 정리 때 지운다
            "plan_flags/user_deleted": dict(flag),
        })
        dry = pc.run(cfg, fs, dry_run=True, now=NOW)
        self.assertEqual(fs.writes, [])
        self.assertEqual(dry["flags_deleted"], 1)
        out = pc.run(cfg, fs, dry_run=False, now=NOW)
        for gone in ("plan_flags/done_old", "plan_requests/done_old", "plan_results/done_old", "plan_flags/user_deleted"):
            self.assertNotIn(gone, fs.docs)
        self.assertIn("plan_flags/done_new", fs.docs)
        self.assertEqual(out["flags_deleted"], 1)          # 짝으로 지운 것은 1번에서, 남은 신고만 여기서 센다
        self.assertEqual(out["finished_deleted"], 1)


class PlanFlagsTest(unittest.TestCase):
    def setUp(self):
        self.store = FingerprintStore(":memory:")
        self.addCleanup(self.store.close)

    @staticmethod
    def flag(minutes_ago, **kw):
        return {"uid": "u", "reason": "inappropriate", "note": "가짜 메모 내용", "at": NOW - dt.timedelta(minutes=minutes_ago), **kw}

    def test_counts_only_new_flags_since_last_check_with_ids_only(self):
        fs = MemFirestore({"plan_flags/a1": self.flag(90), "plan_flags/b2": self.flag(30)})
        # 시험: 세기만, 마지막 시각은 옮기지 않는다
        self.assertEqual(pf.check(fs, self.store, dry_run=True), {"new": 2, "ids": ["a1", "b2"]})
        self.assertIsNone(self.store.get_value(pf.LAST_KEY))
        out = pf.check(fs, self.store, dry_run=False)
        self.assertEqual(out, {"new": 2, "ids": ["a1", "b2"]})
        self.assertNotIn("가짜 메모", json.dumps(out, ensure_ascii=False))     # 내용은 담지 않는다
        self.assertEqual(pf.check(fs, self.store, dry_run=False), {"new": 0, "ids": []})
        fs.docs["plan_flags/c3"] = self.flag(5)
        self.assertEqual(pf.check(fs, self.store, dry_run=False), {"new": 1, "ids": ["c3"]})
        self.assertEqual(pf.check(fs, self.store, dry_run=False)["new"], 0)
        # 질의는 at 만 고른다
        q, _ = fs.queries[-1]
        self.assertEqual(q["select"], {"fields": [{"fieldPath": "at"}]})
        self.assertEqual(fs.writes, [])                     # Firestore 에는 쓰지 않는다

    def test_paging_and_notice(self):
        fs = MemFirestore({f"plan_flags/f{i:03d}": self.flag(600 - i) for i in range(pf.PAGE + 30)})
        out = pf.check(fs, self.store, dry_run=False)
        self.assertEqual(out["new"], pf.PAGE + 30)
        self.assertEqual(len(out["ids"]), pf.MAX_IDS)
        self.assertEqual(out["more"], pf.PAGE + 30 - pf.MAX_IDS)
        self.assertIsNone(pf.notice({"new": 0, "ids": []}))
        self.assertEqual(pf.notice({"new": 2, "ids": ["a1", "b2"]}), "[레디포트] AI 계획 신고 2건: a1, b2")
        line = pf.notice({"new": 12, "ids": [f"r{i}" for i in range(12)]})
        self.assertTrue(line.startswith("[레디포트] AI 계획 신고 12건: r0, r1"))
        self.assertTrue(line.endswith(" 외 2건"))
        self.assertEqual(pf.check(None, self.store)["status"], "not_configured")


class RunHourlyTest(unittest.TestCase, TempDirCase):
    def test_cleanup_once_a_day_and_notice(self):
        tmp = self.make_tmp()
        write_attractions(tmp)
        cfg = self.make_cfg(tmp)
        store = FingerprintStore(":memory:")
        self.addCleanup(store.close)
        fs = MemFirestore({"plan_requests/r1": req()})

        class N:
            sent = []

            def send(self, t):
                self.sent.append(t)
        n = N()
        out = rh.run_hourly(cfg, firestore=fs, dry_run=False, store=store, engine=FakeEngine(), notifier=n, now=NOW, today=TODAY)
        self.assertIn("cleanup", out)
        self.assertEqual(out["plans"]["processed"][0]["status"], "done")
        out2 = rh.run_hourly(cfg, firestore=fs, dry_run=False, store=store, engine=FakeEngine(), now=NOW, today=TODAY)
        self.assertNotIn("cleanup", out2)
        out3 = rh.run_hourly(cfg, firestore=fs, dry_run=False, store=store, engine=FakeEngine(), cleanup="force", now=NOW, today=TODAY)
        self.assertIn("cleanup", out3)
        self.assertEqual(n.sent, [])           # 실패·상한 없으면 알리지 않음
        self.assertEqual(out["flags"], {"new": 0, "ids": []})

    def test_new_flags_are_in_summary_and_notified_only_when_live(self):
        tmp = self.make_tmp()
        write_attractions(tmp)
        cfg = self.make_cfg(tmp)
        store = FingerprintStore(":memory:")
        self.addCleanup(store.close)
        fs = MemFirestore({
            "plan_flags/r7": {"uid": "u", "reason": "unsafe", "note": "가짜 메모", "at": NOW - dt.timedelta(minutes=20)},
        })

        class N:
            def __init__(self):
                self.sent = []

            def send(self, t):
                self.sent.append(t)
        dry_n = N()
        dry = rh.run_hourly(cfg, firestore=fs, dry_run=True, store=store, engine=FakeEngine(), notifier=dry_n,
                            cleanup="skip", now=NOW, today=TODAY)
        self.assertEqual(dry["flags"], {"new": 1, "ids": ["r7"]})
        self.assertEqual(dry_n.sent, [])
        n = N()
        out = rh.run_hourly(cfg, firestore=fs, dry_run=False, store=store, engine=FakeEngine(), notifier=n,
                            cleanup="skip", now=NOW, today=TODAY)
        self.assertEqual(out["flags"], {"new": 1, "ids": ["r7"]})
        self.assertEqual(n.sent, ["[레디포트] AI 계획 신고 1건: r7"])
        again = rh.run_hourly(cfg, firestore=fs, dry_run=False, store=store, engine=FakeEngine(), notifier=n,
                              cleanup="skip", now=NOW, today=TODAY)
        self.assertEqual(again["flags"]["new"], 0)
        self.assertEqual(len(n.sent), 1)

    def test_flag_check_failure_does_not_break_the_run(self):
        tmp = self.make_tmp()
        write_attractions(tmp)
        cfg = self.make_cfg(tmp)
        store = FingerprintStore(":memory:")
        self.addCleanup(store.close)

        class Broken(MemFirestore):
            def run_query(self, q, parent=""):
                if q["from"][0]["collectionId"] == "plan_flags":
                    raise RuntimeError("boom")
                return super().run_query(q, parent)
        out = rh.run_hourly(cfg, firestore=Broken({"plan_requests/r1": req()}), dry_run=False, store=store,
                            engine=FakeEngine(), cleanup="skip", now=NOW, today=TODAY)
        self.assertEqual(out["plans"]["processed"][0]["status"], "done")
        self.assertEqual(out["flags"]["status"], "error")


if __name__ == "__main__":
    unittest.main()
