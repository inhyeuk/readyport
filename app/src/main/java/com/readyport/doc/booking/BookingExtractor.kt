package com.readyport.doc.booking

import java.time.LocalDate
import java.util.Locale

enum class BookingKind { Flight, Lodging, Unknown }

/** 예약 서류에서 뽑은 후보 값. 사용자가 확인·수정한 뒤에만 저장한다 (PRD 7.2). */
data class BookingFields(
    val kind: BookingKind,
    val reference: String?,
    val flightNumbers: List<String>,
    val dates: List<LocalDate>,
    val checkIn: LocalDate?,
    val checkOut: LocalDate?,
    /** 숙소 이름 후보 (숙소 서류일 때만). 사용자가 확인 화면에서 고친다 */
    val stayName: String? = null,
    /** 숙소 주소 후보 (숙소 서류일 때만) */
    val stayAddress: String? = null,
) {
    override fun toString(): String =
        "BookingFields(kind=$kind, flights=${flightNumbers.size}, dates=${dates.size}, " +
            "hasName=${stayName != null}, hasAddress=${stayAddress != null})"
}

/**
 * 공유받은 텍스트·OCR 결과에서 예약번호·편명·날짜를 규칙(정규식)으로 뽑는다.
 * 기기 밖으로 아무것도 보내지 않는다. 틀릴 수 있으므로 결과는 항상 확인 화면을 거친다.
 */
object BookingExtractor {

    fun extract(text: String): BookingFields {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val flights = findFlightNumbers(lines)
        val dates = lines.flatMap(::findDates).distinct().sorted()
        val checkIn = dateNear(lines, CHECK_IN_WORDS)
        val checkOut = dateNear(lines, CHECK_OUT_WORDS)
        val lodgingHint = lines.any { line -> LODGING_WORDS.any { line.contains(it, ignoreCase = true) } }
        val kind = when {
            flights.isNotEmpty() && !lodgingHint -> BookingKind.Flight
            lodgingHint || checkOut != null -> BookingKind.Lodging
            flights.isNotEmpty() -> BookingKind.Flight
            else -> BookingKind.Unknown
        }
        val lodging = kind == BookingKind.Lodging
        val address = if (lodging) findStayAddress(lines) else null
        return BookingFields(
            kind = kind,
            reference = findReference(lines),
            flightNumbers = flights,
            dates = dates,
            // 항공권의 '출발/도착'을 숙소 체크인·아웃으로 오해하지 않게 숙소일 때만 채운다
            checkIn = checkIn.takeIf { lodging },
            checkOut = checkOut.takeIf { lodging },
            stayName = if (lodging) findStayName(lines, address) else null,
            stayAddress = address,
        )
    }

    // --- 숙소 이름·주소 (2026-10-03) ---
    // 규칙(정규식)만으로 **후보**를 뽑는다. 틀릴 수 있으므로 확인 화면에서 사람이 보고 고친 뒤에만 저장한다(PRD 7.2).
    // 기기 밖으로 아무것도 보내지 않고, 못 찾으면 null로 둔다 — 지어내지 않는다.

    private val NAME_LABELS = listOf("숙소명", "숙소 이름", "호텔명", "호텔 이름", "property name", "hotel name", "accommodation name")
    private val ADDRESS_LABELS = listOf("주소", "소재지", "address", "location")
    /** 라벨 줄처럼 보이지만 숙소 이름이 아닌 줄 (`호텔 예약 확인서`) */
    private val NAME_REJECT = listOf("예약", "확인서", "바우처", "영수증", "reservation", "confirmation", "voucher", "booking", "itinerary", "receipt")
    private val ADDRESS_REJECT = listOf("이메일", "메일", "email", "e-mail", "웹", "url", "http")
    private val NAME_WORDS = listOf(
        "호텔", "리조트", "레지던스", "게스트하우스", "호스텔", "료칸",
        "hotel", "resort", "residence", "guest house", "guesthouse", "hostel", "ryokan", "inn ",
    )

