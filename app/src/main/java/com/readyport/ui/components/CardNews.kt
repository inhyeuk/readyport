package com.readyport.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.ReadyPortLineBreak
import com.readyport.ui.theme.Tokens

// ======================= 카드뉴스 부품 (DESIGN_SPEC 4.3, 4.6~4.8) =======================

/** 밝은 바탕 위에서 이 톤의 글자·아이콘 색 (Navy·OnDark 톤의 content는 흰색이라 밝은 바탕에는 쓰지 않는다) */
val BadgeTone.onLight: Color
    get() = when (this) {
        BadgeTone.Navy -> Tokens.Navy
        BadgeTone.OnDark -> Tokens.InkSecondary
        else -> content
    }

/**
 * 섹션 머리 (다듬기 D0 — 재검토2 ①#2 '섹션 머리 = 카드 제목' 해결): **배지 없이** 24dp 아이콘(tone 글자색, 글자를 따라 커짐) +
 * 큰 굵은 제목(headlineSmall 22/30 Bold, 쉬운 모드 24/32). 카드 제목(CardNewsCard)은 한 단계 작은 titleMedium SemiBold + 40dp 배지라
 * 긴 화면에서 '섹션 → 카드' 두 단이 갈려 읽힌다. 아이콘이 없는 섹션 머리(`어느 나라로 가세요?`)도 같은 글자.
 * 위 여백 없음 — 섹션 간격은 sectionGap()이 맡는다.
 * [action]은 옆에 두면 제목·부제가 더 꺾일 만큼 폭이 모자라면 제목 아래 줄로 내려간다(4.3 — 글자 크기와 무관하게 실제 폭 기준).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: BadgeTone = BadgeTone.Accent,
    eyebrow: String? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val typography = MaterialTheme.typography
    val titleStyle = typography.headlineSmall
    val iconSize = textIconSize(LocalDimens.current.icon, titleStyle)
    // 아이콘은 제목 첫 줄 가운데에 (eyebrow가 있으면 그 줄 아래)
    val eyebrowShift = if (eyebrow != null) lineHeightDp(typography.labelMedium) + 2.dp else 0.dp
    val texts: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (eyebrow != null) KoText(eyebrow, typography.labelMedium, color = tone.onLight)
            KoText(title, titleStyle, color = Tokens.Ink, heading = true, glueShort = true)
            if (subtitle != null) KoText(subtitle, typography.bodyMedium, color = Tokens.InkSecondary)
        }
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = tone.onLight,
                modifier = Modifier.padding(top = eyebrowShift + firstLineIconOffset(titleStyle, iconSize)).size(iconSize),
            )
        }
        if (action != null) {
            TrailingFlow(trailing = action, modifier = Modifier.weight(1f), belowGap = 4.dp, main = texts)
        } else {
            Box(Modifier.weight(1f)) { texts() }
        }
    }
}

/** 글자 스타일의 줄 높이(dp) — 시스템 글자 크기를 따른다 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun lineHeightDp(style: TextStyle): Dp = with(LocalDensity.current) {
    if (style.lineHeight.isSp) style.lineHeight.toDp() else style.fontSize.toDp()
}

/**
 * 카드뉴스 카드 스타일.
 * Surface: 흰 바탕 + 그림자 / SurfaceCaution: 흰 바탕 + 왼쪽 CautionBorder 막대(그림자 없음, 17 직접 입력)
 * Accent·Navy: 채움(onDark 내용 세트만) / Caution·Danger: 연한 바탕 + 왼쪽 막대
 */
enum class NewsStyle { Surface, SurfaceCaution, Accent, Navy, Caution, Danger }

/** CardNewsCard의 색 선택 (OnDarkPairsTest가 직접 호출해 허용 색인지 검사한다) */
@Immutable
data class NewsColors(
    val container: Color,
    val title: Color,
    val body: Color,
    val eyebrow: Color,
    val badge: BadgeTone,
    val bar: Color?,
    val shadow: Boolean,
    /** 출처 줄을 White85로 */
    val onColor: Boolean,
    /**
     * 배지 바탕. 보통은 badge.container지만, 상태 카드(Caution·Danger)는 카드 바탕과 배지 바탕이 같은 색이라
     * 배지가 사라지므로 흰 바탕(Surface)으로 띄운다.
     */
    val badgeContainer: Color = badge.container,
)

