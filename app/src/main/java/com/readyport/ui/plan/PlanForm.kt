package com.readyport.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Accessible
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.R
import com.readyport.board.BoardAge
import com.readyport.board.BoardRepository
import com.readyport.plan.BudgetBand
import com.readyport.plan.PlanCheck
import com.readyport.plan.PlanDates
import com.readyport.plan.PlanDraft
import com.readyport.plan.PlanError
import com.readyport.plan.PlanMobility
import com.readyport.plan.PlanProblem
import com.readyport.plan.PlanPurpose
import com.readyport.plan.PlanRepository
import com.readyport.plan.PlanRules
import com.readyport.plan.PlanTravelers
import com.readyport.trip.TripRepository
import com.readyport.ui.board.BoardField
import com.readyport.ui.board.CountryPhoto
import com.readyport.ui.board.ErrorLine
import com.readyport.ui.board.FormBlock
import com.readyport.ui.board.PiiWarningCard
import com.readyport.ui.board.boardCountryName
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LinkRow
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.SelectableCard
import com.readyport.ui.components.minTouch
import com.readyport.ui.nav.PlanRequestRoute
import com.readyport.ui.notice.rememberOpenLink
import com.readyport.ui.settings.PRIVACY_URL
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.wallet.ConsentRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ======================= 여행 계획 요청 양식 (비공개) =======================

/** 여행에서 왔을 때의 날짜 (나라·날짜를 미리 채운다) */
@Immutable
data class PlanTripDates(val start: String, val end: String, val days: Int?)

@Immutable
data class PlanFormUi(
    val loading: Boolean = false,
    val age: BoardAge.Status = BoardAge.Status.Allowed,
    val draft: PlanDraft = PlanDraft(),
    /** 여행에서 왔으면 그 여행 날짜 (30일을 넘으면 days = null — 날짜 그대로 쓰기를 고를 수 없다) */
    val trip: PlanTripDates? = null,
    /** 며칠인지만 보낼 때의 일수 (날짜 그대로를 고르면 숨는다) */
    val daysCount: Int = 3,
    val gendersOn: Boolean = false,
    val allowWarnings: Boolean = false,
    /** 남은 횟수 (모르면 null — 줄을 그리지 않는다) */
    val remaining: PlanRules.Remaining? = null,
    /** 보내기를 한 번 눌렀으면 빠진 칸을 빨갛게 */
    val showProblems: Boolean = false,
    val confirming: Boolean = false,
    val submitting: Boolean = false,
    /** 실패 문구 (문자열 id) */
    val error: Int? = null,
) {
    val check: PlanCheck get() = PlanRules.check(draft)
    val quotaUsed: Boolean get() = remaining?.count == 0
    val usingTripDates: Boolean get() = draft.dates is PlanDates.Range
    val canSend: Boolean get() = !submitting && !quotaUsed && age !is BoardAge.Status.Minor
}

data class PlanFormActions(
    val setCountry: (String) -> Unit = {},
    val useTripDates: (Boolean) -> Unit = {},
    val setDays: (Int) -> Unit = {},
    val togglePurpose: (PlanPurpose) -> Unit = {},
    val setNote: (String) -> Unit = {},
    val setTravelers: (PlanTravelers) -> Unit = {},
    val setGendersOn: (Boolean) -> Unit = {},
    val toggleMobility: (PlanMobility) -> Unit = {},
    val setConsent: (Boolean) -> Unit = {},
    val setBudget: (BudgetBand) -> Unit = {},
    val allowWarnings: () -> Unit = {},
    /** 보내기 → (빠진 칸이 없으면) AI 고지 확인 */
    val submit: () -> Unit = {},
    val confirmSend: () -> Unit = {},
    val dismissConfirm: () -> Unit = {},
    val checkAge: () -> Unit = {},
    val openLink: (String) -> Unit = {},
)

