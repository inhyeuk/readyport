package com.readyport.ui.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.R
import com.readyport.attractions.AttractionsRepository
import com.readyport.attractions.SavedAttractionsRepository
import com.readyport.itinerary.Itinerary
import com.readyport.itinerary.TripItinerary
import com.readyport.pack.PackRepository
import com.readyport.stay.Stays
import com.readyport.trip.TripRepository
import com.readyport.ui.attractions.attractionsBase
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.EmptyState
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.cardShadow
import com.readyport.ui.nav.TripItineraryRoute
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.trip.tripDateRange
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// =====================================================================================
// 관광 일정 화면 (2026-10-09 사장님 요청 — 찜을 내 관광 스케줄로 '날짜별로 나눠 담기')
//
// 한 여행의 날짜에서 하루 칸을 만들고(1일차 · 10월 8일 (목) …, 날짜를 모르면 '날짜 미정' 한 칸),
// 찜한 곳을 같은 지역끼리 같은 날로 나눠 담자고 **제안**한다 → 사람이 '이대로 담기'로 확인 → 그 뒤 위·아래로 그날 순서를,
// '다른 날로'로 날짜를 바꾼다. 다시 옮겨 담으면 새로 찜한 곳만 더한다(사람이 고친 것은 그대로).
// 저장은 여행 장부(기기 안)에 키·날·순서만. 숫자(거리·시간)는 보이지 않는다(결정 D7).
// =====================================================================================

