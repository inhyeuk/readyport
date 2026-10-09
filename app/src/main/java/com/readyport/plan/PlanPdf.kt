package com.readyport.plan

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.readyport.R
import java.io.ByteArrayOutputStream

// ======================= 계획 PDF (휴대폰 안에서 만든다 — 서버에 올리지 않는다) =======================
// 배치(줄 나누기·쪽 나누기)는 순수 함수([PlanPdfLayout] — 글자 폭 재기만 바꿔 끼워 테스트), 그리기는 android.graphics.pdf.PdfDocument.
// 글꼴은 앱에 든 Pretendard(한글) — 시스템 글꼴에 기대지 않는다.

/** 글 모양 (pt) */
enum class PdfStyle(val size: Float, val bold: Boolean, val color: Int, val gapBefore: Float) {
    Title(20f, true, 0xFF16213A.toInt(), 0f),
    Subtitle(12f, false, 0xFF4A5468.toInt(), 4f),
    Notice(10.5f, true, 0xFF7A4100.toInt(), 10f),
    Section(15f, true, 0xFF1F4FD1.toInt(), 18f),
    ItemTitle(12f, true, 0xFF16213A.toInt(), 8f),
    ItemNote(11f, false, 0xFF4A5468.toInt(), 1f),
    Bullet(11f, false, 0xFF16213A.toInt(), 4f),
    Footer(8.5f, false, 0xFF5B6577.toInt(), 0f),
    ;

    val lineHeight: Float get() = size * 1.45f
}

/** 배치할 덩어리 하나. [keepWithNext]: 쪽 끝에 홀로 남지 않게(제목은 다음 덩어리 첫 줄과 같은 쪽에) */
data class PdfBlock(val text: String, val style: PdfStyle, val indent: Float = 0f, val keepWithNext: Boolean = false)

/** 그릴 줄 하나 — [y]는 글자 바닥선 */
data class PdfLine(val text: String, val x: Float, val y: Float, val style: PdfStyle)

data class PdfPage(val number: Int, val lines: List<PdfLine>)

/** 글자 폭 재기 (운영: Paint, 테스트: 가짜) */
fun interface PdfMeasure {
    fun width(text: String, style: PdfStyle): Float
}

/** A4 세로 (pt = 1/72인치) */
object PdfPageSize {
    const val WIDTH = 595
    const val HEIGHT = 842
    const val MARGIN_X = 48f
    const val MARGIN_TOP = 56f

    /** 아래 여백 — 쪽 번호 줄 자리 포함 */
    const val MARGIN_BOTTOM = 64f
    const val CONTENT_WIDTH = WIDTH - MARGIN_X * 2
}

object PlanPdfLayout {
    /** 글 한 덩어리 → 폭 안에 들어가는 줄들 (낱말 단위, 한 낱말이 넘치면 글자 단위) */
    fun wrap(text: String, width: Float, style: PdfStyle, measure: PdfMeasure): List<String> {
        val out = mutableListOf<String>()
        text.split('\n').forEach { para ->
            var line = ""
            para.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (measure.width(candidate, style) <= width) {
                    line = candidate
                } else {
                    if (line.isNotEmpty()) out += line
                    line = ""
                    // 한 낱말이 폭보다 길면 글자 단위로 자른다
                    var rest = word
                    while (measure.width(rest, style) > width && rest.length > 1) {
                        var n = rest.length - 1
                        while (n > 1 && measure.width(rest.take(n), style) > width) n--
                        out += rest.take(n)
                        rest = rest.drop(n)
                    }
                    line = rest
                }
            }
            if (line.isNotEmpty() || para.isBlank()) out += line
        }
        return out.ifEmpty { listOf("") }
    }

    /**
     * 덩어리들 → 쪽들. 덩어리 앞 간격은 쪽 맨 위에서는 뺀다. [PdfBlock.keepWithNext]는 다음 덩어리 첫 줄까지 같은 쪽에 들어가야 한다.
     * 긴 덩어리는 줄 단위로 다음 쪽에 이어진다. 쪽 번호 줄(`n / 전체`)과 [footer]를 모든 쪽 아래에 둔다.
     */
    fun layout(blocks: List<PdfBlock>, measure: PdfMeasure, footer: String): List<PdfPage> {
        val bottom = PdfPageSize.HEIGHT - PdfPageSize.MARGIN_BOTTOM
        val pages = mutableListOf<MutableList<PdfLine>>(mutableListOf())
        var y = PdfPageSize.MARGIN_TOP
        fun newPage() {
            pages += mutableListOf<PdfLine>()
            y = PdfPageSize.MARGIN_TOP
        }
        val wrapped = blocks.map { b -> b to wrap(b.text, PdfPageSize.CONTENT_WIDTH - b.indent, b.style, measure) }

        // 이 덩어리를 시작하려면 이 쪽에 있어야 하는 높이: 보통은 첫 줄, 붙여 둘 덩어리(제목)면 전부 + 다음 덩어리의 그 높이(최대 3단)
        fun minNeed(i: Int, depth: Int = 0): Float {
            val (b, lines) = wrapped[i]
            if (!b.keepWithNext || depth >= 3) return b.style.lineHeight
            val next = wrapped.getOrNull(i + 1) ?: return lines.size * b.style.lineHeight
            return lines.size * b.style.lineHeight + next.first.style.gapBefore + minNeed(i + 1, depth + 1)
        }
        wrapped.forEachIndexed { i, (block, lines) ->
            val atTop = pages.last().isEmpty()
            val gap = if (atTop) 0f else block.style.gapBefore
            val need = gap + minNeed(i)
            if (!atTop && y + need > bottom) newPage()
            if (pages.last().isNotEmpty()) y += block.style.gapBefore
            lines.forEach { text ->
                if (y + block.style.lineHeight > bottom && pages.last().isNotEmpty()) newPage()
                y += block.style.lineHeight
                pages.last() += PdfLine(text, PdfPageSize.MARGIN_X + block.indent, y - block.style.lineHeight * 0.28f, block.style)
            }
        }
        val total = pages.size
        return pages.mapIndexed { i, lines ->
            val fy = PdfPageSize.HEIGHT - PdfPageSize.MARGIN_BOTTOM / 2
            val number = "${i + 1} / $total"
            val footerLines = listOf(
                PdfLine(footer, PdfPageSize.MARGIN_X, fy, PdfStyle.Footer),
                PdfLine(number, PdfPageSize.WIDTH - PdfPageSize.MARGIN_X - measure.width(number, PdfStyle.Footer), fy, PdfStyle.Footer),
            )
            PdfPage(i + 1, lines + footerLines)
        }
    }
}

