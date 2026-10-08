# 공지사항·앱 푸시 운영 안내 (2026-10-08)

> 운영자 요청: *"D.well App과 같이 App 시작시에 공지사항을 띄우는 기능을 추가해줘. 그리고 App Push를 할 수 있는 기능도 추가해줘."*
> 추가 요청: *"공지에 App에 대한 간략한 설명과, 절대적으로 개인정보는 모바일에만 저장되어 유출 가능성은 전혀없다는 두 가지 내용을 카드 뉴스 형태로 만들어서 넣어줘"* → 첫 공지 `about-readyport`(아래 6절).
> 설계 기록: `docs/ARCHITECTURE.md` '구현 결정 기록 (공지사항·앱 푸시)', 화면: `docs/design/DESIGN_SPEC_2026-10-01.md` 부록 K, 작업 보고: `docs/design/NOTICES_PUSH_REPORT.md`.

## 1. 한눈에

```
notices/notices.json (PR로 고친다, 서명 없음)
   │  main 머지
   ▼
GitHub Actions notices.yml ── 검증(tools/notices/build_notices.py check) ── 서명(팩과 같은 Ed25519 키, kid rp-2026-1)
   │                                                                          │
   │  (그림이 바뀌었으면 팩을 다시 만든 뒤 Hosting 배포)                          ▼
   │                                                          Firestore notices/current = {payload, sig, generated_at}
   │                                                                          │  앱이 get (규칙: 문서 하나 읽기만)
   ▼                                                                          ▼
손으로 부르기(notice.py push --send) ── FCM 토픽 메시지 {type:notice, id, v, cat} ──▶ 앱이 서명본을 받아 **그 글로** 알린다
```

- **글은 서명된 데이터에만 있다.** Firestore 콘솔에서 글을 고치면 서명이 맞지 않아 앱이 보여 주지 않는다. 고치는 길은 PR 하나뿐이다.
- **알림 메시지에는 글이 없다.** 앱은 공지 id만 읽고, 제목·본문은 서명된 공지에서 꺼낸다. 서명본을 못 받으면 앱에 든 문구 `새 소식이 있어요`(누르면 공지사항 목록).
- **토큰을 저장하지 않는다.** 보내는 곳은 토픽뿐: `notice_all`(공지 알림을 켠 기기), `notice_promo`(광고성 소식에 동의한 기기), 나라별은 `'notice_all' in topics && 'country_TH' in topics` 같은 조건.
- 비밀키는 저장소에 없다: 운영자 PC `~/.readyport/keys/` 또는 GitHub secret `PACK_SIGNING_KEY_PEM`. 서비스 계정은 secret `FIREBASE_SERVICE_ACCOUNT`.

## 2. 공지 쓰기 → 올리기

1. 틀 만들기: `python tools/notices/notice.py new --id winter-tips --type normal` (광고면 `--type event --promo`). `notices/notices.json`에 **꺼진**(`"active": false`) 공지가 하나 붙는다.
2. 글 고치기 — 칸(스키마 `notices/schema/notices.schema.json`):

| 칸 | 뜻 |
|---|---|
| `id` | 바꾸지 않는 이름(영문 소문자·숫자·`-`·`_` 3~41자) |
| `version` | 내용을 크게 고치면 올린다 → '다시 보지 않기'를 누른 사람에게도 다시 보인다 |
| `active` | `false`면 서명본에 넣지 않는다(초안) |
| `type` | `urgent` 긴급(빨강, 마지막 쪽까지 봐야 닫힘, 앱을 켤 때마다 뜸) · `normal` 일반(파랑) · `event` 이벤트(주황) · `guide` 이용 안내(초록, **한 번 보면 끝**) |
| `category` | `service`(기본) · `promo` 광고성 정보 → 제목이 `(광고)`로 시작해야 하고, 동의한 사람에게만 뜨고 알린다(5절) |
| `title_ko` · `body_ko` · `more_pages_ko[]` | 제목(40자) · 첫 쪽 글(500자) · 다음 쪽 글. 그냥 글자만(HTML 안 됨) |
| `images[]` | `{url, alt_ko}` — 그림 k는 k번째 쪽 맨 위. 주소는 `https://readyport-app.web.app/notices/…png|webp|jpg`만, 파일은 `hosting/public/notices/`에 300KB 이하, `alt_ko`(TalkBack이 읽는 글 — 그림 속 글을 모두)는 꼭 |
| `link` | `{label_ko, url}` 마지막 쪽 버튼. 우리 Hosting·`play.google.com`·`github.com/inhyeuk/readyport`만 |
| `start` · `end` | 시간대가 붙은 시각(`2026-12-01T09:00:00+09:00`). `end`가 없으면 끝나지 않는다 |
| `priority` | 0~1000, 높을수록 먼저 |
| `min_version_code` · `max_version_code` | 이 앱 버전에만 |
| `audience` | `["all"]` 또는 `["TH","JP"]` — 그 나라를 여행으로 만들었거나 찜한 기기에서만(판단은 기기 안에서만) |

