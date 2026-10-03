package com.readyport.stay

import com.readyport.transport.Place
import com.readyport.transport.RideLinker
import com.readyport.transport.syncStayPlace
import com.readyport.trip.Trip
import com.readyport.vault.BookingRecord
import com.readyport.vault.StayRecord
import com.readyport.vault.VaultContents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 묵는 곳 순수 함수 (2026-10-03): 날짜별 여러 숙소·부드러운 알림·구글 지도 주소·가는 곳 맞추기·예전 저장본 옮기기.
 * 가짜 숙소 이름·주소만 쓴다(실제 사람·실제 예약 정보 없음).
 */
class StaysTest {

    private val trip = Trip("TH", "2026-11-03", "2026-11-07", id = "t1")

    private fun stay(
        id: String,
        name: String,
        checkIn: String? = null,
        checkOut: String? = null,
        address: String = "",
        tripId: String? = "t1",
        type: String? = null,
        lat: Double? = null,
        lng: Double? = null,
    ) = StayRecord(
        id = id, tripId = tripId, name = name, addressLocal = address,
        checkIn = checkIn, checkOut = checkOut, type = type, lat = lat, lng = lng, savedAt = "2026-10-02T10:00",
    )

    /** 11월 3일~5일 호텔 A, 5일~7일 호텔 B (밤마다 다른 숙소) */
    private val hotelA = stay("a", "리버뷰 방콕 호텔", "2026-11-03", "2026-11-05", "123 Soi Sukhumvit 11, Bangkok 10110", type = "hotel")
    private val hotelB = stay("b", "아유타야 리버 게스트하우스", "2026-11-05", "2026-11-07", "45 Naresuan Road, Ayutthaya 13000", type = "guest_house")

    // ---------------- 날짜별로 다른 숙소 ----------------

    @Test
    fun differentHotelsOnDifferentNights() {
        val all = listOf(hotelB, hotelA)
        assertEquals(listOf("a", "b"), Stays.forTrip(all, trip).map { it.id })
        assertEquals("a", Stays.covering(all, LocalDate.of(2026, 11, 3))!!.id)
        assertEquals("a", Stays.covering(all, LocalDate.of(2026, 11, 4))!!.id)
        // 옮기는 날(11월 5일)은 새 숙소에서 잔다
        assertEquals("b", Stays.covering(all, LocalDate.of(2026, 11, 5))!!.id)
        assertEquals("b", Stays.covering(all, LocalDate.of(2026, 11, 6))!!.id)
        // 마지막 퇴실일은 묵는 밤이 없지만, 그 날 아침까지는 그 숙소에 있다
        assertNull(Stays.covering(all, LocalDate.of(2026, 11, 7)))
        assertEquals("b", Stays.on(all, LocalDate.of(2026, 11, 7))!!.id)
        assertEquals(2, Stays.nights(hotelA))
        assertEquals(2, Stays.nights(hotelB))
    }

    @Test
    fun arrivalStayIsTheOneCoveringTheArrivalDate() {
        assertEquals("a", Stays.forArrival(listOf(hotelA, hotelB), trip)!!.id)
        // 도착한 날을 덮는 숙소가 없으면 첫 숙소
        val late = stay("c", "늦게 들어가는 호텔", "2026-11-05", "2026-11-07")
        assertEquals("c", Stays.forArrival(listOf(late), trip)!!.id)
        assertNull(Stays.forArrival(emptyList(), trip))
    }

