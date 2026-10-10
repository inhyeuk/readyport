package com.readyport.cloud

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.readyport.data.settings.SettingsRepository
import com.readyport.notice.NoticePushHandler
import com.readyport.pack.PackRepository
import com.readyport.pack.PackSync
import com.readyport.plan.PlanNotifications
import com.readyport.plan.PlanPush
import com.readyport.plan.PlanRepository
import com.readyport.trip.TripNotifications
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * FCM 토픽 알림 (ARCHITECTURE 9.6). 토큰은 서버에 따로 저장하지 않는다 (토픽 구독만).
 * - 공지 알림(`type = notice`, 토픽 notice_all · notice_promo · 조건 `… && country_{ISO2}`): 공지 id만 읽고,
 *   보이는 글은 서명된 공지에서 꺼낸다([NoticePushHandler]). 메시지에 글이 있어도 쓰지 않는다.
 * - 계획 도착 알림(`type = plan`, 토픽 plan_{내 익명 ID}): 요청 id만 읽고 '여행 계획이 도착했어요'를 띄운다([PlanPush]).
 * - 국가 토픽(country_{ISO2}): 메시지에서는 나라 코드만 읽는다. 보이는 문구는 앱에 들어 있는 것만 쓰고, 새 안내는 서명된 팩으로 받는다.
 */
@AndroidEntryPoint
class PolicyMessagingService : FirebaseMessagingService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var packs: PackRepository
    @Inject lateinit var notices: NoticePushHandler
    @Inject lateinit var plans: PlanRepository

    override fun onMessageReceived(message: RemoteMessage) {
        // 공지 알림을 먼저 가른다 — 공지 메시지가 나라 코드 흐름(입국 안내 바뀜)으로 새지 않게
        if (message.data["type"] == "notice") {
            runBlocking { runCatching { notices.handle(message.data) } }
            return
        }
        // 내 여행 계획이 도착했다는 알림(토픽 plan_<내 ID>): 요청 id만 읽는다. 글·계획 내용은 메시지에 없다
        if (message.data["type"] == "plan") {
            val id = PlanPush.requestId(message.data) ?: return
            runBlocking {
                if (settings.current().childMode) return@runBlocking
                runCatching { plans.markArrived(id) }
                PlanNotifications.post(this@PolicyMessagingService, listOf(id))
            }
            return
        }
        val country = message.data["country"]?.takeIf { Regex("^[A-Z]{2}$").matches(it) } ?: return
        runBlocking {
            PackSync.requestNow(this@PolicyMessagingService, settings.current().wifiOnly)
            val name = packs.index()?.value?.countries?.firstOrNull { it.code == country }?.nameKo ?: return@runBlocking
            TripNotifications.policyChanged(this@PolicyMessagingService, name)
        }
    }

    override fun onNewToken(token: String) {
        // 토픽 구독은 새 토큰에도 유지되지만, 혹시 몰라 다시 맞춘다. 토큰 값은 어디에도 보내지 않는다
        CloudSync.request(this)
    }
}
