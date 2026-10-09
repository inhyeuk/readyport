package com.readyport.attractions

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * 찜 순서 (2026-10-09 사장님 요청 — 찜 순서를 관광 순서로): 사람이 정한 순서가 기기에 남고,
 * 예전 저장본은 그 순서 그대로, 새 찜은 맨 뒤, 합쳐진 항목은 자리를 지킨다. 다른 나라 찜 자리는 건드리지 않는다.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class SavedOrderTest {

    private val dir: File = Files.createTempDirectory("saved-order").toFile()
    private fun file() = File(dir, "saved_attractions.preferences_pb")

    /** 같은 파일을 여는 새 저장소(앱을 다시 켠 것처럼) — 이전 scope는 닫는다 */
    private fun <T> withStore(block: suspend (SavedAttractionsRepository) -> T): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            runBlocking { block(SavedAttractionsRepository(PreferenceDataStoreFactory.create(scope = scope) { file() })) }
        } finally {
            scope.cancel()
            Thread.sleep(50)
        }
    }

    private fun s(key: String, at: String = "2026-10-01") = SavedAttraction(key, at)

    @Test fun legacyStoredListKeepsItsOrder() {
        // 예전 버전이 쓴 모양 그대로(순서 필드 없음, JSON 배열 순서 = 찜한 순서)
        val raw = """[{"key":"JP/dotonbori","savedAt":"2026-10-01"},{"key":"TH/wat-arun","savedAt":"2026-10-02"},{"key":"JP/sensoji","savedAt":"2026-10-03"}]"""
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        runBlocking { PreferenceDataStoreFactory.create(scope = scope) { file() }.edit { it[stringPreferencesKey("items")] = raw } }
        scope.cancel()
        Thread.sleep(50)
        withStore { st ->
            assertEquals(listOf("dotonbori", "sensoji"), st.orderOf("JP").first())
            assertEquals(listOf("wat-arun"), st.orderOf("TH").first())
        }
    }

    @Test fun newSavesAppendAndResaveKeepsPlace() = withStore { st ->
        st.setSaved("JP/a", true, "2026-10-01")
        st.setSaved("JP/b", true, "2026-10-02")
        st.setSaved("JP/c", true, "2026-10-03")
        // 이미 찜한 곳을 다시 찜해도 맨 뒤로 밀리지 않고 찜한 날도 그대로
        st.setSaved("JP/a", true, "2026-10-09")
        assertEquals(listOf("a", "b", "c"), st.orderOf("JP").first())
        assertEquals("2026-10-01", st.current().first().savedAt)
        st.setSaved("JP/d", true, "2026-10-09")
        assertEquals(listOf("a", "b", "c", "d"), st.orderOf("JP").first())
    }

    @Test fun moveIsPersistedAcrossRestart() {
        withStore { st ->
            listOf("JP/a", "TH/x", "JP/b", "JP/c").forEach { st.setSaved(it, true, "2026-10-01") }
            st.move("JP/c", -1) // c를 한 칸 위로
            st.move("JP/a", 1) // a를 한 칸 아래로
        }
        withStore { st ->
            assertEquals(listOf("c", "a", "b"), st.orderOf("JP").first())
            // 다른 나라 찜이 있던 칸(두 번째)은 그대로
            assertEquals(listOf("JP/c", "TH/x", "JP/a", "JP/b"), st.current().map { it.key })
            st.moveTo("JP/b", 0)
            assertEquals(listOf("b", "c", "a"), st.orderOf("JP").first())
        }
    }

    @Test fun pureMoveEdges() {
        val items = listOf(s("JP/a"), s("TH/x"), s("JP/b"))
        assertEquals(items, SavedOrder.move(items, "JP/a", -1)) // 맨 위에서 위로 = 그대로
        assertEquals(items, SavedOrder.move(items, "JP/b", 5)) // 맨 아래에서 아래로 = 그대로
        assertEquals(items, SavedOrder.move(items, "JP/nope", 1)) // 없는 키
        assertEquals(listOf("JP/b", "TH/x", "JP/a"), SavedOrder.moveTo(items, "JP/a", 9).map { it.key }) // 범위 밖은 끝으로
    }

    @Test fun mergedMigrationKeepsPosition() {
        val items = listOf(s("XX/first"), s("XX/old-a", "2026-10-02"), s("XX/last"))
        val r = SavedMigration.apply(items, "XX", listOf(Retired("old-a", "merged", "new-a", null)))
        assertEquals(listOf("XX/first", "XX/new-a", "XX/last"), r.items.map { it.key })
        // 새 id를 이미 찜해 두었으면 앞에 있던 자리 하나만 남는다
        val dup = listOf(s("XX/new-a", "2026-10-05"), s("XX/b"), s("XX/old-a", "2026-10-01"))
        val r2 = SavedMigration.apply(dup, "XX", listOf(Retired("old-a", "merged", "new-a", null)))
        assertEquals(listOf("XX/new-a", "XX/b"), r2.items.map { it.key })
        assertEquals("2026-10-01", r2.items.first().savedAt)
    }
}
