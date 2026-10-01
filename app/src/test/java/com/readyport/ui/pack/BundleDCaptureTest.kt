package com.readyport.ui.pack

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.prep.CartKey
import com.readyport.transport.Place
import com.readyport.ui.TestPacks
import com.readyport.ui.components.PHONE_GROUP_TAG
import com.readyport.ui.components.phoneGroups
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import com.readyport.ui.transport.DriverFullScreenBody
import com.readyport.ui.transport.RideAppRow
import com.readyport.ui.transport.TransportContent
import com.readyport.ui.transport.TransportUi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * D 묶음(18 쇼핑·19 이동·20 도움) 보강 캡처와 검사 — 공용 갤러리(Gallery.kt)에 없는 상태를 여기서 찍는다 (DESIGN_SPEC 3.2·8장).
 * - 전체 화면(직원에게 보여주기·기사님께 보여주기·문장 크게): 여러 줄 태국어 localLarge(200%면 약 112sp)가 잘리지 않고 닫기가 보이는지
 * - 담은 품목, 설치된 앱 행(목적지 넣어 열기 / 열고 주소 복사), 여러 장소 선택, 가는 곳 입력 카드, 차 부르기 결과 안내
 * - 200%에서 긴급 전화번호가 한 줄이거나 '-' 뒤에서만 줄을 바꾸는지(숫자 사이에서 끊기지 않는지)
 * 캡처는 build/gallery/bundle_d/{basic|easy|sdk31_basic|sdk31_easy}/ 에 남는다.
 */
abstract class BundleDCaptureBase {

    @get:Rule
    val rule = createComposeRule()

    protected abstract val folder: String

    private val ground = android.graphics.Color.argb(
        255, (Tokens.Ground.red * 255).toInt(), (Tokens.Ground.green * 255).toInt(), (Tokens.Ground.blue * 255).toInt(),
    )

    private fun trimBottom(bmp: Bitmap): Bitmap {
        var bottom = bmp.height - 1
        loop@ while (bottom > 0) {
            for (x in 0 until bmp.width step 4) if (bmp.getPixel(x, bottom) != ground) break@loop
            bottom--
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, minOf(bmp.height, bottom + 24))
    }

    private val th get() = TestPacks.thailand.value
    private val index get() = TestPacks.index.value

    /** 기사님께 보여 줄 긴 태국어 주소 (여러 줄 + 위아래 부호) */
    private val longAddress = "123/45 ซอยสุขุมวิท 11 แขวงคลองเตยเหนือ เขตวัฒนา กรุงเทพมหานคร 10110"

    private fun place(id: String, name: String, address: String = longAddress) = Place(id, name, address)

    /** 화면 높이 그대로 찍는 전체 화면들 */
    protected fun fullScreens(): List<Pair<String, @Composable () -> Unit>> = listOf(
        "staff-full" to { ShowStaffBody(th.shopping.first { it.id == "niello_silver" }, onClose = {}) },
        "phrase-full" to { PhraseFullScreenBody(th.phrases.first { it.id == "address" }, onClose = {}) },
        "driver-full" to { DriverFullScreenBody("กรุณาพาไปที่อยู่นี้", longAddress, onClose = {}) },
    )

    /** 아래 빈 배경을 잘라 찍는 긴 상태들 */
    protected fun states(): List<Pair<String, @Composable () -> Unit>> = listOf(
        "shopping-in-cart" to {
            ShoppingContent(
                ShoppingUi(
                    "TH", "태국", th.shopping,
                    cart = th.shopping.take(2).map { CartKey.of("TH", it.id) }.toSet(),
                    sourceNames = th.sources.associate { it.id to it.name },
                    returnLinks = index.returnLinks, returnFacts = index.returnFacts,
                    indexSources = index.sources.associate { it.id to it.name },
                ),
                { _, _ -> }, {},
            )
        },
        "transport-installed-places" to {
            val places = listOf(place("p1", "방콕 숙소"), place("p2", "왓아룬"), place("p3", "수완나품 공항"))
            TransportContent(
                TransportUi(
                    places, places[1],
                    th.transportApps.map { RideAppRow(it, installed = true) }, mapsInstalled = true, driverPhrase = "กรุณาพาไปที่นี่",
                ),
                R.string.move_ride_copied, { _, _ -> }, {}, {}, {},
            )
        },
        "transport-failed" to {
            val p = place("p1", "방콕 숙소")
            TransportContent(
                TransportUi(listOf(p), p, th.transportApps.mapIndexed { i, a -> RideAppRow(a, installed = i == 0) }, false, "กรุณาพาไปที่นี่"),
                R.string.move_ride_failed, { _, _ -> }, {}, {}, {},
            )
        },
        "transport-editing" to {
            TransportContent(TransportUi(apps = th.transportApps.map { RideAppRow(it, installed = false) }), null, { _, _ -> }, {}, {}, {})
        },
    )

