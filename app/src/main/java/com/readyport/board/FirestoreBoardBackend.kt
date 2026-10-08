package com.readyport.board

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.readyport.doc.ocr.await
import java.time.Instant
import java.util.Date

/**
 * 운영 백엔드: Firestore(board_posts·board_users·config) + 익명 로그인 + (켜졌을 때만) Storage.
 * 필드 이름·모양은 firebase/firestore.rules 와 짝이다 — 한쪽을 바꾸면 tools/firestore/rules.test.mjs 와 함께 바꾼다.
 */
class FirestoreBoardBackend(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : BoardBackend {

    private val posts get() = db.collection(POSTS)
    private fun postRef(id: String) = posts.document(id)
    private fun commentRef(postId: String, id: String) = postRef(postId).collection(COMMENTS).document(id)
    private fun userRef(uid: String) = db.collection(USERS).document(uid)

    override fun currentUid(): String? = auth.currentUser?.uid

    override suspend fun signIn(): String = guard {
        auth.currentUser?.uid ?: try {
            auth.signInAnonymously().await().user?.uid ?: throw BoardError.AuthUnavailable
        } catch (e: FirebaseAuthException) {
            // ERROR_OPERATION_NOT_ALLOWED = 콘솔에서 익명 로그인이 꺼져 있다 (운영자 할 일 — docs/BOARD.md)
            throw BoardError.AuthUnavailable
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    // ---------------- 읽기 ----------------

    override suspend fun posts(query: BoardQuery, token: String?, after: Any?, limit: Int): BoardPage = guard {
        var q: Query = posts.whereEqualTo("kind", query.kind.id).whereEqualTo("hidden", false)
        query.country?.let { q = q.whereEqualTo("country", it) }
        q = when {
            token != null -> q.whereArrayContains("keywords", token).orderBy("createdAt", Query.Direction.DESCENDING)
            query.sort == BoardSort.Popular -> q.orderBy("score", Query.Direction.DESCENDING).orderBy("createdAt", Query.Direction.DESCENDING)
            query.sort == BoardSort.Waiting -> q.whereEqualTo("solved", false).orderBy("createdAt", Query.Direction.DESCENDING)
            else -> q.orderBy("createdAt", Query.Direction.DESCENDING)
        }
        if (after is DocumentSnapshot) q = q.startAfter(after)
        val snap = q.limit(limit.toLong()).get().await()
        BoardPage(snap.documents.mapNotNull(::toPost), if (snap.size() < limit) null else snap.documents.lastOrNull())
    }

    override suspend fun pinned(kind: BoardKind): List<BoardPost> = guard {
        posts.whereEqualTo("kind", kind.id).whereEqualTo("hidden", false).whereEqualTo("pinned", true)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(PINNED_MAX).get().await()
            .documents.mapNotNull(::toPost)
    }

    override suspend fun post(id: String): BoardPost? = guard {
        try {
            toPost(postRef(id).get().await())
        } catch (e: FirebaseFirestoreException) {
            // 가린 글을 남이 열면 규칙이 거절한다 → 없는 글처럼
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
        }
    }

    override suspend fun comments(postId: String, includeHidden: Boolean): List<BoardComment> = guard {
        var q: Query = postRef(postId).collection(COMMENTS)
        if (!includeHidden) q = q.whereEqualTo("hidden", false)
        q.orderBy("createdAt").limit(COMMENTS_MAX).get().await().documents.mapNotNull(::toComment)
    }

    override suspend fun user(uid: String): BoardUser? = guard {
        val d = userRef(uid).get().await()
        if (!d.exists()) {
            null
        } else {
            BoardUser(
                uid = uid,
                nickname = d.getString("nickname").orEmpty(),
                createdAt = d.instant("createdAt") ?: Instant.EPOCH,
                lastPostAt = d.instant("lastPostAt"),
                lastCommentAt = d.instant("lastCommentAt"),
                postCount = d.getLong("postCount")?.toInt() ?: 0,
            )
        }
    }

    override suspend fun config(): BoardConfig =
        runCatching { BoardConfig(db.collection(CONFIG).document("board").get().await().getBoolean("mediaEnabled") == true) }
            .getOrDefault(BoardConfig())

    override suspend fun admins(): Set<String> = runCatching {
        @Suppress("UNCHECKED_CAST")
        (db.collection(CONFIG).document("admins").get().await().get("uids") as? List<String>).orEmpty().toSet()
    }.getOrDefault(emptySet())

    override suspend fun likedPost(postId: String, uid: String): Boolean =
        runCatching { postRef(postId).collection(LIKES).document(uid).get().await().exists() }.getOrDefault(false)

    override suspend fun likedComments(postId: String, uid: String): Set<String> = runCatching {
        db.collectionGroup(LIKES).whereEqualTo("uid", uid).whereEqualTo("postId", postId).get().await()
            .documents.mapNotNull { d -> d.reference.parent.parent?.takeIf { it.parent.id == COMMENTS }?.id }.toSet()
    }.getOrDefault(emptySet())

    override suspend fun reported(postId: String, commentId: String?, uid: String): Boolean = runCatching {
        val parent = if (commentId == null) postRef(postId) else commentRef(postId, commentId)
        parent.collection(REPORTS).document(uid).get().await().exists()
    }.getOrDefault(false)

    // ---------------- 쓰기 ----------------

    override suspend fun saveNickname(uid: String, nickname: String) = guard {
        val ref = userRef(uid)
        val exists = ref.get().await().exists()
        if (exists) {
            ref.update("nickname", nickname).await()
        } else {
            ref.set(mapOf("nickname" to nickname, "createdAt" to FieldValue.serverTimestamp(), "postCount" to 0)).await()
        }
        Unit
    }

    override fun newPostId(): String = posts.document().id

    override suspend fun createPost(uid: String, post: NewPost) = guard {
        val now = FieldValue.serverTimestamp()
        val batch = db.batch()
        batch.set(
            postRef(post.id),
            mapOf(
                "kind" to post.kind.id,
                "title" to post.title,
                "body" to post.body,
                "country" to post.country.orEmpty(),
                "keywords" to post.keywords,
                "media" to post.media.map { it.url },
                "authorUid" to uid,
                "nickname" to post.nickname,
                "createdAt" to now,
                "updatedAt" to now,
                "lastCommentAt" to now,
                "lastCommentId" to "",
                "commentCount" to 0,
                "likeCount" to 0,
                "reportCount" to 0,
                "score" to 0,
                "solved" to false,
                "acceptedId" to "",
                "pinned" to false,
                "hidden" to false,
                "deleted" to false,
            ),
        )
        batch.update(userRef(uid), mapOf("lastPostAt" to now, "lastPostId" to post.id, "postCount" to FieldValue.increment(1)))
        batch.commit().await()
        Unit
    }

    override suspend fun editPost(postId: String, title: String, body: String, country: String?, keywords: List<String>) = guard {
        postRef(postId).update(
            mapOf(
                "title" to title,
                "body" to body,
                "country" to country.orEmpty(),
                "keywords" to keywords,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        Unit
    }

    override suspend fun deletePost(post: BoardPost) = guard {
        val ref = postRef(post.id)
        if (post.commentCount == 0) {
            ref.delete().await()
        } else {
            ref.update(softDeletedPost()).await()
        }
        Unit
    }

    private fun softDeletedPost() = mapOf(
        "deleted" to true,
        "title" to "",
        "body" to "",
        "country" to "",
        "keywords" to emptyList<String>(),
        "media" to emptyList<String>(),
        "nickname" to "",
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    override suspend fun setPostLike(postId: String, uid: String, on: Boolean) = guard {
        val batch = db.batch()
        likePair(batch, postRef(postId), postRef(postId).collection(LIKES).document(uid), uid, postId, on, withScore = true)
        batch.commit().await()
        Unit
    }

    override suspend fun setCommentLike(postId: String, commentId: String, uid: String, on: Boolean) = guard {
        val batch = db.batch()
        val parent = commentRef(postId, commentId)
        likePair(batch, parent, parent.collection(LIKES).document(uid), uid, postId, on, withScore = false)
        batch.commit().await()
        Unit
    }

    /** 추천 문서 + 추천 수(글이면 인기 점수도) — 규칙이 둘을 짝으로 본다 */
    private fun likePair(batch: WriteBatch, parent: DocumentReference, like: DocumentReference, uid: String, postId: String, on: Boolean, withScore: Boolean) {
        val step = if (on) 1L else -1L
        if (on) {
            batch.set(like, mapOf("uid" to uid, "postId" to postId, "at" to FieldValue.serverTimestamp()))
        } else {
            batch.delete(like)
        }
        val counters = buildMap<String, Any> {
            put("likeCount", FieldValue.increment(step))
            if (withScore) put("score", FieldValue.increment(step))
        }
        batch.update(parent, counters)
    }

    override suspend fun report(postId: String, commentId: String?, uid: String, reason: ReportReason) = guard {
        val parent = if (commentId == null) postRef(postId) else commentRef(postId, commentId)
        val batch = db.batch()
        batch.set(parent.collection(REPORTS).document(uid), mapOf("uid" to uid, "reason" to reason.id, "at" to FieldValue.serverTimestamp()))
        batch.update(parent, "reportCount", FieldValue.increment(1))
        batch.commit().await()
        Unit
    }

    override suspend fun addComment(uid: String, comment: NewComment, notify: List<String>): String = guard {
        val post = postRef(comment.postId)
        val ref = post.collection(COMMENTS).document()
        val now = FieldValue.serverTimestamp()
        val batch = db.batch()
        batch.set(
            ref,
            mapOf(
                "postId" to comment.postId,
                "postAuthorUid" to comment.postAuthorUid,
                "authorUid" to uid,
                "nickname" to comment.nickname,
                "body" to comment.body,
                "parentId" to comment.parentId.orEmpty(),
                "replyToId" to comment.replyToId.orEmpty(),
                "replyToUid" to comment.replyToUid.orEmpty(),
                "replyToNick" to comment.replyToNick.orEmpty(),
                "notify" to notify,
                "createdAt" to now,
                "updatedAt" to now,
                "edited" to false,
                "likeCount" to 0,
                "reportCount" to 0,
                "deleted" to false,
                "hidden" to false,
            ),
        )
        batch.update(
            post,
            mapOf(
                "commentCount" to FieldValue.increment(1),
                "score" to FieldValue.increment(1),
                "lastCommentAt" to now,
                "lastCommentId" to ref.id,
            ),
        )
        batch.update(userRef(uid), mapOf("lastCommentAt" to now, "lastCommentPost" to comment.postId, "lastCommentId" to ref.id))
        batch.commit().await()
        ref.id
    }

    override suspend fun editComment(postId: String, commentId: String, body: String) = guard {
        commentRef(postId, commentId).update(mapOf("body" to body, "edited" to true, "updatedAt" to FieldValue.serverTimestamp())).await()
        Unit
    }

    override suspend fun deleteComment(postId: String, commentId: String) = guard {
        commentRef(postId, commentId).update(
            mapOf("deleted" to true, "body" to "", "nickname" to "", "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
        Unit
    }

    override suspend fun accept(postId: String, commentId: String?) = guard {
        postRef(postId).update(mapOf("solved" to (commentId != null), "acceptedId" to commentId.orEmpty())).await()
        Unit
    }

    // ---------------- 운영자 ----------------

    override suspend fun setHidden(postId: String, commentId: String?, hidden: Boolean) = guard {
        (if (commentId == null) postRef(postId) else commentRef(postId, commentId)).update("hidden", hidden).await()
        Unit
    }

    override suspend fun setPinned(postId: String, pinned: Boolean) = guard {
        postRef(postId).update("pinned", pinned).await()
        Unit
    }

    override suspend fun hardDelete(postId: String, commentId: String?) = guard {
        (if (commentId == null) postRef(postId) else commentRef(postId, commentId)).delete().await()
        Unit
    }

    override suspend fun clearReports(postId: String, commentId: String?) = guard {
        (if (commentId == null) postRef(postId) else commentRef(postId, commentId)).update("reportCount", 0).await()
        Unit
    }

    override suspend fun moderationQueue(): List<ReportedItem> = guard {
        val reportedPosts = posts.whereGreaterThan("reportCount", 0).orderBy("reportCount", Query.Direction.DESCENDING)
            .limit(QUEUE_MAX).get().await().documents.mapNotNull(::toPost)
        val hiddenPosts = posts.whereEqualTo("hidden", true).limit(QUEUE_MAX).get().await().documents.mapNotNull(::toPost)
        val reportedComments = db.collectionGroup(COMMENTS).whereGreaterThan("reportCount", 0)
            .orderBy("reportCount", Query.Direction.DESCENDING).limit(QUEUE_MAX).get().await().documents.mapNotNull(::toComment)
        val postById = (reportedPosts + hiddenPosts).associateBy { it.id }.toMutableMap()
        val commentItems = reportedComments.mapNotNull { c ->
            val p = postById[c.postId] ?: toPost(postRef(c.postId).get().await())?.also { postById[it.id] = it }
            p?.let { ReportedItem(it, c) }
        }
        ((reportedPosts + hiddenPosts).distinctBy { it.id }.map { ReportedItem(it) } + commentItems)
            .sortedByDescending { it.reportCount }
    }

    override suspend fun setMediaEnabled(on: Boolean) = guard {
        db.collection(CONFIG).document("board").set(mapOf("mediaEnabled" to on, "updatedAt" to FieldValue.serverTimestamp())).await()
        Unit
    }

    // ---------------- 답글 알림·내 기록 ----------------

    override suspend fun repliesFor(uid: String, since: Instant): List<BoardComment> = guard {
        db.collectionGroup(COMMENTS)
            .whereEqualTo("hidden", false)
            .whereArrayContains("notify", uid)
            .whereGreaterThan("createdAt", Timestamp(Date.from(since)))
            .orderBy("createdAt")
            .limit(REPLIES_MAX)
            .get().await()
            .documents.mapNotNull(::toComment)
            .filter { it.authorUid != uid }
    }

    override suspend fun deleteEverything(uid: String) = guard {
        var failures = 0
        suspend fun unit(block: (WriteBatch) -> Unit) {
            val batch = db.batch()
            block(batch)
            if (runCatching { batch.commit().await() }.isFailure) failures++
        }
        // ① 내 추천 (추천 수를 함께 내린다 — 부모가 이미 없어진 추천은 규칙이 지울 수 없어 남는다: 이 ID 하나뿐인 문서)
        db.collectionGroup(LIKES).whereEqualTo("uid", uid).get().await().documents.forEach { like ->
            val parent = like.reference.parent.parent ?: return@forEach
            val isPost = parent.parent.id == POSTS
            unit { b ->
                b.delete(like.reference)
                b.update(parent, buildMap<String, Any> {
                    put("likeCount", FieldValue.increment(-1))
                    if (isPost) put("score", FieldValue.increment(-1))
                })
            }
        }
        // ② 내 댓글: 글자·닉네임을 비운다(묶음 자리는 남는다)
        db.collectionGroup(COMMENTS).whereEqualTo("authorUid", uid).get().await().documents.forEach { c ->
            if (c.getBoolean("deleted") == true) return@forEach
            unit { b -> b.update(c.reference, mapOf("deleted" to true, "body" to "", "nickname" to "", "updatedAt" to FieldValue.serverTimestamp())) }
        }
        // ③ 내 글: 댓글이 없으면 문서째, 있으면 비우기
        posts.whereEqualTo("authorUid", uid).get().await().documents.forEach { p ->
            val count = p.getLong("commentCount") ?: 0
            if (p.getBoolean("deleted") == true && count > 0) return@forEach
            unit { b -> if (count == 0L) b.delete(p.reference) else b.update(p.reference, softDeletedPost()) }
        }
        if (failures > 0) throw BoardError.Denied
        // ④ 이용자 문서 (닉네임·시각)
        userRef(uid).delete().await()
        Unit
    }

    override suspend fun uploadMedia(uid: String, postId: String, items: List<PreparedMedia>): List<BoardMediaItem> {
        val storage = runCatching { FirebaseStorage.getInstance() }.getOrNull() ?: throw BoardError.MediaUnavailable
        return try {
            items.mapIndexed { i, m ->
                val ref = storage.reference.child("board/$uid/$postId/$i.${m.extension}")
                ref.putBytes(m.bytes, StorageMetadata.Builder().setContentType(m.contentType).build()).await()
                BoardMediaItem(ref.downloadUrl.await().toString(), m.video)
            }
        } catch (e: Exception) {
            // Spark 요금제에는 Storage가 없다(2026-02부터 Blaze 필요) — 버킷이 없거나 규칙이 막으면 여기로 온다
            throw BoardError.MediaUnavailable
        }
    }

    // ---------------- 변환 ----------------

    private fun DocumentSnapshot.instant(field: String): Instant? =
        getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.toInstant()

    private fun toPost(d: DocumentSnapshot): BoardPost? {
        if (!d.exists()) return null
        val kind = BoardKind.of(d.getString("kind")) ?: return null
        val created = d.instant("createdAt") ?: Instant.now()

        @Suppress("UNCHECKED_CAST")
        val media = (d.get("media") as? List<String>).orEmpty().map { BoardMediaItem(it, video = it.substringBefore('?').endsWith(".mp4")) }

        @Suppress("UNCHECKED_CAST")
        val keywords = (d.get("keywords") as? List<String>).orEmpty()
        return BoardPost(
            id = d.id,
            kind = kind,
            title = d.getString("title").orEmpty(),
            body = d.getString("body").orEmpty(),
            country = d.getString("country")?.takeIf { it.isNotEmpty() },
            authorUid = d.getString("authorUid").orEmpty(),
            nickname = d.getString("nickname").orEmpty(),
            createdAt = created,
            updatedAt = d.instant("updatedAt") ?: created,
            lastCommentAt = d.instant("lastCommentAt") ?: created,
            commentCount = d.getLong("commentCount")?.toInt() ?: 0,
            likeCount = d.getLong("likeCount")?.toInt() ?: 0,
            reportCount = d.getLong("reportCount")?.toInt() ?: 0,
            solved = d.getBoolean("solved") == true,
            acceptedId = d.getString("acceptedId")?.takeIf { it.isNotEmpty() },
            pinned = d.getBoolean("pinned") == true,
            hidden = d.getBoolean("hidden") == true,
            deleted = d.getBoolean("deleted") == true,
            media = media,
            keywords = keywords,
        )
    }

    private fun toComment(d: DocumentSnapshot): BoardComment? {
        if (!d.exists()) return null
        val created = d.instant("createdAt") ?: Instant.now()
        return BoardComment(
            id = d.id,
            postId = d.getString("postId") ?: d.reference.parent.parent?.id.orEmpty(),
            postAuthorUid = d.getString("postAuthorUid").orEmpty(),
            authorUid = d.getString("authorUid").orEmpty(),
            nickname = d.getString("nickname").orEmpty(),
            body = d.getString("body").orEmpty(),
            createdAt = created,
            parentId = d.getString("parentId")?.takeIf { it.isNotEmpty() },
            replyToId = d.getString("replyToId")?.takeIf { it.isNotEmpty() },
            replyToUid = d.getString("replyToUid")?.takeIf { it.isNotEmpty() },
            replyToNick = d.getString("replyToNick")?.takeIf { it.isNotEmpty() },
            updatedAt = d.instant("updatedAt") ?: created,
            edited = d.getBoolean("edited") == true,
            likeCount = d.getLong("likeCount")?.toInt() ?: 0,
            reportCount = d.getLong("reportCount")?.toInt() ?: 0,
            deleted = d.getBoolean("deleted") == true,
            hidden = d.getBoolean("hidden") == true,
        )
    }

    /** Firestore·네트워크 예외 → [BoardError] */
    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: BoardError) {
        throw e
    } catch (e: FirebaseNetworkException) {
        throw BoardError.Offline
    } catch (e: FirebaseFirestoreException) {
        throw when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> BoardError.Offline
            FirebaseFirestoreException.Code.NOT_FOUND -> BoardError.NotFound
            else -> BoardError.Denied
        }
    }

    companion object {
        const val POSTS = "board_posts"
        const val COMMENTS = "comments"
        const val LIKES = "likes"
        const val REPORTS = "reports"
        const val USERS = "board_users"
        const val CONFIG = "config"
        private const val PINNED_MAX = 5L
        private const val COMMENTS_MAX = 300L
        private const val REPLIES_MAX = 50L
        private const val QUEUE_MAX = 50L
    }
}