/** 관광 일정 화면에서 하는 일·가는 길 */
data class ItineraryActions(
    val openDetail: (country: String, id: String) -> Unit = { _, _ -> },
    val openSaved: (country: String) -> Unit = {},
    val openAttractions: (country: String) -> Unit = {},
    val confirm: () -> Unit = {},
    val dismissProposal: () -> Unit = {},
    val startProposal: () -> Unit = {},
    val shift: (key: String, by: Int) -> Unit = { _, _ -> },
    val moveToDay: (key: String, day: Int) -> Unit = { _, _ -> },
    val remove: (key: String) -> Unit = {},
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItineraryViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    repo: AttractionsRepository,
    saved: SavedAttractionsRepository,
    packs: PackRepository,
    private val trips: TripRepository,
    wallet: WalletRepository,
) : ViewModel() {
    private val route = handle.toRoute<TripItineraryRoute>()
    private val tripId = route.tripId

    /** 옮겨 담기 제안을 보이는 중인지 — 라우트 값은 처음 한 번만 쓰고, 담거나 닫으면 false로 남는다(뒤로 돌아와도 다시 뜨지 않게) */
    private val proposing = handle.getStateFlow(KEY_PROPOSING, route.transplant)

    private val country = trips.book.map { b -> b.trips.firstOrNull { it.id == tripId }?.country }.distinctUntilChanged()

    val ui: StateFlow<ItineraryUi> = country.flatMapLatest { cc ->
        if (cc == null) {
            flowOf(ItineraryUi(loading = false))
        } else {
            combine(attractionsBase(cc, repo, saved, packs, trips, wallet), trips.book, saved.saved, wallet.state, proposing) { base, book, items, w, p ->
                val trip = book.trips.firstOrNull { it.id == tripId } ?: return@combine ItineraryUi(loading = false)
                // 묵는 곳 좌표: 보관함이 열려 있을 때만 — 날 고르기 계산에만 쓰고 저장·전송하지 않는다
                val stays = (w as? WalletState.Unlocked)?.contents?.stays?.let { Stays.forTrip(it, trip) }.orEmpty()
                ItineraryModel.build(
                    trip = trip,
                    itinerary = book.itineraries[tripId] ?: TripItinerary(),
                    catalog = base.catalog,
                    advisory = base.advisory,
                    saved = items,
                    today = LocalDate.now(),
                    stays = stays,
                    proposing = p,
                    countryName = base.countryName,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItineraryUi())

    fun confirm() = viewModelScope.launch {
        val stops = ui.value.proposal?.stops.orEmpty()
        if (stops.isNotEmpty()) trips.editItinerary(tripId) { Itinerary.add(it, stops) }
        handle[KEY_PROPOSING] = false
    }

    fun dismissProposal() {
        handle[KEY_PROPOSING] = false
    }

    fun startProposal() {
        handle[KEY_PROPOSING] = true
    }

    fun shift(key: String, by: Int) = viewModelScope.launch {
        val days = ui.value.days.size
        trips.editItinerary(tripId) { Itinerary.shift(it, key, by, days) }
    }

    fun moveToDay(key: String, day: Int) = viewModelScope.launch {
        trips.editItinerary(tripId) { Itinerary.moveToDay(it, key, day) }
    }

    fun remove(key: String) = viewModelScope.launch {
        trips.editItinerary(tripId) { Itinerary.remove(it, key) }
    }

    private companion object {
        const val KEY_PROPOSING = "itinerary_proposing"
    }
}

@Composable
fun TripItineraryScreen(
    openDetail: (country: String, id: String) -> Unit,
    openSaved: (country: String) -> Unit,
    openAttractions: (country: String) -> Unit,
    onGone: () -> Unit,
    viewModel: ItineraryViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    if (ui.loading) return
    if (ui.trip == null) {
        // 지운 여행 — 돌아간다
        LaunchedEffect(Unit) { onGone() }
        return
    }
    ItineraryContent(
        ui,
        ItineraryActions(
            openDetail = openDetail,
            openSaved = openSaved,
            openAttractions = openAttractions,
            confirm = { viewModel.confirm() },
            dismissProposal = viewModel::dismissProposal,
            startProposal = viewModel::startProposal,
            shift = { k, by -> viewModel.shift(k, by) },
            moveToDay = { k, d -> viewModel.moveToDay(k, d) },
            remove = { viewModel.remove(it) },
        ),
    )
}

/**
 * 관광 일정 화면 내용. 위에서부터: (샘플 띠) → [제안이면] 제안 카드 + 날별 미리보기 + `이대로 담기`/`담지 않기]
 * [아니면] 새로 찜한 곳 담기 줄 → 안내 한 줄 → 날마다 머리(`1일차 · 10월 8일 (목)` + 오늘) + 카드(곳 줄) → 찜 목록 길 → 기기 안 저장.
 */
@Composable
fun ItineraryContent(ui: ItineraryUi, actions: ItineraryActions) {
    val trip = ui.trip ?: return
    val dimens = LocalDimens.current
    var picking by remember { mutableStateOf<StopUi?>(null) }
    val dates = if (trip.datesValid) tripDateRange(trip.start, trip.end) else stringResource(R.string.itinerary_day_undated)
    val country = ui.countryName.ifBlank { trip.country }
    AppScreen(
        title = stringResource(R.string.itinerary_title),
        subtitle = stringResource(R.string.itinerary_subtitle, country, dates),
        speech = stringResource(R.string.itinerary_speech, country, ui.total),
        icon = Icons.Outlined.Route,
    ) {
        if (ui.sample) {
            item(key = "sample") { NoticeBanner(stringResource(R.string.attractions_sample_banner), icon = Icons.Outlined.Info) }
        }
        val proposal = ui.proposal
        if (proposal != null) {
            item(key = "proposal") {
                val body = listOfNotNull(
                    stringResource(R.string.itinerary_proposal_body, proposal.stops.size),
                    stringResource(R.string.itinerary_proposal_stays).takeIf { proposal.usedStays },
                    stringResource(R.string.itinerary_proposal_after),
                ).joinToString(" ")
                CardNewsCard(title = stringResource(R.string.itinerary_proposal_title), icon = Icons.Outlined.Route, body = body, tone = BadgeTone.Teal) {
                    if (proposal.skipped > 0) IconBullet(stringResource(R.string.itinerary_proposal_skipped, proposal.skipped), Icons.Outlined.Info)
                }
            }
            proposal.days.forEach { day ->
                item(key = "proposal-day-${day.slot.index}") {
                    DaySection(day) { i, stop ->
                        OrderedPlaceRow(
                            number = i + 1,
                            stop = stop,
                            onOpen = null,
                            actions = emptyList(),
                            extraTag = if (stop.isNew) {
                                { StatusTag(stringResource(R.string.itinerary_proposal_new), StatusKind.Info) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            item(key = "proposal-actions") {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                    PrimaryButton(stringResource(R.string.itinerary_proposal_confirm), onClick = actions.confirm, icon = Icons.Outlined.TaskAlt)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        QuietButton(stringResource(R.string.itinerary_proposal_cancel), actions.dismissProposal)
                    }
                }
            }
            return@AppScreen
        }
        if (ui.allIn) {
            item(key = "all-in") { NoticeBanner(stringResource(R.string.itinerary_all_in), icon = Icons.Outlined.TaskAlt) }
        }
        if (ui.fresh > 0) {
            item(key = "fresh") {
                ListGroup {
                    ListRow(
                        stringResource(R.string.itinerary_new_row, ui.fresh),
                        icon = Icons.Filled.Favorite,
                        tone = BadgeTone.Teal,
                        body = stringResource(R.string.itinerary_new_row_body),
                        onClick = actions.startProposal,
                    )
                }
            }
        }
        if (ui.total == 0) {
            if (ui.fresh == 0) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Outlined.FavoriteBorder,
                        stringResource(R.string.itinerary_empty_title),
                        stringResource(if (ui.available) R.string.itinerary_empty_body else R.string.itinerary_empty_not_yet),
                        action = if (ui.available) {
                            { QuietButton(stringResource(R.string.itinerary_open_attractions), { actions.openAttractions(trip.country) }, icon = Icons.Outlined.TravelExplore) }
                        } else {
                            null
                        },
                    )
                }
            }
        } else {
            item(key = "hint") { KoText(stringResource(R.string.itinerary_hint), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary) }
            ui.days.forEach { day ->
                item(key = "day-${day.slot.index}") {
                    DaySection(day) { i, stop ->
                        val a = stop.attraction
                        OrderedPlaceRow(
                            number = i + 1,
                            stop = stop,
                            onOpen = a?.let { { actions.openDetail(it.country, it.id) } },
                            actions = upDownActions(stop.name, i, day.stops.size) { by -> actions.shift(stop.key, by) } +
                                RowAction(
                                    stringResource(R.string.itinerary_move_day),
                                    stringResource(R.string.itinerary_move_day_cd, stop.name),
                                    Icons.Outlined.EditCalendar,
                                ) { picking = stop },
                        )
                    }
                }
            }
        }
        item(key = "footer") {
            Column(Modifier.padding(top = dimens.inner), verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                if (ui.available) QuietButton(stringResource(R.string.itinerary_open_saved), { actions.openSaved(trip.country) }, icon = Icons.Filled.Favorite)
                IconBullet(stringResource(R.string.itinerary_saved_local), Icons.Outlined.Lock)
            }
        }
    }
    picking?.let { stop ->
        val slots = ui.days.map { it.slot }
        val current = ui.days.firstOrNull { d -> d.stops.any { it.key == stop.key } }?.slot?.index ?: 0
        DayPickDialog(
            name = stop.name,
            slots = slots,
            current = current,
            onPick = { day -> if (day != current) actions.moveToDay(stop.key, day); picking = null },
            onRemove = { actions.remove(stop.key); picking = null },
            onDismiss = { picking = null },
        )
    }
}

/** 하루 묶음: 굵은 머리(`1일차 · 10월 8일 (목)` + 오늘) → (먼 지역이 섞였으면 한 줄) → 흰 카드 안 곳 줄 */
@Composable
internal fun DaySection(day: DayUi, row: @Composable (Int, StopUi) -> Unit) {
    val dimens = LocalDimens.current
    Column(Modifier.fillMaxWidth().padding(top = dimens.inner), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KoText(dayLabel(day.slot), MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Tokens.Ink, heading = true)
            if (day.today) TodayTag()
        }
        // 연한 바탕이 양옆으로 12dp 내미는 줄이라 카드 밖에서는 그만큼 들인다(화면 여백 선을 지킨다)
        if (day.mixedFar) MixedFarNote(Modifier.padding(horizontal = 12.dp))
        val shape = MaterialTheme.shapes.large
        Surface(color = Tokens.Surface, shape = shape, modifier = Modifier.fillMaxWidth().cardShadow(shape)) {
            Column {
                if (day.stops.isEmpty()) {
                    KoText(
                        stringResource(R.string.itinerary_day_empty),
                        MaterialTheme.typography.bodyLarge,
                        Modifier.padding(horizontal = dimens.listRowPadding, vertical = dimens.listRowPaddingVertical),
                        color = Tokens.InkSecondary,
                    )
                }
                day.stops.forEachIndexed { i, stop ->
                    if (i > 0) HorizontalDivider(thickness = 1.dp, color = Tokens.Line)
                    row(i, stop)
                }
            }
        }
    }
}
