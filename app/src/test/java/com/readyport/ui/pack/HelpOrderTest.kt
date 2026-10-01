package com.readyport.ui.pack

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 24 도움 순서 (운영자 결정 5 — PRD 5.11 원래 순서):
 * 나라 칩 → 고른 문장 카드 → 자주 쓰는 말 → 긴급 번호(대표 + 전체) → 대사관 → 이럴 땐 이렇게 → 어느 나라에서나.
 * 긴급 번호는 맨 위 `긴급 번호 바로 보기` 한 줄로 바로 내려간다.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h9000dp")
class HelpOrderTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int) = context.getString(id)

    private fun top(text: String) = rule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    @Test
    fun blocksFollowTheOwnerOrder() {
        val th = TestPacks.thailand.value
        rule.setContent { ReadyPortTheme { HelpContent(TestPacks.helpUi(), {}, {}, {}) } }
        rule.waitForIdle()
        val order = listOf(
            s(R.string.help_jump_emergency),
            "태국", // 나라 칩
            "ห้องน้ำอยู่ที่ไหน", // 고른 문장 카드(첫 문장)
            s(R.string.help_phrases_title),
            "경찰을 불러 주세요", // 문장 행
            s(R.string.help_emergency_title),
            th.emergency.first().number, // 대표 번호 1155 (큰 타일)
            th.emergency[1].number, // 191
            s(R.string.help_embassy),
            th.embassy!!.emergencyPhone!!,
            s(R.string.help_procedures_title),
            th.procedures.first().titleKo,
            s(R.string.help_common_title),
            "+82-2-3210-0404",
        )
        val tops = order.map { top(it) }
        tops.zipWithNext().forEachIndexed { i, (a, b) -> assertTrue("${order[i]}($a) 가 ${order[i + 1]}($b) 보다 위", a < b) }
    }

    /** 일본처럼 대표 번호가 짧은 나라도 같은 순서 — 긴 번호(050-…)는 폭 전체 줄 */
    @Test
    fun japanHelpKeepsOrderToo() {
        val jp = runBlocking { TestPacks.repo.pack("JP")!! }
        rule.setContent { ReadyPortTheme { HelpContent(TestPacks.helpUi().copy(selected = jp), {}, {}, {}) } }
        rule.waitForIdle()
        val first = jp.value.phrases.first()
        val tops = listOf(first.local, s(R.string.help_emergency_title), "110", "050-3816-2787", s(R.string.help_embassy)).map { top(it) }
        tops.zipWithNext().forEach { (a, b) -> assertTrue("$a < $b", a < b) }
        assertEquals(4, jp.value.emergency.size)
    }
}

/** 도움 첫 화면(393×851): 문장 카드가 먼저 보이고, `긴급 번호 바로 보기`를 누르면 긴급 번호 묶음으로 내려간다 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class HelpFirstScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int) = context.getString(id)

    @Test
    fun phraseCardFirstAndJumpReachesNumbers() {
        rule.setContent { ReadyPortTheme { HelpContent(TestPacks.helpUi(), {}, {}, {}) } }
        rule.onNodeWithText("ห้องน้ำอยู่ที่ไหน").assertIsDisplayed()
        rule.onNodeWithText(s(R.string.help_jump_emergency)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.help_emergency_title)).assertDoesNotExist()
        rule.onNodeWithText(s(R.string.help_jump_emergency)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(s(R.string.help_emergency_title)).assertIsDisplayed()
        rule.onNodeWithText("1155", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun phraseRowsAreSingleChoiceRadioRows() {
        rule.setContent { ReadyPortTheme { HelpContent(TestPacks.helpUi(), {}, {}, {}) } }
        val thPhrase = TestPacks.thailand.value.phrases
        // 문장 행: 한 개만 고르는 라디오 행, 첫 문장이 골라져 있다
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        rule.onNode(hasText(thPhrase.first().ko) and radio).assertIsSelected()
        rule.onNode(hasText(thPhrase[1].ko) and radio).assertIsNotSelected()
        rule.onNode(hasText(thPhrase[1].ko) and radio).performClick()
        rule.onNode(hasText(thPhrase[1].ko) and radio).assertIsSelected()
        rule.onNode(hasText(thPhrase.first().ko) and radio).assertIsNotSelected()
    }
}
