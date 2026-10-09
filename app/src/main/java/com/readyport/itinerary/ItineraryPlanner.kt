package com.readyport.itinerary

import com.readyport.attractions.RegionGrouping

/**
 * 옮겨 담을 곳 하나 — 계산에만 쓰는 화면 밖 값. [group]은 묶는 단위(관광지 지역 id, 모르면 null = 혼자),
 * [lat]/[lng]는 그 지역 중심(없으면 그 곳) 좌표. 좌표는 **기기 안 계산에만** 쓰고 저장·전송하지 않는다.
 */
data class Spot(val key: String, val group: String?, val lat: Double? = null, val lng: Double? = null)

/** 그 날 밤 묵는 곳 좌표 (보관함이 열려 있을 때 기기 안에서만 계산) */
data class NightStay(val lat: Double, val lng: Double)

/**
 * 찜 → 날짜별 일정 제안 (순수 함수, 결정적).
 *
 * 규칙(사장님 결정 2026-10-09 '날짜별로 나눠 담기'):
 * 1. **같은 지역은 같은 날**. 지역은 찜 순서에서 처음 나온 순서로, 지역 안 곳은 찜 순서 그대로.
 * 2. 이미 일정에 그 지역이 있으면 그 날 뒤에 붙인다(다시 옮겨 담기 — 사람이 고친 것은 건드리지 않는다).
 * 3. 묵는 곳 좌표가 있으면 **그 날 밤 묵는 곳이 그 지역 중심에서 가장 가까운 날**을 고른다
 *    (같은 숙소에 며칠 묵으면 그 가운데 덜 찬 날, 그다음 이른 날). 모든 숙소에서 [FAR_STAY_KM]보다 먼 지역은
 *    숙소를 모르는 날이 있으면 그 날로(멀리 하루 다녀오는 날일 수 있다), 없으면 그래도 가장 가까운 날로.
 * 4. 묵는 곳을 모르면 **앞날부터 차례로** 한 날에 한 지역씩. 날보다 지역이 많으면 가장 덜 찬 날 가운데
 *    이미 있는 지역과 가장 가까운 날에 함께 둔다.
 * 숫자(거리·시간)는 화면에 나오지 않는다(⟦결정 D7⟧).
 */
object ItineraryPlanner {
    /** 같은 숙소로 보는 거리 여유 — 숙소 둘이 이만큼 안이면 같은 '가장 가까운 날'로 친다 */
    const val NEAR_SLACK_KM = 10.0

    /** 어느 숙소에서도 이보다 멀면 숙소 근처 날이 아니라 다녀오는 날로 본다 */
    const val FAR_STAY_KM = 80.0

    /** 한 날의 지역 중심끼리 이보다 멀면 '이동이 길어질 수 있어요' */
    const val MIXED_FAR_KM = 60.0

    data class Proposal(
        /** 새로 더할 곳(날짜·순서) — [Itinerary.add]에 그대로 넘긴다 */
        val stops: List<ItineraryStop>,
        /** 묵는 곳 좌표로 날을 골랐는지 (안내 한 줄) */
        val usedStays: Boolean,
    )

