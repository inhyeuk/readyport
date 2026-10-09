package com.readyport.ui.attractions

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Subway
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.attractions.AccessMode
import com.readyport.attractions.AdvisoryLevel
import com.readyport.attractions.Attraction
import com.readyport.attractions.Booking
import com.readyport.attractions.ClosedDay
import com.readyport.attractions.Entry
import com.readyport.attractions.OpenStatus
import com.readyport.attractions.RegionKind
import com.readyport.attractions.Risk
import com.readyport.attractions.Tag
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.DotBullet
import com.readyport.ui.components.EmptyState
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IllusImage
import com.readyport.ui.components.IllusTones
import com.readyport.ui.components.InfoChip
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LinkRow
import com.readyport.ui.components.ListDivider
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SourceList
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.illusPanelBrush
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.resolveSourceName
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import java.net.URLEncoder

data class AttractionDetailActions(
    val setSaved: (Boolean) -> Unit = {},
    val dismissFirstNotice: () -> Unit = {},
    val openOther: (id: String) -> Unit = {},
    /** '{지역}의 다른 곳 모두 보기' */
    val openRegion: (regionId: String) -> Unit = {},
    val openSafety: () -> Unit = {},
    val openLink: (String) -> Unit = {},
    /** 내 별점 남기기·바꾸기(1~5) · 지우기 · 나이 확인 */
    val vote: (Int) -> Unit = {},
    val removeVote: () -> Unit = {},
    val checkAge: () -> Unit = {},
)

@Composable
fun AttractionDetailScreen(
    openOther: (String) -> Unit,
    openRegion: (String) -> Unit,
    openSafety: (String) -> Unit,
    viewModel: AttractionDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val auth = com.readyport.ui.wallet.rememberDeviceAuth()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { viewModel.refreshMine() }
    AttractionDetailContent(
        ui = ui,
        actions = AttractionDetailActions(
            setSaved = { viewModel.setSaved(it) },
            dismissFirstNotice = { viewModel.dismissFirstNotice() },
            openOther = openOther,
            openRegion = openRegion,
            openSafety = { openSafety(ui.country) },
            openLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
            vote = viewModel::vote,
            removeVote = viewModel::removeVote,
            checkAge = { auth { viewModel.checkAge() } },
        ),
    )
}

/** 지도에서 보기: 공식 Google 지도 URL(숙소와 같은 형식). CN은 좌표 대신 이름으로 찾는다(좌표계 차이, §6.4) */
fun mapsUrl(a: Attraction): String? {
    val query = if (a.country != "CN" && a.lat != null && a.lng != null) {
        "${a.lat},${a.lng}"
    } else {
        a.localShort ?: a.local ?: a.nameEn.ifBlank { null } ?: return null
    }
    return "https://www.google.com/maps/search/?api=1&query=${URLEncoder.encode(query, "UTF-8")}"
}

/**
 * 관광지 상세 (SPEC_v5 §6.4, D3-A 사진 없음 · D4-A 숫자 없음). 순서 고정:
 * 제목 → (경보 변경 안내) → 2단계 띠 → 운영 상태 → (확인 중 띠) → 위험 → 그림(사진 칸) → 칩 → 위치 지도 → 하루 다녀오는 곳 → 어떤 곳이에요 → 위키백과에서 보기 → 가기 전에 알아 둘 것 →
 * 가는 법 → 평점(Google · 레디포트 이용자 · 내 별점) → 찜 버튼(+처음 안내) → 같은 지역의 다른 곳 → 공식 사이트·사진 링크 → 최종 확인·출처.
 */
