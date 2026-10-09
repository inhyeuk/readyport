# 베트남 중부(다낭·호이안·나트랑) 관광지 12곳 보고 — 2026-10-09

- 데이터: `packs/drafts/VN/parts/central.json` — 지역 3개(vn_danang base, vn_hoian daytrip, vn_nhatrang base, 모두 wave 1), 관광지 12곳, 출처 30개.
- 근거: 저장소 밖 `~/.readyport/evidence/VN/<id>/` — 페이지 보이는 글 스냅샷(`N.txt`, 출처 id·URL·수집일 머리말) + `extract.json`(JP와 같은 형식, 인용 172개 + 지역 근거 `_regions/extract.json` 3개). 원문은 커밋하지 않았다.
- 모든 사실 칸(claims·tips·tags·facts·access·address_local)은 허용 출처(다낭시·카인호아성 관광 포털, 호이안 세계유산보존센터, UNESCO WHC, 운영사 공식)의 글자 그대로 인용으로 뒷받침했고 확인일은 모두 2026-10-09. 좌표·현지어 이름은 Wikidata(CC0). robots.txt를 확인했고(전부 허용), Google 지도·리뷰 사이트·언론 기사는 쓰지 않았다. 봇 차단·접속 불가 사이트(chammuseum.vn 시간 초과, vietnam.travel 리다이렉트 반복, vietnamtourism.vn DB 오류)는 우회하지 않았다.
- D4-A 숫자 없음(시간·요금은 인용 원문에만). D12: 위키 문장을 옮기지 않고 공식 사실만 보고 해요체로 새로 썼다. '입장 무료'는 공식 무료 문구가 있는 곳이 없어 하나도 쓰지 않았다(뽀나가르·롱선사는 entry 'unknown').
- **행정구역 개편(2025-07, 꽝남→다낭시 편입·군 폐지, 닌투언→카인호아 편입) 주의**: 공식 페이지 주소 대부분이 옛 군·프엉 이름이라 address_local은 새 행정명이 확인된 2곳(오행산 '프엉 응우하인선', 미선 '투본 사')에만 넣었다.

## 검증 (임시 병합 후, 병합본·문자열은 되돌림)

임시로 `packs/drafts/VN/attractions.json`(JP 작업본 머리 + country VN, unmapped_airports HAN·SGN = upcoming)을 만들고 `strings_attractions.xml`에 아래 2줄을 넣은 뒤 실행:
- `check VN`: **통과** (경고: facts.booking 없음 9곳 — 공식 예약 안내가 없어 비움, nature·sea_island 방문 지점 권장 3곳, 게이트 '보이는 종류(2곳 이상) 3개 < 4개' — 중부만으로는 heritage·theme_park·sea_island만 2곳 이상이라서이고 북부·남부 parts와 합치면 풀린다)
- `verify-quotes VN`: **통과** (12곳 모두, 필드별 출처 일치)
- copycheck: `~/.readyport/copycheck_cache/VN/<id>/`(한·영 위키 MediaWiki API, 문서 없으면 none.txt — 빈원더스) 만든 뒤 도구의 `copycheck()`로 12곳 **통과**(8어절 일치 0, 8글자 겹침 30% 이하).
- 서명 시점 규칙(published·strict)으로도 따로 돌려 봄: 실패는 위 종류 게이트 1개뿐, 상세 최소 충실도 미달 0곳, 곳당 크기 최대 4,030B(내원교) ≤ 4,608B.
- 지역 lint: base 지역 관광지–hub 거리 모두 40km 안(가장 먼 곳: 바나힐 케이블카–다낭역 약 19km, 빈원더스 케이블카 역–냐짱역 약 6km). daytrip 호이안: 미선 유적–호이안 구시가 약 25km.

### 합칠 때 꼭 함께 넣을 것 (이 커밋에는 parts 파일만 넣음)
UNESCO 출처 2개가 `attribution_required: true`라 문자열이 없으면 `check`가 실패한다. `app/src/main/res/values/strings_attractions.xml`의 `settings_credit_unesco_870` 다음에 추가:
```xml
<string name="settings_credit_unesco_948">UNESCO 세계유산센터 — 호이안 고대 도시</string>
<string name="settings_credit_unesco_949">UNESCO 세계유산센터 — 미선 유적</string>
```
- 그 밖의 출처(다낭·카인호아 관광 포털, 호이안 보존센터, 운영사)는 사실만 쓰고 표기 의무 라이선스가 없어 `attribution_required`를 두지 않았다.
- `advisory`: VN 팩 safety(mofa_vn, 2026-10-02 확인) '모든 지역 1단계' → 지역·관광지 모두 level '1'. 출처 `mofa_vn`을 sources에 넣었다(다른 VN parts와 합칠 때 중복 제거).
- `rank.order`는 이 묶음 안에서 201~212(D11 순서). 다른 권역과 합칠 때 나라 전체 순서로 다시 매기면 된다.
- 공항: DAD → vn_danang, CXR → vn_nhatrang(D25-A). HAN·SGN은 다른 권역 몫이라 임시 문서에서만 upcoming으로 두었다.

## 지역

