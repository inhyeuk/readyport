package com.readyport.stay

import com.readyport.transport.Place
import com.readyport.trip.Trip
import com.readyport.vault.StayRecord
import com.readyport.vault.VaultContents
import java.net.URLEncoder
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

// =====================================================================================
// 묵는 곳 (2026-10-03)
//
// 운영자 요청: *"호텔 예약 정보도 관리하는 거지?"* → *"날짜별로 숙소가 달라질 수 있으니 이것도 고려해서 작업해줘.
// … 그리고 숙소는 구글 맵에서 바로 찾아볼 수 있도록 link 되도록 해줘."*
//
// 예전에는 숙소가 **두 곳**에 흩어져 있었다: 예약 서류 한 줄(`BookingRecord(kind="lodging")` — 날짜·예약번호만, 주소 없음)과
// 이동하기 화면의 '가는 곳'(`Place` — 주소만, 날짜 없음). 그래서 같은 호텔을 두 번 적고, 입국 카드 주소 칸은 늘 직접 입력이었다.
// 이제 [StayRecord] 하나가 이름·주소·날짜·예약번호·종류를 함께 들고,
//  ① 여행의 예약 단계 `묵는 곳` 카드가 날짜 순으로 보여 주고 ② 입국 카드 주소·숙소 종류를 채우고
//  ③ 구글 지도에서 찾고 ④ 기사님께 보여 줄 `Place`를 스스로 만든다(한 번만 적는다).
//
// 이 파일은 순수 함수만 둔다(안드로이드 없이 JVM 테스트) — 화면·인텐트는 [StayLinks]와 ui/stay.
// =====================================================================================

/**
 * 숙소 종류. 값은 **서명된 레시피의 `stay_type` 선택지 값과 같은 글자**다
 * (TH_TDAC: hotel/guest_house/hostel/apartment/friend, MY_MDAC: hotel/friend/other) —
 * 그래서 입국 카드에 넣을 때 사이트 글자를 지어내지 않고 레시피 선택지에서 그대로 찾는다.
 * 그 나라 사이트에 없는 종류는 비워 두고 사람이 사이트에서 고른다(없는 선택지를 만들지 않는다).
 */
enum class StayType(val key: String) {
    Hotel("hotel"),
    GuestHouse("guest_house"),
    Hostel("hostel"),
    Apartment("apartment"),
    Friend("friend"),
    Other("other"),
    ;

    companion object {
        fun of(key: String?): StayType? = entries.firstOrNull { it.key == key }
    }
}

/** 부드럽게 알려 줄 것 — 막지 않는다(저장은 언제나 된다). 화면이 앱 문구와 합쳐 한국어 한 줄로 그린다 */
enum class StayNoteKind {
    /** 날짜를 아직 안 적은 숙소가 있어요 */
    MissingDates,

    /** 앞 숙소 퇴실과 다음 숙소 입실 사이에 빈 날이 있어요 */
    Gap,

    /** 두 숙소 날짜가 겹쳐요 */
    Overlap,

    /** 도착한 날 묵을 곳이 아직 없어요 */
    ArrivalMissing,

    /** 여행 날짜 밖의 숙소가 있어요 */
    Outside,
}

data class StayNote(val kind: StayNoteKind, val from: LocalDate? = null, val to: LocalDate? = null)

/**
 * 한 여행의 숙소 묶음 (설정 › 내 정보의 묵는 곳 목록, 다듬기 S2). [trip]이 null이면 **여행이 없는 숙소** 묶음이다 —
 * 예전 예약 서류에서 옮겨 와 아직 어느 여행에도 붙지 않은 숙소.
 */
data class StayGrouping(val trip: Trip?, val stays: List<StayRecord>)

object Stays {

    // ---------------- 날짜 ----------------

    private fun date(raw: String?): LocalDate? = raw?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

    fun checkIn(stay: StayRecord): LocalDate? = date(stay.checkIn)

    fun checkOut(stay: StayRecord): LocalDate? = date(stay.checkOut)

    /** 몇 박인지. 날짜가 둘 다 있고 순서가 맞을 때만 (0박 = 같은 날 들어왔다 나감) */
    fun nights(stay: StayRecord): Int? {
        val from = checkIn(stay) ?: return null
        val to = checkOut(stay) ?: return null
        return ChronoUnit.DAYS.between(from, to).toInt().takeIf { it >= 0 }
    }

    /** 날짜 순(날짜 없는 숙소는 뒤로), 같으면 이름 순 — 화면·자동 입력이 모두 이 순서를 쓴다 */
    private val order = compareBy<StayRecord>({ it.checkIn ?: "9999" }, { it.checkOut ?: "9999" }, { it.name })

