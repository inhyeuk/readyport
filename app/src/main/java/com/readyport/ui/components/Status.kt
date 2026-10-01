package com.readyport.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
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
 * [icon]: 상태 아이콘 대신 쓸 아이콘(색·톤은 [kind] 그대로). [display]: 보일 글자(줄바꿈 보정본).
 */
@Composable
fun StatusTag(text: String, kind: StatusKind, modifier: Modifier = Modifier, display: String? = null, icon: ImageVector? = null) {
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
            val style = MaterialTheme.typography.labelMedium
            Icon(icon ?: kind.icon, contentDescription = null, modifier = Modifier.size(textIconSize(LocalDimens.current.iconSmall, style)))
            KoText(text, style, display = display)
        }
    }
}

/** 기존 작은 칩 (호환). 새 코드는 StatusTag. 기본 바탕은 중립(SurfaceSunken) — AccentSoft 채움은 '선택됨'에만(재검토2 ①#1·④#9) */
@Composable
fun StatusChip(text: String, container: Color = Tokens.SurfaceSunken, content: Color = Tokens.InkSecondary, icon: ImageVector? = null) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val style = MaterialTheme.typography.labelMedium
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(textIconSize(LocalDimens.current.iconSmall, style)))
            KoText(text, style)
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

/**
 * 카드 **안** 판정 한 묶음 (다듬기 D0 — 재검토2 ①#4 '상태 표현이 부품마다 다름'의 규칙 하나):
 * 판정은 [StatusTag] 알약 하나(색 + 아이콘 + 글자), 이유는 그 아래 보통 본문(InkSecondary, 숫자 토큰 굵게) —
 * 상태색 글자 여러 줄이나 연한 채움 + 막대 블록을 카드 안에 두지 않는다. 폭 전체 블록(채움 + 막대)은 화면 단위 경고(NoticeBanner)에만.
 * 쇼핑 리스트(22)·나라 쇼핑(06)·귀국 담아 둔 물건(15)이 같은 모양.
 */
@Composable
fun ImportVerdictNote(status: ImportStatus, note: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ImportVerdictBadge(status)
        note?.let { NumberText(it, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary, emphasisColor = Tokens.Ink) }
    }
}

// ======================= 꼭 채울 칸 표시 (다듬기 D0 — 재검토2 ③#3·②#3·④#3) =======================

/**
 * 꼭 채울 빈칸의 끝 표시: 칸 이름 **뒤** 작은 느낌표(ErrorOutline) 하나 — 칸마다 `꼭 채워요` 글자 태그를 되풀이하지 않는다.
 * 입국 카드 확인(20)의 칸 안 표시·고르는 칸 이름 뒤, 값 복사해서 넣기(21)의 '사이트에서 직접 적을 칸'이 같은 그림·같은 색 규칙:
 * 손대기 전에는 할 일(CautionText), 그 칸을 거쳐 나갔거나 빈칸으로 가기를 눌렀으면 오류([error] = DangerText).
 * TalkBack: `빈칸`(form_blank_cd) — [describe] = false면 장식(칸 자체가 stateDescription으로 알릴 때).
 * 크기는 옆 글자([style])를 따라 커진다(최대 1.5배). [firstLine]: Row(Alignment.Top) 안에서 옆 글 첫 줄 가운데에 맞춘다.
 */
@Composable
fun RequiredMark(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    error: Boolean = false,
    describe: Boolean = true,
    base: Dp = LocalDimens.current.iconSmall + 4.dp,
    firstLine: Boolean = true,
) {
    val size = textIconSize(base, style)
    Icon(
        RequiredIcon,
        contentDescription = if (describe) stringResource(R.string.form_blank_cd) else null,
        tint = requiredMarkColor(error),
        modifier = modifier.padding(top = if (firstLine) firstLineIconOffset(style, size) else 0.dp).size(size),
    )
}

/** 꼭 채울 칸 표시 그림 (요약 줄·칸 끝 같은 그림) */
val RequiredIcon: ImageVector = Icons.Outlined.ErrorOutline

/** 꼭 채울 칸 표시 색: 할 일 = CautionText, 오류 = DangerText */
fun requiredMarkColor(error: Boolean): Color = if (error) Tokens.DangerText else Tokens.CautionText

/**
 * 빈칸 묶음 머리 요약 한 줄: `[!] 꼭 채울 칸 N개 · 모두 M칸` — 칸마다 태그 대신 묶음에 한 번 (값 복사해서 넣기 21, 재검토2 ①#10·②#3).
 * 느낌표 그림이 그대로 범례가 된다(칸 이름 뒤의 같은 그림). [required]가 0이면 `모두 M칸`만.
 */
@Composable
fun RequiredSummary(total: Int, required: Int, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (required > 0) RequiredMark(style = style, describe = false)
        val text = if (required > 0) {
            stringResource(R.string.required_summary, required, total)
        } else {
            stringResource(R.string.required_summary_none, total)
        }
        NumberText(text, style, Modifier.weight(1f), color = Tokens.InkSecondary, emphasisColor = Tokens.Ink)
    }
}
