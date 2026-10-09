"""여행 계획 요청·결과 정리 (하루 한 번, 사장님 결정 2026-10-09: 결과 전달 후 30일 자동 삭제).

1. 끝난 요청(done·failed — finishedAt 이 있다)이 보관 기간(PLAN_RETENTION_DAYS, 기본 30일)을 넘으면
   결과 plan_results/{id} 와 요청 plan_requests/{id} 를 지운다.
   **취소한 요청(cancelled)은 지우지 않는다** — 이용자가 앱에서 '삭제'를 누를 때까지 취소 상태로 남긴다(2026-10-09 사장님 결정).
2. 끝나지 않은 채(queued·processing) 보관 기간을 넘긴 요청도 지운다(처리되지 않은 민감정보를 오래 두지 않는다).
3. processing 이 6시간 넘게 멈춘 요청은 failed(engine_timeout) 로 닫는다(이용자 화면이 '만드는 중'에 머물지 않게).
4. 요청 없이 남은 결과(createdAt 이 보관 기간 넘음)와, 마지막 요청이 보관 기간보다 오래된 plan_quota/{uid} 도 지운다.
쿼리는 단일 필드 조건만 쓴다(복합 색인 없이). 지운 개수만 돌려준다 — 내용은 읽어도 남기지 않는다.
"""
from __future__ import annotations

import datetime as _dt
from typing import Optional

from ..gcp import doc_id
from .plan_requests import REQUESTS, RESULTS

QUOTA = "plan_quota"
STUCK_HOURS = 6
PAGE = 300


def _ts(dt: _dt.datetime) -> dict:
    return {"timestampValue": dt.astimezone(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")}


def older_than_query(collection: str, field: str, cutoff: _dt.datetime, after: Optional[_dt.datetime] = None,
                     limit: int = PAGE) -> dict:
    q = {
        "from": [{"collectionId": collection}],
        "select": {"fields": [{"fieldPath": field}, {"fieldPath": "finishedAt"}, {"fieldPath": "status"},
                              {"fieldPath": "processingAt"}]},
        "where": {"fieldFilter": {"field": {"fieldPath": field}, "op": "LESS_THAN", "value": _ts(cutoff)}},
        "orderBy": [{"field": {"fieldPath": field}, "direction": "ASCENDING"}],
        "limit": limit,
    }
    if after is not None:
        q["startAt"] = {"values": [_ts(after)], "before": False}
    return q


def processing_query(limit: int = PAGE) -> dict:
    return {"from": [{"collectionId": REQUESTS}],
            "select": {"fields": [{"fieldPath": "processingAt"}, {"fieldPath": "status"}]},
            "where": {"fieldFilter": {"field": {"fieldPath": "status"}, "op": "EQUAL", "value": {"stringValue": "processing"}}},
            "limit": limit}


def _paged(firestore, collection: str, field: str, cutoff: _dt.datetime, max_rounds: int = 50):
    after = None
    seen: set[str] = set()
    for _ in range(max_rounds):
        rows = [r for r in firestore.run_query(older_than_query(collection, field, cutoff, after)) if r["_name"] not in seen]
        if not rows:
            return
        for r in rows:
            seen.add(r["_name"])
            yield r
        last = rows[-1].get(field)
        if not isinstance(last, _dt.datetime):
            return
        after = last


def run(cfg, firestore, *, dry_run: bool = True, now: Optional[_dt.datetime] = None) -> dict:
    if firestore is None:
        return {"status": "not_configured"}
    now = now or _dt.datetime.now(_dt.timezone.utc)
    cutoff = now - _dt.timedelta(days=int(cfg.plan_retention_days))
    counts = {"finished_deleted": 0, "stale_deleted": 0, "stuck_failed": 0, "orphan_results_deleted": 0,
              "quota_deleted": 0}

    def delete_pair(rid: str):
        if not dry_run:
            firestore.delete_document(f"{RESULTS}/{rid}")
            firestore.delete_document(f"{REQUESTS}/{rid}")

    # 1. 끝난 지 보관 기간이 지난 요청
    for r in _paged(firestore, REQUESTS, "finishedAt", cutoff):
        if r.get("status") == "cancelled":
            continue           # 이용자가 직접 지울 때까지 남긴다
        delete_pair(doc_id(r["_name"]))
        counts["finished_deleted"] += 1
    # 2. 끝나지 않은 채 오래된 요청
    for r in _paged(firestore, REQUESTS, "createdAt", cutoff):
        if isinstance(r.get("finishedAt"), _dt.datetime):
            continue           # 끝난 것은 1번 기준(finishedAt)으로만
        delete_pair(doc_id(r["_name"]))
        counts["stale_deleted"] += 1
    # 3. 멈춘 처리
    stuck_before = now - _dt.timedelta(hours=STUCK_HOURS)
    for r in firestore.run_query(processing_query()):
        t = r.get("processingAt")
        if r.get("status") == "processing" and isinstance(t, _dt.datetime) and t < stuck_before:
            if not dry_run:
                firestore.set_document(f"{REQUESTS}/{doc_id(r['_name'])}",
                                       {"status": "failed", "error_code": "engine_timeout", "finishedAt": now, "updatedAt": now})
            counts["stuck_failed"] += 1
    # 4. 요청 없이 남은 결과, 오래된 요청 횟수 기록
    for r in _paged(firestore, RESULTS, "createdAt", cutoff):
        if not dry_run:
            firestore.delete_document(r["_name"])
        counts["orphan_results_deleted"] += 1
    for r in _paged(firestore, QUOTA, "last", cutoff):
        if not dry_run:
            firestore.delete_document(r["_name"])
        counts["quota_deleted"] += 1
    return {"status": "ok", "dry_run": dry_run, **counts}
