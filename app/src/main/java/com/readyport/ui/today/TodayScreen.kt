package com.readyport.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.trip.TripStage
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.TopicCard
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.wallet.rememberDeviceAuth
import com.readyport.vault.WalletRepository
import kotlinx.coroutines.launch

/** 여행 단계 표시줄 6칸 (PRD 5.1) */
private val StageLabels = listOf(
    R.string.stage_prepare, R.string.stage_departure, R.string.stage_arrival,
    R.string.stage_traveling, R.string.stage_return, R.string.stage_wrapup,
)

/** '오늘' 화면에서 다른 곳으로 가는 길 */
data class TodayActions(
    val openSettings: () -> Unit = {},
    val makeTrip: () -> Unit = {},
    val editTrip: () -> Unit = {},
    val explore: () -> Unit = {},
    val prepare: () -> Unit = {},
    val openForm: (String) -> Unit = {},
    val registerPassport: () -> Unit = {},
    val present: () -> Unit = {},
    val help: () -> Unit = {},
    val goStay: () -> Unit = {},
    val expense: () -> Unit = {},
)

@Composable
fun TodayScreen(actions: TodayActions, viewModel: TodayViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val auth = rememberDeviceAuth()
    TodayContent(
        ui = ui,
        actions = actions,
        onArrived = viewModel::markArrived,
        onArrivalDone = viewModel::dismissArrival,
        onDestroy = {
            scope.launch {
                if (viewModel.destroyPassportInfo() != WalletRepository.SaveResult.Saved) {
                    auth { scope.launch { viewModel.destroyPassportInfo() } }
                }
            }
        },
        onPostpone = viewModel::postponeDestroy,
        onNewTrip = { viewModel.newTrip(); actions.makeTrip() },
    )
}

