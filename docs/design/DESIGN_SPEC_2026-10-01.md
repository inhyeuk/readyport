# 레디포트 디자인 개편 스펙 v2 — "카드뉴스형" 리디자인 (2026-10-01)

> 요청(제품 소유자): "전체적으로 디자인을 세련되게 수정해줘. 왠만한 제목이나 내용은 이미지, 아이콘, 카드 뉴스 형태로 처리될 수 있도록."
> **최종 결과는 Play 스토어에 올리기 전에 사용자(소유자) 컨펌을 먼저 받는다.** 이 문서는 구현 지시서이며, 업로드·출시 트랙 변경은 포함하지 않는다(8장 마지막 단계 참고).

- 근거 자료: 기본 모드 갤러리 타일 64장(`app/build/gallery/tiles/`, 393dp), 쉬운 모드 전체 캡처 4장(`easy/01_home`, `03_country-entry-TH`, `12_today-traveling`, `22_settings`), 코드(`ui/theme`, `ui/components`, 각 화면), 테스트(`app/src/test/.../ui/**`), 팩 데이터(`packs/src/**`).
- 전문가 5인: ① 시니어 프로덕트/비주얼 디자이너 ② UX·접근성 ③ 카드뉴스·인포그래픽 에디터 ④ Compose 디자인 시스템 엔지니어 ⑤ 브랜드·그로스(Play 전환율).
- 이 문서가 전문가 5인의 의견이 서로 다른 부분을 **최종 결정**한다(1.3절). 구현자는 이 문서를 따르고, 문서와 다른 판단이 필요하면 구현 전에 질문한다.
- **v2.1 (2026-10-01)**: 비평 2건(수정 요구 58개)을 본문에 반영했다. 항목별 처리 결과는 9장에 있다. 본문과 9장이 다르면 본문이 우선이다.

---

## 1. 전문가 5인 검토 요약

### 1.1 점수 (10점 만점)

| 전문가 | visual | clarity | accessibility | consistency | delight | 한 줄 평 |
|---|---|---|---|---|---|---|
| ① 비주얼 디자이너 | 5.0 | 6.0 | 7.0 | 5.0 | 3.5 | 사진 머리글은 좋다. 그 아래는 '흰 카드+회색 테두리+긴 문단+알약 버튼'만 반복되는 문서라서 결론이 묻힌다 |
| ② UX·접근성 | 6.5 | 5.0 | 5.5 | 5.5 | 4.5 | 입력칸 이름 누락, 선택 대비 1.07:1, 48dp 고정 40곳, 확인 없는 삭제가 실제 사용자를 다치게 한다 |
| ③ 카드뉴스 에디터 | 5.5 | 6.0 | 7.5 | 6.0 | 4.0 | 앱 전체 아이콘이 15개 남짓이다. 90일·무료·800달러·1155처럼 숫자로 끝나는 정보를 문장에 묻어 두었다 |
| ④ Compose DS 엔지니어 | 5.5 | 6.5 | 7.0 | 6.0 | 4.0 | 화면별로 고치지 말고 부품 20개를 먼저 만들어야 한다. 테스트가 찾는 문자열과 출처 규칙을 부품이 지키게 한다 |
| ⑤ 브랜드·그로스 | 6.0 | 6.0 | 7.0 | 5.0 | 4.0 | 처음 5초 안에 '칸을 채워 준다·공식 출처·폰에만 저장'이 안 보인다. 내부 ID 노출은 스토어 리뷰감이다 |
| **평균** | **5.7** | **5.9** | **6.8** | **5.5** | **4.0** | 뼈대(토큰·쉬운 모드·사진)는 있고, **표현 체계(아이콘·숫자·카드 위계)와 마감**이 없다 |

목표(개편 후 재검토): 모든 항목 평균 **7.5 이상**, delight 7.0 이상.

### 1.2 우선순위 상위 15개 문제

