## v4 → v5 변경 기록 (5라운드 통합, 최종판)

### 콘텐츠(8.3) 지적: 9건 모두 수용
1. [major] 경보 해시가 생활 안내 문장까지 포함: 수용. `advisory_sha256`은 `id=="safety"` 첫 섹션의 경보 문단만 대상으로 계산한다(정규식은 `packs/schema/advisory_rules.json`에 두고 Python·Kotlin이 함께 씀). 문단 수 TW1·SG1·VN1·JP1·CN2·TH3·MY2·ID2·PH4는 코드로 직접 세어 확인했다. 'VN 날치기 문단만 바꿈 → 해시 불변' 테스트를 더했다. 그래서 P0b 팩 표기 정리 PR이 거짓 경고를 만들지 않는다.
2. [major] Wikidata 참조 세탁: 수용. 유효 참조를 정의했다(P248이 비위키미디어 항목을 가리키거나 P854가 비위키 도메인일 때만 유효. P143·P4656·P887·P3452만 있으면 무효). CSV 열을 추가하고 claims ⑥과 테스트도 넣었다.
3. [major] PH D15-A면 공개 불가: 수용. D15 질문에 '약 8곳뿐이라 공개 기준에 못 미침'이라는 결과를 적었다. C안(PH만 8곳 기준)은 `gates.json` 나라별 덮어쓰기로 구현한다. 막탄을 `ph_mactan`으로 분리했고, 보홀 낮춤 예외는 본섬 자치시 목록으로 좁혔으며(팡라오는 근거와 PR이 있어야 함), CEB 매핑은 갈래별로 정했다. PH 게이트 픽스처는 실제 후보의 '구조(지역별 곳 수·단계)'만 따르고 이름은 가짜로 둔다. 실제 이름을 쓰지 않는 것은 v4의 픽스처 원칙을 지키기 위해서다(부분 조정).
4. [major] VN·JP 1차 구성: 하롱을 daytrip [vn_hanoi]로 바꾼 것은 기술(데이터 모델) 판단이라 그대로 반영했다. 선솜·티톱은 mentions로 둔다. 나트랑을 1차로 올리는 것과 오키나와 여부는 범위·공수 문제라 사장님 결정 D25로 넘겼다. 기본 구현은 A(팩에 공항이 있는 지역은 1차)이고, '1차 선정 근거' 열도 추가했다.
5. [minor] 0404 원문 기준 구역: 수용. 원문 URL·날짜·지명 목록을 필수로 하고 1:1 대조하게 했다. ID 기본값은 1로 확정했다(팩 문장으로 확인). 파푸아 6개 주·말루쿠·북말루쿠·아체는 2단계다. MY는 동부해안(2)과 동부 섬(3)으로 나눴다. JP에는 피난 지시 시정촌을 보수적으로 추가했다. 구역에 name_ko·aliases_ko를 더해 검색 안내에도 재사용한다.
6. [minor] 검증 병목: 인용 대조(`--verify-quotes`)는 결정과 관계없이 기술 기본값으로 넣었다. 공수표는 곳당 60~75분, 이후 45~55분으로 고쳤다. 검증 방식은 D23으로 사장님께 여쭌다.
7. [minor] hub 정의: 수용. hub는 교통 거점의 Wikidata 항목으로 정하고, 'hub 선택 근거' 열을 두었다.
8. [minor] 라이선스 세부: 수용. OSM은 비실질 추출 범위(50개 넘으면 경고)로 쓰고, 정부 데이터는 license와 attribution_required로 출처를 표기하며, 한 DB에서 30% 넘게 가져오지 않는다(저작권법 제93조).
9. [minor] 현지어 폴백·TH 긴 정식명: 수용. 언어 코드 폴백을 고정하고 간·번체 판별 경고를 넣었다. TH는 local_short가 필수이고, 없으면 기사님 버튼을 숨긴다.
10. [minor] 예스진지에 진과스가 없음: 수용. tw_north_coast 1차를 4곳으로 했고, 묶음 별칭의 구성 지명 lint를 넣었다.
11. [minor] 상세가 얇음: 수용. 상세 최소 충실도 규칙(미달 10% 넘으면 실패)을 넣고, 시범 5곳은 D24로 여쭌다.

