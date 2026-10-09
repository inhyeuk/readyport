package com.readyport.attractions.wiki

import com.readyport.BuildConfig
import com.readyport.attractions.WikiPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * 관광지 상세 '위키백과에서 보기' (사장님 결정 2026-10-09: 요약 팝업 + 전체 보기).
 *
 * - 사용자가 버튼을 눌렀을 때만 위키백과 REST 요약(`/api/rest_v1/page/summary/{제목}`)을 한 번 받는다. 개인정보는 보내지 않는다
 *   (요청에는 문서 제목만. 위키미디어 서버에는 IP 주소 등 통신 정보가 간다 — 개인정보 처리방침 4절).
 * - 받는 곳은 [WikiHosts] 허용 목록(ko·en.wikipedia.org)뿐이고, 허용 목록 밖으로 가는 리디렉션은 따라가지 않는다.
 * - 글은 저장하지 않는다. 앱을 쓰는 동안만 메모리에 몇 건 둔다([WikipediaSummaryClient.cache]).
 * - Wikimedia API 예절: 앱 이름·버전·연락처가 든 User-Agent.
 */
object WikiHosts {
    val LANGS = setOf("ko", "en")

    /** 불러오거나 여는 주소: https + ko/en 위키백과 + 요약 API 또는 문서 경로, 경로 조각 하나 */
    private val Allowed = Regex("""^https://(ko|en)\.wikipedia\.org/(api/rest_v1/page/summary/|wiki/)([^/?#]+)$""")

    fun allowed(url: String): Boolean {
        val m = Allowed.matchEntire(url) ?: return false
        val segment = m.groupValues[3]
        return segment != "." && segment != ".." && segment != "%2E" && segment != "%2E%2E"
    }

    /** 제목 → 경로 조각. 공백은 밑줄, 나머지는 퍼센트 인코딩('/'도 %2F — REST API 규칙) */
    fun encodeTitle(title: String): String = URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")

    fun summaryUrl(page: WikiPage): String? =
        "https://${page.lang}.wikipedia.org/api/rest_v1/page/summary/${encodeTitle(page.title)}".takeIf { page.lang in LANGS && allowed(it) }

    fun articleUrl(page: WikiPage): String? =
        "https://${page.lang}.wikipedia.org/wiki/${encodeTitle(page.title)}".takeIf { page.lang in LANGS && allowed(it) }

    /** CC BY-SA 4.0 이용 조건 (한국어) */
    const val LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/deed.ko"
}

/** 팝업에 그릴 요약 하나 — 제목·글(이미지 없음)·문서 주소 */
data class WikiSummary(val lang: String, val title: String, val extract: String, val pageUrl: String)

sealed interface WikiSummaryResult {
    data class Ready(val summary: WikiSummary) : WikiSummaryResult

    /** 문서가 없거나, 동음이의 문서이거나, 요약 글이 없음 */
    data object NotFound : WikiSummaryResult

    /** 인터넷 연결 없음(이름 풀이·연결 실패·시간 초과) */
    data object Offline : WikiSummaryResult

    /** 그 밖의 실패(서버 오류·모르는 응답) — 다시 해 볼 수 있다 */
    data object Failed : WikiSummaryResult
}

/** HTTP 응답 하나 (테스트에서 가짜로 바꿔 끼운다) */
data class WikiHttpResponse(val code: Int, val body: String? = null, val location: String? = null)

/** GET 한 번. 연결 실패는 [IOException]으로 던진다 */
fun interface WikiHttp {
    suspend fun get(url: String, headers: Map<String, String>): WikiHttpResponse
}

@Serializable
private data class SummaryDto(
    val type: String = "",
    val title: String = "",
    val titles: TitlesDto? = null,
    val extract: String = "",
    val lang: String = "",
    @SerialName("content_urls") val contentUrls: ContentUrlsDto? = null,
)

@Serializable
private data class TitlesDto(val canonical: String? = null, val normalized: String? = null)

@Serializable
private data class ContentUrlsDto(val desktop: PageUrlDto? = null)

@Serializable
private data class PageUrlDto(val page: String? = null)

