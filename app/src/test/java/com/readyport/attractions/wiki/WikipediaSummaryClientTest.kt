package com.readyport.attractions.wiki

import com.readyport.attractions.WikiPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/** 위키백과 요약: 주소 만들기·허용 목록·응답 해석. 네트워크 없이 가짜 응답만 쓴다 */
class WikipediaSummaryClientTest {

    private val ko = WikiPage("ko", "센소지")
    private val en = WikiPage("en", "Sensō-ji")

    /** REST v1 summary 응답 모양(필요한 필드만, 글은 가짜) */
    private fun body(
        type: String = "standard",
        title: String = "센소지",
        extract: String = "가짜 요약 첫 문장이에요. 둘째 문장이에요.",
        page: String? = "https://ko.wikipedia.org/wiki/%EC%84%BC%EC%86%8C%EC%A7%80",
    ) = """
        {"type":"$type","title":"${title.replace(' ', '_')}","displaytitle":"<span>$title</span>",
         "titles":{"canonical":"${title.replace(' ', '_')}","normalized":"$title","display":"<span>$title</span>"},
         "lang":"ko","extract":"$extract","extract_html":"<p>$extract</p>",
         "thumbnail":{"source":"https://upload.wikimedia.org/x.jpg"},
         "content_urls":{"desktop":{"page":${page?.let { "\"$it\"" } ?: "null"}},"mobile":{"page":"https://ko.m.wikipedia.org/wiki/x"}}}
    """.trimIndent()

    private class FakeHttp(private val responses: List<Any>) : WikiHttp {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()

        override suspend fun get(url: String, headers: Map<String, String>): WikiHttpResponse {
            calls += url to headers
            return when (val r = responses[minOf(calls.size - 1, responses.size - 1)]) {
                is WikiHttpResponse -> r
                is IOException -> throw r
                else -> error("bad fake")
            }
        }
    }

    private fun client(vararg responses: Any) = FakeHttp(responses.toList()).let { it to WikipediaSummaryClient(it, "ReadyPortTest/1.0 (https://readyport-app.web.app)") }

    // ---------------- 주소 ----------------

    @Test fun summaryUrlEncodesTitle() {
        assertEquals("https://ko.wikipedia.org/api/rest_v1/page/summary/%EC%84%BC%EC%86%8C%EC%A7%80", WikiHosts.summaryUrl(ko))
        assertEquals("https://en.wikipedia.org/api/rest_v1/page/summary/Sens%C5%8D-ji", WikiHosts.summaryUrl(en))
        // 공백은 밑줄, '/'·'?'·'&'는 인코딩 — 경로 조각 하나로 남는다
        assertEquals("https://en.wikipedia.org/api/rest_v1/page/summary/AC%2FDC_%3F%26", WikiHosts.summaryUrl(WikiPage("en", "AC/DC ?&")))
        assertEquals("https://en.wikipedia.org/wiki/Osaka_Castle", WikiHosts.articleUrl(WikiPage("en", "Osaka Castle")))
    }

    @Test fun onlyKoAndEnWikipediaAreAllowed() {
        assertNull(WikiHosts.summaryUrl(WikiPage("fr", "Sensō-ji")))
        assertNull(WikiHosts.summaryUrl(WikiPage("ko", "..")))
        assertTrue(WikiHosts.allowed("https://ko.wikipedia.org/wiki/%EC%84%BC"))
        listOf(
            "http://ko.wikipedia.org/wiki/x",
            "https://ko.m.wikipedia.org/wiki/x",
            "https://fr.wikipedia.org/wiki/x",
            "https://ko.wikipedia.org.evil.example/wiki/x",
            "https://evil.example/ko.wikipedia.org/wiki/x",
            "https://ko.wikipedia.org/w/index.php?title=x",
            "https://ko.wikipedia.org/wiki/a/b",
            "https://ko.wikipedia.org/wiki/x#frag",
            "https://ko.wikipedia.org/api/rest_v1/page/summary/..",
        ).forEach { assertFalse(it, WikiHosts.allowed(it)) }
    }

    // ---------------- 응답 해석 ----------------

    @Test fun standardSummaryIsReady() = runBlocking {
        val (http, c) = client(WikiHttpResponse(200, body()))
        val r = c.summary(ko) as WikiSummaryResult.Ready
        assertEquals(WikiSummary("ko", "센소지", "가짜 요약 첫 문장이에요. 둘째 문장이에요.", "https://ko.wikipedia.org/wiki/%EC%84%BC%EC%86%8C%EC%A7%80"), r.summary)
        // Wikimedia 예절: 설명이 든 User-Agent, JSON 요약 프로필
        val headers = http.calls.single().second
        assertTrue(headers.getValue("User-Agent").contains("https://readyport-app.web.app"))
        assertTrue(headers.getValue("Accept").startsWith("application/json"))
    }

    @Test fun defaultUserAgentNamesAppAndContact() {
        assertTrue(WikipediaSummaryClient.USER_AGENT, Regex("""^ReadyPort/\S+ \(https://readyport-app\.web\.app; [^)]+\) \S+""").matches(WikipediaSummaryClient.USER_AGENT))
    }

