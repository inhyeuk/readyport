package com.readyport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 화면 틀 탭 줄 (DESIGN_SPEC 4.18 — 나라 화면 길잡이 v3) =======================
// 탭(아래 내용을 바꾸는 화면 틀)과 칩·세그먼트(값을 고르는 조작)는 **모양이 달라야 한다**:
// - 탭([SectionTabs]) = 밑줄 표시. 선택 칸은 2~3dp Accent 밑줄 + Accent 굵은 글자, 채움 없음.
// - 고르기(SelectChip·ChoiceSegments·SelectableCard, Controls.kt 규칙 ①②) = Accent 채움 + 흰 글자 + Check.
// 아래 내용을 바꾸는 탭에 '고른 칩' 모양(Accent 채움)을 쓰면 탭과 내용 속 선택지가 같은 모양이 되어
// 지금 어느 갈래에 있는지 읽히지 않는다(운영자 지적 2026-10-02). ChoiceSegments는 영상 정렬·예약 가져오기처럼
// **값을 고르는 곳**에 그대로 둔다.

/** 탭 칸 좌우 안쪽 여백 */
private val TabCellPadding = 6.dp

/** 탭 칸 위아래 안쪽 여백 */
private val TabCellVertical = 10.dp

/** 선택 표시 밑줄 두께 (3.4 비텍스트 대비: Accent on Surface 6.78) */
private val TabUnderline = 3.dp

/** 아이콘과 글자 사이 */
private val TabIconGap = 6.dp

/** 라벨 폭을 잴 때 두는 여유 (글자 사이 간격·반올림) */
private val TabFitSlack = 2.dp

/** 탭 줄 아래 옅은 그림자 높이 (내용이 줄 아래로 지나간다는 표시) */
private val TabBarShadow = 6.dp

/** 고정 줄 그림 썸네일 지름 (글자가 커지면 함께 커진다 — textIconSize) */
private val TabThumb = 30.dp

/** 썸네일 원 지름 대비 그림 크기 (원 안에 들어가고 반짝이 끝만 살짝 닿는다) */
private const val TAB_THUMB_ART = 0.92f

/** 탭 줄 아래 그림자 진하기 (화면상 Ink 7% — 직접 그린다: 플랫폼 그림자 알파에 흔들리지 않게) */
private const val TAB_SHADOW_ALPHA = 0.07f

/**
 * 탭 칸이 라벨을 그리는 단계 (좁아질수록 아래로 — 글자를 자르거나 가로 스크롤하지 않는다).
 * ① [IconLabel] 아이콘 + 전체 라벨 한 줄 ② [Label] 아이콘을 빼고 전체 라벨 한 줄
 * ③ [Short] 짧은 라벨 한 줄(`입국·비자` → `입국`) ④ [Fit] 짧은 라벨을 [FitText]로 줄여 한 줄(감사 최소 글자까지),
 * 그래도 안 들어가면 FitText가 **띄어쓰기 자리에서만** 줄을 바꾼다(음절 사이에서 끊기지 않는다).
 */
private enum class TabFit { IconLabel, Label, Short, Fit }

/**
 * 화면 머리에 붙는 **탭 줄**: 누르면 아래 내용이 바뀐다(스와이프 없음). 어떤 모드에서도 **가로 한 줄**이다 —
 * 쉬운 모드·글자 200%·360dp·기기 언어 영어에서도 세로 목록이나 가로 스크롤로 바뀌지 않는다(그래야 '지금 어느 갈래'가 늘 보인다).
 * - 선택 표시 = 칸 아래 [TabUnderline] Accent 밑줄 + Accent 굵은 글자. Accent 채움(고른 칩 모양)은 쓰지 않는다(이 파일 머리말).
 * - 좁거나 글자가 커지면 [TabFit] 단계로 내려간다. 라벨 폭은 실제 글자 폭(TextMeasurer)으로 재고 **모든 칸이 같은 단계**를 쓴다
 *   (칸마다 아이콘이 있고 없으면 줄이 들쭉날쭉해진다).
 * - 보이는 글자가 짧은 라벨로 바뀌어도 TalkBack·테스트가 읽는 의미 글자는 언제나 [label] 전체 글이다([KoText]의 display).
 * - a11y: 부모 `selectableGroup()` + 칸마다 `selectable(role = Role.Tab)`(선택 상태를 읽어 준다) + `minTouch()`.
 * [shortLabel]: 좁을 때 쓸 짧은 라벨(기본은 [label]과 같다). [icon]: 칸 아이콘(null이면 글자만).
 */