@Composable
fun AttractionDetailContent(ui: AttractionDetailUi, actions: AttractionDetailActions) {
    val a = ui.attraction
    val dimens = LocalDimens.current
    val single = rememberGridColumns() == 1
    if (a == null) {
        AppScreen(title = ui.countryName, speech = ui.countryName) {
            if (!ui.loading) {
                item(key = "missing") { EmptyState(Icons.Outlined.Info, stringResource(R.string.attractions_not_found), null) }
            }
        }
        return
    }
    val region = ui.region
    val fallback = stringResource(R.string.source_official_fallback)
    val sourceOf = { id: String, date: String -> SourceRef(resolveSourceName(id, ui.sourceNames, fallback), displayDate(date)) }
    val speech = stringResource(R.string.attractions_detail_speech, a.title, (listOf(a.summaryKo) + a.bodyKo).joinToString(" "))
    val levelHidden = ui.advisory.changed
    val mapMode = rememberAttractionMapMode(a)
    AppScreen(
        title = a.title,
        subtitle = a.nameEn.takeIf { it.isNotBlank() && it != a.nameKo },
        speech = speech,
        headerActions = { if (!single) HeartToggle(a.title, ui.saved, actions.setSaved) },
    ) {
        if (ui.catalog?.sample == true) {
            item(key = "sample") { NoticeBanner(stringResource(R.string.attractions_sample_banner), icon = Icons.Outlined.Info) }
        }
        when {
            levelHidden -> item(key = "advisory-changed") { AdvisoryChangedNotice(ui.advisory.packVerified, actions.openSafety) }
            a.advisory.level == AdvisoryLevel.Unknown -> item(key = "advisory-unknown") {
                NoticeBanner(stringResource(R.string.attractions_advisory_unknown), icon = Icons.Outlined.ReportProblem, tone = BannerTone.Caution)
            }
            else -> Unit
        }
        if (a.advisory.level == AdvisoryLevel.Two && !levelHidden) {
            item(key = "advisory-2") { AdvisoryLevelNote(a.advisory.lastVerified) }
        }
        if (a.status != OpenStatus.Open) {
            item(key = "status") {
                val text = stringResource(
                    when (a.status) {
                        OpenStatus.Partial -> R.string.attractions_status_partial
                        OpenStatus.TempClosed -> R.string.attractions_status_temp_closed
                        else -> R.string.attractions_status_unknown
                    },
                ).let { t -> listOfNotNull(t, a.statusNoteKo).joinToString(" — ") }
                val shown = a.statusVerified?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.attractions_status_verified, text, displayDate(it)) } ?: text
                NoticeBanner(
                    shown,
                    icon = if (a.status == OpenStatus.TempClosed) Icons.Outlined.Block else Icons.Outlined.ReportProblem,
                    tone = if (a.status == OpenStatus.TempClosed) BannerTone.Danger else BannerTone.Caution,
                )
            }
        }
        // 공식 출처에 휴관·공사 같은 안내가 새로 보임(ARIA 감지, 사람 확인 전) — 안전한 쪽으로 먼저 알린다
        if (ui.flagged) item(key = "flag") { AttractionFlagBand() }
        a.risks.forEach { risk ->
            item(key = "risk-${risk.key}") {
                NoticeBanner(
                    stringResource(
                        when (risk) {
                            Risk.Volcano -> R.string.attractions_risk_volcano
                            Risk.PostDisaster -> R.string.attractions_risk_post_disaster
                            Risk.Seasonal -> R.string.attractions_risk_seasonal
                            Risk.Renovation -> R.string.attractions_risk_renovation
                        },
                    ),
                    icon = Icons.Outlined.ReportProblem,
                    tone = BannerTone.Caution,
                )
            }
        }
        // 사진 칸: 사진이 있으면(D3 B·C) 사진 + 출처, 없으면 2열에서만 종류 그림 패널 (1열은 그림 없음)
        val photo = a.photo
        if (photo != null) {
            item(key = "photo") { PhotoSlot(a) }
        } else if (!single) {
            item(key = "art") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(illusPanelBrush(IllusTones.Teal, rich = false)),
                    contentAlignment = Alignment.Center,
                ) {
                    IllusImage(CategoryArt.of(a.category), Modifier.size(96.dp))
                }
            }
        }
        item(key = "chips") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(listOfNotNull(ui.countryName, region?.nameKo).joinToString(" · "), Icons.Outlined.Place, tone = BadgeTone.Teal)
                InfoChip(stringResource(a.category.labelRes()), a.category.icon(), tone = BadgeTone.Teal)
                a.tags.forEach { t -> InfoChip(stringResource(t.labelRes()), t.icon(), tone = BadgeTone.Teal) }
            }
        }
        if (single) item(key = "save-top") { SaveButton(ui.saved, actions.setSaved) }
        // 위치 지도 (Google 지도 SDK) — 키 없음·중국·좌표 없음이면 칸 자체가 없고 가는 법의 '구글 지도에서 열기'만 남는다
        if (mapMode != MapMode.Hidden) {
            item(key = "map") {
                AttractionMapSection(a, region?.nameKo.orEmpty(), stringResource(a.category.labelRes()), actions.openLink)
            }
        }
        if (region != null && region.kind == RegionKind.Daytrip) {
            item(key = "daytrip") {
                IconBullet(
                    listOfNotNull(region.nameKo, region.noteKo ?: stringResource(R.string.attractions_daytrip)).joinToString(" · "),
                    Icons.Outlined.Place,
                    tone = BadgeTone.Teal,
                )
            }
        }
        if (a.bodyKo.isNotEmpty() || a.summaryKo.isNotBlank()) {
            item(key = "about") {
                CardNewsCard(
                    title = stringResource(R.string.attractions_about),
                    icon = a.category.icon(),
                    tone = BadgeTone.Teal,
                    sources = a.claimSources.map { sourceOf(it.source, it.lastVerified) }.distinct(),
                ) {
                    KoText(a.summaryKo, MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
                    a.bodyKo.forEach { DotBullet(it) }
                }
            }
        }
        // 위키백과 요약 팝업 (제목이 있을 때만, 누를 때만 받아 온다)
        a.wiki?.let { wiki ->
            item(key = "wiki") { AttractionWikiEntry(wiki.preferred, actions.openLink) }
        }
        item(key = "know") { KnowCard(a, sourceOf, actions.openLink) }
        item(key = "go") { HowToGoCard(a, sourceOf, actions.openLink) }
        ui.rating?.let { r ->
            item(key = "rating") { RatingSection(r, actions.vote, actions.removeVote, actions.checkAge, actions.openLink) }
        }
        item(key = "save") {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                SaveButton(ui.saved, actions.setSaved)
                if (ui.showFirstNotice) FirstSaveNotice(actions.dismissFirstNotice)
            }
        }
        if (region != null) {
            item(key = "same-region") {
                ListGroup(stringResource(R.string.attractions_same_region)) {
                    ui.sameRegion.forEachIndexed { i, other ->
                        if (i > 0) ListDivider()
                        ListRow(other.title, icon = other.category.icon(), tone = BadgeTone.Teal, body = other.summaryKo.ifBlank { null }, onClick = { actions.openOther(other.id) })
                    }
                    if (ui.sameRegion.isNotEmpty()) ListDivider()
                    ListRow(stringResource(R.string.attractions_same_region_all, region.nameKo), icon = Icons.Outlined.Place, tone = BadgeTone.Teal, onClick = { actions.openRegion(region.id) })
                }
            }
        }
        item(key = "links") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                a.officialUrl?.let { url -> LinkRow(stringResource(R.string.attractions_official_site), { actions.openLink(url) }) }
                a.photoLink?.let { url -> LinkRow(stringResource(R.string.attractions_wikimedia_photos), { actions.openLink(url) }) }
            }
        }
        item(key = "footer") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KoText(stringResource(R.string.guide_last_verified, displayDate(a.oldestVerified)), MaterialTheme.typography.labelLarge, color = Tokens.InkSecondary)
                SourceList(a.sourceIds.map { sourceOf(it, a.oldestVerified) }.distinctBy { it.name })
            }
        }
    }
}