@HiltViewModel
class PlanFormViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val plans: PlanRepository,
    private val board: BoardRepository,
    private val wallet: com.readyport.vault.WalletRepository,
    trips: TripRepository,
) : ViewModel() {
    private val route = handle.toRoute<PlanRequestRoute>()
    private val _ui = MutableStateFlow(PlanFormUi(loading = true, draft = PlanDraft(country = route.country?.takeIf { it in PlanRules.COUNTRIES })))
    val ui: StateFlow<PlanFormUi> = _ui.asStateFlow()

    /** 보낸 요청 id — 화면이 내 계획 요청으로 간다 */
    private val _done = MutableStateFlow<String?>(null)
    val done: StateFlow<String?> = _done.asStateFlow()

    init {
        viewModelScope.launch {
            val trip = route.tripId?.let { id -> runCatching { trips.trips.first().firstOrNull { it.id == id } }.getOrNull() }
            val dates = trip?.takeIf { it.datesValid }?.let { t ->
                PlanTripDates(t.startDate, t.endDate, PlanRules.days(PlanDates.Range(t.startDate, t.endDate)))
            }
            _ui.update { s ->
                val country = trip?.country?.takeIf { it in PlanRules.COUNTRIES } ?: s.draft.country
                val days = dates?.days ?: s.daysCount
                s.copy(
                    loading = false,
                    trip = dates,
                    daysCount = days.coerceIn(1, PlanRules.MAX_DAYS),
                    draft = s.draft.copy(
                        country = country,
                        dates = if (dates?.days != null) PlanDates.Range(dates.start, dates.end) else PlanDates.Days(days.coerceIn(1, PlanRules.MAX_DAYS)),
                    ),
                )
            }
            refresh()
        }
    }

    /** 나이·남은 횟수 다시 보기 (화면으로 돌아올 때) */
    fun refresh() {
        viewModelScope.launch {
            val age = runCatching { plans.ageStatus() }.getOrDefault(_ui.value.age)
            val left = runCatching { plans.remaining() }.getOrNull()
            _ui.update { it.copy(age = age, remaining = left ?: it.remaining) }
        }
    }

    private fun edit(block: (PlanDraft) -> PlanDraft) = _ui.update { it.copy(draft = block(it.draft), error = null) }

    fun setCountry(c: String) = edit { it.copy(country = c) }

    fun useTripDates(on: Boolean) {
        val t = _ui.value.trip
        edit { d -> if (on && t?.days != null) d.copy(dates = PlanDates.Range(t.start, t.end)) else d.copy(dates = PlanDates.Days(_ui.value.daysCount)) }
    }

    fun setDays(n: Int) {
        val v = n.coerceIn(1, PlanRules.MAX_DAYS)
        _ui.update { it.copy(daysCount = v, draft = it.draft.copy(dates = PlanDates.Days(v)), error = null) }
    }

    fun togglePurpose(p: PlanPurpose) = edit { d ->
        when {
            p in d.purposes -> d.copy(purposes = d.purposes - p)
            d.purposes.size >= PlanRules.MAX_PURPOSES -> d
            else -> d.copy(purposes = d.purposes + p)
        }
    }

    fun setNote(text: String) {
        _ui.update { it.copy(draft = it.draft.copy(note = text), allowWarnings = false, error = null) }
    }

    fun setTravelers(t: PlanTravelers) = edit { it.copy(travelers = t) }

    fun setGendersOn(on: Boolean) {
        _ui.update { s ->
            val t = s.draft.travelers
            s.copy(gendersOn = on, draft = s.draft.copy(travelers = if (on) t.copy(female = t.female ?: 0, male = t.male ?: 0) else t.copy(female = null, male = null)))
        }
    }

    /** 이동 조건을 모두 풀면 동의도 지운다(동의 칸 자체를 보내지 않는다 — 규칙) */
    fun toggleMobility(m: PlanMobility) = edit { d ->
        val next = if (m in d.mobility) d.mobility - m else d.mobility + m
        d.copy(mobility = next, sensitiveConsent = d.sensitiveConsent && next.isNotEmpty())
    }

    fun setConsent(on: Boolean) = edit { it.copy(sensitiveConsent = on && it.mobility.isNotEmpty()) }

    fun setBudget(b: BudgetBand) = edit { it.copy(budget = b) }

    fun allowWarnings() = _ui.update { it.copy(allowWarnings = true) }

    fun submit() {
        val s = _ui.value
        if (!s.canSend) return
        if (!s.check.ready(s.allowWarnings)) {
            _ui.update { it.copy(showProblems = true, error = R.string.plan_err_invalid) }
            return
        }
        _ui.update { it.copy(confirming = true, showProblems = true) }
    }

    fun dismissConfirm() = _ui.update { it.copy(confirming = false) }

    fun confirmSend() {
        val s = _ui.value
        _ui.update { it.copy(confirming = false, submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val id = plans.submit(s.draft, s.allowWarnings)
                _ui.update { it.copy(submitting = false) }
                _done.value = id
            } catch (e: PlanError.QuotaUsed) {
                _ui.update { it.copy(submitting = false, remaining = PlanRules.Remaining(0, e.nextAt), error = null) }
            } catch (e: Exception) {
                _ui.update { it.copy(submitting = false, error = e.planErrorRes()) }
                refresh()
            }
        }
    }

    /** 기기 인증을 마친 뒤: 보관함을 열면 여권 생년월일로 판정해 둔다(게시판과 같은 값) */
    fun checkAge() {
        viewModelScope.launch {
            val s = wallet.unlock()
            if (s is com.readyport.vault.WalletState.Unlocked) board.recordAge(s.contents.passport?.birthDate)
            refresh()
        }
    }
}

