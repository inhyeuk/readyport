package com.readyport.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
import com.readyport.ui.components.Photos
import com.readyport.ui.components.loadPhotoCredits
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 22 설정 · 28 사진·글꼴 출처 (DESIGN_SPEC 6-22·6-28, D1 글꼴 출처) */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class CreditsUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int, vararg a: Any) = context.getString(id, *a)

    @Test
    fun everyBundledPhotoCreditIsShownWithLinks() {
        val credits = loadPhotoCredits(context)
        assertTrue("photo_credits.json을 읽지 못함", credits.isNotEmpty())
        val opened = mutableListOf<String>()
        rule.setContent { ReadyPortTheme { PhotoCreditsContent(credits, { opened += it }) } }
        val list = rule.onNode(hasScrollAction())
        credits.forEach { c ->
            list.performScrollToNode(hasText(c.title))
            rule.onAllNodesWithText(c.title).assertCountEquals(1)
            list.performScrollToNode(hasText(s(R.string.photo_credit_author, c.author)))
        }
        list.performScrollToNode(hasText(s(R.string.photo_credit_open)))
        rule.onAllNodesWithText(s(R.string.photo_credit_open))[0].performClick()
        assertTrue(opened.single() in credits.map { it.sourceUrl })
    }

    /**
     * 번들 사진(drawable photo_*)과 크레딧(photo_credits.json)이 1:1이다 — 쓰지 않게 된 호이안 사진(photo_market)은
     * drawable과 크레딧을 함께 지웠다(DESIGN_SPEC 3.7 ②, 2단계). 크레딧 id는 모두 사진으로 풀린다(Photos.byId).
     */
    @Test
    fun bundledPhotosAndCreditsMatchOneToOne() {
        val credits = loadPhotoCredits(context)
        val drawables = R.drawable::class.java.fields.map { it.name }.filter { it.startsWith("photo_") }.map { it.removePrefix("photo_") }.toSet()
        assertEquals(drawables, credits.map { it.id }.toSet())
        assertEquals(credits.size, credits.map { it.id }.distinct().size)
        credits.forEach { assertNotNull("사진으로 풀리지 않는 크레딧 id: ${it.id}", Photos.byId(it.id)) }
        assertFalse("market" in drawables)
    }

    @Test
    fun fontCreditShowsPretendardAndFullLicenseOffline() {
        rule.setContent { ReadyPortTheme { PhotoCreditsContent(emptyList(), {}) } }
        rule.onNodeWithText("Pretendard Std 1.3.9").assertExists()
        rule.onNodeWithText(s(R.string.credit_font_author, "Kil Hyung-jin")).assertExists()
        rule.onNodeWithText(s(R.string.photo_credit_license, "SIL OFL 1.1")).assertExists()
        // 전문은 접혀 있다가 누르면 앱에 든 파일(assets)에서 읽어 보인다 — 네트워크 없음
        rule.onAllNodesWithText("SIL OPEN FONT LICENSE", substring = true).assertCountEquals(0)
        val toggle = rule.onNode(hasText(s(R.string.credit_license_full)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        toggle.assertIsOff()
        toggle.performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("SIL OPEN FONT LICENSE", substring = true))
        rule.onAllNodesWithText("Reserved Font Name Pretendard Std", substring = true).assertCountEquals(1)
        rule.onAllNodesWithText(s(R.string.credit_license_missing)).assertCountEquals(0)
        rule.onNode(hasText(s(R.string.action_less)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertIsOn()
    }

    @Test
    fun licenseAssetIsBundledAndReflowKeepsEveryWord() {
        val raw = loadLicenseText(context, BundledFonts.single().licenseAsset)
        assertNotNull(raw)
        val flowed = reflowLicense(raw!!)
        fun words(t: String) = t.split(Regex("\\s+")).filter { it.isNotBlank() && !it.all { c -> c == '-' } }
        assertEquals(words(raw), words(flowed))
        // 제목 줄은 따로, 문단 안의 고정 줄바꿈은 없어진다
        assertTrue(flowed.contains("\n\nPREAMBLE\n\n"))
        assertTrue(flowed.contains("used, studied, modified and redistributed freely"))
        assertFalse(flowed.contains("-----"))
    }

    @Test
    fun settingsRowsOpenPhotosAndPrivacyAndToggleSwitches() {
        var photos = 0
        var privacy: String? = null
        var easy = false
        rule.setContent {
            ReadyPortTheme {
                SettingsScreen(
                    easyMode = easy,
                    onEasyModeChange = { easy = it },
                    onOpenPhotos = { photos++ },
                    onOpenPrivacy = { privacy = it },
                )
            }
        }
        val list = rule.onNode(hasScrollAction())
        // 쉬운 모드: 줄 전체가 하나의 스위치 (미리보기 '가'는 읽지 않는다)
        val easyRow = rule.onNode(hasText(s(R.string.settings_easy_mode)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        list.performScrollToNode(hasText(s(R.string.settings_easy_mode)))
        easyRow.assertIsOff()
        easyRow.performClick()
        assertTrue(easy)
        rule.onAllNodesWithText("가").assertCountEquals(0)
        list.performScrollToNode(hasText(s(R.string.settings_privacy_open)))
        rule.onNodeWithText(s(R.string.settings_privacy_open)).performClick()
        assertEquals(PRIVACY_URL, privacy)
        list.performScrollToNode(hasText(s(R.string.settings_credits)))
        rule.onNodeWithText(s(R.string.settings_credits)).performClick()
        assertEquals(1, photos)
    }

    /** 개인정보 안내 설명은 맨 위 약속과 같은 '휴대폰'으로 ('이 기기 안에서만' 대신 — 재검토 R18) */
    @Test
    fun privacyRowSaysPhoneLikeThePromise() {
        rule.setContent { ReadyPortTheme { SettingsScreen(easyMode = false, onEasyModeChange = {}) } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.settings_privacy_body_v2)))
        rule.onNodeWithText(s(R.string.settings_privacy_body_v2)).assertExists()
        rule.onAllNodesWithText("이 기기", substring = true).assertCountEquals(0)
    }
}
