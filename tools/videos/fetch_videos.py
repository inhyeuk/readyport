"""나라별 YouTube 여행 영상 목록 (최대 50개) → 서명 → Firestore videos/{나라}.

YouTube API 서비스 약관·개발자 정책을 따른다 (docs/research/2026-09-30_youtube_videos.md):
- 공식 YouTube Data API v3로만 가져온다. 썸네일은 API가 준 주소(i.ytimg.com)를 그대로 쓴다(복사·수정 없음)
- 저장한 목록은 30일 안에 새로 받는다(매일 실행). 앱도 30일 지난 목록은 보여 주지 않는다
- 아동용(madeForKids) 영상은 뺀다. 조회수·구독자 수는 API 값 그대로(새 지표를 만들지 않는다)

사용:
  YOUTUBE_API_KEY=... python tools/videos/fetch_videos.py --out build/videos          # 파일로만(서명 없음)
  YOUTUBE_API_KEY=... python tools/videos/fetch_videos.py --kid rp-2026-1 --key k.pem --upload
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import json
import os
import pathlib
import re
import sys
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

API = "https://www.googleapis.com/youtube/v3"
MAX_ITEMS = 50
MIN_SECONDS = 180  # 3분 미만(쇼츠 등)은 뺀다
VIDEO_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")
THUMB_PREFIX = "https://i.ytimg.com/"


# ---------------- 순수 함수 (테스트 대상) ----------------

def countries_from_index(index: dict) -> list[dict]:
    return [c for c in index.get("countries", []) if c.get("pack")]


def search_params(name_ko: str, duration: str, api_key: str) -> dict:
    """한국어 '{나라} 여행' 영상 검색. 안전 검색 엄격, 한국 지역."""
    return {
        "part": "id", "type": "video", "q": f"{name_ko} 여행", "maxResults": 50,
        "regionCode": "KR", "relevanceLanguage": "ko", "safeSearch": "strict",
        "videoDuration": duration, "order": "relevance", "key": api_key,
    }


def interleave(*lists: list[str]) -> list[str]:
    """여러 검색 결과를 번갈아 섞는다(각 결과의 관련도 순서 유지, 중복 제거)"""
    out: list[str] = []
    for i in range(max((len(x) for x in lists), default=0)):
        for x in lists:
            if i < len(x) and x[i] not in out:
                out.append(x[i])
    return out


def parse_duration(iso: str) -> int:
    """PT1H2M3S → 3723초. 모르면 0"""
    m = re.fullmatch(r"P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?", iso or "")
    if not m:
        return 0
    d, h, mi, s = (int(x) if x else 0 for x in m.groups())
    return ((d * 24 + h) * 60 + mi) * 60 + s


def pick_thumbnail(thumbs: dict) -> str | None:
    for k in ("medium", "high", "standard", "default"):
        url = (thumbs.get(k) or {}).get("url")
        if url and url.startswith(THUMB_PREFIX):
            return url
    return None


def select_videos(order: list[str], videos: dict[str, dict], channels: dict[str, dict], name_ko: str) -> list[dict]:
    """검색 순서를 지키며 조건에 맞는 영상만 최대 50개. videos/channels 는 API 응답 항목(id → item)."""
    out, seen = [], set()
    for vid in order:
        if vid in seen or not VIDEO_ID.match(vid):
            continue
        seen.add(vid)
        v = videos.get(vid)
        if not v:
            continue
        sn, st, cd, stat = v.get("snippet", {}), v.get("status", {}), v.get("contentDetails", {}), v.get("statistics", {})
        if st.get("madeForKids") or st.get("privacyStatus") != "public":
            continue
        if sn.get("liveBroadcastContent", "none") != "none":
            continue
        seconds = parse_duration(cd.get("duration", ""))
        if seconds < MIN_SECONDS:
            continue
        title = sn.get("title", "")
        if name_ko not in title:
            continue  # 제목에 나라 이름이 있는 영상만 (관련 없는 영상 줄이기)
        thumb = pick_thumbnail(sn.get("thumbnails", {}))
        if not thumb:
            continue
        ch = channels.get(sn.get("channelId", ""), {})
        cst = ch.get("statistics", {})
        subs = None if cst.get("hiddenSubscriberCount") or "subscriberCount" not in cst else int(cst["subscriberCount"])
        out.append({
            "id": vid,
            "title": title,
            "channel_id": sn.get("channelId", ""),
            "channel_title": sn.get("channelTitle", ""),
            "published_at": sn.get("publishedAt", ""),
            "view_count": int(stat["viewCount"]) if "viewCount" in stat else None,
            "subscriber_count": subs,
            "duration_s": seconds,
            "thumbnail": thumb,
        })
        if len(out) >= MAX_ITEMS:
            break
    return out


def build_payload(country: str, query: str, items: list[dict], now: dt.datetime) -> dict:
    return {
        "schema_version": 1,
        "country": country,
        "query": query,
        "source": "YouTube Data API v3",
        "generated_at": now.astimezone(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "items": items,
    }


def canonical_bytes(payload: dict) -> bytes:
    """팩과 같은 규칙: 공백 없는 UTF-8. 서명은 이 바이트 그대로에 대해 한다"""
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def sign(data: bytes, key, kid: str) -> str:
    return json.dumps({"kid": kid, "alg": "Ed25519", "sig": base64.b64encode(key.sign(data)).decode()})


# ---------------- 네트워크 ----------------

def _get(fetch, path: str, params: dict) -> dict:  # pragma: no cover - 실제 네트워크
    url = f"{API}/{path}?" + urllib.parse.urlencode(params)
    resp = fetch("GET", url, headers={"Accept": "application/json"}, timeout=30)
    if resp.status != 200:
        # 키 값은 찍지 않는다
        raise RuntimeError(f"YouTube API {path} HTTP {resp.status}: {resp.text[:300]}")
    return json.loads(resp.text)


def _batched(ids: list[str], n: int = 50):
    for i in range(0, len(ids), n):
        yield ids[i:i + n]


def fetch_country(fetch, api_key: str, name_ko: str) -> tuple[list[str], dict, dict]:  # pragma: no cover
    lists = []
    for duration in ("medium", "long"):  # 4~20분, 20분 넘는 영상
        res = _get(fetch, "search", search_params(name_ko, duration, api_key))
        lists.append([it["id"]["videoId"] for it in res.get("items", []) if it.get("id", {}).get("videoId")])
    ids = interleave(*lists)
    videos: dict[str, dict] = {}
    for chunk in _batched(ids):
        res = _get(fetch, "videos", {"part": "snippet,statistics,contentDetails,status", "id": ",".join(chunk),
                                     "key": api_key, "hl": "ko"})
        videos.update({it["id"]: it for it in res.get("items", [])})
    ch_ids = list(dict.fromkeys(v["snippet"]["channelId"] for v in videos.values() if v.get("snippet", {}).get("channelId")))
    channels: dict[str, dict] = {}
    for chunk in _batched(ch_ids):
        res = _get(fetch, "channels", {"part": "statistics", "id": ",".join(chunk), "key": api_key})
        channels.update({it["id"]: it for it in res.get("items", [])})
    return ids, videos, channels


def main() -> int:  # pragma: no cover
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", help="파일로 저장할 폴더(확인용)")
    ap.add_argument("--kid")
    ap.add_argument("--key", help="Ed25519 개인키 PEM")
    ap.add_argument("--upload", action="store_true", help="Firestore videos/{나라} 에 쓴다")
    args = ap.parse_args()

    api_key = os.environ.get("YOUTUBE_API_KEY", "").strip()
    if not api_key:
        print("::warning title=영상 목록 건너뜀::YOUTUBE_API_KEY 가 없어 영상 목록을 받지 않았어요.")
        return 0

    from ops.aria.net import urllib_fetch
    index = json.loads((ROOT / "packs/src/index.json").read_text(encoding="utf-8"))
    now = dt.datetime.now(dt.timezone.utc)

    key = None
    if args.key:
        from cryptography.hazmat.primitives.serialization import load_pem_private_key
        key = load_pem_private_key(pathlib.Path(args.key).read_bytes(), password=None)
    fs = None
    if args.upload:
        from ops.aria.gcp import SCOPE_DATASTORE, FirestoreRest, ServiceAccountTokenProvider
        fs = FirestoreRest(os.environ["FIREBASE_PROJECT_ID"],
                           ServiceAccountTokenProvider(os.environ["GOOGLE_APPLICATION_CREDENTIALS"], [SCOPE_DATASTORE]),
                           urllib_fetch)
        if key is None or not args.kid:
            print("--upload 에는 --kid 와 --key 가 필요해요", file=sys.stderr)
            return 1

    for c in countries_from_index(index):
        order, videos, channels = fetch_country(urllib_fetch, api_key, c["name_ko"])
        items = select_videos(order, videos, channels, c["name_ko"])
        payload = build_payload(c["code"], f"{c['name_ko']} 여행", items, now)
        data = canonical_bytes(payload)
        print(f"{c['code']}: 후보 {len(order)}개 → {len(items)}개")
        if args.out:
            out = pathlib.Path(args.out)
            out.mkdir(parents=True, exist_ok=True)
            (out / f"{c['code']}.json").write_bytes(data)
        if fs is not None:
            fs.set_document(f"videos/{c['code']}", {
                "payload": data.decode("utf-8"),
                "sig": sign(data, key, args.kid),
                "generated_at": now,
            })
    return 0


if __name__ == "__main__":
    sys.exit(main())
