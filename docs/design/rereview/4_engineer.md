expert: Android Compose 디자인 시스템 엔지니어
scores: visual=7.5 clarity=7.5 accessibility=8.0 consistency=7.0 delight=6.5

## 총평
토큰과 부품을 먼저 만들고 화면을 얹는 구조는 거의 의도대로 됐습니다. 화면 코드에는 `Color(0x…)`, 고정 sp, `RoundedCornerShape`가 하나도 없습니다(탭 라벨 하한 10sp 1곳만 예외). 그래서 첫인상 점수는 확실히 올랐습니다. 200%와 쉬운 모드 캡처에서도 잘림이나 겹침은 찾지 못했습니다. 단계 표시는 글 한 줄+점으로, 2×2 타일은 1열 행으로, 빈칸 칩은 목록+펼치기로 바뀌는 등 적응 배치 수준도 높습니다.
문제는 마지막 20%입니다. 같은 뜻의 부품이 화면마다 private으로 다시 만들어져 있습니다. '급할 때는 도움' 2벌, 라벨-값 행 5벌, 선택 상태 표현 2계열이 그렇고, 큰 글자 분기 기준도 36곳에 흩어져 있습니다. 그래서 캡처끼리 대 보면 여백, 셰브론 위치, 선택 모양, 삭제 버튼 폭이 화면마다 다릅니다.
'카드뉴스' 체감은 첫 화면까지만입니다. 그 아래는 굵은 문장 단계 목록, 노란 태그 반복, 4개 화면에 반복되는 '귀국 전 확인' 글 블록이라 delight는 아직 목표(7.0)에 못 미칩니다.

## 남은 문제
(심각도 순. 화면 이름은 갤러리 번호, 근거 위치는 코드 파일:줄)

1. **24_help (200%, sdk31): 긴급 전화번호가 하이픈에서 두 줄로 쪼개짐 (심각도 높음)**
   - 문제: 대사관 카드 안에 들어간 타일이라 폭이 부족해 `+66-2-481-⏎6000`, `+66-81-914-⏎5803`처럼 끊깁니다. 급할 때 번호를 따라 읽거나 불러 주기 어렵습니다. `EmergencyCallTile`(Tiles.kt:496)은 글자 수(STAT_NUMBER_MAX)로만 stat/statSmall을 고르고 실제 폭은 재지 않습니다.
   - 수정: `TextMeasurer`로 한 줄 폭을 재서 stat→statSmall→titleLarge 순으로 줄입니다(쉬운 모드 최소 18sp 유지). 큰 글자에서는 대사관 타일을 카드 밖 폭 전체로 꺼냅니다. 테스트에는 '모든 전화번호 Text의 lineCount == 1' 단언을 추가합니다.

2. **01_home vs 09~16_today: '급할 때는 도움' 행이 2벌 (중간)**
   - 문제: `HomeScreen.HelpShortcut`(HomeScreen.kt:591)은 cardPadding 20/24, 셰브론 세로 가운데, 큰 글자에서도 설명을 유지합니다. Today 쪽은 `ListGroup{ListRow}`(TodayScreen.kt:366)라 패딩 16 고정, 셰브론 위 맞춤이고 fontScale≥1.8이면 설명을 지웁니다. 캡처에서도 배지 시작선이 40dp↔36dp로 다르고 셰브론 위치도 다릅니다.
   - 수정: components에 `HelpShortcutRow` 하나를 두고 두 화면이 같이 씁니다. `ListRow`의 `.padding(16.dp)`(Lists.kt:87)은 새 토큰 `listRowPadding`(16/20)으로 바꿔 카드 내용 시작선과 맞춥니다(18_prepare '꼭 챙길 물건', 27_settings 쉬운 모드에서도 같은 어긋남).

