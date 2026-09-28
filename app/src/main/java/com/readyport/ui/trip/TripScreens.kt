package com.readyport.ui.trip

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.IndexCountry
import com.readyport.pack.PackRepository
import com.readyport.pack.PackSync
import com.readyport.trip.Trip
import com.readyport.trip.TripNotifications
import com.readyport.trip.TripRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.TopicCard
import com.readyport.ui.theme.LocalDimens
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TripFormUi(val countries: List<IndexCountry> = emptyList(), val existing: Trip? = null, val loaded: Boolean = false)

@HiltViewModel
class TripViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trips: TripRepository,
    private val packs: PackRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(TripFormUi())
    val ui: StateFlow<TripFormUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val countries = packs.index()?.value?.countries.orEmpty().filter { it.pack }
            _ui.value = TripFormUi(countries, trips.current(), loaded = true)
        }
    }

    /** 여행 저장 + 나라 찜(안내 받아 두기) + 입국 카드 알림 예약 */
    fun save(country: String, start: LocalDate, end: LocalDate) = viewModelScope.launch {
        val old = trips.current()
        val keep = old?.takeIf { it.country == country && it.startDate == start.toString() }
        trips.save(
            Trip(
                country = country, startDate = start.toString(), endDate = end.toString(),
                arrivedAt = keep?.arrivedAt, arrivalDismissed = keep?.arrivalDismissed ?: false,
            ),
        )
        // 다른 여행으로 바뀌면 지난 준비물 체크·장바구니를 비운다
        if (old != null && keep == null && old.country != country) settings.clearTripLists()
        settings.setFavorite(country, true)
        PackSync.requestNow(context, settings.current().wifiOnly)
        val form = packs.pack(country)?.value?.forms?.firstOrNull()
        val days = form?.windowDaysIncludingArrival
        if (form != null && days != null) {
            TripNotifications.scheduleFormWindow(context, start.minusDays((days - 1).toLong()), form.nameKo)
        } else {
            TripNotifications.cancelFormWindow(context)
        }
    }

    fun delete() = viewModelScope.launch {
        trips.clear()
        TripNotifications.cancelFormWindow(context)
    }
}

@Composable
fun TripScreen(onDone: () -> Unit, viewModel: TripViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Android 13+ 알림 권한 (입국 카드 제출 가능일 알림)
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (!ui.loaded) return
    TripContent(
        ui = ui,
        onSave = { c, s, e ->
            viewModel.save(c, s, e)
            if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
            onDone()
        },
        onDelete = { viewModel.delete(); onDone() },
    )
}

@Composable
fun TripContent(
    ui: TripFormUi,
    onSave: (String, LocalDate, LocalDate) -> Unit,
    onDelete: () -> Unit,
) {
    var country by remember(ui.existing) { mutableStateOf(ui.existing?.country ?: ui.countries.singleOrNull()?.code) }
    var start by remember(ui.existing) { mutableStateOf(ui.existing?.startDate.orEmpty()) }
    var end by remember(ui.existing) { mutableStateOf(ui.existing?.endDate.orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    LaunchedEffect(start, end) { invalid = false }

    AppScreen(
        title = stringResource(if (ui.existing == null) R.string.trip_create_title else R.string.trip_edit_title),
        subtitle = stringResource(R.string.trip_create_body),
        speech = stringResource(R.string.trip_create_body),
    ) {
        if (ui.countries.isEmpty()) {
            item(key = "none") { TopicCard(stringResource(R.string.trip_no_country), null, tone = CardTone.Notice) }
            return@AppScreen
        }
        item(key = "country") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.trip_country), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.countries.forEach { c ->
                        FilterChip(
                            selected = country == c.code,
                            onClick = { country = c.code },
                            label = { Text(c.nameKo, style = MaterialTheme.typography.labelLarge) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
        item(key = "dates") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                DateField(R.string.trip_start, start) { start = it }
                DateField(R.string.trip_end, end) { end = it }
            }
        }
        if (invalid) item(key = "invalid") { TopicCard(stringResource(R.string.trip_invalid), null, tone = CardTone.Caution) }
        item(key = "save") {
            PrimaryButton(stringResource(R.string.trip_save), enabled = country != null, onClick = {
                val s = runCatching { LocalDate.parse(start.trim()) }.getOrNull()
                val e = runCatching { LocalDate.parse(end.trim()) }.getOrNull()
                if (country == null || s == null || e == null || e.isBefore(s)) invalid = true else onSave(country!!, s, e)
            })
        }
        if (ui.existing != null) {
            item(key = "delete") {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.trip_delete))
                }
            }
        }
    }
}

@Composable
private fun DateField(label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(R.string.trip_date_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}