3. 확인: `python tools/notices/notice.py check` (오류·경고 + 상태 표: 꺼짐/예약됨/보이는 중/끝남). 그림은 `python tools/notices/pack_images.py <png…>`로 줄여 `hosting/public/notices/`에 넣는다.
4. `active`를 `true`로 → `claude/<주제>` 브랜치 → PR → 머지. 머지되면 notices.yml이 서명해 `notices/current`에 올린다(그림이 바뀌었으면 팩을 다시 서명한 뒤 Hosting도 배포 — Hosting 배포는 사이트 전체를 바꾸므로).
5. 끝난 공지는 30일 동안 `지난 공지`로 목록에 남고 그 뒤 서명본에서 빠진다. 원본에서 지워도 된다.

## 3. 알림 보내기 (손으로만)

```powershell
python tools/notices/notice.py push --id winter-tips --target all              # 시험: 보낼 메시지만 보여 준다(네트워크 없음)
python tools/notices/notice.py push --id winter-tips --target all --remote-dry-run   # Actions 를 시험 모드로 돌려 보기(아무것도 안 보냄)
python tools/notices/notice.py push --id winter-tips --target TH --send         # 실제로: 태국을 찜했거나 여행 가는 + 공지 알림을 켠 기기
```

- `--send`는 GitHub REST API로 notices.yml의 `workflow_dispatch`(inputs: `notice_id`, `target`, `dry_run=false`)를 부른다. 토큰은 `git -c credential.namespace=readyport credential fill`에서 꺼내 쓰고 화면에 찍지 않는다. FCM 키는 운영자 PC에 없어도 된다.
- Actions는 같은 검증 → (dry_run이 아니면) 서명본을 다시 올리고 → FCM HTTP v1로 토픽 메시지를 보낸다. 403이면 경고만 남기고 성공으로 끝난다(deploy-packs와 같은 교훈 — 서비스 계정에 `Firebase Cloud Messaging API 관리자` 역할이 없거나 API가 꺼져 있을 때).
- 보내기 전 거절하는 경우: 공지가 없거나 꺼져 있음, 아직 시작 전·이미 끝남, 나라를 골랐는데 `audience`에 그 나라가 없음. 광고 공지를 밤(21~8시 KST)에 보내면 경고 — 기기가 아침 8시까지 미룬다.
- Actions 화면에서 직접 부를 수도 있다: Actions › notices › Run workflow.

## 4. 사람들이 보는 것

- **앱을 켤 때**(첫 실행 질문을 마친 뒤, 자녀 폰 모드 아님, 위젯·알림·공유로 연 실행 아님): 서명본을 최대 2.5초 기다려 받고(못 받으면 휴대폰에 받아 둔 사본) **한 번에 하나**를 띄운다. 여럿이면 우선순위 차례로 다음에 켤 때 하나씩. **긴급 공지만** 여럿을 이어서 띄운다.
- 대화상자: 종류 색 띠 + 배지(`긴급 공지`·`공지`·`이벤트`·`이용 안내`, 광고면 `광고`) + 쪽 수 + 제목 + 그림 + 글(길게 눌러 복사) + 버튼.
  - 긴급: 마지막 쪽까지 닫기(X)가 없고 `끝까지 읽으면 닫을 수 있어요`. 마지막 쪽에서 `확인` · `오늘 하루 보지 않기` · `다시 보지 않기`.
  - 일반·이벤트: 언제든 X · `확인` · `오늘 하루 보지 않기` · `다시 보지 않기`. `확인`만 누르면 다음에 켤 때 다시 뜰 수 있다(차례).
  - 이용 안내: 언제든 X, 보지 않기 버튼 없음 — **한 번 보면 끝**(목록에서 다시 본다).
- **설정 › 공지·소식**: `공지사항`(지금 공지·지난 공지 다시 보기) · `공지 알림`(기본 켬) · `광고성 소식 받기`(기본 끔 — 켜고 끈 날을 그 줄 아래에) · `밤에도 광고성 소식 받기`(광고를 켰을 때만, 기본 끔).
- **알림**: 통로 `공지·소식`(챙길 일 알림과 따로 끌 수 있다). 누르면 그 공지가 열린다.