fun newsColors(style: NewsStyle, tone: BadgeTone = BadgeTone.Accent): NewsColors = when (style) {
    NewsStyle.Surface -> NewsColors(Tokens.Surface, Tokens.Ink, Tokens.Ink, tone.onLight, tone, null, shadow = true, onColor = false)
    NewsStyle.SurfaceCaution -> NewsColors(Tokens.Surface, Tokens.Ink, Tokens.Ink, tone.onLight, tone, Tokens.CautionBorder, shadow = false, onColor = false)
    // eyebrow는 White85 — tone.content(Accent)를 쓰면 Accent 카드 위에서 1.0:1로 사라진다
    NewsStyle.Accent -> NewsColors(Tokens.Accent, OnDark.content, OnDark.content, OnDark.eyebrow, BadgeTone.OnDark, null, shadow = false, onColor = true)
    NewsStyle.Navy -> NewsColors(Tokens.Navy, OnDark.content, OnDark.content, OnDark.eyebrow, BadgeTone.OnDark, null, shadow = false, onColor = true)
    NewsStyle.Caution -> NewsColors(
        Tokens.CautionBg, Tokens.Ink, Tokens.Ink, Tokens.CautionText, BadgeTone.Caution, Tokens.CautionBorder,
        shadow = false, onColor = false, badgeContainer = Tokens.Surface,
    )
    NewsStyle.Danger -> NewsColors(
        Tokens.DangerBg, Tokens.Ink, Tokens.Ink, Tokens.DangerText, BadgeTone.Danger, Tokens.DangerText,
        shadow = false, onColor = false, badgeContainer = Tokens.Surface,
    )
}

/**
 * 카드뉴스 카드: 머리(배지 + eyebrow + 제목) → body → content(FactGrid·StepList·IconBullet·버튼) → 출처(항상 맨 아래, 접힘 밖).
 * Accent·Navy 스타일 안에는 DangerButton을 두지 않는다(D18) — 주 버튼은 colors = ButtonStyles.onDark(), 보조는 SecondaryButton(onDark = true).
 */
@Composable
fun CardNewsCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    body: String? = null,
    tone: BadgeTone = BadgeTone.Accent,
    style: NewsStyle = NewsStyle.Surface,
    sources: List<SourceRef> = emptyList(),
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val dimens = LocalDimens.current
    val c = newsColors(style, tone)
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = modifier.fillMaxWidth().then(if (c.shadow) Modifier.cardShadow(shape) else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = c.container, contentColor = c.body),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (c.bar != null) Modifier.startBar(c.bar) else Modifier)
                .padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.inner),
        ) {
            val head: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (eyebrow != null) KoText(eyebrow, MaterialTheme.typography.labelMedium, color = c.eyebrow)
                    // 카드 제목 = titleMedium SemiBold — 섹션 머리(headlineSmall Bold, 배지 없음)보다 한 단계 작게 (재검토2 ①#2)
                    KoText(title, MaterialTheme.typography.titleMedium, color = c.title, heading = true, glueShort = true)
                }
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(icon, tone = c.badge, containerColor = c.badgeContainer)
                // trailing(예: StatusTag)이 제목을 쪼갤 만큼 폭이 모자라면 제목 아래 줄로 (4.7, 7장 7번)
                if (trailing != null) {
                    TrailingFlow(trailing = trailing, modifier = Modifier.weight(1f), main = head)
                } else {
                    // 한 줄 제목은 40dp 배지 가운데에 (두 줄 이상이면 위 맞춤)
                    Box(Modifier.weight(1f).heightIn(min = LocalDimens.current.iconBadge), contentAlignment = Alignment.CenterStart) { head() }
                }
            }
            // 팩 문장일 수 있다 — 숫자 토큰은 굵게(값은 그대로, 재검토2 ③#1)
            if (body != null) NumberText(body, MaterialTheme.typography.bodyLarge, color = c.body)
            content()
            if (sources.isNotEmpty()) {
                Box(Modifier.padding(top = 4.dp)) { SourceList(sources, onColor = c.onColor) }
            }
        }
    }
}

