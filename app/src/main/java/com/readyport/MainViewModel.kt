package com.readyport

import android.content.Context
import com.readyport.pack.PackSync
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readyport.data.settings.AppSettings
import com.readyport.data.settings.SettingsRepository
import com.readyport.tts.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val speaker: Speaker,
) : ViewModel() {

    /** null = 아직 읽는 중 */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEasyMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEasyMode(enabled) }
    }

    fun setChildMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setChildMode(enabled) }
    }

    /** 나라 안내를 와이파이에서만 받기 (PRD 5.7) */
    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWifiOnly(enabled)
            PackSync.scheduleDaily(context, enabled)
            PackSync.requestNow(context, enabled)
        }
    }

    /** 챙길 일 알림 켬·끔 (PRD 6.1). 작업을 다시 맞추는 일은 ChecklistAlerts가 설정을 지켜보다가 한다 */
    fun setAlertsOn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAlertsOn(enabled) }
    }

    fun setAlertHour(hour: Int) {
        viewModelScope.launch { settingsRepository.setAlertHour(hour) }
    }

    fun speak(text: String) = speaker.speak(text)

    override fun onCleared() {
        speaker.stop()
    }
}
