package com.readyport.ui.home

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.data.settings.AppSettings
import com.readyport.ui.FakeSlots
import com.readyport.ui.ReadyPortRoot
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 홈 첫 화면 예산 (DESIGN_SPEC 6-01, 6장 머리말): 히어로·신뢰 칩·섹션 머리를 꾸며도 나라 사진이 첫 화면에 보여야 한다.
 * 루트 테스트(ReadyPortRootTest·ScreenCaptureTest)는 스크롤 없이 나라 타일을 바로 누르므로, 타일이 탭 막대 위로
 * 충분히(MIN_VISIBLE_DP) 올라와 있는지 지킨다.
 */
abstract class HomeFirstScreenBase {

    @get:Rule
    val rule = createComposeRule()

    protected val context: Context = ApplicationProvider.getApplicationContext()

    protected fun launch(easy: Boolean, online: Boolean = true) {
        rule.setContent {
            ReadyPortRoot(settings = AppSettings(easyMode = easy), onSetEasyMode = {}, onSpeak = {}, online = online, slots = FakeSlots)
        }
        rule.waitForIdle()
    }

    private fun homeTab() = rule.onNode(
        hasText(context.getString(R.string.tab_explore)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
    ).fetchSemanticsNode()

    /** 탭 막대 위쪽에서 [name] 나라 타일 위쪽까지(dp) = 첫 화면에 보이는 타일 높이. 그려지지 않았으면 null */
    protected fun visibleDp(name: String, label: String): Float? {
        val tile = rule.onAllNodes(hasContentDescription(context.getString(R.string.home_country_open, name)))
            .fetchSemanticsNodes().firstOrNull()
        val d = rule.density.density
        val tab = homeTab()
        println("HOMEBUDGET $label: tile($name) top=${tile?.boundsInRoot?.top?.div(d)} tabTop=${tab.boundsInRoot.top / d} tabH=${tab.boundsInRoot.height / d}")
        return tile?.let { (tab.boundsInRoot.top - it.boundsInRoot.top) / d }
    }

    protected fun tabBarHeightDp(): Float = homeTab().boundsInRoot.height / rule.density.density

    protected fun assertVisible(name: String, label: String) {
        val v = visibleDp(name, label)
        assertNotNull("$label: $name 타일이 첫 화면에 없음", v)
        assertTrue("$label: $name 타일이 첫 화면에 ${v}dp만 보임 (< $MIN_VISIBLE_DP)", v!! >= MIN_VISIBLE_DP)
        assertValueLineOnFirstScreen(label)
    }

    /** 재검토 R13: 핵심 가치 한 줄(`입국 카드 칸은 앱이 채우고, 제출만 직접 눌러요`)이 어느 모드에서나 첫 화면(탭 막대 위)에 다 보인다 */
    private fun assertValueLineOnFirstScreen(label: String) {
        val node = rule.onAllNodes(hasText(context.getString(R.string.home_value_prop))).fetchSemanticsNodes().firstOrNull()
        assertNotNull("$label: 가치 문장이 없음", node)
        val d = rule.density.density
        val bottom = node!!.boundsInRoot.bottom / d
        val tabTop = homeTab().boundsInRoot.top / d
        println("HOMEBUDGET $label: value bottom=$bottom tabTop=$tabTop")
        assertTrue("$label: 가치 문장이 첫 화면 밖 ($bottom > $tabTop)", bottom <= tabTop)
    }

    companion object {
        /**
         * 개편 전 배치의 실측(같은 조건): 쉬운 모드+오프라인 일본 14dp, 쉬운 모드+200% 태국 20dp, 320×470 태국 27dp.
         * 그보다 나빠지지 않게 20dp 이상을 요구한다.
         */
        const val MIN_VISIBLE_DP = 20f
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
class HomeFirstScreenTest : HomeFirstScreenBase() {

    @Test fun basicShowsFirstCountry() {
        launch(easy = false)
        assertVisible("태국", "basic")
    }

    /** ScreenCaptureTest.guideAndOffline: 쉬운 모드 + 오프라인 배너에서 두 번째 나라(일본)를 바로 누른다 */
    @Test fun easyOfflineShowsSecondCountry() {
        launch(easy = true, online = false)
        assertVisible("일본", "easy-offline")
    }

    /** ScreenCaptureTest.easyModeDoubleFont: 쉬운 모드 + 글자 200%에서 첫 나라(태국)를 바로 누른다 */
    @Test fun easyDoubleFontShowsFirstCountry() {
        RuntimeEnvironment.setFontScale(2.0f)
        launch(easy = true)
        assertVisible("태국", "easy-200")
    }
}

/** Robolectric 기본 크기(320×470dp) — ReadyPortRootTest와 같은 조건 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class HomeSmallScreenTest : HomeFirstScreenBase() {

    @Test fun smallScreenShowsFirstCountry() {
        launch(easy = false)
        assertVisible("태국", "small")
        println("HOMEBUDGET small: tabBar=${tabBarHeightDp()}")
    }
}
