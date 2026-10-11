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

/** 신고 메모 검사 결과: [blocked]면 보낼 수 없다(200자 넘음 또는 여권·주민번호 같은 글자). 전화·이메일은 경고만 */
data class FlagNoteCheck(val tooLong: Boolean, val pii: List<PiiHit>) {
    val blocked: Boolean get() = tooLong || PiiGuard.blocking(pii)
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

    /**
     * 나라마다 2번 (2026-10-11 사장님 결정 — 시험 운영, 나중에 유료로 횟수를 늘릴 계획). 기간 제한은 없다(누적).
     * 규칙(firebase/firestore.rules plan_quota)과 같은 값. 늘린 횟수는 plan_quota.extra[나라]로 서버가 더해 준다.
     */
    const val COUNTRY_LIMIT = 2

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
    fun quotaPayload(previous: PlanQuota?, country: String, requestId: String, serverTime: Any): Map<String, Any?> {
        val counts = (previous?.counts ?: emptyMap()).toMutableMap()
        counts[country] = (counts[country] ?: 0) + 1
        return buildMap {
            put("last", serverTime)
            put("lastRequestId", requestId)
            put("counts", counts.toMap())
            // 서버가 더해 준 추가 횟수는 그대로 되돌려 쓴다(규칙이 같은 값인지 본다)
            previous?.extra?.takeIf { it.isNotEmpty() }?.let { put("extra", it) }
        }
    }

    /** 신고 메모 최대 글자 수 (규칙 plan_flags: note 1~200자) */
    const val FLAG_NOTE_MAX = 200

    /**
     * 신고 메모 검사: 200자 넘음 · 개인정보처럼 보이는 글자(게시판과 같은 [PiiGuard]).
     * 여권 번호·MRZ·주민번호는 막고([FlagNoteCheck.blocked]), 전화·이메일은 경고만.
     */
    fun checkFlagNote(note: String): FlagNoteCheck {
        val n = note.trim()
        return FlagNoteCheck(tooLong = n.length > FLAG_NOTE_MAX, pii = if (n.isEmpty()) emptyList() else PiiGuard.scan(n))
    }

    /**
     * plan_flags/{요청 id} 문서 (규칙의 칸 그대로): uid · reason · note(있을 때만, 다듬어서) · at(서버 시각 자리 [serverTime]).
     * 메모가 막히는 경우(200자 넘음·여권/주민번호 같은 글자)면 [PlanError.Invalid].
     */
    fun flagPayload(reason: PlanFlagReason, note: String, uid: String, serverTime: Any): Map<String, Any> {
        if (checkFlagNote(note).blocked) throw PlanError.Invalid
        return buildMap {
            put("uid", uid)
            put("reason", reason.id)
            note.trim().takeIf { it.isNotEmpty() }?.let { put("note", it) }
            put("at", serverTime)
        }
    }

    /** 이 나라에 더 보낼 수 있는 횟수. [nextAt]은 예전 7일 기준 자리라 늘 null이다 */
    data class Remaining(val count: Int, val nextAt: Instant? = null)

    /** 나라별 한도(기본 2 + 서버가 더해 준 [PlanQuota.extra]) */
    fun limitFor(q: PlanQuota?, country: String): Int = COUNTRY_LIMIT + (q?.extra?.get(country) ?: 0)

    /** 규칙과 같은 셈: 이 나라 누적 횟수가 한도보다 작아야 한다 */
    fun remaining(q: PlanQuota?, country: String): Remaining =
        Remaining((limitFor(q, country) - (q?.counts?.get(country) ?: 0)).coerceAtLeast(0))
}
