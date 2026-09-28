package com.readyport.doc.mrz

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 체크디지트로 검증하는 MRZ 칸 (ICAO 9303 Part 4, TD3 여권) */
enum class MrzCheck { DocumentNumber, BirthDate, ExpiryDate, PersonalNumber, Composite }

data class MrzData(
    val documentCode: String,
    val issuingState: String,
    val surname: String,
    val givenNames: String,
    val documentNumber: String,
    val nationality: String,
    val birthDate: LocalDate,
    val sex: Char,
    val expiryDate: LocalDate,
    val personalNumber: String,
    val checks: Map<MrzCheck, Boolean>,
) {
    val allChecksPass: Boolean get() = checks.values.all { it }

    // 여권번호 등 개인정보가 로그·크래시 리포트에 찍히지 않도록 가린다 (작업 규칙 3)
    override fun toString(): String = "MrzData(checks=$checks)"
}

/**
 * 여권(TD3, 44자 × 2줄) MRZ 판독기. 기기 OCR 결과 문자열에서 MRZ를 찾아 체크디지트를 검증한다.
 * 네트워크·저장 없음. 순수 Kotlin이라 JVM 단위 테스트로 검증한다.
 */
object MrzParser {
    private const val LINE = 44

    /** ICAO 9303 체크디지트: 가중치 7-3-1, '<'=0, 숫자=그대로, A=10 … Z=35 */
    fun checkDigit(field: String): Int {
        val weights = intArrayOf(7, 3, 1)
        var sum = 0
        field.forEachIndexed { i, c ->
            val v = when (c) {
                '<' -> 0
                in '0'..'9' -> c - '0'
                in 'A'..'Z' -> c - 'A' + 10
                else -> return -1
            }
            sum += v * weights[i % 3]
        }
        return sum % 10
    }

    /**
     * OCR 전체 글자에서 여권 MRZ를 찾는다.
     * 체크디지트를 모두 통과한 결과를 우선하고, 없으면 가장 많이 통과한 결과를 돌려준다(사용자 확인용).
     */
    fun findInText(ocrText: String, today: LocalDate = LocalDate.now()): MrzData? {
        val candidates = ocrText.lines()
            .map(::normalizeLine)
            .filter { it.length >= 30 && it.count { c -> c == '<' } >= 2 }
        val results = mutableListOf<MrzData>()
        for (i in candidates.indices) {
            val l1 = candidates[i]
            if (!l1.startsWith("P")) continue
            for (j in (i + 1)..minOf(i + 2, candidates.lastIndex)) {
                parse(l1, candidates[j], today)?.let(results::add)
            }
        }
        return results.firstOrNull { it.allChecksPass } ?: results.maxByOrNull { r -> r.checks.count { it.value } }
    }

    /** MRZ 두 줄을 판독한다. 형식이 맞지 않으면 null. */
    fun parse(line1Raw: String, line2Raw: String, today: LocalDate = LocalDate.now()): MrzData? {
        // 1줄(이름)은 뒤쪽이 전부 '<'라서 OCR이 많이 빠뜨린다. 30자 이상이면 채워서 쓴다
        val l1 = normalizeLine(line1Raw).let { if (it.length in 30 until LINE) it.padEnd(LINE, '<') else fit(it) } ?: return null
        val l2 = fit(normalizeLine(line2Raw)) ?: return null
        if (l1[0] != 'P') return null

        // 숫자만 오는 자리는 OCR이 자주 헷갈리는 글자(O→0 등)를 바로잡는다
        val fixed = StringBuilder(l2)
        val numericRanges = listOf(9..9, 13..19, 21..27, 42..43)
        for (range in numericRanges) for (k in range) fixed[k] = toDigit(fixed[k])
        val line2 = fixed.toString()

        val docNumberField = bestDocumentNumber(line2.substring(0, 9), line2[9])
        val docCheckOk = checkDigit(docNumberField) == digit(line2[9])
        val birth = line2.substring(13, 19)
        val expiry = line2.substring(21, 27)
        val personal = line2.substring(28, 42)
        val personalCd = line2[42]

        val composite = docNumberField + line2[9] + line2.substring(13, 20) + line2.substring(21, 43)
        val checks = mapOf(
            MrzCheck.DocumentNumber to docCheckOk,
            MrzCheck.BirthDate to (checkDigit(birth) == digit(line2[19])),
            MrzCheck.ExpiryDate to (checkDigit(expiry) == digit(line2[27])),
            MrzCheck.PersonalNumber to personalCheckOk(personal, personalCd),
            MrzCheck.Composite to (checkDigit(composite) == digit(line2[43])),
        )

        val birthDate = parseDate(birth, isExpiry = false, today) ?: return null
        val expiryDate = parseDate(expiry, isExpiry = true, today) ?: return null
        val (surname, given) = parseName(l1.substring(5))

        return MrzData(
            documentCode = l1.substring(0, 2).trimEnd('<'),
            issuingState = l1.substring(2, 5).trimEnd('<'),
            surname = surname,
            givenNames = given,
            documentNumber = docNumberField.trimEnd('<'),
            nationality = line2.substring(10, 13).trimEnd('<'),
            birthDate = birthDate,
            sex = line2[20].takeIf { it == 'M' || it == 'F' } ?: 'X',
            expiryDate = expiryDate,
            personalNumber = personal.trimEnd('<'),
            checks = checks,
        )
    }

