package com.readyport.trip

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.readyport.data.settings.AppSettings
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.PackRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 챙길 일 알림의 살림살이 — **화면이 아니라 여기**에서 작업을 걸고 알린다 (PRD 6.1).
 * 예전에는 체크리스트 화면이 입국 카드 알림을 걸어서, 그 화면을 한 번도 열지 않은 사람은 알림을 받지 못했다.
 *
 * - [start]: 앱이 켜질 때 한 번. 여행·체크·팩·알림 설정이 바뀌면 작업을 다시 맞추고(묶어서) 한 번 살펴본다.
 * - [sweep]: 여행마다 체크리스트를 **다시 만들어** 알릴 것을 정하고 알린다. 앱을 열지 않아도 [ChecklistSweepWorker]가 하루 한 번 돌린다.
 * - 알릴지 말지는 순수 함수 [ChecklistReminders.remindersFor]가 정한다(테스트가 그대로 돌린다).
 * - 기기 안에 있는 값만 읽는다 — 여행 장부·체크·팩. 서버로 보내는 것은 없다.
 */
@Singleton
class ChecklistAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trips: TripRepository,
    private val checklists: ChecklistProvider,
    private val packs: PackRepository,
    private val settings: SettingsRepository,
) {

    /**
     * 앱이 켜질 때: 하루 쓸기를 걸고, 여행·체크·팩·알림 설정이 바뀌면 다시 맞춘다.
     * 바뀜이 몰아칠 때는 [DEBOUNCE_MS]만큼 기다려 한 번만 한다(collectLatest가 앞의 것을 버린다).
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            // 알린 날 기록(lastNotified)은 흐름에 넣지 않는다 — 알리고 나서 또 돌지 않게
            val knobs = settings.settings.map { Knobs(it.alertsOn, it.alertHour, it.alertMutedTrips) }.distinctUntilChanged()
            combine(trips.book, knobs, packs.revision) { book, k, revision -> Triple(book, k, revision) }
                .collectLatest {
                    delay(DEBOUNCE_MS)
                    refresh()
                    sweep(scheduled = false)
                }
        }
    }

    /** 작업 다시 맞추기: 하루 쓸기 + 여행마다 입국 카드 기간 알림 */
    suspend fun refresh(now: LocalDateTime = LocalDateTime.now()) {
        val s = settings.current()
        val wm = WorkManager.getInstance(context)
        TripNotifications.cancelFormWindows(context)
        if (!s.alertsOn) {
            wm.cancelUniqueWork(DAILY)
            return
        }
        val hour = ChecklistReminders.hourOrDefault(s.alertHour)
        val request = PeriodicWorkRequestBuilder<ChecklistSweepWorker>(1, TimeUnit.DAYS)
            // 첫 번째는 다음 'hour'시에. 그다음은 하루마다 — 돌 때마다 이 함수가 다시 맞춰 시각이 밀리지 않게 한다
            .setInitialDelay(ChecklistReminders.delayToHour(now, hour).toMillis(), TimeUnit.MILLISECONDS)
            .addTag(DAILY)
            .build()
        wm.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, request)
        scheduleFormWindows(trips.book.first(), now.toLocalDate(), hour, s)
    }

    /** 여행을 지웠을 때: 그 여행의 알림과 작업을 치운다 */
    fun forget(tripId: String) {
        TripNotifications.cancel(context, tripId)
        TripNotifications.cancelFormWindow(context, tripId)
    }

    /**
     * 알릴 것을 정해서 알린다.
     * @param scheduled 하루 쓸기 작업이 부른 것인지. 아니면(앱 시작·바뀜) 고른 시각이 지난 뒤에만 알린다 —
     *   아침 9시로 해 둔 사람이 새 여행을 만든 그 순간 알림을 받지 않게, 그리고 기기가 꺼져 있어 지나친 날은 따라잡게.
     * @param onlyTrip 그 여행만 (입국 카드 기간 알림)
     */
    suspend fun sweep(scheduled: Boolean, onlyTrip: String? = null, now: LocalDateTime = LocalDateTime.now()) {
        val s = settings.current()
        if (!s.alertsOn) return
        if (!scheduled && now.hour < ChecklistReminders.hourOrDefault(s.alertHour)) return
        val today = now.toLocalDate()
        val book = trips.book.first()
        val list = book.trips.filter { it.datesValid && (onlyTrip == null || it.id == onlyTrip) }
        if (list.isEmpty()) return
        val data = list.associate { it.id to checklists.build(it, book, today) }
        val reminders = ChecklistReminders.remindersFor(list, data, today, s.alertSettings())
        val sent = reminders.filter { TripNotifications.checklist(context, it, checklists.countryName(it.country)) }
        if (sent.isNotEmpty()) settings.markAlerted(sent.map { it.tripId }, today)
    }

    /**
     * 여행마다 입국 카드 기간이 열리는 첫날 알림을 걸어 둔다 — 기간이 정해진 양식이 있고, 아직 안 냈고, 열리는 날이 아직 오지 않은 여행만.
     * 작업 이름은 여행·양식마다 달라 여행이 여럿이어도 서로를 밀어내지 않는다.
     */
    private suspend fun scheduleFormWindows(book: TripBook, today: LocalDate, hour: Int, s: AppSettings) {
        book.trips.filter { it.datesValid && it.id !in s.alertMutedTrips && !today.isAfter(it.start) }.forEach { trip ->
            // 꼭 내야 하는 입국 카드만 기한 알림을 만든다 — 의무가 아닌 신고(forms[].optional)는 뺀다
            val form = packs.pack(trip.country)?.value?.requiredForms?.firstOrNull() ?: return@forEach
            val days = form.windowDaysIncludingArrival?.takeIf { it >= 1 } ?: return@forEach
            if (book.checks[trip.id]?.formSubmitted == true) return@forEach
            val opens = trip.start.minusDays((days - 1).toLong())
            if (opens.isBefore(today)) return@forEach
            TripNotifications.scheduleFormWindow(context, trip.id, form.id, opens, hour)
        }
    }

    /** 작업을 다시 맞출 거리 — 알린 날 기록은 뺀다 */
    private data class Knobs(val on: Boolean, val hour: Int, val muted: Set<String>)

    private companion object {
        /** 하루 쓸기 작업 이름 */
        const val DAILY = "checklist-sweep"

        /** 바뀜이 몰아칠 때 기다리는 시간 */
        const val DEBOUNCE_MS = 700L
    }
}

/** 저장된 설정 → 순수 판단([ChecklistReminders])에 넘길 값. 깨진 날짜는 버린다 */
internal fun AppSettings.alertSettings() = AlertSettings(
    enabled = alertsOn,
    hour = ChecklistReminders.hourOrDefault(alertHour),
    mutedTrips = alertMutedTrips,
    lastNotified = alertLastNotified.mapNotNull { (id, day) -> runCatching { id to LocalDate.parse(day) }.getOrNull() }.toMap(),
)

/**
 * 하루 한 번(고른 시각) 모든 여행을 살펴보고 못한 일을 알린다 — 앱을 열지 않아도 돈다.
 * 돈 뒤에 다음 날 같은 시각으로 다시 맞춘다(주기 작업이 조금씩 밀리는 것을 바로잡는다).
 */
@HiltWorker
class ChecklistSweepWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val alerts: ChecklistAlerts,
    private val boardReplies: com.readyport.board.BoardReplyCheck,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        alerts.sweep(scheduled = true)
        alerts.refresh()
        // 게시판 답글도 하루 한 번 (서버 토큰 없이 — docs/BOARD.md)
        runCatching { boardReplies.run() }
        return Result.success()
    }
}
