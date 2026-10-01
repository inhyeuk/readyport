package com.readyport.ui.prep

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.pack.EssentialRule
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 16 꼭 챙길 물건 개편 (DESIGN_SPEC 6-16): 체크 카드 TalkBack·토글, 긴 이유 접기, 출처 이름 폴백 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class EssentialsUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int, vararg a: Any) = context.getString(id, *a)

    private val longReason = "지도·번역을 많이 써서 배터리가 빨리 닳아요. 부치는 짐에 넣을 수 없고, 1인당 2개(160Wh 이하)까지 들고 탈 수 있어요."

    private val rules = listOf(
        EssentialRule("passport", "여권", "출국과 입국에 꼭 필요해요.", "always"),
        EssentialRule(
            "power_bank", "보조배터리", longReason, "always", ruleBadge = "carry_on_only",
            source = "molit_powerbank_2026", lastVerified = "2026-09-29",
        ),
    )

    @Test
    fun checkRowIsOneCheckboxWithNameAndState() {
        var have by mutableStateOf(setOf<String>())
        rule.setContent {
            ReadyPortTheme {
                EssentialsContent(
                    EssentialsUi("태국", 4, 11, rules.map { EssentialRow(it, it.id in have, null) }),
                    { id, v -> have = if (v) have + id else have - id },
                    {},
                )
            }
        }
        val passport = rule.onNode(hasText("여권") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        passport.assertIsOff()
        passport.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, s(R.string.essentials_have_no)))
        passport.performClick()
        assertEquals(setOf("passport"), have)
        passport.assertIsOn()
        passport.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, s(R.string.essentials_have_yes)))
        // 진행 문장은 그대로 (5개 중 → 여기서는 2개 중 1개)
        rule.onNodeWithText(s(R.string.essentials_progress, 2, 1)).assertExists()
    }

    @Test
    fun longReasonShowsFirstSentenceAndSourceFallsBackToOfficialName() {
        rule.setContent {
            ReadyPortTheme { EssentialsContent(EssentialsUi(rows = rules.map { EssentialRow(it, false, null) }), { _, _ -> }, {}) }
        }
        val list = rule.onNode(hasScrollAction())
        list.performScrollToNode(hasText("지도·번역을 많이 써서 배터리가 빨리 닳아요."))
        // 나머지 문장은 '자세히 보기' 안 — 펼치기 전에는 없다
        rule.onAllNodesWithText("부치는 짐에 넣을 수 없고", substring = true).assertCountEquals(0)
        list.performScrollToNode(hasText(s(R.string.action_more)))
        rule.onNodeWithText(s(R.string.action_more)).performClick()
        rule.onAllNodesWithText("부치는 짐에 넣을 수 없고", substring = true).assertCountEquals(1)
        // 출처 이름을 못 찾으면 내부 ID 대신 '공식 안내'
        list.performScrollToNode(hasText(s(R.string.source_footer, s(R.string.source_official_fallback), "2026.09.29")))
        rule.onAllNodesWithText("molit_powerbank_2026", substring = true).assertCountEquals(0)
    }

    @Test
    fun splitLeadKeepsShortTextAndCutsAtFirstSentence() {
        assertEquals("짧은 글이에요. 두 문장." to null, splitLead("짧은 글이에요. 두 문장."))
        val (lead, rest) = splitLead(longReason)
        assertEquals("지도·번역을 많이 써서 배터리가 빨리 닳아요.", lead)
        assertEquals("부치는 짐에 넣을 수 없고, 1인당 2개(160Wh 이하)까지 들고 탈 수 있어요.", rest)
        // 문장 경계가 없으면 전체를 그대로
        val noBoundary = "가".repeat(80)
        assertEquals(noBoundary, splitLead(noBoundary).first)
        assertNull(splitLead(noBoundary).second)
    }
}
