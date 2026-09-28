package com.readyport.ui.form

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.autofill.FieldValue
import com.readyport.security.SecureScreen
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.TopicCard
import com.readyport.ui.wallet.rememberDeviceAuth

/** 수동 모드 (PRD 6.6): 자동 입력이 안 될 때 값 복사 + 공식 사이트 */
@Composable
fun ManualModeScreen(viewModel: AutofillViewModel = hiltViewModel()) {
    SecureScreen()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val auth = rememberDeviceAuth()
    val url = ui.context?.recipe?.startUrl ?: ui.context?.form?.officialUrl
    ManualModeContent(
        ui = ui,
        onUnlock = { auth { viewModel.unlock() } },
        onCopy = { label, text -> viewModel.clipboard.copy(label, text) },
        onOpenSite = {
            url?.takeIf { it.startsWith("https://") }?.let { context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri())) }
        },
    )
}

@Composable
fun ManualModeContent(
    ui: AutofillUi,
    onUnlock: () -> Unit,
    onCopy: (label: String, text: String) -> Unit,
    onOpenSite: () -> Unit,
) {
    val recipe = ui.context?.recipe
    var copied by remember { mutableStateOf<String?>(null) }
    AppScreen(
        title = stringResource(R.string.manual_title),
        subtitle = stringResource(R.string.manual_body),
        speech = stringResource(R.string.manual_body),
    ) {
        item(key = "open") { PrimaryButton(stringResource(R.string.manual_open_site), onClick = onOpenSite) }
        item(key = "human") { TopicCard(stringResource(R.string.autofill_human_banner), null, tone = CardTone.Caution) }
        if (ui.locked) {
            item(key = "locked") {
                InfoCard {
                    Text(stringResource(R.string.wallet_locked_title), style = MaterialTheme.typography.titleMedium)
                    PrimaryButton(stringResource(R.string.wallet_unlock), onClick = onUnlock)
                }
            }
            return@AppScreen
        }
        recipe?.steps?.forEach { step ->
            item(key = "step-${step.id}") {
                InfoCard {
                    Text(step.titleKo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    step.noteKo?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    step.fields.forEach { f ->
                        val v: FieldValue? = ui.values[f.key]
                        val text = (v?.value ?: v?.display).orEmpty()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${f.labels.ko} · ${f.labels.en}", style = MaterialTheme.typography.labelMedium)
                                Text(text.ifEmpty { stringResource(R.string.form_empty_value) }, style = MaterialTheme.typography.titleMedium)
                                f.hintKo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (text.isNotEmpty()) {
                                val label = stringResource(if (copied == f.key) R.string.manual_copied else R.string.manual_copy)
                                OutlinedButton(
                                    onClick = { onCopy(f.labels.en, text); copied = f.key },
                                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "${f.labels.ko} $label" },
                                ) { Text(label) }
                            }
                        }
                    }
                }
            }
        }
    }
}
