# 레디포트 아키텍처

> 원본: `docs/source/ReadyPort_ClaudeCode_Prompt.md` (2026-09-28 기준). 장 번호는 원본과 같다.
> `[재확인]` = 조사 시점 사실이지만 바뀔 수 있음, 구현 전 공식 출처로 재확인. `[확인 필요]` = 미확정, 추측 금지·운영자에게 질문.

## 9. 아키텍처

### 9.1 기술 스택 (버전은 최신 안정판 확인 후 결정)
- Kotlin, Jetpack Compose, Material 3, Navigation
- DI: Hilt / 직렬화: kotlinx.serialization / 저장: Room(국가 팩·체크리스트·쇼핑 목록), DataStore(설정)
- 백그라운드: WorkManager(Wi-Fi 조건 다운로드, 알림)
- 카메라: CameraX, 문서 인식: ML Kit Text Recognition v2(번들형), NFC: JMRTD(2차)
- 암호화: Android Keystore + Tink(서명 검증 포함)
- WebView(자동 입력), Android TextToSpeech
- Firebase(BoM): Remote Config, Cloud Messaging, Analytics, Crashlytics, App Check(Play Integrity), Firestore
- minSdk 26 권장(적응형 아이콘 기준, 구형 아이콘도 첨부됨) `[확인 필요]`

### 9.2 "앱은 엔진, 정책·양식·콘텐츠는 데이터"
- 정책이 바뀌어도 앱 업데이트 없이 데이터만 바꿔 대응한다.

| 변경 유형 | 예시 | 반영 수단 | 속도 |
|---|---|---|---|
| 긴급 차단·공지 | 사이트 개편으로 자동 입력 오작동 | Remote Config 스위치(국가·양식별 자동 입력 끄기 → 수동 모드), 배너 | 수 분~수 시간 |
| 규칙·양식 변경 | 기한·필드·수수료·URL 변경 | 서명된 국가 팩 새 버전을 Hosting에 배포 + Remote Config 버전 번호 갱신 | 당일 |
| 새로운 절차 유형 | 셀피 단계 신설, 앱 전용 전환 | 앱 업데이트(Play 인앱 업데이트, 최소 버전 강제) | 수일 |

- 모든 팩에 `schema_version`. 구버전 앱이 모르는 스키마를 만나면 오작동하지 말고 수동 모드로 전환.
- 설치 파일에 **기본 팩**을 내장해 서버가 막혀도 앱이 동작하게 한다.

### 9.3 Firebase 무료(Spark) 제약과 사용 방식 `[재확인]`

| 서비스 | 제약 | 사용 |
|---|---|---|
| Hosting | 저장 10GB, 전송 하루 360MB | 서명된 국가 팩·레시피·순위 파일 배포(주력) |
| Firestore | 1GiB, 읽기 5만/일, 쓰기 2만/일 | 익명 실패 리포트, 찜 수 집계, ARIA 하트비트 |
| Remote Config | 무료 | 스위치, 버전 포인터, 최소 버전, 배너 |
| FCM | 무료 | 국가별 정책 변경 알림(토픽) |
| Analytics·Crashlytics·App Check | 무료 | 오류 감지, 남용 방지 |
| Cloud Storage | 2026-02-03부터 Blaze 필요 → **사용 안 함** | — |
| Cloud Functions | Spark 불가 → **사용 안 함** | 서버 로직은 ARIA가 대신 |

- 한도를 넘으면 과금 대신 서비스가 제한된다 → 기본 팩 + 로컬 캐시로 버틴다.
- **텍스트 중심**: 사진은 Firebase로 서비스하지 않는다. 아이콘·일러스트, 앱 내장 대표 이미지, 위키미디어 공용 썸네일 외부 링크(라이선스 표기)를 쓴다.
- **버전 확인 후 다운로드**: Remote Config 버전이 바뀐 국가 팩만 받는다. 팩은 '국가 기본 + 도시 추가'로 나누고 바뀐 부분만 갱신.
- 참고 규모: 텍스트 팩 200KB 가정 시 하루 약 1,800회 다운로드 가능.

### 9.4 Firestore 데이터 (PII 없음)
- `field_reports/{autoId}`: `form_id`, `pack_version`, `step_id`, `error_code`, `app_version`, `ts`. 생성만 허용, 필드 검증 규칙.
- `favorite_counts/{city_id}`: `count`(increment만 허용).
- `ops/heartbeat`: ARIA가 Admin SDK로만 기록(`last_check`, `jobs`).
- `videos/{ISO2}`: 나라별 YouTube 영상 목록(서명 포함). GitHub Actions(`videos.yml`)만 쓰고, 앱은 문서 하나 읽기(get)만(0.3.0 — 아래 기록).
- App Check 적용.

### 9.5 Remote Config 키
- `kill_autofill_{form_id}` (bool), `pack_version_{country}`, `ranking_version`, `min_app_version`, `banner_{id}`, `stale_banner` (ARIA 점검 지연 시)

### 9.6 FCM
- 토픽 `country_{ISO2}`. 사용자가 여행을 만들거나 찜하면 기기에서 구독. 여행 일정 자체는 서버에 올리지 않는다.

### 9.7 교통 앱 연결
- 국가 팩에 국가별 교통 앱 목록(패키지명, 연결 방식)을 둔다. 예: 태국 Grab·Bolt / 일본 GO·Uber. 동남아에는 Uber가 없다 `[재확인]`.
- 3단계 연결:
  1. 공식 문서로 링크 형식을 공개한 앱은 목적지까지 채워서 연다.
     - Google 지도: `https://www.google.com/maps/dir/?api=1&destination={lat},{lng}&travelmode=transit`
     - Uber: `https://m.uber.com/ul/?action=setPickup&pickup=my_location&dropoff[latitude]=..&dropoff[longitude]=..` `[재확인]`
     - Grab 등: 공식 규격이 없으면 2단계로 `[확인 필요]`
  2. 앱 실행 + 목적지 주소(현지어) 클립보드 복사
  3. 미설치 시 Play 스토어로 연결
- 연결 실패 시 자동으로 다음 단계로 넘어간다.
- 출국 전 '앱 준비' 체크리스트: 설치, 가입, 카드 등록, 전화번호 인증을 한국에서 미리.

### 9.8 오프라인
- 찜 → WorkManager `NetworkType.UNMETERED`(설정에서 변경 가능) 조건으로 국가·도시 팩 자동 다운로드.
- 팩 내용: 입국 서류 안내, 교통·결제·음식·계절·안전, 현지어 카드, 긴급 연락처, 숙소 주소(현지 글자), 쇼핑 리스트, 꼭 챙길 물건 규칙.
- 지도 파일은 제공하지 않는다(무료 한도 초과). Google 지도 앱의 오프라인 지역 저장을 단계별로 안내.
- 도움 탭 전체와 입국 QR은 항상 오프라인 동작.
- 현지어 음성은 `TextToSpeech.isLanguageAvailable()`로 확인하고, 없으면 카드 표시만.

