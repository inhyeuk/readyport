package com.readyport.plan

import com.readyport.board.BoardRepository
import com.readyport.board.FakeBoardBackend
import com.readyport.board.MemoryBoardLocalStore
import com.readyport.board.TestClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 계획 요청 살림살이: 나이·로그인·7일 2회·취소·삭제·도착 확인 (가짜 서버, 네트워크 없음) */
class PlanRepositoryTest {
    private val clock = TestClock()
    private val boardBackend = FakeBoardBackend(clock)
    private val boardLocal = MemoryBoardLocalStore()
    private val board = BoardRepository(boardBackend, boardLocal, clock)
    private val backend = FakePlanBackend(clock)
    private val local = MemoryPlanLocalStore()
    private val repo = PlanRepository(backend, board, local, clock)

    private val draft = PlanDraft(
        country = "VN",
        dates = PlanDates.Days(3),
        purposes = listOf(PlanPurpose.Nature),
        travelers = PlanTravelers(adults = 2, children = 1),
        budget = BudgetBand.Comfort,
    )

    @Test fun readingNeverCreatesAnAccount() = runBlocking {
        assertEquals(PlanRules.Remaining(2, null), repo.remaining())
        assertTrue(repo.myRequests().isEmpty())
        assertTrue(repo.arrivals().isEmpty())
        assertNull(boardBackend.uid)
        assertEquals(0, backend.reads)
    }

    @Test fun submitSignsInWritesRequestAndQuotaAndRemembersPending() = runBlocking {
        val id = repo.submit(draft, allowWarnings = false)
        val uid = boardBackend.uid!!
        assertEquals(uid, backend.owners[id])
        assertEquals("queued", backend.docs.getValue(id)["status"])
        assertEquals(clock.now, backend.quotas.getValue(uid).last)
        assertNull(backend.quotas.getValue(uid).prev)
        assertEquals(setOf(id), local.pending())
        assertEquals(1, repo.remaining().count)
        assertEquals(1L, repo.revision.value)
    }

    @Test fun twoPerSevenDays() = runBlocking {
        repo.submit(draft, false)
        clock.advance(3600)
        repo.submit(draft, false)
        val left = repo.remaining()
        assertEquals(0, left.count)
        try {
            repo.submit(draft, false)
            fail("7일에 3번째는 막혀야 한다")
        } catch (e: PlanError.QuotaUsed) {
            assertEquals(left.nextAt, e.nextAt)
        }
        // 첫 요청에서 7일이 지나면 다시
        clock.advance(7 * 86_400L)
        assertEquals(1, repo.remaining().count)
        repo.submit(draft, false)
        assertEquals(3, backend.docs.size)
    }

    @Test fun minorsCannotRequestAndNoAccountIsMade() = runBlocking {
        boardLocal.setAgeStamp("minor:2030-01")
        try {
            repo.submit(draft, false)
            fail("미성년은 요청할 수 없다")
        } catch (e: PlanError.AgeRestricted) {
            assertEquals(java.time.YearMonth.of(2030, 1), e.from)
        }
        assertNull(boardBackend.uid)
        assertTrue(backend.docs.isEmpty())
    }

    @Test fun unknownAgeWithPassportOnPhoneAsksToCheck() = runBlocking {
        val withWallet = BoardRepository(boardBackend, MemoryBoardLocalStore(), clock, walletHasData = { true })
        val r = PlanRepository(backend, withWallet, local, clock)
        try {
            r.submit(draft, false)
            fail("나이를 모르면 먼저 확인")
        } catch (e: PlanError.AgeCheckNeeded) {
            // 기대한 대로
        }
    }

    @Test fun invalidDraftOrUnacceptedWarningIsNotSent() = runBlocking {
        try {
            repo.submit(draft.copy(purposes = emptyList()), false)
            fail()
        } catch (e: PlanError.Invalid) {
            // 기대한 대로
        }
        try {
            repo.submit(draft.copy(note = "연락 010-1234-5678"), allowWarnings = false)
            fail()
        } catch (e: PlanError.Invalid) {
            // 기대한 대로
        }
        assertTrue(backend.docs.isEmpty())
        repo.submit(draft.copy(note = "연락 010-1234-5678"), allowWarnings = true)
        assertEquals(1, backend.docs.size)
    }

    @Test fun offlineSubmitFailsClearly() = runBlocking {
        boardBackend.uid = "u1"
        backend.offline = true
        try {
            repo.submit(draft, false)
            fail()
        } catch (e: PlanError.Offline) {
            // 기대한 대로
        }
    }

    @Test fun cancelOnlyWhileWaitingThenDeleteOnlyCancelled() = runBlocking {
        val id = repo.submit(draft, false)
        val req = repo.myRequests().single()
        assertEquals(PlanStatus.Queued, req.status)
        try {
            repo.delete(req)
            fail("대기 중인 요청은 지울 수 없다(규칙)")
        } catch (e: PlanError.Denied) {
            // 기대한 대로
        }
        repo.cancel(req)
        assertEquals(PlanStatus.Cancelled, backend.statuses[id])
        assertTrue(local.pending().isEmpty())
        val cancelled = repo.myRequests().single()
        try {
            repo.cancel(cancelled)
            fail("취소한 요청은 다시 취소할 수 없다")
        } catch (e: PlanError.Denied) {
            // 기대한 대로
        }
        repo.delete(cancelled)
        assertTrue(repo.myRequests().isEmpty())
        // 지워도 횟수는 그대로
        assertEquals(1, repo.remaining().count)
    }

