package com.readyport.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기기 내장 TextToSpeech로 음성 안내 (PRD 3.2). 네트워크를 쓰지 않는다.
 * 엔진이 없거나 언어를 지원하지 않으면 조용히 아무것도 하지 않는다(화면 표시만).
 */
@Singleton
class Speaker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Locale>? = null

    private val initialized = CompletableDeferred<Boolean>()

    private fun ensure() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            initialized.complete(ready)
            pending?.let { (text, locale) -> if (ready) speakNow(text, locale) }
            pending = null
        }
    }

    /**
     * 해당 언어 음성을 쓸 수 있는지 (ARCHITECTURE 9.8: isLanguageAvailable로 확인, 없으면 카드 표시만).
     * 엔진 준비를 최대 3초 기다린다.
     */
    suspend fun isAvailable(locale: Locale): Boolean {
        ensure()
        val ok = withTimeoutOrNull(3_000) { initialized.await() } ?: return false
        val engine = tts ?: return false
        return ok && engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
    }

    fun speak(text: String, locale: Locale = Locale.KOREAN) {
        ensure()
        if (ready) speakNow(text, locale) else pending = text to locale
    }

    fun stop() {
        tts?.stop()
    }

    private fun speakNow(text: String, locale: Locale) {
        val engine = tts ?: return
        if (engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) return
        engine.language = locale
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "readyport-guide")
    }
}
