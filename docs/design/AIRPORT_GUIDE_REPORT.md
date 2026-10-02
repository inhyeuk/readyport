# 공항에 도착하면 — 작업 기록 (2026-10-03)

운영자 요청(원문): "각 국가별 공항에서 입국 신고하는 위치를 안내해 줄 수 있으면, 정보를 추가해 주면 좋겠어"

브랜치 `claude/airport-guide`(main 0271e6d = 0.4.0 기준). 앱 버전은 올리지 않았다. 태국 데이터만 이 작업에서 넣었고, 나머지 8개 나라는 따로 조사한 `<CC>.json`을 `merge_airports.py`로 합친다.

## 1. 사용자가 보는 것

| 화면 | 무엇이 바뀌었나 |
|---|---|
| 나라 › 입국·비자 | 입국 카드 카드 바로 다음 `공항에 도착하면` 카드: 공항 칩(수완나품·돈므앙·푸껫) → 공항 머리(이름, `방콕 · BKK · Suvarnabhumi Airport`) → 번호 단계(내리기·입국 심사·검역·짐·세관·나가기, 단계마다 아이콘·공식 위치 칩·짧은 설명) → 입국 심사 단계 안 노란 줄 `TDAC를 내고 받은 접수 확인 메일을 입국 심사관에게 보여 줘야 해요.` → `공항 안내도 열기 (공식)` → 출처(태국공항공사 AOT 도착 절차 + 외교부 해외안전여행) |
| 여행 만들기·고치기 | `내리는 공항` 칩(그 나라 팩 공항 + `아직 몰라요`). 공항 안내가 없는 나라는 칸이 없다 |
| 내 여행 › 출국하는 날 | `도착하면 이 순서예요`(짧은 공항 카드 — 단계 제목·위치·입국 카드 줄) + `공항 순서 자세히 보기` |
| 내 여행 › 도착 | 공항을 골랐으면 `도착했어요! 이 순서대로 해요`가 그 공항 순서(팩) → 유심·환전·숙소(앱 안내)로 번호가 이어진다. 안 골랐으면 예전 순서 + `공항별 도착 순서 보기` |
| 체크리스트 › 도착하면 | `도착 공항 순서 보기`(팩에 공항이 있을 때만): 고른 공항 칩 또는 고르는 곳 안내 + `공항 순서 보기`(나라 화면 공항 묶음으로 바로 내려감) + 출처 |

## 2. 태국 데이터 (packs/src/TH/pack.json, 팩 2026.10.03-1)

모든 문장은 아래 공식 페이지를 2026-10-03에 직접 열어 짧은 해요체로 옮겼다. 층·홀은 공식 페이지에 적힌 것만.

| 공항 | 순서 | 위치(공식 원문) | 자동 심사대(한국 여권) | 출처 |
|---|---|---|---|---|
| BKK 수완나품 | 내리기 → 입국 심사(우선 줄: 70세 이상·임신부·아기·몸이 불편한 사람) → 검역 → 짐(국제선 5~23번) → 세관(초록·빨강 줄) → 나가기(택시·Grab·리무진·버스·공항철도·렌터카) | 짐 찾기 `2층 도착 홀`(2nd Floor, Arrival Hall) | `null` — 숨김 | AOT Arrival Procedure (Main Terminal) |
| DMK 돈므앙 | 내리기 → 검역(감염병) → 입국 심사(우선 줄) → 짐(국제선 1~6번) → 세관(식물·동물·식품 검사 뒤 초록·빨강) → 나가기(미터 택시·Grab·리무진·셔틀 A1~A4) | 짐 찾기 `1터미널 1층`(Level 1, Terminal 1) | `null` | AOT Arrival Procedure DMK |
| HKT 푸껫 | 내리기 → 입국 심사(우선 줄) → 검역 → 짐(국제선 1~5번) → 세관 → 나가기 | 짐·세관 `국제선 터미널 1층` | `null` | AOT Arrival Procedure HKT |

- **TDAC를 어디서 보여 주나**: 세 공항 모두 입국 심사 단계 — 외교부 해외안전여행 태국 페이지 원문 "접수 확인 메일을 태국 입국 시 입국심사관에게 제시하여야 함"(출처 `mofa_th`, `form_check_source`).
- **자동 심사대(e-gate)를 null로 둔 이유**: AOT 수완나품 도착 절차 페이지(2026-10-03 열람)는 입국 자동 심사대(ABC)를 "태국·싱가포르·홍콩 여권"으로만 적는다. 한편 2026-08 태국 정부가 한국 등 31개국으로 넓힌다고 발표했다는 보도(Khaosod·TAT News·Time Out 등)가 있으나, 한국 여권을 밝힌 이민국·AOT 공식 페이지는 열지 못했다(주태국 대사관 게시판은 열리지 않음). 규칙대로 `null` — 앱은 줄을 그리지 않는다. 이민국·AOT가 한국 포함을 공식 게시하면 `egate_kr: true` + `egate_note_ko`(첫 입국은 직원 창구에서 생체 정보 등록 등 공식 조건)로 바꾼다.
- 공식 안내도: `https://<공항>.airportthai.co.th/airport-map` (AOT 공식).
- 같은 데이터: 스크래치 `airports/TH.json`(브리프 모양 + `form_check_source`).

