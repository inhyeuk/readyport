# 게시판 작업 보고 (2026-10-08, 브랜치 `claude/board`)

운영자 요청: *"게시판의 기능을 넣어줘. 게시판은 하단 고정 메뉴에 추가해 주고, Q&A와 자유로운 토론을 할 수 있도록 하기 위함이니 그 목적에 맞춰 구성해줘. 게시할 수 있고 답글도 달 수 있도록 해줘. 스토리지 공간 이슈가 있으니 이미지/ 동영상 등록은 가능하도록 하되, 설정에서 off로 해서 나중에 필요할 경우 on으로 변경하면 기능이 활성화되도록 해줘. 그 외 게시판의 주요 기능은 우수 게시판의 사례를 참고해서 적용해줘. 그리고 최대한 아이콘, 이미지 등을 활용해서 예쁘게 구성해줘"*

운영 안내 `docs/BOARD.md` · 설계 기록 `docs/ARCHITECTURE.md` '구현 결정 기록 (게시판)' · 화면 DESIGN_SPEC 부록 L · 결정 `OWNER_DECISIONS.md` B-1~B-9 · PRD 4.1·5.15·5.18·6.3·7.1.

## 1. 무엇을 만들었나

| 갈래 | 파일 |
|---|---|
| 데이터·검사 | `board/BoardModels.kt`(글·댓글·이용자·질의·오류), `BoardText.kt`(길이·검색 낱말·개인정보 그물·욕설·닉네임·시각·미리보기), `BoardThreads.kt`(한 단계 답글·채택 맨 위·보이는 모양) |
| 서버 | `board/BoardBackend.kt`(경계), `FirestoreBoardBackend.kt`(Firestore 묶음 쓰기·익명 로그인·Storage), `BoardRepository.kt`(검사·간격·로그인 늦추기·답글 확인·내 기록 지우기), `BoardLocal.kt`(DataStore `board`: 규칙 동의·닉네임 사본·차단·답글 시각) |
| 사진·동영상(꺼짐) | `board/BoardMedia.kt`(1600px·EXIF 없는 JPEG 다시 그리기, 동영상 30초·20MB·위치 빼고 다시 담기) |
| 답글 알림 | `board/BoardReplies.kt`(알림 글·통로 `board`·`BoardReplyCheck` — 앱 시작·`ChecklistSweepWorker`·`PackSyncWorker`) |
| 화면 | `ui/board/` — `BoardHome`(탭), `BoardPost`(글·댓글), `BoardWrite`(쓰기·고치기), `BoardJoin`(규칙·이름), `BoardDialogs`(신고), `BoardAdmin`(운영자), `BoardSettings`(설정 묶음), `BoardComponents`·`BoardForms`(부품), `BoardArt`(그림 다섯) |
| 길·탭 | `nav/Routes.kt`(다섯 길), `nav/BottomTabs.kt`(탭 다섯·숫자 배지·칸에 맞는 라벨), `ReadyPortRoot`·`MainActivity`·`MainViewModel`·`ReadyPortApp`·`SettingsScreen`·`SettingsRepository`(`boardReplies`)·`AppModule`·`NetworkThumbnails`(켰을 때만 Storage `board/`) |
| 문구 | `res/values/strings_board.xml` |
| 서버 규칙 | `firebase/firestore.rules` '게시판' 절, `firebase/firestore.indexes.json`(새), `firebase/storage.rules`(새), `firebase.json`(색인·Storage 연결) — **배포 안 함** |
| 의존성 | `firebase-auth`, `firebase-storage` (같은 BoM) |
| 테스트 | `board/BoardTextTest`(12)·`BoardThreadsTest`(7)·`BoardRepositoryTest`(23, 가짜 서버 `FakeBoardBackend`)·`BoardMediaTest`(5, 가짜 GPS JPEG)·`BoardRepliesTest`(3), `ui/board/BoardUiTest`(7), `ReadyPortRootTest`(+2), 규칙 `tools/firestore/rules.test.mjs`(+16), 갤러리 `GalleryBoard`(15화면) |
| 문서 | `docs/BOARD.md`(새), 이 보고, PRD, ARCHITECTURE, DESIGN_SPEC 부록 L, OWNER_DECISIONS, HUMAN_TASKS 4-2, 개인정보 처리방침(게시본 3-2절 + 초안), DATA_SAFETY 6절 |

## 2. 사람들이 보는 것

