package com.readyport.ui.today

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.trip.StageInfo
import com.readyport.trip.Trip
import com.readyport.trip.TripStage
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * '내 여행' 상태를 4가지 조건으로 찍고(출국일 입국 카드 상태는 2단계에서 공유 Gallery에도 넣었다), A11yAudit과 같은 터치·이름 규칙을 확인한다 (C 묶음 검토용).
 * - today-departure-form: 출국일에 입국 카드 기간이 열린 경우(태국 TDAC는 도착일 포함 3일이라 보통 이 상태) —
 *   주 버튼(흰 채움)은 입국 카드 하나, `도착했어요`는 보조 버튼이어야 한다(원칙 7).
 * - today-preparing-passport: 여권 미등록 할 일.
 * 캡처: build/gallery/bundle_c/{basic|easy|sdk31_font200/basic|sdk31_font200/easy}/
 */
abstract class TodayStatesCaptureBase {

    @get:Rule
    val rule = createComposeRule()

    protected open val folder: String = ""

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val form = TestPacks.thailand.value.forms.first()
    private val trip = Trip("TH", "2026-11-03", "2026-11-07")

    private val states: List<Pair<String, @Composable () -> Unit>> = listOf(
        "today-departure-form" to {
            TodayContent(
                TodayUi(trip, StageInfo(TripStage.Departure, dayOfTrip = 1, formWindowOpen = true), "태국", form, true),
                TodayActions(), {}, {}, {}, {}, {},
            )
        },
        "today-preparing-passport" to {
            TodayContent(TodayUi(trip, StageInfo(TripStage.Preparing, daysLeft = 10), "태국", form, false), TodayActions(), {}, {}, {}, {}, {})
        },
    )

    private val groundArgb = argb(Tokens.Ground)

    private fun argb(c: androidx.compose.ui.graphics.Color) =
        android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

    private fun near(a: Int, b: Int) =
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) < 12 &&
            abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) < 12 &&
            abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) < 12

    private fun trimBottom(bmp: Bitmap): Bitmap {
        var bottom = bmp.height - 1
        loop@ while (bottom > 0) {
            for (x in 0 until bmp.width step 4) if (bmp.getPixel(x, bottom) != groundArgb) break@loop
            bottom--
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, minOf(bmp.height, bottom + 24))
    }

    protected fun run(easy: Boolean) {
        val dir = File("build/gallery/bundle_c/" + folder + if (easy) "easy" else "basic").apply { deleteRecursively(); mkdirs() }
        var current by mutableIntStateOf(0)
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                Box(Modifier.fillMaxSize().background(Tokens.Ground)) { states[current].second() }
            }
        }
        val problems = mutableListOf<String>()
        val minDp = if (easy) 56 else 48
        states.forEachIndexed { i, (name, _) ->
            rule.runOnIdle { current = i }
            rule.mainClock.advanceTimeBy(2_000)
            rule.waitForIdle()
            val d = rule.density.density
            rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { n ->
                val label = listOfNotNull(
                    n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
                    n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                ).joinToString(" ").takeIf { it.isNotBlank() }
                val b = n.touchBoundsInRoot
                if (b.width < minDp * d - 1 || b.height < minDp * d - 1) problems += "$name: 터치 영역 < ${minDp}dp ($label)"
                if (label == null) problems += "$name: 이름 없는 버튼"
            }
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "%02d_%s.png".format(i, name)).outputStream().use { trimBottom(bmp).compress(Bitmap.CompressFormat.PNG, 100, it) }

            // 입국 카드가 지금 할 일이면 '도착했어요'는 Accent 채움(주 버튼)이 아니다 — 글자를 원문으로 찾을 수 있는 API 33+에서만
            if (name == "today-departure-form" && Build.VERSION.SDK_INT >= 33) {
                val button = rule.onNodeWithText(context.getString(R.string.today_arrived_button)).captureToImage().asAndroidBitmap()
                val fill = button.getPixel((button.width * 0.04f).toInt(), button.height / 2)
                if (near(fill, argb(Tokens.Accent))) problems += "$name: 도착했어요가 주 버튼(Accent 채움) — 한 화면 주 버튼 하나(원칙 7) 위반"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h6000dp-xhdpi")
class TodayStatesCaptureTest : TodayStatesCaptureBase() {
    @Test fun basic() = run(easy = false)
    @Test fun easy() = run(easy = true)
}

/** S10(Android 12)과 같은 sdk 31·글자 200% — 공용 한국어 줄바꿈(keepWords)이 낱말을 지키는지 눈으로 본다 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h12000dp-xhdpi", fontScale = 2.0f)
class TodayStatesCaptureSdk31Test : TodayStatesCaptureBase() {
    override val folder = "sdk31_font200/"
    @Test fun basic() = run(easy = false)
    @Test fun easy() = run(easy = true)
}
