package com.readyport.ui.today

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.data.settings.AppSettings
import com.readyport.trip.StageInfo
import com.readyport.trip.Trip
import com.readyport.trip.TripStage
import com.readyport.ui.FakeSlots
import com.readyport.ui.ReadyPortRoot
import com.readyport.ui.TestPacks
import com.readyport.ui.present.CompanionsContent
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.vault.VaultContents
import com.readyport.vault.WalletState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** M6: '오늘' 화면이 여행 단계에 따라 바뀌는지, 가족 모드 동의, 자녀 폰 모드 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class TripUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int, vararg a: Any) = context.getString(id, *a)
    private val form = TestPacks.thailand.value.forms.first()
    private val trip = Trip("TH", "2026-11-03", "2026-11-07")

    private fun today(stage: StageInfo, hasPassport: Boolean? = true, actions: TodayActions = TodayActions(),
                      onArrived: () -> Unit = {}, onDestroy: () -> Unit = {}, onPostpone: () -> Unit = {}) {
        rule.setContent {
            ReadyPortTheme {
                TodayContent(TodayUi(trip, stage, "태국", form, hasPassport), actions, onArrived, {}, onDestroy, onPostpone, {})
            }
        }
    }

    private fun shown(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        rule.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }

    @Test
    fun preparingShowsFormWhenWindowOpens() {
        var opened: String? = null
        today(StageInfo(TripStage.Preparing, daysLeft = 2, formWindowOpen = true), actions = TodayActions(openForm = { opened = it }))
        rule.onNodeWithText(s(R.string.today_d_day, 2)).assertIsDisplayed()
        shown(s(R.string.today_task_form_title, form.nameKo))
        rule.onNodeWithText(s(R.string.prepare_form_open)).performClick()
        assertEquals("TH_TDAC", opened)
    }

    @Test
    fun preparingAsksForPassportFirst() {
        today(StageInfo(TripStage.Preparing, daysLeft = 10), hasPassport = false)
        shown(s(R.string.today_task_passport_title))
    }

    @Test
    fun departureShowsAirportStepsAndArrivedButton() {
        var arrived = false
        today(StageInfo(TripStage.Departure, dayOfTrip = 1), onArrived = { arrived = true })
        shown(s(R.string.today_departure_step2))
        shown(s(R.string.today_arrived_button))
        rule.onNodeWithText(s(R.string.today_arrived_button)).performClick()
        assertTrue(arrived)
    }

    @Test
    fun arrivalModeShowsQrFirst() {
        var present = false
        today(StageInfo(TripStage.Arrival, dayOfTrip = 1), actions = TodayActions(present = { present = true }))
        rule.onNodeWithText(s(R.string.today_day_n, "태국", 1)).assertIsDisplayed()
        rule.onAllNodesWithText(s(R.string.today_arrival_title)).onFirst().assertIsDisplayed()
        rule.onNodeWithText(s(R.string.today_show_qr)).performClick()
        assertTrue(present)
        shown(s(R.string.today_arrival_step1))
    }

    @Test
    fun travelingGridHasFourBigButtons() {
        today(StageInfo(TripStage.Traveling, dayOfTrip = 2))
        listOf(R.string.today_go_stay, R.string.today_phrases, R.string.today_show_qr, R.string.today_expense).forEach { shown(s(it)) }
    }

    @Test
    fun returnExplainsBeforeDestroyAndCanPostpone() {
        var destroyed = false
        var postponed = false
        today(StageInfo(TripStage.Return, askDestroy = true), onDestroy = { destroyed = true }, onPostpone = { postponed = true })
        shown(s(R.string.today_destroy_body))
        rule.onNodeWithText(s(R.string.today_destroy_later)).performClick()
        assertTrue(postponed)
        rule.onNodeWithText(s(R.string.today_destroy_now)).performClick()
        assertTrue(destroyed)
    }

    @Test
    fun companionNeedsConsent() {
        val added = mutableListOf<String>()
        rule.setContent {
            ReadyPortTheme { CompanionsContent(WalletState.Unlocked(VaultContents()), {}, { added += it }, {}, {}) }
        }
        shown(s(R.string.companion_consent))
        rule.onNodeWithText(s(R.string.companion_label)).performTextInput("첫째")
        rule.onAllNodesWithText(s(R.string.companion_add)).let {
            // 제목과 버튼이 같은 글자 — 버튼은 마지막
        }
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
        rule.onAllNodes(tab(s(R.string.tab_wallet))).assertCountEquals(0)
        rule.onAllNodes(tab(s(R.string.tab_prepare))).assertCountEquals(0)
        rule.onNodeWithText(s(R.string.present_title)).assertIsDisplayed()
    }
}
