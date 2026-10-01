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
    /** 한국 전원(어댑터가 필요한지 비교용) */
    @SerialName("home_power") val homePower: PowerInfo? = null,
    /** 모든 여행에 공통인 꼭 챙길 물건 규칙 (PRD 5.10) */
    val essentials: List<EssentialRule> = emptyList(),
    /** 귀국할 때 확인할 공식 안내(관세청·검역본부) */
    @SerialName("return_links") val returnLinks: List<OfficialLink> = emptyList(),
    /** 귀국 면세 한도 등 요약 (출처·확인일 포함) */
    @SerialName("return_facts") val returnFacts: List<SourcedText> = emptyList(),
)

@Serializable
data class SourcedText(@SerialName("text_ko") val textKo: String, val source: String, @SerialName("last_verified") val lastVerified: String)

/**
 * 전원 정보. 플러그 모양은 공식 출처의 설명을 그대로 옮긴다(형 문자는 출처가 밝힐 때만).
 * kr_plug_fits: 한국 플러그(둥근 핀 2개)가 그대로 맞는지. 출처로 확인되지 않으면 null.
 */
@Serializable
data class PowerInfo(
    @SerialName("plug_ko") val plugKo: String,
    @SerialName("kr_plug_fits") val krPlugFits: Boolean? = null,
    val voltage: String,
    val frequency: String,
    val source: String,
    @SerialName("last_verified") val lastVerified: String,
)

/**
 * 꼭 챙길 물건 규칙. 추천 순서는 목록 순서 그대로 — 수수료 때문에 바꾸지 않는다 (PRD 11.2).
 * condition: always / plug_differs(콘센트 모양이 다를 때) / voltage_differs(전압이 다를 때)
 */
@Serializable
data class EssentialRule(
    val id: String,
    @SerialName("name_ko") val nameKo: String,
    @SerialName("reason_ko") val reasonKo: String,
    val condition: String = "always",
    /** 규정 배지 (예: carry_on_only = 기내 반입만) */
    @SerialName("rule_badge") val ruleBadge: String? = null,
    val link: EssentialLink? = null,
    val source: String? = null,
    @SerialName("last_verified") val lastVerified: String? = null,
)

/**
 * type: affiliate(물건·여행 서비스, 앱에 '수수료 링크' 표시) / official_info(보험·환전·카드 — 수수료 없음)
 * 보험·금융 상품에는 affiliate를 쓰지 않는다 (작업 규칙 11)
 */
@Serializable
data class EssentialLink(val type: String, val url: String, @SerialName("label_ko") val labelKo: String, val partner: String? = null)

@Serializable
data class OfficialLink(val id: String, @SerialName("label_ko") val labelKo: String, val url: String)

/** 쇼핑 리스트 항목 (PRD 11.3). 브랜드명·상품 사진 없음 */
@Serializable
data class ShoppingItem(
    val id: String,
    /** food / daily / souvenir */
    val category: String,
    val names: Names,
    @SerialName("where_ko") val whereKo: String? = null,
    @SerialName("why_ko") val whyKo: String,
    /** allowed / caution / prohibited — 관세청·검역본부 기준 */
    @SerialName("import_status") val importStatus: String,
    @SerialName("import_note_ko") val importNoteKo: String? = null,
    val source: String,
    @SerialName("import_source") val importSource: String,
    @SerialName("last_verified") val lastVerified: String,
)

/** 인기 여행지 순위 (rankings/latest.json, ARCHITECTURE 10.3). 공공 통계를 받기 전에는 게시하지 않는다 */
@Serializable
data class Rankings(
    val version: String,
    val basis: RankingBasis,
    val weights: Map<String, Double>,
    val items: List<RankingItem>,
)

@Serializable
data class RankingBasis(@SerialName("air_month") val airMonth: String, @SerialName("search_week") val searchWeek: String? = null)

@Serializable
data class RankingItem(
    @SerialName("city_id") val cityId: String,
    @SerialName("name_ko") val nameKo: String,
    val country: String,
    val rank: Int,
    @SerialName("prev_rank") val prevRank: Int? = null,
    val reasons: List<String> = emptyList(),
    @SerialName("visa_free_kr") val visaFreeKr: Boolean? = null,
    @SerialName("flight_hours") val flightHours: Double? = null,
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
    val power: PowerInfo? = null,
    val shopping: List<ShoppingItem> = emptyList(),
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
    /** 비자를 온라인으로 신청하는 길 (있을 때만) */
    val apply: VisaApply? = null,
)

/**
 * 비자 온라인 신청. 앱은 [form] 양식의 입력만 돕고, 신청 버튼·결제는 사람이 직접 한다.
 * 예: 인도네시아 e-VOA는 All Indonesia 입국 신고를 낸 뒤 요약 화면에서 신청한다.
 */
@Serializable
data class VisaApply(
    val form: String,
    @SerialName("name_ko") val nameKo: String,
    @SerialName("official_url") val officialUrl: String? = null,
    @SerialName("fee_ko") val feeKo: String,
    @SerialName("steps_ko") val stepsKo: List<String>,
    @SerialName("warning_ko") val warningKo: String? = null,
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
    /** 원어민 검수 여부 (팩 데이터 — 앱 화면에는 표시하지 않는다: 운영자 결정 2, 2026-10-01. 출시 전 원어민 검수 C10은 할 일로 남음) */
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