| 순위 | 문제 | 화면 | 지적 | 심각도 | 해결 (4장 부품) |
|---|---|---|---|---|---|
| 1 | 출처 표시가 깨질 수 있는 구조. 갤러리 픽스처에서 내부 ID(`tat_chanthaburi`, `customs_allowance`, `apqa_plant`)와 `출처 출처` 중복이 보인다(운영 ViewModel은 이름을 넘기므로 **픽스처 결함**). 운영 코드도 이름을 못 찾으면 `?: id`로 ID를 그대로 보인다(방어 부족). **실제 운영 버그**는 도움 탭 `HelpViewModel`(`pack/PackViewModels.kt`)의 `commonSourceName = index.sources.firstOrNull()?.name` — `common.first().source`로 이름을 찾지 않아 다른 출처 이름이 붙을 수 있다 | 13, 16, 18, 20 (+갤러리 픽스처) | 5/5 | 높음 | 픽스처 수정 + 방어적 폴백(`SourceRef`·`SourceFooter v2`, ID 폴백 금지 → `공식 안내`) + `commonSourceName`을 출처 ID로 풀도록 수정(D 묶음), 출처 ID 전수 테스트 |
| 2 | 핵심 결론(90일·무료·도착일 포함 3일·IDR 500,000·220V·800달러)이 굵은 문단 속에 묻힘 | 03, 04, 05, 01·06·13·18(귀국) | 5/5 | 높음 | `StatTile`/`FactGrid`(타일마다 출처), `CardNewsCard` — 기간 타일은 팩 문장 수정 뒤(D11) |
| 3 | 보조 버튼(회색 `OutlinedButton`)과 고지 카드(`CardTone.Notice`)가 비활성처럼 보임. 비제휴 고지는 버튼처럼 보임 | 거의 전부 | 5/5 | 높음 | `SecondaryButton`(tonal + 1dp 테두리), `NoticeBanner`(흰 바탕 + 막대, AccentSoft 채움 금지 — D21) |
| 4 | 가장 급한 긴급 번호가 가장 약한 요소(주황 박스 안 작은 회색 알약, 아이콘 없음, 쉬운 모드에서도 48dp) | 20 | 5/5 | 높음 | `EmergencyCallTile` 2열, 첫 긴급 번호는 화면 위쪽 |
| 5 | 여행 중 2×2 버튼이 아이콘 없는 96dp 알약 → 달걀 모양, 쉬운 모드에서 `숙소로 돌아/가기`로 끊김 | 12 | 5/5 | 높음 | `IconTile`(세로/가로 자동), 칸 폭 기준 1열 전환(D4), 줄바꿈 규칙(3.2) |
| 6 | `귀국 전 확인` 노란 글 덩어리(불릿 4 + 출처 3줄 + 링크 3개)가 4~5개 화면에 똑같이 반복, 경고색 남용 | 01, 02, 06, 13, 18 | 5/5 | 높음 | `ReturnCheckCard v2`(아이콘 행 + 출처 한 줄 + 링크 행), 홈은 접힌 요약 |
| 7 | 되돌릴 수 없는 삭제가 파란 주 버튼이거나 링크처럼 보이고 확인 없이 실행됨 | 13, 14, 24 | 4/5 | 높음 | `DangerButton`, `DestructiveConfirm` |
| 8 | 꼭 챙길 물건: 스위치가 꺼져도 `있어요` 고정 표시, 꺼진 스위치 트랙이 M3 기본 보라(#E6E0E9) | 16 (+22) | 5/5 | 높음 | `CheckRowCard`, ColorScheme `surfaceContainer*` 전부 지정 |
| 9 | 입국 카드 확인: 입력칸 3개가 TalkBack 이름 없음(placeholder만), 12행마다 출처 칩 반복, 화면 절반이 노란 경고색, 빈칸 안내가 긴 문장 | 17 | 4/5 | 높음 | `OutlinedTextField(label, supportingText)`, 출처별 그룹 헤더, 흰 카드 + 주의 막대, `첫 빈칸으로 가기` |
| 10 | 선택 상태가 옅은 파랑뿐(배경 대비 1.07:1), 세그먼트 체크 아이콘까지 제거 | 03~06, 07, 14, 17, 18, 20, 26 | 3/5 | 높음 | 선택 = Accent 채움 + 흰 글자 + Check 아이콘(`ChoiceSegments`, `SelectChip`) |
| 11 | 앱의 중심 이야기인 여행 6단계가 6dp 막대 + 9sp까지 줄어드는 라벨 | 09~13 | 5/5 | 중간 | `JourneyStepper`(아이콘 노드) |
| 12 | 홈 스크롤 과다(200dp 나라 카드 5장 + 같은 칩 반복), 쇼핑 사진 폭 잘림(`fillMaxWidth` 누락), 베트남 호이안 사진이 일본·태국 쇼핑에 쓰임 | 01, 02, 06 | 5/5 | 중간 | 2열 `CountryPhotoTile`, 사진 규칙(3.7절), `photo_market` 사용 중단 |
| 13 | 첫 실행 화면이 회색 바탕 글자+버튼 2개뿐, 브랜드·사진 없음 | 00 | 5/5 | 중간 | 사진 히어로 + `ChoiceCard` 2장 |
| 14 | `준비 중이에요` 카드 7곳 이상 노출 → 미완성 인상 | 15, 18, 23, 24, 25 | 4/5 | 중간 | `ComingSoonGroup` 한 줄 묶음(맨 아래), NFC 비활성 버튼 제거 |
| 15 | 마감 결함: 하드코딩 `heightIn(min = 48.dp)` 40곳(쉬운 모드 미달), 고정 sp(30/34/44/56), 설정 스위치와 글자 붙음, 한 음절 줄바꿈(`좋아/요.`, `어디예/요?`) | 전역 | 4/5 | 중간 | `Modifier.minTouch()`, 타입 역할(display/stat), `ListRow` 간격, 줄바꿈 규칙 |

### 1.3 쟁점과 최종 결정

| # | 쟁점 | 의견 | **결정** | 이유 |
|---|---|---|---|---|
| D1 | 한글 글꼴 | ①③⑤ Pretendard 번들 / ②④ 언급 없음 / PRD 8.4는 `[확인 필요]` | **권고: Pretendard(SIL OFL 1.1) 번들 채택. 단 운영자(사용자) 결정 항목.** 채택 전까지 시스템 글꼴로 모든 설계가 성립하게 만든다(글꼴은 독립 단계 0b). 폰트 파일 다운로드는 사용자 승인 후에만. **승인 요청 때 함께 낼 것**: ① LICENSE 원문의 Reserved Font Name(`Pretendard`) 확인 — 직접 서브셋하면 수정본이라 같은 이름을 못 쓸 수 있으므로 원 저작자 배포 서브셋을 수정 없이 쓰거나 family 이름을 바꾼다 ② OFL 전문(`OFL.txt`)을 assets에 넣고 화면에서 볼 수 있게 함 ③ 실측 용량 비교표(한글 11,172자 전체 / KS X 1001 2,350자 + 시스템 글자 폴백 / 가변 글꼴 서브셋) | 세련됨에 가장 큰 영향. 네트워크 없음(번들). PRD가 결정을 운영자에게 맡겼음 |
| D2 | 카드 경계: 테두리 vs 그림자 | ① 그림자 / ②④ 1dp 테두리 유지 | **흰 정보 카드: 테두리 제거 + 부드러운 그림자(2dp, Ink 8%/12%).** 상태 카드(주의·위험·성공): 그림자 없이 연한 바탕 + **왼쪽 4dp 색 막대**. 사진 카드: 그림자·테두리 없음. 입력칸·꺼진 스위치 등 **조작 요소 경계는 `LineStrong`(#6B7589, 4.63:1)** | '와이어프레임' 인상 제거 + 조작 요소 3:1 유지. 단 Robolectric 소프트웨어 렌더링 캡처에는 그림자가 안 그려질 수 있어, 컨펌 캡처 전에 확인한다(8장 0단계·3단계) |
| D3 | 모서리 | ① 8/12/16/20/28 / ④ 10/16/20/28 / 현재 알약 버튼 | **8 태그 · 12 입력칸·아이콘 배지·썸네일 · 16 모든 버튼·세그먼트·타일 · 20 카드 · 28 히어로·다이얼로그.** 알약(완전 둥근) 모양은 칩·인디케이터에만. PRD 5장 공통 규칙 `모서리 14~20dp`와 다르므로 **PRD 5장 문구 변경도 운영자 확인 항목**(8장 3단계 문서 갱신 목록) | 두 줄 버튼이 달걀 모양이 되는 문제 제거 |
| D4 | 2열 → 1열 전환 기준 | ①③⑤ fontScale>1.3 / ④ 쉬운 모드면서 1.15 / ⑤ 1.5 | **쉬운 모드면 항상 1열(가로형 타일). 기본 모드는 2열 한 칸의 폭(dp)을 fontScale로 나눈 값이 150 미만이면 1열**: `colDp = (창 폭dp − 2×screenPadding − gap) / 2`, `colDp / fontScale < 150 → 1`(393dp 폭이면 fontScale 약 1.14 이상, 360dp 폭이면 약 1.03 이상, 340dp 미만이면 항상 1열). 한 곳(`rememberGridColumns`)에서만 계산. `PhraseTile`(팩 문구)은 항상 1열 | 쉬운 모드 사용자는 큰 글자, 1열이 안전. API 33 미만(S10)에서는 `LineBreak.Heading`이 효과가 없어, fontScale 1.1~1.29에서도 2열 라벨이 음절 단위로 끊긴다(비평 반영) |
| D5 | 홈에서 출국 순서·귀국 전 확인을 뺄지 | ② 홈에서 빼고 상세로 / ①③ 요약 유지 | **유지하되 압축**: 출국 순서는 아이콘 `StepList`(한 줄 제목), 귀국 전 확인은 `ReturnCheckCard(compact = true)` — 제목·아이콘 행·출처는 보이고 문장은 `자세히 보기`로 펼침. 쇼핑 사진 카드는 삭제 | 새 상세 화면·라우트 없이 스크롤 약 40% 감소 |
| D6 | 도움 탭 순서 | ①② 긴급 전화를 맨 위 / PRD 5.11·테스트는 선택 문장 카드가 첫 화면에 보여야 함 | **나라 칩 → 대표 긴급 번호 1개(폭 전체 `EmergencyCallTile`, 보통 관광경찰 1155) + 바로 아래 `SourceFooter` + `다른 긴급 번호 보기` → 선택 문장 카드 → 문장 타일 → 긴급 번호 전체 그리드 → 대사관 → 이럴 땐 이렇게 → 어느 나라에서나.** PRD 5.11 순서 보완은 운영자 확인 항목으로 기록 | `helpTabWorksOffline`가 스크롤 없이 태국어 문장을 찾는다(851dp 안). 급할 때 번호도 첫 화면에 보임 |
| D7 | 선택 표시 | ① 흰 칸+그림자(iOS식) / ② Accent 채움+체크 | **Accent 채움 + 흰 글자 + Check 아이콘.** 세그먼트 트랙은 `SurfaceSunken` + 1dp `LineStrong` 테두리(트랙과 Ground 대비가 1.04:1이라 테두리가 경계를 맡음, Ground 대비 4.25) | 대비(6.0:1)와 색 외 단서 동시 충족 |
| D8 | 삭제 확인 | ② 모든 삭제에 대화상자 | **지갑 여권·예약 삭제, 여행 삭제, 내 정보 비우기, 보여 주기 서류 삭제(21), 같이 가는 사람 삭제(27) → `DestructiveConfirm` 대화상자**(보안 화면에서는 `secure = true`, 본문에 개인정보 없음). **13 귀국 단계의 `여권 정보를 지울까요?` 카드는 그 자체가 확인 단계이므로 대화상자 없음**(버튼만 `DangerButton`으로, `7일 미루기`는 tonal) | `returnExplainsBeforeDestroyAndCanPostpone` 테스트가 버튼 한 번에 삭제를 기대. 이중 확인은 과함 |
| D9 | 입국 카드 확인의 비활성 주 버튼 | ② 비활성 대신 `빈칸으로 가기` | **`맞아요, 입력해 주세요`는 빈칸이 있으면 계속 비활성(읽히는 비활성 색)**, 그 위에 `빈칸 N개 남았어요` 배지 + tonal `첫 빈칸으로 가기` 추가 | `confirmNeedsRequiredChoicesThenEnables` 테스트, 실수 제출 방지 |
| D10 | 도착 단계 체크리스트화 | ①②⑤ 체크 가능한 단계 → `다 했어요` 제거 | **이번 범위: 체크 없음(새 상태 저장 없음). `StepList` + `다 했어요`는 tonal 버튼(TaskAlt).** 체크 저장은 후속 과제 | ViewModel·저장소 변경 없이 디자인만 |
| D11 | 팩 JSON에 `icon`·`headline` 필드 추가 | ③ 추가(스키마 올림, 재서명) / ④ 앱 쪽 매핑 | **이번 개편은 팩 스키마를 바꾸지 않는다.** 아이콘은 앱의 `IconKeys.kt`가 기존 ID(`emergency.id`, `essentials.id`, `section.id`, `phrase.id`, `category`, `source`)로 매핑. 숫자 타일은 **이미 구조화된 필드**(`stay_limit_days`, `fee_ko`(짧을 때), `window_days_including_arrival`, `power.*`, 전화번호)에서만 만든다. `귀국 전 확인`의 800달러 같은 값은 팩에 구조 필드가 없으므로 **앱 문자열로 하드코딩하지 않는다**(정책 값 위조 방지). 팩 확장은 2단계 선택 과제(부록 A). **기간(`window_days_including_arrival`) 타일은 이번 릴리스에서 그리지 않는다**: 태국 팩의 `3`(도착일 포함 3일 = 5월 2~4일)과 같은 화면 불릿 `도착 3일 전부터 도착하는 날까지`(4일 범위)가 서로 모순이라, 큰 타일이 모순을 키운다. 운영자/ARIA가 팩 원문을 고치고 팩 lint(부록 A)가 통과한 뒤 되살린다(앱 하드코딩 금지) | 작업 규칙 6(사실 지어내기 금지)·서명 팩·ARIA 파이프라인 보호 |
| D12 | 아이콘 스타일 | ⑤ Outlined 통일 | **`Icons.Outlined.*` + 방향 있는 것은 `Icons.AutoMirrored.Outlined.*`.** Filled는 '켜짐·선택' 상태(선택된 탭, 찜 켬, 체크된 항목)에만 | 일관성 |
| D13 | 도움 탭 아이콘 | ① Sos / ⑤ SupportAgent 유지 | **탭은 `SupportAgent` 유지**(문장 카드도 포함하는 탭). `Sos`는 긴급 번호에만 | 탭 의미 보존 |
| D14 | 귀국 단계 아이콘 | ① ConnectingAirports / ④ Luggage / ③ Cottage / ② House | **`Icons.Outlined.Cottage`(집으로)** — `Luggage`는 `내 여행` 탭, `Home`은 홈 탭과 겹침 | 중복 없는 아이콘 |
| D15 | `준비 중` 기능 | ⑤ 릴리스 빌드에서 숨김 / ③ 한 줄 묶음 | **전체 카드로 노출하지 않는다. 화면 맨 아래 `곧 추가돼요` 한 줄 묶음(`ComingSoonGroup`)** — 빌드 플래그 없이. 항목마다 `semantics(mergeDescendants = true) { disabled() }`를 줘서 TalkBack이 `사용 안 함`으로 읽고 `assertIsNotEnabled()`가 통과한다 | 테스트(`passport_chip_soon` + `assertIsNotEnabled`)와 정직성 유지 |
| D16 | 나라 카드 TalkBack 이름 | ② 칩 정보까지 `contentDescription`에 | **`contentDescription = home_country_open`(예: `태국 안내 열기`) 그대로, 칩 정보는 `stateDescription`으로.** 칩은 `ChipSpec(icon, text)`로 받고 `text`에는 기존 `home_chip_*` 완성 문장을 그대로 넣는다(값·라벨로 쪼개지 않음). 타일은 `semantics(mergeDescendants = true)`만 쓰고 `clearAndSetSemantics`는 금지(칩 Text가 트리에서 빠지면 `onAllNodesWithText(home_chip_visa_free)`가 깨짐) | `OfflinePackTest`·`ReadyPortRootTest`가 정확히 이 설명과 칩 글자를 찾는다 |
| D17 | 번호 붙은 문자열 | ④ 문자열 그대로 두고 아이콘만 | **`today_departure_step1~5`, `today_arrival_step1~5`, `explore_maps_step1~3`의 `N. ` 접두를 문자열에서 지우고 번호는 `StepList` 원 배지로 그린다.** 테스트는 리소스를 `getString`으로 찾으므로 통과. **접두 삭제와 같은 0단계에서 세 곳(HomeScreen 출국 순서, TodayScreen StepsCard 2곳, CountryScreen 지도 단계)을 최소한 `StepList`로 바꾼다** — 묶음별 머지 사이에 번호 없는 순서 목록이 생기지 않게 | 번호 중복 표시 제거 |
| D18 | 어두운 바탕 위 색 | (비평) Help·InkTertiary·DangerText·Accent가 Navy 위에서 2.2~2.8:1 | **`onDark 내용 세트`만 쓴다(3.1절)**: Accent·Navy·AccentDeep·BrandBlue·사진(스크림) 위 글자·아이콘은 Surface, White85/White80, Gold(Navy·AccentDeep 위에서만) 중 하나. 금지 쌍은 테스트가 코드에서 찾아 막는다(3.8절). 어두운 카드 안 파괴 버튼은 두지 않는다(카드 밖 별도 줄 일반 `DangerButton`) | 대비 4.5:1·3:1 보장 |
| D19 | 쉬운 모드 글자 상한 | PRD 3.2 `쉬운 모드 18~28sp` / 스펙의 displayLarge 56·displayMedium 36·displaySmall 32·stat 34 | **현지인에게 보여 주는 글자(현지어 전체 화면·기사님 카드·선택 문장), 히어로 이름, 큰 숫자(stat)는 PRD 3.2의 예외로 운영자 승인을 받아 문서에 남긴다(8장 3단계).** 쉬운 모드 하단 탭은 autoSize 최소 18sp, 모자라면 줄이지 않고 2줄 | 기존 하드코딩을 공식화하는 것이므로 승인 필요 |
| D20 | 여행 날짜 입력 | 원안 M3 `DatePickerDialog` / (비평) 달력 모드는 가로 스와이프 `LazyRow`, 날짜 칸 고정 40dp, UTC 하루 밀림 | **지금의 텍스트 입력(`YYYY-MM-DD`)을 유지**하고 `leadingIcon = CalendarMonth`, `supportingText`에 `11월 3일 (화)`를 보인다. M3 DatePicker는 쓰지 않는다 | 7장 1번(가로 스와이프 금지)과 200% 제약 동시 충족, 새 시간대 변환 위험 없음 |
| D21 | 누를 수 있는 것과 없는 것의 구분 | (비평) `NoticeBanner`와 tonal `SecondaryButton`이 둘 다 AccentSoft 바탕 + 아이콘 + 굵은 글자 | **누를 수 있는 tonal 버튼은 항상 1dp `tone.content` 테두리**(Accent면 Ground 대비 6.22). **누를 수 없는 폭 전체 띠(배너·카드)에는 AccentSoft 채움을 쓰지 않는다** — `NoticeBanner(Notice)`는 흰 바탕 + 4dp Accent 막대. 작은 배지·`StatTile`·`StatusTag`는 예외(테두리·Accent 글자 없음) | 문제 #3을 나이 든 사용자에게도 확실히 해결 |

---

## 2. 디자인 원칙 (8)

1. **결론 먼저 — 아이콘 → 큰 값 → 한 줄 → 출처.** 모든 정보 카드는 `eyebrow(무엇) → 결론(큰 숫자·예/아니오·아이콘) → 근거 한두 줄 → 출처·최종 확인` 순서다. 문장 속 숫자가 구조화 데이터에 있으면 `StatTile`로 꺼낸다.
2. **카드뉴스는 세로로만, 탭으로만.** 세로 스택과 2열 그리드(`LazyColumn` 안의 `Row`+`weight`)만 쓴다. 가로 캐러셀·페이저·스와이프 금지. 쉬운 모드·큰 글자는 자동 1열(D4).
3. **사진은 장소, 아이콘은 뜻.** 번들 사진은 히어로·나라 선택·섹션 표지에만. 정보 단위는 벡터 아이콘. 장소와 맞지 않는 사진(호이안 등불)은 쓰지 않는다.
4. **색은 뜻이다.** Accent 채움 = 화면당 하나의 다음 행동 / Accent 연한 바탕 = 선택·안내 / Navy = 보안·현지인에게 보여 주기·오프라인 / Help(주황) = 긴급 / Caution·Danger = 진짜 주의·금지에만 / 회색 = 진짜 비활성에만. 상태는 **색 + 아이콘 + 글자** 세 가지로. **누를 수 없는 폭 전체 띠(배너·카드)에는 AccentSoft 채움을 쓰지 않고, 누를 수 있는 tonal 버튼은 항상 테두리를 가진다(D21).** 어두운 채움(Accent·Navy·AccentDeep·BrandBlue·사진) 위에는 `onDark 내용 세트`만 쓴다(D18).
5. **신뢰 요소는 부품이 강제한다.** `SourceFooter`는 ID를 받지 않고, 정책 카드는 출처 없이는 만들 수 없다. 정부 비제휴·`제출은 직접`은 입국 화면 첫 항목 `NoticeBanner`, `내 정보는 이 휴대폰에만`은 보안 화면 맨 위 `SecurityBanner`. 모양만 바꾸고 문구·위치는 유지한다.
6. **높이는 내용이 정한다.** `heightIn(min)`만 쓴다. 고정 `height`, 고정 sp, `maxLines`로 자르기, 말줄임표(ellipsis), 9~10sp까지 줄이는 autoSize 금지. 모든 크기는 토큰에서. 글자를 품는 원·배지(번호 원, 이니셜 아바타)도 글자 크기에 맞춰 커진다(`defaultMinSize` + padding, 또는 sp로 정한 크기). 긴 글을 줄이고 싶으면 줄 수가 아니라 **글자 수 기준**으로 정한다: 60자를 넘으면 첫 문장만 보이고 나머지는 `ExpandableDetail`(첫 문장 경계를 못 찾으면 전체 표시). 출처·고지는 이 규칙에서 제외(항상 전체).
7. **한 화면 주 버튼 하나, 파괴적 동작은 빨강.** 파란 채움 `PrimaryButton`은 화면당 1개. 나머지는 tonal `SecondaryButton`·`IconTile`·`QuietButton`. 지우기는 `DangerButton` + (필요하면) 확인 대화상자.
8. **아이콘은 곁들일 뿐, 이름은 글자가 맡는다.** 아이콘·배지는 장식(`contentDescription = null`). TalkBack 이름·테스트가 찾는 문자열은 기존 리소스 그대로. 새 문구는 **새 리소스 키**로만 추가한다.

---

## 3. 디자인 토큰

파일: `ui/theme/Tokens.kt`, `ui/theme/Theme.kt`. 기존 토큰 값은 **바꾸지 않고 추가만** 한다(Accent 계열 유지, PRD 8.4 표와 호환). 모든 글자 조합은 4.5:1 이상(직접 계산값 병기).

### 3.1 색

**기존(유지)**

| 토큰 | 값 | 쓰임 |
|---|---|---|
| Accent | #1F4FD1 | 주 버튼, 선택 채움, 링크, 아이콘 강조 (흰 글자 6.78) |
| AccentSoft | #E8EEFC | tonal 버튼 바탕, 안내 배너, 아이콘 배지 바탕 (Accent 글자 5.83, Ink 13+) |
| Ink | #16213A | 본문·제목 (Ground 14.66) |
| InkSecondary | #4A5468 | 보조 글자 (Surface 7.5, Ground 6.98, AccentSoft 6.55, CautionBg 6.97) |
| Ground | #F4F5F7 | 화면 바탕 |
| Surface | #FFFFFF | 카드 |
| Line / LineSoft | #D9DDE4 / #E3E6EB | 구분선(비텍스트, 장식) |
| CautionBg / CautionText / CautionBorder | #FFF4DC / #7A4100 / #E6B566 | 주의 (7.44) |
| DangerBg / DangerText | #FDECEA / #A12A22 | 금지·파괴 (6.40, 흰 바탕 7.32) |
| SuccessBg / SuccessText | #E3F4EC / #0F6B45 | 가능·완료 (5.74, 흰 바탕 6.54) |
| Help | #A8431A | 도움 탭·긴급 (흰 바탕 6.03, Ground 5.53) |
| Navy | #0B1A4D | 보안·보여 주기 카드 (흰 글자 16.6) |

**추가**

| 토큰 | 값 | 쓰임 | 검증 |
|---|---|---|---|
| AccentDeep | #1E3A8A | 히어로 그라데이션 끝, 여권 카드 그라데이션, AccentSoft 위 강조 글자 | 흰 글자 10.36, AccentSoft 위 8.91 |
| BrandBlue | #2B5CF2 | 아이콘 히어로 그라데이션 시작(런처 아이콘 그라데이션 #2B5CF2→#0B1A4D와 동일) | 흰 글자 5.36 |
| InkTertiary | #5B6577 | 출처·메타 캡션 전용 | Surface 5.88, Ground 5.39, AccentSoft 5.06, CautionBg 5.38 |
| SurfaceSunken | #EEF1F6 | 세그먼트 트랙, 칩 바탕, 설정 그룹 사이 | InkSecondary 6.72, Accent 5.99 |
| SurfaceHighest | #E6EAF0 | 꺼진 스위치 트랙, 비활성 버튼 바탕 | Ink 13.25, 비활성 글자 InkTertiary 4.87 |
| LineStrong | #6B7589 | 조작 요소 경계(입력칸, 꺼진 스위치 테두리·썸, 선택 안 된 칩 테두리) | 비텍스트 Surface 4.63, Ground 4.25 (≥3) |
| HelpSoft | #FBEDE6 | 긴급 타일·도움 배지 바탕 | Help 글자 5.27, Ink 13.98 |
| VioletSoft / VioletText | #EFEAFE / #5B3CC4 | 카테고리: 이동 | 6.19 |
| TealSoft / TealText | #E0F4F2 / #0B6B66 | 카테고리: 지도·오프라인 | 5.56 |
| Gold | #FFDC8F | Navy 위 여권 eyebrow 전용(런처 아이콘 금색) | Navy 12.52, AccentDeep 7.84 |
| White85 | Color.White.copy(alpha = 0.85f) | Accent·Navy·AccentDeep 카드 위 출처·eyebrow 글자 | **합성값**(바탕 위에 합성한 뒤 계산): Accent 위 5.37, AccentDeep 위 7.93 |
| White80 | Color.White.copy(alpha = 0.80f) | Navy·AccentDeep 위 보조 글자, 어두운 타일의 셰브론 | 합성값: Navy 위 10.85, AccentDeep 위 7.26 |

**`BadgeTone`** (아이콘 배지·타일·배너가 공유, `components/IconBadge.kt`에 enum):

| tone | container | content | 쓰임 |
|---|---|---|---|
| Accent | AccentSoft | Accent | 입국·서류·일반 정보 |
| Success | SuccessBg | SuccessText | 가능·완료·전기 OK |
| Caution | CautionBg | CautionText | 조건부 주의 |
| Danger | DangerBg | DangerText | 금지·삭제·사기 경고 |
| Help | HelpSoft | Help | 긴급·도움·쇼핑 포인트 |
| Violet | VioletSoft | VioletText | 이동 |
| Teal | TealSoft | TealText | 지도·오프라인 |
| Neutral | SurfaceSunken | InkSecondary | 준비 중·기타 |
| Navy | Navy | Surface | 밝은 바탕 위 보안·보여 주기 강조 배지 |
| OnDark (신규) | White 12% | Surface | **어두운 채움(Accent·Navy·AccentDeep) 위**의 배지(장식) — `CardNewsCard(Accent/Navy)`, emphasized `IconTile`, `EmergencyCallTile(large)` |

**onDark 내용 세트 (D18)** — Accent·Navy·AccentDeep·BrandBlue·사진(스크림/`PhotoChip`/검정 0.35 원) 위의 글자·아이콘·테두리는 아래에서만 고른다.

| 바탕 | 허용 | 금지(직접 계산 대비) |
|---|---|---|
| Accent | Surface 6.78, White85 5.37 | Accent 1.0, InkSecondary, Help |
| Navy | Surface 16.6, White80 10.85, White85 12.17, Gold 12.52 | Help 2.74, InkTertiary 2.82, DangerText 2.26, InkSecondary 2.17, Accent 2.44 |
| AccentDeep | Surface 10.36, White80 7.26, White85 7.93, Gold 7.84 | DangerText 1.42 |
| BrandBlue | Surface 5.36 | Gold 4.06, White85 4.34, White80 4.01 |

- 셰브론·Call 아이콘·eyebrow·보조 버튼 글자와 테두리는 모두 Surface 또는 White80/85. 어두운 카드 안의 4dp 막대는 쓰지 않는다(장식이 필요하면 Surface).
- 어두운 카드 안에는 `DangerButton`을 두지 않는다(카드 밖 별도 줄의 일반 `DangerButton`).

**ColorScheme(`Theme.kt`)** — M3 기본 보라가 새지 않게 역할을 모두 지정:
`surfaceContainerLowest = Surface`, `surfaceContainerLow = #FAFBFC`, `surfaceContainer = Surface`, `surfaceContainerHigh = SurfaceSunken`, `surfaceContainerHighest = SurfaceHighest`, `surfaceDim = Ground`, `surfaceBright = Surface`, `surfaceTint = Surface`(톤 틴트 끔 — 그림자만 사용), `tertiary = Help`, `onTertiary = Surface`, `tertiaryContainer = HelpSoft`, `onTertiaryContainer = Help`, `inverseSurface = Navy`, `inverseOnSurface = Surface`, `inversePrimary = AccentSoft`, `outline = LineStrong`(**변경**: 입력칸 테두리), `outlineVariant = Line`, `scrim = Color.Black`.
`SwitchDefaults.colors(uncheckedTrackColor = SurfaceHighest, uncheckedBorderColor = LineStrong, uncheckedThumbColor = LineStrong, checkedTrackColor = Accent, checkedThumbColor = Surface)`는 `components/Controls.kt`의 `appSwitchColors()`로 한곳에서.
**주의(비평 반영)**: M3 `CardDefaults.cardColors()`의 기본 바탕은 `surfaceContainerHighest`(= SurfaceHighest 회색), `AlertDialog`의 기본은 `surfaceContainerHigh`(= SurfaceSunken)다. ColorScheme만으로는 흰 카드가 되지 않으므로 **`CardNewsCard`, `ChoiceCard`, `PassportCard`, `ListGroup`, `DestructiveConfirm`과 그 밖의 모든 `Card()`/`AlertDialog()`는 `containerColor = Surface`를 명시한다**(4장 공통 규칙).

**`CardTone` 변경**: `Notice`는 **폐지 예정**(→ `NoticeBanner`). 11개 이상의 화면 파일이 쓰므로 0단계에서는 지우지 않고 `@Deprecated("NoticeBanner 사용")`만 붙여 지금 모양 그대로 둔다. 2단계에서 사용처가 0개일 때만 지운다. `Caution`은 테두리 대신 왼쪽 4dp `CautionBorder` 막대. 나머지(Neutral/Accent/Navy)는 유지하되 Neutral 테두리 제거 + 그림자(3.5절).

**다크 모드**: 이번에는 넣지 않는다. `values-v29/themes.xml`에 `android:forceDarkAllowed=false`. 화면 코드에서 `Color.White/Black` 직접 사용 금지(사진 위 글자·스크림 제외).

### 3.2 글자(타입 스케일)

`typography(easyMode)`에 역할을 추가하고, 화면의 고정 sp(`30.sp` HomeHero·CountryPhotoCard, `34.sp` CountryHero, `30/56.sp` PhraseCard·PhraseFullScreen, `44/24/20.sp` 쇼핑 전체화면, `28/44/32.sp` 교통)를 모두 역할로 바꾼다. 모든 값은 sp라 시스템 글자 크기가 그대로 곱해진다. `PlatformTextStyle(includeFontPadding = false)`.

| 역할 | 기본 (크기/행간, 굵기) | 쉬운 모드 | 쓰임 |
|---|---|---|---|
| displayLarge | 56/72 Bold | 56/72 | (한국어 큰 글자 예비 — 현지어 전체 화면은 아래 `localLarge`) |
| displayMedium | 34/42 Bold, ls −0.4 | 36/46 | 나라 히어로 이름 |
| displaySmall | 30/38 Bold, ls −0.3 | 32/42 | 홈 히어로, 1열 나라 카드 이름 (현지어에는 쓰지 않음 → `localMedium`) |
| headlineMedium | 24/32 Bold, ls −0.2 | 26/34 | 화면 제목 (유지) |
| titleLarge | 20/28 Bold | 24/32 | 섹션 제목·카드뉴스 제목 |
| titleMedium | 17/24 SemiBold | 22/30 | 카드 제목, 타일 라벨, 값 |
| titleSmall (신규) | 15/20 SemiBold | 20/28 | 그룹 헤더, 키-값 라벨 |
| bodyLarge | 16/24 | 20/30 | 본문 |
| bodyMedium | 15/22 | 19/28 | 보조 본문 |
| bodySmall | 13/18 | 18/26 | 출처 캡션(InkTertiary) — 쉬운 모드 최소 18 |
| labelLarge | 16/22 SemiBold | 20/28 | 버튼 |
| labelMedium | 13/18 **SemiBold**(Medium→SemiBold), ls 0.2 | 18/26 | eyebrow, 태그 |
| labelSmall | 12/16 Medium | 18/24 | 하단 탭 라벨만 |

**추가 스타일** (`ui/theme/Theme.kt`에 `@Immutable data class ReadyPortTypeExtras(val stat: TextStyle, val statSmall: TextStyle, val localLarge: TextStyle, val localMedium: TextStyle)` + `LocalTypeExtras`):

| 스타일 | 기본 | 쉬운 모드 | 비고 |
|---|---|---|---|
| stat | 30/36 Bold, `fontFeatureSettings = "tnum"` | 34/42 | `90일`, `D-3`, `1155` |
| statSmall | 22/28 Bold, tnum | 26/34 | 2열 타일 안 긴 값(`IDR 500,000`), 8자 넘는 전화번호, 사진 위 D-day |
| localLarge | 56/**84** Bold | 56/84 | 현지어 전체 화면(문장·주소·상품) |
| localMedium | 30/**46** Bold | 32/48 | 현지어 큰 카드(도움 선택 문장, 기사님께 보여주기) |

- **현지어 표시 역할(`localLarge`·`localMedium`)은 행간 1.5배 이상 + `LineHeightStyle(Alignment.Center, Trim.None)`**. 태국어는 위아래로 쌓이는 부호(ห้องน้ำ, ที่นี่)가 있어, 1.27~1.29배 행간에 `includeFontPadding = false`를 쓰면 줄이 바뀔 때 부호가 겹치거나 카드 clip에 잘린다. 18·19·20 전체 화면에서 여러 줄 태국어를 sdk 31·200%로 캡처해 확인한다(8장).
- **쉬운 모드 28sp 초과(D19)**: displayLarge 56, displayMedium 36, displaySmall 32, stat 34, localLarge 56, localMedium 32는 PRD 3.2(`쉬운 모드 18~28sp`)의 예외다. 현지인에게 보여 주는 글자, 히어로 이름, 큰 숫자에만 쓰고, 운영자 승인을 받아 PRD에 남긴다(8장 3단계). 승인 전에도 이 값으로 구현하되 승인 요청 목록에 올린다.

**줄바꿈**:
- 제목·버튼·타일 라벨: `lineBreak = LineBreak.Heading`(Balanced·Loose·`WordBreak.Phrase`).
- 본문: `LineBreak.Paragraph`는 `WordBreak.Default`라서 어절 단위로 끊지 않는다. 본문 스타일에는 **`LineBreak(strategy = Strategy.HighQuality, strictness = Strictness.Strict, wordBreak = WordBreak.Phrase)`를 직접 정의해 쓴다**(`ReadyPortLineBreak.Body`).
- 위 설정은 모두 **API 33 이상에서만** 효과가 있다. 테스트 폰(S10 5G, Android 12)에서는 한국어가 음절 사이 어디서나 끊길 수 있으므로: ① 2열 여부는 칸 폭으로 정한다(D4) ② **2열 타일 라벨은 한 줄 7자 이내**로 쓴다. 넘으면 어절 경계에 명시적 `\n`을 넣은 **새 짧은 라벨 키**를 쓴다(부록 B `tile_*`). 테스트가 찾는 기존 문자열 값은 바꾸지 않는다 ③ 팩에서 오는 라벨(`PhraseTile`)은 앱이 길이를 통제할 수 없으므로 항상 1열.
- `source_footer`는 `최종 확인`의 두 공백(`최종`과 `확인` 사이, `확인`과 날짜 사이)을 모두 NBSP(U+00A0)로 바꾼다. API 33 미만에서 `확/인`처럼 음절 사이가 끊기는 것까지 막으려면 `확`과 `인` 사이에 U+2060(WORD JOINER)을 넣는다. 테스트는 `getString(R.string.source_footer, …)`로 비교하므로 영향이 없다.
- 전화번호에는 보이지 않는 문자를 넣지 않는다(테스트가 `+66-81-914-5803` 같은 번호를 글자 그대로 찾는다). 번호는 칸 폭 규칙으로 해결한다(6장 20).

**글꼴(D1)**: 채택 시 `res/font/pretendard_regular|semibold|bold.ttf`, `FontFamily`를 Typography 전체에 적용, 태국어는 시스템 폴백. 굵기는 400/600/700 세 가지만. 미채택 시 시스템 글꼴 + 위 표 그대로.
- **라이선스**: Pretendard의 OFL에는 Reserved Font Name `Pretendard`가 선언돼 있을 가능성이 크다(구현 전에 LICENSE 원문 확인). fonttools로 직접 서브셋하면 수정본이 되어 같은 이름을 쓸 수 없으므로, **원 저작자가 배포하는 서브셋 빌드를 수정 없이 쓰거나, 직접 서브셋하면 family 이름을 바꾼다**(예: `ReadyPort Sans`). `OFL.txt` 전문을 `assets/licenses/OFL.txt`로 넣고, 설정 › `사진·글꼴 출처`에서 전문을 볼 수 있게 한다.
- **용량**: 3종 × 한글 11,172자 전체 TTF는 3.5MB를 넘을 가능성이 크다. 승인 요청 때 **실측 비교표**를 낸다: (가) 11,172자 전체 (나) KS X 1001 2,350자 + 나머지는 시스템 글꼴로 글자 단위 폴백 (다) 가변 글꼴 1개 서브셋. 3.5MB 이하인 안 중 (가) > (다) > (나) 순으로 권고한다.
- 파일 다운로드는 운영자 승인 후에만 한다(0b단계).

### 3.3 모서리 (Shapes)

| 토큰 | dp | 쓰임 |
|---|---|---|
| xs | 8 | 태그(`StatusTag`), 작은 칩 |
| sm | 12 | 입력칸, 아이콘 배지, 썸네일, 배너 |
| md | 16 | **모든 버튼**, 세그먼트, 타일(`IconTile`, `StatTile`, `EmergencyCallTile`) |
| lg | 20 | 카드 |
| xl | 28 | 히어로 사진, 다이얼로그 |
| CircleShape | — | 단계 노드, 번호 원, 빈 상태 큰 원, 아바타, 탭 인디케이터만 |

`Shapes(extraSmall = 8, small = 12, medium = 16, large = 20, extraLarge = 28)`. `Button`/`FilledTonalButton`/`OutlinedButton`은 모두 `shape = MaterialTheme.shapes.medium`.

### 3.4 간격·크기 (`ReadyPortDimens` 확장)

| 필드 | 기본 | 쉬운 모드 | 비고 |
|---|---|---|---|
| screenPadding | 20 | 20 | 기본 16→20 |
| cardPadding | 20 | 24 | 16/20→20/24 |
| gap (카드 사이) | 12 | 16 | 유지 |
| sectionGap (신규) | 32 | 40 | 섹션 사이. `LazyListScope.sectionGap()`이 **`sectionGap − 2×gap`(기본·쉬운 모두 8dp)** 높이의 Spacer item을 넣는다 — `spacedBy(gap)` 안의 Spacer는 앞뒤로 gap이 한 번씩 붙어 실제 간격이 `gap + h + gap`이 되기 때문 |
| inner (신규) | 8 | 12 | 카드 안 결론→본문. 본문→출처는 inner + 4 |
| buttonHeight | 56 | 64 | 기본 52→56 |
| minTouch (신규) | 48 | 56 | 인라인 버튼·칩·행·**모든 `IconButton`/`IconToggleButton`**(히어로 뒤로·찜, AppScreen 뒤로, 검색 지우기 — 지금은 `sizeIn(48)`이라 교체 패턴에서 빠짐). **하드코딩 `heightIn(min = 48.dp)` 40곳과 `sizeIn(minWidth = 48.dp, minHeight = 48.dp)`를 `Modifier.minTouch()`로 교체**(IconButton은 가로·세로 모두) |
| iconBadge (신규) | 40 | 52 | 카드·행 앞 아이콘 배지 |
| iconBadgeSmall (신규) | 32 | 40 | 타일 안 배지 |
| icon (신규) | 24 | 28 | 배지 안·버튼 앞 아이콘 |
| iconSmall (신규) | 16 | 20 | 태그·출처 캡션 아이콘 |
| tileMinHeight (신규) | 112 | 128 | `IconTile`·`StatTile` 최소 높이(1열 가로형은 72/88) |

4pt 체계(4/8/12/16/20/24/32/40)만 쓴다.

### 3.5 층위·테두리

| 대상 | 규칙 |
|---|---|
| 흰 정보 카드(`CardTone.Neutral`) | 테두리 없음. `Modifier.shadow(2.dp, shapes.large, ambientColor = Ink.copy(alpha = .08f), spotColor = Ink.copy(alpha = .12f))`, `CardDefaults.cardElevation(0.dp)` |
| 누를 수 있는 흰 타일 | 같은 그림자 + 오른쪽 `NavigateNext`(또는 위쪽 아이콘) 단서. 눌림은 M3 ripple |
| 상태 카드(주의·위험·성공) | 그림자 없음, 연한 바탕 + 왼쪽 4dp 색 막대(`drawBehind`), 모서리 20 |
| Accent·Navy 채움 카드 | 그림자 없음 |
| 사진 카드 | 그림자·테두리 없음 |
| 입력칸 | 1dp `LineStrong`(포커스 2dp Accent, 오류 2dp DangerText) |
| 목록 구분선 | 1dp `Line`, 아이콘 뒤부터(start = 16 + iconBadge + 16) |

### 3.6 아이콘 크기

20·24·28dp 세 단계 + 배지 32/40/52 + 빈 상태 40(원 96). 장식 아이콘은 모두 `contentDescription = null`. 버튼 안 아이콘은 `icon`(24/28) + 간격 8.

### 3.7 사진·스크림 규칙

1. 사진은 **히어로(홈·나라·첫 실행), 나라 선택 타일, 섹션 표지(공항=출국 순서, 짐=준비물)**에만. 정보 카드 본문에 사진 금지.
2. `photo_market`(베트남 호이안)은 **사용 중단**. 사용처가 0이 되면 drawable과 `assets/photo_credits.json` 항목을 함께 제거(갤러리·크레딧 테스트 갱신). 나라 쇼핑 머리는 아이콘 헤더로 대체. 새 사진은 자유 라이선스 + 크레딧 등록 시에만(이번 범위 밖).
3. **스크림(대비 보장)**: `PhotoBox v2`는 ① 사진 전체에 검정 0.18 틴트, ② **글자 영역(`PhotoTextArea`) 자체에** 세로 그라데이션(위 24dp는 0→0.60, 그 아래 0.60→0.88)을 깐다. 글자 크기가 커져 영역이 커져도 스크림이 함께 커진다. **`PhotoTextArea`는 첫 글자 앞에 위쪽 padding 24dp 이상을 강제**한다(그라데이션이 0→0.60으로 올라가는 위 24dp 구간에는 글자가 놓이지 않음). 그래서 글자는 항상 알파 0.60 이상 위에 놓이고 흰 글자 대비가 **5.74:1 이상**이다(흰 사진 최악 가정).
   - **사진 위의 모든 글자·아이콘은 `PhotoTextArea` 안이나 자체 바탕(`PhotoChip`, 검정 0.35 원형 버튼 바탕) 위에만 둔다.** 0.18 틴트만 있는 사진 윗부분(흰 사진이면 흰 글자 1.53:1)에는 글자를 두지 않는다 — 첫 실행 히어로의 앱 심볼·`home_brand`, 사진 위 D-day도 `PhotoTextArea` 안.
   - 사진 위 버튼(뒤로·찜)은 지금의 검정 0.35 원형 바탕을 유지한다(0.18 틴트 흰 사진 위에서도 흰 아이콘 3.54:1 ≥ 3).
   - 테스트: 흰 단색 사진(`ColorDrawable(White)`)으로 `PhotoBox`+`PhotoTextArea`+`PhotoChip` 최악 경우 캡처(8장).
4. 사진 위 칩(`PhotoChip`)은 흰 92% 바탕 + Ink 글자(검정 위 합성 최악 13.42:1) + 선택 아이콘.
5. 96dp 이하 썸네일(아바타, 사진 출처, 여행 고치기 나라 선택)은 `rememberThumbnail(@DrawableRes, sizeDp)`(BitmapFactory `inSampleSize` 축소 디코딩)로 메모리 절약. 전체 크기 사진은 화면당 최대 한 번의 히어로 + 나라 타일.
6. `Modifier.blur` 금지(API 31+·무거움). 그라데이션만.
7. **CC BY/BY-SA 변경 표시**: 이번 개편은 번들 사진에 어두운 틴트·그라데이션을 덧씌우고 일부를 원형 썸네일로 자른다. `assets/photo_credits.json`의 모든 `changes`를 `잘라내고 크기를 줄이고, 글자가 잘 보이게 어둡게 덮었어요(일부는 둥글게 자름)`로 고치고, 사진 출처 화면 안내도 새 키 `photo_credits_body_v2`로 바꾼다(부록 B). `photo_market`을 지우면 크레딧 항목도 함께 지운다.

### 3.8 TokenContrastTest 추가 쌍

**계산 방식 수정(필수)**: 지금 `contrast()`는 `Color.luminance()`를 쓰는데 이 함수는 알파를 무시한다. 그래서 White85·White80·PhotoChip(흰 92%)이 불투명 흰색으로 계산돼 항상 통과한다(예: White85 on Accent 실제 5.37 → 6.78로 계산). **`contrast(fg, bg)`는 먼저 `fg.compositeOver(bg)`로 합성한 뒤 계산한다**(bg가 반투명이면 bg도 최악 바탕 위에 먼저 합성).

글자(4.5): InkTertiary on Surface/Ground/AccentSoft/CautionBg, InkSecondary on SurfaceSunken/SurfaceHighest/HelpSoft/SuccessBg/DangerBg, Accent on AccentSoft/SurfaceSunken, AccentDeep on AccentSoft, Help on HelpSoft, VioletText on VioletSoft, TealText on TealSoft, Gold on Navy/AccentDeep, Surface on Accent/Navy/AccentDeep/BrandBlue/Help/SuccessText/DangerText, 비활성(InkTertiary on SurfaceHighest), **합성 쌍(기대값 직접 계산)**: White85/Accent 5.37, White85/AccentDeep 7.93, White85/Navy 12.17, White80/Navy 10.85, White80/AccentDeep 7.26, Ink/(흰 92% over 검정) 13.42(PhotoChip 최악), 흰 글자/(검정 0.60 over 흰색) 5.74(`PhotoTextArea` 스크림 최악).
비텍스트(3.0, 새 `nonTextPairs` 맵): LineStrong on Surface/Ground, Accent(선택 채움) vs SurfaceSunken/Ground, 세그먼트 트랙 테두리 LineStrong vs Ground 4.25, tonal 버튼 테두리 Accent vs Ground 6.22, onDark 보조 버튼 테두리 Surface vs Accent 6.78·Navy 16.6, 사진 위 버튼 흰 아이콘/(검정 0.35 over 0.18 틴트 흰 사진) 3.54, 각 BadgeTone content vs container.
**금지 쌍 검사(신규 `OnDarkPairsTest`)**: `ui/` 소스에서 Navy·Accent·AccentDeep·BrandBlue 채움 블록 안에 Help, DangerText, InkTertiary, InkSecondary, Accent 글자·아이콘 색과 BrandBlue 위 Gold가 쓰이지 않는지 정적으로 검사한다(부품 단위로는 `CardNewsCard`·`IconTile(emphasized)`·`EmergencyCallTile(large)`·`SecondaryButton(onDark)`·`PassportCard`의 색 선택 함수를 직접 호출해 반환 색이 3.1절 허용 목록 안인지 단언).

---

## 4. 컴포넌트 카탈로그

모두 `app/src/main/java/com/readyport/ui/components/`. 공통 규칙: 누르는 요소는 `minTouch` 이상 + 이름(글자 또는 설명), 고정 높이 없음, 장식 아이콘 `contentDescription = null`, 제목류는 `semantics { heading() }`. **M3 `Card`·`AlertDialog`를 쓰는 모든 부품은 `containerColor = Surface`를 명시한다**(기본값은 회색 `surfaceContainerHighest`/`surfaceContainerHigh`, 3.1절). 어두운 채움 위 색은 `onDark 내용 세트`(3.1절)만. **한 개만 고르는 선택(칩·타일·행)은 `Role.RadioButton` + 부모 `selectableGroup()`**, 여러 개는 `Role.Checkbox`. 행 전체가 눌리는 라디오·체크 행 안의 `RadioButton(onClick = null)`·`Checkbox(onCheckedChange = null)`·`Switch(onCheckedChange = null)`는 콜백을 null로 둬서 초점이 두 번 잡히지 않게 한다.

### 4.0 기반 파일 배치

| 파일 | 내용 |
|---|---|
| `Layout.kt` | `rememberGridColumns`, `TileGrid`, `LazyListScope.tileRows`, `LazyListScope.sectionGap`, `Modifier.minTouch()`, `rememberKeyIndex`/`scrollToKey`(4.1절) |
| `IconBadge.kt` | `BadgeTone`, `IconBadge` |
| `IconKeys.kt` | ID → 아이콘 매핑 함수(5장 표의 코드 버전) |
| `CardNews.kt` | `SectionHeader`, `CardNewsCard`(+`NewsStyle.SurfaceCaution`, `trailing` 슬롯), `StatTile`/`FactChip`/`FactGrid`, `StepList`, `IconBullet`, `ExpandableDetail` |
| `Banners.kt` | `NoticeBanner`, `SecurityBanner(compact: Boolean = false)`(기존 `LocalOnlyBanner` 이동·재스타일, compact = Lock 아이콘 + `settings_local_only_title` 한 줄), `OfflineBanner`(ReadyPortRoot.kt의 private 함수를 이동·재스타일) |
| `Sources.kt` | `SourceRef`, `SourceFooter`(v2 + 구 시그니처), `SourceList`, `LinkRow`, `displayDate`(pack/PackScreens.kt에서 이동 — B·C·E가 씀) |
| `Buttons.kt` | `PrimaryButton`(+icon), `SecondaryButton`, `DangerButton`, `QuietButton`, `DestructiveConfirm` (AppScreen.kt의 PrimaryButton은 여기로 이동, import 경로 유지 위해 같은 패키지) |
| `Tiles.kt` | `IconTile`, `ChoiceCard`, `SelectTile`, `EmptyState`, `LockedState`, `ComingSoonGroup` |
| `Lists.kt` | `ListRow`, `ListGroup`, `RowTrailing`(+`Custom`), `KeyValueRow`(17·24·25가 공유) |
| `Controls.kt` | `ChoiceSegments`, `SelectChip`, `appSwitchColors()` |
| `Status.kt` | `StatusTag`, `StatusKind`, 기존 `StatusChip`(icon 인자 추가), `ImportVerdictBadge`(ShoppingScreen의 `ImportTag` 이동), `importLabel`·`importColors`(pack/ShoppingScreen.kt에서 이동 — A·B·C가 씀) |
| `Photos.kt` | `PhotoBox v2`, `PhotoTextArea`, `PhotoHeaderCard`(HomeScreen의 `PhotoTopCard` 이동), `CountryPhotoTile`, `ChipSpec`, `PhotoChip`(+icon), `rememberThumbnail`, `Photos.byId(id)`(크레딧 id → drawable, 기존 `Photos` 객체에 추가) |
| `Journey.kt` | `JourneyStepper` |
| `ReturnCheck.kt` | `ReturnCheckCard v2`(pack/ShoppingScreen.kt에서 이동 — 홈·나라·내 여행·쇼핑이 공유) |

**이동 규칙(0단계, 비평 반영)**: 다른 묶음 파일에 있던 공용 심볼(`displayDate`, `importLabel`, `importColors`, `ImportTag`, `ReturnCheckCard`, `PhotoTopCard`, `LocalOnlyBanner`, `OfflineBanner`)은 0단계에서 components로 옮기고, **옛 위치에는 새 함수를 부르는 `@Deprecated` 위임 함수(forwarder)를 남긴다.** `CardTone.Notice`도 `@Deprecated`로 남긴다. 2단계에서 사용처가 0개일 때만 지운다. 1단계 묶음은 이 심볼의 옛 위치를 지우거나 시그니처를 바꾸지 않는다. F 묶음은 `rememberDeviceAuth`·`maskName`·`maskNumber`(wallet, C·E가 씀)의 시그니처를 바꾸지 않는다.

### 4.1 레이아웃 도우미 (`Layout.kt`)

```kotlin
@Composable fun rememberGridColumns(preferred: Int = 2): Int
// easyMode → 1
// widthDp = LocalWindowInfo.current.containerSize.width를 dp로 (LocalConfiguration.screenWidthDp는 lint ConfigurationScreenWidthHeight 경고 → 쓰지 않음)
// colDp = (widthDp − 2×screenPadding − gap) / 2 ; if (colDp / fontScale < 150) → 1 ; else preferred   (D4)

@Composable fun <T> TileGrid(
    items: List<T>, modifier: Modifier = Modifier, columns: Int = rememberGridColumns(),
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
)
// items.chunked(columns) → Row(Modifier.height(IntrinsicSize.Min), spacedBy(dimens.gap)) { itemContent(it, Modifier.weight(1f).fillMaxHeight()) }
// 모자란 칸은 Spacer(Modifier.weight(1f)). 행 사이 spacedBy(dimens.gap). LazyVerticalGrid 중첩 금지

fun <T> LazyListScope.tileRows(keyPrefix: String, items: List<T>, columns: Int, itemContent: @Composable (T, Modifier) -> Unit)
// 긴 목록(쇼핑 등)용: 한 줄을 lazy item 하나로. columns는 화면 composable에서 rememberGridColumns()로 미리 계산해 넘긴다
// (AppScreen의 content 람다는 LazyListScope라 composable 호출 불가)

fun LazyListScope.sectionGap(key: String)   // Spacer 높이 = sectionGap − 2×gap (3.4절)
fun Modifier.minTouch(): Modifier
// heightIn(min = LocalDimens.current.minTouch)과 같은 효과. @Composable 확장 Modifier는 lint ComposableModifierFactory 경고를 내므로
// CompositionLocalConsumerModifierNode를 쓰는 Modifier.Node(ModifierNodeElement)로 만든다. 만들기 어려우면 호출하는 곳에서 heightIn(LocalDimens.current.minTouch)
fun Modifier.minTouchSize(): Modifier   // IconButton용: 가로·세로 모두 minTouch

// 스크롤 이동 도우미 — AppScreen은 0단계에서 state: LazyListState = rememberLazyListState() 인자를 받아 밖으로 내보낸다
class KeyIndex { fun indexOf(key: String): Int? }               // 화면이 item을 넣는 순서대로 key를 기록
@Composable fun rememberKeyIndex(): KeyIndex
suspend fun LazyListState.scrollToKey(index: KeyIndex, key: String, headerOffsetPx: Int = 0)
// LazyListState에는 key로 index를 찾는 공개 API가 없으므로, 화면이 LazyListScope에 item을 넣을 때 KeyIndex에 key를 기록한다.
// AppScreen이 넣는 header·easy-actions item과 sectionGap Spacer item도 같은 방식으로 기록(오프셋 포함).
// stickyHeader가 있으면 scrollOffset = −헤더 높이(px). 이동 뒤 포커스가 필요하면 snapshotFlow { layoutInfo.visibleItemsInfo }로
// 대상 item이 그려진 것을 확인한 다음 FocusRequester.requestFocus()
```

### 4.2 IconBadge

```kotlin
@Composable fun IconBadge(
    icon: ImageVector, modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Accent, size: Dp = LocalDimens.current.iconBadge,
    shape: Shape = MaterialTheme.shapes.small,   // 12dp 둥근 사각. 원은 쓰지 않는다
)
```
- `Box(size, clip(shape), background(tone.container)) { Icon(icon, null, tint = tone.content, size = size * 0.55f) }` + `Modifier.clearAndSetSemantics {}`.
- 쉬운 모드: dimens로 40→52. 글자 크기(fontScale)로는 커지지 않는다(dp).
- 여러 줄 글 옆에서는 `Row(verticalAlignment = Alignment.Top)`로 위를 맞춘다.

### 4.3 SectionHeader (아이콘 + 제목)

```kotlin
@Composable fun SectionHeader(
    title: String, modifier: Modifier = Modifier,
    icon: ImageVector? = null, tone: BadgeTone = BadgeTone.Accent,
    eyebrow: String? = null, subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,  // 예: QuietButton("모두 보기", NavigateNext)
)
```
- `Row(Top, spacedBy(12)) { icon?.let { IconBadge(it, tone) } ; Column(weight 1f) { eyebrow(labelMedium, tone.content) ; title(titleLarge, heading) ; subtitle(bodyMedium, InkSecondary) } ; action }`, 위 여백 없음(섹션 간격은 `sectionGap()`이 맡음).
- 기존 `HomeScreen.SectionTitle`, 도움의 `help_phrases_title`·`help_procedures_title` 텍스트 제목을 대체.
- 200%: 제목 줄바꿈 허용, `action`은 폭이 모자라면 아래 줄(FlowRow).

### 4.4 NoticeBanner

```kotlin
enum class BannerTone(val bg: Color, val bar: Color, val icon: Color, val text: Color) {
    Notice(Surface, Accent, Accent, Ink),   // D21: AccentSoft 채움 금지 — 흰 바탕 + 4dp Accent 막대, 그림자 없음
    Caution(CautionBg, CautionBorder, CautionText, CautionText),
    Danger(DangerBg, DangerText, DangerText, DangerText), Success(SuccessBg, SuccessText, SuccessText, SuccessText),
}
@Composable fun NoticeBanner(
    text: String, modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info, tone: BannerTone = BannerTone.Notice,
    title: String? = null, secondLine: String? = null, secondIcon: ImageVector? = null,
)
```
- `Surface(tone.bg, shapes.small)` + 왼쪽 4dp 막대(`Modifier.height(IntrinsicSize.Min)` + `drawBehind`) + `Row { Icon(24/28) ; Column { title?(titleSmall) ; text(bodyMedium SemiBold) ; secondLine?(Row { secondIcon 20 ; bodyMedium }) } }`, padding 16.
- **누를 수 없다**(clickable 없음, 알약 모양 아님, 테두리 없음). 글자 수 제한 없음, 높이 자동. 누를 수 있는 tonal 버튼과 헷갈리지 않게 Notice 톤은 흰 바탕 + 왼쪽 막대만 쓴다(D21). 폭이 340dp 미만이면 `title`을 생략할 수 있다(07 화면 예산).
- 쓰는 곳: 정부 비제휴(`guide_not_affiliated`, icon `Policy`, 둘째 줄 `제출은 직접` + `TouchApp`) — 입국 화면(03·04·15·17)의 **첫 정보 항목**. 제휴 고지(16, `Handshake`), YouTube 고지(07), 대행 사이트 경고(04, `Danger`, `GppMaybe`), 만료 여권(25, `Danger`, `EventBusy`), 오프라인 배너 스타일(`CloudOff`).
- 기존 문자열을 그대로 넘기므로 `onNodeWithText(guide_not_affiliated)`, `essentials_disclosure` 위치 테스트 유지.

### 4.5 SourceRef / SourceFooter v2 / SourceList / LinkRow

```kotlin
@Immutable data class SourceRef(val name: String, val verified: String /* displayDate 적용된 YYYY.MM.DD */)
fun resolveSourceName(id: String, names: Map<String, String>, fallback: String): String  // 없으면 fallback. 절대 id 반환 금지
@Composable fun SourceFooter(ref: SourceRef, modifier: Modifier = Modifier, onColor: Boolean = false)
@Composable fun SourceFooter(source: String, verifiedDate: String)   // 기존 시그니처: 위로 위임(단계적 이행)
@Composable fun SourceList(refs: List<SourceRef>, onColor: Boolean = false)
@Composable fun LinkRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null)
```
- 모양: `Row(Top) { Icon(AutoMirrored.Outlined.FactCheck, null, iconSmall, tint) ; Spacer(6) ; Text(stringResource(R.string.source_footer, name, verified), bodySmall, color) }`. 색: InkTertiary / `onColor`면 White85.
- **글자는 한 Text 노드, 형식 `출처 {이름} · 최종 확인 {날짜}` 그대로**(`OfflinePackTest.guideOpensOfflineWithSources`가 `getString(source_footer, …)`로 정확히 찾음). `source_footer`의 공백 두 개(`최종`↔`확인`, `확인`↔날짜)만 NBSP(3.2절).
- `SourceList`: **출처가 하나면 `source_footer(name, date)` 한 Text 노드 그대로**(OfflinePackTest 유지). 출처 이름 자체에 ` · `가 들어 있으므로(`외교부 해외안전여행 · 태국`, `인도네시아 이민국 · All Indonesia`) 여러 출처를 ` · `로 잇지 않는다. 날짜가 같은 여러 출처는 **이름을 `, `로 이어 한 줄**로 쓰고 날짜는 맨 끝에 한 번만 쓴다(`출처 관세청 여행자 휴대품 면세 범위, 농림축산검역본부 휴대 식물 검역 · 최종 확인 2026.09.29`, 같은 `source_footer`의 `%1$s`에 `, `로 이은 이름을 넣는다 — 새 키 없음). 날짜가 다르면 줄을 나눈다. 같은 이름은 한 번만(중복 제거).
- 폴백 문자열 신규 `source_official_fallback = 공식 안내`. 모든 화면의 `?: id`, `?: src` 제거.
- `LinkRow`: 줄 전체 클릭, `minTouch`, 오른쪽 `AutoMirrored.Outlined.OpenInNew` 20dp, Accent 글자 labelLarge. 공식 링크 알약 버튼 대체.
- 테스트(신규 `SourceNamesTest`): 모든 번들 팩·index의 정책 항목 `source` ID가 해당 `sources`에서 이름으로 풀리는지. 갤러리 캡처 텍스트에 내부 ID나 `출처 출처`가 없는지(`A11yAudit`와 같은 루프에서 검사). **ID 정규식은 앵커 없는 `\b[a-z]+(?:_[a-z0-9]+)+\b`**(앵커 `^…$`는 `출처 tat_chanthaburi · …`처럼 문장 안의 ID를 못 잡는다).
- 도움 탭 버그 수정(D 묶음, `pack/PackViewModels.kt`): `commonSourceName`을 `index.sources.firstOrNull()?.name`이 아니라 `common.first().source`를 `resolveSourceName`으로 풀어 만든다. 공통 항목의 출처가 여러 개면 `SourceList`로.

### 4.6 StatTile / FactChip / FactGrid

```kotlin
@Immutable data class Fact(val icon: ImageVector, val value: String, val label: String,
    val tone: BadgeTone = BadgeTone.Accent, val source: SourceRef? = null)   // source: 이 값이 나온 출처(카드 SourceList에 모음)
