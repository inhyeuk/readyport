package com.readyport.autofill

import com.readyport.vault.BookingRecord
import com.readyport.vault.VaultContents
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 값이 어디서 왔는지 — 확인 화면의 출처 칩 (PRD 5.2) */
enum class ValueOrigin { Passport, Flight, Lodging, User, None }

/**
 * @param value 공식 사이트에 넣을 값 (text 칸)
 * @param display 사람에게 보여 줄 값 (assist 말풍선, 확인 화면)
 */
data class FieldValue(val key: String, val value: String?, val display: String?, val origin: ValueOrigin) {
    val isEmpty: Boolean get() = display.isNullOrBlank()
    override fun toString() = "FieldValue($key, origin=$origin, empty=$isEmpty)"
}

/**
 * 레시피의 각 칸에 들어갈 값을 여권·예약 서류·사용자 입력에서 모은다. 순수 함수라 JVM 테스트로 검증한다.
 * 세관·건강·서약 질문은 여기서 만들지 않는다 — 사람이 사이트에서 직접 답한다 (PRD 2.2).
 */
object FormValues {

    fun build(recipe: Recipe, vault: VaultContents, saved: Map<String, String>): Map<String, FieldValue> =
        recipe.fields.associate { f -> f.key to valueFor(recipe, f, vault, saved) }

    private fun valueFor(recipe: Recipe, f: RecipeField, vault: VaultContents, saved: Map<String, String>): FieldValue {
        // 사용자가 고르거나 고친 값이 가장 먼저
        saved[f.key]?.takeIf { it.isNotBlank() }?.let { raw ->
            val opt = f.optionsRef?.let { ref -> recipe.options[ref]?.firstOrNull { it.value == raw } }
            return if (opt != null) {
                FieldValue(f.key, "${opt.ko} (${opt.en})", listOfNotNull(opt.ko, opt.en, opt.local).joinToString(" · "), ValueOrigin.User)
            } else {
                val v = transform(f, raw)
                FieldValue(f.key, v, v, ValueOrigin.User)
            }
        }
        val p = vault.passport
        val flights = vault.bookings.filter { it.kind == "flight" }.sortedBy { it.dates.firstOrNull() ?: "9999" }
        val lodging = vault.bookings.filter { it.kind == "lodging" }.sortedBy { it.checkIn ?: "9999" }
        fun passport(v: String?) = FieldValue(f.key, v?.let { transform(f, it) }, v?.let { transform(f, it) }, if (v == null) ValueOrigin.None else ValueOrigin.Passport)
        fun none() = FieldValue(f.key, null, null, ValueOrigin.None)

        return when (f.key) {
            "passport.surname" -> passport(p?.surname)
            "passport.given" -> passport(p?.givenNames)
            "passport.number" -> passport(p?.documentNumber)
            "passport.nationality" -> p?.let {
                val d = nationalityLabel(it.nationality)
                FieldValue(f.key, d, d, ValueOrigin.Passport)
            } ?: none()
            "passport.birth_date" -> p?.let { FieldValue(f.key, it.birthDate, it.birthDate, ValueOrigin.Passport) } ?: none()
            "passport.gender" -> p?.let {
                val d = when (it.sex) { "M" -> "남 (Male)"; "F" -> "여 (Female)"; else -> "기타 (Other)" }
                FieldValue(f.key, d, d, ValueOrigin.Passport)
            } ?: none()
            "trip.arrival_date" -> firstDate(flights.firstOrNull())?.let { FieldValue(f.key, it, it, ValueOrigin.Flight) }
                ?: lodging.firstOrNull()?.checkIn?.let { FieldValue(f.key, it, it, ValueOrigin.Lodging) }
                ?: none()
            "trip.arrival_mode" -> if (flights.isNotEmpty()) FieldValue(f.key, "비행기 (Air)", "비행기 (Air)", ValueOrigin.Flight) else none()
            "trip.flight_no" -> flights.firstOrNull()?.flightNumbers?.firstOrNull()
                ?.let { FieldValue(f.key, transform(f, it), transform(f, it), ValueOrigin.Flight) } ?: none()
            "trip.departure_date" -> lastFlightDate(flights)?.let { FieldValue(f.key, it, it, ValueOrigin.Flight) }
                ?: lodging.lastOrNull()?.checkOut?.let { FieldValue(f.key, it, it, ValueOrigin.Lodging) }
                ?: none()
            "trip.departure_flight_no" -> departureFlight(flights)?.let { FieldValue(f.key, transform(f, it), transform(f, it), ValueOrigin.Flight) } ?: none()
            else -> none()
        }
    }

    private fun transform(f: RecipeField, v: String) = if (f.transform == "upper") v.trim().uppercase() else v.trim()

    private fun firstDate(b: BookingRecord?) = b?.dates?.firstOrNull()

    /** 항공권이 둘 이상이거나, 한 장에 편명이 둘 이상이면 마지막 것을 돌아오는 편으로 본다 */
    private fun lastFlightDate(flights: List<BookingRecord>): String? {
        val all = flights.flatMap { it.dates }.sorted()
        return if (all.size >= 2) all.last() else null
    }

    private fun departureFlight(flights: List<BookingRecord>): String? {
        val all = flights.flatMap { it.flightNumbers }
        return if (all.size >= 2) all.last() else null
    }

    fun nationalityLabel(code: String) = if (code == "KOR") "대한민국 (KOR)" else code

    /** 확인 화면을 통과할 수 있는지: 필수 칸이 모두 채워졌는지 */
    fun missingRequired(recipe: Recipe, values: Map<String, FieldValue>): List<RecipeField> =
        recipe.fields.filter { it.required && values[it.key]?.isEmpty != false }

    /**
     * 엔진에 넘길 계획(JSON). 지금 화면에 보이는 단계의 칸만.
     * 값은 kotlinx JSON으로 만들어서 따옴표·특수문자가 안전하게 들어간다.
     */
    fun plan(recipe: Recipe, values: Map<String, FieldValue>, visibleSteps: Set<String>): JsonObject = buildJsonObject {
        val fields = recipe.steps.filter { it.id in visibleSteps }.flatMap { it.fields }.map { f ->
            val v = values[f.key]
            buildJsonObject {
                put("key", f.key)
                put("selector", f.selector?.let { JsonPrimitive(it) } ?: JsonNull)
                put("widget", f.widget)
                put("value", (if (f.widget == "text") v?.value else v?.display).orEmpty())
                put("label", f.labels.ko)
                put("hint", f.hintKo?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        }
        put("fields", JsonArray(fields))
        put("checkpoints", JsonArray(recipe.checkpoints.map { c ->
            buildJsonObject {
                put("id", c.id)
                put("selector", c.selector?.let { JsonPrimitive(it) } ?: JsonNull)
                put("ko", c.ko)
            }
        }))
    }
}
