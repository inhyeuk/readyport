package com.readyport.attractions

import com.readyport.pack.Advisory
import com.readyport.pack.CountryPack
import com.readyport.pack.PackVersion
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 목록 맨 위로 끌어올린 이유 (§3.1 LiftAnchor) */
enum class LiftReason { Stay, Airport }

/** 끌어올리기 판단에 쓰는 여행 정보 — 기기 안 화면 상태에만 쓰고 저장·전송하지 않는다 */
data class TripContext(
    /** 이 나라 여행이 진행 중인지 (오늘 묵는 곳은 여행 중일 때만) */
    val inProgress: Boolean = false,
    /** 오늘 묵는 곳 좌표 (Stays.on(stays, today)) */
    val stayLat: Double? = null,
    val stayLng: Double? = null,
    /** 이 나라 여행의 도착 공항(IATA) */
    val arrivalAirport: String? = null,
)

data class LiftAnchor(val regionId: String, val reason: LiftReason)

/** 지역 묶음 하나 = 목록의 가로줄 머리 + 지역 카드 한 장 */
data class RegionGroup(
    val region: Region,
    val places: List<Attraction>,
    /** 오늘 묵는 곳·도착 공항으로 맨 위에 올린 지역(딸린 daytrip은 null — 머리 태그는 거점에만) */
    val lifted: LiftReason?,
    /** 지역 안이 모두 2단계면 머리 아래 한 번 주의 띠(그러면 줄마다 태그는 없다) */
    val wholeLevel2: Boolean,
)

/**
 * 지역 묶음과 정렬 (SPEC_v5 §3.1·§5.7) — 목록·검색·찜 공용. 결정적(같은 입력 → 같은 순서).
 * 정렬 키: base = (order, 0, order), daytrip = (거점 order, 1, 자기 order). 끌어올린 지역은 order 대신 -1.
 * 거점 = 끌어올린 지역 가운데 base_regions에 든 첫 번째, 없으면 base_regions[0]. 거점이 걸러 보기로 숨어도 daytrip은 그 자리를 지킨다.
 */
object RegionGrouping {
    /** 오늘 묵는 곳에서 이 거리(직선) 안의 base hub만 올린다 — 숫자는 화면에 보이지 않는다 */
    const val STAY_LIFT_KM = 40.0

    data class SortKey(val primary: Int, val daytrip: Int, val own: Int) : Comparable<SortKey> {
        override fun compareTo(other: SortKey): Int = compareValuesBy(this, other, { it.primary }, { it.daytrip }, { it.own })
    }

    fun sortKey(region: Region, regions: Map<String, Region>, lifted: String?): SortKey {
        fun primaryOf(r: Region?): Int = when {
            r == null -> Int.MAX_VALUE
            r.id == lifted -> -1
            else -> r.order
        }
        return if (region.kind == RegionKind.Daytrip) {
            val anchor = region.baseRegions.firstOrNull { it == lifted } ?: region.baseRegions.firstOrNull()
            SortKey(primaryOf(anchor?.let { regions[it] }), 1, region.order)
        } else {
            SortKey(primaryOf(region), 0, region.order)
        }
    }

    /**
     * 끌어올릴 지역: ① 여행 중이고 오늘 묵는 곳 좌표가 있으면 가장 가까운 base hub(40km 안) ② 아니면 도착 공항이 든 base 지역 ③ 없음.
     * 좁은 나라(compact, SG)에서는 하지 않는다.
     */
    fun liftAnchor(catalog: AttractionsCatalog, trip: TripContext?): LiftAnchor? {
        if (trip == null || catalog.compact) return null
        val bases = catalog.regions.filter { it.kind == RegionKind.Base }
        if (trip.inProgress && trip.stayLat != null && trip.stayLng != null) {
            val nearest = bases.filter { it.hubLat != null && it.hubLng != null }
                .map { it to haversineKm(trip.stayLat, trip.stayLng, it.hubLat!!, it.hubLng!!) }
                .minByOrNull { it.second }
            if (nearest != null && nearest.second <= STAY_LIFT_KM) return LiftAnchor(nearest.first.id, LiftReason.Stay)
        }
        val airport = trip.arrivalAirport ?: return null
        return bases.firstOrNull { airport in it.airports }?.let { LiftAnchor(it.id, LiftReason.Airport) }
    }

