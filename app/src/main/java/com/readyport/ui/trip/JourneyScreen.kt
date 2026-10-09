package com.readyport.ui.trip

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.LocalAirport
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.attractions.AdvisoryState
import com.readyport.attractions.AttractionsRepository
import com.readyport.attractions.SavedAttractionsRepository
import com.readyport.data.settings.SettingsRepository
import com.readyport.itinerary.TripItinerary
import com.readyport.pack.Airport
import com.readyport.pack.FormInfo
import com.readyport.pack.OfficialLink
import com.readyport.pack.PackRepository
import com.readyport.pack.ShoppingItem
import com.readyport.pack.SourcedText
import com.readyport.prep.CartKey
import com.readyport.prep.ImportStatus
import com.readyport.prep.import
import com.readyport.stay.StayNote
import com.readyport.stay.Stays
import com.readyport.transport.PlacesRepository
import com.readyport.trip.Checklist
import com.readyport.trip.ChecklistAlerts
import com.readyport.trip.ChecklistData
import com.readyport.trip.ChecklistItem
import com.readyport.trip.ChecklistProvider
import com.readyport.trip.ChecklistReminders
import com.readyport.trip.ItemDetail
import com.readyport.trip.JourneyStage
import com.readyport.trip.StageInfo
import com.readyport.trip.Trip
import com.readyport.trip.TripRepository
import com.readyport.trip.TripSelection
import com.readyport.trip.TripSignalsRecorder
import com.readyport.trip.TripStage
import com.readyport.trip.TripStages
import com.readyport.trip.markFor
import com.readyport.ui.components.AirportCompactCard
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.ButtonPlacement
import com.readyport.ui.components.ButtonStyles
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.CheckProgressBar
import com.readyport.ui.components.ChecklistDivider
import com.readyport.ui.components.StageSectionCard
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.Fact
import com.readyport.ui.components.FactGrid
import com.readyport.ui.components.HelpShortcutRow
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.InfoChip
import com.readyport.ui.components.InfoTileGrid
import com.readyport.ui.components.ImportVerdictNote
import com.readyport.ui.components.JourneyStageHeader
import com.readyport.ui.components.KoText
import com.readyport.ui.components.NavMosaic
import com.readyport.ui.components.NewsStyle
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.PhotoHeaderCard
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.ReturnCheckCard
import com.readyport.ui.components.ReturnCheckMode
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.StatTile
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.Step
import com.readyport.ui.components.StepList
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.TileSpec
import com.readyport.ui.components.airportSources
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.foldLiveRegion
import com.readyport.ui.components.importKind
import com.readyport.ui.components.importLabel
import com.readyport.ui.components.journeyStageBody
import com.readyport.ui.components.journeyStageName
import com.readyport.ui.components.journeyStageStepName
import com.readyport.ui.components.keepWords
import com.readyport.ui.components.keepKeyVisible
import com.readyport.ui.components.rememberKeyIndex
import com.readyport.ui.components.rememberPhotoLift
import com.readyport.ui.components.rememberThumbnail
import com.readyport.ui.components.resolveSourceName
import com.readyport.ui.components.sectionGap
import com.readyport.ui.itinerary.ItineraryModel
import com.readyport.ui.itinerary.ItineraryUi
import com.readyport.ui.itinerary.TodayPlacesCard
import com.readyport.ui.itinerary.itineraryTile
import com.readyport.ui.stay.StayActions
import com.readyport.ui.stay.StayHereCard
import com.readyport.ui.stay.StaysCard
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.wallet.rememberDeviceAuth
import com.readyport.vault.StayRecord
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// =====================================================================================
// 한 여행 화면 (2026-10-03 — 여행 과정 길잡이, DESIGN_SPEC 부록 H)
//
// 운영자 지적: *"여행 일정(생각 → 일정 검토 및 정리 → 각종 예약 → (반)자동 신청 등 처리 → … → 출국/입국 → 여행 → 복귀)
// 등의 과정이 제대로 안내 되고 그 흐름으로 처리되면 좋겠는데 현재 메뉴 구조는 그렇지 않아."*
//
// 예전에는 같은 여행이 **두 화면**에 흩어져 있었다: `오늘`(단계별 카드)과 `여행 체크리스트`(시간별 묶음).
// 이제 **한 화면**이다 — 머리(진행) → 여행 과정 8단계 막대 → 지금 할 일 → 단계마다 카드(항목 + 그 단계에서 하는 일).
// 예전 오늘 화면의 내용(출국 순서·도착 공항·도착했어요·여행 중 타일·귀국 전 확인·여권 지우기·정리 축하)은
// **그 단계 카드 안**으로 들어갔다. 사라진 것은 없다.
// =====================================================================================

