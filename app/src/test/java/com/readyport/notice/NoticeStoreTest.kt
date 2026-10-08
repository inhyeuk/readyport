package com.readyport.notice

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.readyport.notice.NoticeTestData.notice
import com.readyport.notice.NoticeTestData.payload
import com.readyport.notice.NoticeTestData.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate

/** 공지 받기·기기 안 사본(서명 다시 확인, 예전 서명본 거부)과 '다시 보지 않기' 기록 저장 (DataStore 파일 교체가 안드로이드 방식이라 Robolectric) */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class NoticeStoreTest {

    private fun signed(generated: String, vararg ids: String): SignedNotices {
        val p = payload(*ids.map { notice(it) }.toTypedArray(), generated = generated)
        return SignedNotices(p, sign(p))
    }

    private class FakeRemote(var next: SignedNotices?, var fail: Boolean = false, var slowMs: Long = 0) : NoticeRemote {
        var calls = 0
        override suspend fun fetch(): SignedNotices? {
            calls++
            if (slowMs > 0) delay(slowMs)
            if (fail) error("offline")
            return next
        }
    }

    private fun repo(remote: NoticeRemote, dir: File = Files.createTempDirectory("notices").toFile()) =
        NoticeRepository(remote, dir, NoticeTestData.parser, Dispatchers.IO)

    @Test fun onlineThenOfflineUsesVerifiedCopy() = runBlocking {
        val dir = Files.createTempDirectory("notices").toFile()
        val remote = FakeRemote(signed("2026-10-08T03:00:00Z", "aaa"))
        val r = repo(remote, dir)
        assertEquals(listOf("aaa"), r.refresh(1_000)!!.notices.map { it.id })
        // 인터넷이 끊겨도 기기 안 사본을 쓴다
        remote.fail = true
        assertEquals(listOf("aaa"), r.refresh(1_000)!!.notices.map { it.id })
        assertEquals(listOf("aaa"), repo(FakeRemote(null, fail = true), dir).cached()!!.notices.map { it.id })
    }

    @Test fun olderSignedCopyDoesNotRollBack() = runBlocking {
        val dir = Files.createTempDirectory("notices").toFile()
        val remote = FakeRemote(signed("2026-10-08T03:00:00Z", "new"))
        val r = repo(remote, dir)
        r.refresh(1_000)
        // 예전에 서명된 묶음을 다시 보내도(지운 공지를 되살리려 해도) 받지 않는다
        remote.next = signed("2026-10-01T03:00:00Z", "old")
        assertEquals(listOf("new"), r.refresh(1_000)!!.notices.map { it.id })
        assertEquals(Instant.parse("2026-10-08T03:00:00Z"), r.cached()!!.generatedAt)
    }

    @Test fun tamperedCopyOnThePhoneIsIgnored() = runBlocking {
        val dir = Files.createTempDirectory("notices").toFile()
        repo(FakeRemote(signed("2026-10-08T03:00:00Z", "aaa")), dir).refresh(1_000)
        val f = File(dir, "notices.json")
        f.writeText(f.readText().replace("공지 aaa", "가짜 공지"))
        assertNull(repo(FakeRemote(null, fail = true), dir).refresh(1_000))
    }

    @Test fun slowNetworkFallsBackWithinTimeout() = runBlocking {
        val dir = Files.createTempDirectory("notices").toFile()
        repo(FakeRemote(signed("2026-10-08T03:00:00Z", "aaa")), dir).refresh(1_000)
        val slow = FakeRemote(signed("2026-10-09T03:00:00Z", "bbb"), slowMs = 5_000)
        val started = System.currentTimeMillis()
        assertEquals(listOf("aaa"), repo(slow, dir).refresh(300)!!.notices.map { it.id })
        assertTrue(System.currentTimeMillis() - started < 3_000)
    }

    @Test fun marksAreStoredOnThePhone() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(Files.createTempDirectory("marks").toFile(), "notices.preferences_pb")
        val store = NoticeStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        val d = NoticeTestData.doc(notice("aaa"), notice("bbb"), notice("ccc", type = "guide"))
        val (a, b, c) = d.notices
        val day = LocalDate.of(2026, 10, 8)
        store.record(a, NoticeChoice.Never, day, d.notices)
        store.record(b, NoticeChoice.Today, day, d.notices)
        store.record(c, NoticeChoice.Close, day, d.notices)
        val m = store.marks()
        assertEquals(setOf(a.key, c.key), m.dismissed) // 이용 안내는 닫으면 끝
        assertEquals(mapOf(b.key to day), m.snoozed)
        assertTrue(b.key in m.round)
        scope.cancel()
    }
}
