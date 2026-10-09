# 레디포트 × ARIA 운영 연동

> 원본: `docs/source/ReadyPort_ClaudeCode_Prompt.md` (2026-09-28 기준). 장 번호는 원본과 같다.
> `[재확인]` = 조사 시점 사실이지만 바뀔 수 있음, 구현 전 공식 출처로 재확인. `[확인 필요]` = 미확정, 추측 금지·운영자에게 질문.

## 12. ARIA 운영 연동

### 12.1 전제
- ARIA는 운영자의 Windows PC에서 24시간 도는 Python 기반 자율 에이전트다. 텔레그램 봇 인터페이스(CMD:v1 JSON 명령 형식)와 크롤링·뉴스 수집·번역 파이프라인을 이미 갖고 있다.
- **ARIA 본체 코드는 이 저장소에 없다.** 이 저장소에는 `ops/aria/`에 ARIA가 불러 쓸 수 있는 **독립 모듈과 명확한 인터페이스**를 만든다. ARIA 쪽 연결은 운영자가 따로 한다.
- 역할 분담: 감지·증거 수집은 ARIA, 판단·수정은 Claude(로컬 헤드리스), 승인은 사람(텔레그램 + PR 머지), 배포는 GitHub Actions.

### 12.2 운영 흐름
1. **감지**(하루 1~2회, LLM 없이 결정적 처리): 다음 변경을 확인한 뒤, 변경 지문(diff 해시)과 증거 스냅샷을 저장한다.
   - 외교부 입국허가요건 API 전날 대비
   - 양식 페이지 구조 해시
   - 각국 공지 페이지
   - 기존 뉴스 파이프라인에 입국정책 키워드 추가(TDAC, ETIAS, arrival card 등)
2. **현장 신호**: Firestore `field_reports`를 집계한다. 실패율이 임계치를 넘으면 해당 양식의 `kill_autofill_*`를 자동으로 켜고(안전한 방향) 텔레그램으로 알린다.
3. **승인 요청**: 텔레그램으로 변경 요약과 증거 링크를 보낸다. 운영자가 CMD:v1 '처리' 명령으로 승인한다.
4. **수정**: ARIA가 로컬에서 Claude Code를 헤드리스로 실행한다.
   - `claude -p "<작업 지시>" --allowedTools "<필요 도구만>" --output-format json`
   - CLAUDE.md 규칙이 적용되도록 일반 모드로 실행한다(`--bare`는 CLAUDE.md를 읽지 않으므로 사용 금지) `[재확인]`.
   - 증거 파일과 해당 국가 팩을 입력으로 JSON·레시피를 수정하고, 스키마 검증과 선택자 확인(제출 금지)을 마친 뒤 `claude/` 브랜치에 푸시하고 PR을 만든다.
5. **머지**: 운영자가 GitHub에서 검토하고 머지한다. 비자 필요 여부·기한 등 정책 의미가 바뀌는 수정은 반드시 사람이 확인한다.
6. **배포**(GitHub Actions): 팩 서명 → Firebase Hosting 배포 → Remote Config 버전 갱신 → FCM 토픽 알림.

### 12.3 정기 작업
| 주기 | 작업 |
|---|---|
| 매일 | 정책·양식·공지·뉴스 감지(12.2-1), 현장 신호 집계 |
| 주 1회 | 인기 순위 계산·배포, 제휴 링크 점검, 교통 앱 연결 동작 점검 |
| 월 1회 | 다음 분기 축제 날짜 등 계절 정보 재확인, 90일 넘게 확인하지 않은 결제·교통 항목 재검증, 쇼핑 리스트 검색 추이 갱신, 긴급 연락처·대사관 정보 점검 |

