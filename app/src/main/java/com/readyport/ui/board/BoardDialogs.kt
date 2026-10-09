package com.readyport.ui.board

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.ReportGmailerrorred
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.readyport.R
import com.readyport.board.ReportReason
import com.readyport.ui.components.EqualWidthPair
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LinkRow
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 신고 (Play UGC 정책: 앱 안 신고) — DESIGN_SPEC 부록 L.6 =======================

@StringRes
fun ReportReason.label(): Int = when (this) {
    ReportReason.Spam -> R.string.board_reason_spam
    ReportReason.Abuse -> R.string.board_reason_abuse
    ReportReason.Personal -> R.string.board_reason_personal
    ReportReason.Illegal -> R.string.board_reason_illegal
    ReportReason.Misinfo -> R.string.board_reason_misinfo
    ReportReason.Minor -> R.string.board_reason_minor
    ReportReason.Other -> R.string.board_reason_other
}

private fun ReportReason.icon(): ImageVector = when (this) {
    ReportReason.Spam -> Icons.Outlined.Campaign
    ReportReason.Abuse -> Icons.Outlined.SentimentVeryDissatisfied
    ReportReason.Personal -> Icons.Outlined.PersonSearch
    ReportReason.Illegal -> Icons.Outlined.Gavel
    ReportReason.Misinfo -> Icons.Outlined.ReportGmailerrorred
    ReportReason.Minor -> Icons.Outlined.ChildCare
    ReportReason.Other -> Icons.AutoMirrored.Outlined.HelpOutline
}

/** 신고 대화상자 (바깥을 눌러도 닫힌다 — 실수로 신고되지 않게 고른 뒤 `신고하기`를 눌러야 보낸다) */
@Composable
fun ReportDialog(onSend: (ReportReason) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(horizontal = 16.dp)) { ReportCard(onSend, onDismiss) }
    }
}

/**
 * 신고 카드: 깃발 배지 + `무엇이 문제인가요?` → 이유 여섯(라디오 줄, 아이콘) → 무엇이 일어나는지 → 운영자에게 바로 알리기 → 그만두기 | 신고하기.
 * 이유를 고르기 전에는 `신고하기`를 누를 수 없다.
 */
@Composable
fun ReportCard(onSend: (ReportReason) -> Unit, onDismiss: () -> Unit, initial: ReportReason? = null) {
    var reason by rememberSaveable { mutableStateOf(initial) }
    val context = LocalContext.current
    val title = stringResource(R.string.board_report_title)
    Surface(
        color = Tokens.Surface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().semantics { paneTitle = title },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(LocalDimens.current.cardPadding),
            verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Outlined.Flag, tone = BadgeTone.Danger)
                KoText(title, MaterialTheme.typography.titleLarge, Modifier.weight(1f), color = Tokens.Ink, heading = true, glueShort = true)
            }
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReportReason.entries.forEach { r ->
                    ReasonRow(r, selected = r == reason) { reason = r }
                }
            }
            KoText(stringResource(R.string.board_report_body), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
            LinkRow(stringResource(R.string.board_report_contact), onClick = { contactOperator(context) })
            EqualWidthPair(
                gap = 8.dp,
                first = { m ->
                    TextButton(onClick = onDismiss, modifier = m.minTouch(), colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Accent)) {
                        KoText(stringResource(R.string.action_cancel_keep), MaterialTheme.typography.labelLarge)
                    }
                },
                second = { m ->
                    TextButton(
                        onClick = { reason?.let(onSend) },
                        enabled = reason != null,
                        modifier = m.minTouch(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Tokens.DangerText, disabledContentColor = Tokens.InkTertiary),
                    ) { KoText(stringResource(R.string.board_report_send), MaterialTheme.typography.labelLarge) }
                },
            )
        }
    }
}

@Composable
private fun ReasonRow(r: ReportReason, selected: Boolean, onClick: () -> Unit) {
    val style = MaterialTheme.typography.bodyLarge
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier
            .fillMaxWidth()
            .minTouch()
            .background(if (selected) Tokens.AccentSoft else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Tokens.Accent, unselectedColor = Tokens.LineStrong))
        Icon(r.icon(), contentDescription = null, tint = Tokens.InkSecondary, modifier = Modifier.size(textIconSize(LocalDimens.current.icon, style)))
        KoText(stringResource(r.label()), style, Modifier.weight(1f), color = Tokens.Ink)
    }
}

/** 갤러리·점검용: 대화상자 창 없이 어두운 바탕 위에 신고 카드 (공지 NoticeOnScrim과 같은 방법) */
@Composable
fun ReportOnScrim() {
    Box(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = SCRIM)).padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) { ReportCard(onSend = {}, onDismiss = {}, initial = ReportReason.Personal) }
}

private const val SCRIM = 0.45f
