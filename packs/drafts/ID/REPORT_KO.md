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

## 3차 (2026-10-10)

- 데이터: `packs/drafts/ID/attractions.json` version **2026.10.10-2** — 지역 9개(이번엔 새 지역이 없어 wave 3 지역은 없음) / 관광지 **56곳**(공개본 33 + 3차 23). 기존 33곳 본문은 손대지 않았고 rank.order·위키 제목만 나라 전체 D11로 다시 계산했다. 서명 바이트 203,971B(한도 256,000B), 곳당 2,165~3,848B(한도 4,608B), 출처 133개(OSM 좌표 출처 포함).
- 방침: 사장님 결정(2026-10-10)대로 공공·문화유산 제한(D21-C)을 풀고 **민간 테마파크·수족관·박물관·공연장**을 운영사 공식 사이트로 채웠다. D13(투어·액티비티 업체)·D19(먹이주기·쇼·타기가 전부인 곳)는 그대로 적용했다.
- 근거: `~/.readyport/evidence/ID/<id>/` 에 곳마다 스냅샷 + `extract.json`(23곳 인용 268개, 확인일 모두 2026-10-10). 좌표는 Wikidata P625 우선(18곳), 없는 5곳은 OpenStreetMap(`dufan-ancol` way/119280267, `atlantis-ancol` way/210651751, `crystal-bay` relation/9864841, `suluban-beach` node/6278550285, `rinca-island` way/862984883; 나라 전체 OSM 11곳). 모든 사이트는 robots.txt를 확인한 뒤 HTTP GET으로만 읽었고 막힌 곳은 우회하지 않았다. 실제 브라우저 읽기(`manual_browser_save`)는 이번에 쓰지 않았다.
- 운영 시간·요금 숫자는 한 곳도 싣지 않았고(D4-A) `entry: free` 는 0건이다. 시설이 요금을 밝힌 곳만 `paid`.

### 검증

- `check ID`: **통과**(실패 0). 남은 경고는 1·2차와 같은 종류(hub 40km 초과 4곳은 `ID.curation.json` 유지 근거 있음, 롬복 두 곳 60km 초과, booking 없음, sea_island·nature 방문 지점 권장).
- `verify-quotes ID`: **통과**(56곳). `wiki-fill ID`: 신규 23곳 중 16곳 채움(워터봄·두판·아틀란티스·크리스탈 베이·아투·그린 볼·술루반은 한국어·영어 위키 문서 없음). `place-ids ID --write`: 신규 23곳 모두 채움.
- copycheck: 캐시 `~/.readyport/copycheck_cache/ID/<id>/`(ko·en 위키 본문, 없으면 none.txt) 생성 후 `record`로 `ID.copycheck.json`·`ID.quotes.json` 갱신. promote 시뮬레이션 안에서 **56곳 모두 통과**.
- promote 시뮬레이션(`--wave 3`, 임시 키 `rp-att-sim`, 저장소 밖 복사본에서만): **게이트 실패 0, 실패 0**. 종류 heritage 16 / sea_island 15 / museum 8 / theme_park 7 / nature 7 / city_view 2 / market_street 1(최다 16/56=29%, 50% 미만) · 충실도 미달 0 · base 7지역 모두 3곳 이상 · daytrip 2지역 유지. 서명 산출물은 커밋하지 않았고 진짜 키도 쓰지 않았다.
- 출처 편중(§5.2-14): 사실 문장 387개 기준 발리주 관광 포털(disparda+Love Bali) 약 20%, Wonderful Indonesia 약 11%, ancol.com 약 8% — 30% 한도 아래.

### 관광지(3차 23곳) — rank 는 나라 전체 D11 재계산(지정 → ko 조회수 → en 조회수)

