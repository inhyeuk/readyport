package com.readyport.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.plan.PlanDates
import com.readyport.plan.PlanError
import com.readyport.plan.PlanRepository
import com.readyport.plan.PlanRequest
import com.readyport.plan.PlanRules
import com.readyport.plan.PlanStatus
import com.readyport.ui.board.BoardCard
import com.readyport.ui.board.BoardEmpty
import com.readyport.ui.board.CountryPhoto
import com.readyport.ui.board.ErrorLine
import com.readyport.ui.board.boardCountryName
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.ButtonPlacement
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.sectionGap
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ======================= 내 계획 요청 =======================

/** 확인을 묻는 동작 (취소·삭제) */
@Immutable
data class PlanConfirm(val request: PlanRequest, val delete: Boolean)

@Immutable
data class PlanListUi(
    val loading: Boolean = true,
    val offline: Boolean = false,
    val failed: Boolean = false,
    val items: List<PlanRequest> = emptyList(),
    val confirm: PlanConfirm? = null,
    /** 실패 문구 (취소·삭제) */
    val error: Int? = null,
)

data class PlanListActions(
    val open: (PlanRequest) -> Unit = {},
    val askCancel: (PlanRequest) -> Unit = {},
    val askDelete: (PlanRequest) -> Unit = {},
    val confirm: () -> Unit = {},
    val dismiss: () -> Unit = {},
    val retry: () -> Unit = {},
    val newRequest: () -> Unit = {},
)

@HiltViewModel
class PlanListViewModel @Inject constructor(private val plans: PlanRepository) : ViewModel() {
    private val _ui = MutableStateFlow(PlanListUi())
    val ui: StateFlow<PlanListUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch { plans.revision.collect { reload() } }
    }

    fun reload() {
        _ui.update { it.copy(loading = it.items.isEmpty(), offline = false, failed = false) }
        viewModelScope.launch {
            try {
                val items = plans.myRequests()
                _ui.update { it.copy(loading = false, items = items) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, offline = e is PlanError.Offline, failed = e !is PlanError.Offline) }
            }
        }
    }

    fun ask(req: PlanRequest, delete: Boolean) = _ui.update { it.copy(confirm = PlanConfirm(req, delete), error = null) }

    fun dismiss() = _ui.update { it.copy(confirm = null) }

    fun confirm() {
        val c = _ui.value.confirm ?: return
        _ui.update { it.copy(confirm = null) }
        viewModelScope.launch {
            try {
                if (c.delete) plans.delete(c.request) else plans.cancel(c.request)
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.planErrorRes()) }
            }
        }
    }
}

@Composable
fun PlanListScreen(openPlan: (String) -> Unit, newRequest: () -> Unit, viewModel: PlanListViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }
    PlanListContent(
        ui,
        PlanListActions(
            open = { openPlan(it.id) },
            askCancel = { viewModel.ask(it, delete = false) },
            askDelete = { viewModel.ask(it, delete = true) },
            confirm = viewModel::confirm,
            dismiss = viewModel::dismiss,
            retry = viewModel::reload,
            newRequest = newRequest,
        ),
    )
}

/** 요청 일수 (일수로 보냈으면 그 값, 날짜로 보냈으면 날짜로 센다) */
internal fun PlanRequest.dayCount(): Int? = days ?: if (startDate != null && endDate != null) PlanRules.days(PlanDates.Range(startDate, endDate)) else null

/**
 * 내 계획 요청 (상태 없는 Content): 비공개 안내 → 요청 카드(나라·일수·보낸 날·상태 표시·할 수 있는 일) → 새 요청 → 보관 안내.
 * 도착 = 계획 보기(주 버튼) · 대기/만드는 중 = 요청 취소 · 취소함 = 삭제 · 실패 = 이유.
 */
