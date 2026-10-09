# CLAUDE.md — 레디포트(ReadyPort)

## 제품 요약 (PRD 1장)

- 해외여행 준비부터 귀국까지 함께하는 '여행 필수품' Android 앱(Kotlin, Jetpack Compose). 무료, 광고 없음, 수익은 '제휴' 표시 링크로만.
- 입국 사전신고를 앱이 대신 채우고, 사용자는 확인과 **최종 제출만** 한다.
- 핵심 대상: 해외여행이 처음인 중학생·중년 이상. 설명 없이 쓸 수 있어야 한다.
- 핵심 약속: ① 여권 등 개인정보는 폰 밖으로 나가지 않는다 ② 인터넷 없이도 핵심 기능 동작 ③ 공식 출처와 최종 확인일을 항상 표시.
- 서버: Firebase 무료(Spark)만. 서버 로직 없음. 정책·양식·콘텐츠 변경 감지와 반영은 운영자 PC의 ARIA가 담당.
- 서비스 원칙: 한 화면 할 일 하나 / 중1 수준 쉬운 말 / 앱이 채우고 사람은 확인 / 오프라인 동작 / 현지어는 보여주고 들려주는 용 / 공식 출처·확인일 / 개인정보는 기기 안에.

## 문서 지도

| 문서 | 내용 |
|---|---|
| `docs/PRD.md` | 1~8장(제품·범위·사용자·IA·화면·입국서류 흐름·보안·정책), 11장(추가 서비스), 부록 A·B |
| `docs/ARCHITECTURE.md` | 9~10장(기술 스택, Firebase 제약, 데이터 형식) |
| `docs/ARIA_OPS.md` | 12장(ARIA 운영 연동, `ops/aria/`) |
| `docs/ROADMAP.md` | 13~14장(마일스톤·완료 기준, 사람이 할 일) |
| `docs/QUESTIONS.md` | 운영자에게 묻는 `[확인 필요]`·결정 사항 목록과 답변 기록 |
| `docs/source/` | 원본 지시서 (수정 금지, 참고용) |

## 빌드 (이 PC)

`java`/`gradle`이 PATH에 없다. PowerShell에서:

