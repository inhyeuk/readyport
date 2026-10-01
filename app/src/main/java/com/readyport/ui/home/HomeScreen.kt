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
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.Badge
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
import com.readyport.trip.TripRepository
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
        HomeUi(
            countries = countries,
            trip = homeTrip,
            // 여행 준비(18)와 같은 계산 — 진행 n/5 + 값이 있는 정보 칩(여행 나라 전기 · 기내 반입만 되는 물건)과 그 출처
            essentials = essentialsSummary(index, tripPack, s.haveItems),
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
 * 홈 (DESIGN_SPEC 6-01·6-02, 재검토 R13): 사진 히어로(앱 이름 · 질문 · **핵심 가치 한 줄** · 신뢰 표시) → (여행 있으면) 출발까지 카드
 * → 나라 사진 타일(큰 1장 + 2열, 칩은 나라마다 입국 조건 1개) + 출처 + 소개 문장 → 급할 때는 도움
 * → 여행 준비 기본 정보(출국 순서 · 꼭 챙길 물건 · 귀국 전 확인 요약 · 여권 등록).
 * - 가치 문장(`입국 카드 칸은 앱이 채우고, 제출만 직접 눌러요`)은 어느 모드에서나 히어로 안이다. 첫 화면 예산(HomeFirstScreenTest —
 *   쉬운 모드에서 두 번째 나라 사진이 첫 화면에 보여야 한다)을 지키려고 1열(쉬운 모드·큰 글자)에서는 신뢰 표시 3개를 히어로에서
 *   나라 목록 아래 소개 문장 밑으로 옮긴다(숨기지 않고 자리만 — 재검토 R5. 가치 문장의 `제출만 직접`이 히어로에 남는다).
 *   소개 문장(`home_subtitle`)은 모든 모드에서 나라 목록 바로 아래 안내 줄이다(히어로 글을 가치 문장 하나로).
 * - 나라 타일 칩은 나라마다 같은 구성(입국 조건 1개 — 재검토 R19). `입력 도우미`는 칩 대신 히어로 가치 문장이 말한다.
 * - 주 버튼(채움)은 화면에 하나(원칙 7): 여행이 있으면 출발까지 카드의 `내 여행 보기`, 없으면 `준비물 확인하기`.
 *   여권 카드 버튼은 늘 테두리 보조 버튼.
 * - 쉬운 모드: 여행 준비 기본 정보 카드 넷을 한 줄씩 접어 둔다(누르면 그 자리에서 카드가 펼쳐지고, 아래 `접기`로 다시 접는다) —
 *   히어로·여행 카드·나라·도움이 먼저 보이고 화면 길이가 절반 아래로 준다. 내용은 그대로 다 열어 볼 수 있다.
 * 큰 글자 배치에서는 공용 부품이 출국 단계 아이콘을 글 첫 줄 안으로 옮기고 사진 머리 아이콘은 제목 첫 줄에 맞춘다.
 */
@Composable
fun HomeContent(ui: HomeUi, actions: HomeActions, today: LocalDate = LocalDate.now()) {
    val columns = rememberGridColumns()
    val easy = LocalDimens.current.easyMode
    val fallback = stringResource(R.string.source_official_fallback)
    var departureOpen by rememberSaveable { mutableStateOf(false) }
    var essentialsOpen by rememberSaveable { mutableStateOf(false) }
    var returnOpen by rememberSaveable { mutableStateOf(false) }
    var passportOpen by rememberSaveable { mutableStateOf(false) }
    val essentialsPrimary = ui.trip == null
    val departureTitle = stringResource(R.string.home_departure_title)
    val essentialsTitle = stringResource(R.string.prepare_items_title)
    val returnTitle = stringResource(R.string.shopping_return_title)
    val passportTitle = stringResource(R.string.home_passport_title)
    val essentialsProgress = if (ui.essentials.total > 0) {
        stringResource(R.string.essentials_progress, ui.essentials.total, ui.essentials.done)
    } else {
        null
    }
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
        header = { HomeHero(singleColumn = columns == 1, hasTrip = ui.trip != null) },
        // 홈 자신에서는 `처음으로`를 숨긴다(눌러도 아무 일 없음) — 쉬운 모드는 `소리로 듣기`만 폭 전체 (재검토2 ⑤#12)
        showHomeAction = false,
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
        // 급할 때는 도움 — 나라 바로 다음(쉬운 모드 우선순위: 히어로 · 여행 · 나라 · 도움, 재검토 R13)
        item(key = "help") { HelpShortcutRow(actions.openHelp) }

        sectionGap("basics-gap")
        item(key = "basics-title") { SectionHeader(stringResource(R.string.home_basics_title)) }
        item(key = "departure") {
            Foldable(easy, departureOpen, departureTitle, Icons.Outlined.FlightTakeoff, { departureOpen = it }) { DepartureCard() }
        }
        item(key = "essentials") {
            Foldable(easy, essentialsOpen, essentialsTitle, IconKeys.essentials, { essentialsOpen = it }, body = essentialsProgress) {
                EssentialsCard(ui.essentials, actions.openEssentials, primary = essentialsPrimary)
            }
        }
        if (ui.returnFacts.isNotEmpty() || ui.returnLinks.isNotEmpty()) {
            item(key = "return") {
                Foldable(easy, returnOpen, returnTitle, Icons.Outlined.Inventory2, { returnOpen = it }) {
                    ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, actions.openLink, ReturnCheckMode.Summary)
                }
            }
        }
        item(key = "passport") {
            Foldable(easy, passportOpen, passportTitle, Icons.Outlined.Lock, { passportOpen = it }, tone = BadgeTone.Navy) {
                PassportCard(actions.openMyInfo)
            }
        }
    }
}