## 5. 광고성 정보 (정보통신망법 제50조)

| 법 | 앱에서 |
|---|---|
| 미리 받기 동의(옵트인) | `광고성 소식 받기` 기본 끔. 켠 기기만 토픽 `notice_promo`를 구독하고, 앱을 켤 때 광고 공지도 동의한 기기에만 띄운다 |
| 제목 `(광고)` | 검증 도구·앱 둘 다 막는다(`(광고)` 없는 promo, promo가 아닌데 `(광고)`가 있는 공지는 버림) |
| 받지 않는 방법 표시 | 광고 알림 본문 끝에 `광고 알림 끄기: 레디포트 › 설정 › 공지·소식`, 대화상자에 `광고성 소식은 설정 › 공지·소식에서 언제든 끌 수 있어요.` |
| 21시~다음 날 8시 별도 동의 | `밤에도 광고성 소식 받기`(기본 끔). 끈 기기에 밤에 온 광고는 **버리지 않고 아침 8시까지 미룬다**(WorkManager) — 그때 동의·기간을 다시 보고 알린다. 밤은 **한국 시각과 휴대폰 시각 둘 다**로 본다(해외 여행 중 새벽 알림 방지) |
| 동의·철회 결과 알림(보낸 곳·날짜·결과) | 켜거나 끄면 바로 대화상자: `보내는 곳: 레디포트 / 처리한 날: … / …`. 날짜는 이 휴대폰에만 적고 설정 줄 아래에 계속 보인다 |
| **2년마다 동의 확인(제50조 제8항)** | 서버에 누가 동의했는지 모르는 구조라 운영자가 개별 통지를 보낼 수 없다. **운영자 할 일**: 광고 공지를 처음 보낸 날부터 2년이 되기 전에, 앱 공지(category service)로 "광고성 소식 받기에 동의한 분은 설정에서 계속 받을지 확인해 주세요"를 띄우고, 필요하면 앱 업데이트로 동의 날짜가 2년 지난 기기에 다시 묻는 화면을 넣는다. 법률 검토 때 함께 확인 |

## 6. 첫 공지 `about-readyport` (운영자 요청 2026-10-08)

- 종류 **이용 안내(guide)** — 한 번 보면 앱을 켤 때 다시 뜨지 않고(설치마다 한 번), 다시 보려면 공지사항 목록. `version`을 올리면 다시 한 번 보인다. 끝 날짜 없음, 우선순위 900, 대상 모두, 서비스 안내(광고 아님).
- 카드 두 장(1080×1350): `https://readyport-app.web.app/notices/about-readyport-1.png`(앱 소개), `…/about-readyport-2.png`(여권 정보는 이 휴대폰에만). 파일은 `hosting/public/notices/`, 원본 그리기는 `app/src/test/java/com/readyport/ui/notice/NoticeCardArtTest.kt`(앱 글꼴·토큰·일러스트) → `pack_images.py`.
- **문구 원칙**: 운영자 원문의 "유출 가능성은 전혀 없다"는 쓰지 않았다(잃어버리거나 루팅한 휴대폰·악성 앱까지 막는다고 말할 수 없다 — Play 오인 정책·표시광고법). 대신 사실이면서 강한 말: `레디포트 서버에는 여러분의 여권 정보가 아예 없어요` / `서버 해킹으로 새어 나갈 일이 없어요` + 각주 `휴대폰 잠금은 꼭 걸어 두세요`.

## 7. 처음 한 번 (운영자)

1. Firestore 규칙 배포: `firebase deploy --only firestore:rules` (`notices/{id}` get만 — 저장소에 들어 있음). 규칙 테스트 `cd tools/firestore && npm test`.
2. 서비스 계정 역할 확인: Firestore 쓰기(Cloud Datastore 사용자) + `Firebase Cloud Messaging API 관리자`(+ 그림을 올릴 때 Firebase Hosting 관리자). 이미 videos·deploy-packs가 쓰는 것과 같다.
3. 이 브랜치를 머지하면 notices.yml이 첫 공지를 올리고 그림 때문에 Hosting도 배포한다. **공지를 보이는 앱 버전**(이 기능이 든 버전)을 출시한 뒤에야 사람들이 본다 — 0.5.0은 공지를 읽지 않는다.
4. 개인정보 처리방침 갱신본(이 브랜치의 `hosting/public/privacy/index.html`)이 같은 Hosting 배포로 올라간다. Play Console 데이터 보안 양식은 `docs/play/DATA_SAFETY.md` 4절대로 확인.
