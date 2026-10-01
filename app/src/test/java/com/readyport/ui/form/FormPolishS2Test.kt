package com.readyport.ui.form

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 다듬기 S2 — 입국 카드 확인(20)·값 복사해서 넣기(21)의 재검토2 남은 문제:
 * 띠 셋 → 안심 카드 한 장, 빈칸이 남으면 주 버튼 = 첫 빈칸으로 가기, 선택지 벽 → 구분선 목록(고른 칸은 한 줄),
 * 21 단계 머리 = 번호 원, 노랑 띠 → 마지막 단계 카드 안 한 줄.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class FormPolishS2Test {

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
    private val ctx = FormContext("TH_TDAC", form, recipe.value, recipe.version, false)

    private fun ui(draft: Map<String, String> = emptyMap()) =
        ConfirmUi(ctx, WalletState.Unlocked(contents), FormValues.build(recipe.value, contents, draft), draft)

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    /** 맨 위는 안심 카드 한 장 — 비제휴·이 휴대폰에만·제출은 직접이 한 번씩, 아래 버튼 설명은 띠가 아니라 한 줄 */
    @Test
    fun confirmTopIsOneAssuranceCard() {
        rule.setContent { ReadyPortTheme { FormConfirmContent(ui(), { _, _ -> }, {}, {}, {}, {}) } }
        listOf(R.string.guide_not_affiliated, R.string.settings_local_only_title, R.string.country_submit_self).forEach {
            rule.onAllNodesWithText(s(it)).assertCountEquals(1)
            rule.onNodeWithText(s(it)).assertIsDisplayed()
        }
        val notice = rule.onNodeWithText(s(R.string.guide_not_affiliated)).fetchSemanticsNode().boundsInRoot
        val local = rule.onNodeWithText(s(R.string.settings_local_only_title)).fetchSemanticsNode().boundsInRoot
        assertTrue("정부 비제휴가 첫 줄", notice.top < local.top)
    }

    /** 빈칸이 남은 동안 주 버튼 = `첫 빈칸으로 가기 (N개 남음)` — 누르면 첫 빈칸으로 가서 초점. `맞아요`는 비활성으로 그 아래 */
    @Test
    fun blanksMakeGoToFirstBlankThePrimaryAction() {
        rule.setContent { ReadyPortTheme { FormConfirmContent(ui(), { _, _ -> }, {}, {}, {}, {}) } }
        val missing = FormValues.missingRequired(recipe.value, ui().values)
        val go = s(R.string.form_go_first_missing_count, missing.size)
        scrollTo(go)
        rule.onNodeWithText(go).assertIsDisplayed()
        val goTop = rule.onNodeWithText(go).fetchSemanticsNode().boundsInRoot.top
        val yesNode = rule.onNodeWithText(s(R.string.form_confirm_yes))
        yesNode.assertIsNotEnabled()
        assertTrue("`맞아요`는 첫 빈칸 버튼 아래", yesNode.fetchSemanticsNode().boundsInRoot.top > goTop)
        rule.onNodeWithText(go).performClick()
        rule.waitForIdle()
        val first = missing.first()
        assertTrue("첫 빈칸이 글 칸이어야 이 검사가 맞다: ${first.key}", first.optionsRef == null)
        rule.onNode(hasSetTextAction() and hasText(first.labels.ko, substring = true)).assertIsDisplayed().assertIsFocused()
    }

    /** 이미 고른 칸은 고른 줄 하나 + `선택지 N개 모두 보기` — 펼치면 다른 선택지, 접기 TalkBack 이름에 칸 이름 */
    @Test
    fun chosenFieldShowsOneRowUntilOpened() {
        rule.setContent { ReadyPortTheme { FormConfirmContent(ui(mapOf("trip.purpose" to "tourism")), { _, _ -> }, {}, {}, {}, {}) } }
        val purpose = recipe.value.fields.single { it.key == "trip.purpose" }
        val options = recipe.value.options.getValue(purpose.optionsRef!!)
        val chosen = options.single { it.value == "tourism" }
        val other = options.first { it.value != "tourism" }
        val chosenText = listOfNotNull(chosen.ko, chosen.en, chosen.local).joinToString(" · ")
        val otherText = listOfNotNull(other.ko, other.en, other.local).joinToString(" · ")
        scrollTo(chosenText)
        rule.onNodeWithText(chosenText).assertIsDisplayed().assertIsSelected()
        assertTrue(rule.onAllNodesWithText(otherText).fetchSemanticsNodes().isEmpty())
        val all = s(R.string.form_choice_all, options.size)
        scrollTo(all)
        rule.onNodeWithText(all).performClick()
        scrollTo(otherText)
        rule.onNodeWithText(otherText).assertIsDisplayed()
        rule.onNode(hasContentDescription(s(R.string.collapse_target_cd, s(R.string.fold_target_choices, purpose.labels.ko)))).assertExists()
    }

    /** 21: 안심 카드 한 장, 단계 머리 = 번호 원(TalkBack `1단계 · 개인 정보`), `직접 확인 필요`는 노랑 띠가 아니라 마지막 단계 카드 안 */
    @Test
    fun manualModeUsesAssuranceCardAndNumberHeads() {
        val autofillUi = AutofillUi(context = ctx, values = FormValues.build(recipe.value, contents, emptyMap()))
        rule.setContent { ReadyPortTheme { ManualModeContent(autofillUi, {}, { _, _ -> }, {}) } }
        rule.onAllNodesWithText(s(R.string.settings_local_only_title)).assertCountEquals(1)
        rule.onAllNodesWithText(s(R.string.guide_not_affiliated)).assertCountEquals(1)
        val steps = recipe.value.steps
        steps.forEachIndexed { i, step ->
            val name = s(R.string.country_step_eyebrow, i + 1, step.titleKo)
            rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(name))
            rule.onNode(hasContentDescription(name)).assertIsDisplayed()
            assertTrue(rule.onAllNodesWithText(s(R.string.manual_step_eyebrow, i + 1)).fetchSemanticsNodes().isEmpty())
        }
        // 직접 확인할 것은 마지막 단계 머리보다 아래(그 카드 안)
        val human = s(R.string.autofill_human_banner)
        scrollTo(human)
        rule.onAllNodesWithText(human).assertCountEquals(1)
        val lastHead = rule.onNode(hasContentDescription(s(R.string.country_step_eyebrow, steps.size, steps.last().titleKo)))
            .fetchSemanticsNode().boundsInRoot
        assertTrue(rule.onNodeWithText(human).fetchSemanticsNode().boundsInRoot.top > lastHead.bottom)
    }
}
