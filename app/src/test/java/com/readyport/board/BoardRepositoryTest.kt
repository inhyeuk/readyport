package com.readyport.board

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/**
 * 게시판 살림살이 — 가짜 서버(FakeBoardBackend, 네트워크 없음)로:
 * 쪽 넘기기·정렬·검색 · 채택 · 지우기(자리 남김) · 차단 · 신고 접힘 · 개인정보·욕설 · 간격 · 사진 꺼짐 · 답글 알림 · 내 기록 지우기.
 */
class BoardRepositoryTest {
    private val clock = TestClock()
    private val backend = FakeBoardBackend(clock)
    private val local = MemoryBoardLocalStore()
    private var mediaFlag: Boolean? = null
    private val repo = BoardRepository(backend, local, clock, onMediaEnabled = { mediaFlag = it })

    private fun seed(n: Int, kind: BoardKind = BoardKind.Qna, author: String = "other", start: Instant = clock.now.minusSeconds(100_000)) {
        repeat(n) { i ->
            val title = "질문 $i 태국 입국"
            val body = "본문 $i 입니다. 열 글자 넘게 써요."
            backend.seedPost(
                BoardPost(
                    id = "s$i", kind = kind, title = title, body = body, country = if (i % 2 == 0) "TH" else "JP",
                    authorUid = author, nickname = "여행자 ${1000 + i}", createdAt = start.plusSeconds(i * 60L),
                    likeCount = i % 7, commentCount = i % 3, solved = i % 4 == 0, keywords = BoardKeywords.forPost(title, body),
                ),
            )
        }
    }

    private fun joined(uid: String = "me", nick: String = "여행자 4821") = runBlocking {
        backend.uid = uid
        repo.join(nick).getOrThrow()
    }

    // ---------------- 목록 ----------------

    @Test
    fun pagesOfTwentyUntilTheEnd() = runBlocking {
        seed(45)
        val q = BoardQuery(BoardKind.Qna)
        val first = repo.page(q, null)
        assertEquals(20, first.posts.size)
        assertEquals("s44", first.posts.first().id) // 최신 먼저
        val second = repo.page(q, first.next)
        val third = repo.page(q, second.next)
        assertEquals(20, second.posts.size)
        assertEquals(5, third.posts.size)
        assertNull(third.next)
        assertEquals(45, (first.posts + second.posts + third.posts).map { it.id }.toSet().size)
    }

    @Test
    fun sortPopularAndWaitingAndCountryFilter() = runBlocking {
        seed(12)
        val popular = repo.page(BoardQuery(BoardKind.Qna, BoardSort.Popular), null).posts
        assertEquals(popular.map { it.score }.sortedDescending(), popular.map { it.score })
        val waiting = repo.page(BoardQuery(BoardKind.Qna, BoardSort.Waiting), null).posts
        assertTrue(waiting.isNotEmpty() && waiting.none { it.solved })
        val th = repo.page(BoardQuery(BoardKind.Qna, country = "TH"), null).posts
        assertTrue(th.all { it.country == "TH" } && th.size == 6)
        // 자유 토론 목록에는 Q&A 글이 없다
        assertTrue(repo.page(BoardQuery(BoardKind.Talk), null).posts.isEmpty())
    }

    @Test
    fun searchUsesOneServerTokenThenFiltersAllTokens() = runBlocking {
        seed(3)
        backend.seedPost(
            BoardPost("bus", BoardKind.Qna, "방콕 공항버스 타는 곳", "수완나품 1층 7번 출구 앞이에요.", "TH", "x", "여행자 2000", clock.now,
                keywords = BoardKeywords.forPost("방콕 공항버스 타는 곳", "수완나품 1층 7번 출구 앞이에요.")),
        )
        backend.seedPost(
            BoardPost("cm", BoardKind.Qna, "치앙마이 공항 질문", "공항에서 시내 가는 법이요.", "TH", "x", "여행자 2001", clock.now,
                keywords = BoardKeywords.forPost("치앙마이 공항 질문", "공항에서 시내 가는 법이요.")),
        )
        val found = repo.page(BoardQuery(BoardKind.Qna, search = "방콕 공항버스"), null).posts
        assertEquals(listOf("bus"), found.map { it.id })
    }

