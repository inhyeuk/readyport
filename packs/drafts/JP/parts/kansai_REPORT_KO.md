# 간사이 관광지 10곳 추가 보고 — 2026-10-09

- 데이터: `packs/drafts/JP/parts/kansai.json` (`regions: []` — 기존 jp_osaka·jp_kyoto·jp_nara 그대로, 관광지 10곳, 새 출처 53개)
- 근거: 저장소 밖 `~/.readyport/evidence/JP/<id>/` — 페이지 전체 글 스냅샷(`N.txt`, URL·수집일 머리말) + `extract.json`(시범과 같은 형식, 인용 214개). 원문은 커밋하지 않았다.
- 모든 사실 칸(claims·tips·tags·facts·access·address_local·status)은 허용 출처(시설 공식·관광청·문화청 DB·유네스코) 인용으로 뒷받침했고 확인일은 모두 2026-10-09. robots.txt를 확인했고 Google 지도·리뷰 사이트는 쓰지 않았다.
- D4-A: 여는 시간·요금 숫자 없음(인용 원문에만 있음). D12: 위키 문장을 옮기지 않고 공식 사실만 보고 새로 썼다(해요체).
- 입장 무료는 시설 공식 문구가 있는 곳만(스미요시 타이샤 FAQ 'Entrance is free.'). 나머지는 공식 요금 안내로 `paid`.

## 검증 (임시 병합 후, 병합본은 되돌림)

- `check JP`: **통과** (경고: facts.booking 없음 5곳 — 공식 예약 안내가 없어서 비움, 게이트 경고만)
- `verify-quotes JP`: **통과** (10곳 모두, 필드별 출처 일치)
- 지역 lint: 10곳 모두 hub에서 40km 안(가장 먼 곳: 아라시야마–교토역 약 8km, 가이유칸–난바역 약 7km). 곳당 크기는 모두 4,608B 이하(니조성 4,605B로 아슬아슬).

### 합칠 때 꼭 함께 넣을 것 (이 커밋에는 parts 파일만 넣음)
새 출처 3개가 `attribution_required: true`(문화청 DB·유네스코)라 문자열이 없으면 `check`가 실패한다. 검증할 때 아래 3줄을 임시로 넣었다가 되돌렸다. `app/src/main/res/values/strings_attractions.xml`의 `settings_credit_unesco_870` 다음에 추가:
```xml
<string name="settings_credit_unesco_688">UNESCO 세계유산센터 — 고대 교토의 역사기념물</string>
<string name="settings_credit_bunka_arashiyama">문화청 국가지정문화재 데이터베이스 — 아라시야마(사적·명승)</string>
<string name="settings_credit_bunka_tsutenkaku">문화청 국가지정문화재 데이터베이스 — 쓰텐카쿠(등록유형문화재)</string>
```
- `unesco_870`(도다이지)은 기존 출처를 그대로 썼다(새 문자열 불필요).
- `rank.order`는 이 묶음 안에서 101~110으로 매겼다(D11 순서). 다른 권역 parts와 합칠 때 나라 전체 순서로 다시 매기면 된다.

## 선정 (D11: 공식 지정 → 한국어 조회수 → 영어, 부분 지정 포함 — 오후 결정)

조회수: Wikimedia REST API, 사용자 조회수 2025-10 ~ 2026-09 합계.

