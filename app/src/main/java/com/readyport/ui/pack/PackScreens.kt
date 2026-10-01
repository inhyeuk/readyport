package com.readyport.ui.pack

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
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
import com.readyport.ui.components.EmptyState
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.SourceFooter
import com.readyport.ui.components.SourceList
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.Step
import com.readyport.ui.components.StepList
import com.readyport.ui.components.TileGrid
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.emergencyColors
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.rememberKeyIndex
import com.readyport.ui.components.rememberThumbnail
import com.readyport.ui.components.resolveSourceName
import com.readyport.ui.components.scrollToKey
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.startBar
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.ReadyPortLineBreak
import com.readyport.ui.theme.Tokens
import kotlinx.coroutines.launch

/** 화면 표기: 2026-09-28 → 2026.09.28 (PRD 5장 공통) */
@Deprecated(
    "components.displayDate 사용 (DESIGN_SPEC 4.0 이동 규칙)",
    ReplaceWith("displayDate(iso)", "com.readyport.ui.components.displayDate"),
)
fun displayDate(iso: String): String = com.readyport.ui.components.displayDate(iso)

// ======================= D 묶음 글자 도우미 (2단계 통합 때 components로 옮길 후보) =======================

private const val WORD_JOINER = '\u2060'
/** 출처 날짜 앞 ZERO WIDTH SPACE ([sourceDate]) */
internal const val SOURCE_DATE_BREAK = "\u200B"

private fun isHangul(c: Char): Boolean = c in '\uAC00'..'\uD7A3' || c in '\u1100'..'\u11FF' || c in '\u3130'..'\u318F'

/**
 * 어절 안 글자 사이에 WORD JOINER(U+2060)를 넣어 공백에서만 줄이 바뀌게 한다(keep-all).
 * 한글이 낀 글자 사이에만 넣는다 — 라틴 낱말·숫자·현지어(태국어)는 원래 줄바꿈 규칙 그대로.
 * 한 어절이 줄보다 길면 렌더러가 그 안에서 끊는다(글자가 잘리지는 않는다).
 */
internal fun joinWords(text: String): String {
    if (text.none(::isHangul)) return text
    val out = StringBuilder(text.length * 2)
    text.forEachIndexed { i, c ->
        if (i > 0) {
            val p = text[i - 1]
            if (!p.isWhitespace() && !c.isWhitespace() && p != WORD_JOINER && c != WORD_JOINER && (isHangul(p) || isHangul(c)) &&
                c !in BREAK_BEFORE && p !in BREAK_AFTER
            ) {
                out.append(WORD_JOINER)
            }
        }
        out.append(c)
    }
    return out.toString()
}

/** 어절 안이라도 줄을 바꿔도 되는 자리: 여는 괄호 앞(`800달러 / (과세가격`), 가운뎃점·빗금 뒤(`고기·햄· / 소시지`) */
private const val BREAK_BEFORE = "([{"
private const val BREAK_AFTER = "\u00B7/"

/**
 * API 33 미만(테스트 폰 S10, Android 12)에서는 어절 단위 줄바꿈(WordBreak.Phrase)이 없어 한국어가 음절 사이 어디서나 끊긴다
 * (`주세 / 요`, `받 / 기`). 그곳에서만 [joinWords]로 어절을 묶는다. 33 이상은 테마의 LineBreak가 맡으므로 글자 그대로.
 * 보이지 않는 문자는 TalkBack이 읽지 않는다(source_footer도 U+2060을 쓴다). 내 Text에는 [KeepAllText]로 원문을 의미 글자로 둔다.
 */
internal fun keepAll(text: String): String = if (ReadyPortLineBreak.phraseSupported) text else joinWords(text)

/** [keepAll]로 그리되 의미 글자(TalkBack·테스트)는 원문 그대로 */
@Composable
internal fun KeepAllText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val shown = keepAll(text)
    Text(
        shown,
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = if (shown == text) modifier else modifier.semantics { this.text = AnnotatedString(text) },
    )
}

