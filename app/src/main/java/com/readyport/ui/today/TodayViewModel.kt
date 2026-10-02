package com.readyport.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.Airport
import com.readyport.pack.FormInfo
import com.readyport.pack.OfficialLink
import com.readyport.pack.ShoppingItem
import com.readyport.pack.SourcedText
import com.readyport.prep.CartKey
import com.readyport.pack.PackRepository
import com.readyport.trip.Checklist
import com.readyport.trip.ChecklistItem
import com.readyport.trip.ChecklistProvider
import com.readyport.trip.StageInfo
import com.readyport.trip.Trip
import com.readyport.trip.TripRepository
import com.readyport.trip.TripSelection
import com.readyport.trip.TripSignalsRecorder
import com.readyport.trip.markFor
import com.readyport.trip.TripStage
import com.readyport.trip.TripStages
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TodayUi(
    val trip: Trip? = null,
    val stage: StageInfo = StageInfo(TripStage.NoTrip),
    val countryName: String? = null,
    val form: FormInfo? = null,
    /** 여권이 등록됐는지. 지갑이 잠겨 있으면 모름(null) */
    val hasPassport: Boolean? = null,
    val destroyed: Boolean = false,
    /** 쇼핑 리스트에 담은 물건 — 귀국 때 반입 여부를 다시 보여 준다 (PRD 4.2 ⑧, 11.3) */
    val cart: List<ShoppingItem> = emptyList(),
    val returnLinks: List<OfficialLink> = emptyList(),
    val returnFacts: List<SourcedText> = emptyList(),
    val indexSources: Map<String, String> = emptyMap(),
    /** 여행 나라 팩 출처 id → 이름 (담아 둔 물건의 품목·반입 판정 출처, DESIGN_SPEC 6-13) */
    val sourceNames: Map<String, String> = emptyMap(),
    /** 이 여행의 꼭 챙길 물건 수·챙긴 수 — 정리 단계 숫자 타일 `2 / 5 챙긴 물건`(재검토2 ①#6). 0이면 타일 없음 */
    val essentialsTotal: Int = 0,
    val essentialsDone: Int = 0,
    /** 이 여행 체크리스트 '지금 챙길 것'(지금 단계까지의 안 한 항목 몇 개, [Checklist.nowItems]) */
    val checklistNow: List<ChecklistItem> = emptyList(),
    val checklistDone: Int = 0,
    val checklistTotal: Int = 0,
    /** 저장된 여행 수(지난 여행 포함) — 1개 이상이면 `여행 목록 보기` */
    val tripCount: Int = 0,
    val today: LocalDate = LocalDate.now(),
    /** 이 여행의 도착 공항 안내(여행에 고른 공항, 팩에 공항이 하나뿐이면 그 공항). 없으면 null — 공항 카드 없음 */
    val airport: Airport? = null,
    /** 여행 나라 팩에 공항 안내가 있는지(공항을 고르지 않았을 때 `공항별 도착 순서 보기`) */
    val hasAirports: Boolean = false,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val trips: TripRepository,
    private val packs: PackRepository,
    private val wallet: WalletRepository,
    settings: SettingsRepository,
    private val checklists: ChecklistProvider,
) : ViewModel() {

    /** 날짜·도착 모드 시간이 바뀌므로 1분마다 다시 계산 */
    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(60_000) } }

    val ui: StateFlow<TodayUi> = combine(trips.book, packs.revision, wallet.state, ticker, settings.settings) { book, _, w, now, s ->
        val today = LocalDate.now()
        // 여러 여행 중 지금 여행(여행 중 → 가장 가까운 다가오는 여행 → 정리 안 한 최근 여행)
        val trip = TripSelection.active(book.trips, today)
        val pack = trip?.let { packs.pack(it.country)?.value }
        // 꼭 내야 하는 입국 카드만 오늘 단계·알림에 쓴다 — 의무가 아닌 신고(forms[].optional)는 기한을 만들지 않는다
        val form = pack?.requiredForms?.firstOrNull()
        val contents = (w as? WalletState.Unlocked)?.contents
        // 입국 카드를 냈는지는 이 여행 기준(같은 나라를 또 가도 지난 여행 제출로 닫히지 않게): 지갑이 열려 있으면 바로, 아니면 지난번 기록
        val submitted = trip?.let { t ->
            contents?.let { TripSignalsRecorder.formSubmitted(it, t, pack) } ?: book.checks[t.id]?.formSubmitted
        } ?: false
        val index = packs.index()?.value
        val data = trip?.let { checklists.build(it, book, today) }
        val essentials = data?.items.orEmpty().filter { it.id.startsWith(Checklist.essentialItemId("")) }
        TodayUi(
            trip = trip,
            stage = TripStages.compute(trip, today, now, form?.windowDaysIncludingArrival, submitted),
            countryName = pack?.names?.ko,
            form = form,
            hasPassport = contents?.let { it.passport != null } ?: book.passportSaved,
            cart = trip?.let { t -> pack?.shopping.orEmpty().filter { CartKey.of(t.country, it.id) in s.cart } }.orEmpty(),
            returnLinks = index?.returnLinks.orEmpty(),
            returnFacts = index?.returnFacts.orEmpty(),
            indexSources = index?.sources.orEmpty().associate { it.id to it.name },
            sourceNames = pack?.sources.orEmpty().associate { it.id to it.name },
            essentialsTotal = essentials.size,
            essentialsDone = essentials.count { it.checked },
            checklistNow = if (trip != null && data != null) Checklist.nowItems(data, trip, today) else emptyList(),
            checklistDone = data?.done ?: 0,
            checklistTotal = data?.total ?: 0,
            tripCount = book.trips.size,
            today = today,
            airport = trip?.let { t -> pack?.airport(t.arrivalAirport) } ?: pack?.airports?.singleOrNull(),
            hasAirports = pack?.airports?.isNotEmpty() == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())

    /** 오늘 화면이 보여 주는 여행을 고친다 */
    private suspend fun updateShown(transform: (Trip) -> Trip) {
        val id = ui.value.trip?.id ?: trips.current()?.id ?: return
        trips.update(id, transform)
    }

    /** '지금 챙길 것' 체크 — 체크리스트 화면과 같은 저장(여행 id별) */
    fun toggle(item: ChecklistItem, checked: Boolean) = viewModelScope.launch {
        val id = ui.value.trip?.id ?: return@launch
        trips.setMark(id, item.id, markFor(item, checked))
    }

    fun markArrived() = viewModelScope.launch {
        updateShown { it.copy(arrivedAt = System.currentTimeMillis(), arrivalDismissed = false) }
    }

    /** '도착했어요'를 잘못 눌렀을 때 되돌리기 (재검토 R18): 도착 시각을 지워 출발 당일이면 다시 출국 단계로 */
    fun undoArrived() = viewModelScope.launch { updateShown { it.copy(arrivedAt = null, arrivalDismissed = false) } }

    fun dismissArrival() = viewModelScope.launch { updateShown { it.copy(arrivalDismissed = true) } }

    fun postponeDestroy() = viewModelScope.launch {
        updateShown { it.copy(destroyPostponedUntil = LocalDate.now().plusDays(7).toString()) }
    }

    /** 여권 정보만 지운다 (PRD 4.2 ⑧). 지갑이 잠겨 있으면 먼저 연다 */
    suspend fun destroyPassportInfo(): WalletRepository.SaveResult {
        if (wallet.state.value !is WalletState.Unlocked) wallet.unlock()
        val r = wallet.update { it.withoutPassportInfo() }
        if (r == WalletRepository.SaveResult.Saved) updateShown { it.copy(wrappedUp = true) }
        return r
    }

    fun finishWithoutDestroy() = viewModelScope.launch { updateShown { it.copy(wrappedUp = true) } }
}
