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
    /** 공지 알림(서비스 안내·긴급 공지, 토픽 notice_all). 기본 켬 (docs/NOTICES_PUSH.md) */
    val noticePush: Boolean = true,
    /** 광고성 소식 알림(토픽 notice_promo) — 정보통신망법 제50조: 기본 끔, 사람이 직접 켠다 */
    val promoPush: Boolean = false,
    /** 광고성 소식 받기를 켜거나 끈 날 yyyy-MM-dd (이 휴대폰에만 — 설정에 보여 준다) */
    val promoDate: String? = null,
    /** 밤(21시~다음 날 8시)에도 광고성 소식 받기 — 따로 동의, 기본 끔 */
    val promoNight: Boolean = false,
    /** 밤 광고 알림을 켜거나 끈 날 */
    val promoNightDate: String? = null,
    /** 게시판 답글 알림 — 내 글·댓글에 새 댓글이 오면 이 휴대폰이 스스로 확인해 알린다(서버 토큰 없음). 기본 켬 (docs/BOARD.md) */
    val boardReplies: Boolean = true,
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
    private val noticePushKey = booleanPreferencesKey("notice_push")
    private val promoPushKey = booleanPreferencesKey("promo_push")
    private val promoDateKey = stringPreferencesKey("promo_date")
    private val promoNightKey = booleanPreferencesKey("promo_night")
    private val promoNightDateKey = stringPreferencesKey("promo_night_date")
    private val boardRepliesKey = booleanPreferencesKey("board_replies")

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
            noticePush = prefs[noticePushKey] ?: true,
            promoPush = prefs[promoPushKey] ?: false,
            promoDate = prefs[promoDateKey],
            // 밤 광고 알림은 광고성 소식 받기가 켜져 있을 때만 뜻이 있다
            promoNight = (prefs[promoPushKey] ?: false) && (prefs[promoNightKey] ?: false),
            promoNightDate = prefs[promoNightDateKey],
            boardReplies = prefs[boardRepliesKey] ?: true,
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

    // ---------------- 공지·소식 알림 (docs/NOTICES_PUSH.md) ----------------

    suspend fun setNoticePush(enabled: Boolean) {
        context.settingsStore.edit { it[noticePushKey] = enabled }
    }

    /**
     * 광고성 소식 받기 켬·끔과 그 날(동의·철회 기록 — 이 휴대폰에만, 설정 화면에 보인다). 끄면 밤 광고 알림도 함께 끈다.
     * 서버에는 아무것도 적지 않는다: 받는 길은 토픽 notice_promo 구독뿐이고, 끄면 구독을 푼다(CloudSync).
     */
    suspend fun setPromoPush(enabled: Boolean, today: LocalDate) {
        context.settingsStore.edit {
            it[promoPushKey] = enabled
            it[promoDateKey] = today.toString()
            if (!enabled && it[promoNightKey] == true) {
                it[promoNightKey] = false
                it[promoNightDateKey] = today.toString()
            }
        }
    }

    /** 밤(21시~8시) 광고 알림 — 광고성 소식 받기가 켜져 있을 때만 켤 수 있다 */
    suspend fun setPromoNight(enabled: Boolean, today: LocalDate) {
        context.settingsStore.edit {
            if (enabled && it[promoPushKey] != true) return@edit
            it[promoNightKey] = enabled
            it[promoNightDateKey] = today.toString()
        }
    }

    /** 게시판 답글 알림 켬·끔 */
    suspend fun setBoardReplies(enabled: Boolean) {
        context.settingsStore.edit { it[boardRepliesKey] = enabled }
    }

    private fun readAlerted(raw: Set<String>): Map<String, String> =
        raw.mapNotNull { entry ->
            val id = entry.substringBeforeLast('|', "")
            val day = entry.substringAfterLast('|', "")
            if (id.isEmpty() || day.isEmpty()) null else id to day
        }.toMap()
}