### 12.4 ARIA 기존 이슈 대응 (필수 설계)
| 알려진 이슈 | 이 파이프라인에서 생길 사고 | 대책 |
|---|---|---|
| 태스크 중복 재제출 루프 | 같은 변경으로 Claude 실행·PR이 반복, 구독 사용량 소진 | 변경 지문을 멱등 키로 저장(SQLite). 같은 지문은 1회만 처리. 하루 Claude 호출 상한 |
| 확정값을 스냅샷으로 덮어쓰기 | 사람이 승인한 정책값을 미검증 크롤링 값이 덮어씀 | `draft / approved / published` 상태. ARIA는 draft만 쓰고, approved는 PR 머지로만 변경 |
| Watchdog 300초 타임아웃 | 여러 나라 사이트 순회 중 끊김 | 국가·양식 단위로 작업 분할, 작업별 타임아웃, 실패 국가만 재시도 |
| Gemini 일일 할당량 소진 | 요약 단계가 막히며 감지까지 멈춤 | 감지는 LLM 없이. 요약 실패해도 원본 diff는 텔레그램으로 전송 |

### 12.5 `ops/aria/` 모듈 명세
- `detectors/mofa_entry_diff.py`: 외교부 API 조회·전일 비교
- `detectors/form_structure_hash.py`: 양식 페이지 구조 해시(제출·입력 금지, 조회만)
- `detectors/notice_watch.py`, `detectors/news_keywords.py`
- `signals/field_reports.py`: Firestore 집계(Admin SDK), 임계치 판단
- `actions/kill_switch.py`: Remote Config REST로 `kill_autofill_*`만 변경
- `fingerprint_store.py`: 멱등 키 저장소(SQLite)
- `approvals/telegram_cmd.py`: CMD:v1 형식 승인 명령 파서 인터페이스(ARIA 텔레그램 봇에 연결할 어댑터)
- `runners/claude_headless.py`: `claude -p` 실행 래퍼(타임아웃, JSON 결과 파싱, 호출 상한)
- `jobs/ranking.py`, `jobs/link_check.py`, `jobs/content_freshness.py`, `jobs/shopping_trend.py`
- `heartbeat.py`: 점검 완료 시 `ops/heartbeat` 기록
- 설정은 `.env`(커밋 금지). 크롤링은 하루 1~2회, 요청 간격을 두고, 봇 차단 시 우회하지 말고 "수동 확인 필요"로 표시.

### 12.6 권한과 저장소
- ARIA 자격증명 최소화: 이 저장소 전용 fine-grained GitHub 토큰(쓰기), Remote Config 전용 서비스 계정. **Hosting 배포 권한은 ARIA에 주지 않는다**(GitHub Actions secrets에만).
- 원칙: 안전한 방향(자동 입력 끄기, 경고 배너 켜기)은 ARIA가 자동으로, 위험한 방향(새 정책값 게시, 자동 입력 다시 켜기, 레시피 변경)은 사람 승인 후.
- main 브랜치 보호(PR 리뷰 필수). GitHub 무료 플랜에서 비공개 저장소는 브랜치 보호가 제한되므로 공개 저장소를 기본으로 한다 `[확인 필요]`.

### 12.7 ARIA 감시 (단일 장애점 대책)
- GitHub Actions가 하루 1회 `ops/heartbeat`를 확인한다. 3일 이상 끊기면 운영자에게 알리고 Remote Config `stale_banner`를 켠다(앱에 "정보 점검이 늦어지고 있어요" 배너 표시).
- Claude Code 클라우드 루틴은 PC 장기 부재 시 백업으로만 고려한다. 리서치 프리뷰 단계이며, 하루 실행 한도가 있다(Pro 5회, Max 15회, Team·Enterprise 25회) `[재확인]`.

### 12.8 GitHub Actions
- `deploy-packs.yml`: main 머지 시 팩 스키마 검증 → 서명 → 관광지 서명본 스테이징(`stage_hosting.py`) → Hosting 배포 → Remote Config 버전(`attractions_version_<CC>` 포함, 한 번에 3개까지) → FCM
- `attractions-auto.yml`: 관광지 주간 자동 갱신 PR 검사 → (공개된 나라) 서명 → 머지 → `deploy-packs` 실행 (12.9)
- `aria-watchdog.yml`: 하트비트 점검(하루 1회)
- `android-ci.yml`: 빌드·단위 테스트·린트