## 3. 데이터 모양·검사

- 팩 `airports[]`(선택): ARCHITECTURE 10.4c. 브리프 모양 그대로 + 선택 필드 `form_check_source`(입국 카드 줄만 다른 출처일 때, 없으면 공항 `source`). 다른 나라 조사 파일에 없어도 된다.
- `build_packs.py check_airports`: 공항·단계·입국 카드 줄 출처가 sources에 있는지, IATA 코드 겹침, kind 9종, map_url https, last_verified 실제 날짜, 단계 3~7개, egate_kr 값 종류. 스키마도 같은 것을 본다(공항 없는 팩 통과).
- 앱 모델 `Airport`/`AirportStep`(`PackModels.kt`), `CountryPack.airports = []` 기본 — 예전 팩·예전 앱 모두 그대로.
- 여행 `Trip.arrivalAirport: String?`(IATA 코드만, 기본 null, `encodeDefaults=false`라 예전 저장본과 같은 모양). 저장 때 그 나라 팩에 있는 코드만 남긴다.
- 색인 체크리스트 틀 `airport_steps`(arrival, `from: airports`, `action: open_airport`), 색인 2026.10.03-1.

## 4. 다른 8개 나라 합치기

```
python tools/packs/merge_airports.py --dir <scratchpad>/airports --check        # 검증만
python tools/packs/merge_airports.py --dir <scratchpad>/airports                # 폴더 안 모든 <CC>.json
python tools/packs/merge_airports.py --dir <scratchpad>/airports JP SG          # 고른 나라만
python tools/packs/build_packs.py --kid rp-2026-1 --key ~/.readyport/keys/pack_signing_rp-2026-1.pem
```
- airports는 통째로 바꾸고(다시 돌려도 같은 결과), sources는 id로 겹침 없이 더한다. 같은 id인데 url이 다르면 그 나라는 쓰지 않고 멈춘다(다른 id를 쓸 것).
- 바뀐 나라만 팩 version을 올린다(같은 날이면 `-N+1`).
- 합친 뒤 앱 테스트에서 바뀌는 숫자: `ChecklistTest`의 나라별 항목 수는 `airportItem(cc)`로 자동 반영. 갤러리·접근성은 새 공항 카드까지 자동으로 본다.
- 확인한 것: TH로 `--check` → 합치기 → 다시 합치기(바뀐 것 없음).

## 5. 검증

- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 녹색: 단위 테스트 435개 실패 0(접근성 점검 6벌 — 393dp 100%·200%, sdk31 200%, 360dp 100%·200%, 영어 — 과 갤러리 캡처 포함), lint 오류 0·경고 11.
- 새 테스트: `AirportGuideTest`(나라 화면 공항 묶음 위치·고르기·위치 칩·입국 카드 줄·안내도 링크·여행 공항 기본값·바로 내려가기, 오늘 도착·출국 카드, 여행 고치기 공항 고르기), `AirportPackTest`(서명된 TH 팩·예전 팩·출처 기본값), `ChecklistTest`(공항 항목·고른 공항·저장 모양), `tools/packs/test_build_packs.py`(airports 스키마·검사 + merge_airports 19개 모두 통과).
- 갤러리(basic·easy·sdk31 200%): `03_country-entry-TH`(공항 묶음), `17_today-departure`, `19_today-arrival`(수완나품), `20_today-arrival-no-airport`, `24_trip-edit`(내리는 공항), `29_trip-checklist-arrival`. 모두 읽어 보고 다듬었다: 단계 설명을 제목 시작선에 맞춤(아이콘 열 아님), 공항 카드 배지를 LocalAirport로(바로 아래 `들어갈 때`와 같은 그림이 겹치지 않게), 벨트 번호를 `5번부터 23번까지`로(숫자 굵게가 둘 다 걸리게), 다른 화면으로 가는 길은 글자 버튼, 여행 고치기는 칩 대신 폭 전체 선택 카드(이름 + `방콕 · BKK`).

## 6. 남은 것

- 태국 자동 심사대: 공식 게시 확인 뒤 `egate_kr` 갱신(위 2절).
- 8개 나라 데이터 합치기(통합 담당).
- 배포: main 머지 뒤 `deploy-packs.yml`(TH 팩·색인 버전 포인터). 이 작업에서는 올리지 않았다.
