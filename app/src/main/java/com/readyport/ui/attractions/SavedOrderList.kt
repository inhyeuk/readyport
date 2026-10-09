package com.readyport.ui.attractions

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.readyport.R
import com.readyport.attractions.AdvisoryState
import com.readyport.attractions.AttractionsCatalog
import com.readyport.attractions.Category
import com.readyport.attractions.SavedAttraction
import com.readyport.trip.Trip
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.ChoiceDialog
import com.readyport.ui.components.KoText
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.minTouch
import com.readyport.ui.itinerary.ItineraryModel
import com.readyport.ui.itinerary.OrderedPlaceRow
import com.readyport.ui.itinerary.PickRow
import com.readyport.ui.itinerary.StopUi
import com.readyport.ui.itinerary.upDownActions
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.trip.tripDateRange

// =====================================================================================
// 찜한 관광지 — 사람이 정한 순서 (2026-10-09 사장님 요청: *"찜한 장소는 소팅 순서를 바꿀 수 있도록 해서 관광 순서로"*)
//
// 찜 목록(검색어 없이 '찜한 곳만')은 지역 묶음 대신 **찜 순서 한 줄**로 보인다. 줄마다 지역 이름이 함께 보이고,
// 위·아래 버튼(쉬운 모드에서도 그대로, TalkBack '센소지 한 칸 위로')과 꾹 눌러 끌기로 순서를 바꾼다.
// 순서는 SavedAttractionsRepository가 기기 안에 저장하고([SavedAttractionsRepository.orderOf]), 지도 핀 번호도 같은 순서를 쓴다.
// 맨 위 `여행 일정에 담기`는 이 순서 그대로 여행의 관광 일정에 날짜별로 나눠 담는다(ui/itinerary).
// =====================================================================================

/** 찜 순서 줄 만들기(순수 계산) */
object SavedOrderModel {
    /**
     * 이 나라 찜을 찜 순서대로. [category]가 있으면 그 종류만(정보가 없는 찜은 종류를 몰라 뺀다).
     * 정보가 없는 찜(문 닫음·앱 업데이트 필요 등)도 자리를 지키며 이유 태그와 함께 보인다.
     */
    fun rows(
        catalog: AttractionsCatalog?,
        saved: List<SavedAttraction>,
        country: String,
        category: Category?,
        advisory: AdvisoryState,
    ): List<StopUi> = saved.filter { it.country == country }
        .map { ItineraryModel.resolve(it.key, catalog, advisory) }
        .filter { category == null || it.attraction?.category == category }
}

/** 찜 목록(순서 모드)에서 하는 일 */
data class SavedOrderActions(
    val openDetail: (id: String) -> Unit = {},
    /** 정보가 없는 찜을 눌렀을 때(찜 취소 확인) */
    val onGone: (key: String) -> Unit = {},
    val move: (key: String, by: Int) -> Unit = { _, _ -> },
    val moveTo: (key: String, index: Int) -> Unit = { _, _ -> },
    val openItinerary: (tripId: String) -> Unit = {},
    val makeTrip: () -> Unit = {},
)

/** 찜 목록 순서 모드의 목록 칸: `여행 일정에 담기` + 안내 한 줄 → 순서 카드 */
internal fun LazyListScope.savedOrderItems(
    rows: List<StopUi>,
    reorderable: Boolean,
    countryName: String,
    trips: List<Trip>,
    merged: Set<String>,
    actions: SavedOrderActions,
) {
    if (rows.isEmpty()) return
    item(key = "saved-order-tools") {
        Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner)) {
            AddToTripButton(countryName, trips, actions.openItinerary, actions.makeTrip)
            KoText(
                stringResource(if (reorderable) R.string.saved_order_hint else R.string.saved_order_hint_filtered),
                MaterialTheme.typography.bodyMedium,
                color = Tokens.InkSecondary,
            )
        }
    }
    item(key = "saved-order") { SavedOrderCard(rows, reorderable, merged, actions) }
}

/**
 * 찜 순서 카드: 줄마다 번호 + 이름 + `지역 · 종류` + 위로·아래로. 꾹 눌러 끌면 줄이 손가락을 따라오고
 * 이웃 줄의 반을 넘으면 자리를 바꾼다 — 손을 떼면 그 자리로 저장한다(버튼과 같은 저장소 함수).
 */
