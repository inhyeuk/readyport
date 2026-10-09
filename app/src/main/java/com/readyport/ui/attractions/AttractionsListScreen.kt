package com.readyport.ui.attractions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.attractions.AccessMode
import com.readyport.attractions.AdvisoryLevel
import com.readyport.attractions.Attraction
import com.readyport.attractions.Category
import com.readyport.attractions.LiftReason
import com.readyport.attractions.RegionGroup
import com.readyport.attractions.RegionKind
import com.readyport.attractions.search.ReasonKind
import com.readyport.attractions.search.SearchReason
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.EmptyState
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.KoText
import com.readyport.ui.components.ListDivider
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.RowTrailing
import com.readyport.ui.components.SearchField
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.rememberKeyIndex
import com.readyport.ui.components.scrollToKey
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import kotlinx.coroutines.launch

/** 목록 화면에서 다른 곳으로 가는 길·바꾸는 일 */
data class AttractionsListActions(
    val setQuery: (String) -> Unit = {},
    val setCategory: (Category?) -> Unit = {},
    val setSavedOnly: (Boolean) -> Unit = {},
    val setSaved: (key: String, on: Boolean) -> Unit = { _, _ -> },
    val openDetail: (id: String) -> Unit = {},
    /** 나라 화면 › 여행 정보 › 안전 카드 */
    val openSafety: () -> Unit = {},
    /** 찾는 곳이 없을 때 게시판 */
    val openBoard: () -> Unit = {},
    val dismissFirstNotice: () -> Unit = {},
    /** 찜 목록 순서 바꾸기·여행 일정에 담기 (2026-10-09, SavedOrderList.kt) */
    val savedOrder: SavedOrderActions = SavedOrderActions(),
)

@Composable
fun AttractionsListScreen(
    openDetail: (String) -> Unit,
    openSafety: (String) -> Unit,
    openBoard: () -> Unit,
    openItinerary: (tripId: String) -> Unit = {},
    makeTrip: (country: String) -> Unit = {},
    viewModel: AttractionsListViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val focusOnStart = remember { viewModel.consumeFocusSearch() }
    val scrollTo = remember { viewModel.consumeScrollToRegion() }
    AttractionsListContent(
        ui = ui,
        actions = AttractionsListActions(
            setQuery = viewModel::setQuery,
            setCategory = viewModel::setCategory,
            setSavedOnly = viewModel::setSavedOnly,
            setSaved = { key, on -> viewModel.setSaved(key, on) },
            openDetail = openDetail,
            openSafety = { openSafety(viewModel.country) },
            openBoard = openBoard,
            dismissFirstNotice = { viewModel.dismissFirstNotice() },
            savedOrder = SavedOrderActions(
                openDetail = openDetail,
                move = { key, by -> viewModel.moveSaved(key, by) },
                moveTo = { key, index -> viewModel.moveSavedTo(key, index) },
                openItinerary = openItinerary,
                makeTrip = { makeTrip(viewModel.country) },
            ),
        ),
        focusSearchOnStart = focusOnStart,
        scrollToRegion = scrollTo,
    )
}

/**
 * 관광지 목록 (종류·검색·찜 공용, SPEC_v5 §6.3): 검색 칸 → (입력 중) 개수 줄 → 걸러 보기 → 개수·안내 → 지역 묶음(굵은 가로줄 + 지역 카드).
 * 키보드: 화면에 imePadding, 끌어서 스크롤하면 키보드를 내린다. [focusSearchOnStart]·[scrollToRegion]은 부르는 쪽이 한 번만 준다.
 */
