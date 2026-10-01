package com.readyport.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.readyport.ui.components.KoreanBreak
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.ui.components.SourceTextCheck
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 접근성 점검 (M10, DESIGN_SPEC 8장 0단계 강화): 누를 수 있는 모든 요소가
 *  ① 터치 영역 48dp 이상 — **쉬운 모드는 56dp 이상** (PRD 3.2, DESIGN_SPEC 3.4 minTouch)
 *  ② TalkBack이 읽을 이름(글자 또는 설명)이 있다.
 * 같은 루프에서 화면 글자에 내부 ID(`tat_chanthaburi`)나 `출처 출처`가 보이지 않는지도 본다 (DESIGN_SPEC 4.5).
 * 글자 폭을 실제로 재도록 NATIVE 그래픽(LEGACY는 글자 하나를 1px로 재서 줄바꿈·화면 길이가 실제와 다르다)으로 돌린다.
 * 화면을 아주 길게 잡아(h8000dp·글자 200%는 h12000dp) 목록 항목이 모두 그려지게 하고,
 * ③ 그래도 스크롤이 남으면(= 아래쪽 항목이 그려지지 않아 점검에서 빠짐) 실패한다.
 *
 * 터치 영역(①)은 touchBoundsInRoot — 테마가 쉬운 모드에서 ViewConfiguration.minimumTouchTargetSize를 56dp로 주므로
 * 눈에 보이는 크기가 48dp인 clickable도 터치 영역은 56dp로 넓어져 통과한다(Material 터치 목표 규칙상 맞음).
 * 그래서 ④ **보이는 크기**(boundsInRoot)도 따로 잰다 — 2단계에서 모든 화면 0건을 확인하고 엄격(실패)으로 바꿨다.
 * ⑤ 한국어 줄바꿈 보고(실패 아님): 줄이 한글 낱말 한가운데서 바뀐 곳(`처음이에/요`)과 한글 한 음절만 남은 줄을
 * build/a11y/word-breaks-<클래스>-<모드>.txt에 남긴다 — 캡처 검토(2단계 8장)의 길잡이. 낱말이 한 줄보다 길면 생길 수 있다.
 */
abstract class A11yAuditBase {

    @get:Rule
    val rule = createComposeRule()

    /** 모든 화면은 Gallery 한곳에서 관리한다 (디자인 캡처와 같은 목록) */
    private fun screens(): List<Pair<String, @Composable () -> Unit>> = Gallery.screens()