    /** 라벨이 줄 앞쪽에 있는 줄의 값(같은 줄 뒤 → 없으면 다음 줄) */
    private fun labelled(lines: List<String>, labels: List<String>, reject: List<String>, maxLength: Int): String? {
        for ((i, line) in lines.withIndex()) {
            val lower = line.lowercase(Locale.ROOT)
            if (reject.any { lower.contains(it) }) continue
            val label = labels.firstOrNull { lower.indexOf(it).let { at -> at in 0..LABEL_HEAD } } ?: continue
            val after = line.substring(lower.indexOf(label) + label.length).trim(' ', ':', '：', '-', '=', '\t', ',', '>')
            val value = after.ifEmpty { lines.getOrNull(i + 1)?.trim().orEmpty() }
            if (value.length in 2..maxLength && NAME_REJECT.none { value.lowercase(Locale.ROOT).contains(it) }) return value
        }
        return null
    }

    /** 라벨을 줄 앞쪽에서만 찾는다 — 글 가운데 '주소'(예: "이 주소로 가 주세요")를 라벨로 보지 않게 */
    private const val LABEL_HEAD = 12

    internal fun findStayAddress(lines: List<String>): String? =
        labelled(lines, ADDRESS_LABELS, ADDRESS_REJECT, maxLength = 160)?.takeIf { it.length >= 5 }

    /**
     * 숙소 이름: ① `숙소명` 같은 라벨 값 → ② 라벨이 없으면 '호텔·리조트·Hotel' 같은 말이 든 줄.
     * 서류 제목 줄(`호텔 예약 확인서`)과 주소 줄은 빼고, 너무 긴 줄(문장)은 이름으로 보지 않는다.
     */
    internal fun findStayName(lines: List<String>, address: String?): String? {
        labelled(lines, NAME_LABELS, emptyList(), maxLength = 60)?.let { return it }
        return lines.firstOrNull { line ->
            val lower = line.lowercase(Locale.ROOT)
            line.length in 2..60 && line != address &&
                NAME_WORDS.any { lower.contains(it) } &&
                NAME_REJECT.none { lower.contains(it) } &&
                ADDRESS_LABELS.none { lower.indexOf(it).let { at -> at in 0..LABEL_HEAD } }
        }?.trim(' ', ':', '：', '-', '\t')
    }

    // --- 예약번호 ---

    private val REFERENCE_LABELS = listOf(
        "예약번호", "예약 번호", "확인번호", "확인 번호", "예약코드", "예약 코드",
        "booking reference", "booking ref", "booking number", "booking no", "booking id",
        "confirmation number", "confirmation no", "confirmation code", "confirmation",
        "reservation number", "reservation code", "reservation no", "pnr", "itinerary number",
    )
    private val REFERENCE_VALUE = Regex("""[A-Z0-9][A-Z0-9-]{4,19}""")

    private fun findReference(lines: List<String>): String? {
        for ((i, line) in lines.withIndex()) {
            val lower = line.lowercase(Locale.ROOT)
            val label = REFERENCE_LABELS.firstOrNull { lower.contains(it) } ?: continue
            val after = line.substring(lower.indexOf(label) + label.length)
            // 같은 줄 뒤쪽 → 없으면 다음 줄
            val value = REFERENCE_VALUE.find(after.uppercase(Locale.ROOT))?.value
                ?: lines.getOrNull(i + 1)?.let { next -> REFERENCE_VALUE.matchEntire(next.uppercase(Locale.ROOT).replace(" ", ""))?.value }
            if (value != null && value.any { it.isDigit() || it.isLetter() }) return value
        }
        return null
    }

    // --- 편명 ---

