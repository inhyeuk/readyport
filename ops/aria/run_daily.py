"""매일 감지 실행기 (ARIA 가 부른다). ARIA_OPS 12.2-1·12.2-2·12.4.

    python -m ops.aria.run_daily                 # 기본 = 시험 실행(--dry-run): 조회만, 아무것도 쓰지 않음
    python -m ops.aria.run_daily --live          # 실제 실행: 스냅샷·지문 저장, 알림, 자동 입력 끄기, 하트비트
    python -m ops.aria.run_daily --live --retry-failed   # 오늘 실패·시간초과·미시작 단위만 다시
    python -m ops.aria.run_daily --list-units

설계
- 나라·양식·공지 하나가 한 '단위'. 단위마다 따로 시간 제한(--unit-timeout). 한 곳이 멈춰도 나머지는 계속.
- 전체 시간 예산(--budget-sec, 기본 240초)을 넘기면 남은 단위는 시작하지 않고 deferred 로 남긴다.
  ARIA watchdog(300초)에 전체가 죽지 않게 하려는 것. 다음 실행에서 --retry-failed 로 이어서 돈다.
- 다시 시도는 error·timeout 만. manual_check_needed(봇 차단)는 절대 다시 시도하지 않는다.
- 감지에 LLM 을 쓰지 않는다. 요약기(summarizer)는 선택이고, 실패해도 원본 diff 를 알림으로 보낸다.
- 같은 지문은 한 번만 알린다(fingerprint_store). 알림이 실패한 것은 다음 실행에서 다시 보낸다.
"""
from __future__ import annotations

import argparse
import datetime as _dt
import io
import json
import pathlib
import sys
import threading
import time
from dataclasses import dataclass
from typing import Callable, Optional, Protocol

from . import heartbeat as hb
from .actions import kill_switch
from .approvals import telegram_cmd
from .config import Config, load_config
from .detectors import form_structure_hash, mofa_entry_diff, news_keywords, notice_watch
from .fingerprint_store import FingerprintStore
from .models import (RERUN_LATER, RETRYABLE, STATUS_CHANGED, STATUS_DEFERRED, STATUS_ERROR, STATUS_NOT_CONFIGURED,
                     STATUS_TIMEOUT, STATUS_UNCHANGED, Change, UnitResult)
from .packs_util import list_countries, list_forms
from .signals import field_reports


class Notifier(Protocol):
    def send(self, text: str) -> None: ...


class StdoutNotifier:
    def __init__(self, prefix: str = ""):
        self.prefix = prefix
        self.sent: list[str] = []

    def send(self, text: str) -> None:
        self.sent.append(text)
        out = f"{self.prefix}{text}\n---\n"
        enc = getattr(sys.stdout, "encoding", None) or "utf-8"
        # 윈도 콘솔(cp949)에서 못 쓰는 글자는 ? 로
        sys.stdout.write(out.encode(enc, "replace").decode(enc, "replace"))
        sys.stdout.flush()


@dataclass
class Unit:
    name: str
    fn: Callable[[], UnitResult]
    network: bool = True


def run_with_timeout(unit: Unit, timeout: float) -> UnitResult:
    """단위 하나를 별도 스레드에서. 시간이 넘으면 기다리지 않고 timeout 으로 기록한다."""
    box: dict = {}

    def target():
        try:
            box["r"] = unit.fn()
        except Exception as e:  # noqa: BLE001 — 한 단위의 예외가 전체를 멈추면 안 된다
            box["r"] = UnitResult(unit.name, STATUS_ERROR, message=f"{type(e).__name__}: {e}"[:300])

    t = threading.Thread(target=target, name=f"aria-{unit.name}", daemon=True)
    t.start()
    t.join(timeout)
    if t.is_alive():
        return UnitResult(unit.name, STATUS_TIMEOUT, message=f"{timeout:.0f}초 초과")
    r = box["r"]
    r.unit = unit.name
    return r