@Composable
fun PlanListContent(ui: PlanListUi, actions: PlanListActions = PlanListActions()) {
    val dimens = LocalDimens.current
    ui.confirm?.let { c ->
        DestructiveConfirm(
            title = stringResource(if (c.delete) R.string.plan_delete_confirm_title else R.string.plan_cancel_confirm_title),
            body = stringResource(if (c.delete) R.string.plan_delete_confirm_body else R.string.plan_cancel_confirm_body),
            confirmLabel = stringResource(if (c.delete) R.string.plan_delete else R.string.plan_cancel),
            onConfirm = actions.confirm,
            onDismiss = actions.dismiss,
        )
    }
    AppScreen(title = stringResource(R.string.plan_list_title), speech = stringResource(R.string.plan_list_speech), icon = Icons.AutoMirrored.Outlined.EventNote) {
        item(key = "private") { IconBullet(stringResource(R.string.plan_form_private), Icons.Outlined.Lock, tone = BadgeTone.Accent) }
        ui.error?.let { e -> item(key = "error") { ErrorLine(stringResource(e)) } }
        when {
            ui.loading -> item(key = "loading") { KoText(stringResource(R.string.plan_loading), MaterialTheme.typography.bodyLarge, color = Tokens.InkSecondary) }
            ui.offline || ui.failed -> item(key = "offline") {
                BoardEmpty(
                    title = stringResource(if (ui.offline) R.string.plan_list_offline else R.string.plan_list_error),
                    body = null,
                    icon = if (ui.offline) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline,
                ) {
                    SecondaryButton(stringResource(R.string.plan_retry), onClick = actions.retry, icon = Icons.Outlined.Refresh, fillWidth = false)
                }
            }
            ui.items.isEmpty() -> item(key = "empty") {
                BoardEmpty(stringResource(R.string.plan_list_empty_title), stringResource(R.string.plan_list_empty_body))
            }
            else -> ui.items.forEach { r -> item(key = "req-${r.id}") { PlanRequestCard(r, actions) } }
        }
        item(key = "new") { SecondaryButton(stringResource(R.string.plan_list_new), onClick = actions.newRequest, icon = Icons.Outlined.AddCircleOutline) }
        sectionGap("gap-footer")
        item(key = "footer") {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                IconBullet(stringResource(R.string.plan_list_retention), Icons.Outlined.DeleteForever)
                IconBullet(stringResource(R.string.plan_list_quota_note), Icons.Outlined.Schedule)
            }
        }
    }
}

@Composable
private fun PlanRequestCard(r: PlanRequest, actions: PlanListActions) {
    val dimens = LocalDimens.current
    BoardCard {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (r.country in PlanRules.COUNTRIES) CountryPhoto(r.country, 32.dp)
                val name = if (r.country in PlanRules.COUNTRIES) boardCountryName(r.country) else r.country
                val days = r.dayCount()
                KoText(
                    if (days != null) stringResource(R.string.plan_row_title, name, days) else name,
                    MaterialTheme.typography.titleMedium,
                    Modifier.weight(1f),
                    color = Tokens.Ink,
                    heading = true,
                )
            }
            PlanStatusTag(r.status)
            r.createdAt?.let { KoText(stringResource(R.string.plan_row_sent, shortDate(it)), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary) }
            when (r.status) {
                PlanStatus.Queued, PlanStatus.Processing -> IconBullet(stringResource(R.string.plan_row_waiting), Icons.Outlined.Schedule)
                PlanStatus.Failed -> IconBullet(stringResource(r.failure.textRes()), Icons.Outlined.ErrorOutline, tone = BadgeTone.Danger)
                PlanStatus.Cancelled -> IconBullet(stringResource(R.string.plan_row_cancelled), Icons.Outlined.Block)
                else -> Unit
            }
            when {
                r.status == PlanStatus.Done -> PrimaryButton(stringResource(R.string.plan_open), onClick = { actions.open(r) }, icon = Icons.AutoMirrored.Outlined.EventNote)
                r.status.cancellable -> SecondaryButton(stringResource(R.string.plan_cancel), onClick = { actions.askCancel(r) }, icon = Icons.Outlined.Block, tone = BadgeTone.Neutral)
                r.status.deletable -> DangerButton(stringResource(R.string.plan_delete), onClick = { actions.askDelete(r) }, placement = ButtonPlacement.ItemAction)
                else -> Unit
            }
        }
    }
}
