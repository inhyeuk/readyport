package com.readyport.ui.home

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.nav.BottomTabs
import com.readyport.ui.nav.Tab
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * 묶음 A 검토용 캡처 (하단 탭 변형·320dp 폭처럼 공유 Gallery에 없는 상태): build/gallery/bundle_a/<변형>/
 * - tabs.png: 하단 탭 — 선택 탭 4가지 + 자녀 폰 탭 2가지(도움 탭 선택 포함). 쉬운 모드 2줄 라벨·200%·320dp 폭을 변형으로 본다.
 * - home-essentials.png: 홈의 '꼭 챙길 물건' 진행 줄(n / 5)과 아래 카드들 — 갤러리 픽스처에는 진행 데이터가 없다.
 */
abstract class BundleACaptureBase {

    @get:Rule
    val rule = createComposeRule()

    protected abstract val folder: String

    private val groundArgb = android.graphics.Color.argb(
        255, (Tokens.Ground.red * 255).toInt(), (Tokens.Ground.green * 255).toInt(), (Tokens.Ground.blue * 255).toInt(),
    )

    /** 아래쪽 빈 배경을 잘라 낸다 */
    private fun trimBottom(bmp: Bitmap): Bitmap {
        var bottom = bmp.height - 1
        loop@ while (bottom > 0) {
            for (x in 0 until bmp.width step 4) if (bmp.getPixel(x, bottom) != groundArgb) break@loop
            bottom--
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, minOf(bmp.height, bottom + 24))
    }

    protected fun captureAll(easy: Boolean) {
        val dir = File("build/gallery/bundle_a/$folder/" + if (easy) "easy" else "basic").apply { deleteRecursively(); mkdirs() }
        var page by mutableIntStateOf(0)
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                Box(Modifier.fillMaxSize().background(Tokens.Ground)) {
                    when (page) {
                        0 -> Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Tab.Main.forEach { tab -> BottomTabs(selected = tab, onSelect = {}) }
                            BottomTabs(selected = Tab.Present, onSelect = {}, tabs = Tab.Child)
                            BottomTabs(selected = Tab.Help, onSelect = {}, tabs = Tab.Child)
                        }
                        else -> HomeContent(TestPacks.homeUi(), HomeActions(), today = LocalDate.of(2026, 9, 28))
                    }
                }
            }
        }
        listOf("tabs", "explore").forEachIndexed { i, name ->
            rule.runOnIdle { page = i }
            rule.mainClock.advanceTimeBy(2_000)
            rule.waitForIdle()
            if (name == "home-essentials" && easy) {
                // 쉬운 모드는 여행 준비 카드를 한 줄씩 접어 둔다(재검토 R13) — 꼭 챙길 물건 줄을 눌러 펼친 뒤 찍는다
                val context = ApplicationProvider.getApplicationContext<android.content.Context>()
                rule.onNode(
                    hasText(context.getString(R.string.prepare_items_title)) and
                        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.state_collapsed)),
                ).performClick()
                rule.mainClock.advanceTimeBy(2_000)
                rule.waitForIdle()
            }
            val bmp = trimBottom(rule.onRoot().captureToImage().asAndroidBitmap())
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (name == "home-essentials") {
                val stat = ApplicationProvider.getApplicationContext<android.content.Context>()
                    .getString(R.string.essentials_progress_stat, 2, 5)
                assertTrue("진행 줄(n / 5)이 그려지지 않음", rule.onAllNodesWithText(stat, substring = true).fetchSemanticsNodes().isNotEmpty())
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h4000dp-xhdpi")
class HomeBundleCaptureTest : BundleACaptureBase() {
    override val folder = "w393"

    @Test fun basic() = captureAll(easy = false)

    @Test fun easy() = captureAll(easy = true)
}

/** S10(Android 12)과 같은 sdk 31·글자 200% */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h8000dp-xhdpi", fontScale = 2.0f)
class HomeBundleCaptureSdk31Test : BundleACaptureBase() {
    override val folder = "sdk31_font200"

    @Test fun basic() = captureAll(easy = false)

    @Test fun easy() = captureAll(easy = true)
}

/** 좁은 폭(320dp) — 탭 라벨 autoSize·2줄 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w320dp-h4000dp-xhdpi")
class HomeBundleCaptureNarrowTest : BundleACaptureBase() {
    override val folder = "w320"

    @Test fun basic() = captureAll(easy = false)

    @Test fun easy() = captureAll(easy = true)
}
