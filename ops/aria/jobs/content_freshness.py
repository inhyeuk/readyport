"""오래 확인하지 않은 항목 찾기 (기본 90일). 네트워크 없음, packs/src 읽기만.

`last_verified` 가 있는 모든 객체를 훑어, 오늘로부터 N일보다 오래됐거나 날짜 형식이 이상한 것을 알려 준다.
"""
from __future__ import annotations

import datetime as _dt
import pathlib
from typing import Optional

from ..packs_util import iter_pack_files, load_json, walk


def find_stale(repo_root: pathlib.Path, today: Optional[_dt.date] = None, max_age_days: int = 90) -> list[dict]:
    today = today or _dt.date.today()
    root = pathlib.Path(repo_root)
    out = []
    for f in iter_pack_files(root):
        rel = f.relative_to(root).as_posix()
        for path, v in walk(load_json(f)):
            if not isinstance(v, dict) or "last_verified" not in v:
                continue
            lv = v.get("last_verified")
            item_id = v.get("id") or v.get("form_id") or v.get("country") or ""
            try:
                d = _dt.date.fromisoformat(str(lv))
            except ValueError:
                out.append({"file": rel, "path": path or "$", "id": item_id, "last_verified": lv,
                            "age_days": None, "problem": "날짜 형식 오류"})
                continue
            age = (today - d).days
            if age > max_age_days:
                out.append({"file": rel, "path": path or "$", "id": item_id, "last_verified": lv,
                            "age_days": age, "problem": f"{max_age_days}일 넘음"})
    out.sort(key=lambda x: (x["age_days"] is not None, -(x["age_days"] or 0)))
    return out
