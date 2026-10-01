package com.readyport.ui.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.PackRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.EntryFormCard
import com.readyport.ui.components.ComingSoonGroup
import com.readyport.ui.components.IconTile
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.TileLayout
import com.readyport.ui.components.TileSpec
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.sectionGap
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
        title = stringResource(R.string.prepare_title),
        subtitle = stringResource(R.string.prepare_subtitle),
        speech = stringResource(R.string.prepare_speech),
    ) {
        // 정부 비제휴 고지는 '입국 준비' 탭 맨 위에 둔다 (PRD 8.1) — 모양만 배너로, 문구 그대로(제출은 직접 포함)
        item(key = "disclaimer") {
            NoticeBanner(text = stringResource(R.string.prepare_disclaimer), icon = Icons.Outlined.Policy)
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
        item(key = "items") {
            val progress = if (essentials.total > 0) stringResource(R.string.essentials_progress, essentials.total, essentials.done) else null
            IconTile(
                TileSpec(
                    label = stringResource(R.string.prepare_items_title),
                    icon = Icons.Outlined.Checklist,
                    onClick = onOpenEssentials,
                    supporting = listOfNotNull(stringResource(R.string.prepare_items_body), progress).joinToString("\n"),
                ),
                layout = TileLayout.Horizontal,
            )
        }
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
 * eyebrow `태국 · 도착 전에 내요` + 제목(양식 이름) → 비용(짧은 값이면 정보 칩) → 내는 때(팩 문장 그대로) → `입국 카드 준비하기` → 출처.
 */
@Composable
private fun FormCard(f: FormEntry, primary: Boolean, onOpen: () -> Unit) {
    val fallback = stringResource(R.string.source_official_fallback)
    EntryFormCard(
        name = f.nameKo,
        feeKo = f.feeKo,
        windowKo = f.windowKo,
        source = SourceRef(f.sourceName.ifBlank { fallback }, displayDate(f.lastVerified)),
        eyebrow = stringResource(R.string.prepare_form_eyebrow, f.countryKo),
        onStart = onOpen,
        primary = primary,
    )
}
