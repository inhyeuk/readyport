# 베트남 북부 관광지 6곳 보고 — 2026-10-09 (후속 보완판)

- 데이터: `packs/drafts/VN/parts/north.json` (지역 3개 `vn_hanoi`·`vn_halong`·`vn_ninhbinh`, 모두 wave 1 / 관광지 6곳 / 출처 22개)
- 후속 보완(같은 날): 상세 최소 충실도(§5.2-13)를 맞추려고 가는 법·당일 왕복 근거를 다시 찾았다. 탕롱 황성·호아로 수용소·짱안은 가는 법을 채웠고, **성 요셉 대성당·호찌민 박물관은 끝내 공식 근거를 못 찾아 싣지 않고 '다음 차수'로 옮겼다**(아래). 이제 실린 6곳은 모두 충실도 기준을 넘는다(미달 0곳).
- 근거: 저장소 밖 `~/.readyport/evidence/VN/<id>/` — 페이지 전체 글 스냅샷(`N.txt`, URL·수집일 머리말) + `extract.json`(일본과 같은 형식, 실린 6곳 인용 91개). 지역 당일 왕복 근거는 `~/.readyport/evidence/VN/_regions/<지역 id>/`. 원문은 커밋하지 않았다.
- 모든 사실 칸(claims·tips·tags·facts·access·address_local)은 허용 출처(시설·관리기관 공식 사이트, 성(省) 관광 당국 사이트, 베트남 문화유산국 특별 국가유적 목록, 유네스코 세계유산센터) 인용으로 뒷받침했고 확인일은 모두 2026-10-09. 좌표·현지어 이름·위키 제목은 Wikidata. robots.txt를 확인했고 Google 지도·리뷰 사이트·여행사 글은 쓰지 않았다. 봇 차단(403)·접속 불가 사이트는 우회하지 않았다.
- D4-A: 여는 시간·요금 숫자 없음(인용 원문에만 있음). D12: 위키 문장을 옮기지 않고 공식 사실만 보고 새로 썼다(해요체). D18: 호아로 수용소는 운영 사실·연도만. D13: 크루즈·투어 상품은 쓰지 않았다.
- '입장 무료'(free_entry·entry free)는 한 곳도 없음 — 시설 공식 문구로 무료가 확인된 곳이 없었다.

## 검증 (임시 병합 후, 병합본·문자열은 되돌림)

임시 `packs/drafts/VN/attractions.json`(JP 작업본 머리 필드 + country VN, version 2026.10.09-1, `unmapped_airports` = DAD·CXR·SGN `upcoming`)을 만들고 크레디트 문자열 4줄을 임시로 넣어 돌렸다.

- `check VN`: **통과** — 남은 경고: facts.booking 없음(공식 예약 안내 없음 → 비움), 짱안·땀꼭 방문 지점 site 경고 2건, 게이트 경고(이 묶음만으로는 6곳·base 1곳·종류 2개). **상세 최소 충실도 미달 0곳**(`is_thin` 0/6).
- `verify-quotes VN`: **통과** (6곳 모두, 필드별 출처 일치)
- copycheck(`build_attractions.copycheck`, 캐시 `~/.readyport/copycheck_cache/VN/<id>/{ko,en}wiki.txt`, MediaWiki API): **6곳 모두 통과**
- 지역 lint: 모두 hub에서 40km 안(하노이 3곳은 하노이역에서 약 0.6~1.2km, 짱안·땀꼭은 닌빈역에서 약 6~7km, 하롱베이는 hub와 같은 점). 곳당 크기 3,256~4,380B(문묘 4,380B가 가장 큼, 한도 4,608B).

