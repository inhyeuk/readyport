package com.readyport.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            easyMode = prefs[easyModeKey],
            autoDestroyPassport = prefs[autoDestroyKey] ?: true,
            favorites = prefs[favoritesKey].orEmpty(),
            wifiOnly = prefs[wifiOnlyKey] ?: true,
            helpCountry = prefs[helpCountryKey],
            childMode = prefs[childModeKey] ?: false,
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

    suspend fun setHelpCountry(country: String) {
        context.settingsStore.edit { it[helpCountryKey] = country }
    }
}