// ---------------- 숫자 타일 ----------------

/** 큰 값 하나. [source]: 이 값이 나온 출처(카드 SourceList에 모은다) */
@Immutable
data class Fact(
    val icon: ImageVector,
    val value: String,
    val label: String,
    val tone: BadgeTone = BadgeTone.Accent,
    val source: SourceRef? = null,
)

/** 여러 Fact의 출처 (null 제외). 카드 sources에 req.source와 함께 넘기면 SourceList가 중복을 없앤다 */
fun List<Fact>.sourceRefs(): List<SourceRef> = mapNotNull { it.source }

/** 통화 코드가 앞에 붙은 금액 (`IDR 500,000`) — StatTile이 코드와 숫자를 나눠 그린다 */
private val CurrencyAmount = Regex("""([A-Z]{3})\s+(\d[\d,.]*)""")

/**
 * 숫자 타일 (누를 수 없음 — 누르는 요약은 IconTile). 읽기: "90일 비자 없이 머물러요". Ground 바탕 · 16dp 모서리 · 안쪽 16 · 아이콘(톤 색) → 값 → 라벨.
 * - 값은 **칸 폭에 맞춘 한 줄**([FitText]: stat → statSmall → titleLarge — 쉬운 모드 최소 24sp). `IDR 500,000`·`4박 5일`이
 *   `IDR`/`500,000`처럼 두 줄로 쪼개지지 않는다(재검토 R12). 아주 좁아 가장 작은 크기로도 넘치면 띄어쓰기에서만 줄을 바꾼다.
 * - 통화 코드가 붙은 금액은 코드를 값 위 작은 글자(labelMedium)로 올리고 숫자만 크게 — 화면은 `IDR`⏎`500,000`,
 *   TalkBack·테스트는 `IDR 500,000` 한 덩어리 그대로.
 * - [wide](폭 전체 타일 — 1열·혼자·홀수 마지막): 가로형 아이콘 + (값 / 라벨 한 줄). 2열용 라벨의 줄바꿈(`비자 없이⏎머물러요`)은 넓은 칸에서 한 줄로.
 * - 아이콘은 글자 크기를 따라 커진다(최대 1.5배) — 200%에서 큰 숫자 옆에서 점처럼 작아지지 않게.
 */
@Composable
fun StatTile(fact: Fact, modifier: Modifier = Modifier, wide: Boolean = false) {
    val dimens = LocalDimens.current
    val extras = LocalTypeExtras.current
    val typography = MaterialTheme.typography
    val styles = listOf(extras.stat, extras.statSmall, typography.titleLarge)
    val raw = fact.value.trim()
    val currency = CurrencyAmount.matchEntire(raw)
    val value: @Composable () -> Unit = {
        if (currency != null) {
            Column(Modifier.clearAndSetSemantics { text = AnnotatedString(raw) }) {
                Text(currency.groupValues[1], style = typography.labelMedium, color = Tokens.InkSecondary)
                FitText(currency.groupValues[2], styles, Tokens.Ink, breakChars = " ")
            }
        } else {
            FitText(raw, styles, Tokens.Ink, breakChars = " ")
        }
    }
    val iconSize = textIconSize(dimens.icon, typography.titleLarge)
    val icon: @Composable () -> Unit = {
        Icon(fact.icon, contentDescription = null, tint = fact.tone.onLight, modifier = Modifier.size(iconSize))
    }
    // 바탕은 Ground(회청) 하나 — AccentSoft 채움은 '선택됨'에만 쓴다(재검토2 ①#1). 톤 색은 아이콘이 맡는다.
    // 타일은 흰 카드 안에만 놓인다(비자 카드·정리 단계 사진 카드) — Ground 화면 바탕 위에 바로 두지 않는다.
    Surface(
        color = StatTileContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (wide) dimens.tileRowMinHeight else dimens.tileMinHeight)
            .semantics(mergeDescendants = true) {},
    ) {
        if (wide) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                icon()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    value()
                    NumberText(fact.label.replace('\n', ' '), typography.bodyMedium, color = Tokens.InkSecondary)
                }
            }
        } else {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                icon()
                value()
                NumberText(fact.label, typography.bodyMedium, color = Tokens.InkSecondary)
            }
        }
    }
}

