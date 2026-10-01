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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
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
import com.readyport.ui.trip.TripContent
import com.readyport.ui.trip.TripFormUi
import com.readyport.vault.VaultContents
import com.readyport.vault.WalletState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                      onArrived: () -> Unit = {}, onDestroy: () -> Unit = {}, onPostpone: () -> Unit = {}, onUndoArrived: () -> Unit = {}) {
        rule.setContent {
            ReadyPortTheme {
                TodayContent(TodayUi(trip, stage, "태국", form, hasPassport), actions, onArrived, {}, onDestroy, onPostpone, {}, onUndoArrived)
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
    fun arrivedCanBeUndoneOnDepartureDay() {
        var undone = false
        today(StageInfo(TripStage.Arrival, dayOfTrip = 1), onUndoArrived = { undone = true })
        // '도착했어요'를 잘못 눌렀으면 되돌린다 (재검토 R18)
        shown(s(R.string.today_arrived_undo))
        rule.onNodeWithText(s(R.string.today_arrived_undo)).performClick()
        assertTrue(undone)
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
        // 버튼 이름에 무엇을 지우는지 (`지우기`만이 아니라 `여권 정보 지우기` — 재검토 R18)
        shown(s(R.string.today_destroy_now_target))
        rule.onNodeWithText(s(R.string.today_destroy_now_target)).performClick()
        assertTrue(destroyed)
        rule.onAllNodesWithText(s(R.string.today_destroy_now)).assertCountEquals(0)
    }

    @Test
    fun returnStartsWithPhotoCardAndATaskThatJumpsToTheCart() {
        val th = TestPacks.thailand.value
        val index = TestPacks.index.value
        rule.setContent {
            ReadyPortTheme {
                TodayContent(
                    TodayUi(trip, StageInfo(TripStage.Return), "태국", null, true, cart = th.shopping,
                        returnLinks = index.returnLinks, returnFacts = index.returnFacts,
                        indexSources = index.sources.associate { it.id to it.name }, sourceNames = th.sources.associate { it.id to it.name }),
                    TodayActions(), {}, {}, {}, {}, {},
                )
            }
        }
        // 빈 '여행이 끝났어요' 카드 대신 사진 머리 카드 + 한 줄(기간·담아 둔 물건) + 지금 할 일 (재검토 R14)
        rule.onNodeWithText(s(R.string.today_return_photo_title, "태국")).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.today_fact_nights, 4, 5)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.today_fact_cart, th.shopping.size)).assertIsDisplayed()
        shown(s(R.string.today_return_task_cart))
        rule.onNodeWithText(s(R.string.today_return_task_cart_button)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(s(R.string.today_cart_title)).assertIsDisplayed()
        // 귀국 전 확인은 접힌 요약 — 첫 문장만, 펼치면 팩 문장 전체 (값은 팩 문장 그대로)
        val fact = index.returnFacts.first()
        val (lead, rest) = firstSentence(fact.textKo)
        shown(lead)
        rule.onAllNodesWithText(fact.textKo).assertCountEquals(0)
        shown(s(R.string.today_return_rules_more))
        rule.onNodeWithText(s(R.string.today_return_rules_more)).performClick()
        shown(fact.textKo)
        assertTrue(rest != null)
    }

    @Test
    fun returnRuleNumbersAreBoldButTextIsUnchanged() {
        val text = "별도 면세: 술 2L 이하·미화 400달러 이하, 담배 200개비, 향수 100ml. 신고하지 않으면 최고 1,000만 원 과태료예요."
        val tokens = numberRanges(text).map { text.substring(it.first, it.last + 1) }
        assertEquals(listOf("2L", "400달러", "200개비", "100ml", "1,000만 원"), tokens)
        // 보이는 글자에 보이지 않는 줄바꿈 문자가 끼어 있어도 글자는 그대로, 굵기만 바뀐다
        val shown = com.readyport.ui.components.koDisplay(text, sdk = 31)
        val styled = emphasizeNumbers(text, shown)
        assertEquals(shown, styled.text)
        val bold = styled.spanStyles.map { styled.text.substring(it.start, it.end).filter { c -> c != '\u2060' && c != '\u200B' } }
        assertEquals(listOf("2L", "400달러", "200개비", "100ml", "1,000만 원"), bold.map { it.replace('\u00A0', ' ') })
        assertEquals("첫 문장." to "둘째 문장.", firstSentence("첫 문장. 둘째 문장."))
        assertEquals("문장 하나" to null, firstSentence("문장 하나"))
    }

    @Test
    fun wrapUpCelebratesWithPhotoAndNumbers() {
        var newTrip = false
        rule.setContent {
            ReadyPortTheme {
                TodayContent(TodayUi(trip, StageInfo(TripStage.WrapUp), "태국", null, true), TodayActions(), {}, {}, {}, {}, { newTrip = true })
            }
        }
        // 정리 단계 축하 카드 (재검토 R14·R19): 나라 사진 + 한 줄 + 앱 안 값으로 만든 숫자 타일
        rule.onNodeWithText(s(R.string.today_wrapup_photo_title, "태국")).assertIsDisplayed()
        shown(s(R.string.today_wrapup_fact_country))
        shown(s(R.string.trip_nights, 4, 5))
        shown(s(R.string.today_new_trip))
        rule.onNodeWithText(s(R.string.today_new_trip)).performClick()
        assertTrue(newTrip)
    }

    @Test
    fun returnListsProhibitedCartItemsFirstWithSources() {
        val th = TestPacks.thailand.value
        val index = TestPacks.index.value
        rule.setContent {
            ReadyPortTheme {
                TodayContent(
                    TodayUi(trip, StageInfo(TripStage.Return), "태국", null, true, cart = th.shopping.sortedBy { it.importStatus != "allowed" },
                        returnLinks = index.returnLinks, returnFacts = index.returnFacts,
                        indexSources = index.sources.associate { it.id to it.name }, sourceNames = th.sources.associate { it.id to it.name }),
                    TodayActions(), {}, {}, {}, {}, {},
                )
            }
        }
        val names = th.shopping.sortedBy { it.importStatus != "prohibited" }.map { it.names.ko }
        shown(names.first())
        // 반입 불가 품목이 담아 둔 물건 중 맨 위
        // 화면 밖으로 잘린 행도 비교하게 잘리지 않은 위치(positionInRoot)로 본다 — 귀국 머리 카드가 생겨 담아 둔 물건 카드가 길게 걸친다
        val tops = th.shopping.map { rule.onNodeWithText(it.names.ko).fetchSemanticsNode().positionInRoot.y }
        val prohibitedTop = rule.onNodeWithText(names.first()).fetchSemanticsNode().positionInRoot.y
        assertTrue(tops.all { it >= prohibitedTop })
        // 품목 출처(관광청 안내)가 카드 맨 아래 출처 줄에 이름으로 보인다(내부 ID 아님)
        val itemSource = th.sources.first { it.id == th.shopping.first().source }.name
        rule.onAllNodes(hasText(itemSource, substring = true)).onFirst().assertExists()
        rule.onAllNodes(hasText(th.shopping.first().source, substring = true)).assertCountEquals(0)
    }

    @Test
    fun deletingTripAsksFirst() {
        var deleted = false
        rule.setContent {
            ReadyPortTheme {
                TripContent(TripFormUi(TestPacks.index.value.countries.filter { it.pack }, trip, loaded = true), { _, _, _ -> }, { deleted = true })
            }
        }
        // 날짜가 올바르면 칸 아래에 요일까지 보인다 (D20)
        shown(s(R.string.trip_date_preview, 11, 3, "화"))
        // 날짜 칸은 숫자만 적는다 (재검토 R18 — 숫자 자판, 하이픈은 칸이 그린다): 20261110 → 11월 10일 (화)
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
        rule.onAllNodes(tab(s(R.string.tab_home))).assertCountEquals(0)
        rule.onAllNodes(tab(s(R.string.tab_settings))).assertCountEquals(0)
        rule.onNodeWithText(s(R.string.present_title)).assertIsDisplayed()
    }
}
