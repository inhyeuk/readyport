package com.readyport.ui.attractions

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.attractions.AdvisoryState
import com.readyport.attractions.AttTestData
import com.readyport.attractions.AttractionsDoc
import com.readyport.attractions.SavedAttraction
import com.readyport.attractions.WikiPage
import com.readyport.attractions.search.AttractionSearchIndex
import com.readyport.attractions.wiki.WikiSummary
import com.readyport.attractions.wiki.WikiSummaryResult
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 관광지 지도(Google 지도 SDK)와 위키백과 요약 팝업 캡처 — app/build/screenshots/att_4x·att_5x_*.png.
 * 지도는 [FakeMapRenderer](네트워크·Play 서비스 없음, '테스트 지도' 글자), 위키백과는 가짜 응답만 쓴다.
 * 위키백과 글은 화면 확인용 가짜 문장이다(실제 위키백과 글을 저장소에 넣지 않는다).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
class AttractionsMapWikiCaptureTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val outDir = File("build/screenshots").apply { mkdirs() }
    private val sample: AttractionsDoc = AttTestData.debugSample()

    private val online = MapEnv(keyPresent = true, online = true, playServices = true)
    private val noKey = MapEnv(keyPresent = false, online = true, playServices = true)
    private val offline = MapEnv(keyPresent = true, online = false, playServices = true)

    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(
        env: MapEnv = online,
        easy: Boolean = false,
        wiki: suspend (WikiPage) -> WikiSummaryResult = { WikiSummaryResult.Offline },
        content: @Composable () -> Unit,
    ) {
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                CompositionLocalProvider(
                    LocalMapEnv provides env,
                    LocalPlaceMapRenderer provides FakeMapRenderer,
                    LocalWikiSummaryLoader provides wiki,
                ) {
                    Box(Modifier.background(Tokens.Ground)) { content() }
                }
            }
        }
    }

    private fun scrollToText(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun detailUi(doc: AttractionsDoc = sample, id: String = "sensoji"): AttractionDetailUi {
        val catalog = AttTestData.catalog(doc)
        val a = catalog.attraction(id)!!
        return AttractionDetailUi(
            loading = false, country = doc.country, countryName = "일본", catalog = catalog, attraction = a, region = catalog.region(a.regionId),
            sameRegion = AttractionsListModel.sameRegion(catalog, a, AdvisoryState.Normal), sourceNames = catalog.sources.mapValues { it.value.name },
        )
    }

    private fun listUi(savedOnly: Boolean = false, saved: List<String> = emptyList()): AttractionsListUi {
        val catalog = AttTestData.catalog(sample)
        val items = saved.map { SavedAttraction("JP/$it", "2026-10-09") }
        val content = AttractionsListModel.build(catalog, AttractionSearchIndex(catalog), "", null, savedOnly, items, null, AdvisoryState.Normal)
        return AttractionsListUi(
            loading = false, country = "JP", countryName = "일본", catalog = catalog, content = content,
            savedOnly = savedOnly, savedKeys = items.map { it.key }.toSet(),
        )
    }

    private val mapTitle get() = context.getString(R.string.attractions_map_title)
    private val openGoogleMaps get() = context.getString(R.string.attractions_open_map)

    // ---------------- 상세: 위치 카드 ----------------

    @Test fun detailMapCard() {
        show { AttractionDetailContent(detailUi(), AttractionDetailActions()) }
        scrollToText(context.getString(R.string.attractions_map_expand))
        rule.onNodeWithContentDescription(context.getString(R.string.attractions_map_expand_cd, "센소지")).assertExists()
        capture("att_40_detail_map_card")
    }

    @Test fun detailWithoutKeyHasNoMapButKeepsGoogleMapsLink() {
        show(env = noKey) { AttractionDetailContent(detailUi(), AttractionDetailActions()) }
        rule.onNodeWithText(mapTitle).assertDoesNotExist()
        scrollToText(openGoogleMaps)
        rule.onNodeWithText(openGoogleMaps).assertExists()
        capture("att_41_detail_no_key_fallback")
    }

    @Test fun detailOfflineShowsFallbackCard() {
        show(env = offline) { AttractionDetailContent(detailUi(), AttractionDetailActions()) }
        scrollToText(context.getString(R.string.attractions_map_offline))
        rule.onNodeWithText(context.getString(R.string.attractions_map_expand)).assertDoesNotExist()
        capture("att_42_detail_map_offline")
    }

    @Test fun detailChinaHasNoMap() {
        // 화면 확인용 변형: 나라 코드만 CN으로 (중국 본토에서는 Google 지도가 열리지 않는다)
        val cn = sample.copy(country = "CN")
        show { AttractionDetailContent(detailUi(doc = cn).copy(countryName = "중국"), AttractionDetailActions()) }
        rule.onNodeWithText(mapTitle).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.attractions_map_expand)).assertDoesNotExist()
    }

    @Test fun detailMapOpensFullScreenAndDirections() {
        val opened = mutableListOf<String>()
        show { AttractionDetailContent(detailUi(), AttractionDetailActions(openLink = { opened += it })) }
        scrollToText(context.getString(R.string.attractions_map_expand))
        rule.onNodeWithText(context.getString(R.string.attractions_map_expand)).performClick()
        rule.onNodeWithText(context.getString(R.string.attractions_map_directions)).performClick()
        assertEquals(listOf("https://www.google.com/maps/dir/?api=1&destination=35.71113333,139.796325"), opened)
    }

    @Test fun detailFullMap() {
        val catalog = AttTestData.catalog(sample)
        val a = catalog.attraction("sensoji")!!
        val pin = AttractionsMapPolicy.detailPin(a, "도쿄·요코하마", "문화유산")!!
        show { DetailMapPanel(a, pin, onDismiss = {}, openLink = {}, modifier = Modifier.fillMaxSize()) }
        capture("att_43_detail_full_map")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun detailMapCardEasy200() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(easy = true) { AttractionDetailContent(detailUi(id = "osaka-castle"), AttractionDetailActions()) }
        scrollToText(context.getString(R.string.attractions_map_expand))
        capture("att_44_detail_map_easy200")
    }

    // ---------------- 목록: 지도로 보기 ----------------

    @Test fun listMapButton() {
        show { AttractionsListContent(listUi(), AttractionsListActions()) }
        rule.onNodeWithText(context.getString(R.string.attractions_map_show)).assertExists()
        capture("att_45_list_map_button")
    }

    @Test fun listMapButtonHiddenWithoutKey() {
        show(env = noKey) { AttractionsListContent(listUi(), AttractionsListActions()) }
        rule.onNodeWithText(context.getString(R.string.attractions_map_show)).assertDoesNotExist()
    }

    @Composable
    private fun ListMap(ui: AttractionsListUi, selected: String?, opened: MutableList<String> = mutableListOf()) {
        ListMapPanel(ui, rememberListPins(ui), ui.catalog!!.regions, selected, onSelect = {}, onDismiss = {}, openDetail = { opened += it }, modifier = Modifier.fillMaxSize())
    }

    @Test fun listMapAll() {
        show { ListMap(listUi(), selected = null) }
        rule.onNodeWithText(context.getString(R.string.attractions_map_tap_hint)).assertExists()
        capture("att_46_list_map_all")
    }

    @Test fun listMapSelectedOpensDetail() {
        val opened = mutableListOf<String>()
        show { ListMap(listUi(), selected = "dotonbori", opened = opened) }
        capture("att_47_list_map_selected")
        rule.onNodeWithText(context.getString(R.string.attractions_map_open_detail)).performClick()
        assertEquals(listOf("dotonbori"), opened)
    }

    @Test fun savedMapNumbersFollowSavedOrder() {
        // 저장소 순서: 나라 공원 → 센소지 → 도톤보리 (번호 1·2·3)
        val ui = listUi(savedOnly = true, saved = listOf("nara-park", "sensoji", "dotonbori"))
        show { ListMap(ui, selected = "sensoji") }
        rule.onNodeWithText(context.getString(R.string.attractions_map_saved_hint)).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.attractions_map_selected_number, 2)).assertExists()
        capture("att_48_saved_map_numbered")
    }

    @Test fun listMapButtonOpensDialog() {
        show { AttractionsListContent(listUi(), AttractionsListActions()) }
        rule.onNodeWithText(context.getString(R.string.attractions_map_show)).performClick()
        rule.onNodeWithText(context.getString(R.string.attractions_map_list_title, "일본")).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.attractions_map_close)).performClick()
        rule.onNodeWithText(context.getString(R.string.attractions_map_list_title, "일본")).assertDoesNotExist()
    }

    // ---------------- 위키백과 요약 팝업 ----------------

    private val koPage = WikiPage("ko", "센소지")
    private val fakeKo = WikiSummary(
        "ko", "센소지",
        "[화면 확인용 가짜 글] 위키백과 요약이 이 자리에 보여요. 실제 앱은 사용자가 버튼을 누를 때 위키백과에서 받아 와요.",
        "https://ko.wikipedia.org/wiki/%EC%84%BC%EC%86%8C%EC%A7%80",
    )

    @Composable
    private fun Sheet(page: WikiPage, result: WikiSummaryResult?, opened: MutableList<String> = mutableListOf()) {
        Box(Modifier.background(Tokens.Surface)) {
            WikiSheetContent(page, result, onRetry = {}, openLink = { opened += it }, onClose = {})
        }
    }

    @Test fun wikiReadyKorean() {
        val opened = mutableListOf<String>()
        show { Sheet(koPage, WikiSummaryResult.Ready(fakeKo), opened) }
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_header)).assertExists()
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_attribution, "센소지")).assertExists()
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_english)).assertDoesNotExist()
        capture("att_50_wiki_ready_ko")
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_full)).performClick()
        assertEquals(listOf(fakeKo.pageUrl), opened)
    }

    @Test fun wikiReadyEnglishIsLabelled() {
        val page = WikiPage("en", "Sensō-ji")
        show { Sheet(page, WikiSummaryResult.Ready(fakeKo.copy(lang = "en", title = "Sensō-ji", pageUrl = "https://en.wikipedia.org/wiki/Sens%C5%8D-ji"))) }
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_english)).assertExists()
        capture("att_51_wiki_ready_en")
    }

    @Test fun wikiLoading() {
        show { Sheet(koPage, null) }
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_loading)).assertExists()
        capture("att_52_wiki_loading")
    }

    @Test fun wikiOffline() {
        show { Sheet(koPage, WikiSummaryResult.Offline) }
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_offline)).assertExists()
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_retry)).assertExists()
        capture("att_53_wiki_offline")
    }

    @Test fun wikiNotFound() {
        show { Sheet(koPage, WikiSummaryResult.NotFound) }
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_not_found)).assertExists()
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_retry)).assertDoesNotExist()
        capture("att_54_wiki_not_found")
    }

    @Test fun wikiFailed() {
        show { Sheet(koPage, WikiSummaryResult.Failed) }
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_failed)).assertExists()
        capture("att_55_wiki_failed")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun wikiReadyEasy200() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(easy = true) { Sheet(koPage, WikiSummaryResult.Ready(fakeKo)) }
        capture("att_56_wiki_ready_easy200")
    }

    @Test fun detailWikiButtonOpensSheetWithFakeSummary() {
        val asked = mutableListOf<WikiPage>()
        show(wiki = { page -> asked += page; WikiSummaryResult.Ready(fakeKo) }) { AttractionDetailContent(detailUi(), AttractionDetailActions()) }
        scrollToText(context.getString(R.string.attractions_wiki_open))
        capture("att_57_detail_wiki_button")
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_open)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_header)).assertExists()
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_full)).assertExists()
        // 한국어판이 있으면 한국어판을 연다
        assertEquals(listOf(WikiPage("ko", "센소지")), asked)
    }

    @Test fun detailWithoutWikiHasNoButton() {
        val doc = sample.copy(attractions = sample.attractions.map { it.copy(wiki = null) })
        show { AttractionDetailContent(detailUi(doc = doc), AttractionDetailActions()) }
        rule.onNodeWithText(context.getString(R.string.attractions_wiki_open)).assertDoesNotExist()
    }
}
