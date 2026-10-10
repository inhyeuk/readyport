# 태국(TH) 관광지 1차 작업본 보고 — 2026-10-09

24곳(방콕 8·아유타야 2·매끌롱 2·파타야 4·치앙마이 4·푸껫 4). check·verify-quotes·copycheck 통과, wave 1 게이트 실패 0. 근거는 ~/.readyport/evidence/TH (저장소 밖).

필요한 출처 표시: settings_credit_unesco_576 · settings_credit_unesco_mow_watpho · settings_credit_cnx_unesco_tl

다음 차수: 빅 부다(산사태 뒤 상태 미확인), 치앙마이 나이트 바자, 님만해민, 아이콘시암, 파타야 왓 프라 야이.

## 2차 (2026-10-10)

작업본 `packs/drafts/TH/attractions.json` = **47곳·12지역**, `version: 2026.10.10-1`.
새 지역 6개(`wave: 2`): **칸차나부리·도이 인타논·치앙라이·끄라비(아오낭)·피피섬·코사무이**. 새 장소 **23곳**.
`check` · `verify-quotes` · 복사 대조 모두 통과. 일회용 키로 `promote TH --wave 2` 모의 실행 → **게이트 실패 0건**(서명 결과물은 커밋하지 않았어요).
근거는 `~/.readyport/evidence/TH/<id>/{N.txt, extract.json}`, 복사 대조 캐시는 `~/.readyport/copycheck_cache/TH/<id>/`(저장소 밖).

### 새 지역과 기준점(hub)

| 지역 | 종류 | hub | 근거 |
|---|---|---|---|
| `th_kanchanaburi` (35) | daytrip [th_bangkok] | 칸차나부리역(Q6895690) | 헬파이어 패스 공식 안내가 칸차나부리 버스터미널·남똑선 기차를 기준으로 적어요 |
| `th_inthanon` (55) | daytrip [th_chiangmai] | 도이 인타논 정상(Q1140075) | 공원 안에 Wikidata 교통 거점이 없어 1위 관광지의 방문 지점 |
| `th_chiangrai` (58) | base | 왓 롱쿤(Q496543) | 치앙라이에 역·터미널·선착장 Wikidata 항목이 없음 — **사장님 결정 필요**(아래 3번) |
| `th_krabi` (70) | base | 아오낭 해변(Q49320921) | 국립공원·선사 공식 안내가 아오낭을 섬·해변행 배 출발점으로 적어요 |
| `th_phiphi` (75) | daytrip [th_phuket, th_krabi], 배로 가요 | 피피돈 톤사이만(Q511957) | 톤사이 선착장 Wikidata 항목 없음 → 1위 관광지 방문 지점 |
| `th_samui` (80) | base, 배로 가요 | 왓 프라 야이(꼬판, Q15907894) | 나톤 선착장 Wikidata 항목 없음 → 1위 관광지 방문 지점. 섬 입구는 수랏타니 돈삭 선착장발 여객선 |

- 모든 지역 여행경보 **1단계**(mofa_th, 2026-09-28). 딱(Tak) 주·남부 3단계 구역과 겹치는 곳은 없어요.
- 공항은 그대로 BKK·DMK(방콕)·HKT(푸껫)뿐이라 `unmapped_airports`는 비어 있어요. 치앙라이(CEI)·사무이(USM)는 팩에 없는 공항이에요.
- 40km 초과 유지 근거는 `packs/curation/TH.curation.json`에 적었어요(골든 트라이앵글 68km). 칸차나부리는 daytrip이라 헬파이어 패스 71km는 경고만 나요.

### 새 장소 (지역별, 순서 = D11 재산정 결과)

**칸차나부리 (daytrip, 3곳)**
| 순서 | id | 이름 | 종류 | 사실·팁 | 한국어 미리보기 | 빈 칸 |
|---|---|---|---|---|---|---|
| 7 | `river-kwai-bridge` | 콰이강의 다리 | heritage | 사실 5·팁 2 | 제2차 세계대전 때 포로들이 지은 철도 다리예요. | 입장료 |
| 12 | `erawan-national-park` | 에라완 국립공원 | nature | 사실 5·팁 2 | 7단으로 떨어지는 폭포가 있는 국립공원이에요. | 없음 |
| 21 | `hellfire-pass` | 헬파이어 패스 | museum | 사실 5·팁 4 | 포로들이 깎아 낸 철도 산길과 추모관이에요. | 없음 |

**도이 인타논 (daytrip, 3곳)**
| 순서 | id | 이름 | 종류 | 사실·팁 | 한국어 미리보기 | 빈 칸 |
|---|---|---|---|---|---|---|
| 8 | `doi-inthanon` | 도이 인타논 | nature | 사실 6·팁 3 | 타이에서 가장 높은 산이에요. | 없음 |
| 36 | `wachirathan-falls` | 와치라탄 폭포 | nature | 사실 5·팁 2 | 도이 인타논 길가에 있는 큰 폭포예요. | 없음 |
| 42 | `kiew-mae-pan` | 끼우매판 자연길 | nature | 사실 5·팁 2 | 운해를 보는 도이 인타논 자연길이에요. | 없음 |

**치앙라이 (base, 5곳)**
| 순서 | id | 이름 | 종류 | 사실·팁 | 한국어 미리보기 | 빈 칸 |
|---|---|---|---|---|---|---|
| 5 | `golden-triangle` | 골든 트라이앵글 | city_view | 사실 5·팁 2 | 세 나라가 만나는 메콩강 강가예요. | 없음 |
| 18 | `wat-rong-khun` | 왓 롱쿤 | heritage | 사실 6·팁 2 | 거울 조각으로 꾸민 흰 사원이에요. | 입장료 |
| 28 | `wat-rong-suea-ten` | 왓 롱수아텐 | heritage | 사실 6·팁 2 | 파란색으로 꾸민 법당이 있는 사원이에요. | 입장료 |
| 31 | `baan-dam-museum` | 반담 박물관 | museum | 사실 3·팁 2 | 검게 칠한 건물에 조각을 모은 미술관이에요. | 주소·입장료·쉬는 요일·짧은 현지명 |
| 46 | `chiang-rai-clock-tower` | 치앙라이 시계탑 | city_view | 사실 4·팁 1 | 금빛 무늬로 꾸민 치앙라이 시계탑이에요. | 짧은 현지명 |

**끄라비(아오낭) (base, 3곳)**
| 순서 | id | 이름 | 종류 | 사실·팁 | 한국어 미리보기 | 빈 칸 |
|---|---|---|---|---|---|---|
| 22 | `railay-beach` | 라일레이 해변 | sea_island | 사실 5·팁 2 | 배로만 닿는 절벽 아래 해변이에요. | 없음 |
| 24 | `wat-tham-suea` | 왓 탐수아 | heritage | 사실 4·팁 2 | 호랑이 발자국 바위에서 이름이 온 사원이에요. | 입장료·쉬는 요일 |
| 43 | `ao-nang-beach` | 아오낭 해변 | sea_island | 사실 5·팁 2 | 섬으로 가는 배가 뜨는 끄라비 해변이에요. | 없음 |

**피피섬 (daytrip, 2곳)**
| 순서 | id | 이름 | 종류 | 사실·팁 | 한국어 미리보기 | 빈 칸 |
|---|---|---|---|---|---|---|
| 4 | `phi-phi-islands` | 피피섬 | sea_island | 사실 5·팁 2 | 배로 가는 안다만해의 섬 묶음이에요. | 주소 |
| 40 | `maya-bay` | 마야 베이 | sea_island | 사실 4·팁 2 | 절벽이 둘러싼 피피레섬의 만이에요. | 없음 |

