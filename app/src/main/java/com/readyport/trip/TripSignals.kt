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
    val trip = trips.current() ?: return false
    val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(ZoneId.of(zone)).toLocalDate()
    if (!TripStages.isArrival(trip, today, zone)) return false
    trips.update { it.copy(arrivedAt = nowMillis, arrivalDismissed = false) }
    return true
}

object TripNotifications {
    private const val CHANNEL = "trip"
    private const val FORM_WINDOW = "form-window"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_trip), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun show(context: Context, id: Int, title: String, body: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // 알림에는 개인정보(이름·여권 번호)를 넣지 않는다
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, n)
    }

    fun arrival(context: Context) =
        show(context, 2, context.getString(R.string.notif_arrival_title), context.getString(R.string.notif_arrival_body))

    fun formWindow(context: Context, formName: String) =
        show(context, 1, context.getString(R.string.notif_form_title, formName), context.getString(R.string.notif_form_body))

    /** 입국 카드 제출 가능일 오전 9시에 알림 (로컬 알림, WorkManager — PRD 6.1) */
    fun scheduleFormWindow(context: Context, windowStart: LocalDate, formName: String) {
        val at = windowStart.atTime(9, 0)
        val delay = Duration.between(LocalDateTime.now(), at).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<FormWindowWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf("formName" to formName))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(FORM_WINDOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelFormWindow(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(FORM_WINDOW)
}

@HiltWorker
class FormWindowWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        TripNotifications.formWindow(applicationContext, inputData.getString("formName").orEmpty())
        return Result.success()
    }
}
