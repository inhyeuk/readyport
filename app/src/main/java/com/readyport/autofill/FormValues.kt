package com.readyport.autofill

import com.readyport.stay.Stays
import com.readyport.vault.BookingRecord
import com.readyport.vault.StayRecord
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
        // '한 번 더 적기' 칸(예: 이메일 확인)은 원래 칸 값을 그대로
        if (f.key.endsWith("_confirm")) {
            val base = saved[f.key.removeSuffix("_confirm")]?.trim().orEmpty()
            return if (base.isEmpty()) FieldValue(f.key, null, null, ValueOrigin.None) else FieldValue(f.key, base, base, ValueOrigin.User)
        }
        // 사용자가 고르거나 고친 값이 가장 먼저
        saved[f.key]?.takeIf { it.isNotBlank() }?.let { raw ->
            optionValue(recipe, f, raw, ValueOrigin.User)?.let { return it }
            val v = f.siteMap[raw] ?: transform(f, raw)
            return FieldValue(f.key, v, raw.takeIf { f.siteMap.containsKey(raw) } ?: v, ValueOrigin.User)
        }
        val p = vault.passport
        val flights = vault.bookings.filter { it.kind == "flight" }.sortedBy { it.dates.firstOrNull() ?: "9999" }
        val stays = Stays.sorted(vault.stays)
        // 도착한 날 묵는 곳(없으면 첫 숙소) — 날짜별로 숙소가 다른 여행에서 입국 카드에 들어갈 숙소 (2026-10-03)
        val stay = arrivalStay(vault, flights, stays)
        fun passport(v: String?) = FieldValue(f.key, v?.let { transform(f, it) }, v?.let { transform(f, it) }, if (v == null) ValueOrigin.None else ValueOrigin.Passport)
        fun none() = FieldValue(f.key, null, null, ValueOrigin.None)
        fun lodging(v: String?) = v?.takeIf { it.isNotBlank() }?.trim()
            ?.let { FieldValue(f.key, transform(f, it), transform(f, it), ValueOrigin.Lodging) } ?: none()

        return when (f.key) {
            "passport.surname" -> passport(p?.surname)
            "passport.given" -> passport(p?.givenNames)
            "passport.number" -> passport(p?.documentNumber)
            "passport.nationality" -> p?.let {
                val d = nationalityLabel(it.nationality)
                // select 칸은 사이트 선택지 글자, 말풍선은 한글
                FieldValue(f.key, f.siteMap[it.nationality] ?: d, d, ValueOrigin.Passport)
            } ?: none()
            "passport.full_name_given_first" -> p?.let {
                val v = transform(f, "${it.givenNames} ${it.surname}".trim())
                FieldValue(f.key, v, v, ValueOrigin.Passport)
            } ?: none()
            "passport.full_name_surname_first" -> p?.let {
                val v = transform(f, "${it.surname} ${it.givenNames}".trim())
                FieldValue(f.key, v, v, ValueOrigin.Passport)
            } ?: none()
            "passport.expiry_date" -> p?.let { FieldValue(f.key, transform(f, it.expiryDate), it.expiryDate, ValueOrigin.Passport) } ?: none()
            "passport.birth_date" -> p?.let { FieldValue(f.key, transform(f, it.birthDate), it.birthDate, ValueOrigin.Passport) } ?: none()
            "passport.gender" -> p?.let {
                val d = when (it.sex) { "M" -> "남 → MALE"; "F" -> "여 → FEMALE"; else -> "기타 → UNDEFINED" }
                FieldValue(f.key, f.siteMap[it.sex] ?: d, d, ValueOrigin.Passport)
            } ?: none()
            "trip.arrival_date" -> firstDate(flights.firstOrNull())?.let { FieldValue(f.key, transform(f, it), it, ValueOrigin.Flight) }
                ?: stays.firstNotNullOfOrNull { it.checkIn }?.let { FieldValue(f.key, transform(f, it), it, ValueOrigin.Lodging) }
                ?: none()
            "trip.arrival_mode" -> if (flights.isNotEmpty()) f.siteValue?.takeIf { f.widget == "select" }
                ?.let { FieldValue(f.key, it, "비행기 → $it", ValueOrigin.Flight) } ?: plane(f) else none()
            "trip.departure_mode" -> if (departureFlight(flights) != null) plane(f) else none()
            "trip.flight_no" -> flights.firstOrNull()?.flightNumbers?.firstOrNull()
                ?.let { FieldValue(f.key, transform(f, it), transform(f, it), ValueOrigin.Flight) } ?: none()
            // 항공사 코드와 숫자를 따로 받는 사이트 (예: KE / 651)
            "trip.flight_prefix" -> flights.firstOrNull()?.flightNumbers?.firstOrNull()?.let { splitFlight(it) }
                ?.let { FieldValue(f.key, it.first, it.first, ValueOrigin.Flight) } ?: none()
            "trip.flight_digits" -> flights.firstOrNull()?.flightNumbers?.firstOrNull()?.let { splitFlight(it) }
                ?.let { FieldValue(f.key, it.second, it.second, ValueOrigin.Flight) } ?: none()
            "trip.departure_date" -> lastFlightDate(flights)?.let { FieldValue(f.key, transform(f, it), it, ValueOrigin.Flight) }
                ?: stays.mapNotNull { it.checkOut }.maxOrNull()?.let { FieldValue(f.key, transform(f, it), it, ValueOrigin.Lodging) }
                ?: none()
            "trip.departure_flight_no" -> departureFlight(flights)?.let { FieldValue(f.key, transform(f, it), transform(f, it), ValueOrigin.Flight) } ?: none()
            // 묵는 곳 — 저장해 둔 숙소에서 (2026-10-03). 주소·이름은 글자 그대로, 종류는 그 나라 사이트 선택지에 **있을 때만**.
            // 주·구·동·우편번호는 사이트 목록에서 고르는 칸이라 여기서 만들지 않는다(없는 값을 지어내지 않는다 — 화면이 저장한 주소를 보여 주고 사람이 고른다)
            "stay.address" -> lodging(stay?.addressLocal)
            "stay.hotel" -> lodging(stay?.name)
            "stay.type" -> stay?.type?.let { optionValue(recipe, f, it, ValueOrigin.Lodging) } ?: none()
            else -> none()
        }
    }

    /** 레시피 선택지에 있는 값인지 보고, 있으면 사이트 글자까지 갖춘 값으로 (없으면 null — 사이트에 없는 선택지를 만들지 않는다) */
    private fun optionValue(recipe: Recipe, f: RecipeField, raw: String, origin: ValueOrigin): FieldValue? {
        val opt = f.optionsRef?.let { ref -> recipe.options[ref]?.firstOrNull { it.value == raw } } ?: return null
        // 사이트 말풍선에는 "관광 → HOLIDAY"처럼 골라야 할 사이트 글자를 함께
        val forSite = when {
            // 기본 선택 목록은 사이트 글자 그대로여야 엔진이 고를 수 있다
            f.widget == "select" && opt.site != null -> opt.site
            opt.site != null -> "${opt.ko} → ${opt.site}"
            else -> "${opt.ko} (${opt.en})"
        }
        return FieldValue(f.key, forSite, listOfNotNull(opt.ko, opt.en, opt.local).joinToString(" · "), origin)
    }

    /**
     * 입국 카드에 넣을 숙소: **도착한 날 묵는 곳**(날짜별로 숙소가 다를 수 있다), 없으면 첫 숙소.
     * 도착한 날은 항공권 첫 날짜 → 없으면 가장 이른 체크인으로 본다(여행 화면이 아니어서 여행 날짜를 모른다).
     */
    private fun arrivalStay(vault: VaultContents, flights: List<BookingRecord>, stays: List<StayRecord>): StayRecord? {
        if (stays.isEmpty()) return null
        val arrival = (firstDate(flights.firstOrNull()) ?: stays.firstNotNullOfOrNull { it.checkIn })
            ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        return arrival?.let { Stays.on(stays, it) } ?: stays.first()
    }

    /**
     * 확인 화면 초안에 미리 넣어 둘 값 — 레시피 제안값([defaults])과 함께 쓴다.
     * 지금은 **숙소 종류** 하나: 고르는 칸(user_choice)이라 초안에 들어가야 화면에 골라진 모습으로 보인다(사람이 바꿀 수 있다).
     */
    fun suggest(recipe: Recipe, vault: VaultContents): Map<String, String> {
        val stays = Stays.sorted(vault.stays)
        val stay = arrivalStay(vault, vault.bookings.filter { it.kind == "flight" }.sortedBy { it.dates.firstOrNull() ?: "9999" }, stays)
        val type = stay?.type ?: return emptyMap()
        return recipe.fields.filter { it.key == "stay.type" }
            .filter { f -> f.optionsRef?.let { recipe.options[it]?.any { o -> o.value == type } } == true }
            .associate { it.key to type }
    }

    private fun transform(f: RecipeField, v: String) = when (f.transform) {
        "upper" -> v.trim().uppercase()
        // 사이트 날짜 칸 형식 yyyy/mm/dd (실기기에서 확인)
        "date_slash" -> v.trim().replace('-', '/')
        // dd/MM/yyyy (SGAC·MDAC·All Indonesia, 실기기 확인)
        "date_dmy" -> v.trim().split('-').takeIf { it.size == 3 }?.let { (y, m, d) -> "$d/$m/$y" } ?: v.trim()
        else -> v.trim()
    }

    /** "KE651" → ("KE", "651"), "7C2201" → ("7C", "2201") */
    internal fun splitFlight(no: String): Pair<String, String>? =
        Regex("^([A-Z0-9]{2})(\\d{1,4})$").matchEntire(no.trim().uppercase().replace(" ", ""))?.let { it.groupValues[1] to it.groupValues[2] }

    private fun plane(f: RecipeField): FieldValue {
        val d = f.siteValue?.let { "비행기 → $it" } ?: "비행기"
        return FieldValue(f.key, d, d, ValueOrigin.Flight)
    }

    /** 레시피의 제안 값 (확인 화면에 미리 넣어 두고 사람이 고친다) */
    fun defaults(recipe: Recipe): Map<String, String> =
        recipe.fields.mapNotNull { f -> f.defaultValue?.let { f.key to it } }.toMap()

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
                // text 칸은 넣을 값, assist 칸은 사이트에서 고를 글자가 담긴 값("관광 → HOLIDAY")
                put("value", (v?.value ?: v?.display).orEmpty())
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
