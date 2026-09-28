# ops/aria — ARIA 운영 모듈

ARIA(운영자 PC의 24시간 에이전트)가 불러 쓰는 **독립 모듈**이다. ARIA 본체 코드는 이 저장소에 없고, 연결은 운영자가 한다.
설계는 `docs/ARIA_OPS.md` 12장.

역할 분담: 감지·증거 수집 = ARIA(이 모듈) / 판단·수정 = Claude Code 헤드리스 / 승인 = 사람(텔레그램 + PR 머지) / 배포 = GitHub Actions.

## 안전 원칙

- **안전한 방향만 자동**: 자동 입력 끄기(`kill_autofill_*`=true), 경고 배너 켜기(`stale_banner`=true).
- **위험한 방향은 사람 승인**: 자동 입력 다시 켜기(`approved_by_human=True` 필요), 새 정책값 게시·레시피 변경(PR 머지로만).
- **ARIA에는 Hosting 배포 권한을 주지 않는다.** 서명 키도 주지 않는다. 배포는 GitHub Actions secrets 로만.
- Remote Config 는 `kill_autofill_{FORM_ID}` 와 `stale_banner` 말고는 **어떤 키도 바꾸지 않는다**(허용 목록 + 바꾼 뒤 전체 비교).
- 정부 사이트에는 **GET(조회)만**. 입력·제출·버튼 누르기 없음. 봇 차단(Cloudflare·Turnstile·캡차, 403/429/503)이면 **우회·재시도하지 않고** `manual_check_needed`.
- 감지는 **LLM 없이** 결정적으로. 요약(LLM)이 실패해도 원본 diff 를 알림으로 보낸다.
- 같은 변경 지문은 **한 번만** 처리. Claude 호출은 **하루 상한**.
- ARIA 는 상태를 `draft` 로만 쓴다. `approved` 는 PR 머지 경로(`role="merge_sync"`)에서만.
- 개인정보는 다루지 않는다. 현장 리포트도 양식·버전·단계·오류 코드뿐.

## 모듈

| 파일 | 하는 일 |
|---|---|
| `config.py` | 환경변수 > `ops/aria/.env` 순서로 설정 읽기(작은 파서, 의존성 없음) |
| `net.py` | urllib 기반 fetcher, 봇 차단 판별, 주소의 인증키 가리기 |
| `models.py` | `Change`(감지 결과·지문·증거 경로), `UnitResult`(단위 작업 결과·상태) |
| `fingerprint_store.py` | SQLite 멱등 저장소: 지문 1회 처리, draft/approved/published, 텔레그램 검토 결과, 하루 Claude 상한, 작은 키-값 |
| `evidence.py` | 증거 스냅샷 파일 저장 (`data/evidence/<감지기>/<날짜>/`) |
| `detectors/mofa_entry_diff.py` | 외교부 입국허가요건(공공데이터포털) 받아 정규화 → 전날 스냅샷과 비교 → 나라별 Change |
| `detectors/form_structure_hash.py` | 양식 공식 페이지 GET → 구조 해시(태그 + 입력 칸 name/id/type/formcontrolname) 비교 |
| `detectors/notice_watch.py` | 공지 페이지 글 해시 비교, 바뀐 줄(diff) 증거 |
| `detectors/news_keywords.py` | 뉴스 제목에서 입국정책 키워드(TDAC, ETIAS, arrival card, 입국신고 …) 찾기 (순수 함수) |
| `signals/field_reports.py` | Firestore `field_reports` 집계, 양식별 임계치 판단(표본 수·실패 수·실패율) |
| `gcp.py` | Firestore REST, 토큰 제공자(서비스 계정은 google-auth 선택 설치) |
| `actions/kill_switch.py` | Remote Config REST: GET(ETag) → 허용 키만 수정 → PUT(If-Match) |
| `approvals/telegram_cmd.py` | `CMD:v1 {...}` 명령 파서(엄격), 승인 요청·알림 메시지 만들기, 명령 처리 |
| `runners/claude_headless.py` | `claude -p ... --allowedTools ... --output-format json` 실행 래퍼 |
| `jobs/ranking.py` | 인기 순위(항공 0.5 / 검색 0.3 / 찜 0.2) → `rankings/latest.json`. 항공 자료 없으면 만들지 않음 |
| `jobs/link_check.py` | `packs/src/**/*.json` 의 모든 https 주소 HEAD/GET 점검 |
| `jobs/content_freshness.py` | `last_verified` 가 N일(기본 90) 넘은 항목 목록 |
| `jobs/shopping_trend.py` | 네이버 데이터랩 검색 추이(키 없으면 `not_configured`) |
| `heartbeat.py` | `ops/heartbeat` 기록, `check_stale()`(watchdog 용) |
| `run_daily.py` | 매일 감지 실행기(단위별 시간 제한·예산·실패만 재시도) |

## 설정 (`ops/aria/.env`, 커밋 금지)

`.env.example` 을 복사해서 채운다.

