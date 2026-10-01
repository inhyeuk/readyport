package com.readyport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readyport.ui.theme.LocalDimens

// ======================= 레이아웃 도우미 (DESIGN_SPEC 4.1) =======================

// ---------------- 반응형 판정: 앱 전체에서 이 한 곳만 (재검토 R5) ----------------
// 화면·부품은 글자 배율(fontScale) 숫자를 직접 보지 않는다. 아래 LayoutInfo(창 폭 ÷ 실측 글자 배율로 한 번 계산)만 읽는다.
// 큰 글자·쉬운 모드에서도 내용은 숨기지 않는다 — 배치만 바꾼다(배지·끝 요소를 윗줄로, 2열 → 1열, 아이콘을 글 첫 줄 안으로).

/** 2열 한 칸이 이 폭(dp ÷ 글자 배율)보다 좁으면 1열로 (D4) */
private const val MIN_COLUMN_DP = 150f

/**
 * 화면 안쪽 폭(창 폭 − 양옆 screenPadding)을 글자 배율로 나눈 값이 이보다 작으면 [LayoutClass.Stacked].
 * 393dp 창에서 기본 모드 글자 130%(예전 largeFont 기준)와 같은 값이다 — 360dp 창이면 약 118%, 412dp면 약 137%부터.
 */
private const val STACK_TEXT_WIDTH_DP = 272f

/** 창 폭을 아직 모를 때 쓰는 기준 폭(dp) */
private const val REFERENCE_WIDTH_DP = 393f

/** 340dp 미만의 좁은 창 (320×470 화면 예산, DESIGN_SPEC 6장 머리말) */
internal const val NARROW_WINDOW_DP = 340f

/** 화면 배치 단계 (R5) */
enum class LayoutClass {
    /** 2열 그리드. 배지·끝 요소는 제목 옆 */
    Roomy,

    /** 1열(쉬운 모드·조금 큰 글자·좁은 창). 배지·끝 요소는 아직 제목 옆 */
    Compact,

    /** 큰 글자: 배지·끝 요소·썸네일을 윗줄로 올리고 글에 폭 전체를 준다 (예전 largeFont()/hugeFont() 자리) */
    Stacked,
}

/**
 * 반응형 판정 결과 하나. [ReadyPortTheme]이 [ProvideLayoutInfo]로 한 번 계산해 내려 준다.
 * @property widthDp 창 폭(dp, 모르면 0)
 * @property textScale 실측 글자 배율 = 본문(bodyLarge)의 실제 크기(dp) ÷ 그 sp 값 — 시스템 글자 크기 설정의 실제 배율(API 34+ 비선형 확대 포함).
 *   쉬운 모드의 큰 글자(20sp)는 배율이 아니라 모드 값이라 넣지 않는다(쉬운 모드는 이미 1열이고, 100%에서 배지를 윗줄로 올리지 않는다)
 * @property columns 타일 그리드 열 수(1/2)
 */
@Immutable
data class LayoutInfo(val widthDp: Float, val textScale: Float, val columns: Int, val layoutClass: LayoutClass) {
    /** 큰 글자 배치(배지·끝 요소 윗줄) */
    val stacked: Boolean get() = layoutClass == LayoutClass.Stacked

    /** 340dp 미만 창(320×470 화면 예산) */
    val narrow: Boolean get() = widthDp > 0f && widthDp < NARROW_WINDOW_DP
}

/**
 * 순수 계산(단위 테스트용). 열 수: 쉬운 모드는 항상 1열, 기본 모드는 2열 한 칸 폭 `(창 폭 − 2×screenPadding − gap) / 2`를
 * 글자 배율로 나눈 값이 150 미만이면 1열(393dp 창이면 약 114%부터, 360dp면 약 103%부터, 340dp 미만이면 늘 1열).
 * Stacked: `(창 폭 − 2×screenPadding) ÷ 글자 배율 < 272`. 그 밖에 1열이면 Compact, 2열이면 Roomy.
 */
fun layoutInfoOf(widthDp: Float, textScale: Float, easyMode: Boolean, screenPaddingDp: Float = 20f, gapDp: Float = 12f): LayoutInfo {
    val scale = textScale.coerceAtLeast(0.5f)
    val columns = when {
        easyMode || widthDp <= 0f -> 1
        (widthDp - 2 * screenPaddingDp - gapDp) / 2f / scale < MIN_COLUMN_DP -> 1
        else -> 2
    }
    val width = if (widthDp > 0f) widthDp else REFERENCE_WIDTH_DP
    val textWidth = (width - 2 * screenPaddingDp) / scale
    val cls = when {
        textWidth < STACK_TEXT_WIDTH_DP -> LayoutClass.Stacked
        columns == 1 -> LayoutClass.Compact
        else -> LayoutClass.Roomy
    }
    return LayoutInfo(widthDp, scale, columns, cls)
}

