# 다듬기 D0(공용 부품) 보고 — 2026-10-01

- 브랜치: `claude/design-refresh` (로컬 커밋만 — 푸시·Play Console·Firebase·GitHub·`packs/` 손대지 않음)
- 입력: `OWNER_DECISIONS.md` '운영자 결정 기록'(구속력 있는 결정), 최종 재검토 `rereview2/1~5_*.md` '남은 문제', `REREVIEW_SUMMARY.md`, `FIX_A_REPORT.md`, `FIX_C_REPORT.md`, 스펙 2~4장·부록
- 범위: `ui/components`, `ui/theme`, 공용 테스트 + 바뀐 공용 부품을 부르는 화면 호출부 이전. 화면별 배치·문구는 다음 화면 라운드로 넘긴다(5절).
- 스펙 갱신: `DESIGN_SPEC_2026-10-01.md` **부록 D — 다듬기 D0** (이 부록이 4장·부록 C보다 우선)

## 1. 결과

| 항목 | 다듬기 전 | 다듬기 후 |
|---|---|---|
| `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | BUILD SUCCESSFUL | **BUILD SUCCESSFUL in 4m 31s** |
| 단위 테스트 | 317개 / 65 클래스 | **334개 / 69 클래스, 실패 0** (새 17 — D0 부품 11 + 감사 6) |
| lint 오류 / 경고 | 0 / 11 | **0 / 11** (기준선과 같은 11건 — UseKtx 3·UnusedAttribute 2·IconXmlAndPng 2·InlinedApi·OldTargetApi·ObsoleteSdkInt·TypographyDashes, 새 경고 0) |
| 접근성·줄바꿈 감사 구성 | 393dp 4구성(sdk36 100%·200%, sdk31 200%), 줄바꿈은 **보고만** | **9구성 11케이스** — + 360dp(sdk36 100% 기본·쉬운, sdk31 200% 기본·쉬운) + 기기 언어 영어(sdk36 100% 기본·쉬운). 줄바꿈 위반·글자 넘침 **실패**. 위반 0건 |

## 2. 운영자 결정 반영 (공용 부품)

| # | 결정 | 처리 |
|---|---|---|
| 2 | 태국어 `원어민 검수 전` 표시 제거 | 도움 문장 카드의 `StatusTag(help_unreviewed)`, 입국 카드 확인의 `form_labels_unreviewed` 줄을 지우고 두 문자열도 삭제. 팩 `reviewed`·레시피 `labels_reviewed` 값은 그대로(주석만 '앱에 표시하지 않음'). `ARCHITECTURE.md`·`STORE_LISTING_KO.md`·`PackModels` 주석 갱신. 스토어 이미지는 다시 찍어야 한다(5절) |
| 6 | 흰 카드에 아주 옅은 1dp 테두리 + 그림자 | `Modifier.cardShadow(shape, border = true)`가 그림자와 1dp `LineSoft` 테두리를 함께 그린다 → `InfoCard`·`CardNewsCard`(Surface)·`ListGroup`·`IconTile`·`ChoiceCard`와 화면의 흰 카드(쇼핑 품목·꼭 챙길 물건·영상·여행 고치기 날짜·사진 출처·입국 카드 확인 요약)가 모두 같은 경계. 자기 2dp 테두리가 있는 추천 `ChoiceCard`와 사진이 가장자리까지 닿는 `PhotoHeaderCard`만 테두리 없음(사진 위 밝은 선 방지) |
| 10 | 귀국 전 확인: 전체는 귀국 단계에만, 나머지는 한 줄 요약 + 펼치기 | `ReturnCheckCard(…, mode: ReturnCheckMode)` **필수 인자**. `Full` = 내 여행 귀국 단계(안내 → 사실 4행 첫 문장 → 펼치면 전체 → 출처 → 링크). `Summary` = 홈·나라 쇼핑·쇼핑 리스트(첫 사실 한 줄 + `면세 한도·반입 금지 문장 전체 보기` → 펼치면 사실 전체·안내·공식 링크). 출처는 두 모양 모두 접힘 밖. 관세청 둘째 문장(별도 면세)은 술잔(`LocalBar`) 아이콘으로 기본 면세(저금통)와 구분 |
| 13 | `담았어요 ✓` → `담았어요` | `shopping_in_cart` 값 변경(체크 아이콘만 남음), 테스트(`inCartLabelHasNoCheckGlyph`)·주석 수정 |

## 3. 재검토2 공용 지적 반영

| 지적 | 처리 |
|---|---|
| ①#1 AccentSoft 과부하 | **버튼은 바탕 흰색, 선택은 바탕 AccentSoft**. `SecondaryButton` = 흰 바탕 + 1.5dp 톤 글자색 테두리 + 톤 글자색(outlined). Neutral 보조 버튼 = SurfaceSunken + 1.5dp LineStrong + Ink(흰 바탕 + 1dp LineStrong 비선택 칩과 모양이 갈림 — ④#5). `StatTile` 바탕 = Ground(톤 색은 아이콘만). `StatusChip` 기본 바탕 = SurfaceSunken |
| ①#2 섹션 머리 = 카드 제목 | `SectionHeader` = 배지 없이 24dp 아이콘(글자 따라 커짐) + headlineSmall 22 Bold(쉬운 24). `CardNewsCard` 제목 = titleMedium 17 SemiBold(쉬운 22) + 40dp 배지, 한 줄 제목은 배지 가운데 |
| ①#3 띠 3~4개 쌓임 | 새 부품 `AssuranceCard`(흰 바탕 + 4dp Accent 막대, 줄마다 Policy 비제휴 · Lock 이 휴대폰에만 · TouchApp 제출은 직접 — 문구·순서 그대로, 줄마다 한 Text 노드). 화면 채택은 화면 라운드 |
| ①#4 상태 표현 제각각 | 새 부품 `ImportVerdictNote(status, note)` = StatusTag 알약 + 보통 본문 이유(InkSecondary, 숫자 굵게). 쇼핑 리스트 `ImportVerdictPanel`(채움 + 막대 블록 + 상태색 본문) 삭제 → 공용, 귀국 단계 담아 둔 물건도 같은 부품 |
| ①#5 쉬운 모드 `처음으로·소리로 듣기` | 같은 폭으로 내용선 끝까지(`EqualWidthPair`), 반 폭에 라벨이 한 줄로 안 들어가면(200%) 위아래로 쌓고 둘 다 폭 전체. 모양은 한 단계 낮게(`EasyActionButton`: 흰 바탕 + 1dp LineStrong + Ink 글자 + Accent 아이콘, 64dp) |
| ③#1 숫자 강조가 귀국 카드에만 | 숫자 로직을 `Numbers.kt` 한 곳으로(`numberRanges`·`emphasizeNumbers(color)`·`NumberText`). 단위 목록 확장: 통화(띄어 써도 — 달러·원·밧·바트·루피아·링깃·엔·위안·페소·유로), 한국어 단위(붙여 쓸 때만 — 단계·개비·개월·시간·개·세·일·월·년·박·분·주·명·인·회·번·칸·곳·%), 영문 단위(Wh·mAh·ml·L·kg·V·Hz 등, 뒤에 영문자 없을 때만), `[A-Z]{3} ` 통화 코드, `만/천/억` 자리(`미화 1만 5천 달러`). 단위 없는 맨 숫자(번지·우편번호·날짜·전화번호)는 굵게 하지 않음(가운뎃점 숫자 목록 `20·50·100`만 예외). `DotBullet`·`IconBullet`·`CardNewsCard` 본문·`StatTile` 라벨·`StepList` 문장형 글과 보조 글·`ReturnCheckCard`·판정 메모·꼭 챙길 물건 설명·쇼핑 품목 설명이 안에서 쓴다. 비자 요약 문장은 타일 값 되풀이라 일부러 굵게 하지 않음 |
| ③#3·②#3·④#3 `꼭 채워요` 되풀이 | 새 공용 `RequiredMark`(이름 뒤 작은 느낌표, TalkBack `빈칸`, 할 일 Caution / 오류 Danger, 글자 따라 커짐) + `RequiredSummary`(`[!] 꼭 채울 칸 N개 · 모두 M칸`). 값 복사해서 넣기(21)의 '사이트에서 직접 적을 칸'은 칸마다 `꼭 채워요` 태그 대신 묶음 머리 요약 한 줄 + 한국어 이름 바로 뒤 느낌표, 시작선 4dp 들여쓰기 제거. 입국 카드 확인(20)의 고르는 칸 느낌표도 같은 부품. `form_field_required` 삭제 |
| ③#4 쉬운 모드 단계 = 5dp 점 6개 | `JourneyStepper` 1열 = 지금 단계 배지(48/56dp, 글자 따라 최대 1.25배) + 문장 + **폭 전체 여섯 칸 막대**(칸마다 단계 아이콘: 지난 칸 Accent + 흰 Check, 지금 칸 AccentSoft + 2dp Accent, 남은 칸 흰 바탕 + 1dp LineStrong). 아이콘은 글자 따라 커지고 칸 폭을 넘지 않음 |
| ④#1·②#5 입국 카드 카드 두 벌·말 세 가지 | 새 공용 `EntryFormCard` — 나라 입국·비자(03·04)와 여행 준비(18)가 같은 부품. 말은 **입국 카드** 하나: eyebrow `온라인 입국 카드`(`entry_form_label`, 인도네시아 `1단계 · 온라인 입국 카드`), 비용 칩 `무료 입국 카드 비용`(`fact_label_form_fee`), 버튼 `입국 카드 준비하기`(`prepare_form_open`). 날짜 묶음 도우미 두 벌 → `keepMonthDay` 하나(KoreanText.kt). `home_passport_body`·`settings_myinfo_body`의 `입국 신고서` → `입국 카드`. 삭제: `form_fee_chip_label`·`country_form_label`·`country_form_start`·`country_form_manual_start` |
| ④#2 StepList 문장형이 24에만 | `StepList(sentence: Boolean? = null)` — 부품이 고른다: 모든 단계가 20자 이하·문장부호 없음이면 제목 글자, 아니면 목록 전체 본문 글자. 04 e-VOA 단계·05 지도 저장 단계가 자동으로 문장형(캡처 확인). 도움 절차의 `sentence = true` 삭제 |
| ④#5 버튼 변형 규칙 | `DangerButton(placement: ButtonPlacement)` **필수** — `CardAction`(카드·화면 단위, 폭 전체) / `ItemAction`(목록 항목, 끝 정렬을 부품이 스스로). 호출부 8곳 이전: 29 `내 정보 비우고 다시 시작`은 카드의 하나뿐인 행동이라 폭 전체로(재검토 지적), 예약 서류·같이 가는 사람 지우기는 ItemAction(같이 가는 사람 카드의 `여권 등록하기`는 폭 전체 보조 버튼, 지우기는 그 아래 끝 정렬) |
| ②#2 펼친 글을 TalkBack이 못 따라감 | 공용 `ExpandToggle`(펼친 뒤 `접기`의 TalkBack 이름 = `{대상} 접기`) + `Modifier.foldLiveRegion()`(liveRegion Polite). `ExpandableDetail(label, target)`은 펼쳐 나온 내용을 늘 있는 상자의 liveRegion 안에 둔다. 위 글을 바꾸는 카드(귀국 전 확인 사실 행, 나라 화면 비자 요약·e-VOA 단계·팩 섹션 문장, 쉬운 모드 홈 접힌 카드)는 바뀌는 묶음에 liveRegion. 나라 화면 `MoreToggle` 몸통을 공용으로, 쇼핑·꼭 챙길 물건·입국 카드 확인의 펼침 줄에 대상(`{품목} 설명`, `빈칸 이름`, `{여권에서} 가져온 칸`) |

## 4. 테스트 그물 (④#4)

- `A11yAuditTest`에 `A11yAudit360Test`(sdk36·360dp 기본·쉬운), `A11yAudit360Sdk31Test`(sdk31·360dp·200% 기본·쉬운), `A11yAuditEnglishTest`(sdk36·기기 언어 영어 기본·쉬운) 추가.
- **줄바꿈 위반(낱말 중간·한 음절 줄)을 실패로.** 낱말이 한 줄보다 넓어 피할 수 없는 경우만 예외 — `TextMeasurer`로 그 낱말 폭을 실제로 재서 칸 폭과 비교.
- **WORD JOINER 누락 버그 수정**(옛 74줄): 줄 끝 앞 글자를 보이지 않는 문자(WORD JOINER·ZWSP)를 건너뛰고 판정.
- **글자 넘침 단언 추가**: `hasVisualOverflow`는 semantics가 돌려주는 결과에서 쓸 수 없었다(단락 폭이 칸 최대 폭으로 다시 만들어져 `1`·`레디포트`도 넘침으로 나옴 — 진단 로그로 확인). 그래서 줄마다 글자 폭 > 칸 폭, 단락 높이 > 칸 높이, 줄 수 제한 초과를 재는 같은 뜻의 단언으로.
- 새 감사가 찾아 고친 실제 문제(전부 360dp·sdk31·200%·쉬운 모드): 홈 히어로 `어디로 떠나세/요?`, 이동하기 `보여주/기`, 여권 카드 `PASSPORT · 여/권` → KoText로. 글 끝 한 음절 낱말이 홀로 남던 `적을 / 칸`·`있는 / 주`·`가져온 / 값` → `koDisplay`가 글 끝 한 음절 낱말을 앞 낱말에 붙인다(`glueLastShortWord`, 모든 API). 흰 사진 최악 픽스처의 제목도 KoText로.
- 새 테스트 `components/SharedComponentsPolishD0Test`(11): 보조 버튼 흰 바탕·StatTile Ground·테두리 1dp, 귀국 전 확인 요약 모양(한 줄 → 펼치면 전체·링크, 출처 늘 보임, `{대상} 접기`, liveRegion), 펼침 줄 대상 이름·liveRegion, `RequiredMark`·`RequiredSummary`(`꼭 채워요` 없음), StepList 길이 자동 판정, DangerButton 폭 규칙, 쉬운 모드 두 버튼 같은 폭·내용선 끝, 숫자 토큰 단위, `담았어요`, 도움 화면에 `검수` 글자 없음, 03·18 입국 카드 같은 말.
- 바꾼 테스트: `SharedComponentsFixCTest`(DangerButton placement·ReturnCheckCard Full), `ComponentsPage`(EntryFormCard·AssuranceCard·RequiredSummary·ImportVerdictNote·귀국 전 확인 요약 견본 추가), `CountryDesignTest`·`CountryLayoutTest`(`입국 카드 비용`·`온라인 입국 카드`·`keepMonthDay`), `ReadyPortRootTest`(`입국 카드 준비하기`), `FormUiTest`(`꼭 채워요` 글자 0개), `HelpShoppingLogicTest`(주석).

## 5. 화면 라운드에 넘기는 것 (정확한 할 일)

1. **안심 카드 채택**(①#3·⑤#8): 20 입국 카드 확인(비제휴 NoticeBanner + Navy `SecurityBanner(compact)` + `제출은 직접` 줄 → `AssuranceCard()` 한 장), 21 값 복사해서 넣기(비제휴 + Navy → `AssuranceCard()`, 노랑 `직접 확인 필요` 띠는 3단계 `건강 신고` 카드 안 IconBullet로), 03·04 입국 화면 첫 항목(`AssuranceCard(items = listOf(NotAffiliated, SubmitSelf))`), 18 여행 준비의 다른 문장 비제휴 띠도 같은 부품으로. Navy 보안 띠는 지갑·여권 화면에만. 테스트가 `guide_not_affiliated` 한 노드를 찾으므로 그대로 통과한다.
2. **도움 탭 순서**(결정 5): `pack/PackScreens.kt` — 나라 칩 → 선택 문장 카드 → 문장 타일 → 긴급 번호 → 대사관 → 이럴 땐 이렇게 → 어느 나라에서나(대표 긴급 번호 1개를 위에 따로 두지 않음).
3. **스토어 이미지**(결정 1·2·+): 나라 섞기(05 도움 일본, 03 인도네시아, 07 싱가포르·말레이시아 — `StoreAssetsTest` 픽스처), 1번 캡션 `칸은 앱이 채워요 / 제출만 직접`, 05·07(검수 배지 사라짐)·08(여섯 칸 막대) 다시 찍어 `docs/play/store/`·`STORE_LISTING_KO.md` 표 갱신. 업로드는 여전히 보류.
4. **화면 안 흰 카드 제목 위계 맞추기**(①#2 연장): 화면이 직접 그리는 카드 제목 중 titleLarge인 것 — 쇼핑 품목 이름(`ShopItemCard`), 꼭 챙길 물건 `CheckRowCard` 이름(아직=titleLarge), 같이 가는 사람 카드 이름 — 을 카드 제목 규칙(titleMedium SemiBold)으로 할지 화면별로 정한다. 입국 카드 확인의 조각 카드(`CardSegment`)는 그림자·테두리가 없다 — 결정 6에 맞춰 옆선 1dp LineSoft를 둘지 정한다.
5. **카드 안 판정 규칙의 남은 곳**(①#4): 05 전기 `한국 플러그 그대로`(카드 안 Success 채움 배너 → StatusTag + 한 줄), 04 대행 사이트 경고(카드 안 Danger 배너 — 화면 단위 경고로 둘지), 29 키 분실 카드(호박색 채움 → 흰 카드 + 4dp Caution 막대, 지워지는 범위 한 줄 — ②#4).
6. **입국 카드 확인(20)**(①#9·①#8): 빈칸이 남은 동안 주 버튼 자리 `첫 빈칸으로 가기`, 선택지 14개를 `ListGroup` 구분선 행으로.
7. **값 복사해서 넣기(21)**(③#3): 단계 머리 eyebrow `1단계`를 StepList 번호 원 배지 + 제목으로(04 `1단계 · 온라인 입국 카드` eyebrow도 같이, ③#9 — 2단계 Success 색 → Accent).
8. **홈·여행 준비 `꼭 챙길 물건` 칩 값**(①#15·②#9·③#10): `InfoChip(value = …)`로 `220V`·`한국 플러그 그대로`, 진행 `n/5`를 01·02·18 모두에.
9. **쉬운 모드 홈의 `처음으로`**(⑤#12): 홈에서는 숨기고 `소리로 듣기`만 폭 전체 — `AppScreen`에 인자 하나가 필요하다(공용이지만 홈만의 결정이라 화면 라운드에서 함께). `ReadyPortRootTest`가 화면마다 `처음으로` 개수를 센다.
10. **그 밖의 화면 문구·배치**: 30 `여권 정보 지우기`를 맨 아래로 + 24dp 간격(②#4), 13 `도착을 잘못 눌렀어요`(②#7), 날짜 ISO 표시(①#13), `보여주기`→`보여 주기`(①#17·⑤#10), `급할 때는 도움`·`앱에 들어 있는 안내` 쉬운 말(⑤#10), 19 `제휴`→`수수료`(⑤#9), 35 라이선스 알약 → InfoChip + `원본 보기` 이름(②#12·④#9), 15 담아 둔 물건 판정 요약 줄(③#12), 16 결산 타일(①#6·③#13), 200% 기본 모드 홈 접기(②#1 — 결정 8과 관련, 운영자 확인).
11. **남은 공용 후보**(이번 범위 밖으로 둠): `NoticeBanner` 본문 첫 문장만 SemiBold(①#12 — 안심 카드는 이미 첫 줄만 굵게), `IconBadge`·`SelectionMark`·셰브론의 글자 배율 크기(③#4·④#7·②#6 — 이번에는 JourneyStepper만), `AppTextField`·spacing 토큰(④#7), `ConsentRow` → `CheckRow` 공용(④#8), 한 목록 안 쌓기 판정 통일(④#6).

## 6. 운영자 확인 (이번에 생긴 기본값)

- 보조 버튼이 연한 파랑 채움 → **흰 바탕 + 파란 테두리**로 바뀌었다(앱 전체). Neutral 보조 버튼(쇼핑 `현지에서 먹기로 담기` 등)은 연회색 채움 + 회색 테두리.
- 카드 제목이 한 단계 작아졌다(20 Bold → 17 SemiBold). 섹션 머리는 22 Bold + 배지 없는 아이콘.
- 팩 문장 숫자는 **단위가 붙은 것만** 굵게(번지·우편번호·날짜·전화번호는 보통).
- `입국 신고서`(홈 여권 카드·설정)도 `입국 카드`로 바꿨다. 공식 이름에 '신고'가 든 양식 이름(`인도네시아 입국 신고 (All Indonesia)`)은 팩 원문 그대로.
- 같이 가는 사람 카드: `여권 등록하기`가 폭 전체, `지우기`는 그 아래 끝 정렬로 나뉘었다.

## 7. 눈으로 확인한 캡처

`app/build/gallery/{basic,easy,sdk31_font200/basic,sdk31_font200/easy}`: 01 홈(요약 귀국 카드·섹션 머리·카드 테두리), 03 태국 입국(EntryFormCard·숫자 굵게·보조 버튼), 04 인도네시아 200%(1단계 eyebrow·e-VOA 단계 문장형), 14 여행 중 쉬운 모드·쉬운 200%(여섯 칸 막대·같은 폭 두 버튼 → 200%에서 위아래 폭 전체), 18 여행 준비(03과 같은 카드), 21 값 복사해서 넣기 기본·쉬운 200%(요약 줄·이름 뒤 느낌표), 22 쇼핑(판정 알약 + 본문, 흰 테두리 담기 버튼, Neutral 버튼), 24 도움(배지 없는 섹션 머리, 검수 배지 없음), 30 지갑(ItemAction·CardAction), 36·39 부품 페이지(안심 카드, 귀국 전 확인 전체·요약·폴백, 꼭 채울 칸, 판정 묶음).
