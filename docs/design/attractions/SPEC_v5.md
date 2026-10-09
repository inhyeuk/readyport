# 레디포트 '관광지' 설계 스펙 v5 (최종판, 2026-10-09)

> **상태**: 5라운드 비평(콘텐츠 8.3 · UX 8.3 · 아키텍처 8.1)을 v4에 반영한 **최종판**이다. 사장님이 정할 것은 open_decisions에 모았고, 본문에는 `⟦결정 Dn⟧`으로 표시했다. 그 밖의 내용은 엔지니어가 더 묻지 않고 구현할 수 있게 썼다. 결정이 걸린 곳마다 **기본 구현**(추천안)을 적었고, 결정이 다르게 나도 **데이터·설정 파일·문자열만 바꾸면 되는 구조**로 짰다(§15 결정별 변경 범위).
> **기능 이름**: 기본은 **'관광지'**(⟦결정 D17⟧)다. 문자열 키 접두사 `attractions_`와 패키지 `attractions`는 이름이 어떻게 정해지든 그대로 둔다.
> **범위 밖**: 14세 미만 게시판 법률 검토(별도 작업으로 진행), 개별 식당·투어 상품, 날짜별 일정표, pack.json 공항 목록 확장. pack.json 표기 정리 PR은 P0b에서 따로 낸다(§5.7의 경보 문단 해시 덕분에 이 PR이 관광지 화면에 거짓 경고를 만들지 않는다).
> **결정 번호**: v1~v4 번호를 이어 쓴다. 이번에 새로 생긴 결정은 D23(사실 대조 방식)·D24(시범 5곳)·D25(1차 지역 구성)다. D15·D22·D3은 이번에 확인된 사실을 보태 다시 여쭌다.
> **표기**: 본문 예시의 qid·좌표·현지어·요금·군 이름은 모두 **예시(미검증)** 다. 테스트 픽스처에 그대로 옮기지 않는다(§4.9).

---

## 0. 확인한 현재 코드·문서 사실 (설계 전제, 2026-10-09)

| 항목 | 현재 | 이 스펙에서 |
|---|---|---|
| 나라 데이터 | `packs/src/<CC>/pack.json` → `tools/packs/build_packs.py`(서명) → `app/src/main/assets/packs/<CC>/pack.json(+.sig)`(커밋함) | **pack.json은 건드리지 않는다.** 관광지는 따로 서명한 파일 `attractions.json`에 둔다 |
| 서명 키 | `rp-2026-1` 하나. GitHub 시크릿 `PACK_SIGNING_KEY_PEM`으로 deploy-packs·notices·videos 워크플로에도 들어 있음. `load_key`는 password=None | 관광지 전용 키 **`rp-att-2026-1`**, 암호 건 PEM, 운영자 PC에만 둠(§4.1, ⟦결정 D22⟧) |
| Hosting 배포 | `hosting/public/packs/`는 커밋하지 않음. `deploy-packs.yml`·`notices.yml`이 build_packs 뒤 `firebase deploy --only hosting`(사이트 전체 교체). `firebase.json`에 predeploy 없음. deploy-packs는 fetch-depth 2 | 공용 `stage_hosting.py` + firebase.json predeploy 검사(§10.1) |
| 손 배포 절차 | `CLAUDE.md` 46행, `docs/HUMAN_TASKS.md` 48행 | stage_hosting 경유로 **교체** |
| Hosting 캐시 | `Cache-Control: public, max-age=300` | 받은 version < RC면 `Stale` |
| 안전 문장 | `sections[]` 중 `id=="safety"`의 `body_ko` 배열 + `source: mofa_xx` + `last_verified`. 경보 문단(§5.7 정규식) 수 = TW 1·SG 1·VN 1·JP 1·CN 2·TH 3·MY 2·ID 2·PH 4(코드로 셈). 나머지 문단은 날치기·반간첩법·태풍 같은 생활 안내 | 해시는 **경보 문단만** 대상(§5.7) |
| 경보 판정 코드 | `CountryScreen.kt`의 `isHighAdvisory`·`HighAdvisoryWords`·`AdvisoryBanner`(Danger) | 앞의 두 개만 `com.readyport.pack.Advisory`로 옮긴다. 낱말·정규식은 `packs/schema/advisory_rules.json`과 대조한다 |
| PackRepository | `_revision` 하나를 여러 ViewModel이 구독. `update`가 쓰기 직후 `cache.remove(path)` | **API는 바꾸지 않는다**(v4의 notify 미루기·flushDeferred는 삭제). `UpdateResult`에 `Stale`·`NotFound`만 추가 |
| 네트워크 판정 | `net/Connectivity.kt`는 INTERNET·VALIDATED만 봄 | `networkStateFlow()`(metered) 추가 |
| SDK | minSdk 26, targetSdk 36. 매니페스트 권한은 CAMERA·POST_NOTIFICATIONS뿐. setExpedited·getForegroundInfo 사용 0건 | **setExpedited 쓰지 않는다**(FGS 요구 회피, §4.1) |
| Scaffold·인셋 | `ReadyPortRoot.MainScaffold`: bottomBar = BottomTabs, NavHost에 `Modifier.padding(inner)`만 있음(267행). consumeWindowInsets·imePadding 사용 0건 | NavHost를 `padding(inner).consumeWindowInsets(inner)`로 바꾼다(§6.3) |
| 스크롤 도우미 | `AppScreen`이 item key를 `keyIndex`에 기록, `state.scrollToKey(keyIndex, "key")` | 그대로 쓴다 |
| AppScreen | `AppScreen(title, …, subtitle, headerActions, modifier…)`, LazyColumn `contentPadding(horizontal = screenPadding)`(기본 20dp → 내용 폭 353dp) | **인자는 추가하지 않는다** |
| 나라 라우트 | `CountryRoute(…, focusAirports: Boolean = false, airport, tripId)`, `LaunchedEffect(ui.focusAirports…)`로 한 번 스크롤 | 같은 방식으로 `focusSection: String? = null`(값 `"safety"`)을 더한다(§6.4) |
| 검색 칸 | `BoardHome`과 `VideosScreen.SearchField`(private)에 두 벌 | `components/SearchField.kt` 하나로 합친다 |
| 그림 타일 | `Tiles.kt`의 `NavTile(large/row)`은 private, `NavMosaic`(첫 타일 크게 + 머리), `TileGrid` 2열 | `NavTile`을 **internal**로 열어 종류 타일에 쓴다(새 타일 부품 0개) |
| 이동하기 | `TransportViewModel` 나라 = `trip?.country ?: helpCountry ?: favorites.first()`. `Place`에 lat/lng | 고르기 모드는 이동하기 나라 하나로만 연다 |
| 숙소 | `StayRecord.lat/lng`(기기 안), `Stays.on(ui.stays, ui.today)` | 여행 중 '오늘 묵는 곳 지역'을 맨 위로 올리는 데 쓴다 |
| 찜 문구 | 나라 하트 `explore_favorite_remove` = '%1$s 찜 취소' | 앱 전체에서 '찜하기 / 찜 취소' 두 말만 쓴다 |
| 접근성 낭독 | `announceForAccessibility` 사용 0건(API 36에서 폐기) | 쓰지 않는다 |
| 저장소 패턴 | 모두 `preferencesDataStore` | 찜도 같은 방식 |
| 백업 | `allowBackup="false"`, 규칙 파일 두 개 모두 전체 exclude | ⟦결정 D16⟧ |
| debug/release 분리 선례 | `AppCheckInstaller`를 debug·release 소스셋에 같은 이름으로 둠 | `AttractionsFallback`도 같은 방식(§4.9) |
| 위치 권한 | 없음 | **계속 없음** |
| 앱 크기 | 10/9 AAB 14.3MB(그중 proguard.map 7.3MB는 사용자에게 가지 않음 → 실제 배포분 약 7MB) | 관광지 글 9개국 약 +0.5MB. 사진(D3-B)은 +약 4.2MB. P0b에서 bundletool로 다시 잰다 |
| 운영자 PC | java·gradle이 PATH에 없음 | 앱 테스트는 CI에서만 돌린다. promote 게이트에는 Python으로 검사할 수 있는 것만 넣는다 |
| 홈 | 부록 H.6, `HomeFirstScreenTest` | 건드리지 않는다(⟦결정 D9⟧) |

---

## 1. 목표와 성공 기준

1. 나라마다 **종류 → 관광지 목록(지역별 묶음) → 상세**로 이어지는 길을 만든다.
2. **이름·별칭·지역·가까운 역·종류 동의어·설명** 어느 것으로 찾아도 나오는 검색을 만든다. 결과는 **지역 묶음**(굵은 가로줄 + 지역 카드 한 장 + 여백)으로 보여 준다. 키보드가 떠 있어도 결과 개수와 첫 결과가 보여야 한다.
3. **찜하기**는 기기 안에만 저장한다. 팩이 바뀌어도 찜이 조용히 사라지지 않고, 합쳐진 항목의 찜은 새 항목으로 옮겨진다.
4. 모든 사실(숫자·최상급·유래·배경 포함)에 **출처 + 확인일**을 단다. 지어낸 값과 베낀 글은 빌드가 기계로 막는다(인용 대조 §5.3, copycheck §5.4).
5. 이동 시간 문제는 **지역 묶음(40/70km) + 하루 다녀오는 곳 표시 + 가는 법 + 여행 중 '오늘 묵는 곳' 지역을 맨 위로**로 푼다. 숫자는 보여 주지 않는다(⟦결정 D7⟧).
6. 오프라인에서도 목록·검색·찜·상세 글이 모두 동작한다. 위치 권한은 없고, 서버로 나가는 개인 데이터도 없다.
7. **나라 안전 정보와 관광지 경보 표시가 서로 어긋나지 않는다.** 둘이 다르면 언제나 **버전이 더 새 쪽**을 따른다(§5.7). 경보와 무관한 문장을 고치거나 날짜만 재확인해서는 거짓 경고가 뜨지 않는다.
8. '지역을 눌렀더니 1~2곳뿐'이거나 '상세가 얇다'는 느낌을 막는다(지역 깊이 게이트, 상세 최소 충실도).
9. 아직 싣지 않은 지역이나 경보 때문에 뺀 지역을 찾으면 **이유를 알려 준다**('준비 중이에요', '여행경보 3단계 이상이라 싣지 않았어요').

**출시 게이트**(나라별. `promote`·`sign`으로 published 서명할 때 아래 중 하나라도 어기면 실패. 숫자는 `packs/curation/gates.json`에 두고 나라별로 덮어쓸 수 있다. 이 파일은 PR 리뷰 대상이다)
- 관광지 `min_places`(기본 12) 이상. ⟦결정 D15⟧가 C안이면 PH만 8로 둔다.
- 보이는 base 지역 2곳 이상. **보이는 base 지역마다 관광지 3곳 이상**, daytrip 지역은 1곳 이상이어야 한다(0곳인 지역은 원래 그리지 않는다).
- 보이는 종류 4개 이상, 종류마다 2곳 이상.
- 한 종류가 전체의 50%를 넘으면 경고. CSV `상한 초과 사유`가 비어 있으면 실패.
- **상세 최소 충실도**(§5.2-13) 미달 항목이 10%를 넘으면 실패.
- 모든 관광지의 copycheck가 통과(텍스트 해시 기록 또는 캐시 재검사, §5.4).
- CN 항목은 모두 `address_local`이 있어야 한다. TH 항목은 모두 `names.local_short`가 있거나, 없다는 사실이 기록돼 기사님 버튼이 숨겨져야 한다(§4.4).
- 모든 문자열이 NFC여야 한다.
- 서명 kid가 `rp-att-*`이어야 한다.
- (v4의 '별칭에 이름의 공백 제거형 필수'와 'AttractionsFirstScreenTest 통과'는 **삭제**했다. 앞의 것은 정규화가 대신하고, 뒤의 것은 CI의 `CommittedAttractionsContractTest`가 맡는다.)
- **긴급 제외(`retire`)는 이 게이트를 적용하지 않는다**(§10.1).

**CI 게이트**(PR 머지 조건): `build_attractions.py --check`, `--verify-committed`, 도구 unittest, `testDebugUnitTest` 안의 `CommittedAttractionsContractTest`(§12), 기존 캡처·문자열 테스트 회귀 0.

---

## 2. 종류(카테고리) 체계

### 2.1 원칙
- **한 곳에 주 종류는 하나**(`category`)만 둔다. 겹치는 성격은 태그와 동의어로 찾게 한다.
- **판정 우선순위**(먼저 걸리는 번호를 CSV에 기록한다):
  1. 운영 주체가 놀이·체험 시설 회사인 고정 시설은 `theme_park`. **단, 전망이 주 상품인 시설(전망대·스카이워크·전망 케이블카)은 `city_view`** 다.
  2. 공식 분류가 박물관·미술관이면 `museum`.
  3. 유네스코 문화유산, 국가 지정 역사지구·보존지구는 `heritage`.
  3.5. 상점·노점·식당이 늘어선 것 자체가 주 목적인 거리·시장은 `market_street`.
  4. 상점이 주 목적이 아닌 옛 거리, 전망·야경 명소는 `city_view`.
  5. 그래도 애매하면 '시간과 입장료를 가장 많이 쓰는 이유'로 정하고, 그다음은 '챙길 것(복장·신발·수영복·현금)이 가장 많이 달라지는 쪽'으로 정한다.
- **동물 판정**: 동물 먹이 주기·쇼·타기·안기를 빼도 방문 이유가 남는지로 정한다(⟦결정 D19⟧, 기본 A안).
- **스키장**은 theme_park(+seasonal open_only)로 두되 1차에는 넣지 않는다. **상설 공연장**은 ⟦결정 D21⟧(기본 C안)을 따른다.
- 그 나라에 2곳 미만인 종류는 타일을 그리지 않는다('모든 종류'에서는 보인다).
- 데이터 값은 영어로 쓰고, 화면 라벨은 `strings_attractions.xml`에 7자 이내로 둔다. 허용 값 목록은 `packs/schema/attractions.enums.json` 한 곳에 둔다(§4.8).
- 앱은 모르는 종류 값이 와도 깨지지 않는다.

### 2.2 종류 7개

| 값 | 라벨 | 넣는 것 | 넣지 않는 것 | 목록 아이콘 | 일러스트 |
|---|---|---|---|---|---|
| `heritage` | 역사·유적 | 궁·성·사원·성당·옛 유적·역사지구·사적지 | 유적 안에 따로 입장하는 박물관(→museum) | `AccountBalance` | 기와지붕 문루 |
| `nature` | 산·자연 | 산·국립공원·협곡·폭포·동굴·호수·자연 정원·지열 지대 | 사람이 지은 랜드마크 정원(→city_view) | `Landscape` | 봉우리+폭포 |
| `sea_island` | 바다·섬 | 해변·섬·라군·만 | 투어 업체·상품 | `BeachAccess` | 야자수+섬 |
| `city_view` | 도시·전망 | 랜드마크·전망대·스카이워크·전망 케이블카·옛 거리·야경 명소·도시 정원 | 쇼핑·먹거리가 주목적인 거리 | `LocationCity` | 스카이라인+전망대 |
| `market_street` | 시장·쇼핑거리 | 야시장·전통시장·먹거리 거리·호커센터·쇼핑 **지구**(⟦결정 D14⟧) | 개별 상점·식당·카페 | `Storefront` | 노점 차양+등 |
| `museum` | 박물관·미술관 | 박물관·미술관·기념관·디지털 아트 뮤지엄 | 놀이 시설 | `Museum` | 기둥 건물+액자 |
| `theme_park` | 테마파크·체험 | 놀이공원·동물원·아쿠아리움·놀이 중심 케이블카 단지·(D21) 상설 공연장 | 투어·클래스·다이빙 업체(⟦결정 D13⟧), §5.2-2 (나)에 해당하는 시설 | `Attractions` | 관람차 |

- **색은 종류가 아니라 그 요소가 놓인 갈래를 따른다.** 여행 정보 갈래 안(종류 타일, 목록 줄 IconBadge, 상세 머리 패널)은 모두 Teal이다. 종류 구분은 그림·아이콘·글자가 맡는다. `TokenContrastTest`에 새 색 쌍은 없다.
- 쇼핑 갈래 `NavMosaic`에는 '물건 사기 좋은 곳' 행을 더한다. 그림은 새 `Illus.MarketStall`(주황 팔레트)이고 `columns = 1`이다. 누르면 `AttractionsRoute(country, category="market_street")`로 간다. 관광지 상태가 Available이 아니면 이 행은 그리지 않는다.
- '동네·섬 전체'(센토사, 우붓, 꾸따, 유후인, 하코네)는 관광지가 아니라 **지역**으로 다룬다.

### 2.3 판정표 `docs/attractions/CATEGORY_RULES.md` (고정 예. 애매한 곳은 이 표에 한 줄 추가한 뒤에 데이터를 넣는다)

| 곳 | 판정 | 근거 |
|---|---|---|
| 왓 아룬 / 방콕 왕궁(왓 프라깨우는 같은 입구라 별칭) / 자금성 / 보로부두르 | heritage | 3 |
| 후시미 이나리 / 도이수텝 / 바투 동굴 | heritage (+stairs / +mountain_view / 참배 목적) | 5 |
| 브사키 사원 | heritage (risk volcano, hazard = MAGMA 아궁산) | 3/5 |
| 호이안 구시가 / 조지타운 / 믈라카 / 산넨자카 / 싱가포르 보태닉 가든 | heritage | 3 |
| 뽀나가르 참탑 / 롱선사(나트랑, D25-A일 때) | heritage | 3/5 |
| 하노이 구시가 | 국가 지정이 확인되면 heritage, 아니면 market_street | 3 / 3.5 |
| 도톤보리 / 시먼딩 / 카오산 로드 / 난징루 / 짜뚜짝 시장 / 담 시장 / 국제거리(오키나와, D25-B) | market_street (public_space) | 3.5 |
| 와이탄 / 지우펀 옛 거리 / 스펀 옛 거리 | city_view (public_space). 지우펀 영화 배경설은 쓰지 않는다(제작사가 공식 부인) | 4 |
| 진과스 황금박물관 | museum (신베이시 공식 운영) | 2 |
| 예류 지질공원 | nature | 5 |
| 가든스 바이 더 베이 | city_view | 5 |
| 도쿄 스카이트리 / 타이베이 101 전망대 / 마리나베이샌즈 스카이파크 / 마하나콘 스카이워크 / 랑카위 스카이캡 | city_view (+cable_car 해당 시) | 1 예외 |
| 바나힐·골든브리지 / 빈원더스 나트랑 | theme_park | 1 |
| 판시판 케이블카 | theme_park. 전망 위주로 확인되면 city_view(+cable_car) | 1 / 1 예외 |
| 후지산 | nature (risk volcano), geo = 후지스바루라인 5합목 | 5 |
| 오와쿠다니 / 벳푸 지옥순례 | nature (+hot_spring, 오와쿠다니는 risk volcano·JMA) | 5 |
| 다케가와라 온천 | 국가 등록 유형문화재로 확인되면 heritage(+hot_spring), 아니면 넣지 않음 | 3 |
| 베이터우 지열곡 / 온천박물관 | nature / museum | 5 / 2 |
| 타이루거 | nature (risk post_disaster) | 5 |
| 하롱베이 | sea_island (+unesco), geo = 뚜언쩌우 국제여객선터미널. 선솜 동굴·티톱섬은 같은 배로 가는 곳이라 mentions_ko | 5, 별칭 규칙 |
| 슈리성(D25-B) | heritage (risk renovation, status 30일 주기) | 3 |
| 만리장성 | 바다링이 대표 항목. 무톈위는 ⟦D1⟧ 분량에 따라 별도 항목 또는 mentions_ko(별칭 금지) | §5.2-10 |
| 팀랩 플래닛 | museum | 2 |
| 만다이 동물원 / 추라우미 수족관 / 나라 공원 / 우붓 몽키 포레스트 / 보홀 안경원숭이 보호구역 | 포함((가)). 먹이 주기·공연은 tips에 관리기관 안내로만 | §5.2-2 |
| 오슬롭 고래상어 관람 / 사파리 월드 / 농눅 빌리지 / 코끼리 캠프 / 수빅 Ocean Adventure | 제외((나)) | §5.2-2 |
| 코끼리 보호 시설(sanctuary) | 제외(업체 상품) | §5.2-1 |
| 수상인형극장 / 인상 류싼제 / 송성 / 카바레 쇼 | ⟦D21⟧(기본 C: 수상인형극만 theme_park+performance) | D21 |
| 개별 쇼핑몰(아이콘시암 등) | ⟦D14⟧ 기본: 몰 이름은 지구의 별칭 | §5.2-6 |
| 독립운동 사적지 | ⟦D18⟧ A안이면 heritage 또는 museum(공식 분류에 따름) | 2/3 |

### 2.4 태그 (보조 정보, 사실만)
판단이 들어가는 태그(아이와 함께·사진 명소·현지인 추천·로맨틱)는 쓰지 않는다. 태그마다 근거 출처가 있어야 하고, 앱이 모르는 태그는 숨긴다.