@Composable
fun <T> SectionTabs(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    shortLabel: @Composable (T) -> String = label,
    icon: (T) -> ImageVector? = { null },
    art: (T) -> SectionArt? = { null },
) {
    if (options.isEmpty()) return
    val dimens = LocalDimens.current
    val typography = MaterialTheme.typography
    val base = typography.labelLarge
    // 줄이는 단계의 마지막 값이 그 모드의 최소 글자 크기다 (쉬운 모드 18sp = PRD 3.2 하한, 기본 모드 13sp)
    val styles = remember(base, typography.labelMedium) { listOf(base, typography.labelMedium) }
    // 폭은 **선택 칸(굵게)** 기준으로 잰다 — 굵은 글자가 더 넓어서, 보통 굵기로 재면 고른 칸만 칸 밖으로 넘친다
    val widest = remember(base) { base.copy(fontWeight = FontWeight.Bold) }
    val measurer = rememberTextMeasurer(cacheSize = options.size * 2 + 2)
    val density = LocalDensity.current
    val labels = options.map { label(it) }
    val shorts = options.map { shortLabel(it) }
    val arts = options.map { art(it) }
    val icons = options.map { icon(it) }
    // 그림 썸네일이 있으면 아이콘 대신 둥근 썸네일(길잡이 v4) — 고정 줄도 위 그림 메뉴와 같은 그림을 쓴다
    val thumbs = arts.all { it != null }
    val iconSize = if (thumbs) textIconSize(TabThumb, base) else textIconSize(dimens.iconSmall + 2.dp, base)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = with(density) { (maxWidth / options.size).roundToPx() }
        val inner = with(density) { (TabCellPadding * 2).roundToPx() }
        val iconRoom = with(density) { (iconSize + TabIconGap).roundToPx() }
        // 글자 사이 간격·반올림 때문에 딱 맞는 폭에서 1px씩 넘치는 일이 없게 여유를 둔다
        val room = cell - inner - with(density) { TabFitSlack.roundToPx() }
        fun fits(text: String, available: Int): Boolean =
            available > 0 && measurer.measure(koDisplay(text), widest, softWrap = false, maxLines = 1).size.width <= available
        val fit = when {
            (thumbs || icons.all { it != null }) && labels.all { fits(it, room - iconRoom) } -> TabFit.IconLabel
            labels.all { fits(it, room) } -> TabFit.Label
            shorts.all { fits(it, room) } -> TabFit.Short
            else -> TabFit.Fit
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
            options.forEachIndexed { i, option ->
                TabCell(
                    full = labels[i],
                    short = shorts[i],
                    icon = icons[i].takeIf { fit == TabFit.IconLabel && !thumbs },
                    art = arts[i].takeIf { fit == TabFit.IconLabel },
                    selected = option == selected,
                    fit = fit,
                    styles = styles,
                    iconSize = iconSize,
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

/** 탭 한 칸: 글자(+아이콘)는 가운데, 선택 밑줄은 칸 맨 아래(겹쳐 그려 칸 높이가 바뀌지 않는다) */
@Composable
private fun TabCell(
    full: String,
    short: String,
    icon: ImageVector?,
    art: SectionArt?,
    selected: Boolean,
    fit: TabFit,
    styles: List<TextStyle>,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val content = if (selected) Tokens.Accent else Tokens.InkSecondary
    val weight = if (selected) FontWeight.Bold else FontWeight.SemiBold
    val shown = if (fit == TabFit.IconLabel || fit == TabFit.Label) full else short
    Box(
        modifier
            // minTouch()가 아니라 heightIn — 칸 높이를 줄 전체(IntrinsicSize.Min)가 재므로 **고유 높이에도** 48/56dp가 들어가야 한다
            .heightIn(min = LocalDimens.current.minTouch)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            // FitText는 자기 글자를 의미 글자로 쓰므로, 줄여 그리는 단계에서는 칸 이름을 전체 라벨로 밝힌다
            .then(if (fit == TabFit.Fit) Modifier.semantics { contentDescription = full } else Modifier),
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = TabCellPadding, vertical = TabCellVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (art != null) {
                // 둥근 그라데이션 바탕 위 그림(꾸밈). 그림은 원보다 살짝 커서 스티커처럼 원 밖으로 조금 나온다
                Box(Modifier.size(iconSize).background(illusThumbBrush(art.tone, selected), CircleShape), contentAlignment = Alignment.Center) {
                    IllusImage(art.image, Modifier.requiredSize(iconSize * TAB_THUMB_ART), calm = !selected)
                }
                Spacer(Modifier.width(TabIconGap))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
                Spacer(Modifier.width(TabIconGap))
            }
            if (fit == TabFit.Fit) {
                FitText(short, styles.map { it.copy(fontWeight = weight) }, content, breakChars = " ")
            } else {
                KoText(
                    full,
                    styles.first().copy(fontWeight = weight),
                    color = content,
                    textAlign = TextAlign.Center,
                    display = if (shown == full) null else koDisplay(shown),
                )
            }
        }
        if (selected) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(TabUnderline).background(Tokens.Accent))
        }
    }
}

/**
 * 탭 줄을 **화면 틀**로 보이게 하는 바탕 (나라 화면 03~06): 흰 바탕 + 아래 1dp 선 + 아래로 번지는 옅은 그림자.
 * 바탕은 위로 [bleedTop](목록 위쪽 여백)까지 칠한다 — 고정된 탭 줄 위로 내용이 비쳐 보이지 않게.
 * 그림자는 직접 그린다(플랫폼 그림자 알파에 흔들리지 않게, Tokens 머리말 참고): 줄 바로 아래 [TabBarShadow]만큼.
 * 좌우로 화면 끝까지 넓히는 것은 부르는 쪽(`Modifier.fullBleed(screenPadding)`)이 한다.
 */
fun Modifier.tabBarSurface(bleedTop: Dp): Modifier = drawWithContent {
    val top = -bleedTop.toPx()
    drawRect(Tokens.Surface, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
    drawContent()
    val line = 1.dp.toPx()
    drawRect(Tokens.Line, topLeft = Offset(0f, size.height - line), size = Size(size.width, line))
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Tokens.Ink.copy(alpha = TAB_SHADOW_ALPHA), Color.Transparent),
            startY = size.height,
            endY = size.height + TabBarShadow.toPx(),
        ),
        topLeft = Offset(0f, size.height),
        size = Size(size.width, TabBarShadow.toPx()),
    )
}