@Composable fun StatTile(fact: Fact, modifier: Modifier = Modifier)
@Composable fun FactGrid(facts: List<Fact>, modifier: Modifier = Modifier, columns: Int = rememberGridColumns())
@Composable fun FactChip(fact: Fact, modifier: Modifier = Modifier)   // 한 줄: 아이콘 16 + 값(labelLarge Bold) + 라벨
fun shortValue(text: String): String?
// 1) 12자 이하면 그대로  2) " · "(공백·가운뎃점·공백) 앞부분 또는 첫 문장(". " 앞부분) 중 먼저 끊기는 쪽이 14자 이하면 그 부분
// 3) 아니면 null(→ 타일 대신 글 행). **","에서는 절대 자르지 않는다**(숫자 사이 쉼표: "IDR 500,000 · …"가 "IDR 500"이 되면 정책 값 왜곡)
fun feeIcon(value: String): ImageVector  // 값이 정확히 "무료"일 때만 MoneyOff, 나머지는 Payments
```
- `shortValue` 단위 테스트(번들 팩 전체): TH `무료`→`무료`, JP `무료. 돈을 받는…`→`무료`, SG `무료. 돈을 받는…`→`무료`, ID apply `IDR 500,000 · 카드(…)…`→`IDR 500,000`, ID form `무료`→`무료`, MY `공식 안내에 요금이 적혀 있지 않아요. …`→`null`(글 행).
- `StatTile`: `Surface(tone.container, shapes.medium, heightIn(min = tileMinHeight))`, padding 16, `Column(spacedBy(6)) { Icon(icon, null, icon크기, tone.content) ; Text(value, stat 또는 값이 8자 넘으면 statSmall, Ink) ; Text(label, bodyMedium, InkSecondary) }`. `semantics(mergeDescendants = true)` — 읽기는 `90일 비자 없이 머물러요`. 누를 수 없음(누르는 요약은 `IconTile`).
- `FactGrid`: `TileGrid` 사용, 같은 행 높이 맞춤. **팩의 구조화 필드에서만** 값을 만든다(D11). 앱 자체 주장(예: `직접`)은 타일로 만들지 않는다. 타일이 2개 미만이면 그리지 않고 글로 보인다. 그리드 바로 아래(카드 안)에 출처 필수: **카드의 `sources`는 `req.source` + 각 타일의 `Fact.source`를 모두 담고, `SourceList`가 중복을 없앤다**(출처가 다른 값이 한 카드에 섞여도 각 값의 출처가 보이게. 태국처럼 모두 같은 출처면 한 줄 그대로).
- 200%/쉬운 모드: 1열, 값 줄바꿈 허용.

### 4.7 CardNewsCard (카드뉴스 카드)

```kotlin
enum class NewsStyle { Surface, SurfaceCaution, Accent, Navy, Caution, Danger }
// SurfaceCaution: 흰 바탕 + 왼쪽 4dp CautionBorder 막대만(17 직접 입력 카드). 그림자 없음
@Composable fun CardNewsCard(
    title: String, icon: ImageVector, modifier: Modifier = Modifier,
    eyebrow: String? = null, body: String? = null,
    tone: BadgeTone = BadgeTone.Accent, style: NewsStyle = NewsStyle.Surface,
    sources: List<SourceRef> = emptyList(),
    trailing: (@Composable () -> Unit)? = null,   // 머리 줄 오른쪽(예: StatusTag(form_missing_count))
    content: @Composable ColumnScope.() -> Unit = {},
)
```
- 구조: 머리 `Row(Top, spacedBy(12)) { IconBadge(icon, tone) ; Column { eyebrow(labelMedium) ; title(titleLarge, heading) } }` → body(bodyLarge) → content(`FactGrid`/`StepList`/`IconBullet`/버튼) → `SourceList(sources)`(항상 맨 아래, 접힘 영역 밖).
- 모든 스타일에서 카드 바탕은 `containerColor`를 명시한다(Surface 계열은 `Surface`).
- `Accent`/`Navy` 스타일: 채움 바탕, **onDark 내용 세트만**(3.1절): 제목·본문 Surface, **eyebrow White85**(tone.content를 쓰면 Accent 카드 위에서 1.0:1로 사라짐), 배지 `BadgeTone.OnDark`(흰 12% 바탕 + Surface 아이콘 — 장식), 출처 `onColor = true`(White85), 주 버튼은 흰 채움(`ButtonStyles.onDark`), 보조 버튼은 `SecondaryButton(onDark = true)`. 안에 `DangerButton`을 두지 않는다.
- `Caution`/`Danger`: 연한 바탕 + 왼쪽 막대(3.5절).
- 기존 `InfoCard`+`Text` 조합(비자·양식·섹션·전기·대사관·여권 분실·귀국 전 확인)을 대체. `InfoCard`는 남겨 두되 새 코드는 `CardNewsCard` 우선.

### 4.8 StepList / IconBullet / ExpandableDetail

```kotlin
@Immutable data class Step(val text: String, val icon: ImageVector? = null, val detail: String? = null)
// selfAction(직접 해요 태그)은 두지 않는다: 팩 단계 순서에 앱이 뜻을 붙이면 ARIA가 단계를 바꿀 때 엉뚱한 단계에 붙는다(D11)
@Composable fun StepList(steps: List<Step>, modifier: Modifier = Modifier, numbered: Boolean = true)
@Composable fun IconBullet(text: String, icon: ImageVector, modifier: Modifier = Modifier, tone: BadgeTone = BadgeTone.Neutral)
@Composable fun ExpandableDetail(label: String = stringResource(R.string.action_more), content: @Composable ColumnScope.() -> Unit)
```
- `StepList` 행: `Row(Modifier.height(IntrinsicSize.Min)) { Column(widthIn(min = 28/36), CenterHorizontally) { 번호 원 ; 다음 행까지 2dp Line 세로선(weight 1f) } ; Spacer(12) ; Column(weight 1f, padding(bottom = gap)) { Row { icon?(20/24, Accent) ; Text(text, titleMedium) } ; detail?(bodyMedium InkSecondary) } }`. 행마다 `semantics(mergeDescendants = true)`. 높이는 글에 맞춤.
- **번호 원은 글자가 크기를 정한다**: `Box(Modifier.defaultMinSize(28/36dp).background(Accent, CircleShape).padding(horizontal = 4.dp).wrapContentSize(), Center) { Text(숫자, labelLarge, 흰색) }` + `aspectRatio(1f)` 대신 `layout`에서 가로·세로 중 큰 값으로 정사각형을 맞춘다. 고정 28/36dp 원 안에 글자를 넣으면 200%(S10 선형 2배: 32sp ≈ 줄 높이 44dp)에서 숫자가 넘치거나 잘린다. 같은 규칙을 이니셜 아바타(27)에도 쓴다. 200% 캡처에 StepList와 아바타를 넣는다.
- 번호는 원 안에만(D17). 문자열의 `N. ` 접두 삭제.
- `IconBullet`: `• 문장`을 대체. `Row(Top) { Icon(20/24, tone.content) ; Spacer(12) ; Text(bodyLarge, weight 1f) }`. Caution/Danger 톤이면 행 바탕을 연하게.
- `ExpandableDetail`: `Row(minTouch, toggleable(open, role = Role.Button))` + `stateDescription = 펼쳐짐/접힘` + `ExpandMore/ExpandLess`, `AnimatedVisibility`. **출처는 절대 이 안에 넣지 않는다.** `소리로 듣기`는 접힘과 무관하게 전체 문장을 읽는다(speech 문자열은 그대로).

### 4.9 IconTile / InfoTileGrid

```kotlin
enum class TileLayout { Auto, Vertical, Horizontal }
@Immutable data class TileSpec(val label: String, val icon: ImageVector, val onClick: () -> Unit,
    val supporting: String? = null, val tone: BadgeTone = BadgeTone.Accent, val emphasized: Boolean = false)
@Composable fun IconTile(spec: TileSpec, modifier: Modifier = Modifier, layout: TileLayout = TileLayout.Auto)
@Composable fun InfoTileGrid(tiles: List<TileSpec>, modifier: Modifier = Modifier, columns: Int = rememberGridColumns())
```
- Vertical(2열): `Surface(onClick, shapes.medium, Surface 바탕 + 3.5절 그림자, heightIn(min = tileMinHeight))`, padding 16, `Column { IconBadge(iconBadge, tone) ; Spacer(12) ; Text(label, titleMedium, lineBreak = Heading) ; supporting?(bodySmall) }`, 오른쪽 아래 `AutoMirrored.Outlined.NavigateNext`(InkTertiary).
- Horizontal(1열, 쉬운 모드·큰 글자에서 Auto가 선택): `Row(heightIn(min = 72/88), padding 16) { IconBadge ; Column(weight 1f) { label ; supporting } ; NavigateNext }`.
- `emphasized = true`: Navy 채움(화면당 1개, 예: 여행 중 `숙소로 돌아가기`), onDark 내용 세트만: 라벨 Surface, supporting White80, 배지 `BadgeTone.OnDark`, **셰브론 `NavigateNext`는 White80**(InkTertiary on Navy 2.82 금지).
- 2열 라벨은 한 줄 7자 이내(3.2절). 넘는 라벨은 부록 B의 짧은 `tile_*` 키를 쓴다.
- a11y: `role = Role.Button`, 이름 = label 글자(병합). 쉬운 모드 높이 128 ≥ 64dp.

### 4.10 ListRow / ListGroup

```kotlin
sealed interface RowTrailing {
    data object Chevron : RowTrailing; data object External : RowTrailing; data object None : RowTrailing
    data class Switch(val checked: Boolean, val onChange: (Boolean) -> Unit) : RowTrailing
    data class Custom(val content: @Composable () -> Unit) : RowTrailing   // 예: ImportVerdictBadge(06), OfflinePin 칩(01)
}
@Composable fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, subLabel: String? = null,
    badge: (@Composable () -> Unit)? = null)   // 17·24·25 공유
@Composable fun ListRow(
    title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, body: String? = null,
    tone: BadgeTone = BadgeTone.Accent, trailing: RowTrailing = RowTrailing.Chevron, onClick: (() -> Unit)? = null,
)
@Composable fun ListGroup(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)
```
- `Row(heightIn(min = 64/72), padding(16), horizontalArrangement = spacedBy(16)) { IconBadge ; Column(weight 1f) { title(titleMedium) ; body(bodyMedium InkSecondary) } ; trailing }` — **글자와 스위치 사이 16dp 보장**(22 결함 해결).
- Switch 행은 줄 전체 `toggleable(role = Role.Switch)`, Switch는 `onCheckedChange = null`, `appSwitchColors()`. Chevron 행은 `clickable(role = Button)`.
- `ListGroup`: 제목(titleSmall InkSecondary) + 그림자 카드(`containerColor = Surface`) 안에 행들, 행 사이 `HorizontalDivider(Line, start = 16 + iconBadge + 16)`. 행이 아닌 설명 `Text`(bodyMedium)도 넣을 수 있다(07 `videos_terms`).
- `KeyValueRow`: `Column(Modifier.semantics(mergeDescendants = true)) { Row { label(titleSmall, weight 1f) ; badge? } ; subLabel?(bodySmall InkSecondary) ; value(titleMedium Bold, tnum) }` — 라벨과 값을 한 번에 읽는다. 값을 오른쪽 좁은 칸에 두지 않아 200%에서도 겹치지 않음. 행 사이 `HorizontalDivider`. `label`·`subLabel`·`value`는 각각 단독 Text 노드로 유지(테스트가 `ERIKSSON`, `Family Name · นามสกุล`을 찾음).
- 미리보기처럼 읽을 필요 없는 장식(22 쉬운 모드 `가/가`)은 `Modifier.clearAndSetSemantics {}`로 숨긴다.

### 4.11 버튼

```kotlin
@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, icon: ImageVector? = null, colors: ButtonColors = ButtonDefaults.buttonColors())   // 기존 + icon (기본 null → 호환)
@Composable fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, tone: BadgeTone = BadgeTone.Accent, fillWidth: Boolean = true, enabled: Boolean = true,
    onDark: Boolean = false)
