package com.readyport.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.data.settings.AppSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 디자인 확인용 화면 캡처. 검증(assert)은 하지 않고 build/screenshots/에 PNG를 남긴다.
 * 기기 없이 쉬운 모드·글자 확대 모습을 눈으로 확인하는 용도.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
class ScreenCaptureTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun capture(name: String) {
        // 누름 효과(ripple)가 끝난 뒤 찍는다
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun openTab(label: Int) {
        rule.onNode(
            hasText(context.getString(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).performClick()
    }

    private fun captureAll(prefix: String, settings: AppSettings) {
        rule.setContent { ReadyPortRoot(settings = settings, onSetEasyMode = {}, onSpeak = {}) }
        capture("${prefix}_1_today")
        openTab(R.string.tab_prepare); capture("${prefix}_2_prepare")
        openTab(R.string.tab_explore); capture("${prefix}_3_explore")
        openTab(R.string.tab_wallet); capture("${prefix}_4_wallet")
        openTab(R.string.tab_help); capture("${prefix}_5_help")
    }

    @Test fun basicMode() = captureAll("basic", AppSettings(easyMode = false))

    @Test fun easyMode() = captureAll("easy", AppSettings(easyMode = true))

    @Test fun easyModeDoubleFont() {
        RuntimeEnvironment.setFontScale(2.0f)
        captureAll("easy_font200", AppSettings(easyMode = true))
    }

    @Test fun firstRun() {
        rule.setContent { ReadyPortRoot(settings = AppSettings(easyMode = null), onSetEasyMode = {}, onSpeak = {}) }
        capture("first_run")
    }
}
