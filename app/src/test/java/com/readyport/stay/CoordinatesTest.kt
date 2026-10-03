package com.readyport.stay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 숙소 좌표 읽기 (다듬기 S2). 쓰는 좌표·주소는 모두 **지어낸 값**이다(실제 숙소·실제 사람 없음).
 * 못 읽는 글자는 반드시 null — 잘못 읽어서 엉뚱한 자리를 지도에 띄우지 않는다.
 */
class CoordinatesTest {

    @Test
    fun readsPlainPair() {
        assertEquals(LatLng(13.75, 100.5), Coordinates.parse("13.75, 100.5"))
        assertEquals(LatLng(13.75, 100.5), Coordinates.parse("13.75,100.5"))
        assertEquals(LatLng(13.75, 100.5), Coordinates.parse("  13.75   100.5  "))
        // 정수 쌍도 좌표다
        assertEquals(LatLng(14.0, 100.0), Coordinates.parse("14, 100"))
        // 남반구·서반구는 음수
        assertEquals(LatLng(-33.86, 151.2), Coordinates.parse("-33.86, 151.2"))
    }

    @Test
    fun readsHemisphereLetters() {
        assertEquals(LatLng(13.75, 100.5), Coordinates.parse("13.75° N, 100.5° E"))
        assertEquals(LatLng(-33.86, -70.66), Coordinates.parse("33.86S 70.66W"))
        // 경도를 먼저 적어도 방위 글자로 가린다
        assertEquals(LatLng(13.75, 100.5), Coordinates.parse("100.5E, 13.75N"))
    }

    @Test
    fun readsGoogleMapsUrls() {
        assertEquals(
            LatLng(13.7461, 100.5349),
            Coordinates.parse("https://www.google.com/maps/place/Fake+Hotel/@13.7461,100.5349,17z/data=!4m5"),
        )
        assertEquals(
            LatLng(13.7461, 100.5349),
            Coordinates.parse("https://www.google.com/maps/search/?api=1&query=13.7461,100.5349"),
        )
        assertEquals(LatLng(13.7461, 100.5349), Coordinates.parse("https://maps.google.com/?q=13.7461,100.5349"))
        assertEquals(
            LatLng(13.7461, 100.5349),
            Coordinates.parse("https://www.google.com/maps/dir/?api=1&destination=13.7461,100.5349&travelmode=transit"),
        )
        // 긴 주소의 !3d!4d 자리
        assertEquals(
            LatLng(13.7461, 100.5349),
            Coordinates.parse("https://www.google.com/maps/place/x/data=!3m1!4b1!4m2!3m1!1s0x0:0x0!3d13.7461!4d100.5349"),
        )
    }

    @Test
    fun prefersUrlShapeOverStrayNumbers() {
        // 줌 값(17z)·보기 번호가 더 있어도 @ 뒤 두 숫자만 쓴다
        assertEquals(LatLng(35.0116, 135.7681), Coordinates.parse("google.com/maps/@35.0116,135.7681,15.5z/data=!3m1!1e3"))
        // 주소인데 좌표 틀이 없으면 모르겠다고 한다(주소 안 숫자를 좌표로 읽지 않는다)
        assertNull(Coordinates.parse("https://maps.app.goo.gl/abc123"))
        assertNull(Coordinates.parse("https://www.google.com/maps/place/Fake+Hotel+123+Road+11"))
    }

    @Test
    fun refusesJunk() {
        assertNull(Coordinates.parse(null))
        assertNull(Coordinates.parse(""))
        assertNull(Coordinates.parse("   "))
        assertNull(Coordinates.parse("여기 어디쯤"))
        // 숫자 하나는 좌표가 아니다
        assertNull(Coordinates.parse("13.7461"))
        // 숫자 셋 이상은 무엇이 위도인지 알 수 없다
        assertNull(Coordinates.parse("13.7461, 100.5349, 15"))
        // 주소 글자가 섞이면 좌표로 보지 않는다
        assertNull(Coordinates.parse("123 Soi Fake 11, Bangkok"))
        assertNull(Coordinates.parse("위도 13.7461 경도 100.5349"))
        // 도분초는 아직 안 받는다 — 모르겠다고 한다(잘못 읽지 않는다)
        assertNull(Coordinates.parse("13°44'46\"N 100°32'06\"E"))
    }

    @Test
    fun refusesOutOfRange() {
        assertNull(Coordinates.parse("91, 100.5"))
        assertNull(Coordinates.parse("-90.5, 100.5"))
        assertNull(Coordinates.parse("13.75, 181"))
        assertNull(Coordinates.parse("13.75, -180.4"))
        // 한계값은 받는다
        assertEquals(LatLng(90.0, 180.0), Coordinates.parse("90, 180"))
        assertEquals(LatLng(-90.0, -180.0), Coordinates.parse("-90, -180"))
    }

    @Test
    fun roundsToSixDecimals() {
        assertEquals(LatLng(13.746123, 100.534957), Coordinates.parse("13.7461234567, 100.5349567"))
    }

    @Test
    fun formatsForTheField() {
        assertEquals("13.75, 100.5", Coordinates.format(13.75, 100.5))
        assertEquals("14, 100", Coordinates.format(14.0, 100.0))
        assertEquals("", Coordinates.text(13.75, null))
        assertEquals("", Coordinates.text(null, 100.5))
        assertEquals("13.75, 100.5", Coordinates.text(13.75, 100.5))
        // 칸에 보인 글자를 다시 읽으면 같은 값이다(저장 → 보여 주기 → 다시 읽기가 흔들리지 않게)
        assertEquals(LatLng(13.746123, 100.534957), Coordinates.parse(Coordinates.format(13.746123, 100.534957)))
    }
}
