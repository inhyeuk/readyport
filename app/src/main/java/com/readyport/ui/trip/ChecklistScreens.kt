package com.readyport.ui.trip

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.LocalAirport
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.pack.PackRepository
import com.readyport.trip.AutoState
import com.readyport.trip.Checklist
import com.readyport.trip.ChecklistAction
import com.readyport.trip.ChecklistData
import com.readyport.trip.ChecklistItem
import com.readyport.trip.ChecklistPhase
import com.readyport.trip.ChecklistProvider
import com.readyport.trip.ItemDetail
import com.readyport.trip.ItemKind
import com.readyport.trip.PassportStatus
import com.readyport.trip.Trip
import com.readyport.trip.TripNotifications
import com.readyport.trip.TripRepository
import com.readyport.trip.TripSelection
import com.readyport.trip.TripTiming
import com.readyport.trip.markFor
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.ButtonPlacement
import com.readyport.ui.components.CheckEmphasis
import com.readyport.ui.components.CheckProgressBar
import com.readyport.ui.components.ChecklistActions
import com.readyport.ui.components.ChecklistDivider
import com.readyport.ui.components.ChecklistPhaseCard
import com.readyport.ui.components.ChecklistRow
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.DotBullet
import com.readyport.ui.components.EmptyState
import com.readyport.ui.components.ExpandToggle
import com.readyport.ui.components.ExpandableDetail
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.InfoChip
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LinkRow
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.NumberText
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.PhoneNumberText
import com.readyport.ui.components.PhotoBox
import com.readyport.ui.components.PhotoTextArea
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.QuietDangerButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SourceFooter
import com.readyport.ui.components.SourceList
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.formWindowKo
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.keepMonthDay
import com.readyport.ui.components.koDisplay
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.personalWindowKo
import com.readyport.ui.components.rememberPhotoLift
import com.readyport.ui.components.rememberThumbnail
import com.readyport.ui.components.sectionGap
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.Tokens
import com.readyport.ui.wallet.rememberDeviceAuth
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// =====================================================================================
// 내 여행 목록 (2026-10-02 — 여러 여행: 같은 나라라도 날짜가 다르면 다른 여행·다른 체크리스트)
// =====================================================================================

/** 목록 한 줄 */
data class TripRow(
    val trip: Trip,
    val timing: TripTiming,
    val countryName: String,
    val done: Int,
    val total: Int,
    /** 다른 여행과 날짜가 겹치는지(막지 않고 가볍게 알린다) */
    val overlaps: Boolean = false,
)

data class TripListUi(val loaded: Boolean = false, val rows: List<TripRow> = emptyList(), val today: LocalDate = LocalDate.now())

@HiltViewModel
class TripListViewModel @Inject constructor(
    trips: TripRepository,
    private val checklists: ChecklistProvider,
) : ViewModel() {
    val ui: StateFlow<TripListUi> = trips.book.map { b ->
        val today = LocalDate.now()
        val overlap = TripSelection.overlapping(b.trips)
        val rows = TripSelection.ordered(b.trips, today).map { (timing, t) ->
            val data = checklists.build(t, b, today)
            TripRow(t, timing, checklists.countryName(t.country), data.done, data.total, t.id in overlap)
        }
        TripListUi(loaded = true, rows = rows, today = today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripListUi())
}

@Composable
fun TripListScreen(onOpen: (String) -> Unit, onAdd: () -> Unit, viewModel: TripListViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    if (!ui.loaded) return
    TripListContent(ui, onOpen, onAdd)
}

/**
 * 내 여행 목록: 여행 중 → 다가오는 여행 → 지난 여행(접어 둠). 줄마다 나라 사진 원형 썸네일 + 이름 + 날짜 + 상태 + 체크리스트 `12 / 30` 막대.
 * 날짜가 겹치는 여행이 있으면 맨 위 가벼운 안내 한 줄 + 그 줄에 `날짜가 겹쳐요`(주의 태그) — 막지는 않는다.
 * 주 버튼은 `새 여행 만들기` 하나(목록 아래).
 */
@Composable
fun TripListContent(ui: TripListUi, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    var pastOpen by rememberSaveable { mutableStateOf(false) }
    val ongoing = ui.rows.filter { it.timing == TripTiming.Ongoing }
    val upcoming = ui.rows.filter { it.timing == TripTiming.Upcoming }
    val past = ui.rows.filter { it.timing == TripTiming.Past }
    AppScreen(
        title = stringResource(R.string.trips_title),
        subtitle = stringResource(R.string.trips_subtitle),
        speech = stringResource(R.string.trips_speech),
    ) {
        if (ui.rows.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.Luggage,
                    title = stringResource(R.string.trips_empty_title),
                    body = stringResource(R.string.trips_empty_body),
                    tone = BadgeTone.Accent,
                )
            }
        }
        if (ui.rows.any { it.overlaps }) {
            item(key = "overlap") { NoticeBanner(stringResource(R.string.trips_overlap_note), icon = Icons.Outlined.EventBusy) }
        }
        if (ongoing.isNotEmpty()) {
            item(key = "ongoing-title") { SectionHeader(stringResource(R.string.trips_group_ongoing), icon = Icons.Outlined.Luggage) }
            ongoing.forEach { r -> item(key = "trip-${r.trip.id}") { TripRowCard(r, ui.today) { onOpen(r.trip.id) } } }
        }
        if (upcoming.isNotEmpty()) {
            if (ongoing.isNotEmpty()) sectionGap("upcoming-gap")
            item(key = "upcoming-title") { SectionHeader(stringResource(R.string.trips_group_upcoming), icon = Icons.Outlined.EventAvailable) }
            upcoming.forEach { r -> item(key = "trip-${r.trip.id}") { TripRowCard(r, ui.today) { onOpen(r.trip.id) } } }
        }
        item(key = "add") {
            PrimaryButton(stringResource(R.string.today_new_trip), onClick = onAdd, icon = Icons.Outlined.EditCalendar)
        }
        if (past.isNotEmpty()) {
            sectionGap("past-gap")
            item(key = "past-toggle") {
                ExpandToggle(
                    open = pastOpen,
                    onOpenChange = { pastOpen = it },
                    label = stringResource(R.string.trips_group_past, past.size),
                    target = stringResource(R.string.trips_past_target),
                )
            }
            if (pastOpen) past.forEach { r -> item(key = "trip-${r.trip.id}") { TripRowCard(r, ui.today) { onOpen(r.trip.id) } } }
        }
        item(key = "local") { IconBullet(stringResource(R.string.trips_local_only), Icons.Outlined.Lock) }
    }
}

