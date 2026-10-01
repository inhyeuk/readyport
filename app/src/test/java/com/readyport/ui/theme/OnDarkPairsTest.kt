package com.readyport.ui.theme

import androidx.compose.ui.graphics.Color
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.NewsStyle
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.emergencyColors
import com.readyport.ui.components.newsColors
import com.readyport.ui.components.passportCardColors
import com.readyport.ui.components.secondaryButtonColors
import com.readyport.ui.components.tileColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 어두운 채움(Accent·Navy·AccentDeep·BrandBlue) 위 금지 색 쌍 (DESIGN_SPEC D18, 3.8).
 * ① 부품의 색 선택 함수가 돌려주는 글자·아이콘 색이 3.1절 onDark 허용 목록 안인지
 * ② ui/ 소스에서 어두운 채움 블록 안에 Help·DangerText·InkTertiary·InkSecondary·Accent 글자·아이콘 색(그리고 BrandBlue 위 Gold)을
 *    직접 쓰는 곳이 없는지 정적으로 검사한다.
 */
class OnDarkPairsTest {

    private fun assertAllowed(what: String, background: Color, colors: List<Color>) {
        val allowed = OnDark.allowedOn(background)
        assertTrue("$what: 허용 목록이 비어 있음", allowed.isNotEmpty())
        colors.forEachIndexed { i, c -> assertTrue("$what[$i] = $c 는 onDark 허용 색이 아님", c in allowed) }
    }

    @Test
    fun componentColorChoicesStayInOnDarkSet() {
        for (style in listOf(NewsStyle.Accent, NewsStyle.Navy)) {
            val c = newsColors(style, BadgeTone.Help)
            assertAllowed("CardNewsCard($style)", c.container, listOf(c.title, c.body, c.eyebrow, c.badge.content))
            assertEquals(BadgeTone.OnDark, c.badge)
            assertTrue("CardNewsCard($style) 출처는 onColor", c.onColor)
        }
        tileColors(emphasized = true).let { t ->
            assertAllowed("IconTile(emphasized)", t.container, listOf(t.label, t.supporting, t.chevron, t.badge.content))
        }
        emergencyColors(large = true).let { e ->
            assertAllowed("EmergencyCallTile(large)", e.container, listOf(e.number, e.label, e.note, e.call, e.badge.content))
            assertEquals("어두운 타일에는 막대를 두지 않는다", null, e.bar)
        }
        for (bg in listOf(Tokens.Accent, Tokens.Navy)) {
            val b = secondaryButtonColors(onDark = true)
            assertAllowed("SecondaryButton(onDark) on $bg", bg, listOf(b.content, b.border))
        }
        passportCardColors().let { p ->
            for (bg in listOf(p.gradientTop, p.gradientBottom)) {
                assertAllowed("PassportCard on $bg", bg, listOf(p.eyebrow, p.label, p.value, p.icon))
            }
        }
        // BrandBlue 위에는 Surface만 (Gold 4.06, White85 4.34 금지)
        assertEquals(setOf(Tokens.Surface), OnDark.allowedOn(Tokens.BrandBlue))
    }

    // ---------------- 소스 정적 검사 ----------------

    private val darkFill = Regex(
        """CardTone\.(Navy|Accent)\b|NewsStyle\.(Navy|Accent)\b|background\(\s*Tokens\.(Navy|Accent|AccentDeep|BrandBlue)\b|""" +
            """\b(containerColor|color)\s*=\s*Tokens\.(Navy|Accent|AccentDeep|BrandBlue)\b""",
    )
    private val forbiddenContent = Regex(
        """\b(color|tint|contentColor|iconColor|textColor|labelColor)\s*=\s*Tokens\.(Help|DangerText|InkTertiary|InkSecondary|Accent)\b|""" +
            """colorScheme\.(onSurfaceVariant|primary|tertiary|error)\b""",
    )
    private val goldUse = Regex("""Tokens\.Gold\b""")

    /** `color = Tokens.Navy`는 Surface(color = …)일 때만 채움이다 (Text(color = …)는 글자 색) */
    private fun isFill(src: String, m: MatchResult): Boolean {
        if (!m.value.startsWith("color")) return true
        val paren = enclosingParen(src, m.range.first)
        if (paren < 0) return false
        return src.substring(0, paren).trimEnd().endsWith("Surface")
    }

    /** [from] 위치를 감싸는 가장 안쪽 '(' 의 index (없으면 -1). 중괄호 블록을 벗어나면 멈춘다 */
    private fun enclosingParen(src: String, from: Int): Int {
        var depth = 0
        var i = from - 1
        while (i >= 0) {
            when (src[i]) {
                ')' -> depth++
                '(' -> if (depth == 0) return i else depth--
                '{' -> if (depth == 0) return -1
                '}' -> depth++
            }
            i--
        }
        return -1
    }

    private fun matching(src: String, open: Int, openCh: Char, closeCh: Char): Int {
        var depth = 0
        for (i in open until src.length) {
            if (src[i] == openCh) depth++
            if (src[i] == closeCh) { depth--; if (depth == 0) return i }
        }
        return src.length - 1
    }

    /** 어두운 채움 표시가 들어 있는 호출의 인자 + 뒤따르는 람다 블록 범위 */
    private fun fillScope(src: String, markerAt: Int): IntRange {
        var paren = enclosingParen(src, markerAt)
        var scope = markerAt..markerAt
        repeat(4) {
            if (paren < 0) return scope
            val close = matching(src, paren, '(', ')')
            scope = paren..close
            var j = close + 1
            while (j < src.length && src[j].isWhitespace()) j++
            if (j < src.length && src[j] == '{') return paren..matching(src, j, '{', '}')
            paren = enclosingParen(src, paren)
        }
        return scope
    }

    @Test
    fun noForbiddenColorsInsideDarkFills() {
        val root = File("src/main/java/com/readyport/ui")
        assertTrue("소스 폴더 없음: ${root.absolutePath}", root.isDirectory)
        val problems = mutableListOf<String>()
        var scopes = 0
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val src = file.readText()
            darkFill.findAll(src).filter { isFill(src, it) }.forEach { m ->
                val scope = fillScope(src, m.range.first)
                scopes++
                val text = src.substring(scope.first, scope.last + 1)
                val base = scope.first
                forbiddenContent.findAll(text).forEach { f ->
                    val lineStart = src.lastIndexOf('\n', base + f.range.first) + 1
                    val lineEnd = src.indexOf('\n', base + f.range.first).let { if (it < 0) src.length else it }
                    val line = src.substring(lineStart, lineEnd)
                    // 흰 버튼(containerColor = Surface) 안의 Accent·Navy 글자는 자기 바탕 위라 허용
                    if ("containerColor = Tokens.Surface" in line) return@forEach
                    val lineNo = src.substring(0, base + f.range.first).count { it == '\n' } + 1
                    problems += "${file.name}:$lineNo ${m.value} 안에 ${f.value}"
                }
                if ("BrandBlue" in m.value) {
                    goldUse.findAll(text).forEach { g ->
                        val lineNo = src.substring(0, base + g.range.first).count { it == '\n' } + 1
                        problems += "${file.name}:$lineNo BrandBlue 위 Gold"
                    }
                }
            }
        }
        println("ONDARK scopes=$scopes problems=${problems.size}")
        assertTrue("어두운 채움 블록을 하나도 찾지 못함", scopes > 5)
        assertTrue(problems.distinct().joinToString("\n"), problems.isEmpty())
    }
}
