# 대만(TW) 관광지 1차 작업본 보고 — 2026-10-09

- 데이터: `packs/drafts/TW/attractions.json` (release `draft`, version `2026.10.09-1`). 지역 8개(1차 4개·2차 4개), 관광지 **23곳**(모두 1차 지역), 출처 87개.
- 근거: 저장소 밖 `~/.readyport/evidence/TW/<id>/` — 공식 페이지 전체 글 스냅샷(`N.txt`, URL·수집일·robots 확인 결과 머리말) + `extract.json`(일본·베트남과 같은 형식, 인용이 필요한 필드 257개). 지역 근거는 `~/.readyport/evidence/TW/_regions/`(당일 왕복 근거 `tw_north_coast/`, hub Wikidata 기록 `_hubs/`). 원문은 커밋하지 않았다.
- 모든 사실 칸(claims·tips·tags·facts·access·address_local·status(open 아님)·seasonal)은 허용 출처 인용으로 뒷받침했고 확인일은 모두 2026-10-09. 좌표·현지어 이름·위키 제목은 Wikidata, 경보는 팩 safety(`mofa_tw`, 2026-10-02, 대만은 none만).
- 수집 원칙: robots.txt를 먼저 읽고 Claude 계열이나 `*`에 막힌 경로는 가져오지 않았다. **국립고궁박물원(npm.gov.tw)·예류 지질공원(ylgeopark.org.tw)은 robots.txt가 Claude 계열을 막아 한 번도 가져오지 않았고** `official_url` 링크만 걸었다. travel.taipei·khh.travel(403), ymsnp.gov.tw·nps.gov.tw(내용 없는 자바스크립트 페이지)는 우회하지 않았다. Google 지도·리뷰·블로그·여행사 글은 쓰지 않았다(place-ids 도구로 ID만 저장).
- 대만 정부 사이트 인증서에 Subject Key Identifier가 없어 Python 3.14 기본값(VERIFY_X509_STRICT)으로는 연결이 실패한다. 이 엄격 플래그 하나만 끄고 체인·호스트 검증은 그대로 두었다.
- 문화재 지정은 문화부 문화자산국 오픈데이터(`data.boch.gov.tw/opendata/v2/assetsCase/1.1·1.2.json`, 政府資料開放授權條款-第1版, 출처 표기 의무)의 레코드로 확인했다. nchdb 상세 페이지는 자바스크립트로 그려 글이 없어서, 해당 레코드를 `key: value` 줄로 스냅샷에 저장해 인용을 대조했다.
- D4-A: 여는 시간·요금 숫자 없음. D12: 위키 문장을 옮기지 않고 공식 사실로 새로 썼다(해요체). D18: 중정기념당은 운영·건축 사실만(평가 문장·공원 전환 계획 등은 뺐다). D13·D14·D19·D21: 해당 곳 없음. 지우펀 애니메이션 배경설은 쓰지 않았다.
- '입장 무료'(entry free + free_entry)는 시설·관리기관 공식 안내에 무료 문구가 있는 3곳만: 베이터우 지열곡(臺北自來水事業處 FAQ), 베이터우 온천박물관(관할 타이베이시 문화국 안내), 불광산 불타기념관(기념관 공식 사이트).

## 검증 (2026-10-09, 이 커밋 상태)

출처표기 문자열 12줄을 `strings_attractions.xml`에 임시로 넣고 돌린 뒤 되돌렸다(앱 파일은 커밋하지 않음).

- `build_attractions.py check TW`: **통과**. 남은 경고 — facts.booking 없음 15곳(공식 예약 안내가 없어 비워 둠, 화면에 안 보임), 방문 지점 site 경고 2곳(예류·지열곡: 입구·방문자센터 Wikidata 항목이 없음). 문자열 없이 돌리면 `settings_credit_boch_*` 12건만 실패한다(아래 목록을 넣으면 해결).
- `build_attractions.py verify-quotes TW`: **통과**(23곳, 필드별 출처 일치).
- copycheck(`build_attractions.copycheck`, 캐시 `~/.readyport/copycheck_cache/TW/<id>/{ko,en}wiki.txt`(MediaWiki API) + evidence 스냅샷): **23곳 모두 통과**. 스펀 옛 거리는 한·영 위키 문서가 없어 evidence 대조만.
- 게이트(wave 1, gates.json 기본값): **실패 0** — 23곳 ≥ 12, 보이는 base 지역 3곳(타이베이 9·타이난 5·가오슝 5) 모두 ≥3, daytrip 북해안 4곳 ≥1, 2곳 이상인 종류 5개(역사·유적 7, 박물관·미술관 5, 도시·전망 5, 시장·쇼핑거리 4, 산·자연 2), 최다 종류 30%(< 50%), 상세 최소 충실도 미달 0곳.
- 지역 lint: 모든 곳이 hub 40km 안(가장 먼 곳은 불광산 불타기념관, 가오슝역에서 약 19km). 곳당 크기 2,867~4,458B(한도 4,608B), 파일 약 111KB.
- 출처 도메인 비율(§5.2-14): 가장 많은 taiwan.net.tw가 인용 필드의 13%, 문화자산국 오픈데이터 12% — 30% 상한 안.
- `wiki-fill TW`(22곳, 스펀 없음)·`place-ids TW --write`(23곳, ID만) 실행. rank.order는 아래 D11 순서로 나라 전체에 다시 매겼다.

## 지역

| id | 이름 | 묶음 | kind | hub (Wikidata) | 공항 | 차수 | 관광지 |
|---|---|---|---|---|---|---|---|
| tw_taipei | 타이베이(별칭 단수이·베이터우) | 북부 | base | 타이베이역 Q700588 | TPE·TSA | 1 | 9 |
| tw_north_coast | 지우펀·진과스·스펀·예류(별칭 예스진지) | 북부 | daytrip [tw_taipei], note '타이베이에서 하루 다녀오기도 해요' | 루이팡역 Q6140992 | — | 1 | 4 |
| tw_tainan | 타이난 | 남부 | base | 타이난역 Q699212 | — | 1 | 5 |
| tw_kaohsiung | 가오슝 | 남부 | base | 가오슝역 Q709900 | KHH | 1 | 5 |
| tw_taichung | 타이중 | 중부 | base | 타이중역 Q7675922 | — | 2 | 0 |
| tw_sunmoonlake | 일월담(별칭 르웨탄) | 중부 | daytrip [tw_taichung] | 수이서 선착장 Q52805438 | — | 2 | 0 |
| tw_kenting | 컨딩 | 남부 | base | 헝춘 환승센터 Q11072841 | — | 2 | 0 |
| tw_hualien | 화롄·타이루거 | 동부 | base | 화롄역 Q11615659 | — | 2 | 0 |

