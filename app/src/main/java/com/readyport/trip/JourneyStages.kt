package com.readyport.trip

import java.time.LocalDate

// ======================= 두 축 (여행 과정 길잡이, 2026-10-03) =======================
// 운영자 지적: *"여행 일정(생각 → 일정 검토 및 정리 → 각종 예약 → (반)자동 신청 등 처리 → … → 출국/입국 → 여행 → 복귀)
// 등의 과정이 제대로 안내 되고 그 흐름으로 처리되면 좋겠는데 현재 메뉴 구조는 그렇지 않아."*
//
// 체크리스트는 **두 가지**를 한 값(예전 `ChecklistPhase`)에 섞어 담고 있었다: '무엇을 하는 일인지'와 '언제까지 하는 일인지'.
// 그래서 묶음 이름이 `한 달 전쯤`·`일주일 전`이 되고, 사장님이 생각하는 묶음(예약·신청·출국)이 화면에 없었다.
// 두 축을 **갈라 놓는다**:
//
// | 축 | 값 | 무엇을 정하는가 |
// |---|---|---|
// | 할 일의 종류 | [JourneyStage] 8단계 | 묶기·길잡이(단계 막대)·'지금 할 일' |
// | 언제까지 | [DueWindow] 8칸 | 기한([Checklist.dueBy])·늦음·알림([ChecklistReminders]) |
//
// 두 축은 서로 **독립**이다. 기한·늦음·알림 규칙은 2026-10-02 체크리스트와 **글자 하나 다르지 않다**(같은 값·같은 날짜 계산) —
// 바뀐 것은 묶는 방식뿐이다.

/**
 * 두 축 ① **할 일의 종류** — 여행 과정 8단계. 화면이 항목을 묶고, 단계 막대가 길을 안내한다.
 *
 * [key]: 팩 데이터(`checklist[].stage`)의 값.
 * [legacy]: 단계 값이 없는 **예전 서명 팩**을 읽을 때 쓰는 기한 축 이름([DueWindow.key]) — 그 시간에 주로 하는 일의 단계로 옮긴다.
 *   (예전 팩도 항목이 빠지지 않고 뜨고, 체크는 항목 id에 붙어 있어 그대로 남는다)
 */
enum class JourneyStage(val key: String, val legacy: List<String> = emptyList()) {
    /** 계획 — 어디·언제·누구와, 내리는 공항 */
    Plan("plan", listOf("month")),

    /** 예약 — 항공·숙소·보험·데이터 */
    Book("book"),

    /** 서류 — 여권 정보 저장, 비자 신청, 입국 카드 내기 */
    Docs("docs", listOf("three_days")),

    /** 짐 — 꼭 챙길 물건, 오프라인 자료, 현지어·긴급 번호 */
    Pack("pack", listOf("week")),

    /** 출국 */
    Departure("departure", listOf("departure_day")),

    /** 입국 — 공항 도착 순서 */
    Arrival("arrival", listOf("arrival")),

    /** 여행 중 */
    During("during", listOf("during")),

    /** 복귀 — 돌아오기 전 + 돌아와서 */
    Return("return", listOf("before_return", "back")),
    ;

    /** 단계 막대의 칸 번호 (0부터) */
    val barIndex: Int get() = ordinal

    /** 떠나기 전에 하는 단계인지 (계획·예약·서류·짐) */
    val beforeDeparture: Boolean get() = this < Departure

    companion object {
        /** 떠나기 전 네 단계 — '지금 단계'를 고를 때 아직 안 끝난 첫 단계를 찾는 순서 */
        val Preparation: List<JourneyStage> get() = entries.filter { it.beforeDeparture }

        fun of(key: String?): JourneyStage? = entries.firstOrNull { it.key == key }

        /** 예전 서명 팩(단계 값 없음): 기한 축 이름으로 단계를 고른다 */
        fun ofLegacy(dueKey: String?): JourneyStage? = entries.firstOrNull { dueKey in it.legacy }

        /** 팩 값 → 단계 (`stage`가 있으면 그 값, 없으면 예전 이름에서) */
        fun resolve(stageKey: String?, dueKey: String?): JourneyStage? = of(stageKey) ?: ofLegacy(dueKey)
    }
}

/**
 * 두 축 ② **언제까지** — 기한 칸. 값·순서·기한 날짜([Checklist.dueBy])·늦음 범위는 예전 `ChecklistPhase`와 같다.
 * 팩 데이터의 `phase` 값이 그대로 이 축이다(그래서 예전 팩도 기한이 똑같이 계산된다).
 */
enum class DueWindow(val key: String) {
    /** 떠나기 한 달 전쯤 */
    Month("month"),

    /** 일주일 전 */
    Week("week"),

    /** 3일 전 */
    ThreeDays("three_days"),

    /** 출발하는 날 */
    DepartureDay("departure_day"),

    /** 도착하면 */
    Arrival("arrival"),

    /** 여행 중 */
    During("during"),

    /** 돌아오기 전 */
    BeforeReturn("before_return"),

    /** 돌아와서 */
    Back("back"),
    ;

    companion object {
        fun of(key: String?): DueWindow? = entries.firstOrNull { it.key == key }
    }
}

/** 이 기한 칸의 항목을 이 날까지 해 두면 좋다 (2026-10-02 규칙 그대로) */
internal fun DueWindow.dueDate(trip: Trip): LocalDate = when (this) {
    DueWindow.Month -> trip.start.minusDays(8)
    DueWindow.Week -> trip.start.minusDays(4)
    DueWindow.ThreeDays -> trip.start.minusDays(1)
    DueWindow.DepartureDay -> trip.start
    DueWindow.Arrival -> trip.start.plusDays(1).coerceAtMost(trip.end)
    DueWindow.During -> trip.end
    DueWindow.BeforeReturn -> trip.end
    DueWindow.Back -> trip.end.plusDays(7)
}

/** 늦음 표시는 떠나기 전·도착 칸만 — 여행 중·귀국 뒤 항목에 '늦었어요'를 붙이지 않는다 (2026-10-02 규칙 그대로) */
internal val DueWindow.overdueApplies: Boolean
    get() = this <= DueWindow.Arrival