**코사무이 (base, 4곳)**
| 순서 | id | 이름 | 종류 | 사실·팁 | 한국어 미리보기 | 빈 칸 |
|---|---|---|---|---|---|---|
| 29 | `wat-phra-yai-samui` | 코사무이 빅 부다 | heritage | 사실 5·팁 2 | 꼬판 섬에 모신 황금빛 큰 불상이에요. | 입장료 |
| 41 | `hin-ta-hin-yai` | 힌타 힌야이 | nature | 사실 5·팁 2 | 라마이 해변 남쪽 바닷가의 바위예요. | 주소 |
| 44 | `chaweng-beach` | 짜웽 해변 | sea_island | 사실 4·팁 2 | 코사무이에서 가장 긴 흰 모래 해변이에요. | 없음 |
| 45 | `bophut-beach` | 보풋 해변 | sea_island | 사실 5·팁 2 | 오래된 어촌 마을이 있는 조용한 해변이에요. | 없음 |

**1차 보류분에서 채운 곳 (기존 지역, 3곳)**
| 순서 | id | 지역 | 종류 | 사실·팁 | 한국어 미리보기 | 빈 칸 |
|---|---|---|---|---|---|---|
| 23 | `big-buddha` | th_phuket | heritage | 사실 5·팁 2 | 언덕 위에 모신 흰 대리석 큰 불상이에요. | 입장료·쉬는 요일 |
| 34 | `chiang-mai-night-bazaar` | th_chiangmai | market_street | 사실 2·팁 1 | 창클란로에 서는 치앙마이 밤 시장이에요. | 없음 |
| 47 | `wat-phra-yai-pattaya` | th_pattaya | heritage | 사실 4·팁 2 | 파타야 언덕에 있는 큰 불상 사원이에요. | 주소·입장료 |

- 한 곳당 크기 2,481~4,070B(상한 4,608B), 종류 분포 heritage 17·sea_island 9·city_view 7·nature 5·market_street 5·museum 4.
- 상세 최소 충실도(§5.2-13) 미달 0곳.
- 좌표: Wikidata 20곳, **OSM 3곳**(치앙라이 시계탑 `way/487637817`, 힌타 힌야이 `node/4703796578`, 파타야 빅 부다 `way/322626265`). `settings_credit_osm` 문자열은 이미 있어요.

### 순서(D11) 재산정
나라 전체 47곳을 ① 공식 지정 → ② 한국어 위키 조회수 → ③ 영어 조회수로 다시 매겼어요(Wikimedia REST pageviews, 2025-10~2026-09).
- 1~3위는 지정이 있는 곳 그대로(왓 포·아유타야·푸껫 올드타운).
- 한국어 문서가 있는 새 곳이 위로 올라왔어요: 피피섬 2,678 → 4위, 골든 트라이앵글 2,327 → 5위, 콰이강의 다리 1,774 → 7위, 도이 인타논 775 → 8위, 에라완 국립공원 181 → 12위.
- 영어만 있는 곳 가운데 왓 롱쿤 92,945가 가장 높아 18위, 헬파이어 패스 30,312 → 21위, 라일레이 28,606 → 22위, 푸껫 빅 부다 27,621 → 23위, 왓 탐수아 26,463 → 24위입니다.
- 한국어·영어 문서가 없는 11곳(37~47위)은 1차에 있던 3곳(매끌롱 기찻길 시장·파타야 해변·프롬텝 곶)을 그대로 두고, 새 곳을 그 뒤에 붙였어요.
- 피피섬의 Wikidata 한국어 sitelink는 '피피 군도'인데 조회수 데이터는 넘겨주기 제목 '피피섬'에만 있어요(2,678). 조회수는 '피피섬' 값을 썼어요.

### 아직 보류한 것과 이유
1. **님만해민(Q13015222)** — Wikidata에 좌표(P625)가 없고, TAT 공식 페이지(`tourismthailand.org/Shop/nimmanhaemin-road`)를 실제 브라우저로 열어 읽어 보니 **Fact 칸이 비어 있어요**(주소만 있음). 사실이 0개라 상세를 채울 수 없어 뺐어요. 넣으려면 치앙마이시 공식 안내나 OSM 좌표 + 사실 출처가 더 필요해요.
2. **아이콘시암** — 1차와 같은 이유(D14: 쇼핑 지구 정의 근거 없음)로 그대로 보류예요.
3. **왓 카오 프라밧·농눅 빌리지** — 1차 판단 그대로(Wikidata 항목 없음 / D19 제외).
4. **담넌사두억·해변들의 '정류장 이름 있는 access'** — 버스 운영사(transport.co.th)·railway.co.th 본 사이트가 이 PC에서 여전히 열리지 않아 1차 상태 그대로예요.
5. **공식 지정(designations)** — 도이 인타논·에라완은 국립공원 지정(왕실 포고)이 있지만 예술국·관보급 1차 출처를 확보하지 못해 `designations`를 비웠어요(1차의 도이수텝과 같은 기준). 확보하면 순서가 올라가요.

### 필요한 출처 표시 문자열
- **새로 필요한 것 없음.** 새 출처 44개는 모두 `use: facts`라 `attribution_required`가 아니고, OSM(`coords_osm`, ODbL-1.0)에 필요한 `settings_credit_osm`은 2차 결정 커밋(`b0e9dbf`)에서 이미 들어가 있어요.
- 1차에서 요청한 `settings_credit_unesco_576` · `settings_credit_unesco_mow_watpho` · `settings_credit_cnx_unesco_tl`은 그대로 필요해요.

### 사장님 결정이 필요한 것
1. **푸껫 빅 부다를 실었어요.** 2024-08 산사태 뒤 폐쇄 소식이 있었고 재개방을 알리는 **공식 공고는 못 찾았어요**. 다만 TAT 공식 관광지 페이지(tourismthailand.org)가 폐쇄 안내 없이 운영 정보를 그대로 싣고 있어 `status: open`으로 뒀어요. 더 엄격하게 가려면 뺐다가 사원·주 산림청 공고가 나온 뒤 넣는 게 맞아요.
2. **치앙라이 hub**: 역·터미널·선착장에 Wikidata 항목이 없어 **왓 롱쿤**을 기준점으로 썼어요. SPEC 대체 규칙을 글자대로 따르면 지역 1위인 **골든 트라이앵글(솝루악)** 이 기준점이 되는데, 시내에서 60km 떨어진 국경 지점이라 지역 기준점으로 맞지 않다고 봤어요. 대안: ⓐ 왓 롱수아텐을 기준점으로(골든 트라이앵글 54km로 여유) ⓑ 골든 트라이앵글을 별도 daytrip 지역(`th_goldentriangle`)으로 분리.
3. **골든 트라이앵글을 치앙라이에 둔 것**: hub에서 68km로 70km 실패선에 2km 남았어요. 좌표가 조금만 움직이면 서명이 막혀요. 2번 ⓐ나 ⓑ로 바꾸는 게 안전해요.
4. **헬파이어 패스가 칸차나부리 hub에서 71km**입니다(daytrip이라 경고만). 방콕에서 하루에 콰이강의 다리와 함께 보기는 멀어요. 별도 지역으로 쪼갤지 정해 주세요.
5. **짧은 현지명이 없는 2곳**(반담 박물관·치앙라이 시계탑): Wikidata 태국어 라벨과 공식 태국어 표기를 못 찾아 `no_local_short_ok`로 두고 **기사님 버튼을 숨겼어요**. LLM 음역은 금지라 비워 뒀어요.
6. **출처 편중(§5.2-14)**: 새 사실의 큰 부분이 관광체육부 Thailand Tourism Directory와 TAT 공식 페이지에서 왔어요(시설 자체 공식 사이트가 있는 곳은 헬파이어 패스뿐). 한 비공개 DB 30% 한도에 걸릴 수 있어 사장님 확인이 필요해요.
7. **왓 프라 야이(코사무이) 건립 연도**: 같은 TTD 페이지 안에서 1972년과 1982년이 엇갈려서 글에 연도를 쓰지 않았어요.
8. **마야 베이 계절 휴무**: 공식 목록(2023)에 '8월 1일~9월 30일'이 적혀 있지만 해마다 달라질 수 있어 날짜 숫자 없이 '늦여름에 몇 주 동안 닫아요 + 공고 확인'으로 썼어요(D4-A).
9. **왓 탐수아·반담 박물관·치앙라이 시계탑의 access**: 공식 출처에 정류장·노선 이름이 없어 1차 본보기(방빠인)처럼 '…일대·…길(차로 가요)' 문장형으로 썼어요.
10. **막힌 사이트**: 도이 인타논·에라완·마야 베이·아오낭 등의 `tourismthailand.org`와 DNP 포털(`portal.dnp.go.th`)은 자동 요청이 막혀서 **실제 브라우저로 사람처럼 열어 읽은 저장본**(`manual_browser_save`)을 썼어요(2차 결정 표 허용). CAPTCHA·로그인은 하지 않았어요.

