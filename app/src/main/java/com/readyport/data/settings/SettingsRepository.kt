package com.readyport.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 앱 설정. 개인정보는 여기에 두지 않는다(여권 등은 M2 암호화 보관함). */
data class AppSettings(
    /** null이면 첫 실행 질문("해외여행이 처음이세요?")에 아직 답하지 않은 상태 */
    val easyMode: Boolean?,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val easyModeKey = booleanPreferencesKey("easy_mode")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(easyMode = prefs[easyModeKey])
    }

    suspend fun setEasyMode(enabled: Boolean) {
        context.settingsStore.edit { it[easyModeKey] = enabled }
    }
}