| 지역 | 곳 | 지정 | 한국어 | 영어 | 종류 | 결과 |
|---|---|---|---|---|---|---|
| 교토 | 기요미즈데라 | 세계유산·국보(본당) | 7,320 | 134,340 | heritage | ✅ |
| 나라 | 도다이지 | 세계유산·국보(대불전) | 5,447 | 128,308 | heritage | ✅ |
| 교토 | 니조성 | 세계유산·사적·국보 | 2,935 | 76,311 | heritage | ✅ |
| 교토 | 아라시야마 | 사적·명승 | 2,662 | 28,290 | nature | ✅ |
| 교토 | 금각사(로쿠온지) | 세계유산·특별사적·특별명승 | 2,450 | 132,469 | heritage | ✅ |
| 교토 | 뵤도인(우지) | 세계유산·국보 | 2,383 | 51,208 | heritage | ✗ 종류 균형(heritage 50% 상한) — 다음 차례 |
| 교토 | 긴카쿠지(지쇼지) | 세계유산·국보 | 2,075 | 28,965 | heritage | ✗ 종류 균형 — 다음 차례 |
| 교토 | 기온 | 전통건조물군 보존지구 | 1,957 | 44,714 | (heritage) | ✗ 종류 균형 |
| 교토 | 야사카 신사 | 국보·중요문화재 | 1,724 | 37,175 | heritage | ✗ 종류 균형 |
| 교토 | 교토 국립박물관 | 중요문화재(메이지 고토칸·정문) | 433 | 8,015 | museum | ✅ 박물관 종류 |
| 오사카 | 쓰텐카쿠 | 등록유형문화재 | 951 | 29,712 | city_view | ✅ |
| 오사카 | 스미요시 타이샤 | 국보(본전 4채) | 361 | 12,970 | heritage | ✅ (지정 우선) |
| 오사카 | 시텐노지 | (공식 사이트에 건물 지정 문구 없음, Wikidata P1435 없음) | 1,460 | 32,030 | heritage | ✗ 지정 근거 확인 못 함 |
| 오사카 | 유니버설 스튜디오 재팬 | — | 5,107 | 159,262 | theme_park | ✗ **공식 사이트가 봇 차단(Akamai)이 걸린 JS 앱** — 우회 금지라 공식 사실을 못 받음. 오사카관광국 페이지는 요금 한 줄뿐이라 상세 최소 충실도 미달 |
| 오사카 | 신사이바시 | — | 2,549 | 29,126 | market_street | ✗ 도톤보리와 붙어 있는 같은 종류 |
| 오사카 | 아베노 하루카스 | — | 1,075 | 20,856 | city_view | ✗ 쓰텐카쿠와 같은 종류 |
| 오사카 | 우메다 스카이빌딩 | — | 814 | 32,762 | city_view | ✗ 같은 종류 |
| 오사카 | 구로몬 시장 | — | 786 | (영문 문서 없음) | market_street | ✗ 같은 종류(도톤보리) |
| 오사카 | 가이유칸 | — | 507 | 36,557 | theme_park | ✅ 테마파크 종류(D19 (가) 수족관) |
| 교토 | 니시키 시장 | — | 602 | 12,154 | market_street | ✅ 교토 시장 종류 |

- 간사이 14곳 종류: heritage 7(오사카성·스미요시·후시미·기요미즈·니조·금각사·도다이지) = 50%(초과 아님), nature 2(나라 공원·아라시야마), market_street 2, city_view 1, museum 1, theme_park 1.
- 지역별: 오사카 5(목표 5) · 교토 7(목표 7) · 나라 2(목표 2).
- 신세카이는 쓰텐카쿠의 '설명에 나와요'로 넣었다(같은 곳).


---

## 1. 기요미즈데라 (`kiyomizu-dera`, rank.order 101)

```
기요미즈데라
Kiyomizu-dera
[일본 · 교토] [역사·유적] [세계유산] [휠체어로 다닐 수 있어요]

어떤 곳이에요
  기요미즈데라는 778년에 세워진 절로, 교토 동쪽 히가시야마의 오토와산 중턱에 있어요. 절 이름 '기요미즈(맑은 물)'는 경내 오토와 폭포의 맑은 물에서 왔어요. 본당 앞으로 내민 무대는 높이가 13m 가까이 되고, 못을 쓰지 않고 짜 맞춘 느티나무 기둥 18개가 받치고 있어요. 국보인 본당을 비롯해 지금 건물 대부분은 1633년에 다시 지은 것이고, 절은 세계유산 '고대 교토의 역사기념물'의 하나예요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 경내로 들어가는 길은 기요미즈자카 위 니오몬 쪽과 차완자카 위 비상 도로 입구, 두 곳뿐이에요. 지도 앱이 들어갈 수 없는 길을 안내할 수 있다고 해요.
  • 경내 전체가 금연이고, 카페·찻집 밖에서는 걸으면서 먹거나 마시지 말아 달라고 해요.
  • 드론·셀카봉(모노포드)·삼각대를 쓴 촬영은 금지예요.
  • 절에 주차장이 없어서 대중교통이나 택시로 오라고 권해요.
  ↗ 공식 사이트 (https://www.kiyomizudera.or.jp/)

가는 법
  가까운 곳: 시버스 고조자카 정류장에서 동쪽으로 걸어가요 (게이한 기요미즈고조역에서도 걸어갈 수 있어요) (현지어: 清水五条駅)
  [ 기사님께 보여 주기 ]  →  清水寺 / 京都市東山区清水1丁目294
```
- 별칭(검색): 청수사, 기요미즈 절 / 영어 Kiyomizu Temple / 설명에 나와요: 오토와 폭포, 기요미즈 무대
- 출처: kiyomizu_official_access_ja https://www.kiyomizudera.or.jp/access.php · kiyomizu_official_faq https://www.kiyomizudera.or.jp/en/faq/ · kiyomizu_official_faq_ja https://www.kiyomizudera.or.jp/faq.php · kiyomizu_official_learn https://www.kiyomizudera.or.jp/en/learn/ · kiyomizu_official_location https://www.kiyomizudera.or.jp/en/location/ · kiyomizu_official_visit https://www.kiyomizudera.or.jp/en/visit/ · unesco_688 https://whc.unesco.org/en/list/688/maps/ · 좌표·이름 Wikidata Q221716
- 사실 칸(인용 필수 필드) 14 · 인용 23 · 출처 7 · 크기 4,508B
- 지정: 세계유산 구성 자산(688-004) · 본당 국보(부분 지정) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 7,320 / 영어 134,340
- 빈 칸·약한 근거: 쉬는 날(공식 문구 없음 → 'unknown', 화면에 안 그림), 예약 안내 없음. 공식 영문 페이지의 세계유산 등재 연도가 '1944'로 잘못 적혀 있어(유네스코 1994) 연도는 쓰지 않았다. 방문 지점은 절 좌표(니오몬 항목 미사용).