---

아래는 1차 묶음별 상세 보고(에이전트 작업 메모)다.


---

# 태국 andaman 그룹(th_phuket) 큐레이션 보고 — 2026-10-09

- 조각 파일: `scratchpad/th/parts/andaman.json` (지역 1 + 관광지 4 + 출처 16)
- 근거: `~/.readyport/evidence/TH/<id>/N.txt` + `extract.json`, 지역 근거 `~/.readyport/evidence/TH/_regions/th_phuket/`
- spec: `scratchpad/th/spec/{patong-beach,wat-chalong,phuket-old-town,promthep-cape,_regions_th_phuket}.json`
- 검사: `python validate_part.py parts/andaman.json` → **결과: 통과**(설정 출처 표기 필요 0건). 경고는 조각이라 생기는 게이트 경고 3건, sea_island의 geo.kind=site 권장 경고 1건, facts.booking 없음 경고 2건(공식 예약 안내가 없어 비워 둠)
- 출처 id 접두사 `hkt_`. 모든 last_verified 2026-10-09, advisory 1단계(mofa_th, 2026-09-28)

## 지역 th_phuket (order 60, base, 묶음 '안다만해', airports ["HKT"], wave 1)
- **hub = 푸껫 올드타운 (Q17063772, 7.885714, 98.387558)**
  - 푸껫 버스터미널 1·2는 Wikidata 항목이 없다(검색어 'Phuket Bus Terminal', 'สถานีขนส่ง…ภูเก็ต', 시내 반경 15km SPARQL로 확인). 그래서 §3.1의 대체 규칙에 따라 관광지의 방문 지점을 썼다.
  - 근거(태국관광청 TAT Newsroom, 2026-10-09 확인, `_regions/th_phuket/2.txt`): 빠통행 스마트 버스 노선이 "Patong ↔ Phuket Bus Terminal 1"이고, 무료 셔틀 드래곤 라인은 "Free electric shuttle serving a circular route through Phuket Old Town."이며 정류장에 "Phuket Terminal 1"이 들어 있다. 즉 시내 버스 거점(터미널 1)과 올드타운이 같은 셔틀 노선 안에 있다. 운영사 시간표(`1.txt`)에도 "Bus Terminal 2 → Phuket Town → Patong Beach"가 나온다.
  - 주의: §3.1 대체 규칙의 원문은 'rank.order가 가장 높은 관광지의 방문 지점'이다. 순위는 코디네이터가 매기므로 올드타운이 1위가 아닐 수 있다 → 아래 '판단 필요'.
- hub와 관광지 직선거리(대략): 빠통 해변 약 10km, 왓 찰롱 약 7km, 프롬텝 곶 약 17km, 올드타운 0km → 모두 40km 안이다.
- **공항 HKT(Q240694, 8.113333, 98.316944)–hub 약 26km** → 60km 경고에 걸리지 않는다.
- **advisory_watch_ko = []**: SPEC §3.3 표에는 watch ["푸껫"]가 있지만, §4.3은 "도·주·섬 전체 이름처럼 다른 구역과 겹치는 이름은 넣지 않는다"고 정한다. '푸껫'은 주·섬·시 이름이 모두 같아서 기본값 []로 두었다. 지금 TH 팩 safety 문단에는 '푸껫'이 나오지 않는다(pack.json에서 확인, 공항 이름에만 있음). 남쪽 4개 주·딱 주·캄보디아 국경 50km와는 멀다.
- aliases_ko ["푸켓"]: 흔한 다른 표기를 검색에 쓰려고 넣었다.

## 관광지 (4곳)

### 1. patong-beach — 빠통 해변 (Q630024)
- 미리보기: "식당·상점 거리를 낀 안다만해의 긴 해변이에요." / "안다만해에 면한 해변으로, 길이 4km가 넘는 흰 모래사장에 야자수가 줄지어 있어요."
- 종류: sea_island, facts public_space (§2.2 해변).
- 사실 수: required_fact_fields 9 / extract 13줄 (claims 4, tips 4, access)
- 지정: 없음
- 접근: bus — TAT의 Phuket OneMap 안내에 나오는 푸껫 스마트 버스 빠통 해변 정류장(버스터미널 1 노선)과 빠통(정실론) 정류장(공항–라와이 노선)
- tips: TAT 남서 계절풍 해양 안전 안내(깃발 뜻·이안류). 태국 전체 대상 공지지만 푸껫을 직접 언급한다.
- 빠진 것: 주소(공공 해변), 빠통시 공식 안내(patongcity.go.th) — robots.txt에서 막혀 있음
- 출처: hkt_patong_ttd, hkt_tat_monsoon, hkt_tat_onemap

### 2. wat-chalong — 왓 찰롱 (Q461025)
- 미리보기: "찰롱에 있는 절로, 높이 60m 체디가 있어요." / "정식 이름은 왓 차이야타라람이고, 19세기 초에 세워진 절이에요."
- 종류: heritage. §2.2 정의(사원)와 판정표의 왓 아룬 줄을 따랐다. 국가 지정이 확인되지 않아 엄밀히는 판정 5(참배·관람 목적)다.
- 사실 수: 14 / extract 19줄
- facts: facility, **entry free + free_entry 태그**(사원 공식 FAQ 문구 "there is no charge to enter the temple"), regular_closed ["none"](FAQ "open daily"), booking 없음. 태그 dress_code.
- 주소: 공식 사이트 태국어 연락처 "70 หมู่ 6 ถนนเจ้าฟ้า (ตะวันตก) ฉลอง ภูเก็ต 83000"
- 접근: car_only — 관광 디렉터리(관광체육부 관광국)의 수라꾼 경기장→찰롱 오거리 길 안내. 이동 수단은 오토바이·자동차만 적혀 있다.
- 지정: 넣지 않음. Wikidata P1435(registered Thai historic site)만 있고, 예술국 등록이나 관보는 찾지 못했다 → 확인 필요
- 공식 사이트 판단: wat-chalong-phuket.com의 바닥글은 "© wat chalong temple"이다. 정부 관광 디렉터리(attraction/1996)도 이 곳의 Website를 `"Website":"www.wat-chalong-phuket.com"`로 적는다. 다만 투어 회사 배너 링크가 있다 → 판단 필요
- 출처: hkt_chalong_info, hkt_chalong_faq, hkt_chalong_contact_th, hkt_chalong_ttd

