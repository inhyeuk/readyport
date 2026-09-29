"""tools/videos/fetch_videos.py 의 순수 함수 테스트 (네트워크 없음)

python -m unittest tools/videos/test_fetch_videos.py
"""
import datetime as dt
import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import fetch_videos as fv  # noqa: E402


def video(vid, title="태국 여행 브이로그", seconds="PT12M3S", kids=False, privacy="public", live="none",
          channel="UC1", views="1000", thumb="https://i.ytimg.com/vi/x/mqdefault.jpg"):
    return {
        "id": vid,
        "snippet": {"title": title, "channelId": channel, "channelTitle": "채널", "publishedAt": "2026-09-01T00:00:00Z",
                    "liveBroadcastContent": live, "thumbnails": {"medium": {"url": thumb}}},
        "status": {"madeForKids": kids, "privacyStatus": privacy},
        "contentDetails": {"duration": seconds},
        "statistics": {"viewCount": views},
    }


class FetchVideosTest(unittest.TestCase):
    def test_duration(self):
        self.assertEqual(fv.parse_duration("PT1H2M3S"), 3723)
        self.assertEqual(fv.parse_duration("PT45S"), 45)
        self.assertEqual(fv.parse_duration("P1DT1S"), 86401)
        self.assertEqual(fv.parse_duration("bad"), 0)

    def test_interleave_keeps_order_and_dedupes(self):
        self.assertEqual(fv.interleave(["a", "b", "c"], ["x", "a"]), ["a", "x", "b", "c"])

    def test_search_is_safe_korean(self):
        p = fv.search_params("태국", "medium", "K")
        self.assertEqual((p["q"], p["safeSearch"], p["regionCode"], p["relevanceLanguage"]), ("태국 여행", "strict", "KR", "ko"))

    def test_select_filters(self):
        ids = ["AAAAAAAAAA1", "AAAAAAAAAA2", "AAAAAAAAAA3", "AAAAAAAAAA4", "AAAAAAAAAA5", "AAAAAAAAAA6", "AAAAAAAAAA7", "bad"]
        videos = {
            "AAAAAAAAAA1": video("AAAAAAAAAA1"),
            "AAAAAAAAAA2": video("AAAAAAAAAA2", kids=True),                   # 아동용 제외
            "AAAAAAAAAA3": video("AAAAAAAAAA3", seconds="PT59S"),             # 쇼츠 제외
            "AAAAAAAAAA4": video("AAAAAAAAAA4", title="일본 먹방"),            # 나라 이름 없음 제외
            "AAAAAAAAAA5": video("AAAAAAAAAA5", thumb="https://evil.example/t.jpg"),  # 썸네일 주소 제외
            "AAAAAAAAAA6": video("AAAAAAAAAA6", live="upcoming"),              # 예정 방송 제외
            "AAAAAAAAAA7": video("AAAAAAAAAA7", channel="UC2"),
        }
        channels = {"UC1": {"statistics": {"subscriberCount": "5000"}},
                    "UC2": {"statistics": {"hiddenSubscriberCount": True, "subscriberCount": "0"}}}
        out = fv.select_videos(ids, videos, channels, "태국")
        self.assertEqual([x["id"] for x in out], ["AAAAAAAAAA1", "AAAAAAAAAA7"])
        self.assertEqual(out[0]["subscriber_count"], 5000)
        self.assertIsNone(out[1]["subscriber_count"])  # 숨긴 구독자 수는 보여 주지 않는다
        self.assertEqual(out[0]["view_count"], 1000)

    def test_max_fifty(self):
        ids = [f"A{i:010d}" for i in range(80)]
        videos = {i: video(i) for i in ids}
        self.assertEqual(len(fv.select_videos(ids, videos, {}, "태국")), 50)

    def test_payload_and_signature(self):
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        key = Ed25519PrivateKey.generate()
        payload = fv.build_payload("TH", "태국 여행", [], dt.datetime(2026, 9, 30, 3, 0, tzinfo=dt.timezone.utc))
        data = fv.canonical_bytes(payload)
        self.assertIn('"generated_at":"2026-09-30T03:00:00Z"', data.decode())
        sig = json.loads(fv.sign(data, key, "test"))
        import base64
        key.public_key().verify(base64.b64decode(sig["sig"]), data)  # 예외 없으면 통과
        self.assertEqual(sig["alg"], "Ed25519")


if __name__ == "__main__":
    unittest.main()
