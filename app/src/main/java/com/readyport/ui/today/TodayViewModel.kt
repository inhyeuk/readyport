package com.readyport.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readyport.pack.FormInfo
import com.readyport.pack.PackRepository
import com.readyport.trip.StageInfo
import com.readyport.trip.Trip
import com.readyport.trip.TripRepository
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
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val trips: TripRepository,
    private val packs: PackRepository,
    private val wallet: WalletRepository,
) : ViewModel() {

    /** 날짜·도착 모드 시간이 바뀌므로 1분마다 다시 계산 */
    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(60_000) } }

    val ui: StateFlow<TodayUi> = combine(trips.trip, packs.revision, wallet.state, ticker) { trip, _, w, now ->
        val pack = trip?.let { packs.pack(it.country)?.value }
        val form = pack?.forms?.firstOrNull()
        val contents = (w as? WalletState.Unlocked)?.contents
        val submitted = form?.let { contents?.forms?.get(it.id)?.status == "submitted" } ?: false
        TodayUi(
            trip = trip,
            stage = TripStages.compute(trip, LocalDate.now(), now, form?.windowDaysIncludingArrival, submitted),
            countryName = pack?.names?.ko,
            form = form,
            hasPassport = contents?.let { it.passport != null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())

    fun markArrived() = viewModelScope.launch {
        trips.update { it.copy(arrivedAt = System.currentTimeMillis(), arrivalDismissed = false) }
    }

    fun dismissArrival() = viewModelScope.launch { trips.update { it.copy(arrivalDismissed = true) } }

    fun postponeDestroy() = viewModelScope.launch {
        trips.update { it.copy(destroyPostponedUntil = LocalDate.now().plusDays(7).toString()) }
    }

    /** 여권 정보만 지운다 (PRD 4.2 ⑧). 지갑이 잠겨 있으면 먼저 연다 */
    suspend fun destroyPassportInfo(): WalletRepository.SaveResult {
        if (wallet.state.value !is WalletState.Unlocked) wallet.unlock()
        val r = wallet.update { it.withoutPassportInfo() }
        if (r == WalletRepository.SaveResult.Saved) trips.update { it.copy(wrappedUp = true) }
        return r
    }

    fun finishWithoutDestroy() = viewModelScope.launch { trips.update { it.copy(wrappedUp = true) } }

    fun newTrip() = viewModelScope.launch { trips.clear() }
}
