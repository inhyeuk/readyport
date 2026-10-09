package com.readyport.itinerary

import com.readyport.trip.Trip
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// =====================================================================================
// 관광 일정 (2026-10-09 사장님 요청)
//
// *"찜한 장소는 소팅 순서를 바꿀 수 있도록 해서 관광 순서로 활용할 수 있도록 하면 좋겠어.
//   찜한 내용이 내 관광스케쥴에 이식될 수 있도록 하는 기능이 있으면 좋겠어."* → 결정: **날짜별로 나눠 담기**.
//
// 한 여행의 관광 일정 = 관광지 키("<CC>/<id>") + 며칠째(0부터) + 그 날 안의 순서(목록 순서)뿐이다.
// 개인정보·좌표·날짜 글자는 담지 않는다. 여행 장부([com.readyport.trip.TripBook])에 함께 저장해서
// 여행을 지우면 같이 지워지고(TripRepository.delete), 서버로 보내지 않는다(CloudSync는 여행 나라만 읽는다).
// 이동 시간·거리 숫자는 보여 주지 않는다(⟦결정 D7⟧) — 먼 지역이 섞인 날에 부드러운 한 줄만.
// =====================================================================================

/** 일정의 한 곳: 관광지 전역 키 + 며칠째(0 = 1일차). 그 날 안의 순서는 [TripItinerary.stops]의 순서다 */
@Serializable
data class ItineraryStop(val key: String, val day: Int)

/** 한 여행의 관광 일정 */
@Serializable
data class TripItinerary(val stops: List<ItineraryStop> = emptyList()) {
    val keys: Set<String> get() = stops.mapTo(LinkedHashSet()) { it.key }
}

/** 일정의 하루 칸. [date]가 null이면 '날짜 미정'(여행 날짜를 읽을 수 없을 때 한 칸뿐) */
data class DaySlot(val index: Int, val date: LocalDate?)

/** 여행 날짜에서 하루 칸을 만든다 — 출발일이 1일차, 돌아오는 날이 마지막 날 */
object ItineraryDays {
    /** 아주 긴 여행도 화면이 끝없이 길어지지 않게 — 넘는 날의 곳은 마지막 칸에 모인다 */
    const val MAX_DAYS = 60

    fun slots(trip: Trip): List<DaySlot> {
        if (!trip.datesValid) return listOf(DaySlot(0, null))
        val count = (ChronoUnit.DAYS.between(trip.start, trip.end) + 1).toInt().coerceIn(1, MAX_DAYS)
        return (0 until count).map { DaySlot(it, trip.start.plusDays(it.toLong())) }
    }

    /** 오늘이 여행 며칠째 칸인지(0부터). 여행 기간 밖이거나 날짜가 없으면 null */
    fun todayIndex(trip: Trip, today: LocalDate): Int? {
        if (!trip.datesValid || today.isBefore(trip.start) || today.isAfter(trip.end)) return null
        return ChronoUnit.DAYS.between(trip.start, today).toInt().coerceAtMost(MAX_DAYS - 1)
    }
}

/**
 * 일정 고치기(순수 함수). 저장된 날이 칸 수를 넘으면(여행 날짜를 줄였을 때) **마지막 칸**에 있는 것으로 본다 —
 * 지우지 않고, 사람이 다른 날로 옮기면 그때 새 날이 저장된다.
 */
object Itinerary {
    fun effectiveDay(stop: ItineraryStop, dayCount: Int): Int = stop.day.coerceIn(0, (dayCount - 1).coerceAtLeast(0))

    /** 칸마다 그 날의 곳(순서대로) */
    fun byDay(itinerary: TripItinerary, dayCount: Int): List<List<ItineraryStop>> {
        val days = List(dayCount.coerceAtLeast(1)) { mutableListOf<ItineraryStop>() }
        itinerary.stops.forEach { days[effectiveDay(it, days.size)] += it }
        return days
    }

    /** 새 곳을 더한다 — 이미 일정에 있는 키는 건너뛴다(다시 옮겨 담아도 겹치지 않고, 사람이 고친 날·순서를 지우지 않는다) */
    fun add(itinerary: TripItinerary, stops: List<ItineraryStop>): TripItinerary {
        val have = itinerary.keys.toMutableSet()
        val fresh = stops.filter { have.add(it.key) }
        return if (fresh.isEmpty()) itinerary else itinerary.copy(stops = itinerary.stops + fresh)
    }

    /** 다른 날로 옮긴다 — 그 날의 맨 뒤로 */
    fun moveToDay(itinerary: TripItinerary, key: String, day: Int): TripItinerary {
        val stop = itinerary.stops.firstOrNull { it.key == key } ?: return itinerary
        return itinerary.copy(stops = itinerary.stops.filterNot { it.key == key } + stop.copy(day = day.coerceAtLeast(0)))
    }

    /** 같은 날 안에서 [by]칸 옮긴다(-1 = 한 칸 위로) */
    fun shift(itinerary: TripItinerary, key: String, by: Int, dayCount: Int): TripItinerary {
        val stops = itinerary.stops
        val at = stops.indexOfFirst { it.key == key }
        if (at < 0) return itinerary
        val day = effectiveDay(stops[at], dayCount)
        val sameDay = stops.indices.filter { effectiveDay(stops[it], dayCount) == day }
        val pos = sameDay.indexOf(at)
        val target = (pos + by).coerceIn(0, sameDay.lastIndex)
        if (target == pos) return itinerary
        val list = stops.toMutableList()
        val moved = list.removeAt(at)
        // 아래로: 지운 뒤 한 칸씩 당겨졌으니 같은 번호에 넣으면 이웃 뒤 / 위로: 이웃 앞
        list.add(sameDay[target].coerceIn(0, list.size), moved)
        return itinerary.copy(stops = list)
    }

    fun remove(itinerary: TripItinerary, key: String): TripItinerary =
        itinerary.copy(stops = itinerary.stops.filterNot { it.key == key })
}