| rank | 곳 | 지역 | 종류 | claims/tips/tags/인용 | 크기 | 좌표 | 핵심 출처 |
|---|---|---|---|---|---|---|---|
| 5 | 린짜섬(로 부아야) `rinca-island` | id_labuanbajo | nature | 6/4/1/15 | 3848B | osm | 코모도 국립공원 관리청, UNESCO 609 |
| 13 | 따만 미니 인도네시아 인다 `tmii` | id_jakarta | theme_park | 4/3/1/14 | 3265B | wd | TMII 공식, 트랜스자카르타 |
| 16 | 자카르타 대성당 `katedral-jakarta` | id_jakarta | heritage | 5/3/0/11 | 3190B | wd | 대성당 공식 |
| 25 | 따만사리(물의 궁전) `taman-sari-yogyakarta` | id_yogyakarta | heritage | 5/4/1/19 | 3749B | wd | 족자 왕궁 관광 사이트, Trans Jogja |
| 29 | 빤다와 해변 `pandawa-beach` | id_bali_bukit | sea_island | 4/2/0/8 | 2794B | wd | Love Bali |
| 31 | 라뚜 보꼬 궁전 유적 `ratu-boko` | id_yogyakarta | heritage | 6/3/0/13 | 3131B | wd | InJourney(운영사) |
| 32 | 안촐 따만 임피안 `ancol-taman-impian` | id_jakarta | sea_island | 4/4/1/13 | 3148B | wd | 안촐 운영사 |
| 33 | 프레데부르흐 요새 박물관 `benteng-vredeburg` | id_yogyakarta | museum | 5/3/1/11 | 3314B | wd | 박물관 공식 |
| 34 | 삼비사리 사원 `candi-sambisari` | id_yogyakarta | heritage | 5/2/1/11 | 3226B | wd | 족자 특별주 관광청 포털 |
| 35 | 소노부도요 박물관 `sonobudoyo` | id_yogyakarta | museum | 6/4/1/17 | 3701B | wd | 박물관 공식(특별주 문화청 산하) |
| 36 | 씨월드 안촐 `sea-world-ancol` | id_jakarta | theme_park | 4/4/0/12 | 3114B | wd | 안촐 운영사 |
| 37 | 울렌 센탈루 박물관 `ullen-sentalu` | id_yogyakarta | museum | 5/3/0/13 | 3207B | wd | 박물관 공식 |
| 39 | 프람바난 라마야나 발레 `ramayana-ballet-prambanan` | id_yogyakarta | theme_park | 4/3/0/11 | 3239B | wd | InJourney(운영사), 티켓 사이트 |
| 40 | 이조 사원 `candi-ijo` | id_yogyakarta | heritage | 4/2/1/10 | 3035B | wd | 족자 특별주 관광청 포털 |
| 42 | 파시피카 박물관 `museum-pasifika` | id_bali_bukit | museum | 4/4/0/13 | 3123B | wd | 박물관 공식 |
| 43 | 아궁 라이 미술관(ARMA) `arma-museum` | id_bali_ubud | museum | 4/3/0/14 | 3138B | wd | ARMA 공식 |
| 44 | 아틀란티스 안촐 `atlantis-ancol` | id_jakarta | theme_park | 4/4/0/11 | 3008B | osm | 안촐 운영사 |
| 45 | 아투 해변 `atuh-beach` | id_nusa_penida | sea_island | 4/1/0/6 | 2414B | wd | Love Bali |
| 48 | 크리스탈 베이 `crystal-bay` | id_nusa_penida | sea_island | 3/2/0/6 | 2433B | osm | Love Bali |
| 49 | 둔야 판타시(두판) `dufan-ancol` | id_jakarta | theme_park | 4/4/0/11 | 3176B | osm | 안촐 운영사 |
| 50 | 그린 볼 해변 `green-bowl-beach` | id_bali_bukit | sea_island | 3/2/1/7 | 2523B | wd | Love Bali |
| 55 | 술루반 해변(블루 포인트) `suluban-beach` | id_bali_bukit | sea_island | 3/1/0/5 | 2165B | osm | Love Bali |
| 56 | 워터봄 발리 `waterbom-bali` | id_bali_south | theme_park | 5/4/1/17 | 3652B | wd | 워터봄 공식(운영사) |

신규 23곳 사실(claims·tips·tags) 180개, 인용 268개.