### 3. phuket-old-town — 푸껫 올드타운 (Q17063772)
- 미리보기: "시노-포르투갈풍 상점 주택이 늘어선 옛 시가지예요." / "주석을 찾아 복건 화교와 포르투갈 등 유럽 광산 회사가 들어오면서 중국·포르투갈 양식이 섞인 거리가 생겼어요."
- 종류: **heritage (판정 3)**. 자연환경정책계획실(ONEP) 공개 자료에 따르면 총리실 규정에 따라 지정된 '옛 도시 구역(เมืองเก่า)'이다. CSV 행: "27,ภูเก็ต,ภูเก็ต,ใต้,2,2.76,…", 면적 2.76㎢. 지정 날짜 표기(2자리 연도)는 해석이 모호해 글에 넣지 않았다. 대안은 city_view 옛 거리(판정 4) + public_space다.
- facts: facility, entry unknown, regular_closed ["unknown"], visit_note_ko(TAT "can be easily explored in a morning"). heritage는 public_space를 쓸 수 없다.
- 사실 수: 10 / extract 14줄
- 지정: `{"kind":"th_declared_old_town","name_ko":"태국 정부 지정 옛 도시 구역(เมืองเก่า) 푸껫","source":"hkt_onep_oldtowns"}` — 지침이 정한 두 가지(UNESCO·예술국)에 없는 새 kind다 → 판단 필요
- 이름: Wikidata에 th 라벨이 없어서 관광 디렉터리의 공식 태국어 이름을 local "ย่านการค้าเมืองเก่าภูเก็ต"로, 본문에 나오는 "ย่านเมืองเก่าภูเก็ต"을 local_short로 썼다. names.source = hkt_oldtown_ttd
- 접근: bus — 무료 셔틀 드래곤 라인(푸껫 터미널 1·타이후아 박물관·디북 거리·왓 몽콘니밋 정류장)
- 빠진 것: 주소, 일요 야시장의 여는 요일(시청 phuketcity.go.th가 robots.txt로 막혀 있어 근거 없음)
- 출처: hkt_onep_oldtown_csv, hkt_onep_oldtowns(지정), hkt_tat_oldtown, hkt_oldtown_ttd, hkt_tat_onemap

### 4. promthep-cape — 프롬텝 곶 (Q13026486) — 빅 부다 대신 넣음
- 미리보기: "푸껫섬 남쪽 끝에서 바다를 내려다보는 곶이에요." / "푸껫섬 남쪽 끝 곶으로, 오른쪽에 나이한 해변, 왼쪽에 나이야 해변이 보여요."
- 종류: city_view (판정 4, 전망 명소), public_space
- 사실 수: 8 / extract 13줄
- 접근: car_only — 관광 디렉터리의 길 안내(4021→4024→4233)
- 깐짜나피섹 등대 사실(1996=불기 2539, 높이 50m)은 관광 디렉터리의 태국어 글에서 가져왔다.
- 출처: hkt_promthep_ttd, hkt_tat_viewpoints

## 출처 관련 참고
- thailandtourismdirectory.go.th(관광체육부 관광국, OfficialOwner "กรมการท่องเที่ยว") 페이지는 내용이 `__NEXT_DATA__` JSON에만 있다. 그래서 코디네이터 도구 `fetch_ttd.py`로 저장했다(머리말 url = 사람이 보는 페이지, '키: 값' 줄). 쓴 스냅샷은 patong-beach/5.txt, wat-chalong/5.txt, phuket-old-town/6.txt, promthep-cape/3.txt다. 같은 폴더의 1.txt(같은 데이터를 `/_next/data/…json`으로 먼저 받은 것)는 extract에서 가리키지 않는 남은 파일이다. HasCost 같은 숫자 칸은 쓰지 않았다.
- §5.2-14: 빠통·올드타운·프롬텝은 운영자 공식 사이트가 없거나 막힌 공공장소라서 관광 디렉터리와 TAT Newsroom 비중이 크다. 시청 사이트(patongcity.go.th·phuketcity.go.th)는 robots.txt에서 막혀 있다.
- 막혀서 쓰지 않은 곳(우회하지 않음): tourismthailand.org 403, phuket.go.th 403, prd.go.th 기사 404
- settings_credit 필요: 없음(attribution_required 출처 없음)

## 다음 차수(뺀 곳)
- **빅 부다(Phuket Big Buddha, Q1548655)**
  - 지금 공개 상태를 공식 출처로 확인하지 못했다. 관광 디렉터리(attraction/97995) 데이터에는 `"IsOpen":0`이 있다. 2024-08 산사태 뒤 폐쇄되었고, 2026년 3월 재개방 소식은 민간 매체·여행 사이트에만 있다. 공식 사이트로 적힌 phuket-big-buddha.com은 운영 주체가 불분명한 SEO 블로그라 쓰지 않았다.
  - 다시 넣으려면 운영 주체(왓 끼띠상카람/왓 까따)나 주·산림청의 공식 재개방 안내가 필요하다. 넣는다면 risk post_disaster와 상태 30일 주기 확인이 필요하다.
  - 저장한 증거 `evidence/TH/big-buddha/1.txt`(빈 HTML), `2.txt`(데이터 JSON)는 남겨 두었고 extract는 없다.

## 사장님 판단이 필요한 것
1. hub: 버스터미널에 Wikidata 항목이 없어서 올드타운 좌표를 썼다. 순위 1위 관광지 방문 지점이라는 대체 규칙과 다를 수 있다.
2. advisory_watch_ko: SPEC 표의 ["푸껫"]과 §4.3 원칙이 충돌해 []로 두었다.
3. 올드타운 종류: heritage(ONEP 옛 도시 구역 지정)로 둘지, city_view(public_space)로 둘지. 새 지정 kind `th_declared_old_town`을 D11 '공식 지정'으로 칠지도 정해야 한다.
4. 왓 찰롱 공식 사이트(투어 배너 있음)의 무료 입장 문구로 free_entry를 붙여도 될지. 정부 디렉터리가 이 사이트를 이 곳의 웹사이트로 적고 있다.
5. 빅 부다를 4번째 곳에서 빼고 프롬텝 곶으로 바꾼 것.

---

# TH 조각 보고 — bangkok_more (2026-10-09)

맡은 일: th_bangkok의 추가 5곳(지역 정의는 코디네이터 몫이라 넣지 않음). 출처 id 접두사 `bkk_`.
결과: 5곳 실음(목표 5곳). 아이콘시암은 뺌(다음 차수 참고).
`python validate_part.py parts/bangkok_core.json parts/bangkok_more.json` → **결과: 통과** (게이트 경고는 조각이라 무시).

| id | 종류 | 크기 | 사실 필드 수(required_fact_fields) | extract 줄 | 알아 둘 것 줄 |
|---|---|---|---|---|---|
| jim-thompson-house | museum | 4,202B | 14 | 17 | 6 |
| mahanakhon-skywalk | city_view | 3,849B | 14 | 23 | 7 |
| chatuchak-weekend-market | market_street (public_space) | 3,376B | 10 | 14 | 4 |
| lumphini-park | city_view (public_space) | 2,566B | 7 | 8 | 3 |
| yaowarat | market_street (public_space) | 3,201B | 8 | 11 | 3 |

모두 thin=False, last_verified 2026-10-09(advisory만 2026-09-28), rank.order 1(임시), designations 없음.

## 출처를 고른 과정 (공통)
- tourismthailand.org: HTTP 403(봇 차단) → 우회하지 않음.
- bangkok.go.th / webportal.bangkok.go.th(BMA, 짜뚜짝 시장을 맡는 กองอำนวยการตลาดนัด 포함): Cloudflare 403 → 우회하지 않음.
- thailandtourismdirectory.go.th: 화면을 자바스크립트로만 그려서 fetch.py로 본문을 못 받음.
- chatuchakmarket.org(Wikidata P856): 운영 주체가 아니라 광고·제휴 링크(GetYourGuide)가 붙은 민간 안내 사이트라 쓰지 않음. bangkoktourist.com은 도메인이 도박 광고로 넘어가 있어 쓰지 않음.
- Lumphini의 Wikidata P856(203.155.220.217 BMA 공원과 옛 서버)은 접속 끊김.
- 그래서 시설 공식 사이트(짐 톰슨 재단, 킹 파워 마하나콘)를 먼저 쓰고, 공공장소는 **태국 정부 운영 am2026thailand.go.th**(페이지 끝 "This website is managed by the Government of Thailand.")와 **MRT 운영사 BEM(metro.bemplc.co.th) 'Around MRT / Popular Attractions'** 페이지를 썼다.

---

