"""공지 도구 테스트 — 검증 규칙·서명본·FCM 메시지·운영자 도우미 시험 실행 (네트워크 없음).

python -m unittest tools/notices/test_notices.py
"""
import copy
import datetime as dt
import json
import pathlib
import sys
import tempfile
import types
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build_notices as bn  # noqa: E402
import notice as tool  # noqa: E402

NOW = dt.datetime(2026, 10, 8, 3, 0, tzinfo=dt.timezone.utc)  # 12:00 KST
COUNTRIES = {"TH", "JP", "SG", "MY", "ID", "TW", "CN", "PH", "VN"}


def notice(**over):
    n = {
        "id": "service-check",
        "version": 1,
        "active": True,
        "type": "normal",
        "category": "service",
        "title_ko": "서비스 점검 안내",
        "body_ko": "10월 12일 새벽에 잠깐 새 안내를 받을 수 없어요.",
        "start": "2026-10-08T09:00:00+09:00",
        "end": "2026-10-20T23:59:00+09:00",
        "priority": 5,
        "audience": ["all"],
    }
    n.update(over)
    return n


def doc(*notices):
    return {"schema_version": 1, "notices": list(notices)}


def check(*notices, hosting=None):
    return bn.validate(doc(*notices), NOW, COUNTRIES, hosting=hosting)


class ValidatorTest(unittest.TestCase):
    def test_real_source_passes_and_only_the_intro_is_on(self):
        source = json.loads(bn.SOURCE.read_text(encoding="utf-8"))
        errors, _ = bn.validate(source, NOW, bn.known_countries())  # 그림 파일(hosting/public/notices)까지 본다
        self.assertEqual([], errors)
        # 켜진 것은 운영자가 요청한 소개 공지(카드뉴스 두 장) 하나뿐 — 나머지 견본은 꺼진 채로 머지된다
        self.assertEqual(["about-readyport"], [n["id"] for n in source["notices"] if n["active"]])
        about = bn.find_notice(source, "about-readyport")
        self.assertEqual(("guide", "service"), (about["type"], about["category"]))  # 한 번 보면 끝(이용 안내), 광고 아님
        self.assertNotIn("end", about)
        self.assertEqual(2, len(about["images"]))
        self.assertEqual(["about-readyport"], [n["id"] for n in bn.build_payload(source, NOW)["notices"]])

    def test_good_notice_passes(self):
        self.assertEqual(([], []), check(notice()))

    def test_bad_image_url(self):
        errors, _ = check(notice(images=[{"url": "https://evil.example/a.png", "alt_ko": "그림"}]))
        self.assertTrue(any("images[0].url" in e for e in errors))
        errors, _ = check(notice(images=[{"url": "https://readyport-app.web.app/notices/../x.png", "alt_ko": "그림"}]))
        self.assertTrue(any("images[0].url" in e for e in errors))

    def test_missing_alt_text(self):
        errors, _ = check(notice(images=[{"url": "https://readyport-app.web.app/notices/a.webp", "alt_ko": " "}]))
        self.assertTrue(any("alt_ko" in e for e in errors))
        errors, _ = check(notice(images=[{"url": "https://readyport-app.web.app/notices/a.webp"}]))
        self.assertTrue(any("alt_ko" in e for e in errors))

    def test_image_file_must_exist_and_be_small(self):
        with tempfile.TemporaryDirectory() as d:
            root = pathlib.Path(d)
            img = {"url": "https://readyport-app.web.app/notices/a.webp", "alt_ko": "가을 여행 그림"}
            errors, _ = check(notice(images=[img]), hosting=root)
            self.assertTrue(any("파일이 없어요" in e for e in errors))
            (root / "notices").mkdir()
            (root / "notices" / "a.webp").write_bytes(b"x" * (bn.MAX_IMAGE_BYTES + 1))
            errors, _ = check(notice(images=[img]), hosting=root)
            self.assertTrue(any("KB" in e for e in errors))
            (root / "notices" / "a.webp").write_bytes(b"x" * 100)
            self.assertEqual([], check(notice(images=[img]), hosting=root)[0])

    def test_bad_link(self):
        for url in ["http://readyport-app.web.app/", "https://example.com/", "https://github.com/someone/else",
                    "https://user@play.google.com/", "https://readyport-app.web.app:8443/", "javascript:alert(1)"]:
            errors, _ = check(notice(link={"label_ko": "열기", "url": url}))
            self.assertTrue(any("link.url" in e for e in errors), url)
        for url in ["https://play.google.com/store/apps/details?id=com.readyport", "https://github.com/inhyeuk/readyport/releases",
                    "https://readyport-app.web.app/privacy/"]:
            self.assertEqual([], check(notice(link={"label_ko": "열기", "url": url}))[0], url)

    def test_expired_and_empty_window(self):
        errors, _ = check(notice(start="2026-10-10T09:00:00+09:00", end="2026-10-10T09:00:00+09:00"))
        self.assertTrue(any("기간이 비어" in e for e in errors))
        errors, _ = check(notice(start="2026-10-08T09:00:00"))  # 시간대 없음
        self.assertTrue(any("start" in e for e in errors))
        # 이미 끝난 공지는 오류가 아니라 경고, 30일이 지나면 서명본에서 빠진다
        ended = notice(start="2026-08-01T09:00:00+09:00", end="2026-08-20T09:00:00+09:00")
        errors, warnings = check(ended)
        self.assertEqual([], errors)
        self.assertTrue(any("끝난 지" in w for w in warnings))
        self.assertEqual([], bn.build_payload(doc(ended), NOW)["notices"])
        recent = notice(start="2026-09-20T09:00:00+09:00", end="2026-10-01T09:00:00+09:00")
        self.assertEqual(1, len(bn.build_payload(doc(recent), NOW)["notices"]))

    def test_unknown_type_and_category(self):
        self.assertTrue(any("type" in e for e in check(notice(type="popup"))[0]))
        self.assertTrue(any("category" in e for e in check(notice(category="ads"))[0]))

    def test_oversize_body_and_html(self):
        self.assertTrue(any("body_ko" in e and "줄여" in e for e in check(notice(body_ko="가" * 501))[0]))
        self.assertTrue(any("title_ko" in e for e in check(notice(title_ko="가" * 41))[0]))
        self.assertTrue(any("HTML" in e for e in check(notice(body_ko="<b>굵게</b> 안 돼요"))[0]))
        self.assertTrue(any("HTML" in e for e in check(notice(body_ko="a &amp; b"))[0]))
        self.assertTrue(any("쪽이" in e for e in check(notice(more_pages_ko=["쪽"] * 6))[0]))

    def test_promo_rules(self):
        errors, _ = check(notice(category="promo", type="event", title_ko="가을 할인 소식"))
        self.assertTrue(any("(광고)" in e for e in errors))
        self.assertEqual([], check(notice(category="promo", type="event", title_ko="(광고) 가을 할인 소식"))[0])
        self.assertTrue(any("urgent·guide" in e for e in check(notice(category="promo", type="urgent", title_ko="(광고) 급해요"))[0]))
        self.assertTrue(any("promo 로" in e for e in check(notice(title_ko="(광고) 서비스인 척"))[0]))

    def test_audience_versions_and_ids(self):
        self.assertTrue(any("나라" in e for e in check(notice(audience=["XX"]))[0]))
        self.assertTrue(any("혼자" in e for e in check(notice(audience=["all", "TH"]))[0]))
        self.assertEqual([], check(notice(audience=["TH", "JP"]))[0])
        self.assertTrue(any("min_version_code" in e for e in check(notice(min_version_code=9, max_version_code=7))[0]))
        self.assertTrue(any("두 번" in e for e in check(notice(), notice())[0]))
        self.assertTrue(any("id" in e for e in check(notice(id="Bad Id"))[0]))


