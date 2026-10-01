package com.readyport.ui.home

import com.readyport.ui.components.SourceRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 홈·첫 실행 화면의 글 다듬기 도우미 (출처 줄 묶기, 날짜 덩어리, 어절 단위 줄바꿈) */
class HomeFormatTest {

    @Test
    fun sameHeadSameDateSourcesShareOneLine() {
        val refs = listOf("태국", "일본", "싱가포르").map { SourceRef("외교부 해외안전여행 · $it", "2026.09.28") }
        assertEquals(
            listOf(SourceRef("외교부 해외안전여행 · 태국, 일본, 싱가포르", "2026.09.28")),
            compactSourceRefs(refs),
        )
    }

    @Test
    fun differentHeadsOrDatesStayAsTheyAre() {
        val mixed = listOf(SourceRef("외교부 해외안전여행 · 태국", "2026.09.28"), SourceRef("관세청 여행자 휴대품 면세 범위", "2026.09.28"))
        assertEquals(mixed, compactSourceRefs(mixed))
        val dates = listOf(SourceRef("외교부 해외안전여행 · 태국", "2026.09.28"), SourceRef("외교부 해외안전여행 · 일본", "2026.09.30"))
        assertEquals(dates, compactSourceRefs(dates))
        val one = listOf(SourceRef("외교부 해외안전여행 · 태국", "2026.09.28"))
        assertEquals(one, compactSourceRefs(one))
    }

    @Test
    fun duplicateNamesAreNotRepeated() {
        val refs = listOf(SourceRef("외교부 해외안전여행 · 태국", "2026.09.28"), SourceRef("외교부 해외안전여행 · 태국", "2026.09.28"))
        assertEquals(refs.take(1), compactSourceRefs(refs))
    }

    @Test
    fun keepWordsJoinsSyllablesOnlyInsideWordsBeforeApi33() {
        val wj = "\u2060"
        val s = keepWords("네, 처음이에요", sdk = 31)
        assertEquals("네, 처음이에요", s.replace(wj, ""))
        assertTrue("처${wj}음${wj}이${wj}에${wj}요" in s)
        // 띄어쓰기 앞뒤에는 넣지 않는다 → 줄은 띄어쓰기에서만 바뀐다
        assertFalse(" $wj" in s || "$wj " in s)
        // 줄바꿈 문자 앞뒤에도 넣지 않는다
        assertEquals("가${wj}나\n다", keepWords("가나\n다", sdk = 31))
    }

    @Test
    fun keepWordsLeavesTextAsIsFromApi33() {
        assertEquals("네, 처음이에요", keepWords("네, 처음이에요", sdk = 33))
        assertEquals("출국하는 날, 이 순서대로", keepWords("출국하는 날, 이 순서대로", sdk = 36))
    }

    @Test
    fun dateChunkHasNoBreakOpportunity() {
        val s = unbreakable("11월 3일 (화)")
        assertFalse(" " in s)
        assertEquals("11월 3일 (화)", s.replace("\u2060", "").replace('\u00A0', ' '))
    }
}
