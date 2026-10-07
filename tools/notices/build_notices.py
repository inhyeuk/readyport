"""공지 원본 검증 → 서명 → Firestore notices/current, 그리고 공지 알림(FCM) 보내기.

원본: notices/notices.json (사람이 PR로 고친다, 서명 없음). 스키마: notices/schema/notices.schema.json
서명본(payload): 공백 없는 UTF-8 JSON {"schema_version":1,"generated_at":"…Z","notices":[…]} — active 공지만,
  끝난 지 30일 넘은 공지는 뺀다. 서명은 국가 팩과 같은 Ed25519 키(kid rp-2026-1)로 이 바이트 그대로에 대해 한다.
올리는 곳: Firestore notices/current = {payload, sig, generated_at}. 앱은 get 만 한다(규칙: 읽기 하나, 목록·쓰기 불가).
비밀키는 저장소에 두지 않는다: 운영자 PC ~/.readyport/keys/ 또는 GitHub secret PACK_SIGNING_KEY_PEM (build_packs.load_key 재사용).

하위 명령
  check                                   검증만 (키 필요 없음)
  build --out DIR [--kid K --key PEM]     서명본을 파일로 (확인용, 키가 있으면 .sig 도)
  upload --kid K --key PEM                서명해서 Firestore 에 올린다 (CI: notices.yml)
  fcm --id ID --target all|ISO2 [--send]  공지 알림 데이터 메시지. --send 없으면 보낼 내용만 보여 준다

알림 메시지(FCM HTTP v1)에는 글을 넣지 않는다 — {type:"notice", id, v, cat} 만. 앱이 서명된 공지를 받아 그 제목·본문으로 알린다.
토큰은 어디에도 저장하지 않는다: 토픽(notice_all · notice_promo · country_{ISO2})으로만 보낸다.
나라 코드는 'country' 키로 넣지 않는다 — 0.5.0 앱은 country 가 든 메시지를 '입국 안내가 바뀌었어요'로 읽는다.
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

SOURCE = ROOT / "notices" / "notices.json"
SCHEMA = ROOT / "notices" / "schema" / "notices.schema.json"
HOSTING = ROOT / "hosting" / "public"
INDEX = ROOT / "packs" / "src" / "index.json"

KST = dt.timezone(dt.timedelta(hours=9), "KST")
TYPES = ("urgent", "normal", "event", "guide")
CATEGORIES = ("service", "promo")
IMAGE_PREFIX = "https://readyport-app.web.app/notices/"
IMAGE_EXT = (".png", ".webp", ".jpg")
# 링크로 열 수 있는 곳: 우리 Hosting, Play 스토어, 이 앱의 GitHub 저장소
LINK_HOSTS = {"readyport-app.web.app": "/", "play.google.com": "/", "github.com": "/inhyeuk/readyport"}
ID_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{2,40}$")
ISO2_RE = re.compile(r"^[A-Z]{2}$")
HTML_RE = re.compile(r"<\s*/?\s*[A-Za-z!][^>]*>|&[A-Za-z]+;|&#\d+;")
CONTROL_RE = re.compile(r"[\x00-\x09\x0b-\x1f\x7f]")

MAX_TITLE = 40
MAX_BODY = 500
MAX_ALT = 300  # 카드뉴스 그림 속 글을 모두 담는다
MAX_PAGES = 6
MAX_IMAGE_BYTES = 300_000
MAX_PAYLOAD_BYTES = 100_000
KEEP_ENDED_DAYS = 30
PROMO_PREFIX = "(광고)"
NIGHT_START, NIGHT_END = 21, 8  # 정보통신망법 제50조 제3항: 21시~다음 날 8시는 별도 동의가 있어야 광고를 보낸다

TOPIC_ALL = "notice_all"
TOPIC_PROMO = "notice_promo"


# ---------------- 순수 함수 (테스트 대상) ----------------

def parse_time(value) -> dt.datetime | None:
    """시간대가 붙은 ISO 날짜·시각만 받는다 (시간대 없는 값은 기기마다 다르게 읽힌다)."""
    if not isinstance(value, str):
        return None
    try:
        t = dt.datetime.fromisoformat(value)
    except ValueError:
        return None
    return t if t.tzinfo is not None else None


def known_countries(index_path: pathlib.Path = INDEX) -> set[str]:
    index = json.loads(index_path.read_text(encoding="utf-8"))
    return {c["code"] for c in index.get("countries", [])}


def image_ok(url: str) -> bool:
    if not isinstance(url, str) or not url.startswith(IMAGE_PREFIX) or not url.lower().endswith(IMAGE_EXT):
        return False
    rest = url[len(IMAGE_PREFIX):]
    return bool(rest) and ".." not in rest and "?" not in rest and "#" not in rest and "//" not in rest


def link_ok(url: str) -> bool:
    """https + 허용한 곳만. 사용자 정보·포트·다른 하위 도메인은 막는다."""
    try:
        u = urllib.parse.urlsplit(url)
    except ValueError:
        return False
    if u.scheme != "https" or u.username or u.password or u.port is not None:
        return False
    prefix = LINK_HOSTS.get((u.hostname or "").lower())
    if prefix is None:
        return False
    path = u.path or "/"
    return path == prefix or path.startswith(prefix.rstrip("/") + "/") or prefix == "/"


def page_count(n: dict) -> int:
    return max(1 + len(n.get("more_pages_ko") or []), len(n.get("images") or []))


def _text_problems(label: str, text, limit: int) -> list[str]:
    if not isinstance(text, str) or not text.strip():
        return [f"{label}: 비어 있어요"]
    out = []
    if len(text) > limit:
        out.append(f"{label}: {len(text)}자 — {limit}자 이하로 줄여 주세요")
    if HTML_RE.search(text):
        out.append(f"{label}: HTML 태그·엔티티는 쓸 수 없어요(그냥 글자만)")
    if CONTROL_RE.search(text):
        out.append(f"{label}: 보이지 않는 제어 문자가 있어요")
    return out


def check_notice(n: dict, now: dt.datetime, countries: set[str], hosting: pathlib.Path | None) -> tuple[list[str], list[str]]:
    """공지 하나의 (오류, 경고). hosting 이 있으면 그림 파일이 있는지·크기도 본다."""
    nid = n.get("id", "?")
    errors: list[str] = []
    warnings: list[str] = []
    e = errors.append

    def where(msg: str) -> str:
        return f"[{nid}] {msg}"

    if not isinstance(n.get("id"), str) or not ID_RE.match(n["id"]):
        e(where("id: 영문 소문자·숫자·-·_ 3~41자로 적어 주세요"))
    if not isinstance(n.get("version"), int) or n["version"] < 1:
        e(where("version: 1 이상 정수"))
    if n.get("type") not in TYPES:
        e(where(f"type '{n.get('type')}': {', '.join(TYPES)} 중 하나"))
    category = n.get("category", "service")
    if category not in CATEGORIES:
        e(where(f"category '{category}': service 또는 promo"))

    for msg in _text_problems("title_ko", n.get("title_ko"), MAX_TITLE):
        e(where(msg))
    for msg in _text_problems("body_ko", n.get("body_ko"), MAX_BODY):
        e(where(msg))
    for i, page in enumerate(n.get("more_pages_ko") or []):
        for msg in _text_problems(f"more_pages_ko[{i}]", page, MAX_BODY):
            e(where(msg))
    if page_count(n) > MAX_PAGES:
        e(where(f"쪽이 {page_count(n)}장이에요 — {MAX_PAGES}장 이하로"))

    if category == "promo":
        title = n.get("title_ko") or ""
        if not title.startswith(PROMO_PREFIX):
            e(where(f"광고성 공지(promo)는 제목이 '{PROMO_PREFIX}'로 시작해야 해요 (정보통신망법 제50조)"))
        if n.get("type") in ("urgent", "guide"):
            e(where("광고성 공지는 urgent·guide 가 될 수 없어요 — event 나 normal 로"))
    elif PROMO_PREFIX in (n.get("title_ko") or ""):
        e(where(f"제목에 '{PROMO_PREFIX}'가 있는데 category 가 service 예요 — 광고면 promo 로"))

    for i, img in enumerate(n.get("images") or []):
        url = img.get("url") if isinstance(img, dict) else None
        if not image_ok(url):
            e(where(f"images[{i}].url: 우리 Hosting({IMAGE_PREFIX}…png|webp|jpg)만 쓸 수 있어요 — {url}"))
        elif hosting is not None:
            f = hosting / url[len("https://readyport-app.web.app/"):]
            if not f.is_file():
                e(where(f"images[{i}]: 파일이 없어요 — hosting/public/{f.relative_to(hosting).as_posix()}"))
            elif f.stat().st_size > MAX_IMAGE_BYTES:
                e(where(f"images[{i}]: {f.stat().st_size // 1000}KB — {MAX_IMAGE_BYTES // 1000}KB 이하로 (무료 전송 한도)"))
        alt = img.get("alt_ko") if isinstance(img, dict) else None
        if not isinstance(alt, str) or not alt.strip():
            e(where(f"images[{i}].alt_ko: 그림 설명(TalkBack이 읽는 글)이 꼭 있어야 해요"))
        else:
            for msg in _text_problems(f"images[{i}].alt_ko", alt, MAX_ALT):
                e(where(msg))

    link = n.get("link")
    if link is not None:
        if not link_ok(link.get("url", "")):
            e(where(f"link.url: https + 우리 Hosting·play.google.com·github.com/inhyeuk/readyport 만 — {link.get('url')}"))
        for msg in _text_problems("link.label_ko", link.get("label_ko"), 24):
            e(where(msg))

    start = parse_time(n.get("start"))
    end = parse_time(n.get("end")) if n.get("end") is not None else None
    if start is None:
        e(where("start: 시간대가 붙은 ISO 날짜·시각 (예: 2026-10-20T09:00:00+09:00)"))
    if n.get("end") is not None and end is None:
        e(where("end: 시간대가 붙은 ISO 날짜·시각"))
    if start and end:
        if end <= start:
            e(where("기간이 비어 있어요: end 가 start 보다 늦어야 해요"))
        elif n.get("active") and end <= now:
            ended = (now - end).days
            if ended > KEEP_ENDED_DAYS:
                warnings.append(where(f"끝난 지 {ended}일 — 서명본에서 빠져요. notices.json 에서 지워도 돼요"))
            else:
                warnings.append(where(f"이미 끝난 공지예요 — 공지사항 목록의 '지난 공지'에만 {KEEP_ENDED_DAYS}일 동안 남아요"))
    if start and n.get("active") and start - now > dt.timedelta(days=365):
        warnings.append(where("시작이 1년도 더 뒤예요 — 날짜를 확인해 주세요"))
    if n.get("type") == "urgent" and n.get("active") and end is None:
        warnings.append(where("긴급 공지에 end 가 없어요 — 매번 앱을 열 때 떠요"))

    lo, hi = n.get("min_version_code"), n.get("max_version_code")
    if lo is not None and hi is not None and lo > hi:
        e(where("min_version_code 가 max_version_code 보다 커요"))

    audience = n.get("audience", ["all"])
    if not isinstance(audience, list) or not audience:
        e(where("audience: [\"all\"] 또는 나라 코드 목록"))
    elif "all" in audience:
        if len(audience) != 1:
            e(where("audience: \"all\" 은 혼자 써요"))
    else:
        unknown = [c for c in audience if c not in countries]
        if unknown:
            e(where(f"audience: 앱에 없는 나라 {unknown}"))
    return errors, warnings


def schema_errors(doc: dict) -> list[str]:
    import jsonschema

    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    out = []
    for err in jsonschema.Draft202012Validator(schema).iter_errors(doc):
        path = "/".join(str(p) for p in err.absolute_path)
        out.append(f"스키마 {path or '(맨 위)'}: {err.message}")
    return out


def validate(doc: dict, now: dt.datetime, countries: set[str], hosting: pathlib.Path | None = HOSTING,
             use_schema: bool = True) -> tuple[list[str], list[str]]:
    errors = schema_errors(doc) if use_schema else []
    warnings: list[str] = []
    if doc.get("schema_version") != 1:
        errors.append("schema_version 은 1")
    notices = doc.get("notices")
    if not isinstance(notices, list):
        return errors + ["notices 는 목록"], warnings
    seen = set()
    for n in notices:
        if not isinstance(n, dict):
            errors.append("notices 안에 객체가 아닌 값")
            continue
        if n.get("id") in seen:
            errors.append(f"[{n.get('id')}] id 가 두 번 있어요")
        seen.add(n.get("id"))
        e, w = check_notice(n, now, countries, hosting)
        errors += e
        warnings += w
    return errors, warnings


def normalize(n: dict) -> dict:
    """서명본에 넣을 모양: 기본값을 채우고 active 는 뺀다(서명본에는 켜진 공지만 있다)."""
    out = {
        "id": n["id"],
        "version": n["version"],
        "type": n["type"],
        "category": n.get("category", "service"),
        "title_ko": n["title_ko"],
        "body_ko": n["body_ko"],
        "start": n["start"],
        "priority": n.get("priority", 0),
        "audience": n.get("audience", ["all"]),
    }
    for k in ("more_pages_ko", "images", "link", "end", "min_version_code", "max_version_code"):
        if n.get(k) not in (None, []):
            out[k] = n[k]
    return out


def build_payload(doc: dict, now: dt.datetime) -> dict:
    keep = []
    for n in doc.get("notices", []):
        if not n.get("active"):
            continue
        end = parse_time(n.get("end")) if n.get("end") else None
        if end is not None and now - end > dt.timedelta(days=KEEP_ENDED_DAYS):
            continue
        keep.append(normalize(n))
    keep.sort(key=lambda x: (-x["priority"], x["start"], x["id"]))
    return {
        "schema_version": 1,
        "generated_at": now.astimezone(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "notices": keep,
    }


def canonical_bytes(payload: dict) -> bytes:
    """팩과 같은 규칙: 공백 없는 UTF-8. 서명은 이 바이트 그대로에 대해 한다"""
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def sign(data: bytes, key, kid: str) -> str:
    return json.dumps({"kid": kid, "alg": "Ed25519", "sig": base64.b64encode(key.sign(data)).decode()})


def find_notice(doc: dict, notice_id: str) -> dict | None:
    return next((n for n in doc.get("notices", []) if n.get("id") == notice_id), None)


def is_night_kst(now: dt.datetime) -> bool:
    h = now.astimezone(KST).hour
    return h >= NIGHT_START or h < NIGHT_END


def push_problems(doc: dict, notice_id: str, target: str, now: dt.datetime) -> tuple[list[str], list[str]]:
    """알림을 보내기 전 확인. 앱이 보여 주지 않을 공지에 알림을 보내지 않는다."""
    n = find_notice(doc, notice_id)
    if n is None:
        return [f"공지 '{notice_id}'가 notices.json 에 없어요"], []
    errors, warnings = [], []
    if not n.get("active"):
        errors.append(f"[{notice_id}] active 가 false 예요 — 먼저 켜서 main 에 머지해 주세요")
    start = parse_time(n.get("start"))
    end = parse_time(n.get("end")) if n.get("end") else None
    if start is None or start > now:
        errors.append(f"[{notice_id}] 아직 시작 전이에요({n.get('start')}) — 앱이 보여 주지 않아요")
    if end is not None and end <= now:
        errors.append(f"[{notice_id}] 이미 끝났어요({n.get('end')})")
    if target != "all" and not ISO2_RE.match(target):
        errors.append(f"target '{target}': all 또는 나라 코드 두 글자")
    audience = n.get("audience", ["all"])
    if target != "all" and "all" not in audience and target not in audience:
        errors.append(f"[{notice_id}] audience {audience} 에 {target} 이 없어요 — 받은 기기가 보여 주지 않아요")
    if target == "all" and "all" not in audience:
        warnings.append(f"[{notice_id}] audience 가 {audience} 라 그 나라가 없는 기기는 알림을 띄우지 않아요")
    if n.get("category", "service") == "promo" and is_night_kst(now):
        warnings.append("지금은 21시~8시예요 — 광고성 공지는 밤 알림에 따로 동의한 사람만 바로 받고, 나머지 기기는 아침 8시까지 미뤄요")
    return errors, warnings


def fcm_message(notice: dict, target: str) -> dict:
    """글은 넣지 않는다. 앱이 서명된 공지의 제목·본문으로 알린다."""
    cat = notice.get("category", "service")
    topic = TOPIC_PROMO if cat == "promo" else TOPIC_ALL
    msg: dict = {
        "data": {"type": "notice", "id": notice["id"], "v": str(notice["version"]), "cat": cat},
        "android": {"priority": "NORMAL", "ttl": "86400s"},
    }
    if target == "all":
        msg["topic"] = topic
    else:
        if not ISO2_RE.match(target):
            raise ValueError(target)
        # 그 나라를 구독한 기기 중 공지(또는 광고) 알림을 켠 기기만 — 끈 기기는 토픽에서 빠져 있다
        msg["condition"] = f"'{topic}' in topics && 'country_{target}' in topics"
    return {"message": msg}


# ---------------- 실행 ----------------

def _now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def _load_source() -> dict:
    return json.loads(SOURCE.read_text(encoding="utf-8"))


def _report(errors: list[str], warnings: list[str]) -> None:
    for w in warnings:
        print("경고: " + w)
    if errors:
        print("검증 실패:", file=sys.stderr)
        for e in errors:
            print("  - " + e, file=sys.stderr)


def _key(args):
    # 팩 서명 도구와 같은 규칙으로 비밀키를 읽는다 (환경변수 READYPORT_PACK_KEY 또는 --key 파일). 키 값은 찍지 않는다
    sys.path.insert(0, str(ROOT / "tools" / "packs"))
    from build_packs import load_key

    return load_key(args)


def cmd_check(args) -> int:
    doc = _load_source()
    errors, warnings = validate(doc, _now(), known_countries())
    _report(errors, warnings)
    if errors:
        return 1
    live = [n["id"] for n in doc["notices"] if n.get("active")]
    print(f"검증 통과: 공지 {len(doc['notices'])}개 (켜진 것 {len(live)}개{': ' + ', '.join(live) if live else ''})")
    return 0


def _signed(args) -> tuple[bytes, str | None, dt.datetime] | None:
    doc = _load_source()
    now = _now()
    errors, warnings = validate(doc, now, known_countries())
    _report(errors, warnings)
    if errors:
        return None
    payload = build_payload(doc, now)
    data = canonical_bytes(payload)
    if len(data) > MAX_PAYLOAD_BYTES:
        print(f"서명본이 {len(data)} bytes — {MAX_PAYLOAD_BYTES} 이하로 (오래된 공지를 지워 주세요)", file=sys.stderr)
        return None
    sig = None
    if args.key or os.environ.get("READYPORT_PACK_KEY"):
        key, kid = _key(args)
        sig = sign(data, key, kid)
    print(f"서명본: 공지 {len(payload['notices'])}개, {len(data)} bytes, generated_at={payload['generated_at']}"
          + (", 서명함" if sig else ", 서명 안 함"))
    return data, sig, now


def cmd_build(args) -> int:
    signed = _signed(args)
    if signed is None:
        return 1
    data, sig, _ = signed
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "notices.json").write_bytes(data)
    if sig:
        (out / "notices.json.sig").write_text(sig, encoding="utf-8")
    print(f"썼어요: {out}")
    return 0


def cmd_upload(args) -> int:  # pragma: no cover - 실제 네트워크
    if not (args.key or os.environ.get("READYPORT_PACK_KEY")) or not (args.kid or os.environ.get("READYPORT_PACK_KID")):
        print("upload 에는 --kid 와 --key 가 필요해요", file=sys.stderr)
        return 1
    signed = _signed(args)
    if signed is None:
        return 1
    data, sig, now = signed
    from ops.aria.gcp import SCOPE_DATASTORE, FirestoreRest, ServiceAccountTokenProvider
    from ops.aria.net import urllib_fetch

    fs = FirestoreRest(os.environ["FIREBASE_PROJECT_ID"],
                       ServiceAccountTokenProvider(os.environ["GOOGLE_APPLICATION_CREDENTIALS"], [SCOPE_DATASTORE]),
                       urllib_fetch)
    fs.set_document("notices/current", {"payload": data.decode("utf-8"), "sig": sig, "generated_at": now})
    print("Firestore notices/current 올림")
    return 0


def cmd_fcm(args) -> int:
    doc = _load_source()
    now = _now()
    errors, warnings = push_problems(doc, args.id, args.target, now)
    _report(errors, warnings)
    if errors:
        return 1
    message = fcm_message(find_notice(doc, args.id), args.target)
    print("보낼 메시지:", json.dumps(message, ensure_ascii=False))
    if not args.send:
        print("시험 실행이라 보내지 않았어요 (--send 를 붙이면 보내요)")
        return 0
    return _send_fcm(message)  # pragma: no cover


def _send_fcm(message: dict) -> int:  # pragma: no cover - 실제 네트워크
    from ops.aria.gcp import ServiceAccountTokenProvider
    from ops.aria.net import urllib_fetch

    project = os.environ["FIREBASE_PROJECT_ID"]
    tokens = ServiceAccountTokenProvider(os.environ["GOOGLE_APPLICATION_CREDENTIALS"],
                                         ["https://www.googleapis.com/auth/firebase.messaging"])
    url = f"https://fcm.googleapis.com/v1/projects/{project}/messages:send"
    resp = urllib_fetch("POST", url, headers={"Authorization": f"Bearer {tokens.get_token()}",
                                              "Content-Type": "application/json"},
                        data=json.dumps(message).encode("utf-8"), timeout=20)
    print("FCM", resp.status)
    if resp.status != 200:
        print(resp.text[:600])
        # deploy-packs 의 교훈: 403이면 대개 서비스 계정 역할(Firebase Cloud Messaging API 관리자)이 없거나 API가 꺼져 있다
        print(f"::warning title=공지 알림 실패::HTTP {resp.status}. 403이면 서비스 계정에 "
              "'Firebase Cloud Messaging API 관리자' 역할이 없거나 FCM API가 꺼져 있어요.")
        return 1
    return 0


def main(argv=None) -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):  # pragma: no cover
        pass
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("check")
    b = sub.add_parser("build")
    b.add_argument("--out", required=True)
    b.add_argument("--kid")
    b.add_argument("--key")
    u = sub.add_parser("upload")
    u.add_argument("--kid")
    u.add_argument("--key")
    f = sub.add_parser("fcm")
    f.add_argument("--id", required=True)
    f.add_argument("--target", default="all")
    f.add_argument("--send", action="store_true")
    args = ap.parse_args(argv)
    return {"check": cmd_check, "build": cmd_build, "upload": cmd_upload, "fcm": cmd_fcm}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