def run_units(units: list[Unit], *, unit_timeout: float = 60, retries: int = 1, budget_sec: float = 240,
              interval_sec: float = 0, sleep: Callable[[float], None] = time.sleep,
              clock: Callable[[], float] = time.monotonic) -> dict[str, UnitResult]:
    start = clock()
    results: dict[str, UnitResult] = {}
    pending = list(units)
    for attempt in range(retries + 1):
        next_round = []
        first_net = True
        for u in pending:
            if clock() - start > budget_sec:
                results[u.name] = UnitResult(u.name, STATUS_DEFERRED, message="시간 예산 초과 — 다음에 --retry-failed")
                continue
            if u.network and not first_net and interval_sec:
                sleep(interval_sec)  # 요청 간격(크롤링 예절)
            if u.network:
                first_net = False
            r = run_with_timeout(u, unit_timeout)
            if attempt:
                r.message = f"(재시도 {attempt}) {r.message}"
            results[u.name] = r
            if r.status in RETRYABLE:
                next_round.append(u)
        pending = next_round
        if not pending:
            break
    return results


def build_units(cfg: Config, store: FingerprintStore, fetcher, *, persist: bool, today: _dt.date,
                firestore=None, now: Optional[_dt.datetime] = None, news_items: Optional[list] = None,
                only: Optional[list[str]] = None) -> list[Unit]:
    units: list[Unit] = []
    countries = list_countries(cfg.repo_root)
    units.append(Unit("mofa", lambda: mofa_entry_diff.run(cfg, fetcher, today, countries, persist)))
    for f in list_forms(cfg.repo_root):
        fid, url = f["form_id"], f["official_url"]
        units.append(Unit(f"form:{fid}", lambda fid=fid, url=url: form_structure_hash.check_form(
            fid, url, store, fetcher, cfg, persist, cfg.http_timeout_sec, cfg.user_agent)))
    for nid, url in cfg.notice_urls:
        units.append(Unit(f"notice:{nid}", lambda nid=nid, url=url: notice_watch.check_notice(
            nid, url, store, fetcher, cfg, persist, cfg.http_timeout_sec, cfg.user_agent)))
    units.append(Unit("field_reports", lambda: _field_unit(cfg, firestore, now), network=False))
    if news_items is not None:
        units.append(Unit("news", lambda: _news_unit(news_items), network=False))
    if only:
        units = [u for u in units if any(u.name == o or u.name.startswith(o + ":") for o in only)]
    return units


def _field_unit(cfg: Config, firestore, now: Optional[_dt.datetime]) -> UnitResult:
    if firestore is None:
        return UnitResult("field_reports", STATUS_NOT_CONFIGURED, message="Firestore 연결 없음")
    now = now or _dt.datetime.now(_dt.timezone.utc)
    decisions = field_reports.fetch_and_decide(firestore, now, cfg.field_window_hours, cfg.field_min_samples,
                                               cfg.field_min_failures, cfg.field_fail_rate)
    kills = [d for d in decisions if d.kill]
    return UnitResult("field_reports", STATUS_CHANGED if kills else STATUS_UNCHANGED,
                      message=f"양식 {len(decisions)}개 집계, 끌 것 {len(kills)}",
                      extra={"decisions": [d.to_dict() for d in decisions]})


def _news_unit(items: list) -> UnitResult:
    changes = news_keywords.to_changes(news_keywords.match_entry_policy_news(items))
    return UnitResult("news", STATUS_CHANGED if changes else STATUS_UNCHANGED, changes,
                      message=f"입국정책 뉴스 {len(changes)}건")


