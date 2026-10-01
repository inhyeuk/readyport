package com.readyport.ui.home

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalAirport
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.readyport.prep.Essentials
import com.readyport.trip.TripRepository
import com.readyport.ui.components.AppScreen
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
import com.readyport.ui.components.NewsStyle
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.PhotoBox
import com.readyport.ui.components.PhotoHeaderCard
import com.readyport.ui.components.PhotoTextArea
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.ReturnCheckCard
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SourceList
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.Step
import com.readyport.ui.components.StepList
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.keepWords
import com.readyport.ui.components.noBreak
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.rememberThumbnail
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.tileRows
import com.readyport.ui.onboarding.AppSymbol
import com.readyport.ui.tabs.EssentialsSummary
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

/** 홈 위쪽 '내 여행' 요약. [code]: 나라 코드(사진 썸네일용, DESIGN_SPEC 6-02) */
data class HomeTrip(val countryKo: String, val startDate: LocalDate, val endDate: LocalDate, val code: String? = null)

data class HomeUi(
    val countries: List<HomeCountry> = emptyList(),
    val trip: HomeTrip? = null,
    val essentials: EssentialsSummary = EssentialsSummary(),
    val returnLinks: List<OfficialLink> = emptyList(),
    val returnFacts: List<SourcedText> = emptyList(),
    val indexSources: Map<String, String> = emptyMap(),
)

