package com.readyport.ui.pack

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.pack.CountryPack
import com.readyport.pack.EmergencyContact
import com.readyport.pack.Loaded
import com.readyport.pack.PackOrigin
import com.readyport.pack.Phrase
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SourceFooter
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.TopicCard
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/** 화면 표기: 2026-09-28 → 2026.09.28 (PRD 5장 공통) */
fun displayDate(iso: String) = iso.replace('-', '.')

// ======================= 도움 =======================

@Composable
fun HelpScreen(viewModel: HelpViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    HelpContent(
        ui = ui,
        onSelectCountry = viewModel::selectCountry,
        onSpeak = viewModel::speak,
        // 전화 앱에 번호만 넣어 연다. 권한이 필요 없고, 거는 것은 사용자가 한다
        onCall = { number -> context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri())) },
    )
}

@Composable
fun HelpContent(
    ui: HelpUi,
    onSelectCountry: (String) -> Unit,
    onSpeak: (Phrase) -> Unit,
    onCall: (String) -> Unit,
) {
    val pack = ui.selected?.value
    var selectedPhrase by remember(pack?.country) { mutableStateOf(pack?.phrases?.firstOrNull()) }
    var fullScreen by remember { mutableStateOf(false) }
    fun sourceName(id: String) = pack?.source(id)?.name ?: id

    AppScreen(
        title = stringResource(R.string.help_title),
        speech = stringResource(R.string.help_speech),
        headerActions = {
            StatusChip(stringResource(R.string.help_offline_badge), container = Tokens.SuccessBg, content = Tokens.SuccessText)
        },
    ) {
        if (ui.countries.size > 1) {
            item(key = "countries") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.help_choose_country), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.countries.forEach { c ->
                            FilterChip(
                                selected = c.code == pack?.country,
                                onClick = { onSelectCountry(c.code) },
                                label = { Text(c.nameKo, style = MaterialTheme.typography.labelLarge) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
            }
        }
        if (pack == null) {
            item(key = "no-country") { TopicCard(stringResource(R.string.help_no_country), null, tone = CardTone.Notice) }
        } else {
            selectedPhrase?.let { phrase ->
                item(key = "phrase-card") {
                    PhraseCard(
                        phrase = phrase,
                        languageName = pack.localLanguage?.nameKo.orEmpty(),
                        ttsAvailable = ui.ttsAvailable,
                        onSpeak = { onSpeak(phrase) },
                        onFullScreen = { fullScreen = true },
                    )
                }
            }
            item(key = "phrases-title") {
                Text(stringResource(R.string.help_phrases_title), style = MaterialTheme.typography.titleLarge)
            }
            // 자주 쓰는 문장 2열 버튼 (PRD 5.11). 스와이프 없이 버튼으로만
            pack.phrases.chunked(2).forEachIndexed { i, pair ->
                item(key = "phrases-$i") {
                    Row(horizontalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                        pair.forEach { p ->
                            OutlinedButton(
                                onClick = { selectedPhrase = p },
                                modifier = Modifier.weight(1f).heightIn(min = LocalDimens.current.buttonHeight),
                            ) { Text(p.ko, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
                        }
                        if (pair.size == 1) Column(Modifier.weight(1f)) {}
                    }
                }
            }
            item(key = "emergency") {
                InfoCard(tone = CardTone.Caution) {
                    Text(stringResource(R.string.help_emergency_title), style = MaterialTheme.typography.titleMedium)
                    pack.emergency.forEach { EmergencyRow(it, onCall) }
                    pack.emergency.firstOrNull()?.let { SourceFooter(sourceName(it.source), displayDate(it.lastVerified)) }
                }
            }
            pack.embassy?.let { emb ->
                item(key = "embassy") {
                    InfoCard {
                        Text(stringResource(R.string.help_embassy), style = MaterialTheme.typography.titleMedium)
                        Text(emb.nameKo, style = MaterialTheme.typography.bodyLarge)
                        Text(emb.address, style = MaterialTheme.typography.bodyMedium)
                        CallButton(emb.nameKo, emb.phone, onCall)
                        emb.emergencyPhone?.let {
                            Text(stringResource(R.string.help_embassy_after_hours), style = MaterialTheme.typography.labelMedium)
                            CallButton(emb.nameKo + " " + stringResource(R.string.help_embassy_after_hours), it, onCall)
                        }
                        SourceFooter(sourceName(emb.source), displayDate(emb.lastVerified))
                    }
                }
            }
            if (pack.procedures.isNotEmpty()) {
                item(key = "procedures-title") {
                    Text(stringResource(R.string.help_procedures_title), style = MaterialTheme.typography.titleLarge)
                }
                pack.procedures.forEach { proc ->
                    item(key = "proc-${proc.id}") {
                        InfoCard {
                            Text(proc.titleKo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            proc.stepsKo.forEachIndexed { i, step ->
                                Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                            }
                            SourceFooter(sourceName(proc.source), displayDate(proc.lastVerified))
                        }
                    }
                }
            }
        }
        if (ui.common.isNotEmpty()) {
            item(key = "common") {
                InfoCard {
                    Text(stringResource(R.string.help_common_title), style = MaterialTheme.typography.titleMedium)
                    ui.common.forEach { EmergencyRow(it, onCall) }
                    ui.common.firstOrNull()?.let { SourceFooter(ui.commonSourceName ?: it.source, displayDate(it.lastVerified)) }
                }
            }
        }
    }

    if (fullScreen) {
        selectedPhrase?.let { phrase -> PhraseFullScreen(phrase, onClose = { fullScreen = false }) }
    }
}

/** 선택된 문장 큰 카드: 현지어 30sp + 한국어 + 영어 (PRD 5.11) */
@Composable
private fun PhraseCard(
    phrase: Phrase,
    languageName: String,
    ttsAvailable: Boolean,
    onSpeak: () -> Unit,
    onFullScreen: () -> Unit,
) {
    InfoCard(tone = CardTone.Navy) {
        if (!phrase.reviewed) StatusChip(stringResource(R.string.help_unreviewed), Tokens.CautionBg, Tokens.CautionText)
        Text(phrase.local, fontSize = 30.sp, lineHeight = 40.sp, style = MaterialTheme.typography.headlineLarge)
        phrase.romanized?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(phrase.ko, style = MaterialTheme.typography.titleMedium)
        Text(phrase.en, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ttsAvailable) {
                OutlinedButton(onClick = onSpeak, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.help_play_sound), color = Tokens.Surface, style = MaterialTheme.typography.labelLarge)
                }
            }
            OutlinedButton(onClick = onFullScreen, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.help_full_screen), color = Tokens.Surface, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (!ttsAvailable && languageName.isNotEmpty()) {
            Text(stringResource(R.string.help_no_tts, languageName), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 기사님·직원에게 보여 주는 전체 화면 */
@Composable
private fun PhraseFullScreen(phrase: Phrase, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Tokens.Surface)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(phrase.local, fontSize = 56.sp, lineHeight = 72.sp, textAlign = TextAlign.Center, color = Tokens.Ink)
            Text(phrase.en, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = Tokens.InkSecondary)
            Text(phrase.ko, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = Tokens.InkSecondary)
            PrimaryButton(stringResource(R.string.help_close), onClick = onClose)
        }
    }
}

@Composable
private fun EmergencyRow(contact: EmergencyContact, onCall: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(contact.labelKo, style = MaterialTheme.typography.bodyLarge)
        contact.noteKo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        CallButton(contact.labelKo, contact.number, onCall)
    }
}

@Composable
private fun CallButton(label: String, number: String, onCall: (String) -> Unit) {
    val description = stringResource(R.string.help_call, label)
    OutlinedButton(
        onClick = { onCall(number) },
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$description $number" },
    ) { Text(number, style = MaterialTheme.typography.titleMedium) }
}
