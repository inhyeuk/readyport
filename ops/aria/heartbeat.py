"""하트비트 (ARIA_OPS 12.7).

- write_heartbeat: 점검이 끝나면 Firestore `ops/heartbeat` 에 {last_check, jobs} 를 쓴다.
- check_stale: GitHub Actions watchdog 이 쓰는 순수 함수. 마지막 점검이 days 일 넘게 없으면 True.
  True 면 watchdog 이 운영자에게 알리고 actions.kill_switch.set_stale_banner(on=True) 를 부른다.

watchdog 에서 쓰는 예:
    python -m ops.aria.heartbeat --check   (종료 코드 0=정상, 2=멈춤)
"""
from __future__ import annotations

import argparse
import datetime as _dt
import sys
from typing import Optional, Union

HEARTBEAT_DOC = "ops/heartbeat"


def _to_dt(v: Union[str, _dt.datetime, None]) -> Optional[_dt.datetime]:
    if v is None or v == "":
        return None
    if isinstance(v, _dt.datetime):
        return v if v.tzinfo else v.replace(tzinfo=_dt.timezone.utc)
    from .gcp import parse_timestamp
    return parse_timestamp(str(v))


def check_stale(last_check: Union[str, _dt.datetime, None], now: Union[str, _dt.datetime, None] = None,
                days: float = 3) -> bool:
    """기록이 없거나 now - last_check > days 이면 True(멈춤)."""
    last = _to_dt(last_check)
    now_dt = _to_dt(now) or _dt.datetime.now(_dt.timezone.utc)
    if last is None:
        return True
    return (now_dt - last) > _dt.timedelta(days=days)


def write_heartbeat(firestore, jobs: dict[str, str], now: Optional[_dt.datetime] = None) -> dict:
    """jobs: {단위: 상태}. 개인정보 없음."""
    now = now or _dt.datetime.now(_dt.timezone.utc)
    data = {"last_check": now, "jobs": {str(k)[:60]: str(v)[:40] for k, v in jobs.items()}}
    firestore.set_document(HEARTBEAT_DOC, data)
    return data


def main(argv=None) -> int:  # pragma: no cover - 얇은 CLI
    ap = argparse.ArgumentParser(description="ARIA 하트비트 점검")
    ap.add_argument("--check", action="store_true", help="Firestore 의 last_check 가 오래됐는지")
    ap.add_argument("--days", type=float, default=3)
    args = ap.parse_args(argv)
    if not args.check:
        ap.print_help()
        return 1
    from .config import load_config
    from .gcp import SCOPE_DATASTORE, FirestoreRest, ServiceAccountTokenProvider
    from .net import urllib_fetch
    cfg = load_config()
    fs = FirestoreRest(cfg.firebase_project_id,
                       ServiceAccountTokenProvider(cfg.google_credentials_path, [SCOPE_DATASTORE]), urllib_fetch)
    doc = fs.get_document(HEARTBEAT_DOC) or {}
    stale = check_stale(doc.get("last_check"), days=args.days)
    print(f"last_check={doc.get('last_check')} stale={stale}")
    return 2 if stale else 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
