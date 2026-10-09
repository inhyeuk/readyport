package com.readyport.ui.plan

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.board.BoardAge
import com.readyport.plan.BudgetBand
import com.readyport.plan.PlanDates
import com.readyport.plan.PlanDay
import com.readyport.plan.PlanDraft
import com.readyport.plan.PlanFailure
import com.readyport.plan.PlanFlagReason
import com.readyport.plan.PlanItem
import com.readyport.plan.PlanMobility
import com.readyport.plan.PlanPurpose
import com.readyport.plan.PlanRequest
import com.readyport.plan.PlanResult
import com.readyport.plan.PlanRules
import com.readyport.plan.PlanStatus
import com.readyport.plan.PlanTravelers
import com.readyport.plan.TimeHint
import com.readyport.ui.board.BoardHomeContent
import com.readyport.ui.board.BoardHomeUi
import com.readyport.ui.board.BoardNav
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.YearMonth

/**
 * 여행 계획 요청 화면 캡처 — app/build/screenshots/plan_*.png (긴 화면은 h2600dp 한 장).
 * 가짜 자료만(개인정보 없음). 계획 글은 화면 확인용으로 지어낸 문장이다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
class PlanCaptureTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val outDir = File("build/screenshots").apply { mkdirs() }
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(easy: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                Box(Modifier.background(Tokens.Ground)) { content() }
            }
        }
    }

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    // ---------------- 게시판 맨 위 카드 ----------------

    @Test fun boardEntryCard() {
        var opened = 0
        show { BoardHomeContent(BoardHomeUi(loading = false), BoardNav(openPlanRequest = { opened++ })) }
        rule.onNodeWithText(s(R.string.plan_entry_title)).assertExists()
        rule.onNodeWithText(s(R.string.plan_entry_badge)).assertExists()
        capture("plan_01_board_entry")
        rule.onNodeWithText(s(R.string.plan_entry_request)).performClick()
        assertEquals(1, opened)
    }

    @Test fun boardEntryMinorIsReadOnly() {
        show { BoardHomeContent(BoardHomeUi(loading = false, age = BoardAge.Status.Minor(YearMonth.of(2030, 3)))) }
        rule.onNodeWithText(s(R.string.plan_entry_minor)).assertExists()
        rule.onAllNodesWithText(s(R.string.plan_entry_request)).assertCountEquals(0)
        capture("plan_02_board_entry_minor")
    }

    // ---------------- 양식 ----------------

    private val draft = PlanDraft(
        country = "JP",
        dates = PlanDates.Range("2026-11-02", "2026-11-06"),
        purposes = listOf(PlanPurpose.Food, PlanPurpose.HistoryCulture),
        note = "라멘을 좋아해요",
        travelers = PlanTravelers(adults = 2, seniors = 1),
        budget = BudgetBand.Standard,
    )
    private val fromTrip = PlanFormUi(
        draft = draft,
        trip = PlanTripDates("2026-11-02", "2026-11-06", 5),
        remaining = PlanRules.Remaining(1, null),
    )

    @Test
    @Config(qualifiers = "w393dp-h2600dp-xxhdpi")
    fun formWithoutMobilityHasNoConsent() {
        show { PlanFormContent(fromTrip) }
        rule.onNodeWithText(s(R.string.plan_quota_left, 1)).assertExists()
        rule.onAllNodesWithText(s(R.string.plan_consent_title)).assertCountEquals(0)
        rule.onNodeWithText(s(R.string.plan_ai_notice)).assertExists()
        capture("plan_03_form")
    }

    @Test
    @Config(qualifiers = "w393dp-h2900dp-xxhdpi")
    fun formWithMobilityShowsSeparateConsent() {
        var consent: Boolean? = null
        val ui = fromTrip.copy(draft = draft.copy(mobility = setOf(PlanMobility.Wheelchair, PlanMobility.StairsHard)), showProblems = true)
        show { PlanFormContent(ui, PlanFormActions(setConsent = { consent = it })) }
        rule.onNodeWithText(s(R.string.plan_consent_title)).assertExists()
        rule.onNodeWithText(s(R.string.plan_consent_refuse)).assertExists()
        rule.onNodeWithText(s(R.string.plan_consent_needed)).assertExists()
        capture("plan_04_form_mobility_consent")
        rule.onNodeWithText(s(R.string.plan_consent_check)).performClick()
        assertEquals(true, consent)
    }

    @Test fun quotaUsedDisablesSending() {
        val ui = fromTrip.copy(remaining = PlanRules.Remaining(0, Instant.parse("2026-10-14T03:00:00Z")))
        show { PlanFormContent(ui) }
        rule.onNodeWithText(s(R.string.plan_quota_used, "10월 14일")).assertExists()
        scrollTo(s(R.string.plan_submit))
        rule.onNodeWithText(s(R.string.plan_submit)).assertIsNotEnabled()
    }

    @Test fun minorSeesExplanationOnly() {
        show { PlanFormContent(fromTrip.copy(age = BoardAge.Status.Minor(YearMonth.of(2030, 3)))) }
        rule.onNodeWithText(s(R.string.plan_age_minor_title)).assertExists()
        rule.onAllNodesWithText(s(R.string.plan_submit)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.plan_country)).assertCountEquals(0)
    }

    @Test fun confirmDialogShowsAiNotice() {
        var sent = 0
        show { PlanFormContent(fromTrip.copy(confirming = true), PlanFormActions(confirmSend = { sent++ })) }
        rule.onNodeWithText(s(R.string.plan_confirm_title)).assertExists()
        capture("plan_05_form_confirm")
        rule.onNodeWithText(s(R.string.plan_confirm_send)).performClick()
        assertEquals(1, sent)
    }

    @Test fun purposeChipsToggleAndStepperChanges() {
        val toggled = mutableListOf<PlanPurpose>()
        var travelers: PlanTravelers? = null
        show { PlanFormContent(fromTrip, PlanFormActions(togglePurpose = { toggled += it }, setTravelers = { travelers = it })) }
        scrollTo(s(R.string.plan_purpose_nature))
        rule.onNodeWithText(s(R.string.plan_purpose_nature)).performClick()
        assertEquals(listOf(PlanPurpose.Nature), toggled)
        scrollTo(s(R.string.plan_children))
        rule.onNode(androidx.compose.ui.test.hasContentDescription(s(R.string.plan_count_plus, s(R.string.plan_children)))).performClick()
        assertEquals(PlanTravelers(adults = 2, seniors = 1, children = 1), travelers)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun formEasy200() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(easy = true) { PlanFormContent(fromTrip.copy(draft = draft.copy(mobility = setOf(PlanMobility.WithInfant)))) }
        capture("plan_06_form_easy200_top")
        scrollTo(s(R.string.plan_consent_check))
        capture("plan_06_form_easy200_consent")
    }

    // ---------------- 내 계획 요청 ----------------

    private val sent = Instant.parse("2026-10-09T01:00:00Z")
    private val all = listOf(
        PlanRequest("r1", "JP", PlanStatus.Done, sent, sent, 5, null, null),
        PlanRequest("r2", "VN", PlanStatus.Processing, sent, null, 3, null, null),
        PlanRequest("r3", "TH", PlanStatus.Queued, sent, null, null, "2026-11-02", "2026-11-04"),
        PlanRequest("r4", "TW", PlanStatus.Failed, sent, sent, 2, null, null, PlanFailure.EngineTimeout),
        PlanRequest("r5", "SG", PlanStatus.Cancelled, sent, sent, 4, null, null),
        PlanRequest("r6", "PH", PlanStatus.Unknown, sent, null, 6, null, null),
    )

    @Test
    @Config(qualifiers = "w393dp-h3000dp-xxhdpi")
    fun listAllStatuses() {
        val asked = mutableListOf<String>()
        show { PlanListContent(PlanListUi(loading = false, items = all), PlanListActions(askDelete = { asked += "del:${it.id}" }, askCancel = { asked += "cancel:${it.id}" })) }
        listOf(R.string.plan_status_queued, R.string.plan_status_processing, R.string.plan_status_done, R.string.plan_status_failed, R.string.plan_status_cancelled)
            .forEach { rule.onNodeWithText(s(it)).assertExists() }
        rule.onAllNodesWithText(s(R.string.plan_open)).assertCountEquals(1)
        rule.onAllNodesWithText(s(R.string.plan_cancel)).assertCountEquals(2)
        // 끝난 요청(도착·실패·취소)은 모두 지울 수 있다 — 2026-10-09 사장님 결정
        rule.onAllNodesWithText(s(R.string.plan_delete)).assertCountEquals(3)
        rule.onNodeWithText(s(R.string.plan_fail_engine)).assertExists()
        rule.onNodeWithText(s(R.string.plan_list_retention)).assertExists()
        capture("plan_07_list_all")
        val deletes = rule.onAllNodesWithText(s(R.string.plan_delete))
        for (i in 0 until 3) deletes[i].performClick()
        assertTrue("del:r5" in asked)
        assertEquals(3, asked.count { it.startsWith("del:") })
    }

    @Test fun listCancelConfirmation() {
        show { PlanListContent(PlanListUi(loading = false, items = all.take(2), confirm = PlanConfirm(all[1], delete = false))) }
        rule.onNodeWithText(s(R.string.plan_cancel_confirm_title)).assertExists()
        capture("plan_08_list_cancel_confirm")
    }

    @Test fun listEmpty() {
        show { PlanListContent(PlanListUi(loading = false)) }
        rule.onNodeWithText(s(R.string.plan_list_empty_title)).assertExists()
        capture("plan_09_list_empty")
    }

    // ---------------- 계획 보기 ----------------

    private val result = PlanResult(
        requestId = "r1",
        country = "JP",
        days = listOf(
            PlanDay(1, "도착하고 가볍게", listOf(PlanItem(TimeHint.Evening, null, "숙소 근처 산책", "첫날은 쉬엄쉬엄 둘러봐요."))),
            PlanDay(
                2,
                "아사쿠사 둘러보기",
                listOf(
                    PlanItem(TimeHint.Morning, "sensoji", "센소지 구경", "사람이 적은 아침에 가면 걷기 편해요."),
                    PlanItem(TimeHint.Lunch, null, "근처에서 점심", "가게 이름은 현지에서 골라요."),
                    PlanItem(TimeHint.Afternoon, null, "강가 산책", ""),
                ),
            ),
        ),
        tips = listOf("교통카드를 미리 충전해 두면 편해요."),
        budgetNotes = listOf("보통 식당 위주로 잡았어요."),
        caveats = listOf("출발 전에 공식 안내(영업·휴무·예약)를 꼭 확인하세요."),
        noticeKo = null,
        createdAt = sent,
    )

    @Test
    @Config(qualifiers = "w393dp-h2600dp-xxhdpi")
    fun planView() {
        val opened = mutableListOf<String>()
        show { PlanViewContent(PlanViewUi(loading = false, result = result, places = mapOf("sensoji" to "센소지")), PlanViewActions(openPlace = { c, id -> opened += "$c/$id" })) }
        rule.onNodeWithText(s(R.string.plan_ai_label)).assertExists()
        rule.onNodeWithText(s(R.string.plan_view_notice_default)).assertExists()
        rule.onNodeWithText(s(R.string.plan_pdf_save)).assertExists()
        capture("plan_10_view")
        rule.onNodeWithText(s(R.string.plan_place_open, "센소지")).performClick()
        assertEquals(listOf("JP/sensoji"), opened)
    }

    @Test fun planViewWithoutPackPlaceHasNoLink() {
        show { PlanViewContent(PlanViewUi(loading = false, result = result)) }
        rule.onAllNodesWithText(s(R.string.plan_place_open, "센소지")).assertCountEquals(0)
    }

    @Test fun planViewMissing() {
        show { PlanViewContent(PlanViewUi(loading = false, result = null)) }
        rule.onNodeWithText(s(R.string.plan_view_missing_title)).assertExists()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun planViewEasy200() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(easy = true) { PlanViewContent(PlanViewUi(loading = false, result = result, places = mapOf("sensoji" to "센소지"))) }
        capture("plan_11_view_easy200")
        scrollTo(s(R.string.plan_place_open, "센소지"))
        capture("plan_11_view_easy200_day2")
    }

    // ---------------- AI 계획 신고 (Play AI 생성 콘텐츠 정책) ----------------

    private fun captureDialog(name: String) {
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val bitmap = rule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun planViewShowsFlagActionAndNoticeMentionsIt() {
        var opened = 0
        show { PlanViewContent(PlanViewUi(loading = false, result = result), PlanViewActions(openFlag = { opened++ })) }
        rule.onNodeWithText(s(R.string.plan_view_not_verified_flag)).assertExists()
        scrollTo(s(R.string.plan_flag_open))
        capture("plan_12_view_flag_button")
        rule.onNodeWithText(s(R.string.plan_flag_open)).performClick()
        assertEquals(1, opened)
        rule.onAllNodesWithText(s(R.string.plan_flag_flagged)).assertCountEquals(0)
    }

    @Test fun flagDialogSendsChosenReasonAndNoteOnlyAfterPicking() {
        val sent = mutableListOf<Pair<PlanFlagReason, String>>()
        var closed = 0
        show {
            PlanViewContent(
                PlanViewUi(loading = false, result = result, flagDialog = true),
                PlanViewActions(sendFlag = { r, n -> sent += r to n }, closeFlag = { closed++ }),
            )
        }
        rule.onNodeWithText(s(R.string.plan_flag_title)).assertExists()
        PlanFlagReason.entries.forEach { rule.onNodeWithText(s(it.labelRes())).assertExists() }
        rule.onNodeWithText(s(R.string.plan_flag_send)).assertIsNotEnabled()
        captureDialog("plan_13_flag_dialog")
        rule.onNodeWithText(s(R.string.plan_flag_reason_unsafe)).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("밤늦게 걷는 길이에요")
        rule.onNodeWithText(s(R.string.plan_flag_send)).assertIsEnabled()
        captureDialog("plan_14_flag_dialog_filled")
        rule.onNodeWithText(s(R.string.plan_flag_send)).performClick()
        assertEquals(listOf(PlanFlagReason.Unsafe to "밤늦게 걷는 길이에요"), sent)
        rule.onNodeWithText(s(R.string.action_cancel_keep)).performClick()
        assertEquals(1, closed)
    }

    @Test
    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    fun flagNoteWithPassportLikeTextCannotBeSent() {
        // 가짜 여권 번호 모양 — 지워야 보낼 수 있다
        show { PlanFlagOnScrim(reason = PlanFlagReason.Inaccurate, note = "여권 M00000000 적어요") }
        rule.onNodeWithText(s(R.string.plan_flag_pii_block)).assertExists()
        rule.onNodeWithText(s(R.string.plan_flag_send)).assertIsNotEnabled()
        capture("plan_15_flag_pii_blocked")
    }

    @Test
    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    fun flagErrorStaysInsideTheCard() {
        show { PlanFlagOnScrim(error = R.string.plan_err_offline) }
        rule.onNodeWithText(s(R.string.plan_err_offline)).assertExists()
        rule.onNodeWithText(s(R.string.plan_flag_send)).assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "w393dp-h2600dp-xxhdpi")
    fun flaggedPlanShowsDoneMessageThenFlaggedState() {
        var ui by mutableStateOf(PlanViewUi(loading = false, result = result, flagged = true, flagJustSent = true))
        show { PlanViewContent(ui) }
        rule.onNodeWithText(s(R.string.plan_flag_done)).assertExists()
        rule.onNodeWithText(s(R.string.plan_flag_flagged)).assertExists()
        rule.onAllNodesWithText(s(R.string.plan_flag_open)).assertCountEquals(0)
        capture("plan_16_flagged_just_sent")
        ui = ui.copy(flagJustSent = false)
        rule.waitForIdle()
        rule.onNodeWithText(s(R.string.plan_flag_flagged_body)).assertExists()
        rule.onAllNodesWithText(s(R.string.plan_flag_done)).assertCountEquals(0)
        // 이미 신고했으면 대화상자도 열리지 않는다
        ui = ui.copy(flagDialog = true)
        rule.waitForIdle()
        rule.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w360dp-h1600dp-xxhdpi")
    fun flagCardEasy200() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(easy = true) { PlanFlagOnScrim(reason = PlanFlagReason.Other) }
        rule.onNodeWithText(s(R.string.plan_flag_reason_other)).assertExists()
        capture("plan_17_flag_card_easy200")
    }
}

