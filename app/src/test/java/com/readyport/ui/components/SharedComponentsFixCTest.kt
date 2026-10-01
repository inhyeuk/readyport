package com.readyport.ui.components

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 재검토 수정 C(통합): 화면에 있던 임시 부품을 공용으로 올린 것의 동작.
 * 기기 언어를 **영어**로 둔다 — 앱 글자 스타일이 한국어를 스스로 정해 어절 줄바꿈이 기기 언어와 상관없이 맞는지 함께 본다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "en-rUS-w393dp-h851dp")
class SharedComponentsFixCTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    /**
     * 제목은 띄어쓰기에서만 줄을 바꾼다 — 기기 언어가 영어여도 (`태국 입국 카드 (TDAC)/를`처럼 낱말 안에서 끊기지 않게).
     * 글자는 그대로다(getString 원문과 같다).
     */
    @Test
    fun koreanTitlesBreakOnlyAtSpacesOnNonKoreanDevice() {
        val title = context.getString(R.string.today_task_form_title, "태국 입국 카드 (TDAC)")
        var width by mutableStateOf(160)
        rule.setContent {
            ReadyPortTheme {
                Box(Modifier.width(width.dp)) { KoText(title, MaterialTheme.typography.titleLarge, heading = true) }
            }
        }
        var wrapped = 0
        for (w in 160..340 step 12) {
            width = w
            rule.waitForIdle()
            val layout = layoutOf(title)
            val shown = layout.layoutInput.text.text
            for (line in 1 until layout.lineCount) {
                val start = layout.getLineStart(line)
                assertEquals("w=$w: 낱말 안에서 줄이 바뀜 — ${shown.substring(0, start)} / ${shown.substring(start)}", ' ', shown[start - 1])
                wrapped++
            }
        }
        assertTrue("줄이 바뀌는 폭을 하나도 확인하지 못함", wrapped > 3)
    }

    /** 숫자 타일 값은 칸 폭에 맞춘 한 줄, 2열 홀수면 마지막 타일이 폭 전체(재검토 R12 — 공용 StatTile·FactGrid) */
    @Test
    fun statTileValuesStayOnOneLineAndOddLastTileIsWide() {
        val facts = listOf(
            Fact(Icons.Outlined.Payments, "IDR 500,000", "비자 비용"),
            Fact(Icons.Outlined.EventAvailable, "30일", "머물 수 있어요"),
            Fact(Icons.Outlined.DateRange, "4박 5일", "여행 기간"),
        )
        rule.setContent { ReadyPortTheme { Box(Modifier.width(300.dp).testTag("grid")) { FactGrid(facts, columns = 2) } } }
        rule.waitForIdle()
        assertEquals(1, layoutOf("30일").lineCount)
        assertEquals(1, layoutOf("4박 5일").lineCount)
        // 통화 코드가 붙은 금액: 코드는 값 위 작은 글자, TalkBack은 통화 코드와 숫자를 한 덩어리로
        rule.onNodeWithText("IDR 500,000").assertIsDisplayed()
        val grid = rule.onNodeWithTag("grid").getBoundsInRoot()
        val last = rule.onNodeWithText("4박 5일").getBoundsInRoot()
        val first = rule.onNodeWithText("30일").getBoundsInRoot()
        assertTrue("홀수 마지막 타일은 폭 전체", abs((last.right - last.left).value - (grid.right - grid.left).value) < 1f)
        assertTrue("2열 타일은 반 폭", (first.right - first.left).value < (grid.right - grid.left).value * 0.6f)
    }

    /** 지우기 버튼은 무엇을 지우는지 TalkBack 이름으로 (재검토 R18 — 공용 DangerButton 인자) */
    @Test
    fun dangerButtonNamesWhatItDeletes() {
        var clicked = 0
        rule.setContent { ReadyPortTheme { DangerButton("지우기", onClick = { clicked++ }, placement = ButtonPlacement.ItemAction, contentDescription = "방콕 왕복 지우기") } }
        rule.onNodeWithContentDescription("방콕 왕복 지우기").assertHasClickAction().performClick()
        assertEquals(1, clicked)
    }

    /** 단계 목록 문장형: 굵은 제목 글자 대신 본문 글자 (도움 절차 — 재검토 ④-9). 기본형은 그대로 굵은 제목 글자 */
    @Test
    fun stepListSentenceStyleUsesBodyText() {
        val sentence = "가까운 경찰서에서 분실 신고서를 받아요. 대사관에 낼 때 필요해요."
        val title = "공항에 가요"
        rule.setContent {
            ReadyPortTheme {
                androidx.compose.foundation.layout.Column {
                    StepList(listOf(Step(sentence)), sentence = true)
                    StepList(listOf(Step(title)))
                }
            }
        }
        rule.waitForIdle()
        val body = layoutOf(sentence).layoutInput.style
        val head = layoutOf(title).layoutInput.style
        assertEquals(FontWeight.Normal, body.fontWeight ?: FontWeight.Normal)
        assertEquals(FontWeight.SemiBold, head.fontWeight)
        assertTrue(body.fontSize.value < head.fontSize.value)
    }

    /** 어두운 사진만 그릴 때 밝힌다(사진 파일은 그대로 — 재검토 R19 공용 보정) */
    @Test
    fun photoLiftOnlyForDarkPhotos() {
        val dark = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF303030.toInt()) }
        val bright = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFC8C8C8.toInt()) }
        assertNotNull(photoLiftFilter(dark.asImageBitmap()))
        assertNull(photoLiftFilter(bright.asImageBitmap()))
    }

    /** 귀국 전 확인(공용, 전체 모양 — 귀국 단계): 첫 문장만 보이고 숫자 토큰 그대로, 펼치면 팩 문장 전체 */
    @Test
    fun returnCheckCardFoldsToFirstSentences() {
        val index = TestPacks.index.value
        rule.setContent {
            ReadyPortTheme {
                ReturnCheckCard(index.returnLinks, index.returnFacts, index.sources.associate { it.id to it.name }, {}, ReturnCheckMode.Full)
            }
        }
        val fact = index.returnFacts.first()
        val (lead, rest) = firstSentence(fact.textKo)
        assertNotNull(rest)
        rule.onNodeWithText(lead, useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithText(fact.textKo, useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithText(context.getString(R.string.return_check_full)).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText(fact.textKo, useUnmergedTree = true).assertIsDisplayed()
    }
}
