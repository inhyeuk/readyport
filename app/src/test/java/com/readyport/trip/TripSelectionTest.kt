package com.readyport.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 여러 여행 중 '지금 여행' 고르기 (2026-10-02) */
class TripSelectionTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun trip(id: String, c: String, s: String, e: String, wrapped: Boolean = false) =
        Trip(country = c, startDate = s, endDate = e, wrappedUp = wrapped, id = id)

    private val pastWrapped = trip("p1", "JP", "2026-08-01", "2026-08-04", wrapped = true)
    private val pastOpen = trip("p2", "SG", "2026-09-10", "2026-09-14")
    private val nov = trip("u1", "TH", "2026-11-03", "2026-11-07")
    private val feb = trip("u2", "TH", "2027-02-10", "2027-02-14")

    @Test
    fun ongoingWinsOverUpcomingAndPast() {
        val list = listOf(pastOpen, nov, feb)
        assertEquals("u1", TripSelection.active(list, d("2026-11-05"))?.id)
        assertEquals(TripTiming.Ongoing, TripSelection.timing(nov, d("2026-11-03")))
        assertEquals(TripTiming.Ongoing, TripSelection.timing(nov, d("2026-11-07")))
    }

    @Test
    fun nearestUpcomingBeforeAnyPastTrip() {
        // 정리 안 한 지난 여행이 있어도 다가오는 여행이 먼저
        assertEquals("u1", TripSelection.active(listOf(pastOpen, feb, nov), d("2026-10-02"))?.id)
        // 11월 여행이 끝나면 2월 여행
        assertEquals("u2", TripSelection.active(listOf(pastOpen, feb, nov), d("2026-11-20"))?.id)
    }

    @Test
    fun mostRecentUnfinishedPastTripWhenNothingAhead() {
        assertEquals("p2", TripSelection.active(listOf(pastWrapped, pastOpen), d("2026-10-02"))?.id)
        // 정리를 마친 여행은 돌아온 뒤 2주까지만 축하 카드
        assertEquals("p1", TripSelection.active(listOf(pastWrapped), d("2026-08-10"))?.id)
        assertNull(TripSelection.active(listOf(pastWrapped), d("2026-10-02")))
        assertNull(TripSelection.active(emptyList(), d("2026-10-02")))
    }

    @Test
    fun ongoingTripsPickTheOneEndingFirst() {
        val long = trip("l", "JP", "2026-11-01", "2026-11-20")
        assertEquals("u1", TripSelection.active(listOf(long, nov), d("2026-11-04"))?.id)
    }

    @Test
    fun brokenDatesAreIgnored() {
        val broken = Trip(country = "TH", startDate = "2026-13-40", endDate = "x", id = "b")
        val backwards = trip("r", "TH", "2026-11-07", "2026-11-03")
        assertEquals("u1", TripSelection.active(listOf(broken, backwards, nov), d("2026-10-02"))?.id)
        assertEquals(listOf("u1"), TripSelection.ordered(listOf(broken, backwards, nov), d("2026-10-02")).map { it.second.id })
    }

    @Test
    fun orderedGroupsOngoingUpcomingPast() {
        val order = TripSelection.ordered(listOf(pastWrapped, feb, pastOpen, nov), d("2026-11-04"))
        assertEquals(listOf("u1", "u2", "p2", "p1"), order.map { it.second.id })
        assertEquals(listOf(TripTiming.Ongoing, TripTiming.Upcoming, TripTiming.Past, TripTiming.Past), order.map { it.first })
    }

    @Test
    fun overlapIsDetectedButSameCountryOtherDatesIsNot() {
        val overlap = trip("o", "JP", "2026-11-06", "2026-11-09")
        assertEquals(setOf("u1", "o"), TripSelection.overlapping(listOf(nov, feb, overlap)))
        assertTrue(TripSelection.overlapping(listOf(nov, feb)).isEmpty())
    }

    @Test
    fun upcomingForSameCountry() {
        assertEquals("u1", TripSelection.upcomingFor(listOf(pastOpen, feb, nov), "TH", d("2026-10-02"))?.id)
        assertNull(TripSelection.upcomingFor(listOf(pastOpen, nov), "SG", d("2026-10-02")))
        assertNull(TripSelection.upcomingFor(listOf(nov), "JP", d("2026-10-02")))
    }

    @Test
    fun stageSemanticsStayPerTrip() {
        // 같은 나라 두 여행: 각 여행의 단계는 그 여행 날짜로만 정해진다
        val today = d("2026-11-02")
        assertEquals(TripStage.Preparing, TripStages.compute(nov, today, 0L, 3).stage)
        assertTrue(TripStages.compute(nov, today, 0L, 3).formWindowOpen)
        val far = TripStages.compute(feb, today, 0L, 3)
        assertEquals(TripStage.Preparing, far.stage)
        assertEquals(false, far.formWindowOpen)
    }
}
