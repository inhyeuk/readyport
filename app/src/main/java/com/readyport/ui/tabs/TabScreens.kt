package com.readyport.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Outlet
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.CountryPack
import com.readyport.pack.EssentialRule
import com.readyport.pack.PackIndex
import com.readyport.pack.PackRepository
import com.readyport.pack.PowerInfo
import com.readyport.prep.Essentials
import com.readyport.trip.TripRepository
import com.readyport.ui.components.Assurance
import com.readyport.ui.components.AssuranceCard
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.ComingSoonGroup
import com.readyport.ui.components.EntryFormCard
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.InfoChip
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.keepWords
import com.readyport.ui.components.sectionGap
import com.readyport.ui.country.personalWindowKo
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** 준비 탭에 보여 줄 입국 서류 하나 */
data class FormEntry(
    val formId: String,
    val nameKo: String,
    val countryKo: String,
    val feeKo: String,
    val windowKo: String,
    /** 출처 이름. 못 찾으면 빈 문자열 → 화면이 `공식 안내`로 보인다(내부 ID를 넣지 않는다, DESIGN_SPEC 4.5) */
    val sourceName: String,
    val lastVerified: String,
    /** 도착일을 포함해 며칠 전부터 낼 수 있는지 (팩 forms[].window_days_including_arrival) */
    val windowDays: Int? = null,
    /** 이 나라로 가는 내 여행의 출발일(=도착일로 본다 — 오늘 단계·알림과 같은 계산). 있으면 일반 예시 대신 내 여행 기간을 보인다 */
    val tripArrival: LocalDate? = null,
)

/**
 * 준비 탭·홈의 '꼭 챙길 물건' 요약: 전체 n개 중 m개 + 값이 있는 정보 칩의 재료(재검토2 ①#15·②#9·③#10).
 * [power]: 여행 나라의 전기(팩 power — 여행이 있을 때만), [carryOnOnly]: 기내 반입만 되는 물건(팩 essentials rule_badge = carry_on_only),
 * [sources]: 칩 값의 출처(이름을 못 찾으면 빈 이름 → 화면이 `공식 안내`).
 */
data class EssentialsSummary(
    val total: Int = 0,
    val done: Int = 0,
    val power: PowerInfo? = null,
    val carryOnOnly: List<EssentialRule> = emptyList(),
    val sources: List<SourceRef> = emptyList(),
)

/** 팩 값만으로 꼭 챙길 물건 요약을 만든다 — 홈·여행 준비가 같은 계산(여행 나라 [tripPack]이 없으면 전기 칩 없음) */
internal fun essentialsSummary(index: PackIndex?, tripPack: CountryPack?, have: Set<String>): EssentialsSummary {
    val rules = Essentials.select(index?.essentials.orEmpty(), index?.homePower, tripPack?.power)
    val carry = rules.filter { it.ruleBadge == CARRY_ON_ONLY }
    val names = index?.sources.orEmpty().associate { it.id to it.name }
    val power = tripPack?.power
    val sources = buildList {
        if (power != null) add(SourceRef(tripPack.source(power.source)?.name.orEmpty(), displayDate(power.lastVerified)))
        carry.forEach { r -> r.lastVerified?.let { d -> add(SourceRef(r.source?.let { names[it] }.orEmpty(), displayDate(d))) } }
    }
    return EssentialsSummary(rules.size, rules.count { it.id in have }, power, carry, sources)
}

private const val CARRY_ON_ONLY = "carry_on_only"

