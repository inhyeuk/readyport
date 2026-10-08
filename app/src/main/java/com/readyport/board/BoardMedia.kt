package com.readyport.board

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.graphics.scale
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 게시판 사진·동영상 올리기 준비 (꺼져 있는 기능 — config/board.mediaEnabled, docs/BOARD.md 5절).
 * 새 라이브러리 없이 안드로이드 기본 부품만 쓴다(BitmapFactory·ExifInterface·MediaExtractor·MediaMuxer).
 *
 * - 사진: 긴 변 [BoardLimits.IMAGE_MAX_EDGE]px 이하로 줄이고, 방향(EXIF orientation)대로 돌린 뒤 **JPEG로 다시 그린다** —
 *   다시 그린 JPEG에는 EXIF가 아예 없으므로 **촬영 위치(GPS)·기기·시각이 모두 빠진다**(BoardMediaTest가 가짜 GPS 사진으로 확인).
 * - 동영상: [BoardLimits.VIDEO_MAX_SECONDS]초·[BoardLimits.VIDEO_MAX_BYTES] 이하만. 트랙만 새 MP4로 옮겨 담아(MediaMuxer) 위치 정보(©xyz)를 뺀다.
 */
// android.media.ExifInterface(기본 부품): 방향만 읽는다. androidx.exifinterface는 새 의존성이라 넣지 않았다(minSdk 26이면 기본 판으로 충분)
@SuppressLint("ExifInterface")
object BoardMediaPrep {
    private const val JPEG_QUALITY = 82
    private const val VIDEO_BUFFER = 1 shl 20

    /** 사진 바이트 → 올릴 JPEG (못 읽으면 null) */
    fun prepareImage(bytes: ByteArray, maxEdge: Int = BoardLimits.IMAGE_MAX_EDGE): PreparedMedia? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // 2의 거듭제곱으로 먼저 크게 줄여 메모리를 아낀다(긴 변이 maxEdge의 2배 아래가 될 때까지)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val oriented = rotate(decoded, orientation(bytes))
        val scaled = scaleDown(oriented, maxEdge)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return PreparedMedia(out.toByteArray(), video = false)
    }

    private fun orientation(bytes: ByteArray): Int = runCatching {
        android.media.ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val edge = max(bitmap.width, bitmap.height)
        if (edge <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / edge
        return bitmap.scale((bitmap.width * ratio).roundToInt().coerceAtLeast(1), (bitmap.height * ratio).roundToInt().coerceAtLeast(1))
    }

    /** 동영상 검사 결과 */
    sealed interface VideoCheck {
        data class Ok(val media: PreparedMedia) : VideoCheck
        data object TooLong : VideoCheck
        data object TooBig : VideoCheck
        data object Unreadable : VideoCheck
    }

    /** 고른 동영상(콘텐츠 주소) → 길이·크기 검사 → 위치 정보를 뺀 MP4 */
    fun prepareVideo(context: Context, uri: Uri): VideoCheck {
        val seconds = runCatching {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(context, uri)
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000)
                } finally {
                    release()
                }
            }
        }.getOrNull() ?: return VideoCheck.Unreadable
        if (seconds > BoardLimits.VIDEO_MAX_SECONDS) return VideoCheck.TooLong
        val out = File.createTempFile("board", ".mp4", context.cacheDir)
        return try {
            if (!remux(context, uri, out)) return VideoCheck.Unreadable
            if (out.length() > BoardLimits.VIDEO_MAX_BYTES) return VideoCheck.TooBig
            VideoCheck.Ok(PreparedMedia(out.readBytes(), video = true))
        } finally {
            out.delete()
        }
    }

    /** 영상·소리 트랙만 새 MP4로 옮긴다 — 위치(setLocation)·그 밖의 메타데이터를 넣지 않는다 */
    private fun remux(context: Context, uri: Uri, out: File): Boolean = runCatching {
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(context, uri, null)
            val map = HashMap<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    map[i] = muxer.addTrack(format)
                }
            }
            if (map.isEmpty()) return@runCatching false
            rotationOf(context, uri)?.let(muxer::setOrientationHint)
            muxer.start()
            val buffer = ByteBuffer.allocate(VIDEO_BUFFER)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.offset = 0
                info.presentationTimeUs = extractor.sampleTime
                // 표본 표시 → 코덱 버퍼 표시(키 프레임만 옮긴다)
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                map[track]?.let { muxer.writeSampleData(it, buffer, info) }
                extractor.advance()
            }
            muxer.stop()
            true
        } finally {
            extractor.release()
            runCatching { muxer.release() }
        }
    }.getOrDefault(false)

    private fun rotationOf(context: Context, uri: Uri): Int? = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(context, uri)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
            } finally {
                release()
            }
        }
    }.getOrNull()
}