    @Test
    fun pinnedPostsAreNotRepeatedInTheList() = runBlocking {
        seed(3)
        backend.posts["s1"] = backend.posts.getValue("s1").copy(pinned = true)
        assertEquals(listOf("s1"), repo.pinned(BoardKind.Qna).map { it.id })
        assertFalse(repo.page(BoardQuery(BoardKind.Qna), null).posts.any { it.id == "s1" })
    }

    @Test
    fun hiddenPostsAreNotListedAndOpenAsGone() = runBlocking {
        seed(2)
        backend.posts["s0"] = backend.posts.getValue("s0").copy(hidden = true)
        assertEquals(listOf("s1"), repo.page(BoardQuery(BoardKind.Qna), null).posts.map { it.id })
        assertNull(repo.detail("s0"))
    }

    // ---------------- 쓰기 ----------------

    @Test
    fun joinValidatesNicknameAndSignsInLazily() = runBlocking {
        assertNull(repo.uid())
        assertTrue(repo.join("레디포트 운영자").isFailure)
        assertNull(backend.uid) // 잘못된 이름이면 로그인도 하지 않는다
        assertTrue(repo.join("여행자 4821").isSuccess)
        assertEquals("여행자 4821", repo.me().nickname)
        assertTrue(repo.ready())
    }

    @Test
    fun readingNeverSignsIn() = runBlocking {
        seed(2)
        repo.page(BoardQuery(BoardKind.Qna), null)
        repo.detail("s0")
        repo.checkReplies()
        assertNull(backend.uid)
    }

    @Test
    fun submitMasksProfanityStoresKeywordsAndBumpsRevision() = runBlocking {
        joined()
        val before = repo.revision.value
        val id = repo.submit(BoardKind.Talk, "방콕 야시장 후기", "씨발 너무 더웠어요. 그래도 야시장은 최고!", "TH")
        val p = backend.posts.getValue(id)
        assertEquals("＊＊ 너무 더웠어요. 그래도 야시장은 최고!", p.body)
        assertTrue("야시" in p.keywords)
        assertEquals("여행자 4821", p.nickname)
        assertEquals(before + 1, repo.revision.value)
    }

    @Test
    fun piiBlocksPassportButLetsEmbassyPhoneThroughWhenAllowed() = runBlocking {
        joined()
        try {
            repo.submit(BoardKind.Qna, "여권 번호 칸 질문", "제 여권이 M12345678 인데 TDAC에 어떻게 써요?", "TH")
            fail("여권 번호는 막아야 한다")
        } catch (e: IllegalArgumentException) {
            assertEquals("draft_blocked", e.message)
        }
        val body = "대사관 번호는 +66-2-247-7537 이에요. 급하면 전화하세요."
        assertTrue(repo.check("대사관 번호 공유", body).warnOnly)
        try {
            repo.submit(BoardKind.Talk, "대사관 번호 공유", body, "TH")
            fail("경고는 확인을 받아야 한다")
        } catch (e: IllegalArgumentException) {
            assertEquals("draft_warnings", e.message)
        }
        val id = repo.submit(BoardKind.Talk, "대사관 번호 공유", body, "TH", allowWarnings = true)
        assertTrue(backend.posts.containsKey(id))
    }

