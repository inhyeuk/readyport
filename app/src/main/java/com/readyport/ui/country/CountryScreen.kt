package com.readyport.ui.country

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.readyport.ui.components.Fact
import com.readyport.ui.components.FactGrid
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.ImportVerdictBadge
import com.readyport.ui.components.InfoTileGrid
import com.readyport.ui.components.NewsStyle
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.PhotoBox
import com.readyport.ui.components.PhotoChip
import com.readyport.ui.components.PhotoTextColumn
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.ReturnCheckCard
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.StatTile
import com.readyport.ui.components.Step
import com.readyport.ui.components.StepList
import com.readyport.ui.components.TileSpec
import com.readyport.ui.components.TrailingFlow
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.feeIcon
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.rememberKeyIndex
import com.readyport.ui.components.resolveSourceName
import com.readyport.ui.components.scrollToKey
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.shortValue
import com.readyport.ui.components.sourceRefs
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

    // 섹션 전환은 2열 폭일 때만 위에 고정한다 — 쉬운 모드·큰 글자에서는 세로 목록이라 고정하면 화면을 가린다 (6-03)
    val sticky = rememberGridColumns() == 2
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
                pack.requirements.filter { it.nationality == "KR" }.forEach { req ->
                    item(key = "req-${req.purpose}") { VisaCard(pack, req, sourceOf, actions.openLink) }
                    req.apply?.let { apply ->
                        item(key = "visa-apply-${req.purpose}") { VisaApplyCard(apply, sourceOf) { actions.openForm(apply.form) } }
                    }
                }
                pack.forms.forEach { form ->
                    item(key = "form-${form.id}") {
                        FormCard(form, autofill = form.id in ui.autofillForms, sourceOf) { actions.openForm(form.id) }
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
                item(key = "tools-title") {
                    SectionHeader(stringResource(R.string.country_travel_tools_title), icon = Icons.Outlined.Explore)
                }
                item(key = "tools") {
                    // 현지어·긴급, 이동, 영상, 지도를 한눈에 — 예전 '현지에서 급할 때'·영상·이동 카드를 타일로 흡수 (6-05)
                    InfoTileGrid(
                        listOf(
                            TileSpec(
                                stringResource(R.string.tile_phrases_emergency), Icons.Outlined.Translate,
                                onClick = { actions.openHelp(pack.country) }, tone = BadgeTone.Help,
                            ),
                            TileSpec(stringResource(R.string.move_title), Icons.Outlined.LocalTaxi, onClick = actions.openMove, tone = BadgeTone.Violet),
                            TileSpec(stringResource(R.string.tile_videos), Icons.Outlined.SmartDisplay, onClick = { actions.openVideos(pack.country) }),
                            TileSpec(
                                stringResource(R.string.tile_maps), Icons.Outlined.Map,
                                onClick = { scope.launch { listState.scrollToKey(keys, "maps", if (sticky) stickyHeight else 0) } },
                                tone = BadgeTone.Teal,
                            ),
                        ),
                    )
                }
                sectionGap("gap-tools")
                pack.power?.let { power -> item(key = "power") { PowerCard(power, sourceOf) } }
                pack.sections.filter { it.id != "entry" }.forEach { s ->
                    item(key = "section-${s.id}") { SectionCard(s, sourceOf) }
                }
                item(key = "maps") { MapsCard() }
            }

            CountrySection.Shopping -> {
                if (pack.shopping.isNotEmpty()) {
                    item(key = "shopping") { ShoppingCard(pack, sourceOf) { actions.openShopping(pack.country) } }
                }
                item(key = "return") { ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, actions.openLink) }
            }
        }
    }
}

// ======================= 머리글 =======================

/** 히어로 최소 높이 — 320×470 화면 예산(DESIGN_SPEC 6-03)에서 정부 비제휴 고지가 스크롤 없이 보이게 */
private val HeroMinHeight = 220.dp

/** 나라 대표 경치 머리글: 뒤로 · 찜 · 나라 이름 · 확인 날짜·저장 칩 (글자는 모두 스크림 영역 안, 3.7) */
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
                Text(
                    pack.names.ko,
                    style = MaterialTheme.typography.displayMedium,
                    color = OnDark.content,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    listOf(pack.names.en, pack.names.local).distinct().joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnDark.content,
                )
                FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PhotoChip(stringResource(R.string.guide_last_verified, displayDate(pack.lastVerified)), Icons.Outlined.CalendarMonth)
                    PhotoChip(
                        stringResource(if (loaded.origin == PackOrigin.Bundled) R.string.guide_origin_bundled else R.string.guide_origin_downloaded),
                        Icons.Outlined.OfflinePin,
                    )
                }
            }
        }
    }
}

