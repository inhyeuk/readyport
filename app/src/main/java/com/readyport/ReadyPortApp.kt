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
    @Inject lateinit var tripSignals: com.readyport.trip.TripSignalsRecorder
    @Inject lateinit var checklistAlerts: com.readyport.trip.ChecklistAlerts
    @Inject lateinit var boardReplies: com.readyport.board.BoardReplyCheck
    @Inject lateinit var board: com.readyport.board.BoardRepository

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
        // 예전 한 여행 저장본을 여행 목록으로 옮긴다(예전 꼭 챙길 물건 체크는 그 여행 체크리스트로). 그다음 지갑을 열 때마다 여행별 결과만 적는다
        appScope.launch {
            trips.migrateLegacy(settings.current().haveItems)
            tripSignals.start(appScope)
            // 못한 일 알림: 하루 쓸기와 여행별 입국 카드 알림을 여기서 맞춘다(화면을 열지 않아도 알려 준다 — PRD 6.1)
            checklistAlerts.start(appScope)
        }
        // 게시판 나이 확인(만 19세): 보관함이 열릴 때마다 본인 여권 생년월일로 판정해 결과 값만 남긴다(생년월일은 남기지 않는다)
        appScope.launch {
            wallet.state.collect { s ->
                if (s is com.readyport.vault.WalletState.Unlocked) runCatching { board.recordAge(s.contents.passport?.birthDate) }
            }
        }
        // 게시판 답글: 앱을 켤 때 한 번 확인(로그인한 적이 없거나 알림을 껐으면 아무것도 하지 않는다). 하루 한 번은 쓸기 작업이 본다
        appScope.launch { runCatching { boardReplies.run() } }
        // 하루 한 번 찜한 나라의 새 안내 확인 (와이파이 설정을 따른다)
        appScope.launch { PackSync.scheduleDaily(this@ReadyPortApp, settings.current().wifiOnly) }
        // 찜한 나라·여행 나라·공지 알림 설정이 바뀌면 토픽 구독과 익명 찜 수를 맞춘다. 밀린 실패 리포트도 이때 보낸다
        appScope.launch {
            combine(settings.settings, trips.trip) { s, t -> listOf(s.favorites, t?.country, s.noticePush, s.promoPush, s.childMode) }
                .distinctUntilChanged()
                .collect { CloudSync.request(this@ReadyPortApp) }
        }
    }
}