    @Test
    fun lengthLimitsMatchTheRules() {
        assertEquals(FieldProblem.TooShort, repo.check("짧다", "열 글자가 넘는 본문이에요").title)
        assertEquals(FieldProblem.TooLong, repo.check("가".repeat(61), "열 글자가 넘는 본문이에요").title)
        assertEquals(FieldProblem.TooShort, repo.check("제목 네 글자", "짧은 본문").body)
        assertEquals(FieldProblem.TooLong, repo.checkComment("가".repeat(1001)).body)
        assertNull(repo.checkComment("좋아요").body)
    }

    @Test
    fun postRateLimitTellsHowManySecondsToWait() = runBlocking {
        joined()
        repo.submit(BoardKind.Qna, "첫 질문입니다", "첫 질문 본문입니다. 열 글자 넘게.", null)
        clock.advance(12)
        try {
            repo.submit(BoardKind.Qna, "둘째 질문입니다", "둘째 질문 본문입니다. 열 글자 넘게.", null)
            fail("30초 간격")
        } catch (e: BoardError.TooFast) {
            assertEquals(18, e.waitSeconds)
        }
        clock.advance(19)
        repo.submit(BoardKind.Qna, "둘째 질문입니다", "둘째 질문 본문입니다. 열 글자 넘게.", null)
        assertEquals(2, backend.posts.size)
    }

    @Test
    fun commentRateLimitIsTenSeconds() = runBlocking {
        seed(1)
        joined()
        val post = backend.posts.getValue("s0")
        repo.comment(post, "첫 댓글")
        clock.advance(4)
        try {
            repo.comment(backend.posts.getValue("s0"), "둘째 댓글")
            fail("10초 간격")
        } catch (e: BoardError.TooFast) {
            assertEquals(6, e.waitSeconds)
        }
    }

    @Test
    fun authUnavailableIsReportedPlainly() = runBlocking {
        backend.authAvailable = false
        val r = repo.join("여행자 4821")
        assertTrue(r.exceptionOrNull() is BoardError.AuthUnavailable)
    }

    // ---------------- 댓글·답글·채택 ----------------

    @Test
    fun repliesThreadOneLevelAndNotifyTheRightPeople() = runBlocking {
        seed(1, author = "asker")
        joined("alice", "앨리스 여행")
        val post = backend.posts.getValue("s0")
        val c1 = repo.comment(post, "도착 72시간 전부터 내요.")
        // bob이 alice 댓글에 답글
        clock.advance(11)
        backend.uid = "bob"
        repo.join("밥 여행").getOrThrow()
        val r1 = repo.comment(backend.posts.getValue("s0"), "맞아요", target = backend.comments.getValue("s0").first { it.id == c1 })
        // alice가 bob 답글에 답하면 같은 묶음(c1) + @밥 여행
        clock.advance(11)
        backend.uid = "alice"
        val reply = backend.comments.getValue("s0").first { it.id == r1 }
        val r2 = repo.comment(backend.posts.getValue("s0"), "고마워요", target = reply)
        val stored = backend.comments.getValue("s0").first { it.id == r2 }
        assertEquals(c1, stored.parentId)
        assertEquals("밥 여행", stored.replyToNick)
        assertEquals(listOf("asker", "bob"), backend.notifyLog.first { it.first == r2 }.second)
        val threads = BoardThreads.build(backend.comments.getValue("s0"), null)
        assertEquals(1, threads.size)
        assertEquals(listOf(r1, r2), threads.single().replies.map { it.id })
    }

    @Test
    fun askerAcceptsAnAnswerAndPostIsSolved() = runBlocking {
        seed(1, author = "asker")
        backend.posts["s0"] = backend.posts.getValue("s0").copy(solved = false)
        joined("alice", "앨리스 여행")
        val answer = repo.comment(backend.posts.getValue("s0"), "공항 1층이에요.")
        // 남은 채택할 수 없다
        try {
            repo.accept(backend.posts.getValue("s0"), backend.comments.getValue("s0").single())
            fail()
        } catch (e: BoardError) {
            assertEquals(BoardError.Denied, e)
        }
        backend.uid = "asker"
        repo.accept(backend.posts.getValue("s0"), backend.comments.getValue("s0").single())
        val p = backend.posts.getValue("s0")
        assertTrue(p.solved)
        assertEquals(answer, p.acceptedId)
        assertEquals(answer, BoardThreads.build(backend.comments.getValue("s0"), p.acceptedId).first().root.id)
        // 다시 누르면 풀기
        repo.accept(p, null)
        assertFalse(backend.posts.getValue("s0").solved)
    }

    @Test
    fun softDeleteKeepsThreadAndPostWithCommentsIsEmptiedNotRemoved() = runBlocking {
        joined("alice", "앨리스 여행")
        val id = repo.submit(BoardKind.Qna, "지울 질문입니다", "지울 질문 본문이에요. 열 글자.", "JP")
        clock.advance(31)
        backend.uid = "bob"
        repo.join("밥 여행").getOrThrow()
        val c = repo.comment(backend.posts.getValue(id), "답이에요")
        clock.advance(11)
        val reply = repo.comment(backend.posts.getValue(id), "덧붙여요", target = backend.comments.getValue(id).first { it.id == c })
        // bob이 자기 댓글을 지우면 자리만 남고 답글은 그대로
        repo.deleteComment(backend.comments.getValue(id).first { it.id == c })
        val threads = BoardThreads.build(backend.comments.getValue(id), null)
        assertEquals(Shown.Deleted, BoardShow.comment(threads.single().root, emptySet(), emptySet()))
        assertEquals(listOf(reply), threads.single().replies.map { it.id })
        // alice가 글을 지우면 댓글이 있으니 내용만 비운다
        backend.uid = "alice"
        repo.delete(backend.posts.getValue(id))
        val p = backend.posts.getValue(id)
        assertTrue(p.deleted && p.title.isEmpty() && p.body.isEmpty())
        assertEquals(2, backend.comments.getValue(id).size)
    }

    @Test
    fun postWithoutCommentsIsRemovedEntirely() = runBlocking {
        joined()
        val id = repo.submit(BoardKind.Talk, "금방 지울 글이에요", "금방 지울 글 본문이에요.", null)
        repo.delete(backend.posts.getValue(id))
        assertFalse(backend.posts.containsKey(id))
    }

    // ---------------- 추천·신고·차단 ----------------

    @Test
    fun likeOnceAndNotOwnPost() = runBlocking {
        seed(1)
        joined()
        repo.setLike(backend.posts.getValue("s0"), true)
        assertEquals(1, backend.posts.getValue("s0").likeCount)
        try {
            repo.setLike(backend.posts.getValue("s0"), true)
            fail()
        } catch (e: BoardError) {
            assertEquals(BoardError.Denied, e)
        }
        repo.setLike(backend.posts.getValue("s0"), false)
        assertEquals(0, backend.posts.getValue("s0").likeCount)
    }

    @Test
    fun threeReportsFoldThePostOnThisPhoneUntilRevealed() = runBlocking {
        seed(1)
        for (u in listOf("r1", "r2", "r3")) {
            backend.uid = u
            repo.report("s0", null, ReportReason.Spam)
        }
        val p = backend.posts.getValue("s0")
        assertEquals(3, p.reportCount)
        assertEquals(Shown.Reported, BoardShow.post(p, emptySet(), emptySet()))
        assertEquals(Shown.Normal, BoardShow.post(p, emptySet(), setOf("s0")))
        // 같은 사람은 두 번 신고할 수 없다
        try {
            repo.report("s0", null, ReportReason.Abuse)
            fail()
        } catch (e: BoardError) {
            assertEquals(BoardError.Denied, e)
        }
    }

