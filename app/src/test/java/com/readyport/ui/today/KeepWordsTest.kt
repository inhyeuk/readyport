package com.readyport.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** API 33 미만 낱말 보호(joinKoreanWords): 보이는 글자는 그대로, 한글 낱말 안에만 WORD JOINER (DESIGN_SPEC 3.2) */
class KeepWordsTest {

    private val wj = "⁠"

    /** 보이지 않는 글자를 걷어 내면 원문과 같아야 한다 (관형사 뒤 NBSP는 공백으로 돌려 비교) */
    private fun visible(s: String) = s.replace(wj, "").replace(' ', ' ')

    @Test
    fun joinsSyllablesInsideWordsOnly() {
        assertEquals("숙${wj}소${wj}로 돌${wj}아${wj}가${wj}기", joinKoreanWords("숙소로 돌아가기"))
        // 숫자·괄호·영문이 한글에 붙어 있으면 한 낱말
        assertEquals("(6${wj}단${wj}계 중 1${wj}단${wj}계${wj})", joinKoreanWords("(6단계 중 1단계)"))
        assertEquals("QR${wj}을", joinKoreanWords("QR을"))
    }

    @Test
    fun leavesNonKoreanTextAlone() {
        listOf("2026.09.29", "TDAC", "IDR 500,000", "กรุณาเรียกตำรวจ", "").forEach { assertEquals(it, joinKoreanWords(it)) }
        // 영문끼리는 원래 끊기지 않으므로 WORD JOINER를 넣지 않는다
        assertFalse(joinKoreanWords("태국 입국 카드 (TDAC)").contains("T${wj}D"))
    }

    @Test
    fun middleDotAllowsBreakAfterIt() {
        // `유심·` 뒤에서는 끊어도 된다
        assertEquals("유${wj}심${wj}·인${wj}터${wj}넷 준${wj}비", joinKoreanWords("유심·인터넷 준비"))
    }

    @Test
    fun determinerStaysWithNextWord() {
        assertEquals("이 순${wj}서${wj}대${wj}로", joinKoreanWords("이 순서대로"))
        assertEquals("새 여${wj}행", joinKoreanWords("새 여행"))
        // 낱말 끝의 '이'(조사)는 관형사가 아니다
        assertTrue(joinKoreanWords("인터넷이 없어도").contains("이 없"))
    }

    @Test
    fun boundNounStaysWithPreviousWord() {
        assertEquals("낼 수 있${wj}어${wj}요", joinKoreanWords("낼 수 있어요"))
        // '수'로 시작하는 보통 낱말(수입)은 그대로
        assertTrue(joinKoreanWords("밝힌 수입 금지").contains("힌 수"))
    }

    @Test
    fun visibleTextIsUnchanged() {
        listOf(
            "도착했어요! 이 순서대로 해요",
            "현지어 문장 카드와 긴급 연락처는 인터넷이 없어도 볼 수 있어요.",
            "내는 때: 태국에 도착하는 날을 포함해 3일 안에 내요. 예: 5월 4일 도착이면 5월 2일~4일",
            "외교부 해외안전여행 · 태국",
        ).forEach { assertEquals(it, visible(joinKoreanWords(it))) }
    }
}