### 9.9 여행 장부·체크리스트 저장 (2026-10-02)
- 예전: DataStore `trip`의 `current_trip` 키 하나 = 여행 하나. **지금**: 같은 DataStore의 `trip_book` 키 하나에 JSON `TripBook { trips: [Trip(id=UUID, …)], checks: {tripId → TripChecks}, passportSaved }`. 백업 제외 규칙(`backup_rules.xml`·`data_extraction_rules.xml` 앱 데이터 전체 제외)은 그대로 적용된다.
- `Trip`은 날짜·나라·도착 기록뿐(개인정보 없음). 여행은 `id`로 가린다 — 같은 나라라도 다른 여행. `TripChecks = { marks(항목 id → 사람이 정한 체크), custom(내 항목 글), passport(여권 결과), formSubmitted }`.
- **옮기기**: 읽을 때 `trip_book`이 없고 `current_trip`만 있으면 그 여행을 목록 하나로 읽는다(id = 저장본 글자에서 만든 이름 UUID — 옮기기 전에 읽어도 같은 id). 앱 시작 때 `migrateLegacy(settings.haveItems)`가 장부로 옮기고 예전 '꼭 챙길 물건' 체크를 그 여행의 `essential.<id>` 체크로 옮긴 뒤 예전 키를 지운다. 새 장부를 한 번이라도 쓰면 예전 키는 지운다(같은 여행이 두 번 생기지 않게). 테스트 `TripRepositoryTest`.
- **지금 여행** = `TripSelection.active`(여행 중 → 가장 가까운 다가오는 여행 → 가장 최근 지난 여행: 귀국 단계는 정리할 때까지, 정리 단계는 14일). `TripRepository.trip`·`current()`·`update(transform)`는 이 여행을 뜻한다(오늘 화면·도착 감지·클라우드 토픽이 그대로 쓴다). 단계 계산 `TripStages.compute`는 여행마다 그대로.
- **여권 남은 기간의 개인정보 처리**: `TripSignalsRecorder`(앱 범위)가 지갑 상태를 지켜보다가 **열려 있을 때만** 여행마다 `PassportValidity.check(만료일, 여행, 팩 기준)`을 계산해 결과(`ok`/`short`/`unknown`, 기준 달 수, 기준일 종류, 어떤 여행 날짜·기준으로 셌는지 key)만 장부에 쓴다. 만료일은 메모리에서만 쓰고 저장·로그·알림·서버에 넣지 않는다(`toString`도 상태만). 지갑이 잠겨 있으면 지난 결과를 쓰고, 여행 날짜나 팩 기준이 바뀌면(key 불일치) 결과를 쓰지 않는다. 지갑 파일이 없으면 '여권 없음'. 같은 방법으로 '이 여행 입국 카드를 냈는지'(제출 시각이 그 여행 입국 카드 기간 ~ 귀국일)를 있다/없다로만 적는다 — 같은 나라를 또 가도 지난 여행 제출로 닫히지 않는다.
- 체크리스트 = 순수 함수 `Checklist.build(여행, 색인, 팩, 체크, 여권 있음, 오늘)` — 화면 ViewModel(체크리스트·목록·오늘·홈·여행 준비·꼭 챙길 물건)이 `ChecklistProvider`로 같은 계산을 쓴다.

---

## 10. 데이터 형식 (초안 — 구현하며 JSON Schema로 확정)

### 10.1 국가 팩 `packs/{ISO2}/pack.json`
```json
{
  "schema_version": 1,
  "country": "TH",
  "version": "2026.09.27-1",
  "last_verified": "2026-09-27",
  "sources": [{"id": "mofa_entry", "name": "외교부 입국허가요건", "url": "[확인 필요]"}],
  "requirements": [
    {"nationality": "KR", "purpose": "tourism", "visa": "not_required", "stay_limit_days": null,
     "forms": ["TH_TDAC"], "source": "mofa_entry", "last_verified": "2026-09-27"}
  ],
  "content": {
    "basic": {}, "seasons": {"11": {}}, "transport": {}, "payment": {}, "food": {}, "safety": {},
    "places": []
  },
  "transport_apps": [
    {"name": "Grab", "package": "[확인 필요]", "link_type": "open_and_copy"},
    {"name": "Google Maps", "package": "com.google.android.apps.maps", "link_type": "maps_url"}
  ],
  "phrases": [{"id": "restroom", "ko": "화장실이 어디예요?", "en": "Where is the restroom?", "local": "ห้องน้ำอยู่ที่ไหน", "tts_lang": "th-TH"}],
  "emergency": [{"id": "local_emergency", "label_ko": "현지 긴급 전화", "number": "[확인 필요]"}],
  "essentials_rules": [],
  "shopping": [],
  "signature": "base64..."
}
```

### 10.2 양식 레시피 `recipes/{form_id}.json`
```json
{
  "schema_version": 1,
  "form_id": "TH_TDAC",
  "version": "2026.09.27-1",
  "official_url_patterns": ["https://[공식 도메인]/*"],
  "open_window": {"hours_before_arrival": 72},
  "steps": [
    {"id": "personal", "fields": [
      {"key": "passport.surname", "selector": "[확인 필요]", "type": "text",
       "labels": {"ko": "성 (영문)", "en": "Family name", "local": "นามสกุล"},
       "source": "passport", "confirm": "bulk"}
    ]},
    {"id": "travel", "fields": [
      {"key": "trip.purpose", "selector": "[확인 필요]", "type": "single_choice",
       "labels": {"ko": "여행 목적", "en": "Purpose of travel", "local": "วัตถุประสงค์การเดินทาง"},
       "source": "user_choice", "confirm": "individual",
       "options": [{"value": "[확인 필요]", "ko": "관광", "en": "Tourism", "local": "ท่องเที่ยว"}]}
    ]}
  ],
  "checkpoints": ["captcha", "final_submit"],
  "result_capture": {"qr": true, "confirmation_no": true, "pdf": true},
  "signature": "base64..."
}
```
- `confirm`: `bulk`(서류에서 온 값) / `individual`(선택·추정·신고 질문)
- 선택자는 공식 사이트를 **제출하지 않고** 구조만 확인해서 채운다.

