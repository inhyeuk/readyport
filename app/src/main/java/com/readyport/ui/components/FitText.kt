package com.readyport.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

// ======================= 칸 폭에 맞춘 한 줄 글자 (재검토 R6) =======================

/**
 * [text]를 **한 줄에 들어가는 가장 큰 스타일**로 그린다(긴급 전화번호·큰 숫자). [styles]는 큰 것부터 작은 것 순서.
 * 실제 칸 폭을 TextMeasurer로 잰다 — SubcomposeLayout(BoxWithConstraints)을 쓰지 않으므로 TileGrid 행의 IntrinsicSize.Min 안에서도 쓸 수 있다.
 * 가장 작은 스타일로도 한 줄에 안 들어가면(아주 좁은 창) [breakChars] 글자 뒤에서만 줄을 바꿔 여러 줄로 그린다(숫자 한가운데서 끊기거나 잘리지 않음).
 * 의미 글자(TalkBack·테스트)는 언제나 [text] 원문 한 노드이고 GetTextLayoutResult로 실제 줄 수를 알려 준다. 보이지 않는 문자를 의미 글자에 넣지 않는다(3.2).
 * 스타일 목록의 마지막 값이 그 모드의 최소 글자 크기다(쉬운 모드 18sp 이상 — 부르는 쪽이 정한다).
 */
@Composable
fun FitText(
    text: String,
    styles: List<TextStyle>,
    color: Color,
    modifier: Modifier = Modifier,
    breakChars: String = "-",
) {
    require(styles.isNotEmpty())
    val measurer = rememberTextMeasurer(cacheSize = styles.size + 2)
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val policy = remember(text, styles, measurer, breakChars) { FitTextPolicy(text, styles, measurer, breakChars, layout) }
    Layout(
        modifier = modifier
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

private class FitTextPolicy(
    private val text: String,
    private val styles: List<TextStyle>,
    private val measurer: TextMeasurer,
    private val breakChars: String,
    private val layout: MutableState<TextLayoutResult?>,
) : MeasurePolicy {

    /** 가장 작은 스타일로도 넘칠 때 그리는 글: [breakChars] 글자 뒤에 줄바꿈 자리(ZWSP) — 의미 글자는 원문 */
    private val wrapped = breakAfter(text, breakChars)

    /** [breakChars] 뒤에서 나눈 묶음 (최소 고유 폭 = 가장 긴 묶음) */
    private val chunks: List<String> = buildList {
        val cur = StringBuilder()
        text.forEach { c ->
            cur.append(c)
            if (c in breakChars || c == ' ') {
                add(cur.toString())
                cur.clear()
            }
        }
        if (cur.isNotEmpty()) add(cur.toString())
    }

    private fun oneLine(style: TextStyle): TextLayoutResult =
        measurer.measure(text, style, softWrap = false, maxLines = 1)

    /** 폭 [maxWidth] 안에 한 줄로 들어가는 첫 스타일. 없으면 가장 작은 스타일로 여러 줄 */
    fun choose(maxWidth: Int): TextLayoutResult {
        for (style in styles) {
            val r = oneLine(style)
            if (r.size.width <= maxWidth) return r
        }
        return measurer.measure(wrapped, styles.last(), constraints = Constraints(maxWidth = maxWidth.coerceAtLeast(0)))
    }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val r = choose(maxWidth)
        layout.value = r
        return layout(constraints.constrainWidth(r.size.width), constraints.constrainHeight(r.size.height)) {}
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        oneLine(styles.first()).size.width

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        chunks.maxOfOrNull { measurer.measure(it, styles.last(), softWrap = false, maxLines = 1).size.width } ?: 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        choose(width).size.height

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        choose(width).size.height
}

// ======================= 줄 수를 지키는 글자 (둘러보기 히어로 한 줄, 2026-10-03) =======================

/**
 * [text]를 **[maxLines]줄 안에 들어가는 가장 큰 스타일**로 그린다 — 글자를 키우거나 창이 좁아도 줄이 늘지 않는 소개 한 줄용.
 * [styles]는 큰 것부터 작은 것 순서다. 가장 작은 스타일로도 [maxLines]줄에 안 들어가면 그 스타일로 **잘리지 않고** 더 많은 줄로 그린다
 * (내용을 자르거나 숨기지 않는다 — DESIGN_SPEC 4.1).
 * - 보이는 글은 한국어 낱말 보호([koDisplay])를 거쳐 낱말 한가운데서 줄이 바뀌지 않는다.
 * - 의미 글자(TalkBack·테스트)는 언제나 [text] 원문 한 노드이고, 실제 줄 수는 `GetTextLayoutResult`로 알려 준다.
 * [FitText]와 다른 점은 '한 줄에 맞추기'가 아니라 '[maxLines]줄에 맞추기'라는 것뿐이다.
 */
@Composable
fun FitLines(
    text: String,
    styles: List<TextStyle>,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    require(styles.isNotEmpty())
    val measurer = rememberTextMeasurer(cacheSize = styles.size + 2)
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val shown = remember(text) { koDisplay(text) }
    val policy = remember(shown, styles, measurer, maxLines) { FitLinesPolicy(shown, styles, measurer, maxLines, layout) }
    Layout(
        modifier = modifier
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

private class FitLinesPolicy(
    private val shown: String,
    private val styles: List<TextStyle>,
    private val measurer: TextMeasurer,
    private val maxLines: Int,
    private val layout: MutableState<TextLayoutResult?>,
) : MeasurePolicy {

    /** 낱말(띄어쓰기 단위) — 가장 작은 스타일의 가장 긴 낱말이 최소 고유 폭이다 */
    private val words: List<String> = shown.split(' ').filter { it.isNotEmpty() }

    private fun oneLine(style: TextStyle): TextLayoutResult = measurer.measure(shown, style, softWrap = false, maxLines = 1)

    private fun wrapped(style: TextStyle, maxWidth: Int): TextLayoutResult =
        measurer.measure(shown, style, constraints = Constraints(maxWidth = maxWidth.coerceAtLeast(0)))

    /** 폭 [maxWidth] 안에서 [maxLines]줄 안에 들어가는 첫 스타일. 없으면 가장 작은 스타일 그대로 */
    fun choose(maxWidth: Int): TextLayoutResult {
        for (style in styles) {
            val r = wrapped(style, maxWidth)
            if (r.lineCount <= maxLines) return r
        }
        return wrapped(styles.last(), maxWidth)
    }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val r = if (constraints.hasBoundedWidth) choose(constraints.maxWidth) else oneLine(styles.first())
        layout.value = r
        return layout(constraints.constrainWidth(r.size.width), constraints.constrainHeight(r.size.height)) {}
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        oneLine(styles.first()).size.width

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        words.maxOfOrNull { measurer.measure(it, styles.last(), softWrap = false, maxLines = 1).size.width } ?: 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        choose(width).size.height

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        choose(width).size.height
}
