import contextlib
import datetime as dt
import io
import json
import threading
import unittest

from ops.aria import run_daily as rd
from ops.aria.fingerprint_store import FingerprintStore
from ops.aria.models import STATUS_DEFERRED, STATUS_ERROR, STATUS_TIMEOUT, STATUS_UNCHANGED, UnitResult
from ops.aria.tests.helpers import FakeFetcher, TempDirCase, html_resp, json_resp, make_repo, net_error
from ops.aria.tests.test_detectors import JP, TH, mofa_payload
from ops.aria.tests.test_signals_actions import FakeRC, load_template, rep, NOW
from ops.aria.actions.kill_switch import RemoteConfigClient
from ops.aria.gcp import StaticTokenProvider

FORM_V1 = "<form><input name='surname'></form>"
FORM_V2 = "<form><input name='surname'><input name='nationality'></form>"


class ListNotifier:
    def __init__(self, fail=False):
        self.sent, self.fail = [], fail

    def send(self, text):
        if self.fail:
            raise RuntimeError("telegram down")
        self.sent.append(text)


class FakeFirestore:
    def __init__(self, reports=()):
        self.reports = list(reports)
        self.docs = {}

    def run_query(self, q):
        return self.reports

    def set_document(self, path, data):
        self.docs[path] = data


class RunUnitsTest(unittest.TestCase):
    def test_timeout_does_not_block_others(self):
        gate = threading.Event()
        self.addCleanup(gate.set)
        units = [rd.Unit("slow", lambda: gate.wait(5) and UnitResult("slow", STATUS_UNCHANGED)),
                 rd.Unit("fast", lambda: UnitResult("fast", STATUS_UNCHANGED))]
        res = rd.run_units(units, unit_timeout=0.2, retries=0, budget_sec=30)
        self.assertEqual(res["slow"].status, STATUS_TIMEOUT)
        self.assertEqual(res["fast"].status, STATUS_UNCHANGED)

    def test_exception_is_error_and_retried_only_failures(self):
        calls = {"bad": 0, "manual": 0, "ok": 0}

        def bad():
            calls["bad"] += 1
            raise RuntimeError("boom")

        def manual():
            calls["manual"] += 1
            return UnitResult("manual", "manual_check_needed")

        def ok():
            calls["ok"] += 1
            return UnitResult("ok", STATUS_UNCHANGED)
        res = rd.run_units([rd.Unit("bad", bad), rd.Unit("manual", manual), rd.Unit("ok", ok)],
                           unit_timeout=2, retries=2, budget_sec=30)
        self.assertEqual(res["bad"].status, STATUS_ERROR)
        self.assertEqual(calls, {"bad": 3, "manual": 1, "ok": 1})

    def test_budget_defers(self):
        t = [0.0]

        def clock():
            t[0] += 10
            return t[0]
        units = [rd.Unit(f"u{i}", lambda: UnitResult("x", STATUS_UNCHANGED)) for i in range(5)]
        res = rd.run_units(units, unit_timeout=1, retries=0, budget_sec=25, clock=clock)
        self.assertEqual([res[f"u{i}"].status for i in range(5)],
                         [STATUS_UNCHANGED, STATUS_UNCHANGED, STATUS_DEFERRED, STATUS_DEFERRED, STATUS_DEFERRED])