- 팩 공항 3개는 모두 1차 지역에 매핑했다(`unmapped_airports` 없음). 1차 선정 근거: 팩 공항이 있는 지역(타이베이·가오슝) + SPEC §3.3 TW 표의 1차(북해안·타이난).
- hub 선택 근거: 각 지역 여행자가 출발하는 철도역·환승센터·선착장의 Wikidata 항목(행정구역 좌표 아님). 북해안은 지우펀·진과스 버스와 핑시선 열차가 갈라지는 루이팡역.
- 경보: 팩 safety가 '대만에 내려진 여행경보 없음' → 지역·관광지 모두 `none`. advisory_basis는 팩 `2026.10.03-2` 기준으로 계산해 넣었다.
- **묶음 별칭 '예스진지'**: 구성 지명 예류·스펀·진과스·지우펀이 네 곳의 `names.ko`에 모두 있다. 구성 지명 lint는 `packs/curation/TW.curation.json`의 `regions.tw_north_coast.group_alias_parts`를 읽는데, 이번 커밋은 두 파일만 넣기로 해서 그 파일은 만들지 않았다 → 합칠 때 `{"regions": {"tw_north_coast": {"group_alias_parts": {"예스진지": ["예류", "스펀", "진과스", "지우펀"]}}}}`를 넣으면 lint가 지킨다.
- 2차 지역 4개는 hub만 정해 두었고 관광지는 없다(promote --wave 1 때 `upcoming_regions`로 넘어간다). 화롄·타이루거는 SPEC대로 risk post_disaster 대상이라 2차.

### 당일 왕복 근거 (tw_north_coast, `_regions/tw_north_coast/extract.json` 7줄, 확인일 2026-10-09)
| 출처 | URL | 요지 |
|---|---|---|
| 交通部觀光署 台灣好行 965 九份金瓜石線 | https://www.taiwantrip.com.tw/Frontend/Route/Select_p?RouteID=R0111 | 타이베이 시내 MRT역 출발 → 진과스(황금박물관), 같은 날 돌아오는 예약 반 — 직접 근거 |
| 台灣好行 795 木柵平溪線 | https://www.taiwantrip.com.tw/Frontend/Route/Select_p?RouteID=R0020 | MRT 동물원역 ↔ 스펀 방문자센터 |
| 台灣好行 716 皇冠北海岸線 | https://www.taiwantrip.com.tw/Frontend/Route/Select_p?RouteID=R0018 | MRT 단수이역 ↔ 예류 지질공원 |
| 台灣好行 856 黃金福隆線 | https://www.taiwantrip.com.tw/Frontend/Route/Select_p?RouteID=R0014 | 루이팡역·지우펀·진과스, 기룽커윈 타이베이–루이팡(–진과스) |
| 新北市立黃金博物館 交通資訊 | https://www.gep.ntpc.gov.tw/xmdoc/cont?xsmsid=0G246368749796990112 | 타이베이에서 열차로 루이팡역 |

## 곳별 (순서 = D11: 공식 지정(부분·등록 포함) → 한국어 위키 조회수 → 영어 조회수, Wikimedia REST 2025-10~2026-09 합계)

### 타이베이 (`tw_taipei`)

| 순서 | id | 이름 | 종류 | 미리보기(summary) | claims/tips/tags · 인용 필드 | 공식 지정 | 위키 조회수 ko / en (2025-10~2026-09) | 크기 |
|---|---|---|---|---|---|---|---|---|
| 1 | `national-palace-museum` | 국립고궁박물원 | 박물관·미술관 · 시설(입장 unknown, 휴무 unknown) | 옛 중국 황실 소장품을 중심으로 한 타이베이의 박물관이에요. | 4/2/0 · 9 | 타이베이시 역사건축 국립고궁박물원(2020) | 2,746 (국립고궁박물원) / 87,764 (National Palace Museum) | 3,491B |
| 2 | `chiang-kai-shek-memorial-hall` | 중정기념당 | 박물관·미술관 · 시설(입장 unknown, 휴무 none) | 타이베이 중정구에 있는 국립 기념관과 공원이에요. | 5/4/1 · 13 | 국가 지정 고적(2007, 등록 이름 臺灣民主紀念園區) | 2,460 (중정기념당) / 58,027 (Chiang Kai-shek Memorial Hall) | 4,101B |
| 3 | `longshan-temple` | 룽산사 | 역사·유적 · 시설(입장 unknown, 휴무 unknown) | 1738년에 세운 타이베이 완화구의 관음 사원이에요. | 6/4/0 · 13 | 국가 지정 고적 艋舺龍山寺(2018) | 1,463 (방카 룽산사) / 30,389 (Longshan Temple (Taipei)) | 4,297B |
| 4 | `fort-san-domingo` | 단수이 홍마오청 | 역사·유적 · 시설(입장 paid, 휴무 irregular) | 단수이강 어귀 언덕 위에 남은 옛 네덜란드 성채예요. | 5/4/0 · 12 | 국가 지정 고적 단수이 홍마오청(1983) | 1,041 (훙마오성) / 16,552 (Fort Santo Domingo) | 4,345B |
| 7 | `beitou-hot-spring-museum` | 베이터우 온천박물관 | 박물관·미술관 · 시설(입장 free, 휴무 mon) | 1913년에 지은 공공 목욕탕 건물을 쓰는 박물관이에요. | 6/2/1 · 12 | 직할시 지정 고적 베이터우 공공욕장(1997) | 276 (베이터우 온천 박물관) / 2,479 (Beitou Hot Spring Museum) | 4,185B |
| 12 | `taipei-101-observatory` | 타이베이 101 전망대 | 도시·전망 · 시설(입장 paid, 휴무 none) | 타이베이 101 빌딩 89층에 있는 전망대예요. | 6/4/0 · 13 | 없음 | 7,429 (타이베이 101) / 1,091,879 (Taipei 101) | 3,990B |
| 13 | `ximending` | 시먼딩 | 시장·쇼핑거리 · 공공 공간 | 타이베이 완화구의 보행자 쇼핑·먹거리 상권이에요. | 6/2/0 · 9 | 없음 | 2,964 (시먼딩) / 29,972 (Ximending) | 3,701B |
| 16 | `shilin-night-market` | 스린 야시장 | 시장·쇼핑거리 · 공공 공간 | 메트로 젠탄역 가까이 여러 거리에 걸친 야시장이에요. | 5/3/1 · 11 | 없음 | 312 (스린 야시장) / 12,435 (Shilin Night Market) | 3,652B |
| 22 | `beitou-thermal-valley` | 베이터우 지열곡 | 산·자연 · 시설(입장 free, 휴무 mon) | 온천이 솟아 김이 피어오르는 베이터우의 골짜기예요. | 5/4/2 · 14 | 없음 | 0 (문서 없음) / 2,701 (Thermal Valley) | 4,192B |