---

> 12.9~12.12: 사장님 결정 2026-10-09 (`docs/design/ARIA_AUTOMATION_REVIEW_2026-10-09.md` 끝 표)에 따른 서버·ARIA 쪽 구현.
> 앱 화면은 따로 만든다(아래 문서 모양이 앱과의 약속).

### 12.9 관광지 주간 갱신 (검사 통과하면 자동 반영)
1. **감지** `run_weekly --step watch` (LLM 없음): `packs/src`·`packs/drafts` 의 `attractions.json` 에서 관광지가 쓰는 사실 출처
   (`sources.use` = facts·hazard·heritage_registry)와 `official_url` 을 모아 주소마다 한 번 GET. robots.txt 준수, 요청 간격 `REQUEST_INTERVAL_SEC`,
   봇 차단·robots 막음이면 `manual_check_needed`(재시도·우회 없음). 보이는 글만 공백 정리 → sha256 비교.
   바뀌면 그 주소를 쓰는 관광지마다 `~/.readyport/evidence/<CC>/<id>/aria_<출처>_<해시12>.txt`(+ `.json` 메타) 스냅샷과 Change(지문 1회).
2. **갱신** `run_weekly --step update`: 그 주 확인이 끝난 나라만, 나라마다 한 묶음. 상한 `CLAUDE_DAILY_CAP`(하루) + `ATTRACTIONS_WEEKLY_CAP`(주).
   `ARIA_DATA_DIR/worktrees/` 에 origin/main 기준 git worktree + 브랜치 `aria/attractions-<cc>-<yyyymmdd>-<지문8>` →
   Claude 헤드리스(도구: 읽기·편집·`build_attractions.py check` 만, 웹·git 금지)가 **새 스냅샷만 근거로** 작업본 사실 칸을 고치고 `extract.json` 에 글자 그대로 인용.
   → 범위 검사(`tools/attractions/auto_update_guard.py`: 작업본만, 이미 있는 관광지의 사실 칸만) → `build_attractions.py check <CC>`
   → `build_attractions.py record <CC> --ids …`(인용 대조·copycheck, 통과 기록만 `packs/curation`) → 커밋·푸시 → PR + 라벨 `attractions-auto`.
   어느 단계든 실패하면 **PR 없이 텔레그램 알림만**.
3. **서명·배포** `attractions-auto.yml`: 범위 검사·check·`verify-quotes --use-record`(증거는 운영자 PC 에만 있으므로 ARIA 가 남긴 facts 해시 기록으로 확인)·도구 테스트
   → 공개된 나라면 `apply-drafts <CC> --ids …`(그 곳만 원본으로 옮겨 서명, secrets `ATTRACTIONS_SIGNING_KEY_PEM`) → 서명본을 PR 브랜치에 커밋
   → squash 머지 → `deploy-packs.yml` 실행(Hosting + `attractions_version_<CC>`). 공개 전 나라는 작업본만 머지(새 나라 공개는 D22 사람 승인).
   - 신뢰 경계: ARIA 는 서명 키가 없다. 대신 CI 가 확인하는 '인용 대조 완료'는 ARIA PC 의 기록을 믿는다(증거 원문을 공개 저장소에 올리지 않기 위해).
4. **안전한 방향 예외 — '공식 안내가 바뀌었어요 — 확인 중'**: 스냅샷에 휴관·공사·폐쇄 표현(임시 휴관·休館·工事·closed·闭馆 …)의 **개수가 늘면**
   사람 승인 전에도 Firestore `attraction_flags/{CC}` 를 쓴다(허용 목록: 이 컬렉션·아래 모양만). 그 주소의 표현 수가 기준선 이하로 돌아오면 뗀다.

