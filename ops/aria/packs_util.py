"""packs/src 원본을 읽는 도우미 (읽기만 한다)."""
from __future__ import annotations

import json
import pathlib
from typing import Any, Iterator


def packs_src(repo_root: pathlib.Path) -> pathlib.Path:
    return pathlib.Path(repo_root) / "packs" / "src"


def load_json(path: pathlib.Path) -> Any:
    return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))


def iter_pack_files(repo_root: pathlib.Path) -> Iterator[pathlib.Path]:
    yield from sorted(packs_src(repo_root).rglob("*.json"))


def list_forms(repo_root: pathlib.Path, with_recipe_only: bool = True) -> list[dict[str, str]]:
    """각 국가 팩의 forms[] → [{"form_id","country","official_url"}].

    기본은 자동 입력 레시피가 있는 양식만 — 구조 해시는 레시피가 깨지는지 보려는 것이다.
    (예: Visit Japan Web은 로그인이 필요해 레시피가 없고, 서버가 앱 화면에도 HTTP 404를 돌려준다)
    """
    recipes = {p.stem for p in (packs_src(repo_root) / "recipes").glob("*.json")}
    out = []
    for path in sorted(packs_src(repo_root).glob("*/pack.json")):
        pack = load_json(path)
        for f in pack.get("forms", []):
            if with_recipe_only and f.get("id") not in recipes:
                continue
            if f.get("id") and f.get("official_url"):
                out.append({"form_id": f["id"], "country": pack.get("country", path.parent.name),
                            "official_url": f["official_url"]})
    return out


def list_countries(repo_root: pathlib.Path) -> list[str]:
    index = packs_src(repo_root) / "index.json"
    if not index.exists():
        return []
    return [c["code"] for c in load_json(index).get("countries", [])]


def walk(obj: Any, path: str = "") -> Iterator[tuple[str, Any]]:
    """모든 값을 (json 경로, 값) 으로 훑는다."""
    yield path, obj
    if isinstance(obj, dict):
        for k, v in obj.items():
            yield from walk(v, f"{path}.{k}")
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            yield from walk(v, f"{path}[{i}]")
