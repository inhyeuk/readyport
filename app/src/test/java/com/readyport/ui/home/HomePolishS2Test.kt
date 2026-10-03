package com.readyport.ui.home

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 다듬기 S2 — 홈(01·02)·여행 준비(18)의 재검토2 남은 문제:
 * 홈 자신에서는 `처음으로`를 숨긴다(⑤#12), 꼭 챙길 물건 칩은 값이 있는 것만(①#15·②#9), 진행 n / 5는 01·02·18 같은 모양(③#10).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h6000dp")
class HomePolishS2Test {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    /** 쉬운 모드 홈: `처음으로` 없이 `소리로 듣기`만 내용선 폭 전체 */
    @Test
    fun easyHomeHidesGoHome() {
        rule.setContent {
            ReadyPortTheme(easyMode = true) { HomeContent(TestPacks.homeUi(), HomeActions(), today = LocalDate.of(2026, 9, 28)) }
        }
        rule.onAllNodesWithText(s(R.string.action_home)).assertCountEquals(0)
        // 여행 준비 조각은 모두 내 여행 탭으로 — 둘러보기에는 꼭 챙길 물건 진행·출국 순서가 없다
        rule.onAllNodesWithText(s(R.string.home_basics_title)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.today_departure_step2)).assertCountEquals(0)
        val listen = rule.onNode(hasText(s(R.string.action_listen)) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .assertIsDisplayed().getBoundsInRoot()
        assertEquals(20f, listen.left.value, 1f)
        assertEquals(373f, listen.right.value, 1f)
    }
}