@Composable
fun AttractionsListContent(
    ui: AttractionsListUi,
    actions: AttractionsListActions,
    focusSearchOnStart: Boolean = false,
    scrollToRegion: String? = null,
    listState: LazyListState = rememberLazyListState(),
    initialFilterOpen: Boolean = false,
) {
    val content = ui.content
    val dimens = LocalDimens.current
    val single = rememberGridColumns() == 1
    val keys = rememberKeyIndex()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(initialFilterOpen) }
    var confirmUnsave by remember { mutableStateOf<String?>(null) }
    // 지도로 보기(화면 가득 지도). 상세에 다녀와도 다시 열린 채로
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    val mapMode = rememberListMapMode(ui)
    val title = if (ui.savedOnly) {
        stringResource(R.string.attractions_saved_title, ui.countryName)
    } else {
        stringResource(R.string.attractions_screen_title, ui.countryName)
    }
    // 끌어서 스크롤하면 키보드를 내린다 (§6.3 키보드 규칙 4)
    val dragClears = remember(focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) focusManager.clearFocus()
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(focusSearchOnStart) {
        if (focusSearchOnStart) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(scrollToRegion, content != null) {
        if (scrollToRegion != null && content != null) {
            withFrameNanos { }
            listState.scrollToKey(keys, "region-$scrollToRegion")
        }
    }
    val categories = content?.categories.orEmpty()
    val collapsible = categories.size >= AttractionsListModel.COLLAPSE_FILTER_AT
    // 찜 목록(검색어 없음)은 지역 묶음 대신 사람이 정한 찜 순서 한 줄 (2026-10-09, SavedOrderList.kt)
    val ordered = ui.savedOnly && ui.query.isBlank()
    val savedRows = remember(ordered, ui.catalog, ui.savedItems, ui.category, ui.advisory, ui.country) {
        if (ordered) SavedOrderModel.rows(ui.catalog, ui.savedItems, ui.country, ui.category, ui.advisory) else emptyList()
    }
    val speech = stringResource(R.string.attractions_list_speech, title, content?.total ?: 0, content?.regionCount ?: 0) +
        content?.groups.orEmpty().joinToString(" ") { g -> g.region.nameKo + ": " + g.places.take(5).joinToString(", ") { it.nameKo } + "." }

    AppScreen(
        title = title,
        speech = speech,
        modifier = Modifier.imePadding().nestedScroll(dragClears),
        state = listState,
        keyIndex = keys,
    ) {
        if (ui.catalog?.sample == true) {
            item(key = "sample") { NoticeBanner(stringResource(R.string.attractions_sample_banner), icon = Icons.Outlined.Info) }
        }
        if (ui.advisory.changed) {
            item(key = "advisory-changed") { AdvisoryChangedNotice(ui.advisory.packVerified, actions.openSafety) }
        }
        if (ui.showFirstNotice) {
            item(key = "first-notice") { FirstSaveNotice(actions.dismissFirstNotice) }
        }
        item(key = "search") {
            SearchField(
                query = ui.query,
                onChange = actions.setQuery,
                label = stringResource(R.string.attractions_search),
                clearLabel = stringResource(R.string.attractions_search_clear),
                focusRequester = focusRequester,
                onFocusChange = { has ->
                    focused = has
                    if (has) {
                        filterOpen = false
                        scope.launch { listState.scrollToKey(keys, "search") }
                    }
                },
            )
        }
        if (content == null) return@AppScreen
        if (focused || ui.query.isNotBlank()) {
            item(key = "count") {
                KoText(
                    stringResource(R.string.attractions_count_line, content.total, content.regionCount),
                    MaterialTheme.typography.titleSmall,
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = Tokens.InkSecondary,
                )
            }
        }
        if (categories.size > 1 || ui.savedOnly || ui.category != null) {
            item(key = "filter") {
                FilterBar(
                    categories = categories,
                    selected = ui.category,
                    savedOnly = ui.savedOnly,
                    collapsible = collapsible,
                    open = filterOpen || !collapsible,
                    onToggle = { filterOpen = !filterOpen },
                    onCategory = actions.setCategory,
                    onSavedOnly = actions.setSavedOnly,
                    single = single,
                )
            }
        }
        content.search?.categoryHint?.takeIf { it != ui.category }?.let { hint ->
            item(key = "category-hint") {
                ListGroup {
                    ListRow(
                        stringResource(R.string.attractions_category_hint, stringResource(hint.labelRes())),
                        icon = hint.icon(),
                        tone = BadgeTone.Teal,
                        onClick = { actions.setQuery(""); actions.setCategory(hint) },
                    )
                }
            }
        }
        if (content.search?.chosung == true) {
            item(key = "chosung") { StatusTag(stringResource(R.string.attractions_chosung), StatusKind.Info) }
        }
        if (content.total > 0) {
            item(key = "count-hint") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (ui.catalog?.compact == true) {
                        KoText(stringResource(R.string.attractions_region_count, content.total), MaterialTheme.typography.titleSmall, color = Tokens.Ink)
                    } else {
                        KoText(
                            stringResource(R.string.attractions_region_count, content.total) + " · " + stringResource(R.string.attractions_region_hint),
                            MaterialTheme.typography.bodyMedium,
                            color = Tokens.InkSecondary,
                        )
                    }
                    ui.upcomingAirportName?.let {
                        StatusTag(stringResource(R.string.attractions_airport_upcoming, it), StatusKind.Info, icon = Icons.Outlined.FlightLand)
                    }
                }
            }
        }
        if (content.total > 0 && mapMode == MapMode.InApp) {
            item(key = "map") { AttractionsMapButton(onClick = { mapOpen = true }) }
        }
        if (ordered) {
            savedOrderItems(
                rows = savedRows,
                reorderable = ui.category == null,
                countryName = ui.countryName,
                trips = ui.trips,
                merged = ui.merged,
                actions = actions.savedOrder.copy(onGone = { confirmUnsave = it }),
            )
        }
        if (!ordered && (content.regionCount > AttractionsListModel.JUMP_REGIONS || content.total > AttractionsListModel.JUMP_PLACES)) {
            item(key = "jump") {
                ListGroup(stringResource(R.string.attractions_jump_title)) {
                    content.groups.forEachIndexed { i, g ->
                        if (i > 0) ListDivider()
                        val cd = stringResource(R.string.attractions_jump_cd, g.region.nameKo)
                        ListRow(
                            stringResource(R.string.attractions_jump_row, g.region.nameKo, g.places.size),
                            modifier = Modifier.semantics { contentDescription = cd },
                            body = g.region.groupKo.ifBlank { null },
                            onClick = { scope.launch { listState.scrollToKey(keys, "region-${g.region.id}") } },
                        )
                    }
                }
            }
        }
        if (!ordered) content.groups.forEach { g ->
            item(key = "region-${g.region.id}") {
                RegionHeader(g, ui.savedKeys, levelHidden = ui.advisory.changed)
            }
            item(key = "region-${g.region.id}-card") {
                RegionCard(
                    group = g,
                    savedKeys = ui.savedKeys,
                    reasons = content.reasons,
                    showBadge = ui.category == null,
                    single = single,
                    levelHidden = ui.advisory.changed,
                    onOpen = actions.openDetail,
                    onSave = actions.setSaved,
                )
            }
        }
        if (content.widenCategory > 0 || content.widenSaved > 0) {
            item(key = "widen") {
                ListGroup {
                    if (content.widenCategory > 0) {
                        ListRow(stringResource(R.string.attractions_widen_category, content.widenCategory), onClick = { actions.setCategory(null) })
                    }
                    if (content.widenCategory > 0 && content.widenSaved > 0) ListDivider()
                    if (content.widenSaved > 0) {
                        ListRow(stringResource(R.string.attractions_widen_saved, content.widenSaved), onClick = { actions.setSavedOnly(false) })
                    }
                }
            }
        }
        val search = content.search
        search?.upcoming?.let { name ->
            item(key = "upcoming") {
                NoticeBanner(stringResource(R.string.attractions_upcoming, name), icon = Icons.Outlined.Schedule)
            }
        }
        search?.excludedArea?.let { area ->
            item(key = "excluded-area") {
                val level = if (area.level == "special") {
                    stringResource(R.string.attractions_level_special)
                } else {
                    stringResource(R.string.attractions_level_step, area.level)
                }
                Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                    NoticeBanner(stringResource(R.string.attractions_excluded_area, area.nameKo, level), icon = Icons.Outlined.ReportProblem, tone = BannerTone.Caution)
                    ListGroup { ListRow(stringResource(R.string.attractions_safety_open), icon = Icons.Outlined.Shield, tone = BadgeTone.Caution, onClick = actions.openSafety) }
                }
            }
        }
        search?.excludedTopic?.let { (word, instead) ->
            item(key = "excluded-topic") {
                val text = if (instead != null) {
                    stringResource(R.string.attractions_excluded_topic, word, stringResource(instead.labelRes()))
                } else {
                    stringResource(R.string.attractions_excluded_topic_plain, word)
                }
                Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                    NoticeBanner(text, icon = Icons.Outlined.Info)
                    if (instead != null) {
                        ListGroup {
                            ListRow(
                                stringResource(R.string.attractions_category_hint, stringResource(instead.labelRes())),
                                icon = instead.icon(),
                                tone = BadgeTone.Teal,
                                onClick = { actions.setQuery(""); actions.setCategory(instead) },
                            )
                        }
                    }
                }
            }
        }
        val noNotice = search == null || (search.upcoming == null && search.excludedArea == null && search.excludedTopic == null)
        if (content.total == 0 && content.widenCategory == 0 && content.widenSaved == 0 && noNotice) {
            item(key = "empty") {
                when {
                    content.searching -> EmptyState(
                        Icons.Outlined.SearchOff,
                        stringResource(R.string.attractions_empty_search_title),
                        stringResource(R.string.attractions_empty_search_body),
                        action = { QuietButton(stringResource(R.string.attractions_empty_board), actions.openBoard, icon = Icons.Outlined.Forum) },
                    )
                    ui.savedOnly && content.savedExtras.isEmpty() -> EmptyState(
                        Icons.Outlined.FavoriteBorder,
                        stringResource(R.string.attractions_saved_empty_title),
                        stringResource(R.string.attractions_saved_empty_body),
                    )
                    else -> Unit
                }
            }
        }
        if (focused && ui.query.isBlank()) {
            item(key = "suggest") { Suggestions(ui, actions) }
        }
        if (!ordered && content.savedExtras.isNotEmpty()) {
            item(key = "saved-extras") {
                ListGroup {
                    content.savedExtras.forEachIndexed { i, extra ->
                        if (i > 0) ListDivider()
                        val merged = extra.key in ui.merged
                        ListRow(
                            extra.name,
                            icon = Icons.Filled.Favorite,
                            tone = BadgeTone.Teal,
                            trailing = RowTrailing.Custom { SavedGoneTag(extra.gone) },
                            body = if (merged) stringResource(R.string.attractions_merged) else (extra.gone as? SavedGone.Retired)?.note,
                            onClick = { confirmUnsave = extra.key },
                        )
                    }
                }
            }
        }
        if (content.total > 3) {
            item(key = "top") {
                SecondaryButton(
                    stringResource(R.string.attractions_scroll_top),
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    icon = Icons.Outlined.VerticalAlignTop,
                )
            }
        }
    }
    if (mapOpen && mapMode == MapMode.InApp) {
        AttractionsListMapDialog(ui, onDismiss = { mapOpen = false }, openDetail = actions.openDetail)
    }
    confirmUnsave?.let { key ->
        DestructiveConfirm(
            title = stringResource(R.string.attractions_unsave_title),
            body = stringResource(R.string.attractions_unsave_body),
            confirmLabel = stringResource(R.string.attractions_unsave),
            onConfirm = { actions.setSaved(key, false); confirmUnsave = null },
            onDismiss = { confirmUnsave = null },
        )
    }
}