    /**
     * 항공사 코드(IATA 2자리) + 숫자 1~4자리. 오탐을 줄이려고
     * ① 한국 출발 노선에서 흔한 항공사 코드이거나 ② 같은 줄에 '편명/flight' 같은 말이 있을 때만 인정한다.
     */
    private val FLIGHT = Regex("""\b([A-Z][A-Z0-9]|[0-9][A-Z])\s?(\d{1,4})\b""")
    private val FLIGHT_WORDS = listOf("편명", "항공편", "flight", "flt")
    // IATA 항공사 지정 코드(공개 코드표). 목록에 없으면 문맥 단어로만 인정한다.
    private val KNOWN_CARRIERS = setOf(
        "KE", "OZ", "7C", "LJ", "TW", "BX", "ZE", "RS", "RF", "YP",
        "JL", "NH", "MM", "GK", "BC", "TG", "FD", "SL", "VN", "VJ", "QH",
        "SQ", "TR", "MH", "AK", "D7", "GA", "QZ", "PR", "5J", "Z2", "CX", "UO",
        "CI", "BR", "IT", "CA", "MU", "CZ", "HO", "EK", "QR", "EY", "TK",
        "LH", "AF", "KL", "BA", "AY", "UA", "AA", "DL", "AC", "HA", "QF", "NZ", "JQ",
    )

    private fun findFlightNumbers(lines: List<String>): List<String> {
        val found = linkedSetOf<String>()
        for (line in lines) {
            val hasWord = FLIGHT_WORDS.any { line.contains(it, ignoreCase = true) }
            for (m in FLIGHT.findAll(line.uppercase(Locale.ROOT))) {
                val carrier = m.groupValues[1]
                if (carrier.all { it.isDigit() }) continue
                if (carrier in KNOWN_CARRIERS || hasWord) found += carrier + m.groupValues[2]
            }
        }
        return found.toList()
    }

    // --- 날짜 ---

    private val CHECK_IN_WORDS = listOf("체크인", "입실", "check-in", "check in", "checkin")
    private val CHECK_OUT_WORDS = listOf("체크아웃", "퇴실", "check-out", "check out", "checkout")
    private val LODGING_WORDS = listOf("호텔", "숙소", "객실", "체크아웃", "hotel", "room", "check-out", "check out", "guest", "resort", "hostel")

    private val MONTHS = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
    )
    private val ISO = Regex("""\b(20\d{2})[-./](\d{1,2})[-./](\d{1,2})\b""")
    private val KOREAN = Regex("""(20\d{2})\s*년\s*(\d{1,2})\s*월\s*(\d{1,2})\s*일""")
    private val DAY_MON_YEAR = Regex("""\b(\d{1,2})\s*([A-Za-z]{3})[A-Za-z]*\.?,?\s*(20\d{2}|\d{2})\b""")
    private val MON_DAY_YEAR = Regex("""\b([A-Za-z]{3})[A-Za-z]*\.?\s+(\d{1,2}),?\s+(20\d{2})\b""")

    internal fun findDates(line: String): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        ISO.findAll(line).forEach { m -> date(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let(out::add) }
        KOREAN.findAll(line).forEach { m -> date(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let(out::add) }
        DAY_MON_YEAR.findAll(line).forEach { m ->
            val month = MONTHS[m.groupValues[2].uppercase(Locale.ROOT)] ?: return@forEach
            val year = m.groupValues[3].let { if (it.length == 2) "20$it" else it }
            date(year, month.toString(), m.groupValues[1])?.let(out::add)
        }
        MON_DAY_YEAR.findAll(line).forEach { m ->
            val month = MONTHS[m.groupValues[1].uppercase(Locale.ROOT)] ?: return@forEach
            date(m.groupValues[3], month.toString(), m.groupValues[2])?.let(out::add)
        }
        return out
    }

    private fun date(y: String, m: String, d: String): LocalDate? =
        runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()

    /** 라벨이 있는 줄(없으면 다음 줄)의 첫 날짜 */
    private fun dateNear(lines: List<String>, words: List<String>): LocalDate? {
        for ((i, line) in lines.withIndex()) {
            if (words.none { line.contains(it, ignoreCase = true) }) continue
            val d = findDates(line).firstOrNull() ?: lines.getOrNull(i + 1)?.let { findDates(it).firstOrNull() }
            if (d != null) return d
        }
        return null
    }
}