/** 한 여행 화면의 값 (예전 `ChecklistUi` + `TodayUi`) */
data class JourneyUi(
    val loaded: Boolean = false,
    val trip: Trip? = null,
    val countryName: String? = null,
    val data: ChecklistData = ChecklistData(),
    val today: LocalDate = LocalDate.now(),
    val overlaps: Boolean = false,
    /** 챙길 일 알림이 켜져 있는지 (설정) */
    val alertsOn: Boolean = true,
    /** 알려 줄 시각 (설정) */
    val alertHour: Int = ChecklistReminders.DEFAULT_HOUR,
    /** 이 여행만 조용히 두었는지 */
    val muted: Boolean = false,
    /** 지금 시(다음 알림이 오늘인지 내일인지 보여 주려고) */
    val nowHour: Int = java.time.LocalTime.now().hour,
    /** 날짜·도착으로 정해지는 상태 (도착 모드·입국 카드 기간·여권 파기 물어볼 때) */
    val stage: StageInfo = StageInfo(TripStage.NoTrip),
    val form: FormInfo? = null,
    /** 쇼핑 리스트에 담은 물건 — 복귀 단계에서 반입 여부를 다시 보여 준다 (PRD 11.3) */
    val cart: List<ShoppingItem> = emptyList(),
    val returnLinks: List<OfficialLink> = emptyList(),
    val returnFacts: List<SourcedText> = emptyList(),
    val indexSources: Map<String, String> = emptyMap(),
    val sourceNames: Map<String, String> = emptyMap(),
    /** 이 여행의 도착 공항 안내(고른 공항, 팩에 공항이 하나뿐이면 그 공항). 없으면 공항 카드 없음 */
    val airport: Airport? = null,
    val hasAirports: Boolean = false,
    /** 이 나라 팩에 쇼핑 품목이 있는지 (여행 중 타일) */
    val hasShopping: Boolean = false,
    /** 이 여행에서 산 물건을 담았는지 */
    val essentialsTotal: Int = 0,
    val essentialsDone: Int = 0,
    /** 이 여행 묵는 곳 — 날짜 순 (2026-10-03). 보관함이 잠겨 있으면 비어 있다 */
    val stays: List<StayRecord> = emptyList(),
    /** 빈 날·겹침 같은 부드러운 알림 (막지 않는다) */
    val stayNotes: List<StayNote> = emptyList(),
    /**
     * 이 여행의 관광 일정(2026-10-09) — 계획 단계 `관광 일정` 타일과 여행 중 `오늘 갈 곳`.
     * null이면 아직 모름(타일·카드 없음). 이 나라 관광지가 없고 담은 곳도 없으면 타일을 그리지 않는다.
     */
    val itinerary: ItineraryUi? = null,
)

@HiltViewModel
class JourneyViewModel @Inject constructor(
    private val trips: TripRepository,
    private val packs: PackRepository,
    private val checklists: ChecklistProvider,
    private val wallet: WalletRepository,
    private val settings: SettingsRepository,
    private val alerts: ChecklistAlerts,
    private val places: PlacesRepository,
    private val attractions: AttractionsRepository,
    private val savedAttractions: SavedAttractionsRepository,
) : ViewModel() {
    private val tripId = MutableStateFlow<String?>(null)

    fun load(id: String) {
        tripId.value = id
    }

    /** 날짜·도착 모드 시간이 바뀌므로 1분마다 다시 계산 (예전 오늘 화면과 같은 박자) */
    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(60_000) } }

    /**
     * 관광 일정 요약(타일·오늘 갈 곳). 관광지 파일은 여행 나라 것만 읽고, 일정·찜은 기기 안 저장소에서.
     * 묵는 곳 좌표는 여기서 쓰지 않는다(제안은 관광 일정 화면에서만).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val itinerary: Flow<ItineraryUi?> = combine(trips.book, tripId) { b, id -> b.trips.firstOrNull { it.id == id }?.country }
        .distinctUntilChanged()
        .flatMapLatest { cc ->
            if (cc == null) {
                flowOf(null)
            } else {
                combine(attractions.revisionOf(cc), packs.revision, trips.book, savedAttractions.saved, ticker) { _, _, book, items, _ ->
                    val trip = book.trips.firstOrNull { it.id == tripId.value && it.country == cc } ?: return@combine null
                    val catalog = attractions.catalog(cc)
                    val advisory = catalog?.let { AdvisoryState.evaluate(it, packs.pack(cc)?.value) } ?: AdvisoryState.Normal
                    ItineraryModel.build(trip, book.itineraries[trip.id] ?: TripItinerary(), catalog, advisory, items, LocalDate.now())
                }
            }
        }

    val ui: StateFlow<JourneyUi> = combine(
        trips.book, tripId, settings.settings, wallet.state, combine(ticker, packs.revision) { now, _ -> now },
    ) { book, id, s, w, now ->
        val trip = book.trips.firstOrNull { it.id == id } ?: return@combine JourneyUi(loaded = id != null)
        val today = LocalDate.now()
        val pack = packs.pack(trip.country)?.value
        val index = packs.index()?.value
        // 꼭 내야 하는 입국 카드만 단계·알림에 쓴다 — 의무가 아닌 신고(forms[].optional)는 기한을 만들지 않는다
        val form = pack?.requiredForms?.firstOrNull()
        val contents = (w as? WalletState.Unlocked)?.contents
        val submitted = contents?.let { TripSignalsRecorder.formSubmitted(it, trip, pack) } ?: book.checks[trip.id]?.formSubmitted ?: false
        val data = checklists.build(trip, book, today)
        val essentials = data.items.filter { it.id.startsWith(Checklist.essentialItemId("")) }
        JourneyUi(
            loaded = true,
            trip = trip,
            countryName = checklists.countryName(trip.country),
            data = data,
            today = today,
            overlaps = trip.id in TripSelection.overlapping(book.trips),
            alertsOn = s.alertsOn,
            alertHour = ChecklistReminders.hourOrDefault(s.alertHour),
            muted = trip.id in s.alertMutedTrips,
            stage = TripStages.compute(trip, today, now, form?.windowDaysIncludingArrival, submitted),
            form = form,
            cart = pack?.shopping.orEmpty().filter { CartKey.of(trip.country, it.id) in s.cart },
            returnLinks = index?.returnLinks.orEmpty(),
            returnFacts = index?.returnFacts.orEmpty(),
            indexSources = index?.sources.orEmpty().associate { it.id to it.name },
            sourceNames = pack?.sources.orEmpty().associate { it.id to it.name },
            airport = pack?.airport(trip.arrivalAirport) ?: pack?.airports?.singleOrNull(),
            hasAirports = pack?.airports?.isNotEmpty() == true,
            hasShopping = pack?.shopping?.isNotEmpty() == true,
            essentialsTotal = essentials.size,
            essentialsDone = essentials.count { it.checked },
            stays = contents?.let { Stays.forTrip(it.stays, trip) }.orEmpty(),
            stayNotes = contents?.let { Stays.notes(it.stays, trip) }.orEmpty(),
        )
    }.combine(itinerary) { u, plan -> if (u.trip != null) u.copy(itinerary = plan) else u }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JourneyUi())

    /**
     * 기사님께 보여 주기: 그 숙소를 '가는 곳'으로 맞춰 두고 고른다(이동하기 화면이 바로 그 주소를 보여 준다).
     * 예전 예약 서류에서 옮겨 온 숙소도 여기서 가는 곳이 만들어진다.
     */
    fun showStayToDriver(stayId: String) = viewModelScope.launch {
        val stay = (wallet.state.value as? WalletState.Unlocked)?.contents?.stays?.firstOrNull { it.id == stayId } ?: return@launch
        val place = Stays.place(stay) ?: return@launch
        places.syncStay(stay.id, place)
        places.select(stay.id)
    }

    /** 이 여행만 조용히 두기 (설정이 바뀌면 ChecklistAlerts가 작업을 다시 맞춘다) */
    fun setMuted(muted: Boolean) = viewModelScope.launch {
        tripId.value?.let { settings.setTripAlertMuted(it, muted) }
    }

    fun toggle(item: ChecklistItem, checked: Boolean) = viewModelScope.launch {
        val id = tripId.value ?: return@launch
        trips.setMark(id, item.id, markFor(item, checked))
    }

    fun addCustom(text: String) = viewModelScope.launch { tripId.value?.let { trips.addCustom(it, text) } }

    fun editCustom(itemId: String, text: String) = viewModelScope.launch { tripId.value?.let { trips.editCustom(it, itemId, text) } }

    fun removeCustom(itemId: String) = viewModelScope.launch { tripId.value?.let { trips.removeCustom(it, itemId) } }

    private fun update(transform: (Trip) -> Trip) = viewModelScope.launch {
        tripId.value?.let { trips.update(it, transform) }
    }

    fun markArrived() = update { it.copy(arrivedAt = System.currentTimeMillis(), arrivalDismissed = false) }

    /** '도착했어요'를 잘못 눌렀을 때 되돌리기 (재검토 R18) */
    fun undoArrived() = update { it.copy(arrivedAt = null, arrivalDismissed = false) }

    fun dismissArrival() = update { it.copy(arrivalDismissed = true) }

    fun postponeDestroy() = update { it.copy(destroyPostponedUntil = LocalDate.now().plusDays(7).toString()) }

    /** 여권 정보만 지운다 (PRD 4.2 ⑧). 지갑이 잠겨 있으면 먼저 연다 */
    suspend fun destroyPassportInfo(): WalletRepository.SaveResult {
        if (wallet.state.value !is WalletState.Unlocked) wallet.unlock()
        val r = wallet.update { it.withoutPassportInfo() }
        if (r == WalletRepository.SaveResult.Saved) tripId.value?.let { id -> trips.update(id) { it.copy(wrappedUp = true) } }
        return r
    }

    fun deleteTrip() = viewModelScope.launch {
        val id = tripId.value ?: return@launch
        trips.delete(id)
        // 지운 여행의 알림·작업을 바로 치운다(나머지는 ChecklistAlerts가 여행 장부가 바뀐 것을 보고 다시 맞춘다)
        alerts.forget(id)
    }
}

