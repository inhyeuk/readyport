package com.readyport.ui.country

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Outlet
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.CountryPack
import com.readyport.pack.FormInfo
import com.readyport.pack.Loaded
import com.readyport.pack.OfficialLink
import com.readyport.pack.PackOrigin
import com.readyport.pack.PackRepository
import com.readyport.pack.PackSync
import com.readyport.pack.PowerInfo
import com.readyport.pack.Requirement
import com.readyport.pack.Section
import com.readyport.pack.ShoppingItem
import com.readyport.pack.SourcedText
import com.readyport.pack.VisaApply
import com.readyport.prep.import
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.ChoiceSegments
import com.readyport.ui.components.DotBullet
import com.readyport.ui.components.Fact
import com.readyport.ui.components.FactGrid
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.ImportVerdictBadge
import com.readyport.ui.components.InfoChip
import com.readyport.ui.components.InfoTileGrid
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LinkRow
import com.readyport.ui.components.NewsStyle
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.PhotoBox
import com.readyport.ui.components.PhotoTextColumn
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.EntryFormCard
import com.readyport.ui.components.ExpandToggle
import com.readyport.ui.components.foldLiveRegion
import com.readyport.ui.components.feeTone
import com.readyport.ui.components.ReturnCheckCard
import com.readyport.ui.components.ReturnCheckMode
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SourceList
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.StatTile
import com.readyport.ui.components.Step
import com.readyport.ui.components.StepList
import com.readyport.ui.components.TileSpec
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.feeIcon
import com.readyport.ui.components.importLabel
import com.readyport.ui.components.koDisplay
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.rememberKeyIndex
import com.readyport.ui.components.resolveSourceName
import com.readyport.ui.components.scrollToKey
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.shortValue
import com.readyport.ui.components.sourceRefs
import com.readyport.ui.components.textIconSize
import com.readyport.ui.nav.CountryRoute
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CountryUi(
    val loaded: Loaded<CountryPack>? = null,
    val favorite: Boolean = false,
    /** 앱이 공식 사이트 입력을 채워 줄 수 있는 양식(레시피가 있는 것) */
    val autofillForms: Set<String> = emptySet(),
    val returnLinks: List<OfficialLink> = emptyList(),
    val returnFacts: List<SourcedText> = emptyList(),
    val indexSources: Map<String, String> = emptyMap(),
)

/** 나라 화면에서 다른 곳으로 가는 길 */
data class CountryActions(
    val back: () -> Unit = {},
    val openForm: (String) -> Unit = {},
    val planTrip: (String) -> Unit = {},
    val openHelp: (String) -> Unit = {},
    val openMove: () -> Unit = {},
    val openShopping: (String) -> Unit = {},
    val openVideos: (String) -> Unit = {},
    val openLink: (String) -> Unit = {},
    val toggleFavorite: () -> Unit = {},
)