// ======================= 그림 메뉴 (DESIGN_SPEC 부록 E.6 — 나라 화면 길잡이 v4) =======================
// 운영자 지적(2026-10-02): 메뉴와 내용을 나눈 구조는 좋지만 메뉴가 볼품없다 → **그림으로**.
// - 펼친 상태 [SectionCards]: 히어로 바로 아래 그림 카드 셋(같은 폭·한 줄). 카드 = 그라데이션 패널 위 일러스트 + 라벨.
// - 접힌 상태: 그림 메뉴가 화면 위로 지나가면 [SectionTabs](art = 썸네일)가 고정 줄로 나타난다(나라 화면은 둘 중 하나만 보인다).
// - 고른 칸 = **떠오름(그림자) + 짙어진 그라데이션 + 아래 Accent 막대 + Accent 굵은 글자**. 고르지 않은 칸 = 옅은 그라데이션·채도 낮춘 그림·1dp 선.
//   AccentSoft 단색 채움·Check 표시(고르기 모양)는 쓰지 않는다(부록 E.1).

/** 그림 카드 사이 간격 */
private val CardGap = 10.dp

/** 카드 테두리 안쪽에서 그림 패널까지 */
private val CardInset = 6.dp

/** 라벨 좌우 안쪽 여백 */
private val CardLabelPadding = 4.dp

/** 라벨 위 여백(패널 아래부터) */
private val CardLabelTop = 2.dp

/** 라벨 아래 여백(선택 막대 자리 포함) */
private val CardLabelBottom = 16.dp