| id | 이름 | kind | hub (선택 근거) | 공항 | 비고 |
|---|---|---|---|---|---|
| vn_danang | 다낭 | base, order 50 | 다낭역 Q3096350 (시내 철도 거점) | DAD | |
| vn_hoian | 호이안 | daytrip [vn_danang], order 60, note '다낭에서 하루 다녀오기도 해요' | 호이안 구시가 Q5965459 — 버스터미널 Wikidata 항목이 없어 규칙대로 rank 1위 관광지 방문 지점 | — | 당일 왕복 근거: 다낭시 교통국 LK02 다낭→호이안 버스(새벽~저녁 운행, 다낭 관광 포털 2024-05 공지) + 다낭 관광 포털 '오행산과 호이안 구시가를 하루에 묶어 둘러본다'(베트남어, 2025-08) |
| vn_nhatrang | 나트랑 (별칭 냐짱) | base, order 80 | 냐짱역 Q3097205 | CXR | D25-A로 1차 |

## 선정 (D11: 공식 지정 → 한국어 조회수 → 영어, 부분 지정 포함)

조회수: Wikimedia REST API, 사용자 조회수 2025-10 ~ 2026-09 합계.

| 지역 | 곳 | 지정 | 한국어 | 영어 | 종류 | 결과 |
|---|---|---|---|---|---|---|
| 다낭 | 오행산 | 국가특별유적(2018) | 403 | 19,055 | nature | ✅ |
| 다낭 | 바나힐 | — | 6,958 | 51,018 | theme_park(판정표) | ✅ |
| 다낭 | 미케 해변 | — | 2,218 | — | sea_island(public_space) | ✅ |
| 다낭 | 다낭 대성당 | (지정 확인 안 함) | 1,488 | — | heritage | ✗ 종류 균형(heritage가 이미 5곳) — 다음 차례 |
| 다낭 | 용다리 | — | 333 | 24,711 | city_view(public_space) | ✅ 종류 균형 |
| 다낭 | 참 조각 박물관 | (국보 소장, 시설 지정 아님) | 213 | 5,885 | museum | ✅ 박물관 종류 |
| 다낭 | 한 시장 | — | (문서 없음) | — | market_street | ✗ **Wikidata 좌표 없음**(Q97163247) — OSM 3순위를 쓰면 settings_credit_osm이 필요해 보류 |
| 다낭 | 선짜 반도 영응사 | — | (문서 없음) | — | heritage | ✗ 공식 출처·조회수 부족 |
| 호이안 | 호이안 구시가 | 세계유산·국가 유적(1990) | (도시 문서 5,276) | 12,921 | heritage | ✅ |
| 호이안 | 미선 유적 | 세계유산 | 1,643 | 36(새 제목)/49,191(옛 제목) | heritage | ✅ |
| 호이안 | 내원교(일본 다리) | 국가 유적(1990)·세계유산 구성 요소 | 267 | 19,373 | heritage | ✅ |
| 호이안 | 안방 해변 | — | (문서 없음) | — | sea_island | ✗ **Wikidata 좌표 없음**(Q141622910) |
| 나트랑 | 뽀나가르 참탑 | 국가특별유적 | 1,559 | 20,930 | heritage | ✅ |
| 나트랑 | 나트랑 해변 | 나트랑만 국가 명승(2005, 부분) | — | 604 | sea_island(public_space) | ✅ |
| 나트랑 | 롱선사 | — | 1,807 | 3,358 | heritage | ✅ |
| 나트랑 | 빈원더스 나트랑 | — | — | — | theme_park(판정표) | ✅ |
| 나트랑 | 담 시장 | — | 138 | — | market_street | ✗ **Wikidata 좌표 없음**(Q10748821). 성 관광 포털에 공식 글은 있음 |

- 중부 12곳 종류: heritage 5(42%), theme_park 2, sea_island 2, nature 1, city_view 1, museum 1. market_street 0 — 시장 후보 2곳(한 시장·담 시장) 모두 Wikidata 좌표가 없다. **사장님 판단 필요**: OSM 노드 좌표(3순위)를 허용하면 `settings_credit_osm` 문자열과 함께 한 시장·담 시장을 바로 넣을 수 있다.
- 지역별: 다낭 5(목표 4~5) · 호이안 3(목표 3) · 나트랑 4(목표 4~5).
- 판정표 대로: 바나힐·골든브리지/빈원더스 = theme_park, 뽀나가르·롱선사·호이안 구시가 = heritage. 오행산은 지정 이름이 '명승(danh thắng)'이고 산·동굴이 방문 이유라 nature(원칙 5).

---

## 1. 오행산 (`marble-mountains`, rank.order 201)

