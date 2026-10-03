package com.readyport.trip

import com.readyport.pack.CountryPack
import com.readyport.pack.PackIndex
import com.readyport.ui.TestPacks
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * 여행 과정 8단계 (2026-10-03, DESIGN_SPEC 부록 H) — **두 축이 서로 독립인지**를 본다.
 *
 * ① 묶는 축([JourneyStage]): 29개 틀 항목과 나라 팩 항목이 어느 단계에 들어가는지.
 * ② 기한 축([DueWindow]): 기한·늦음·알림은 2026-10-02와 **똑같아야 한다** — 단계가 바뀌어도 날짜 계산은 그대로.
 * ③ 예전 서명 팩(단계 값이 없는 색인)도 그대로 열리고, 체크는 항목 id에 붙어 있어 묶음이 바뀌어도 남는다.
 */
class JourneyStagesTest {

    private val index = TestPacks.index.value
    private fun pack(cc: String): CountryPack = runBlocking { TestPacks.repo.pack(cc)!!.value }
    private fun d(s: String) = LocalDate.parse(s)
    private val trip = Trip(country = "TH", startDate = "2026-11-03", endDate = "2026-11-07", id = "t-th")

    private fun build(idx: PackIndex = index, t: Trip = trip, checks: TripChecks = TripChecks(), today: String = "2026-10-02") =
        Checklist.build(Checklist.Input(t, idx, pack(t.country), checks, passportSaved = true, today = d(today)))

    /** 서명된 내장 색인에서 `stage` 줄만 지운 것 = 예전 서명 팩 (앱은 phase 에서 단계를 옮겨 와야 한다) */
    private val legacyIndex: PackIndex by lazy {
        val text = File("src/main/assets/packs/index.json").readText()
        // 내장 팩은 공백 없이 쓰여 있다 (build_packs.py separators=(",", ":"))
        val stripped = text.replace(Regex(""""stage":"[a-z_]+","""), "")
        assertTrue("stage 줄이 지워져야 한다", "\"stage\"" !in stripped)
        Json { ignoreUnknownKeys = true }.decodeFromString(PackIndex.serializer(), stripped)
    }

    /** 틀 29줄이 들어가는 단계 (운영자와 합의한 묶음 — 사장님이 생각하는 '무엇을 하는 일') */
    private val expected = mapOf(
        "passport_validity" to JourneyStage.Plan,
        "visa" to JourneyStage.Plan,
        "advisory" to JourneyStage.Plan,
        "booking" to JourneyStage.Book,
        "essential.travel_insurance" to JourneyStage.Book,
        "data" to JourneyStage.Book,
        "passport_saved" to JourneyStage.Docs,
        "entry_form" to JourneyStage.Docs,
        "essential.payment" to JourneyStage.Pack,
        "essential.medicine" to JourneyStage.Pack,
        "essential.power_bank" to JourneyStage.Pack,
        "essential.plug_adapter" to JourneyStage.Pack,
        "essential.voltage_check" to JourneyStage.Pack,
        "offline_pack" to JourneyStage.Pack,
        "offline_map" to JourneyStage.Pack,
        "address_local" to JourneyStage.Pack,
        "consular_call" to JourneyStage.Pack,
        "phrases" to JourneyStage.Pack,
        "essential.passport" to JourneyStage.Departure,
        "airport_time" to JourneyStage.Departure,
        "airport_steps" to JourneyStage.Arrival,
        "show_entry" to JourneyStage.Arrival,
        "emergency_number" to JourneyStage.Arrival,
        "passport_keep" to JourneyStage.During,
        "shopping_check" to JourneyStage.During,
        "return_rules" to JourneyStage.Return,
        "leftover_cash" to JourneyStage.Return,
        "return_flight" to JourneyStage.Return,
        "passport_destroy" to JourneyStage.Return,
    )

    // ---------------- ① 묶는 축 ----------------

    @Test
    fun templateHas29ItemsAndEveryOneCarriesAStage() {
        assertEquals(29, index.checklist.size)
        assertEquals(expected.keys, index.checklist.map { it.id }.toSet())
        index.checklist.forEach { assertNotNull("${it.id} stage", it.stage) }
    }

    /** 29개 항목이 모두 합의한 단계로 들어간다 (일본 — 전기 조건이 다 붙는 나라로 본다) */
    @Test
    fun everyTemplateItemLandsInItsStage() {
        val data = build(t = trip.copy(country = "JP"))
        val byId = data.items.associate { it.id to it.stage }
        expected.forEach { (id, stage) ->
            // 그 나라에 사실이 없어 만들어지지 않는 항목은 건너뛴다(사실을 지어내지 않는다)
            byId[id]?.let { assertEquals(id, stage, it) }
        }
        // 일본은 전기 조건이 모두 붙는 나라 — 29줄이 하나도 빠지지 않는다
        assertEquals(expected.keys, byId.keys.filter { it in expected }.toSet())
    }

