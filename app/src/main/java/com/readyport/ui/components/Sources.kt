package com.readyport.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 출처 (DESIGN_SPEC 4.5) =======================

/** 출처 한 줄. [verified]는 displayDate를 거친 YYYY.MM.DD. 내부 ID는 절대 넣지 않는다 */
@Immutable
data class SourceRef(val name: String, val verified: String)

/** "2026-09-28" → "2026.09.28" (출처 줄 날짜 표기) */
fun displayDate(iso: String): String = iso.replace('-', '.')

/**
 * 출처 ID → 화면에 보일 이름. 이름을 못 찾으면 [fallback](`source_official_fallback` = 공식 안내).
 * 절대 ID를 그대로 돌려주지 않는다 (DESIGN_SPEC 1.2 #1).
 */
fun resolveSourceName(id: String, names: Map<String, String>, fallback: String): String =
    names[id]?.takeIf { it.isNotBlank() } ?: fallback

/** 출처 이름에서 기관과 세부를 가르는 구분 (`태국관광청 · 춤폰 쇼핑`) */
private const val AGENCY_PART = " · "

/**
 * 출처 이름의 기관 부분: ` · ` 앞(`태국관광청 · 춤폰 쇼핑` → `태국관광청`), 없으면 이름 전체.
 * 이름을 바꾸거나 지어내지 않는다 — 묶을 때 같은 앞부분을 한 번만 쓸 뿐이다.
 */
internal fun agencyOf(name: String): String = name.substringBefore(AGENCY_PART)

/**
 * 출처 여러 개를 **기관별로** 묶는다 (재검토 R9). 같은 기관(이름의 ` · ` 앞부분이 같거나, ` · `가 없는 이름의 첫 낱말이 같음)의
 * 세부는 `기관 · 세부1, 세부2`(또는 `기관 세부1, 세부2`)로 한 줄에, 같은 이름은 한 번만. 기관 안에서 날짜가 다르면 날짜별로 나눈다.
 * 이름은 하나도 빠뜨리지 않는다(출처 규칙 — 정책 값마다 출처가 보인다).
 * 출처 이름 자체에 ` · `가 들어 있으므로 여러 기관을 ` · `로 잇지 않는다.
 */
fun sourceLines(refs: List<SourceRef>): List<SourceRef> {
    val distinct = refs.distinct()
    if (distinct.size <= 1) return distinct
    val names = distinct.map { it.name }
    // ` · `가 있는 이름의 앞부분은 기관. ` · `가 없는 이름은 그 이름 전체가 다른 이름의 기관이면 그 기관,
    // 아니면 첫 낱말이 다른 이름의 기관·첫 낱말과 같을 때만 첫 낱말로 묶는다(`관세청 …`·`농림축산검역본부 …`).
    val explicit = names.filter { AGENCY_PART in it }.map(::agencyOf).toSet()
    val firstWords = names.filter { AGENCY_PART !in it }.map { it.substringBefore(' ') }
    fun keyOf(name: String): Pair<String, String> {
        if (AGENCY_PART in name) return agencyOf(name) to name.substringAfter(AGENCY_PART)
        if (name in explicit) return name to ""
        val first = name.substringBefore(' ')
        val shared = first != name && (first in explicit || firstWords.count { it == first } > 1)
        return if (shared) first to name.substringAfter(' ') else name to ""
    }
    data class Group(val agency: String, val date: String, val details: LinkedHashSet<String>, var joiner: String)
    val groups = LinkedHashMap<Pair<String, String>, Group>()
    distinct.forEach { ref ->
        val (agency, detail) = keyOf(ref.name)
        val g = groups.getOrPut(agency to ref.verified) { Group(agency, ref.verified, LinkedHashSet(), " ") }
        if (AGENCY_PART in ref.name) g.joiner = AGENCY_PART
        if (detail.isNotEmpty()) g.details += detail
    }
    return groups.values.map { g ->
        val name = if (g.details.isEmpty()) g.agency else g.agency + g.joiner + g.details.joinToString(", ")
        SourceRef(name, g.date)
    }
}

/**
 * 여러 출처 줄을 [SourceFooter]에 넘길 묶음으로: 같은 날짜의 기관 줄은 **한 덩어리**(이름 줄을 줄바꿈으로 잇고 날짜는 끝에 한 번 —
 * `출처 기관1 …⏎기관2 …⏎기관3 … · 최종 확인 2026.09.29`, 형식은 source_footer 그대로). 날짜가 다르면 덩어리를 나눈다(처음 나온 순서).
 */
fun sourceBlocks(refs: List<SourceRef>): List<SourceRef> =
    sourceLines(refs).groupBy { it.verified }.map { (date, lines) -> SourceRef(lines.joinToString("\n") { it.name }, date) }

private const val SOURCE_ICON = "source-icon"

