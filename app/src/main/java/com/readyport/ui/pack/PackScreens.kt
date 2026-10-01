package com.readyport.ui.pack

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.pack.EmergencyContact
import com.readyport.pack.Phrase
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.EmergencyCallTile
import com.readyport.ui.components.EmptyState
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.KoText
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.Photos
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.SelectableCard
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.selectionBadgeContainer
import com.readyport.ui.components.ShowLocalBody
import com.readyport.ui.components.SourceFooter
import com.readyport.ui.components.SourceList
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.Step
import com.readyport.ui.components.StepList
import com.readyport.ui.components.TileGrid
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.rememberKeyIndex
import com.readyport.ui.components.rememberThumbnail
import com.readyport.ui.components.resolveSourceName
import com.readyport.ui.components.scrollToKey
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.Tokens
import kotlinx.coroutines.launch

// ======================= 도움 (DESIGN_SPEC 6-20, D6) =======================

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

/** 2열 칸에 둘 수 있는 짧은 번호 (`1155`·`191`). 더 길면 폭 전체 (6-20 번호 길이 규칙) */
private const val SHORT_NUMBER_MAX = 6

/**
 * 긴급 번호를 줄로 나눈다: 짧은 번호는 [columns]칸씩 한 줄, 긴 번호는 혼자 한 줄.
 * 남은 짧은 번호 하나는 반쪽 칸 대신 폭 전체로 (빈 칸을 남기지 않는다).
 */
internal fun emergencyRows(contacts: List<EmergencyContact>, columns: Int): List<List<EmergencyContact>> {
    val rows = mutableListOf<List<EmergencyContact>>()
    val buffer = mutableListOf<EmergencyContact>()
    fun flush() {
        if (buffer.isNotEmpty()) rows += buffer.toList()
        buffer.clear()
    }
    contacts.forEach { c ->
        if (columns <= 1 || c.number.length > SHORT_NUMBER_MAX) {
            flush()
            rows += listOf(c)
        } else {
            buffer += c
            if (buffer.size == columns) flush()
        }
    }
    flush()
    return rows
}

