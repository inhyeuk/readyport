package com.readyport.trip

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * 여행 하나. 날짜·나라만 담는다(여권 등 개인정보 없음). 기기 안 DataStore에만 저장하고 서버로 보내지 않는다 (ARCHITECTURE 9.6).
 * 여행은 [id]로 가린다 — 나라가 아니라. 같은 나라를 다른 날짜에 가면 다른 여행이고 체크리스트도 따로다(2026-10-02 운영자 요청).
 */
@Serializable
data class Trip(
    val country: String,
    /** 출발일(한국 출발) yyyy-MM-dd */
    val startDate: String,
    /** 돌아오는 날 yyyy-MM-dd */
    val endDate: String,
    val homeZone: String = "Asia/Seoul",
    /** 시간대 변경(또는 '도착했어요')으로 도착을 안 시각 (epoch ms) */
    val arrivedAt: Long? = null,
    /** 도착 순서 카드를 닫았는지 */
    val arrivalDismissed: Boolean = false,
    /** 여권 정보 파기를 이 날짜까지 미룸 */
    val destroyPostponedUntil: String? = null,
    /** 귀국 정리를 마쳤는지 */
    val wrappedUp: Boolean = false,
    /** 여행 고유 번호(UUID). 비어 있으면 저장할 때 저장소가 새로 붙인다. 예전 한 여행 저장본은 옮길 때 붙인다 */
    val id: String = "",
) {
    val start: LocalDate get() = LocalDate.parse(startDate)
    val end: LocalDate get() = LocalDate.parse(endDate)

    /** 날짜를 읽을 수 있는지(저장값이 깨졌으면 목록·단계 계산에서 뺀다) */
    val datesValid: Boolean get() = runCatching { !end.isBefore(start) }.getOrDefault(false)

    /** [other]와 여행 날짜가 하루라도 겹치는지 */
    fun overlaps(other: Trip): Boolean = datesValid && other.datesValid && id != other.id &&
        !end.isBefore(other.start) && !other.end.isBefore(start)
}

/** 내 여행 목록에서의 자리 */
enum class TripTiming { Ongoing, Upcoming, Past }

/**
 * 여러 여행 중 '지금 여행'(오늘 화면·홈이 보여 줄 여행)을 고른다 — 순수 함수.
 * ① 오늘이 여행 기간 안인 여행(여럿이면 먼저 끝나는 것) ② 없으면 가장 가까운 다가오는 여행
 * ③ 없으면 가장 최근 지난 여행 — 귀국 단계(정리 안 함)는 언제까지나, 정리 단계(축하 카드)는 돌아온 뒤 [WRAPUP_DAYS]일까지.
 * 그보다 오래된 정리 끝난 여행만 있으면 null(여행 없음 — 새 여행 만들기, 지난 여행은 목록에 남는다).
 */
object TripSelection {
    fun timing(trip: Trip, today: LocalDate): TripTiming = when {
        today.isBefore(trip.start) -> TripTiming.Upcoming
        today.isAfter(trip.end) -> TripTiming.Past
        else -> TripTiming.Ongoing
    }

    fun active(trips: List<Trip>, today: LocalDate): Trip? {
        val valid = trips.filter { it.datesValid }
        valid.filter { timing(it, today) == TripTiming.Ongoing }.minWithOrNull(compareBy<Trip>({ it.end }, { it.start }))?.let { return it }
        valid.filter { timing(it, today) == TripTiming.Upcoming }.minWithOrNull(compareBy<Trip>({ it.start }, { it.end }))?.let { return it }
        return valid.filter { timing(it, today) == TripTiming.Past }
            .filter { !it.wrappedUp || !today.isAfter(it.end.plusDays(WRAPUP_DAYS)) }
            .maxWithOrNull(compareBy<Trip>({ it.end }, { it.start }))
    }

    /** 정리를 마친 여행의 축하 카드를 오늘 화면에 두는 기간(돌아온 날부터) */
    const val WRAPUP_DAYS = 14L

    /** 목록 순서: 여행 중 → 다가오는 여행(가까운 순) → 지난 여행(최근 순) */
    fun ordered(trips: List<Trip>, today: LocalDate): List<Pair<TripTiming, Trip>> {
        val valid = trips.filter { it.datesValid }
        val ongoing = valid.filter { timing(it, today) == TripTiming.Ongoing }.sortedBy { it.end }
        val upcoming = valid.filter { timing(it, today) == TripTiming.Upcoming }.sortedWith(compareBy({ it.start }, { it.end }))
        val past = valid.filter { timing(it, today) == TripTiming.Past }.sortedWith(compareByDescending<Trip> { it.end }.thenByDescending { it.start })
        return ongoing.map { TripTiming.Ongoing to it } + upcoming.map { TripTiming.Upcoming to it } + past.map { TripTiming.Past to it }
    }