| id | 라벨 | 근거 |
|---|---|---|
| `unesco` | 세계유산 | whc.unesco.org 또는 Wikidata P1435(유효 참조) |
| `indoor` | 실내(비 와도 좋아요) | 공식 시설 안내 |
| `free_entry` | 입장 무료 | 공식 (public_space에는 붙이지 않음) |
| `booking_required` | 예약 필수 | 공식 |
| `dress_code` | 복장 규정 있음 | 공식 |
| `night` | 밤에 열어요 | 공식·지자체 관광 포털 |
| `stairs` | 계단·오르막 많음 | 공식 안내 문구가 있을 때만 |
| `step_free` | 휠체어로 다닐 수 있어요 | 시설 공식 접근성 페이지 |
| `cable_car` | 케이블카 있음 | 운영사 |
| `mountain_view` | 산 위·전망 | 공식 또는 Wikidata 고도(유효 참조) |
| `seafront` | 바다 앞 | 공식 |
| `foreigner_price` | 외국인 요금 따로 | 공식 요금표 |
| `hot_spring` | 온천 | 지자체·온천 협회 공식 |
| (D21 B·C) `performance` | 공연 | 극장 공식 |
| (D13-B) `act_snorkel`·`act_kayak`·`act_trail`·`act_swim` | 스노클링·카약·등산로·물놀이 | 관리기관 공식 안내만 |

### 2.5 종류 검색 동의어 (앱에 고정한 사전 `CategorySynonyms.kt`)

| 종류 | 동의어 |
|---|---|
| heritage | 유적, 역사, 절, 사원, 성당, 궁, 성, 사적지 |
| nature | 산, 자연, 폭포, 국립공원, 동굴, 호수, 협곡 |
| sea_island | 바다, 해변, 비치, 섬, 호핑 |
| city_view | 전망, 야경, 랜드마크, 전망대 |
| market_street | 쇼핑, 시장, 야시장, 먹거리, 거리 |
| museum | 박물관, 미술관, 전시, 기념관 |
| theme_park | 액티비티, 체험, 놀이공원, 테마파크, 동물원, 수족관 |
| (태그 hot_spring) | 온천, 노천탕 |
- **안 싣는 것 사전** `ExcludedTopics.kt`: 스노클링·다이빙·서핑·호핑투어·투어·마사지·맛집·식당. 낱말마다 대신 안내할 종류를 붙인다(바다 관련 → sea_island, 맛집·식당 → market_street, 투어·마사지 → 없음). D13-B가 되면 act_* 태그 라벨과 겹치는 낱말은 이 사전에서 뺀다.

---

## 3. 지역(권역) 체계

### 3.1 정의
- **지역(`region`)** 은 도시 하나 또는 관광 덩어리 하나다. 그 안의 곳끼리는 대체로 1시간 안에 오갈 수 있고, 하루에 여러 곳을 도는 단위다. 목록의 가로줄 머리가 이 단위다.
- **hub(지역 기준점)**: SCHEMA_RULES에 이렇게 정한다. **'그 지역 여행자가 실제로 출발하는 교통 거점(주요 역·버스터미널·선착장)의 Wikidata 항목'** 이다. 마땅한 거점이 없으면 rank.order가 가장 높은 관광지의 방문 지점을 쓴다. 행정구역 QID의 좌표(시청·기하 중심)는 쓰지 않는다. regions 시트에 'hub 선택 근거'를 적는다. 예: th_bangkok = 시암 BTS 같은 시내 거점, vn_hanoi = 호안끼엠 일대 거점, cn_zhangjiajie = 무릉원 쪽 거점(천문산이 40km를 넘으면 근거를 적거나 지역을 나눈다).
- **lint**: base 지역에서 관광지–hub 직선거리가 40km를 넘으면 경고하고, regions 시트의 '같은 지역 유지 근거'가 비어 있으면 서명에 실패한다. 70km를 넘으면 실패한다(daytrip 지역으로 나눈다). daytrip 지역 안에서는 70km를 넘어도 경고만 한다.
- **하루 다녀오는 곳**: `kind: "daytrip"` + `base_regions: [id, …]`(1개 이상, 모두 base). 머리 아래에 `note_ko`를 보여 준다. 당일 왕복 근거(운영사 시간표 URL·확인일)는 regions 시트에 둔다. 화면에는 숫자를 보여 주지 않는다.
- **묶음(`group_ko`)** 은 지리 이름일 뿐이다(건너뛰기 소제목·검색 별칭). 이동 시간의 뜻은 없고, 경보 숨김 판정에도 쓰지 않는다.
- `water_crossing: true` = 배를 타야 닿는 지역이다. 머리에 '배를 타요'(Info)를 붙인다.
- **정렬 키(RegionGrouping, 결정적)**
  - base: `(order, 0, order)`. daytrip: `(거점의 order, 1, 자기 order)`. 거점 = '끌어올린 지역 가운데 base_regions에 든 첫 번째, 없으면 base_regions[0]'. 거점이 걸러 보기로 숨어도 daytrip은 그 자리를 지킨다.
  - **끌어올리기(`LiftAnchor`, 우선순위 순)**
    1. **오늘 묵는 곳**: 이 나라 여행이 진행 중이고 `Stays.on(stays, today)`에 좌표가 있을 때, 숙소에서 가장 가까운 base hub가 직선 40km 안이면 그 지역을 `(-1,0,0)`으로 올린다. 머리 태그는 '오늘 묵는 곳 지역'(Info, Hotel)이다.
    2. **도착 공항**: 1번이 성립하지 않고, 이 나라 여행이 있고, `trip.arrivalAirport`가 어떤 base 지역의 `airports`에 있으면 그 지역을 올린다. 머리 태그는 '내 도착 공항 지역'(Info, FlightLand)이다. 도착 공항이 `unmapped_airports`에 있고 reason이 `upcoming`(준비 중인 지역)이면 개수 줄 아래에 Info 한 줄 '도착 공항(깜라인) 근처 지역은 준비 중이에요'를 보여 준다(공항 이름은 pack.json airports의 한국어 이름).
    3. 그 밖에는 올리지 않는다.
    - 딸린 daytrip 지역도 함께 올라간다. 계산은 기기 안에서만 하고 숫자는 보여 주지 않는다. 숙소 좌표는 화면 상태에만 쓰고 저장하거나 보내지 않는다. compact 나라(SG)에서는 하지 않는다.
  - 모르는 region id를 가리키는 관광지는 목록과 검색에서 빼고, 찜 목록에서는 '앱을 업데이트하면 볼 수 있어요'로 보여 준다.
- **공항 매핑**: `airports`에는 pack.json `airports[].code`만 넣는다. 팩의 모든 공항 코드는 어느 한 지역에 매핑되거나 `unmapped_airports: [{code, reason, reason_ko}]`에 있어야 한다(빠짐 0). reason ∈ `compact`(나라 전체가 가까워요) / `upcoming`(준비 중인 지역) / `advisory`(여행경보 때문에 싣지 않은 지역) / `safety_retired`(안전 문제로 뺀 지역). 공항–hub 거리가 60km를 넘으면 경고한다.
- 관광지가 0곳인 지역은 그리지 않는다. `compact_country: true`(SG)이면 안내 문장과 끌어올리기가 없다.
- **안내 문장**(개수 줄에 붙임): '지역이 다르면 이동이 길어질 수 있어요. 하루는 같은 지역 안에서 다니면 편해요.'
- **묶음 이름 별칭**(예스진지처럼 여러 곳의 머리글자를 딴 이름)은 그 이름을 이루는 지명이 모두 실려 있을 때만 허용한다. regions 시트에 '묶음 별칭 구성 지명'을 적으면 lint가 지명마다 그 지역 관광지 이름·별칭에 있는지 확인하고, 하나라도 없으면 실패시킨다.

### 3.2 공개 차수(wave)와 1차 선정 규칙
- regions 시트와 작업본 `packs/drafts/<CC>/attractions.json`의 지역에 `wave`(정수)를 둔다. **wave는 작업본 전용 필드**다. `promote --wave N`은 wave가 N 이하인 지역과 그 관광지만 골라 wave 필드를 지우고 서명한다.
- promote는 wave 때문에 빠진 지역의 `name_ko`·`aliases_ko`를 published 파일의 `upcoming_regions[]`에 남긴다. 그 지역의 공항은 `unmapped_airports`(reason `upcoming`)로 옮긴다.
- **1차 선정 규칙**(기본 구현, ⟦결정 D25⟧ A안): ① **pack.json에 공항이 있는 지역은 1차에 넣는다.** 못 넣으면 CSV에 사유를 적는다. ② 그 밖의 지역은 지정 여부와 조회수, 한국발 직항 여부로 고른다. ③ regions 시트 '1차 선정 근거' 열에 쓴 지표(팩 공항·직항·조회수)를 적는다.
- 2차 지역은 앱을 업데이트하지 않고 promote(팩 갱신)만으로 추가한다.
- ⟦결정 D1⟧ A안(깊게, 기본)은 아래 표의 차수를 따른다. B안(넓게)이면 모든 지역을 1차로 두되 게이트의 '지역마다 3곳'을 '2곳'으로 낮춘다(gates.json의 값만 바뀜).

### 3.3 9개국 지역 초안
> 큐레이터가 경계·당일 왕복 근거·경보 구역(§5.6)을 확인한 뒤 확정한다. 바뀌면 이 표와 팩을 함께 고친다. 지역 advisory는 §5.6 판정값 가운데 그 지역 관광지의 최고 단계로 둔다. 괄호 안 숫자는 1차 목표 곳 수(예시)다.

**TH 태국** (advisory 기본 1)
| id | 이름 | 묶음 | kind (base_regions) | 공항·비고 | 차수 |
|---|---|---|---|---|---|
| th_bangkok | 방콕 | 방콕권 | base | BKK·DMK | 1 (8) |
| th_ayutthaya | 아유타야 | 방콕권 | daytrip [th_bangkok] | | 1 (2) |
| th_maeklong | 담넌사두억·매끌롱 | 방콕권 | daytrip [th_bangkok] | | 1 (2) |
| th_kanchanaburi | 칸차나부리 | 방콕권 | daytrip [th_bangkok] | | 2 |
| th_pattaya | 파타야 | 동부 해안 | base | | 1 (3) |
| th_chiangmai | 치앙마이 | 북부 | base | (CNX는 팩에 없음) | 1 (4) |
| th_inthanon | 도이 인타논 | 북부 | daytrip [th_chiangmai] | | 2 |
| th_chiangrai | 치앙라이 | 북부 | base | | 2 |
| th_phuket | 푸껫 | 안다만해 | base, watch ["푸껫"] | HKT | 1 (4) |
| th_krabi | 끄라비(아오낭) | 안다만해 | base | | 2 |
| th_phiphi | 피피섬 | 안다만해 | daytrip [th_phuket, th_krabi], water_crossing | 마야베이 seasonal | 2 |
| th_samui | 코사무이 | 타이만 | base | | 2 |
제외: 빠따니·나라티왓·얄라·송클라 주, 캄보디아 국경 50km 안쪽, 딱 주(2단계, D15). `unmapped_airports`: 없음.

**JP 일본** (advisory 기본 none)
| id | 이름 | 묶음 | kind (base_regions) | 공항·비고 | 차수 |
|---|---|---|---|---|---|
| jp_tokyo | 도쿄·요코하마 | 도쿄권 | base | NRT·HND | 1 (8) |
| jp_hakone | 하코네 | 도쿄권 | daytrip [jp_tokyo] | 오와쿠다니 risk volcano(JMA) | 1 (1~2) |
| jp_kamakura | 가마쿠라 | 도쿄권 | daytrip [jp_tokyo] | | 1 (1~2) |
| jp_nikko | 닛코 | 도쿄권 | daytrip [jp_tokyo] | | 2 |
| jp_fuji | 후지산·가와구치코 | 도쿄권 | daytrip [jp_tokyo] | risk volcano·seasonal | 1 (1~2) |
| jp_osaka | 오사카 | 간사이 | base | KIX | 1 (5) |
| jp_kyoto | 교토 | 간사이 | base | | 1 (7) |
| jp_nara | 나라 | 간사이 | daytrip [jp_osaka, jp_kyoto] | | 1 (2) |
| jp_kobe | 고베 | 간사이 | base | | 2 |
| jp_himeji | 히메지 | 간사이 | daytrip [jp_osaka, jp_kobe] | | 2 |
| jp_fukuoka | 후쿠오카 | 규슈 | base | FUK | 1 (3) |
| jp_yufuin_beppu | 유후인·벳푸 | 규슈 | base | | 1 (3) |
| jp_nagasaki | 나가사키 | 규슈 | base | | 2 |
| jp_tsushima | 대마도(쓰시마) | 규슈 | base, water_crossing, note '부산에서 배로 가요' | 근거 = 선사 공식 노선 | 2 |
| jp_sapporo | 삿포로·오타루 | 홋카이도 | base | | 1 (3) |
| jp_biei_furano | 비에이·후라노 | 홋카이도 | base | | 2 |
| jp_okinawa_naha | 오키나와 나하·남부 | 오키나와 | base | (OKA는 팩에 없음) | D25-B면 1 (3), 아니면 2 |
| jp_okinawa_north | 오키나와 중북부 | 오키나와 | base | | D25-B면 1 (3), 아니면 2 |
| jp_nagoya / jp_takayama / jp_hiroshima | 나고야 / 다카야마·시라카와고 / 히로시마·미야지마 | 중부 / 중부 / 주고쿠 | base | | 2 |
※ 오사카·교토·고베는 합치지 않는다. 제외: 후쿠시마 원전 구역(§5.6). `unmapped_airports`: 없음.

**VN 베트남** (advisory 기본 1)
| id | 이름 | 묶음 | kind (base_regions) | 공항 | 차수 |
|---|---|---|---|---|---|
| vn_hanoi | 하노이 | 북부 | base | HAN | 1 (4) |
| vn_halong | 하롱베이 | 북부 | **daytrip [vn_hanoi]**, note '하노이에서 하루 다녀오기도 해요' | 근거 = 하노이–하롱 고속버스 운영사 시간표. 크루즈 숙박은 업체 상품이라 쓰지 않음 | 1 (1~2) |
| vn_ninhbinh | 닌빈 | 북부 | daytrip [vn_hanoi] | | 1 (2) |
| vn_sapa | 사파 | 북부 | base | | 2 |
| vn_danang | 다낭 | 중부 | base | DAD | 1 (5) |
| vn_hoian | 호이안 | 중부 | daytrip [vn_danang], note '다낭에서 하루 다녀오기도 해요' | | 1 (3) |
| vn_hue | 후에 | 중부 | base | | 2 |
| vn_nhatrang | 나트랑(냐짱) | 남중부 | base | CXR | **D25-A·B면 1 (4~5)**, C면 2 |
| vn_dalat | 달랏 | 남중부 | base | | 2 |
| vn_hcmc | 호찌민 | 남부 | base | SGN | 1 (4) |
| vn_mekong | 메콩 델타 | 남부 | daytrip [vn_hcmc] | | 2 |
| vn_phuquoc | 푸꾸옥 | 남부 | base | (PQC는 팩에 없음) | 1 (3) |
※ 나트랑 1차 후보: 뽀나가르 참탑, 롱선사, 나트랑 해변(public_space), 담 시장, 빈원더스 나트랑. D25-C이면 CXR은 `unmapped_airports`(upcoming)로 둔다.

**TW 대만** (advisory none만 허용)
| id | 이름 | 묶음 | kind (base_regions) | 공항 | 차수 |
|---|---|---|---|---|---|
| tw_taipei | 타이베이(단수이·베이터우 포함) | 북부 | base | TPE·TSA | 1 (8) |
| tw_north_coast | 지우펀·진과스·스펀·예류 | 북부 | daytrip [tw_taipei], 별칭 '예스진지'(구성 지명: 예류·스펀·진과스·지우펀) | | 1 (4: 예류 지질공원·스펀 옛 거리·진과스 황금박물관·지우펀 옛 거리) |
| tw_taichung | 타이중 | 중부 | base | | 2 |
| tw_sunmoonlake | 일월담 | 중부 | daytrip [tw_taichung] | | 2 |
| tw_tainan | 타이난 | 남부 | base | | 1 (3) |
| tw_kaohsiung | 가오슝 | 남부 | base | KHH | 1 (4) |
| tw_kenting | 컨딩 | 남부 | base | | 2 |
| tw_hualien | 화롄·타이루거 | 동부 | base (risk post_disaster) | | 2 |
※ 스펀의 천등 날리기는 업체 행위라 관리기관 안내가 있을 때만 tips로 다룬다.

**PH 필리핀** (advisory 기본 2, 낮춤 예외 1: 보라카이섬·보홀섬·막탄섬(라푸라푸시)·수빅시. 2단계 지역은 ⟦결정 D15⟧를 따른다)
| id | 이름 | 묶음 | kind (base_regions) | 공항·비고 | 차수 |
|---|---|---|---|---|---|
| ph_manila | 마닐라 | 루손 | base (2) | MNL | D15-B면 1 |
| ph_tagaytay | 타가이타이 | 루손 | daytrip [ph_manila] (2) | risk volcano(따알, PHIVOLCS) | 2 |
| ph_clark | 클락·앙헬레스 | 루손 | base (2) | | 2 |
| ph_subic | 수빅 | 루손 | base | 프리포트 관광지는 수빅 자치시 밖이라 기본 2 | 2 |
| ph_mactan | 막탄(라푸라푸시) | 비사야 | base (1) | CEB(라푸라푸시 소재) | D15-B면 ph_cebu에 합침 |
| ph_cebu | 세부시 | 비사야 | base (2) | | D15-B면 1 (4, 막탄 포함) |
| ph_cebu_south | 세부 남부(모알보알·카와산) | 비사야 | daytrip [ph_cebu] (2) | | 2 |
| ph_bohol | 보홀 | 비사야 | base (본섬 1, 팡라오 근거 확인 전 2) | | 1 (4) |
| ph_boracay | 보라카이 | 비사야 | base (1) | | 1 (3) |
| ph_elnido / ph_coron / ph_puerto_princesa | 엘니도 / 코론 / 푸에르토프린세사 | 팔라완 | base (2) | | 2 |
※ D15-A·C에서는 막탄을 세부시와 분리한 `ph_mactan`으로 둔다. 막탄은 공공 관광지가 1~2곳뿐이라 '지역마다 3곳'을 못 채우고, 그 경우 그리지 않는다. 그러면 CEB는 `unmapped_airports`(advisory)로 간다. **D15-A이면 PH 1차는 보라카이·보홀 약 8곳뿐이라 공개 기준 12곳에 못 미쳐 공개하지 못한다. D15-C이면 gates.json에서 PH의 min_places를 8로 두고 공개한다.** 제외: 팔라완 남부 3단계, 민다나오 3·4단계.

**ID 인도네시아** (advisory 기본 **1**. 서파푸아·파푸아 6개 주, 말루쿠·북말루쿠, 아체는 2)
| id | 이름 | 묶음 | kind (base_regions) | 공항·비고 | 차수 |
|---|---|---|---|---|---|
| id_bali_south | 발리 남부(꾸따·스미냑·짱구·사누르) | 발리 | base | DPS | 1 (4) |
| id_bali_ubud | 발리 우붓 | 발리 | base | 킨타마니·바투르는 hub 거리 근거와 함께 배치 | 1 (4) |
| id_bali_bukit | 발리 울루와뚜·누사두아 | 발리 | base | | 1 (3) |
| id_bali_east | 발리 동부(렘푸양·브사키·띠르따 강가) | 발리 | daytrip [id_bali_ubud, id_bali_south] | 브사키 risk volcano 필수 | 1 (2) |
| id_nusa_penida | 누사페니다 | 발리 | daytrip [id_bali_south], water_crossing | | 2 |
| id_yogyakarta | 족자카르타(보로부두르·프람바난) | 자바 | base | | 1 (3) |
| id_jakarta | 자카르타 | 자바 | base | CGK | D25-A면 1 (3), 못 채우면 CSV 사유 + upcoming |
| id_lombok / id_labuanbajo | 롬복 / 라부안바조(코모도) | 누사틍가라 | base | | 2 |
| *id_east_java* | *동자바(브로모·이젠)* | 자바 | 보류: MAGMA hazard 출처를 확보한 뒤 | | 보류 |

**MY 말레이시아** (advisory 기본 1. 사바 동부해안 2, 동부 섬들·동부해안 일부 3)
| id | 이름 | 묶음 | kind (base_regions) | 공항 | 차수 |
|---|---|---|---|---|---|
| my_kl | 쿠알라룸푸르(바투 동굴 포함) | 반도 서부 | base | KUL | 1 (6) |
| my_genting | 겐팅 하이랜드 | 반도 서부 | daytrip [my_kl] | | 1 (1) |
| my_malacca | 말라카(믈라카) | 반도 서부 | base | | 1 (3) |
| my_penang | 페낭 | 반도 북부 | base | | 1 (3) |
| my_langkawi | 랑카위 | 반도 북부 | base | | 2 |
| my_kotakinabalu | 코타키나발루 | 사바 | base | BKI | 1 (3) |
| my_kinabalu | 키나발루 공원 | 사바 | daytrip [my_kotakinabalu] | | 2 |
제외: 사바 동부 3단계 구역은 늘 제외하고, 2단계 구역은 D15를 따른다. 코타키나발루·키나발루 공원이 두 구역 밖이라는 것을 단위 테스트로 고정한다.

**SG 싱가포르** (`compact_country: true`, 묶음 없음, advisory none만)
| id | 이름 | kind | 차수 |
|---|---|---|---|
| sg_central | 시내 중심(마리나베이·차이나타운·오차드·보태닉 가든) | base | 1 |
| sg_sentosa | 센토사 | base | 1 |
| sg_north | 만다이(북부) | base | 1 |
`unmapped_airports`: SIN(compact). 창이 쥬얼은 넣지 않는다.