/**
 * 현지어(태국어 등)를 표시 역할이 아닌 크기로 보일 때: 행간 1.5배 + 줄 높이 가운데·자르지 않음 —
 * 위아래로 쌓이는 부호(ที่นี่)가 겹치거나 잘리지 않게 (DESIGN_SPEC 3.2). 크기는 [base] 역할 그대로(고정 sp 없음).
 */
internal fun localText(base: TextStyle): TextStyle = base.copy(
    lineHeight = base.fontSize * 1.5f,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/**
 * 출처 줄의 날짜. `source_footer`는 `확인`과 날짜 사이도 NBSP라 `최종 확인 2026.09.29`가 한 덩어리가 되는데,
 * 쉬운 모드·200%에서는 그 덩어리가 줄보다 넓어 날짜 한가운데서 끊긴다(`2026.09.2 / 9`).
 * 날짜 앞에 ZERO WIDTH SPACE를 두면(UAX#14 LB8) 날짜가 통째로 다음 줄로 넘어간다. 날짜 글자는 그대로.
 * 2단계 통합: source_footer의 두 번째 NBSP를 일반 공백으로 바꾸면 이 함수는 displayDate로 되돌린다.
 */
internal fun sourceDate(iso: String): String = SOURCE_DATE_BREAK + com.readyport.ui.components.displayDate(iso)

/** 전화번호를 '-' 바로 뒤에서 나눈 묶음 (`+66-81-914-5803` → `+66-`, `81-`, `914-`, `5803`). 이어 붙이면 원래 번호 */
internal fun phoneGroups(number: String): List<String> {
    val groups = mutableListOf<String>()
    val cur = StringBuilder()
    number.forEach { c ->
        cur.append(c)
        if (c == '-' || c == ' ') {
            groups += cur.toString()
            cur.clear()
        }
    }
    if (cur.isNotEmpty()) groups += cur.toString()
    return groups
}

/** 번호 묶음 노드의 testTag (200% 줄바꿈 검사용 — 의미 글자는 번호 전체 한 노드) */
internal const val PHONE_GROUP_TAG = "phone-group"

/**
 * 긴급 전화번호 (6-20 번호 길이 규칙 + 200% 보강). `+66-81-914-5803`은 UAX#14상 한 낱말이라, 줄보다 넓으면
 * 숫자 한가운데서 끊긴다(`+66-81-914-58 / 03` — 잘못 읽고 잘못 걸 수 있다). 그래서 '-'로 나뉜 번호는
 * 묶음([phoneGroups])을 간격 없이 FlowRow에 놓는다: 한 줄에 들어가면 한 Text와 똑같이 보이고, 넘치면 '-' 뒤에서만 줄을 바꾼다.
 * 번호 글자에는 보이지 않는 문자를 넣지 않고, 의미 글자는 번호 전체 한 노드(테스트·TalkBack이 그대로 찾는다).
 * (BoxWithConstraints로 재지 않는다 — TileGrid 행이 IntrinsicSize.Min으로 높이를 맞춰 SubcomposeLayout을 쓸 수 없다)
 */
@Composable
internal fun PhoneNumberText(number: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val groups = phoneGroups(number)
    if (groups.size <= 1) {
        Text(number, style = style, color = color, modifier = modifier)
    } else {
        FlowRow(modifier.semantics { text = AnnotatedString(number) }) {
            groups.forEach { g ->
                Text(g, style = style, color = color, modifier = Modifier.clearAndSetSemantics { testTag = PHONE_GROUP_TAG })
            }
        }
    }
}

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

/** 번호가 이 글자 수를 넘으면 statSmall (6-20) */
private const val STAT_NUMBER_MAX = 8

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
    fun ref(id: String, verified: String) = SourceRef(keepAll(resolveSourceName(id, packSources, fallback)), sourceDate(verified))

    val listState = rememberLazyListState()
    val keyIndex = rememberKeyIndex()
    val scope = rememberCoroutineScope()
    val columns = rememberGridColumns()

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
                        HelpCallTile(
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
                                keepAll(stringResource(R.string.help_more_numbers)),
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
                            HelpCallTile(
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
                item(key = "embassy") {
                    CardNewsCard(
                        title = stringResource(R.string.help_embassy),
                        icon = Icons.Outlined.AccountBalance,
                        sources = listOf(ref(emb.source, emb.lastVerified)),
                    ) {
                        IconBullet(keepAll(emb.address), Icons.Outlined.Place)
                        HelpCallTile(
                            label = emb.nameKo,
                            number = emb.phone,
                            icon = Icons.Outlined.AccountBalance,
                            onCall = { onCall(emb.phone) },
                        )
                        emb.emergencyPhone?.let { phone ->
                            HelpCallTile(
                                label = stringResource(R.string.help_embassy_after_hours),
                                number = phone,
                                icon = Icons.Outlined.Sos,
                                onCall = { onCall(phone) },
                            )
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
                            title = keepAll(proc.titleKo),
                            icon = Icons.Outlined.ReportProblem,
                            tone = BadgeTone.Caution,
                            sources = listOf(ref(proc.source, proc.lastVerified)),
                        ) {
                            StepList(proc.stepsKo.map { s -> stepOf(s).let { it.copy(text = keepAll(it.text), detail = it.detail?.let(::keepAll)) } })
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
                    SourceRef(keepAll(named), sourceDate(c.lastVerified))
                }.distinct()
                CardNewsCard(
                    title = stringResource(R.string.help_common_title),
                    icon = Icons.Outlined.SupportAgent,
                    sources = refs,
                ) {
                    ui.common.forEach { c ->
                        HelpCallTile(
                            label = c.labelKo,
                            number = c.number,
                            icon = IconKeys.emergency(c.id),
                            onCall = { onCall(c.number) },
                            note = c.noteKo,
                        )
                    }
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

/**
 * 도움 탭의 긴급 번호 타일. 0단계 components.EmergencyCallTile과 같은 모양·색·TalkBack 설명에 두 가지를 고쳤다
 * (공용 부품은 1단계에서 고칠 수 없어 여기 둔다 — 2단계 통합 때 EmergencyCallTile로 합친다):
 * ① 번호: [PhoneNumberText] — 200%·카드 안 좁은 폭에서도 숫자 사이에서 끊기지 않는다.
 * ② 연한 Help 바탕 타일의 배지: 배지 바탕(HelpSoft)이 타일 바탕과 같아 사라지고 아이콘만 떠 보였다 → 흰 바탕으로 띄워
 *    배지·번호·라벨이 같은 왼쪽 선에 선다(ChoiceCard·상태 카드와 같은 처리). large(Navy)는 OnDark 배지 그대로.
 * 라벨·메모는 API 33 미만에서 어절 단위로 줄을 바꾼다([keepAll]).
 */
@Composable
private fun HelpCallTile(
    label: String,
    number: String,
    icon: ImageVector,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    large: Boolean = false,
) {
    val dimens = LocalDimens.current
    val extras = LocalTypeExtras.current
    val c = emergencyColors(large)
    val shape = MaterialTheme.shapes.medium
    val description = stringResource(R.string.help_call, label) + " " + number + (note?.let { ", $it" } ?: "")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.tileMinHeight)
            .clip(shape)
            .background(c.container)
            .then(if (c.bar != null) Modifier.startBar(c.bar) else Modifier)
            .clickable(role = Role.Button, onClick = onCall)
            .semantics { contentDescription = description },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon,
                    tone = c.badge,
                    size = dimens.iconBadgeSmall,
                    containerColor = if (large) c.badge.container else Tokens.Surface,
                )
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.Call, contentDescription = null, tint = c.call, modifier = Modifier.size(dimens.icon))
            }
            PhoneNumberText(number, if (number.length > STAT_NUMBER_MAX) extras.statSmall else extras.stat, c.number, Modifier.fillMaxWidth())
            KeepAllText(label, MaterialTheme.typography.bodyMedium, c.label)
            if (note != null) {
                if (large) {
                    StatusTag(keepAll(note), StatusKind.Info)
                } else {
                    KeepAllText(note, MaterialTheme.typography.bodySmall, c.note)
                }
            }
        }
    }
}

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
 * `원어민 검수 전` 태그 (6-20 ④): Caution 톤(자체 바탕이 있어 Navy 위 허용)에 Update 아이콘 —
 * 경고 삼각형은 긴급 문장 옆에서 '위험·금지'로 읽혀서, 아직 검수 전이라는 뜻의 Update를 쓴다. 모양은 StatusTag 그대로.
 */