@Composable fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.DeleteOutline, fillWidth: Boolean = false)   // 밝은 바탕에서만. 어두운 카드 안에 두지 않음(D18)
@Composable fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null)
@Composable fun DestructiveConfirm(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    secure: Boolean = false)   // 보안 화면(17·21·23~27)에서는 true
object ButtonStyles { @Composable fun onDark(): ButtonColors /* Surface 바탕 + Navy/Accent 글자 */ }
```
- 모두 `shapes.medium`(16dp), **높이 `heightIn(min = buttonHeight)`(56/64, 최소값 — 두 줄 라벨이면 커짐)**, 아이콘 24/28 + 간격 8, 라벨 `labelLarge` + `lineBreak = Heading`, `softWrap` 허용.
- `PrimaryButton` 비활성 색: container `SurfaceHighest`, 글자 `InkTertiary`(4.87 — 비활성이어도 읽힘).
- `SecondaryButton` = `FilledTonalButton`(tone.container / tone.content) **+ 1dp `tone.content` 테두리**(Accent면 Ground 대비 6.22 — D21: 누를 수 없는 배너와 구분). 기존 회색 `OutlinedButton` 전부 대체.
- `SecondaryButton(onDark = true)`: **투명 바탕 + 1.5dp Surface 테두리(Accent 위 6.78, Navy 위 16.6) + Surface 글자·아이콘**. 흰 12% tonal은 경계가 Accent 대비 1.29:1, Navy 대비 1.40:1이라 글자만 떠 보여서 쓰지 않는다. Accent 카드·Navy 카드·`PassportCard`의 모든 보조 버튼에 공통(04 비자 링크, 19 기사님 카드, 20 선택 문장 카드, 24 여권 보기).
- `DangerButton` = `OutlinedButton(border 1.5dp DangerText, content DangerText)` + 아이콘. 주 버튼 자리에 두지 않는다(오른쪽 정렬 또는 별도 줄).
- `QuietButton` = `TextButton`(Accent 글자, `minTouch`) — 3순위 동작(수동 모드, 내 정보 잠그기).
- `DestructiveConfirm` = `AlertDialog(containerColor = Surface, icon = DeleteOutline(DangerText), title titleLarge, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(body, bodyLarge) } }, confirmButton = TextButton(DangerText, minTouch), dismissButton = TextButton(action_cancel_keep = 그만두기, minTouch))`, 모서리 28. M3 AlertDialog 본문은 넘치면 잘리므로 200%에서도 읽히게 본문을 스크롤로 감싼다.
  - `secure = true`면 `properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)` — 대화상자는 별도 창이다. 기본값 `Inherit`에 기대지 않고 명시해서, `SecureScreen()`이 플래그를 거두는 순서와 상관없이 캡처를 막는다.
  - **제목·본문에 개인정보(이름·여권번호·예약번호)를 넣지 않는다.** 확인 문구는 부록 B의 `*_confirm_title/body` 키만.
- 쉬운 모드의 `처음으로`·`소리로 듣기`(AppScreen `EasyActionButton`)는 `SecondaryButton(fillWidth = false)` 64dp + 아이콘 28, FlowRow 유지.

### 4.12 StatusTag / ImportVerdictBadge

```kotlin
enum class StatusKind(val icon: ImageVector, val tone: BadgeTone) {
    Allowed(Icons.Outlined.CheckCircle, BadgeTone.Success), Caution(Icons.Outlined.ReportProblem, BadgeTone.Caution),
    Prohibited(Icons.Outlined.Block, BadgeTone.Danger), Info(Icons.Outlined.Info, BadgeTone.Accent),
    Soon(Icons.Outlined.Schedule, BadgeTone.Neutral), Verified(Icons.Outlined.Verified, BadgeTone.Success),
    Self(Icons.Outlined.TouchApp, BadgeTone.Help), Required(Icons.Outlined.ErrorOutline, BadgeTone.Danger),
}
@Composable fun StatusTag(text: String, kind: StatusKind, modifier: Modifier = Modifier)
@Composable fun StatusChip(text: String, container: Color = AccentSoft, content: Color = Ink, icon: ImageVector? = null)  // 기존 호환
@Composable fun ImportVerdictBadge(status: ImportStatus)   // Allowed/Caution/Prohibited + import_allowed/import_caution/import_prohibited 문자열
```
- `Surface(tone.container, shapes.extraSmall) { Row(padding 10×4, spacedBy(4)) { Icon(iconSmall, tone.content) ; Text(labelMedium, tone.content) } }`, `heightIn(min = 28)`, **누를 수 없음**, maxLines 없음. 누를 수 있는 태그가 필요하면 `StatusTag`를 쓰지 말고 `SelectChip`/`AssistChip`(minTouch, `Role.Button`, `onClickLabel`)을 쓴다(17 ⑥).

### 4.13 PhotoBox v2 / PhotoHeaderCard / CountryPhotoTile / PhotoChip

```kotlin
@Composable fun PhotoBox(@DrawableRes photo: Int?, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.large,
    minHeight: Dp = 200.dp, alignment: Alignment = Alignment.Center, content: @Composable BoxScope.() -> Unit)   // 기존 + alignment(크롭 기준)
@Composable fun BoxScope.PhotoTextArea(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)  // 아래 정렬 + 자체 스크림(3.7절)
@Composable fun PhotoHeaderCard(@DrawableRes photo: Int?, title: String, modifier: Modifier = Modifier, icon: ImageVector? = null,
    minHeight: Dp = 150.dp, content: @Composable ColumnScope.() -> Unit)   // 사진 머리 + 흰 몸통 카드 (기존 PhotoTopCard)
@Immutable data class ChipSpec(val icon: ImageVector, val text: String)   // text = 기존 home_chip_* 완성 문장 그대로
@Composable fun CountryPhotoTile(nameKo: String, nameEn: String, @DrawableRes photo: Int?, chips: List<ChipSpec>,
    onClick: () -> Unit, openLabel: String, modifier: Modifier = Modifier, large: Boolean = false)
@Composable fun PhotoChip(text: String, icon: ImageVector? = null)
```
- **`PhotoBox`는 안에서 `modifier.fillMaxWidth()`를 적용한다**(호출하는 쪽이 modifier를 넘겨도 사라지지 않게. `weight`와 함께 써도 동작). 01 쇼핑 사진 폭 잘림의 재발 방지.
- `PhotoTextArea`: 위쪽 padding 24dp 이상 강제 + 자체 스크림(3.7절). 사진 위 글자는 모두 이 안에.
- `CountryPhotoTile`: 2열이면 `minHeight 176/—`, 이름 `titleLarge` 흰색, 칩 1개(차이 칩: 무비자는 `EventAvailable`, 도착비자는 `Approval` + Caution 느낌의 흰 칩), 1열(`large`)이면 현재처럼 200dp·displaySmall·칩 2개. `semantics(mergeDescendants = true) { contentDescription = openLabel /* home_country_open, 변경 금지 */; stateDescription = chips.joinToString(", ") { it.text } ; role = Button }`. **`clearAndSetSemantics` 금지**(칩 Text가 트리에 남아야 `home_chip_visa_free` 테스트가 통과, D16).

### 4.14 JourneyStepper

```kotlin
@Composable fun JourneyStepper(current: Int, description: String, modifier: Modifier = Modifier, preview: Boolean = false)
```
- 기본 모드: `Row` 안 노드 6개(`Column(weight 1f) { 원 ; 라벨 }`) + 노드 사이 2dp 선. 지난 단계 = Accent 채움 원 32dp + 흰 `Check`, 지금 = 36dp AccentSoft + 2dp Accent 링 + 단계 아이콘(Accent), 다음 = 32dp Surface + 1dp LineStrong + 아이콘 InkTertiary. 라벨 `labelMedium`(지금만 Bold Accent), **autoSize 제거**(9sp 축소 금지), 라벨은 줄바꿈 허용.
- `rememberGridColumns() == 1`(쉬운 모드 또는 D4 칸 폭 기준 큰 글자): 현재 단계 아이콘 원 48dp + `today_stage_now` 문장(bodyLarge Accent) + 8dp 점 6개.
- `preview = true`(여행 없음): 모든 노드 Neutral, `준비`만 링 — "시작점" 표시.
- 아이콘: 준비 `Backpack` · 출국 `FlightTakeoff` · 도착 `FlightLand` · 여행 중 `Explore` · 귀국 `Cottage` · 정리 `TaskAlt`.
- 전체 `clearAndSetSemantics { contentDescription = description }` — `today_stage_desc` 그대로(`ReadyPortRootTest.talkBackLabels`).

### 4.15 ReturnCheckCard v2 (`ReturnCheck.kt`)

```kotlin
@Composable fun ReturnCheckCard(
    links: List<OfficialLink>, facts: List<SourcedText>, sourceNames: Map<String, String>,
    onOpenLink: (String) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false,
)
```
- `CardNewsCard(title = shopping_return_title(기존 `귀국 전 확인`), icon = Inventory2, tone = Accent, style = Surface)` — **노란 카드 전체 채움을 없앤다.**
- 본문: 안내 한 줄(기존 `shopping_return_body`, bodyMedium) → 사실 행 `IconBullet` 4개(아이콘·톤은 `IconKeys.returnFact(source)`: `customs_allowance` → `Savings`/Accent, `apqa_plant` → `Eco`/Caution, `apqa_livestock` → `NoMeals`/Danger, 기타 → `Info`/Neutral). **문장은 팩 원문 그대로**(값 하드코딩 없음, D11).
- 출처: `SourceList`(날짜 같은 3개 → 한 줄).
- 링크: `LinkRow` 3개(기존 라벨 그대로). 알약 버튼 제거.
- `compact = true`(홈): 사실 행을 `ExpandableDetail(return_check_more)` 안에 넣고, 접힌 상태에서는 아이콘 3개 요약 줄(`면세 한도`·`과일·식물`·`고기·축산물` — 주제 이름만, 값 없음, 신규 문자열)만 보인다. 출처·링크는 접힘 밖.

### 4.16 EmptyState / LockedState / ChoiceCard / ComingSoonGroup (`Tiles.kt`)

```kotlin
@Composable fun EmptyState(icon: ImageVector, title: String, body: String?, modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral, action: (@Composable () -> Unit)? = null)
@Composable fun LockedState(title: String, body: String?, buttonLabel: String, onUnlock: () -> Unit,
    modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Lock, badgeIcon: ImageVector = Icons.Outlined.Fingerprint)
@Composable fun ChoiceCard(title: String, body: String?, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, emphasized: Boolean = false, preview: (@Composable () -> Unit)? = null)
@Composable fun SelectTile(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)
// 작은 단일 선택 타일(26 예약 종류): selectable(role = RadioButton), 세로 Column { 아이콘 ; 라벨 }, 선택 = Accent 채움 + 흰 글자 + Check
@Composable fun ComingSoonGroup(items: List<Pair<ImageVector, String>>, modifier: Modifier = Modifier)
```
- `EmptyState`: 가운데 정렬, 원 96dp(tone.container) + 아이콘 40dp, titleLarge(heading), bodyLarge 가운데, 선택 버튼. 오류가 아닌 상태에 Caution 금지.
- `LockedState`: Navy 원 96dp + 흰 `icon` 40(기본 Lock) + 오른쪽 아래 작은 `badgeIcon` 배지(원 32, AccentSoft — 기본 Fingerprint, 21은 Lock), 제목, 설명, `PrimaryButton(icon = Fingerprint)`.
- `ChoiceCard`: 카드 전체 `clickable(role = Button)`, `containerColor = Surface`, `heightIn(min = 96)`, `Row { IconBadge(52) ; Column { title(titleLarge) ; body ; preview } ; NavigateNext }`, `emphasized`면 2dp Accent 테두리 + AccentSoft 바탕. **`preview`(예: `가 가`)는 `clearAndSetSemantics {}`로 숨긴다**(TalkBack이 잡음으로 읽음). 카드 이름은 title + body.
- `SelectTile`: 칸 폭이 약 109dp(3칸)라 가로 Row(배지 52 + 글 + 셰브론)는 들어가지 않으므로 세로 배치. `heightIn(min = tileMinHeight)`, 부모 `selectableGroup()`. 1열이면 가로 배치.
- `ComingSoonGroup`: Neutral 연한 카드 한 장, 제목 `coming_soon_group(곧 추가돼요)` + 항목마다 `Row(Modifier.semantics(mergeDescendants = true) { disabled() }) { Icon(Schedule 또는 항목 아이콘, InkTertiary) ; Text(bodyMedium InkSecondary) }` + `StatusTag(coming_soon, Soon)` 1개. 누를 수 없음. **항목 글자가 든 노드에 `Disabled` semantics가 있어야** `WalletUiTest`의 `onNodeWithText(passport_chip_soon).assertIsNotEnabled()`가 통과하고, TalkBack도 `사용 안 함`으로 읽는다.

### 4.17 ChoiceSegments / SelectChip (`Controls.kt`)

```kotlin
@Composable fun <T> ChoiceSegments(options: List<T>, selected: T, onSelect: (T) -> Unit,
    label: @Composable (T) -> String, icon: (T) -> ImageVector?, modifier: Modifier = Modifier)
