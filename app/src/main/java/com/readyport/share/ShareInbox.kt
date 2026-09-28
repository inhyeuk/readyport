package com.readyport.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 다른 앱의 '공유하기'로 받은 예약 서류. 메모리에만 두고 처리 뒤 바로 비운다 (PRD 7.2) */
data class SharedPayload(val text: String?, val uri: Uri?, val mimeType: String?) {
    override fun toString() = "SharedPayload(mime=$mimeType, hasText=${text != null}, hasUri=${uri != null})"
}

@Singleton
class ShareInbox @Inject constructor() {
    private val _pending = MutableStateFlow<SharedPayload?>(null)
    val pending: StateFlow<SharedPayload?> = _pending.asStateFlow()

    fun offer(intent: Intent?): Boolean {
        val payload = intent?.toPayload() ?: return false
        _pending.value = payload
        return true
    }

    fun take(): SharedPayload? = _pending.value.also { _pending.value = null }

    private fun Intent.toPayload(): SharedPayload? {
        if (action != Intent.ACTION_SEND) return null
        val text = getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (text == null && uri == null) return null
        return SharedPayload(text = text, uri = uri, mimeType = type)
    }
}
