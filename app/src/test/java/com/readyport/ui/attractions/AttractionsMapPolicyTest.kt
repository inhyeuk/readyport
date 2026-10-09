package com.readyport.ui.attractions

import com.readyport.attractions.AttTestData
import com.readyport.attractions.GeoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 지도를 보일지(키 없음·중국·인터넷·Play 서비스·좌표)와 표시 계산 — 가짜 자료만 */
class AttractionsMapPolicyTest {
    private val ok = MapEnv(keyPresent = true, online = true, playServices = true)

    @Test fun inAppOnlyWhenEverythingIsThere() {
        assertEquals(MapMode.InApp, AttractionsMapPolicy.mode(ok, "JP", hasCoords = true))
    }

    @Test fun noKeyHidesMap() {
        // 사장님이 키를 넣기 전(빌드 속성 MAPS_API_KEY 비어 있음): 깨진 지도 대신 칸을 숨긴다 — 오프라인이어도 숨김
        assertEquals(MapMode.Hidden, AttractionsMapPolicy.mode(ok.copy(keyPresent = false), "JP", true))
        assertEquals(MapMode.Hidden, AttractionsMapPolicy.mode(MapEnv(false, false, false), "JP", true))
    }

    @Test fun chinaHidesMap() {
        assertEquals(MapMode.Hidden, AttractionsMapPolicy.mode(ok, "CN", true))
        assertEquals(MapMode.Hidden, AttractionsMapPolicy.mode(ok.copy(online = false), "CN", true))
        assertTrue(AttractionsMapPolicy.countrySupported("TW"))
        assertTrue(AttractionsMapPolicy.countrySupported("JP"))
    }

    @Test fun noPlayServicesOrNoCoordsHidesMap() {
        assertEquals(MapMode.Hidden, AttractionsMapPolicy.mode(ok.copy(playServices = false), "JP", true))
        assertEquals(MapMode.Hidden, AttractionsMapPolicy.mode(ok, "JP", hasCoords = false))
    }

    @Test fun offlineShowsFallbackCard() {
        assertEquals(MapMode.Offline, AttractionsMapPolicy.mode(ok.copy(online = false), "JP", true))
    }

    @Test fun coordsMustBeRealNumbers() {
        val base = AttTestData.basic()
        fun withGeo(geo: GeoDto?) = AttTestData.catalog(base.copy(attractions = listOf(base.attractions.first().copy(geo = geo)))).attractions.first()
        assertTrue(AttractionsMapPolicy.hasCoords(withGeo(GeoDto("site", 35.0, 135.0))))
        listOf(null, GeoDto("site", null, 135.0), GeoDto("site", 0.0, 0.0), GeoDto("site", 95.0, 135.0)).forEach {
            assertTrue("$it", !AttractionsMapPolicy.hasCoords(withGeo(it)))
        }
    }

    @Test fun listModeNeedsAtLeastOnePlaceWithCoords() {
        val c = AttTestData.catalog(AttTestData.basic())
        assertEquals(MapMode.InApp, AttractionsMapPolicy.listMode(ok, "XX", c.attractions))
        assertEquals(MapMode.Hidden, AttractionsMapPolicy.listMode(ok, "XX", emptyList()))
        assertEquals(MapMode.Hidden, AttractionsMapPolicy.listMode(ok, "CN", c.attractions))
    }

    @Test fun pinsGetRegionColoursAndNoNumbersWhenNotSavedOnly() {
        val c = AttTestData.catalog(AttTestData.basic())
        val pins = AttractionsMapPolicy.pins(c.attractions, c.regions, { c.region(it)!!.nameKo }, { "종류" })
        assertEquals(c.attractions.map { it.id }, pins.map { it.id })
        assertTrue(pins.all { it.number == null })
        // 지역 order 순서대로 색: xx_one(10) → 0번 색, xx_two(20) → 1번 색, xx_day(30) → 2번 색
        assertEquals(PIN_HUES[0], pins.first { it.regionId == "xx_one" }.hue)
        assertEquals(PIN_HUES[1], pins.first { it.regionId == "xx_two" }.hue)
        assertEquals(PIN_HUES[2], pins.first { it.regionId == "xx_day" }.hue)
        assertEquals("가나시 · 종류", pins.first { it.id == "fake-temple" }.subtitle)
        assertEquals(listOf("가나시", "마바시", "사아섬"), AttractionsMapPolicy.legend(pins, c.regions).map { it.name })
    }

    @Test fun savedOnlyPinsFollowSavedListOrder() {
        val c = AttTestData.catalog(AttTestData.basic())
        // 저장소가 준 찜 순서(다른 나라·사라진 항목이 섞여 있다)
        val savedKeys = linkedSetOf("XX/fake-hill", "JP/elsewhere", "XX/gone", "XX/fake-temple", "XX/fake-isle")
        val order = AttractionsMapPolicy.savedOrder(savedKeys, "XX")
        assertEquals(listOf("XX/fake-hill", "XX/gone", "XX/fake-temple", "XX/fake-isle"), order)
        val shown = c.attractions.filter { it.key in savedKeys }
        val pins = AttractionsMapPolicy.pins(shown, c.regions, { "" }, { "" }, savedOrder = order)
        // 번호 = 이 나라 찜 목록의 자리(사라진 'gone'이 2번 자리를 차지 — 목록과 같은 번호), 표시는 번호 순
        assertEquals(listOf("fake-hill" to 1, "fake-temple" to 3, "fake-isle" to 4), pins.map { it.id to it.number })
    }

    @Test fun directionsUrlIsOfficialMapsUrlAndNeverForChina() {
        val c = AttTestData.catalog(AttTestData.basic())
        val a = c.attractions.first()
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=35.01,135.0", AttractionsMapPolicy.directionsUrl(a))
        assertNull(AttractionsMapPolicy.directionsUrl(a.copy(country = "CN")))
    }

    @Test fun detailPinNeedsCoords() {
        val c = AttTestData.catalog(AttTestData.basic())
        val a = c.attractions.first()
        assertEquals("샘플 사원", AttractionsMapPolicy.detailPin(a, "가나시", "문화유산")!!.title)
        assertNull(AttractionsMapPolicy.detailPin(a.copy(lat = null), "가나시", "문화유산"))
    }
}
