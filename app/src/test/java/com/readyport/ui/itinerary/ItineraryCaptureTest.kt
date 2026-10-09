package com.readyport.ui.itinerary

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.attractions.AdvisoryState
import com.readyport.attractions.AttTestData
import com.readyport.attractions.SavedAttraction
import com.readyport.attractions.search.AttractionSearchIndex
import com.readyport.itinerary.Itinerary
import com.readyport.itinerary.ItineraryStop
import com.readyport.itinerary.TripItinerary
import com.readyport.stay.Stays
import com.readyport.trip.Checklist
import com.readyport.trip.JourneyStage
import com.readyport.trip.Trip
import com.readyport.trip.TripStages
import com.readyport.ui.TestPacks
import com.readyport.ui.attractions.AttractionsListActions
import com.readyport.ui.attractions.AttractionsListContent
import com.readyport.ui.attractions.AttractionsListModel
import com.readyport.ui.attractions.AttractionsListUi
import com.readyport.ui.attractions.SavedOrderActions
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import com.readyport.ui.trip.ChecklistActions
import com.readyport.ui.trip.JourneyUi
import com.readyport.ui.trip.TripJourneyContent
import com.readyport.vault.StayRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * 찜 순서·관광 일정 화면 캡처 (2026-10-09) — app/build/screenshots/itin_*.png. debug 샘플 일본 5곳, 숙소는 가짜 이름·좌표.
 * 기본 w393dp-h851dp, 쉬운 모드 200%는 w360dp-h640dp. 캡처와 함께 TalkBack 이름·누름을 가볍게 확인한다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
class ItineraryCaptureTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun captureDialog(name: String) {
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val bitmap = rule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(easy: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                Box(Modifier.background(Tokens.Ground)) { content() }
            }
        }
    }

    private fun scrollToText(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private val catalog = AttTestData.catalog(AttTestData.debugSample())
    private val trip = Trip("JP", "2026-10-08", "2026-10-12", id = "jp1")
    private val trip2 = Trip("JP", "2026-12-20", "2026-12-23", id = "jp2")
    private val saves = listOf("osaka-castle", "sensoji", "fushimi-inari-taisha", "dotonbori", "nara-park", "vanished-place")
        .map { SavedAttraction("JP/$it", "2026-10-01") }

    /** 가짜 숙소: 8·9일 밤 오사카, 10·11일 밤 도쿄 */
    private val stays = listOf(
        StayRecord(id = "s1", tripId = "jp1", name = "가짜 오사카 숙소", checkIn = "2026-10-08", checkOut = "2026-10-10", lat = 34.70, lng = 135.50, savedAt = "2026-10-01"),
        StayRecord(id = "s2", tripId = "jp1", name = "가짜 도쿄 숙소", checkIn = "2026-10-10", checkOut = "2026-10-12", lat = 35.69, lng = 139.70, savedAt = "2026-10-01"),
    )

    private fun savedUi(trips: List<Trip> = listOf(trip)): AttractionsListUi {
        val content = AttractionsListModel.build(catalog, AttractionSearchIndex(catalog), "", null, true, saves, null, AdvisoryState.Normal)
        return AttractionsListUi(
            loading = false, country = "JP", countryName = "일본", catalog = catalog, content = content,
            savedOnly = true, savedKeys = saves.map { it.key }.toSet(), savedItems = saves, trips = trips,
        )
    }

    private fun planUi(
        plan: TripItinerary,
        today: LocalDate = LocalDate.of(2026, 10, 1),
        proposing: Boolean = false,
        withStays: Boolean = false,
        t: Trip = trip,
    ) = ItineraryModel.build(t, plan, catalog, AdvisoryState.Normal, saves, today, if (withStays) stays else emptyList(), proposing, "일본")

    /** 사람이 확인한 일정(숙소 기준 제안 → 담기) + 한 날에 도쿄·오사카를 섞어 둔 모습 */
    private val confirmed: TripItinerary
        get() {
            val p = planUi(TripItinerary(), proposing = true, withStays = true).proposal!!
            return Itinerary.moveToDay(Itinerary.add(TripItinerary(), p.stops), "JP/nara-park", 2)
        }

    // ---------------- 찜 목록: 순서 ----------------

    @Test fun savedOrderList() {
        val moves = mutableListOf<Pair<String, Int>>()
        show { AttractionsListContent(savedUi(), AttractionsListActions(savedOrder = SavedOrderActions(move = { k, by -> moves += k to by }))) }
        capture("itin_01_saved_order")
        // 위·아래 버튼은 무엇을 옮기는지까지 읽는다
        rule.onNodeWithContentDescription(context.getString(R.string.order_move_up_cd, "센소지")).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.order_move_up_cd, "오사카성")).assertDoesNotExist() // 맨 위
        rule.onNodeWithContentDescription(context.getString(R.string.order_move_down_cd, "오사카성")).performClick()
        assertEquals(listOf("JP/osaka-castle" to 1), moves)
        scrollToText(context.getString(R.string.attractions_saved_missing))
        capture("itin_01b_saved_order_bottom")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun savedOrderEasy200() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(easy = true) { AttractionsListContent(savedUi(), AttractionsListActions()) }
        scrollToText(context.getString(R.string.saved_order_hint))
        capture("itin_02_saved_order_easy200")
    }

    @Test fun addToTripPicksAmongTrips() {
        val opened = mutableListOf<String>()
        show { AttractionsListContent(savedUi(listOf(trip, trip2)), AttractionsListActions(savedOrder = SavedOrderActions(openItinerary = { opened += it }))) }
        rule.onNodeWithText(context.getString(R.string.saved_add_to_trip)).performClick()
        captureDialog("itin_03_pick_trip")
        rule.onNode(hasText("12월 20일", substring = true) and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("jp2"), opened)
    }

    @Test fun addToTripWithoutTrip() {
        var made = 0
        show { AttractionsListContent(savedUi(emptyList()), AttractionsListActions(savedOrder = SavedOrderActions(makeTrip = { made++ }))) }
        rule.onNodeWithText(context.getString(R.string.saved_add_to_trip)).performClick()
        captureDialog("itin_04_no_trip")
        rule.onNodeWithText(context.getString(R.string.saved_no_trip_make)).performClick()
        assertEquals(1, made)
    }

    // ---------------- 관광 일정 ----------------

    @Test fun transplantProposal() {
        var confirmed = 0
        show { ItineraryContent(planUi(TripItinerary(), proposing = true, withStays = true), ItineraryActions(confirm = { confirmed++ })) }
        rule.onNodeWithText(context.getString(R.string.itinerary_proposal_stays), substring = true).assertExists()
        capture("itin_10_proposal")
        scrollToText(context.getString(R.string.itinerary_proposal_confirm))
        capture("itin_10b_proposal_bottom")
        rule.onNodeWithText(context.getString(R.string.itinerary_proposal_confirm)).performClick()
        assertEquals(1, confirmed)
    }

    @Test fun planEditor() {
        val moved = mutableListOf<Pair<String, Int>>()
        show { ItineraryContent(planUi(confirmed), ItineraryActions(moveToDay = { k, d -> moved += k to d })) }
        capture("itin_11_plan")
        scrollToText(context.getString(R.string.itinerary_mixed_far))
        capture("itin_11b_plan_mixed")
        // 다른 날로 → 날 고르기
        val moveNara = context.getString(R.string.itinerary_move_day_cd, "나라 공원")
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(moveNara))
        rule.onNodeWithContentDescription(moveNara).performClick()
        captureDialog("itin_11c_pick_day")
        rule.onNode(hasText("5일차", substring = true) and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("JP/nara-park" to 4), moved)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun planEditorEasy200() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(easy = true) { ItineraryContent(planUi(confirmed), ItineraryActions()) }
        scrollToText(context.getString(R.string.itinerary_hint))
        capture("itin_12_plan_easy200")
    }

    @Test fun emptyPlanWithNewSaves() {
        show { ItineraryContent(planUi(TripItinerary()), ItineraryActions()) }
        rule.onNodeWithText(context.getString(R.string.itinerary_new_row, 5)).assertExists()
        capture("itin_13_plan_empty_with_saves")
    }

    // ---------------- 내 여행 화면 ----------------

    private fun journeyUi(today: LocalDate, plan: TripItinerary): JourneyUi {
        val pack = runBlocking { TestPacks.repo.pack("JP")!!.value }
        val data = Checklist.build(Checklist.Input(trip, TestPacks.index.value, pack, passportSaved = true, today = today))
        val now = today.atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return JourneyUi(
            loaded = true,
            trip = trip.copy(arrivedAt = if (today.isAfter(trip.start)) now - 86_400_000L else null),
            countryName = pack.names.ko,
            data = data,
            today = today,
            stage = TripStages.compute(trip, today, now),
            airport = pack.airports.firstOrNull(),
            hasAirports = pack.airports.isNotEmpty(),
            stays = Stays.forTrip(stays, trip),
            itinerary = planUi(plan, today = today),
        )
    }

    @Test fun journeyPlanTile() {
        val opened = mutableListOf<String>()
        show {
            TripJourneyContent(journeyUi(LocalDate.of(2026, 10, 1), confirmed), ChecklistActions(openItinerary = { opened += it }), openAtFirst = JourneyStage.Plan.key)
        }
        scrollToText(context.getString(R.string.itinerary_tile))
        capture("itin_20_journey_plan_tile")
        rule.onNodeWithText(context.getString(R.string.itinerary_tile)).performClick()
        assertEquals(listOf("jp1"), opened)
    }

    @Test fun journeyTodayPlaces() {
        val details = mutableListOf<String>()
        show {
            TripJourneyContent(
                journeyUi(LocalDate.of(2026, 10, 10), confirmed),
                ChecklistActions(openAttraction = { _, id -> details += id }),
                openAtFirst = JourneyStage.During.key,
            )
        }
        scrollToText(context.getString(R.string.itinerary_today_title))
        capture("itin_21_journey_today")
        scrollToText(context.getString(R.string.itinerary_open_all))
        capture("itin_21b_journey_today_bottom")
        rule.onNodeWithContentDescription(context.getString(R.string.itinerary_open_map_cd, "센소지")).assertExists()
    }

    @Test fun reTransplantIdempotentOnScreen() {
        // 담은 뒤 다시 옮겨 담기를 눌러도 새 곳이 없으면 '모두 들어 있어요'만
        val all = Itinerary.add(confirmed, listOf(ItineraryStop("JP/vanished-place", 0)))
        show { ItineraryContent(planUi(all, proposing = true), ItineraryActions()) }
        rule.onNodeWithText(context.getString(R.string.itinerary_all_in)).assertExists()
    }
}
