package com.readyport.trip

import com.readyport.pack.CountryPack
import com.readyport.ui.TestPacks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 챙길 일 알림 판단 (2026-10-03): 순수 함수 [ChecklistReminders]만 — 안드로이드 없이 서명된 내장 팩으로 돌린다.
 * 태국 TDAC는 기간 3일(도착일 포함)이라 11월 3일 출발이면 11월 1일에 기간이 열린다.
 */
class ChecklistRemindersTest {

    private val index = TestPacks.index.value
    private fun pack(cc: String): CountryPack = runBlocking { TestPacks.repo.pack(cc)!!.value }
    private fun d(s: String) = LocalDate.parse(s)

    private val th = Trip(country = "TH", startDate = "2026-11-03", endDate = "2026-11-07", id = "t-th")
    private val jp = Trip(country = "JP", startDate = "2026-11-03", endDate = "2026-11-07", id = "t-jp")

    private fun data(trip: Trip, today: LocalDate, checks: TripChecks = TripChecks()) =
        Checklist.build(Checklist.Input(trip, index, pack(trip.country), checks, passportSaved = true, today = today))

    /** [phase]보다 앞선 단계의 항목을 모두 '했음'으로 (늦음을 없애고 지금 단계만 남긴다) */
    private fun checkedBefore(trip: Trip, today: LocalDate, phase: ChecklistPhase): TripChecks =
        TripChecks(marks = data(trip, today).items.filter { it.phase!! < phase }.associate { it.id to true })

    /** 지금 단계까지 **모두** 했음 */
    private fun checkedThrough(trip: Trip, today: LocalDate): TripChecks {
        val current = Checklist.currentPhase(trip, today)
        return TripChecks(marks = data(trip, today).items.filter { it.phase!! <= current }.associate { it.id to true })
    }

    private fun reminders(
        trips: List<Trip>,
        today: LocalDate,
        checks: Map<String, TripChecks> = emptyMap(),
        settings: AlertSettings = AlertSettings(),
    ) = ChecklistReminders.remindersFor(
        trips,
        trips.associate { it.id to data(it, today, checks[it.id] ?: TripChecks()) },
        today,
        settings,
    )

    // ---------------- 늦은 항목 ----------------

    @Test
    fun overdueItemsAreNotifiedAndFuturePhasesAreNot() {
        val today = d("2026-10-31") // 출발 3일 전 — '떠나기 한 달 전쯤'(10월 26일까지)·'일주일 전'(10월 30일까지)이 지났다
        val list = reminders(listOf(th), today)
        val r = list.single()
        assertEquals(ReminderReason.Overdue, r.reason)
        assertEquals(th.id, r.tripId)
        assertEquals(th.start, r.departure)
        // 알리는 제목은 지금 단계까지의 것뿐 — 뒤 단계('돌아와서' 등)는 담지 않는다
        val current = Checklist.currentPhase(th, today)
        val allowed = data(th, today).items.filter { it.phase!! <= current }.map { it.title }
        assertTrue(r.titles.toString(), r.titles.isNotEmpty() && r.titles.all { it in allowed })
        // 아직 기간이 열리지 않은 입국 카드(11월 1일부터)는 담지 않는다
        val form = data(th, today).items.first { it.detail is ItemDetail.Form }
        assertTrue("기간 전 입국 카드가 알림에 들어감", form.title !in r.titles)
        // 첫 제목은 가장 급한 것(늦은 항목)
        val overdue = data(th, today).items.filter { it.overdue }.map { it.title }
        assertTrue(r.titles.first() in overdue)
    }

    /** 늦은 것이 없고 D-7·3·1도 아닌 날은 아무것도 알리지 않는다 (조르지 않는다) */
    @Test
    fun quietWhenNothingIsOverdueAndNoCountdownDay() {
        val today = d("2026-10-29") // 출발 5일 전 — '일주일 전'은 10월 30일까지라 아직 늦지 않았다
        val checks = checkedBefore(th, today, ChecklistPhase.Week)
        val r = reminders(listOf(th), today, mapOf(th.id to checks))
        assertTrue(r.toString(), r.isEmpty())
    }

    /** 지금 단계까지 다 했으면 알리지 않는다 */
    @Test
    fun nothingWhenEverythingRelevantIsChecked() {
        val today = d("2026-10-31")
        val r = reminders(listOf(th), today, mapOf(th.id to checkedThrough(th, today)))
        assertTrue(r.toString(), r.isEmpty())
    }

    // ---------------- 입국 카드 기간 ----------------

    @Test
    fun formWindowOpeningTodayIsNotifiedWithTheFormName() {
        val today = d("2026-11-01") // TDAC 기간 첫날 (3일 전부터)
        val r = reminders(listOf(th), today).single()
        assertEquals(ReminderReason.FormWindow, r.reason)
        assertEquals(pack("TH").forms.first().nameKo, r.formName)
        assertEquals(2L, r.daysLeft)
    }

    /** 이미 낸 여행은 기간이 열려도 알리지 않는다(다른 늦은 일이 없으면) */
    @Test
    fun submittedFormDoesNotNotify() {
        val today = d("2026-11-01")
        val checks = checkedThrough(th, today).copy(formSubmitted = true)
        assertTrue(reminders(listOf(th), today, mapOf(th.id to checks)).isEmpty())
    }

    /** 기간이 정해지지 않은 입국 카드(일본)는 늦음·급함을 붙이지 않는다 — 그것 때문에 알리지 않는다 */
    @Test
    fun formWithoutWindowIsNeverOverdue() {
        val today = d("2026-11-04") // 출발 다음 날 — 출발하는 날 단계가 지났다
        val checks = TripChecks(
            marks = data(jp, today).items
                .filter { it.phase!! <= ChecklistPhase.Arrival && it.detail !is ItemDetail.Form }
                .associate { it.id to true },
        )
        val r = reminders(listOf(jp), today, mapOf(jp.id to checks))
        assertTrue(r.toString(), r.isEmpty())
    }

