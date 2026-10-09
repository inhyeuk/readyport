"""GitHub Actions 배포·감시 도우미 (ARIA_OPS 12.8).

하위 명령
  rc-versions      packs/src 의 버전(+ 커밋된 관광지 서명본의 attractions_version_<CC>)으로 Remote Config '버전 포인터'만 바꾼다.
                   관광지 키는 한 번에 3개까지(Hosting 하루 한도), 7일 안 재공개는 경고.
                   kill_autofill_* · stale_banner · min_app_version 등 다른 키는 절대 건드리지 않는다
                   (템플릿 전체 배포는 ARIA가 켠 스위치를 되돌리므로 CI에서 쓰지 않는다).
  fcm-notify       바뀐 나라 팩의 토픽 country_{ISO2} 로 알림. 메시지에는 나라 코드만 담는다.
  purge-reports    보관 기간(expire_at)이 지난 익명 실패 리포트를 지운다 (개인정보처리방침: 1년 보관).
                   게시판 신고 기록도 신고한 날부터 1년이 지나고 처리가 끝났으면 지운다 (개인정보처리방침 3-2절).
  heartbeat-watch  ops/heartbeat 가 3일 넘게 멈추면 stale_banner 를 켜고 실패로 끝낸다(운영자에게 메일).
                   다시 살아나면 stale_banner 를 끈다.

환경 변수: FIREBASE_PROJECT_ID, GOOGLE_APPLICATION_CREDENTIALS(서비스 계정 JSON 경로)
"""
from __future__ import annotations

import argparse
import copy
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

VERSION_KEY_RE = re.compile(
    r"^(index_version|pack_version_[A-Z]{2}|recipe_version_[A-Z]{2}_[A-Z0-9_]+|attractions_version_[A-Z]{2})$")
ATTRACTIONS_KEY_RE = re.compile(r"^attractions_version_[A-Z]{2}$")
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "packs"
# 한 번 실행에서 올리는 관광지 버전 키 상한 (SPEC_v5 리뷰: Spark Hosting 하루 360MB — 여러 나라가 같은 날 받으면 넘칠 수 있다)
ATTRACTIONS_MAX_PER_RUN = 3
ATTRACTIONS_MIN_DAYS = 7
SCOPE_FCM = "https://www.googleapis.com/auth/firebase.messaging"


# ---------------- 순수 함수 (테스트 대상) ----------------

def pack_versions(src: pathlib.Path, assets: pathlib.Path | None = ASSETS) -> dict[str, str]:
    """packs/src 에서 {RC 키: 버전}. 관광지는 커밋된 서명본(assets = Hosting 에 실제로 올라가는 것)의 버전 (SPEC_v5 §4.1)."""
    out = {"index_version": json.loads((src / "index.json").read_text(encoding="utf-8"))["version"]}
    for p in sorted(src.glob("[A-Z][A-Z]/pack.json")):
        d = json.loads(p.read_text(encoding="utf-8"))
        out[f"pack_version_{d['country']}"] = d["version"]
    for p in sorted((src / "recipes").glob("*.json")):
        d = json.loads(p.read_text(encoding="utf-8"))
        out[f"recipe_version_{p.stem}"] = d["version"]
    if assets is not None and assets.is_dir():
        for p in sorted(assets.glob("[A-Z][A-Z]/attractions.json")):
            d = json.loads(p.read_text(encoding="utf-8"))
            if d.get("doc_type") == "attractions" and d.get("release") == "published" and not d.get("sample"):
                out[f"attractions_version_{p.parent.name}"] = d["version"]
    return out


def _current_value(template: dict, key: str) -> str | None:
    p = (template.get("parameters") or {}).get(key)
    if p is None:
        for g in (template.get("parameterGroups") or {}).values():
            p = (g.get("parameters") or {}).get(key, p)
    return ((p or {}).get("defaultValue") or {}).get("value")


def _version_date(v: str | None):
    import datetime as dt
    m = re.match(r"^(\d{4})\.(\d{2})\.(\d{2})-\d+$", v or "")
    return dt.date(int(m.group(1)), int(m.group(2)), int(m.group(3))) if m else None


