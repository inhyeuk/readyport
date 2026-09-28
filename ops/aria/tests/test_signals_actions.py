import datetime as dt
import json
import pathlib
import unittest

from ops.aria.actions import kill_switch as ks
from ops.aria.gcp import FirestoreRest, StaticTokenProvider, from_fs_value, parse_timestamp, to_fs_value
from ops.aria.heartbeat import check_stale, write_heartbeat
from ops.aria.net import Response
from ops.aria.signals import field_reports as fr
from ops.aria.tests.helpers import FakeFetcher, json_resp

REPO = pathlib.Path(__file__).resolve().parents[3]
NOW = dt.datetime(2026, 9, 29, 12, 0, tzinfo=dt.timezone.utc)


def rep(form_id="TH_TDAC", code="missing", hours_ago=1, step="personal"):
    return {"form_id": form_id, "pack_version": "2026.09.28-2", "step_id": step, "error_code": code,
            "app_version": "1", "ts": NOW - dt.timedelta(hours=hours_ago)}


class FieldReportsTest(unittest.TestCase):
    def test_aggregate_filters_window_and_bad_form_ids(self):
        reports = [rep() for _ in range(3)] + [rep(hours_ago=30), rep(form_id="bad id"),
                                                rep(form_id="<script>"), rep(code="ok")]
        stats = fr.aggregate(reports, NOW - dt.timedelta(hours=24), NOW)
        self.assertEqual(list(stats), ["TH_TDAC"])
        s = stats["TH_TDAC"]
        self.assertEqual((s.total, s.failures), (4, 3))
        self.assertEqual(s.by_error["missing"], 3)

    def test_decide_thresholds(self):
        since = NOW - dt.timedelta(hours=24)
        few = fr.aggregate([rep() for _ in range(3)], since)
        self.assertFalse(fr.decide(few, min_samples=5, min_failures=5)[0].kill)
        many = fr.aggregate([rep() for _ in range(6)], since)
        d = fr.decide(many, min_samples=5, min_failures=5, fail_rate=0.5)[0]
        self.assertTrue(d.kill)
        mostly_ok = fr.aggregate([rep() for _ in range(5)] + [rep(code="ok") for _ in range(20)], since)
        d = fr.decide(mostly_ok, min_samples=5, min_failures=5, fail_rate=0.5)[0]
        self.assertFalse(d.kill)
        self.assertIn("실패율", d.reason)

    def test_ts_formats(self):
        since = NOW - dt.timedelta(hours=24)
        ms = int((NOW - dt.timedelta(hours=1)).timestamp() * 1000)
        stats = fr.aggregate([dict(rep(), ts=ms), dict(rep(), ts="2026-09-29T11:00:00.123456789Z"),
                              dict(rep(), ts="garbage")], since)
        self.assertEqual(stats["TH_TDAC"].total, 2)

    def test_fetch_and_decide_via_firestore_rest(self):
        docs = [{"document": {"name": f"projects/p/databases/(default)/documents/field_reports/{i}",
                              "fields": {k: to_fs_value(v) for k, v in rep().items()}}} for i in range(6)]
        docs.append({"readTime": "x"})  # 문서 없는 줄
        f = FakeFetcher(default=json_resp(docs))
        fs = FirestoreRest("proj", StaticTokenProvider("tok"), f)
        decisions = fr.fetch_and_decide(fs, NOW)
        self.assertTrue(decisions[0].kill)
        call = f.calls[0]
        self.assertEqual(call["method"], "POST")
        self.assertTrue(call["url"].endswith("/documents:runQuery"))
        self.assertEqual(call["headers"]["Authorization"], "Bearer tok")
        q = json.loads(call["data"])["structuredQuery"]
        self.assertEqual(q["from"], [{"collectionId": "field_reports"}])

    def test_fs_value_roundtrip(self):
        v = {"a": 1, "b": 1.5, "c": True, "d": None, "e": ["x"], "f": NOW, "g": {"h": "i"}}
        back = {k: from_fs_value(to_fs_value(x)) for k, x in v.items()}
        self.assertEqual(back, v)
        with self.assertRaises(ValueError):
            to_fs_value(dt.datetime(2026, 1, 1))
        self.assertEqual(parse_timestamp("2026-09-29T12:00:00Z"), NOW)