@Composable
private fun HeartToggle(title: String, saved: Boolean, onSave: (Boolean) -> Unit) {
    val cd = stringResource(R.string.attractions_save_cd, title)
    val state = stringResource(if (saved) R.string.attractions_saved_state else R.string.attractions_unsaved_state)
    IconToggleButton(
        checked = saved,
        onCheckedChange = onSave,
        modifier = Modifier.minTouchSize().semantics {
            contentDescription = cd
            stateDescription = state
        },
    ) {
        Icon(if (saved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = null, tint = if (saved) Tokens.Accent else Tokens.InkSecondary)
    }
}

/** 찜 버튼 — 글자가 상태를 말한다(liveRegion Polite, announceForAccessibility 쓰지 않음) */
@Composable
private fun SaveButton(saved: Boolean, onSave: (Boolean) -> Unit) {
    val label = stringResource(if (saved) R.string.attractions_saved_button_on else R.string.attractions_save)
    val mod = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    if (saved) {
        SecondaryButton(label, onClick = { onSave(false) }, modifier = mod, icon = Icons.Filled.Favorite, tone = BadgeTone.Teal)
    } else {
        PrimaryButton(label, onClick = { onSave(true) }, modifier = mod, icon = Icons.Outlined.FavoriteBorder)
    }
}

/** 가기 전에 알아 둘 것: 쉬는 요일이 첫 줄 → 누구나 다닐 수 있는 곳 → 입장료 유무 → 예약 → 관람 안내 → 철 → 팁 → 복장·휠체어 → 공식 사이트 안내 */
@Composable
private fun KnowCard(a: Attraction, sourceOf: (String, String) -> SourceRef, openLink: (String) -> Unit) {
    val f = a.facts
    val dayNames = ClosedDay.entries.associateWith { dayName(it) }
    val lines = buildList<Pair<String, ImageVector>> {
        if (f != null && !f.publicSpace) {
            val days = f.closedDays
            when {
                days.contains(ClosedDay.Irregular) -> add(stringResource(R.string.attractions_closed_irregular) to Icons.Outlined.CalendarMonth)
                days == listOf(ClosedDay.NoneDay) -> add(stringResource(R.string.attractions_closed_none) to Icons.Outlined.EventAvailable)
                days.isNotEmpty() -> add(
                    stringResource(R.string.attractions_closed_days, days.filter { it != ClosedDay.NoneDay }.joinToString("·") { dayNames.getValue(it) }) to Icons.Outlined.CalendarMonth,
                )
                else -> Unit
            }
            f.closedNoteKo?.let { add(it to Icons.Outlined.CalendarMonth) }
        }
        if (f?.publicSpace == true) add(stringResource(R.string.attractions_public_space) to Icons.Outlined.Info)
        if (f != null && !f.publicSpace) {
            when (f.entry) {
                Entry.Free -> add(stringResource(R.string.attractions_entry_free) to Icons.Outlined.Payments)
                Entry.Paid -> add(stringResource(R.string.attractions_entry_paid) to Icons.Outlined.Payments)
                Entry.Unknown -> Unit
            }
        }
        when (f?.booking) {
            Booking.Required -> add(stringResource(R.string.attractions_booking_required) to Icons.Outlined.EventAvailable)
            Booking.Recommended -> add(stringResource(R.string.attractions_booking_recommended) to Icons.Outlined.EventAvailable)
            else -> Unit
        }
        f?.bookingNoteKo?.let { add(it to Icons.Outlined.EventAvailable) }
        f?.visitNoteKo?.let { add(it to Icons.Outlined.Info) }
        a.seasonal.forEach { add(it.text to Icons.Outlined.CalendarMonth) }
        a.tips.forEach { add(it.text to Icons.Outlined.Info) }
        a.tags.filter { it == Tag.DressCode || it == Tag.StepFree }.forEach { add(stringResource(it.labelRes()) to it.icon()) }
    }
    val sources = buildList {
        f?.let { if (it.source.isNotBlank()) add(sourceOf(it.source, it.lastVerified)) }
        a.tips.forEach { add(sourceOf(it.source, it.lastVerified)) }
        a.seasonal.forEach { add(sourceOf(it.source, it.lastVerified)) }
    }.distinct()
    CardNewsCard(title = stringResource(R.string.attractions_know), icon = Icons.Outlined.Info, tone = BadgeTone.Teal, sources = sources) {
        lines.forEach { (text, icon) -> IconBullet(text, icon) }
        a.officialUrl?.let { url ->
            LinkRow(stringResource(R.string.attractions_hours_official), { openLink(url) })
        }
    }
}

/** 가는 법: 교통 수단 · 가까운 곳(현지어) · 지도에서 보기 (숫자 없음, D7-A) */
@Composable
private fun HowToGoCard(a: Attraction, sourceOf: (String, String) -> SourceRef, openLink: (String) -> Unit) {
    val source = a.accessSource?.takeIf { it.isNotBlank() }
    CardNewsCard(
        title = stringResource(R.string.attractions_how_to_go),
        icon = Icons.Outlined.Map,
        tone = BadgeTone.Teal,
        sources = listOfNotNull(source?.let { sourceOf(it, a.oldestVerified) }),
    ) {
        if (a.accessModes.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                a.accessModes.forEach { m -> InfoChip(stringResource(m.labelRes()), m.icon(), tone = BadgeTone.Teal) }
            }
        }
        a.nearestKo?.let { IconBullet(stringResource(R.string.attractions_nearest, it), Icons.Outlined.Place) }
        (a.localShort ?: a.local)?.let { IconBullet(stringResource(R.string.attractions_local_name, it), Icons.Outlined.Translate) }
        mapsUrl(a)?.let { url ->
            SecondaryButton(stringResource(R.string.attractions_open_map), onClick = { openLink(url) }, icon = Icons.Outlined.Map, tone = BadgeTone.Teal)
        }
        if (a.country == "CN") IconBullet(stringResource(R.string.attractions_cn_map_note), Icons.Outlined.Info)
    }
}

