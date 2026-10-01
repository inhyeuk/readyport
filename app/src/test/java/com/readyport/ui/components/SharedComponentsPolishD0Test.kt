package com.readyport.ui.components

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.country.CountryActions
import com.readyport.ui.country.CountryContent
import com.readyport.ui.country.CountrySection
import com.readyport.ui.pack.HelpContent
import com.readyport.ui.tabs.PrepareContent
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 다듬기 D0(공용 부품): 운영자 결정(6·10·13·2)과 재검토2 공용 지적을 부품이 지키는지.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h851dp")
class SharedComponentsPolishD0Test {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    /** ①#1: 버튼은 바탕 흰색(테두리), 선택은 바탕 AccentSoft — 보조 버튼·숫자 타일에 AccentSoft 채움이 없다 */
    @Test
    fun secondaryButtonsAreWhiteOutlinedAndStatTilesSitOnGround() {
        val accent = secondaryButtonColors()
        assertEquals(Tokens.Surface, accent.container)
        assertEquals(Tokens.Accent, accent.border)
        assertEquals(1.5.dp, accent.borderWidth)
        val neutral = secondaryButtonColors(BadgeTone.Neutral)
        // 비선택 칩(흰 바탕 + 1dp LineStrong)과 다른 모양
        assertEquals(Tokens.SurfaceSunken, neutral.container)
        assertEquals(Tokens.LineStrong, neutral.border)
        BadgeTone.entries.filter { it != BadgeTone.Neutral && it != BadgeTone.Navy && it != BadgeTone.OnDark }.forEach {
            assertTrue("$it 보조 버튼이 AccentSoft로 채워짐", secondaryButtonColors(it).container != Tokens.AccentSoft)
        }
        assertEquals(Tokens.Ground, StatTileContainer)
        assertEquals(1.dp, CardBorderWidth)
    }

