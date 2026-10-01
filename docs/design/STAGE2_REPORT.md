# 디자인 개편 2단계(통합) 보고 — 2026-10-01

- 브랜치: `claude/design-refresh` (로컬 커밋만, 푸시·Play Console·Firebase·GitHub 작업 없음, `packs/` 손대지 않음)
- 입력: `DESIGN_SPEC_2026-10-01.md` 2~4·7장·8장 2단계, `STAGE0_REPORT.md`, `STAGE1_BUNDLE_RESULTS.md`, `BUNDLE_A_NOTES.md`

## 1. 결과

| 항목 | 2단계 시작(1단계 A~F 합친 뒤) | 2단계 끝 |
|---|---|---|
| `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | BUILD SUCCESSFUL | **BUILD SUCCESSFUL** (마지막 빌드 줄은 §6) |
| 단위 테스트 | 270개 / 59 클래스, 실패 0 | **276개 / 60 클래스, 실패 0** |
| lint 오류 | 0 | **0** |
| lint 경고 | 85 = UnusedResources 74 + 그 밖 11 | **11 = UnusedResources 0 + 그 밖 11**(기준선과 같은 11건, 새 경고 0) |
| 보이는 크기 미달(A11yAudit ④) | 0건(보고만) | **엄격(실패)으로 전환** — 모든 화면·모드 0건 |
| 줄바꿈 보고(새 A11yAudit ⑤, sdk31·200% 포함) | — | **0건** (41화면 × 감사 5종: 기본·쉬운·200% 쉬운·sdk31 200% 기본·쉬운) |

새 테스트: `components/KoreanTextTest`(14) + `KoTextSemanticsTest`(sdk31·200%, 2), `ReadyPortRootTest.homeHelpCardOpensHelpTab`, `CreditsUiTest.bundledPhotosAndCreditsMatchOneToOne`.
지운 테스트: `today/KeepWordsTest`(6, 공용 테스트로 옮김), Home·Wallet·HelpShopping의 묶음 전용 줄바꿈 테스트 6개(같은 경우를 공용 테스트가 검사).

## 2. 묶음이 넘긴 공용 부품 문제 → 처리

### 2.1 한국어 줄바꿈 하나로 합치기 (A·B·C·D·E·F)
- 새 공용 파일 `ui/components/KoreanText.kt`
  - `keepWords(text, sdk)`: **API 33 미만에서만** 낱말 안 한글이 낀 글자 사이에 WORD JOINER, 관형사(`이·새·한·두…`) 뒤·의존 명사(`수·것·중…`) 앞 NBSP, 한글 뒤 여는 괄호 앞과 긴 낱말(6자 초과)의 가운뎃점·물결·빗금 뒤는 줄바꿈 자리, 띄어 쓴 ` · `·` — `는 앞 낱말에 붙임. 33 이상·한글 없는 글(전화번호·태국어)은 그대로.
  - `KoText(text, style, modifier, …)`: 보이는 글자만 보정하고 **의미 글자(TalkBack·테스트)는 원문** — `semantics { text = 원문 }`. KoText 안에서만 쓰는 `koDisplay`는 모든 API에서 긴 낱말의 가운뎃점 뒤·한글 뒤 괄호 앞·주소 점 뒤(`imigrasi.\u200bgo.\u200bid`, 날짜·숫자 제외)에 ZWSP, `glueShort`(제목의 한 음절 낱말 묶기)를 더한다.
  - 함께 옮긴 것: `glueShortWords`·`noBreak`(A의 날짜 덩어리)·`keepTogether`·`breakAfter(Dots)`(E), `largeFont()/hugeFont()`·`LARGE/HUGE_FONT_SCALE`·`BadgeTitleLayout`·`koDescription`(E), `textIconSize`·`firstLineIconOffset`(새로).
  - API 33 이상에서 보이는 글자·TalkBack 글자는 문자열 리소스와 같다(테스트는 그대로 getString으로 통과).
- 공용 부품 안에서 적용: SectionHeader·CardNewsCard(제목은 `glueShort`)·StatTile·FactChip·StepList·IconBullet·ExpandableDetail·버튼 4종·DestructiveConfirm·SourceFooter·LinkRow·StatusTag/StatusChip(→ImportVerdictBadge)·ReturnCheckCard·JourneyStepper·ComingSoonGroup·ListRow·ListGroup·KeyValueRow·IconTile·ChoiceCard·SelectTile·EmptyState·LockedState·EmergencyCallTile·NoticeBanner·SecurityBanner·OfflineBanner·PhotoChip·PhotoHeaderCard·CountryPhotoTile·ChoiceSegments·SelectChip·AppScreen 제목·부제.
- 지운 묶음 사본: A `keepWords`·`unbreakable`·`FirstRunChoice`·`largeText`, B `koreanPhraseWrap`·`wrapKo`(+가운뎃점·따옴표 규칙), C `keepWords`·`keepTitle`(→ `! ` 줄바꿈만 남김)·`joinKoreanWords`·`breakableDate`·`SoonGroup`, D `keepAll`·`KeepAllText`·`joinWords`·`sourceDate`·`HelpCallTile`·`PhoneNumberText`·`phoneGroups`·`localText`·`ShowLocalBody`(→ 공용 `ShowLocal.kt`·`Tiles.kt`), E `KoBreak`·`KoText`·`KoStatusTag`·`KoNotice`·`koSemantics`·`CompactSecurityLine`·`BadgeTitleLayout`·`WideSourceFooter`·`LocalOnlyCard`, F `keepWords`·`KeepText`·`SpokenAs`. 화면 호출 약 400곳을 공용 부품으로 되돌리거나 `KoText`로 바꿨다(부품에 넘기는 글자의 `keepWords()` 감싸기는 지움).
- 2단계에서 고친 규칙(캡처·줄바꿈 보고로 확인): 괄호·따옴표 묶음(B·C의 NBSP 묶기)은 **쓰지 않는다** — 쉬운 모드 200%의 좁은 줄(약 8자)에서 묶음이 한 줄보다 길어져 `(둥/근 핀 2개)`, `'오프라/인 지도'`처럼 억지로 끊겼다.

### 2.2 출처 줄 날짜 (B·C·D·E)
- `source_footer` = `출처 %1$s\u00A0· 최\u2060종\u00A0확\u2060인 %2$s`: B 제안대로 `확인`↔날짜를 보통 띄어쓰기로 → 날짜가 **통째로** 다음 줄로 간다(숫자·점은 UAX#14상 끊기지 않음). 더해서 이름 뒤 ` · `가 줄 머리에 오지 않게 NBSP. 테스트는 getString 비교라 영향 없음.
- 큰 글자(130%↑)에서는 출처 아이콘을 글 첫 줄 앞에 넣어 폭 전체를 쓴다(E의 WideSourceFooter를 공용으로). `EssentialsLargeFontTest`·새 `KoTextSemanticsTest.sourceFooterDateNeverSplits`가 sdk31·200%에서 날짜 한 줄을 확인.
- 지움: D `sourceDate`(ZWSP), C `breakableDate`, E `sourceFooterDisplay`.

### 2.3 그 밖의 부품 문제
| 지적 (묶음) | 처리 |
|---|---|
| 큰 글자에서 아이콘만 작게 남음 — 버튼·StepList·ImportVerdictBadge·PhotoHeaderCard (C) | `textIconSize(base, style)`: 글자의 실제 확대 비율(API 34+ 비선형 포함)을 따라 최대 1.5배. 버튼·태그·칩·출처·링크·배너·StepList·IconBullet·세그먼트·SelectChip·ComingSoonGroup·SelectTile + 화면 안 작은 아이콘(영상 메타·준비물 상태·쇼핑 판정 등). IconBadge는 스펙 4.2대로 dp 고정 |
| PhotoHeaderCard 아이콘이 두 줄 제목 가운데에 뜸 (A 요청 2) | 제목 **첫 줄 가운데**(`firstLineIconOffset`)로 위 맞춤 → A가 큰 글자에서 빼던 사진 머리 아이콘을 되살림 |
| StepList 보조문이 아이콘 열에서 시작 (A 요청 3) | 보조문을 단계 글 시작선에 맞춤. 130%↑는 단계 아이콘(장식)을 빼고 번호 원만 |
| ChoiceCard 큰 글자 `처음이에/요` (A ⑥) | 150%↑ 또는 1열 130%↑에서 배지·셰브론 윗줄 + 제목·설명 폭 전체(A의 FirstRunChoice를 부품으로) |
| ComingSoonGroup 머리 `곧 추/가돼/요` (C·F) | 머리 줄 `TrailingFlow`(태그가 제목을 쪼갤 만큼 좁으면 아래 줄) + KoText. 항목 `(준비 / 중)`도 어절 단위 |
| ListRow 스위치·셰브론이 큰 글자에서 제목 폭을 뺏음 (F) | `BadgeTitleLayout`(E)로: 130%↑에서 제목이 한 줄에 안 들어가면 배지·끝 요소 윗줄, 제목·설명 폭 전체. 설명은 끝 요소 아래까지 넓힘 |
| ListGroup 제목 4dp 들여쓰기 (F) | 없앰 |
| AppScreen 오른쪽 칩이 제목을 세로로 쪼갬 (F) | `headerActions`를 `TrailingFlow`로 |
| SecurityBanner `휴대폰/에만`, `이` 홀로 남음 (F·E) | 제목 KoText(`glueShort`), 큰 글자에서 전체형은 배지를 제목 위로·compact는 자물쇠 첫 줄 맞춤 → E의 LocalOnlyCard·CompactSecurityLine 지움 |
| ReturnCheckCard `농림축/산검역본부`, `면세 범/위` (A·C·D) | 본문·사실 행·링크 모두 KoText. `관세청·농림축산검역본부`는 가운뎃점 뒤에서만 |
| JourneyStepper `(6단계 중/1단계)`, `6단/계)` (C) | KoText + `중`을 의존 명사로 — `(6단계 중 / 1단계)`처럼 띄어쓰기에서만 |
| EmergencyCallTile 번호가 숫자 사이에서 끊김·연한 타일 배지가 사라짐 (D) | D의 HelpCallTile 개선을 부품에: `PhoneNumberText`(`-` 뒤에서만, 의미 글자는 번호 전체)·흰 배지. 도움 화면은 공용 부품으로 되돌림 |
| `원어민 검수 전` 태그에 Update 아이콘을 못 씀 (D) | `StatusTag(icon = …)` 인자 추가(톤은 kind 그대로), D의 UnreviewedTag 지움 |
| `IconKeys.option` 아이콘 3개를 화면에서 덮어씀 (E) | 공용 IconKeys에 BusinessCenter·Forum·CorporateFare, E의 optionIcon 지움 |
| OfflineBanner 여러 줄일 때 아이콘이 가운데 | 첫 줄 맞춤 |

### 2.4 갤러리·캡처
- 갤러리 픽스처: 홈에 준비물 진행 `EssentialsSummary(5, 2)`(A 요청 7), 새 항목 `today-departure-form`(C의 출국일 입국 카드 상태 — 주 버튼 하나). 이제 41화면 × 4모드가 A11yAudit·캡처를 지난다. 묶음 전용 캡처 테스트(A·C·D·E·F)는 갤러리에 없는 변형(하단 탭 6상태·320dp·전체 화면 3종·자동 입력 3상태·지갑 12상태 등)을 계속 찍으므로 남겼다.
- **긴 캡처 그림자 인공물(A 8)**: 캡처 인공물이 맞다 — 그림자 광원이 창 맨 위(lightY 0, lightZ 600dp)라 h6000dp 창 아래쪽 카드는 그림자가 10dp 이상 밀려 겹친 카드처럼 보였다. 광원 값은 비공개 테마 속성이라 바꿀 수 없어서 **쪽 단위 캡처**로 바꿨다: 내용은 8000dp(200%는 12000dp) 칸에 한 번 배치하고, 기기 높이 창(h700dp)에 쪽마다 끌어올려(graphicsLayer) 찍은 뒤 이어 붙인다. 그림자가 실기기와 같고, 16,384px 렌더 한계에 걸리던 sdk31 200% 쉬운 모드 17(입국 카드 확인)의 아래쪽도 끝까지 찍힌다(E 지적). `shadow_check.txt`: 스펙 delta 9 / 대조군 15 — 보임.
- 시험용 보고 추가: A11yAudit이 화면마다 **한글 낱말 한가운데 줄바꿈·한 음절 줄**을 `build/a11y/word-breaks-*.txt`에 남긴다(실패 아님). 이 보고로 찾은 것 — 괄호·따옴표 묶기 부작용, 여권 단계 `칩 확인(선/택)`, 예약 입력 안내 `적어/요`, 입국 카드 선택지 `아파트·레/지던스`, 부품 페이지 본문 — 을 고쳤다.

## 3. 8장 2단계 항목
- **배선**: `HomeActions.openHelp` → 도움 탭(`switchTab(Tab.Help)`), 테스트 `homeHelpCardOpensHelpTab`. 1단계가 더한 다른 기본값 액션은 없음(전수 diff 확인). 나라 화면 `openHelp`·`openLink`는 원래 배선돼 있음.
- **@Deprecated 위임 함수 7개 + `CardTone.Notice` 삭제**(사용처 0 확인): `pack.displayDate`, `pack.importColors`·`importLabel`·`ImportTag`·`ReturnCheckCard`, `settings.LocalOnlyBanner`, `home.PhotoTopCard`.
- **문자열**: 사용처 0인 키 **59개 삭제**(옛 탐색 화면 `explore_*`, 옛 설정 행, 1단계가 안 쓰게 된 `country_help_*`·`country_videos_*`·`settings_photos*`·`photo_credits_*`(v1)·`home_return_photo`·`essentials_have` 등). 테스트가 R.string으로 쓰는 키는 없었다. **`rankings_*` 15개는 남김** — PRD의 인기 순위(HUMAN_TASKS C3·C4 데이터 연결 뒤 켜짐) 문구라 `tools:ignore="UnusedResources"`로 보존 표시(운영자 확인 항목).
- **`photo_market`**: 사용처 0 → `drawable-nodpi/photo_market.webp`·`Photos.Market`·`byId("market")`·`photo_credits.json` 항목 삭제. 새 테스트가 번들 사진과 크레딧이 1:1인지 확인.
- **전수 검색**: 화면의 `heightIn(min = 48.dp)` 0건, `sizeIn(48` 0건(남은 `sizeIn`은 설정 글꼴 견본 72dp 최소 크기 — 터치 크기 아님), 고정 `.sp`는 `BottomTabs`의 기본 모드 라벨 최소 10sp(스펙 7장 7번 예외)뿐, `AutofillScreen` 포함. 화면의 맨 M3 `Button`/`OutlinedButton`/`FilledTonalButton`/`TextButton` 0건(IconButton 3곳은 `minTouchSize()`).

## 4. 지킨 조건
쉬운 모드 크기(56dp 터치·64dp 버튼, 엄격 감사), 200%(sdk36·sdk31) 잘림·겹침 없음(감사 ③·캡처 잘림 검사), 대비 테스트(TokenContrast·OnDarkPairs), 출처·최종 확인 줄(접힘 밖), 정부 비제휴·`제출은 직접`, 보안 배너·FLAG_SECURE(DestructiveConfirm secure 포함), 가로 스와이프 없음, 새 네트워크 라이브러리 없음. 화면의 공개 시그니처는 맨 뒤 기본값 인자만 더했다(`StatusTag(…, display, icon)`).

## 5. 운영자 확인 항목 (3단계 컨펌 때 함께)
1. **스펙 8장 3단계 5번**: D1 글꼴(Pretendard Std — 테마 주석상 2026-10-01 승인, PRD 기록 필요) · D3 PRD 5장 `모서리 14~20dp` 문구 → 8/12/16/20/28 · D6 PRD 5.11 도움 순서 보완 · **D19 쉬운 모드 28sp 초과 예외**(displayLarge 56·displayMedium 36·displaySmall 32·stat 34·localLarge 56·localMedium 32) · 태국 팩 기간 문장 모순(부록 A, 팩 수정은 ARIA/운영자).
2. **0단계**: 흰 카드 그림자 보정(그대로 / 더 옅게 / LineSoft 1dp 병행) · IconTile 세로형 셰브론 오른쪽 위(스펙 4.9 문구) · 스펙 문구 정정(3.2 'API 33 이상에서만', `최\u2060종` WJ, 부록 B `fact_label_*`의 `\n`).
3. **A ①~⑧**: ① 홈 출처 줄 같은 앞부분 이름 합치기 ② `내 여행 보기` 버튼 Luggage 아이콘 ③ 히어로 2열 280dp·1열 160dp ④ 도움 바로가기의 오프라인 칩 뺌 ⑤ 신뢰 칩 같은 폭 3칸·1열은 소개 문장 대신 칩 ⑥ ChoiceCard 큰 글자 세로 배치(2단계에서 공용 부품이 됨) ⑦ 첫 실행 질문 200% headlineMedium ⑧ 큰 글자 장식 아이콘 빼기(2단계: StepList 단계 아이콘만 빼고 사진 머리 아이콘은 첫 줄 맞춤으로 되살림).
4. **B**: e-VOA 신청 카드가 있으면 All Indonesia 양식 카드 버튼을 보조로 낮춤(화면당 주 버튼 하나)·그 카드 본문 생략 · 비자 카드 제목을 팩 `visa` 값에서 만든 결론으로(스펙에 없던 결정) · 영상 제목 배지 320dp 미만 생략.
5. **C**: 09 좁은 창(340dp 미만) 지금 할 일 머리글·설명 생략 · **10 여행 요약 타일(`11월 3일 · 화요일에 떠나요`, `4박 5일`)은 검토 반영으로 지웠음** — 다시 넣을지.
6. **D**: `원어민 검수 전` Update 아이콘(2단계 반영됨) · 가는 곳 카드에서 현지어 주소 뺌·기사님 카드에만 크게(스펙 6-19와 다름) · 기사님 전체 화면은 주소가 가장 큰 글자 · 쇼핑 카드 `담기`만 테두리 버튼·`직원에게 보여주기`는 글자 버튼 · 쇼핑 맨 위 반입 판정 요약 칩 뺌(M8UiTest 851dp 예산) · **`담았어요 ✓` + 체크 아이콘이 두 번** — 문구(`shopping_in_cart`, 테스트가 찾음) 변경 승인 필요.
7. **E**: 16 진행 카드를 사진 위 112dp 띠로 · 17 빈칸 이름 태그는 넓은 화면만 · 직접 입력 카드 `빈칸 N개` 태그 위치(제목 아래) · 22 개인정보는 링크 줄 분리 · 스펙 6-17 `isError`에 "손댄 뒤부터 오류" 문구 보완(문서).
8. **F**: NFC 칩 확인 묶음을 값 확인 화면 맨 아래 · 직접 입력 여권 태그 주의 색 · 보여 주기 머리글은 칩 때문에 직접 구성(이제 AppScreen `headerActions`가 TrailingFlow라 공용으로 되돌릴 수 있음).
9. **2단계 판단**: `rankings_*` 문구 15개 보존 · ReturnCheckCard compact(홈)의 안내 본문은 스펙 4.15대로 유지(A 요청 4 — 빼면 홈 카드가 짧아짐) · 1열 200%에서 CardNewsCard(Navy)·PhotoHeaderCard 제목 한 단계 작게(A 요청 5)와 PhotoChip 여백 8dp(A 요청 6)는 하지 않음 · 괄호·따옴표 NBSP 묶기 폐기(§2.1) · 글자 옆 아이콘 최대 1.5배.
10. **아직 못 한 확인**: 실기기(S10 5G)·에뮬레이터 시각 점검 — 글자 200%, 쉬운 모드, TalkBack 한 바퀴, 여러 줄 태국어 18·19·20 전체 화면(8장 2단계 마지막 줄). 이번 확인은 Robolectric 캡처(sdk36·sdk31 200%)로만 했다.

## 6. 빌드
- 마지막 빌드: `BUILD SUCCESSFUL in 4m 14s`
- 캡처: `app/build/gallery/{basic,easy,sdk31_font200/basic,sdk31_font200/easy}/` 41장씩 + `tiles/`, 줄바꿈 보고 `app/build/a11y/word-breaks-*.txt`.
