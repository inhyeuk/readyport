import datetime as dt
import json
import pathlib
import unittest

from ops.aria.detectors import form_structure_hash as fsh
from ops.aria.detectors import mofa_entry_diff as mofa
from ops.aria.detectors import news_keywords as news
from ops.aria.detectors import notice_watch as nw
from ops.aria.fingerprint_store import FingerprintStore
from ops.aria.models import STATUS_BASELINE, STATUS_CHANGED, STATUS_ERROR, STATUS_MANUAL, STATUS_NOT_CONFIGURED, \
    STATUS_UNCHANGED
from ops.aria.net import Response, looks_like_bot_challenge, redact_url
from ops.aria.tests.helpers import FakeFetcher, TempDirCase, html_resp, json_resp, net_error


def mofa_payload(items, total=None):
    return {"response": {"header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                         "body": {"items": {"item": items}, "totalCount": total if total is not None else len(items)}}}


TH = {"country_iso_alp2": "TH", "country_nm": "태국", "visa": "<p>90일 무비자</p>", "rnum": "1"}
JP = {"country_iso_alp2": "JP", "country_nm": "일본", "visa": "90일 무비자", "rnum": "2"}
US = {"country_iso_alp2": "US", "country_nm": "미국", "visa": "ESTA", "rnum": "3"}


class MofaTest(unittest.TestCase, TempDirCase):
    def setUp(self):
        self.tmp = self.make_tmp()
        self.cfg = self.make_cfg(self.tmp)

    def run_with(self, items, day, countries=("TH", "JP"), persist=True):
        f = FakeFetcher(default=json_resp(mofa_payload(items)))
        return mofa.run(self.cfg, f, day, list(countries), persist), f

    def test_baseline_then_change(self):
        r, f = self.run_with([TH, JP, US], dt.date(2026, 9, 28))
        self.assertEqual(r.status, STATUS_BASELINE)
        self.assertIn("serviceKey=TESTKEY", f.calls[0]["url"])
        self.assertEqual(f.calls[0]["method"], "GET")
        snap = json.loads(pathlib.Path(r.evidence_path).read_text(encoding="utf-8"))
        self.assertEqual(set(snap), {"TH", "JP"})  # 관심 나라만
        self.assertEqual(snap["TH"]["visa"], "90일 무비자")  # 태그 제거
        self.assertNotIn("rnum", snap["TH"])

        r2, _ = self.run_with([dict(TH, rnum="9"), JP], dt.date(2026, 9, 29))
        self.assertEqual(r2.status, STATUS_UNCHANGED)  # rnum 만 바뀐 것은 무시

        changed = dict(TH, visa="30일 무비자")
        r3, _ = self.run_with([changed, JP], dt.date(2026, 9, 30))
        self.assertEqual(r3.status, STATUS_CHANGED)
        self.assertEqual(len(r3.changes), 1)
        c = r3.changes[0]
        self.assertEqual(c.unit, "TH")
        self.assertEqual(c.diff["diff"]["changed"]["visa"], {"before": "90일 무비자", "after": "30일 무비자"})
        self.assertTrue(pathlib.Path(c.evidence_path).exists())
        self.assertNotIn("TESTKEY", pathlib.Path(c.evidence_path).read_text(encoding="utf-8"))
        # 같은 날 다시 돌려도 같은 지문 (멱등)
        r4, _ = self.run_with([changed, JP], dt.date(2026, 9, 30))
        self.assertEqual(r4.changes[0].fingerprint, c.fingerprint)

    def test_dry_run_writes_nothing(self):
        r, _ = self.run_with([TH], dt.date(2026, 9, 28), persist=False)
        self.assertEqual(r.status, STATUS_BASELINE)
        self.assertFalse((self.cfg.snapshot_dir / "mofa").exists())

    def test_empty_response_is_error_not_mass_removal(self):
        self.run_with([TH, JP], dt.date(2026, 9, 28))
        r, _ = self.run_with([], dt.date(2026, 9, 29))
        self.assertEqual(r.status, STATUS_ERROR)

    def test_not_configured(self):
        self.cfg.mofa_service_key = ""
        r = mofa.run(self.cfg, FakeFetcher(), dt.date(2026, 9, 28))
        self.assertEqual(r.status, STATUS_NOT_CONFIGURED)

    def test_api_error_code_and_http_error_hide_key(self):
        bad = {"response": {"header": {"resultCode": "30", "resultMsg": "SERVICE KEY IS NOT REGISTERED"}}}
        r = mofa.run(self.cfg, FakeFetcher(default=json_resp(bad)), dt.date(2026, 9, 28))
        self.assertEqual(r.status, STATUS_ERROR)
        r = mofa.run(self.cfg, FakeFetcher(default=Response(500, b"")), dt.date(2026, 9, 28))
        self.assertEqual(r.status, STATUS_ERROR)
        self.assertNotIn("TESTKEY", r.message)
        r = mofa.run(self.cfg, FakeFetcher(default=net_error()), dt.date(2026, 9, 28))
        self.assertEqual(r.status, STATUS_ERROR)

    def test_pagination(self):
        pages = {1: mofa_payload([TH], total=2), 2: mofa_payload([JP], total=2)}

        def route(method, url, headers, data):
            page = int(url.split("pageNo=")[1].split("&")[0])
            return json_resp(pages[page])
        items = mofa.fetch_items("https://mofa.example.test/api", "K", FakeFetcher(default=route))
        self.assertEqual(len(items), 2)

    def test_xml_and_odcloud_shapes(self):
        xml = ("<response><header><resultCode>00</resultCode></header><body><items>"
               "<item><country_iso_alp2>TH</country_iso_alp2><visa>90</visa></item></items>"
               "<totalCount>1</totalCount></body></response>")
        items, total = mofa.parse_payload(xml)
        self.assertEqual(items, [{"country_iso_alp2": "TH", "visa": "90"}])
        self.assertEqual(total, 1)
        items, total = mofa.parse_payload(json.dumps({"currentCount": 1, "data": [TH], "totalCount": 1}))
        self.assertEqual(len(items), 1)

    def test_diff_added_removed(self):
        d = mofa.diff_snapshots({"A": {"x": "1"}}, {"B": {"x": "1"}})
        self.assertIn("removed_country", d["A"])
        self.assertIn("added_country", d["B"])

    def test_redact(self):
        self.assertEqual(redact_url("https://x?serviceKey=abc&pageNo=1"), "https://x?serviceKey=***&pageNo=1")


PAGE_V1 = """<html><head><title>t</title><script nonce="abc123">var csrf='zzz';</script>
<meta name="csrf-token" content="111"></head><body><h1>Arrival card 안내</h1>
<form id="f1"><input type="text" name="surname" id="mat-input-3" value="x">
<input type="hidden" name="_csrf" value="token-1">
<select formcontrolname="purpose"><option>A</option></select>
<button type="submit">Next</button></form></body></html>"""


class FormStructureTest(unittest.TestCase, TempDirCase):
    def setUp(self):
        self.tmp = self.make_tmp()
        self.cfg = self.make_cfg(self.tmp)
        self.store = FingerprintStore(self.tmp / "s.sqlite")
        self.addCleanup(self.store.close)

    def check(self, body, status=200, persist=True):
        f = FakeFetcher(default=html_resp(body, status))
        r = fsh.check_form("TH_TDAC", "https://form.example.test/th", self.store, f, self.cfg, persist)
        return r, f

    def test_hash_ignores_text_scripts_values_and_digit_ids(self):
        a = fsh.structure_hash(fsh.extract_structure(PAGE_V1))
        b_html = (PAGE_V1.replace("안내", "공지 바뀜").replace("abc123", "NEW").replace("token-1", "token-2")
                  .replace("mat-input-3", "mat-input-7").replace("var csrf='zzz'", "var csrf='yyy'")
                  .replace("<option>A</option>", "<option>A</option><option>B</option>")
                  .replace('content="111"', 'content="222"'))
        b = fsh.structure_hash(fsh.extract_structure(b_html))
        self.assertEqual(a, b)

    def test_baseline_unchanged_changed_and_get_only(self):
        r, f = self.check(PAGE_V1)
        self.assertEqual(r.status, STATUS_BASELINE)
        r, _ = self.check(PAGE_V1)
        self.assertEqual(r.status, STATUS_UNCHANGED)
        v2 = PAGE_V1.replace('<button type="submit">', '<input type="text" name="nationality"><button type="submit">')
        r, f = self.check(v2)
        self.assertEqual(r.status, STATUS_CHANGED)
        c = r.changes[0]
        self.assertTrue(any("nationality" in x for x in c.diff["controls_added"]))
        self.assertTrue(pathlib.Path(c.evidence_path).exists())
        # 새 해시가 기준이 된다
        r, _ = self.check(v2)
        self.assertEqual(r.status, STATUS_UNCHANGED)
        for call in f.calls:
            self.assertEqual(call["method"], "GET")
            self.assertIsNone(call["data"])

    def test_bot_challenge_is_manual_and_not_retried(self):
        for body, status in [("<html><div class='cf-turnstile'></div></html>", 200),
                             ("<title>Just a moment...</title>", 503),
                             ("forbidden", 403), ("slow down", 429),
                             ("<script src='https://www.google.com/recaptcha/api.js'></script>", 200)]:
            r, f = self.check(body, status)
            self.assertEqual(r.status, STATUS_MANUAL, body)
            self.assertEqual(len(f.calls), 1)
        self.assertIsNone(self.store.get_value("form_hash:TH_TDAC"))

    def test_other_http_error_and_network_error(self):
        r, _ = self.check("x", 500)
        self.assertEqual(r.status, STATUS_ERROR)
        f = FakeFetcher(default=net_error())
        r = fsh.check_form("TH_TDAC", "https://form.example.test/th", self.store, f, self.cfg)
        self.assertEqual(r.status, STATUS_ERROR)

    def test_non_https_refused(self):
        f = FakeFetcher()
        r = fsh.check_form("TH_TDAC", "http://form.example.test/th", self.store, f, self.cfg)
        self.assertEqual(r.status, STATUS_ERROR)
        self.assertEqual(f.calls, [])

    def test_challenge_detector(self):
        self.assertFalse(looks_like_bot_challenge(html_resp("<p>ok</p>")))
        self.assertTrue(looks_like_bot_challenge(html_resp("<p>ok</p>", 429)))


class NoticeTest(unittest.TestCase, TempDirCase):
    def setUp(self):
        self.tmp = self.make_tmp()
        self.cfg = self.make_cfg(self.tmp)
        self.store = FingerprintStore(self.tmp / "s.sqlite")
        self.addCleanup(self.store.close)

    def check(self, body, status=200):
        f = FakeFetcher(default=html_resp(body, status))
        return nw.check_notice("th_imm", "https://notice.example.test/", self.store, f, self.cfg)

    def test_flow(self):
        base = "<html><head><title>x</title></head><body><p>공지 1</p><script>t=1</script></body></html>"
        self.assertEqual(self.check(base).status, STATUS_BASELINE)
        self.assertEqual(self.check(base.replace("t=1", "t=2")).status, STATUS_UNCHANGED)
        r = self.check(base.replace("공지 1", "공지 1</p><p>새 공지: TDAC 변경"))
        self.assertEqual(r.status, STATUS_CHANGED)
        self.assertEqual(r.changes[0].unit, "notice:th_imm")
        self.assertTrue(any("새 공지" in line for line in r.changes[0].diff["unified_diff"]))
        self.assertEqual(self.check("x", 429).status, STATUS_MANUAL)

    def test_normalize_text(self):
        self.assertEqual(nw.normalize_text("<div>a   b</div><div>c</div><style>x{}</style>"), "a b\nc")


class NewsTest(unittest.TestCase):
    def test_match(self):
        items = [
            {"title": "Thailand updates TDAC rules for tourists", "url": "https://n.test/1", "ts": "2026-09-29T01:00:00Z"},
            {"title": "태국 입국신고 온라인으로 바뀐다", "url": "https://n.test/2", "ts": "t"},
            ("Stock market today", "https://n.test/3", "t"),
            ("EU ETIAS launch date set", "https://n.test/4", "t"),
            ("TDACS company earnings", "https://n.test/5", "t"),  # 단어 경계: TDACS 는 아님
            {"title": "TDAC again", "url": "https://n.test/1", "ts": "t"},  # 같은 url
            ("Malaysia MDAC and SG Arrival Card tips", "https://n.test/6", "t"),
        ]
        m = news.match_entry_policy_news(items)
        urls = [x.url for x in m]
        self.assertEqual(urls, ["https://n.test/1", "https://n.test/2", "https://n.test/4", "https://n.test/6"])
        self.assertEqual(m[0].form_ids, ["TH_TDAC"])
        self.assertIn("입국신고", m[1].keywords)
        self.assertEqual(m[3].form_ids, ["MY_MDAC", "SG_SGAC"])
        changes = news.to_changes(m)
        self.assertEqual(changes[0].unit, "TH_TDAC")
        self.assertEqual(changes[3].unit, "news")
        # 지문은 url 기준: 제목이 조금 바뀌어도 같다
        again = news.to_changes(news.match_entry_policy_news([("TDAC rules updated (edit)", "https://n.test/1", "t2")]))
        self.assertEqual(again[0].fingerprint, changes[0].fingerprint)

    def test_adapter_doc_exists(self):
        self.assertIn("match_entry_policy_news", news.ADAPTER_DOC)


if __name__ == "__main__":
    unittest.main()
