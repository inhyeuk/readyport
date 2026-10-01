package com.readyport.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 버튼 (DESIGN_SPEC 4.11) =======================
// 모두 모서리 16dp(shapes.medium), 높이는 최소값(buttonHeight 56/64) — 두 줄 라벨이면 커진다.
// 한 화면 주 버튼(PrimaryButton)은 하나. 파괴적 동작은 DangerButton.

private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)

/**
 * '다음으로·넘어가기' 방향 아이콘(꺾쇠·화살표). 버튼 **앞**에 두지 않는다(재검토 R11) — 넘기면 라벨 **뒤**에 그린다.
 * 버튼 앞에는 뜻 아이콘(ShoppingBag·EditCalendar·ArrowDownward 등)을 쓴다.
 */
private val TrailingOnlyIcons = setOf(
    Icons.AutoMirrored.Outlined.NavigateNext,
    Icons.AutoMirrored.Outlined.ArrowForward,
    Icons.Outlined.ChevronRight,
)

/** 이 아이콘을 버튼 라벨 뒤에 그리는지 (꺾쇠·화살표) */
internal fun isTrailingOnlyIcon(icon: ImageVector?): Boolean = icon != null && icon in TrailingOnlyIcons

/**
 * 버튼 라벨: 아이콘(24/28, 글자 크기를 따라 커짐 — 3.6) + 간격 8 + 어절 단위로 줄을 바꾸는 라벨(의미 글자는 원문).
 * 꺾쇠·화살표 아이콘은 라벨 뒤에 둔다([TrailingOnlyIcons]).
 */
@Composable
private fun RowScope.ButtonLabel(text: String, icon: ImageVector?) {
    val style = MaterialTheme.typography.labelLarge
    val size = textIconSize(LocalDimens.current.icon, style)
    val trailing = isTrailingOnlyIcon(icon)
    if (icon != null && !trailing) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size))
        Spacer(Modifier.width(8.dp))
    }
    KoText(text, style, textAlign = TextAlign.Center)
    if (icon != null && trailing) {
        Spacer(Modifier.width(4.dp))
        Icon(icon, contentDescription = null, modifier = Modifier.size(size))
    }
}

/**
 * 화면 가득 너비의 주 버튼 (Accent 채움). 비활성이어도 읽히게 SurfaceHighest 바탕 + InkTertiary 글자(4.87).
 * 어두운 카드 안에서는 colors = ButtonStyles.onDark().
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = colors.copy(disabledContainerColor = Tokens.SurfaceHighest, disabledContentColor = Tokens.InkTertiary),
        contentPadding = ButtonPadding,
        modifier = modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight),
    ) { ButtonLabel(text, icon) }
}

/** 보조 버튼 색. 어두운 채움 위(onDark)는 투명 + 1.5dp Surface 테두리 + Surface 글자 (D18) */
@Immutable
data class SecondaryButtonColors(val container: Color, val content: Color, val border: Color, val borderWidth: Dp)

fun secondaryButtonColors(tone: BadgeTone = BadgeTone.Accent, onDark: Boolean = false): SecondaryButtonColors = when {
    onDark -> SecondaryButtonColors(Color.Transparent, OnDark.content, OnDark.content, 1.5.dp)
    // Neutral을 tonal(SurfaceSunken + InkSecondary)로 칠하면 비활성 버튼(SurfaceHighest + InkTertiary)과 거의 같아 보인다(문제 #3).
    // 흰 바탕 + Ink 글자 + LineStrong 테두리(Surface 4.63 / Ground 4.25)로 '누를 수 있음'을 분명히 한다.
    tone == BadgeTone.Neutral -> SecondaryButtonColors(Tokens.Surface, Tokens.Ink, Tokens.LineStrong, 1.dp)
    // D21: 누를 수 있는 tonal 버튼은 항상 1dp tone.content 테두리 (Accent면 Ground 대비 6.22)
    else -> SecondaryButtonColors(tone.container, tone.content, tone.content, 1.dp)
}

/**
 * 보조 버튼 = tonal + 1dp 테두리 (기존 회색 OutlinedButton 대체).
 * [onDark]: Accent·Navy 카드·여권 카드 안의 보조 버튼(투명 + 흰 테두리).
 * [fillWidth] = false면 글자 폭만큼 (FlowRow 안 등).
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: BadgeTone = BadgeTone.Accent,
    fillWidth: Boolean = true,
    enabled: Boolean = true,
    onDark: Boolean = false,
) {
    val c = secondaryButtonColors(tone, onDark)
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = c.container,
            contentColor = c.content,
            disabledContainerColor = Tokens.SurfaceHighest,
            disabledContentColor = Tokens.InkTertiary,
        ),
        // 비활성은 조작 요소 경계(LineStrong) 대신 장식선(Line) — 누를 수 있는 버튼과 헷갈리지 않게
        border = BorderStroke(c.borderWidth, if (enabled) c.border else Tokens.Line),
        contentPadding = ButtonPadding,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = LocalDimens.current.buttonHeight),
    ) { ButtonLabel(text, icon) }
}

/** 지우기 등 되돌릴 수 없는 동작. 밝은 바탕에서만 — 어두운 카드 안에 두지 않는다(D18). 주 버튼 자리에 두지 않는다 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.DeleteOutline,
    fillWidth: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, Tokens.DangerText),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Tokens.DangerText),
        contentPadding = ButtonPadding,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = LocalDimens.current.buttonHeight),
    ) { ButtonLabel(text, icon) }
}

/** 3순위 동작(수동 모드, 내 정보 잠그기 등): 글자 버튼 */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    TextButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Accent),
        modifier = modifier.minTouch(),
    ) { ButtonLabel(text, icon) }
}

/**
 * 되돌릴 수 없는 삭제 확인 (D8). 제목·본문에 개인정보(이름·여권번호·예약번호)를 넣지 않는다 — `*_confirm_title/body` 키만.
 * 본문은 200%에서도 잘리지 않게 스크롤로 감싼다. [secure]면 대화상자 창에도 FLAG_SECURE(SecureOn)를 직접 건다.
 * 대화상자를 닫는 것은 호출하는 쪽(onConfirm·onDismiss 안에서 상태를 끈다).
 */
@Composable
fun DestructiveConfirm(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    secure: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = Tokens.DangerText),
                modifier = Modifier.minTouch(),
            ) { KoText(confirmLabel, MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Accent),
                modifier = Modifier.minTouch(),
            ) { KoText(stringResource(R.string.action_cancel_keep), MaterialTheme.typography.labelLarge) }
        },
        icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Tokens.DangerText) },
        title = { KoText(title, MaterialTheme.typography.titleLarge, glueShort = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                KoText(body, MaterialTheme.typography.bodyLarge)
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = Tokens.Surface,
        iconContentColor = Tokens.DangerText,
        titleContentColor = Tokens.Ink,
        textContentColor = Tokens.Ink,
        properties = DialogProperties(securePolicy = if (secure) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit),
    )
}

object ButtonStyles {
    /** 어두운 채움(Accent·Navy) 카드 안의 흰 주 버튼: Surface 바탕 + [content] 글자(Navy 또는 Accent) */
    @Composable
    fun onDark(content: Color = Tokens.Navy): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Tokens.Surface,
        contentColor = content,
        disabledContainerColor = Tokens.SurfaceHighest,
        disabledContentColor = Tokens.InkTertiary,
    )
}
