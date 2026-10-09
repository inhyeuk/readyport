"""설정 읽기.

환경변수가 먼저, 없으면 `ops/aria/.env` 파일 값을 쓴다. python-dotenv 없이 작은 파서로 읽는다.
`.env`는 커밋하지 않는다(.gitignore). 값 이름 목록은 `.env.example`.
"""
from __future__ import annotations

import os
import pathlib
from dataclasses import dataclass, field
from typing import Mapping

ARIA_DIR = pathlib.Path(__file__).resolve().parent
REPO_ROOT = ARIA_DIR.parents[1]
HOME_READYPORT = pathlib.Path(os.path.expanduser("~")) / ".readyport"
DEFAULT_ENV_FILE = ARIA_DIR / ".env"

# [재확인] 공공데이터포털 '외교부_국가·지역별 입국허가요건' 조회 주소.
# 조사 시점 기억에 기댄 값이라 확정이 아니다. 운영자가 공공데이터포털 활용신청 화면의
# '요청주소'로 확인한 뒤 .env 의 MOFA_API_URL 로 덮어쓴다.
MOFA_API_URL_DEFAULT = "https://apis.data.go.kr/1262000/EntranceVisaService2/getEntranceVisaList2"


def parse_env_text(text: str) -> dict[str, str]:
    """`.env` 글을 사전으로. KEY=VALUE, # 주석, 빈 줄, `export ` 앞머리, 따옴표를 처리한다."""
    out: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export "):].strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        elif " #" in value:
            # 따옴표 없는 값 뒤의 주석은 버린다
            value = value.split(" #", 1)[0].rstrip()
        if key:
            out[key] = value
    return out


def load_env_file(path: pathlib.Path | str | None = None) -> dict[str, str]:
    p = pathlib.Path(path) if path else DEFAULT_ENV_FILE
    if not p.exists():
        return {}
    return parse_env_text(p.read_text(encoding="utf-8-sig"))


def _split_list(value: str) -> list[str]:
    return [v.strip() for v in value.split(",") if v.strip()]


@dataclass
class Config:
    repo_root: pathlib.Path = REPO_ROOT
    data_dir: pathlib.Path = ARIA_DIR / "data"
    # 외교부 입국허가요건 (공공데이터포털)
    mofa_service_key: str = ""
    mofa_api_url: str = MOFA_API_URL_DEFAULT
    # Firebase
    firebase_project_id: str = ""
    google_credentials_path: str = ""
    # Claude 헤드리스
    claude_bin: str = "claude"
    claude_daily_cap: int = 3
    claude_timeout_sec: int = 1800
    # 크롤링 예절
    request_interval_sec: float = 3.0
    http_timeout_sec: float = 20.0
    user_agent: str = "ReadyPort-ARIA/0.1 (+operator check; GET only)"
    # 공지 페이지: "ID|URL" 을 쉼표로 이은 목록
    notice_urls: list[tuple[str, str]] = field(default_factory=list)
    # 현장 신호 임계치 [확인 필요] 운영하며 조정
    field_window_hours: int = 24
    field_min_samples: int = 5
    field_min_failures: int = 5
    field_fail_rate: float = 0.5
    # 네이버 데이터랩 (쇼핑 리스트 검색 추이)
    naver_client_id: str = ""
    naver_client_secret: str = ""
    # 관광지 주간 갱신 (사장님 결정 2026-10-09: 검사 통과하면 자동 반영, 서명은 GitHub Actions)
    attractions_weekly_cap: int = 3          # 한 주(ISO 주)에 관광지 갱신으로 Claude 를 부르는 상한(CLAUDE_DAILY_CAP 과 함께 본다)
    attractions_evidence_dir: pathlib.Path = HOME_READYPORT / "evidence"   # 저장소 밖 증거 폴더(build_attractions 와 같은 곳)
    github_repo: str = "inhyeuk/readyport"
    # 이용자 평점 주간 집계
    ratings_min_n: int = 5                   # 평가가 이 수보다 적으면 숨김(통계 문서에 싣지 않음)
    # 여행 계획 요청 (1시간마다)
    plan_daily_cap: int = 5                  # 하루 계획 생성(Claude) 상한 — CLAUDE_DAILY_CAP 과 따로 센다
    plan_timeout_sec: int = 600              # 계획 하나 생성 시간 제한
    plan_max_per_run: int = 3                # 한 번 실행에서 처리할 요청 수
    plan_weekly_limit: int = 2               # 이용자 한 명이 7일 동안 요청할 수 있는 수(규칙과 같은 값)
    plan_retention_days: int = 30            # 끝난(완료·실패·취소) 요청·결과 보관 일수

    @property
    def db_path(self) -> pathlib.Path:
        return self.data_dir / "aria_state.sqlite"

    @property
    def snapshot_dir(self) -> pathlib.Path:
        return self.data_dir / "snapshots"

    @property
    def evidence_dir(self) -> pathlib.Path:
        return self.data_dir / "evidence"