/** 테마가 내려 주는 판정 (밖이면 null → 그 자리에서 계산) */
val LocalLayoutInfo = compositionLocalOf<LayoutInfo?> { null }

/** 지금 화면의 반응형 판정. 화면·부품은 이것만 읽는다 */
@Composable
fun rememberLayoutInfo(): LayoutInfo = LocalLayoutInfo.current ?: computeLayoutInfo()

/** 지금 화면의 배치 단계 */
@Composable
fun rememberLayoutClass(): LayoutClass = rememberLayoutInfo().layoutClass

/** 큰 글자 배치인지 ([LayoutClass.Stacked]) */
@Composable
fun isStackedLayout(): Boolean = rememberLayoutInfo().stacked

/**
 * 목록 행(배지 + 제목 + 끝 요소 — 설정·스위치 행 [ListRow], 꼭 챙길 물건 체크 카드, 홈 접힌 줄)을 **통째로** 쌓을지 (재검토2 ④#6).
 * 큰 글자 배치(Stacked)에서 배지가 있는 행은 제목 길이와 관계없이 **모두** 배지·끝 요소(스위치·꺾쇠·체크 상자)를 윗줄에,
 * 제목·설명을 아래 폭 전체로 둔다 — 행마다 '제목이 옆에 들어가는지'로 정하면 한 묶음 안에서 `여권·예약 서류 관리`는 쌓이고
 * 바로 다음 `같이 가는 사람`은 옆에 서서 시작선이 들쭉날쭉했다. 배지가 없는 행은 제목이 옆에 안 들어갈 때만 쌓는다(BadgeTitleLayout).
 * 내용은 숨기지 않고 배치만 바뀐다.
 */
@Composable
fun isStackedListRow(hasBadge: Boolean = true): Boolean = hasBadge && isStackedLayout()

/** 340dp 미만 창 */
@Composable
fun isNarrowWindow(): Boolean = rememberLayoutInfo().narrow

/** [content] 아래에 지금 창·글자 크기의 판정을 내려 준다 (ReadyPortTheme 안에서 한 번) */
@Composable
fun ProvideLayoutInfo(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutInfo provides computeLayoutInfo(), content = content)
}

@Composable
private fun computeLayoutInfo(): LayoutInfo {
    val dimens = LocalDimens.current
    val density = LocalDensity.current
    val body = MaterialTheme.typography.bodyLarge.fontSize
    val widthDp = windowWidthDp()
    // textIconSize와 같은 방법: sp를 실제 dp로 바꿔 잰 배율(API 34+ 비선형 확대 포함)
    val textScale = if (body.isSp && body.value > 0f) with(density) { body.toDp().value } / body.value else density.fontScale
    return remember(widthDp, textScale, dimens.easyMode, dimens.screenPadding, dimens.gap) {
        layoutInfoOf(widthDp, textScale, dimens.easyMode, dimens.screenPadding.value, dimens.gap.value)
    }
}

/**
 * 타일 그리드 열 수 (D4) — [LayoutInfo.columns]. [preferred]가 1이면 1, 2열이 들어가는 폭이면 [preferred].
 * 창 폭은 LocalWindowInfo로 읽는다(LocalConfiguration.screenWidthDp는 lint ConfigurationScreenWidthHeight 경고).
 */
@Composable
fun rememberGridColumns(preferred: Int = 2): Int {
    if (preferred <= 1) return 1
    return if (rememberLayoutInfo().columns == 1) 1 else preferred
}

/** 창 폭(dp). 아직 모르면 0 */
@Composable
internal fun windowWidthDp(): Float {
    val widthPx = LocalWindowInfo.current.containerSize.width
    return if (widthPx <= 0) 0f else widthPx / LocalDensity.current.density
}

/**
 * 그리드 칸 안의 부품에게 알려 주는 그 그리드의 열 수 (그리드 밖이면 null).
 * IconTile(Auto)·SelectTile이 가로/세로 배치를 **자기가 놓인 칸 수**로 고른다 — 쉬운 모드에서 3칸 행에 놓인 타일이
 * 가로 배치로 바뀌어 라벨이 음절 단위로 쪼개지던 문제 (rememberGridColumns()는 화면 기준이라 칸 폭을 모른다).
 * BoxWithConstraints는 TileGrid 행의 IntrinsicSize.Min과 함께 쓸 수 없어서 CompositionLocal로 넘긴다.
 */