**CN 중국** (advisory 기본 1, 티베트·신장 special. 지명 표기는 ⟦결정 D20⟧)
| id | 이름 | 묶음 | kind (base_regions) | 공항·비고 | 차수 |
|---|---|---|---|---|---|
| cn_beijing | 베이징 | 화북 | base | PEK·PKX | 1 (6) |
| cn_greatwall | 만리장성 | 화북 | daytrip [cn_beijing] | 바다링 대표 | 1 (1~2) |
| cn_qingdao | 칭다오 | 화북 | base | | 2 |
| cn_changbaishan | 백두산(D18) | 동북 | base | | 2 |
| cn_harbin | 하얼빈(D18) | 동북 | base | D18-A일 때만 | 2 |
| cn_xian | 시안 | 서북 | base | | 2 |
| cn_shanghai | 상하이 | 화동 | base | PVG | 1 (5) |
| cn_suzhou | 쑤저우 | 화동 | daytrip [cn_shanghai] | | 1 (2) |
| cn_hangzhou | 항저우 | 화동 | base | | 1 (3) |
| cn_zhangjiajie | 장자제 | 화중 | base | hub = 무릉원 쪽 거점 | 1 (3) |
| cn_chengdu / cn_chongqing / cn_guilin | 청두 / 충칭 / 구이린·양숴 | 서남 / 서남 / 화남 | base | | 2 |

---

## 4. 데이터 스키마 — `attractions.json` (나라별, 따로 서명)

### 4.1 파일·배포·키·동기화
- **파일 세 곳**
  - 작업본 `packs/drafts/<CC>/attractions.json`: `release`는 언제나 `draft`이고 `wave`를 쓸 수 있다. 몇 주씩 걸리는 편집을 여기에 커밋한다.
  - 원본 `packs/src/<CC>/attractions.json`: 마지막으로 서명한 원본이다. promote·sign·retire만 쓴다.
  - 서명본 `app/src/main/assets/packs/<CC>/attractions.json(+.sig)`: **커밋한다.**
- **키**: 관광지 전용 kid **`rp-att-2026-1`**. 비밀키는 암호를 건 PEM으로 운영자 PC의 `~/.readyport/keys/`에만 두고, **GitHub 시크릿에는 넣지 않는다.** `build_attractions.py`는 자체 `load_key`에서 `getpass`로 암호를 묻는다(D22-B일 때만 환경 변수 `RP_ATT_KEY_PASS`를 허용). build_packs의 키 로딩은 바꾸지 않는다.
  - 앱 `PackKeys`는 `TRUSTED_FOR: Map<DocKind, Map<kid, PublicKey>>`로 나눈다. `DocKind.Attractions`는 `rp-att-*`만, pack·index·recipe는 기존 키만 받는다. 공개키는 P1 앱 업데이트에 넣는다.
  - stage_hosting과 `--verify-committed`는 attractions 서명의 kid가 att 키인지 검사한다. `docs/SECURITY_KEYS.md`에 두 키의 용도와 보관 위치를 적는다.
  - 누가 서명하는지는 ⟦결정 D22⟧(기본 A: 사장님이 직접 실행하고 암호를 입력, ARIA는 `--check`까지).
- **Hosting**: `tools/deploy/stage_hosting.py`가 커밋된 서명본을 `hosting/public/packs/<CC>/`로 복사한다. 워크플로 두 개와 firebase.json predeploy 검사가 이를 지킨다(§10.1).
- **스키마**: `packs/schema/attractions.schema.json`(`additionalProperties:false`, drafts용 변형은 `wave` 허용). 허용 값 목록은 `packs/schema/attractions.enums.json`에 둔다. 앱 상수는 `SUPPORTED_ATTRACTIONS_SCHEMA = 1`이다.
- **schema_version 규칙**(`docs/attractions/SCHEMA_RULES.md`): 값 추가·선택 필드 추가는 1을 유지하고, 필수 필드 추가·의미 변경·삭제만 버전을 올린다. v5에서 추가한 `upcoming_regions`·`excluded_areas`·`unmapped_airports[].reason`·`ko_paren`은 모두 선택 필드라 1을 유지한다.
- **좌표계**는 WGS-84만 쓴다. GCJ-02·BD-09 변환은 금지한다. **모든 문자열은 NFC**여야 한다(서명 실패 조건).
- **버전 포인터**: RC 키 `attractions_version_<CC>`, `RemoteConfigVersions.attractionsVersion(cc)`. `ci_deploy.py`의 `VERSION_KEY_RE`에 이 키를 추가하고, `pack_versions()`는 커밋된 assets 서명본에서 version을 읽는다. RC 템플릿 전체 배포는 금지한다(rc-versions만).
- **전송량 규칙**(`docs/ARCHITECTURE.md` 9.3에 추가)
  - `ci_deploy rc-versions`는 **한 번 실행에서 attractions 키를 최대 3개까지만 바꾼다.** 나머지는 경고를 내고 다음 날 workflow_dispatch로 넘긴다. 상한 계산식(활성 사용자 × 나라당 대상 비율 × gzip 크기 ≤ 180MB, 즉 Spark 하루 360MB의 절반)을 문서에 적는다.
  - 7일 규칙: 템플릿의 현재 값과 새 값의 **버전 문자열 날짜**(`2026.10.20-1`)가 7일 미만으로 차이 나면 경고한다(이력 API 불필요). 긴급 retire는 예외다.
  - 관광지 변경에는 fcm-notify를 보내지 않는다. 첫 공개는 나라별로 하루 이상 간격을 둔다.
- **결과값**: `UpdateResult`에 `Stale`(받은 version < RC)과 `NotFound`(HTTP 404, `HttpPackRemote`가 구분)를 추가한다. pack 경로도 같은 값을 쓴다.
- **revision**: `SignedFileStore`(공용)에는 revision이 없다. `PackRepository`는 **바꾸지 않는다.** `AttractionsRepository.revisionOf(cc): StateFlow<Int>`는 나라별로 둔다. **관광지 작업은 pack.json을 받지 않는다**(v4의 `update(cc, notify)`·`flushDeferred`·`onCleared` 훅은 삭제. 경보 일치는 §5.7의 버전 방향 판정으로 해결).
- **동기화**
  1. **화면을 열 때 받기**: 여행 정보 갈래가 그려질 때 `online && (!wifiOnly || !metered)`이고 RC가 기기 본보다 새것이면, `OneTimeWorkRequestBuilder<AttractionsSyncWorker>().setConstraints(CONNECTED)` + `enqueueUniqueWork("attractions-now-$cc", KEEP)`를 건다. **setExpedited는 쓰지 않는다**(minSdk 26 기기에서 포그라운드 서비스가 필요해지기 때문).
  2. **[지금 받기]**: 같은 고유 작업을 와이파이 전용 설정과 상관없이 건다. 화면을 떠나도 계속된다. 기존 `pack-sync-now`는 건드리지 않는다.
  3. **완료 판정**: 받은 version이 RC보다 낮으면 `Stale`이다. 같은 실행에서 나라당 5분에 한 번 다시 시도하고, 다음 화면 진입이나 다음 Worker 주기에 또 시도한다.
  4. **실패 기억**: `preferencesDataStore(name="attractions_sync")`에 나라별 `{rc, result, at}`를 둔다. `UnsupportedSchema`이면 `NeedsAppUpdate`로 두고 RC가 바뀌기 전까지 다시 받지 않는다. `SignatureInvalid`·`Malformed`이면 같은 RC에 대해 하루 1회만 다시 시도한다. `NotFound`이면 이번 주기를 건너뛴다.
  5. **PackSyncWorker 대상**: pack·attractions 모두 '찜한 나라 ∪ 진행 중이거나 30일 안에 출발하는 여행의 나라'다. attractions는 여기에 '관광지 찜이 있는 나라'를 더한다. pack은 '기기 pack version < 관광지 `advisory_basis.pack_version`인 나라'를 더한다(§5.7 (a)).
- **네트워크 판정**: `net/Connectivity.kt`에 `networkStateFlow(): Flow<NetState(online, metered)>`를 추가한다.

### 4.2 최상위
```json
{
  "doc_type": "attractions",
  "schema_version": 1,
  "country": "TH",
  "version": "2026.10.20-1",
  "release": "published",
  "editorial_rules": "2026-10",
  "compact_country": false,
  "advisory_basis": { "pack_version": "2026.10.03-2", "safety_last_verified": "2026-09-28",
                      "advisory_sha256": "…(빌드가 계산, §5.7)" },
  "unmapped_airports": [ { "code": "CXR", "reason": "upcoming", "reason_ko": "준비 중인 지역" } ],
  "upcoming_regions": [ { "name_ko": "끄라비", "aliases_ko": ["크라비", "아오낭"] } ],
  "excluded_areas": [ { "name_ko": "빠따니", "aliases_ko": [], "level": "3" } ],
  "regions": [ Region ],
  "attractions": [ Attraction ],
  "retired": [ Retired ],
  "sources": [ { "id": "wd", "name": "Wikidata", "url": "https://www.wikidata.org/", "use": "skeleton", "license": "CC0" } ]
}
```
- `doc_type`(`"attractions"`), `regions`, `attractions`는 필수다. doc_type이 다르면 Malformed로 본다. `regions`·`attractions`가 빈 배열이어도 앱은 깨지지 않는다(갈래 상태 NotYet).
- `advisory_basis.advisory_sha256`의 계산은 §5.7을 따른다. v4의 `safety_sha256`은 이름과 뜻을 함께 바꾼다(아직 공개 전이라 호환 부담 없음).
- `excluded_areas`는 promote가 `advisory_zones.json`에서 그 나라의 raise 구역(3·4·special, 그리고 D15-A·C에서 2단계 구역)의 `name_ko`·`aliases_ko`로 만든다.
- `sources[].use` ∈ `skeleton` / `facts` / `open_data` / `advisory` / `hazard` / `heritage_registry` / `coords_osm` / `photo` / `sample`(서명 금지). **`license`는 `open_data`·`heritage_registry`·`coords_osm`·`photo`에 필수**다(예: `JP-GSTOU-2.0`, `TW-OGDL-1.0`, `KOGL-1`, `ODbL-1.0`, `CC0`). 출처 표기 의무가 있는 라이선스(`attribution_required: true`)이면 상세 SourceList와 설정 › 출처에 기관 이름을 보여 준다.

### 4.3 Region
```json
{
  "id": "th_ayutthaya", "order": 20,
  "name_ko": "아유타야", "name_en": "Ayutthaya",
  "aliases_ko": ["아유타야 역사공원"],
  "group_ko": "방콕권",
  "kind": "daytrip", "base_regions": ["th_bangkok"],
  "note_ko": "방콕에서 하루 다녀오는 곳",
  "water_crossing": false,
  "hub": { "name_ko": "(교통 거점 이름)", "lat": 0.0, "lng": 0.0, "qid": "Q…" },
  "airports": [],
  "advisory": { "level": "1", "source": "mofa_th", "last_verified": "2026-09-28" },
  "advisory_watch_ko": []
}
```
- `kind` ∈ `base`/`daytrip`. daytrip이면 `base_regions`가 1개 이상이고 모두 base여야 한다.
- `hub`는 §3.1의 정의를 따른다(교통 거점의 qid가 필수).
- `advisory_watch_ko`(선택): 앞으로 0404 안전 문장에 나오면 이 지역을 숨겨야 할 **이 지역만의 지명**이다. 도·주·섬 전체 이름처럼 다른 구역과 겹치는 이름은 넣지 않는다. 서명할 때 지금 pack 경보 문단에 이미 걸리면 실패한다.
- (drafts 전용) `wave`.

### 4.4 Attraction
```json
{
  "id": "wat-arun",
  "qid": "Q…(예시)",
  "names": { "ko": "왓 아룬", "ko_basis": "editorial", "ko_paren": null, "en": "Wat Arun",
             "local": "(Wikidata 현지어 라벨)", "local_short": "(현지 통용 이름)",
             "local_lang": "th", "source": "wd" },
  "aliases_ko": ["새벽 사원"],
  "aliases_en": ["Temple of Dawn"],
  "mentions_ko": [],
  "region": "th_bangkok",
  "area_ko": "방콕 톤부리",
  "category": "heritage",
  "tags": [ { "id": "dress_code", "source": "wat_arun_official", "last_verified": "2026-10-15" } ],
  "geo": { "kind": "pier", "lat": 0.0, "lng": 0.0, "source": "wd", "qid": "Q…", "osm": null, "last_verified": "2026-10-15" },
  "wd_geo": { "lat": 0.0, "lng": 0.0 },
  "address_local": { "text": "…", "source": "wat_arun_official", "last_verified": "2026-10-15" },
  "access": { "modes": ["boat", "metro"], "nearest_ko": "짜오프라야 수상버스 왓 아룬 선착장",
              "nearest_local": { "text": "(현지어 라벨)", "qid": "Q…", "source": "wd" },
              "source": "chaophraya_express", "last_verified": "2026-10-15" },
  "official_url": "https://…",
  "summary_ko": "짜오프라야강 서쪽 강가에 있는 사원이에요.",
  "body_ko": ["…", "…"],
  "claims": [ { "id": "c1", "text_ko": "…", "source": "…", "last_verified": "2026-10-15" } ],
  "tips_ko": [ { "text": "어깨와 무릎을 가리는 옷을 입어야 해요.", "source": "wat_arun_official", "last_verified": "2026-10-15" } ],
  "facts": { "kind": "facility", "entry": "paid", "booking": "none", "booking_note_ko": null,
             "regular_closed": ["none"], "closed_note_ko": null, "visit_note_ko": null,
             "source": "wat_arun_official", "last_verified": "2026-10-15" },
  "volatile": null,
  "seasonal": [],
  "risk": [],
  "status": { "value": "open", "note_ko": null, "source": "wat_arun_official", "last_verified": "2026-10-15" },
  "advisory": { "level": "1", "source": "mofa_th", "last_verified": "2026-09-28" },
  "rank": { "order": 3, "designations": [ { "kind": "unesco", "name_ko": "…", "source": "unesco" } ] },
  "photo": null,
  "photo_link": null,
  "source": "wd",
  "last_verified": "2026-10-15"
}
```

**필드 규칙**
- `names`
  - `ko`는 편집 판단(`ko_basis: "editorial"`)으로 한국 여행자가 많이 쓰는 표기를 쓴다. 국립국어원 표기는 별칭으로 둔다. 기준표는 `docs/attractions/NAMES_KO.md`다. **CN은 ⟦결정 D20⟧**(기본 A: `ko` = 현지음, `ko_paren` = 한자음 → 화면 '장자제(장가계)', 검색은 둘 다).
  - `local`은 Wikidata 현지어 라벨이나 공식 표기만 쓴다. **LLM 음역은 금지한다.** **언어 코드 폴백은 고정한다**: CN `zh-hans → zh-cn → zh`(간체 확인), TW `zh-hant → zh-tw → zh`(번체 확인), JP `ja`, VN `vi`(성조 없는 라벨이면 경고), TH `th`, MY `ms → en`, ID `id`, PH·SG `en`. `zh` 폴백을 쓴 경우 wikidata_fill이 간·번체 판별 사전(OpenCC 데이터, 운영자 PC 의존성)으로 대조해 맞지 않으면 경고한다.
  - `local_short`: Wikidata 현지어 별칭 가운데 짧은 통용명이나 공식 약칭이다. 넓은 곳은 방문 지점 이름을 쓴다. **TH는 published에서 필수**다. 찾지 못하면 `local_short: null` + CSV '짧은 현지명 없음 ✔'을 남기고, 이때 기사님 버튼은 숨긴다(긴 왕실 정식명을 기사님께 보여 주지 않기 위해서다).
  - `local_lang`은 나라별로 고정한다(TH th, JP ja, VN vi, CN zh-Hans, TW zh-Hant, ID id, MY ms 또는 en, PH en, SG en).
- **별칭 규칙**: `aliases_ko/en`에는 **같은 방문 지점(geo에서 2km 안, 같은 입구)을 부르는 다른 이름만** 넣는다. 다른 입구·구간·항구는 별도 관광지로 두거나 `mentions_ko[]`에 넣는다. mentions는 검색 점수 10이고 이유 줄은 '설명에 나와요: …'다. 기사님 카드와 지도에는 쓰지 않는다.
  - lint: 별칭 후보가 다른 QID의 라벨과 같고 그 좌표가 2km 넘게 떨어져 있으면 경고한다. mentions와 aliases에 같은 낱말이 있으면 실패한다. **별칭이 정규화한 뒤 이름이나 다른 별칭과 같으면 경고한다**(중복 별칭. 공백 제거형은 정규화가 처리하므로 넣지 않는다).
- `geo`
  - `kind` ∈ `site`/`entrance`/`pier`/`visitor_center`/`station`. nature·sea_island 전부와 넓은 heritage는 방문 지점이 필수다(site면 경고).
  - **좌표 출처 순서(고정)**: ① 방문 지점의 Wikidata 항목(P625, CC0, `qid` 필수) → ② 관리기관이 공개한 숫자 좌표(`use: open_data`, 라이선스 기록) → ③ OSM 노드(`osm:"node/123"` 필수, 나라당 50개를 넘으면 경고) → ④ Google·네이버·카카오 지도에서 복사하는 것은 금지.
- **CN 규칙**: published CN 항목은 `address_local`(공식 중문 주소)이 필수다. 상세의 지도 연결은 좌표 대신 이름으로 검색하게 넘긴다. 이동하기 고르기에는 좌표를 넘기지 않는다.
- `access`: `modes` ⊂ `train`/`metro`/`bus`/`boat`/`car_only`/`walk_from_center`. `nearest_ko`는 운영사 노선도로 확인되는 이름만 쓴다. `nearest_local`(선택, qid 필수). 소요 시간 숫자는 넣지 않는다.
- `summary_ko`: 한 문장, 40자 이하. `body_ko`: 2~4문장, 합계 300자 이하. 직접 쓴 글이어야 한다.
- `claims[]`: 본문 속 검증 가능한 사실(연도·높이·개수·최상급·지정·유래·배경·건립 주체)이다. 출처는 §5.1 허용표의 `facts`·`heritage_registry`·UNESCO, 그리고 **유효 참조가 달린 Wikidata statement**(§5.1)만 쓴다.
- `tips_ko`: 최대 4개, 출처 필수.
- `facts`
  - `kind` ∈ `facility`(기본) / `public_space`. public_space는 category가 market_street·city_view·sea_island·nature일 때만 쓸 수 있다. public_space이면 `entry`·`booking`·`regular_closed`와 facts의 출처를 생략할 수 있고, 상세에는 '누구나 다닐 수 있는 곳이에요'를 보여 준다. 여는 요일이 의미 있는 야시장이면 regular_closed를 넣고 지자체 관광 포털을 출처로 쓴다.
  - `entry` ∈ `free`/`paid`/`unknown`, `booking` ∈ `none`/`recommended`/`required`. facility에서 `entry: unknown`이면 그 줄을 그리지 않는다.
  - `regular_closed` ⊂ `mon`~`sun`/`none`/`irregular`/`unknown`. 공식 페이지에서만 가져오고, 상세 '가기 전에 알아 둘 것'의 첫 줄에 둔다.
  - `visit_note_ko`(선택): 공식 안내가 관람 시간을 직접 밝힐 때만 쓴다.
- `volatile`(⟦결정 D4⟧가 숫자 표시일 때만, 아니면 null): `{ hours_ko, special_closed_ko, fee{kind fixed|tiered|dynamic|free, items[{amount,currency,who_ko}], as_of}, source, last_verified }`.
- `seasonal[]`: `{kind: closed|open_only, text_ko, source, last_verified}`.
- `risk[]` ⊂ `volcano`/`post_disaster`/`seasonal`/`renovation`. volcano이면 `use: hazard` 출처가 붙은 status나 tip이 필수다. 큐레이터 체크리스트 '화산 근처인가'에는 아궁(브사키)·바투르(킨타마니)·따알(타가이타이)·하코네·후지를 적어 둔다.
- `status.value` ∈ `open`/`partial`/`temp_closed`.
- `advisory`: 필수. `level` ∈ `"none"`,`"1"`,`"2"`,`"special"`,`"3"`,`"4"`. TW·SG는 none만, JP는 기본 none이다. special·3·4이면 빌드가 실패한다. `source`는 pack safety 섹션의 source와 같아야 하고, 값은 §5.6 판정 이상이어야 한다.
- `id`: `^[a-z0-9]+(-[a-z0-9]+)*$`, 나라 안에서 유일하다. 바꾸지도 재사용하지도 않는다. 전역 키는 `"<CC>/<id>"`다.
- `rank.designations[]`: 공식 지정만 넣는다. `rank.order`: CSV에서 계산한 정수다(⟦결정 D11⟧).
- `photo`(⟦결정 D3⟧ B·C): `{ file, sha256, title, author, license, license_url, source_url, changes }`. `photo_link`(A): Commons URL(선택).

