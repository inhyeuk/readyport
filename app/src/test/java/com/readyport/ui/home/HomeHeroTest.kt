package com.readyport.ui.home

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 둘러보기 히어로가 여행으로 가는 **단 하나의 자리**인지 (운영자 2026-10-03, DESIGN_SPEC 부록 H.5).
 * 상태 네 가지마다 버튼 구성과 가는 길을 지킨다. 화면을 길게 잡아(h4000dp) 스크롤 없이 모두 그려지게 한다.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h4000dp")
class HomeHeroTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int) = context.getString(id)

    private val trip = HomeTrip(
        "태국", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 7), code = "TH", id = "g-th",
        checklistDone = 12, checklistTotal = 28,
    )

    private val opened = mutableListOf<String>()

    private fun show(ui: HomeUi, today: LocalDate = LocalDate.of(2026, 10, 31)) {
        rule.setContent {
            ReadyPortTheme {
                HomeContent(
                    ui,
                    HomeActions(
                        openTrip = { opened += "trip:$it" },
                        openTrips = { opened += "trips" },
                        openPastTrips = { opened += "past" },
                        makeTrip = { opened += "make" },
                    ),
                    today = today,
                )
            }
        }
        rule.waitForIdle()
    }

    /** 그 글자가 화면에 아예 없는지 */
    private fun missing(@StringRes id: Int) = rule.onAllNodesWithText(s(id)).fetchSemanticsNodes().isEmpty()

    /** ① 여행이 없으면 `새 여행 만들기` 하나뿐 — `내 여행 점검`·`예전 여행지 다시보기`는 없다 */
    @Test
    fun noTripShowsOnlyMakeTrip() {
        show(TestPacks.homeUi())
        rule.onNodeWithText(s(R.string.today_new_trip)).assertIsDisplayed()
        assertTrue("여행이 없는데 `내 여행 점검`이 있다", missing(R.string.explore_trip_check))
        assertTrue("여행이 없는데 `예전 여행지 다시보기`가 있다", missing(R.string.explore_past_trips))
        rule.onNodeWithText(s(R.string.today_new_trip)).performClick()
        assertEquals(listOf("make"), opened)
    }

    /** ② 다가오는 여행 하나: `내 여행 점검`이 그 여행으로(목록이 아니라) + 남은 날·체크리스트 진행이 함께 보인다 */
    @Test
    fun oneUpcomingTripChecksThatTrip() {
        show(TestPacks.homeUi().copy(trip = trip, activeTrips = 1))
        rule.onNodeWithText(context.getString(R.string.home_trip_days, 3)).assertExists()
        rule.onNodeWithText(context.getString(R.string.ck_now_eyebrow, 12, 28)).assertExists()
        rule.onNodeWithText(s(R.string.explore_trip_check)).performClick()
        assertEquals(listOf("trip:g-th"), opened)
        assertTrue("끝난 여행이 없는데 `예전 여행지 다시보기`가 있다", missing(R.string.explore_past_trips))
    }

    /** ③ 다가오는 여행이 둘 이상이면 어느 여행인지 고르도록 내 여행 목록으로 */
    @Test
    fun twoUpcomingTripsGoToTheTripList() {
        show(TestPacks.homeUi().copy(trip = trip, activeTrips = 2))
        rule.onNodeWithText(s(R.string.explore_trip_check)).performClick()
        assertEquals(listOf("trips"), opened)
    }

    /** ④ 다가오는 여행 + 지난 여행: 버튼 셋이 모두 있고 `예전 여행지 다시보기`는 지난 여행으로 */
    @Test
    fun upcomingAndPastShowAllThreeActions() {
        show(TestPacks.homeUi().copy(trip = trip, activeTrips = 1, pastTrips = 2))
        rule.onNodeWithText(s(R.string.explore_trip_check)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.today_new_trip)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.explore_past_trips)).performClick()
        assertEquals(listOf("past"), opened)
    }

    /** ⑤ 지난 여행만 있으면 주 버튼은 `새 여행 만들기`, `내 여행 점검`은 없다 */
    @Test
    fun pastOnlyKeepsMakeTripAsThePrimary() {
        show(TestPacks.homeUi().copy(trip = trip, pastTrips = 1), today = LocalDate.of(2026, 11, 12))
        rule.onNodeWithText(s(R.string.today_new_trip)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.explore_past_trips)).assertIsDisplayed()
        assertTrue("지난 여행만 있는데 `내 여행 점검`이 있다", missing(R.string.explore_trip_check))
        rule.onNodeWithText(s(R.string.home_trip_after)).assertExists()
    }
}
