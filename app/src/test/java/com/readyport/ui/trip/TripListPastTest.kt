package com.readyport.ui.trip

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.trip.Trip
import com.readyport.trip.TripTiming
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 둘러보기 히어로 `예전 여행지 다시보기`가 닿는 곳: 내 여행 목록의 `지난 여행` 묶음이 **펼쳐진 채로** 열린다
 * (지난 여행만 보는 화면을 따로 만들지 않는다 — 2026-10-03 부록 H.5).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h4000dp")
class TripListPastTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val ui = TripListUi(
        loaded = true,
        today = LocalDate.of(2026, 11, 20),
        rows = listOf(
            TripRow(Trip("TH", "2026-12-10", "2026-12-14", id = "t1"), TripTiming.Upcoming, "태국", 3, 20),
            TripRow(Trip("JP", "2026-10-01", "2026-10-05", id = "t2"), TripTiming.Past, "일본", 18, 18),
        ),
    )

    private fun show(openPast: Boolean) {
        rule.setContent { ReadyPortTheme { TripListContent(ui, {}, {}, openPast = openPast) } }
        rule.waitForIdle()
    }

    /** 그냥 내 여행 탭을 열면 지난 여행은 접힌 채다 */
    @Test
    fun pastStaysFoldedByDefault() {
        show(openPast = false)
        rule.onNodeWithText(context.getString(R.string.trips_group_past, 1)).assertIsDisplayed()
        assertTrue("지난 여행이 접혀 있지 않다", rule.onAllNodesWithText("일본").fetchSemanticsNodes().isEmpty())
    }

    /** `예전 여행지 다시보기`로 오면 지난 여행 줄이 바로 보인다 */
    @Test
    fun pastOpensWhenComingFromExplore() {
        show(openPast = true)
        rule.onAllNodesWithText("일본").onFirst().assertIsDisplayed()
    }
}
