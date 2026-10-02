package com.readyport.trip

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.readyport.MainActivity
import com.readyport.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 도착 감지 (PRD 4.2): 위치 권한 없이 시간대 변경 + 여행 일정으로 판단한다.
 * TIMEZONE_CHANGED 는 매니페스트 등록이 허용된 방송이다.
 */
class TimezoneReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun trips(): TripRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val zone = intent.getStringExtra(Intent.EXTRA_TIMEZONE) ?: ZoneId.systemDefault().id
        val trips = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).trips()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (onZoneChanged(trips, zone, System.currentTimeMillis())) TripNotifications.arrival(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * 시간대가 바뀌었을 때: 여행 중 한국 밖 시간대면 도착으로 기록한다. 기록했으면 true.
 * 리시버와 테스트가 같은 함수를 쓴다.
 */
suspend fun onZoneChanged(trips: TripRepository, zone: String, nowMillis: Long): Boolean {
    val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(ZoneId.of(zone)).toLocalDate()
    // 여러 여행 중 그날 기준 '지금 여행'(여행 중 → 가장 가까운 다가오는 여행) — 출발 전날 밤 도착도 같은 여행으로 본다
    val trip = TripSelection.active(trips.all(), today) ?: return false
    if (!TripStages.isArrival(trip, today, zone)) return false
    trips.update(trip.id) { it.copy(arrivedAt = nowMillis, arrivalDismissed = false) }
    return true
}

object TripNotifications {
    private const val CHANNEL = "trip"

    /** 챙길 일 알림 — 여행 알림(도착·안내 변경)과 따로 켜고 끌 수 있게 통로를 나눈다 */
    private const val CHANNEL_CHECKLIST = "checklist"

    /** 여행별 입국 카드 알림 작업 이름 앞머리 (여행마다 따로 — 예전에는 이름 하나라 두 번째 여행이 첫 여행을 밀어냈다) */
    private const val FORM_WINDOW = "form-window"

    /** 여행별 입국 카드 알림 작업 태그 (여행 하나만, 또는 모두 지울 때) */
    const val TAG_FORM_WINDOW = "form-window"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_trip), NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CHECKLIST, context.getString(R.string.notif_channel_checklist), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** 휴대폰 알림 권한(Android 13+)이 있는지 */
    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** 알렸으면 true (권한이 없으면 아무것도 하지 않고 false — 그러면 '오늘 알림' 기록도 남기지 않는다) */
    private fun show(
        context: Context,
        id: Int,
        title: String,
        body: String,
        channel: String = CHANNEL,
        group: String? = null,
        tripId: String? = null,
    ): Boolean {
        if (!canNotify(context)) return false
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // 누르면 그 여행의 체크리스트로 (여행 id만 넘긴다 — 개인정보 없음)
        if (tripId != null) intent.putExtra(MainActivity.EXTRA_OPEN_CHECKLIST, tripId)
        val open = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // 알림에는 개인정보(이름·여권 번호·생년월일)를 넣지 않는다
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply { if (group != null) setGroup(group) }
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, n)
        return true
    }

    /** 찜한 나라의 입국 안내가 바뀌었다는 알림 (FCM 토픽). 문구는 앱에 있는 것만 쓴다 */
    fun policyChanged(context: Context, countryKo: String) =
        show(context, 4, context.getString(R.string.notif_policy_title, countryKo), context.getString(R.string.notif_policy_body))

    fun arrival(context: Context) =
        show(context, 2, context.getString(R.string.notif_arrival_title), context.getString(R.string.notif_arrival_body))

    /**
     * 한 여행의 '아직 못한 일' 알림 하나 (PRD 6.1). 제목 = `태국 여행 · 11월 3일 출발`,
     * 본문 = 왜 알리는지 + 못한 일 개수와 첫 항목 이름. 누르면 그 여행 체크리스트가 열린다.
     * 알림 번호는 여행 id에서 만들어(여행마다 하나) 여러 여행이 서로를 지우지 않는다.
     */
    fun checklist(context: Context, reminder: Reminder, countryKo: String): Boolean {
        val date = context.getString(R.string.today_date_md, reminder.departure.monthValue, reminder.departure.dayOfMonth)
        val title = context.getString(R.string.alert_title, countryKo, date)
        val lead = itemsLead(context, reminder.titles)
        val body = when (reminder.reason) {
            ReminderReason.FormWindow -> context.getString(R.string.alert_body_form, reminder.formName.orEmpty())
            ReminderReason.Overdue -> context.getString(R.string.alert_body_todo, reminder.count, lead)
            ReminderReason.Countdown -> context.getString(R.string.alert_body_countdown, reminder.daysLeft.toInt(), reminder.count, lead)
        }
        return show(
            context, notificationId(reminder.tripId), title, body,
            channel = CHANNEL_CHECKLIST, group = group(reminder.tripId), tripId = reminder.tripId,
        )
    }

    /** `여권 남은 기간 확인하기 외 2개` (하나면 제목만) */
    private fun itemsLead(context: Context, titles: List<String>): String {
        val first = titles.firstOrNull().orEmpty()
        return if (titles.size <= 1) first else context.getString(R.string.alert_items_more, first, titles.size - 1)
    }

    /** 여행별 알림 번호 — 다른 알림(1·2·4)과 겹치지 않게 1000부터 */
    fun notificationId(tripId: String): Int = 1_000 + tripId.hashCode().mod(900_000)

    /** 여행별 알림 묶음 이름 */
    private fun group(tripId: String) = "readyport.trip.$tripId"

    /** 그 여행 알림을 치운다 (여행을 지웠을 때) */
    fun cancel(context: Context, tripId: String) =
        NotificationManagerCompat.from(context).cancel(notificationId(tripId))

    /** 여행·양식마다 다른 작업 이름 (예전에는 `form-window` 하나라 여행이 여럿이면 하나만 남았다) */
    fun formWorkName(tripId: String, formId: String) = "$FORM_WINDOW:$tripId:$formId"

    /** 여행 하나의 입국 카드 알림 작업 태그 */
    fun formWorkTag(tripId: String) = "$FORM_WINDOW:$tripId"

    /** 입국 카드를 낼 수 있는 첫날 [hour]시에 알림 (로컬 알림, WorkManager — PRD 6.1). 작업은 여행·양식마다 따로 */
    fun scheduleFormWindow(context: Context, tripId: String, formId: String, windowStart: LocalDate, hour: Int) {
        val at = windowStart.atTime(hour, 0)
        val delay = Duration.between(LocalDateTime.now(), at).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<FormWindowWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf(FormWindowWorker.TRIP_ID to tripId))
            .addTag(TAG_FORM_WINDOW)
            .addTag(formWorkTag(tripId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(formWorkName(tripId, formId), ExistingWorkPolicy.REPLACE, request)
    }

    /** 여행 하나의 입국 카드 알림만 지운다 */
    fun cancelFormWindow(context: Context, tripId: String) =
        WorkManager.getInstance(context).cancelAllWorkByTag(formWorkTag(tripId))

    /** 모든 여행의 입국 카드 알림을 지운다 (다시 맞추기 전·알림을 껐을 때) */
    fun cancelFormWindows(context: Context) =
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_FORM_WINDOW)
}

/**
 * 입국 카드 기간이 열리는 첫날 그 여행만 다시 살펴본다 — 알릴 것·하루 한 번 규칙은 [ChecklistAlerts]가 하루 쓸기와 똑같이 판단한다.
 */
@HiltWorker
class FormWindowWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val alerts: ChecklistAlerts,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        alerts.sweep(scheduled = true, onlyTrip = inputData.getString(TRIP_ID))
        return Result.success()
    }

    companion object {
        const val TRIP_ID = "tripId"
    }
}
