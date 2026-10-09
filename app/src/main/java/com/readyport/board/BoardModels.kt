package com.readyport.board

import java.time.Instant

// ======================= 게시판 데이터 (docs/BOARD.md, ARCHITECTURE '게시판' 기록) =======================
// 레디포트에서 **처음으로 사람이 쓴 글이 서버(Firestore)에 올라가는** 기능이다. 여권·여행 정보와는 길이 완전히 다르다:
// - 여권·예약·여행 날짜는 여전히 이 휴대폰 안에만 있다(이 패키지는 그것들을 읽지 않는다).
// - 게시판 글·댓글·닉네임·익명 게시판 ID는 서버에 올라가 **누구나 볼 수 있다**.
// 서버 로직(Cloud Functions)이 없으므로 숫자(댓글·추천·신고 수)는 앱이 한 번에 묶어 쓰고(batch), Firestore 규칙이 짝을 검사한다.

/** 게시판 두 개 — 목적이 다르다: 질문과 답변(입국 서류·비자·공항) / 자유 토론(여행 이야기·정보 나눔) */
enum class BoardKind(val id: String) {
    Qna("qna"),
    Talk("talk"),
    ;

    companion object {
        fun of(id: String?): BoardKind? = entries.firstOrNull { it.id == id }
    }
}

/** 목록 정렬: 최신 / 인기(추천 + 댓글) / 답변 기다려요(Q&A만 — 해결되지 않은 질문) */
enum class BoardSort { Latest, Popular, Waiting }

/** 사진·동영상 하나 (Storage 내려받기 주소). 사진·동영상 올리기가 꺼져 있으면 언제나 빈 목록 */
data class BoardMediaItem(val url: String, val video: Boolean)

/** 게시글 한 개 (Firestore board_posts/{id}) */
data class BoardPost(
    val id: String,
    val kind: BoardKind,
    val title: String,
    val body: String,
    /** 나라 태그(ISO2, 앱의 9개 나라) — 없으면 null */
    val country: String?,
    val authorUid: String,
    val nickname: String,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val lastCommentAt: Instant = createdAt,
    val commentCount: Int = 0,
    val likeCount: Int = 0,
    val reportCount: Int = 0,
    /** Q&A: 글쓴이가 답 하나를 채택하면 true (`해결됨`) */
    val solved: Boolean = false,
    val acceptedId: String? = null,
    /** 운영자가 맨 위에 고정 */
    val pinned: Boolean = false,
    /** 운영자가 가림(임시조치) — 운영자·글쓴이 말고는 목록·읽기에서 빠진다(규칙) */
    val hidden: Boolean = false,
    /** 글쓴이가 지움 — 댓글이 있으면 글만 비우고 댓글 묶음은 남는다 */
    val deleted: Boolean = false,
    val media: List<BoardMediaItem> = emptyList(),
    val keywords: List<String> = emptyList(),
) {
    val score: Int get() = likeCount + commentCount
    val edited: Boolean get() = updatedAt.isAfter(createdAt) && !deleted
}

/** 댓글 한 개 (board_posts/{p}/comments/{id}). [parentId]가 있으면 그 댓글에 단 답글(한 단계만) */
data class BoardComment(
    val id: String,
    val postId: String,
    val postAuthorUid: String,
    val authorUid: String,
    val nickname: String,
    val body: String,
    val createdAt: Instant,
    val parentId: String? = null,
    /** 답글이 가리키는 댓글(맨 위 댓글 또는 같은 묶음의 답글) — 알림 대상·`@닉네임`이 그 댓글에서 온다(규칙) */
    val replyToId: String? = null,
    /** 답글이 가리키는 사람(답글의 답글은 같은 묶음에 붙고 `@닉네임`으로 표시) */
    val replyToUid: String? = null,
    val replyToNick: String? = null,
    val updatedAt: Instant = createdAt,
    val edited: Boolean = false,
    val likeCount: Int = 0,
    val reportCount: Int = 0,
    val deleted: Boolean = false,
    val hidden: Boolean = false,
)

