# 인도네시아(ID) 관광지 1차 작업본 보고 — 2026-10-09

- 데이터: `packs/drafts/ID/attractions.json` (release draft, version 2026.10.09-1 / 지역 9개(1차 6개 + 2차 3개) / 관광지 19곳 / 출처 46개)
- 근거: 저장소 밖 `~/.readyport/evidence/ID/<id>/` — 페이지 글 스냅샷(`N.txt`, URL·수집일·수집 방법 머리말) + `extract.json`(JP 예시와 같은 형식, 19곳 인용 206개). 지역 근거는 `~/.readyport/evidence/ID/_regions/`. 원문은 커밋하지 않았다.
- 모든 사실 칸(claims·tips·tags·facts·access·address_local)은 허용 출처(시설 공식·운영사, 발리주 관광청, 족자카르타 특별주 관광청·교통국, 자카르타 특별주 정부, 트랜스자카르타, 에너지광물자원부(ESDM, MAGMA 운영 기관), UNESCO WHC) 인용으로 뒷받침했고 확인일은 모두 2026-10-09. 좌표는 모두 Wikidata P625. robots.txt를 확인했고 막힌 곳은 우회하지 않았다. Google 지도·리뷰·여행사·블로그·뉴스는 쓰지 않았다.
- D4-A: 시간·요금 숫자 없음. D12: 위키 문장 번역 없음. D13: 투어·체험 업체 없음. D19: 우붓 몽키 포레스트는 (가) — 먹이 주기는 '직원만 줘요'라는 관리기관 안내로만 적었다. D21: 울루와뚜·GWK의 께짝 공연은 '매일 열려요'라는 사실만 적었다(별도 항목 아님).
- 입장 무료(오후 결정): 시설 공식 '무료' 문구가 있는 곳이 없어 `entry: free`·`free_entry`는 0곳이다.

## 검증

- `build_attractions.py check ID`: **통과**(설정 › 출처 문자열 3개를 임시로 넣고 확인한 뒤 되돌림). 남은 경고: facts.booking 없음 14곳(공식 예약 안내 없음), sea_island·nature 방문 지점 권장 4곳(꾸따·사누르·짐바란·몽키 포레스트 — Wikidata 대표 좌표).
- `build_attractions.py verify-quotes ID`: **통과**(19곳, 필드별 출처 일치).
- copycheck: `~/.readyport/copycheck_cache/ID/<id>/`에 한·영 위키백과 본문(위키 없는 꾸따·짐바란은 '문서 없음' 메모) + **Indonesia.travel 한국어 페이지**(발리·우붓·족자카르타·보로부두르; 자카르타 한국어 페이지는 404)를 두고 `build_attractions.copycheck()` 실행 → **19곳 모두 통과**(evidence 스냅샷도 비교 대상).
- 공개 게이트(`gate_failures`): **실패 0, 경고 0**. 19곳(≥12) · 보이는 base 지역 5곳 모두 3곳 이상 · daytrip(발리 동부) 3곳 · 2곳 이상 종류 4개(heritage 9 · sea_island 3 · city_view 2 · museum 2) · 최다 종류 heritage 9/19=47%(50% 이하) · 상세 최소 충실도 미달 0곳.
- 지역 lint: 모든 곳이 hub에서 40km 안(가장 먼 곳: 보로부두르–족자카르타역 약 27km).
- 곳당 크기 2,362~3,927B(한도 4,608B).
- `wiki-fill ID`: 17곳 채움(꾸따 해변·짐바란 해변은 위키 문서 없음). `place-ids ID --write`: 19곳 모두 place ID 채움 — 앱 상세의 구글 별점이 맞는 곳인지 표본 확인 필요.

## 지역

| id | 이름 | kind | wave | hub (선택 근거) | 공항 | 곳 수 |
|---|---|---|---|---|---|---|
| id_bali_south | 발리 남부 | base | 1 | 응우라라이 국제공항 Q1061846 — 발리 남부 여행자의 실제 출발 거점 | DPS | 3 |
| id_bali_bukit | 발리 울루와뚜·누사두아 | base | 1 | 울루와뚜 사원 Q1381933 — 마땅한 교통 거점 항목이 없어 지역 1순위 관광지 방문 지점 | — | 3 |
| id_bali_ubud | 발리 우붓 | base | 1 | 우붓 몽키 포레스트 Q3025956 — 같은 이유로 지역 1순위 관광지 | — | 3 |
| id_bali_east | 발리 동부 | daytrip [ubud, south] | 1 | 브사키 사원 Q829418 — 지역 1순위 관광지 | — | 3 |
| id_nusa_penida | 누사페니다 | daytrip [south], water_crossing | 2 | 누사페니다섬 Q1520301(항구 항목 Q137525026에 좌표가 없어 섬 항목) | — | 0 |
| id_yogyakarta | 족자카르타 | base | 1 | 족자카르타역(뚜구역) Q2457850 | — (팩에 JOG·YIA 없음) | 4 |
| id_jakarta | 자카르타 | base | 1 | 감비르역 Q1889128 | CGK | 3 |
| id_lombok | 롬복 | base | 2 | 롬복 국제공항 Q1395514 | — | 0 |
| id_labuanbajo | 라부안바조(코모도) | base | 2 | 코모도 국제공항 Q1927261 | — | 0 |

