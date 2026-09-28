# 레디포트 × ARIA 운영 연동

> 원본: `docs/source/ReadyPort_ClaudeCode_Prompt.md` (2026-09-28 기준). 장 번호는 원본과 같다.
> `[재확인]` = 조사 시점 사실이지만 바뀔 수 있음, 구현 전 공식 출처로 재확인. `[확인 필요]` = 미확정, 추측 금지·운영자에게 질문.

## 12. ARIA 운영 연동

### 12.1 전제
- ARIA는 운영자의 Windows PC에서 24시간 도는 Python 기반 자율 에이전트다. 텔레그램 봇 인터페이스(CMD:v1 JSON 명령 형식)와 크롤링·뉴스 수집·번역 파이프라인을 이미 갖고 있다.
- **ARIA 본체 코드는 이 저장소에 없다.** 이 저장소에는 `ops/aria/`에 ARIA가 불러 쓸 수 있는 **독립 모듈과 명확한 인터페이스**를 만든다. ARIA 쪽 연결은 운영자가 따로 한다.
- 역할 분담: 감지·증거 수집은 ARIA, 판단·수정은 Claude(로컬 헤드리스), 승인은 사람(텔레그램 + PR 머지), 배포는 GitHub Actions.

### 12.2 운영 흐름
1. **감지**(하루 1~2회, LLM 없이 결정적 처리): 다음 변경을 확인한 뒤, 변경 지문(diff 해시)과 증거 스냅샷을 저장한다.
   - 외교부 입국허가요건 API 전날 대비
   - 양식 페이지 구조 해시
   - 각국 공지 페이지
   - 기존 뉴스 파이프라인에 입국정책 키워드 추가(TDAC, ETIAS, arrival card 등)
2. **현장 신호**: Firestore `field_reports`를 집계한다. 실패율이 임계치를 넘으면 해당 양식의 `kill_autofill_*`를 자동으로 켜고(안전한 방향) 텔레그램으로 알린다.
3. **승인 요청**: 텔레그램으로 변경 요약과 증거 링크를 보낸다. 운영자가 CMD:v1 '처리' 명령으로 승인한다.
4. **수정**: ARIA가 로컬에서 Claude Code를 헤드리스로 실행한다.
   - `claude -p "<작업 지시>" --allowedTools "<필요 도구만>" --output-format json`
   - CLAUDE.md 규칙이 적용되도록 일반 모드로 실행한다(`--bare`는 CLAUDE.md를 읽지 않으므로 사용 금지) `[재확인]`.
   - 증거 파일과 해당 국가 팩을 입력으로 JSON·레시피를 수정하고, 스키마 검증과 선택자 확인(제출 금지)을 마친 뒤 `claude/` 브랜치에 푸시하고 PR을 만든다.
5. **머지**: 운영자가 GitHub에서 검토하고 머지한다. 비자 필요 여부·기한 등 정책 의미가 바뀌는 수정은 반드시 사람이 확인한다.
6. **배포**(GitHub Actions): 팩 서명 → Firebase Hosting 배포 → Remote Config 버전 갱신 → FCM 토픽 알림.

### 12.3 정기 작업
| 주기 | 작업 |
|---|---|
| 매일 | 정책·양식·공지·뉴스 감지(12.2-1), 현장 신호 집계 |
| 주 1회 | 인기 순위 계산·배포, 제휴 링크 점검, 교통 앱 연결 동작 점검 |
| 월 1회 | 다음 분기 축제 날짜 등 계절 정보 재확인, 90일 넘게 확인하지 않은 결제·교통 항목 재검증, 쇼핑 리스트 검색 추이 갱신, 긴급 연락처·대사관 정보 점검 |

