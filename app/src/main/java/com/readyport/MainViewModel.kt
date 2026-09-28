package com.readyport

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

    fun speak(text: String) = speaker.speak(text)

    override fun onCleared() {
        speaker.stop()
    }
}