- 팩 공항 DPS·CGK 모두 지역에 매핑(`unmapped_airports` 없음). 자카르타는 3곳을 채워 1차로 둠.
- 경보: 모든 지역·관광지 1단계(mofa_id, 팩 safety 2026-09-28). 파푸아·말루쿠·아체(2단계)에는 1차 관광지가 없다. 동자바(브로모·이젠)는 스펙대로 보류(지역도 만들지 않음).
- 발리 동부 daytrip 근거: 발리주 관광청 띠르따 강가 페이지가 이곳을 'Bali East Tour'·'Bali Lempuyang Tour' 하루 일정의 들르는 곳으로 소개(`_regions/extract.json`). 운영사 시간표 같은 대중교통 근거는 없다.
- 킨타마니·바투르: 이번 1차에 넣지 않았다(바투르 화산 hazard 근거와 장소별 공식 안내를 따로 확보해야 함). 넣을 때는 우붓 hub에서 직선 30km대라 id_bali_ubud 또는 동부 daytrip에 둘 수 있다.

## 관광지 (rank = D11: 공식 지정 → 한국어 조회수 → 영어 조회수, 조회수 = Wikimedia REST 사용자 조회수 2025-10~2026-09 합계)

| rank | 곳 | 종류 | 지정 | 한국어 | 영어 | claims/tips/tags/인용 | facts | 가는 법 |
|---|---|---|---|---|---|---|---|---|
| 1 | 보로부두르 사원 `borobudur` | heritage | 세계유산(1991) | 1,467 | 237,827 | 6/4/1/15 | 시설·유료·예약 권장 | 차로만 |
| 2 | 프람바난 사원 `prambanan` | heritage | 세계유산(1991) | 1,043 | 115,628 | 6/2/1/12 | 시설·유료 | 버스(트랜스 족자) |
| 3 | 족자카르타 왕궁 `kraton-yogyakarta` | heritage | 세계유산 '족자카르타 우주론적 축'(2023)의 중심 | — | 16,907 | 4/4/2/12 | 시설·유료 | 시내에서 걸어서 |
| 4 | 모나스 `monas` | city_view | — | 884 | 32,545 | 4/2/0/12 | 시설·유료 | 버스(트랜스자카르타) |
| 5 | 이스티끌랄 모스크 `istiqlal` | heritage | — | 489 | 45,058 | 4/2/0/10 | 시설·단체 예약 권장 | 시내에서 걸어서 |
| 6 | 브사키 사원 `besakih` | heritage (risk volcano) | — | 110 | 27,164 | 4/3/1/12 | 시설 | 차로만 |
| 7 | 따나롯 사원 `tanah-lot` | heritage | — | 85 | 40,728 | 4/2/2/15 | 시설·유료 | 차로만 |
| 8 | 우붓 몽키 포레스트 `ubud-monkey-forest` | nature | — | 84 | 17,802 | 3/4/1/14 | 시설·유료·휴무 없음 | 시내에서 걸어서 |
| 9 | 울루와뚜 사원 `uluwatu-temple` | heritage | — | — | 27,915 | 4/2/1/10 | 시설 | 차로만 |
| 10 | 가루다 위스누 끄안짜나 문화공원 `garuda-wisnu-kencana` | theme_park | — | — | 24,499 | 3/2/0/9 | 시설·유료 | 차로만 |
| 11 | 사누르 해변 `sanur-beach` | sea_island | — | — | 17,412(사누르 문서) | 3/2/0/7 | 누구나 | 시내에서 걸어서 |
| 12 | 고아 가자 `goa-gajah` | heritage | — | — | 13,272 | 4/2/0/11 | 시설 | 차로만 |
| 13 | 띠르따 강가 `tirta-gangga` | city_view | — | — | 10,536 | 4/2/0/10 | 시설 | 차로만 |
| 14 | 자카르타 역사박물관 `jakarta-history-museum` | museum | — | — | 9,897 | 4/2/0/10 | 시설 | 버스(트랜스자카르타) |
| 15 | 말리오보로 거리 `malioboro` | market_street | — (아래 메모) | — | 7,757 | 4/2/1/9 | 누구나 | 버스(트랜스 족자) |
| 16 | 렘푸양 사원 `lempuyang` | heritage | — | — | 6,558 | 4/2/1/11 | 시설 | 차로만 |
| 17 | 네카 미술관 `neka-art-museum` | museum | — | — | 1,093 | 3/2/0/10 | 시설·유료 | 차로만 |
| 18 | 짐바란 해변 `jimbaran-bay` | sea_island | — | — | — | 4/2/0/8 | 누구나 | 차로만 |
| 19 | 꾸따 해변 `kuta-beach` | sea_island | — | — | — | 4/2/0/9 | 누구나 | 시내에서 걸어서 |

