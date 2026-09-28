package com.readyport.ui.pack

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.data.settings.AppSettings
import com.readyport.pack.PackOrigin
import com.readyport.ui.FakeSlots
import com.readyport.ui.ReadyPortRoot
import com.readyport.ui.TestPacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * M3 완료 기준: "비행기 모드에서 가이드·도움 탭 동작".
 * 네트워크가 항상 실패하는 상태에서, 커밋된 서명 팩만으로 화면이 모두 뜨는지 본다.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class OfflinePackTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int) = context.getString(id)

    private fun launchOffline() = rule.setContent {
        ReadyPortRoot(
            settings = AppSettings(easyMode = false),
            onSetEasyMode = {}, onSpeak = {},
            online = false,
            slots = FakeSlots,
        )
    }

    private fun tab(@StringRes label: Int) =
        rule.onNode(hasText(s(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private fun shown(text: String, substring: Boolean = false) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring))
        // 같은 문구가 여러 곳에 있을 수 있다(예: 준비 중인 나라 여러 개)
        rule.onAllNodesWithText(text, substring = substring).onFirst().assertIsDisplayed()
    }

    @Test
    fun bundledPacksVerifyWithProductionKey() {
        val th = TestPacks.thailand
        assertEquals(PackOrigin.Bundled, th.origin)
        assertEquals("TH", th.value.country)
        assertTrue(th.value.forms.any { it.id == "TH_TDAC" && it.officialUrl.startsWith("https://tdac.immigration.go.th") })
        // 모든 정책 항목의 출처가 sources 에 있다 (작업 규칙 6)
        val ids = th.value.sources.map { it.id }.toSet()
        (th.value.requirements.map { it.source } + th.value.forms.map { it.source } + th.value.sections.map { it.source } +
            th.value.emergency.map { it.source } + th.value.procedures.map { it.source }).forEach { assertTrue(it, it in ids) }
    }

    @Test
    fun offlineBannerShows() {
        launchOffline()
        rule.onNodeWithText(s(R.string.offline_banner)).assertIsDisplayed()
    }

    @Test
    fun helpTabWorksOffline() {
        launchOffline()
        tab(R.string.tab_help).performClick()
        // 선택된 문장 카드(현지어) + 긴급 전화 + 대사관 + 여권 분실 순서 + 영사콜센터
        rule.onNodeWithText("ห้องน้ำอยู่ที่ไหน").assertIsDisplayed()
        shown("1155")
        shown("+66-81-914-5803")
        shown("여권을 잃어버렸어요")
        shown("+82-2-3210-0404")
        assertEquals(0, TestPacks.remoteCalls)
    }

    @Test
    fun choosingPhraseUpdatesBigCard() {
        launchOffline()
        tab(R.string.tab_help).performClick()
        shown("경찰을 불러 주세요")
        rule.onNodeWithText("경찰을 불러 주세요").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("กรุณาเรียกตำรวจ"))
        rule.onNodeWithText("กรุณาเรียกตำรวจ").assertIsDisplayed()
    }

    @Test
    fun guideOpensOfflineWithSources() {
        launchOffline()
        tab(R.string.tab_explore).performClick()
        rule.onAllNodesWithText(s(R.string.explore_open_guide)).onFirst().performClick()
        rule.onNodeWithText("태국").assertIsDisplayed()
        shown("비자 없이 90일", substring = true)
        shown("태국 입국 카드 (TDAC)")
        // 정책 카드 아래 '출처 … · 최종 확인 …'
        shown(context.getString(R.string.source_footer, "외교부 해외안전여행 · 태국", "2026.09.28"))
    }

    @Test
    fun allMvpCountriesAreSavedOffline() {
        launchOffline()
        tab(R.string.tab_explore).performClick()
        for (name in listOf("태국", "일본", "싱가포르", "말레이시아", "인도네시아")) shown(name)
        shown(context.getString(R.string.explore_status_saved, "2026.09.28"))
    }
}
