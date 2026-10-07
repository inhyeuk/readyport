# 공지사항·앱 푸시 작업 보고 (2026-10-08, 브랜치 `claude/notices-push`)

운영자 요청: *"D.well App과 같이 App 시작시에 공지사항을 띄우는 기능을 추가해줘. 그리고 App Push를 할 수 있는 기능도 추가해줘."*
추가 요청: *"공지에 App에 대한 간략한 설명과, 절대적으로 개인정보는 모바일에만 저장되어 유출 가능성은 전혀없다는 두 가지 내용을 카드 뉴스 형태로 만들어서 넣어줘"*

운영 안내 `docs/NOTICES_PUSH.md` · 설계 기록 `docs/ARCHITECTURE.md` '구현 결정 기록 (공지사항·앱 푸시)' · 화면 DESIGN_SPEC 부록 K · 결정 `OWNER_DECISIONS.md` N-1~N-10.

## 1. 무엇을 만들었나

| 갈래 | 파일 |
|---|---|
| 공지 원본·스키마 | `notices/notices.json`(켜진 것: `about-readyport` 하나, `welcome-050`은 꺼짐), `notices/schema/notices.schema.json` |
| 검증·서명·올리기·FCM | `tools/notices/build_notices.py`(check · build · upload · fcm), 운영자 도우미 `tools/notices/notice.py`(new · check · push), 그림 줄이기 `tools/notices/pack_images.py`, 테스트 `tools/notices/test_notices.py` |
| CI | `.github/workflows/notices.yml` — main의 `notices/**`·`hosting/public/notices/**` → 검증·서명·Firestore `notices/current`(그림이 바뀌면 팩 재서명 후 Hosting 배포) / workflow_dispatch(notice_id·target·dry_run 기본 참) → FCM 토픽, 403은 경고 |
| 규칙 | `firebase/firestore.rules` `match /notices/{id} { allow get: if true; allow list, write: if false; }` + `tools/firestore/rules.test.mjs` 한 개 (**배포 안 함**) |
| 앱 — 데이터 | `notice/Notices.kt`(모델·서명 해석·안전 검사·고르기·버튼 규칙), `notice/NoticeRepository.kt`(Firestore 받기·기기 안 사본·되돌리기 막기·보지 않기 기록), `notice/NoticePush.kt`(알림 판단·밤 미루기·통로 `notice`·처리기·미루기 작업) |
| 앱 — 화면 | `ui/notice/NoticeScreens.kt`(대화상자 카드·공지사항 목록·ViewModel), 설정 › 공지·소식(`SettingsScreen`), `NoticesRoute`, `MainActivity`·`MainViewModel`·`ReadyPortRoot` 연결 |
| 앱 — 기존 것 넓히기 | `CloudSync`(토픽 `notice_all`·`notice_promo`), `PolicyMessagingService`(type notice 먼저), `SettingsRepository`(다섯 칸), `NetworkThumbnails` 허용 목록(+ 우리 Hosting `notices/`, 리디렉션 안 따라감) |
| 문구 | `res/values/strings_notices.xml`(새 문구 전부) |
| 첫 공지 그림 | `hosting/public/notices/about-readyport-1.png`·`-2.png`(1080×1350), 그리기 `NoticeCardArtTest` |
| 문서 | PRD 5.15·5.17·6.2·8.2, ARCHITECTURE 9.4·9.6·결정 기록, DESIGN_SPEC 부록 K, NOTICES_PUSH.md, HUMAN_TASKS 4-1, 개인정보 처리방침(게시본 `hosting/public/privacy/index.html` + 초안), DATA_SAFETY 5절, OWNER_DECISIONS |

## 2. 사람들이 보는 것

- **앱을 켤 때**: 첫 실행 질문을 마친 뒤(같은 실행 안에서 마쳐도), 자녀 폰 모드가 아니고 위젯·알림·공유로 연 실행이 아니면 공지 하나. 새 버전의 첫 화면은 `레디포트를 소개해요`(이용 안내) — 카드 두 장 + 마지막 쪽 `개인정보 처리방침 보기`, 한 번 보면 끝.
- **설정 › 공지·소식**: 공지사항 · 공지 알림(켬) · 광고성 소식 받기(끔, 날짜) · (켰으면) 밤에도 받기 · `알림은 주제 구독으로만…`.
- **알림**: 통로 `공지·소식`. 글은 서명된 공지의 제목·첫 쪽, 못 받으면 `새 소식이 있어요`.