### 지우펀·진과스·스펀·예류 (`tw_north_coast`)

| 순서 | id | 이름 | 종류 | 미리보기(summary) | claims/tips/tags · 인용 필드 | 공식 지정 | 위키 조회수 ko / en (2025-10~2026-09) | 크기 |
|---|---|---|---|---|---|---|---|---|
| 11 | `gold-museum` | 진과스 황금박물관 | 박물관·미술관 · 시설(입장 paid, 휴무 irregular) | 옛 금광 마을 진과스에 있는 신베이시립 광업 박물관이에요. | 6/4/0 · 13 | 신베이시 지정 고적 진과스 태자빈관(2007, 박물관 안내 시설 일부) | 0 (문서 없음) / 1,530 (New Taipei City Gold Museum) | 4,458B |
| 15 | `yehliu-geopark` | 예류 지질공원 | 산·자연 · 시설(입장 paid, 휴무 unknown) | 파도와 바람에 깎인 기암이 늘어선 북해안의 곶이에요. | 6/3/0 · 12 | 없음 | 1,521 (예류지질공원) / 428 (Yehliu Geopark) | 3,886B |
| 20 | `jiufen-old-street` | 지우펀 옛 거리 | 도시·전망 · 공공 공간 | 옛 금광 마을 산비탈에 골목 가게가 모인 거리예요. | 5/2/0 · 8 | 없음 | 0 (문서 없음) / 6,841 (Jiufen Old Street) | 3,283B |
| 23 | `shifen-old-street` | 스펀 옛 거리 | 도시·전망 · 공공 공간 | 가게 문 앞 선로로 기차가 지나가는 옛 거리예요. | 5/3/0 · 9 | 없음 | 0 (문서 없음) / 0 (문서 없음) | 3,255B |

### 타이난 (`tw_tainan`)

| 순서 | id | 이름 | 종류 | 미리보기(summary) | claims/tips/tags · 인용 필드 | 공식 지정 | 위키 조회수 ko / en (2025-10~2026-09) | 크기 |
|---|---|---|---|---|---|---|---|---|
| 5 | `anping-old-fort` | 안평고보 | 역사·유적 · 시설(입장 paid, 휴무 none) | 네덜란드 질란디아 요새 터에 선 안핑의 고적이에요. | 6/1/0 · 10 | 국가 지정 고적 질란디아 요새 유구(1983 지정, 2023 통합) | 809 (질란디아 요새 (타이완)) / 31,475 (Fort Zeelandia (Taiwan)) | 3,889B |
| 6 | `chihkan-tower` | 적감루 | 역사·유적 · 시설(입장 paid, 휴무 none) | 네덜란드 성터 위에 청나라 때 누각이 선 고적이에요. | 5/1/1 · 11 | 국가 지정 고적 적감루(1983) | 525 (츠칸러우) / 10,508 (Fort Provintia) | 3,719B |
| 8 | `eternal-golden-castle` | 억재금성 | 역사·유적 · 시설(입장 paid, 휴무 none) | 청나라가 1876년에 완공한 안핑의 서양식 포대예요. | 6/2/0 · 11 | 국가 지정 고적 이곤신 포대(억재금성)(1983) | 55 (이자이 금성) / 3,225 (Eternal Golden Castle) | 4,079B |
| 9 | `tainan-confucius-temple` | 타이난 공자묘 | 역사·유적 · 시설(입장 unknown, 휴무 none) | 1665년 사당에서 시작한 타이난의 공자 사당이에요. | 6/2/0 · 11 | 국가 지정 고적 타이난 공자묘(1983) | 0 (문서 없음) / 4,752 (Tainan Confucian Temple) | 3,851B |
| 18 | `garden-night-market` | 화원야시장 | 시장·쇼핑거리 · 공공 공간 | 노점이 400개 가까이 모이는 타이난 북구 야시장이에요. | 4/1/1 · 9 | 없음 | 96 (화원 야시장) / 1,640 (Tainan Flower Night Market) | 2,867B |

### 가오슝 (`tw_kaohsiung`)

