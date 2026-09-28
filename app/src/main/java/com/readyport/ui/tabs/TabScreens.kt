package com.readyport.ui.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.PackRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SourceFooter
import com.readyport.ui.components.TopicCard
import com.readyport.ui.pack.displayDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.readyport.prep.Essentials
import com.readyport.trip.TripRepository
import javax.inject.Inject

/** 준비 탭에 보여 줄 입국 서류 하나 */
data class FormEntry(
    val formId: String,
    val nameKo: String,
    val countryKo: String,
    val feeKo: String,
    val windowKo: String,
    val sourceName: String,
    val lastVerified: String,
)

/** 준비 탭의 '꼭 챙길 물건' 요약: 전체 n개 중 m개 */
data class EssentialsSummary(val total: Int = 0, val done: Int = 0)

@HiltViewModel
class PrepareViewModel @Inject constructor(
    packs: PackRepository,
    settings: SettingsRepository,
    trips: TripRepository,
) : ViewModel() {
    val essentials: StateFlow<EssentialsSummary> = combine(settings.settings, trips.trip, packs.revision) { s, trip, _ ->
        val index = packs.index()?.value
        val dest = trip?.let { packs.pack(it.country)?.value?.power }
        val rules = Essentials.select(index?.essentials.orEmpty(), index?.homePower, dest)
        EssentialsSummary(rules.size, rules.count { it.id in s.haveItems })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EssentialsSummary())

    /** 찜한 나라의 입국 서류. 찜이 없으면 받아 둔 모든 나라 (여행 만들기는 M6) */
    val forms: StateFlow<List<FormEntry>> = combine(settings.settings, packs.revision) { s, _ ->
        val countries = packs.index()?.value?.countries.orEmpty().filter { it.pack }
        val chosen = countries.filter { it.code in s.favorites }.ifEmpty { countries }
        chosen.mapNotNull { packs.pack(it.code)?.value }.flatMap { pack ->
            pack.forms.map { f ->
                FormEntry(f.id, f.nameKo, pack.names.ko, f.feeKo, f.windowKo, pack.source(f.source)?.name ?: f.source, f.lastVerified)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun PrepareScreen(onOpenForm: (String) -> Unit, onOpenEssentials: () -> Unit = {}, viewModel: PrepareViewModel = hiltViewModel()) {
    val forms by viewModel.forms.collectAsStateWithLifecycle()
    val essentials by viewModel.essentials.collectAsStateWithLifecycle()
    PrepareContent(forms, onOpenForm, essentials, onOpenEssentials)
}

@Composable
fun PrepareContent(
    forms: List<FormEntry>,
    onOpenForm: (String) -> Unit,
    essentials: EssentialsSummary = EssentialsSummary(),
    onOpenEssentials: () -> Unit = {},
) {
    AppScreen(
        title = stringResource(R.string.prepare_title),
        subtitle = stringResource(R.string.prepare_subtitle),
        speech = stringResource(R.string.prepare_speech),
    ) {
        // 정부 비제휴 고지는 '입국 준비' 탭 맨 위에 둔다 (PRD 8.1)
        item(key = "disclaimer") {
            TopicCard(title = stringResource(R.string.prepare_disclaimer), body = null, tone = CardTone.Notice)
        }
        if (forms.isEmpty()) {
            item(key = "forms") {
                TopicCard(stringResource(R.string.prepare_forms_title), stringResource(R.string.prepare_forms_body), comingSoon = true)
            }
        }
        forms.forEach { f ->
            item(key = "form-${f.formId}") {
                InfoCard {
                    Text(stringResource(R.string.prepare_forms_title), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.prepare_form_country, f.countryKo, f.nameKo), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.guide_form_fee, f.feeKo), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.guide_form_window, f.windowKo), style = MaterialTheme.typography.bodyMedium)
                    PrimaryButton(stringResource(R.string.prepare_form_open), onClick = { onOpenForm(f.formId) })
                    SourceFooter(f.sourceName, displayDate(f.lastVerified))
                }
            }
        }
        item(key = "items") {
            InfoCard {
                Text(stringResource(R.string.prepare_items_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.prepare_items_body), style = MaterialTheme.typography.bodyMedium)
                if (essentials.total > 0) {
                    Text(stringResource(R.string.essentials_progress, essentials.total, essentials.done), style = MaterialTheme.typography.bodyLarge)
                }
                PrimaryButton(stringResource(R.string.prepare_items_open), onClick = onOpenEssentials)
            }
        }
        item(key = "apps") {
            TopicCard(stringResource(R.string.prepare_apps_title), stringResource(R.string.prepare_apps_body), comingSoon = true)
        }
        item(key = "bookings") {
            TopicCard(stringResource(R.string.prepare_bookings_title), stringResource(R.string.prepare_bookings_body), comingSoon = true)
        }
    }
}