@Composable
fun TripJourneyScreen(
    tripId: String,
    actions: ChecklistActions,
    onDeleted: () -> Unit,
    viewModel: JourneyViewModel = hiltViewModel(),
) {
    LaunchedEffect(tripId) { viewModel.load(tripId) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = rememberDeviceAuth()
    if (!ui.loaded) return
    if (ui.trip == null) {
        // 지운 여행 — 목록으로
        LaunchedEffect(Unit) { onDeleted() }
        return
    }
    TripJourneyContent(
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
            setMuted = { viewModel.setMuted(it) },
            arrived = viewModel::markArrived,
            undoArrived = viewModel::undoArrived,
            arrivalDone = viewModel::dismissArrival,
            postponeDestroy = viewModel::postponeDestroy,
            // 기사님께 보여 주기: 가는 곳으로 맞춰 둔 뒤 이동하기 화면으로 (같은 호텔을 두 번 적지 않는다)
            showStayToDriver = { id ->
                viewModel.showStayToDriver(id)
                actions.openTransport()
            },
        ),
    )
}

/**
 * 한 여행 화면 — 이 여행의 모든 것이 여기서 닿는다.
 * 위에서부터: 나라 사진 머리(`12 / 30` + 막대 + 지금 단계) → 지금 할 일(가장 급한 일 한 가지 + 그 단계를 펼치는 버튼)
 * → 알림 한 줄 → **번호 붙은 단계 카드 여덟**(접혔다 펴지는 아코디언) → 내가 넣은 항목 → 급할 때는 도움
 * → 기기 안 저장 → 이 여행 지우기.
 * - 묶는 축은 **단계**(무엇을 하는 일), 늦음·알림은 **기한 축**(언제까지)이 따로 맡는다.
 *
 * **아코디언** (운영자 2026-10-03, 부록 H.7):
 * *"내 여행에서 계획, 예약, 등을 클릭하면 하단으로 이동한 뒤 상단으로 바로 이동할 수 있는 방법이 없어.
 * 따라서 각 단계를 클릭하면 접혔다가 펴지는 형태로 해줘. 다른 단계를 클릭하면 펼쳐져있던 기존 내용이 모두 접히도록 해줘."*
 * - 한 번에 **한 단계만** 펼쳐진다. 누른 단계가 그 자리에서 펴지고 나머지는 모두 접힌다. 열린 단계를 다시 누르면 접힌다(모두 접힘).
 * - 처음 열 때 펼쳐져 있는 단계는 **지금 단계**다. 고른 단계는 rememberSaveable로 화면이 다시 만들어져도(회전·프로세스 종료) 그대로다.
 * - **예전 단계 막대(4칸 두 줄 그림 격자)는 지웠다**: 막대를 누르면 긴 스크롤로 아래 카드까지 내려가고 돌아오는 길이 없었다 —
 *   운영자가 말한 바로 그 문제다. 막대가 들고 있던 그림·진행·`지금`은 카드 머리로 들어갔고, 아코디언이 '고르기'를 맡는다.
 * - 접힌 단계도 머리에 **번호·이름·언제까지·`3 / 7`·막대**가 남는다 — 숨기지 않고 접기만 한다. 여덟 머리가 모두 짧아
 *   어느 단계를 펼쳐도 조금만 올리면 단계 목록 전체가 다시 보인다.
 *
 * @param openAtFirst 처음 펼쳐 둘 단계의 key. null이면 **지금 단계**(운영 기본값), [JOURNEY_ALL_FOLDED]면 모두 접힘.
 *   갤러리 캡처·테스트가 한 상태를 바로 띄워 보려고 쓴다 — 운영 화면은 넘기지 않는다.
 */