## 1. jim-thompson-house — 짐 톰슨의 집 (Q2916351)
- 미리보기: summary "실크 사업가 짐 톰슨이 살던 티크 목조 가옥 박물관이에요." / body 첫 문장 "'태국 실크왕'으로 알려진 제임스 톰슨의 방콕 집과 아시아 미술 소장품을 보여 줘요."
- 종류 판정: museum — 판정 2(공식 명칭 "The Jim Thompson House Museum").
- 사실: claims 6(실크왕·소장품, 아유타야 티크 가옥 6채 1959년 재조립, 동남아 소장품, 1967년 실종, 같은 부지 아트센터, 5개 언어 가이드 동반), tips 4(가이드 동반, 신발 벗기·큰 가방 보관, 사진 규칙, '오늘 휴관' 사칭 주의), tag stairs(공식 "several stairways; there are no elevators"), facts paid / 매일 / 예약 불필요("No reservation required"), access BTS 국립경기장역 1번 출구, address_local(태국어, am2026 /th/).
- 지정: 없음.
- 빠진 것: 센샙 운하 보트(후아창 선착장) 경로는 am2026에만 있어 access 출처를 하나로 맞추려고 넣지 않음. dress_code는 am2026에만 'Modest attire'라 태그 안 붙임.
- 출처: bkk_jt_home https://jimthompsonhouse.org/ · bkk_jt_visit https://jimthompsonhouse.org/visitor-information/ · bkk_am2026_jimthompson(…/en/explore-bangkok/jim-thompson-house-museum) · bkk_am2026_jimthompson_th(…/th/…)
- 참고: jimthompsonhouse.com은 인증서가 맞지 않아 .org(현재 공식)를 씀.

## 2. mahanakhon-skywalk — 마하나콘 스카이워크 (Q1640197, 건물 항목)
- 미리보기: summary "킹 파워 마하나콘 빌딩 꼭대기의 전망대예요." / body 첫 문장 "74층 실내 전망대와 78층 옥상 전망대를 함께 둘러봐요."
- 종류 판정: city_view — 판정표 1 예외(전망이 주 상품인 스카이워크, 표에 고정 예).
- 사실: claims 4(78층 '더 피크' 314m, 50초 안 74층·360도, 유리 바닥 63㎡·310m, 올레 스헤렌 설계·픽셀 나선 외벽), tips 4(반입 금지 물품, 유리 바닥 신발 덮개·소지품, 택시용 태국어 길 안내 카드, 1층 매표소), tags indoor·night(자정까지)·step_free("wheelchair friendly building"), facts paid / 매일 / 예약 none(1층 매표소), access BTS 총논시역 3번 출구, address_local(태국어 공식 /th/location).
- 지정: 없음.
- 판단 메모: 'Thailand's highest'·'fastest' 같은 최상급은 인용에만 있고 글에는 쓰지 않음. I-Tilt·SkyRides·TukTuk Quest(VR 놀이)는 D13에 따라 글에 넣지 않음.
- 출처: bkk_mnk_skywalk(/experience/mahanakhon-skywalk) · bkk_mnk_tower(/about-us/mahanakhon-tower) · bkk_mnk_location(/location) · bkk_mnk_location_th(/th/location). (contact 페이지 4.txt는 저장만 하고 인용 안 함)

## 3. chatuchak-weekend-market — 짜뚜짝 주말시장 (Q1068311)
- 미리보기: summary "토·일요일에 열리는 방콕의 큰 주말 시장이에요." / body 첫 문장 "1982년 방콕 200주년 기념 행사 때 사남루앙에서 이곳으로 옮겨 온 시장이에요."
- 종류 판정: market_street public_space — 판정 3.5(표에 고정 예 '짜뚜짝 시장').
- 사실: claims 4(1982년 사남루앙에서 이전, 구역 구성, 수·목 식물 시장·금 야시장, 토·일 운영+'JJ Market'), tips 3(미로라 배치도 사진, 더위 피하는 시간대, 옷·모자·신발·물), facts public_space + regular_closed [mon~fri] + closed_note_ko "금요일 오후에는 일부 구역만 열어요."(근거: am2026 "Saturday–Sunday … (some zones open Friday afternoon)"), access MRT 깜팽펫역 2번·BTS 모칫역 1번·버스, address_local "แขวงจตุจักร เขตจตุจักร กรุงเทพฯ"(구·동까지만).
- 지정: 없음.
- 주의: am2026은 시작을 "1938년 정부 계획"이라 하고, BEM은 "1982년 이전"이라 함 — 서로 다른 사건이라 글에는 BEM의 1982년 이전만 씀.
- 출처: bkk_am2026_chatuchak · bkk_am2026_chatuchak_th · bkk_mrt_chatuchak(BEM pid=120)

## 4. lumphini-park — 룸피니 공원 (Q977437)
- 미리보기: summary "방콕 도심에 있는 큰 나무 그늘의 공원이에요." / body 첫 문장 "2025년에 100년을 맞은 방콕 도심 공원이에요."
- 종류 판정: **city_view(도시 정원) public_space** — SPEC 2.2 city_view '도시 정원' / nature는 '사람이 지은 랜드마크 정원 제외'. 사람이 만든 도심 공원이라 판정 4(전망·도시 명소 쪽)·5(쓰는 시간의 이유가 도심 휴식·운동)로 city_view. nature가 낫다고 보시면 category만 바꾸면 됨.
- 사실: claims 4(2025년 100년, 나무 그늘·휴식·행사, 해 지기 전후 운동, 아침 시장), tips 2(아침 시장, MRT 실롬역 1번 출구 쪽 입구), access MRT 룸피니역 3번 출구.
- 지정: 없음. **확인 필요**: Wikidata P1435 = "Thai historic site to be considered for registration"(Q122834559) — 예술국 근거를 못 찾아 넣지 않음.
- 빠진 것: address_local 없음(BMA 페이지 차단). 공식 출처가 MRT 운영사 안내 페이지뿐이라 사실이 얇음 — BMA 접속이 되면 보강 권장. BEM pid=100의 'the first public park in Thailand'는 인용에만 있고 글에 '최초'는 쓰지 않음. 같은 페이지의 "a distance of 2.2 kms"는 어색해서 쓰지 않음.
- 출처: bkk_mrt_lumphini(BEM pid=100) · bkk_mrt_lumphini_old(BEM pid=32)

## 5. yaowarat — 야오와랏 (차이나타운) (Q1518029 Yaowarat Road)
- 미리보기: summary "금 거래와 길거리 음식으로 알려진 방콕 차이나타운이에요." / body 첫 문장 "야오와랏은 방콕의 차이나타운으로, 금 거래로 알려진 지역이에요."
- 종류 판정: market_street public_space — 판정 3.5(먹거리·상점이 늘어선 거리가 주 목적).
- 사실: claims 4(차이나타운·금 거래·탕토깡 금방 박물관, 왓 망꼰 까말라왓, 길거리 음식 낮·밤, 삼펭 정착지·도소매), tips 2(왓망꼰역에서 야오와랏 거리 가는 길, 왓 망꼰 가는 길), tag night(BEM "you can eat all day and night!"), access MRT 왓망꼰역 1번 출구. mentions_ko: 삼펭·왓 망꼰 까말라왓·탕토깡 금방 박물관.
- 지정: 없음.
- 판단 메모: 'largest China Town in Thailand'는 인용에만 두고 글에 '가장'은 쓰지 않음. 좌표는 거리 항목 Q1518029(Chinatown 항목 Q34053037은 kowiki 없음, 좌표가 넓은 지역 중심). night 태그 근거가 운영사 홍보 문구라 약하면 빼도 됨.
- 빠진 것: address_local 없음(거리). BMA·TAT 차단으로 지자체 출처 없음.
- 출처: bkk_mrt_yaowarat(pid=158) · bkk_mrt_sampheng(pid=137) · bkk_mrt_watmangkon(pid=163)

---

## 다음 차수
- **아이콘시암(ICONSIAM, Q20585880)**: D14에 따라 '쇼핑 지구'의 별칭으로만 실을 수 있는데, 아이콘시암을 포함하는 쇼핑 지구(예: 짜런나컨 강변)를 공식 출처로 정의한 근거를 찾지 못함(TAT 403, BMA 차단). 몰 단독 항목은 D14 위반이라 뺌.

