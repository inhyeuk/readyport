package com.readyport.doc.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.core.graphics.createBitmap

/**
 * 기기 안 글자 인식 (ML Kit Text Recognition v2, PRD 7.2). 모델은 앱에 싣지 않고 Play 서비스가 받아 둔다
 * (매니페스트 DEPENDENCIES로 설치 때, 그리고 [prefetch]로 앱 시작 때 한 번 더 요청). 인식은 기기 안에서만 한다.
 * 이미지는 메모리에서만 다루고 파일로 저장하지 않는다. 다 쓴 비트맵은 바로 recycle 한다.
 */
@Singleton
class OcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 여권 MRZ(영문·숫자)용 */
    val latin: TextRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** 예약 서류(한글+영문)용. 한국어 인식기는 라틴 문자도 읽는다 */
    private val korean: TextRecognizer by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }

    /**
     * 글자 인식 모델을 미리 받아 둔다 (이미 있으면 바로 끝남). 인터넷이 될 때 앱 시작에서 부른다 —
     * 여행지에서 인터넷 없이 여권을 찍어도 되도록. 실패해도 조용히 넘어간다(다음 실행 때 다시 시도).
     */
    fun prefetch() {
        runCatching {
            val request = ModuleInstallRequest.newBuilder().addApi(latin).addApi(korean).build()
            ModuleInstall.getClient(context).installModules(request)
        }
    }

    suspend fun recognize(bitmap: Bitmap, koreanText: Boolean): String {
        val recognizer = if (koreanText) korean else latin
        return recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
    }

    /** 사진 한 장을 읽는다. 원본 파일은 건드리지 않고 복사도 하지 않는다 */
    suspend fun recognizeImage(uri: Uri, koreanText: Boolean): String {
        val bitmap = withContext(Dispatchers.IO) { decodeScaled(uri) }
        return try {
            recognize(bitmap, koreanText)
        } finally {
            bitmap.recycle()
        }
    }

    /** PDF 앞 몇 쪽을 그림으로 그려서 읽는다 (기기에 PDF 글자 추출 기능이 없는 경우가 많아서) */
    suspend fun recognizePdf(uri: Uri, maxPages: Int = 3): String = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext ""
        pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                buildString {
                    for (i in 0 until minOf(renderer.pageCount, maxPages)) {
                        val page = renderer.openPage(i)
                        val scale = 2
                        val bitmap = createBitmap(page.width * scale, page.height * scale)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        try {
                            appendLine(recognize(bitmap, koreanText = true))
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }

    private fun decodeScaled(uri: Uri): Bitmap {
        val maxSide = 2400
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val ratio = maxSide.toFloat() / longest
                    decoder.setTargetSize((info.size.width * ratio).toInt(), (info.size.height * ratio).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
