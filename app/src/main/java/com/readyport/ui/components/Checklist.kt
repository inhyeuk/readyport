package com.readyport.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.DeleteOutline
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/**
 * 체크리스트 항목 강조 — 늦음은 부드럽게(Caution), 빨강은 출발 당일 입국 카드처럼 정말 급한 것에만(Urgent).
 */
enum class CheckEmphasis { Normal, Overdue, Urgent }

/**
 * 체크리스트 한 줄 (여행 체크리스트·오늘 화면 '지금 챙길 것'이 함께 쓴다, 2026-10-02).
 * - 머리 줄(배지 + 제목 + 표시 태그 + 체크 상자) **전체가** Role.Checkbox 토글 — 손가락이 큰 칸 어디를 눌러도 된다(최소 minTouch).
 *   TalkBack은 제목·태그를 한 번에 읽고 상태는 [stateText](`했어요`/`아직이에요`/`11월 1일부터 할 수 있어요`)로 알린다.
 * - 체크하면 배지가 초록으로 바뀌며 살짝 튀었다 돌아온다(스프링 한 번 — 요란하지 않게). 제목은 흐려지지만 줄을 긋지 않는다(읽기 쉬움).
 * - 설명·버튼·출처([body]·[extra])는 토글 밖 — 안의 버튼을 눌러도 체크가 바뀌지 않는다. 제목 시작선에 맞추고, 큰 글자 배치에서는 폭 전체.
 * - [enabled] = false(아직 열리지 않은 항목): 체크 상자 흐리게, 누를 수 없음 — 내용은 그대로 다 보인다.
 */
@Composable
fun ChecklistRow(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    stateText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: CheckEmphasis = CheckEmphasis.Normal,
    tags: (@Composable () -> Unit)? = null,
    body: String? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val dimens = LocalDimens.current
    val stacked = isStackedListRow()
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        if (checked) {
            bounce.snapTo(0.82f)
            bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    val tone = when {
        checked -> BadgeTone.Success
        emphasis == CheckEmphasis.Urgent -> BadgeTone.Danger
        emphasis == CheckEmphasis.Overdue -> BadgeTone.Caution
        !enabled -> BadgeTone.Neutral
        else -> BadgeTone.Accent
    }
    val titleColor by animateColorAsState(if (checked) Tokens.InkSecondary else Tokens.Ink, label = "check-title")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BadgeTitleLayout(
            modifier = Modifier
                .fillMaxWidth()
                .minTouch()
                .clip(MaterialTheme.shapes.small)
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
                .semantics { stateDescription = stateText },
            badge = { IconBadge(icon, tone = tone, modifier = Modifier.scale(bounce.value)) },
            trailing = {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled,
                    // 체크 상자 색은 앱 전체 Accent 하나(꼭 챙길 물건·동의 체크와 같게)
                    colors = CheckboxDefaults.colors(
                        checkedColor = Tokens.Accent,
                        uncheckedColor = Tokens.LineStrong,
                        checkmarkColor = Tokens.Surface,
                        disabledUncheckedColor = Tokens.Line,
                    ),
                )
            },
            stack = stacked,
            gap = 12.dp,
            forceStack = stacked,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KoText(title, MaterialTheme.typography.titleMedium, color = titleColor, glueShort = true)
                    if (tags != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { tags() }
                    }
                }
            },
        )
        if (body != null || extra != null) {
            // 제목 시작선(배지 + 12dp)에 맞춘다. 큰 글자 배치는 폭 전체
            val indent = if (stacked) 0.dp else dimens.iconBadge + 12.dp
            Column(Modifier.fillMaxWidth().padding(start = indent), verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                if (body != null) NumberText(body, MaterialTheme.typography.bodyMedium, color = if (checked) Tokens.InkSecondary else Tokens.Ink)
                extra?.invoke(this)
            }
        }
    }
}