```
오행산
Marble Mountains
[베트남 · 다낭] [산·자연] [계단·오르막 많음]

대리석 산 다섯 봉우리에 동굴과 절이 모인 명승지예요.

어떤 곳이에요
  오행산은 다낭 시내에서 남동쪽으로 약 8km 떨어진 산 무리로, 이름은 금·수·목·화·토 오행에서 왔어요. 여행자가 주로 오르는 투이선에는 탐타이사·린응사 같은 옛 절과 종유석 동굴이 모여 있어요. 지질학자들은 이 산들이 원래 바다의 작은 섬이었다고 봐요. 2018년 국가특별유적이 됐고, 산자락에는 대리석 조각 마을 논느억이 이어져요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 투이선은 돌계단으로 걸어 오르거나, 표를 따로 사서 엘리베이터를 탈 수 있어요.
  • 암푸 동굴은 투이선과 표를 따로 팔아요.
  • 여름에는 햇볕이 강한 한낮을 피해 이른 아침이나 늦은 오후에 오르라고 권해요.
  ↗ 공식 안내 (https://danangfantasticity.com/en/marble-mountains)

가는 법
  차로만 — 다낭 시내에서 남동쪽, 다낭–호이안 길가에 있어요. 투이선은 1번·2번 입구 어느 쪽에서나 표를 사요
  [ 기사님께 보여 주기 ]  →  Ngũ Hành Sơn / 81 Huyền Trân Công Chúa, Phường Ngũ Hành Sơn, T.P Đà Nẵng
```
- 별칭(검색): 마블 마운틴, 응우하인선, 응우한선 / 영어 Ngu Hanh Son / 설명에 나와요: 투이선, 후옌콩 동굴, 암푸 동굴, 논느억 석조 마을
- 출처: dnf_marble_en https://danangfantasticity.com/en/marble-mountains · dnf_marble_vi https://danangfantasticity.com/en/di-san-canh-quan/danh-thang-ngu-hanh-son · 좌표·이름 Wikidata Q6755207
- 사실 칸(인용 필수 필드) 13 · 인용 17 · 출처 2 · 크기 3,997B
- 지정: 국가특별유적(2018) · 유네스코 아시아·태평양 기록유산 — 오행산 마애 글씨(2022) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 403 / 영어 19,055
- 빈 칸·약한 근거: 쉬는 날 공식 문구 없음('unknown', 화면에 안 그림), 예약 안내 없음. Wikidata 좌표가 소수 둘째 자리까지라(16.0, 108.26) 방문 지점이 아니라 산 무리 좌표다(geo.kind site → nature 경고) — 투이선 입구 항목이 Wikidata에 없다. 가는 법 '차로만'은 공식 문구('다낭에서 15분 이동', '차로 30분 호이안')에서 판단한 것이고 대중교통 부정 문구는 없다(약한 근거). 포털의 영문 페이지 주소는 옛 '군' 표기라 2025-08 갱신된 베트남어 페이지 주소(새 '프엉')를 썼다. 다낭시의 2025 결의(특정 기념일 무료)는 상시 무료가 아니어서 입장료 '있음' 유지.

## 2. 바나힐 (`ba-na-hills`, rank.order 202)

```
바나힐
Ba Na Hills
[베트남 · 다낭] [테마파크·체험] [케이블카 있음] [산 위·전망]

케이블카로 오르는 산 위 테마파크로, 골든브리지가 있어요.

어떤 곳이에요
  바나힐은 다낭 시내에서 20km 넘게 떨어진 쭈어산 위, 해발 1,487m에 있는 테마파크예요. 2009년 첫 케이블카 노선이 열리면서 산 위가 본격적으로 개발됐어요. 산 위에는 거대한 돌손이 받치는 모양의 골든브리지와 프랑스 마을, 실내 놀이 구역이 있어요. 골든브리지는 2019년에 처음 공개됐어요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 바깥 음식과 음료는 가지고 들어갈 수 없어요.
  • 입구에 짐 보관소가 있어요.
  • 공식 사이트에서 산 표는 환불하거나 날짜를 바꿀 수 없어요.
  ↗ 공식 안내 (https://sunworld.vn/en/banahills)

가는 법
  차로만 — 다낭 시내에서 택시·그랩 같은 차로 가요(낮 시간 셔틀버스는 없어요). 산 위로는 케이블카로 올라가요
  [ 기사님께 보여 주기 ]  →  Bà Nà
```
- 별칭(검색): 바나힐스, 썬월드 바나힐 / 영어 Sun World Ba Na Hills / 설명에 나와요: 골든브리지, 프랑스 마을
- 출처: banahills_official_about https://sunworld.vn/en/banahills/about-us · banahills_official_faq https://sunworld.vn/en/banahills/transportations · 좌표·이름 Wikidata Q1018810
- 사실 칸(인용 필수 필드) 12 · 인용 16 · 출처 2 · 크기 3,508B
- 지정: 없음 · 위키 조회수(2025-10~2026-09, 사용자) 한국어 6,958 / 영어 51,018
- 빈 칸·약한 근거: 쉬는 날·운영 시간 공식 문구는 '바뀔 수 있음'뿐이라 비움. 주소: 운영사 표기가 'An Son, Hoa Ninh ward, Hoa Vang province'로 옛·새 행정구역이 섞여 있어 address_local을 넣지 않았다. 방문 지점은 Wikidata 'Cáp treo Bà Nà'(Q1018810) 좌표(케이블카 쪽), 바나힐 항목 좌표는 wd_geo에 따로 둠. 공식 FAQ 답은 접힌 아코디언이라 페이지에 들어 있는 FAQ 질문·답 글만 따로 떼어 저장(2.txt). 수상 실적(WTA)·기네스 개수는 판촉 문구라 글에 쓰지 않음.

## 3. 미케 해변 (`my-khe-beach`, rank.order 203)

