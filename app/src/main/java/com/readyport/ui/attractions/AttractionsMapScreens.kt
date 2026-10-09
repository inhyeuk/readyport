package com.readyport.ui.attractions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.readyport.R
import com.readyport.attractions.Attraction
import com.readyport.attractions.Category
import com.readyport.attractions.Region
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 상세: 위치 카드 =======================

/**
 * 상세 '위치' 카드 (제목·칩 바로 아래). 앱 안 지도(라이트 모드 정지 지도 + 표시 하나) → 누르면 큰 지도.
 * 인터넷이 없으면 안내 + '구글 지도에서 열기'. 지도를 못 그리는 경우([MapMode.Hidden])에는 아무것도 그리지 않는다 —
 * 가는 법 카드의 '구글 지도에서 열기'가 그대로 있다.
 */
@Composable
fun AttractionMapSection(a: Attraction, regionName: String, categoryName: String, openLink: (String) -> Unit) {
    val mode = rememberAttractionMapMode(a)
    if (mode == MapMode.Hidden) return
    val dimens = LocalDimens.current
    var full by rememberSaveable(a.key) { mutableStateOf(false) }
    val pin = remember(a, regionName, categoryName) { AttractionsMapPolicy.detailPin(a, regionName, categoryName) } ?: return
    CardNewsCard(title = stringResource(R.string.attractions_map_title), icon = Icons.Outlined.Map, tone = BadgeTone.Teal) {
        when (mode) {
            MapMode.InApp -> {
                val expandCd = stringResource(R.string.attractions_map_expand_cd, a.title)
                val spec = MapSpec(listOf(pin), lite = true, contentDescription = stringResource(R.string.attractions_map_cd, a.title))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (dimens.easyMode) 200.dp else 168.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Tokens.SurfaceSunken),
                ) {
                    LocalPlaceMapRenderer.current.Render(spec, onPinClick = { full = true }, onMapClick = { full = true }, modifier = Modifier.fillMaxSize())
                    // 지도 위를 덮는 누름 칸 — 라이트 지도는 손으로 움직이지 않으니 어디를 눌러도 큰 지도로 (TalkBack: 버튼 하나)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(role = Role.Button, onClick = { full = true })
                            .semantics { contentDescription = expandCd },
                    )
                }
                SecondaryButton(stringResource(R.string.attractions_map_expand), onClick = { full = true }, icon = Icons.Outlined.OpenInFull, tone = BadgeTone.Teal)
            }
            else -> {
                IconBullet(stringResource(R.string.attractions_map_offline), Icons.Outlined.CloudOff)
                mapsUrl(a)?.let { url ->
                    SecondaryButton(stringResource(R.string.attractions_open_map), onClick = { openLink(url) }, icon = Icons.Outlined.Map, tone = BadgeTone.Teal)
                }
            }
        }
    }
    if (full && mode == MapMode.InApp) {
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            DetailMapPanel(a, pin, onDismiss = { full = false }, openLink = openLink, modifier = Modifier.fillMaxSize())
        }
    }
}

/** 상세의 큰 지도: 표시 하나 + 아래 카드(이름 · 지역·종류 · '구글 지도 앱에서 길찾기') */
@Composable
fun DetailMapPanel(a: Attraction, pin: MapPin, onDismiss: () -> Unit, openLink: (String) -> Unit, modifier: Modifier = Modifier) {
    PlaceMapPanel(a.title, listOf(pin), pin.id, onSelect = {}, onDismiss = onDismiss, hint = null, legend = emptyList(), modifier = modifier) {
        PlaceCard(pin) {
            AttractionsMapPolicy.directionsUrl(a)?.let { url ->
                PrimaryButton(stringResource(R.string.attractions_map_directions), onClick = { openLink(url) }, icon = Icons.Outlined.Directions)
            }
        }
    }
}

/** 이 관광지의 지도 칸 모양 — 상세가 칸을 둘지 정할 때 */
@Composable
fun rememberAttractionMapMode(a: Attraction): MapMode = AttractionsMapPolicy.mode(rememberMapEnv(), a.country, AttractionsMapPolicy.hasCoords(a))

// ======================= 목록: 지도로 보기 =======================

/** 목록 '지도로 보기'를 둘지 — 지금 보이는 곳 가운데 지도에 그릴 수 있는 곳이 있고 앱 안 지도를 쓸 수 있을 때만 [MapMode.InApp] */
@Composable
fun rememberListMapMode(ui: AttractionsListUi): MapMode =
    AttractionsMapPolicy.listMode(rememberMapEnv(), ui.country, ui.content?.groups.orEmpty().flatMap { it.places })

/** 목록 '지도로 보기' 버튼 */
@Composable
fun AttractionsMapButton(onClick: () -> Unit) {
    SecondaryButton(stringResource(R.string.attractions_map_show), onClick = onClick, icon = Icons.Outlined.Map, tone = BadgeTone.Teal)
}

/**
 * 목록의 큰 지도: 지금 목록에 보이는 곳(검색·종류·찜 거르기 그대로)을 표시로. 지역마다 색, 찜한 곳만 보기면 찜 순서 번호.
 * 표시를 누르면 아래 카드에 이름 → '자세히 보기'로 상세. 상세에서 돌아오면 지도가 다시 열린다(부르는 쪽이 rememberSaveable로 연 상태를 둔다).
 */