    /** 나라 팩 항목도 단계를 들고 온다 (중국 네 줄) */
    @Test
    fun countryPackItemsCarryStages() {
        val cn = build(t = trip.copy(country = "CN"))
        val byId = cn.items.filter { it.id.startsWith("country.") }.associate { it.id to it.stage }
        assertEquals(
            mapOf(
                "country.medicine_cold" to JourneyStage.Pack,
                "country.cash_declare" to JourneyStage.Docs,
                "country.return_ticket" to JourneyStage.Departure,
                "country.stay_register" to JourneyStage.Arrival,
            ),
            byId,
        )
    }

    /** 단계는 여행 순서대로 나오고, 항목은 단계 안에서 틀 순서를 지킨다 */
    @Test
    fun stagesAreInJourneyOrder() {
        val data = build()
        assertEquals(JourneyStage.entries.toList(), data.stages)
        val order = data.items.map { it.stage!!.ordinal }
        assertEquals(order.sorted(), order)
        assertEquals("passport_validity", data.stage(JourneyStage.Plan).first().id)
        assertEquals("passport_destroy", data.stage(JourneyStage.Return).last().id)
    }

    // ---------------- ② 기한 축은 그대로 ----------------

    /** 기한 날짜는 2026-10-02 규칙 그대로 — 단계를 바꿔도 하루도 움직이지 않는다 */
    @Test
    fun dueDatesAreUnchangedByTheRegrouping() {
        val data = build()
        fun due(id: String) = data.items.first { it.id == id }.dueBy
        // 한 달 전쯤 = 출발 8일 전, 일주일 전 = 4일 전, 3일 전 = 하루 전, 출발하는 날 = 출발일
        assertEquals(d("2026-10-26"), due("passport_validity"))
        assertEquals(d("2026-10-26"), due("booking"))
        assertEquals(d("2026-10-30"), due("essential.payment"))
        assertEquals(d("2026-10-30"), due("consular_call"))
        assertEquals(d("2026-11-02"), due("entry_form"))
        assertEquals(d("2026-11-02"), due("phrases"))
        assertEquals(d("2026-11-03"), due("essential.passport"))
        assertEquals(d("2026-11-04"), due("airport_steps"))
        assertEquals(d("2026-11-07"), due("passport_keep"))
        assertEquals(d("2026-11-07"), due("leftover_cash"))
        assertEquals(d("2026-11-14"), due("passport_destroy"))
        // 같은 단계(짐) 안에도 기한이 다른 항목이 있다 — 두 축이 서로 독립이라는 증거
        assertEquals(DueWindow.Week, data.items.first { it.id == "essential.payment" }.due)
        assertEquals(DueWindow.ThreeDays, data.items.first { it.id == "phrases" }.due)
        assertEquals(JourneyStage.Pack, data.items.first { it.id == "phrases" }.stage)
    }

    /** 늦음은 기한 축만 본다 — 짐 단계 안에서도 기한이 지난 것만 늦다 */
    @Test
    fun overdueFollowsTheDueAxisNotTheStage() {
        val data = build(today = "2026-10-31")
        assertTrue(data.items.first { it.id == "essential.payment" }.overdue)
        assertEquals(false, data.items.first { it.id == "phrases" }.overdue)
    }

    // ---------------- ③ 예전 서명 팩 · 체크 살아남기 ----------------

    /** 단계 값이 없는 예전 서명 색인도 그대로 열리고, 기한 이름에서 단계를 옮겨 온다 */
    @Test
    fun oldSignedIndexFallsBackToLegacyStages() {
        val old = build(legacyIndex)
        val now = build()
        // 항목이 하나도 빠지지 않는다 (차례는 단계 순서라 다르다 — 묶음이 다르니까)
        assertEquals(now.items.map { it.id }.toSet(), old.items.map { it.id }.toSet())
        assertEquals(now.total, old.total)
        // 기한은 항목마다 **완전히 같다**
        assertEquals(now.items.associate { it.id to it.due }, old.items.associate { it.id to it.due })
        assertEquals(now.items.associate { it.id to it.dueBy }, old.items.associate { it.id to it.dueBy })
        // 단계는 기한 이름에서 옮겨 온 값 (month→계획 · week→짐 · three_days→서류 · departure_day→출국 · arrival→입국 · during→여행 중 · before_return·back→복귀)
        val byId = old.items.associate { it.id to it.stage }
        assertEquals(JourneyStage.Plan, byId["booking"])
        assertEquals(JourneyStage.Pack, byId["essential.payment"])
        assertEquals(JourneyStage.Docs, byId["entry_form"])
        assertEquals(JourneyStage.Departure, byId["airport_time"])
        assertEquals(JourneyStage.Arrival, byId["emergency_number"])
        assertEquals(JourneyStage.During, byId["passport_keep"])
        assertEquals(JourneyStage.Return, byId["leftover_cash"])
        assertEquals(JourneyStage.Return, byId["passport_destroy"])
        // 예전 색인에는 `예약`으로 가는 기한 이름이 없다 — 그 단계에는 항목이 없다.
        // 화면은 여덟 단계를 **언제나** 그리므로(단계마다 그 단계에서 하는 일이 있다) 예약 서류 넣어 두기는 그대로 닿는다
        assertEquals(JourneyStage.entries - JourneyStage.Book, old.stages)
    }

