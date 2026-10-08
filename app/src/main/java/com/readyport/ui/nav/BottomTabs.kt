package com.readyport.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.components.TextCircle
import com.readyport.ui.components.koDisplay
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// 탭 다섯: 둘러보기 · 내 여행 · 게시판 · 도움 · 설정 (2026-10-08 게시판 추가 — 예전 넷은 2026-10-03 부록 H)

/** [selectedIcon]: 선택된 탭에서만 쓰는 채운 아이콘 (Filled는 '켜짐·선택' 상태에만, D12) */
enum class Tab(@StringRes val label: Int, val icon: ImageVector, val route: Any, val selectedIcon: ImageVector = icon) {
    /** 둘러보기 = 어디 갈까(생각 단계). 여행 흐름은 내 여행 탭이 맡는다 (2026-10-03 부록 H) */
    Home(R.string.tab_explore, Icons.Outlined.TravelExplore, HomeRoute, Icons.Filled.TravelExplore),
    Trip(R.string.tab_trip, Icons.Outlined.Luggage, TripsRoute, Icons.Filled.Luggage),

    /** 게시판 = Q&A · 자유 토론 (docs/BOARD.md). 자녀 폰 모드에는 없다 */
    Board(R.string.tab_board, Icons.Outlined.Forum, BoardRoute, Icons.Filled.Forum),
    Help(R.string.tab_help, Icons.Outlined.SupportAgent, HelpRoute, Icons.Filled.SupportAgent),
    Settings(R.string.tab_settings, Icons.Outlined.Settings, SettingsRoute, Icons.Filled.Settings),

    /** 자녀 폰 모드에서만 쓰는 탭 */
    Present(R.string.tab_present, Icons.Outlined.QrCode2, PresentRoute, Icons.Filled.QrCode2);

    companion object {
        val Main = listOf(Home, Trip, Board, Help, Settings)
        /** 자녀 폰: 자기 QR과 도움만 (PRD 3.3) — 게시판 없음 */
        val Child = listOf(Present, Help)
    }
}

/** 선택 탭 아이콘 뒤 알약 인디케이터 크기 (DESIGN_SPEC 6장 공통 틀) — 글자가 아니라 아이콘만 품는다. 다섯 칸(360dp에 약 71dp)에 맞춰 56dp */
private val IndicatorWidth = 56.dp
private val IndicatorHeight = 32.dp

/**
 * 탭 글자가 칸에 맞게 줄어들 수 있는 하한(실제 화면 크기 dp — 글자 크기 설정과 무관한 바닥).
 * 다섯 칸이 360dp 폭·글자 200%에서도 낱말을 쪼개지 않게, 기본 모드는 11dp, 쉬운 모드는 18dp(= 쉬운 모드 100%의 탭 글자, D19)까지만 줄인다.
 * 줄여도 안 들어가면 그 크기로 낱말 경계에서 줄을 더 바꾼다(자르지 않는다).
 */
private val BasicFloor = 11.dp
private val EasyFloor = 18.dp

/**
 * 하단 탭 다섯: 둘러보기 · 내 여행 · 게시판 · 도움 · 설정 (DESIGN_SPEC 6장 공통 틀, 부록 H·L).
 * Material NavigationBar는 높이가 고정이라 글자를 크게 키우면 라벨이 잘린다 → 높이가 내용에 맞춰 늘어나는 탭 막대를 직접 그린다.
 * - 선택 탭: 아이콘 뒤 56×32dp 알약(AccentSoft, 도움 탭은 HelpSoft) + 채운 아이콘 + 굵은 라벨 — 색 말고도 모양·굵기로 구분
 * - 도움 탭은 선택 여부와 상관없이 따뜻한 색(Help)으로 항상 구분한다(PRD). 비선택이면 굵기만 Medium
 * - 라벨([TabLabel]): 칸 폭에 맞춰 낱말을 쪼개지 않는 가장 큰 크기로(기본 1줄, 쉬운 모드 2줄까지), 하한 11/18dp
 * - [badges]: 탭 아이콘 오른쪽 위 빨간 숫자(게시판 새 댓글) — TalkBack은 탭 상태로 `새 댓글 2개`
 * - 눌림 물결(ripple)은 칸 전체 사각형이 아니라 알약 안에만 그린다(칸 전체가 누르는 영역인 것은 그대로)
 */