3. **00·17·20·24 vs 03·07·22·33: 선택 상태 표현이 두 계열·네 구현 (중간)**
   - 문제: D7의 'Accent 채움+흰 글자+체크'는 `ChoiceSegments`·`SelectChip`·`SelectTile`만 따릅니다. 나머지 셋은 'AccentSoft+2dp 테두리'를 화면 안에서 직접 그립니다: 나라 라디오 카드(TripScreens.kt:303), 문장 타일(PackScreens.kt:492), 입국 카드 라디오 행(FormConfirmScreen.kt:717). 비선택 모양도 LineStrong 테두리(17·20)와 그림자(24)로 갈립니다.
   - 수정: Controls.kt에 `SelectableCard(selected, role, leading, trailingCheck)` 하나를 만듭니다. 규칙은 "큰 카드형 단일 선택 = AccentSoft+2dp Accent+CheckCircle / 칩·세그먼트·작은 타일 = Accent 채움"으로 스펙에 명문화하고, 비선택은 1dp LineStrong으로 통일합니다.

4. **20·21·26·30·32: 라벨-값 행 구현 5벌 (중간)**
   - 문제: `KeyValueRow`, `ValueRow`(FormConfirmScreen.kt:483, 현지어 크게), `CopyRow/FieldNames`(ManualModeScreen.kt:196-208), `PassportField/BookingFact`(WalletScreen.kt:426·491), `ConfirmationNumber`(PresentScreens.kt:462)가 따로 있습니다. 같은 'ERIKSSON' 줄이 20에서는 3줄(라벨/영문/값), 21에서는 2줄(라벨+영문 한 줄/값)이고 들여쓰기도 다릅니다.
   - 수정: `KeyValueRow`에 `leading`(아이콘 배지), `trailing`(복사 버튼), `subLabelInline`, `subLabelStyle` 슬롯을 더해 다섯 곳을 흡수합니다.

5. **01(쉬운 모드·200%), 09~16(200%): 큰 글자·쉬운 모드에서 내용을 숨김 (중간)**
   - 문제: `HomeHero`는 1열이면 `home_subtitle`을 빼므로(HomeScreen.kt:315) 쉬운 모드, 즉 처음 여행하는 사람에게서 앱 소개 문장이 사라집니다. Today는 fontScale≥1.8이면 도움 행 설명을 지우는데 홈의 같은 행은 유지해 서로 모순입니다.
   - 수정: 숨기지 말고 배치를 바꿉니다. 소개 문장은 히어로 바로 아래 bodyLarge로 꺼내고, 도움 행은 `BadgeTitleLayout(stack)`으로 처리합니다.

6. **전역: 반응형 분기 기준이 흩어짐 (중간, 유지보수)**
   - 문제: D4의 `rememberGridColumns`(칸 폭÷fontScale, 150dp) 말고도 기준이 36곳에 흩어져 있습니다: `LARGE_FONT_SCALE` 1.3, `HUGE_FONT_SCALE` 1.5, FirstRun `HERO_SMALLER_SCALE`, Today `1.8f`, Home `1.5f` 리터럴. 폭(360 vs 412dp)을 보지 않고, API 34+ 비선형 확대에서는 같은 fontScale이 실제로는 다른 크기여서 기기마다 결과가 다릅니다.
   - 수정: Layout.kt에서 `LayoutClass`(Roomy/Compact/Stacked)를 '창 폭 ÷ 실측 글자 배율(textIconSize처럼 sp→dp 측정)'로 한 번 계산해 CompositionLocal로 내립니다. 화면은 그 값만 읽습니다.

7. **app/src/test: 디자인 시스템 회귀를 막는 그물에 구멍 (중간)**
   - ① A11yAudit는 393dp에서만 돕니다. 360dp·320dp×200%는 단언이 없습니다(320은 홈 캡처뿐).
   - ② 낱말 중간 줄바꿈 검사는 보고만 하고 실패하지 않습니다. 게다가 `before = t[end-1]`이 WORD JOINER면 한글로 보지 않아(A11yAuditTest.kt:74) API 33 미만의 강제 분절을 놓칩니다. 그래서 '0건'이라는 결과를 그대로 믿기 어렵습니다.
   - ③ 글자 잘림(`TextLayoutResult.hasVisualOverflow`) 단언이 없습니다.
   - ④ 캡처는 사람 검토용이라 픽셀 회귀 비교가 없습니다.
   - 수정: sdk31·w360dp·200% 감사를 추가하고, 보이지 않는 문자를 건너뛴 앞 글자로 판정한 뒤 실패로 승격합니다. 전 노드에 hasVisualOverflow==false를 단언하고, 핵심 6화면은 기존 captureToImage로 허용 오차 diff를 겁니다(새 라이브러리 없이).

