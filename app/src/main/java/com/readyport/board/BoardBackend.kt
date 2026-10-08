package com.readyport.board

import java.time.Instant

/**
 * 게시판 서버 호출을 한곳에 모은다. 운영은 [FirestoreBoardBackend](Firestore + 익명 로그인 + Storage),
 * 테스트는 메모리 안 가짜(`FakeBoardBackend` — 네트워크 없음)로 바꾼다.
 *
 * 숫자(댓글·추천·신고 수)를 바꾸는 쓰기는 모두 **한 번에 묶어**(batch) 보낸다 — 규칙이 `getAfter`/`existsAfter`로
 * '추천 문서가 생기면서 추천 수가 1 오른다' 같은 짝을 확인한다(Cloud Functions 없이 숫자를 지키는 방법).
 * 실패는 [BoardError]로 바꿔 던진다.
 */
interface BoardBackend {
    /** 지금 로그인한 익명 게시판 ID (없으면 null) — 이 함수는 로그인하지 않는다 */
    fun currentUid(): String?

    /** 익명 로그인 (이미 했으면 그 ID). 콘솔에서 익명 로그인이 꺼져 있으면 [BoardError.AuthUnavailable] */
    suspend fun signIn(): String

    /** 로그아웃 (내 게시판 기록 모두 지우기 뒤) */
    suspend fun signOut()

    // ---------------- 읽기 (로그인 없이도 된다) ----------------

    /** 목록 한 쪽. [token]: 검색 조각(array-contains) — 있으면 정렬은 최신만 */
    suspend fun posts(query: BoardQuery, token: String?, after: Any?, limit: Int): BoardPage

    /** 운영자가 고정한 글 (게시판마다 최대 5개) */
    suspend fun pinned(kind: BoardKind): List<BoardPost>

    suspend fun post(id: String): BoardPost?

    /** 댓글 전부(쓴 차례). [includeHidden]: 운영자 보기 — 가린 댓글까지 */
    suspend fun comments(postId: String, includeHidden: Boolean): List<BoardComment>

    suspend fun user(uid: String): BoardUser?

    suspend fun config(): BoardConfig

    suspend fun admins(): Set<String>

    /** 내가 이 글을 추천했는지 */
    suspend fun likedPost(postId: String, uid: String): Boolean

    /** 이 글에서 내가 추천한 댓글 id */
    suspend fun likedComments(postId: String, uid: String): Set<String>

    /** 내가 이미 신고했는지 (글 또는 댓글) */
    suspend fun reported(postId: String, commentId: String?, uid: String): Boolean

    // ---------------- 쓰기 (로그인 필요) ----------------

    /** 닉네임 저장 (처음이면 board_users 문서를 만든다) */
    suspend fun saveNickname(uid: String, nickname: String)

    /** 새 글 id (Storage 경로·규칙에 먼저 필요하다) */
    fun newPostId(): String

    /** 새 글 + 내 이용자 문서(lastPostAt = 서버 시각) 한 번에. 30초 안에 또 쓰면 규칙이 거절 */
    suspend fun createPost(uid: String, post: NewPost)

    suspend fun editPost(postId: String, title: String, body: String, country: String?, keywords: List<String>)

    /** 글 지우기: 댓글이 없으면 문서째, 있으면 글만 비우고 묶음은 남긴다 */
    suspend fun deletePost(post: BoardPost)

    suspend fun setPostLike(postId: String, uid: String, on: Boolean)

    suspend fun setCommentLike(postId: String, commentId: String, uid: String, on: Boolean)

    /** 신고 문서 + 신고 수 +1 (한 사람 한 번) */
    suspend fun report(postId: String, commentId: String?, uid: String, reason: ReportReason)

    /** 댓글 + 글의 댓글 수 +1 + 내 lastCommentAt 한 번에 (10초 간격 규칙). 새 댓글 id */
    suspend fun addComment(uid: String, comment: NewComment, notify: List<String>): String

    suspend fun editComment(postId: String, commentId: String, body: String)

    /** 댓글 지우기 — 글자만 비우고 자리는 남는다(`삭제된 댓글이에요`) */
    suspend fun deleteComment(postId: String, commentId: String)

    /** 답 채택(null이면 채택 풀기) — 질문한 사람만(규칙) */
    suspend fun accept(postId: String, commentId: String?)

    // ---------------- 운영자 (config/admins 에 든 ID만 — 규칙) ----------------

    suspend fun setHidden(postId: String, commentId: String?, hidden: Boolean)

    suspend fun setPinned(postId: String, pinned: Boolean)

    /** 문서째 지우기 */
    suspend fun hardDelete(postId: String, commentId: String?)

    /** 신고 수를 0으로(신고를 검토해 문제없다고 본 경우) */
    suspend fun clearReports(postId: String, commentId: String?)

    /** 신고가 있는 글·댓글 + 가린 글 */
    suspend fun moderationQueue(): List<ReportedItem>

    suspend fun setMediaEnabled(on: Boolean)

    // ---------------- 답글 알림·내 기록 ----------------

    /** [since] 뒤에 나에게 온 댓글·답글(내 글의 댓글, 내 댓글의 답글) — 내가 쓴 것은 빼고 */
    suspend fun repliesFor(uid: String, since: Instant): List<BoardComment>

    /** 내 글·댓글·추천·이용자 문서를 지운다 (클라이언트 묶음 쓰기) */
    suspend fun deleteEverything(uid: String)

    /** 사진·동영상 올리기 (Storage board/{uid}/{postId}/…) — 꺼져 있거나 요금제가 안 되면 [BoardError.MediaUnavailable] */
    suspend fun uploadMedia(uid: String, postId: String, items: List<PreparedMedia>): List<BoardMediaItem>
}

/** 올릴 준비가 끝난 사진·동영상 (사진은 줄이고 EXIF를 뺀 JPEG, 동영상은 위치 정보 뺀 MP4) */
class PreparedMedia(val bytes: ByteArray, val video: Boolean) {
    val contentType: String get() = if (video) "video/mp4" else "image/jpeg"
    val extension: String get() = if (video) "mp4" else "jpg"
}
