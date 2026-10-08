package com.readyport.board

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 테스트 시계 — [advance]로 시간을 보낸다 */
class TestClock(var now: Instant = Instant.parse("2026-10-08T03:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    fun advance(seconds: Long) {
        now = now.plusSeconds(seconds)
    }
}

/**
 * 메모리 안 가짜 게시판 서버 (네트워크 없음). Firestore 규칙(firebase/firestore.rules)이 막는 것 중 앱이 기대는 것을 같은 뜻으로 흉내 낸다:
 * 글쓴이 = 로그인 ID, 30초·10초 간격, 가린 글은 목록에서 빠짐, 추천·신고는 한 사람 한 번, 자기 글 추천·신고 불가, 채택은 질문한 사람만.
 * 규칙 자체는 tools/firestore/rules.test.mjs 가 에뮬레이터로 따로 검사한다.
 */
class FakeBoardBackend(private val clock: TestClock = TestClock()) : BoardBackend {
    var uid: String? = null
    var authAvailable = true
    var offline = false
    var mediaAvailable = false
    var config = BoardConfig()
    var admins: Set<String> = emptySet()
    val users = linkedMapOf<String, BoardUser>()
    val posts = linkedMapOf<String, BoardPost>()
    val comments = linkedMapOf<String, MutableList<BoardComment>>()
    private val likes = mutableSetOf<Pair<String, String>>() // (postId, uid)
    private val commentLikes = mutableSetOf<Triple<String, String, String>>() // (postId, commentId, uid)
    val reports = mutableMapOf<Pair<String, String?>, MutableMap<String, ReportReason>>()
    val notifyLog = mutableListOf<Pair<String, List<String>>>() // (commentId, notify)
    var uploads = 0
    private var seq = 0
    var postsCalls = 0

    private fun online() {
        if (offline) throw BoardError.Offline
    }

    private fun me(): String = uid ?: throw BoardError.Denied

    override fun currentUid(): String? = uid

    override suspend fun signIn(): String {
        online()
        if (!authAvailable) throw BoardError.AuthUnavailable
        return uid ?: "anon-${++seq}".also { uid = it }
    }

    override suspend fun signOut() {
        uid = null
    }

    override suspend fun posts(query: BoardQuery, token: String?, after: Any?, limit: Int): BoardPage {
        online()
        postsCalls++
        var list = posts.values.filter { it.kind == query.kind && !it.hidden }
        query.country?.let { c -> list = list.filter { it.country == c } }
        if (token != null) list = list.filter { token in it.keywords }
        list = when {
            token != null -> list.sortedByDescending { it.createdAt }
            query.sort == BoardSort.Popular -> list.sortedWith(compareByDescending<BoardPost> { it.score }.thenByDescending { it.createdAt })
            query.sort == BoardSort.Waiting -> list.filter { !it.solved }.sortedByDescending { it.createdAt }
            else -> list.sortedByDescending { it.createdAt }
        }
        val from = (after as? Int) ?: 0
        val page = list.drop(from).take(limit)
        val next = if (page.size < limit) null else from + page.size
        return BoardPage(page, next)
    }

    override suspend fun pinned(kind: BoardKind): List<BoardPost> =
        posts.values.filter { it.kind == kind && it.pinned && !it.hidden }.sortedByDescending { it.createdAt }

    override suspend fun post(id: String): BoardPost? {
        online()
        val p = posts[id] ?: return null
        return if (p.hidden && uid != p.authorUid && uid !in admins) null else p
    }

    override suspend fun comments(postId: String, includeHidden: Boolean): List<BoardComment> =
        comments[postId].orEmpty().filter { includeHidden || !it.hidden }.sortedBy { it.createdAt }

    override suspend fun user(uid: String): BoardUser? = users[uid]

    override suspend fun config(): BoardConfig = config

    override suspend fun admins(): Set<String> = admins

    override suspend fun likedPost(postId: String, uid: String): Boolean = (postId to uid) in likes

    override suspend fun likedComments(postId: String, uid: String): Set<String> =
        commentLikes.filter { it.first == postId && it.third == uid }.map { it.second }.toSet()

    override suspend fun reported(postId: String, commentId: String?, uid: String): Boolean =
        reports[postId to commentId]?.containsKey(uid) == true

    override suspend fun saveNickname(uid: String, nickname: String) {
        online()
        require(uid == me())
        if (NicknameRules.validate(nickname) != null) throw BoardError.Denied
        users[uid] = users[uid]?.copy(nickname = nickname) ?: BoardUser(uid, nickname, clock.instant())
    }

    override fun newPostId(): String = "p${++seq}"

