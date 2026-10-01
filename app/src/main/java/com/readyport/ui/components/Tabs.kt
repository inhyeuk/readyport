package com.readyport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val icons = options.map { icon(it) }
    val iconSize = textIconSize(dimens.iconSmall + 2.dp, base)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = with(density) { (maxWidth / options.size).roundToPx() }
        val inner = with(density) { (TabCellPadding * 2).roundToPx() }
        val iconRoom = with(density) { (iconSize + TabIconGap).roundToPx() }
        // 글자 사이 간격·반올림 때문에 딱 맞는 폭에서 1px씩 넘치는 일이 없게 여유를 둔다
        val room = cell - inner - with(density) { TabFitSlack.roundToPx() }
        fun fits(text: String, available: Int): Boolean =
            available > 0 && measurer.measure(koDisplay(text), widest, softWrap = false, maxLines = 1).size.width <= available
        val fit = when {
            icons.all { it != null } && labels.all { fits(it, room - iconRoom) } -> TabFit.IconLabel
            labels.all { fits(it, room) } -> TabFit.Label
            shorts.all { fits(it, room) } -> TabFit.Short
            else -> TabFit.Fit
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
            options.forEachIndexed { i, option ->
                TabCell(
                    full = labels[i],
                    short = shorts[i],
                    icon = icons[i].takeIf { fit == TabFit.IconLabel },
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
            if (icon != null) {
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
