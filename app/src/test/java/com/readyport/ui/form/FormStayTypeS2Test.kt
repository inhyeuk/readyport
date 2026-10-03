package com.readyport.ui.form

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.autofill.FormValues
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.vault.PassportRecord
import com.readyport.vault.StayRecord
import com.readyport.vault.VaultContents
import com.readyport.vault.WalletState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 다듬기 S2 운영자 결정 S2-4: **그 나라 사이트 선택지에 없는 숙소 종류는 앱이 대신 고르지 않고 비워 둔다.**
 * 비운 칸은 빈 필수 칸으로 세고(주 버튼 막힘), 확인 화면이 왜 비웠는지 한국어로 말한다.
 * 숙소 이름·주소는 모두 지어낸 값이다.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h2400dp")
class FormStayTypeS2Test {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private val passport = PassportRecord(
        surname = "ERIKSSON", givenNames = "ANNA MARIA", documentNumber = "L898902C3",
        nationality = "KOR", issuingState = "KOR", birthDate = "1974-08-12", sex = "F",
        expiryDate = "2036-04-15", source = "mrz", mrzVerified = true, savedAt = "x",
    )

    private fun stay(type: String?) = StayRecord(
        id = "s1", name = "페이크 리버 게스트하우스", addressLocal = "45 Fake Road, Kuala Lumpur 50000",
        checkIn = "2026-11-03", checkOut = "2026-11-07", type = type, savedAt = "x",
    )

    /** 말레이시아 MDAC 확인 화면 한 벌 (MDAC에는 게스트하우스 선택지가 없다) */
    private fun showMdac(type: String?): VaultContents {
        val recipe = runBlocking { TestPacks.repo.recipe("MY_MDAC") }!!
        val pack = runBlocking { TestPacks.repo.pack("MY") }!!.value
        val form = pack.forms.first { it.id == "MY_MDAC" }
        val contents = VaultContents(passport = passport, stays = listOf(stay(type)))
        val ctx = FormContext("MY_MDAC", form, recipe.value, recipe.version, false)
        val draft = FormValues.defaults(recipe.value) + FormValues.suggest(recipe.value, contents)
        rule.setContent {
            ReadyPortTheme {
                FormConfirmContent(
                    ConfirmUi(ctx, WalletState.Unlocked(contents), FormValues.build(recipe.value, contents, draft), draft),
                    { _, _ -> }, {}, {}, {}, {},
                )
            }
        }
        return contents
    }

    private fun scrollTo(text: String) = rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    /** 그 나라 선택지에 없는 종류 → 비워 두고, 왜 비웠는지 말한다 */
    @Test
    fun typeTheSiteDoesNotOfferIsLeftBlankAndExplained() {
        showMdac("guest_house")
        val note = s(R.string.form_stay_type_not_listed)
        scrollTo(note)
        rule.onNodeWithText(note).assertIsDisplayed()
    }

    /** 아직 아무 종류도 안 골랐으면 '아직 안 고른 칸'이라고 말한다 */
    @Test
    fun typeNotPickedYetSaysSo() {
        showMdac(null)
        val note = s(R.string.form_stay_type_blank)
        scrollTo(note)
        rule.onNodeWithText(note).assertIsDisplayed()
    }

    /** 비운 칸은 **빈 필수 칸으로 센다** — 주 버튼(`맞아요, 입력해 주세요`)이 막힌다 */
    @Test
    fun blankTypeKeepsTheConfirmButtonDisabled() {
        val contents = showMdac("guest_house")
        val recipe = runBlocking { TestPacks.repo.recipe("MY_MDAC") }!!.value
        val missing = FormValues.missingRequired(recipe, FormValues.build(recipe, contents, FormValues.suggest(recipe, contents)))
        assertTrue("숙소 종류가 빈 필수 칸으로 세어지지 않았다", missing.any { it.key == "stay.type" })
        val yes = s(R.string.form_confirm_yes)
        scrollTo(yes)
        rule.onNodeWithText(yes).assertIsNotEnabled()
    }
}
