package com.readyport.plan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.readyport.MainActivity
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.trip.TripNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 계획 도착 푸시(FCM) — 토픽 이름과 메시지 해석. 토큰은 서버에 저장하지 않는다 */
object PlanPush {
    private val SAFE = Regex("^[A-Za-z0-9_-]{1,64}$")

    /** 내 계획 토픽 — 로그인(익명) ID가 안전한 글자일 때만 */
    fun topic(uid: String): String = "plan_$uid".also { require(SAFE.matches(uid)) }

    /** 푸시 데이터에서 요청 id만 꺼낸다(type=plan). 모양이 다르면 null */
    fun requestId(data: Map<String, String>): String? =
        data["request"]?.takeIf { data["type"] == "plan" && Regex("^[A-Za-z0-9]{10,40}$").matches(it) }
}

/** '여행 계획이 도착했어요' 알림 통로 — 게시판 답글·챙길 일 알림과 따로 켜고 끈다 */
object PlanNotifications {
    const val CHANNEL = "plan"

    /** 알림 번호 (게시판 답글 3,000,001 다음) */
    const val ID = 3_000_002

    /** [ids]: 새로 도착한 요청 id — 하나면 그 계획, 여럿이면 내 계획 요청 목록을 연다 */
    fun post(context: Context, ids: List<String>): Boolean {
        if (ids.isEmpty() || !TripNotifications.canNotify(context)) return false
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.plan_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        // 누르면 그 계획(요청 id만 넘긴다 — 내용·나라는 알림에 넣지 않는다)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_PLAN, if (ids.size == 1) ids.first() else "")
        val open = PendingIntent.getActivity(context, ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = context.getString(R.string.plan_arrived_title)
        val body = if (ids.size == 1) context.getString(R.string.plan_arrived_body) else context.getString(R.string.plan_arrived_body_many, ids.size)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(ID, n)
        return true
    }
}

/**
 * 끝나지 않은 내 계획 요청이 도착했는지 본다 — 앱을 켤 때·돌아올 때와 하루 한 번(챙길 일 쓸기·팩 받기 작업, 게시판 답글 확인 옆).
 * 서버 토큰(FCM) 없이 앱이 스스로 확인해 이 휴대폰 안에서 알린다. 자녀 폰 모드·로그인한 적 없음·끝나지 않은 요청 없음이면 아무것도 하지 않는다.
 */
@Singleton
class PlanArrivalCheck @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plans: PlanRepository,
    private val settings: SettingsRepository,
) {
    /** [minGapSeconds]: 앱으로 돌아올 때는 10분 안에 또 읽지 않는다 */
    suspend fun run(minGapSeconds: Long = 0) {
        if (settings.current().childMode) return
        if (!pushEnsured) {
            pushEnsured = true
            runCatching { plans.ensurePush() }
        }
        val done = runCatching { plans.arrivals(minGapSeconds) }.getOrDefault(emptyList())
        PlanNotifications.post(context, done)
    }

    private var pushEnsured = false

    companion object {
        const val RESUME_GAP_SECONDS = 600L
    }
}
