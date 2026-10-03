package com.readyport.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.pack.PackRepository
import com.readyport.pack.Requirement
import com.readyport.trip.ChecklistProvider
import com.readyport.trip.TripRepository
import com.readyport.trip.TripSelection
import com.readyport.trip.TripTiming
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.ButtonStyles
import com.readyport.ui.components.CheckProgressBar
import com.readyport.ui.components.ChipSpec
import com.readyport.ui.components.CountryPhotoTile
import com.readyport.ui.components.EqualWidthPair
import com.readyport.ui.components.FitText
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.InfoChip
import com.readyport.ui.components.KoText
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.PhotoBox
import com.readyport.ui.components.PhotoTextArea
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SourceList
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.noBreak
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.tileRows
import com.readyport.ui.onboarding.AppSymbol
import com.readyport.ui.onboarding.ValuePropText
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
 * [checklistDone]/[checklistTotal]: 그 여행 진행(0이면 표시 없음)
 */
data class HomeTrip(
    val countryKo: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val code: String? = null,
    val id: String = "",
    val checklistDone: Int = 0,
    val checklistTotal: Int = 0,
)

/**
 * [trip]: 지금 가리키는 여행(TripSelection.active — 여행 중 → 다가오는 → 최근 지난 여행 차례).
 * [activeTrips]: 여행 중·다가오는 여행 수, [pastTrips]: 끝난 여행 수 — 히어로의 여행 버튼 세 개를 이 수가 정한다.
 */
data class HomeUi(
    val countries: List<HomeCountry> = emptyList(),
    val trip: HomeTrip? = null,
    val activeTrips: Int = 0,
    val pastTrips: Int = 0,
)

data class HomeActions(
    val openCountry: (String) -> Unit = {},
    /** 그 여행 화면으로 (내 여행 탭의 한 여행 화면) */
    val openTrip: (String) -> Unit = {},
    /** 내 여행 목록 */
    val openTrips: () -> Unit = {},
    /** 내 여행 목록의 `지난 여행` 묶음을 펼친 채로 — 히어로 `예전 여행지 다시보기` */
    val openPastTrips: () -> Unit = {},
    /** 새 여행 만들기 */
    val makeTrip: () -> Unit = {},
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
                    checklistDone = data?.done ?: 0, checklistTotal = data?.total ?: 0,
                )
            }.getOrNull()
        }
        val valid = book.trips.filter { it.datesValid }
        val past = valid.count { TripSelection.timing(it, today) == TripTiming.Past }
        HomeUi(countries = countries, trip = homeTrip, activeTrips = valid.size - past, pastTrips = past)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}

@Composable
fun HomeScreen(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    HomeContent(ui = ui, actions = actions)
}