@Composable
fun PlanFormScreen(onSent: (String) -> Unit, viewModel: PlanFormViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { done?.let(onSent) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val auth = com.readyport.ui.wallet.rememberDeviceAuth()
    val openLink = rememberOpenLink()
    PlanFormContent(
        ui,
        PlanFormActions(
            setCountry = viewModel::setCountry,
            useTripDates = viewModel::useTripDates,
            setDays = viewModel::setDays,
            togglePurpose = viewModel::togglePurpose,
            setNote = viewModel::setNote,
            setTravelers = viewModel::setTravelers,
            setGendersOn = viewModel::setGendersOn,
            toggleMobility = viewModel::toggleMobility,
            setConsent = viewModel::setConsent,
            setBudget = viewModel::setBudget,
            allowWarnings = viewModel::allowWarnings,
            submit = viewModel::submit,
            confirmSend = viewModel::confirmSend,
            dismissConfirm = viewModel::dismissConfirm,
            checkAge = { auth { viewModel.checkAge() } },
            openLink = openLink,
        ),
    )
}

/** `10월 20일` 같은 짧은 날짜 */
internal fun shortDate(iso: String): String = runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("M월 d일")) }.getOrDefault(iso)

/** 다시 요청할 수 있는 날 (기기 시간대) */
internal fun shortDate(at: Instant): String = at.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("M월 d일"))

/**
 * 계획 요청 양식 (상태 없는 Content — 갤러리·테스트):
 * 비공개 안내 → (나이) → 남은 횟수 → 나라 → 며칠 → 목적·메모(개인정보 경고) → 함께 가는 사람 → 이동 조건(+민감정보 별도 동의) → 예산 →
 * AI 고지 → 빠진 칸 → 보내기(주 버튼 하나). 보내기 전에 AI 고지를 한 번 더 확인한다(대화상자).
 */