/** 사진 칸 (D3 B·C 대비): assets/attractions/<CC>/<file>. 읽지 못하면 아무것도 그리지 않는다. 사진 바로 아래 TASL */
@Composable
private fun PhotoSlot(a: Attraction) {
    val photo = a.photo ?: return
    val context = LocalContext.current
    val bitmap = remember(a.key, photo.file) {
        runCatching { context.assets.open("attractions/${a.country}/${photo.file}").use { BitmapFactory.decodeStream(it) } }.getOrNull()
    } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(PHOTO_RATIO).clip(MaterialTheme.shapes.large),
        )
        KoText(
            stringResource(R.string.attractions_photo_credit, photo.title, photo.author, photo.license),
            MaterialTheme.typography.labelSmall,
            Modifier.padding(horizontal = 4.dp),
            color = Tokens.InkSecondary,
        )
    }
}

private const val PHOTO_RATIO = 16f / 9f

@Composable
private fun dayName(d: ClosedDay): String = stringResource(
    when (d) {
        ClosedDay.Mon -> R.string.attractions_day_mon
        ClosedDay.Tue -> R.string.attractions_day_tue
        ClosedDay.Wed -> R.string.attractions_day_wed
        ClosedDay.Thu -> R.string.attractions_day_thu
        ClosedDay.Fri -> R.string.attractions_day_fri
        ClosedDay.Sat -> R.string.attractions_day_sat
        else -> R.string.attractions_day_sun
    },
)