```
미케 해변
My Khe Beach
[베트남 · 다낭] [바다·섬]

다낭 동쪽 해안 도로를 따라 이어진 모래 해변이에요.

어떤 곳이에요
  미케 해변은 다낭 동쪽 해안의 호앙사–보응우옌잡 관광 도로와 나란히 이어진 해변이에요. 모래가 곱고 물결이 잔잔한 편이라 해수욕을 하러 많이 찾아요. 선짜 반도·오행산과 같은 해안 길 위에 있어요.

가기 전에 알아 둘 것
  • 누구나 다닐 수 있는 곳이에요
  • 해변에는 구조대가 늘 근무해요.
  • 해변 가 노점에서 음료와 간단한 먹거리를 팔아요.
  ↗ 공식 안내 (https://danangfantasticity.com/en/discovery/my-khe-beach-one-of-the-ten-most-beautiful-beaches-in-asia)

가는 법
  버스 — 해변과 나란한 호앙사–보응우옌잡 해안 도로를 따라 시내버스가 다녀요
  [ 기사님께 보여 주기 ]  →  Mỹ Khê
```
- 별칭(검색): 미케 비치 / 영어 —
- 출처: dnf_bus_2024 https://danangfantasticity.com/en/news/two-bus-routes-will-operate-between-da-nang-and-quang-nam-starting-april-30th · dnf_mykhe https://danangfantasticity.com/en/discovery/my-khe-beach-one-of-the-ten-most-beautiful-beaches-in-asia · 좌표·이름 Wikidata Q10796763
- 사실 칸(인용 필수 필드) 6 · 인용 7 · 출처 2 · 크기 2,352B
- 지정: 없음 · 위키 조회수(2025-10~2026-09, 사용자) 한국어 2,218 / 영어 (영문 문서 없음)
- 빈 칸·약한 근거: 공식 포털 글이 짧아 사실이 적다(트립어드바이저 순위·별점은 리뷰 사이트 평가라 쓰지 않음). 공식 글의 '900m'는 해변 전체 길이로 보기 어려워 숫자를 쓰지 않았다. 영어 위키 문서 없음. 시내버스 근거는 2024-05 다낭시 교통국 발표(해안 도로 경유 노선)라 노선 번호는 쓰지 않음. Wikidata 좌표 site(sea_island 경고).

## 4. 용다리 (`dragon-bridge`, rank.order 204)

```
용다리
Dragon Bridge
[베트남 · 다낭] [도시·전망]

한강을 건너는 용 모양 다리로, 밤에 불과 물을 뿜어요.

어떤 곳이에요
  용다리는 다낭 한강을 가로지르는 길이 666m의 다리로, 용이 불과 물을 뿜도록 설계됐어요. 2013년 3월 29일, 다낭 해방 38주년에 개통했어요. 다낭 공항 쪽 시내에서 미케 해변·논느억 해변으로 가는 길을 짧게 이어 줘요.

가기 전에 알아 둘 것
  • 누구나 다닐 수 있는 곳이에요
  • 금·토·일요일과 공휴일 밤에 용 머리에서 불을 뿜고, 이어서 물을 뿜는 공연이 있어요.
  • 공연 중에는 다리로 차가 다닐 수 없어요. 오토바이로 가면 일찍 도착하라고 해요.
  • 강가 카페나 음료 가게에 앉아 공연을 볼 수 있어요.
  ↗ 공식 안내 (https://danangfantasticity.com/en/dragon-bridge)

가는 법
  시내에서 걸어서 · 버스 — 시내 중심에서는 쩐푸 거리 끝까지 가면 다리가 보여요. 시내버스는 참 조각 박물관 정류장에서 내려 조금 걸어요
  [ 기사님께 보여 주기 ]  →  Cầu Rồng
```
- 별칭(검색): 롱교, 드래곤 브리지 / 영어 Cau Rong
- 출처: dnf_dragon https://danangfantasticity.com/en/dragon-bridge · dnf_dragonshow https://danangfantasticity.com/en/the-dragon-show · 좌표·이름 Wikidata Q5305270
- 사실 칸(인용 필수 필드) 8 · 인용 10 · 출처 2 · 크기 2,808B
- 지정: 없음 · 위키 조회수(2025-10~2026-09, 사용자) 한국어 333 / 영어 24,711
- 빈 칸·약한 근거: 공연 시각(밤 9시)은 D4에 따라 숫자 없이 요일만. 포털 글의 시내버스 운행 시간 문구는 오래된 정보라 쓰지 않음. 주소는 옛 프엉 이름(Phuoc Ninh)이라 뺌.

## 5. 참 조각 박물관 (`cham-museum`, rank.order 205)

```
참 조각 박물관
Museum of Cham Sculpture
[베트남 · 다낭] [박물관·미술관] [복장 규정 있음]

참파 왕국의 돌 조각을 모은 다낭 시내 박물관이에요.

어떤 곳이에요
  참 조각 박물관은 5~15세기 참파 왕국의 조각 300점 넘게를 사암·테라코타·금속 등 여러 재료로 보여 줘요. 첫 건물은 1915년에 짓기 시작해 1916년에 완공했고, 1919년에 문을 열었어요. 참 사원 건축의 특징을 살려 설계했고, 전시실은 짜끼에우·미선·동즈엉처럼 유물이 나온 지역별로 나뉘어요. 2011년 베트남 1급 박물관이 됐어요.

가기 전에 알아 둘 것
  • 정기 휴관: 월요일
  • 입장료 있어요
  • 삼각대와 플래시를 쓴 촬영은 안 돼요.
  • 무거운 손짐은 짐 보관 창구에 맡겨야 해요.
  • 전시실 안에서는 먹거나 마실 수 없고, 유물을 만지면 안 돼요.
  • 박물관 와이파이에 접속하면 베트남어·영어·프랑스어 오디오 가이드를 들을 수 있어요.
  ↗ 공식 안내 (https://danangfantasticity.com/en/overview-da-nang-museum-of-cham-sculpture)

가는 법
  시내에서 걸어서 · 버스 — 다낭 시내 중심, 용다리 서쪽 끝 가까이에 있어요
  [ 기사님께 보여 주기 ]  →  Bảo tàng Chăm
```
- 별칭(검색): 다낭 참 박물관, 참파 조각 박물관 / 영어 Da Nang Museum of Cham Sculpture
- 출처: dnf_cham https://danangfantasticity.com/en/overview-da-nang-museum-of-cham-sculpture · dnf_dragon https://danangfantasticity.com/en/dragon-bridge · 좌표·이름 Wikidata Q6940790
- 사실 칸(인용 필수 필드) 12 · 인용 16 · 출처 2 · 크기 3,489B
- 지정: 없음 · 위키 조회수(2025-10~2026-09, 사용자) 한국어 213 / 영어 5,885
- 빈 칸·약한 근거: 공식 사이트 chammuseum.vn이 이 PC에서 접속 시간 초과(우회하지 않음) → 다낭 관광 포털의 박물관 소개(2020-09 게시)로만 채웠다. 포털 주소는 폐지된 '하이쩌우군' 표기라 address_local 비움. official_url도 포털 페이지. 국보 소장(2022-12 기준 6점)은 지정이 아니라 소장품이라 rank.designations에 넣지 않음.