    fun sorted(stays: List<StayRecord>): List<StayRecord> = stays.sortedWith(order)

    /**
     * 이 여행의 숙소. 여행 id가 붙은 숙소는 그 여행 것이고,
     * 여행 id가 없는 숙소(예전 예약 서류에서 옮겨 온 것)는 **날짜가 이 여행과 겹칠 때만** 보인다 —
     * 날짜가 아예 없으면 어느 여행인지 알 수 없어 모든 여행에서 보인다(옮겨 온 것을 잃지 않게).
     */
    fun forTrip(stays: List<StayRecord>, trip: Trip): List<StayRecord> =
        sorted(stays.filter { it.tripId == trip.id || (it.tripId == null && matchesTrip(it, trip)) })

    private fun matchesTrip(stay: StayRecord, trip: Trip): Boolean {
        if (!trip.datesValid) return false
        val from = checkIn(stay) ?: checkOut(stay) ?: return true
        val to = checkOut(stay) ?: from
        return !to.isBefore(trip.start) && !from.isAfter(trip.end)
    }

    /** 그 날 밤 묵는 곳: 입실 ≤ 그 날 < 퇴실 (퇴실일에는 다음 숙소로 옮기므로 퇴실일은 치지 않는다) */
    fun covering(stays: List<StayRecord>, date: LocalDate): StayRecord? = sorted(stays).firstOrNull { stay ->
        val from = checkIn(stay)
        val to = checkOut(stay)
        when {
            from != null && to != null -> !date.isBefore(from) && date.isBefore(to)
            from != null -> !date.isBefore(from)
            to != null -> date.isBefore(to)
            else -> false
        }
    }

    /** 그 날 머무는 곳(화면용): 그 날 밤 묵는 곳 → 없으면 그 날 아침 퇴실하는 곳 */
    fun on(stays: List<StayRecord>, date: LocalDate): StayRecord? =
        covering(stays, date) ?: sorted(stays).firstOrNull { checkOut(it) == date }

    /** 입국 카드·공항 안내가 쓸 숙소: 도착한 날 묵는 곳 → 없으면 첫 숙소 */
    fun forArrival(stays: List<StayRecord>, trip: Trip): StayRecord? {
        val mine = forTrip(stays, trip)
        if (!trip.datesValid) return mine.firstOrNull()
        return on(mine, trip.start) ?: mine.firstOrNull()
    }

    /**
     * 숙소를 **여행별로 묶는다** — 설정 › 내 정보의 묵는 곳 목록(다듬기 S2)이 쓴다.
     * 여행 id가 붙은 숙소만 그 여행 묶음에 넣고, **여행 id가 없거나 그 여행이 사라진** 숙소는 마지막 묶음([StayGrouping.trip] = null)이다
     * — 예전 `lodging` 예약 서류에서 옮겨 와 아직 여행에 붙지 않은 숙소를 볼 화면이 없던 문제(STAYS_REPORT 6.2).
     * 한 숙소가 두 묶음에 들어가지 않도록 여기서는 날짜로 추측하지 않는다(여행 화면의 [forTrip]은 날짜로도 찾는다).
     * 순서: 여행 날짜 빠른 순(날짜가 깨진 여행은 뒤) → 여행 없는 묶음 맨 끝. 빈 묶음은 돌려주지 않는다.
     */
    fun group(stays: List<StayRecord>, trips: List<Trip>): List<StayGrouping> {
        if (stays.isEmpty()) return emptyList()
        val known = trips.associateBy { it.id }
        val byTrip = trips
            .sortedWith(compareBy({ if (it.datesValid) 0 else 1 }, { it.startDate }, { it.endDate }))
            .mapNotNull { trip ->
                sorted(stays.filter { it.tripId == trip.id }).takeIf { it.isNotEmpty() }?.let { StayGrouping(trip, it) }
            }
        val orphans = sorted(stays.filter { it.tripId == null || it.tripId !in known.keys })
        return byTrip + listOfNotNull(orphans.takeIf { it.isNotEmpty() }?.let { StayGrouping(null, it) })
    }

    // ---------------- 부드러운 알림 ----------------

