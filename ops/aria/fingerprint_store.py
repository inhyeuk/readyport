"""멱등 키 저장소 (SQLite). ARIA_OPS 12.4 대책.

- 같은 변경 지문은 한 번만 처리한다(`register` 가 처음일 때만 True).
- 상태 `draft / approved / published`. ARIA 는 draft 만 만든다.
  approved·published 는 PR 머지·배포 경로(role="merge_sync")에서만 바꾼다.
  이 막음은 실수로 덮어쓰는 사고를 막는 장치다. 같은 PC의 파일이라 보안 경계는 아니다.
  진짜 확정값은 main 브랜치(사람이 머지한 PR)에 있다.
- 텔레그램 승인(review)은 "Claude 로 고쳐 봐도 된다"는 허락일 뿐, approved 가 아니다.
- 하루 Claude 호출 상한(현지 날짜 기준)을 센다.
"""
from __future__ import annotations

import datetime as _dt
import json
import pathlib
import re
import sqlite3
import threading
from typing import Any, Optional

STATES = ("draft", "approved", "published")
REVIEWS = ("pending", "accepted", "rejected")
ROLE_ARIA = "aria"
ROLE_MERGE_SYNC = "merge_sync"

_FP_RE = re.compile(r"^[0-9a-f]{64}$")
_SHA_RE = re.compile(r"^[0-9a-f]{40}$")

_SCHEMA = """
CREATE TABLE IF NOT EXISTS changes (
    fingerprint   TEXT PRIMARY KEY,
    detector      TEXT NOT NULL,
    unit          TEXT NOT NULL,
    summary       TEXT NOT NULL,
    evidence_path TEXT NOT NULL DEFAULT '',
    state         TEXT NOT NULL DEFAULT 'draft',
    review        TEXT NOT NULL DEFAULT 'pending',
    review_note   TEXT NOT NULL DEFAULT '',
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL,
    notified_at   TEXT,
    claude_run_at TEXT,
    claude_status TEXT,
    pr_url        TEXT,
    merge_sha     TEXT
);
CREATE TABLE IF NOT EXISTS claude_calls (
    day   TEXT PRIMARY KEY,
    count INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS kv (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
"""


class StoreError(Exception):
    pass


def _now_iso() -> str:
    return _dt.datetime.now(_dt.timezone.utc).isoformat(timespec="seconds")


def _local_today() -> _dt.date:
    return _dt.datetime.now().astimezone().date()


def check_fp(fp: str) -> str:
    if not isinstance(fp, str) or not _FP_RE.match(fp):
        raise StoreError("지문은 64자리 소문자 16진수여야 한다")
    return fp


def _locked(fn):
    """여러 스레드(단위 작업)가 같은 연결을 쓰므로 한 번에 하나씩."""
    def wrapper(self, *a, **kw):
        with self._lock:
            return fn(self, *a, **kw)
    wrapper.__name__ = fn.__name__
    wrapper.__doc__ = fn.__doc__
    return wrapper