/** 고른 카드 아래 선택 막대(가운데 알약 모양) 두께·카드 아래 끝에서 띄운 거리 */
private val CardPillHeight = 4.dp
private val CardPillBottom = 6.dp

/** 고른 카드 선택 막대 폭 = 카드 폭 × 이 비율 */
private const val CARD_PILL_FRACTION = 0.36f

/** 고른 카드의 떠오름 (그림자) */
private val CardLift = 6.dp

/** 카드 테두리 (고르지 않은 카드) */
private val CardBorder = 1.dp

/** 그림 패널 가로:세로 */
private const val PANEL_ASPECT = 1.3f

/**
 * 340dp 미만 창(320×470 화면 예산, DESIGN_SPEC 6장 머리말)의 그림 패널 가로:세로 — 납작하게 해서
 * 정부 비제휴 고지가 스크롤 없이 보이는 자리를 지킨다.
 */
private const val NARROW_PANEL_ASPECT = 2.4f

/** 패널 높이 대비 그림 크기 */
private const val PANEL_ART = 0.9f

/** 쌓인 배치(한 줄에 안 들어갈 때) 행의 그림 패널 한 변 */
private val RowPanel = 64.dp

/** 그림 메뉴 라벨 단계: ① 전체 라벨 한 줄 ② 짧은 라벨 한 줄 ③ 카드를 폭 전체 가로 행 셋으로 쌓기(전체 라벨, 어절 단위 줄바꿈) */
private enum class CardFit { Full, Short, Rows }

/**
 * 나라 화면 머리의 **그림 메뉴**(펼친 상태): 갈래 셋을 같은 폭 그림 카드로 한 줄에 놓는다. 누르면 아래 내용이 바뀐다.
 * - 라벨은 실제 글자 폭(굵게)으로 재서 모든 카드가 같은 단계를 쓴다([CardFit]). 짧은 라벨로도 한 줄이 안 되면
 *   카드 셋을 폭 전체 가로 행으로 쌓는다(그림은 그대로 — 내용을 숨기거나 글자를 자르지 않는다).
 * - 보이는 글자가 짧은 라벨이어도 TalkBack·테스트가 읽는 이름은 전체 라벨([KoText]의 display).
 * - a11y: 부모 `selectableGroup()` + 카드마다 `selectable(role = Role.Tab)` + 최소 터치 높이. 그림은 꾸밈(이름 없음).
 */
