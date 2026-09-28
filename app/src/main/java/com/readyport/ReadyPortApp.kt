package com.readyport

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.readyport.vault.WalletRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ReadyPortApp : Application() {

    @Inject lateinit var wallet: WalletRepository

    override fun onCreate() {
        super.onCreate()
        // 앱이 화면에서 사라지면 지갑을 잠가 복호화한 내용을 메모리에서 지운다 (PRD 7.3)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = wallet.lock()
        })
    }
}
