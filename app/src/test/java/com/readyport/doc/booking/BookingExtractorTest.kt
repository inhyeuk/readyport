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
}
