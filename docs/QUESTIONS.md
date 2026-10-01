# 운영자 질문 목록

> 지시서 0장 2번: 구현 전에 `[확인 필요]` 항목, 라이브러리 버전, 사람이 해야 하는 일을 모아 운영자에게 보여 준다.
> 답이 정해지면 "답변" 칸을 채우고, 해당 문서·코드에서 `[확인 필요]`를 지운다.
> 상태: ⏳ 대기 · ✅ 확정 · 🔎 Claude가 공식 출처 조사 후 확인 요청 예정

## A. 결정이 필요한 것 (M1 착수 전)

| # | 질문 | 선택지 / 제안 | 막히는 단계 | 답변 |
|---|---|---|---|---|
| A1 | 패키지명(applicationId). Play 출시 후 바꿀 수 없음 | 제안 `com.readyport.app` (현재 임시 적용) | M1 | ✅ `com.readyport` |
| A2 | 앱 글꼴 | ① 시스템 글꼴(용량 0, 기기 확대 설정과 잘 맞음) ② IBM Plex Sans KR 번들(OFL 표기, 약 +2~4MB) | M1 | ✅ ① 시스템 글꼴 → **2026-10-01 바꿈: Pretendard Std 1.3.9 번들**(디자인 개편 D1, SIL OFL 1.1, 약 1.2MB, 라이선스 `app/src/main/assets/licenses/pretendard_std_OFL.txt`) — 아래 D1 |
| A3 | minSdk | 제안 26 (Android 8.0, 적응형 아이콘 기준. 국내 점유율상 손실 거의 없음) | M1 | ✅ 26 |
| A4 | 라이브러리 버전 | M0은 이 PC 캐시에서 검증된 조합(AGP 8.13.2, Kotlin 2.0.21, Compose BOM 2024.12.01)으로 빌드. M1에서 Hilt·Room·Navigation을 넣을 때 **최신 안정판으로 일괄 업그레이드**할지 | M1 | ✅ 최신 안정판으로 업그레이드 |
| A5 | 네트워크 SDK 승인(작업 규칙 9) | Firebase BoM 중 Remote Config·FCM·Crashlytics·App Check·Firestore는 명세상 필요. **Analytics는 개인정보 약속과 긴장 관계**라 빼거나(권장) 광고 ID·자동 화면 수집을 끈 채로 넣을지 | M3/M9 | ✅ Analytics 넣지 않음 |
| A6 | GitHub 저장소 | 공개/비공개, 계정·저장소 이름. 명세는 공개 기본(브랜치 보호·신뢰 증명) | M0 푸시, M9 | ✅ 공개 저장소. 생성·푸시는 C13(운영자 로그인 필요) |
| A7 | 기기 내 LLM(Gemini Nano, ML Kit GenAI) 보조 추출 | 1차에서 제외하고 규칙 기반 + 수동 확인만(권장) / 지원 기기에서 선택 사용 | M2 | ✅ 1차 제외 |
| A8 | 클립보드 자동 지우기 기본값 | 제안: 켬, 60초 | M4 | ✅ 켬, 60초 |

## B. 사실 확인 — Claude가 공식 출처로 조사해 확인 요청할 것 🔎

지어내지 않는다(작업 규칙 6). 해당 마일스톤 직전에 공식 출처 URL과 확인일을 붙여 제시하고, 운영자 확인 후 반영한다.