/** 화면 문구(한국어)를 넘겨받아 PDF 덩어리를 만든다 — 문구는 부르는 쪽이 strings 에서 고른다 */
data class PlanPdfText(
    val title: String,
    val subtitle: String,
    val notice: String,
    val dayTitle: (PlanDay) -> String,
    val timeHint: (TimeHint?) -> String?,
    val placeName: (String) -> String?,
    val tipsTitle: String,
    val budgetTitle: String,
    val caveatsTitle: String,
    val footer: String,
)

object PlanPdfContent {
    fun blocks(plan: PlanResult, t: PlanPdfText): List<PdfBlock> = buildList {
        add(PdfBlock(t.title, PdfStyle.Title))
        add(PdfBlock(t.subtitle, PdfStyle.Subtitle))
        add(PdfBlock(t.notice, PdfStyle.Notice))
        plan.days.forEach { day ->
            add(PdfBlock(t.dayTitle(day), PdfStyle.Section, keepWithNext = true))
            day.items.forEach { item ->
                val hint = t.timeHint(item.timeHint)
                val place = item.placeId?.let(t.placeName)
                val head = listOfNotNull(hint?.let { "[$it]" }, item.title).joinToString(" ")
                val hasNote = item.note.isNotBlank() || place != null
                add(PdfBlock(head, PdfStyle.ItemTitle, indent = 8f, keepWithNext = hasNote))
                place?.let { add(PdfBlock("· $it", PdfStyle.ItemNote, indent = 20f)) }
                if (item.note.isNotBlank()) add(PdfBlock(item.note, PdfStyle.ItemNote, indent = 20f))
            }
        }
        listOf(t.tipsTitle to plan.tips, t.budgetTitle to plan.budgetNotes, t.caveatsTitle to plan.caveats).forEach { (title, lines) ->
            if (lines.isEmpty()) return@forEach
            add(PdfBlock(title, PdfStyle.Section, keepWithNext = true))
            lines.forEach { add(PdfBlock("· $it", PdfStyle.Bullet, indent = 8f)) }
        }
    }
}

/** 실제 PDF 그리기 (A4, 앱 내장 한글 글꼴) */
object PlanPdfRenderer {
    fun render(context: Context, plan: PlanResult, text: PlanPdfText): ByteArray {
        val regular = runCatching { ResourcesCompat.getFont(context, R.font.pretendard_std_regular) }.getOrNull() ?: Typeface.DEFAULT
        val bold = runCatching { ResourcesCompat.getFont(context, R.font.pretendard_std_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val paints = PdfStyle.entries.associateWith { s ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = if (s.bold) bold else regular
                textSize = s.size
                color = s.color
            }
        }
        val measure = PdfMeasure { str, style -> paints.getValue(style).measureText(str) }
        val pages = PlanPdfLayout.layout(PlanPdfContent.blocks(plan, text), measure, text.footer)
        val doc = PdfDocument()
        try {
            pages.forEach { p ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PdfPageSize.WIDTH, PdfPageSize.HEIGHT, p.number).create())
                page.canvas.drawColor(Color.WHITE)
                p.lines.forEach { l -> page.canvas.drawText(l.text, l.x, l.y, paints.getValue(l.style)) }
                doc.finishPage(page)
            }
            return ByteArrayOutputStream().use { out ->
                doc.writeTo(out)
                out.toByteArray()
            }
        } finally {
            doc.close()
        }
    }
}