    @Test
    fun staysWithoutTripIdShowOnlyWhereDatesFit() {
        val moved = stay("m", "옮겨 온 숙소", "2026-11-03", "2026-11-05", tripId = null)
        val other = stay("o", "다른 날 숙소", "2027-02-10", "2027-02-14", tripId = null)
        val undated = stay("u", "날짜 없는 숙소", tripId = null)
        val all = listOf(moved, other, undated)
        assertEquals(setOf("m", "u"), Stays.forTrip(all, trip).map { it.id }.toSet())
        // 다른 여행 숙소는 이 여행에 끼어들지 않는다
        assertFalse(Stays.forTrip(all, trip).any { it.id == "o" })
        // 다른 여행 id가 붙은 숙소도 섞이지 않는다
        assertEquals(emptyList<String>(), Stays.forTrip(listOf(stay("x", "남의 여행", tripId = "t2")), trip).map { it.id })
    }

    // ---------------- 부드러운 알림 ----------------

    @Test
    fun notesWarnGentlyWithoutBlocking() {
        assertEquals(emptyList<StayNote>(), Stays.notes(listOf(hotelA, hotelB), trip))
        // 빈 날: 5일 퇴실 → 6일 입실
        val gap = Stays.notes(listOf(hotelA, stay("g", "하루 비는 호텔", "2026-11-06", "2026-11-07")), trip)
        assertEquals(StayNoteKind.Gap, gap.single().kind)
        assertEquals(LocalDate.of(2026, 11, 5), gap.single().from)
        assertEquals(LocalDate.of(2026, 11, 6), gap.single().to)
        // 겹침: 4일 입실인데 앞 숙소는 5일 퇴실
        val overlap = Stays.notes(listOf(hotelA, stay("v", "겹치는 호텔", "2026-11-04", "2026-11-07")), trip)
        assertEquals(listOf(StayNoteKind.Overlap), overlap.map { it.kind })
        // 날짜를 안 적음
        val missing = Stays.notes(listOf(stay("n", "날짜 없는 호텔")), trip)
        assertTrue(StayNoteKind.MissingDates in missing.map { it.kind })
        // 도착한 날 묵을 곳이 없음
        val late = Stays.notes(listOf(stay("l", "늦게 가는 호텔", "2026-11-05", "2026-11-07")), trip)
        assertTrue(StayNoteKind.ArrivalMissing in late.map { it.kind })
        // 여행 날짜 밖
        val outside = Stays.notes(listOf(stay("x", "먼저 들어간 호텔", "2026-11-01", "2026-11-08")), trip)
        assertTrue(StayNoteKind.Outside in outside.map { it.kind })
        // 숙소가 없으면 알림이 아니라 '아직 없어요'다
        assertEquals(emptyList<StayNote>(), Stays.notes(emptyList(), trip))
    }

    // ---------------- 구글 지도 (공식 URL 문서) ----------------