/**
 * 정책·정보 카드 하단 "출처 {이름} · 최종 확인 {날짜}" (PRD 5장 공통).
 * 글자는 한 Text 노드, 의미 글자는 형식 그대로(`OfflinePackTest`가 getString(source_footer, …)로 찾는다).
 * 줄바꿈(재검토 R9): `source_footer`는 `최종 확인 {날짜}`를 한 덩어리로 묶는다(NBSP·WORD JOINER) — 날짜만 다음 줄로 넘어가
 * 홀로 남지 않는다. 그 덩어리가 한 줄보다 길면(쉬운 모드 200%의 좁은 줄) 그때만 `확인`과 날짜 사이에서 줄을 바꾼다 —
 * 날짜는 언제나 **통째로** 한 줄(`2026.09.2 / 8` 없음, 숫자·점은 UAX#14상 끊기지 않는다). 이름은 어절 단위로만 줄을 바꾼다([koDisplay]).
 * 큰 글자 배치(Stacked)에서는 아이콘을 글 첫 줄 앞에 넣어 글에 카드 폭 전체를 준다.
 * [onColor]: 어두운 채움(Accent·Navy) 위면 White85, 아니면 InkTertiary.
 */
@Composable
fun SourceFooter(ref: SourceRef, modifier: Modifier = Modifier, onColor: Boolean = false) {
    val color = if (onColor) Tokens.White85 else Tokens.InkTertiary
    val style = MaterialTheme.typography.bodySmall
    val full = stringResource(R.string.source_footer, ref.name, ref.verified)
    // 덩어리가 한 줄에 안 들어간 폭(px). 그 폭에서는 `확인`과 날짜 사이를 보통 띄어쓰기로 풀어 날짜만 통째로 다음 줄에 둔다
    var looseAtWidth by remember(full) { mutableIntStateOf(-1) }
    val glued = remember(full) { koDisplay(full) }
    val loose = remember(full) { looseDateBreak(glued, ref.verified) }
    val onLayout: (TextLayoutResult) -> Unit = { r ->
        val width = r.layoutInput.constraints.maxWidth
        val text = r.layoutInput.text.text
        if (text == glued || text.endsWith(glued)) {
            if (dateChunkBroken(r, glued, ref.verified)) looseAtWidth = width
        } else if (looseAtWidth != width) {
            // 폭이 바뀌었으면(회전 등) 다시 묶어 본다
            looseAtWidth = -1
        }
    }
    val shown = if (looseAtWidth >= 0) loose else glued
    if (isStackedLayout()) {
        Text(
            buildAnnotatedString {
                appendInlineContent(SOURCE_ICON, "[i]")
                append(' ')
                append(shown)
            },
            modifier = modifier.semantics { text = AnnotatedString(full) },
            style = style,
            color = color,
            onTextLayout = onLayout,
            inlineContent = mapOf(
                SOURCE_ICON to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                    Icon(IconKeys.source, contentDescription = null, tint = color, modifier = Modifier.fillMaxSize())
                },
            ),
        )
        return
    }
    val iconSize = textIconSize(LocalDimens.current.iconSmall, style)
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(
            IconKeys.source,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(top = firstLineIconOffset(style, iconSize)).size(iconSize),
        )
        Spacer(Modifier.width(6.dp))
        KoText(full, style, color = color, display = shown, onTextLayout = onLayout)
    }
}

/** `확인`과 날짜 사이 NBSP를 보통 띄어쓰기로 (날짜 덩어리가 한 줄보다 길 때만) */
internal fun looseDateBreak(display: String, date: String): String {
    val at = display.lastIndexOf(date)
    if (at <= 0 || display[at - 1] != KoreanBreak.NBSP) return display
    return display.substring(0, at - 1) + " " + display.substring(at)
}

/** `최종 확인 {날짜}` 덩어리(마지막 `최`부터 끝까지)가 여러 줄에 걸쳐 그려졌는지 */
internal fun dateChunkBroken(r: TextLayoutResult, display: String, date: String): Boolean {
    val text = r.layoutInput.text.text
    val offset = text.length - display.length
    val dateAt = display.lastIndexOf(date)
    if (dateAt < 0) return false
    val chunkStart = display.lastIndexOf('최', dateAt).takeIf { it >= 0 } ?: dateAt
    return r.getLineForOffset(offset + chunkStart) != r.getLineForOffset(offset + display.length - 1)
}

/** 기존 시그니처 (단계적 이행): 새 SourceFooter로 위임한다 */
@Composable
fun SourceFooter(source: String, verifiedDate: String) {
    SourceFooter(SourceRef(source, verifiedDate))
}

/**
 * 카드 맨 아래 출처 목록. 출처가 하나면 `source_footer(name, date)` 한 줄 그대로,
 * 여러 개면 기관별로 묶고([sourceLines]) 날짜가 모두 같으면 날짜를 끝에 한 번만([sourceBlocks]). 접힘 영역 안에 넣지 않는다.
 */
@Composable
fun SourceList(refs: List<SourceRef>, onColor: Boolean = false) {
    if (refs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sourceBlocks(refs).forEach { SourceFooter(it, onColor = onColor) }
    }
}

/** 공식 링크 한 줄: 줄 전체가 눌리고 오른쪽에 '새 창' 아이콘. 알약 버튼 대신 쓴다 */
@Composable
fun LinkRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val dimens = LocalDimens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minTouch()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val style = MaterialTheme.typography.labelLarge
        if (icon != null) Icon(icon, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(textIconSize(dimens.icon, style)))
        KoText(label, style, Modifier.weight(1f), color = Tokens.Accent)
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(textIconSize(20.dp, style)))
    }
}
