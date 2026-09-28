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
- `link.type`: `affiliate`(물건·여행 서비스, '제휴' 표시) / `official_info`(보험·환전·카드, 수수료 없음)
- 조건: 목적지 기후·플러그·전압, 기간, 아이 동반, 계절

### 10.5 쇼핑 항목
```json
{"id": "dried_mango", "category": "food",
 "names": {"ko": "말린 망고", "local": "มะม่วงอบแห้ง", "en": "Dried mango"},
 "where_ko": "대형마트·공항 면세점", "why_ko": "선물용으로 많이 사요",
 "import_status": "allowed", "import_note_ko": "",
 "popularity": {"source": "naver_datalab", "period": "2026-09", "relative": null}}
```
- `import_status`: `allowed` / `caution` / `prohibited` — 관세청·농림축산검역본부 기준. 예: 생과일은 `prohibited`.
