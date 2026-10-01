package com.readyport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.pack.OfficialLink
import com.readyport.pack.SourcedText
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/**
 * '귀국 전 확인 — 면세 한도·반입 금지 품목(관세청·검역본부)' (DESIGN_SPEC 4.15, 재검토 R14). 홈·나라 쇼핑·쇼핑 리스트·내 여행 귀국이 함께 쓴다.
 * 흰 카드뉴스 카드: 안내 한 줄 → 사실 행(주제 아이콘 + 팩 문장의 **첫 문장**, 숫자 토큰 굵게 — 채움 없는 행, 재검토 R1)
 * → `면세 한도·반입 금지 문장 전체 보기`(펼치면 같은 행이 팩 문장 전체로) → 출처(기관별 묶음, 접힘 밖) → 공식 링크 행.
 * 글은 **팩 원문 그대로** — 앱은 문장을 자르기만 하고(첫 문장 경계) 값을 만들거나 바꾸지 않는다(D11). TalkBack·테스트 글자는 원문.
 * 출처 이름을 못 찾으면 `공식 안내` — 내부 ID를 보이지 않는다.
 */
@Composable
fun ReturnCheckCard(
    links: List<OfficialLink>,
    facts: List<SourcedText>,
    sourceNames: Map<String, String>,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fallback = stringResource(R.string.source_official_fallback)
    val refs = facts.map { SourceRef(resolveSourceName(it.source, sourceNames, fallback), displayDate(it.lastVerified)) }
    var open by rememberSaveable { mutableStateOf(false) }
    val split = remember(facts) { facts.map { firstSentence(it.textKo) } }
    CardNewsCard(
        title = stringResource(R.string.shopping_return_title),
        icon = Icons.Outlined.Inventory2,
        modifier = modifier,
        tone = BadgeTone.Accent,
        style = NewsStyle.Surface,
    ) {
        KoText(stringResource(R.string.return_check_lead), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        if (facts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                facts.forEachIndexed { i, f ->
                    val (lead, rest) = split[i]
                    ReturnFactRow(f.source, if (open || rest == null) f.textKo.trim() else lead)
                }
            }
        }
        if (split.any { it.second != null }) {
            // 펼침 내용은 위 행이 맡는다(문장 전체로 바뀜). 이름에 무엇을 펼치는지 담는다(재검토 R18)
            ExpandableDetail(open = open, onOpenChange = { open = it }, label = stringResource(R.string.return_check_full)) {}
        }
        if (refs.isNotEmpty()) Column(Modifier.padding(top = 4.dp)) { SourceList(refs) }
        links.forEach { link -> LinkRow(link.labelKo, onClick = { onOpenLink(link.url) }) }
    }
}

/** 사실 한 행: 주제 아이콘(톤 색, 채움 없음) + 팩 문장(숫자 토큰 굵게). TalkBack·테스트 글자는 원문 */
@Composable
private fun ReturnFactRow(source: String, text: String) {
    val dimens = LocalDimens.current
    val (icon, tone) = IconKeys.returnFact(source)
    val style = MaterialTheme.typography.bodyLarge
    val size = textIconSize(if (dimens.easyMode) 24.dp else 20.dp, style)
    val shown = remember(text) { koDisplay(text) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tone.onLight, modifier = Modifier.padding(top = firstLineIconOffset(style, size)).size(size))
        Spacer(Modifier.width(12.dp))
        Text(
            emphasizeNumbers(text, shown),
            style = style,
            color = Tokens.Ink,
            modifier = Modifier.weight(1f).semantics { this.text = AnnotatedString(text) },
        )
    }
}

/** 문장 끝(. ! ?) 뒤 띄어쓰기 */
private val SentenceEnd = Regex("""(?<=[.!?])\s+""")

/** 팩 문장을 (첫 문장, 나머지)로 나눈다. 문장 경계가 없으면 (전체, null) — 글을 지어내지 않고 자르기만 한다 */
fun firstSentence(text: String): Pair<String, String?> {
    val t = text.trim()
    val m = SentenceEnd.find(t) ?: return t to null
    val rest = t.substring(m.range.last + 1).trim()
    return t.substring(0, m.range.first).trim() to rest.ifEmpty { null }
}

/** 숫자 + 단위 토큰 (`800달러`, `2L`, `200개비`, `100ml`, `30%`, `1,000만 원`, `19세`) — 굵게만 바꾼다(값을 만들지 않음) */
private val NumberToken = Regex("""\d+(?:[,.]\d+)*(?:\s?만\s?원|만|달러|개비|ml|mL|L|kg|g|Wh|%|원|세|개|일|박)?""")

/** [text]에서 숫자 토큰이 차지하는 자리 */
fun numberRanges(text: String): List<IntRange> = NumberToken.findAll(text).map { it.range }.toList()

/**
 * 보이는 글자([shown] = koDisplay 보정본 — 원문에 보이지 않는 줄바꿈 문자만 끼워 넣고 띄어쓰기를 NBSP로 바꾼 것)에
 * 원문 [text]의 숫자 토큰 자리를 굵게(Ink) 입힌다. 글자는 바꾸지 않는다.
 */
fun emphasizeNumbers(text: String, shown: String): AnnotatedString {
    val ranges = numberRanges(text)
    if (ranges.isEmpty()) return AnnotatedString(shown)
    // 보이는 글자 i → 원문 j (끼워 넣은 글자는 앞 원문 글자에 붙인다)
    val origin = IntArray(shown.length)
    var j = 0
    shown.forEachIndexed { i, c ->
        val same = j < text.length && (c == text[j] || (c == KoreanBreak.NBSP && text[j] == ' '))
        origin[i] = if (same) j++ else (j - 1).coerceAtLeast(0)
    }
    return buildAnnotatedString {
        append(shown)
        var start = -1
        for (i in shown.indices) {
            val bold = ranges.any { origin[i] in it }
            if (bold && start < 0) start = i
            if (!bold && start >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Tokens.Ink), start, i)
                start = -1
            }
        }
        if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Tokens.Ink), start, shown.length)
    }
}
