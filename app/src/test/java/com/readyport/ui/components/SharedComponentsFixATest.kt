package com.readyport.ui.components

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 재검토 수정 A(R1~R11) 공용 부품의 순수 규칙 — Robolectric 없이 */
class SharedComponentRulesTest {

    /** R5: 판정 한 곳 — 393dp 창에서 예전 기준(2열 1.14배 미만, 큰 글자 1.3배 이상)과 같고, 폭이 좁으면 더 일찍 쌓는다 */
    @Test
    fun layoutInfoMatchesOldThresholdsAndFollowsWidth() {
        assertEquals(LayoutClass.Roomy, layoutInfoOf(393f, 1f, easyMode = false).layoutClass)
        assertEquals(2, layoutInfoOf(393f, 1.1f, easyMode = false).columns)
        assertEquals(1, layoutInfoOf(393f, 1.15f, easyMode = false).columns)
        assertEquals(LayoutClass.Compact, layoutInfoOf(393f, 1.2f, easyMode = false).layoutClass)
        assertEquals(LayoutClass.Stacked, layoutInfoOf(393f, 1.3f, easyMode = false).layoutClass)
        assertEquals(LayoutClass.Stacked, layoutInfoOf(393f, 2f, easyMode = false).layoutClass)
        // 쉬운 모드 100%는 1열이지만 쌓지 않는다 (글자 배율은 시스템 글자 크기 설정의 실제 배율 — 쉬운 모드 글자 크기는 넣지 않는다)
        val easy = layoutInfoOf(393f, 1f, easyMode = true)
        assertEquals(1, easy.columns)
        assertEquals(LayoutClass.Compact, easy.layoutClass)
        // 같은 120%라도 360dp 창이면 쌓는다 (창 폭 ÷ 글자 배율)
        assertEquals(LayoutClass.Stacked, layoutInfoOf(360f, 1.2f, easyMode = false).layoutClass)
        assertTrue(layoutInfoOf(320f, 1f, easyMode = false).narrow)
        assertFalse(layoutInfoOf(393f, 1f, easyMode = false).narrow)
    }

    /** R9: 기관별로 묶고 세부는 `, `로, 이름은 하나도 빠뜨리지 않는다 */
    @Test
    fun sourceLinesGroupByAgency() {
        val d = "2026.09.29"
        val lines = sourceLines(
            listOf(
                SourceRef("태국관광청 · 찬타부리 기념품", d),
                SourceRef("농림축산검역본부 휴대 식물 검역", d),
                SourceRef("관세청 여행자 휴대품 면세 범위", d),
                SourceRef("태국관광청 · 춤폰 쇼핑", d),
                SourceRef("농림축산검역본부 검역 제외 식물", d),
                SourceRef("태국관광청 · 나콘시탐마랏 기념품", d),
                SourceRef("태국관광청 · 춤폰 쇼핑", d),
            ),
        )
        assertEquals(
            listOf(
                SourceRef("태국관광청 · 찬타부리 기념품, 춤폰 쇼핑, 나콘시탐마랏 기념품", d),
                SourceRef("농림축산검역본부 휴대 식물 검역, 검역 제외 식물", d),
                SourceRef("관세청 여행자 휴대품 면세 범위", d),
            ),
            lines,
        )
        // 날짜가 같으면 한 덩어리(이름 줄은 줄바꿈, 날짜는 끝에 한 번)
        val blocks = sourceBlocks(lines)
        assertEquals(1, blocks.size)
        assertEquals(lines.joinToString("\n") { it.name }, blocks.single().name)
        // 기관 이름 전체가 다른 출처의 기관이면 그 기관으로: `주태국 대한민국 대사관` + `… · 사증 안내`
        val embassy = sourceLines(listOf(SourceRef("주태국 대한민국 대사관", d), SourceRef("주태국 대한민국 대사관 · 사증 안내", d)))
        assertEquals(listOf(SourceRef("주태국 대한민국 대사관 · 사증 안내", d)), embassy)
        // 날짜가 다르면 덩어리를 나눈다
        val mixed = sourceBlocks(listOf(SourceRef("관세청 여행자 휴대품 면세 범위", d), SourceRef("태국 이민국 TDAC", "2026.09.30")))
        assertEquals(2, mixed.size)
    }