/** 게시판 이용자 (board_users/{uid}) — 닉네임과 글쓰기 간격 확인용 시각만. 개인정보 없음 */
data class BoardUser(
    val uid: String,
    val nickname: String,
    val createdAt: Instant,
    val lastPostAt: Instant? = null,
    val lastCommentAt: Instant? = null,
    val postCount: Int = 0,
)

/** 운영 설정 (config/board) — 사진·동영상 올리기 스위치. 운영자만 바꾼다(규칙) */
data class BoardConfig(val mediaEnabled: Boolean = false)

/** 신고 이유 (Play UGC 정책: 앱 안 신고) */
enum class ReportReason(val id: String) {
    Spam("spam"),
    Abuse("abuse"),
    Personal("personal"),
    Illegal("illegal"),
    Misinfo("misinfo"),
    /** 미성년자가 쓴 글 같아요 — 운영자가 확인하면 지운다(개인정보처리방침) */
    Minor("minor"),
    Other("other"),
}

/** 목록 질의 */
data class BoardQuery(
    val kind: BoardKind,
    val sort: BoardSort = BoardSort.Latest,
    val country: String? = null,
    /** 찾는 말(앞뒤 공백 없이). 비면 찾지 않는다 */
    val search: String? = null,
)

/** 목록 한 쪽. [next]가 null이면 끝 — 백엔드가 정하는 커서(Firestore는 마지막 문서) */
data class BoardPage(val posts: List<BoardPost>, val next: Any?)

/** 새 글 */
data class NewPost(
    val id: String,
    val kind: BoardKind,
    val title: String,
    val body: String,
    val country: String?,
    val nickname: String,
    val keywords: List<String>,
    val media: List<BoardMediaItem> = emptyList(),
)

/** 새 댓글. [parentId]: 답글이면 묶음 맨 위 댓글 id */
data class NewComment(
    val postId: String,
    val postAuthorUid: String,
    val body: String,
    val nickname: String,
    val parentId: String? = null,
    val replyToId: String? = null,
    val replyToUid: String? = null,
    val replyToNick: String? = null,
)

/** 게시판에서 고를 수 있는 나라 (앱이 안내하는 9개 나라 — 사진 칩) */
object BoardCountries {
    val codes: List<String> = listOf("TH", "JP", "VN", "PH", "TW", "SG", "MY", "ID", "CN")

    fun valid(code: String?): Boolean = code == null || code in codes
}

/** 운영자 신고·가림 목록 한 줄 */
data class ReportedItem(val post: BoardPost, val comment: BoardComment? = null) {
    val reportCount: Int get() = comment?.reportCount ?: post.reportCount
    val hidden: Boolean get() = comment?.hidden ?: post.hidden
}

/** 게시판 쓰기 실패 이유 — 화면이 쉬운 한국어로 바꾼다 */
sealed class BoardError(message: String) : Exception(message) {
    /** 인터넷이 없거나 서버에 닿지 않음 */
    data object Offline : BoardError("offline")

    /** 익명 로그인이 켜져 있지 않음(운영자가 콘솔에서 켜야 한다) 등 — 글쓰기를 아직 쓸 수 없음 */
    data object AuthUnavailable : BoardError("auth")

    /** 규칙이 거절(권한·검증) */
    data object Denied : BoardError("denied")

    /** 너무 빨리 또 씀. [waitSeconds] 뒤에 다시 */
    data class TooFast(val waitSeconds: Long) : BoardError("too_fast")

    /** 사진·동영상 저장소를 쓸 수 없음(요금제·꺼짐) */
    data object MediaUnavailable : BoardError("media")

    /** 글이 없어졌거나 가려짐 */
    data object NotFound : BoardError("not_found")

    /** 만 19세 미만 — [from] 달부터 쓸 수 있다(읽기는 그대로). BoardAge */
    data class AgeRestricted(val from: java.time.YearMonth) : BoardError("age")

    /** 나이를 아직 모름 — 보관함(내 정보)을 한 번 열면 정해진다 */
    data object AgeCheckNeeded : BoardError("age_check")
}