## 2. 도다이지 (`todaiji`, rank.order 102)

```
도다이지
Tōdai-ji
[일본 · 나라] [역사·유적] [세계유산]

어떤 곳이에요
  도다이지는 쇼무 천황이 743년 큰 비로자나불상을 만들겠다는 조칙을 내리면서 시작된 절이에요. 청동 대불은 749년에 완성됐고, 대불을 모신 대불전은 751년에 다 지어졌어요. 대불전은 1180년과 1567년 두 번 불탔고, 지금 건물은 에도 시대에 다시 지으면서 정면 폭이 처음보다 좁아졌어요. 대불과 대불전은 일본 국보이고, 도다이지는 1998년 세계유산 '고대 나라의 역사기념물'에 들어갔어요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 대불전 말고 다른 건물 안에서는 사진을 찍을 수 없어요. 참배길에서는 기념사진을 찍어도 돼요.
  • 대불전 안에서는 스케치·손전등·삼각대 사용과 단체 사진 촬영이 금지예요.
  • 대불전 매표소를 지나면 음성 안내기를 빌릴 수 있고, 한국어도 있어요(유료).
  • 도다이지에는 주차장이 없어요. 근처 주차장을 써야 해요.
  ↗ 공식 사이트 (https://www.todaiji.or.jp/)

가는 법
  가까운 곳: 긴테쓰나라역에서 동쪽으로 걸어가요 (JR 나라역·긴테쓰나라역에서 시내 순환버스도 다녀요) (현지어: 近鉄奈良駅)
  [ 기사님께 보여 주기 ]  →  東大寺 / 奈良市雑司町406-1
```
- 별칭(검색): 동대사, 도다이지 대불전 / 영어 Todaiji Temple, Daibutsu-den / 설명에 나와요: -
- 출처: todaiji_official_access https://www.todaiji.or.jp/en/access/ · todaiji_official_daibutsuden https://www.todaiji.or.jp/en/information/daibutsuden/ · todaiji_official_haikan https://www.todaiji.or.jp/en/information/haikan/ · todaiji_official_history https://www.todaiji.or.jp/en/history/narajidai/ · todaiji_official_ja https://www.todaiji.or.jp/ · todaiji_official_tourist https://www.todaiji.or.jp/en/tourist-information/ · unesco_870 (기존 출처) · 좌표·이름 Wikidata Q3012032
- 사실 칸(인용 필수 필드) 13 · 인용 22 · 출처 7 · 크기 4,282B
- 지정: 세계유산 구성 자산(고대 나라) · 대불전·대불 국보(부분 지정) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 5,447 / 영어 128,308
- 빈 칸·약한 근거: 쉬는 날·예약 공식 문구 없음. 공식 '세계 최대 목조 건축' 문구는 최상급(편집 원칙 11)이라 쓰지 않았다. 좌표는 대불전 항목(Q3012032). 나라 공원 mentions_ko의 '도다이지'와 별개 항목으로 둠.

## 3. 니조성 (`nijo-castle`, rank.order 103)

