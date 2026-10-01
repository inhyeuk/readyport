# 다듬기 S 통합 보고 — 2026-10-02

- 브랜치: `claude/design-refresh` (로컬 커밋만 — 푸시·main·버전·업로드 없음)
- 입력: S1(`97ab3fb` 도움·쇼핑·이동하기·설정·스토어), S2(`1c611ce` 홈·나라 입국·여행 준비·입국 카드 확인·값 복사해서 넣기), S3(`17ea751` 내 여행·꼭 챙길 물건·내 정보·여권·예약 서류·보여 주기) → 합친 커밋 `d6685a3`
- 기준: `OWNER_DECISIONS.md` '운영자 결정 기록', `POLISH_D0_REPORT.md`, `rereview2/*` 남은 문제, 스펙 2~4장·부록 C/D

## 1. 합친 트리 상태
합친 그대로 `assembleDebug testDebugUnitTest lintDebug`가 **통과**했다(테스트 367개, lint 오류 0·경고 11 = 기준선). 겹친 문자열 이름 0, S1·S2가 지운 문자열(`help_more_numbers`·`shopping_show_staff_short`·`photo_credit_license`·`home_items_powerbank`)을 부르는 곳 0, `AppScreen(showHomeAction)`·Gallery.kt 합침도 문제없음. 고칠 의미 충돌은 없었고, 테스트 컴파일 경고 1건(`HomePolishS2Test`의 쓸모없는 `!!`)만 지웠다.

## 2. 공용으로 옮긴 부품 (모양 그대로)
| 부품 | 예전 자리 → 새 자리 | 쓰는 곳 |
|---|---|---|
| `StepHead(number, text)` 번호 원 순서 머리 | `country/CountryScreen.kt`(internal, 21이 country 패키지에서 import) → `components/CardNews.kt`(StepList 옆) | 04 나라 입국, 21 값 복사해서 넣기 |
| `personalWindowKo`·`formWindowRange`·`windowRuleOnly` 내 여행 날짜로 '내는 때' | `CountryScreen.kt` → `components/EntryForm.kt` | 03·04, 18. `formWindowRange`는 내 여행 할 일 칩(TodayScreen `formWindow`)도 같이 쓴다(날짜 계산 한 곳, 표시 형식은 그대로). KDoc에 **도착일 = 여행 출발일 가정**과 그래서 문구가 `…에 도착하면`(단정형 금지)인 이유를 적음 |
| `EssentialsSummary`·`essentialsSummary`·`EssentialsChips`·`EssentialsProgress`·`essentialsSources` | `tabs/TabScreens.kt`(홈이 tabs에서 import) → `components/Essentials.kt` | 01·02 홈, 18 여행 준비 |

`ComponentsPage` 1쪽에 순서 머리 + 내 여행 날짜 입국 카드, 꼭 챙길 물건 요약(칩·진행·출처) 견본을 더했다.

## 3. 갤러리 픽스처
- 19 꼭 챙길 물건: `power = th.value.power` + 출처 이름(팩 sources) — 여행지 전기 값 카드가 캡처에 나온다.
- 23 이동하기: 기사님 문장 = 팩 `phrases[id=address]`의 현지어(`กรุณาพาไปที่อยู่นี้`)와 한국어 뜻, 차 앱은 운영처럼 지도 링크 제외(하드코딩 `กรุณาพาไปที่นี่` 삭제).
- 27 설정: `easyMode = LocalDimens.current.easyMode` — 쉬운 모드 캡처에서 쉬운 모드 스위치가 켜짐.
- 35 사진 출처: `loadPhotoCredits(context)` — `assets/photo_credits.json`의 번들 사진 8장 전부.

## 4. 문구
- 18 여행 준비의 `곧 추가돼요`(미리 설치할 앱·예약 서류) 묶음 삭제 — 예약 서류는 이미 내 정보에 있고, 앱 받기는 이동하기(`Play 스토어에서 받기`)가 맡는다. S3처럼 `곧 추가돼요`는 내 정보 맨 아래 한 곳뿐. `prepare_speech`도 맞춤, 안 쓰는 `prepare_apps_title`·`prepare_bookings_title` 삭제(부품 견본·테스트는 `wallet_profile_title`로).
- '제휴'(수수료 뜻) → '수수료 링크': 안 쓰는 옛 `essentials_disclosure`·`essentials_affiliate_label` 삭제(ComponentsPage는 S3의 `essentials_fee_*`로), `rankings_rule_no_ads`(보존 문구) `수수료 링크`로, PRD(14·220·222·325·332·425·436·457행)·ARCHITECTURE·스펙 6-15/6-16·STORE_LISTING 수수료 문단·코드 주석. **수수료 고지는 그대로 있다.** 정부 비제휴 문구(`정부 기관과 제휴하지 않았어요`)와 사업 용어 `제휴 프로그램 가입`(QUESTIONS·HUMAN_TASKS)은 그대로.

