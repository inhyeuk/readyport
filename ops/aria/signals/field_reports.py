"""Firestore `field_reports` 집계와 임계치 판단 (ARCHITECTURE 9.4, ARIA_OPS 12.2-2).

문서 필드: form_id, pack_version, step_id, error_code, app_version, ts (개인정보 없음).
판단: 양식별로 창(기본 24시간) 안의 리포트를 세어
  표본 수 >= min_samples 이고 실패 수 >= min_failures 이고 실패율 >= fail_rate 이면 kill=True.
앱이 '성공' 리포트도 보낸다면 error_code 가 SUCCESS_CODES 인 것을 성공으로 센다.
지금 앱(M4)은 실패만 쌓으므로 실패율은 1.0 이 되고, 사실상 '실패 건수 기준'으로 동작한다.
리포트는 앱(사용자 기기)이 만든 값이라 믿지 않는다: form_id 형식이 이상하면 버린다.
"""
from __future__ import annotations

import datetime as _dt
import re
from collections import Counter
from dataclasses import dataclass, field
from typing import Any, Iterable, Optional

FORM_ID_RE = re.compile(r"^[A-Z]{2}_[A-Z0-9_]{2,40}$")
SUCCESS_CODES = {"ok", "success", "none", ""}


@dataclass
class FormStats:
    form_id: str
    total: int = 0
    failures: int = 0
    by_error: Counter = field(default_factory=Counter)
    by_step: Counter = field(default_factory=Counter)
    by_pack_version: Counter = field(default_factory=Counter)

    @property
    def fail_rate(self) -> float:
        return self.failures / self.total if self.total else 0.0


@dataclass
class Decision:
    form_id: str
    kill: bool
    reason: str
    stats: FormStats

    def to_dict(self) -> dict:
        s = self.stats
        return {"form_id": self.form_id, "kill": self.kill, "reason": self.reason, "total": s.total,
                "failures": s.failures, "fail_rate": round(s.fail_rate, 3),
                "top_errors": s.by_error.most_common(5), "top_steps": s.by_step.most_common(5),
                "pack_versions": dict(s.by_pack_version)}


def _as_dt(v: Any) -> Optional[_dt.datetime]:
    if isinstance(v, _dt.datetime):
        return v if v.tzinfo else v.replace(tzinfo=_dt.timezone.utc)
    if isinstance(v, (int, float)):
        # 밀리초 숫자로 올 수도 있다 [확인 필요: 앱이 쓰는 ts 형식, M9]
        return _dt.datetime.fromtimestamp(v / 1000 if v > 1e11 else v, tz=_dt.timezone.utc)
    if isinstance(v, str):
        try:
            from ..gcp import parse_timestamp
            return parse_timestamp(v)
        except ValueError:
            return None
    return None


def aggregate(reports: Iterable[dict], since: _dt.datetime, until: Optional[_dt.datetime] = None) -> dict[str, FormStats]:
    stats: dict[str, FormStats] = {}
    for r in reports:
        fid = r.get("form_id")
        if not isinstance(fid, str) or not FORM_ID_RE.match(fid):
            continue
        ts = _as_dt(r.get("ts"))
        if ts is None or ts < since or (until is not None and ts > until):
            continue
        s = stats.setdefault(fid, FormStats(fid))
        s.total += 1
        code = str(r.get("error_code") or "").strip().lower()
        if code not in SUCCESS_CODES:
            s.failures += 1
            s.by_error[code[:40]] += 1
            s.by_step[str(r.get("step_id") or "")[:40]] += 1
        s.by_pack_version[str(r.get("pack_version") or "")[:20]] += 1
    return stats


def decide(stats: dict[str, FormStats], min_samples: int = 5, min_failures: int = 5,
           fail_rate: float = 0.5) -> list[Decision]:
    out = []
    for fid in sorted(stats):
        s = stats[fid]
        if s.total < min_samples:
            out.append(Decision(fid, False, f"표본 부족 ({s.total} < {min_samples})", s))
        elif s.failures < min_failures:
            out.append(Decision(fid, False, f"실패 적음 ({s.failures} < {min_failures})", s))
        elif s.fail_rate < fail_rate:
            out.append(Decision(fid, False, f"실패율 낮음 ({s.fail_rate:.2f} < {fail_rate})", s))
        else:
            out.append(Decision(fid, True, f"실패 {s.failures}/{s.total} ({s.fail_rate:.0%}) — 자동 입력 끔", s))
    return out


def build_query(since: _dt.datetime, limit: int = 5000) -> dict:
    """ts >= since 인 field_reports. [확인 필요] ts 를 Firestore timestamp 로 쓴다는 가정(M9에서 확정)."""
    from ..gcp import to_fs_value
    return {
        "from": [{"collectionId": "field_reports"}],
        "where": {"fieldFilter": {"field": {"fieldPath": "ts"}, "op": "GREATER_THAN_OR_EQUAL",
                                  "value": to_fs_value(since)}},
        "limit": limit,
    }


def fetch_and_decide(firestore, now: _dt.datetime, window_hours: int = 24, min_samples: int = 5,
                     min_failures: int = 5, fail_rate: float = 0.5) -> list[Decision]:
    """firestore: gcp.FirestoreRest 또는 run_query(dict)->list[dict] 를 가진 가짜."""
    since = now - _dt.timedelta(hours=window_hours)
    reports = firestore.run_query(build_query(since))
    return decide(aggregate(reports, since, now), min_samples, min_failures, fail_rate)