**한 줄 미리보기(summary_ko)**
- 보로부두르: 8~9세기에 지은 세계 최대 불교 사원이에요. / 프람바난: 9세기에 지은 인도네시아 최대 힌두 사원 단지예요. / 족자카르타 왕궁: 지금도 술탄 가족이 사는 족자카르타의 왕궁이에요. / 말리오보로: 족자카르타 시내의 쇼핑·먹거리 큰길이에요.
- 모나스: 메르데카 광장 한가운데 선 높이 132m 기념탑이에요. / 이스티끌랄: 1978년에 문을 연 동남아시아에서 가장 큰 모스크예요. / 자카르타 역사박물관: 꼬따 뚜아에서 자카르타의 역사를 보여 주는 박물관이에요.
- 따나롯: 바닷가 바위섬 위에 선 발리 힌두 사원이에요. / 꾸따: 서핑과 해넘이로 알려진 발리 남부 해변이에요. / 사누르: 물결이 잔잔한 덴파사르 동쪽의 흰 모래 해변이에요.
- 울루와뚜: 인도양 절벽 위에 선 바다 사원이에요. / GWK: 거대한 가루다 위스누 상이 서 있는 문화공원이에요. / 짐바란: 물결이 잔잔하고 해산물 식당이 늘어선 해변이에요.
- 몽키 포레스트: 원숭이와 옛 사원이 함께 있는 우붓의 숲이에요. / 고아 가자: 쁘따누 강 골짜기에 있는 옛 동굴 사원이에요. / 네카 미술관: 발리 회화의 여러 갈래를 모아 둔 우붓의 미술관이에요.
- 브사키: 아궁산 비탈에 자리한 발리에서 가장 큰 힌두 사원이에요. / 렘푸양: 렘푸양산을 따라 사원 일곱 곳이 이어지는 사원 무리예요. / 띠르따 강가: 1948년 까랑아슴 왕이 만든 물의 정원이에요.

**곳별 메모·빈칸**
- 브사키: `risk: volcano`. hazard 출처 = ESDM(에너지광물자원부) 웹의 PVMBG 아궁산 발표(2020-07) — "MAGMA Indonesia 웹·앱에서 아궁산 상태·권고를 언제든 확인", "위험 구역은 수시로 바뀜" 두 문장을 tips로 실었다. **magma.esdm.go.id·vsi.esdm.go.id 는 robots.txt가 전체 금지(`Disallow: /`)라 현재 경보 단계를 가져오지 않았다.** 현재 단계는 싣지 않았고, risk가 있어 status 확인 주기는 30일(서명 60일 실패)이다.
- 종류 판정: GWK = theme_park(운영사가 문화공원·공연·조각상 투어를 파는 시설, 규칙 1). 띠르따 강가 = city_view(1948년에 만든 왕실 물의 정원 — '사람이 지은 랜드마크 정원'). 몽키 포레스트 = nature(12.5헥타르 숲·나무 186종, 숲 안 사원은 기도 전용이라 관람 대상 아님).
- 렘푸양: 항목 QID는 사원 무리(Pura Lempuyang Luhur Q48728528), 좌표는 '천국의 문'이 있는 가장 아래 사원(Pura Penataran Agung Lempuyang Q47492282).
- 말리오보로: Wikidata에서 '족자카르타 우주론적 축'의 일부(P361)이지만 참조가 없고 UNESCO 페이지 본문에도 거리 이름이 없어 지정으로 치지 않았다. 축 항목 Wikidata의 공식 사이트 `jogjaworldheritage.com`은 **도박 스팸이 차지한 도메인**이라 쓰지 않았다.
- 모나스: Wikidata P1435 '인도네시아 문화재(cagar budaya)'가 있으나 참조가 없어 지정으로 치지 않았다(국가 문화재 DB `cagarbudaya.kemdikbud.go.id`는 접속 불가).
- 고아 가자·렘푸양·띠르따 강가·울루와뚜·브사키: 공식 근거가 발리주 관광청 페이지(+브사키는 ESDM) 위주라 입장 유무·쉬는 요일을 비웠다(`entry: unknown`). '가기 전에 알아 둘 것'은 tips 2~3줄로 채웠다.
- 네카 미술관: 공식 페이지에 '매일 연다'는 문장과 화~일 시간표(월요일 없음)가 함께 있어 쉬는 요일은 `unknown`으로 두었다.

## 가는 법(access) — 사장님 확인 필요

- 발리 9곳 중 7곳과 보로부두르는 `car_only`(차로만)이다. **발리 대중버스(Trans Metro Dewata)와 보로부두르행 버스의 공식 운영사 노선 근거를 찾지 못해**, 공식 위치 문구(마을·지구·군, 주차장)만 인용하고 '차로 가요'로 적었다(베트남 미선·오행산과 같은 방식). 대중교통이 아예 없다는 뜻은 아니어서, 공식 노선 근거가 생기면 `bus`로 바꾼다.
- 꾸따·사누르·몽키 포레스트는 마을 안 위치 문구로 `walk_from_center`, 족자카르타 왕궁은 UNESCO의 '시내 중심 남북 축 한가운데' 문구로 `walk_from_center`, 프람바난·말리오보로는 족자카르타 교통국 트랜스 족자 노선표, 모나스·자카르타 역사박물관은 트랜스자카르타 노선 목록(종점 이름)으로 `bus`.