/** StatTile 바탕 (InkSecondary 6.98·Accent 6.22 — Ground 위 대비) */
val StatTileContainer: Color = Tokens.Ground

/**
 * 숫자 타일 그리드. **팩의 구조화 필드에서만** 값을 만든다(D11). 타일이 2개 미만이면 그리지 않는다 — 호출하는 쪽이 글로(또는 [StatTile] `wide` 한 장으로) 보인다.
 * 순서는 넘겨받은 그대로(긴 값을 골라 옮기면 순서가 바뀌어 잘못 읽힌다). 1열이면 가로형 타일을 쌓고, 2열에서 타일 수가 홀수면
 * 남는 칸을 비우지 않고 **마지막 타일**을 맨 아래 폭 전체 가로형으로 놓는다(재검토 R12).
 * 바로 아래(카드 안)에 출처를 꼭 둔다: 카드 sources = req.source + facts.sourceRefs().
 */
@Composable
fun FactGrid(facts: List<Fact>, modifier: Modifier = Modifier, columns: Int = rememberGridColumns()) {
    if (facts.size < 2) return
    if (columns == 2 && facts.size % 2 == 1) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
            TileGrid(facts.dropLast(1), columns = columns) { fact, cell -> StatTile(fact, cell) }
            StatTile(facts.last(), wide = true)
        }
    } else {
        TileGrid(facts, modifier, columns) { fact, cell -> StatTile(fact, cell, wide = columns == 1) }
    }
}

/**
 * 누를 수 없는 정보 칩 (재검토 R1): 아이콘 + 글자만 — **채움·테두리 없음**. 누를 수 있는 칩·tonal 버튼(채움 + 테두리)과 한눈에 구분된다.
 * 홈 신뢰 표시(사진 위 — [onDark]), 꼭 챙길 물건 주제(플러그·전압·보조배터리), 귀국 전 확인 주제, 사실 칩(FactChip)이 모두 이 모양이다.
 * - [value]: 굵게 보일 값(있으면 `값 라벨` 순서, 없으면 [text]만 보통 굵기)
 * - [tone]: 아이콘 색(밝은 바탕에서 tone.onLight). 글자는 Ink(값)·InkSecondary(라벨)
 * - [onDark]: 어두운 채움·사진 스크림 위 — 아이콘·글자 모두 Surface(onDark 내용 세트)
 * - [textStyle]: 글자 스타일(기본 labelLarge — 사진 위 신뢰 표시처럼 작게 둘 때 labelMedium)
 * TalkBack은 칩 하나를 한 번에 읽는다(mergeDescendants). 글이 길면 칩 안에서 줄을 바꾼다(아이콘은 첫 줄에 맞춤).
 */
@Composable
fun InfoChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    value: String? = null,
    tone: BadgeTone = BadgeTone.Neutral,
    onDark: Boolean = false,
    textStyle: TextStyle? = null,
) {
    val valueStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val textStyle = textStyle ?: if (value != null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelLarge
    val firstStyle = if (value != null) valueStyle else textStyle
    val iconSize = textIconSize(LocalDimens.current.iconSmall + 4.dp, firstStyle)
    val iconColor = if (onDark) OnDark.content else tone.onLight
    val valueColor = if (onDark) OnDark.content else Tokens.Ink
    val textColor = if (onDark) OnDark.content else Tokens.InkSecondary
    Row(
        modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.padding(top = firstLineIconOffset(firstStyle, iconSize)).size(iconSize),
        )
        if (value != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KoText(value, valueStyle, color = valueColor)
                if (text.isNotEmpty()) KoText(text, textStyle, Modifier.align(Alignment.Bottom), color = textColor)
            }
        } else {
            KoText(text, textStyle, color = if (onDark) OnDark.content else Tokens.InkSecondary)
        }
    }
}

/** 한 줄 사실(값 + 라벨) — [InfoChip] 모양(채움 없음, 누를 수 없음). 라벨이 비면 값만 */
@Composable
fun FactChip(fact: Fact, modifier: Modifier = Modifier) {
    if (fact.label.isEmpty()) {
        InfoChip(fact.value, fact.icon, modifier, tone = fact.tone)
    } else {
        InfoChip(fact.label, fact.icon, modifier, value = fact.value, tone = fact.tone)
    }
}