data class HomeActions(
    val openCountry: (String) -> Unit = {},
    val openTrip: () -> Unit = {},
    val openEssentials: () -> Unit = {},
    val openMyInfo: () -> Unit = {},
    val openLink: (String) -> Unit = {},
    /** '급할 때는 도움' 줄 → 도움 탭 (DESIGN_SPEC 6-01 ⑩, ReadyPortRoot 배선은 2단계) */
    val openHelp: () -> Unit = {},
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    packs: PackRepository,
    settings: SettingsRepository,
    trips: TripRepository,
) : ViewModel() {
    val ui: StateFlow<HomeUi> = combine(settings.settings, packs.revision, trips.trip) { s, _, trip ->
        val index = packs.index()?.value
        val countries = index?.countries.orEmpty().map { c ->
            val pack = if (c.pack) packs.pack(c.code)?.value else null
            val visa = pack?.requirements?.firstOrNull { it.nationality == "KR" && it.purpose == "tourism" }
            HomeCountry(
                code = c.code, nameKo = c.nameKo, nameEn = c.nameEn,
                visa = visa,
                hasForm = pack?.forms?.isNotEmpty() == true,
                ready = pack != null,
                sourceName = visa?.let { pack.source(it.source)?.name },
            )
        }
        val tripPack = trip?.let { packs.pack(it.country)?.value }
        val homeTrip = trip?.let { t ->
            runCatching { HomeTrip(tripPack?.names?.ko ?: t.country, LocalDate.parse(t.startDate), LocalDate.parse(t.endDate), t.country) }.getOrNull()
        }
        val rules = Essentials.select(index?.essentials.orEmpty(), index?.homePower, tripPack?.power)
        HomeUi(
            countries = countries,
            trip = homeTrip,
            essentials = EssentialsSummary(rules.size, rules.count { it.id in s.haveItems }),
            returnLinks = index?.returnLinks.orEmpty(),
            returnFacts = index?.returnFacts.orEmpty(),
            indexSources = index?.sources.orEmpty().associate { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}

@Composable
fun HomeScreen(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    HomeContent(
        ui = ui,
        actions = actions.copy(openLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }),
    )
}

/**
 * 홈 (DESIGN_SPEC 6-01·6-02): 사진 히어로(신뢰 칩 3개) → (여행 있으면) 출발까지 카드 → 나라 사진 타일(큰 1장 + 2열) + 출처
 * → 여행 준비 기본 정보(출국 순서 · 꼭 챙길 물건 · 귀국 전 확인 요약) → 여권 등록 → 급할 때는 도움.
 * 신뢰 표시는 어느 모드에서나 히어로 안(처음 5초 안에 보이게, 1.1 ⑤) — 누를 수 없는 InfoChip(채움 없음, 재검토 R1).
 * 소개 문장도 어느 모드에서나 보인다(재검토 R5 — 쉬운 모드·큰 글자에서 내용을 숨기지 않는다): 2열은 히어로 안,
 * 1열(쉬운 모드·큰 글자)은 나라 사진 목록 바로 아래 안내 줄로 자리만 옮긴다 — 히어로가 길어져 첫 화면에서 나라 사진이 밀려나지 않게
 * (6장 첫 화면 예산, HomeFirstScreenTest. 최종 배치는 B 묶음 R13 홈 첫인상에서 가치 문장과 함께 정한다).
 * 큰 글자 배치에서는 공용 부품이 출국 단계 아이콘을 글 첫 줄 안으로 옮기고 사진 머리 아이콘은 제목 첫 줄에 맞춘다.
 */
@Composable
fun HomeContent(ui: HomeUi, actions: HomeActions, today: LocalDate = LocalDate.now()) {
    val columns = rememberGridColumns()
    val fallback = stringResource(R.string.source_official_fallback)
    // 여행 중인 나라(없으면 첫 나라)를 크게, 나머지는 2열(쉬운 모드·큰 글자는 모두 1열 큰 타일)
    val featured = ui.countries.firstOrNull { it.code == ui.trip?.code } ?: ui.countries.firstOrNull()
    val others = ui.countries.filter { it.code != featured?.code }
    // 칩에 쓴 입국 조건(정책 값)의 출처 — 칩이 출처 없이 보이지 않게 그리드 바로 아래에 (원칙 5).
    // 같은 기관(`외교부 해외안전여행 · 태국`, `… · 일본`)은 공용 SourceList가 한 줄로 묶는다(재검토 R9 — `외교부 해외안전여행 · 태국, 일본`).
    // 이름 안 줄바꿈(어절 단위)은 SourceFooter가 한다
    val countrySources = ui.countries.mapNotNull { c ->
        c.visa?.let { v -> SourceRef(c.sourceName?.takeIf { it.isNotBlank() } ?: fallback, displayDate(v.lastVerified)) }
    }
    AppScreen(
        title = stringResource(R.string.home_title),
        speech = stringResource(R.string.home_speech),
        header = { HomeHero(singleColumn = columns == 1) },
    ) {
        ui.trip?.let { trip ->
            item(key = "trip") { TripCountdownCard(trip, today, actions.openTrip) }
            sectionGap("countries-gap")
        }
        item(key = "countries-title") {
            SectionHeader(
                title = stringResource(R.string.home_countries_title),
                subtitle = stringResource(R.string.home_countries_body),
            )
        }
        featured?.let { c ->
            item(key = "country-${c.code}") { HomeCountryTile(c, large = true, onOpen = actions.openCountry, detailChips = true) }
        }
        if (columns == 1) {
            others.forEach { c -> item(key = "country-${c.code}") { HomeCountryTile(c, large = true, onOpen = actions.openCountry) } }
        } else {
            tileRows("countries", others, columns) { c, cell -> HomeCountryTile(c, large = false, onOpen = actions.openCountry, modifier = cell) }
        }
        if (countrySources.isNotEmpty()) {
            item(key = "countries-sources") { Box(Modifier.padding(horizontal = 4.dp)) { SourceList(countrySources) } }
        }
        if (columns == 1) {
            // 1열: 히어로 소개 문장을 숨기지 않고 나라 목록 아래로 옮긴다 (재검토 R5)
            item(key = "intro") { IconBullet(stringResource(R.string.home_subtitle), Icons.Outlined.Info) }
        }

        sectionGap("basics-gap")
        item(key = "basics-title") { SectionHeader(stringResource(R.string.home_basics_title)) }
        item(key = "departure") { DepartureCard() }
        item(key = "essentials") { EssentialsCard(ui.essentials, actions.openEssentials) }
        if (ui.returnFacts.isNotEmpty() || ui.returnLinks.isNotEmpty()) {
            item(key = "return") {
                ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, actions.openLink, compact = true)
            }
        }
        item(key = "passport") {
            CardNewsCard(
                title = stringResource(R.string.home_passport_title),
                icon = Icons.Outlined.Lock,
                body = stringResource(R.string.home_passport_body),
                style = NewsStyle.Navy,
            ) {
                PrimaryButton(
                    stringResource(R.string.home_passport_open),
                    onClick = actions.openMyInfo,
                    icon = Icons.Outlined.Badge,
                    colors = ButtonStyles.onDark(),
                )
            }
        }
        item(key = "help") { HelpShortcutRow(actions.openHelp) }
    }
}

/**
 * 맨 위 사진 머리글: 앱 심볼·이름 + "어디로 떠나세요?"(heading) + 한 줄 소개 + 신뢰 표시 3개
 * (공식 출처만 · 폰에만 저장 · 인터넷 없이도 — 처음 5초 안에 보이게, DESIGN_SPEC 1.1 ⑤).
 * 사진 위 글자·표시는 모두 스크림 글자 영역(PhotoTextArea) 안.
 * - 신뢰 표시는 누를 수 없으므로 버튼처럼 보이는 상자 없이 아이콘 + 글자(InfoChip onDark)로 한 줄에 흐르게 둔다(재검토 R1).
 * - 소개 문장은 2열에서 여기, 1열에서는 나라 목록 아래(HomeContent — 숨기지 않고 자리만 옮김, 재검토 R5).
 *   2열은 글 위로 사진(하늘)이 보이게 최소 높이 280dp, 1열은 160dp.
 */
@Composable
private fun HomeHero(singleColumn: Boolean) {
    val brandStyle = MaterialTheme.typography.labelLarge
    // 심볼 지름 = 앱 이름 한 줄 높이(최소 24dp) — 글자를 키워도 이름과 크기가 어울리고 줄 높이를 늘리지 않는다
    val symbolSize = maxOf(24.dp, with(LocalDensity.current) { brandStyle.lineHeight.toDp() })
    PhotoBox(Photos.Home, minHeight = if (singleColumn) 160.dp else 280.dp, shape = MaterialTheme.shapes.extraLarge) {
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
            Text(
                stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
                color = OnDark.content,
                modifier = Modifier.semantics { heading() },
            )
            if (!singleColumn) {
                KoText(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge, color = OnDark.content)
            }
            TrustStrip(Modifier.padding(top = 6.dp))
        }
    }
}

