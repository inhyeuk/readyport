package com.readyport.trip

import com.readyport.pack.CountryPack
import com.readyport.pack.PackRepository
import com.readyport.vault.VaultContents
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 지갑을 열거나 여권을 저장·지울 때(지갑 내용이 메모리에 있을 때만) 여행마다 **결과만** 적는다 — 여권 만료일·번호는 적지 않고 로그도 남기지 않는다.
 * - 여권이 저장돼 있는지(있다/없다)
 * - 여행별 여권 남은 기간 결과(ok/short/unknown + 기준 달 수, [PassportValidity])
 * - 여행별 입국 카드를 그 여행 기간에 냈는지(제출 시각이 그 여행 입국 카드 기간 ~ 귀국일 사이)
 * 지갑이 잠겨 있으면 지난 결과를 그대로 쓴다(체크리스트가 잠금 때마다 깜빡이지 않게). 지갑 파일이 아예 없으면 '여권 없음'.
 */
@Singleton
class TripSignalsRecorder @Inject constructor(
    private val wallet: WalletRepository,
    private val trips: TripRepository,
    private val packs: PackRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(wallet.state, trips.trips, packs.revision) { state, list, _ -> state to list }
                .collectLatest { (state, list) ->
                    when (state) {
                        is WalletState.Unlocked -> {
                            val packsByCountry = list.map { it.country }.distinct().associateWith { packs.pack(it)?.value }
                            val (saved, perTrip) = signals(state.contents, list, packsByCountry)
                            trips.recordSignals(saved, perTrip)
                        }
                        is WalletState.Locked -> if (!state.hasData) trips.recordSignals(false, emptyMap())
                        else -> Unit
                    }
                }
        }
    }

    companion object {
        /** 순수 함수(테스트용): 지갑 내용 → (여권 있음, 여행 id → (여권 결과, 입국 카드 냈는지)) */
        fun signals(
            contents: VaultContents,
            list: List<Trip>,
            packsByCountry: Map<String, CountryPack?>,
        ): Pair<Boolean, Map<String, Pair<PassportCheck?, Boolean?>>> {
            val expiry = contents.passport?.expiryDate?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
            val perTrip = list.filter { it.datesValid }.associate { trip ->
                val pack = packsByCountry[trip.country]
                val rule = pack?.requirements?.firstOrNull { it.nationality == "KR" && it.purpose == "tourism" }?.passportValidity
                val check = expiry?.let { PassportValidity.check(it, trip, rule) }
                trip.id to (check to formSubmitted(contents, trip, pack))
            }
            return (contents.passport != null) to perTrip
        }

        /** 이 여행 입국 카드를 그 여행 기간에 냈는지. 낸 기록이 없으면 false, 양식이 없는 나라는 null */
        fun formSubmitted(contents: VaultContents, trip: Trip, pack: CountryPack?): Boolean? {
            val form = pack?.forms?.firstOrNull() ?: return null
            val record = contents.forms[form.id] ?: return false
            if (record.status != "submitted") return false
            val at = record.submittedAt?.let { runCatching { LocalDateTime.parse(it).toLocalDate() }.getOrNull() } ?: return false
            // 기간이 정해진 양식은 그 기간 시작일부터, 아니면 출발 30일 전부터 귀국일까지 낸 것만 이 여행 것으로 본다
            val from = form.windowDaysIncludingArrival?.takeIf { it >= 1 }?.let { trip.start.minusDays((it - 1).toLong()) }
                ?: trip.start.minusDays(30)
            return !at.isBefore(from.minusDays(1)) && !at.isAfter(trip.end)
        }
    }
}
