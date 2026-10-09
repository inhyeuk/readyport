package com.readyport.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PDF 배치(줄 나누기·쪽 나누기) — 글자 폭은 가짜(글자마다 크기 × 0.6, 한글은 × 1.0). 그리기(PdfDocument)는 기기에서만.
 */
class PlanPdfLayoutTest {
    private val measure = PdfMeasure { text, style -> text.sumOf { c -> (if (c in '가'..'힣') 1.0 else 0.6) * style.size.toDouble() }.toFloat() }

    @Test fun wrapsAtWordsAndBreaksLongWords() {
        val width = 12f * 10 // 한글 10자
        val lines = PlanPdfLayout.wrap("가나다 라마바사 아자차카타파하 가나", width, PdfStyle.ItemTitle, measure)
        lines.forEach { assertTrue("$it 넘침", measure.width(it, PdfStyle.ItemTitle) <= width) }
        assertEquals("가나다 라마바사 아자차카타파하 가나".replace(" ", ""), lines.joinToString("").replace(" ", ""))
        // 10자보다 긴 낱말은 글자 단위로
        val long = PlanPdfLayout.wrap("가".repeat(25), width, PdfStyle.ItemTitle, measure)
        assertEquals(listOf("가".repeat(10), "가".repeat(10), "가".repeat(5)), long)
        // 줄바꿈은 지킨다
        assertEquals(listOf("가", "나"), PlanPdfLayout.wrap("가\n나", width, PdfStyle.ItemTitle, measure))
    }

    private fun plan(days: Int, items: Int): PlanResult = PlanResult(
        requestId = "r",
        country = "JP",
        days = (1..days).map { d ->
            PlanDay(d, "하루 제목 $d", (1..items).map { i -> PlanItem(TimeHint.entries[i % 6], if (i == 1) "sensoji" else null, "할 일 $i 구경하고 쉬기", "설명 ".repeat(30).trim()) })
        },
        tips = listOf("팁 하나", "팁 둘"),
        budgetNotes = listOf("예산은 보통이에요"),
        caveats = listOf("출발 전에 공식 안내를 꼭 확인하세요."),
        noticeKo = null,
        createdAt = null,
    )

    private val text = PlanPdfText(
        title = "여행 계획 · 일본 5일",
        subtitle = "레디포트에서 받은 AI 여행 계획 초안",
        notice = "AI가 만든 계획이에요. 출발 전 공식 안내로 다시 확인하세요.",
        dayTitle = { "${it.day}일째 · ${it.title}" },
        timeHint = { it?.name },
        placeName = { if (it == "sensoji") "센소지" else null },
        tipsTitle = "알아 두면 좋아요",
        budgetTitle = "예산",
        caveatsTitle = "꼭 확인할 것",
        footer = "레디포트 · AI가 만든 계획이에요.",
    )

    @Test fun contentBlocksInOrder() {
        val blocks = PlanPdfContent.blocks(plan(1, 2), text)
        assertEquals(PdfStyle.Title, blocks[0].style)
        assertEquals(PdfStyle.Notice, blocks[2].style)
        assertEquals("1일째 · 하루 제목 1", blocks[3].text)
        assertTrue(blocks[3].keepWithNext)
        assertTrue(blocks.any { it.text == "· 센소지" })
        assertEquals(listOf("알아 두면 좋아요", "예산", "꼭 확인할 것"), blocks.filter { it.style == PdfStyle.Section && it.text.length < 10 }.map { it.text })
    }

    @Test fun paginatesA4WithFooterOnEveryPage() {
        val pages = PlanPdfLayout.layout(PlanPdfContent.blocks(plan(7, 6), text), measure, text.footer)
        assertTrue("여러 쪽이어야 한다", pages.size > 2)
        val bottom = PdfPageSize.HEIGHT - PdfPageSize.MARGIN_BOTTOM
        pages.forEachIndexed { i, p ->
            assertEquals(i + 1, p.number)
            val body = p.lines.filter { it.style != PdfStyle.Footer }
            assertTrue(body.isNotEmpty())
            body.forEach { l ->
                assertTrue("쪽 아래로 넘침: ${l.y}", l.y <= bottom)
                assertTrue(l.y >= PdfPageSize.MARGIN_TOP)
                assertTrue("오른쪽으로 넘침", l.x + measure.width(l.text, l.style) <= PdfPageSize.WIDTH - PdfPageSize.MARGIN_X + 0.01f)
            }
            val footer = p.lines.filter { it.style == PdfStyle.Footer }.map { it.text }
            assertTrue(text.footer in footer)
            assertTrue("${i + 1} / ${pages.size}" in footer)
            // 날 제목이 쪽 맨 끝에 홀로 남지 않는다
            assertFalse("${p.number}쪽 끝이 제목", body.last().style == PdfStyle.Section)
        }
        // 글이 빠지지 않는다(모든 날 제목이 어딘가에)
        val all = pages.flatMap { it.lines }.map { it.text }
        (1..7).forEach { d -> assertTrue(all.any { it.startsWith("${d}일째") }) }
    }

    @Test fun shortPlanIsOnePage() {
        val pages = PlanPdfLayout.layout(PlanPdfContent.blocks(plan(1, 1), text), measure, text.footer)
        assertEquals(1, pages.size)
        assertTrue(pages[0].lines.any { it.text == "1 / 1" })
    }
}