    /**
     * 막지 않는 알림 목록: 날짜를 안 적음 · 빈 날 · 겹침 · 도착한 날 묵을 곳 없음 · 여행 날짜 밖.
     * 숙소가 하나도 없으면 빈 목록 — 카드가 `아직 없어요`로 말한다(알림이 아니다).
     */
    fun notes(stays: List<StayRecord>, trip: Trip): List<StayNote> {
        val mine = forTrip(stays, trip)
        if (mine.isEmpty()) return emptyList()
        val out = mutableListOf<StayNote>()
        if (mine.any { checkIn(it) == null || checkOut(it) == null }) out += StayNote(StayNoteKind.MissingDates)
        mine.zipWithNext { a, b ->
            val end = checkOut(a)
            val start = checkIn(b)
            if (end != null && start != null) {
                if (start.isAfter(end)) out += StayNote(StayNoteKind.Gap, end, start)
                if (start.isBefore(end)) out += StayNote(StayNoteKind.Overlap, start, end)
            }
        }
        if (trip.datesValid) {
            if (on(mine, trip.start) == null) out += StayNote(StayNoteKind.ArrivalMissing, trip.start)
            val outside = mine.any { stay ->
                val from = checkIn(stay)
                val to = checkOut(stay)
                (from != null && from.isBefore(trip.start)) || (to != null && to.isAfter(trip.end))
            }
            if (outside) out += StayNote(StayNoteKind.Outside)
        }
        // 같은 종류는 한 번만 말한다(빈 날이 둘이면 첫 빈 날만) — 알림이 쌓여 겁주지 않게
        return out.distinctBy { it.kind }
    }

    // ---------------- 구글 지도 (developers.google.com/maps/documentation/urls) ----------------

    /**
     * 지도에서 찾을 글자: 좌표를 알면 `lat,lng`, 아니면 주소, 주소도 없으면 이름.
     * 셋 다 없으면 null — 지도 버튼을 그리지 않는다(빈 검색으로 보내지 않는다).
     */
    fun searchQuery(stay: StayRecord): String? = when {
        stay.lat != null && stay.lng != null -> "${stay.lat},${stay.lng}"
        stay.addressLocal.isNotBlank() -> stay.addressLocal.trim()
        stay.name.isNotBlank() -> stay.name.trim()
        else -> null
    }

    /** 공식 문서의 '장소 찾기' 주소(Search). 길찾기는 [com.readyport.transport.RideLinker.mapsUrl] 그대로 쓴다 */
    fun searchUrl(stay: StayRecord): String? = searchQuery(stay)?.let {
        "https://www.google.com/maps/search/?api=1&query=${URLEncoder.encode(it, "UTF-8")}"
    }

    // ---------------- 가는 곳(Place)과 한 방향으로 맞추기 ----------------

    /**
     * 이 숙소의 '가는 곳' — 기사님께 보여 주기·차 부르기·길찾기가 쓰는 [Place]. **id는 숙소 id와 같다**(한 번만 적는다).
     * 보여 줄 주소가 없으면 null → 저장소에서 그 가는 곳을 지운다.
     */
    fun place(stay: StayRecord): Place? {
        val address = stay.addressLocal.trim()
        if (address.isEmpty()) return null
        return Place(id = stay.id, name = stay.name.trim().ifEmpty { address }, addressLocal = address, lat = stay.lat, lng = stay.lng)
    }

    // ---------------- 예전 저장본 옮기기 ----------------

    /**
     * 예전 `lodging` 예약 서류를 숙소로 옮긴다 — **id를 그대로 두고**(가는 곳·체크 표시가 흔들리지 않게)
     * 이름·예약번호·체크인·체크아웃을 그대로 가져온다. 주소는 예약 서류에 없어서 비어 있다(사람이 채운다).
     * 옮긴 예약 서류는 목록에서 빠진다 — 같은 숙소가 두 곳에 보이지 않게. 옮길 것이 없으면 **같은 객체를 그대로** 돌려준다(한 번만 동작).
     */
    fun migrate(contents: VaultContents): VaultContents {
        val lodging = contents.bookings.filter { it.kind == "lodging" }
        if (lodging.isEmpty()) return contents
        val moved = lodging.map { b ->
            StayRecord(
                id = b.id,
                tripId = null,
                name = b.title,
                addressLocal = "",
                checkIn = b.checkIn ?: b.dates.firstOrNull(),
                checkOut = b.checkOut ?: b.dates.drop(1).lastOrNull(),
                reference = b.reference,
                savedAt = b.savedAt,
            )
        }
        val known = contents.stays.map { it.id }.toSet()
        return contents.copy(
            bookings = contents.bookings.filterNot { it.kind == "lodging" },
            stays = contents.stays + moved.filterNot { it.id in known },
        )
    }

    /** 새 숙소 id */
    fun newId(): String = UUID.randomUUID().toString()
}