@Composable
fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit, tabs: List<Tab> = Tab.Main, badges: Map<Tab, Int> = emptyMap()) {
    Surface(color = Tokens.Surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = Tokens.LineSoft)
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp).selectableGroup()) {
                tabs.forEach { tab -> TabItem(tab, selected = tab == selected, badge = badges[tab] ?: 0, onClick = { onSelect(tab) }) }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val labelStyle = MaterialTheme.typography.labelSmall
    val help = tab == Tab.Help
    val color: Color = when {
        help -> Tokens.Help
        selected -> Tokens.Accent
        else -> Tokens.InkSecondary
    }
    val interaction = remember { MutableInteractionSource() }
    val style = labelStyle.copy(
        textAlign = TextAlign.Center,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
    )
    val badgeText = if (badge > 0) stringResource(R.string.board_tab_new_cd, badge) else null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = dimens.buttonHeight + 8.dp)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .then(if (badgeText != null) Modifier.semantics { stateDescription = badgeText } else Modifier)
            // 위아래 4dp: 인디케이터(32dp)가 들어와도 막대 높이가 예전(아이콘 24 + 위아래 8)과 같다 — 다른 탭 첫 화면 예산 유지
            .padding(vertical = 4.dp, horizontal = 1.dp),
    ) {
        Box(Modifier.size(IndicatorWidth, IndicatorHeight), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(IndicatorWidth, IndicatorHeight)
                    .clip(CircleShape)
                    .indication(interaction, ripple())
                    .background(
                        color = when {
                            !selected -> Color.Transparent
                            help -> Tokens.HelpSoft
                            else -> Tokens.AccentSoft
                        },
                        shape = CircleShape,
                    ),
            )
            Icon(
                if (selected) tab.selectedIcon else tab.icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(dimens.icon),
            )
            if (badge > 0) {
                TextCircle(
                    if (badge > BADGE_MAX) "$BADGE_MAX+" else badge.toString(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // 크기 0으로 놓고 제 크기대로 그린다 — 아이콘 오른쪽 위에 걸치고, 글자가 커져도 탭 칸 높이에 눌리지 않는다
                        .layout { measurable, _ ->
                            val p = measurable.measure(Constraints())
                            layout(0, 0) { p.place(BadgeShift.roundToPx(), -BadgeLift.roundToPx()) }
                        }
                        .clearAndSetSemantics {},
                    minSize = BadgeMin,
                    container = Tokens.DangerText,
                    content = Tokens.Surface,
                    // 숫자 원은 글자 크기 설정을 따라 커지지 않는다(dp) — 커지면 아이콘을 덮는다. 정확한 수는 TalkBack 상태가 말한다
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = with(LocalDensity.current) { BadgeText.toSp() },
                        lineHeight = with(LocalDensity.current) { (BadgeText + 3.dp).toSp() },
                    ),
                )
            }
        }
        TabLabel(stringResource(tab.label), style, color, if (dimens.easyMode) EasyFloor else BasicFloor, maxLines = if (dimens.easyMode) 2 else 1)
    }
}

/** 이보다 많으면 `9+` — 숫자 원이 이웃 탭으로 넘치지 않게(TalkBack은 정확한 수) */
private const val BADGE_MAX = 9
private val BadgeMin = 18.dp
private val BadgeText = 11.dp

/** 숫자 원 왼쪽 끝 = 아이콘 가운데에서 오른쪽으로 */
private val BadgeShift = 7.dp
private val BadgeLift = 4.dp

/**
 * 탭 라벨: 칸 폭 안에서 **낱말을 쪼개지 않고 [maxLines]줄 안에 들어가는 가장 큰 글자**로 그린다.
 * 보이는 글은 한 글자 낱말을 다음 낱말에 붙인다(`내 여행` → 한 덩어리 — 한 음절 줄 금지). 의미 글자(TalkBack·테스트)는 원문 하나.
 * 크기는 스타일 크기에서 [floor](dp)까지 1dp씩. 하한에서도 안 되면 하한 크기로 낱말 경계에서 줄을 더 바꾼다.
 */
@Composable
private fun TabLabel(text: String, style: TextStyle, color: Color, floor: Dp, maxLines: Int) {
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val shown = remember(text) { koDisplay(text, glueShort = true) }
    val policy = remember(shown, style, measurer, floor, maxLines) { TabLabelPolicy(shown, style, measurer, floor, maxLines, layout) }
    Layout(
        modifier = Modifier
            .semantics {
                this.text = AnnotatedString(text)
                getTextLayoutResult { results ->
                    val r = layout.value ?: return@getTextLayoutResult false
                    results += r
                    true
                }
            }
            .drawBehind { layout.value?.let { drawText(it, color = color) } },
        measurePolicy = policy,
    )
}

private class TabLabelPolicy(
    private val shown: String,
    private val style: TextStyle,
    private val measurer: TextMeasurer,
    private val floor: Dp,
    private val maxLines: Int,
    private val layout: MutableState<TextLayoutResult?>,
) : MeasurePolicy {
    private val words = shown.split(' ').filter { it.isNotEmpty() }

    private fun MeasureScope.sizes(): List<TextStyle> {
        val basePx = style.fontSize.toPx()
        val floorPx = floor.toPx().coerceAtMost(basePx)
        val step = 1.dp.toPx()
        val out = mutableListOf<TextStyle>()
        var px = basePx
        while (px >= floorPx - 0.01f) {
            out += style.copy(fontSize = px.toSp(), lineHeight = (px * LINE_RATIO).toSp())
            px -= step
        }
        if (out.isEmpty()) out += style
        return out
    }

    private fun fits(s: TextStyle, maxWidth: Int): TextLayoutResult? {
        if (words.any { measurer.measure(it, s, softWrap = false, maxLines = 1).size.width > maxWidth }) return null
        val r = measurer.measure(shown, s, constraints = Constraints(maxWidth = maxWidth))
        return r.takeIf { it.lineCount <= maxLines }
    }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val candidates = sizes()
        val r = candidates.firstNotNullOfOrNull { fits(it, maxWidth) }
            ?: measurer.measure(shown, candidates.last(), constraints = Constraints(maxWidth = maxWidth.coerceAtLeast(0)))
        layout.value = r
        return layout(r.size.width.coerceAtMost(maxWidth), constraints.constrainHeight(r.size.height)) {}
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurer.measure(shown, style, softWrap = false, maxLines = 1).size.width

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int = 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurer.measure(shown, style, constraints = Constraints(maxWidth = width.coerceAtLeast(0))).size.height

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        minIntrinsicHeight(measurables, width)

    private companion object {
        /** 줄 높이 = 글자 크기 × 1.33 (labelSmall 12/16, 쉬운 모드 18/24와 같은 비율) */
        const val LINE_RATIO = 1.33f
    }
}