**확인 주기(등급)**
| 등급 | 필드 | 주기 | 경고 / 실패(서명 시점) |
|---|---|---|---|
| 안 바뀜 | id, qid, names, aliases, mentions, region, area, category, geo, official_url, summary/body, claims, rank | 365일 | 365 경고 / 540 실패 |
| 가끔 바뀜 | facts, tips, tags, address_local, access, seasonal, advisory, status(open·risk 없음) | 180일 | 180 경고 / 365 실패 |
| 자주 바뀜 | status(open이 아니거나 risk 있음) | 30일 | 30 경고 / 60 실패 |
| 숫자 | volatile(D4) | 90일 | 90 경고 / 180 실패(앱은 180일이 넘으면 숨김) |
- 실패는 **서명할 때만** 낸다. `--check`·`--verify-committed`에서는 경고만 하고, `retire`에서는 검사하지 않는다.

### 4.5 Retired
```json
{ "id": "old-place", "reason": "merged", "replaced_by": "grand-palace", "note_ko": "다른 항목으로 합쳤어요.", "source": "editorial", "date": "2026-11-01" }
```
- `reason` ∈ `closed`/`long_closure`/`safety`/`editorial`/`merged`. merged이면 `replaced_by`가 필수이고, 그 id는 현재 attractions에 있어야 한다(retired를 가리키면 실패, 연쇄는 1단계까지만).
- 빠진 항목은 영구히 남기고 id를 다시 쓰지 않는다.

### 4.6 크기 예산
- 1곳 4.5KB 이하, 나라 파일 250KB 이하, 9개국 합계 1.8MB 이하(UTF-8 실측, 넘으면 서명 실패). gzip 전송, `HttpPackRemote` MAX_BYTES(2MB) 안.

### 4.7 작업본(drafts) 규칙
- `release: "draft"`만 허용하고 `wave`를 쓸 수 있다. CI는 `--check`로 스키마와 lint를 돌리고, 날짜와 게이트는 경고만 한다.
- `promote <CC> --wave N --kid …`: drafts에서 wave로 거르고, wave 필드를 지우고, upcoming_regions·excluded_areas·unmapped 공항을 계산하고, release를 published로 바꾸고, advisory_basis를 계산한 뒤 게이트와 copycheck를 돌린다. 성공하면 src와 assets를 함께 쓰고, 실패하면 둘 다 그대로 둔다.

### 4.8 앱 모델: 관대하게 읽기
- enum 성격의 필드는 모두 `String`으로 읽는다. `Json { ignoreUnknownKeys = true }`. 필수는 `doc_type`·`regions`·`attractions`뿐이다.
- `AttractionsMapper`
  - 모르는 tag·modes·risk·요일은 숨긴다. 모르는 facts.kind는 facility로 본다.
  - 모르는 category는 목록·검색에서 빼고, 찜 목록에서는 '앱을 업데이트하면 볼 수 있어요'로 보여 준다.
  - 모르는 status는 open이 아닌 것으로 보고 '공식 사이트에서 확인하세요' 띠를 단다.
  - advisory가 special·3·4이면 목록·검색에서 숨기고, 찜 목록에서 '안전 문제로 확인 중이에요'로 보여 준다. 모르는 level은 보여 주되 Caution '여행경보를 꼭 확인하세요 — 외교부 0404'를 붙인다.
  - 모르는 region.kind는 base로, 모르는 retired.reason은 editorial로 본다.
- **enum 단일 출처**: `packs/schema/attractions.enums.json`. Python lint가 이 파일을 읽는다. Kotlin 테스트 `EnumsContractTest`는 이 파일과 앱 상수(종류·태그 라벨 맵 등)가 같은지, 모든 category·tag에 문자열 키가 있는지 대조한다.

### 4.9 debug 샘플
- 서명하지 않은 JSON을 `app/src/debug/assets/attractions_samples/<CC>.json`에 둔다.
- **`com.readyport.attractions.AttractionsFallback`** 객체를 `app/src/debug/java`(샘플 assets를 읽음)와 `app/src/release/java`(언제나 null)에 같은 이름으로 둔다(AppCheckInstaller 선례). main의 `AttractionsRepository`는 '서명본 없음 → `AttractionsFallback.read(cc)`'만 부른다. Hilt 모듈은 main 하나만 둔다.
- 최상위 `"sample": true`이면 화면 맨 위에 '샘플 데이터' 띠를 보여 준다(debug만). 이름은 누가 봐도 가짜('샘플 사원 1')여야 하고 출처 id는 `sample`만 쓴다. 실제 이름·요금·연도·경보는 금지한다.
- 테스트 픽스처(`src/test`)도 가짜 이름을 쓴다. 게이트 픽스처는 **실제 후보 목록의 지역별 곳 수와 경보 단계 구조만** 따라 만든다(이름은 가짜).

---

## 5. 출처·라이선스·검증

### 5.1 출처 허용표 (`docs/attractions/SOURCES_POLICY.md`)
| 출처 | 허용 | 쓰는 범위 |
|---|---|---|
| Wikidata (CC0) | ✅ | QID, P625, P856, P1435, 현지어 라벨·별칭, P18. **claims 출처로는 '유효 참조'가 있는 statement만** 쓴다 |
| 공식 시설·관광청·교통 운영사·선사 | ✅ 사실만 | 시간·요금·정기 휴관·예약·복장·상태·역·노선. 문장은 가져오지 않는다 |
| 나라별 공식 문화재 DB(`heritage_registry`, license 필수) | ✅ 사실만 | 건립 연도·건립 주체·지정 사실. JP 문화청 DB(정부표준이용규약 2.0, 출처 표기) / CN 国家文物局·5A 목록 / TH 예술국 / VN 문화유산국 / TW 文化部 國家文化資產網(政府資料開放授權條款, 출처 표기) / MY Jabatan Warisan Negara / ID Cagar Budaya / PH NHCP / SG Roots.sg / UNESCO |
| 화산·재해·국립공원 기관(PHIVOLCS, MAGMA, JMA) | ✅ 사실만 | status·seasonal·risk(`hazard`) |
| 지자체 관광 포털 | ✅ 사실만 | public_space의 여는 요일·시간 |
| 대한민국 대사관·총영사관 공지 | ✅ 사실만 | tips(바가지·사기·안전 주의) |
| 정부 오픈데이터 | 조건부 | 라이선스를 기록한 뒤 `open_data` |
| 공공누리 | 조건부 | 사실은 유형과 관계없이 쓸 수 있다. 문장·사진 재사용은 1유형만 |
| OpenStreetMap (ODbL) | ⚠ 좌표 3순위 | 노드 ID 기록, 출처 표기, 비실질 추출 범위(§5.8) |
| 0404.go.kr | ✅ | 여행경보 단계와 구역 원문 |
| Natural Earth (PD) / geoBoundaries gbOpen (CC BY 4.0) | ✅ | 경계(§5.6) |
| GADM | ❌ | 비상업·재배포 금지 라이선스라 쓰지 않는다 |
| Wikimedia 조회수 통계 | ✅ CSV만 | 선정 지표 |
| 네이버 검색광고 키워드 조회수 | ⚠ CSV 참고 열만 | API 약관을 확인한 뒤에만 쓴다 |
| Wikipedia / Wikivoyage (CC BY-SA) | ⚠ 참고만 | 후보 발굴용. 문장 번역 금지(⟦결정 D12⟧). 사실의 단독 출처로 쓰지 않는다 |
| 나무위키 | ❌ | copycheck 비교용 스냅샷만(수동 저장, 저장소 밖) |
| 블로그·카페, Google Places, 네이버·카카오 지역 API, 지도 앱 좌표, 여행사, 리뷰, 가이드북 | ❌ | 쓰지 않는다 |

**Wikidata 유효 참조 정의**(wikidata_fill이 statement마다 판정해 CSV에 기록)
- 유효: 참조 블록에 ① **P248(stated in)** 이 있고 그 대상이 위키미디어 프로젝트·위키백과 언어판(P31이 Q14827288 또는 Q10876391 계열)이 아니거나, ② **P854(reference URL)** 의 도메인이 `*.wikipedia.org`·`*.wikimedia.org`·`wikidata.org`가 아닐 때.
- 무효: P143(imported from)·P4656(Wikimedia import URL)·P887(based on heuristic)·P3452(inferred from)만 있는 참조. 이런 statement는 '참조 없음'으로 본다.
- CSV에는 `wd_lastrevid`와 함께 '유효 참조 속성·대상(QID 또는 URL)'을 적는다. claims 검사 ⑥은 이 기록이 유효일 때만 통과시킨다. 참조 URL은 가능하면 evidence 스냅샷으로도 저장한다.

### 5.2 편집 원칙 (`docs/attractions/EDITORIAL_RULES.md`)
1. 개별 식당·카페·마사지숍·투어 업체(코끼리 보호 시설 포함)는 넣지 않는다(⟦결정 D13⟧).
2. **동물**(⟦결정 D19⟧ A안): (가) 동물 먹이 주기·쇼·타기·안기를 빼도 방문 이유가 남으면 넣고, 그 행위는 tips에 관리기관 안내로만 적는다. (나) 남지 않으면 뺀다. 운영 주체는 판정에 쓰지 않는다. CSV에 동물원·수족관 협회 가입 여부를 보조 근거로 남긴다.
3. 여행경보 3·4단계·특별여행주의보 구역은 넣지 않는다. 2단계는 ⟦결정 D15⟧를 따른다.
4. 순서는 수수료·제휴와 무관하다. v1에는 제휴 링크가 없다(⟦결정 D10⟧).
5. 대표 이름은 한국 여행자가 통용하는 표기로 쓴다. CN은 ⟦결정 D20⟧을 따른다.
6. 대형 쇼핑몰은 ⟦결정 D14⟧를 따른다.
7. **금지어**(summary·body·tips·visit_note): '가장 아름다운', '최고의', '꼭 가야 할', '필수 코스', '숨은 명소', '현지인이 사랑하는', '인생샷', '대표적'.
8. 영화·드라마 연관은 제작사나 공식 관광청이 밝힌 경우만 쓴다.
9. 정치적으로 민감한 지명·사적지(⟦결정 D18⟧)는 위치·운영·예약 사실만 쓴다.
10. 별칭은 같은 입구만 허용한다.
11. '최초'·'유일'은 claims 출처 원문이 같은 뜻을 직접 밝힐 때만 쓰고, CSV에 근거 문구 위치를 적는다.
12. 상설 공연장은 ⟦결정 D21⟧을 따른다.
13. **상세 최소 충실도**: 다음을 모두 갖춰야 한다. ① body 2문장 이상(claims 근거) ② '가기 전에 알아 둘 것' 2줄 이상(정기 휴관·복장·예약·입장 유무·seasonal·tips·public_space 문장 중) ③ access의 modes와 nearest_ko ④ official_url 또는 공식 근거 링크. 미달이면 published에서 경고하고, 나라의 미달 항목이 10%를 넘으면 실패한다.
14. **DB 추출 한도**: 한 비공개 DB(관광청 목록·포털)에서 한 나라 관광지 사실의 30%를 넘게 가져오지 않는다. 사실은 각 시설의 공식 페이지에서 뽑는다. curation_report가 출처 도메인 비율을 경고한다(한국 저작권법 제93조 데이터베이스제작자 권리 고려).

### 5.3 제작 5단계
1. **후보**: `packs/curation/<CC>.csv`(저장소 안, 앱에는 싣지 않음). 후보원은 Wikidata SPARQL, 유네스코·각국 지정 목록, 관광청 대표 명소 목록(이름만), 한국발 직항 도시, (D18-A) 국외 독립운동 사적지 목록(이름·위치만)이다.
   - attractions 시트 열: id, qid, 후보 근거, 지정, 한국어·영어 위키백과 12개월 조회수, sitelink 수, 선정 근거 지표, 판정 종류·번호, 지역, geo 출처 순서, 검증자, 확인일, 'claims만 ✔', 근거 스냅샷 해시, 짧은 사실값, wd_lastrevid, **유효 참조 속성·대상**, 최상급 근거 문구 위치, 동물 협회 가입, 나무위키 문서 없음 ✔, 상한 초과 사유, **짧은 현지명 없음 ✔**, **인용 해시 목록**, 탈락 사유.
   - regions 시트 열: id, kind, base_regions, wave, **1차 선정 근거**, **hub 선택 근거**, 당일 왕복 근거 URL·확인일, 경보 구역 대조 결과, 40km 초과 유지 근거, **묶음 별칭 구성 지명**.
   - sources 시트 열: source id, URL, license, page_watch 추출 규칙, 자동 감시 가능 여부.
   - **선정 규칙**: 지역별로 ① 지정 → ② 한국어 조회수 → ③ 한국어 문서가 없거나 조회수 0이면 '영어 조회수 × 나라 보정 계수'와 sitelink 수 순서. `rank.order`는 이 순서로 매긴다.
   - **CSV 저작권 규칙**: 원문 문장을 복사하지 않는다. curation_report가 40자 넘는 연속 문장을 경고한다.
2. **자동 채움**: `wikidata_fill.py`가 QID로 좌표·라벨(폴백 순서 §4.4)·별칭·P856·P1435·P18을 가져오고, 유효 참조 여부와 lastrevid를 기록하고, 별칭 QID 충돌을 조회한다.
3. **사실 채움**: 공식 페이지·문화재 DB의 텍스트 스냅샷을 **저장소 밖** `~/.readyport/evidence/<CC>/<id>/<source>-<date>.txt`에 둔다.
   - **인용 대조(기술 기본값, D23과 무관하게 적용)**: LLM이나 사람이 뽑은 사실은 `~/.readyport/evidence/<CC>/<id>/extract.json`에 `{field, value, quote, source, snapshot}`로 저장한다. `curation_report.py --verify-quotes`가 quote가 해당 스냅샷에 **글자 그대로**(공백만 정규화) 들어 있는지 확인하고, 없으면 그 사실 행을 거부한다. quote 원문은 저장소에 넣지 않고 CSV에는 sha256만 남긴다. 사람은 '값이 인용의 뜻과 맞는지'를 보고 검증자·확인일을 채운다(대조 범위는 ⟦결정 D23⟧).
   - LLM 기억으로 사실을 채우는 것은 금지한다.
4. **설명 작성**: LLM에는 확인을 마친 claims·facts 표만 준다. 원문 글은 어떤 언어로도 주지 않는다. 직접 고쳐 쓴 뒤 'claims만 ✔'을 표시한다.
5. **lint·copycheck·promote**(§10).

**공수(현실치, D23-A 기준)**: 지역당 20분 + 관광지는 첫 나라 곳당 60~75분, 이후 45~55분(copycheck 수동 저장 포함). 계산식 = 1차 지역 수 × 20분 + 곳 수 × 곳당 시간.
- D1-A(깊게, JP·VN·TH) + D25-A(나트랑 포함): 약 27지역·약 105곳 → **약 100~120시간**. D23-B이면 약 60~70시간(첫 나라에서 실측해 다시 보고).
- 9개국 1차(약 57지역·약 235곳): D23-A 약 200~230시간, D23-B 약 120~140시간.
- ⟦결정 D24⟧ A이면 일본 시범 5곳(약 5~6시간)이 이 시간에 포함된다(데이터를 그대로 1차에 씀).

### 5.4 copycheck (`docs/attractions/COPYCHECK.md`)
- 캐시 위치는 `~/.readyport/copycheck_cache/<CC>/<id>/`이고, 파일마다 머리 메타 `{collected_via: mediawiki_api|dump|manual_browser_save, url, date}`를 둔다.
- 한국어 위키백과는 MediaWiki API(`action=parse`, 연락처가 든 User-Agent)나 덤프로만 가져온다. 나무위키는 **수동 저장만** 허용한다(자동 수집 금지). 문서가 없으면 CSV에 '나무위키 문서 없음 ✔'을 남긴다. 공식 관광청 한국어 페이지는 수동 저장한다. evidence 스냅샷도 비교 대상이다.
- 필수 묶음: evidence + (있으면) 한국어 위키백과 + (있으면) 나무위키 + (있으면) 관광청 한국어 페이지.
- 실패 기준: 8어절 연속 일치가 1곳 이상이거나, 8글자 n-gram 겹침이 30%를 넘을 때.
- **결과 기록**: `packs/curation/<CC>.copycheck.json`(커밋)에 `{id: {text_sha256: sha256(summary+body+tips+visit_note), checked_at, cache_set_sha256}}`를 남긴다. sign·promote는 **text_sha256이 바뀐 항목만** 캐시로 다시 검사하고, 바뀐 항목의 캐시가 없으면 서명을 거부한다. 글이 그대로인 재서명(advisory_basis 갱신 등)에는 캐시가 필요 없다.
- `~/.readyport/evidence`와 `~/.readyport/copycheck_cache`는 ARIA 주간 백업 대상에 넣는다(`~/.readyport/keys`는 제외). ARIA_OPS에 이 내용을 적는다.

### 5.5 신선도 표시(앱)
- 관광지 확인일은 나라 머리글 `latestVerified()`에 넣지 않는다.
- `volatile.last_verified`가 180일을 넘으면 숫자를 숨기고 '확인한 지 오래됐어요 — 공식 사이트에서 확인하세요'를 보여 준다.
- status가 open이 아니면 언제나 '(확인일 …)'을 붙인다.
- 상세 SourceFooter의 '최종 확인'은 이 관광지 필드 가운데 가장 오래된 날짜로 한다.

### 5.6 여행경보 — 빌드 쪽 기하 검사
- 관광지·지역 `advisory.level`이 3·4·special이면 실패한다.
- **구역 모델** `packs/curation/advisory_zones.json`(커밋)
```json
{
  "meta": { "natural_earth": "5.1.x", "geoboundaries": "gbOpen <build id>", "built": "2026-10-…",
            "attribution": "Natural Earth (public domain); geoBoundaries (CC BY 4.0)" },
  "countries": {
    "PH": { "default": "2", "source": "mofa_ph",
            "mofa": { "url": "https://www.0404.go.kr/…", "notice_date": "…", "place_names": ["보라카이섬", "보홀섬", "막탄섬", "라푸라푸시", "수빅시", "…"] },
            "raise": [ { "zone": "ph_palawan_south", "name_ko": "팔라완 남부", "aliases_ko": [], "kind": "admin", "dataset": "gb_ADM3", "ids": ["…"], "level": "3", "mofa_names": ["…"] },
                       { "zone": "ph_mindanao_l4", "name_ko": "민다나오", "aliases_ko": [], "kind": "admin", "dataset": "gb_ADM2", "ids": ["…"], "level": "4", "mofa_names": ["…"] } ],
            "lower_exception": [ { "zone": "ph_boracay", "name_ko": "보라카이섬", "kind": "circle", "center_qid": "Q…", "km": 4, "level": "1", "mofa_names": ["보라카이섬"] },
                                 { "zone": "ph_bohol_main", "name_ko": "보홀섬", "kind": "admin", "dataset": "gb_ADM3", "ids": ["(보홀 본섬 자치시 목록 — 팡라오·다우이스와 섬 자치시 제외)"], "level": "1", "mofa_names": ["보홀섬"] },
                                 { "zone": "ph_lapulapu", "name_ko": "막탄섬(라푸라푸시)", "kind": "admin", "dataset": "gb_ADM3", "ids": ["Lapu-Lapu City"], "level": "1", "mofa_names": ["막탄섬", "라푸라푸시"] },
                                 { "zone": "ph_subic_municipality", "name_ko": "수빅시", "kind": "admin", "dataset": "gb_ADM3", "ids": ["Subic"], "level": "1", "mofa_names": ["수빅시"] } ] },
    "MY": { "default": "1", "raise": [ { "zone": "my_sabah_east_coast", "name_ko": "사바주 동부해안", "kind": "admin", "dataset": "gb_ADM2", "ids": ["(원문 군 목록)"], "level": "2" },
                                      { "zone": "my_sabah_east_islands", "name_ko": "사바주 동부 섬", "kind": "admin", "dataset": "gb_ADM2", "ids": ["(원문 지명)"], "level": "3" } ] },
    "TH": { "default": "1", "raise": [ { "zone": "th_tak", "name_ko": "딱", "kind": "admin", "dataset": "ne_admin1", "ids": ["Tak"], "level": "2" },
                                      { "zone": "th_deep_south", "name_ko": "빠따니", "aliases_ko": ["나라티왓", "얄라", "송클라"], "kind": "admin", "dataset": "ne_admin1", "ids": ["Pattani","Narathiwat","Yala","Songkhla"], "level": "3" },
                                      { "zone": "th_kh_border_50km", "name_ko": "캄보디아 국경 지역", "kind": "buffer", "line": "TH-KH", "km": 50, "level": "3" } ] },
    "CN": { "default": "1", "raise": [ { "zone": "cn_tibet_xinjiang", "name_ko": "티베트", "aliases_ko": ["시짱", "신장", "신장위구르"], "kind": "admin", "dataset": "ne_admin1", "ids": ["Xizang","Xinjiang"], "level": "special" } ] },
    "JP": { "default": "none", "raise": [ { "zone": "jp_fukushima_30km", "name_ko": "후쿠시마 원전 주변", "kind": "circle", "center_qid": "Q…", "km": 30, "level": "3" },
                                        { "zone": "jp_fukushima_evac", "name_ko": "후쿠시마 피난 지시 구역", "kind": "admin", "dataset": "gb_ADM3", "ids": ["Namie","Futaba","Okuma","Tomioka","Iitate","Katsurao","Minamisoma"], "level": "3" } ] },
    "ID": { "default": "1", "raise": [ { "zone": "id_papua", "name_ko": "파푸아", "aliases_ko": ["서파푸아", "라자암팟"], "kind": "admin", "dataset": "gb_ADM1", "ids": ["Papua","Papua Tengah","Papua Pegunungan","Papua Selatan","Papua Barat","Papua Barat Daya"], "level": "2" },
                                      { "zone": "id_maluku", "name_ko": "말루쿠", "kind": "admin", "dataset": "gb_ADM1", "ids": ["Maluku","Maluku Utara"], "level": "2" },
                                      { "zone": "id_aceh", "name_ko": "아체", "kind": "admin", "dataset": "gb_ADM1", "ids": ["Aceh"], "level": "2" } ] },
    "TW": { "default": "none" }, "SG": { "default": "none" }, "VN": { "default": "1" }
  }
}
```
- **0404 원문 기준**: 구역은 팩 요약문이 아니라 **0404 원문 공지**에서 만든다. 나라마다 `mofa.url`·`notice_date`·`place_names`(원문 지명 목록)가 필수다. `advisory_zones.py extract`와 build lint는 원문 지명이 모두 어느 구역의 `mofa_names`에 대응하는지 1:1로 대조하고, 빠진 것이 있으면 실패한다. 원문이 '일부'라고만 하면 그 행정구역 전체를 보수적으로 넣는다(JP 미나미소마 전체, TH 송클라 전체, ID 북말루쿠 포함).
- **판정 단계** = 들어 있는 raise 구역 가운데 최고 단계. 어느 raise에도 없으면 들어 있는 lower_exception의 단계, 그것도 없으면 default. 단계 순서는 none < 1 < 2 < special = 3 < 4.
- **실패 조건**: 관광지 `advisory.level`이 그 geo의 판정 단계보다 낮을 때, 지역 hub도 마찬가지, 판정 단계가 3·4·special인 위치에 관광지가 있을 때. lower_exception으로 판정이 낮아진 관광지는 CSV '경보 구역 대조 결과'에 근거가 없으면 실패. 경계에서 ±5km 안이면 경고하고 CSV 근거가 없으면 실패.
- **보홀**: 0404 원문이 '보홀섬'이므로 lower_exception은 본섬 자치시 목록으로만 정의한다. 팡라오섬(알로나 비치)은 0404 원문 근거(공지 문구·지도)를 CSV에 남기고 구역 PR을 거친 뒤에만 1단계로 둔다. 그전에는 2단계(D15)로 본다.
- **수빅**: 프리포트 관광지는 수빅 자치시 밖이라 기본 2다. 1로 두려면 0404 근거로 구역 PR을 낸다.
- **도구 분리**: `tools/attractions/advisory_zones.py extract`(운영자 PC, pyshp/shapely)가 필요한 경계만 단순화해 JSON으로 커밋한다. 판정은 build_attractions 안의 순수 표준 라이브러리 구현(하버사인, ray-casting, 원·버퍼)으로 한다. CI unittest는 작은 고정 폴리곤 픽스처로 돌린다.
- **교차 검사**: `advisory_basis.advisory_sha256`이 지금 pack의 경보 문단 해시와 다르면 경고하고 서명할 때 자동으로 갱신한다. pack safety `last_verified`가 basis보다 30일 넘게 새롭고 해시도 다르면 서명 실패(재대조 강제).
- **watch 기준선 검사**: 서명할 때 지금 pack의 경보 문단 가운데 `isHighAdvisory`가 참인 문단에 어떤 지역의 `advisory_watch_ko`가 나오면 실패한다.
- `packs/schema/advisory_rules.json`이 단일 출처다: `{ "advisory_paragraph_regex": "[1-4]단계|특별여행|여행경보는 없|내려진 여행경보|여행금지|출국권고|여행자제|여행유의", "high_words": [ … ] }`. Python은 이 파일을 읽고, Kotlin 테스트는 앱 상수와 이 파일이 같은지 대조한다.