def load_template():
    return json.loads((REPO / "firebase" / "remoteconfig.template.json").read_text(encoding="utf-8"))


class FakeRC:
    """Remote Config REST 흉내. GET 은 ETag 를 주고, PUT 은 If-Match 가 맞아야 받는다."""

    def __init__(self, template):
        self.template = template
        self.etag = "etag-1"
        self.puts = []

    def __call__(self, method, url, headers=None, data=None, timeout=20):
        assert url == "https://firebaseremoteconfig.googleapis.com/v1/projects/proj/remoteConfig", url
        if method == "GET":
            return json_resp(self.template, headers={"ETag": self.etag})
        if method == "PUT":
            if headers.get("If-Match") != self.etag:
                return Response(412, b"precondition")
            self.puts.append(json.loads(data))
            self.template = json.loads(data)
            self.etag = f"etag-{len(self.puts) + 1}"
            return json_resp(self.template, headers={"ETag": self.etag})
        raise AssertionError(method)


class KillSwitchTest(unittest.TestCase):
    def setUp(self):
        self.rc = FakeRC(load_template())
        self.client = ks.RemoteConfigClient("proj", StaticTokenProvider(), self.rc)

    def test_allowlist(self):
        for bad in ["min_app_version", "pack_version_TH", "index_version", "recipe_version_TH_TDAC",
                    "kill_autofill_", "kill_autofill_th_tdac", "kill_autofill_TH_TDAC;x", "stale_banner"]:
            with self.assertRaises(ks.NotAllowed, msg=bad):
                ks.assert_allowed_key(bad)
        ks.assert_allowed_key("kill_autofill_TH_TDAC")
        ks.assert_allowed_key("stale_banner", allow_stale=True)
        with self.assertRaises(ks.NotAllowed):
            ks.apply_bool(load_template(), "min_app_version", True)
        with self.assertRaises(ks.NotAllowed):
            ks.kill_autofill_on(self.client, "TH-TDAC")
        self.assertEqual(self.rc.puts, [])

    def test_kill_on_changes_only_that_key(self):
        before = load_template()
        r = ks.kill_autofill_on(self.client, "TH_TDAC")
        self.assertTrue(r.changed)
        self.assertEqual(len(self.rc.puts), 1)
        after = self.rc.puts[0]
        self.assertEqual(after["parameters"]["kill_autofill_TH_TDAC"]["defaultValue"]["value"], "true")
        del after["parameters"]["kill_autofill_TH_TDAC"]
        del before["parameters"]["kill_autofill_TH_TDAC"]
        self.assertEqual(after, before)
        # 이미 켜져 있으면 PUT 안 함
        r = ks.kill_autofill_on(self.client, "TH_TDAC")
        self.assertFalse(r.changed)
        self.assertEqual(len(self.rc.puts), 1)

    def test_kill_on_new_key_and_group_and_conditionals(self):
        t = load_template()
        t["parameterGroups"] = {"g": {"parameters": {"kill_autofill_JP_VJW": {
            "defaultValue": {"value": "false"}, "conditionalValues": {"old_app": {"value": "false"}},
            "valueType": "BOOLEAN"}}}}
        self.rc.template = t
        ks.kill_autofill_on(self.client, "JP_VJW")
        p = self.rc.template["parameterGroups"]["g"]["parameters"]["kill_autofill_JP_VJW"]
        self.assertEqual(p["defaultValue"]["value"], "true")
        self.assertEqual(p["conditionalValues"]["old_app"]["value"], "true")
        self.assertNotIn("kill_autofill_JP_VJW", self.rc.template["parameters"])
        ks.kill_autofill_on(self.client, "VN_EVISA")
        self.assertEqual(self.rc.template["parameters"]["kill_autofill_VN_EVISA"]["valueType"], "BOOLEAN")

    def test_kill_off_requires_human(self):
        ks.kill_autofill_on(self.client, "TH_TDAC")
        with self.assertRaises(ks.NotAllowed):
            ks.kill_autofill_off(self.client, "TH_TDAC")
        with self.assertRaises(ks.NotAllowed):
            ks.kill_autofill_off(self.client, "TH_TDAC", approved_by_human="yes")  # True 만 인정
        r = ks.kill_autofill_off(self.client, "TH_TDAC", approved_by_human=True)
        self.assertTrue(r.changed)
        self.assertEqual(self.rc.template["parameters"]["kill_autofill_TH_TDAC"]["defaultValue"]["value"], "false")

    def test_dry_run_no_put(self):
        r = ks.kill_autofill_on(self.client, "TH_TDAC", dry_run=True)
        self.assertTrue(r.changed and r.dry_run)
        self.assertEqual(self.rc.puts, [])

    def test_stale_banner(self):
        ks.set_stale_banner(self.client, True)
        self.assertEqual(self.rc.template["parameters"]["stale_banner"]["defaultValue"]["value"], "true")
        with self.assertRaises(ks.NotAllowed):
            ks.set_stale_banner(self.client, False)
        ks.set_stale_banner(self.client, False, heartbeat_recovered=True)
        self.assertEqual(self.rc.template["parameters"]["stale_banner"]["defaultValue"]["value"], "false")

    def test_missing_etag_refuses(self):
        def no_etag(method, url, headers=None, data=None, timeout=20):
            return json_resp(load_template())
        client = ks.RemoteConfigClient("proj", StaticTokenProvider(), no_etag)
        with self.assertRaises(Exception):
            ks.kill_autofill_on(client, "TH_TDAC")


