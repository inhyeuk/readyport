package com.readyport.ui.home

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalAirport
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.OfficialLink
import com.readyport.pack.PackRepository
import com.readyport.pack.Requirement
import com.readyport.pack.SourcedText
import com.readyport.trip.ChecklistData
import com.readyport.trip.ChecklistProvider
import com.readyport.trip.TripRepository
import com.readyport.trip.TripSelection
import com.readyport.ui.components.CheckProgressBar
import androidx.compose.material.icons.outlined.CalendarMonth
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTitleLayout
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.ButtonStyles
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.ChipSpec
import com.readyport.ui.components.CountryPhotoTile
import com.readyport.ui.components.FitText
import com.readyport.ui.components.HelpShortcutRow
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.InfoChip
import com.readyport.ui.components.KoText
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.NewsStyle
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.PhotoBox
import com.readyport.ui.components.PhotoHeaderCard
import com.readyport.ui.components.PhotoTextArea
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.ReturnCheckCard
import com.readyport.ui.components.foldLiveRegion
import com.readyport.ui.components.ReturnCheckMode
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SourceList
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.Step
import com.readyport.ui.components.StepList
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.isStackedListRow
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.noBreak
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.rememberPhotoLift
import com.readyport.ui.components.rememberThumbnail
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.tileRows
import com.readyport.ui.components.textIconSize
import com.readyport.ui.onboarding.AppSymbol
import com.readyport.ui.onboarding.ValuePropText
import com.readyport.ui.components.EssentialsChips
import com.readyport.ui.components.EssentialsProgress
import com.readyport.ui.components.EssentialsSummary
import com.readyport.ui.components.essentialsSources
import com.readyport.ui.components.essentialsSummary
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

/** 홈의 나라 사진 카드 하나 */
data class HomeCountry(
    val code: String,
    val nameKo: String,
    val nameEn: String,
    /** 한국 여권·관광 목적 입국 조건 (없으면 안내 준비 중) */
    val visa: Requirement? = null,
    val hasForm: Boolean = false,
    val ready: Boolean = false,
    /** [visa] 출처 이름 (나라 그리드 아래 SourceList용, DESIGN_SPEC 6-01). 못 찾으면 null */
    val sourceName: String? = null,
)

/**
 * 둘러보기가 가리키는 '내 여행' 한 줄. [code]: 나라 코드(사진 썸네일용), [id]: 그 여행 화면으로 가는 길.
 * [checklistDone]/[checklistTotal]: 그 여행 진행(0이면 표시 없음), [tripCount]: 저장된 여행 수(2개 이상이면 `여행 n개 모두 보기`)
 */
data class HomeTrip(
    val countryKo: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val code: String? = null,
    val id: String = "",
    val checklistDone: Int = 0,
    val checklistTotal: Int = 0,
    val tripCount: Int = 1,
)

data class HomeUi(
    val countries: List<HomeCountry> = emptyList(),
    val trip: HomeTrip? = null,
)

