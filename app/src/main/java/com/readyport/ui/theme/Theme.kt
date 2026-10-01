package com.readyport.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.readyport.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 쉬운 모드(PRD 3.2)에 따라 달라지는 크기 (DESIGN_SPEC 3.4).
 * 글자는 모두 sp라서 시스템 글자 크기 확대 설정이 그대로 곱해진다. 4pt 체계(4/8/12/16/20/24/32/40)만 쓴다.
 */
@Immutable
data class ReadyPortDimens(
    val easyMode: Boolean,
    /** 버튼 최소 높이 (두 줄 라벨이면 커진다) */
    val buttonHeight: Dp,
    val screenPadding: Dp,
    val cardPadding: Dp,
    /** 카드 사이 */
    val gap: Dp,
    /** 섹션 사이. LazyListScope.sectionGap()이 (sectionGap − 2×gap) 높이를 넣는다 */
    val sectionGap: Dp = 32.dp,
    /** 카드 안 결론 → 본문 (본문 → 출처는 inner + 4) */
    val inner: Dp = 8.dp,
    /** 누르는 요소 최소 크기: 인라인 버튼·칩·행·IconButton (Modifier.minTouch / minTouchSize) */
    val minTouch: Dp = 48.dp,
    /** 카드·행 앞 아이콘 배지 */
    val iconBadge: Dp = 40.dp,
    /** 타일 안 배지 */
    val iconBadgeSmall: Dp = 32.dp,
    /** 배지 안·버튼 앞 아이콘 */
    val icon: Dp = 24.dp,
    /** 태그·출처 캡션 아이콘 */
    val iconSmall: Dp = 16.dp,
    /** IconTile·StatTile 최소 높이 (세로형) */
    val tileMinHeight: Dp = 112.dp,
    /** 1열 가로형 타일 최소 높이 */
    val tileRowMinHeight: Dp = 72.dp,
    /** ListRow 최소 높이 */
    val listRowMinHeight: Dp = 64.dp,
    /** StepList 번호 원·이니셜 아바타의 최소 지름 (글자가 크면 함께 커진다) */
    val stepBadge: Dp = 28.dp,
)

private val BasicDimens = ReadyPortDimens(
    easyMode = false, buttonHeight = 56.dp, screenPadding = 20.dp, cardPadding = 20.dp, gap = 12.dp,
    sectionGap = 32.dp, inner = 8.dp, minTouch = 48.dp, iconBadge = 40.dp, iconBadgeSmall = 32.dp,
    icon = 24.dp, iconSmall = 16.dp, tileMinHeight = 112.dp, tileRowMinHeight = 72.dp, listRowMinHeight = 64.dp,
    stepBadge = 28.dp,
)
private val EasyDimens = ReadyPortDimens(
    easyMode = true, buttonHeight = 64.dp, screenPadding = 20.dp, cardPadding = 24.dp, gap = 16.dp,
    sectionGap = 40.dp, inner = 12.dp, minTouch = 56.dp, iconBadge = 52.dp, iconBadgeSmall = 40.dp,
    icon = 28.dp, iconSmall = 20.dp, tileMinHeight = 128.dp, tileRowMinHeight = 88.dp, listRowMinHeight = 72.dp,
    stepBadge = 36.dp,
)

val LocalDimens = staticCompositionLocalOf { BasicDimens }

/**
 * M3 기본 보라가 새지 않게 역할을 모두 지정한다 (DESIGN_SPEC 3.1).
 * 주의: M3 Card 기본 바탕은 surfaceContainerHighest(회색), AlertDialog는 surfaceContainerHigh라서
 * 흰 카드·대화상자는 부품에서 containerColor = Surface를 명시한다.
 */
private val ColorScheme = lightColorScheme(
    primary = Tokens.Accent,
    onPrimary = Tokens.Surface,
    primaryContainer = Tokens.AccentSoft,
    onPrimaryContainer = Tokens.Ink,
    inversePrimary = Tokens.AccentSoft,
    secondary = Tokens.InkSecondary,
    onSecondary = Tokens.Surface,
    // 선택 칩 등 (M3 기본값 보라색 대신 코발트 계열)
    secondaryContainer = Tokens.AccentSoft,
    onSecondaryContainer = Tokens.Ink,
    tertiary = Tokens.Help,
    onTertiary = Tokens.Surface,
    tertiaryContainer = Tokens.HelpSoft,
    onTertiaryContainer = Tokens.Help,
    background = Tokens.Ground,
    onBackground = Tokens.Ink,
    surface = Tokens.Surface,
    onSurface = Tokens.Ink,
    surfaceVariant = Tokens.Ground,
    onSurfaceVariant = Tokens.InkSecondary,
    // 톤 틴트를 끈다 — 층위는 그림자만으로
    surfaceTint = Tokens.Surface,
    inverseSurface = Tokens.Navy,
    inverseOnSurface = Tokens.Surface,
    error = Tokens.DangerText,
    onError = Tokens.Surface,
    errorContainer = Tokens.DangerBg,
    onErrorContainer = Tokens.DangerText,
    // 입력칸 테두리 등 조작 요소 경계 (3:1 이상)
    outline = Tokens.LineStrong,
    outlineVariant = Tokens.Line,
    scrim = Color.Black,
    surfaceBright = Tokens.Surface,
    surfaceDim = Tokens.Ground,
    surfaceContainer = Tokens.Surface,
    surfaceContainerHigh = Tokens.SurfaceSunken,
    surfaceContainerHighest = Tokens.SurfaceHighest,
    surfaceContainerLow = Tokens.SurfaceLow,
    surfaceContainerLowest = Tokens.Surface,
)