    /** 날짜가 다른 여행과 겹치는 여행 id (막지 않고 가볍게 알린다) */
    fun overlapping(trips: List<Trip>): Set<String> =
        trips.filter { t -> trips.any { it.overlaps(t) } }.map { it.id }.toSet()

    /** 같은 나라로 가는 다가오는(또는 여행 중인) 여행 — 나라 화면 '내 여행에 넣기'가 새로 만들지 물어볼 때 */
    fun upcomingFor(trips: List<Trip>, country: String, today: LocalDate): Trip? =
        trips.filter { it.datesValid && it.country == country && !today.isAfter(it.end) }.minByOrNull { it.start }
}

/** '오늘' 화면 단계 (PRD 4.2). 표시줄 6칸: 준비·출국·도착·여행 중·귀국·정리 */
enum class TripStage(val barIndex: Int) {
    NoTrip(0), Preparing(0), Departure(1), Arrival(2), Traveling(3), Return(4), WrapUp(5)
}

data class StageInfo(
    val stage: TripStage,
    /** 출발까지 남은 날 (출발 전만) */
    val daysLeft: Long? = null,
    /** 여행 며칠째 (여행 중) */
    val dayOfTrip: Long? = null,
    /** 입국 카드를 낼 수 있는 기간인지 (PRD 4.2 ④) */
    val formWindowOpen: Boolean = false,
    /** 여권 정보 파기를 물어볼 때인지 (PRD 4.2 ⑧) */
    val askDestroy: Boolean = false,
)

object TripStages {
    /** 도착 순서 카드를 보여 주는 시간 */
    const val ARRIVAL_MODE_HOURS = 6L

    /**
     * 순수 함수: 여행·오늘 날짜·지금 시각으로 단계를 정한다.
     * @param formWindowDays 도착일을 포함해 며칠 전부터 입국 카드를 낼 수 있는지 (국가 팩 forms[].window_days_including_arrival)
     */
    fun compute(
        trip: Trip?,
        today: LocalDate,
        nowMillis: Long,
        formWindowDays: Int? = null,
        formSubmitted: Boolean = false,
    ): StageInfo {
        if (trip == null) return StageInfo(TripStage.NoTrip)
        val start = trip.start
        val end = trip.end
        val arrived = trip.arrivedAt != null
        return when {
            today.isBefore(start) -> {
                val windowStart = formWindowDays?.let { start.minusDays((it - 1).toLong()) }
                val open = windowStart != null && !today.isBefore(windowStart) && !formSubmitted
                StageInfo(TripStage.Preparing, daysLeft = start.toEpochDay() - today.toEpochDay(), formWindowOpen = open)
            }
            !today.isAfter(end) -> {
                val day = today.toEpochDay() - start.toEpochDay() + 1
                val windowOpen = formWindowDays != null && !formSubmitted && !arrived
                when {
                    // 도착한 뒤 몇 시간은 도착 순서 카드 (PRD 4.2 ⑥)
                    arrived && !trip.arrivalDismissed && nowMillis - trip.arrivedAt < ARRIVAL_MODE_HOURS * 3_600_000 ->
                        StageInfo(TripStage.Arrival, dayOfTrip = day)
                    // 출발 당일에 아직 도착을 모르면 출국 단계 (일본처럼 시간대가 같은 나라는 '도착했어요' 버튼으로)
                    !arrived && today == start -> StageInfo(TripStage.Departure, dayOfTrip = day, formWindowOpen = windowOpen)
                    else -> StageInfo(TripStage.Traveling, dayOfTrip = day)
                }
            }
            else -> {
                val postponed = trip.destroyPostponedUntil?.let { !today.isAfter(LocalDate.parse(it)) } == true
                if (trip.wrappedUp) StageInfo(TripStage.WrapUp)
                else StageInfo(TripStage.Return, askDestroy = !postponed)
            }
        }
    }

    /** 시간대가 바뀌었을 때 도착으로 볼지: 여행 기간 앞뒤 하루 안이고 한국 시간대가 아니면 */
    fun isArrival(trip: Trip, today: LocalDate, newZoneId: String): Boolean =
        trip.arrivedAt == null &&
            newZoneId != trip.homeZone &&
            !today.isBefore(trip.start.minusDays(1)) &&
            !today.isAfter(trip.end)
}