@Composable
internal fun SavedGoneTag(gone: SavedGone) {
    when (gone) {
        SavedGone.LoadFailed -> StatusTag(stringResource(R.string.attractions_saved_load_failed), StatusKind.Soon)
        SavedGone.Missing -> StatusTag(stringResource(R.string.attractions_saved_missing), StatusKind.Soon)
        SavedGone.NeedsUpdate -> StatusTag(stringResource(R.string.attractions_saved_need_update), StatusKind.Soon)
        SavedGone.Safety -> StatusTag(stringResource(R.string.attractions_saved_safety), StatusKind.Caution)
        is SavedGone.Retired -> StatusTag(
            stringResource(
                when (gone.reason) {
                    "closed" -> R.string.attractions_retired_closed
                    "long_closure" -> R.string.attractions_retired_long_closure
                    "safety" -> R.string.attractions_retired_safety
                    "merged" -> R.string.attractions_merged
                    else -> R.string.attractions_retired_editorial
                },
            ),
            StatusKind.Prohibited,
        )
    }
}

/** 처음 찜 안내 (§6.5) — 한 번만, [알겠어요]로 닫는다 */
@Composable
internal fun FirstSaveNotice(onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NoticeBanner(stringResource(R.string.attractions_first_save_notice), icon = Icons.Filled.Favorite)
        QuietButton(stringResource(R.string.attractions_first_save_ok), onDismiss)
    }
}

