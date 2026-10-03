package com.readyport.ui.trip

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.data.settings.AppSettings
import com.readyport.pack.CountryPack
import com.readyport.trip.Checklist
import com.readyport.trip.ChecklistData
import com.readyport.trip.JourneyStage
import com.readyport.trip.StageInfo
import com.readyport.trip.Trip
import com.readyport.trip.TripChecks
import com.readyport.trip.TripStage
import com.readyport.trip.TripStages
import com.readyport.ui.FakeSlots
import com.readyport.ui.ReadyPortRoot
import com.readyport.ui.TestPacks
import com.readyport.ui.components.firstSentence
import com.readyport.ui.present.CompanionsContent
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.vault.VaultContents
import com.readyport.vault.WalletState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 한 여행 화면 (2026-10-03 부록 H): 여행 과정 8단계 막대, 지금 할 일, 단계마다 할 일·안내.
 * 예전 `오늘` 화면이 하던 일(출국 순서·도착 공항·도착했어요·여행 중 타일·귀국 전 확인·여권 지우기·정리 축하)이
 * 모두 **그 단계 카드 안**으로 들어왔는지 본다 — 사라진 것이 없어야 한다.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h3000dp")
class JourneyUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int, vararg a: Any) = context.getString(id, *a)
    private val index = TestPacks.index.value
    private fun pack(cc: String): CountryPack = runBlocking { TestPacks.repo.pack(cc)!!.value }
    private val th = pack("TH")
    private val trip = Trip("TH", "2026-11-03", "2026-11-07", id = "t-th")

    private fun data(t: Trip, today: LocalDate, checks: TripChecks = TripChecks()) =
        Checklist.build(Checklist.Input(t, index, pack(t.country), checks, passportSaved = true, today = today))

    /** [upTo] 단계가 '지금 단계'가 되게 앞 단계를 모두 체크한다 */
    private fun through(t: Trip, today: LocalDate, upTo: JourneyStage): ChecklistData {
        val marks = data(t, today).items.filter { it.stage!! < upTo }.associate { it.id to true }
        return data(t, today, TripChecks(marks = marks))
    }

    private fun ui(
        t: Trip = trip,
        today: LocalDate = LocalDate.of(2026, 10, 2),
        data: ChecklistData = data(t, today),
        cart: List<com.readyport.pack.ShoppingItem> = emptyList(),
        stage: StageInfo? = null,
    ): JourneyUi {
        val p = pack(t.country)
        return JourneyUi(
            loaded = true, trip = t, countryName = p.names.ko, data = data, today = today,
            stage = stage ?: TripStages.compute(t, today, 0L, p.requiredForms.firstOrNull()?.windowDaysIncludingArrival),
            form = p.requiredForms.firstOrNull(),
            cart = cart,
            returnLinks = index.returnLinks, returnFacts = index.returnFacts,
            indexSources = index.sources.associate { it.id to it.name },
            sourceNames = p.sources.associate { it.id to it.name },
            airport = p.airport(t.arrivalAirport) ?: p.airports.singleOrNull(),
            hasAirports = p.airports.isNotEmpty(),
            hasShopping = p.shopping.isNotEmpty(),
            essentialsTotal = 5, essentialsDone = 2,
        )
    }

    private fun show(ui: JourneyUi, actions: ChecklistActions = ChecklistActions()) {
        rule.setContent { ReadyPortTheme { TripJourneyContent(ui, actions) } }
    }

    private fun shown(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        rule.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }

    private fun stageName(stage: JourneyStage) = when (stage) {
        JourneyStage.Plan -> R.string.journey_plan
        JourneyStage.Book -> R.string.journey_book
        JourneyStage.Docs -> R.string.journey_docs
        JourneyStage.Pack -> R.string.journey_pack
        JourneyStage.Departure -> R.string.journey_departure
        JourneyStage.Arrival -> R.string.journey_arrival
        JourneyStage.During -> R.string.journey_during
        JourneyStage.Return -> R.string.journey_return
    }

    // ---------------- 단계 막대 ----------------

    /** 여덟 단계 카드가 **모두** 한 화면에 있고(숨기거나 가로 스크롤하지 않는다), 머리는 `3단계 서류, …`로 읽힌다 */
    @Test
    fun everyStageHasANumberedHeader() {
        show(ui())
        JourneyStage.entries.forEach { stage ->
            val items = ui().data.stage(stage)
            val cd = s(R.string.ck_phase_cd, s(R.string.journey_stage_step_cd, stage.step, s(stageName(stage))), items.size, items.count { it.checked })
            rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(cd))
            rule.onNodeWithContentDescription(cd).assertIsDisplayed()
        }
    }

    // ---------------- 지금 할 일 ----------------

    /** 지금 할 일은 가장 급한 일 하나와 그 단계로 가는 버튼이다 (출발 당일 = 아직 안 낸 입국 카드) */
    @Test
    fun nowCardPointsAtTheMostUrgentTask() {
        val today = trip.start
        // 입국 카드만 빼고 앞 단계를 다 했다 — 출발 당일 안 낸 입국 카드가 가장 급하다(빨강)
        val marks = data(trip, today).items
            .filter { it.stage!! <= JourneyStage.Departure && it.id != "entry_form" }
            .associate { it.id to true }
        show(ui(today = today, data = data(trip, today, TripChecks(marks = marks))))
        rule.onNodeWithText(s(R.string.today_next_label)).assertIsDisplayed()
        // 같은 제목이 서류 단계 카드에도 있다(가리키는 것과 가리켜지는 것) — 맨 위 카드의 것을 본다
        rule.onAllNodesWithText("입국 카드 내기").onFirst().assertIsDisplayed()
        shown(s(R.string.ck_urgent))
        shown(s(R.string.journey_open_stage_step, JourneyStage.Docs.step, s(R.string.journey_docs)))
    }

    // ---------------- 단계마다 하는 일 ----------------

    /** 계획 단계: 나라 안내와 날짜·공항 고치기로 가는 길 (나라 안내로 가는 유일한 길) */
    @Test
    fun planStageLinksToTheCountryGuideAndTripEdit() {
        var country: String? = null
        var edited: String? = null
        show(ui(), ChecklistActions(openCountry = { country = it }, editTrip = { edited = it }))
        shown(s(R.string.journey_plan_country, "태국"))
        rule.onNodeWithText(s(R.string.journey_plan_country, "태국")).performClick()
        assertEquals("TH", country)
        shown(s(R.string.journey_plan_edit))
        rule.onNodeWithText(s(R.string.journey_plan_edit)).performClick()
        assertEquals(trip.id, edited)
    }

    /** 예약 단계가 예약 서류의 집이다 (예전에는 설정 › 내 정보 안에만 있었다) */
    @Test
    fun bookStageIsTheHomeOfBookingImport() {
        var booking = false
        val today = LocalDate.of(2026, 10, 2)
        show(ui(today = today, data = through(trip, today, JourneyStage.Book)), ChecklistActions(openBooking = { booking = true }))
        shown(s(R.string.journey_booking_title))
        shown(s(R.string.journey_booking_open))
        rule.onNodeWithText(s(R.string.journey_booking_open)).performClick()
        assertTrue(booking)
    }

    /** 출국 단계: 출국 순서 다섯 + 도착 공항 짧은 카드 + 도착했어요 */
    @Test
    fun departureStageKeepsTheAirportStepsAndArrivedButton() {
        var arrived = false
        val t = trip.copy(arrivalAirport = "BKK")
        val today = t.start
        show(ui(t, today, through(t, today, JourneyStage.Departure)), ChecklistActions(arrived = { arrived = true }))
        shown(s(R.string.today_departure_steps_title))
        shown(s(R.string.today_departure_step2))
        shown(s(R.string.airport_today_departure_title))
        shown(s(R.string.today_arrived_button))
        rule.onNodeWithText(s(R.string.today_arrived_button)).performClick()
        assertTrue(arrived)
    }

    /** 입국 단계: 공항 순서 + 유심·환전·숙소 + 다 했어요 + 되돌리기 */
    @Test
    fun arrivalStageKeepsTheArrivalCardAndUndo() {
        var done = false
        var undone = false
        val t = trip.copy(arrivalAirport = "BKK", arrivedAt = 1L)
        val today = t.start
        show(
            ui(t, today, through(t, today, JourneyStage.Arrival), stage = StageInfo(TripStage.Arrival, dayOfTrip = 1)),
            ChecklistActions(arrivalDone = { done = true }, undoArrived = { undone = true }),
        )
        shown(s(R.string.today_arrival_title))
        shown(s(R.string.today_arrival_step3))
        shown(s(R.string.today_arrival_done))
        rule.onNodeWithText(s(R.string.today_arrival_done)).performClick()
        assertTrue(done)
        shown(s(R.string.today_arrived_undo_v2))
        rule.onNodeWithText(s(R.string.today_arrived_undo_v2)).performClick()
        assertTrue(undone)
    }

    /** 여행 중 단계: 큰 타일 넷 (숙소로 돌아가기·현지어·보여 주기·쇼핑 리스트) */
    @Test
    fun duringStageKeepsTheBigTiles() {
        val today = LocalDate.of(2026, 11, 5)
        show(ui(today = today, data = through(trip, today, JourneyStage.During)))
        listOf(R.string.today_go_stay, R.string.today_phrases, R.string.today_show_qr, R.string.ck_action_shopping).forEach { shown(s(it)) }
    }

    /** 복귀 단계: 담아 둔 물건 + 귀국 전 확인 **전체**(운영자 결정 10) + 여권 정보 지우기 미루기 */
    @Test
    fun returnStageKeepsTheCartReturnCheckAndDestroy() {
        var postponed = false
        val today = LocalDate.of(2026, 11, 8)
        show(
            ui(today = today, data = through(trip, today, JourneyStage.Return), cart = th.shopping.take(3)),
            ChecklistActions(postponeDestroy = { postponed = true }),
        )
        shown(s(R.string.today_cart_title))
        // 귀국 전 확인은 첫 문장 + 펼치면 팩 문장 전체
        val fact = index.returnFacts.first()
        val (lead, rest) = firstSentence(fact.textKo)
        shown(lead)
        assertTrue(rest != null)
        shown(s(R.string.return_check_full))
        // 여권 정보 지우기: 항목 안 빨간 버튼 + 7일 미루기
        shown(s(R.string.today_destroy_later))
        rule.onNodeWithText(s(R.string.today_destroy_later)).performClick()
        assertTrue(postponed)
        shown(s(R.string.today_destroy_now_target))
        rule.onAllNodesWithText("지우기").assertCountEquals(0)
    }

    /** 정리를 마친 여행은 복귀 단계에 축하 카드 (사진 + 숫자 타일) */
    @Test
    fun wrappedUpTripCelebratesInTheReturnStage() {
        val today = LocalDate.of(2026, 11, 10)
        val t = trip.copy(wrappedUp = true)
        show(ui(t, today, through(t, today, JourneyStage.Return)))
        shown(s(R.string.today_wrapup_photo_title, "태국"))
        shown(s(R.string.trip_nights, 4, 5))
        shown(s(R.string.essentials_progress_stat, 2, 5))
    }

    // ---------------- 길은 하나 ----------------

    /** 서류 단계: 입국 카드 준비하기 · 여권 등록(길은 이 단계 하나) */
    @Test
    fun docsStageOpensTheEntryFormAndPassport() {
        var form: String? = null
        val today = LocalDate.of(2026, 10, 2)
        show(ui(today = today, data = through(trip, today, JourneyStage.Docs)), ChecklistActions(openForm = { form = it }))
        shown(s(R.string.prepare_form_open))
        rule.onNodeWithText(s(R.string.prepare_form_open)).performClick()
        assertEquals("TH_TDAC", form)
    }

    /** 짐 단계: 꼭 챙길 물건 자세히 보기 · 현지어와 긴급 번호 */
    @Test
    fun packStageOpensEssentialsAndHelp() {
        var essentials = false
        var help = false
        val today = LocalDate.of(2026, 10, 2)
        show(
            ui(today = today, data = through(trip, today, JourneyStage.Pack)),
            ChecklistActions(openEssentials = { essentials = true }, openHelp = { help = true }),
        )
        shown(s(R.string.ck_essentials_link))
        rule.onNodeWithText(s(R.string.ck_essentials_link)).performClick()
        assertTrue(essentials)
        shown(s(R.string.ck_action_help))
        rule.onAllNodesWithText(s(R.string.ck_action_help)).onFirst().performClick()
        assertTrue(help)
    }

    /** 입국 단계: 공항 순서 보기 · 입국 때 보여 주기 */
    @Test
    fun arrivalStageOpensTheAirportGuideAndPresent() {
        var airport: Pair<String, String?>? = null
        var present = false
        val t = trip.copy(arrivalAirport = "BKK")
        val today = t.start.plusDays(1)
        show(
            ui(t, today, through(t, today, JourneyStage.Arrival)),
            ChecklistActions(openAirport = { c, a -> airport = c to a }, openPresent = { present = true }),
        )
        shown(s(R.string.ck_action_airport))
        rule.onAllNodesWithText(s(R.string.ck_action_airport)).onFirst().performClick()
        assertEquals("TH" to "BKK", airport)
        shown(s(R.string.ck_action_present))
        rule.onAllNodesWithText(s(R.string.ck_action_present)).onFirst().performClick()
        assertTrue(present)
    }

    // ---------------- 여행 만들기·고치기 · 자녀 폰 ----------------

    @Test
    fun deletingTripAsksFirst() {
        var deleted = false
        rule.setContent {
            ReadyPortTheme {
                TripContent(TripFormUi(index.countries.filter { it.pack }, trip, loaded = true), { _, _, _, _ -> }, { deleted = true })
            }
        }
        // 날짜가 올바르면 칸 아래에 요일까지 보인다 (D20)
        shown(s(R.string.trip_date_preview, 11, 3, "화"))
        // 달력이 주 입력이고(다듬기 S2) 숫자로 적는 길도 그대로: `달력 대신 숫자로 적기` → 20261110 → 11월 10일 (화)
        rule.onNodeWithContentDescription(s(R.string.date_pick_type_cd, s(R.string.trip_start))).performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val startField = rule.onAllNodes(hasSetTextAction())[0]
        startField.performTextClearance()
        startField.performTextInput("20261110")
        shown(s(R.string.trip_date_preview, 11, 10, "화"))

        shown(s(R.string.trip_delete))
        rule.onNodeWithText(s(R.string.trip_delete)).performClick()
        rule.onNodeWithText(s(R.string.trip_delete_confirm_title)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.action_cancel_keep)).performClick()
        assertFalse(deleted)
        rule.onNodeWithText(s(R.string.trip_delete)).performClick()
        rule.onNodeWithText(s(R.string.trip_delete_confirm)).performClick()
        assertTrue(deleted)
    }

    @Test
    fun companionNeedsConsent() {
        val added = mutableListOf<String>()
        rule.setContent {
            ReadyPortTheme { CompanionsContent(WalletState.Unlocked(VaultContents()), {}, { added += it }, {}, {}) }
        }
        shown(s(R.string.companion_consent))
        rule.onNodeWithText(s(R.string.companion_label)).performTextInput("첫째")
        val button = rule.onAllNodesWithText(s(R.string.companion_add))[1]
        button.assertIsNotEnabled()
        rule.onNodeWithText(s(R.string.companion_consent_yes)).performClick()
        button.assertIsEnabled().performClick()
        assertEquals(listOf("첫째"), added)
    }

    @Test
    fun childModeShowsOnlyQrAndHelpTabs() {
        rule.setContent {
            ReadyPortRoot(settings = AppSettings(easyMode = true, childMode = true), onSetEasyMode = {}, onSpeak = {}, slots = FakeSlots)
        }
        fun tab(label: String) = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        rule.onNode(tab(s(R.string.tab_present))).assertIsDisplayed()
        rule.onNode(tab(s(R.string.tab_help))).assertIsDisplayed()
        rule.onAllNodes(tab(s(R.string.tab_explore))).assertCountEquals(0)
        rule.onAllNodes(tab(s(R.string.tab_settings))).assertCountEquals(0)
        rule.onNodeWithText(s(R.string.present_title)).assertIsDisplayed()
    }
}
