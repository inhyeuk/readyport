"""테스트 도우미: 가짜 fetcher, 임시 저장소, 임시 설정."""
from __future__ import annotations

import json
import pathlib
import tempfile

from ops.aria.config import Config
from ops.aria.net import NetworkError, Response


class FakeFetcher:
    """주소별로 정해 둔 응답을 돌려준다. 모든 호출을 기록한다. 모르는 주소면 실패(실제 네트워크 없음)."""

    def __init__(self, routes=None, default=None):
        self.routes = dict(routes or {})
        self.default = default
        self.calls: list[dict] = []

    def __call__(self, method, url, headers=None, data=None, timeout=20.0):
        self.calls.append({"method": method, "url": url, "headers": dict(headers or {}), "data": data})
        for key in (url, url.split("?", 1)[0], (method, url), (method, url.split("?", 1)[0])):
            if key in self.routes:
                r = self.routes[key]
                break
        else:
            r = self.default
        if r is None:
            raise AssertionError(f"예상하지 못한 요청: {method} {url}")
        if callable(r) and not isinstance(r, Response):
            r = r(method, url, headers, data)
        if isinstance(r, Exception):
            raise r
        return r


def html_resp(body: str, status: int = 200) -> Response:
    return Response(status, body.encode("utf-8"), {"Content-Type": "text/html; charset=utf-8"})


def json_resp(obj, status: int = 200, headers=None) -> Response:
    h = {"Content-Type": "application/json; charset=utf-8"}
    h.update(headers or {})
    return Response(status, json.dumps(obj, ensure_ascii=False).encode("utf-8"), h)


def net_error(msg="timed out"):
    return NetworkError(msg)


def make_repo(tmp: pathlib.Path, forms=None, extra_pack=None) -> pathlib.Path:
    """packs/src 를 흉내 낸 임시 저장소."""
    forms = forms if forms is not None else [("TH", "TH_TDAC", "https://form.example.test/th"),
                                             ("SG", "SG_SGAC", "https://form.example.test/sg")]
    src = tmp / "packs" / "src"
    (src / "recipes").mkdir(parents=True, exist_ok=True)
    countries = sorted({cc for cc, _, _ in forms})
    (src / "index.json").write_text(json.dumps({
        "schema_version": 1, "version": "2026.09.28-1",
        "countries": [{"code": cc, "name_ko": cc, "name_en": cc, "pack": True} for cc in countries],
        "sources": [{"id": "s", "name": "s", "url": "https://source.example.test/"}],
    }), encoding="utf-8")
    for cc in countries:
        pack = {"country": cc, "last_verified": "2026-09-01",
                "sources": [{"id": "s", "name": "s", "url": f"https://source.example.test/{cc}"}],
                "forms": [{"id": fid, "official_url": url, "source": "s", "last_verified": "2026-09-01"}
                          for c, fid, url in forms if c == cc]}
        if extra_pack and cc in extra_pack:
            pack.update(extra_pack[cc])
        (src / cc).mkdir(parents=True, exist_ok=True)
        (src / cc / "pack.json").write_text(json.dumps(pack, ensure_ascii=False), encoding="utf-8")
    # 구조 감시는 레시피가 있는 양식만 한다
    for _, fid, _ in forms:
        (src / "recipes" / f"{fid}.json").write_text(json.dumps({"form_id": fid, "version": "2026.09.28-1"}), encoding="utf-8")
    return tmp


class TempDirCase:
    """unittest.TestCase 와 함께 섞어 쓰는 임시 폴더."""

    def make_tmp(self) -> pathlib.Path:
        d = tempfile.TemporaryDirectory()
        self.addCleanup(d.cleanup)
        return pathlib.Path(d.name)

    def make_cfg(self, tmp: pathlib.Path, **kw) -> Config:
        cfg = Config()
        cfg.repo_root = tmp
        cfg.data_dir = tmp / "data"
        cfg.request_interval_sec = 0
        cfg.mofa_service_key = "TESTKEY"
        cfg.mofa_api_url = "https://mofa.example.test/api"
        for k, v in kw.items():
            setattr(cfg, k, v)
        return cfg