## 다음 차수

| 곳 | 종류 | 조회수(ko/en) | 막힌 것 | 풀리는 조건 |
|---|---|---|---|---|
| 우붓 왕궁(뿌리 사렌) | heritage | 996 / 19,896 | 공식 사이트·지자체 안내를 찾지 못함(기안야르 군 관광 사이트 DNS 실패) | 기안야르 군·왕실 공식 안내 확보 — **한국어 조회수가 높아 1순위** |
| 뜨갈랄랑 계단식 논 | nature | 위키 없음 | 공식 장소 안내 없음(관광청 지역 페이지 한 줄 언급뿐). UNESCO 발리 수박 경관(1194)의 구성 요소(울룬 다누 바뚜르 사원·빠끄리산 유역·짜뚜르 앙가 바뚜까루·따만 아윤 사원 등)에 **들지 않는다**(UNESCO 페이지 본문 기준) | 기안야르 군·마을 관리 공식 안내 |
| 띠르따 엠풀 | heritage | — / 25,480 | 발리주 관광청 페이지가 한 문장뿐(사실 2개 미만). Wikidata상 UNESCO 구성 요소 '빠끄리산 유역 수박 경관'의 일부(P361, 참조 없음) | 관리기관 공식 안내 또는 UNESCO 구성 요소 지도 문서 |
| 인도네시아 국립박물관 | museum | 220 / 12,460 | 공식 사이트에 연혁은 있으나 운영 상태·관람 안내·교통 근거 없음 | 박물관 공식 관람 안내 |
| 꼬따 뚜아(파타힐라 광장) | city_view | 31 / 18,708 | 자카르타 역사박물관의 mentions_ko로 검색되게만 함 | 광장 자체 공식 안내가 생기면 별도 항목 검토 |
| 스미냑 해변·누사두아 해변 | sea_island | — / 19,391(누사두아) | 스미냑: 공식 장소 페이지 없음 / 누사두아: 발리주 관광청 페이지에 위치·가는 법 문구 없음 | 바둥 군 공식 안내 |
| 킨타마니·바투르 | nature (risk volcano) | — | 바투르 화산 hazard 근거와 장소별 공식 안내 | PVMBG 공개 자료(로봇 허용 경로) 확보 |
| 누사페니다·롬복·라부안바조 | — | — | 스펙상 2차(지역만 wave 2로 둠) | 2차 큐레이션 |

## 막힌 출처 (우회하지 않음)

- MAGMA Indonesia(magma.esdm.go.id)·VSI(vsi.esdm.go.id): robots.txt `Disallow: /`. jakarta-tourism.go.id·tanahlot.net: robots.txt 전체 금지. borobudurpark.com: 인증서 이름 불일치(운영사 새 도메인 injourneydestination.id 사용). bnpb.go.id: Cloudflare 봇 확인.
- DNS 실패: pariwisata.badungkab.go.id, 기안야르 군 관광 사이트들, cagarbudaya.kemdikbud.go.id, monas.jakarta.go.id, ihaindonesia.co.id.
- indonesia.travel 영어 장소 페이지(따나롯·우붓 왕궁 등)는 /gb-en/ ↔ /gb/en/ 리디렉트가 돌아 열리지 않음(지역 페이지만 열림).
- 이번 작업 중 웹 검색 한도(턴당 200회)에 닿아, 우붓 왕궁 등 일부 후보는 추가 검색을 하지 못했다.

## 설정 › 출처 문자열 (필요, 앱 담당이 추가)

`attribution_required` 출처 3개 — 아래 키가 없으면 `check`가 실패한다(검사할 때만 임시로 넣고 되돌림).

```xml
<string name="settings_credit_unesco_592">UNESCO World Heritage Centre — 보로부두르 사원 단지</string>
<string name="settings_credit_unesco_642">UNESCO World Heritage Centre — 프람바난 사원 단지</string>
<string name="settings_credit_unesco_1671">UNESCO World Heritage Centre — 족자카르타 우주론적 축과 역사 유적</string>
```

## 사장님 결정이 필요한 것

1. **발리·보로부두르의 '차로만' 표기**: 공식 대중교통 근거가 없어 car_only로 두었다. 이대로 둘지, 근거가 생길 때까지 해당 곳을 다음 차수로 미룰지.
2. **브사키 화산 정보**: MAGMA는 robots 전체 금지라 현재 경보 단계를 싣지 못하고 'MAGMA에서 확인하세요' 안내(정부 발표 인용)만 실었다. 이 수준으로 공개해도 되는지.
3. **우붓 왕궁**: 한국어 조회수 996으로 높지만 공식 근거가 없어 뺐다. 공식 안내 출처를 알면 다음 차수 1순위로 넣는다.
4. 지역 hub 3곳(부킷·우붓·동부)은 교통 거점 Wikidata 항목이 없어 지역 1순위 관광지 지점으로 두었다(스펙 §3.1 대체 규칙).