/** 팩 단계 문장 → 첫 문장(굵게) + 나머지(보조). 문장 경계(". ")가 없으면 전체를 한 줄로 — 값은 팩 원문 그대로 */
internal fun stepOf(text: String): Step {
    val cut = text.indexOf(". ")
    if (cut <= 0) return Step(text)
    val rest = text.substring(cut + 2).trim()
    return if (rest.isEmpty()) Step(text) else Step(text.substring(0, cut + 1), detail = rest)
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
    val fallback = stringResource(R.string.source_official_fallback)
    val packSources = remember(pack) { pack?.sources.orEmpty().associate { it.id to it.name } }
    fun ref(id: String, verified: String) = SourceRef(resolveSourceName(id, packSources, fallback), displayDate(verified))

    val listState = rememberLazyListState()
    val keyIndex = rememberKeyIndex()
    val scope = rememberCoroutineScope()
    val columns = rememberGridColumns()
    // 큰 글자 배치: 긴 번호(대사관·영사콜센터) 타일을 카드 안 좁은 칸 대신 카드 밖 폭 전체로 꺼낸다 — 번호가 한 줄에 들어가게 (재검토 R6)
    val stacked = isStackedLayout()

    val offlineBadge: @Composable () -> Unit = {
        StatusChip(
            stringResource(R.string.help_offline_badge),
            container = Tokens.SuccessBg,
            content = Tokens.SuccessText,
            icon = Icons.Outlined.OfflinePin,
        )
    }

    AppScreen(
        title = stringResource(R.string.help_title),
        speech = stringResource(R.string.help_speech),
        // 2열 폭이면 제목 옆, 쉬운 모드·큰 글자(1열)면 제목 아래 줄 — 옆에 두면 칩이 폭을 가져가 `도/움`처럼 제목이 쪼개진다
        headerActions = { if (columns > 1) offlineBadge() },
        state = listState,
        keyIndex = keyIndex,
    ) {
        if (columns == 1) {
            item(key = "offline-badge") { offlineBadge() }
        }
        if (ui.countries.size > 1) {
            item(key = "countries") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.help_choose_country),
                        style = MaterialTheme.typography.titleSmall,
                        color = Tokens.InkSecondary,
                    )
                    FlowRow(
                        Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ui.countries.forEach { c ->
                            SelectChip(
                                selected = c.code == pack?.country,
                                onClick = { onSelectCountry(c.code) },
                                label = c.nameKo,
                                avatar = { CountryAvatar(c.code) },
                            )
                        }
                    }
                }
            }
        }
        if (pack == null) {
            item(key = "no-country") {
                EmptyState(icon = Icons.Outlined.SupportAgent, title = stringResource(R.string.help_no_country), body = null)
            }
        } else {
            // ③ 대표 긴급 번호 (보통 관광경찰) — 급할 때 번호가 첫 화면에 보이게 (D6). 출처를 바로 아래에
            pack.emergency.firstOrNull()?.let { top ->
                item(key = "emergency-top") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EmergencyCallTile(
                            label = top.labelKo,
                            number = top.number,
                            icon = IconKeys.emergency(top.id),
                            onCall = { onCall(top.number) },
                            note = top.noteKo,
                            large = true,
                        )
                        SourceFooter(ref(top.source, top.lastVerified))
                        if (pack.emergency.size > 1) {
                            QuietButton(
                                stringResource(R.string.help_more_numbers),
                                onClick = { scope.launch { listState.scrollToKey(keyIndex, "emergency") } },
                                icon = Icons.Outlined.ArrowDownward,
                                // 글자 버튼 안쪽 여백(12dp)만큼 당겨 화살표가 타일·출처 줄과 같은 왼쪽 선에 서게
                                modifier = Modifier.offset(x = -TextButtonInset),
                            )
                        }
                    }
                }
            }
            // ④ 고른 문장 큰 카드 (현지인에게 보여 주기)
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
            // ⑤ 자주 쓰는 말 — 팩 문구는 길이를 앱이 정할 수 없어 항상 1열 (D4)
            if (pack.phrases.isNotEmpty()) {
                sectionGap("gap-phrases")
                item(key = "phrases-title") {
                    SectionHeader(stringResource(R.string.help_phrases_title), icon = Icons.Outlined.Translate)
                }
                item(key = "phrases") {
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pack.phrases.forEach { p ->
                            PhraseTile(
                                phrase = p,
                                selected = p == selectedPhrase,
                                onClick = {
                                    selectedPhrase = p
                                    scope.launch { listState.scrollToKey(keyIndex, "phrase-card") }
                                },
                            )
                        }
                    }
                }
            }
            // ⑥ 나머지 긴급 번호 (짧은 번호는 2열, 긴 번호는 폭 전체)
            val rest = pack.emergency.drop(1)
            if (rest.isNotEmpty()) {
                sectionGap("gap-emergency")
                item(key = "emergency") {
                    SectionHeader(stringResource(R.string.help_emergency_title), icon = Icons.Outlined.Sos, tone = BadgeTone.Help)
                }
                emergencyRows(rest, columns).forEachIndexed { i, row ->
                    item(key = "emergency-row-$i") {
                        TileGrid(row, columns = row.size) { c, cell ->
                            EmergencyCallTile(
                                label = c.labelKo,
                                number = c.number,
                                icon = IconKeys.emergency(c.id),
                                onCall = { onCall(c.number) },
                                modifier = cell,
                                note = c.noteKo,
                            )
                        }
                    }
                }
                item(key = "emergency-source") {
                    SourceList(rest.map { ref(it.source, it.lastVerified) }.distinct())
                }
            }
            // ⑦ 대사관
            pack.embassy?.let { emb ->
                sectionGap("gap-embassy")
                val embassySources = listOf(ref(emb.source, emb.lastVerified))
                val embassyTiles: @Composable () -> Unit = {
                    EmergencyCallTile(
                        label = emb.nameKo,
                        number = emb.phone,
                        icon = Icons.Outlined.AccountBalance,
                        onCall = { onCall(emb.phone) },
                    )
                    emb.emergencyPhone?.let { phone ->
                        EmergencyCallTile(
                            label = stringResource(R.string.help_embassy_after_hours),
                            number = phone,
                            icon = Icons.Outlined.Sos,
                            onCall = { onCall(phone) },
                        )
                    }
                }
                item(key = "embassy") {
                    CardNewsCard(
                        title = stringResource(R.string.help_embassy),
                        icon = Icons.Outlined.AccountBalance,
                        sources = if (stacked) emptyList() else embassySources,
                    ) {
                        IconBullet(emb.address, Icons.Outlined.Place)
                        if (!stacked) embassyTiles()
                    }
                }
                if (stacked) {
                    // 카드 밖 폭 전체 타일 + 그 아래 출처 (출처는 늘 번호 바로 아래에 보인다)
                    item(key = "embassy-tiles") {
                        Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                            embassyTiles()
                            SourceList(embassySources)
                        }
                    }
                }
            }
            // ⑧ 이럴 땐 이렇게 (여권 분실 등)
            if (pack.procedures.isNotEmpty()) {
                sectionGap("gap-procedures")
                item(key = "procedures-title") {
                    SectionHeader(stringResource(R.string.help_procedures_title), icon = Icons.Outlined.TipsAndUpdates)
                }
                pack.procedures.forEach { proc ->
                    item(key = "proc-${proc.id}") {
                        CardNewsCard(
                            title = proc.titleKo,
                            icon = Icons.Outlined.ReportProblem,
                            tone = BadgeTone.Caution,
                            sources = listOf(ref(proc.source, proc.lastVerified)),
                        ) {
                            StepList(proc.stepsKo.map { stepOf(it) })
                        }
                    }
                }
            }
        }
        // ⑨ 어느 나라에서나 (영사콜센터) — 출처는 항목의 출처 ID를 이름으로 푼 값 (commonSourceName 수정, 4.5)
        if (ui.common.isNotEmpty()) {
            sectionGap("gap-common")
            item(key = "common") {
                val refs = ui.common.mapIndexed { i, c ->
                    val named = ui.indexSources[c.source]?.takeIf { it.isNotBlank() }
                        ?: ui.commonSourceName.takeIf { i == 0 }
                        ?: fallback
                    SourceRef(named, displayDate(c.lastVerified))
                }.distinct()
                val commonTiles: @Composable () -> Unit = {
                    ui.common.forEach { c ->
                        EmergencyCallTile(
                            label = c.labelKo,
                            number = c.number,
                            icon = IconKeys.emergency(c.id),
                            onCall = { onCall(c.number) },
                            note = c.noteKo,
                        )
                    }
                }
                if (stacked) {
                    // 큰 글자: 머리 카드 → 카드 밖 폭 전체 타일 → 출처 (대사관과 같은 배치, 재검토 R6)
                    Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                        CardNewsCard(title = stringResource(R.string.help_common_title), icon = Icons.Outlined.SupportAgent)
                        commonTiles()
                        SourceList(refs)
                    }
                } else {
                    CardNewsCard(
                        title = stringResource(R.string.help_common_title),
                        icon = Icons.Outlined.SupportAgent,
                        sources = refs,
                    ) { commonTiles() }
                }
            }
        }
    }

    if (fullScreen) {
        selectedPhrase?.let { phrase -> PhraseFullScreen(phrase, onClose = { fullScreen = false }) }
    }
}