### 5.7 여행경보 — 실행 시 규칙 (RegionGrouping)
- **경보 문단 해시 `Advisory.advisoryHash(pack)`**(Python `advisory_hash` 동일 구현)
  1. `pack.sections` 가운데 `id == "safety"`인 **첫 항목**을 고른다. 없으면 빌드는 실패하고, 앱은 '해시 없음'으로 본다.
  2. `body_ko`의 원소(문단) 가운데 `advisory_paragraph_regex`에 걸리는 것만 고른다(지금 팩 기준 TW 1·SG 1·VN 1·JP 1·CN 2·TH 3·MY 2·ID 2·PH 4). 끝 문단 '여행경보는 자주 바뀌어요…'는 걸리지 않는다.
  3. 각 문단을 NFC로 바꾸고, 공백 연속을 공백 하나로 줄이고, 앞뒤를 자른다. 그다음 `"\n"`으로 이어 UTF-8 sha256(소문자 hex)을 낸다.
  4. 공용 테스트 벡터 3개(`packs/schema/advisory_hash_vectors.json`)를 Python·Kotlin 테스트가 함께 쓴다.
- **표시**
  | level | 표시 |
  |---|---|
  | none, 1 | 그리지 않는다 |
  | 2 | 지역 안이 모두 2이면 지역 머리 아래 한 번 `AdvisoryLevelNote`. 섞여 있으면 2인 줄에만 StatusTag(Caution) '여행경보 2단계'. 상세는 제목 아래에 언제나 |
  | special·3·4 | 숨긴다 |
  | 모르는 값 | Caution '여행경보를 꼭 확인하세요 — 외교부 0404' |
  - `AdvisoryLevelNote(level, source, verified)` = `NoticeBanner(tone = Caution, icon = ReportProblem)` + '이 지역은 여행경보 2단계(여행자제)예요' + SourceList '외교부 해외안전여행 · 확인일 …'. AdvisoryBanner(Danger)는 쓰지 않는다.
- **pack과 관광지의 경보가 다를 수 있을 때 — 버전 방향으로 판정한다**(기기 pack version vs `advisory_basis.pack_version`, `PackVersion.compare`)
  - (a) **기기 pack < basis**: 관광지가 더 새 경보를 반영해 서명된 것이다. 관광지 쪽 단계 표시를 그대로 보여 주고 안내 줄은 없다. 그 나라를 PackSync 다음 주기 대상에 넣는다.
  - (b) **같음**: 아무것도 하지 않는다.
  - (c) **기기 pack > basis이고 경보 문단 해시가 다를 때만**:
    1. 관광지 쪽 단계 표시(2단계 머리·줄 태그·상세 띠)를 숨긴다.
    2. 목록·상세 맨 위에 Caution 한 줄 '여행경보가 바뀌었을 수 있어요 — 나라 안내의 안전 정보를 확인하세요 (확인일 …)' + `ListRow('안전 정보 보기 ›')`(§6.4)를 둔다.
    3. pack 경보 문단 가운데 `isHighAdvisory`가 참인 문단에 어떤 지역의 `advisory_watch_ko` 낱말이 나오면, 그 지역 관광지를 목록·검색에서 숨기고 찜 목록에서는 '안전 문제로 확인 중이에요'로 보여 준다. 이름·별칭·group_ko는 매칭에 쓰지 않는다.
  - (c)에서 해시가 같으면 1~3을 모두 하지 않는다. 생활 안내 문단을 고치거나 날짜만 재확인한 경우가 여기에 해당하며, 거짓 경고는 0이다.
  - 기기 pack에 safety가 없으면(해시 없음) (c)의 '해시 다름'으로 보되, 버전 방향 조건은 그대로 적용한다.
- 이 규칙에서는 나라 화면이 언제나 기기의 최신 pack을 보여 주므로, '안전 정보 보기'로 따라간 화면과 목록의 안내가 서로 어긋나지 않는다.

### 5.8 데이터 라이선스 고지 (`packs/DATA_LICENSES.md`)
- attractions.json: Wikidata 값은 CC0이다. `source=osm` 좌표는 OSMF Substantial Extract 가이드라인상 **비실질 추출 범위**(나라당 100개 미만, 체계적 반복 추출 아님)에서만 쓰고, 출처 표기 '일부 위치 © OpenStreetMap 기여자(ODbL)'를 유지한다. build_attractions는 나라별 osm 좌표가 50개를 넘으면 경고한다. 본문 글은 프로젝트 저작물이다.
- 정부 데이터: 정부표준이용규약 2.0(JP), 政府資料開放授權條款(TW), 공공누리(KR) 등 출처 표기 의무가 있는 출처는 `sources[].license` + `attribution_required`로 표시하고, 상세 SourceList와 설정 › 출처에 기관 이름을 보여 준다.
- advisory_zones.json: Natural Earth(PD)·geoBoundaries(CC BY 4.0) 출처를 표기한다.
- 사진(D3 B·C): 사진마다 TASL을 적는다.
- build_attractions는 osm 좌표가 하나라도 있으면 `settings_credit_osm` 문자열 키가 있는지, attribution_required 출처가 있으면 `settings_credit_<source>` 키가 있는지 확인하고, 없으면 실패한다.

### 5.9 ARIA 운영 (`docs/ARIA_OPS.md`에 추가)
- 월 1회 링크 점검(official_url·출처 URL의 404·도메인 변경을 텔레그램으로 알림, 자동 수정 없음).
- 월 1회 `page_watch.py`: 추출 규칙으로 해당 부분만 떼어 마지막 사람 확인 evidence와 비교한다. 막히면 CDP 실제 Chrome으로 다시 시도하고, 그래도 안 되면 '자동 감시 불가' 목록에 올린다. 변경이 없어도 last_verified는 늘리지 않는다.
- 주 1회: 확인 주기 초과 목록 + `build_attractions.py --cross-check` 결과로 재검토 작업을 만든다.
- 0404 경보 변경 감지 시: 해당 나라 재검토 작업을 만든다. 3·4·special로 오르면 '긴급: `build_attractions.py retire <CC> --ids …` 필요'와 advisory_zones.json 갱신 대상을 함께 알린다.
- 서명 실행 주체는 ⟦결정 D22⟧. 기본(A)에서는 ARIA가 서명 키 암호를 갖지 않는다.
- 주간 백업에 `~/.readyport/evidence`·`copycheck_cache`를 넣는다(키 제외).
- 운영 부담(120곳 기준): 180일 등급 연 약 24시간, risk 대상 30일 확인 연 약 9시간, D4 숫자를 쓰면 연 60~80시간 추가.

---

## 6. 화면과 흐름

### 6.1 들어가는 길 (⟦결정 D2⟧ A안 — 기본 구현)
**여행 정보 갈래의 상태** (`AttractionsAvailability`, 화면에 들어올 때 정하고 고정)
| 상태 | 조건 | 그리는 것 · 자리 |
|---|---|---|
| Available | 기기에 published 파일이 있고 보이는 관광지가 1곳 이상 | 갈래 맨 위 '관광지' 묶음 |
| Downloadable | RC에 버전 있음 · 기기에 없음 · 받기 막힘(와이파이 전용인데 종량제이거나 오프라인) | **갈래 맨 끝** 연한 채움 카드 '관광지 안내를 받을 수 있어요 · 100KB 이하' + [지금 받기] + '와이파이에 연결되면 자동으로 받아요'. 오프라인이면 버튼을 비활성으로 두고 '인터넷에 연결되면 받을 수 있어요' |
| Downloading | 고유 작업이 ENQUEUED·RUNNING | 같은 자리 '받고 있어요'(진행 표시). ENQUEUED가 30초를 넘으면 '연결되면 받아요' |
| Downloaded | 이번 진입에서 받기 성공 | 같은 자리 `ListRow('받았어요 · 관광지 보기 ›')`. 묶음은 다음 진입 때 맨 위에 나타난다(레이아웃 점프 금지) |
| NeedsAppUpdate | 같은 RC에서 UnsupportedSchema | 갈래 맨 끝 Soon 톤 '앱을 업데이트하면 관광지 안내를 볼 수 있어요' |
| NotYet | RC 키 없음, NotFound, 서명·형식 실패, 또는 보이는 관광지 0곳 | 갈래 맨 끝 `ComingSoonGroup('곧 추가돼요' · '관광지')` |
- 고정 규칙: 한 진입 안에서 허용되는 전이는 Downloadable → Downloading → Downloaded(실패하면 원래 상태)뿐이다. CountryViewModel은 관광지 상태를 `revisionOf(cc)`와 WorkInfo 흐름으로 따로 합치고, **CountryUi 재계산을 일으키지 않는다.**
- '와이파이에서만' 설명은 새 키 `explore_wifi_only_desc_v2`를 쓴다.

**Available일 때 갈래 순서**: 1) 3단계 이상 배너(기존) 2) '관광지' 묶음 `AttractionsEntry` 3) sectionGap → `SectionHeader('알아 둘 것')` + 기존 카드(safety 카드 key `section-safety`) 4) `NavMosaic`(그대로).

**종류 타일 구현**: `Tiles.kt`의 `NavTile`을 **internal**로 연다(새 타일 부품 0개). 보이는 종류 가운데 곳 수가 많은 순으로 **최대 5개**를 골라 §2.2 순서로 놓고, `TileGrid(types) { spec, cell -> NavTile(spec, cell) }`로 그린다. 'n곳'은 `TileSpec.supporting`에 넣는다. '모든 종류' 타일(그림 `Illus.Travel`)은 k+1이 짝수면 그리드의 마지막 칸, 홀수면 그리드 아래에 `NavTile(allSpec, row = true)`로 폭 전체에 둔다. 빈 반 칸은 생기지 않는다. **1열**: `NavTile(row = true)` 최대 4줄 + '모든 종류 보기 (n종류 · m곳) ›' 행.

**2열(기본)**
```
┌ 여행 정보 ───────────────────────────────┐
│ ▣ 관광지                                 │ SectionHeader
│   종류를 고르면 유명한 곳을 지역별로 보여 줘요│
│ ┌──────────────────────────────────────┐ │
│ │ 🔍 관광지 찾기                         │ │ 입력칸 모양 Surface(role=Button)
│ └──────────────────────────────────────┘ │ → AttractionsRoute(focusSearch=true)
│ ♥ 찜한 관광지 3곳                      ›  │ 0곳이면 없음
│ ┌────────────┐ ┌────────────┐            │ NavTile(Teal)
│ │ [문루 그림] │ │ [봉우리 그림]│            │
│ │ 역사·유적 6곳│ │ 산·자연 3곳 │            │
│ ├────────────┤ ├────────────┤            │
│ │ [섬 그림]   │ │ [스카이라인]│            │
│ │ 바다·섬 4곳 │ │ 도시·전망 3곳│            │
│ ├────────────┤ ├────────────┤            │
│ │ [노점 그림] │ │ [여행 그림] │            │ k=5 → '모든 종류'가 6번째 칸
│ │ 시장·쇼핑 3곳│ │ 모든 종류 21곳│           │
│ └────────────┘ └────────────┘            │
│ ▣ 알아 둘 것                             │
│   (기존 전기·돈·안전·지도 카드, NavMosaic)│
└──────────────────────────────────────────┘
```
**1열(쉬운 모드·큰 글자)**
```
│ ▣ 관광지                                 │
│ [🔍 관광지 찾기                       ]   │ 64dp
│ [문루48] 역사·유적 · 6곳             ›    │ 최대 4줄
│ …                                        │
│ 모든 종류 보기 (7종류 · 21곳)        ›    │
│ ♥ 찜한 관광지 3곳                    ›    │
```
- 그림 메뉴 라벨 '여행 정보'는 그대로 두고, 홈도 건드리지 않는다(⟦결정 D9⟧).
- `AttractionsEntry`는 위치와 무관한 부품이다. D2가 B·C로 정해지면 호출 위치와 `CountrySection` enum만 바뀐다.

**쇼핑 갈래**: NavMosaic `columns = 1`, 행 두 개 '쇼핑 리스트 보기'(기존) / '물건 사기 좋은 곳'(MarketStall).

### 6.2 라우트
```kotlin
@Serializable data class AttractionsRoute(
    val country: String,
    val category: String? = null,      // null = 모든 종류
    val query: String? = null,         // SavedStateHandle 초기값으로만
    val savedOnly: Boolean = false,
    val focusSearch: Boolean = false,  // 한 번만 소비
    val pickForMove: Boolean = false,
    val scrollToRegion: String? = null, // 한 번만 소비
)
@Serializable data class AttractionDetailRoute(val country: String, val id: String)
// CountryRoute에 추가: val focusSection: String? = null  // "safety"만, 한 번만 소비
```
- **한 번만 쓰는 인자**: ViewModel이 SavedStateHandle에 `consumed_focusSearch`·`consumed_scrollToRegion`을 기록하고, 소비한 뒤에는 무시한다. `previousBackStackEntry.savedStateHandle`로 받은 값은 `remove<String>(key)`로 꺼내 쓴다. 그래서 상세에서 뒤로 돌아와도 키보드가 다시 뜨거나 지역으로 다시 점프하지 않는다.
- 매니페스트 딥링크는 없다. 화면 제목은 '태국 관광지' / '태국 찜한 관광지' / '가는 곳으로 고르기' / 범위를 넓히면 '관광지'.
- **상세 → 상세**('같은 지역의 다른 곳'): `navigate(AttractionDetailRoute(…)) { popUpTo<AttractionDetailRoute> { inclusive = true } }`.
- **'방콕의 다른 곳 모두 보기'**: 백스택 바로 아래가 같은 나라의 `AttractionsRoute`이면 `previousBackStackEntry.savedStateHandle["scrollToRegion"] = "th_bangkok"`을 넣고 `popBackStack()`. 목록은 걸러 보기를 '모든 종류'로, 검색어를 비움으로 풀고 그 지역으로 스크롤한다. 아니면 `AttractionsRoute(country, scrollToRegion = …)`를 연다. 스크롤은 `state.scrollToKey(keyIndex, "region-<id>")`.

### 6.3 목록 화면 — `AttractionsListScreen` (종류·검색·찜·고르기 공용)

**2열(기본)**
```
┌──────────────────────────────────────────┐
│ ← 태국 관광지                             │ AppScreen 머리
│ ┌──────────────────────────────────────┐ │
│ │ 🔍 관광지 찾기                     ✕  │ │ 공용 SearchField (key "search")
│ └──────────────────────────────────────┘ │
│ 6곳 · 지역 3곳                            │ 포커스·검색어 있을 때만
│ [ 걸러 보기: 역사·유적 · 찜한 곳만 끔  ▾ ]│ 48dp, 보이는 종류 ≥4일 때
│   (펼치면) [태국만 보기]* [모든 종류][역사·유적✓]… │ SelectChip FlowRow
│            [♥ 찜한 곳만]                  │
│ 6곳 · 지역이 다르면 이동이 길어질 수 있어요.│ 개수+안내
│ 하루는 같은 지역 안에서 다니면 편해요.    │
│━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━│ 2dp LineStrong
│ 📍 방콕  [오늘 묵는 곳 지역]        [⬆]  │ stickyHeader(조건부)
│    4곳 · 찜 2곳                           │
│ ╭──────────────────────────────────────╮ │ 지역 카드 item 1개
│ │ 🏛 왓 아룬                        ♡  │ │ IconBadge(종류 미고정 때만), IconToggleButton
│ │   Wat Arun                           │ │
│ │   짜오프라야강 서쪽 강가에 있는 사원이에요.│ │
│ │   [⛴ 배를 타요]                       │ │
│ │ ──────────────────────────────────── │ │
│ │ 🏛 왓 포                          ♥  │ │
│ │   다른 이름: **…**                    │ │ 이유 줄(검색 중)
│ ╰──────────────────────────────────────╯ │
│━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━│
│ 📍 아유타야 · 2곳 · 방콕에서 하루 다녀오는 곳│
│ ( 모든 종류에서 3곳 더 있어요         › ) │ 넓혀 찾기
│ 끄라비는 아직 준비 중이에요 …              │ upcoming 안내(해당 시)
│ ( 맨 위로                              ) │
└──────────────────────────────────────────┘
```
\* '태국만 보기' 칩은 범위를 넓혔을 때만 펼침의 맨 앞에 나온다.

**1열(쉬운 모드·큰 글자)**: 2열과 같은 머리 구조(검색 64dp, '걸러 보기' 폭 전체 버튼, 펼침 안에는 ChoiceSegments 1열 + '찜한 곳만 보기' ListRow(Switch)). 지역 머리는 sticky가 아니고, 줄은 IconBadge 없이 'titleMedium 이름 / 아이콘16+종류 / summary / [♥ 찜했어요](찜한 곳만)'.

- **걸러 보기 규칙(2열·1열 공통)**: 보이는 종류가 **3개 이하**이면 칩을 펼친 채로 두고 버튼은 그리지 않는다. 4개 이상이면 접힌 버튼 하나만 둔다(SubcomposeLayout 측정 없이 결정적 규칙으로). 검색 칸에 포커스가 오면 펼침을 접는다.
- **지역 묶음 = `RegionGroupedList(groups, …)`**(목록·검색·찜·고르기 공용)
  - 지역 하나 = 카드 item 하나(key `region-<id>-card`). sticky 조건이면 머리를 `stickyHeader`(key `region-<id>`)로, 아니면 같은 item 안에 둔다(key는 `region-<id>`로 기록).
  - sticky 머리는 `Modifier.fullBleed(screenPadding)` + Ground 바탕이고, 2dp 선만 안쪽 padding을 쓴다.
  - **sticky 조건**: `rememberGridColumns()==2 && fontScale ≤ 1.3 && !easyMode && !touchExplorationEnabled`. 마지막 값은 Context의 `AccessibilityManager`에 `TouchExplorationStateChangeListener`를 달아 상태로 구독한다. TalkBack 사용자는 비sticky 모양을 받고 heading 탐색으로 지역을 건너뛴다.
  - 범위를 넓혔을 때 머리 글자는 '태국 · 방콕'이다.
