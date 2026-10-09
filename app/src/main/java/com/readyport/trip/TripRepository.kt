package com.readyport.trip

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.readyport.itinerary.TripItinerary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tripStore: DataStore<Preferences> by preferencesDataStore(name = "trip")

/**
 * 기기 안 여행 장부 한 덩어리 — 여행 목록 + 여행별 체크 상태. 날짜·나라·체크 표시뿐이고 개인정보는 없다.
 * 여권 만료일은 여기에 두지 않는다: 여권 남은 기간은 지갑(암호화)을 열었을 때 계산한 **결과만**(ok/short/unknown + 필요한 달 수) 둔다 (ARCHITECTURE 9.9).
 */
@Serializable
data class TripBook(
    val trips: List<Trip> = emptyList(),
    /** 여행 id → 그 여행의 체크 상태 */
    val checks: Map<String, TripChecks> = emptyMap(),
    /** 여권이 저장돼 있는지 — 지갑을 마지막으로 열었을 때 본 값(지갑이 잠겨 있으면 이 값을 쓴다). 모르면 null */
    val passportSaved: Boolean? = null,
    /**
     * 여행 id → 관광 일정(관광지 키 + 며칠째 + 순서만, 2026-10-09). 여행을 지우면 함께 지운다.
     * 예전 저장본에는 없어서 빈 값으로 읽힌다(encodeDefaults=false).
     */
    val itineraries: Map<String, TripItinerary> = emptyMap(),
)

/**
 * 한 여행의 체크 상태. 모두 기기 안에만(DataStore, 백업 제외 규칙 적용 폴더).
 * [marks]: 사람이 직접 정한 체크(앱이 확인한 항목을 되돌린 것 포함) — 항목 id → 체크.
 */
@Serializable
data class TripChecks(
    val marks: Map<String, Boolean> = emptyMap(),
    val custom: List<CustomItem> = emptyList(),
    /** 여권 남은 기간 결과(날짜 없음). 여권이 없거나 아직 확인 못 했으면 null */
    val passport: PassportCheck? = null,
    /** 이 여행 입국 카드 기간 안에 '냈어요' 기록이 있는지(지갑을 열었을 때 앱이 확인). 모르면 null */
    val formSubmitted: Boolean? = null,
    /** 이 여행 묵는 곳 가운데 **주소를 적어 둔 곳**이 있는지(지갑을 열었을 때 앱이 확인). 주소 글자는 적지 않는다. 모르면 null */
    val stayAddress: Boolean? = null,
)

/**
 * 지갑을 열었을 때 앱이 본 여행 하나의 결과 — **판정과 있다/없다만**. 여권 번호·만료일·숙소 주소 같은 글자는 담지 않는다.
 */
data class TripSignal(
    val passport: PassportCheck? = null,
    val formSubmitted: Boolean? = null,
    val stayAddress: Boolean? = null,
)

/** 내가 넣은 항목 — 글자만 */
@Serializable
data class CustomItem(val id: String, val text: String)