class PayloadTest(unittest.TestCase):
    def test_inactive_left_out_and_sorted(self):
        d = doc(notice(id="low", priority=1), notice(id="draft", active=False), notice(id="high", priority=9))
        p = bn.build_payload(d, NOW)
        self.assertEqual(["high", "low"], [n["id"] for n in p["notices"]])
        self.assertNotIn("active", p["notices"][0])
        self.assertEqual("2026-10-08T03:00:00Z", p["generated_at"])

    def test_canonical_bytes_and_signature_roundtrip(self):
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

        key = Ed25519PrivateKey.generate()
        data = bn.canonical_bytes(bn.build_payload(doc(notice()), NOW))
        self.assertNotIn(b"\n", data)
        self.assertNotIn(b", ", data)
        self.assertIn("서비스 점검".encode("utf-8"), data)  # ensure_ascii=False — 앱과 같은 바이트
        sig = json.loads(bn.sign(data, key, "test-1"))
        self.assertEqual(("test-1", "Ed25519"), (sig["kid"], sig["alg"]))
        import base64
        key.public_key().verify(base64.b64decode(sig["sig"]), data)  # 틀리면 예외


class FcmTest(unittest.TestCase):
    def test_message_has_no_text_and_no_country_key(self):
        m = bn.fcm_message(notice(), "all")["message"]
        self.assertEqual("notice_all", m["topic"])
        self.assertEqual({"type": "notice", "id": "service-check", "v": "1", "cat": "service"}, m["data"])
        self.assertNotIn("notification", m)  # 글은 앱이 서명된 공지에서 꺼낸다
        self.assertNotIn("country", m["data"])  # 0.5.0 앱이 정책 변경 알림으로 읽지 않게

    def test_country_and_promo_targets(self):
        m = bn.fcm_message(notice(), "TH")["message"]
        self.assertEqual("'notice_all' in topics && 'country_TH' in topics", m["condition"])
        self.assertNotIn("topic", m)
        p = bn.fcm_message(notice(category="promo", title_ko="(광고) 소식"), "all")["message"]
        self.assertEqual("notice_promo", p["topic"])
        self.assertEqual("promo", p["data"]["cat"])
        with self.assertRaises(ValueError):
            bn.fcm_message(notice(), "th'; drop")

    def test_push_problems(self):
        d = doc(notice(), notice(id="later", start="2026-11-01T09:00:00+09:00"), notice(id="off", active=False),
                notice(id="thai", audience=["TH"]))
        self.assertEqual(([], []), bn.push_problems(d, "service-check", "all", NOW))
        self.assertTrue(bn.push_problems(d, "nope", "all", NOW)[0])
        self.assertTrue(any("시작 전" in e for e in bn.push_problems(d, "later", "all", NOW)[0]))
        self.assertTrue(any("active" in e for e in bn.push_problems(d, "off", "all", NOW)[0]))
        self.assertTrue(any("audience" in e for e in bn.push_problems(d, "thai", "JP", NOW)[0]))
        self.assertTrue(bn.push_problems(d, "thai", "all", NOW)[1])  # 경고만

    def test_promo_at_night_warns(self):
        night = dt.datetime(2026, 10, 8, 13, 30, tzinfo=dt.timezone.utc)  # 22:30 KST
        self.assertTrue(bn.is_night_kst(night))
        self.assertFalse(bn.is_night_kst(NOW))
        d = doc(notice(category="promo", type="event", title_ko="(광고) 소식"))
        self.assertTrue(any("아침 8시" in w for w in bn.push_problems(d, "service-check", "all", night)[1]))


