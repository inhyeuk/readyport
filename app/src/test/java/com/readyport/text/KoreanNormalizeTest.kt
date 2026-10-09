package com.readyport.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 검색 정규화 (SPEC_v5 §7.2·§12) */
class KoreanNormalizeTest {
    private fun n(s: String) = KoreanNormalize.norm(s).text

    @Test fun spacesAndSymbolsRemoved() {
        assertEquals("샘플사원", n("샘플 사원"))
        assertEquals("가나시티", n("가나·시티"))
        assertEquals("가나시티", n("가나/시-티_"))
        assertEquals("abc", n("A b, C!"))
    }

    @Test fun latinAccentsAndToneMarks() {
        val r = KoreanNormalize.norm("Đà Nẵng")
        assertEquals("danang", r.text)
        assertEquals(r.text.length, r.srcIndex.size)
        // 'ẵ'(원문 4번째 글자)는 정규화 3번째 글자
        assertEquals(4, r.srcIndex[3])
        assertEquals("sensoji", n("Sensō-ji"))
    }

    @Test fun tenseConsonantsFold() {
        assertEquals(n("바다니"), n("빠따니"))
        assertEquals(n("가"), n("까"))
        assertEquals(n("갓"), n("갔"))
        assertEquals('ㄱ', KoreanNormalize.foldConsonant('ㄲ'))
    }

    @Test fun kanaAndFullWidth() {
        assertEquals("が", n("が"))
        assertEquals("mbs", n("ＭＢＳ"))
        assertEquals("ア", n("ｱ"))
    }

    @Test fun compatibilityJamoKept() {
        // NFKC를 쓰면 호환 자모가 조합형으로 바뀌어 초성 검색이 깨진다
        assertEquals("ㅎㅈㅅ", KoreanNormalize.normQuery("ㅎㅈㅅ"))
        assertTrue(KoreanNormalize.isAllConsonants("ㅎㅈㅅ"))
        assertFalse(KoreanNormalize.isAllConsonants("ㅎ"))
    }

    @Test fun dottedCapitalIStaysOneToOne() {
        val r = KoreanNormalize.norm("İstanbul")
        assertEquals(r.text.length, r.srcIndex.size)
        assertTrue(r.text.endsWith("stanbul"))
    }

    @Test fun queryIsNfc() {
        val decomposed = java.text.Normalizer.normalize("샘플", java.text.Normalizer.Form.NFD)
        assertEquals("샘플", KoreanNormalize.normQuery(decomposed))
    }

    @Test fun sourceRangeForBold() {
        val r = KoreanNormalize.norm("왓 아룬 사원")
        // '아룬' = 정규화 [1, 3) → 원문 [2, 4)
        assertEquals(2 until 4, r.sourceRange(1, 3))
        // '왓아' = 정규화 [0, 2) → 원문 [0, 3) (사이 공백 포함)
        assertEquals(0 until 3, r.sourceRange(0, 2))
    }

    @Test fun chosungAndFinals() {
        assertEquals("ㅅㅍㅅㅇ", KoreanNormalize.chosung(n("샘플 사원")))
        assertEquals("다" to 'ㄹ', KoreanNormalize.splitLastFinal("달"))
        assertEquals("달" to 'ㄱ', KoreanNormalize.splitLastFinal("닭"))
        assertNull(KoreanNormalize.splitLastFinal("다"))
        assertEquals('ㄱ', KoreanNormalize.initialOf('까'))
    }
}
