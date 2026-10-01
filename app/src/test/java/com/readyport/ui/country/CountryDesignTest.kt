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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.components.displayDate
import com.readyport.ui.theme.ReadyPortTheme
import kotlinx.coroutines.runBlocking
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
        // 숫자 타일: 머무는 날(요건) · 비자 비용(e-VOA 안내) · 입국 신고 비용(All Indonesia)
        scrollTo(s(R.string.fact_days, 30))
        rule.onNodeWithText(s(R.string.fact_days, 30)).assertIsDisplayed()
        scrollTo("IDR 500,000")
        rule.onNodeWithText("IDR 500,000").assertIsDisplayed()
        // 날짜가 같은 출처는 한 줄, 날짜가 다른 e-VOA 출처는 다음 줄 — IDR 500,000이 외교부 정보처럼 보이지 않게
        val req = id.requirements.single()
        val form = id.forms.single { it.id in req.forms }
        val sameDay = s(
            R.string.source_footer,
            id.source(req.source)!!.name + ", " + id.source(form.source)!!.name,
            displayDate(req.lastVerified),
        )
        scrollTo(sameDay)
        rule.onNodeWithText(sameDay).assertIsDisplayed()
        val apply = req.apply!!
        val evoa = s(R.string.source_footer, id.source(apply.source)!!.name, displayDate(apply.lastVerified))
        scrollTo(evoa)
        assertTrue(rule.onAllNodesWithText(evoa).fetchSemanticsNodes().isNotEmpty())
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
        scrollTo(koreanPhraseWrap(first))
        rule.onNodeWithText(koreanPhraseWrap(first)).assertIsDisplayed()
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
        scrollTo(koreanPhraseWrap(first))
        rule.onNodeWithText(koreanPhraseWrap(first)).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText(koreanPhraseWrap(long)).fetchSemanticsNodes().isEmpty())
        openMore(s(R.string.country_more_section, entry.titleKo))
        scrollTo(koreanPhraseWrap(long))
        rule.onNodeWithText(koreanPhraseWrap(long)).assertIsDisplayed()
    }

    @Test
    fun safetyWarningIsShownWhole() {
        val th = pack("TH")
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions(), CountrySection.Travel) } }
        val safety = th.sections.single { it.id == "safety" }
        // '… 3단계(출국권고)예요. 가지 마세요.' — 경고의 뒷문장이 접혀 숨으면 안 된다
        val warning = safety.bodyKo.first { it.length > 60 && ". " in it }
        scrollTo(koreanPhraseWrap(warning))
        rule.onNodeWithText(koreanPhraseWrap(warning)).assertIsDisplayed()
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
        val line = s(R.string.source_footer, names.joinToString(", "), displayDate(shown.first().lastVerified))
        scrollTo(line)
        rule.onNodeWithText(line).assertIsDisplayed()
        scrollTo(s(R.string.shopping_open))
        rule.onNodeWithText(s(R.string.shopping_open)).assertIsDisplayed()
    }
}
