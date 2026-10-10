package com.readyport.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanSavesTest {
    private fun item(place: String?) = PlanItem(TimeHint.Morning, place, "할 일", "")

    private fun result(vararg days: PlanDay) = PlanResult("req1", "JP", days.toList(), emptyList(), emptyList(), emptyList(), null, null)

    @Test
    fun followsDayThenItemOrderAndDropsDuplicatesUnknownAndNoPlace() {
        val r = result(
            PlanDay(2, "둘째 날", listOf(item("c"), item(null), item("a"))),
            PlanDay(1, "첫째 날", listOf(item("a"), item("b"), item("gone"))),
        )
        // 하루 번호 순서(1일 → 2일), 같은 곳은 처음 나온 자리, 앱 파일에 없는 곳(gone)·번호 없는 항목은 뺀다
        assertEquals(listOf("a", "b", "c"), PlanSaves.orderedPlaceIds(r, setOf("a", "b", "c")))
    }

    @Test
    fun emptyWhenNothingKnown() {
        val r = result(PlanDay(1, "하루", listOf(item("x"), item(null))))
        assertEquals(emptyList<String>(), PlanSaves.orderedPlaceIds(r, emptySet()))
    }

    @Test
    fun stopsMapPlanDayToTripDayInOrderAndDropDuplicates() {
        val r = result(
            PlanDay(2, "둘째 날", listOf(item("c"), item("a"), item(null))),
            PlanDay(1, "첫째 날", listOf(item("a"), item("b"), item("gone"))),
        )
        val stops = PlanSaves.stops(r, setOf("a", "b", "c"), dayCount = 2)
        // 1일차 = 0, 2일차 = 1. a 는 처음 나온 1일차에만, 하루 안 순서는 계획 그대로
        assertEquals(
            listOf(
                com.readyport.itinerary.ItineraryStop("JP/a", 0),
                com.readyport.itinerary.ItineraryStop("JP/b", 0),
                com.readyport.itinerary.ItineraryStop("JP/c", 1),
            ),
            stops,
        )
    }

    @Test
    fun stopsClampDayToTripLength() {
        val r = result(PlanDay(5, "다섯째 날", listOf(item("a"))))
        assertEquals(listOf(com.readyport.itinerary.ItineraryStop("JP/a", 1)), PlanSaves.stops(r, setOf("a"), dayCount = 2))
    }
}