/**
 * 팩 문장에서 타일에 넣을 짧은 값 (DESIGN_SPEC 4.6).
 * 1) 12자 이하면 그대로 2) " · " 앞부분 또는 첫 문장(". " 앞) 중 먼저 끊기는 쪽이 14자 이하면 그 부분 3) 아니면 null(→ 글 행).
 * ","에서는 절대 자르지 않는다 ("IDR 500,000 · …"가 "IDR 500"이 되면 정책 값 왜곡).
 */
fun shortValue(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return null
    if (t.length <= 12) return t
    val cut = listOf(t.indexOf(" · "), t.indexOf(". ")).filter { it > 0 }.minOrNull() ?: return null
    return t.substring(0, cut).trim().takeIf { it.isNotEmpty() && it.length <= 14 }
}

/** 비용 아이콘: 값이 정확히 "무료"일 때만 MoneyOff, 나머지는 Payments */
fun feeIcon(value: String): ImageVector = if (value == "무료") Icons.Outlined.MoneyOff else Icons.Outlined.Payments

// ---------------- 단계 목록 ----------------

/** 단계 하나. 팩 단계 순서에 앱이 뜻(직접 해요 등)을 붙이지 않는다(D11) */
@Immutable
data class Step(val text: String, val icon: ImageVector? = null, val detail: String? = null)

/**
 * 글자를 품는 원(번호 원·이니셜 아바타). 고정 크기 원 대신 글자가 크기를 정한다 —
 * [minSize]보다 크면 가로·세로 중 큰 값으로 정사각형을 맞춘다(200%에서도 숫자가 넘치거나 잘리지 않음).
 */
@Composable
fun TextCircle(
    text: String,
    modifier: Modifier = Modifier,
    minSize: Dp = LocalDimens.current.stepBadge,
    container: Color = Tokens.Accent,
    content: Color = Tokens.Surface,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Box(
        modifier = modifier
            .background(container, CircleShape)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val side = maxOf(placeable.width, placeable.height, minSize.roundToPx())
                layout(side, side) { placeable.place((side - placeable.width) / 2, (side - placeable.height) / 2) }
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = content, textAlign = TextAlign.Center)
    }
}

/**
 * 번호 원 + 세로 연결선 + 단계 글. 번호는 원 안에만(D17) — 문자열에 "N. "을 넣지 않는다.
 * 행마다 mergeDescendants라 TalkBack은 "1 공항에 가요"처럼 한 번에 읽는다. 높이는 글에 맞춘다.
 * - 단계 글은 짧은 제목이든 문장이든 **본문 줄바꿈**(들어가는 만큼 채움, 어절 단위 — [ReadyPortLineBreak.Body])으로 그린다.
 *   제목용 균형 줄바꿈은 오른쪽에 자리가 남아도 일찍 꺾어 `'오프라인 지도'` 같은 화면 이름까지 갈랐다(재검토 B1).
 * - 글자 모양은 **부품이 단계 글 길이로 고른다**(다듬기 D0 — 재검토2 ④#2): 모든 단계가 짧은 제목(20자 이하·문장부호 없음 —
 *   홈 출국 순서 `공항에 가요`)이면 굵은 제목 글자(titleMedium), 하나라도 문장이면 목록 전체를 본문 글자(bodyLarge, Ink)로 —
 *   번호 원만 강조해 굵은 글 벽(04 e-VOA 단계, 05 지도 저장, 24 도움 절차)을 만들지 않는다. [sentence]로 직접 정할 수도 있다(null = 자동).
 *   문장형 글과 보조 글은 숫자 토큰을 굵게(팩 문장 — 재검토2 ③#1).
 */
