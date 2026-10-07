package com.readyport.notice

import com.readyport.notice.NoticeTestData.doc
import com.readyport.notice.NoticeTestData.notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 공지 알림 (FCM): 알림 글은 서명된 공지에서만 · 못 받으면 앱 문구 · 설정(공지 알림·광고 동의·밤 동의)·자녀 폰 모드 ·
 * 밤(21~8시) 광고는 아침까지 미루기.
 */
class NoticePushTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val noon = Instant.parse("2026-10-08T03:00:00Z") // 12:00 KST
    private val night = Instant.parse("2026-10-08T13:30:00Z") // 22:30 KST

    private fun ctx(at: Instant = noon, countries: Set<String> = emptySet()) =
        NoticeContext(at, LocalDate.of(2026, 10, 8), 8, countries)

    private val signed = doc(
        notice("svc", title = "서비스 점검 안내", body = "10월 12일 새벽에 잠깐 쉬어요."),
        notice("promo", type = "event", category = "promo", title = "(광고) 가을 여행 혜택"),
        notice("thai", audience = "\"TH\""),
        notice("later", start = "2026-11-01T09:00:00+09:00"),
    )

    private val on = PushPrefs(noticePush = true, promoPush = true)

    private fun decide(
        data: Map<String, String>,
        prefs: PushPrefs = on,
        at: Instant = noon,
        doc: NoticeDoc? = signed,
        dismissed: Set<String> = emptySet(),
        local: ZoneId = seoul,
        countries: Set<String> = emptySet(),
    ) = NoticePushRules.decide(data, doc, prefs, ctx(at, countries), dismissed, local)

    private fun text(o: PushOutcome) = NoticePushRules.text(o as PushOutcome.Show, "새 소식이 있어요", "눌러서 공지사항을 확인해 주세요.", "광고 알림 끄기")

    @Test fun usesSignedTextNotPayloadText() {
        // 메시지에 글을 넣어 보내도(누가 FCM 키를 얻었다고 해도) 앱은 서명된 공지 글만 쓴다
        val o = decide(mapOf("type" to "notice", "id" to "svc", "title" to "가짜 제목", "body" to "가짜 본문 http://evil.example"))
        val t = text(o)
        assertEquals("서비스 점검 안내", t.title)
        assertEquals("10월 12일 새벽에 잠깐 쉬어요.", t.body)
        assertEquals("svc", t.noticeId)
    }

    @Test fun genericTextWhenSignedNoticeIsMissing() {
        val noDoc = text(decide(mapOf("type" to "notice", "id" to "svc", "title" to "가짜 제목"), doc = null))
        assertEquals("새 소식이 있어요", noDoc.title)
        assertNull(noDoc.noticeId) // 누르면 공지사항 목록
        val unknown = text(decide(mapOf("type" to "notice", "id" to "not-yet")))
        assertEquals("새 소식이 있어요", unknown.title)
    }

    @Test fun settingsAndChildModeGate() {
        val svc = mapOf("type" to "notice", "id" to "svc")
        assertTrue(decide(svc, prefs = on.copy(noticePush = false)) is PushOutcome.Ignore)
        assertTrue(decide(svc, prefs = on.copy(childMode = true)) is PushOutcome.Ignore)
        assertTrue(decide(mapOf("type" to "other", "id" to "svc")) is PushOutcome.Ignore)
        assertTrue(decide(mapOf("type" to "notice", "id" to "../x")) is PushOutcome.Ignore)
        // 기간 전·다른 나라 공지·다시 보지 않기를 누른 공지는 알리지 않는다
        assertTrue(decide(mapOf("type" to "notice", "id" to "later")) is PushOutcome.Ignore)
        assertTrue(decide(mapOf("type" to "notice", "id" to "thai")) is PushOutcome.Ignore)
        assertTrue(decide(mapOf("type" to "notice", "id" to "thai"), countries = setOf("TH")) is PushOutcome.Show)
        val key = signed.notices.first { it.id == "svc" }.key
        assertTrue(decide(svc, dismissed = setOf(key)) is PushOutcome.Ignore)
    }

    @Test fun promoNeedsConsent() {
        val promo = mapOf("type" to "notice", "id" to "promo", "cat" to "promo")
        assertTrue(decide(promo, prefs = on.copy(promoPush = false)) is PushOutcome.Ignore)
        val shown = text(decide(promo, prefs = PushPrefs(noticePush = false, promoPush = true)))
        assertTrue(shown.title.startsWith("(광고)"))
        assertTrue("받지 않는 방법을 함께", shown.body.endsWith("광고 알림 끄기"))
        // 메시지가 광고라고 하면 서명본을 못 받았어도 광고 규칙으로(더 엄격하게만)
        assertTrue(decide(mapOf("type" to "notice", "id" to "svc", "cat" to "promo"), prefs = on.copy(promoPush = false)) is PushOutcome.Ignore)
    }

    @Test fun promoAtNightIsHeldUntilEightNotDropped() {
        val promo = mapOf("type" to "notice", "id" to "promo")
        val held = decide(promo, at = night) as PushOutcome.Hold
        assertEquals(Instant.parse("2026-10-08T23:00:00Z"), held.until) // 다음 날 08:00 KST
        assertEquals("promo", held.id)
        // 밤 광고에 따로 동의했으면 바로
        assertTrue(decide(promo, at = night, prefs = on.copy(promoNight = true)) is PushOutcome.Show)
        // 서비스 공지는 밤에도 바로
        assertTrue(decide(mapOf("type" to "notice", "id" to "svc"), at = night) is PushOutcome.Show)
    }

    @Test fun nightIsCheckedInKoreaAndOnThisPhone() {
        // 한국은 낮 12시지만 파리는 새벽 5시 — 파리 아침 8시(= 한국 15시)까지 미룬다
        val paris = ZoneId.of("Europe/Paris")
        val held = decide(mapOf("type" to "notice", "id" to "promo"), local = paris) as PushOutcome.Hold
        assertEquals(Instant.parse("2026-10-08T06:00:00Z"), held.until)
        assertFalse(NoticePushRules.isNightAnywhere(held.until, paris))
        // 어느 시간대든 두 낮이 겹치는 때를 찾는다
        for (zone in listOf("America/Los_Angeles", "America/Sao_Paulo", "Pacific/Honolulu", "Asia/Bangkok", "Pacific/Kiritimati")) {
            val z = ZoneId.of(zone)
            val until = NoticePushRules.releaseAt(night, z)
            assertTrue(zone, until.isAfter(night))
            assertFalse(zone, NoticePushRules.isNight(until, NoticePushRules.KST))
        }
    }

    @Test fun longBodyIsShortened() {
        val d = doc(notice("long", body = "가".repeat(300)))
        val t = text(decide(mapOf("type" to "notice", "id" to "long"), doc = d))
        assertTrue(t.body.length <= NoticePushRules.BODY_MAX)
        assertTrue(t.body.endsWith("…"))
    }
}
