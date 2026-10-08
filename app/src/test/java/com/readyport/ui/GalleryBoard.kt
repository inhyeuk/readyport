package com.readyport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.readyport.board.BoardComment
import com.readyport.board.BoardKeywords
import com.readyport.board.BoardKind
import com.readyport.board.BoardMe
import com.readyport.board.BoardPost
import com.readyport.board.BoardSort
import com.readyport.board.DraftCheck
import com.readyport.board.PiiGuard
import com.readyport.board.PreparedMedia
import com.readyport.board.Profanity
import com.readyport.board.ReportedItem
import com.readyport.ui.board.Attachment
import com.readyport.ui.board.BoardAdminContent
import com.readyport.ui.board.BoardAdminUi
import com.readyport.ui.board.BoardHomeContent
import com.readyport.ui.board.BoardHomeUi
import com.readyport.ui.board.BoardJoinUi
import com.readyport.ui.board.BoardNicknameContent
import com.readyport.ui.board.BoardPostContent
import com.readyport.ui.board.BoardPostUi
import com.readyport.ui.board.BoardRulesContent
import com.readyport.ui.board.BoardSettingsBinding
import com.readyport.ui.board.BoardSettingsUi
import com.readyport.ui.board.BoardWriteContent
import com.readyport.ui.board.BoardWriteUi
import com.readyport.ui.board.JoinStep
import com.readyport.ui.board.ReportOnScrim
import com.readyport.ui.nav.BottomTabs
import com.readyport.ui.nav.Tab
import com.readyport.ui.settings.SettingsScreen
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import java.time.Instant

/**
 * 갤러리·접근성 점검용 게시판 화면 (docs/BOARD.md, DESIGN_SPEC 부록 L). **모두 지어낸 값**이다 —
 * 닉네임(`여행자 4821`·`바다 고양이` …)·게시판 ID·글은 실제 사람·실제 글이 아니다. 여권 번호처럼 보이는 글자도 견본(M12345678)뿐.
 */
object GalleryBoard {
    val now: Instant = Instant.parse("2026-10-08T03:00:00Z")
    private fun ago(minutes: Long) = now.minusSeconds(minutes * 60)

    private const val ASKER = "uid-asker-4821"
    private const val OPERATOR = "uid-operator"
    private val admins = setOf(OPERATOR)

    private fun post(
        id: String,
        kind: BoardKind,
        title: String,
        body: String,
        country: String?,
        nick: String,
        uid: String,
        minutesAgo: Long,
        comments: Int = 0,
        likes: Int = 0,
        solved: Boolean = false,
        pinned: Boolean = false,
        reports: Int = 0,
        deleted: Boolean = false,
    ) = BoardPost(
        id = id, kind = kind, title = if (deleted) "" else title, body = if (deleted) "" else body, country = country,
        authorUid = uid, nickname = if (deleted) "" else nick, createdAt = ago(minutesAgo), commentCount = comments,
        likeCount = likes, solved = solved, pinned = pinned, reportCount = reports, deleted = deleted,
        keywords = BoardKeywords.forPost(title, body),
    )

    private val qnaPinned = post(
        "pin1", BoardKind.Qna, "게시판 이용 안내: 개인정보는 쓰지 마세요",
        "여권 번호·전화번호·예약 번호는 누구나 보게 돼요. 질문할 때는 나라와 날짜만 적어 주세요.",
        null, "안내 담당", OPERATOR, 60 * 24 * 5, comments = 0, likes = 21, solved = true, pinned = true,
    )

    private val qnaPosts = listOf(
        post(
            "q1", BoardKind.Qna, "일본 Visit Japan Web QR이 안 열려요",
            "입국 심사 QR을 저장해 뒀는데 비행기 모드에서 안 열려요. 미리 캡처해 두면 되나요?",
            "JP", "첫 해외", "uid-first", 25, comments = 0, likes = 1,
        ),
        post(
            "q2", BoardKind.Qna, "태국 TDAC는 도착 며칠 전부터 낼 수 있나요?",
            "11월 3일에 방콕에 도착해요. TDAC를 너무 일찍 내면 안 된다고 들었는데 언제부터 되나요?",
            "TH", "여행자 4821", ASKER, 120, comments = 5, likes = 12, solved = true,
        ),
        post(
            "q3", BoardKind.Qna, "베트남 전자비자 사진 규격이 궁금해요",
            "휴대폰으로 찍은 증명사진도 되나요? 배경은 흰색이어야 하나요?",
            "VN", "느린 여행", "uid-slow", 60 * 20, comments = 2, likes = 3,
        ),
        post("q4", BoardKind.Qna, "광고 글", "이 글은 신고가 쌓인 견본이에요. 접혀 보여요.", null, "홍보 계정", "uid-ad", 60 * 30, reports = 3),
        post(
            "q5", BoardKind.Qna, "싱가포르 SG Arrival Card 가족 것도 한 번에 되나요?",
            "아이 둘과 가요. 한 사람 계정으로 가족 모두 낼 수 있나요?",
            "SG", "짐 싸는 중", "uid-pack", 60 * 24 * 3, comments = 3, likes = 7, solved = true,
        ),
        post("q6", BoardKind.Qna, "", "", "MY", "", "uid-gone", 60 * 24 * 4, comments = 2, likes = 0, deleted = true),
    )