| # | 항목 | 쓰이는 곳 | 마일스톤 |
|---|---|---|---|
| B1 | ✅ 5개 모두(2026-09-28): TDAC(도착일 포함 3일), MDAC(imigresen-online.imi.gov.my, 3일 전부터, **요금 공식 표기 없음**), SGAC(eservices.ica.gov.sg, 무료, 도착일 포함 3일), All Indonesia(allindonesia.imigrasi.go.id, 무료, 도착 3일 전부터), Visit Japan Web(vjw.digital.go.jp, 무료, 의무 아님, 계정 필요). 근거: docs/research/2026-09-28_M5_countries.md | 레시피·팩 | M4·M5 |
| B2 | ✅ TDAC(2026-09-28 실기기 리허설, 제출 안 함): 입력 칸 종류 전부 확인. 날짜 칸은 yyyy/mm/dd 직접 입력, 전화 나라 번호는 글자 칸(+는 사이트 고정), 성별·오는 방법은 라디오, 목적·숙소 종류는 선택 목록(선택지 글자 확인: HOLIDAY/BUSINESS…, HOTEL/GUEST HOUSE…), 국적·생년월일·나라·주는 자동 완성. 앱은 글자 칸 13개를 채우고, 고르는 칸은 '관광 → HOLIDAY'처럼 고를 글자를 말풍선으로 보여 줌 | 레시피 | M4 |
| B3 | 외교부 입국허가요건 공공데이터 API 주소·형식 — M3 태국 팩은 외교부 해외안전여행(0404.go.kr) 페이지로 확인함. API는 ARIA 감지 구현(M9) 때 조사 | 국가 팩 `sources`, ARIA 감지 | M9 |
| B4 | ✅ 5개국 모두 긴급 전화·대사관·여권 분실 순서를 0404·각 대사관 누리집에서 확인해 팩에 반영(2026-09-28). 일본 대사관 대표번호는 0404와 대사관 누리집이 달라 대사관 누리집 값을 씀 | 도움 탭 | M3·M5 |
| B5 | 교통 앱 패키지명·공식 딥링크 규격(Grab·Bolt·GO·Uber 등), 동남아 Uber 여부 | 이동하기 | M7 |
| B6 | ✅ 타깃 API: 2026-08-31부터 신규·업데이트 36 이상(연장 시 11-01), 확인 2026-09-28 developer.android.com/google/play/requirements/target-sdk. 접근성 API 정책은 M10에서 | 빌드 설정, 정책 | M1·M10 |
| B7 | Firebase Spark 한도(Hosting·Firestore), Cloud Storage Blaze 전환 여부 | 아키텍처 | M3·M9 |
| B8 | ✅ ML Kit이 보내는 것: 기기 정보(제조사·모델·OS), 앱 패키지·버전, 성능 지표, 설치별 식별자, 이미지 형식·해상도 설정, 이벤트·오류 코드. SDK에서 끌 수 없음(확인 2026-09-28 developers.google.com/ml-kit/android-data-disclosure). 여권 등록 화면 고지 문구에 반영 | 개인정보 안내 문구 | M2 |
| B9 | 관세청·검역본부 면세 한도·반입 금지 품목 공식 링크 | 쇼핑 리스트, 귀국 단계 | M8 |
| B10 | 인천공항·한국공항공사 여객 통계 API 형식 | 인기 순위 | M8·M9 |

## C. 운영자가 직접 해야 하는 일 (지시서 14장)

