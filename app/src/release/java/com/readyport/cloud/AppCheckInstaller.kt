package com.readyport.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * 출시 빌드: Play Integrity로 App Check. Play Console 앱 연결과 Firebase 콘솔 등록은 사람 작업.
 */
object AppCheckInstaller {
    fun install(context: Context) {
        runCatching {
            FirebaseApp.initializeApp(context)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
    }
}