class WikipediaSummaryClient(
    private val http: WikiHttp = UrlConnectionWikiHttp,
    private val userAgent: String = USER_AGENT,
) {
    /** 이번 앱 실행 동안만 — 파일·DataStore에 쓰지 않는다 */
    private val cache = object : LinkedHashMap<WikiPage, WikiSummary>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<WikiPage, WikiSummary>?): Boolean = size > CACHE_SIZE
    }

    suspend fun summary(page: WikiPage): WikiSummaryResult {
        synchronized(cache) { cache[page] }?.let { return WikiSummaryResult.Ready(it) }
        var url = WikiHosts.summaryUrl(page) ?: return WikiSummaryResult.NotFound
        val headers = mapOf(
            "User-Agent" to userAgent,
            "Accept" to "application/json; charset=utf-8; profile=\"https://www.mediawiki.org/wiki/Specs/Summary/1.4.0\"",
        )
        repeat(MAX_REDIRECTS + 1) {
            val resp = try {
                http.get(url, headers)
            } catch (e: IOException) {
                return if (e.isOffline()) WikiSummaryResult.Offline else WikiSummaryResult.Failed
            }
            when (resp.code) {
                HttpURLConnection.HTTP_OK -> {
                    val result = parse(page, resp.body)
                    if (result is WikiSummaryResult.Ready) synchronized(cache) { cache[page] = result.summary }
                    return result
                }
                HttpURLConnection.HTTP_NOT_FOUND -> return WikiSummaryResult.NotFound
                301, 302, 303, 307, 308 -> {
                    // 넘겨 주는 곳도 허용 목록 안이어야 따라간다(같은 위키백과의 다른 제목 등)
                    val next = resp.location?.let { loc -> runCatching { URL(URL(url), loc).toString() }.getOrNull() }
                    if (next == null || !WikiHosts.allowed(next) || !next.contains("/api/rest_v1/page/summary/")) return WikiSummaryResult.Failed
                    url = next
                }
                else -> return WikiSummaryResult.Failed
            }
        }
        return WikiSummaryResult.Failed
    }

    companion object {
        /** Wikimedia User-Agent 정책: 앱 이름/버전 (연락처) 라이브러리 */
        val USER_AGENT = "ReadyPort/${BuildConfig.VERSION_NAME} (https://readyport-app.web.app; Android app) HttpURLConnection"
        private const val CACHE_SIZE = 20
        private const val MAX_REDIRECTS = 2
        private val SummaryJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            isLenient = true
        }

        /** 앱 하나에 하나 (세션 메모리 캐시를 같이 쓴다) */
        val Default: WikipediaSummaryClient by lazy { WikipediaSummaryClient() }

        /** 응답 글 → 결과. 순수 함수라 테스트한다 */
        internal fun parse(page: WikiPage, body: String?): WikiSummaryResult {
            if (body.isNullOrBlank()) return WikiSummaryResult.Failed
            val dto = runCatching { SummaryJson.decodeFromString(SummaryDto.serializer(), body) }.getOrNull() ?: return WikiSummaryResult.Failed
            if (dto.type == "disambiguation" || dto.type.endsWith("not_found")) return WikiSummaryResult.NotFound
            val extract = dto.extract.trim()
            if (extract.isEmpty()) return WikiSummaryResult.NotFound
            val title = (dto.titles?.normalized ?: dto.title.replace('_', ' ')).ifBlank { page.title }
            val shownPage = WikiPage(page.lang, (dto.titles?.canonical ?: dto.title).replace('_', ' ').ifBlank { page.title })
            val pageUrl = dto.contentUrls?.desktop?.page?.takeIf { WikiHosts.allowed(it) && it.startsWith("https://${page.lang}.") }
                ?: WikiHosts.articleUrl(shownPage)
                ?: return WikiSummaryResult.Failed
            return WikiSummaryResult.Ready(WikiSummary(page.lang, title, extract, pageUrl))
        }

        private fun IOException.isOffline(): Boolean =
            this is UnknownHostException || this is ConnectException || this is NoRouteToHostException ||
                (this is InterruptedIOException) // SocketTimeoutException 포함
    }
}

/** 실제 네트워크: HttpURLConnection(새 라이브러리 없음). 리디렉션은 직접 판단하고, 응답은 256KB까지만 읽는다 */
object UrlConnectionWikiHttp : WikiHttp {
    private const val MAX_BYTES = 256 * 1024

    override suspend fun get(url: String, headers: Map<String, String>): WikiHttpResponse = withContext(Dispatchers.IO) {
        if (!WikiHosts.allowed(url)) throw IOException("not allowed")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            val body = if (code == HttpURLConnection.HTTP_OK) {
                conn.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_BYTES) throw IOException("too large")
                    }
                    out.toString("UTF-8")
                }
            } else {
                null
            }
            WikiHttpResponse(code, body, conn.getHeaderField("Location"))
        } finally {
            conn.disconnect()
        }
    }
}
