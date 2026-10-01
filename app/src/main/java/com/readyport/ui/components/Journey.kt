package com.readyport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/** 여행 단계 이름 6칸 (PRD 5.1) */
private val StageNames = listOf(
    R.string.stage_prepare, R.string.stage_departure, R.string.stage_arrival,
    R.string.stage_traveling, R.string.stage_return, R.string.stage_wrapup,
)

/**
 * 여행 6단계 표시 (DESIGN_SPEC 4.14). 전체가 한 문장으로 읽힌다 — [description]에 `today_stage_desc`를 그대로 넘긴다.
 * - 기본: 아이콘 노드 6개 + 연결선. 지난 단계 = Accent 채움 + 흰 Check, 지금 = AccentSoft + 2dp Accent 링 + 단계 아이콘,
 *   다음 = Surface + 1dp LineStrong + InkTertiary 아이콘. 라벨은 줄바꿈 허용(9sp 축소 없음).
 * - rememberGridColumns() == 1(쉬운 모드·큰 글자): 현재 단계 원(48/56dp, 글자를 따라 커짐) + `today_stage_now` 문장 + 폭 전체 여섯 칸 막대([StageSegments]).
 * - [preview](여행 없음): 모든 노드 Neutral, 시작점(준비)만 링.
 */
@Composable
fun JourneyStepper(current: Int, description: String, modifier: Modifier = Modifier, preview: Boolean = false) {
    val labels = StageNames.map { stringResource(it) }
    val now = current.coerceIn(0, labels.lastIndex)
    val compact = rememberGridColumns() == 1
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (compact) {
            val style = MaterialTheme.typography.bodyLarge
            // 지금 단계 배지: 글자를 따라 커진다(최대 1.25배 — 200%에서 큰 글 옆에 작게 남지 않게, 재검토2 ③#4)
            val badge = scaledBadgeSize(if (LocalDimens.current.easyMode) 56.dp else 48.dp, style)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(badge)
                        .background(Tokens.AccentSoft, CircleShape)
                        .border(2.dp, Tokens.Accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(IconKeys.stage(now), contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(badge * 0.55f))
                }
                // 어절 단위로만 줄을 바꾼다(`6단/계)` 방지 — KoText). 전체가 clearAndSetSemantics라 읽기는 description
                KoText(
                    stringResource(R.string.today_stage_now, labels[now], now + 1, labels.size),
                    style,
                    Modifier.weight(1f),
                    color = Tokens.Accent,
                )
            }
            Spacer(Modifier.height(12.dp))
            StageSegments(now, preview)
        } else {
            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, label ->
                    val state = when {
                        i == now -> NodeState.Now
                        !preview && i < now -> NodeState.Done
                        else -> NodeState.Next
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StageNode(
                            index = i,
                            state = state,
                            leftLine = if (i == 0) null else if (!preview && i <= now) Tokens.Accent else Tokens.Line,
                            rightLine = if (i == labels.lastIndex) null else if (!preview && i < now) Tokens.Accent else Tokens.Line,
                        )
                        KoText(
                            label,
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (state == NodeState.Now) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (state == NodeState.Now) Tokens.Accent else Tokens.InkSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private enum class NodeState { Done, Now, Next }

/** 칸 사이 간격 */
private val SegmentGap = 4.dp

/**
 * 쉬운 모드·큰 글자의 여섯 칸 막대 (재검토2 ③#4 — 지름 5dp 점 여섯 개 대신): 폭 전체를 여섯 칸으로 나누고 칸마다 단계 아이콘 배지.
 * 지난 칸 = Accent 채움 + 흰 Check, 지금 칸 = AccentSoft + 2dp Accent 테두리 + 단계 아이콘(Accent),
 * 남은 칸 = 흰 바탕 + 1dp LineStrong + 단계 아이콘(InkTertiary). 아이콘은 글자를 따라 커지고(최대 1.5배) 칸 폭을 넘지 않는다.
 * 넘김 표시(●●●●○○)처럼 보이지 않게 칸은 둥근 사각이고 서로 붙어 한 막대로 읽힌다. 장식이다 — 읽기는 위 문장(description).
 */
@Composable
private fun StageSegments(now: Int, preview: Boolean) {
    val dimens = LocalDimens.current
    val wanted = textIconSize(if (dimens.easyMode) 24.dp else 20.dp, MaterialTheme.typography.bodyLarge)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = (maxWidth - SegmentGap * (StageNames.size - 1)) / StageNames.size
        val icon = minOf(wanted, cell - 12.dp).coerceAtLeast(12.dp)
        val shape = MaterialTheme.shapes.extraSmall
        Row(horizontalArrangement = Arrangement.spacedBy(SegmentGap)) {
            StageNames.indices.forEach { i ->
                val state = when {
                    i == now -> NodeState.Now
                    !preview && i < now -> NodeState.Done
                    else -> NodeState.Next
                }
                val (bg, border, tint) = when (state) {
                    NodeState.Done -> Triple(Tokens.Accent, null, Tokens.Surface)
                    NodeState.Now -> Triple(Tokens.AccentSoft, Tokens.Accent, Tokens.Accent)
                    NodeState.Next -> Triple(Tokens.Surface, Tokens.LineStrong, Tokens.InkTertiary)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(icon + 12.dp)
                        .clip(shape)
                        .background(bg)
                        .then(if (border != null) Modifier.border(if (state == NodeState.Now) 2.dp else 1.dp, border, shape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state == NodeState.Done) Icons.Outlined.Check else IconKeys.stage(i),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(icon),
                    )
                }
            }
        }
    }
}

/** 글자를 따라 커지는 배지 지름 (최대 1.25배 — 화면 폭을 다 먹지 않게) */
@Composable
@ReadOnlyComposable
internal fun scaledBadgeSize(base: Dp, style: TextStyle): Dp {
    val fontSize = style.fontSize
    if (!fontSize.isSp || fontSize.value <= 0f) return base
    val actual = with(LocalDensity.current) { fontSize.toDp() }.value
    return base * (actual / fontSize.value).coerceIn(1f, 1.25f)
}

@Composable
private fun StageNode(index: Int, state: NodeState, leftLine: Color?, rightLine: Color?) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .drawBehind {
                val y = size.height / 2
                val stroke = 2.dp.toPx()
                val ltr = layoutDirection == LayoutDirection.Ltr
                val startX = if (ltr) 0f else size.width
                val endX = if (ltr) size.width else 0f
                leftLine?.let { drawLine(it, Offset(startX, y), Offset(size.width / 2, y), stroke) }
                rightLine?.let { drawLine(it, Offset(size.width / 2, y), Offset(endX, y), stroke) }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            NodeState.Done -> Box(Modifier.size(32.dp).background(Tokens.Accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Tokens.Surface, modifier = Modifier.size(20.dp))
            }
            NodeState.Now -> Box(
                Modifier.size(36.dp).background(Tokens.AccentSoft, CircleShape).border(2.dp, Tokens.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IconKeys.stage(index), contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(20.dp))
            }
            NodeState.Next -> Box(
                Modifier.size(32.dp).background(Tokens.Surface, CircleShape).border(1.dp, Tokens.LineStrong, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IconKeys.stage(index), contentDescription = null, tint = Tokens.InkTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