class FingerprintStore:
    def __init__(self, path: pathlib.Path | str, role: str = ROLE_ARIA):
        if role not in (ROLE_ARIA, ROLE_MERGE_SYNC):
            raise StoreError(f"모르는 역할: {role}")
        self.role = role
        self._lock = threading.RLock()
        self.path = str(path)
        if self.path != ":memory:":
            pathlib.Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        # isolation_level=None: 트랜잭션을 직접 연다(BEGIN IMMEDIATE)
        self._db = sqlite3.connect(self.path, isolation_level=None, timeout=30, check_same_thread=False)
        self._db.row_factory = sqlite3.Row
        self._db.executescript(_SCHEMA)

    @_locked
    def close(self):
        self._db.close()

    # ---- 변경 기록 ----
    @_locked
    def register(self, change) -> bool:
        """처음 보는 지문이면 draft 로 저장하고 True. 이미 있으면 아무것도 바꾸지 않고 False."""
        fp = check_fp(change.fingerprint)
        now = _now_iso()
        cur = self._db.execute(
            "INSERT OR IGNORE INTO changes (fingerprint, detector, unit, summary, evidence_path,"
            " state, review, created_at, updated_at) VALUES (?,?,?,?,?,'draft','pending',?,?)",
            (fp, change.detector, change.unit, change.summary, change.evidence_path or "", now, now),
        )
        return cur.rowcount == 1

    @_locked
    def seen(self, fp: str) -> bool:
        return self.get(fp) is not None

    @_locked
    def get(self, fp: str) -> Optional[dict[str, Any]]:
        row = self._db.execute("SELECT * FROM changes WHERE fingerprint=?", (check_fp(fp),)).fetchone()
        return dict(row) if row else None

    @_locked
    def mark_notified(self, fp: str) -> None:
        self._db.execute("UPDATE changes SET notified_at=?, updated_at=? WHERE fingerprint=?",
                         (_now_iso(), _now_iso(), check_fp(fp)))

    @_locked
    def list_unnotified(self, limit: int = 50) -> list[dict[str, Any]]:
        """등록은 됐는데 알림이 못 나간 것(알림 실패 후 다음 실행에서 다시 보냄)."""
        rows = self._db.execute("SELECT * FROM changes WHERE notified_at IS NULL ORDER BY created_at LIMIT ?", (limit,))
        return [dict(r) for r in rows]

    @_locked
    def list_pending(self, detector: str, unit_prefix: str = "", limit: int = 500) -> list[dict[str, Any]]:
        """자동 경로(관광지 주간 갱신)가 아직 손대지 않은 변경: claude_status 가 비어 있는 것."""
        rows = self._db.execute(
            "SELECT * FROM changes WHERE detector=? AND unit LIKE ? AND claude_status IS NULL ORDER BY created_at LIMIT ?",
            (detector, unit_prefix.replace("%", "") + "%", limit))
        return [dict(r) for r in rows]

    @_locked
    def list_recent(self, limit: int = 20) -> list[dict[str, Any]]:
        rows = self._db.execute("SELECT * FROM changes ORDER BY created_at DESC LIMIT ?", (limit,))
        return [dict(r) for r in rows]

    # ---- 사람 검토(텔레그램) ----
    @_locked
    def set_review(self, fp: str, review: str, note: str = "") -> bool:
        """텔레그램 approve/reject 결과. pending 일 때만 바뀐다(한 번 결정하면 끝)."""
        if review not in ("accepted", "rejected"):
            raise StoreError(f"review 값이 잘못됨: {review}")
        cur = self._db.execute(
            "UPDATE changes SET review=?, review_note=?, updated_at=? WHERE fingerprint=? AND review='pending'",
            (review, note[:500], _now_iso(), check_fp(fp)),
        )
        return cur.rowcount == 1

    # ---- 상태(draft/approved/published) ----
    @_locked
    def set_state(self, fp: str, state: str) -> None:
        """ARIA 쪽에서 부르는 일반 경로. draft 로만 둘 수 있다."""
        if state not in STATES:
            raise StoreError(f"모르는 상태: {state}")
        if state != "draft":
            raise PermissionError("approved·published 는 PR 머지 경로(mark_approved_by_merge 등)에서만 바꾼다")
        # 이미 approved/published 인 것을 draft 로 되돌리지도 않는다(확정값 보호)
        self._db.execute("UPDATE changes SET updated_at=? WHERE fingerprint=? AND state='draft'",
                         (_now_iso(), check_fp(fp)))

    @_locked
    def mark_approved_by_merge(self, fp: str, pr_url: str, merge_sha: str) -> None:
        """PR 이 main 에 머지됐을 때만. role='merge_sync' 로 연 저장소에서만 허용."""
        if self.role != ROLE_MERGE_SYNC:
            raise PermissionError("approved 는 PR 머지 경로(role='merge_sync')만 바꿀 수 있다")
        if not _SHA_RE.match(merge_sha or ""):
            raise StoreError("머지 커밋 sha(40자리 16진수)가 필요하다")
        if not (pr_url or "").startswith("https://github.com/"):
            raise StoreError("PR 주소가 필요하다")
        cur = self._db.execute(
            "UPDATE changes SET state='approved', pr_url=?, merge_sha=?, updated_at=?"
            " WHERE fingerprint=? AND state='draft'",
            (pr_url, merge_sha, _now_iso(), check_fp(fp)),
        )
        if cur.rowcount != 1:
            raise StoreError("draft 상태의 지문이 아니다")

    @_locked
    def mark_published(self, fp: str) -> None:
        if self.role != ROLE_MERGE_SYNC:
            raise PermissionError("published 는 배포 경로(role='merge_sync')만 바꿀 수 있다")
        cur = self._db.execute(
            "UPDATE changes SET state='published', updated_at=? WHERE fingerprint=? AND state='approved'",
            (_now_iso(), check_fp(fp)),
        )
        if cur.rowcount != 1:
            raise StoreError("approved 상태의 지문이 아니다")

    # ---- Claude 실행 ----
    @_locked
    def claude_calls_today(self, today: Optional[_dt.date] = None) -> int:
        day = (today or _local_today()).isoformat()
        row = self._db.execute("SELECT count FROM claude_calls WHERE day=?", (day,)).fetchone()
        return int(row["count"]) if row else 0

    @_locked
    def claim_claude_run(self, fp: str, daily_cap: int, today: Optional[_dt.date] = None) -> tuple[bool, str]:
        """Claude 실행 자격을 한 번에 확인하고 차지한다.

        조건: 지문이 있고, 사람이 accepted 했고, 아직 실행한 적 없고, 오늘 상한이 남았다.
        성공하면 오늘 횟수를 1 올리고 claude_run_at 을 채운다(같은 지문 두 번 실행 방지).
        """
        check_fp(fp)
        day = (today or _local_today()).isoformat()
        db = self._db
        db.execute("BEGIN IMMEDIATE")
        try:
            row = db.execute("SELECT review, claude_run_at FROM changes WHERE fingerprint=?", (fp,)).fetchone()
            if row is None:
                db.execute("ROLLBACK")
                return False, "unknown_fingerprint"
            if row["review"] != "accepted":
                db.execute("ROLLBACK")
                return False, "not_accepted"
            if row["claude_run_at"]:
                db.execute("ROLLBACK")
                return False, "already_ran"
            c = db.execute("SELECT count FROM claude_calls WHERE day=?", (day,)).fetchone()
            count = int(c["count"]) if c else 0
            if count >= daily_cap:
                db.execute("ROLLBACK")
                return False, "daily_cap_reached"
            db.execute("INSERT INTO claude_calls(day, count) VALUES(?, 1)"
                       " ON CONFLICT(day) DO UPDATE SET count=count+1", (day,))
            db.execute("UPDATE changes SET claude_run_at=?, claude_status='running', updated_at=? WHERE fingerprint=?",
                       (_now_iso(), _now_iso(), fp))
            db.execute("COMMIT")
            return True, "ok"
        except Exception:
            db.execute("ROLLBACK")
            raise

    @_locked
    def claim_auto_run(self, fp: str, daily_cap: int, counters: Optional[list[tuple[str, int]]] = None,
                       today: Optional[_dt.date] = None) -> tuple[bool, str]:
        """사람 승인 없이 자동으로 도는 경로(사장님이 '검사 통과하면 자동 반영'으로 정한 관광지 주간 갱신)의 실행 자격.

        claim_claude_run 과 같지만 review=accepted 를 요구하지 않는다. 대신 하루 Claude 상한(claude_calls)과
        추가 상한 목록 counters=[(kv 키, 상한)] 을 모두 확인하고, 통과하면 한 번에 모두 1씩 올린다.
        """
        check_fp(fp)
        day = (today or _local_today()).isoformat()
        db = self._db
        db.execute("BEGIN IMMEDIATE")
        try:
            row = db.execute("SELECT claude_run_at FROM changes WHERE fingerprint=?", (fp,)).fetchone()
            if row is None:
                db.execute("ROLLBACK")
                return False, "unknown_fingerprint"
            if row["claude_run_at"]:
                db.execute("ROLLBACK")
                return False, "already_ran"
            c = db.execute("SELECT count FROM claude_calls WHERE day=?", (day,)).fetchone()
            if (int(c["count"]) if c else 0) >= daily_cap:
                db.execute("ROLLBACK")
                return False, "daily_cap_reached"
            for key, cap in counters or []:
                v = db.execute("SELECT value FROM kv WHERE key=?", (key,)).fetchone()
                if (int(json.loads(v["value"])) if v else 0) >= cap:
                    db.execute("ROLLBACK")
                    return False, f"cap_reached:{key}"
            db.execute("INSERT INTO claude_calls(day, count) VALUES(?, 1)"
                       " ON CONFLICT(day) DO UPDATE SET count=count+1", (day,))
            for key, _ in counters or []:
                v = db.execute("SELECT value FROM kv WHERE key=?", (key,)).fetchone()
                n = (int(json.loads(v["value"])) if v else 0) + 1
                db.execute("INSERT INTO kv(key, value, updated_at) VALUES(?,?,?)"
                           " ON CONFLICT(key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at",
                           (key, json.dumps(n), _now_iso()))
            db.execute("UPDATE changes SET claude_run_at=?, claude_status='running', updated_at=? WHERE fingerprint=?",
                       (_now_iso(), _now_iso(), fp))
            db.execute("COMMIT")
            return True, "ok"
        except Exception:
            db.execute("ROLLBACK")
            raise

    @_locked
    def claim_counter(self, key: str, cap: int) -> bool:
        """작은 상한 하나(예: 하루 계획 생성 수)를 확인하고 1 올린다. 넘으면 False(아무것도 안 바꿈)."""
        db = self._db
        db.execute("BEGIN IMMEDIATE")
        try:
            v = db.execute("SELECT value FROM kv WHERE key=?", (key,)).fetchone()
            n = int(json.loads(v["value"])) if v else 0
            if n >= cap:
                db.execute("ROLLBACK")
                return False
            db.execute("INSERT INTO kv(key, value, updated_at) VALUES(?,?,?)"
                       " ON CONFLICT(key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at",
                       (key, json.dumps(n + 1), _now_iso()))
            db.execute("COMMIT")
            return True
        except Exception:
            db.execute("ROLLBACK")
            raise

    @_locked
    def record_claude_result(self, fp: str, status: str, pr_url: str | None = None) -> None:
        self._db.execute("UPDATE changes SET claude_status=?, pr_url=COALESCE(?, pr_url), updated_at=?"
                         " WHERE fingerprint=?", (status[:50], pr_url, _now_iso(), check_fp(fp)))

    # ---- 작은 키-값 (구조 해시·공지 해시 등) ----
    @_locked
    def get_value(self, key: str) -> Any:
        row = self._db.execute("SELECT value FROM kv WHERE key=?", (key,)).fetchone()
        return json.loads(row["value"]) if row else None

    @_locked
    def set_value(self, key: str, value: Any) -> None:
        self._db.execute("INSERT INTO kv(key, value, updated_at) VALUES(?,?,?)"
                         " ON CONFLICT(key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at",
                         (key, json.dumps(value, ensure_ascii=False), _now_iso()))
