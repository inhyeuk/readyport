package com.readyport.attractions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// packs/schema/attractions.schema.json 과 같은 모양. **관대하게 읽는다**(SPEC_v5 §4.8):
// - enum 성격의 값은 모두 String으로 읽고, 모르는 값은 AttractionsMapper가 숨기거나 안전한 쪽으로 본다.
// - 필수는 doc_type·regions·attractions 뿐. 나머지는 모두 기본값이 있어 빠져도 깨지지 않는다.
// - 값을 추가하거나 선택 필드를 더해도 schema_version 1을 유지한다(SCHEMA_RULES). 앱이 읽는 최대 스키마는 아래 상수.

const val SUPPORTED_ATTRACTIONS_SCHEMA = 1

/** 관대한 JSON: 모르는 키 무시, 잘못된 값은 기본값으로, null은 기본값으로 */
val AttractionsJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

@Serializable
data class AttractionsDoc(
    @SerialName("doc_type") val docType: String,
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val country: String = "",
    val version: String = "",
    val release: String = "",
    /** debug 샘플(서명하지 않음)만 true — 화면 맨 위에 '샘플 데이터' 띠 */
    val sample: Boolean = false,
    @SerialName("compact_country") val compactCountry: Boolean = false,
    @SerialName("advisory_basis") val advisoryBasis: AdvisoryBasisDto? = null,
    @SerialName("unmapped_airports") val unmappedAirports: List<UnmappedAirportDto> = emptyList(),
    @SerialName("upcoming_regions") val upcomingRegions: List<NamedAreaDto> = emptyList(),
    @SerialName("excluded_areas") val excludedAreas: List<ExcludedAreaDto> = emptyList(),
    val regions: List<RegionDto>,
    val attractions: List<AttractionDto>,
    val retired: List<RetiredDto> = emptyList(),
    val sources: List<AttractionSourceDto> = emptyList(),
)

@Serializable
data class AdvisoryBasisDto(
    @SerialName("pack_version") val packVersion: String = "",
    @SerialName("safety_last_verified") val safetyLastVerified: String = "",
    @SerialName("advisory_sha256") val advisorySha256: String = "",
)

@Serializable
data class UnmappedAirportDto(val code: String = "", val reason: String = "", @SerialName("reason_ko") val reasonKo: String = "")

@Serializable
data class NamedAreaDto(@SerialName("name_ko") val nameKo: String = "", @SerialName("aliases_ko") val aliasesKo: List<String> = emptyList())

@Serializable
data class ExcludedAreaDto(
    @SerialName("name_ko") val nameKo: String = "",
    @SerialName("aliases_ko") val aliasesKo: List<String> = emptyList(),
    val level: String = "",
)

@Serializable
data class AdvisoryDto(val level: String = "", val source: String = "", @SerialName("last_verified") val lastVerified: String = "")

@Serializable
data class HubDto(@SerialName("name_ko") val nameKo: String = "", val lat: Double? = null, val lng: Double? = null, val qid: String? = null)

@Serializable
data class RegionDto(
    val id: String,
    val order: Int = 0,
    @SerialName("name_ko") val nameKo: String = "",
    @SerialName("name_en") val nameEn: String = "",
    @SerialName("aliases_ko") val aliasesKo: List<String> = emptyList(),
    @SerialName("group_ko") val groupKo: String = "",
    val kind: String = "base",
    @SerialName("base_regions") val baseRegions: List<String> = emptyList(),
    @SerialName("note_ko") val noteKo: String? = null,
    @SerialName("water_crossing") val waterCrossing: Boolean = false,
    val hub: HubDto? = null,
    val airports: List<String> = emptyList(),
    val advisory: AdvisoryDto? = null,
    @SerialName("advisory_watch_ko") val advisoryWatchKo: List<String> = emptyList(),
)

@Serializable
data class NamesDto(
    val ko: String = "",
    @SerialName("ko_paren") val koParen: String? = null,
    val en: String = "",
    val local: String? = null,
    @SerialName("local_short") val localShort: String? = null,
    @SerialName("local_lang") val localLang: String? = null,
    val source: String = "",
)

@Serializable
data class TagDto(val id: String = "", val source: String = "", @SerialName("last_verified") val lastVerified: String = "")

@Serializable
data class GeoDto(
    val kind: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val source: String = "",
    @SerialName("last_verified") val lastVerified: String = "",
)