## 3. 첫 공지 카드 (운영자 추가 요청)

`about-readyport` — guide · service · priority 900 · start 2026-10-08T00:00+09:00 · end 없음 · audience all · version 1.

| 카드 | 글 | 파일 |
|---|---|---|
| ① | **레디포트는 이런 앱이에요** · `계획부터 복귀까지 8단계 / 여행을 만들면 할 일을 알려 드려요` · `입국 카드 칸은 앱이 채워요 / 제출은 직접 눌러요` · `9개 나라 · 24개 공항 안내 / 모두 공식 출처와 확인 날짜까지` · 맨 아래 `정부 기관과 제휴하지 않은 앱이에요` | `https://readyport-app.web.app/notices/about-readyport-1.png` (104,253 bytes) |
| ② | **여권 정보는 이 휴대폰에만** · 가운데 `레디포트 서버에는 여러분의 여권 정보가 아예 없어요` · `휴대폰 안에서 암호화해 보관해요` · `서버 해킹으로 새어 나갈 일이 없어요` · `여행이 끝나면 버튼 하나로 지울 수 있어요` · 각주 `휴대폰 잠금은 꼭 걸어 두세요` | `https://readyport-app.web.app/notices/about-readyport-2.png` (96,578 bytes) |

- 사실 확인(코드): 여권·예약 서류는 Android Keystore AES-256-GCM(`vault/VaultCipher.kt`·`KeystoreKeys.kt` — 화면 잠금이 있으면 키 사용에 지문·PIN 필요), 서버로 보내는 것은 익명 리포트·찜 수·토픽 구독뿐(`cloud/CloudSync.kt`), 귀국 단계 `여권 정보를 지울까요?` 카드의 버튼 하나(그 카드 자체가 확인 — D8). 정부 사이트 제출은 사용자가 직접 — `레디포트 서버`에는 없다.
- 운영자 원문 "유출 가능성은 전혀 없다"는 쓰지 않았다(잃어버린·루팅한 휴대폰·악성 앱). 코디네이터가 알린 문구 그대로, `그래서`만 한 줄에 맞게 뺐다(약해지지 않음). 잠금이 없는 휴대폰에서는 키가 잠금 없이 만들어지므로 각주 `휴대폰 잠금은 꼭 걸어 두세요`가 정직한 단서다.
- 그리기: 400×500dp 캔버스 @2.7 → 1080×1350, 앱 글꼴(Pretendard)·토큰·일러스트(`Illus.Plan`·`Entry`·`Arrival`)·앱 아이콘. 256색 PNG(오차 확산)로 줄여 약 100KB씩(무손실 284·252KB) — 눈으로 차이 없음, 무료 전송 한도 절약.
- 대화상자에서는 카드 폭으로 0.82배(굵은 줄 약 17sp). 글자 200%에서도 그림은 커지지 않으므로 대체 글(카드 글 전부)과 쪽 글이 함께 있다.

## 4. 검사

(빌드·테스트 결과는 아래 5절)

- 단위: 서명(좋음·변조·다른 kid·다른 키·모르는 스키마·모르는 종류), 안전 검사(그림·링크·광고 표시·길이·시간대), 고르기(기간·버전·대상·다시 보지 않기·버전 올리면 다시·오늘 하루), 하나씩 돌아가며·긴급 이어서·자녀 폰 모드·광고 동의·이용 안내 한 번, 목록 순서, 버튼 규칙, 알림(서명 글만·앱 문구·설정·자녀·기간·대상·다시 보지 않기·광고 동의·밤 미루기·파리 등 시간대·긴 본문), 저장(사본·되돌리기 막기·기기 안 변조·느린 네트워크·기록), 토픽(켬·끔·광고·자녀), 설정(동의 날짜·밤은 광고 켰을 때만·끄면 함께), 화면(긴급 닫기·일반·이용 안내·다시 보기·대체 글·광고 끄는 방법·paneTitle·목록·설정 날짜·결과 대화상자), 카드 그림(1080×1350·줄 잘림·대체 글에 카드 글 전부·커밋본 크기).
- 파이썬: 검증(나쁜 주소·대체 글 없음·파일 없음·크기·링크·빈 기간·끝난 공지·모르는 종류·긴 본문·HTML·광고 규칙·대상·버전·id), 서명본(꺼진 것 빼기·정렬·서명 왕복), FCM 메시지(글 없음·`country` 키 없음·조건·광고 토픽), 보내기 전 확인, 운영자 도우미 시험 실행(네트워크 안 씀)·보내기 요청 모양·토큰 안 찍음·틀.
- 갤러리 8장 추가(`notice-about-1/2`·`notice-urgent-first/last`·`notice-normal`·`notice-event-promo`·`notices-list`·`settings-notices-promo`) — 접근성 점검(기본·쉬운·200%·360dp·sdk31·영어)이 모두 본다.