    override suspend fun createPost(uid: String, post: NewPost) {
        online()
        if (uid != me()) throw BoardError.Denied
        val user = users[uid] ?: throw BoardError.Denied
        val now = clock.instant()
        // 규칙: 30초 간격(서버 시각)
        if (user.lastPostAt != null && Duration.between(user.lastPostAt, now).seconds <= BoardLimits.POST_GAP_SECONDS) throw BoardError.Denied
        if (post.nickname != user.nickname) throw BoardError.Denied
        if (post.media.isNotEmpty() && !config.mediaEnabled) throw BoardError.Denied
        if (checkLength(post.title, BoardLimits.TITLE) != null || checkLength(post.body, BoardLimits.BODY) != null) throw BoardError.Denied
        posts[post.id] = BoardPost(
            id = post.id, kind = post.kind, title = post.title, body = post.body, country = post.country, authorUid = uid,
            nickname = post.nickname, createdAt = now, keywords = post.keywords, media = post.media,
        )
        users[uid] = user.copy(lastPostAt = now, postCount = user.postCount + 1)
    }

    override suspend fun editPost(postId: String, title: String, body: String, country: String?, keywords: List<String>) {
        val p = posts[postId] ?: throw BoardError.NotFound
        if (p.authorUid != me() || p.deleted) throw BoardError.Denied
        posts[postId] = p.copy(title = title, body = body, country = country, keywords = keywords, updatedAt = clock.instant())
    }

    override suspend fun deletePost(post: BoardPost) {
        val p = posts[post.id] ?: throw BoardError.NotFound
        if (p.authorUid != me()) throw BoardError.Denied
        if (p.commentCount == 0) {
            posts.remove(post.id)
        } else {
            posts[post.id] = p.copy(deleted = true, title = "", body = "", country = null, keywords = emptyList(), media = emptyList(), nickname = "")
        }
    }

    override suspend fun setPostLike(postId: String, uid: String, on: Boolean) {
        online()
        val p = posts[postId] ?: throw BoardError.NotFound
        val key = postId to uid
        if (on) {
            if (key in likes || p.authorUid == uid || p.deleted) throw BoardError.Denied
            likes += key
        } else {
            if (key !in likes) throw BoardError.Denied
            likes -= key
        }
        posts[postId] = p.copy(likeCount = p.likeCount + if (on) 1 else -1)
    }

    override suspend fun setCommentLike(postId: String, commentId: String, uid: String, on: Boolean) {
        val list = comments[postId] ?: throw BoardError.NotFound
        val i = list.indexOfFirst { it.id == commentId }
        val c = list[i]
        val key = Triple(postId, commentId, uid)
        if (on) {
            if (key in commentLikes || c.authorUid == uid) throw BoardError.Denied
            commentLikes += key
        } else {
            if (key !in commentLikes) throw BoardError.Denied
            commentLikes -= key
        }
        list[i] = c.copy(likeCount = c.likeCount + if (on) 1 else -1)
    }

    override suspend fun report(postId: String, commentId: String?, uid: String, reason: ReportReason) {
        val p = posts[postId] ?: throw BoardError.NotFound
        val target = reports.getOrPut(postId to commentId) { mutableMapOf() }
        if (uid in target) throw BoardError.Denied
        if (commentId == null) {
            if (p.authorUid == uid) throw BoardError.Denied
            posts[postId] = p.copy(reportCount = p.reportCount + 1)
        } else {
            val list = comments.getValue(postId)
            val i = list.indexOfFirst { it.id == commentId }
            if (list[i].authorUid == uid) throw BoardError.Denied
            list[i] = list[i].copy(reportCount = list[i].reportCount + 1)
        }
        target[uid] = reason
    }

    override suspend fun addComment(uid: String, comment: NewComment, notify: List<String>): String {
        online()
        if (uid != me()) throw BoardError.Denied
        val user = users[uid] ?: throw BoardError.Denied
        val now = clock.instant()
        if (user.lastCommentAt != null && Duration.between(user.lastCommentAt, now).seconds <= BoardLimits.COMMENT_GAP_SECONDS) throw BoardError.Denied
        val p = posts[comment.postId] ?: throw BoardError.NotFound
        if (p.hidden || p.deleted) throw BoardError.Denied
        val list = comments.getOrPut(comment.postId) { mutableListOf() }
        // 규칙: 답글은 한 단계 — 부모는 맨 위 댓글
        comment.parentId?.let { pid -> if (list.first { it.id == pid }.parentId != null) throw BoardError.Denied }
        // 규칙: 알림은 글쓴이·답글 대상만, 나는 빼고
        if (notify.any { it != comment.postAuthorUid && it != comment.replyToUid } || uid in notify) throw BoardError.Denied
        val id = "c${++seq}"
        list += BoardComment(
            id = id, postId = comment.postId, postAuthorUid = comment.postAuthorUid, authorUid = uid, nickname = user.nickname,
            body = comment.body, createdAt = now, parentId = comment.parentId, replyToId = comment.replyToId,
            replyToUid = comment.replyToUid, replyToNick = comment.replyToNick,
        )
        notifyLog += id to notify
        posts[p.id] = p.copy(commentCount = p.commentCount + 1, lastCommentAt = now)
        users[uid] = user.copy(lastCommentAt = now)
        return id
    }

