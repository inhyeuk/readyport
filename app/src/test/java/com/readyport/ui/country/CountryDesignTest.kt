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
        // 배너는 여행 정보 첫 읽는 카드(전기)보다 위, 출처 줄이 바로 아래
        val banner = rule.onNodeWithText(warning).getBoundsInRoot()
        val firstCard = rule.onNodeWithText(s(R.string.guide_power_title)).getBoundsInRoot()
        assertTrue("위험 배너가 맨 위가 아님", banner.bottom <= firstCard.top)
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
        rule.onNodeWithText(s(R.string.fact_days, 90)).assertIsDisplayed()
        val req = th.requirements.single()
        // 다듬기 S(재검토2 ③#5): 숫자 타일(`90일 비자 없이 머물러요`)이 있으면 요약은 처음부터 `비자 설명 자세히 보기` 안 —
        // 타일 바로 밑에서 `비자 없이 90일까지 머물 수 있어요`를 되풀이하지 않는다. 펼치면 원문 전체
        val first = req.summaryKo.substringBefore(". ") + "."
        assertTrue("비자 없이 90일" in first)
        assertTrue(rule.onAllNodesWithText(first).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText(req.summaryKo).fetchSemanticsNodes().isEmpty())
        // 보이는 글도 무엇을 펼치는지 밝힌다(같은 `자세히 보기`가 여러 번 보이지 않게)
        rule.onNodeWithText(s(R.string.country_more_visa)).assertIsDisplayed()
        openMore(s(R.string.country_more_visa))
        scrollTo(req.summaryKo)
        rule.onNodeWithText(req.summaryKo).assertIsDisplayed()
        // 요건·입국 카드 비용 모두 외교부 출처 → 한 줄 그대로 (OfflinePackTest와 같은 글자)
        val line = s(R.string.source_footer, th.source(req.source)!!.name, displayDate(req.lastVerified))
        assertTrue(rule.onAllNodesWithText(line).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun longEntrySentencesFoldButSafetyWarningsNever() {
        // 인도네시아 들어갈 때: 위 카드와 겹치지 않는 60자 넘는 문장은 첫 문장만 보이고 펼치면 원문 전체
        val id = pack("ID")
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("ID"), CountryActions()) } }
        val entry = id.sections.single { it.id == "entry" }
        val shownAbove = id.requirements.map { it.summaryKo } + id.forms.map { it.windowKo }
        val long = entry.bodyKo.first { it.length > 60 && ". " in it && !isRestated(it, shownAbove) }
        val first = long.substringBefore(". ") + "."
        scrollTo(first)
        rule.onNodeWithText(first).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText(long).fetchSemanticsNodes().isEmpty())
        openMore(s(R.string.country_more_section, entry.titleKo))
        scrollTo(long)
        rule.onNodeWithText(long).assertIsDisplayed()
    }

    /**
     * 다듬기 S(재검토2 ③#5·③#12·⑤#15): 태국 들어갈 때의 `비자 없이 90일까지…`(비자 카드와 같은 말)·TDAC 기간 문장(입국 카드 내는 때와 같은 말)은
     * 접어 두고 — 한 화면에 `90일`·기간이 두세 번 나오지 않게 — 펼치면 팩 원문 전체가 팩 순서대로 나온다. 앱은 문장을 지우거나 고치지 않는다.
     */
    @Test
    fun entrySentencesAlreadyOnCardsAboveFoldAway() {
        val th = pack("TH")
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions()) } }
        val entry = th.sections.single { it.id == "entry" }
        val shownAbove = th.requirements.map { it.summaryKo } + th.forms.map { it.windowKo }
        val restated = entry.bodyKo.filter { isRestated(it, shownAbove) }
        assertEquals(2, restated.size)
        assertTrue(restated.any { "90일" in it } && restated.any { "TDAC" in it })
        restated.forEach { r ->
            listOf(r, r.substringBefore(". ") + ".").forEach { t ->
                assertTrue("위 카드와 같은 문장이 보임: $t", rule.onAllNodesWithText(t).fetchSemanticsNodes().isEmpty())
            }
        }
        val kept = entry.bodyKo.filterNot { isRestated(it, shownAbove) }
        scrollTo(kept.last())
        rule.onNodeWithText(kept.last()).assertIsDisplayed()
        openMore(s(R.string.country_more_section, entry.titleKo))
        entry.bodyKo.forEach { line ->
            scrollTo(line)
            rule.onNodeWithText(line).assertIsDisplayed()
        }
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

    /**
     * 지도 저장 카드는 읽는 흐름에 그대로 있고(v3), 그 자리로 보내던 `오프라인 지도` 타일은 없앴다 —
     * 길 안내 묶음이 지도 카드 **아래**로 내려와 같은 화면 안을 되돌아가는 길이 되었기 때문(NAV_V3_REPORT).
     */
    @Test
    fun mapsCardStaysInTheReadingFlowWithoutATile() {
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions(), CountrySection.Travel) } }
        assertTrue(rule.onAllNodesWithText(s(R.string.tile_maps)).fetchSemanticsNodes().isEmpty())
        scrollTo(s(R.string.explore_maps_title))
        val title = rule.onNodeWithText(s(R.string.explore_maps_title))
        title.assertIsDisplayed()
        // 고정된 탭 줄 아래에 와야 한다(가려지지 않게)
        val sections = rule.onNodeWithContentDescription(s(R.string.country_sections, "태국")).getBoundsInRoot()
        assertTrue("지도 카드가 탭 줄에 가려짐", title.getBoundsInRoot().top >= sections.bottom)
    }

    /** 현지 도구 타일은 내용 맨 끝 길 안내 묶음에 모여 있다 (v3 — 읽는 카드 사이에 메뉴를 끼우지 않는다) */
    @Test
    fun travelToolTilesOpenTheirDestinations() {
        val opened = mutableListOf<String>()
        val actions = CountryActions(
            openHelp = { opened += "help:$it" },
            openMove = { opened += "move" },
            openVideos = { opened += "videos:$it" },
        )
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), actions, CountrySection.Travel) } }
        // 폭 전체 큰 타일의 라벨은 한 줄로 편다(2열 칸 라벨의 줄바꿈은 그대로)
        val phrases = s(R.string.tile_phrases_emergency).replace('\n', ' ')
        scrollTo(s(R.string.tile_videos))
        rule.onNodeWithText(phrases).performClick()
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
        val lifted = listOf("TH", "JP", "SG", "MY", "ID", "TW", "CN", "PH", "VN").associateWith { code ->
            pack(code).sections.single { it.id == "safety" }.bodyKo.count { isHighAdvisory(it) }
        }
        // 필리핀: 3단계(팔라완 남쪽·민다나오 일부)와 4단계(잠보앙가·술루 등) 두 문장. 2단계 문장은 단계 숫자 3·4를 쓰지 않는다
        assertEquals(mapOf("TH" to 1, "JP" to 1, "SG" to 0, "MY" to 1, "ID" to 0, "TW" to 0, "CN" to 1, "PH" to 2, "VN" to 0), lifted)
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