/**
 * 신뢰 표시 3개 (누를 수 없음): 상자·채움 없이 아이콘 + 글자(InfoChip, onDark)를 한 줄에 흐르게 놓는다 —
 * 들어가지 않으면 다음 줄로(큰 글자에서도 잘리지 않음). TalkBack은 표시마다 글자 한 번.
 */
@Composable
private fun TrustStrip(modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val style = MaterialTheme.typography.labelMedium
        InfoChip(stringResource(R.string.trust_official), Icons.AutoMirrored.Outlined.FactCheck, onDark = true, textStyle = style)
        InfoChip(stringResource(R.string.trust_local), Icons.Outlined.Lock, onDark = true, textStyle = style)
        InfoChip(stringResource(R.string.trust_offline), Icons.Outlined.OfflinePin, onDark = true, textStyle = style)
    }
}

/**
 * 나라 사진 타일. [large]면 1열 큰 타일, 아니면 2열 칸. 칩은 차이 정보(입국 조건) 1개 —
 * [detailChips](맨 위 1장)일 때만 입력 도우미 칩을 더한다(같은 칩이 타일마다 반복되지 않게, 6-01).
 * TalkBack 이름은 `home_country_open`, 칩은 stateDescription(CountryPhotoTile, D16).
 */
@Composable
private fun HomeCountryTile(
    c: HomeCountry,
    large: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    detailChips: Boolean = false,
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
        if (detailChips) {
            if (c.visa?.apply != null) add(ChipSpec(Icons.Outlined.EditNote, stringResource(R.string.home_chip_visa_apply)))
            else if (c.hasForm) add(ChipSpec(Icons.Outlined.EditNote, stringResource(R.string.home_chip_form)))
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
 * 출국하는 날 순서: 공항 사진 머리 + 번호 단계 5개(1단계는 제목 + 보조문).
 * 글자가 크면 공용 StepList가 단계 아이콘을 빼고(번호 원만), PhotoHeaderCard는 제목 아이콘을 첫 줄에 맞춘다(2단계 통합).
 */
@Composable
private fun DepartureCard() {
    PhotoHeaderCard(
        Photos.Airport,
        stringResource(R.string.home_departure_title),
        icon = Icons.Outlined.FlightTakeoff,
    ) {
        StepList(
            listOf(
                Step(
                    stringResource(R.string.today_departure_step1),
                    Icons.Outlined.LocalAirport,
                    stringResource(R.string.today_departure_step1_detail),
                ),
                Step(stringResource(R.string.today_departure_step2), Icons.Outlined.Luggage),
                Step(stringResource(R.string.today_departure_step3), Icons.Outlined.Security),
                Step(stringResource(R.string.today_departure_step4), Icons.Outlined.HowToReg),
                Step(stringResource(R.string.today_departure_step5), Icons.Outlined.MeetingRoom),
            ),
        )
    }
}

/**
 * 꼭 챙길 물건: 짐 사진 머리 + 주제 3개(이름만 — 값 없음, 누를 수 없는 InfoChip — 재검토 R1) + 진행(있을 때) + 준비물 확인 버튼.
 * 진행 줄은 큰 숫자 `n / 5`와 설명을 글자 기준선에 맞춰 한 줄로, 큰 글자 배치에서는 큰 숫자를 설명 위로 쌓는다.
 * 아이콘은 '꼭 챙길 물건' 개념 하나(IconKeys.essentials — 홈·여행 준비·꼭 챙길 물건 화면 공통, 재검토 R11).
 */
@Composable
private fun EssentialsCard(summary: EssentialsSummary, onOpen: () -> Unit) {
    val dimens = LocalDimens.current
    val large = isStackedLayout()
    PhotoHeaderCard(
        Photos.Packing,
        stringResource(R.string.prepare_items_title),
        icon = IconKeys.essentials,
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoChip(stringResource(R.string.home_items_plug), Icons.Outlined.Power, tone = BadgeTone.Accent)
            InfoChip(stringResource(R.string.home_items_voltage), Icons.Outlined.ElectricBolt, tone = BadgeTone.Accent)
            InfoChip(stringResource(R.string.home_items_powerbank), Icons.Outlined.BatteryChargingFull, tone = BadgeTone.Accent)
        }
        if (summary.total > 0) {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                val stat = stringResource(R.string.essentials_progress_stat, summary.done, summary.total)
                val label = keepWords(stringResource(R.string.essentials_progress, summary.total, summary.done))
                val statStyle = LocalTypeExtras.current.statSmall
                val labelStyle = MaterialTheme.typography.bodyMedium
                if (large) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stat, style = statStyle, color = Tokens.Accent)
                        Text(label, style = labelStyle, color = Tokens.InkSecondary)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stat, style = statStyle, color = Tokens.Accent, modifier = Modifier.alignByBaseline())
                        Text(label, style = labelStyle, color = Tokens.InkSecondary, modifier = Modifier.alignByBaseline().weight(1f))
                    }
                }
                LinearProgressIndicator(
                    progress = { summary.done.toFloat() / summary.total },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Tokens.Accent,
                    trackColor = Tokens.SurfaceSunken,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
        PrimaryButton(stringResource(R.string.home_essentials_open), onClick = onOpen, icon = IconKeys.essentials)
    }
}