class RunDailyTest(unittest.TestCase, TempDirCase):
    def setUp(self):
        self.tmp = self.make_tmp()
        make_repo(self.tmp)
        self.cfg = self.make_cfg(self.tmp)
        self.store = FingerprintStore(self.cfg.db_path)
        self.addCleanup(self.store.close)
        self.pages = {"https://form.example.test/th": html_resp(FORM_V1),
                      "https://form.example.test/sg": html_resp("<div class='cf-turnstile'></div>")}
        self.mofa_items = [TH, JP]

    def fetcher(self):
        routes = dict(self.pages)
        routes["https://mofa.example.test/api"] = json_resp(mofa_payload(self.mofa_items))
        return FakeFetcher(routes=routes)

    def go(self, day, **kw):
        kw.setdefault("dry_run", False)
        f = kw.pop("fetcher", None) or self.fetcher()
        out = rd.run_daily(self.cfg, fetcher=f, store=self.store, today=day, sleep=lambda s: None, **kw)
        return out, f

    def test_dry_run_is_default_and_writes_nothing(self):
        n = ListNotifier()
        with contextlib.redirect_stdout(io.StringIO()):
            out = rd.run_daily(self.cfg, fetcher=self.fetcher(), store=self.store, notifier=n,
                               today=dt.date(2026, 9, 28), sleep=lambda s: None)
        self.assertTrue(out["dry_run"])
        self.assertEqual(n.sent, [])  # 주입된 알림으로 보내지 않음
        self.assertFalse(self.cfg.snapshot_dir.exists())
        self.assertFalse((self.cfg.data_dir / "runs").exists())
        self.assertIsNone(self.store.get_value("form_hash:TH_TDAC"))

    def test_live_flow_notifies_once(self):
        n = ListNotifier()
        fs = FakeFirestore()
        out, _ = self.go(dt.date(2026, 9, 28), notifier=n, firestore=fs, now=NOW)
        self.assertEqual(out["units"]["mofa"]["status"], "baseline")
        self.assertEqual(out["units"]["form:TH_TDAC"]["status"], "baseline")
        self.assertEqual(out["units"]["form:SG_SGAC"]["status"], "manual_check_needed")
        self.assertEqual(out["units"]["field_reports"]["status"], "unchanged")
        self.assertTrue(out["heartbeat"])
        self.assertIn("form:SG_SGAC", fs.docs["ops/heartbeat"]["jobs"])
        self.assertEqual(n.sent, [])

        self.pages["https://form.example.test/th"] = html_resp(FORM_V2)
        self.mofa_items = [dict(TH, visa="30일"), JP]
        out, f = self.go(dt.date(2026, 9, 29), notifier=n)
        self.assertEqual(len(out["notified"]), 2)
        self.assertEqual(len(n.sent), 2)
        self.assertTrue(all("CMD:v1" in s for s in n.sent))
        # SG 는 차단 화면 → 한 번만 요청(재시도 없음)
        self.assertEqual(sum(1 for c in f.calls if c["url"] == "https://form.example.test/sg"), 1)
        self.assertTrue(all(c["method"] == "GET" for c in f.calls))

        # 같은 날 다시 돌려도 같은 지문이라 다시 알리지 않는다
        out, _ = self.go(dt.date(2026, 9, 29), notifier=n)
        self.assertEqual(out["notified"], [])
        self.assertEqual(len(n.sent), 2)

    def test_summarizer_failure_still_sends_raw_diff(self):
        self.go(dt.date(2026, 9, 28), notifier=ListNotifier())
        self.mofa_items = [dict(TH, visa="30일"), JP]

        def broken_summarizer(ch):
            raise RuntimeError("Gemini quota exceeded")
        n = ListNotifier()
        self.go(dt.date(2026, 9, 29), notifier=n, summarizer=broken_summarizer, only=["mofa"])
        self.assertEqual(len(n.sent), 1)
        self.assertIn("30일", n.sent[0])
        self.assertNotIn("AI 요약", n.sent[0])

    def test_failed_notification_is_resent_next_run(self):
        self.go(dt.date(2026, 9, 28), notifier=ListNotifier(), only=["mofa"])
        self.mofa_items = [dict(TH, visa="30일"), JP]
        self.go(dt.date(2026, 9, 29), notifier=ListNotifier(fail=True), only=["mofa"])
        self.assertEqual(len(self.store.list_unnotified()), 1)
        n = ListNotifier()
        out, _ = self.go(dt.date(2026, 9, 29), notifier=n, only=["mofa"])
        self.assertEqual(len(n.sent), 1)
        self.assertIn("재전송", n.sent[0])
        self.assertEqual(self.store.list_unnotified(), [])

    def test_retry_failed_runs_only_failed_units(self):
        self.pages["https://form.example.test/th"] = net_error()
        out, _ = self.go(dt.date(2026, 9, 28), notifier=ListNotifier(), retries=0)
        self.assertEqual(out["rerun_later"], ["form:TH_TDAC"])
        self.pages["https://form.example.test/th"] = html_resp(FORM_V1)
        out, f = self.go(dt.date(2026, 9, 28), notifier=ListNotifier(), retry_failed=True)
        self.assertEqual(list(out["units"]), ["form:TH_TDAC"])
        self.assertEqual([c["url"] for c in f.calls], ["https://form.example.test/th"])
        state = json.loads((self.cfg.data_dir / "runs" / "2026-09-28.json").read_text(encoding="utf-8"))
        self.assertEqual(state["form:TH_TDAC"], "baseline")
        self.assertEqual(state["form:SG_SGAC"], "manual_check_needed")

    def test_field_reports_turn_kill_switch_on(self):
        rc = FakeRC(load_template())
        client = RemoteConfigClient("proj", StaticTokenProvider(), rc)
        fs = FakeFirestore([rep() for _ in range(6)])
        n = ListNotifier()
        out, _ = self.go(dt.date(2026, 9, 28), notifier=n, firestore=fs, rc_client=client, now=NOW,
                          only=["field_reports"])
        self.assertEqual(rc.template["parameters"]["kill_autofill_TH_TDAC"]["defaultValue"]["value"], "true")
        self.assertEqual(len(rc.puts), 1)
        self.assertTrue(any("자동 입력 끔" in s for s in n.sent))
        # 시험 실행이면 PUT 하지 않는다
        rc2 = FakeRC(load_template())
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            rd.run_daily(self.cfg, fetcher=FakeFetcher(), store=self.store, firestore=fs,
                         rc_client=RemoteConfigClient("proj", StaticTokenProvider(), rc2), now=NOW,
                         only=["field_reports"], today=dt.date(2026, 9, 28))
        self.assertEqual(rc2.puts, [])
        self.assertIn("[시험]", buf.getvalue())

    def test_news_unit(self):
        n = ListNotifier()
        items = [{"title": "TDAC update", "url": "https://n.test/1", "ts": "t"}, {"title": "weather", "url": "u", "ts": "t"}]
        out, _ = self.go(dt.date(2026, 9, 28), notifier=n, only=["news"], news_items=items)
        self.assertEqual(out["units"]["news"]["changes"], 1)
        self.assertIn("입국정책 뉴스", n.sent[0])
        out, _ = self.go(dt.date(2026, 9, 28), notifier=n, only=["news"], news_items=items)
        self.assertEqual(len(n.sent), 1)


if __name__ == "__main__":
    unittest.main()