@HiltViewModel
class CountryViewModel @Inject constructor(
    handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val packs: PackRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val country = handle.toRoute<CountryRoute>().country

    val ui: StateFlow<CountryUi> = combine(settings.settings, packs.revision) { s, _ ->
        val loaded = packs.pack(country)
        val index = packs.index()?.value
        CountryUi(
            loaded = loaded,
            favorite = country in s.favorites,
            autofillForms = loaded?.value?.forms.orEmpty().filter { packs.recipe(it.id) != null }.map { it.id }.toSet(),
            returnLinks = index?.returnLinks.orEmpty(),
            returnFacts = index?.returnFacts.orEmpty(),
            indexSources = index?.sources.orEmpty().associate { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CountryUi())

    fun toggleFavorite() = viewModelScope.launch {
        val now = settings.current()
        settings.setFavorite(country, country !in now.favorites)
        PackSync.requestNow(context, now.wifiOnly)
    }

    /** 도움 탭이 이 나라 문장·연락처를 먼저 보여 주게 */
    fun chooseForHelp() = viewModelScope.launch { settings.setHelpCountry(country) }
}

@Composable
fun CountryScreen(actions: CountryActions, viewModel: CountryViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CountryContent(
        ui = ui,
        actions = actions.copy(
            openLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
            openHelp = { code -> viewModel.chooseForHelp(); actions.openHelp(code) },
            toggleFavorite = { viewModel.toggleFavorite() },
        ),
    )
}

/** 나라 화면 안의 세 갈래. 스와이프 없이 버튼으로만 바꾼다 */
enum class CountrySection(val label: Int) {
    Entry(R.string.country_tab_entry),
    Travel(R.string.country_tab_travel),
    Shopping(R.string.country_tab_shopping),
}

/** 섹션 칸 아이콘 (DESIGN_SPEC 6-03: 입국·비자 Approval · 여행 정보 Explore · 쇼핑 ShoppingBag) */
private fun CountrySection.icon(): ImageVector = when (this) {
    CountrySection.Entry -> Icons.Outlined.Approval
    CountrySection.Travel -> Icons.Outlined.Explore
    CountrySection.Shopping -> Icons.Outlined.ShoppingBag
}

/** 출처 ID + 확인 날짜 → 화면에 보일 출처 한 줄 (이름을 못 찾으면 `공식 안내` — 내부 ID 금지) */
private typealias SourceOf = (id: String, lastVerified: String) -> SourceRef

@Composable
fun CountryContent(ui: CountryUi, actions: CountryActions, initialSection: CountrySection = CountrySection.Entry) {
    val loaded = ui.loaded ?: return
    val pack = loaded.value
    var section by rememberSaveable(pack.country) { mutableIntStateOf(initialSection.ordinal) }
    val fallback = stringResource(R.string.source_official_fallback)
    val names = remember(pack, ui.indexSources) { ui.indexSources + pack.sources.associate { it.id to it.name } }
    val sourceOf: SourceOf = { id, date -> SourceRef(resolveSourceName(id, names, fallback), displayDate(date)) }
    // 비자 온라인 신청이 지나가는 입국 신고 양식 (인도네시아 e-VOA → All Indonesia). 팩 신청 단계 1번이 '입국 신고 칸을 채워 제출'이므로
    // 양식 카드가 1단계(주 버튼), 비자 신청 카드가 2단계(보조 버튼) — 같은 화면에 파란 주 버튼이 둘이 되지 않게(원칙 7, 재검토 R12)
    val formIds = remember(pack) { pack.forms.map { it.id }.toSet() }
    val applyForms = remember(pack) { pack.requirements.mapNotNull { it.apply?.form }.filter { it in formIds }.toSet() }
    val krRequirements = remember(pack) { pack.requirements.filter { it.nationality == "KR" } }
    // 여행경보 3단계 이상 문장 — 여행 정보 맨 위 위험 배너로 끌어올린다(팩 문장 그대로, 재검토 R17)
    val safety = remember(pack) { pack.sections.firstOrNull { it.id == "safety" } }
    val advisories = remember(safety) { safety?.bodyKo.orEmpty().filter { isHighAdvisory(it) } }

    // 섹션 전환은 2열 폭일 때만 위에 고정한다 — 쉬운 모드·큰 글자에서는 세로 목록이라 고정하면 화면을 가린다 (6-03)
    val single = rememberGridColumns() == 1
    val sticky = !single
    var stickyHeight by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val keys = rememberKeyIndex()
    val scope = rememberCoroutineScope()
    val sectionsLabel = stringResource(R.string.country_sections, pack.names.ko)
    val picker: @Composable () -> Unit = {
        ChoiceSegments(
            options = CountrySection.entries,
            selected = CountrySection.entries[section],
            onSelect = { section = it.ordinal },
            label = { stringResource(it.label) },
            icon = { it.icon() },
            modifier = Modifier.semantics { contentDescription = sectionsLabel },
        )
    }
    val tileLabel: @Composable (Int) -> String = { id -> gridLabel(stringResource(id), single) }

    AppScreen(
        title = pack.names.ko,
        speech = stringResource(R.string.country_speech, pack.names.ko),
        header = { CountryHero(loaded, ui.favorite, actions) },
        state = listState,
        keyIndex = keys,
    ) {
        if (sticky) {
            stickyHeader(key = "sections") {
                // 바탕을 Ground로 칠해 아래 내용이 비쳐 보이지 않게
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Tokens.Ground)
                        .padding(vertical = 4.dp)
                        .onSizeChanged { stickyHeight = it.height },
                ) { picker() }
            }
        } else {
            item(key = "sections") { picker() }
        }

        when (CountrySection.entries[section]) {
            CountrySection.Entry -> {
                // 정부 비제휴·제출은 직접 — 입국 화면의 첫 정보 항목 (원칙 5)
                item(key = "not-affiliated") {
                    NoticeBanner(
                        stringResource(R.string.guide_not_affiliated),
                        icon = Icons.Outlined.Policy,
                        secondLine = stringResource(R.string.country_submit_self),
                        secondIcon = Icons.Outlined.TouchApp,
                    )
                }
                krRequirements.forEach { req ->
                    item(key = "req-${req.purpose}") { VisaCard(pack, req, sourceOf, actions.openLink) }
                }
                // 1단계: 비자 신청이 지나가는 입국 신고 양식 (없으면 단계 표시 없이 양식 카드만)
                pack.forms.filter { it.id in applyForms }.forEach { form ->
                    item(key = "form-${form.id}") {
                        FormCard(form, autofill = form.id in ui.autofillForms, step = 1, sourceOf = sourceOf) { actions.openForm(form.id) }
                    }
                }
                // 2단계: 비자 온라인 신청 (그 양식이 이 팩에 없으면 단계 표시 없이 주 버튼)
                krRequirements.forEach { req ->
                    req.apply?.let { apply ->
                        val staged = apply.form in applyForms
                        item(key = "visa-apply-${req.purpose}") {
                            VisaApplyCard(apply, step = if (staged) 2 else null, primary = !staged, sourceOf = sourceOf) {
                                actions.openForm(apply.form)
                            }
                        }
                    }
                }
                pack.forms.filter { it.id !in applyForms }.forEach { form ->
                    item(key = "form-${form.id}") {
                        FormCard(form, autofill = form.id in ui.autofillForms, step = null, sourceOf = sourceOf) { actions.openForm(form.id) }
                    }
                }
                pack.sections.filter { it.id == "entry" }.forEach { s ->
                    item(key = "section-${s.id}") { SectionCard(s, sourceOf) }
                }
                item(key = "plan") {
                    SecondaryButton(
                        stringResource(R.string.country_plan_trip),
                        onClick = { actions.planTrip(pack.country) },
                        icon = Icons.Outlined.EditCalendar,
                    )
                }
            }

            CountrySection.Travel -> {
                // 여행경보 3단계(출국권고) 이상 문장은 맨 위 위험 배너로 — 화폐 단위와 같은 점 불릿 사이에 묻히지 않게 (재검토 R17)
                if (safety != null && advisories.isNotEmpty()) {
                    item(key = "advisory") { AdvisoryBanner(safety, advisories, sourceOf) }
                }
                item(key = "tools-title") {
                    SectionHeader(stringResource(R.string.country_travel_tools_title), icon = Icons.Outlined.Explore)
                }
                item(key = "tools") {
                    // 현지어·긴급, 이동, 영상, 지도를 한눈에 — 예전 '현지에서 급할 때'·영상·이동 카드를 타일로 흡수 (6-05)
                    InfoTileGrid(
                        listOf(
                            TileSpec(
                                tileLabel(R.string.tile_phrases_emergency), Icons.Outlined.Translate,
                                onClick = { actions.openHelp(pack.country) }, tone = BadgeTone.Help,
                            ),
                            TileSpec(tileLabel(R.string.move_title), Icons.Outlined.LocalTaxi, onClick = actions.openMove, tone = BadgeTone.Violet),
                            TileSpec(tileLabel(R.string.tile_videos), Icons.Outlined.SmartDisplay, onClick = { actions.openVideos(pack.country) }),
                            TileSpec(
                                tileLabel(R.string.tile_maps), Icons.Outlined.Map,
                                onClick = { scope.launch { listState.scrollToKey(keys, "maps", if (sticky) stickyHeight else 0) } },
                                tone = BadgeTone.Teal,
                            ),
                        ),
                    )
                }
                sectionGap("gap-tools")
                pack.power?.let { power -> item(key = "power") { PowerCard(power, sourceOf) } }
                pack.sections.filter { it.id != "entry" }.forEach { s ->
                    // 위험 배너로 올린 문장은 안전 카드에서 되풀이하지 않는다(한 사실은 한 번). 남는 문장이 없으면 카드도 없다(출처는 배너 아래에)
                    val lifted = if (s.id == "safety") advisories else emptyList()
                    if (s.bodyKo.any { it !in lifted }) {
                        item(key = "section-${s.id}") { SectionCard(s, sourceOf, exclude = lifted) }
                    }
                }
                item(key = "maps") { MapsCard() }
            }

            CountrySection.Shopping -> {
                if (pack.shopping.isNotEmpty()) {
                    item(key = "shopping") { ShoppingCard(pack, sourceOf) { actions.openShopping(pack.country) } }
                }
                item(key = "return") {
                    // 나라 쇼핑은 한 줄 요약 + 펼치기 — 전체는 내 여행 귀국 단계에만 (운영자 결정 10)
                    ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, actions.openLink, ReturnCheckMode.Summary)
                }
            }
        }
    }
}