### 10.3 인기 순위 `rankings/latest.json`
```json
{
  "version": "2026-W39",
  "basis": {"air_month": "2026-08", "search_week": "2026-W39"},
  "weights": {"air": 0.5, "search": 0.3, "favorites": 0.2},
  "items": [{"city_id": "OSA", "rank": 1, "prev_rank": 1, "score": 0.0,
             "reasons": ["passenger_up", "search_rising"], "visa_free_kr": true, "flight_hours": null}]
}
```

### 10.4 꼭 챙길 물건 규칙
```json
{"id": "power_bank", "conditions": {"always": true}, "rule_badge": "carry_on_only",
 "reason_ko": "지도·번역을 많이 써서 배터리가 빨리 닳아요.",
 "link": {"type": "affiliate", "partner": "[확인 필요]", "url": "[확인 필요]"}}
```
- `link.type`: `affiliate`(물건·여행 서비스, 앱에 '수수료 링크' 표시) / `official_info`(보험·환전·카드, 수수료 없음)
- 조건: 목적지 기후·플러그·전압, 기간, 아이 동반, 계절

### 10.4b 여행 체크리스트 틀·여권 남은 기간 (2026-10-02)
- **색인 `checklist[]`** (`index.schema.json`): `{id, phase, icon, kind, title_ko?, body_ko?, from?, action?, when?}` — 모든 나라 공통 순서와 '챙기기' 안내 글(숫자 없음). `phase` = month · week · three_days · departure_day · arrival · during · before_return · back. `kind` = generic(안내) · auto(앱이 확인: `from` = passport_validity · passport_saved · offline_pack · entry_form · passport_destroyed) · pack(팩·색인 사실: visa · advisory · consular · emergency · return_facts) · essential(`from` = `essential:<id>`, 꼭 챙길 물건 규칙과 조건 그대로). **정책 사실은 틀에 적지 않는다** — 사실이 필요한 항목은 팩·색인 값에서 가져오고 값이 없는 나라에서는 앱이 항목을 만들지 않는다. 서명된 색인이라 팩과 같이 고쳐 배포한다.
- **나라 팩 `checklist[]`** (`pack.schema.json`): `{id, phase, icon, title_ko, section, text_ko}` — `text_ko`는 같은 팩 `sections[section].body_ko`의 한 문장과 글자까지 같아야 한다(`build_packs.py`가 검사, 출처·확인일은 그 섹션 것). 예: 현금 신고 기준, 중국 감기약 성분·주숙등기, 싱가포르 담배·전자담배.
- **`requirements[].passport_validity`**: `{months(1~24), basis(arrival|departure|stay_end), source, last_verified}` 또는 `null`(공식 안내가 기준을 밝히지 않음 — 앱은 '공식 안내에서 확인하세요'만). `build_packs.py`가 출처·basis·months를 검사, 테스트 `tools/packs/test_build_packs.py`.
- **섹션 id `rules`**(알아 둘 규정): 출처가 다른 공지(예: 0404 안전공지)의 규정 문장을 '한 섹션 한 출처' 규칙을 지키며 담는다. 나라 화면 '입국·비자' 갈래의 '들어갈 때' 바로 뒤에 보이고, 여행경보 단계가 아니라서 위험 배너로 올리지 않는다.

### 10.5 쇼핑 항목
```json
{"id": "dried_mango", "category": "food",
 "names": {"ko": "말린 망고", "local": "มะม่วงอบแห้ง", "en": "Dried mango"},
 "where_ko": "대형마트·공항 면세점", "why_ko": "선물용으로 많이 사요",
 "import_status": "allowed", "import_note_ko": "",
 "popularity": {"source": "naver_datalab", "period": "2026-09", "relative": null}}
```
- `import_status`: `allowed` / `caution` / `prohibited` — 관세청·농림축산검역본부 기준. 예: 생과일은 `prohibited`.

---

## 구현 결정 기록 (M3, 2026-09-28)

- **서명 방식**: 10.1 초안의 `"signature"` 필드 대신 **분리 서명 파일** `<파일>.sig` = `{"kid","alg":"Ed25519","sig"}`. 서명 대상은 게시되는 JSON 바이트 전체(공백 없는 UTF-8)라서 JSON 정규화 문제가 없다. 키 교체는 `kid`로 한다(앱 `PackKeys.TRUSTED`에 새 키 추가 → 전체 재서명 → 옛 키 제거).
- **키**: `rp-2026-1`. 비밀키는 운영자 PC `~/.readyport/keys/`에만 있다(`tools/packs/keygen.py`). GitHub Actions에는 secret `READYPORT_PACK_KEY`(PEM 내용)와 `READYPORT_PACK_KID`로 등록한다. ARIA에는 주지 않는다(12.6).
- **원본과 배포본**: 사람이 고치는 원본은 `packs/src/`(서명 없음). `tools/packs/build_packs.py`가 스키마(`packs/schema/`)·출처 연결·미확정 표시(`[확인 필요]`,`[재확인]`)를 검사한 뒤 서명해 `app/src/main/assets/packs/`(내장 기본 팩, 커밋함)와 `hosting/public/packs/`(배포본, 커밋 안 함)에 쓴다. 원어민 검수 전 문장(`reviewed:false`)은 경고만 한다. (2026-10-01 운영자 결정 2번으로 화면 배지는 뺐다. `reviewed` 필드·빌드 경고와 출시 전 원어민 검수(QUESTIONS C10·C16·C17)는 그대로 — 아래 디자인 개편 기록)
- **저장**: 받은 팩은 Room 대신 `noBackupFilesDir/packs/` 파일로 둔다. 읽을 때마다 서명을 다시 검증하고(기기 안 변조 대비), 내장본과 받은 본 중 서명이 맞고 스키마를 아는 가장 새 버전을 쓴다. Room은 체크리스트·쇼핑 목록 상태(M8)에서 쓴다.
- **스키마 변경**: 10.1 대비 `embassy.address`(공관이 공개한 표기 그대로 — 한글 주소를 지어내지 않음), `forms[].window_days_including_arrival`(태국 TDAC는 공식 안내가 "도착일 포함 3일"이라 72시간 표기 대신), `phrases[].reviewed`, `sections[]`(콘텐츠를 id별 목록으로), `procedures[]`(위기 때 할 일 순서), 인덱스의 `common_emergency`(영사콜센터).
- **배포 주소**: `https://readyport-app.web.app/packs/{index.json | CC/pack.json}` (+ `.sig`), `Cache-Control: max-age=300`. Remote Config 키 `index_version`, `pack_version_{CC}`와 같은 값일 때만 받는다(무료 전송 한도).
- **동기화**: `PackSyncWorker`(WorkManager) — 찜을 바꾸면 즉시 1회, 그리고 하루 1회. 기본은 와이파이(UNMETERED)에서만. 서명·스키마가 틀린 팩은 버리고 재시도하지 않는다. 네트워크 오류만 재시도.

