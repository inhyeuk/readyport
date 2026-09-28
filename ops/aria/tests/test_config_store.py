import datetime as dt
import unittest

from ops.aria.config import load_config, parse_env_text
from ops.aria.fingerprint_store import ROLE_MERGE_SYNC, FingerprintStore, StoreError
from ops.aria.models import Change, fingerprint_of
from ops.aria.tests.helpers import TempDirCase


class ConfigTest(unittest.TestCase, TempDirCase):
    def test_parse_env_text(self):
        env = parse_env_text('# 주석\nA=1\nexport B="두 칸"\nC=\'x\'\nD=val # 뒤 주석\n\nNOEQ\nE=\n')
        self.assertEqual(env, {"A": "1", "B": "두 칸", "C": "x", "D": "val", "E": ""})

    def test_environ_overrides_env_file(self):
        tmp = self.make_tmp()
        f = tmp / ".env"
        f.write_text("MOFA_SERVICE_KEY=fromfile\nCLAUDE_DAILY_CAP=7\nNOTICE_URLS=th|https://a.test/x,sg|https://b.test/y\n",
                     encoding="utf-8")
        cfg = load_config(f, environ={"MOFA_SERVICE_KEY": "fromenv"})
        self.assertEqual(cfg.mofa_service_key, "fromenv")
        self.assertEqual(cfg.claude_daily_cap, 7)
        self.assertEqual(cfg.notice_urls, [("th", "https://a.test/x"), ("sg", "https://b.test/y")])

    def test_notice_urls_https_only(self):
        tmp = self.make_tmp()
        with self.assertRaises(ValueError):
            load_config(tmp / "none.env", environ={"NOTICE_URLS": "a|http://x.test"})

    def test_missing_env_file_ok_and_default_url_is_marked(self):
        tmp = self.make_tmp()
        cfg = load_config(tmp / "none.env", environ={})
        self.assertEqual(cfg.mofa_service_key, "")
        self.assertTrue(cfg.mofa_api_url.startswith("https://"))


def ch(unit="TH", diff=None):
    return Change("mofa_entry_diff", unit, "요약", diff or {"a": 1})


class StoreTest(unittest.TestCase, TempDirCase):
    def setUp(self):
        self.tmp = self.make_tmp()
        self.store = FingerprintStore(self.tmp / "s.sqlite")
        self.addCleanup(self.store.close)

    def test_fingerprint_is_stable_and_order_independent(self):
        a = fingerprint_of("d", "u", {"x": 1, "y": [1, 2]})
        b = fingerprint_of("d", "u", {"y": [1, 2], "x": 1})
        self.assertEqual(a, b)
        self.assertEqual(len(a), 64)
        self.assertNotEqual(a, fingerprint_of("d", "u2", {"x": 1, "y": [1, 2]}))

    def test_register_once(self):
        c = ch()
        self.assertTrue(self.store.register(c))
        self.assertFalse(self.store.register(ch()))  # 같은 지문
        self.assertEqual(self.store.get(c.fingerprint)["state"], "draft")

    def test_aria_cannot_set_approved(self):
        c = ch()
        self.store.register(c)
        with self.assertRaises(PermissionError):
            self.store.set_state(c.fingerprint, "approved")
        with self.assertRaises(PermissionError):
            self.store.set_state(c.fingerprint, "published")
        with self.assertRaises(PermissionError):
            self.store.mark_approved_by_merge(c.fingerprint, "https://github.com/o/r/pull/1", "a" * 40)
        self.assertEqual(self.store.get(c.fingerprint)["state"], "draft")

    def test_merge_path_sets_approved_then_published(self):
        c = ch()
        self.store.register(c)
        merge = FingerprintStore(self.tmp / "s.sqlite", role=ROLE_MERGE_SYNC)
        self.addCleanup(merge.close)
        with self.assertRaises(StoreError):
            merge.mark_approved_by_merge(c.fingerprint, "https://github.com/o/r/pull/1", "not-a-sha")
        merge.mark_approved_by_merge(c.fingerprint, "https://github.com/o/r/pull/1", "b" * 40)
        self.assertEqual(self.store.get(c.fingerprint)["state"], "approved")
        # 다시 등록해도 approved 가 draft 로 덮이지 않는다
        self.assertFalse(self.store.register(ch()))
        self.store.set_state(c.fingerprint, "draft")
        self.assertEqual(self.store.get(c.fingerprint)["state"], "approved")
        merge.mark_published(c.fingerprint)
        self.assertEqual(self.store.get(c.fingerprint)["state"], "published")

    def test_review_decided_once(self):
        c = ch()
        self.store.register(c)
        self.assertTrue(self.store.set_review(c.fingerprint, "accepted"))
        self.assertFalse(self.store.set_review(c.fingerprint, "rejected"))
        self.assertEqual(self.store.get(c.fingerprint)["review"], "accepted")

    def test_claude_claim_requires_acceptance_and_runs_once_with_daily_cap(self):
        day = dt.date(2026, 9, 29)
        fps = []
        for i in range(3):
            c = ch(unit=f"U{i}")
            self.store.register(c)
            fps.append(c.fingerprint)
        self.assertEqual(self.store.claim_claude_run(fps[0], 2, day), (False, "not_accepted"))
        for fp in fps:
            self.store.set_review(fp, "accepted")
        self.assertEqual(self.store.claim_claude_run(fps[0], 2, day), (True, "ok"))
        self.assertEqual(self.store.claim_claude_run(fps[0], 2, day), (False, "already_ran"))
        self.assertEqual(self.store.claim_claude_run(fps[1], 2, day), (True, "ok"))
        self.assertEqual(self.store.claim_claude_run(fps[2], 2, day), (False, "daily_cap_reached"))
        self.assertEqual(self.store.claude_calls_today(day), 2)
        # 다음 날은 다시 가능
        self.assertEqual(self.store.claim_claude_run(fps[2], 2, day + dt.timedelta(days=1)), (True, "ok"))

    def test_bad_fingerprint_rejected(self):
        with self.assertRaises(StoreError):
            self.store.get("xyz")

    def test_kv(self):
        self.assertIsNone(self.store.get_value("k"))
        self.store.set_value("k", {"hash": "한글"})
        self.store.set_value("k", {"hash": "2"})
        self.assertEqual(self.store.get_value("k"), {"hash": "2"})

    def test_unnotified(self):
        c = ch()
        self.store.register(c)
        self.assertEqual(len(self.store.list_unnotified()), 1)
        self.store.mark_notified(c.fingerprint)
        self.assertEqual(self.store.list_unnotified(), [])


if __name__ == "__main__":
    unittest.main()
