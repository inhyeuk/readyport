package com.readyport.plan

import java.time.Instant

/** 하루 안의 때 (ARIA TIME_HINTS) — 시각 숫자 대신 쓴다 */
enum class TimeHint(val id: String) {
    Morning("morning"),
    LateMorning("late_morning"),
    Lunch("lunch"),
    Afternoon("afternoon"),
    Evening("evening"),
    Night("night"),
    ;

    companion object {
        fun of(id: String?): TimeHint? = entries.firstOrNull { it.id == id }
    }
}

data class PlanItem(val timeHint: TimeHint?, val placeId: String?, val title: String, val note: String)

data class PlanDay(val day: Int, val title: String, val items: List<PlanItem>)

/** 받은 계획 (plan_results/{id}.plan + 고지) */
data class PlanResult(
    val requestId: String,
    val country: String,
    val days: List<PlanDay>,
    val tips: List<String>,
    val budgetNotes: List<String>,
    val caveats: List<String>,
    /** ARIA가 넣은 AI 고지 글(없으면 화면 기본 문구) */
    val noticeKo: String?,
    val createdAt: Instant?,
)

/**
 * plan_results 문서(Firestore 지도 모양) → [PlanResult]. **관대하게 읽는다**: 모르는 칸은 무시, 모양이 틀린 항목·빈 글은 빼고,
 * 너무 긴 글은 자른다(화면·PDF가 깨지지 않게). 하루도 없으면 null(보여 줄 것이 없음).
 */
object PlanResultParser {
    const val TITLE_MAX = 80
    const val TEXT_MAX = 400
    const val MAX_DAYS = PlanRules.MAX_DAYS
    const val MAX_ITEMS = 10
    const val MAX_LINES = 10
    private val PlaceId = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    fun parse(id: String, doc: Map<String, Any?>?): PlanResult? {
        if (doc == null) return null
        val plan = doc["plan"] as? Map<*, *> ?: return null
        val days = (plan["days"] as? List<*>).orEmpty().take(MAX_DAYS).mapIndexedNotNull { i, raw ->
            val d = raw as? Map<*, *> ?: return@mapIndexedNotNull null
            val items = (d["items"] as? List<*>).orEmpty().take(MAX_ITEMS).mapNotNull { r ->
                val it = r as? Map<*, *> ?: return@mapNotNull null
                val title = text(it["title"], TITLE_MAX) ?: return@mapNotNull null
                PlanItem(
                    timeHint = TimeHint.of(it["time_hint"] as? String),
                    placeId = (it["place_id"] as? String)?.takeIf { p -> p.length <= 80 && PlaceId.matches(p) },
                    title = title,
                    note = text(it["note"], TEXT_MAX).orEmpty(),
                )
            }
            if (items.isEmpty()) null else PlanDay(day = i + 1, title = text(d["title"], TITLE_MAX).orEmpty(), items = items)
        }.mapIndexed { i, d -> d.copy(day = i + 1) }
        if (days.isEmpty()) return null
        return PlanResult(
            requestId = (doc["request_id"] as? String)?.takeIf { it.isNotBlank() } ?: id,
            country = (doc["country"] as? String).orEmpty(),
            days = days,
            tips = lines(plan["tips"]),
            budgetNotes = lines(plan["budget_notes"]),
            caveats = lines(plan["caveats"]),
            noticeKo = text(doc["notice_ko"], TEXT_MAX),
            createdAt = doc["createdAt"] as? Instant,
        )
    }

    private fun lines(v: Any?): List<String> = (v as? List<*>).orEmpty().mapNotNull { text(it, TEXT_MAX) }.take(MAX_LINES)

    /** 글: 앞뒤 공백·제어 문자를 빼고, 비면 null, 길면 [max]자에서 자르고 `…` */
    internal fun text(v: Any?, max: Int): String? {
        val s = (v as? String)?.replace(Regex("[\\u0000-\\u0008\\u000B-\\u001F\\u007F]"), "")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (s.length > max) s.take(max - 1).trimEnd() + "…" else s
    }
}