@Composable fun SelectChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null, avatar: (@Composable () -> Unit)? = null)
```
- `ChoiceSegments`(`rememberGridColumns() == 2`일 때): 트랙 `SurfaceSunken` + **1dp `LineStrong` 테두리**(모서리 16, padding 4 — 폭 340dp 미만이면 padding 0) 안 칸들 `weight(1f)`, 부모 `selectableGroup()`. 선택 칸 = Accent 채움 + 흰 글자 + `Check` 16/20 아이콘, 비선택 = 투명 + Ink 글자 + 칸 아이콘(InkSecondary). **높이 `heightIn(min = buttonHeight)`**(최소값 — 2줄 라벨이 잘리지 않음), `selectable(role = Role.Tab)`. 폭 340dp 미만이면 칸 아이콘 없이 한 줄 글자만.
- `ChoiceSegments`(`rememberGridColumns() == 1`, 즉 쉬운 모드·큰 글자): 세그먼트 대신 **폭 전체 세로 라디오 목록**(`selectable(role = Tab)` 행 = 아이콘 + 글자 + 선택 시 Check, 각 행 minTouch). 이 경우 sticky로 고정하지 않는다.
- `SelectChip`: `FilterChip` + `FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Surface, selectedLeadingIconColor = Surface, labelColor = Ink)`, 비선택 테두리 1dp `LineStrong`, 선택이면 leading `Check`, 아니면 `leadingIcon`/`avatar`(24dp 원형 나라 사진 썸네일). 모서리 12, `minTouch`. `singleChoice: Boolean = true` 인자 추가 — **FilterChip의 기본 role은 Checkbox라서, 한 개만 고르는 곳(나라 14·20, 정렬 07, 분류 18, 목적지 19)에서는 `Modifier.semantics { role = Role.RadioButton }`로 덮어쓰고 부모에 `selectableGroup()`**을 둔다. 켬·끔 토글(17 `form_local_large`)은 `singleChoice = false`(Checkbox 그대로).
- 쓰는 곳: `ChoiceSegments` = 나라 섹션(03~06), 영상 정렬(07). `SelectChip` = 나라 칩(14·20), 쇼핑 분류(18), 이동 목적지(19), 현지어 크게(17, 토글). 예약 종류(26)는 `SelectTile`. 입국 카드의 여행 목적·숙소 종류는 `SelectChip` 대신 **폭 전체 라디오 행**(17 참고).

---

## 5. 아이콘 맵

스타일: `Icons.Outlined.*`, 방향성 아이콘은 `Icons.AutoMirrored.Outlined.*`(표에 `AM.` 표기). 모두 `material-icons-extended`(core 포함)에 존재함을 확인했다. 코드 매핑은 `components/IconKeys.kt` 한곳에서.

### 5.1 탭·공통

| 개념 | 아이콘 |
|---|---|
| 홈 탭 / 처음으로 | `Outlined.Home` (선택 시 `Filled.Home`) |
| 내 여행 탭 | `Outlined.Luggage` |
| 도움 탭 | `Outlined.SupportAgent` (D13) |
| 설정 탭 | `Outlined.Settings` |
| 입국 QR 탭(자녀 폰) / 입국 QR 보기 | `Outlined.QrCode2` |
| 소리로 듣기 | `AM.Outlined.VolumeUp` |
| 이전 화면 | `AM.Outlined.ArrowBack` |
| 다음·자세히 | `AM.Outlined.NavigateNext` |
| 펼치기 / 접기 | `Outlined.ExpandMore` / `Outlined.ExpandLess` |
| 외부 링크·공식 사이트 열기 | `AM.Outlined.OpenInNew` |
| 안내 | `Outlined.Info` |
| 출처·최종 확인 | `AM.Outlined.FactCheck` |
| 정부 비제휴 | `Outlined.Policy` |
| 제출은 직접 / 직접 하는 단계 | `Outlined.TouchApp` |
| 제휴 고지 | `Outlined.Handshake` |
| 인터넷 없어도 돼요 / 앱에 저장됨 | `Outlined.OfflinePin` |
| 인터넷 없음 | `Outlined.CloudOff` |
| 비행기 모드에서도 보여요 | `Outlined.AirplanemodeActive` |
| 준비 중 | `Outlined.Schedule` |
| 지우기 | `Outlined.DeleteOutline` |
| 여행 만들기·고치기·날짜 | `Outlined.EditCalendar` / 날짜 칸 `Outlined.CalendarMonth` |
| 찜 | `Outlined.FavoriteBorder` / 켬 `Filled.Favorite` |

### 5.2 여행 단계 (`JourneyStepper`)

| 준비 | 출국 | 도착 | 여행 중 | 귀국 | 정리 |
|---|---|---|---|---|---|
| `Backpack` | `FlightTakeoff` | `FlightLand` | `Explore` | `Cottage` | `TaskAlt` |

### 5.3 입국·비자

| 개념 | 아이콘 |
|---|---|
| 입국·비자 섹션 / 비자·도착비자 | `Outlined.Approval` |
| 입국 신고서(TDAC·VJW·SGAC·MDAC·All Indonesia) | `Outlined.AssignmentInd` |
| 앱이 칸을 채워 줌 / 직접 입력 칸 | `Outlined.EditNote` |
| 비자 없이 머무는 날 | `Outlined.EventAvailable` |
| 비용 무료(값이 정확히 `무료`일 때만, `feeIcon()`) | `Outlined.MoneyOff` |
| 비용·수수료 | `Outlined.Payments` |
| 카드 결제 | `Outlined.CreditCard` |
| 내는 기간·기한 | `Outlined.Schedule` |
| e-VOA 받기 | `Outlined.DownloadForOffline` |
| 공식 사이트(go.id 등) | `Outlined.VerifiedUser` |
| 대행·가짜 사이트 경고 | `Outlined.GppMaybe` |
| 여권 남은 기간 6개월 | `Outlined.HourglassBottom` |
| 돌아가는 표·항공권 | `AM.Outlined.AirplaneTicket` |
| 출국·입국 심사 | `Outlined.HowToReg` |
| 보안 검색 | `Outlined.Security` |
| 체크인·짐 맡기기·짐 찾기 | `Outlined.Luggage` |
| 공항 가기 | `Outlined.LocalAirport` |
| 타는 곳(게이트) | `Outlined.MeetingRoom` |
| 유심·인터넷 | `Outlined.SimCard` |
| 환전 | `Outlined.CurrencyExchange` |
| 숙소 / 숙소로 가기 | `Outlined.Hotel` |

### 5.4 여행 정보·도구

| 개념 | 아이콘 |
|---|---|
| 여행 정보 섹션 | `Outlined.Explore` |
| 전기·플러그 | `Outlined.Power` |
| 어댑터 | `Outlined.Outlet` |
| 전압 | `Outlined.ElectricBolt` |
| 주파수 | `Outlined.GraphicEq` |
| 한국 플러그 그대로 OK | `Outlined.CheckCircle`(Success) |
| 돈(섹션 `payment`) | `Outlined.Payments` |
| 현금·지폐 | `Outlined.LocalAtm` |
| 안전·여행경보(섹션 `safety`) | `Outlined.GppMaybe` |
| 들어갈 때(섹션 `entry`) | `Outlined.FlightLand` |
| 이동하기·차 부르기 | `Outlined.LocalTaxi` (Violet) |
| 지하철·버스 | `Outlined.DirectionsSubway` |
| 가는 곳·주소 | `Outlined.Place` |
| 기사님께 보여주기 | `Outlined.Hail` |
| 화면 크게 | `Outlined.Fullscreen` |
| 지도·오프라인 지도 | `Outlined.Map` (Teal) |
| 여행 영상 | `Outlined.SmartDisplay` (Accent — 빨간 `PlayCircle` 금지) |
| 영상 정렬: 조회수/최신/구독자 | `Outlined.Visibility` / `Outlined.NewReleases` / `Outlined.Groups` |
| 영상 검색 | `Outlined.Search` |

### 5.5 쇼핑·귀국

| 개념 | 아이콘 |
|---|---|
| 쇼핑 섹션·쇼핑 리스트 | `Outlined.ShoppingBag` (Help) |
| 분류: 전체 / 먹거리(`food`) / 기념품(`souvenir`) / 생활용품(`daily`) | `Outlined.Apps` / `Outlined.Restaurant` / `Outlined.Redeem` / `Outlined.ShoppingBasket` |
| 담기 / 담았어요 | `Outlined.AddShoppingCart` / `Outlined.CheckCircle` |
| 직원에게 보여주기 | `Outlined.Translate` |
| 반입 가능 / 주의 / 불가 | `Outlined.CheckCircle` / `Outlined.ReportProblem` / `Outlined.Block` |
| 귀국 전 확인(세관) | `Outlined.Inventory2` |
| 면세 한도(`customs_allowance`) | `Outlined.Savings` |
| 과일·식물 검역(`apqa_plant`) | `Outlined.Eco` |
| 고기·축산물(`apqa_livestock`) | `Outlined.NoMeals` |

### 5.6 도움·긴급 (`emergency.id` 매핑)

| ID / 개념 | 아이콘 |
|---|---|
| `tourist_police`, `kl_tourist_police`, `police` | `Outlined.LocalPolice` |
| `medical` | `Outlined.LocalHospital` |
| `ambulance`, `ambulance2` | `Outlined.Emergency` |
| `fire` | `Outlined.LocalFireDepartment` |
| `fire_ambulance`, `ambulance_fire`, `all` | `Outlined.Sos` |
| `sea` | `Outlined.Sailing` |
| `jnto_hotline`, `consular_call_center`(영사콜센터) | `Outlined.SupportAgent` |
| 대사관 | `Outlined.AccountBalance` |
| 모르는 ID | `Outlined.Call` |
| 전화 걸기(타일 오른쪽 위) | `Outlined.Call` |
| 여권 분실 절차 | `Outlined.ReportProblem` |
| 현지어 카드·번역 | `Outlined.Translate` |
| 문장 `restroom`/`lost`/`hospital`/`slowly`/`address`/`police`/`help` | `Wc` / `WrongLocation` / `LocalHospital` / `Hearing` / `Place` / `LocalPolice` / `PanTool` |

### 5.7 준비·지갑·설정

| 개념 | 아이콘 |
|---|---|
| 여권(`passport`)·내 정보 | `Outlined.Badge` |
| 보조배터리(`power_bank`) | `Outlined.BatteryChargingFull` |
| 멀티 어댑터(`plug_adapter`) | `Outlined.Outlet` |
| 전압 확인(`voltage_check`) | `Outlined.ElectricBolt` |
| 여행자 보험(`travel_insurance`) | `Outlined.HealthAndSafety` |
| 결제 수단(`payment`) | `Outlined.CreditCard` |
| 상비약(`medicine`) | `Outlined.Medication` |
| 모르는 준비물 | `Outlined.Checklist` |
| 미리 설치할 앱 | `Outlined.InstallMobile` |
| 예약 서류 / 종류 항공권·숙소·기타 | `Outlined.Description` / `AM.Outlined.AirplaneTicket`·`Outlined.Hotel`·`Outlined.Description` |
| 예약 번호 | `Outlined.ConfirmationNumber` |
| 같이 가는 사람 / 추가 | `Outlined.FamilyRestroom` / `Outlined.PersonAdd` |
| 내 정보는 이 휴대폰에만·잠김 | `Outlined.Lock` |
| 지문·얼굴로 열기 | `Outlined.Fingerprint` |
| 여권 촬영 / NFC 칩 / 값 확인 | `Outlined.PhotoCamera` / `Outlined.Nfc` / `AM.Outlined.FactCheck` |
| MRZ 확인 완료·검증됨 | `Outlined.Verified` |
| 여권 만료 | `Outlined.EventBusy` |
| 쉬운 모드 | `Outlined.TextIncrease` |
| 와이파이에서만 받기 | `Outlined.Wifi` |
| 자녀 폰 모드 | `Outlined.ChildCare` |
| 개인정보 안내 | `Outlined.PrivacyTip` |
| 출처와 알림 | `Outlined.Policy` |
| 사진 출처 | `Outlined.PhotoLibrary` |
| 앱 정보 | `Outlined.Info` |
| 첫 실행: 처음이에요 / 가 봤어요 | `Outlined.TextIncrease` / `Outlined.TravelExplore` |
| 오늘 쓴 돈 적기 | `AM.Outlined.ReceiptLong` |
| 입국 카드 확인: **출처별** 그룹 여권(`Passport`)/비행(`Flight`)/숙소(`Lodging`)/내가 적음(`User`) — 테스트가 `form_origin_*` 헤더를 찾으므로 출처 기준, 그룹 안 순서는 레시피 순서 | `Outlined.Badge` / `AM.Outlined.AirplaneTicket` / `Outlined.Hotel` / `Outlined.EditNote` |
| 여행 목적 관광/출장/회의/치료·건강/공부 | `BeachAccess` / `Work` / `Groups` / `LocalHospital` / `School` |
| 숙소 종류 호텔/게스트하우스/유스호스텔/아파트/친구 집 | `Hotel` / `House` / `Bed` / `Apartment` / `People` |
| 모르는 선택지(MY `my_state` 9개, `stay_type` `other` 등 매핑 없는 값) | **아이콘 없음**(옆의 `RadioButton`과 원이 두 개로 보이지 않게) |

---

## 6. 화면별 개편 계획 (29개)

표기: **유지 문자열** = 테스트·TalkBack이 의존하므로 문구·노드 구조를 바꾸면 안 되는 것(리소스 값 변경은 D17·`source_footer` NBSP/WORD JOINER만 허용). **신규 문자열** = 새 리소스 키(`res/values/strings_design_v2.xml`에 모두 모은다, 부록 B).

**공통 치환표 — 아래 29개 화면 목록에 없는 화면·상태에도 그대로 적용**(AutofillScreen, ManualModeScreen, PassportIntro/Scan/Manual, Present 열린 상태, Wallet Failed(NeedsAuth/Corrupted), Today Departure·WrapUp, Videos Loading, Help 나라 없음, Shopping 빈 상태, Trip 나라 없음·잘못된 값, Transport 장소 추가·여러 장소 선택):

| 지금 | 바꿀 것 |
|---|---|
| `TopicCard`/`InfoCard(CardTone.Notice)` 안내 | `NoticeBanner(Notice)` |
| 오류성 `TopicCard(Caution)` | `NoticeBanner(Caution)` 또는 `(Danger)`(되돌릴 수 없는 위험·금지) |
| `TopicCard(comingSoon)` | `ComingSoonGroup`(화면 맨 아래) |
| `OutlinedButton` | `SecondaryButton` |
| `TextButton` | `QuietButton` |
| `FilterChip` | `SelectChip` |
| `heightIn(min = 48.dp)`, `sizeIn(48)` | `Modifier.minTouch()` / `minTouchSize()` |
| 고정 sp | 3.2절 역할 |
| 빈 화면 글 | `EmptyState`(오류가 아니면 Neutral) |

**320×470dp 화면 예산**(Robolectric 기본 크기, qualifiers 없는 `ReadyPortRootTest`·`VideosTest`가 이 크기에서 스크롤 없이 찾는 것): 하단 탭을 빼면 보이는 높이 약 394dp. 이 크기에서 `rememberGridColumns()`는 1(폭 < 340dp)이지만 `ChoiceSegments`는 텍스트 전용 한 줄 세그먼트로 그린다(03·07 예외 — 섹션 전환은 칸 3개라 라디오 목록보다 짧음). ① 03: `guide_not_affiliated`는 약 390dp 위에서 시작(`countryPhotoCardOpensCountryWithSections`) ② 22: `settings_myinfo_open` 행은 `SecurityBanner` 바로 아래(`settingsShowsLocalOnlyPromiseAndMyInfo`) ③ 07: 머리·고지·검색·정렬·개수의 합계 약 420dp 이하, 첫 영상 카드의 `videos_open` 노드가 보임.

**공통 터치·보안**: 모든 `IconButton`/`IconToggleButton`(히어로 뒤로·찜, AppScreen 뒤로, 검색 지우기)은 `minTouchSize()`(48/56). **FLAG_SECURE 화면(`SecureScreen()`을 부르는 곳: 21·27 `PresentScreens`, 23·24 지갑, 25 여권 흐름 전체(소개·촬영·직접 입력·값 확인), 26 예약 서류, 17·Autofill·ManualMode 입국 카드)은 `SecurityBanner`를 둔다.** 지갑(23·24)은 지금처럼 전체 배너를 맨 위에, 21·25·26·27은 `compact`(Lock 아이콘 + 한 줄)를 맨 위에 둔다. 입국 카드 화면(17·Autofill·ManualMode)은 정부 비제휴 `NoticeBanner`가 첫 정보 항목이어야 하므로(원칙 5) **compact `SecurityBanner`를 그 바로 다음**에 둔다. 보안 화면의 파괴 확인은 `DestructiveConfirm(secure = true)`.

### 00 첫 실행 — `onboarding/FirstRunScreen.kt`
- 순서: ① `PhotoBox(Photos.Home, minHeight 220, shapes.extraLarge)` 히어로 — **`PhotoTextArea` 안에** 앱 심볼(런처 전경 벡터 40dp) + `home_brand`(labelLarge) + `first_run_title`(displaySmall, heading)(사진 윗부분 0.18 틴트 위에 글자를 두지 않음, 3.7절) ② `first_run_body`(bodyLarge) ③ `ChoiceCard`(TextIncrease, `first_run_yes`, 미리보기 `가 가` 두 크기(`clearAndSetSemantics {}`로 숨김) + `first_run_easy_preview`, emphasized) ④ `ChoiceCard`(TravelExplore, `first_run_no`, `first_run_basic_preview`).
- 200%: 히어로 minHeight 160, 세로 스크롤 허용.
- 유지: `first_run_title`, `first_run_yes`, `first_run_no`(카드 안 제목 Text로, 클릭 가능한 노드 이름).
- 신규: `first_run_easy_preview`(큰 글자·큰 버튼), `first_run_basic_preview`(기본 화면).

### 01 홈 / 02 홈(여행 있음) — `home/HomeScreen.kt`
- 순서:
  1. `HomeHero`: `PhotoBox(Photos.Home, minHeight 200, xl)` + `home_brand` + `home_title`(displaySmall, **heading 유지**) + `home_subtitle` + `TrustStrip`(FlowRow `PhotoChip` 3개: `FactCheck 공식 출처만` · `Lock 폰에만 저장` · `OfflinePin 인터넷 없이도`).
  2. (02만) `TripCountdownCard`(히어로 아래 −24dp 겹침 대신 **히어로 바로 아래 카드**: Accent 채움, 왼쪽 나라 사진 원형 56dp 썸네일(장식), `home_trip_label` eyebrow(White85), `home_trip_days` stat 스타일(Surface), `FlightTakeoff` + 날짜 한 줄 `home_trip_dates`(Surface), 흰 `PrimaryButton(home_trip_open, icon AM.NavigateNext, colors = ButtonStyles.onDark())`).
  3. `SectionHeader(home_countries_title, subtitle = home_countries_body)`(아이콘 없음 — 바로 아래 사진이 그림 역할).
  4. 나라: 여행 중인 나라(없으면 첫 나라)는 `CountryPhotoTile(large = true)` 1장, 나머지는 `TileGrid` 2열(`CountryPhotoTile`, 칩 1개 = 차이 정보: 무비자 `ChipSpec(EventAvailable, home_chip_visa_free 완성 문장)` / 도착비자 `ChipSpec(Approval, home_chip_visa_arrival)`). 쉬운 모드·큰 글자: 모두 1열 `large`. **그리드 바로 아래 `SourceList`**(칩에 쓴 각 나라 `req.source` 이름과 최종 확인 날짜 — 정책 값 칩이 출처 없이 보이지 않게, 원칙 5).
  5. `sectionGap` + `SectionHeader(home_basics_title)`.
  6. `PhotoHeaderCard(Photos.Airport, home_departure_title, icon FlightTakeoff)` 안에 `StepList`(아이콘: LocalAirport, Luggage, Security, HowToReg, MeetingRoom; 1단계 detail `today_departure_step1_detail`).
  7. `PhotoHeaderCard(Photos.Packing, prepare_items_title)` 안에 `FactChip` 3개(Power `home_items_plug`, ElectricBolt `home_items_voltage`, BatteryChargingFull `home_items_powerbank` — 주제 이름만) + 진행 `n/5`(데이터 있을 때) + `PrimaryButton(준비물 확인하기, icon Checklist)`.
  8. **쇼핑 사진 카드(`home_return_photo`) 삭제.** `ReturnCheckCard(compact = true)`.
  9. `CardNewsCard(style = Navy, icon Lock, home_passport_title, body home_passport_body)` + 흰 버튼 `home_passport_open`(icon Badge, `ButtonStyles.onDark()`).
  10. `ListRow`(icon Sos, tone Help, `today_help_title` → 새 문구 `help_shortcut_title` 급할 때는 도움, body `today_help_body`, `trailing = RowTrailing.Custom { OfflinePin 칩 }`, onClick = 새 `HomeActions.openHelp`) — "누르세요"라고 쓰고 누를 수 없던 문제 해결.
- 줄이는 글: 출국 1단계를 제목+보조문으로 분리, 반복 칩 제거(타일당 1개).
- 유지: `home_title`(heading), `home_country_open`(contentDescription 정확히), `home_chip_visa_free`(첫 나라 칩 노출: `allMvpCountriesAreSavedOffline`가 `onFirst().assertExists()` — 칩 Text가 트리에 남아야 함, D16), `home_trip_days`, `home_trip_open`, `home_passport_open`.
- 신규: `trust_official`, `trust_local`, `trust_offline`, `home_trip_dates`(%1$s ~ %2$s), `home_items_plug`, `home_items_voltage`, `home_items_powerbank`, `help_shortcut_title`, `return_topic_allowance`, `return_topic_plant`, `return_topic_livestock`, `return_check_more`, `today_departure_step1_detail`.
- 액션 추가: `HomeActions.openHelp: () -> Unit = {}`(배선은 8장 3단계).

### 03 나라 · 입국·비자(태국) — `country/CountryScreen.kt`
- 히어로 `CountryHero`: **minHeight 280 → 220dp**(320×470 예산, 6장 머리말), 이름 displayMedium(34.sp 하드코딩 제거), 메타 줄을 `PhotoChip` 2개로 분리(`CalendarMonth guide_last_verified`, `OfflinePin guide_origin_bundled/downloaded`) — 이름·칩은 `PhotoTextArea` 안. 뒤로·찜 버튼은 `minTouchSize()`(48/56, 쉬운 모드 56) + 지금의 검정 0.35 원형 바탕 유지(`country_back` 설명 유지), 찜 버튼 설명 `explore_favorite_add/remove`.
- 섹션 전환: `ChoiceSegments`(Approval 입국·비자 / Explore 여행 정보 / ShoppingBag 쇼핑, 탭 전환이며 스와이프 아님). **`rememberGridColumns() == 2`일 때만 stickyHeader로 고정**하고 헤더 바탕은 `Ground`로 칠한다(내용이 비쳐 보이지 않게). 1열(쉬운 모드·큰 글자)이면 고정하지 않고 세로 라디오 목록(4.17절) — 쉬운 모드·200%에서 130dp 넘는 머리가 화면을 가리지 않게. 폭 340dp 미만이면 아이콘 없는 한 줄 세그먼트. 섹션 안 `scrollToKey`는 sticky 헤더 높이만큼 오프셋을 뺀다.
- 입국 순서: ① `NoticeBanner(guide_not_affiliated, Policy, secondLine = country_submit_self, TouchApp)` ② 비자 `CardNewsCard(style = Accent, icon Approval, eyebrow country_visa_title)` 안 `FactGrid`: [EventAvailable `fact_days`(stayLimitDays) · `fact_label_visa_free`/`fact_label_visa_arrival`, source = req] [`feeIcon` `shortValue(form.feeKo)` · `fact_label_form_fee`, source = form.source]. **기간 타일(`windowDaysIncludingArrival`)은 이번 릴리스에서 그리지 않는다**(D11 — 태국 팩 불릿 `도착 3일 전부터 도착하는 날까지`와 모순, 팩 수정·lint 후 되살림). **`직접` 타일은 두지 않는다**(팩 사실이 아닌 앱 주장이고 ① 배너 둘째 줄과 겹침). 아래 `req.summaryKo`(bodyLarge, **굵게 하지 않음**), 카드 `sources` = req.source + 타일 출처 전부(`SourceList`, onColor — 태국은 모두 `mofa_th`라 한 줄 `출처 외교부 해외안전여행 · 태국 · 최종 확인 2026.09.28` 그대로). ③ 양식 `CardNewsCard(icon AssignmentInd, eyebrow country_form_label, title form.nameKo)` — 본문 한 줄, `guide_form_fee`·`guide_form_window`는 라벨-값 `IconBullet`(Payments/Schedule) 2줄(팩 값 그대로의 글 행), `PrimaryButton(country_form_start, icon EditNote)`, `SourceFooter`. ④ `들어갈 때` `CardNewsCard(icon FlightLand)` + `IconBullet` 목록(불릿 문장 그대로, 아이콘은 **Neutral `Check` 공통** — 문장 의미를 앱이 추측하지 않도록) + `SourceFooter`. ⑤ `SecondaryButton(country_plan_trip, icon EditCalendar)`.
- 주의: `window_days_including_arrival = 3`은 **"도착일 포함 3일"**이지 "3일 전부터"가 아니다(태국 예: 5월 4일 도착 → 5월 2~4일). 타일을 되살릴 때 라벨을 정확히 쓴다(`fact_label_window`).
- 유지: `태국 입국 카드 (TDAC)`(form.nameKo 단독 Text), `비자 없이 90일`(summary 또는 타일에 substring), `source_footer`(`외교부 해외안전여행 · 태국`, `2026.09.28`) 단독 Text, `country_form_start`, `country_back`, `guide_not_affiliated`(320×470에서 약 390dp 위에서 시작).
- 신규: `country_submit_self`(마지막 제출은 직접 눌러요), `fact_days`(%1$d일), `fact_label_visa_free`(비자 없이 머물러요), `fact_label_visa_arrival`(도착비자로 머물러요), `fact_label_form_fee`(입국 카드 비용), `fact_label_visa_fee`(비자 비용), `fact_label_window`(`도착일 포함\n내는 기간` — 타일 복원용, 이번 릴리스 미사용).

### 04 나라 · 입국·비자(인도네시아, e-VOA) — 같은 파일
- 비자 카드: 03과 같은 `FactGrid` — [Approval `30일` 도착비자, source = req(`mofa_id`)] [Payments `IDR 500,000`(`shortValue(apply.feeKo)`) `fact_label_visa_fee`, source = apply.source(`embassy_id_evoa`)] [MoneyOff `무료` `fact_label_form_fee`(All Indonesia), source = form.source(`imigrasi_aid`)]. `직접` 타일 없음. 카드 아래 `SourceList`가 세 출처를 모두 보인다(날짜가 같은 `mofa_id`·`imigrasi_aid`는 `, `로 한 줄, 2026.09.30인 `embassy_id_evoa`는 다음 줄) — `IDR 500,000`이 외교부 정보처럼 보이지 않게. 문장 요약은 그 위, `country_visa_link`는 `SecondaryButton(onDark = true, icon OpenInNew)`(투명 + 흰 테두리).
- e-VOA 카드: `CardNewsCard(icon Approval, tone Success, eyebrow country_visa_apply_label, title apply.nameKo)` → 비용 행 `IconBullet(Payments, guide_form_fee)` + `StatusTag`는 쓰지 않음(환불 불가는 feeKo 원문에 있음) → `SectionHeader`급 소제목 `country_visa_apply_steps` → **`StepList`(numbered, 번호만 — 아이콘·`직접 해요` 태그를 단계 인덱스에 붙이지 않는다**. ARIA가 단계 순서·개수를 바꾸면 결제 아이콘·태그가 엉뚱한 단계에 붙어 `제출·결제는 직접` 안내가 틀어짐, D11) → **경고는 `NoticeBanner(Danger, GppMaybe, apply.warningKo)`를 버튼 바로 위** → `country_visa_apply_note`(TouchApp IconBullet — 앱 문자열이라 단계와 무관) → `PrimaryButton(country_visa_apply_start)` → `SourceFooter`(apply.source).
- All Indonesia 양식 카드·들어갈 때 카드: 03과 같음.
- 유지: `인도네시아 전자 도착비자 (e-VOA)`(apply.nameKo 단독 Text, `VisaApplyTest`), `country_visa_apply_start`.
- 신규: 없음(`step_self`는 쓰지 않으므로 만들지 않음).

### 05 나라 · 여행 정보 — 같은 파일
- 순서: ① `SectionHeader(country_travel_tools_title, icon Explore)` + `InfoTileGrid` 2×2(Translate `tile_phrases_emergency` → openHelp, LocalTaxi(Violet) `move_title` → openMove, SmartDisplay `tile_videos` → openVideos, Map(Teal) `tile_maps` → 지도 카드로 `state.scrollToKey(keys, "maps")`(4.1절) — 라벨은 2열 한 줄 7자 규칙에 맞춘 짧은 키, 3.2절) ② 전기 `CardNewsCard(icon Power)`: 맨 위 결론 `NoticeBanner(Success, CheckCircle, guide_power_kr_fits)` 또는 `(Caution, Outlet, guide_power_kr_adapter)`(krPlugFits null이면 생략), `FactGrid`[Power plugKo(shortValue 안 되면 행) / ElectricBolt voltage / GraphicEq frequency], `SourceFooter` ③ 돈·안전 섹션 `CardNewsCard(icon = IconKeys.section(id))` + `IconBullet` 목록 + 출처(여행경보 단계 배지는 팩에 구조 필드가 없어 이번엔 하지 않음 — 부록 A) ④ 현지에서 급할 때 Navy 카드 → 타일로 흡수했으므로 **삭제**(타일 `country_help_open`) ⑤ YouTube·이동하기 카드 삭제(타일로) ⑥ 지도 저장 `CardNewsCard(icon Map, tone Teal)` + `StepList`(AccountCircle→OfflinePin→Download 없이 Neutral 번호만) + note + `SourceFooter`.
- 줄이는 글: `이동하기. 가는 곳을…` 중복 제거(`move_speech`를 본문에 쓰지 않음). 빨간 PlayCircle 제거.
- 유지: `country_tab_travel`, `explore_maps_step1~3`(D17 접두 삭제). `country_help_open`·`country_videos_*`는 현재 테스트가 찾지 않는다. 타일에는 기존 긴 문구(13자) 대신 새 짧은 키를 쓰고, 옛 키는 2단계에서 사용처가 0이면 지운다. 지도 카드 제목은 `explore_maps_title` 그대로.
- 신규: `country_travel_tools_title`(현지에서 쓰는 도구), `tile_phrases_emergency`(`현지어와\n긴급 번호`), `tile_videos`(여행 영상), `tile_maps`(오프라인 지도).

### 06 나라 · 쇼핑(일본) — 같은 파일
- ① `CardNewsCard(icon ShoppingBag, tone Help, title shopping_title)` — **사진 없음**(히어로가 이미 위에 있음, 호이안 사진 제거). 부제 `shopping_subtitle_v2`(관광청 쇼핑 안내에서 골랐어요 — `준비 중` 문구 삭제). 품목 3개 = `ListRow`형 미리보기(IconBadge = 분류 아이콘, 제목 nameKo, `trailing = RowTrailing.Custom { ImportVerdictBadge }`) — **whyKo는 미리보기에 넣지 않는다**(줄 수로 자르기 금지, 원칙 6. 전체 글은 18에서). 품목 아래(카드 안) **`SourceList`(품목 출처 + 반입 판정 출처 `importSource`)** — 반입 판정이 출처 없이 보이지 않게. `PrimaryButton(shopping_open, icon AM.NavigateNext)`(`외 N개 더` 문구는 버튼 위 bodySmall). ② `ReturnCheckCard`(compact = false).
- 유지: `shopping_open`, `country_tab_shopping`, `import_*`.
- 신규: `shopping_subtitle_v2`. (`shopping_subtitle`은 18에서도 v2로 교체, 기존 키는 삭제하지 말고 미사용 처리 — lint unused는 허용 목록에)

### 07 여행 영상 / 08 영상(오프라인) — `video/VideosScreen.kt`
- 07: 제목 줄 `SectionHeader`형(icon SmartDisplay) → `NoticeBanner(Notice, 기존 면책 문구)` → 검색칸(Outlined.Search, 모서리 12, `LineStrong`, 지우기 `IconButton`은 `minTouchSize()` + 설명 `videos_search_clear`) → `ChoiceSegments`(Visibility/NewReleases/Groups) → 개수 캡션 → 영상 카드: `Surface` 카드로 묶고 썸네일 16:9 모서리 16, 가운데 재생 = 흰 원 48dp + `Filled.PlayArrow` Navy, 길이 배지 유지, 제목 titleMedium(**maxLines 없음 — 전체 표시**, 원칙 6), 메타 한 줄 `Visibility 조회수 · CalendarMonth 날짜`(bodySmall), 카드 전체 `semantics(mergeDescendants = true)` + `role = Button`. **`contentDescription = videos_open(title)`은 카드의 클릭 노드 하나에만** 둔다(중복 노드 금지). 약관 = `ListGroup`(Policy 헤더) 안에 **`videos_terms` 설명문을 그대로 먼저 보이고**(YouTube API 약관상 필수 — 빼지 않음) 그 아래 `LinkRow` 2개(`videos_youtube_terms` 등 그대로).
- **320×470dp 예산(`VideosTest`)**: 이 테스트는 `app/src/test/java/com/readyport/video/VideosTest.kt`(ui/ 밖)에 있고 qualifiers 없이 돈다. 스크롤 없이 첫 영상의 `videos_open` 설명, `videos_count`, `videos_search_empty`가 보여야 하고, `videos_sort_recent`·`videos_youtube_terms` 클릭과 `videos_search_clear` 설명도 확인한다. 지금 첫 썸네일이 약 365dp에서 시작하므로 **머리·고지·검색·정렬·개수의 합계는 약 420dp 이하**로 정한다: 폭 340dp 미만이면 세그먼트 트랙 padding 0·아이콘 없음, `NoticeBanner` title 없음, `SectionHeader` 아이콘 배지 생략.
- 08: 경고 카드 대신 `EmptyState(CloudOff, 기존 제목, 기존 본문)`(Neutral 톤). 약관 `ListGroup`(설명문 포함) 유지.
- Loading 상태(갤러리 밖): `EmptyState`형 진행 표시(`CircularProgressIndicator` + 기존 문구), 공통 치환표 적용.
- `Filled.Search/Clear/PlayCircle` → Outlined(재생 원 안 PlayArrow는 채움 허용 — 상태 아님이지만 재생 기호 관례).
- 유지: `videos_youtube_terms`, `videos_sort_recent`, `videos_terms`(본문 전체), `videos_open`, `videos_count`, `videos_search_empty`, `videos_search_clear` 등 기존 문자열 — **`VideosTest`를 07 회귀 테스트로 포함**.

### 09 내 여행(없음) / 10 준비 중 / 11 도착 / 12 여행 중 / 13 귀국 — `today/TodayScreen.kt`
공통: 제목·부제는 AppScreen 그대로(`today_title`, `today_d_day`, `today_day_n`), 부제가 `내 여행`으로 제목과 겹치는 경우(11~13)는 `today_trip_dates`(11월 3일 ~ 7일)로 교체. `StageBar` → `JourneyStepper`(설명 `today_stage_desc` 그대로). 맨 아래 도움 카드 → `ListRow(Sos, Help, help_shortcut_title, today_help_body, onClick = actions.help)`. `여행 고치기`(`trip_edit_title` 버튼) → 화면 맨 아래 `QuietButton(icon EditCalendar)`.
- **09 (NoTrip)**: `JourneyStepper(preview = true)` → `NextCard` = `CardNewsCard(style Accent, icon EditCalendar, eyebrow today_next_label, title today_next_title, body today_next_body)` + 흰 `PrimaryButton(today_make_trip, colors = ButtonStyles.onDark())` → `SecondaryButton(today_next_button, icon TravelExplore)`(카드 밖 밝은 바탕).
- **10 (Preparing)**: NextCard(icon AssignmentInd, `today_task_form_title`, `today_task_form_body`, 버튼 `prepare_form_open`) — 여권 미등록이면 `today_task_passport_*`(icon Badge). **요약 타일 그리드는 두지 않는다**(비평 반영): NextCard와 같은 행동을 반복해 `한 화면 할 일 하나` 우선순위를 우회하고, 타일에 `prepare_form_open`을 다시 쓰면 `TripUiTest.preparingShowsFormWhenWindowOpens`의 `onNodeWithText(prepare_form_open)`이 노드 2개를 찾아 실패한다. 또 `TodayActions.prepare`는 15(여행 준비)로 가지 16(꼭 챙길 물건)으로 가지 않는다. 16 바로가기가 필요해지면 `TodayActions.openEssentials: () -> Unit = {}`를 추가하고 2단계에서 배선한다(이번 범위 밖).
- **Departure**: `StepList`(출국 5단계, 5.3 아이콘) + `PrimaryButton(today_arrived_button, icon FlightLand)`. **WrapUp**: 지금 내용·문구 그대로 공통 치환표 적용(안내 카드는 `CardNewsCard(icon TaskAlt)`, 버튼은 4.11절). 두 상태는 0단계에서 Gallery에 `today-departure`, `today-wrapup`으로 추가해 A11yAudit 대상에 넣는다.
- **11 (Arrival)**: NextCard = eyebrow `today_next_label` + title `today_arrival_qr_title`(입국 심사 때 QR을 보여 주세요) + 흰 버튼 `today_show_qr`(QrCode2, `ButtonStyles.onDark()`). 아래 `CardNewsCard(icon FlightLand, title today_arrival_title)` + `StepList`(HowToReg, Luggage, SimCard, CurrencyExchange, Hotel) + `SecondaryButton(today_arrival_done, icon TaskAlt)`(D10).
- **12 (Traveling)**: `InfoTileGrid` 2×2(Hotel `today_go_stay` **emphasized Navy** — 라벨 Surface·배지 OnDark·셰브론 White80, Translate(Help) `today_phrases`, QrCode2 `today_show_qr`, AM.ReceiptLong `today_expense`). 쉬운 모드·큰 글자(D4 칸 폭 기준) 1열 가로형. 라벨은 테스트가 찾는 기존 문자열이라 값을 바꾸지 않고, 칸 폭 규칙으로 `숙소로 돌아가/기` 끊김을 막는다.
- **13 (Return)**: ① `CardNewsCard(icon Cottage, title today_return_title, body today_return_customs)` ② 담아 둔 물건 = `CardNewsCard(icon ShoppingBag)` 안 행들을 **불가 → 주의 → 가능 순 정렬**, 각 행 `IconBadge(분류) + nameKo + ImportVerdictBadge + importNote(bodyMedium)`, **카드 맨 아래 `SourceList`(품목 출처 + `importSource`)** — 반입 판정과 `망고는 검역본부가 밝힌 수입 금지 과일` 같은 importNote가 출처 없이 보이지 않게 ③ `ReturnCheckCard` ④ 여권 지우기 제안 = `CardNewsCard(style Surface, icon Lock, tone Neutral, title today_destroy_title, body today_destroy_body)` + `SecondaryButton(today_destroy_later, icon Schedule)` + `DangerButton(today_destroy_now)` — **대화상자 없음(D8)**, `7일 미루기`가 먼저(위).
- 유지: `today_stage_desc`, `today_d_day`, `today_day_n`, `today_task_form_title`, `prepare_form_open`, `today_task_passport_title`, `today_departure_step2`(리소스), `today_arrived_button`, `today_arrival_title`(onFirst — 두 번 나와도 됨, 한 번으로 줄임), `today_show_qr`, `today_arrival_step1`, `today_go_stay`, `today_phrases`, `today_expense`, `today_destroy_body`, `today_destroy_later`, `today_destroy_now`.
- 신규: `today_trip_dates`, `today_arrival_qr_title`. 리소스 수정: `today_departure_step1~5`, `today_arrival_step1~5` 접두 삭제(D17).

### 14 여행 고치기 — `trip/TripScreens.kt`
- 나라 선택: `TileGrid` 2열 `CountryRadioCard`(원형 48dp 사진 썸네일 `rememberThumbnail` + 이름 titleMedium, 선택 시 2dp Accent 테두리 + `Filled.CheckCircle`, `selectable(role = RadioButton)`), 쉬운 모드 1열.
- 날짜(D20): **M3 `DatePickerDialog`는 쓰지 않는다**(달력 모드는 달 목록이 가로로 스와이프되는 `LazyRow`라 7장 1번 위반, 날짜 칸이 고정 약 40dp라 200%·쉬운 모드에서 숫자가 잘림). 지금의 텍스트 입력(`YYYY-MM-DD`, 라벨 `trip_start`/`trip_end`)을 유지하고 `OutlinedTextField(leadingIcon = CalendarMonth, supportingText = 11월 3일 (화) — 값이 올바를 때만)`로 바꾼다. 저장값 형식 검증은 지금처럼. 시간대 변환이 없으므로 UTC 하루 밀림 문제도 없다. 아래 요약 `FactChip`(`trip_nights` 4박 5일).
- 주 버튼: 편집이면 `trip_save_edit`(저장하기), 새로 만들기면 `trip_save`. 삭제 = `DangerButton(trip_delete)` + `DestructiveConfirm(trip_delete_confirm_title, trip_delete_confirm_body)`.
- 나라 없음·잘못된 값 상태(갤러리 밖): 공통 치환표(오류는 `NoticeBanner(Caution)`).
- 안내 문장 `이 휴대폰에만 저장해요`는 `IconBullet(Lock)`.
- 유지: `trip_start`, `trip_end`(필드 라벨), `trip_delete`, `trip_create_title/edit_title`.
- 신규: `trip_save_edit`, `trip_nights`(%1$d박 %2$d일), `trip_delete_confirm_title`(이 여행을 지울까요?), `trip_delete_confirm_body`(**확정**: 여행 날짜와 입국 카드 알림이 지워져요. 여권·예약 서류는 남아요. — 실제 동작 `TripViewModel.delete()` = `trips.clear()` + `TripNotifications.cancelFormWindow()`, 지갑·장바구니는 그대로).

### 15 여행 준비 — `tabs/TabScreens.kt`
- ① `NoticeBanner(prepare_disclaimer, Policy)`(문구 유지, 제출은 직접 포함) ② 양식 카드 = `CardNewsCard(icon AssignmentInd, eyebrow prepare_forms_title(나라 이름 포함 새 eyebrow `prepare_form_eyebrow`: %1$s · 도착 전에 내요), title form.nameKo)` — `태국 · 태국 입국 카드` 중복 제거, `FactChip` 1개(비용 `shortValue`, `feeIcon`) + 기간은 03처럼 `IconBullet(Schedule, guide_form_window)` 글 행(기간 칩·타일은 D11에 따라 이번 릴리스에서 만들지 않음), `PrimaryButton(prepare_form_open, icon EditNote)`, `SourceFooter`(form.source) ③ `IconTile`(Horizontal, Checklist, `prepare_items_title`, supporting `prepare_items_body`) ④ `ComingSoonGroup`(InstallMobile `prepare_apps_title`, Description `prepare_bookings_title`).
- (선택, 8장 3단계 배선) `예약 서류`를 지갑의 예약 추가로 연결할 수 있으면 `ComingSoonGroup`에서 빼고 `IconTile`로.
- 유지: `prepare_form_open`, `guide_not_affiliated` 계열 문구.
- 신규: `prepare_form_eyebrow`, `coming_soon_group`.

### 16 꼭 챙길 물건 — `prep/EssentialsScreen.kt`
- ① `NoticeBanner(essentials_disclosure, Handshake)` — **목록보다 위 유지**(`disclosureIsAtTopAboveItems`) ② 진행 카드: stat `2 / 5`(`essentials_progress_stat`) + `LinearProgressIndicator`(8dp, 둥근 끝, `drawStopIndicator = {}`로 점 제거) + `essentials_progress` 문장(유지) ③ 항목 `CheckRowCard`(아래).
- `CheckRowCard(row, onHave, onOpenLink)`: 머리 `Row(toggleable(role = Checkbox))` = `IconBadge(IconKeys.essential(id))` + `Column { nameKo(titleLarge) ; 상태 줄 }` + 오른쪽 `Checkbox(onCheckedChange = null)`(**Checkbox로 결정**: 의미가 '챙겼는지'). 상태 줄: 체크면 `CheckCircle` + `essentials_have_yes`(SuccessText), **아직이면 아이콘 없이 `essentials_have_no` 글자만**(InkSecondary — `RadioButtonUnchecked` 아이콘과 Checkbox가 서로 다른 두 컨트롤처럼 보이지 않게).
  - 카드 모양: 아직 = 흰 정보 카드(그림자, 3.5절). **체크됨 = 상태 카드 규칙**(그림자 없음 + `SuccessBg` 바탕 + 왼쪽 4dp `SuccessText` 막대), 바탕 전환은 `animateColorAsState`. 두 규칙을 섞지 않는다.
  - 그 아래 규정 `StatusTag`(carry_on_only → Caution, `essentials_badge_carry_on`), `reasonKo`(bodyMedium — 60자 넘으면 첫 문장 + `ExpandableDetail`, 원칙 6), 링크 = `SecondaryButton(link.labelKo, icon OpenInNew, fillWidth = false)` + 제휴면 `StatusChip(essentials_affiliate_label)`, `SourceFooter`(이름 없으면 fallback).
- TalkBack: 토글 줄 semantics = **이름(nameKo) + `stateDescription`(`essentials_have_yes`/`essentials_have_no`)** + role Checkbox. 기존 `essentials_have`(있어요)는 쓰지 않고 2단계에서 사용처가 0이면 지운다(테스트가 찾지 않음).
- 유지: `essentials_disclosure`, `essentials_progress`, `essentials_for_trip`, `essentials_badge_carry_on`, `essentials_affiliate_label`(**정확히 1개**), `여권`(nameKo 단독 Text — 다른 곳에 `여권` 단독 Text를 만들지 말 것), 링크 라벨(`공식 비교 사이트 열기`).
- 신규: `essentials_have_yes`(챙겼어요), `essentials_have_no`(아직이에요), `essentials_progress_stat`(%1$d / %2$d).

### 17 입국 카드 확인 — `form/FormConfirmScreen.kt`
- 머리: 제목 `form_confirm_title`(heading) + 부제(AppScreen 그대로).
- 스크롤 이동을 위해 화면은 `AppScreen(state = listState)`와 `rememberKeyIndex()`를 쓴다(4.1절). **직접 입력 칸은 칸마다 lazy item(`field-<key>`)으로 나눈다**(지금은 모두 `individual` item 하나라서 화면 밖 칸에 FocusRequester를 쓸 수 없음).
- 순서: ① `NoticeBanner(guide_not_affiliated, Policy)`(첫 정보 항목) ② compact `SecurityBanner`(6장 머리말 공통 보안) ③ **`현지어 크게` = `SelectChip(selected = localLarge, singleChoice = false, leadingIcon = Translate, label = form_local_large)`** — 켬·끔 상태가 있는 토글이므로 `SecondaryButton`으로 바꾸지 않는다. AppScreen에 `제목 아래 줄` 슬롯이 없으므로 이 위치(배너 다음 item)에 둔다 ④ 자동 값: `form_from_documents` 제목 아래 **출처별 그룹**(5.7절: 여권 `Badge` / 비행 `AM.AirplaneTicket` / 숙소 `Hotel` / 내가 적음 `EditNote`, 그룹 안 순서는 레시피 순서): 그룹 헤더 `Row { IconBadge ; Text(form_origin_passport 등 — 기존 문자열 단독 Text) ; StatusTag(form_group_count, Info) }`, 행 = `KeyValueRow`(4.10절, Lists.kt) — 행마다 붙던 출처 칩 삭제 ⑤ 직접 입력 = **`CardNewsCard(style = NewsStyle.SurfaceCaution, icon EditNote, title form_choose_yourself, trailing = { StatusTag(form_missing_count, Required) })`**(흰 바탕 + Caution 막대만 — 0단계에서 `SurfaceCaution`과 `trailing` 추가).
  - 텍스트 칸: `OutlinedTextField(label = { Text(f.labels.ko) }, supportingText = { Text(listOfNotNull(f.hintKo, f.labels.en, f.labels.local).joinToString(" · "), style = if (localLarge) headlineMedium else bodySmall) }, isError = 필수이고 비었음, trailingIcon = ErrorOutline(설명 form_field_required))`. 따로 그리던 `FieldLabel`은 이 칸에서 제거(중복 읽기 방지)하되, **`localLarge`가 켜지면 supportingText가 headlineMedium으로 커져** 영어·현지어 라벨을 키우던 기능을 유지한다.
  - 선택형(여행 목적·숙소 종류 등): **폭 전체 라디오 행** `Row(selectable(role = RadioButton), minTouch) { Icon(IconKeys.option(value)) — 매핑 없는 값은 아이콘 없음 ; Text("ko · en · local" — 기존 조합 그대로, localLarge면 현지어 부분 확대) ; RadioButton(onClick = null) }`, 그룹 부모 `selectableGroup()`.
- ⑥ `form_labels_unreviewed`(bodySmall InkTertiary) ⑦ `form_confirm_notice`를 `NoticeBanner(Notice, TouchApp)`로 버튼 바로 위 ⑧ 빈칸이 있으면: `StatusTag(form_missing_count)` + 빈칸 이름 태그 `FlowRow` — **태그는 누를 수 있으므로 `StatusTag`가 아니라 `AssistChip`(minTouch 48/56, `Role.Button`, `onClickLabel = form_go_field_cd`(%1$s 칸으로 가기))**, 누르면 `scrollToKey("field-<key>")` 후 그 칸 `requestFocus()` + `SecondaryButton(form_go_first_missing, icon AM.NavigateNext)`. `form_need_required` 문장은 `liveRegion = Polite`로 두되 **빈칸 개수가 바뀔 때만** 다시 알린다 ⑨ `PrimaryButton(form_confirm_yes)`(빈칸 있으면 비활성 — D9) ⑩ `SecondaryButton(form_confirm_fix / form_fix_done, icon Edit)` ⑪ `QuietButton(form_manual_mode, icon OpenInNew)`.
- `KeyValueRow`는 17(E)과 24·25(F)가 함께 쓰므로 0단계에서 `Lists.kt`에 만든다(4.10절). 값을 오른쪽 좁은 칸에 두지 않아 200%에서도 안 겹침.
- `SecureScreen()` 호출 위치·`form_killed`·`wallet_unlock`·`form_need_passport` 흐름 유지.
- 같은 E 묶음의 `AutofillScreen.kt`(핵심 흐름)·`ManualModeScreen.kt`: 공통 치환표 + 첫 항목 `NoticeBanner(guide_not_affiliated)` + 그다음 compact `SecurityBanner`. `AutofillScreen`의 `heightIn(min = 48.dp)`도 `minTouch()`로(2단계 0건 기준).
- 유지: `form_confirm_title`, `form_from_documents`, `ERIKSSON`/`KE651`(값 Text), `Family Name · นามสกุล`(subLabel 단독 Text), `form_origin_passport`, `form_origin_flight`(그룹 헤더 단독 Text), `관광 · Tourism · ท่องเที่ยว`(라디오 행 단독 Text, 클릭하면 선택), `form_confirm_yes`(비활성/활성 동작), `form_local_large`(토글), `form_killed`, `wallet_unlock`, `form_need_passport`, `wallet_passport_add`, `form_need_required`는 FlowRow 대체 시 **리소스는 두고 TalkBack용 liveRegion 문장으로 유지**.
- 신규: `form_group_count`(%1$d칸), `form_missing_count`(빈칸 %1$d개 남았어요), `form_go_first_missing`(첫 빈칸으로 가기), `form_field_required`(꼭 채워요), `form_go_field_cd`(%1$s 칸으로 가기).

### 18 쇼핑 리스트 — `pack/ShoppingScreen.kt`
- 머리: 제목 `shopping_title` + 부제 `shopping_subtitle_v2` + 분류 `SelectChip`(Apps/Restaurant/Redeem/ShoppingBasket; 품목 없는 분류 칩은 안 보임 — `noDailyChipWhenNoDailyItems`).
- 품목 `ShopItemCard`: 머리 `Row { IconBadge(분류, 64dp 대신 iconBadge) ; Column { nameKo(titleLarge) ; names.local(bodyMedium InkSecondary) } }` → `ImportVerdictBadge` + importNote(**bodyMedium**, 배지 색 글자 — 위계 역전 해결) → whyKo(bodyMedium, **전체 표시** — 줄 수로 자르지 않음. 60자 넘으면 첫 문장 + `ExpandableDetail`, 원칙 6) → `IconBullet(Place, shopping_where)`(파는 곳 `whereKo`가 있을 때) → `FlowRow { SecondaryButton(shopping_add ↔ shopping_in_cart, AddShoppingCart ↔ CheckCircle, fillWidth=false) ; SecondaryButton(shopping_show_staff, Translate, fillWidth=false) }` → **`SourceList(품목 source, importSource)`**(반입 판정의 근거 출처까지, 이름 fallback). 버튼 TalkBack: `onClickLabel`/`stateDescription`에 상품명(`shopping_add_cd` %1$s 담기) — **보이는 글자는 `담기` 그대로**(테스트).
- 정렬: 분류 필터 뒤 목록은 팩 순서 유지(반입 불가를 따로 묶는 것은 13에서만).
- 분류 칩은 단일 선택 → `SelectChip(singleChoice = true)`(Role.RadioButton + `selectableGroup()`).
- 맨 아래 `ReturnCheckCard`. 전체 화면(직원에게 보여주기)은 `localLarge`(행간 1.5배) + en/ko 역할 스타일.
- 빈 상태(품목 없음): `TopicCard(comingSoon)` → 공통 치환표(`ComingSoonGroup` 또는 Neutral `EmptyState(ShoppingBag)`).
- 유지: `import_allowed/caution/prohibited`(각 존재), `shopping_in_cart`, `shopping_cat_souvenir`, `shopping_add`(필터 후 단일), `shopping_show_staff`, 현지어 이름은 **카드에 한 번만**(`โลคอล-c` 카드 + 전체 화면 = 2개), `Name-c`(전체 화면).
- 신규: `shopping_add_cd`(%1$s 담기), `shopping_show_staff_cd`(%1$s 직원에게 보여주기).

### 19 이동하기 — `transport/TransportScreen.kt`
- ① 가는 곳 `CardNewsCard(icon Place, eyebrow move_destination, title 장소 이름)` + 현지어 주소(`localMedium`) + `StatusTag(move_place_saved, Allowed)` + `SecondaryButton(move_place_change, fillWidth=false)`. **장소 추가 편집 상태와 여러 장소 선택은 지금 기능 그대로 유지**하고 선택은 `SelectChip(singleChoice = true)` ② 기사님께 보여주기 Navy 카드 유지(좋은 화면) — eyebrow 앞 `Hail` 아이콘(White85), 현지어 문장 `localMedium`(28.sp 하드코딩 제거, 행간 1.5배), 버튼 `SecondaryButton(onDark = true, icon Fullscreen)` ③ 차 부르기 = `ListGroup(title)` 안 앱마다 `ListRow(icon LocalTaxi/Map, title 앱 이름, body = 지금의 상태 라벨(`move_ride_install` / `move_ride_open_with_dest` / `move_ride_open_copy`), trailing External, onClick)` — 세 가지 상태(설치 안 됨 / 목적지 넣어 열기 / 열고 주소 복사)를 그대로 보인다. TalkBack 이름도 상태 라벨을 포함하고, `transport_get_app`(%1$s 받기)은 **설치 안 된 경우의 행 이름에만** 쓴다 ④ 지하철·버스 `CardNewsCard(icon DirectionsSubway)` + `PrimaryButton(기존, icon Map)`.
- 유지: 기존 교통 문자열(`move_ride_*` 포함), 전체 화면 동작.
- 신규: `transport_get_app`(%1$s 받기).

### 20 도움 — `pack/PackScreens.kt`
- 순서(D6): ① 제목 `help_title` + `StatusChip(help_offline_badge, icon OfflinePin)` ② 나라 `SelectChip(singleChoice = true)`(avatar = 24dp 원형 나라 사진, 장식) ③ **대표 긴급 번호**: `pack.emergency.first()`를 폭 전체 `EmergencyCallTile(large)` + **바로 아래 `SourceFooter`**(긴급 번호의 출처·최종 확인 — 출처가 훨씬 아래 ⑦에만 있지 않게) + `QuietButton(help_more_numbers)`(누르면 `scrollToKey("emergency")`) ④ 선택 문장 Navy 카드: 배지 `help_unreviewed`(Caution 톤 StatusTag — 자체 바탕이 있어 Navy 위 허용, Update 아이콘), 현지어 `localMedium`(행간 1.5배), 로마자(White80), 한국어 titleMedium(Surface), 영어(White80), 버튼 `SecondaryButton(onDark = true, Fullscreen, help_full_screen)`·`SecondaryButton(onDark = true, VolumeUp, help_play_sound)`, `help_no_tts` 안내(White80) ⑤ `SectionHeader(help_phrases_title, icon Translate)` + `PhraseTile` **항상 1열**(팩 문구는 앱이 길이를 통제할 수 없음, 2열 칸에서 아이콘을 빼면 글 폭이 약 98dp뿐): `Surface(shapes.medium, heightIn(min = 72))` 안 `Row { Icon(IconKeys.phrase(id), 28) ; Text(p.ko, labelLarge, 왼쪽 정렬, lineBreak Heading, weight 1f) }`, 선택된 타일 = AccentSoft + 2dp Accent 테두리 + `CheckCircle`, `selectable(role = RadioButton)` + 부모 `selectableGroup()`. 누르면 문장 카드로 `scrollToKey("phrase-card")` + 카드에 `liveRegion = Polite` ⑥ `SectionHeader(help_emergency_title, icon Sos, tone Help)` + 나머지 긴급 번호 `EmergencyCallTile` 그리드(번호 길이 규칙 아래) + `SourceFooter` ⑦ 대사관 `CardNewsCard(icon AccountBalance, title help_embassy)` + 이름·주소 + `EmergencyCallTile`(대표·근무시간 외) + 출처 ⑧ `SectionHeader(help_procedures_title)` + 절차 `CardNewsCard(icon ReportProblem, title proc.titleKo)` + `StepList(numbered)` + 출처 ⑨ 어느 나라에서나 `CardNewsCard(icon SupportAgent, help_common_title, sources = 영사콜센터 항목의 출처를 명시해 넘김)` + `EmergencyCallTile`(영사콜센터) — 지금 화면의 `출처 외교부`를 잃지 않는다. 공통 항목 출처 이름은 `commonSourceName` 버그 수정 후의 값(4.5절).
- `EmergencyCallTile(label, number, icon, onCall, modifier, note: String? = null, large: Boolean = false)`:
  - 기본: `Surface(onClick, shapes.medium, HelpSoft 바탕 + 왼쪽 4dp Help 막대, heightIn(min = tileMinHeight))` → `Row { IconBadge(icon, Help) ; Spacer(weight) ; Icon(Call, Help) }` → 번호 `stat`(tnum, Ink) → 라벨 bodyMedium → note bodySmall.
  - `large`: Navy 채움, **onDark 내용 세트만** — 번호·라벨 Surface, note White80, 배지 `BadgeTone.OnDark`, **Call 아이콘 Surface**(Help on Navy 2.74 금지), 4dp 막대 없음, note가 있으면 `StatusTag`(자체 바탕).
  - **번호 길이 규칙**: `number.length > 6`이면 그리드에서도 폭 전체(1열) 타일, `> 8`이면 `statSmall`. 2열에는 `1155`·`191` 같은 짧은 번호만. 번호 글자에는 보이지 않는 문자를 넣지 않는다(테스트가 번호를 글자 그대로 찾음, 3.2절).
  - TalkBack: `semantics(mergeDescendants = true) { contentDescription = "${stringResource(R.string.help_call, label)} $number" + (note?.let { ", $it" } ?: ""); role = Button }` — **기존 CallButton 설명 형식 그대로**에 보이는 note를 뒤에 붙인다(병합 노드에 contentDescription을 넣으면 병합된 Text가 읽히지 않으므로 `근무시간 외 긴급`·`통화료가 들어요`가 빠지지 않게). 자동 발신 없음(다이얼만).
- 나라 없음 상태(갤러리 밖): `EmptyState(SupportAgent, 기존 문구)` + 나라 선택(공통 치환표).
- 유지: `help_title`, `help_offline_badge`, 첫 문장 현지어(`ห้องน้ำอยู่ที่ไหน`, 851dp 첫 화면 안 — 1열 PhraseTile로 바뀌어도 선택 문장 카드는 ④로 문장 타일보다 위), `경찰을 불러 주세요`(타일 단독 Text, 클릭 시 카드 갱신), `กรุณาเรียกตำรวจ`, `1155`, `+66-81-914-5803`, `여권을 잃어버렸어요`, `+82-2-3210-0404`(번호 단독 Text).
- 신규: `help_more_numbers`(다른 긴급 번호 보기).

### 21 입국 때 보여 주기(잠김) — `present/PresentScreens.kt`
- 제목 옆 `StatusChip(present_offline, icon AirplanemodeActive, Success)` 유지. 맨 위 compact `SecurityBanner`(6장 공통). 잠김: `LockedState(icon = QrCode2, badgeIcon = Lock, title wallet_locked_title, body wallet_locked_body, button wallet_unlock)`.
- 열린 상태(0단계에서 Gallery `present-unlocked`(문서 1개)로 추가): QR 카드는 기능 그대로, 사람 선택은 `SelectChip(singleChoice = true)`, **`present_brightness`는 켬·끔 토글이므로 `ListRow(trailing = Switch)`**(버튼으로 바꾸지 않음), 그 밖의 동작은 `SecondaryButton(icon)`, `present_delete`는 `DangerButton` + `DestructiveConfirm(present_delete_confirm_title, present_delete_confirm_body, secure = true)`.
- 유지: `present_title`, `wallet_unlock`, `present_offline`.

### 22 설정 — `settings/SettingsScreen.kt`
- ① `SecurityBanner`(= `LocalOnlyBanner` 재스타일: Navy, 원형 Lock 배지, 제목 `settings_local_only_title`, 본문 — **맨 위 유지**) ② `ListGroup(settings_group_myinfo)`: `ListRow(Badge, settings_myinfo_open, body settings_myinfo_body, Chevron)`, `ListRow(FamilyRestroom, wallet_companions_title, body settings_family_mode_desc, Chevron)` ③ `ListGroup(settings_group_display)`: `ListRow(TextIncrease, settings_easy_mode, body settings_easy_mode_desc + 오른쪽 미리보기 '가/가'(`clearAndSetSemantics {}` — TalkBack 잡음 방지), Switch)`, `ListRow(ChildCare, settings_child_mode, Switch)` ④ `ListGroup(settings_group_data)`: `ListRow(Wifi, explore_wifi_only, Switch)` ⑤ `ListGroup(settings_group_about)`: `ListRow(PrivacyTip, settings_privacy, body, onClick = 처리방침)`, `ListRow(Policy, settings_disclaimer, body settings_disclaimer_body, None)`(회색 비활성 카드 → 정상 행), `ListRow(PhotoLibrary, settings_photos, Chevron)`, `ListRow(Info, settings_about, body settings_version)`.
- 유지: `settings_title`(heading, 뒤로 없음), `settings_easy_mode`(Switch 행 이름), `settings_myinfo_open`(누르면 지갑 — **320×470에서 스크롤 없이 클릭되도록 `SecurityBanner` 바로 아래 첫 행**, `ListGroup` 제목은 그 위 한 줄만), `settings_local_only_title`.
- 사진 출처 행: D1 채택 시 `settings_credits`(사진·글꼴 출처).
- 신규: `settings_group_myinfo`(내 정보), `settings_group_display`(화면·사용), `settings_group_data`(데이터), `settings_group_about`(안내·출처).

### 23 내 정보(잠김) / 24 내 정보(열림) — `wallet/WalletScreen.kt`
- 공통: `SecurityBanner` 맨 위(FLAG_SECURE 그대로). `준비 중` 카드(`wallet_profile_title`, `wallet_documents_title`) → 화면 맨 아래 `ComingSoonGroup`. `같이 가는 사람` 카드 중복 제거 → `ListRow(FamilyRestroom, wallet_companions_title, body, Chevron)` 1개.
- 23: `LockedState(wallet_locked_title, wallet_locked_body, wallet_unlock)`. `wallet_no_lock_*`, `wallet_key_lost` + `wallet_reset`(→ `DangerButton` + `DestructiveConfirm(wallet_reset_confirm_title, wallet_reset_confirm_body, secure = true)`) 흐름 유지. 실패 상태(NeedsAuth/Corrupted, 갤러리 밖)는 공통 치환표 — 0단계에서 Gallery `wallet-key-lost` 추가.
- 24: `PassportCard`(wallet 패키지 private): `Card(shapes.large, containerColor = Color.Transparent, Brush.verticalGradient(Navy → AccentDeep))`, padding 20 → `Row { Icon(Badge, Gold) ; Text(wallet_passport_card_label 대신 신규 wallet_passport_eyebrow "PASSPORT · 여권", labelMedium Gold) }`(Gold는 Navy·AccentDeep 위에서만) → 마스킹 값 3개(`wallet_passport_name/number/expiry` 라벨 White80 bodySmall + 값 titleLarge Surface tnum, 기본 2열, 1열 규칙 동일 — `KeyValueRow`의 onDark 색 변형) → `StatusTag(wallet_passport_verified, Verified)` 또는 `wallet_passport_manual`(자체 바탕) → 만료 임박·만료면 `NoticeBanner(Caution/Danger, EventBusy)` → 행동 `SecondaryButton(onDark = true, wallet_passport_show/hide, icon Visibility)`(투명 + 흰 테두리). **`DangerButton(wallet_passport_delete)`은 Navy 카드 밖, 카드 바로 아래 별도 줄 오른쪽에 일반 모양으로** 둔다(D18 — Navy 위 DangerText 2.26:1, 흰 테두리 변형은 만들지 않음) + `DestructiveConfirm(today_destroy_title, today_destroy_body, secure = true)`.
- 예약 서류: `SectionHeader(wallet_bookings_title, icon Description)` + `BookingCard` = `CardNewsCard(icon AirplaneTicket/Hotel/Description, eyebrow 종류, title)` + 편명·예약번호 `KeyValueRow` + 날짜 `FlightTakeoff → FlightLand` 행 + `DangerButton(wallet_booking_delete)` + `DestructiveConfirm(booking_delete_confirm_title, booking_delete_confirm_body, secure = true)` → `SecondaryButton(wallet_booking_add, icon Add)`.
- 자동 삭제 스위치 → `ListRow(AutoDelete, wallet_auto_destroy, body, Switch)`. `내 정보 잠그기` → `QuietButton(wallet_lock, icon Lock)`.
- 유지: `wallet_title`(heading, 서브 화면 뒤로 버튼 — `minTouchSize()`), `wallet_unlock`, `wallet_reset`, `wallet_passport_verified`, `wallet_passport_show`, `wallet_passport_expiring`, `wallet_passport_expired`, `wallet_passport_add`, `wallet_no_lock_title`, `wallet_locked_title`, `wallet_key_lost`, `ERIKSSON`·`L898902C3`(자세히 보기 시), 마스킹 규칙.
- 신규: `wallet_passport_eyebrow`, `booking_delete_confirm_title`(이 예약 서류를 지울까요?), `booking_delete_confirm_body`(지운 서류는 되돌릴 수 없어요.), `wallet_reset_confirm_title`, `wallet_reset_confirm_body`, `action_cancel_keep`(그만두기).

### 25 여권 값 확인 — `wallet/PassportScreens.kt`
- 여권 흐름 전체(소개 PassportIntro·촬영 Scan·직접 입력 Manual·값 확인)의 맨 위에 compact `SecurityBanner`(6장 공통). 0단계에서 Gallery에 `passport-intro` 추가.
- `PassportStepper`(칩 3개) → 누를 수 없는 아이콘 스텝퍼(PhotoCamera → Nfc → AM.FactCheck, 연결선, 완료 Check, 현재 채움) **+ 아이콘 아래 단계 이름 글자 유지**(`passport_step_scan`/`passport_step_chip`/`passport_step_confirm` — 아이콘만 남기면 보이는 단계 이름이 사라져 원칙 8 위반, labelMedium, 줄바꿈 허용) + `passport_steps_desc` 한 문장 semantics 유지.
- 값 목록 = `KeyValueRow`(badge 슬롯) + 확인 배지 `StatusTag(passport_check_ok, Verified)` / `passport_check_fail`(Caution).
- **경고는 목록 위**: `passport_check_warning` → `NoticeBanner(Caution)`, 만료 → `NoticeBanner(Danger, EventBusy, wallet_passport_expired)`.
- NFC 비활성 버튼 → 버튼 제거, 목록 아래 `ComingSoonGroup(Nfc, passport_chip_soon)`(문자열 유지, 누를 수 없음, **항목 노드에 `disabled()` semantics** — 4.16절).
- 버튼: `PrimaryButton(passport_save, icon Check)` → `SecondaryButton(passport_rescan, PhotoCamera)` → `QuietButton(passport_use_manual)`.
- `SecureScreen()` 유지.
- 유지(`WalletUiTest.confirmScreenSavesVerifiedPassport` 등): `passport_save`, `passport_check_ok/fail/warning`, `passport_chip_soon` **노드의 Disabled semantics**(`assertIsNotEnabled()`), **`allChecksPass`가 아니면 `passport_save` 버튼을 아예 그리지 않음**(노드 0개), **`passport_check_ok` 정확히 3개 / `passport_check_fail` 정확히 1개**(검증 실패 픽스처) — 배지를 다른 곳에 중복으로 그리지 않는다.

### 26 예약 서류 추가(검토) — `wallet/BookingImportScreen.kt`
- 맨 위 compact `SecurityBanner`(6장 공통).
- 종류 = `TileGrid` 3칸 **`SelectTile`**(4.16절 — 세로 아이콘 + 라벨, AM.AirplaneTicket/Hotel/Description, 선택 시 Accent 채움 + Check, `Role.RadioButton` + `selectableGroup()`). 칸 폭이 약 109dp라 `ChoiceCard`형 가로 Row는 들어가지 않는다. 1열이면 가로 배치.
- 입력칸 `OutlinedTextField(leadingIcon)`: 이름 `Description`, 예약 번호 `ConfirmationNumber`, 편명 `AM.AirplaneTicket`, 체크인·아웃 `CalendarMonth`.
- `찾은 날짜` → `IconBullet(EventAvailable, booking_field_dates …)`.
- 유지: `booking_save`, `ABC123`(필드 값).

### 27 같이 가는 사람 — `present/PresentScreens.kt`(CompanionsContent)
- 맨 위 compact `SecurityBanner`(6장 공통).
- 사람이 없으면 `EmptyState(FamilyRestroom, 새 문구 companion_empty_title, 기존 부제)`, 있으면 이니셜 원형 아바타(AccentSoft, **글자 크기에 맞춰 커지는 원** — 4.8절 번호 원과 같은 규칙) + 이름 `ListRow` + **사람마다 `companion_passport_add`(여권 등록) 버튼 또는 `companion_passport_done`(여권 등록됨) 표시 유지**(기능 손실 금지) + `DangerButton(companion_delete)` + `DestructiveConfirm(companion_delete_confirm_title, companion_delete_confirm_body, secure = true)` — 이름은 대화상자에 넣지 않는다.
- 추가 폼 = 흰 `CardNewsCard(icon PersonAdd, title companion_add)`(노란 톤 제거) → `OutlinedTextField(label = companion_label)` → 동의 행 `Row(toggleable(role = Checkbox), minTouch) { Checkbox(onCheckedChange = null) ; Text(companion_consent_yes) }` + 위 질문 `companion_consent` → `PrimaryButton(companion_add, icon PersonAdd)`(비활성 규칙 유지) + 비활성일 때 이유 `companion_add_hint`.
- 유지: `companion_add`(제목 1개 + 버튼 1개 = **정확히 2개**, 버튼이 마지막), `companion_label`, `companion_consent`, `companion_consent_yes`, `companion_passport_add`, `companion_passport_done`.
- 신규: `companion_empty_title`(보호자 폰 하나로 가족 서류를 챙겨요), `companion_add_hint`(이름을 적고 동의에 체크하면 추가할 수 있어요), `companion_delete_confirm_title`, `companion_delete_confirm_body`.

### 28 사진 출처 — `settings/SettingsScreen.kt`(PhotoCreditsContent)
- 행마다 왼쪽 72dp 썸네일(`rememberThumbnail(Photos.byId(credit.id), 72)`, 모서리 12, **장식 — `contentDescription = null`**: 옆에 보이는 제목과 이중으로 읽히지 않게), 오른쪽 제목(titleMedium)·찍은 사람·`StatusTag(license, Info)`, `LinkRow(원본 보기)`. credit id(`home`, `th`, `airport` …) → drawable 매핑은 0단계에서 `Photos.byId(id)`로 추가.
- 화면 안내는 `photo_credits_body_v2`(변경 사항: 잘라내고 크기를 줄이고, 어둡게 덮고, 일부는 둥글게 자름 — 3.7절 7번), `photo_credits.json`의 `changes`도 같은 뜻으로.
- Pretendard 채택 시 `글꼴 출처`(Pretendard · SIL OFL 1.1) 항목 + `OFL.txt` 전문 보기(`LinkRow`, 앱 안 화면 — 새 라우트 대신 같은 화면의 펼침 `ExpandableDetail`) 추가, 설정의 행 이름을 `사진·글꼴 출처`로(신규 `settings_credits`).
- 테스트: `photo_credits.json`의 모든 항목이 화면에 나오는지.

### 공통 틀 — `components/AppScreen.kt`, `nav/BottomTabs.kt`
- `AppScreen`: 기본 제목 줄에 선택 `icon: ImageVector? = null` 인자(`IconBadge` + headlineMedium, heading 유지), **`state: LazyListState = rememberLazyListState()` 인자 추가**(0단계 — 화면이 `scrollToKey`를 쓰게, 4.1절), AppScreen이 넣는 header·easy-actions item도 `KeyIndex`에 기록. 뒤로 버튼 `minTouchSize()`(지금 `sizeIn(48)`). `contentPadding`은 dimens, 쉬운 모드 `처음으로·소리로 듣기` = tonal 64dp(4.11).
- `BottomTabs`: 선택 탭 아이콘 뒤 64×32dp 알약 인디케이터(AccentSoft, 도움 탭은 HelpSoft) + 선택 탭만 `Filled` 아이콘·Bold. 도움 탭은 비선택이어도 Help 색 유지(PRD)·굵기는 Medium으로 내림. 높이 가변, `Role.Tab`·selected 유지. **autoSize 최소: 기본 모드 10sp(기존 예외 유지), 쉬운 모드 18sp — 모자라면 줄이지 않고 2줄로 넘긴다**(D19, PRD 3.2).

---

## 7. 하지 않을 것

1. **가로 스와이프 금지**: HorizontalPager, 가로 캐러셀, `LazyRow` 스크롤 목록, 스와이프 삭제·스와이프 탭 전환, **M3 `DatePicker` 달력 모드(달 목록이 가로 스와이프 `LazyRow`)**. 모든 전환은 탭. 카드뉴스는 세로 스택·2열 그리드만.
2. **새 네트워크 라이브러리·SDK·원격 이미지 금지**. 이미지 로더·아이콘 폰트 CDN·애니메이션 라이브러리(Lottie 등) 추가 금지. 아이콘은 `material-icons-extended`, 그림은 번들 사진 9장(→8장)과 자체 벡터만. Pretendard는 번들 파일(네트워크 없음)이며 D1 승인 후에만.
3. **출처·최종 확인 줄을 숨기거나 접힘 안에 넣지 않는다.** 내부 ID를 화면에 내지 않는다. 출처 없는 정책 카드를 만들지 않는다.
4. **고지 문구를 줄이거나 옮기지 않는다**: 정부 비제휴(입국 화면 첫 항목), `제출은 직접`, 제휴 고지(목록 위), YouTube 고지, `원어민 검수 전`, `내 정보는 이 휴대폰에만`(보안 화면 맨 위). 모양만 바꾼다.
5. **보안 화면 동작 변경 금지**: `SecureScreen()`(FLAG_SECURE) 호출 위치, 마스킹, 잠금·본인 확인 흐름, `LocalOnlyBanner` 문구.
6. **정책 값을 앱에 하드코딩하지 않는다**: 800달러·2L·1,000만 원·여행경보 단계 같은 값은 팩 구조 필드가 생기기 전까지 타일로 만들지 않는다(D11). 팩 스키마·서명·ARIA 설정은 이번에 건드리지 않는다.
7. **고정 높이·고정 sp·`maxLines`로 자르기·말줄임표·9~10sp autoSize 축소 금지**(하단 탭의 기존 10sp 최소 autoSize는 **기본 모드에서만** 예외로 유지, 쉬운 모드는 최소 18sp + 2줄 — D19). 200% 글자에서 겹치거나 잘리는 레이아웃 금지. 글자를 품는 고정 크기 원·상자 금지(4.8절).
8. **색만으로 상태를 전달하지 않는다**(아이콘+글자 병기). 파괴적 동작에 Accent 채움 금지. 어두운 채움 위에 onDark 내용 세트 밖의 색 금지(D18).
9. **테스트가 의존하는 보이는 문자열의 값·노드 구조를 바꾸지 않는다**(6장 '유지' 목록). 예외: D17 번호 접두 삭제, `source_footer` NBSP·WORD JOINER(3.2절). 새 문구는 새 키. 테스트가 찾는 Text를 `clearAndSetSemantics`로 트리에서 지우지 않는다.
10. **새 화면·새 라우트는 만들지 않는다**(홈 바로가기 등은 기존 목적지로). 새 ViewModel 상태 저장(체크리스트 저장 등)도 이번 범위 밖.
11. **다크 모드·애니메이션 과잉 금지**: 색 전환(`animateColorAsState`)·펼침(`AnimatedVisibility`) 정도만. 반복 애니메이션 없음.
12. **Play 스토어 업로드·스토어 등록정보 변경·출시 트랙 변경을 하지 않는다** — 사용자 컨펌 후 별도 진행.
13. 실제 정부 사이트 제출, 가짜 데이터 제출, 실제 여권 이미지 사용 금지(작업 규칙 1·4) — 캡처는 ICAO 표본 값만.

---

## 8. 구현 순서

원칙: **기반(토큰·부품·문자열)을 한 사람이 먼저 끝내고**, 화면 묶음은 **파일이 겹치지 않게** 병렬로 한다. 단계마다 `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`(CLAUDE.md 환경 변수) 통과가 완료 조건. 모든 변경은 `claude/design-v2-*` 브랜치 → PR(main 보호).

**lint 기준(전 단계 공통)**: **오류 0, `UnusedResources`를 뺀 새 경고 0.** 지금 lint 보고서(`app/build/reports/lint-results-debug.xml`)의 54건(그중 `UnusedResources` 41건)이 기준선이다. 0단계는 아직 안 쓰는 신규 문자열 약 60개를 넣고, 1단계는 옛 키(`home_return_photo`, `home_essentials_open`, `prepare_items_open`, `country_help_title/body`, `country_videos_open/body`, `settings_photos_open`, `essentials_have`, `shopping_subtitle` 등)를 쓰지 않게 만들므로 새 `UnusedResources` 경고는 피할 수 없다. 2단계에서 사용처가 0인 옛 키를 지운다. `Modifier.minTouch()`는 `Modifier.Node`로(ComposableModifierFactory 경고 회피), 화면 폭은 `LocalWindowInfo.current.containerSize`로(ConfigurationScreenWidthHeight 경고 회피) — 4.1절.

### 0단계 — 기반 (단일 작업자, 다른 단계보다 먼저)
- `ui/theme/Tokens.kt`: 3.1절 추가 토큰, `BadgeTone`용 값(`OnDark` 포함).
- `ui/theme/Theme.kt`: ColorScheme 역할 전부, Shapes, `typography(easy)` 역할 추가·조정, `ReadyPortTypeExtras`(stat·statSmall·localLarge·localMedium), `ReadyPortLineBreak.Body`, `ReadyPortDimens` 확장(3.4절).
- `res/values-v29/themes.xml`: `forceDarkAllowed=false`.
- `ui/components/*`: 4.0절 파일 전부(`Layout`, `IconBadge`, `IconKeys`, `CardNews`, `Banners`, `Sources`, `Buttons`, `Tiles`, `Lists`, `Controls`, `Status`, `Photos v2`, `Journey`, `ReturnCheck`). 비평 반영으로 0단계에 함께 넣을 것: `SecondaryButton(onDark)`, `BadgeTone.OnDark`, `RowTrailing.Custom`, `KeyValueRow`, `LockedState(badgeIcon)`, `SelectTile`, `SelectChip(singleChoice)`, `NewsStyle.SurfaceCaution` + `CardNewsCard(trailing)`, `DestructiveConfirm(secure)`, `SecurityBanner(compact)`, `Fact.source`, `feeIcon`, `ChipSpec`, `Photos.byId`, `KeyIndex`/`scrollToKey`, `minTouchSize()`.
- **이동(4.0절 이동 규칙)**: `PhotoTopCard`(home) → `PhotoHeaderCard`, `ImportTag`·`ReturnCheckCard`·`importLabel`·`importColors`(pack/ShoppingScreen) → components, `displayDate`(pack/PackScreens) → `Sources.kt`, `LocalOnlyBanner`(settings) → `SecurityBanner`, `OfflineBanner`(ReadyPortRoot private) → `Banners.kt`. **옛 위치에는 `@Deprecated` 위임 함수를 남기고**, `CardTone.Notice`도 `@Deprecated`로 남긴다(2단계에서 사용처 0일 때만 삭제).
- **0단계가 import·호출을 고치는 화면 파일**(1단계 시작 전에만, 동작 변경 없음): `home/HomeScreen.kt`, `country/CountryScreen.kt`, `today/TodayScreen.kt`, `pack/ShoppingScreen.kt`, `ui/ReadyPortRoot.kt`(OfflineBanner). 같은 때 **D17의 세 곳(HomeScreen 출국 순서, TodayScreen StepsCard 2곳, CountryScreen 지도 단계)을 최소한 `StepList`로 바꾼다**(문자열 접두 삭제와 동시에 — 번호 없는 순서 목록이 머지 사이에 생기지 않게).
- `AppScreen`: `EasyActionButton`·`PrimaryButton` 이동, 선택 `icon` 인자, **`state: LazyListState` 인자**, 뒤로 버튼 `minTouchSize()`.
- `res/values/strings_design_v2.xml`: **부록 B의 신규 문자열 전부**를 한 번에 추가(병렬 단계가 strings.xml을 동시에 고치지 않게 — 이후 동결). 리소스 수정: `source_footer` NBSP 2곳(+선택 WORD JOINER), D17 번호 접두 삭제(`today_departure_step1~5`, `today_arrival_step1~5`, `explore_maps_step1~3`) + `today_departure_step1` 분리. `assets/photo_credits.json`의 `changes` 수정(3.7절 7번).
- 테스트:
  - `TokenContrastTest`(3.8절 — **`compositeOver` 합성 계산으로 수정**, 합성 쌍·nonText 쌍 추가), 신규 `OnDarkPairsTest`(금지 쌍), 신규 `SourceNamesTest`(4.5절, 앵커 없는 ID 정규식), `shortValue`·`feeIcon` 단위 테스트(4.6절).
  - `Gallery.kt` 픽스처 수정(`EssentialRow`에 실제 출처 이름, `ShoppingUi.sourceNames`·`indexSources` 채움, `TodayUi` 귀국 출처 이름) + **픽스처 새 필드 값을 미리 채움**(1단계가 공유 테스트 파일을 고치지 않게).
  - **Gallery 항목 추가**: `today-departure`, `today-wrapup`, `present-unlocked`(문서 1개), `wallet-key-lost`, `passport-intro`, `manual-mode`, `components`(부품 페이지), `photo-worst-white`(흰 단색 사진 위 `PhotoTextArea`·`PhotoChip`·사진 위 버튼 — 3.7절) → `A11yAuditTest`(기본·쉬운·200%)가 새 부품과 갤러리 밖 상태까지 점검.
  - **`A11yAuditTest` 강화**: 쉬운 모드 감사는 **누를 수 있는 모든 요소를 56dp 이상**으로 검사(지금은 48dp, PrimaryButton만 56). **`@Config(sdk = [31])` 변형 추가** — fontScale 2.0에서 기본·쉬운 모드 A11yAudit와 갤러리를 한 번 더 돌린다(API 34+는 비선형 글자 확대라 sdk 36의 200%는 S10(선형 2배)보다 덜 커지고, `LineBreak`도 36에서만 작동해 S10의 음절 단위 줄바꿈이 캡처에 안 나타남). Robolectric `android-all` sdk 31 jar가 처음 한 번 Gradle 의존성으로 받아진다.
  - **`GalleryCaptureTest`**: 높이 qualifier를 `h3600dp` → **`h6000dp` 이상**(지금 `easy/01_home.png`가 정확히 3600dp로 잘려 있음). `systemProperty("robolectric.pixelCopyRenderMode", "hardware")`를 켜고 **그림자가 캡처에 보이는지 확인**한다(소프트웨어 렌더링이면 `Modifier.shadow`가 안 그려져 테두리 없는 흰 카드가 Ground 대비 1.1:1로 경계 없이 보임). 안 보이면 3단계 컨펌 자료는 에뮬레이터·실기기 캡처로 만든다.
- **공유 테스트 파일 동결**: 0단계 뒤 `Gallery.kt`, `FakeSlots.kt`, `StoreAssetsTest.kt`, `ScreenCaptureTest.kt`, `A11yAuditTest.kt`, `GalleryCaptureTest.kt`는 **2단계 담당만** 고친다. 묶음 전용 테스트 파일(예: `ui/today/*Test.kt`)은 그 묶음이 고친다.
- 완료 기준: 기존 화면이 새 토큰으로 깨지지 않고(버튼 모서리·색 변화만) 모든 테스트 통과.

### 0b단계 — 글꼴(선택, D1 승인 후, 다른 단계와 병렬 가능)
- 운영자 승인 요청(라이선스 RFN 확인 결과 + 실측 용량 비교표, 3.2절) → 승인 → 파일 확보(다운로드는 사용자 확인 후) → 원 배포 서브셋 그대로 또는 이름 바꾼 서브셋 → `res/font/` → `Theme.kt` FontFamily → `assets/licenses/OFL.txt` + 크레딧·`settings_credits` → APK 크기 증가 보고(3.5MB 이하).

### 1단계 — 화면 묶음 (병렬, 파일 소유권 분리)

| 묶음 | 소유 파일 | 화면 |
|---|---|---|
| A 홈·첫인상 | `home/HomeScreen.kt`, `onboarding/FirstRunScreen.kt`, `nav/BottomTabs.kt` | 00, 01, 02, 하단 탭 |
| B 나라·영상 | `country/CountryScreen.kt`, `video/VideosScreen.kt` | 03, 04, 05, 06, 07, 08 |
| C 내 여행·준비 | `today/TodayScreen.kt`, `today/TodayViewModel.kt`, `trip/TripScreens.kt`, `tabs/TabScreens.kt` | 09~13, Departure·WrapUp, 14, 15 |
| D 도움·쇼핑·이동 | `pack/PackScreens.kt`, `pack/PackViewModels.kt`(`commonSourceName` 수정), `pack/ShoppingScreen.kt`, `transport/TransportScreen.kt` | 18, 19, 20 |
| E 준비물·설정·입국 카드 | `prep/EssentialsScreen.kt`, `settings/SettingsScreen.kt`, `form/FormConfirmScreen.kt`, `form/AutofillScreen.kt`, `form/ManualModeScreen.kt`, `form/FormViewModels.kt` | 16, 17, Autofill·ManualMode, 22, 28 |
| F 지갑·여권·보여 주기 | `wallet/WalletScreen.kt`, `wallet/WalletViewModels.kt`, `wallet/PassportScreens.kt`, `wallet/BookingImportScreen.kt`, `present/PresentScreens.kt` | 21, 23~27, Passport 소개·촬영·직접 입력 |

- 규칙: 공용 부품을 고쳐야 하면 직접 고치지 말고 0단계 담당에게 요청(또는 1단계 시작 전 합의된 목록만). 새 액션이 필요하면 `XxxActions`에 **기본값 있는 필드만 추가**하고 배선은 2단계에서.
- **시그니처 규칙**: data class·`*Content` 함수의 변경은 **맨 뒤에 기본값 있는 인자 추가만** 허용한다(위치 인자로 부르는 곳이 있음: `HomeTrip(…)`은 Gallery·StoreAssetsTest, `FormEntry`(7개)는 FakeSlots, `TodayContent`(7개)·`PresentContent`·`CompanionsContent`·`FormConfirmContent`의 람다 순서는 테스트가 위치로 넘김). 예: `HomeTrip(…, code: String? = null)`, `FormEntry(…, windowDays: Int? = null)`. 중간 삽입·순서 변경 금지.
- 다른 묶음 파일의 공용 심볼은 옛 위치를 지우거나 시그니처를 바꾸지 않는다(4.0절 이동 규칙). F는 `rememberDeviceAuth`·`maskName`·`maskNumber` 시그니처 유지.
- 묶음별 완료 기준: 해당 화면 테스트 + `A11yAuditTest`(기본·쉬운·200%, sdk 36·31) + `GalleryCaptureTest` 재생성, 6장 '유지' 문자열 검사 통과, lint 기준(위) 충족. B는 `VideosTest`(video 패키지), A·B·E는 `ReadyPortRootTest`(320×470 예산)도 포함.

### 2단계 — 통합 (단일 작업자)
- `ui/ReadyPortRoot.kt` 배선: `HomeActions.openHelp`, (선택) 준비 → 지갑 예약 추가, 5장 타일 목적지 확인.
- `@Deprecated` 위임 함수·`CardTone.Notice`의 사용처 0 확인 후 삭제. 사용처 0인 옛 문자열 키 삭제.
- `photo_market` 사용처 0 확인 → drawable·크레딧 제거, 사진 출처 테스트 갱신.
- 하드코딩 `heightIn(min = 48.dp)`·`sizeIn(48)`·고정 sp 전수 검색 결과 0건 확인(`form/AutofillScreen.kt` 포함).
- 전체 테스트 + lint + 에뮬레이터/실기기(S10 5G) 시각 점검(글자 200%, 쉬운 모드, TalkBack 한 바퀴, 여러 줄 태국어 18·19·20 전체 화면).

### 3단계 — 검수·컨펌 게이트 (Play 업로드 전 필수)
1. `GalleryCaptureTest` 재실행 → `app/build/gallery/` 기본·쉬운 모드 전체 + 타일 재생성(h6000dp, 그림자 확인 — 안 보이면 에뮬레이터·실기기 캡처).
2. 전문가 5인 관점 재검토(같은 기준 점수표) → 목표 평균 7.5 미만 항목 수정.
3. `StoreAssetsTest`로 Play 스크린샷·그래픽 재생성(권장 순서: 홈 → 03 입국(FactGrid) → 17 입국 카드 확인 → 20 도움 → 19 기사님 카드 → 24 내 정보 → 12 여행 중 → 쉬운 모드 비교). 캡션에 `정부 기관과 제휴하지 않은 앱이에요` 병기. 07 영상(제3자 썸네일·YouTube 상표)은 스토어 스크린샷에 쓰지 않는다.
4. 사용자에게 **변경 요약 + 전후 캡처 비교 + 스토어 스크린샷**을 보여 주고 **명시적 컨펌**을 받는다. 컨펌 전에는 Play Console 업로드·등록정보 수정·트랙 변경을 하지 않는다. 출처 문제(#1)는 "픽스처 결함 수정 + 방어적 폴백 + 도움 탭 출처 이름 버그 수정"으로 정확히 보고한다(운영 화면에서 ID가 보였다고 과장하지 않음).
5. **운영자 확인 항목**(컨펌 때 함께 받는다): D1 글꼴(라이선스·용량표), D3 모서리 — PRD 5장 공통 `모서리 14~20dp` 문구 변경, D6 PRD 5.11 도움 순서 보완, D19 쉬운 모드 28sp 초과 예외(PRD 3.2), 태국 팩 기간 문장 수정(D11, 부록 A).
6. 문서 갱신: PRD 8.4 토큰 표·글꼴 결정, PRD 5장 모서리 문구, PRD 3.2 쉬운 모드 예외, 5.11 도움 순서 보완(D6), `docs/ARCHITECTURE.md` "구현 결정 기록"에 D1~D21 요약.

---

## 부록 A — 2단계(선택) 팩 확장 제안 (이번 범위 밖, 운영자 승인 필요)

- `index.json`의 `return_facts[]`, 나라 팩 `sections[].body_ko[]`에 선택 필드 `headline_ko`(예: `800달러`), `icon`(열거형 키)을 추가하면 `귀국 전 확인`·`돈`·`여행경보`를 `StatTile`로 바꿀 수 있다. 앱은 `ignoreUnknownKeys = true`라 기존 앱과 호환. 필요한 일: `packs/schema/*` 수정 → 팩 원문 값 입력(출처·확인일 포함) → `build_packs.py` 재서명 → 호스팅 배포 → `rc-versions`. 여행경보 단계는 `level`(1~4) 구조 필드 필요.
- **출시 전 필수(운영자/ARIA, 앱 하드코딩 금지)**: 태국 팩 `forms[].window_days_including_arrival = 3`(도착일 포함 3일)과 `들어갈 때` 불릿 `도착 3일 전부터 도착하는 날까지`(4일 범위)가 서로 모순이다. 공식 안내 원문으로 어느 쪽이 맞는지 확인해 팩 원문을 고치고(출처·확인일 갱신 → 재서명 → 배포), **팩 lint**에 `window_days_including_arrival`과 같은 팩 불릿 문장의 기간 표현이 맞는지 확인하는 검사를 추가한다(다른 나라 포함). 이 검사가 통과하기 전에는 앱의 기간 타일을 그리지 않는다(D11).

## 부록 B — 신규 문자열 목록 (`res/values/strings_design_v2.xml`)

| 키 | 값 |
|---|---|
| source_official_fallback | 공식 안내 |
| action_more | 자세히 보기 |
| action_less | 접기 |
| state_expanded | 펼쳐짐 |
| state_collapsed | 접힘 |
| action_cancel_keep | 그만두기 |
| coming_soon_group | 곧 추가돼요 |
| trust_official | 공식 출처만 |
| trust_local | 폰에만 저장 |
| trust_offline | 인터넷 없이도 |
| first_run_easy_preview | 큰 글자·큰 버튼 |
| first_run_basic_preview | 기본 화면 |
| home_trip_dates | %1$s ~ %2$s |
| home_items_plug | 플러그 |
| home_items_voltage | 전압 |
| home_items_powerbank | 보조배터리 |
| help_shortcut_title | 급할 때는 도움 |
| return_topic_allowance | 면세 한도 |
| return_topic_plant | 과일·식물 |
| return_topic_livestock | 고기·축산물 |
| return_check_more | 면세 한도와 반입 금지 품목 보기 |
| today_departure_step1_detail | 국제선은 넉넉히 일찍 가는 게 좋아요. |
| country_submit_self | 마지막 제출은 직접 눌러요 |
| fact_days | %1$d일 |
| fact_label_visa_free | 비자 없이 머물러요 |
| fact_label_visa_arrival | 도착비자로 머물러요 |
| fact_label_form_fee | 입국 카드 비용 |
| fact_label_visa_fee | 비자 비용 |
| fact_label_window | 도착일 포함\n내는 기간 *(한 줄 7자 이내, 타일 복원 때 사용 — 이번 릴리스 미사용)* |
| country_travel_tools_title | 현지에서 쓰는 도구 |
| tile_phrases_emergency | 현지어와\n긴급 번호 |
| tile_videos | 여행 영상 |
| tile_maps | 오프라인 지도 |
| shopping_subtitle_v2 | 관광청 쇼핑 안내에서 골랐어요 |
| shopping_add_cd | %1$s 담기 |
| shopping_show_staff_cd | %1$s 직원에게 보여주기 |
| today_trip_dates | %1$s ~ %2$s |
| today_arrival_qr_title | 입국 심사 때 QR을 보여 주세요 |
| trip_save_edit | 저장하기 |
| trip_nights | %1$d박 %2$d일 |
| trip_delete_confirm_title | 이 여행을 지울까요? |
| trip_delete_confirm_body | 여행 날짜와 입국 카드 알림이 지워져요. 여권·예약 서류는 남아요. *(확정 — `TripViewModel.delete()` = `trips.clear()` + `cancelFormWindow`)* |
| prepare_form_eyebrow | %1$s · 도착 전에 내요 |
| essentials_have_yes | 챙겼어요 |
| essentials_have_no | 아직이에요 |
| essentials_progress_stat | %1$d / %2$d *(인자 순서 (done, total) — 기존 `essentials_progress`(total, done)와 반대이니 주의)* |
| form_group_count | %1$d칸 |
| form_missing_count | 빈칸 %1$d개 남았어요 |
| form_go_first_missing | 첫 빈칸으로 가기 |
| form_field_required | 꼭 채워요 |
| form_go_field_cd | %1$s 칸으로 가기 |
| transport_get_app | %1$s 받기 |
| help_more_numbers | 다른 긴급 번호 보기 |
| settings_group_myinfo | 내 정보 |
| settings_group_display | 화면·사용 |
| settings_group_data | 데이터 |
| settings_group_about | 안내·출처 |
| settings_credits | 사진·글꼴 출처 *(D1 채택 시)* |
| wallet_passport_eyebrow | PASSPORT · 여권 |
| booking_delete_confirm_title | 이 예약 서류를 지울까요? |
| booking_delete_confirm_body | 지운 서류는 되돌릴 수 없어요. |
| present_delete_confirm_title | 이 서류를 지울까요? |
| present_delete_confirm_body | 지우면 입국 때 이 서류를 보여 줄 수 없어요. 되돌릴 수 없어요. |
| companion_delete_confirm_title | 같이 가는 사람을 지울까요? |
| companion_delete_confirm_body | 이 사람의 입국 서류도 함께 지워져요. 되돌릴 수 없어요. *(`delete(id)`가 그 사람의 entryDocs·blob까지 지움)* |
| wallet_reset_confirm_title | 내 정보를 모두 비울까요? |
| wallet_reset_confirm_body | 여권, 예약 서류, 같이 가는 사람, 입국 서류가 이 휴대폰에서 모두 지워져요. 되돌릴 수 없어요. *(`repo.wipe()`)* |
| photo_credits_body_v2 | 모든 사진은 위키미디어 공용에서 가져와 잘라내고 크기를 줄이고, 글자가 잘 보이게 어둡게 덮었어요(일부는 둥글게 잘랐어요). |
| companion_empty_title | 보호자 폰 하나로 가족 서류를 챙겨요 |
| companion_add_hint | 이름을 적고 동의에 체크하면 추가할 수 있어요 |

리소스 **값 수정**(키 유지): `source_footer`(`최종`·`확인`·날짜 사이 두 공백을 NBSP로, 선택적으로 `확`과 `인` 사이 U+2060), `today_departure_step1`(`공항에 가요`), `today_departure_step2~5`·`today_arrival_step1~5`·`explore_maps_step1~3`(앞의 `N. ` 삭제).

키 이름 정정: 귀국 전 확인 카드의 제목·안내는 **`shopping_return_title`·`shopping_return_body`**(기존 키. `return_check_title`이라는 키는 없다).

---

## 9. 비평 반영 기록 (2026-10-01, v2.1)

두 비평(비평 1: 26개, 비평 2: 32개, 둘 다 `approve_with_changes`)의 수정 요구 58개를 모두 검토했다. 코드·팩으로 사실을 확인한 항목: `TokenContrastTest.contrast()`의 `luminance()` 사용, ID 팩 `apply.fee_ko`(`IDR 500,000 · …`), 태국 팩 기간 모순(`window_days_including_arrival = 3` vs `도착 3일 전부터…`), 태국 `forms[].source = mofa_th`(req와 같음), `TripViewModel.delete()` 범위, 동반자 삭제·`wipe()` 범위, `HelpViewModel.commonSourceName`, `shopping_return_title` 키, `VideosTest` 위치, `CardTone.Notice` 사용 파일 11개, lint 보고서 54건(UnusedResources 41). **전체 기각 0건, 부분 반영 2건(2-16, 2-27 — 2-27의 번호 안 U+2060만 기각), 대안 반영 4건(1-8, 1-15, 2-18, 2-22), 나머지는 요구대로 반영**(아래 '처리' 열). 두 비평이 겹치는 항목(대비 계산, shortValue, 비자 출처, SourceList, 날짜, sticky 헤더 등)은 하나로 합쳐 처리했다.

**처리 표기**: 반영 = 요구대로 / 대안 반영 = 비평이 제시한 대안 중 하나 또는 같은 목적의 다른 방법 / 부분 반영 = 일부만 반영하고 이유를 적음.

### 9.1 비평 1

| # | 항목 | 처리 | 반영 내용 (위치) |
|---|---|---|---|
| 1-1 | TokenContrastTest 알파 무시 | 반영 | `fg.compositeOver(bg)` 후 계산, 합성 기대값 5.37·10.85·7.26·7.93·13.42·12.17 추가 (3.1, 3.8) |
| 1-2 | 어두운 바탕 색 조합 | 반영 | `onDark 내용 세트`·금지 쌍 표, `BadgeTone.OnDark`, EmergencyCallTile(large) Call 아이콘 Surface·막대 제거, IconTile 셰브론 White80, eyebrow White85, DangerButton은 어두운 카드 밖, `OnDarkPairsTest` (D18, 3.1, 3.8, 4.7, 4.9, 6-20, 6-24) |
| 1-3 | SecondaryButton(onDark) 없음 | 반영 | `onDark: Boolean` 추가 — 투명 + 1.5dp Surface 테두리 + Surface 글자, 04·19·20·24 공통 (4.11) |
| 1-4 | 번호 원·아바타 200% 넘침 | 반영 | 원 크기를 글자가 정함(`defaultMinSize` + padding), 아바타 같은 규칙, 200% 캡처 포함 (원칙 6, 4.8, 6-27) |
| 1-5 | API 33 미만 줄바꿈·2열 폭 | 반영 | D4를 칸 폭/fontScale < 150 기준으로 변경, 2열 라벨 한 줄 7자·짧은 `tile_*` 키, PhraseTile 항상 1열, 문자열 3개 정리(`fact_label_window` 단축, `직접` 타일·`fact_label_self_submit` 삭제, `country_help_open` 대신 `tile_phrases_emergency`) (D4, 3.2, 4.1, 6-05, 6-20, 부록 B) |
| 1-6 | 감사·캡처가 sdk 36에서만 | 반영 | `@Config(sdk = [31])` fontScale 2.0 변형, 쉬운 모드 감사는 누르는 요소 전부 56dp (8장 0단계) |
| 1-7 | 뒤로·찜 IconButton 48dp 고정 | 반영 | `minTouchSize()`, `sizeIn(48)`도 교체 대상, 검정 0.35 원 유지(3.54:1) (3.4, 4.1, 6 머리말, 6-03, 공통 틀) |
| 1-8 | 17 누르는 빈칸 태그 | 대안 반영 | 누르는 태그를 `AssistChip`(minTouch, Role.Button, `form_go_field_cd`)으로, liveRegion은 개수 바뀔 때만 (4.12, 6-17) |
| 1-9 | 비자 FactGrid 출처 섞임 | 반영 | `Fact.source` + 카드 `SourceList`가 모든 타일 출처 표시, `직접` 타일 삭제 (4.6, 6-03, 6-04) |
| 1-10 | shortValue 쉼표 자르기 | 반영(비평 2와 통합) | `' · '`·첫 문장 `'. '`에서만 자르고 `,`는 금지. JP·SG 기대값은 비평 2의 `무료`를 따름(첫 문장 규칙이 정확한 값을 냄) (4.6) |
| 1-11 | 태국 기간 타일 vs 팩 문장 | 반영 | 기간 타일·칩을 이번 릴리스에서 숨김, 팩 수정·팩 lint는 운영자/ARIA 과제 (D11, 6-03, 6-15, 부록 A) |
| 1-12 | 출처·최종 확인 누락 5곳 | 반영 | ① 20 대표 번호 아래 SourceFooter ② ⑨ sources 명시 ③ 13·06에 품목+`importSource` SourceList ④ 홈 그리드 아래 SourceList ⑤ 07 `videos_terms` 문단 유지 (D6, 6-01, 6-06, 6-07, 6-13, 6-20) |
| 1-13 | e-VOA 단계 인덱스 매핑 | 반영 | 번호만 있는 StepList, `Step.selfAction`·`step_self` 삭제 (4.8, 6-04, 부록 B) |
| 1-14 | 스크림 위 24dp 구간·사진 위 글자 위치 | 반영 | PhotoTextArea 위 padding ≥ 24dp, 사진 위 글자는 PhotoTextArea·자체 바탕 위에만, 흰 사진 최악 캡처 (3.7, 4.13, 6-00, 8장) |
| 1-15 | DatePicker 가로 스와이프·고정 칸 | 대안 반영 | M3 DatePicker를 쓰지 않고 텍스트 입력 유지 + supportingText `11월 3일 (화)` (D20, 6-14, 7장 1번) |
| 1-16 | ChoiceSegments 높이·sticky·트랙 대비 | 반영 | `heightIn(min)`, 1열이면 sticky 끄고 세로 라디오 목록, 트랙 1dp LineStrong, `selectableGroup()` (D7, 4.17, 6-03) |
| 1-17 | SelectChip role·이중 초점 | 반영 | 단일 선택은 Role.RadioButton 덮어쓰기 + `selectableGroup()`, 행 안 RadioButton·Checkbox·Switch 콜백 null (4 공통 규칙, 4.17) |
| 1-18 | TalkBack 이름 4건 | 반영 | ① note를 설명 뒤에 붙임 ② 미리보기 `clearAndSetSemantics` ③ KeyValueRow 병합 ④ 스텝퍼 단계 이름 글자 유지 (4.10, 4.16, 6-20, 6-22, 6-25) |
| 1-19 | NoticeBanner vs tonal 버튼 | 반영(두 가지 모두) | tonal 버튼 1dp 테두리 + Notice 배너 흰 바탕·막대, 원칙 4에 AccentSoft 채움 규칙 (D21, 원칙 4, 4.4, 4.11) |
| 1-20 | 태국어 행간 | 반영 | `localLarge`·`localMedium` 역할, 행간 1.5배 + `LineHeightStyle(Center, Trim.None)`, sdk 31·200% 캡처 (3.2, 6-18·19·20) |
| 1-21 | 쉬운 모드 28sp 초과·하단 탭 | 반영 | PRD 3.2 예외로 운영자 승인 항목, 쉬운 모드 하단 탭 최소 18sp + 2줄 (D19, 3.2, 7장 7번, 공통 틀, 8장 3단계) |
| 1-22 | 보안 화면 배너·대화상자 | 반영(범위 확대) | 25 흐름 전체·26·27에 compact SecurityBanner, 같은 이유로 21·17·Autofill·ManualMode에도(입국 카드 화면은 비제휴 배너 다음), `DestructiveConfirm(secure)` + 본문 스크롤 + 개인정보 금지 (6 머리말, 4.11, 6-17·21·25·26·27) |
| 1-23 | SourceList 구분자 | 반영(비평 2와 통합) | 여러 출처는 `, `로 잇고 날짜는 끝에 한 번, 출처 하나는 형식 그대로 (4.5) |
| 1-24 | Pretendard RFN·OFL 전문 | 반영 | 원 배포 서브셋 그대로 또는 이름 변경, `OFL.txt` 전문 보기, 다운로드는 승인 후 (D1, 3.2, 6-28, 0b단계) |
| 1-25 | 사진 변경 사항 표시 | 반영 | `changes`·`photo_credits_body_v2`를 '어둡게 덮음·둥글게 자름' 포함으로 (3.7 7번, 6-28, 부록 B) |
| 1-26 | PRD 5장 모서리 문구 | 반영 | 운영자 확인 항목·문서 갱신 목록에 추가 (D3, 8장 3단계) |

### 9.2 비평 2

| # | 항목 | 처리 | 반영 내용 (위치) |
|---|---|---|---|
| 2-1 | ComingSoonGroup Disabled·25 테스트 조건 | 반영 | 항목 노드 `disabled()`, 25 유지 목록에 save 미표시·ok 3개·fail 1개 (D15, 4.16, 6-25) |
| 2-2 | CountryPhotoTile 칩 구조 | 반영 | `ChipSpec(icon, text)`에 `home_chip_*` 완성 문장, merge만·clearAndSet 금지 (D16, 4.13, 6-01) |
| 2-3 | 스크롤 이동에 LazyListState 필요 | 반영 | AppScreen `state` 인자, `KeyIndex`/`scrollToKey`, 17 칸별 lazy item, snapshotFlow 후 requestFocus, sticky 오프셋 (4.1, 6-17, 공통 틀) |
| 2-4 | CardTone.Notice·공용 심볼 이동 | 반영 | `@Deprecated` 위임 유지·2단계 삭제, `displayDate`·`importLabel`·`importColors`·`OfflineBanner` 이동, 0단계 import 수정 파일 목록, F 시그니처 유지 (3.1, 4.0, 8장) |
| 2-5 | 파일 소유권 빈틈 | 반영 | Autofill·FormViewModels → E, TodayViewModel → C, PackViewModels → D, WalletViewModels → F, 공유 테스트 동결, 시그니처는 맨 뒤 기본값 인자만 (8장) |
| 2-6 | 빠진 화면·상태 | 반영 | 공통 치환표, Gallery 항목 6개(+components·photo-worst-white) 추가 (6 머리말, 6-07·09·13·14·18·20·21·23·25, 8장) |
| 2-7 | VideosTest 320×470 예산·약관 문구 | 반영 | 합계 420dp 이하 규칙, `videos_open`은 클릭 노드 하나, `videos_terms` 유지, VideosTest를 07 회귀 테스트로 (6-07, 8장) |
| 2-8 | ReadyPortRootTest 예산(03·22) | 반영 | CountryHero 220dp, 340dp 미만 세그먼트 한 줄 글자, 22 `settings_myinfo_open` 첫 행 (6 머리말, 6-03, 6-22) |
| 2-9 | 17 세부 7건(a~g) | 반영 | (a) SelectChip 토글 (b) localLarge면 supportingText headlineMedium (c) `NewsStyle.SurfaceCaution` + `trailing` (d) 출처별 그룹·EditNote (e) AssistChip (f) KeyValueRow를 Lists.kt (g) 모르는 값 아이콘 없음 (4.0, 4.7, 4.10, 5.7, 6-17) |
| 2-10 | shortValue `. ` 규칙·MoneyOff | 반영 | `' · '`·`'. '` 분할, `,` 금지, `feeIcon`(정확히 `무료`만 MoneyOff), 단위 테스트 6건 (4.6, 5.3) |
| 2-11 | 비자 FactGrid 출처 | 반영(1-9와 통합) | `Fact.source`, 태국은 같은 출처라 한 줄 유지 확인 (4.6, 6-03, 6-04) |
| 2-12 | contrast 알파 | 반영(1-1과 통합) | PhotoChip·스크림 쌍도 합성 계산 (3.8) |
| 2-13 | lint '새 경고 없음' 불가능 | 반영 | 기준을 '오류 0, UnusedResources 제외 새 경고 0'으로, minTouch는 Modifier.Node, 폭은 `LocalWindowInfo` (4.1, 8장) |
| 2-14 | D17 접두 삭제 시점 | 반영 | 0단계에서 세 곳을 최소 StepList로 함께 변경 (D17, 8장 0단계) |
| 2-15 | LineBreak.Paragraph·NBSP | 반영 | 본문용 `LineBreak(HighQuality, Strict, Phrase)` 직접 정의, `source_footer` NBSP 2곳 + 선택 U+2060 (3.2, 부록 B) |
| 2-16 | 부록 B 누락 키·trip 본문 | 부분 반영 | 확인 대화상자 키 6개·`form_go_field_cd`·`tile_*`·`photo_credits_body_v2` 추가, `shopping_return_title` 정정, `trip_delete_confirm_body` 확정, `essentials_progress_stat` 인자 순서 명시. **10 타일 키와 `transport_app_body`는 만들지 않음** — 10 그리드를 없앴고(2-20), 19 행 본문은 기존 `move_ride_*`를 씀(2-30) (부록 B, 6-10, 6-19) |
| 2-17 | onDark 버튼·BadgeTone·RowTrailing·LockedState·SelectTile·밝기 토글 | 반영 | `SecondaryButton(onDark)`, `BadgeTone.OnDark`, `RowTrailing.Custom`, `LockedState(badgeIcon)`, `SelectTile`, 24 여권 지우기는 카드 밖 일반 DangerButton, `present_brightness`는 ListRow(Switch) (3.1, 4.10, 4.11, 4.16, 6-21, 6-24, 6-26) |
| 2-18 | 10 요약 그리드 중복·테스트 충돌 | 대안 반영 | 그리드 삭제, 16 바로가기가 필요하면 `TodayActions.openEssentials` 기본값 추가(이번 범위 밖) (6-10) |
| 2-19 | 원칙 6과 줄 수 자르기 충돌 | 반영 | 글자 수(60자) 기준 첫 문장 + ExpandableDetail, 말줄임 금지, 06 미리보기 whyKo 제거, 18 전체 표시, 영상 제목 maxLines 제거 (원칙 6, 6-06·07·16·18, 7장 7번) |
| 2-20 | 캡처에 그림자 안 보임·3600dp 잘림 | 반영 | `pixelCopyRenderMode=hardware`로 확인, 안 되면 실기기 캡처, h6000dp (D2, 8장 0·3단계) |
| 2-21 | Card·AlertDialog 기본 회색 | 반영 | ColorScheme 근거 정정, 모든 Card·AlertDialog `containerColor = Surface` 명시 규칙 (3.1, 4 공통 규칙, 4.7·4.10·4.11·4.16) |
| 2-22 | 날짜 달력 격자·UTC | 대안 반영 | DatePicker 자체를 쓰지 않아(D20) 격자·시간대 문제가 생기지 않음. 저장 형식 검증 유지 (6-14) |
| 2-23 | sticky 헤더 가림 | 반영(1-16과 통합) | 2열일 때만 sticky, 바탕 Ground, 오프셋에서 헤더 높이 뺌 (4.17, 6-03) |
| 2-24 | sectionGap 계산 | 반영 | Spacer = sectionGap − 2×gap(8dp) (3.4, 4.1) |
| 2-25 | 문제 #1 과장·정규식·도움 버그 | 반영 | #1을 '픽스처 결함 + 방어적 폴백 + commonSourceName 버그'로 재작성, 앵커 없는 정규식, D 묶음 수정, 컨펌 때 정확히 보고 (1.2, 4.5, 8장) |
| 2-26 | SourceList 경계 | 반영(1-23과 통합) | (4.5) |
| 2-27 | 긴 전화번호 줄바꿈 | 부분 반영 | 6자 초과 폭 전체·8자 초과 statSmall 반영. **번호 안 U+2060은 기각** — 테스트가 `+66-81-914-5803` 등을 글자 그대로 찾으므로 보이는 번호에 보이지 않는 문자를 넣지 않음 (3.2, 6-20) |
| 2-28 | 19 차 부르기 상태 손실 | 반영 | 행 본문·TalkBack에 `move_ride_*` 3상태, `transport_get_app`은 설치 안 됨일 때만, 편집·장소 선택 유지 (6-19) |
| 2-29 | 27·18·07 빠진 요소 | 반영 | 27 `companion_passport_add/done` 유지, 18 `SourceList(source, importSource)` + `IconBullet(Place, shopping_where)`, 07 ListGroup에 설명 Text (4.10, 6-07, 6-18, 6-27) |
| 2-30 | 글꼴 용량 | 반영 | 승인 요청에 실측 비교표(11,172자 / KS X 1001 2,350자 + 폴백 / 가변 글꼴) (D1, 3.2, 0b단계) |
| 2-31 | 썸네일 설명·id 매핑·PhotoBox 폭 | 반영 | 썸네일 장식(null), `Photos.byId`, PhotoBox 안에서 `fillMaxWidth()` (4.0, 4.13, 6-28) |
| 2-32 | 16 CheckRowCard 혼선 | 반영 | 아직 = 아이콘 없이 글자만, 체크 = 상태 카드 규칙(그림자 없음 + Success 막대), semantics = 이름 + stateDescription, `essentials_have`는 2단계 정리 (6-16) |

### 9.3 부분 반영·대안 반영 요약

- **2-27 번호 U+2060**: 기각한 부분. 테스트 호환을 지키고, 폭 전체 배치와 `statSmall`로 줄바꿈 위험을 줄인다.
- **2-16 키 2종**: 다른 수정(2-18 그리드 삭제, 2-28 기존 상태 라벨 사용)으로 필요가 없어졌다.
- **1-10 JP·SG 기대값**: 비평 1은 `null`, 비평 2는 `무료`였다. 첫 문장 규칙이 원문 그대로의 정확한 값을 주므로 비평 2를 따랐다.
- **1-15 / 2-22 날짜**: 비평 1이 제시한 '텍스트 입력 유지'를 택했다. 그래서 비평 2의 Input 모드·UTC 변환 지시는 대상이 없어졌다.
- **1-22 보안 배너**: 요구한 25·26·27 외에 `SecureScreen()`을 부르는 21·17·Autofill·ManualMode까지 넓혔다. 입국 카드 화면에서는 원칙 5(비제휴 고지가 첫 항목)와 부딪치지 않게 순서를 정했다.
