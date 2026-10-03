package com.readyport.autofill

import com.readyport.ui.TestPacks
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.StayRecord
import com.readyport.vault.VaultContents
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 묵는 곳 → 입국 카드 자동 입력 (2026-10-03). 서명된 레시피를 운영 공개키로 읽어 쓴다.
 * 규칙: 주소·이름은 글자 그대로, 숙소 종류는 **그 나라 사이트 선택지에 있을 때만**.
 * 주·구·동·우편번호는 사이트 목록에서 고르는 칸이라 앱이 채우지 않는다(채웠다고 말하지 않는다).
 */
class StayAutofillTest {

    private fun recipe(id: String) = runBlocking { TestPacks.repo.recipe(id) }!!.value

    // ICAO 9303 표본 이름(가상 인물)
    private val passport = PassportRecord(
        surname = "ERIKSSON", givenNames = "ANNA MARIA", documentNumber = "L898902C3",
        nationality = "KOR", issuingState = "KOR", birthDate = "1974-08-12", sex = "F",
        expiryDate = "2036-04-15", source = "mrz", mrzVerified = true, savedAt = "x",
    )
    private val flight = BookingRecord(
        id = "f1", kind = "flight", title = "가는 편", flightNumbers = listOf("KE651"),
        dates = listOf("2026-11-03"), savedAt = "x",
    )

    private fun stay(id: String, name: String, address: String, checkIn: String?, checkOut: String?, type: String?) = StayRecord(
        id = id, tripId = "t1", name = name, addressLocal = address, checkIn = checkIn, checkOut = checkOut,
        type = type, savedAt = "x",
    )

    /** 11월 3일~5일 방콕 호텔, 5일~7일 아유타야 게스트하우스 — 도착한 날은 방콕 호텔이다 */
    private val bangkok = stay("a", "리버뷰 방콕 호텔", "123 Soi Sukhumvit 11, Bangkok 10110", "2026-11-03", "2026-11-05", "hotel")
    private val ayutthaya = stay("b", "아유타야 리버 게스트하우스", "45 Naresuan Road, Ayutthaya 13000", "2026-11-05", "2026-11-07", "guest_house")
    private val vault = VaultContents(passport = passport, bookings = listOf(flight), stays = listOf(ayutthaya, bangkok))

    @Test
    fun thailandFillsAddressAndType() {
        val r = recipe("TH_TDAC")
        val v = FormValues.build(r, vault, emptyMap())
        // 도착한 날(11월 3일) 묵는 곳 — 둘째 숙소가 아니라 방콕 호텔
        assertEquals("123 SOI SUKHUMVIT 11, BANGKOK 10110", v.getValue("stay.address").value)
        assertEquals(ValueOrigin.Lodging, v.getValue("stay.address").origin)
        // 숙소 종류는 TDAC 선택지 글자까지 (assist 칸이라 `호텔 → HOTEL`)
        assertEquals("호텔 → HOTEL", v.getValue("stay.type").value)
        assertEquals(ValueOrigin.Lodging, v.getValue("stay.type").origin)
        // 주·구·동·우편번호는 사이트 목록에서 고른다 — 비어 있다고 그대로 말한다
        listOf("stay.province", "stay.district", "stay.sub_district", "stay.post_code").forEach { key ->
            assertTrue(key, v.getValue(key).isEmpty)
            assertEquals(key, ValueOrigin.None, v.getValue(key).origin)
        }
        // 그래서 필수인 '숙소가 있는 주'는 여전히 빈칸으로 남는다(채웠다고 하지 않는다)
        val missing = FormValues.missingRequired(r, v).map { it.key }
        assertTrue("stay.province" in missing)
        assertFalse("stay.address" in missing)
        assertFalse("stay.type" in missing)
    }