### 합칠 때 꼭 함께 넣을 것 (이 커밋에는 parts 파일만 넣음)
출처 4개가 `attribution_required: true`라 문자열이 없으면 `check`가 실패한다. `app/src/main/res/values/strings_attractions.xml`의 `settings_credit_unesco_870` 다음에 추가(검증 때 이 그대로 넣었다가 되돌림):
```xml
<string name="settings_credit_unesco_1328">UNESCO 세계유산센터 — 탕롱-하노이 황성 중심 구역</string>
<string name="settings_credit_unesco_672">UNESCO 세계유산센터 — 하롱베이-깟바 군도</string>
<string name="settings_credit_unesco_1438">UNESCO 세계유산센터 — 짱안 경관 단지</string>
<string name="settings_credit_dsvh_special">베트남 문화유산국(Cục Di sản văn hóa) 특별 국가유적 목록 — 탕롱 황성·문묘·하롱베이·짱안·땀꼭</string>
```
- `dsvh_special`: 문화유산국 사이트 하단에 '이 사이트 정보를 다시 쓸 때 출처 "Cục Di sản văn hóa"를 밝혀 달라'는 문구가 있어 출처 표기 대상으로 두었다.
- 다른 parts(중부·남부)와 합칠 때 `unmapped_airports`에서 DAD·CXR·SGN을 지역에 매핑하고, `rank.order`(이 묶음은 201~206)를 나라 전체 순서로 다시 매기면 된다.

## 지역 (wave 1)

| id | 이름 | kind | hub (Wikidata) | 공항 | note_ko |
|---|---|---|---|---|---|
| vn_hanoi | 하노이 | base | 하노이역 Q1207323 | HAN | — |
| vn_halong | 하롱베이 | daytrip [vn_hanoi] | 뚜언쩌우(국제여객항) Q7857335 | — | 하노이에서 하루 다녀오기도 해요 |
| vn_ninhbinh | 닌빈 | daytrip [vn_hanoi] | 닌빈역 Q16877130 | — | 하노이에서 하루 다녀오기도 해요 |

- hub 선택 근거: 하노이 = 호안끼엠 쪽 시내에서 가까운 철도 중심역(하노이역). 하롱 = 판정표의 방문 지점인 뚜언쩌우 국제여객항(항구 항목이 없어 뚜언쩌우 섬·동 항목). 닌빈 = 닌빈역.
- 경보: 팩 safety '모든 지역이 1단계' → 지역·관광지 모두 level 1, source `mofa_vn`, 확인일 2026-10-02(팩 safety 날짜).

### 당일 왕복 근거 (regions 시트 칸, 저장소 밖 `_regions/<id>/extract.json`, 확인일 2026-10-09)
| 지역 | 근거 URL | 인용 요지 |
|---|---|---|
| vn_halong | https://halongtourism.com.vn/di-chuyen/ (꽝닌성 관광국·관광정보진흥센터 공식 '하롱베이 가는 법') | 하노이 도심 출발 셔틀버스가 매일 아침 하노이를 떠나 하롱에서 돌아온다는 안내, 자람역–하롱 열차(Halong Express), 하노이에서 약 170km |
| vn_ninhbinh | https://dulichninhbinh.com.vn/item/1242 (닌빈성 관광국 관광정보진흥센터 공식 포털의 운수업체 안내) · https://dulichninhbinh.com.vn/item/1240 | 하노이–닌빈 리무진 노선 출발 시간대(하노이 출발·닌빈 출발 모두 아침부터 저녁까지), 닌빈역은 남북선 주요 역 |
- 지시서의 '운영사 시간표'는 아니고 **성(省) 관광 당국의 공식 안내**다(하노이–하롱 버스 운영사·베트남 철도 시간표는 JS 앱·403으로 못 받음, vietnam.travel은 robots.txt·하위 페이지 리디렉션 고리). 하롱 안내의 셔틀 시각은 원문 그대로 남겼고 앱에는 숫자를 싣지 않는다.

## 선정 (D11: 공식 지정 → 한국어 조회수 → 영어, 부분 지정 포함)

조회수: Wikimedia REST API, 사용자 조회수 2025-10 ~ 2026-09 합계.

