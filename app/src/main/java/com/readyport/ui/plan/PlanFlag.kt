package com.readyport.ui.plan

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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.ReportProblem
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.readyport.R
import com.readyport.plan.PlanFlagReason
import com.readyport.plan.PlanRules
import com.readyport.ui.board.BoardField
import com.readyport.ui.board.ErrorLine
import com.readyport.ui.board.PiiWarningCard
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.EqualWidthPair
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= AI 계획 신고 (Play 'AI 생성 콘텐츠' 정책: 앱을 떠나지 않고 신고) =======================
// 게시판 신고 카드(BoardDialogs.ReportCard)와 같은 모양: 깃발 배지 + 질문 → 이유(라디오 줄) → 메모(선택) → 무엇이 일어나는지 → 그만두기 | 신고하기.
// 신고는 plan_flags/{요청 id}에 계획 하나당 한 번, 운영자만 읽는다(firebase/firestore.rules).

@StringRes
fun PlanFlagReason.labelRes(): Int = when (this) {
    PlanFlagReason.Inaccurate -> R.string.plan_flag_reason_inaccurate
    PlanFlagReason.Inappropriate -> R.string.plan_flag_reason_inappropriate
    PlanFlagReason.Unsafe -> R.string.plan_flag_reason_unsafe
    PlanFlagReason.Other -> R.string.plan_flag_reason_other
}

private fun PlanFlagReason.icon(): ImageVector = when (this) {
    PlanFlagReason.Inaccurate -> Icons.Outlined.ErrorOutline
    PlanFlagReason.Inappropriate -> Icons.Outlined.SentimentVeryDissatisfied
    PlanFlagReason.Unsafe -> Icons.Outlined.ReportProblem
    PlanFlagReason.Other -> Icons.AutoMirrored.Outlined.HelpOutline
}

/** 신고 대화상자 (바깥을 눌러도 닫힌다 — 이유를 고르고 `신고하기`를 눌러야 보낸다) */
@Composable
fun PlanFlagDialog(
    sending: Boolean,
    @StringRes error: Int?,
    onSend: (PlanFlagReason, String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(horizontal = 16.dp)) { PlanFlagCard(sending, error, onSend, onDismiss) }
    }
}

/**
 * 신고 카드: 이유 4가지(하나 고르기) + 메모(선택, 200자, 게시판과 같은 개인정보 거르기 — 여권·주민번호 모양은 지워야 보낸다).
 * 이유를 고르기 전·메모가 막힌 동안·보내는 중에는 `신고하기`를 누를 수 없다.
 */
@Composable
fun PlanFlagCard(
    sending: Boolean,
    @StringRes error: Int?,
    onSend: (PlanFlagReason, String) -> Unit,
    onDismiss: () -> Unit,
    initialReason: PlanFlagReason? = null,
    initialNote: String = "",
) {
    var reason by rememberSaveable { mutableStateOf(initialReason) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    val check = remember(note) { PlanRules.checkFlagNote(note) }
    val dimens = LocalDimens.current
    val title = stringResource(R.string.plan_flag_title)
    Surface(
        color = Tokens.Surface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().semantics { paneTitle = title },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.gap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Outlined.Flag, tone = BadgeTone.Danger)
                KoText(title, MaterialTheme.typography.titleLarge, Modifier.weight(1f), color = Tokens.Ink, heading = true, glueShort = true)
            }
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlanFlagReason.entries.forEach { r -> FlagReasonRow(r, selected = r == reason) { reason = r } }
            }
            BoardField(
                label = stringResource(R.string.plan_flag_note_label),
                value = note,
                onChange = { note = it },
                max = PlanRules.FLAG_NOTE_MAX,
                placeholder = stringResource(R.string.plan_flag_note_hint),
                problem = if (check.tooLong) stringResource(R.string.plan_note_long) else null,
                minLines = 2,
            )
            IconBullet(stringResource(R.string.plan_flag_note_privacy), Icons.Outlined.Policy, tone = BadgeTone.Caution)
            if (check.pii.isNotEmpty()) {
                PiiWarningCard(
                    note.trim(), check.pii, onAllow = null,
                    blockText = R.string.plan_flag_pii_block, warnText = R.string.plan_flag_pii_warn,
                )
            }
            KoText(stringResource(R.string.plan_flag_body), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
            error?.let { ErrorLine(stringResource(it)) }
            EqualWidthPair(
                gap = 8.dp,
                first = { m ->
                    TextButton(onClick = onDismiss, modifier = m.minTouch(), colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Accent)) {
                        KoText(stringResource(R.string.action_cancel_keep), MaterialTheme.typography.labelLarge)
                    }
                },
                second = { m ->
                    TextButton(
                        onClick = { reason?.let { onSend(it, note) } },
                        enabled = reason != null && !check.blocked && !sending,
                        modifier = m.minTouch(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Tokens.DangerText, disabledContentColor = Tokens.InkTertiary),
                    ) { KoText(stringResource(if (sending) R.string.plan_flag_sending else R.string.plan_flag_send), MaterialTheme.typography.labelLarge) }
                },
            )
        }
    }
}

@Composable
private fun FlagReasonRow(r: PlanFlagReason, selected: Boolean, onClick: () -> Unit) {
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
        KoText(stringResource(r.labelRes()), style, Modifier.weight(1f), color = Tokens.Ink)
    }
}

/** 점검·캡처용: 대화상자 창 없이 어두운 바탕 위에 신고 카드 (게시판 ReportOnScrim과 같은 방법) */
@Composable
fun PlanFlagOnScrim(reason: PlanFlagReason? = PlanFlagReason.Inaccurate, note: String = "", error: Int? = null) {
    Box(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) { PlanFlagCard(sending = false, error = error, onSend = { _, _ -> }, onDismiss = {}, initialReason = reason, initialNote = note) }
}
