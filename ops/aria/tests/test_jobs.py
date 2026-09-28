import datetime as dt
import json
import unittest

from ops.aria.jobs import content_freshness, link_check, ranking, shopping_trend
from ops.aria.net import Response
from ops.aria.tests.helpers import FakeFetcher, TempDirCase, html_resp, json_resp, make_repo, net_error


class RankingTest(unittest.TestCase):
    def test_refuses_without_air(self):
        for air in (None, {}):
            with self.assertRaises(ranking.RankingRefused):
                ranking.compute_ranking(air, air_month="2026-08")

    def test_weights_and_shape(self):
        r = ranking.compute_ranking(
            {"OSA": 100, "BKK": 80, "SIN": 50}, air_month="2026-08", search_week="2026-W39",
            air_prev={"OSA": 100, "BKK": 60}, search={"BKK": 100, "OSA": 50},
            search_prev={"BKK": 50, "OSA": 50}, favorites={"SIN": 10, "OSA": 5, "ZZZ": 99},
            meta={"OSA": {"visa_free_kr": True, "flight_hours": 1.8}},
            prev_ranking={"items": [{"city_id": "OSA", "rank": 1}]}, today=dt.date(2026, 9, 29))
        self.assertEqual(r["version"], "2026-W40")
        self.assertEqual(r["weights"], {"air": 0.5, "search": 0.3, "favorites": 0.2})
        self.assertEqual(r["basis"], {"air_month": "2026-08", "search_week": "2026-W39"})
        by = {i["city_id"]: i for i in r["items"]}
        self.assertNotIn("ZZZ", by)  # 항공 자료 없는 도시는 넣지 않음
        # OSA: 0.5*1 + 0.3*0.5 + 0.2*0.5 = 0.75 / BKK: 0.5*0.8 + 0.3*1 + 0 = 0.7 / SIN: 0.25 + 0 + 0.2 = 0.45
        self.assertEqual([i["city_id"] for i in r["items"]], ["OSA", "BKK", "SIN"])
        self.assertAlmostEqual(by["OSA"]["score"], 0.75)
        self.assertAlmostEqual(by["BKK"]["score"], 0.7)
        self.assertEqual(by["BKK"]["reasons"], ["passenger_up", "search_rising"])
        self.assertEqual(by["OSA"]["prev_rank"], 1)
        self.assertIsNone(by["BKK"]["prev_rank"])
        self.assertIsNone(by["SIN"]["visa_free_kr"])
        self.assertEqual(set(r["items"][0]), {"city_id", "rank", "prev_rank", "score", "reasons", "visa_free_kr",
                                              "flight_hours"})


class LinkCheckTest(unittest.TestCase, TempDirCase):
    def test_collect_and_check(self):
        tmp = self.make_tmp()
        make_repo(tmp, forms=[("TH", "TH_TDAC", "https://form.example.test/th")], extra_pack={"TH": {
            "essentials_rules": [{"id": "x", "link": {"url": "https://shop.example.test/a"}}],
            "return_links": [{"url": "https://ret.example.test/gone"}],
            "patterns": ["https://form.example.test/*"],
            "bad": "[확인 필요]"}})
        urls = link_check.collect_urls(tmp)
        self.assertIn("https://shop.example.test/a", urls)
        self.assertIn("https://ret.example.test/gone", urls)
        self.assertNotIn("https://form.example.test/*", urls)
        f = FakeFetcher(routes={
            ("HEAD", "https://form.example.test/th"): Response(405),
            ("GET", "https://form.example.test/th"): html_resp("<div class='cf-turnstile'></div>", 403),
            ("HEAD", "https://shop.example.test/a"): Response(200),
            ("HEAD", "https://ret.example.test/gone"): Response(404),
            ("HEAD", "https://source.example.test/"): net_error("timed out"),
            ("HEAD", "https://source.example.test/TH"): Response(301),
        })
        sleeps = []
        out = link_check.run(tmp, f, interval_sec=1, sleep=sleeps.append)
        res = {r["url"]: r["result"] for r in out["results"]}
        self.assertEqual(res["https://form.example.test/th"], "manual_check_needed")
        self.assertEqual(res["https://shop.example.test/a"], "ok")
        self.assertEqual(res["https://ret.example.test/gone"], "broken")
        self.assertEqual(res["https://source.example.test/"], "error")
        self.assertEqual(res["https://source.example.test/TH"], "ok")
        self.assertEqual(len(sleeps), out["checked"] - 1)
        self.assertTrue(all(c["method"] in ("HEAD", "GET") and c["data"] is None for c in f.calls))


class FreshnessTest(unittest.TestCase, TempDirCase):
    def test_find_stale(self):
        tmp = self.make_tmp()
        make_repo(tmp, forms=[("TH", "TH_TDAC", "https://f.test/")], extra_pack={"TH": {
            "payment": [{"id": "old_card", "last_verified": "2026-01-01"},
                        {"id": "bad_date", "last_verified": "어제"}]}})
        stale = content_freshness.find_stale(tmp, today=dt.date(2026, 9, 29), max_age_days=90)
        ids = {s["id"]: s for s in stale}
        self.assertIn("old_card", ids)
        self.assertEqual(ids["old_card"]["age_days"], 271)
        self.assertIn("bad_date", ids)
        self.assertNotIn("TH_TDAC", ids)  # 2026-09-01 은 28일
        self.assertEqual(len(content_freshness.find_stale(tmp, today=dt.date(2026, 9, 29), max_age_days=10)), 4)


class ShoppingTrendTest(unittest.TestCase):
    def test_not_configured(self):
        f = FakeFetcher()
        r = shopping_trend.fetch_relative_trend({"mango": ["말린 망고"]}, "2026-08-01", "2026-09-01", fetcher=f)
        self.assertEqual(r["status"], "not_configured")
        self.assertEqual(f.calls, [])

    def test_ok_chunks(self):
        def route(method, url, headers, data):
            body = json.loads(data)
            return json_resp({"results": [{"title": g["groupName"], "data": [{"period": "2026-08-01", "ratio": 42.0}]}
                                          for g in body["keywordGroups"]]})
        f = FakeFetcher(default=route)
        groups = {f"g{i}": [f"k{i}"] for i in range(7)}
        r = shopping_trend.fetch_relative_trend(groups, "2026-08-01", "2026-09-01", client_id="id",
                                                client_secret="sec", fetcher=f)
        self.assertEqual(r["status"], "ok")
        self.assertEqual(len(f.calls), 2)
        self.assertEqual(r["latest_ratio"]["g6"], 42.0)
        self.assertEqual(f.calls[0]["headers"]["X-Naver-Client-Id"], "id")

    def test_http_error(self):
        f = FakeFetcher(default=Response(401, b"no"))
        r = shopping_trend.fetch_relative_trend({"a": ["b"]}, "x", "y", client_id="i", client_secret="s", fetcher=f)
        self.assertEqual(r["status"], "error")


if __name__ == "__main__":
    unittest.main()
