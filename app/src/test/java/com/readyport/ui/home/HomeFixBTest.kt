package com.readyport.ui.home

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 재검토 수정 B1 — 홈 (R13·R19): 나라 타일 칩 구성 통일, 도움 줄 위치, 쉬운 모드 접기(정보를 숨기지 않고 한 번 눌러 펼침).
 * 화면 전체가 그려지게 아주 긴 창(h6000dp)에서 본다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h6000dp")
class HomeFixBTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun show(easy: Boolean) {
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                HomeContent(TestPacks.homeUi(), HomeActions(), today = LocalDate.of(2026, 9, 28))
            }
        }
        rule.waitForIdle()
    }

    /** R19: 나라 타일마다 칩은 입국 조건 1개 — 맨 위 나라에만 `입력 도우미` 칩이 더 붙지 않는다 */
    @Test
    fun everyCountryTileHasTheSameSingleChip() {
        show(easy = false)
        val names = TestPacks.homeUi().countries.map { it.nameKo }
        names.forEach { name ->
            val node = rule.onNodeWithContentDescription(s(R.string.home_country_open, name)).fetchSemanticsNode()
            val state = node.config.getOrNull(SemanticsProperties.StateDescription)
            assertTrue("$name 칩 없음", !state.isNullOrBlank())
            assertTrue("$name 칩이 여러 개: $state", ", " !in state!!)
        }
        assertTrue(rule.onAllNodesWithText("도우미", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
