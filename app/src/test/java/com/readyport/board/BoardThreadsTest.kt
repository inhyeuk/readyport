package com.readyport.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** 댓글 묶음(한 단계 답글·@닉네임·채택 맨 위)과 보이는 모양(지움·차단·신고 접힘·가림) */
class BoardThreadsTest {
    private val t0 = Instant.parse("2026-10-08T00:00:00Z")

    private fun c(id: String, at: Long, parent: String? = null, author: String = "u-$id", replyTo: String? = null) = BoardComment(
        id = id, postId = "p1", postAuthorUid = "asker", authorUid = author, nickname = "닉$id", body = "본문 $id",
        createdAt = t0.plusSeconds(at), parentId = parent, replyToNick = replyTo,
    )

    @Test
    fun repliesAreOneLevelUnderTheirRootInOrder() {
        val list = listOf(c("a", 10), c("b", 20), c("a2", 30, parent = "a"), c("a1", 15, parent = "a"), c("b1", 40, parent = "b"))
        val threads = BoardThreads.build(list.shuffled(), acceptedId = null)
        assertEquals(listOf("a", "b"), threads.map { it.root.id })
        assertEquals(listOf("a1", "a2"), threads[0].replies.map { it.id })
        assertEquals(listOf("b1"), threads[1].replies.map { it.id })
    }

    @Test
    fun acceptedAnswerThreadMovesToTop() {
        val list = listOf(c("a", 10), c("b", 20), c("c", 30))
        val threads = BoardThreads.build(list, acceptedId = "c")
        assertEquals(listOf("c", "a", "b"), threads.map { it.root.id })
        assertTrue(threads.first().accepted)
        assertEquals(listOf(true, false, false), threads.map { it.accepted })
    }

    @Test
    fun replyToAReplyAttachesToSameThreadWithMention() {
        val root = c("a", 10)
        val reply = c("a1", 20, parent = "a", author = "carol")
        // 답글(a1)에 답하면 부모는 맨 위(a), 알림·@닉네임은 누른 답글(a1)에서
        val target = BoardThreads.replyTarget(reply)
        assertEquals("a", target.parentId)
        assertEquals("a1", target.replyToId)
        assertEquals("carol", target.uid)
        assertEquals("닉a1", target.nickname)
        assertEquals("a", BoardThreads.replyTarget(root).parentId)
    }

    @Test
    fun orphanReplyOfHiddenParentStaysVisibleAsRoot() {
        val threads = BoardThreads.build(listOf(c("a1", 20, parent = "gone")), null)
        assertEquals(listOf("a1"), threads.map { it.root.id })
    }

    @Test
    fun notifyIsPostAuthorAndReplyTargetButNeverMe() {
        assertEquals(listOf("asker", "carol"), BoardThreads.notifyUids("bob", "asker", "carol"))
        assertEquals(listOf("carol"), BoardThreads.notifyUids("asker", "asker", "carol"))
        assertEquals(listOf("asker"), BoardThreads.notifyUids("bob", "asker", "asker"))
        assertEquals(emptyList<String>(), BoardThreads.notifyUids("asker", "asker", null))
    }

    @Test
    fun shownStatesSoftDeleteBlockReportHidden() {
        val base = c("a", 10)
        assertEquals(Shown.Normal, BoardShow.comment(base, emptySet(), emptySet()))
        assertEquals(Shown.Deleted, BoardShow.comment(base.copy(deleted = true, body = ""), emptySet(), emptySet()))
        assertEquals(Shown.Blocked, BoardShow.comment(base, setOf("u-a"), emptySet()))
        // 신고 3번이면 접고, `그래도 보기`를 누르면 보인다
        val reported = base.copy(reportCount = 3)
        assertEquals(Shown.Normal, BoardShow.comment(base.copy(reportCount = 2), emptySet(), emptySet()))
        assertEquals(Shown.Reported, BoardShow.comment(reported, emptySet(), emptySet()))
        assertEquals(Shown.Normal, BoardShow.comment(reported, emptySet(), setOf("a")))
        assertEquals(Shown.Hidden, BoardShow.comment(base.copy(hidden = true), emptySet(), emptySet()))
        // 지운 댓글에도 답글 묶음은 남는다
        val threads = BoardThreads.build(listOf(base.copy(deleted = true, body = ""), c("a1", 20, parent = "a")), null)
        assertEquals(1, threads.size)
        assertEquals(1, threads.single().replies.size)
    }

    @Test
    fun postShownStates() {
        val p = BoardPost("p", BoardKind.Talk, "제목입니다", "본문은 열 글자 이상", null, "bob", "닉", t0)
        assertEquals(Shown.Normal, BoardShow.post(p, emptySet(), emptySet()))
        assertEquals(Shown.Reported, BoardShow.post(p.copy(reportCount = 5), emptySet(), emptySet()))
        assertEquals(Shown.Normal, BoardShow.post(p.copy(reportCount = 5), emptySet(), setOf("p")))
        assertEquals(Shown.Blocked, BoardShow.post(p, setOf("bob"), emptySet()))
        assertEquals(Shown.Deleted, BoardShow.post(p.copy(deleted = true), emptySet(), emptySet()))
    }
}