@Serializable
data class SourcedTextDto(val text: String = "", val source: String = "", @SerialName("last_verified") val lastVerified: String = "")

@Serializable
data class NearestLocalDto(val text: String = "", val qid: String? = null, val source: String = "")

@Serializable
data class AccessDto(
    val modes: List<String> = emptyList(),
    @SerialName("nearest_ko") val nearestKo: String? = null,
    @SerialName("nearest_local") val nearestLocal: NearestLocalDto? = null,
    val source: String = "",
    @SerialName("last_verified") val lastVerified: String = "",
)

@Serializable
data class ClaimDto(val id: String = "", @SerialName("text_ko") val textKo: String = "", val source: String = "", @SerialName("last_verified") val lastVerified: String = "")

@Serializable
data class FactsDto(
    val kind: String = "facility",
    val entry: String = "unknown",
    val booking: String = "none",
    @SerialName("booking_note_ko") val bookingNoteKo: String? = null,
    @SerialName("regular_closed") val regularClosed: List<String> = emptyList(),
    @SerialName("closed_note_ko") val closedNoteKo: String? = null,
    @SerialName("visit_note_ko") val visitNoteKo: String? = null,
    val source: String = "",
    @SerialName("last_verified") val lastVerified: String = "",
)

@Serializable
data class SeasonalDto(val kind: String = "", @SerialName("text_ko") val textKo: String = "", val source: String = "", @SerialName("last_verified") val lastVerified: String = "")

@Serializable
data class StatusDto(val value: String = "", @SerialName("note_ko") val noteKo: String? = null, val source: String = "", @SerialName("last_verified") val lastVerified: String = "")

@Serializable
data class DesignationDto(val kind: String = "", @SerialName("name_ko") val nameKo: String = "", val source: String = "")

@Serializable
data class RankDto(val order: Int = Int.MAX_VALUE, val designations: List<DesignationDto> = emptyList())

/** 사진(⟦결정 D3⟧ B·C 대비 — 첫 판은 없음). 있으면 상세 머리에 그리고 TASL을 사진 바로 아래에 둔다 */
@Serializable
data class PhotoDto(
    val file: String = "",
    val title: String = "",
    val author: String = "",
    val license: String = "",
    @SerialName("license_url") val licenseUrl: String = "",
    @SerialName("source_url") val sourceUrl: String = "",
    val changes: String? = null,
)

@Serializable
data class AttractionDto(
    val id: String,
    val names: NamesDto = NamesDto(),
    @SerialName("aliases_ko") val aliasesKo: List<String> = emptyList(),
    @SerialName("aliases_en") val aliasesEn: List<String> = emptyList(),
    @SerialName("mentions_ko") val mentionsKo: List<String> = emptyList(),
    val region: String = "",
    @SerialName("area_ko") val areaKo: String? = null,
    val category: String = "",
    val tags: List<TagDto> = emptyList(),
    val geo: GeoDto? = null,
    @SerialName("address_local") val addressLocal: SourcedTextDto? = null,
    val access: AccessDto? = null,
    @SerialName("official_url") val officialUrl: String? = null,
    @SerialName("summary_ko") val summaryKo: String = "",
    @SerialName("body_ko") val bodyKo: List<String> = emptyList(),
    val claims: List<ClaimDto> = emptyList(),
    @SerialName("tips_ko") val tipsKo: List<SourcedTextDto> = emptyList(),
    val facts: FactsDto? = null,
    val seasonal: List<SeasonalDto> = emptyList(),
    val risk: List<String> = emptyList(),
    val status: StatusDto? = null,
    val advisory: AdvisoryDto? = null,
    val rank: RankDto? = null,
    val photo: PhotoDto? = null,
    @SerialName("photo_link") val photoLink: String? = null,
    val source: String = "",
    @SerialName("last_verified") val lastVerified: String = "",
)

@Serializable
data class RetiredDto(
    val id: String = "",
    val reason: String = "",
    @SerialName("replaced_by") val replacedBy: String? = null,
    @SerialName("note_ko") val noteKo: String? = null,
    val source: String = "",
    val date: String = "",
)

@Serializable
data class AttractionSourceDto(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val use: String = "",
    val license: String? = null,
    @SerialName("attribution_required") val attributionRequired: Boolean = false,
)
