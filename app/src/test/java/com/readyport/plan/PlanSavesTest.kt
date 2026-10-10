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
}