@Composable
fun StepList(steps: List<Step>, modifier: Modifier = Modifier, numbered: Boolean = true, sentence: Boolean? = null) {
    val dimens = LocalDimens.current
    val typography = MaterialTheme.typography
    val asSentence = sentence ?: steps.any { isSentenceStep(it.text) }
    val textStyle = (if (asSentence) typography.bodyLarge else typography.titleMedium).copy(lineBreak = ReadyPortLineBreak.Body)
    val iconSize = textIconSize(if (dimens.easyMode) 24.dp else 20.dp, textStyle)
    val iconTop = firstLineIconOffset(textStyle, iconSize)
    // 큰 글자 배치에서는 단계 아이콘을 빼지 않고 글 첫 줄 안(맨 앞)으로 옮긴다 — 아이콘 열이 글 폭을 뺏지 않게 (재검토 R5)
    val inlineIcons = isStackedLayout()
    Column(modifier.fillMaxWidth()) {
        steps.forEachIndexed { i, step ->
            val last = i == steps.lastIndex
            StepRow(
                badge = {
                    if (numbered) {
                        TextCircle("${i + 1}")
                    } else {
                        Box(Modifier.padding(top = 8.dp).size(12.dp).background(Tokens.Accent, CircleShape))
                    }
                },
                showLine = !last,
                minBadge = dimens.stepBadge,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                // 보조 글(detail)은 아이콘 열이 아니라 단계 글 시작선에 맞춘다 (BUNDLE_A_NOTES 요청 3)
                Row(
                    Modifier.padding(top = 2.dp, bottom = if (last) 0.dp else dimens.gap),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!inlineIcons && step.icon != null) {
                        Icon(step.icon, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.padding(top = iconTop).size(iconSize))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (inlineIcons && step.icon != null) {
                            LeadIconText(step.text, step.icon, textStyle, Tokens.Ink, Tokens.Accent)
                        } else if (asSentence) {
                            NumberText(step.text, textStyle, color = Tokens.Ink)
                        } else {
                            KoText(step.text, textStyle, color = Tokens.Ink)
                        }
                        if (step.detail != null) {
                            NumberText(step.detail, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** 단계 제목 글자로 둘 수 있는 길이 (이보다 길거나 문장부호가 있으면 문장형) */
private const val STEP_TITLE_MAX = 20

/** 단계 글이 문장인지: 20자를 넘거나 문장부호(. ! ? :)가 있으면 문장 (재검토2 ④#2) */
internal fun isSentenceStep(text: String): Boolean {
    val t = text.trim()
    return t.length > STEP_TITLE_MAX || t.any { it in ".!?:" }
}

/**
 * 순서 머리 한 줄 (다듬기 S — 04 나라 입국의 입국 카드→비자 순서, 21 값 복사해서 넣기의 사이트 단계):
 * [StepList]와 같은 번호 원([TextCircle] — 글자 따라 커짐) + 짧은 글(titleMedium Ink). 카드 바로 위에 붙여
 * `1단계 · …` 글자 eyebrow 대신 순서를 보인다(재검토2 ③#3·③#9 — 2단계도 Success가 아니라 Accent 하나).
 * 공용 카드(EntryFormCard·CardNewsCard·InfoCard)에 배지 자리가 없어 카드 밖 머리로 둔다.
 * 글 끝 한 음절 낱말은 앞 낱말에 붙인다(glueShort — 큰 글자에서 `내/요` 같은 홀로 남은 음절 방지).
 * TalkBack: 한 덩어리 제목 `1단계 · {글}`(country_step_eyebrow).
 */
@Composable
fun StepHead(number: Int, text: String, modifier: Modifier = Modifier) {
    val a11y = stringResource(R.string.country_step_eyebrow, number, text)
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = a11y
                heading()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
    ) {
        TextCircle("$number")
        KoText(text, MaterialTheme.typography.titleMedium, Modifier.weight(1f), color = Tokens.Ink, glueShort = true)
    }
}

/**
 * 단계 한 줄: 왼쪽 배지(가운데 정렬, 최소 폭 [minBadge]) + 배지 아래에서 줄 끝까지 이어지는 2dp 세로선 + 오른쪽 글.
 * 높이는 글이 정한다. (IntrinsicSize를 쓰면 weight가 걸린 글의 고유 높이가 지나치게 크게 계산돼 직접 잰다)
 */
@Composable
private fun StepRow(
    badge: @Composable () -> Unit,
    showLine: Boolean,
    minBadge: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(badge, { if (showLine) Box(Modifier.background(Tokens.Line)) }, content),
        modifier = modifier.fillMaxWidth(),
    ) { (badgeMeasurables, lineMeasurables, contentMeasurables), constraints ->
        val gap = 12.dp.roundToPx()
        val lineGap = 2.dp.roundToPx()
        val badgePlaceable = badgeMeasurables.firstOrNull()?.measure(Constraints())
        val badgeW = badgePlaceable?.width ?: 0
        val badgeH = badgePlaceable?.height ?: 0
        val leftW = maxOf(badgeW, minBadge.roundToPx())
        val contentW = if (constraints.hasBoundedWidth) (constraints.maxWidth - leftW - gap).coerceAtLeast(0) else Constraints.Infinity
        val contentPlaceable = contentMeasurables.firstOrNull()?.measure(Constraints(maxWidth = contentW))
        val height = maxOf(badgeH, contentPlaceable?.height ?: 0)
        val lineH = (height - badgeH - lineGap * 2).coerceAtLeast(0)
        val linePlaceable = if (lineH > 0) lineMeasurables.firstOrNull()?.measure(Constraints.fixed(2.dp.roundToPx(), lineH)) else null
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else leftW + gap + (contentPlaceable?.width ?: 0)
        layout(width, height) {
            badgePlaceable?.placeRelative((leftW - badgeW) / 2, 0)
            linePlaceable?.placeRelative((leftW - linePlaceable.width) / 2, badgeH + lineGap)
            contentPlaceable?.placeRelative(leftW + gap, 0)
        }
    }
}

/** 연한 바탕 행이 글자 줄을 밀지 않도록 바탕만 양옆으로 내미는 폭 */
private val BulletBleed = 12.dp

/**
 * "• 문장"을 대체하는 아이콘 행. Caution·Danger 톤이면 행 바탕을 연하게 —
 * 이때 바탕은 양옆으로 12dp 내밀어(카드 안쪽 여백 안) 아이콘·글자 위치가 다른 행과 같은 줄에 선다.
 * 글은 팩 문장일 수 있어 숫자 토큰을 굵게 보인다(값은 그대로, 재검토2 ③#1). [display]: 보일 글자(날짜 묶음 등 — 의미 글자는 [text]).
 */
@Composable
fun IconBullet(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
    display: String? = null,
) {
    val dimens = LocalDimens.current
    val tinted = tone == BadgeTone.Caution || tone == BadgeTone.Danger
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (tinted) {
                    Modifier
                        .layout { measurable, constraints ->
                            val bleed = BulletBleed.roundToPx()
                            val wide = if (constraints.hasBoundedWidth) {
                                constraints.copy(minWidth = constraints.minWidth + bleed * 2, maxWidth = constraints.maxWidth + bleed * 2)
                            } else {
                                constraints
                            }
                            val p = measurable.measure(wide)
                            val w = if (constraints.hasBoundedWidth) p.width - bleed * 2 else p.width
                            layout(w.coerceAtLeast(0), p.height) { p.placeRelative(-bleed, 0) }
                        }
                        .clip(MaterialTheme.shapes.small)
                        .background(tone.container)
                        .padding(horizontal = BulletBleed, vertical = 8.dp)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.Top,
    ) {
        val style = MaterialTheme.typography.bodyLarge
        val size = textIconSize(if (dimens.easyMode) 24.dp else 20.dp, style)
        Icon(
            icon,
            contentDescription = null,
            tint = tone.onLight,
            modifier = Modifier.padding(top = firstLineIconOffset(style, size)).size(size),
        )
        Spacer(Modifier.width(12.dp))
        NumberText(text, style, Modifier.weight(1f), color = Tokens.Ink, display = display)
    }
}

/**
 * 팩 문장 목록의 불릿 (재검토 R8): 뜻 없는 6dp 점(InkTertiary) 하나 — 앱은 팩 문장의 뜻(허용·금지)을 추측해 기호를 고르지 않는다.
 * ✓(Check)는 앱이 확인한 상태(챙겼어요·확인 완료)에만 쓰고, 금지·경고 문장 앞에는 절대 두지 않는다. 대시(—)도 쓰지 않는다.
 * 점은 글 **첫 줄 가운데**에 맞춘다. 글자는 IconBullet과 같은 bodyLarge·같은 시작선(점 칸 폭 = IconBullet 아이콘 폭).
 */
@Composable
fun DotBullet(text: String, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    val style = MaterialTheme.typography.bodyLarge
    val slot = textIconSize(if (dimens.easyMode) 24.dp else 20.dp, style)
    val dot = 6.dp
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(slot, lineHeight), contentAlignment = Alignment.Center) {
            Box(Modifier.size(dot).background(Tokens.InkTertiary, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        // 팩 문장 — 숫자 토큰 굵게(`20·50·100·500·1,000밧`, `미화 1만 5천 달러`, 재검토2 ③#1)
        NumberText(text, style, Modifier.weight(1f), color = Tokens.Ink)
    }
}

/**
 * 펼침 영역. 출처는 절대 이 안에 넣지 않는다. TalkBack: 버튼 + 상태(펼쳐짐/접힘).
 * '소리로 듣기'는 접힘과 무관하게 전체 문장을 읽는다(speech 문자열은 그대로).
 * [target]: 무엇을 펼치는지(`보조배터리 설명`) — 펼친 뒤 `접기` 버튼의 TalkBack 이름이 `보조배터리 설명 접기`가 된다(재검토2 ②#2).
 * 펼쳐 나온 내용은 TalkBack이 바로 읽는다(liveRegion Polite) — 버튼 아래에 늘어난 글을 찾아 쓸어 넘기지 않아도 된다.
 */
@Composable
fun ExpandableDetail(
    label: String = stringResource(R.string.action_more),
    target: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ExpandableDetail(open = open, onOpenChange = { open = it }, label = label, target = target, content = content)
}

/** 펼침 상태를 밖에서 쥐는 판 (입국 카드 확인의 출처별 값 묶음처럼 화면이 상태를 기억할 때) */
@Composable
fun ExpandableDetail(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    label: String,
    target: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = LocalDimens.current
    Column(Modifier.fillMaxWidth()) {
        ExpandToggle(open = open, onOpenChange = onOpenChange, label = label, target = target)
        // 늘 있는 상자에 liveRegion — 내용이 나타나면 그 바뀜을 알린다
        Column(Modifier.fillMaxWidth().foldLiveRegion()) {
            AnimatedVisibility(visible = open) {
                Column(Modifier.padding(top = dimens.inner), verticalArrangement = Arrangement.spacedBy(dimens.inner), content = content)
            }
        }
    }
}

/**
 * 펼침·접기 줄 하나 (minTouch, 버튼 + 펼쳐짐/접힘 상태) — 아래에 내용을 더하는 [ExpandableDetail]과, 위 글을 첫 문장 ↔ 전체로
 * 바꾸는 카드(귀국 전 확인·나라 화면 팩 글)가 함께 쓴다. 글을 바꾸는 쪽은 바뀌는 글 묶음에 [foldLiveRegion]을 둔다
 * (바뀐 글이 버튼 **위**에 있어 TalkBack 초점이 따라가지 않던 문제 — 재검토2 ②#2).
 * - 보이는 글: 접힘 [label], 펼침 `접기`(action_less)
 * - TalkBack 이름: 접힘 [closedName](없으면 보이는 글), 펼침 `{target} 접기`([target]이 있을 때 — 없으면 보이는 글)
 */
@Composable
fun ExpandToggle(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    target: String? = null,
    closedName: String? = null,
) {
    val dimens = LocalDimens.current
    val state = stringResource(if (open) R.string.state_expanded else R.string.state_collapsed)
    val shown = if (open) stringResource(R.string.action_less) else label
    val name = if (open) target?.let { stringResource(R.string.collapse_target_cd, it) } else closedName
    Row(
        modifier
            .fillMaxWidth()
            .minTouch()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = open, role = Role.Button, onValueChange = onOpenChange)
            .semantics {
                stateDescription = state
                if (name != null) contentDescription = name
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KoText(shown, MaterialTheme.typography.labelLarge, Modifier.weight(1f), color = Tokens.Accent)
        Icon(
            if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = Tokens.Accent,
            modifier = Modifier.size(textIconSize(dimens.icon, MaterialTheme.typography.labelLarge)),
        )
    }
}

/** 펼침·접기로 글이 바뀌는 묶음: 바뀌면 TalkBack이 새 글을 읽는다(liveRegion Polite, 재검토2 ②#2) */
fun Modifier.foldLiveRegion(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }
