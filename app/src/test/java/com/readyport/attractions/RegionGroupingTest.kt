package com.readyport.attractions

import com.readyport.attractions.AttTestData.place
import com.readyport.attractions.AttTestData.region
import com.readyport.pack.CountryPack
import com.readyport.pack.Names
import com.readyport.pack.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 지역 묶음·정렬·끌어올리기·경보 표시 (SPEC_v5 §3.1·§5.7·§12) */
class RegionGroupingTest {
    private val doc = AttTestData.doc(
        regions = listOf(
            region("xx_b", 20, airports = listOf("BBB"), hub = 36.0 to 136.0),
            region("xx_a", 10, airports = listOf("AAA"), hub = 35.0 to 135.0),
            region("xx_day_b", 30, kind = "daytrip", base = listOf("xx_a", "xx_b")),
            region("xx_c", 40, hub = 37.0 to 137.0),
        ),
        attractions = listOf(
            place("p-b1", "xx_b", "나 장소", rank = 2),
            place("p-b2", "xx_b", "가 장소", rank = 2),
            place("p-b3", "xx_b", "다 장소", rank = 1),
            place("p-a1", "xx_a", "에이 장소"),
            place("p-d1", "xx_day_b", "하루 장소"),
            place("p-c1", "xx_c", "씨 장소"),
        ),
        unmapped = listOf(UnmappedAirportDto("CCC", "upcoming", "준비 중인 지역")),
    )
    private val catalog = AttTestData.catalog(doc)

    private fun order(anchor: LiftAnchor? = null, places: List<Attraction> = catalog.attractions) =
        RegionGrouping.group(catalog, places, anchor).map { it.region.id }

    @Test fun stableOrderDaytripAfterAnchor() {
        assertEquals(listOf("xx_a", "xx_day_b", "xx_b", "xx_c"), order())
    }

    @Test fun insideRegionByRankThenName() {
        val g = RegionGrouping.group(catalog, catalog.attractions).first { it.region.id == "xx_b" }
        assertEquals(listOf("p-b3", "p-b2", "p-b1"), g.places.map { it.id })
    }

    @Test fun daytripKeepsPlaceWhenAnchorHidden() {
        val noA = catalog.attractions.filter { it.regionId != "xx_a" }
        assertEquals(listOf("xx_day_b", "xx_b", "xx_c"), order(places = noA))
    }

    @Test fun emptyRegionsAreNotDrawn() {
        assertFalse("xx_c" in order(places = catalog.attractions.filter { it.regionId != "xx_c" }))
    }

    @Test fun airportLiftMovesRegionAndItsDaytrip() {
        val anchor = RegionGrouping.liftAnchor(catalog, TripContext(arrivalAirport = "BBB"))!!
        assertEquals(LiftAnchor("xx_b", LiftReason.Airport), anchor)
        // 끌어올린 거점이 base_regions 에 있으면 daytrip 도 함께 올라간다
        assertEquals(listOf("xx_b", "xx_day_b", "xx_a", "xx_c"), order(anchor))
        assertEquals(LiftReason.Airport, RegionGrouping.group(catalog, catalog.attractions, anchor).first().lifted)
    }

    @Test fun stayLiftWithin40km() {
        val near = RegionGrouping.liftAnchor(catalog, TripContext(inProgress = true, stayLat = 37.1, stayLng = 137.0, arrivalAirport = "AAA"))
        assertEquals(LiftAnchor("xx_c", LiftReason.Stay), near)
        // 40km 밖이면 숙소로는 올리지 않고 도착 공항으로
        val far = RegionGrouping.liftAnchor(catalog, TripContext(inProgress = true, stayLat = 40.0, stayLng = 140.0, arrivalAirport = "AAA"))
        assertEquals(LiftAnchor("xx_a", LiftReason.Airport), far)
        // 여행 중이 아니면 숙소 좌표를 쓰지 않는다
        assertNull(RegionGrouping.liftAnchor(catalog, TripContext(inProgress = false, stayLat = 37.1, stayLng = 137.0)))
    }

    @Test fun compactCountryNeverLifts() {
        val compact = AttTestData.catalog(doc.copy(compactCountry = true))
        assertNull(RegionGrouping.liftAnchor(compact, TripContext(arrivalAirport = "BBB")))
        assertNull(RegionGrouping.upcomingArrivalAirport(compact, TripContext(arrivalAirport = "CCC")))
    }