| 지역 | 곳 | 지정 | 한국어 | 영어 | 종류 | 결과 |
|---|---|---|---|---|---|---|
| 하롱 | 하롱베이 | 세계유산·특별 국가유적 | 6,287 | 149,580 | sea_island | ✅ |
| 하노이 | 탕롱 황성 | 세계유산·특별 국가유적 | 2,512 | 37,226 | heritage | ✅ |
| 하노이 | 호안끼엠 호수·응옥선 사당 | 특별 국가유적(2013, 문화유산국 목록 확인) | 2,308 | 34,434 | (city_view) | ⏭ 다음 차수 — 지정 말고는 공식 근거를 못 받음(재시도: hanoi.gov.vn·hoankiem.hanoi.gov.vn·sodulich.hanoi.gov.vn 연결 시간 초과 45초, hanoitourism.vn 연결 실패) |
| 하노이 | 문묘(반미에우-꾸옥뜨잠) | 특별 국가유적·진사 비석 세계기록유산·국보 | 1,823 | 54,893 | heritage | ✅ |
| 하노이 | 호아로 수용소 | 하노이 역사 유적(부분) | 1,238 | 150,459 | heritage | ✅ |
| 닌빈 | 짱안 | 세계유산(복합)·특별 국가유적 | 522 | 20,182 | nature | ✅ |
| 닌빈 | 땀꼭-빅동 | 세계유산 구성 지구·특별 국가유적 | 349 | 9,622 | nature | ✅ |
| 닌빈 | 호아르 고도 | 세계유산 구성 지구·특별 국가유적(2012) | 177 | 26,417 | heritage | ✗ 닌빈 목표 2곳(짱안·땀꼭이 한국어 조회수 앞섬) — 다음 차례 |
| 닌빈 | 바이딘사 | 지정 근거 없음 | 523 | 3,145 | heritage | ✗ 지정 없음·목표 수 초과 |
| 하노이 | 성 요셉 대성당 | — | 2,701 | 45,726 | heritage | ⏭ **다음 차수 — 가는 법 공식 근거 없음**(아래, 데이터·근거는 준비돼 있음) |
| 하노이 | 쩐꾸옥 사원 | — | 850 | 18,937 | heritage | ✗ 이번엔 후보 조사 안 함 |
| 하노이 | 호찌민 주석릉(묘소) | (특별 국가유적은 '주석궁 안 호찌민 유적지'로 별개) | 439 | 105,863 | heritage | ⏭ 다음 차수 — 운영 기관 bqllang.gov.vn 재시도도 연결 시간 초과 |
| 하노이 | 하노이 구시가지 | 국가 지정 확인 못 함 | 420 | 16,888 | (market_street) | ✗ 공식 사이트 phocohanoi.gov.vn 도메인 조회 실패, Wikidata 좌표 없음 |
| 하노이 | 호찌민 박물관 | — | 357 | 8,520 | museum | ⏭ **다음 차수 — 가는 법 공식 근거 없음**(아래, 데이터·근거는 준비돼 있음) |
| 하노이 | 베트남 민족학 박물관 | — | 167 | 8,783 | museum | ✗ vme.org.vn 403(봇 차단) — 우회 금지 |
| 하노이 | 동쑤언 시장 | — | (문서 없음) | 9,908 | market_street | ✗ 공식 도메인 chodongxuan.vn 만료 |
| 하노이 | 탕롱 수상인형극장 | (D21 공공·문화 성격 가능) | (문서 없음) | (문서 없음) | theme_park | ✗ thanglongwaterpuppet.org 가 극장과 무관한 광고 사이트로 바뀌어 공식 출처로 못 씀 |

- 북부 6곳 종류: heritage 3(탕롱 황성·문묘·호아로) = 50%(상한 초과 아님), nature 2(짱안·땀꼭), sea_island 1. 박물관 종류는 호찌민 박물관이 빠지면서 0곳.
- 지역별: 하노이 3(목표 4~5, base 최소 3은 충족) · 하롱 1(목표 1~2) · 닌빈 2(목표 2).

---

## 1. 하롱베이 (`ha-long-bay`, rank.order 201)