**곳별 메모**
- 워터봄 발리: 운영사 사이트(홈·Our Park·F.A.Q·Accessibility·Contact). 연중 무휴이고 **녜피(발리 힌두 새해)에만 쉼**을 `seasonal` closed + `regular_closed: none` 으로 넣었다. 성수기 하루 전 예약 권고 → `booking: recommended`. 슬라이드 탑이 계단뿐이라는 접근성 문구 → `stairs` 태그. 가는 법은 대중교통 근거가 없어 `car_only`(전용 주차장 문구).
- TMII: 운영 안내(개장 1975년 4월·150헥타르·2023년 9월 새 단장·녹지 70%), 케이블카 → `cable_car` 태그, 가는 법은 트랜스자카르타 'TMII - PANCORAN' 노선(공식 노선 목록). 입장권 가격표의 날짜별 숫자는 싣지 않았다.
- 두판·씨월드·아틀란티스·안촐 따만 임피안: 안촐 운영사(PT Pembangunan Jaya Ancol) 공식 사이트. 각 표가 **안촐 입장권 별도**라는 문구를 tips 에 넣었다. 씨월드의 다이버 먹이 주기 쇼는 D19 (가) — 수조 28곳 관람이 방문 이유라 포함하고 쇼는 tips 에 운영사 안내로만 적었다. 가는 법은 안촐 사이트의 KRL 안촐역·캄풍 반단역·트랜스자카르타 안촐 정류장 문장.
- 울렌 센탈루: 가이드 투어로만 관람(투어 3종·영어 투어 별도·단체 예약 필수), **월요일 휴관**.
- 따만사리: 왕궁(Kagungan Dalem)이 직접 운영하는 관광지. 복장(소매 있는 옷, 치마·반바지 불가)을 `dress_code` 태그로, 가는 법은 Trans Jogja 노선의 'Portabel Tejokusuman (Tamansari)' 정류장.
- 라뚜 보꼬·라마야나 발레: InJourney(운영사). 라마야나 발레는 소개 페이지(화·목·토)와 티켓 페이지(화·목·금·토)의 공연 요일이 달라서 **요일을 싣지 않고** '일정은 예고 없이 바뀐다'는 공식 문장만 tips 로 넣었다. 비가 오면 실내 극장으로 옮긴다는 점, 환불 불가 조건도 tips 에 있다. 공연장은 D21 폐지로 theme_park.
- 소노부도요: 월요일 휴관, 외국인 요금 별도(`foreigner_price`), 저녁 와양 공연·영화 상영, 본관 입구 두 곳과 별관.
- 프레데부르흐 요새 박물관: 현재 프로필 페이지(Indonesian Heritage Agency 관리, 소장품 7,000점 넘음)와 역사 페이지(1765년 12월 VOC 공사 시작)를 썼다. 요금 페이지에 외국인 요금이 따로 있어 `foreigner_price`. 방문 시간 페이지와 팬데믹 때 공지의 휴관 요일이 달라 `regular_closed: unknown`.
- 삼비사리·이조 사원: 족자카르타 특별주 관광청 포털(visitingjogja) 기사. 기사 날짜가 오래됐다(삼비사리 2019). 수치(지표 아래 6.5m, 해발 410m, 17개 구조물 등)는 기사 그대로이고 입장 정보는 없어 `entry: unknown`.
- 아궁 라이 미술관(ARMA): 입장료 안내 페이지에 입장료와 어린이 무료 문구, 공연(금·토·일 저녁)과 워크숍 안내.
- 파시피카 박물관: 공식 사이트의 'About'과 'Admission' 정책. 사이트 안에서 부지 면적(12,000/12,500㎡)이 엇갈려 면적은 쓰지 않았고, 순위·트립어드바이저 문구는 옮기지 않았다.
- 빤다와·그린 볼·술루반·크리스탈 베이·아투: Love Bali(발리주 정부 포털) 짧은 소개문 기반이라 사실이 적다(곳당 claims 3~4). 공개 해변이라 `facts.kind: public_space`.
- 린짜섬(로 부아야): 코모도 국립공원 관리청 사이트의 destinasi 목록·방문 규칙·가이드 안내·fauna 페이지와 UNESCO 609. `unesco` 태그와 designation 은 파다르·핑크 비치와 같은 '부분 지정 = 지정' 규칙. **가는 법(배)** 은 공식 문장이 약해 규칙 페이지의 수상 교통 문구에 의지했다(아래 결정 3).

### 이번에도 못 한 것 / 보류 (이유)