// ======================= 타일 라벨 =======================

/**
 * 2열 타일용 라벨(`현지어와\n긴급 번호`)을 1열(쉬운 모드·큰 글자, 폭 전체 칸)에서는 한 줄 문장으로 — 넓은 칸에 짧은 줄이 생기지 않게.
 * 한국어 줄바꿈(어절 단위)은 IconTile이 한다.
 */
private fun gridLabel(label: String, single: Boolean): String = if (single) label.replace('\n', ' ') else label

// ======================= 긴 글 접기 (원칙 6) =======================

/** 이 글자 수를 넘는 팩 글은 첫 문장만 보이고 나머지는 펼쳐서 본다 (원칙 6) */
private const val FOLD_CHARS = 60

/** 첫 문장과 나머지. 60자 이하이거나 첫 문장 경계(". ")를 못 찾으면 나머지는 null(→ 전체 표시) */
private fun foldSplit(text: String): Pair<String, String?> {
    val t = text.trim()
    if (t.length <= FOLD_CHARS) return t to null
    val i = t.indexOf(". ")
    if (i <= 0) return t to null
    val rest = t.substring(i + 2).trim()
    return if (rest.isEmpty()) t to null else t.substring(0, i + 1) to rest
}

/**
 * 접힌 글을 펼치는 줄 = 공용 [ExpandToggle]. 보이는 글은 짧게 `자세히 보기`/`접기` — 큰 글자에서 두 줄로 꺾이지 않게.
 * 한 화면에 여러 개라 TalkBack 이름은 무엇을 펼치는지 밝힌다: 접힘 [a11yLabel], 펼침 `{target} 접기`(재검토2 ②#2).
 * 글을 바꿔 보이는 방식(접힘 = 첫 문장, 펼침 = 전체)이라 바뀌는 글 묶음에 foldLiveRegion을 둔다(바뀐 글을 TalkBack이 읽는다).
 */
