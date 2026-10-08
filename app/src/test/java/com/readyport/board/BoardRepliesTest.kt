package com.readyport.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** 답글 알림 글: 숫자 + 가장 최근 댓글의 짧은 미리보기(욕설이면 미리보기 없이) */
class BoardRepliesTest {
    private val t0 = Instant.parse("2026-10-08T00:00:00Z")
    private fun c(id: String, body: String, sec: Long, nick: String = "바다 고양이") =
        BoardComment(id, "p1", "me", "u-$id", nick, body, t0.plusSeconds(sec))

    @Test
    fun countAndPreviewOfLatest() {
        val t = BoardReplyText.of(listOf(c("a", "첫 답이에요", 10), c("b", "도착 3일 전부터 낼 수 있어요", 20)))!!
        assertEquals(2, t.count)
        assertEquals("바다 고양이: 도착 3일 전부터 낼 수 있어요", t.preview)
        assertEquals("p1", t.postId)
    }

    @Test
    fun longPreviewIsShortened() {
        val long = "가".repeat(100)
        val p = BoardReplyText.of(listOf(c("a", long, 1)))!!.preview!!
        assertTrue(p.endsWith("…"))
        assertTrue(p.length <= "바다 고양이: ".length + BoardReplyText.PREVIEW_MAX)
    }

    @Test
    fun profanityMeansNoPreview() {
        assertNull(BoardReplyText.of(listOf(c("a", "씨발 뭐래", 1)))!!.preview)
        assertNull(BoardReplyText.of(listOf(c("a", "좋아요", 1, nick = "병신여행")))!!.preview)
        assertNull(BoardReplyText.of(emptyList()))
    }
}
