package com.readyport.trip

import com.readyport.pack.PassportValidityRule
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** 여권 남은 기간 결과. 날짜는 담지 않는다 */
@Serializable
enum class PassportStatus {
    /** 이 여행 기준을 채운다 */
    Ok,

    /** 기준보다 모자란다(또는 여행이 끝나기 전에 끝난다) */
    Short,

    /** 이 나라 기준을 공식 안내에서 찾지 못했다 — 여행 기간은 채운다. 사람이 공식 안내에서 확인한다 */
    Unknown,
}

/**
 * 한 여행의 여권 남은 기간 결과 — 기기 안 체크 상태에 **이것만** 남는다(만료일 없음, ARCHITECTURE 9.9).
 * [months]·[basis]: 쓴 기준(팩 값). [key]: 어떤 여행 날짜·기준으로 계산했는지 — 날짜·기준이 바뀌면 결과를 버리고 다시 계산한다.
 */
@Serializable
data class PassportCheck(
    val status: PassportStatus,
    val months: Int? = null,
    val basis: String? = null,
    val key: String = "",
) {
    override fun toString() = "PassportCheck(status=$status, months=$months)"
}

/**
 * 여권 남은 기간 판단 — 순수 함수. 만료일은 지갑(Keystore AES-GCM)을 연 그 순간 메모리에서만 쓰고 저장·기록하지 않는다.
 * 기준 날짜: arrival = 출발일(이 앱은 출발일을 도착일로 본다 — 단계·입국 카드 기간과 같은 기준), departure·stay_end = 돌아오는 날.
 */
object PassportValidity {

    /** 결과가 어느 여행 날짜·기준으로 계산됐는지 (개인정보 없음) */
    fun key(trip: Trip, rule: PassportValidityRule?): String =
        listOf(trip.country, trip.startDate, trip.endDate, rule?.months ?: "-", rule?.basis ?: "-").joinToString("|")

    /** 기준 날짜(이 날로부터 [PassportValidityRule.months]달 이상 남아야 한다) */
    fun baseDate(trip: Trip, rule: PassportValidityRule): LocalDate = when (rule.basis) {
        "arrival" -> trip.start
        else -> trip.end
    }

    fun check(expiry: LocalDate, trip: Trip, rule: PassportValidityRule?): PassportCheck {
        val key = key(trip, rule)
        if (rule == null) {
            // 나라 기준을 모를 때도 '여행이 끝나기 전에 끝나는 여권'은 분명히 모자란다
            val status = if (expiry.isBefore(trip.end)) PassportStatus.Short else PassportStatus.Unknown
            return PassportCheck(status, key = key)
        }
        val needed = baseDate(trip, rule).plusMonths(rule.months.toLong())
        val status = if (expiry.isBefore(needed) || expiry.isBefore(trip.end)) PassportStatus.Short else PassportStatus.Ok
        return PassportCheck(status, rule.months, rule.basis, key)
    }

    /** 저장된 결과가 지금 여행·기준과 맞는지(날짜를 고쳤으면 다시 계산할 때까지 쓰지 않는다) */
    fun isCurrent(check: PassportCheck?, trip: Trip, rule: PassportValidityRule?): Boolean = check != null && check.key == key(trip, rule)
}