    private val talkPosts = listOf(
        post(
            "t1", BoardKind.Talk, "방콕 쩟페어 야시장 다녀왔어요",
            "저녁 7시쯤 가니 사람이 아주 많았어요. 현금을 조금 챙겨 가면 편해요. 망고 찹쌀밥 꼭 드세요!",
            "TH", "바다 고양이", "uid-cat", 40, comments = 8, likes = 24,
        ),
        post(
            "t2", BoardKind.Talk, "오사카 주유패스, 이틀이면 충분했어요",
            "첫날 성·전망대, 둘째 날 유람선까지 탔어요. 아침 일찍 움직이면 줄이 짧아요.",
            "JP", "도쿄 산책", "uid-walk", 60 * 5, comments = 3, likes = 9,
        ),
        post(
            "t3", BoardKind.Talk, "첫 해외여행 준비물 체크 끝!",
            "레디포트 체크리스트로 하나씩 지웠어요. 멀티 어댑터랑 보조배터리 꼭 챙기세요.",
            null, "첫 해외", "uid-first", 60 * 26, comments = 1, likes = 5,
        ),
        post(
            "t4", BoardKind.Talk, "다낭 우기에는 우산보다 우비가 좋아요",
            "바람이 세서 우산이 자꾸 뒤집혔어요. 편의점 우비가 제일 편했어요.",
            "VN", "느린 여행", "uid-slow", 60 * 24 * 2, comments = 0, likes = 2,
        ),
    )

    private fun c(
        id: String,
        uid: String,
        nick: String,
        body: String,
        minutesAgo: Long,
        parent: String? = null,
        replyTo: String? = null,
        likes: Int = 0,
        deleted: Boolean = false,
        reports: Int = 0,
    ) = BoardComment(
        id = id, postId = "q2", postAuthorUid = ASKER, authorUid = uid, nickname = if (deleted) "" else nick,
        body = if (deleted) "" else body, createdAt = ago(minutesAgo), parentId = parent, replyToNick = replyTo,
        likeCount = likes, deleted = deleted, reportCount = reports,
    )

    private val detailComments = listOf(
        c("c1", "uid-slow", "느린 여행", "저는 공항 와이파이로 냈는데, 미리 내 두는 게 마음 편해요.", 110, likes = 3),
        c(
            "c2", "uid-cat", "바다 고양이",
            "도착하기 3일 전부터 낼 수 있어요. 공식 사이트에서 내고 받은 QR을 사진으로 저장해 두세요.", 100, likes = 9,
        ),
        c("r1", ASKER, "여행자 4821", "고마워요! 아이 것도 따로 내야 하나요?", 90, parent = "c2"),
        c("r2", "uid-cat", "바다 고양이", "네, 한 사람씩 따로 내요. 아이도 마찬가지예요.", 80, parent = "c2", replyTo = "여행자 4821"),
        c("c3", "uid-gone", "", "", 70, deleted = true),
        c("r3", "uid-first", "첫 해외", "저도 같은 게 궁금했어요.", 60, parent = "c3"),
        c("c4", "uid-ad", "홍보 계정", "신고가 쌓인 견본 댓글이에요.", 50, reports = 3),
    )

    private val detailUi = BoardPostUi(
        loading = false,
        post = qnaPosts[1].copy(acceptedId = "c2", commentCount = detailComments.size),
        comments = detailComments,
        liked = false,
        me = BoardMe(ASKER, "여행자 4821", admin = false),
        admins = admins,
        likedComments = setOf("c2"),
        now = now,
    )

    private val talkDetailUi = BoardPostUi(
        loading = false,
        post = talkPosts[0],
        comments = listOf(
            BoardComment("k1", "t1", "uid-cat", "uid-walk", "도쿄 산책", "망고 찹쌀밥 저도 먹었어요! 몇 시에 문을 닫나요?", ago(30)),
            BoardComment("k2", "t1", "uid-cat", "uid-cat", "바다 고양이", "자정쯤 닫는 가게가 많았어요.", ago(20), parentId = "k1", replyToNick = "도쿄 산책"),
        ),
        liked = true,
        me = BoardMe("uid-walk", "도쿄 산책", admin = false),
        admins = admins,
        now = now,
        composer = "저도 다음 달에 가요. 012 3456 7890 으로 연락 주세요",
        replyTo = BoardComment("k2", "t1", "uid-cat", "uid-cat", "바다 고양이", "자정쯤 닫는 가게가 많았어요.", ago(20), parentId = "k1"),
    ).let { it.copy(check = DraftCheck(pii = PiiGuard.scan(it.composer))) }

    private val writeTitle = "TDAC 여권 번호 칸 질문"
    private val writeBody = "제 여권이 M12345678 인데 TDAC 여권 번호 칸에 이렇게 쓰면 되나요? 답은 010-1234-5678 로 주세요."
    private val writePii = BoardWriteUi(
        kind = BoardKind.Qna, country = "TH", title = writeTitle, body = writeBody,
        check = DraftCheck(pii = PiiGuard.scan("$writeTitle\n$writeBody")),
    )

