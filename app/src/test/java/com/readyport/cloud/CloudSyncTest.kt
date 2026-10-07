package com.readyport.cloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.autofill.FieldReport
import com.readyport.autofill.QueuedFieldReporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** M9: 서버로 보내는 것은 익명 리포트·찜 수·토픽 구독뿐이고, 규칙과 같은 검사를 거친다 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class CloudSyncTest {

    private fun report(step: String = "personal", code: String = "selector_missing") =
        FieldReport("TH_TDAC", "2026.09.28-2", step, code, "0.1.0", 1L)

    @Test fun planCountsOnceAndSyncsTopics() {
        val p = CloudSyncPlan.plan(setOf("TH", "JP"), "SG", counted = setOf("JP"), subscribed = setOf("country_JP", "country_MY"))
        assertEquals(setOf("TH"), p.countFavorites)
        assertEquals(setOf("country_TH", "country_SG"), p.subscribe)
        assertEquals(setOf("country_MY"), p.unsubscribe)
        // 이상한 코드는 무시
        assertTrue(CloudSyncPlan.plan(setOf("../x"), null, emptySet(), emptySet()).subscribe.isEmpty())
    }

    @Test fun noticeTopicsFollowSettings() {
        // 공지 알림 켬(기본) → notice_all, 광고성 소식 동의 → notice_promo. 끄면 구독을 푼다. 토큰은 어디에도 보내지 않는다
        val on = CloudSyncPlan.plan(emptySet(), null, emptySet(), emptySet(), NoticeTopics(notice = true, promo = false))
        assertEquals(setOf("notice_all"), on.subscribe)
        val promo = CloudSyncPlan.plan(emptySet(), null, emptySet(), setOf("notice_all"), NoticeTopics(notice = true, promo = true))
        assertEquals(setOf("notice_promo"), promo.subscribe)
        val off = CloudSyncPlan.plan(setOf("TH"), null, emptySet(), setOf("notice_all", "notice_promo", "country_TH"), NoticeTopics(notice = false, promo = false))
        assertEquals(setOf("notice_all", "notice_promo"), off.unsubscribe)
        assertTrue(off.subscribe.isEmpty())
        // 자녀 폰 모드: 공지 토픽은 모두 푼다(공지를 띄우지 않는 모드). 나라 토픽은 그대로
        val child = CloudSyncPlan.plan(setOf("TH"), null, emptySet(), setOf("notice_all", "country_TH"), NoticeTopics(notice = true, promo = true, childMode = true))
        assertEquals(setOf("notice_all"), child.unsubscribe)
        assertTrue(child.subscribe.isEmpty())
    }

    @Test fun runnerSubscribesNoticeTopics() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val queue = QueuedFieldReporter(File(Files.createTempDirectory("q").toFile(), "r.jsonl"))
        val backend = FakeBackend()
        val runner = CloudSyncRunner(backend, queue, CloudState(context))
        assertTrue(runner.run(emptySet(), "JP", NoticeTopics(notice = true, promo = true)))
        assertTrue(backend.topics.containsAll(setOf("notice_all", "notice_promo", "country_JP")))
        assertTrue(runner.run(emptySet(), "JP", NoticeTopics(notice = true, promo = false)))
        assertFalse("notice_promo" in backend.topics)
        assertTrue("notice_all" in backend.topics)
        // 다음 테스트가 쓰는 같은 저장소를 깨끗이
        runner.run(emptySet(), null)
        Unit
    }

    @Test fun reportsExpireAfterOneYear() {
        // 규칙은 335~395일 사이만 받는다
        val days = (CloudSyncPlan.expiryMillis(0) / 86_400_000L)
        assertTrue(days in 336..394)
    }

    @Test fun reportFieldsMatchRules() {
        val f = CloudSyncPlan.toFirestore(report())!!
        assertEquals(setOf("form_id", "pack_version", "step_id", "error_code", "app_version"), f.keys)
        assertNull(CloudSyncPlan.toFirestore(report(code = "other")))
        assertNull(CloudSyncPlan.toFirestore(report(step = "has space")))
        assertEquals("-", CloudSyncPlan.toFirestore(report(step = "-"))!!["step_id"])
    }

    private class FakeBackend(val failReports: Boolean = false) : CloudBackend {
        val reports = mutableListOf<Map<String, Any>>()
        val favorites = mutableListOf<String>()
        val topics = mutableSetOf<String>()
        override fun addReport(fields: Map<String, Any>) { if (failReports) error("offline"); reports += fields }
        override fun incrementFavorite(country: String) { favorites += country }
        override fun subscribe(topic: String) { topics += topic }
        override fun unsubscribe(topic: String) { topics -= topic }
    }

    @Test fun runnerSendsQueueAndIsIdempotent() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val queue = QueuedFieldReporter(File(Files.createTempDirectory("q").toFile(), "r.jsonl"))
        queue.report(report())
        queue.report(report(code = "bogus")) // 규칙에 안 맞음 → 버림
        queue.report(report(step = "travel"))
        val state = CloudState(context)
        val backend = FakeBackend()
        val runner = CloudSyncRunner(backend, queue, state)

        assertTrue(runner.run(setOf("TH"), "TH"))
        assertEquals(2, backend.reports.size)
        assertTrue(queue.pending().isEmpty())
        assertEquals(listOf("TH"), backend.favorites)
        assertEquals(setOf("country_TH"), backend.topics)

        // 다시 돌려도 찜 수는 다시 올리지 않는다
        assertTrue(runner.run(setOf("TH"), "TH"))
        assertEquals(listOf("TH"), backend.favorites)
    }

    @Test fun offlineKeepsQueue() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val queue = QueuedFieldReporter(File(Files.createTempDirectory("q").toFile(), "r.jsonl"))
        queue.report(report())
        val ok = CloudSyncRunner(FakeBackend(failReports = true), queue, CloudState(context)).run(emptySet(), null)
        assertFalse(ok)
        assertEquals(1, queue.pending().size)
    }
}
