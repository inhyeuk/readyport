package com.readyport.ui.itinerary

import com.readyport.attractions.AdvisoryState
import com.readyport.attractions.Attraction
import com.readyport.attractions.AttractionsCatalog
import com.readyport.attractions.SavedAttraction
import com.readyport.itinerary.DaySlot
import com.readyport.itinerary.Itinerary
import com.readyport.itinerary.ItineraryDays
import com.readyport.itinerary.ItineraryPlanner
import com.readyport.itinerary.ItineraryStop
import com.readyport.itinerary.NightStay
import com.readyport.itinerary.Spot
import com.readyport.itinerary.TripItinerary
import com.readyport.stay.Stays
import com.readyport.trip.Trip
import com.readyport.ui.attractions.AttractionsListModel
import com.readyport.ui.attractions.SavedGone
import com.readyport.vault.StayRecord
import java.time.LocalDate

/** 일정의 한 곳(화면용). [attraction]이 null이면 정보가 없는 곳 — [gone]이 이유('앱을 업데이트하면 볼 수 있어요' 등) */
data class StopUi(
    val key: String,
    val name: String,
    val attraction: Attraction? = null,
    val regionId: String? = null,
    val regionName: String? = null,
    val gone: SavedGone? = null,
    /** 옮겨 담기 제안에서 새로 들어가는 곳 */
    val isNew: Boolean = false,
    /** 지역 중심(없으면 그 곳) — 기기 안 계산용, 화면에 숫자로 나오지 않는다 */
    val lat: Double? = null,
    val lng: Double? = null,
) {
    val spot: Spot get() = Spot(key, regionId, lat, lng)
}

/** 하루 칸(화면용) */
data class DayUi(val slot: DaySlot, val stops: List<StopUi>, val mixedFar: Boolean, val today: Boolean)

/** 찜 → 일정 옮겨 담기 제안 */
data class ProposalUi(
    /** 곳이 있는 날만(이미 있던 곳 + 새로 담는 곳) */
    val days: List<DayUi>,
    val stops: List<ItineraryStop>,
    val usedStays: Boolean,
    /** 정보가 없어(앱 업데이트 필요·문 닫음 등) 담지 않은 찜 수 */
    val skipped: Int,
)

data class ItineraryUi(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val countryName: String = "",
    /** 이 나라 관광지 파일이 있는지 (없으면 '곧 추가돼요') */
    val available: Boolean = false,
    val sample: Boolean = false,
    val days: List<DayUi> = emptyList(),
    /** 일정에 담은 곳 수 */
    val total: Int = 0,
    /** 아직 일정에 없는 이 나라 찜 수(담을 수 있는 곳) */
    val fresh: Int = 0,
    val proposal: ProposalUi? = null,
    /** 옮겨 담기를 눌렀는데 새로 담을 곳이 없을 때 */
    val allIn: Boolean = false,
    val todayIndex: Int? = null,
) {
    val today: DayUi? get() = todayIndex?.let { i -> days.firstOrNull { it.slot.index == i } }
}

/** 관광 일정 화면 값 만들기(순수 계산 — 테스트한다). 좌표는 여기서만 쓰고 결과에는 키·날만 남는다 */
object ItineraryModel {

    /** 일정·찜의 키 하나를 화면 값으로. 합쳐진 항목(retired merged)은 새 항목으로 보여 준다 */
    fun resolve(key: String, catalog: AttractionsCatalog?, advisory: AdvisoryState): StopUi {
        val id = key.substringAfter('/')
        if (catalog != null) {
            val direct = catalog.attraction(id)
            val a = direct ?: catalog.retired[id]?.takeIf { it.reason == "merged" }?.replacedBy?.let { catalog.attraction(it) }
            if (a != null && a.regionId !in advisory.hiddenRegions) {
                val region = catalog.region(a.regionId)
                return StopUi(
                    key = key,
                    name = a.title,
                    attraction = a,
                    regionId = a.regionId,
                    regionName = region?.nameKo,
                    lat = region?.hubLat ?: a.lat,
                    lng = region?.hubLng ?: a.lng,
                )
            }
        }
        val extra = AttractionsListModel.savedExtras(catalog, listOf(SavedAttraction(key, "")), advisory, key.substringBefore('/')).firstOrNull()
        return StopUi(key = key, name = extra?.name ?: id, gone = extra?.gone ?: SavedGone.Missing)
    }