    @Test
    fun mapsLinksUseDocumentedUrls() {
        val url = Stays.searchUrl(hotelA)!!
        assertTrue(url.startsWith("https://www.google.com/maps/search/?api=1&query="))
        // 주소가 그대로 퍼센트 인코딩된다(공백·쉼표가 주소를 깨지 않게)
        assertTrue(url.contains("123+Soi+Sukhumvit+11%2C+Bangkok+10110"))
        // 좌표를 알면 좌표로 찾는다
        val pinned = hotelA.copy(lat = 13.7437, lng = 100.5548)
        assertEquals("https://www.google.com/maps/search/?api=1&query=13.7437%2C100.5548", Stays.searchUrl(pinned))
        // 주소가 없으면 이름으로, 둘 다 없으면 지도 버튼이 없다
        assertTrue(Stays.searchUrl(stay("n", "이름만 호텔"))!!.contains("query=%EC%9D%B4%EB%A6%84%EB%A7%8C"))
        assertNull(Stays.searchUrl(stay("e", "")))
        // 길찾기는 이동하기 화면과 같은 주소(공식 문서 dir)
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=13.7437%2C100.5548&travelmode=transit",
            RideLinker.mapsUrl(Stays.place(pinned)!!),
        )
    }

    // ---------------- 가는 곳과 한 방향으로 ----------------

    @Test
    fun stayBecomesPlaceWithTheSameId() {
        val place = Stays.place(hotelA)!!
        assertEquals("a", place.id)
        assertEquals("리버뷰 방콕 호텔", place.name)
        assertEquals("123 Soi Sukhumvit 11, Bangkok 10110", place.addressLocal)
        // 보여 줄 주소가 없으면 가는 곳이 아니다
        assertNull(Stays.place(stay("n", "주소 없는 호텔")))
    }

    @Test
    fun syncKeepsManualPlacesAndReplacesInPlace() {
        val manual = Place("hand-1", "친구 집", "1 Somewhere Road")
        val first = syncStayPlace(listOf(manual), "a", Stays.place(hotelA))
        assertEquals(listOf("hand-1", "a"), first.map { it.id })
        // 주소를 고치면 그 자리에서 바뀐다(순서가 흔들리지 않는다)
        val edited = syncStayPlace(first, "a", Stays.place(hotelA.copy(addressLocal = "999 New Road, Bangkok")))
        assertEquals(listOf("hand-1", "a"), edited.map { it.id })
        assertEquals("999 New Road, Bangkok", edited.last().addressLocal)
        // 숙소를 지우거나 주소를 비우면 가는 곳에서 빠진다 — 사람이 넣은 가는 곳은 그대로
        val removed = syncStayPlace(edited, "a", null)
        assertEquals(listOf("hand-1"), removed.map { it.id })
    }

    // ---------------- 예전 저장본 옮기기 ----------------

    @Test
    fun migratesLodgingBookingsKeepingIds() {
        val before = VaultContents(
            bookings = listOf(
                BookingRecord(id = "f1", kind = "flight", title = "방콕 왕복", flightNumbers = listOf("KE651"), dates = listOf("2026-11-03"), savedAt = "x"),
                BookingRecord(id = "L1", kind = "lodging", title = "방콕 숙소", reference = "RV-0000", checkIn = "2026-11-03", checkOut = "2026-11-07", savedAt = "y"),
                BookingRecord(id = "o1", kind = "other", title = "기차표", savedAt = "z"),
            ),
        )
        val after = Stays.migrate(before)
        // 숙소는 묵는 곳으로 옮겨 가고 **id가 그대로**다 (가는 곳·체크 표시가 흔들리지 않게)
        val moved = after.stays.single()
        assertEquals("L1", moved.id)
        assertEquals("방콕 숙소", moved.name)
        assertEquals("RV-0000", moved.reference)
        assertEquals("2026-11-03", moved.checkIn)
        assertEquals("2026-11-07", moved.checkOut)
        assertNull(moved.tripId)
        assertEquals("y", moved.savedAt)
        // 항공권·그 밖의 서류는 그대로 남는다(잃는 것이 없다)
        assertEquals(listOf("f1", "o1"), after.bookings.map { it.id })
        // 두 번 해도 같다(같은 객체를 그대로 돌려준다)
        assertSame(after, Stays.migrate(after))
        // 날짜 칸이 없고 dates만 있던 예전 저장본도 날짜를 살린다
        val fromDates = Stays.migrate(
            VaultContents(bookings = listOf(BookingRecord(id = "L2", kind = "lodging", title = "숙소", dates = listOf("2026-12-01", "2026-12-04"), savedAt = "y"))),
        ).stays.single()
        assertEquals("2026-12-01", fromDates.checkIn)
        assertEquals("2026-12-04", fromDates.checkOut)
    }

    @Test
    fun stayTypeValuesMatchRecipeOptionValues() {
        // 레시피 stay_type 선택지 값과 글자가 같아야 입국 카드에 넣을 수 있다 (사이트 글자를 지어내지 않는다)
        assertEquals(
            listOf("hotel", "guest_house", "hostel", "apartment", "friend", "other"),
            StayType.entries.map { it.key },
        )
        assertEquals(StayType.Hotel, StayType.of("hotel"))
        assertNull(StayType.of("villa"))
        assertNull(StayType.of(null))
    }
}
