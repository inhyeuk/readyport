package com.readyport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.readyport.R
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
 * - rememberGridColumns() == 1(쉬운 모드·큰 글자): 현재 단계 원 48dp + `today_stage_now` 문장 + 8dp 점 6개.
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(Tokens.AccentSoft, CircleShape)
                        .border(2.dp, Tokens.Accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(IconKeys.stage(now), contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(28.dp))
                }
                Text(
                    stringResource(R.string.today_stage_now, labels[now], now + 1, labels.size),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Tokens.Accent,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.indices.forEach { i ->
                    val color = when {
                        preview -> if (i == now) Tokens.Accent else Tokens.Line
                        i <= now -> Tokens.Accent
                        else -> Tokens.Line
                    }
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                }
            }
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
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium.copy(
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