/**
 * 여권 등록 카드 (Navy, onDark 내용 세트). 버튼은 테두리 보조 버튼 — 화면의 채운 주 버튼은 하나(원칙 7, 재검토 R13).
 */
@Composable
private fun PassportCard(onOpen: () -> Unit) {
    CardNewsCard(
        title = stringResource(R.string.home_passport_title),
        icon = Icons.Outlined.Lock,
        body = stringResource(R.string.home_passport_body),
        style = NewsStyle.Navy,
    ) {
        SecondaryButton(stringResource(R.string.home_passport_open), onClick = onOpen, icon = Icons.Outlined.Badge, onDark = true)
    }
}

/**
 * 쉬운 모드에서 접어 두는 카드 (재검토 R13): 접혀 있으면 한 줄(아이콘 배지 + 제목 + [body] + 펼침 표시 — 줄 전체가 버튼, `접힘`),
 * 누르면 그 자리에 원래 카드를 그대로 펼치고 바로 아래 `접기` 줄을 둔다. 기본 모드([easy] = false)는 늘 펼친 카드.
 * 정보를 숨기지 않는다 — 한 번 누르면 원래 카드 전체가 보인다.
 */
@Composable
private fun Foldable(
    easy: Boolean,
    open: Boolean,
    title: String,
    icon: ImageVector,
    onOpenChange: (Boolean) -> Unit,
    body: String? = null,
    tone: BadgeTone = BadgeTone.Accent,
    content: @Composable () -> Unit,
) {
    if (!easy) {
        content()
        return
    }
    // 펼치면 누른 줄이 사라지고 카드가 그 자리에 온다 — 바뀐 내용을 TalkBack이 읽게 늘 있는 상자에 liveRegion (재검토2 ②#2)
    Box(Modifier.foldLiveRegion()) {
        if (!open) {
            FoldRow(title, icon, tone, body) { onOpenChange(true) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner)) {
                content()
                FoldBackRow(title) { onOpenChange(false) }
            }
        }
    }
}

/**
 * 접힌 카드 한 줄: 흰 그림자 카드 안 목록 행(ListRow와 같은 여백 토큰·배지·배치 — BadgeTitleLayout) + 끝에 펼침 표시(ExpandMore).
 * 줄 전체가 버튼이고 상태는 `접힘`. 큰 글자 배치(Stacked)에서는 **네 줄 모두** 배지·펼침 표시를 윗줄에, 제목·설명을 폭 전체로 —
 * 줄마다 '제목이 옆에 들어가는지'로 따로 정하면 한 목록 안에서 모양이 섞였다(재검토2 ②#10·④#6). 숨기는 글은 없다.
 * (ListRow의 끝 요소는 다음 화면 꺾쇠라 '펼침'과 뜻이 달라 같은 배치 부품으로 직접 짠다)
 */