| 곳 | 결과 |
|---|---|
| 롬복(LOP) 공항 pack.json 추가 | **안 함** — 공항 공식 사이트(lombok-airport.co.id, 2016 저작권 표기)에 입국 절차 글이 없고 버스·택시 안내만 있다. e-VOA·세관 절차의 공식 근거를 찾지 못했다 |
| 라부안바조(LBJ) 공항 pack.json 추가 | **안 함** — 공항 공식 사이트가 열리지 않는다(도메인 확인 실패). 입국 절차 근거 0 |
| 롬복 보강 | 보류 — NTB 도·군 관광 사이트(disbudpar.ntbprov.go.id, lombokbaratkab·lomboktimurkab 등)와 KSDAE 도메인(ksdae.kehutanan.go.id)이 연결되지 않고, Wonderful Indonesia 는 인증서 만료로 열 수 없다(우회하지 않음). 이번 차수 롬복 신규 0곳 |
| 서부 브두굴(울룬 다누 브라탄·자티루위·바투까루·따만 아윤) 새 daytrip 지역 | 보류 — Love Bali 한 단락과 UNESCO 1194(구성 요소 이름만)뿐이라 '가기 전에 알아 둘 것' 2줄·가는 법을 채울 근거가 없다(wave 3 지역으로 만들 수 없음) |
| 떼게능안 폭포·스쿰풀 폭포·군웅 까위·따만 우중·께르따 고사·뻥글리뿌란 마을·고아 라와·따만 사라스와띠 | 보류 — Love Bali 소개문이 한두 문장이고 입장·복장·휴관 등 안내가 없어 상세 충실도(§5.2-13) 미달 |
| 타만 사파리 발리·발리 버드 파크 | **D19 보류** — 먹이 주기·쇼·코끼리 등 타기 상품이 방문 설명의 큰 부분이라 '먹이주기·쇼·타기를 빼도 방문 이유가 남는가' 판정이 애매. 사장님 결정 사항 |
| 자카르타 아쿠아리움 | 보류 — 공식 사이트가 서버 오류(500) |
| 무세움 마칸(MACAN) | 보류 — 공식 사이트 상단에 '일시 휴관(tutup sementara)' 문구가 떠 있어 재개 확인 후 추가 |
| 타만 핀타르 | 보류 — 요금·시간 페이지가 비어 있어 가기 전 정보 2줄 미달 |
| 스미냑 해변·꼬따 뚜아(파타힐라 광장) | 계속 보류(공식 장소 안내 없음) |
| 메라피 화산 박물관·알룬알룬 끼둘·뚜구·께딴단 마을 | 보류 — 관광청 포털 기사가 제3자 글 전재(박물관) 또는 광고성 서술이라 출처로 쓰지 않음 |
| 자카르타 주정부 박물관 5곳(와양·바하리·세니 루빠 단 께라믹·따만 쁘라사스띠·뗵스띨) | 보류 — 주정부 포털 소개문은 있으나 요금·시간 문서가 PDF뿐이라 가기 전 정보 2줄 미달 |

### 설정 › 출처 문자열 (필요, 앱 담당이 추가)

새 `attribution_required` 출처는 없다(`unesco_609` 와 `osm` 키는 이미 있음). 다만 린짜섬이 `unesco_609` 를 쓰므로 문구에 곳 이름을 하나 더 넣는 걸 권한다.

```xml
<string name="settings_credit_unesco_609">UNESCO World Heritage Centre — Komodo National Park — 코모도 국립공원·파다르섬·핑크 비치(코모도)·린짜섬</string>
```

### 사장님 결정이 필요한 것

1. **타만 사파리 발리·발리 버드 파크(D19)**: 사파리 차량·공원 관람이 중심이지만 코끼리 타기·먹이 주기·쇼 상품이 크다. 넣으려면 '관람 부분만 싣고 상품은 tips 에 운영사 안내로만' 방식으로 진행해도 되는지.
2. **롬복·라부안바조 공항**: 입국 절차의 공식 근거를 못 찾아 pack.json 에 넣지 않았다. 공식 도착 안내(DPS 의 InJourney 도착 가이드 같은 것)가 생기면 `airports` 를 채우고 두 지역에 매핑한다.
3. **린짜섬 가는 법**: '라부안바조에서 배' 문장이 관리청 사이트에는 없고 규칙 페이지의 수상 교통 문구로만 뒷받침된다. 이대로 둘지, 근거가 생길 때까지 `access` 를 비울지.
4. **관광청 포털 기사(삼비사리·이조)**: 지자체 관광 포털이라 허용 출처이지만 2019년 기사 위주라 날짜가 오래됐다. 이 수준으로 공개할지.
5. **프레데부르흐 휴관 요일**: 현재 방문 시간 페이지에는 월요일이 없고 팬데믹 공지에는 '월요일·공휴일 휴관'이 있다. 공식 사이트에서 확인될 때까지 unknown 으로 둔 게 맞는지.
6. 안촐 운영사 한 곳에서 4곳(두판·씨월드·아틀란티스·안촐 따만 임피안)을 넣었다. 출처 편중 한도(30%)는 넘지 않지만(ancol.com 약 8%), 4곳이 모두 '안촐 입장권 별도' 구조라 앱에서 한 묶음으로 보여 줄지.