class OperatorToolTest(unittest.TestCase):
    def args(self, **over):
        a = {"id": "service-check", "target": "all", "send": False, "remote_dry_run": False, "ref": "main"}
        a.update(over)
        return types.SimpleNamespace(**a)

    def test_dry_run_touches_no_network(self):
        def no_network(*_a, **_k):
            raise AssertionError("시험 실행이 네트워크를 썼다")

        lines = []
        rc = tool.push(self.args(), NOW, doc(notice()), fetch=no_network, token=no_network, out=lines.append)
        self.assertEqual(0, rc)
        self.assertTrue(any("notice_all" in x for x in lines))
        self.assertTrue(any("\"dry_run\": \"true\"" in x for x in lines))

    def test_send_dispatches_workflow(self):
        calls = []

        def fetch(method, url, headers=None, data=None, timeout=None):
            calls.append((method, url, headers, json.loads(data)))
            return types.SimpleNamespace(status=204, text="")

        lines = []
        rc = tool.push(self.args(send=True, target="TH"), NOW, doc(notice()), fetch=fetch, token=lambda: "tok", out=lines.append)
        self.assertEqual(0, rc)
        method, url, headers, body = calls[0]
        self.assertEqual("POST", method)
        self.assertTrue(url.endswith("/repos/inhyeuk/readyport/actions/workflows/notices.yml/dispatches"))
        self.assertEqual({"ref": "main", "inputs": {"notice_id": "service-check", "target": "TH", "dry_run": "false"}}, body)
        self.assertEqual("Bearer tok", headers["Authorization"])
        self.assertFalse(any("tok" in x for x in lines))  # 토큰은 화면에 찍지 않는다

    def test_refuses_notice_that_would_not_show(self):
        lines = []
        rc = tool.push(self.args(id="later"), NOW, doc(notice(id="later", start="2026-12-01T09:00:00+09:00")),
                       fetch=None, token=None, out=lines.append)
        self.assertEqual(1, rc)

    def test_scaffold_is_off_and_valid(self):
        d = tool.add_notice(doc(), tool.scaffold("autumn-event", "event", True, NOW))
        n = d["notices"][0]
        self.assertFalse(n["active"])
        self.assertTrue(n["title_ko"].startswith("(광고)"))
        self.assertEqual([], bn.validate(d, NOW, COUNTRIES)[0])
        with self.assertRaises(ValueError):
            tool.add_notice(d, tool.scaffold("autumn-event", "event", False, NOW))

    def test_credential_parsing_and_state(self):
        self.assertEqual("abc", tool.parse_credential("protocol=https\nhost=github.com\nusername=x\npassword=abc\n"))
        self.assertIsNone(tool.parse_credential("protocol=https\n"))
        self.assertEqual("보이는 중", tool.state_of(notice(), NOW))
        self.assertEqual("꺼짐(초안)", tool.state_of(notice(active=False), NOW))
        self.assertEqual("예약됨", tool.state_of(notice(start="2026-11-01T00:00:00+09:00"), NOW))


if __name__ == "__main__":
    unittest.main()