```
니조성
Nijō Castle
[일본 · 교토] [역사·유적] [세계유산]

어떤 곳이에요
  니조성은 도쿠가와 이에야스의 명으로 1603년에 완성됐고, 쇼군이 교토에서 머물던 성이에요. 1867년 15대 쇼군 도쿠가와 요시노부는 니노마루 궁전에서 도쿠가와 정권을 끝낸다고 밝혔어요. 니노마루 궁전의 건물 6동은 국보이고, 궁전 안에는 벽화가 3,600점 넘게 있어요. 성 전체가 일본 사적이고, 세계유산 '고대 교토의 역사기념물'의 하나예요.

가기 전에 알아 둘 것
  • 쉬는 날 없어요 — 성은 12월 29~31일 쉬어요. 니노마루 궁전은 1·7·8·12월 화요일(공휴일이면 다음 날)과 12월 26~28일, 1월 1~3일에 닫아요.
  • 입장료 있어요
  • 성·니노마루 궁전은 예약이 필요 없어요. 혼마루 궁전은 웹 티켓으로 미리 예약해야 해요.
  • 니노마루·혼마루 궁전 안에서는 사진·영상을 찍을 수 없고, 성 안에서 셀카봉·삼각대도 쓰지 말아 달라고 해요.
  • 성 안에서는 정해진 곳에서만 먹고 마실 수 있어요. 걸으면서 마시면 안 돼요.
  • 성과 궁전은 한 번 나가면 다시 들어갈 수 없어요.
  • 전동 보조 휠체어를 무료로 빌려줘요(수량 한정, 예약 불가).
  ↗ 공식 사이트 (https://nijo-jocastle.city.kyoto.lg.jp/)

가는 법
  가까운 곳: 지하철 도자이선 니조조마에역 (교토역에서는 가라스마오이케역에서 갈아타요) (현지어: 二条城前駅)
  [ 기사님께 보여 주기 ]  →  二条城 / 京都市中京区二条通堀川西入二条城町541
```
- 별칭(검색): 니조조 / 영어 Nijo-jo Castle / 설명에 나와요: 혼마루 궁전
- 출처: jnto_nijo https://www.japan.travel/en/spot/1165/ · nijo_official_annai https://nijo-jocastle.city.kyoto.lg.jp/guide/annai/?lang=en · nijo_official_caution https://nijo-jocastle.city.kyoto.lg.jp/guide/caution/?lang=en · nijo_official_faq https://nijo-jocastle.city.kyoto.lg.jp/guide/faq/?lang=en · nijo_official_ninomaru https://nijo-jocastle.city.kyoto.lg.jp/introduction/highlights/ninomaru/?lang=en · nijo_official_overview https://nijo-jocastle.city.kyoto.lg.jp/introduction/highlights/overview/?lang=en · nijo_official_top_ja https://nijo-jocastle.city.kyoto.lg.jp/ · unesco_688 https://whc.unesco.org/en/list/688/maps/ · 좌표·이름 Wikidata Q1013399
- 사실 칸(인용 필수 필드) 13 · 인용 26 · 출처 10 · 크기 4,605B
- 지정: 세계유산 구성 자산(688-017) · 국가 사적(전체) · 니노마루 궁전 국보 · 위키 조회수(2025-10~2026-09, 사용자) 한국어 2,935 / 영어 76,311
- 빈 칸·약한 근거: 공식 교통 안내의 역 이름이 이미지에만 있어 가까운 역(니조조마에)은 JNTO 공식 페이지로 확인. 관람 소요 시간(1~1.5시간 등)은 숫자가 claims에 없어 뺐다. 크기 4,605B로 상한(4,608B)에 붙어 있음 — 글을 더 늘리면 넘는다.

## 4. 아라시야마 (`arashiyama`, rank.order 104)

```
아라시야마
Arashiyama
[일본 · 교토] [산·자연]

어떤 곳이에요
  아라시야마는 교토 서쪽 오이강(가쓰라강) 가에 솟은 산과 그 협곡 일대로, 1927년 일본 사적·명승으로 지정됐어요. 봄에는 벚꽃, 가을에는 단풍을 보는 곳으로 지정 해설에 적혀 있어요. 산 앞으로 가쓰라강을 건너는 도게쓰교는 400년 넘게 이 지역을 대표해 온 다리예요.

가기 전에 알아 둘 것
  • 누구나 다닐 수 있는 곳이에요
  • 교토역에서는 JR 사가노선이 빠르지만 붐벼요. 지하철 도자이선과 란덴(게이후쿠 전철)을 갈아타는 길도 안내해요.
  • 교토역에서 버스로 가면 붐비고 시간도 오래 걸린다고 해요.
  • JR 사가아라시야마역에서 도게쓰교로 가는 큰길은 차가 많아요. 보도를 벗어나지 말라고 해요.
  • 봄 벚꽃과 가을 단풍 때는 사람이 많이 몰려요.
  ↗ 공식 사이트 (https://kyoto.travel/en/destinations/togetsukyo-bridge/)

가는 법
  가까운 곳: 게이후쿠 전철(란덴) 아라시야마역에서 걸어서 가요 (JR 사가아라시야마역·한큐 아라시야마역에서도 걸어가요) (현지어: 嵐山駅)
  [ 기사님께 보여 주기 ]  →  嵐山
```
- 별칭(검색): 아라시야마 협곡 / 영어 Mount Arashi / 설명에 나와요: 도게쓰교, 가쓰라강
- 출처: bunka_arashiyama https://kunishitei.bunka.go.jp/heritage/detail/401/1659 · kyototravel_access_arashiyama https://kyoto.travel/en/getting-around/comfortable-access-to-saga-arashiyama/ · kyototravel_togetsukyo https://kyoto.travel/en/destinations/togetsukyo-bridge/ · 좌표·이름 Wikidata Q11477257
- 사실 칸(인용 필수 필드) 8 · 인용 17 · 출처 3 · 크기 3,557B
- 지정: 국가 사적·명승(1927 지정, 문화청 DB) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 2,662 / 영어 28,290
- 빈 칸·약한 근거: 공식 운영 주체가 없는 넓은 명승지라 official_url은 교토시 관광협회(공식) 도게쓰교 페이지. public_space라 입장·쉬는 날 없음. 주소 없음. 방문 지점은 란덴 아라시야마역(Q11477257). 대나무숲(지쿠린)은 공식 근거를 따로 못 받아 쓰지 않음.