def limit_attraction_keys(template: dict, versions: dict[str, str],
                          max_changes: int = ATTRACTIONS_MAX_PER_RUN) -> tuple[dict[str, str], list[str]]:
    """관광지 버전 키는 한 번에 max_changes 개까지만 올린다(나머지는 이번에는 그대로 두고 다음 실행으로).
    같은 나라를 7일 안에 다시 올리면 경고만(버전 문자열의 날짜로 계산). (남길 versions, 경고 목록)."""
    out, warnings, changing = {}, [], []
    for k, v in versions.items():
        if ATTRACTIONS_KEY_RE.match(k) and _current_value(template, k) != v:
            changing.append(k)
            continue
        out[k] = v
    for i, k in enumerate(sorted(changing)):
        if i >= max_changes:
            warnings.append(f"{k}: 이번 실행 상한({max_changes}개) 초과 — 다음 실행(workflow_dispatch)에서 올린다")
            continue
        old, new = _version_date(_current_value(template, k)), _version_date(versions[k])
        if old and new and (new - old).days < ATTRACTIONS_MIN_DAYS:
            warnings.append(f"{k}: 지난 버전과 {(new - old).days}일 차이(7일 미만) — 받는 양이 늘 수 있음")
        out[k] = versions[k]
    return out, warnings


def _without(template: dict, keys: set[str]) -> dict:
    t = copy.deepcopy(template)
    for k in keys:
        (t.get("parameters") or {}).pop(k, None)
        for g in (t.get("parameterGroups") or {}).values():
            (g.get("parameters") or {}).pop(k, None)
    t.pop("version", None)
    return t


def apply_versions(template: dict, versions: dict[str, str]) -> tuple[dict, list[str]]:
    """버전 키만 바꾼 템플릿 사본과 바뀐 키 목록. 허용 밖 키가 바뀌면 예외."""
    for k in versions:
        if not VERSION_KEY_RE.match(k):
            raise ValueError(f"버전 키가 아님: {k}")
    t = copy.deepcopy(template)
    changed = []
    params = t.setdefault("parameters", {})
    for k, v in versions.items():
        p = params.get(k)
        if p is None:
            for g in (t.get("parameterGroups") or {}).values():
                if k in (g.get("parameters") or {}):
                    p = g["parameters"][k]
        if p is None:
            params[k] = {"defaultValue": {"value": v}, "valueType": "STRING", "description": "팩 버전 포인터 (CI)"}
            changed.append(k)
        elif (p.get("defaultValue") or {}).get("value") != v:
            p["defaultValue"] = {"value": v}
            changed.append(k)
    if _without(t, set(versions)) != _without(template, set(versions)):
        raise RuntimeError("버전 키 밖이 바뀌었다 — 중단")
    return t, changed


def changed_countries(changed_files: list[str]) -> list[str]:
    """git diff 파일 목록에서 바뀐 나라 팩(ISO2)."""
    out = set()
    for f in changed_files:
        m = re.match(r"^packs/src/([A-Z]{2})/pack\.json$", f.strip().replace("\\", "/"))
        if m:
            out.add(m.group(1))
    return sorted(out)


def fcm_message(country: str) -> dict:
    """나라 코드만 담는다. 문구는 앱에 들어 있는 것을 쓴다(PolicyMessagingService)."""
    if not re.match(r"^[A-Z]{2}$", country):
        raise ValueError(country)
    return {"message": {"topic": f"country_{country}", "data": {"country": country},
                        "android": {"priority": "NORMAL"}}}


def expired_reports_query(now_iso: str, limit: int = 500) -> dict:
    """expire_at < now 인 field_reports."""
    return {
        "from": [{"collectionId": "field_reports"}],
        "where": {"fieldFilter": {"field": {"fieldPath": "expire_at"}, "op": "LESS_THAN",
                                  "value": {"timestampValue": now_iso}}},
        "limit": limit,
    }


