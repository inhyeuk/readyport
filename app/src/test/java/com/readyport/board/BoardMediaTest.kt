package com.readyport.board

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.nio.file.Files

/**
 * 게시판 사진 준비 (사진·동영상 올리기는 기본 꺼짐 — 켰을 때의 길): 긴 변 1600px 이하로 줄이고,
 * **EXIF(찍은 곳 GPS·기기·시각)를 모두 지운다**. 시험 사진은 단색 그림에 가짜 GPS 태그를 붙여 만든다(실제 사진 아님).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class BoardMediaTest {

    /** 가로 [w]×세로 [h] JPEG + 가짜 GPS(서울 시청 근처 아님 — 바다 한가운데 0.5, 0.5)·기기·방향 태그 */
    private fun fixtureJpeg(w: Int, h: Int, orientation: Int = ExifInterface.ORIENTATION_NORMAL): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(40, 120, 200)) }
        val file = Files.createTempFile("fixture", ".jpg").toFile()
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "0/1,30/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "0/1,30/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            setAttribute(ExifInterface.TAG_MAKE, "FakeCam")
            setAttribute(ExifInterface.TAG_MODEL, "Fixture 1")
            setAttribute(ExifInterface.TAG_DATETIME, "2026:10:08 09:00:00")
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file.readBytes().also { file.delete() }
    }

    private fun exif(bytes: ByteArray) = ExifInterface(ByteArrayInputStream(bytes))

    @Suppress("DEPRECATION")
    private fun hasGps(e: ExifInterface): Boolean = e.getLatLong(FloatArray(2))

    private fun size(bytes: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return o.outWidth to o.outHeight
    }

    @Test
    fun fixtureReallyHasGps() {
        val src = fixtureJpeg(400, 300)
        assertTrue(hasGps(exif(src)))
        assertEquals("FakeCam", exif(src).getAttribute(ExifInterface.TAG_MAKE))
    }

    @Test
    fun bigPhotoIsShrunkTo1600AndAllExifIsGone() {
        val out = BoardMediaPrep.prepareImage(fixtureJpeg(4000, 3000))!!
        assertFalse(out.video)
        assertEquals("image/jpeg", out.contentType)
        val (w, h) = size(out.bytes)
        assertEquals(1600, maxOf(w, h))
        assertEquals(1200, minOf(w, h))
        val e = exif(out.bytes)
        assertFalse("GPS가 남으면 안 된다", hasGps(e))
        assertNull(e.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(e.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(e.getAttribute(ExifInterface.TAG_MODEL))
        assertNull(e.getAttribute(ExifInterface.TAG_DATETIME))
    }

    @Test
    fun rotatedPhotoIsTurnedUprightBeforeTheTagIsDropped() {
        val out = BoardMediaPrep.prepareImage(fixtureJpeg(3200, 2400, ExifInterface.ORIENTATION_ROTATE_90))!!
        assertEquals(1200 to 1600, size(out.bytes))
        // 방향 태그도 없다(이미 돌려 그렸으므로)
        val o = exif(out.bytes).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        assertTrue(o == ExifInterface.ORIENTATION_UNDEFINED || o == ExifInterface.ORIENTATION_NORMAL)
    }

    @Test
    fun smallPhotoKeepsItsSizeButStillLosesExif() {
        val out = BoardMediaPrep.prepareImage(fixtureJpeg(800, 600))!!
        assertEquals(800 to 600, size(out.bytes))
        assertFalse(hasGps(exif(out.bytes)))
    }

    @Test
    fun notAnImageIsRejected() {
        assertNull(BoardMediaPrep.prepareImage("not a jpeg".toByteArray()))
        assertNull(BoardMediaPrep.prepareImage(ByteArray(0)))
    }
}
