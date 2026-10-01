# 재검토 수정 C(통합) 보고 — 2026-10-01

- 브랜치: `claude/design-refresh` (로컬 커밋만 — 푸시·Play Console·Firebase·GitHub·`packs/` 손대지 않음)
- 입력: 수정 B 세 묶음(b1 `65dbd8e` 홈·첫 실행·나라 / b2 `3f686e7` 귀국·정리·꼭 챙길 물건·입국 카드 확인 / b3 `1bd5ca6` 지갑·여권·보여 주기·쇼핑·이동·도움·설정·스토어)이 남긴 통합 할 일, `REREVIEW_SUMMARY.md`, `FIX_A_REPORT.md`, `rereview/*.md`
- 운영자 결정은 `OWNER_DECISIONS.md` 한 곳에 모았다(17개).

## 1. 결과

| 항목 | 통합 전(B 세 묶음 합친 뒤) | 통합 후 |
|---|---|---|
| `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | BUILD SUCCESSFUL | **BUILD SUCCESSFUL in 4m 29s** |
| 단위 테스트 | 311개 / 64 클래스 | **317개 / 65 클래스, 실패 0** (새 `SharedComponentsFixCTest` 6) |
| lint 오류 / 경고 | 0 / 11 | **0 / 11** (기준선과 같은 11건 — UseKtx 3·UnusedAttribute 2·IconXmlAndPng 2·InlinedApi·OldTargetApi·ObsoleteSdkInt·TypographyDashes) |
| A11yAudit 줄바꿈·보이는 크기 보고 (기본·쉬운·200%·sdk31) | 0건 | **0건** |
| 화면에만 있던 임시 부품 | 6개 | **0개** (모두 공용으로) |
| 쓰지 않는 문자열 | 13개(테스트만 '없음' 확인) + 통합으로 생긴 8개 | **21개 삭제**, 새 키 3개(`strings_fix_c.xml`) |

## 2. 항목별 처리

### 2.1 화면 임시 부품 → 공용 부품
- **`StatTile` = 칸 폭에 맞춘 한 줄** (CardNews.kt): 값을 `FitText(stat → statSmall → titleLarge)`로 그린다. `IDR 500,000`처럼 통화 코드가 붙은 금액은 코드를 값 위 작은 글자로, TalkBack은 한 덩어리. `wide`(가로형) 인자 추가, 아이콘은 글자 크기를 따라 커짐. **`FactGrid`**가 1열이면 가로형, 2열 홀수면 마지막 타일을 폭 전체로 놓는다(b1 규칙을 공용으로). 나라 화면 `FitStatTile`·`CurrencyAmount` 삭제 → `FactTiles` = 0개 없음 / 1개 `StatTile(wide)` / 그 밖 `FactGrid`. 정리 단계 숫자 타일도 같은 부품.
- **어두운 사진 밝기 보정** (Photos.kt): b1 `rememberBrightPhoto`·`photoLiftFilter`·`FilteredPainter`를 옮기고 **공용 사진 틀 자체가 보정**한다 — `PhotoBox(res)`·`PhotoHeaderCard(res)`·`CountryPhotoTile`(홈 나라 타일)이 모두 그린다. 썸네일은 `rememberPhotoLift(bitmap)`(홈 여행 카드·여행 고치기 나라 카드·도움 나라 칩·사진 출처). 평균 밝기 128 미만만(지금 홈·일본·말레이시아·싱가포르·태국), 사진 파일은 그대로. 사진 출처 설명(`photo_credits_body_c`)과 `photo_credits.json`의 `changes`에 밝기 보정을 적었다(CC BY 변경 표시). 첫 실행 화면의 로컬 함수 삭제.
- **`ReturnCheckCard` = 접힌 요약 하나** (ReturnCheck.kt): b2 `ReturnRulesCard`를 공용으로 — 안내 한 줄 → 사실 행(주제 아이콘 + 팩 문장의 첫 문장, 숫자 토큰 굵게) → `면세 한도·반입 금지 문장 전체 보기`(펼치면 같은 행이 문장 전체) → 출처 묶음 → 공식 링크. 홈·나라 쇼핑·쇼핑 리스트·내 여행 귀국이 같은 카드. `compact` 인자와 주제 이름 칩 줄은 없앰. `firstSentence`·`numberRanges`·`emphasizeNumbers`도 공용으로(테스트 import 변경). TodayScreen의 로컬 카드·행·도우미 삭제.
- **`DangerButton(contentDescription)`** (Buttons.kt): 무엇을 지우는지 TalkBack 이름. 보여 주기(서류·같이 가는 사람)·지갑(예약 서류)의 `Modifier.semantics { contentDescription }` 우회 삭제.
- **`StepList(sentence)`** (CardNews.kt): 문장형 = 본문 글자(bodyLarge, Ink) — 번호 원만 강조. 단계 글은 이제 **언제나 본문 줄바꿈**(어절 단위로 채움)이라 b1 `BodyBreakStepList`(나라 단계·지도 저장)는 공용 `StepList`로, b3 `SentenceStepList`(도움 절차)는 `StepList(sentence = true)`로 바꾸고 둘 다 삭제.

### 2.2 문자열 정리
- 삭제(21): 테스트가 '없음'만 확인하던 옛 문구 13개(`form_manual_mode`·`home_chip_form`·`home_chip_visa_apply`·`home_essentials_body`·`passport_chip_soon`·`passport_privacy_1/2`·`passport_step_chip`·`present_share`·`settings_privacy_body`·`today_destroy_now`·`wallet_passport_show`·`wallet_passport_verified`), 공용 귀국 카드로 합치며 안 쓰게 된 7개(`shopping_return_body`·`return_topic_allowance/plant/livestock`·`return_check_more`·`today_return_rules_lead/more`), `photo_credits_body_v2`.
- 테스트는 새 문구가 보이는지 + 옛 말(글자 그대로 `MRZ`·`수동 모드`·`다른 폰으로 보내기`·`이 기기`·`칩 확인`·`도우미`·`지우기`·`자세히 보기`)이 없는지로 바꿨다. 부품 페이지 견본도 새 키로.
- 새 키(`strings_fix_c.xml`): `return_check_lead`, `return_check_full`, `photo_credits_body_c`.
- `rankings_*` 15개는 2단계 결정대로 보존(`tools:ignore`, 운영자 결정 16번).

### 2.3 sdk36 캡처의 낱말 안 줄바꿈 (`태국 입국 카드 (TDAC)/를`)
- 원인: 어절 단위 줄바꿈(WordBreak.Phrase)은 **글자의 언어가 한국어일 때만** 띄어쓰기에서 끊는다. 글자 스타일에 언어가 없어 기기 언어를 따랐고, 스토어 캡처(`StoreAssetsTest`, 언어 지정 없음 = 영어)에서 제목이 `)`와 `를` 사이에서 끊겼다. 갤러리·감사는 `ko-rKR`이라 안 보였다. **기기 언어가 영어인 실제 폰에서도 같은 문제**였다.
- 해결(공용 글자 스타일, Theme.kt): `ReadyPortLineBreak.Korean` = `LocaleList("ko-KR")`를 타입 스케일 전부와 숫자 스타일에 넣었다. 현지어 스타일(`localLarge`·`localMedium`·`localText`)은 빼서 태국어·일본어 글꼴 선택은 기기 언어를 따른다. 보이는 글자는 그대로(보이지 않는 문자 없음) — API 33 이상에서 getString과 같다. API 33 미만은 그대로 `keepWords`(WORD JOINER)+Simple.
- 확인: 스토어 04가 `태국 입국 카드 / (TDAC)를 낼 수 있어요`. 새 테스트 `koreanTitlesBreakOnlyAtSpacesOnNonKoreanDevice`(기기 언어 **영어**, 폭 160~340dp 16단계)가 줄이 띄어쓰기에서만 바뀌는지 확인.

### 2.4 스토어 이미지·등록 문구
- `docs/play/store/` 8장 + 그래픽 이미지를 이 빌드 결과로 교체(이름 그대로, 남은 옛 이름 없음).
- 다시 보며 고친 것: **01 캡션**이 히어로 가치 문장을 거의 그대로 되풀이(`입국 신고서 칸은…`) → `나라만 고르면 / 입국 준비가 한곳에`. **02**는 `빈칸 2개 남았어요` 경고 카드가 첫 화면을 덮어 값이 하나도 안 보였다 → 스토어 픽스처에 견본 휴대폰 번호(010-1234-5678)·나라 번호를 넣어 `빈칸을 다 채웠어요` + 여권에서 가져온 값이 보이게. **04**는 위 줄바꿈 수정.
- `STORE_LISTING_KO.md` 그래픽·스크린숏 절: 스토어 전용 픽스처, 8장 순서·화면·캡션 표, 영상 화면 제외, 업로드는 운영자 컨펌 뒤.

### 2.5 그 밖에 캡처를 보며 고친 것
- **가치 문장 줄바꿈** (홈 히어로·첫 실행 브랜드 묶음): `입국 카드 칸은 앱이 / 채우고, …`처럼 뜻 덩어리 가운데서 꺾였다 → 공용 `ValuePropText`가 쉼표 뒤에서 두 줄(`…채우고,` / `제출만 직접 눌러요`)로, 그렇게 해서 두 줄을 넘으면(좁은 창·200%) 보통 줄바꿈으로 되돌린다(의미 글자는 원문).
- `ReadyPortRootTest.firstRunNoKeepsBasicMode`: 320×470 기본 창에서 두 번째 선택 카드가 화면 밖이라 클릭이 빗나갔다 → 스크롤한 뒤 누르게(첫 실행 화면은 세로 스크롤).

## 3. 새 테스트 (`components/SharedComponentsFixCTest.kt`, sdk36·기기 언어 영어)
`koreanTitlesBreakOnlyAtSpacesOnNonKoreanDevice` · `statTileValuesStayOnOneLineAndOddLastTileIsWide`(`30일`·`4박 5일` 한 줄, `IDR 500,000` 한 덩어리, 홀수 마지막 타일 폭 전체) · `dangerButtonNamesWhatItDeletes` · `stepListSentenceStyleUsesBodyText` · `photoLiftOnlyForDarkPhotos` · `returnCheckCardFoldsToFirstSentences`.

## 4. 눈으로 확인한 캡처
- 스토어 8장 전부(`app/build/store/`).
- 갤러리(기본·쉬운·sdk31 200%): 첫 실행, 홈(히어로·귀국 전 확인·쉬운 모드 접힘), 나라 입국(태국 90일 가로형 타일, 인도네시아 200% `30일`·`IDR`⏎`500,000`), 나라 여행 정보(지도 저장 단계), 내 여행 귀국(200% 접힌 요약)·정리(숫자 타일), 도움(절차 문장형), 쇼핑 리스트, 여행 고치기 썸네일, 사진 출처, 부품 페이지.

## 5. 남은 것
- 운영자 결정 17개: `OWNER_DECISIONS.md`.
- 스펙·PRD 문서 갱신(부록 B에서 지운 키, StepList·StatTile·ReturnCheckCard 시그니처, 3.2 언어 고정)은 결정 17번과 함께.
- 실기기(S10 5G) 확인: 영어 기기 언어에서 한글 줄바꿈, 사진 밝기 보정, TalkBack `○○ 지우기`.
