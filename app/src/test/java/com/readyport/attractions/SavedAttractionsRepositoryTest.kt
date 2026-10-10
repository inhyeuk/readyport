package com.readyport.attractions

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** 관광지 찜: 이 휴대폰에만, 팩이 바뀌어도 조용히 지우지 않고 합쳐진 항목은 새 id로 (SPEC_v5 §6.5) */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class SavedAttractionsRepositoryTest {

    private fun store(): SavedAttractionsRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(Files.createTempDirectory("saved").toFile(), "saved_attractions.preferences_pb")
        return SavedAttractionsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    private val merged = listOf(
        Retired("old-a", "merged", "new-a", null),
        Retired("gone", "closed", null, "문을 닫았어요"),
    )

    @Test fun saveAndUnsave() = runBlocking {
        val s = store()
        s.setSaved("XX/a", true, "2026-10-09")
        s.setSaved("XX/b", true, "2026-10-09")
        s.setSaved("XX/a", false, "2026-10-09")
        assertEquals(listOf("XX/b"), s.current().map { it.key })
    }

    @Test fun addAllKeepsPlanOrderAndSkipsExisting() = runBlocking {
        val s = store()
        s.setSaved("XX/b", true, "2026-10-01")
        val (added, existing) = s.addAll("XX", listOf("a", "b", "c", "a"), "2026-10-10")
        assertEquals(2, added)
        assertEquals(1, existing)
        // 이미 찜한 b 는 자리·날짜 그대로, 새 곳은 계획 순서대로 맨 뒤
        assertEquals(listOf("XX/b", "XX/a", "XX/c"), s.current().map { it.key })
        assertEquals("2026-10-01", s.current().first { it.key == "XX/b" }.savedAt)
        // 다시 눌러도 달라지지 않는다
        assertEquals(0 to 3, s.addAll("XX", listOf("a", "b", "c"), "2026-10-11"))
        assertEquals(3, s.current().size)
    }

    @Test fun pureMigrationMovesAndDedupes() {
        val items = listOf(
            SavedAttraction("XX/old-a", "2026-10-01"),
            SavedAttraction("XX/new-a", "2026-10-05"),
            SavedAttraction("YY/old-a", "2026-10-02"), // 다른 나라 같은 id는 그대로
            SavedAttraction("XX/gone", "2026-10-03"), // 닫은 곳은 지우지 않는다
        )
        val r = SavedMigration.apply(items, "XX", merged)
        assertEquals(listOf("XX/new-a", "YY/old-a", "XX/gone"), r.items.map { it.key })
        assertEquals("2026-10-01", r.items.first().savedAt) // 찜한 날은 이른 값
        assertEquals(setOf("XX/new-a"), r.moved)
    }

    @Test fun migrateOncePerVersionAndIdempotent() = runBlocking {
        val s = store()
        s.setSaved("XX/old-a", true, "2026-10-01")
        assertEquals(1, s.migrate("XX", "2026.10.09-1", merged))
        assertEquals(listOf("XX/new-a"), s.current().map { it.key })
        assertEquals(setOf("XX/new-a"), s.mergedNotice.first())
        // 같은 버전·낮은 버전으로는 다시 하지 않는다
        s.setSaved("XX/old-a", true, "2026-10-02")
        assertEquals(0, s.migrate("XX", "2026.10.09-1", merged))
        assertEquals(0, s.migrate("XX", "2026.10.01-1", merged))
        assertEquals(setOf("XX/new-a", "XX/old-a"), s.current().map { it.key }.toSet())
        // 새 버전이면 다시 — 결과는 중복 없이 하나
        assertEquals(1, s.migrate("XX", "2026.10.10-1", merged))
        assertEquals(listOf("XX/new-a"), s.current().map { it.key })
    }

    @Test fun firstNoticeAndMergedNotice() = runBlocking {
        val s = store()
        assertFalse(s.firstNoticeDone.first())
        s.markFirstNoticeDone()
        assertTrue(s.firstNoticeDone.first())
        s.setSaved("XX/old-a", true, "2026-10-01")
        s.migrate("XX", "2026.10.09-1", merged)
        s.clearMergedNotice()
        assertTrue(s.mergedNotice.first().isEmpty())
    }
}