/**
 * 모서리 (DESIGN_SPEC 3.3): 8 태그 · 12 입력칸·배지·썸네일·배너 · 16 버튼·세그먼트·타일 · 20 카드 · 28 히어로·대화상자.
 * 알약(완전 둥근) 모양은 칩·인디케이터에만.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 줄바꿈 규칙 (DESIGN_SPEC 3.2).
 * 어절 단위 줄바꿈(WordBreak.Phrase)과 Strictness는 API 33부터지만, 줄 나누기 **전략**(Balanced·HighQuality)은
 * setBreakStrategy(API 23+)라 33 미만(테스트 폰 S10, Android 12)에서도 적용된다. 그곳에서는 한국어가 음절 사이 어디서나
 * 끊길 수 있는데 Balanced가 줄 길이를 맞추려고 한 줄에 들어갈 낱말까지 쪼갠다(`태국 입국 카 / 드 (TDAC)`).
 * 그래서 33 미만에서는 Simple(들어가는 만큼 채우는 줄바꿈)을 쓴다 — 스펙 3.2의 'API 33 이상에서만 효과'를 실제로 맞춘다.
 */
object ReadyPortLineBreak {
    /** WordBreak.Phrase(어절 단위)를 쓸 수 있는 API 33 이상인지 */
    val phraseSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** 제목·버튼·타일 라벨: 균형 잡힌 줄, 어절 단위 (33 미만은 Simple) */
    val Heading: LineBreak get() = if (phraseSupported) LineBreak.Heading else LineBreak.Simple

    /** 본문: LineBreak.Paragraph는 어절(Phrase) 단위가 아니라서 직접 정의한다 (33 미만은 Simple) */
    val Body: LineBreak
        get() = if (phraseSupported) {
            LineBreak(
                strategy = LineBreak.Strategy.HighQuality,
                strictness = LineBreak.Strictness.Strict,
                wordBreak = LineBreak.WordBreak.Phrase,
            )
        } else {
            LineBreak.Simple
        }
}

private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

/**
 * Pretendard Std 1.3.9 (SIL OFL 1.1, 배포 원본 OTF 그대로 번들 — D1 운영자 승인 2026-10-01).
 * 한글 2,350자·라틴 포함. 없는 글자(태국어 등)는 시스템 글꼴로 자동 대체된다. 라이선스: assets/licenses/pretendard_std_OFL.txt
 * 가변 글꼴은 굵기 조절이 렌더러(Robolectric 캡처 등)마다 달라 굵기별 정적 글꼴 4개를 쓴다.
 */
val Pretendard = FontFamily(
    Font(R.font.pretendard_std_regular, FontWeight.Normal),
    Font(R.font.pretendard_std_medium, FontWeight.Medium),
    Font(R.font.pretendard_std_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_std_bold, FontWeight.Bold),
)

private fun style(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    spacing: Float = 0f,
    lineBreak: LineBreak = ReadyPortLineBreak.Body,
) = TextStyle(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontFamily = Pretendard,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineBreak = lineBreak,
    platformStyle = NoFontPadding,
)

/**
 * 타입 스케일 (DESIGN_SPEC 3.2). 쉬운 모드는 글자 18~28sp (PRD 3.2).
 * 예외(D19, 운영자 승인 항목): displayLarge 56, displayMedium 36, displaySmall 32 — 히어로 이름·현지인에게 보여 주는 글자.
 */