## 2차 (2026-10-10)

- 데이터: `packs/drafts/ID/attractions.json` version **2026.10.10-1** — 지역 9개(1차 6 + 2차 3, 2차 지역은 모두 `wave: 2`) / 관광지 **34곳**(1차 19 + 2차 15) / 출처 82개(OSM 좌표 출처 `osm` 포함). 1차 지역·관광지 본문은 손대지 않았다(rank.order 와 wiki 제목만 나라 전체 재계산).
- 근거: `~/.readyport/evidence/ID/<id>/` 에 곳마다 스냅샷 + `extract.json`(2차 15곳 인용 176개). 확인일 모두 2026-10-10. 좌표는 Wikidata P625가 1순위, 항목·좌표가 없는 곳은 OpenStreetMap(`geo.source: osm`, node·way·relation id 기록, 6곳) — 앱의 `settings_credit_osm` 문자열은 이미 있다.
- 막혔던 공식 사이트는 **실제 브라우저로 사람처럼 읽어 저장**했다(`manual_browser_save`): MAGMA Indonesia 화산 활동 단계 목록 + 바투르·린자니 활동 보고(2026-10-08자). CAPTCHA·로그인 없이 그냥 열렸다. 나머지는 robots.txt 확인 뒤 HTTP GET.
- 새로 쓴 공식 출처: 인도네시아 관광부 공식 포털 Wonderful Indonesia(indonesia.travel, 국가 관광청 — 이번에는 `/gb/en/destination/…` 경로가 정상 응답), Love Bali(발리주 정부 관광 포털 lovebali.baliprov.go.id — 1차에 쓴 disparda 와 같은 발리주), 끌룽꿍 군 관광청·군청(누사페니다), 코모도 국립공원 관리청 공식 사이트(tnkomodo.ksdae.kehutanan.go.id), 산림부 KSDAE 공원 프로필, ITDC(만달리카 운영사), 국립박물관 공식, UNESCO WHC·세계지질공원, MAGMA(PVMBG).

### 검증

- `check ID`: **통과**(설정 › 출처 문자열 2개를 임시로 넣고 확인한 뒤 되돌림). 남은 경고: hub 40km 초과 4곳(아래, `packs/curation/ID.curation.json` 에 유지 근거를 적어 경고로 내려감), 롬복 안 두 곳 60km 초과(꾸따 만달리카–길리 트라왕안) 경고, booking 없음·sea_island/nature 방문 지점 권장은 1차와 같은 종류.
- `verify-quotes ID`: **통과**(34곳). `wiki-fill ID`: 2차 15곳 중 9곳 채움(쩨낑·켈링킹·브로큰 비치·앙겔스 빌라봉·꾸따 만달리카·핑크 비치는 위키 문서 없음). `place-ids ID --write`: 14곳 채움, **파다르섬은 좌표 1km 안 후보 없음**(비워 둠).
- copycheck: 캐시 생성(`~/.readyport/copycheck_cache/ID/<id>/` ko·en 위키 본문, 없으면 none.txt) → promote 시뮬레이션 안에서 **34곳 모두 통과**.
- promote 시뮬레이션(`--wave 2`, 임시 키 `rp-att-sim`, 저장소 밖 복사본에서만): **게이트 실패 0, 실패 0**, 34곳 · base 7지역 모두 3곳 이상 · daytrip 2지역(발리 동부 3, 누사페니다 3) · 종류 heritage 11 / sea_island 9 / nature 7 / museum 3 / city_view 2(최다 11/34=32%) · 충실도 미달 0. 서명 산출물은 커밋하지 않았다.
- 곳당 크기 2,836~4,434B(한도 4,608B), 파일 176KB. 큰 곳(린자니·코모도·바투르)은 화산 권고·공원 규칙 tips 때문이다.

### 지역(2차)

| id | 이름 | kind | hub | 공항 | 곳 수 | 비고 |
|---|---|---|---|---|---|---|
| id_nusa_penida | 누사페니다 | daytrip [id_bali_south], water_crossing | 누사페니다섬 Q1520301(1차 그대로) | — | 3 | 당일 왕복 근거: 끌룽꿍 군청 '누사페니다 개요'(세 명소가 붕아 므까르 마을), Love Bali 켈링킹 페이지 "using passable vessel from harbor" (`_regions/2.txt`). **선사 시간표 근거는 없다**(아래 결정 1) |
| id_lombok | 롬복 | base | 롬복 국제공항 Q1395514 | — (팩에 LOP 없음) | 3 | 길리 트라왕안 52km·린자니 46km 초과 → curation 유지 근거 |
| id_labuanbajo | 라부안바조(코모도) | base | 코모도 국제공항 Q1927261 | — (팩에 LBJ 없음) | 3 | 코모도 NP 43km·핑크 비치 42km 초과 → curation 유지 근거 |