    @Test fun pageUrlFallsBackToBuiltUrlWhenResponseUrlIsNotAllowed() = runBlocking {
        val (_, c) = client(WikiHttpResponse(200, body(title = "Osaka Castle", page = "https://evil.example/wiki/x")))
        val r = c.summary(WikiPage("en", "Osaka Castle")) as WikiSummaryResult.Ready
        assertEquals("https://en.wikipedia.org/wiki/Osaka_Castle", r.summary.pageUrl)
        assertEquals("Osaka Castle", r.summary.title)
    }

    @Test fun notFoundDisambiguationAndEmptyExtract() = runBlocking {
        assertEquals(WikiSummaryResult.NotFound, client(WikiHttpResponse(404)).second.summary(ko))
        assertEquals(WikiSummaryResult.NotFound, client(WikiHttpResponse(200, body(type = "disambiguation"))).second.summary(ko))
        assertEquals(WikiSummaryResult.NotFound, client(WikiHttpResponse(200, body(type = "no-extract", extract = " "))).second.summary(ko))
        assertEquals(
            WikiSummaryResult.NotFound,
            client(WikiHttpResponse(200, """{"type":"https://mediawiki.org/wiki/HyperSwitch/errors/not_found","title":"Not found."}""")).second.summary(ko),
        )
    }

    @Test fun brokenResponsesFail() = runBlocking {
        assertEquals(WikiSummaryResult.Failed, client(WikiHttpResponse(200, "<html>oops")).second.summary(ko))
        assertEquals(WikiSummaryResult.Failed, client(WikiHttpResponse(200, null)).second.summary(ko))
        assertEquals(WikiSummaryResult.Failed, client(WikiHttpResponse(503)).second.summary(ko))
        assertEquals(WikiSummaryResult.Failed, client(WikiHttpResponse(429)).second.summary(ko))
    }

    @Test fun networkErrorsAreOfflineOrFailed() = runBlocking {
        assertEquals(WikiSummaryResult.Offline, client(UnknownHostException("ko.wikipedia.org")).second.summary(ko))
        assertEquals(WikiSummaryResult.Offline, client(SocketTimeoutException()).second.summary(ko))
        assertEquals(WikiSummaryResult.Failed, client(SSLHandshakeException("bad cert")).second.summary(ko))
    }

    @Test fun redirectInsideAllowListIsFollowed() = runBlocking {
        val (http, c) = client(
            WikiHttpResponse(302, location = "%EC%84%BC%EC%86%8C%EC%A7%80"),
            WikiHttpResponse(200, body()),
        )
        // 다른 이름(센소사) → 넘겨 준 제목(센소지)으로 한 번 더
        assertTrue(c.summary(WikiPage("ko", "센소사")) is WikiSummaryResult.Ready)
        assertEquals("https://ko.wikipedia.org/api/rest_v1/page/summary/%EC%84%BC%EC%86%8C%EC%82%AC", http.calls[0].first)
        assertEquals("https://ko.wikipedia.org/api/rest_v1/page/summary/%EC%84%BC%EC%86%8C%EC%A7%80", http.calls[1].first)
    }

    @Test fun redirectOutsideAllowListIsRefused() = runBlocking {
        val (http, c) = client(WikiHttpResponse(301, location = "https://evil.example/api/rest_v1/page/summary/x"), WikiHttpResponse(200, body()))
        assertEquals(WikiSummaryResult.Failed, c.summary(ko))
        assertEquals(1, http.calls.size)
        // 같은 위키백과라도 요약 API가 아닌 곳은 따라가지 않는다
        val (_, c2) = client(WikiHttpResponse(302, location = "https://ko.wikipedia.org/wiki/x"))
        assertEquals(WikiSummaryResult.Failed, c2.summary(ko))
    }

    @Test fun redirectLoopStops() = runBlocking {
        val (http, c) = client(WikiHttpResponse(302, location = "%EC%84%BC"))
        assertEquals(WikiSummaryResult.Failed, c.summary(ko))
        assertEquals(3, http.calls.size)
    }

    @Test fun sessionCacheAvoidsSecondRequestButNotForFailures() = runBlocking {
        val (http, c) = client(WikiHttpResponse(200, body()))
        c.summary(ko)
        c.summary(ko)
        assertEquals(1, http.calls.size)
        val (http2, c2) = client(UnknownHostException("x"), WikiHttpResponse(200, body()))
        assertEquals(WikiSummaryResult.Offline, c2.summary(ko))
        assertTrue(c2.summary(ko) is WikiSummaryResult.Ready)
        assertEquals(2, http2.calls.size)
    }

    @Test fun badLanguageNeverHitsNetwork() = runBlocking {
        val (http, c) = client(WikiHttpResponse(200, body()))
        assertEquals(WikiSummaryResult.NotFound, c.summary(WikiPage("ja", "浅草寺")))
        assertTrue(http.calls.isEmpty())
    }
}
