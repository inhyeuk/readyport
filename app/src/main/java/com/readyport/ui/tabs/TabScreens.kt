package com.readyport.ui.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.PackRepository
import com.readyport.trip.ChecklistProvider
import com.readyport.trip.TripRepository
import com.readyport.trip.TripSelection
import com.readyport.ui.home.essentialsHave
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.Assurance
import com.readyport.ui.components.AssuranceCard
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.EntryFormCard
import com.readyport.ui.components.EssentialsChips
import com.readyport.ui.components.EssentialsProgress
import com.readyport.ui.components.EssentialsSummary
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.essentialsSources
import com.readyport.ui.components.essentialsSummary
import com.readyport.ui.components.personalWindowKo
import com.readyport.ui.components.sectionGap
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
    /** 의무가 아닌(권장) 신고인지 (팩 forms[].optional — 베트남 PAI). 주 버튼이 되지 않고 `꼭 내야 하는 건 아니에요`로 보인다 */
    val optional: Boolean = false,
)

// '꼭 챙길 물건' 요약(EssentialsSummary·essentialsSummary·칩·진행 줄)은 홈과 함께 쓰는 공용 부품(components/Essentials.kt)

@HiltViewModel
class PrepareViewModel @Inject constructor(
    packs: PackRepository,
    settings: SettingsRepository,
    trips: TripRepository,
    checklists: ChecklistProvider,
) : ViewModel() {
    val essentials: StateFlow<EssentialsSummary> = combine(settings.settings, trips.book, packs.revision) { s, book, _ ->
        val trip = TripSelection.active(book.trips, LocalDate.now())
        // 꼭 챙길 물건 체크 = 지금 여행 체크리스트의 같은 항목(여행이 없으면 설정의 체크)
        val have = if (trip != null) essentialsHave(checklists.build(trip, book)) else s.haveItems
        essentialsSummary(packs.index()?.value, trip?.let { packs.pack(it.country)?.value }, have)
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
                    optional = f.optional,
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
 * 입국 카드 카드뉴스(비용 칩·내는 때 — 내 여행이 있으면 내 날짜로·출처) → 꼭 챙길 물건 카드(값 칩 · `n / 5` 진행 — 홈과 같은 모양).
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
            // 주 버튼은 화면에 하나 — 둘째 서류부터는 보조 버튼. 의무가 아닌 신고는 첫 장이어도 주 버튼이 되지 않는다
            item(key = "form-${f.formId}") { FormCard(f, primary = i == 0 && !f.optional, onOpen = { onOpenForm(f.formId) }) }
        }
        sectionGap("items-gap")
        item(key = "items") { EssentialsPrepCard(essentials, onOpenEssentials) }
        // '곧 추가돼요' 묶음은 두지 않는다(다듬기 S — 재검토2 ⑤#11, S3가 내 정보 맨 아래 한 곳으로 모은 것과 같은 정리):
        // `예약 서류`는 이미 내 정보에 있는 기능이고, `미리 설치할 앱`은 이동하기 화면의 차량 호출 앱(`Play 스토어에서 받기`)이 맡는다 —
        // 있는 기능을 '곧 추가'라고 하지 않는다.
    }
}

/**
 * 입국 카드 한 장 = 공용 [EntryFormCard](나라 입국·비자 03·04와 같은 카드·같은 말 — 재검토2 ④#1·②#5):
 * eyebrow `태국 · 도착 전에 내요` + 제목(양식 이름) → 비용(짧은 값이면 정보 칩) → 내는 때 → `입국 카드 준비하기` → 출처.
 * 내는 때: 내 여행이 이 나라면 일반 예시(`예: 5월 4일 도착이면…`) 대신 팩 기간 일수 + 내 출발일로 계산한 날짜(재검토2 ③#5).
 * 의무가 아닌 신고(베트남 PAI)는 eyebrow가 `베트남 · 내면 좋아요 (의무 아님)`이고 알약·버튼도 바뀐다.
 */
@Composable
private fun FormCard(f: FormEntry, primary: Boolean, onOpen: () -> Unit) {
    val fallback = stringResource(R.string.source_official_fallback)
    EntryFormCard(
        name = f.nameKo,
        feeKo = f.feeKo,
        windowKo = personalWindowKo(f.windowKo, f.windowDays, f.tripArrival),
        source = SourceRef(f.sourceName.ifBlank { fallback }, displayDate(f.lastVerified)),
        eyebrow = stringResource(
            if (f.optional) R.string.prepare_form_eyebrow_optional else R.string.prepare_form_eyebrow,
            f.countryKo,
        ),
        onStart = onOpen,
        primary = primary,
        optional = f.optional,
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