@Composable
fun PlanFormContent(ui: PlanFormUi, actions: PlanFormActions = PlanFormActions()) {
    val dimens = LocalDimens.current
    val d = ui.draft
    val check = ui.check
    val problems = if (ui.showProblems) check.problems else emptySet()
    if (ui.confirming) PlanConfirmDialog(actions.confirmSend, actions.dismissConfirm)
    AppScreen(title = stringResource(R.string.plan_form_title), speech = stringResource(R.string.plan_form_speech), icon = Icons.Outlined.AutoAwesome) {
        item(key = "private") { IconBullet(stringResource(R.string.plan_form_private), Icons.Outlined.Lock, tone = BadgeTone.Accent) }
        when (val age = ui.age) {
            is BoardAge.Status.Minor -> {
                item(key = "age-minor") {
                    NoticeBanner(stringResource(R.string.plan_age_minor_body), title = stringResource(R.string.plan_age_minor_title), icon = Icons.Outlined.ChildCare)
                }
                // 미성년이면 양식 없이 안내만(읽기 전용)
                return@AppScreen
            }
            BoardAge.Status.NeedsCheck -> item(key = "age-check") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NoticeBanner(stringResource(R.string.plan_age_check_body), title = stringResource(R.string.plan_age_check_title), icon = Icons.Outlined.VerifiedUser)
                    SecondaryButton(stringResource(R.string.board_age_check_button), onClick = actions.checkAge, icon = Icons.Outlined.VerifiedUser)
                }
            }
            BoardAge.Status.Allowed -> Unit
        }
        ui.remaining?.let { left ->
            item(key = "quota") {
                if (left.count <= 0) {
                    NoticeBanner(
                        left.nextAt?.let { stringResource(R.string.plan_quota_used, shortDate(it)) } ?: stringResource(R.string.plan_quota_used_nodate),
                        icon = Icons.Outlined.Schedule,
                        tone = BannerTone.Caution,
                    )
                } else {
                    IconBullet(stringResource(R.string.plan_quota_left, left.count), Icons.Outlined.Schedule)
                }
            }
        }
        item(key = "country") {
            FormBlock {
                KoText(stringResource(R.string.plan_country), MaterialTheme.typography.titleSmall, color = Tokens.Ink, heading = true)
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanRules.COUNTRIES.forEach { c ->
                        SelectChip(selected = d.country == c, onClick = { actions.setCountry(c) }, label = boardCountryName(c), avatar = { CountryPhoto(c, 24.dp) })
                    }
                }
                if (PlanProblem.Country in problems) ErrorLine(stringResource(R.string.plan_problem_country))
            }
        }
        item(key = "dates") { DatesBlock(ui, actions, PlanProblem.Dates in problems) }
        item(key = "purposes") { PurposesBlock(ui, actions, problems) }
        if (check.pii.isNotEmpty()) {
            item(key = "pii") {
                PiiWarningCard(
                    d.note.trim(), check.pii, onAllow = if (ui.allowWarnings) null else actions.allowWarnings,
                    blockText = R.string.plan_pii_block, warnText = R.string.plan_pii_warn, allowText = R.string.plan_pii_send_anyway,
                )
            }
        }
        item(key = "travelers") { TravelersBlock(ui, actions, problems) }
        item(key = "mobility") { MobilityBlock(ui, actions, PlanProblem.Consent in problems) }
        item(key = "budget") { BudgetBlock(d.budget, actions, PlanProblem.Budget in problems) }
        item(key = "ai") { PlanAiNotice(actions.openLink) }
        ui.error?.let { e -> item(key = "error") { ErrorLine(stringResource(e)) } }
        item(key = "submit") {
            PrimaryButton(
                stringResource(if (ui.submitting) R.string.plan_sending else R.string.plan_submit),
                onClick = actions.submit,
                enabled = ui.canSend,
                icon = Icons.AutoMirrored.Outlined.Send,
            )
        }
        item(key = "retention") {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                IconBullet(stringResource(R.string.plan_retention_note), Icons.Outlined.DeleteForever)
            }
        }
    }
}

