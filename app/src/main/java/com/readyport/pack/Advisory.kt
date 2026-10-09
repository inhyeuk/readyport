package com.readyport.pack

import java.security.MessageDigest
import java.text.Normalizer

/**
 * 여행경보 문단 판정 (관광지 SPEC_v5 §5.6·§5.7). 낱말·정규식의 단일 출처는 packs/schema/advisory_rules.json —
 * 테스트(AdvisoryTest)가 이 상수와 파일을 대조하고, 경보 문단 해시는 Python(build_attractions.advisory_hash)과 공용 벡터로 맞춘다.
 */
object Advisory {
    /** 외교부 여행경보 중 '가지 말라'는 단계(3단계 출국권고·4단계 여행금지·특별여행주의보)를 말하는 낱말 */
    val HighAdvisoryWords: List<String> = listOf("3단계", "4단계", "출국권고", "여행금지", "특별여행주의보", "가지 마세요")

    /** 안전 섹션 문단 가운데 '경보 문단'(단계·경보를 말하는 문단)을 고르는 정규식 */
    const val PARAGRAPH_REGEX = "[1-4]단계|특별여행|여행경보는 없|내려진 여행경보|여행금지|출국권고|여행자제|여행유의"

    private val paragraphRegex = Regex(PARAGRAPH_REGEX)
    private val spaces = Regex("""\s+""")

    /** 문장 안의 단계 이름 그대로 찾는다(앱이 문장 뜻을 지어내지 않는다, D11). 1·2단계만 말하는 문장은 아니다 */
    fun isHighAdvisory(sentence: String): Boolean = HighAdvisoryWords.any { it in sentence }

    /** safety 섹션(첫 항목)의 경보 문단. safety 섹션이 없으면 null */
    fun paragraphs(pack: CountryPack): List<String>? =
        pack.sections.firstOrNull { it.id == "safety" }?.bodyKo?.filter { paragraphRegex.containsMatchIn(it) }

    /**
     * 경보 문단 해시: 문단마다 NFC → 연속 공백을 하나로 → 앞뒤 자르기, `\n`으로 이어 UTF-8 sha256(소문자 hex).
     * 생활 안내 문단(날치기·태풍 등)을 고치거나 날짜만 다시 확인해서는 바뀌지 않는다 — 관광지 화면에 거짓 경고가 뜨지 않게.
     */
    fun advisoryHash(pack: CountryPack): String? = paragraphs(pack)?.let { hashOf(it) }

    fun hashOf(paragraphs: List<String>): String {
        val joined = paragraphs.joinToString("\n") { normalizeSpaces(it) }
        val digest = MessageDigest.getInstance("SHA-256").digest(joined.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun normalizeSpaces(text: String): String =
        spaces.replace(Normalizer.normalize(text, Normalizer.Form.NFC), " ").trim()
}
