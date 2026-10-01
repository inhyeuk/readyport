package com.readyport.trip

import com.readyport.pack.PackRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** 저장된 여행 장부 + 팩으로 한 여행의 체크리스트를 만든다 (화면 ViewModel들이 함께 쓴다) */
@Singleton
class ChecklistProvider @Inject constructor(private val packs: PackRepository) {

    suspend fun build(trip: Trip, book: TripBook, today: LocalDate = LocalDate.now()): ChecklistData {
        val index = packs.index()?.value
        val pack = packs.pack(trip.country)?.value
        return Checklist.build(
            Checklist.Input(
                trip = trip,
                index = index,
                pack = pack,
                checks = book.checks[trip.id] ?: TripChecks(),
                passportSaved = book.passportSaved,
                today = today,
            ),
        )
    }

    /** 나라 이름(한국어). 팩이 없으면 색인 이름, 그것도 없으면 코드 */
    suspend fun countryName(code: String): String =
        packs.pack(code)?.value?.names?.ko ?: packs.index()?.value?.countries?.firstOrNull { it.code == code }?.nameKo ?: code
}

/** 사람이 체크를 바꿨을 때 저장할 값: 앱 판단과 같으면 표시를 지워 앱 판단으로 되돌리고, 다르면 사람이 정한 값으로 */
fun markFor(item: ChecklistItem, checked: Boolean): Boolean? {
    val auto = item.auto
    return when {
        auto == null || auto == AutoState.Unknown -> if (checked) true else null
        checked == (auto == AutoState.Done) -> null
        else -> checked
    }
}