/**
 * 여행 한 줄 = 누를 수 있는 흰 카드(Role.Button, 셰브론). TalkBack은 한 문장(`태국 여행, 11월 3일 ~ 7일, 출발까지 3일, 체크리스트 30개 중 12개 했어요`).
 * 큰 글자 배치에서는 썸네일을 글 위로.
 */
@Composable
private fun TripRowCard(row: TripRow, today: LocalDate, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.large
    val stacked = isStackedLayout()
    val dates = tripDateRange(row.trip.start, row.trip.end)
    val status = tripStatus(row, today)
    val cd = stringResource(R.string.trips_row_cd, row.countryName, dates, status, row.total, row.done)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().cardShadow(shape),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = cd }
                .padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.inner),
        ) {
            val head: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    KoText(row.countryName, MaterialTheme.typography.titleMedium, color = Tokens.Ink, heading = true)
                    KoText(dates, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary, display = koDisplay(keepMonthDay(dates)))
                }
            }
            if (stacked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountryCircle(row.trip.country, if (dimens.easyMode) 56.dp else 48.dp)
                    Box(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null, tint = Tokens.InkSecondary)
                }
                head(Modifier.fillMaxWidth())
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountryCircle(row.trip.country, if (dimens.easyMode) 56.dp else 48.dp)
                    head(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null, tint = Tokens.InkSecondary)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip(status, Icons.Outlined.DateRange, tone = BadgeTone.Accent)
                InfoChip(stringResource(R.string.trips_row_progress, row.done, row.total), Icons.Outlined.Checklist, tone = BadgeTone.Accent)
                if (row.overlaps) StatusTag(stringResource(R.string.trips_overlap_tag), StatusKind.Caution, icon = Icons.Outlined.EventBusy)
            }
            CheckProgressBar(row.done, row.total)
        }
    }
}

@Composable
private fun tripStatus(row: TripRow, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, row.trip.start).toInt()
    return when {
        row.timing == TripTiming.Past -> stringResource(R.string.trips_status_past)
        row.timing == TripTiming.Ongoing -> stringResource(R.string.trips_status_ongoing)
        days == 0 -> stringResource(R.string.trips_status_today)
        else -> stringResource(R.string.trips_status_days, days)
    }
}

/** 나라 사진 원형 썸네일(장식). 사진이 없으면 남색 원 */
@Composable
internal fun CountryCircle(code: String, size: Dp) {
    val thumb = rememberThumbnail(Photos.country(code), size)
    Box(Modifier.size(size).clip(CircleShape).background(Tokens.Navy).clearAndSetSemantics {}) {
        if (thumb != null) {
            Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, colorFilter = rememberPhotoLift(thumb), modifier = Modifier.matchParentSize())
        }
    }
}

/** 여행 날짜 한 줄: 11월 3일 ~ 7일 (달이 바뀌면 11월 30일 ~ 12월 2일) — 오늘 화면과 같은 문구 */
@Composable
internal fun tripDateRange(start: LocalDate, end: LocalDate): String {
    val from = stringResource(R.string.today_date_md, start.monthValue, start.dayOfMonth)
    val to = if (start.year == end.year && start.month == end.month) {
        stringResource(R.string.today_date_d, end.dayOfMonth)
    } else {
        stringResource(R.string.today_date_md, end.monthValue, end.dayOfMonth)
    }
    return stringResource(R.string.today_trip_dates, from, to)
}

@Composable
internal fun monthDay(date: LocalDate): String = stringResource(R.string.today_date_md, date.monthValue, date.dayOfMonth)

// =====================================================================================
// 여행 체크리스트 (한 여행)
// =====================================================================================

