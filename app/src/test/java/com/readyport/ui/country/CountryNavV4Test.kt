package com.readyport.ui.country

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
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
 * 나라 화면 길잡이 v4 (2026-10-02 운영자: "메뉴가 볼품없으니 세련되게 이미지화해서 구성해줘"):
 * ① 히어로 아래 **그림 메뉴**(그림 카드 셋, 한 줄) — 패널은 단색이 아니라 그라데이션(고르기 채움 AccentSoft와 다른 말)
 * ② 그림 메뉴가 위로 지나가면 **접힌 고정 줄**이 맨 위에 — 두 메뉴가 동시에 있지 않다(TalkBack에도 한 벌)
 * ③ 접힌 줄에서 갈래를 바꾸면 새 갈래 첫 카드가 고정 줄 **바로 아래**에서 시작한다
 * 색을 재므로 NATIVE 그래픽으로 돌린다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h851dp-xhdpi")
class CountryNavV4Test {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun show(section: CountrySection = CountrySection.Entry) {
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions(), section) } }
    }

    /** 처음 화면: 그림 메뉴 한 벌만(접힌 줄 없음), 셋 다 Role.Tab, 고른 카드가 선택 상태 */
    @Test
    fun expandedMenuIsTheOnlyMenuAtTheTop() {
        show()
        CountrySection.entries.forEach { rule.onAllNodesWithText(s(it.label)).assertCountEquals(1) }
        rule.onNodeWithText(s(R.string.country_tab_entry)).assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        // 그림 메뉴는 히어로 아래(맨 위 고정 줄이 아니다)
        val card = rule.onNodeWithText(s(R.string.country_tab_travel)).getBoundsInRoot()
        assertTrue("그림 메뉴가 히어로 아래가 아니다: $card", card.top.value > 150f)
    }

    /** 그림 패널은 그라데이션: 고른 카드 패널은 오른쪽 아래가 왼쪽 위보다 짙고, 어느 지점도 AccentSoft 단색 채움이 아니다 */
    @Test
    fun panelsAreGradientsNotSelectionFill() {
        show()
        val b = rule.onNodeWithText(s(R.string.country_tab_entry)).getBoundsInRoot()
        val d = rule.density.density
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        // 패널 안쪽 모서리 근처(카드 안 6dp + 둥근 모서리 피해 10dp)
        val tl = Color(bmp.getPixel(((b.left.value + 12) * d).toInt(), ((b.top.value + 12) * d).toInt()))
        val w = b.right.value - b.left.value
        val panelBottom = b.top.value + 6 + (w - 12) / 1.3f
        val br = Color(bmp.getPixel(((b.right.value - 12) * d).toInt(), ((panelBottom - 6) * d).toInt()))
        assertTrue("고른 패널이 그라데이션이 아니다: $tl / $br", luminance(br) < luminance(tl) - 0.03f)
        assertTrue("패널 왼쪽 위가 AccentSoft 단색 채움과 같다", distance(tl, Tokens.AccentSoft) > 0.02f)
    }

    /** 스크롤해서 그림 메뉴가 지나가면 접힌 고정 줄이 맨 위에 — 그때도 탭은 한 벌, 선택은 그대로 */
    @Test
    fun compactBarTakesOverAfterScrolling() {
        show(CountrySection.Travel)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.explore_maps_title)))
        rule.waitForIdle()
        CountrySection.entries.forEach { rule.onAllNodesWithText(s(it.label)).assertCountEquals(1) }
        val strip = rule.tabStrip(s(R.string.country_sections, "태국"))
        strip.assertIsDisplayed()
        assertTrue("접힌 줄이 맨 위가 아니다", strip.getBoundsInRoot().top.value <= 1f)
        rule.onNodeWithText(s(R.string.country_tab_travel)).assertIsSelected()
    }

    /** 접힌 줄에서 갈래를 바꾸면 첫 카드가 고정 줄 바로 아래(가리지 않고, 멀리 떨어지지도 않게)에서 시작한다 */
    @Test
    fun switchingFromCompactBarStartsRightBelowIt() {
        show(CountrySection.Travel)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.explore_maps_title)))
        rule.onNodeWithText(s(R.string.country_tab_entry)).performClick()
        rule.waitForIdle()
        val strip = rule.tabStrip(s(R.string.country_sections, "태국")).getBoundsInRoot()
        val first = rule.onNodeWithText(s(R.string.guide_not_affiliated)).getBoundsInRoot()
        val gap = first.top.value - strip.bottom.value
        assertTrue("첫 카드가 고정 줄 바로 아래가 아니다: gap=$gap ($first / $strip)", gap >= -1f && gap <= 40f)
    }

    /** 그림 메뉴에서 갈래를 바꿔도 같은 약속: 메뉴는 위로 지나가고 고정 줄 아래에서 새 갈래가 시작한다 */
    @Test
    fun switchingFromExpandedMenuScrollsContentUnderTheBar() {
        show()
        rule.onNodeWithText(s(R.string.country_tab_travel)).performClick()
        rule.waitForIdle()
        val strip = rule.tabStrip(s(R.string.country_sections, "태국")).getBoundsInRoot()
        assertTrue("고정 줄이 맨 위가 아니다: $strip", strip.top.value <= 1f)
        CountrySection.entries.forEach { rule.onAllNodesWithText(s(it.label)).assertCountEquals(1) }
        rule.onNodeWithText(s(R.string.country_tab_travel)).assertIsSelected()
    }

    private fun luminance(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

    private fun distance(a: Color, b: Color) =
        maxOf(kotlin.math.abs(a.red - b.red), kotlin.math.abs(a.green - b.green), kotlin.math.abs(a.blue - b.blue))
}

/**
 * 좁은 칸(320dp 기기에서 화면 분할·팝업 창처럼 260dp만 받을 때) + 쉬운 모드 + 글자 200%: 짧은 라벨로도 한 줄이 안 되면
 * 그림 카드 셋을 폭 전체 행으로 쌓는다(그림은 그대로, 글자는 다 보인다). 320dp 창 전체에서는 짧은 라벨이 아직 한 줄에 들어간다.
 * 창 높이는 2400dp — 쌓인 세 행이 모두 그려진 채로 위치를 잰다(글자 200% 쉬운 모드 히어로·버튼이 길다).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w320dp-h2400dp-xhdpi", fontScale = 2.0f)
class CountryNavV4StackedTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun cardsStackIntoFullWidthRowsWhenLabelsDoNotFit() {
        rule.setContent {
            ReadyPortTheme(easyMode = true) {
                // 바깥 Box가 창 폭 고정 제약을 풀어 준다(뿌리에 바로 width를 주면 창 폭으로 늘어난다)
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.width(260.dp)) { CountryContent(TestPacks.countryUi("TH"), CountryActions()) }
                }
            }
        }
        val rects = CountrySection.entries.map { rule.onNodeWithText(context.getString(it.label)).getBoundsInRoot() }
        rects.zipWithNext { a, b -> assertTrue("쌓인 행이 위아래 순서가 아니다: $rects", b.top.value >= a.bottom.value - 1f) }
        rects.forEach { r -> assertEquals("쌓인 행이 같은 폭이 아니다: $rects", rects.first().right.value - rects.first().left.value, r.right.value - r.left.value, 1f) }
        CountrySection.entries.forEach {
            rule.onNodeWithText(context.getString(it.label)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
        // 눈으로 확인할 캡처 (build/gallery/nav_v4/stacked_260dp_easy_font200.png)
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = java.io.File("build/gallery/nav_v4").apply { mkdirs() }
        java.io.File(dir, "stacked_260dp_easy_font200.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
