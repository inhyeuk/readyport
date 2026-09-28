"""증거 스냅샷 파일 저장. 개인정보는 들어가지 않는다(공개 페이지·공개 API 응답만)."""
from __future__ import annotations

import datetime as _dt
import json
import pathlib
import re
from typing import Any


def safe_name(s: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", s)[:80] or "x"


def save_evidence(evidence_dir: pathlib.Path, detector: str, unit: str, payload: Any,
                  fingerprint: str = "", ext: str = "json", now: _dt.datetime | None = None) -> str:
    now = now or _dt.datetime.now().astimezone()
    folder = pathlib.Path(evidence_dir) / safe_name(detector) / now.strftime("%Y-%m-%d")
    folder.mkdir(parents=True, exist_ok=True)
    name = f"{safe_name(unit)}_{now.strftime('%H%M%S')}"
    if fingerprint:
        name += f"_{fingerprint[:12]}"
    path = folder / f"{name}.{ext}"
    if ext == "json":
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    elif isinstance(payload, bytes):
        path.write_bytes(payload)
    else:
        path.write_text(str(payload), encoding="utf-8")
    return str(path)
