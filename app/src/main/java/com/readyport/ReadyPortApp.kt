package com.readyport

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.readyport.data.settings.SettingsRepository
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // 앱이 화면에서 사라지면 지갑을 잠가 복호화한 내용을 메모리에서 지운다 (PRD 7.3)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = wallet.lock()
        })
        // 하루 한 번 찜한 나라의 새 안내 확인 (와이파이 설정을 따른다)
        appScope.launch { PackSync.scheduleDaily(this@ReadyPortApp, settings.current().wifiOnly) }
    }
}