| # | 할 일 | 필요한 시점 | 상태 |
|---|---|---|---|
| C1 | Firebase 프로젝트 생성(Spark), `google-services.json` 전달, 서비스 계정(Remote Config 전용) 발급 | M3 | ✅ 2026-09-28 Hosting(`readyport-app.web.app/packs/`)·Remote Config 템플릿 배포까지 완료. 프로젝트 `readyport-app`(DoingWell과 같은 Google 계정, Spark), Android 앱 2개(`com.readyport`, `com.readyport.debug`) 등록, `app/google-services.json` 저장. 서비스 계정은 M9 |
| C2 | ✅ 키 `rp-2026-1` 생성(2026-09-28, `tools/packs/keygen.py`). 비밀키는 이 PC `C:\Users\inhye\.readyport\keys\pack_signing_rp-2026-1.pem`에만 있음 → **운영자가 안전한 곳에 백업**하고, GitHub 저장소가 생기면 그 내용을 Actions secret `READYPORT_PACK_KEY`로 등록 | M3 | 백업 ⏳ |
| C3 | 공공데이터포털(외교부·인천공항·한국공항공사) API 키 발급 | M3·M8 | ⏳ |
| C4 | 네이버 데이터랩(API HUB 종량제) 키 발급·비용 확인 | M8 | ⏳ |
| C5 | 제휴 프로그램 선택·가입, 앱 게재 허용과 표기 문구 약관 확인 | M8 | ⏳ |
| C6 | Play Console 계정·앱 서명 키·스토어 등록 | M10 | ⏳ |
| C7 | GitHub fine-grained 토큰(이 저장소 전용), Actions secrets 등록 | M9 | ⏳ |
| C8 | 법률 검토: 개인정보처리방침, 보험·금융 링크, 가족 모드 동의, 정부 서비스 관련 스토어 정책 | M10 | ⏳ |
| C9 | 상표 검색(KIPRIS), Play 스토어 동일 이름 확인 | 출시 전 | ⏳ |
| C10 | 현지어 문장·양식 라벨 원어민 검수(태국어·말레이어·인도네시아어·일본어), 긴급 연락처 검수. 태국어 7문장이 `packs/src/TH/pack.json`에 `reviewed:false`로 들어가 있음. ※ 2026-10-01 운영자 결정으로 화면의 '원어민 검수 전' 배지는 뺐지만, 검수는 **출시 전 할 일로 그대로**(PRD 5.2 '번역은 원어민 검수 후 반영') | 출시 전 | ⏳ |
| C11 | 실기기 테스트 — 보유 S10 5G(Android 12)는 NFC·카메라 가능. **Android 13+ 테마 아이콘 확인용 기기 별도 필요** | M2·M6·M10 | ⏳ |
| C12 | 사용자 테스트: 60대 무경험자 5명, 중학생 5명 | M10 | ⏳ |
| C15 | **Visit Japan Web 자동 입력 리허설**: 계정 로그인이 필요해 Claude가 할 수 없음(계정 만들기·로그인 금지). 운영자가 폰에서 직접 로그인한 상태로 열어 주면 구조만 읽어 레시피를 만든다. 그 전까지 일본은 안내 + 수동 모드 | M5 이후 | ⏳ |
| C16 | 말레이어·인도네시아어·일본어 문장 7개씩 원어민 검수 (`reviewed:false`). ※ 화면 배지는 2026-10-01에 뺐지만 검수는 출시 전 할 일(C10과 같음) | 출시 전 | ⏳ |
| C17 | 쇼핑 리스트 현지어 이름(태국어·말레이어·인도네시아어·일본어) 원어민 검수 — Claude가 옮긴 이름이라 '직원에게 보여주기' 전에 확인 필요. ※ 화면 배지 없이도 출시 전 할 일(C10과 같음) | 출시 전 | ⏳ |
| C18 | 제휴 프로그램 가입·약관 확인(앱 게재 허용, 표기 문구). 가입 전까지 '사러 가기' 링크 없음 | 제휴 시작 전 | ⏳ |
| C19 | 축산물 성분 과자(카야 잼·솔티드 에그·일본 과자 상자) 반입 기준을 검역본부에 확인 — 지금은 '주의'로 표시 | 출시 전 | ⏳ |
| C20 | ✅ 2026-09-29 GitHub secrets `PACK_SIGNING_KEY_PEM`·`FIREBASE_SERVICE_ACCOUNT`(서비스 계정 github-deploy: Hosting·Remote Config·FCM 관리자, Datastore 뷰어, 서비스 사용량 소비자) 등록. deploy-packs 수동 실행 #2 전 단계 성공. ARIA PC용 별도 계정(Remote Config·Firestore만)은 ARIA 연결 때 | M9 | ✅ |
| C21 | App Check: Play Console 앱 연결 → Play Integrity 등록, 디버그 토큰 등록(logcat 'DebugAppCheckProvider'), 확인 후 Firestore 강제 모드 켜기 | 출시 전 | ⏳ |
| C22 | Firestore 위치가 nam5(미국)로 자동 생성됨. 서울로 바꾸려면 비어 있을 때 지우고 asia-northeast3로 다시 만들기(선택) | 선택 | ⏳ |
| C23 | ARIA 설정: 공공데이터포털 외교부 입국허가요건 API 키·요청 주소 확인(`MOFA_API_URL` [재확인]), 감시할 공지 페이지 목록(`NOTICE_URLS`), 텔레그램 봇에 chat_id 허용 목록 연결, `CLAUDE_BIN`은 claude.exe. **ARIA가 하트비트를 남기기 시작하면 GitHub Actions의 `aria-watchdog`를 다시 켜기**(2026-09-29 꺼 둠 — 켜 두면 ARIA 없이 매일 실패 메일과 앱 '점검 지연' 배너가 뜸) | ARIA 연결 시 | ⏳ |
| C24 | 개발 폰에서 확인하느라 `favorite_counts/TH`가 1로 만들어짐(2026-09-29). 출시 전 0부터 세려면 콘솔에서 지우기(선택) | 출시 전 | ⏳ |
| C14 | ✅ TDAC 실기기 리허설 완료(2026-09-28): 폰 Chrome에서 사람 확인이 저절로 통과됨. DevTools로 화면 구조와 서버가 준 선택지 목록만 읽음. 입력·제출 없음. 여는 버튼('Arrival Card')만 눌렀음 | M4 | ✅ |
| C13 | ✅ 2026-09-29 공개 저장소 https://github.com/inhyeuk/readyport 생성·푸시. main 보호: PR 필수, 관리자 포함 우회 금지, 강제 푸시·삭제 금지. 승인 수는 0(혼자 운영이라 자기 PR을 승인할 수 없음 — 사람 검토는 머지 버튼을 누르는 것으로) | M0 | ✅ |
| C25 | 디자인 개편 Play 업로드: 운영자 결정 1번(2026-10-01)으로 **보류** — 더 다듬은 뒤 다시 확인. 올릴 때 스크린숏은 나라를 섞고 1번 캡션을 '칸은 앱이 채워요 / 제출만 직접'으로(`docs/play/store/`, `STORE_LISTING_KO.md`) | 다음 업로드 때 | ⏳ |