## 사장님 판단이 필요한 것
1. **am2026thailand.go.th를 사실 출처로 써도 되는지**: 태국 정부가 운영하는 *.go.th(IMF·세계은행 총회 개최 포털)이지만, 페이지 끝에 Lonely Planet 등 참고 목록이 있는 편집 기사형이다. 짜뚜짝의 운영 요일·교통·팁, 짐 톰슨의 연혁(1959·1967)이 이 포털 근거다.
2. **BEM(MRT 운영사) 'Popular Attractions' 페이지를 사실 출처로**: 교통 운영사 공식이라 access에는 맞지만, 룸피니·야오와랏은 claims까지 이 페이지에 기대고 있다(BMA·TAT가 막혀서). 얇다고 보시면 두 곳을 다음 차수로 미룰 수 있다.
3. 룸피니 공원 종류: city_view(현재) 또는 nature.
4. 마하나콘 이름: Wikidata 항목은 건물(King Power Mahanakhon)이라 names.en을 'Mahanakhon SkyWalk'(방문 대상)로, 건물 이름은 aliases_en에 둠. local은 Wikidata th 라벨 "คิง เพาเวอร์ มหานคร"에서 보이지 않는 글자(U+200B)를 뺀 것, local_short는 th 별칭 "ตึกมหานคร".
5. 짜뚜짝 regular_closed를 [mon~fri]로 두고 금요일 오후 일부 구역은 closed_note_ko로 적음(수·목 식물 시장·금 야시장은 본문에). 화면에 '월~금 쉼'으로 보이는 게 괜찮은지.

## settings_credit
- 이 조각의 출처는 모두 `use: facts`라 attribution_required 없음 → 새로 필요한 settings_credit 없음.

---

# TH 그룹 central 큐레이션 보고 (2026-10-09)

산출: `parts/central.json`(지역 2 + 곳 6 + 출처 23), 근거 `~/.readyport/evidence/TH/<id>/N.txt + extract.json`, `spec/<id>.json`, 지역 근거 `~/.readyport/evidence/TH/_regions/th_maeklong/`(1~5.txt + extract.json, spec `spec/_regions_th_maeklong.json`). 빌드 스크립트 `central_build.py`.
검사: `python validate_part.py parts/bangkok_core.json parts/central.json` → **결과: 통과** (게이트 경고는 조각이라 무시: 곳 수·종류 수).

## 지역

### th_maeklong — 담넌사두억·매끌롱 (order 30, daytrip [th_bangkok], 방콕권)
- note_ko: '방콕에서 하루 다녀오기도 해요'. aliases_ko ["매끌롱"].
- hub: **매끌롱역** Q6633481 (13.4075, 99.9983) — 방콕에서 기차로 오는 여행자의 도착 거점이고, 매끌롱 기찻길 시장이 역 바로 옆이에요. 담넌사두억 수상시장은 이 hub에서 약 13km(daytrip이라 거리 lint 대상 아님).
- **당일 왕복 근거**(확인일 2026-10-09, `_regions/th_maeklong/extract.json`, field `regions.th_maeklong.kind[daytrip]`):
  - 철도청 SRT 시간표 반램–매끌롱선(매끌롱행) https://ttsview.railway.co.th/SRT_Schedule2022.php?ln=en&line=6&trip=1 — 인용 "Mae Klong Arr. 9381 9383 9385 9387 07:30 10:10 13:30 16:40", 도착 "08:30 11:10 14:30 17:40".
  - 같은 노선 매끌롱발 (trip=2) — 인용 "Ban Laem Arr. 9380 9382 9384 9386 06:20 09:00 11:30 15:30".
  - 웡위안야이–마하차이선 (line=5 trip=1/2) — 웡위안야이 출발 "05:30 06:25 07:00 07:40 08:35"…, 마하차이 출발 저녁 편 "16:00 16:30 17:35 18:10 19:00".
  - TAT Newsroom(2020-08-26) https://www.tatnews.org/2020/08/samut-songkhrams-small-size-belies-its-rich-attractions/ — "take the train from Thon Buri Railway Station, change once in Mahachai and take another train directly into the Mae Klong Railway Market".
  - 판단: 웡위안야이 아침 편 → 마하차이(강 건너 반램) → 07:30 반램발 08:30 매끌롱 도착, 귀로 15:30 매끌롱발 → 마하차이 저녁 편으로 방콕 복귀 가능 = 당일 왕복. (railway.co.th 본 사이트는 이 PC에서 접속 안 됨, 시간표 서버 ttsview만 사용. 마하차이–반램 사이 강 건너기(나룻배)는 공식 근거를 못 찾아 근거 줄에 넣지 않았어요.)

### th_pattaya — 파타야 (order 40, base, 동부 해안, airports [])
- hub: **파타야 해변** Q49321024 (12.931944, 100.879444). 여행자가 실제로 내리는 북파타야 버스터미널(진리의 성전 공식 안내의 'Rung Rueng Bus Terminal')은 Wikidata 항목이 없고, 파타야역(Q13021757)은 하루 운행이 적어 실제 출발 거점으로 보기 어려워서 SPEC §3.1 대체 규칙(가장 대표적인 관광지 방문 지점)을 썼어요. → **판단 필요 1**.
- hub–관광지 거리: 진리의 성전 약 4.6km, 좀티엔 약 5.6km, 프라땀낙 약 1.8km — 모두 40km 안.
- 경보: 1단계. 캄보디아 국경(뜨랏 쪽)과 200km 이상 떨어져 있어요.

## 곳 (6곳)

| id | 지역 | 종류(판정) | 크기 | required 사실 필드 / extract 줄 |
|---|---|---|---|---|
| damnoen-saduak-floating-market | th_maeklong | market_street, public_space (판정 3.5) | 2,988B | 8 / 12 |
| maeklong-railway-market | th_maeklong | market_street, public_space (판정 3.5) | 2,930B | 8 / 12 |
| sanctuary-of-truth | th_pattaya | museum (판정 2) | 3,501B | 14 / 24 |
| pattaya-beach | th_pattaya | sea_island, public_space | 2,790B | 7 / 13 |
| jomtien-beach | th_pattaya | sea_island, public_space | 2,538B | 6 / 11 |
| pratumnak-hill | th_pattaya | city_view, public_space (판정 4, 전망 명소) | 2,565B | 6 / 8 |

### 담넌사두억 수상시장 (Q13014948)
- 미리보기: "운하를 따라 배 위에서 물건을 파는 수상시장이에요." / "라마 4세의 뜻으로 판 담넌사두억 운하에 선 시장이에요. …"
- 판정: 3.5(상점·노점이 늘어선 것 자체가 목적인 시장). 노천 운하 시장이라 public_space.
- 출처: cen_ttd_1501(관광체육부 TTD), cen_tat_ratchaburi(TAT Newsroom 2020).
- 주소: TTD 태국어 주소. 지정: 없음.
- 빠진 것·주의: access는 TTD의 교통수단(버스·자동차·배)만 있고 **정류장 이름이 없어** nearest_ko를 '담넌사두억 운하 시장 일대(방콕에서 버스·자동차로 가요)'로 썼어요(본보기 bang-pa-in처럼 문장형). 방콕 남부터미널(사이따이마이) 노선은 공식 근거를 못 찾았어요. 정기 휴무 없음은 public_space라 안 넣음. names.local은 Wikidata th 라벨.

### 매끌롱 기찻길 시장 (Q112117516)
- 미리보기: "기차가 오면 노점이 차양을 접는 철길 시장이에요." / "매끌롱역 바로 옆 선로 양쪽에 노점이 늘어선 시장이에요."
- 판정: 3.5. 출처: cen_ttd_104647(TTD), cen_tat_gems(TAT 2017), cen_tat_maeklong_2018(TAT 2018, 내무부 설명), cen_tat_samutsongkhram(TAT 2020).
- access: train, '매끌롱역 바로 옆'(TTD). 주소: TTD Address.th 'สถานีรถไฟแม่กลอง'(역 이름이라 주소로는 약함).
- 주의: **Wikidata에 th 라벨이 없어** names.local/local_short는 TTD 공식 이름 'ตลาดร่มหุบ'를 썼어요(names.source는 'wd' 그대로) → **판단 필요 2**. TTD 항목의 Official 칸이 0(관광청 직접 검수 아님)이에요. '하루 8번 통과'는 TTD 문구이고 SRT 시간표(도착 4·출발 4편)와 맞아요. '60~70년'은 2018년 내무부 설명이라 연도를 붙였어요.

