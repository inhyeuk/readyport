package com.readyport.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** 앱 설정. 개인정보는 여기에 두지 않는다(여권 등은 vault 패키지의 암호화 보관함). */
data class AppSettings(
    /** null이면 첫 실행 질문("해외여행이 처음이세요?")에 아직 답하지 않은 상태 */
    val easyMode: Boolean?,
    /** 여행이 끝나면 여권 정보 파기 (PRD 7.3). 기본 켬 — 개인정보 쪽으로 안전한 기본값 */
    val autoDestroyPassport: Boolean = true,
    /** 찜한 나라 코드(ISO2). 여행 일정이 아니라서 서버에 보내지 않아도 되지만, 보내지 않는다 */
    val favorites: Set<String> = emptySet(),
    /** 찜한 곳 안내를 와이파이에서만 받기 (PRD 5.7, 기본 켬) */
    val wifiOnly: Boolean = true,
    /** 도움 탭에서 마지막으로 고른 나라 */
    val helpCountry: String? = null,
    /** 자녀 폰 모드: 입국 QR과 도움만 (PRD 3.3) */
    val childMode: Boolean = false,
    /** 꼭 챙길 물건에서 '있어요'를 누른 항목 id (PRD 5.10) */
    val haveItems: Set<String> = emptySet(),
    /** 쇼핑 리스트에 담은 항목 "TH/item-id" (PRD 5.8). 귀국 때 반입 여부를 다시 보여 준다 */
    val cart: Set<String> = emptySet(),
    /** 챙길 일 알림 (PRD 6.1). 기본 켬 — 빼먹지 않게 */
    val alertsOn: Boolean = true,
    /** 알려 줄 시각(시, 0~23). 기본 아침 9시 (ChecklistReminders.DEFAULT_HOUR) */
    val alertHour: Int = 9,
    /** 알림을 꺼 둔 여행 id (체크리스트 화면의 조용히 두기) */
    val alertMutedTrips: Set<String> = emptySet(),
    /** 여행 id → 마지막으로 알린 날 yyyy-MM-dd (한 여행에 하루 한 번만) */
    val alertLastNotified: Map<String, String> = emptyMap(),
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val easyModeKey = booleanPreferencesKey("easy_mode")
    private val autoDestroyKey = booleanPreferencesKey("auto_destroy_passport")
    private val favoritesKey = stringSetPreferencesKey("favorite_countries")
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only")
    private val helpCountryKey = stringPreferencesKey("help_country")
    private val childModeKey = booleanPreferencesKey("child_mode")
    private val haveItemsKey = stringSetPreferencesKey("have_items")
    private val cartKey = stringSetPreferencesKey("shopping_cart")
    private val alertsOnKey = booleanPreferencesKey("alerts_on")
    private val alertHourKey = intPreferencesKey("alert_hour")
    private val alertMutedKey = stringSetPreferencesKey("alert_muted_trips")
    private val alertLastKey = stringSetPreferencesKey("alert_last_notified")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            easyMode = prefs[easyModeKey],
            autoDestroyPassport = prefs[autoDestroyKey] ?: true,
            favorites = prefs[favoritesKey].orEmpty(),
            wifiOnly = prefs[wifiOnlyKey] ?: true,
            helpCountry = prefs[helpCountryKey],
            childMode = prefs[childModeKey] ?: false,
            haveItems = prefs[haveItemsKey].orEmpty(),
            cart = prefs[cartKey].orEmpty(),
            alertsOn = prefs[alertsOnKey] ?: true,
            alertHour = prefs[alertHourKey] ?: 9,
            alertMutedTrips = prefs[alertMutedKey].orEmpty(),
            alertLastNotified = readAlerted(prefs[alertLastKey].orEmpty()),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setEasyMode(enabled: Boolean) {
        context.settingsStore.edit { it[easyModeKey] = enabled }
    }

    suspend fun setAutoDestroyPassport(enabled: Boolean) {
        context.settingsStore.edit { it[autoDestroyKey] = enabled }
    }

    suspend fun setFavorite(country: String, favorite: Boolean) {
        context.settingsStore.edit {
            val now = it[favoritesKey].orEmpty()
            it[favoritesKey] = if (favorite) now + country else now - country
        }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.settingsStore.edit { it[wifiOnlyKey] = enabled }
    }

    suspend fun setChildMode(enabled: Boolean) {
        context.settingsStore.edit { it[childModeKey] = enabled }
    }

    suspend fun setHave(itemId: String, have: Boolean) {
        context.settingsStore.edit {
            val now = it[haveItemsKey].orEmpty()
            it[haveItemsKey] = if (have) now + itemId else now - itemId
        }
    }

    suspend fun setInCart(key: String, inCart: Boolean) {
        context.settingsStore.edit {
            val now = it[cartKey].orEmpty()
            it[cartKey] = if (inCart) now + key else now - key
        }
    }

    /** 새 여행을 만들면 지난 여행의 준비물 체크·장바구니를 비운다 */
    suspend fun clearTripLists() {
        context.settingsStore.edit {
            it.remove(haveItemsKey)
            it.remove(cartKey)
        }
    }

    suspend fun setHelpCountry(country: String) {
        context.settingsStore.edit { it[helpCountryKey] = country }
    }

    // ---------------- 챙길 일 알림 (PRD 6.1) ----------------

    suspend fun setAlertsOn(enabled: Boolean) {
        context.settingsStore.edit { it[alertsOnKey] = enabled }
    }

    suspend fun setAlertHour(hour: Int) {
        context.settingsStore.edit { it[alertHourKey] = hour }
    }

    /** 이 여행만 조용히 두기 (체크리스트 화면) */
    suspend fun setTripAlertMuted(tripId: String, muted: Boolean) {
        context.settingsStore.edit {
            val now = it[alertMutedKey].orEmpty()
            it[alertMutedKey] = if (muted) now + tripId else now - tripId
        }
    }

    /**
     * 그 여행에 오늘 알렸다고 적는다(하루 한 번 규칙). 오래된 기록([keepDays]일 지난 것)은 지운다 — 설정이 끝없이 커지지 않게.
     * 저장 모양: `여행id|yyyy-MM-dd` 묶음 (여행 id는 UUID라 `|`가 없다).
     */
    suspend fun markAlerted(tripIds: Collection<String>, date: LocalDate, keepDays: Long = 30) {
        if (tripIds.isEmpty()) return
        val day = date.toString()
        val keepFrom = date.minusDays(keepDays).toString()
        context.settingsStore.edit { prefs ->
            val kept = prefs[alertLastKey].orEmpty()
                .filterNot { it.substringBeforeLast('|') in tripIds }
                .filter { it.substringAfterLast('|', "") >= keepFrom }
            prefs[alertLastKey] = (kept + tripIds.map { "$it|$day" }).toSet()
        }
    }

    private fun readAlerted(raw: Set<String>): Map<String, String> =
        raw.mapNotNull { entry ->
            val id = entry.substringBeforeLast('|', "")
            val day = entry.substringAfterLast('|', "")
            if (id.isEmpty() || day.isEmpty()) null else id to day
        }.toMap()
}
