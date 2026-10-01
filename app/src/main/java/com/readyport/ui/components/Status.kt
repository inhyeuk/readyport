package com.readyport.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.prep.ImportStatus
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 상태 태그 (DESIGN_SPEC 4.12) =======================

/** 상태 = 색 + 아이콘 + 글자 (색만으로 전하지 않는다) */
enum class StatusKind(val icon: ImageVector, val tone: BadgeTone) {
    Allowed(Icons.Outlined.CheckCircle, BadgeTone.Success),
    Caution(Icons.Outlined.ReportProblem, BadgeTone.Caution),
    Prohibited(Icons.Outlined.Block, BadgeTone.Danger),
    Info(Icons.Outlined.Info, BadgeTone.Accent),
    Soon(Icons.Outlined.Schedule, BadgeTone.Neutral),
    Verified(Icons.Outlined.Verified, BadgeTone.Success),
    Self(Icons.Outlined.TouchApp, BadgeTone.Help),
    Required(Icons.Outlined.ErrorOutline, BadgeTone.Danger),
}

/**
 * 누를 수 없는 작은 상태 표시 (모서리 8). maxLines 없음 — 글자가 길면 줄을 바꾼다.
 * 누를 수 있는 태그가 필요하면 SelectChip·AssistChip(minTouch, Role.Button)을 쓴다.
 */
@Composable
fun StatusTag(text: String, kind: StatusKind, modifier: Modifier = Modifier) {
    Surface(
        color = kind.tone.container,
        contentColor = kind.tone.content,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.heightIn(min = 28.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(kind.icon, contentDescription = null, modifier = Modifier.size(LocalDimens.current.iconSmall))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 기존 작은 칩 (호환). 새 코드는 StatusTag */
@Composable
fun StatusChip(text: String, container: Color = Tokens.AccentSoft, content: Color = Tokens.Ink, icon: ImageVector? = null) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(LocalDimens.current.iconSmall))
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 한국 반입 태그 색: 가능 초록 / 주의 주황 / 불가 빨강 (PRD 5.8) */
fun importColors(status: ImportStatus): Pair<Color, Color> = when (status) {
    ImportStatus.Allowed -> Tokens.SuccessBg to Tokens.SuccessText
    ImportStatus.Caution -> Tokens.CautionBg to Tokens.CautionText
    ImportStatus.Prohibited -> Tokens.DangerBg to Tokens.DangerText
}

@StringRes
fun importLabel(status: ImportStatus): Int = when (status) {
    ImportStatus.Allowed -> R.string.import_allowed
    ImportStatus.Caution -> R.string.import_caution
    ImportStatus.Prohibited -> R.string.import_prohibited
}

/** 한국 반입 판정 상태 */
fun importKind(status: ImportStatus): StatusKind = when (status) {
    ImportStatus.Allowed -> StatusKind.Allowed
    ImportStatus.Caution -> StatusKind.Caution
    ImportStatus.Prohibited -> StatusKind.Prohibited
}

/** 한국 반입 가능·주의·불가 배지 (기존 ImportTag) — 글자는 import_allowed/caution/prohibited 그대로 */
@Composable
fun ImportVerdictBadge(status: ImportStatus) {
    StatusTag(stringResource(importLabel(status)), importKind(status))
}
