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
import androidx.compose.material.icons.outlined.DateRange
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.pack.PackRepository
import com.readyport.pack.Requirement
import com.readyport.trip.ChecklistProvider
import com.readyport.trip.Trip
import com.readyport.trip.TripRepository
import com.readyport.trip.TripSelection
import com.readyport.trip.TripTiming
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BadgeTitleLayout
import com.readyport.ui.components.ButtonStyles
import com.readyport.ui.components.CheckProgressBar
import com.readyport.ui.components.ChipSpec
import com.readyport.ui.components.CountryPhotoTile
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
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.TextCircle
import com.readyport.ui.components.FitLines
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.rememberLayoutInfo
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.noBreak
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.tileRows
import com.readyport.ui.onboarding.AppSymbol
import com.readyport.ui.theme.LocalDimens
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
 * [trips]: 히어로에 **흰 박스로 하나씩** 보여 줄 여행 — 여행 중 먼저, 그다음 떠나는 날 가까운 순,
 *   최대 [MAX_TRIP_BOXES]개(그보다 많으면 `여행 n개 모두 보기` 줄). 지난 여행은 들어오지 않는다.
 * [activeTrips]: 여행 중·다가오는 여행 **전체** 수, [pastTrips]: 끝난 여행 수.
 */
data class HomeUi(
    val countries: List<HomeCountry> = emptyList(),
    val trips: List<HomeTrip> = emptyList(),
    val activeTrips: Int = 0,
    val pastTrips: Int = 0,
)

/**
 * 히어로에 흰 박스로 둘 여행 수 (2026-10-03 부록 H.7).
 * 박스 하나가 나라 이름 + 출발까지 + 날짜 + 체크리스트 + 막대라 기본 모드에서 약 150dp, 쉬운 모드에서 약 230dp다.
 * 셋을 두면 히어로만으로 휴대폰 첫 화면을 다 먹어 **둘러보기의 본일(어느 나라로 갈까)인 나라 사진이 첫 화면에서 사라진다**
 * (운영자 결정 9). 둘이면 현실의 거의 모든 경우(여행 중 하나 + 다음 하나)를 담고 첫 나라 타일이 남는다 —
 * 더 많으면 `여행 n개 모두 보기`가 내 여행 목록으로 데려간다.
 */
const val MAX_TRIP_BOXES = 2

/**
 * 히어로 박스에 올릴 여행 순서 (순수 함수 — 단위 테스트가 이 규칙만 본다):
 * **여행 중 먼저, 그다음 떠나는 날 가까운 순**(내 여행 목록과 같은 순서). 지난 여행은 들어오지 않는다 —
 * 그것은 `예전 여행지 다시보기`가 맡는다(운영자 2026-10-03).
 */
internal fun heroTripOrder(trips: List<Trip>, today: LocalDate): List<Trip> =
    TripSelection.ordered(trips, today).filter { (timing, _) -> timing != TripTiming.Past }.map { it.second }

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
        // 히어로 박스 순서 = 내 여행 목록과 같은 순서(여행 중 → 다가오는 여행 가까운 순). 지난 여행은 `예전 여행지 다시보기`가 맡는다
        val active = heroTripOrder(book.trips, today)
        // 체크리스트는 **보여 줄 박스만** 센다(여행이 많아도 둘러보기가 느려지지 않게)
        val boxes = active.take(MAX_TRIP_BOXES).mapNotNull { t ->
            runCatching {
                val pack = packs.pack(t.country)?.value
                val data = checklists.build(t, book, today)
                HomeTrip(
                    pack?.names?.ko ?: t.country, LocalDate.parse(t.startDate), LocalDate.parse(t.endDate), t.country, t.id,
                    checklistDone = data.done, checklistTotal = data.total,
                )
            }.getOrNull()
        }
        val past = book.trips.count { it.datesValid && TripSelection.timing(it, today) == TripTiming.Past }
        HomeUi(countries = countries, trips = boxes, activeTrips = active.size, pastTrips = past)
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
    val featured = ui.countries.firstOrNull { it.code == ui.trips.firstOrNull()?.code } ?: ui.countries.firstOrNull()
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
            HeroTagline()
            if (!singleColumn) TrustStrip(Modifier.padding(top = 6.dp), onDark = true)
            // 여행으로 가는 길 — 같은 스크림 영역 안, 브랜드·소개 글 묶음과 조금 떼어 둔다
            Column(
                Modifier.padding(top = dimens.inner),
                verticalArrangement = Arrangement.spacedBy(dimens.inner),
            ) {
                if (ui.trips.isNotEmpty()) HeroTrips(ui, actions, today)
                HeroTripActions(ui, actions)
            }
        }
    }
}