    internal fun normalizeLine(raw: String): String = buildString {
        for (c in raw.uppercase()) {
            when (c) {
                ' ', '\t' -> Unit
                '«', '‹', '(', '[', '{' -> append('<') // OCR이 '<'를 비슷한 모양으로 읽는 경우
                in 'A'..'Z', in '0'..'9', '<' -> append(c)
                else -> Unit
            }
        }
    }

    /** OCR이 끝의 '<'를 빠뜨리면 44자로 채운다. 너무 짧거나 길면 버린다. */
    private fun fit(line: String): String? = when {
        line.length == LINE -> line
        line.length in 40 until LINE -> line.padEnd(LINE, '<')
        line.length in (LINE + 1)..(LINE + 2) && line.substring(LINE).all { it == '<' } -> line.substring(0, LINE)
        else -> null
    }

    private fun toDigit(c: Char): Char = when (c) {
        'O', 'Q', 'D' -> '0'
        'I', 'L' -> '1'
        'Z' -> '2'
        'S' -> '5'
        'G' -> '6'
        'B' -> '8'
        else -> c
    }

    private fun digit(c: Char): Int = if (c == '<') 0 else if (c in '0'..'9') c - '0' else -1

    /** 여권번호는 글자·숫자가 섞여 있어 O/0 같은 혼동을 체크디지트가 맞는 쪽으로 고른다(최대 16가지). */
    private fun bestDocumentNumber(field: String, cd: Char): String {
        val want = digit(cd)
        if (checkDigit(field) == want) return field
        val ambiguous = field.indices.filter { field[it] == 'O' || field[it] == '0' }.take(4)
        for (mask in 1 until (1 shl ambiguous.size)) {
            val chars = field.toCharArray()
            ambiguous.forEachIndexed { bit, idx ->
                if (mask and (1 shl bit) != 0) chars[idx] = if (chars[idx] == 'O') '0' else 'O'
            }
            val candidate = String(chars)
            if (checkDigit(candidate) == want) return candidate
        }
        return field
    }

    /** 개인번호 칸이 비어 있으면 체크디지트는 '<' 또는 '0'이다 (ICAO 9303) */
    private fun personalCheckOk(field: String, cd: Char): Boolean =
        if (field.all { it == '<' }) cd == '<' || cd == '0' else checkDigit(field) == digit(cd)

    /** YYMMDD → 날짜. 생년월일은 미래가 될 수 없고, 만료일은 2000년대로 본다. */
    private fun parseDate(s: String, isExpiry: Boolean, today: LocalDate): LocalDate? {
        if (!s.all { it in '0'..'9' }) return null
        val yy = s.substring(0, 2).toInt()
        val mmdd = s.substring(2)
        val century = if (isExpiry) {
            if (yy >= 70) 1900 else 2000
        } else {
            if (2000 + yy > today.year) 1900 else 2000
        }
        return runCatching { LocalDate.parse("${century + yy}$mmdd", DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
    }

    private fun parseName(field: String): Pair<String, String> {
        val parts = field.trimEnd('<').split("<<", limit = 2)
        val surname = parts[0].replace('<', ' ').trim()
        val given = parts.getOrNull(1)?.replace('<', ' ')?.trim()?.replace(Regex(" +"), " ").orEmpty()
        return surname to given
    }
}
