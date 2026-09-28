"""감지 결과를 담는 공통 형식."""
from __future__ import annotations

import hashlib
import json
from dataclasses import asdict, dataclass, field
from typing import Any

# 단위 작업 결과 상태
STATUS_UNCHANGED = "unchanged"          # 바뀐 것 없음
STATUS_CHANGED = "changed"              # 변경 감지 (changes 에 담김)
STATUS_BASELINE = "baseline"            # 첫 실행이라 기준만 저장
STATUS_MANUAL = "manual_check_needed"   # 봇 차단 등. 다시 시도·우회하지 않는다
STATUS_ERROR = "error"                  # 네트워크 오류 등. 다시 시도해도 됨
STATUS_TIMEOUT = "timeout"
STATUS_SKIPPED = "skipped"              # 설정이 없어 건너뜀
STATUS_NOT_CONFIGURED = "not_configured"
STATUS_DEFERRED = "deferred"            # 시간 예산이 모자라 시작 못 함 (다음 --retry-failed 에서)

RETRYABLE = {STATUS_ERROR, STATUS_TIMEOUT}
# --retry-failed 로 다시 돌릴 상태 (manual_check_needed 는 절대 다시 돌리지 않는다)
RERUN_LATER = RETRYABLE | {STATUS_DEFERRED}


def canonical_json(obj: Any) -> str:
    """같은 내용이면 항상 같은 글자가 되도록 정렬·공백 없이."""
    return json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def fingerprint_of(detector: str, unit: str, diff: Any) -> str:
    """변경 지문 = (감지기, 단위, 정규화한 diff) 의 sha256."""
    payload = canonical_json({"detector": detector, "unit": unit, "diff": diff})
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


@dataclass
class Change:
    detector: str            # 예: mofa_entry_diff
    unit: str                # 예: TH, TH_TDAC, notice:th_immigration
    summary: str             # 사람이 읽는 한 줄 (LLM 아님, 결정적으로 만든 글)
    diff: Any                # 정규화한 변경 내용
    evidence_path: str = ""  # 증거 스냅샷 파일 경로
    fingerprint: str = ""

    def __post_init__(self):
        if not self.fingerprint:
            self.fingerprint = fingerprint_of(self.detector, self.unit, self.diff)

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class UnitResult:
    unit: str
    status: str
    changes: list[Change] = field(default_factory=list)
    message: str = ""
    evidence_path: str = ""
    extra: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        d = asdict(self)
        d["changes"] = [c.to_dict() for c in self.changes]
        return d