data class ChecklistUi(
    val loaded: Boolean = false,
    val trip: Trip? = null,
    val countryName: String? = null,
    val data: ChecklistData = ChecklistData(),
    val today: LocalDate = LocalDate.now(),
    val overlaps: Boolean = false,
)

/** 체크리스트 화면에서 다른 곳으로 가는 길·하는 일 */
data class ChecklistActions(
    val toggle: (ChecklistItem, Boolean) -> Unit = { _, _ -> },
    val addCustom: (String) -> Unit = {},
    val editCustom: (String, String) -> Unit = { _, _ -> },
    val removeCustom: (String) -> Unit = {},
    val openPassport: () -> Unit = {},
    val openWallet: () -> Unit = {},
    val openForm: (String) -> Unit = {},
    val openHelp: () -> Unit = {},
    val openPresent: () -> Unit = {},
    val openTransport: () -> Unit = {},
    val openShopping: (String) -> Unit = {},
    val openEssentials: () -> Unit = {},
    val openLink: (String) -> Unit = {},
    val destroyPassport: () -> Unit = {},
    val editTrip: (String) -> Unit = {},
    val deleteTrip: () -> Unit = {},
    /** 나라 화면 `공항에 도착하면` 묶음 (나라, 처음 고를 공항) */
    val openAirport: (String, String?) -> Unit = { _, _ -> },
)

