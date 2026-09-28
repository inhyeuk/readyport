package com.readyport.trip

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * 여행 하나. 날짜·나라만 담는다(여권 등 개인정보 없음). 기기 안 DataStore에만 저장하고 서버로 보내지 않는다 (ARCHITECTURE 9.6).
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
) {
    val start: LocalDate get() = LocalDate.parse(startDate)
    val end: LocalDate get() = LocalDate.parse(endDate)
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
