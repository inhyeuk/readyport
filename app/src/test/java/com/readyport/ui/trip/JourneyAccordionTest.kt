package com.readyport.ui.trip

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.pack.CountryPack
import com.readyport.trip.Checklist
import com.readyport.trip.ChecklistData
import com.readyport.trip.JourneyStage
import com.readyport.trip.Trip
import com.readyport.trip.TripChecks
import com.readyport.trip.TripStages
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 한 여행 화면의 **단계 아코디언** (운영자 2026-10-03, DESIGN_SPEC 부록 H.7).
 * *"내 여행에서 계획, 예약, 등을 클릭하면 하단으로 이동한 뒤 상단으로 바로 이동할 수 있는 방법이 없어.
 * 따라서 각 단계를 클릭하면 접혔다가 펴지는 형태로 해줘. 다른 단계를 클릭하면 펼쳐져있던 기존 내용이 모두 접히도록 해줘.
 * 그리고 각 단계에 번호가 붙으면 좋겠어."*
 * 화면 전체가 그려지게 아주 긴 창(h6000dp)에서 본다 — 누를 때 스크롤이 끼어들지 않게.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h6000dp")
class JourneyAccordionTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int, vararg a: Any) = context.getString(id, *a)
    private val index = TestPacks.index.value
    private fun pack(cc: String): CountryPack = runBlocking { TestPacks.repo.pack(cc)!!.value }
    private val trip = Trip("TH", "2026-11-03", "2026-11-07", id = "t-th")
    private val today = LocalDate.of(2026, 10, 2)

    private val data: ChecklistData =
        Checklist.build(Checklist.Input(trip, index, pack("TH"), TripChecks(), passportSaved = true, today = today))

    private fun ui() = JourneyUi(
        loaded = true,
        trip = trip,
        countryName = pack("TH").names.ko,
        data = data,
        today = today,
        stage = TripStages.compute(trip, today, 0L, pack("TH").requiredForms.firstOrNull()?.windowDaysIncludingArrival),
        form = pack("TH").requiredForms.firstOrNull(),
        returnLinks = index.returnLinks,
        returnFacts = index.returnFacts,
        indexSources = index.sources.associate { it.id to it.name },
        sourceNames = pack("TH").sources.associate { it.id to it.name },
        airport = pack("TH").airports.singleOrNull(),
        hasAirports = pack("TH").airports.isNotEmpty(),
        hasShopping = pack("TH").shopping.isNotEmpty(),
    )

    private fun nameRes(stage: JourneyStage) = when (stage) {
        JourneyStage.Plan -> R.string.journey_plan
        JourneyStage.Book -> R.string.journey_book
        JourneyStage.Docs -> R.string.journey_docs
        JourneyStage.Pack -> R.string.journey_pack
        JourneyStage.Departure -> R.string.journey_departure
        JourneyStage.Arrival -> R.string.journey_arrival
        JourneyStage.During -> R.string.journey_during
        JourneyStage.Return -> R.string.journey_return
    }

    private fun bodyRes(stage: JourneyStage) = when (stage) {
        JourneyStage.Plan -> R.string.journey_plan_body
        JourneyStage.Book -> R.string.journey_book_body
        JourneyStage.Docs -> R.string.journey_docs_body
        JourneyStage.Pack -> R.string.journey_pack_body
        JourneyStage.Departure -> R.string.journey_departure_body
        JourneyStage.Arrival -> R.string.journey_arrival_body
        JourneyStage.During -> R.string.journey_during_body
        JourneyStage.Return -> R.string.journey_return_body
    }

    /** 머리 TalkBack 이름 = `3단계 서류, 7개 중 2개 했어요` */
    private fun headerCd(stage: JourneyStage): String {
        val items = data.stage(stage)
        return s(
            R.string.ck_phase_cd,
            s(R.string.journey_stage_step_cd, stage.step, s(nameRes(stage))),
            items.size,
            items.count { it.checked },
        )
    }

    private fun header(stage: JourneyStage) = rule.onNodeWithContentDescription(headerCd(stage))

    /** 그 단계가 펼쳐져 있는지 — 머리의 펼쳐짐/접힘 상태로 본다 */
    private fun isOpen(stage: JourneyStage): Boolean =
        header(stage).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == s(R.string.state_expanded)

    /** 그 단계 설명 한 줄이 실제로 화면에 그려졌는지 (펼쳐졌을 때만 있다) */
    private fun bodyShown(stage: JourneyStage): Boolean =
        rule.onAllNodesWithText(s(bodyRes(stage))).fetchSemanticsNodes().isNotEmpty()

    private fun show(openAtFirst: String? = null) {
        rule.setContent { ReadyPortTheme { TripJourneyContent(ui(), ChecklistActions(), openAtFirst = openAtFirst) } }
        rule.waitForIdle()
    }

    // ---------------- 번호 ----------------

    /** 단계 번호는 1~8이고 순서대로다 (enum 차례 = 화면 차례) */
    @Test
    fun stageNumbersAreOneToEightInOrder() {
        assertEquals((1..8).toList(), JourneyStage.entries.map { it.step })
        show()
        // 카드가 번호 순서대로 위에서 아래로 놓인다
        val tops = JourneyStage.entries.map { header(it).getBoundsInRoot().top.value }
        assertEquals(tops.sorted(), tops)
    }

    /** 모든 단계 머리가 `n단계 이름`으로 읽힌다 — TalkBack이 `1 계획`이 아니라 `1단계 계획`으로 말한다 */
    @Test
    fun everyHeaderReadsItsStepNumber() {
        show()
        JourneyStage.entries.forEach { stage ->
            header(stage).assertIsDisplayed()
            assertTrue(
                "${stage.key} 머리 이름에 단계 번호가 없다",
                headerCd(stage).startsWith(s(R.string.journey_stage_step_cd, stage.step, s(nameRes(stage)))),
            )
        }
    }

    // ---------------- 아코디언 ----------------

    /** 처음 펼쳐져 있는 단계는 **지금 단계**다 (아무것도 안 한 새 여행 = 계획) */
    @Test
    fun defaultOpenStageIsTheCurrentStage() {
        val current = Checklist.currentStage(data, trip, today)
        assertEquals(JourneyStage.Plan, current)
        show()
        assertTrue("지금 단계가 접혀 있다", isOpen(current))
        assertTrue("지금 단계 설명이 없다", bodyShown(current))
        JourneyStage.entries.filter { it != current }.forEach {
            assertFalse("${it.key}가 처음부터 펼쳐져 있다", isOpen(it))
            assertFalse("${it.key} 내용이 접혔는데 남아 있다", bodyShown(it))
        }
    }

    /** 다른 단계를 누르면 그 단계가 펴지고 **펼쳐져 있던 단계는 접힌다** (한 번에 하나) */
    @Test
    fun tappingAStageOpensItAndFoldsEveryOtherStage() {
        show()
        assertTrue(isOpen(JourneyStage.Plan))
        header(JourneyStage.Return).performClick()
        rule.waitForIdle()
        assertTrue("누른 단계가 펴지지 않았다", isOpen(JourneyStage.Return))
        assertFalse("펼쳐져 있던 단계가 접히지 않았다", isOpen(JourneyStage.Plan))
        assertFalse("접힌 단계의 내용이 남아 있다", bodyShown(JourneyStage.Plan))
        assertTrue(bodyShown(JourneyStage.Return))
        // 또 다른 단계를 누르면 복귀도 접힌다
        header(JourneyStage.Pack).performClick()
        rule.waitForIdle()
        assertTrue(isOpen(JourneyStage.Pack))
        assertEquals(
            "펼쳐진 단계가 하나가 아니다",
            1,
            JourneyStage.entries.count { isOpen(it) },
        )
    }

    /** 펼쳐진 단계를 다시 누르면 접힌다 (모두 접힘) — 머리는 모두 남는다 */
    @Test
    fun tappingTheOpenStageFoldsItAndKeepsEveryHeader() {
        show()
        header(JourneyStage.Plan).performClick()
        rule.waitForIdle()
        assertEquals("접었는데 펼쳐진 단계가 있다", 0, JourneyStage.entries.count { isOpen(it) })
        // 머리(번호·이름·진행)는 여덟 개 모두 그대로 — 숨기지 않고 접기만 한다
        JourneyStage.entries.forEach { header(it).assertIsDisplayed() }
    }

    /** 머리 전체가 누를 수 있는 단추(Role.Button) + 펼쳐짐/접힘 상태를 알린다 */
    @Test
    fun headersAreExpandableButtonsWithAnnouncedState() {
        show()
        val plan = header(JourneyStage.Plan).fetchSemanticsNode()
        assertEquals(Role.Button, plan.config.getOrNull(SemanticsProperties.Role))
        assertEquals(s(R.string.state_expanded), plan.config.getOrNull(SemanticsProperties.StateDescription))
        rule.onNode(
            hasContentDescription(headerCd(JourneyStage.Book)) and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, s(R.string.state_collapsed)),
        ).assertIsDisplayed()
    }

    /** 고른 단계는 화면이 다시 만들어져도(회전·프로세스 종료) 그대로다 (rememberSaveable) */
    @Test
    fun openStageSurvivesRecreation() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { ReadyPortTheme { TripJourneyContent(ui(), ChecklistActions()) } }
        rule.waitForIdle()
        header(JourneyStage.During).performClick()
        rule.waitForIdle()
        assertTrue(isOpen(JourneyStage.During))
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertTrue("다시 만든 뒤 고른 단계가 사라졌다", isOpen(JourneyStage.During))
        assertFalse("다시 만든 뒤 지금 단계가 다시 펼쳐졌다", isOpen(JourneyStage.Plan))
    }

    /** 모두 접힌 상태도 그대로 뜬다 (갤러리·테스트용 openAtFirst) */
    @Test
    fun allFoldedStateShowsOnlyHeaders()  {
        show(openAtFirst = JOURNEY_ALL_FOLDED)
        assertEquals(0, JourneyStage.entries.count { isOpen(it) })
        JourneyStage.entries.forEach { assertFalse(bodyShown(it)) }
    }

    // ---------------- 지금 할 일 → 그 단계를 펼친다 ----------------

    /** 지금 할 일 버튼은 번호가 붙은 `n단계 이름 열기`이고, 누르면 그 단계가 펴진다 */
    @Test
    fun theNowCardButtonOpensThatStageInPlace() {
        show(openAtFirst = JOURNEY_ALL_FOLDED)
        val label = s(R.string.journey_open_stage_step, JourneyStage.Plan.step, s(R.string.journey_plan))
        rule.onNodeWithText(label).assertIsDisplayed()
        rule.onNodeWithText(label).performClick()
        rule.waitForIdle()
        assertTrue("지금 할 일 버튼이 그 단계를 펼치지 않았다", isOpen(JourneyStage.Plan))
    }

    /** 예전 단계 막대(그림 격자 탭 줄)는 더 없다 — 같은 일을 하는 자리가 둘이던 것을 하나로 (부록 H.7) */
    @Test
    fun theOldStageBarIsGone() {
        show()
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(0)
        // 단계 이름은 화면에 **한 번만** 나온다(막대 + 카드로 두 번 나오던 것을 없앴다)
        JourneyStage.entries.forEach { stage ->
            rule.onAllNodesWithText(s(nameRes(stage))).assertCountEquals(1)
        }
    }
}