| 문서 | 모양 | 쓰는 쪽 | 읽는 쪽 |
|---|---|---|---|
| `attraction_flags/{CC}` | `{ids: [관광지 id…], kind: "check_in_progress", at: timestamp}` | ARIA 서비스 계정만 | 누구나(get) |

앱(나중 작업): 상세 화면에서 `ids` 에 든 관광지면 '공식 안내가 바뀌었어요 — 확인 중' 띠. 받은 관광지 파일의 그 곳 `status.last_verified` 가 `at` 날짜 이후면 띠를 숨긴다(이미 반영됨).
문서가 없거나 못 읽으면 띠 없음(오프라인 우선).

### 12.10 관광지 평점 (구글 별점 실시간 + 레디포트 자체 평점)
- **구글 별점**: 저장하지 않는다. 관광지 파일에는 `google_place_id`(선택, 스키마·enums 에 추가)만 둔다 — 약관상 place ID 는 기간 제한 없이 저장 가능 `[재확인]`.
  채우기: `python tools/attractions/build_attractions.py place-ids <CC> [--ids …] [--write]` — Places API (New) Text Search, 필드 마스크 `places.id` 만(IDs Only),
  좌표 둘레 약 1km 사각형 안에서만. 키는 `~/.readyport/keys/maps.properties` 의 `MAPS_API_KEY`(없으면 분명한 안내 후 종료). 앱이 상세를 열 때 조회·'Google 제공' 표시.
- **자체 평점**:

| 문서 | 모양 | 규칙 |
|---|---|---|
| `attraction_ratings/{CC}_{관광지 id}/votes/{uid}` | `{stars: 1–5 정수, at: 서버 시각, visited: true}` | 로그인한 본인만 만들기·고치기·지우기·읽기(한 사람 한 표), 목록 불가, 자유 글 없음 |
| `attraction_rating_stats/{CC}` | `{<관광지 id>: {avg: 소수 1자리, n}, …, _meta: {updated_at, min_n}}` | 누구나 get, 쓰기는 서비스 계정만 |

  - 만 19세 이상만 평가: **서버(규칙)로는 확인할 수 없다.** 앱이 게시판과 같은 기준(휴대폰의 여권 생년월일, 기기 안 판정)으로 평가 화면을 막는다.
  - `run_weekly --step ratings` 가 주 1회 집계. 평가가 `RATINGS_MIN_N`(기본 5)명보다 적은 관광지는 통계 문서에 넣지 않는다(앱은 '평가가 아직 적어요').

### 12.11 여행 계획 요청 (비공개, 1인 7일 2회)

| 문서 | 모양 | 규칙 |
|---|---|---|
| `plan_requests/{id}` | `uid`, `country`(9개국), `purposes`(1~5개: sightseeing·food·shopping·nature·history_culture·relaxation·kids_family·activity·other), `purpose_note`(선택 ≤200자), `travelers`{adults·seniors·teens·children 0~20(합 1~20), genders?{female·male}}, `mobility`(선택: long_walk_hard·wheelchair·stairs_hard·with_infant·other_none), `sensitive_consent: true`(mobility 를 고르면 필수, 안 고르면 없음), `days`(1~30) **또는** `start_date`·`end_date`('YYYY-MM-DD'), `budget_band`(budget·standard·comfort·premium), `currency: "KRW"`, `status: "queued"`, `createdAt` = 서버 시각 | 만들기: 로그인 본인 + 같은 묶음에서 `plan_quota/{uid}` 갱신. 읽기: 본인·운영자. 이용자 수정은 취소만(queued·processing → `cancelled`, `finishedAt` = 서버 시각). 삭제 불가 |
| `plan_quota/{uid}` | `{last, prev, lastRequestId}` | 새 요청과 같은 묶음에서만. `prev` 는 직전 `last`, 직전 `prev` 가 7일 안이면 거절(= 7일에 2번). 본인만 읽음, 지우기 불가 |
| `plan_results/{id}` | `{uid, request_id, country, plan: {days[{day, title, items[{time_hint, place_id?, title, note}]}], tips[], budget_notes[], caveats[]}, ai_generated: true, notice_ko, engine, pack_version, attractions_version, createdAt}` | 본인·운영자 읽기(없으면 '없음'), 쓰기는 서비스 계정만 |