    override suspend fun editComment(postId: String, commentId: String, body: String) {
        val list = comments.getValue(postId)
        val i = list.indexOfFirst { it.id == commentId }
        if (list[i].authorUid != me()) throw BoardError.Denied
        list[i] = list[i].copy(body = body, edited = true, updatedAt = clock.instant())
    }

    override suspend fun deleteComment(postId: String, commentId: String) {
        val list = comments.getValue(postId)
        val i = list.indexOfFirst { it.id == commentId }
        if (list[i].authorUid != me()) throw BoardError.Denied
        list[i] = list[i].copy(deleted = true, body = "", nickname = "")
    }

    override suspend fun accept(postId: String, commentId: String?) {
        val p = posts.getValue(postId)
        if (p.authorUid != me() || p.kind != BoardKind.Qna) throw BoardError.Denied
        if (commentId != null) {
            val c = comments[postId].orEmpty().firstOrNull { it.id == commentId } ?: throw BoardError.Denied
            if (c.parentId != null || c.authorUid == p.authorUid || c.deleted) throw BoardError.Denied
        }
        posts[postId] = p.copy(solved = commentId != null, acceptedId = commentId)
    }

    private fun admin() {
        if (uid !in admins) throw BoardError.Denied
    }

    override suspend fun setHidden(postId: String, commentId: String?, hidden: Boolean) {
        admin()
        if (commentId == null) {
            posts[postId] = posts.getValue(postId).copy(hidden = hidden)
        } else {
            val list = comments.getValue(postId)
            val i = list.indexOfFirst { it.id == commentId }
            list[i] = list[i].copy(hidden = hidden)
        }
    }

    override suspend fun setPinned(postId: String, pinned: Boolean) {
        admin()
        posts[postId] = posts.getValue(postId).copy(pinned = pinned)
    }

    override suspend fun hardDelete(postId: String, commentId: String?) {
        admin()
        if (commentId == null) posts.remove(postId) else comments[postId]?.removeAll { it.id == commentId }
    }

    override suspend fun clearReports(postId: String, commentId: String?) {
        admin()
        posts[postId] = posts.getValue(postId).copy(reportCount = 0)
    }

    override suspend fun moderationQueue(): List<ReportedItem> {
        admin()
        return posts.values.filter { it.reportCount > 0 || it.hidden }.map { ReportedItem(it) } +
            comments.values.flatten().filter { it.reportCount > 0 }.map { ReportedItem(posts.getValue(it.postId), it) }
    }

    override suspend fun setMediaEnabled(on: Boolean) {
        admin()
        config = BoardConfig(on)
    }

    override suspend fun repliesFor(uid: String, since: Instant): List<BoardComment> {
        online()
        val ids = notifyLog.filter { uid in it.second }.map { it.first }.toSet()
        return comments.values.flatten().filter { it.id in ids && !it.hidden && it.createdAt.isAfter(since) && it.authorUid != uid }
            .sortedBy { it.createdAt }
    }

    override suspend fun deleteEverything(uid: String) {
        likes.removeAll { (pid, u) -> u == uid && posts[pid]?.let { p -> posts[pid] = p.copy(likeCount = p.likeCount - 1) } != null }
        comments.forEach { (_, list) -> list.replaceAll { if (it.authorUid == uid) it.copy(deleted = true, body = "", nickname = "") else it } }
        posts.values.filter { it.authorUid == uid }.forEach { p ->
            if (p.commentCount == 0) posts.remove(p.id) else posts[p.id] = p.copy(deleted = true, title = "", body = "", nickname = "")
        }
        users.remove(uid)
    }

    override suspend fun uploadMedia(uid: String, postId: String, items: List<PreparedMedia>): List<BoardMediaItem> {
        if (!mediaAvailable) throw BoardError.MediaUnavailable
        uploads += items.size
        return items.mapIndexed { i, m ->
            BoardMediaItem(
                "https://firebasestorage.googleapis.com/v0/b/readyport-app.firebasestorage.app/o/board%2F$uid%2F$postId%2F$i.${m.extension}?alt=media&token=t",
                m.video,
            )
        }
    }

    /** 다른 사람 글 하나 바로 넣기 (테스트 준비) */
    fun seedPost(p: BoardPost) {
        posts[p.id] = p
    }

    fun seedComment(c: BoardComment) {
        comments.getOrPut(c.postId) { mutableListOf() } += c
        notifyLog += c.id to BoardThreads.notifyUids(c.authorUid, c.postAuthorUid, c.replyToUid)
        posts[c.postId]?.let { posts[c.postId] = it.copy(commentCount = it.commentCount + 1) }
    }
}
