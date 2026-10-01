package com.readyport.ui.country

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 나라 화면 길잡이 v3 (2026-10-02 운영자 지적: 메뉴와 내용이 구분되지 않는다):
 * ① 탭 줄은 어떤 모드에서도 **가로 한 줄**이고 위에 고정된다 — 내용은 그 아래로 지나간다(가려지지 않는다)
 * ② 선택 표시는 **밑줄**이다 — 칸 바탕은 흰색(고른 칩처럼 Accent 채움이 아니다). 실제로 그린 화면의 픽셀로 확인한다
 * ③ 길 안내 타일은 **연한 톤 채움**이라 흰 읽는 카드와 색으로 갈린다 — 역시 픽셀로 확인한다
 * ④ 길 안내 타일은 읽는 카드 사이가 아니라 그 갈래 내용의 **맨 끝**에 모여 있다
 * 색을 재므로 NATIVE 그래픽으로 돌린다(LEGACY는 글자·채움을 제대로 그리지 않는다).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h851dp-xhdpi")
class CountryNavV3Test {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun flat(id: Int) = s(id).replace('\n', ' ')

    private fun show(section: CountrySection = CountrySection.Entry, easy: Boolean = false) {
        rule.setContent { ReadyPortTheme(easyMode = easy) { CountryContent(TestPacks.countryUi("TH"), CountryActions(), section) } }
    }

    @Test fun tabsStayOnOneHorizontalRow() = assertTabsOnOneRow(rule, context, easy = false)

    @Test fun tabsStayOnOneHorizontalRowInEasyMode() = assertTabsOnOneRow(rule, context, easy = true)

    /**
     * 선택 칸 = 아래 Accent 막대(바탕은 흰색) — 고른 칩(Accent 채움)과 다른 모양이어야 한다.
     * v4(그림 메뉴): 막대는 카드 아래 가운데 알약 모양(카드 아래 끝에서 6dp 위, 4dp 두께 → 가운데는 아래 끝에서 8dp).
     */
    @Test
    fun selectedTabIsUnderlinedNotFilled() {
        show()
        val tab = rule.onNodeWithText(s(R.string.country_tab_entry))
        tab.assertIsSelected()
        val bounds = tab.getBoundsInRoot()
        val d = rule.density.density
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val x = ((bounds.left.value + bounds.right.value) / 2 * d).toInt()
        // 글자 줄 위쪽 바탕: 흰색(채움 없음)
        assertColor(bmp, x, ((bounds.top.value + 3) * d).toInt(), Tokens.Surface, "선택 탭 바탕")
        // 칸 아래 가운데: Accent 막대
        assertColor(bmp, x, ((bounds.bottom.value - 8) * d).toInt(), Tokens.Accent, "선택 탭 밑줄")
        // 선택하지 않은 칸에는 밑줄이 없다
        val other = rule.onNodeWithText(s(R.string.country_tab_shopping)).getBoundsInRoot()
        val ox = ((other.left.value + other.right.value) / 2 * d).toInt()
        assertColor(bmp, ox, ((other.bottom.value - 8) * d).toInt(), Tokens.Surface, "비선택 탭 밑줄 없음")
    }

