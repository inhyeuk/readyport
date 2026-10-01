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
import com.readyport.ui.tabs.PrepareContent
import com.readyport.ui.components.essentialsSummary
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
    private val index get() = TestPacks.index.value
    private val got = setOf("passport", "medicine")

    /** 쉬운 모드 홈: `처음으로` 없이 `소리로 듣기`만 내용선 폭 전체 */
    @Test
    fun easyHomeHidesGoHome() {
        rule.setContent {
            ReadyPortTheme(easyMode = true) { HomeContent(TestPacks.homeUi(), HomeActions(), today = LocalDate.of(2026, 9, 28)) }
        }
        rule.onAllNodesWithText(s(R.string.action_home)).assertCountEquals(0)
        val listen = rule.onNode(hasText(s(R.string.action_listen)) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .assertIsDisplayed().getBoundsInRoot()
        assertEquals(20f, listen.left.value, 1f)
        assertEquals(373f, listen.right.value, 1f)
    }

    /** 여행이 있으면 그 나라 팩 전기 값(`220 V 전압`·`한국 플러그 그대로 써요`) + 기내 반입만 보조배터리 + 출처, 진행 2 / 5 */
    @Test
    fun essentialsChipsCarryValuesWithTrip() {
        val th = TestPacks.thailand.value
        val summary = essentialsSummary(index, th, got)
        rule.setContent {
            ReadyPortTheme {
                HomeContent(
                    TestPacks.homeUi().copy(
                        trip = HomeTrip("태국", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 7), code = "TH"),
                        essentials = summary,
                    ),
                    HomeActions(), today = LocalDate.of(2026, 10, 31),
                )
            }
        }
        rule.waitForIdle()
        assertEquals(5, summary.total)
        assertEquals(2, summary.done)
        rule.onNodeWithText(th.power!!.voltage).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.essentials_power_kr_plug_fits)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.essentials_badge_carry_on)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.essentials_progress_stat, 2, 5)).assertIsDisplayed()
        // 값 없는 주제 이름 칩(`플러그`·`보조배터리` 단독)은 없다
        rule.onAllNodesWithText(s(R.string.home_items_plug)).assertCountEquals(0)
        // 칩 값의 출처(태국관광청 전기)가 카드 안에
        val powerSource = th.source(th.power.source)!!.name
        assertTrue(rule.onAllNodes(hasText(powerSource, substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    /** 여행이 없으면 전기 칩은 없고(값이 없으니) 기내 반입만 칩과 진행만 */
    @Test
    fun noTripShowsOnlyChipsWithValues() {
        rule.setContent {
            ReadyPortTheme { HomeContent(TestPacks.homeUi().copy(essentials = essentialsSummary(index, null, got)), HomeActions(), today = LocalDate.of(2026, 9, 28)) }
        }
        rule.waitForIdle()
        rule.onAllNodesWithText(s(R.string.essentials_power_kr_plug_fits)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.home_items_voltage)).assertCountEquals(0)
        rule.onNodeWithText(s(R.string.essentials_badge_carry_on)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.essentials_progress_stat, 2, 5)).assertIsDisplayed()
    }

    /** 여행 준비(18)의 꼭 챙길 물건도 같은 진행 줄(n / 5)과 값 칩 — 홈과 같은 모양 */
    @Test
    fun prepareShowsTheSameProgress() {
        val th = TestPacks.thailand.value
        rule.setContent { ReadyPortTheme { PrepareContent(TestPacks.formEntries(), {}, essentialsSummary(index, th, got)) } }
        rule.waitForIdle()
        rule.onNodeWithText(s(R.string.essentials_progress_stat, 2, 5)).assertIsDisplayed()
        rule.onNodeWithText(th.power!!.voltage).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.home_essentials_open)).assertIsDisplayed()
        // '곧 추가돼요'는 내 정보 맨 아래 한 곳뿐(다듬기 S 통합 — 재검토2 ⑤#11): 여행 준비에는 없다. 예약 서류는 이미 있는 기능
        rule.onAllNodesWithText(s(R.string.coming_soon_group)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.coming_soon)).assertCountEquals(0)
    }
}