val LocalTileColumns = staticCompositionLocalOf<Int?> { null }

/**
 * 짧은 목록용 그리드: 같은 행의 칸 높이를 맞춘다(IntrinsicSize.Min). LazyVerticalGrid를 겹쳐 쓰지 않는다.
 * [itemContent]는 (항목, 칸 Modifier) — 칸 Modifier(weight + fillMaxHeight)를 꼭 타일에 넘긴다.
 * 칸 안에서는 LocalTileColumns = [columns].
 */
@Suppress("ComposableLambdaParameterNaming") // 스펙 4.1 시그니처(itemContent) 그대로
@Composable
fun <T> TileGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    columns: Int = rememberGridColumns(),
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val cols = columns.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
        items.chunked(cols).forEach { row -> TileRow(row, cols, itemContent) }
    }
}

@Composable
private fun <T> TileRow(row: List<T>, columns: Int, itemContent: @Composable (item: T, modifier: Modifier) -> Unit) {
    CompositionLocalProvider(LocalTileColumns provides columns) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
        ) {
            row.forEach { itemContent(it, Modifier.weight(1f).fillMaxHeight()) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** 타일이 가로(1열)로 놓이는지: 그리드 안이면 그 그리드의 열 수, 밖이면 rememberGridColumns() 기준 */
@Composable
internal fun rememberSingleColumnCell(): Boolean = (LocalTileColumns.current ?: rememberGridColumns()) == 1

/**
 * 제목 덩어리 [main] + 오른쪽 끝 [trailing] (CardNewsCard `trailing`, SectionHeader `action`, ListRow `RowTrailing.Custom`).
 * trailing을 옆에 두면 main이 더 많은 줄로 꺾이는 경우(폭이 모자람) trailing을 main 아래 줄로 내린다 —
 * trailing이 먼저 제 폭을 다 가져가 제목이 한 음절씩 쪼개지던 문제 (DESIGN_SPEC 4.3 '폭이 모자라면 아래 줄', 7장 7번).
 * 글자 크기·쉬운 모드와 상관없이 실제 폭으로 정한다.
 * [centerVertically]: 옆에 둘 때 세로 가운데 맞춤(한 줄짜리 행). 아니면 위 맞춤.
 */
@Composable
internal fun TrailingFlow(
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
    belowGap: Dp = 8.dp,
    centerVertically: Boolean = false,
    main: @Composable () -> Unit,
) {
    Layout(contents = listOf(main, { Box { trailing() } }), modifier = modifier) { (mainMs, trailMs), constraints ->
        val m = mainMs.first()
        val t = trailMs.first()
        val gapPx = gap.roundToPx()
        val free = Constraints()
        if (!constraints.hasBoundedWidth) {
            val mp = m.measure(free)
            val tp = t.measure(free)
            val h = maxOf(mp.height, tp.height)
            return@Layout layout(mp.width + gapPx + tp.width, h) {
                mp.placeRelative(0, if (centerVertically) (h - mp.height) / 2 else 0)
                tp.placeRelative(mp.width + gapPx, if (centerVertically) (h - tp.height) / 2 else 0)
            }
        }
        val maxW = constraints.maxWidth
        val trailW = t.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(maxW)
        val besideW = maxW - gapPx - trailW
        val beside = trailW == 0 ||
            (besideW > 0 && m.maxIntrinsicHeight(besideW) <= m.maxIntrinsicHeight(maxW))
        if (beside) {
            val mainW = if (trailW == 0) maxW else besideW
            val mp = m.measure(Constraints(maxWidth = mainW))
            val tp = t.measure(Constraints(maxWidth = maxOf(trailW, 0)))
            val h = maxOf(mp.height, tp.height).coerceAtLeast(constraints.minHeight)
            layout(maxW, h) {
                mp.placeRelative(0, if (centerVertically) (h - mp.height) / 2 else 0)
                tp.placeRelative(maxW - tp.width, if (centerVertically) (h - tp.height) / 2 else 0)
            }
        } else {
            val mp = m.measure(Constraints(maxWidth = maxW))
            val tp = t.measure(Constraints(maxWidth = maxW))
            val below = belowGap.roundToPx()
            val h = (mp.height + below + tp.height).coerceAtLeast(constraints.minHeight)
            layout(maxW, h) {
                mp.placeRelative(0, 0)
                tp.placeRelative(0, mp.height + below)
            }
        }
    }
}

/**
 * 두 요소를 **같은 폭**으로 한 줄에 놓아 내용선 끝까지 채운다(쉬운 모드 `처음으로`·`소리로 듣기`, 재검토2 ①#5).
 * 둘 중 하나라도 반 폭에서 한 줄로 안 들어가면(큰 글자) 위아래로 쌓고 둘 다 폭 전체 — 글자는 쪼개지지 않는다.
 * 한 줄에 둘 때는 높이도 큰 쪽에 맞춘다. [first]·[second]는 받은 Modifier를 꼭 자기 맨 바깥에 붙인다.
 */
@Composable
internal fun EqualWidthPair(
    gap: Dp,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf({ first(Modifier) }, { second(Modifier) }), modifier = modifier.fillMaxWidth()) { (aMs, bMs), constraints ->
        val a = aMs.first()
        val b = bMs.first()
        val g = gap.roundToPx()
        if (!constraints.hasBoundedWidth) {
            val ap = a.measure(Constraints())
            val bp = b.measure(Constraints())
            return@Layout layout(maxOf(ap.width, bp.width), ap.height + g + bp.height) {
                ap.placeRelative(0, 0)
                bp.placeRelative(0, ap.height + g)
            }
        }
        val w = constraints.maxWidth
        val half = ((w - g) / 2).coerceAtLeast(0)
        val side = a.maxIntrinsicWidth(Constraints.Infinity) <= half && b.maxIntrinsicWidth(Constraints.Infinity) <= half
        if (side) {
            val h = maxOf(a.maxIntrinsicHeight(half), b.maxIntrinsicHeight(half))
            val cell = Constraints(minWidth = half, maxWidth = half, minHeight = h, maxHeight = maxOf(h, 0))
            val ap = a.measure(cell)
            val bp = b.measure(cell)
            val height = maxOf(ap.height, bp.height)
            layout(w, height) {
                ap.placeRelative(0, 0)
                bp.placeRelative(w - bp.width, 0)
            }
        } else {
            val full = Constraints(minWidth = w, maxWidth = w)
            val ap = a.measure(full)
            val bp = b.measure(full)
            layout(w, ap.height + g + bp.height) {
                ap.placeRelative(0, 0)
                bp.placeRelative(0, ap.height + g)
            }
        }
    }
}

/**
 * 긴 목록(쇼핑 등)용: 한 줄을 lazy item 하나로. [columns]는 화면 composable에서 rememberGridColumns()로 미리 계산해 넘긴다
 * (AppScreen의 content 람다는 LazyListScope라 composable 호출 불가).
 */
fun <T> LazyListScope.tileRows(
    keyPrefix: String,
    items: List<T>,
    columns: Int,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val cols = columns.coerceAtLeast(1)
    items.chunked(cols).forEachIndexed { i, row ->
        item(key = "$keyPrefix-$i", contentType = "tileRow") { TileRow(row, cols, itemContent) }
    }
}

/**
 * 섹션 사이 간격. LazyColumn이 spacedBy(gap)라 Spacer 앞뒤로 gap이 한 번씩 붙으므로
 * Spacer 높이는 `sectionGap − 2×gap`(기본·쉬운 모두 8dp)이다 (DESIGN_SPEC 3.4).
 */
fun LazyListScope.sectionGap(key: String) {
    item(key = key, contentType = "sectionGap") {
        val d = LocalDimens.current
        Spacer(Modifier.height(d.sectionGap - d.gap * 2))
    }
}

// ---------------- 화면 끝까지 넓히기 ----------------

/**
 * 목록 양옆 여백([AppScreen]의 contentPadding = screenPadding)을 벗어나 **화면 끝까지** 넓히는 항목 —
 * 화면 틀(고정된 탭 줄)처럼 바탕이 가장자리에 닿아야 하는 한 항목에만 쓴다. 내용 카드에는 쓰지 않는다.
 * 재는 폭을 좌우 [bleed]만큼 늘려 그리고, 자리는 원래 폭 그대로 차지한 뒤 왼쪽으로 [bleed]만큼 밀어 놓는다
 * (IconBullet의 연한 바탕 내밀기와 같은 방법). RTL에서도 `placeRelative`가 방향을 맞춘다.
 */
fun Modifier.fullBleed(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = bleed.roundToPx() * 2
    val wide = if (constraints.hasBoundedWidth) {
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    } else {
        constraints
    }
    val placeable = measurable.measure(wide)
    val width = if (constraints.hasBoundedWidth) placeable.width - extra else placeable.width
    layout(width.coerceAtLeast(0), placeable.height) { placeable.placeRelative(-bleed.roundToPx(), 0) }
}

// ---------------- 최소 터치 크기 ----------------

/**
 * 높이를 LocalDimens.minTouch(48/56dp) 이상으로. `heightIn(min = minTouch)`와 같은 효과.
 * @Composable 확장 Modifier는 lint ComposableModifierFactory 경고를 내므로 Modifier.Node로 만든다.
 */
fun Modifier.minTouch(): Modifier = this then MinTouchElement(bothAxes = false)

/** IconButton·IconToggleButton용: 가로·세로 모두 minTouch 이상 */
fun Modifier.minTouchSize(): Modifier = this then MinTouchElement(bothAxes = true)

private data class MinTouchElement(val bothAxes: Boolean) : ModifierNodeElement<MinTouchNode>() {
    override fun create() = MinTouchNode(bothAxes)

    override fun update(node: MinTouchNode) {
        node.bothAxes = bothAxes
    }

    override fun InspectorInfo.inspectableProperties() {
        name = if (bothAxes) "minTouchSize" else "minTouch"
    }
}

private class MinTouchNode(var bothAxes: Boolean) : Modifier.Node(), LayoutModifierNode, CompositionLocalConsumerModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val min = currentValueOf(LocalDimens).minTouch.roundToPx()
        val minHeight = maxOf(constraints.minHeight, if (constraints.hasBoundedHeight) min.coerceAtMost(constraints.maxHeight) else min)
        val minWidth = if (bothAxes) {
            maxOf(constraints.minWidth, if (constraints.hasBoundedWidth) min.coerceAtMost(constraints.maxWidth) else min)
        } else {
            constraints.minWidth
        }
        val placeable = measurable.measure(constraints.copy(minWidth = minWidth, minHeight = minHeight))
        return layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
}

