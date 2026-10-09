package com.readyport.board

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant

/**
 * 이 휴대폰에만 두는 게시판 기록 (DataStore `board` — 백업 제외 규칙 그대로). 서버로 보내지 않는다.
 * - 규칙에 동의했는지, 내 닉네임 사본, **차단한 사람**(게시판 ID — 이 휴대폰에서만 가린다), 답글을 마지막으로 본 시각, 안 읽은 답글 수
 */
interface BoardLocalStore {
    val blocked: Flow<Set<String>>
    val unread: Flow<Int>
    suspend fun rulesAgreed(): Boolean
    suspend fun setRulesAgreed(agreed: Boolean)
    suspend fun nickname(): String?
    suspend fun setNickname(nickname: String?)
    suspend fun block(uid: String)
    suspend fun unblockAll()
    suspend fun lastReplyCheck(): Instant?
    suspend fun setLastReplyCheck(at: Instant)
    suspend fun setUnread(count: Int)

    /** 게시판 쓰기 나이 확인 값([BoardAge.stamp]). 생년월일은 남기지 않는다. 기록 지우기로도 지우지 않는다 */
    suspend fun ageStamp(): String?
    suspend fun setAgeStamp(stamp: String)

    /** 내 게시판 기록 모두 지우기 뒤: 이 휴대폰의 게시판 기록도 처음으로(차단 목록은 남긴다 — 내 안전 설정) */
    suspend fun clearAccount()
}

/** 메모리 안 사본 (테스트·갤러리) */
class MemoryBoardLocalStore(
    rules: Boolean = false,
    nickname: String? = null,
    blocked: Set<String> = emptySet(),
) : BoardLocalStore {
    private val rulesFlow = MutableStateFlow(rules)
    private val nick = MutableStateFlow(nickname)
    private val blockedFlow = MutableStateFlow(blocked)
    private val unreadFlow = MutableStateFlow(0)
    private var lastCheck: Instant? = null
    private var age: String? = null
    override val blocked: Flow<Set<String>> = blockedFlow
    override val unread: Flow<Int> = unreadFlow
    override suspend fun rulesAgreed() = rulesFlow.value
    override suspend fun setRulesAgreed(agreed: Boolean) { rulesFlow.value = agreed }
    override suspend fun nickname() = nick.value
    override suspend fun setNickname(nickname: String?) { nick.value = nickname }
    override suspend fun block(uid: String) { blockedFlow.value = blockedFlow.value + uid }
    override suspend fun unblockAll() { blockedFlow.value = emptySet() }
    override suspend fun lastReplyCheck() = lastCheck
    override suspend fun setLastReplyCheck(at: Instant) { lastCheck = at }
    override suspend fun setUnread(count: Int) { unreadFlow.value = count }
    override suspend fun ageStamp() = age
    override suspend fun setAgeStamp(stamp: String) { age = stamp }
    override suspend fun clearAccount() {
        rulesFlow.value = false
        nick.value = null
        lastCheck = null
        unreadFlow.value = 0
    }
}

/** 나 (게시판 안에서) */
data class BoardMe(val uid: String?, val nickname: String?, val admin: Boolean) {
    val joined: Boolean get() = uid != null && nickname != null
}

/** 글 화면 한 번에 필요한 것 */
data class PostDetail(
    val post: BoardPost,
    val comments: List<BoardComment>,
    val liked: Boolean,
    val likedComments: Set<String>,
    val reported: Boolean,
)

/** 올리기 전 검사 (화면이 문구로 바꾼다) */
data class DraftCheck(
    val title: FieldProblem? = null,
    val body: FieldProblem? = null,
    val pii: List<PiiHit> = emptyList(),
    val profanity: Boolean = false,
) {
    /** 고치지 않으면 올릴 수 없는가 (길이·여권/주민번호/MRZ) */
    val blocking: Boolean get() = title != null || body != null || PiiGuard.blocking(pii)

    /** 경고만 있는가 (전화·이메일) — `이대로 올리기`로 넘길 수 있다 */
    val warnOnly: Boolean get() = !blocking && pii.isNotEmpty()
}

