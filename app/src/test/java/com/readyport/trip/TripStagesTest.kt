package com.readyport.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** M6: 여행 단계 엔진 (PRD 4.2). 태국 TDAC는 도착일 포함 3일 전부터 */
class TripStagesTest {

    private val trip = Trip(country = "TH", startDate = "2026-11-03", endDate = "2026-11-07")
    private val now = 1_800_000_000_000L
    private fun d(s: String) = LocalDate.parse(s)
    private fun at(day: String, t: Trip = trip, submitted: Boolean = false, nowMs: Long = now) =
        TripStages.compute(t, d(day), nowMs, formWindowDays = 3, formSubmitted = submitted)

    @Test
    fun noTrip() = assertEquals(TripStage.NoTrip, TripStages.compute(null, d("2026-10-01"), now).stage)

    @Test
    fun preparingCountsDownAndOpensFormWindow() {
        val far = at("2026-10-20")
        assertEquals(TripStage.Preparing, far.stage)
        assertEquals(14L, far.daysLeft)
        assertFalse(far.formWindowOpen)
        // 11-03 도착이면 11-01부터 낼 수 있다 (도착일 포함 3일)
        assertFalse(at("2026-10-31").formWindowOpen)
        assertTrue(at("2026-11-01").formWindowOpen)
        assertTrue(at("2026-11-02").formWindowOpen)
        // 이미 냈으면 닫는다
        assertFalse(at("2026-11-02", submitted = true).formWindowOpen)
    }

    @Test
    fun departureDayUntilArrival() {
        val dep = at("2026-11-03")
        assertEquals(TripStage.Departure, dep.stage)
        assertEquals(1, dep.stage.barIndex)
        assertTrue(dep.formWindowOpen) // 출발 당일도 아직 낼 수 있다
    }

    @Test
    fun arrivalModeForSixHoursThenTraveling() {
        val arrived = trip.copy(arrivedAt = now - 60 * 60_000)
        assertEquals(TripStage.Arrival, at("2026-11-03", arrived).stage)
        val later = at("2026-11-03", arrived, nowMs = now + 6 * 3_600_000)
        assertEquals(TripStage.Traveling, later.stage)
        assertEquals(TripStage.Traveling, at("2026-11-03", arrived.copy(arrivalDismissed = true)).stage)
        // 2일째부터는 도착을 몰라도 여행 중 (일본처럼 시간대가 같은 나라)
        val day2 = at("2026-11-04")
        assertEquals(TripStage.Traveling, day2.stage)
        assertEquals(2L, day2.dayOfTrip)
    }

    @Test
    fun returnAsksToDestroyUnlessPostponedThenWrapUp() {
        val back = at("2026-11-08")
        assertEquals(TripStage.Return, back.stage)
        assertTrue(back.askDestroy)
        assertFalse(at("2026-11-08", trip.copy(destroyPostponedUntil = "2026-11-15")).askDestroy)
        assertTrue(at("2026-11-16", trip.copy(destroyPostponedUntil = "2026-11-15")).askDestroy)
        assertEquals(TripStage.WrapUp, at("2026-11-08", trip.copy(wrappedUp = true)).stage)
    }

    @Test
    fun timezoneArrivalDetection() {
        // 시간대가 바뀌면 도착 (여행 기간 앞뒤)
        assertTrue(TripStages.isArrival(trip, d("2026-11-03"), "Asia/Bangkok"))
        assertTrue(TripStages.isArrival(trip, d("2026-11-02"), "Asia/Bangkok")) // 전날 밤 출발
        // 한국 시간대로 바뀐 것, 여행 기간 밖, 이미 도착한 경우는 아니다
        assertFalse(TripStages.isArrival(trip, d("2026-11-03"), "Asia/Seoul"))
        assertFalse(TripStages.isArrival(trip, d("2026-10-20"), "Asia/Bangkok"))
        assertFalse(TripStages.isArrival(trip, d("2026-11-09"), "Asia/Bangkok"))
        assertFalse(TripStages.isArrival(trip.copy(arrivedAt = now), d("2026-11-03"), "Asia/Bangkok"))
    }
}