@Composable
fun TodayContent(
    ui: TodayUi,
    actions: TodayActions,
    onArrived: () -> Unit,
    onArrivalDone: () -> Unit,
    onDestroy: () -> Unit,
    onPostpone: () -> Unit,
    onNewTrip: () -> Unit,
) {
    val stage = ui.stage
    val country = ui.countryName.orEmpty()
    val title = when (stage.stage) {
        TripStage.NoTrip -> stringResource(R.string.today_title)
        TripStage.Traveling, TripStage.Arrival -> stringResource(R.string.today_day_n, country, (stage.dayOfTrip ?: 1).toInt())
        else -> stringResource(R.string.today_trip_title, country)
    }
    val subtitle = when (stage.stage) {
        TripStage.NoTrip -> stringResource(R.string.today_no_trip)
        TripStage.Preparing -> stringResource(R.string.today_d_day, (stage.daysLeft ?: 0).toInt())
        else -> stringResource(R.string.today_title)
    }
    val stageName = stringResource(StageLabels[stage.stage.barIndex])

    AppScreen(
        title = title,
        subtitle = subtitle,
        speech = stringResource(R.string.today_speech_trip, title, stageName),
        headerActions = {
            IconButton(onClick = actions.openSettings, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                    modifier = Modifier.size(if (LocalDimens.current.easyMode) 32.dp else 24.dp),
                )
            }
        },
    ) {
        item(key = "stages") { StageBar(current = stage.stage.barIndex) }

        when (stage.stage) {
            TripStage.NoTrip -> {
                item(key = "next") {
                    NextCard(
                        label = stringResource(R.string.today_next_label),
                        title = stringResource(R.string.today_next_title),
                        body = stringResource(R.string.today_next_body),
                        button = stringResource(R.string.today_make_trip),
                        onClick = actions.makeTrip,
                    )
                }
                item(key = "explore") {
                    OutlinedButton(onClick = actions.explore, modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight)) {
                        Text(stringResource(R.string.today_next_button), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            TripStage.Preparing -> item(key = "next") {
                // 한 화면에 할 일 하나 (PRD 1.1): 입국 카드 > 여권 > 준비물
                when {
                    stage.formWindowOpen && ui.form != null && ui.hasPassport != false -> NextCard(
                        label = stringResource(R.string.today_next_label),
                        title = stringResource(R.string.today_task_form_title, ui.form.nameKo),
                        body = stringResource(R.string.today_task_form_body),
                        button = stringResource(R.string.prepare_form_open),
                        onClick = { actions.openForm(ui.form.id) },
                    )
                    ui.hasPassport == false -> NextCard(
                        label = stringResource(R.string.today_next_label),
                        title = stringResource(R.string.today_task_passport_title),
                        body = stringResource(R.string.today_task_passport_body),
                        button = stringResource(R.string.wallet_passport_add),
                        onClick = actions.registerPassport,
                    )
                    else -> NextCard(
                        label = stringResource(R.string.today_next_label),
                        title = stringResource(R.string.today_task_ready_title),
                        body = stringResource(R.string.today_task_ready_body),
                        button = stringResource(R.string.today_open_prepare),
                        onClick = actions.prepare,
                    )
                }
            }
            TripStage.Departure -> {
                if (stage.formWindowOpen && ui.form != null) {
                    item(key = "form") {
                        NextCard(
                            label = stringResource(R.string.today_next_label),
                            title = stringResource(R.string.today_task_form_title, ui.form.nameKo),
                            body = stringResource(R.string.today_task_form_body),
                            button = stringResource(R.string.prepare_form_open),
                            onClick = { actions.openForm(ui.form.id) },
                        )
                    }
                }
                item(key = "departure") {
                    StepsCard(
                        stringResource(R.string.today_departure_steps_title),
                        listOf(
                            R.string.today_departure_step1, R.string.today_departure_step2, R.string.today_departure_step3,
                            R.string.today_departure_step4, R.string.today_departure_step5,
                        ).map { stringResource(it) },
                    )
                }
                item(key = "arrived") { PrimaryButton(stringResource(R.string.today_arrived_button), onClick = onArrived) }
            }
            TripStage.Arrival -> {
                item(key = "qr") {
                    NextCard(
                        label = stringResource(R.string.today_next_label),
                        title = stringResource(R.string.today_arrival_title),
                        body = null,
                        button = stringResource(R.string.today_show_qr),
                        onClick = actions.present,
                    )
                }
                item(key = "arrival") {
                    StepsCard(
                        stringResource(R.string.today_arrival_title),
                        listOf(
                            R.string.today_arrival_step1, R.string.today_arrival_step2, R.string.today_arrival_step3,
                            R.string.today_arrival_step4, R.string.today_arrival_step5,
                        ).map { stringResource(it) },
                    )
                }
                item(key = "done") {
                    OutlinedButton(onClick = onArrivalDone, modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight)) {
                        Text(stringResource(R.string.today_arrival_done), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            TripStage.Traveling -> item(key = "grid") {
                // 2×2 큰 버튼 (PRD 5.1)
                Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                        BigTile(stringResource(R.string.today_go_stay), actions.goStay, Modifier.weight(1f))
                        BigTile(stringResource(R.string.today_phrases), actions.help, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                        BigTile(stringResource(R.string.today_show_qr), actions.present, Modifier.weight(1f))
                        BigTile(stringResource(R.string.today_expense), actions.expense, Modifier.weight(1f))
                    }
                }
            }
            TripStage.Return -> {
                item(key = "return") {
                    TopicCard(stringResource(R.string.today_return_title), stringResource(R.string.today_return_customs))
                }
                if (stage.askDestroy) {
                    item(key = "destroy") {
                        InfoCard(tone = CardTone.Caution) {
                            Text(stringResource(R.string.today_destroy_title), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.today_destroy_body), style = MaterialTheme.typography.bodyLarge)
                            PrimaryButton(stringResource(R.string.today_destroy_now), onClick = onDestroy)
                            OutlinedButton(onClick = onPostpone, modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight)) {
                                Text(stringResource(R.string.today_destroy_later), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            TripStage.WrapUp -> item(key = "wrap") {
                NextCard(
                    label = stringResource(R.string.stage_wrapup),
                    title = stringResource(R.string.today_wrapup_title),
                    body = null,
                    button = stringResource(R.string.today_new_trip),
                    onClick = onNewTrip,
                )
            }
        }
        if (stage.stage != TripStage.NoTrip && stage.stage != TripStage.WrapUp) {
            item(key = "edit") {
                OutlinedButton(onClick = actions.editTrip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.trip_edit_title), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item(key = "help") {
            TopicCard(
                title = stringResource(R.string.today_help_title),
                body = stringResource(R.string.today_help_body),
            )
        }
    }
}

@Composable
private fun NextCard(label: String, title: String, body: String?, button: String, onClick: () -> Unit) {
    InfoCard(tone = CardTone.Accent) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium)
        PrimaryButton(
            text = button,
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Tokens.Surface, contentColor = Tokens.Accent),
        )
    }
}

@Composable
private fun StepsCard(title: String, steps: List<String>) {
    InfoCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        steps.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun BigTile(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 96.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StageBar(current: Int) {
    val labels = StageLabels.map { stringResource(it) }
    val description = stringResource(R.string.today_stage_desc, labels[current], current + 1, labels.size)
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, _ ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(color = if (index <= current) Tokens.Accent else Tokens.Line, shape = MaterialTheme.shapes.small),
                )
            }
        }
        if (LocalDimens.current.easyMode) {
            // 쉬운 모드: 작은 글자 6개 대신 지금 단계만 한 줄로 크게
            Text(
                text = stringResource(R.string.today_stage_now, labels[current], current + 1, labels.size),
                style = MaterialTheme.typography.bodyLarge,
                color = Tokens.Accent,
            )
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                val style = MaterialTheme.typography.labelSmall
                BasicText(
                    text = label,
                    style = style.copy(
                        color = if (index == current) Tokens.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}
