package com.readyport.notice

import com.google.crypto.tink.subtle.Ed25519Sign
import com.readyport.notice.NoticeTestData.doc
import com.readyport.notice.NoticeTestData.notice
import com.readyport.notice.NoticeTestData.parser
import com.readyport.notice.NoticeTestData.payload
import com.readyport.notice.NoticeTestData.sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * 공지사항 (docs/NOTICES_PUSH.md): 서명 확인 · 안전 검사 · 띄울지 고르기(기간·버전·대상·다시 보지 않기·오늘 하루) ·
 * 앱을 켤 때 하나씩(돌아가며)·긴급은 이어서 · 자녀 폰 모드 · 광고 동의 · 버튼 규칙.
 */
class NoticesTest {

    private val now = Instant.parse("2026-10-08T03:00:00Z") // 12:00 KST
    private val today = LocalDate.of(2026, 10, 8)
    private fun ctx(
        countries: Set<String> = emptySet(),
        versionCode: Int = 8,
        childMode: Boolean = false,
        promoOn: Boolean = false,
        at: Instant = now,
        day: LocalDate = today,
    ) = NoticeContext(at, day, versionCode, countries, childMode, promoOn)

    // ---------------- 서명 ----------------

    @Test fun goodSignatureParses() {
        val p = payload(notice("aaa"))
        val d = parser.parse(p, sign(p))!!
        assertEquals(listOf("aaa"), d.notices.map { it.id })
        assertEquals(Instant.parse("2026-10-08T03:00:00Z"), d.generatedAt)
    }

    @Test fun tamperedPayloadIsRejected() {
        val p = payload(notice("aaa", title = "서비스 점검"))
        // Firestore 콘솔에서 글을 고친 것과 같다 — 서명이 맞지 않아 아무것도 보이지 않는다
        assertNull(parser.parse(p.replace("서비스 점검", "가짜 공지"), sign(p)))
    }

    @Test fun wrongKidOrWrongKeyIsRejected() {
        val p = payload(notice("aaa"))
        assertNull(parser.parse(p, sign(p, kid = "rp-2099-9")))
        val other = Ed25519Sign.KeyPair.newKeyPair()
        assertNull(parser.parse(p, sign(p, key = other.privateKey)))
        assertNull(parser.parse(p, "not a signature"))
    }

    @Test fun unknownSchemaIsRejectedButUnknownTypeOnlySkipsThatNotice() {
        val v2 = payload(notice("aaa"), schema = 2)
        assertNull(parser.parse(v2, sign(v2)))
        val p = payload(notice("aaa"), notice("bbb", type = "popup"), notice("ccc"))
        assertEquals(listOf("aaa", "ccc"), parser.parse(p, sign(p))!!.notices.map { it.id })
    }

    // ---------------- 안전 검사 ----------------

    @Test fun unsafeImagesAndLinksAreDropped() {
        val n = notice(
            "aaa",
            extra = """"images":[{"url":"https://evil.example/a.png","alt_ko":"그림"},""" +
                """{"url":"https://readyport-app.web.app/notices/a.webp","alt_ko":"가을 그림"},""" +
                """{"url":"https://readyport-app.web.app/notices/b.webp","alt_ko":" "}],""" +
                """"link":{"label_ko":"열기","url":"http://play.google.com/store"}""",
        )
        val a = doc(n).notices.single()
        assertEquals(listOf("https://readyport-app.web.app/notices/a.webp"), a.images.map { it.url })
        assertNull(a.link)
        assertTrue(NoticeRules.linkOk("https://play.google.com/store/apps/details?id=com.readyport"))
        assertTrue(NoticeRules.linkOk("https://github.com/inhyeuk/readyport/releases"))
        assertFalse(NoticeRules.linkOk("https://github.com/someone/else"))
        assertFalse(NoticeRules.linkOk("https://user@readyport-app.web.app/"))
        assertFalse(NoticeRules.linkOk("https://readyport-app.web.app:8443/"))
        assertFalse(NoticeRules.imageOk("https://readyport-app.web.app/notices/../packs/x.png"))
    }

    @Test fun badNoticesAreDropped() {
        val long = "가".repeat(501)
        val d = doc(
            notice("aaa", body = long),
            notice("bbb", category = "promo", title = "광고 표시 없는 광고"),
            notice("ccc", category = "promo", type = "urgent"),
            notice("ddd", title = "(광고) 서비스인 척"),
            notice("eee", start = "2026-10-08T09:00:00"), // 시간대 없음
            notice("fff"),
        )
        assertEquals(listOf("fff"), d.notices.map { it.id })
    }

    @Test fun pagesFollowBodyMorePagesAndImages() {
        val n = doc(
            notice(
                "aaa",
                extra = """"more_pages_ko":["둘째 쪽"],"images":[{"url":"https://readyport-app.web.app/notices/1.webp","alt_ko":"그림 하나"},""" +
                    """{"url":"https://readyport-app.web.app/notices/2.webp","alt_ko":"그림 둘"},{"url":"https://readyport-app.web.app/notices/3.webp","alt_ko":"그림 셋"}]""",
            ),
        ).notices.single()
        val pages = n.pages()
        assertEquals(3, pages.size)
        assertEquals("본문 aaa 이에요.", pages[0].text)
        assertEquals("그림 하나", pages[0].image?.altKo)
        assertEquals("둘째 쪽", pages[1].text)
        assertNull(pages[2].text)
        assertEquals("그림 셋", pages[2].image?.altKo)
    }

    // ---------------- 고르기 ----------------

    @Test fun windowVersionAndAudience() {
        val d = doc(
            notice("live"),
            notice("later", start = "2026-10-09T09:00:00+09:00"),
            notice("ended", start = "2026-09-01T09:00:00+09:00", end = "2026-10-08T11:59:00+09:00"),
            notice("newapp", extra = """"min_version_code":9"""),
            notice("oldapp", extra = """"max_version_code":7"""),
            notice("thai", audience = "\"TH\""),
        )
        val ids = { c: NoticeContext -> d.notices.filter { NoticeSelector.eligible(it, c) }.map { it.id }.toSet() }
        assertEquals(setOf("live"), ids(ctx()))
        // 찜했거나 여행 가는 나라가 태국이면 태국 공지도
        assertEquals(setOf("live", "thai"), ids(ctx(countries = setOf("TH"))))
        assertEquals(setOf("live", "newapp"), ids(ctx(versionCode = 9)))
        assertEquals(setOf("live", "oldapp"), ids(ctx(versionCode = 7)))
        // 시작 시각이 되면 보인다
        assertTrue("later" in ids(ctx(at = Instant.parse("2026-10-09T00:00:00Z"))))
    }

    @Test fun dismissedAndTodayOnly() {
        val d = doc(notice("aaa", priority = 5), notice("bbb", priority = 1))
        val a = d.notices.first { it.id == "aaa" }
        val never = NoticeMarks(dismissed = setOf(a.key))
        assertEquals(listOf("bbb"), NoticeSelector.launchQueue(d, ctx(), never).map { it.id })
        // 내용을 고쳐 version을 올리면 다시 보인다
        val bumped = doc(notice("aaa", priority = 5, version = 2), notice("bbb", priority = 1))
        assertEquals(listOf("aaa"), NoticeSelector.launchQueue(bumped, ctx(), never).map { it.id })
        // 오늘 하루 보지 않기: 오늘은 안 보이고, 내일은 다시 보인다
        val today = NoticeSelector.after(NoticeMarks(), a, NoticeChoice.Today, this.today, d.notices)
        assertEquals(listOf("bbb"), NoticeSelector.launchQueue(d, ctx(), today).map { it.id })
        assertEquals(listOf("aaa"), NoticeSelector.launchQueue(d, ctx(day = this.today.plusDays(1)), today.copy(round = emptySet())).map { it.id })
        // 지난 날의 '오늘 하루' 기록은 다음에 적을 때 지운다
        val later = NoticeSelector.after(today, a, NoticeChoice.Close, this.today.plusDays(2), d.notices)
        assertTrue(later.snoozed.isEmpty())
    }

    @Test fun oneNoticePerLaunchTakingTurns() {
        val d = doc(notice("aaa", priority = 9), notice("bbb", priority = 5), notice("ccc", priority = 1))
        var marks = NoticeMarks()
        val shown = mutableListOf<String>()
        repeat(4) {
            val queue = NoticeSelector.launchQueue(d, ctx(), marks)
            assertEquals(1, queue.size) // 한 번 켤 때 하나만
            shown += queue.single().id
            marks = NoticeSelector.after(marks, queue.single(), NoticeChoice.Close, today, NoticeSelector.candidates(d, ctx(), marks))
        }
        // 높은 것부터 차례로, 한 바퀴 돌면 다시 처음부터
        assertEquals(listOf("aaa", "bbb", "ccc", "aaa"), shown)
    }

    @Test fun urgentNoticesChainAndHideOthers() {
        val d = doc(
            notice("normal", priority = 100),
            notice("urgent-a", type = "urgent", priority = 1),
            notice("urgent-b", type = "urgent", priority = 5),
        )
        assertEquals(listOf("urgent-b", "urgent-a"), NoticeSelector.launchQueue(d, ctx(), NoticeMarks()).map { it.id })
        // 긴급 공지는 '확인'만 하면 다음에 켤 때 또 뜬다(다시 보지 않기를 눌러야 그친다)
        val u = d.notices.first { it.id == "urgent-b" }
        val after = NoticeSelector.after(NoticeMarks(), u, NoticeChoice.Close, today, emptyList())
        assertTrue(u.key !in after.dismissed && after.round.isEmpty())
        val never = NoticeSelector.after(after, u, NoticeChoice.Never, today, emptyList())
        assertEquals(listOf("urgent-a"), NoticeSelector.launchQueue(d, ctx(), never).map { it.id })
    }

