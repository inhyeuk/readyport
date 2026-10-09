package com.readyport.text

import java.text.Normalizer
import java.util.Locale

/**
 * 검색용 정규화 (관광지 SPEC_v5 §7.2). 게시판 검색 로직은 바꾸지 않는다(이 파일은 관광지 검색만 쓴다).
 * 단계: (질의만) NFC → 글자 단위 소문자 → 전각 ASCII·반각 가나 접기 → 라틴 악센트·성조 제거 → 공백·기호 제거 → 된소리 접기.
 * **NFKC는 쓰지 않는다**(호환 자모가 조합형으로 바뀌어 초성 검색이 깨진다).
 * [Normalized.srcIndex]: 정규화 글자 i가 원문 몇 번째 글자에서 왔는지 — 굵게 범위를 원문 위에 그릴 때 쓴다.
 */
object KoreanNormalize {

    data class Normalized(val text: String, val srcIndex: IntArray) {
        override fun equals(other: Any?): Boolean = other is Normalized && other.text == text && other.srcIndex.contentEquals(srcIndex)
        override fun hashCode(): Int = text.hashCode() * 31 + srcIndex.contentHashCode()

        /** 정규화 범위 [start, end) → 원문 범위 [srcIndex[start], srcIndex[end-1]+1) */
        fun sourceRange(start: Int, end: Int): IntRange = srcIndex[start] until (srcIndex[end - 1] + 1)
    }

    private const val SYLLABLE_BASE = 0xAC00
    private const val SYLLABLE_LAST = 0xD7A3
    private const val JUNG_COUNT = 21
    private const val JONG_COUNT = 28

    /** 초성 순서(유니코드) → 호환 자모 */
    private val CHO = charArrayOf('ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ')

    /** 종성 순서 → 호환 자모 (0 = 없음) */
    private val JONG = charArrayOf(
        '\u0000', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ',
        'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
    )

    /** 겹받침 → (앞 받침 종성 번호, 뒤 자음) — '닭' → '달' + 'ㄱ' */
    private val DOUBLE_FINAL = mapOf(
        3 to (1 to 'ㅅ'), 5 to (4 to 'ㅈ'), 6 to (4 to 'ㅎ'), 9 to (8 to 'ㄱ'), 10 to (8 to 'ㅁ'), 11 to (8 to 'ㅂ'),
        12 to (8 to 'ㅅ'), 13 to (8 to 'ㅌ'), 14 to (8 to 'ㅍ'), 15 to (8 to 'ㅎ'), 18 to (17 to 'ㅅ'),
    )

    /** 된소리 접기: 초성 ㄲㄸㅃㅆㅉ → ㄱㄷㅂㅅㅈ */
    private val TENSE_CHO = mapOf(1 to 0, 4 to 3, 8 to 7, 10 to 9, 13 to 12)

    /** 된소리 접기: 종성 ㄲ → ㄱ, ㅆ → ㅅ */
    private val TENSE_JONG = mapOf(2 to 1, 20 to 19)

    private val TENSE_COMPAT = mapOf('ㄲ' to 'ㄱ', 'ㄸ' to 'ㄷ', 'ㅃ' to 'ㅂ', 'ㅆ' to 'ㅅ', 'ㅉ' to 'ㅈ')

    /** 지우는 기호(공백 외): 가운뎃점·슬래시·하이픈·밑줄과 일반 문장 부호 */
    private const val EXTRA_SYMBOLS = "·ㆍ・/-_"

    /** 반각 가나 U+FF66~FF9D → 전각 (탁점·반탁점 U+FF9E·FF9F는 결합 부호로) */
    private const val HALF_KANA = "ヲァィゥェォャュョッーアイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン"

    fun isSyllable(c: Char): Boolean = c.code in SYLLABLE_BASE..SYLLABLE_LAST

    fun isCompatConsonant(c: Char): Boolean = c in 'ㄱ'..'ㅎ'

    /** 음절의 초성(호환 자모, 된소리 접음). 음절이 아니면 null */
    fun initialOf(c: Char): Char? {
        if (!isSyllable(c)) return null
        val cho = (c.code - SYLLABLE_BASE) / (JUNG_COUNT * JONG_COUNT)
        return CHO[TENSE_CHO[cho] ?: cho]
    }

    /** 호환 자모의 된소리 접기 */
    fun foldConsonant(c: Char): Char = TENSE_COMPAT[c] ?: c