data class HomeActions(
    val openCountry: (String) -> Unit = {},
    /** 그 여행 화면으로 (내 여행 탭의 한 여행 화면) */
    val openTrip: (String) -> Unit = {},
    /** 내 여행 목록 */
    val openTrips: () -> Unit = {},
    /** 새 여행 만들기 — 둘러보기의 단 하나의 주 버튼 */
    val makeTrip: () -> Unit = {},
    /** '급할 때는 도움' 줄 → 도움 탭 (DESIGN_SPEC 6-01) */
    val openHelp: () -> Unit = {},
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    packs: PackRepository,
    trips: TripRepository,
    checklists: ChecklistProvider,
) : ViewModel() {
    val ui: StateFlow<HomeUi> = combine(packs.revision, trips.book) { _, book ->
        val today = LocalDate.now()
        val trip = TripSelection.active(book.trips, today)
        val index = packs.index()?.value
        val countries = index?.countries.orEmpty().map { c ->
            val pack = if (c.pack) packs.pack(c.code)?.value else null
            val visa = pack?.requirements?.firstOrNull { it.nationality == "KR" && it.purpose == "tourism" }
            HomeCountry(
                code = c.code, nameKo = c.nameKo, nameEn = c.nameEn,
                visa = visa,
                // 꼭 내야 하는 입국 카드가 있는 나라인지 — 의무가 아닌 신고(forms[].optional)는 세지 않는다
                hasForm = pack?.requiredForms?.isNotEmpty() == true,
                ready = pack != null,
                sourceName = visa?.let { pack.source(it.source)?.name },
            )
        }
        val tripPack = trip?.let { packs.pack(it.country)?.value }
        val data = trip?.let { checklists.build(it, book, today) }
        val homeTrip = trip?.let { t ->
            runCatching {
                HomeTrip(
                    tripPack?.names?.ko ?: t.country, LocalDate.parse(t.startDate), LocalDate.parse(t.endDate), t.country, t.id,
                    checklistDone = data?.done ?: 0, checklistTotal = data?.total ?: 0, tripCount = book.trips.size,
                )
            }.getOrNull()
        }
        HomeUi(countries = countries, trip = homeTrip)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}

@Composable
fun HomeScreen(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    HomeContent(ui = ui, actions = actions)
}

/**
 * 둘러보기 (예전 '홈', 2026-10-03 부록 H): **어디 갈까를 고르는 화면**이다 — 여행 흐름(출국 순서·꼭 챙길 물건·귀국 전 확인·여권)은
 * 모두 내 여행 탭의 한 여행 화면으로 옮겼다. 같은 일을 두 곳에서 하지 않는다.
 * 위에서부터: 사진 히어로(앱 이름 · 질문 · 핵심 가치 한 줄 · 신뢰 표시) → 여행 만들기(또는 내 여행으로 가는 한 줄)
 * → 나라 사진 타일(큰 1장 + 2열) + 출처 + 소개 문장 → 급할 때는 도움.
 * - 주 버튼(채움)은 화면에 하나(원칙 7): 여행이 없으면 `여행 만들기`, 있으면 `내 여행 보기`.
 * - 1열(쉬운 모드·큰 글자)에서는 신뢰 표시 3개를 히어로에서 나라 목록 아래로 옮긴다(숨기지 않고 자리만 — 재검토 R5).
 */
@Composable
fun HomeContent(ui: HomeUi, actions: HomeActions, today: LocalDate = LocalDate.now()) {
    val columns = rememberGridColumns()
    val fallback = stringResource(R.string.source_official_fallback)
    // 여행 중인 나라(없으면 첫 나라)를 크게, 나머지는 2열(쉬운 모드·큰 글자는 모두 1열 큰 타일)
    val featured = ui.countries.firstOrNull { it.code == ui.trip?.code } ?: ui.countries.firstOrNull()
    val others = ui.countries.filter { it.code != featured?.code }
    // 칩에 쓴 입국 조건(정책 값)의 출처 — 칩이 출처 없이 보이지 않게 그리드 바로 아래에 (원칙 5)
    val countrySources = ui.countries.mapNotNull { c ->
        c.visa?.let { v -> SourceRef(c.sourceName?.takeIf { it.isNotBlank() } ?: fallback, displayDate(v.lastVerified)) }
    }
    AppScreen(
        title = stringResource(R.string.home_title),
        speech = stringResource(R.string.home_speech),
        header = { HomeHero(singleColumn = columns == 1, hasTrip = ui.trip != null) },
        // 둘러보기 자신에서는 `처음으로`를 숨긴다(눌러도 아무 일 없음) — 쉬운 모드는 `소리로 듣기`만 폭 전체
        showHomeAction = false,
    ) {
        // 여행이 있으면 그 여행으로 가는 카드가 맨 위 — 여행이 없으면 나라를 먼저 보여 주고(첫 화면 예산, 운영자 결정 9)
        // `여행 만들기`는 나라 목록 **아래**에 둔다: 둘러보다가 고른 다음이 자연스러운 자리다
        ui.trip?.let { trip ->
            item(key = "trip") { TripCountdownCard(trip, today, { actions.openTrip(trip.id) }, actions.openTrips) }
            sectionGap("countries-gap")
        }
        item(key = "countries-title") {
            SectionHeader(
                title = stringResource(R.string.home_countries_title),
                subtitle = stringResource(R.string.home_countries_body),
            )
        }
        featured?.let { c ->
            item(key = "country-${c.code}") { HomeCountryTile(c, large = true, onOpen = actions.openCountry) }
        }
        if (columns == 1) {
            others.forEach { c -> item(key = "country-${c.code}") { HomeCountryTile(c, large = true, onOpen = actions.openCountry) } }
        } else {
            tileRows("countries", others, columns) { c, cell -> HomeCountryTile(c, large = false, onOpen = actions.openCountry, modifier = cell) }
        }
        if (countrySources.isNotEmpty()) {
            item(key = "countries-sources") { Box(Modifier.padding(horizontal = 4.dp)) { SourceList(countrySources) } }
        }
        // 소개 문장(모든 모드) + 1열에서는 히어로에서 옮겨 온 신뢰 표시 (숨기지 않고 자리만 — 재검토 R5·R13)
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner)) {
                IconBullet(stringResource(R.string.home_subtitle), Icons.Outlined.Info)
                if (columns == 1) TrustStrip(onDark = false)
            }
        }
        if (ui.trip == null) {
            item(key = "make-trip") { MakeTripCard(actions.makeTrip) }
        }
        item(key = "help") { HelpShortcutRow(actions.openHelp) }
    }
}