/**
 * 둘러보기 (예전 '홈', 2026-10-03 부록 H·H.5): **어디 갈까를 고르는 화면**이다 — 여행 흐름(출국 순서·꼭 챙길 물건·귀국 전 확인·여권)은
 * 모두 내 여행 탭의 한 여행 화면으로 옮겼다. 같은 일을 두 곳에서 하지 않는다.
 * 위에서부터 **단 두 덩어리**다: ① 히어로 한 장(`어디로 떠나세요?` + 내 여행 상태 + 여행 버튼) ② `어느 나라로 가세요?` + 나라 사진 타일 + 출처 + 소개 문장.
 * - 여행으로 가는 길은 히어로 **안에만** 있다(운영자 2026-10-03): 예전의 `새 여행 만들기` 카드와 `출발까지` 카드는 히어로가 흡수했다.
 * - 급할 때 도움은 아래 **도움 탭**이 맡는다 — 둘러보기 안의 `급할 때는 도움` 줄은 같은 길이 두 개여서 지웠다.
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
        header = { HomeHero(ui, actions, today, singleColumn = columns == 1) },
        // 둘러보기 자신에서는 `처음으로`를 숨긴다(눌러도 아무 일 없음) — 쉬운 모드는 `소리로 듣기`만 폭 전체
        showHomeAction = false,
    ) {
        // 나라 묶음 머리글은 제목 한 줄만 — `사진을 누르면 그 나라 안내가 열려요`(home_countries_body)는 맨 아래
        // 소개 문장(`나라를 고르면 입국 서류부터 … 알려 드려요`)과 같은 말이라 지웠다(한 화면에서 같은 말 두 번 금지, 운영자 2026-10-03)
        item(key = "countries-title") { SectionHeader(title = stringResource(R.string.home_countries_title)) }
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
    }
}

/**
 * 맨 위 히어로 **한 장**: 사진 위 스크림 글자 영역(PhotoTextArea) 하나에 앱 심볼·이름 + "어디로 떠나세요?"(heading)
 * + 핵심 가치 한 줄(재검토 R13 — 모든 모드) + 신뢰 표시 3개 + **내 여행 상태 + 여행 버튼**(운영자 2026-10-03)을 차례로 담는다.
 * - 여행 버튼을 사진 **안**에 두는 이유(운영자 말 그대로 `어디로 떠나세요 안에 배치`): 흰 몸통을 따로 붙이면 사진 아래 여백과 몸통 여백이
 *   겹쳐 첫 화면 예산(운영자 결정 9 — 1열에서도 나라 사진이 첫 화면에 보여야 한다)을 40dp 더 먹는다. 사진 위 글자는 이미 스크림 영역
 *   (아래로 갈수록 검정 0.60 → 0.88)에 있어 흰 채움 버튼·흰 테두리 버튼이 가장 또렷하다.
 * - 어두운 채움 위이므로 onDark 내용 세트만 쓴다(D18): 채움 주 버튼은 **흰 바탕 + Accent 글자**(`ButtonStyles.onDark`),
 *   보조는 투명 + 1.5dp 흰 테두리. 사진 위에 Accent 단색을 또 깔면 사진과 버튼의 경계가 흐려진다.
 * - 사진은 그릴 때만 밝기 보정(운영자 결정 11, 파일은 그대로).
 * - 신뢰 표시는 누를 수 없으므로 아이콘 + 글자(InfoChip onDark)로 한 줄에 흐르게 둔다(재검토 R1).
 *   1열([singleColumn] — 쉬운 모드·큰 글자)이면 나라 목록 아래로 옮긴다 — 첫 화면에서 나라 사진이 밀려나지 않게.
 */
@Composable
private fun HomeHero(ui: HomeUi, actions: HomeActions, today: LocalDate, singleColumn: Boolean) {
    val dimens = LocalDimens.current
    val brandStyle = MaterialTheme.typography.labelLarge
    // 심볼 지름 = 앱 이름 한 줄 높이(최소 24dp) — 글자를 키워도 이름과 크기가 어울리고 줄 높이를 늘리지 않는다
    val symbolSize = maxOf(24.dp, with(LocalDensity.current) { brandStyle.lineHeight.toDp() })
    PhotoBox(
        Photos.Home,
        // 1열은 글이 이미 길어 사진을 낮게(160dp), 2열은 글 위로 하늘이 보이게 220dp (나라 히어로와 같은 값)
        minHeight = if (singleColumn) 160.dp else 220.dp,
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
            // 여행으로 가는 길 — 같은 스크림 영역 안, 브랜드·가치 글 묶음과 조금 떼어 둔다
            Column(
                Modifier.padding(top = dimens.inner),
                verticalArrangement = Arrangement.spacedBy(dimens.inner),
            ) {
                ui.trip?.let { HeroTripStatus(it, today) }
                HeroTripActions(ui, actions)
            }
        }
    }
}

/**
 * 히어로의 내 여행 상태 (예전 `TripCountdownCard`가 하던 일 — 카드를 따로 두지 않는다):
 * eyebrow `내 여행 · 태국` → 결론 큰 숫자 `출발 3일 전`(원칙 1) → 날짜 한 줄 → 이 여행 체크리스트 진행.
 * 큰 글자에서는 큰 숫자를 칸 폭에 맞춰 한 줄에 들어가는 크기로 그린다(FitText, 재검토 R5·R6).
 * 바로 아래 `내 여행 점검` 버튼이 어느 여행으로 가는지 말해 주는 자리라 버튼은 여기 두지 않는다.
 */
