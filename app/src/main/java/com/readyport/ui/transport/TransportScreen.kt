package com.readyport.ui.transport

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.autofill.SafeClipboard
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.PackRepository
import com.readyport.pack.TransportApp
import com.readyport.transport.Attempt
import com.readyport.transport.LinkType
import com.readyport.transport.Place
import com.readyport.transport.PlacesRepository
import com.readyport.transport.RideLinker
import com.readyport.trip.TripRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.StatusChip
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RideAppRow(val app: TransportApp, val installed: Boolean)

data class TransportUi(
    val places: List<Place> = emptyList(),
    val selected: Place? = null,
    val apps: List<RideAppRow> = emptyList(),
    val mapsInstalled: Boolean = false,
    /** 현지어 "이 주소로 가 주세요" (국가 팩 phrases id=address) */
    val driverPhrase: String? = null,
)

@HiltViewModel
class TransportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val places: PlacesRepository,
    private val packs: PackRepository,
    private val trips: TripRepository,
    private val settings: SettingsRepository,
    private val clipboard: SafeClipboard,
) : ViewModel() {

    val ui: StateFlow<TransportUi> = combine(places.places, places.selectedId, trips.trip, settings.settings, packs.revision) { list, sel, trip, s, _ ->
        // 지금 여행 나라 → 도움 탭에서 고른 나라 → 찜한 나라
        val country = trip?.country ?: s.helpCountry ?: s.favorites.firstOrNull()
        val pack = country?.let { packs.pack(it)?.value }
        TransportUi(
            places = list,
            selected = list.firstOrNull { it.id == sel } ?: list.lastOrNull(),
            apps = pack?.transportApps.orEmpty().filter { it.linkType != "maps_url" }
                .map { RideAppRow(it, RideLinker.isInstalled(context, it.`package`)) },
            mapsInstalled = RideLinker.isInstalled(context, MAPS_PKG),
            driverPhrase = pack?.phrases?.firstOrNull { it.id == "address" }?.local,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransportUi())

    fun add(name: String, address: String) = viewModelScope.launch { places.add(name, address) }
    fun select(id: String) = viewModelScope.launch { places.select(id) }

    /** 3단계 연결. 성공한 단계를 돌려준다 */
    fun open(app: TransportApp, installed: Boolean): Attempt? {
        val dest = ui.value.selected ?: return null
        val plan = RideLinker.plan(app.`package`, LinkType.of(app.linkType), installed, dest)
        return RideLinker.execute(context, plan) { clipboard.copy(app.name, it) }
    }

    fun openMaps(): Attempt? {
        val dest = ui.value.selected ?: return null
        val attempts = listOf(Attempt.OpenUri(RideLinker.mapsUrl(dest), null))
        return RideLinker.execute(context, attempts) { clipboard.copy("address", it) }
    }

    companion object {
        const val MAPS_PKG = "com.google.android.apps.maps"
    }
}

@Composable
fun TransportScreen(viewModel: TransportViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<Int?>(null) }
    TransportContent(
        ui = ui,
        message = message,
        onAdd = viewModel::add,
        onSelect = viewModel::select,
        onRide = { row ->
            message = when (viewModel.open(row.app, row.installed)) {
                is Attempt.LaunchApp -> R.string.move_ride_copied
                null -> R.string.move_ride_failed
                else -> null
            }
        },
        onMaps = { viewModel.openMaps() },
    )
}

@Composable
fun TransportContent(
    ui: TransportUi,
    message: Int?,
    onAdd: (String, String) -> Unit,
    onSelect: (String) -> Unit,
    onRide: (RideAppRow) -> Unit,
    onMaps: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var fullScreen by remember { mutableStateOf(false) }
    val dest = ui.selected
    val gap = LocalDimens.current.gap

    AppScreen(title = stringResource(R.string.move_title), speech = stringResource(R.string.move_speech)) {
        // 가는 곳 카드
        item(key = "dest") {
            InfoCard {
                Text(stringResource(R.string.move_destination), style = MaterialTheme.typography.labelLarge)
                if (dest == null || editing) {
                    if (dest == null) Text(stringResource(R.string.move_no_place), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.move_place_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(address, { address = it }, label = { Text(stringResource(R.string.move_place_address)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    PrimaryButton(stringResource(R.string.move_place_add), enabled = name.isNotBlank() && address.isNotBlank(), onClick = {
                        onAdd(name, address); name = ""; address = ""; editing = false
                    })
                } else {
                    Text(dest.name, style = MaterialTheme.typography.titleLarge)
                    Text(dest.addressLocal, style = MaterialTheme.typography.bodyLarge)
                    StatusChip(stringResource(R.string.move_place_saved), Tokens.SuccessBg, Tokens.SuccessText)
                    if (ui.places.size > 1) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ui.places.forEach { p ->
                                FilterChip(selected = p.id == dest.id, onClick = { onSelect(p.id) }, label = { Text(p.name) }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                    }
                    OutlinedButton(onClick = { editing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.move_place_change)) }
                }
            }
        }
        if (dest == null) return@AppScreen

        // 기사님께 보여주기 (남색, 인터넷 없이)
        item(key = "driver") {
            InfoCard(tone = CardTone.Navy) {
                Text(stringResource(R.string.move_show_driver), style = MaterialTheme.typography.labelLarge)
                ui.driverPhrase?.let { Text(it, fontSize = 28.sp, lineHeight = 36.sp) }
                Text(dest.addressLocal, style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { fullScreen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.move_full_screen), color = Tokens.Surface)
                }
            }
        }

        // 차 부르기: 앱별 3단계 연결
        item(key = "ride") {
            InfoCard {
                Text(stringResource(R.string.move_ride_title), style = MaterialTheme.typography.titleMedium)
                if (ui.apps.isEmpty()) Text(stringResource(R.string.move_no_apps), style = MaterialTheme.typography.bodyMedium)
                ui.apps.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.app.name, style = MaterialTheme.typography.titleMedium)
                        val label = when {
                            !row.installed -> R.string.move_ride_install
                            LinkType.of(row.app.linkType) == LinkType.OpenAndCopy || (LinkType.of(row.app.linkType) == LinkType.UberUrl && dest.lat == null) -> R.string.move_ride_open_copy
                            else -> R.string.move_ride_open_with_dest
                        }
                        OutlinedButton(onClick = { onRide(row) }, modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight)) {
                            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = Tokens.CautionText) }
                Text(stringResource(R.string.move_fare_note), style = MaterialTheme.typography.bodySmall)
            }
        }
        item(key = "transit") {
            InfoCard {
                Text(stringResource(R.string.move_transit_title), style = MaterialTheme.typography.titleMedium)
                PrimaryButton(stringResource(R.string.move_transit_button), onClick = onMaps)
            }
        }
        item(key = "space") { Column(Modifier.padding(bottom = gap)) {} }
    }

    if (fullScreen && dest != null) {
        Dialog(onDismissRequest = { fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier.fillMaxSize().background(Tokens.Surface).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ui.driverPhrase?.let { Text(it, fontSize = 44.sp, lineHeight = 56.sp, textAlign = TextAlign.Center, color = Tokens.Ink) }
                Text(dest.addressLocal, fontSize = 32.sp, lineHeight = 42.sp, textAlign = TextAlign.Center, color = Tokens.Ink)
                PrimaryButton(stringResource(R.string.help_close), onClick = { fullScreen = false })
            }
        }
    }
}