### 12.4 ARIA 기존 이슈 대응 (필수 설계)
| 알려진 이슈 | 이 파이프라인에서 생길 사고 | 대책 |
|---|---|---|
| 태스크 중복 재제출 루프 | 같은 변경으로 Claude 실행·PR이 반복, 구독 사용량 소진 | 변경 지문을 멱등 키로 저장(SQLite). 같은 지문은 1회만 처리. 하루 Claude 호출 상한 |
| 확정값을 스냅샷으로 덮어쓰기 | 사람이 승인한 정책값을 미검증 크롤링 값이 덮어씀 | `draft / approved / published` 상태. ARIA는 draft만 쓰고, approved는 PR 머지로만 변경 |
| Watchdog 300초 타임아웃 | 여러 나라 사이트 순회 중 끊김 | 국가·양식 단위로 작업 분할, 작업별 타임아웃, 실패 국가만 재시도 |
| Gemini 일일 할당량 소진 | 요약 단계가 막히며 감지까지 멈춤 | 감지는 LLM 없이. 요약 실패해도 원본 diff는 텔레그램으로 전송 |

### 12.5 `ops/aria/` 모듈 명세
- `detectors/mofa_entry_diff.py`: 외교부 API 조회·전일 비교
- `detectors/form_structure_hash.py`: 양식 페이지 구조 해시(제출·입력 금지, 조회만)
- `detectors/notice_watch.py`, `detectors/news_keywords.py`
- `signals/field_reports.py`: Firestore 집계(Admin SDK), 임계치 판단
- `actions/kill_switch.py`: Remote Config REST로 `kill_autofill_*`만 변경
- `fingerprint_store.py`: 멱등 키 저장소(SQLite)
- `approvals/telegram_cmd.py`: CMD:v1 형식 승인 명령 파서 인터페이스(ARIA 텔레그램 봇에 연결할 어댑터)
- `runners/claude_headless.py`: `claude -p` 실행 래퍼(타임아웃, JSON 결과 파싱, 호출 상한)
- `jobs/ranking.py`, `jobs/link_check.py`, `jobs/content_freshness.py`, `jobs/shopping_trend.py`
- `heartbeat.py`: 점검 완료 시 `ops/heartbeat` 기록
- 설정은 `.env`(커밋 금지). 크롤링은 하루 1~2회, 요청 간격을 두고, 봇 차단 시 우회하지 말고 "수동 확인 필요"로 표시.

### 12.6 권한과 저장소
- ARIA 자격증명 최소화: 이 저장소 전용 fine-grained GitHub 토큰(쓰기), Remote Config 전용 서비스 계정. **Hosting 배포 권한은 ARIA에 주지 않는다**(GitHub Actions secrets에만).
- 원칙: 안전한 방향(자동 입력 끄기, 경고 배너 켜기)은 ARIA가 자동으로, 위험한 방향(새 정책값 게시, 자동 입력 다시 켜기, 레시피 변경)은 사람 승인 후.
- main 브랜치 보호(PR 리뷰 필수). GitHub 무료 플랜에서 비공개 저장소는 브랜치 보호가 제한되므로 공개 저장소를 기본으로 한다 `[확인 필요]`.

### 12.7 ARIA 감시 (단일 장애점 대책)
- GitHub Actions가 하루 1회 `ops/heartbeat`를 확인한다. 3일 이상 끊기면 운영자에게 알리고 Remote Config `stale_banner`를 켠다(앱에 "정보 점검이 늦어지고 있어요" 배너 표시).
- Claude Code 클라우드 루틴은 PC 장기 부재 시 백업으로만 고려한다. 리서치 프리뷰 단계이며, 하루 실행 한도가 있다(Pro 5회, Max 15회, Team·Enterprise 25회) `[재확인]`.

### 12.8 GitHub Actions
- `deploy-packs.yml`: main 머지 시 팩 스키마 검증 → 서명 → Hosting 배포 → Remote Config 버전 → FCM
- `aria-watchdog.yml`: 하트비트 점검(하루 1회)
- `android-ci.yml`: 빌드·단위 테스트·린트