/**
 * 02 내 여행 요약 (DESIGN_SPEC 6-02): Accent 채움 카드 — 나라 사진 원형 썸네일(장식) + eyebrow `내 여행 · 태국`(White85)
 * + 출발까지 큰 숫자(stat) + 날짜 한 줄 + 흰 주 버튼. 어두운 채움 위라 onDark 내용 세트만 쓴다(D18).
 * 큰 글자 배치에서는 썸네일을 글 위로 올리고, 큰 숫자는 칸 폭에 맞춰 한 줄에 들어가는 크기(stat → statSmall)로 그린다(FitText, 재검토 R5·R6).
 */
@Composable
private fun TripCountdownCard(trip: HomeTrip, today: LocalDate, onOpen: () -> Unit) {
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
            PrimaryButton(
                stringResource(R.string.home_trip_open),
                onClick = onOpen,
                // 앞에 붙는 아이콘이라 '>'(NavigateNext)보다 내 여행 탭과 같은 짐가방이 자연스럽다 (BUNDLE_A_NOTES ②)
                icon = Icons.Outlined.Luggage,
                colors = ButtonStyles.onDark(content = Tokens.Accent),
            )
        }
    }
}

/** 여행 나라 사진 원형 썸네일(장식, 축소 디코딩). 사진이 없으면 비행기 아이콘 배지 */
@Composable
private fun TripThumbnail(code: String?) {
    val size = if (LocalDimens.current.easyMode) 64.dp else 56.dp
    val thumb = rememberThumbnail(code?.let { Photos.country(it) }, size)
    if (thumb != null) {
        Image(
            bitmap = thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(2.dp, OnDark.content, CircleShape),
        )
    } else {
        IconBadge(Icons.Outlined.FlightTakeoff, tone = BadgeTone.OnDark, size = size, shape = CircleShape)
    }
}