@Composable
fun TripJourneyContent(ui: JourneyUi, actions: ChecklistActions, openAtFirst: String? = null) {
    val trip = ui.trip ?: return
    val country = ui.countryName ?: trip.country
    val data = ui.data
    val current = Checklist.currentStage(data, trip, ui.today)
    val nights = ChronoUnit.DAYS.between(trip.start, trip.end).toInt()
    val dates = tripDateRange(trip.start, trip.end)
    val subtitle = stringResource(R.string.ck_progress_title, dates, stringResource(R.string.trip_nights, nights, nights + 1))
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDestroy by remember { mutableStateOf(false) }
    // 펼친 단계: null = 아직 고르지 않음(지금 단계가 펼쳐져 있다), ALL_FOLDED = 모두 접음, 그 밖 = 그 단계 key
    var chosen by rememberSaveable(trip.id) { mutableStateOf(openAtFirst) }
    val open: JourneyStage? = when (chosen) {
        null -> current
        JOURNEY_ALL_FOLDED -> null
        else -> JourneyStage.of(chosen)
    }
    val listState = rememberLazyListState()
    val keys = rememberKeyIndex()
    // 펼친 단계의 머리가 화면 밖으로 밀렸을 때만 **필요한 만큼** 올린다(긴 점프 없음 — keepKeyVisible)
    var ensure by remember { mutableStateOf<JourneyStage?>(null) }
    LaunchedEffect(ensure) {
        val s = ensure ?: return@LaunchedEffect
        listState.keepKeyVisible(keys, stageKey(s))
        ensure = null
    }
    val setOpen: (JourneyStage, Boolean) -> Unit = { s, want ->
        chosen = if (want) s.key else JOURNEY_ALL_FOLDED
        if (want) ensure = s
    }
    // 지금 할 일·`묵는 곳 보기`처럼 **밖에서 가리키는** 길은 접지 않고 언제나 그 단계를 펼친다
    val openStage: (JourneyStage) -> Unit = { s -> setOpen(s, true) }
    // `묵는 곳 보기`는 같은 화면 예약 단계를 펼친다 — 주소를 적는 곳이 한 군데뿐이라(2026-10-03)
    val acts = actions.copy(openStays = { openStage(JourneyStage.Book) })
    // 여행 과정은 **언제나 여덟 단계**다 — 그 나라에 항목이 없는 단계(예전 서명 팩 등)도 자리를 비우지 않는다.
    // 단계마다 그 단계에서 하는 일(예약 서류 넣어 두기 등)이 있어서, 항목이 비어도 카드가 할 일을 들고 있다
    val stages = JourneyStage.entries
    AppScreen(
        title = stringResource(R.string.journey_trip_title, country),
        subtitle = subtitle,
        speech = stringResource(R.string.ck_speech, country, data.total, data.done),
        icon = Icons.Outlined.Luggage,
        state = listState,
        keyIndex = keys,
    ) {
        item(key = "hero") { ChecklistHero(trip.country, data) }
        if (ui.overlaps) {
            item(key = "overlap") { NoticeBanner(stringResource(R.string.trips_overlap_note), icon = Icons.Outlined.EventBusy) }
        }
        item(key = "next") { NowCard(ui, current, openStage) }
        // 못한 일을 언제 알려 주는지 + 이 여행만 조용히 두기 (PRD 6.1)
        item(key = "alert") { ReminderRow(ui, actions) }
        stages.forEach { stage ->
            item(key = stageKey(stage)) {
                StageCard(
                    stage = stage,
                    items = data.stage(stage),
                    ui = ui,
                    now = stage == current,
                    open = stage == open,
                    onOpenChange = { want -> setOpen(stage, want) },
                    actions = acts,
                    onDestroy = { confirmDestroy = true },
                )
            }
        }
        sectionGap("custom-gap")
        item(key = "custom") { CustomCard(data.custom, actions) }
        item(key = "help") { HelpShortcutRow(actions.openHelp) }
        item(key = "local") { IconBullet(stringResource(R.string.ck_saved_local), Icons.Outlined.Lock) }
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

/** 목록에서 그 단계 카드를 찾는 열쇠 (펼친 뒤 머리가 보이는지 볼 때 쓴다) */
internal fun stageKey(stage: JourneyStage) = "stage-${stage.key}"

/**
 * 아코디언이 '모두 접힘'을 기억하는 값 (단계 key와 겹치지 않는 빈 글자).
 * [TripJourneyContent]의 `openAtFirst`에 넘기면 모두 접힌 모습으로 띄운다(갤러리 캡처·테스트).
 */
internal const val JOURNEY_ALL_FOLDED = ""

/**
 * 지금 할 일 (Accent 채움, 화면에 하나): 가장 급한 안 한 일 **한 가지**와 그 일이 있는 **단계를 펼치는** 버튼.
 * 항목을 여기서 다시 그리지 않는다 — 누르면 그 단계 카드가 그 자리에서 펴지고 설명·버튼·출처를 함께 본다(길은 하나).
 * 버튼 글자에 단계 번호가 들어간다(`3단계 서류 열기`) — 번호가 순서를 말해 준다(운영자 2026-10-03).
 * 다 했으면 지금 단계 이름과 `이 단계는 다 했어요`.
 */
@Composable
private fun NowCard(ui: JourneyUi, current: JourneyStage, onGo: (JourneyStage) -> Unit) {
    val trip = ui.trip ?: return
    val next = Checklist.nowItems(ui.data, trip, ui.today, limit = 1).firstOrNull()
    val stage = next?.stage ?: current
    CardNewsCard(
        title = next?.title ?: stringResource(R.string.ck_now_all_done),
        icon = IconKeys.journeyStage(stage),
        eyebrow = stringResource(R.string.today_next_label),
        body = if (next == null) journeyStageBody(current) else null,
        style = NewsStyle.Accent,
    ) {
        // 단계 이름은 바로 아래 버튼(`<단계> 단계로`)이 말한다 — 여기서 또 적지 않는다
        if (next != null && (next.urgent || next.overdue)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (next.urgent) StatusTag(stringResource(R.string.ck_urgent), StatusKind.Required)
                else StatusTag(stringResource(R.string.ck_overdue), StatusKind.Caution)
            }
        }
        PrimaryButton(
            text = stringResource(R.string.journey_open_stage_step, stage.step, journeyStageName(stage)),
            onClick = { onGo(stage) },
            // 그 단계 카드는 이 카드 아래에 있다 = ArrowDownward (버튼 앞 꺾쇠 금지, R11)
            icon = Icons.Outlined.ArrowDownward,
            colors = ButtonStyles.onDark(Tokens.Accent),
        )
    }
}

/**
 * 단계 카드 하나 — **접혔다 펴지는 아코디언**(운영자 2026-10-03, 부록 H.7).
 * 머리(**번호 배지** + 이름 + 언제까지 + `3 / 7` + 막대, 지금 단계면 `지금` 태그, 끝에 펼침 꺾쇠) **전체가 단추**다.
 * 펼치면 그림 패널 + 그 단계가 무엇을 하는 때인지 한 줄 → 항목들 → **그 단계에서 하는 일**(출국 순서·공항 카드·예약 서류
 * 가져오기 같은 안내 카드). 접히면 머리만 남는다(숨기지 않고 접기만).
 * TalkBack: 머리는 `3단계 서류, 7개 중 2개 했어요` + 펼쳐짐/접힘 상태.
 */
@Composable
private fun StageCard(
    stage: JourneyStage,
    items: List<ChecklistItem>,
    ui: JourneyUi,
    now: Boolean,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    actions: ChecklistActions,
    onDestroy: () -> Unit,
) {
    val trip = ui.trip ?: return
    val dimens = LocalDimens.current
    val done = items.count { it.checked }
    val total = items.size
    StageSectionCard(
        title = journeyStageName(stage),
        hint = stageHint(stage, items, trip),
        icon = IconKeys.journeyStage(stage),
        done = done,
        total = total,
        now = now,
        nowLabel = stringResource(R.string.ck_phase_now),
        headerDescription = stringResource(R.string.ck_phase_cd, journeyStageStepName(stage), total, done),
        step = stage.step,
        open = open,
        onOpenChange = onOpenChange,
    ) {
        // 단계 그림은 펼친 글 옆에 (예전 단계 막대의 그림 언어를 여기로 옮겼다 — 부록 E.6·H.3)
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(dimens.inner + 4.dp)) {
            JourneyStageHeader(stage, size = dimens.iconBadge + 8.dp)
            KoText(
                journeyStageBody(stage),
                MaterialTheme.typography.bodyMedium,
                Modifier.weight(1f),
                color = Tokens.InkSecondary,
            )
        }
        items.forEachIndexed { i, item ->
            if (i > 0) ChecklistDivider()
            ItemRow(item, trip, ui.today, actions, onDestroy)
        }
        StageExtras(stage, ui, actions)
    }
}