/**
 * 여행이 없을 때의 단 하나의 할 일 (Accent 채움): `여행을 만들면 순서대로 알려 드려요` + `새 여행 만들기`.
 * 여행을 만들면 이 자리에 내 여행 카드가 들어온다 — 둘러보기에는 언제나 여행으로 가는 길이 한 줄 있다.
 */
@Composable
private fun MakeTripCard(onMake: () -> Unit) {
    CardNewsCard(
        title = stringResource(R.string.journey_explore_none_title),
        icon = Icons.Outlined.EditCalendar,
        eyebrow = stringResource(R.string.today_next_label),
        body = stringResource(R.string.journey_explore_none_body),
        style = NewsStyle.Accent,
    ) {
        PrimaryButton(
            text = stringResource(R.string.today_new_trip),
            onClick = onMake,
            // 배지(EditCalendar)와 같은 아이콘을 버튼에 되풀이하지 않는다 — 버튼은 '다음 화면으로'
            icon = Icons.AutoMirrored.Outlined.NavigateNext,
            colors = ButtonStyles.onDark(Tokens.Accent),
        )
    }
}

/**
 * 맨 위 사진 머리글: 앱 심볼·이름 + "어디로 떠나세요?"(heading) + 핵심 가치 한 줄(재검토 R13 — 모든 모드) + 신뢰 표시 3개
 * (공식 출처만 · 폰에만 저장 · 인터넷 없이도 — 처음 5초 안에 보이게, DESIGN_SPEC 1.1 ⑤).
 * 사진 위 글자·표시는 모두 스크림 글자 영역(PhotoTextArea) 안. 사진은 그릴 때만 밝기 보정(재검토 R19, 파일은 그대로).
 * - 신뢰 표시는 누를 수 없으므로 버튼처럼 보이는 상자 없이 아이콘 + 글자(InfoChip onDark)로 한 줄에 흐르게 둔다(재검토 R1).
 *   1열([singleColumn] — 쉬운 모드·큰 글자)이면 나라 목록 아래로 옮긴다 — 가치 문장이 들어오면서 첫 화면에서 나라 사진이 밀려나지 않게.
 * - 2열은 글 위로 사진(하늘)이 보이게 최소 높이 280dp, 1열·여행이 있을 때는 160dp(내용 높이 — 출발까지 카드를 위로).
 */
