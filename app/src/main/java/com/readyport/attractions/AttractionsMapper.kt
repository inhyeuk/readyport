package com.readyport.attractions

/**
 * 화면이 쓰는 관광지 모델. [AttractionsMapper]가 관대하게 읽은 [AttractionsDoc]을 이 모양으로 바꾼다 —
 * 모르는 값은 여기서 이미 숨기거나 안전한 쪽으로 정해져 있어 화면은 값을 다시 따지지 않는다.
 */

/** 종류 7개 (SPEC_v5 §2.2). 순서 = 타일·칩 순서. 값은 packs/schema/attractions.enums.json `categories`와 같다 */
enum class Category(val key: String) {
    Heritage("heritage"),
    Nature("nature"),
    SeaIsland("sea_island"),
    CityView("city_view"),
    MarketStreet("market_street"),
    Museum("museum"),
    ThemePark("theme_park"),
    ;

    companion object {
        fun of(key: String?): Category? = entries.firstOrNull { it.key == key }
    }
}

/** 태그 (사실만, §2.4). 앱이 모르는 태그는 숨긴다 */
enum class Tag(val key: String) {
    Unesco("unesco"),
    Indoor("indoor"),
    FreeEntry("free_entry"),
    BookingRequired("booking_required"),
    DressCode("dress_code"),
    Night("night"),
    Stairs("stairs"),
    StepFree("step_free"),
    CableCar("cable_car"),
    MountainView("mountain_view"),
    Seafront("seafront"),
    ForeignerPrice("foreigner_price"),
    HotSpring("hot_spring"),
    ;

    companion object {
        fun of(key: String?): Tag? = entries.firstOrNull { it.key == key }
    }
}

enum class AccessMode(val key: String) {
    Train("train"), Metro("metro"), Bus("bus"), Boat("boat"), CarOnly("car_only"), WalkFromCenter("walk_from_center");

    companion object {
        fun of(key: String?): AccessMode? = entries.firstOrNull { it.key == key }
    }
}

/** 쉬는 요일 — 월~일 + 쉬는 날 없음 + 비정기. `unknown`·모르는 값은 그리지 않는다 */
enum class ClosedDay(val key: String) {
    Mon("mon"), Tue("tue"), Wed("wed"), Thu("thu"), Fri("fri"), Sat("sat"), Sun("sun"), NoneDay("none"), Irregular("irregular");

    companion object {
        fun of(key: String?): ClosedDay? = entries.firstOrNull { it.key == key }
    }
}

enum class Entry { Free, Paid, Unknown }

enum class Booking { None, Recommended, Required }

/** 운영 상태. 모르는 값은 '열려 있지 않음'으로 보고 '공식 사이트에서 확인하세요' 띠를 단다 */
enum class OpenStatus { Open, Partial, TempClosed, Unknown }

enum class Risk(val key: String) {
    Volcano("volcano"), PostDisaster("post_disaster"), Seasonal("seasonal"), Renovation("renovation");

