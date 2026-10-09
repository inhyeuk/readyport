package com.readyport.attractions.rating

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * 관광지 상세의 **Google 별점**(사장님 결정 2026-10-09 ①: 상세를 열 때 실시간, 저장 안 함, 'Google 제공').
 *
 * - Places API (New) Place Details `GET https://places.googleapis.com/v1/places/{place id}` 한 번, 필드는 별점·리뷰 수·지도 주소만
 *   (X-Goog-FieldMask). 관광지 파일에는 place ID 만 있다(약관상 저장 가능한 값). 별점은 **이번 앱 실행 동안 메모리에만**(디스크 없음).
 * - 받는 곳은 [PlacesHosts] 허용 목록(places.googleapis.com 의 그 주소 모양)뿐이고 리디렉션은 따라가지 않는다.
 * - 키는 지도 SDK 와 같은 키(BuildConfig.MAPS_API_KEY — 빌드 때 저장소 밖에서 넣는다). Android 앱 제한 키라
 *   X-Android-Package·X-Android-Cert(서명 인증서 SHA-1) 머리글을 함께 보낸다.
 * - 보내는 것: place ID·필드 이름·키·앱 패키지와 인증서 지문. 이용자 정보는 보내지 않는다(IP 주소 등 통신 정보는 Google에 간다 — 처리방침 4절).
 * - 실패하면 조용히 숨긴다(null).
 */
object PlacesHosts {
    private val Allowed = Regex("""^https://places\.googleapis\.com/v1/places/[A-Za-z0-9_-]{10,300}$""")
    private val PlaceId = Regex("""^[A-Za-z0-9_-]{10,300}$""")

    fun allowed(url: String): Boolean = Allowed.matches(url)

    /** place ID → 주소 (모양이 틀리면 null — 요청하지 않는다) */
    fun detailsUrl(placeId: String): String? =
        "https://places.googleapis.com/v1/places/$placeId".takeIf { PlaceId.matches(placeId) && allowed(it) }

    /** 받은 지도 주소는 Google 지도 주소일 때만 연다 */
    fun mapsLinkAllowed(url: String?): Boolean {
        if (url == null || !url.startsWith("https://")) return false
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
        return host == "maps.google.com" || host == "www.google.com" || host == "google.com" || host == "maps.app.goo.gl"
    }

    const val FIELD_MASK = "rating,userRatingCount,googleMapsUri"
}

/** 상세에 보일 Google 별점 */
data class GoogleRating(val rating: Double, val count: Int, val mapsUri: String?)

/** HTTP 응답 하나 (테스트에서 가짜로 바꿔 끼운다) */
data class PlacesHttpResponse(val code: Int, val body: String?)

fun interface PlacesHttp {
    suspend fun get(url: String, headers: Map<String, String>): PlacesHttpResponse
}

@Serializable
private data class PlaceDto(val rating: Double? = null, val userRatingCount: Long? = null, val googleMapsUri: String? = null)

class GoogleRatingClient(
    private val apiKey: String,
    private val packageName: String,
    /** 서명 인증서 SHA-1 (대문자 16진수, 콜론 없음). 못 구하면 null — 머리글 없이 보낸다(제한 키면 거절된다) */
    private val certSha1: () -> String?,
    private val http: PlacesHttp = UrlConnectionPlacesHttp,
) {
    private val cache = object : LinkedHashMap<String, GoogleRating>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GoogleRating>?): Boolean = size > CACHE_SIZE
    }

    val enabled: Boolean get() = apiKey.isNotBlank()

    /** 요청 머리글 (키·필드·앱 제한 확인용) */
    fun headers(): Map<String, String> = buildMap {
        put("X-Goog-Api-Key", apiKey)
        put("X-Goog-FieldMask", PlacesHosts.FIELD_MASK)
        put("X-Android-Package", packageName)
        certSha1()?.let { put("X-Android-Cert", it) }
    }

    suspend fun rating(placeId: String?): GoogleRating? {
        if (!enabled || placeId == null) return null
        synchronized(cache) { cache[placeId] }?.let { return it }
        val url = PlacesHosts.detailsUrl(placeId) ?: return null
        val resp = try {
            http.get(url, headers())
        } catch (e: IOException) {
            return null
        }
        if (resp.code != HttpURLConnection.HTTP_OK) return null
        val parsed = parse(resp.body) ?: return null
        synchronized(cache) { cache[placeId] = parsed }
        return parsed
    }

    companion object {
        private const val CACHE_SIZE = 50
        private val PlaceJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            isLenient = true
        }

        /** 응답 글 → 별점 (순수 — 테스트). 별점이 없거나 0~5 밖이면 null. 리뷰 수는 없으면 0 */
        fun parse(body: String?): GoogleRating? {
            if (body.isNullOrBlank()) return null
            val dto = runCatching { PlaceJson.decodeFromString(PlaceDto.serializer(), body) }.getOrNull() ?: return null
            val rating = dto.rating?.takeIf { !it.isNaN() && it in 1.0..5.0 } ?: return null
            val count = (dto.userRatingCount ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            return GoogleRating(Math.round(rating * 10) / 10.0, count, dto.googleMapsUri?.takeIf { PlacesHosts.mapsLinkAllowed(it) })
        }
    }
}

/** 리뷰 수 쉬운 표기: 1만 미만은 `1,234`, 1만부터 `1.2만`(소수 한 자리, .0은 뺀다) */
fun koreanCount(n: Int): String = when {
    n < 10_000 -> "%,d".format(Locale.ROOT, n)
    else -> {
        val tenth = n / 1_000 // 1만 = 10, 1.2만 = 12
        val whole = tenth / 10
        val frac = tenth % 10
        val head = "%,d".format(Locale.ROOT, whole)
        if (frac == 0) "${head}만" else "$head.${frac}만"
    }
}

/** 이 앱 서명 인증서의 SHA-1 (대문자 16진수, 콜론 없음) — Android 앱 제한 키 머리글 X-Android-Cert */
object AppSigning {
    @Volatile private var cached: String? = null

    fun sha1(context: Context): String? = cached ?: runCatching {
        val pm = context.packageManager
        val cert: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 지금 서명한 인증서(Play 앱 서명이면 Google이 관리하는 앱 서명 키)
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()?.toByteArray()
        }
        cert?.let { hex(MessageDigest.getInstance("SHA-1").digest(it)) }
    }.getOrNull()?.also { cached = it }

    /** 바이트 → 대문자 16진수(콜론 없음) */
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(Locale.ROOT, it) }
}

/** 실제 네트워크: HttpURLConnection(새 라이브러리 없음). 허용 목록 밖 주소는 열지 않고, 응답은 64KB까지만 읽는다 */
object UrlConnectionPlacesHttp : PlacesHttp {
    private const val MAX_BYTES = 64 * 1024

    override suspend fun get(url: String, headers: Map<String, String>): PlacesHttpResponse = withContext(Dispatchers.IO) {
        if (!PlacesHosts.allowed(url)) throw IOException("not allowed")
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
            PlacesHttpResponse(code, body)
        } finally {
            conn.disconnect()
        }
    }
}