```
하롱베이
Ha Long Bay
[베트남 · 하롱베이] [바다·섬] [세계유산]
(지역 머리: 하롱베이 — 하노이에서 하루 다녀오기도 해요)

어떤 곳이에요
  하롱베이는 베트남 북동부 꽝닌성의 만으로, 바다가 들어와 잠긴 탑 모양 석회암 카르스트 지형이에요. 1994년 세계유산에 올랐고, 2023년 경계가 바뀌어 지금은 하이퐁시의 깟바 군도와 함께 등재돼 있어요. 관리기관이 정한 관광 노선은 8개이고, VHL2 노선에는 띠똡섬과 승솟 동굴이 들어 있어요. 2009년에는 베트남 특별 국가유적으로도 지정됐어요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 관광선은 뚜언쩌우 국제여객항이나 하롱 국제여객항에서 출발해요.
  • 관람료는 노선마다 따로 정해져 있어요.
  • 배가 항구를 떠나고 돌아오는 시간은 여름과 겨울에 달라요.
  • 승솟 동굴로 올라가는 길은 경사가 있어요.
  ↗ 공식 사이트 (https://halongbay.com.vn/)

가는 법
  가까운 곳: 뚜언쩌우 국제여객항(또는 하롱 국제여객항)에서 관광선을 타요 (현지어: Tuần Châu)
  [ 기사님께 보여 주기 ]  →  Vịnh Hạ Long / (주소 칸 비움)
```
- 별칭(검색): 하롱만, 할롱만, 할롱베이 / 영어 Hạ Long Bay / 설명에 나와요: 띠똡섬, 승솟 동굴
- 출처: dsvh_special https://dsvh.gov.vn/danh-muc-di-tich-quoc-gia-dac-biet-1752 · halong_fee https://halongbay.com.vn/p/58-muc-phi-tham-quan-vinh-ha-long · halong_hours https://halongbay.com.vn/c/thoi-gian-on-khach-tham-quan · halong_route2 https://halongbay.com.vn/tours/2-hanh-trinh-vhl-2-cang-tau-bai-tam-soi-sim-dao-ti-top-hang-sung-sot-vung-tung-sau-dong-me-cung-hang-bo-nau-hang-luon-hang-trong-hang-trinh-nu-hang-ho-dong-tien · unesco_672 https://whc.unesco.org/en/list/672/ · 좌표·이름 Wikidata Q7857335
- 사실 칸(인용 필수 필드) 13 · 인용 16 · 출처 5 · 크기 3,963B
- 지정: 세계유산 하롱베이-깟바 군도 · 특별 국가유적 하롱베이(2009) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 6,287 / 영어 149,580
- 판정·빈 칸·약한 근거: 종류 sea_island(판정표 고정). 방문 지점은 판정표대로 뚜언쩌우 국제여객항이지만 **항구 자체의 Wikidata 항목이 없어** 뚜언쩌우(섬·동, Q7857335) 좌표를 kind pier로 썼다(만 자체 좌표는 wd_geo). 크루즈 숙박 상품은 쓰지 않았다(관리기관 요금표의 숙박 노선도 언급 안 함). 쉬는 날·예약 공식 문구 없음 → regular_closed unknown, booking 비움. 관람료는 노선별 숫자만 있어 '있어요'만 표시. 띠똡섬 '호찌민이 이름 붙인 유일한 섬', 승솟 동굴 '가장 넓은 동굴'은 최상급·정치 문구라 쓰지 않았다.

## 2. 탕롱 황성 (`thang-long-citadel`, rank.order 202)

