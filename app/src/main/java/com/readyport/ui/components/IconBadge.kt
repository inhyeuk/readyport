package com.readyport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/**
 * 아이콘 배지·타일·배너가 함께 쓰는 색 짝 (DESIGN_SPEC 3.1 BadgeTone).
 * 모든 짝은 TokenContrastTest(비텍스트 3:1)가 검사한다.
 */
enum class BadgeTone(val container: Color, val content: Color) {
    /** 입국·서류·일반 정보 */
    Accent(Tokens.AccentSoft, Tokens.Accent),

    /** 가능·완료·전기 OK */
    Success(Tokens.SuccessBg, Tokens.SuccessText),

    /** 조건부 주의 */
    Caution(Tokens.CautionBg, Tokens.CautionText),

    /** 금지·삭제·사기 경고 */
    Danger(Tokens.DangerBg, Tokens.DangerText),

    /** 긴급·도움·쇼핑 포인트 */
    Help(Tokens.HelpSoft, Tokens.Help),

    /** 이동 */
    Violet(Tokens.VioletSoft, Tokens.VioletText),

    /** 지도·오프라인 */
    Teal(Tokens.TealSoft, Tokens.TealText),

    /** 준비 중·기타 */
    Neutral(Tokens.SurfaceSunken, Tokens.InkSecondary),

    /** 밝은 바탕 위 보안·보여 주기 강조 배지 */
    Navy(Tokens.Navy, Tokens.Surface),

    /** 어두운 채움(Accent·Navy·AccentDeep) 위의 장식 배지 — 흰 12% + Surface 아이콘 */
    OnDark(Tokens.White12, Tokens.Surface),
}

/**
 * 어두운 채움(Accent·Navy·AccentDeep·BrandBlue·사진 스크림) 위 글자·아이콘·테두리에 쓸 수 있는 색 (D18, onDark 내용 세트).
 * 이 밖의 색(Help·DangerText·InkTertiary·InkSecondary·Accent, BrandBlue 위 Gold)은 OnDarkPairsTest가 막는다.
 */
object OnDark {
    /** 제목·본문·아이콘·버튼 테두리 */
    val content: Color = Tokens.Surface

    /** eyebrow·출처 */
    val eyebrow: Color = Tokens.White85

    /** 보조 글자·셰브론 (Navy·AccentDeep 위) */
    val secondary: Color = Tokens.White80

    /** 여권 eyebrow (Navy·AccentDeep 위에서만) */
    val gold: Color = Tokens.Gold

    /** 바탕별 허용 색 (DESIGN_SPEC 3.1 표) */
    fun allowedOn(background: Color): Set<Color> = when (background) {
        Tokens.Accent -> setOf(Tokens.Surface, Tokens.White85)
        Tokens.Navy -> setOf(Tokens.Surface, Tokens.White80, Tokens.White85, Tokens.Gold)
        Tokens.AccentDeep -> setOf(Tokens.Surface, Tokens.White80, Tokens.White85, Tokens.Gold)
        Tokens.BrandBlue -> setOf(Tokens.Surface)
        else -> emptySet()
    }
}

/**
 * 여권 카드(24 — F 묶음 wallet.PassportCard)의 색: Navy → AccentDeep 세로 그라데이션 위 onDark 내용 세트만.
 * Gold는 Navy·AccentDeep 위에서만. 지우기 버튼은 카드 밖 별도 줄(일반 DangerButton)에 둔다(D18).
 */
@Immutable
data class PassportCardColors(
    val gradientTop: Color,
    val gradientBottom: Color,
    /** PASSPORT · 여권 eyebrow */
    val eyebrow: Color,
    /** eyebrow 앞 Badge 아이콘 */
    val icon: Color,
    /** 마스킹 값의 라벨 (bodySmall) */
    val label: Color,
    /** 마스킹 값 (titleLarge, tnum) */
    val value: Color,
)

fun passportCardColors(): PassportCardColors = PassportCardColors(
    gradientTop = Tokens.Navy,
    gradientBottom = Tokens.AccentDeep,
    eyebrow = OnDark.gold,
    icon = OnDark.gold,
    label = OnDark.secondary,
    value = OnDark.content,
)

/**
 * 카드·행 앞 아이콘 배지 (DESIGN_SPEC 4.2). 장식이라 TalkBack에서 숨긴다.
 * 크기는 dp(쉬운 모드 40→52)로만 커지고 글자 크기로는 커지지 않는다. 여러 줄 글 옆에서는 Row(Alignment.Top)로 위를 맞춘다.
 * [containerColor]: 배지가 놓인 바탕이 tone.container와 같은 색일 때(상태 카드, 강조 ChoiceCard) 흰 바탕 등으로 띄운다.
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Accent,
    size: Dp = LocalDimens.current.iconBadge,
    shape: Shape = MaterialTheme.shapes.small,
    containerColor: Color = tone.container,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(size * 0.55f))
    }
}
