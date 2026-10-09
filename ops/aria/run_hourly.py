"""1시간마다 실행기 (ARIA 가 부른다): 여행 계획 요청 처리 + AI 계획 신고 확인 + 하루 한 번 정리. 사장님 결정 2026-10-09 ②.

    python -m ops.aria.run_hourly                    # 시험(기본): 대기 요청 수만 본다 — 쓰기·Claude 없음
    python -m ops.aria.run_hourly --live             # 실제 처리(요청 → 계획 → plan_results), 하루 한 번 정리
    python -m ops.aria.run_hourly --live --cleanup force   # 정리를 지금 한 번 더
    python -m ops.aria.run_hourly --live --cleanup skip

- 계획 하나에 Claude 가 몇 분 걸릴 수 있다(PLAN_TIMEOUT_SEC). ARIA watchdog(300초) 밖의 백그라운드 작업으로 부른다.
  --budget-sec(기본 1500) 이 지나면 새 요청을 시작하지 않고 다음 시간으로 넘긴다.
- 상한: PLAN_DAILY_CAP(하루 계획 수, CLAUDE_DAILY_CAP 과 따로), PLAN_MAX_PER_RUN(한 번에 처리할 수).
- 출력·로그에는 요청 id·상태·개수만. 요청 내용은 남기지 않는다.
- AI 계획 신고(plan_flags, Play 정책): 지난 확인 뒤 새 신고를 세어 요약 "flags": {"new": n, "ids": [...]} 에 넣고,
  시험이 아니고 n>0 이면 알림 한 줄 '[레디포트] AI 계획 신고 n건: id…'(id 만, 이유·메모·계획 내용 없음).
- 알림은 표준 출력(StdoutNotifier) — 요약 JSON 앞줄로 나가고, ARIA 스킬(readyport_ops.hourly)이 그 줄을 텔레그램으로 보낸다.
"""
from __future__ import annotations

import argparse
import datetime as _dt
import io
import json
import sys
from typing import Optional

from .config import Config, load_config
from .fingerprint_store import FingerprintStore
from .jobs import plan_cleanup, plan_flags, plan_requests

CLEANUP_KEY = "plan_cleanup:last_day"


def run_hourly(cfg: Config, *, firestore, dry_run: bool = True, store: Optional[FingerprintStore] = None,
               engine=None, cleanup: str = "auto", budget_sec: float = 1500, notifier=None,
               now: Optional[_dt.datetime] = None, today: Optional[_dt.date] = None, monotonic=None) -> dict:
    today = today or _dt.datetime.now().astimezone().date()
    now = now or _dt.datetime.now(_dt.timezone.utc)
    own_store = store is None
    store = store or FingerprintStore(cfg.db_path)
    try:
        kw = {"monotonic": monotonic} if monotonic is not None else {}
        plans = plan_requests.run(cfg, firestore, store, engine=engine, dry_run=dry_run, now=now, today=today,
                                  budget_sec=budget_sec, **kw)
        out = {"date": today.isoformat(), "dry_run": dry_run, "plans": plans}
        try:
            out["flags"] = plan_flags.check(firestore, store, dry_run=dry_run)
        except Exception as e:  # noqa: BLE001 - 신고 확인이 실패해도 계획 처리 요약은 낸다
            out["flags"] = {"status": "error", "error": type(e).__name__, "new": 0, "ids": []}
        done_today = store.get_value(CLEANUP_KEY) == today.isoformat()
        if cleanup == "force" or (cleanup == "auto" and not done_today):
            out["cleanup"] = plan_cleanup.run(cfg, firestore, dry_run=dry_run, now=now)
            if not dry_run and out["cleanup"].get("status") == "ok":
                store.set_value(CLEANUP_KEY, today.isoformat())
        if notifier is not None and not dry_run:
            failed = [p for p in plans.get("processed", []) if p.get("status") == "failed"]
            if plans.get("cap_reached") or failed:
                try:
                    notifier.send(f"[레디포트] 여행 계획: 완료 {sum(p.get('status') == 'done' for p in plans.get('processed', []))}, "
                                  f"실패 {len(failed)} ({', '.join(sorted({p.get('code', '') for p in failed}))})"
                                  + (" — 오늘 상한(PLAN_DAILY_CAP)에 닿아 남은 요청은 내일" if plans.get("cap_reached") else ""))
                except Exception:  # noqa: BLE001
                    pass
            line = plan_flags.notice(out["flags"])
            if line:
                try:
                    notifier.send(line)
                except Exception:  # noqa: BLE001
                    pass
        return out
    finally:
        if own_store:
            store.close()


def main(argv=None) -> int:  # pragma: no cover - 실제 연결을 만드는 얇은 CLI
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    ap = argparse.ArgumentParser(description="레디포트 1시간마다 작업 (ARIA): 여행 계획 요청")
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--dry-run", action="store_true", default=True)
    g.add_argument("--live", action="store_true")
    ap.add_argument("--cleanup", choices=("auto", "force", "skip"), default="auto")
    ap.add_argument("--budget-sec", type=float, default=1500)
    args = ap.parse_args(argv)
    from .net import urllib_fetch
    cfg = load_config()
    if not (cfg.firebase_project_id and cfg.google_credentials_path):
        print(json.dumps({"status": "not_configured",
                          "message": "FIREBASE_PROJECT_ID·GOOGLE_APPLICATION_CREDENTIALS(서비스 계정 aria-ops) 필요"},
                         ensure_ascii=False))
        return 0
    from .gcp import SCOPE_DATASTORE, FirestoreRest, ServiceAccountTokenProvider
    firestore = FirestoreRest(cfg.firebase_project_id, ServiceAccountTokenProvider(
        cfg.google_credentials_path, [SCOPE_DATASTORE]), urllib_fetch)
    from .run_daily import StdoutNotifier
    # 알림 줄은 요약 JSON 앞에 표준 출력으로 (ARIA 스킬이 '알림 줄'로 읽어 텔레그램에 보낸다). 시험이면 보내지 않는다
    summary = run_hourly(cfg, firestore=firestore, dry_run=not args.live, cleanup=args.cleanup,
                         budget_sec=args.budget_sec, notifier=StdoutNotifier() if args.live else None)
    print(json.dumps(summary, ensure_ascii=False, indent=1, default=str))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