```
탕롱 황성
Imperial Citadel of Thăng Long
[베트남 · 하노이] [역사·유적] [세계유산] [복장 규정 있음]

어떤 곳이에요
  탕롱 황성은 11세기 리 왕조가 세운 성으로, 다이비엣(대월)의 독립을 상징해요. 7세기 중국 요새 터 위에 지어졌고, 거의 13세기 동안 이 지역 정치 권력의 중심이었어요. 황성 중심 구역과 호앙지에우 18번지 고고학 유적이 2010년 세계유산에 올랐어요. 2026년 9월에는 옛 정전 자리인 낀티엔전(경천전) 복원 공사를 시작했어요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 복장 규정 있음
  • 단정하고 예의 바른 옷차림으로 와 달라고 안내해요.
  • 드론(플라이캠)은 날릴 수 없어요.
  • 나무에 오르거나 잔디를 밟지 말아 달라고 해요.
  • 16세 미만은 나이를 증명할 서류가 있으면 관람료가 무료예요.
  ↗ 공식 사이트 (https://hoangthanhthanglong.vn/)

가는 법
  가까운 곳: 하노이 도심 한가운데에 있어요 — 호앙지에우 거리 19C
  [ 기사님께 보여 주기 ]  →  Hoàng thành Thăng Long / Số 19C đường Hoàng Diệu, Phường Ba Đình, Thành phố Hà Nội
```
- 별칭(검색): 탕롱 왕궁, 하노이 황성 / 영어 Thang Long Imperial Citadel / 설명에 나와요: 낀티엔전, 호앙지에우 18번지 유적
- 출처: dsvh_special https://dsvh.gov.vn/danh-muc-di-tich-quoc-gia-dac-biet-1752 · hoangthanh_kinhthien https://hoangthanhthanglong.vn/khoi-cong-phuc-dung-dien-kinh-thien-trung-tam-hoang-thanh-thang-long/ · hoangthanh_rules https://hoangthanhthanglong.vn/mot-so-quy-dinh-danh-cho-khach-tham-quan-di-san-the-gioi-hoang-thanh-thang-long/ · hoangthanh_ticket https://hoangthanhthanglong.vn/gia-ve-tham-quan-hoang-thanh-thang-long/ · unesco_1328 https://whc.unesco.org/en/list/1328/ · 좌표·이름 Wikidata Q206219
- 사실 칸(인용 필수 필드) 15 · 인용 19 · 출처 5 · 크기 4,209B
- 지정: 세계유산 탕롱-하노이 황성 중심 구역 · 특별 국가유적 탕롱 황성 중심 구역(2009) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 2,512 / 영어 37,226
- 판정·빈 칸·약한 근거: 가는 법은 대중교통 정류장 근거가 없어(버스 노선 검색 timbus.vn 접속 불가, 메트로 3호선 지하 구간 미개통) 유네스코의 '수도 한가운데(located in the heart of the capital)' 문구와 관리센터 안내의 19C 호앙지에우 입구·주차 위치로 walk_from_center를 썼다(근거가 상대적으로 약함). 쉬는 날 공식 문구 없음(머리글에 시간만 있음) → unknown. 공식 사이트에 온라인 예매(Đặt vé trực tuyến)·야간 투어 예약 전화가 있으나 '필요/권장' 문구가 없어 booking 비움, 야간 투어는 업체형 상품이라 태그로 쓰지 않았다. 2026-09-29 낀티엔전 복원 착공 — 관람 제한 문구는 없어 status open 유지, 다음 확인 때 공사 구역 안내를 다시 볼 것. 주소는 보존센터·관람 입구(19C Hoàng Diệu, 2025 행정구역 개편 후 'Phường Ba Đình').

## 3. 하노이 문묘 (`van-mieu`, rank.order 203)

```
하노이 문묘
Temple of Literature
[베트남 · 하노이] [역사·유적] [복장 규정 있음] [밤에 열어요]

어떤 곳이에요
  문묘(반미에우)는 1070년 리 타인똥 왕의 명으로 세워졌고, 1076년에는 국자감(꾸옥뜨잠)이 세워졌어요. 1075년 첫 유교 과거 시험이 열렸고, 1484년 레 타인똥 왕이 진사 비석을 세우게 했어요. 진사 비석은 2011년 유네스코 세계기록유산이 됐고, 2015년 비석 82개가 국보로 지정됐어요. 문묘·국자감은 2012년 베트남 특별 국가유적으로 지정됐어요.

가기 전에 알아 둘 것
  • 쉬는 날 없어요
  • 입장료 있어요
  • 복장 규정 있음
  • 사당에 들어갈 때는 단정한 옷을 입고, 신성한 구역에서는 조용히 해 달라고 안내해요.
  • 경내는 금연이에요.
  • 비석·유물·건물을 만지거나 낙서하면 안 되고, 잔디에도 들어가지 말아 달라고 해요.
  • 수·토·일요일 밤에는 야간 관람 프로그램을 따로 운영해요.
  ↗ 공식 사이트 (https://vanmieu.gov.vn/en/introduction/visitor-information)

가는 법
  가까운 곳: 꾸옥뜨잠 거리 58번지 앞 정류장(38번 버스), 똔득탕 거리 40번지 맞은편 정류장(02·41·E08·E09번 버스)
  [ 기사님께 보여 주기 ]  →  Văn Miếu – Quốc Tử Giám / Số 58 phố Quốc Tử Giám, phường Văn Miếu - Quốc Tử Giám, Hà Nội
```
- 별칭(검색): 문묘, 반미에우, 국자감, 문묘 국자감 / 영어 Van Mieu, Văn Miếu / 설명에 나와요: 진사 비석
- 출처: dsvh_special https://dsvh.gov.vn/danh-muc-di-tich-quoc-gia-dac-biet-1752 · vanmieu_history_en https://vanmieu.gov.vn/en/introduction/site-history · vanmieu_visitor_en https://vanmieu.gov.vn/en/introduction/visitor-information · vanmieu_visitor_vi https://vanmieu.gov.vn/vi/introduction/visitor-information · 좌표·이름 Wikidata Q1202019
- 사실 칸(인용 필수 필드) 15 · 인용 19 · 출처 4 · 크기 4,380B
- 지정: 특별 국가유적 문묘-국자감(2012) · 세계기록유산 진사 비석(2011) · 국보 진사 비석 82개(2015) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 1,823 / 영어 54,893
- 판정·빈 칸·약한 근거: 가장 충실한 곳(공식 영·베트남어 방문 안내). 문묘는 세계유산이 아니라 **진사 비석만 세계기록유산**이라 '세계유산' 낱말·unesco 태그를 쓰지 않았다. 야간 관람(수·토·일)은 시설 자체 프로그램이라 night 태그+팁으로 넣고 시간 숫자는 뺐다(D4). 입장료 숫자(표준권)는 쓰지 않았다.