/**
 * 단계 '언제까지' 한 줄. 떠나기 전 단계는 그 단계 항목 중 **가장 이른 기한**(기한 축)으로,
 * 떠난 뒤 단계는 날짜 대신 말로(`공항에 내린 날`) — 기한 규칙은 그대로 두고 보여 주는 말만 단계에 맞춘다.
 */
@Composable
private fun stageHint(stage: JourneyStage, items: List<ChecklistItem>, trip: Trip): String? = when (stage) {
    JourneyStage.Departure -> stringResource(R.string.journey_hint_departure, monthDay(trip.start))
    JourneyStage.Arrival -> stringResource(R.string.journey_hint_arrival)
    JourneyStage.During -> stringResource(R.string.journey_hint_during)
    JourneyStage.Return -> stringResource(R.string.journey_hint_return)
    else -> items.mapNotNull { it.dueBy }.minOrNull()?.let { stringResource(R.string.journey_due_by, monthDay(it)) }
}

/** 그 단계에서 하는 일 — 예전 `오늘` 화면의 단계별 카드가 여기로 들어왔다 */
@Composable
private fun StageExtras(stage: JourneyStage, ui: JourneyUi, actions: ChecklistActions) {
    when (stage) {
        JourneyStage.Plan -> PlanExtras(ui, actions)
        JourneyStage.Book -> {
            // 묵는 곳이 예약 단계의 집이다 (2026-10-03) — 날짜별로 여러 곳, 지도·기사님께 보여 주기까지 여기서
            StaysCard(ui.stays, ui.stayNotes, stayActions(actions))
            BookingCard(actions)
        }
        JourneyStage.Docs -> Unit
        JourneyStage.Pack -> {
            if (ui.data.stage(stage).any { it.detail is ItemDetail.Essential }) {
                // 같은 앱 안 화면으로 — 바깥 링크 모양 대신 글자 버튼
                Box(Modifier.fillMaxWidth()) {
                    QuietButton(stringResource(R.string.ck_essentials_link), onClick = actions.openEssentials, icon = IconKeys.essentials)
                }
            }
        }
        JourneyStage.Departure -> DepartureExtras(ui, actions)
        JourneyStage.Arrival -> ArrivalExtras(ui, actions)
        JourneyStage.During -> DuringExtras(ui, actions)
        JourneyStage.Return -> ReturnExtras(ui, actions)
    }
}