## 6. 호이안 구시가 (`hoi-an-ancient-town`, rank.order 206)

```
호이안 구시가
Hoi An Ancient Town
[베트남 · 호이안] [역사·유적] [세계유산]

15~19세기 무역항의 목조 거리가 남은 옛 도시예요.

어떤 곳이에요
  호이안 구시가는 투본강 하구 북쪽 강변의 옛 무역항으로, 15~19세기에 동남아·동아시아와 널리 교역했어요. 목조 건물 1,107채가 좁은 보행로를 따라 줄지어 있고, 건물 앞은 거리로, 뒤는 강으로 열려 있어요. 1990년 베트남 국가 유적이 됐고, 1999년 세계유산에 올랐어요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 구시가에 들어가기 전에 입장권을 사 달라고 해요.
  • 표는 한 번 사면 호이안에 머무는 동안(기간 제한 있음) 쓸 수 있어요. 둘러본 뒤에도 표를 챙겨 두세요.
  • 낮부터 밤까지 정해진 시간에는 구시가가 '걷기·자전거 거리'로 운영돼요.
  ↗ 공식 안내 (https://hoianheritage.danang.gov.vn/en/)

가는 법
  버스 — 다낭에서 LK02 버스를 타고 호이안 버스터미널에서 내려요
  [ 기사님께 보여 주기 ]  →  Phố cổ Hội An
```
- 별칭(검색): 호이안 올드타운, 호이안 옛 시가지 / 영어 Hoi An Old Town / 설명에 나와요: 떤끼 고가, 푸젠 회관, 꽌꽁 사당
- 출처: dnf_bus_2024 https://danangfantasticity.com/en/news/two-bus-routes-will-operate-between-da-nang-and-quang-nam-starting-april-30th · hoian_center_jb_renovations https://hoianheritage.danang.gov.vn/en/specialised-exchanges/research-exchange/japanese-covered-bridge-in-hoi-an-renovations-1.html · hoian_center_visit https://hoianheritage.danang.gov.vn/en/news/news-events/announcement-of-the-visiting-in-hoi-an-ancient-town-125.html · unesco_948 https://whc.unesco.org/en/list/948/ · 좌표·이름 Wikidata Q5965459
- 사실 칸(인용 필수 필드) 11 · 인용 16 · 출처 4 · 크기 3,555B
- 지정: 세계유산 호이안 고대 도시(1999) · 베트남 국가 유적(1990) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 (문서 없음 — 도시 문서 호이안 5,276) / 영어 12,921
- 빈 칸·약한 근거: 쉬는 날 문구 없음. 한국어 위키 문서 없음(도시 문서 '호이안'만 있음, 조회수 5,276). 버스 근거(LK02)는 2024-05 발표라 지금 노선 번호가 바뀌었을 수 있다 — 사람 확인 권장. 국가특별유적(2009) 지정은 공식 출처를 못 찾아 넣지 않았다(1990 국가 유적만). 16세 미만 면제·8명 이상 무료 가이드는 숫자 규칙(claims ①) 때문에 tips에서 뺌.

## 7. 미선 유적 (`my-son-sanctuary`, rank.order 207)