@Composable
private fun DatesBlock(ui: PlanFormUi, actions: PlanFormActions, problem: Boolean) {
    FormBlock {
        KoText(stringResource(R.string.plan_dates), MaterialTheme.typography.titleSmall, color = Tokens.Ink, heading = true)
        val trip = ui.trip
        if (trip != null) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val range = "${shortDate(trip.start)}~${shortDate(trip.end)}"
                if (trip.days != null) {
                    SelectableCard(selected = ui.usingTripDates, onClick = { actions.useTripDates(true) }) {
                        KoText(stringResource(R.string.plan_dates_trip, range, trip.days), MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
                    }
                } else {
                    IconBullet(stringResource(R.string.plan_dates_too_long), Icons.Outlined.Schedule, tone = BadgeTone.Caution)
                }
                SelectableCard(selected = !ui.usingTripDates, onClick = { actions.useTripDates(false) }) {
                    KoText(stringResource(R.string.plan_dates_days), MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
                }
            }
        }
        if (!ui.usingTripDates) {
            CountStepper(stringResource(R.string.plan_days_label), ui.daysCount, actions.setDays, min = 1, max = PlanRules.MAX_DAYS, valueText = stringResource(R.string.plan_days_value, ui.daysCount))
        }
        KoText(stringResource(R.string.plan_dates_note), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        if (problem) ErrorLine(stringResource(R.string.plan_problem_dates))
    }
}

@Composable
private fun PurposesBlock(ui: PlanFormUi, actions: PlanFormActions, problems: Set<PlanProblem>) {
    val d = ui.draft
    FormBlock {
        KoText(stringResource(R.string.plan_purposes), MaterialTheme.typography.titleSmall, color = Tokens.Ink, heading = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanPurpose.entries.forEach { p ->
                SelectChip(selected = p in d.purposes, onClick = { actions.togglePurpose(p) }, label = stringResource(p.labelRes()), singleChoice = false)
            }
        }
        if (d.purposes.size >= PlanRules.MAX_PURPOSES) {
            KoText(stringResource(R.string.plan_purposes_max), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        }
        if (PlanProblem.Purposes in problems) ErrorLine(stringResource(R.string.plan_problem_purposes))
        BoardField(
            label = stringResource(R.string.plan_note_label),
            value = d.note,
            onChange = actions.setNote,
            max = PlanRules.NOTE_MAX,
            placeholder = stringResource(R.string.plan_note_hint),
            problem = if (PlanProblem.NoteTooLong in ui.check.problems) stringResource(R.string.plan_note_long) else null,
            minLines = 2,
        )
        IconBullet(stringResource(R.string.plan_note_privacy), Icons.Outlined.Policy, tone = BadgeTone.Caution)
    }
}

@Composable
private fun TravelersBlock(ui: PlanFormUi, actions: PlanFormActions, problems: Set<PlanProblem>) {
    val t = ui.draft.travelers
    FormBlock {
        KoText(stringResource(R.string.plan_travelers), MaterialTheme.typography.titleSmall, color = Tokens.Ink, heading = true)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CountStepper(stringResource(R.string.plan_adults), t.adults, { actions.setTravelers(t.copy(adults = it)) })
            CountStepper(stringResource(R.string.plan_seniors), t.seniors, { actions.setTravelers(t.copy(seniors = it)) })
            CountStepper(stringResource(R.string.plan_teens), t.teens, { actions.setTravelers(t.copy(teens = it)) })
            CountStepper(stringResource(R.string.plan_children), t.children, { actions.setTravelers(t.copy(children = it)) })
        }
        KoText(stringResource(R.string.plan_travelers_total, t.total), MaterialTheme.typography.titleSmall, color = Tokens.InkSecondary)
        if (PlanProblem.Travelers in ui.check.problems) ErrorLine(stringResource(R.string.plan_travelers_problem))
        ConsentRow(stringResource(R.string.plan_genders_toggle), ui.gendersOn, actions.setGendersOn)
        if (ui.gendersOn) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CountStepper(stringResource(R.string.plan_female), t.female ?: 0, { actions.setTravelers(t.copy(female = it)) })
                CountStepper(stringResource(R.string.plan_male), t.male ?: 0, { actions.setTravelers(t.copy(male = it)) })
            }
            if (PlanProblem.Genders in ui.check.problems) ErrorLine(stringResource(R.string.plan_genders_problem))
        }
    }
}