/**
 * 게시판 살림살이: 서버([BoardBackend]) + 이 휴대폰 기록([BoardLocalStore]) + 올리기 전 검사(길이·개인정보·욕설·간격).
 * 화면(ViewModel)은 이것만 부른다. 로그인은 **처음으로 쓰기·추천·신고를 할 때만**([ensureSignedIn]) — 읽기는 로그인 없이.
 */
class BoardRepository(
    private val backend: BoardBackend,
    private val local: BoardLocalStore,
    private val clock: Clock = Clock.systemUTC(),
    /** 사진·동영상 스위치가 바뀌면(운영 설정을 읽을 때마다) 알린다 — 그림 불러오기 허용 목록을 맞춘다 */
    private val onMediaEnabled: (Boolean) -> Unit = {},
    /** 여권 보관함 파일이 있는지 — 나이를 아직 모를 때 '확인 필요'인지 '성인으로 봄'인지 가른다 */
    private val walletHasData: () -> Boolean = { false },
) {
    private val lock = Mutex()
    private val _config = MutableStateFlow(BoardConfig())

    /** 마지막으로 읽은 운영 설정 (기본: 사진·동영상 꺼짐) */
    val config: StateFlow<BoardConfig> = _config.asStateFlow()
    private var admins: Set<String>? = null

    private val _revision = MutableStateFlow(0L)

    /** 내가 무언가를 쓰거나 지울 때마다 1씩 오른다 — 목록 화면이 이때만 다시 불러온다(읽기 횟수 절약, 무료 한도) */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private fun bump() = _revision.update { it + 1 }

    val blocked: Flow<Set<String>> get() = local.blocked
    val unread: Flow<Int> get() = local.unread

    fun uid(): String? = backend.currentUid()

    suspend fun rulesAgreed(): Boolean = local.rulesAgreed()

    suspend fun agreeRules() = local.setRulesAgreed(true)

    /** 운영 설정·운영자 목록을 새로 읽는다 (게시판을 열 때) */
    suspend fun refreshConfig(): BoardConfig {
        val c = backend.config()
        _config.value = c
        onMediaEnabled(c.mediaEnabled)
        admins = backend.admins()
        return c
    }

    /** 운영자 게시판 ID (글 카드의 `운영자` 표시) */
    suspend fun adminSet(): Set<String> = admins ?: runCatching { backend.admins() }.getOrDefault(emptySet()).also { admins = it }

    /** 나: 로그인 안 했으면 uid·닉네임 null. 닉네임은 서버 문서가 원본, 없으면 이 휴대폰 사본 */
    suspend fun me(): BoardMe {
        val uid = backend.currentUid() ?: return BoardMe(null, null, false)
        val nickname = runCatching { backend.user(uid)?.nickname }.getOrNull() ?: local.nickname()
        val adminSet = admins ?: runCatching { backend.admins() }.getOrDefault(emptySet()).also { admins = it }
        return BoardMe(uid, nickname, uid in adminSet)
    }

    suspend fun ensureSignedIn(): String = backend.currentUid() ?: backend.signIn()

    // ---------------- 나이 확인 (만 19세, 여권 생년월일 — BoardAge) ----------------

    /** 지금 쓸 수 있는지 */
    suspend fun ageStatus(): BoardAge.Status =
        BoardAge.status(local.ageStamp(), walletHasData(), java.time.LocalDate.now(clock.withZone(java.time.ZoneId.systemDefault())))

    /**
     * 보관함을 열 때마다 부른다(앱이 보관함 상태를 지켜본다). [birthDate] = 본인 여권 생년월일, 없으면 null.
     * 여권을 지워도(여행 뒤 자동 삭제 포함) 이미 남긴 미성년 값은 풀리는 달까지 그대로 둔다.
     */
    suspend fun recordAge(birthDate: String?) {
        val today = java.time.LocalDate.now(clock.withZone(java.time.ZoneId.systemDefault()))
        val next = BoardAge.stamp(birthDate, today)
        if (birthDate == null && local.ageStamp() != null) return
        if (local.ageStamp() != next) local.setAgeStamp(next)
    }

    /** 쓰기·추천·신고·채택 전에: 나이를 확인하고 로그인한다. 미성년이면 [BoardError.AgeRestricted] */
    private suspend fun writer(): String {
        requireWriter()
        return ensureSignedIn()
    }

    /**
     * 게시판 밖에서 같은 기준을 쓰는 기능(여행 계획 요청·관광지 별점, 사장님 결정 2026-10-09): 만 19세 확인 → 익명 로그인.
     * 닉네임·규칙 동의는 필요 없다(공개 글이 아니다). 미성년이면 [BoardError.AgeRestricted] — 익명 계정도 만들지 않는다.
     */
    suspend fun adultUid(): String = writer()

    private suspend fun requireWriter() {
        when (val s = ageStatus()) {
            BoardAge.Status.Allowed -> Unit
            is BoardAge.Status.Minor -> throw BoardError.AgeRestricted(s.from)
            BoardAge.Status.NeedsCheck -> throw BoardError.AgeCheckNeeded
        }
    }

    /** 닉네임 정하기(처음이면 익명 로그인 + 이용자 문서). 규칙에 동의한 뒤에만 */
    suspend fun join(nickname: String): Result<Unit> = runCatching {
        val n = nickname.trim()
        NicknameRules.validate(n)?.let { throw IllegalArgumentException(it.name) }
        // 미성년이면 익명 계정도 만들지 않는다(서버에 기록이 생기지 않게)
        val uid = writer()
        backend.saveNickname(uid, n)
        local.setNickname(n)
        local.setRulesAgreed(true)
        bump()
    }

    // ---------------- 목록 ----------------

    /**
     * 목록 한 쪽. 검색이면 서버에는 조각 하나만 묻고(array-contains) 나머지 조각은 여기서 거른다(Firestore 전문 검색 없음).
     * `답변 기다려요`는 지운 질문을 뺀다. 고정 글은 따로 맨 위에 보이므로 목록에서 뺀다.
     */
    suspend fun page(query: BoardQuery, after: Any?): BoardPage {
        val plan = query.search?.let(BoardKeywords::query)
        val raw = backend.posts(query, plan?.server, after, BoardLimits.PAGE_SIZE)
        val posts = raw.posts.filter { p ->
            !p.pinned && (plan == null || BoardKeywords.matches(p, plan)) &&
                !(query.sort == BoardSort.Waiting && p.deleted)
        }
        return BoardPage(posts, raw.next)
    }

    suspend fun pinned(kind: BoardKind): List<BoardPost> = backend.pinned(kind)

    suspend fun detail(postId: String, includeHidden: Boolean = false): PostDetail? {
        val post = backend.post(postId) ?: return null
        val comments = backend.comments(postId, includeHidden)
        val uid = backend.currentUid()
        return PostDetail(
            post = post,
            comments = comments,
            liked = uid != null && backend.likedPost(postId, uid),
            likedComments = uid?.let { backend.likedComments(postId, it) }.orEmpty(),
            reported = uid != null && backend.reported(postId, null, uid),
        )
    }

    // ---------------- 쓰기 ----------------

    /** 올리기 전 검사 (화면이 글자를 바꿀 때마다) */
    fun check(title: String, body: String): DraftCheck = DraftCheck(
        title = checkLength(title, BoardLimits.TITLE),
        body = checkLength(body, BoardLimits.BODY),
        pii = PiiGuard.scan("$title\n$body"),
        profanity = Profanity.contains(title) || Profanity.contains(body),
    )

    /** 댓글 검사 */
    fun checkComment(body: String): DraftCheck = DraftCheck(
        body = checkLength(body, BoardLimits.COMMENT),
        pii = PiiGuard.scan(body),
        profanity = Profanity.contains(body),
    )

    /** 지금 글을 올릴 수 있을 때까지 남은 초 (서버 규칙과 같은 30초 — 기기 시계 기준 미리 알려 주기) */
    suspend fun postWait(uid: String): Long =
        BoardTime.waitSeconds(clock.instant(), runCatching { backend.user(uid)?.lastPostAt }.getOrNull(), BoardLimits.POST_GAP_SECONDS)

    suspend fun commentWait(uid: String): Long =
        BoardTime.waitSeconds(clock.instant(), runCatching { backend.user(uid)?.lastCommentAt }.getOrNull(), BoardLimits.COMMENT_GAP_SECONDS)

    /**
     * 새 글. 길이·개인정보(여권·주민번호·MRZ는 막고, 전화·이메일은 [allowWarnings]로 넘긴다)·간격을 보고, 욕설은 `＊`로 가려 올린다.
     * [media]: 사진·동영상(꺼져 있으면 무시하지 않고 거절 — 화면은 꺼져 있으면 붙이는 버튼을 보이지 않는다)
     * @return 새 글 id
     */
    suspend fun submit(
        kind: BoardKind,
        title: String,
        body: String,
        country: String?,
        allowWarnings: Boolean = false,
        media: List<PreparedMedia> = emptyList(),
    ): String = lock.withLock {
        val check = check(title, body)
        require(!check.blocking) { "draft_blocked" }
        require(!check.warnOnly || allowWarnings) { "draft_warnings" }
        require(BoardCountries.valid(country)) { "country" }
        val uid = writer()
        val nickname = backend.user(uid)?.nickname ?: throw BoardError.Denied
        val wait = postWait(uid)
        if (wait > 0) throw BoardError.TooFast(wait)
        if (media.isNotEmpty() && !config.value.mediaEnabled) throw BoardError.MediaUnavailable
        val cleanTitle = Profanity.mask(title.trim())
        val cleanBody = Profanity.mask(body.trim())
        val id = backend.newPostId()
        val uploaded = if (media.isEmpty()) emptyList() else backend.uploadMedia(uid, id, media)
        backend.createPost(
            uid,
            NewPost(
                id = id, kind = kind, title = cleanTitle, body = cleanBody, country = country, nickname = nickname,
                keywords = BoardKeywords.forPost(cleanTitle, cleanBody), media = uploaded,
            ),
        )
        bump()
        id
    }

    suspend fun edit(post: BoardPost, title: String, body: String, country: String?, allowWarnings: Boolean = false) {
        val check = check(title, body)
        require(!check.blocking) { "draft_blocked" }
        require(!check.warnOnly || allowWarnings) { "draft_warnings" }
        requireWriter()
        val t = Profanity.mask(title.trim())
        val b = Profanity.mask(body.trim())
        backend.editPost(post.id, t, b, country, BoardKeywords.forPost(t, b))
        bump()
    }

    suspend fun delete(post: BoardPost) {
        backend.deletePost(post)
        bump()
    }

    suspend fun setLike(post: BoardPost, on: Boolean) = backend.setPostLike(post.id, writer(), on)

    suspend fun setCommentLike(comment: BoardComment, on: Boolean) =
        backend.setCommentLike(comment.postId, comment.id, writer(), on)

    suspend fun report(postId: String, commentId: String?, reason: ReportReason) =
        backend.report(postId, commentId, writer(), reason)

    /**
     * 댓글·답글. [target]: 답글이면 누른 댓글(답글의 답글은 같은 묶음 + `@닉네임`).
     * 알림 대상은 글쓴이와 답글 대상(나는 빼고) — 규칙도 이 둘만 받는다.
     */
    suspend fun comment(post: BoardPost, body: String, target: BoardComment? = null, allowWarnings: Boolean = false): String = lock.withLock {
        val check = checkComment(body)
        require(!check.blocking) { "draft_blocked" }
        require(!check.warnOnly || allowWarnings) { "draft_warnings" }
        val uid = writer()
        val nickname = backend.user(uid)?.nickname ?: throw BoardError.Denied
        val wait = commentWait(uid)
        if (wait > 0) throw BoardError.TooFast(wait)
        val reply = target?.let(BoardThreads::replyTarget)
        backend.addComment(
            uid,
            NewComment(
                postId = post.id, postAuthorUid = post.authorUid, body = Profanity.mask(body.trim()), nickname = nickname,
                parentId = reply?.parentId, replyToId = reply?.replyToId, replyToUid = reply?.uid, replyToNick = reply?.nickname,
            ),
            BoardThreads.notifyUids(uid, post.authorUid, reply?.uid),
        ).also { bump() }
    }

    suspend fun editComment(comment: BoardComment, body: String) {
        require(checkComment(body).body == null) { "draft_blocked" }
        requireWriter()
        backend.editComment(comment.postId, comment.id, Profanity.mask(body.trim()))
        bump()
    }

    suspend fun deleteComment(comment: BoardComment) {
        backend.deleteComment(comment.postId, comment.id)
        bump()
    }

    /** 채택(질문한 사람만 — 화면도 질문한 사람에게만 버튼을 보인다). 이미 채택한 답을 다시 누르면 풀기 */
    suspend fun accept(post: BoardPost, comment: BoardComment?) {
        requireWriter()
        backend.accept(post.id, comment?.id)
        bump()
    }

    /** 이 사람의 글·댓글을 이 휴대폰에서 가린다 (서버에는 아무것도 보내지 않는다) */
    suspend fun block(uid: String) = local.block(uid)

    suspend fun unblockAll() = local.unblockAll()

    // ---------------- 운영자 ----------------

    suspend fun setHidden(postId: String, commentId: String?, hidden: Boolean) {
        backend.setHidden(postId, commentId, hidden)
        bump()
    }

    suspend fun setPinned(postId: String, pinned: Boolean) {
        backend.setPinned(postId, pinned)
        bump()
    }

    suspend fun hardDelete(postId: String, commentId: String?) {
        backend.hardDelete(postId, commentId)
        bump()
    }
    suspend fun clearReports(postId: String, commentId: String?) = backend.clearReports(postId, commentId)
    suspend fun moderationQueue(): List<ReportedItem> = backend.moderationQueue()

    suspend fun setMediaEnabled(on: Boolean) {
        backend.setMediaEnabled(on)
        _config.value = BoardConfig(on)
        onMediaEnabled(on)
    }

    // ---------------- 답글 알림 ----------------

    /**
     * 내게 온 새 댓글·답글을 본다(앱 시작·하루 한 번). **로그인한 적이 없으면 아무것도 하지 않는다**(로그인을 만들지 않는다).
     * 처음 볼 때는 기준 시각만 적는다(예전 댓글로 알림이 쏟아지지 않게). 차단한 사람의 것은 센다에서 뺀다.
     * @return 새로 온 댓글(알림에 쓸 것) — 안 읽은 수는 [unread]에 더한다
     */
    suspend fun checkReplies(): List<BoardComment> {
        val uid = backend.currentUid() ?: return emptyList()
        val now = clock.instant()
        val since = local.lastReplyCheck()
        if (since == null) {
            local.setLastReplyCheck(now)
            return emptyList()
        }
        val blockedNow = local.blocked.first()
        val fresh = backend.repliesFor(uid, since).filter { it.authorUid != uid && it.authorUid !in blockedNow && !it.deleted }
        local.setLastReplyCheck(fresh.maxOfOrNull { it.createdAt }?.coerceAtLeast(since) ?: since)
        if (fresh.isNotEmpty()) local.setUnread(local.unread.first() + fresh.size)
        return fresh
    }

    /** 글을 쓸 준비가 됐는지(규칙 동의 + 게시판 이름) — 아니면 쓰기 버튼이 규칙·이름 화면으로 간다 */
    suspend fun ready(): Boolean = local.rulesAgreed() && me().joined

    /** 게시판 탭을 열면 안 읽은 표시를 지운다 */
    suspend fun markRepliesRead() = local.setUnread(0)

    // ---------------- 내 기록 ----------------

    /**
     * 내 게시판 기록 모두 지우기: 내 글(댓글이 없으면 문서째, 있으면 비우기)·내 댓글(비우기)·내 추천·이용자 문서 → 로그아웃.
     * 이 휴대폰의 게시판 기록도 처음으로. 익명 계정이라 지운 뒤에는 예전 글을 내 것으로 되찾을 수 없다.
     */
    suspend fun deleteEverything() {
        val uid = backend.currentUid() ?: run {
            local.clearAccount()
            return
        }
        backend.deleteEverything(uid)
        backend.signOut()
        local.clearAccount()
        bump()
    }

    private fun Instant.coerceAtLeast(other: Instant): Instant = if (isBefore(other)) other else this
}