    /** 결정 10: 요약 모양은 한 줄(첫 사실의 첫 문장) + 펼치기, 출처는 접혀도 보이고 링크는 펼쳐야 보인다 */
    @Test
    fun returnCheckSummaryShowsOneLineThenEverything() {
        val index = TestPacks.index.value
        rule.setContent {
            ReadyPortTheme {
                ReturnCheckCard(index.returnLinks, index.returnFacts, index.sources.associate { it.id to it.name }, {}, ReturnCheckMode.Summary)
            }
        }
        val first = index.returnFacts.first()
        rule.onNodeWithText(firstSentence(first.textKo).first, useUnmergedTree = true).assertIsDisplayed()
        // 둘째 사실부터는 접힌 동안 보이지 않는다
        index.returnFacts.drop(1).forEach { f ->
            rule.onAllNodesWithText(firstSentence(f.textKo).first, useUnmergedTree = true).assertCountEquals(0)
        }
        rule.onAllNodesWithText(index.returnLinks.first().labelKo).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.return_check_lead)).assertCountEquals(0)
        // 출처는 접힘 밖
        rule.onAllNodes(hasText("출처", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().also { assertTrue("출처 줄이 접힌 요약에서 사라짐", it.isNotEmpty()) }
        rule.onNodeWithText(s(R.string.return_check_full)).performClick()
        rule.waitForIdle()
        index.returnFacts.forEach { f -> rule.onNodeWithText(f.textKo.trim(), useUnmergedTree = true).assertExists() }
        rule.onNodeWithText(index.returnLinks.first().labelKo).assertExists()
        // 펼친 뒤 접기 버튼은 무엇을 접는지 TalkBack 이름으로
        rule.onNode(hasContentDescription(s(R.string.collapse_target_cd, s(R.string.fold_target_return)))).assertExists()
        // 바뀌는 사실 행 묶음은 liveRegion
        assertTrue(
            rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /** ②#2: 펼침 줄은 대상을 이름에 담고, 펼친 내용은 liveRegion 안에 나타난다 */
    @Test
    fun expandableDetailNamesItsTargetAndAnnounces() {
        rule.setContent {
            ReadyPortTheme {
                ExpandableDetail(label = s(R.string.essentials_more, "보조배터리"), target = s(R.string.fold_target_item, "보조배터리")) {
                    Text("펼친 글")
                }
            }
        }
        rule.onNodeWithText(s(R.string.essentials_more, "보조배터리")).performClick()
        rule.waitForIdle()
        rule.onNode(hasContentDescription(s(R.string.collapse_target_cd, s(R.string.fold_target_item, "보조배터리")))).assertExists()
        rule.onNodeWithText("펼친 글").assertIsDisplayed()
        assertTrue(
            rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /** ③#3: 꼭 채울 칸 표시 = 작은 느낌표(TalkBack `빈칸`) + 묶음 요약 한 줄 — `꼭 채워요` 글자 태그 없음 */
    @Test
    fun requiredMarkAndSummary() {
        rule.setContent {
            ReadyPortTheme {
                Column {
                    RequiredSummary(total = 9, required = 7)
                    RequiredMark()
                }
            }
        }
        rule.onNodeWithText(s(R.string.required_summary, 7, 9)).assertIsDisplayed()
        rule.onNode(hasContentDescription(s(R.string.form_blank_cd))).assertExists()
        rule.onAllNodesWithText("꼭 채워요").assertCountEquals(0)
    }

    /** ④#2: 단계 글 모양은 부품이 길이로 고른다 — 짧은 제목은 굵은 제목 글자, 문장이 하나라도 있으면 본문 글자 */
    @Test
    fun stepListPicksSentenceStyleByLength() {
        val sentence = "제출 뒤 요약 화면에서 버튼을 누르고 결제를 마쳐요."
        val title = "공항에 가요"
        rule.setContent {
            ReadyPortTheme {
                Column {
                    StepList(listOf(Step(title), Step(sentence)))
                    StepList(listOf(Step("체크인해요"), Step("짐을 부쳐요")))
                }
            }
        }
        assertEquals(FontWeight.Normal, layoutOf(sentence).layoutInput.style.fontWeight ?: FontWeight.Normal)
        assertEquals(FontWeight.Normal, layoutOf(title).layoutInput.style.fontWeight ?: FontWeight.Normal)
        assertEquals(FontWeight.SemiBold, layoutOf("체크인해요").layoutInput.style.fontWeight)
        assertTrue(isSentenceStep(sentence))
        assertFalse(isSentenceStep(title))
    }

    /** ④#5: 지우기 폭은 뜻으로 — 카드 단위 = 폭 전체, 목록 항목 = 끝 정렬 */
    @Test
    fun dangerButtonPlacementDecidesWidth() {
        rule.setContent {
            ReadyPortTheme {
                Box(Modifier.width(320.dp).testTag("box")) {
                    Column {
                        DangerButton("카드 지우기", onClick = {}, placement = ButtonPlacement.CardAction)
                        DangerButton("항목 지우기", onClick = {}, placement = ButtonPlacement.ItemAction)
                    }
                }
            }
        }
        val box = rule.onNodeWithTag("box").getBoundsInRoot()
        val card = rule.onNodeWithText("카드 지우기").getBoundsInRoot()
        assertTrue("카드 단위 지우기는 폭 전체", abs((card.right - card.left).value - (box.right - box.left).value) < 1f)
        val item = rule.onNode(hasText("항목 지우기") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).getBoundsInRoot()
        assertTrue("목록 항목 지우기는 글자 폭만큼", (item.right - item.left).value < (box.right - box.left).value * 0.8f)
        assertTrue("목록 항목 지우기는 끝 정렬", abs(item.right.value - box.right.value) < 1f)
    }

    /** ①#5: 쉬운 모드 `처음으로`·`소리로 듣기`는 같은 폭으로 내용선 끝까지 */
    @Test
    fun easyActionsShareWidthAndFillTheLine() {
        rule.setContent {
            ReadyPortTheme(easyMode = true) {
                AppScreen(title = "제목", speech = "말") { }
            }
        }
        val home = rule.onNode(hasText(s(R.string.action_home)) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).getBoundsInRoot()
        val listen = rule.onNode(hasText(s(R.string.action_listen)) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).getBoundsInRoot()
        assertEquals((home.right - home.left).value, (listen.right - listen.left).value, 1f)
        // 393dp 창 − 양옆 20dp = 내용선. 오른쪽 끝이 내용선(373dp)에 닿는다
        assertEquals(373f, listen.right.value, 1f)
        assertEquals(20f, home.left.value, 1f)
    }

    /** ③#1: 팩 문장 숫자 토큰 — 단위 목록 한 곳(밧·루피아·Wh·만/천 자리·통화 코드·단계) */
    @Test
    fun numberTokensCoverPackUnits() {
        fun tokens(t: String) = numberRanges(t).map { t.substring(it.first, it.last + 1) }
        assertEquals(listOf("20", "50", "100", "500", "1,000밧"), tokens("지폐는 20·50·100·500·1,000밧이에요."))
        assertEquals(listOf("1만 5천 달러"), tokens("미화 1만 5천 달러를 넘으면 신고해요."))
        assertEquals(listOf("1인", "2개", "160Wh"), tokens("1인당 2개(160Wh 이하)까지 들고 탈 수 있어요."))
        assertEquals(listOf("IDR 500,000"), tokens("비용은 IDR 500,000이에요."))
        assertEquals(listOf("1억 루피아"), tokens("1억 루피아 넘는 현금은 신고해요."))
        assertEquals(listOf("220 V", "60 Hz"), tokens("220 V, 60 Hz예요."))
        assertEquals(listOf("3단계"), tokens("3단계(출국권고)예요."))
        // 다음 낱말 첫 글자를 단위로 읽지 않고, 단위 없는 맨 숫자(번지·우편번호·날짜)는 굵게 하지 않는다
        assertEquals(emptyList<String>(), tokens("3 인도네시아"))
        assertEquals(emptyList<String>(), tokens("23 Thiam-Ruammit Road, Bangkok 10310 · 최종 확인 2026.09.29"))
    }

    /** 결정 13: `담았어요` — 체크 아이콘과 ✓ 글자가 겹치지 않게 */
    @Test
    fun inCartLabelHasNoCheckGlyph() {
        assertEquals("담았어요", s(R.string.shopping_in_cart))
        assertFalse(s(R.string.shopping_in_cart).contains('✓'))
    }

    /** 결정 2: 태국어 `원어민 검수 전` 표시를 어디에도 그리지 않는다(팩의 reviewed 값은 그대로) */
    @Test
    fun noNativeReviewBadgeOnHelp() {
        val help = TestPacks.helpUi()
        assertTrue("검사 대상 팩에 미검수 문장이 있어야 함", TestPacks.thailand.value.phrases.any { !it.reviewed })
        rule.setContent { ReadyPortTheme { HelpContent(help, {}, {}, {}) } }
        rule.waitForIdle()
        rule.onAllNodes(hasText("검수", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    /** ④#1·②#5: 같은 양식이면 나라 입국(03)과 여행 준비(18)의 비용 라벨·버튼 라벨이 같다 — `입국 카드` 한 말 */
    @Test
    fun entryFormCardSpeaksOneTermOnBothScreens() {
        var screen by androidx.compose.runtime.mutableStateOf(0)
        rule.setContent {
            ReadyPortTheme {
                if (screen == 0) {
                    CountryContent(TestPacks.countryUi("TH"), CountryActions(), CountrySection.Entry)
                } else {
                    PrepareContent(TestPacks.formEntries(), {})
                }
            }
        }
        listOf(0, 1).forEach { i ->
            rule.runOnIdle { screen = i }
            rule.waitForIdle()
            listOf(s(R.string.prepare_form_open), s(R.string.fact_label_form_fee)).forEach { label ->
                rule.onNode(hasScrollAction()).performScrollToNode(hasText(label))
                rule.onAllNodes(hasText(label), useUnmergedTree = true).fetchSemanticsNodes().also { assertTrue("$i: $label 없음", it.isNotEmpty()) }
            }
            rule.onAllNodes(hasText("입국 신고 비용", substring = true), useUnmergedTree = true).assertCountEquals(0)
        }
    }
}

private operator fun <T> androidx.compose.runtime.MutableState<T>.getValue(thisObj: Any?, p: kotlin.reflect.KProperty<*>) = value
private operator fun <T> androidx.compose.runtime.MutableState<T>.setValue(thisObj: Any?, p: kotlin.reflect.KProperty<*>, v: T) { value = v }