@HiltViewModel
class PrepareViewModel @Inject constructor(
    packs: PackRepository,
    settings: SettingsRepository,
    trips: TripRepository,
) : ViewModel() {
    val essentials: StateFlow<EssentialsSummary> = combine(settings.settings, trips.trip, packs.revision) { s, trip, _ ->
        essentialsSummary(packs.index()?.value, trip?.let { packs.pack(it.country)?.value }, s.haveItems)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EssentialsSummary())

    /** 찜한 나라의 입국 서류(여행 나라가 맨 앞). 찜이 없으면 받아 둔 모든 나라 */
    val forms: StateFlow<List<FormEntry>> = combine(settings.settings, packs.revision, trips.trip) { s, _, trip ->
        val countries = packs.index()?.value?.countries.orEmpty().filter { it.pack }
        val chosen = countries.filter { it.code in s.favorites }.ifEmpty { countries }
        // 끝나지 않은 여행만 내 날짜를 쓴다(지난 여행의 기간은 쓸모없다)
        val upcoming = trip?.takeIf { runCatching { !LocalDate.now().isAfter(it.start) }.getOrDefault(false) }
        chosen.sortedByDescending { it.code == trip?.country }.mapNotNull { packs.pack(it.code)?.value }.flatMap { pack ->
            pack.forms.map { f ->
                // 출처 이름을 못 찾으면 ID 대신 빈 값 — 화면에서 `공식 안내`로 (ID 폴백 금지)
                FormEntry(
                    f.id, f.nameKo, pack.names.ko, f.feeKo, f.windowKo, pack.source(f.source)?.name.orEmpty(), f.lastVerified,
                    windowDays = f.windowDaysIncludingArrival,
                    tripArrival = upcoming?.takeIf { it.country == pack.country }?.start,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun PrepareScreen(onOpenForm: (String) -> Unit, onOpenEssentials: () -> Unit = {}, viewModel: PrepareViewModel = hiltViewModel()) {
    val forms by viewModel.forms.collectAsStateWithLifecycle()
    val essentials by viewModel.essentials.collectAsStateWithLifecycle()
    PrepareContent(forms, onOpenForm, essentials, onOpenEssentials)
}

/**
 * 여행 준비 (DESIGN_SPEC 6-15): 안심 카드(비제휴 · 제출은 직접 — 나라 입국 화면과 같은 부품·같은 문구, 재검토2 ①#3·⑤#8) →
 * 입국 카드 카드뉴스(비용 칩·내는 때 — 내 여행이 있으면 내 날짜로·출처) → 꼭 챙길 물건 카드(값 칩 · `n / 5` 진행 — 홈과 같은 모양) →
 * 곧 추가돼요(준비 중 기능 한 장).
 */
@Composable
fun PrepareContent(
    forms: List<FormEntry>,
    onOpenForm: (String) -> Unit,
    essentials: EssentialsSummary = EssentialsSummary(),
    onOpenEssentials: () -> Unit = {},
) {
    AppScreen(
        title = stringResource(R.string.prepare_title),
        subtitle = stringResource(R.string.prepare_subtitle),
        speech = stringResource(R.string.prepare_speech),
    ) {
        // 정부 비제휴 · 제출은 직접 — '입국 준비' 탭 맨 위 (PRD 8.1). 나라 입국 화면(03·04)과 같은 안심 카드·같은 문구
        item(key = "disclaimer") {
            AssuranceCard(items = listOf(Assurance.NotAffiliated, Assurance.SubmitSelf))
        }
        if (forms.isEmpty()) {
            item(key = "forms") {
                CardNewsCard(
                    title = stringResource(R.string.prepare_forms_title),
                    icon = Icons.Outlined.AssignmentInd,
                    tone = BadgeTone.Neutral,
                    body = stringResource(R.string.prepare_forms_body),
                )
            }
        }
        forms.forEachIndexed { i, f ->
            // 주 버튼은 화면에 하나 — 둘째 서류부터는 보조 버튼
            item(key = "form-${f.formId}") { FormCard(f, primary = i == 0, onOpen = { onOpenForm(f.formId) }) }
        }
        sectionGap("items-gap")
        item(key = "items") { EssentialsPrepCard(essentials, onOpenEssentials) }
        item(key = "soon") {
            ComingSoonGroup(
                listOf(
                    Icons.Outlined.InstallMobile to stringResource(R.string.prepare_apps_title),
                    Icons.Outlined.Description to stringResource(R.string.prepare_bookings_title),
                ),
            )
        }
    }
}

/**
 * 입국 카드 한 장 = 공용 [EntryFormCard](나라 입국·비자 03·04와 같은 카드·같은 말 — 재검토2 ④#1·②#5):
 * eyebrow `태국 · 도착 전에 내요` + 제목(양식 이름) → 비용(짧은 값이면 정보 칩) → 내는 때 → `입국 카드 준비하기` → 출처.
 * 내는 때: 내 여행이 이 나라면 일반 예시(`예: 5월 4일 도착이면…`) 대신 팩 기간 일수 + 내 출발일로 계산한 날짜(재검토2 ③#5).
 */
@Composable
private fun FormCard(f: FormEntry, primary: Boolean, onOpen: () -> Unit) {
    val fallback = stringResource(R.string.source_official_fallback)
    EntryFormCard(
        name = f.nameKo,
        feeKo = f.feeKo,
        windowKo = personalWindowKo(f.windowKo, f.windowDays, f.tripArrival),
        source = SourceRef(f.sourceName.ifBlank { fallback }, displayDate(f.lastVerified)),
        eyebrow = stringResource(R.string.prepare_form_eyebrow, f.countryKo),
        onStart = onOpen,
        primary = primary,
    )
}

/**
 * 여행 준비의 꼭 챙길 물건 카드: 홈 카드와 같은 내용·같은 모양(값 칩 → `n / 5` 진행 줄 + 막대 → 준비물 확인 버튼 → 칩 값 출처) —
 * 사진 머리는 홈에만(같은 짐 사진을 되풀이하지 않게). 버튼은 보조(이 화면의 주 버튼은 첫 입국 카드).
 */
@Composable
private fun EssentialsPrepCard(summary: EssentialsSummary, onOpen: () -> Unit) {
    CardNewsCard(
        title = stringResource(R.string.prepare_items_title),
        icon = IconKeys.essentials,
        body = stringResource(R.string.prepare_items_body),
        sources = essentialsSources(summary),
    ) {
        EssentialsChips(summary)
        EssentialsProgress(summary)
        SecondaryButton(stringResource(R.string.home_essentials_open), onClick = onOpen, icon = IconKeys.essentials)
    }
}

/** 칩 값의 출처 (이름을 못 찾으면 `공식 안내`) */
@Composable
internal fun essentialsSources(summary: EssentialsSummary): List<SourceRef> {
    val fallback = stringResource(R.string.source_official_fallback)
    return summary.sources.map { if (it.name.isBlank()) it.copy(name = fallback) else it }
}

/**
 * 꼭 챙길 물건 정보 칩 — **값이 있는 것만**(재검토2 ①#15·②#9·③#10): 주제 이름만 늘어놓던 `플러그 · 전압 · 보조배터리` 대신
 * 여행 나라 팩 전기(`한국 플러그 그대로 써요` 또는 `변환 어댑터 챙기세요`, `220 V 전압`) + 기내 반입만 되는 물건(`보조배터리 기내 반입만 가능`).
 * 값이 하나도 없으면 칩 줄을 그리지 않는다. 누를 수 없는 InfoChip(값 굵게 + 말).
 */
@Composable
internal fun EssentialsChips(summary: EssentialsSummary) {
    val power = summary.power
    if (power == null && summary.carryOnOnly.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (power != null) {
            // 팩 kr_plug_fits가 true일 때만 '그대로' — 확인되지 않은 나라는 어댑터를 권한다(Essentials.plugDiffers와 같은 규칙)
            if (power.krPlugFits == true) {
                InfoChip(stringResource(R.string.items_chip_plug_fits), Icons.Outlined.Power, value = stringResource(R.string.items_chip_plug_value), tone = BadgeTone.Accent)
            } else {
                InfoChip(stringResource(R.string.items_chip_adapter), Icons.Outlined.Outlet, value = stringResource(R.string.items_chip_adapter_value), tone = BadgeTone.Accent)
            }
            InfoChip(stringResource(R.string.home_items_voltage), Icons.Outlined.ElectricBolt, value = power.voltage, tone = BadgeTone.Accent)
        }
        summary.carryOnOnly.forEach { r ->
            InfoChip(stringResource(R.string.essentials_badge_carry_on), IconKeys.essential(r.id), value = r.nameKo, tone = BadgeTone.Accent)
        }
    }
}

/**
 * 진행 줄: 큰 숫자 `n / 5` + 설명을 글자 기준선에 맞춰 한 줄로(큰 글자 배치에서는 큰 숫자를 설명 위로) + 폭 전체 막대.
 * 홈(01·02)과 여행 준비(18)가 같은 모양(재검토2 ③#10). 진행 데이터가 없으면 그리지 않는다.
 */
@Composable
internal fun EssentialsProgress(summary: EssentialsSummary) {
    if (summary.total <= 0) return
    val dimens = LocalDimens.current
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
        val stat = stringResource(R.string.essentials_progress_stat, summary.done, summary.total)
        val label = keepWords(stringResource(R.string.essentials_progress, summary.total, summary.done))
        val statStyle = LocalTypeExtras.current.statSmall
        val labelStyle = MaterialTheme.typography.bodyMedium
        if (isStackedLayout()) {
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
            modifier = Modifier.fillMaxWidth().height(dimens.inner),
            color = Tokens.Accent,
            trackColor = Tokens.SurfaceSunken,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}