/** §5.7 (c): 나라 안내의 경보가 관광지보다 새로 바뀌었을 수 있을 때 맨 위 한 줄 + 안전 정보 보기 */
@Composable
internal fun AdvisoryChangedNotice(verified: String?, openSafety: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner)) {
        NoticeBanner(
            stringResource(R.string.attractions_advisory_changed, verified?.replace('-', '.').orEmpty()),
            icon = Icons.Outlined.ReportProblem,
            tone = BannerTone.Caution,
        )
        ListGroup { ListRow(stringResource(R.string.attractions_safety_open), icon = Icons.Outlined.Shield, tone = BadgeTone.Caution, onClick = openSafety) }
    }
}

/** 걸러 보기: 종류 4개 이상이면 접힌 버튼 하나, 3개 이하이면 늘 펼친 칩 (§6.3, 2열·1열 공통) */
@Composable
private fun FilterBar(
    categories: List<Category>,
    selected: Category?,
    savedOnly: Boolean,
    collapsible: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    onCategory: (Category?) -> Unit,
    onSavedOnly: (Boolean) -> Unit,
    single: Boolean,
) {
    val dimens = LocalDimens.current
    val current = buildList {
        add(selected?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.attractions_all_categories))
        if (savedOnly) add(stringResource(R.string.attractions_filter_saved_only))
    }.joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
        if (collapsible) {
            SecondaryButton(
                if (open) stringResource(R.string.attractions_filter_close) else stringResource(R.string.attractions_filter_button, current),
                onClick = onToggle,
                icon = Icons.Outlined.FilterList,
                tone = BadgeTone.Teal,
            )
        }
        if (open) {
            if (single) {
                // 1열(쉬운 모드·큰 글자): 칩 대신 폭 전체 줄 — 고른 줄은 체크
                ListGroup {
                    (listOf<Category?>(null) + categories).forEachIndexed { i, c ->
                        if (i > 0) ListDivider()
                        ListRow(
                            c?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.attractions_all_categories),
                            icon = c?.icon(),
                            tone = BadgeTone.Teal,
                            trailing = if (c == selected) RowTrailing.Custom { StatusTag(stringResource(R.string.attractions_filter_selected), StatusKind.Info) } else RowTrailing.None,
                            onClick = { onCategory(c) },
                        )
                    }
                    ListDivider()
                    ListRow(
                        stringResource(R.string.attractions_filter_saved_only_row),
                        icon = Icons.Filled.Favorite,
                        tone = BadgeTone.Teal,
                        trailing = RowTrailing.Switch(savedOnly, onSavedOnly),
                    )
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip(selected = selected == null, onClick = { onCategory(null) }, label = stringResource(R.string.attractions_all_categories))
                    categories.forEach { c ->
                        SelectChip(selected = selected == c, onClick = { onCategory(c) }, label = stringResource(c.labelRes()), leadingIcon = c.icon())
                    }
                    SelectChip(
                        selected = savedOnly,
                        onClick = { onSavedOnly(!savedOnly) },
                        label = stringResource(R.string.attractions_filter_saved_only),
                        leadingIcon = Icons.Outlined.FavoriteBorder,
                        singleChoice = false,
                    )
                }
            }
        }
    }
}

