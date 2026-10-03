package com.readyport.ui.stay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Hail
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.stay.StayLinks
import com.readyport.stay.StayNote
import com.readyport.stay.StayNoteKind
import com.readyport.stay.StayType
import com.readyport.stay.Stays
import com.readyport.trip.Trip
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.ChecklistDivider
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.KoText
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.keepMonthDay
import com.readyport.ui.components.koDisplay
import com.readyport.ui.components.localText
import com.readyport.ui.components.minTouch
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.trip.tripDateRange
import com.readyport.vault.StayRecord
import java.time.LocalDate

// =====================================================================================
// 묵는 곳 카드 (2026-10-03) — 여행 화면의 예약·입국·여행 중 단계가 함께 쓴다.
// 상태 없는 부품이라 갤러리·접근성 점검이 그대로 띄운다. 지도는 공식 Google Maps URL만 쓴다([StayLinks]).
// =====================================================================================

/** 숙소 하나로 할 수 있는 일 — 화면(여행 화면)이 길을 넘겨 준다 */
data class StayActions(
    /** 숙소 고치기 (null이면 새 숙소) */
    val edit: (String?) -> Unit = {},
    /** 기사님께 보여 주기 — 그 숙소를 '가는 곳'으로 골라 두고 이동하기 화면으로 */
    val showToDriver: (String) -> Unit = {},
    /** 예약 서류에서 가져오기 */
    val import: () -> Unit = {},
)

@Composable
internal fun stayTypeLabel(type: String?): String? = when (StayType.of(type)) {
    StayType.Hotel -> stringResource(R.string.stay_type_hotel)
    StayType.GuestHouse -> stringResource(R.string.stay_type_guest_house)
    StayType.Hostel -> stringResource(R.string.stay_type_hostel)
    StayType.Apartment -> stringResource(R.string.stay_type_apartment)
    StayType.Friend -> stringResource(R.string.stay_type_friend)
    StayType.Other -> stringResource(R.string.stay_type_other)
    null -> null
}

/**
 * 숙소 종류 아이콘 — 입국 카드 선택지와 같은 그림([IconKeys.option]).
 * `그 밖의 숙소`는 정해진 그림이 없다(말줄임) — 호텔 그림을 돌려주면 두 선택지가 같은 그림이 된다.
 */
internal fun stayTypeIcon(type: String?) = IconKeys.option(type.orEmpty())
    ?: if (type == StayType.Other.key) Icons.Outlined.MoreHoriz else Icons.Outlined.Hotel

/**
 * 숙소 한 줄의 둘째 줄: `11월 3일 ~ 5일 · 2박 · 호텔` / `11월 3일부터` / `날짜를 아직 안 적었어요`.
 * 숙소 종류는 말로만 붙인다 — 줄마다 종류 아이콘을 또 그리면 카드 머리(묵는 곳)와 같은 그림이 겹친다.
 */
@Composable
internal fun stayDatesLabel(stay: StayRecord, withType: Boolean = false): String {
    val from = Stays.checkIn(stay)
    val to = Stays.checkOut(stay)
    val nights = Stays.nights(stay)
    val range = when {
        from != null && to != null -> tripDateRange(from, to)
        from != null -> stringResource(R.string.stay_dates_from, monthDay(from))
        to != null -> stringResource(R.string.stay_dates_until, monthDay(to))
        else -> stringResource(R.string.stay_dates_none)
    }
    val count = when {
        nights == null -> null
        nights == 0 -> stringResource(R.string.stay_nights_same_day)
        else -> stringResource(R.string.stay_nights, nights)
    }
    val type = if (withType) stayTypeLabel(stay.type) else null
    return listOfNotNull(range, count, type).joinToString(" · ")
}

@Composable
private fun monthDay(date: LocalDate): String = stringResource(R.string.today_date_md, date.monthValue, date.dayOfMonth)