@Composable
private fun MoreToggle(open: Boolean, onOpenChange: (Boolean) -> Unit, a11yLabel: String, target: String) {
    ExpandToggle(
        open = open,
        onOpenChange = onOpenChange,
        label = stringResource(R.string.action_more),
        target = target,
        closedName = a11yLabel,
    )
}

// ======================= 머리글 =======================

/** 히어로 최소 높이 — 320×470 화면 예산(DESIGN_SPEC 6-03)에서 정부 비제휴 고지가 스크롤 없이 보이게 */
private val HeroMinHeight = 220.dp

/**
 * 나라 대표 경치 머리글: 뒤로 · 찜 · 나라 이름 · 확인 날짜·저장 표시 (글자는 모두 스크림 영역 안, 3.7).
 * 확인 날짜·저장 표시는 누를 수 없으므로 상자 없는 정보 칩(InfoChip onDark — 홈 신뢰 표시와 같은 모양, 재검토 R1)으로 한 줄에 흐르게 —
 * 흰 상자 두 줄보다 스크림이 낮아 사진이 더 보인다. 어두운 사진(해 질 녘 왓아룬 등)은 그릴 때만 밝힌다(재검토 R19, 파일은 그대로).
 */
@Composable
private fun CountryHero(loaded: Loaded<CountryPack>, favorite: Boolean, actions: CountryActions) {
    val pack = loaded.value
    val dimens = LocalDimens.current
    PhotoBox(Photos.country(pack.country), minHeight = 0.dp, shape = MaterialTheme.shapes.extraLarge) {
        // 버튼 줄과 나라 이름을 세로로 쌓는다 — 글자를 키워도 서로 겹치지 않는다
        Column(Modifier.fillMaxWidth().heightIn(min = HeroMinHeight), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                // 사진 위 버튼: 검정 0.35 원형 바탕(흰 사진에서도 3.54:1) + 48/56dp
                IconButton(
                    onClick = actions.back,
                    modifier = Modifier.minTouchSize().clip(CircleShape).background(Tokens.PhotoButtonBg),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.country_back),
                        tint = OnDark.content,
                        modifier = Modifier.size(dimens.icon),
                    )
                }
                val label = stringResource(if (favorite) R.string.explore_favorite_remove else R.string.explore_favorite_add, pack.names.ko)
                IconToggleButton(
                    checked = favorite,
                    onCheckedChange = { actions.toggleFavorite() },
                    modifier = Modifier
                        .minTouchSize()
                        .clip(CircleShape)
                        .background(Tokens.PhotoButtonBg)
                        .semantics { contentDescription = label },
                ) {
                    // Filled는 '켜짐' 상태에만 (D12)
                    Icon(
                        if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = OnDark.content,
                        modifier = Modifier.size(dimens.icon),
                    )
                }
            }
            PhotoTextColumn {
                KoText(
                    pack.names.ko,
                    style = MaterialTheme.typography.displayMedium,
                    color = OnDark.content,
                    modifier = Modifier.semantics { heading() },
                )
                KoText(
                    listOf(pack.names.en, pack.names.local).distinct().joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnDark.content,
                )
                FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val style = MaterialTheme.typography.labelMedium
                    InfoChip(
                        stringResource(R.string.guide_last_verified, displayDate(pack.lastVerified)),
                        Icons.Outlined.CalendarMonth,
                        onDark = true,
                        textStyle = style,
                    )
                    InfoChip(
                        stringResource(if (loaded.origin == PackOrigin.Bundled) R.string.guide_origin_bundled else R.string.guide_origin_downloaded),
                        Icons.Outlined.OfflinePin,
                        onDark = true,
                        textStyle = style,
                    )
                }
            }
        }
    }
}

// ======================= 03·04 입국·비자 =======================

/**
 * 비자 카드 (흰 카드뉴스 카드 — 재검토 R12: Accent 채움은 화면의 주 버튼에만). 결론 → 숫자 타일 → 팩 요약(첫 문장, 나머지는 펼쳐서)
 * → 공식 안내 줄 → 출처.
 * 타일은 비자 사실만: [머무는 날][비자 비용]. 입국 카드(양식) 비용 `무료`는 그 양식 카드로 옮겼다 — 인도네시아에서
 * `IDR 500,000 비자 비용` 옆에 초록 `무료`가 붙어 비자가 무료로 읽히던 문제. 값은 팩의 구조화 필드(stay_limit_days, fee_ko의 짧은 값)에서만(D11).
 * 기간(window_days_including_arrival) 타일은 이번 릴리스에서 그리지 않는다(D11) · '직접' 타일 없음.
 * 공식 안내는 버튼 대신 링크 줄(LinkRow) — 강한 행동이 한 화면에 여럿 겹치지 않게(재검토 ②-3).
 * 출처 = 요건 출처 + 각 타일 출처 (SourceList가 날짜별로 묶고 중복을 없앤다).
 */