/**
 * 계획: 날짜·내리는 공항 고치기 + 이 나라 안내 + **관광 일정**(2026-10-09, 이 나라 관광지가 있거나 담은 곳이 있을 때)
 * (그림 모자이크 — 나라 화면 길 안내와 같은 모양)
 */
@Composable
private fun PlanExtras(ui: JourneyUi, actions: ChecklistActions) {
    val trip = ui.trip ?: return
    val country = ui.countryName ?: trip.country
    val plan = ui.itinerary?.takeIf { it.available || it.total > 0 }
    NavMosaic(
        listOfNotNull(
            TileSpec(
                stringResource(R.string.journey_plan_country, country),
                Icons.Outlined.TravelExplore,
                { actions.openCountry(trip.country) },
                tone = BadgeTone.Accent,
                illustration = com.readyport.ui.components.Illus.Entry,
            ),
            TileSpec(
                stringResource(R.string.journey_plan_edit),
                Icons.Outlined.EditCalendar,
                { actions.editTrip(trip.id) },
                tone = BadgeTone.Accent,
                illustration = com.readyport.ui.components.Illus.Plan,
            ),
            plan?.let { itineraryTile(it) { actions.openItinerary(trip.id) } },
        ),
        // 타일이 둘·셋뿐이라 2열 그리드에 한 칸만 차는 모양이 된다 — 폭 전체 행으로
        columns = 1,
    )
}

/** 숙소 카드가 쓰는 길 — 여행 화면의 동작에서 만든다 */
private fun stayActions(actions: ChecklistActions) = StayActions(
    edit = actions.openStayEdit,
    showToDriver = actions.showStayToDriver,
    import = actions.openBooking,
)

/** 예약 단계의 집: 예약 서류 가져오기 (예전에는 설정 › 내 정보 안에만 있어서 여행 흐름에서 보이지 않았다) */
@Composable
private fun BookingCard(actions: ChecklistActions) {
    CardNewsCard(
        title = stringResource(R.string.journey_booking_title),
        icon = IconKeys.bookingKind("flight"),
        body = stringResource(R.string.journey_booking_body),
    ) {
        // 버튼 아이콘은 서류(머리 배지의 항공권과 겹치지 않게)
        SecondaryButton(stringResource(R.string.journey_booking_open), onClick = actions.openBooking, icon = IconKeys.bookingKind("other"))
    }
}

/** 출국: 출국 순서 카드 + 도착 공항 짧은 카드 + `도착했어요` (출발 당일부터) */
@Composable
private fun DepartureExtras(ui: JourneyUi, actions: ChecklistActions) {
    val trip = ui.trip ?: return
    PhotoHeaderCard(
        photo = Photos.Airport,
        title = keepTitle(stringResource(R.string.today_departure_steps_title)),
        icon = Icons.Outlined.FlightTakeoff,
    ) {
        StepList(
            listOf(
                step(stringResource(R.string.today_departure_step1), Icons.Outlined.LocalAirport, stringResource(R.string.today_departure_step1_detail)),
                step(stringResource(R.string.today_departure_step2), Icons.Outlined.Luggage),
                step(stringResource(R.string.today_departure_step3), Icons.Outlined.Security),
                step(stringResource(R.string.today_departure_step4), Icons.Outlined.HowToReg),
                step(stringResource(R.string.today_departure_step5), Icons.Outlined.MeetingRoom),
            ),
        )
    }
    // 도착하면 이 순서예요 — 이 여행의 도착 공항 순서(짧은 모양). 자세히는 나라 화면 공항 묶음
    ui.airport?.let { airport ->
        AirportCompactCard(
            title = keepTitle(stringResource(R.string.airport_today_departure_title)),
            airport = airport,
            sources = airportRefs(ui, airport),
            onOpenGuide = { actions.openAirport(trip.country, airport.code) },
        )
    }
    // 출발 당일부터 — 그 전에 누를 일이 없다(시간대가 바뀌면 앱이 스스로 안다).
    // 화면의 채운 주 버튼은 맨 위 `지금 할 일` 하나뿐이라 여기서는 테두리 보조 버튼(원칙 7)
    if (!ui.today.isBefore(trip.start) && trip.arrivedAt == null) {
        SecondaryButton(stringResource(R.string.today_arrived_button), onClick = actions.arrived, icon = Icons.Outlined.FlightLand)
    }
}