    /** 내용은 고정된 탭 줄 **아래로** 지나간다 — 스크롤해도 탭 줄이 보이고 읽는 카드가 그 위로 올라오지 않는다 */
    @Test
    fun contentScrollsUnderTheStickyTabs() {
        show(CountrySection.Travel)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.explore_maps_title)))
        rule.waitForIdle()
        val tabs = rule.tabStrip(s(R.string.country_sections, "태국"))
        tabs.assertIsDisplayed()
        val strip = tabs.getBoundsInRoot()
        assertTrue("탭 줄이 위에 고정되지 않았다: $strip", strip.top.value <= 24f)
        val card = rule.onNodeWithText(s(R.string.explore_maps_title)).getBoundsInRoot()
        assertTrue("읽는 카드가 탭 줄에 가려졌다: $card / $strip", card.top.value >= strip.bottom.value - 1f)
    }

    /** 갈래를 바꾸면 그 갈래 첫 카드가 고정된 탭 줄 바로 아래에서 시작한다 (scrollToKey) */
    @Test
    fun switchingTabsStartsTheNewSectionBelowTheTabs() {
        show(CountrySection.Travel)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.explore_maps_title)))
        rule.onNodeWithText(s(R.string.country_tab_shopping)).performClick()
        rule.waitForIdle()
        val strip = rule.tabStrip(s(R.string.country_sections, "태국")).getBoundsInRoot()
        assertTrue("탭 줄이 맨 위에 서지 않았다: $strip", strip.top.value <= 24f)
        val first = rule.onNodeWithText(s(R.string.shopping_title, "태국")).getBoundsInRoot()
        assertTrue("첫 카드가 탭 줄에 가려졌다: $first / $strip", first.top.value >= strip.bottom.value - 1f)
    }

    /** 길 안내 모자이크: 연한 톤 채움(읽는 카드는 흰색) + 내용 맨 끝 + 크기가 다른 타일 */
    @Test
    fun navMosaicIsTintedAndSitsAtTheEnd() {
        show(CountrySection.Travel)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.tile_videos)))
        rule.waitForIdle()
        val header = rule.onNodeWithText(s(R.string.nav_more_here)).getBoundsInRoot()
        val maps = rule.onNodeWithText(s(R.string.explore_maps_title)).getBoundsInRoot()
        assertTrue("길 안내 묶음이 읽는 카드(지도 저장)보다 위에 있다", header.top.value >= maps.top.value)
        val large = rule.onNodeWithText(flat(R.string.tile_phrases_emergency)).getBoundsInRoot()
        assertTrue("큰 타일이 묶음 머리 아래가 아니다", large.top.value >= header.bottom.value - 1f)
        val small = rule.onNodeWithText(s(R.string.move_title)).getBoundsInRoot()
        val largeW = large.right.value - large.left.value
        val smallW = small.right.value - small.left.value
        assertTrue("큰 타일이 폭 전체가 아니다: $largeW / $smallW", largeW > smallW + 1f)
        // 색: 큰 타일은 연한 Help 채움, 읽는 카드는 흰색
        val d = rule.density.density
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        assertColor(
            bmp, ((large.right.value - 3) * d).toInt(), ((large.top.value + large.bottom.value) / 2 * d).toInt(),
            Tokens.HelpSoft, "길 안내 타일 채움",
        )
        assertColor(bmp, ((maps.left.value - 8) * d).toInt(), ((maps.top.value + 4) * d).toInt(), Tokens.Surface, "읽는 카드 채움")
    }

    /** 타일은 모두 '가는 길' — 누르면 그 화면으로 (라벨은 그대로) */
    @Test
    fun mosaicTilesOpenTheirDestinations() {
        val opened = mutableListOf<String>()
        rule.setContent {
            ReadyPortTheme {
                CountryContent(
                    TestPacks.countryUi("TH"),
                    CountryActions(openHelp = { opened += "help:$it" }, openMove = { opened += "move" }, openVideos = { opened += "videos:$it" }),
                    CountrySection.Travel,
                )
            }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.tile_videos)))
        rule.onNodeWithText(flat(R.string.tile_phrases_emergency)).performClick()
        rule.onNodeWithText(s(R.string.move_title)).performClick()
        rule.onNodeWithText(s(R.string.tile_videos)).performClick()
        assertEquals(listOf("help:TH", "move", "videos:TH"), opened)
    }

    /** 입국·비자: 맨 아래 보조 버튼이 모자이크로 (`내 여행에 넣기` = 큰 타일) */
    @Test
    fun entrySectionEndsWithItsOwnMosaic() {
        val planned = mutableListOf<String>()
        rule.setContent {
            ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions(planTrip = { planned += it })) }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.nav_tile_plan_trip)))
        rule.onNodeWithText(s(R.string.nav_more_here)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.nav_tile_plan_trip)).performClick()
        assertEquals(listOf("TH"), planned)
    }
}