## 5. 금각사 (`kinkaku-ji`, rank.order 105)

```
금각사
Kinkaku-ji
[일본 · 교토] [역사·유적] [세계유산]

어떤 곳이에요
  금각사의 정식 이름은 로쿠온지로, 쇼코쿠지에 딸린 선종 절이에요. 무로마치 막부 3대 쇼군 아시카가 요시미쓰가 1397년 이곳에 산장을 짓기 시작했고, 그가 죽은 뒤 유언에 따라 절이 됐어요. 누각 금각의 2층과 3층은 옻칠 위에 순금박을 입혔고, 1987년에 옻칠과 금박을 새로 했어요. 금각을 둘러싼 정원은 특별사적·특별명승이고, 절은 세계유산 '고대 교토의 역사기념물'의 하나예요.

가기 전에 알아 둘 것
  • 쉬는 날 없어요
  • 입장료 있어요
  • 사진·영상은 개인이 즐기는 범위의 스냅 촬영만 할 수 있어요. SNS 등에 공개하려는 촬영, 단체 사진, 드론은 금지예요.
  • 금각 옆까지는 큰 턱 없이 갈 수 있지만, 그 뒤로 계단이 있어 휠체어·유모차는 계단 앞에서 되돌아 나와야 해요. 휠체어는 예약 없이 남는 것이 있으면 빌릴 수 있어요.
  • 짐을 맡길 곳이나 사물함이 없어요.
  • 경내 정원에는 도시락을 먹을 만한 곳이 없어요.
  ↗ 공식 사이트 (https://www.shokoku-ji.jp/kinkakuji/)

가는 법
  가까운 곳: 교토 시버스 긴카쿠지미치(金閣寺道) 정류장
  [ 기사님께 보여 주기 ]  →  金閣寺 / 京都府京都市北区金閣寺町１
```
- 별칭(검색): 긴카쿠지, 킨카쿠지, 로쿠온지, 녹원사 / 영어 Rokuon-ji, Temple of the Golden Pavilion / 설명에 나와요: 쇼코쿠지
- 출처: kinkakuji_official_about https://www.shokoku-ji.jp/kinkakuji/about/ · kinkakuji_official_access https://www.shokoku-ji.jp/kinkakuji/access/ · kinkakuji_official_faq https://www.shokoku-ji.jp/kinkakuji/faq/ · kinkakuji_official_guide https://www.shokoku-ji.jp/kinkakuji/guide/ · unesco_688 https://whc.unesco.org/en/list/688/maps/ · 좌표·이름 Wikidata Q270983
- 사실 칸(인용 필수 필드) 13 · 인용 23 · 출처 5 · 크기 4,386B
- 지정: 세계유산 구성 자산(688-013) · 정원 특별사적·특별명승(부분 지정) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 2,450 / 영어 132,469
- 빈 칸·약한 근거: 예약 안내 없음. 가까운 곳이 버스 정류장(긴카쿠지미치)이라 Wikidata 항목이 없어 nearest_local 없음(기사님 카드에 역 이름 없음). 1950년 방화 소실은 공식 페이지에 없어 쓰지 않음. 한국어 이름은 통용 표기 '금각사'(위키 문서명은 '로쿠온지').

## 6. 쓰텐카쿠 (`tsutenkaku`, rank.order 106)

```
쓰텐카쿠
Tsūtenkaku
[일본 · 오사카] [도시·전망] [밤에 열어요]

어떤 곳이에요
  처음 쓰텐카쿠는 1912년 내국권업박람회 터에 개선문 위에 에펠탑 윗부분을 얹은 듯한 모습으로 세워졌어요. 이 탑은 1943년 아래 영화관 화재로 해체됐고, 지금 탑은 신세카이를 되살리자는 지역 주민들의 출자로 1956년에 다시 지었어요. 탑 높이는 100m이고, 도쿄 타워도 설계한 나이토 다추가 설계했어요. 지금 탑은 2007년 일본 등록유형문화재가 됐어요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 예약 권장 — 표를 살 때 입장 시간을 정해야 해요. 온라인 사전 구매를 권하고, 저녁에는 표가 다 팔릴 수 있다고 해요.
  • 탑에는 버스·승용차 주차장이 없어요. 근처 유료 주차장을 써야 해요.
  • 삼각대 촬영처럼 한자리를 오래 차지하는 행동은 금지예요.
  • 산 입장권은 환불·재발행이 안 되고, 나간 뒤에는 다시 들어갈 수 없어요.
  • 특별 옥외 전망대는 날씨가 나쁘면 예고 없이 닫을 수 있어요.
  ↗ 공식 사이트 (https://www.tsutenkaku.co.jp/)

가는 법
  가까운 곳: 오사카 메트로 사카이스지선 에비스초역에서 걸어가요 (미도스지선 도부쓰엔마에역·JR 신이마미야역에서도 걸어가요) (현지어: 恵美須町駅)
  [ 기사님께 보여 주기 ]  →  通天閣 / 大阪市浪速区恵美須東1-18-6
```
- 별칭(검색): 통천각, 츠텐카쿠, 쓰텐카쿠 타워 / 영어 Tsutenkaku Tower / 설명에 나와요: 신세카이, 빌리켄
- 출처: bunka_tsutenkaku https://kunishitei.bunka.go.jp/heritage/detail/101/00005702 · tsutenkaku_official_access https://www.tsutenkaku.co.jp/access/index.html · tsutenkaku_official_annai https://www.tsutenkaku.co.jp/annai/index.html · tsutenkaku_official_shiryo https://www.tsutenkaku.co.jp/other/shiryo.html · 좌표·이름 Wikidata Q1148463
- 사실 칸(인용 필수 필드) 13 · 인용 25 · 출처 4 · 크기 4,482B
- 지정: 등록유형문화재(2007, 문화청 DB) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 951 / 영어 29,712
- 빈 칸·약한 근거: 쉬는 날 공식 문구 없음(임시 휴업은 공식 X로 공지). 초대 탑 높이는 공식(75m)과 JNTO(64m)가 달라 쓰지 않음. '밤에 열어요' 태그는 공식 영업시간 표 근거.

