package com.readyport.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.components.cardShadow
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.ceil

/**
 * 디자인 검토용: 모든 화면을 전체 길이로 찍어 build/gallery/{basic|easy}/이름.png 로 남긴다.
 * **쪽 단위로 그려 이어 붙인다**(2단계): 화면 내용은 아주 긴 칸([contentHeightDp] — 기본 8000dp, 글자 200%는 12000dp)에
 * 한 번 배치해 목록 항목이 모두 그려지게 하고, 기기 높이 창(h700dp)에 한 쪽씩 끌어올려(graphicsLayer) 찍은 뒤 이어 붙인다.
 * - 긴 창 한 장으로 찍으면 그림자 광원이 창 맨 위라 아래쪽 카드일수록 그림자가 아래로 밀려 겹친 카드처럼 보였다
 *   (BUNDLE_A_NOTES 8 — 캡처 인공물). 쪽마다 실기기 화면 높이 안에서 그리므로 그림자가 실기기와 같다.
 * - 렌더 한계(16,384px)는 창 한 장에만 걸리므로, 글자 200% 쉬운 모드 입국 카드 확인(약 10,000dp)도 끝까지 찍힌다(E 묶음 지적).
 * 이어 붙인 뒤 아래쪽 빈 배경은 잘라 낸다. 내용이 [contentHeightDp] 끝까지 차 있으면(= 잘림) 다 찍은 뒤 실패한다.
 * 기본 모드(sdk 36)는 검토용 타일(build/gallery/tiles/, tiles.txt)도 매번 새로 만든다 — 지난 번호의 타일이 남지 않게.
 * 캡처는 robolectric.pixelCopyRenderMode=hardware(app/build.gradle.kts)로 그린다 — 그림자 확인은 shadowCheck.
 * 언어는 ko-rKR: 한국어 기기처럼 WordBreak.Phrase(어절 단위, API 33+)가 적용된다.
 */
abstract class GalleryCaptureBase {

    @get:Rule
    val rule = createComposeRule()

    /** build/gallery/ 아래 하위 폴더 (끝에 / 포함, 기본은 빈 문자열) */
    protected open val folder: String = ""

    /** 화면 내용을 배치할 칸 높이 — 이보다 긴 화면은 잘림으로 실패 */
    protected open val contentHeightDp: Int = 8000

    protected val groundArgb = android.graphics.Color.argb(
        255, (Tokens.Ground.red * 255).toInt(), (Tokens.Ground.green * 255).toInt(), (Tokens.Ground.blue * 255).toInt(),
    )

    /** [bmp]의 [row] 줄이 모두 바탕색인지 (4px 간격으로 본다) */
    private fun blankRow(bmp: Bitmap, row: Int): Boolean {
        for (x in 0 until bmp.width step 4) if (bmp.getPixel(x, row) != groundArgb) return false
        return true
    }

