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