    fun build(
        trip: Trip,
        itinerary: TripItinerary,
        catalog: AttractionsCatalog?,
        advisory: AdvisoryState,
        saved: List<SavedAttraction>,
        today: LocalDate,
        stays: List<StayRecord> = emptyList(),
        proposing: Boolean = false,
        countryName: String = trip.country,
    ): ItineraryUi {
        val slots = ItineraryDays.slots(trip)
        val todayIndex = ItineraryDays.todayIndex(trip, today)
        val mine = itinerary.stops.filter { it.key.substringBefore('/') == trip.country }
        val resolved = mine.associate { it.key to resolve(it.key, catalog, advisory) }
        val byDay = Itinerary.byDay(TripItinerary(mine), slots.size)
        val days = slots.map { slot ->
            val stops = byDay[slot.index].map { resolved.getValue(it.key) }
            DayUi(slot, stops, ItineraryPlanner.mixedFar(stops.filter { it.attraction != null }.map { it.spot }), slot.index == todayIndex)
        }
        // 아직 일정에 없는 이 나라 찜(찜 순서). 합쳐진 항목은 새 키로도 이미 있는 것으로 본다
        val inPlan = mine.map { it.key }.toSet() + resolved.values.mapNotNull { it.attraction?.key }
        val candidates = saved.filter { it.country == trip.country && it.key !in inPlan }
            .map { resolve(it.key, catalog, advisory) }
            .distinctBy { it.attraction?.key ?: it.key }
        val usable = candidates.filter { it.attraction != null }
        val proposal = if (proposing && usable.isNotEmpty()) {
            propose(slots, byDay, resolved, usable, stays, todayIndex, candidates.size - usable.size)
        } else {
            null
        }
        return ItineraryUi(
            loading = false,
            trip = trip,
            countryName = countryName,
            available = catalog != null,
            sample = catalog?.sample == true,
            days = days,
            total = mine.size,
            fresh = usable.size,
            proposal = proposal,
            allIn = proposing && usable.isEmpty(),
            todayIndex = todayIndex,
        )
    }

    private fun propose(
        slots: List<DaySlot>,
        byDay: List<List<ItineraryStop>>,
        resolved: Map<String, StopUi>,
        usable: List<StopUi>,
        stays: List<StayRecord>,
        todayIndex: Int?,
        skipped: Int,
    ): ProposalUi {
        // 그 날 묵는 곳 좌표 — 보관함이 열려 있을 때만 들어온다. 계산에만 쓰고 결과에는 남기지 않는다
        val nights = slots.map { slot ->
            slot.date?.let { Stays.on(stays, it) }?.let { s -> if (s.lat != null && s.lng != null) NightStay(s.lat, s.lng) else null }
        }
        val existing = byDay.flatten().map { it to resolved[it.key]?.takeIf { s -> s.attraction != null }?.spot }
        val result = ItineraryPlanner.propose(usable.map { it.spot }, slots.size, nights, existing)
        val fresh = usable.associateBy { it.key }
        val newByDay = result.stops.groupBy { it.day }
        val days = slots.mapNotNull { slot ->
            val old = byDay[slot.index].map { resolved.getValue(it.key) }
            val added = newByDay[slot.index].orEmpty().map { fresh.getValue(it.key).copy(isNew = true) }
            val all = old + added
            if (all.isEmpty()) {
                null
            } else {
                DayUi(slot, all, ItineraryPlanner.mixedFar(all.filter { it.attraction != null }.map { it.spot }), slot.index == todayIndex)
            }
        }
        return ProposalUi(days, result.stops, result.usedStays, skipped)
    }
}