@Composable
private fun VisaCard(pack: CountryPack, req: Requirement, sourceOf: SourceOf, onOpenLink: (String) -> Unit) {
    val reqRef = sourceOf(req.source, req.lastVerified)
    val single = rememberGridColumns() == 1
    val visaFreeLabel = gridLabel(stringResource(R.string.fact_label_visa_free), single)
    val visaArrivalLabel = gridLabel(stringResource(R.string.fact_label_visa_arrival), single)
    val visaFeeLabel = gridLabel(stringResource(R.string.fact_label_visa_fee), single)
    val days = req.stayLimitDays?.let { stringResource(R.string.fact_days, it) }
    val facts = buildList {
        if (days != null) {
            when (req.visa) {
                "not_required" -> add(Fact(Icons.Outlined.EventAvailable, days, visaFreeLabel, source = reqRef))
                "on_arrival" -> add(Fact(Icons.Outlined.Approval, days, visaArrivalLabel, source = reqRef))
            }
        }
        req.apply?.let { apply ->
            shortValue(apply.feeKo)?.let { v ->
                add(Fact(feeIcon(v), v, visaFeeLabel, feeTone(v), sourceOf(apply.source, apply.lastVerified)))
            }
        }
    }
    val headline = when (req.visa) {
        "not_required" -> stringResource(R.string.country_visa_headline_not_required)
        "on_arrival" -> stringResource(R.string.country_visa_headline_on_arrival)
        else -> stringResource(R.string.guide_requirements_title)
    }
    CardNewsCard(
        title = headline,
        icon = Icons.Outlined.Approval,
        eyebrow = stringResource(R.string.country_visa_title),
        style = NewsStyle.Surface,
        sources = listOf(reqRef) + facts.sourceRefs(),
    ) {
        FactTiles(facts)
        VisaSummary(req.summaryKo)
        if (req.visa != "not_required") {
            pack.source(req.source)?.let { src ->
                LinkRow(stringResource(R.string.country_visa_link), onClick = { onOpenLink(src.url) })
            }
        }
    }
}

/**
 * 팩 요약 원문 (굵게 하지 않음 — 결론은 위 제목·타일이 맡는다). 60자를 넘으면 첫 문장만 보이고(원칙 6)
 * 펼치면 전체 원문 한 덩어리. 타일 값(30일·IDR 500,000)을 되풀이하는 뒷문장이 글 벽이 되지 않게.
 */
@Composable
private fun VisaSummary(summary: String) {
    val (first, rest) = foldSplit(summary)
    var open by rememberSaveable(summary) { mutableStateOf(false) }
    KoText(
        if (open || rest == null) summary.trim() else first,
        style = MaterialTheme.typography.bodyLarge,
        color = Tokens.Ink,
        modifier = Modifier.foldLiveRegion(),
    )
    if (rest != null) MoreToggle(open, { open = it }, stringResource(R.string.country_more_visa), stringResource(R.string.fold_target_visa))
}

/**
 * 숫자 타일 묶음 — 순서는 넘겨받은 그대로. 공용 [FactGrid]가 1열이면 가로형, 2열 홀수면 마지막 타일을 맨 아래 폭 전체로 놓고,
 * 공용 [StatTile]이 값을 칸 폭에 맞춘 한 줄로 그린다(`IDR 500,000`이 `IDR`/`500,000` 두 줄로 쪼개지지 않게, 재검토 R12).
 * 타일이 하나면(무비자 나라의 `90일`) 폭 전체 가로형 한 장.
 */
@Composable
private fun FactTiles(facts: List<Fact>) {
    when {
        facts.isEmpty() -> Unit
        facts.size == 1 -> StatTile(facts.single(), wide = true)
        else -> FactGrid(facts)
    }
}

/**
 * 팩 단계 문장을 첫 문장(굵게)과 나머지(보조 글)로 나눈다 — 글자는 하나도 바꾸지 않고 모양만 나눈다.
 * 첫 문장 경계(". ")를 못 찾으면 전체가 한 줄 제목.
 */
private fun splitFirstSentence(text: String): Step {
    val t = text.trim()
    val i = t.indexOf(". ")
    if (i <= 0) return Step(t)
    val rest = t.substring(i + 2).trim()
    return if (rest.isEmpty()) Step(t) else Step(t.substring(0, i + 1), detail = rest)
}

/** 카드 eyebrow — [step]이 있으면 `1단계 · 온라인 입국 신고`처럼 순서를 앞에 붙인다(재검토 R12, 인도네시아) */
@Composable
private fun stepEyebrow(step: Int?, label: String): String =
    if (step == null) label else stringResource(R.string.country_step_eyebrow, step, label)