    companion object {
        fun of(key: String?): Risk? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 여행경보 단계(§5.7 표시 규칙). [Hidden] = special·3·4 — 목록·검색에서 숨긴다.
 * [Unknown] = 앱이 모르는 값 — 보여 주되 '여행경보를 꼭 확인하세요' 띠를 붙인다.
 */
enum class AdvisoryLevel {
    None, One, Two, Hidden, Unknown;

    companion object {
        fun of(raw: String?): AdvisoryLevel = when (raw) {
            "none" -> None
            "1" -> One
            "2" -> Two
            "special", "3", "4" -> Hidden
            else -> Unknown
        }
    }
}

enum class RegionKind { Base, Daytrip }

data class AdvisoryInfo(val level: AdvisoryLevel, val source: String, val lastVerified: String)

data class Region(
    val id: String,
    val order: Int,
    val nameKo: String,
    val nameEn: String,
    val aliasesKo: List<String>,
    val groupKo: String,
    val kind: RegionKind,
    val baseRegions: List<String>,
    val noteKo: String?,
    val waterCrossing: Boolean,
    val hubLat: Double?,
    val hubLng: Double?,
    val airports: List<String>,
    val advisory: AdvisoryInfo,
    val watchKo: List<String>,
)

/** 출처 하나 + 확인일 (상세 출처 줄) */
data class SourcedLine(val text: String, val source: String, val lastVerified: String)

data class Facts(
    val publicSpace: Boolean,
    val entry: Entry,
    val booking: Booking,
    val bookingNoteKo: String?,
    val closedDays: List<ClosedDay>,
    val closedNoteKo: String?,
    val visitNoteKo: String?,
    val source: String,
    val lastVerified: String,
)

data class Attraction(
    val country: String,
    val id: String,
    val nameKo: String,
    val koParen: String?,
    val nameEn: String,
    val local: String?,
    val localShort: String?,
    val aliasesKo: List<String>,
    val aliasesEn: List<String>,
    val mentionsKo: List<String>,
    val regionId: String,
    val areaKo: String?,
    val category: Category,
    val tags: List<Tag>,
    val lat: Double?,
    val lng: Double?,
    val addressLocal: String?,
    val accessModes: List<AccessMode>,
    val nearestKo: String?,
    val nearestLocal: String?,
    val accessSource: String?,
    val officialUrl: String?,
    val summaryKo: String,
    val bodyKo: List<String>,
    val claimSources: List<SourcedLine>,
    val tips: List<SourcedLine>,
    val facts: Facts?,
    val seasonal: List<SourcedLine>,
    val risks: List<Risk>,
    val status: OpenStatus,
    val statusNoteKo: String?,
    val statusSource: String?,
    val statusVerified: String?,
    val advisory: AdvisoryInfo,
    val rankOrder: Int,
    val photo: PhotoDto?,
    val photoLink: String?,
    /** 이 관광지 필드 가운데 가장 오래된 확인일(§5.5) — 상세 '최종 확인' */
    val oldestVerified: String,
    /** 상세 출처 목록에 보일 출처 id (중복 없이, 나온 순서) */
    val sourceIds: List<String>,
) {
    /** 전역 키 "<CC>/<id>" — 찜·가는 곳이 쓴다 */
    val key: String get() = "$country/$id"

    /** 화면 제목: 이름(+ 괄호 한자음, ⟦결정 D20⟧) */
    val title: String get() = if (koParen.isNullOrBlank()) nameKo else "$nameKo($koParen)"
}

data class Retired(val id: String, val reason: String, val replacedBy: String?, val noteKo: String?)

/** 목록·검색에서 빠졌지만 찜 목록에는 이유와 함께 보이는 항목 */
enum class HiddenReason { NeedsUpdate, Safety }

data class UnmappedAirport(val code: String, val reason: String, val reasonKo: String)

data class ExcludedArea(val nameKo: String, val aliasesKo: List<String>, val level: String)

data class AttractionsCatalog(
    val country: String,
    val version: String,
    val sample: Boolean,
    val compact: Boolean,
    val regions: List<Region>,
    /** 목록·검색에 보이는 관광지(모르는 종류·지역, 경보 3단계 이상 제외) */
    val attractions: List<Attraction>,
    /** 숨긴 항목 id → 이유 (찜 목록 안내용) */
    val hidden: Map<String, HiddenReason>,
    /** 숨긴 항목의 이름(찜 목록에 이름은 보여 준다) */
    val hiddenNames: Map<String, String>,
    val retired: Map<String, Retired>,
    val upcoming: List<NamedAreaDto>,
    val excluded: List<ExcludedArea>,
    val unmappedAirports: List<UnmappedAirport>,
    val advisoryBasis: AdvisoryBasisDto?,
    val sources: Map<String, AttractionSourceDto>,
) {
    private val byId = attractions.associateBy { it.id }
    private val regionById = regions.associateBy { it.id }

    fun attraction(id: String): Attraction? = byId[id]

    fun region(id: String): Region? = regionById[id]

    /** 곳이 있는 종류와 곳 수 (§2.1: 2곳 미만인 종류는 타일을 그리지 않는다 — 타일 쪽에서 거른다) */
    fun categoryCounts(): Map<Category, Int> = attractions.groupingBy { it.category }.eachCount()
}

object AttractionsMapper {

    /** 관대 매핑. doc_type이 attractions가 아니면 null(Malformed) */
    fun map(doc: AttractionsDoc): AttractionsCatalog? {
        if (doc.docType != "attractions") return null
        val country = doc.country
        val regions = doc.regions.filter { it.id.isNotBlank() }.map { r ->
            Region(
                id = r.id,
                order = r.order,
                nameKo = r.nameKo.ifBlank { r.nameEn },
                nameEn = r.nameEn,
                aliasesKo = r.aliasesKo,
                groupKo = r.groupKo,
                // 모르는 region.kind는 base로 (§4.8)
                kind = if (r.kind == "daytrip" && r.baseRegions.isNotEmpty()) RegionKind.Daytrip else RegionKind.Base,
                baseRegions = r.baseRegions,
                noteKo = r.noteKo?.takeIf { it.isNotBlank() },
                waterCrossing = r.waterCrossing,
                hubLat = r.hub?.lat,
                hubLng = r.hub?.lng,
                airports = r.airports,
                advisory = r.advisory.toAdvisory(),
                watchKo = r.advisoryWatchKo,
            )
        }
        val regionIds = regions.map { it.id }.toSet()
        val visible = mutableListOf<Attraction>()
        val hidden = mutableMapOf<String, HiddenReason>()
        val hiddenNames = mutableMapOf<String, String>()
        for (a in doc.attractions) {
            if (a.id.isBlank()) continue
            val category = Category.of(a.category)
            val advisory = a.advisory.toAdvisory()
            when {
                category == null || a.region !in regionIds -> hidden[a.id] = HiddenReason.NeedsUpdate
                advisory.level == AdvisoryLevel.Hidden -> hidden[a.id] = HiddenReason.Safety
                else -> visible += a.toAttraction(country, category, advisory)
            }
            if (a.id in hidden) hiddenNames[a.id] = a.names.ko.ifBlank { a.names.en }
        }
        return AttractionsCatalog(
            country = country,
            version = doc.version,
            sample = doc.sample,
            compact = doc.compactCountry,
            regions = regions,
            attractions = visible,
            hidden = hidden,
            hiddenNames = hiddenNames,
            retired = doc.retired.filter { it.id.isNotBlank() }.associate { r ->
                // 모르는 retired.reason은 editorial로 (§4.8)
                val reason = if (r.reason in RETIRED_REASONS) r.reason else "editorial"
                r.id to Retired(r.id, reason, r.replacedBy, r.noteKo)
            },
            upcoming = doc.upcomingRegions.filter { it.nameKo.isNotBlank() },
            excluded = doc.excludedAreas.filter { it.nameKo.isNotBlank() }.map { ExcludedArea(it.nameKo, it.aliasesKo, it.level) },
            unmappedAirports = doc.unmappedAirports.map { UnmappedAirport(it.code, it.reason, it.reasonKo) },
            advisoryBasis = doc.advisoryBasis,
            sources = doc.sources.associateBy { it.id },
        )
    }

    private val RETIRED_REASONS = setOf("closed", "long_closure", "safety", "editorial", "merged")

    private fun AdvisoryDto?.toAdvisory() = AdvisoryInfo(AdvisoryLevel.of(this?.level), this?.source.orEmpty(), this?.lastVerified.orEmpty())

    private fun AttractionDto.toAttraction(country: String, category: Category, advisory: AdvisoryInfo): Attraction {
        val f = facts?.let { fx ->
            Facts(
                // 모르는 facts.kind는 facility로 (§4.8)
                publicSpace = fx.kind == "public_space",
                entry = when (fx.entry) { "free" -> Entry.Free; "paid" -> Entry.Paid; else -> Entry.Unknown },
                booking = when (fx.booking) { "required" -> Booking.Required; "recommended" -> Booking.Recommended; else -> Booking.None },
                bookingNoteKo = fx.bookingNoteKo?.takeIf { it.isNotBlank() },
                closedDays = fx.regularClosed.mapNotNull { ClosedDay.of(it) },
                closedNoteKo = fx.closedNoteKo?.takeIf { it.isNotBlank() },
                visitNoteKo = fx.visitNoteKo?.takeIf { it.isNotBlank() },
                source = fx.source,
                lastVerified = fx.lastVerified,
            )
        }
        val statusValue = when (status?.value) {
            "open" -> OpenStatus.Open
            "partial" -> OpenStatus.Partial
            "temp_closed" -> OpenStatus.TempClosed
            else -> OpenStatus.Unknown
        }
        val dates = buildList {
            add(lastVerified)
            geo?.let { add(it.lastVerified) }
            claims.forEach { add(it.lastVerified) }
            tipsKo.forEach { add(it.lastVerified) }
            tags.forEach { add(it.lastVerified) }
            facts?.let { add(it.lastVerified) }
            access?.let { add(it.lastVerified) }
            addressLocal?.let { add(it.lastVerified) }
            seasonal.forEach { add(it.lastVerified) }
            status?.let { add(it.lastVerified) }
            advisory.lastVerified.let { add(it) }
        }.filter { IsoDate.matches(it) }
        val sourceIds = buildList {
            claims.forEach { add(it.source) }
            facts?.source?.let { add(it) }
            tipsKo.forEach { add(it.source) }
            tags.forEach { add(it.source) }
            access?.source?.let { add(it) }
            addressLocal?.source?.let { add(it) }
            seasonal.forEach { add(it.source) }
            status?.source?.let { add(it) }
            add(names.source)
            add(source)
        }.filter { it.isNotBlank() }.distinct()
        return Attraction(
            country = country,
            id = id,
            nameKo = names.ko.ifBlank { names.en },
            koParen = names.koParen?.takeIf { it.isNotBlank() },
            nameEn = names.en,
            local = names.local?.takeIf { it.isNotBlank() },
            localShort = names.localShort?.takeIf { it.isNotBlank() },
            aliasesKo = aliasesKo,
            aliasesEn = aliasesEn,
            mentionsKo = mentionsKo,
            regionId = region,
            areaKo = areaKo?.takeIf { it.isNotBlank() },
            category = category,
            tags = tags.mapNotNull { Tag.of(it.id) }.distinct(),
            lat = geo?.lat,
            lng = geo?.lng,
            addressLocal = addressLocal?.text?.takeIf { it.isNotBlank() },
            accessModes = access?.modes.orEmpty().mapNotNull { AccessMode.of(it) }.distinct(),
            nearestKo = access?.nearestKo?.takeIf { it.isNotBlank() },
            nearestLocal = access?.nearestLocal?.text?.takeIf { it.isNotBlank() },
            accessSource = access?.source,
            officialUrl = officialUrl?.takeIf { it.startsWith("https://") },
            summaryKo = summaryKo,
            bodyKo = bodyKo.filter { it.isNotBlank() },
            claimSources = claims.map { SourcedLine(it.textKo, it.source, it.lastVerified) },
            tips = tipsKo.filter { it.text.isNotBlank() }.map { SourcedLine(it.text, it.source, it.lastVerified) },
            facts = f,
            seasonal = seasonal.filter { it.kind == "closed" || it.kind == "open_only" }.map { SourcedLine(it.textKo, it.source, it.lastVerified) },
            risks = risk.mapNotNull { Risk.of(it) },
            status = statusValue,
            statusNoteKo = status?.noteKo?.takeIf { it.isNotBlank() },
            statusSource = status?.source,
            statusVerified = status?.lastVerified,
            advisory = advisory,
            rankOrder = rank?.order ?: Int.MAX_VALUE,
            photo = photo?.takeIf { it.file.isNotBlank() },
            photoLink = photoLink?.takeIf { it.startsWith("https://commons.wikimedia.org/") },
            oldestVerified = dates.minOrNull() ?: lastVerified,
            sourceIds = sourceIds,
        )
    }

    private val IsoDate = Regex("""\d{4}-\d{2}-\d{2}""")
}