## 7. 교토 국립박물관 (`kyoto-national-museum`, rank.order 107)

```
교토 국립박물관
Kyoto National Museum
[일본 · 교토] [박물관·미술관] [휠체어로 다닐 수 있어요]

어떤 곳이에요
  교토 국립박물관은 1897년 교토 히가시야마 기슭에 문을 연 박물관이에요. 고고 유물·도자기·불상·회화·서예·직물·칠기 같은 문화재를 모으고 전시해요. 붉은 벽돌 건물인 메이지 고토칸은 1895년에 완공됐고, 정문과 함께 일본 중요문화재예요. 소장품 전시와 특별전은 2014년에 문을 연 헤이세이 지신칸에서 열려요.

가기 전에 알아 둘 것
  • 쉬는 날: 월요일 — 월요일이 공휴일이면 그다음 날 쉬어요. 연말연시에도 쉬고, 전시 교체 등으로 임시 휴관하는 날이 있어요.
  • 입장료 있어요
  • 메이지 고토칸은 2015년부터 내진 공사 준비로 닫혀 있어 안에 들어갈 수 없어요.
  • 특별전 기간에는 소장품 전시를 따로 볼 수 없고, 정원만 여는 기간에는 전시실이 닫혀요. 가기 전에 일정을 확인하세요.
  • 전시실 안에서는 사진을 찍을 수 없어요(특별전의 지정 촬영 장소는 예외). 큰 짐은 코인 로커에 맡겨야 해요.
  • 휠체어와 유모차를 무료로 빌려줘요(수량 한정). 남문 입구에 휠체어용 경사로가 있어요.
  ↗ 공식 사이트 (https://www.kyohaku.go.jp/eng/)

가는 법
  가까운 곳: 게이한 시치조역에서 시치조 거리를 따라 동쪽으로 걸어가요 (교토역 앞에서 시버스도 다녀요) (현지어: 七条駅)
  [ 기사님께 보여 주기 ]  →  京都国立博物館 / 京都市東山区茶屋町527
```
- 별칭(검색): (없음) / 영어 KNM / 설명에 나와요: 메이지 고토칸, 헤이세이 지신칸
- 출처: kyohaku_official_access https://www.kyohaku.go.jp/eng/visit/access/ · kyohaku_official_access_ja https://www.kyohaku.go.jp/jp/visit/access/ · kyohaku_official_accessibility https://www.kyohaku.go.jp/eng/visit/accessibility/ · kyohaku_official_facilities https://www.kyohaku.go.jp/eng/about/facilities/ · kyohaku_official_faq https://www.kyohaku.go.jp/eng/visit/faq/ · kyohaku_official_info https://www.kyohaku.go.jp/eng/visit/info/ · kyohaku_official_requests https://www.kyohaku.go.jp/eng/visit/requests/ · 좌표·이름 Wikidata Q147286
- 사실 칸(인용 필수 필드) 13 · 인용 23 · 출처 7 · 크기 4,556B
- 지정: 메이지 고토칸·정문 중요문화재(부분 지정) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 433 / 영어 8,015
- 빈 칸·약한 근거: 예약 안내 없음(단체만 언급). 특별전·정원만 개방 기간에 따라 볼 수 있는 것이 달라짐(팁으로 안내). 메이지 고토칸은 2015년부터 닫혀 있음(팁).

## 8. 스미요시 타이샤 (`sumiyoshi-taisha`, rank.order 108)

