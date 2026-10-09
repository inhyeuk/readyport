package com.readyport.ui.attractions

import com.readyport.attractions.AdvisoryState
import com.readyport.attractions.AttTestData
import com.readyport.attractions.AttTestData.place
import com.readyport.attractions.AttTestData.region
import com.readyport.attractions.Category
import com.readyport.attractions.RetiredDto
import com.readyport.attractions.SavedAttraction
import com.readyport.attractions.search.AttractionSearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 목록 계산: 범위(종류·찜) AND, 넓혀 찾기 개수, 걸러 보기 접기 규칙, 사라진 찜, 종류 타일 (SPEC_v5 §6.3·§6.5·§6.7) */
class AttractionsListModelTest {
    private val catalog = AttTestData.catalog(AttTestData.basic())
    private val index = AttractionSearchIndex(catalog)

    private fun build(query: String = "", category: Category? = null, savedOnly: Boolean = false, saved: List<String> = emptyList()) =
        AttractionsListModel.build(catalog, index, query, category, savedOnly, saved.map { SavedAttraction("XX/$it", "2026-10-09") }, null, AdvisoryState.Normal)

    @Test fun allPlacesGroupedByRegion() {
        val c = build()
        assertEquals(5, c.total)
        assertEquals(3, c.regionCount)
        assertFalse(c.searching)
    }

    @Test fun categoryAndSavedAreAnd() {
        val c = build(category = Category.Heritage, savedOnly = true, saved = listOf("fake-temple", "fake-hill"))
        assertEquals(listOf("fake-temple"), c.groups.flatMap { g -> g.places.map { it.id } })
        assertEquals(1, c.widenCategory) // 찜한 다른 종류 1곳(fake-hill)
        assertEquals(0, c.widenSaved)
    }

    @Test fun widenCountsForSearch() {
        // '가짜' — 이름에 '가짜'가 든 곳: 가짜 야시장. 설명 글에는 모두 '가짜' → 모든 곳이 결과
        val c = build(query = "가짜", category = Category.MarketStreet)
        assertEquals(1, c.total)
        assertEquals(4, c.widenCategory)
        val saved = build(query = "가짜", savedOnly = true, saved = listOf("fake-market"))
        assertEquals(1, saved.total)
        assertEquals(4, saved.widenSaved)
    }

    @Test fun filterCollapseRule() {
        assertTrue(build().categories.size >= AttractionsListModel.COLLAPSE_FILTER_AT)
        val three = AttTestData.catalog(
            AttTestData.doc(
                listOf(region("xx_a", 1)),
                listOf(place("a", "xx_a", "가"), place("b", "xx_a", "나", category = "nature"), place("c", "xx_a", "다", category = "museum")),
            ),
        )
        val c = AttractionsListModel.build(three, AttractionSearchIndex(three), "", null, false, emptyList(), null, AdvisoryState.Normal)
        assertEquals(3, c.categories.size)
        assertTrue(c.categories.size < AttractionsListModel.COLLAPSE_FILTER_AT)
    }

    @Test fun savedExtrasNeverSilentlyDropped() {
        val doc = AttTestData.basic().let { d ->
            d.copy(
                attractions = d.attractions + place("danger", "xx_one", "위험한 곳", level = "3") + place("newcat", "xx_one", "새 종류", category = "space"),
                retired = listOf(RetiredDto("closed-one", "closed", null, "문을 닫았어요")),
            )
        }
        val cat = AttTestData.catalog(doc)
        val saved = listOf("fake-temple", "danger", "newcat", "closed-one", "vanished").map { SavedAttraction("XX/$it", "2026-10-09") } +
            SavedAttraction("YY/other", "2026-10-09")
        val extras = AttractionsListModel.savedExtras(cat, saved, AdvisoryState.Normal).associate { it.key to it.gone }
        assertEquals(SavedGone.Safety, extras["XX/danger"])
        assertEquals(SavedGone.NeedsUpdate, extras["XX/newcat"])
        assertEquals(SavedGone.Retired("closed", "문을 닫았어요"), extras["XX/closed-one"])
        assertEquals(SavedGone.Missing, extras["XX/vanished"])
        assertFalse("XX/fake-temple" in extras)
        assertFalse("YY/other" in extras)
        // 파일을 읽지 못하면 모두 '불러오지 못했어요'
        assertTrue(AttractionsListModel.savedExtras(null, saved, AdvisoryState.Normal, "XX").all { it.gone == SavedGone.LoadFailed })
    }

    @Test fun watchHiddenRegionLeavesListButStaysInSaved() {
        val adv = AdvisoryState(changed = true, packVerified = "2026-10-05", hiddenRegions = setOf("xx_one"))
        val c = AttractionsListModel.build(catalog, index, "", null, true, listOf(SavedAttraction("XX/fake-temple", "2026-10-09")), null, adv)
        assertEquals(0, c.total)
        assertEquals(SavedGone.Safety, c.savedExtras.single().gone)
    }

    @Test fun sameRegionTopTwo() {
        val a = catalog.attraction("fake-temple")!!
        assertEquals(listOf("fake-market"), AttractionsListModel.sameRegion(catalog, a, AdvisoryState.Normal).map { it.id })
    }

    @Test fun entryCategoryTiles() {
        val regions = listOf(region("xx_a", 1))
        val places = listOf(
            "heritage" to 4, "nature" to 3, "sea_island" to 2, "city_view" to 2, "market_street" to 2, "museum" to 3, "theme_park" to 1,
        ).flatMap { (c, n) -> (1..n).map { i -> place("$c-$i", "xx_a", "$c $i", category = c) } }
        val cat = AttTestData.catalog(AttTestData.doc(regions, places))
        val tiles = AttractionsListModel.entryCategories(cat)
        // 2곳 이상인 종류 가운데 많은 순 5개(동점은 §2.2 순서), 놓는 순서는 §2.2 순서
        assertEquals(
            listOf(Category.Heritage, Category.Nature, Category.SeaIsland, Category.CityView, Category.Museum),
            tiles.map { it.first },
        )
        assertTrue(tiles.none { it.first == Category.ThemePark })
    }
}