    @Test fun upcomingArrivalAirport() {
        assertEquals("CCC", RegionGrouping.upcomingArrivalAirport(catalog, TripContext(arrivalAirport = "CCC")))
        assertNull(RegionGrouping.upcomingArrivalAirport(catalog, TripContext(arrivalAirport = "AAA")))
    }

    @Test fun sortKeys() {
        val regions = catalog.regions.associateBy { it.id }
        assertEquals(RegionGrouping.SortKey(10, 0, 10), RegionGrouping.sortKey(regions.getValue("xx_a"), regions, null))
        assertEquals(RegionGrouping.SortKey(10, 1, 30), RegionGrouping.sortKey(regions.getValue("xx_day_b"), regions, null))
        assertEquals(RegionGrouping.SortKey(-1, 1, 30), RegionGrouping.sortKey(regions.getValue("xx_day_b"), regions, "xx_b"))
    }

    @Test fun level2WholeVsMixed() {
        val d = AttTestData.doc(
            regions = listOf(region("xx_two", 1, level = "2"), region("xx_mix", 2, level = "2")),
            attractions = listOf(
                place("t1", "xx_two", "가", level = "2"), place("t2", "xx_two", "나", level = "2"),
                place("m1", "xx_mix", "다", level = "2"), place("m2", "xx_mix", "라", level = "1"),
            ),
        )
        val c = AttTestData.catalog(d)
        val groups = RegionGrouping.group(c, c.attractions).associateBy { it.region.id }
        assertTrue(groups.getValue("xx_two").wholeLevel2)
        assertFalse(groups.getValue("xx_mix").wholeLevel2)
    }

    // ---------------- 경보 버전 방향 (§5.7) ----------------

    private fun pack(version: String, safety: List<String>) = CountryPack(
        schemaVersion = 1, country = "XX", version = version, lastVerified = "2026-10-01", names = Names("가짜", "Fake", "Fake"),
        sources = emptyList(), sections = listOf(Section("safety", "안전", safety, "mofa_xx", "2026-10-05")),
    )

    private val baseSafety = listOf("가짜 나라에는 여행경보 1단계(여행유의)가 있어요.", "날치기를 조심하세요.")

    private fun withBasis(watch: List<String> = emptyList()): AttractionsCatalog {
        val hash = com.readyport.pack.Advisory.advisoryHash(pack("2026.10.01-1", baseSafety))!!
        val d = doc.copy(
            advisoryBasis = AdvisoryBasisDto("2026.10.01-1", "2026-10-01", hash),
            regions = doc.regions.map { if (it.id == "xx_c") it.copy(advisoryWatchKo = watch) else it },
        )
        return AttTestData.catalog(d)
    }

    @Test fun olderPackKeepsAttractionLevels() {
        assertEquals(AdvisoryState.Normal, AdvisoryState.evaluate(withBasis(), pack("2026.09.01-1", listOf("여행경보 2단계예요."))))
    }

    @Test fun samePackNothing() {
        assertEquals(AdvisoryState.Normal, AdvisoryState.evaluate(withBasis(), pack("2026.10.01-1", listOf("다른 문장 여행경보 2단계"))))
    }

    @Test fun newerPackSameAdvisoryParagraphsNoWarning() {
        val lifeChanged = listOf(baseSafety[0], "날치기와 소매치기를 조심하세요.")
        assertEquals(AdvisoryState.Normal, AdvisoryState.evaluate(withBasis(), pack("2026.10.05-1", lifeChanged)))
    }

    @Test fun newerPackChangedAdvisoryWarnsAndWatchHides() {
        val changed = listOf("가짜 나라에는 여행경보 1단계(여행유의)가 있어요.", "씨해안은 여행경보 3단계(출국권고)예요.")
        val s = AdvisoryState.evaluate(withBasis(watch = listOf("씨해안")), pack("2026.10.05-1", changed))
        assertTrue(s.changed)
        assertEquals(setOf("xx_c"), s.hiddenRegions)
        assertEquals("2026-10-05", s.packVerified)
        // watch 낱말이 1·2단계 문단에만 나오면 숨기지 않는다
        val mild = listOf("가짜 나라에는 여행경보 1단계(여행유의)가 있어요.", "씨해안은 여행경보 2단계(여행자제)예요.")
        assertTrue(AdvisoryState.evaluate(withBasis(watch = listOf("씨해안")), pack("2026.10.05-1", mild)).hiddenRegions.isEmpty())
    }

    @Test fun haversine() {
        assertEquals(111.2, RegionGrouping.haversineKm(0.0, 0.0, 1.0, 0.0), 0.2)
    }
}