/**
 * 비자 온라인 신청(e-VOA) 카드 (6-04). 단계는 번호만 — 아이콘·'직접 해요' 태그를 단계 순서에 붙이지 않는다(D11).
 * 60자를 넘는 단계의 보조 글은 접어 두고 `단계 설명 자세히 보기`로 펼친다(원칙 6) — '직접 제출'은 위 배너와 아래 안내가 늘 보인다.
 * 대행 사이트 경고는 버튼 바로 위 Danger 배너(고지라 접지 않음).
 * [step] = 2: 이 신청이 지나가는 입국 신고 양식 카드가 1단계로 위에 있다(팩 신청 단계 1번이 그 양식 제출) — 그 카드가 주 버튼이고
 * 이 카드 버튼은 보조([primary] = false, 원칙 7 화면당 주 버튼 하나).
 */
@Composable
private fun VisaApplyCard(apply: VisaApply, step: Int?, primary: Boolean, sourceOf: SourceOf, onStart: () -> Unit) {
    CardNewsCard(
        title = apply.nameKo,
        icon = Icons.Outlined.Approval,
        tone = BadgeTone.Success,
        eyebrow = stepEyebrow(step, stringResource(R.string.country_visa_apply_label)),
        sources = listOf(sourceOf(apply.source, apply.lastVerified)),
    ) {
        IconBullet(stringResource(R.string.guide_form_fee, apply.feeKo), Icons.Outlined.Payments)
        KoText(
            stringResource(R.string.country_visa_apply_steps),
            style = MaterialTheme.typography.titleMedium,
            color = Tokens.Ink,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        FoldedSteps(apply.stepsKo)
        apply.warningKo?.let { warning ->
            NoticeBanner(warning, icon = Icons.Outlined.GppMaybe, tone = BannerTone.Danger)
        }
        IconBullet(stringResource(R.string.country_visa_apply_note), Icons.Outlined.TouchApp, tone = BadgeTone.Help)
        val label = stringResource(R.string.country_visa_apply_start)
        if (primary) {
            PrimaryButton(label, onClick = onStart, icon = Icons.Outlined.EditNote)
        } else {
            SecondaryButton(label, onClick = onStart, icon = Icons.Outlined.EditNote)
        }
    }
}

/** 단계 목록: 단계마다 첫 문장(굵게) + 나머지(보조 글). 60자를 넘는 단계의 보조 글은 펼쳤을 때만 */
@Composable
private fun FoldedSteps(steps: List<String>) {
    var open by rememberSaveable(steps) { mutableStateOf(false) }
    val split = steps.map { splitFirstSentence(it) to (it.trim().length > FOLD_CHARS) }
    val foldable = split.any { (step, long) -> long && step.detail != null }
    StepList(
        split.map { (step, long) ->
            Step(step.text, detail = step.detail?.takeIf { open || !long })
        },
        modifier = Modifier.foldLiveRegion(),
    )
    if (foldable) MoreToggle(open, { open = it }, stringResource(R.string.country_more_steps), stringResource(R.string.fold_target_steps))
}

/**
 * 입국 카드(온라인 입국 신고 양식) 카드 = 공용 [EntryFormCard](여행 준비 18과 같은 카드·같은 말, 재검토2 ④#1·②#5).
 * 무엇 → 앱이 해 주는 것(자동 입력이면 칸을 채워 줌, 아니면 값 복사) → 비용 → 내는 때 → `입국 카드 준비하기`(주 버튼) → 출처.
 * [step] = 1: 비자 온라인 신청이 이 양식을 거친다(인도네시아) — eyebrow `1단계 · 온라인 입국 카드`.
 */
@Composable
private fun FormCard(form: FormInfo, autofill: Boolean, step: Int?, sourceOf: SourceOf, onStart: () -> Unit) {
    EntryFormCard(
        name = form.nameKo,
        feeKo = form.feeKo,
        windowKo = form.windowKo,
        source = sourceOf(form.source, form.lastVerified),
        eyebrow = stepEyebrow(step, stringResource(R.string.entry_form_label)),
        onStart = onStart,
        body = stringResource(if (autofill) R.string.country_form_autofill_body else R.string.country_form_manual_body),
    )
}

/**
 * 팩 섹션 카드(들어갈 때·돈·안전): 아이콘 머리 + 문장 행 + 출처.
 * 문장 앞 기호는 문장 뜻을 앱이 추측해 고르지 않는다(D11): 모든 섹션이 뜻 없는 점 하나(DotBullet, 재검토 R8) —
 * `…입국이 거절될 수 있어요` 옆에 '좋음'으로 읽히는 체크나 대시를 두지 않는다.
 * 60자를 넘는 문장은 첫 문장만 보이고 `… 자세히 보기`로 펼친다(원칙 6). 안전(여행경보)은 경고 뒷부분이 숨으면 안 되므로 접지 않는다.
 * [exclude]: 맨 위 위험 배너(AdvisoryBanner)로 올린 문장 — 여기서는 되풀이하지 않는다.
 */
@Composable
private fun SectionCard(s: Section, sourceOf: SourceOf, exclude: List<String> = emptyList()) {
    val safety = s.id == "safety"
    val lines = s.bodyKo.filter { it !in exclude }
    val split = lines.map { if (safety) it.trim() to null else foldSplit(it) }
    var open by rememberSaveable(s.id, s.bodyKo) { mutableStateOf(false) }
    CardNewsCard(
        title = s.titleKo,
        icon = IconKeys.section(s.id),
        tone = if (safety) BadgeTone.Caution else BadgeTone.Accent,
        sources = listOf(sourceOf(s.source, s.lastVerified)),
    ) {
        Column(Modifier.foldLiveRegion(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            lines.zip(split).forEach { (full, parts) ->
                val (first, rest) = parts
                DotBullet(if (open || rest == null) full.trim() else first)
            }
        }
        if (split.any { it.second != null }) {
            MoreToggle(open, { open = it }, stringResource(R.string.country_more_section, s.titleKo), s.titleKo)
        }
    }
}

// ======================= 05 여행경보 위험 배너 (재검토 R17) =======================

/**
 * 외교부 여행경보 중 '가지 말라'는 단계(3단계 출국권고·4단계 여행금지·특별여행주의보)를 말하는 팩 문장인지 —
 * 문장 안의 단계 이름 그대로 찾는다(앱이 문장 뜻을 지어내지 않는다, D11). 1·2단계만 말하는 문장은 아니다.
 */
internal fun isHighAdvisory(sentence: String): Boolean = HighAdvisoryWords.any { it in sentence }

private val HighAdvisoryWords = listOf("3단계", "4단계", "출국권고", "여행금지", "특별여행주의보", "가지 마세요")

/**
 * 여행 정보 맨 위 위험 배너: 안전 섹션 제목(팩) + 3단계 이상 문장(팩 원문 그대로, 줄마다 한 문장) + 그 출처.
 * 누를 수 없는 Danger 띠(NoticeBanner) — 출처는 배너 바로 아래(정책 문장이 출처 없이 보이지 않게, 원칙 5).
 */
@Composable
private fun AdvisoryBanner(safety: Section, sentences: List<String>, sourceOf: SourceOf) {
    Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner)) {
        NoticeBanner(
            sentences.joinToString("\n") { it.trim() },
            icon = IconKeys.section(safety.id),
            tone = BannerTone.Danger,
            title = safety.titleKo,
        )
        Box(Modifier.padding(horizontal = 4.dp)) { SourceList(listOf(sourceOf(safety.source, safety.lastVerified))) }
    }
}

