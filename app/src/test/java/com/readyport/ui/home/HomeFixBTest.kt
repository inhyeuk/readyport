package com.readyport.ui.home

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.tabs.EssentialsSummary
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
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
                HomeContent(TestPacks.homeUi().copy(essentials = EssentialsSummary(5, 2)), HomeActions(), today = LocalDate.of(2026, 9, 28))
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
        assertTrue(rule.onAllNodesWithText(s(R.string.home_chip_form)).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText(s(R.string.home_chip_visa_apply)).fetchSemanticsNodes().isEmpty())
    }

    /** R13: 급할 때는 도움 줄은 나라 바로 다음, 여행 준비 기본 정보보다 위 */
    @Test
    fun helpComesBeforeTravelBasics() {
        show(easy = false)
        val help = rule.onNodeWithText(s(R.string.help_shortcut_title)).getBoundsInRoot()
        val basics = rule.onNodeWithText(s(R.string.home_basics_title)).getBoundsInRoot()
        assertTrue("도움 줄이 기본 정보보다 아래", help.bottom <= basics.top)
    }

    /** 기본 모드: 여행 준비 카드는 처음부터 펼쳐져 있다 */
    @Test
    fun basicModeShowsTravelBasicsOpen() {
        show(easy = false)
        rule.onNodeWithText(s(R.string.today_departure_step2)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.home_passport_body)).assertIsDisplayed()
    }

    /**
     * R13 쉬운 모드: 여행 준비 카드 넷은 한 줄씩 접혀 있고(접힘), 누르면 그 자리에서 원래 카드가 펼쳐지며(내용 그대로),
     * 아래 `접기`(무엇을 접는지 TalkBack 이름)로 다시 접힌다.
     */
    @Test
    fun easyModeFoldsTravelBasicsAndOpensThemInPlace() {
        show(easy = true)
        val departure = s(R.string.home_departure_title)
        val step = s(R.string.today_departure_step2)
        assertTrue("접혀 있어야 함", rule.onAllNodesWithText(step).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText(s(R.string.home_passport_body)).fetchSemanticsNodes().isEmpty())
        val row = rule.onNode(hasText(departure) and hasStateDescription(s(R.string.state_collapsed)))
        row.performClick()
        rule.onNodeWithText(step).assertIsDisplayed()
        val fold = rule.onNode(hasContentDescription(s(R.string.home_fold_less_cd, departure)))
        fold.assertIsDisplayed().performClick()
        assertTrue("다시 접혀야 함", rule.onAllNodesWithText(step).fetchSemanticsNodes().isEmpty())
        // 꼭 챙길 물건 줄은 진행(5개 중 2개)을 접힌 채로도 보여 준다
        rule.onNodeWithText(s(R.string.essentials_progress, 5, 2)).assertIsDisplayed()
        assertEquals(
            4,
            rule.onAllNodes(hasStateDescription(s(R.string.state_collapsed))).fetchSemanticsNodes().size,
        )
    }

    private fun hasStateDescription(value: String) =
        androidx.compose.ui.test.SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
}