internal fun typography(easyMode: Boolean): Typography {
    val h = ReadyPortLineBreak.Heading
    val bold = FontWeight.Bold
    val semi = FontWeight.SemiBold
    val medium = FontWeight.Medium
    return if (easyMode) {
        Typography(
            displayLarge = style(56, 72, bold, lineBreak = h),
            displayMedium = style(36, 46, bold, -0.4f, h),
            displaySmall = style(32, 42, bold, -0.3f, h),
            headlineLarge = style(28, 36, bold, lineBreak = h),
            headlineMedium = style(26, 34, bold, -0.2f, h),
            headlineSmall = style(24, 32, bold, lineBreak = h),
            titleLarge = style(24, 32, bold, lineBreak = h),
            titleMedium = style(22, 30, semi, lineBreak = h),
            titleSmall = style(20, 28, semi, lineBreak = h),
            bodyLarge = style(20, 30),
            bodyMedium = style(19, 28),
            bodySmall = style(18, 26),
            labelLarge = style(20, 28, semi, lineBreak = h),
            labelMedium = style(18, 26, semi, 0.2f, h),
            labelSmall = style(18, 24, medium, lineBreak = h),
        )
    } else {
        Typography(
            displayLarge = style(56, 72, bold, lineBreak = h),
            displayMedium = style(34, 42, bold, -0.4f, h),
            displaySmall = style(30, 38, bold, -0.3f, h),
            headlineLarge = style(26, 34, bold, lineBreak = h),
            headlineMedium = style(24, 32, bold, -0.2f, h),
            headlineSmall = style(22, 30, bold, lineBreak = h),
            titleLarge = style(20, 28, bold, lineBreak = h),
            titleMedium = style(17, 24, semi, lineBreak = h),
            titleSmall = style(15, 20, semi, lineBreak = h),
            bodyLarge = style(16, 24),
            bodyMedium = style(15, 22),
            bodySmall = style(13, 18),
            labelLarge = style(16, 22, semi, lineBreak = h),
            labelMedium = style(13, 18, semi, 0.2f, h),
            labelSmall = style(12, 16, medium, lineBreak = h),
        )
    }
}

/**
 * M3 Typography에 없는 역할 (DESIGN_SPEC 3.2 추가 스타일).
 * - stat/statSmall: 큰 숫자(`90일`, `D-3`, `1155`) — 고정폭 숫자(tnum)
 * - localLarge/localMedium: 현지인에게 보여 주는 현지어. 태국어 위아래 부호가 겹치지 않게 행간 1.5배 이상 + Trim.None
 */
@Immutable
data class ReadyPortTypeExtras(
    val stat: TextStyle,
    val statSmall: TextStyle,
    val localLarge: TextStyle,
    val localMedium: TextStyle,
)

private val LocalLineHeight = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

private fun statStyle(size: Int, line: Int) = TextStyle(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontFamily = Pretendard,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
    lineBreak = ReadyPortLineBreak.Heading,
    platformStyle = NoFontPadding,
)

private fun localStyle(size: Int, line: Int) = TextStyle(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight.Bold,
    lineHeightStyle = LocalLineHeight,
    platformStyle = NoFontPadding,
)

internal fun typeExtras(easyMode: Boolean): ReadyPortTypeExtras = if (easyMode) {
    ReadyPortTypeExtras(
        stat = statStyle(34, 42),
        statSmall = statStyle(26, 34),
        localLarge = localStyle(56, 84),
        localMedium = localStyle(32, 48),
    )
} else {
    ReadyPortTypeExtras(
        stat = statStyle(30, 36),
        statSmall = statStyle(22, 28),
        localLarge = localStyle(56, 84),
        localMedium = localStyle(30, 46),
    )
}

private val BasicExtras = typeExtras(false)
private val EasyExtras = typeExtras(true)

val LocalTypeExtras = staticCompositionLocalOf { BasicExtras }

/**
 * 쉬운 모드에서는 누르는 요소의 최소 터치 영역을 56dp로 넓힌다(DESIGN_SPEC 3.4 minTouch).
 * M3 부품은 LocalMinimumInteractiveComponentSize로 자리를 넓히고, 그 밖의 clickable은
 * ViewConfiguration.minimumTouchTargetSize로 터치 영역이 넓어진다.
 */
private class MinTouchViewConfiguration(
    private val base: ViewConfiguration,
    override val minimumTouchTargetSize: DpSize,
) : ViewConfiguration by base

@Composable
fun ReadyPortTheme(easyMode: Boolean = false, content: @Composable () -> Unit) {
    val dimens = if (easyMode) EasyDimens else BasicDimens
    CompositionLocalProvider(
        LocalDimens provides dimens,
        LocalTypeExtras provides if (easyMode) EasyExtras else BasicExtras,
    ) {
        MaterialTheme(
            colorScheme = ColorScheme,
            typography = typography(easyMode),
            shapes = AppShapes,
        ) {
            val base = LocalViewConfiguration.current
            val viewConfiguration = remember(base, dimens.minTouch) {
                MinTouchViewConfiguration(base, DpSize(dimens.minTouch, dimens.minTouch))
            }
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides dimens.minTouch,
                LocalViewConfiguration provides viewConfiguration,
                content = content,
            )
        }
    }
}
