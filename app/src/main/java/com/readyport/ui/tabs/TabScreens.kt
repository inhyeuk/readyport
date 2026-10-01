package com.readyport.ui.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.PackRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.Fact
import com.readyport.ui.components.FactChip
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconTile
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.TileLayout
import com.readyport.ui.components.TileSpec
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.feeIcon
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.shortValue
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.today.breakableDate
import com.readyport.ui.today.keepWords
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.readyport.prep.Essentials
import com.readyport.trip.TripRepository
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
)

/** 준비 탭의 '꼭 챙길 물건' 요약: 전체 n개 중 m개 */
data class EssentialsSummary(val total: Int = 0, val done: Int = 0)

@HiltViewModel
class PrepareViewModel @Inject constructor(
    packs: PackRepository,
    settings: SettingsRepository,
    trips: TripRepository,
) : ViewModel() {
    val essentials: StateFlow<EssentialsSummary> = combine(settings.settings, trips.trip, packs.revision) { s, trip, _ ->
        val index = packs.index()?.value
        val dest = trip?.let { packs.pack(it.country)?.value?.power }
        val rules = Essentials.select(index?.essentials.orEmpty(), index?.homePower, dest)
        EssentialsSummary(rules.size, rules.count { it.id in s.haveItems })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EssentialsSummary())

    /** 찜한 나라의 입국 서류. 찜이 없으면 받아 둔 모든 나라 (여행 만들기는 M6) */
    val forms: StateFlow<List<FormEntry>> = combine(settings.settings, packs.revision) { s, _ ->
        val countries = packs.index()?.value?.countries.orEmpty().filter { it.pack }
        val chosen = countries.filter { it.code in s.favorites }.ifEmpty { countries }
        chosen.mapNotNull { packs.pack(it.code)?.value }.flatMap { pack ->
            pack.forms.map { f ->
                // 출처 이름을 못 찾으면 ID 대신 빈 값 — 화면에서 `공식 안내`로 (ID 폴백 금지)
                FormEntry(f.id, f.nameKo, pack.names.ko, f.feeKo, f.windowKo, pack.source(f.source)?.name.orEmpty(), f.lastVerified)
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
 * 여행 준비 (DESIGN_SPEC 6-15): 정부 비제휴 고지(첫 항목, 제출은 직접 포함) → 입국 카드 카드뉴스(비용 칩·내는 때·출처) →
 * 꼭 챙길 물건 타일 → 곧 추가돼요(준비 중 기능 한 장).
 */
@Composable
fun PrepareContent(
    forms: List<FormEntry>,
    onOpenForm: (String) -> Unit,
    essentials: EssentialsSummary = EssentialsSummary(),
    onOpenEssentials: () -> Unit = {},
) {
    AppScreen(
        title = keepWords(stringResource(R.string.prepare_title)),
        subtitle = keepWords(stringResource(R.string.prepare_subtitle)),
        speech = stringResource(R.string.prepare_speech),
    ) {
        // 정부 비제휴 고지는 '입국 준비' 탭 맨 위에 둔다 (PRD 8.1) — 모양만 배너로, 문구 그대로(제출은 직접 포함)
        item(key = "disclaimer") {
            NoticeBanner(text = keepWords(stringResource(R.string.prepare_disclaimer)), icon = Icons.Outlined.Policy)
        }
        if (forms.isEmpty()) {
            item(key = "forms") {
                CardNewsCard(
                    title = keepWords(stringResource(R.string.prepare_forms_title)),
                    icon = Icons.Outlined.AssignmentInd,
                    tone = BadgeTone.Neutral,
                    body = keepWords(stringResource(R.string.prepare_forms_body)),
                )
            }
        }
        forms.forEachIndexed { i, f ->
            // 주 버튼은 화면에 하나 — 둘째 서류부터는 보조 버튼
            item(key = "form-${f.formId}") { FormCard(f, primary = i == 0, onOpen = { onOpenForm(f.formId) }) }
        }
        sectionGap("items-gap")
        item(key = "items") {
            val progress = if (essentials.total > 0) stringResource(R.string.essentials_progress, essentials.total, essentials.done) else null
            IconTile(
                TileSpec(
                    label = keepWords(stringResource(R.string.prepare_items_title)),
                    icon = Icons.Outlined.Checklist,
                    onClick = onOpenEssentials,
                    supporting = keepWords(listOfNotNull(stringResource(R.string.prepare_items_body), progress).joinToString("\n")),
                ),
                layout = TileLayout.Horizontal,
            )
        }
        item(key = "soon") {
            SoonGroup(
                listOf(
                    Icons.Outlined.InstallMobile to stringResource(R.string.prepare_apps_title),
                    Icons.Outlined.Description to stringResource(R.string.prepare_bookings_title),
                ),
            )
        }
    }
}

/**
 * 입국 카드 한 장: eyebrow `태국 · 도착 전에 내요` + 제목(양식 이름) → 비용 칩(팩 값이 짧을 때만, 아니면 글 행) →
 * 내는 때(팩 문장 그대로의 글 행 — 기간 칩·타일은 D11에 따라 이번 릴리스에서 만들지 않는다) → 버튼 → 출처.
 * 글자는 keepWords(API 33 미만 낱말 보호), 출처 날짜는 breakableDate(날짜가 한가운데서 끊기지 않게).
 */
@Composable
private fun FormCard(f: FormEntry, primary: Boolean, onOpen: () -> Unit) {
    val fallback = stringResource(R.string.source_official_fallback)
    CardNewsCard(
        title = keepWords(f.nameKo),
        icon = Icons.Outlined.AssignmentInd,
        eyebrow = keepWords(stringResource(R.string.prepare_form_eyebrow, f.countryKo)),
        sources = listOf(SourceRef(keepWords(f.sourceName.ifBlank { fallback }), breakableDate(displayDate(f.lastVerified)))),
    ) {
        val fee = shortValue(f.feeKo)
        if (fee != null) {
            val icon = feeIcon(fee)
            FactChip(
                Fact(
                    icon = icon,
                    value = keepWords(fee),
                    label = keepWords(stringResource(R.string.fact_label_form_fee)),
                    tone = if (icon == Icons.Outlined.MoneyOff) BadgeTone.Success else BadgeTone.Accent,
                ),
            )
        } else {
            IconBullet(keepWords(stringResource(R.string.guide_form_fee, f.feeKo)), Icons.Outlined.Payments)
        }
        IconBullet(keepWords(stringResource(R.string.guide_form_window, f.windowKo)), Icons.Outlined.Schedule)
        val label = keepWords(stringResource(R.string.prepare_form_open))
        if (primary) {
            PrimaryButton(label, onClick = onOpen, icon = Icons.Outlined.EditNote, modifier = Modifier.padding(top = 4.dp))
        } else {
            SecondaryButton(label, onClick = onOpen, icon = Icons.Outlined.EditNote, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * '곧 추가돼요' 한 장 (D15, 누를 수 없음) — ComingSoonGroup(0단계 부품)과 같은 모양·semantics에 머리 줄만 FlowRow.
 * 부품의 머리 줄은 Row(제목 weight + `준비 중이에요` 태그)라 쉬운 모드·200%에서 태그가 폭을 먼저 가져가 제목이 `곧 추/가돼/요`로
 * 쪼개진다. 여기서는 한 줄에 둘이 안 들어가면 태그가 제목 아래 줄로 내려간다.
 * (부품은 동결 — 2단계에서 ComingSoonGroup 머리 줄이 같은 규칙을 갖게 되면 부품으로 되돌린다)
 */
@Composable
private fun SoonGroup(items: List<Pair<ImageVector, String>>) {
    val dimens = LocalDimens.current
    Surface(color = Tokens.SurfaceSunken, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    keepWords(stringResource(R.string.coming_soon_group)),
                    style = MaterialTheme.typography.titleSmall,
                    color = Tokens.InkSecondary,
                    modifier = Modifier.padding(end = 8.dp).semantics { heading() },
                )
                StatusTag(keepWords(stringResource(R.string.coming_soon)), StatusKind.Soon)
            }
            items.forEach { (icon, text) ->
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) { disabled() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = Tokens.InkTertiary, modifier = Modifier.size(dimens.icon))
                    Text(keepWords(text), style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