- **LOP(롬복)·LBJ(라부안바조) 공항은 pack.json 에 없어** `airports` 를 비웠다. 팩에 공항을 넣으면 두 지역에 매핑하면 된다.

### 관광지(2차 15곳) — rank 는 나라 전체 D11 재계산(지정 → ko 조회수 → en 조회수, Wikimedia REST 2025-10~2026-09)

| rank | 곳 | 지역 | 종류 | 지정 | ko / en | claims/tips/tags/인용 | facts | 가는 법 | 출처(핵심) |
|---|---|---|---|---|---|---|---|---|---|
| 3 | 바투르산(킨타마니 전망) `kintamani-batur` | id_bali_ubud | nature, **risk volcano** | 유네스코 세계지질공원(2015) | 477 / 54,082 | 6/3/1/12 | 누구나 | 차로만 | Love Bali, UNESCO 지질공원, MAGMA(1단계 정상) |
| 4 | 코모도 국립공원 `komodo-national-park` | id_labuanbajo | nature | 세계유산(1991) | 420 / 55,920 | 6/4/1/16 | 시설·유료 | 배(라부안바조 항구) | 관리청 공식(규칙·티켓), UNESCO 609 |
| 6 | 파다르섬 `padar-island` | id_labuanbajo | nature | 세계유산 일부 | — / 13,553 | 5/3/2/14 | 시설·유료 | 배 | WI 파다르, 관리청 공식 |
| 7 | 핑크 비치(코모도) `pink-beach-komodo` | id_labuanbajo | sea_island | 세계유산 일부 | 위키 없음 | 4/3/2/16 | 시설·유료 | 배 | WI 핑크비치, 관리청 공식 |
| 8 | 우붓 왕궁 `ubud-palace` | id_bali_ubud | heritage | — | **996** / 19,896 | 4/2/0/8 | 시설(입장·휴무 미상) | 시내 걸어서 | WI Istana Ubud(인도네시아어 2문단) |
| 10 | 길리 트라왕안 `gili-trawangan` | id_lombok | sea_island | — | 493 / 1,377 | 4/3/0/11 | 누구나 | 배(승기기 항구) | WI 길리 |
| 12 | 누사두아 해변 `nusa-dua-beach` | id_bali_bukit | sea_island | — | 283 / 19,391(누사두아 문서) | 4/2/0/8 | 누구나 | 차로만 | Love Bali, WI |
| 13 | 인도네시아 국립박물관 `museum-nasional` | id_jakarta | museum | — | 220 / 12,460 | 5/3/1/15 | 시설·유료·**월요일 휴관**(국경일도) | 버스(트랜스자카르타 모누멘 나시오날) | 박물관 공식 프로필·요금/시간 페이지 |
| 18 | 띠르따 엠풀 사원 `tirta-empul` | id_bali_ubud | heritage | — | — / 25,480 | 5/3/1/13 | 시설(입장·휴무 미상) | 차로만 | WI(상세), Love Bali |
| 26 | 린자니 국립공원 `rinjani` | id_lombok | nature, **risk volcano** | — | — / 1,562(국립공원 문서) | 6/4/1/18 | 시설 | 차로만(슴발룬 라왕 기점) | KSDAE 프로필(41,330ha), WI, MAGMA(**2단계 주의**) |
| 28 | 앙겔스 빌라봉 `angels-billabong` | id_nusa_penida | nature | — | 위키 없음 | 4/2/0/7 | 누구나 | 차로만(섬 안) | 끌룽꿍 군 관광청(기사 전재)·군청 |
| 29 | 브로큰 비치 `broken-beach` | id_nusa_penida | sea_island | — | 위키 없음 | 4/2/0/7 | 누구나 | 차로만(섬 안) | 끌룽꿍 군 관광청 Broken Beach 페이지 |
| 30 | 뜨갈랄랑 계단식 논(쩨낑) `ceking-rice-terrace` | id_bali_ubud | nature | — | 위키 없음 | 4/3/0/9 | 누구나 | 차로만 | Love Bali 'Ceking (Panorama)', WI Tegallalang |
| 32 | 켈링킹 해변 `kelingking-beach` | id_nusa_penida | sea_island | — | 위키 없음 | 4/2/1/9 | 누구나 | 차로만(섬 안) | Love Bali, WI |
| 34 | 꾸따 해변(만달리카) `kuta-mandalika` | id_lombok | sea_island | — | 위키 없음 | 4/3/1/10 | 누구나 | 차로만 | ITDC(지구·비치 파크·발라위스타), WI |