class FakeFirestore:
    def __init__(self):
        self.docs = {}

    def set_document(self, path, data):
        self.docs[path] = data


class HeartbeatTest(unittest.TestCase):
    def test_check_stale(self):
        self.assertTrue(check_stale(None, NOW))
        self.assertFalse(check_stale(NOW - dt.timedelta(days=2), NOW))
        self.assertTrue(check_stale(NOW - dt.timedelta(days=3, seconds=1), NOW))
        self.assertFalse(check_stale("2026-09-28T12:00:00Z", "2026-09-29T12:00:00Z"))
        self.assertTrue(check_stale("2026-09-20T00:00:00Z", NOW, days=3))

    def test_write(self):
        fs = FakeFirestore()
        write_heartbeat(fs, {"mofa": "unchanged"}, NOW)
        self.assertEqual(fs.docs["ops/heartbeat"], {"last_check": NOW, "jobs": {"mofa": "unchanged"}})

    def test_write_via_rest(self):
        f = FakeFetcher(default=json_resp({"name": "x", "fields": {}}))
        fs = FirestoreRest("proj", StaticTokenProvider(), f)
        write_heartbeat(fs, {"a": "b"}, NOW)
        call = f.calls[0]
        self.assertEqual(call["method"], "PATCH")
        self.assertIn("/documents/ops/heartbeat?updateMask.fieldPaths=last_check", call["url"])
        body = json.loads(call["data"])
        self.assertEqual(body["fields"]["last_check"], {"timestampValue": "2026-09-29T12:00:00Z"})


if __name__ == "__main__":
    unittest.main()


class GzipBodyTest(unittest.TestCase):
    """Remote Config는 gzip으로 요청해야 ETag를 준다 — 응답 본문은 우리가 푼다."""

    def test_decode_gzip_body(self):
        import gzip as _gz
        from ops.aria.net import decode_body
        raw = b'{"parameters": {}}'
        self.assertEqual(decode_body(_gz.compress(raw), {"Content-Encoding": "gzip"}), raw)
        self.assertEqual(decode_body(raw, {}), raw)
        # 헤더만 gzip이고 본문이 아니면 그대로
        self.assertEqual(decode_body(raw, {"content-encoding": "gzip"}), raw)

    def test_rc_requests_ask_for_gzip(self):
        from ops.aria.actions.kill_switch import RemoteConfigClient
        from ops.aria.gcp import StaticTokenProvider
        from ops.aria.net import Response
        seen = []

        def fetch(method, url, headers=None, data=None, timeout=20.0):
            seen.append((method, headers))
            return Response(200, b'{"parameters": {}}', {"ETag": "etag-1"}, url)

        rc = RemoteConfigClient("p", StaticTokenProvider(), fetch)
        _, etag = rc.get()
        rc.put({"parameters": {}}, etag)
        self.assertEqual(etag, "etag-1")
        self.assertTrue(all(h.get("Accept-Encoding") == "gzip" for _, h in seen))