/** TextButton(QuietButton)의 가로 안쪽 여백 — 글자 버튼을 다른 요소와 같은 왼쪽 선에 맞출 때 당기는 만큼 */
internal val TextButtonInset = 12.dp

/** 나라 칩 앞 24dp 원형 사진 (장식) */
@Composable
private fun CountryAvatar(code: String) {
    val thumb = rememberThumbnail(Photos.country(code), 24.dp)
    if (thumb != null) {
        Image(
            thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(24.dp).clip(CircleShape).background(Tokens.SurfaceSunken),
        )
    }
}

/**
 * 고른 문장 큰 카드 (Navy, onDark 내용 세트만 — D18): 언어 이름 → 현지어(localMedium, 행간 1.5배) → 로마자 →
 * 한국어 → 영어 → 화면 크게·소리로 들려주기. 문장이 바뀌면 TalkBack이 알린다(liveRegion).
 */
@Composable
private fun PhraseCard(
    phrase: Phrase,
    languageName: String,
    ttsAvailable: Boolean,
    onSpeak: () -> Unit,
    onFullScreen: () -> Unit,
) {
    val dimens = LocalDimens.current
    val extras = LocalTypeExtras.current
    val shape = MaterialTheme.shapes.large
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Navy, contentColor = OnDark.content),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconBadge(Icons.Outlined.Translate, tone = BadgeTone.OnDark, size = dimens.iconBadgeSmall)
                    if (languageName.isNotEmpty()) {
                        Text(languageName, style = MaterialTheme.typography.labelMedium, color = OnDark.eyebrow)
                    }
                }
                if (!phrase.reviewed) StatusTag(icon = Icons.Outlined.Update, kind = StatusKind.Caution, text = stringResource(R.string.help_unreviewed))
            }
            Text(phrase.local, style = extras.localMedium, color = OnDark.content)
            phrase.romanized?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = OnDark.secondary) }
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KoText(phrase.ko, MaterialTheme.typography.titleMedium, color = OnDark.content)
                Text(phrase.en, style = MaterialTheme.typography.bodyMedium, color = OnDark.secondary)
            }
            FlowRow(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryButton(
                    stringResource(R.string.help_full_screen),
                    onClick = onFullScreen,
                    icon = Icons.Outlined.Fullscreen,
                    fillWidth = false,
                    onDark = true,
                )
                if (ttsAvailable) {
                    SecondaryButton(
                        stringResource(R.string.help_play_sound),
                        onClick = onSpeak,
                        icon = Icons.AutoMirrored.Outlined.VolumeUp,
                        fillWidth = false,
                        onDark = true,
                    )
                }
            }
            if (!ttsAvailable && languageName.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = OnDark.secondary,
                        modifier = Modifier.padding(top = 1.dp).size(textIconSize(dimens.iconSmall)),
                    )
                    KoText(stringResource(R.string.help_no_tts, languageName), MaterialTheme.typography.bodySmall, color = OnDark.secondary)
                }
            }
        }
    }
}