def purge_expired(firestore, now_iso: str, max_rounds: int = 20) -> int:
    """지운 개수. 한 번에 500개씩, 남은 게 없을 때까지."""
    total = 0
    for _ in range(max_rounds):
        docs = firestore.run_query(expired_reports_query(now_iso))
        if not docs:
            break
        for d in docs:
            firestore.delete_document(d["_name"])
            total += 1
    return total


BOARD_REPORT_DAYS = 365


def board_reports_query(cutoff_iso: str, after_iso: str | None = None, limit: int = 300) -> dict:
    """게시판 신고(board_posts/{p}/reports, …/comments/{c}/reports) 가운데 at < cutoff, 오래된 순.
    field_reports 와는 컬렉션 이름이 달라 섞이지 않는다. 색인: firestore.indexes.json fieldOverrides(reports.at, COLLECTION_GROUP)."""
    q = {
        "from": [{"collectionId": "reports", "allDescendants": True}],
        "where": {"fieldFilter": {"field": {"fieldPath": "at"}, "op": "LESS_THAN", "value": {"timestampValue": cutoff_iso}}},
        "orderBy": [{"field": {"fieldPath": "at"}, "direction": "ASCENDING"}],
        "limit": limit,
    }
    if after_iso:
        q["startAt"] = {"values": [{"timestampValue": after_iso}], "before": False}
    return q


def report_target_path(report_name: str) -> str | None:
    """신고 문서 이름 → 신고한 글·댓글 문서 경로(documents/ 뒤). 게시판 신고가 아니면 None."""
    tail = report_name.split("/documents/", 1)[-1]
    if not tail.startswith("board_posts/") or "/reports/" not in tail:
        return None
    return tail.rsplit("/reports/", 1)[0]


def report_settled(target: dict | None) -> bool:
    """처리가 끝났는지: 글·댓글이 없어졌거나, 운영자가 가렸거나, 지웠거나, 신고 수를 0으로 정리했다(운영자 대기열에 없음)."""
    if target is None:
        return True
    if target.get("hidden") is True or target.get("deleted") is True:
        return True
    return int(target.get("reportCount") or 0) == 0


def purge_board_reports(firestore, cutoff_iso: str, max_rounds: int = 20) -> int:
    """신고한 날부터 1년이 지났고 처리가 끝난 게시판 신고를 지운다. 아직 대기 중인 신고는 남긴다."""
    total = 0
    after = None
    seen: set[str] = set()
    targets: dict[str, dict | None] = {}
    for _ in range(max_rounds):
        docs = [d for d in firestore.run_query(board_reports_query(cutoff_iso, after)) if d["_name"] not in seen]
        if not docs:
            break
        for d in docs:
            seen.add(d["_name"])
            path = report_target_path(d["_name"])
            if path is None:
                continue
            if path not in targets:
                targets[path] = firestore.get_document(path)
            if report_settled(targets[path]):
                firestore.delete_document(d["_name"])
                total += 1
        last = docs[-1].get("at")
        after = last if isinstance(last, str) else (last.strftime("%Y-%m-%dT%H:%M:%S.%fZ") if last is not None else None)
        if after is None:
            break
    return total


# ---------------- 실행 ----------------

def _clients():  # pragma: no cover - 실제 네트워크
    import os

    from ops.aria.actions.kill_switch import RemoteConfigClient
    from ops.aria.gcp import SCOPE_DATASTORE, SCOPE_REMOTE_CONFIG, FirestoreRest, ServiceAccountTokenProvider
    from ops.aria.net import urllib_fetch

    project = os.environ["FIREBASE_PROJECT_ID"]
    cred = os.environ["GOOGLE_APPLICATION_CREDENTIALS"]
    rc = RemoteConfigClient(project, ServiceAccountTokenProvider(cred, [SCOPE_REMOTE_CONFIG]), urllib_fetch)
    fs = FirestoreRest(project, ServiceAccountTokenProvider(cred, [SCOPE_DATASTORE]), urllib_fetch)
    fcm_tokens = ServiceAccountTokenProvider(cred, [SCOPE_FCM])
    return project, rc, fs, fcm_tokens, urllib_fetch