/** 여행 목록을 기기에만 저장한다 (백업 제외 규칙 적용 폴더) */
@Singleton
class TripRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val legacyKey = stringPreferencesKey("current_trip")
    private val bookKey = stringPreferencesKey("trip_book")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** 장부 전체. 예전 한 여행 저장본(`current_trip`)만 있으면 그 여행을 목록으로 옮긴 모양으로 읽는다(쓰기 전에도 같은 id) */
    val book: Flow<TripBook> = context.tripStore.data.map(::readBook).distinctUntilChanged()

    val trips: Flow<List<Trip>> = book.map { it.trips }.distinctUntilChanged()

    /** 지금 여행 — 여행 중 → 가장 가까운 다가오는 여행 → 정리를 마치지 않은 최근 여행 ([TripSelection.active]) */
    val trip: Flow<Trip?> = trips.map { TripSelection.active(it, LocalDate.now()) }.distinctUntilChanged()

    suspend fun all(): List<Trip> = trips.first()

    suspend fun current(): Trip? = trip.first()

    suspend fun get(id: String): Trip? = all().firstOrNull { it.id == id }

    /** 같은 id가 있으면 바꾸고 없으면 더한다. id가 비어 있으면 새 id를 붙인다. 저장한 여행을 돌려준다 */
    suspend fun save(trip: Trip): Trip {
        val saved = if (trip.id.isBlank()) trip.copy(id = UUID.randomUUID().toString()) else trip
        editBook { b ->
            val list = if (b.trips.any { it.id == saved.id }) b.trips.map { if (it.id == saved.id) saved else it } else b.trips + saved
            // 나라를 바꾸면 그 여행의 관광 일정(다른 나라 관광지)은 뜻이 없어 지운다
            val countryChanged = b.trips.any { it.id == saved.id && it.country != saved.country }
            b.copy(trips = list, itineraries = if (countryChanged) b.itineraries - saved.id else b.itineraries)
        }
        return saved
    }

    /** 여행 [id]를 고친다. 없으면 아무 일도 하지 않는다 */
    suspend fun update(id: String, transform: (Trip) -> Trip) {
        editBook { b -> b.copy(trips = b.trips.map { if (it.id == id) transform(it).copy(id = id) else it }) }
    }

    /** 지금 여행을 고친다 (오늘 화면·도착 감지가 쓴다) */
    suspend fun update(transform: (Trip) -> Trip) {
        val id = current()?.id ?: return
        update(id, transform)
    }

    /** 여행 하나와 그 체크 상태·관광 일정을 지운다 */
    suspend fun delete(id: String) {
        editBook { b -> b.copy(trips = b.trips.filterNot { it.id == id }, checks = b.checks - id, itineraries = b.itineraries - id) }
    }

    /** 모든 여행·체크를 지운다 (테스트·초기화용) */
    suspend fun clear() {
        context.tripStore.edit { it.remove(bookKey); it.remove(legacyKey) }
    }

    // ---------------- 관광 일정 (2026-10-09) ----------------

    fun itinerary(id: String): Flow<TripItinerary> = book.map { it.itineraries[id] ?: TripItinerary() }.distinctUntilChanged()

    /** 관광 일정을 고친다. 없는 여행이면 아무 일도 하지 않는다. 빈 일정은 장부에서 뺀다 */
    suspend fun editItinerary(tripId: String, transform: (TripItinerary) -> TripItinerary) = editBook { b ->
        if (b.trips.none { it.id == tripId }) return@editBook b
        val next = transform(b.itineraries[tripId] ?: TripItinerary())
        b.copy(itineraries = if (next.stops.isEmpty()) b.itineraries - tripId else b.itineraries + (tripId to next))
    }

    // ---------------- 체크 상태 ----------------

    fun checks(id: String): Flow<TripChecks> = book.map { it.checks[id] ?: TripChecks() }.distinctUntilChanged()

    /** 항목 체크를 사람이 정한다. [checked] = null이면 앱 판단(자동 확인)으로 되돌린다 */
    suspend fun setMark(tripId: String, itemId: String, checked: Boolean?) = editChecks(tripId) { c ->
        c.copy(marks = if (checked == null) c.marks - itemId else c.marks + (itemId to checked))
    }

    /** 내 항목 더하기. 빈 글이면 더하지 않고 null */
    suspend fun addCustom(tripId: String, text: String): String? {
        val t = text.trim().take(CUSTOM_MAX)
        if (t.isEmpty()) return null
        val id = "custom.${UUID.randomUUID()}"
        editChecks(tripId) { c -> c.copy(custom = c.custom + CustomItem(id, t)) }
        return id
    }

    suspend fun editCustom(tripId: String, itemId: String, text: String) {
        val t = text.trim().take(CUSTOM_MAX)
        if (t.isEmpty()) return
        editChecks(tripId) { c -> c.copy(custom = c.custom.map { if (it.id == itemId) it.copy(text = t) else it }) }
    }

    suspend fun removeCustom(tripId: String, itemId: String) = editChecks(tripId) { c ->
        c.copy(custom = c.custom.filterNot { it.id == itemId }, marks = c.marks - itemId)
    }

    /**
     * 지갑을 열었을 때 앱이 본 것을 적는다(개인정보 없음 — 있다/없다·결과만).
     * [passportSaved]: 여권이 저장돼 있는지. [perTrip]: 여행 id → 그 여행 결과([TripSignal]).
     */
    suspend fun recordSignals(passportSaved: Boolean, perTrip: Map<String, TripSignal>) = editBook { b ->
        val checks = b.checks.toMutableMap()
        b.trips.forEach { t ->
            val signal = perTrip[t.id] ?: TripSignal()
            val old = checks[t.id] ?: TripChecks()
            val next = old.copy(
                passport = signal.passport,
                formSubmitted = signal.formSubmitted ?: old.formSubmitted,
                stayAddress = signal.stayAddress ?: old.stayAddress,
            )
            if (next != old) checks[t.id] = next
        }
        b.copy(checks = checks, passportSaved = passportSaved)
    }

    /**
     * 예전 한 여행 저장본을 목록으로 옮긴다(앱 시작 때 한 번). 예전 '꼭 챙길 물건' 체크([legacyHave])는 그 여행의 체크로 옮긴다.
     * 옮겼으면 그 여행 id, 옮길 게 없으면 null.
     */
    suspend fun migrateLegacy(legacyHave: Set<String> = emptySet()): String? {
        var migrated: String? = null
        context.tripStore.edit { prefs ->
            val raw = prefs[legacyKey] ?: return@edit
            if (prefs[bookKey] != null) {
                prefs.remove(legacyKey)
                return@edit
            }
            val trip = legacyTrip(raw)
            if (trip == null) {
                prefs.remove(legacyKey)
                return@edit
            }
            val marks = legacyHave.associate { Checklist.essentialItemId(it) to true }
            val book = TripBook(trips = listOf(trip), checks = if (marks.isEmpty()) emptyMap() else mapOf(trip.id to TripChecks(marks = marks)))
            prefs[bookKey] = json.encodeToString(TripBook.serializer(), book)
            prefs.remove(legacyKey)
            migrated = trip.id
        }
        return migrated
    }

    /** 테스트용: 예전 버전(한 여행)이 저장한 모양을 그대로 써 둔다 */
    @androidx.annotation.VisibleForTesting
    internal suspend fun writeLegacyForTest(raw: String) {
        context.tripStore.edit { it.remove(bookKey); it[legacyKey] = raw }
    }

    private suspend fun editChecks(tripId: String, transform: (TripChecks) -> TripChecks) = editBook { b ->
        if (b.trips.none { it.id == tripId }) return@editBook b
        val next = transform(b.checks[tripId] ?: TripChecks())
        b.copy(checks = b.checks + (tripId to next))
    }

    private suspend fun editBook(transform: (TripBook) -> TripBook) {
        context.tripStore.edit { prefs ->
            val next = transform(readBook(prefs))
            writeBook(prefs, next)
        }
    }

    private fun writeBook(prefs: MutablePreferences, book: TripBook) {
        prefs[bookKey] = json.encodeToString(TripBook.serializer(), book)
        // 한 번이라도 새 장부를 쓰면 예전 저장본은 지운다(같은 여행이 두 번 생기지 않게)
        prefs.remove(legacyKey)
    }

    private fun readBook(prefs: Preferences): TripBook {
        prefs[bookKey]?.let { raw -> return runCatching { json.decodeFromString(TripBook.serializer(), raw) }.getOrElse { TripBook() } }
        val legacy = prefs[legacyKey]?.let(::legacyTrip) ?: return TripBook()
        return TripBook(trips = listOf(legacy))
    }

    /** 예전 저장본 → id 붙은 여행. id는 저장본 글자에서 정해서(같은 글이면 같은 id) 옮기기 전에 읽어도 id가 흔들리지 않는다 */
    private fun legacyTrip(raw: String): Trip? = runCatching { json.decodeFromString(Trip.serializer(), raw) }.getOrNull()
        ?.let { t -> if (t.id.isNotBlank()) t else t.copy(id = UUID.nameUUIDFromBytes("legacy-trip:$raw".toByteArray()).toString()) }

    companion object {
        /** 내 항목 글자 수 한도 — 메모장이 아니라 할 일 한 줄 */
        const val CUSTOM_MAX = 80
    }
}