    @Test fun doneRequestsCanBeDeletedButNotCancelled() = runBlocking {
        val id = repo.submit(draft, false)
        backend.statuses[id] = PlanStatus.Done
        val done = repo.myRequests().single()
        assertTrue(!done.status.cancellable && done.status.deletable)
    }

    @Test fun arrivalsReportNewlyDoneOnlyOnce() = runBlocking {
        val a = repo.submit(draft, false)
        clock.advance(60)
        val b = repo.submit(draft, false)
        // 아직 아무것도 끝나지 않음
        assertTrue(repo.arrivals().isEmpty())
        assertEquals(setOf(a, b), local.pending())
        backend.statuses[a] = PlanStatus.Done
        backend.statuses[b] = PlanStatus.Failed
        assertEquals(listOf(a), repo.arrivals())
        assertTrue(local.pending().isEmpty())
        // 두 번 알리지 않는다 — 끝나지 않은 요청이 없으면 서버도 읽지 않는다
        val reads = backend.reads
        assertTrue(repo.arrivals().isEmpty())
        assertEquals(reads, backend.reads)
    }

    @Test fun arrivalsKeepPendingWhenOfflineAndRespectGap() = runBlocking {
        val a = repo.submit(draft, false)
        backend.offline = true
        assertTrue(repo.arrivals().isEmpty())
        assertEquals(setOf(a), local.pending())
        backend.offline = false
        backend.statuses[a] = PlanStatus.Done
        // 방금 확인했으면 10분 안에는 건너뛴다
        assertTrue(repo.arrivals(minGapSeconds = 600).isEmpty())
        clock.advance(601)
        assertEquals(listOf(a), repo.arrivals(minGapSeconds = 600))
    }

    @Test fun listSyncsPendingSoTheScreenDoesNotNotifyAgain() = runBlocking {
        val a = repo.submit(draft, false)
        backend.statuses[a] = PlanStatus.Done
        repo.myRequests()
        assertTrue(local.pending().isEmpty())
        assertTrue(repo.arrivals().isEmpty())
    }

    // ---------------- AI 계획 신고 ----------------

    @Test fun flagWritesOnceAndRemembersFlaggedState() = runBlocking {
        val id = repo.submit(draft, false)
        val uid = boardBackend.uid!!
        backend.resultOwners[id] = uid
        assertTrue(!repo.isFlagged(id))
        repo.flag(id, PlanFlagReason.Unsafe, "  둘째 날 길 안내가 이상해요  ")
        assertEquals(
            mapOf("uid" to uid, "reason" to "unsafe", "note" to "둘째 날 길 안내가 이상해요", "at" to FakePlanBackend.SERVER_TIME),
            backend.flags.getValue(id),
        )
        assertTrue(repo.isFlagged(id))
        // 이미 서버에 있으면(다른 기기·다시 설치) 신고한 것으로 본다 — 오류 없이 '신고함'
        local.setFlagged(emptySet())
        repo.flag(id, PlanFlagReason.Other, "")
        assertTrue(repo.isFlagged(id))
        assertEquals("unsafe", backend.flags.getValue(id)["reason"])
    }

    @Test fun flagNoteIsCheckedBeforeSendingAndFailuresAreNotRemembered() = runBlocking {
        val id = repo.submit(draft, false)
        backend.resultOwners[id] = boardBackend.uid!!
        // 여권 번호처럼 보이는 글자(가짜)·200자 넘는 메모는 보내지 않는다
        for (bad in listOf("여권 M00000000 이에요", "가".repeat(PlanRules.FLAG_NOTE_MAX + 1))) {
            try {
                repo.flag(id, PlanFlagReason.Inaccurate, bad)
                fail("막혀야 한다: $bad")
            } catch (e: PlanError.Invalid) {
                // 기대한 대로
            }
        }
        backend.offline = true
        try {
            repo.flag(id, PlanFlagReason.Inaccurate, "")
            fail("인터넷 없으면 실패")
        } catch (e: PlanError.Offline) {
            // 기대한 대로
        }
        assertTrue(backend.flags.isEmpty())
        assertTrue(!repo.isFlagged(id))
        // 남의 계획은 신고할 수 없다(규칙)
        backend.offline = false
        backend.resultOwners[id] = "someone-else"
        try {
            repo.flag(id, PlanFlagReason.Inaccurate, "")
            fail("남의 계획")
        } catch (e: PlanError.Denied) {
            // 기대한 대로
        }
        assertTrue(!repo.isFlagged(id))
    }

    @Test fun flagNeedsSignInAndDeleteForgetsLocalMarkOnly() = runBlocking {
        try {
            repo.flag("r-none", PlanFlagReason.Other, "")
            fail("로그인한 적이 없으면 신고할 계획도 없다")
        } catch (e: PlanError.Denied) {
            // 기대한 대로
        }
        assertNull(boardBackend.uid)
        val id = repo.submit(draft, false)
        backend.resultOwners[id] = boardBackend.uid!!
        repo.flag(id, PlanFlagReason.Inappropriate, "")
        val req = repo.myRequests().single()
        repo.cancel(req)
        repo.delete(repo.myRequests().single())
        assertTrue(!repo.isFlagged(id))
        // 서버의 신고 기록은 앱이 지우지 않는다(규칙이 막는다) — ARIA 정리 작업이 요청과 함께 지운다
        assertTrue(id in backend.flags)
    }
}
