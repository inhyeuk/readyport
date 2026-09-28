package com.readyport.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * 디버그 빌드: App Check 디버그 공급자. 처음 실행하면 logcat에 디버그 토큰이 찍히고,
 * 운영자가 Firebase 콘솔 › App Check › 디버그 토큰에 등록해야 강제 모드에서 통과한다 (사람 작업).
 */
object AppCheckInstaller {
    fun install(context: Context) {
        runCatching {
            FirebaseApp.initializeApp(context)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        }
    }
}