    /**
     * @param fresh 새로 담을 곳(찜 순서). 이미 일정에 있는 키는 부르는 쪽이 뺀다(여기서도 한 번 더 뺀다)
     * @param dayCount 하루 칸 수(1 이상)
     * @param nights 칸마다 그 날 묵는 곳 좌표(모르면 null). 크기가 칸 수보다 작으면 나머지는 모름
     * @param existing 이미 일정에 있는 곳과 그 지역(그 지역이 있는 날에 붙이려고)
     */
    fun propose(
        fresh: List<Spot>,
        dayCount: Int,
        nights: List<NightStay?> = emptyList(),
        existing: List<Pair<ItineraryStop, Spot?>> = emptyList(),
    ): Proposal {
        val days = dayCount.coerceAtLeast(1)
        val have = existing.map { it.first.key }.toSet()
        val places = fresh.filter { it.key !in have }.distinctBy { it.key }
        if (places.isEmpty()) return Proposal(emptyList(), false)

        // 날마다 들어 있는 지역(묶음 열쇠)과 그 중심
        val dayGroups = List(days) { mutableListOf<Spot>() }
        val groupDay = mutableMapOf<String, Int>()
        existing.forEach { (stop, spot) ->
            val d = Itinerary.effectiveDay(stop, days)
            val g = groupKey(spot ?: Spot(stop.key, null))
            if (dayGroups[d].none { groupKey(it) == g }) dayGroups[d] += (spot ?: Spot(stop.key, null))
            groupDay.putIfAbsent(g, d)
        }

        val nightAt = List(days) { nights.getOrNull(it) }
        val anyNight = nightAt.any { it != null }
        var usedStays = false
        val out = mutableListOf<ItineraryStop>()

        // 지역 묶음 — 찜 순서에서 처음 나온 순서
        val groups = LinkedHashMap<String, MutableList<Spot>>()
        places.forEach { groups.getOrPut(groupKey(it)) { mutableListOf() } += it }

        groups.forEach { (g, members) ->
            val center = members.firstOrNull { it.lat != null && it.lng != null }
            val day = groupDay[g] ?: run {
                val byStay = if (anyNight && center != null) chooseByStay(center, nightAt, dayGroups) else null
                if (byStay != null) {
                    usedStays = true
                    byStay
                } else {
                    chooseInOrder(center, dayGroups, (0 until days).toList())
                }
            }
            groupDay[g] = day
            if (dayGroups[day].none { groupKey(it) == g }) dayGroups[day] += (center ?: members.first())
            members.forEach { out += ItineraryStop(it.key, day) }
        }
        return Proposal(out, usedStays)
    }

    /** 한 날에 서로 먼 지역이 섞였는지 — 좌표를 모르는 다른 지역도 섞인 것으로 본다(조심하는 쪽) */
    fun mixedFar(spots: List<Spot>): Boolean {
        val byGroup = spots.distinctBy { groupKey(it) }
        if (byGroup.size < 2) return false
        for (i in byGroup.indices) for (j in i + 1 until byGroup.size) {
            val a = byGroup[i]
            val b = byGroup[j]
            if (a.lat == null || a.lng == null || b.lat == null || b.lng == null) return true
            if (RegionGrouping.haversineKm(a.lat, a.lng, b.lat, b.lng) > MIXED_FAR_KM) return true
        }
        return false
    }

    private fun groupKey(spot: Spot): String = spot.group ?: "key:${spot.key}"

    private fun chooseByStay(center: Spot, nights: List<NightStay?>, dayGroups: List<List<Spot>>): Int? {
        val dist = nights.mapIndexedNotNull { d, n -> n?.let { d to RegionGrouping.haversineKm(center.lat!!, center.lng!!, it.lat, it.lng) } }
        if (dist.isEmpty()) return null
        val best = dist.minOf { it.second }
        if (best > FAR_STAY_KM) {
            // 어느 숙소에서도 먼 곳 — 숙소를 모르는 날이 있으면 그 날(다녀오는 날)
            val unknown = nights.indices.filter { nights[it] == null }
            if (unknown.isNotEmpty()) return chooseInOrder(center, dayGroups, unknown)
        }
        val near = dist.filter { it.second <= best + NEAR_SLACK_KM }.map { it.first }
        return near.minWith(compareBy<Int> { dayGroups[it].size }.thenBy { it })
    }

    /** 가장 덜 찬 날 → (이미 지역이 있으면) 그 지역들과 가장 가까운 날 → 이른 날 */
    private fun chooseInOrder(center: Spot?, dayGroups: List<List<Spot>>, candidates: List<Int>): Int {
        val least = candidates.minOf { dayGroups[it].size }
        val pool = candidates.filter { dayGroups[it].size == least }
        if (least == 0 || center?.lat == null || center.lng == null) return pool.first()
        return pool.minWith(
            compareBy<Int> { d ->
                dayGroups[d].mapNotNull { s -> if (s.lat != null && s.lng != null) RegionGrouping.haversineKm(center.lat, center.lng, s.lat, s.lng) else null }
                    .minOrNull() ?: Double.MAX_VALUE
            }.thenBy { it },
        )
    }
}