    private fun save(bmp: Bitmap, file: File) {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 긴 캡처를 검토용 타일(한 장 최대 1400px = 700dp)로 같은 높이씩 나눈다 */
    private fun writeTiles(bmp: Bitmap, index: Int, name: String, dir: File, list: MutableList<String>) {
        val n = ceil(bmp.height / TILE_MAX_PX.toDouble()).toInt().coerceAtLeast(1)
        val h = ceil(bmp.height / n.toDouble()).toInt()
        for (k in 0 until n) {
            val top = k * h
            val tile = Bitmap.createBitmap(bmp, 0, top, bmp.width, minOf(h, bmp.height - top))
            val f = File(dir, "%02d_%s_%dof%d.png".format(index, name, k + 1, n))
            save(tile, f)
            list += f.absolutePath.replace('\\', '/')
        }
    }

    protected fun captureAll(easy: Boolean, tiles: Boolean = false) {
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val thumb = BitmapFactory.decodeResource(res, R.drawable.photo_th).asImageBitmap()
        val list = Gallery.screens(thumb)
        // 화면 목록이 바뀌면 번호가 밀리므로 지난 캡처를 지우고 새로 찍는다
        val dir = File("build/gallery/" + folder + if (easy) "easy" else "basic").apply { deleteRecursively(); mkdirs() }
        val tileDir = File("build/gallery/tiles")
        val tileList = mutableListOf<String>()
        if (tiles) tileDir.apply { deleteRecursively(); mkdirs() }
        val cut = mutableListOf<String>()
        var current by mutableIntStateOf(0)
        var offsetPx by mutableIntStateOf(0)
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                Box(Modifier.fillMaxSize().background(Tokens.Ground)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(Alignment.Top, unbounded = true)
                            .requiredHeight(contentHeightDp.dp)
                            .graphicsLayer { translationY = -offsetPx.toFloat() }
                            .background(Tokens.Ground),
                    ) { list[current].second() }
                }
            }
        }
        val contentPx = (contentHeightDp * rule.density.density).toInt()
        list.forEachIndexed { i, (name, _) ->
            rule.runOnIdle {
                current = i
                offsetPx = 0
            }
            rule.mainClock.advanceTimeBy(2_000)
            rule.waitForIdle()
            val pages = mutableListOf<Bitmap>()
            var pageH = 0
            var bottom = -1 // 이어 붙인 그림에서 마지막으로 내용이 있는 줄
            while (true) {
                val page = rule.onRoot().captureToImage().asAndroidBitmap()
                pageH = page.height
                val top = pages.size * pageH
                val visible = minOf(pageH, contentPx - top)
                var last = -1
                for (y in visible - 1 downTo 0) if (!blankRow(page, y)) { last = y; break }
                if (last >= 0) bottom = top + last
                pages += page
                // 한 쪽(700dp)이 통째로 바탕뿐이면 내용이 끝난 것(카드 사이 틈은 한 쪽보다 훨씬 좁다). 칸 끝에 닿아도 그만
                if (last < 0 || top + pageH >= contentPx) break
                rule.runOnIdle { offsetPx = pages.size * pageH }
                rule.waitForIdle()
            }
            if (bottom >= contentPx - 1) cut += name
            val height = minOf(contentPx, bottom + 24).coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(pages.first().width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            pages.forEachIndexed { k, page -> canvas.drawBitmap(page, 0f, (k * pageH).toFloat(), null) }
            save(bmp, File(dir, "%02d_%s.png".format(i, name)))
            if (tiles) writeTiles(bmp, i, name, tileDir, tileList)
        }
        if (tiles) File("build/gallery/tiles.txt").writeText(tileList.joinToString("\n", postfix = "\n"))
        assertTrue(
            "캡처 칸($contentHeightDp dp)보다 긴 화면(맨 아래까지 잘림): $cut — contentHeightDp를 올리거나 화면을 나눌 것",
            cut.isEmpty(),
        )
    }

    private companion object {
        const val TILE_MAX_PX = 1400
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h700dp-xhdpi")
class GalleryCaptureTest : GalleryCaptureBase() {

    @Test fun basic() = captureAll(easy = false, tiles = true)

    @Test fun easy() = captureAll(easy = true)

    /**
     * 흰 카드의 부드러운 그림자(2dp, DESIGN_SPEC D2·3.5)가 캡처에 그려지는지 확인한다.
     * 소프트웨어 렌더링이면 Modifier.shadow가 안 그려져 테두리 없는 흰 카드가 Ground와 1.1:1로 경계 없이 보인다 —
     * 그때는 3단계 컨펌 자료를 에뮬레이터·실기기 캡처로 만든다. 결과는 build/gallery/shadow_check.txt.
     * 렌더러가 그림자를 그리는데(대조군이 보임) 스펙 그림자가 안 보이면 실패한다 — 플랫폼 그림자 알파 보정(Tokens.ShadowSpot) 회귀 방지.
     */
    @Test fun shadowCheck() {
        rule.setContent {
            ReadyPortTheme {
                Box(Modifier.fillMaxSize().background(Tokens.Ground)) {
                    val shape = MaterialTheme.shapes.large
                    // 왼쪽: 스펙 그림자(화면상 Ink 8%/12%) / 오른쪽: 대조군(검정 기본 그림자, 같은 2dp)
                    Box(
                        Modifier
                            .padding(start = 40.dp, top = 40.dp)
                            .size(150.dp, 100.dp)
                            .cardShadow(shape)
                            .background(Tokens.Surface, shape),
                    )
                    Box(
                        Modifier
                            .padding(start = 220.dp, top = 40.dp)
                            .size(150.dp, 100.dp)
                            .shadow(2.dp, shape)
                            .background(Tokens.Surface, shape),
                    )
                }
            }
        }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val d = rule.density.density
        val bottom = ((40 + 100) * d).toInt()
        val groundR = android.graphics.Color.red(groundArgb)
        /** 카드 아래 8dp 안에서 Ground보다 가장 많이 어두워진 정도 (0이면 그림자 없음) */
        fun delta(centerXdp: Int): Int {
            val x = (centerXdp * d).toInt()
            val darkest = (1..(8 * d).toInt()).minOf { android.graphics.Color.red(bmp.getPixel(x, bottom + it)) }
            return groundR - darkest
        }
        val spec = delta(40 + 75)
        val control = delta(220 + 75)
        val rendererDraws = control >= 3
        val report = "spec(화면상 Ink 8%/12%, 알파 보정 ambient=${Tokens.ShadowAmbient.alpha}, spot=${"%.2f".format(Tokens.ShadowSpot.alpha)}) " +
            "delta=$spec, control(검정 기본) delta=$control, Ground R=$groundR → " +
            (if (rendererDraws) "렌더러는 그림자를 그린다" else "렌더러가 그림자를 그리지 않는다(실기기·에뮬레이터 캡처 필요)") + ", " +
            (if (spec >= 3) "스펙 그림자 보임" else "스펙 그림자가 Ground 위에서 거의 보이지 않는다") +
            " (pixelCopyRenderMode=${System.getProperty("robolectric.pixelCopyRenderMode")})"
        println("SHADOW $report")
        File("build/gallery").mkdirs()
        File("build/gallery/shadow_check.txt").writeText(report + "\n")
        if (rendererDraws) {
            assertTrue("흰 카드 그림자가 보이지 않음: $report", spec >= 3)
            assertTrue("흰 카드 그림자가 기본 그림자보다 진함: $report", spec <= control)
        }
    }
}

/**
 * S10(Android 12)과 같은 sdk 31·글자 200%: 선형 2배 확대와 음절 단위 줄바꿈이 캡처에 나타난다 (DESIGN_SPEC 8장 0단계).
 * 쪽 단위 캡처라 렌더 한계(16,384px)에 걸리지 않는다 — 예전 한 장 캡처에서는 쉬운 모드 입국 카드 확인의 아래쪽이 비어 찍혔다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h700dp-xhdpi", fontScale = 2.0f)
class GalleryCaptureSdk31Test : GalleryCaptureBase() {

    override val folder = "sdk31_font200/"

    // 공항 묶음(2026-10-03)까지 들어간 나라 입국 화면은 쉬운 모드 200%에서 한 화면이 1만 5천 dp를 넘는다
    override val contentHeightDp = 18000

    @Test fun basic() = captureAll(easy = false)

    @Test fun easy() = captureAll(easy = true)
}