    @Test
    fun blockHidesThatPersonOnThisPhoneOnly() = runBlocking {
        seed(2, author = "troll")
        repo.block("troll")
        val blocked = repo.blocked.first()
        assertEquals(setOf("troll"), blocked)
        val posts = repo.page(BoardQuery(BoardKind.Qna), null).posts
        assertTrue(posts.all { BoardShow.post(it, blocked, emptySet()) == Shown.Blocked })
        // 서버에는 아무것도 보내지 않았다 (글은 그대로)
        assertEquals(2, backend.posts.size)
        repo.unblockAll()
        assertTrue(repo.blocked.first().isEmpty())
    }

    // ---------------- 사진·동영상 ----------------

    @Test
    fun mediaIsRejectedWhileSwitchIsOffAndUploadedWhenOn() = runBlocking {
        joined()
        val photo = PreparedMedia(byteArrayOf(1, 2, 3), video = false)
        repo.refreshConfig()
        assertEquals(false, mediaFlag)
        try {
            repo.submit(BoardKind.Talk, "사진 있는 글이에요", "사진 있는 글 본문이에요.", null, media = listOf(photo))
            fail("꺼져 있으면 사진을 올리지 않는다")
        } catch (e: BoardError) {
            assertEquals(BoardError.MediaUnavailable, e)
        }
        assertEquals(0, backend.uploads)
        // 운영자가 켜면 허용 목록도 켠다
        backend.admins = setOf("me")
        repo.setMediaEnabled(true)
        assertEquals(true, mediaFlag)
        // 저장소가 없으면(Spark) 쉬운 문구로 실패
        try {
            repo.submit(BoardKind.Talk, "사진 있는 글이에요", "사진 있는 글 본문이에요.", null, media = listOf(photo))
            fail()
        } catch (e: BoardError) {
            assertEquals(BoardError.MediaUnavailable, e)
        }
        backend.mediaAvailable = true
        val id = repo.submit(BoardKind.Talk, "사진 있는 글이에요", "사진 있는 글 본문이에요.", null, media = listOf(photo))
        assertEquals(1, backend.posts.getValue(id).media.size)
    }

    // ---------------- 답글 알림 ----------------

    @Test
    fun replyCheckCountsNewRepliesOnceAndSkipsBlocked() = runBlocking {
        joined("asker", "질문자 여행")
        val id = repo.submit(BoardKind.Qna, "알림 받을 질문", "알림 받을 질문 본문이에요.", null)
        // 처음 확인은 기준 시각만 적는다
        assertTrue(repo.checkReplies().isEmpty())
        clock.advance(60)
        backend.seedComment(BoardComment("x1", id, "asker", "bob", "밥 여행", "답이에요", clock.now))
        backend.seedComment(BoardComment("x2", id, "asker", "troll", "트롤", "광고", clock.now))
        repo.block("troll")
        val fresh = repo.checkReplies()
        assertEquals(listOf("x1"), fresh.map { it.id })
        assertEquals(1, repo.unread.first())
        // 다시 확인하면 같은 댓글로 또 알리지 않는다
        assertTrue(repo.checkReplies().isEmpty())
        repo.markRepliesRead()
        assertEquals(0, repo.unread.first())
    }

    // ---------------- 내 기록 지우기 ----------------

    @Test
    fun deleteEverythingRemovesMyStuffSignsOutAndClearsThisPhone() = runBlocking {
        seed(1)
        joined()
        val mine = repo.submit(BoardKind.Talk, "내가 쓴 글이에요", "내가 쓴 글 본문이에요.", null)
        clock.advance(11)
        repo.comment(backend.posts.getValue("s0"), "내 댓글")
        repo.setLike(backend.posts.getValue("s0"), true)
        repo.deleteEverything()
        assertFalse(backend.posts.containsKey(mine))
        assertTrue(backend.comments.getValue("s0").single().deleted)
        assertEquals(0, backend.posts.getValue("s0").likeCount)
        assertFalse(backend.users.containsKey("me"))
        assertNull(backend.uid)
        assertFalse(local.rulesAgreed())
        assertNull(local.nickname())
    }
}
