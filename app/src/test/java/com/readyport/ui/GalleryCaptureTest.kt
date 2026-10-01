package com.readyport.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 디자인 검토용: 모든 화면을 전체 길이로 찍어 build/gallery/{basic|easy}/이름.png 로 남긴다 (검증 없음).
 * 화면을 아주 길게 잡고, 아래쪽 빈 배경은 잘라 낸다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h3600dp-xhdpi")
class GalleryCaptureTest {

    @get:Rule
    val rule = createComposeRule()

    private fun trimBottom(bmp: Bitmap, ground: Int): Bitmap {
        var bottom = bmp.height - 1
        loop@ while (bottom > 0) {
            for (x in 0 until bmp.width step 4) if (bmp.getPixel(x, bottom) != ground) break@loop
            bottom--
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, minOf(bmp.height, bottom + 24))
    }

    private fun captureAll(easy: Boolean) {
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val thumb = BitmapFactory.decodeResource(res, R.drawable.photo_th).asImageBitmap()
        val list = Gallery.screens(thumb)
        val dir = File("build/gallery/" + if (easy) "easy" else "basic").apply { mkdirs() }
        var current by mutableIntStateOf(0)
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                Box(Modifier.fillMaxSize().background(Tokens.Ground)) { list[current].second() }
            }
        }
        val ground = android.graphics.Color.argb(255, (Tokens.Ground.red * 255).toInt(), (Tokens.Ground.green * 255).toInt(), (Tokens.Ground.blue * 255).toInt())
        list.forEachIndexed { i, (name, _) ->
            rule.runOnIdle { current = i }
            rule.mainClock.advanceTimeBy(2_000)
            rule.waitForIdle()
            val bmp = trimBottom(rule.onRoot().captureToImage().asAndroidBitmap(), ground)
            File(dir, "%02d_%s.png".format(i, name)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun basic() = captureAll(easy = false)

    @Test fun easy() = captureAll(easy = true)
}