| 순서 | id | 이름 | 종류 | 미리보기(summary) | claims/tips/tags · 인용 필드 | 공식 지정 | 위키 조회수 ko / en (2025-10~2026-09) | 크기 |
|---|---|---|---|---|---|---|---|---|
| 10 | `cihou-fort` | 치허우 포대 | 역사·유적 · 시설(입장 unknown, 휴무 none) | 가오슝항 입구 언덕 위에 남은 청나라 말기 포대예요. | 6/3/0 · 11 | 국가 지정 고적 치허우 포대(2019) | 0 (문서 없음) / 1,691 (Cihou Fort) | 4,148B |
| 14 | `fo-guang-shan` | 불광산 불타기념관 | 박물관·미술관 · 시설(입장 free, 휴무 tue) | 부처 치아 사리를 모시려고 지은 불교 기념관이에요. | 5/4/2 · 14 | 없음 | 1,603 (불광산불타기념관) / 12,505 (Fo Guang Shan Buddha Museum) | 4,171B |
| 17 | `liuhe-night-market` | 리우허 야시장 | 시장·쇼핑거리 · 공공 공간 | 낮엔 큰길, 밤엔 먹거리 노점이 들어서는 야시장이에요. | 6/2/1 · 11 | 없음 | 184 (리우허 야시장) / 2,161 (Liouhe Night Market) | 3,666B |
| 19 | `lotus-pond` | 연지담 | 도시·전망 · 공공 공간 | 용호탑과 누각들이 물 위에 선 쭤잉의 호수예요. | 5/3/0 · 10 | 없음 | 0 (문서 없음) / 7,503 (Lotus Pond, Kaohsiung) | 3,669B |
| 21 | `pier-2-art-center` | 보얼 예술특구 | 도시·전망 · 공공 공간 | 항구 옆 옛 창고들을 예술 공간으로 바꾼 지구예요. | 5/4/0 · 11 | 없음 | 0 (문서 없음) / 3,036 (Pier-2 Art Center) | 3,818B |


### 곳별 빈 칸·메모
- **국립고궁박물원**: npm.gov.tw가 robots 차단이라 입장료·정기 휴관은 `unknown`(화면에 안 보임). 가는 법은 관광청 안내의 버스 정류장 '故宮博物院(正館)'. 지정은 이번에 확인한 타이베이시 **역사건축(2020, 등록)** — D11에서 지정으로 셌다.
- **중정기념당**: 지침 초안에는 '지정 없음'이었으나 문화자산국 古蹟 데이터에 **臺灣民主紀念園區(國定古蹟, 2007-11-09)**가 있고 주소·관리 주체가 같아 지정으로 넣었다. 입장 무료 문구가 없어 entry `unknown`, 정기 휴관 `none` + 휴관 공지일 메모.
- **룽산사**: 입장료·휴무 공식 문구가 없어 둘 다 `unknown`. 참배 순서 등 사원 공식 안내 tips 4개.
- **단수이 홍마오청 / 진과스 황금박물관**: 정기 휴관이 '매달 첫째 월요일'이라 enum에 맞는 값이 없어 `["irregular"]` + `closed_note_ko`로 적었다.
- **베이터우 지열곡**: 전용 사이트가 없어 `official_url`은 관리 주체 臺北自來水事業處 페이지. 좌표는 지열곡 자체(site).
- **베이터우 온천박물관**: 박물관 사이트가 운영 정보를 글로 보여 주지 않아(이미지 PDF) 관할 타이베이시 문화국 안내·FAQ를 썼다.
- **예류 지질공원**: 운영 사이트 대신 관리기관 北海岸及觀音山國家風景區管理處 페이지로 사실·요금 유무(유료)·교통을 채웠다. 정기 휴무 `unknown`. 좌표는 공원 자체(site).
- **스펀·지우펀 옛 거리**: 운영 주체가 없는 거리라 신베이시 관광 포털(newtaipei.travel 영문)과 台灣好行 노선 페이지가 1차 출처. 공식 중문 주소가 없어 `address_local: null`. 천등은 시 관광국 페이지가 직접 안내해 tip으로만 넣었다.
- **적감루**: 공식 페이지의 해신묘·비석 받침 수리 공지로 `status: partial`(30일 확인 주기 대상).
- **타이난 공자묘·화원야시장**: 시설 자체 사이트를 못 찾아 twtainan.net 영문 페이지를 `official_url`로. 공자묘 입장료 근거 없음 → `unknown`. 화원야시장 쉬는 날 월·화·수(twtainan, 2024-06-05 갱신).
- **타이난 고적 4곳 access**: 타이난시 교통국 버스 정보(2384.tainan.gov.tw)의 정류장 이름으로 채웠다(고적 페이지의 노선 번호는 옛 번호라 쓰지 않음).
- **치허우 포대**: 치진 해변(旗津海水浴場)을 먼저 보려 했으나 관할 khh.travel이 403이라 공식 사실·교통을 못 채워 지침의 대안인 국가 지정 고적 치허우 포대로 바꿨다. 정기 휴무 `none`은 문화자산국 레코드의 '全天候自由參觀' 한 줄 근거, 무료 문구는 없어 entry `unknown`.
- **불광산**: 사찰 대신 공식 사이트에 입장 무료·화요일 휴관·복장·버스 안내가 모두 있는 **불타기념관**(museum)을 방문 지점으로 골랐다.

## 다음 차수
- **양명산 국립공원**(타이베이, nature): ymsnp.gov.tw·nps.gov.tw가 일반 요청에 내용 없는 자바스크립트 페이지만 주고, travel.taipei는 403, 다른 공식 교통 근거를 못 찾아 access를 채울 수 없었다. 방문 지점 Wikidata 항목도 없다. 다음에는 擎天崗(Q11078643)·小油坑(Q10959487)처럼 항목이 있는 지점으로 나눠 싣는 안을 검토.
- **치진 해변**(가오슝, sea_island): khh.travel 403. 해수욕장 공사 보도가 있으나 공식 확인 못 함.
- **祀典武廟**(타이난, 國定古蹟 19831228000009), **打狗英國領事館及官邸**(가오슝, 國定古蹟 20190223000001), 샤오바이궁·후웨이 포대(단수이, 홍마오청과 통합권): 시간 관계로 이번에 넣지 않음.
- 2차 지역(타이중·일월담·컨딩·화롄/타이루거): SPEC 차수대로 다음 차례.

