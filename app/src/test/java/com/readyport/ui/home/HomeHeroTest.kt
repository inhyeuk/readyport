package com.readyport.ui.home

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.trip.Trip
import com.readyport.ui.TestPacks
import com.readyport.ui.components.noBreak
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 둘러보기 히어로 (운영자 2026-10-03, DESIGN_SPEC 부록 H.5·H.7).
 * *"내 여행이 1개 이상일 경우, 여행을 흰색 박스로 각 여행을 구분해 주고, 여행 국가는 좀 더 선명하게 표시하고,
 * 둥근 박스 형태로 1, 2,.. 로 번호를 매겨줘."*
 * 박스 수·번호·순서·가는 길과 버튼 구성을 지킨다. 화면을 길게 잡아(h4000dp) 스크롤 없이 모두 그려지게 한다.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h4000dp")
class HomeHeroTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int, vararg a: Any) = context.getString(id, *a)

    private val th = HomeTrip(
        "태국", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 7), code = "TH", id = "g-th",
        checklistDone = 12, checklistTotal = 28,
    )
    private val jp = HomeTrip(
        "일본", LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 9), code = "JP", id = "g-jp",
        checklistDone = 3, checklistTotal = 24,
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

    /**
     * 여행 박스 = 누를 수 있는 Role.Button 노드 중 TalkBack 이름이 `여행 n`인 것.
     * (나라 사진 타일도 Role.Button + 나라 이름을 품으므로 이름으로 가린다)
     */
    private fun tripBox(number: Int) = rule.onNode(
        hasClickAction() and hasContentDescription(s(R.string.explore_trip_number_cd, number)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
    )

    /** 박스 하나를 TalkBack이 한 번에 읽는 글 (나라 이름·출발까지·날짜·진행이 모두 들어 있다) */
    private fun boxText(number: Int): String =
        tripBox(number).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }.orEmpty()

    /** 둥근 번호 배지(`여행 n`)가 몇 개 있는지 */
    private fun numberBadges(number: Int) =
        rule.onAllNodesWithContentDescription(s(R.string.explore_trip_number_cd, number))

    // ---------------- ① 여행이 없을 때 ----------------

    /** 여행이 없으면 박스도 없고 `새 여행 만들기`(채움) 하나뿐이다 */
    @Test
    fun noTripShowsOnlyMakeTrip() {
        show(TestPacks.homeUi())
        rule.onNodeWithText(s(R.string.today_new_trip)).assertIsDisplayed()
        assertTrue("여행이 없는데 `내 여행` 묶음이 있다", missing(R.string.tab_trip))
        assertTrue("여행이 없는데 `예전 여행지 다시보기`가 있다", missing(R.string.explore_past_trips))
        numberBadges(1).assertCountEquals(0)
        rule.onNodeWithText(s(R.string.today_new_trip)).performClick()
        assertEquals(listOf("make"), opened)
    }

    // ---------------- ② 여행 하나 = 흰 박스 하나 ----------------

    /** 여행 하나: 번호 1 + 나라 이름 + 출발까지 + 날짜 + 체크리스트 진행이 한 박스에, 누르면 그 여행으로 */
    @Test
    fun oneTripGetsItsOwnNumberedBox() {
        show(TestPacks.homeUi().copy(trips = listOf(th), activeTrips = 1))
        rule.onNodeWithText(s(R.string.tab_trip)).assertIsDisplayed()
        numberBadges(1).assertCountEquals(1)
        // 박스 단추 이름 = `여행 1` + 나라 + 출발까지 (InfoChip 날짜·진행은 그 안에서 따로 읽히는 줄이다)
        val box = boxText(1)
        listOf("태국", s(R.string.home_trip_days, 3)).forEach {
            assertTrue("박스 이름에 `$it`가 없다 — 읽은 글: $box", box.contains(it))
        }
        // 날짜·체크리스트 진행 줄은 박스 안에 그대로 있다
        rule.onNodeWithText(s(R.string.ck_now_eyebrow, 12, 28)).assertIsDisplayed()
        val fmt = DateTimeFormatter.ofPattern(s(R.string.home_trip_date_format), Locale.KOREAN)
        // 같은 달이면 끝 날짜의 달을 뺀다(e569312 — `11월 3일 (화) ~ 7일 (토)`)
        val endFmt = if (th.startDate.month == th.endDate.month) {
            DateTimeFormatter.ofPattern(s(R.string.home_trip_date_format_day), Locale.KOREAN)
        } else {
            fmt
        }
        val dates = s(R.string.home_trip_dates, noBreak(th.startDate.format(fmt)), noBreak(th.endDate.format(endFmt)))
        rule.onNodeWithText(dates).assertIsDisplayed()
        tripBox(1).performClick()
        assertEquals(listOf("trip:g-th"), opened)
        assertTrue("끝난 여행이 없는데 `예전 여행지 다시보기`가 있다", missing(R.string.explore_past_trips))
        assertTrue("박스가 하나인데 `모두 보기` 줄이 있다", rule.onAllNodesWithText(s(R.string.explore_trips_all, 1)).fetchSemanticsNodes().isEmpty())
    }

    // ---------------- ③ 여행 둘 = 박스 둘, 번호 1·2 ----------------

    /** 여행 둘: 박스가 둘로 갈리고 번호 1·2가 **위에서 아래 순서**로 붙는다. 박스마다 그 여행으로 간다 */
    @Test
    fun twoTripsAreTwoNumberedBoxesInOrder() {
        show(TestPacks.homeUi().copy(trips = listOf(th, jp), activeTrips = 2))
        numberBadges(1).assertCountEquals(1)
        numberBadges(2).assertCountEquals(1)
        assertTrue("1번 박스가 태국이 아니다", boxText(1).contains("태국"))
        assertTrue("2번 박스가 일본이 아니다", boxText(2).contains("일본"))
        val first = tripBox(1).getBoundsInRoot()
        val second = tripBox(2).getBoundsInRoot()
        assertTrue("1번 박스가 2번 박스보다 아래에 있다", first.top.value < second.top.value)
        tripBox(2).performClick()
        assertEquals(listOf("trip:g-jp"), opened)
    }

    // ---------------- ④ 박스보다 많으면 `모두 보기` ----------------

    /** 여행이 박스 수보다 많으면 박스는 [MAX_TRIP_BOXES]개까지, `여행 4개 모두 보기`가 목록으로 데려간다 */
    @Test
    fun moreTripsThanBoxesShowAnAllTripsRow() {
        show(TestPacks.homeUi().copy(trips = listOf(th, jp), activeTrips = 4))
        assertEquals(2, MAX_TRIP_BOXES)
        numberBadges(1).assertCountEquals(1)
        numberBadges(2).assertCountEquals(1)
        numberBadges(3).assertCountEquals(0)
        rule.onNodeWithText(s(R.string.explore_trips_all, 4)).performClick()
        assertEquals(listOf("trips"), opened)
    }

    // ---------------- ⑤ 버튼 구성 ----------------

    /** 지난 여행이 있으면 `예전 여행지 다시보기`가 붙고, 채움 버튼은 그대로 `새 여행 만들기` 하나다 */
    @Test
    fun pastTripsAddTheLookBackButtonOnly() {
        show(TestPacks.homeUi().copy(trips = listOf(th), activeTrips = 1, pastTrips = 2))
        rule.onNodeWithText(s(R.string.today_new_trip)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.explore_past_trips)).performClick()
        assertEquals(listOf("past"), opened)
    }

    /** 지난 여행만 있으면 박스는 없다 — 지난 여행은 `예전 여행지 다시보기` 뒤에 있다(히어로에 박스로 두지 않는다) */
    @Test
    fun pastOnlyKeepsNoBox() {
        show(TestPacks.homeUi().copy(pastTrips = 1), today = LocalDate.of(2026, 11, 12))
        numberBadges(1).assertCountEquals(0)
        assertTrue("지난 여행만 있는데 `내 여행` 박스 묶음이 있다", missing(R.string.tab_trip))
        rule.onNodeWithText(s(R.string.today_new_trip)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.explore_past_trips)).assertIsDisplayed()
    }

    // ---------------- 박스 순서를 정하는 순수 함수 ----------------

    /** 박스 순서: **여행 중 먼저**, 그다음 떠나는 날 가까운 순. 지난 여행은 들어오지 않는다 */
    @Test
    fun heroTripOrderPutsOngoingFirstAndDropsPastTrips() {
        val today = LocalDate.of(2026, 11, 7)
        val ongoing = Trip("JP", "2026-11-05", "2026-11-09", id = "ongoing")
        val soon = Trip("TH", "2026-11-20", "2026-11-24", id = "soon")
        val later = Trip("SG", "2027-01-10", "2027-01-13", id = "later")
        val past = Trip("VN", "2026-09-01", "2026-09-05", id = "past")
        val order = heroTripOrder(listOf(later, past, soon, ongoing), today).map { it.id }
        assertEquals(listOf("ongoing", "soon", "later"), order)
    }
}
