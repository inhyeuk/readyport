expert: Android Compose 디자인 시스템 엔지니어
scores: visual=8.0 clarity=8.0 accessibility=8.5 consistency=7.5 delight=7.0

## 총평
수정 A·B·C를 거치며 지난번에 지적한 '같은 뜻의 부품이 화면마다 따로 있는 문제'는 대부분 공용 부품으로 정리됐습니다. `SelectableCard`, `KeyValueRow` 슬롯, `HelpShortcutRow`, `InfoChip`, `FitText`, `LayoutClass`, `PhotoCache`가 생겼습니다. 화면 코드에는 fontScale 리터럴, `Color(0x…)`, 고정 sp, `RoundedCornerShape`가 0건이고, 화면에만 있던 임시 부품도 0개입니다.
캡처로 봐도 효과가 분명합니다. 200%에서 긴급 번호가 한 줄로 나오고, 20 입국 카드 확인의 노란 신호는 17회에서 머리 1회+칸 끝 '!'로 줄었습니다. 15·16에는 사진 머리 카드가 생겼고, 24 절차는 본문 글자로 바뀌었습니다.
남은 감점 요인은 세 가지입니다. 첫째, 같은 정보 카드가 아직 두 벌인 곳이 있습니다(03↔18 입국 카드 카드). 둘째, 같은 규칙이 일부 화면에만 적용됐습니다(StepList 문장형은 24만, 빈칸 표시는 20만). 셋째, 테스트 그물은 지난번 지적 그대로입니다. 수정 C 2.3절의 `(TDAC)/를` 줄바꿈 버그가 감사를 통과한 이유가 바로 '한 폭·한 언어·보고만' 구조였다는 점이 이를 뒷받침합니다.
목표치(각 7.5, delight 7.0)는 이 관점에서 모두 넘었다고 봅니다. 다만 consistency는 경계선입니다.

## 이전 지적 해결 여부
| # | 이전 지적 | 판정 | 근거(지금 상태) |
|---|---|---|---|
| 1 | 24(200%) 긴급 번호가 하이픈에서 두 줄 | **해결** | `PhoneNumberText = FitText`(칸 폭 실측, stat→…→bodySmall), Stacked에서 대사관 타일을 카드 밖 폭 전체로. sdk31 200% 캡처에서 `+66-81-914-5803` 한 줄 확인, `phoneNumbers*`가 `lineCount == 1`을 단언 |
| 2 | '급할 때는 도움' 2벌, ListRow 여백 16 고정 | **해결** | `HelpShortcutRow` 하나(01·09~16 배지 시작선이 x=40dp로 같음). `listRowPadding`=cardPadding |
| 3 | 선택 상태 2계열·4구현 | **대부분 해결** | `SelectableCard`+C.2 규칙(17·20·24). 남은 것: 33 `SelectTile`(Accent 채움)과 17 나라 카드가 크기·모양이 비슷한데 계열이 달라 경계가 모호함(아래 10번) |
| 4 | 라벨-값 행 5벌 | **대부분 해결** | 20·21·26·30·32가 `KeyValueRow` 사용. 21의 '사이트에서 직접 적을 칸'(`OnSiteField`)만 따로 있음(아래 3번) |
| 5 | 큰 글자·쉬운 모드에서 내용 숨김 | **해결** | 소개 문장은 1열에서 나라 목록 아래로 옮겨 계속 보임(운영자 결정 9), 도움 줄 설명 유지, 단계 아이콘은 `LeadIconText`로 자리만 이동 |
| 6 | 반응형 판정 36곳 | **해결** | `layoutInfoOf`/`LocalLayoutInfo` 한 곳, 화면의 fontScale 0건. 남은 것: 행 단위 쌓기 판정(아래 6번) |
| 7 | 테스트 그물 구멍 | **부분 해결** | 추가된 것: 번호 한 줄 단언, `verifiedLabelAndDateStayTogether`, `koreanTitlesBreakOnlyAtSpacesOnNonKoreanDevice`. 그대로인 것: 감사는 393dp만, 낱말 중간 줄바꿈은 보고만, WORD JOINER 누락 버그(A11yAuditTest.kt:74), 전체 노드 `hasVisualOverflow` 없음, 픽셀 diff 없음 |
| 8 | 20 노란 신호 17회 | **해결(20)** | 머리 `빈칸 8개 남았어요` 1회+칸 끝 '!' 아이콘. 같은 개념인 21은 아직 `꼭 채워요` 태그 9회 |
| 9 | StepList 문장형이 굵은 글 벽 | **부분 해결** | `StepList(sentence)` 생김, 24만 적용. 04 e-VOA 4단계·05 지도 3단계는 여전히 2~3줄 SemiBold |
| 10 | 출처 줄에서 날짜만 다음 줄 | **해결** | NBSP+`looseDateBreak`. `· 최종 확인 2026.09.28`이 통째로 넘어감, 기관별 묶기도 됨 |
| 11 | 누를 수 없는 칩이 버튼처럼 보임 | **해결** | `InfoChip`(채움·테두리 없음): 홈 신뢰 표시, 플러그·전압. 예외 하나: 35 라이선스 알약(아래 9번) |
| 12 | 사진 동기 디코드 | **해결** | `PhotoCache`+inSampleSize+IO 디코드, `painterResource` 사진 0건(intrinsic 최적화는 보류로 명시) |
| 13 | 버튼 앞 셰브론 | **해결** | 부품이 꺾쇠를 라벨 뒤로 옮김(`isTrailingOnlyIcon`). 06 ShoppingBag, 20 ArrowDownward |
| 14 | DangerButton 폭 제각각 | **부분 해결** | 15·17·26·30(여권)=폭 전체, 30·34 항목별=끝 정렬로 규칙에 맞음. 29 `내 정보 비우고 다시 시작`(카드의 단독 파괴 행동)만 끝 정렬. 기본값 `fillWidth = false`라 호출부가 고르는 구조는 그대로 |
| 15 | 쇼핑 배지 톤·아이콘 단조 | **해결** | Neutral 고정, `IconKeys.item`(Cookie·LocalCafe·RiceBowl·Diamond·Eco) |
| 16 | 03 '들어갈 때' 체크 불릿 | **해결** | `DotBullet` |
| 17 | 15 제목뿐인 빈 카드 | **해결** | 사진 머리+`4박 5일 여행`·`담아 둔 물건 4개`+지금 할 일+주 버튼 |
| 18 | 마감(리터럴 dp, AppTextField, IconBadge 고정 dp) | **미해결** | 화면 리터럴 dp 299개(이전 약 330), spacing 토큰 없음. OutlinedTextField 9곳 직접 호출. `IconBadge(size = iconBadge)` 고정 |