/** 빈 검색어 추천: 지역 이름·종류 버튼 */
@Composable
private fun Suggestions(ui: AttractionsListUi, actions: AttractionsListActions) {
    val catalog = ui.catalog ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KoText(stringResource(R.string.attractions_suggest_title), MaterialTheme.typography.titleSmall, color = Tokens.InkSecondary, heading = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            catalog.regions.filter { r -> catalog.attractions.any { it.regionId == r.id } }.sortedBy { it.order }.forEach { r ->
                SelectChip(selected = false, onClick = { actions.setQuery(r.nameKo) }, label = r.nameKo, leadingIcon = Icons.Outlined.Place, singleChoice = false)
            }
            ui.content?.categories.orEmpty().forEach { c ->
                SelectChip(selected = false, onClick = { actions.setCategory(c) }, label = stringResource(c.labelRes()), leadingIcon = c.icon(), singleChoice = false)
            }
        }
    }
}

/** 지역 머리: 굵은 가로줄(2dp LineStrong) + 지역 이름(제목) + 끌어올린 이유 + 곳 수 + 하루 다녀오는 곳 안내 + 배 + 경보 2단계 띠 */
@Composable
private fun RegionHeader(group: RegionGroup, savedKeys: Set<String>, levelHidden: Boolean) {
    val r = group.region
    val dimens = LocalDimens.current
    val savedCount = group.places.count { it.key in savedKeys }
    Column(Modifier.fillMaxWidth().padding(top = dimens.inner), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 4.dp), thickness = 2.dp, color = Tokens.LineStrong)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val style = MaterialTheme.typography.titleLarge
            Icon(Icons.Outlined.Place, contentDescription = null, tint = Tokens.TealText, modifier = Modifier.size(textIconSize(dimens.icon, style)))
            KoText(r.nameKo, style.copy(fontWeight = FontWeight.Bold), Modifier.weight(1f), color = Tokens.Ink, heading = true)
        }
        if (group.lifted != null || r.waterCrossing) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (group.lifted) {
                LiftReason.Stay -> StatusTag(stringResource(R.string.attractions_lift_stay), StatusKind.Info, icon = Icons.Outlined.Hotel)
                LiftReason.Airport -> StatusTag(stringResource(R.string.attractions_lift_airport), StatusKind.Info, icon = Icons.Outlined.FlightLand)
                null -> Unit
            }
            if (r.waterCrossing) StatusTag(stringResource(R.string.attractions_water_crossing), StatusKind.Info, icon = Icons.Outlined.DirectionsBoat)
        }
        val count = if (savedCount > 0) {
            stringResource(R.string.attractions_region_count_saved, group.places.size, savedCount)
        } else {
            stringResource(R.string.attractions_region_count, group.places.size)
        }
        val note = if (r.kind == RegionKind.Daytrip) (r.noteKo ?: stringResource(R.string.attractions_daytrip)) else null
        KoText(listOfNotNull(count, note).joinToString(" · "), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        if (group.wholeLevel2 && !levelHidden) AdvisoryLevelNote(group.places.first().advisory.lastVerified)
    }
}

