package com.readyport.ui.country

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.formWindowRange
import com.readyport.ui.components.windowRuleOnly
import com.readyport.ui.tabs.PrepareContent
import com.readyport.ui.theme.ReadyPortTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 다듬기 S2 — 나라 입국·비자(03·04)·여행 정보(05)·여행 준비(18)의 재검토2 남은 문제:
 * 안심 카드, 내 여행 날짜로 '내는 때', 히어로 최신 확인 날짜, 펼침 줄 보이는 글에 대상, 순서 머리, 카드 안 판정(대행 사이트·전기).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class CountryPolishS2Test {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun pack(code: String) = runBlocking { TestPacks.repo.pack(code)!!.value }

    private fun scrollTo(text: String, substring: Boolean = false) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring))
    }

    // ---------------- 순수 함수 ----------------

    @Test
    fun windowRuleDropsOnlyTheGenericExample() {
        assertEquals(
            "태국에 도착하는 날을 포함해 3일 안에 내요.",
            windowRuleOnly("태국에 도착하는 날을 포함해 3일 안에 내요. 예: 5월 4일 도착이면 5월 2일~4일"),
        )
        assertEquals("도착 3일 전부터 낼 수 있어요. 입국·세관·검역을 한 번에 신고해요.", windowRuleOnly("도착 3일 전부터 낼 수 있어요. 입국·세관·검역을 한 번에 신고해요."))
        assertEquals("3일 안에 내요.", windowRuleOnly("3일 안에(예: 5월 4일 도착이면 5월 2일~4일) 내요."))
    }

    @Test
    fun formWindowCountsTheArrivalDay() {
        // 팩 window_days_including_arrival = 3 → 도착 2일 전부터 도착일까지 (TripStages·알림과 같은 계산)
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 3), formWindowRange(LocalDate.of(2026, 11, 3), 3))
        assertEquals(LocalDate.of(2026, 10, 30) to LocalDate.of(2026, 11, 1), formWindowRange(LocalDate.of(2026, 11, 1), 3))
        assertEquals(LocalDate.of(2026, 11, 3) to LocalDate.of(2026, 11, 3), formWindowRange(LocalDate.of(2026, 11, 3), 1))
    }

    @Test
    fun restatedNeedsALongSharedPhrase() {
        assertTrue(isRestated("비자 없이 90일까지 머물 수 있어요(관광·친척 방문 등).", listOf("관광 목적이면 비자 없이 90일까지 머물 수 있어요(두 나라 협정).")))
        assertFalse(isRestated("내고 나서 받은 확인 메일을 입국 심사 때 보여 주세요.", listOf("관광 목적이면 비자 없이 90일까지 머물 수 있어요.")))
        assertFalse(isRestated("비자 없이 나갔다 들어오기를 자주 되풀이하면 거절될 수 있어요.", listOf("비자 없이 90일까지 머물 수 있어요.")))
        // 이름(All Indonesia)만 겹치고 나머지가 새 말이면 접지 않는다
        assertFalse(
            isRestated(
                "도착 3일 전부터 All Indonesia에 입국·세관·건강 신고를 한 번에 무료로 내요. 이 공항들은 따로 전자세관신고를 안 해도 돼요.",
                listOf("여권이 6개월 넘게 남아 있어야 해요. 도착 전에 All Indonesia도 내요."),
            ),
        )
    }

    /** 히어로 `최종 확인`은 화면 안 카드 중 가장 최근 날짜 — 태국 팩 전체 2026-09-28, 들어갈 때 2026-10-01 */
    @Test
    fun heroShowsTheLatestVerifiedDate() {
        val th = pack("TH")
        val entry = th.sections.single { it.id == "entry" }
        assertTrue(entry.lastVerified > th.lastVerified)
        assertEquals(entry.lastVerified, th.latestVerified())
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions()) } }
        rule.onNodeWithText(s(R.string.guide_last_verified, displayDate(entry.lastVerified))).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText(s(R.string.guide_last_verified, displayDate(th.lastVerified))).fetchSemanticsNodes().isEmpty())
    }

    // ---------------- 03 태국 ----------------

    /** 내 여행(11월 3일)이 있으면 '내는 때'에 일반 예시 대신 내 날짜(팩 기간 3일 + 출발일). 규칙 문장은 그대로 */
    @Test
    fun tripDateReplacesTheGenericExample() {
        val form = pack("TH").forms.single()
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH").copy(tripArrival = LocalDate.of(2026, 11, 3)), CountryActions()) } }
        val mine = s(R.string.form_window_mine, s(R.string.date_month_day, 11, 3), s(R.string.date_range_same_month, 11, 1, 3))
        scrollTo(mine, substring = true)
        val line = s(R.string.guide_form_window, "${windowRuleOnly(form.windowKo)}\n$mine")
        rule.onNodeWithText(line).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("5월 4일", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun noTripKeepsThePackSentence() {
        val form = pack("TH").forms.single()
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions()) } }
        val line = s(R.string.guide_form_window, form.windowKo)
        scrollTo(line)
        rule.onNodeWithText(line).assertIsDisplayed()
    }

    /** 입국 화면 첫 항목 = 안심 카드(비제휴 · 제출은 직접) — 비제휴 한 노드, Navy 보안 띠 없음 */
    @Test
    fun entryStartsWithAssuranceCard() {
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions()) } }
        rule.onNodeWithText(s(R.string.guide_not_affiliated)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.country_submit_self)).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText(s(R.string.settings_local_only_title)).fetchSemanticsNodes().isEmpty())
    }

    // ---------------- 04 인도네시아 ----------------

    /** 펼침 줄 셋이 같은 `자세히 보기`로 보이지 않는다 — 보이는 글에 대상(비자 설명·단계 설명·들어갈 때) */
    @Test
    fun indonesiaTogglesNameWhatTheyOpen() {
        val id = pack("ID")
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("ID"), CountryActions()) } }
        val entry = id.sections.single { it.id == "entry" }
        listOf(s(R.string.country_more_visa), s(R.string.country_more_steps), s(R.string.country_more_section, entry.titleKo)).forEach { label ->
            scrollTo(label)
            rule.onNodeWithText(label).assertIsDisplayed()
        }
        assertTrue(rule.onAllNodesWithText(s(R.string.action_more)).fetchSemanticsNodes().isEmpty())
    }

    /** 대행 사이트 경고 = 카드 안 판정 알약 + 보통 본문(팩 문장 그대로) — 빨간 채움 블록 아님 */
    @Test
    fun agentSiteWarningIsAnInCardVerdict() {
        val warning = pack("ID").requirements.single().apply!!.warningKo!!
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("ID"), CountryActions()) } }
        scrollTo(warning)
        rule.onNodeWithText(warning).assertIsDisplayed()
        val tag = s(R.string.country_visa_apply_warning_tag)
        scrollTo(tag)
        rule.onNodeWithText(tag).assertIsDisplayed()
    }

    /** 순서 머리 ①·② (번호 원 + 한 줄, TalkBack `1단계 · …`) */
    @Test
    fun indonesiaStepsUseNumberHeads() {
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("ID"), CountryActions()) } }
        listOf(1 to R.string.country_step_head_form, 2 to R.string.country_step_head_visa).forEach { (n, head) ->
            val name = s(R.string.country_step_eyebrow, n, s(head))
            rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(name))
            rule.onNode(hasContentDescription(name)).assertIsDisplayed()
        }
    }

    // ---------------- 05 여행 정보 ----------------

    /** 전기 카드 결론 = `한국 플러그 그대로` 알약 + 한 줄(팩 kr_plug_fits) */
    @Test
    fun powerVerdictIsAStatusTag() {
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions(), CountrySection.Travel) } }
        scrollTo(s(R.string.power_tag_fits))
        rule.onNodeWithText(s(R.string.power_tag_fits)).assertIsDisplayed()
        scrollTo(s(R.string.guide_power_kr_fits))
        rule.onNodeWithText(s(R.string.guide_power_kr_fits)).assertIsDisplayed()
        // 카드 안 초록 채움 띠는 없다 — 알약이 결론, 문장은 보통 본문(아래)
        val tag = rule.onNodeWithText(s(R.string.power_tag_fits)).fetchSemanticsNode().boundsInRoot
        val line = rule.onNodeWithText(s(R.string.guide_power_kr_fits)).fetchSemanticsNode().boundsInRoot
        assertTrue(line.top >= tag.bottom)
    }

    // ---------------- 18 여행 준비 ----------------

    /** 여행 준비도 안심 카드(나라 입국 화면과 같은 문구) + 내 여행 날짜 */
    @Test
    fun prepareUsesAssuranceCardAndTripDates() {
        val form = pack("TH").forms.single()
        val entries = TestPacks.formEntries().map { it.copy(windowDays = form.windowDaysIncludingArrival, tripArrival = LocalDate.of(2026, 11, 3)) }
        rule.setContent { ReadyPortTheme { PrepareContent(entries, {}) } }
        rule.onNodeWithText(s(R.string.guide_not_affiliated)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.country_submit_self)).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText(s(R.string.prepare_disclaimer)).fetchSemanticsNodes().isEmpty())
        val mine = s(R.string.form_window_mine, s(R.string.date_month_day, 11, 3), s(R.string.date_range_same_month, 11, 1, 3))
        scrollTo(mine, substring = true)
        rule.onNodeWithText(mine, substring = true).assertIsDisplayed()
    }
}
