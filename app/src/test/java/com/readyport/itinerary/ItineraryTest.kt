package com.readyport.itinerary

import com.readyport.trip.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 관광 일정의 하루 칸·고치기·옮겨 담기 제안 (2026-10-09 '날짜별로 나눠 담기'). 좌표는 가짜 */
class ItineraryTest {

    private val trip = Trip("XX", "2026-10-08", "2026-10-11", id = "t1")

    // ---------------- 하루 칸 ----------------

    @Test fun slotsFromDates() {
        val slots = ItineraryDays.slots(trip)
        assertEquals(4, slots.size) // 3박 4일
        assertEquals(LocalDate.of(2026, 10, 8), slots.first().date)
        assertEquals(LocalDate.of(2026, 10, 11), slots.last().date)
        assertEquals(listOf(0, 1, 2, 3), slots.map { it.index })
        // 당일치기 = 한 칸
        assertEquals(1, ItineraryDays.slots(Trip("XX", "2026-10-08", "2026-10-08", id = "d")).size)
    }

    @Test fun noDatesGivesOneUndatedSlot() {
        val broken = Trip("XX", "언젠가", "", id = "t2")
        val slots = ItineraryDays.slots(broken)
        assertEquals(1, slots.size)
        assertNull(slots.single().date)
        assertNull(ItineraryDays.todayIndex(broken, LocalDate.of(2026, 10, 9)))
        // 끝이 시작보다 앞서도 날짜 미정
        assertNull(ItineraryDays.slots(Trip("XX", "2026-10-11", "2026-10-08", id = "t3")).single().date)
    }

    @Test fun longTripIsCapped() {
        assertEquals(30, ItineraryDays.slots(Trip("XX", "2026-10-01", "2026-10-30", id = "m")).size)
        val year = Trip("XX", "2026-01-01", "2026-12-31", id = "y")
        assertEquals(ItineraryDays.MAX_DAYS, ItineraryDays.slots(year).size)
        assertEquals(ItineraryDays.MAX_DAYS - 1, ItineraryDays.todayIndex(year, LocalDate.of(2026, 11, 1)))
    }

    @Test fun todaySelection() {
        assertNull(ItineraryDays.todayIndex(trip, LocalDate.of(2026, 10, 7)))
        assertEquals(0, ItineraryDays.todayIndex(trip, LocalDate.of(2026, 10, 8)))
        assertEquals(1, ItineraryDays.todayIndex(trip, LocalDate.of(2026, 10, 9)))
        assertEquals(3, ItineraryDays.todayIndex(trip, LocalDate.of(2026, 10, 11)))
        assertNull(ItineraryDays.todayIndex(trip, LocalDate.of(2026, 10, 12)))
    }

    // ---------------- 고치기 ----------------

    private fun plan(vararg s: Pair<String, Int>) = TripItinerary(s.map { ItineraryStop(it.first, it.second) })

    @Test fun byDayClampsDaysBeyondTheTrip() {
        val days = Itinerary.byDay(plan("XX/a" to 0, "XX/b" to 9, "XX/c" to 1), 3)
        assertEquals(listOf("XX/a"), days[0].map { it.key })
        assertEquals(listOf("XX/c"), days[1].map { it.key })
        assertEquals(listOf("XX/b"), days[2].map { it.key }) // 줄인 여행 — 마지막 날로, 지우지 않는다
    }

    @Test fun addSkipsExistingKeys() {
        val p = plan("XX/a" to 0)
        val next = Itinerary.add(p, listOf(ItineraryStop("XX/a", 2), ItineraryStop("XX/b", 1), ItineraryStop("XX/b", 2)))
        assertEquals(listOf(ItineraryStop("XX/a", 0), ItineraryStop("XX/b", 1)), next.stops)
        assertEquals(next, Itinerary.add(next, listOf(ItineraryStop("XX/a", 3))))
    }

    @Test fun shiftWithinDayOnly() {
        val p = plan("XX/a" to 0, "XX/x" to 1, "XX/b" to 0, "XX/c" to 0)
        val down = Itinerary.shift(p, "XX/a", 1, 4)
        assertEquals(listOf("XX/b", "XX/a", "XX/c"), Itinerary.byDay(down, 4)[0].map { it.key })
        val up = Itinerary.shift(p, "XX/c", -1, 4)
        assertEquals(listOf("XX/a", "XX/c", "XX/b"), Itinerary.byDay(up, 4)[0].map { it.key })
        assertEquals(p, Itinerary.shift(p, "XX/a", -1, 4)) // 맨 위
        assertEquals(p, Itinerary.shift(p, "XX/x", 1, 4)) // 그 날 하나뿐
        assertEquals(listOf("XX/x"), Itinerary.byDay(down, 4)[1].map { it.key }) // 다른 날은 그대로
    }

    @Test fun moveToDayAppendsAndRemove() {
        val p = plan("XX/a" to 0, "XX/b" to 1, "XX/c" to 1)
        val moved = Itinerary.moveToDay(p, "XX/a", 1)
        assertEquals(listOf("XX/b", "XX/c", "XX/a"), Itinerary.byDay(moved, 2)[1].map { it.key })
        assertTrue(Itinerary.byDay(moved, 2)[0].isEmpty())
        assertEquals(listOf("XX/b", "XX/c"), Itinerary.remove(moved, "XX/a").stops.map { it.key })
    }

    // ---------------- 옮겨 담기 제안 ----------------