def notify_changes(results: dict[str, UnitResult], store: FingerprintStore, notifier: Notifier, *,
                   persist: bool, summarizer: Optional[Callable[[Change], str]] = None) -> list[str]:
    """새 지문만 알린다. 요약기가 실패해도 원본 diff 로 보낸다. 보낸 지문 목록."""
    sent = []
    for r in results.values():
        for ch in r.changes:
            is_new = store.register(ch) if persist else not store.seen(ch.fingerprint)
            if not is_new:
                continue
            if ch.detector == news_keywords.DETECTOR:
                text = telegram_cmd.format_news_alert(ch)
            else:
                llm = None
                if summarizer is not None:
                    try:
                        llm = summarizer(ch)
                    except Exception:  # noqa: BLE001 — 요약 실패(할당량 등)는 무시하고 원본을 보낸다
                        llm = None
                text = telegram_cmd.format_approval_request(ch, llm)
            try:
                notifier.send(text)
            except Exception:  # noqa: BLE001 — 다음 실행에서 list_unnotified 로 다시
                continue
            if persist:
                store.mark_notified(ch.fingerprint)
            sent.append(ch.fingerprint)
    return sent


def resend_unnotified(store: FingerprintStore, notifier: Notifier, skip: set[str]) -> list[str]:
    sent = []
    for row in store.list_unnotified():
        if row["fingerprint"] in skip:
            continue
        text = (f"[레디포트] (지난 알림 재전송) {row['summary']}\n증거: {row['evidence_path']}\n"
                f"지문: {row['fingerprint']}\n" + telegram_cmd.format_command("approve", fp=row["fingerprint"]))
        try:
            notifier.send(text)
        except Exception:  # noqa: BLE001
            continue
        store.mark_notified(row["fingerprint"])
        sent.append(row["fingerprint"])
    return sent


def apply_field_kills(results: dict[str, UnitResult], rc_client, notifier: Notifier, dry_run: bool) -> list[dict]:
    """실패율이 높은 양식은 자동 입력을 끈다(안전한 방향이라 자동). 끄기만 하고 켜지는 않는다."""
    r = results.get("field_reports")
    out = []
    if r is None:
        return out
    for d in r.extra.get("decisions", []):
        if not d["kill"]:
            continue
        if rc_client is None:
            notifier.send(f"[레디포트] {d['form_id']} 실패 많음({d['reason']}) — Remote Config 연결이 없어 직접 꺼 주세요")
            out.append({"form_id": d["form_id"], "result": "no_client"})
            continue
        try:
            res = kill_switch.kill_autofill_on(rc_client, d["form_id"], dry_run=dry_run)
        except Exception as e:  # noqa: BLE001
            notifier.send(f"[레디포트] {d['form_id']} 자동 입력 끄기 실패: {e}")
            out.append({"form_id": d["form_id"], "result": "error"})
            continue
        if res.changed:
            notifier.send(telegram_cmd.format_kill_notice(res.to_dict(), d))
        out.append({"form_id": d["form_id"], "result": res.to_dict()})
    return out


def _runs_file(cfg: Config, today: _dt.date) -> pathlib.Path:
    return cfg.data_dir / "runs" / f"{today.isoformat()}.json"


def load_run_state(cfg: Config, today: _dt.date) -> dict[str, str]:
    p = _runs_file(cfg, today)
    return json.loads(p.read_text(encoding="utf-8")) if p.exists() else {}


