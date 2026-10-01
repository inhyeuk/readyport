# 재검토 수정 A(공용 부품·일관성) 보고 — 2026-10-01

- 브랜치: `claude/design-refresh` (로컬 커밋만 — 푸시·Play Console·Firebase·GitHub·`packs/` 손대지 않음)
- 입력: `REREVIEW_SUMMARY.md` A 묶음(R1~R11), `rereview/1~5_*.md`, `DESIGN_SPEC_2026-10-01.md` 2~5장, `STAGE0_REPORT.md`, `STAGE2_REPORT.md`
- 스펙 갱신: `DESIGN_SPEC_2026-10-01.md` **부록 C — 재검토 반영(A)** (부품 카탈로그·선택 규칙·반응형 판정·긴급 번호·출처 줄·아이콘 맵)

## 1. 결과

| 항목 | 수정 전 | 수정 후 |
|---|---|---|
| `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | BUILD SUCCESSFUL | **BUILD SUCCESSFUL** |
| 단위 테스트 | 276개 / 60 클래스, 실패 0 | **288개 / 63 클래스, 실패 0** (새 12) |
| lint 오류 / 경고 | 0 / 11 (UseKtx 3·UnusedAttribute 2·IconXmlAndPng 2·InlinedApi·OldTargetApi·ObsoleteSdkInt·TypographyDashes 각 1) | **0 / 11** (기준선과 같은 11건, 새 경고 0) |
| 코드의 글자 배율(fontScale) 판정 | 36곳 흩어짐(`1.3f`·`1.5f`·`1.8f` 리터럴, `largeFont()`·`hugeFont()`, 화면별 상수) | **Layout.kt 한 곳**(`layoutInfoOf`) — 화면·부품의 fontScale 리터럴 0건 |

새 테스트: `components/SharedComponentsFixATest.kt` — 순수 규칙 4(`SharedComponentRulesTest`: 반응형 판정 기준·출처 기관 묶기·품목 아이콘·꺾쇠 뒤로) + 기본 글자 6(`SharedComponentsFixATest`: InfoChip 누를 수 없음·SelectableCard 라디오·KeyValueRow 가린 값·끝 버튼·`최종 확인`↔날짜 한 줄·FitText 단계·테마 판정) + 200% sdk31 2(`SharedComponentsFixALargeFontTest`: 도움 줄 설명 유지·단계 아이콘 인라인). 바꾼 테스트: `BundleDStateCaptureSdk31Test.phoneNumbers*`(번호 **한 줄** 단언으로 강화), `SourceNamesTest`·`CountryDesignTest`(기관별 출처 묶기), `HomeFormatTest`(공용 `sourceLines`로).

## 2. 항목별 처리

### R1 누를 수 없는 칩이 버튼처럼 보임 → `InfoChip`
- **API**: `InfoChip(text, icon, modifier, value: String? = null, tone: BadgeTone = Neutral, onDark: Boolean = false, textStyle: TextStyle? = null)` (CardNews.kt). 아이콘(iconSmall+4, 글자를 따라 커짐, 첫 줄 맞춤) + 글자. **채움·테두리·클릭 없음**, 칩 하나를 한 번에 읽음(mergeDescendants). `value`가 있으면 `값(굵게) 라벨`. `onDark`면 아이콘·글자 모두 Surface(사진 스크림 위 흰 글자 5.74:1 이상).
- `FactChip(fact)`은 이제 InfoChip 모양(AccentSoft 채움 칩 → 채움 없음). 호출부 그대로(18 여행 준비 `무료 입국 카드 비용`, 17 여행 고치기 `N박 N+1일`).
- **옮긴 곳**: 홈 히어로 신뢰 표시 3개(흰 92% 상자 `TrustCell`·폭 측정 `TrustStrip` → `FlowRow` 안 `InfoChip(onDark)`), 홈 `꼭 챙길 물건` 플러그·전압·보조배터리, `ReturnCheckCard(compact)` 주제 칩(면세 한도·과일·식물·고기·축산물), 부품 페이지·흰 사진 최악 페이지.
- 누를 수 있는 것과 구분: 누르는 칩 = `SelectChip`(흰 바탕 + 1dp LineStrong/선택 Accent 채움), tonal 버튼 = 채움 + 1dp 테두리. 정보 칩 = 아이콘 + 글자뿐.

### R2 선택 표시 두 계열 → `SelectableCard` + 규칙 하나
- **API**: `SelectableCard(selected, onClick, modifier, role = RadioButton, leading, vertical = false, selectionMark = true, minHeight = minTouch, shape = medium, contentPadding, content)` + `SelectionMark(selected)`·`selectionIconTint(selected)`·`selectionBadgeContainer(selected)` (Controls.kt).
- **규칙(부록 C.2)**: ① 칩·세그먼트·작은 타일 = Accent 채움 + 흰 글자 + Check ② 큰 카드·폭 전체 행 = 선택 AccentSoft + 2dp Accent + 채운 CheckCircle / 비선택 흰 바탕 + 1dp LineStrong + 빈 원. AccentSoft 채움은 '선택됨'에만.
- **옮긴 곳**: 17 여행 고치기 나라 카드(`CountryRadioCard`), 20 입국 카드 선택지(`ChoiceField` — M3 RadioButton·지역 테두리 대신, 첫 행 focusRequester는 modifier로), 24 도움 `PhraseTile`(비선택 그림자 → 1dp LineStrong + 빈 원). 00 첫 실행 `ChoiceCard(emphasized)`는 선택처럼 보이지 않게 흰 바탕 + 2dp Accent 테두리 + 그림자로.
- 테스트가 찾는 글자(`관광 · Tourism · ท่องเที่ยว`, 문장 ko)는 그대로 단독 노드.

### R3 라벨-값 행 다섯 벌 → `KeyValueRow` 슬롯
- **API**: `KeyValueRow(label, value, modifier, subLabel, badge, onDark, leading: ImageVector?, trailing: (@Composable)?, subLabelInline, subLabelStyle: TextStyle?, valueStyle: ValueStyle(Default/Large/Stat), masked, supporting, verticalPadding = 8.dp)` (Lists.kt).
  - `trailing`(복사 버튼 등)은 옆에 두면 라벨·값이 더 꺾일 만큼 좁으면 값 아래 줄로(TrailingFlow — 실제 폭 기준, 도움말 길이는 보지 않아 행마다 버튼 자리가 들쭉날쭉하지 않음). 누르는 끝 요소는 자기 이름을 가진다(행에 병합되지 않음). `supporting`(도움말)은 그 아래 폭 전체.
  - `masked`: 화면 글자(`L••••••C3`)는 그대로, TalkBack은 새 문자열 `kv_masked_cd` `가려 둔 값`.
- **옮긴 곳**: 20 `ValueRow`(현지어 크게 분기 → `subLabelStyle = headlineMedium`), 21 수동 모드 `CopyRow`(`FieldNames`+값+도움말+복사, `roomy`/`largeFont` 분기 제거 → `subLabelInline`·`supporting`·`trailing`), 30 지갑 `PassportField`(`onDark`·`ValueStyle.Large`·`masked`)·`BookingFact`(`leading`), 26 보여 주기 `ConfirmationNumber`(`ValueStyle.Stat`)·가린 이름/여권번호 사실(`masked`). 32 여권 값 확인은 원래 KeyValueRow(badge).

### R4 '급할 때는 도움' 두 벌 + ListRow 여백 16 고정
- **토큰**: `ReadyPortDimens.listRowPadding`(가로 20/24 = cardPadding)·`listRowPaddingVertical`(16/16).
- `ListRow`: `padding(16)` → 토큰(배지가 다른 카드 내용과 같은 시작선), `extra` 슬롯(설명 아래 장식) 추가, 큰 글자 배치는 `isStackedLayout()`.
- `ListDivider(indent = !isStackedLayout())`: 들여쓰기 = listRowPadding + iconBadge + 16, 큰 글자면 자동으로 0.
- 가로형 `IconTile` 안쪽 여백도 같은 토큰(18 여행 준비 `꼭 챙길 물건` 어긋남).
- **신규** `HelpShortcutRow(onClick, modifier)` = 흰 그림자 카드 안 ListRow 하나. **옮긴 곳**: 홈(`HomeScreen.HelpShortcut` 삭제), 내 여행(지역 `ListGroup{ListRow}` + 180%↑ 설명 숨김 삭제), 설정(`SettingRow` 몸통 → ListRow, `rowTextStart`·링크 들여쓰기도 토큰).

### R5 반응형 판정 36곳 → Layout.kt 한 곳, 내용 숨기지 않기
- **API(Layout.kt)**: `enum LayoutClass { Roomy, Compact, Stacked }`, `data class LayoutInfo(widthDp, textScale, columns, layoutClass)`(+`stacked`·`narrow`), 순수 함수 `layoutInfoOf(widthDp, textScale, easyMode, screenPaddingDp, gapDp)`, `LocalLayoutInfo`, `ProvideLayoutInfo`(ReadyPortTheme이 한 번 호출), `rememberLayoutInfo()`·`rememberLayoutClass()`·`isStackedLayout()`·`isNarrowWindow()`·`rememberGridColumns()`(이제 LayoutInfo를 읽는 얇은 함수).
- 글자 배율 = 본문(bodyLarge) 실제 크기(dp) ÷ 그 sp 값(시스템 글자 크기 설정의 실제 배율, API 34+ 비선형 포함 — 쉬운 모드 글자 크기는 넣지 않음). 열 수 기준(D4)은 그대로, `Stacked` = `(창 폭 − 40) ÷ 글자 배율 < 272`(393dp에서 예전 130%와 같음, 폭이 좁으면 더 일찍).
- **지운 것**: `largeFont()`·`hugeFont()`·`LARGE_FONT_SCALE`·`HUGE_FONT_SCALE`, `choiceCardStacked()`, FirstRun `HERO_SMALLER_SCALE`, 홈 `1.5f`·`LARGE_FONT_SCALE`, 내 여행 `1.8f`, 영상·세그먼트·내 여행의 창 폭 직접 계산.
- **옮긴 곳**(→ `isStackedLayout()`/`isNarrowWindow()`): Banners(보안 띠 2곳), Lists(ListRow), Sources(SourceFooter), Tiles(ChoiceCard), CardNews(StepList), Controls(ChoiceSegments), Home(꼭 챙길 물건 진행·여행 카드), FirstRun(히어로), Settings(링크 들여쓰기·사진 출처), Essentials(물건 카드), Autofill(안내 높이·위 고지 2곳), FormConfirm(직접 고를 칸 머리), ManualMode(복사 버튼 — 이제 부품이 폭으로 정함), Videos·Today(좁은 창).
- **내용을 숨기던 곳 → 배치 변경**: 홈 히어로 소개 문장(1열에서 빠짐 → 2열은 히어로 안, 1열은 나라 사진 목록 바로 아래 안내 줄 — 첫 화면 예산 `HomeFirstScreenTest`를 지키며 숨기지 않음), 내 여행 도움 줄 설명(180%↑ 삭제 → 배지·셰브론 윗줄 + 설명 폭 전체), StepList 단계 아이콘(130%↑ 삭제 → `LeadIconText`로 글 첫 줄 안), 홈 여행 카드 큰 숫자(150%↑ statSmall 고정 → `FitText`로 칸에 맞춤).

### R6 긴급 번호가 하이픈에서 두 줄 → 칸 폭에 맞춘 한 줄
- **API**: `FitText(text, styles, color, modifier, breakChars = "-")` (FitText.kt, 신규) — TextMeasurer로 실제 칸 폭을 재서 한 줄에 들어가는 첫 스타일로 그림(직접 그리기 + `semantics { text; getTextLayoutResult }`). SubcomposeLayout을 쓰지 않아 TileGrid의 `IntrinsicSize.Min` 안에서도 동작(고유 크기 계산 구현). 가장 작은 크기로도 넘치면 `breakChars` 뒤에서만 여러 줄.
- `PhoneNumberText(number, color, modifier, styles = phoneNumberStyles())` = FitText, 단계 stat → statSmall → titleLarge → titleMedium → bodyLarge → bodySmall 크기(굵게·tnum, 쉬운 모드 최소 18sp). 예전 FlowRow 묶음(`PHONE_GROUP_TAG`)은 지움(`phoneGroups()`는 남김).
- 도움 화면: 큰 글자 배치에서 대사관·영사콜센터 타일을 **카드 밖 폭 전체**로 꺼내고 출처를 그 아래에(`isStackedLayout()`).
- **테스트**: `BundleDStateCaptureSdk31Test.phoneNumbersBasic/Easy`(393dp·sdk31·200%) — 모든 번호 `lineCount == 1`, 칸보다 넓지 않음, 최소 글자 크기 이상. `SharedComponentsFixATest.fitTextStepsDownToStayOnOneLine` — 좁으면 작은 크기로 한 줄, 아주 좁으면 `-` 뒤에서만 줄바꿈.

### R7 긴급 타일 색 → Help 하나
- `emergencyColors(large = true)` = Help 채움 + Surface(6.03)·White85(4.81) — `OnDark.allowedOn(Help)` 추가(White80 4.45는 금지). 대표 번호(1155)도 주황 계열, Navy는 보안·현지인에게 보여 주기·오프라인에만.
- 쇼핑 카드·담아 둔 물건 카드의 Help 톤 → 기본(Accent), 품목 배지 Help → Neutral(22 쇼핑 리스트) — 주황은 긴급·도움에만.

### R8 불릿 기호 → 뜻 없는 점
- **API**: `DotBullet(text, modifier)` (CardNews.kt) — 6dp InkTertiary 점, 첫 줄 가운데, IconBullet과 같은 글 시작선.
- **옮긴 곳**: 나라 화면 팩 섹션 카드(들어갈 때 ✓ · 돈·안전 대시 → 모두 점). ✓는 앱이 확인한 상태에만(규칙을 부품 문서·스펙에 기록).

### R9 출처 줄 → 기관별 묶기, 날짜 한 번, 날짜 홀로 남지 않게
- `sourceLines(refs)`: 기관별 한 줄(` · ` 앞부분 / 이름 전체가 다른 출처의 기관 / 공유하는 첫 낱말), 세부 `, `, 이름 빠짐 없음, 기관 안 날짜가 다르면 나눔.
- `sourceBlocks(refs)`(신규): 같은 날짜 기관 줄을 한 덩어리 `출처 A⏎B⏎C · 최종 확인 날짜`(형식은 `source_footer` 그대로). `SourceList`가 사용.
- `source_footer`: `확인`과 날짜 사이 보통 띄어쓰기 → **NBSP**. `SourceFooter`는 그 덩어리가 한 줄보다 긴 좁은 줄에서만 그 자리를 풀어 날짜를 통째로 다음 줄에(`looseDateBreak`·`dateChunkBroken`, 폭이 바뀌면 다시 묶음). 큰 글자 아이콘 인라인도 유지.
- **옮긴 곳**: 홈 `compactSourceRefs` 삭제(공용으로 같은 결과), 내 여행 담아 둔 물건 `STACK_SOURCES_OVER` 줄 쌓기 삭제(6줄 → 기관 3줄 한 덩어리).
- **테스트**: `verifiedLabelAndDateStayTogether`(칸 폭 200~380dp 31단계에서 `최`와 날짜 끝이 같은 줄), `sourceLinesGroupByAgency`, 기존 `sourceFooterDateNeverSplits`·`EssentialsLargeFontTest`·`OfflinePackTest`(한 출처 형식) 통과.

### R10 사진 동기 디코드 → 크기 맞춤 + 백그라운드 + 캐시
- `PhotoCache`(앱 전체 LruCache, 최대 24MB/힙 1/8) + `rememberPhoto(res, targetWidthPx, square)`: 헤더로 원본 크기(캐시)를 읽어 inSampleSize를 고르고, 캐시에 있으면 바로, 없으면 `produceState` + `Dispatchers.IO`로 디코드. `PhotoBox(photo, …, widthFraction = 1f)`(2열 나라 타일 0.5), `PhotoHeaderCard(photo)`, `rememberThumbnail`(정사각 썸네일)이 모두 이 길로. `painterResource` 사진 사용 0건.
- JVM 테스트(Robolectric, `Build.FINGERPRINT == "robolectric"`)에서만 즉시 디코드 — 캡처·감사가 디코드를 기다리지 않아 사진 대신 남색 칸이 찍히는 일이 없게. 실기기는 언제나 백그라운드.
- 남긴 것: `ListRow`·`KeyValueRow`의 intrinsic 측정을 SubcomposeLayout 1회로 바꾸는 최적화는 하지 않았다(IntrinsicSize.Min 그리드와 함께 쓰는 곳이 있어 SubcomposeLayout 불가 — 측정 비용은 실기기 프로파일 후 판단).

### R11 아이콘 정리
- 같은 개념 한 아이콘: `꼭 챙길 물건` = `IconKeys.essentials`(Checklist — 홈 Backpack·꼭 챙길 물건 Luggage 대체), `최신순` = `IconKeys.sortRecent`(CalendarMonth — NewReleases 대체).
- 품목 아이콘 `IconKeys.item(id, category)`: 커피·차·과자·생과일·절임·잼·육포·장신구·직물·가죽·문구를 구분(나머지는 분류 아이콘). 06·15·22 모두 Neutral 배지 + 품목 아이콘.
- 버튼 앞 꺾쇠: 부품 규칙 — `NavigateNext`·`ArrowForward`·`ChevronRight`는 라벨 **뒤**에 그린다(`isTrailingOnlyIcon`). 뜻 아이콘으로 바꾼 곳: 나라 쇼핑 `쇼핑 리스트 보기` → ShoppingBag, 입국 카드 `첫 빈칸으로 가기` → ArrowDownward. 내 여행 `여행 만들기`·`여권 등록하기`는 꺾쇠가 라벨 뒤로.
- 경고 문장에 체크 금지: R8(점 불릿).
- 스펙 5장 갱신표는 부록 C.6.

## 3. 지킨 조건
쉬운 모드 크기(56dp 터치·64dp 버튼 — 엄격 감사 통과), 200%(sdk36·sdk31) 잘림·겹침·한 음절 줄 없음(A11yAudit 4구성·줄바꿈 보고), 대비 테스트(TokenContrast·OnDarkPairs — Help 바탕 onDark 쌍 추가), 출처·최종 확인 줄(접힘 밖, 이름 빠짐 없음), 정부 비제휴·`제출은 직접`·보안 배너 위치, 가로 스와이프 없음, 새 네트워크 라이브러리 없음. 테스트가 찾는 문자열·TalkBack 이름은 그대로(새 문구는 `strings_design_v2.xml`의 새 키 `kv_masked_cd` 하나).

## 4. B 묶음(화면) 작업자에게
- **반응형은 Layout.kt만 읽는다**: `isStackedLayout()`·`rememberGridColumns()`·`isNarrowWindow()`·`rememberLayoutInfo()`. `LocalDensity.current.fontScale`·숫자 기준을 화면에 쓰지 않는다. 큰 글자·쉬운 모드에서 **글을 지우지 말고** 배치를 바꾼다(배지·끝 요소 윗줄, 1열, `LeadIconText`).
- **누를 수 없는 표시는 `InfoChip`**(R13 히어로 가치 문장 옆, R14 사진 머리 카드의 사실, R19 나라 카드 칩 구성 통일 등). 상태(가능·주의·불가)는 `StatusTag`. 채운 알약을 새로 만들지 않는다.
- **선택은 `SelectChip`(작은 것) / `SelectableCard`(큰 카드·폭 전체 행)** 둘 중 하나. 화면에서 AccentSoft·테두리를 직접 칠하지 않는다.
- **라벨-값은 `KeyValueRow`**(슬롯: leading·trailing·subLabelInline·subLabelStyle·valueStyle·masked·supporting). R16 입국 카드 '빈칸 N개' 정리 때 행마다 `꼭 채워요` 태그 대신 작은 표시가 필요하면 `badge` 슬롯이나 라벨 끝 표시로.
- **팩 문장 목록은 `DotBullet`**. R17(05 안전) 위험 배너는 `NoticeBanner(Danger)` 슬롯 위에, 문장 불릿은 점 그대로.
- **출처는 `SourceList(refs)`에 전부 넘긴다** — 기관별 묶기·날짜 한 번은 부품이 한다(R14 15 귀국 출처 6줄 문제는 이미 3줄 한 덩어리). 화면에서 이름을 `, `·`\n`로 직접 잇지 않는다.
- **R12 비자 카드**: Accent 채움 해제 시 `newsColors(NewsStyle.Surface)`로. `IDR 500,000` 두 줄 방지는 `FitText(value, listOf(stat, statSmall))`(StatTile 안 값)로 칸에 맞추거나 통화 코드를 eyebrow로.
- **R13 홈**: 소개 문장(`home_subtitle`)은 2열에서 히어로 안, 1열(쉬운 모드·큰 글자)에서는 나라 목록 아래 `IconBullet` 안내 줄(`item(key = "intro")`)로 옮겨 두었다 — 히어로에 넣으면 쉬운 모드 첫 화면 예산(`HomeFirstScreenTest`: 쉬운 모드+오프라인 일본·쉬운 모드 200% 태국 타일 20dp 이상)이 깨진다(지금 65dp·78dp). 가치 문장(새 키)을 넣을 때 소개 문장과 겹치면 하나로 합치고 자리를 다시 정한다. 신뢰 표시는 히어로 안 `InfoChip(onDark, textStyle = labelMedium)` FlowRow.
- **R14 15·16**: 사진 머리 카드는 `PhotoHeaderCard(photo)`(백그라운드 디코드). 요약 숫자 타일은 `FactGrid`/`StatTile`, 값이 길면 `FitText`.
- **R15 19**: 완료 행 정리 시 체크는 '앱이 확인한 상태'라 ✓ 그대로 써도 된다(R8 규칙은 팩 문장 불릿에만).
- **R18 TalkBack**: `DangerButton`·`ExpandableDetail`에 대상 이름을 붙이는 일은 이번에 하지 않았다(부품 시그니처 변경 없이 화면에서 새 문자열로).
- 버튼 아이콘: 앞에는 뜻 아이콘, 꺾쇠를 넘기면 부품이 라벨 뒤로 옮긴다.
- 쇼핑 품목 아이콘은 `IconKeys.item(id, category)` + `BadgeTone.Neutral`.

## 5. 운영자 확인 항목 (추가분)
1. **Stacked 기준이 창 폭을 본다**: 360dp 창에서는 약 118%부터 배지·끝 요소가 윗줄로 간다(예전은 폭과 무관하게 130%). 393dp 기준 결과는 같다.
2. **FactChip 모양 변경**(채움 칩 → 채움 없는 아이콘 + 글자): 18 여행 준비 `무료 입국 카드 비용`, 17 여행 고치기 `N박 N+1일`도 같이 바뀐다.
5. **예전 150%(hugeFont) 기준을 Stacked(130% 상당)로 합침**: 설정 사진 출처 썸네일을 글 위로, 첫 실행 히어로 질문을 한 단계 작게 하는 시점이 150% → 130%로 당겨졌다.
6. **1열 홈 소개 문장 위치**: 나라 목록 아래 안내 줄(첫 화면 예산 때문 — 최종 자리는 R13에서).
3. **대표 긴급 번호 타일 Help 채움**(흰 글자 6.03:1): 시각 확인.
4. 실기기(S10) 확인 필요: 사진 백그라운드 디코드 직후 한 프레임 남색 바탕이 보일 수 있음(캐시 뒤에는 없음).

## 6. 빌드
- 마지막 빌드: `BUILD SUCCESSFUL in 4m 26s` (`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`)
- 캡처: `app/build/gallery/{basic,easy,sdk31_font200/basic,sdk31_font200/easy}/` 41장씩 + `tiles/` — 홈(신뢰 표시·1열 소개 문장), 도움(Help 대표 타일·선택 카드·200% 폭 전체 번호 타일), 입국 카드 선택지, 수동 모드 복사 행, 지갑 여권 카드, 귀국 출처 묶음, 나라 들어갈 때 점 불릿을 눈으로 확인했다.
- 첫 화면 예산(`HomeFirstScreenTest`): 쉬운 모드 200% 태국 타일 78dp, 쉬운 모드+오프라인 일본 65dp(전 28dp), 320×470 114dp(전 74dp) — 모두 20dp 이상.
- A11yAudit 줄바꿈 보고·보이는 크기 미달 보고: 모두 0건.
