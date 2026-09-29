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