    private fun label(n: SemanticsNode): String? {
        val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val desc = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        return listOfNotNull(text, desc).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private val anyNode = SemanticsMatcher("모든 노드") { true }

    private val invisible = setOf(KoreanBreak.WORD_JOINER, KoreanBreak.ZERO_WIDTH_SPACE)

    /** ⑤ 글자 노드의 줄바꿈 중 한글 낱말 한가운데서 바뀐 곳·한 음절만 남은 줄 */
    private fun wordBreaks(name: String): List<String> {
        val out = mutableListOf<String>()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().forEach { n ->
                val results = mutableListOf<TextLayoutResult>()
                runCatching { n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
                val layout = results.firstOrNull() ?: return@forEach
                val t = layout.layoutInput.text.text
                for (line in 0 until layout.lineCount) {
                    val start = layout.getLineStart(line)
                    val end = layout.getLineEnd(line)
                    val content = t.substring(start, end).filterNot { it in invisible }.trim()
                    if (layout.lineCount > 1 && content.length == 1 && KoreanBreak.isHangul(content[0])) {
                        out += "$name: 한 음절 줄 '$content' — ${t.filterNot { it in invisible }.replace('\n', '⏎')}"
                    }
                    if (line == layout.lineCount - 1 || end <= 0 || end >= t.length) continue
                    val before = t[end - 1]
                    val after = t.substring(end).firstOrNull { it !in invisible } ?: continue
                    if (KoreanBreak.isHangul(before) && KoreanBreak.isHangul(after)) {
                        val clean = t.filterNot { it in invisible }
                        out += "$name: 낱말 중간 줄바꿈 '${t.substring(maxOf(start, end - 6), end).filterNot { it in invisible }}/" +
                            "${t.substring(end, minOf(t.length, end + 6)).filterNot { it in invisible }}' — ${clean.replace('\n', '⏎')}"
                    }
                }
            }
        return out.distinct()
    }

    protected fun audit(easyMode: Boolean) {
        val problems = mutableListOf<String>()
        val breaks = mutableListOf<String>()
        var audited = 0
        var current by androidx.compose.runtime.mutableStateOf(0)
        val list = screens()
        val minDp = if (easyMode) 56 else 48
        rule.setContent {
            ReadyPortTheme(easyMode = easyMode) { list[current].second() }
        }
        list.forEachIndexed { i, (name, _) ->
            rule.runOnIdle { current = i }
            rule.waitForIdle()
            val density = rule.density.density
            val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            audited += nodes.size
            nodes.forEach { n ->
                val b = n.touchBoundsInRoot
                val minPx = minDp * density - 1
                val lbl = label(n)
                if (b.width < minPx || b.height < minPx) {
                    problems += "$name: 터치 영역 ${(b.width / density).toInt()}x${(b.height / density).toInt()}dp < ${minDp}dp ($lbl)"
                }
                if (lbl == null) problems += "$name: 이름 없는 버튼 (${b})"
                // ④ 보이는 크기 (터치 영역 확장 없이) — 2단계부터 모든 화면 엄격
                val v = n.boundsInRoot
                if (v.width < minPx || v.height < minPx) {
                    problems += "$name: 보이는 크기 ${(v.width / density).toInt()}x${(v.height / density).toInt()}dp < ${minDp}dp ($lbl)"
                }
            }
            // ③ 화면이 qualifiers 높이보다 길면 아래쪽 항목이 그려지지 않아 점검에서 빠진다
            val cut = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .fetchSemanticsNodes()
                .any { n -> n.config[SemanticsProperties.VerticalScrollAxisRange].let { it.maxValue() > it.value() + 0.5f } }
            if (cut) problems += "$name: 화면이 감사 높이보다 길어 아래쪽을 점검하지 못함 — qualifiers 높이를 올리거나 화면을 나눌 것"

            val texts = rule.onAllNodes(anyNode, useUnmergedTree = true).fetchSemanticsNodes().flatMap { n ->
                n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() +
                    n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }
            SourceTextCheck.leaks(texts).forEach { problems += "$name: 내부 ID 또는 '출처 출처'가 보임: $it" }
            breaks += wordBreaks(name)
        }
        println("A11Y audited=$audited easy=$easyMode wordBreaks=${breaks.size}")
        File("build/a11y").mkdirs()
        File("build/a11y/word-breaks-${javaClass.simpleName}-${if (easyMode) "easy" else "basic"}.txt")
            .writeText(breaks.joinToString("\n", postfix = if (breaks.isEmpty()) "" else "\n"))
        assertTrue("점검한 버튼이 너무 적음: $audited", audited > 60)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

private operator fun <T> androidx.compose.runtime.MutableState<T>.getValue(thisObj: Any?, p: kotlin.reflect.KProperty<*>) = value
private operator fun <T> androidx.compose.runtime.MutableState<T>.setValue(thisObj: Any?, p: kotlin.reflect.KProperty<*>, v: T) { value = v }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h8000dp")
class A11yAuditTest : A11yAuditBase() {
    @Test fun normalMode() = audit(easyMode = false)
    @Test fun easyMode() = audit(easyMode = true)
}

/** 글자 크기 200% (시스템 설정 최대)에서도 모든 화면이 그려지고 같은 규칙을 지킨다 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h12000dp", fontScale = 2.0f)
class A11yAuditLargeFontTest : A11yAuditBase() {
    @Test fun easyModeLargeFont() = audit(easyMode = true)
}

/**
 * 테스트 폰(S10 5G, Android 12)과 같은 sdk 31에서 200% (DESIGN_SPEC 8장 0단계).
 * API 34+는 글자 확대가 비선형이라 sdk 36의 200%는 S10(선형 2배)보다 덜 커지고, LineBreak도 33 미만에서는 효과가 없다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h12000dp", fontScale = 2.0f)
class A11yAuditSdk31Test : A11yAuditBase() {
    @Test fun normalModeLargeFont() = audit(easyMode = false)
    @Test fun easyModeLargeFont() = audit(easyMode = true)
}
