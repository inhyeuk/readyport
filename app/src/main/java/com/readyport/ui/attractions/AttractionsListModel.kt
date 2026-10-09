package com.readyport.ui.attractions

import com.readyport.attractions.AdvisoryState
import com.readyport.attractions.Attraction
import com.readyport.attractions.AttractionsCatalog
import com.readyport.attractions.Category
import com.readyport.attractions.HiddenReason
import com.readyport.attractions.LiftAnchor
import com.readyport.attractions.RegionGroup
import com.readyport.attractions.RegionGrouping
import com.readyport.attractions.SavedAttraction
import com.readyport.attractions.search.AttractionSearchIndex
import com.readyport.attractions.search.SearchReason
import com.readyport.attractions.search.SearchResult

/** 찜했지만 목록에 보이지 않는 항목의 상태 (§6.5 '팩에서 사라진 찜' — 조용히 지우지 않는다) */
sealed interface SavedGone {
    /** 그 나라 파일을 읽지 못함 */
    data object LoadFailed : SavedGone

    /** retired(문 닫음·오래 쉼·안전·편집) */
    data class Retired(val reason: String, val note: String?) : SavedGone

    /** 파일은 정상인데 키가 없음 */
    data object Missing : SavedGone

    /** 모르는 종류·지역(앱 업데이트 필요) */
    data object NeedsUpdate : SavedGone

    /** 여행경보로 숨김 */
    data object Safety : SavedGone
}

data class SavedExtra(val key: String, val name: String, val gone: SavedGone)

/** 목록 화면이 그릴 것 전부 (검색·걸러 보기·찜 공용). [AttractionsListModel.build]가 만든다 — 순수 계산이라 테스트한다 */
data class ListContent(
    val groups: List<RegionGroup>,
    val total: Int,
    val regionCount: Int,
    val searching: Boolean,
    val search: SearchResult?,
    val reasons: Map<String, SearchReason?>,
    /** 범위 밖 결과 — '모든 종류에서 n곳 더', '찜 말고도 n곳 더' */
    val widenCategory: Int,
    val widenSaved: Int,
    /** 곳이 있는 종류(§2.2 순서) — 걸러 보기 칩 */
    val categories: List<Category>,
    val savedExtras: List<SavedExtra>,
)

object AttractionsListModel {
    /** 보이는 종류가 이 수 이상이면 걸러 보기를 접힌 버튼 하나로 (§6.3, 결정적 규칙) */
    const val COLLAPSE_FILTER_AT = 4

    /** 지역이 이보다 많거나 결과가 [JUMP_PLACES]보다 많을 때만 '지역으로 건너뛰기' */
    const val JUMP_REGIONS = 6
    const val JUMP_PLACES = 25

    fun build(
        catalog: AttractionsCatalog,
        index: AttractionSearchIndex,
        query: String,
        category: Category?,
        savedOnly: Boolean,
        saved: List<SavedAttraction>,
        anchor: LiftAnchor?,
        advisory: AdvisoryState,
    ): ListContent {
        val savedKeys = saved.map { it.key }.toSet()
        val usable = catalog.attractions.filter { it.regionId !in advisory.hiddenRegions }
        fun inCategory(a: Attraction) = category == null || a.category == category
        fun inSaved(a: Attraction) = !savedOnly || a.key in savedKeys
        val searching = query.isNotBlank()
        val search = if (searching) index.search(query) else null
        val pool: List<Attraction>
        val scores: Map<String, Int>?
        if (search != null) {
            val usableIds = usable.map { it.id }.toSet()
            val hits = search.hits.filter { it.attraction.id in usableIds }
            pool = hits.map { it.attraction }
            scores = hits.associate { it.attraction.id to it.score }
        } else {
            pool = usable
            scores = null
        }
        val shown = pool.filter { inCategory(it) && inSaved(it) }
        val widenCategory = if (category != null) pool.count { !inCategory(it) && inSaved(it) } else 0
        // '찜 말고도 n곳 더'는 검색할 때만 — 찜 목록을 그냥 열었을 때는 찜이 0이면 빈 상태(하트 그림)를 보인다
        val widenSaved = if (savedOnly && searching) pool.count { inCategory(it) && !inSaved(it) } else 0
        val groups = RegionGrouping.group(catalog, shown, anchor, scores)
        val categories = Category.entries.filter { c -> usable.any { it.category == c } }
        val reasons = search?.hits?.associate { it.attraction.id to it.reason }.orEmpty()
        return ListContent(
            groups = groups,
            total = shown.size,
            regionCount = groups.size,
            searching = searching,
            search = search,
            reasons = reasons,
            widenCategory = widenCategory,
            widenSaved = widenSaved,
            categories = categories,
            savedExtras = if (savedOnly) savedExtras(catalog, saved, advisory) else emptyList(),
        )
    }

    /** 이 나라 찜 가운데 목록에 보이지 않는 것과 그 이유 */
    fun savedExtras(catalog: AttractionsCatalog?, saved: List<SavedAttraction>, advisory: AdvisoryState, country: String? = catalog?.country): List<SavedExtra> {
        val mine = saved.filter { it.country == country }
        if (catalog == null) return mine.map { SavedExtra(it.key, it.id, SavedGone.LoadFailed) }
        return mine.mapNotNull { s ->
            val visible = catalog.attraction(s.id)
            when {
                visible != null && visible.regionId in advisory.hiddenRegions -> SavedExtra(s.key, visible.nameKo, SavedGone.Safety)
                visible != null -> null
                s.id in catalog.retired -> catalog.retired.getValue(s.id).let { r -> SavedExtra(s.key, s.id, SavedGone.Retired(r.reason, r.noteKo)) }
                catalog.hidden[s.id] == HiddenReason.Safety -> SavedExtra(s.key, catalog.hiddenNames[s.id] ?: s.id, SavedGone.Safety)
                catalog.hidden[s.id] == HiddenReason.NeedsUpdate -> SavedExtra(s.key, catalog.hiddenNames[s.id] ?: s.id, SavedGone.NeedsUpdate)
                else -> SavedExtra(s.key, s.id, SavedGone.Missing)
            }
        }
    }

    /** 같은 지역의 다른 곳: 자기를 뺀 rank 상위 2곳 (§6.4, 경보 숨김·모르는 종류는 이미 빠져 있다) */
    fun sameRegion(catalog: AttractionsCatalog, a: Attraction, advisory: AdvisoryState): List<Attraction> =
        catalog.attractions.filter { it.regionId == a.regionId && it.id != a.id && it.regionId !in advisory.hiddenRegions }
            .sortedWith(compareBy<Attraction> { it.rankOrder }.thenBy { it.nameKo })
            .take(2)

    /** 여행 정보 갈래의 종류 타일: 2곳 이상인 종류 가운데 곳 수가 많은 순으로 최대 5개, 놓는 순서는 §2.2 순서 */
    fun entryCategories(catalog: AttractionsCatalog, max: Int = 5): List<Pair<Category, Int>> {
        val counts = catalog.categoryCounts().filter { it.value >= 2 }
        val top = counts.entries.sortedWith(compareByDescending<Map.Entry<Category, Int>> { it.value }.thenBy { it.key.ordinal })
            .take(max).map { it.key }.toSet()
        return Category.entries.filter { it in top }.map { it to counts.getValue(it) }
    }
}