## 5. 큰 글자 목록 행 쌓기 (재검토2 ④#6)
- `Layout.kt` `isStackedListRow(hasBadge)` 한 곳: Stacked면 **배지가 있는 목록 행은 제목 길이와 관계없이 모두** 배지·끝 요소 윗줄 + 제목·설명 폭 전체. `BadgeTitleLayout(forceStack)` 추가.
- 적용: `ListRow`(설정·내 정보·보여 주기 밝기·이동하기 차 앱·도움 바로가기 — Custom 끝 요소 포함), 19 꼭 챙길 물건 체크 카드, 홈 접힌 줄(S2가 이미 같은 규칙 — 같은 도우미로). `KeyValueRow` 끝 요소(21 복사 버튼)는 Stacked에서 늘 값 아래 줄(예전엔 `국적`만 아래로).
- 새 테스트 `ListRowStackTest`(4): sdk31·200% 설정 기본·쉬운, 꼭 챙길 물건 — 행 제목 시작선이 모두 같고 배지 옆이 아님 / 100%에서는 모두 배지 옆.

## 6. 결과
| 항목 | 값 |
|---|---|
| `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | **BUILD SUCCESSFUL** |
| 단위 테스트 | **371개 / 76 클래스, 실패 0** (합친 직후 367 + `ListRowStackTest` 4; `HomePolishS2Test`에 18 `곧 추가` 0개 단언) |
| lint | 오류 0 / 경고 11 (기준선과 같은 종류·개수, 새 경고 0) |
| 감사 | A11yAudit 기본·쉬운·200%·sdk31·360dp·영어 기기 모두 통과, 갤러리 캡처 잘림 0 |
| 스토어 | `StoreScreenshotsTest`·`StoreFeatureGraphicTest` 다시 찍어 `docs/play/store/`에 같은 이름으로(02·03·04·06 바뀜). 견본 HONG GILDONG/KOR, 01 홈·03 인도네시아·05 일본·07 말레이시아·08 싱가포르, 1번 캡션 `칸은 앱이 채워요 / 제출만 직접`, 모든 장 `정부 기관과 제휴하지 않은 앱이에요`, 검수 배지 없음 — 눈으로 확인 |
| 전후 비교 `docs/design/compare/` | 만드는 스크립트·테스트가 저장소에 없어(이전 라운드가 손으로 만듦) **다시 만들지 않음** |

눈으로 본 캡처: 27 설정(200% 기본 — 9행 모두 같은 시작선, 쉬운 모드 스위치 켜짐), 19·23·18(기본·200%), 35(8장), 36 부품, 01·02 홈(200% 기본·쉬운), 04 인도네시아 200%, 20·21 200%.

## 7. 남은 것
- 갤러리 캡처에는 360dp 폴더가 없다(360dp는 A11yAudit 감사로만 본다).
- 여행지 전기 칩이 두 벌: 01·02·18(`EssentialsChips` — 플러그 먼저, 모두 Accent, 미확인 나라 `챙기세요`)과 19(`PowerValuesCard` — 전압 먼저, Success/Caution, 미확인 `챙기면 안전해요`), 문자열도 `items_chip_*`·`essentials_power_*` 두 벌. 모양을 바꾸지 말라는 범위라 그대로 둠 — 다음 라운드에 한 부품으로.
- '내는 기간' 날짜 표시 형식 두 가지(03·18 `11월 1일~3일`, 내 여행 칩 `11월 1일 ~ 3일`) — 계산은 하나로 합침.
- 23 200%에서 `한국어 뜻: 이 주소로 가 / 주세요`처럼 보조 동사 앞에서 줄이 바뀜(규칙 위반은 아님).
- Play 업로드는 여전히 보류(운영자 결정 1).