@Composable
private fun HeroTripStatus(trip: HomeTrip, today: LocalDate) {
    val extras = LocalTypeExtras.current
    val days = ChronoUnit.DAYS.between(today, trip.startDate).toInt()
    val status = when {
        days > 0 -> stringResource(R.string.home_trip_days, days)
        days == 0 -> stringResource(R.string.home_trip_today)
        !today.isAfter(trip.endDate) -> stringResource(R.string.home_trip_during)
        else -> stringResource(R.string.home_trip_after)
    }
    val format = DateTimeFormatter.ofPattern(stringResource(R.string.home_trip_date_format), Locale.KOREAN)
    val dates = stringResource(R.string.home_trip_dates, noBreak(trip.startDate.format(format)), noBreak(trip.endDate.format(format)))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.home_trip_label, trip.countryKo),
            style = MaterialTheme.typography.labelMedium,
            color = OnDark.eyebrow,
        )
        FitText(status, styles = listOf(extras.stat, extras.statSmall), color = OnDark.content, breakChars = " ")
        InfoChip(dates, Icons.Outlined.FlightTakeoff, onDark = true)
        // 이 여행 체크리스트 진행 — 앱 안 값(누를 수 없는 칩 + 막대)
        if (trip.checklistTotal > 0) {
            InfoChip(
                stringResource(R.string.ck_now_eyebrow, trip.checklistDone, trip.checklistTotal),
                IconKeys.essentials,
                onDark = true,
            )
            CheckProgressBar(trip.checklistDone, trip.checklistTotal, onDark = true)
        }
    }
}

/**
 * 히어로의 여행 버튼 — 둘러보기에서 여행으로 가는 **단 하나의 자리**(운영자 2026-10-03).
 * 상태가 버튼을 정한다: 언제나 `새 여행 만들기`, 여행 중·다가오는 여행이 있으면 `내 여행 점검`, 끝난 여행이 있으면 `예전 여행지 다시보기`.
 * 채움 버튼은 하나(원칙 7): 할 일이 남은 여행이 있으면 `내 여행 점검`, 없으면 `새 여행 만들기`. 사진 위라 흰 채움 + Accent 글자(D18).
 * - `내 여행 점검`은 다가오는 여행이 하나면 그 여행 화면으로, 둘 이상이면 어느 여행인지 고르도록 내 여행 목록으로 간다.
 * - 보조 버튼이 둘이면 같은 폭으로 한 줄에, 반 폭에 한 줄로 안 들어가면(큰 글자) 위아래로 쌓고 둘 다 폭 전체(EqualWidthPair).
 */
@Composable
private fun HeroTripActions(ui: HomeUi, actions: HomeActions) {
    val dimens = LocalDimens.current
    val newTrip: @Composable (Modifier) -> Unit = { m ->
        SecondaryButton(
            stringResource(R.string.today_new_trip),
            onClick = actions.makeTrip,
            modifier = m,
            icon = Icons.Outlined.EditCalendar,
            onDark = true,
        )
    }
    val pastTrips: @Composable (Modifier) -> Unit = { m ->
        SecondaryButton(
            stringResource(R.string.explore_past_trips),
            onClick = actions.openPastTrips,
            modifier = m,
            icon = Icons.Outlined.History,
            onDark = true,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
        if (ui.activeTrips > 0) {
            val trip = ui.trip
            PrimaryButton(
                stringResource(R.string.explore_trip_check),
                onClick = { if (ui.activeTrips == 1 && trip != null) actions.openTrip(trip.id) else actions.openTrips() },
                // 앞에 붙는 뜻 아이콘 — 내 여행 탭과 같은 짐가방 (BUNDLE_A_NOTES ②)
                icon = Icons.Outlined.Luggage,
                colors = ButtonStyles.onDark(Tokens.Accent),
            )
            if (ui.pastTrips > 0) EqualWidthPair(dimens.inner, first = newTrip, second = pastTrips) else newTrip(Modifier)
        } else {
            PrimaryButton(
                stringResource(R.string.today_new_trip),
                onClick = actions.makeTrip,
                icon = Icons.Outlined.EditCalendar,
                colors = ButtonStyles.onDark(Tokens.Accent),
            )
            if (ui.pastTrips > 0) pastTrips(Modifier)
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
