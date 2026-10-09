package com.readyport.pack

import android.content.res.AssetManager
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.readyport.doc.ocr.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 내장 기본 팩: assets/packs/ */
class AssetBundledPacks(private val assets: AssetManager) : BundledPacks {
    override fun read(path: String): ByteArray? =
        runCatching { assets.open("packs/$path").use { it.readBytes() } }.getOrNull()
}

/**
 * Firebase Hosting에서 팩 받기. HTTPS만, 크기 제한. 개인정보는 보내지 않는다(요청에 여행 정보 없음).
 */
class HttpPackRemote(private val baseUrl: String = BASE_URL) : PackRemote {
    override suspend fun fetch(path: String): ByteArray = withContext(Dispatchers.IO) {
        val url = URL(baseUrl + path)
        if (url.protocol != "https") throw IOException("https only")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) throw PackNotFoundException("HTTP 404")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("too large")
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val BASE_URL = "https://readyport-app.web.app/packs/"
        private const val MAX_BYTES = 2 * 1024 * 1024
    }
}

/** 서버에 올라간 최신 팩 버전 (ARCHITECTURE 9.5: pack_version_{country}, index_version) */
interface PackVersionSource {
    /** 최신 값을 받아 적용한다. 실패(오프라인 등)면 false */
    suspend fun refresh(): Boolean
    fun indexVersion(): String?
    fun packVersion(country: String): String?
    fun recipeVersion(formId: String): String?
    /** 관광지 파일 버전 (RC `attractions_version_<CC>`, SPEC_v5 §4.1). 없으면 null — 그 나라 관광지는 받지 않는다 */
    fun attractionsVersion(country: String): String? = null
    /** 안전 스위치: true면 이 양식은 자동 입력 없이 수동 모드로만 (ARCHITECTURE 9.5) */
    fun autofillKilled(formId: String): Boolean
}

class RemoteConfigVersions(private val rc: FirebaseRemoteConfig) : PackVersionSource {
    init {
        rc.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 })
    }

    override suspend fun refresh(): Boolean = runCatching { rc.fetchAndActivate().await(); true }.getOrDefault(false)

    override fun indexVersion(): String? = rc.getString("index_version").ifBlank { null }

    override fun packVersion(country: String): String? = rc.getString("pack_version_$country").ifBlank { null }

    override fun recipeVersion(formId: String): String? = rc.getString("recipe_version_$formId").ifBlank { null }

    override fun attractionsVersion(country: String): String? = rc.getString("attractions_version_$country").ifBlank { null }

    override fun autofillKilled(formId: String): Boolean = rc.getBoolean("kill_autofill_$formId")
}
