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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
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

/** 2열 한 칸이 이 폭(dp ÷ fontScale)보다 좁으면 1열로 (D4) */
private const val MIN_COLUMN_DP = 150f

/**
 * 타일 그리드 열 수 (D4). 한 곳에서만 계산한다.
 * - 쉬운 모드 → 항상 1열
 * - 기본 모드 → 2열 한 칸 폭 `(창 폭 − 2×screenPadding − gap) / 2`를 fontScale로 나눈 값이 150 미만이면 1열
 *   (393dp 폭이면 fontScale 약 1.14 이상, 360dp 폭이면 약 1.03 이상, 340dp 미만이면 항상 1열)
 * 창 폭은 LocalWindowInfo로 읽는다(LocalConfiguration.screenWidthDp는 lint ConfigurationScreenWidthHeight 경고).
 * 창 폭을 아직 모르면(0) 안전하게 1열.
 */
@Composable
fun rememberGridColumns(preferred: Int = 2): Int {
    val dimens = LocalDimens.current
    if (dimens.easyMode || preferred <= 1) return 1
    val widthDp = windowWidthDp()
    if (widthDp <= 0f) return 1
    val colDp = (widthDp - 2 * dimens.screenPadding.value - dimens.gap.value) / 2f
    return if (colDp / LocalDensity.current.fontScale < MIN_COLUMN_DP) 1 else preferred
}

/** 창 폭(dp). 아직 모르면 0 */
@Composable
internal fun windowWidthDp(): Float {
    val widthPx = LocalWindowInfo.current.containerSize.width
    return if (widthPx <= 0) 0f else widthPx / LocalDensity.current.density
}

/** 340dp 미만의 좁은 창 (320×470 화면 예산, DESIGN_SPEC 6장 머리말) */
internal const val NARROW_WINDOW_DP = 340f

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
