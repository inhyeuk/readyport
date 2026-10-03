package com.readyport.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** 날짜 글자를 다루는 순수 함수 (앱 전체가 이 한 벌만 쓴다 — 예전에는 ui/trip과 ui/wallet에 두 벌이 있었다) */
class DateDigitsTest {

    @Test
    fun keepsDigitsOnly() {
        assertEquals("19740812", dateDigits("1974-08-12"))
        assertEquals("20261103", dateDigits("2026-11-03 "))
        assertEquals("12345678", dateDigits("1234567890"))
        assertEquals("", dateDigits(null))
        // 글 속 숫자도 숫자다 — 날짜 모양을 가리는 일은 parseDateDigits가 한다
        assertEquals("113", dateDigits("11월 3일"))
    }

    @Test
    fun parsesEightDigits() {
        assertEquals(LocalDate.of(1974, 8, 12), parseDateDigits("19740812"))
        assertNull(parseDateDigits("19741312"))
        assertNull(parseDateDigits("197408"))
        assertNull(parseDateDigits("2026-11-03"))
    }

    @Test
    fun convertsBackAndForth() {
        assertEquals("20261103", digitsOf("2026-11-03"))
        assertEquals("", digitsOf("11월 3일"))
        assertEquals("20261103", digitsOf(LocalDate.of(2026, 11, 3)))
        assertEquals("", digitsOf(null as LocalDate?))
    }

    @Test
    fun drawsHyphensWithoutChangingTheValue() {
        assertEquals("1974-08-12", DateDigitsTransformation.filter(AnnotatedString("19740812")).text.text)
        assertEquals("2026-1", DateDigitsTransformation.filter(AnnotatedString("20261")).text.text)
        // 커서 자리: 숫자 자리 → 보이는 글자 자리(하이픈을 건너뛴다)
        val mapping = DateDigitsTransformation.filter(AnnotatedString("20261103")).offsetMapping
        assertEquals(4, mapping.originalToTransformed(4))
        assertEquals(6, mapping.originalToTransformed(5))
        assertEquals(4, mapping.transformedToOriginal(4))
        assertEquals(4, mapping.transformedToOriginal(5))
    }

    @Test
    fun pickerMillisRoundTrip() {
        val date = LocalDate.of(2026, 11, 3)
        assertEquals(date, pickerDateOf(date.toPickerMillis()))
        assertNull(pickerDateOf(null))
    }
}