## 구현 결정 기록 (M4, 2026-09-28)

- **엔진은 앱 안에**: `app/src/main/assets/autofill/engine.js`. 레시피(`packs/src/recipes/<FORM_ID>.json`, 서명)는 선언형 데이터만 준다. 엔진은 어떤 요소도 `click()` 하지 않고, submit·button·checkbox·radio·file·password·hidden 입력과 목록형(combobox) 칸을 건드리지 않으며, 말풍선은 `textContent`로만 쓴다. WebView에 `addJavascriptInterface`를 두지 않는다(사이트 스크립트가 앱에 닿을 길이 없음).
- **칸 종류 2가지**: `text`(앱이 채움)와 `assist`(값을 한글 말풍선으로 보여 주고 사람이 입력). 선택 목록·달력·자동 완성은 실제 선택지 글자와 날짜 형식을 확인하기 전까지 `assist`로 둔다 — 앱이 추측해서 고르지 않는다.
- **TDAC 선택자 출처**: 사이트 첫 화면이 Cloudflare Turnstile(사람 확인)로 막혀 있어 입력 화면을 열지 않았다(우회 금지). 대신 공개된 앱 코드(`/arrival-card/chunk-*.js`, 사이트 버전 2026.09.00-0543)의 `formControlName` 값으로 선택자(`[formcontrolname="..."]`)를 확인했다. 선택지 글자·날짜 형식은 서버에서 받아 오므로 코드에 없다.
- **사이트의 '다음'은 사람이 누른다**: 엔진은 지금 화면에 보이는 단계(`probe` 선택자)만 채운다. 사람 확인·건강 질문·서약 체크·이메일·최종 제출은 표시만 한다(`checkpoints`).
- **실패 → 수동 모드**: 선택자를 못 찾으면(`missing`) 수동 모드(값 복사 + 공식 사이트)를 권하고 익명 리포트(양식·레시피 버전·단계·오류 코드·앱 버전·사이트 버전, 개인정보 없음)를 `noBackupFilesDir/reports/field_reports.jsonl`에 쌓는다. Firestore 전송은 M9. Remote Config `kill_autofill_{FORM_ID}`가 켜지면 처음부터 수동 모드.
- **값의 출처**: 여권·예약 서류 값은 보관함 원본에서 매번 가져오고, 사용자가 고르거나 적은 값·고친 값만 `VaultContents.forms[FORM_ID].values`(암호화)에 둔다. 제출 완료 화면 주소(`submitted_url_contains`)를 보면 상태를 `submitted`로 바꾼다.
- **클립보드**: 복사 후 60초 뒤 우리 값일 때만 지운다. Android 13+는 `EXTRA_IS_SENSITIVE`.
- **테스트**: `tools/autofill`(node --test + jsdom)로 엔진을 가짜 화면(`mock_tdac.html`)에서 검사한다. 실제 정부 사이트에는 연결하지 않는다(작업 규칙 1).
- **리허설로 바뀐 것(2026-09-28)**: 폰 Chrome(원격 디버깅, 포트 9333)으로 TDAC 입력 화면을 열어 구조를 읽었다. 날짜 칸이 `yyyy/mm/dd` 직접 입력이라 `transform: date_slash`로 채운다. 선택지(목적·숙소·성별·오는 방법)는 서버 응답 `gotoAdd`의 목록으로 확인해 레시피 `options[].site`·`site_value`에 적었다. 엔진은 여전히 목록·라디오를 누르지 않고, "관광 → HOLIDAY"처럼 고를 글자를 말풍선으로 보여 준다. 레시피 버전 2026.09.28-2.

## 구현 결정 기록 (M5, 2026-09-28)

- **양식 5개 현황**: TDAC·SGAC·All Indonesia·MDAC는 레시피가 있고, Visit Japan Web은 계정 로그인이 필요해 레시피 없이 안내·수동 모드. 구조 확인은 폰 Chrome에서 입력 화면을 열어 읽거나(입력·제출 없음) 공개 앱 코드의 입력 칸 이름·id로 했다. 입력값을 넣어야만 보이는 다음 단계는 공개 코드로만 확인했다(가짜 값으로 정부 양식을 진행하지 않음).
- **엔진 `select`**: MDAC처럼 기본 `<select>`인 칸은 선택지 글자가 **정확히 하나** 같을 때만 고른다(클릭 없음, change 이벤트). 예: "KOR - REPUBLIC OF KOREA" (같은 목록에 북한 항목도 있어 부분 일치는 쓰지 않는다). 레시피 `site_map`(앱 값→사이트 글자)과 `options[].site`로 값을 만든다.
- **값 변환**: `date_dmy`(dd/MM/yyyy), 항공사 코드·숫자 나누기(`trip.flight_prefix`/`trip.flight_digits`), 이름 순서(`passport.full_name_given_first` — SGAC는 'Given Name followed by Surname'). 순서를 확인 못 한 이름 칸(MDAC·All Indonesia)은 assist로 두고 "여권에 적힌 그대로"라고 안내한다.
- **사실 확인 기록**: `docs/research/2026-09-28_M5_countries.md`. UNVERIFIED 값(MDAC 요금, 싱가포르 긴급여권 수수료 등)은 팩에 넣지 않았다.

## 구현 결정 기록 (M7, 2026-09-28)

