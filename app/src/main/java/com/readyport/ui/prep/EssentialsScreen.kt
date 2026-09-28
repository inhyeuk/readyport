package com.readyport.ui.prep

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.EssentialRule
import com.readyport.pack.PackRepository
import com.readyport.prep.Essentials
import com.readyport.trip.TripRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.SourceFooter
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.TopicCard
import com.readyport.ui.pack.displayDate
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class EssentialRow(val rule: EssentialRule, val have: Boolean, val sourceName: String?)

data class EssentialsUi(
    val countryKo: String? = null,
    val nights: Int? = null,
    val month: Int? = null,
    val rows: List<EssentialRow> = emptyList(),
) {
    val done get() = rows.count { it.have }
    val hasAffiliate get() = rows.any { Essentials.isAffiliate(it.rule) }
}

@HiltViewModel
class EssentialsViewModel @Inject constructor(
    private val packs: PackRepository,
    private val settings: SettingsRepository,
    trips: TripRepository,
) : ViewModel() {

    val ui: StateFlow<EssentialsUi> = combine(settings.settings, trips.trip, packs.revision) { s, trip, _ ->
        val index = packs.index()?.value
        val pack = trip?.let { packs.pack(it.country)?.value }
        val rules = Essentials.select(index?.essentials.orEmpty(), index?.homePower, pack?.power)
        EssentialsUi(
            countryKo = pack?.names?.ko,
            nights = trip?.let { ChronoUnit.DAYS.between(it.start, it.end).toInt() },
            month = trip?.start?.monthValue,
            rows = rules.map { r ->
                EssentialRow(r, r.id in s.haveItems, r.source?.let { id -> index?.sources?.firstOrNull { it.id == id }?.name ?: id })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EssentialsUi())

    fun setHave(id: String, have: Boolean) = viewModelScope.launch { settings.setHave(id, have) }
}

@Composable
fun EssentialsScreen(viewModel: EssentialsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    EssentialsContent(
        ui = ui,
        onHave = viewModel::setHave,
        onOpenLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
    )
}

@Composable
fun EssentialsContent(ui: EssentialsUi, onHave: (String, Boolean) -> Unit, onOpenLink: (String) -> Unit) {
    val subtitle = if (ui.countryKo != null && ui.nights != null && ui.month != null) {
        stringResource(R.string.essentials_for_trip, ui.countryKo, ui.nights, ui.month)
    } else {
        stringResource(R.string.essentials_no_trip)
    }
    AppScreen(
        title = stringResource(R.string.prepare_items_title),
        subtitle = subtitle,
        speech = stringResource(R.string.essentials_speech),
    ) {
        // 제휴 고지는 목록 맨 위, 회색 박스 (PRD 5.10). 제휴 링크가 아직 없어도 원칙은 늘 보여 준다
        item(key = "disclosure") {
            TopicCard(stringResource(R.string.essentials_disclosure), null, tone = CardTone.Notice)
        }
        item(key = "progress") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.essentials_progress, ui.rows.size, ui.done), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { if (ui.rows.isEmpty()) 0f else ui.done.toFloat() / ui.rows.size },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        ui.rows.forEach { row -> item(key = "item-${row.rule.id}") { EssentialCard(row, onHave, onOpenLink) } }
    }
}

@Composable
private fun EssentialCard(row: EssentialRow, onHave: (String, Boolean) -> Unit, onOpenLink: (String) -> Unit) {
    val r = row.rule
    InfoCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.toggleable(row.have, role = Role.Switch, onValueChange = { onHave(r.id, it) }),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.nameKo, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.essentials_have), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = row.have, onCheckedChange = null)
        }
        if (r.ruleBadge == "carry_on_only") {
            StatusChip(stringResource(R.string.essentials_badge_carry_on), Tokens.CautionBg, Tokens.CautionText)
        }
        Text(r.reasonKo, style = MaterialTheme.typography.bodyMedium)
        r.link?.let { link ->
            val affiliate = link.type == "affiliate"
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { onOpenLink(link.url) }, modifier = Modifier.heightIn(min = LocalDimens.current.buttonHeight)) {
                    Text(link.labelKo, style = MaterialTheme.typography.labelLarge)
                }
                // '제휴' 라벨은 제휴 링크에만. 보험·금융 안내(official_info)에는 붙이지 않는다
                if (affiliate) StatusChip(stringResource(R.string.essentials_affiliate_label), Tokens.Ground, Tokens.InkSecondary)
            }
        }
        if (row.sourceName != null && r.lastVerified != null) SourceFooter(row.sourceName, displayDate(r.lastVerified))
    }
}
