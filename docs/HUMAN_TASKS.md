# 사람이 해야 하는 일 (M10 인계 목록, 2026-09-29)

> Claude는 계정 만들기·로그인·결제·약관 동의·비밀값 입력·정부 양식 제출을 하지 않는다. 아래는 운영자가 할 일이다.
> 번호는 `docs/QUESTIONS.md`와 같다. **굵게** = 출시를 막는 일.

## 1. 지금 바로
- ✅ C13 GitHub 저장소: https://github.com/inhyeuk/readyport (2026-09-29, main 보호 적용)
1. **C2 서명 키 백업**: `C:\Users\inhye\.readyport\keys\pack_signing_rp-2026-1.pem`을 암호화한 USB·비밀번호 관리자 등 안전한 곳에 한 부 더. 잃으면 앱 업데이트 없이는 새 팩을 못 믿게 한다.

## 2. 실기기 확인 (Claude가 대신 못 한 것)
- 지갑 잠금 해제(PIN·지문) → 여권 촬영: 표본 사진 `/sdcard/Pictures/icao_specimen_mrz.jpg`(ICAO 표본, 실제 여권 금지) → 값 확인 → 저장 → '여행 종료 후 파기'
- 입국 카드 확인 → 자동 입력 화면: **제출 버튼은 누르지 않는다**(가짜 값만)
- 시간대 바꾸기(설정 › 날짜와 시간) → '도착했어요' 카드, 비행기 모드 → 가이드·도움·입국 QR·기사님께 보여주기
- 알림 권한 허용 → 입국 카드 기간 알림
- 홈 화면 위젯(입국 QR), 원형·둥근 사각 런처, Android 13+ 테마 아이콘(**C11**, 13 이상 기기 필요)
- 60대 무경험자 5명·중학생 5명 과업 테스트(**C12**): '입국카드 제출 직전까지', '숙소까지 이동'

## 3. 계정·키·비밀값
- **C6 Play Console**: 개발자 계정, 앱 만들기(`com.readyport`), Play App Signing, 내부 테스트 트랙에 `app-release.aab` 올리기
- ✅ C20 GitHub secrets 등록 완료(2026-09-29). ARIA PC용 서비스 계정(Remote Config·Firestore만)은 ARIA 연결 때
- **C21 App Check**: Play Console 연결 → Play Integrity 등록, 디버그 토큰 등록(logcat `DebugAppCheckProvider`) → Firestore 강제 모드 켜기
- C3·C4 공공데이터포털(외교부 입국허가요건·인천공항·한국공항공사), 네이버 데이터랩(비용 확인) 키 → 인기 순위·쇼핑 검색 추이가 켜진다
- C23 ARIA 연결: `ops/aria/.env`(`.env.example` 참고), `MOFA_API_URL` 확인, `NOTICE_URLS`, 텔레그램 chat_id 허용 목록, `CLAUDE_BIN`=claude.exe → 연결 후 https://github.com/inhyeuk/readyport/actions/workflows/aria-watchdog.yml 에서 **Enable workflow**
- C7 이 저장소 전용 fine-grained 토큰(ARIA가 PR 만들 때)

## 4. 검토·법률
- **C8 법률 검토**: `docs/play/PRIVACY_POLICY_DRAFT.md`, `docs/play/DATA_SAFETY.md`(특히 WebView로 정부 사이트에 입력하는 경로, 국외 이전, 가족 모드 동의), 보험 공식 비교 링크, 스토어 정책(오인 주장)
- 개인정보처리방침 게시 주소 정하기(법률 검토 후 Claude가 Hosting에 올릴 수 있음)
- **C9 상표**(KIPRIS)·Play 동일 이름 확인 — `docs/play/STORE_LISTING_KO.md`
- **C10·C16·C17 원어민 검수**: 태국어·말레이어·인도네시아어·일본어 문장 7개씩 + 쇼핑 현지어 이름. 검수 후 `reviewed: true`
- C19 카야 잼·솔티드 에그·과자 상자 반입 기준 검역본부 문의(지금 '주의')
- C5·C18 제휴 프로그램 가입·약관(가입 전까지 '사러 가기' 없음)

## 4-1. 공지사항·앱 푸시 (2026-10-08, `docs/NOTICES_PUSH.md` 7절)
- Firestore 규칙 배포: `firebase deploy --only firestore:rules`(`notices/{id}` get만 — 머지 전에 해도 된다, 아직 문서가 없으면 앱은 그냥 공지가 없다)
- 서비스 계정 역할 확인: `Firebase Cloud Messaging API 관리자`(공지 알림) — videos·deploy-packs와 같은 계정
- 머지하면 notices.yml이 첫 공지(`about-readyport`)와 그림·개인정보 처리방침 갱신본을 올린다. 사람들이 보는 것은 **이 기능이 든 앱 버전을 출시한 뒤**
- Play Console 데이터 보안 양식 다시 보기(`docs/play/DATA_SAFETY.md` 5절 — 새 수집 항목은 없음)
- 광고 알림을 처음 보낸 날로부터 **2년 안에** 수신 동의 재확인 공지(정보통신망법 제50조 제8항, NOTICES_PUSH.md 5절) + 법률 검토(C8)
- 첫 실제 알림 시험: `python tools/notices/notice.py push --id about-readyport --target all --remote-dry-run` → `--send`는 공지 알림을 켠 **모든** 기기에 가므로 시험용 기기만 받게 하는 방법은 없다(토픽뿐). 첫 알림은 실제로 알릴 만한 공지로

## 4-2. 게시판 (2026-10-08, `docs/BOARD.md` 2절)
- **Firebase 콘솔 › Authentication › 익명 로그인 켜기** (꺼져 있으면 글쓰기가 `준비하고 있어요`로 막힌다). App Check를 강제하면 Authentication에도 등록
- **규칙·색인 배포**: `firebase deploy --only firestore:rules,firestore:indexes --project readyport-app` (색인 빌드 몇 분)
- **운영자 ID 등록**: 운영자 폰에서 글 하나 → 설정 › 게시판 › ID 복사 → 콘솔 `config/admins { uids: [...] }`
- **Play Console 데이터 보안 다시 내기**(`docs/play/DATA_SAFETY.md` 6절: 사용자 제작 콘텐츠·사용자 ID) + 콘텐츠 등급 '사용자 상호작용'
- **개인정보 처리방침 게시**: `hosting/public/privacy/index.html` 3-2절(게시판) — 법률 검토(C8, 임시조치 통지 방법 포함) 뒤 Hosting 배포
- 사진·동영상은 **켜지 않은 채 출시**. 켜려면 Blaze 요금제 + 예산 알림 + `firebase deploy --only storage` + 앱 설정 스위치(`docs/BOARD.md` 5절)

## 5. 선택
- C15 Visit Japan Web: 운영자가 폰에서 로그인한 상태로 열어 주면 Claude가 구조만 읽어 레시피를 만든다
- C22 Firestore 위치 nam5 → 서울로 옮기기(비어 있을 때)
- C24 개발 폰이 만든 `favorite_counts/TH`(1) 지우기
- ✅ 앱 크기(2026-09-29): ML Kit를 Play 서비스 모델로 바꿔 AAB 28→9MB. 모델은 설치 때(매니페스트 DEPENDENCIES)와 앱 시작 때(ModuleInstall) 받아 오프라인 촬영 유지. 실기기 한국어 OCR 확인
