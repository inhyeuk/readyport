package com.readyport.plan

/**
 * 받은 계획 → 찜할 관광지. 계획의 하루 순서·항목 순서 그대로, 같은 곳은 한 번만, **이 휴대폰의 관광지 파일에 있는 곳만**
 * (계획이 만들어진 뒤 사라진 곳·번호가 없는 항목(식사 같은 것)은 뺀다). 찜 목록 자체가 순서라서 이 순서가 그대로 관광 순서가 된다.
 */
object PlanSaves {
    /**
     * 받은 계획 → 새 여행의 관광 일정. 계획의 **1일차 = 여행 1일차**(0부터)이고, 하루 안 순서도 계획 그대로다.
     * 같은 곳이 여러 날에 나오면 처음 나온 날에만 둔다(일정은 한 곳을 한 번만 가진다). 하루 번호가 여행 칸 수를 넘으면 마지막 날로.
     */
    fun stops(result: PlanResult, known: Set<String>, dayCount: Int): List<com.readyport.itinerary.ItineraryStop> {
        val last = (dayCount - 1).coerceAtLeast(0)
        val seen = HashSet<String>()
        return result.days.sortedBy { it.day }.flatMap { day ->
            day.items.mapNotNull { it.placeId }.filter { it in known }.mapNotNull { id ->
                if (seen.add(id)) com.readyport.itinerary.ItineraryStop("${result.country}/$id", (day.day - 1).coerceIn(0, last)) else null
            }
        }
    }

    fun orderedPlaceIds(result: PlanResult, known: Set<String>): List<String> =
        result.days.sortedBy { it.day }
            .flatMap { it.items }
            .mapNotNull { it.placeId }
            .filter { it in known }
            .distinct()
}
