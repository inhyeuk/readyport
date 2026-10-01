package com.readyport.ui.prep

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.pack.EssentialRule
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 16 꼭 챙길 물건 개편 (DESIGN_SPEC 6-16): 체크 카드 TalkBack·토글, 긴 이유 접기, 출처 이름 폴백 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class EssentialsUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int, vararg a: Any) = context.getString(id, *a)

    private val longReason = "지도·번역을 많이 써서 배터리가 빨리 닳아요. 부치는 짐에 넣을 수 없고, 1인당 2개(160Wh 이하)까지 들고 탈 수 있어요."
    private val ruleSentence = "부치는 짐에 넣을 수 없고, 1인당 2개(160Wh 이하)까지 들고 탈 수 있어요."

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
    fun ruleRowShowsRuleSentenceFirstAndSourceFallsBackToOfficialName() {
        rule.setContent {
            ReadyPortTheme { EssentialsContent(EssentialsUi(rows = rules.map { EssentialRow(it, false, null) }), { _, _ -> }, {}) }
        }
        val list = rule.onNode(hasScrollAction())
        // 규정 배지가 있는 물건은 규정 문장(숫자)을 먼저 — 출처 줄이 가리키는 내용이 접힌 곳에 숨지 않게 (원칙 1)
        list.performScrollToNode(hasText(ruleSentence))
        // 나머지 문장은 '자세히 보기' 안 — 펼치기 전에는 없다
        rule.onAllNodesWithText("배터리가 빨리 닳아요", substring = true).assertCountEquals(0)
        list.performScrollToNode(hasText(s(R.string.action_more)))
        rule.onNodeWithText(s(R.string.action_more)).performClick()
        rule.onAllNodesWithText("배터리가 빨리 닳아요", substring = true).assertCountEquals(1)
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
        // 규정 문장 우선: 숫자가 든 문장을 앞에, 나머지는 원래 순서로
        assertEquals(ruleSentence to "지도·번역을 많이 써서 배터리가 빨리 닳아요.", splitLead(longReason, preferRule = true))
        // 숫자 문장이 없으면 첫 문장 그대로
        val plain = "첫 문장은 이렇게 꽤 길게 이어지는 설명이라서 한 줄에 다 들어가지 않아요. 두 번째 문장도 길게 이어지면서 전체가 예순 자를 넉넉히 넘겨요."
        assertEquals("첫 문장은 이렇게 꽤 길게 이어지는 설명이라서 한 줄에 다 들어가지 않아요.", splitLead(plain, preferRule = true).first)
        // 문장 경계가 없으면 전체를 그대로
        val noBoundary = "가".repeat(80)
        assertEquals(noBoundary, splitLead(noBoundary).first)
        assertNull(splitLead(noBoundary).second)
    }
}

/**
 * 테스트 폰(S10, Android 12)과 같은 sdk 31·글자 200%: 출처 줄의 날짜가 줄 사이에서 쪼개지지 않는다(`2026.09.2 / 9` 방지),
 * 한국어 줄바꿈 보정(KoBreak)이 화면 글자에만 들어가고 TalkBack·테스트 글자는 원문 그대로다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h6000dp", fontScale = 2.0f)
class EssentialsLargeFontTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val longSource = "국토교통부 보조배터리 기내 반입 기준 (2026.4.8 발표)"
    private val rows = listOf(
        EssentialRule(
            "power_bank", "보조배터리",
            "지도·번역을 많이 써서 배터리가 빨리 닳아요. 부치는 짐에 넣을 수 없고, 1인당 2개(160Wh 이하)까지 들고 탈 수 있어요.",
            "always", ruleBadge = "carry_on_only", source = "molit_powerbank_2026", lastVerified = "2026-09-29",
        ),
        EssentialRule("pay", "결제 수단 두 가지", "카드 한 장이 막혀도 쓸 수 있게 두 가지를 챙겨요.", "always", source = "x", lastVerified = "2026-09-29"),
    )

    private fun dateStaysOnOneLine(easy: Boolean) {
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                EssentialsContent(EssentialsUi("태국", 4, 11, rows.map { EssentialRow(it, false, longSource) }), { _, _ -> }, {})
            }
        }
        val date = "2026.09.29"
        val full = context.getString(R.string.source_footer, longSource, date)
        val nodes = rule.onAllNodesWithText(full).fetchSemanticsNodes()
        assertEquals(2, nodes.size)
        rule.onAllNodesWithText(full).fetchSemanticsNodes().indices.forEach { i ->
            val results = mutableListOf<TextLayoutResult>()
            rule.onAllNodesWithText(full)[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.single()
            val shown = layout.layoutInput.text.text
            val start = shown.indexOf(date)
            assertTrue("날짜가 보이는 글자에 그대로 있어야 함: $shown", start >= 0)
            assertEquals("날짜가 두 줄로 쪼개짐: $shown", layout.getLineForOffset(start), layout.getLineForOffset(start + date.length - 1))
        }
        // 보정은 화면 글자에만 — TalkBack·테스트는 원문 이름
        rule.onAllNodesWithText("결제 수단 두 가지").assertCountEquals(1)
        rule.onAllNodesWithText("\ubcf4\uc870\ubc30\ud130\ub9ac").assertCountEquals(1)
    }

    @Test
    fun sourceDateIsNeverSplitBasic() = dateStaysOnOneLine(easy = false)

    @Test
    fun sourceDateIsNeverSplitEasy() = dateStaysOnOneLine(easy = true)
}