```
미선 유적
My Son Sanctuary
[베트남 · 호이안] [역사·유적] [세계유산] [복장 규정 있음]

참파 왕국의 힌두 사원 탑이 모인 골짜기 유적이에요.

어떤 곳이에요
  미선 유적은 산으로 둘러싸인 분지에 4~13세기 참파 왕국이 지은 힌두 사원 탑들이 모인 곳이에요. 오랫동안 참파 왕국의 종교·정치 중심지였고, 사원은 주로 시바 신에게 바쳐졌어요. 탑은 구운 벽돌로 쌓고 힌두 신화를 새긴 사암 부조로 꾸몄어요. 1999년 세계유산에 올랐어요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 비가 많은 우기에는 길이 미끄럽고 골짜기가 물에 잠기기도 해요.
  • 한낮 더위를 피해 이른 아침에 둘러보라고 권해요.
  • 벽돌 벽·비석·구조물은 만지거나 오르면 안 돼요.
  ↗ 공식 안내 (https://whc.unesco.org/en/list/949/)

가는 법
  차로만 — 산으로 둘러싸인 외딴 골짜기라 차로 가요
  [ 기사님께 보여 주기 ]  →  Mỹ Sơn / My Son Village, Thu Bon Commune, Da Nang City
```
- 별칭(검색): 미썬 유적, 미선 성지 / 영어 My Son
- 출처: dnf_myson https://danangfantasticity.com/en/my-son-sanctuary · unesco_949 https://whc.unesco.org/en/list/949/ · 좌표·이름 Wikidata Q391406
- 사실 칸(인용 필수 필드) 13 · 인용 15 · 출처 2 · 크기 3,321B
- 지정: 세계유산 미선 유적(1999) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 1,643 / 영어 36 (2026 문서 이동, 옛 제목 Mỹ Sơn 49,191)
- 빈 칸·약한 근거: **거리 규칙**: hub(호이안 구시가)에서 직선 약 25km, 다낭역에서 약 35km라 40km 안이다. vn_hoian은 daytrip 지역이라 70km 규칙만 적용(경고 없음). 호이안의 관광 덩어리가 아니라 산골짜기라는 점을 지역 소속 근거로 적어 둔다(투본강 상류·호이안 항구와의 관계: UNESCO 글). 운영 시간·쉬는 날 비움. 가는 법 '차로만'은 '외딴 골짜기' 문구에서 판단(약한 근거). 영어 위키 sitelink가 'Mỹ Sơn archaeological site'로 바뀐 지 얼마 안 돼 조회수가 작게 나온다(옛 제목 'Mỹ Sơn' 49,191). 국가특별유적 지정은 공식 출처 확인 못 해 뺌.

## 8. 내원교 (`japanese-bridge`, rank.order 208)

```
내원교
Japanese Covered Bridge
[베트남 · 호이안] [역사·유적] [세계유산] [복장 규정 있음]

호이안 구시가 물길 위, 작은 사당이 있는 지붕 다리예요.

어떤 곳이에요
  내원교는 16세기 말~17세기 초 일본 상인들이 일본인 구역과 중국인 구역을 잇기 위해 놓은 다리로 전해져요. 위는 집, 아래는 다리인 구조이고, 가운데 사당에는 도교의 신 북제진무를 모셔요. 2022년 말부터 복원해 2024년 8월에 준공했고, 베트남 2만 동 지폐에도 그려져 있어요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 다리 위와 사당은 오전, 그리고 오후부터 저녁까지 정해진 시간에만 들어갈 수 있어요(호이안 구시가 입장권 포함).
  • 다리 위에 한 번에 올라갈 수 있는 인원이 정해져 있어, 붐비면 줄을 서서 기다려요.
  • 신성한 곳이라 단정한 옷차림과 조용한 관람을 부탁해요.
  • 다리 바깥쪽은 하루 종일 자유롭게 보고 사진을 찍을 수 있어요.
  ↗ 공식 안내 (https://danangfantasticity.com/en/architecture/japanese-covered-bridge-chua-cau)

가는 법
  시내에서 걸어서 — 호이안 구시가 안, 투본강으로 흘러드는 작은 물길 위에 있어요
  [ 기사님께 보여 주기 ]  →  Chùa Cầu
```
- 별칭(검색): 일본 다리, 일본교, 라이비엔교, 쭈어꺼우 / 영어 Japanese Bridge, Chua Cau, Lai Vien Kieu
- 출처: dnf_chuacau https://danangfantasticity.com/en/architecture/japanese-covered-bridge-chua-cau · hoian_center_jb_renovations https://hoianheritage.danang.gov.vn/en/specialised-exchanges/research-exchange/japanese-covered-bridge-in-hoi-an-renovations-1.html · hoian_center_jb_restoration https://hoianheritage.danang.gov.vn/en/specialised-exchanges/research-exchange/information-on-the-restoration-results-of-the-japanese-covered-bridge-hoi-an-12.html · unesco_948 https://whc.unesco.org/en/list/948/ · 좌표·이름 Wikidata Q1091054
- 사실 칸(인용 필수 필드) 13 · 인용 18 · 출처 4 · 크기 4,030B
- 지정: 베트남 국가 유적(1990) · 세계유산 호이안 고대 도시의 구성 요소 · 위키 조회수(2025-10~2026-09, 사용자) 한국어 267 / 영어 19,373
- 빈 칸·약한 근거: 호이안 구시가 안의 같은 표 구역이지만 다른 Wikidata 항목·방문 지점(구시가 좌표에서 약 280m)이라 별도 항목으로 뒀다(별칭 규칙 '같은 입구'에 맞지 않음). 운영 시간 숫자는 visit_note에서 뺌(D4). 한 번에 오를 수 있는 인원(20명)은 숫자 규칙 때문에 tips에서 수치 없이 씀. UNESCO 글은 다리 연대를 '18세기'로, 시 관광 포털은 '16세기 말~17세기 초'로 적어 엇갈린다 → 본문은 포털 쪽을 '전해져요'로 썼다(사람 확인 권장).

## 9. 뽀나가르 참탑 (`po-nagar`, rank.order 209)