@Composable
private fun UnreviewedTag(text: String) {
    val tone = StatusKind.Caution.tone
    Surface(
        color = tone.container,
        contentColor = tone.content,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.heightIn(min = 28.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Update, contentDescription = null, modifier = Modifier.size(LocalDimens.current.iconSmall))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
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
                if (!phrase.reviewed) UnreviewedTag(stringResource(R.string.help_unreviewed))
            }
            Text(phrase.local, style = extras.localMedium, color = OnDark.content)
            phrase.romanized?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = OnDark.secondary) }
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KeepAllText(phrase.ko, MaterialTheme.typography.titleMedium, OnDark.content)
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
                        keepAll(stringResource(R.string.help_play_sound)),
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
                        modifier = Modifier.padding(top = 1.dp).size(dimens.iconSmall),
                    )
                    KeepAllText(stringResource(R.string.help_no_tts, languageName), MaterialTheme.typography.bodySmall, OnDark.secondary)
                }
            }
        }
    }
}

/**
 * 자주 쓰는 말 한 줄 (항상 1열). 고르면 AccentSoft + 2dp Accent 테두리 + CheckCircle.
 * 한 개만 고르는 선택이라 Role.RadioButton (부모 selectableGroup). 글자는 한국어 문장 하나뿐(테스트가 단독 Text로 찾는다).
 * 눌림 물결은 타일 모양(16dp)으로 자른다 — selectable 앞에 clip.
 */