**한 줄 미리보기(summary_ko)**
- 우붓 왕궁: 우붓 왕가가 지금도 사는 우붓 시내의 왕궁이에요. / 띠르따 엠풀: 성스러운 샘물에서 정화 의식을 하는 발리 힌두 사원이에요. / 국립박물관: 1778년 협회에서 비롯한 인도네시아의 국립박물관이에요. / 누사두아: 리조트 단지 앞에 있는 발리 남동쪽 끝의 흰 모래 해변이에요. / 쩨낑: 우붓 북쪽 뜨갈랄랑의 계단식 논 전망 지점이에요. / 바투르산: 칼데라 호수를 내려다보는 발리 북동부의 활화산이에요.
- 켈링킹: 새끼손가락 모양 곶 아래에 숨은 누사페니다의 흰 모래 해변이에요. / 브로큰 비치: 절벽 가운데 둥근 구멍으로 바닷물이 드나드는 누사페니다의 해안이에요. / 앙겔스 빌라봉: 바다로 이어지는 물길 끝에 생긴 누사페니다의 맑은 자연 못이에요.
- 꾸따 만달리카: 만달리카 관광지구에 있는 롬복 남부의 흰 모래 해변이에요. / 길리 트라왕안: 자동차가 없는 롬복 북서쪽 바다의 작은 섬이에요. / 린자니: 인도네시아에서 두 번째로 높은 화산을 품은 롬복의 국립공원이에요.
- 코모도: 코모도왕도마뱀이 사는 섬들로 이뤄진 세계유산 국립공원이에요. / 파다르섬: 언덕 위에서 세 개의 만을 내려다보는 코모도 국립공원의 섬이에요. / 핑크 비치: 코모도섬에 있는 분홍빛 모래 해변이에요.

**곳별 메모·빈칸**
- 바투르산: 방문 지점은 **페넬로칸 전망 길가**(OSM node/13258153820, Wikidata 'Penelokan Batur' Q116266091 에는 좌표 없음). qid 는 바투르산 Q43876(위키 조회수·요약용), `wd_geo` 는 산 정상 좌표. hazard = MAGMA 바투르 보고(2026-10-08, **1단계 정상**, "분화구 활동 구역에 오래 머물거나 밤을 보내지 말고 가스 분출구에 가까이 가지 말 것")를 tips·status.note 에 실었다. 지정 = `unesco_geopark`(PH 초콜릿 힐의 보홀 지오파크 선례). 우붓 hub 에서 29km 라 id_bali_ubud 에 뒀다(SPEC §3.3 메모대로).
- 린자니: 방문 지점 = 슴발룬 라왕 마을(등산 기점, Q12513392, `geo.kind: entrance`), qid 는 국립공원 Q1381840. MAGMA 기준 **2단계(Waspada)** — 바루자리 분화구 반경 1.5km 안 활동·야영 금지 권고, 등산은 허용(마스크·눈 보호구 준비)을 claims·tips·status.note 에 실었다. 공식 사이트 rinjaninationalpark.id 는 503 이라 KSDAE 프로필 페이지를 official_url 로 썼고, eRinjani 예약 의무는 공식 근거를 못 찾아 booking 을 비웠다.
- 코모도: 방문 지점 = 로 리앙 관리소(OSM node/6865781785). D19 판정 (가): 야생 서식지에서 레인저와 함께 관찰하는 곳이고 먹이 주기·쇼가 없다(관리청: "먹이를 주지 않는다"). 공식 규칙 페이지에서 '혼자 다닐 수 없음·timed entry·동물 접근 금지·PNBP 티켓'을 tips·facts 로. 요금표는 이미지라 숫자 없음(D4-A 와 무관). SiOra 예약 앱은 공식 문장이 '다운로드' 링크뿐이라 booking 비움.
- 파다르섬·핑크 비치: 세계유산 코모도 국립공원 **안**에 있어 '부분 지정 = 지정' 규칙으로 `unesco` 태그·designation 을 달았다(UNESCO 본문이 파다르를 3대 섬으로 명시; 핑크 비치는 코모도섬 안). 핑크 비치는 Wikidata 항목이 없어 OSM way/762003934. 파다르 좌표는 Wikidata '파다르 브사르섬' Q1134717.
- 우붓 왕궁(한국어 조회수 996, 1차 보류 1순위): 기안야르 군 사이트는 Cloudflare 차단, 왕궁 자체 사이트 없음 → **인도네시아 관광부 공식 포털 페이지**(인도네시아어 2문단)로 채웠다. 입장 유무·휴무 미상, 공연은 D21대로 '저녁에 열려요' 사실만.
- 띠르따 엠풀: WI 상세 페이지(926년 창건·마누까야 마을·물구멍 30개·사롱 규정) + Love Bali. 복장 규정 → `dress_code`. 입장료는 공식 문구가 없어 `entry: unknown`.
- 국립박물관: 공식 요금·시간 페이지에 '월요일·국경일 휴관', 외국인 요금 → `foreigner_price`. 가는 법은 트랜스자카르타 노선 목록의 '모누멘 나시오날' 종점(1차 모나스와 같은 출처, 스냅샷 재수집).
- 누사두아 해변: Wikidata 는 리조트 지구 항목 Q277598(좌표가 지구 중심) → 해변 좌표는 OSM relation/3498598. '아시아태평양 최초 그린 글로브' 문장은 최상급 근거 규칙 때문에 쓰지 않았다.
- 쩨낑(뜨갈랄랑): Love Bali 에 공식 장소 페이지가 있어 넣었다(Wikidata 'Ceking Terrace' Q134730557 에 좌표 없음 → OSM 전망 지점 node/13313259901). WI 페이지의 '유네스코 세계유산 등재' 문장은 1차 메모대로(발리 수박 경관 구성 요소에 들지 않음) **쓰지 않았다**.
- 누사페니다 3곳: 켈링킹은 Love Bali+WI(70~80도 절벽 계단 → `stairs`). 브로큰 비치는 군 관광청 공식 페이지. **앙겔스 빌라봉은 군 관광청 사이트에 실린 글이 Liputan6 기사 전재**라 출처 성격이 약하다(아래 결정 2). 끌룽꿍 군수 공문(SE 556/028/Dispar/2023, 수영 금지)은 원문을 찾지 못해 싣지 않았다.
- 꾸따 만달리카: Wikidata 좌표(-8.9167,116.2833)가 해변에서 약 2km 바다 쪽이라 OSM 해변 relation/3899716 을 geo 로, Wikidata 좌표는 wd_geo 로 남겼다. 별칭에 '꾸따'·'꾸따 해변'은 발리 꾸따와 겹쳐 넣지 않았다.
- 길리 트라왕안: access 는 WI 의 '승기기에서 스피드보트·정기 여객선' 문장으로 `boat` + nearest_local 승기기(Q12513615). 방살 항구 문장은 공식 페이지에 없어 쓰지 않았다. 해양보전구역(TWP Gili Matra, KKP) 데이터베이스는 접속 불가(연결 거부·타임아웃).