/** 체크리스트 진행 막대 (8dp, 둥근 끝). 다 하면 초록. TalkBack은 옆 글(`7개 중 3개`)이 읽으므로 막대는 숨긴다 */
@Composable
fun CheckProgressBar(done: Int, total: Int, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val complete = total > 0 && done >= total
    LinearProgressIndicator(
        progress = { if (total == 0) 0f else done.toFloat() / total },
        modifier = modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics {},
        color = when {
            onDark -> OnDark.content
            complete -> Tokens.SuccessText
            else -> Tokens.Accent
        },
        trackColor = if (onDark) Tokens.White12 else Tokens.SurfaceSunken,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/**
 * 여행 과정 단계 카드 (2026-10-03 — 예전 이름 `ChecklistPhaseCard`): 머리(단계 아이콘 + 단계 이름 + 언제까지 + `3 / 7` + 막대)
 * → 항목들(사이 1dp Line). 지금 단계면 머리에 `지금` 태그(Accent). 흰 카드 + 그림자 + 옅은 테두리(결정 6).
 * TalkBack: 머리는 제목(heading) 하나로 `짐, 8개 중 3개 했어요`를 읽는다.
 */
@Composable
fun StageSectionCard(
    title: String,
    hint: String?,
    icon: ImageVector,
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    now: Boolean = false,
    nowLabel: String? = null,
    headerDescription: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.large
    val stacked = isStackedLayout() || dimens.easyMode
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = modifier.fillMaxWidth().cardShadow(shape),
    ) {
        Column(Modifier.fillMaxWidth().padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.inner + 4.dp)) {
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = headerDescription },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val style = MaterialTheme.typography.titleLarge
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val size = textIconSize(dimens.icon, style)
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (now) Tokens.Accent else Tokens.InkSecondary,
                        modifier = Modifier.padding(top = firstLineIconOffset(style, size)).size(size),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        KoText(title, style, color = Tokens.Ink, heading = true, glueShort = true)
                        if (hint != null && !stacked) PhaseHint(hint, stacked = false)
                    }
                    if (!stacked) Count(done, total)
                }
                // 큰 글자 배치: 언제 할지는 폭 전체로 — 아이콘·숫자 사이 좁은 칸에서 `전쯤부/터`처럼 쪼개지지 않게(`·` 자리에서 줄을 바꾼다).
                // `3 / 7`은 막대 옆으로 내려 제목에 폭을 준다
                if (hint != null && stacked) PhaseHint(hint, stacked = true)
                if (now && nowLabel != null) StatusTag(nowLabel, StatusKind.Info)
                if (stacked) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CheckProgressBar(done, total, Modifier.weight(1f))
                        Count(done, total)
                    }
                } else {
                    CheckProgressBar(done, total)
                }
            }
            content()
        }
    }
}

/** 단계 '언제' 한 줄(`출발 7일 전부터 · 10월 30일까지`). [stacked]면 `·` 자리에서 줄을 바꿔 폭 전체로 */
@Composable
private fun PhaseHint(hint: String, stacked: Boolean) {
    val shown = if (stacked) hint.replace(" · ", "\n") else hint
    KoText(hint, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary, display = koDisplay(keepMonthDay(shown), glueShort = true))
}

@Composable
private fun Count(done: Int, total: Int) {
    KoText(
        "$done / $total",
        MaterialTheme.typography.titleMedium,
        color = if (total > 0 && done >= total) Tokens.SuccessText else Tokens.Ink,
        textAlign = TextAlign.End,
    )
}

/** 체크리스트 카드 안 항목 사이 줄 */
@Composable
fun ChecklistDivider() {
    HorizontalDivider(thickness = 1.dp, color = Tokens.Line)
}

/**
 * 둘 중 하나를 고르는 대화상자(지우기가 아닌 선택 — 빨강 없음). 버튼 둘 다 Accent 글자, 높이 minTouch.
 * 본문은 200%에서도 잘리지 않게 스크롤로 감싼다. 닫기(뒤로·바깥 누름)는 [onDismiss].
 */
@Composable
fun ChoiceDialog(
    title: String,
    body: String,
    first: String,
    onFirst: () -> Unit,
    second: String,
    onSecond: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onFirst, colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Accent), modifier = Modifier.minTouch()) {
                KoText(first, MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onSecond, colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Accent), modifier = Modifier.minTouch()) {
                KoText(second, MaterialTheme.typography.labelLarge)
            }
        },
        icon = icon?.let { { Icon(it, contentDescription = null, tint = Tokens.Accent) } },
        title = { KoText(title, MaterialTheme.typography.titleLarge, glueShort = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                KoText(body, MaterialTheme.typography.bodyLarge)
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = Tokens.Surface,
        titleContentColor = Tokens.Ink,
        textContentColor = Tokens.Ink,
    )
}

/**
 * 목록 항목 하나에 딸린 가벼운 지우기(글자 버튼, DangerText) — 내가 넣은 한 줄 항목처럼 작은 것에만.
 * 여행·여권처럼 무거운 것을 지울 때는 [DangerButton] + 확인 대화상자.
 * [contentDescription]: 무엇을 지우는지 붙인 TalkBack 이름(`우산 챙기기 지우기`).
 */
@Composable
fun QuietDangerButton(text: String, onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(contentColor = Tokens.DangerText),
        modifier = modifier.minTouch().semantics { this.contentDescription = contentDescription },
    ) {
        val style = MaterialTheme.typography.labelLarge
        Icon(
            androidx.compose.material.icons.Icons.Outlined.DeleteOutline,
            contentDescription = null,
            modifier = Modifier.size(textIconSize(LocalDimens.current.icon, style)),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 6.dp))
        KoText(text, style)
    }
}

/** 체크리스트 항목 아래 작은 버튼 묶음 자리(버튼이 둘 이상이면 위아래로) */
@Composable
fun ChecklistActions(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