## 5. 결과

- 빌드: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` **성공**.
- 단위 테스트 **591개 실패 0**(main 860007f 기준 542개 + 새 49개: `NoticesTest` 18 · `NoticePushTest` 7 · `NoticeStoreTest` 5 · `NoticeUiTest` 12 · `NoticeCardArtTest` 2 · `AboutNoticeTest` 2 · `CloudSyncTest` +2 · `NoticeSettingsStoreTest` 1). 접근성 점검(기본·쉬운·200%·360dp·sdk31·영어)이 새 갤러리 8장을 포함해 모두 통과.
- lint **오류 0, 경고 12** — main(860007f)에서 같은 명령으로 잰 기준선과 같은 12개·같은 항목(AndroidGradlePluginVersion·GradleDependency·IconXmlAndPng 2·InlinedApi·OldTargetApi·TypographyDashes·UnusedAttribute 2·UseKtx 3). 새 경고 0. (예전 기준선 11에서 하나 는 것은 새 AGP 버전이 나와 생긴 'AndroidGradlePluginVersion' 알림 — 이 작업과 무관)
- 파이썬: `tools/notices/test_notices.py` 22개, `tools/packs/test_build_packs.py` 31개, `tools/videos/test_fetch_videos.py` 통과. `build_notices.py check` 통과(켜진 공지 `about-readyport` 하나).
- Firestore 규칙 에뮬레이터 테스트(`tools/firestore`, 새 테스트 한 개)는 이 작업 폴더에 `node_modules`가 없어 돌리지 않았다 — 운영자가 규칙 배포 전에 `cd tools/firestore && npm install && npm test`.
- 갤러리(검토함): `59_notice-about-1` · `60_notice-about-2` · `61_notice-urgent-first` · `62_notice-urgent-last` · `63_notice-normal` · `64_notice-event-promo` · `65_notices-list` · `66_settings-notices-promo` — 기본·쉬운·sdk31 200%(기본·쉬운). 고친 것: 긴급 공지 중간 쪽에서 X가 없을 때 머리 높이가 달라 제목이 밀리던 것(빈 자리 둠), 카드 ①③째 줄 잘림·② 셋째 줄 잘림(레이아웃 줄이고 '넘치면 실패' 검사 추가), `그래서 … / 없어요` 한 음절 줄(문구 줄임), 설정 한 줄을 '레디포트는 … 받거나 저장하지 않아요'로(구글 FCM 자체는 토큰을 가진다 — 과장 없이).

## 6. 운영자 할 일

1. Firestore 규칙 배포 `firebase deploy --only firestore:rules`(배포 전 규칙 테스트).
2. 서비스 계정에 `Firebase Cloud Messaging API 관리자` 역할 확인(이미 videos·deploy-packs가 쓰는 계정).
3. 머지 → notices.yml이 첫 공지·그림·개인정보 처리방침 갱신본을 올린다. 사람들이 보는 것은 **이 기능이 든 앱 버전을 출시한 뒤**(버전 올리기·업로드는 이 작업 밖).
4. Play Console 데이터 보안 양식 다시 보기(새 수집 항목 없음 — `DATA_SAFETY.md` 5절), 법률 검토(C8)에 광고 동의 2년 재확인 방법 포함.
5. 알림은 `python tools/notices/notice.py push --id … --target all --send`(먼저 `--remote-dry-run`). 이번 작업에서는 **한 번도 보내지 않았다**.