## 넣어야 할 출처표기 문자열 (`app/src/main/res/values/strings_attractions.xml`, 문화자산국 오픈데이터 = 政府資料開放授權條款-第1版 출처 표기)
검증 때 아래 그대로 넣었다가 되돌렸다. 이 문자열이 없으면 `check TW`가 실패한다.
```xml
<string name="settings_credit_boch_npm">문화부 문화자산국 국가문화자산망 — 국립고궁박물원(타이베이시 역사건축)</string>
<string name="settings_credit_boch_cksmh">문화부 문화자산국 국가문화자산망 — 타이완 민주기념원구(臺灣民主紀念園區, 국가 지정 고적)</string>
<string name="settings_credit_boch_longshan">문화부 문화자산국 국가문화자산망 — 룽산사(국가 지정 고적)</string>
<string name="settings_credit_boch_ximending_redhouse">문화부 문화자산국 국가문화자산망 — 시먼훙러우(타이베이시 지정 고적)</string>
<string name="settings_credit_boch_fort_san_domingo">문화부 문화자산국 국가문화자산망 — 단수이 홍마오청(국가 지정 고적)</string>
<string name="settings_credit_boch_beitou_bathhouse">문화부 문화자산국 국가문화자산망 — 베이터우 공공욕장(직할시 지정 고적)</string>
<string name="settings_credit_boch_gold_taizi">문화부 문화자산국 국가문화자산망 — 진과스 태자빈관(신베이시 지정 고적)</string>
<string name="settings_credit_boch_chihkan">문화부 문화자산국 국가문화자산망 — 적감루(국가 지정 고적)</string>
<string name="settings_credit_boch_anping">문화부 문화자산국 국가문화자산망 — 질란디아 요새 유구·안평고보(국가 지정 고적)</string>
<string name="settings_credit_boch_tainan_confucius">문화부 문화자산국 국가문화자산망 — 타이난 공자묘(국가 지정 고적)</string>
<string name="settings_credit_boch_eternal_golden_castle">문화부 문화자산국 국가문화자산망 — 이곤신 포대(억재금성)(국가 지정 고적)</string>
<string name="settings_credit_boch_cihou_fort">문화부 문화자산국 국가문화자산망 — 치허우 포대(국가 지정 고적)</string>
```

## 사장님 판단이 필요한 점
1. **종류 판정 3곳**(판정표에 없는 곳): 보얼 예술특구 = 도시·전망(공공 공간; 문화국이 운영하는 예술 지구, 안에 유료 전시관 있음 — museum 대안), 연지담 = 도시·전망(공공 공간; 용호탑은 mentions, 좌표는 용호탑 — nature 대안·이름 '용호탑' 대안), 불광산 = 불타기념관(museum) 선택(사찰 heritage 대안). 판정이 정해지면 `docs/attractions/CATEGORY_RULES.md`에 한 줄 추가 필요.
2. **지정 인정**: (a) 중정기념당 = 臺灣民主紀念園區(國定古蹟 2007) 등록 이름까지 보여 줄지, (b) 황금박물관 = 박물관 '主題設施'인 태자빈관(直轄市定古蹟, 소유 台電·수리 중 비공개)을 부분 지정으로 칠지(빼면 designation과 claims c6 삭제), (c) 스린 야시장 안 시 지정 고적 2곳(士林公有市場·士林慈諴宮)·시먼딩의 시먼훙러우를 부분 지정으로 칠지 — 지금은 mentions·claim으로만 두고 지정으로 세지 않았다.
3. **정기 휴관 '매달 첫째 월요일'**(홍마오청·황금박물관): enum에 값이 없어 `irregular` + 메모. 화면이 '부정기 휴관'으로 보이면 오해 소지 → `unknown` + 메모로 바꾸거나 enum 값 추가를 검토.
4. **적감루 status partial**(경내 일부 수리): 그대로 둘지(30일 확인 대상) open으로 둘지.
5. **지우펀 '비정성시' 촬영(1980년대 영화)**: 신베이시 관광 포털이 밝힌 사실이라 §5.2-8 조건은 맞아 넣었다(애니메이션 배경설은 넣지 않음). 영화 언급을 모두 빼려면 claims c3와 body 셋째 문장 삭제.
6. **'입장 무료' 근거 수준**: 베이터우 온천박물관은 박물관 자체가 아니라 관할 문화국 안내, 지열곡은 관리 주체(수도사업처) FAQ가 근거. 더 엄격히 하려면 두 곳의 entry를 `unknown`, free_entry 태그 삭제.
7. **치진 해변 → 치허우 포대 대체**, **타이난 공자묘 휴무 'New Year’s Eve'**(음력·양력 불명이라 영문 병기) 표기, 대표 한국어 이름(적감루·안평고보·억재금성 — 위키 라벨 츠칸러우·질란디아 요새·이자이 금성은 별칭) 확인.
8. 국립고궁박물원 입장료·휴관일은 robots 차단이라 사장님이 npm.gov.tw를 직접 확인해 주셔야 채울 수 있다.

---

# 2차 (2026-10-10)

- 데이터: `packs/drafts/TW/attractions.json` (release `draft`, version **`2026.10.10-1`**). 지역 8개 그대로, 관광지 **23 → 44곳**(새로 21곳), 출처 87 → **140개**.
- 새 지역 4곳을 채웠다: **타이중**(base, 3곳) · **일월담**(타이중에서 하루 다녀오는 곳, 4곳) · **컨딩**(base, 4곳) · **화롄·타이루거**(base, 3곳). 모두 `wave: 2`.
- 1차 보고서 '다음 차수'에 적어 둔 보류분 7곳도 함께 채웠다: 양밍산을 Wikidata 항목이 있는 지점으로 나눈 **칭톈강·샤오유컹**, 단수이의 **후웨이 포대·단수이 세관 세무사 관저(샤오바이궁)**, 가오슝의 **치진 해수욕장·다거우 영국 영사관**, 타이난의 **쓰뎬우먀오**.
- 근거는 1차에서 이미 모아 둔 `~/.readyport/evidence/TW/<id>/` 스냅샷을 **그대로 다시 썼다**(새로 수집한 페이지 없음). 곳마다 `extract.json`을 새로 만들어 인용을 필드별로 묶었다. 확인일은 모두 2026-10-10.
- 막힌 사이트는 1차 때 사람과 같은 열람(실제 브라우저)으로 저장해 둔 스냅샷을 썼다: `travel.taipei`(칭톈강), `khh.travel`(치진 해수욕장·다거우 영국 영사관), `travel.taichung.gov.tw`(펑자 야시장·가오메이 습지), `hualien.travel`(타이루거·치싱탄·둥다먼). 머리줄에 `collected_via: manual_browser_save`가 남아 있다. 1차 때 못 뚫은 `ymsnp.gov.tw`·`nps.gov.tw`는 이번에도 쓰지 않고, 양밍산 두 곳은 관광서 `taiwan.net.tw`와 타이베이 관광 포털·공차 동태 시스템으로 채웠다.
- D4-A: 여는 시간·요금 숫자 없음(계절별 개방 시간·휴관일은 숫자 없이 문장으로만). D12: 위키 문장을 옮기지 않고 공식 사실로 새로 썼다(해요체). D13·D14·D18·D19·D20·D21: 해당 곳 없음. `entry: free`를 새로 붙인 곳은 없다(치진 해수욕장은 '입장료 없음'이 공식 안내에 있지만 `public_space`라 태그를 붙이지 않고 claims로만 남겼다).
- 연도 표기: 문화자산국 레코드나 관광서 글에 서력이 함께 적힌 사실만 서력으로 썼다. 민국 연호만 있는 사실(컨딩 국가공원 설립 연도, 어롼비 공원 개방 연도, 등대 일반 공개 연도 등)은 **환산하지 않고 아예 넣지 않았다**.