// ======================= 05 여행 정보 =======================

/**
 * 전기 카드: 결론(한국 플러그가 맞는지) 배너 → 숫자 타일(짧은 값만) → 나머지는 글 행 → 출처.
 * 값은 팩 원문 그대로. 짧게 줄일 수 없는 값(플러그 모양 설명 등)은 타일로 만들지 않는다.
 */
@Composable
private fun PowerCard(power: PowerInfo, sourceOf: SourceOf) {
    val values = listOf(
        PowerValue(Icons.Outlined.Power, power.plugKo, stringResource(R.string.power_label_plug)),
        PowerValue(Icons.Outlined.ElectricBolt, power.voltage, stringResource(R.string.power_label_voltage)),
        PowerValue(Icons.Outlined.GraphicEq, power.frequency, stringResource(R.string.power_label_frequency)),
    )
    val tiles = values.mapNotNull { v -> shortValue(v.value)?.takeIf { it == v.value.trim() }?.let { v to Fact(v.icon, it, v.label) } }
        .takeIf { it.size >= 2 }.orEmpty()
    val rows = values - tiles.map { it.first }.toSet()
    CardNewsCard(
        title = stringResource(R.string.guide_power_title),
        icon = Icons.Outlined.Power,
        sources = listOf(sourceOf(power.source, power.lastVerified)),
    ) {
        power.krPlugFits?.let { fits ->
            if (fits) {
                NoticeBanner(stringResource(R.string.guide_power_kr_fits), icon = Icons.Outlined.CheckCircle, tone = BannerTone.Success)
            } else {
                NoticeBanner(stringResource(R.string.guide_power_kr_adapter), icon = Icons.Outlined.Outlet, tone = BannerTone.Caution)
            }
        }
        rows.forEach { IconBullet(it.value, it.icon) }
        FactTiles(tiles.map { it.second })
    }
}

/** 전기 값 하나: 팩 원문 값 + 아이콘 + 타일 라벨 */
private data class PowerValue(val icon: ImageVector, val value: String, val label: String)

/** 지도 저장 카드 (Teal): 번호 단계 + 안내 + 출처 */
@Composable
private fun MapsCard() {
    CardNewsCard(
        title = stringResource(R.string.explore_maps_title),
        icon = Icons.Outlined.Map,
        tone = BadgeTone.Teal,
        sources = listOf(SourceRef(stringResource(R.string.explore_maps_source), "2026.09.28")),
    ) {
        StepList(
            listOf(R.string.explore_maps_step1, R.string.explore_maps_step2, R.string.explore_maps_step3)
                .map { Step(stringResource(it)) },
        )
        IconBullet(stringResource(R.string.explore_maps_note), Icons.Outlined.Info)
    }
}

// ======================= 06 쇼핑 =======================

