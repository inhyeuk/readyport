package com.readyport.ui.country

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.keepMonthDay
import com.readyport.ui.components.sourceBlocks
import com.readyport.ui.theme.ReadyPortTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 나라 화면 카드뉴스 개편 (DESIGN_SPEC 6-03~06): 숫자 타일의 출처, 지도 타일 이동, 쇼핑 미리보기의 반입 판정 출처.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class CountryDesignTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun pack(code: String) = runBlocking { TestPacks.repo.pack(code)!!.value }

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    /** 펼침 줄: 보이는 글은 `자세히 보기`, TalkBack 이름은 무엇을 펼치는지 */
    private fun openMore(name: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(name))
        rule.onNodeWithContentDescription(name).assertIsDisplayed().performClick()
    }

    @Test
    fun visaFactsShowEachValueWithItsOwnSource() {
        val id = pack("ID")
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("ID"), CountryActions()) } }
        // 비자 카드 숫자 타일: 머무는 날(요건) · 비자 비용(e-VOA 안내). 입국 신고 비용(All Indonesia)은 그 양식 카드로 (재검토 R12)
        scrollTo(s(R.string.fact_days, 30))
        rule.onNodeWithText(s(R.string.fact_days, 30)).assertIsDisplayed()
        scrollTo("IDR 500,000")
        rule.onNodeWithText("IDR 500,000").assertIsDisplayed()
        // 비자 카드: 요건 출처(외교부)와 비자 비용 출처(대사관 e-VOA 안내)는 날짜가 달라 따로 — IDR 500,000이 외교부 정보처럼 보이지 않게
        val req = id.requirements.single()
        val reqLine = s(R.string.source_footer, id.source(req.source)!!.name, displayDate(req.lastVerified))
        scrollTo(reqLine)
        rule.onNodeWithText(reqLine).assertIsDisplayed()
        val apply = req.apply!!
        val evoa = s(R.string.source_footer, id.source(apply.source)!!.name, displayDate(apply.lastVerified))
        scrollTo(evoa)
        assertTrue(rule.onAllNodesWithText(evoa).fetchSemanticsNodes().isNotEmpty())
        // 입국 카드 카드: `무료 입국 카드 비용` 칩(여행 준비와 같은 말) + 그 양식 출처(이민국 · All Indonesia)
        val form = id.forms.single { it.id in req.forms }
        scrollTo(s(R.string.fact_label_form_fee))
        rule.onNodeWithText(s(R.string.fact_label_form_fee)).assertIsDisplayed()
        val formLine = s(R.string.source_footer, id.source(form.source)!!.name, displayDate(form.lastVerified))
        scrollTo(formLine)
        assertTrue(rule.onAllNodesWithText(formLine).fetchSemanticsNodes().isNotEmpty())
    }

    /** 재검토 R17: 여행경보 3단계(출국권고) 문장은 여행 정보 맨 위 위험 배너에 팩 원문 그대로 한 번만 — 안전 카드에서 되풀이하지 않는다 */
    @Test
    fun highTravelAdvisoryIsLiftedIntoTopDangerBanner() {
        val th = pack("TH")
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions(), CountrySection.Travel) } }
        val safety = th.sections.single { it.id == "safety" }
        val warning = safety.bodyKo.single { isHighAdvisory(it) }
        assertTrue("가지 마세요" in warning)
        rule.onNodeWithText(warning).assertIsDisplayed()
        assertEquals(1, rule.onAllNodesWithText(warning).fetchSemanticsNodes().size)
        // 배너는 현지 도구 타일보다 위, 출처 줄이 바로 아래
        val banner = rule.onNodeWithText(warning).getBoundsInRoot()
        val tools = rule.onNodeWithText(s(R.string.country_travel_tools_title)).getBoundsInRoot()
        assertTrue("위험 배너가 맨 위가 아님", banner.bottom <= tools.top)
        rule.onAllNodesWithText(s(R.string.source_footer, th.source(safety.source)!!.name, displayDate(safety.lastVerified)))
            .onFirst().assertIsDisplayed()
        // 1·2단계 문장은 안전 카드에 그대로
        val rest = safety.bodyKo.filterNot { isHighAdvisory(it) }
        scrollTo(rest.first())
        rule.onNodeWithText(rest.first()).assertIsDisplayed()
    }

    @Test
    fun thailandVisaCardKeepsSummaryAndSingleSourceLine() {
        val th = pack("TH")
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions()) } }
        rule.onNodeWithText(s(R.string.country_visa_headline_not_required)).assertIsDisplayed()
        val req = th.requirements.single()
        // 원칙 6: 60자를 넘는 요약은 첫 문장만(비자 없이 90일 — OfflinePackTest가 찾는 말은 첫 문장에 있다), 펼치면 원문 전체
        val first = req.summaryKo.substringBefore(". ") + "."
        assertTrue(req.summaryKo.length > 60 && "비자 없이 90일" in first)
        scrollTo(first)
        rule.onNodeWithText(first).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText(req.summaryKo).fetchSemanticsNodes().isEmpty())
        openMore(s(R.string.country_more_visa))
        scrollTo(req.summaryKo)
        rule.onNodeWithText(req.summaryKo).assertIsDisplayed()
        // 요건·입국 카드 비용 모두 외교부 출처 → 한 줄 그대로 (OfflinePackTest와 같은 글자)
        val line = s(R.string.source_footer, th.source(req.source)!!.name, displayDate(req.lastVerified))
        assertTrue(rule.onAllNodesWithText(line).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun longEntrySentencesFoldButSafetyWarningsNever() {
        val th = pack("TH")
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions()) } }
        val entry = th.sections.single { it.id == "entry" }
        val long = entry.bodyKo.first { it.length > 60 && ". " in it }
        val first = long.substringBefore(". ") + "."
        scrollTo(first)
        rule.onNodeWithText(first).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText(long).fetchSemanticsNodes().isEmpty())
        openMore(s(R.string.country_more_section, entry.titleKo))
        scrollTo(long)
        rule.onNodeWithText(long).assertIsDisplayed()
    }

    @Test
    fun safetyWarningIsShownWhole() {
        val th = pack("TH")
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions(), CountrySection.Travel) } }
        val safety = th.sections.single { it.id == "safety" }
        // '… 3단계(출국권고)예요. 가지 마세요.' — 경고의 뒷문장이 접혀 숨으면 안 된다
        val warning = safety.bodyKo.first { it.length > 60 && ". " in it }
        scrollTo(warning)
        rule.onNodeWithText(warning).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithContentDescription(s(R.string.country_more_section, safety.titleKo)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun mapsTileScrollsBelowTheStickySections() {
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions(), CountrySection.Travel) } }
        rule.onNodeWithText(s(R.string.tile_maps)).performClick()
        rule.waitForIdle()
        val title = rule.onNodeWithText(s(R.string.explore_maps_title))
        title.assertIsDisplayed()
        // 고정된 섹션 전환 아래에 와야 한다(가려지지 않게)
        val sections = rule.onNodeWithContentDescription(s(R.string.country_sections, "태국")).getBoundsInRoot()
        assertTrue("지도 카드가 섹션 전환에 가려짐", title.getBoundsInRoot().top >= sections.bottom)
    }

    @Test
    fun travelToolTilesOpenTheirDestinations() {
        val opened = mutableListOf<String>()
        val actions = CountryActions(
            openHelp = { opened += "help:$it" },
            openMove = { opened += "move" },
            openVideos = { opened += "videos:$it" },
        )
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), actions, CountrySection.Travel) } }
        rule.onNodeWithText(s(R.string.tile_phrases_emergency)).performClick()
        rule.onNodeWithText(s(R.string.move_title)).performClick()
        rule.onNodeWithText(s(R.string.tile_videos)).performClick()
        assertTrue(opened.toString(), opened == listOf("help:TH", "move", "videos:TH"))
    }

    @Test
    fun shoppingPreviewShowsVerdictWithItsSourceButNotTheReason() {
        val jp = pack("JP")
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("JP"), CountryActions(), CountrySection.Shopping) } }
        val first = jp.shopping.first()
        rule.onNodeWithText(first.names.ko, substring = true).assertIsDisplayed()
        // 이유(why_ko)는 미리보기에 넣지 않는다 (전체 글은 쇼핑 리스트에서)
        assertTrue(rule.onAllNodesWithText(first.whyKo).fetchSemanticsNodes().isEmpty())
        // 품목 출처 + 반입 판정 출처가 카드 출처 줄에 함께 있다 (같은 날짜는 한 줄, 같은 이름은 한 번)
        val shown = jp.shopping.take(3)
        val names = shown.flatMap { listOf(it.source, it.importSource) }.map { jp.source(it)!!.name }.distinct()
        assertTrue(names.contains(jp.source(first.importSource)!!.name))
        // 같은 날짜 → 기관별 한 줄씩 한 덩어리, 날짜는 끝에 한 번 (재검토 R9). 이름은 하나도 빠지지 않는다
        val block = sourceBlocks(names.map { SourceRef(it, displayDate(shown.first().lastVerified)) }).single()
        names.forEach { n ->
            val detail = if (" · " in n) n.substringAfter(" · ") else n.substringAfter(' ')
            assertTrue("출처 이름이 빠짐: $n", detail in block.name)
        }
        val line = s(R.string.source_footer, block.name, block.verified)
        scrollTo(line)
        rule.onNodeWithText(line).assertIsDisplayed()
        scrollTo(s(R.string.shopping_open))
        rule.onNodeWithText(s(R.string.shopping_open)).assertIsDisplayed()
    }

    /** 위험 배너로 올리는 문장 = 팩 안전 문장 중 3단계(출국권고) 이상을 말하는 것만 — 1·2단계·경보 없음 문장은 아니다 */
    @Test
    fun highAdvisoryPicksOnlyLevelThreeSentences() {
        val lifted = listOf("TH", "JP", "SG", "MY", "ID").associateWith { code ->
            pack(code).sections.single { it.id == "safety" }.bodyKo.count { isHighAdvisory(it) }
        }
        assertEquals(mapOf("TH" to 1, "JP" to 1, "SG" to 0, "MY" to 1, "ID" to 0), lifted)
        assertTrue(!isHighAdvisory("대부분 지역은 1단계(여행유의)예요."))
        assertTrue(!isHighAdvisory("서파푸아·파푸아·말루쿠·아체는 2단계(여행자제)예요."))
    }

    /** 내는 때 줄의 `N월 N일`은 줄 사이에서 갈라지지 않게 NBSP로 묶는다 — 다른 글자는 그대로 */
    @Test
    fun monthAndDayStayTogether() {
        assertEquals("예: 5월 4일 도착이면 5월 2일~4일", keepMonthDay("예: 5월 4일 도착이면 5월 2일~4일"))
        assertEquals("도착 3일 전부터 낼 수 있어요.", keepMonthDay("도착 3일 전부터 낼 수 있어요."))
    }
}