## 좌표

- 19곳은 Wikidata(QID) 좌표. **2곳은 OpenStreetMap 좌표**(2026-10-10 결정, source `osm`, license `ODbL-1.0`):
  - 르웨탄 케이블카 — Wikidata `Q11086169`에 좌표가 없어 `node/800331755`(日月潭端車站, aerialway=station).
  - 치진 해수욕장 — Wikidata `Q86731337`에 좌표가 없어 `way/145773967`(旗津海水浴場).
- 마오비터우 공원은 중국어 위키가 컨딩 국가공원으로 넘겨주지만, OSM 태그에서 별도 항목 `Q49521379`(貓鼻頭公園)를 찾아 그 Wikidata 좌표를 썼다.
- 타이루거는 공원 항목 `Q707427` 좌표(화롄역에서 약 33km — 40km 안)를 썼다. 방문 지점(타이루거 방문자센터) Wikidata 항목이 없어 `geo.kind`는 `site`이고 '방문 지점 권장' 경고가 남는다.

## 새 장소 (rank.order 순)

| order | id | 한국어 이름 | 지역 | 종류 | 사실 수 | 지정 | ko/en 조회수 | status | 빈 칸 |
|---|---|---|---|---|---|---|---|---|---|
| 10 | eluanbi-park | 어롼비 공원 | 컨딩 | 산·자연 | claims 8 · tips 2 | 국가 지정 고적 어롼비 등대(2024) | – / 3,308 | open | regular_closed=unknown, photo_link |
| 11 | former-british-consulate-takao | 다거우 영국 영사관 | 가오슝 | 역사·유적 | claims 6 · tips 2 | 국가 지정 고적 다거우 영국 영사관 및 관저(2019) | – / 2,780 | **partial** | photo_link |
| 12 | state-temple-of-the-martial-god | 쓰뎬우먀오 | 타이난 | 역사·유적 | claims 8 · tips 2 | 국가 지정 고적 쓰뎬우먀오(1983) | – / 2,353 | open | entry=unknown, photo_link |
| 13 | hobe-fort | 후웨이 포대 | 타이베이(단수이) | 역사·유적 | claims 8 · tips 2 | 국가 지정 고적 후웨이 포대(1985) | – / 2,234 | open | photo_link |
| 16 | hengchun-old-town | 헝춘 옛 성 | 컨딩 | 역사·유적 | claims 8 · tips 2 | 국가 지정 고적 헝춘 옛 성(1985) | – / 1,373 | open | entry=unknown, photo_link |
| 17 | tamsui-customs-officers-residence | 단수이 세관 세무사 관저 | 타이베이(단수이) | 역사·유적 | claims 6 · tips 2 | 직할시 지정 고적(1997) | – / 473 | open | entry=unknown, photo_link |
| 22 | taroko-gorge | 타이루거 국가공원 | 화롄 | 산·자연 | claims 6 · tips 3 | 없음 | 372 / 26,550 | **partial** | google_place_id, photo_link |
| 24 | sun-moon-lake | 르웨탄 | 일월담 | 산·자연 | claims 6 · tips 3 | 없음 | 262 / 35,516 | open | address_local, photo_link |
| 27 | fengjia-night-market | 펑자 야시장 | 타이중 | 시장·쇼핑거리 | claims 5 · tips 2 | 없음 | 64 / 3,981 | open | photo_link |
| 28 | qixingtan-beach | 치싱탄 해변 | 화롄 | 바다·섬 | claims 5 · tips 3 | 없음 | 58 / 2,745 | open | photo_link |
| 29 | kenting-national-park | 컨딩 국가공원 | 컨딩 | 산·자연 | claims 8 · tips 3 | 없음 | – / 11,170 | open | photo_link |
| 32 | sun-moon-lake-wenwu-temple | 원우먀오 | 일월담 | 역사·유적 | claims 6 · tips 1 | 없음 | – / 6,182 | open | entry=unknown, photo_link |
| 33 | gaomei-wetlands | 가오메이 습지 | 타이중 | 산·자연 | claims 6 · tips 2 | 없음 | – / 3,821 | open | photo_link |
| 34 | national-museum-of-natural-science | 국립자연과학박물관 | 타이중 | 박물관·미술관 | claims 6 · tips 2 | 없음 | – / 3,709 | **partial** | photo_link |
| 37 | dongdamen-night-market | 둥다먼 야시장 | 화롄 | 시장·쇼핑거리 | claims 6 · tips 3 | 없음 | – / 1,759 | open | photo_link |
| 38 | sun-moon-lake-ropeway | 르웨탄 케이블카 | 일월담 | 산·자연 | claims 6 · tips 3 · tags 2 | 없음 | – / 1,393 | open | photo_link |
| 39 | cijin-beach | 치진 해수욕장 | 가오슝 | 바다·섬 | claims 5 · tips 2 | 없음 | – / – | **partial** | wiki, photo_link |
| 40 | maobitou-park | 마오비터우 공원 | 컨딩 | 바다·섬 | claims 5 · tips 2 | 없음 | – / – | open | wiki, entry=unknown, regular_closed=unknown, photo_link |
| 41 | qingtiangang | 칭톈강 | 타이베이(양밍산) | 산·자연 | claims 4 · tips 2 | 없음 | – / – | open | wiki, address_local, photo_link |
| 43 | xiaoyoukeng | 샤오유컹 | 타이베이(양밍산) | 산·자연 | claims 4 · tips 2 | 없음 | – / – | open | wiki, photo_link |
| 44 | xuanguang-temple | 쉬안광쓰 | 일월담 | 역사·유적 | claims 5 · tips 1 | 없음 | – / – | open | wiki, entry=unknown, photo_link |