@Composable
private fun HomeHero(singleColumn: Boolean, hasTrip: Boolean) {
    val brandStyle = MaterialTheme.typography.labelLarge
    // 심볼 지름 = 앱 이름 한 줄 높이(최소 24dp) — 글자를 키워도 이름과 크기가 어울리고 줄 높이를 늘리지 않는다
    val symbolSize = maxOf(24.dp, with(LocalDensity.current) { brandStyle.lineHeight.toDp() })
    PhotoBox(
        Photos.Home,
        // 여행이 있으면 사진 높이를 내용만큼으로 — 출발까지 카드가 첫 화면 위쪽에 오게 (재검토2 ⑤#13, 글은 그대로)
        minHeight = if (singleColumn || hasTrip) 160.dp else 280.dp,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        PhotoTextArea {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppSymbol(size = symbolSize)
                Text(
                    stringResource(R.string.home_brand),
                    style = brandStyle,
                    color = OnDark.content,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            // 어절 단위로만 줄을 바꾼다(sdk 31·200%·360dp에서 `떠나세/요?` 방지 — KoText)
            KoText(
                stringResource(R.string.home_title),
                MaterialTheme.typography.displaySmall,
                color = OnDark.content,
                heading = true,
            )
            ValuePropText(MaterialTheme.typography.titleMedium)
            if (!singleColumn) TrustStrip(Modifier.padding(top = 6.dp), onDark = true)
        }
    }
}

/**
 * 신뢰 표시 3개 (누를 수 없음): 상자·채움 없이 아이콘 + 글자(InfoChip)를 한 줄에 흐르게 놓는다 —
 * 들어가지 않으면 다음 줄로(큰 글자에서도 잘리지 않음). TalkBack은 표시마다 글자 한 번.
 * [onDark]: 히어로 사진 위(흰 글자), 아니면 밝은 화면 바탕 위(Accent 아이콘 + 보조 글자).
 */
@Composable
private fun TrustStrip(modifier: Modifier = Modifier, onDark: Boolean) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val style = MaterialTheme.typography.labelMedium
        val tone = BadgeTone.Accent
        InfoChip(stringResource(R.string.trust_official), Icons.AutoMirrored.Outlined.FactCheck, tone = tone, onDark = onDark, textStyle = style)
        InfoChip(stringResource(R.string.trust_local), Icons.Outlined.Lock, tone = tone, onDark = onDark, textStyle = style)
        InfoChip(stringResource(R.string.trust_offline), Icons.Outlined.OfflinePin, tone = tone, onDark = onDark, textStyle = style)
    }
}

/**
 * 나라 사진 타일. [large]면 1열 큰 타일, 아니면 2열 칸. 칩은 **나라마다 같은 구성** — 입국 조건(차이 정보) 1개
 * (재검토 R19: 맨 위 나라에만 입력 도우미 칩이 더 붙어 타일 모양이 달랐다. 도우미는 히어로 가치 문장이 말한다).
 * 안내가 아직 없는 나라는 `안내 준비 중` 1개. TalkBack 이름은 `home_country_open`, 칩은 stateDescription(CountryPhotoTile, D16).
 */
@Composable
private fun HomeCountryTile(
    c: HomeCountry,
    large: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = buildList {
        c.visa?.let { v ->
            add(
                when {
                    v.visa == "not_required" && v.stayLimitDays != null ->
                        ChipSpec(Icons.Outlined.EventAvailable, stringResource(R.string.home_chip_visa_free, v.stayLimitDays))
                    v.visa == "on_arrival" && v.stayLimitDays != null ->
                        ChipSpec(Icons.Outlined.Approval, stringResource(R.string.home_chip_visa_arrival, v.stayLimitDays))
                    else -> ChipSpec(Icons.Outlined.Approval, stringResource(R.string.home_chip_visa_check))
                },
            )
        }
        if (!c.ready) add(ChipSpec(Icons.Outlined.Schedule, stringResource(R.string.home_chip_not_ready)))
    }
    CountryPhotoTile(
        nameKo = c.nameKo,
        nameEn = c.nameEn,
        photo = Photos.country(c.code),
        chips = chips,
        onClick = { onOpen(c.code) },
        openLabel = stringResource(R.string.home_country_open, c.nameKo),
        modifier = modifier,
        large = large,
        enabled = c.ready,
    )
}