/** 알림 한 줄 (막지 않는 안내) */
@Composable
internal fun stayNoteText(note: StayNote): String = when (note.kind) {
    StayNoteKind.MissingDates -> stringResource(R.string.stay_note_missing_dates)
    StayNoteKind.Gap -> stringResource(
        R.string.stay_note_gap,
        note.from?.let { monthDay(it) }.orEmpty(),
        note.to?.let { monthDay(it) }.orEmpty(),
    )
    StayNoteKind.Overlap -> stringResource(R.string.stay_note_overlap)
    StayNoteKind.ArrivalMissing -> stringResource(R.string.stay_note_arrival_missing, note.from?.let { monthDay(it) }.orEmpty())
    StayNoteKind.Outside -> stringResource(R.string.stay_note_outside)
}

/**
 * 예약 단계의 `묵는 곳` 카드: 날짜 순으로 숙소 한 줄씩(이름 · 날짜 범위와 몇 박 · 주소 한 줄 · 지도 버튼) →
 * 부드러운 알림(빈 날·겹침 등) → `숙소 추가` → `예약 서류에서 가져오기`.
 * 숙소가 없으면 무엇에 쓰는지 한 줄로 알려 주고 바로 넣을 수 있게 한다.
 */
@Composable
fun StaysCard(stays: List<StayRecord>, notes: List<StayNote>, actions: StayActions) {
    CardNewsCard(
        title = stringResource(R.string.stays_title),
        icon = Icons.Outlined.Hotel,
        body = if (stays.isEmpty()) stringResource(R.string.stays_card_body) else null,
    ) {
        if (stays.isEmpty()) {
            IconBullet(stringResource(R.string.stays_empty), Icons.Outlined.Info)
        } else {
            stays.forEachIndexed { i, stay ->
                if (i > 0) ChecklistDivider()
                StayRow(stay, actions)
            }
        }
        notes.forEach { note ->
            IconBullet(stayNoteText(note), Icons.Outlined.Info, tone = BadgeTone.Caution)
        }
        SecondaryButton(stringResource(R.string.stays_add), onClick = { actions.edit(null) }, icon = Icons.Outlined.Add)
        QuietButton(stringResource(R.string.stays_import), onClick = actions.import, icon = IconKeys.bookingKind("other"))
    }
}

/**
 * 숙소 한 줄: 누르면 고치기(행 전체가 버튼, TalkBack 이름은 `사쿠라 호텔 고치기`) + 아래 지도·기사님께 보여 주기.
 * 주소는 현지 글자일 수 있어 행간을 넓힌 표시 글꼴([localText])로 그린다.
 */
@Composable
private fun StayRow(stay: StayRecord, actions: StayActions) {
    val dimens = LocalDimens.current
    val dates = stayDatesLabel(stay, withType = true)
    val address = stay.addressLocal.trim()
    val addressShown = address.ifEmpty { stringResource(R.string.stay_address_none) }
    val rowCd = stringResource(R.string.stay_row_cd, stay.name, dates, addressShown)
    val editName = stringResource(R.string.stay_edit_target, stay.name)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
        Row(
            Modifier
                .fillMaxWidth()
                .minTouch()
                .clickable(role = Role.Button, onClickLabel = editName) { actions.edit(stay.id) }
                .semantics(mergeDescendants = true) { contentDescription = rowCd }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 줄 표시는 '가는 곳'과 같은 장소 핀 — 숙소 종류는 글로 말한다(카드 머리의 묵는 곳 그림과 겹치지 않게)
            IconBadge(Icons.Outlined.Place, tone = BadgeTone.Neutral, size = dimens.iconBadgeSmall)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KoText(stay.name, MaterialTheme.typography.titleMedium, color = Tokens.Ink, glueShort = true)
                KoText(
                    dates,
                    MaterialTheme.typography.bodyMedium,
                    color = Tokens.InkSecondary,
                    display = koDisplay(keepMonthDay(dates), glueShort = true),
                )
                if (address.isEmpty()) {
                    KoText(addressShown, MaterialTheme.typography.bodyMedium, color = Tokens.InkTertiary)
                } else {
                    Text(address, style = localText(MaterialTheme.typography.bodyMedium), color = Tokens.InkSecondary)
                }
                stay.addressKo?.takeIf { it.isNotBlank() }?.let {
                    KoText(it, MaterialTheme.typography.bodySmall, color = Tokens.InkTertiary)
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.NavigateNext,
                contentDescription = null,
                tint = Tokens.InkTertiary,
                modifier = Modifier.padding(top = 4.dp).size(dimens.icon),
            )
        }
        StayLinkButtons(stay, actions)
    }
}

