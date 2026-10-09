package com.readyport.attractions.search

import com.readyport.attractions.AttTestData
import com.readyport.attractions.AttTestData.place
import com.readyport.attractions.AttTestData.region
import com.readyport.attractions.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 관광지 검색 (SPEC_v5 §7·§12) — 가짜 이름 */
class AttractionSearchIndexTest {
    private val catalog = AttTestData.catalog(AttTestData.basic())
    private val index = AttractionSearchIndex(catalog)

    private fun ids(q: String) = index.search(q).hits.map { it.attraction.id }

    @Test fun nameWithoutSpaceIsExact() {
        val hit = index.search("샘플사원").hits.first()
        assertEquals("fake-temple", hit.attraction.id)
        assertEquals(100, hit.score)
        assertNull(hit.reason) // 이름 그대로면 이유 줄 없음
    }

    @Test fun prefixAndContains() {
        assertEquals(80, index.search("샘플").hits.first { it.attraction.id == "fake-temple" }.score)
        assertEquals(60, index.search("사원").hits.first { it.attraction.id == "fake-temple" }.score)
    }

    @Test fun aliasAndEnglishReasons() {
        val alias = index.search("새벽절").hits.first()
        assertEquals("fake-temple", alias.attraction.id)
        assertEquals(ReasonKind.Alias, alias.reason!!.kind)
        assertEquals("새벽 절", alias.reason!!.text)
        assertEquals(0..3, alias.reason!!.bold)
        val en = index.search("sample temple").hits.first()
        assertEquals(ReasonKind.English, en.reason!!.kind)
    }

    @Test fun regionAliasFindsRegionPlaces() {
        val r = index.search("가나시티")
        assertEquals(setOf("fake-temple", "fake-market"), r.hits.map { it.attraction.id }.toSet())
        assertTrue(r.hits.all { it.reason?.kind == ReasonKind.Region })
    }

    @Test fun oneAliasManyRegions() {
        val doc = AttTestData.doc(
            regions = listOf(region("xx_a", 1, name = "북가짜", aliases = listOf("가짜도")), region("xx_b", 2, name = "남가짜", aliases = listOf("가짜도"))),
            attractions = listOf(place("p-a", "xx_a", "가짜 하나"), place("p-b", "xx_b", "가짜 둘")),
        )
        val idx = AttractionSearchIndex(AttTestData.catalog(doc))
        assertEquals(setOf("p-a", "p-b"), idx.search("가짜도").hits.map { it.attraction.id }.toSet())
    }

    @Test fun stationAndMentions() {
        assertEquals(ReasonKind.Station, index.search("가나중앙역").hits.first().reason!!.kind)
        val m = index.search("무명봉").hits.first()
        assertEquals("fake-hill", m.attraction.id)
        assertEquals(ReasonKind.Mention, m.reason!!.kind)
        assertEquals(10, m.score)
    }

    @Test fun categorySynonymsAndTags() {
        assertTrue("fake-park" in ids("액티비티"))
        assertTrue("fake-park" in ids("온천"))
        assertTrue("fake-temple" in ids("사적지"))
        assertEquals(Category.Nature, index.search("산").categoryHint)
        assertEquals(Category.ThemePark, index.search("놀이공원").categoryHint)
    }

    @Test fun oneCharOnlyTitleAndCategory() {
        // '절' — 종류 동의어(heritage)에는 걸리지만 설명 글 속 글자로는 걸리지 않는다
        val r = index.search("절")
        assertEquals(listOf("fake-temple"), r.hits.map { it.attraction.id })
        assertTrue(ids("문").isEmpty()) // 설명 '문장'에만 있는 글자
    }

    @Test fun chosungSearch() {
        val r = index.search("ㅅㅍㅅㅇ")
        assertTrue(r.chosung)
        assertEquals("fake-temple", r.hits.first().attraction.id)
        assertEquals(100, r.hits.first().score)
    }

    @Test fun trailingJamoAndFinalMove() {
        assertEquals("fake-temple", index.search("샘플 사ㅇ").hits.first().attraction.id)
        // '달' → '다' + ㄹ초성 → '다라원'
        assertTrue("fake-hill" in ids("달"))
        // 겹받침 '닭' → '달' + ㄱ초성 → '달기 공원'
        assertTrue("fake-park" in ids("닭"))
    }

    @Test fun regionPlusCategoryScoresBelowName() {
        val r = index.search("가나시 사원")
        val combo = r.hits.first { it.attraction.id == "fake-temple" }
        assertTrue(combo.score < 100)
        assertTrue(index.search("샘플 사원").hits.first().score > combo.score)
    }

    @Test fun excludedTopicUpcomingAndExcludedArea() {
        val topic = index.search("스노클링")
        assertTrue(topic.hits.isEmpty())
        assertEquals("스노클링" to Category.SeaIsland, topic.excludedTopic)
        assertEquals("나중시", index.search("나중시").upcoming)
        assertEquals("나중시", index.search("나중 시티").upcoming)
        assertEquals("위험섬", index.search("위험섬").excludedArea!!.nameKo)
        assertNull(index.search("샘플").upcoming)
    }

    @Test fun noMatch() {
        val r = index.search("전혀없는말")
        assertTrue(r.hits.isEmpty())
        assertNull(r.excludedTopic)
        assertFalse(r.chosung)
    }

    @Test fun debugSampleSearch() {
        val idx = AttractionSearchIndex(AttTestData.catalog(AttTestData.debugSample()))
        assertEquals("fushimi-inari-taisha", idx.search("이나리").hits.first().attraction.id)
        assertEquals("dotonbori", idx.search("ㄷㅌㅂㄹ").hits.first().attraction.id)
        assertNotNull(idx.search("후쿠오카").upcoming)
    }
}