### 보류분 처리 결과

| 곳 | 결과 |
|---|---|
| 우붓 왕궁 | **해결**(rank 8) |
| 띠르따 엠풀 | **해결**(rank 18) |
| 인도네시아 국립박물관 | **해결**(rank 13) |
| 누사두아 해변 | **해결**(rank 12) |
| 스미냑 해변 | **계속 보류** — 공식 장소 페이지 없음. Love Bali 는 인접 쁘띠뜽엣·르기안 해변만 있고, 바둥 군 관광청(sita.badungkab.go.id)은 자동 요청·실제 브라우저 모두 연결 안 됨(dispar.badungkab.go.id 는 열리나 장소 안내 없음). 바둥 군 공식 안내가 생기면 Wikidata Q12502896 으로 넣는다 |
| 뜨갈랄랑 | **해결**(쩨낑 공식 페이지 확인, rank 30) |
| 킨타마니·바투르 | **해결**(MAGMA 실제 브라우저 읽기 성공, 현재 단계까지 실음) |
| 꼬따 뚜아(파타힐라 광장) | 그대로(역사박물관 mentions) |
| 누사페니다·롬복·라부안바조 | **지역 채움**(각 3곳) |

### 아직 막힌 출처 (우회하지 않음)
- gianyarkab.go.id(Cloudflare 403), dispar.gianyarkab.go.id·sita.badungkab.go.id·disbudpar.ntbprov.go.id·lombokutarakab.go.id(연결 실패/타임아웃), lomboktengahkab.go.id(봇 확인), rinjaninationalpark.id(503), bpolbf.id(DNS 없음), komodo-park.com(폐쇄, KSDAE 가 아직 링크), kkji.kp3k.kkp.go.id·sidako.kkp.go.id(연결 실패), disparda.baliprov.go.id 의 켈링킹 페이지(404, 홈만 열림).

### 설정 › 출처 문자열 (필요, 앱 담당이 추가)

```xml
<string name="settings_credit_unesco_609">UNESCO World Heritage Centre — Komodo National Park — 코모도 국립공원·파다르섬·핑크 비치</string>
<string name="settings_credit_unesco_geopark_batur">UNESCO International Geoscience and Geoparks Programme — Batur UNESCO Global Geopark — 바투르산(킨타마니)</string>
```
(`settings_credit_osm` 은 이미 있음. 검사할 때 위 2개를 임시로 넣고 되돌렸다.)

### 사장님 결정이 필요한 것

1. **누사페니다 당일 왕복 근거**: 선사 시간표 대신 발리주·끌룽꿍 군 공식 문구(항구에서 배)만 있다. 이대로 daytrip 으로 둘지.
2. **앙겔스 빌라봉 출처**: 끌룽꿍 군 관광청 사이트에 실린 글이지만 Liputan6 기사 전재다. 군 관광청이 게시한 안내로 보고 둘지, 뺄지(빼도 누사페니다는 2곳이라 daytrip 최소 1곳은 넘는다).
3. **린자니 2단계(주의)**: MAGMA 권고(바루자리 반경 1.5km 금지, 등산 허용)를 그대로 실었다. risk volcano 라 status 확인 주기 30일. 이 수준으로 공개할지.
4. **LOP·LBJ 공항**: pack.json 에 두 공항을 넣을지(넣으면 지역에 매핑).
5. 40km 초과 4곳(길리 트라왕안·린자니·코모도·핑크 비치)의 유지 근거(`packs/curation/ID.curation.json`)가 적절한지.