    @Test fun childModeShowsNothing() {
        val d = doc(notice("aaa", type = "urgent"), notice("bbb"))
        assertTrue(NoticeSelector.launchQueue(d, ctx(childMode = true), NoticeMarks()).isEmpty())
        assertTrue(NoticeSelector.launchQueue(null, ctx(), NoticeMarks()).isEmpty())
    }

    @Test fun promoNeedsConsentToPopUpButStaysInTheList() {
        val d = doc(notice("promo", type = "event", category = "promo", priority = 50), notice("svc"))
        assertEquals(listOf("svc"), NoticeSelector.launchQueue(d, ctx(), NoticeMarks()).map { it.id })
        assertEquals(listOf("promo"), NoticeSelector.launchQueue(d, ctx(promoOn = true), NoticeMarks()).map { it.id })
        assertEquals(setOf("promo", "svc"), NoticeSelector.listed(d, ctx()).map { it.notice.id }.toSet())
    }

    @Test fun guideShowsOnceThenLivesInTheList() {
        val d = doc(notice("guide", type = "guide"))
        val g = d.notices.single()
        assertEquals(listOf("guide"), NoticeSelector.launchQueue(d, ctx(), NoticeMarks()).map { it.id })
        val after = NoticeSelector.after(NoticeMarks(), g, NoticeChoice.Close, today, listOf(g))
        assertTrue(NoticeSelector.launchQueue(d, ctx(), after).isEmpty())
        assertEquals(listOf("guide"), NoticeSelector.listed(d, ctx()).map { it.notice.id })
    }

    @Test fun listShowsCurrentThenPast() {
        val d = doc(
            notice("now-low", priority = 1),
            notice("now-high", priority = 9),
            notice("past", start = "2026-09-01T09:00:00+09:00", end = "2026-10-01T09:00:00+09:00"),
            notice("future", start = "2026-11-01T09:00:00+09:00"),
        )
        val listed = NoticeSelector.listed(d, ctx())
        assertEquals(listOf("now-high", "now-low", "past"), listed.map { it.notice.id })
        assertEquals(listOf(true, true, false), listed.map { it.current })
    }

    // ---------------- 버튼 규칙 (D.well 공지와 같은 뜻) ----------------

    @Test fun urgentMustReachTheLastPage() {
        val first = NoticeButtons.of(NoticeType.Urgent, NoticeMode.Launch, 0, 3)
        assertFalse(first.closable)
        assertFalse(first.dismissRow)
        assertEquals(NoticePrimary.Next, first.primary)
        val last = NoticeButtons.of(NoticeType.Urgent, NoticeMode.Launch, 2, 3)
        assertTrue(last.closable)
        assertTrue(last.dismissRow)
        assertEquals(NoticePrimary.Confirm, last.primary)
        assertTrue(last.previous)
        // 목록에서 다시 볼 때는 언제든 닫힌다
        assertTrue(NoticeButtons.of(NoticeType.Urgent, NoticeMode.Reader, 0, 3).closable)
        assertFalse(NoticeButtons.of(NoticeType.Urgent, NoticeMode.Reader, 2, 3).dismissRow)
    }

    @Test fun normalEventGuideButtons() {
        for (t in listOf(NoticeType.Normal, NoticeType.Event)) {
            val b = NoticeButtons.of(t, NoticeMode.Launch, 0, 1)
            assertTrue(b.closable && b.dismissRow && b.primary == NoticePrimary.Confirm && !b.previous)
        }
        val guide = NoticeButtons.of(NoticeType.Guide, NoticeMode.Launch, 0, 2)
        assertTrue(guide.closable)
        assertFalse(guide.dismissRow)
        assertEquals(NoticePrimary.Next, guide.primary)
    }

    @Test fun realSourceNoticesParseWithTheirOwnRules() {
        // notices/notices.json 의 공지가 앱의 안전 검사를 그대로 지나가는지 (서명은 테스트 키로)
        val source = java.io.File("../notices/notices.json").readText()
        val root = kotlinx.serialization.json.Json.parseToJsonElement(source) as kotlinx.serialization.json.JsonObject
        val items = (root["notices"] as kotlinx.serialization.json.JsonArray).map { it.toString() }
        val d = doc(*items.toTypedArray())
        assertEquals(items.size, d.notices.size)
        assertNotNull(d.notices.firstOrNull())
    }
}
