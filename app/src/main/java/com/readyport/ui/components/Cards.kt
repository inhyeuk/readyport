package com.readyport.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/**
 * 기존 정보 카드 톤. 새 코드는 CardNewsCard·NoticeBanner를 먼저 쓴다 (DESIGN_SPEC 3.1·4.7).
 * - Neutral: 테두리 없이 부드러운 그림자 (3.5)
 * - Caution: 테두리 대신 왼쪽 4dp CautionBorder 막대
 * (옛 Notice 톤은 2단계에서 사용처 0을 확인하고 지웠다 — 안내 띠는 NoticeBanner)
 */
enum class CardTone(val container: Color, val content: Color, val border: Color?) {
    Neutral(Tokens.Surface, Tokens.Ink, null),
    Accent(Tokens.Accent, Tokens.Surface, null),
    Navy(Tokens.Navy, Tokens.Surface, null),
    Caution(Tokens.CautionBg, Tokens.CautionText, null),
}

/**
 * 흰 정보 카드 경계: 2dp 그림자(화면에 보이는 진하기 Ink 8%/12% — DESIGN_SPEC 3.5, 플랫폼 그림자 알파 보정은 Tokens.ShadowAmbient/Spot)
 * **+ 아주 옅은 1dp LineSoft 테두리**(운영자 결정 6, 2026-10-01). 그림자가 거의 안 보이는 화면·기기에서도 흰 카드가 Ground 위에서 떠 보인다.
 * 그림자 없는 상태 카드·채움 카드에는 쓰지 않는다.
 * [border] = false: 자기 테두리가 따로 있는 카드(추천 ChoiceCard 2dp Accent)나 사진이 가장자리까지 닿는 사진 머리 카드 —
 * 테두리는 내용 위에 그려지므로 사진 가장자리에 밝은 선이 생기지 않게 뺀다(3.5 '사진 카드: 테두리 없음').
 */
fun Modifier.cardShadow(shape: Shape, border: Boolean = true): Modifier = shadow(
    elevation = 2.dp,
    shape = shape,
    clip = false,
    ambientColor = Tokens.ShadowAmbient,
    spotColor = Tokens.ShadowSpot,
).then(if (border) Modifier.border(CardBorderWidth, Tokens.CardEdge, shape) else Modifier)

/** 흰 카드 테두리 두께 (운영자 결정 6) — 색은 Tokens.CardEdge(2026-10-09 진하게: 항목 구분) */
val CardBorderWidth = 1.dp

/** 상태 카드·배너 왼쪽(RTL이면 오른쪽) 색 막대. 바깥 모양(clip)이 모서리를 둥글게 자른다 */
fun Modifier.startBar(color: Color, width: Dp = 4.dp): Modifier = drawBehind {
    val w = width.toPx()
    val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - w
    drawRect(color = color, topLeft = Offset(x, 0f), size = Size(w, size.height))
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Neutral,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.large
    val shadow = tone == CardTone.Neutral
    val bar = if (tone == CardTone.Caution) Tokens.CautionBorder else null
    Card(
        modifier = modifier.fillMaxWidth().then(if (shadow) Modifier.cardShadow(shape) else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = tone.content),
        elevation = CardDefaults.cardElevation(0.dp),
        border = tone.border?.let { BorderStroke(1.dp, it) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (bar != null) Modifier.startBar(bar) else Modifier)
                .padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.gap / 2),
            content = content,
        )
    }
}

/** 제목 + 설명 카드. [comingSoon]이면 아직 만들지 않은 기능이라는 표시를 붙인다 (새 화면은 ComingSoonGroup) */
@Composable
fun TopicCard(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Neutral,
    comingSoon: Boolean = false,
) {
    InfoCard(modifier = modifier, tone = tone) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium)
        if (comingSoon) StatusChip(stringResource(R.string.coming_soon))
    }
}