    private fun writeMedia(thumb: ImageBitmap?) = BoardWriteUi(
        kind = BoardKind.Talk, country = "VN", title = "다낭 해변 사진 몇 장",
        body = "미케 해변 아침이 정말 예뻤어요. 사진은 크기를 줄여 올렸어요.",
        mediaEnabled = true,
        attachments = listOf(
            Attachment(1, PreparedMedia(ByteArray(0), video = false), thumb),
            Attachment(2, PreparedMedia(ByteArray(0), video = false), thumb),
            Attachment(3, PreparedMedia(ByteArray(0), video = true), null),
        ),
        check = DraftCheck(profanity = Profanity.contains("씨발")),
    )

    private val adminUi = BoardAdminUi(
        loading = false,
        items = listOf(
            ReportedItem(qnaPosts[3]),
            ReportedItem(qnaPosts[1].copy(hidden = true, reportCount = 0)),
            ReportedItem(qnaPosts[1], detailComments[6]),
        ),
    )

    /** 지어낸 익명 게시판 ID (실제 계정 아님) */
    private const val FAKE_BOARD_ID = "Xq3fK9pLm2TzR8vB1nWc7YdE4hA2"

    fun screens(thumb: ImageBitmap?): List<Pair<String, @Composable () -> Unit>> = listOf(
        // 게시판 첫 화면: Q&A — 운영자 고정 글 · 답변 기다려요 · 해결됨 · 신고 접힘 · 지운 글
        "board-qna" to {
            BoardHomeContent(BoardHomeUi(loading = false, pinned = listOf(qnaPinned), posts = qnaPosts, canLoadMore = true, admins = admins, now = now))
        },
        // 자유 토론 · 인기순 · 나라(태국) 고름
        "board-talk" to {
            BoardHomeContent(BoardHomeUi(kind = BoardKind.Talk, sort = BoardSort.Popular, country = "TH", loading = false, posts = talkPosts, now = now))
        },
        // 찾은 글 없음 / 인터넷 없음
        "board-empty-search" to {
            BoardHomeContent(BoardHomeUi(loading = false, search = "환전 수수료", now = now))
        },
        "board-offline" to { BoardHomeContent(BoardHomeUi(loading = false, offline = true, now = now)) },
        // 글 화면: 채택한 답이 맨 위 · 답글 묶음 · @닉네임 · 지운 댓글 · 신고 접힌 댓글 (질문한 사람이 보는 화면 — 채택 버튼)
        "board-post-qna" to { BoardPostContent(detailUi) },
        // 자유 토론 글: 추천함 · 답글 쓰는 중 + 전화번호 경고
        "board-post-talk" to { BoardPostContent(talkDetailUi) },
        // 글쓰기: 여권 번호·전화번호를 찾아 빨갛게 표시 (올리기 막힘)
        "board-write-pii" to { BoardWriteContent(writePii) },
        // 글쓰기: 운영자가 사진·동영상을 켰을 때 (사진 두 장 + 동영상 하나) + 욕설 안내
        "board-write-media" to { BoardWriteContent(writeMedia(thumb)) },
        "board-rules" to { BoardRulesContent(onAgree = {}) },
        "board-nickname" to { BoardNicknameContent(BoardJoinUi(step = JoinStep.Nickname, nickname = "여행자 4821")) },
        "board-report" to { ReportOnScrim() },
        "board-admin" to { BoardAdminContent(adminUi) },
        // 설정 › 게시판: 답글 알림 · 내 게시판 ID(복사) · 규칙 · 차단 2명 · 내 기록 지우기
        "settings-board" to {
            SettingsScreen(
                easyMode = LocalDimens.current.easyMode, onEasyModeChange = {}, notifGranted = true,
                board = BoardSettingsBinding(BoardSettingsUi(uid = FAKE_BOARD_ID, nickname = "여행자 4821", blockedCount = 2)),
                boardReplies = true,
            )
        },
        // 운영자 설정: 사진·동영상 올리기 스위치(꺼짐) · 신고·가림 관리
        "settings-board-admin" to {
            SettingsScreen(
                easyMode = LocalDimens.current.easyMode, onEasyModeChange = {}, notifGranted = true,
                board = BoardSettingsBinding(BoardSettingsUi(uid = OPERATOR, nickname = "안내 담당", admin = true, mediaEnabled = false)),
                boardReplies = true,
            )
        },
        // 하단 탭 다섯: 게시판 선택 / 둘러보기 선택 + 게시판 새 댓글 3 / 자녀 폰
        "tabs-5" to {
            Column(Modifier.fillMaxWidth().background(Tokens.Ground), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BottomTabs(selected = Tab.Board, onSelect = {})
                BottomTabs(selected = Tab.Home, onSelect = {}, badges = mapOf(Tab.Board to 3))
                BottomTabs(selected = Tab.Help, onSelect = {}, badges = mapOf(Tab.Board to 120))
                BottomTabs(selected = Tab.Present, onSelect = {}, tabs = Tab.Child)
            }
        },
    )
}
