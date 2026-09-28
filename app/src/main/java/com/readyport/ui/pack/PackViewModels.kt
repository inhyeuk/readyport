package com.readyport.ui.pack

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.CountryPack
import com.readyport.pack.EmergencyContact
import com.readyport.pack.IndexCountry
import com.readyport.pack.Loaded
import com.readyport.pack.PackRepository
import com.readyport.pack.PackSync
import com.readyport.pack.Phrase
import com.readyport.tts.Speaker
import com.readyport.ui.nav.GuideRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

// ---------------- 여행지 ----------------

sealed interface PackStatus {
    data object NotReady : PackStatus
    data class Saved(val lastVerified: String) : PackStatus
    data object Waiting : PackStatus
    data object Downloading : PackStatus
}

data class CountryRow(val country: IndexCountry, val favorite: Boolean, val status: PackStatus?)

data class ExploreUi(val rows: List<CountryRow> = emptyList(), val wifiOnly: Boolean = true)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packs: PackRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val work = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PackSync.TAG_NOW)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }

    val ui: StateFlow<ExploreUi> = combine(settings.settings, packs.revision, work) { s, _, running ->
        val index = packs.index()?.value
        val rows = index?.countries.orEmpty().map { c ->
            val loaded = if (c.pack) packs.pack(c.code) else null
            val favorite = c.code in s.favorites
            val status = when {
                !c.pack -> PackStatus.NotReady
                loaded != null -> PackStatus.Saved(loaded.value.lastVerified)
                favorite && running -> PackStatus.Downloading
                favorite -> PackStatus.Waiting
                else -> null
            }
            CountryRow(c, favorite, status)
        }
        ExploreUi(rows, s.wifiOnly)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUi())

    fun toggleFavorite(country: String) = viewModelScope.launch {
        val now = settings.current()
        settings.setFavorite(country, country !in now.favorites)
        PackSync.requestNow(context, now.wifiOnly)
    }

    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch {
        settings.setWifiOnly(enabled)
        PackSync.scheduleDaily(context, enabled)
        PackSync.requestNow(context, enabled)
    }
}

// ---------------- 국가 가이드 ----------------

@HiltViewModel
class GuideViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val packs: PackRepository,
) : ViewModel() {
    private val country = handle.toRoute<GuideRoute>().country
    private val _pack = MutableStateFlow<Loaded<CountryPack>?>(null)
    val pack: StateFlow<Loaded<CountryPack>?> = _pack.asStateFlow()

    init {
        viewModelScope.launch { packs.revision.collect { _pack.value = packs.pack(country) } }
    }
}

// ---------------- 도움 ----------------

data class HelpUi(
    val countries: List<IndexCountry> = emptyList(),
    val selected: Loaded<CountryPack>? = null,
    val common: List<EmergencyContact> = emptyList(),
    val commonSourceName: String? = null,
    val ttsAvailable: Boolean = false,
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HelpUi())

    fun selectCountry(code: String) = viewModelScope.launch { settings.setHelpCountry(code) }

    fun speak(phrase: Phrase) {
        val lang = ui.value.selected?.value?.localLanguage?.ttsLang ?: return
        speaker.speak(phrase.local, Locale.forLanguageTag(lang))
    }
}
