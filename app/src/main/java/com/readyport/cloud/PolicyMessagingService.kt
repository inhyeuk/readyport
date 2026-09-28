package com.readyport.cloud

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.PackRepository
import com.readyport.pack.PackSync
import com.readyport.trip.TripNotifications
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * 국가 토픽(country_{ISO2}) 알림 (ARCHITECTURE 9.6).
 * 메시지에서는 나라 코드만 읽는다. 보이는 문구는 앱에 들어 있는 것만 쓰고, 새 안내는 서명된 팩으로 받는다.
 * 토큰은 서버에 따로 저장하지 않는다 (토픽 구독만).
 */
@AndroidEntryPoint
class PolicyMessagingService : FirebaseMessagingService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var packs: PackRepository

    override fun onMessageReceived(message: RemoteMessage) {
        val country = message.data["country"]?.takeIf { Regex("^[A-Z]{2}$").matches(it) } ?: return
        runBlocking {
            PackSync.requestNow(this@PolicyMessagingService, settings.current().wifiOnly)
            val name = packs.index()?.value?.countries?.firstOrNull { it.code == country }?.nameKo ?: return@runBlocking
            TripNotifications.policyChanged(this@PolicyMessagingService, name)
        }
    }

    override fun onNewToken(token: String) {
        // 토픽 구독은 새 토큰에도 유지되지만, 혹시 몰라 다시 맞춘다
        CloudSync.request(this)
    }
}
