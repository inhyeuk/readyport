package com.readyport.pack

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// packs/schema/pack.schema.json · index.schema.json 과 1:1. 스키마를 바꾸면 여기와 SUPPORTED_SCHEMA를 같이 바꾼다.

const val SUPPORTED_SCHEMA = 1

@Serializable
data class PackIndex(
    @SerialName("schema_version") val schemaVersion: Int,
    val version: String,
    val countries: List<IndexCountry>,
    @SerialName("common_emergency") val commonEmergency: List<EmergencyContact> = emptyList(),
    val sources: List<PackSource> = emptyList(),
)

@Serializable
data class IndexCountry(
    val code: String,
    @SerialName("name_ko") val nameKo: String,
    @SerialName("name_en") val nameEn: String,
    /** 국가 팩이 있는지. false면 '안내 준비 중' */
    val pack: Boolean,
)

@Serializable
data class CountryPack(
    @SerialName("schema_version") val schemaVersion: Int,
    val country: String,
    val version: String,
    @SerialName("last_verified") val lastVerified: String,
    val names: Names,
    @SerialName("local_language") val localLanguage: LocalLanguage? = null,
    val sources: List<PackSource>,
    val requirements: List<Requirement> = emptyList(),
    val forms: List<FormInfo> = emptyList(),
    val sections: List<Section> = emptyList(),
    @SerialName("transport_apps") val transportApps: List<TransportApp> = emptyList(),
    val phrases: List<Phrase> = emptyList(),
    val emergency: List<EmergencyContact> = emptyList(),
    val embassy: Embassy? = null,
    val procedures: List<Procedure> = emptyList(),
) {
    fun source(id: String): PackSource? = sources.firstOrNull { it.id == id }
}

@Serializable data class Names(val ko: String, val en: String, val local: String)

@Serializable
data class LocalLanguage(@SerialName("name_ko") val nameKo: String, @SerialName("tts_lang") val ttsLang: String)

@Serializable data class PackSource(val id: String, val name: String, val url: String)

@Serializable
data class Requirement(
    val nationality: String,
    val purpose: String,
    val visa: String,
    @SerialName("stay_limit_days") val stayLimitDays: Int? = null,
    val forms: List<String> = emptyList(),
    @SerialName("summary_ko") val summaryKo: String,
    val source: String,
    @SerialName("last_verified") val lastVerified: String,
)

@Serializable
data class FormInfo(
    val id: String,
    @SerialName("name_ko") val nameKo: String,
    @SerialName("name_en") val nameEn: String,
    @SerialName("official_url") val officialUrl: String,
    @SerialName("fee_ko") val feeKo: String,
    @SerialName("window_ko") val windowKo: String,
    /** 도착일을 포함해 며칠 전부터 낼 수 있는지 (3 = 도착 2일 전~도착일) */
    @SerialName("window_days_including_arrival") val windowDaysIncludingArrival: Int? = null,
    val source: String,
    @SerialName("last_verified") val lastVerified: String,
)

@Serializable
data class Section(
    val id: String,
    @SerialName("title_ko") val titleKo: String,
    @SerialName("body_ko") val bodyKo: List<String>,
    val source: String,
    @SerialName("last_verified") val lastVerified: String,
)

@Serializable
data class TransportApp(val name: String, val `package`: String, @SerialName("link_type") val linkType: String)

@Serializable
data class Phrase(
    val id: String,
    val ko: String,
    val en: String,
    val local: String,
    val romanized: String? = null,
    /** 원어민 검수 여부. false면 화면에 '검수 전' 표시 */
    val reviewed: Boolean = false,
)

@Serializable
data class EmergencyContact(
    val id: String,
    @SerialName("label_ko") val labelKo: String,
    val number: String,
    @SerialName("note_ko") val noteKo: String? = null,
    val source: String,
    @SerialName("last_verified") val lastVerified: String,
)

@Serializable
data class Embassy(
    @SerialName("name_ko") val nameKo: String,
    /** 공관이 공개한 표기 그대로 */
    val address: String,
    val phone: String,
    @SerialName("emergency_phone") val emergencyPhone: String? = null,
    val source: String,
    @SerialName("last_verified") val lastVerified: String,
)

@Serializable
data class Procedure(
    val id: String,
    @SerialName("title_ko") val titleKo: String,
    @SerialName("steps_ko") val stepsKo: List<String>,
    val source: String,
    @SerialName("last_verified") val lastVerified: String,
)

/** 팩 버전 "yyyy.MM.dd-N" 비교. 형식이 틀리면 가장 낮게 본다 */
object PackVersion {
    private val re = Regex("""^(\d{4})\.(\d{2})\.(\d{2})-(\d+)$""")

    fun compare(a: String, b: String): Int = key(a).compareTo(key(b))

    fun isNewer(candidate: String, current: String?): Boolean = current == null || compare(candidate, current) > 0

    private fun key(v: String): Long {
        val m = re.matchEntire(v) ?: return -1
        val (y, mo, d, n) = m.destructured
        return ((y.toLong() * 100 + mo.toLong()) * 100 + d.toLong()) * 10_000 + n.toLong().coerceAtMost(9_999)
    }
}