    /** 도착 공항 근처 지역이 아직 준비 중이면 그 공항 코드 — 개수 줄 아래 Info 한 줄 */
    fun upcomingArrivalAirport(catalog: AttractionsCatalog, trip: TripContext?): String? {
        val airport = trip?.arrivalAirport ?: return null
        if (catalog.compact) return null
        return catalog.unmappedAirports.firstOrNull { it.code == airport && it.reason == "upcoming" }?.code
    }

    /**
     * [places]를 지역별로 묶는다. 0곳인 지역은 그리지 않는다. [scores]가 있으면(검색 중) 지역 안을 점수 순, 아니면 rank.order → 가나다.
     */
    fun group(
        catalog: AttractionsCatalog,
        places: List<Attraction>,
        anchor: LiftAnchor? = null,
        scores: Map<String, Int>? = null,
    ): List<RegionGroup> {
        val regions = catalog.regions.associateBy { it.id }
        val lifted = anchor?.regionId
        return places.groupBy { it.regionId }
            .mapNotNull { (rid, list) -> regions[rid]?.let { it to list } }
            .sortedWith(compareBy<Pair<Region, List<Attraction>>> { sortKey(it.first, regions, lifted) }.thenBy { it.first.id })
            .map { (region, list) ->
                val sorted = if (scores != null) {
                    list.sortedWith(compareByDescending<Attraction> { scores[it.id] ?: 0 }.thenBy { it.rankOrder }.thenBy { it.nameKo })
                } else {
                    list.sortedWith(compareBy<Attraction> { it.rankOrder }.thenBy { it.nameKo })
                }
                RegionGroup(
                    region = region,
                    places = sorted,
                    lifted = if (region.id == lifted) anchor.reason else null,
                    wholeLevel2 = sorted.isNotEmpty() && sorted.all { it.advisory.level == AdvisoryLevel.Two },
                )
            }
    }

    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0088
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(sqrt(a))
    }
}

/**
 * 나라 안내(pack)와 관광지의 여행경보가 어긋날 수 있을 때 — **버전이 더 새 쪽을 따른다** (§5.7).
 * (a) 기기 pack < basis: 관광지가 더 새 경보로 서명됨 → 관광지 단계 그대로 (b) 같음 → 그대로
 * (c) 기기 pack > basis이고 경보 문단 해시가 다를 때만 → 관광지 단계 표시를 숨기고 '여행경보가 바뀌었을 수 있어요' 안내,
 *     3단계 이상 문단에 지역 watch 낱말이 나오면 그 지역을 숨긴다. 생활 안내 문장만 고친 경우(해시 같음)는 아무것도 하지 않는다.
 */
data class AdvisoryState(
    /** (c): 단계 표시(2단계 띠·태그)를 숨기고 맨 위에 안내 한 줄 */
    val changed: Boolean,
    /** 안내 줄의 확인일 = 기기 pack safety 확인일 */
    val packVerified: String?,
    /** watch 낱말이 3단계 이상 문단에 나와 숨기는 지역 */
    val hiddenRegions: Set<String>,
) {
    companion object {
        val Normal = AdvisoryState(false, null, emptySet())

        fun evaluate(catalog: AttractionsCatalog, pack: CountryPack?): AdvisoryState {
            val basis = catalog.advisoryBasis ?: return Normal
            if (pack == null || basis.packVersion.isBlank()) return Normal
            if (PackVersion.compare(pack.version, basis.packVersion) <= 0) return Normal
            val paragraphs = Advisory.paragraphs(pack)
            val hash = paragraphs?.let { Advisory.hashOf(it) }
            if (hash != null && hash == basis.advisorySha256) return Normal
            val high = paragraphs.orEmpty().filter { Advisory.isHighAdvisory(it) }
            val hidden = catalog.regions.filter { r -> r.watchKo.any { w -> high.any { w in it } } }.map { it.id }.toSet()
            val verified = pack.sections.firstOrNull { it.id == "safety" }?.lastVerified
            return AdvisoryState(true, verified, hidden)
        }
    }
}