    /** 체크는 항목 id에 붙어 있다 — 묶음이 바뀌어도(예전 팩 ↔ 새 팩) 한 일이 그대로 남는다 */
    @Test
    fun checkStatesSurviveTheRegrouping() {
        val marks = mapOf("booking" to true, "essential.payment" to true, "phrases" to true, "leftover_cash" to true)
        val checks = TripChecks(marks = marks)
        val old = build(legacyIndex, checks = checks)
        val now = build(checks = checks)
        assertEquals(old.done, now.done)
        assertEquals(
            old.items.filter { it.checked }.map { it.id }.toSet(),
            now.items.filter { it.checked }.map { it.id }.toSet(),
        )
        marks.keys.forEach { id -> assertTrue(id, now.items.first { it.id == id }.checked) }
    }

    /** 알림은 기한 축만 본다 — 예전 팩과 새 팩이 **같은 항목을 같은 이유로** 알린다 */
    @Test
    fun remindersAreIdenticalBeforeAndAfterTheRegrouping() {
        listOf("2026-10-27", "2026-10-31", "2026-11-01", "2026-11-04").forEach { day ->
            val today = d(day)
            fun reminder(idx: PackIndex) = ChecklistReminders.reminderFor(
                trip,
                Checklist.build(Checklist.Input(trip, idx, pack("TH"), TripChecks(), passportSaved = true, today = today)),
                today,
            )
            val old = reminder(legacyIndex)
            val now = reminder(index)
            assertEquals(day, old?.reason, now?.reason)
            // **같은 항목을 같은 이유로** 알린다. 급함·늦음·기한이 같은 항목들 사이의 차례만 목록 순서를 따른다
            assertEquals(day, old?.titles?.toSet(), now?.titles?.toSet())
            assertEquals(day, old?.titles?.firstOrNull(), now?.titles?.firstOrNull())
        }
    }

    // ---------------- 지금 단계 ----------------

    /** 떠나기 전 '지금 단계'는 날짜가 아니라 **아직 안 끝난 첫 준비 단계**다 (계획 → 예약 → 서류 → 짐) */
    @Test
    fun currentStageWalksThroughPreparationAsYouFinishIt() {
        val today = d("2026-10-02")
        var data = build(today = "2026-10-02")
        assertEquals(JourneyStage.Plan, Checklist.currentStage(data, trip, today))
        fun checkThrough(upTo: JourneyStage): TripChecks =
            TripChecks(marks = build().items.filter { it.stage!! <= upTo }.associate { it.id to true })
        data = build(checks = checkThrough(JourneyStage.Plan))
        assertEquals(JourneyStage.Book, Checklist.currentStage(data, trip, today))
        data = build(checks = checkThrough(JourneyStage.Book))
        assertEquals(JourneyStage.Docs, Checklist.currentStage(data, trip, today))
        data = build(checks = checkThrough(JourneyStage.Docs))
        assertEquals(JourneyStage.Pack, Checklist.currentStage(data, trip, today))
        data = build(checks = checkThrough(JourneyStage.Pack))
        assertEquals(JourneyStage.Departure, Checklist.currentStage(data, trip, today))
    }

    /** 떠난 뒤는 날짜가 정한다 — 출국 · 입국 · 여행 중 · 복귀 */
    @Test
    fun currentStageAfterDepartureFollowsTheDates() {
        val data = build()
        assertEquals(JourneyStage.Departure, Checklist.currentStage(data, trip, d("2026-11-03")))
        assertEquals(JourneyStage.Arrival, Checklist.currentStage(data, trip.copy(arrivedAt = 1L), d("2026-11-03")))
        assertEquals(JourneyStage.Arrival, Checklist.currentStage(data, trip, d("2026-11-04")))
        assertEquals(JourneyStage.During, Checklist.currentStage(data, trip, d("2026-11-05")))
        assertEquals(JourneyStage.Return, Checklist.currentStage(data, trip, d("2026-11-06")))
        assertEquals(JourneyStage.Return, Checklist.currentStage(data, trip, d("2026-11-09")))
    }

    /** 아직 할 수 없는 항목(입국 카드 기간 전)만 남은 단계는 '지금 단계'로 서지 않는다 */
    @Test
    fun lockedOnlyStageIsNotCurrent() {
        val today = d("2026-10-02")
        val marks = build().items
            .filter { it.stage!! <= JourneyStage.Docs && it.id != "entry_form" }
            .associate { it.id to true }
        val data = build(checks = TripChecks(marks = marks))
        // 서류 단계에 남은 것은 아직 열리지 않은 입국 카드뿐 → 짐 단계가 지금 단계
        assertEquals(JourneyStage.Pack, Checklist.currentStage(data, trip, today))
    }
}