/**
 * 히어로 한 줄 소개 (운영자 2026-10-03 — 부록 H.7).
 * 운영자 지적(그대로): *"어디로 떠나세요에서 '입국 카드 … 눌러요'라는 문구가 첫화면에 있어서 뜬금 없는 의미를 전달하고 있어.
 * 따라서 '레디포트는 당신의 여행이 수월해지도록 돕습니다.'라는 문구를 작게 2줄 이내로 표시해줘."*
 * - 문장은 **운영자가 적어 준 그대로**다 — 앱의 다른 글은 해요체인데 이 한 줄만 합니다체인 것은 **일부러 둔 예외**다.
 * - 작은 글자(labelMedium — 바로 아래 신뢰 표시와 같은 크기, 사진 스크림 위에서 흰 SemiBold라 또렷하다).
 * - **두 줄을 넘지 않는다**: 글자를 키운 사람(200%)·좁은 창(360dp)·쉬운 모드에서는 줄이 늘어나는 대신 글자를 조금씩 줄여
 *   두 줄에 맞춘다([FitLines]). 줄이는 한도는 `1 / 글자 배율`까지 — 100%의 기본 크기(기본 13sp·쉬운 모드 18sp)로 그려지는
 *   **실제 크기보다 작아지지 않는다**(키운 배율만 되돌리는 셈이다. 쉬운 모드 18sp 아래로 내려가지 않는다는 뜻).
 * - 예전 `입국 카드 칸은 앱이 채우고, 제출만 직접 눌러요`(`home_value_prop`)는 **첫 실행 안내와 스토어 그래픽에 그대로 남는다** —
 *   그 자리에서는 앱을 처음 보는 사람에게 맞는 말이다. 둘러보기 첫 화면에서만 바꿨다.
 */
@Composable
private fun HeroTagline(modifier: Modifier = Modifier) {
    val base = MaterialTheme.typography.labelMedium
    val scale = rememberLayoutInfo().textScale
    val floor = (1f / scale).coerceIn(MIN_TAGLINE_FACTOR, 1f)
    val factors = listOf(1f, 0.86f, 0.74f, 0.62f, floor).map { maxOf(it, floor) }.distinct()
    val styles = factors.map { f ->
        if (f >= 1f) base else base.copy(fontSize = base.fontSize * f, lineHeight = base.lineHeight * f)
    }
    FitLines(
        stringResource(R.string.explore_hero_tagline),
        styles = styles,
        color = OnDark.content,
        maxLines = TAGLINE_MAX_LINES,
        modifier = modifier,
    )
}

/** 히어로 소개 한 줄의 최대 줄 수 (운영자: `작게 2줄 이내로`) */
internal const val TAGLINE_MAX_LINES = 2

/** 소개 한 줄을 줄일 수 있는 최소 비율 — 글자 200%를 100% 크기로 되돌리는 선 */
private const val MIN_TAGLINE_FACTOR = 0.5f

/**
 * 히어로의 **내 여행 흰 박스들** (운영자 2026-10-03 — 부록 H.7).
 * 운영자 지적(그대로): *"내 여행이 1개 이상일 경우, 여행을 흰색 박스로 각 여행을 구분해 주고, 여행 국가는 좀 더 선명하게
 * 표시하고, 둥근 박스 형태로 1, 2,.. 로 번호를 매겨줘."*
 * - 묶음 머리글은 `내 여행` 한 줄(eyebrow) — 나라 이름은 이제 박스 안에 크게 있어서 `내 여행 · 태국`을 되풀이하지 않는다.
 * - 박스는 최대 [MAX_TRIP_BOXES]개. 더 있으면 `여행 n개 모두 보기`가 내 여행 목록으로 데려간다.
 */
@Composable
private fun HeroTrips(ui: HomeUi, actions: HomeActions, today: LocalDate) {
    val dimens = LocalDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
        Text(
            stringResource(R.string.tab_trip),
            style = MaterialTheme.typography.labelMedium,
            color = OnDark.eyebrow,
        )
        ui.trips.forEachIndexed { i, trip ->
            HeroTripBox(number = i + 1, trip = trip, today = today, onOpen = { actions.openTrip(trip.id) })
        }
        if (ui.activeTrips > ui.trips.size) {
            SecondaryButton(
                stringResource(R.string.explore_trips_all, ui.activeTrips),
                onClick = actions.openTrips,
                // 내 여행 탭과 같은 짐가방 (BUNDLE_A_NOTES ②)
                icon = Icons.Outlined.Luggage,
                onDark = true,
            )
        }
    }
}

/**
 * 여행 한 박스 (사진 위 **흰 Surface** — 어두운 스크림 위라 테두리 없이도 또렷하게 떨어진다. 그림자는 사진 위에서 탁해져 쓰지 않는다).
 * 줄 차례: **둥근 번호 + 나라 이름(크게)** → `출발 3일 전` 태그 → 날짜 → `체크리스트 12 / 28` + 진행 막대.
 * - 나라 이름은 titleLarge(기본 20sp·쉬운 모드 24sp, Bold) — 예전 히어로에서 가장 큰 글자였던 `출발 3일 전`은 태그로 내려
 *   **나라가 가장 선명한 글자**가 되게 했다(운영자 요청).
 * - 박스 전체가 그 여행으로 가는 단추다. TalkBack은 한 번에 `여행 1, 태국, 출발 3일 전, 11월 3일 (화) ~ 11월 7일 (토), 체크리스트 12 / 28`.
 */
