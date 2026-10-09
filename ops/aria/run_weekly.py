"""주간 실행기 (ARIA 가 부른다): 관광지 공식 출처 확인 → 자동 갱신 PR, 이용자 평점 집계. 사장님 결정 2026-10-09 ①.

    python -m ops.aria.run_weekly --list-units                 # 단위 목록(네트워크 없음)
    python -m ops.aria.run_weekly                              # 시험(기본): 조회만 — 저장·알림·쓰기·Claude 없음
    python -m ops.aria.run_weekly --live --step watch          # 월 06:00. 공식 페이지 확인(240초 예산, 남으면 deferred)
    python -m ops.aria.run_weekly --live --step watch --retry-failed   # 10분마다 rerun_later 가 빌 때까지
    python -m ops.aria.run_weekly --live --step update         # 오래 걸림(Claude). watchdog 밖 백그라운드 작업으로
    python -m ops.aria.run_weekly --live --step ratings        # 평점 집계(Firestore 필요)

설계 (run_daily 와 같은 원칙)
- 공식 페이지 주소 하나 = 단위 하나. 단위마다 시간 제한, 전체 예산을 넘기면 deferred 로 남겨 --retry-failed 로 잇는다.
- 다시 시도는 error·timeout 만. manual_check_needed(봇 차단·robots 막음)는 다시 돌지 않는다.
- 감지는 LLM 없이. 같은 변경 지문은 한 번만 등록·알림.
- update 는 이번 주 확인이 끝난 나라만(묶음을 쪼개지 않게). Claude 상한: CLAUDE_DAILY_CAP + ATTRACTIONS_WEEKLY_CAP.
- 휴관·공사 표현이 새로 늘면 attraction_flags/{CC} 에 '확인 중' 표시(안전한 방향 — 자동).
"""
from __future__ import annotations

import argparse
import datetime as _dt
import io
import json
import pathlib
import sys
import time
from typing import Callable, Optional

from .actions import attraction_flags
from .approvals import telegram_cmd
from .config import Config, load_config
from .fingerprint_store import FingerprintStore
from .jobs import attractions_update, attractions_watch, ratings_weekly
from .models import RERUN_LATER, STATUS_CHANGED, UnitResult
from .robots import RobotsCache
from .run_daily import StdoutNotifier, Unit, run_units

STEPS = ("watch", "update", "ratings", "all")


def week_key(today: _dt.date) -> str:
    return attractions_update.iso_week(today)


def _runs_file(cfg: Config, today: _dt.date) -> pathlib.Path:
    return pathlib.Path(cfg.data_dir) / "runs" / f"weekly-{week_key(today)}.json"


def load_week_state(cfg: Config, today: _dt.date) -> dict[str, str]:
    p = _runs_file(cfg, today)
    return json.loads(p.read_text(encoding="utf-8")) if p.exists() else {}


