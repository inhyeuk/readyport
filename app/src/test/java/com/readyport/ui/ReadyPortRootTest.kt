package com.readyport.ui

import android.app.Application
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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** M1 완료 기준: 모든 탭 이동, 쉬운 모드 전환, 글자 확대·TalkBack 동작 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class ReadyPortRootTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int) = context.getString(id)

    private var settings by mutableStateOf<AppSettings?>(AppSettings(easyMode = false))
    private val spoken = mutableListOf<String>()

    private fun launch(initial: AppSettings?) {
        settings = initial
        rule.setContent {
            ReadyPortRoot(
                settings = settings,
                onSetEasyMode = { settings = AppSettings(easyMode = it) },
                onSpeak = { spoken += it },
                slots = FakeSlots,
            )
        }
    }

    private fun heading(@StringRes title: Int) =
        rule.onNode(isHeading() and hasText(s(title)))

    /** 목록 아래쪽 항목은 스크롤해야 그려진다 */
    private fun scrollTo(matcher: SemanticsMatcher) {
        rule.onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    private fun tab(@StringRes label: Int) =
        rule.onNode(hasText(s(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    @Test
    fun everyTabNavigates() {
        launch(AppSettings(easyMode = false))
        heading(R.string.home_title).assertIsDisplayed()
        tab(R.string.tab_explore).assertIsSelected()

        val tabs = listOf(
            R.string.tab_trip to R.string.trips_title,
            R.string.tab_help to R.string.help_title,
            R.string.tab_settings to R.string.settings_title,
            R.string.tab_explore to R.string.home_title,
        )
        for ((label, title) in tabs) {
            tab(label).performClick()
            heading(title).assertIsDisplayed()
            tab(label).assertIsSelected()
        }
    }

    /**
     * 둘러보기에서 도움까지는 **탭 막대 한 번**이다 (운영자 2026-10-03: 둘러보기 안의 `급할 때는 도움` 줄은 같은 길이 두 개라 지웠다).
     * 급할 때 가는 길이 사라지지 않았음을 지킨다 — 스크롤 없이, 둘러보기 어디에서나 보이는 탭으로.
     */
    @Test
    fun helpIsOneTapFromExploreViaTheTabBar() {
        launch(AppSettings(easyMode = false))
        heading(R.string.home_title).assertIsDisplayed()
        // 둘러보기 본문에는 SOS 바로가기 줄이 없다 — 도움 탭이 그 일을 맡는다
        assertEquals(0, rule.onAllNodesWithText(s(R.string.help_shortcut_title)).fetchSemanticsNodes().size)
        tab(R.string.tab_help).performClick()
        heading(R.string.help_title).assertIsDisplayed()
        tab(R.string.tab_help).assertIsSelected()
    }

    /** 내 여행 탭의 첫 화면은 여행 목록이다 (2026-10-03 부록 H — 여행 줄기의 시작점) */
    @Test
    fun tripTabStartsAtTheTripList() {
        launch(AppSettings(easyMode = false))
        tab(R.string.tab_trip).performClick()
        heading(R.string.trips_title).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.trips_empty_title)).assertIsDisplayed()
        tab(R.string.tab_trip).assertIsSelected()
    }

    @Test
    fun countryPhotoCardOpensCountryWithSections() {
        launch(AppSettings(easyMode = false))
        scrollTo(hasContentDescription(context.getString(R.string.home_country_open, "태국")))
        rule.onNodeWithContentDescription(context.getString(R.string.home_country_open, "태국")).performClick()
        rule.onNode(isHeading() and hasText("태국")).assertIsDisplayed()
        tab(R.string.tab_explore).assertIsSelected()
        // 입국·비자: 정부 비제휴 고지가 맨 위, 입국 카드 입력 도우미
        rule.onNodeWithText(s(R.string.guide_not_affiliated)).assertIsDisplayed()
        scrollTo(hasText(s(R.string.prepare_form_open)))
        rule.onNodeWithText(s(R.string.prepare_form_open)).assertIsDisplayed()
        scrollTo(hasText(s(R.string.country_tab_shopping)))
        rule.onNodeWithText(s(R.string.country_tab_shopping)).performClick()
        scrollTo(hasText(s(R.string.shopping_open)))
        scrollTo(hasContentDescription(s(R.string.country_back)))
        rule.onNodeWithContentDescription(s(R.string.country_back)).performClick()
        // 돌아오면 둘러보기의 **그 자리**(누르고 간 나라 타일)다 — 히어로는 위로 올라가 있으므로 올려 보고 제목을 확인한다
        rule.onNodeWithContentDescription(context.getString(R.string.home_country_open, "태국")).assertIsDisplayed()
        tab(R.string.tab_explore).assertIsSelected()
        scrollTo(hasText(s(R.string.home_title)))
        heading(R.string.home_title).assertIsDisplayed()
    }

    @Test
    fun settingsShowsLocalOnlyPromiseAndMyInfo() {
        launch(AppSettings(easyMode = false))
        tab(R.string.tab_settings).performClick()
        rule.onNodeWithText(s(R.string.settings_local_only_title)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.settings_myinfo_open)).performClick()
        heading(R.string.wallet_title).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.settings_local_only_title)).assertIsDisplayed()
        tab(R.string.tab_settings).assertIsSelected()
    }

    @Test
    fun firstRunYesTurnsOnEasyMode() {
        launch(AppSettings(easyMode = null))
        heading(R.string.first_run_title).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.first_run_yes)).performClick()
        assertEquals(true, settings?.easyMode)
        heading(R.string.home_title).assertIsDisplayed()
        // 쉬운 모드 공통 줄: 홈 자신에서는 `처음으로` 없이 `소리로 듣기`만 (재검토2 ⑤#12 — 다른 화면에는 둘 다)
        rule.onNodeWithText(s(R.string.action_listen)).assertIsDisplayed()
        rule.onAllNodesWithText(s(R.string.action_home)).assertCountEquals(0)
    }

    @Test
    fun firstRunNoKeepsBasicMode() {
        launch(AppSettings(easyMode = null))
        // 첫 실행 화면은 세로 스크롤 — 작은 창(320×470)에서는 두 번째 카드까지 스크롤해서 누른다
        rule.onNodeWithText(s(R.string.first_run_no)).performScrollTo().performClick()
        assertEquals(false, settings?.easyMode)
        rule.onAllNodesWithText(s(R.string.action_home)).assertCountEquals(0)
    }

    @Test
    fun easyModeToggleInSettings() {
        launch(AppSettings(easyMode = false))
        tab(R.string.tab_settings).performClick()
        heading(R.string.settings_title).assertIsDisplayed()
        rule.onAllNodesWithText(s(R.string.action_home)).assertCountEquals(0)

        scrollTo(hasText(s(R.string.settings_easy_mode)))
        rule.onNodeWithText(s(R.string.settings_easy_mode)).performClick()
        assertEquals(true, settings?.easyMode)
        scrollTo(hasText(s(R.string.action_home)))
        rule.onNodeWithText(s(R.string.action_home)).assertIsDisplayed()

        scrollTo(hasText(s(R.string.settings_easy_mode)))
        rule.onNodeWithText(s(R.string.settings_easy_mode)).performClick()
        assertEquals(false, settings?.easyMode)
        rule.onAllNodesWithText(s(R.string.action_home)).assertCountEquals(0)
    }

    @Test
    fun easyModeHomeButtonReturnsHome() {
        launch(AppSettings(easyMode = true))
        tab(R.string.tab_help).performClick()
        heading(R.string.help_title).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.action_home)).performClick()
        heading(R.string.home_title).assertIsDisplayed()
        tab(R.string.tab_explore).assertIsSelected()
    }

    @Test
    fun easyModeListenSpeaksScreenSummary() {
        launch(AppSettings(easyMode = true))
        tab(R.string.tab_help).performClick()
        rule.onNodeWithText(s(R.string.action_listen)).performClick()
        assertEquals(listOf(s(R.string.help_speech)), spoken)
    }

    @Test
    fun worksAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2.0f)
        launch(AppSettings(easyMode = true))
        heading(R.string.home_title).assertIsDisplayed()
        for (label in listOf(R.string.tab_trip, R.string.tab_help, R.string.tab_settings, R.string.tab_explore)) {
            tab(label).assertIsDisplayed().performClick()
            tab(label).assertIsSelected()
        }
    }

    @Test
    fun talkBackLabels() {
        launch(AppSettings(easyMode = false))
        // 사진 카드는 나라 이름으로 읽힌다
        scrollTo(hasContentDescription(context.getString(R.string.home_country_open, "일본")))
        rule.onNodeWithContentDescription(context.getString(R.string.home_country_open, "일본")).assertIsDisplayed()
        // 내 여행 탭은 여행 목록으로 열린다(빈 목록 안내)
        tab(R.string.tab_trip).performClick()
        rule.onNodeWithText(s(R.string.trips_empty_title)).assertIsDisplayed()
        // 탭 4개 모두 Tab 역할과 이름을 가진다
        for (label in listOf(R.string.tab_explore, R.string.tab_trip, R.string.tab_help, R.string.tab_settings)) {
            tab(label).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        }
    }

    @Test
    fun subScreensHaveBackButtonTabsDoNot() {
        launch(AppSettings(easyMode = false))
        rule.onAllNodes(hasContentDescription(s(R.string.action_back))).assertCountEquals(0)
        tab(R.string.tab_settings).performClick()
        rule.onAllNodes(hasContentDescription(s(R.string.action_back))).assertCountEquals(0)
        rule.onNodeWithText(s(R.string.settings_myinfo_open)).performClick()
        heading(R.string.wallet_title).assertIsDisplayed()
        rule.onNodeWithContentDescription(s(R.string.action_back)).assertIsDisplayed().performClick()
        heading(R.string.settings_title).assertIsDisplayed()
    }
}
