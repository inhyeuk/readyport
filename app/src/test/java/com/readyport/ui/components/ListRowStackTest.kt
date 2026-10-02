package com.readyport.ui.components

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.pack.EssentialRule
import com.readyport.ui.prep.EssentialRow
import com.readyport.ui.prep.EssentialsContent
import com.readyport.ui.prep.EssentialsUi
import com.readyport.ui.settings.SettingsScreen
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** 설정 화면 행 제목들(배지 있는 ListRow 11줄 — 알림 묶음 2줄 포함) */
private val settingsTitles = listOf(
    R.string.settings_myinfo_open, R.string.wallet_companions_title, R.string.settings_easy_mode, R.string.settings_child_mode,
    R.string.settings_alerts, R.string.settings_alert_hour,
    R.string.explore_wifi_only, R.string.settings_privacy, R.string.settings_disclaimer, R.string.settings_credits, R.string.settings_about,
)

/** 꼭 챙길 물건 카드 이름이 짧은 것·긴 것 섞인 목록 */
private val essentialRows = listOf(
    EssentialRule("passport", "여권", "만료일이 6개월 넘게 남았는지 확인해요.", "always"),
    EssentialRule("pay", "결제 수단 두 가지", "카드 한 장이 막혀도 쓸 수 있게 두 가지를 챙겨요.", "always"),
    EssentialRule("medicine", "상비약", "먹던 약은 영문 처방전과 함께 챙겨요.", "always"),
)

/** 행 제목 글자 노드(합쳐지기 전 트리)의 왼쪽 끝(px) */
private fun lefts(rule: SemanticsNodeInteractionsProvider, titles: List<String>): Map<String, Float> =
    titles.associateWith { rule.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left }

private fun assertSameStart(where: String, lefts: Map<String, Float>) {
    val min = lefts.values.min()
    val off = lefts.filterValues { abs(it - min) > 1.5f }
    assertTrue("$where: 한 묶음 안 행 제목의 시작선이 갈림(행마다 쌓기 판정이 다름) — $lefts", off.isEmpty())
}

/**
 * 재검토2 ④#6: 큰 글자(sdk 31·200%)에서 목록 행은 **모두** 배지·끝 요소를 윗줄에 두고 제목을 폭 전체로 —
 * 행마다 '제목이 옆에 들어가는지'로 정하면 `여권·예약 서류 관리`(쌓임)와 `같이 가는 사람`(옆)의 시작선이 갈렸다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h8000dp", fontScale = 2.0f)
class ListRowStackLargeFontTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun settingsRowsStackTogether(easy: Boolean) {
        rule.setContent { ReadyPortTheme(easyMode = easy) { SettingsScreen(easyMode = easy, onEasyModeChange = {}, notifGranted = true) } }
        val titles = settingsTitles.map { context.getString(it) }
        val l = lefts(rule, titles)
        assertSameStart("설정 200%" + if (easy) " 쉬운 모드" else "", l)
        // 쌓인 행의 제목은 배지 시작선(행 가로 여백)에서 시작 — 배지 옆이 아니다
        val badgeSide = rule.density.run { (LIST_ROW_TEXT_BESIDE_MIN_DP).toFloat() * density }
        assertTrue("200%에서 제목이 배지 옆에 섬(쌓이지 않음): $l", l.values.all { it < badgeSide })
    }

    @Test fun settingsRowsStackTogetherBasic() = settingsRowsStackTogether(easy = false)

    @Test fun settingsRowsStackTogetherEasy() = settingsRowsStackTogether(easy = true)

    @Test
    fun essentialsCardsStackTogether() {
        rule.setContent {
            ReadyPortTheme {
                EssentialsContent(EssentialsUi("태국", 4, 11, essentialRows.map { EssentialRow(it, false, null) }), { _, _ -> }, {})
            }
        }
        assertSameStart("꼭 챙길 물건 200%", lefts(rule, essentialRows.map { it.nameKo }))
    }
}

/** 기본 글자(100%)에서는 그대로 배지 옆 제목 — 큰 글자 규칙이 보통 크기까지 번지지 않는다 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h8000dp")
class ListRowStackDefaultFontTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun settingsTitlesSitBesideBadge() {
        rule.setContent { ReadyPortTheme { SettingsScreen(easyMode = false, onEasyModeChange = {}, notifGranted = true) } }
        val l = lefts(rule, settingsTitles.map { context.getString(it) })
        assertSameStart("설정 100%", l)
        val badgeSide = rule.density.run { (LIST_ROW_TEXT_BESIDE_MIN_DP).toFloat() * density }
        assertTrue("100%에서 제목이 배지 옆이 아님: $l", l.values.all { it >= badgeSide })
    }
}

/** 배지 옆 제목의 최소 시작선(dp): 화면 여백 20 + 행 여백 20 + 배지 40 + 간격 16 = 96 → 여유를 두고 80 */
private const val LIST_ROW_TEXT_BESIDE_MIN_DP = 80