def save_week_state(cfg: Config, today: _dt.date, state: dict[str, str]) -> None:
    p = _runs_file(cfg, today)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(state, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")


def build_watch_units(cfg: Config, store, fetcher, *, persist: bool, countries: Optional[list[str]] = None,
                      now: Optional[_dt.datetime] = None, robots: Optional[RobotsCache] = None) -> list[Unit]:
    robots = robots or RobotsCache(fetcher, cfg.user_agent, cfg.http_timeout_sec)
    units = []
    for cc in attractions_watch.attraction_countries(cfg.repo_root):
        if countries and cc not in countries:
            continue
        for t in attractions_watch.collect_targets(cfg.repo_root, cc):
            units.append(Unit(t.unit, lambda t=t: attractions_watch.check_target(
                t, store, fetcher, robots, evidence_dir=pathlib.Path(cfg.attractions_evidence_dir), persist=persist,
                timeout=cfg.http_timeout_sec, user_agent=cfg.user_agent, now=now)))
    return units


def unit_country(unit_name: str) -> str:
    return unit_name.split(":")[1] if unit_name.startswith("att:") else ""


def incomplete_countries(state: dict[str, str], all_units: list[str]) -> set[str]:
    return {unit_country(u) for u in all_units if state.get(u) is None or state.get(u) in RERUN_LATER}


def register_and_notify(results: dict[str, UnitResult], store, notifier, *, persist: bool) -> list[dict]:
    new_rows = []
    for r in results.values():
        for ch in r.changes:
            is_new = store.register(ch) if persist else not store.seen(ch.fingerprint)
            if not is_new:
                continue
            if persist:
                attractions_update.remember_change(store, ch, r.extra.get("snapshots") or [])
            new_rows.append({"fingerprint": ch.fingerprint, "summary": ch.summary})
    if new_rows:
        try:
            notifier.send(telegram_cmd.format_attractions_changes(new_rows))
            if persist:
                for row in new_rows:
                    store.mark_notified(row["fingerprint"])
        except Exception:  # noqa: BLE001 — 알림 실패해도 갱신 흐름은 계속(지문은 등록됨)
            pass
    return new_rows


def apply_flags(results: dict[str, UnitResult], store, firestore, notifier, *, dry_run: bool,
                now: _dt.datetime) -> list[dict]:
    by_cc: dict[str, list[UnitResult]] = {}
    for name, r in results.items():
        by_cc.setdefault(unit_country(name), []).append(r)
    out = []
    for cc, rs in sorted(by_cc.items()):
        if cc not in attraction_flags.COUNTRIES:
            continue
        old = store.get_value(attractions_watch.flags_key(cc)) or {}
        new = attractions_watch.update_flag_state(old, rs)
        if not dry_run:
            store.set_value(attractions_watch.flags_key(cc), new)
        if not new and not old and firestore is None:
            continue
        try:
            res = attraction_flags.sync_flags(firestore, cc, sorted(new), now, dry_run=dry_run)
        except Exception as e:  # noqa: BLE001
            notifier.send(f"[레디포트] {cc} 관광지 '확인 중' 표시 쓰기 실패: {type(e).__name__}")
            out.append({"cc": cc, "result": "error"})
            continue
        if res.changed or set(new) != set(old):
            notifier.send(telegram_cmd.format_attraction_flags(cc, sorted(new), res.changed))
        out.append(res.to_dict())
    return out


def run_weekly(cfg: Config, *, fetcher, step: str = "all", dry_run: bool = True, notifier=None, firestore=None,
               github=None, store: Optional[FingerprintStore] = None, countries: Optional[list[str]] = None,
               retry_failed: bool = False, unit_timeout: float = 60, retries: int = 1, budget_sec: float = 240,
               today: Optional[_dt.date] = None, now: Optional[_dt.datetime] = None,
               sleep: Callable[[float], None] = time.sleep, cmd_runner=None, claude_runner=None) -> dict:
    if step not in STEPS:
        raise ValueError(f"step 은 {STEPS} 중 하나")
    today = today or _dt.datetime.now().astimezone().date()
    now = now or _dt.datetime.now(_dt.timezone.utc)
    persist = not dry_run
    if dry_run:
        notifier = StdoutNotifier("[시험] ")
    notifier = notifier or StdoutNotifier()
    own_store = store is None
    store = store or FingerprintStore(cfg.db_path)
    summary: dict = {"date": today.isoformat(), "week": week_key(today), "dry_run": dry_run, "step": step}
    try:
        state = load_week_state(cfg, today)
        all_units = build_watch_units(cfg, store, fetcher, persist=persist, countries=countries, now=now)
        if step in ("watch", "all"):
            units = all_units
            if retry_failed:
                units = [u for u in units if state.get(u.name) in RERUN_LATER or u.name not in state]
            results = run_units(units, unit_timeout=unit_timeout, retries=retries, budget_sec=budget_sec,
                                interval_sec=cfg.request_interval_sec, sleep=sleep)
            new_rows = register_and_notify(results, store, notifier, persist=persist)
            flags = apply_flags(results, store, firestore, notifier, dry_run=dry_run, now=now)
            state.update({k: v.status for k, v in results.items()})
            if persist:
                save_week_state(cfg, today, state)
            summary["watch"] = {
                "units": {k: {"status": v.status, "message": v.message} for k, v in results.items()},
                "new_changes": len(new_rows), "flags": flags,
                "rerun_later": sorted(k for k, v in results.items() if v.status in RERUN_LATER),
                "changed": sorted(k for k, v in results.items() if v.status == STATUS_CHANGED),
            }
        if step in ("update", "all"):
            skip = incomplete_countries(state, [u.name for u in all_units])
            kw = {}
            if cmd_runner is not None:
                kw["cmd_runner"] = cmd_runner
            if claude_runner is not None:
                kw["claude_runner"] = claude_runner
            summary["update"] = attractions_update.run_update(cfg, store, github=github, notifier=notifier,
                                                              today=today, dry_run=dry_run, countries=countries,
                                                              skip=skip, **kw)
        if step in ("ratings", "all"):
            summary["ratings"] = ratings_weekly.run(cfg, firestore, dry_run=dry_run, now=now, countries=countries)
        return summary
    finally:
        if own_store:
            store.close()


def main(argv=None) -> int:  # pragma: no cover - 실제 연결을 만드는 얇은 CLI
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    ap = argparse.ArgumentParser(description="레디포트 주간 작업 (ARIA): 관광지 확인·자동 갱신, 평점 집계")
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--dry-run", action="store_true", default=True, help="기본값. 조회만")
    g.add_argument("--live", action="store_true", help="실제 실행(저장·알림·Claude·PR·Firestore 쓰기)")
    ap.add_argument("--step", choices=STEPS, default="watch")
    ap.add_argument("--only", help="나라 코드 쉼표 목록 (예: JP,VN)")
    ap.add_argument("--retry-failed", action="store_true")
    ap.add_argument("--unit-timeout", type=float, default=60)
    ap.add_argument("--budget-sec", type=float, default=240)
    ap.add_argument("--retries", type=int, default=1)
    ap.add_argument("--list-units", action="store_true")
    args = ap.parse_args(argv)

    from .net import urllib_fetch
    cfg = load_config()
    countries = [c.strip().upper() for c in args.only.split(",")] if args.only else None
    if args.list_units:
        store = FingerprintStore(":memory:")
        for u in build_watch_units(cfg, store, urllib_fetch, persist=False, countries=countries):
            print(u.name)
        return 0
    firestore = None
    if cfg.firebase_project_id and cfg.google_credentials_path:
        from .gcp import SCOPE_DATASTORE, FirestoreRest, ServiceAccountTokenProvider
        firestore = FirestoreRest(cfg.firebase_project_id, ServiceAccountTokenProvider(
            cfg.google_credentials_path, [SCOPE_DATASTORE]), urllib_fetch)
    from .actions.github_pr import GitHubClient, credential_token
    github = GitHubClient(cfg.github_repo, lambda: credential_token(cwd=cfg.repo_root), urllib_fetch)
    summary = run_weekly(cfg, fetcher=urllib_fetch, step=args.step, dry_run=not args.live, firestore=firestore,
                         github=github, countries=countries, retry_failed=args.retry_failed,
                         unit_timeout=args.unit_timeout, retries=args.retries, budget_sec=args.budget_sec)
    print(json.dumps(summary, ensure_ascii=False, indent=1, default=str))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
