package com.readyport.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
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

    private fun ensure() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            pending?.let { (text, locale) -> if (ready) speakNow(text, locale) }
            pending = null
        }
    }

    /** 해당 언어 음성을 쓸 수 있는지. 현지어 카드(M7)에서 '소리로 들려주기' 노출 여부에 쓴다. */
    fun isAvailable(locale: Locale): Boolean {
        val engine = tts ?: return false
        return ready && engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
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