@Composable
internal fun SavedOrderCard(rows: List<StopUi>, reorderable: Boolean, merged: Set<String>, actions: SavedOrderActions) {
    val keys = rows.map { it.key }
    val order = remember(keys) { keys.toMutableStateList() }
    val byKey = rows.associateBy { it.key }
    val heights = remember { mutableStateMapOf<String, Int>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var startIndex by remember { mutableIntStateOf(-1) }
    val draggingLabel = stringResource(R.string.saved_order_dragging)
    val shape = MaterialTheme.shapes.large
    Surface(color = Tokens.Surface, shape = shape, modifier = Modifier.fillMaxWidth().cardShadow(shape)) {
        Column {
            order.forEachIndexed { i, k ->
                val stop = byKey[k] ?: return@forEachIndexed
                key(k) {
                    if (i > 0) HorizontalDivider(thickness = 1.dp, color = Tokens.Line)
                    val lifted = dragging == k
                    val drag = if (reorderable) {
                        Modifier.pointerInput(k) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = k
                                    offset = 0f
                                    startIndex = order.indexOf(k)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    val at = order.indexOf(k)
                                    if (offset > 0 && at < order.lastIndex) {
                                        val h = heights[order[at + 1]] ?: 0
                                        if (h > 0 && offset > h / 2f) {
                                            order.removeAt(at)
                                            order.add(at + 1, k)
                                            offset -= h
                                        }
                                    } else if (offset < 0 && at > 0) {
                                        val h = heights[order[at - 1]] ?: 0
                                        if (h > 0 && -offset > h / 2f) {
                                            order.removeAt(at)
                                            order.add(at - 1, k)
                                            offset += h
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val at = order.indexOf(k)
                                    dragging = null
                                    offset = 0f
                                    if (at >= 0 && at != startIndex) actions.moveTo(k, at)
                                },
                                onDragCancel = {
                                    dragging = null
                                    offset = 0f
                                    order.clear()
                                    order.addAll(keys)
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                    OrderedPlaceRow(
                        number = i + 1,
                        stop = stop,
                        onOpen = { stop.attraction?.let { actions.openDetail(it.id) } ?: actions.onGone(stop.key) },
                        actions = if (reorderable) upDownActions(stop.name, i, order.size) { by -> actions.move(stop.key, by) } else emptyList(),
                        extraTag = if (stop.key in merged) {
                            { StatusTag(stringResource(R.string.attractions_merged), StatusKind.Info) }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .onSizeChanged { heights[k] = it.height }
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { translationY = if (lifted) offset else 0f }
                            .then(if (lifted) Modifier.background(Tokens.TealSoft).semantics { stateDescription = draggingLabel } else Modifier)
                            .then(drag),
                    )
                }
            }
        }
    }
}

/**
 * `여행 일정에 담기`: 이 나라 여행이 하나면 바로 그 여행의 관광 일정(옮겨 담기 제안)으로, 여럿이면 고르고,
 * 없으면 '여행이 아직 없어요' + `여행 만들기`.
 */
@Composable
internal fun AddToTripButton(countryName: String, trips: List<Trip>, openItinerary: (String) -> Unit, makeTrip: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    SecondaryButton(
        stringResource(R.string.saved_add_to_trip),
        onClick = { if (trips.size == 1) openItinerary(trips.single().id) else asking = true },
        icon = Icons.Outlined.Route,
        tone = BadgeTone.Teal,
    )
    if (!asking) return
    if (trips.isEmpty()) {
        ChoiceDialog(
            title = stringResource(R.string.saved_no_trip_title, countryName),
            body = stringResource(R.string.saved_no_trip_body),
            first = stringResource(R.string.saved_no_trip_make),
            onFirst = { asking = false; makeTrip() },
            second = stringResource(R.string.saved_no_trip_later),
            onSecond = { asking = false },
            onDismiss = { asking = false },
            icon = Icons.Outlined.Luggage,
        )
    } else {
        AlertDialog(
            onDismissRequest = { asking = false },
            confirmButton = {
                TextButton(onClick = { asking = false }, modifier = Modifier.minTouch(), colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Accent)) {
                    KoText(stringResource(R.string.itinerary_close), MaterialTheme.typography.labelLarge)
                }
            },
            title = { KoText(stringResource(R.string.saved_pick_trip_title), MaterialTheme.typography.titleLarge, glueShort = true) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    trips.forEach { t ->
                        val dates = if (t.datesValid) tripDateRange(t.start, t.end) else stringResource(R.string.itinerary_day_undated)
                        PickRow(stringResource(R.string.saved_pick_trip_row, countryName) + " · " + dates, selected = false) {
                            asking = false
                            openItinerary(t.id)
                        }
                    }
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = Tokens.Surface,
            titleContentColor = Tokens.Ink,
            textContentColor = Tokens.Ink,
        )
    }
}
