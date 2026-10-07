"""운영자용 공지 도우미 (docs/NOTICES_PUSH.md).

  python tools/notices/notice.py new --id summer-tips --type normal            # notices.json 에 꺼진 공지 틀 하나 추가
  python tools/notices/notice.py new --id autumn-event --type event --promo    # 광고성 공지 틀 (제목이 (광고)로 시작)
  python tools/notices/notice.py check                                         # 검증 + 지금 상태 표
  python tools/notices/notice.py push --id welcome-050 --target all            # 시험: 보낼 내용만 보여 준다(네트워크 없음)
  python tools/notices/notice.py push --id welcome-050 --target TH --send       # GitHub Actions(notices.yml)로 실제 보내기
  python tools/notices/notice.py push --id welcome-050 --target all --remote-dry-run   # Actions 를 시험 모드로 돌려 보기

push --send 는 GitHub REST API 로 notices.yml 의 workflow_dispatch 를 부른다. 토큰은 이 저장소 전용 자격 증명
(git -c credential.namespace=readyport credential fill)에서 꺼내 쓰고, 화면·파일 어디에도 남기지 않는다.
FCM 서비스 계정 키는 이 PC에 없어도 된다 — 보내는 일은 GitHub Actions 가 secret 으로 한다.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build_notices as bn  # noqa: E402

REPO = "inhyeuk/readyport"
WORKFLOW = "notices.yml"
API = "https://api.github.com"


# ---------------- 순수 함수 (테스트 대상) ----------------

def scaffold(notice_id: str, notice_type: str, promo: bool, now: dt.datetime) -> dict:
    """꺼진(active false) 공지 틀. 글을 고치고 active 를 true 로 바꿔 PR 을 올린다."""
    start = (now.astimezone(bn.KST) + dt.timedelta(days=1)).replace(hour=9, minute=0, second=0, microsecond=0)
    title = "(광고) 제목을 적어 주세요" if promo else "제목을 적어 주세요"
    return {
        "id": notice_id,
        "version": 1,
        "active": False,
        "type": notice_type,
        "category": "promo" if promo else "service",
        "title_ko": title,
        "body_ko": "본문을 적어 주세요. 쉬운 말(해요체)로 짧게 적어요.",
        "start": start.isoformat(),
        "end": (start + dt.timedelta(days=14)).replace(hour=23, minute=59).isoformat(),
        "priority": 0,
        "audience": ["all"],
    }


def add_notice(doc: dict, notice: dict) -> dict:
    if bn.find_notice(doc, notice["id"]) is not None:
        raise ValueError(f"id '{notice['id']}' 가 이미 있어요")
    out = dict(doc)
    out["notices"] = list(doc.get("notices", [])) + [notice]
    return out


def state_of(n: dict, now: dt.datetime) -> str:
    if not n.get("active"):
        return "꺼짐(초안)"
    start = bn.parse_time(n.get("start"))
    end = bn.parse_time(n.get("end")) if n.get("end") else None
    if start and start > now:
        return "예약됨"
    if end and end <= now:
        return "끝남"
    return "보이는 중"


def dispatch_request(notice_id: str, target: str, dry_run: bool, ref: str = "main") -> tuple[str, dict]:
    """workflow_dispatch 요청 (주소, 본문). inputs 는 모두 문자열."""
    url = f"{API}/repos/{REPO}/actions/workflows/{WORKFLOW}/dispatches"
    body = {"ref": ref, "inputs": {"notice_id": notice_id, "target": target, "dry_run": "true" if dry_run else "false"}}
    return url, body


def parse_credential(output: str) -> str | None:
    for line in output.splitlines():
        if line.startswith("password="):
            return line[len("password="):].strip() or None
    return None


def github_token(run=subprocess.run) -> str | None:  # pragma: no cover - 운영자 PC
    r = run(["git", "-c", "credential.namespace=readyport", "credential", "fill"],
            input="protocol=https\nhost=github.com\n\n", capture_output=True, text=True, timeout=120)
    return parse_credential(r.stdout) if r.returncode == 0 else None


def push(args, now: dt.datetime, doc: dict, fetch=None, token=None, out=print) -> int:
    """시험(기본): 네트워크 없이 보낼 내용만 보여 준다. --send / --remote-dry-run: Actions 를 부른다."""
    errors, warnings = bn.push_problems(doc, args.id, args.target, now)
    for w in warnings:
        out("경고: " + w)
    if errors:
        for e in errors:
            out("안 돼요: " + e)
        return 1
    message = bn.fcm_message(bn.find_notice(doc, args.id), args.target)
    remote = args.send or args.remote_dry_run
    url, body = dispatch_request(args.id, args.target, dry_run=not args.send, ref=args.ref)
    out("FCM 메시지(Actions 가 보낼 것): " + json.dumps(message, ensure_ascii=False))
    out("GitHub Actions 요청: POST " + url + " " + json.dumps(body, ensure_ascii=False))
    if not remote:
        out("시험이라 아무것도 보내지 않았어요. 정말 보내려면 --send 를 붙여요.")
        return 0
    t = (token or github_token)()
    if not t:
        out("GitHub 자격 증명을 찾지 못했어요 (git -c credential.namespace=readyport credential fill)")
        return 1
    if fetch is None:  # pragma: no cover
        from ops.aria.net import urllib_fetch as fetch
    resp = fetch("POST", url, headers={"Authorization": f"Bearer {t}", "Accept": "application/vnd.github+json",
                                       "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json"},
                 data=json.dumps(body).encode("utf-8"), timeout=30)
    if resp.status != 204:
        out(f"GitHub 가 거절했어요: HTTP {resp.status} {resp.text[:300]}")
        return 1
    out("보냈어요. 진행은 https://github.com/" + REPO + "/actions/workflows/" + WORKFLOW + " 에서 봐요"
        + (" (시험 모드 — 알림은 가지 않아요)" if not args.send else ""))
    return 0


# ---------------- 실행 ----------------

def main(argv=None) -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):  # pragma: no cover
        pass
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    n = sub.add_parser("new")
    n.add_argument("--id", required=True)
    n.add_argument("--type", choices=bn.TYPES, default="normal")
    n.add_argument("--promo", action="store_true", help="광고성 정보(category promo)")
    sub.add_parser("check")
    p = sub.add_parser("push")
    p.add_argument("--id", required=True)
    p.add_argument("--target", default="all", help="all 또는 나라 코드(TH 등)")
    p.add_argument("--send", action="store_true", help="정말 보낸다(Actions dry_run=false)")
    p.add_argument("--remote-dry-run", action="store_true", help="Actions 를 시험 모드로 돌린다")
    p.add_argument("--ref", default="main")
    args = ap.parse_args(argv)
    now = dt.datetime.now(dt.timezone.utc)
    doc = json.loads(bn.SOURCE.read_text(encoding="utf-8"))

    if args.cmd == "new":
        if not bn.ID_RE.match(args.id):
            print("id: 영문 소문자·숫자·-·_ 3~41자")
            return 1
        doc = add_notice(doc, scaffold(args.id, args.type, args.promo, now))
        bn.SOURCE.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"notices/notices.json 에 '{args.id}'(꺼짐)를 넣었어요. 글을 고치고 active 를 true 로 바꾼 뒤 check → PR.")
        return 0
    if args.cmd == "check":
        errors, warnings = bn.validate(doc, now, bn.known_countries())
        for w in warnings:
            print("경고: " + w)
        for e in errors:
            print("오류: " + e)
        for x in doc.get("notices", []):
            print(f"  {x.get('id'):<24} {x.get('type'):<7} {x.get('category', 'service'):<8} {state_of(x, now):<8} {x.get('title_ko')}")
        print("검증 통과" if not errors else f"오류 {len(errors)}개")
        return 1 if errors else 0
    return push(args, now, doc)


if __name__ == "__main__":
    sys.exit(main())