## 4. 호아로 수용소 (`hoa-lo-prison`, rank.order 204)

```
호아로 수용소
Hỏa Lò Prison
[베트남 · 하노이] [역사·유적]

어떤 곳이에요
  호아로 수용소는 1896년 프랑스 식민 당국이 하노이에 지은 감옥이에요. 식민지 시기에는 수많은 베트남 애국·혁명 운동가가 이곳에 갇혔어요. 1964년부터 1973년까지는 격추된 미군 조종사를 가뒀고, 조종사들은 이곳을 '하노이 힐튼'이라 불렀어요. 1993년 감옥 터의 쓰임새가 바뀌었고, 남동쪽에 남은 부분을 보수해 역사 유적으로 지정했어요.

가기 전에 알아 둘 것
  • 쉬는 날 없어요
  • 입장료 있어요
  • 명절(뗏)과 공휴일을 포함해 매일 문을 열어요.
  • 내국인·외국인 모두 관람료를 내요.
  ↗ 공식 사이트 (https://hoalo.vn/)

가는 법
  가까운 곳: 하노이 도심 한가운데에 있어요 — 호아로 거리 1번지
  [ 기사님께 보여 주기 ]  →  Nhà tù Hỏa Lò / Số 1 phố Hỏa Lò, phường Cửa Nam, thành phố Hà Nội
```
- 별칭(검색): 호아로 감옥, 하노이 힐튼 / 영어 Hoa Lo Prison, Hanoi Hilton / 설명에 나와요: -
- 출처: hoalo_fee https://hoalo.vn/Articles/12/26017/thong-bao-thay-%C4%91oi-phi-tham-quan-ban-ngay.html · hoalo_home https://hoalo.vn/ · hoalo_intro https://hoalo.vn/Articles/14/34/gioi-thieu-di-tich-lich-su-nha-tu-hoa-lo.html · 좌표·이름 Wikidata Q1359941
- 사실 칸(인용 필수 필드) 9 · 인용 12 · 출처 3 · 크기 3,256B
- 지정: 하노이 역사 유적(감옥 남동쪽 남은 부분) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 1,238 / 영어 150,459
- 판정·빈 칸·약한 근거: D18 성격(식민지 감옥·미군 포로) — 공식 유적 관리위원회 소개문의 사실만 썼다(연도·용도·별명). 공식 이름이 '역사 유적(Di tích lịch sử)'이라 종류 heritage(판정 2·3: 박물관 공식 분류 아님). 지정은 '하노이 역사 유적으로 순위 지정(남동쪽 남은 부분)'을 부분 지정으로 쳤다(국가급 여부는 확인 못 함). 가는 법은 관리위원회 첫 화면의 '하노이 수도 한가운데(Nằm giữa trung tâm của Thủ đô Hà Nội)' 문구로 walk_from_center(정류장 근거는 없음). '인도차이나 최대 감옥 중 하나' 문구는 최상급이라 뺐다.

## 5. 짱안 (`trang-an`, rank.order 205)