def cmd_rc_versions(args) -> int:  # pragma: no cover
    versions = pack_versions(ROOT / "packs/src")
    _, rc, _, _, _ = _clients()
    template, etag = rc.get()
    versions, warnings = limit_attraction_keys(template, versions)
    for w in warnings:
        print(f"::warning title=관광지 버전::{w}")
    new_t, changed = apply_versions(template, versions)
    print("바뀔 키:", changed or "없음")
    if changed and not args.dry_run:
        rc.put(new_t, etag)
        print("Remote Config 버전 갱신 완료")
    return 0


def cmd_fcm_notify(args) -> int:  # pragma: no cover
    files = pathlib.Path(args.changed_files).read_text(encoding="utf-8").splitlines()
    countries = changed_countries(files)
    print("알릴 나라:", countries or "없음")
    if not countries or args.dry_run:
        return 0
    project, _, _, tokens, fetch = _clients()
    url = f"https://fcm.googleapis.com/v1/projects/{project}/messages:send"
    for c in countries:
        resp = fetch("POST", url, headers={"Authorization": f"Bearer {tokens.get_token()}",
                                           "Content-Type": "application/json"},
                     data=json.dumps(fcm_message(c)).encode("utf-8"), timeout=20)
        print(c, resp.status)
        if resp.status != 200:
            # 403이면 대개 서비스 계정에 'Firebase Cloud Messaging API 관리자' 역할이 없거나 API가 꺼져 있다
            print(resp.text[:600])
            print(f"::warning title=FCM 알림 실패 ({c})::HTTP {resp.status}. 403이면 서비스 계정에 "
                  "'Firebase Cloud Messaging API 관리자' 역할이 없거나 FCM API가 꺼져 있어요.")
            return 1
    return 0


def cmd_purge_reports(args) -> int:  # pragma: no cover
    import datetime as dt
    _, _, fs, _, _ = _clients()
    now = dt.datetime.now(dt.timezone.utc)
    now_iso = now.strftime("%Y-%m-%dT%H:%M:%SZ")
    cutoff_iso = (now - dt.timedelta(days=BOARD_REPORT_DAYS)).strftime("%Y-%m-%dT%H:%M:%SZ")
    if args.dry_run:
        print("지울 대상:", len(fs.run_query(expired_reports_query(now_iso))), "개 (시험 실행)")
        print("1년 지난 게시판 신고(처리 여부 확인 전):", len(fs.run_query(board_reports_query(cutoff_iso))), "개 (시험 실행)")
        return 0
    print("지움:", purge_expired(fs, now_iso), "개")
    print("게시판 신고 지움:", purge_board_reports(fs, cutoff_iso), "개")
    return 0


def cmd_heartbeat_watch(args) -> int:  # pragma: no cover
    from ops.aria.actions.kill_switch import set_stale_banner
    from ops.aria.heartbeat import HEARTBEAT_DOC, check_stale

    _, rc, fs, _, _ = _clients()
    doc = fs.get_document(HEARTBEAT_DOC) or {}
    stale = check_stale(doc.get("last_check"), days=args.days)
    print(f"last_check={doc.get('last_check')} stale={stale}")
    if stale:
        # 안전한 방향: 앱에 '정보 점검이 늦어지고 있어요' 배너
        print(set_stale_banner(rc, True, dry_run=args.dry_run).to_dict())
        return 2
    print(set_stale_banner(rc, False, heartbeat_recovered=True, dry_run=args.dry_run).to_dict())
    return 0


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("rc-versions")
    a.add_argument("--dry-run", action="store_true")
    b = sub.add_parser("fcm-notify")
    b.add_argument("--changed-files", required=True, help="git diff --name-only 결과 파일")
    b.add_argument("--dry-run", action="store_true")
    d = sub.add_parser("purge-reports")
    d.add_argument("--dry-run", action="store_true")
    c = sub.add_parser("heartbeat-watch")
    c.add_argument("--days", type=float, default=3)
    c.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)
    return {"rc-versions": cmd_rc_versions, "fcm-notify": cmd_fcm_notify, "heartbeat-watch": cmd_heartbeat_watch,
            "purge-reports": cmd_purge_reports}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