/** 지역 카드: 그 지역 관광지 줄을 카드 한 장에 (줄 사이 가는 선) */
@Composable
private fun RegionCard(
    group: RegionGroup,
    savedKeys: Set<String>,
    reasons: Map<String, SearchReason?>,
    showBadge: Boolean,
    single: Boolean,
    levelHidden: Boolean,
    onOpen: (String) -> Unit,
    onSave: (String, Boolean) -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(color = Tokens.Surface, shape = shape, modifier = Modifier.fillMaxWidth().cardShadow(shape)) {
        Column {
            group.places.forEachIndexed { i, a ->
                if (i > 0) ListDivider(indent = false)
                AttractionRow(
                    a = a,
                    regionName = group.region.nameKo,
                    saved = a.key in savedKeys,
                    reason = reasons[a.id],
                    showBadge = showBadge && !single,
                    single = single,
                    mixedLevel2 = !group.wholeLevel2 && a.advisory.level == AdvisoryLevel.Two && !levelHidden,
                    onOpen = { onOpen(a.id) },
                    onSave = { onSave(a.key, it) },
                )
            }
        }
    }
}

/**
 * 관광지 한 줄. 2열: 줄 전체 = 상세, 하트는 따로 떨어진 토글(48dp). 1열(쉬운 모드·큰 글자): 하트 없이 '찜했어요' 태그.
 * TalkBack: '이름, 지역, 종류, 배를 타요' (+ ', 찜함'), 찜하기·찜 취소는 사용자 지정 동작으로도.
 */
