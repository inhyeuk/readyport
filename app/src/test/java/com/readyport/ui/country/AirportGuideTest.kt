package com.readyport.ui.country

import android.app.Application
import android.content.Context
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.trip.StageInfo
import com.readyport.trip.Trip
import com.readyport.trip.TripStage
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.today.TodayActions
import com.readyport.ui.today.TodayContent
import com.readyport.ui.today.TodayUi
import com.readyport.ui.trip.TripContent
import com.readyport.ui.trip.TripFormUi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 공항에 도착하면 (2026-10-03, 운영자: "각 국가별 공항에서 입국 신고하는 위치를 안내"):
 * 나라 화면 입국·비자의 공항 묶음(고르기·단계·위치·입국 카드 줄·공식 안내도·출처), 오늘 화면 도착·출국 단계의 짧은 모양, 여행 고치기의 공항 고르기.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h851dp")
class AirportGuideTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    private val th get() = TestPacks.thailand.value

    private fun shown(text: String, substring: Boolean = false) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring))
        rule.onAllNodes(hasText(text, substring = substring)).onFirst().assertIsDisplayed()
    }

    @Test
    fun countryEntryShowsAirportBlockAfterFormCard() {
        val opened = mutableListOf<String>()
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions(openLink = { opened += it })) } }
        shown(s(R.string.airport_guide_title))
        // 고르기 칩 셋(공항 이름), 처음은 팩 첫 공항(수완나품) — 한 개만 고르는 칩
        th.airports.forEach { rule.onAllNodesWithText(it.nameKo).onFirst().assertExists() }
        rule.onNode(hasText("수완나품 공항") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected()
        // 단계: 팩 문장 그대로 + 위치 칩 + 입국 카드 줄(TDAC)
        val bkk = th.airport("BKK")!!
        shown(bkk.steps.first { it.kind == "baggage" }.bodyKo)
        shown("2층 도착 홀")
        shown(bkk.formCheckKo!!)
        // 자동 심사대는 모르면(null) 줄이 없다
        rule.onAllNodesWithText(s(R.string.airport_egate_yes)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.airport_egate_no)).assertCountEquals(0)
        // 출처: 공항 운영사 안내 + 0404(입국 카드 줄)
        shown(th.source(bkk.source)!!.name, substring = true)
        // 공식 안내도는 브라우저로
        shown(s(R.string.airport_map_open))
        rule.onNodeWithText(s(R.string.airport_map_open)).performClick()
        assertEquals(listOf(bkk.mapUrl), opened)
    }

    /** 공항 묶음은 입국 카드 카드 다음(그 카드를 어디서 보여 주는지 이어 읽는다), 들어갈 때 섹션 앞 — 긴 창에서 한 번에 그려 위치를 잰다 */
    @Test
    @Config(qualifiers = "ko-rKR-w393dp-h4000dp")
    fun airportBlockSitsBetweenFormCardAndEntrySection() {
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions()) } }
        rule.waitForIdle()
        fun top(text: String) = rule.onAllNodesWithText(text).onFirst().fetchSemanticsNode().positionInRoot.y
        val form = top(th.forms.first().nameKo)
        val block = top(s(R.string.airport_guide_title))
        val entry = top(th.sections.first { it.id == "entry" }.titleKo)
        assertTrue("공항 묶음($block)이 입국 카드($form) 아래가 아니다", block > form)
        assertTrue("공항 묶음($block)이 들어갈 때($entry) 위가 아니다", block < entry)
    }

    @Test
    fun pickingAnotherAirportSwapsSteps() {
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions()) } }
        shown("돈므앙 공항")
        rule.onNode(hasText("돈므앙 공항") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        shown("1터미널 1층")
        rule.onAllNodesWithText("2층 도착 홀").assertCountEquals(0)
    }

    @Test
    fun tripAirportIsTheDefaultAndPacksWithoutAirportsShowNothing() {
        val trip = Trip("TH", "2026-11-03", "2026-11-07", id = "t", arrivalAirport = "HKT")
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH").copy(upcomingTrip = trip), CountryActions()) } }
        shown("국제선 터미널 1층")
        rule.onNode(hasText("푸껫 공항") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected()
    }

    @Test
    fun noAirportsNoBlock() {
        val jp = TestPacks.countryUi("JP")
        if (jp.loaded!!.value.airports.isNotEmpty()) return // 공항을 합친 뒤에는 이 확인이 뜻이 없다
        rule.setContent { ReadyPortTheme { CountryContent(jp, CountryActions()) } }
        rule.waitForIdle()
        rule.onAllNodesWithText(s(R.string.airport_guide_title)).assertCountEquals(0)
    }

    @Test
    fun deepLinkScrollsToAirportBlock() {
        rule.setContent {
            ReadyPortTheme {
                CountryContent(TestPacks.countryUi("TH").copy(focusAirports = true, focusAirport = "DMK"), CountryActions(), listState = rememberLazyListState())
            }
        }
        rule.waitForIdle()
        // 스크롤하지 않아도 공항 묶음이 보이고, 부른 곳이 준 공항이 골라져 있다
        rule.onNodeWithText(s(R.string.airport_guide_title)).assertIsDisplayed()
        rule.onNode(hasText("돈므앙 공항") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected()
    }

    @Test
    fun todayArrivalShowsAirportStepsAndOpensGuide() {
        val trip = Trip("TH", "2026-11-03", "2026-11-07", id = "t", arrivalAirport = "BKK")
        var opened: Pair<String, String?>? = null
        val bkk = th.airport("BKK")!!
        rule.setContent {
            ReadyPortTheme {
                TodayContent(
                    TodayUi(trip, StageInfo(TripStage.Arrival, dayOfTrip = 1), "태국", th.forms.first(), true,
                        sourceNames = th.sources.associate { it.id to it.name }, airport = bkk, hasAirports = true),
                    TodayActions(openAirportGuide = { c, a -> opened = c to a }), {}, {}, {}, {}, {},
                )
            }
        }
        // 공항 이름 · 도시 eyebrow, 단계 제목, 입국 카드 줄, 앱 안내 단계(유심)가 이어진다 — 일반 1·2단계(입국 심사 — 여권과…)는 없다
        shown(s(R.string.airport_city_code, bkk.nameKo, bkk.cityKo))
        shown(bkk.formCheckKo!!)
        shown(s(R.string.today_arrival_step3))
        rule.onAllNodesWithText(s(R.string.today_arrival_step1)).assertCountEquals(0)
        shown(s(R.string.airport_open_guide))
        rule.onNodeWithText(s(R.string.airport_open_guide)).performClick()
        assertEquals("TH" to "BKK", opened)
    }

    @Test
    fun todayArrivalWithoutAirportKeepsGenericStepsAndOffersGuide() {
        val trip = Trip("TH", "2026-11-03", "2026-11-07", id = "t")
        var opened: Pair<String, String?>? = null
        rule.setContent {
            ReadyPortTheme {
                TodayContent(
                    TodayUi(trip, StageInfo(TripStage.Arrival, dayOfTrip = 1), "태국", th.forms.first(), true, hasAirports = true),
                    TodayActions(openAirportGuide = { c, a -> opened = c to a }), {}, {}, {}, {}, {},
                )
            }
        }
        shown(s(R.string.today_arrival_step1))
        shown(s(R.string.airport_open_guide_any))
        rule.onNodeWithText(s(R.string.airport_open_guide_any)).performClick()
        assertEquals("TH", opened!!.first)
        assertNull(opened!!.second)
    }

    @Test
    fun todayDepartureShowsCompactAirportCard() {
        val trip = Trip("TH", "2026-11-03", "2026-11-07", id = "t", arrivalAirport = "DMK")
        val dmk = th.airport("DMK")!!
        rule.setContent {
            ReadyPortTheme {
                TodayContent(
                    TodayUi(trip, StageInfo(TripStage.Departure, dayOfTrip = 1), "태국", th.forms.first(), true, airport = dmk, hasAirports = true),
                    TodayActions(), {}, {}, {}, {}, {},
                )
            }
        }
        shown(s(R.string.airport_today_departure_title))
        shown(dmk.steps.first { it.kind == "baggage" }.titleKo)
        shown("1터미널 1층")
        // 짧은 모양은 단계 설명(body)을 빼고 제목·위치만 — 입국 카드 줄은 남는다
        rule.onAllNodesWithText(dmk.steps.first { it.kind == "baggage" }.bodyKo).assertCountEquals(0)
        shown(dmk.formCheckKo!!)
    }

    @Test
    fun tripEditPicksAirportOfThatCountryOnly() {
        var saved: String? = "unset"
        val trip = Trip("TH", "2026-11-03", "2026-11-07", id = "t")
        rule.setContent {
            ReadyPortTheme {
                TripContent(
                    TripFormUi(TestPacks.index.value.countries.filter { it.pack }, trip, loaded = true, airports = mapOf("TH" to th.airports)),
                    { _, _, _, a -> saved = a }, {},
                )
            }
        }
        shown(s(R.string.trip_airport_title))
        // 처음은 `아직 몰라요`
        rule.onNode(hasText(s(R.string.trip_airport_unknown)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected()
        // 공항 한 줄 = 이름 + `방콕 · DMK`(도시·코드)
        shown(s(R.string.airport_city_code, "방콕", "DMK"))
        rule.onNode(hasText("돈므앙 공항") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        shown(s(R.string.trip_save_edit))
        rule.onNodeWithText(s(R.string.trip_save_edit)).performClick()
        assertEquals("DMK", saved)
        // 공항 안내가 없는 나라를 고르면 공항 고르기가 사라지고, 저장값은 null
        val jp = TestPacks.index.value.countries.first { it.code == "JP" }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(jp.nameKo))
        rule.onNodeWithText(jp.nameKo).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(s(R.string.trip_airport_title)).assertCountEquals(0)
        shown(s(R.string.trip_save_edit))
        rule.onNodeWithText(s(R.string.trip_save_edit)).performClick()
        assertNull(saved)
    }

    // ---------------- 자동 심사대 줄 (나라별 판정, 2026-10-03 아홉 나라) ----------------

    private fun entry(cc: String) = rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi(cc), CountryActions()) } }

    private fun airportOf(cc: String, code: String) = runBlocking { TestPacks.repo.pack(cc)!!.value.airport(code)!! }

    /** 싱가포르: 국적과 관계없이 쓸 수 있어요(초록 줄) + 조건 메모. 공항이 하나라 고르기 칩이 없다 */
    @Test
    fun singaporeShowsEgateYesWithConditions() {
        entry("SG")
        shown(s(R.string.airport_egate_yes))
        shown("사전 등록 없이", substring = true)
        rule.onAllNodesWithText(s(R.string.airport_guide_pick)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.airport_egate_no)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.airport_egate_unknown)).assertCountEquals(0)
    }

    /** 대만: 쓸 수 있지만 **처음에 유인 카운터 등록**이 조건 — 단계 제목이 먼저 등록을 말하고 메모가 장소·시간을 말한다 */
    @Test
    fun taiwanEgateRequiresFirstTimeRegistration() {
        entry("TW")
        val tpe = airportOf("TW", "TPE")
        shown(tpe.steps.first { it.kind == "egate" }.titleKo)
        shown(s(R.string.airport_egate_yes))
        shown("처음 쓰기 전에 반드시", substring = true)
        shown("제1터미널 도착층 카운터", substring = true)
    }

    /** 필리핀 마닐라: 쓸 수 없어요(막힘 줄) + 왜인지 메모 */
    @Test
    fun manilaShowsEgateNo() {
        entry("PH")
        shown(s(R.string.airport_egate_no))
        shown("필리핀 국민", substring = true)
        rule.onAllNodesWithText(s(R.string.airport_egate_yes)).assertCountEquals(0)
    }

    /** 일본: 공식 안내가 한국 여권을 밝히지 않았고 메모도 없다 → 자동 심사대 줄이 아예 없다 */
    @Test
    fun japanDrawsNoEgateLine() {
        entry("JP")
        shown(s(R.string.airport_guide_title))
        listOf(R.string.airport_egate_yes, R.string.airport_egate_no, R.string.airport_egate_unknown).forEach {
            rule.onAllNodesWithText(s(it)).assertCountEquals(0)
        }
        // 공동 키오스크(VJW) 단계와 입국 카드 줄은 보인다
        shown(airportOf("JP", "NRT").steps.first { it.kind == "form_check" }.titleKo)
        shown(airportOf("JP", "NRT").formCheckKo!!)
    }

    /** 인도네시아: 판정은 모름(`분명하지 않아요` 줄)이고, 자카르타는 서로 다른 공식 안내 두 곳을 그대로 보여 준다 */
    @Test
    fun indonesiaShowsBothOfficialAnswers() {
        entry("ID")
        // 처음 고른 공항은 팩 첫 공항(발리) — 이민국 발표 + 공항 안내에 자동 게이트 이야기가 없다는 말
        shown(s(R.string.airport_egate_unknown))
        shown("인도네시아 이민국 발표(2024년)", substring = true)
        rule.onAllNodesWithText(s(R.string.airport_egate_yes)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.airport_egate_no)).assertCountEquals(0)
        // 자카르타로 바꾸면 두 안내가 서로 다르다는 말이 먼저 나온다(앱이 한쪽을 고르지 않는다)
        val cgk = airportOf("ID", "CGK")
        rule.onNode(hasText(cgk.nameKo) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        shown("공식 안내 두 곳이 서로 달라요", substring = true)
        shown(s(R.string.airport_egate_unknown))
    }

    /**
     * 베트남: 사전 입국 정보(PAI)는 의무가 아닌 양식 — `꼭 내야 하는 건 아니에요` 알약·`미리 준비해 두기` 버튼이고
     * `입국 카드 준비하기`(의무 서류 버튼)는 쓰지 않는다. 공항 줄도 의무가 아니라고 말한다.
     */
    @Test
    fun vietnamPreArrivalFormIsShownAsOptional() {
        entry("VN")
        val vn = runBlocking { TestPacks.repo.pack("VN")!!.value }
        shown(vn.forms.single().nameKo)
        shown(s(R.string.entry_form_optional_tag))
        shown(s(R.string.entry_form_optional_open))
        shown(s(R.string.entry_form_label_optional))
        rule.onAllNodesWithText(s(R.string.prepare_form_open)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.entry_form_label)).assertCountEquals(0)
        // 공항 묶음: 입국 카드 줄 + 자동 심사대는 베트남 국민용
        shown(airportOf("VN", "SGN").formCheckKo!!)
        shown(s(R.string.airport_egate_no))
    }

    @Test
    fun countryLatestVerifiedIncludesAirports() {
        val latest = th.latestVerified()
        assertTrue(latest >= th.airports.maxOf { it.lastVerified })
    }
}
