package com.readyport.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
 * 화면을 아주 길게 잡고(h6000dp — 쉬운 모드 홈이 3600dp를 넘는다), 아래쪽 빈 배경은 잘라 낸다.
 * 화면이 캡처 높이보다 길어 맨 아래 줄까지 내용이 차 있으면(= 잘림) 다 찍은 뒤 실패한다.
 * 기본 모드(sdk 36)는 검토용 타일(build/gallery/tiles/, tiles.txt)도 매번 새로 만든다 — 지난 번호의 타일이 남지 않게.
 * 캡처는 robolectric.pixelCopyRenderMode=hardware(app/build.gradle.kts)로 그린다 — 그림자 확인은 shadowCheck.
 * 언어는 ko-rKR: 한국어 기기처럼 WordBreak.Phrase(어절 단위, API 33+)가 적용된다.
 */
abstract class GalleryCaptureBase {

    @get:Rule
    val rule = createComposeRule()

    /** build/gallery/ 아래 하위 폴더 (끝에 / 포함, 기본은 빈 문자열) */
    protected open val folder: String = ""

    /** 아래쪽 빈 배경을 잘라 낸다. 맨 아래 줄까지 내용이 있으면 null(= 캡처 높이보다 긴 화면) */
    private fun trimBottom(bmp: Bitmap, ground: Int): Bitmap? {
        var bottom = bmp.height - 1
        loop@ while (bottom > 0) {
            for (x in 0 until bmp.width step 4) if (bmp.getPixel(x, bottom) != ground) break@loop
            bottom--
        }
        if (bottom >= bmp.height - 1) return null
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, minOf(bmp.height, bottom + 24))
    }

    protected val groundArgb = android.graphics.Color.argb(
        255, (Tokens.Ground.red * 255).toInt(), (Tokens.Ground.green * 255).toInt(), (Tokens.Ground.blue * 255).toInt(),
    )

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
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                Box(Modifier.fillMaxSize().background(Tokens.Ground)) { list[current].second() }
            }
        }
        list.forEachIndexed { i, (name, _) ->
            rule.runOnIdle { current = i }
            rule.mainClock.advanceTimeBy(2_000)
            rule.waitForIdle()
            val full = rule.onRoot().captureToImage().asAndroidBitmap()
            val bmp = trimBottom(full, groundArgb) ?: full.also { cut += name }
            save(bmp, File(dir, "%02d_%s.png".format(i, name)))
            if (tiles) writeTiles(bmp, i, name, tileDir, tileList)
        }
        if (tiles) File("build/gallery/tiles.txt").writeText(tileList.joinToString("\n", postfix = "\n"))
        assertTrue(
            "캡처 높이보다 긴 화면(맨 아래까지 잘림): $cut — qualifiers 높이를 올리거나 화면을 나눌 것",
            cut.isEmpty(),
        )
    }

    private companion object {
        const val TILE_MAX_PX = 1400
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h6000dp-xhdpi")
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

/** S10(Android 12)과 같은 sdk 31·글자 200%: 선형 2배 확대와 음절 단위 줄바꿈이 캡처에 나타난다 (DESIGN_SPEC 8장 0단계) */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h12000dp-xhdpi", fontScale = 2.0f)
class GalleryCaptureSdk31Test : GalleryCaptureBase() {

    override val folder = "sdk31_font200/"

    @Test fun basic() = captureAll(easy = false)

    @Test fun easy() = captureAll(easy = true)
}