```
뽀나가르 참탑
Po Nagar Cham Towers
[베트남 · 나트랑] [역사·유적] [복장 규정 있음] [계단·오르막 많음]

까이강 하구 언덕 위에 선 참파 시대 사원 탑들이에요.

어떤 곳이에요
  뽀나가르 참탑은 나트랑 시내 북쪽 까이강 하구의 꾸라오 언덕에 참족이 8~13세기에 세운 사원 탑 무리예요. 참족의 땅의 어머니 여신 뽀나가르를 모시고, 베트남 사람들은 티엔이아나 성모로 섬겨 왔어요. 높이 약 23m의 주탑 안에는 화강암 한 덩이를 깎은 여신상이 있어요. 해마다 음력 3월 20~23일에 탑바 축제가 열려요.

가기 전에 알아 둘 것
  • 어깨와 무릎을 가리는 옷을 입어야 해요. 옷이 맞지 않으면 입구에서 가운을 무료로 빌려줘요.
  • 탑 안에 들어갈 때는 모자와 선글라스를 벗고 조용히 해 달라고 해요.
  • 제단·신상·벽돌 벽의 부조는 만지면 안 돼요.
  • 의식 중인 사람을 찍을 때는 먼저 허락을 받으세요.
  ↗ 공식 안내 (https://vanhoacham.khanhhoa.gov.vn/vi/ponagar)

가는 법
  시내에서 걸어서 — 나트랑 시내 중심에서 북쪽으로 약 2km, 까이강 하구 꾸라오 언덕 위에 있어요
  [ 기사님께 보여 주기 ]  →  Tháp Po Nagar
```
- 별칭(검색): 포나가르 탑, 뽀나가 사원, 탑바 / 영어 Po Nagar, Thap Ba
- 출처: khdl_ponagar https://dulichso.khanhhoa.gov.vn/en/article/po-nagar-cham-towers-77f · khdl_ponagar_etiquette https://dulichso.khanhhoa.gov.vn/en/article/visitor-etiquette-for-ponagar-tower-and-po-klong-garai-tower-b0c · khdl_ponagar_info https://dulichso.khanhhoa.gov.vn/en/article/po-nagar-cham-towers-a-millennium-old-heritage-7f7 · khdl_ponagar_values https://dulichso.khanhhoa.gov.vn/en/article/po-nagar-cham-towers-a-place-preserving-cham-viet-cultural-values-899 · 좌표·이름 Wikidata Q600269
- 사실 칸(인용 필수 필드) 14 · 인용 17 · 출처 4 · 크기 3,811B
- 지정: 국가특별유적 · 위키 조회수(2025-10~2026-09, 사용자) 한국어 1,559 / 영어 20,930
- 빈 칸·약한 근거: 입장료·쉬는 날 공식 문구를 찾지 못함(entry 'unknown', 화면에 안 그림). 공식 포털 주소는 옛 프엉(Vĩnh Phước)이라 address_local 비움. 국가특별유적 근거는 성 관광 포털 기사 문구('the special national monument Po Nagar Cham Towers')이고 지정 결정문(152/QĐ-TTg, 2025)은 직접 확인 못 함 — 사람 확인 권장. 가는 법 '시내에서 걸어서'는 '시내 중심에서 북쪽 약 2km' 문구로 판단.

## 10. 나트랑 해변 (`nha-trang-beach`, rank.order 210)

```
나트랑 해변
Tran Phu Beach
[베트남 · 나트랑] [바다·섬]

나트랑 시내 쩐푸 거리를 따라 이어진 해변이에요.

어떤 곳이에요
  나트랑 해변은 나트랑만 해안의 가운데 활 모양 구간으로, 시내 중심 쩐푸 거리를 따라 이어져요. 만에는 크고 작은 섬 19개가 있고, 한가운데에 혼째섬이 있어요. 해안 공원 길에는 이른 아침과 밤에 걷거나 운동하는 사람이 많아요. 나트랑만은 2005년 국가 명승으로 지정됐어요.

가기 전에 알아 둘 것
  • 누구나 다닐 수 있는 곳이에요
  • 쩐푸 거리의 구조대가 지켜보는 안전 깃발 구역 안에서 수영하라고 안내해요.
  • 이안류에 휩쓸리면 해변 쪽으로 거슬러 헤엄치지 말고, 해안과 나란히 헤엄쳐 빠져나오라고 해요.
  • 쩐푸 거리 해변은 소매치기가 많은 곳이라, 휴대폰·지갑을 모래 위에 두고 자리를 비우지 마세요.
  ↗ 공식 안내 (https://dulichso.khanhhoa.gov.vn/en/article/nha-trang-bay-13c)

가는 법
  시내에서 걸어서 — 나트랑 시내 중심, 쩐푸 거리를 따라 이어져요
```
- 별칭(검색): 냐짱 해변, 쩐푸 해변 / 영어 Nha Trang Beach
- 출처: khdl_belongings https://dulichso.khanhhoa.gov.vn/en/article/protecting-belongings-and-money-while-swimming-at-the-beach-02a · khdl_nhatrangbay https://dulichso.khanhhoa.gov.vn/en/article/nha-trang-bay-13c · khdl_ripcurrent https://dulichso.khanhhoa.gov.vn/en/article/identifying-rip-currents-when-swimming-at-the-beach-0d0 · 좌표·이름 Wikidata Q138189312
- 사실 칸(인용 필수 필드) 8 · 인용 12 · 출처 3 · 크기 3,134B
- 지정: 나트랑만 국가 명승(2005, 해변이 속한 만) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 (문서 없음) / 영어 604
- 빈 칸·약한 근거: 해변 Wikidata 항목(Q138189312)에 베트남어 라벨이 없어 names.local·local_short = null(기사님 버튼 숨김). 지정은 해변이 속한 '나트랑만' 국가 명승(2005, 부분 지정으로 처리 — 오후 결정). 한국어 위키 문서 없음. Wikidata 좌표 site(sea_island 경고).

## 11. 롱선사 (`long-son-pagoda`, rank.order 211)