// ======================= 03·04 입국·비자 =======================

/** '무료'면 초록(가능), 그 밖의 비용은 기본 Accent — 색만으로 뜻을 전하지 않도록 아이콘(feeIcon)도 함께 바뀐다 */
private fun feeTone(value: String): BadgeTone = if (value == "무료") BadgeTone.Success else BadgeTone.Accent

/**
 * 비자 카드 (Accent 채움, onDark 내용 세트만). 결론 → 숫자 타일 → 팩 요약 원문 → 공식 안내 → 출처.
 * 타일 값은 팩의 구조화 필드(stay_limit_days, fee_ko의 짧은 값)에서만 만든다 (D11).
 * 기간(window_days_including_arrival) 타일은 이번 릴리스에서 그리지 않는다(D11) · '직접' 타일 없음.
 * 출처 = 요건 출처 + 각 타일 출처 (SourceList가 날짜별로 묶고 중복을 없앤다).
 */
@Composable
private fun VisaCard(pack: CountryPack, req: Requirement, sourceOf: SourceOf, onOpenLink: (String) -> Unit) {
    val reqRef = sourceOf(req.source, req.lastVerified)
    val visaFreeLabel = stringResource(R.string.fact_label_visa_free)
    val visaArrivalLabel = stringResource(R.string.fact_label_visa_arrival)
    val visaFeeLabel = stringResource(R.string.fact_label_visa_fee)
    val formFeeLabel = stringResource(R.string.fact_label_form_fee)
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
        pack.forms.filter { it.id in req.forms }.forEach { form ->
            shortValue(form.feeKo)?.let { v ->
                add(Fact(feeIcon(v), v, formFeeLabel, feeTone(v), sourceOf(form.source, form.lastVerified)))
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
        style = NewsStyle.Accent,
        sources = listOf(reqRef) + facts.sourceRefs(),
    ) {
        FactTiles(facts)
        // 팩 요약 원문 (굵게 하지 않음 — 결론은 위 제목·타일이 맡는다)
        Text(req.summaryKo, style = MaterialTheme.typography.bodyLarge, color = OnDark.content)
        if (req.visa != "not_required") {
            pack.source(req.source)?.let { src ->
                SecondaryButton(
                    stringResource(R.string.country_visa_link),
                    onClick = { onOpenLink(src.url) },
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    onDark = true,
                )
            }
        }
    }
}

/**
 * 숫자 타일 묶음. 2열에서 타일 수가 홀수면 남는 칸을 비우지 않고, 값이 가장 긴 타일을 맨 아래 폭 전체로 놓는다
 * (예: `IDR 500,000`이 반 칸에서 두 줄로 꺾이지 않게). 1열이면 순서 그대로 쌓는다. 2개 미만이면 그리지 않는다(글이 대신).
 */
@Composable
private fun FactTiles(facts: List<Fact>) {
    if (facts.size < 2) return
    val columns = rememberGridColumns()
    if (columns == 2 && facts.size % 2 == 1) {
        val wide = facts.maxBy { it.value.length }
        Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
            FactGrid(facts - wide, columns = columns)
            StatTile(wide)
        }
    } else {
        FactGrid(facts, columns = columns)
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

/**
 * 비자 온라인 신청(e-VOA) 카드 (6-04). 단계는 번호만 — 아이콘·'직접 해요' 태그를 단계 순서에 붙이지 않는다(D11).
 * 대행 사이트 경고는 버튼 바로 위 Danger 배너.
 */
@Composable
private fun VisaApplyCard(apply: VisaApply, sourceOf: SourceOf, onStart: () -> Unit) {
    CardNewsCard(
        title = apply.nameKo,
        icon = Icons.Outlined.Approval,
        tone = BadgeTone.Success,
        eyebrow = stringResource(R.string.country_visa_apply_label),
        sources = listOf(sourceOf(apply.source, apply.lastVerified)),
    ) {
        IconBullet(stringResource(R.string.guide_form_fee, apply.feeKo), feeIcon(shortValue(apply.feeKo) ?: apply.feeKo))
        Text(
            stringResource(R.string.country_visa_apply_steps),
            style = MaterialTheme.typography.titleMedium,
            color = Tokens.Ink,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        StepList(apply.stepsKo.map(::splitFirstSentence))
        apply.warningKo?.let { warning ->
            NoticeBanner(warning, icon = Icons.Outlined.GppMaybe, tone = BannerTone.Danger)
        }
        IconBullet(stringResource(R.string.country_visa_apply_note), Icons.Outlined.TouchApp, tone = BadgeTone.Help)
        PrimaryButton(stringResource(R.string.country_visa_apply_start), onClick = onStart, icon = Icons.Outlined.EditNote)
    }
}

/** 온라인 입국 신고 카드: 무엇 → 앱이 해 주는 것 → 비용·내는 때(팩 원문 그대로) → 입력 도와받기 → 출처 */
@Composable
private fun FormCard(form: FormInfo, autofill: Boolean, sourceOf: SourceOf, onStart: () -> Unit) {
    CardNewsCard(
        title = form.nameKo,
        icon = Icons.Outlined.AssignmentInd,
        eyebrow = stringResource(R.string.country_form_label),
        body = stringResource(if (autofill) R.string.country_form_autofill_body else R.string.country_form_manual_body),
        sources = listOf(sourceOf(form.source, form.lastVerified)),
    ) {
        IconBullet(stringResource(R.string.guide_form_fee, form.feeKo), feeIcon(shortValue(form.feeKo) ?: form.feeKo))
        IconBullet(stringResource(R.string.guide_form_window, form.windowKo), Icons.Outlined.Schedule)
        PrimaryButton(
            stringResource(if (autofill) R.string.country_form_start else R.string.country_form_manual_start),
            onClick = onStart,
            icon = Icons.Outlined.EditNote,
        )
    }
}

/**
 * 팩 섹션 카드(들어갈 때·돈·안전): 아이콘 머리 + 문장 행 + 출처.
 * 문장 앞 아이콘은 Neutral Check 공통 — 문장 뜻을 앱이 추측해 아이콘을 고르지 않는다(6-03 ④).
 */
@Composable
private fun SectionCard(s: Section, sourceOf: SourceOf) {
    CardNewsCard(
        title = s.titleKo,
        icon = IconKeys.section(s.id),
        tone = if (s.id == "safety") BadgeTone.Caution else BadgeTone.Accent,
        sources = listOf(sourceOf(s.source, s.lastVerified)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            s.bodyKo.forEach { IconBullet(it, Icons.Outlined.Check) }
        }
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
    val dimens = LocalDimens.current
    CardNewsCard(
        title = stringResource(R.string.shopping_title, pack.names.ko),
        icon = Icons.Outlined.ShoppingBag,
        tone = BadgeTone.Help,
        sources = shown.flatMap { listOf(sourceOf(it.source, it.lastVerified), sourceOf(it.importSource, it.lastVerified)) },
    ) {
        Text(stringResource(R.string.shopping_subtitle_v2), style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        Column {
            shown.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = dimens.iconBadge + 12.dp), thickness = 1.dp, color = Tokens.Line)
                ShoppingPreviewRow(item)
            }
        }
        if (pack.shopping.size > 3) {
            Text(
                stringResource(R.string.country_shopping_more, pack.shopping.size - 3),
                style = MaterialTheme.typography.bodySmall,
                color = Tokens.InkSecondary,
            )
        }
        PrimaryButton(stringResource(R.string.shopping_open), onClick = onOpen, icon = Icons.AutoMirrored.Outlined.NavigateNext)
    }
}

/** 품목 한 줄: 분류 배지 + 이름 + 반입 판정(폭이 모자라면 이름 아래 줄로) — 한 번에 읽는다 */
@Composable
private fun ShoppingPreviewRow(item: ShoppingItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(IconKeys.shoppingCategory(item.category), tone = BadgeTone.Neutral)
        TrailingFlow(
            trailing = { ImportVerdictBadge(item.import) },
            modifier = Modifier.weight(1f),
            centerVertically = true,
        ) {
            Text(item.names.ko, style = MaterialTheme.typography.titleMedium, color = Tokens.Ink)
        }
    }
}