    private fun capture(list: List<Pair<String, @Composable () -> Unit>>, easy: Boolean, trim: Boolean, check: (String) -> Unit = {}) {
        val dir = File("build/gallery/bundle_d/$folder${if (easy) "easy" else "basic"}").apply { mkdirs() }
        var current by mutableIntStateOf(0)
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                Box(Modifier.fillMaxSize().background(Tokens.Ground)) { list[current].second() }
            }
        }
        list.forEachIndexed { i, (name, _) ->
            rule.runOnIdle { current = i }
            rule.mainClock.advanceTimeBy(2_000)
            rule.waitForIdle()
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "$name.png").outputStream().use { (if (trim) trimBottom(bmp) else bmp).compress(Bitmap.CompressFormat.PNG, 100, it) }
            check(name)
        }
    }

    protected fun captureFull(easy: Boolean) {
        val close = ApplicationProvider.getApplicationContext<Application>().getString(R.string.help_close)
        // 닫기는 아래에 고정돼 화면 안에 보여야 한다 (긴 태국어가 화면을 넘겨도)
        capture(fullScreens(), easy, trim = false) { rule.onNodeWithText(close).assertIsDisplayed() }
    }

    protected fun captureStates(easy: Boolean) = capture(states(), easy, trim = true)

    // ---------------- 전화번호 줄바꿈 ----------------

    private fun lineCount(n: SemanticsNode): Int? {
        val results = mutableListOf<TextLayoutResult>()
        n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.firstOrNull()?.lineCount
    }

    /**
     * 도움 화면의 모든 전화번호: 한 Text로 한 줄이거나, '-' 뒤에서 나눈 묶음(PHONE_GROUP_TAG)들이 모두 한 줄 높이.
     * 묶음을 이어 붙이면 번호 그대로이고, 의미 글자는 번호 전체 한 노드다.
     */
    protected fun phoneNumbersNeverBreakInsideDigits(easy: Boolean) {
        val ui = TestPacks.helpUi()
        rule.setContent { ReadyPortTheme(easyMode = easy) { HelpContent(ui, {}, {}, {}) } }
        rule.waitForIdle()
        val pack = th
        val numbers = (pack.emergency.map { it.number } + listOfNotNull(pack.embassy?.phone, pack.embassy?.emergencyPhone) + ui.common.map { it.number }).distinct()
        var grouped = 0
        numbers.forEach { number ->
            val nodes = rule.onAllNodes(hasText(number), useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("번호가 의미 글자로 없음: $number", nodes.isNotEmpty())
            nodes.forEach { n ->
                val lines = lineCount(n)
                if (lines != null) {
                    assertTrue("$number 가 ${lines}줄로 꺾임(숫자 사이에서 끊김)", lines == 1)
                } else {
                    val groups = n.children.filter { SemanticsMatcher.expectValue(SemanticsProperties.TestTag, PHONE_GROUP_TAG).matches(it) }
                    assertTrue("$number: 한 줄도 묶음도 아님", groups.size >= 2)
                    assertTrue("$number: 묶음을 이으면 번호가 아님", phoneGroups(number).size == groups.size)
                    val heights = groups.map { it.boundsInRoot.height }
                    assertTrue("$number: 묶음 안에서 줄이 꺾임 $heights", heights.max() <= heights.min() * 1.2f)
                    grouped++
                }
            }
        }
        println("PHONE easy=$easy numbers=${numbers.size} grouped=$grouped")
        // 묶음 노드는 번호 노드 밖에서 보이지 않는다
        val strayGroups = rule.onAllNodes(hasTestTag(PHONE_GROUP_TAG), useUnmergedTree = true).fetchSemanticsNodes()
            .count { it.parent?.config?.getOrNull(SemanticsProperties.Text) == null }
        assertTrue("번호 노드 밖의 묶음: $strayGroups", strayGroups == 0)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h851dp-xhdpi")
class BundleDFullScreenCaptureTest : BundleDCaptureBase() {
    override val folder = ""

    @Test fun basic() = captureFull(easy = false)

    @Test fun easy() = captureFull(easy = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h851dp-xhdpi", fontScale = 2.0f)
class BundleDFullScreenCaptureSdk31Test : BundleDCaptureBase() {
    override val folder = "sdk31_"

    @Test fun basic() = captureFull(easy = false)

    @Test fun easy() = captureFull(easy = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h6000dp-xhdpi")
class BundleDStateCaptureTest : BundleDCaptureBase() {
    override val folder = ""

    @Test fun basic() = captureStates(easy = false)

    @Test fun easy() = captureStates(easy = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h12000dp-xhdpi", fontScale = 2.0f)
class BundleDStateCaptureSdk31Test : BundleDCaptureBase() {
    override val folder = "sdk31_"

    @Test fun basic() = captureStates(easy = false)

    @Test fun easy() = captureStates(easy = true)

    @Test fun phoneNumbersBasic() = phoneNumbersNeverBreakInsideDigits(easy = false)

    @Test fun phoneNumbersEasy() = phoneNumbersNeverBreakInsideDigits(easy = true)
}
