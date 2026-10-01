package com.readyport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 선택 부품 (DESIGN_SPEC 4.17, D7) =======================
// 선택 = Accent 채움 + 흰 글자 + Check 아이콘 (대비 6.78 + 색 외 단서)

/**
 * 탭 전환 세그먼트 (나라 섹션, 영상 정렬). 스와이프 없이 탭으로만.
 * - 2열 폭(rememberGridColumns() == 2): 트랙 SurfaceSunken + 1dp LineStrong 테두리 안의 칸들
 * - 1열(쉬운 모드·큰 글자): 폭 전체 세로 라디오 목록 (sticky로 고정하지 않는다)
 * - 기본 모드에서 창 폭 340dp 미만(320×470 예산): 칸 아이콘 없는 한 줄 글자 세그먼트, 트랙 padding 0.
 *   선택 칸의 Check는 남긴다(D7) — 글자 폭을 뺏지 않게 글자 위에 작게
 */
@Composable
fun <T> ChoiceSegments(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    icon: (T) -> ImageVector?,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val width = windowWidthDp()
    val narrow = !dimens.easyMode && width > 0f && width < NARROW_WINDOW_DP
    val columns = rememberGridColumns()
    if (!narrow && columns == 1) {
        Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                val sel = o == selected
                val content = if (sel) Tokens.Surface else Tokens.Ink
                val shape = MaterialTheme.shapes.medium
                Row(
                    Modifier
                        .fillMaxWidth()
                        .minTouch()
                        .clip(shape)
                        .background(if (sel) Tokens.Accent else Tokens.Surface)
                        .then(if (sel) Modifier else Modifier.border(1.dp, Tokens.LineStrong, shape))
                        .selectable(selected = sel, role = Role.Tab, onClick = { onSelect(o) })
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val style = MaterialTheme.typography.labelLarge
                    val iconSize = textIconSize(dimens.icon, style)
                    icon(o)?.let { Icon(it, contentDescription = null, tint = if (sel) Tokens.Surface else Tokens.InkSecondary, modifier = Modifier.size(iconSize)) }
                    KoText(label(o), style, Modifier.weight(1f), color = content)
                    if (sel) Icon(Icons.Outlined.Check, contentDescription = null, tint = Tokens.Surface, modifier = Modifier.size(iconSize))
                }
            }
        }
        return
    }
    val trackShape = MaterialTheme.shapes.medium
    val trackPadding = if (narrow) 0.dp else 4.dp
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(trackShape)
            .background(Tokens.SurfaceSunken)
            .border(1.dp, Tokens.LineStrong, trackShape)
            .padding(trackPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(if (narrow) 0.dp else 4.dp),
    ) {
        options.forEach { o ->
            val sel = o == selected
            val content: Color = if (sel) Tokens.Surface else Tokens.Ink
            val iconSize = textIconSize(if (dimens.easyMode) 20.dp else 16.dp, MaterialTheme.typography.labelLarge)
            val cell = Modifier
                .weight(1f)
                .fillMaxHeight()
                .heightIn(min = dimens.buttonHeight - trackPadding * 2)
                .clip(MaterialTheme.shapes.small)
                .background(if (sel) Tokens.Accent else Color.Transparent)
                .selectable(selected = sel, role = Role.Tab, onClick = { onSelect(o) })
            val labelText: @Composable () -> Unit = {
                KoText(
                    label(o),
                    MaterialTheme.typography.labelLarge.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold),
                    color = content,
                    textAlign = TextAlign.Center,
                )
            }
            if (narrow) {
                // 340dp 미만: 칸 아이콘은 빼고 글자는 한 줄 그대로. 선택 칸의 Check(D7 색 외 단서)는 글자 위에 작게 —
                // 모든 칸이 같은 자리를 비워 두어 글자 줄이 칸마다 같은 높이에 선다. 최소 높이(56dp) 안이라 화면 예산은 그대로.
                Column(
                    cell.padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (sel) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = Tokens.Surface, modifier = Modifier.size(iconSize))
                    } else {
                        Spacer(Modifier.size(iconSize))
                    }
                    labelText()
                }
            } else {
                val cellIcon = if (sel) Icons.Outlined.Check else icon(o)
                Row(
                    cell.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (cellIcon != null) {
                        Icon(
                            cellIcon,
                            contentDescription = null,
                            tint = if (sel) Tokens.Surface else Tokens.InkSecondary,
                            modifier = Modifier.size(iconSize),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    labelText()
                }
            }
        }
    }
}

/**
 * 고르는 칩. 기본은 한 개만 고르는 곳(나라·정렬·분류·목적지) — FilterChip의 기본 role(Checkbox)을 RadioButton으로 덮어쓰고
 * 부모에 selectableGroup()을 둔다. 켬·끔 토글(17 `현지어 크게`)은 [singleChoice] = false(Checkbox 그대로).
 * [avatar]: 24dp 원형 나라 사진 등(장식).
 */
@Composable
fun SelectChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    avatar: (@Composable () -> Unit)? = null,
    singleChoice: Boolean = true,
) {
    val iconSize = textIconSize(if (LocalDimens.current.easyMode) 22.dp else 18.dp, MaterialTheme.typography.labelLarge)
    val leading: (@Composable () -> Unit)? = when {
        selected -> {
            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(iconSize)) }
        }
        avatar != null -> avatar
        leadingIcon != null -> {
            { Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(iconSize)) }
        }
        else -> null
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { KoText(label, MaterialTheme.typography.labelLarge) },
        modifier = modifier
            .minTouch()
            .then(if (singleChoice) Modifier.semantics { role = Role.RadioButton } else Modifier),
        leadingIcon = leading,
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Tokens.Surface,
            labelColor = Tokens.Ink,
            iconColor = Tokens.InkSecondary,
            selectedContainerColor = Tokens.Accent,
            selectedLabelColor = Tokens.Surface,
            selectedLeadingIconColor = Tokens.Surface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Tokens.LineStrong,
            selectedBorderColor = Tokens.Accent,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
    )
}

/** 스위치 색 한곳에서: 꺼짐 = SurfaceHighest 트랙 + LineStrong 테두리·썸(M3 기본 보라 방지), 켜짐 = Accent */
@Composable
fun appSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Tokens.Surface,
    checkedTrackColor = Tokens.Accent,
    checkedBorderColor = Tokens.Accent,
    uncheckedThumbColor = Tokens.LineStrong,
    uncheckedTrackColor = Tokens.SurfaceHighest,
    uncheckedBorderColor = Tokens.LineStrong,
)
