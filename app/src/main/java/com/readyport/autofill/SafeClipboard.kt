package com.readyport.autofill

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * 값 복사 (수동 모드). 여권 번호 같은 값이 클립보드에 오래 남지 않게 60초 뒤 지운다
 * (PRD 7.4, 운영자 결정 A8). Android 13+에서는 '민감한 내용'으로 표시해 미리보기에 안 보이게 한다.
 */
class SafeClipboard(context: Context, private val clearAfterMs: Long = 60_000) {
    private val cm = context.getSystemService(ClipboardManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    fun copy(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        cm.setPrimaryClip(clip)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ clearIfOurs(label) }, clearAfterMs)
    }

    private fun clearIfOurs(label: String) {
        // 그사이 사용자가 다른 것을 복사했으면 건드리지 않는다
        val current = cm.primaryClipDescription?.label?.toString()
        if (current != label) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
        else cm.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
