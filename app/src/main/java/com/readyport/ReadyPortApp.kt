package com.readyport

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.readyport.cloud.AppCheckInstaller
import com.readyport.cloud.CloudSync
import com.readyport.data.settings.SettingsRepository
import com.readyport.trip.TripRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import com.readyport.pack.PackSync
import com.readyport.vault.WalletRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ReadyPortApp : Application(), Configuration.Provider {

    @Inject lateinit var wallet: WalletRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var trips: TripRepository
    @Inject lateinit var ocr: com.readyport.doc.ocr.OcrEngine

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Firestore·FCM 요청에 App Check 토큰을 붙인다 (강제 여부는 콘솔에서)
        AppCheckInstaller.install(this)
        // 글자 인식 모델(Play 서비스) 미리 받기 — 없을 때만 내려받는다
        appScope.launch { ocr.prefetch() }
        // 앱이 화면에서 사라지면 지갑을 잠가 복호화한 내용을 메모리에서 지운다 (PRD 7.3)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = wallet.lock()
        })
        // 하루 한 번 찜한 나라의 새 안내 확인 (와이파이 설정을 따른다)
        appScope.launch { PackSync.scheduleDaily(this@ReadyPortApp, settings.current().wifiOnly) }
        // 찜한 나라·여행 나라가 바뀌면 토픽 구독과 익명 찜 수를 맞춘다. 밀린 실패 리포트도 이때 보낸다
        appScope.launch {
            combine(settings.settings, trips.trip) { s, t -> s.favorites to t?.country }
                .distinctUntilChanged()
                .collect { CloudSync.request(this@ReadyPortApp) }
        }
    }
}