- ARIA 가 쓰는 칸: 요청 `status`(processing·done·failed), `processingAt`, `finishedAt`, `error_code`(invalid_request·quota_exceeded·country_unavailable·engine_error·engine_timeout·invalid_output), `updatedAt`.
- `run_hourly`: queued 요청 → 모양 재확인 + 7일 2회 이중 확인 → processing → 지시문(요청은 '자료'로만 + 그 나라 **서명된** 앱 내장 팩·관광지) → 엔진
  → 결과 검사(일수·모양·길이, `place_id` 는 실제 관광지만, 시각·금액 숫자 금지) → `plan_results` + done. 처리 중 취소되면 결과를 쓰지 않는다.
- 엔진: `ops/aria/runners/plan_engine.py` 의 `generate()` 하나(지금 Claude Code 헤드리스 — 사장님 결정. 약관 검토 메모는 결정 표 참고). 빈 임시 폴더, 도구 모두 막음, 지시문은 표준 입력.
- 상한: `PLAN_DAILY_CAP`(하루), `PLAN_MAX_PER_RUN`, `--budget-sec`. 요청 내용은 로그·출력·텔레그램 어디에도 남기지 않는다(id·상태·코드만).
- 정리(하루 한 번, `plan_cleanup`): 끝난 지 30일(`PLAN_RETENTION_DAYS`) 지난 요청·결과 삭제, 끝나지 않은 채 30일 넘은 요청 삭제, 6시간 넘게 멈춘 processing → failed, 오래된 `plan_quota` 삭제.
- 만 19세 이상·게시판 계정 조건은 앱이 확인한다(규칙으로 강제 불가 — 12.10 과 같음). 익명 계정이라 앱을 다시 깔면 새 ID 가 되는 한계는 남는다.

### 12.12 ARIA 스케줄 (ARIA 본체에 운영자가 등록)
| 언제 | 명령 (저장소 루트) | 비고 |
|---|---|---|
| 월 06:00 | `python -m ops.aria.run_weekly --live --step watch` | 240초 예산(watchdog 300초 안) |
| 월 06:10~09:00, 10분마다 | `python -m ops.aria.run_weekly --live --step watch --retry-failed` | 출력 `watch.rerun_later` 가 비면 멈춤 |
| 월 watch 끝난 뒤 1번 | `python -m ops.aria.run_weekly --live --step update` | **백그라운드 작업**(watchdog 밖, Claude 최대 `CLAUDE_TIMEOUT_SEC`) |
| 월 09:30 | `python -m ops.aria.run_weekly --live --step ratings` | Firestore 필요 |
| 매시 정각 | `python -m ops.aria.run_hourly --live` | **백그라운드 작업**(계획 하나에 몇 분), 하루 한 번 정리 포함 |

텔레그램으로 보내려면 run_daily 처럼 파이썬에서 `run_weekly(..., notifier=TelegramNotifier())`·`run_hourly(..., notifier=...)` 로 부른다.

**사람이 할 일**: ① GitHub secrets `ATTRACTIONS_SIGNING_KEY_PEM`(+암호 걸었으면 `ATTRACTIONS_SIGNING_KEY_PASS`) ② main 보호에 '승인 필요'가 있으면 vars `ATTRACTIONS_AUTO_APPROVE=true` + 'Allow GitHub Actions to create and approve pull requests'
③ 서비스 계정 `aria-ops`(Cloud Datastore User 역할) 키를 저장소 밖에 두고 `GOOGLE_APPLICATION_CREDENTIALS` ④ Places API (New) 사용 설정 + `maps.properties` 의 키에 Places API 허용
⑤ Firestore 규칙 배포(`firebase deploy --only firestore:rules`) ⑥ ARIA 스케줄 등록(위 표).