@Composable
fun <T> SectionCards(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    art: (T) -> SectionArt,
    modifier: Modifier = Modifier,
    shortLabel: @Composable (T) -> String = label,
) {
    if (options.isEmpty()) return
    val style = MaterialTheme.typography.labelLarge
    val widest = remember(style) { style.copy(fontWeight = FontWeight.Bold) }
    val measurer = rememberTextMeasurer(cacheSize = options.size * 2 + 2)
    val density = LocalDensity.current
    val labels = options.map { label(it) }
    val shorts = options.map { shortLabel(it) }
    val aspect = if (isNarrowWindow()) NARROW_PANEL_ASPECT else PANEL_ASPECT
    val dimens = LocalDimens.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val n = options.size
        val cell = with(density) { ((maxWidth - CardGap * (n - 1)) / n).roundToPx() }
        val room = cell - with(density) { (CardLabelPadding * 2 + TabFitSlack).roundToPx() }
        fun fits(text: String, available: Int = room): Boolean =
            available > 0 && measurer.measure(koDisplay(text), widest, softWrap = false, maxLines = 1).size.width <= available
        val fit = when {
            labels.all { fits(it) } -> CardFit.Full
            shorts.all { fits(it) } -> CardFit.Short
            else -> CardFit.Rows
        }
        // 쌓인 행 라벨: 그림 패널 옆에 전체 라벨이 한 줄로 안 들어가면(모든 행 함께) 짧은 라벨 — `입국·비 / 자`처럼 낱말 안에서 끊기지 않게
        val rowWidth = maxWidth - CardInset * 2 - RowPanel - dimens.gap - TabFitSlack
        val rowShort = fit == CardFit.Rows && !labels.all { fits(it, with(density) { rowWidth.roundToPx() }) }
        if (fit == CardFit.Rows) {
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(CardGap)) {
                options.forEachIndexed { i, option ->
                    SectionRowCard(labels[i], if (rowShort) shorts[i] else labels[i], art(option), option == selected) { onSelect(option) }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                options.forEachIndexed { i, option ->
                    SectionCard(
                        full = labels[i],
                        shown = if (fit == CardFit.Full) labels[i] else shorts[i],
                        art = art(option),
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        aspect = aspect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 카드 바깥 모양: 고른 카드는 떠오름(그림자), 아니면 1dp LineSoft 선. 바탕은 흰색, 고른 카드 맨 아래 Accent 막대 */
private fun Modifier.sectionCardFrame(shape: Shape, selected: Boolean, onClick: () -> Unit): Modifier = this
    .then(
        if (selected) {
            Modifier.shadow(CardLift, shape, clip = false, ambientColor = Tokens.ShadowAmbient, spotColor = Tokens.ShadowSpot)
        } else {
            Modifier
        },
    )
    .clip(shape)
    .background(Tokens.Surface)
    .then(if (selected) Modifier else Modifier.border(CardBorder, Tokens.LineSoft, shape))
    .selectable(selected = selected, role = Role.Tab, onClick = onClick)
    .drawWithContent {
        drawContent()
        if (selected) {
            // 카드 아래 가운데 Accent 알약 막대 — 모서리가 둥근 카드에 폭 전체 막대를 그으면 아래가 웃는 입 모양으로 휘어 보였다
            val h = CardPillHeight.toPx()
            val w = size.width * CARD_PILL_FRACTION
            drawRoundRect(
                Tokens.Accent,
                topLeft = Offset((size.width - w) / 2f, size.height - CardPillBottom.toPx() - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f),
            )
        }
    }

/** 고른 칸은 Accent 굵게, 아니면 InkSecondary 보통 굵기 */
@Composable
private fun cardLabelStyle(selected: Boolean): TextStyle =
    MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)

/** 그림 카드 하나(한 줄 배치): 그라데이션 패널 + 그림, 아래 라벨 한 줄 */
@Composable
private fun SectionCard(
    full: String,
    shown: String,
    art: SectionArt,
    selected: Boolean,
    onClick: () -> Unit,
    aspect: Float,
    modifier: Modifier,
) {
    Column(
        modifier
            .heightIn(min = LocalDimens.current.minTouch)
            .sectionCardFrame(MaterialTheme.shapes.large, selected, onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(CardInset)
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(MaterialTheme.shapes.medium)
                .background(illusPanelBrush(art.tone, rich = selected)),
            contentAlignment = Alignment.Center,
        ) {
            IllusImage(art.image, Modifier.fillMaxHeight(PANEL_ART).aspectRatio(1f), calm = !selected)
        }
        KoText(
            full,
            cardLabelStyle(selected),
            color = if (selected) Tokens.Accent else Tokens.InkSecondary,
            textAlign = TextAlign.Center,
            display = if (shown == full) null else koDisplay(shown),
            modifier = Modifier.padding(start = CardLabelPadding, end = CardLabelPadding, top = CardLabelTop, bottom = CardLabelBottom),
        )
    }
}

/** 그림 카드 하나(쌓인 배치 — 글자가 커서 한 줄에 셋이 안 들어갈 때): 왼쪽 그림 패널 + 전체 라벨(어절 단위 줄바꿈) */
@Composable
private fun SectionRowCard(full: String, shown: String, art: SectionArt, selected: Boolean, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.tileRowMinHeight)
            .sectionCardFrame(MaterialTheme.shapes.large, selected, onClick)
            .padding(start = CardInset, end = CardInset, top = CardInset, bottom = CardInset + CardPillBottom + CardPillHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gap),
    ) {
        Box(
            Modifier.size(RowPanel).clip(MaterialTheme.shapes.medium).background(illusPanelBrush(art.tone, rich = selected)),
            contentAlignment = Alignment.Center,
        ) {
            IllusImage(art.image, Modifier.fillMaxSize(PANEL_ART), calm = !selected)
        }
        KoText(
            full,
            cardLabelStyle(selected),
            color = if (selected) Tokens.Accent else Tokens.InkSecondary,
            glueShort = true,
            display = if (shown == full) null else koDisplay(shown, glueShort = true),
            modifier = Modifier.weight(1f),
        )
    }
}