/**
 * 자주 쓰는 말 한 줄 (항상 1열) = 공용 SelectableCard(재검토 R2 규칙 ② — 선택 AccentSoft + 2dp Accent + CheckCircle,
 * 비선택 흰 바탕 + 1dp LineStrong + 빈 원). 한 개만 고르는 선택이라 Role.RadioButton (부모 selectableGroup).
 * 글자는 한국어 문장 하나뿐(테스트가 단독 Text로 찾는다).
 */
@Composable
private fun PhraseTile(phrase: Phrase, selected: Boolean, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    SelectableCard(
        selected = selected,
        onClick = onClick,
        minHeight = dimens.tileRowMinHeight,
        leading = {
            IconBadge(
                IconKeys.phrase(phrase.id) ?: Icons.Outlined.Translate,
                size = dimens.iconBadgeSmall,
                // 고른 카드는 바탕이 AccentSoft라 배지를 흰 바탕으로 띄운다
                containerColor = selectionBadgeContainer(selected),
            )
        },
    ) {
        KoText(
            phrase.ko,
            style = MaterialTheme.typography.labelLarge,
            color = Tokens.Ink,
            // 짧은 문장 라벨: 한 음절 낱말(`가 주세요`의 `가`)이 줄 끝에 홀로 남지 않게
            glueShort = true,
        )
    }
}

/** 문장 전체 화면 내용: 현지어 localLarge(행간 1.5배) + 영어·한국어 */
@Composable
internal fun PhraseFullScreenBody(phrase: Phrase, onClose: () -> Unit) {
    val extras = LocalTypeExtras.current
    ShowLocalBody(onClose) {
        Text(phrase.local, style = extras.localLarge, textAlign = TextAlign.Center, color = Tokens.Ink)
        Text(phrase.en, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = Tokens.InkSecondary)
        KoText(phrase.ko, MaterialTheme.typography.titleLarge, color = Tokens.InkSecondary, textAlign = TextAlign.Center)
    }
}

/** 현지인에게 보여 주는 전체 화면 */
@Composable
private fun PhraseFullScreen(phrase: Phrase, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PhraseFullScreenBody(phrase, onClose)
    }
}