- **탭** `둘러보기 · 내 여행 · 게시판 · 도움 · 설정`. 새 댓글이 오면 게시판 탭에 빨간 숫자(9 넘으면 `9+`, TalkBack `새 댓글 n개`). 자녀 폰 모드에는 없다.
- **게시판 첫 화면**: 그림 카드 둘(Q&A — 물음표·체크 말풍선 파랑 / 자유 토론 — 하트 말풍선 청록) → 게시판 머리(목적 한 줄 + `질문하기`/`글쓰기` + Q&A는 `정부 기관이 아니니 공식 안내로 확인`) → 최신·인기·답변 기다려요 → 나라(사진 대화상자) → 찾기 → 운영자 고정 글 → 글 카드 → `글 더 보기` → 공개 한 줄 + 규칙·운영자 연락.
- **글**: 해결됨·나라 사진 태그·제목·글쓴이(첫 글자 아바타)·본문·추천·공유·⋯ → 답변(채택한 답 맨 위 초록 테두리) → 한 단계 답글(@닉네임) → 쓰기 카드.
- **처음 쓰기**: 규칙 다섯 → `규칙을 지킬게요` → 이름(`여행자 4821` 추천) + 공개 범위 세 줄 → 쓰기(좋은 질문 쓰는 법·나라 칩·제목/내용·개인정보 경고·`올리기`).
- **설정 › 게시판**: 답글 알림 · 내 게시판 ID(복사) · 규칙 · 차단 풀기 · (운영자) 사진·동영상 스위치·신고 관리 · 내 기록 모두 지우기.

## 3. 참고한 게시판의 좋은 점 → 레디포트에 맞게

| 어디서 | 무엇 | 여기서 |
|---|---|---|
| Stack Overflow | 답 채택 · 해결 표시 · 채택한 답을 맨 위 · 추천 | 질문한 사람만 채택(규칙), `해결됨` 초록 / `답변 기다려요` 호박색, 채택 묶음 맨 위 |
| 네이버 지식iN | `답변 기다려요` 모아 보기, 질문 쓰는 법 안내 | 정렬 `답변 기다려요`, `좋은 질문을 쓰는 법` 카드 |
| 클리앙 | 운영자 공지 고정, 신고 누적 블라인드, 차단 | 고정 글 + `운영자` 배지, 신고 3번이면 접기 + `그래도 보기`, 이 휴대폰 차단 |
| 디스콰이엇 | 카드형 글 목록·아바타·태그 | 카드뉴스 글 카드, 첫 글자 아바타(ID 색), 나라 사진 태그 |
| Reddit | 인기 정렬, 지운 글·댓글의 자리 남기기(묶음 유지), 한 단계 답글 | `인기 = 추천 + 댓글`, `삭제된 댓글이에요`, 답글의 답글은 같은 묶음 + @닉네임 |

## 4. 신원·개인정보 (새 신뢰 경계)

- 이름·이메일·전화 없이 **Firebase 익명 로그인** — 처음 쓰기·추천·신고를 누를 때만. 읽기는 로그인 없이.
- 게시판 코드는 지갑·여행 장부를 읽지 않는다. 서버에 가는 것은 사람이 공개하려고 쓴 글·별명·익명 ID뿐 — 화면에서 세 번(이름 정하기·쓰기 아래·설정) 말한다.
- 개인정보 그물: 여권 번호·MRZ·주민등록번호는 막고(빨갛게 표시), 전화·이메일은 경고(`이대로 올리기`). 욕설은 `＊`. 답글 알림 미리보기는 욕설이면 숫자만, 잠금 화면에서는 숨김.
- 차단·규칙 동의·답글 시각은 이 휴대폰에만. `내 게시판 기록 모두 지우기` → 서버의 내 글·댓글·추천·이름 → 로그아웃.

## 5. 서버 없이 지키는 규칙 (Firestore 규칙 테스트 23/23 통과 — 기존 7 + 게시판 16)

익명 사용자의 올바른 글 · 로그인·닉네임 없으면 쓰기 불가 · 닉네임 규칙(운영자 사칭 금지) · 필드·길이 검사(15가지 나쁜 글) · 글쓴이 = 로그인 ID·닉네임 = 내 닉네임 · 남의 글 고치기·지우기 불가 · 추천 수는 추천 문서와 짝(두 번·남 대신·자기 글 불가) · 신고 수는 신고 문서와 짝(한 번, 신고 문서는 운영자만 읽음) · 댓글 수는 새 댓글과 짝, 답글 한 단계, 알림 대상 제한 · 댓글 고치기·지우기(자리 남김) · 글 30초·댓글 10초(서버 시각, 시각 되돌리기 불가) · 가린 글은 목록·읽기에서 빠짐 · 고정·가림·사진 스위치는 운영자만, `config/admins`는 아무도 못 씀 · 채택은 질문한 사람만·맨 위 답만 · 사진은 켰을 때·내 경로만 · 내 기록 지우기 질의. 기존 리포트·찜 수·공지·영상 규칙 테스트 그대로 통과.

## 6. 사진·동영상 — 만들어 두고 꺼 둠

