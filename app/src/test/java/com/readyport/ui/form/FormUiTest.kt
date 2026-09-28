package com.readyport.ui.form

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.autofill.FormValues
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.VaultContents
import com.readyport.vault.WalletState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** M4: 3개 국어 확인 화면(PRD 5.2)과 수동 모드 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class FormUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private val recipe = TestPacks.tdacRecipe
    private val form = TestPacks.thailand.value.forms.first()
    private val passport = PassportRecord(
        surname = "ERIKSSON", givenNames = "ANNA MARIA", documentNumber = "L898902C3",
        nationality = "KOR", issuingState = "KOR", birthDate = "1974-08-12", sex = "F",
        expiryDate = "2036-04-15", source = "mrz", mrzVerified = true, savedAt = "x",
    )
    private val flight = BookingRecord(id = "1", kind = "flight", title = "t", flightNumbers = listOf("KE651"), dates = listOf("2026-11-03"), savedAt = "x")
    private val contents = VaultContents(passport = passport, bookings = listOf(flight))

    private fun ctx(killed: Boolean = false) = FormContext("TH_TDAC", form, recipe.value, recipe.version, killed)

    private fun ui(draft: Map<String, String> = emptyMap(), wallet: WalletState = WalletState.Unlocked(contents)) = ConfirmUi(
        context = ctx(),
        wallet = wallet,
        values = (wallet as? WalletState.Unlocked)?.let { FormValues.build(recipe.value, it.contents, draft) }.orEmpty(),
        draft = draft,
    )

    private fun shown(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        rule.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }

    @Test
    fun showsBulkValuesWithThreeLanguageLabelsAndSources() {
        rule.setContent { ReadyPortTheme { FormConfirmContent(ui(), { _, _ -> }, {}, {}, {}, {}) } }
        rule.onNodeWithText(s(R.string.form_confirm_title, form.nameKo)).assertIsDisplayed()
        shown(s(R.string.form_from_documents))
        shown("ERIKSSON")
        shown("Family Name · นามสกุล")
        shown(s(R.string.form_origin_passport))
        shown("KE651")
        shown(s(R.string.form_origin_flight))
    }

    @Test
    fun confirmNeedsRequiredChoicesThenEnables() {
        var state by mutableStateOf(ui())
        val set = { k: String, v: String -> state = ui(state.draft + (k to v)) }
        var confirmed = false
        rule.setContent { ReadyPortTheme { FormConfirmContent(state, set, {}, {}, { confirmed = true }, {}) } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.form_confirm_yes)))
        rule.onNodeWithText(s(R.string.form_confirm_yes)).assertIsNotEnabled()

        // 여행 목적은 ko · en · local 칩으로 고른다
        shown("관광 · Tourism · ท่องเที่ยว")
        rule.onNodeWithText("관광 · Tourism · ท่องเที่ยว").performClick()
        assertEquals("tourism", state.draft["trip.purpose"])

        state = ui(
            state.draft + mapOf(
                "stay.type" to "hotel", "profile.occupation" to "OFFICE WORKER", "profile.country_res" to "대한민국",
                "profile.phone_code" to "82", "trip.country_board" to "대한민국",
                "profile.city_res" to "SEOUL", "profile.phone" to "1012345678", "stay.province" to "BANGKOK", "stay.address" to "1 SAMPLE RD",
            ),
        )
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.form_confirm_yes)))
        rule.onNodeWithText(s(R.string.form_confirm_yes)).assertIsEnabled().performClick()
        assertTrue(confirmed)
    }

    @Test
    fun killSwitchOffersManualModeOnly() {
        rule.setContent {
            ReadyPortTheme { FormConfirmContent(ui().copy(context = ctx(killed = true)), { _, _ -> }, {}, {}, {}, {}) }
        }
        shown(s(R.string.form_killed))
        rule.onAllNodesWithText(s(R.string.form_confirm_yes)).assertCountEquals(0)
    }

    @Test
    fun lockedWalletAsksToUnlock() {
        var unlocked = false
        rule.setContent {
            ReadyPortTheme { FormConfirmContent(ui(wallet = WalletState.Locked(true)), { _, _ -> }, { unlocked = true }, {}, {}, {}) }
        }
        shown(s(R.string.wallet_unlock))
        rule.onNodeWithText(s(R.string.wallet_unlock)).performClick()
        assertTrue(unlocked)
        rule.onAllNodesWithText("ERIKSSON").assertCountEquals(0)
    }

    @Test
    fun noPassportAsksToRegister() {
        var register = false
        rule.setContent {
            ReadyPortTheme {
                FormConfirmContent(ui(wallet = WalletState.Unlocked(VaultContents())), { _, _ -> }, {}, { register = true }, {}, {})
            }
        }
        shown(s(R.string.form_need_passport))
        rule.onNodeWithText(s(R.string.wallet_passport_add)).performClick()
        assertTrue(register)
    }

    @Test
    fun manualModeCopiesValues() {
        val copies = mutableListOf<Pair<String, String>>()
        val autofillUi = AutofillUi(context = ctx(), values = FormValues.build(recipe.value, contents, emptyMap()))
        rule.setContent { ReadyPortTheme { ManualModeContent(autofillUi, {}, { l, t -> copies += l to t }, {}) } }
        shown("ERIKSSON")
        rule.onNodeWithText(s(R.string.manual_copy)).let { rule.onAllNodesWithText(s(R.string.manual_copy)).onFirst().performClick() }
        assertEquals("Family Name" to "ERIKSSON", copies.first())
        shown(s(R.string.manual_copied))
        // 건강 질문 단계는 값 없이 안내만
        shown(recipe.value.steps.last().noteKo!!)
    }
}
