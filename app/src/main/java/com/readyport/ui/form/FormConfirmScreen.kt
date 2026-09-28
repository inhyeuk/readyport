package com.readyport.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.autofill.FieldValue
import com.readyport.autofill.FormValues
import com.readyport.autofill.RecipeField
import com.readyport.autofill.ValueOrigin
import com.readyport.security.SecureScreen
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.TopicCard
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.wallet.rememberDeviceAuth
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import kotlinx.coroutines.launch

@Composable
fun FormConfirmScreen(
    onAutofill: () -> Unit,
    onManual: () -> Unit,
    onRegisterPassport: () -> Unit,
    viewModel: FormConfirmViewModel = hiltViewModel(),
) {
    SecureScreen()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val auth = rememberDeviceAuth()
    FormConfirmContent(
        ui = ui,
        onSetValue = viewModel::setValue,
        onUnlock = { auth { viewModel.unlock() } },
        onRegisterPassport = onRegisterPassport,
        onConfirm = {
            scope.launch {
                when (viewModel.confirm()) {
                    WalletRepository.SaveResult.Saved -> onAutofill()
                    WalletRepository.SaveResult.Failed -> Unit
                    else -> auth { scope.launch { if (viewModel.confirm() == WalletRepository.SaveResult.Saved) onAutofill() } }
                }
            }
        },
        onManual = onManual,
    )
}