/** 쉬운 모드·기본 모드 + 글자 200% + sdk 31(S10) + 좁은 창(360dp): 가장 빡빡한 조합에서도 탭 줄은 가로 한 줄 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w360dp-h851dp-xhdpi", fontScale = 2.0f)
class CountryNavV3LargeFontTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun tabsStayOnOneHorizontalRowInEasyMode() = assertTabsOnOneRow(rule, context, easy = true)

    @Test fun tabsStayOnOneHorizontalRowInBasicMode() = assertTabsOnOneRow(rule, context, easy = false)

    /** 큰 글자에서도 갈래를 바꾸면 첫 카드가 고정된 탭 줄 아래에서 시작한다 */
    @Test
    fun switchingTabsKeepsTheFirstCardBelowTheTabs() {
        rule.setContent { ReadyPortTheme(easyMode = true) { CountryContent(TestPacks.countryUi("TH"), CountryActions(), CountrySection.Travel) } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(context.getString(R.string.explore_maps_title)))
        rule.onNodeWithText(context.getString(R.string.country_tab_shopping)).performClick()
        rule.waitForIdle()
        val strip = rule.tabStrip(context.getString(R.string.country_sections, "태국")).getBoundsInRoot()
        val first = rule.onNodeWithText(context.getString(R.string.shopping_title, "태국")).getBoundsInRoot()
        assertTrue("첫 카드가 탭 줄에 가려졌다: $first / $strip", first.top.value >= strip.bottom.value - 1f)
    }
}

// ---------------- 공용 단언 ----------------

/** 탭 셋이 같은 줄(같은 위·같은 높이, 왼쪽에서 오른쪽)에 있고 모두 Role.Tab인지 — 세로 목록으로 바뀌지 않았다는 확인 */
internal fun assertTabsOnOneRow(rule: ComposeContentTestRule, context: Context, easy: Boolean) {
    rule.setContent { ReadyPortTheme(easyMode = easy) { CountryContent(TestPacks.countryUi("TH"), CountryActions()) } }
    val tabs = CountrySection.entries.map { rule.onNodeWithText(context.getString(it.label)) }
    val rects = tabs.map { it.getBoundsInRoot() }
    rects.forEach { r ->
        assertEquals("탭이 같은 줄에 없다: $rects", rects.first().top.value, r.top.value, 1f)
        assertEquals("탭 높이가 다르다: $rects", rects.first().let { it.bottom.value - it.top.value }, r.bottom.value - r.top.value, 1f)
    }
    rects.zipWithNext { a, b -> assertTrue("탭이 왼쪽→오른쪽 한 줄이 아니다: $rects", a.right.value <= b.left.value + 1f) }
    tabs.forEach { it.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)) }
}

/** 탭 줄 묶음(설명이 `{나라} 안내 종류`인 노드) */
internal fun ComposeContentTestRule.tabStrip(label: String): SemanticsNodeInteraction =
    onNode(
        SemanticsMatcher("탭 줄 ($label)") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
        },
    )

/** [bmp]의 ([x], [y]) 픽셀이 [expected] 색인지 — 실제로 그린 화면으로 채움·밑줄 회귀를 막는다 */
internal fun assertColor(bmp: Bitmap, x: Int, y: Int, expected: Color, what: String) {
    val px = bmp.getPixel(x.coerceIn(0, bmp.width - 1), y.coerceIn(0, bmp.height - 1))
    val got = Color(px)
    val diff = maxOf(
        kotlin.math.abs(got.red - expected.red),
        kotlin.math.abs(got.green - expected.green),
        kotlin.math.abs(got.blue - expected.blue),
    )
    assertTrue("$what: ($x, $y) 색이 ${expected.hex()} 이 아니라 ${got.hex()}", diff <= 0.02f)
}

private fun Color.hex(): String = "#%02X%02X%02X".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
