package com.readyport.plan

/**
 * 받은 계획 → 찜할 관광지. 계획의 하루 순서·항목 순서 그대로, 같은 곳은 한 번만, **이 휴대폰의 관광지 파일에 있는 곳만**
 * (계획이 만들어진 뒤 사라진 곳·번호가 없는 항목(식사 같은 것)은 뺀다). 찜 목록 자체가 순서라서 이 순서가 그대로 관광 순서가 된다.
 */
object PlanSaves {
    fun orderedPlaceIds(result: PlanResult, known: Set<String>): List<String> =
        result.days.sortedBy { it.day }
            .flatMap { it.items }
            .mapNotNull { it.placeId }
            .filter { it in known }
            .distinct()
}