```
스미요시 타이샤
Sumiyoshi Taisha
[일본 · 오사카] [역사·유적] [입장 무료]

어떤 곳이에요
  스미요시 타이샤는 바다의 신 셋(스미요시 삼신)과 진구 황후를 모신 신사예요. 신사에 전하는 역사로는 211년 진구 황후가 세웠다고 하고, 지금도 뱃일을 하는 사람들이 항해의 안전을 빌러 와요. 본전 4채는 1810년에 지은 일본 국보로, 대륙 건축의 영향을 받기 전 양식인 '스미요시즈쿠리'를 보여 줘요. 서쪽 입구의 가파른 아치형 다리 소리하시는 1600년 무렵 요도도노의 기부로 지금 모습이 됐어요.

가기 전에 알아 둘 것
  • 입장료 없어요
  • 경내에서 먹고 마시거나 담배를 피우지 말아 달라고 해요. 드론도 금지예요.
  • 기념품 판매소와 난쿤샤에서는 현금(엔)만 받고 신용카드는 안 돼요.
  • 1월 첫 참배(하쓰모데) 때는 200만 명이 찾아와 아주 붐벼요.
  • 소리하시 다리는 경사가 40도 넘는 곳이 있어요.
  ↗ 공식 사이트 (https://www.sumiyoshitaisha.net/)

가는 법
  가까운 곳: 난카이 본선 스미요시타이샤역에서 동쪽으로 걸어가요 (한카이 전차 스미요시토리이마에역에서도 바로 가요) (현지어: 住吉大社駅)
  [ 기사님께 보여 주기 ]  →  住吉大社 / 大阪府大阪市住吉区住吉2丁目9-89
```
- 별칭(검색): 스미요시 대사, 스미요시 신사, 스미욧상 / 영어 Sumiyoshi Grand Shrine / 설명에 나와요: 소리하시, 다이코바시
- 출처: sumiyoshi_official_access https://sumiyoshitaisha.net/access/ · sumiyoshi_official_en https://www.sumiyoshitaisha.net/en/ · sumiyoshi_official_faq_en https://www.sumiyoshitaisha.net/en/faq.html · sumiyoshi_official_ja https://www.sumiyoshitaisha.net/ · 좌표·이름 Wikidata Q705949
- 사실 칸(인용 필수 필드) 13 · 인용 19 · 출처 4 · 크기 4,316B
- 지정: 본전 4채 국보(부분 지정) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 361 / 영어 12,970
- 빈 칸·약한 근거: 공식 FAQ에 'Entrance is free.'가 있어 entry free + free_entry 태그(오후 결정 기준 충족). 'No fixed holidays'는 기념품 판매소 문장이라 쉬는 날은 'unknown'으로 둠.

## 9. 니시키 시장 (`nishiki-market`, rank.order 109)

```
니시키 시장
Nishiki Market
[일본 · 교토] [시장·쇼핑거리]

어떤 곳이에요
  니시키 시장은 교토 시내 중심의 니시키코지 거리를 따라 동서로 약 390m 이어진 시장 거리예요. 1615년 에도 막부가 교토에 공인한 생선 도매상 세 곳 가운데 하나가 되면서 본격적인 생선 시장으로 자리 잡았어요. '교토의 부엌'이라고 불리고, 교토 채소·민물고기·유바·나마후 같은 식재료를 파는 가게가 모여 있어요. 에도 시대 화가 이토 자쿠추가 이 시장의 채소 도매상 집에서 태어났어요.

가기 전에 알아 둘 것
  • 누구나 다닐 수 있는 곳이에요
  • 시장 안에서 먹으면서 걷지 말고, 산 가게 앞이나 가게 안에서 먹어 달라고 해요.
  • 시장 전체가 쉬는 날은 없고 가게마다 달라요. 수요일에 쉬는 가게가 많고, 1월 1~3일에는 닫는 가게가 많아요.
  • 길이 공도라 휠체어로 다닐 수 있지만, 턱 없는 길로 정비된 곳은 아니고 늘 붐빈다고 해요.
  • 지붕(아케이드)이 있어 비 오는 날에도 둘러볼 수 있어요.
  ↗ 공식 사이트 (https://www.kyoto-nishiki.or.jp/)

가는 법
  가까운 곳: 지하철 가라스마선 시조역·한큐 가라스마역에서 걸어서 가요 (교토역에서 시버스도 다녀요) (현지어: 四条駅)
  [ 기사님께 보여 주기 ]  →  錦市場 / 京都府京都市中京区西大文字町609番地
```
- 별칭(검색): 니시키 시장 상점가, 교토의 부엌 / 영어 Nishiki Ichiba / 설명에 나와요: 니시키코지, 이토 자쿠추
- 출처: nishiki_official_about https://www.kyoto-nishiki.or.jp/about/ · nishiki_official_access https://www.kyoto-nishiki.or.jp/access/ · nishiki_official_faq https://www.kyoto-nishiki.or.jp/contact/ · nishiki_official_manner https://www.kyoto-nishiki.or.jp/manner/ · 좌표·이름 Wikidata Q11650434
- 사실 칸(인용 필수 필드) 11 · 인용 15 · 출처 4 · 크기 3,928B
- 지정: 없음 · 위키 조회수(2025-10~2026-09, 사용자) 한국어 602 / 영어 12,154
- 빈 칸·약한 근거: 지정 없음(D11 순서상 지정 곳 다음). 주소는 상점가 진흥조합 주소(공식 접근 페이지 머리). 가게별 휴무는 팁으로만. 영문 위키 조회수는 'Nishiki Market' 문서 기준.