조회수는 Wikimedia REST pageviews 2025-10 ~ 2026-09 합계다. '–'는 그 언어 위키백과 문서가 없다는 뜻이다.

### 한국어 미리보기(요약 한 줄)

- 펑자 야시장 — 펑자대학 옆 길을 따라 늘어선 타이중의 야시장이에요.
- 국립자연과학박물관 — 공룡과 미라, 열대우림 온실을 함께 보는 국립 박물관이에요.
- 가오메이 습지 — 다자시 하구 갯벌에 나무 데크가 놓인 습지예요.
- 르웨탄 — 라루섬을 사이에 두고 해와 달 모양으로 갈리는 산중 호수예요.
- 원우먀오 — 호수 북쪽 언덕에 세 전각이 이어진 큰 사당이에요.
- 쉬안광쓰 — 호수 바로 위 계단 끝에 앉은 작은 절이에요.
- 르웨탄 케이블카 — 이다사오 옆에서 산을 넘어 호수를 내려다보는 케이블카예요.
- 컨딩 국가공원 — 헝춘반도 남쪽 삼면이 바다인 타이완 첫 국가공원이에요.
- 어롼비 공원 — 타이완 남쪽 끝 산호초 바위숲과 흰 등대가 있는 공원이에요.
- 마오비터우 공원 — 타이완해협과 바시해협이 갈리는 산호초 곶이에요.
- 헝춘 옛 성 — 사철 봄 같다고 이름 붙은 옛 현성과 네 성문이에요.
- 타이루거 국가공원 — 리우시가 대리암을 깎아 만든 깊은 협곡 국가공원이에요.
- 치싱탄 해변 — 둥근 자갈이 깔린 초승달 모양 해만이에요.
- 둥다먼 야시장 — 흩어져 있던 시장을 한곳에 모은 화롄의 야시장이에요.
- 칭톈강 — 다툰 화산군 사이에 펼쳐진 넓은 풀밭이에요.
- 샤오유컹 — 분기공에서 김이 솟는 양밍산의 화산 지형이에요.
- 후웨이 포대 — 청프 전쟁 뒤 단수이 언덕에 쌓은 서양식 포대예요.
- 단수이 세관 세무사 관저 — 단수이 언덕에 안팎을 흰색으로 칠한 옛 세관 관저예요.
- 치진 해수욕장 — 치진섬 서쪽에 길게 뻗은 모래 해변이에요.
- 다거우 영국 영사관 — 가오슝항이 내려다보이는 언덕의 옛 영국 영사관이에요.
- 쓰뎬우먀오 — 관성제군을 모시고 관방 제사를 받던 타이난의 관제묘예요.

## status가 open이 아닌 4곳 (모두 공식 공고 근거)

| id | status | 근거 |
|---|---|---|
| taroko-gorge | partial + risk `post_disaster` | 화롄현 관광 포털 '受震災影響多處封閉，出發前務必查詢最新開放資訊' |
| national-museum-of-natural-science | partial + risk `renovation` | 과학센터 일부 층 전시구역 휴관 공고(공식 사이트) |
| former-british-consulate-takao | partial + risk `renovation` | 가오슝 관광 포털 '山上官邸進行維修工程，室內正常營運，請由古蹟大門進出' |
| cijin-beach | partial + risk `renovation` | 가오슝 관광 포털의 입구 광장·산책로 공사 구역 울타리 공고(표시 따라 우회) |

## 1차 장소 손댄 것

- **사실 내용은 하나도 고치지 않았다.** `rank.order`만 D11에 따라 나라 전체(44곳) 다시 계산했다(공식 지정 → 한국어 위키 조회수 내림차순 → 영어 조회수 내림차순). 1차 23곳의 order 값이 새 곳들과 섞이며 바뀌었다.
- 1차 보고서의 '종류 판정'·'지정 인정' 등 미해결 항목은 그대로 둔다(아래 사장님 판단 목록 8번).

## 검증 (2026-10-10, 이 커밋 상태)

출처표기 문자열 7줄(아래)을 `strings_attractions.xml`에 임시로 넣고 돌린 뒤 되돌렸다(앱 파일은 커밋하지 않음).

- `check TW`: **통과**. 남은 경고 — facts.booking 없음(공식 예약 안내 없음), `nature`·`sea_island` 방문 지점 경고 11곳(해당 지점 Wikidata 항목이 없음).
- `verify-quotes TW`: **통과**(44곳, 필드별 출처 일치).
- copycheck: 새 21곳 **모두 통과**(`record TW --ids …`). 캐시는 `~/.readyport/copycheck_cache/TW/<id>/`에 새로 받았고, 한·영 위키 문서가 없는 6곳(쉬안광쓰·마오비터우 공원·칭톈강·샤오유컹·치진 해수욕장·스펀 옛 거리)은 evidence 스냅샷 대조만 한다.
- `promote TW --wave 2` 모의 실행(임시 키 `rp-att-tmp-tw`를 임시 폴더에 만들어 쓰고 끝나고 지웠다. 서명 결과물은 커밋하지 않고 `git checkout`으로 되돌렸다): **게이트 실패 0건**.
- 지역 lint: 모든 곳이 hub 40km 안(가장 먼 곳은 타이루거, 화롄역에서 약 33km). 곳당 크기 한도(4,608B) 안.
- `wiki-fill TW`: 21곳 갱신(6곳은 한·영 위키 문서 없음). `place-ids TW --write`: 20곳 채움, 타이루거는 좌표 1km 안에 후보가 없어 비워 뒀다.

## 넣어야 할 출처표기 문자열 (2차에서 새로 필요한 7줄)

1차 12줄에 더해 아래 7줄이 필요하다. 없으면 `check TW`가 실패한다.