- **교통 앱 3단계**(`transport/RideLinker`): ① 공식 문서가 있는 링크로 목적지까지(Google 지도 `maps/dir/?api=1&destination=&travelmode=transit`, Uber `uber://riderequest?...dropoff[...]` — 좌표가 있을 때만) ② 앱만 열고 현지어 주소를 클립보드에(60초 뒤 지움) ③ 설치 안 됨 → Play 스토어. 앞 단계가 실패하면 자동으로 다음 단계.
- **나라별 앱**(국가 팩 `transport_apps`, 각 항목에 공식 출처): 태국·말레이시아 Grab·Bolt, 싱가포르·인도네시아 Grab·Gojek, 일본 GO·S.RIDE·Uber, 모든 나라 Google 지도. Grab·Bolt·GO·Gojek은 목적지를 채우는 공개 규격이 없어 ②부터(Grab의 grab://open은 파트너 API 문서에만 있어 쓰지 않음). 패키지명은 Google Play 페이지로 확인.
- **설치 확인**: `QUERY_ALL_PACKAGES` 없이 매니페스트 `<queries>`에 위 패키지만 선언.
- **가는 곳**: 이름·현지어 주소를 잠금 없는 기기 저장소(DataStore, 백업 제외)에 — 인터넷·지문 없이 '기사님께 보여주기'를 바로 열기 위해. 여권 같은 개인정보는 넣지 않는다.
- **실기기 확인(2026-09-28)**: 미설치 Grab → Play 스토어 열림, 설치된 Google 지도 → 길찾기 화면으로 열림(지도 앱 첫 실행 약관 동의 화면은 사람이 할 일이라 누르지 않음). 연결 실패 → 앱 열고 주소 복사는 Robolectric 테스트로 확인.

## 구현 결정 기록 (M8, 2026-09-29)

- **꼭 챙길 물건**(`prep/Essentials`, index `essentials`): 모든 나라 공통 규칙을 index에 두고, 조건 `always`/`plug_differs`/`voltage_differs`로 여행지에 맞춰 고른다. **목록 순서 = 추천 순서**(수수료 링크 여부로 바꾸지 않음, 테스트로 고정). 여행이 없으면 `always`만.
- **전원 정보**: IEC 세계 플러그 안내가 폐지되어(2026-09 확인) 각국 정부·전력회사·관광청 자료로 바꿨다. 플러그 형 문자(A·C 등)는 출처가 밝힐 때만 쓰고, 대신 `plug_ko`(출처 설명)와 `kr_plug_fits`(한국 둥근 2핀이 맞는지, 모르면 null)를 둔다. null이면 어댑터를 권한다(챙겨 가서 손해 없음 — 안전한 쪽). 전압은 10% 넘게 다를 때만 '전압 확인'(220↔230V는 같은 계열로 봄, 설계 기준). 일본은 공식 페이지가 플러그 모양을 밝히지 않아 null.
- **수수료 고지·표시**: 고지 띠(`essentials_fee_disclosure`)는 '꼭 챙길 물건' 맨 위(항상). '수수료 링크' 라벨(`essentials_fee_link_label`)은 `link.type == affiliate`에만 — 앱 안 낱말은 '제휴'를 쓰지 않는다(정부 비제휴 문구와 섞이지 않게). 보험은 `official_info`(보험다모아)로 수수료 없이 안내. `build_packs.py`가 보험·환전·카드·금융 항목의 affiliate 링크를 막는다. 아직 가입한 제휴 프로그램이 없어 수수료 링크는 0개(가입·약관 확인은 사람 작업).
- **보조배터리 규정**: 국토교통부 2026-04-08 발표(4-20 시행) 기준 — 위탁 금지, 1인당 2개(160Wh 이하), 기내 충전·사용 금지. 2025년의 '100Wh 이하 5개'는 폐지된 기준이라 쓰지 않는다.
- **쇼핑 리스트**(국가 팩 `shopping`): 관광청이 소개하는 품목 분류만(브랜드명·사진 없음). 네이버 검색 추이는 키가 없어 쓰지 않았고 화면에 "한국인 검색 순위는 준비 중"이라고 밝힌다. 반입 태그는 관세청·검역본부(`import_source`) 기준 — 확실하지 않으면 '주의'(안전한 쪽). 관광청이 권해도 한국 반입 금지면 '불가'로 둔다(싱가포르 박과=육포, 생망고).
- **귀국**: 담은 물건을 '오늘'의 귀국 단계에서 반입 태그와 함께 다시 보여 준다. 면세 한도 요약(`return_facts`)과 관세청·검역본부 링크(`return_links`). 검역본부 사이트는 qia.go.kr → apqa.go.kr로 옮겨졌다.
- **인기 순위**: 공공 여객 통계 키가 없어 순위를 만들지 않는다(지어낸 순위 금지). '순위는 이렇게 정해요' 설명 화면만 먼저 둔다.
- **저장**: '있어요'·장바구니는 DataStore(개인정보 아님, 서버로 보내지 않음). 다른 나라로 여행을 바꾸면 비운다. *(2026-10-02: 여행이 있으면 '있어요'는 그 여행 체크리스트의 `essential.<id>` 체크로 저장 — 9.9. 여행을 바꿔도 지우지 않는다. 장바구니는 나라 코드가 붙은 키라 그대로)*
- **사실 확인 기록**: `docs/research/2026-09-29_M8_essentials_shopping.md`.

## 구현 결정 기록 (M9, 2026-09-29)

- **앱 → 서버는 세 가지뿐**(`cloud/CloudSync`): ① 익명 실패 리포트 → `field_reports`(생성만) ② 찜한 나라마다 기기당 한 번 `favorite_counts/{ISO2}` +1 ③ FCM 토픽 `country_{ISO2}` 구독(찜 + 여행 나라). 여행 날짜·여권·기기 식별자는 보내지 않는다. FCM 토큰도 서버에 저장하지 않는다. 인터넷이 될 때 WorkManager로 한 번에, 실패하면 지수 백오프(최대 5회).
- **리포트 검사 이중화**: 앱(`CloudSyncPlan.toFirestore`)이 Firestore 규칙과 같은 검사를 하고, 맞지 않는 리포트는 보내지 않고 버린다. `ts`는 서버 시각(규칙이 `request.time`과 같은지 확인).
- **Firestore 규칙**(`firebase/firestore.rules`): field_reports 생성만·필드 목록 고정·오류 코드 목록·id 형식, favorite_counts 생성은 1·수정은 +1만·삭제 불가·읽기 공개, 나머지 경로(ops/heartbeat 포함) 앱 접근 불가. 에뮬레이터 테스트 5개.
- **Firestore 위치**: 규칙을 처음 배포할 때 CLI가 데이터베이스를 `nam5`(미국 멀티 리전)로 자동 생성했다. 익명 리포트·찜 수만 담아 지연은 문제없다. 서울(asia-northeast3)로 바꾸려면 비어 있을 때 운영자가 지우고 다시 만든다(되돌릴 수 없는 설정이라 Claude는 지우지 않음).
- **App Check**: 출시 빌드 Play Integrity, 디버그 빌드 디버그 공급자(src/release·src/debug). **강제 모드는 아직 끔** — Play Console 앱 연결·디버그 토큰 등록 뒤 운영자가 콘솔에서 켠다.
- **FCM 알림**: 메시지에는 나라 코드만(`data.country`). 보이는 문구는 앱에 들어 있는 것만 쓰고, 새 안내 내용은 서명된 팩으로만 받는다(원격 문구로 정책을 전하지 않음).
- **배포 파이프라인**(`.github/workflows/deploy-packs.yml`): main의 `packs/src` 변경 → 검증 → 서명(secret 키) → 앱 내장 팩과 같은지 확인(Ed25519 서명은 결정적) → Hosting → Remote Config **버전 키만**(`tools/deploy/ci_deploy.py rc-versions`, 스위치 키는 건드리면 중단) → 바뀐 나라 토픽 알림.
- **ARIA 감시**(`aria-watchdog.yml`): 매일 `ops/heartbeat` 확인, 3일 넘게 멈추면 `stale_banner` 켜고 실패(GitHub 메일), 살아나면 끔.
- **ops/aria**: 12.5 모듈 전부. 감지는 LLM 없이, GET만, 봇 차단이면 우회하지 않고 `manual_check_needed`. 구조 해시는 레시피가 있는 양식만(Visit Japan Web은 로그인 필요·서버가 앱 화면에도 HTTP 404를 돌려줘 제외). 안전한 방향(자동 입력 끄기·배너 켜기)만 자동, 끄기/다시 켜기는 사람 승인.
- **리허설(2026-09-29)**: 실제 공식 양식 4곳 구조 감지 dry-run → 기준 해시 저장 단계까지 정상(조회만). 폰에서 CloudSync 실행 → `favorite_counts/TH` 생성 확인, `field_reports` 공개 읽기 403 확인. PR 생성·Actions 배포는 GitHub 저장소·secrets가 생긴 뒤(사람 작업 C13·C20).

- **비자 온라인 신청 (2026-09-30)**: 5개 나라 중 한국 일반 여권으로 비자가 필요한 곳은 인도네시아뿐. e-VOA는 이제 All Indonesia 입국 신고를 낸 뒤 요약 화면의 'Apply for Visa On Arrival' 버튼 → 카드 결제로 신청한다(추가 입력 칸 없음, 근거 docs/research/2026-09-30_ID_evoa.md). 그래서 새 레시피 없이 팩 `requirements[].apply`(신청 순서·요금·가짜 사이트 경고·출처)로 안내하고, 입력은 기존 ID_ALL_INDONESIA 레시피가 돕는다. 신청 버튼·결제·캡차는 사람이 직접(엔진은 누르지 않음). 결제 화면이 다른 주소면 WebView 밖 브라우저로 열린다.

## 구현 결정 기록 (앱 구조·영상, 0.2.0~0.3.1, 2026-09-29~10-01)

- **하단 탭 4개**(0.2.0, 운영자 요청 2026-09-29): 홈·내 여행·도움·설정. 예전 준비·여행지·지갑 탭을 정리했다 — 나라 고르기는 홈의 사진 카드, 나라별 입국·여행·쇼핑은 나라 화면(`CountryRoute`, 전환 버튼 3개·스와이프 없음), 지갑은 설정 › 내 정보(`WalletRoute`). 자녀 폰 모드는 `입국 QR · 도움` 두 탭. 화면 목록은 PRD 4.1·5장.
- **사진**: 위키미디어 공용 자유 라이선스(CC0·CC BY·CC BY-SA) 사진을 앱에 넣는다(WebP 1080px). Firebase로 보내지 않는다(9.3 텍스트 중심 원칙 그대로). 출처·변경 표시는 `assets/photo_credits.json` → 설정 › 사진·글꼴 출처. 디자인 개편에서 장소와 맞지 않는 `photo_market`(베트남 호이안)을 지워 지금 8장.
- **YouTube 여행 영상**(0.3.0): YouTube Data API v3만 쓴다(긁어 오기 금지). `.github/workflows/videos.yml`이 매일 03:30 KST `tools/videos/fetch_videos.py`로 나라별 `"{나라} 여행"` 한국어 영상을 모은다(안전 검색 엄격, 아동용·쇼츠·방송·나라 이름 없는 제목 제외, 최대 50개) → 팩 키로 Ed25519 서명 → Firestore `videos/{ISO2}`(규칙: get만, 목록·쓰기 불가). 앱은 서명 확인, 30일 지난 목록 거부(API 정책), id·썸네일 주소(`i.ytimg.com`만) 검사, 썸네일은 메모리에서만. 정렬은 API 값 그대로(조회수·최신·구독자 — 새 점수 없음). secret `YOUTUBE_API_KEY`가 없으면 경고만. 근거 `docs/research/2026-09-30_youtube_videos.md`.
- **뒤로 버튼·영상 검색**(0.3.1): 탭 첫 화면이 아니면 `AppScreen` 제목 왼쪽에 '이전 화면으로'. 영상은 제목·채널 검색(띄어쓰기·대소문자 무시).

## 구현 결정 기록 (디자인 개편, 2026-10-01)

브랜치 `claude/design-refresh`. 스펙 `docs/design/DESIGN_SPEC_2026-10-01.md`(v2.1), 과정 기록 `docs/design/STAGE0_REPORT.md`·`STAGE1_BUNDLE_RESULTS.md`·`STAGE2_REPORT.md`·`FIX_A_REPORT.md`·`FIX_C_REPORT.md`, 재검토 `REREVIEW_SUMMARY.md`. 전문가 5인 평균(visual·clarity·accessibility·consistency·delight) 5.7·5.9·6.8·5.5·4.0 → 최종 8.0·7.7·8.2·7.6·7.1(목표 7.5·delight 7.0 달성). 팩 스키마·서명·ARIA 설정은 바꾸지 않았다.

### 설계 결정 D1~D21 (스펙 1.3절)
- **D1 글꼴**: Pretendard Std 1.3.9(SIL OFL 1.1) 번들 — **운영자 승인 2026-10-01**. 공식 배포본 정적 OTF 4굵기를 고치지 않고 넣어 이름(Reserved Font Name)을 그대로 쓴다(약 1.2MB). 라이선스 `app/src/main/assets/licenses/pretendard_std_OFL.txt`, 설정 › 사진·글꼴 출처에서 전문 보기. 현지어 글자는 시스템 글꼴. (QUESTIONS A2 변경)
- **D2 카드 경계**: 흰 정보 카드 = 그림자 2dp + (운영자 결정 6번) 아주 옅은 1dp `LineSoft` 테두리. 그림자 진하기는 화면에 보이는 Ink 8%/12% — Android가 그림자 색 알파에 테마 알파(0.039/0.19)를 한 번 더 곱해서, 그 값으로 나눠 넣는다(`Tokens.ShadowAmbient/Spot`). 상태 카드 = 연한 바탕 + 왼쪽 4dp 막대, 사진 카드 = 없음, 조작 요소 경계 = `LineStrong`.
- **D3 모서리**: 8 태그 · 12 입력칸·배지·썸네일 · 16 버튼·타일 · 20 카드 · 28 히어로·대화상자. 알약은 칩·인디케이터만. PRD 5장 문구를 바꿈(운영자 결정 4번).
- **D4 1열 전환**: 쉬운 모드는 항상 1열, 기본 모드는 2열 한 칸 폭 ÷ 글자 배율이 150dp 미만이면 1열. 판정은 `ui/components/Layout.kt` 한 곳(`layoutInfoOf`, 수정 A R5). 큰 글자 배치(배지·끝 요소를 윗줄로)는 (창 폭 − 40) ÷ 글자 배율 < 272 — 393dp 창은 130%, 360dp 창은 약 118%부터. 큰 글자에서 글을 지우지 않고 배치만 바꾼다.
- **D5 홈 압축**: 출국 순서·귀국 전 확인은 홈에 두되 짧게(번호 단계, 접힌 요약).
- **D6 도움 순서**: 스펙은 대표 긴급 번호를 문장 카드 위에 두었으나, **운영자 결정 5번으로 PRD 5.11 원래 순서(문장 카드 먼저)**.
- **D7 선택 표시**: 칩·세그먼트·작은 타일 = Accent 채움 + 흰 글자 + 체크. 큰 카드·폭 전체 행 = AccentSoft + 2dp Accent + 채운 체크 원(`SelectableCard`, 수정 A R2). AccentSoft 채움은 '선택됨'에만.
- **D8 지우기 확인**: 여권·예약 서류·여행·내 정보 비우기·보여 주기 서류·같이 가는 사람 삭제는 확인 대화상자(보안 화면은 `secure`). 귀국 단계 '여권 정보를 지울까요?' 카드는 그 자체가 확인이라 대화상자 없음.
- **D9 입국 카드 확인**: 빈칸이 있으면 주 버튼 비활성 + '빈칸 N개 남았어요' + '첫 빈칸으로 가기'.
- **D10 도착 단계**: 체크 저장 없이 번호 단계 + '다 했어요'. 체크 저장은 후속 과제.
- **D11 팩 스키마 그대로**: 아이콘은 앱 `IconKeys.kt`가 기존 ID로 고른다. 숫자 타일은 이미 구조화된 필드(`stay_limit_days`, 짧은 `fee_ko`, `power.*`, 전화번호)에서만 — 800달러 같은 정책 값은 앱에 적지 않는다. 입국 카드 제출 기간 타일은 그리지 않는다: 태국 팩 문장 모순은 외교부 0404 원문(2026-10-01 확인)으로 팩 불릿을 고쳐 해소(TH 2026.10.01-1, 커밋 8bfed6d, 배포는 main 머지 때). 타일은 팩 lint(스펙 부록 A) 뒤 되살린다.
- **D12 아이콘**: `Icons.Outlined.*`(방향 있는 것은 AutoMirrored). Filled는 켜짐·선택 상태만.
- **D13** 도움 탭 아이콘 SupportAgent 유지(Sos는 긴급 번호에만). **D14** 귀국 단계 아이콘 Cottage.
- **D15 준비 중 기능**: 화면 맨 아래 '곧 추가돼요' 한 줄 묶음, 항목은 누를 수 없음(TalkBack '사용 안 함').
- **D16 나라 카드 TalkBack**: 이름은 '○○ 안내 열기' 그대로, 칩 정보는 stateDescription.
- **D17 번호 문자열**: `N. ` 접두를 문자열에서 지우고 번호 원(StepList)으로 그린다.
- **D18 어두운 바탕 위 색**: Accent·Navy·AccentDeep·BrandBlue·사진 위에는 흰색·흰 85/80%·Gold(Navy·AccentDeep 위만)만. 금지 쌍은 `OnDarkPairsTest`가 막는다.
- **D19 쉬운 모드 28sp 초과**: 현지인에게 보여 주는 글자·히어로 이름·큰 숫자(displayLarge 56 · displayMedium 36 · displaySmall 32 · stat 34 · localLarge 56 · localMedium 32)는 예외 — **운영자 승인 2026-10-01, PRD 3.2**. 쉬운 모드 하단 탭은 최소 18sp, 모자라면 2줄.
- **D20 여행 날짜**: 글자 입력(YYYY-MM-DD) 유지 + 달력 아이콘·요일 안내. M3 DatePicker는 달 목록이 가로 스와이프라 쓰지 않는다.
- **D21 누를 수 있는지 구분**: 누를 수 있는 보조(tonal) 버튼은 항상 1dp 테두리, 누를 수 없는 폭 전체 띠에는 AccentSoft 채움 금지(`NoticeBanner` = 흰 바탕 + 4dp 막대). 누를 수 없는 칩은 채움·테두리 없는 `InfoChip`(수정 A R1).

### 구현하며 정한 것
- **공용 부품**(`app/src/main/java/com/readyport/ui/components/`): CardNewsCard·StatTile/FactGrid·IconTile·ListRow/ListGroup·KeyValueRow·SelectChip/SelectableCard·NoticeBanner/SecurityBanner·SourceFooter/SourceList·ReturnCheckCard·EmergencyCallTile·FitText 등. 화면에만 있던 임시 부품은 0개(수정 C). 출처 줄은 ID를 받지 않고 이름을 못 찾으면 '공식 안내', 기관별로 묶고 날짜는 같으면 한 번, `최종 확인`과 날짜는 한 줄(NBSP·WORD JOINER).
- **한국어 줄바꿈**: 글자 스타일 언어를 ko-KR로 고정(기기 언어가 영어여도 어절 단위). API 33 미만은 단순 줄바꿈 + `KoText`(보이는 글자만 낱말 묶기, TalkBack·테스트는 원문).
- **사진**: 백그라운드 디코드 + 앱 전체 캐시(`PhotoCache`). 평균 밝기가 낮은 사진만 그릴 때 밝힌다(파일은 그대로, 크레딧에 변경 표시 — 운영자 결정 11번). 글자는 사진 위 스크림 영역 안에만(흰 글자 대비 5.74:1 이상).
- **검사**: `TokenContrastTest`(반투명 색은 합성 후 계산), `OnDarkPairsTest`, `SourceNamesTest`, A11yAudit(기본·쉬운·200%, API 36·31 — 쉬운 모드 누르는 요소 56dp, 화면 잘림·한 음절 줄 실패), 갤러리 41화면 캡처. 수정 C 기준 단위 테스트 317개 실패 0, lint 오류 0(경고 11 = 기준선).
- 다크 모드는 넣지 않는다(`forceDarkAllowed=false`). 새 네트워크 라이브러리 없음.

### 운영자 결정 (2026-10-01, 원문 `docs/design/OWNER_DECISIONS.md` '운영자 결정 기록')

| # | 항목 | 결정 |
|---|---|---|
| 1 | Play 업로드 | 보류 — 더 다듬은 뒤 다시 확인 |
| 2 | '원어민 검수 전' 배지 | 화면에서 제거. 원어민 검수(PRD 5.2 '번역은 원어민 검수 후 반영', QUESTIONS C10·C16·C17)는 출시 전 할 일로 남김 |
| 3 | 쉬운 모드 28sp 초과(D19) | 예외 승인 → PRD 3.2 |
| 4 | 모서리 체계(D3) | 새 체계 유지 → PRD 5장 |
| 5 | 도움 탭 순서(D6) | 문장 카드 먼저: 나라 칩 → 선택 문장 카드 → 문장 타일 → 긴급 번호 → 대사관 → 이럴 땐 이렇게 → 어느 나라에서나 |
| 6 | 흰 카드 경계(D2) | 아주 옅은 1dp `LineSoft` 테두리 추가(그림자와 함께) |
| 7 | 큰 글자 배치로 바뀌는 시점 | 지금 그대로(창 폭 기준) |
| 8 | 쉬운 모드 홈 길이 | 지금 그대로(쉬운 모드에서만 여행 준비 카드 접음) |
| 9 | 1열 홈 신뢰 표시 위치 | 지금 그대로(나라 사진 목록 아래) |
| 10 | 귀국 전 확인 카드 | 전체 문장은 내 여행 '귀국' 단계에만. 홈·나라 쇼핑·쇼핑 리스트는 한 줄 요약 + 펼치기 |
| 11 | 어두운 사진 밝기 보정 | 지금 그대로 |
| 12 | 쉬운 말 문구(`값 복사해서 넣기`·`가족 폰으로 보내기`·`여권 아래 두 줄 확인 완료` 등) | 지금 그대로 |
| 13 | 쇼핑 '담았어요 ✓' | '담았어요'로(✓ 제거, 테스트 수정) |
| 14 | 여행 요약 타일 | 지운 채로 |
| 15 | 화면별 작은 기본값 14개 | 일괄 승인(인도네시아 1단계 입국 신고 → 2단계 비자 신청, 가는 곳 카드에는 현지어 주소 빼고 기사님 카드에만, IconTile 셰브론 오른쪽 위 등) |
| 16 | 인기 순위 문구 15개(`rankings_*`) | 보존 |
| 17 | 스펙·PRD 문서 갱신 | 이번 다듬기에서 함께(이 기록·PRD 3.2·5장·5.11·8.4·QUESTIONS) |
| + | Play 스크린숏 | 나라를 섞고 1번 캡션을 '칸은 앱이 채워요 / 제출만 직접'으로 |

- 결정이 아닌 남은 확인: 실기기(S10 5G)·에뮬레이터에서 글자 200%, 쉬운 모드, TalkBack 한 바퀴, 여러 줄 태국어 전체 화면, 대표 긴급 번호 주황 타일, 사진 디코드 직후 한 프레임.

## 구현 결정 기록 (나라 추가 c2 — 필리핀·베트남, 2026-10-02)

- 필리핀 eTravel(`PH_ETRAVEL`): 이메일 계정 + 6자리 확인 코드 뒤에 칸이 있어 레시피 없이 값 복사(수동) 모드(일본 VJW와 같음). `window_days_including_arrival` 3 = 공식 '도착 72시간 안'에 늘 들어가는 날짜 단위 기간.
- 베트남 전자비자(`requirements[].apply.form = VN_EVISA`): 팩 양식도 레시피도 아닌 신청 → 나라 화면 신청 카드는 공식 사이트(`official_url`)를 여는 안내 카드(`siteOnly`). 양식이 신고 안내 동의 창 뒤에 있고 사진 업로드·확인 글자가 있어 구조를 읽지 않았다(동의 금지).
- 비자 없이 들어가는 나라(`visa = not_required`)의 신청은 비자 카드 비용 타일로 올리지 않고 버튼도 보조 — 짧은 여행에도 비자가 필요한 것처럼 읽히지 않게.
- 베트남 이민국이 권하는 도착 전 입국 정보(prearrival.immigration.gov.vn)는 의무가 아니고 확인 글자 창이 먼저 떠서 양식 카드로 만들지 않고 '들어갈 때' 문장으로만 안내.
- c1(대만·중국)과 c2(필리핀·베트남) 합치기: 나라 목록은 기존 5개(TH JP SG MY ID) 자리를 그대로 두고 TW·CN·PH·VN을 뒤에 붙였다(이미 쓰던 사람의 홈 순서가 바뀌지 않게). 두 가지가 각자 다른 내용으로 index `2026.10.02-1`을 썼으므로 합친 index는 `2026.10.02-2`(받아 둔 쪽이 어느 것이든 새 목록을 다시 받게).

## 구현 결정 기록 (여러 여행·여행 체크리스트, 2026-10-02)

- 운영자 요청(PRD 5.16): 여권 만료 기간부터 귀국 뒤까지 체크리스트 + 같은 나라 여러 번 = 다른 여행.
- 여행 장부(9.9): 여행 목록 + 여행별 체크를 DataStore 한 키에. Room을 새로 쓰지 않았다(작은 JSON, 원자적 편집, 기존 백업 제외 그대로).
- 체크리스트 틀은 서명된 색인에(10.4b). 앱에는 문구 틀이 없고 팩을 고치면 함께 바뀐다. 사실은 팩 값에서만.
- **꼭 챙길 물건은 체크리스트 안으로**: 7개 규칙이 체크리스트 항목(`essential.<id>`)이 되고, 꼭 챙길 물건 화면은 그 항목들을 자세히 보는 화면(같은 체크)으로 남았다 — 두 목록이 따로 체크되지 않는다. 여행이 없을 때만 예전 설정 체크.
- 여권 남은 기간 기준(공식 페이지를 열어 확인, last_verified 2026-10-02): 태국 6개월(0404 입국 거부 사유) · 싱가포르 6개월(ICA) · 말레이시아 6개월(0404) · 인도네시아 6개월(0404 도착비자 구비서류) · 대만 6개월(대만 외교부 영사사무국, 입국일 기준) · 필리핀 6개월(주필리핀 대사관, 입국일 현재) · 베트남 6개월(주베트남 대사관 무사증 안내) — 모두 입국일 기준. 일본·중국은 공식 안내에 기준이 없어 `null`.
- 중국 「국무원 출입국관리규정」(2026-09-15 시행): 0404 안전공지(2026-09-24, 주광저우 총영사관)를 출처로 새 섹션 `rules`(알아 둘 규정) — '들어갈 때' 섹션의 출처(0404 나라 페이지)와 다른 공지라 한 섹션 한 출처 규칙을 지키려고 따로 둔다. 원문(국무원령 제841호 제3조 2항)은 '신원·신청 사유를 확인할 때 문서·자료·전자데이터 제시·제공 요구, 협조 의무'이고 이민관리기관에 출입국 변방검사기관이 들어간다(제18조).
- 입국 카드 알림은 작업 하나라 가장 먼저 떠나는 여행에 맞춘다(새 권한·새 알림 없음).
- 체크리스트 화면은 여권 값을 보이지 않는다(결과 문장만) → FLAG_SECURE 대상 아님.
