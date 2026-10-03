package com.readyport.trip

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 챙길 일 알림 설정 — 기기 안 설정(DataStore)에 그대로 들어 있는 값만. 개인정보 없음.
 * [lastNotified]: 여행 id → 마지막으로 알린 날(한 여행에 하루 한 번만 알리려고).
 */
data class AlertSettings(
    val enabled: Boolean = true,
    val hour: Int = ChecklistReminders.DEFAULT_HOUR,
    /** 알림을 꺼 둔 여행 id */
    val mutedTrips: Set<String> = emptySet(),
    val lastNotified: Map<String, LocalDate> = emptyMap(),
)

/** 왜 알리는지 (알림 한 줄 문구가 달라진다) */
enum class ReminderReason {
    /** 입국 카드를 낼 수 있는 기간이 오늘 열렸고 아직 안 냈다 */
    FormWindow,

    /** 기한이 지났는데 아직 안 한 항목이 있다 ([ChecklistItem.overdue]) */
    Overdue,

    /** 출발 7·3·1일 전 요약 */
    Countdown,
}

/**
 * 여행 하나에 보낼 알림 하나.
 * [titles]: 아직 못한 항목의 제목 — 색인 틀·나라 팩에서 온 글뿐이다. **내가 넣은 항목(사람이 쓴 글)은 담지 않는다**
 * (알림에 개인정보가 들어가지 않게 — ARCHITECTURE 9.9). 급한 것 → 늦은 것 → 단계 순서.
 */
data class Reminder(
    val tripId: String,
    /** 나라 코드(ISO2). 한국어 이름은 알림을 그리는 쪽이 팩에서 찾는다 */
    val country: String,
    val departure: LocalDate,
    val reason: ReminderReason,
    val titles: List<String>,
    /** 출발까지 남은 날(지났으면 음수) */
    val daysLeft: Long,
    /** [ReminderReason.FormWindow]일 때 입국 카드 이름 */
    val formName: String? = null,
) {
    val count: Int get() = titles.size
}

/**
 * 무엇을 언제 알릴지 정하는 **순수 함수** (안드로이드 없음 — 단위 테스트가 그대로 돌린다).
 *
 * 알리는 것 (여행마다 하루 한 번, 알림 하나):
 * ① 입국 카드를 낼 수 있는 기간이 **오늘** 열렸고 아직 안 냈다
 * ② 기한이 지났는데 아직 안 한 항목이 있다 (체크리스트 화면과 같은 [ChecklistItem.overdue] 규칙)
 * ③ 출발 7·3·1일 전인데 지금 단계까지 못한 일이 남았다
 *
 * 알리지 않는 것: 지금 단계까지 다 했을 때 · 아직 열리지 않은 뒤 단계 · 아직 할 수 없는 항목(입국 카드 기간 전) ·
 * 기간이 정해지지 않은 입국 카드(늦음을 붙이지 않는다) · 알림을 꺼 둔 여행 · 오늘 이미 알린 여행 · 날짜가 깨진 여행.
 */
object ChecklistReminders {

    /** 기본 알림 시각(아침 9시) */
    const val DEFAULT_HOUR = 9

    /** 설정에서 고를 수 있는 시각 */
    val HOURS = listOf(8, 9, 20)

    /** 출발 전 요약을 보내는 날 (D-7 · D-3 · D-1) */
    val COUNTDOWN_DAYS = listOf(7L, 3L, 1L)

    /** 알린 날 기록을 남겨 두는 기간 */
    const val KEEP_DAYS = 30L

    /**
     * 오늘 보낼 알림 — 여행마다 많아야 하나.
     * @param checklists 여행 id → 그 여행의 체크리스트([Checklist.build]로 오늘 날짜로 다시 만든 것)
     */
    fun remindersFor(
        trips: List<Trip>,
        checklists: Map<String, ChecklistData>,
        today: LocalDate,
        settings: AlertSettings,
    ): List<Reminder> {
        if (!settings.enabled) return emptyList()
        return trips.filter { it.datesValid && it.id !in settings.mutedTrips && settings.lastNotified[it.id] != today }
            .mapNotNull { trip -> checklists[trip.id]?.let { reminderFor(trip, it, today) } }
    }

    /** 여행 하나: 알릴 것이 있으면 알림 하나, 없으면 null */
    fun reminderFor(trip: Trip, data: ChecklistData, today: LocalDate): Reminder? {
        val current = Checklist.currentDue(trip, today)
        // 지금 기한 칸까지의 안 한 항목만 — 뒤 칸은 알리지 않고, 아직 할 수 없는 항목(기간 전)도 뺀다.
        // 내가 넣은 항목(due == null, 사람이 쓴 글)은 알림에 담지 않는다.
        // 묶는 축(단계)이 바뀌어도 알리는 항목은 그대로다 — 알림은 기한 축만 본다
        val pending = data.items
            .filter { it.due != null && it.due <= current && !it.checked && !it.locked(today) }
            // 급한 것 → 늦은 것 → 기한이 이른 것. 기한으로 묶어 세우므로 **묶는 단계가 바뀌어도 차례가 흔들리지 않는다**
            .sortedWith(compareBy({ !it.urgent }, { !it.overdue }, { it.due }))
        if (pending.isEmpty()) return null
        val titles = pending.map { it.title }
        val daysLeft = trip.start.toEpochDay() - today.toEpochDay()
        fun of(reason: ReminderReason, formName: String? = null) =
            Reminder(trip.id, trip.country, trip.start, reason, titles, daysLeft, formName)
        // ① 오늘 입국 카드 기간이 열렸다 (예전 한 번짜리 알림을 대신한다)
        val opening = pending.firstOrNull { it.opensOn == today && it.detail is ItemDetail.Form }
        if (opening != null) return of(ReminderReason.FormWindow, (opening.detail as ItemDetail.Form).formName)
        // ② 기한이 지난 항목
        if (pending.any { it.overdue }) return of(ReminderReason.Overdue)
        // ③ 출발 전 요약
        if (daysLeft in COUNTDOWN_DAYS) return of(ReminderReason.Countdown)
        return null
    }

    /** [now]에서 다음 [hour]시까지 — 오늘 그 시각이 지났으면 다음 날 (하루 쓸기 작업의 첫 지연) */
    fun delayToHour(now: LocalDateTime, hour: Int): Duration {
        val todayAt = now.toLocalDate().atTime(hour, 0)
        val at = if (now.isBefore(todayAt)) todayAt else todayAt.plusDays(1)
        return Duration.between(now, at)
    }

    /** 고른 시각이 목록에 없으면(예전 저장값) 기본값으로 */
    fun hourOrDefault(hour: Int): Int = if (hour in HOURS) hour else DEFAULT_HOUR
}