@HiltViewModel
class ChecklistViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val trips: TripRepository,
    private val checklists: ChecklistProvider,
    private val wallet: WalletRepository,
    private val packs: PackRepository,
) : ViewModel() {
    private val tripId = MutableStateFlow<String?>(null)

    fun load(id: String) {
        tripId.value = id
    }

    val ui: StateFlow<ChecklistUi> = combine(trips.book, tripId) { b, id ->
        val trip = b.trips.firstOrNull { it.id == id } ?: return@combine ChecklistUi(loaded = id != null)
        val today = LocalDate.now()
        ChecklistUi(
            loaded = true,
            trip = trip,
            countryName = checklists.countryName(trip.country),
            data = checklists.build(trip, b, today),
            today = today,
            overlaps = trip.id in TripSelection.overlapping(b.trips),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChecklistUi())

    fun toggle(item: ChecklistItem, checked: Boolean) = viewModelScope.launch {
        val id = tripId.value ?: return@launch
        trips.setMark(id, item.id, markFor(item, checked))
    }

    fun addCustom(text: String) = viewModelScope.launch { tripId.value?.let { trips.addCustom(it, text) } }

    fun editCustom(itemId: String, text: String) = viewModelScope.launch { tripId.value?.let { trips.editCustom(it, itemId, text) } }

    fun removeCustom(itemId: String) = viewModelScope.launch { tripId.value?.let { trips.removeCustom(it, itemId) } }

    /** 여권 정보만 지운다(오늘 화면 귀국 단계와 같은 동작). 지갑이 잠겨 있으면 먼저 연다 */
    suspend fun destroyPassportInfo(): WalletRepository.SaveResult {
        if (wallet.state.value !is WalletState.Unlocked) wallet.unlock()
        val r = wallet.update { it.withoutPassportInfo() }
        if (r == WalletRepository.SaveResult.Saved) tripId.value?.let { id -> trips.update(id) { it.copy(wrappedUp = true) } }
        return r
    }

    fun deleteTrip() = viewModelScope.launch {
        val id = tripId.value ?: return@launch
        trips.delete(id)
        rescheduleFormReminder(context, trips, packs)
    }
}

@Composable
fun TripChecklistScreen(
    tripId: String,
    actions: ChecklistActions,
    onDeleted: () -> Unit,
    viewModel: ChecklistViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(tripId) { viewModel.load(tripId) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = rememberDeviceAuth()
    if (!ui.loaded) return
    if (ui.trip == null) {
        // 지운 여행 — 목록으로
        androidx.compose.runtime.LaunchedEffect(Unit) { onDeleted() }
        return
    }
    TripChecklistContent(
        ui = ui,
        actions = actions.copy(
            toggle = viewModel::toggle,
            addCustom = { viewModel.addCustom(it) },
            editCustom = { id, t -> viewModel.editCustom(id, t) },
            removeCustom = { viewModel.removeCustom(it) },
            openLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
            destroyPassport = {
                scope.launch {
                    if (viewModel.destroyPassportInfo() != WalletRepository.SaveResult.Saved) {
                        auth { scope.launch { viewModel.destroyPassportInfo() } }
                    }
                }
            },
            deleteTrip = { viewModel.deleteTrip(); onDeleted() },
        ),
    )
}

/**
 * 한 여행의 체크리스트 (2026-10-02 운영자 요청 — 여권 만료 기간부터 돌아와서 할 일까지 빠짐없이).
 * 위에서부터: 나라 사진 머리(전체 `12 / 30` + 막대 + 지금 단계) → 단계 카드 여덟(떠나기 한 달 전쯤 … 돌아와서, 단계마다 `3 / 7` 막대)
 * → 내가 넣은 항목 → 기기 안 저장 한 줄 → 여행 고치기 · 이 여행 지우기.
 * - 항목 표시: 앱이 확인한 항목 = `앱이 확인했어요`(체크 아이콘 태그, 사람이 바꿀 수 있음) · 팩 사실 = 출처·확인일 · 내 항목 = `내 항목`.
 * - 이미 지난 단계를 다 했으면 그 단계 카드는 머리만 보이고 `한 일 7개 보기`로 펼친다(화면이 길어지지 않게 — 내용은 숨기지 않고 접기만).
 * - 늦은 항목은 주의 태그(`지금 해 두세요`), 빨강은 출발 당일 입국 카드(`오늘 꼭 내요`)에만.
 */
@Composable
fun TripChecklistContent(ui: ChecklistUi, actions: ChecklistActions) {
    val trip = ui.trip ?: return
    val country = ui.countryName ?: trip.country
    val data = ui.data
    val current = Checklist.currentPhase(trip, ui.today)
    val nights = ChronoUnit.DAYS.between(trip.start, trip.end).toInt()
    val dates = tripDateRange(trip.start, trip.end)
    val subtitle = stringResource(R.string.ck_progress_title, dates, stringResource(R.string.trip_nights, nights, nights + 1))
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDestroy by remember { mutableStateOf(false) }
    AppScreen(
        title = stringResource(R.string.ck_title, country),
        subtitle = subtitle,
        speech = stringResource(R.string.ck_speech, country, data.total, data.done),
        icon = Icons.Outlined.Checklist,
    ) {
        item(key = "hero") { ChecklistHero(trip.country, data, phaseName(current)) }
        if (ui.overlaps) {
            item(key = "overlap") { NoticeBanner(stringResource(R.string.trips_overlap_note), icon = Icons.Outlined.EventBusy) }
        }
        data.phases.forEach { phase ->
            item(key = "phase-${phase.key}") {
                PhaseCard(
                    phase, data.phase(phase), trip, ui.today, now = phase == current, actions = actions, onDestroy = { confirmDestroy = true },
                    currentDone = data.phase(current).all { it.checked },
                )
            }
        }
        sectionGap("custom-gap")
        item(key = "custom") { CustomCard(data.custom, actions) }
        item(key = "local") { IconBullet(stringResource(R.string.ck_saved_local), Icons.Outlined.Lock) }
        item(key = "edit") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                QuietButton(stringResource(R.string.trip_edit_title), onClick = { actions.editTrip(trip.id) }, icon = Icons.Outlined.EditCalendar)
            }
        }
        item(key = "delete") {
            DangerButton(stringResource(R.string.trip_delete), onClick = { confirmDelete = true }, placement = ButtonPlacement.CardAction)
        }
    }
    if (confirmDelete) {
        DestructiveConfirm(
            title = stringResource(R.string.trip_delete_confirm_title),
            body = stringResource(R.string.ck_delete_body),
            confirmLabel = stringResource(R.string.trip_delete_confirm),
            onConfirm = { confirmDelete = false; actions.deleteTrip() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmDestroy) {
        DestructiveConfirm(
            title = stringResource(R.string.today_destroy_title),
            body = stringResource(R.string.today_destroy_body),
            confirmLabel = stringResource(R.string.today_destroy_now_target),
            onConfirm = { confirmDestroy = false; actions.destroyPassport() },
            onDismiss = { confirmDestroy = false },
        )
    }
}

@Composable
internal fun phaseName(phase: ChecklistPhase): String = stringResource(
    when (phase) {
        ChecklistPhase.Month -> R.string.ck_phase_month
        ChecklistPhase.Week -> R.string.ck_phase_week
        ChecklistPhase.ThreeDays -> R.string.ck_phase_three_days
        ChecklistPhase.DepartureDay -> R.string.ck_phase_departure_day
        ChecklistPhase.Arrival -> R.string.ck_phase_arrival
        ChecklistPhase.During -> R.string.ck_phase_during
        ChecklistPhase.BeforeReturn -> R.string.ck_phase_before_return
        ChecklistPhase.Back -> R.string.ck_phase_back
    },
)

/** 단계 언제: `출발 7일 전부터 · 10월 30일까지` */
@Composable
internal fun phaseHint(phase: ChecklistPhase, trip: Trip): String {
    val due = monthDay(Checklist.dueBy(phase, trip))
    return when (phase) {
        ChecklistPhase.Month -> stringResource(R.string.ck_hint_month, due)
        ChecklistPhase.Week -> stringResource(R.string.ck_hint_week, due)
        ChecklistPhase.ThreeDays -> stringResource(R.string.ck_hint_three_days, due)
        ChecklistPhase.DepartureDay -> stringResource(R.string.ck_hint_departure_day, due)
        ChecklistPhase.Arrival -> stringResource(R.string.ck_hint_arrival, due)
        ChecklistPhase.During -> stringResource(R.string.ck_hint_during, due)
        ChecklistPhase.BeforeReturn -> stringResource(R.string.ck_hint_before_return, due)
        ChecklistPhase.Back -> stringResource(R.string.ck_hint_back, monthDay(trip.end))
    }
}

/**
 * 체크리스트 사진 머리(섹션 표지 — 나라 사진): 스크림 영역 안에 큰 숫자 `12 / 30` + 문장 + 막대 + 지금 단계 칩.
 * 큰 숫자는 보는 사람용 — TalkBack은 문장(`모두 30개 중 12개 했어요`)을 읽는다. 쉬운 모드·큰 글자는 숫자 아래에 문장.
 */
@Composable
private fun ChecklistHero(country: String, data: ChecklistData, nowPhase: String) {
    val dimens = LocalDimens.current
    val extras = LocalTypeExtras.current
    val stacked = isStackedLayout() || dimens.easyMode
    val sentence = stringResource(R.string.ck_progress_sentence, data.total, data.done)
    PhotoBox(Photos.country(country), minHeight = 120.dp) {
        PhotoTextArea {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Checklist, contentDescription = null, tint = OnDark.content, modifier = Modifier.size(dimens.icon))
                Text(
                    stringResource(R.string.essentials_progress_stat, data.done, data.total),
                    style = extras.stat,
                    color = OnDark.content,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                if (!stacked) KoText(sentence, MaterialTheme.typography.bodyMedium, Modifier.weight(1f), color = OnDark.content, glueShort = true)
            }
            if (stacked) KoText(sentence, MaterialTheme.typography.bodyLarge, color = OnDark.content, glueShort = true)
            CheckProgressBar(data.done, data.total, Modifier.padding(top = 4.dp), onDark = true)
            InfoChip(nowPhase, IconKeys.essentials, value = stringResource(R.string.ck_phase_now), onDark = true, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PhaseCard(
    phase: ChecklistPhase,
    items: List<ChecklistItem>,
    trip: Trip,
    today: LocalDate,
    now: Boolean,
    actions: ChecklistActions,
    onDestroy: () -> Unit,
    /** 지금 단계 항목을 다 했는지(그러면 다음 단계를 펼친다) */
    currentDone: Boolean = false,
) {
    val name = phaseName(phase)
    val done = items.count { it.checked }
    val total = items.size
    val allDone = total > 0 && done == total
    // 펼쳐 둘 단계: 지금 단계와 그 앞의 아직 안 끝난 단계(늦은 일이 묻히지 않게).
    // 다 끝난 지난 단계(`한 일 7개 보기`)와 뒤 단계(`항목 3개 보기`)는 머리·진행만 보이고 그 자리에서 펼친다 — 숨기지 않고 접기만.
    // 지금 단계를 다 했으면 바로 다음 단계를 펼쳐 미리 할 수 있게 한다
    val current = Checklist.currentPhase(trip, today)
    val next = ChecklistPhase.entries.getOrNull(current.ordinal + 1)
    val foldable = (allDone && phase < current) || (phase > current && !(phase == next && currentDone))
    var open by rememberSaveable(phase.key, foldable) { mutableStateOf(!foldable) }
    ChecklistPhaseCard(
        title = name,
        hint = phaseHint(phase, trip),
        icon = IconKeys.checklistPhase(phase.barIndex),
        done = done,
        total = total,
        now = now,
        nowLabel = stringResource(R.string.ck_phase_now),
        headerDescription = stringResource(R.string.ck_phase_cd, name, total, done),
    ) {
        if (foldable) {
            ExpandToggle(
                open = open,
                onOpenChange = { open = it },
                label = if (allDone) stringResource(R.string.ck_phase_show, done) else stringResource(R.string.ck_phase_show_items, total),
                target = stringResource(R.string.ck_phase_target, name),
            )
        }
        if (open) {
            items.forEachIndexed { i, item ->
                if (i > 0) ChecklistDivider()
                ItemRow(item, trip, today, actions, onDestroy)
            }
            if (phase == ChecklistPhase.Week && items.any { it.detail is ItemDetail.Essential }) {
                // 같은 앱 안 화면으로 — 바깥 링크 모양(LinkRow) 대신 글자 버튼
                Box(Modifier.fillMaxWidth()) {
                    QuietButton(stringResource(R.string.ck_essentials_link), onClick = actions.openEssentials, icon = IconKeys.essentials)
                }
            }
        }
    }
}

/** 항목 한 줄: 공용 [ChecklistRow] + 항목별 설명·버튼·출처 */
@Composable
internal fun ItemRow(
    item: ChecklistItem,
    trip: Trip,
    today: LocalDate,
    actions: ChecklistActions,
    onDestroy: () -> Unit = {},
    compact: Boolean = false,
) {
    val locked = item.locked(today)
    val openDate = item.opensOn?.let { monthDay(it) }
    val state = when {
        locked && openDate != null -> stringResource(R.string.ck_opens_on, openDate)
        item.checked -> stringResource(R.string.ck_state_done)
        else -> stringResource(R.string.ck_state_todo)
    }
    val extra: (@Composable ColumnScope.() -> Unit)? = if (compact || item.checked) null else ({ ItemExtra(item, trip, today, actions, onDestroy) })
    val emphasis = when {
        item.urgent -> CheckEmphasis.Urgent
        item.overdue -> CheckEmphasis.Overdue
        else -> CheckEmphasis.Normal
    }
    ChecklistRow(
        title = item.title,
        icon = IconKeys.checklist(item.icon),
        checked = item.checked,
        onCheckedChange = { actions.toggle(item, it) },
        stateText = state,
        enabled = !locked,
        emphasis = emphasis,
        tags = itemTags(item, locked, openDate, compact),
        // 한 일은 제목·태그만 남기고 설명·버튼·출처를 접는다(화면이 짧아지고 '끝난 것'이 한눈에) — 체크를 풀면 다시 다 보인다
        body = if (compact || item.checked) null else itemBody(item),
        extra = extra,
    )
}

/** 머리 줄 태그: 급함·늦음·열리는 날·앱이 확인·내 항목 (하나 이상일 때만) */
@Composable
private fun itemTags(item: ChecklistItem, locked: Boolean, openDate: String?, compact: Boolean): (@Composable () -> Unit)? {
    val urgent = stringResource(R.string.ck_urgent)
    val overdue = stringResource(R.string.ck_overdue)
    val opens = openDate?.let { stringResource(R.string.ck_opens_on, it) }
    val autoDone = stringResource(R.string.ck_auto_marker)
    val override = stringResource(R.string.ck_auto_override)
    val custom = stringResource(R.string.ck_custom_kind)
    val phase = item.phase?.let { phaseName(it) }
    val tags = buildList<@Composable () -> Unit> {
        if (item.urgent) add { StatusTag(urgent, StatusKind.Required) }
        else if (item.overdue) add { StatusTag(overdue, StatusKind.Caution) }
        if (locked && opens != null) add { StatusTag(opens, StatusKind.Soon, display = koDisplay(keepMonthDay(opens), glueShort = true)) }
        if (item.kind == ItemKind.Auto && item.auto == AutoState.Done && !item.overridden) add { StatusTag(autoDone, StatusKind.Verified) }
        if (item.overridden) add { StatusTag(override, StatusKind.Self) }
        if (item.kind == ItemKind.Custom) add { StatusTag(custom, StatusKind.Self, icon = Icons.Outlined.EditNote) }
        if (compact && phase != null && !item.urgent && !item.overdue && !locked) add { StatusTag(phase, StatusKind.Info) }
    }
    if (tags.isEmpty()) return null
    return { tags.forEach { it() } }
}

/** 설명 글: 여권 항목은 결과 문장, 입국 카드는 내 날짜로 바꾼 기간 문장, 나머지는 틀·팩 문장 */
@Composable
private fun itemBody(item: ChecklistItem): String? = when (val d = item.detail) {
    is ItemDetail.Passport -> passportSentence(d)
    is ItemDetail.Form -> item.body?.let { personalWindowKo(it, d.windowTo?.let { to -> d.windowFrom?.let { from -> (ChronoUnit.DAYS.between(from, to) + 1).toInt() } }, d.windowTo) }
    is ItemDetail.Destroy -> if (d.hasPassport == false) stringResource(R.string.ck_destroy_none) else item.body
    else -> item.body
}

@Composable
private fun passportSentence(d: ItemDetail.Passport): String {
    val basis = d.check?.basis ?: d.rule?.basis
    val basisName = stringResource(
        when (basis) {
            "departure" -> R.string.ck_pp_basis_departure
            "stay_end" -> R.string.ck_pp_basis_stay_end
            else -> R.string.ck_pp_basis_arrival
        },
    )
    val months = d.check?.months ?: d.rule?.months
    val check = d.check
    return when {
        d.saved == false -> stringResource(R.string.ck_pp_no_passport)
        check == null && months != null -> stringResource(R.string.ck_pp_need, basisName, months) + " " + stringResource(R.string.ck_pp_locked)
        check == null -> stringResource(R.string.ck_pp_unknown_rule) + " " + stringResource(R.string.ck_pp_locked)
        check.status == PassportStatus.Ok && months != null -> stringResource(R.string.ck_pp_need, basisName, months) + " " + stringResource(R.string.ck_pp_ok)
        check.status == PassportStatus.Short && check.months != null -> stringResource(R.string.ck_pp_short, basisName, check.months)
        check.status == PassportStatus.Short -> stringResource(R.string.ck_pp_short_trip)
        else -> stringResource(R.string.ck_pp_unknown_rule_ok)
    }
}

/** 항목별 아래 요소: 값 칩·버튼·링크·출처 (출처는 언제나 맨 아래) */
@Composable
private fun ItemExtra(item: ChecklistItem, trip: Trip, today: LocalDate, actions: ChecklistActions, onDestroy: () -> Unit) {
    val fallback = stringResource(R.string.source_official_fallback)
    when (val d = item.detail) {
        is ItemDetail.Passport -> {
            val check = d.check
            when {
                d.saved == false -> SecondaryButton(stringResource(R.string.wallet_passport_add), onClick = actions.openPassport, icon = Icons.Outlined.Badge)
                check == null -> SecondaryButton(stringResource(R.string.ck_pp_open_wallet), onClick = actions.openWallet, icon = Icons.Outlined.AccountBalanceWallet)
                check.status == PassportStatus.Short && d.reissue != null ->
                    LinkRow(stringResource(R.string.ck_pp_reissue), onClick = { actions.openLink(d.reissue.url) }, icon = Icons.AutoMirrored.Outlined.OpenInNew)
                check.status == PassportStatus.Unknown && d.official != null ->
                    LinkRow(stringResource(R.string.ck_official_open), onClick = { actions.openLink(d.official.url) }, icon = Icons.AutoMirrored.Outlined.OpenInNew)
            }
            if (d.rule == null && check == null && d.saved != false && d.official != null) {
                LinkRow(stringResource(R.string.ck_official_open), onClick = { actions.openLink(d.official.url) }, icon = Icons.AutoMirrored.Outlined.OpenInNew)
            }
            KoText(stringResource(R.string.ck_pp_privacy), MaterialTheme.typography.bodySmall, color = Tokens.InkTertiary)
        }
        is ItemDetail.Form -> {
            if (d.windowFrom != null && d.windowTo != null && !item.locked(today)) {
                InfoChip(stringResource(R.string.today_form_window_label), Icons.Outlined.EventAvailable, value = keepMonthDay(formWindowKo(d.windowFrom, d.windowTo)), tone = BadgeTone.Accent)
            }
            SecondaryButton(stringResource(R.string.prepare_form_open), onClick = { actions.openForm(d.formId) }, icon = Icons.Outlined.EditNote)
        }
        is ItemDetail.Visa -> {
            val apply = d.apply
            val url = apply?.officialUrl
            if (apply != null && url != null) {
                SecondaryButton(stringResource(R.string.ck_visa_apply, apply.nameKo), onClick = { actions.openLink(url) }, icon = Icons.AutoMirrored.Outlined.OpenInNew)
            }
        }
        is ItemDetail.Phone -> {
            // 번호는 칸 폭에 맞춰 한 줄(도움 화면 긴급 번호와 같은 FitText) — 200%에서도 번호 가운데서 줄이 바뀌지 않게
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Call, contentDescription = null, tint = Tokens.Help, modifier = Modifier.padding(top = 4.dp).size(LocalDimens.current.icon))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PhoneNumberText(d.number, Tokens.Ink, styles = listOf(MaterialTheme.typography.titleLarge, MaterialTheme.typography.titleMedium, MaterialTheme.typography.bodyLarge))
                    KoText(d.label, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                }
            }
        }
        is ItemDetail.Essential -> {
            if (d.rule.ruleBadge == "carry_on_only") StatusTag(stringResource(R.string.essentials_badge_carry_on), StatusKind.Caution)
            d.rule.link?.let { link ->
                SecondaryButton(link.labelKo, onClick = { actions.openLink(link.url) }, icon = Icons.AutoMirrored.Outlined.OpenInNew)
            }
        }
        is ItemDetail.Return -> {
            if (d.facts.size > 1) {
                ExpandableDetail(label = stringResource(R.string.ck_return_more), target = stringResource(R.string.ck_return_target)) {
                    d.facts.drop(1).forEach { (text, _) -> DotBullet(text) }
                }
            }
        }
        is ItemDetail.Destroy -> {
            if (d.hasPassport != false && !item.locked(today) && !item.checked) {
                DangerButton(stringResource(R.string.today_destroy_now_target), onClick = onDestroy, placement = ButtonPlacement.ItemAction)
            }
        }
        is ItemDetail.Offline -> d.packVersion?.let { InfoChip(stringResource(R.string.ck_offline_value, displayDate(it)), Icons.Outlined.OfflinePin, tone = BadgeTone.Teal) }
        is ItemDetail.AirportGuide -> {
            // 고른 공항이 있으면 값 칩, 없으면 고르는 곳 안내(여행 고치기) — 공항 순서는 나라 화면 공항 묶음에서
            if (d.name != null) {
                InfoChip(stringResource(R.string.ck_airport_chosen), Icons.Outlined.LocalAirport, value = d.name, tone = BadgeTone.Accent)
            } else {
                KoText(stringResource(R.string.ck_airport_not_chosen), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
            }
            SecondaryButton(stringResource(R.string.ck_action_airport), onClick = { actions.openAirport(d.country, d.code) }, icon = Icons.Outlined.LocalAirport)
        }
        null -> Unit
    }
    // 다른 화면으로 가는 버튼(입국 카드·여권은 위에서 이미)
    when (item.action) {
        ChecklistAction.OpenHelp -> SecondaryButton(stringResource(R.string.ck_action_help), onClick = actions.openHelp, icon = Icons.Outlined.Translate)
        ChecklistAction.OpenPresent -> SecondaryButton(stringResource(R.string.ck_action_present), onClick = actions.openPresent, icon = Icons.Outlined.QrCode2)
        ChecklistAction.OpenTransport -> SecondaryButton(stringResource(R.string.ck_action_transport), onClick = actions.openTransport, icon = Icons.Outlined.Home)
        ChecklistAction.OpenShopping -> SecondaryButton(stringResource(R.string.ck_action_shopping), onClick = { actions.openShopping(trip.country) }, icon = Icons.Outlined.ShoppingBag)
        ChecklistAction.OpenLink -> if (item.detail !is ItemDetail.Visa) {
            item.link?.let { link -> SecondaryButton(stringResource(R.string.ck_official_open), onClick = { actions.openLink(link.url) }, icon = Icons.AutoMirrored.Outlined.OpenInNew) }
        }
        ChecklistAction.OpenPassport -> if (item.detail !is ItemDetail.Passport && item.auto != AutoState.Done) {
            SecondaryButton(stringResource(R.string.wallet_passport_add), onClick = actions.openPassport, icon = Icons.Outlined.Badge)
        }
        else -> Unit
    }
    // 출처: 팩·색인 사실이 있는 항목만. 귀국 사실은 문장마다 출처를 모은다
    val refs = when (val d = item.detail) {
        is ItemDetail.Return -> d.facts.map { (_, s) -> SourceRef(s.name ?: fallback, displayDate(s.lastVerified)) }
        else -> listOfNotNull(item.source?.let { SourceRef(it.name ?: fallback, displayDate(it.lastVerified)) })
    }
    when {
        refs.size == 1 -> SourceFooter(refs.single())
        refs.size > 1 -> SourceList(refs)
    }
}

/** 내가 넣은 항목 카드: 항목(체크 + 고치기·지우기) → 더하기 칸 */
@Composable
private fun CustomCard(items: List<ChecklistItem>, actions: ChecklistActions) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.large
    var text by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    val done = items.count { it.checked }
    ChecklistPhaseCard(
        title = stringResource(R.string.ck_custom_title),
        hint = if (items.isEmpty()) stringResource(R.string.ck_custom_empty) else null,
        icon = Icons.Outlined.EditNote,
        done = done,
        total = items.size,
        headerDescription = stringResource(R.string.ck_phase_cd, stringResource(R.string.ck_custom_title), items.size, done),
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) ChecklistDivider()
            if (editing == item.id) {
                CustomField(
                    value = editText,
                    onValueChange = { editText = it },
                    button = stringResource(R.string.ck_custom_save),
                    buttonIcon = Icons.Outlined.Check,
                    onSubmit = {
                        actions.editCustom(item.id, editText)
                        editing = null
                    },
                )
            } else {
                ChecklistRow(
                    title = item.title,
                    icon = IconKeys.checklist("custom"),
                    checked = item.checked,
                    onCheckedChange = { actions.toggle(item, it) },
                    stateText = stringResource(if (item.checked) R.string.ck_state_done else R.string.ck_state_todo),
                    extra = {
                        // 고치기(글자 버튼) · 지우기(빨간 테두리, 끝 정렬). TalkBack 이름에 무엇을 고치고 지우는지 붙인다(R18)
                        val editName = stringResource(R.string.ck_custom_edit_target, item.title)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            QuietButton(
                                stringResource(R.string.ck_custom_edit),
                                onClick = { editing = item.id; editText = item.title },
                                icon = Icons.Outlined.EditNote,
                                modifier = Modifier.semantics { contentDescription = editName },
                            )
                            QuietDangerButton(
                                stringResource(R.string.trip_delete_confirm),
                                onClick = { actions.removeCustom(item.id) },
                                contentDescription = stringResource(R.string.ck_custom_delete_target, item.title),
                            )
                        }
                    },
                )
            }
        }
        if (items.isNotEmpty()) ChecklistDivider()
        CustomField(
            value = text,
            onValueChange = { text = it },
            button = stringResource(R.string.ck_custom_add),
            buttonIcon = Icons.Outlined.Add,
            onSubmit = {
                actions.addCustom(text)
                text = ""
            },
        )
    }
}

