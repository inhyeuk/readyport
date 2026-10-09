package com.readyport.board

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 게시판 쓰기 나이 확인 (만 19세, 본인 여권 생년월일 — 2026-10-09 사장님 결정).
 * 생년월일(가짜 값)은 판정에만 쓰고, 남는 값은 `adult`·`assumed`·`minor:YYYY-MM`뿐인지 본다.
 */
class BoardAgeTest {
    private val today = LocalDate.parse("2026-10-08")

    @Test
    fun `만 19세 생일 당일부터 쓸 수 있다`() {
        assertEquals(BoardAge.ADULT, BoardAge.stamp("2007-10-08", today))
        assertEquals(BoardAge.ADULT, BoardAge.stamp("1980-01-01", today))
    }

    @Test
    fun `생일 하루 전이면 미성년 - 생일 다음 달부터 풀린다`() {
        val s = BoardAge.stamp("2007-10-09", today)
        assertEquals("minor:2026-11", s)
        assertEquals(BoardAge.Status.Minor(YearMonth.parse("2026-11")), BoardAge.status(s, walletHasData = true, today = today))
        assertEquals(BoardAge.Status.Allowed, BoardAge.status(s, walletHasData = true, today = LocalDate.parse("2026-11-01")))
    }

    @Test
    fun `남는 값에 생년월일 날짜가 없다`() {
        val s = BoardAge.stamp("2012-03-17", today)
        assertEquals("minor:2031-04", s)
        assertFalse(s.contains("17"))
    }

    @Test
    fun `여권이 없거나 날짜를 못 읽으면 성인으로 본다`() {
        assertEquals(BoardAge.ASSUMED, BoardAge.stamp(null, today))
        assertEquals(BoardAge.ASSUMED, BoardAge.stamp("not-a-date", today))
        assertEquals(BoardAge.Status.Allowed, BoardAge.status(BoardAge.ASSUMED, walletHasData = true, today = today))
    }

    @Test
    fun `아직 모르면 보관함이 있을 때만 확인 필요`() {
        assertEquals(BoardAge.Status.NeedsCheck, BoardAge.status(null, walletHasData = true, today = today))
        assertEquals(BoardAge.Status.Allowed, BoardAge.status(null, walletHasData = false, today = today))
    }

    // ---------------- 저장소 연결 ----------------

    private val clock = TestClock()
    private val backend = FakeBoardBackend(clock)
    private val local = MemoryBoardLocalStore()
    private var walletHasData = true
    private val repo = BoardRepository(backend, local, clock, walletHasData = { walletHasData })

    @Test
    fun `미성년은 익명 계정도 만들지 않고 쓰기가 막힌다`() = runBlocking {
        repo.recordAge("2010-05-05")
        backend.uid = null
        val r = repo.join("여행자 1234")
        assertTrue(r.exceptionOrNull() is BoardError.AgeRestricted)
        assertEquals(null, backend.currentUid())
    }

    @Test
    fun `나이를 모르면 확인 필요로 막고 여권을 열면 풀린다`() = runBlocking {
        backend.uid = "me"
        assertTrue(repo.join("여행자 1234").exceptionOrNull() is BoardError.AgeCheckNeeded)
        repo.recordAge("1990-02-02")
        assertTrue(repo.join("여행자 1234").isSuccess)
    }

    @Test
    fun `여권을 지워도 이미 남긴 미성년 값은 그대로`() = runBlocking {
        repo.recordAge("2010-05-05")
        repo.recordAge(null)
        assertTrue(repo.ageStatus() is BoardAge.Status.Minor)
    }

    @Test
    fun `여권 없이 열어 본 뒤 여권을 넣으면 다시 계산한다`() = runBlocking {
        repo.recordAge(null)
        assertEquals(BoardAge.Status.Allowed, repo.ageStatus())
        repo.recordAge("2010-05-05")
        assertTrue(repo.ageStatus() is BoardAge.Status.Minor)
    }

    @Test
    fun `미성년은 추천·신고도 못 한다`() = runBlocking {
        backend.uid = "me"
        repo.recordAge("1990-02-02")
        repo.join("여행자 1234").getOrThrow()
        repo.recordAge("2010-05-05")
        val post = BoardPost(
            id = "p1", kind = BoardKind.Qna, title = "질문 하나", body = "본문입니다. 열 글자 넘게.", country = "TH",
            authorUid = "other", nickname = "여행자 1", createdAt = clock.now,
        )
        backend.seedPost(post)
        try {
            repo.setLike(post, true)
            fail("추천이 막히지 않음")
        } catch (e: BoardError.AgeRestricted) {
        }
        try {
            repo.report("p1", null, ReportReason.Minor)
            fail("신고가 막히지 않음")
        } catch (e: BoardError.AgeRestricted) {
        }
    }
}