| 이름 | 뜻 |
|---|---|
| `MOFA_SERVICE_KEY` | 공공데이터포털 인증키 |
| `MOFA_API_URL` | 외교부 입국허가요건 요청주소 **[재확인]** — 기본값은 확정이 아니다 |
| `FIREBASE_PROJECT_ID` | Firebase 프로젝트 id |
| `GOOGLE_APPLICATION_CREDENTIALS` | Remote Config·Firestore 전용 서비스 계정 JSON 경로(저장소 밖) |
| `CLAUDE_BIN` | claude 실행 파일. 윈도는 `claude.exe` 경로(`.cmd` 는 거절) |
| `CLAUDE_DAILY_CAP` | 하루 Claude 실행 상한(기본 3) |
| `CLAUDE_TIMEOUT_SEC` | Claude 한 번 실행 시간 제한(기본 1800) |
| `NOTICE_URLS` | 공지 페이지 `ID|https://...` 쉼표 목록 |
| `REQUEST_INTERVAL_SEC`, `HTTP_TIMEOUT_SEC` | 요청 간격(기본 3초), 요청 시간 제한 |
| `FIELD_WINDOW_HOURS`, `FIELD_MIN_SAMPLES`, `FIELD_MIN_FAILURES`, `FIELD_FAIL_RATE` | 현장 신호 임계치 **[확인 필요]** 운영하며 조정 |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | 네이버 데이터랩 |
| `ARIA_DATA_DIR` | 스냅샷·증거·SQLite 폴더(기본 `ops/aria/data`, 커밋 안 함) |

## ARIA 가 부르는 법

저장소 루트에서:

```powershell
python -m ops.aria.run_daily --list-units           # 단위 목록 (네트워크 없음)
python -m ops.aria.run_daily                        # 시험 실행(기본): 조회만, 저장·알림·스위치 변경 없음
python -m ops.aria.run_daily --live                 # 실제 실행
python -m ops.aria.run_daily --live --retry-failed  # 오늘 error·timeout·deferred 단위만 다시
python -m ops.aria.run_daily --live --only form:TH_TDAC,mofa
python -m ops.aria.run_daily --live --only news --news-file news.json   # [{title,url,ts}]
```

- 기본 시간 예산 240초(`--budget-sec`), 단위당 60초(`--unit-timeout`). ARIA watchdog(300초)보다 먼저 끝나고,
  시작 못 한 단위는 `deferred` 로 남아 다음 `--retry-failed` 에서 이어진다. `manual_check_needed` 는 다시 돌지 않는다.
- 결과는 JSON 으로 표준 출력에 나온다. 텔레그램으로 보내려면 파이썬에서 직접 부르고 notifier 를 넣는다:

```python
from ops.aria.config import load_config
from ops.aria.net import urllib_fetch
from ops.aria.run_daily import run_daily

class TelegramNotifier:            # ARIA 쪽 봇으로 보내는 어댑터(운영자가 작성)
    def send(self, text): aria_bot.send_to_owner(text)

summary = run_daily(load_config(), fetcher=urllib_fetch, dry_run=False, notifier=TelegramNotifier(),
                    summarizer=None,          # 선택: Change -> 요약 글. 실패해도 원본 diff 는 나간다
                    firestore=..., rc_client=...)   # 없으면 현장 신호·하트비트는 건너뜀
```

### 텔레그램 명령 (CMD:v1)

```
CMD:v1 {"action":"approve","fp":"<64자리 지문>"}     # Claude 로 고쳐 PR 을 만들어도 된다
CMD:v1 {"action":"reject","fp":"<지문>","reason":"오탐"}
CMD:v1 {"action":"status"}  /  {"action":"status","fp":"<지문>"}
CMD:v1 {"action":"kill","form_id":"TH_TDAC"}          # 자동 입력 끄기
CMD:v1 {"action":"unkill","form_id":"TH_TDAC"}        # 자동 입력 다시 켜기(사람 명령)
```

봇은 **보낸 사람이 운영자인지(chat_id/user_id 허용 목록) 먼저 확인**한 뒤 `parse_command()` → `handle_command()` 를 부른다.
모르는 필드·중복 키·형식 오류는 모두 거절된다.

### 승인 후 Claude 실행

```python
from ops.aria.runners.claude_headless import run_for_change
row = store.get(fp)   # review == "accepted" 인 것만 실행된다
res = run_for_change(row, store, cfg.repo_root, scope="TH / TH_TDAC", claude_bin=cfg.claude_bin,
                     daily_cap=cfg.claude_daily_cap, timeout_sec=cfg.claude_timeout_sec)
```

지시문에 저장소 규칙(정부 사이트 제출 금지, 캡차 우회 금지, 개인정보 금지, `python tools/packs/build_packs.py --check` 통과 후에만,
`claude/<slug>` 브랜치 푸시 + PR, main 직접 푸시 금지)이 들어간다. `--bare` 는 절대 쓰지 않는다(CLAUDE.md 를 안 읽음).

### watchdog (GitHub Actions)

```python
from ops.aria.heartbeat import check_stale
if check_stale(doc["last_check"], now, days=3):
    kill_switch.set_stale_banner(rc_client, True)       # 켜기는 자동
# 회복 뒤 끄기: set_stale_banner(rc_client, False, heartbeat_recovered=True)
```

## 알아 둘 한계

- 화면을 스크립트로 그리는 양식 사이트(SPA)는 GET 으로 껍데기 HTML 만 보인다. 구조 해시는 껍데기 변경만 잡는다.
  TDAC 처럼 첫 화면이 Turnstile 이면 늘 `manual_check_needed` 가 나온다 — 정상이다(우회하지 않음).
- `fingerprint_store` 의 approved 막음은 실수 방지 장치다. 진짜 확정값은 사람이 머지한 main 브랜치에 있다.
- 현장 리포트는 지금 앱(M4)이 실패만 쌓으므로 실패율은 사실상 1.0, '실패 건수 기준'으로 동작한다.

## 테스트

```powershell
python -m unittest discover -s ops/aria/tests -t .
```

표준 라이브러리 `unittest` 만 쓰고 네트워크를 쓰지 않는다(가짜 fetcher·가짜 Remote Config·가짜 Firestore).
