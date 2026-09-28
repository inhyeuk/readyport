# Google Play 데이터 보안(Data safety) 기재안 — 초안

> 2026-09-29 작성. **법률 검토 전 초안**. 앱 코드(버전 0.1.0, versionCode 1)와 SDK 공식 공개 문서로 작성했다.
> Play Console 설문에 옮길 때 운영자가 최종 확인한다. `[재확인]`은 제출 직전 다시 볼 것.

## 1. 앱이 기기 밖으로 보내는 것 (코드 기준 전부)

| 보내는 곳 | 내용 | 코드 | 개인 식별 |
|---|---|---|---|
| Firebase Hosting (readyport-app.web.app) | 서명된 국가 팩 **내려받기만** | `pack/PackSources.kt` | 없음(요청만) |
| Firebase Remote Config | 팩 버전·스위치 **받기** | `pack/PackSources.kt` | SDK 기본 수집(아래 3절) |
| Firestore `field_reports` | 자동 입력 실패 리포트: 양식 id·레시피 버전·단계 id·오류 코드·앱 버전·서버 시각·사이트 버전 | `cloud/CloudSync.kt` | 없음 — 이름·여권·기기 id 없음 |
| Firestore `favorite_counts/{나라}` | 찜한 나라마다 기기당 한 번 +1 | `cloud/CloudSync.kt` | 없음 — 숫자만 올림 |
| FCM 토픽 `country_{나라}` | 찜하거나 여행 가는 나라의 알림 구독 | `cloud/CloudSync.kt` | Firebase 설치 ID(SDK) |
| 각국 정부 공식 사이트 (WebView) | **사용자가 확인한 뒤** 공식 양식에 여권·여행 정보 입력. 제출 버튼은 사용자가 누른다 | `ui/form/AutofillScreen.kt` | 있음 — 사용자 → 정부 사이트 |

**기기 안에만 두는 것**(수집 아님): 여권 정보·예약 서류·입국 QR 사진(Android Keystore AES-256-GCM, 백업 제외), 여행 날짜·나라, 가는 곳 주소, 준비물 체크, 쇼핑 장바구니, 설정. 카메라 이미지는 기기 안에서 글자만 읽고 바로 지운다.

## 2. 설문 답안 (초안)

- **데이터를 수집하거나 공유하나요?** 예 (아래 항목)
- **전송 중 암호화?** 예 (모두 HTTPS)
- **삭제 요청 방법?** 앱의 데이터는 기기 안에 있고 '지갑 › 모두 지우기'·앱 삭제로 지워진다. 서버로 보낸 항목(실패 리포트·찜 수)은 사람과 연결되지 않아 개별 삭제 대상을 찾을 수 없다고 안내 `[법률 검토]`.

| Play 분류 | 수집 | 공유 | 선택/필수 | 목적 | 근거 |
|---|---|---|---|---|---|
| 앱 정보 및 성능 › 진단 | 예 | 아니요 | 선택(자동 입력을 쓸 때만) | 앱 기능, 분석 | 실패 리포트 · ML Kit 진단(성능·오류 코드) |
| 앱 활동 › 앱 상호작용 | 예 | 아니요 | 선택(찜할 때만) | 앱 기능(인기 순위), 분석 | 찜 수 +1 |
| 기기 또는 기타 ID | 예 | 아니요 | 필수(SDK 기본) | 앱 기능(알림), 분석 | Firebase 설치 ID, ML Kit 설치별 식별자 |
| 개인 정보(이름·여권 번호 등) | **보수적으로 '예' 검토** `[법률 검토]` | 아니요 | 선택 | 앱 기능 | WebView로 사용자가 정부 사이트에 입력·제출. 운영자 서버는 받지 않음. PRD 8.1: "해석이 애매하므로 보수적으로 기재" |
| 사진·동영상 | 아니요 | — | — | — | 기기 안 처리 후 삭제, 입국 QR 사진은 기기 안 암호화 보관 |
| 위치 | 아니요 `[재확인]` | — | — | — | GPS 권한 없음. Remote Config가 국가 코드·시간대를 수집(아래) — 대략적 위치로 볼지 확인 |

## 3. SDK가 스스로 수집하는 것 (공식 공개 문서, 2026-09-29 확인)

- **ML Kit (글자 인식 v2, 번들 모델)** — developers.google.com/ml-kit/android-data-disclosure: 기기 정보(제조사·모델·OS), 앱 정보(패키지·버전), 성능 지표, API 설정(이미지 형식·해상도), "사용자·기기를 고유하게 식별하려는 것이 아닌" 설치별 식별자, 이벤트·오류 코드. 목적 "진단 및 사용 분석". 제3자 전달 없음, HTTPS.
- **Firebase** — firebase.google.com/docs/android/play-data-disclosure:
  - Cloud Messaging: 앱 버전, Firebase 사용자 에이전트 → 메시지 전송
  - Remote Config: 국가 코드·언어 코드·시간대, 플랫폼·OS 버전, 앱 ID·패키지명, SDK 버전 → 앱 동작 변경
  - Firestore: Firebase 사용자 에이전트
  - App Check: Firebase 사용자 에이전트 → 증명 확인
  - Installations: Firebase 설치 ID(FID), 사용자 에이전트
- **넣지 않은 것**: Firebase Analytics, Crashlytics, 광고 SDK, 광고 ID(AD_ID 권한 없음 — 병합 매니페스트 확인).

## 4. 권한 (출시 빌드 병합 매니페스트)

CAMERA(여권·서류 촬영), POST_NOTIFICATIONS(입국 카드 기간·정책 알림), USE_BIOMETRIC/USE_FINGERPRINT(지갑 잠금), INTERNET·ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED·WAKE_LOCK·FOREGROUND_SERVICE(WorkManager), c2dm RECEIVE(FCM). 위치·연락처·저장소·전체 앱 목록 권한 없음(교통 앱은 `<queries>`에 패키지만 선언).