/**
 * 달력 대화상자 (다듬기 S2). **접근성 점검(A11yAuditTest)은 갤러리 화면에서 대화상자를 띄우지 않으므로** 여기서 본다:
 * 달력 단추 이름 · 칸을 눌러 열리는지 · 날짜를 고르면 값이 들어오는지 · `달력 대신 숫자로 적기`로 숫자 칸이 되는지 ·
 * 달력 날짜 칸의 보이는 크기(Material 3이 정한 48dp).
 * 검토용 캡처는 build/gallery/datepick/ 에 남긴다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h1400dp-xhdpi")
class DatePickFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private val label = "떠나는 날"
    private val openNow = "떠나는 날 달력에서 고르기, 지금 11월 3일 (화)"
    private val openEmpty = "떠나는 날 달력에서 고르기, 아직 안 골랐어요"
    private val typeName = "떠나는 날 숫자로 적기"

    /** 달력 안의 날짜 칸 (고를 수 있는 칸 = 누를 수 있고 '고름' 상태를 가진 칸) */
    private val dayCell = SemanticsMatcher("달력 날짜 칸") { n ->
        n.config.contains(SemanticsActions.OnClick) && n.config.getOrNull(SemanticsProperties.Selected) != null
    }

    /** 날짜 칸 하나를 띄우고 바뀐 값을 [changes]에 모은다 */
    private fun setField(initial: String = "20261103", easy: Boolean = false, changes: MutableList<String>) {
        val value = mutableStateOf(initial)
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                Column(
                    Modifier.fillMaxSize().background(Tokens.Ground).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DatePickField(
                        label = label,
                        value = value.value,
                        onChange = {
                            value.value = it
                            changes += it
                        },
                        note = "달력에서 고르거나 숫자로 적어요.",
                    )
                }
            }
        }
    }

    /** 읽기 전용 날짜 칸에는 SetText가 없다 — 라벨로 찾는다 */
    private fun field() = rule.onNode(hasText(label))

    /**
     * 달력을 열고 **대화상자 창이 접근성 나무에 등록될 때까지** 기다린다.
     * Robolectric에서 새 창은 waitForIdle만으로는 등록되지 않아 시계를 한 번 밀어 준다(갤러리 캡처와 같은 방법).
     */
    private fun openCalendar(trigger: () -> Unit) {
        trigger()
        pump()
    }

    /** Robolectric에서 창이 뜨고 사라지는 것·다시 그리는 것을 끝까지 흘려 보낸다 */
    private fun pump() {
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    private fun dialogOpen(): Boolean = rule.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()

    private fun labelOf(n: SemanticsNode): String =
        (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() + " " +
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()).trim()

    @Test
    fun calendarButtonSaysWhatItDoesAndWhatIsChosen() {
        setField(changes = mutableListOf())
        rule.onNodeWithContentDescription(openNow).assertIsDisplayed()
        // 칸 아래 확인 글도 한국어 날짜
        rule.onNodeWithText("11월 3일 (화)").assertIsDisplayed()
    }

    @Test
    fun emptyFieldSaysNothingChosenYet() {
        setField(initial = "", changes = mutableListOf())
        rule.onNodeWithContentDescription(openEmpty).assertIsDisplayed()
    }

    @Test
    fun calendarButtonOpensDialogAndConfirmFillsTheField() {
        val changes = mutableListOf<String>()
        setField(changes = changes)
        openCalendar { rule.onNodeWithContentDescription(openNow, useUnmergedTree = true).performClick() }
        assertTrue("달력 대화상자가 열리지 않았다", dialogOpen())
        rule.onNodeWithText("이 날로 하기").assertIsDisplayed()
        rule.onNodeWithText("이 날로 하기").performClick()
        pump()
        // 이미 고른 날이 그대로 들어오고 대화상자가 닫힌다
        assertEquals(listOf("20261103"), changes)
        assertTrue(!dialogOpen())
    }

    @Test
    fun pickingAnotherDayChangesTheValue() {
        val changes = mutableListOf<String>()
        setField(changes = changes)
        openCalendar { rule.onNodeWithContentDescription(openNow, useUnmergedTree = true).performClick() }
        val cells = rule.onAllNodes(dayCell).fetchSemanticsNodes()
        if (cells.size < 28) {
            File("build/gallery/datepick").mkdirs()
            File("build/gallery/datepick/dialog-nodes.txt").writeText(
                rule.onAllNodes(SemanticsMatcher("모든 노드") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes().joinToString("\n") { labelOf(it) },
            )
        }
        assertTrue("달력에서 날짜 칸을 ${cells.size}개만 찾았다 (dialog-nodes.txt 참고)", cells.size >= 28)
        // 마지막 칸(그 달 끝 무렵)을 골라 본다 — 어느 날인지는 달마다 다르므로 '2026년 11월'만 본다
        rule.onAllNodes(dayCell)[cells.size - 1].performClick()
        pump()
        rule.onNodeWithText("이 날로 하기").performClick()
        pump()
        val picked = changes.last()
        assertTrue("고른 날짜가 2026년 11월이 아니다: $picked", picked.startsWith("202611"))
        assertTrue("고른 날짜를 읽을 수 없다: $picked", parseDateDigits(picked) != null)
        assertTrue("고른 날짜가 11월 3일 그대로다", picked != "20261103")
    }

    @Test
    fun tappingTheFieldOpensTheCalendarToo() {
        setField(changes = mutableListOf())
        openCalendar { field().performClick() }
        assertTrue("칸을 눌러도 달력이 열려야 한다", dialogOpen())
    }

    /** 칸 아래 `숫자로 적기`는 달력을 열지 않아도 보이고, 누르면 그 칸이 숫자 자판 칸이 된다 */
    @Test
    fun typingPathIsOnTheScreenNotInTheDialog() {
        val changes = mutableListOf<String>()
        setField(initial = "", changes = changes)
        // 읽기 전용이라 아직 적을 수 없다
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithContentDescription(typeName).performClick()
        pump()
        assertTrue("`숫자로 적기`는 달력을 열지 않는다", !dialogOpen())
        // 이제 숫자 칸 — 숫자 자판으로 적을 수 있다(읽기 전용이 풀려 SetText가 생긴다)
        rule.onNode(hasSetTextAction()).performTextInput("20261107")
        pump()
        assertEquals("20261107", changes.last())
        rule.onNodeWithText("11월 7일 (토)").assertIsDisplayed()
    }

    /** 숫자로 적기로 바꾼 칸을 다시 눌러도 달력이 또 튀어나오지 않는다(커서만 옮긴다) */
    @Test
    fun calendarDoesNotPopUpAgainAfterSwitchingToTyping() {
        setField(initial = "", changes = mutableListOf())
        rule.onNodeWithContentDescription(typeName).performClick()
        pump()
        rule.onNode(hasSetTextAction()).performClick()
        pump()
        assertTrue(!dialogOpen())
        // 숫자 칸으로 바꾼 뒤에는 `숫자로 적기` 단추가 사라진다(할 일이 끝났다)
        assertTrue(rule.onAllNodes(hasContentDescriptionExactly(typeName)).fetchSemanticsNodes().isEmpty())
    }

    /** 달력 단추는 숫자 칸으로 바꾼 뒤에도 남는다 — 언제든 달력으로 돌아갈 수 있다 */
    @Test
    fun calendarButtonStaysAfterSwitchingToTyping() {
        setField(changes = mutableListOf())
        rule.onNodeWithContentDescription(typeName).performClick()
        pump()
        openCalendar { rule.onNodeWithContentDescription(openNow, useUnmergedTree = true).performClick() }
        assertTrue("숫자 칸으로 바꾼 뒤에도 달력 단추는 열려야 한다", dialogOpen())
    }

    /**
     * 달력 안 날짜 칸의 크기(기본·쉬운 모드). Material이 정한 크기라 앱 토큰으로 키울 수 없으므로 **재어서 적어 둔다** —
     * 측정값은 build/gallery/datepick/touch.txt. 터치 영역이 48dp 아래로 떨어지면 실패한다.
     */
    @Test
    fun dayCellsAreBigEnoughToTapBasic() {
        measureDayCells(easy = false)
    }

    @Test
    fun dayCellsAreBigEnoughToTapEasyMode() {
        measureDayCells(easy = true)
    }

    private fun measureDayCells(easy: Boolean): String {
        setField(easy = easy, changes = mutableListOf())
        openCalendar { rule.onNodeWithContentDescription(openNow, useUnmergedTree = true).performClick() }
        val cells = rule.onAllNodes(dayCell).fetchSemanticsNodes()
        assertTrue("달력에서 날짜 칸을 찾지 못했다", cells.size >= 28)
        val d = rule.density.density
        val w = cells.minOf { it.boundsInRoot.width } / d
        val h = cells.minOf { it.boundsInRoot.height } / d
        val tw = cells.minOf { it.touchBoundsInRoot.width } / d
        val th = cells.minOf { it.touchBoundsInRoot.height } / d
        val mode = if (easy) "쉬운 모드" else "기본 모드"
        val line = "$mode: 날짜 칸 ${cells.size}개 · 보이는 크기 " + "%.1f×%.1f".format(w, h) + "dp · 터치 영역 " +
            "%.1f×%.1f".format(tw, th) + "dp"
        File("build/gallery/datepick").mkdirs()
        File("build/gallery/datepick/touch-${if (easy) "easy" else "basic"}.txt").writeText(line + System.lineSeparator())
        println("DATEPICK $line")
        assertTrue("$mode 달력 날짜 칸의 터치 영역이 48dp보다 작다 — $line", minOf(tw, th) >= 47.9f)
        return line
    }

    /** 검토용 캡처 ①: 날짜 칸(달력 닫힘) — 달력 단추가 붙은 모습 */
    @Test
    fun capturesFieldClosed() {
        setField(changes = mutableListOf())
        val dir = File("build/gallery/datepick").apply { mkdirs() }
        write(rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap(), File(dir, "00_field_closed.png"))
    }

    /**
     * 검토용 캡처 ②: 열린 달력. Robolectric은 대화상자 **창**을 찍지 못하므로(갤러리 캡처도 같다)
     * 대화상자가 그리는 것과 **같은 부품**([DatePickCalendar])을 흰 카드 안에 그대로 띄워 찍는다.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun capturesCalendar() {
        rule.setContent {
            ReadyPortTheme {
                Column(Modifier.fillMaxSize().background(Tokens.Ground).padding(16.dp)) {
                    Surface(color = Tokens.Surface, shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge) {
                        DatePickCalendar(
                            state = rememberDatePickState(LocalDate.of(2026, 11, 3), DateRules()),
                            title = "떠나는 날 고르기",
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val dir = File("build/gallery/datepick").apply { mkdirs() }
        write(rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap(), File(dir, "01_calendar.png"))
    }

    private fun write(bmp: Bitmap, file: File) {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