## 남은 문제
(심각도 순. 근거 위치는 코드 파일:줄)

1. **03 vs 18(+10·12): '입국 카드' 카드가 두 벌이고 문구도 다름 (중간)**
   - 문제: `CountryScreen.FormCard`(CountryScreen.kt:709)와 `TabScreens.FormCard`(TabScreens.kt:167)는 같은 양식을 같은 순서(제목→비용→내는 때→버튼→출처)로 그리지만 구현이 따로 있습니다.
     - 비용 칩: 03은 `InfoChip`+`form_fee_chip_label`(`무료 입국 신고 비용`), 18은 `FactChip`+`fact_label_form_fee`(`무료 입국 카드 비용`)
     - 같은 행동의 버튼 라벨: 03 `입력 도와받기`, 18·10·12 `입국 카드 준비하기`
     - 날짜 붙이기 도우미 2벌: `glueMonthDay`와 `keepMonthDay`가 각자 `MonthDay` 정규식을 갖고 있음
   - 처음 쓰는 사람에게는 '입국 신고'와 '입국 카드'가 다른 물건처럼 읽힙니다.
   - 수정:
     - components에 `EntryFormCard(form, eyebrow, step, primary, onStart)` 하나를 두고, 비용은 `InfoChip` 하나로 그립니다.
     - 버튼 라벨은 화면과 관계없이 하나로 고정합니다.
     - `keepMonthDay`는 KoreanText.kt로 옮깁니다.
     - 테스트에 '같은 양식이면 03·18의 비용 라벨과 버튼 라벨이 같다'는 단언을 둡니다.

2. **04·05: StepList 문장형이 아직 굵은 글 벽 (중간)**
   - 문제: 수정 C가 `StepList(sentence = true)`를 만들었지만 24 '여권을 잃어버렸어요'에만 썼습니다. 04 e-VOA 4단계(`제출 뒤 요약 화면에 'Apply for Visa On Arrival (VOA)' 버튼이…`)와 05 지도 저장 3단계는 2~3줄 titleMedium SemiBold 그대로입니다. 같은 부품인데 화면마다 굵기 규칙이 다릅니다.
   - 수정: 호출부가 고르지 않게 합니다. `StepList`가 '단계 글이 한 줄 제목(20자 이하·문장부호 없음)이면 Title, 아니면 Sentence'로 스스로 판정하게 합니다. 기본 Title은 홈 출국 순서처럼 짧은 제목에만 남습니다.

