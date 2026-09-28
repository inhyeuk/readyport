package com.readyport.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 쉬운 모드(PRD 3.2)에 따라 달라지는 크기.
 * 글자는 모두 sp라서 시스템 글자 크기 확대 설정이 그대로 곱해진다.
 */
@Immutable
data class ReadyPortDimens(
    val easyMode: Boolean,
    val buttonHeight: Dp,
    val screenPadding: Dp,
    val cardPadding: Dp,
    val gap: Dp,
)

private val BasicDimens = ReadyPortDimens(
    easyMode = false, buttonHeight = 52.dp, screenPadding = 16.dp, cardPadding = 16.dp, gap = 12.dp,
)
private val EasyDimens = ReadyPortDimens(
    easyMode = true, buttonHeight = 64.dp, screenPadding = 20.dp, cardPadding = 20.dp, gap = 16.dp,
)

val LocalDimens = staticCompositionLocalOf { BasicDimens }

private val ColorScheme = lightColorScheme(
    primary = Tokens.Accent,
    onPrimary = Tokens.Surface,
    primaryContainer = Tokens.AccentSoft,
    onPrimaryContainer = Tokens.Ink,
    secondary = Tokens.InkSecondary,
    onSecondary = Tokens.Surface,
    background = Tokens.Ground,
    onBackground = Tokens.Ink,
    surface = Tokens.Surface,
    onSurface = Tokens.Ink,
    surfaceVariant = Tokens.Ground,
    onSurfaceVariant = Tokens.InkSecondary,
    surfaceContainer = Tokens.Surface,
    outline = Tokens.Line,
    outlineVariant = Tokens.LineSoft,
    error = Tokens.DangerText,
    errorContainer = Tokens.DangerBg,
    onErrorContainer = Tokens.DangerText,
)

// 모서리 14~20dp (PRD 5장 공통)
private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

/** 쉬운 모드는 글자 18~28sp 범위 (PRD 3.2). 기본 모드는 일반 앱 크기. */
internal fun typography(easyMode: Boolean): Typography {
    fun style(size: Int, weight: FontWeight = FontWeight.Normal, line: Float = 1.4f) =
        TextStyle(fontSize = size.sp, lineHeight = (size * line).sp, fontWeight = weight)
    return if (easyMode) {
        Typography(
            headlineLarge = style(28, FontWeight.Bold, 1.3f),
            headlineMedium = style(26, FontWeight.Bold, 1.3f),
            titleLarge = style(24, FontWeight.Bold),
            titleMedium = style(22, FontWeight.SemiBold),
            bodyLarge = style(20),
            bodyMedium = style(19),
            bodySmall = style(18),
            labelLarge = style(20, FontWeight.SemiBold),
            labelMedium = style(18, FontWeight.Medium),
            labelSmall = style(18, FontWeight.Medium),
        )
    } else {
        Typography(
            headlineLarge = style(26, FontWeight.Bold, 1.3f),
            headlineMedium = style(24, FontWeight.Bold, 1.3f),
            titleLarge = style(20, FontWeight.Bold),
            titleMedium = style(17, FontWeight.SemiBold),
            bodyLarge = style(16),
            bodyMedium = style(15),
            bodySmall = style(13),
            labelLarge = style(16, FontWeight.SemiBold),
            labelMedium = style(13, FontWeight.Medium),
            labelSmall = style(12, FontWeight.Medium),
        )
    }
}

@Composable
fun ReadyPortTheme(easyMode: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDimens provides if (easyMode) EasyDimens else BasicDimens) {
        MaterialTheme(
            colorScheme = ColorScheme,
            typography = typography(easyMode),
            shapes = AppShapes,
            content = content,
        )
    }
}