/** 지도에서 보기 · 기사님께 보여 주기 (주소·좌표가 있을 때만) */
@Composable
private fun StayLinkButtons(stay: StayRecord, actions: StayActions) {
    val context = LocalContext.current
    val mapsCd = stringResource(R.string.stay_open_maps_cd, stay.name)
    val driverCd = stringResource(R.string.stay_show_driver_cd, stay.name)
    val hasMaps = Stays.searchUrl(stay) != null
    val hasAddress = stay.addressLocal.isNotBlank()
    if (!hasMaps && !hasAddress) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (hasMaps) {
            QuietButton(
                stringResource(R.string.stay_open_maps),
                onClick = { StayLinks.openSearch(context, stay) },
                icon = Icons.Outlined.Map,
                modifier = Modifier.semantics { contentDescription = mapsCd },
            )
        }
        if (hasAddress) {
            QuietButton(
                stringResource(R.string.stay_show_driver),
                onClick = { actions.showToDriver(stay.id) },
                icon = Icons.Outlined.Hail,
                modifier = Modifier.semantics { contentDescription = driverCd },
            )
        }
    }
}

/**
 * 입국·여행 중 단계의 한 숙소 카드: 공항에서·여행 중에 필요한 것만 — 이름 · 날짜 · 주소(크게) · 지도 · 기사님께 보여 주기.
 * [titleRes]는 `도착한 날 묵는 곳`·`오늘 묵는 곳`, [stay]가 없으면 왜 비었는지 한 줄과 `숙소 추가`.
 */
@Composable
fun StayHereCard(titleRes: Int, emptyRes: Int, stay: StayRecord?, actions: StayActions) {
    CardNewsCard(
        // 제목이 이미 `도착한 날 묵는 곳`이라 eyebrow에 `묵는 곳`을 또 적지 않는다
        title = stringResource(titleRes),
        icon = Icons.Outlined.Hotel,
        body = if (stay == null) stringResource(emptyRes) else null,
    ) {
        if (stay == null) {
            SecondaryButton(stringResource(R.string.stays_add), onClick = { actions.edit(null) }, icon = Icons.Outlined.Add)
            return@CardNewsCard
        }
        val dates = stayDatesLabel(stay, withType = true)
        KoText(stay.name, MaterialTheme.typography.titleMedium, color = Tokens.Ink, glueShort = true)
        KoText(
            dates,
            MaterialTheme.typography.bodyMedium,
            color = Tokens.InkSecondary,
            display = koDisplay(keepMonthDay(dates), glueShort = true),
        )
        val address = stay.addressLocal.trim()
        if (address.isEmpty()) {
            IconBullet(stringResource(R.string.stay_address_none), Icons.Outlined.Info)
        } else {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Outlined.Place,
                    contentDescription = null,
                    tint = Tokens.Accent,
                    modifier = Modifier.padding(top = 4.dp).size(LocalDimens.current.iconSmall),
                )
                Box(Modifier.weight(1f)) {
                    Text(address, style = localText(MaterialTheme.typography.titleMedium), color = Tokens.Ink)
                }
            }
        }
        stay.addressKo?.takeIf { it.isNotBlank() }?.let {
            KoText(it, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        }
        StayLinkButtons(stay, actions)
    }
}