    // ---------------- 출발 전 요약 ----------------

    @Test
    fun countdownSummaryOnSevenThreeAndOneDayBefore() {
        listOf("2026-10-27" to 7L, "2026-10-31" to 3L, "2026-11-02" to 1L).forEach { (day, left) ->
            val today = d(day)
            val current = Checklist.currentPhase(th, today)
            val r = reminders(listOf(th), today, mapOf(th.id to checkedBefore(th, today, current))).single()
            assertEquals("$day 단계 요약", ReminderReason.Countdown, r.reason)
            assertEquals(left, r.daysLeft)
            assertEquals(r.titles.size, r.count)
        }
    }

    @Test
    fun noCountdownOnOtherDays() {
        listOf("2026-10-28", "2026-10-29", "2026-11-01").forEach { day ->
            val today = d(day)
            val current = Checklist.currentPhase(th, today)
            val checks = checkedBefore(th, today, current)
            // 11월 1일은 입국 카드 기간이 열리는 날이라 그 이유로 알린다 — 요약(Countdown)은 아니다
            val r = reminders(listOf(th), today, mapOf(th.id to checks)).firstOrNull()
            assertTrue("$day: $r", r == null || r.reason != ReminderReason.Countdown)
        }
    }

    // ---------------- 하루 한 번 · 여행마다 따로 · 설정 ----------------

    @Test
    fun onlyOnceADayPerTrip() {
        val today = d("2026-10-31")
        val sent = AlertSettings(lastNotified = mapOf(th.id to today))
        assertTrue(reminders(listOf(th), today, settings = sent).isEmpty())
        // 어제 알린 것은 오늘을 막지 않는다
        val yesterday = AlertSettings(lastNotified = mapOf(th.id to today.minusDays(1)))
        assertEquals(1, reminders(listOf(th), today, settings = yesterday).size)
    }

    @Test
    fun twoTripsDoNotCollide() {
        val today = d("2026-10-31")
        val list = reminders(listOf(th, jp), today)
        assertEquals(2, list.size)
        assertEquals(listOf(th.id, jp.id), list.map { it.tripId })
        // 한 여행에만 오늘 알렸으면 다른 여행은 그대로 알린다
        val one = reminders(listOf(th, jp), today, settings = AlertSettings(lastNotified = mapOf(th.id to today)))
        assertEquals(listOf(jp.id), one.map { it.tripId })
    }

    @Test
    fun mutedTripIsExcluded() {
        val today = d("2026-10-31")
        val list = reminders(listOf(th, jp), today, settings = AlertSettings(mutedTrips = setOf(th.id)))
        assertEquals(listOf(jp.id), list.map { it.tripId })
    }

    @Test
    fun alertsOffMeansNothingAtAll() {
        val today = d("2026-10-31")
        assertTrue(reminders(listOf(th, jp), today, settings = AlertSettings(enabled = false)).isEmpty())
    }

    /** 날짜가 깨진 여행은 건너뛴다 (목록·단계 계산과 같은 규칙) */
    @Test
    fun brokenDatesAreSkipped() {
        val broken = th.copy(id = "t-broken", startDate = "안 날짜")
        val list = ChecklistReminders.remindersFor(listOf(broken), emptyMap(), d("2026-10-31"), AlertSettings())
        assertTrue(list.isEmpty())
    }

    /** 돌아온 뒤에는 조르지 않는다 — '돌아와서' 항목에는 늦음이 붙지 않는다 (Checklist.overdueApplies) */
    @Test
    fun afterTheTripNothingIsOverdue() {
        val today = d("2026-11-10")
        val checks = TripChecks(marks = data(th, today).items.filter { it.phase!! <= ChecklistPhase.Arrival }.associate { it.id to true })
        assertTrue(reminders(listOf(th), today, mapOf(th.id to checks)).isEmpty())
    }

    // ---------------- 알림 시각 ----------------

    @Test
    fun delayToHourWaitsForTheNextChosenHour() {
        val hour = 9
        // 아침 7시 30분 → 1시간 30분 뒤
        assertEquals(90, ChecklistReminders.delayToHour(LocalDateTime.of(2026, 10, 31, 7, 30), hour).toMinutes())
        // 이미 지난 10시 → 다음 날 아침 9시 (23시간)
        assertEquals(23 * 60, ChecklistReminders.delayToHour(LocalDateTime.of(2026, 10, 31, 10, 0), hour).toMinutes())
        // 정각이면 다음 날
        assertEquals(24 * 60, ChecklistReminders.delayToHour(LocalDateTime.of(2026, 10, 31, 9, 0), hour).toMinutes())
        assertEquals(ChecklistReminders.DEFAULT_HOUR, ChecklistReminders.hourOrDefault(13))
        assertTrue(ChecklistReminders.HOURS.all { ChecklistReminders.hourOrDefault(it) == it })
    }

    /** 알림에는 내가 넣은 항목(사람이 쓴 글)을 담지 않는다 — 개인정보가 알림에 새지 않게 */
    @Test
    fun customItemsNeverAppearInReminders() {
        val today = d("2026-10-31")
        val checks = TripChecks(custom = listOf(CustomItem("custom.1", "김OO 이모에게 전화하기")))
        val r = reminders(listOf(th), today, mapOf(th.id to checks)).single()
        assertNotNull(r)
        assertTrue(r.titles.none { it.contains("전화하기") })
        assertNull(data(th, today, checks).custom.firstOrNull { it.title in r.titles })
    }
}
