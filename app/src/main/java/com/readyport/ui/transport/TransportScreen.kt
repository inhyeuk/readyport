package com.readyport.ui.transport

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Hail
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.ListDivider
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.RowTrailing
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.sectionGap
import com.readyport.ui.pack.KeepAllText
import com.readyport.ui.pack.ShowLocalBody
import com.readyport.ui.pack.keepAll
import com.readyport.ui.pack.localText
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
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

/** 차 부르기 행의 상태 라벨: 설치 안 됨 / 목적지 넣어 열기 / 열고 주소 복사 (6-19 — 세 상태를 그대로 보인다) */
internal fun rideLabel(row: RideAppRow, dest: Place): Int {
    val type = LinkType.of(row.app.linkType)
    return when {
        !row.installed -> R.string.move_ride_install
        type == LinkType.OpenAndCopy || (type == LinkType.UberUrl && dest.lat == null) -> R.string.move_ride_open_copy
        else -> R.string.move_ride_open_with_dest
    }
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

    AppScreen(title = stringResource(R.string.move_title), speech = stringResource(R.string.move_speech)) {
        // ① 가는 곳
        item(key = "dest") {
            if (dest == null || editing) {
                CardNewsCard(
                    title = stringResource(R.string.move_destination),
                    icon = Icons.Outlined.EditLocationAlt,
                    body = if (dest == null) keepAll(stringResource(R.string.move_no_place)) else null,
                    tone = BadgeTone.Violet,
                ) {
                    OutlinedTextField(
                        name, { name = it },
                        label = { Text(keepAll(stringResource(R.string.move_place_name))) },
                        leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        address, { address = it },
                        label = { Text(keepAll(stringResource(R.string.move_place_address))) },
                        minLines = 2,
                        textStyle = localText(MaterialTheme.typography.bodyLarge),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        stringResource(R.string.move_place_add),
                        enabled = name.isNotBlank() && address.isNotBlank(),
                        icon = Icons.Outlined.Save,
                        onClick = { onAdd(name, address); name = ""; address = ""; editing = false },
                    )
                    if (dest != null) {
                        QuietButton(stringResource(R.string.action_cancel_keep), onClick = { editing = false })
                    }
                }
            } else {
                // 가는 곳 카드는 짧게(eyebrow·이름·저장됨·바꾸기): 현지어 주소는 바로 아래 기사님 카드(Navy)에 크게 한 번만 —
                // 같은 주소를 두 카드에 크기만 달리해 겹쳐 보이면 어느 쪽을 보여 줄지 위계가 흐려진다
                // (스펙 6-19 ①의 localMedium 주소 대신 — 검토 의견 반영, 운영자 확인 항목)
                CardNewsCard(
                    title = dest.name,
                    icon = Icons.Outlined.Place,
                    eyebrow = stringResource(R.string.move_destination),
                    tone = BadgeTone.Violet,
                ) {
                    StatusTag(keepAll(stringResource(R.string.move_place_saved)), StatusKind.Allowed)
                    if (ui.places.size > 1) {
                        // 여러 장소 중 하나 고르기 (한 개만 — Role.RadioButton + selectableGroup)
                        FlowRow(
                            Modifier.selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ui.places.forEach { p ->
                                SelectChip(
                                    selected = p.id == dest.id,
                                    onClick = { onSelect(p.id) },
                                    label = p.name,
                                    leadingIcon = Icons.Outlined.Place,
                                )
                            }
                        }
                    }
                    SecondaryButton(
                        stringResource(R.string.move_place_change),
                        onClick = { editing = true },
                        icon = Icons.Outlined.EditLocationAlt,
                        fillWidth = false,
                    )
                }
            }
        }
        if (dest == null) return@AppScreen

        // ② 기사님께 보여주기 (Navy, 인터넷 없이) — onDark 내용 세트만 (D18)
        item(key = "driver") { DriverCard(ui.driverPhrase, dest.addressLocal, onFullScreen = { fullScreen = true }) }

        // ③ 차 부르기: 앱마다 한 줄, 행 본문 = 지금 상태(설치 안 됨 / 목적지 넣어 열기 / 열고 주소 복사)
        sectionGap("gap-ride")
        item(key = "ride-title") {
            SectionHeader(stringResource(R.string.move_ride_title), icon = Icons.Outlined.LocalTaxi, tone = BadgeTone.Violet)
        }
        item(key = "ride") {
            if (ui.apps.isEmpty()) {
                NoticeBanner(keepAll(stringResource(R.string.move_no_apps)), icon = Icons.Outlined.Info)
            } else {
                ListGroup {
                    ui.apps.forEachIndexed { i, row ->
                        if (i > 0) ListDivider()
                        val maps = LinkType.of(row.app.linkType) == LinkType.MapsUrl
                        // 보이는 제목은 앱 이름, 본문은 지금 상태(설치 안 됨 / 목적지 넣어 열기 / 열고 주소 복사) — 6-19 ③.
                        // 설치 안 된 앱은 TalkBack 동작 이름만 `Grab 받기`(transport_get_app) — 보이는 글에 '받기'가 두 번 나오지 않게
                        val getApp = if (row.installed) null else stringResource(R.string.transport_get_app, row.app.name)
                        ListRow(
                            title = row.app.name,
                            icon = if (maps) Icons.Outlined.Map else Icons.Outlined.LocalTaxi,
                            tone = if (maps) BadgeTone.Teal else BadgeTone.Violet,
                            body = keepAll(stringResource(rideLabel(row, dest))),
                            trailing = RowTrailing.External,
                            onClick = { onRide(row) },
                            modifier = if (getApp == null) {
                                Modifier
                            } else {
                                Modifier.semantics {
                                    onClick(label = getApp) {
                                        onRide(row)
                                        true
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        message?.let { msg ->
            item(key = "ride-message") {
                // 앱을 열고 난 결과 (복사했어요 / 못 열었어요) — 바뀌면 TalkBack이 알린다
                Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    if (msg == R.string.move_ride_failed) {
                        NoticeBanner(keepAll(stringResource(msg)), icon = Icons.Outlined.ReportProblem, tone = BannerTone.Caution)
                    } else {
                        NoticeBanner(keepAll(stringResource(msg)), icon = Icons.Outlined.CheckCircle, tone = BannerTone.Success)
                    }
                }
            }
        }
        item(key = "fare-note") {
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Tokens.InkTertiary,
                    modifier = Modifier.padding(top = 1.dp).size(LocalDimens.current.iconSmall),
                )
                KeepAllText(stringResource(R.string.move_fare_note), MaterialTheme.typography.bodySmall, Tokens.InkTertiary)
            }
        }

        // ④ 지하철·버스
        sectionGap("gap-transit")
        item(key = "transit") {
            CardNewsCard(
                title = stringResource(R.string.move_transit_title),
                icon = Icons.Outlined.DirectionsSubway,
                tone = BadgeTone.Violet,
            ) {
                PrimaryButton(keepAll(stringResource(R.string.move_transit_button)), onClick = onMaps, icon = Icons.Outlined.Map)
            }
        }
    }

    if (fullScreen && dest != null) {
        DriverFullScreen(ui.driverPhrase, dest.addressLocal, onClose = { fullScreen = false })
    }
}

/** 기사님께 보여주기 카드: Hail + eyebrow(White85) → 현지어 문장(localMedium) → 주소 → 화면 가득 (투명 + 흰 테두리 버튼) */
@Composable
private fun DriverCard(phrase: String?, address: String, onFullScreen: () -> Unit) {
    val dimens = LocalDimens.current
    val extras = LocalTypeExtras.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Tokens.Navy, contentColor = OnDark.content),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBadge(Icons.Outlined.Hail, tone = BadgeTone.OnDark, size = dimens.iconBadgeSmall)
                Text(stringResource(R.string.move_show_driver), style = MaterialTheme.typography.labelMedium, color = OnDark.eyebrow)
            }
            phrase?.let { Text(it, style = extras.localMedium, color = OnDark.content) }
            Text(address, style = localText(MaterialTheme.typography.titleLarge), color = OnDark.content)
            SecondaryButton(
                keepAll(stringResource(R.string.move_full_screen)),
                onClick = onFullScreen,
                icon = Icons.Outlined.Fullscreen,
                fillWidth = false,
                onDark = true,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * 기사님께 보여 주는 전체 화면 내용: 기사님이 읽을 핵심인 주소를 가장 크게(localLarge), 부탁 문장은 그 위에 localMedium.
 * 둘 다 현지어 표시 역할(행간 1.5배) — 고정 sp 없음. 닫기는 아래 고정(긴 주소가 화면을 넘겨도 보인다).
 */
@Composable
internal fun DriverFullScreenBody(phrase: String?, address: String, onClose: () -> Unit) {
    val extras = LocalTypeExtras.current
    ShowLocalBody(onClose) {
        phrase?.let { Text(it, style = extras.localMedium, textAlign = TextAlign.Center, color = Tokens.InkSecondary) }
        Text(address, style = extras.localLarge, textAlign = TextAlign.Center, color = Tokens.Ink)
    }
}

/** 기사님께 보여 주는 전체 화면 */
@Composable
private fun DriverFullScreen(phrase: String?, address: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DriverFullScreenBody(phrase, address, onClose)
    }
}