def save_run_state(cfg: Config, today: _dt.date, state: dict[str, str]) -> None:
    p = _runs_file(cfg, today)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(state, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")


def run_daily(cfg: Config, *, fetcher, dry_run: bool = True, notifier: Optional[Notifier] = None,
              summarizer=None, firestore=None, rc_client=None, store: Optional[FingerprintStore] = None,
              only: Optional[list[str]] = None, retry_failed: bool = False, unit_timeout: float = 60,
              retries: int = 1, budget_sec: float = 240, news_items: Optional[list] = None,
              today: Optional[_dt.date] = None, now: Optional[_dt.datetime] = None,
              sleep: Callable[[float], None] = time.sleep) -> dict:
    today = today or _dt.datetime.now().astimezone().date()
    persist = not dry_run
    if dry_run:
        # 시험 실행: 주입된 알림(텔레그램)으로 보내지 않고 화면에만
        notifier = StdoutNotifier("[시험] ")
    notifier = notifier or StdoutNotifier()
    own_store = store is None
    store = store or FingerprintStore(cfg.db_path)
    try:
        units = build_units(cfg, store, fetcher, persist=persist, today=today, firestore=firestore, now=now,
                            news_items=news_items, only=only)
        state = load_run_state(cfg, today)
        if retry_failed:
            units = [u for u in units if state.get(u.name) in RERUN_LATER or u.name not in state]
        results = run_units(units, unit_timeout=unit_timeout, retries=retries, budget_sec=budget_sec,
                            interval_sec=cfg.request_interval_sec, sleep=sleep)
        sent = notify_changes(results, store, notifier, persist=persist, summarizer=summarizer)
        if persist:
            sent += resend_unnotified(store, notifier, set(sent))
        kills = apply_field_kills(results, rc_client, notifier, dry_run)
        state.update({k: v.status for k, v in results.items()})
        heartbeat_written = False
        if persist:
            save_run_state(cfg, today, state)
            if firestore is not None:
                try:
                    hb.write_heartbeat(firestore, state, now)
                    heartbeat_written = True
                except Exception as e:  # noqa: BLE001
                    notifier.send(f"[레디포트] 하트비트 기록 실패: {e}")
        return {
            "date": today.isoformat(), "dry_run": dry_run,
            "units": {k: {"status": v.status, "message": v.message, "changes": len(v.changes)}
                      for k, v in results.items()},
            "notified": sent, "kills": kills, "heartbeat": heartbeat_written,
            "rerun_later": sorted(k for k, v in results.items() if v.status in RERUN_LATER),
        }
    finally:
        if own_store:
            store.close()


def main(argv=None) -> int:  # pragma: no cover - 실제 연결을 만드는 얇은 CLI
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    ap = argparse.ArgumentParser(description="레디포트 매일 감지 (ARIA)")
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--dry-run", action="store_true", default=True, help="기본값. 조회만 하고 아무것도 쓰지 않음")
    g.add_argument("--live", action="store_true", help="실제 실행(저장·알림·자동 입력 끄기·하트비트)")
    ap.add_argument("--only", help="쉼표로: mofa,form,form:TH_TDAC,notice,field_reports,news")
    ap.add_argument("--retry-failed", action="store_true")
    ap.add_argument("--unit-timeout", type=float, default=60)
    ap.add_argument("--budget-sec", type=float, default=240)
    ap.add_argument("--retries", type=int, default=1)
    ap.add_argument("--news-file", help="뉴스 목록 JSON 파일 [{title,url,ts}]")
    ap.add_argument("--list-units", action="store_true")
    args = ap.parse_args(argv)

    from .net import urllib_fetch
    cfg = load_config()
    dry_run = not args.live
    only = [o.strip() for o in args.only.split(",")] if args.only else None
    if args.list_units:
        store = FingerprintStore(":memory:")
        for u in build_units(cfg, store, urllib_fetch, persist=False, today=_dt.date.today(), news_items=[]):
            print(u.name)
        return 0
    firestore = rc_client = None
    if cfg.firebase_project_id and cfg.google_credentials_path:
        from .gcp import SCOPE_DATASTORE, SCOPE_REMOTE_CONFIG, FirestoreRest, ServiceAccountTokenProvider
        firestore = FirestoreRest(cfg.firebase_project_id, ServiceAccountTokenProvider(
            cfg.google_credentials_path, [SCOPE_DATASTORE]), urllib_fetch)
        rc_client = kill_switch.RemoteConfigClient(cfg.firebase_project_id, ServiceAccountTokenProvider(
            cfg.google_credentials_path, [SCOPE_REMOTE_CONFIG]), urllib_fetch)
    news_items = None
    if args.news_file:
        news_items = json.loads(pathlib.Path(args.news_file).read_text(encoding="utf-8"))
    summary = run_daily(cfg, fetcher=urllib_fetch, dry_run=dry_run, firestore=firestore, rc_client=rc_client,
                        only=only, retry_failed=args.retry_failed, unit_timeout=args.unit_timeout,
                        retries=args.retries, budget_sec=args.budget_sec, news_items=news_items)
    print(json.dumps(summary, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