def _parse_notice_urls(value: str) -> list[tuple[str, str]]:
    out = []
    for item in _split_list(value):
        if "|" not in item:
            raise ValueError(f"NOTICE_URLS 항목은 'ID|URL' 형식이어야 한다: {item!r}")
        nid, url = item.split("|", 1)
        nid, url = nid.strip(), url.strip()
        if not url.startswith("https://"):
            raise ValueError(f"NOTICE_URLS 는 https 주소만: {nid}")
        out.append((nid, url))
    return out


def load_config(env_file: pathlib.Path | str | None = None,
                environ: Mapping[str, str] | None = None) -> Config:
    """환경변수 > .env 순서로 합쳐 Config 를 만든다."""
    values = load_env_file(env_file)
    values.update({k: v for k, v in (os.environ if environ is None else environ).items()})

    def get(name: str, default: str = "") -> str:
        v = values.get(name)
        return v if v not in (None, "") else default

    cfg = Config()
    if get("ARIA_REPO_ROOT"):
        cfg.repo_root = pathlib.Path(get("ARIA_REPO_ROOT"))
    if get("ARIA_DATA_DIR"):
        cfg.data_dir = pathlib.Path(get("ARIA_DATA_DIR"))
    cfg.mofa_service_key = get("MOFA_SERVICE_KEY")
    cfg.mofa_api_url = get("MOFA_API_URL", MOFA_API_URL_DEFAULT)
    cfg.firebase_project_id = get("FIREBASE_PROJECT_ID")
    cfg.google_credentials_path = get("GOOGLE_APPLICATION_CREDENTIALS")
    cfg.claude_bin = get("CLAUDE_BIN", "claude")
    cfg.claude_daily_cap = int(get("CLAUDE_DAILY_CAP", "3"))
    cfg.claude_timeout_sec = int(get("CLAUDE_TIMEOUT_SEC", "1800"))
    cfg.request_interval_sec = float(get("REQUEST_INTERVAL_SEC", "3"))
    cfg.http_timeout_sec = float(get("HTTP_TIMEOUT_SEC", "20"))
    cfg.notice_urls = _parse_notice_urls(get("NOTICE_URLS"))
    cfg.field_window_hours = int(get("FIELD_WINDOW_HOURS", "24"))
    cfg.field_min_samples = int(get("FIELD_MIN_SAMPLES", "5"))
    cfg.field_min_failures = int(get("FIELD_MIN_FAILURES", "5"))
    cfg.field_fail_rate = float(get("FIELD_FAIL_RATE", "0.5"))
    cfg.naver_client_id = get("NAVER_CLIENT_ID")
    cfg.naver_client_secret = get("NAVER_CLIENT_SECRET")
    cfg.attractions_weekly_cap = int(get("ATTRACTIONS_WEEKLY_CAP", "3"))
    if get("ATTRACTIONS_EVIDENCE_DIR"):
        cfg.attractions_evidence_dir = pathlib.Path(os.path.expanduser(get("ATTRACTIONS_EVIDENCE_DIR")))
    cfg.github_repo = get("GITHUB_REPO", "inhyeuk/readyport")
    cfg.ratings_min_n = int(get("RATINGS_MIN_N", "5"))
    cfg.plan_daily_cap = int(get("PLAN_DAILY_CAP", "5"))
    cfg.plan_timeout_sec = int(get("PLAN_TIMEOUT_SEC", "600"))
    cfg.plan_max_per_run = int(get("PLAN_MAX_PER_RUN", "3"))
    cfg.plan_weekly_limit = int(get("PLAN_WEEKLY_LIMIT", "2"))
    cfg.plan_retention_days = int(get("PLAN_RETENTION_DAYS", "30"))
    return cfg