```powershell
$env:JAVA_HOME='E:\_PROGRAM_Installed\Android Studio\jbr'
$env:ANDROID_HOME='E:\_PROGRAM_Installed\Android_SDK'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

- 라이브러리 버전은 `gradle/libs.versions.toml`에서만 관리한다.
- AGP 9: Kotlin 내장이라 `org.jetbrains.kotlin.android` 플러그인을 쓰지 않는다. compileSdk 37(최신 AndroidX 요구), targetSdk 36.
- 단위 테스트는 Robolectric(SDK 36)으로 Compose 화면까지 JVM에서 돈다. `ScreenCaptureTest`가 `app/build/screenshots/`에 화면 PNG를 남긴다(기기 없이 디자인 확인).
- PowerShell `Get-Content`/`Set-Content`로 소스를 고치지 말 것 — UTF-8 한글이 깨진다. Edit/Write 도구를 쓴다.

## Firebase

- 프로젝트 `readyport-app` (Spark, 운영자 Google 계정 — DoingWell과 같은 계정이지만 별도 프로젝트). `firebase` CLI 로그인됨.
- `app/google-services.json`은 비밀이 아니라서 저장소에 둔다(보호는 Firestore 규칙·App Check로). 서비스 계정 키·서명 비밀키는 절대 저장소에 두지 않는다.
- Analytics는 넣지 않는다(운영자 결정 2026-09-28).

## 국가 팩

- 원본은 `packs/src/`, 스키마는 `packs/schema/`. 고친 뒤: `python tools/packs/build_packs.py --kid rp-2026-1 --key ~/.readyport/keys/pack_signing_rp-2026-1.pem` → 내장본(assets) 갱신 → `firebase deploy --only hosting` → Remote Config `pack_version_{CC}` 올리기.
- **Remote Config 템플릿 전체 배포(`firebase deploy --only remoteconfig`)는 ARIA가 돌기 시작하면 쓰지 않는다** — ARIA가 켠 `kill_autofill_*`·`stale_banner`를 되돌린다. 버전 포인터는 `python tools/deploy/ci_deploy.py rc-versions`(버전 키만 바꿈)로. main 머지 후에는 `deploy-packs.yml`이 한다.
- Firestore 규칙: `firebase/firestore.rules`, 테스트 `cd tools/firestore && npm test`(에뮬레이터). 배포 `firebase deploy --only firestore:rules`.
- ARIA 모듈: `ops/aria/` (README 참고), 테스트 `python -m unittest discover -s ops/aria/tests -t .`
- 팩 버전은 `yyyy.MM.dd-N`. 모든 정책·연락처 항목에 `source`·`last_verified`. 확인 안 된 값은 넣지 않는다(빌드가 `[확인 필요]`를 막는다).
- Bash heredoc은 백슬래시를 한 겹 벗긴다. 이스케이프·윈도 경로가 든 편집은 Edit/Write 도구나 파일로 쓴 스크립트로 한다.
- 설계 결정은 docs/ARCHITECTURE.md 끝 "구현 결정 기록".

## 관광지

- 설계: `docs/design/attractions/SPEC_v5.md` + 사장님 결정 `DECISIONS_2026-10-09.md`(다르면 결정 문서 우선. D15-B: 여행경보 2단계 지역도 싣고 주의 띠).
- 파일: 작업본 `packs/drafts/<CC>/attractions.json`(release=draft, 지역마다 wave) → 원본 `packs/src/<CC>/attractions.json` → 서명본 `app/src/main/assets/packs/<CC>/attractions.json(+.sig)`(커밋). 스키마·허용 값 `packs/schema/attractions.*.json`, 게이트 `packs/curation/gates.json`, 근거 기록 `packs/curation/<CC>.curation.json`.
- 서명 키는 **관광지 전용** `rp-att-2026-1`(`~/.readyport/keys/attractions_signing_rp-att-2026-1.pem`, 저장소 금지). 앱 공개키는 `PackKeys.ATTRACTIONS`.
  예외(사장님 결정 2026-10-09): 주간 자동 갱신 서명용으로 GitHub secrets `ATTRACTIONS_SIGNING_KEY_PEM` 에만 둔다(`attractions-auto.yml`). ARIA 에는 주지 않는다.
- 주간 자동 갱신·평점·여행 계획 요청(ARIA): `docs/ARIA_OPS.md` 12.9~12.12. 자동 PR 은 `aria/attractions-*` 브랜치 + 라벨 `attractions-auto`, 사실 칸만(`tools/attractions/auto_update_guard.py`).
- 사실마다 인용: `~/.readyport/evidence/<CC>/<id>/extract.json`(+스냅샷 .txt), 글 대조 캐시 `~/.readyport/copycheck_cache/<CC>/<id>/*.txt`(첫 줄 JSON 메타). 둘 다 저장소 밖.
- 명령: `python tools/attractions/build_attractions.py check` → `promote <CC> --wave 1 --kid rp-att-2026-1 --key ~/.readyport/keys/attractions_signing_rp-att-2026-1.pem` → 커밋·PR → 배포 직전 `python tools/deploy/stage_hosting.py`. 긴급 제외는 `retire <CC> --ids … --reason safety`. 도구 테스트 `python -m unittest tools/attractions/test_build_attractions.py tools/deploy/test_stage_hosting.py`.
- debug 빌드는 서명본이 없는 나라에 `app/src/debug/assets/attractions_samples/<CC>.json`(샘플, 서명 없음)을 보인다. release는 '곧 추가돼요'.

## 자동 입력

- 엔진 `app/src/main/assets/autofill/engine.js`(앱 내장), 레시피 `packs/src/recipes/`. 엔진 테스트: `cd tools/autofill && npm test` (가짜 화면만).
- 확인 안 된 선택 목록·달력 칸은 `widget: "assist"`. 선택지 글자를 추측해서 넣지 않는다.
- 사이트 사람 확인(Turnstile·캡차)은 절대 우회·대신 풀기 금지. 구조 확인이 필요하면 공개 코드 분석이나 운영자가 직접 통과한 화면에서 읽기만.

---

## 작업 규칙 (지시서 15장 원문)

1. **정부 사이트 제출 금지**: 테스트·개발 중 어떤 경우에도 실제 정부 사이트에 제출하지 않는다. 자동화 테스트는 필드 존재·선택자 확인까지만 하고 제출 버튼 앞에서 멈춘다. 가짜 데이터로 제출하는 것은 허위 신고다.
2. **캡차 우회 금지**, 접근성 API로 다른 앱 조작 금지, 서버 헤드리스 브라우저로 개인정보 처리 금지.
3. **PII 금지 구역**: 로그, Crashlytics, Analytics, Firestore, 저장소, 이슈·PR 설명에 여권·예약·개인 정보를 넣지 않는다.
4. **테스트 데이터**: ICAO 9303 표본(가상 국가) 같은 가짜 데이터만 쓴다. 실제 여권 이미지를 저장소에 넣지 않는다.
5. **레시피는 선언형 JSON만**. 원격 JavaScript 실행 코드 금지. 서명 검증 없는 팩 적용 금지.
6. **사실을 지어내지 않는다**: 비자 규칙, 수수료, 제출 기한, 긴급번호, 딥링크 형식, 선택자 등 확인되지 않은 값은 `[확인 필요]`로 남기고 운영자에게 질문한다. 모든 정책 데이터에 `source`와 `last_verified`를 채운다.
7. **콘텐츠 복제 금지**: 외부 글·이미지·노선도·상품 사진을 복제하지 않는다. CC BY-SA 자료는 출처를 표기한다.
8. **쉬운 말 우선**: UI 문구는 3.4절 규칙을 따른다. 새 전문 용어를 쓰기 전에 쉬운 말을 먼저 정한다.
9. **새 의존성**: 네트워크를 쓰는 라이브러리나 SDK를 추가하기 전에 이유와 수집 데이터를 설명하고 승인을 받는다.
10. **무료 한도 준수**: Cloud Storage·Cloud Functions를 쓰지 않는다. Firebase로 이미지를 서비스하지 않는다.
11. **제휴 규칙**: 제휴 링크에는 항상 '제휴' 표시. 보험·금융 상품에는 제휴 링크를 넣지 않는다. 수수료로 추천 순서를 바꾸지 않는다.
12. **안전한 방향 우선**: 자동 입력이 불확실하면 수동 모드로 떨어뜨린다. 정책 의미가 바뀌는 변경은 사람 승인 없이 게시하지 않는다.
13. **작업 방식**: 마일스톤 단위로 작업하고, 끝날 때마다 완료 기준 충족 여부와 남은 `[확인 필요]` 목록을 보고한다. PR 설명에는 변경 이유와 출처를 적는다.
14. **헤드리스 실행 시**(ARIA가 호출): 지시받은 국가·양식 범위만 수정하고, 스키마 검증을 통과한 경우에만 `claude/` 브랜치로 PR을 만든다. main에 직접 푸시하지 않는다.

## 저장소
- 원격: https://github.com/inhyeuk/readyport (공개). **main은 보호됨 — 직접 푸시 불가(관리자 포함)**. 모든 변경은 `claude/<주제>` 브랜치 → PR → 머지.
- 이 저장소는 `credential.namespace=readyport`로 자격증명을 따로 쓴다(ARIA가 쓰는 기존 GitHub 토큰은 workflow 권한이 없고, 건드리지 않는다).