8. **20_form-confirm: 노란 신호가 한 화면에 17회 (중간)**
   - 문제: 머리 `빈칸 8개` 태그, 칸마다 `꼭 채워요` 8개, 하단 빈칸 칩 8개가 모두 Caution 색이라 경고색이 남용되고 시선이 흩어집니다(1차 리뷰 #9가 지적한 '화면 절반이 노란색'이 형태만 바뀌어 남음).
   - 수정: 칸별 태그를 빼고 스펙 원안대로 `isError`+`trailingIcon(ErrorOutline)`로 바꿉니다. 필수 표시는 라벨 끝 `*`나 supportingText 앞 작은 아이콘 하나로 충분합니다. 태그는 머리 1회만 둡니다.

9. **04·05·24: StepList의 문장형 단계가 전부 굵은 글 (중간)**
   - 문제: e-VOA 4단계, 지도 3단계, '여권을 잃어버렸어요' 5단계가 2~3줄 titleMedium SemiBold로 굵은 글 벽이 됩니다. 홈 출국 순서처럼 짧은 제목형일 때만 굵어야 맞습니다.
   - 수정: `StepList(style = Title|Sentence)`를 두거나, 20자를 넘으면 bodyLarge Regular로 그리고 번호 원만 강조합니다.

10. **03·04·05·18·19·24 등 다수: 출처 줄에서 날짜만 다음 줄로 넘어감 (낮음~중간)**
    - 문제: strings.xml의 `source_footer`가 `확⁠인 %2$s`라서 '확인'과 날짜 사이가 일반 공백입니다(스펙 3.2는 NBSP). 그래서 `· 최종 확인⏎2026.09.28`처럼 끊깁니다. `sourceFooterDateNeverSplits`는 날짜 안쪽만 검사합니다.
    - 수정: `확⁠인 %2$s`로 바꾸고, 테스트에 '최종 확인'과 날짜가 같은 줄인지 단언을 추가합니다.

11. **01·02_home: 누를 수 없는 요소가 버튼처럼 보임 (낮음~중간)**
    - 문제: 히어로 신뢰 칩 3개는 흰 92% 둥근 사각+위 아이콘이라 큰 버튼처럼 보입니다. '꼭 챙길 물건'의 `플러그·전압·보조배터리` FactChip은 AccentSoft 채움이라 필터 칩처럼 보입니다. D21의 정신과 어긋납니다.
    - 수정: 신뢰 표시는 바탕 없는 아이콘+글 한 줄(White85 구분점)이나 낮은 알약으로 바꿉니다. FactChip은 Neutral(SurfaceSunken) 톤으로 tonal 버튼과 구분합니다.

12. **01·02·05(사진 많은 화면): 사진 디코드 성능 (낮음~중간)**
    - 문제: `PhotoBox`는 `painterResource`로 1080×675 webp를 컴포지션 중에 메인 스레드에서 동기 디코드합니다(장당 약 2.9MB ARGB, 홈 8장). LazyColumn에서 항목이 다시 들어올 때마다 다시 디코드하고, `rememberThumbnail`도 composition 안에서 동기 디코드합니다. S10에서 스크롤 끊김 위험이 있습니다. `ListRow`·`KeyValueRow`의 `BadgeTitleLayout`/`TrailingFlow`는 행마다 intrinsic 측정을 2~3회 해서 20번 화면(수십 행)에서 측정 비용이 큽니다.
    - 수정: 앱 범위 `LruCache<ImageBitmap>`+`produceState(Dispatchers.IO)` 비동기 디코드를 쓰고, 2열 타일은 `inSampleSize=2`로 줄입니다. intrinsic은 SubcomposeLayout 1회 측정으로 바꾸는 것을 검토합니다.

13. **06·09·20: 버튼 앞에 셰브론 아이콘 (낮음)**
    - 문제: `> 쇼핑 리스트 보기`(CountryScreen.kt:819), `> 여행 만들기`(TodayScreen.kt:199·221), `> 첫 빈칸으로 가기`(FormConfirmScreen.kt:826)가 남아 있습니다. 홈 `home_trip_open`은 같은 이유로 Luggage로 바꿨는데(BUNDLE_A_NOTES ②) 교훈이 부품 규칙이 되지 않았습니다.
    - 수정: 뜻 아이콘(ShoppingBag·EditCalendar·ArrowDownward)으로 바꾸고, `PrimaryButton`에 '셰브론은 trailing에만' 규칙을 둡니다.

14. **15·17 vs 26·29·30: DangerButton 폭·정렬 제각각 (낮음)**
    - 문제: 15·17은 폭 전체, 26·29·30은 끝 정렬 wrap입니다.
    - 수정: '카드의 단독 파괴 행동 = 폭 전체 / 목록 항목별 삭제 = 끝 정렬 wrap' 규칙을 정해 기본값과 문서를 맞춥니다.

15. **06 vs 15 vs 22: 같은 쇼핑 품목의 배지 톤이 다르고 아이콘이 단조로움 (낮음)**
    - 문제: 06·15는 Neutral, 22는 Help 톤입니다. 먹거리는 차·과자·망고·두리안·커피가 모두 포크·나이프입니다.
    - 수정: 톤을 하나로 고정하고, 품목 ID에 따라 Coffee·Cookie·EmojiFoodBeverage·Spa 등으로 세분합니다(delight에 바로 효과).

16. **03_country-entry: '들어갈 때' 불릿만 체크 (낮음)**
    - 문제: '여권이 훼손돼 있으면 입국이 거절될 수 있어요' 옆의 체크가 '좋음'으로 읽힙니다. CountryScreen.kt:714 주석이 돈·안전 섹션에서 체크를 뺀 이유가 그대로 적용되는 경우입니다.
    - 수정: 모든 섹션을 중립 불릿(Remove 또는 작은 점)으로 통일합니다.

17. **15_today-return: '여행이 끝났어요' 카드가 제목뿐인 빈 카드 (낮음)**
    - 문제: 스펙의 body `today_return_customs`가 빠졌습니다(TodayScreen.kt:317).
    - 수정: 본문을 붙이거나 SectionHeader로 바꿉니다.

18. **전역: 마감 (낮음)**
    - 화면 코드의 리터럴 dp가 약 330개입니다(FormConfirm 42, Home 32). 2·4·6·10dp 같은 4pt 밖 값도 섞여 있습니다.
    - OutlinedTextField 9곳 중 3곳만 색을 따로 지정하고 공용 `AppTextField`가 없습니다.
    - IconBadge가 dp 고정이라 200%에서 글자 대비 왜소해 보입니다(03·27·30).
    - Pretendard Std(2,350자)라 팩 문장의 비KS 음절은 글자 단위로 시스템 글꼴과 섞입니다.
    - 수정: spacing 토큰(xxs 2/xs 4/s 8/m 12)을 추가하고, `AppTextField`를 만들고, 배지에 `textIconSize` 비율(최대 1.25배)을 적용합니다.

## 잘된 점
- 토큰 규율이 좋습니다. 화면 코드에 하드코딩 색·sp·모서리 0건, `@Deprecated` 잔재 정리, ColorScheme 역할 전부 지정으로 M3 보라가 새지 않습니다.
- 적응 배치가 실제로 작동합니다. JourneyStepper 축약, 2×2→1열 행, 빈칸 칩→목록+펼치기, `BadgeTitleLayout(stack)`, `TrailingFlow` 덕분에 200%(sdk31)·쉬운 모드에서 잘림·겹침이 보이지 않습니다.
- 신뢰 요소를 부품이 강제합니다. `SourceFooter/SourceList`(ID 폴백 없음), 입국 화면 첫 `NoticeBanner`, 보안 화면 `SecurityBanner`가 빠짐없이 같은 모양으로 들어가 있습니다.
- 테스트 기반이 탄탄합니다. A11yAudit 3구성+보이는 크기 엄격 검사, 알파 합성 대비 계산, `OnDarkPairsTest` 정적 검사, 한국어 줄바꿈 단위 테스트, 출처 ID 전수 테스트가 있습니다.
- 검수 인프라가 성숙했습니다. 쪽 단위 캡처 스티칭으로 그림자 인공물을 없애고, 타일 목록·sdk31 200% 갤러리를 자동으로 만듭니다.