    /** 데이터 정규화: 빌드가 NFC를 보장하므로 NFC를 건너뛴다 */
    fun norm(s: String): Normalized = normalize(s)

    /** 질의 정규화: 먼저 NFC (위치 맵은 NFC 결과 기준) */
    fun normQuery(s: String): String = normalize(Normalizer.normalize(s, Normalizer.Form.NFC)).text

    /** 초성 문자열 — 정규화한 글자와 1:1(음절이면 초성, 아니면 그 글자) */
    fun chosung(normalized: String): String = buildString(normalized.length) {
        normalized.forEach { c -> append(initialOf(c) ?: c) }
    }

    /** 질의 낱말이 2자 이상 모두 자음인지(초성 검색) */
    fun isAllConsonants(word: String): Boolean = word.length >= 2 && word.all { isCompatConsonant(it) }

    /**
     * 마지막 음절의 받침을 다음 음절 초성으로 옮긴 꼴: '각' → ("가", 'ㄱ'), 겹받침은 뒤 자음 '닭' → ("달", 'ㄱ').
     * 받침이 없으면 null
     */
    fun splitLastFinal(word: String): Pair<String, Char>? {
        val last = word.lastOrNull() ?: return null
        if (!isSyllable(last)) return null
        val code = last.code - SYLLABLE_BASE
        val jong = code % JONG_COUNT
        if (jong == 0) return null
        val base = code - jong
        val (keep, moved) = DOUBLE_FINAL[jong] ?: (0 to JONG[jong])
        val kept = (SYLLABLE_BASE + base + keep).toChar()
        return word.dropLast(1) + kept to foldConsonant(moved)
    }

    private fun normalize(s: String): Normalized {
        val out = StringBuilder(s.length)
        val idx = ArrayList<Int>(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val width = Character.charCount(cp)
            for (piece in fold(cp)) {
                out.append(piece)
                idx += i
            }
            i += width
        }
        return Normalized(out.toString(), idx.toIntArray())
    }

    /** 한 코드포인트 → 정규화한 글자들(0개 = 지움) */
    private fun fold(cp: Int): CharArray {
        if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) return CharArray(0)
        val ch = String(Character.toChars(cp))
        if (ch.length == 1 && ch[0] in EXTRA_SYMBOLS) return CharArray(0)
        if (isPunctuation(cp)) return CharArray(0)
        // 전각 ASCII U+FF01~FF5E → ASCII
        var c = if (cp in 0xFF01..0xFF5E) cp - 0xFEE0 else cp
        // 반각 가나
        if (c in 0xFF66..0xFF9D) return charArrayOf(HALF_KANA[c - 0xFF66])
        if (c == 0xFF9E) return charArrayOf('゙')
        if (c == 0xFF9F) return charArrayOf('゚')
        // 글자 단위 소문자 — 결과가 한 글자가 아니면('İ' 등) 원문 그대로
        val lower = String(Character.toChars(c)).lowercase(Locale.ROOT)
        if (lower.codePointCount(0, lower.length) == 1) c = lower.codePointAt(0)
        // 라틴 글자만 악센트·성조 제거 (đ/Đ → d)
        if (Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN) {
            if (c == 'đ'.code || c == 'Đ'.code) return charArrayOf('d')
            val stripped = Normalizer.normalize(String(Character.toChars(c)), Normalizer.Form.NFD)
                .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            val recomposed = Normalizer.normalize(stripped, Normalizer.Form.NFC)
            val lowered = recomposed.lowercase(Locale.ROOT)
            return (if (lowered.length == recomposed.length) lowered else recomposed).toCharArray()
        }
        if (c in SYLLABLE_BASE..SYLLABLE_LAST) {
            val code = c - SYLLABLE_BASE
            val cho = code / (JUNG_COUNT * JONG_COUNT)
            val jung = (code / JONG_COUNT) % JUNG_COUNT
            val jong = code % JONG_COUNT
            val folded = SYLLABLE_BASE + ((TENSE_CHO[cho] ?: cho) * JUNG_COUNT + jung) * JONG_COUNT + (TENSE_JONG[jong] ?: jong)
            return charArrayOf(folded.toChar())
        }
        if (c in 'ㄱ'.code..'ㅎ'.code) return charArrayOf(foldConsonant(c.toChar()))
        return Character.toChars(c)
    }

    private fun isPunctuation(cp: Int): Boolean = when (Character.getType(cp)) {
        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(), Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(), Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        -> true
        else -> false
    }
}