@Composable
private fun HeroTripBox(number: Int, trip: HomeTrip, today: LocalDate, onOpen: () -> Unit) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.large
    val days = ChronoUnit.DAYS.between(today, trip.startDate).toInt()
    val status = when {
        days > 0 -> stringResource(R.string.home_trip_days, days)
        days == 0 -> stringResource(R.string.home_trip_today)
        !today.isAfter(trip.endDate) -> stringResource(R.string.home_trip_during)
        else -> stringResource(R.string.home_trip_after)
    }
    val format = DateTimeFormatter.ofPattern(stringResource(R.string.home_trip_date_format), Locale.KOREAN)
    // 같은 해·같은 달이면 끝 날짜의 달을 뺀다(`10월 8일 (목) ~ 14일 (수)`) — 큰 글자에서도 날짜가 한 줄에 들어가게
    val sameMonth = trip.startDate.year == trip.endDate.year && trip.startDate.month == trip.endDate.month
    val endFormat = if (sameMonth) DateTimeFormatter.ofPattern(stringResource(R.string.home_trip_date_format_day), Locale.KOREAN) else format
    val dates = stringResource(R.string.home_trip_dates, noBreak(trip.startDate.format(format)), noBreak(trip.endDate.format(endFormat)))
    val numberName = stringResource(R.string.explore_trip_number_cd, number)
    val meta = MaterialTheme.typography.labelMedium
    Surface(
        onClick = onOpen,
        color = Tokens.Surface,
        contentColor = Tokens.Ink,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { role = Role.Button },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.inner),
        ) {
            // 둥근 번호 + 나라 이름을 한 줄에 — 이름이 번호 옆 남는 폭 전체를 쓴다.
            // `출발 3일 전` 태그는 이름 **옆**에 두면 큰 글자에서 이름이 `인도/네시/아`처럼 세 줄로 쪼개져(운영자 실기기 지적)
            // 이름 아래 줄로 내렸다. 단계 번호는 네모, 여행 번호는 둥근 원(부록 H.7). TalkBack은 `여행 1`
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextCircle(number.toString(), modifier = Modifier.clearAndSetSemantics { contentDescription = numberName })
                KoText(
                    trip.countryKo,
                    MaterialTheme.typography.titleLarge,
                    color = Tokens.Ink,
                    glueShort = true,
                    modifier = Modifier.weight(1f),
                )
            }
            StatusTag(status, StatusKind.Info, icon = Icons.Outlined.FlightTakeoff)
            InfoChip(dates, Icons.Outlined.DateRange, textStyle = meta)
            // 이 여행 체크리스트 진행 — 앱 안 값(누를 수 없는 칩 + 막대)
            if (trip.checklistTotal > 0) {
                InfoChip(
                    stringResource(R.string.ck_now_eyebrow, trip.checklistDone, trip.checklistTotal),
                    IconKeys.essentials,
                    textStyle = meta,
                )
                CheckProgressBar(trip.checklistDone, trip.checklistTotal)
            }
        }
    }
}

/**
 * 히어로의 여행 버튼 (2026-10-03 부록 H.7로 고쳐 씀).
 * 이제 **여행마다 흰 박스가 그 여행으로 가는 길**이라서 예전의 `내 여행 점검` 채움 버튼은 지웠다 —
 * 같은 일을 하는 자리가 둘이 되고, 둘 이상일 때는 '어느 여행인지' 다시 고르게 해 한 번 더 누르게 했다.
 * 그래서 **채움 버튼은 언제나 `새 여행 만들기`** 하나다(원칙 7 — 화면에 채운 버튼 하나):
 * 박스로 갈 수 없는 단 하나의 할 일이고, 여행이 없을 때 화면에서 할 수 있는 유일한 일이다.
 * 끝난 여행이 있으면 `예전 여행지 다시보기`가 테두리 버튼으로 아래에 붙는다. 사진 위라 흰 채움 + Accent 글자(D18).
 */
@Composable
private fun HeroTripActions(ui: HomeUi, actions: HomeActions) {
    val dimens = LocalDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
        PrimaryButton(
            stringResource(R.string.today_new_trip),
            onClick = actions.makeTrip,
            icon = Icons.Outlined.EditCalendar,
            colors = ButtonStyles.onDark(Tokens.Accent),
        )
        if (ui.pastTrips > 0) {
            SecondaryButton(
                stringResource(R.string.explore_past_trips),
                onClick = actions.openPastTrips,
                icon = Icons.Outlined.History,
                onDark = true,
            )
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