- **키보드(ime) 규칙**
  0. **선행 변경**: `ReadyPortRoot` NavHost modifier를 `Modifier.padding(inner).consumeWindowInsets(inner)`로 바꾼다. 기존 화면은 WindowInsets를 쓰지 않으므로 회귀가 없다. 이 변경 덕분에 아래 `imePadding()`은 '키보드 − 이미 쓴 하단 탭 여백'만 더한다(탭 높이만큼 빈 띠가 생기지 않음).
  1. 목록 화면 루트(AppScreen modifier 인자)에 `imePadding()`.
  2. 검색 칸에 포커스가 오면 `scrollToKey(keyIndex, "search")`로 검색 칸을 맨 위로 올리고 걸러 보기 펼침을 접는다.
  3. 개수 줄 '6곳 · 지역 3곳'은 포커스나 검색어가 있을 때 보인다. liveRegion Polite, 입력이 1초 멈춘 뒤 갱신한다.
  4. 사용자가 목록을 끌어 스크롤하면(nestedScroll, source = UserInput) `clearFocus()`. IME '검색' 동작도 clearFocus.
  5. `focusSearch`(미소비)로 들어오면 첫 프레임 뒤 `requestFocus()` + `keyboardController?.show()`, 그리고 소비 표시.
  6. '맨 위로'는 `animateScrollToItem(0)`만 한다(포커스 주지 않음).
  - 테스트용으로 목록 내부 Composable은 `imeBottom: Dp`를 받는다. 정의는 `(ime.bottom − inner.bottom).coerceAtLeast(0)`이고, 화면은 consume 체인을 거친 값을 넘긴다.
- **머리 높이 예산**(`AttractionsFirstScreenTest`가 실측)
  - 기본(w393dp-h851dp, JP 종류 7개, 종류 하나 선택): 첫 `AttractionRow` 윗변 ≤ 머리 **260dp** + 지역 머리. (TitleBlock 56 + 검색 56 + 걸러 보기 48 + 안내 두 줄 48 + gap 12×4 ≈ 256dp. v4의 240dp는 안내 두 줄이 들어가면 지킬 수 없어 260dp로 고친다.)
  - 쉬운 200%(w360dp-h640dp-xxhdpi): 첫 줄 윗변이 '화면 아래 − 80dp'보다 위.
  - **키보드 260dp**: 보이는 높이 = 640 − 상태 표시줄 24 − 키보드 260 = 356dp. 검색 칸을 맨 위로 올린 뒤 검색 72 + 개수 줄 40 + 지역 머리 60 = 172dp 아래에서 첫 줄이 시작하므로 키보드 위에 보인다. **오프라인(OfflineBanner)이면 그 높이를 더 빼고도** 첫 줄 윗변이 키보드 위에 있어야 한다.
  - 픽스처는 최악 조건(지역 9개·종류 7개·가장 긴 이름)으로 만든다.
- **지역으로 건너뛰기**: 보이는 지역이 6개를 넘거나 결과가 25곳을 넘을 때만 목록 맨 위에 `ListGroup('지역으로 건너뛰기')`(행 = '방콕 · 4곳', `onClickLabel '방콕으로 건너뛰기'`)를 둔다. 소제목은 group_ko.
- **줄 `AttractionRow`**
  - 2열: 줄 전체를 누르면 상세(`onClickLabel '자세히 보기'`). 하트는 따로 떨어진 `IconToggleButton`(48/56dp, 이름 '관광지 찜하기: %1$s', stateDescription '찜함 / 찜 안 함'). 줄 semantics에 `customActions = [찜하기 | 찜 취소]`. 종류가 하나로 걸러져 있으면 IconBadge를 생략한다.
  - 1열: 줄을 누르면 상세. 찜 토글은 없고 찜한 곳만 StatusTag '찜했어요'.
  - TalkBack 낭독: '왓 아룬, 방콕, 역사·유적, 배를 타요' + 찜했으면 ', 찜함'. 지역 머리는 heading().
- **지역 안 정렬**: 검색 중이면 점수 순, 아니면 `rank.order` → 가나다(⟦결정 D11⟧).
- **speech**: '{제목}. 모두 {n}곳, 지역 {m}곳. {지역}: {이름}, {이름}…'(지역마다 처음 5곳).
- 검색어·걸러 보기·범위·스크롤 위치·소비 플래그는 `SavedStateHandle`에 둔다.
- **메모리**: `SignedFileStore`는 검증된 바이트와 version만 경로별로 캐시한다. `AttractionsRepository`는 디코딩한 모델을 **LRU 3개국**만 들고 있다(라우트 나라와 찜 나라 우선). 검색 인덱스는 ViewModel이 들고, '태국만 보기'로 돌아오면 다른 나라 인덱스를 놓는다.

### 6.4 상세 화면 — `AttractionDetailScreen`

**사진 없음(⟦결정 D3⟧ A, 기본) · 2열**
```
┌──────────────────────────────────────────┐
│ ←                                    ♡  │ headerActions 하트(2열만)
│ 왓 아룬                                   │ title = names.ko(+ko_paren)
│ Wat Arun                                 │ subtitle = names.en
│ ⚠ 여행경보가 바뀌었을 수 있어요 … / 안전 정보 보기 › │ §5.7 (c)일 때만
│ ⚠ 이 지역은 여행경보 2단계(여행자제)예요   │ level 2
│ ⚠ 일부만 열려 있어요: … (확인일 …)        │ status ≠ open
│ ⛰ 화산 경보 단계가 바뀔 수 있어요 — JMA   │ risk volcano
│ ┌──────────────────────────────────────┐ │ Teal soft 패널(2열만)
│ │        [문루 일러스트 96dp]           │ │
│ └──────────────────────────────────────┘ │
│ [태국 · 방콕] [역사·유적] [세계유산] [복장 규정]│ FactChip FlowRow
│ 📍 아유타야 지역 · 방콕에서 하루 다녀오는 곳│ daytrip일 때만
│ ┌ 어떤 곳이에요 ───────────────────────┐ │
│ │ body_ko · 출처 SourceList              │ │
│ └───────────────────────────────────────┘ │
│ ┌ 가기 전에 알아 둘 것 ─────────────────┐ │
│ │ • 월요일에 쉬어요 (regular_closed 첫 줄)│ │
│ │ • (public_space) 누구나 다닐 수 있는 곳이에요 │
│ │ • tips · 입장료 유무·예약 · visit_note · seasonal · step_free │
│ │ [D4: 숫자 또는 공식 사이트 안내]       │ │
│ │ ↗ 여는 시간·입장료는 공식 사이트에서   │ │
│ │ 출처: …                                │ │
│ └───────────────────────────────────────┘ │
│ ┌ 가는 법 ─────────────────────────────┐ │
│ │ ⛴ 배 · 🚇 지하철                      │ │
│ │ 가까운 곳: … (현지어: …)              │ │
│ │ [ 기사님께 보여 주기 ]  (주 버튼)      │ │ local_short ?: local, TH는 local_short만
│ │ ( 지도에서 보기 )                      │ │
│ │ (CN) 중국에서는 지도 위치가 조금 어긋날 수 있어요. 기사님께는 현지 이름을 보여 주세요. │
│ └───────────────────────────────────────┘ │
│ [ ♡ 찜하기 ]  /  [ ♥ 찜했어요 · 누르면 취소돼요 ]│
│ (처음 찜한 직후만) 찜 안내                │
│ ┌ 같은 지역의 다른 곳 ─────────────────┐ │
│ │ 왓 포                              › │ │ rank 상위 2곳(자기 제외)
│ │ 왕궁                               › │ │
│ │ 방콕의 다른 곳 모두 보기           › │ │
│ └───────────────────────────────────────┘ │
│ (market_street) 이 나라 살 거리 보기    › │
│ (A안) 사진 보기 (위키미디어 공용)      ↗  │
│ 최종 확인 2026-10-01                      │
└──────────────────────────────────────────┘
```
- **제목**: `AppScreen(title = names.ko (+ ' (' + ko_paren + ')'), subtitle = names.en)`. 화면 안 heading은 1개만 둔다.
- **1열**: 머리 하트 없음. 찜 버튼을 칩 아래와 본문 카드 아래 두 곳에 둔다(상태는 하나). 일러스트 패널 없음.
- **상태 띠 순서(고정)**: TitleBlock → (모르는 level / 경보 변경 안내) → level 2 → status ≠ open → risk → 그림 → 칩.
- **찜 버튼 낭독**: 버튼 Text에 `liveRegion = Polite`. `announceForAccessibility`는 쓰지 않는다.
- **'같은 지역의 다른 곳' 규칙(고정)**: 같은 region에서 자기를 뺀 `rank.order` 상위 2곳(경보 숨김·모르는 category 제외). 0곳이면 '모두 보기' 한 줄만 둔다. 라벨은 '{지역 이름}의 다른 곳 모두 보기'다. daytrip 상세에서도 그 daytrip 지역 기준이고, 거점 지역 링크는 두지 않는다.
- **'안전 정보 보기' 경로**(목록·상세 공통): 백스택 바로 아래가 같은 나라의 `CountryRoute`이면 그 항목의 savedStateHandle에 `focusSection = "safety"`를 넣고 `popBackStack()`. 아니면 `CountryRoute(cc, focusSection = "safety")`를 연다. 나라 화면은 focusAirports 선례처럼 Travel 갈래를 고르고 `section-safety` 키를 '첫 카드 윗변 = 고정 줄 아래 + gap' 규칙으로 스크롤한 뒤 소비 표시한다. 나라 화면은 기기의 최신 pack을 보여 주므로 목록의 안내와 같은 문장을 본다.
- **사진 있음(B·C)**: 히어로 + 검정 0.35 원형 뒤로·하트 + `PhotoTextArea` + 바로 아래 TASL. 상태 띠는 TASL 다음에 둔다.
- **'기사님께 보여 주기'**: 기존 ShowLocal 화면에 `local_short ?: local`을 크게, 정식명(다를 때)·`address_local.text`·'가까운 역: {nearest_local}'을 작게 보여 준다. TH는 `local_short`가 없으면 버튼을 숨긴다.
- **'지도에서 보기'**: CN이 아니면 `geo:lat,lng?q=lat,lng(local_short ?: local ?: en)`, 받을 앱이 없으면 `https://www.google.com/maps/search/?api=1&query=lat,lng`. **CN**이면 좌표 없이 `geo:0,0?q=<이름>`과 이름 검색 URL. 오프라인이면 기존 MapsCard 안내.
- **speech**: '{이름}. {summary}. {body}. 가기 전에 알아 둘 것: {정기 휴관}. {tips}. 출처는 화면 아래에 있어요.'

### 6.5 찜
- **저장소** `SavedAttractionsRepository`: `preferencesDataStore(name = "saved_attractions")` + 키 `items`(JSON `[{key:"TH/wat-arun", savedAt}]`) + `migrated_version_<CC>`. 생성자는 `Context`만 받는다.
- **쓰기 경로 분리**: CloudSync·Firestore·Messaging·`SettingsRepository.favorites` 쓰기에 의존하지 않는다(아키텍처 테스트). 관광지 찜은 나라 찜(알림 구독·집계)을 켜지 않는다.
- **말**: 앱 전체에서 '찜하기'와 '찜 취소' 두 말만 쓴다.
- **피드백**: 하트·버튼 상태(채움 + Accent + stateDescription)만. 스낵바·되돌리기는 없다.
- **처음 찜 안내**(한 번만): `NoticeBanner(Notice)` '찜은 이 휴대폰에만 저장돼요. 나라 찜과 달리 알림은 오지 않아요.' + (여행이 있으면) '여행 화면에서도 모아 볼 수 있어요.' + ⟦결정 D16⟧ 문장 + [알겠어요]. 자리: ① 상세에서 찜하면 찜 버튼 바로 아래 ② 목록 하트로 찜하면 목록에는 넣지 않고, 처음 '찜한 관광지' 화면을 열 때 맨 위나 나라 화면 '찜한 관광지 n곳' 줄 바로 아래 가운데 먼저 보이는 곳에 한 번.
- **보는 곳**: ① 나라 › 여행 정보 › 관광지 '찜한 관광지 n곳'(집) ② 내 여행 1단계 계획 PlanExtras 타일 '찜한 관광지 n곳'(찜 1곳 이상일 때만, Teal + Illus.Travel) ③ 7단계 여행 중 `ListRow('찜한 관광지 n곳 — 지역별로 보기')`(오늘 묵는 곳 정렬) ④ 목록의 '찜한 곳만'.
- **팩에서 사라진 찜**
  1. 그 나라 파일 로드 실패 → '정보를 불러오지 못했어요(앱을 업데이트해 주세요)'.
  2. **merged 이전**: published 파일을 **성공적으로 로드했을 때만**, 그리고 로드한 version이 `migrated_version_<CC>`보다 높을 때만 실행한다. 순서는 **Place(`PlacesRepository.renameExternal("att:TH/<옛>", "att:TH/<새>")`) → 찜** 이고, 각 단계는 단일 `edit {}` 트랜잭션이며 멱등이다. 찜은 키를 새 id로 바꾸고 중복을 없앤다(savedAt은 이른 값). 마지막에 `migrated_version_<CC>`를 기록한다. 찜 목록에는 한 번만 Info '다른 항목으로 합쳤어요'를 보여 준다.
  3. 그 밖의 `retired` → StatusTag(Prohibited) reason별('문을 닫았어요'·'오래 쉬어요'·'안전 문제로 뺐어요'·'목록에서 뺐어요') + note. 누르면 '찜 취소' 확인.
  4. 파일은 정상인데 키가 없음 → Soon '정보가 빠졌어요'. 누르면 '찜 취소' 확인.
  5. 모르는 category/region → '앱을 업데이트하면 볼 수 있어요'. 경보 숨김 → '안전 문제로 확인 중이에요'.
  - 어느 경우에도 조용히 지우지 않는다.
- **백업 ⟦결정 D16⟧**: A(기본) 그대로 / B 두 규칙 파일에 `datastore/saved_attractions.preferences_pb` include 1개 + `allowBackup="true"` + 개인정보 안내·DATA_SAFETY 한 줄 + 테스트 / C 설정 '찜 목록 내보내기·가져오기'(`RP-SAVED:TH/wat-arun,…`, 최대 500개).

### 6.6 이동하기 연결 — 고르기 모드
- `TransportUi`에 지금 쓰는 나라 `country`를 노출한다.
- 진입점: 이동하기 가는 곳 편집 카드 안, 입력 칸 위의 QuietButton '찜한 관광지에서 고르기 (n곳)'. n은 이동하기 나라의 찜 수이고, 0이거나 나라가 없으면 숨긴다.
- `AttractionsRoute(country = transportCountry, savedOnly = true, pickForMove = true)`. 제목은 '가는 곳으로 고르기'이고, 하트·찜 버튼을 숨기고, savedOnly를 고정하고, 줄 onClickLabel은 '가는 곳으로 정하기'다. 넓혀 찾기 줄은 없다. 다른 나라 찜이 있으면 누를 수 없는 안내 '다른 나라 찜 %1$d곳은 그 나라 여행에서 고를 수 있어요'.
- 줄을 누르면 `PlacesRepository.upsertExternal(id = "att:TH/wat-arun", name = names.ko, addressLocal = listOfNotNull(local_short ?: local, address_local?.text).joinToString("\n"), lat, lng, select = true)` → `popBackStack()`. CN은 lat/lng를 null로 넘긴다. 현지 이름과 주소가 모두 없으면 줄을 비활성으로 두고 '현지어 주소가 없어 고를 수 없어요'.
- `upsertExternal`은 고정 id로 맞춘다. 이미 있으면 이름·주소·좌표만 덮어쓰고 목록 줄에 '가는 곳에 있어요'(Info)를 붙인다. 돌아온 카드의 선택 이름 Text에 `liveRegion = Polite`.

### 6.7 검색·걸러 보기 동작
- **범위** = 나라 × 종류 × 찜만, **모두 AND**. 검색어도 이 범위 안에서 찾는다.
- **넓혀 찾기**(화면 안 상태, 새 라우트를 쌓지 않음): 범위 밖에도 결과가 있으면 맨 아래(0건이면 빈 상태 안)에 '모든 종류에서 n곳 더 있어요' → '찜 말고도 n곳 더 있어요' → '모든 나라에서 찾기' 순서로 보여 준다. '모든 나라에서 찾기'를 누르면 `scopeAllCountries = true`로 두고 다른 나라 파일을 `Dispatchers.Default`에서 차례로 로드한다('다른 나라도 찾고 있어요'). 걸러 보기 펼침 맨 앞에 '태국만 보기' 칩이 생긴다. 넓힌 상태에서는 보이는 종류 수와 관계없이 걸러 보기를 펼쳐 둔다. **시스템 뒤로 가기는 언제나 화면을 나간다.**
- **종류 안내 줄**: 정규화한 질의가 종류 동의어·라벨과 정확히 같으면 결과 맨 위에 '종류: 산·자연으로 보기 ›'.
- **안 싣는 것 안내**: 결과가 0이고 질의가 `ExcludedTopics` 낱말과 같거나 그 낱말을 포함하면 '%1$s 같은 업체·상품은 싣지 않아요. %2$s 종류에서 장소를 찾아 보세요.'
- **준비 중·경보 제외 안내**(새로 추가): 질의가 `upcoming_regions`의 이름·별칭과 일치하면 Soon 톤으로 누를 수 없는 '%1$s은(는) 아직 준비 중이에요. 다음 업데이트에서 추가돼요'를 보여 준다(조사 문제를 피하는 틀: '%1$s · 아직 준비 중이에요. 다음 업데이트에서 추가돼요'). 질의가 `excluded_areas`와 일치하면 '%1$s · 여행경보 %2$s 지역이라 싣지 않았어요' + `ListRow('안전 정보 보기 ›')`(§6.4 경로)를 보여 준다. 결과가 0이면 빈 상태 대신 이것을 보여 주고, 결과가 있으면 맨 아래 한 줄로 둔다.
- **빈 검색어 추천**: '이렇게 찾아 보세요' 아래 지역 이름 버튼과 종류 버튼을 FlowRow로 둔다.
- **이유 줄**(조사 없는 틀): '다른 이름: **…**' / '영어 이름: **…**' / '현지 이름: …' / '지역: **방콕**' / '가까운 역: **…**' / '종류: 역사·유적' / '설명에 나와요: **…**'. 초성 검색이면 결과 위에 '초성으로 찾았어요'.
- **로드량**: 기본은 라우트 나라 파일 하나만 로드하고 인덱싱한다.

### 6.8 빈 상태·오프라인
| 상태 | 표시 |
|---|---|
| 갈래 상태 6가지 | §6.1 |
| 검색 0 + 넓힐 곳 있음 | 넓혀 찾기 줄만 |
| 검색 0 + 안 싣는 것 / 준비 중 / 경보 제외 | §6.7 안내 |
| 검색 0 + 해당 없음 | 600ms 뒤 `EmptyState('다른 말로 찾아 보세요 — 영어 이름이나 지역 이름도 돼요')` + 조용한 줄 '찾는 곳이 없으면 게시판에서 알려 주세요' → BoardRoute |
| 찜 0 | `EmptyState`(하트 그림 + '하트를 누르면 여기에 모여요') |
| 오프라인 | 목록·검색·찜·상세 글 동작, 지도 안내 덧붙임 |
| debug 샘플 | '샘플 데이터' 띠 |
- 검색 0건 질의는 저장하지 않는다.

### 6.9 문자열 (`res/values/strings_attractions.xml`, 새 키만)
- 기존 키 값은 바꾸지 않는다. 기능 이름(⟦결정 D17⟧, 기본 '관광지'): 묶음 머리 '관광지', 제목 '%1$s 관광지', 검색 '관광지 찾기', '찜한 관광지', `attractions_save_cd` '관광지 찜하기: %1$s', 계획 타일 '찜한 관광지 %1$d곳'.
- 새 키 예: `explore_wifi_only_desc_v2`, `attractions_need_update`, `attractions_public_space`, `attractions_cn_map_note`, `attractions_lift_stay`, `attractions_lift_airport`, `attractions_airport_upcoming` '도착 공항(%1$s) 근처 지역은 준비 중이에요', `attractions_upcoming` '%1$s · 아직 준비 중이에요. 다음 업데이트에서 추가돼요', `attractions_excluded_area` '%1$s · 여행경보 %2$s 지역이라 싣지 않았어요', `attractions_merged`, `attractions_pick_other_countries`, `attractions_excluded_topic`, `attractions_only_country` '%1$s만 보기', `attractions_searching_more`, `attractions_scroll_top`, `attractions_filter_button` '걸러 보기: %1$s', `attractions_waiting_network` '연결되면 받아요', `settings_credit_osm`, `settings_credit_<source>`.
- P1 작업: '찜' grep 결과표를 부록 M에 넣고, 나라 히어로 하트 TalkBack 새 키 '%1$s 찜하기(새 소식 받기)'를 추가한다.
- 리뷰 체크: ① `%s` 바로 뒤에 조사가 붙지 않는 틀 ② 종류 라벨 7자 이내 ③ '가는 곳'과 헷갈리는 말 없음 ④ 찜 동사는 '찜하기'·'찜 취소'만.

