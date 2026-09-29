# 나라별 YouTube 여행 영상 — 법·정책 확인 (2026-09-30)

운영자 요청: 각 나라 여행 설명에 YouTube 영상 링크를 최대 50개, 구독자 수·업로드 날짜·조회수로 정렬, 썸네일을 눌러 열기.

## 결론
가능하다. 단, **YouTube 공식 API(YouTube Data API v3)로만** 가져오고 YouTube API 서비스 약관·개발자 정책을 지킨다.
(웹 페이지를 긁어 오는 방식은 YouTube 약관 위반이라 쓰지 않는다.)

## 지켜야 할 것 → 레디포트에서 한 일
| 정책 (https://developers.google.com/youtube/terms/developer-policies, 2026-09-30 확인) | 반영 |
|---|---|
| 인증 없는 API 데이터는 30일 넘게 저장하지 않는다 (III.E.4.d), 저장 데이터는 최신에 가깝게 (III.E.4.e) | GitHub Actions `videos.yml`이 매일 새로 받음. 앱은 `generated_at`이 30일 지난 목록을 보여 주지 않음(`VideoListParser.MAX_AGE`). 앱 설치 파일에는 넣지 않음 |
| YouTube 서비스 약관 링크 표시, 자기 약관에 YouTube 약관 동의 문구 (III.A.1) | 영상 화면 아래 'YouTube 서비스 약관' 버튼 + 문구, 개인정보 처리방침에도 |
| 개인정보처리방침에 Google 개인정보처리방침 링크, 무엇을 쓰는지 설명 (III.A.2) | hosting/public/privacy 4절에 추가 |
| YouTube가 출처임을 분명히 (III.F.2) | 화면 제목 아래 'YouTube', 항목마다 'YouTube', 안내 카드 |
| API 데이터로 새 지표를 만들지 않는다 (III.E.4.h) | 조회수·구독자 수·게시일을 API 값 그대로 표시·정렬만(점수·순위 계산 없음). 숨긴 구독자 수는 표시하지 않음 |
| 아동용(Made for Kids) 영상 처리 (III.E.4.j) | 목록에서 아예 뺌 |
| 썸네일은 YouTube가 준 것을 그대로 | i.ytimg.com 주소만 허용, 메모리에서만 잠깐 보관(파일 저장·수정 없음) |

## 고르는 방법 (tools/videos/fetch_videos.py)
- 검색: `"{나라} 여행"`, 지역 KR, 언어 ko, 안전 검색 엄격, 4~20분·20분 이상 두 번 검색해 번갈아 섞음
- 빼는 것: 아동용, 비공개, 방송 예정·중, 3분 미만(쇼츠), 제목에 나라 이름이 없는 영상, 이상한 썸네일 주소
- 최대 50개, 정렬은 앱에서 조회수순(기본)·최신순·구독자순
- 쿼터: 검색 100단위 × 2 × 5개 나라 + 목록 조회 몇 단위 ≈ 하루 1,010단위(무료 10,000단위 안)

## 사람이 할 일
- Google Cloud(readyport-app)에서 **YouTube Data API v3 사용 설정** → **API 키** 만들기(이 API만 허용하도록 제한)
- GitHub 저장소 secret **YOUTUBE_API_KEY** 등록 → Actions의 `videos` 워크플로 수동 실행
- 키가 없으면 워크플로는 경고만 남기고 성공으로 끝나며, 앱은 '지금은 영상 목록을 볼 수 없어요'를 보여 준다