    // 가짜 지역 중심: 가나(35,135) · 마바(35,136.5, 약 136km 동쪽) · 사아(35.05,135.05, 가나 옆)
    private fun ga(key: String) = Spot(key, "ga", 35.0, 135.0)
    private fun ma(key: String) = Spot(key, "ma", 35.0, 136.5)
    private fun sa(key: String) = Spot(key, "sa", 35.05, 135.05)

    @Test fun sameRegionSameDayInSavedOrder() {
        val r = ItineraryPlanner.propose(listOf(ga("a"), ma("m1"), ga("b"), ma("m2")), dayCount = 4)
        // 4일 여행: 도착일(0)·귀국일(3)은 비워 두고 1·2일차에만 담는다
        assertEquals(listOf(ItineraryStop("a", 1), ItineraryStop("b", 1), ItineraryStop("m1", 2), ItineraryStop("m2", 2)), r.stops)
        assertFalse(r.usedStays)
    }

    @Test fun moreRegionsThanDaysShareTheNearestDay() {
        // 이틀에 세 지역: 가나·마바가 하루씩, 사아는 가까운 가나 날에 함께
        val r = ItineraryPlanner.propose(listOf(ga("a"), ma("m"), sa("s")), dayCount = 2)
        assertEquals(mapOf("a" to 0, "m" to 1, "s" to 0), r.stops.associate { it.key to it.day })
        // 한 칸뿐이면 모두 그 날
        assertTrue(ItineraryPlanner.propose(listOf(ga("a"), ma("m")), dayCount = 1).stops.all { it.day == 0 })
    }

    @Test fun stayAwarePutsRegionOnNightsNearItsHub() {
        // 1~2일 밤은 마바 숙소, 3~4일 밤은 가나 숙소 → 찜 순서는 가나가 먼저지만 가나는 3일차로
        val nights = listOf(NightStay(35.0, 136.49), NightStay(35.0, 136.49), NightStay(35.0, 135.01), NightStay(35.0, 135.01))
        val r = ItineraryPlanner.propose(listOf(ga("a"), ma("m"), ga("b")), dayCount = 4, nights = nights)
        assertTrue(r.usedStays)
        assertEquals(mapOf("a" to 2, "b" to 2, "m" to 1), r.stops.associate { it.key to it.day })
        // 가나 숙소 밤 가운데 열린 날은 2일차뿐(3일차=귀국일) → 가까운 사아도 같은 날
        val r2 = ItineraryPlanner.propose(listOf(ga("a"), sa("s")), dayCount = 4, nights = nights)
        assertEquals(mapOf("a" to 2, "s" to 2), r2.stops.associate { it.key to it.day })
    }

    @Test fun farFromEveryStayGoesToADayWithoutStay() {
        // 숙소는 가나뿐(1·2일), 3·4일은 숙소 모름 → 먼 마바는 열린 날 가운데 숙소 모르는 3일차(다녀오는 날)
        val nights = listOf(NightStay(35.0, 135.0), NightStay(35.0, 135.0), null, null)
        val r = ItineraryPlanner.propose(listOf(ma("m")), dayCount = 4, nights = nights)
        assertEquals(2, r.stops.single().day)
    }

    @Test fun reTransplantAddsOnlyNewAndJoinsExistingRegionDay() {
        // 사람이 가나를 3일차로 옮겨 둔 일정
        val existing = listOf(ItineraryStop("a", 2) to ga("a"), ItineraryStop("m", 0) to ma("m"))
        val r = ItineraryPlanner.propose(listOf(ga("a"), ga("b"), ma("m"), sa("s")), dayCount = 4, existing = existing)
        // a·m은 다시 넣지 않고, 새 가나 b는 가나가 있는 3일차, 새 지역 사아는 빈 날 가운데 앞날
        assertEquals(listOf(ItineraryStop("b", 2), ItineraryStop("s", 1)), r.stops)
        val merged = Itinerary.add(TripItinerary(existing.map { it.first }), r.stops)
        assertEquals(listOf("a", "m", "b", "s"), merged.stops.map { it.key })
        // 한 번 더 해도 바뀌지 않는다
        val again = ItineraryPlanner.propose(listOf(ga("a"), ga("b"), ma("m"), sa("s")), 4, existing = merged.stops.map { it to null })
        assertTrue(again.stops.isEmpty())
    }

    @Test fun arrivalAndDepartureDaysStayEmpty() {
        // 사장님 결정(2026-10-09): 3일 이상이면 첫날·마지막 날에는 제안하지 않는다. 지역이 많아도 가운데 날에 모은다
        val r = ItineraryPlanner.propose(listOf(ga("a"), ma("m"), sa("s")), dayCount = 3)
        assertTrue(r.stops.all { it.day == 1 })
        assertEquals(listOf(1, 2, 3), ItineraryPlanner.openDays(5))
        assertEquals(listOf(0, 1), ItineraryPlanner.openDays(2))
        assertEquals(listOf(0), ItineraryPlanner.openDays(1))
    }

    @Test fun mixedFarOnlyWhenRegionsAreFarApart() {
        assertFalse(ItineraryPlanner.mixedFar(listOf(ga("a"), ga("b"))))
        assertFalse(ItineraryPlanner.mixedFar(listOf(ga("a"), sa("s")))) // 가까운 두 지역
        assertTrue(ItineraryPlanner.mixedFar(listOf(ga("a"), ma("m"))))
        assertTrue(ItineraryPlanner.mixedFar(listOf(ga("a"), Spot("u", "unknown")))) // 모르면 조심하는 쪽
    }
}