@Composable
private fun PhraseTile(phrase: Phrase, selected: Boolean, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.medium
    Surface(
        color = if (selected) Tokens.AccentSoft else Tokens.Surface,
        contentColor = Tokens.Ink,
        shape = shape,
        border = if (selected) BorderStroke(2.dp, Tokens.Accent) else null,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier else Modifier.cardShadow(shape))
            .heightIn(min = dimens.tileRowMinHeight)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconBadge(
                IconKeys.phrase(phrase.id) ?: Icons.Outlined.Translate,
                size = dimens.iconBadgeSmall,
                // 고른 타일은 바탕이 AccentSoft라 배지를 흰 바탕으로 띄운다
                containerColor = if (selected) Tokens.Surface else BadgeTone.Accent.container,
            )
            KeepAllText(
                phrase.ko,
                style = MaterialTheme.typography.labelLarge,
                color = Tokens.Ink,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(dimens.icon))
            }
        }
    }
}

/**
 * 현지인에게 보여 주는 전체 화면 틀 (18·19·20 공통): 글은 스크롤 영역에, 닫기 버튼은 아래에 고정 —
 * 여러 줄 태국어가 localLarge(200%면 약 112sp)로 화면을 넘겨도 닫기가 항상 보인다.
 */
@Composable
internal fun ShowLocalBody(onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxSize().background(Tokens.Surface)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
        Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)) {
            PrimaryButton(stringResource(R.string.help_close), onClick = onClose, icon = Icons.Outlined.Close)
        }
    }
}

/** 문장 전체 화면 내용: 현지어 localLarge(행간 1.5배) + 영어·한국어 */
@Composable
internal fun PhraseFullScreenBody(phrase: Phrase, onClose: () -> Unit) {
    val extras = LocalTypeExtras.current
    ShowLocalBody(onClose) {
        Text(phrase.local, style = extras.localLarge, textAlign = TextAlign.Center, color = Tokens.Ink)
        Text(phrase.en, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = Tokens.InkSecondary)
        KeepAllText(phrase.ko, MaterialTheme.typography.titleLarge, Tokens.InkSecondary, textAlign = TextAlign.Center)
    }
}

/** 현지인에게 보여 주는 전체 화면 */
@Composable
private fun PhraseFullScreen(phrase: Phrase, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PhraseFullScreenBody(phrase, onClose)
    }
}
