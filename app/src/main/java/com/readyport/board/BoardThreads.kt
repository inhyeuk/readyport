package com.readyport.board

// ======================= 댓글 묶음·보이는 모양 (순수 함수 — BoardThreadsTest) =======================

/** 화면에 어떻게 보일지 */
enum class Shown {
    /** 그대로 */
    Normal,

    /** 쓴 사람이 지움 — 자리만 남는다(`삭제된 댓글이에요`) */
    Deleted,

    /** 이 휴대폰에서 차단한 사람 — 자리만 남는다(`차단한 사람의 글이에요`) */
    Blocked,

    /** 신고가 3번 넘게 쌓여 접어 둠 — 눌러서 볼 수 있다 */
    Reported,

    /** 운영자가 가림 — 운영자와 쓴 사람만 이 상태로 본다 */
    Hidden,
}

object BoardShow {
    fun post(p: BoardPost, blocked: Set<String>, revealed: Set<String>): Shown = when {
        p.hidden -> Shown.Hidden
        p.deleted -> Shown.Deleted
        p.authorUid in blocked -> Shown.Blocked
        p.reportCount >= BoardLimits.AUTO_HIDE_REPORTS && p.id !in revealed -> Shown.Reported
        else -> Shown.Normal
    }

    fun comment(c: BoardComment, blocked: Set<String>, revealed: Set<String>): Shown = when {
        c.hidden -> Shown.Hidden
        c.deleted -> Shown.Deleted
        c.authorUid in blocked -> Shown.Blocked
        c.reportCount >= BoardLimits.AUTO_HIDE_REPORTS && c.id !in revealed -> Shown.Reported
        else -> Shown.Normal
    }
}

/** 답글을 붙일 곳 */
data class ReplyTarget(val parentId: String, val replyToId: String, val uid: String, val nickname: String)

/** 댓글 하나와 그 아래 답글(한 단계). [accepted]: 질문한 사람이 채택한 답 */
data class CommentThread(val root: BoardComment, val replies: List<BoardComment>, val accepted: Boolean = false)

object BoardThreads {
    /**
     * 댓글 → 묶음. 맨 위 댓글은 쓴 차례대로, 답글은 그 아래 쓴 차례대로.
     * - 채택한 답의 묶음은 **맨 위**로 올린다(Stack Overflow처럼).
     * - 답글의 답글은 따로 깊어지지 않고 같은 묶음에 붙는다 — 누구에게 한 말인지는 `@닉네임`([BoardComment.replyToNick]).
     * - 부모가 목록에 없는(운영자가 가린) 답글은 맨 위 댓글처럼 보인다(사라지지 않게).
     */
    fun build(comments: List<BoardComment>, acceptedId: String?): List<CommentThread> {
        val sorted = comments.sortedWith(compareBy({ it.createdAt }, { it.id }))
        val ids = sorted.map { it.id }.toSet()
        val roots = sorted.filter { it.parentId == null || it.parentId !in ids }
        val byParent = sorted.filter { it.parentId != null && it.parentId in ids }.groupBy { it.parentId }
        val threads = roots.map { r -> CommentThread(r, byParent[r.id].orEmpty(), accepted = acceptedId != null && r.id == acceptedId) }
        val (top, rest) = threads.partition { it.accepted }
        return top + rest
    }

    /**
     * 답글을 달 때 실제로 붙일 곳: 맨 위 댓글에 답하면 그 댓글, 답글에 답하면 그 답글의 맨 위 댓글(한 단계만).
     * 알림 대상·`@닉네임`은 누른 댓글에서 온다.
     */
    fun replyTarget(target: BoardComment): ReplyTarget =
        ReplyTarget(parentId = target.parentId ?: target.id, replyToId = target.id, uid = target.authorUid, nickname = target.nickname)

    /** 알림 받을 사람: 글쓴이와 답글 대상(자기 자신은 빼고, 겹치면 하나) — 규칙: 최대 2명 */
    fun notifyUids(me: String, postAuthorUid: String, replyToUid: String?): List<String> =
        listOfNotNull(postAuthorUid, replyToUid).filter { it != me && it.isNotBlank() }.distinct()
}