```xml
<string name="settings_credit_osm">OpenStreetMap 기여자 — 일부 방문 지점 좌표 (ODbL 1.0)</string>
<string name="settings_credit_boch_eluanbi_lighthouse">문화부 문화자산국 국가문화자산망 — 어롼비 등대(국가 지정 고적)</string>
<string name="settings_credit_boch_hengchun">문화부 문화자산국 국가문화자산망 — 헝춘 옛 성(국가 지정 고적)</string>
<string name="settings_credit_boch_hobe_fort">문화부 문화자산국 국가문화자산망 — 후웨이 포대(국가 지정 고적)</string>
<string name="settings_credit_boch_tamsui_customs_residence">문화부 문화자산국 국가문화자산망 — 전 청나라 단수이 세관 세무사 관저(직할시 지정 고적)</string>
<string name="settings_credit_boch_british_consulate">문화부 문화자산국 국가문화자산망 — 다거우 영국 영사관 및 관저(국가 지정 고적)</string>
<string name="settings_credit_boch_martial_temple">문화부 문화자산국 국가문화자산망 — 쓰뎬우먀오(국가 지정 고적)</string>
```

## 아직 보류한 것과 이유

- **미야하라(宮原眼科, 타이중)**: 타이중시 관광 포털에 소개가 있지만 민간 제과 업체가 운영하는 가게이고, 공식 안내에 대중교통 근거가 없어 `access`를 채울 수 없었다. 종류 판정도 애매하다(역사 건물 + 상점). 다음 차수에 교통 근거를 찾으면 넣는다.
- **타이중 레인보우 빌리지·국립타이완미술관·이중제 상권**: 근거를 모으지 않았다(이번 차수 근거 범위 밖).
- **일월담 쉬안짱쓰·츠언타·향산 방문자센터**: 관리처 페이지 스냅샷을 모으지 않았다. 일월담은 하루 다녀오는 지역이라 4곳으로도 기준을 넘긴다.
- **컨딩 국립해양생물박물관·사딩 자연공원·룽롼탄**: 근거 미수집.
- **화롄 쓰바 고지·치싱 가쓰오 박물관·마타이안**: 근거 미수집. 화롄은 base 지역 최소 3곳을 겨우 채웠으니 다음 차수에 보강하면 좋겠다.
- **양밍산 국가공원 자체 항목**: 여전히 `ymsnp.gov.tw`·`nps.gov.tw`가 자동 요청에 내용 없는 자바스크립트만 주고 방문 지점 Wikidata 항목도 없어 넣지 않았다. 대신 항목이 있는 칭톈강(Q11078643)·샤오유컹(Q10959487) 두 지점으로 나눠 실었다(1차 보고서에서 검토하자고 한 안을 그대로 적용).
- **예류·국립고궁박물원**: 1차와 같다. robots.txt가 Claude 계열을 막아 요금·휴관일을 못 채운다(사장님이 직접 확인해 주셔야 한다).
- **타이루거 google_place_id**: Places Text Search가 좌표 1km 안에서 후보를 못 찾았다. 방문자센터 좌표로 다시 찾거나 비워 두는 안.

## 사장님 판단이 필요한 점

1. **르웨탄 케이블카 종류 판정**: 판정표에 없는 유형이다. 지금은 `nature` + 태그 `cable_car`·`mountain_view`로 두었다(대안: `city_view`). 민간(주족문화촌)이 운영하는 유료 시설이라 `theme_park`로 볼 여지도 있다. 정해지면 `docs/attractions/CATEGORY_RULES.md`에 한 줄 추가가 필요하다.
2. **마오비터우 공원 입장료**: 관리처 안내에 '시험 삼아 무료 입장'과 요금이 함께 적혀 있다. 지금은 `entry: unknown` + 안내 문장으로 두었다. `free`로 올릴지, 요금 안내가 돌아올 때까지 unknown으로 둘지.
3. **'매달 첫째 월요일/수요일 휴무' 표기**: 1차의 홍마오청·황금박물관과 같은 문제다. 후웨이 포대·단수이 세관 세무사 관저(매달 첫째 월요일), 르웨탄 케이블카(매달 첫째 수요일)도 enum에 값이 없어 `irregular` + 메모로 두었다. 화면에 '부정기 휴관'으로 보이면 오해 소지가 있어 enum 값 추가를 다시 검토해 주시면 좋겠다.
4. **어롼비 공원·마오비터우 공원 `regular_closed: unknown`**: 관리처 페이지에 계절별 개방 시간만 있고 정기 휴관일 문구가 없다. '쉬는 날 없음(none)'으로 볼지 unknown으로 둘지.
5. **타이루거 노출 여부**: 지진 피해로 여러 구간이 막혀 `status: partial` + `risk: post_disaster`로 실었다. 화롄 base 지역 3곳 가운데 하나라 빼면 지역이 기준(3곳)에 못 미친다. 그대로 실을지, 개방 구간이 늘 때까지 화롄 지역을 미룰지.
6. **대표 한국어 이름 확인**: 쓰뎬우먀오 / 원우먀오 / 쉬안광쓰 / 칭톈강 / 샤오유컹 / 후웨이 포대 / 어롼비 공원 / 마오비터우 공원 / 가오메이 습지 / 둥다먼 야시장 — 모두 중국어 음을 그대로 적었다(`ko_basis: editorial`). 르웨탄은 `ko_paren`에 '일월담'을 함께 넣었다. 한자 독음('일월담 문무묘', '현광사', '호미 포대' 등)을 앞세울지.
7. **치진 해변 이름**: 1차 보고서에서 '치진 해변 → 치허우 포대 대체'로 적었는데, 이번에 공식 공사 공고 근거가 생겨 **치허우 포대와 함께** `cijin-beach`(旗津海水浴場, '치진 해수욕장')로 실었다. 두 곳을 모두 둘지, 치진 쪽을 하나로 합칠지.
8. **1차 보고서의 미해결 항목 1~8**(종류 판정 3곳, 지정 인정 3건, 적감루 status, 지우펀 영화 언급, '입장 무료' 근거 수준, 고궁박물원 요금)은 그대로 남아 있다.