### 진리의 성전 (Q262459)
- 미리보기: "나무로 짓고 지금도 조각을 이어 가는 박물관이에요." / "1981년에 짓기 시작했고, 지금도 조각과 공사가 이어지고 있어요."
- 판정: **museum, 판정 2** — 운영 주체 공식 이름이 'Sanctuary of Truth Museum' / 'พิพิธภัณฑ์ปราสาทสัจธรรม'. 국가 지정 문화재가 아니라 heritage가 아님.
- 출처: 시설 공식 사이트 pty_sot_home·visit·visit_th·faq·founder + pty_ttd_1698(공사 진행·안전모 1건).
- 태그: dress_code(Please dress modestly), night(야간 투어 일정), step_free(FAQ: 엘리베이터·휠체어·경사로). facts: paid(요금표), 매일 운영, booking none(당일 매표소 구매 가능).
- access: 방콕–파타야 버스 → 룽르앙 버스터미널(북파타야) → 돌핀 로터리 → 나끌루아 소이 12(공식 Visit Us). 주소: 공식 태국어 페이지.
- D19: 시설 안에 코끼리 타기·먹이 주기, 미니 동물원이 있지만 방문 이유의 중심이 아니라 포함하고 글에는 넣지 않았어요(규칙 (가)). 지정: 없음.

### 파타야 해변 (Q49321024)
- 미리보기: "파타야 시내 앞을 따라 길게 이어진 해변이에요." / "북파타야에서 남파타야까지 약 3km 이어지는 해변이에요."
- 출처: pty_ttd_1691(TTD), pty_tat_zoning(TAT Newsroom 2015, TAT 파타야 사무소 발표 구역 규정).
- access: 걷기·버스(TTD), nearest_ko '파타야 시내 해변 도로(북파타야~남파타야)' — 정류장 이름 없음.
- 주의: Wikidata th 라벨 없음 → names.local 'หาดพัทยา'는 TTD 공식 이름(판단 필요 2와 같음). TTD 안에서도 길이가 3km(Detail)·6km(AreaSize)로 엇갈려 Detail의 '약 3km'만 썼어요(TAT 2015는 2.9km). 2015 구역 규정의 '수요일 상인 휴무'는 오래된 정보라 넣지 않았어요. geo.kind site(경고만).

### 좀티엔 해변 (Q2423544)
- 미리보기: "파타야 남쪽에 길게 뻗은 조용한 해변이에요." / "파타야 시내에서 남쪽으로 약 4km 떨어진, 길이 약 6km의 해변이에요."
- 출처: pty_ttd_1688, pty_tat_zoning. access: 버스(TTD), 정류장 이름 없음. geo.kind site(경고만).

### 프라땀낙 언덕 (Q106856322)
- 미리보기: "파타야 시내와 파타야만이 내려다보이는 언덕이에요." / "남파타야와 좀티엔 해변 사이에 있는 작은 언덕이에요."
- 판정: city_view(판정 4, 전망 명소). 꼭대기 사원(왓 카오 프라밧)은 별도 항목이 아니라 mentions_ko.
- 출처: pty_ttd_1687, pty_tat_fireworks(TAT 2018 — 불꽃 축제 조망 지점 안내).
- access: car_only(TTD 교통수단이 오토바이·자동차뿐), '프라땀낙 길'(TTD Road).
- 주의: Wikidata th 라벨 없음 → local 'เขาพระตำหนัก'는 TTD 공식 이름에서. photo_link의 Commons P18은 언덕 위 'Pattaya City' 글자 간판 사진이에요.

## 출처 비율 (§5.2-14 참고)
- 사실 필드 49개 가운데 TTD(관광체육부 DB) 출처가 27개(55%)예요. 시설 공식 사이트가 있는 곳은 진리의 성전뿐이고, 해변·시장·언덕은 공식 운영 주체 사이트가 없거나 막혀 있어요(라차부리·사뭇송크람 도청은 Cloudflare 403, 파타야시 사이트에는 관광지 페이지 없음, tourismthailand.org는 403). 나라 전체 30% 한도는 코디네이터가 합친 뒤 확인해 주세요.

## 다음 차수 (뺀 곳과 사유)
- **왓 프라 야이(빅 부다, 파타야)**: TTD 21350에 공식 정보가 있지만 **Wikidata 항목(QID·P625)을 찾지 못해** 좌표를 정할 수 없어 뺐어요. TTD 교통수단도 오토바이·자동차뿐이에요.
- **왓 카오 프라밧**: Wikidata 항목 없음. 프라땀낙 언덕 mentions로만 다뤘어요.
- **농눅 빌리지**: 판정표상 제외(D19 (나)).
- 담넌사두억 수상시장·해변들의 **정류장 이름 있는 access**: 버스 운영사(transport.co.th)와 railway.co.th 본 사이트가 이 PC에서 접속되지 않아 다음 차수에 보완.

## settings_credit
- 이 조각에는 attribution_required 출처가 없어요(UNESCO 출처 없음). 필요 없음.

## 사장님 판단 필요
1. **th_pattaya hub**: 버스터미널에 Wikidata 항목이 없어 '파타야 해변'(관광지 방문 지점)을 hub로 썼어요. 파타야역(Q13021757, 12.9402/100.9093)으로 바꿀 수도 있어요.
2. **names.local 출처**: 매끌롱 기찻길 시장·파타야 해변·프라땀낙 언덕은 Wikidata th 라벨이 없어 관광체육부 TTD 공식 이름을 넣었어요(SPEC은 'Wikidata 라벨이나 공식 표기' 허용). names.source가 'wd'인 채라 표기를 바꿀지 결정이 필요해요.
3. **nearest_ko 문장형**: 정류장 이름이 없는 곳(담넌사두억·해변 2곳·프라땀낙)은 본보기(bang-pa-in)처럼 '…일대·…길' 문장으로 썼어요. 더 엄격하게 하려면 이 4곳은 다음 차수로 미뤄야 해요.
4. **TAT Newsroom 오래된 기사**(2015·2017·2018·2020)를 근거로 썼어요. 연도가 드러나는 내용(구역 규정·60~70년)은 글에 연도를 붙였어요.

---

# TH north 조각 보고서 (치앙마이, 2026-10-09)

산출: `parts/north.json`, evidence `~/.readyport/evidence/TH/{doi-suthep,wat-chedi-luang,wat-phra-singh,tha-phae-gate,_regions/th_chiangmai}/`, spec `spec/{doi-suthep,wat-chedi-luang,wat-phra-singh,tha-phae-gate,_regions_th_chiangmai}.json`.
검사: `python validate_part.py parts/north.json` → **결과: 통과** (경고는 booking 키 없음·조각이라 생기는 게이트 경고뿐).