/** 입국: 보여 주기 + 도착 공항 순서(유심·환전·숙소 이어서) + 다 했어요 · 되돌리기 */
@Composable
private fun ArrivalExtras(ui: JourneyUi, actions: ChecklistActions) {
    val trip = ui.trip ?: return
    val arrived = trip.arrivedAt != null
    val after = listOf(
        step(stringResource(R.string.today_arrival_step3), Icons.Outlined.SimCard),
        step(stringResource(R.string.today_arrival_step4), Icons.Outlined.CurrencyExchange),
        step(stringResource(R.string.today_arrival_step5), Icons.Outlined.Hotel),
    )
    val done: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        // 체크 저장 없이 '다 했어요'만 (D10)
        if (arrived && !trip.arrivalDismissed) {
            SecondaryButton(
                text = stringResource(R.string.today_arrival_done),
                onClick = actions.arrivalDone,
                icon = Icons.Outlined.TaskAlt,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    // 공항에서 바로 필요한 것: 도착한 날 묵는 곳 주소 + 지도 (2026-10-03)
    StayHereCard(
        titleRes = R.string.stay_arrival_title,
        emptyRes = R.string.stay_arrival_empty,
        stay = Stays.forArrival(ui.stays, trip),
        actions = stayActions(actions),
    )
    val airport = ui.airport
    if (airport != null) {
        AirportCompactCard(
            title = keepTitle(stringResource(R.string.today_arrival_title)),
            airport = airport,
            sources = airportRefs(ui, airport),
            onOpenGuide = { actions.openAirport(trip.country, airport.code) },
            extra = after,
            footer = done,
        )
    } else {
        CardNewsCard(title = keepTitle(stringResource(R.string.today_arrival_title)), icon = Icons.Outlined.FlightLand) {
            StepList(
                listOf(
                    step(stringResource(R.string.today_arrival_step1), Icons.Outlined.HowToReg),
                    step(stringResource(R.string.today_arrival_step2), Icons.Outlined.Luggage),
                ) + after,
            )
            if (ui.hasAirports) {
                QuietButton(
                    stringResource(R.string.airport_open_guide_any),
                    onClick = { actions.openAirport(trip.country, null) },
                    icon = Icons.Outlined.LocalAirport,
                )
            }
            done()
        }
    }
    // '도착했어요'를 잘못 눌렀으면 되돌린다(재검토 R18) — 출발 당일에만: 그 뒤에는 되돌려도 '여행 중'이라 뜻이 없다
    if (arrived && ui.today == trip.start) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            QuietButton(stringResource(R.string.today_arrived_undo_v2), onClick = actions.undoArrived, icon = Icons.AutoMirrored.Outlined.Undo)
        }
    }
}

/** 여행 중: 큰 타일 넷 (PRD 5.1). 숙소로 돌아가기만 Navy 강조 */
@Composable
private fun DuringExtras(ui: JourneyUi, actions: ChecklistActions) {
    val trip = ui.trip ?: return
    // 오늘 묵는 곳 — 날짜별로 숙소가 다른 여행에서 '오늘 어디로 돌아가는지'
    StayHereCard(
        titleRes = R.string.stay_today_title,
        emptyRes = R.string.stay_today_empty,
        stay = Stays.on(ui.stays, ui.today),
        actions = stayActions(actions),
    )
    // 오늘 갈 곳 — 관광 일정에 담은 곳이 있을 때만(2026-10-09)
    ui.itinerary?.takeIf { it.total > 0 }?.let { plan ->
        TodayPlacesCard(plan, actions.openAttraction, actions.openLink) { actions.openItinerary(trip.id) }
    }
    InfoTileGrid(
        listOfNotNull(
            TileSpec(stringResource(R.string.today_go_stay), Icons.Outlined.Hotel, actions.openTransport, emphasized = true),
            TileSpec(stringResource(R.string.today_phrases), Icons.Outlined.Translate, actions.openHelp, tone = BadgeTone.Help),
            TileSpec(stringResource(R.string.today_show_qr), Icons.Outlined.QrCode2, actions.openPresent),
            if (ui.hasShopping) {
                TileSpec(stringResource(R.string.ck_action_shopping), Icons.Outlined.ShoppingBag, { actions.openShopping(trip.country) })
            } else {
                null
            },
        ),
    )
}

/** 복귀: (정리를 마쳤으면) 축하 카드 → 담아 둔 물건 → 귀국 전 확인 전체 → 여권 지우기 미루기 */
@Composable
private fun ReturnExtras(ui: JourneyUi, actions: ChecklistActions) {
    val trip = ui.trip ?: return
    if (trip.wrappedUp) WrapUpCard(ui)
    if (ui.cart.isNotEmpty()) CartCard(ui)
    if (ui.returnFacts.isNotEmpty() || ui.returnLinks.isNotEmpty()) {
        // 귀국 전 확인 전체 모양은 복귀 단계에만(운영자 결정 10)
        ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, actions.openLink, ReturnCheckMode.Full)
    }
    if (ui.stage.askDestroy) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            QuietButton(stringResource(R.string.today_destroy_later), onClick = actions.postponeDestroy, icon = Icons.Outlined.Schedule)
        }
    }
}

/** 공항 안내 출처 줄 — 팩 출처 이름(못 찾으면 `공식 안내`), 날짜는 공항 확인일 */
@Composable
private fun airportRefs(ui: JourneyUi, airport: Airport): List<SourceRef> {
    val fallback = stringResource(R.string.source_official_fallback)
    val names = ui.indexSources + ui.sourceNames
    return airportSources(airport) { id, date -> SourceRef(resolveSourceName(id, names, fallback), displayDate(date)) }
}

/** 단계 한 줄 (글자는 keepWords) */
private fun step(text: String, icon: ImageVector, detail: String? = null) = Step(text, icon, detail?.let(::keepWords))