// ---------------- key로 스크롤 이동 ----------------

/**
 * LazyListState에는 key로 index를 찾는 공개 API가 없다. 화면이 LazyListScope에 item을 넣는 순서대로 key를 기록해 둔다.
 * AppScreen(keyIndex = …)에 넘기면 AppScreen이 넣는 header·easy-actions item과 sectionGap Spacer까지 모두 기록된다.
 */
class KeyIndex {
    private val keys = ArrayList<Any?>()

    /** [key] item의 index. 없으면 null */
    fun indexOf(key: String): Int? = keys.indexOf(key).takeIf { it >= 0 }

    /** 지금까지 기록한 item 수 */
    val size: Int get() = keys.size

    /**
     * [scope]에 넣는 item의 key를 기록하는 LazyListScope를 돌려준다.
     * LazyColumn의 content 람다는 목록을 다시 만들 때마다 처음부터 다시 돌므로, 여기서 기록을 비우고 새로 쓴다.
     */
    fun track(scope: LazyListScope): LazyListScope {
        keys.clear()
        return TrackingScope(scope, this)
    }

    internal fun record(key: Any?) {
        keys += key
    }
}

private class TrackingScope(private val inner: LazyListScope, private val index: KeyIndex) : LazyListScope by inner {
    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        index.record(key)
        inner.item(key, contentType, content)
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        repeat(count) { index.record(key?.invoke(it)) }
        inner.items(count, key, contentType, itemContent)
    }

    override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.(Int) -> Unit) {
        index.record(key)
        inner.stickyHeader(key, contentType, content)
    }
}

@Composable
fun rememberKeyIndex(): KeyIndex = remember { KeyIndex() }

/**
 * [key] item으로 스크롤한다. stickyHeader가 있으면 [headerOffsetPx](헤더 높이)만큼 덜 올려 헤더에 가려지지 않게 한다.
 * 이동 뒤 포커스가 필요하면 snapshotFlow { layoutInfo.visibleItemsInfo }로 대상 item이 그려진 것을 확인한 다음 requestFocus().
 * @return 이동했으면 true (key가 없으면 false)
 */
suspend fun LazyListState.scrollToKey(index: KeyIndex, key: String, headerOffsetPx: Int = 0): Boolean {
    val i = index.indexOf(key) ?: return false
    animateScrollToItem(i, -headerOffsetPx)
    return true
}
