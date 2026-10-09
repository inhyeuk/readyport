package com.readyport.ui.attractions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.attractions.AdvisoryState
import com.readyport.attractions.Attraction
import com.readyport.attractions.AttractionsCatalog
import com.readyport.attractions.AttractionsOrigin
import com.readyport.attractions.AttractionsRepository
import com.readyport.attractions.Category
import com.readyport.attractions.LiftAnchor
import com.readyport.attractions.Region
import com.readyport.attractions.RegionGrouping
import com.readyport.attractions.SavedAttraction
import com.readyport.attractions.SavedAttractionsRepository
import com.readyport.attractions.TripContext
import com.readyport.attractions.search.AttractionSearchIndex
import com.readyport.pack.CountryPack
import com.readyport.pack.PackRepository
import com.readyport.stay.Stays
import com.readyport.trip.TripRepository
import com.readyport.trip.TripSelection
import com.readyport.trip.TripTiming
import com.readyport.ui.nav.AttractionDetailRoute
import com.readyport.ui.nav.AttractionsRoute
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 나라 하나의 관광지 바탕(파일·검색 색인·경보 판정·끌어올리기) — 목록·상세가 같이 쓴다 */
data class AttractionsBase(
    val catalog: AttractionsCatalog?,
    val index: AttractionSearchIndex?,
    val countryName: String,
    val pack: CountryPack?,
    val advisory: AdvisoryState,
    val anchor: LiftAnchor?,
    /** 도착 공항 근처 지역이 준비 중이면 그 공항 이름 */
    val upcomingAirportName: String?,
    /** 이번에 합쳐서 옮긴 찜(한 번 안내) */
    val merged: Set<String>,
)

/** 관광지 화면이 공통으로 읽는 것 (파일 로드 → 합쳐진 찜 옮기기 → 경보·끌어올리기 판정) */
internal fun attractionsBase(
    country: String,
    repo: AttractionsRepository,
    saved: SavedAttractionsRepository,
    packs: PackRepository,
    trips: TripRepository,
    wallet: WalletRepository,
): Flow<AttractionsBase> = combine(repo.revisionOf(country), packs.revision, trips.book, wallet.state) { _, _, book, walletState ->
    val loaded = repo.load(country)
    val catalog = repo.catalog(country)
    // 합쳐진 항목의 찜 옮기기: 서명본을 성공적으로 읽었을 때만(샘플·로드 실패는 아님), 버전이 오를 때 한 번 (§6.5)
    if (catalog != null && loaded != null && loaded.origin != AttractionsOrigin.Sample) {
        saved.migrate(country, catalog.version, catalog.retired.values)
    }
    val merged = saved.mergedNotice.first()
    val pack = packs.pack(country)?.value
    val today = LocalDate.now()
    val trip = book.trips.firstOrNull { t ->
        t.country == country && t.datesValid && TripSelection.timing(t, today) != TripTiming.Past
    }?.let { t ->
        val ongoing = TripSelection.timing(t, today) == TripTiming.Ongoing
        // 오늘 묵는 곳 좌표: 내 정보가 열려 있을 때만(화면 상태에만 쓰고 저장·전송하지 않는다)
        val stay = (walletState as? WalletState.Unlocked)?.contents?.stays
            ?.let { Stays.on(Stays.forTrip(it, t), today) }
        TripContext(inProgress = ongoing, stayLat = stay?.lat, stayLng = stay?.lng, arrivalAirport = t.arrivalAirport)
    }
    val anchor = catalog?.let { RegionGrouping.liftAnchor(it, trip) }
    val upcomingAirport = catalog?.let { RegionGrouping.upcomingArrivalAirport(it, trip) }
    AttractionsBase(
        catalog = catalog,
        index = catalog?.let { AttractionSearchIndex(it) },
        countryName = pack?.names?.ko ?: country,
        pack = pack,
        advisory = catalog?.let { AdvisoryState.evaluate(it, pack) } ?: AdvisoryState.Normal,
        anchor = anchor,
        upcomingAirportName = upcomingAirport?.let { code -> pack?.airport(code)?.nameKo ?: code },
        merged = merged,
    )
}