/** 반입 판정 순서: 불가 → 주의 → 가능 (6-13) */
private fun importOrder(status: ImportStatus): Int = when (status) {
    ImportStatus.Prohibited -> 0
    ImportStatus.Caution -> 1
    ImportStatus.Allowed -> 2
}

/**
 * 담아 둔 물건 카드 (6-13): 행마다 분류 아이콘 + 이름 + 반입 판정 배지 + 판정 설명.
 * 카드 맨 아래 출처 = 반입 판정 출처(importSource) 먼저, 그다음 품목 출처.
 */
@Composable
private fun CartCard(ui: JourneyUi) {
    val fallback = stringResource(R.string.source_official_fallback)
    val names = ui.indexSources + ui.sourceNames
    val items = ui.cart.sortedBy { importOrder(it.import) }
    val ordered = items.map { it.importSource to it.lastVerified } + items.map { it.source to it.lastVerified }
    val refs = ordered.map { (id, date) -> SourceRef(resolveSourceName(id, names, fallback), displayDate(date)) }
    CardNewsCard(
        title = stringResource(R.string.today_cart_title),
        icon = Icons.Outlined.ShoppingBag,
        sources = refs,
    ) {
        // 카드 머리 아래 결론 한 줄: 판정별 알약(위험 순, 로컬 값)
        val counts = items.groupingBy { it.import }.eachCount()
        FlowRow(
            Modifier.padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(ImportStatus.Prohibited, ImportStatus.Caution, ImportStatus.Allowed).forEach { status ->
                counts[status]?.let { n ->
                    StatusTag(stringResource(R.string.today_cart_verdict_count, stringResource(importLabel(status)), n), importKind(status))
                }
            }
        }
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = Tokens.Line)
            CartRow(item)
        }
    }
}

@Composable
private fun CartRow(item: ShoppingItem) {
    val dimens = LocalDimens.current
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(IconKeys.item(item.id, item.category), tone = BadgeTone.Neutral, size = dimens.iconBadgeSmall)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KoText(item.names.ko, style = MaterialTheme.typography.titleMedium, color = Tokens.Ink)
            ImportVerdictNote(item.import, item.importNoteKo)
        }
    }
}

/**
 * 정리 축하 카드 (재검토 R14·R19): 흰 카드 가운데 둥근 나라 사진 + 여권 도장 같은 배지 →
 * `태국 여행, 잘 다녀오셨어요` → 한 줄 → 숫자 타일(여행 기간·챙긴 물건·담아 온 물건 — 앱 안 값만).
 */
@Composable
private fun WrapUpCard(ui: JourneyUi) {
    val dimens = LocalDimens.current
    val country = ui.countryName
    val trip = ui.trip
    val nights = trip?.let { ChronoUnit.DAYS.between(it.start, it.end).toInt().takeIf { n -> n >= 0 } }
    val title = if (country != null) {
        stringResource(R.string.today_wrapup_photo_title, country)
    } else {
        stringResource(R.string.today_wrapup_photo_title_plain)
    }
    val facts = listOfNotNull(
        nights?.let { Fact(Icons.Outlined.DateRange, stringResource(R.string.trip_nights, it, it + 1), stringResource(R.string.trip_length_label)) },
        ui.essentialsTotal.takeIf { it > 0 }?.let {
            Fact(IconKeys.essentials, stringResource(R.string.essentials_progress_stat, ui.essentialsDone, it), stringResource(R.string.today_wrapup_fact_essentials))
        },
        ui.cart.size.takeIf { it > 0 }?.let {
            Fact(Icons.Outlined.ShoppingBag, stringResource(R.string.today_wrapup_fact_cart_value, it), stringResource(R.string.today_wrapup_fact_cart))
        },
    )
    val shape = MaterialTheme.shapes.large
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().cardShadow(shape),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(dimens.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.inner),
        ) {
            StampPhoto(trip?.country, Modifier.padding(vertical = 4.dp))
            KoText(title, MaterialTheme.typography.titleLarge, color = Tokens.Ink, textAlign = TextAlign.Center, heading = true, glueShort = true)
            KoText(
                stringResource(R.string.today_wrapup_title_lines),
                MaterialTheme.typography.bodyLarge,
                color = Tokens.InkSecondary,
                textAlign = TextAlign.Center,
            )
            when {
                facts.size >= 2 -> FactGrid(facts, Modifier.padding(top = 4.dp))
                facts.size == 1 -> StatTile(facts.single(), Modifier.padding(top = 4.dp), wide = true)
            }
        }
    }
}

/** 정리 단계의 둥근 나라 사진 + 여권 도장 같은 배지 (장식 — TalkBack 숨김) */
@Composable
private fun StampPhoto(country: String?, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    val size = dimens.iconBadge * 3
    val badge = dimens.iconBadge
    val photo = rememberThumbnail(country?.let(Photos::country), size)
    Box(modifier.size(size + badge / 4).clearAndSetSemantics {}) {
        Box(Modifier.size(size).clip(CircleShape).background(Tokens.Navy)) {
            if (photo != null) {
                Image(
                    photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = rememberPhotoLift(photo),
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(badge)
                .clip(CircleShape)
                .background(Tokens.Navy)
                .border(2.dp, OnDark.gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = OnDark.gold, modifier = Modifier.size(dimens.icon))
        }
    }
}

/**
 * 제목용 줄바꿈: 문장 중간의 `! `·`? ` 뒤에서 줄을 바꾼다 — API 33 미만에서만.
 * 낱말 보호(keepWords)는 제목을 그리는 공용 부품(CardNewsCard 등)이 한다.
 */
internal fun keepTitle(text: String): String =
    if (!com.readyport.ui.components.KoreanBreak.syllableBreaks()) text else text.replace("! ", "!\n").replace("? ", "?\n")

