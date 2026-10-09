package com.readyport.plan

import com.readyport.board.PiiGuard
import com.readyport.board.PiiHit
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** 양식 검사에서 찾은 문제 (화면이 문구로 바꾼다) */
enum class PlanProblem {
    Country,
    Purposes,
    NoteTooLong,

    /** 메모에 여권 번호·주민번호·MRZ 같은 글자 — 고쳐야 보낼 수 있다 */
    NotePii,
    Travelers,
    Genders,
    Dates,
    Budget,

    /** 이동 조건을 골랐는데 민감정보 별도 동의를 하지 않음 */
    Consent,
}

/** 검사 결과: [problems]가 비고 경고(전화·이메일)를 넘겼으면 보낼 수 있다 */
data class PlanCheck(val problems: Set<PlanProblem>, val pii: List<PiiHit>) {
    /** 경고만(전화·이메일) — `이대로 보내기`로 넘길 수 있다 */
    val warnOnly: Boolean get() = pii.isNotEmpty() && !PiiGuard.blocking(pii)
    fun ready(allowWarnings: Boolean): Boolean = problems.isEmpty() && (!warnOnly || allowWarnings)
}

/**
 * 규칙(firebase/firestore.rules newPlanOk·plan_quota)과 **같은 모양**의 요청을 만든다 (순수 함수 — PlanRulesTest).
 * - 앱이 먼저 같은 기준으로 막고, 규칙·ARIA가 한 번 더 본다.
 * - 날짜: 여행 날짜(시작~끝, 30일 이하) 또는 며칠(1~30) 중 하나만.
 */
object PlanRules {
    val COUNTRIES: List<String> = listOf("TH", "JP", "VN", "PH", "TW", "SG", "MY", "ID", "CN")
    const val MAX_PURPOSES = 5
    const val NOTE_MAX = 200
    const val MAX_PER_GROUP = 20
    const val MAX_TOTAL = 20
    const val MAX_DAYS = 30

    /** 1인 7일에 2번 (규칙: 직전 prev 가 7일 안이면 거절) */
    const val WEEKLY_LIMIT = 2
    val WINDOW: Duration = Duration.ofDays(7)

    private val IsoDate = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")

    fun check(d: PlanDraft): PlanCheck {
        val problems = mutableSetOf<PlanProblem>()
        if (d.country !in COUNTRIES) problems += PlanProblem.Country
        if (d.purposes.isEmpty() || d.purposes.size > MAX_PURPOSES || d.purposes.toSet().size != d.purposes.size) problems += PlanProblem.Purposes
        val note = d.note.trim()
        if (note.length > NOTE_MAX) problems += PlanProblem.NoteTooLong
        val pii = if (note.isEmpty()) emptyList() else PiiGuard.scan(note)
        if (PiiGuard.blocking(pii)) problems += PlanProblem.NotePii
        val t = d.travelers
        val groups = listOf(t.adults, t.seniors, t.teens, t.children)
        if (groups.any { it !in 0..MAX_PER_GROUP } || t.total !in 1..MAX_TOTAL) problems += PlanProblem.Travelers
        val genders = listOfNotNull(t.female, t.male)
        if (genders.any { it !in 0..MAX_PER_GROUP } || genders.sum() > t.total) problems += PlanProblem.Genders
        if (days(d.dates) == null) problems += PlanProblem.Dates
        if (d.budget == null) problems += PlanProblem.Budget
        if (d.mobility.isNotEmpty() && !d.sensitiveConsent) problems += PlanProblem.Consent
        return PlanCheck(problems, pii)
    }

    /** 여행 일수 (1~30). 날짜가 잘못됐거나 30일을 넘으면 null */
    fun days(dates: PlanDates): Int? = when (dates) {
        is PlanDates.Days -> dates.count.takeIf { it in 1..MAX_DAYS }
        is PlanDates.Range -> {
            val s = parse(dates.start)
            val e = parse(dates.end)
            if (s == null || e == null || e.isBefore(s)) null else (ChronoUnit.DAYS.between(s, e) + 1).toInt().takeIf { it in 1..MAX_DAYS }
        }
    }

    private fun parse(s: String): LocalDate? =
        if (!IsoDate.matches(s)) null else try { LocalDate.parse(s) } catch (e: DateTimeParseException) { null }

    /**
     * plan_requests/{id} 문서 (규칙 newPlanOk 의 칸 그대로). [serverTime] = 서버 시각 자리(Firestore FieldValue.serverTimestamp()).
     * - 목적은 고른 순서가 아니라 정해진 순서로(같은 요청이면 같은 모양).
     * - 메모·성별·이동 조건은 있을 때만. 이동 조건이 없으면 sensitive_consent 칸 자체를 넣지 않는다(규칙).
     * 양식이 덜 됐으면 [PlanError.Invalid].
     */
    fun payload(d: PlanDraft, uid: String, serverTime: Any): Map<String, Any> {
        if (!check(d).ready(allowWarnings = true)) throw PlanError.Invalid
        val t = d.travelers
        val travelers = buildMap<String, Any> {
            put("adults", t.adults)
            put("seniors", t.seniors)
            put("teens", t.teens)
            put("children", t.children)
            val genders = buildMap<String, Any> {
                t.female?.let { put("female", it) }
                t.male?.let { put("male", it) }
            }
            if (genders.isNotEmpty()) put("genders", genders)
        }
        return buildMap {
            put("uid", uid)
            put("country", d.country!!)
            put("purposes", PlanPurpose.entries.filter { it in d.purposes }.map { it.id })
            d.note.trim().takeIf { it.isNotEmpty() }?.let { put("purpose_note", it) }
            put("travelers", travelers)
            if (d.mobility.isNotEmpty()) {
                put("mobility", PlanMobility.entries.filter { it in d.mobility }.map { it.id })
                put("sensitive_consent", true)
            }
            when (val dates = d.dates) {
                is PlanDates.Range -> {
                    put("start_date", dates.start)
                    put("end_date", dates.end)
                }
                is PlanDates.Days -> put("days", dates.count)
            }
            put("budget_band", d.budget!!.id)
            put("currency", "KRW")
            put("status", PlanStatus.Queued.id)
            put("createdAt", serverTime)
        }
    }

    /**
     * plan_quota/{uid} 를 요청과 **같은 묶음**에서 쓸 값: last = 지금(서버 시각), prev = 직전 last(처음이면 null), lastRequestId = 새 요청 id.
     * [previousLast]는 서버에서 읽은 값을 **그대로** 넘긴다(규칙이 prev == 직전 last 를 같은 값으로 비교).
     */
    fun quotaPayload(previousLast: Any?, requestId: String, serverTime: Any): Map<String, Any?> =
        mapOf("last" to serverTime, "prev" to previousLast, "lastRequestId" to requestId)

    /** 이번 7일 안에 더 보낼 수 있는 횟수와, 0이면 다시 보낼 수 있는 때 */
    data class Remaining(val count: Int, val nextAt: Instant?)

    /** 규칙과 같은 셈: 다음 요청은 직전 prev 가 없거나 7일보다 오래됐을 때만 */
    fun remaining(q: PlanQuota?, now: Instant): Remaining {
        val last = q?.last ?: return Remaining(WEEKLY_LIMIT, null)
        val windowStart = now.minus(WINDOW)
        val prev = q.prev
        return when {
            prev != null && !prev.isBefore(windowStart) -> Remaining(0, prev.plus(WINDOW))
            last.isBefore(windowStart) -> Remaining(WEEKLY_LIMIT, null)
            else -> Remaining(WEEKLY_LIMIT - 1, null)
        }
    }
}