@Composable
fun FormConfirmContent(
    ui: ConfirmUi,
    onSetValue: (String, String) -> Unit,
    onUnlock: () -> Unit,
    onRegisterPassport: () -> Unit,
    onConfirm: () -> Unit,
    onManual: () -> Unit,
) {
    val ctx = ui.context
    val recipe = ctx?.recipe
    var localLarge by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val formName = ctx?.form?.nameKo ?: ctx?.formId.orEmpty()

    AppScreen(
        title = stringResource(R.string.form_confirm_title, formName),
        subtitle = stringResource(R.string.form_confirm_body),
        speech = stringResource(R.string.form_confirm_body),
        headerActions = {
            FilterChip(
                selected = localLarge,
                onClick = { localLarge = !localLarge },
                label = { Text(stringResource(R.string.form_local_large), style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        },
    ) {
        item(key = "not-affiliated") { TopicCard(stringResource(R.string.guide_not_affiliated), null, tone = CardTone.Notice) }
        if (ctx == null) return@AppScreen
        if (!ctx.autofillAvailable) {
            item(key = "no-autofill") {
                InfoCard(tone = CardTone.Caution) {
                    Text(stringResource(if (ctx.killed) R.string.form_killed else R.string.form_no_recipe), style = MaterialTheme.typography.bodyLarge)
                    PrimaryButton(stringResource(R.string.form_manual_mode), onClick = onManual)
                }
            }
            if (recipe == null) return@AppScreen
        }
        when (val w = ui.wallet) {
            is WalletState.Unlocked -> if (w.contents.passport == null) {
                item(key = "need-passport") {
                    InfoCard(tone = CardTone.Caution) {
                        Text(stringResource(R.string.form_need_passport), style = MaterialTheme.typography.bodyLarge)
                        PrimaryButton(stringResource(R.string.wallet_passport_add), onClick = onRegisterPassport)
                    }
                }
                return@AppScreen
            }
            else -> {
                item(key = "locked") {
                    InfoCard {
                        Text(stringResource(R.string.wallet_locked_title), style = MaterialTheme.typography.titleMedium)
                        PrimaryButton(stringResource(R.string.wallet_unlock), onClick = onUnlock)
                    }
                }
                return@AppScreen
            }
        }
        recipe!!

        // 서류에서 온 값: 묶어서 한 번 확인 (PRD 6.2)
        val bulk = recipe.fields.filter { it.confirm == "bulk" && ui.values[it.key]?.isEmpty == false }
        // 고르거나 적는 값 + 서류에 없던 값: 하나씩
        val individual = recipe.fields.filter { it.confirm == "individual" || ui.values[it.key]?.isEmpty != false }

        item(key = "bulk") {
            InfoCard {
                Text(stringResource(R.string.form_from_documents), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                bulk.forEach { f ->
                    val v = ui.values.getValue(f.key)
                    if (editing && v.origin != ValueOrigin.None) {
                        EditRow(f, localLarge, ui.draft[f.key] ?: v.display.orEmpty(), onSetValue)
                    } else {
                        ValueRow(f, v, localLarge)
                    }
                }
            }
        }
        if (individual.isNotEmpty()) {
            item(key = "individual") {
                InfoCard(tone = CardTone.Caution) {
                    Text(stringResource(R.string.form_choose_yourself), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    individual.forEach { f ->
                        val options = f.optionsRef?.let { recipe.options[it] }
                        if (options != null) {
                            FieldLabel(f, localLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                options.forEach { o ->
                                    FilterChip(
                                        selected = ui.draft[f.key] == o.value,
                                        onClick = { onSetValue(f.key, o.value) },
                                        label = {
                                            Text(listOfNotNull(o.ko, o.en, o.local).joinToString(" · "), style = MaterialTheme.typography.labelLarge)
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                }
                            }
                        } else {
                            EditRow(f, localLarge, ui.draft[f.key].orEmpty(), onSetValue)
                        }
                    }
                }
            }
        }
        if (!recipe.labelsReviewed) {
            item(key = "unreviewed") {
                Text(stringResource(R.string.form_labels_unreviewed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "notice") { Text(stringResource(R.string.form_confirm_notice), style = MaterialTheme.typography.bodyLarge) }
        item(key = "actions") {
            val missing = FormValues.missingRequired(recipe, ui.values)
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                if (missing.isNotEmpty()) {
                    Text(
                        stringResource(R.string.form_need_required, missing.joinToString(", ") { it.labels.ko }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tokens.CautionText,
                    )
                }
                if (ui.saveFailed) Text(stringResource(R.string.form_save_failed), color = Tokens.DangerText)
                if (ctx.autofillAvailable) {
                    PrimaryButton(stringResource(R.string.form_confirm_yes), onClick = onConfirm, enabled = missing.isEmpty())
                }
                OutlinedButton(
                    onClick = { editing = !editing },
                    modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight),
                ) { Text(stringResource(if (editing) R.string.form_fix_done else R.string.form_confirm_fix), style = MaterialTheme.typography.labelLarge) }
                TextButton(onClick = onManual, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.form_manual_mode), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 한글 라벨(크게) + 영어·현지어(작게). '현지어 크게'를 켜면 현지어를 크게 */
@Composable
private fun FieldLabel(f: RecipeField, localLarge: Boolean) {
    Column {
        Text(f.labels.ko, style = MaterialTheme.typography.titleMedium)
        val sub = listOfNotNull(f.labels.en, f.labels.local).joinToString(" · ")
        Text(
            sub,
            style = if (localLarge) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ValueRow(f: RecipeField, v: FieldValue, localLarge: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) { FieldLabel(f, localLarge) }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(v.display ?: stringResource(R.string.form_empty_value), style = MaterialTheme.typography.titleMedium)
            OriginChip(v.origin)
        }
    }
}

@Composable
private fun EditRow(f: RecipeField, localLarge: Boolean, value: String, onSetValue: (String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(f, localLarge)
        OutlinedTextField(
            value = value,
            onValueChange = { onSetValue(f.key, it) },
            singleLine = true,
            placeholder = f.hintKo?.let { { Text(it) } },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OriginChip(origin: ValueOrigin) {
    val label = when (origin) {
        ValueOrigin.Passport -> R.string.form_origin_passport
        ValueOrigin.Flight -> R.string.form_origin_flight
        ValueOrigin.Lodging -> R.string.form_origin_lodging
        ValueOrigin.User -> R.string.form_origin_user
        ValueOrigin.None -> return
    }
    StatusChip(stringResource(label))
}
