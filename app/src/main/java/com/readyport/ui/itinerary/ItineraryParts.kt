package com.readyport.ui.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.itinerary.DaySlot
import com.readyport.ui.attractions.SavedGoneTag
import com.readyport.ui.attractions.labelRes
import com.readyport.ui.attractions.mapsUrl
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.TileSpec
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.trip.monthDay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 줄 아래 글자 버튼 하나 — 화면 글은 짧게(`위로`), TalkBack 이름은 무엇을 옮기는지까지(`센소지 한 칸 위로`) */
data class RowAction(val label: String, val description: String, val icon: ImageVector, val onClick: () -> Unit)

/** `1일차 · 10월 8일 (목)` / 날짜가 없으면 `날짜 미정` */
@Composable
internal fun dayLabel(slot: DaySlot): String {
    val date = slot.date ?: return stringResource(R.string.itinerary_day_undated)
    return stringResource(R.string.itinerary_day, slot.index + 1, monthDay(date), weekday(date))
}

internal fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

/** 순서 번호 동그라미 (꾸밈 — 번호는 줄 이름이 말한다) */
@Composable
internal fun OrderNumber(n: Int, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    Box(
        modifier
            .sizeIn(minWidth = dimens.stepBadge, minHeight = dimens.stepBadge)
            .background(Tokens.TealSoft, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            n.toString(),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Tokens.TealText,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/**
 * 순서가 있는 관광지 한 줄 (찜 목록·관광 일정·오늘 갈 곳 공용).
 * 위: 번호 + 이름 + `지역 · 종류`(+ 상태 태그) — 누르면 상세. 아래: 글자 버튼 줄([actions], 위로·아래로·다른 날로·지도).
 * 버튼을 줄 오른쪽에 아이콘으로 몰지 않고 글자와 함께 아래에 둔다 — 쉬운 모드·큰 글자에서도 이름이 좁아지지 않는다.
 * TalkBack: 줄 = `1번째, 센소지, 도쿄`, 같은 동작을 사용자 지정 동작으로도.
 */
@Composable
internal fun OrderedPlaceRow(
    number: Int,
    stop: StopUi,
    onOpen: (() -> Unit)?,
    actions: List<RowAction>,
    modifier: Modifier = Modifier,
    extraTag: (@Composable () -> Unit)? = null,
    /** 줄 양옆 여백 — 목록 카드 안이면 행 여백, 이미 안쪽 여백이 있는 카드 안이면 0 */
    inset: Dp = LocalDimens.current.listRowPadding,
) {
    val dimens = LocalDimens.current
    val a = stop.attraction
    val category = a?.let { stringResource(it.category.labelRes()) }
    val place = listOfNotNull(stop.regionName, category).joinToString(" · ")
    val speech = stringResource(R.string.saved_order_place_cd, number, stop.name, place.ifBlank { stop.name })
    val openLabel = stringResource(R.string.attractions_open_detail)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onOpen != null) Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen) else Modifier)
                .semantics(mergeDescendants = true) {
                    contentDescription = speech
                    customActions = actions.map { act -> CustomAccessibilityAction(act.description) { act.onClick(); true } }
                }
                .padding(start = inset, end = inset, top = dimens.listRowPaddingVertical, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OrderNumber(number)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KoText(stop.name, MaterialTheme.typography.titleMedium, color = Tokens.Ink, glueShort = true)
                if (place.isNotBlank()) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        androidx.compose.material3.Icon(
                            Icons.Outlined.Place,
                            contentDescription = null,
                            tint = Tokens.TealText,
                            modifier = Modifier.padding(top = 2.dp).size(textIconSize(dimens.iconSmall, MaterialTheme.typography.bodyMedium)),
                        )
                        KoText(place, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                    }
                }
                val gone = stop.gone
                if (gone != null || extraTag != null) {
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (gone != null) SavedGoneTag(gone)
                        extraTag?.invoke()
                    }
                }
            }
        }
        if (actions.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = (inset - 8.dp).coerceAtLeast(0.dp), end = (inset - 8.dp).coerceAtLeast(0.dp), bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                actions.forEach { act ->
                    QuietButton(act.label, act.onClick, Modifier.semantics { contentDescription = act.description }, icon = act.icon)
                }
            }
        } else {
            Box(Modifier.padding(bottom = dimens.listRowPaddingVertical - 4.dp))
        }
    }
}

/** 위로·아래로 버튼(맨 위·맨 아래에서는 그쪽 버튼을 뺀다) */
@Composable
internal fun upDownActions(name: String, index: Int, count: Int, move: (Int) -> Unit): List<RowAction> = buildList {
    if (index > 0) {
        add(RowAction(stringResource(R.string.order_move_up), stringResource(R.string.order_move_up_cd, name), Icons.Outlined.KeyboardArrowUp) { move(-1) })
    }
    if (index < count - 1) {
        add(RowAction(stringResource(R.string.order_move_down), stringResource(R.string.order_move_down_cd, name), Icons.Outlined.KeyboardArrowDown) { move(1) })
    }
}

