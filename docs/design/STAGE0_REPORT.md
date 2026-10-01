# 디자인 개편 0단계(기반) 작업 보고 — 2026-10-01

- 브랜치: `claude/design-refresh` (커밋·푸시는 오케스트레이터가 한다)
- 기준 문서: `docs/design/DESIGN_SPEC_2026-10-01.md` 8장 0단계 (v2.1)
- 범위 밖으로 남긴 것: 글꼴(0b단계, 운영자 승인 전 — 파일 다운로드 없음), packs/·Play Console·Firebase·GitHub 작업 없음

## 1. 결과

| 항목 | 0단계 전(기준선) | 0단계 후 |
|---|---|---|
| `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | 실패(A11yAudit 3개) | **BUILD SUCCESSFUL** |
| 단위 테스트 | 150개 중 3개 실패 | **178개, 실패 0** (테스트 클래스 33개, 검증 반영 후) |
| lint 오류 | 0 | **0** |
| lint 경고 | 53건 = UnusedResources 41 + 그 밖 12 | 90건 = UnusedResources 78 + 그 밖 **12(기준선과 같음, 새 경고 0)** (검증 반영 후) |

- 기준선 lint는 스펙에 54건으로 적혀 있지만 실제 보고서는 53건이었다(UnusedResources 41건은 같음).
- UnusedResources 증가(+37)는 부록 B 새 문자열 70개 중 아직 화면이 쓰지 않는 키다(스펙 8장: 허용).
- 기준선 A11yAudit 실패 3건 = `form/FormConfirmScreen.kt` 입력칸(OutlinedTextField) 이름 없음 → 처음엔 `label` 한 줄로 고쳤고, 검증 반영에서 스펙 6-17대로 `FieldLabel`을 빼고 `supportingText`로 옮겼다(8절 V5).
- 갤러리 화면 40개(기존 29 + 새 항목 11 — 부품 페이지가 4장이 됨).

## 2. 절별 구현 내용

### 3.1 색 — `ui/theme/Tokens.kt`
- 기존 토큰 값은 그대로. 추가: `AccentDeep, BrandBlue, InkTertiary, SurfaceSunken, SurfaceHighest, SurfaceLow(#FAFBFC), LineStrong, HelpSoft, VioletSoft/Text, TealSoft/Text, Gold, White85, White80, White12(OnDark 배지 바탕), PhotoChipBg(흰 92%), PhotoButtonBg(검정 35%), PhotoTint(검정 18%), ShadowAmbient(Ink 8%), ShadowSpot(Ink 12%)`.
- `BadgeTone` 10종(OnDark 포함)과 onDark 허용 색 목록 `OnDark.allowedOn(bg)`은 `components/IconBadge.kt`.
- ColorScheme(`Theme.kt`): 3.1 표의 역할 전부 지정(`surfaceContainer*`, `surfaceDim/Bright`, `surfaceTint = Surface`, `tertiary* = Help 계열`, `inverse*`, **`outline = LineStrong`**, `outlineVariant = Line`, `scrim`).
- `CardTone.Notice`는 `@Deprecated`(모양 그대로), `Neutral` = 테두리 없음 + 그림자, `Caution` = 테두리 대신 왼쪽 4dp CautionBorder 막대.
- `res/values-v29/themes.xml`: `android:forceDarkAllowed=false`.

### 3.2 글자 — `Theme.kt`
- `typography(easy)`: display L/M/S, headline L/M/S, title L/M/S(신규 titleSmall), body L/M/S, label L/M/S를 표 값대로(쉬운 모드 값 포함). 모두 `PlatformTextStyle(includeFontPadding = false)`.
- 줄바꿈: 제목·버튼·라벨 = `LineBreak.Heading`, 본문 = `ReadyPortLineBreak.Body`(HighQuality·Strict·Phrase).
- `ReadyPortTypeExtras(stat, statSmall, localLarge, localMedium)` + `LocalTypeExtras`(stat은 tnum, local은 행간 1.5배 + `LineHeightStyle(Center, Trim.None)`).
- `source_footer`: `최종`↔`확인`, `확인`↔날짜를 NBSP로, `확`·`인` 사이 U+2060. **추가로 `최`·`종` 사이에도 U+2060**(7장 편차 참고).

### 3.3 모서리 / 3.4 간격 / 3.5 층위
- `Shapes(8, 12, 16, 20, 28)`. 부품의 버튼은 모두 `shapes.medium`.
- `ReadyPortDimens` 확장(기본/쉬운): screenPadding 20/20, cardPadding 20/24, gap 12/16, sectionGap 32/40, inner 8/12, buttonHeight 56/64, minTouch 48/56, iconBadge 40/52, iconBadgeSmall 32/40, icon 24/28, iconSmall 16/20, tileMinHeight 112/128 + 보조 필드 `tileRowMinHeight 72/88`, `listRowMinHeight 64/72`, `stepBadge 28/36`.
- 흰 카드 그림자 `Modifier.cardShadow(shape)`(2dp, Ink 8%/12%), 상태 막대 `Modifier.startBar(color)`(RTL이면 오른쪽).
- **쉬운 모드 56dp**: `ReadyPortTheme`이 `LocalMinimumInteractiveComponentSize`와 `LocalViewConfiguration.minimumTouchTargetSize`를 `minTouch`(48/56)로 준다 — M3 부품은 자리가, 그 밖의 clickable은 터치 영역이 56dp가 된다(묶음 파일을 고치지 않고 강화된 감사를 통과). 화면의 `heightIn(min = 48.dp)` → `minTouch()` 교체는 그대로 1단계 각 묶음 일이다.

### 3.7 사진
- `PhotoBox v2`: 사진 전체 검정 0.18 틴트만, `modifier.fillMaxWidth()` 내부 적용, `alignment` 인자, `Painter` 오버로드(흰 단색 최악 캡처용).
- `PhotoTextArea`(BoxScope, 아래 정렬) / `PhotoTextColumn`(Column 안용): 자체 스크림(위 24dp 0→0.60, 아래 0.60→0.88) + 위쪽 padding 28dp(≥24).
- PhotoBox v2가 강한 전체 스크림을 없앴기 때문에, 사진 위에 흰 글자를 바로 올리던 0단계 대상 파일(HomeHero·CountryPhotoCard·쇼핑 사진·CountryHero)은 글자 영역을 `PhotoTextArea`/`PhotoTextColumn`으로 감쌌다(대비 유지 목적, 문구·구조 그대로).
- `assets/photo_credits.json`의 `changes` 9개를 `잘라내고 크기를 줄이고, 글자가 잘 보이게 어둡게 덮었어요(일부는 둥글게 자름)`로. `photo_market`은 아직 쓰이므로 drawable·크레딧 유지(2단계).
- `Photos.byId(id)`, `rememberThumbnail(res, sizeDp)`(inSampleSize, `LocalResources` 사용).

### 3.8 테스트
- `TokenContrastTest`: `fg.compositeOver(bg)` 합성 후 계산. 글자 쌍 39개, 합성 쌍 7개(기대값 ±0.1 — 실측 5.38/7.98/12.19/10.85/7.26/13.42/5.74), 비텍스트 3:1 쌍 9개, `BadgeTone` 10종.
- `OnDarkPairsTest`(신규): ① `newsColors(Accent/Navy)`, `tileColors(emphasized)`, `emergencyColors(large)`, `secondaryButtonColors(onDark)`, `passportCardColors()`가 돌려주는 색이 onDark 허용 목록 안인지 ② `ui/` 소스 정적 검사 — 어두운 채움(CardTone/NewsStyle Navy·Accent, `background(Tokens.Navy…)`, `containerColor =`, `Surface(color =`) 블록 안의 Help·DangerText·InkTertiary·InkSecondary·Accent 글자색과 BrandBlue 위 Gold(흰 버튼 `containerColor = Tokens.Surface` 줄은 제외). 현재 블록 19개, 위반 0.
- `SourceNamesTest`(신규, `ui/components`): 번들 팩 5개 + index의 모든 정책 항목 출처 ID가 이름으로 풀림, `resolveSourceName`이 ID를 돌려주지 않음, `sourceLines` 묶기, 앵커 없는 ID 정규식 `\b[a-z]+(?:_[a-z0-9]+)+\b`. 같은 검사(`SourceTextCheck.leaks`)를 A11yAudit 루프에서 모든 갤러리 화면 글자에 적용.
- `FactValuesTest`(신규): `shortValue` 번들 팩 6건(TH·JP·SG `무료`, ID apply `IDR 500,000`, ID form `무료`, MY `null`) + 규칙 5건, `feeIcon`.
- `ComponentsBehaviorTest`(신규): KeyIndex 기록·`scrollToKey`, `rememberGridColumns`(393dp 기본 2열·쉬운 1열), `minTouch` 48/56, ComingSoonGroup disabled, CountryPhotoTile 설명+stateDescription+칩 글자 유지, SourceList 한 줄 형식, StepList 번호.

### 4장 부품 — `ui/components/`
4.0 표의 파일 전부 + 비평 반영 추가분 전부(아래 6절 API 목록). 부품 공통 규칙(누르는 요소 minTouch·이름, 장식 아이콘 null, 제목 heading, M3 Card/AlertDialog `containerColor` 명시, 단일 선택 Role.RadioButton/Tab, 행 안 컨트롤 콜백 null) 적용.

### 이동 규칙(4.0) — 옛 위치에 `@Deprecated` 위임 함수
| 심볼 | 새 위치 | 옛 위치(위임 유지) |
|---|---|---|
| `displayDate` | `Sources.kt` | `pack/PackScreens.kt` |
| `importLabel`, `importColors`, `ImportTag`(→`ImportVerdictBadge`) | `Status.kt` | `pack/ShoppingScreen.kt` |
| `ReturnCheckCard`(v2) | `ReturnCheck.kt` | `pack/ShoppingScreen.kt` |
| `PhotoTopCard`(→`PhotoHeaderCard`) | `Photos.kt` | `home/HomeScreen.kt` |
| `LocalOnlyBanner`(→`SecurityBanner`) | `Banners.kt` | `settings/SettingsScreen.kt` |
| `OfflineBanner` | `Banners.kt` | (옛 함수가 `ReadyPortRoot.kt` private이라 위임 없이 삭제·import로 교체) |
| `StatusChip`, `SourceFooter(source, date)` | `Status.kt`, `Sources.kt` | 같은 패키지라 import 그대로 |
| `PrimaryButton`, (EasyActionButton) | `Buttons.kt` | `AppScreen.kt`에서 옮김(같은 패키지) |

옛 위치를 쓰는 화면 파일(1단계 묶음 소유)은 deprecation 경고만 나고 그대로 동작한다.

### D17 — 번호 접두 삭제 + 세 곳 StepList
- 문자열: `today_departure_step1` = `공항에 가요`(뒷문장은 새 키 `today_departure_step1_detail`), `today_departure_step2~5`, `today_arrival_step1~5`, `explore_maps_step1~3`의 `N. ` 삭제.
- `HomeScreen` 출국 순서, `TodayScreen` StepsCard 2곳(출국·도착 — 5.3 아이콘), `CountryScreen` 지도 단계(번호만)를 `StepList`로.

### AppScreen(공통 틀)
- 새 인자: `icon: ImageVector? = null`(IconBadge + headlineMedium), `state: LazyListState = rememberLazyListState()`, `keyIndex: KeyIndex? = null`(header·easy-actions·화면 item·sectionGap을 모두 기록). 뒤로 버튼 `minTouchSize()`. 쉬운 모드 `처음으로·소리로 듣기` = `SecondaryButton(fillWidth = false)` + 아이콘.

### 문자열
- `res/values/strings_design_v2.xml`: 부록 B 70개 전부(키 목록을 스펙 표와 기계 대조 — 누락·초과 0, strings.xml과 중복 0). 이후 동결.

### 갤러리·감사
- `Gallery.kt` 픽스처: `EssentialRow` 실제 출처 이름, `ShoppingUi.sourceNames·indexSources`, `TodayUi` 귀국 `indexSources·sourceNames`, `HomeTrip(code = "TH")`. `FakeSlots.helpUi`의 `commonSourceName`을 `common.first().source`로 풀도록 고침 + `indexSources`. `TestPacks.homeUi()`가 `HomeCountry.sourceName`을 채움. `StoreAssetsTest`의 `HomeTrip`에도 `code`.
- 새 갤러리 항목: `today-departure`, `today-wrapup`, `present-unlocked`(문서 1개), `wallet-key-lost`, `passport-intro`, `manual-mode`, `components-1/2/3/4`(부품 페이지, `ComponentsPage.kt`), `photo-worst-white`.
- `A11yAuditTest`: 쉬운 모드는 누르는 요소 전부 56dp, 내부 ID·`출처 출처` 검사 추가, `A11yAuditSdk31Test`(sdk 31·200%, 기본·쉬운) 추가. sdk 31 android-all은 Robolectric이 받아 왔다. 검증 반영으로 NATIVE 그래픽·`ko-rKR`·'화면 잘림' 실패·보이는 크기 보고 추가(8절).
- `GalleryCaptureTest`: `h6000dp`, 매번 폴더를 비우고 새로 찍음, `GalleryCaptureSdk31Test`(sdk 31·200% → `build/gallery/sdk31_font200/`), `shadowCheck`. `app/build.gradle.kts`에 `robolectric.pixelCopyRenderMode=hardware`. 검증 반영으로 `ko-rKR`, sdk 31 높이 h12000dp, 잘림 실패, 타일 재생성, 그림자 보임 단언 추가(8절).

## 3. 그림자 확인 결과 (D2, 8장 0단계)
처음 결과: 렌더러는 그림자를 그리는데(검정 기본 대조군 15단계) 스펙 값(Ink 8%/12%)을 색 알파에 그대로 넣으면 **1단계 차이로 거의 안 보였다** — Android가 그림자 색 알파에 테마의 `ambientShadowAlpha`(0.039)·`spotShadowAlpha`(0.19)를 한 번 더 곱하기 때문(실제 0.3%/2.3%). 실기기도 같다.
**검증 반영(8절 V7)**: 스펙의 8%/12%를 '화면에 보이는 진하기'로 보고 플랫폼 알파로 나눠 보정했다(`Tokens.ShadowAmbient` = Ink 100% — 0.08/0.039는 1을 넘음, `Tokens.ShadowSpot` = Ink 63%). 지금 `shadow_check.txt`: **스펙 delta=9, 대조군 delta=15 → 스펙 그림자 보임**(기본 그림자보다 옅음). `shadowCheck`가 '렌더러가 그리는데 스펙 그림자가 안 보임'·'기본보다 진함'이면 실패한다.
**3단계 운영자 확인 항목으로 남김**: (가) 이 보정 그대로 (나) 더 옅게(예: spot 40%) (다) 아주 옅은 1dp `LineSoft` 테두리 병행.

## 4. 스펙과 다른 점 (이유)
1. ~~`ListGroup(modifier, title, content)`~~ → **검증 반영으로 스펙대로 `ListGroup(title, modifier, content)`**(lint `ModifierParameter`는 이 함수에서만 `@Suppress`).
2. ~~`TileGrid`의 람다 이름 `content`~~ → **검증 반영으로 스펙대로 `itemContent`**(lint `ComposableLambdaParameterNaming`은 이 함수에서만 `@Suppress`).
3. **`scrollToKey`가 `Boolean`을 돌려준다**(key가 없으면 false). 시그니처는 같고 반환만 추가.
4. **`AppScreen(keyIndex)` 인자 추가** — LazyListState만으로는 key→index를 알 수 없어서. `KeyIndex.track(scope)`가 LazyListScope를 감싸 item·items·stickyHeader의 key를 기록한다.
5. **`CountryPhotoTile(…, enabled = true)`** 추가(준비 중 나라는 누를 수 없음 — 기존 동작 유지용), **`KeyValueRow(…, onDark = false)`** 추가(24 여권 카드의 onDark 변형).
6. **`PhotoBox(painter: Painter?)` 오버로드와 `PhotoTextColumn`** 추가(흰 단색 최악 캡처, Column 안 스크림 영역).
7. **`source_footer`에 `최`·`종` 사이 U+2060도** 넣었다 — 캡처에서 `최/종`으로 끊겨서(3.2의 `확/인`과 같은 이유). 테스트는 getString으로 비교해 영향 없음.
8. **IconTile 세로형 셰브론은 오른쪽 위**(배지 줄 끝) — 스펙 4.9는 오른쪽 아래. 2열 칸 글 폭(393dp에서 약 138dp)에서 셰브론(24dp)을 라벨 줄 끝에 두면 7자 라벨이 꺾이고, 따로 줄을 두면 타일 높이가 112dp를 넘는다. **유지 — 3단계 운영자 확인 항목(스펙 4.9 문구 수정 제안)**.
9. **ReturnCheckCard 순서는 4.15대로**(안내 → 사실 행 → 출처 → 링크). CardNewsCard의 `sources`(맨 아래)를 쓰지 않고 출처를 링크 위에 둔다. ~~compact의 주제 요약 줄은 펼쳐도 보인다~~ → 검증 반영: 접혀 있을 때만 보인다.
10. ~~ExpandableDetail 라벨은 열려도 그대로~~ → 검증 반영: 펼치면 `접기`(action_less).
11. **OfflineBanner 옛 위치 위임 함수 없음** — 원래 private이라 다른 파일이 쓸 수 없었다.
12. **CountryHero minHeight 280 → 220dp를 0단계에서 적용**(6-03 항목). 새 토큰(buttonHeight 56, cardPadding 20)으로 320×470 예산이 넘쳐 `ReadyPortRootTest.countryPhotoCardOpensCountryWithSections`가 실패했기 때문. 히어로 글자도 `PhotoTextColumn` 안으로.
13. **`PassportIntroContent` 추출**(F 소유 `wallet/PassportScreens.kt`) — 갤러리 `passport-intro`에 상태 없는 화면이 필요. 동작 그대로(Screen이 SecureScreen·사진 선택·onFound를 맡음).
14. **EmergencyCallTile·passportCardColors()·OnDark 객체는 components에 0단계로 만들었다**(4.0 표에는 없지만 OnDarkPairsTest가 색 선택 함수를 직접 검사해야 해서). PassportCard 자체는 F가 만든다.
15. **갤러리 부품 페이지를 나눔** — 처음 3장, 검증 반영에서 4장(`components-1~4`). sdk 31·200% 쉬운 모드에서도 한 장이 감사·캡처 높이 안에 들어가게(넘치면 이제 테스트가 실패한다).
16. **데이터 필드 미리 추가**(맨 뒤, 기본값): `HomeCountry.sourceName`, `HomeTrip.code`, `TodayUi.sourceNames`, `HelpUi.indexSources` — ViewModel에서도 채운다(화면은 아직 안 씀). `HelpViewModel.commonSourceName` 버그 수정은 D 몫으로 남겼다(픽스처만 올바른 값).

## 5. 1단계 묶음 A–F가 알아야 할 것
**공통**
- 공유 테스트 파일(`Gallery.kt`, `FakeSlots.kt`, `StoreAssetsTest.kt`, `ScreenCaptureTest.kt`, `A11yAuditTest.kt`, `GalleryCaptureTest.kt`, `ComponentsPage.kt`)과 `strings*.xml`은 동결 — 2단계 담당만 고친다.
- 4절 표의 옛 위치 위임 함수·`CardTone.Notice`를 지우거나 시그니처를 바꾸지 않는다. 화면이 새 위치를 import하면 경고가 사라진다.
- 강화된 감사: 쉬운 모드는 누르는 요소 56dp(테마가 대부분 보장하지만 직접 `clickable`을 쓰는 행은 `minTouch()`를 붙일 것), sdk 31·200% 감사·캡처가 함께 돈다. 화면 글자에 `snake_case` ID가 나오면 감사가 실패한다(출처 이름은 `resolveSourceName(id, names, stringResource(R.string.source_official_fallback))`).
- lint에서 피할 것: `@Composable` Modifier 팩토리(→ `Modifier.minTouch()` 사용), `LocalConfiguration.screenWidthDp`(→ `rememberGridColumns()`), `LocalContext.current.resources`(→ `LocalResources.current`), modifier가 첫 선택 인자가 아닌 시그니처.
- `PhotoBox` 위 흰 글자는 반드시 `PhotoTextArea`/`PhotoTextColumn` 안(0.18 틴트만 있는 윗부분에 글자 금지). 사진 위 버튼은 `Tokens.PhotoButtonBg` 원.
- `ChoiceSegments`는 340dp 미만 기본 모드에서 한 줄 글자 세그먼트, 1열(쉬운 모드·큰 글자)에서 세로 라디오 목록으로 알아서 바뀐다.
- `ListGroup` 안 행 사이 구분선은 `ListDivider()`를 직접 넣는다.
- 확인 대화상자 `DestructiveConfirm`은 닫기를 호출하는 쪽이 한다(onConfirm·onDismiss에서 상태 끄기). 보안 화면은 `secure = true`.

**A (홈·첫인상)**: `HomeTrip.code`, `HomeCountry.sourceName` 준비됨. `PhotoTopCard` 위임 함수 유지. 홈은 `ReturnCheckCard(compact = true)`로 바꾸고 `HomeActions.openHelp` 추가(배선 2단계). `CountryPhotoCard`는 아직 옛 모양(→ `CountryPhotoTile`).
**B (나라·영상)**: CountryHero는 이미 220dp·`PhotoTextColumn`. `SectionPicker` → `ChoiceSegments`, `TopicCard(Notice)` → `NoticeBanner`. `Photos.Market`(호이안) 사용처를 없애면 2단계가 drawable·크레딧을 지운다. `FactGrid`의 카드 `sources = listOf(req) + facts.sourceRefs()`.
**C (내 여행·준비)**: `TodayUi.sourceNames` 준비(13 담아 둔 물건 SourceList). StepsCard는 이미 StepList. `JourneyStepper(current, description = today_stage_desc)`.
**D (도움·쇼핑·이동)**: `pack/PackScreens.kt`의 `displayDate`, `pack/ShoppingScreen.kt`의 `importColors·importLabel·ImportTag·ReturnCheckCard` 위임 함수 유지. `HelpUi.indexSources` 준비, `commonSourceName` VM 수정은 D 몫. `EmergencyCallTile(large)` 준비(번호 6자 초과는 폭 전체로 놓는 것은 호출 쪽).
**E (준비물·설정·입국 카드)**: FormConfirm 직접 입력칸(`EditRow`)은 검증 반영으로 6-17의 `label` + `supportingText` 형태(FieldLabel 제거, 중복 읽기 없음) — 나머지 6-17은 E 몫. `settings/SettingsScreen.kt`의 `LocalOnlyBanner` 위임 유지. 사진 출처 안내는 `photo_credits_body_v2`로 바꿀 것(json은 이미 바뀜). `KeyValueRow`는 Lists.kt.
**F (지갑·여권·보여 주기)**: `passportCardColors()`(Navy→AccentDeep, Gold eyebrow, White80 라벨, Surface 값), `KeyValueRow(onDark = true)`, `SecondaryButton(onDark = true)`, `LockedState(badgeIcon)`, `SelectTile`, `ComingSoonGroup`(passport_chip_soon disabled), `TextCircle`(이니셜 아바타), `PassportIntroContent` 추출됨. `rememberDeviceAuth`·`maskName`·`maskNumber` 시그니처 유지.

## 6. 부품 API (`com.readyport.ui.components`)
```kotlin
// Layout.kt
@Composable fun rememberGridColumns(preferred: Int = 2): Int
@Composable fun <T> TileGrid(items: List<T>, modifier: Modifier = Modifier, columns: Int = rememberGridColumns(), itemContent: @Composable (item: T, modifier: Modifier) -> Unit)
val LocalTileColumns: ProvidableCompositionLocal<Int?>         // 그리드 칸 안의 부품이 보는 그 그리드의 열 수 (밖이면 null)
fun <T> LazyListScope.tileRows(keyPrefix: String, items: List<T>, columns: Int, itemContent: @Composable (item: T, modifier: Modifier) -> Unit)
fun LazyListScope.sectionGap(key: String)                       // Spacer = sectionGap − 2×gap
fun Modifier.minTouch(): Modifier                                // Modifier.Node, 높이 ≥ 48/56
fun Modifier.minTouchSize(): Modifier                            // 가로·세로 ≥ 48/56
class KeyIndex { fun indexOf(key: String): Int?; val size: Int; fun track(scope: LazyListScope): LazyListScope }
@Composable fun rememberKeyIndex(): KeyIndex
suspend fun LazyListState.scrollToKey(index: KeyIndex, key: String, headerOffsetPx: Int = 0): Boolean
// IconBadge.kt
enum class BadgeTone(container, content) { Accent, Success, Caution, Danger, Help, Violet, Teal, Neutral, Navy, OnDark }
object OnDark { content; eyebrow; secondary; gold; fun allowedOn(background: Color): Set<Color> }
data class PassportCardColors(gradientTop, gradientBottom, eyebrow, icon, label, value); fun passportCardColors(): PassportCardColors
@Composable fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, tone: BadgeTone = BadgeTone.Accent, size: Dp = LocalDimens.current.iconBadge, shape: Shape = MaterialTheme.shapes.small, containerColor: Color = tone.container)
// IconKeys.kt
object IconKeys { stages; stage(i); emergency(id); phrase(id): ImageVector?; section(id); essential(id); shoppingCategory(c); importStatus(s); returnFact(source): Pair<ImageVector, BadgeTone>; formOrigin(o); option(value): ImageVector?; bookingKind(k); source }
// CardNews.kt
val BadgeTone.onLight: Color
@Composable fun SectionHeader(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tone: BadgeTone = BadgeTone.Accent, eyebrow: String? = null, subtitle: String? = null, action: (@Composable () -> Unit)? = null)
enum class NewsStyle { Surface, SurfaceCaution, Accent, Navy, Caution, Danger }
data class NewsColors(..., badgeContainer: Color = badge.container); fun newsColors(style: NewsStyle, tone: BadgeTone = BadgeTone.Accent): NewsColors
@Composable fun CardNewsCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, eyebrow: String? = null, body: String? = null, tone: BadgeTone = BadgeTone.Accent, style: NewsStyle = NewsStyle.Surface, sources: List<SourceRef> = emptyList(), trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {})
data class Fact(icon: ImageVector, value: String, label: String, tone: BadgeTone = BadgeTone.Accent, source: SourceRef? = null); fun List<Fact>.sourceRefs(): List<SourceRef>
@Composable fun StatTile(fact: Fact, modifier: Modifier = Modifier)
@Composable fun FactGrid(facts: List<Fact>, modifier: Modifier = Modifier, columns: Int = rememberGridColumns())   // 2개 미만이면 그리지 않음
@Composable fun FactChip(fact: Fact, modifier: Modifier = Modifier)
fun shortValue(text: String): String?;  fun feeIcon(value: String): ImageVector
data class Step(text: String, icon: ImageVector? = null, detail: String? = null)
@Composable fun TextCircle(text: String, modifier: Modifier = Modifier, minSize: Dp = LocalDimens.current.stepBadge, container: Color = Tokens.Accent, content: Color = Tokens.Surface, style: TextStyle = MaterialTheme.typography.labelLarge)
@Composable fun StepList(steps: List<Step>, modifier: Modifier = Modifier, numbered: Boolean = true)
@Composable fun IconBullet(text: String, icon: ImageVector, modifier: Modifier = Modifier, tone: BadgeTone = BadgeTone.Neutral)
@Composable fun ExpandableDetail(label: String = stringResource(R.string.action_more), content: @Composable ColumnScope.() -> Unit)
// Cards.kt
enum class CardTone { Neutral, Accent, Navy, Caution, @Deprecated Notice }
fun Modifier.cardShadow(shape: Shape): Modifier;  fun Modifier.startBar(color: Color, width: Dp = 4.dp): Modifier
@Composable fun InfoCard(...); @Composable fun TopicCard(...)                      // 기존
// Banners.kt
enum class BannerTone(bg, bar, icon, text) { Notice, Caution, Danger, Success }
@Composable fun NoticeBanner(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Info, tone: BannerTone = BannerTone.Notice, title: String? = null, secondLine: String? = null, secondIcon: ImageVector? = null)
@Composable fun SecurityBanner(modifier: Modifier = Modifier, compact: Boolean = false)
@Composable fun OfflineBanner(modifier: Modifier = Modifier)
// Sources.kt
data class SourceRef(name: String, verified: String)
fun displayDate(iso: String): String;  fun resolveSourceName(id: String, names: Map<String, String>, fallback: String): String;  fun sourceLines(refs: List<SourceRef>): List<SourceRef>
@Composable fun SourceFooter(ref: SourceRef, modifier: Modifier = Modifier, onColor: Boolean = false)
@Composable fun SourceFooter(source: String, verifiedDate: String)                // 기존 시그니처, 위임
@Composable fun SourceList(refs: List<SourceRef>, onColor: Boolean = false)
@Composable fun LinkRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null)
// Buttons.kt
@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, colors: ButtonColors = ButtonDefaults.buttonColors())
data class SecondaryButtonColors(container, content, border, borderWidth); fun secondaryButtonColors(tone: BadgeTone = BadgeTone.Accent, onDark: Boolean = false)
@Composable fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, tone: BadgeTone = BadgeTone.Accent, fillWidth: Boolean = true, enabled: Boolean = true, onDark: Boolean = false)
@Composable fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.DeleteOutline, fillWidth: Boolean = false)
@Composable fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null)
@Composable fun DestructiveConfirm(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, secure: Boolean = false)
object ButtonStyles { @Composable fun onDark(content: Color = Tokens.Navy): ButtonColors }
// Tiles.kt
enum class TileLayout { Auto, Vertical, Horizontal }
data class TileSpec(label: String, icon: ImageVector, onClick: () -> Unit, supporting: String? = null, tone: BadgeTone = BadgeTone.Accent, emphasized: Boolean = false)
data class TileColors(...); fun tileColors(emphasized: Boolean, tone: BadgeTone = BadgeTone.Accent): TileColors
@Composable fun IconTile(spec: TileSpec, modifier: Modifier = Modifier, layout: TileLayout = TileLayout.Auto)
@Composable fun InfoTileGrid(tiles: List<TileSpec>, modifier: Modifier = Modifier, columns: Int = rememberGridColumns())
@Composable fun ChoiceCard(title: String, body: String?, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, emphasized: Boolean = false, preview: (@Composable () -> Unit)? = null)
@Composable fun SelectTile(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)
@Composable fun EmptyState(icon: ImageVector, title: String, body: String?, modifier: Modifier = Modifier, tone: BadgeTone = BadgeTone.Neutral, action: (@Composable () -> Unit)? = null)
@Composable fun LockedState(title: String, body: String?, buttonLabel: String, onUnlock: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Lock, badgeIcon: ImageVector = Icons.Outlined.Fingerprint)
@Composable fun ComingSoonGroup(items: List<Pair<ImageVector, String>>, modifier: Modifier = Modifier)
data class EmergencyColors(...); fun emergencyColors(large: Boolean): EmergencyColors
@Composable fun EmergencyCallTile(label: String, number: String, icon: ImageVector, onCall: () -> Unit, modifier: Modifier = Modifier, note: String? = null, large: Boolean = false)
// Lists.kt
sealed interface RowTrailing { Chevron; External; None; Switch(checked, onChange); Custom(content) }
@Composable fun ListRow(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, body: String? = null, tone: BadgeTone = BadgeTone.Accent, trailing: RowTrailing = RowTrailing.Chevron, onClick: (() -> Unit)? = null)
@Composable fun ListDivider(indent: Boolean = true)
@Composable fun ListGroup(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)
@Composable fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, subLabel: String? = null, badge: (@Composable () -> Unit)? = null, onDark: Boolean = false)
// Controls.kt
@Composable fun <T> ChoiceSegments(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String, icon: (T) -> ImageVector?, modifier: Modifier = Modifier)
@Composable fun SelectChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier, leadingIcon: ImageVector? = null, avatar: (@Composable () -> Unit)? = null, singleChoice: Boolean = true)
@Composable fun appSwitchColors(): SwitchColors
// Status.kt
enum class StatusKind(icon, tone) { Allowed, Caution, Prohibited, Info, Soon, Verified, Self, Required }
@Composable fun StatusTag(text: String, kind: StatusKind, modifier: Modifier = Modifier)
@Composable fun StatusChip(text: String, container: Color = Tokens.AccentSoft, content: Color = Tokens.Ink, icon: ImageVector? = null)
fun importColors(status: ImportStatus): Pair<Color, Color>;  @StringRes fun importLabel(status: ImportStatus): Int;  fun importKind(status: ImportStatus): StatusKind
@Composable fun ImportVerdictBadge(status: ImportStatus)
// Photos.kt
object Photos { Home; Airport; Packing; Market(사용 중단 예정); fun country(code): Int?; fun byId(id): Int? }
@Composable fun PhotoBox(@DrawableRes photo: Int?, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.large, minHeight: Dp = 200.dp, alignment: Alignment = Alignment.Center, content: @Composable BoxScope.() -> Unit)
@Composable fun PhotoBox(painter: Painter?, …같은 인자…)
@Composable fun BoxScope.PhotoTextArea(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)
@Composable fun PhotoTextColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)
@Composable fun PhotoChip(text: String, icon: ImageVector? = null)
@Composable fun PhotoHeaderCard(@DrawableRes photo: Int?, title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, minHeight: Dp = 150.dp, content: @Composable ColumnScope.() -> Unit)
@Composable fun PhotoHeaderCard(painter: Painter?, …같은 인자…)              // 흰 단색 최악 캡처용 (null 리터럴은 두 겹침이라 쓰지 않는다)
data class ChipSpec(icon: ImageVector, text: String)
@Composable fun CountryPhotoTile(nameKo: String, nameEn: String, @DrawableRes photo: Int?, chips: List<ChipSpec>, onClick: () -> Unit, openLabel: String, modifier: Modifier = Modifier, large: Boolean = false, enabled: Boolean = true)
@Composable fun rememberThumbnail(@DrawableRes res: Int?, sizeDp: Dp): ImageBitmap?
// Journey.kt · ReturnCheck.kt · AppScreen.kt
@Composable fun JourneyStepper(current: Int, description: String, modifier: Modifier = Modifier, preview: Boolean = false)
@Composable fun ReturnCheckCard(links: List<OfficialLink>, facts: List<SourcedText>, sourceNames: Map<String, String>, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false)
@Composable fun AppScreen(title, speech, modifier = Modifier, subtitle = null, headerActions = {}, header = null, icon: ImageVector? = null, state: LazyListState = rememberLazyListState(), keyIndex: KeyIndex? = null, content: LazyListScope.() -> Unit)
// theme
data class ReadyPortDimens(easyMode, buttonHeight, screenPadding, cardPadding, gap, sectionGap, inner, minTouch, iconBadge, iconBadgeSmall, icon, iconSmall, tileMinHeight, tileRowMinHeight, listRowMinHeight, stepBadge)
data class ReadyPortTypeExtras(stat, statSmall, localLarge, localMedium); val LocalTypeExtras;  object ReadyPortLineBreak { phraseSupported; Heading; Body }  // 33 미만은 Simple
```

## 7. 확인 필요·남은 일
- 그림자 가시성(3절) — 3단계 운영자 결정.
- 운영자 확인 항목(스펙 8장 3단계 그대로): D1 글꼴, D3 모서리 PRD 문구, D6 도움 순서, D19 쉬운 모드 28sp 초과, 태국 팩 기간 문장.
- Robolectric 캡처에서는 `LineBreak.Heading/Phrase`가 일부만 적용돼 `가/방`처럼 음절 단위로 끊긴 곳이 보인다(sdk 31 캡처는 S10과 같은 조건). 2열 라벨 7자 규칙과 칸 폭 1열 전환(D4)이 대응책. 검증 반영으로 sdk 36 캡처는 `ko-rKR`(Phrase 적용), API 33 미만은 Simple 줄바꿈(8절 V15·V16).
- 검증 반영 추가분은 8.3절.

## 8. 검증 반영 (2026-10-01, 독립 검증 25건)

독립 검증자들이 0단계 결과에서 찾은 문제(major 6, minor 19)를 아래처럼 처리했다. **결과: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` BUILD SUCCESSFUL, 단위 테스트 178개 실패 0(33 클래스), lint 오류 0·경고 90(UnusedResources 78 + 기준선 12, 새 경고 0).** 갤러리(`basic`·`easy`·`sdk31_font200`)와 타일은 이 빌드에서 다시 만들었다.

### 8.1 처리 표

| # | 지적 (심각도) | 처리 |
|---|---|---|
| V1 | `CardNewsCard` `trailing`이 먼저 제 폭을 가져가 제목이 한 음절씩 쪼개짐 (major) | **고침.** 새 내부 레이아웃 `TrailingFlow`(Layout.kt): trailing을 옆에 두면 제목 덩어리가 **더 많은 줄로 꺾일 때만** 제목 아래 줄로 내린다(고유 높이 비교 — 글자 크기·쉬운 모드와 무관하게 실제 폭 기준). 17 직접 입력 카드 패턴 그대로 `ComponentsBehaviorTest.cardTrailingMovesBelowTitleOnlyWhenItWouldSqueezeIt`로 검사 |
| V2 | `ListRow` `RowTrailing.Custom` 배지가 제목을 쪼갬 (major) | **고침.** Custom 끝 요소는 `TrailingFlow`로(설명 없는 행은 세로 가운데). `listRowCustomTrailingDropsBelowWhenTitleWouldWrap` |
| V3·V20 | sdk 31·200% 쉬운 모드에서 부품 페이지 1·입국 카드 확인이 h8000dp에 잘려 일부 부품이 감사에서 빠짐 (major) | **고침.** ① 부품 페이지를 4장으로(`components-1~4`) ② 글자 200% 감사·sdk 31 캡처 높이 h12000dp ③ **감사가 '화면 잘림'을 실패로 잡는다**(세로 스크롤이 남으면 = 아래 항목이 안 그려짐) ④ 캡처도 맨 아래 줄까지 내용이 차 있으면 실패. 지금 가장 긴 화면: sdk 31 쉬운 모드 입국 카드 확인 17,445px(약 8,720dp) |
| (추가 발견) | 감사가 Robolectric **LEGACY 그래픽**으로 돌아 글자 하나를 약 1px로 재고 있었다 — 줄바꿈·화면 길이가 실제와 달라 '높이 안에 들어간다'는 판단이 의미 없었음 | **고침.** `A11yAudit*`와 `ComponentsBehaviorTest`를 `@GraphicsMode(NATIVE)`로(캡처와 같은 글자 측정) |
| V4 | `ChoiceSegments` 340dp 미만에서 선택 칸 Check까지 빠짐 (minor) | **고침.** 좁은 폭에서도 칸 아이콘만 빼고, 선택 칸 Check(D7)는 **글자 위에 작게**(모든 칸이 같은 자리를 비워 글자 줄 높이가 같음). 최소 높이 56dp 안이라 320×470 예산 변화 없음 |
| V5 | E 묶음 `FormConfirmScreen.kt`에 `label`을 더해 TalkBack이 이름을 두 번 읽음 (minor) | **고침(스펙 6-17 그대로).** `EditRow`에서 `FieldLabel`을 빼고 `OutlinedTextField(label = ko, supportingText = hint · en · local)`, `현지어 크게`면 supportingText가 headlineMedium. 묶음 밖 파일 수정은 이것뿐 — **E는 1단계를 이 상태에서 시작**한다(isError·trailingIcon·라디오 행 등 나머지 6-17은 E 몫) |
| V6 | 쉬운 모드 56dp 감사가 테마의 터치 영역 확장(`minimumTouchTargetSize`) 때문에 보이는 크기가 작은 버튼을 못 잡음 (minor) | **보완.** 감사가 **보이는 크기(boundsInRoot)**도 잰다. 공용 부품 페이지(`components-*`, `photo-worst-white`)는 바로 실패, 나머지 화면은 `app/build/a11y/visual-undersized-<클래스>-<모드>.txt`에 1단계 할 일로 남긴다. 지금 건수: sdk36 기본 8(나라 히어로 뒤로·찜 `IconButton` 40dp — 6-03 `minTouchSize()`), sdk36 쉬운 92, sdk36 200% 쉬운 31, sdk31 200% 기본 8·쉬운 31. **2단계에서 0건 확인 후 엄격(실패)으로 바꾼다** |
| V7·V13 | 스펙 그림자(Ink 8%/12%)가 거의 안 보여 흰 카드 경계가 없음 (major) | **보정.** 3절. 화면상 8%/12%가 되게 플랫폼 그림자 알파(0.039/0.19)로 나눔 → delta 9(기본 15). `shadowCheck`가 회귀를 막음. **운영자 확인 항목**(그대로/더 옅게/LineSoft 병행) |
| V8 | 강조 `ChoiceCard`의 배지 바탕(AccentSoft)이 카드 바탕과 같아 안 보임 (minor) | **고침.** `IconBadge(containerColor = …)` 인자 추가(맨 뒤, 기본값 tone.container) → 강조 카드는 흰 배지 |
| V9 | 시그니처 편차 (minor) | **고침.** `ListGroup(title, modifier, content)`, `TileGrid(…, itemContent)` 스펙대로(lint는 그 함수만 `@Suppress`). `scrollToKey`의 Boolean 반환은 호출 호환이라 유지 |
| V10·V17 | 작은 편차: SectionHeader action 줄바꿈 기준, ExpandableDetail `접기`, ReturnCheck 요약 줄, IconTile 셰브론 위치 (minor) | SectionHeader action은 `TrailingFlow`(실제 폭 기준, 100%에서도 `현지에서 쓰 / 는 도구` 없음) — **고침**. ExpandableDetail은 펼치면 `접기` — **고침**. ReturnCheck compact 요약 줄은 접혀 있을 때만 — **고침**. IconTile 셰브론은 **유지**(4절 8, 운영자 확인) |
| V11 | `photo-worst-white`가 흰 사진 위 `PhotoHeaderCard`를 안 찍음 (minor) | **고침.** `PhotoHeaderCard(painter: Painter?, …)` 오버로드 + 흰 단색 캡처. 같은 페이지의 사진 위 뒤로 버튼은 `minTouchSize()`(V6 보이는 크기 검사가 잡음) |
| V12 | `source_footer`의 `최⁠종` WORD JOINER가 스펙(확↔인만)보다 많음 (minor) | **유지.** 3.2의 이유(API 33 미만 음절 끊김)가 `최/종`에도 그대로이고 테스트는 getString 비교라 영향 없음. 3단계 문서 갱신 때 스펙 3.2에 `최⁠종`도 적도록 제안 |
| V14 | `SelectTile`이 화면 기준(rememberGridColumns)으로 가로 배치를 골라 3칸 행에서 `항/공/권`·Check 겹침 (major) | **고침.** 새 `LocalTileColumns`: `TileGrid`·`tileRows`가 칸 안에 그 그리드의 열 수를 알려 주고, `SelectTile`·`IconTile(Auto)`는 **놓인 그리드의 열 수**로 배치를 고른다(BoxWithConstraints는 행의 IntrinsicSize.Min과 함께 못 씀). 화면은 `TileGrid(columns = if (rememberGridColumns() == 1) 1 else 3)` — 부품 페이지도 이렇게 바꿈. `selectTileFollowsItsGridNotTheScreen` |
| V15 | `LineBreak.Heading`(Balanced)이 API 33 미만에서도 적용돼 들어갈 낱말까지 쪼갬 (major) | **고침.** `ReadyPortLineBreak`: API 33 이상은 스펙대로(Heading / HighQuality·Strict·Phrase), **미만은 `LineBreak.Simple`**(들어가는 만큼 채움). sdk 31 캡처: `태국 입국 카드 / (TDAC)`. 스펙 3.2의 '33 이상에서만 효과'를 실제로 맞춘 것 — 3단계 문서 갱신 때 3.2 문장 정정 제안 |
| V16 | sdk 36 갤러리가 en-US라 WordBreak.Phrase가 안 보임 (minor) | **고침.** 갤러리·감사 qualifiers에 `ko-rKR`(sdk 31 변형 포함) |
| V18 | 2열 StatTile 라벨 `비자 없이 머물러 / 요` (minor) | **고침.** `fact_label_visa_free` = `비자 없이\n머물러요`, `fact_label_visa_arrival` = `도착비자로\n머물러요`(3.2의 '7자 넘으면 어절 경계에 \n' 규칙을 부록 B 값에 적용 — 새 키라 테스트 영향 없음). 3단계 문서 갱신 때 부록 B 값도 맞춘다 |
| V19 | Neutral `SecondaryButton`이 비활성 주 버튼처럼 보임 (minor) | **고침.** Neutral = 흰 바탕 + Ink 글자 + 1dp LineStrong(누를 수 있음이 분명), 비활성 테두리는 장식선 Line. 부품 페이지의 `7일 미루기`는 6-13대로 일반 tonal(Accent) + Schedule |
| V21 | `build/gallery/tiles`가 옛 번호로 남아 있음 (minor) | **고침.** `GalleryCaptureTest.basic`이 매번 `tiles/`·`tiles.txt`를 지우고 새로 만든다(긴 캡처를 1,400px 이하 같은 높이로 분할, 지금 95장) |
| V22 | 아직 안 바꾼 화면의 M3 `OutlinedButton`이 알약 모양 (minor) | **1단계 몫.** M3 `Button`/`OutlinedButton` 기본 모양(CornerFull)은 테마 Shapes로 바뀌지 않는다 — 부품(`SecondaryButton` 등)으로 교체하는 것이 해결. 2단계 전수 검색 목록에 `shape` 없는 `OutlinedButton(`·`Button(`·`FilledTonalButton(`을 추가할 것 |
| V23 | 부품 페이지 대사관 타일 아이콘이 Sos (minor) | **고침.** `AccountBalance`(5.6) |
| V24 | 여러 줄 ListRow 배지가 가운데, Caution/Danger 카드 배지가 안 보임, 바탕 있는 IconBullet 글자가 12dp 더 들어감 (minor) | **고침.** ListRow는 설명이 있으면 위 맞춤(4.2), `NewsColors.badgeContainer`(Caution·Danger는 흰 배지), 바탕 있는 IconBullet은 바탕만 양옆 12dp 내밀어 글자 줄을 맞춤. 같은 이유로 `KeyValueRow` 배지도 `TrailingFlow`, 가로형 IconTile은 보조 글이 있으면 위 맞춤 |

### 8.2 1단계 묶음이 알아야 할 것 (추가)
- **끝 요소는 알아서 내려간다**: `CardNewsCard(trailing)`, `SectionHeader(action)`, `ListRow(RowTrailing.Custom)`, `KeyValueRow(badge)`는 폭이 모자라면 제목 아래 줄로 간다. 화면에서 fontScale·쉬운 모드로 따로 분기하지 않는다.
- **타일 배치는 놓인 그리드 기준**: `SelectTile`·`IconTile(Auto)`는 `TileGrid`/`tileRows` 칸 안이면 그 열 수로 정한다. 큰 글자에서 1열로 내리는 것은 화면이 `columns`로 정한다(26: `if (rememberGridColumns() == 1) 1 else 3`).
- **보이는 크기 할 일 목록**: `app/build/a11y/visual-undersized-*.txt`에서 자기 화면 줄을 0으로 만든다(대부분 `heightIn(min = 48.dp)` → `minTouch()`, `IconButton` → `minTouchSize()`, 콜백 있는 M3 Checkbox·RadioButton → 행 전체 toggleable/selectable + 컨트롤 콜백 null).
- **감사·캡처가 '화면 잘림'을 실패로 잡는다**: 화면이 h8000dp(200%는 h12000dp)를 넘으면 실패 — 화면을 줄이거나 2단계 담당에게 높이 조정을 요청한다(공유 테스트 파일은 동결).
- **줄바꿈**: API 33 미만은 Simple. 2열 라벨 7자 규칙(3.2)은 그대로 지킨다.
- `IconBadge(containerColor)`: 배지 바탕이 놓인 바탕과 같은 색일 때만 쓴다(상태 카드·강조 카드).
- E: `FormConfirmScreen.EditRow`는 이미 6-17의 label/supportingText 형태다.

### 8.3 운영자 확인 항목 (추가분 — 3단계 컨펌 때 함께)
1. 흰 카드 그림자 보정(V7): 그대로 / 더 옅게 / LineSoft 1dp 병행.
2. IconTile 세로형 셰브론 오른쪽 위(V10, 스펙 4.9 문구 수정).
3. 스펙 문구 정정: 3.2 'API 33 이상에서만 효과'(V15), 3.2 `최⁠종` WORD JOINER(V12), 부록 B `fact_label_visa_free/arrival`의 `\n`(V18).