@Composable
internal fun AttractionRow(
    a: Attraction,
    regionName: String,
    saved: Boolean,
    reason: SearchReason?,
    showBadge: Boolean,
    single: Boolean,
    mixedLevel2: Boolean,
    onOpen: () -> Unit,
    onSave: (Boolean) -> Unit,
) {
    val dimens = LocalDimens.current
    val category = stringResource(a.category.labelRes())
    val boat = AccessMode.Boat in a.accessModes
    val carOnly = a.accessModes == listOf(AccessMode.CarOnly)
    val boatText = stringResource(R.string.attractions_water_crossing)
    val carText = stringResource(R.string.attractions_car_only)
    val savedState = stringResource(R.string.attractions_saved_state)
    val speech = listOfNotNull(a.title, regionName, category, boatText.takeIf { boat }, carText.takeIf { carOnly }, savedState.takeIf { saved }).joinToString(", ")
    val saveLabel = stringResource(R.string.attractions_save)
    val unsaveLabel = stringResource(R.string.attractions_unsave)
    val openLabel = stringResource(R.string.attractions_open_detail)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
            .padding(start = dimens.listRowPadding, end = if (single) dimens.listRowPadding else 4.dp, top = dimens.listRowPaddingVertical, bottom = dimens.listRowPaddingVertical),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showBadge) IconBadge(a.category.icon(), tone = BadgeTone.Teal)
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    contentDescription = speech
                    if (!single) {
                        customActions = listOf(
                            CustomAccessibilityAction(if (saved) unsaveLabel else saveLabel) { onSave(!saved); true },
                        )
                    }
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KoText(a.title, MaterialTheme.typography.titleMedium, color = Tokens.Ink, glueShort = true)
            if (single) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(a.category.icon(), contentDescription = null, tint = Tokens.TealText, modifier = Modifier.size(dimens.iconSmall))
                    KoText(category, MaterialTheme.typography.bodySmall, color = Tokens.InkSecondary)
                }
            } else if (a.nameEn.isNotBlank() && a.nameEn != a.nameKo) {
                KoText(a.nameEn, MaterialTheme.typography.bodySmall, color = Tokens.InkSecondary)
            }
            if (a.summaryKo.isNotBlank()) KoText(a.summaryKo, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
            reason?.let { ReasonLine(it) }
            val tags = boat || carOnly || mixedLevel2 || a.advisory.level == AdvisoryLevel.Unknown || (single && saved)
            if (tags) {
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (boat) StatusTag(boatText, StatusKind.Info, icon = Icons.Outlined.DirectionsBoat)
                    if (carOnly) StatusTag(carText, StatusKind.Info, icon = Icons.Outlined.DirectionsCar)
                    if (mixedLevel2) StatusTag(stringResource(R.string.attractions_advisory_level2_tag), StatusKind.Caution)
                    if (a.advisory.level == AdvisoryLevel.Unknown) StatusTag(stringResource(R.string.attractions_advisory_unknown), StatusKind.Caution)
                    if (single && saved) StatusTag(stringResource(R.string.attractions_saved_tag), StatusKind.Info, icon = Icons.Filled.Favorite)
                }
            }
        }
        if (!single) {
            val cd = stringResource(R.string.attractions_save_cd, a.title)
            val state = if (saved) savedState else stringResource(R.string.attractions_unsaved_state)
            IconToggleButton(
                checked = saved,
                onCheckedChange = onSave,
                modifier = Modifier.minTouchSize().semantics {
                    contentDescription = cd
                    stateDescription = state
                },
            ) {
                Icon(
                    if (saved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (saved) Tokens.Accent else Tokens.InkSecondary,
                )
            }
        }
    }
}

/** 이유 줄 — 맞은 부분을 굵게 (조사 없는 틀) */
@Composable
private fun ReasonLine(reason: SearchReason) {
    val template = when (reason.kind) {
        ReasonKind.Alias -> R.string.attractions_reason_alias
        ReasonKind.English -> R.string.attractions_reason_english
        ReasonKind.Local -> R.string.attractions_reason_local
        ReasonKind.Region -> R.string.attractions_reason_region
        ReasonKind.Station -> R.string.attractions_reason_station
        ReasonKind.Category -> R.string.attractions_reason_category
        ReasonKind.Mention, ReasonKind.Body -> R.string.attractions_reason_mention
    }
    val full = stringResource(template, reason.text)
    val offset = full.indexOf(reason.text).coerceAtLeast(0)
    val styled: AnnotatedString = buildAnnotatedString {
        append(full)
        reason.bold?.let { r ->
            val start = (offset + r.first).coerceIn(0, full.length)
            val end = (offset + r.last + 1).coerceIn(start, full.length)
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Tokens.Ink), start, end)
        }
    }
    androidx.compose.material3.Text(styled, style = MaterialTheme.typography.bodySmall, color = Tokens.InkSecondary)
}

/** 여행경보 2단계 띠 (D15-B: 늘 보인다) + 출처 줄 */
@Composable
internal fun AdvisoryLevelNote(verified: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NoticeBanner(stringResource(R.string.attractions_advisory_level2), icon = Icons.Outlined.ReportProblem, tone = BannerTone.Caution)
        com.readyport.ui.components.SourceList(
            listOf(com.readyport.ui.components.SourceRef(stringResource(R.string.attractions_advisory_source), verified.replace('-', '.'))),
        )
    }
}
