package com.readyport.doc.booking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 가짜 예약 문구만 쓴다. 실제 예약번호·이름 금지 (작업 규칙 3·4) */
class BookingExtractorTest {

    @Test
    fun koreanFlightItinerary() {
        val text = """
            [항공권 예약 안내]
            예약번호: ABC123
            편명 KE 651  인천 → 방콕
            출발 2026년 11월 3일 18:05
            귀국 편명 KE652 2026년 11월 7일
        """.trimIndent()
        val r = BookingExtractor.extract(text)
        assertEquals(BookingKind.Flight, r.kind)
        assertEquals("ABC123", r.reference)
        assertEquals(listOf("KE651", "KE652"), r.flightNumbers)
        assertEquals(listOf(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 7)), r.dates)
        assertNull(r.checkIn)
    }

    @Test
    fun englishHotelConfirmation() {
        val text = """
            Your booking is confirmed!
            Confirmation number: 4417-2290-12
            Sample Riverside Hotel
            Check-in: Tue, 3 Nov 2026 (from 14:00)
            Check-out: Sat, 7 Nov 2026 (until 12:00)
            1 room, 2 adults
        """.trimIndent()
        val r = BookingExtractor.extract(text)
        assertEquals(BookingKind.Lodging, r.kind)
        assertEquals("4417-2290-12", r.reference)
        assertEquals(LocalDate.of(2026, 11, 3), r.checkIn)
        assertEquals(LocalDate.of(2026, 11, 7), r.checkOut)
        assertTrue(r.flightNumbers.isEmpty())
    }

    @Test
    fun referenceOnNextLineAndUsStyleDates() {
        val text = """
            Booking reference
            XYZ9QK
            Flight 7C2201 Nov 3, 2026
        """.trimIndent()
        val r = BookingExtractor.extract(text)
        assertEquals("XYZ9QK", r.reference)
        assertEquals(listOf("7C2201"), r.flightNumbers)
        assertEquals(listOf(LocalDate.of(2026, 11, 3)), r.dates)
    }

    @Test
    fun ticketStyleCompactDate() {
        assertEquals(listOf(LocalDate.of(2026, 11, 3)), BookingExtractor.findDates("KE651 03NOV26 ICNBKK"))
        assertEquals(listOf(LocalDate.of(2026, 11, 3)), BookingExtractor.findDates("3 NOVEMBER 2026"))
        assertEquals(listOf(LocalDate.of(2026, 11, 3)), BookingExtractor.findDates("2026.11.03"))
    }

    @Test
    fun ignoresLookalikesWithoutContext() {
        // 알 수 없는 코드 + 문맥 단어 없음 → 편명이 아니다. 잘못된 날짜도 버린다
        val r = BookingExtractor.extract("주소 AB 12, 전화 02-1234-5678\n2026-13-45")
        assertTrue(r.flightNumbers.isEmpty())
        assertTrue(r.dates.isEmpty())
        assertEquals(BookingKind.Unknown, r.kind)
        assertNull(r.reference)
    }

    @Test
    fun toStringHidesReference() {
        val r = BookingExtractor.extract("예약번호 ABC123")
        assertTrue(!r.toString().contains("ABC123"))
    }

    // ---------------- 숙소 이름·주소 (2026-10-03) ----------------

    @Test
    fun koreanHotelVoucherGivesNameAndAddress() {
        val text = """
            호텔 예약 확인서
            예약번호: RV-0000-0000
            숙소명: 리버뷰 방콕 호텔
            주소: 123 Soi Sukhumvit 11, Khlong Toei Nuea, Watthana, Bangkok 10110
            체크인 2026년 11월 3일 15:00
            체크아웃 2026년 11월 5일 11:00
            객실 1개, 성인 2명
        """.trimIndent()
        val r = BookingExtractor.extract(text)
        assertEquals(BookingKind.Lodging, r.kind)
        assertEquals("리버뷰 방콕 호텔", r.stayName)
        assertEquals("123 Soi Sukhumvit 11, Khlong Toei Nuea, Watthana, Bangkok 10110", r.stayAddress)
        assertEquals(LocalDate.of(2026, 11, 3), r.checkIn)
        assertEquals(LocalDate.of(2026, 11, 5), r.checkOut)
        // 서류 제목 줄(`호텔 예약 확인서`)을 숙소 이름으로 보지 않는다
        assertTrue(r.stayName != "호텔 예약 확인서")
    }

    @Test
    fun englishHotelVoucherNameFromKeywordLine() {
        val text = """
            Your booking is confirmed!
            Confirmation number: 4417-2290-12
            Sample Riverside Hotel
            Address: 45 Naresuan Road, Pratu Chai, Ayutthaya 13000
            Check-in: Tue, 3 Nov 2026 (from 14:00)
            Check-out: Sat, 7 Nov 2026 (until 12:00)
        """.trimIndent()
        val r = BookingExtractor.extract(text)
        assertEquals("Sample Riverside Hotel", r.stayName)
        assertEquals("45 Naresuan Road, Pratu Chai, Ayutthaya 13000", r.stayAddress)
    }

    @Test
    fun addressOnNextLineAndGuesthouseName() {
        val text = """
            숙소 예약 내역
            Sample Garden Guesthouse
            주소
            9 Jalan Petaling, 50000 Kuala Lumpur
            체크인 2026-12-01
            체크아웃 2026-12-03
        """.trimIndent()
        val r = BookingExtractor.extract(text)
        assertEquals("Sample Garden Guesthouse", r.stayName)
        assertEquals("9 Jalan Petaling, 50000 Kuala Lumpur", r.stayAddress)
    }

    @Test
    fun staysConservativeWhenNothingLooksLikeAStay() {
        // 항공권에서는 숙소 후보를 만들지 않는다 (출발/도착을 숙소로 오해하지 않게)
        val flight = BookingExtractor.extract("예약번호 ABC123\n편명 KE651 2026년 11월 3일")
        assertNull(flight.stayName)
        assertNull(flight.stayAddress)
        // 이메일 주소 줄은 주소가 아니다
        val mail = BookingExtractor.extract("호텔 예약\n체크아웃 2026-11-05\n이메일 주소: sample@example.org")
        assertNull(mail.stayAddress)
        // 못 찾으면 지어내지 않고 비워 둔다
        val bare = BookingExtractor.extract("숙소 체크아웃 2026-11-05")
        assertNull(bare.stayAddress)
    }

    @Test
    fun toStringHidesStayNameAndAddress() {
        val r = BookingExtractor.extract("숙소명: 리버뷰 방콕 호텔\n주소: 123 Sample Road\n체크아웃 2026-11-05")
        assertTrue(!r.toString().contains("리버뷰"))
        assertTrue(!r.toString().contains("Sample Road"))
    }
}