- 스위치 `config/board.mediaEnabled`(운영자만, 기본 없음 = 꺼짐). 꺼지면 버튼이 없고 규칙도 거절. 켜면 사진 4장(1600px, EXIF·GPS 지움 — 가짜 GPS 사진으로 테스트), 동영상 30초·20MB(위치 정보 빼고 다시 담기 — 실기기 확인 필요), 재생은 휴대폰 기본 앱.
- **켜려면 Blaze(종량제) 요금제 + 예산 알림 + `firebase deploy --only storage`** — Storage가 없으면 `지금은 사진·동영상을 올릴 수 없어요. 글만 올려 주세요.`

## 7. 검사

- 빌드: `assembleDebug testDebugUnitTest lintDebug` — 결과는 아래 8절.
- 접근성 감사 11개(393dp 기본·쉬운, 200% 쉬운, sdk31 200% 기본·쉬운, 360dp 기본·쉬운, 360dp sdk31 200% 기본·쉬운, 영어 기본·쉬운) 모두 통과 — 게시판 15화면 + 탭 막대 포함. 고친 것: 입력칸 이름(TalkBack), API 33 미만 입력칸·댓글의 낱말 안 줄바꿈(보이는 글만 WORD JOINER — `KeepWordsTransformation`), 탭 숫자가 칸에 눌려 넘침(크기 0 겹침·dp 글자), 글 카드 아래 줄 넘침(`TrailingFlow`), 긴 게시판 ID 넘침(4자마다 줄바꿈 자리), 닉네임 한 음절 줄.
- 갤러리(가짜 값만): `board-qna`·`board-talk`·`board-empty-search`·`board-offline`·`board-post-qna`·`board-post-talk`·`board-write-pii`·`board-write-media`·`board-rules`·`board-nickname`·`board-report`·`board-admin`·`settings-board`·`settings-board-admin`·`tabs-5` — 기본·쉬운·sdk31 200%로 찍어 눈으로 보고 고침(머리 카드가 위 그림 카드를 되풀이하던 것 정리, 고정 안내 글의 `해결됨` 제거, 빨강 상자 막대 모서리, 이름표 그림이 텔레비전처럼 보이던 것, 숫자 배지가 아이콘을 덮던 것, `글쓴이` 시계 아이콘).
- 기존 테스트 하나(`HomeHeroTest.oneTripGetsItsOwnNumberedBox`)는 바탕 커밋 e569312(같은 달이면 끝 날짜 달 생략)에서 이미 깨져 있었다 — 테스트의 기대 날짜를 그 동작에 맞췄다.

## 8. 결과

- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` — **BUILD SUCCESSFUL**, 단위 테스트 **650개 모두 통과**(게시판 새 테스트 59개: 글 다루기 12 · 묶음 7 · 저장소 23 · 사진 5 · 알림 글 3 · 화면 7 · 루트 2).
- lint **오류 0, 경고 14** — 게시판에서 나온 경고 0. 공지 작업 기준선 12개와 같은 항목 + `NewerVersionAvailable` 2개(Kotlin 2.4.21이 새로 나와 생긴 버전 알림 — 이 작업과 무관, 버전은 올리지 않았다).
- Firestore 규칙 `cd tools/firestore && npm test` — **23/23 통과**(기존 7 + 게시판 16).
- `python tools/packs/test_build_packs.py` 31개 OK · `python tools/notices/test_notices.py` 22개 OK.
- 접근성 감사 11개 모두 통과, 갤러리 기본·쉬운·sdk31 200% 캡처 확인.
- `gradle/libs.versions.toml`(firebase-auth·firebase-storage 두 줄)도 커밋에 넣었다 — 빠지면 빌드가 되지 않는다.

## 9. 운영자가 할 일 (출시 전)

1. Firebase 콘솔 › Authentication › **익명 로그인 켜기** (App Check 강제 중이면 Authentication 등록)
2. `firebase deploy --only firestore:rules,firestore:indexes --project readyport-app` (색인 빌드 몇 분)
3. 운영자 ID 등록: 글 하나 → 설정 › 게시판 › ID 복사 → 콘솔 `config/admins { uids: [...] }`
4. Play Console 데이터 보안(사용자 제작 콘텐츠·사용자 ID) + 콘텐츠 등급 '사용자 상호작용'
5. 개인정보 처리방침 3-2절 법률 검토(C8, 임시조치 통지) 후 Hosting 게시
6. (켤 때만) Blaze + 예산 알림 + Storage 시작 + `firebase deploy --only storage` + 설정 스위치
7. 실기기: 익명 로그인·글·댓글·채택·신고·차단·답글 알림(하루 쓸기), (켰을 때) 사진 EXIF·동영상 위치 제거