### 6.10 상태 톤 매핑 (고정)
| 대상 | StatusKind | 아이콘 |
|---|---|---|
| '배를 타요', '차로만 가요', water_crossing | Info | DirectionsBoat / DirectionsCar |
| '오늘 묵는 곳 지역', '내 도착 공항 지역', '찜했어요', '가는 곳에 있어요', '다른 항목으로 합쳤어요', 도착 공항 준비 중 | Info(누를 수 없음) | Hotel / FlightLand / Favorite / Place / Info |
| status partial, level 2, 모르는 level, 경보 변경 안내, 경보 제외 지역 안내 | Caution | ReportProblem |
| status temp_closed, retired | Prohibited | Block |
| 확인 오래됨, '정보가 빠졌어요', NeedsAppUpdate, 준비 중 지역 | Soon(Neutral) | Schedule |

### 6.11 그림 언어
- **P1 첫 작업**: 생성기를 `tools/design/illus.py` + 미리보기 HTML로 저장소에 들인다. 기존 Illus를 다시 만들어 바이트가 같은지 확인한 뒤 새 그림을 더한다.
- 종류 일러스트 7장(청록 + 금색 포인트, 64격자, 외곽선 2.4) + `Illus.MarketStall`(주황). 공수 1.2~1.7일.
- 쓰는 곳: 종류 타일(56dp, 1열 48dp), 사진 없는 상세 머리 패널(96dp, 2열만). '모든 종류'와 계획 타일은 `Illus.Travel`.

---

## 7. 검색

### 7.1 구현
- `AttractionSearchIndex`(순수 Kotlin). Room·FTS는 쓰지 않는다. 나라당 50~120곳이라 키를 칠 때마다 전수 채점해도 1ms 수준이다.
- 정규화는 `com.readyport.text.KoreanNormalize`(새 파일)가 맡는다. 게시판 검색 로직은 바꾸지 않는다.
- 팩을 로드할 때 정규화·초성 문자열을 미리 만들고, 파일 version이 바뀔 때만 다시 만든다.

### 7.2 정규화 `norm(s): Normalized(text, srcIndex: IntArray)`
- **데이터**는 빌드에서 NFC가 보장되므로 앱은 1단계를 건너뛰고 2~6단계만 위치를 추적한다. **질의**는 전체 NFC를 먼저 적용한다(위치 맵 불필요).
1. NFC(질의만). **NFKC 금지**(호환 자모가 조합형으로 바뀌어 초성 검색이 깨진다).
2. 소문자화: **글자 단위 `lowercase(Locale.ROOT)`**. 결과가 1글자가 아니면 원문 글자를 그대로 둔다('İ' 등).
3. 전각 ASCII(U+FF01~FF5E → −0xFEE0)와 반각 가나(U+FF66~FF9F → 전각, 표로)만 접는다.
4. 라틴 악센트·성조 제거는 `UnicodeScript.LATIN` 글자에만(글자 단위 NFD → Mn 제거 → NFC, `đ/Đ→d`).
5. 공백·기호(`·ㆍ・/-_` 포함) 제거.
6. 된소리 접기(초성 ㄲㄸㅃㅆㅉ, 종성 ㄲㅆ). 초성 문자열은 이름·별칭·지역 이름에서 만든다.
- 굵게 범위는 `[srcIndex[start], srcIndex[end-1]+1)`. 초성 일치는 음절 전체를 굵게 한다.

### 7.3 질의 처리
1. 공백으로 나눠 낱말마다 `norm`.
2. 끝 자모: 마지막 글자가 호환 자모면 그 초성으로 시작하는 음절과 접두 일치. 받침이 있는데 일치가 0이면 받침을 다음 음절 초성으로 옮겨 재시도. 겹받침은 뒤 자음을 옮긴다('닭'→'달ㄱ').
3. 점수 = max(공백 없앤 전체 질의 점수, AND 점수). AND = 낱말마다 '가장 높은 필드 점수'의 min. 동점은 평균.
4. 1글자 질의는 title·category만. 2자 이상 전부 자음이면 초성 검색.

### 7.4 대상 필드와 점수
| 필드 | 내용 | 점수 |
|---|---|---|
| title | names.ko / ko_paren / en / local / local_short | 완전 100, 앞부분 80, 중간 60 |
| alias | aliases_ko / en | 앞부분 70, 중간 55 |
| region | 지역 이름·별칭·group_ko·area_ko | 40 |
| access | nearest_ko / nearest_local | 35 |
| category | 종류 라벨 + 동의어 + 태그 라벨 | 30 |
| body | summary + body + tips + mentions_ko | 10 |
- `upcoming_regions`·`excluded_areas`는 점수를 매기지 않고 §6.7 안내 판정에만 쓴다(정규화 후 완전·앞부분 일치).
- 250ms 디바운스. IME 조합 중인 글자도 반영한다.

### 7.5 별칭 관리
- 체크리스트: 국립국어원 표기 / 통용 표기 / 영어 / 줄임말 / 별명 / 묶음 이름(구성 지명이 모두 실려 있을 때만) / (D14-A) 몰 이름 / (D20) 한자음. 공백 제거형은 넣지 않는다.
- 지역 별칭 예: '발리' → id_bali_* 전부, '호치민' → vn_hcmc, '냐짱' → vn_nhatrang, '타로코' → tw_hualien, '믈라카' → my_malacca, '예스진지' → tw_north_coast, '장가계' → cn_zhangjiajie, '계림' → cn_guilin.

---

## 8. 거리·이동 시간
- 위치 권한은 쓰지 않는다. 해법은 지역 정의(40/70km, hub = 교통 거점) + daytrip 표시 + access + '같은 지역의 다른 곳' + 지역 묶음 + 여행 중 오늘 묵는 곳 지역을 맨 위로다.
- 숫자 표시 ⟦결정 D7⟧: A(기본) 숫자 없음 / B 기준점에서 '직선 약 N km' / C 출처 있는 대략 이동 시간.
- Rome2rio·Google 경로 결과는 저장하거나 보여 주지 않는다.

## 9. 사진 ⟦결정 D3⟧
- 공통: CC0·PD·CC BY·CC BY-SA만 쓰고, 파노라마의 자유(FoP)·인물·품질을 확인하고, TASL을 사진 아래와 설정 › 사진 출처에 둔다. Firebase Hosting으로 사진을 보내지 않는다. 목록 썸네일은 없다.
- A(기본): 사진 없음 + 종류 일러스트 + `photo_link`. 배포 크기 약 7.5MB.
- B: `assets/attractions/<CC>/<id>.webp`, 480px, 35KB 이하, 합계 4.5MB 이하 → 약 11.7MB.
- C: B + 찜한 곳만 `upload.wikimedia.org`에서 받아 디스크 캐시(LRU 30MB), 개인정보 안내 필요.
- 코드는 A로 만들되 `photo`가 있으면 B로 그린다. P0b에서 bundletool로 크기를 다시 재서 D3 설명의 숫자를 고친다.

---

## 10. 파이프라인·빌드

### 10.1 배포 모델
- `tools/packs/build_packs.py`: 기존 대상만 처리하고, 동작과 exit code는 그대로다.
- **`tools/attractions/build_attractions.py`**
  - `--check [<CC>…]`(CI·ARIA): drafts와 src의 스키마·lint를 돌린다. 날짜·게이트·copycheck는 경고만.
  - `promote <CC> --wave N --kid rp-att-…`(운영자 PC): §4.7.
  - `sign <CC…> --kid …`: src를 다시 서명한다(advisory_basis 갱신 등). 게이트를 적용하고, copycheck는 text_sha256이 바뀐 항목만 검사한다.
  - **`retire <CC> --ids a,b --reason safety|closed|long_closure|editorial [--note-ko …] --kid …`(긴급 제외)**: src에서 해당 항목을 attractions에서 retired로 옮기고, 0곳이 된 지역을 regions에서 빼고(그 공항은 unmapped `safety_retired`로), version만 올린다. 검사는 스키마·서명·doc_type·참조 무결성만 한다. **게이트·copycheck·확인일은 건너뛴다.** base 지역이 1곳 이하가 되어도 실패하지 않고 경고만 낸다. RC 7일 규칙에서도 예외다.
  - 나라마다 따로 처리한다. 한 나라가 실패하면 그 나라의 src·assets는 건드리지 않고 다른 나라는 계속 진행하며, 실패가 하나라도 있으면 exit 1.
  - draft 서명 금지, `use: sample` 거부, kid가 `rp-att-*`이 아니면 거부. 출력은 결정적이다(정규화 직렬화, 생성 시각 없음).
  - `--verify-committed`(CI): 커밋된 서명본의 서명·kid·doc_type·스키마·NFC를 확인하고, **src 정규화 바이트와 assets 바이트가 같은지** 본다. 날짜 초과는 경고만 하고, drafts는 비교하지 않는다.
  - `--cross-check`(CI·ARIA): 공항 매핑, advisory source id, 경보 문단 해시 차이. CI에서는 `::warning`만 내고 pack PR을 막지 않는다.
- **`tools/deploy/stage_hosting.py`**: ① build_packs 서명(실패하면 중단) ② 커밋된 attractions 서명본을 `hosting/public/packs/<CC>/`로 복사 ③ 복사본 서명·kid·doc_type을 다시 검증. `--check-staged`는 assets에 있는 모든 attractions가 hosting에 같은 바이트로 있는지만 확인한다(없으면 exit 1).
- **firebase.json**: `hosting.predeploy: ["python tools/deploy/stage_hosting.py --check-staged"]`.
- **워크플로**: `deploy-packs.yml`·`notices.yml`은 `firebase deploy` 직전에 `stage_hosting.py`를 실행하고, deploy-packs 트리거 paths에 `app/src/main/assets/packs/**`를 더한다. `android-ci.yml`에는 `--check`, `--verify-committed`, `--cross-check`(경고), 도구 unittest, `testDebugUnitTest`(CommittedAttractionsContractTest 포함)를 둔다. **어떤 워크플로에도 att 키를 넣지 않는다.**
- **문서 교체**: `CLAUDE.md` 46행과 `docs/HUMAN_TASKS.md` 48행의 손 배포 명령을 'python tools/deploy/stage_hosting.py … → firebase deploy --only hosting'으로 바꾼다. 관광지 절차 한 줄을 더한다: 'drafts 편집·커밋 → (D22 실행자가) promote로 서명 → assets 커밋 → PR → 머지되면 stage_hosting이 Hosting에 올림 → rc-versions로 attractions_version_<CC> 갱신(하루 최대 3개)'. 긴급 절차도 적는다: 'retire → 커밋 → PR → rc-versions'.

### 10.2 실패 조건(서명 시점, 나라 단위. retire는 표시한 것만)
- 스키마 위반, doc_type 없음, id 형식·중복, retired id와 겹침, 없는 지역, daytrip 규칙 위반, `airports`가 pack.json에 없음, 팩 공항이 매핑도 unmapped도 아님. *(retire에도 적용)*
- enum 밖 값(`attractions.enums.json` 기준). *(retire에도 적용)*
- NFC가 아닌 문자열. *(retire에도 적용)*
- 사실 필드에 source·last_verified 없음(public_space 면제 제외), sources에 없는 source, `license` 필수 use인데 license 없음, geo 출처 형식 위반, `nearest_local`에 qid 없음, hub에 qid 없음.
- public_space인데 category가 허용 밖.
- 경보: advisory source 불일치, TW·SG가 none이 아님, level 3·4·special, §5.6 판정 단계보다 낮음, 낮춤 예외·경계 ±5km인데 CSV 근거 없음, advisory_watch_ko가 지금 pack 경보 문단에 걸림, 0404 원문 지명이 구역에 1:1로 대응하지 않음, pack safety가 basis보다 30일 넘게 새롭고 해시가 다름, pack에 `id=="safety"` 섹션 없음.
- 거리: base 지역 관광지–hub 70km 초과, 40km 초과인데 CSV 근거 없음, 좌표가 나라 경계 상자 밖.
- risk volcano인데 hazard 출처 없음. volatile fee 형식 미달.
- 글 길이: summary 40자 초과·두 문장 이상, body 300자·5문장 초과, tips 4개 초과.
- **claims 검사**(summary·body·tips·visit_note 대상): ① 숫자·한글 수량 표현이 claims에 있어야 함 ② '세계유산' → tags에 unesco ③ 금지어 ④ '가장 …한' → claims ⑤ 유래·배경 표지어 → claims, '최초'·'유일'은 CSV 근거 위치 필수 ⑥ claims source가 `wd`이면 **유효 참조**로 기록된 statement여야 함.
- **인용 대조**: CSV 사실 행의 인용 해시가 없거나, `--verify-quotes` 기록이 실패.
- 본문에 `[확인 필요]`. aliases와 mentions 중복. 묶음 별칭의 구성 지명 누락.
- retired merged인데 replaced_by가 없거나, 없는 id이거나, retired를 가리킴. *(retire에도 적용)*
- CN published 항목에 address_local 없음. TH published 항목에 local_short도 없고 '짧은 현지명 없음 ✔'도 없음.
- osm 좌표가 있는데 `settings_credit_osm` 없음, attribution_required 출처의 문자열 키 없음.
- 사진 라이선스·TASL·sha256·크기(B·C).
- 확인일 등급별 실패 기준 초과, 크기 예산 초과.
- copycheck 위반, 또는 text_sha256이 바뀌었는데 캐시 없음.
- published 게이트(§1) 미달. `use: sample` 존재. kid가 att 키가 아님. *(kid 검사는 retire에도 적용)*

**경고**: 확인 주기 초과, 지역 안 두 곳이 60km 초과, base 40km 초과(근거 있음), 공항–hub 60km 초과, NAMES_KO·pack.json 표기 다름, 별칭이 다른 QID 라벨과 일치(2km 초과), 중복 별칭(정규화 후 같음), 종류 50% 초과(사유 있음), nature·sea_island인데 geo.kind=site, zh 폴백 라벨의 간·번체 불일치, vi 라벨 성조 없음, osm 좌표 나라당 50개 초과, 출처 도메인 30% 초과, 상세 최소 충실도 미달(10% 이하), 경보 해시가 basis와 다름(자동 갱신), copycheck 캐시 메타 없는 파일, draft 게이트 미달 목록, retire 뒤 base 지역 1곳 이하.

### 10.3 새 도구
- `tools/attractions/build_attractions.py`(check·promote·sign·retire·verify-committed·cross-check, 순수 Python 기하 판정, 암호 PEM 로딩)
- `tools/attractions/advisory_zones.py`(`extract`, 0404 원문 지명 대조)
- `tools/attractions/wikidata_fill.py`(라벨 폴백, 유효 참조 판정, lastrevid, 별칭 QID 조회, 간·번체 판별)
- `tools/attractions/copycheck.py`(메타 검사, `<CC>.copycheck.json` 갱신)
- `tools/attractions/curation_report.py`(CSV ↔ 팩 대조, `--verify-quotes`, 40자 연속 문장, 선정 지표·rank.order, 출처 도메인 비율, 상세 충실도 표)
- `tools/attractions/page_watch.py`
- `tools/attractions/test_build_attractions.py`, `tools/deploy/test_stage_hosting.py`
- `tools/deploy/stage_hosting.py`, `tools/deploy/ci_deploy.py` 확장(하루 3키 상한, 날짜 기반 7일 경고)
- `tools/design/illus.py` + 미리보기 HTML
- 운영자 PC 의존성은 `tools/attractions/requirements-operator.txt`(pyshp·shapely·OpenCC 데이터)에 따로 둔다.
- 공용 스키마 파일: `packs/schema/attractions.schema.json`, `attractions.enums.json`, `advisory_rules.json`, `advisory_hash_vectors.json`, `packs/curation/gates.json`.

### 10.4 앱 코드 배치
- `com.readyport.pack.SignedFileStore`(새 공용, revision 없음, 검증된 바이트+version만 캐시, 경로별 Mutex).
- `com.readyport.pack.PackKeys`: `TRUSTED_FOR: Map<DocKind, Map<kid, key>>` + att 공개키.
- `com.readyport.pack.PackRepository`: `UpdateResult.Stale/NotFound`만 추가(그 밖의 API 변경 없음).
- `com.readyport.pack.Advisory`: `isHighAdvisory`, `HighAdvisoryWords`, `AdvisoryParagraphRegex`, `advisoryHash(pack)`.
- `com.readyport.pack.HttpPackRemote`: 404 → NotFound.
- `com.readyport.net.Connectivity`: `networkStateFlow()`.
- `com.readyport.attractions`: `AttractionsModels`, `AttractionsMapper`, `AttractionsSource`, `AttractionsRepository`(LRU 3, draft·doc_type·kid 불일치 무시), `AttractionsFallback`(debug/release 소스셋), `AttractionsSync`, `AttractionsSyncWorker`(expedited 없음), `AttractionsAvailability`, `SavedAttractionsRepository`(merged 이전·migrated_version), `RegionGrouping`(정렬·LiftAnchor·경보 표시·버전 방향·해시·watch·upcoming/excluded 판정), `CategorySynonyms`, `ExcludedTopics`, `search/AttractionSearchIndex`.
- `com.readyport.text.KoreanNormalize`.
- `pack/PackSync.kt`: 대상 확장, NotFound·Stale 처리.
- `remoteconfig`: `attractionsVersion(cc)`.
- `transport/PlacesRepository.kt`: `upsertExternal`, `renameExternal`. `TransportUi.country`.
- `ui/ReadyPortRoot.kt`: NavHost `consumeWindowInsets(inner)`.
- `ui/nav/Routes.kt`: `AttractionsRoute`, `AttractionDetailRoute`, `CountryRoute.focusSection`.
- `ui/components/SearchField.kt`(공용화), `ui/components/Tiles.kt`(`NavTile` internal).
- `ui/attractions/`: `AttractionsEntry`, `AttractionsListScreen`, `AttractionDetailScreen`, `RegionGroupedList`, `AttractionRow`, `AdvisoryLevelNote`, `AttractionsViewModel`(소비 플래그), `SavedFirstNotice`, `CategoryIllustrations`, `FilterBar`.
- `ui/country/CountryScreen.kt`: Travel 분기에 `AttractionsEntry`, '알아 둘 것' 머리, `section-safety` 키, focusSection 처리, 쇼핑 NavMosaic columns=1 + 새 행. AdvisoryBanner는 그대로.
- `ui/trip/…`: PlanExtras 타일, 여행 중 ListRow, LiftAnchor 전달.
- `ui/Illustrations.kt`: 종류 7장 + MarketStall.
- `AppScreen`: 변경 없음.

---

## 11. 디자인 스펙 개정 — 부록 M '관광지'(2026-10)
1. **7-10 라우트**: `AttractionsRoute`, `AttractionDetailRoute`, `CountryRoute.focusSection`. 상세→상세는 popUpTo로 바꿔 끼운다. **한 번만 쓰는 인자는 소비 플래그로 처리**한다(새 조항).
2. **7-6**: 따로 서명한 `attractions.json`, 관광지 전용 키.
3. **3.7-1 / 7-2**: ⟦결정 D3⟧에 따른 히어로 사진·원격 썸네일 예외.
4. **4장 카탈로그**: `RegionGroupedList`, `AttractionRow`, `AdvisoryLevelNote`, `SearchField`(공용화), `NavTile` 공개 범위 변경.
5. **E.4 예외**(D2 A): 여행 정보 갈래 맨 위 '관광지' 묶음.
6. **그리드 빈 칸 규칙**: '모든 종류' 칸, 쇼핑 모자이크 columns=1.
7. **H.5 표**: '찜한 관광지'.
8. **문자열**: '찜' grep 결과, '찜한 나라 / 찜한 관광지', 조사 없는 틀, 찜 동사 두 개.
9. **색**: 관광지 요소는 Teal, 종류별 색 없음, 상태 톤 매핑(§6.10).
10. **그림**: 생성기 편입, 종류 7장 + MarketStall.
11. **키보드 규칙**: NavHost consumeWindowInsets, 검색이 있는 목록 화면은 imePadding, 포커스 시 검색 칸 맨 위 정렬, 끌기 스크롤 시 키보드 내림, 입력 중 개수 줄.
12. **걸러 보기 규칙**: 선택지가 4개 이상이면 접힌 버튼(2열·1열 공통).
13. **접근성**: announceForAccessibility 금지, stateDescription·liveRegion만, 터치 탐색 중에는 stickyHeader를 쓰지 않음.
나머지(가로 스크롤 금지, 말줄임 금지, 출처 줄 숨김 금지, 색만으로 상태 전달 금지, 쉬운 모드 규칙, 스낵바·바텀시트 없음, H.6 홈)는 그대로다.

---

## 12. 테스트 계획

