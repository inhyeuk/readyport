package com.readyport.ui.pack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.EmergencyContact
import com.readyport.pack.IndexCountry
import com.readyport.pack.CountryPack
import com.readyport.pack.Loaded
import com.readyport.pack.PackRepository
import com.readyport.pack.Phrase
import com.readyport.tts.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

// ---------------- 도움 ----------------

data class HelpUi(
    val countries: List<IndexCountry> = emptyList(),
    val selected: Loaded<CountryPack>? = null,
    val common: List<EmergencyContact> = emptyList(),
    val commonSourceName: String? = null,
    val ttsAvailable: Boolean = false,
    /** index 출처 id → 이름 (어느 나라에서나 항목의 출처가 여러 개일 때 SourceList용, DESIGN_SPEC 4.5) */
    val indexSources: Map<String, String> = emptyMap(),
)

@HiltViewModel
class HelpViewModel @Inject constructor(
    private val packs: PackRepository,
    private val settings: SettingsRepository,
    private val speaker: Speaker,
) : ViewModel() {

    val ui: StateFlow<HelpUi> = combine(settings.settings, packs.revision) { s, _ ->
        val index = packs.index()?.value
        val available = index?.countries.orEmpty().filter { it.pack && packs.pack(it.code) != null }
        // 고른 나라 → 찜한 나라 → 받아 둔 첫 나라 순서
        val code = s.helpCountry?.takeIf { c -> available.any { it.code == c } }
            ?: available.firstOrNull { it.code in s.favorites }?.code
            ?: available.firstOrNull()?.code
        val selected = code?.let { packs.pack(it) }
        val tts = selected?.value?.localLanguage?.ttsLang
            ?.let { speaker.isAvailable(Locale.forLanguageTag(it)) } ?: false
        HelpUi(
            countries = available,
            selected = selected,
            common = index?.commonEmergency.orEmpty(),
            commonSourceName = index?.sources?.firstOrNull()?.name,
            ttsAvailable = tts,
            indexSources = index?.sources.orEmpty().associate { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HelpUi())

    fun selectCountry(code: String) = viewModelScope.launch { settings.setHelpCountry(code) }

    fun speak(phrase: Phrase) {
        val lang = ui.value.selected?.value?.localLanguage?.ttsLang ?: return
        speaker.speak(phrase.local, Locale.forLanguageTag(lang))
    }
}