```
롱선사
Long Son Pagoda
[베트남 · 나트랑] [역사·유적] [계단·오르막 많음]

산 위 흰 불상으로 알려진 나트랑 시내의 절이에요.

어떤 곳이에요
  롱선사는 나트랑 시내 짜이투이산 기슭의 100년 넘은 절로, '흰 부처 절'이라고도 불려요. 산 위 흰 불상은 땅에서 24m 높이로, 23/10 거리에서도 잘 보여요. 카인호아성 불교 활동의 중심이라 큰 불교 행사가 이곳에서 열려요.

가기 전에 알아 둘 것
  • 흰 불상이 있는 곳까지는 계단을 걸어 올라가요.
  • 불상이 있는 단에서 나트랑 시내가 내려다보여요.
  • 주민들이 기도하러 오는 조용한 절이에요.
  ↗ 공식 안내 (https://dulichso.khanhhoa.gov.vn/en/article/long-son-pagoda-b4e)

가는 법
  시내에서 걸어서 — 나트랑 시내 23/10 거리, 짜이투이산 기슭에 있어요
  [ 기사님께 보여 주기 ]  →  Chùa Long Sơn
```
- 별칭(검색): 롱선 사원, 흰 부처 절 / 영어 White Buddha Pagoda
- 출처: khdl_longson https://dulichso.khanhhoa.gov.vn/en/article/long-son-pagoda-b4e · 좌표·이름 Wikidata Q2277986
- 사실 칸(인용 필수 필드) 11 · 인용 14 · 출처 1 · 크기 3,026B
- 지정: 없음 · 위키 조회수(2025-10~2026-09, 사용자) 한국어 1,807 / 영어 3,358
- 빈 칸·약한 근거: 출처가 성 관광 포털 1개뿐(다른 공식 출처: 성 포털 en.khanhhoa.gov.vn 페이지 404, vietnamtourism.vn DB 오류). 입장료·쉬는 날 문구 없음(entry 'unknown'). 창건 연도(1886 등)는 공식 출처에 없어 쓰지 않았다('100년 넘은'만). 주소는 옛 프엉(Phương Sơn)이라 뺌. 지정 없음(D11에서 조회수 순).

## 12. 빈원더스 나트랑 (`vinwonders-nha-trang`, rank.order 212)

```
빈원더스 나트랑
VinWonders Nha Trang
[베트남 · 나트랑] [테마파크·체험] [케이블카 있음]

혼째섬에 있는 놀이공원으로, 바다를 건너는 케이블카로 가요.

어떤 곳이에요
  빈원더스 나트랑은 나트랑 앞바다 혼째섬에 있는 놀이공원이에요. 놀이기구 구역과 다섯 대륙의 식물을 모은 월드 가든, 바닷물을 쓰는 워터파크, 해양 생물 전시관이 있어요. 본토 쩐푸 거리의 케이블카 역에서 바다를 건너 섬으로 들어가요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 놀이기구마다 키·몸무게·나이 조건과 운영 시간이 달라요.
  • 케이블카는 정기 점검이나 강풍 때 멈추고, 그때는 쾌속선으로 건너가요.
  ↗ 공식 안내 (https://vinwonders.com/en/vinwonders-nha-trang/)

가는 법
  배 — 본토 쩐푸 거리의 빈펄 케이블카 역에서 바다를 건너는 케이블카로 혼째섬에 가요(케이블카가 멈추면 쾌속선)
  [ 기사님께 보여 주기 ]  →  VinWonders
```
- 별칭(검색): 빈원더스 냐짱, 빈펄랜드 나트랑 / 영어 Vinpearl Land Nha Trang / 설명에 나와요: 빈펄 케이블카, 혼째섬
- 출처: vinwonders_nt_cablecar https://vinwonders.com/en/wonderpedia/news/vinpearl-cable-car-nha-trang/ · vinwonders_nt_cablecar_notice https://vinwonders.com/en/wonderpedia/news/notice-temporary-suspension-vinpearl-nha-trang-cable-car/ · vinwonders_nt_official https://vinwonders.com/en/vinwonders-nha-trang/ · vinwonders_nt_requirements https://vinwonders.com/en/wonderpedia/news/information-attraction-requirements-at-vinwonders-nha-trang/ · 좌표·이름 Wikidata Q2527182
- 사실 칸(인용 필수 필드) 8 · 인용 14 · 출처 4 · 크기 3,032B
- 지정: 없음 · 위키 조회수(2025-10~2026-09, 사용자) 한국어 (문서 없음) / 영어 (문서 없음)
- 빈 칸·약한 근거: Wikidata에 빈원더스 나트랑 항목이 없어 qid null, 이름 출처는 운영사 공식(names.source = vinwonders_nt_official). 방문 지점은 Wikidata '빈펄 케이블카'(Q2527182) 좌표 = 본토 케이블카 역(geo.kind station). 공식 안내: 2026-10-26 ~ 2026-11-10 케이블카 정기 점검 운휴(그동안 쾌속선) — 날짜가 지나면 무의미해서 tips에는 '점검·강풍 때 쾌속선'으로 일반화했다. 케이블카 길이는 공식 글끼리 2,643m / 3,320m로 엇갈려 쓰지 않음. 새 쇼·인어 쇼·먹이 주기가 있지만 놀이기구·정원·워터파크만으로 방문 이유가 남아 D19 (가) 포함. 주소는 옛 프엉 표기라 뺌. 위키 문서 없음.