## D. 디자인 개편 결정 (2026-10-01)

근거: `docs/design/DESIGN_SPEC_2026-10-01.md` 1.3절(D1~D21), 운영자 답변 `docs/design/OWNER_DECISIONS.md` '운영자 결정 기록'. 17개 전체 요약은 `docs/ARCHITECTURE.md` 디자인 개편 기록.

| # (스펙 결정 번호) | 질문 | 반영한 곳 | 답변 |
|---|---|---|---|
| D1 | 한글 글꼴: Pretendard 번들 / 시스템 글꼴 | PRD 8.4, A2 | ✅ 2026-10-01 Pretendard Std 1.3.9(SIL OFL 1.1) 번들 |
| D2 | 흰 카드 경계: 그림자 그대로 / 더 옅게 / 1dp 테두리 함께 | PRD 5장 공통·8.4 | ✅ 2026-10-01 아주 옅은 1dp `LineSoft` 테두리 + 그림자 |
| D3 | 모서리: 새 체계 / PRD 5장 '14~20dp' 유지 | PRD 5장 공통 | ✅ 2026-10-01 새 체계 — 태그 8 · 입력칸·배지 12 · 버튼·타일 16 · 카드 20 · 히어로·대화상자 28 |
| D6 | 도움 탭 순서: 대표 긴급 번호 먼저 / 문장 카드 먼저(PRD 5.11) | PRD 5.11 | ✅ 2026-10-01 문장 카드 먼저: 나라 칩 → 선택 문장 카드 → 문장 타일 → 긴급 번호 → 대사관 → 이럴 땐 이렇게 → 어느 나라에서나 |
| D19 | 쉬운 모드 28sp 넘는 글자: 예외 승인 / 28sp로 낮춤 | PRD 3.2·8.4 | ✅ 2026-10-01 예외 승인 — displayLarge 56 · displayMedium 36 · displaySmall 32 · stat 34 · localLarge 56 · localMedium 32 |
| — | '원어민 검수 전' 배지 | PRD 5.2·5.11, C10·C16·C17 | ✅ 2026-10-01 화면에서 제거. 검수 자체는 출시 전 할 일로 ⏳ |

## 답변 기록

- 2026-09-28: A1 `com.readyport` · A2 시스템 글꼴 · A4 M1에서 최신 안정판으로 업그레이드 · A5 Firebase Analytics 제외(Crashlytics·익명 실패 리포트로 오류 파악)
- 2026-09-28: 운영자 "남은 것은 추천대로" → A3 minSdk 26 · A6 공개 저장소 · A7 기기 내 LLM 1차 제외 · A8 클립보드 켬/60초. Firebase는 DoingWell과 같은 계정에 **별도 프로젝트**로 생성(DoingWell 프로젝트는 Cloud Functions 사용으로 Blaze라, Spark 전용 원칙과 과금을 섞지 않기 위함)
- 2026-10-01: 디자인 개편 운영자 결정 — **글꼴** Pretendard Std 1.3.9 번들(D1, A2를 바꿈) · **모서리** 8/12/16/20/28 체계, PRD 5장 '14~20dp' 문구 수정(D3) · **흰 카드** 1dp LineSoft 테두리 + 그림자(D2) · **도움 탭 순서** 문장 카드 먼저(PRD 5.11 원래 순서, D6) · **쉬운 모드 28sp 초과 예외** 승인, PRD 3.2에 기록(D19) · **'원어민 검수 전' 배지** 화면에서 제거(C10·C16·C17 검수는 출시 전 할 일로 남김) · **Play 업로드** 보류(C25). 나머지(쇼핑 '담았어요', 귀국 전 확인 전체는 귀국 단계에만 등) 17개 전체는 `docs/design/OWNER_DECISIONS.md`
- 2026-10-01: 태국 TDAC 제출 기간 문장 — 외교부 해외안전여행 원문("입국하는 날을 포함하여 입국 전 3일 이내")에 맞춰 TH 팩 불릿 수정·재서명(2026.10.01-1). 배포는 main 머지 때 deploy-packs