    /** R11: 같은 분류라도 품목마다 다른 아이콘, 모르는 품목은 분류 아이콘 */
    @Test
    fun foodItemsGetDistinctIcons() {
        val icons = listOf("coffee_beans", "tea", "boxed_sweets", "fresh_mango", "umeboshi", "kaya", "bakkwa").map { IconKeys.item(it, "food") }
        assertEquals(icons.size, icons.distinct().size)
        assertEquals(IconKeys.item("durian_chips", "food"), IconKeys.item("salted_egg_snack", "food"))
        assertEquals(IconKeys.shoppingCategory("souvenir"), IconKeys.item("lacquerware", "souvenir"))
        assertEquals(IconKeys.item("silver", "souvenir"), IconKeys.item("peranakan_accessories", "souvenir"))
    }

    /** R11: 꺾쇠·화살표는 버튼 앞이 아니라 라벨 뒤 */
    @Test
    fun chevronsAreTrailingOnly() {
        assertTrue(isTrailingOnlyIcon(Icons.AutoMirrored.Outlined.NavigateNext))
        assertFalse(isTrailingOnlyIcon(Icons.Outlined.Lock))
        assertFalse(isTrailingOnlyIcon(null))
    }
}

/** 재검토 수정 A(R1~R11) 공용 부품의 동작 (기본 글자 크기). 글자 폭을 실제로 재도록 NATIVE 그래픽 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h851dp")
class SharedComponentsFixATest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** R1: 정보 칩은 누를 수 없다(클릭 동작·버튼 역할 없음) — 누를 수 있는 칩과 구분 */
    @Test
    fun infoChipIsNotInteractive() {
        rule.setContent { ReadyPortTheme { InfoChip("플러그", Icons.Outlined.Lock) } }
        rule.onNodeWithText("플러그").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        rule.onNodeWithText("플러그").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    /** R2: 큰 선택 카드는 RadioButton 역할 + 선택 상태, 그룹 안에서 하나만 */
    @Test
    fun selectableCardIsRadioWithState() {
        var picked by mutableStateOf("가")
        rule.setContent {
            ReadyPortTheme {
                Column(Modifier.selectableGroup()) {
                    listOf("가", "나").forEach { label ->
                        SelectableCard(selected = picked == label, onClick = { picked = label }) { Text(label) }
                    }
                }
            }
        }
        rule.onNodeWithText("가").assertIsSelected()
        rule.onNodeWithText("나").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        rule.onNodeWithText("나").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("나").assertIsSelected()
    }

    /** R3: 라벨-값 행 — 가린 값은 TalkBack이 점 대신 읽고, 끝 버튼은 따로 눌린다 */
    @Test
    fun keyValueRowMasksAndKeepsTrailingButtonSeparate() {
        var copied = 0
        rule.setContent {
            ReadyPortTheme {
                Column {
                    KeyValueRow("여권 번호", "L••••••C3", masked = true)
                    KeyValueRow(
                        "성", "ERIKSSON", subLabel = "Family Name", subLabelInline = true,
                        trailing = {
                            QuietButton("복사", onClick = { copied++ }, icon = Icons.Outlined.ContentCopy)
                        },
                    )
                }
            }
        }
        rule.onNodeWithText("L••••••C3").assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.kv_masked_cd), useUnmergedTree = true).assertIsDisplayed()
        rule.onNode(hasText("복사") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, copied)
        rule.onNodeWithText("ERIKSSON").assertIsDisplayed()
    }

    /**
     * R9: `최종 확인`과 날짜는 들어갈 자리가 있으면 같은 줄 — 날짜만 다음 줄로 넘어가 홀로 남지 않는다.
     * 칸 폭을 바꿔 가며 확인한다(이름 길이에 따라 줄 끝이 어디서 끊기든).
     */
    @Test
    fun verifiedLabelAndDateStayTogether() {
        val ref = SourceRef("국토교통부 보조배터리 기내 반입 기준 (2026.4.8 발표)", "2026.09.29")
        var width by mutableStateOf(200)
        rule.setContent { ReadyPortTheme { androidx.compose.foundation.layout.Box(Modifier.width(width.dp)) { SourceFooter(ref) } } }
        val full = context.getString(R.string.source_footer, ref.name, ref.verified)
        var checked = 0
        for (w in 200..380 step 6) {
            width = w
            rule.waitForIdle()
            val results = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(full).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.single()
            val shown = layout.layoutInput.text.text
            val dateAt = shown.lastIndexOf(ref.verified)
            val labelAt = shown.lastIndexOf('최', dateAt)
            assertTrue(dateAt > 0 && labelAt > 0)
            assertEquals(
                "w=$w: `최종 확인`과 날짜가 다른 줄: $shown",
                layout.getLineForOffset(labelAt),
                layout.getLineForOffset(dateAt + ref.verified.length - 1),
            )
            checked++
        }
        assertTrue(checked > 20)
    }

    /** R6: 칸 폭을 재서 한 줄에 들어가는 가장 큰 크기 — 좁으면 작은 크기로, 아주 좁으면 '-' 뒤에서만 줄바꿈 */
    @Test
    fun fitTextStepsDownToStayOnOneLine() {
        val number = "+66-81-914-5803"
        var width by mutableStateOf(400)
        rule.setContent {
            ReadyPortTheme {
                androidx.compose.foundation.layout.Box(Modifier.width(width.dp)) {
                    PhoneNumberText(number, com.readyport.ui.theme.Tokens.Ink)
                }
            }
        }
        fun layout(): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(number).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }
        rule.waitForIdle()
        val wide = layout()
        assertEquals(1, wide.lineCount)
        width = 170
        rule.waitForIdle()
        val narrow = layout()
        assertEquals("좁은 칸에서도 한 줄", 1, narrow.lineCount)
        assertTrue("좁으면 더 작은 글자", narrow.layoutInput.style.fontSize.value < wide.layoutInput.style.fontSize.value)
        width = 60
        rule.waitForIdle()
        val tiny = layout()
        assertTrue("아주 좁으면 여러 줄", tiny.lineCount > 1)
        // 숫자 한가운데서 끊기지 않는다: 줄이 바뀌는 자리 앞 글자는 '-'
        val shown = tiny.layoutInput.text.text
        for (line in 0 until tiny.lineCount - 1) {
            val end = tiny.getLineEnd(line)
            val before = shown.substring(0, end).trimEnd('\u200B').last()
            assertEquals("줄이 '-' 뒤에서만 바뀜: $shown", '-', before)
        }
    }

    /** R5: 테마가 판정을 한 번 내려 준다 (393dp·100% = 2열 Roomy) */
    @Test
    fun themeProvidesLayoutInfo() {
        var info: LayoutInfo? = null
        var extrasStat = 0f
        rule.setContent {
            ReadyPortTheme {
                info = LocalLayoutInfo.current
                extrasStat = LocalTypeExtras.current.stat.fontSize.value
            }
        }
        rule.waitForIdle()
        assertEquals(LayoutClass.Roomy, info?.layoutClass)
        assertEquals(2, info?.columns)
        assertTrue(extrasStat > 0f)
    }
}