/**
 * 쇼핑 미리보기 카드 (사진 없음 — 히어로가 이미 위에 있다). 품목 3개 = 분류 아이콘 + 이름 + 한국 반입 판정 배지.
 * 이유(why_ko)는 미리보기에 넣지 않는다(줄 수로 자르기 금지, 전체 글은 쇼핑 리스트에서).
 * 출처 = 품목 출처 + 반입 판정 출처 — 반입 판정이 출처 없이 보이지 않게.
 */
@Composable
private fun ShoppingCard(pack: CountryPack, sourceOf: SourceOf, onOpen: () -> Unit) {
    val shown = pack.shopping.take(3)
    CardNewsCard(
        title = stringResource(R.string.shopping_title, pack.names.ko),
        icon = Icons.Outlined.ShoppingBag,
        sources = shown.flatMap { listOf(sourceOf(it.source, it.lastVerified), sourceOf(it.importSource, it.lastVerified)) },
    ) {
        KoText(stringResource(R.string.shopping_subtitle_v2), style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        ShoppingPreviewList(shown)
        if (pack.shopping.size > 3) {
            KoText(
                stringResource(R.string.country_shopping_more, pack.shopping.size - 3),
                style = MaterialTheme.typography.bodySmall,
                color = Tokens.InkSecondary,
            )
        }
        // 버튼 앞에는 뜻 아이콘(쇼핑 리스트 = ShoppingBag) — 꺾쇠를 앞에 두지 않는다(재검토 R11)
        PrimaryButton(stringResource(R.string.shopping_open), onClick = onOpen, icon = Icons.Outlined.ShoppingBag)
    }
}

/** 미리보기 행의 배지·글 사이 간격 */
private val PreviewGap = 12.dp

/**
 * 품목 미리보기 행들. 반입 판정 배지를 이름 옆에 둘지 아래에 둘지는 **카드 단위**로 한 번에 정한다:
 * - 1열(쉬운 모드·큰 글자)이면 항상 이름 아래 — 넓은 글자에서 배지가 한 글자 이름(`차`)을 덮지 않게
 * - 2열 폭이면 모든 품목의 이름 한 줄 폭 + 배지 폭이 행에 다 들어갈 때만 옆에, 하나라도 안 들어가면 모두 아래 (배지가 지그재그로 놓이지 않게)
 * 폭은 같은 글자 스타일로 잰다(TextMeasurer). 옆에 둘 때도 이름은 weight 칸이라 어림이 조금 틀려도 겹치지 않고 줄만 바뀐다.
 */
@Composable
private fun ShoppingPreviewList(items: List<ShoppingItem>) {
    val single = rememberGridColumns() == 1
    val dimens = LocalDimens.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.titleMedium
    val tagStyle = MaterialTheme.typography.labelMedium
    val needed = items.map { item ->
        val name = measurer.measure(koDisplay(item.names.ko), nameStyle).size.width
        val tag = measurer.measure(koDisplay(stringResource(importLabel(item.import))), tagStyle).size.width
        // 태그 아이콘은 글자 크기를 따라 커진다(textIconSize — StatusTag와 같은 계산)
        name + with(density) { (PreviewGap + StatusTagChrome + textIconSize(dimens.iconSmall, tagStyle)).roundToPx() } + tag
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val textW = with(density) { (maxWidth - dimens.iconBadge - PreviewGap).roundToPx() }
        PreviewRows(items, beside = !single && needed.all { it <= textW })
    }
}

/** 공용 StatusTag(반입 판정 배지)의 글자 밖 폭: 양옆 안쪽 10dp씩 + 아이콘과 글자 사이 4dp (아이콘은 iconSmall) */
private val StatusTagChrome = 24.dp

@Composable
private fun PreviewName(item: ShoppingItem, modifier: Modifier = Modifier) {
    KoText(item.names.ko, style = MaterialTheme.typography.titleMedium, color = Tokens.Ink, modifier = modifier)
}

/** 품목 한 줄: 분류 배지 + 이름 + 반입 판정 — 한 번에 읽는다(mergeDescendants) */
@Composable
private fun PreviewRows(items: List<ShoppingItem>, beside: Boolean) {
    val dimens = LocalDimens.current
    // 아래 배치에서 첫 줄 이름이 배지 가운데에 오게 (글자가 배지보다 크면 0)
    val lineHeight = with(LocalDensity.current) { MaterialTheme.typography.titleMedium.lineHeight.toDp() }
    val nameTop = ((dimens.iconBadge - lineHeight) / 2).coerceAtLeast(0.dp)
    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = dimens.iconBadge + PreviewGap), thickness = 1.dp, color = Tokens.Line)
            Row(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .padding(vertical = 10.dp),
                verticalAlignment = if (beside) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(PreviewGap),
            ) {
                IconBadge(IconKeys.item(item.id, item.category), tone = BadgeTone.Neutral)
                if (beside) {
                    PreviewName(item, Modifier.weight(1f))
                    ImportVerdictBadge(item.import)
                } else {
                    Column(Modifier.weight(1f).padding(top = nameTop), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewName(item)
                        ImportVerdictBadge(item.import)
                    }
                }
            }
        }
    }
}