/** 내 항목 글 칸 + 버튼(빈 글이면 버튼 꺼짐). 글자 수 한도는 저장소가 지킨다 */
@Composable
private fun CustomField(
    value: String,
    onValueChange: (String) -> Unit,
    button: String,
    buttonIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onSubmit: () -> Unit,
) {
    val dimens = LocalDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.take(TripRepository.CUSTOM_MAX)) },
            label = { KoText(stringResource(R.string.ck_custom_label)) },
            placeholder = { KoText(stringResource(R.string.ck_custom_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onSubmit() }),
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Tokens.Surface,
                unfocusedContainerColor = Tokens.Surface,
                unfocusedBorderColor = Tokens.LineStrong,
                focusedBorderColor = Tokens.Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(button, onClick = onSubmit, icon = buttonIcon, enabled = value.isNotBlank())
    }
}

/**
 * 가장 가까운 다가오는 여행의 입국 카드 알림을 다시 맞춘다(알림 작업은 하나뿐 — 여행이 여럿이면 가장 먼저 떠나는 여행).
 * 기간이 정해진 입국 카드가 없으면 알림을 지운다.
 */
internal suspend fun rescheduleFormReminder(context: android.content.Context, trips: TripRepository, packs: PackRepository) {
    val today = LocalDate.now()
    val next = trips.all().filter { it.datesValid && it.start.isAfter(today.minusDays(1)) }.sortedBy { it.start }.firstNotNullOfOrNull { t ->
        // 꼭 내야 하는 입국 카드만 알림을 만든다 — 의무가 아닌 신고(forms[].optional)로는 기한 알림을 걸지 않는다
        val form = packs.pack(t.country)?.value?.requiredForms?.firstOrNull()
        val days = form?.windowDaysIncludingArrival
        if (form != null && days != null) Triple(t, form, days) else null
    }
    if (next == null) {
        TripNotifications.cancelFormWindow(context)
    } else {
        val (t, form, days) = next
        TripNotifications.scheduleFormWindow(context, t.start.minusDays((days - 1).toLong()), form.nameKo)
    }
}