```
짱안
Tràng An
[베트남 · 닌빈] [산·자연] [세계유산]
(지역 머리: 닌빈 — 하노이에서 하루 다녀오기도 해요)

어떤 곳이에요
  짱안은 홍강 삼각주 남쪽 끝의 석회암 카르스트 지대로, 2014년 베트남의 첫 복합 세계유산(문화·자연)이 됐어요. 봉우리 사이 물길과 동굴 일부는 작은 배로 지날 수 있고, 현지 뱃사공이 노를 젓는 삼판배를 타고 둘러봐요. 동굴에서는 3만 년 넘게 이어진 사람 활동의 흔적이 나왔어요. 10~11세기에는 베트남의 옛 수도 호아르가 이곳에 세워졌어요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 관람료의 어른·어린이 구분은 나이가 아니라 키로 해요.
  ↗ 공식 사이트 (https://trangandanhthang.vn/)

가는 법
  가까운 곳: 짱안 선착장(생태관광지 안, 관광객 지원소가 있어요)에서 배를 타요
  [ 기사님께 보여 주기 ]  →  Quần thể danh thắng Tràng An / Khu du lịch sinh thái Tràng An, phường Tây Hoa Lư, tỉnh Ninh Bình
```
- 별칭(검색): 짱안 경관 단지 / 영어 Trang An Landscape Complex, Trang An / 설명에 나와요: 호아르
- 출처: dsvh_special https://dsvh.gov.vn/danh-muc-di-tich-quoc-gia-dac-biet-1752 · nbtourism_trangan_station https://dulichninhbinh.com.vn/item/1180 · trangan_kdl https://trangandanhthang.vn/khu-du-lich-trang-an/ · trangan_prices https://trangandanhthang.vn/gia-ve-tham-quan-cac-khu-diem-du-lich/ · unesco_1438 https://whc.unesco.org/en/list/1438/ · 좌표·이름 Wikidata Q10810559
- 사실 칸(인용 필수 필드) 12 · 인용 14 · 출처 5 · 크기 3,823B
- 지정: 세계유산 짱안 경관 단지(복합유산) · 특별 국가유적 짱안-땀꼭-빅동 경관 지구(2012) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 522 / 영어 20,182
- 판정·빈 칸·약한 근거: 복합유산(문화+자연)이지만 방문 방식(삼판배로 카르스트 물길·동굴 통과)으로 판정 5 → nature. '베트남 첫 복합 세계유산'은 관리위원회 공식 문구 그대로(trangan_kdl, '첫'). **방문 지점(짱안 선착장) Wikidata 항목이 없어** 경관 단지 좌표(site) — 경고 1건. 가는 법·주소는 닌빈성 관광국 산하 관광정보센터의 '짱안 선착장 관광객 지원소' 안내(주소 포함)로 채웠다(배 타는 곳까지). 쉬는 날 공식 문구 없음 → unknown.

## 6. 땀꼭 (`tam-coc-bich-dong`, rank.order 206)

```
땀꼭
Tam Cốc – Bích Động
[베트남 · 닌빈] [산·자연] [세계유산]
(지역 머리: 닌빈 — 하노이에서 하루 다녀오기도 해요)

어떤 곳이에요
  땀꼭-빅동은 닌빈의 석회암 동굴 지대로, 쩐 왕조의 부럼 행궁과 관련된 유적이 함께 있어요. '육지의 하롱베이'라는 별명으로도 불려요. 주요 뱃길은 반럼 선착장에서 응오동강을 따라 땀꼭으로 가는 길과, 빅동 아래 동굴을 지나는 길 등이에요. 짱안 세계유산의 일부이고, 2012년 베트남 특별 국가유적으로 지정됐어요.

가기 전에 알아 둘 것
  • 입장료 있어요
  • 입장권은 단지 안 전동차가 포함된 것과 포함되지 않은 것 두 가지예요.
  • 빅동 사원·띠엔 동굴·항무아 등은 걸어서나 자전거로 둘러볼 수 있어요.
  ↗ 공식 사이트 (https://trangandanhthang.vn/tam-coc-bich-dong/)

가는 법
  가까운 곳: 반럼 선착장에서 응오동강을 따라 가는 배를 타요
  [ 기사님께 보여 주기 ]  →  Tam Cốc - Bích Động / (주소 칸 비움)
```
- 별칭(검색): 땀꼭 빅동, 탐꼭 / 영어 Tam Coc, Tam Coc - Bich Dong / 설명에 나와요: 빅동 사원, 항무아
- 출처: dsvh_special https://dsvh.gov.vn/danh-muc-di-tich-quoc-gia-dac-biet-1752 · trangan_prices https://trangandanhthang.vn/gia-ve-tham-quan-cac-khu-diem-du-lich/ · trangan_tamcoc https://trangandanhthang.vn/tam-coc-bich-dong/ · unesco_1438 https://whc.unesco.org/en/list/1438/ · 좌표·이름 Wikidata Q1406000
- 사실 칸(인용 필수 필드) 10 · 인용 11 · 출처 4 · 크기 3,484B
- 지정: 세계유산 짱안 경관 단지(구성 지구) · 특별 국가유적 짱안-땀꼭-빅동 경관 지구(2012) · 위키 조회수(2025-10~2026-09, 사용자) 한국어 349 / 영어 9,622
- 판정·빈 칸·약한 근거: 종류 nature(뱃길·동굴). 좌표는 땀꼭-빅동 항목(Q1406000, 빅동 사원 쪽) — **반럼 선착장 Wikidata 항목 없음**, site 경고 1건. Wikidata 대표 사진(P18)이 짱안 동굴 사진이라 photo_link는 비웠다. 가는 법은 관리위원회의 뱃길 안내(반럼 선착장 출발)만 근거로 썼다. 쉬는 날 공식 문구 없음.

