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
        val common = index?.commonEmergency.orEmpty()
        val indexSources = index?.sources.orEmpty().associate { it.id to it.name }
        HelpUi(
            countries = available,
            selected = selected,
            common = common,
            commonSourceName = commonSourceName(common, indexSources),
            ttsAvailable = tts,
            indexSources = indexSources,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HelpUi())

    fun selectCountry(code: String) = viewModelScope.launch { settings.setHelpCountry(code) }

    fun speak(phrase: Phrase) {
        val lang = ui.value.selected?.value?.localLanguage?.ttsLang ?: return
        speaker.speak(phrase.local, Locale.forLanguageTag(lang))
    }
}

/**
 * '어느 나라에서나' 카드의 출처 이름: 첫 공통 항목의 **출처 ID**를 index 출처 목록에서 이름으로 푼다.
 * (예전에는 index 출처 목록의 첫 이름을 그대로 붙여서, 항목과 다른 출처 이름이 보일 수 있었다 — DESIGN_SPEC 1.2 #1, 4.5)
 * 이름을 못 찾으면 null — 화면이 `공식 안내`로 대신 보인다(내부 ID는 절대 보이지 않는다).
 */
internal fun commonSourceName(common: List<EmergencyContact>, indexSources: Map<String, String>): String? =
    common.firstOrNull()?.source?.let { id -> indexSources[id]?.takeIf { it.isNotBlank() } }
