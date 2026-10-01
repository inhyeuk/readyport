package com.readyport.ui.home

import com.readyport.ui.components.SourceRef
import org.junit.Assert.assertEquals
import org.junit.Test

/** 홈 화면의 글 다듬기 도우미 (출처 줄 묶기). 한국어 줄바꿈·날짜 덩어리는 components/KoreanTextTest */
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

}