data class AttractionsListUi(
    val loading: Boolean = true,
    val country: String = "",
    val countryName: String = "",
    val catalog: AttractionsCatalog? = null,
    val content: ListContent? = null,
    /** 입력 칸에 보이는 글자(바로 반영). 결과는 250ms 멈춘 뒤의 글자로 계산한다 */
    val query: String = "",
    val category: Category? = null,
    val savedOnly: Boolean = false,
    val savedKeys: Set<String> = emptySet(),
    val anchor: LiftAnchor? = null,
    val upcomingAirportName: String? = null,
    val advisory: AdvisoryState = AdvisoryState.Normal,
    /** 찜 화면 첫 진입 안내 (한 번만) */
    val showFirstNotice: Boolean = false,
    val merged: Set<String> = emptySet(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class AttractionsListViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    repo: AttractionsRepository,
    private val saved: SavedAttractionsRepository,
    packs: PackRepository,
    trips: TripRepository,
    wallet: WalletRepository,
) : ViewModel() {
    private val route = handle.toRoute<AttractionsRoute>()
    val country = route.country

    private val query = handle.getStateFlow(KEY_QUERY, route.query.orEmpty())
    private val category = handle.getStateFlow(KEY_CATEGORY, route.category.orEmpty())
    private val savedOnly = handle.getStateFlow(KEY_SAVED_ONLY, route.savedOnly)

    private val base = attractionsBase(country, repo, saved, packs, trips, wallet)

    private data class Filters(val query: String, val category: String, val savedOnly: Boolean)

    private val filters = combine(query.debounce(SEARCH_DEBOUNCE_MS), category, savedOnly) { q, c, s -> Filters(q, c, s) }

    val ui: StateFlow<AttractionsListUi> = combine(base, saved.saved, saved.firstNoticeDone, filters, query) { b, items, noticeDone, f, typed ->
        val catalog = b.catalog
        val cat = Category.of(f.category)
        val content = if (catalog != null && b.index != null) {
            AttractionsListModel.build(catalog, b.index, f.query, cat, f.savedOnly, items, b.anchor, b.advisory)
        } else {
            null
        }
        AttractionsListUi(
            loading = false,
            country = country,
            countryName = b.countryName,
            catalog = catalog,
            content = content,
            query = typed,
            category = cat,
            savedOnly = f.savedOnly,
            savedKeys = items.map { it.key }.toSet(),
            anchor = b.anchor,
            upcomingAirportName = b.upcomingAirportName,
            advisory = b.advisory,
            showFirstNotice = f.savedOnly && !noticeDone && items.any { it.country == country },
            merged = b.merged,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttractionsListUi(country = country, query = route.query.orEmpty()))

    fun setQuery(text: String) {
        handle[KEY_QUERY] = text
    }

    fun setCategory(category: Category?) {
        handle[KEY_CATEGORY] = category?.key.orEmpty()
    }

    fun setSavedOnly(on: Boolean) {
        handle[KEY_SAVED_ONLY] = on
    }

    fun setSaved(key: String, on: Boolean) = viewModelScope.launch {
        saved.setSaved(key, on, LocalDate.now().toString())
    }

    fun dismissFirstNotice() = viewModelScope.launch {
        saved.markFirstNoticeDone()
        saved.clearMergedNotice()
    }

    /** focusSearch는 한 번만 — 상세에서 돌아와도 키보드가 다시 뜨지 않게 (§6.2 소비 플래그) */
    fun consumeFocusSearch(): Boolean {
        if (!route.focusSearch || handle.get<Boolean>(CONSUMED_FOCUS) == true) return false
        handle[CONSUMED_FOCUS] = true
        return true
    }

    /** scrollToRegion도 한 번만 */
    fun consumeScrollToRegion(): String? {
        val target = route.scrollToRegion ?: return null
        if (handle.get<Boolean>(CONSUMED_SCROLL) == true) return null
        handle[CONSUMED_SCROLL] = true
        return target
    }

    private companion object {
        const val KEY_QUERY = "att_query"
        const val KEY_CATEGORY = "att_category"
        const val KEY_SAVED_ONLY = "att_saved_only"
        const val CONSUMED_FOCUS = "consumed_focusSearch"
        const val CONSUMED_SCROLL = "consumed_scrollToRegion"
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}

data class AttractionDetailUi(
    val loading: Boolean = true,
    val country: String = "",
    val countryName: String = "",
    val catalog: AttractionsCatalog? = null,
    val attraction: Attraction? = null,
    val region: Region? = null,
    val saved: Boolean = false,
    val sameRegion: List<Attraction> = emptyList(),
    val advisory: AdvisoryState = AdvisoryState.Normal,
    /** 이 화면에서 처음 찜한 직후 — 찜 버튼 바로 아래 안내 */
    val showFirstNotice: Boolean = false,
    /** 출처 id → 이름 (팩 안전 출처 포함) */
    val sourceNames: Map<String, String> = emptyMap(),
)

@HiltViewModel
class AttractionDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    repo: AttractionsRepository,
    private val saved: SavedAttractionsRepository,
    packs: PackRepository,
    trips: TripRepository,
    wallet: WalletRepository,
) : ViewModel() {
    private val route = handle.toRoute<AttractionDetailRoute>()
    private val justSavedFirst = MutableStateFlow(false)

    val ui: StateFlow<AttractionDetailUi> = combine(
        attractionsBase(route.country, repo, saved, packs, trips, wallet),
        saved.saved,
        justSavedFirst,
    ) { b, items, first ->
        val catalog = b.catalog
        val a = catalog?.attraction(route.id)
        AttractionDetailUi(
            loading = false,
            country = route.country,
            countryName = b.countryName,
            catalog = catalog,
            attraction = a?.takeIf { it.regionId !in b.advisory.hiddenRegions },
            region = a?.let { catalog.region(it.regionId) },
            saved = a != null && items.any { it.key == a.key },
            sameRegion = if (catalog != null && a != null) AttractionsListModel.sameRegion(catalog, a, b.advisory) else emptyList(),
            advisory = b.advisory,
            showFirstNotice = first,
            sourceNames = catalog?.sources.orEmpty().mapValues { it.value.name } +
                b.pack?.sources.orEmpty().associate { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttractionDetailUi(country = route.country))

    fun setSaved(on: Boolean) = viewModelScope.launch {
        val a = ui.value.attraction ?: return@launch
        val firstEver = on && !saved.firstNoticeDone.first() && saved.current().isEmpty()
        saved.setSaved(a.key, on, LocalDate.now().toString())
        if (firstEver) justSavedFirst.value = true
    }

    fun dismissFirstNotice() = viewModelScope.launch {
        saved.markFirstNoticeDone()
        justSavedFirst.value = false
    }
}

/** 찜 목록 한 줄 (찜 화면·나라 화면 줄의 수 세기) */
internal fun List<SavedAttraction>.countFor(country: String): Int = count { it.country == country }
