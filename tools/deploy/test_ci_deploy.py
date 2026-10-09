"""python -m unittest tools/deploy/test_ci_deploy.py"""
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import ci_deploy as cd  # noqa: E402


class CiDeployTest(unittest.TestCase):
    TEMPLATE = {
        "parameters": {
            "index_version": {"defaultValue": {"value": "old"}},
            "pack_version_TH": {"defaultValue": {"value": "old"}},
            "kill_autofill_TH_TDAC": {"defaultValue": {"value": "true"}},
            "stale_banner": {"defaultValue": {"value": "true"}},
        },
        "version": {"versionNumber": "7"},
    }

    def test_only_version_keys_change(self):
        t, changed = cd.apply_versions(self.TEMPLATE, {"index_version": "new", "pack_version_TH": "old", "pack_version_JP": "v"})
        self.assertEqual(changed, ["index_version", "pack_version_JP"])
        # ARIA가 켠 스위치는 그대로
        self.assertEqual(t["parameters"]["kill_autofill_TH_TDAC"]["defaultValue"]["value"], "true")
        self.assertEqual(t["parameters"]["stale_banner"]["defaultValue"]["value"], "true")

    def test_refuses_non_version_keys(self):
        with self.assertRaises(ValueError):
            cd.apply_versions(self.TEMPLATE, {"kill_autofill_TH_TDAC": "false"})
        with self.assertRaises(ValueError):
            cd.apply_versions(self.TEMPLATE, {"min_app_version": "99"})

    def test_repo_versions_are_valid_keys(self):
        v = cd.pack_versions(cd.ROOT / "packs/src")
        self.assertIn("index_version", v)
        self.assertIn("pack_version_TH", v)
        self.assertIn("recipe_version_TH_TDAC", v)
        for k in v:
            self.assertRegex(k, cd.VERSION_KEY_RE)

    def test_changed_countries_and_message(self):
        files = ["packs/src/TH/pack.json", "packs/src/index.json", "app/x.kt", "packs/src/recipes/TH_TDAC.json", "packs/src/JP/pack.json"]
        self.assertEqual(cd.changed_countries(files), ["JP", "TH"])
        m = cd.fcm_message("TH")["message"]
        self.assertEqual(m["topic"], "country_TH")
        self.assertEqual(m["data"], {"country": "TH"})
        self.assertNotIn("notification", m)
        with self.assertRaises(ValueError):
            cd.fcm_message("th/../x")


class BoardReportPurgeTest(unittest.TestCase):
    BASE = "projects/p/databases/(default)/documents/"

    def test_target_path(self):
        self.assertEqual(cd.report_target_path(self.BASE + "board_posts/a/reports/u1"), "board_posts/a")
        self.assertEqual(cd.report_target_path(self.BASE + "board_posts/a/comments/c/reports/u1"), "board_posts/a/comments/c")
        self.assertIsNone(cd.report_target_path(self.BASE + "field_reports/r1"))

    def test_settled(self):
        self.assertTrue(cd.report_settled(None))
        self.assertTrue(cd.report_settled({"hidden": True, "reportCount": 3}))
        self.assertTrue(cd.report_settled({"deleted": True, "reportCount": 2}))
        self.assertTrue(cd.report_settled({"reportCount": 0}))
        self.assertFalse(cd.report_settled({"reportCount": 1, "hidden": False}))

    def test_keeps_pending_and_pages_forward(self):
        base = self.BASE
        reports = [
            {"_name": base + "board_posts/done/reports/u1", "at": "2025-01-01T00:00:00Z"},
            {"_name": base + "board_posts/pending/reports/u2", "at": "2025-02-01T00:00:00Z"},
            {"_name": base + "board_posts/gone/comments/c/reports/u3", "at": "2025-03-01T00:00:00Z"},
        ]
        items = {"board_posts/done": {"reportCount": 0}, "board_posts/pending": {"reportCount": 2, "hidden": False}}

        class FakeFs:
            def __init__(self):
                self.deleted = []
                self.queries = []

            def run_query(self, q):
                self.queries.append(q)
                after = (q.get("startAt") or {}).get("values", [{}])[0].get("timestampValue")
                rows = [r for r in reports if r["_name"] not in self.deleted and (after is None or r["at"] > after)]
                return rows[: q["limit"]]

            def get_document(self, path):
                return items.get(path)

            def delete_document(self, name):
                self.deleted.append(name)

        fs = FakeFs()
        q = cd.board_reports_query("2025-10-01T00:00:00Z", limit=1)
        self.assertEqual(q["from"], [{"collectionId": "reports", "allDescendants": True}])
        n = cd.purge_board_reports(fs, "2025-10-01T00:00:00Z")
        self.assertEqual(n, 2)
        self.assertNotIn(base + "board_posts/pending/reports/u2", fs.deleted)


class PurgeTest(unittest.TestCase):
    def test_purge_deletes_only_what_query_returns(self):
        class FakeFs:
            def __init__(self):
                self.docs = [{"_name": f"projects/p/databases/(default)/documents/field_reports/r{i}"} for i in range(3)]
                self.deleted = []
                self.queries = []

            def run_query(self, q):
                self.queries.append(q)
                return [d for d in self.docs if d["_name"] not in self.deleted]

            def delete_document(self, name):
                self.deleted.append(name)

        fs = FakeFs()
        self.assertEqual(cd.purge_expired(fs, "2027-10-01T00:00:00Z"), 3)
        q = fs.queries[0]
        self.assertEqual(q["from"], [{"collectionId": "field_reports"}])
        self.assertEqual(q["where"]["fieldFilter"]["field"]["fieldPath"], "expire_at")
        self.assertEqual(q["where"]["fieldFilter"]["op"], "LESS_THAN")


if __name__ == "__main__":
    unittest.main()