/** 재검토 수정 A: 글자 200%(sdk 31, 선형 확대 — 테스트 폰 S10과 같은 조건)에서 내용을 숨기지 않고 배치만 바꾸는지 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h2000dp", fontScale = 2.0f)
class SharedComponentsFixALargeFontTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** R4·R5: '급할 때는 도움' 줄은 큰 글자에서도 설명을 보인다(예전 내 여행 화면은 180%↑에서 지웠다) */
    @Test
    fun helpShortcutKeepsBodyAtLargeFont() {
        var info: LayoutInfo? = null
        rule.setContent {
            ReadyPortTheme(easyMode = true) {
                info = LocalLayoutInfo.current
                HelpShortcutRow(onClick = {})
            }
        }
        rule.waitForIdle()
        assertEquals(LayoutClass.Stacked, info?.layoutClass)
        rule.onNodeWithText(context.getString(R.string.today_help_body), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.help_shortcut_title), useUnmergedTree = true).assertIsDisplayed()
    }

    /** R5: 큰 글자 배치에서도 단계 아이콘을 빼지 않는다 — 글 첫 줄 안으로 옮긴다(의미 글자는 단계 글 그대로) */
    @Test
    fun stepListKeepsIconsInlineAtLargeFont() {
        val step = context.getString(R.string.today_departure_step2)
        rule.setContent { ReadyPortTheme { StepList(listOf(Step(step, Icons.Outlined.EventAvailable))) } }
        rule.onNodeWithText(step, useUnmergedTree = true).assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(step, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        // 보이는 글자 맨 앞에 아이콘 자리(인라인 아이콘)가 있다
        assertTrue(results.single().placeholderRects.isNotEmpty())
    }
}