## 10. 가이유칸 (`kaiyukan`, rank.order 110)

```
가이유칸
Osaka Aquarium Kaiyukan
[일본 · 오사카] [테마파크·체험] [휠체어로 다닐 수 있어요]
(주의) 일부만 열어요 — '알류샨 열도' 수조는 리뉴얼 공사로 닫혀 있어요. (확인일 2026-10-09)

어떤 곳이에요
  가이유칸은 태평양을 중심으로 그 둘레 바다의 생물과 자연환경을 보여 주는 수족관이에요. 관람은 '일본의 숲'에서 시작해 바닷속, 바다 밑으로 태평양 둘레를 도는 순서로 이어져요. 가운데 '태평양' 수조에는 고래상어와 쥐가오리 등 여러 생물이 함께 살아요. 환태평양 화산대가 만든 다양한 환경과 그곳의 생명을 전시 주제로 삼고 있어요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 예약 권장 — 날짜·시간을 정하는 e티켓을 미리 사 두기를 권해요. 당일권은 원하는 시간에 못 들어가고 기다릴 수 있어요.
  • 캐리어 같은 큰 짐과 카트는 들고 들어갈 수 없어요. 숙소나 코인 로커에 먼저 맡겨야 해요.
  • 관내에서는 카페(cafe R.O.F) 말고는 음식을 먹을 수 없고, 음료는 뚜껑이 있어 쏟아지지 않는 것만 마실 수 있어요.
  • 수조 앞을 오래 차지하는 촬영과 삼각대·셀카봉 사용은 삼가 달라고 해요. 생물이 눈부신 플래시 촬영도 안 돼요.
  • 관내 대부분이 경사로라 휠체어로 돌아볼 수 있고, 휠체어도 빌려줘요(수량 한정).
  ↗ 공식 사이트 (https://www.kaiyukan.com/)

가는 법
  가까운 곳: 오사카 메트로 주오선 오사카코역(가이유칸마에)에서 걸어가요 (버스·셔틀선도 다녀요) (현지어: 大阪港駅)
  [ 기사님께 보여 주기 ]  →  海遊館 / 大阪府大阪市港区海岸通1－1－10
```
- 별칭(검색): 오사카 해유관, 해유관, 카이유칸, 오사카 수족관 / 영어 Kaiyukan / 설명에 나와요: 덴포잔, 고래상어
- 출처: kaiyukan_official_access https://www.kaiyukan.com/info/access/ · kaiyukan_official_barrierfree https://www.kaiyukan.com/info/area/barrierfree/ · kaiyukan_official_exhibition https://www.kaiyukan.com/about/exhibition/ · kaiyukan_official_rules https://www.kaiyukan.com/info/area/prohibition/ · kaiyukan_official_ticket https://www.kaiyukan.com/info/ticket/kaiyukan/ · 좌표·이름 Wikidata Q1191885
- 사실 칸(인용 필수 필드) 13 · 인용 21 · 출처 5 · 크기 4,420B
- 지정: 없음 · 위키 조회수(2025-10~2026-09, 사용자) 한국어 507 / 영어 36,557
- 빈 칸·약한 근거: **상태 partial**: 공식 전시 페이지에 '알류샨 열도' 수조가 리뉴얼 공사로 폐쇄 중 → risk renovation, 30일 주기 재확인 대상. 쉬는 날은 공식 달력이 동적이라 'unknown'. D19 (가): 먹이 주기·쇼를 빼도 관람 이유가 남는 수족관.

---

## 남은 일·사장님 확인

1. **USJ**: 한국어 조회수 1위(5,107)지만 공식 사이트가 봇 차단 JS 앱이라 이번엔 뺐다. 사람이 브라우저로 공식 페이지를 저장해 주면(수동 저장 스냅샷) 다음 차수에 넣을 수 있다.
2. **종류 균형 때문에 뺀 지정 곳**(뵤도인·긴카쿠지·야사카 신사·기온): 일본 전체 heritage 비율을 보고 다음 차수 후보로.
3. **가이유칸 상태 partial**(알류샨 열도 수조 공사) — 30일마다 다시 확인해야 한다(확인 주기 '자주 바뀜').
4. 합칠 때 크레디트 문자열 3줄(위) 추가, rank.order 재배열.