@Composable
fun AttractionsListMapDialog(ui: AttractionsListUi, onDismiss: () -> Unit, openDetail: (String) -> Unit) {
    val catalog = ui.catalog ?: return
    val pins = rememberListPins(ui)
    if (pins.isEmpty()) return
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    // 상세로 넘어가는 동안에는 대화상자를 내린다(넘어가는 화면 위에 겹치지 않게). 돌아오면 새로 그려져 다시 보인다
    var leaving by remember { mutableStateOf(false) }
    if (leaving) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ListMapPanel(
            ui, pins, catalog.regions, selected, onSelect = { selected = it }, onDismiss = onDismiss,
            openDetail = { id ->
                leaving = true
                openDetail(id)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 목록 지도의 내용 (테스트가 대화상자 창 없이 캡처한다) */
@Composable
fun ListMapPanel(
    ui: AttractionsListUi,
    pins: List<MapPin>,
    regions: List<Region>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    openDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (ui.savedOnly) stringResource(R.string.attractions_map_saved_title) else stringResource(R.string.attractions_map_list_title, ui.countryName)
    PlaceMapPanel(
        title = title,
        pins = pins,
        selectedId = selected,
        onSelect = onSelect,
        onDismiss = onDismiss,
        hint = if (ui.savedOnly) stringResource(R.string.attractions_map_saved_hint) else null,
        legend = if (ui.savedOnly) emptyList() else AttractionsMapPolicy.legend(pins, regions),
        modifier = modifier,
    ) {
        val pin = pins.firstOrNull { it.id == selected }
        if (pin == null) {
            IconBullet(stringResource(R.string.attractions_map_tap_hint), Icons.Outlined.Info)
        } else {
            PlaceCard(pin) {
                PrimaryButton(stringResource(R.string.attractions_map_open_detail), onClick = { openDetail(pin.id) })
            }
        }
    }
}

/** 목록에 보이는 곳 → 지도 표시 (찜한 곳만 보기면 찜 순서 번호) */
@Composable
fun rememberListPins(ui: AttractionsListUi): List<MapPin> {
    val catalog = ui.catalog ?: return emptyList()
    val places = ui.content?.groups.orEmpty().flatMap { it.places }
    val categoryNames = Category.entries.associateWith { stringResource(it.labelRes()) }
    val savedOrder = if (ui.savedOnly) AttractionsMapPolicy.savedOrder(ui.savedKeys, ui.country) else null
    return remember(places, savedOrder, catalog) {
        AttractionsMapPolicy.pins(
            places, catalog.regions,
            regionName = { id -> catalog.region(id)?.nameKo.orEmpty() },
            categoryName = { a -> categoryNames.getValue(a.category) },
            savedOrder = savedOrder,
        )
    }
}

// ======================= 공용: 화면 가득 지도 =======================

/**
 * 화면 가득 지도 내용: 머리(제목·닫기) → (지역 색 범례) → 지도 → 아래 칸([bottom]). 대화상자 창(뒤로 가기로 닫힘) 안에 둔다.
 * 위치 권한·내 위치 표시 없음.
 */
@Composable
fun PlaceMapPanel(
    title: String,
    pins: List<MapPin>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    hint: String?,
    legend: List<MapLegend>,
    modifier: Modifier = Modifier,
    bottom: @Composable () -> Unit,
) {
    val dimens = LocalDimens.current
    Surface(modifier, color = Tokens.Ground) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = dimens.screenPadding, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    KoText(title, MaterialTheme.typography.titleLarge, color = Tokens.Ink, heading = true, glueShort = true)
                    if (hint != null) KoText(hint, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                }
                val closeCd = stringResource(R.string.attractions_map_close)
                IconButton(onClick = onDismiss, modifier = Modifier.minTouchSize().semantics { contentDescription = closeCd }) {
                    Icon(Icons.Outlined.Close, contentDescription = null, tint = Tokens.Ink)
                }
            }
            if (legend.size > 1) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = dimens.screenPadding).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    legend.forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(12.dp).background(hueColor(l.hue), CircleShape))
                            KoText(l.name, MaterialTheme.typography.labelLarge, color = Tokens.InkSecondary)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(Tokens.SurfaceSunken)) {
                val spec = MapSpec(pins, lite = false, contentDescription = title, selectedId = selectedId)
                LocalPlaceMapRenderer.current.Render(spec, onPinClick = { onSelect(it) }, onMapClick = { onSelect(null) }, modifier = Modifier.fillMaxSize())
            }
            Surface(color = Tokens.Surface, modifier = Modifier.fillMaxWidth().cardShadow(MaterialTheme.shapes.large, border = false)) {
                Column(
                    Modifier.fillMaxWidth().padding(dimens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(dimens.inner),
                ) { bottom() }
            }
        }
    }
}

/** 지도 아래 장소 카드: (번호) 이름 · 지역·종류 + 동작 버튼 */
@Composable
private fun PlaceCard(pin: MapPin, action: @Composable () -> Unit) {
    val dimens = LocalDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pin.number?.let { n ->
                val cd = stringResource(R.string.attractions_map_selected_number, n)
                Box(Modifier.semantics { contentDescription = cd }) { NumberPin(n, selected = false) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KoText(pin.title, MaterialTheme.typography.titleMedium, color = Tokens.Ink, glueShort = true)
                if (pin.subtitle.isNotBlank()) KoText(pin.subtitle, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
            }
        }
        action()
    }
}