@Composable
private fun FoldRow(title: String, icon: ImageVector, tone: BadgeTone, body: String?, onOpen: () -> Unit) {
    val dimens = LocalDimens.current
    val collapsed = stringResource(R.string.state_collapsed)
    val titleStyle = MaterialTheme.typography.titleMedium
    val iconSize = textIconSize(dimens.icon, titleStyle)
    val stacked = isStackedListRow()
    val chevron: @Composable () -> Unit = {
        Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(iconSize))
    }
    val titleText: @Composable () -> Unit = { KoText(title, titleStyle, color = Tokens.Ink, glueShort = true) }
    val bodyText: (@Composable () -> Unit)? = body?.let { b -> { KoText(b, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary) } }
    ListGroup {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.listRowMinHeight)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics { stateDescription = collapsed }
                .padding(horizontal = dimens.listRowPadding, vertical = dimens.listRowPaddingVertical),
            verticalAlignment = if (body != null || stacked) Alignment.Top else Alignment.CenterVertically,
        ) {
            if (stacked) {
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon, tone = tone)
                        Spacer(Modifier.weight(1f))
                        chevron()
                    }
                    Box(Modifier.padding(top = dimens.inner)) { titleText() }
                    if (bodyText != null) Box(Modifier.padding(top = 2.dp)) { bodyText() }
                }
            } else {
                BadgeTitleLayout(
                    title = titleText,
                    modifier = Modifier.weight(1f),
                    badge = { IconBadge(icon, tone = tone) },
                    trailing = chevron,
                    below = bodyText,
                    gap = 16.dp,
                )
            }
        }
    }
}

/** 펼친 카드 아래 `접기` 줄 (ExpandableDetail 펼침 줄과 같은 모양). TalkBack 이름은 무엇을 접는지(`출국하는 날, 이 순서대로 접기`) */
@Composable
private fun FoldBackRow(title: String, onFold: () -> Unit) {
    val dimens = LocalDimens.current
    val expanded = stringResource(R.string.state_expanded)
    val name = stringResource(R.string.home_fold_less_cd, title)
    val style = MaterialTheme.typography.labelLarge
    Row(
        Modifier
            .fillMaxWidth()
            .minTouch()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onFold)
            .semantics {
                contentDescription = name
                stateDescription = expanded
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KoText(stringResource(R.string.action_less), style, Modifier.weight(1f), color = Tokens.Accent)
        Icon(
            Icons.Outlined.ExpandLess,
            contentDescription = null,
            tint = Tokens.Accent,
            modifier = Modifier.size(textIconSize(dimens.icon, style)),
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
 * 꼭 챙길 물건: 짐 사진 머리 + **값이 있는 정보 칩**(여행 나라 전기 `한국 플러그 그대로 써요`·`220 V 전압`, `보조배터리 기내 반입만 가능` —
 * 값 없는 주제 이름만 늘어놓지 않는다, 재검토2 ①#15·②#9) + 진행 `n / 5`와 막대(여행 준비 18과 같은 부품, ③#10) + 준비물 확인 버튼 + 칩 값의 출처.
 * 아이콘은 '꼭 챙길 물건' 개념 하나(IconKeys.essentials — 홈·여행 준비·꼭 챙길 물건 화면 공통, 재검토 R11).
 * [primary] = false(여행이 있어 출발까지 카드의 `내 여행 보기`가 주 버튼)면 보조 버튼 — 화면의 채운 버튼은 하나(원칙 7, 재검토 R13).
 */
@Composable
private fun EssentialsCard(summary: EssentialsSummary, onOpen: () -> Unit, primary: Boolean) {
    PhotoHeaderCard(
        Photos.Packing,
        stringResource(R.string.prepare_items_title),
        icon = IconKeys.essentials,
    ) {
        EssentialsChips(summary)
        EssentialsProgress(summary)
        if (primary) {
            PrimaryButton(stringResource(R.string.home_essentials_open), onClick = onOpen, icon = IconKeys.essentials)
        } else {
            SecondaryButton(stringResource(R.string.home_essentials_open), onClick = onOpen, icon = IconKeys.essentials)
        }
        // 칩 값(팩 전기·기내 반입 기준)이 출처 없이 보이지 않게 — 카드 맨 아래(원칙 5)
        val sources = essentialsSources(summary)
        if (sources.isNotEmpty()) Box(Modifier.padding(top = 4.dp)) { SourceList(sources) }
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