### UX(8.3) 지적: 9건 모두 수용(충돌 1건 조정)
1. [major] imePadding이 겹쳐 빈 띠가 생김: 수용. ReadyPortRoot NavHost에 `consumeWindowInsets(inner)`를 넣었다(267행 확인). imeBottom의 정의를 고치고, 예산은 356dp 기준으로 다시 계산했다. 오프라인 + 키보드 경우와 인셋 단위 테스트도 추가했다.
2. [major] 칩이 머리 예산을 깸: 수용. 2열에도 '걸러 보기' 접힘 버튼을 쓰고, 보이는 종류가 3개 이하일 때만 펼쳐 둔다(결정적 규칙). 기본 머리 예산은 실제 구성으로 다시 계산해 240dp에서 260dp로 고쳤다. 240은 안내 두 줄이 들어가면 지킬 수 없다.
3. [major] 한 번만 쓰는 라우트 인자가 반복 실행됨: 수용. SavedStateHandle 소비 플래그와 remove를 쓰고 테스트를 넣었다.
4. [major] 2차·경보 제외 지역 검색이 '다른 말로'로 끝남: 수용. `upcoming_regions`·`excluded_areas`(선택 필드, schema 1 유지), 도착 공항 준비 중 Info 줄을 넣었다. 1차 선정 규칙은 D25로 넘겼다.
5. [major] '안전 정보 보기'가 안전 카드에 닿지 않음: 앞부분은 수용했다. `CountryRoute.focusSection="safety"`(focusAirports 선례, 실제 코드 확인)와 pop/push 규칙을 넣었다. **충돌 조정**: UX안은 '돌아갈 때 flushDeferred 호출'이었지만, 아키텍처 지적을 받아들여 notify 미루기 장치 자체를 지웠으므로 flushDeferred도 없앴다. 나라 화면이 언제나 최신 pack을 보여 주고, 경보 안내는 기기 pack이 더 새로울 때만 뜨기 때문에 두 화면의 문장이 같다. UX가 의도한 결과는 그대로 이뤄진다.
6. [minor] NavTile이 private: 수용. internal로 열고 TileGrid·NavTile(row)로 구현하며, 새 부품은 0개다.
7. [minor] sticky와 TalkBack: 수용. 터치 탐색 중에는 sticky를 끄고 A11y 테스트를 추가했다.
8. [minor] 같은 지역 고르는 규칙: 수용. rank 상위 2곳(자기 제외)이고, 0곳이면 '모두 보기'만 둔다.

### 아키텍처(8.1) 지적: 11건 모두 수용
1. [major] 긴급 retire가 막힘: 수용. `retire` 명령을 만들어 게이트·copycheck·날짜 검사를 건너뛰게 했다. copycheck 결과는 해시로 커밋한다(`<CC>.copycheck.json`). evidence와 캐시는 ARIA 백업에 넣는다. 0곳이면 앱은 NotYet으로 처리한다.
2. [major] setExpedited와 FGS: 수용. minSdk 26 확인. expedited를 쓰지 않고 CONNECTED 고유 작업만 둔다. ENQUEUED가 30초를 넘으면 문구를 바꾼다. 소스·매니페스트 테스트를 넣었다.
3. [major] notify 미루기가 실제로 지켜지지 않음: 수용. `update(notify)`·`flushDeferred`·onCleared 훅을 지우고, 버전 방향 판정((a) 기기 팩 < basis면 안내 없음, (c) 기기 팩 > basis이고 해시가 다를 때만 안내)으로 바꿨다. PackRepository API는 바꾸지 않는다.
4. [major] 게이트의 앱 테스트를 Python이 실행할 수 없음: 수용. 게이트에서 AttractionsFirstScreenTest를 빼고, CI에 CommittedAttractionsContractTest를 넣었다. enums·경보 규칙은 JSON 단일 출처로 두고, 최악 조건 픽스처를 쓴다.
5. [minor] 키 범위: 수용. 관광지 전용 kid rp-att-2026-1, 암호 PEM, 시크릿 제외, PackKeys를 DocKind별로 분리, kid 검사. 누가 실행하는지는 D22로 여쭌다.
6. [minor] 공백 제거 별칭: 수용(v4 콘텐츠 규칙을 뒤집음). 정규화가 이미 처리하므로 게이트와 lint에서 지우고, 반대로 '중복 별칭' 경고와 '왓아룬→title 100' 테스트를 넣었다.
7. [minor] NFD·소문자 길이: 수용. NFC lint(서명 실패), 글자 단위 Locale.ROOT 소문자화, 질의만 전체 NFC.
8. [minor] Spark 하루 한도: 수용. 실행당 3키 상한, 버전 날짜로 7일 판정, 나라별 공개 간격.
9. [minor] debug Hilt 중복: 수용. AttractionsFallback을 debug·release 소스셋에 같은 이름으로 둔다(AppCheckInstaller 선례).
10. [minor] 캐시 누적: 수용. 바이트 캐시와 모델 LRU 3.
11. [minor] safety 섹션 식별·merged 순서: 수용. `id=="safety"` 첫 항목, 공용 해시 벡터, Place → 찜 순서의 멱등 트랜잭션, migrated_version.

### 그 밖에 정리한 것
- 실제 코드로 다시 확인했다: minSdk 26/targetSdk 36, ReadyPortRoot NavHost `padding(inner)`만 있음, CountryRoute.focusAirports 있음, 9개국 경보 문단 수, ID 기본 1단계 문장.
- 단계표에 MVP 정의(일본 1차 공개까지)와 단계별 인수 조건을 넣었다. 시범 P2a를 나눴고, P1 공수는 16~19일, P0a는 4~5일로 고쳤다.
- §15 '결정별 변경 범위' 표를 새로 만들었다.
- 거절한 지적은 없다. 부분 조정은 2건이다: PH 픽스처의 실제 이름은 쓰지 않고 구조만 따르며, 머리 예산은 240에서 260dp로 현실화했다.