3. **21 vs 20: 같은 '꼭 채울 빈칸'을 두 가지로 표시, 21에 노란 태그 9회 (중간)**
   - 문제: 20은 칸 끝 `!` 아이콘(Caution)으로 바뀌었습니다. 21 `OnSiteField`(ManualModeScreen.kt:271)는 행마다 `! 꼭 채워요` 태그를 달아 한 화면에 9번 나옵니다. 시작선도 위 `KeyValueRow`보다 4dp 안쪽(x=44dp vs 40dp)입니다.
   - 수정: 공용 `RequiredMark`(아이콘+TalkBack `꼭 채워야 하는 칸`)를 만들어 20의 trailingIcon과 21에서 같이 씁니다. `OnSiteField`는 `KeyValueRow(value = null, badge = RequiredMark, supporting = 도움말)`로 흡수합니다.

4. **app/src/test: 감사 그물이 지난번 그대로 (중간)**
   - 문제:
     - ① `A11yAuditTest`는 393dp(sdk36/31)에서만 돕니다.
     - ② `wordBreaks`는 `breaks`에만 쌓이고 `problems`에 들어가지 않아 실패하지 않습니다(A11yAuditTest.kt:127·134).
     - ③ `before = t[end - 1]`이 WORD JOINER면 한글로 보지 않아 API 33 미만 `keepWords` 문자열의 강제 분절을 놓칩니다(A11yAuditTest.kt:74).
     - ④ `hasVisualOverflow` 단언은 `BundleDCaptureTest`에만 있습니다.
     - ⑤ 수정 C 2.3절의 `(TDAC)/를` 버그는 감사가 `ko-rKR`로만 돌아서 못 잡았습니다. 기기 언어가 바뀌면 다른 결과가 나오는 영역인데도 감사 구성은 한국어 하나뿐입니다.
   - 수정:
     - 감사 구성을 늘립니다: `w360dp`·sdk31·200%, `en-rUS` 기기 언어·100%.
     - 줄바꿈 위반을 실패로 승격합니다.
     - 줄 끝에서 보이지 않는 문자를 건너뛴 앞 글자로 판정합니다.
     - 모든 Text 노드에 `hasVisualOverflow == false`를 단언합니다.
     - 핵심 6화면(01·03·20·24·30·스토어 01)은 기존 `captureToImage`로 허용 오차 diff를 겁니다(새 라이브러리 없이).

5. **22·29: 버튼 변형 규칙이 부품에 없음 (중간~낮음)**
   - 문제:
     - 22 `현지에서 먹기로 담기`는 `SecondaryButton(tone = Neutral)`이라 흰 바탕+1dp LineStrong입니다. 같은 화면 위쪽의 비선택 필터 칩(`먹거리`·`기념품`)과 같은 모양이라, 버튼이 아니라 '고르지 않은 선택지'로 읽힙니다.
     - `DangerButton(fillWidth = false)` 기본값 때문에 29의 카드 단독 파괴 행동만 끝 정렬로 남았습니다.
   - 수정: Neutral 톤 Secondary는 SelectChip과 모양을 구분합니다(채움 SurfaceSunken+테두리, 또는 `QuietButton`). DangerButton에는 `placement = CardAction(폭 전체) | ItemAction(끝 정렬)`를 필수 인자로 둬서 호출부가 의미로 고르게 합니다.

6. **27·30 등(200%): 한 목록 안에서 행마다 쌓기 판정이 달라 모양이 섞임 (중간~낮음)**
   - 문제: `BadgeTitleLayout`은 행마다 '제목이 옆에 들어가는지'로 쌓기를 정합니다(KoreanText.kt:370). 그래서 27 설정 200%에서 `여권·예약 서류 관리`는 배지 윗줄+제목 아래, 바로 다음 `같이 가는 사람`은 배지 옆 제목입니다. 한 묶음 안에서 시작선이 들쭉날쭉합니다.
   - 수정: `ListGroup`이 `LocalGroupStack`을 내려서 '한 행이라도 쌓이면 묶음 전체를 쌓기'로 맞춥니다(묶음 단위 측정 1회). 또는 Stacked 레이아웃이면 배지 있는 행은 모두 쌓기로 고정합니다.