/** 하루에 먼 지역이 섞였을 때 부드러운 한 줄 (숫자 없음 — 결정 D7) */
@Composable
internal fun MixedFarNote(modifier: Modifier = Modifier) {
    IconBullet(stringResource(R.string.itinerary_mixed_far), Icons.Outlined.Route, modifier, tone = BadgeTone.Caution)
}

/**
 * 다른 날로 옮기기: 날 목록(라디오) + `일정에서 빼기`. 지우는 것이 아니라 일정에서만 빼므로(찜은 그대로) 확인을 한 번 더 묻지 않는다.
 */
@Composable
internal fun DayPickDialog(
    name: String,
    slots: List<DaySlot>,
    current: Int,
    onPick: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minTouch(), colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Accent)) {
                KoText(stringResource(R.string.itinerary_close), MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            val cd = stringResource(R.string.itinerary_remove_cd, name)
            TextButton(
                onClick = onRemove,
                modifier = Modifier.minTouch().semantics { contentDescription = cd },
                colors = ButtonDefaults.textButtonColors(contentColor = Tokens.DangerText),
            ) {
                androidx.compose.material3.Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                KoText(stringResource(R.string.itinerary_remove), MaterialTheme.typography.labelLarge)
            }
        },
        title = { KoText(stringResource(R.string.itinerary_pick_day_title, name), MaterialTheme.typography.titleLarge, glueShort = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                slots.forEach { slot -> PickRow(dayLabel(slot), slot.index == current) { onPick(slot.index) } }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = Tokens.Surface,
        titleContentColor = Tokens.Ink,
        textContentColor = Tokens.Ink,
    )
}

/** 대화상자 안 고르기 한 줄 (라디오) */
@Composable
internal fun PickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .minTouch()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Tokens.Accent, unselectedColor = Tokens.LineStrong))
        KoText(label, MaterialTheme.typography.bodyLarge, Modifier.weight(1f), color = Tokens.Ink)
    }
}

/** 내 여행 › 계획 단계의 `관광 일정` 타일 (곳 수는 보조 글) */
@Composable
internal fun itineraryTile(ui: ItineraryUi, onClick: () -> Unit): TileSpec = TileSpec(
    stringResource(R.string.itinerary_tile),
    Icons.Outlined.Route,
    onClick,
    supporting = when {
        ui.total > 0 && ui.fresh > 0 -> stringResource(R.string.itinerary_tile_count_new, ui.total, ui.fresh)
        ui.total > 0 -> stringResource(R.string.itinerary_tile_count, ui.total)
        ui.fresh > 0 -> stringResource(R.string.itinerary_tile_new, ui.fresh)
        else -> stringResource(R.string.itinerary_tile_empty)
    },
    tone = BadgeTone.Teal,
    illustration = com.readyport.ui.components.Illus.Travel,
)

/**
 * 여행 중 `오늘 갈 곳` (내 여행 › 7단계 여행 중): 오늘 칸의 곳을 순서대로 — 누르면 상세, 곳마다 `구글 지도에서 열기`
 * (공식 Google 지도 주소, 상세 화면과 같은 [mapsUrl]). 먼 지역이 섞였으면 부드러운 한 줄. 숫자(거리·시간) 없음.
 */
@Composable
internal fun TodayPlacesCard(
    ui: ItineraryUi,
    openDetail: (country: String, id: String) -> Unit,
    openLink: (String) -> Unit,
    openAll: () -> Unit,
) {
    val day = ui.today ?: return
    CardNewsCard(
        title = stringResource(R.string.itinerary_today_title),
        icon = Icons.Outlined.Place,
        eyebrow = dayLabel(day.slot),
        tone = BadgeTone.Teal,
    ) {
        if (day.stops.isEmpty()) {
            KoText(stringResource(R.string.itinerary_today_none), MaterialTheme.typography.bodyLarge, color = Tokens.InkSecondary)
        } else {
            Column {
                day.stops.forEachIndexed { i, stop ->
                    if (i > 0) androidx.compose.material3.HorizontalDivider(thickness = 1.dp, color = Tokens.Line)
                    val a = stop.attraction
                    val url = a?.let { mapsUrl(it) }
                    OrderedPlaceRow(
                        number = i + 1,
                        stop = stop,
                        onOpen = a?.let { { openDetail(it.country, it.id) } },
                        inset = 0.dp,
                        actions = listOfNotNull(
                            url?.let {
                                RowAction(stringResource(R.string.itinerary_open_map), stringResource(R.string.itinerary_open_map_cd, stop.name), Icons.Outlined.Map) { openLink(it) }
                            },
                        ),
                    )
                }
            }
            if (day.mixedFar) MixedFarNote()
        }
        QuietButton(stringResource(R.string.itinerary_open_all), openAll, icon = Icons.Outlined.Route)
    }
}

/** 상태 태그 줄(오늘) — 하루 머리에 */
@Composable
internal fun TodayTag() {
    StatusTag(stringResource(R.string.itinerary_today), StatusKind.Info)
}
