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
import androidx.compose.runtime.remember
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

/**
 * 출처 여러 개를 줄로 묶는다: 같은 날짜끼리 이름을 ", "로 잇고 날짜는 끝에 한 번만. 같은 이름은 한 번만.
 * 출처 이름 자체에 " · "가 들어 있으므로(`외교부 해외안전여행 · 태국`) 여러 출처를 " · "로 잇지 않는다.
 */
fun sourceLines(refs: List<SourceRef>): List<SourceRef> =
    refs.groupBy { it.verified }.map { (date, group) -> SourceRef(group.map { it.name }.distinct().joinToString(", "), date) }

private const val SOURCE_ICON = "source-icon"

/**
 * 정책·정보 카드 하단 "출처 {이름} · 최종 확인 {날짜}" (PRD 5장 공통).
 * 글자는 한 Text 노드, 의미 글자는 형식 그대로(`OfflinePackTest`가 getString(source_footer, …)로 찾는다).
 * 줄바꿈: `source_footer`는 `최종 확인`을 한 덩어리로 묶고 날짜 앞은 보통 띄어쓰기라, 줄이 모자라면 날짜가 **통째로** 다음 줄로 간다
 * (`2026.09.2 / 8` 방지 — 숫자·점은 UAX#14상 끊기지 않는다). 이름은 어절 단위로만 줄을 바꾼다([koDisplay]).
 * 큰 글자(130% 이상)에서는 아이콘을 글 첫 줄 앞에 넣어 글에 카드 폭 전체를 준다.
 * [onColor]: 어두운 채움(Accent·Navy) 위면 White85, 아니면 InkTertiary.
 */
@Composable
fun SourceFooter(ref: SourceRef, modifier: Modifier = Modifier, onColor: Boolean = false) {
    val color = if (onColor) Tokens.White85 else Tokens.InkTertiary
    val style = MaterialTheme.typography.bodySmall
    val full = stringResource(R.string.source_footer, ref.name, ref.verified)
    if (largeFont()) {
        val shown = remember(full) { koDisplay(full) }
        Text(
            buildAnnotatedString {
                appendInlineContent(SOURCE_ICON, "[i]")
                append(' ')
                append(shown)
            },
            modifier = modifier.semantics { text = AnnotatedString(full) },
            style = style,
            color = color,
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
        KoText(full, style, color = color)
    }
}

/** 기존 시그니처 (단계적 이행): 새 SourceFooter로 위임한다 */
@Composable
fun SourceFooter(source: String, verifiedDate: String) {
    SourceFooter(SourceRef(source, verifiedDate))
}

/**
 * 카드 맨 아래 출처 목록. 출처가 하나면 `source_footer(name, date)` 한 줄 그대로,
 * 여러 개면 같은 날짜끼리 한 줄(sourceLines). 접힘 영역 안에 넣지 않는다.
 */
@Composable
fun SourceList(refs: List<SourceRef>, onColor: Boolean = false) {
    if (refs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sourceLines(refs).forEach { SourceFooter(it, onColor = onColor) }
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