7. **전역 마감: 지난번 18번이 그대로 (낮음~중간)**
   - 리터럴 dp: 화면 코드에 299개 남아 있습니다(8dp 77, 4dp 59, 2dp 21, 6dp 18, 10dp 6, 3dp 2). spacing 토큰이 없어 4pt 밖 값이 계속 생깁니다.
   - 입력칸: `OutlinedTextField` 9곳을 직접 호출합니다. 17 날짜칸에는 스펙 D20의 `leadingIcon = CalendarMonth`가 없고, 33·34에는 leadingIcon이 있고, 20은 trailing `!`입니다.
   - IconBadge: `IconBadge(size = iconBadge)`가 고정 dp라(IconBadge.kt:123) 200%에서 배지와 선택 카드 앞 아이콘이 제목 대비 왜소합니다(24·27·20 sdk31 200%).
   - 수정: `Spacing(xxs 2/xs 4/s 8/m 12/l 16)`을 둡니다. `AppTextField(label, supporting, leading, required)` 하나로 9곳을 흡수합니다. 배지는 `max(iconBadge, textIconSize × 1.25)`(상한 1.5배)로 키웁니다.

8. **19·31·34: 체크 줄 부품이 화면 파일에 있고 체크 색이 2계열 (낮음)**
   - 문제: `ConsentRow`는 `wallet/PassportScreens.kt:370`의 `internal`인데 `PresentScreens`가 import해서 씁니다. 공용 부품이 components 밖에 있습니다. 체크 색도 갈립니다. 19 꼭 챙길 물건은 `SuccessText` 채움, 31·34 동의는 `Accent` 채움입니다.
   - 수정: `components/Controls.kt`에 `CheckRow`로 옮깁니다. 체크박스 색은 Accent 하나로 하고, '챙겼어요' 같은 완료 의미는 이미 있는 상태 태그(✓ 챙겼어요)가 맡게 합니다.

9. **35(+일부 태그): AccentSoft 채움 알약이 선택 칩처럼 보임 (낮음)**
   - 문제: `라이선스: CC BY 2.0`은 누를 수 없는 정보인데 AccentSoft 채움+Accent 글자 알약입니다. C.2의 'AccentSoft 채움은 선택됨에만'과 어긋납니다.
   - 수정: `InfoChip`이나 `StatusTag(Neutral)`로 바꿉니다.

10. **33 vs 17: 비슷한 격자 타일인데 선택 계열이 다름 (낮음)**
    - 문제: 33 예약 서류 종류(3열 아이콘 타일)는 Accent 채움, 17 나라(2열 썸네일 카드)는 AccentSoft+CheckCircle입니다. C.2 경계가 '작은 타일 vs 큰 카드'라는 크기 기준이라, 비슷한 크기에서 결과가 갈립니다.
    - 수정: 경계를 모양으로 명문화합니다(예: '라디오 마크를 둘 자리가 있는 카드 = ②, 글자·아이콘만 있는 칩·타일 = ①'). 부품 문서와 C.2에 적습니다.

11. **운영자 결정 항목 중 영향이 큰 것 (결정 대기 — 감점하지 않음)**
    - 결정 10 `ReturnCheckCard` 통일: 01·02·06·15·22 다섯 화면에 같은 긴 카드가 나옵니다(문장 4줄+출처 3줄+링크 3개). 앱에서 가장 긴 반복 블록이라 delight·스크롤 길이에 가장 크게 영향을 줍니다. '홈만 주제 요약' 선택을 권합니다.
    - 결정 2 `원어민 검수 전`: 스토어 05 첫 화면 한가운데 노란 배지로 보여 전환에 불리합니다. 업로드(결정 1) 전에 정하는 것이 좋습니다.

## 잘된 점
- 부품 통합이 실제로 끝났습니다. 화면 임시 부품 0개, fontScale·하드코딩 색·고정 sp·모서리 0건입니다. `LayoutClass` 한 곳 판정, `KeyValueRow` 슬롯, `SelectableCard`+선택 규칙 문서(C.2)가 있습니다.
- `FitText`를 잘 설계했습니다. SubcomposeLayout 없이 고유 크기를 구현해 `IntrinsicSize.Min` 그리드 안에서도 동작합니다. 전화번호와 `IDR 500,000`·`4박 5일`이 칸 폭에 맞춰 한 줄로 들어가고, TalkBack은 한 덩어리로 읽습니다.
- 줄바꿈 언어 고정(`ReadyPortLineBreak.Korean`)이 실제 사용자 버그를 막았습니다. 기기 언어가 영어인 폰에서 생기던 `(TDAC)/를` 분절입니다. 현지어 스타일은 제외해 글꼴 선택도 지켰습니다.
- 사진 경로가 정돈됐습니다. `PhotoCache`(LruCache)+inSampleSize+IO 디코드에 렌더 단계 밝기 보정을 더했고, 사진 파일은 그대로 두고 CC BY 변경 표시까지 남겼습니다.
- 스토어 8장은 앱 실제 화면 그대로입니다. 견본 픽스처(HONG GILDONG, 빈칸 없음)와 비제휴 캡션을 써서 과장 없이 일관됩니다.