/** 이동 조건(선택) — 하나라도 고르면 민감정보 **별도 동의** 묶음이 바로 아래에 나온다 */
@Composable
private fun MobilityBlock(ui: PlanFormUi, actions: PlanFormActions, consentProblem: Boolean) {
    val d = ui.draft
    FormBlock {
        KoText(stringResource(R.string.plan_mobility), MaterialTheme.typography.titleSmall, color = Tokens.Ink, heading = true)
        KoText(stringResource(R.string.plan_mobility_lead), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanMobility.entries.forEach { m ->
                SelectChip(selected = m in d.mobility, onClick = { actions.toggleMobility(m) }, label = stringResource(m.labelRes()), singleChoice = false)
            }
        }
        if (d.mobility.isNotEmpty()) {
            CardNewsCard(title = stringResource(R.string.plan_consent_title), icon = Icons.AutoMirrored.Outlined.Accessible, tone = BadgeTone.Caution) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KoText(stringResource(R.string.plan_consent_items), MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
                    KoText(stringResource(R.string.plan_consent_purpose), MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
                    KoText(stringResource(R.string.plan_consent_retention), MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
                    KoText(stringResource(R.string.plan_consent_transfer), MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
                    KoText(stringResource(R.string.plan_consent_refuse), MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
                }
                ConsentRow(stringResource(R.string.plan_consent_check), d.sensitiveConsent, actions.setConsent)
            }
            if (consentProblem) ErrorLine(stringResource(R.string.plan_consent_needed))
        }
    }
}

@Composable
private fun BudgetBlock(selected: BudgetBand?, actions: PlanFormActions, problem: Boolean) {
    FormBlock {
        KoText(stringResource(R.string.plan_budget), MaterialTheme.typography.titleSmall, color = Tokens.Ink, heading = true)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetBand.entries.forEach { b ->
                SelectableCard(selected = selected == b, onClick = { actions.setBudget(b) }, role = Role.RadioButton) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        KoText(stringResource(b.labelRes()), MaterialTheme.typography.titleMedium, color = Tokens.Ink)
                        KoText(stringResource(b.descRes()), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                    }
                }
            }
        }
        KoText(stringResource(R.string.plan_budget_note), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        if (problem) ErrorLine(stringResource(R.string.plan_problem_budget))
    }
}

/** AI 고지 카드 (보내기 바로 위): AI(Claude)가 만든다 · 레디포트 확인 정보 아님 · 1시간 안팎 · 보내는 것 · 처리방침 */
@Composable
fun PlanAiNotice(openLink: (String) -> Unit, modifier: Modifier = Modifier) {
    CardNewsCard(title = stringResource(R.string.plan_ai_title), icon = Icons.Outlined.AutoAwesome, tone = BadgeTone.Violet, modifier = modifier) {
        KoText(stringResource(R.string.plan_ai_notice), MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
        IconBullet(stringResource(R.string.plan_processing_notice), Icons.Outlined.Schedule)
        IconBullet(stringResource(R.string.plan_send_items), Icons.Outlined.Public)
        LinkRow(stringResource(R.string.plan_privacy_link), { openLink(PRIVACY_URL) })
    }
}

@Composable
private fun PlanConfirmDialog(onSend: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onSend, modifier = Modifier.minTouch()) {
                KoText(stringResource(R.string.plan_confirm_send), MaterialTheme.typography.labelLarge, color = Tokens.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minTouch()) {
                KoText(stringResource(R.string.plan_confirm_back), MaterialTheme.typography.labelLarge, color = Tokens.InkSecondary)
            }
        },
        title = { KoText(stringResource(R.string.plan_confirm_title), MaterialTheme.typography.titleLarge, glueShort = true) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                KoText(stringResource(R.string.plan_ai_notice), MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
                KoText(stringResource(R.string.plan_processing_notice), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = Tokens.Surface,
        titleContentColor = Tokens.Ink,
        textContentColor = Tokens.Ink,
    )
}