---

## 다음 차수 (싣지 않음 — 충실도 미달로 내보내지 않음)

| 곳 | 준비된 것 | 막힌 것 | 풀 방법 |
|---|---|---|---|
| 성 요셉 대성당 (`hanoi-st-joseph-cathedral`) | 하노이 대교구 공식 페이지 스냅샷·인용 10개(정식 개관 1887-12-23, 1924 주교좌, 크기, 색유리화, 일요일 미사, 주소 Số 40 Nhà Chung) — 생성 스크립트 안에 그대로 있음 | 가는 법: 교구 페이지에 교통 안내 없음, 하노이 버스 노선(timbus.vn) 접속 불가, 하노이 시·호안끼엠 구 포털 연결 시간 초과 | 하노이 공공 버스 운영 자료나 구·시 관광 페이지를 사람이 브라우저로 저장 → access 한 줄만 채우면 바로 실을 수 있음 |
| 호찌민 박물관 (`ho-chi-minh-museum`) | 박물관 공식 페이지 5개 스냅샷·인용 14개(개관 1990-05-19, 월·금 휴관, 외국인 유료, 복장·짐 규정, 주소 19 Ngọc Hà) | 가는 법: 박물관 사이트에 교통 안내 없음(같은 이유) | 위와 같음 |
| 호안끼엠 호수·응옥선 사당 | 특별 국가유적 지정(문화유산국 목록) | 시설·구 공식 페이지 전부 연결 시간 초과 | 구 포털 접속이 되는 환경에서 다시 수집 |
| 호찌민 묘소 | — | 운영 기관 bqllang.gov.vn 연결 시간 초과 | 같은 방법(관람 규칙은 반드시 운영 기관 원문) |

- 참고로 시도한 경로: 하노이 메트로(metrohanoi.vn) — 3호선은 지상 구간(Nhổn–Cầu Giấy)만 운행 중이고 Văn Miếu·Ga Hà Nội 지하역은 아직 운행 안내가 없어 도심 관광지 가는 법으로 쓸 수 없었다. 트란세르코(transerco.com.vn) — 노선·정류장 정보가 JS 앱(timbus)으로만 제공. 공항버스 86번 사이트도 JS.

## 빈 칸·약한 근거·남은 일

1. **탕롱 황성·호아로 수용소의 가는 법은 walk_from_center**: 정류장 근거가 아니라 공식 문구 '수도 한가운데'(유네스코·호아로 관리위원회)에 기댄 값이다. 하노이 버스·메트로 정류장 근거가 생기면 bus·metro로 바꾸는 것이 낫다.
2. **방문 지점 좌표**: 하롱(뚜언쩌우 섬 항목으로 대신), 짱안·땀꼭(선착장 Wikidata 항목 없음 → site 경고). OSM 노드를 쓰려면 `settings_credit_osm` 문자열이 새로 필요하다.
3. 탕롱 황성 낀티엔전 복원 공사(2026-09-29 착공) — 관람 제한 공지가 생기면 status partial로 바꿔야 한다.
4. 당일 왕복 근거는 성 관광 당국 안내(운영사 시간표 아님) — 운영사 시간표를 받을 수 있게 되면 교체.
5. 합칠 때 크레디트 문자열 4줄(위) 추가, `unmapped_airports` 정리, rank.order 재배열.