fun AccessMode.labelRes(): Int = when (this) {
    AccessMode.Train -> R.string.attractions_mode_train
    AccessMode.Metro -> R.string.attractions_mode_metro
    AccessMode.Bus -> R.string.attractions_mode_bus
    AccessMode.Boat -> R.string.attractions_mode_boat
    AccessMode.CarOnly -> R.string.attractions_mode_car_only
    AccessMode.WalkFromCenter -> R.string.attractions_mode_walk_from_center
}

fun AccessMode.icon(): ImageVector = when (this) {
    AccessMode.Train -> Icons.Outlined.Train
    AccessMode.Metro -> Icons.Outlined.Subway
    AccessMode.Bus -> Icons.Outlined.DirectionsBus
    AccessMode.Boat -> Icons.Outlined.DirectionsBoat
    AccessMode.CarOnly -> Icons.Outlined.DirectionsCar
    AccessMode.WalkFromCenter -> Icons.AutoMirrored.Outlined.DirectionsWalk
}

fun Tag.labelRes(): Int = when (this) {
    Tag.Unesco -> R.string.attractions_tag_unesco
    Tag.Indoor -> R.string.attractions_tag_indoor
    Tag.FreeEntry -> R.string.attractions_tag_free_entry
    Tag.BookingRequired -> R.string.attractions_tag_booking_required
    Tag.DressCode -> R.string.attractions_tag_dress_code
    Tag.Night -> R.string.attractions_tag_night
    Tag.Stairs -> R.string.attractions_tag_stairs
    Tag.StepFree -> R.string.attractions_tag_step_free
    Tag.CableCar -> R.string.attractions_tag_cable_car
    Tag.MountainView -> R.string.attractions_tag_mountain_view
    Tag.Seafront -> R.string.attractions_tag_seafront
    Tag.ForeignerPrice -> R.string.attractions_tag_foreigner_price
    Tag.HotSpring -> R.string.attractions_tag_hot_spring
}

fun Tag.icon(): ImageVector = when (this) {
    Tag.Unesco -> Icons.Outlined.Info
    Tag.BookingRequired -> Icons.Outlined.EventAvailable
    Tag.FreeEntry, Tag.ForeignerPrice -> Icons.Outlined.Payments
    Tag.CableCar, Tag.MountainView -> Icons.Outlined.Place
    Tag.Seafront -> Icons.Outlined.DirectionsBoat
    else -> Icons.Outlined.Info
}
