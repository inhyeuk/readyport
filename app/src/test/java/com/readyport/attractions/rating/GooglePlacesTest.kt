package com.readyport.attractions.rating

import com.readyport.attractions.AttTestData
import com.readyport.attractions.AttractionsJson
import com.readyport.attractions.AttractionDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Google 별점 (Places API (New)) — 가짜 HTTP 만, 실제 네트워크 없음. 키·지문은 가짜 값 */
class GooglePlacesTest {
    private val placeId = "ChIJ_fake_place_0001"

    private class Recorder(var response: PlacesHttpResponse = PlacesHttpResponse(200, BODY)) : PlacesHttp {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        var fail = false
        override suspend fun get(url: String, headers: Map<String, String>): PlacesHttpResponse {
            calls += url to headers
            if (fail) throw IOException("offline")
            return response
        }
    }

    private fun client(http: PlacesHttp, key: String = "test-key", sha: String? = "AB01CD") =
        GoogleRatingClient(apiKey = key, packageName = "com.readyport.debug", certSha1 = { sha }, http = http)

    @Test fun parsesRatingCountAndOnlyGoogleMapsLinks() {
        assertEquals(GoogleRating(4.5, 12345, "https://maps.google.com/?cid=1"), GoogleRatingClient.parse(BODY))
        assertEquals(GoogleRating(4.0, 0, null), GoogleRatingClient.parse("""{"rating": 4}"""))
        assertNull(GoogleRatingClient.parse("""{"userRatingCount": 3}"""))
        assertNull(GoogleRatingClient.parse("""{"rating": 0}"""))
        assertNull(GoogleRatingClient.parse("""{"rating": 6.1}"""))
        assertNull(GoogleRatingClient.parse("글"))
        assertNull(GoogleRatingClient.parse(null))
        // 지도 주소가 Google 이 아니면 링크를 보이지 않는다
        assertNull(GoogleRatingClient.parse("""{"rating": 4.2, "googleMapsUri": "https://evil.example/maps"}""")!!.mapsUri)
        assertNull(GoogleRatingClient.parse("""{"rating": 4.2, "googleMapsUri": "http://maps.google.com/"}""")!!.mapsUri)
        // 모르는 칸은 무시
        assertEquals(4.2, GoogleRatingClient.parse("""{"rating": 4.24, "reviews": [], "name": "x"}""")!!.rating, 0.0)
    }

    @Test fun headersForAndroidRestrictedKey() {
        val h = client(Recorder()).headers()
        assertEquals(
            mapOf(
                "X-Goog-Api-Key" to "test-key",
                "X-Goog-FieldMask" to "rating,userRatingCount,googleMapsUri",
                "X-Android-Package" to "com.readyport.debug",
                "X-Android-Cert" to "AB01CD",
            ),
            h,
        )
        // 지문을 못 구하면 그 머리글만 뺀다
        assertFalse("X-Android-Cert" in client(Recorder(), sha = null).headers())
    }

    @Test fun requestsTheDetailsUrlOnceAndCachesForTheSession() = runBlocking {
        val http = Recorder()
        val c = client(http)
        assertEquals(4.5, c.rating(placeId)!!.rating, 0.0)
        assertEquals(4.5, c.rating(placeId)!!.rating, 0.0)
        assertEquals(1, http.calls.size)
        assertEquals("https://places.googleapis.com/v1/places/$placeId", http.calls[0].first)
    }

    @Test fun failuresHideQuietly() = runBlocking {
        val http = Recorder(PlacesHttpResponse(403, null))
        assertNull(client(http).rating(placeId))
        http.fail = true
        assertNull(client(http).rating(placeId))
        // 모양이 틀린 place ID 는 묻지도 않는다
        val before = http.calls.size
        assertNull(client(http).rating("bad id/../x"))
        assertNull(client(http).rating(null))
        assertEquals(before, http.calls.size)
        // 키가 없는 빌드(CI)는 꺼져 있다
        assertNull(client(http, key = "").rating(placeId))
        assertEquals(before, http.calls.size)
    }

    @Test fun allowListIsOnlyThePlaceDetailsPath() {
        assertTrue(PlacesHosts.allowed("https://places.googleapis.com/v1/places/$placeId"))
        assertFalse(PlacesHosts.allowed("http://places.googleapis.com/v1/places/$placeId"))
        assertFalse(PlacesHosts.allowed("https://places.googleapis.com/v1/places/$placeId?key=x"))
        assertFalse(PlacesHosts.allowed("https://places.googleapis.com.evil.example/v1/places/$placeId"))
        assertFalse(PlacesHosts.allowed("https://places.googleapis.com/v1/places:searchText"))
        assertNull(PlacesHosts.detailsUrl("short"))
    }

    @Test fun koreanReviewCount() {
        assertEquals("0", koreanCount(0))
        assertEquals("987", koreanCount(987))
        assertEquals("9,999", koreanCount(9_999))
        assertEquals("1만", koreanCount(10_000))
        assertEquals("1.2만", koreanCount(12_345))
        assertEquals("10만", koreanCount(100_400))
        assertEquals("123.4만", koreanCount(1_234_567))
    }

    @Test fun certFingerprintIsUppercaseHexWithoutColons() {
        assertEquals("00AB0F", AppSigning.hex(byteArrayOf(0, 0xAB.toByte(), 0x0F)))
    }

    @Test fun placeIdIsReadLenientlyFromTheAttractionsFile() {
        val catalog = AttTestData.catalog(AttTestData.debugSample())
        assertEquals("ChIJ8T1GpMGOGGARDYGSgpooDWw", catalog.attraction("sensoji")!!.googlePlaceId)
        // 모양이 틀리면 버린다(별점 칸 없음)
        val dto = AttractionsJson.decodeFromString(AttractionDto.serializer(), """{"id": "x", "google_place_id": "no spaces allowed"}""")
        assertEquals("no spaces allowed", dto.googlePlaceId)
        val doc = AttTestData.debugSample()
        val broken = doc.copy(attractions = doc.attractions.map { it.copy(googlePlaceId = "bad id") })
        assertNull(AttTestData.catalog(broken).attraction("sensoji")!!.googlePlaceId)
    }

    private companion object {
        const val BODY = """{"rating": 4.48, "userRatingCount": 12345, "googleMapsUri": "https://maps.google.com/?cid=1"}"""
    }
}