## 지역 th_chiangmai
- order 50, base, group '북부', airports [] (CNX는 팩에 없음), advisory 1(mofa_th, 2026-09-28), advisory_watch_ko [], wave 1.
- **hub = 치앙마이역 (Q1884773, 18.78365, 99.01688)**. 근거: 국립공원청 도이수텝-뿌이 국립공원 페이지(https://portal.dnp.go.th/Content/nationalpark?contentId=914, 2026-10-09 확인)가 방콕–치앙마이 열차 도착지로 치앙마이역을 들고, 역에 대기하는 차량으로 국립공원까지 간다고 적음 — 인용 "เมื่อเดินทางมาถึงสถานีรถไฟ เชียงใหม่ สามารถเรียกบริการรถยนต์โดยสารที่จอดอยู่ประจำสถานีรถไฟเชียงใหม่ …". 같은 페이지에 치앙마이 버스터미널(아케이드)도 나오지만 Wikidata 항목·좌표가 없어 역을 골랐음. evidence `_regions/th_chiangmai/1.txt` + extract.json(3줄).
- hub–관광지 직선거리: 도이수텝 10.3km, 왓 체디루앙 3.2km, 왓 프라싱 3.8km, 타패문 2.5km (모두 40km 안). 딱 주·캄보디아 국경과 멀어 1단계.

## 곳별

### 1. doi-suthep — 왓 프라탓 도이수텝 (Q1517698) · heritage · 3,691B
- 미리보기: "도이수텝산 위에 부처 사리를 모신 사원이에요." / "므앙라이 왕조의 쿠에나 왕이 수코타이에서 온 부처 사리를 모시려고 지은 사원이에요."
- 판정: 판정표 §2.3 '도이수텝 = heritage (+stairs/+mountain_view)' (5). 태그 stairs·mountain_view는 TAT Newsroom 문구("A staircase of over 300 steps", "situated on Doi Suthep mountain").
- 사실: required_fact_fields 12 / extract 18줄. claims 5, tips 3, tags 2, facts(매일 — TTD 여는 요일 7일 등록), access.
- access: car_only — "사원 주차장(자가용·버스), 또는 크루바 스리위차이 기념비·치앙마이 동물원 앞에서 차량 대절"(국립공원청).
- 지정: 없음. Wikidata P1435 '등록 고대 유적'만 있고 예술국·관보 근거를 못 찾음 → **확인 필요**.
- 빠진 것: address_local(TTD 주소가 '9 หมู่ 9 ศรีวิชัย'로 불완전해 비움), entry(공식 요금 근거 없음 → unknown), booking, dress_code(공식 문구 못 찾음).
- 주의: 건립 연도가 출처마다 다름(UNESCO 잠정목록 1419년 vs TAT '14세기 말') → 글에 연도를 쓰지 않음. 고도도 TTD 1,053m vs TAT 1,056m라 쓰지 않음. 케이블카는 운영사 근거가 없어 cable_car 태그 없이 팁으로만.
- 출처: cnx_unesco_tl, cnx_tat_cm2021, cnx_ttd_doisuthep, cnx_dnp_suthep.

### 2. wat-chedi-luang — 왓 체디루앙 (Q1454288) · heritage · 2,916B
- 미리보기: "구시가 한가운데 큰 체디가 서 있는 절이에요." / "므앙라이 왕조 7대 쌘므앙마 왕 때 지은 절이에요."
- 판정: 사원(heritage, 3/5). 
- 사실: 8 / extract 17줄. claims 5, tips 1, facts(매일), access.
- access: walk_from_center + bus — "구시가 한가운데 쁘라뽁끌라오 길 103(걸어서), 정기 노선버스"(TTD: "built in the heart of Chiang Mai", 주소, 이동수단 Bus). **정류장 이름은 공식 출처에 없음**.
- 지정: 없음(P1435만 → 확인 필요). UNESCO 잠정목록 신청서 구성 유적(claim c5, 지정 아님).
- 빠진 것: address_local, entry, booking, dress_code. 건립 연도 출처 불일치(UNESCO 1411 vs TTD '연도 미상·1391 추정') → 글에 연도 안 씀.
- 출처: cnx_ttd_chediluang, cnx_unesco_tl.

### 3. wat-phra-singh — 왓 프라싱 (Q1657130) · heritage · 2,916B
- 미리보기: "프라싱 불상을 모신 치앙마이 구시가의 절이에요." / "1345~1355년 므앙라이 왕조의 파유 왕이 지은 절이에요."
- 판정: 사원(heritage, 3/5). 브리프 목록에 없던 곳이지만 나이트 바자·님만해민을 못 싣게 되어 대신 넣음(공식 근거 충분).
- 사실: 8 / extract 8줄. claims 5, tips 1, facts(TTD "Every day"), access.
- access: walk_from_center — "타패문에서 랏차담넌 길을 따라 끝까지"(TTD 타패 워킹 스트리트 주소 "Starting from Tha Phae Gate Long to Ratchadamnoen Road until Wat Phra Singh").
- 지정: 없음(P1435만 → 확인 필요).
- 출처: cnx_unesco_tl, cnx_ttd_phrasingh, cnx_ttd_walkingstreet.

### 4. tha-phae-gate — 타패문 (Q13016585) · city_view (public_space) · 3,146B
- 미리보기: "치앙마이 구시가 동쪽에 있는 성문이에요." / "1296년 망라이 왕이 세운 치앙마이 성곽의 다섯 문 가운데 동쪽 문이에요."
- 판정: **city_view 4(랜드마크·옛 성문)**. heritage(3)는 국가 지정·등재 근거가 있어야 하는데, 치앙마이 성곽은 UNESCO **잠정목록**(2015)일 뿐이고 예술국 등록 근거를 못 찾아 city_view로 둠. 근거가 나오면 heritage로 바꿀 수 있음.
- '올드시티'는 문과 같은 지점이 아니라서 aliases가 아닌 mentions_ko(치앙마이 올드시티·구시가·일요 워킹 스트리트·랏차담넌 길)에 넣음.
- 사실: 8 / extract 12줄. claims 5(UNESCO 4 + TTD 1), tips 2(일요 거리 장 차량 통제·파는 물건), access.
- access: walk_from_center + bus — "랏차담넌 길이 시작되는 곳(걸어서), 주(州) 노선버스"(TTD "You can travel by provincial bus.").
- official_url: UNESCO 잠정목록 페이지(성문 설명이 있는 공식 근거).
- 출처: cnx_unesco_tl, cnx_ttd_walkingstreet.

## 다음 차수 (뺀 곳)
- **치앙마이 나이트 바자**(Q5095199): TAT Newsroom에 이름·파는 물건은 나오지만 가는 법(정류장·노선) 공식 근거를 못 찾음. thailand.go.th 공항버스 안내는 Incapsula 봇 차단, tourismthailand.org 403 → 우회하지 않음. TTD에도 항목 없음.
- **님만해민**(Q13015222): Wikidata에 좌표(P625)가 없고, 공식 근거 페이지도 못 찾음.
- (참고) 일요 워킹 스트리트(TTD 5283)는 Wikidata 항목이 없어 따로 싣지 않고 타패문의 claim·tips·mentions로 담음.

## settings_credit
- `settings_credit_cnx_unesco_tl` 필요. 제안 문구: "치앙마이 성곽·사원 사실 일부: UNESCO 세계유산센터 잠정목록(Monuments, Sites and Cultural Landscape of Chiang Mai, Capital of Lanna), CC BY-SA IGO 3.0"

## 사장님 판단이 필요한 것
1. **old city 접근(access)**: 구시가 세 곳(체디루앙·프라싱·타패문)은 공식 출처에 정류장 이름이 없어 '걸어서(walk_from_center)'와 위치 문장(길 이름)으로 채움. 이 정도로 충분한지.
2. **타패문 종류**: city_view(현재) vs heritage. 치앙마이 세계유산 등재 신청(ICOMOS 현지 조사 2026-08, 결정은 2027 예상 — 뉴스 기준이라 데이터엔 안 씀)이 통과하면 heritage+unesco로 바꾸는 게 맞음.
3. **지정(D11 순서)**: 세 사원 모두 Wikidata P1435 '등록 고대 유적'이 있으나 예술국·관보 근거를 못 찾아 designations를 비움. 예술국 근거를 확보하면 순서가 올라감.
4. **출처 편중(§5.2-14)**: 이 묶음 사실의 큰 부분이 Thailand Tourism Directory(관광체육부 DB)와 UNESCO 잠정목록에서 옴. 시설 자체 공식 사이트(사원)는 찾지 못함(접속 불가·없음). 나라 전체 30% 한도는 코디네이터가 합친 뒤 확인 필요.
5. 원래 목표의 나이트 바자·님만해민 대신 왓 프라싱을 넣음(곳 수 4, 지역 최소 3 충족). heritage 3/4라 나라 전체에서 종류 비율 확인 필요.