**JVM 단위**
- 정규화: 띄어쓰기·가운뎃점·대소문자·성조('Đà Nẵng'→'danang' + 위치 맵), 된소리 접기, 'が' 보존, 굵게 범위, 'ＭＢＳ'→'mbs', 'ㅎㅈㅅ' 초성 유지(NFKC 회귀 방지), 반각 가나, **'İstanbul' 소문자화 뒤 srcIndex 길이 = 정규화 길이**, **'왓아룬' → '왓 아룬' title 100점(별칭 없이)**.
- 별칭·동의어: 푸켓→푸껫, 호치민→호찌민, 새벽사원→왓 아룬, 발리→4개 지역, 이나리역, 예스진지, 장가계→장자제, '액티비티'→theme_park, '온천'→hot_spring.
- 초성·끝 자모·받침 이동·겹받침('달ㄱ'), '마리나베이 샌즈'.
- 점수: '방콕 사원' < '왓 아룬'. mentions '무톈위' → 바다링 + '설명에 나와요'.
- 안내 줄: '산' → 종류 안내 줄, '스노클링' 0건 → 안 싣는 것 안내, **'나트랑'(upcoming) → 준비 중 안내**, **'민다나오'(excluded) → 경보 안내 + 안전 정보 줄**.
- 걸러 보기: AND, 넓혀 찾기, '태국만 보기' 복귀, 뒤로 가기는 화면 나감. **보이는 종류 3개 → 펼침 고정, 4개 → 접힌 버튼.**
- 지역 정렬: order 안정, daytrip 거점 뒤, 거점이 숨어도 자리 유지, 공항·오늘 숙소 끌어올리기, 40km 밖 안 올림, SG 안 올림, **도착 공항 upcoming → Info 줄**.
- 경보: none·1 표시 없음, 지역 전체 2·섞임, 모르는 level, special/3/4 숨김, TW none 띠 0. **해시: 9개국 현재 팩의 경보 문단 수(TW1·SG1·VN1·JP1·CN2·TH3·MY2·ID2·PH4), 'VN 날치기 문단만 바꿈 → 해시 불변·띠 0', 공용 벡터 3개 Python=Kotlin.** **버전 방향: '기기 팩 < basis + 해시 다름 → 안내 0·단계 표시 유지', '기기 팩 > basis + 해시 다름 → 안내 줄·단계 숨김', 'watch 푸껫 → 숨김', '해시 다름 + 팔라완 문단 → 엘니도 안 숨김'.** `advisory_rules.json` = 앱 상수.
- 관대 매핑: 모르는 값이 섞여도 로드, doc_type 불일치 Malformed, 빈 regions/attractions → NotYet.
- 찜: 저장·취소, 사라짐 5종, **merged 이전(Place → 찜 순서, 멱등, 중복 제거, migrated_version보다 낮은 version 로드 시 이전 안 함, 로드 실패 시 이전 안 함)**. 아키텍처 테스트(CloudSync·Firestore·Messaging import 0).
- 동기화: 상태 6가지 판정, 고정(같은 진입에서 Available 승격 없음), '관광지 Updated 뒤 CountryUi 재계산 0회', Stale 5분 재시도, schema 2 → NeedsAppUpdate·fetch 0회, SignatureInvalid 하루 1회, NotFound retry 없음, 와이파이 전용 + metered → 자동 받기 없음, [지금 받기] → KEEP, **ENQUEUED 30초 → '연결되면 받아요'**, **'setExpedited·getForegroundInfo 사용 0(소스 grep)', '매니페스트 FOREGROUND_SERVICE 권한 0'**, **PackSync 대상에 '기기 팩 < basis' 나라 포함**.
- 키: **'attractions 경로는 rp-2026-1 서명 거부, pack 경로는 rp-att 서명 거부'**.
- 메모리: **'9개국 넓혀 찾기 후 복귀 → 보유 모델 ≤ 3'**.
- 소비 인자: **'상세 → 뒤로: 포커스 요청 0회·firstVisibleItem 유지', '모두 보기로 돌아온 뒤 상세 → 뒤로: 재점프 0회', 'focusSection safety 한 번만'**.
- 인셋: **'consumeWindowInsets 뒤 imePadding = 키보드 − inner.bottom'**(Robolectric WindowInsetsCompat 주입).
- 고르기: 이동하기 나라만, 다른 나라 안내 줄, 비활성 줄, upsertExternal 중복 0, 좌표 → uber_url, CN 좌표 null. CN 상세 지도 인텐트에 좌표 없음.
- 로드량: 찜 TH 2곳 savedOnly → 로드 파일 1개.
- 신선도: volatile 180일 숨김, latestVerified 미포함, public_space 문장.
- 저장소: 서명 불일치 → 내장본, 스키마 초과 무시, draft 무시, **release 소스셋 AttractionsFallback null, debug에서 서명본이 있으면 샘플 미사용**.
- **`CommittedAttractionsContractTest`**: `app/src/main/assets/packs/*/attractions.json` 전부에 대해 서명(att kid)·Mapper '모르는 값' 0건·category·tag 문자열 키 존재·RegionGrouping·SearchIndex 생성·지역 이름 검색 시 그 지역 관광지가 나옴·0곳 지역 없음·NFC.
- **`EnumsContractTest`**: `attractions.enums.json` = 앱 상수.
- D16-B: 백업 XML include 1개·exclude 0. D7-B: 하버사인 오차 1% 이내.

**Robolectric 캡처(기본 w393dp-h851dp · 쉬운 200% w360dp-h640dp-xxhdpi)**
- [필수] 여행 정보 갈래: Available 2열(종류 3·4·5개, 빈 칸 없음)·1열, Downloadable(와이파이 전용·오프라인), Downloading→받았어요, NeedsAppUpdate, NotYet. JP 7종류 쉬운 200%에서 '알아 둘 것' 머리가 1.2화면 안.
- [필수] 목록: 지역 카드(sticky/비sticky), 걸러 보기 접힘·펼침(2열·1열), 키보드 260dp(온라인·오프라인), 이유 줄 4종, 넓혀 찾기, 0건, 준비 중·경보 제외 안내, 찜한 관광지(사라짐 포함), 고르기 모드, 맨 위로, 피피섬 줄에 Caution 없음.
- [필수] 상세: heading 노드 1개, 사진 없음(2열 일러스트/1열 없음), level 2 띠, 경보 변경 안내, status partial, volcano 띠, 정기 휴관 첫 줄, 하단 찜 버튼, 같은 지역 2곳 + 모두 보기, 같은 지역 0곳 → '모두 보기'만, '모두 보기 → 방콕 머리가 맨 위', **'안전 정보 보기 → safety 카드 윗변이 고정 줄 바로 아래, 문장 = 디스크 pack'**.
- [필수] 첫 찜: 목록에서 첫 찜 → 레이아웃 변화 0, 찜 화면 첫 진입 → 안내, 상세 첫 찜 → 버튼 아래 안내.
- [필수] 계획 타일(찜 0 → 없음, 3 → '찜한 관광지 3곳'), 쇼핑 모자이크 columns=1 + MarketStall, 이동하기 '찜한 관광지에서 고르기'.
- [필수] 회귀 0: 게시판·영상 SearchField 공용화, `HomeFirstScreenTest`, 나라 화면 기존 캡처, 기존 화면 전체(consumeWindowInsets 변경 뒤).
- [필수] `A11yAuditTest`: 새 화면, 찜 토글 semantics(toggleable + stateDescription, liveRegion Polite), announceForAccessibility 0(소스 grep), **터치 탐색 켬 → stickyHeader 0개**.
- [필수] `AttractionsFirstScreenTest`: 기본·쉬운 200%·키보드 260dp·키보드 + 오프라인, 최악 조건 픽스처, JP 종류 7개·하나 선택 상태 머리 ≤ 260dp.
- [나중] 종류 하나로 거른 2열 IconBadge 없음, '다른 나라도 찾고 있어요', CN 지도 안내, public_space 상세, merged 태그, 오늘 묵는 곳 머리 태그, 다크 모드 전 화면.

**도구(unittest, 표준 라이브러리만)**
- §10.2 실패 조건마다 1개 이상. 특히 다음을 포함한다.
- claims ①~⑥, **'P143만 있는 P571 → claims 출처 거부', 'P248이 비위키 항목이면 통과', 'P854 위키백과 URL → 거부'**.
- '대표적' 금지어, volcano + hazard 없음, TW level 1 거부, special.
- 구역 모델: PH default 2에서 마닐라 level 1 실패, 보라카이 낮춤 예외 + CSV 근거 → 통과·없으면 실패, **팡라오 좌표는 ph_bohol_main 밖 → 2**, 수빅 프리포트 level 1 실패, **KK·키나발루는 my_sabah_east_coast·islands 밖**, CN 티베트 special 실패, TH 국경 50km 안 실패, **JP 피난 지시 시정촌 안 실패**, **ID 파푸아 6개 주 level 2**, ±5km 근거 요구, **0404 원문 지명 대조 누락 실패**.
- 해시: **경보 문단만 대상, 생활 문단 변경 → 해시 불변**, 공용 벡터, safety 섹션 없음 → 실패, 자동 갱신·30일 규칙, watch 기준선 실패.
- 거리: base 70km 실패·40km 근거, 미매핑 공항.
- 게이트: **SG compact 통과, PH 픽스처(실제 후보 구조: 보라카이 3~4·보홀 4~5·막탄 1~2·세부시 4) — D15-A gates 기본 → 실패(8곳 < 12), D15-C gates PH 8 → 통과, D15-B → 통과**, 종류 50% 초과 + 사유 없음 실패, base 지역 3곳 미만 실패, 상세 충실도 미달 10% 초과 실패, CN address_local 없음 실패, TH local_short 규칙, NFC 아님 실패, kid 불일치 실패.
- 별칭: aliases/mentions 중복 실패, 중복 별칭 경고, **예스진지 구성 지명 누락 실패**.
- merged replaced_by 검사, public_space category 제한, osm + 출처 문자열 없음 실패, attribution_required + 문자열 없음 실패, osm 51개 경고.
- 파이프라인: draft 미출력, sample 거부, copycheck 캐시 없음·메타 없음, **'copycheck 해시가 같은 항목은 캐시 없이 통과'**, **'retire는 게이트 미달에서도 서명', 'retire로 0곳 지역 제거·공항 unmapped 이동'**, 한 나라 실패해도 다른 나라 진행·실패 나라 src·assets 미변경, promote 실패 시 src 미변경, 관광지 실패 상태에서도 build_packs 성공, `--verify-committed`가 src 미서명 변경을 잡고 drafts 변경은 무시, `--check`는 날짜 실패를 경고로, 공지 그림 스테이징 뒤에도 attractions 있음, `--check-staged` 누락 시 exit 1, **promote가 upcoming_regions·excluded_areas·unmapped(upcoming) 생성**, **`--verify-quotes`: 스냅샷에 없는 인용 → 행 거부**.
- ci_deploy: assets version만 읽음, **한 실행 attractions 키 4개 → 3개만 반영 + 경고**, **버전 날짜 7일 미만 → 경고**.

---

## 13. 단계별 출시 (MVP 먼저)

**MVP 정의**: P0a + P0b + P1 + P2 첫 나라(일본 1차) 공개. 이 시점에 사용자는 일본에서 종류 → 지역 묶음 목록 → 상세, 검색, 찜, 이동하기 고르기를 모두 쓸 수 있고, 나머지 나라는 'NotYet'(곧 추가돼요)으로 보인다.

| 단계 | 내용 | 공수 | 완료 기준(인수 조건) |
|---|---|---|---|
| **P0a 데이터 입력 준비** | 스키마·enums·advisory_rules·해시 벡터·gates.json, build_attractions(check/promote/sign/retire/verify/cross-check, 기하 판정, 암호 PEM), att 키 생성(운영자 PC), stage_hosting + `--check-staged` + predeploy + 워크플로 2개 + CLAUDE.md·HUMAN_TASKS 교체, 규칙 문서 5종(CATEGORY·SOURCES·EDITORIAL·SCHEMA·COPYCHECK), NAMES_KO 초안, wikidata_fill(유효 참조·라벨 폴백), curation_report `--verify-quotes`, DATA_LICENSES·SECURITY_KEYS | 4~5일 | 도구 unittest 전부 통과. 가짜 샘플 2개국 3곳씩 drafts `--check` 통과. 공지 배포 스테이징 테스트·predeploy 검사 통과. retire 리허설(테스트 키) 통과 |
| **P0b 첫 공개 준비** | advisory_zones extract + 0404 원문 대조 + 구역 JSON(9개국), copycheck(+해시 기록), curation_report(선정 지표·rank.order·도메인 비율·충실도), ci_deploy 확장(3키 상한·날짜 7일), 부록 M, bundletool 재측정(D3 숫자), 팩 표기 정리 PR(별도) | 3~4일 | 구역 픽스처 테스트 통과. 팩 표기 정리 PR 뒤 9개국 경보 문단 해시 변화가 '경보 문단을 고친 나라'에만 있음. 첫 나라 promote 리허설(테스트 키) 통과 |
| **P1 앱** | SignedFileStore·PackKeys 분리·att 공개키, Advisory 이동·해시·버전 방향, 404/Stale, Connectivity metered, 저장소(LRU 3)·관대 매핑·상태 6가지·고정·고유 작업(expedited 없음), AttractionsFallback, 검색·정규화, 찜(merged 이전), 목록(ime·consumeWindowInsets·걸러 보기 버튼·sticky 조건)·상세·고르기·빈 상태·준비 중/경보 제외 안내·라우트(소비 플래그)·CountryRoute.focusSection, SearchField 공용화, NavTile internal, AdvisoryLevelNote, 그림 생성기 + 일러스트 8장, 내 여행·이동하기·쇼핑 연결, 문자열 정리, debug 샘플, CommittedAttractionsContractTest·EnumsContractTest | **16~19일** | 단위·[필수] 캡처·AttractionsFirstScreenTest·A11yAuditTest 통과, 기존 캡처 회귀 0. 릴리스 빌드에서 모든 나라 NotYet. Play 업로드는 사장님 확인 후 |
| **P2a 시범(⟦결정 D24⟧ A)** | 일본 5곳(센소지·후시미 이나리·도톤보리·오사카성·나라 공원)을 실제 규칙대로 만들어 debug 빌드 캡처(2열·쉬운 모드)로 보고 | 5~6시간 | 사장님이 정보량·형식을 확인. 바뀐 점은 EDITORIAL_RULES에 반영 |
| **P2b 데이터 1차** | P0a 직후 P1과 나란히 시작. ⟦결정 D1⟧·⟦결정 D25⟧ 분량·순서(기본: 일본 → 베트남 → 태국 → 대만 → 나머지), ⟦결정 D23⟧ 대조 방식 | §5.3 계산식 | 나라별 게이트 통과 → promote(⟦결정 D22⟧ 실행자) → 커밋 → PR(CI 계약 테스트 통과) → 머지 → rc-versions(하루 최대 3키, 첫 공개는 나라별 하루 이상 간격). 첫 나라 실측 시간을 사장님께 보고 |
| **P3 운영** | ARIA 링크 점검·page_watch·신선도·교차 검사·경보 감시·retire 알림·evidence 백업 | 1~2일 | 첫 월간 보고 수신, 백업 zip에 evidence 포함 확인 |
| **P4 확장(선택)** | 사진(D3), 거리(D7), 활동 태그(D13-B), 2차 지역(wave 2), 줄 단위 item, [나중] 캡처 | 별도 | 별도 승인 |

- 공개 순서 예(D1-A·D25-A): 1주차 JP → 다음 날 VN → 그다음 날 TH → 이후 TW·SG·MY·ID·CN·PH. 하루에 한 나라씩만 RC를 올린다.
- Play 업로드와 스토어 등록정보 변경은 사장님 확인 후에만 한다.

---

## 14. 위험과 대응
| 위험 | 대응 |
|---|---|
| LLM이 기억으로 사실을 채움 | 인용 대조(스냅샷에 글자 그대로 있어야 통과), claims lint ①~⑥, 검증자·확인일 없는 행 제외 |
| 위키백과가 Wikidata를 거쳐 '공식 출처'로 세탁됨 | 유효 참조 정의(P143·P4656·P887·P3452만이면 무효) |
| 공식 부인된 이야기·평가어 위장 | 유래 표지어 lint, 판정표, 금지어('대표적'), 최초·유일 근거 위치 |
| 관광청·나무위키 문장 복제 | 원문 미제공, copycheck(필수 캐시 + 해시 기록) |
| 자동 수집 약관 위반 | 위키 API·덤프만, 나무위키 수동 저장 |
| 한 DB 통째 추출(DB제작자 권리) | 한 DB 30% 한도, 도메인 비율 경고 |
| 정부 데이터 출처 표기 누락 | sources.license + attribution_required + 문자열 존재 검사 |
| OSM share-alike 부담 | 비실질 추출 범위(나라당 50 경고·100 미만), 출처 표기 |
| 생활 안내 문장 수정이 거짓 경보를 띄움 | 해시를 경보 문단만으로 좁힘 |
| 팩이 관광지보다 오래돼 거짓 경보 | 버전 방향 판정(기기 팩 < basis면 안내 없음) |
| 구역 경계가 요약문 해석을 따름 | 0404 원문 지명 1:1 대조, '일부'는 보수적으로 전체 |
| 보홀 주 전체가 1단계로 잡힘 | 본섬 자치시 목록, 팡라오는 근거 확인 뒤 PR |
| PH 2단계 제외 시 공개 불가를 모르고 결정 | D15에 결과 명시, PH 픽스처로 갈래별 통과·실패 고정 |
| 인기 지역(나트랑 등)이 비어 '적다'는 불만 | 팩 공항 지역 1차 원칙(D25), upcoming 안내, 도착 공항 준비 중 안내 |
| 경보로 뺀 곳 검색이 '다른 말로'로 끝남 | excluded_areas 안내 + 안전 정보 줄 |
| 상세가 얇음 | 상세 최소 충실도 규칙, 시범 5곳 확인(D24) |
| 검증 병목(사장님 1인) | 인용 대조로 기계 검증, D23, 첫 나라 실측 보고 |
| 긴급 제외가 게이트·캐시에 막힘 | retire 명령, copycheck 해시 기록, evidence 백업 |
| 서명 키 남용·CI 우회 | 관광지 전용 키, 암호 PEM, GitHub 시크릿 제외, kid별 신뢰, D22 |
| setExpedited로 포그라운드 서비스·정책 신고 | expedited 미사용, 소스·매니페스트 테스트 |
| notify 미루기가 캐시 때문에 지켜지지 않음 | 장치 삭제, 버전 방향 판정으로 대체 |
| Python·Kotlin 계약 어긋남 | enums·advisory_rules 단일 파일, 계약 테스트, 커밋본 앱 코드 검증 |
| 키보드 위 빈 띠 | NavHost consumeWindowInsets, 오프라인 포함 실측 |
| 칩 줄이 머리를 차지 | 걸러 보기 접힘 버튼(종류 4개 이상) |
| 뒤로 올 때 키보드·점프 반복 | 소비 플래그, savedStateHandle remove |
| '안전 정보 보기'가 안전 카드에 닿지 않음 | CountryRoute.focusSection + section-safety 스크롤 |
| TalkBack과 stickyHeader 충돌 | 터치 탐색 중 sticky 끔 |
| 메모리 누적 | 바이트 캐시 + 모델 LRU 3 |
| NFD 데이터로 굵게 범위 어긋남 | NFC lint, 글자 단위 Locale.ROOT 소문자화 |
| debug 바인딩 중복 | AttractionsFallback 소스셋 분리 |
| Spark 하루 한도 초과 | 하루 3키 상한, 나라별 공개 간격, 날짜 기반 7일 경고, FCM 없음 |
| 중국 좌표계·지도 차단 | WGS-84 고정, 이름 검색, address_local 필수, 고르기 좌표 null |
| 현지어 간·번체 뒤바뀜, TH 긴 정식명 | 라벨 폴백 고정·판별 경고, TH local_short 필수 |
| hub 선택에 따라 lint가 흔들림 | hub = 교통 거점 정의 + 근거 열 |
| 묶음 별칭 일부 누락(예스진지) | 진과스 추가, 구성 지명 lint |
| 앱 크기 기준 | D3에 실측 숫자로 다시 여쭘 |
| 업체·안전 책임 | 업체·(나) 동물 상품 제외, 제휴 없음, 활동은 관리기관 근거 태그만, 공연은 D21 |

---

## 15. 결정별 변경 범위 (어느 쪽으로 정해져도 코드 구조는 그대로)
| 결정 | 바뀌는 곳 |
|---|---|
| D1 공개 방식 | gates.json 값, regions 시트 wave |
| D2 들어가는 길 | `AttractionsEntry` 호출 위치, `CountrySection` enum |
| D3 사진 | `photo` 데이터·assets, C면 캐시 모듈·개인정보 문구 |
| D4 숫자 | `volatile` 데이터, 운영 주기 |
| D7 거리 | RegionGrouping 정렬 옵션, 문자열 |
| D9 홈 | 홈 화면 한 줄 추가 여부 |
| D10·D13·D14·D19·D21 | EDITORIAL_RULES·판정표·데이터, (D13-B) 태그·ExcludedTopics |
| D11 순서 | `rank.order` 계산 규칙 |
| D12 번역 | SOURCES_POLICY·라이선스 고지 |
| D15 2단계 | gates.json(PH min), advisory_zones 처리, 지역 표 |
| D16 백업 | 백업 XML 또는 설정 화면 |
| D17 이름 | strings_attractions.xml 값 |
| D18·D20 | NAMES_KO·데이터, `ko_paren` |
| D22 서명 | ARIA_OPS·키 암호 보관 방식(B면 환경 변수 허용) |
| D23 대조 | 큐레이션 절차·공수(도구는 동일) |
| D24 시범 | 일정(P2a 유무) |
| D25 1차 지역 | regions 시트 wave, unmapped_airports |
