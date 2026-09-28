package com.readyport.security

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * 민감 화면(지갑·여권·예약 서류)에서 화면 캡처·최근 앱 미리보기를 막는다 (PRD 7.3 FLAG_SECURE).
 * 화면을 떠나면 다시 푼다.
 */
@Composable
fun SecureScreen() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
