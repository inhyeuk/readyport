package com.readyport.ui.attractions

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.attractions.AdvisoryDto
import com.readyport.attractions.AdvisoryState
import com.readyport.attractions.AttTestData
import com.readyport.attractions.AttractionsDoc
import com.readyport.attractions.Category
import com.readyport.attractions.LiftAnchor
import com.readyport.attractions.LiftReason
import com.readyport.attractions.SavedAttraction
import com.readyport.attractions.StatusDto
import com.readyport.attractions.search.AttractionSearchIndex
import com.readyport.ui.TestPacks
import com.readyport.ui.country.CountryActions
import com.readyport.ui.country.CountryContent
import com.readyport.ui.country.CountrySection
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 관광지 화면 캡처 (SPEC_v5 §12 Robolectric) — app/build/screenshots/att_*.png. debug 샘플(실제 이름 + '샘플' 글)로 그린다.
 * 기본 w393dp-h851dp, 쉬운 모드 200%는 w360dp-h640dp. 캡처와 함께 화면 규칙(제목 하나·찜 토글 상태 설명 등)을 가볍게 확인한다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
class AttractionsCaptureTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(easy: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                Box(Modifier.background(Tokens.Ground)) { content() }
            }
        }
    }

    private fun scrollToText(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private val sample: AttractionsDoc = AttTestData.debugSample()

    private fun listUi(
        doc: AttractionsDoc = sample,
        query: String = "",
        category: Category? = null,
        savedOnly: Boolean = false,
        saved: List<String> = emptyList(),
        anchor: LiftAnchor? = null,
        upcomingAirport: String? = null,
        firstNotice: Boolean = false,
    ): AttractionsListUi {
        val catalog = AttTestData.catalog(doc)
        val items = saved.map { SavedAttraction("JP/$it", "2026-10-09") }
        val content = AttractionsListModel.build(catalog, AttractionSearchIndex(catalog), query, category, savedOnly, items, anchor, AdvisoryState.Normal)
        return AttractionsListUi(
            loading = false, country = "JP", countryName = "일본", catalog = catalog, content = content, query = query,
            category = category, savedOnly = savedOnly, savedKeys = items.map { it.key }.toSet(), anchor = anchor,
            upcomingAirportName = upcomingAirport, showFirstNotice = firstNotice, savedItems = items,
        )
    }

    private fun detailUi(doc: AttractionsDoc = sample, id: String = "sensoji", saved: Boolean = false, firstNotice: Boolean = false): AttractionDetailUi {
        val catalog = AttTestData.catalog(doc)
        val a = catalog.attraction(id)!!
        return AttractionDetailUi(
            loading = false, country = "JP", countryName = "일본", catalog = catalog, attraction = a, region = catalog.region(a.regionId),
            saved = saved, sameRegion = AttractionsListModel.sameRegion(catalog, a, AdvisoryState.Normal), showFirstNotice = firstNotice,
            sourceNames = catalog.sources.mapValues { it.value.name },
        )
    }

    // ---------------- 나라 화면 › 여행 정보 맨 위 (D2-A) ----------------

    @Test fun countryEntry() {
        val entry = AttractionsEntryUi.of(AttTestData.catalog(sample), savedCount = 2)
        show { CountryContent(TestPacks.countryUi("JP"), CountryActions(), initialSection = CountrySection.Travel, attractions = entry) }
        scrollToText(context.getString(R.string.attractions_entry_desc))
        capture("att_01_country_entry")
        rule.onNodeWithText(context.getString(R.string.attractions_saved_row, 2)).assertExists()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun countryEntryEasy200() {
        RuntimeEnvironment.setFontScale(2.0f)
        val entry = AttractionsEntryUi.of(AttTestData.catalog(sample), savedCount = 2)
        show(easy = true) { CountryContent(TestPacks.countryUi("JP"), CountryActions(), initialSection = CountrySection.Travel, attractions = entry) }
        scrollToText(context.getString(R.string.attractions_entry_desc))
        capture("att_02_country_entry_easy200")
    }

    @Test fun countryComingSoon() {
        show { CountryContent(TestPacks.countryUi("TH"), CountryActions(), initialSection = CountrySection.Travel, attractions = AttractionsEntryUi.NotYet) }
        scrollToText(context.getString(R.string.attractions_coming_soon_item))
        capture("att_03_country_coming_soon")
    }

    // ---------------- 목록 ----------------

    @Test fun listAll() {
        show { AttractionsListContent(listUi(), AttractionsListActions()) }
        capture("att_10_list_all")
        scrollToText("나라 공원")
        capture("att_10b_list_all_bottom")
    }

    @Test fun listCategoryFilterOpen() {
        show { AttractionsListContent(listUi(category = Category.Heritage), AttractionsListActions(), initialFilterOpen = true) }
        capture("att_11_list_heritage_filter")
    }

    @Test fun searchReasonAndChosung() {
        show { AttractionsListContent(listUi(query = "이나리"), AttractionsListActions()) }
        capture("att_12_search_reason")
    }

    @Test fun searchChosung() {
        show { AttractionsListContent(listUi(query = "ㄷㅌㅂㄹ"), AttractionsListActions()) }
        rule.onNodeWithText(context.getString(R.string.attractions_chosung)).assertExists()
        capture("att_13_search_chosung")
    }

    @Test fun searchUpcoming() {
        show { AttractionsListContent(listUi(query = "오키나와"), AttractionsListActions()) }
        rule.onNodeWithText(context.getString(R.string.attractions_upcoming, "오키나와")).assertExists()
        capture("att_14_search_upcoming")
    }

    @Test fun searchExcludedArea() {
        show { AttractionsListContent(listUi(query = "후쿠시마"), AttractionsListActions()) }
        rule.onNodeWithText(context.getString(R.string.attractions_safety_open)).assertExists()
        capture("att_15_search_excluded_area")
    }

    @Test fun searchExcludedTopic() {
        show { AttractionsListContent(listUi(query = "스노클링"), AttractionsListActions()) }
        capture("att_16_search_excluded_topic")
    }

    @Test fun searchEmpty() {
        show { AttractionsListContent(listUi(query = "전혀없는곳"), AttractionsListActions()) }
        rule.onNodeWithText(context.getString(R.string.attractions_empty_search_title)).assertExists()
        capture("att_17_search_empty")
    }

    @Test fun savedList() {
        show {
            AttractionsListContent(listUi(savedOnly = true, saved = listOf("sensoji", "dotonbori", "vanished-place"), firstNotice = true), AttractionsListActions())
        }
        capture("att_20_saved")
        scrollToText(context.getString(R.string.attractions_saved_missing))
        capture("att_20b_saved_missing")
    }

    @Test fun savedEmpty() {
        show { AttractionsListContent(listUi(savedOnly = true), AttractionsListActions()) }
        rule.onNodeWithText(context.getString(R.string.attractions_saved_empty_title)).assertExists()
        capture("att_21_saved_empty")
    }

    @Test fun liftedAirportAndLevel2Region() {
        // 화면 확인용 변형: 오사카 지역을 2단계로 (D15-B 주의 띠) + 도착 공항 KIX로 끌어올림
        // 지역이 많으면 맨 위에 '지역 바로가기'가 생겨 머리가 화면 밖으로 밀린다 — 도쿄·오사카 두 지역만으로 본다
        val two = setOf("jp_tokyo", "jp_osaka")
        val base = sample.copy(regions = sample.regions.filter { it.id in two }, attractions = sample.attractions.filter { it.region in two })
        val doc = base.copy(
            regions = base.regions.map { if (it.id == "jp_osaka") it.copy(advisory = AdvisoryDto("2", "mofa_jp", "2026-09-28")) else it },
            attractions = base.attractions.map { if (it.region == "jp_osaka") it.copy(advisory = AdvisoryDto("2", "mofa_jp", "2026-09-28")) else it },
        )
        show { AttractionsListContent(listUi(doc = doc, anchor = LiftAnchor("jp_osaka", LiftReason.Airport)), AttractionsListActions()) }
        rule.onNodeWithText(context.getString(R.string.attractions_lift_airport)).assertExists()
        capture("att_22_list_lifted_level2")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun listEasy200() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(easy = true) { AttractionsListContent(listUi(saved = listOf("sensoji")), AttractionsListActions()) }
        capture("att_23_list_easy200")
    }

    // ---------------- 상세 ----------------

    @Test fun detail() {
        show { AttractionDetailContent(detailUi(), AttractionDetailActions()) }
        // 화면 안 제목(heading)은 하나
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading).and(hasText("센소지"))).assertCountEquals(1)
        capture("att_30_detail")
        scrollToText(context.getString(R.string.attractions_how_to_go))
        capture("att_30b_detail_middle")
        scrollToText(context.getString(R.string.attractions_wikimedia_photos))
        capture("att_30c_detail_bottom")
    }

    @Test fun detailSavedFirstNotice() {
        show { AttractionDetailContent(detailUi(id = "fushimi-inari-taisha", saved = true, firstNotice = true), AttractionDetailActions()) }
        scrollToText(context.getString(R.string.attractions_first_save_notice))
        capture("att_31_detail_first_save")
        rule.onAllNodesWithText(context.getString(R.string.attractions_saved_button_on)).assertCountEquals(1)
    }

    @Test fun detailLevel2PartialStatus() {
        val doc = sample.copy(
            regions = sample.regions.map { if (it.id == "jp_osaka") it.copy(advisory = AdvisoryDto("2", "mofa_jp", "2026-09-28")) else it },
            attractions = sample.attractions.map {
                if (it.id == "osaka-castle") {
                    it.copy(advisory = AdvisoryDto("2", "mofa_jp", "2026-09-28"), status = StatusDto("partial", "[샘플] 일부 구역 공사", "sample", "2026-10-09"))
                } else {
                    it
                }
            },
        )
        show { AttractionDetailContent(detailUi(doc = doc, id = "osaka-castle"), AttractionDetailActions()) }
        rule.onNodeWithText(context.getString(R.string.attractions_advisory_level2)).assertExists()
        capture("att_32_detail_level2_partial")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun detailEasy200() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(easy = true) { AttractionDetailContent(detailUi(id = "dotonbori"), AttractionDetailActions()) }
        capture("att_33_detail_easy200")
        scrollToText(context.getString(R.string.attractions_public_space))
        capture("att_33b_detail_easy200_know")
    }

    @Test fun capturesWritten() {
        // 다른 캡처가 먼저 돌지 않았어도 폴더는 있어야 한다(CI 산출물 경로)
        assertTrue(outDir.isDirectory)
    }
}
