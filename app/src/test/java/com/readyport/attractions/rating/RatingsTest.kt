package com.readyport.attractions.rating

import com.readyport.attractions.AttTestData
import com.readyport.board.BoardError
import com.readyport.board.BoardRepository
import com.readyport.board.FakeBoardBackend
import com.readyport.board.MemoryBoardLocalStore
import com.readyport.board.TestClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/** 메모리 안 가짜 평점 서버 (네트워크 없음) — 규칙: 한 사람 한 표, 별 1~5, 키 모양 */
class FakeRatingBackend : RatingBackend {
    val votes = mutableMapOf<Pair<String, String>, Int>()
    var statsDoc: Map<String, Any?>? = null
    var flagsDoc: AttractionFlags? = null
    var statsReads = 0
    var offline = false

    override suspend fun myVote(key: String, uid: String): Int? {
        if (offline) throw BoardError.Offline
        return votes[key to uid]
    }

    override suspend fun setVote(key: String, uid: String, stars: Int) {
        if (offline) throw BoardError.Offline
        check(RatingRules.key(key.substringBefore('_'), key.substringAfter('_')) == key)
        check(stars in 1..5)
        votes[key to uid] = stars
    }

    override suspend fun deleteVote(key: String, uid: String) {
        votes.remove(key to uid)
    }

    override suspend fun stats(country: String): Map<String, Any?>? {
        statsReads++
        if (offline) throw BoardError.Offline
        return statsDoc
    }

    override suspend fun flags(country: String): AttractionFlags? = flagsDoc
}

/** 레디포트 평점·확인 중 표시 (가짜 자료만) */
class RatingsTest {
    @Test fun ratingKeyMatchesRule() {
        assertEquals("JP_sensoji", RatingRules.key("JP", "sensoji"))
        assertEquals("JP_fushimi-inari-taisha", RatingRules.key("JP", "fushimi-inari-taisha"))
        assertNull(RatingRules.key("KR", "sensoji"))
        assertNull(RatingRules.key("JP", "Sensoji"))
        assertNull(RatingRules.key("JP", "a--b"))
        assertNull(RatingRules.key("JP", "x".repeat(90)))
    }

    @Test fun votePayloadIsExactlyTheRuleShape() {
        val server = Any()
        val p = RatingRules.votePayload(4, server)
        assertEquals(mapOf("stars" to 4, "at" to server, "visited" to true), p)
        try {
            RatingRules.votePayload(6, server)
            fail()
        } catch (e: IllegalArgumentException) {
            // 기대한 대로
        }
    }

    @Test fun statsAreHiddenBelowFiveOrWhenBroken() {
        val doc = mapOf(
            "sensoji" to mapOf("avg" to 4.26, "n" to 12L),
            "few" to mapOf("avg" to 5.0, "n" to 4L),
            "bad" to mapOf("avg" to 7.0, "n" to 30L),
            "text" to "4.5",
            "_meta" to mapOf("min_n" to 5L),
        )
        assertEquals(RatingStats(4.3, 12), RatingRules.stats(doc, "sensoji"))
        assertNull(RatingRules.stats(doc, "few"))
        assertNull(RatingRules.stats(doc, "bad"))
        assertNull(RatingRules.stats(doc, "text"))
        assertNull(RatingRules.stats(doc, "missing"))
        assertNull(RatingRules.stats(null, "sensoji"))
    }

    private val sensoji = AttTestData.catalog(AttTestData.debugSample()).attraction("sensoji")!!

    @Test fun flagBandShowsForFlaggedIdsUntilTheFileIsReverifiedLater() {
        val verified = sensoji.statusVerified!!
        val flaggedOnVerifyDay = Instant.parse("${verified}T06:00:00Z")
        val flags = AttractionFlags(setOf("sensoji"), AttractionFlags.CHECK_IN_PROGRESS, flaggedOnVerifyDay)
        // 같은 날 확인한 파일이면 띠를 남긴다(안전한 쪽)
        assertTrue(flags.shows(sensoji))
        // 표시를 단 뒤에 다시 확인한 파일이면 숨긴다
        val earlier = flags.copy(at = flaggedOnVerifyDay.minusSeconds(3 * 86_400L))
        assertFalse(earlier.shows(sensoji))
        // 시각이 없으면 띠를 보인다
        assertTrue(flags.copy(at = null).shows(sensoji))
        // 다른 곳·다른 종류는 아니다
        assertFalse(flags.copy(ids = setOf("dotonbori")).shows(sensoji))
        assertFalse(flags.copy(kind = "other").shows(sensoji))
    }

    @Test fun flagsParseLeniently() {
        val f = AttractionFlags.parse(mapOf("ids" to listOf("a", 3, "b"), "kind" to "check_in_progress"), null)!!
        assertEquals(setOf("a", "b"), f.ids)
        assertEquals(emptySet<String>(), AttractionFlags.parse(mapOf("ids" to "a"), null)!!.ids)
        assertNull(AttractionFlags.parse(null, null))
    }

    // ---------------- 저장소 ----------------

    private val clock = TestClock()
    private val boardBackend = FakeBoardBackend(clock)
    private val local = MemoryBoardLocalStore()
    private val board = BoardRepository(boardBackend, local, clock)
    private val backend = FakeRatingBackend()
    private val repo = RatingRepository(backend, board, clock)

    @Test fun readingMyVoteNeverSignsIn() = runBlocking {
        assertEquals(MyVote.None, repo.myVote("JP", "sensoji"))
        assertNull(boardBackend.uid)
    }

    @Test fun voteSignsInOncePerPersonAndCanChangeOrRemove() = runBlocking {
        repo.vote("JP", "sensoji", 5)
        val uid = boardBackend.uid!!
        assertEquals(5, backend.votes["JP_sensoji" to uid])
        repo.vote("JP", "sensoji", 3)
        assertEquals(1, backend.votes.size)
        assertEquals(MyVote.Given(3), repo.myVote("JP", "sensoji"))
        repo.removeVote("JP", "sensoji")
        assertEquals(MyVote.None, repo.myVote("JP", "sensoji"))
    }

    @Test fun minorsCannotVote() = runBlocking {
        local.setAgeStamp("minor:2031-05")
        try {
            repo.vote("JP", "sensoji", 4)
            fail()
        } catch (e: BoardError.AgeRestricted) {
            // 기대한 대로
        }
        assertNull(boardBackend.uid)
        assertTrue(backend.votes.isEmpty())
    }

    @Test fun statsAreCachedForTenMinutesAndOfflineHides() = runBlocking {
        backend.statsDoc = mapOf("sensoji" to mapOf("avg" to 4.0, "n" to 5L))
        assertEquals(RatingStats(4.0, 5), repo.stats("JP", "sensoji"))
        assertEquals(RatingStats(4.0, 5), repo.stats("JP", "sensoji"))
        assertEquals(1, backend.statsReads)
        clock.advance(601)
        backend.offline = true
        assertNull(repo.stats("JP", "sensoji"))
        assertEquals(MyVote.None, repo.myVote("JP", "sensoji"))
    }
}
