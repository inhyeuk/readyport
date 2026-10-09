package com.readyport.ui.itinerary

import com.readyport.attractions.AdvisoryState
import com.readyport.attractions.AttTestData
import com.readyport.attractions.AttractionsCatalog
import com.readyport.attractions.SavedAttraction
import com.readyport.itinerary.Itinerary
import com.readyport.itinerary.ItineraryStop
import com.readyport.itinerary.TripItinerary
import com.readyport.trip.Trip
import com.readyport.ui.attractions.SavedGone
import com.readyport.ui.attractions.SavedOrderModel
import com.readyport.vault.StayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 찜 → 관광 일정 화면 값 (debug 샘플 일본 5곳: 도쿄 센소지 · 교토 후시미 이나리 · 오사카 도톤보리·오사카성 · 나라 공원).
 * 숙소는 가짜 이름·좌표(도시 중심 근처 아무 점)다.
 */
class ItineraryModelTest {
    private val catalog: AttractionsCatalog = AttTestData.catalog(AttTestData.debugSample())
    private val trip = Trip("JP", "2026-10-08", "2026-10-12", id = "jp1")
    private val before = LocalDate.of(2026, 10, 1)

    private fun saved(vararg ids: String) = ids.map { SavedAttraction("JP/$it", "2026-10-01") }

    private val mySaves = saved("sensoji", "dotonbori", "fushimi-inari-taisha", "osaka-castle", "vanished-place")

    private fun build(
        plan: TripItinerary = TripItinerary(),
        saves: List<SavedAttraction> = mySaves,
        today: LocalDate = before,
        stays: List<StayRecord> = emptyList(),
        proposing: Boolean = true,
    ) = ItineraryModel.build(trip, plan, catalog, AdvisoryState.Normal, saves, today, stays, proposing, "일본")

    private fun dayOf(stops: List<ItineraryStop>) = stops.associate { it.key.substringAfter('/') to it.day }

    @Test fun firstTransplantGroupsRegionsInSavedOrder() {
        val ui = build()
        val p = ui.proposal!!
        assertEquals(5, ui.days.size) // 4박 5일
        assertEquals(4, ui.fresh)
        assertEquals(1, p.skipped) // 정보가 없는 찜은 담지 않는다
        assertFalse(p.usedStays)
        // 도착일(0)·귀국일(4)은 비우고, 찜 순서에서 처음 나온 지역 순서대로 하루씩, 같은 지역(오사카 둘)은 같은 날
        assertEquals(mapOf("sensoji" to 1, "dotonbori" to 2, "osaka-castle" to 2, "fushimi-inari-taisha" to 3), dayOf(p.stops))
        assertEquals(listOf("JP/dotonbori", "JP/osaka-castle"), p.stops.filter { it.day == 2 }.map { it.key })
        // 미리보기는 곳이 있는 날만, 새 곳 표시
        assertEquals(listOf(1, 2, 3), p.days.map { it.slot.index })
        assertTrue(p.days.flatMap { it.stops }.all { it.isNew })
    }

    @Test fun stayAwareProposalUsesNightsNearEachRegion() {
        // 8·9일 밤 오사카, 10·11일 밤 도쿄(12일 아침 퇴실)
        val stays = listOf(
            StayRecord(id = "s1", tripId = "jp1", name = "가짜 오사카 숙소", checkIn = "2026-10-08", checkOut = "2026-10-10", lat = 34.70, lng = 135.50, savedAt = "2026-10-01"),
            StayRecord(id = "s2", tripId = "jp1", name = "가짜 도쿄 숙소", checkIn = "2026-10-10", checkOut = "2026-10-12", lat = 35.69, lng = 139.70, savedAt = "2026-10-01"),
        )
        val p = build(stays = stays).proposal!!
        assertTrue(p.usedStays)
        val days = dayOf(p.stops)
        // 도착일(8일)·귀국일(12일)은 비운다 → 오사카 숙소 밤 가운데 열린 날은 9일(1)뿐, 도쿄는 10·11일(2·3)
        assertEquals(2, days["sensoji"]) // 도쿄 숙소 첫날
        assertEquals(1, days["dotonbori"])
        assertEquals(1, days["osaka-castle"])
        assertEquals(1, days["fushimi-inari-taisha"]) // 오사카 숙소 근처(같은 날)
        // 저장되는 값에는 좌표가 없다(키·날만)
        assertTrue(p.stops.all { it.key.startsWith("JP/") })
    }

    @Test fun confirmedPlanThenReTransplantAddsOnlyNew() {
        val first = build().proposal!!
        // 사람이 센소지를 마지막 날로 옮겼다
        val plan = Itinerary.moveToDay(Itinerary.add(TripItinerary(), first.stops), "JP/sensoji", 4)
        val again = build(plan = plan)
        assertNull(again.proposal)
        assertTrue(again.allIn)
        assertEquals(0, again.fresh)
        assertEquals(4, again.total)
        // 새로 나라 공원을 찜 → 그것만 제안, 사람이 고친 센소지 날은 그대로
        val more = build(plan = plan, saves = mySaves + saved("nara-park"))
        val p = more.proposal!!
        assertEquals(listOf("JP/nara-park"), p.stops.map { it.key })
        val merged = Itinerary.add(plan, p.stops)
        assertEquals(4, merged.stops.first { it.key == "JP/sensoji" }.day)
        assertEquals(5, merged.stops.size)
        assertNull(build(plan = merged, saves = mySaves + saved("nara-park")).proposal)
    }

    @Test fun todayAndMixedRegionsAndMissing() {
        val plan = TripItinerary(
            listOf(
                ItineraryStop("JP/sensoji", 2),
                ItineraryStop("JP/dotonbori", 2),
                ItineraryStop("JP/vanished-place", 3),
                ItineraryStop("JP/osaka-castle", 1),
            ),
        )
        val ui = build(plan = plan, today = LocalDate.of(2026, 10, 10), proposing = false)
        assertEquals(2, ui.todayIndex)
        val today = assertNotNullAndGet(ui.today)
        assertEquals(listOf("센소지", "도톤보리"), today.stops.map { it.name })
        assertTrue(today.today)
        assertTrue(today.mixedFar) // 도쿄와 오사카가 한 날
        assertFalse(ui.days[1].mixedFar)
        val gone = ui.days[3].stops.single()
        assertNull(gone.attraction)
        assertEquals(SavedGone.Missing, gone.gone)
        // 여행 전·후에는 오늘 칸이 없다
        assertNull(build(plan = plan, proposing = false).today)
    }

    @Test fun savedOrderRowsFollowSavedOrderWithRegion() {
        val rows = SavedOrderModel.rows(catalog, saved("osaka-castle", "sensoji", "vanished-place") + SavedAttraction("TH/x", "2026-10-01"), "JP", null, AdvisoryState.Normal)
        assertEquals(listOf("JP/osaka-castle", "JP/sensoji", "JP/vanished-place"), rows.map { it.key })
        assertEquals("오사카", rows.first().regionName)
        assertNotNull(rows[2].gone)
    }

    private fun <T> assertNotNullAndGet(v: T?): T {
        assertNotNull(v)
        return v!!
    }
}