    @Test
    fun malaysiaFillsSelectOptionExactly() {
        val r = recipe("MY_MDAC")
        val v = FormValues.build(r, vault, emptyMap())
        assertEquals("123 SOI SUKHUMVIT 11, BANGKOK 10110", v.getValue("stay.address").value)
        // MDAC 숙소 종류는 기본 select — 사이트 글자와 **정확히 같아야** 엔진이 고를 수 있다
        assertEquals("HOTEL/MOTEL/REST HOUSE", v.getValue("stay.type").value)
        // 주·도시·우편번호는 사이트에서 고른다
        listOf("stay.state", "stay.city", "stay.post_code").forEach { assertTrue(it, v.getValue(it).isEmpty) }
    }

    @Test
    fun typesTheSiteDoesNotOfferStayEmpty() {
        // 게스트하우스는 MDAC 선택지에 없다 → 비워 두고 사람이 사이트에서 고른다(없는 선택지를 만들지 않는다)
        val only = vault.copy(stays = listOf(ayutthaya), bookings = emptyList())
        val my = FormValues.build(recipe("MY_MDAC"), only, emptyMap())
        assertTrue(my.getValue("stay.type").isEmpty)
        assertEquals(emptyMap<String, String>(), FormValues.suggest(recipe("MY_MDAC"), only))
        // TDAC에는 있다
        val th = FormValues.build(recipe("TH_TDAC"), only, emptyMap())
        assertEquals("게스트하우스 → GUEST HOUSE", th.getValue("stay.type").value)
        assertEquals(mapOf("stay.type" to "guest_house"), FormValues.suggest(recipe("TH_TDAC"), only))
    }

    @Test
    fun singaporeAndIndonesiaFillHotelName() {
        val sg = FormValues.build(recipe("SG_SGAC"), vault, emptyMap())
        assertEquals("리버뷰 방콕 호텔", sg.getValue("stay.hotel").display)
        assertEquals("123 SOI SUKHUMVIT 11, BANGKOK 10110", sg.getValue("stay.address").value)
        assertTrue(sg.getValue("stay.post_code").isEmpty)
        val id = FormValues.build(recipe("ID_ALL_INDONESIA"), vault, emptyMap())
        assertEquals("리버뷰 방콕 호텔", id.getValue("stay.hotel").display)
        assertEquals("123 SOI SUKHUMVIT 11, BANGKOK 10110", id.getValue("stay.address").value)
        // 대만은 '호텔 이름 또는 주소' 한 칸 — 주소를 넣는다
        val tw = FormValues.build(recipe("TW_TWAC"), vault, emptyMap())
        assertEquals("123 Soi Sukhumvit 11, Bangkok 10110", tw.getValue("stay.address").value)
    }

    @Test
    fun userEditWinsOverTheSavedStay() {
        val v = FormValues.build(recipe("TH_TDAC"), vault, mapOf("stay.address" to "999 new road", "stay.type" to "friend"))
        assertEquals("999 NEW ROAD", v.getValue("stay.address").value)
        assertEquals(ValueOrigin.User, v.getValue("stay.address").origin)
        assertEquals("친구 집 → FRIEND'S HOUSE", v.getValue("stay.type").value)
        assertEquals(ValueOrigin.User, v.getValue("stay.type").origin)
    }

    @Test
    fun datesFallBackToStaysWhenThereIsNoFlight() {
        val noFlight = vault.copy(bookings = emptyList())
        val v = FormValues.build(recipe("TH_TDAC"), noFlight, emptyMap())
        assertEquals("2026-11-03", v.getValue("trip.arrival_date").display)
        assertEquals(ValueOrigin.Lodging, v.getValue("trip.arrival_date").origin)
        // 떠나는 날은 마지막 퇴실일
        assertEquals("2026-11-07", v.getValue("trip.departure_date").display)
        // 숙소도 항공권도 없으면 비어 있다
        val empty = FormValues.build(recipe("TH_TDAC"), VaultContents(passport = passport), emptyMap())
        assertTrue(empty.getValue("stay.address").isEmpty)
        assertTrue(empty.getValue("trip.arrival_date").isEmpty)
        assertNull(empty.getValue("stay.type").value)
    }
}