/**
 * 02 내 여행 요약 (DESIGN_SPEC 6-02): Accent 채움 카드 — 나라 사진 원형 썸네일(장식) + eyebrow `내 여행 · 태국`(White85)
 * + 출발까지 큰 숫자(stat) + 날짜 한 줄 + 흰 주 버튼. 어두운 채움 위라 onDark 내용 세트만 쓴다(D18).
 * 큰 글자 배치에서는 썸네일을 글 위로 올리고, 큰 숫자는 칸 폭에 맞춰 한 줄에 들어가는 크기(stat → statSmall)로 그린다(FitText, 재검토 R5·R6).
 */
@Composable
private fun TripCountdownCard(trip: HomeTrip, today: LocalDate, onOpen: () -> Unit, onOpenAll: () -> Unit = {}) {
    val dimens = LocalDimens.current
    val extras = LocalTypeExtras.current
    val stacked = isStackedLayout()
    val days = ChronoUnit.DAYS.between(today, trip.startDate).toInt()
    val status = when {
        days > 0 -> stringResource(R.string.home_trip_days, days)
        days == 0 -> stringResource(R.string.home_trip_today)
        !today.isAfter(trip.endDate) -> stringResource(R.string.home_trip_during)
        else -> stringResource(R.string.home_trip_after)
    }
    val format = DateTimeFormatter.ofPattern(stringResource(R.string.home_trip_date_format), Locale.KOREAN)
    val dates = stringResource(R.string.home_trip_dates, noBreak(trip.startDate.format(format)), noBreak(trip.endDate.format(format)))
    val head: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.home_trip_label, trip.countryKo), style = MaterialTheme.typography.labelMedium, color = OnDark.eyebrow)
            FitText(
                status,
                styles = listOf(extras.stat, extras.statSmall),
                color = OnDark.content,
                modifier = Modifier.semantics { heading() },
                breakChars = " ",
            )
        }
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Tokens.Accent, contentColor = OnDark.content),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.inner + 4.dp)) {
            if (stacked) {
                TripThumbnail(trip.code)
                head()
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TripThumbnail(trip.code)
                    Box(Modifier.weight(1f)) { head() }
                }
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Outlined.FlightTakeoff,
                    contentDescription = null,
                    tint = OnDark.content,
                    modifier = Modifier.padding(top = 1.dp).size(dimens.iconSmall + 4.dp),
                )
                Text(dates, style = MaterialTheme.typography.titleSmall, color = OnDark.content, modifier = Modifier.weight(1f))
            }
            // 이 여행 체크리스트 진행 — 앱 안 값(누를 수 없는 칩 + 막대)
            if (trip.checklistTotal > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(
                        stringResource(R.string.ck_now_eyebrow, trip.checklistDone, trip.checklistTotal),
                        IconKeys.essentials,
                        onDark = true,
                    )
                    CheckProgressBar(trip.checklistDone, trip.checklistTotal, onDark = true)
                }
            }
            PrimaryButton(
                stringResource(R.string.home_trip_open),
                onClick = onOpen,
                // 앞에 붙는 아이콘이라 '>'(NavigateNext)보다 내 여행 탭과 같은 짐가방이 자연스럽다 (BUNDLE_A_NOTES ②)
                icon = Icons.Outlined.Luggage,
                colors = ButtonStyles.onDark(content = Tokens.Accent),
            )
            if (trip.tripCount > 1) {
                SecondaryButton(stringResource(R.string.home_trips_all, trip.tripCount), onClick = onOpenAll, icon = Icons.Outlined.CalendarMonth, onDark = true)
            }
        }
    }
}

/** 여행 나라 사진 원형 썸네일(장식, 축소 디코딩). 어두운 사진은 그릴 때만 밝힌다(재검토 R19). 사진이 없으면 비행기 아이콘 배지 */
@Composable
private fun TripThumbnail(code: String?) {
    val size = if (LocalDimens.current.easyMode) 64.dp else 56.dp
    val thumb = rememberThumbnail(code?.let { Photos.country(it) }, size)
    if (thumb != null) {
        Image(
            bitmap = thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = rememberPhotoLift(thumb),
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(2.dp, OnDark.content, CircleShape),
        )
    } else {
        IconBadge(Icons.Outlined.FlightTakeoff, tone = BadgeTone.OnDark, size = size, shape = CircleShape)
    }
}
