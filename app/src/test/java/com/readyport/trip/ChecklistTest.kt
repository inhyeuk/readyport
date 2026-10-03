package com.readyport.trip

import com.readyport.pack.CountryPack
import com.readyport.pack.PassportValidityRule
import com.readyport.ui.TestPacks
import com.readyport.vault.FormRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.VaultContents
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 여행 체크리스트 만들기 (2026-10-02): 서명된 내장 팩 그대로(TestPacks)로 나라별 항목 수·기간 열림·여권 결과·같은 나라 두 여행을 본다.
 */
class ChecklistTest {

    private val index = TestPacks.index.value
    private fun pack(cc: String): CountryPack = runBlocking { TestPacks.repo.pack(cc)!!.value }
    private fun d(s: String) = LocalDate.parse(s)
    private fun trip(cc: String, s: String = "2026-11-03", e: String = "2026-11-07", id: String = "t-$cc") =
        Trip(country = cc, startDate = s, endDate = e, id = id)

    private fun build(
        t: Trip,
        today: String = "2026-10-02",
        checks: TripChecks = TripChecks(),
        saved: Boolean? = null,
    ) = Checklist.build(Checklist.Input(t, index, pack(t.country), checks, saved, d(today)))

    private fun ChecklistData.item(id: String) = items.firstOrNull { it.id == id }

    /** 팩에 공항 안내가 있으면 `도착 공항 순서 보기` 한 줄 */
    private fun airportItem(cc: String) = if (pack(cc).airports.isNotEmpty()) 1 else 0

    // ---------------- 나라별 항목 ----------------

    @Test
    fun thailandAndChinaTripCounts() {
        val th = build(trip("TH"))
        val cn = build(trip("CN"))
        // 틀 29줄 중 태국·중국은 어댑터·전압(한국 플러그·220 V 그대로)이 빠지고, 나라 팩 항목이 더해진다.
        // `도착 공항 순서 보기`는 팩에 공항 안내(airports)가 있는 나라만 — 공항을 합치면 그 나라 수가 하나 는다(merge_airports.py)
        assertEquals(27 + airportItem("TH"), th.total)
        assertEquals(1, airportItem("TH"))
        assertEquals(30 + airportItem("CN"), cn.total)
        assertEquals(JourneyStage.entries.toList(), th.stages)
        // 단계 순서 — 계획이 맨 앞, 복귀(여권 정보 지우기)가 맨 끝
        assertEquals("passport_validity", th.items.first().id)
        assertEquals("passport_destroy", th.items.last().id)
        assertTrue(cn.items.any { it.id == "country.stay_register" && it.stage == JourneyStage.Arrival })
        // 나머지 나라: 전기 조건(어댑터·전압)·입국 카드 유무·나라 팩 항목 수만큼 달라진다
        val counts = listOf("JP", "SG", "MY", "ID", "TW", "PH", "VN").associateWith { build(trip(it)).total }
        // 베트남은 사전 입국 정보(PAI)가 양식 카드로 옮겨 가면서 `입국 정보 미리 내기` 팩 항목이 빠졌다 —
        // 의무가 아닌 신고(forms[].optional)는 기한 있는 할 일로 세지 않는다
        val base = mapOf("JP" to 29, "SG" to 29, "MY" to 28, "ID" to 27, "TW" to 30, "PH" to 29, "VN" to 25)
        assertEquals(base.mapValues { (cc, n) -> n + airportItem(cc) }, counts)
    }

    @Test
    fun everyCountryBuildsAndFactItemsCarrySources() {
        listOf("TH", "JP", "SG", "MY", "ID", "TW", "CN", "PH", "VN").forEach { cc ->
            val p = pack(cc)
            val data = build(trip(cc))
            assertTrue("$cc 항목 수 ${data.total}", data.total in 22..34)
            data.items.filter { it.kind == ItemKind.Pack }.forEach { item ->
                assertNotNull("$cc ${item.id} 출처", item.source)
                assertNotNull("$cc ${item.id} 출처 이름", item.source?.name)
            }
            // 나라 팩 항목은 팩 섹션 문장 그대로(지어낸 말 없음)
            data.items.filter { it.id.startsWith("country.") }.forEach { item ->
                assertTrue("$cc ${item.id}", p.sections.any { s -> item.body in s.bodyKo })
            }
            // 꼭 내야 하는 입국 카드가 없는 나라(베트남 — 사전 입국 정보는 의무가 아님)는 입국 카드·확인 화면 항목을 만들지 않는다
            assertEquals(cc, p.requiredForms.isNotEmpty(), data.item("entry_form") != null)
            assertEquals(cc, p.requiredForms.isNotEmpty(), data.item("show_entry") != null)
        }
    }

    @Test
    fun essentialsAreFoldedInWithTheirConditions() {
        val th = build(trip("TH"))
        val ids = th.items.filter { it.id.startsWith("essential.") }.map { it.id.removePrefix("essential.") }.toSet()
        assertEquals(setOf("passport", "power_bank", "travel_insurance", "payment", "medicine"), ids)
        // 싱가포르(영국식 3핀) = 어댑터, 일본(100 V·플러그 미확인) = 어댑터 + 전압 확인
        assertNotNull(build(trip("SG")).item("essential.plug_adapter"))
        assertNull(build(trip("SG")).item("essential.voltage_check"))
        val jp = build(trip("JP"))
        assertNotNull(jp.item("essential.plug_adapter"))
        assertNotNull(jp.item("essential.voltage_check"))
        // 보조배터리 규칙은 국토교통부 출처를 그대로
        assertEquals("2026-09-29", th.item("essential.power_bank")?.source?.lastVerified)
    }

    // ---------------- 기간이 열리는 항목 ----------------

    @Test
    fun entryFormUnlocksWhenWindowOpens() {
        val t = trip("TH")
        val form = build(t).item("entry_form")!!
        // 기한 축은 기간 일수가 정하고(3일 → 3일 전 칸), 묶는 축은 언제나 서류 단계다
        assertEquals(DueWindow.ThreeDays, form.due)
        assertEquals(JourneyStage.Docs, form.stage)
        assertEquals(d("2026-11-01"), form.opensOn)
        assertTrue(form.locked(d("2026-10-31")))
        assertFalse(form.locked(d("2026-11-01")))
        // 잠긴 동안은 사람이 체크해도 체크되지 않는다
        val marked = build(t, today = "2026-10-31", checks = TripChecks(marks = mapOf("entry_form" to true))).item("entry_form")!!
        assertFalse(marked.checked)
        // 출발 당일까지 안 냈으면 그때만 빨강(급함)
        assertFalse(build(t, today = "2026-11-02").item("entry_form")!!.urgent)
        assertTrue(build(t, today = "2026-11-03").item("entry_form")!!.urgent)
        // 이 여행 기간에 낸 기록이 있으면 앱이 체크
        val done = build(t, today = "2026-11-02", checks = TripChecks(formSubmitted = true)).item("entry_form")!!
        assertTrue(done.checked)
        assertEquals(AutoState.Done, done.auto)
    }

    @Test
    fun windowLengthPicksPhase() {
        // 대만 TWAC는 도착 7일 전부터 → 일주일 전 단계, 중국 온라인 입국 카드는 기간이 없어 잠그지 않는다
        val tw = build(trip("TW")).item("entry_form")!!
        assertEquals(DueWindow.Week, tw.due)
        assertEquals(JourneyStage.Docs, tw.stage)
        assertEquals(d("2026-10-28"), tw.opensOn)
        val cn = build(trip("CN")).item("entry_form")!!
        assertEquals(DueWindow.ThreeDays, cn.due)
        assertNull(cn.opensOn)
        // 미리 안 내도 되는 입국 카드(기간 없음)는 출발 당일에도 빨강·늦음을 붙이지 않는다
        val cnDay = build(trip("CN"), today = "2026-11-03").item("entry_form")!!
        assertFalse(cnDay.urgent)
        assertFalse(cnDay.overdue)
    }

    @Test
    fun overdueIsGentleAndOnlyBeforeTravel() {
        val t = trip("TH")
        // 출발 5일 전: 한 달 전 항목(기한 10-26)은 늦음, 일주일 전 항목은 아직
        val data = build(t, today = "2026-10-29")
        assertTrue(data.item("booking")!!.overdue)
        assertFalse(data.item("offline_map")!!.overdue)
        // 여행 중·귀국 항목에는 늦음을 붙이지 않는다
        val after = build(t, today = "2026-11-20")
        assertFalse(after.item("leftover_cash")!!.overdue)
        assertFalse(after.item("passport_keep")!!.overdue)
    }

    // ---------------- 여권 남은 기간 ----------------

    private val thRule get() = pack("TH").requirements.first().passportValidity

    @Test
    fun passportRulesFromPacks() {
        val rules = listOf("TH", "JP", "SG", "MY", "ID", "TW", "CN", "PH", "VN").associateWith { pack(it).requirements.first().passportValidity }
        listOf("TH", "SG", "MY", "ID", "TW", "PH", "VN").forEach { cc ->
            val r = rules[cc]!!
            assertEquals(cc, 6, r.months)
            assertEquals(cc, "arrival", r.basis)
            assertEquals(cc, "2026-10-02", r.lastVerified)
            assertNotNull("$cc 출처", pack(cc).source(r.source))
        }
        // 공식 안내에 기준이 없는 나라는 비워 둔다(지어내지 않는다)
        assertNull(rules["JP"])
        assertNull(rules["CN"])
    }

    @Test
    fun passportValidityResults() {
        val t = trip("TH")
        val rule = thRule
        // 입국일(11-03) 기준 6개월 = 2027-05-03 이상
        assertEquals(PassportStatus.Ok, PassportValidity.check(d("2027-05-03"), t, rule).status)
        assertEquals(PassportStatus.Short, PassportValidity.check(d("2027-05-02"), t, rule).status)
        assertEquals(6, PassportValidity.check(d("2027-05-02"), t, rule).months)
        // 기준이 없는 나라: 여행 기간을 채우면 '모름', 여행 중에 끝나면 '모자람'
        assertEquals(PassportStatus.Unknown, PassportValidity.check(d("2026-11-08"), trip("JP"), null).status)
        assertEquals(PassportStatus.Short, PassportValidity.check(d("2026-11-05"), trip("JP"), null).status)
        // 나오는 날 기준이면 돌아오는 날부터 센다
        val departure = PassportValidityRule(3, "departure", "x", "2026-10-02")
        assertEquals(PassportStatus.Ok, PassportValidity.check(d("2027-02-07"), t, departure).status)
        assertEquals(PassportStatus.Short, PassportValidity.check(d("2027-02-06"), t, departure).status)
    }

    @Test
    fun passportItemFollowsStoredResultOnly() {
        val t = trip("TH")
        val ok = PassportValidity.check(d("2030-01-01"), t, thRule)
        val short = PassportValidity.check(d("2027-01-01"), t, thRule)

        val okItem = build(t, checks = TripChecks(passport = ok), saved = true).item("passport_validity")!!
        assertTrue(okItem.checked)
        assertEquals(AutoState.Done, okItem.auto)

        val shortItem = build(t, checks = TripChecks(passport = short), saved = true).item("passport_validity")!!
        assertFalse(shortItem.checked)
        assertEquals(AutoState.NotDone, shortItem.auto)
        assertEquals(PassportStatus.Short, (shortItem.detail as ItemDetail.Passport).check?.status)
        assertNotNull((shortItem.detail as ItemDetail.Passport).reissue)

        // 여행 날짜를 고치면 지난 결과는 쓰지 않는다(지갑을 다시 열 때까지 '모름')
        val moved = build(t.copy(startDate = "2026-12-03", endDate = "2026-12-07"), checks = TripChecks(passport = ok), saved = true).item("passport_validity")!!
        assertEquals(AutoState.Unknown, moved.auto)
        assertFalse(moved.checked)

        // 여권이 없으면 손으로 체크하는 항목(여권 넣기 버튼)
        val none = build(t, saved = false).item("passport_validity")!!
        assertEquals(AutoState.Unknown, none.auto)
        assertEquals(false, (none.detail as ItemDetail.Passport).saved)
        assertTrue(build(t, saved = false, checks = TripChecks(marks = mapOf("passport_validity" to true))).item("passport_validity")!!.checked)

        // 앱이 확인한 것도 사람이 되돌릴 수 있다
        val overridden = build(t, checks = TripChecks(passport = ok, marks = mapOf("passport_validity" to false)), saved = true).item("passport_validity")!!
        assertFalse(overridden.checked)
        assertTrue(overridden.overridden)

        // 기준이 없는 나라: 규칙 null + 0404 링크
        val jp = trip("JP")
        val jpItem = build(jp, checks = TripChecks(passport = PassportValidity.check(d("2030-01-01"), jp, null)), saved = true).item("passport_validity")!!
        val detail = jpItem.detail as ItemDetail.Passport
        assertNull(detail.rule)
        assertEquals(PassportStatus.Unknown, detail.check?.status)
        assertTrue(detail.official!!.url.contains("0404.go.kr"))
        assertFalse(jpItem.checked)
    }

    @Test
    fun signalsStoreResultsNotDates() {
        val th = trip("TH")
        val thLater = trip("TH", "2027-02-10", "2027-02-14", id = "t-TH-2")
        val jp = trip("JP")
        val contents = VaultContents(
            passport = PassportRecord("HAPPY", "TRAVELER", "M12345678", "KOR", "KOR", "1990-01-01", "F", "2027-06-01", "manual", false, "x"),
            forms = mapOf("TH_TDAC" to FormRecord("TH_TDAC", status = "submitted", updatedAt = "x", submittedAt = "2026-11-01T10:00:00")),
        )
        val packs = mapOf("TH" to pack("TH"), "JP" to pack("JP"))
        val (saved, perTrip) = TripSignalsRecorder.signals(contents, listOf(th, thLater, jp), packs)
        assertTrue(saved)
        // 11월 여행: 2027-05-03 이상 필요 → 괜찮음, 2월 여행: 2027-08-10 이상 필요 → 모자람 (같은 나라라도 여행마다 다르다)
        assertEquals(PassportStatus.Ok, perTrip[th.id]!!.first!!.status)
        assertEquals(PassportStatus.Short, perTrip[thLater.id]!!.first!!.status)
        assertEquals(PassportStatus.Unknown, perTrip[jp.id]!!.first!!.status)
        // 입국 카드 제출 기록은 그 여행 기간 것만
        assertEquals(true, perTrip[th.id]!!.second)
        assertEquals(false, perTrip[thLater.id]!!.second)
        // 저장되는 결과에 날짜·여권 번호가 없다
        val stored = Json.encodeToString(TripBook.serializer(), TripBook(checks = perTrip.mapValues { (_, v) -> TripChecks(passport = v.first, formSubmitted = v.second) }))
        assertFalse(stored.contains("2027-06-01"))
        assertFalse(stored.contains("M12345678"))
        assertFalse(stored.contains("1990"))
    }

    // ---------------- 도착 공항 순서 (2026-10-03) ----------------

    @Test
    fun airportItemOnlyWhenPackHasAirports() {
        val th = build(trip("TH"))
        val item = th.item("airport_steps")
        assertNotNull(item)
        assertEquals(JourneyStage.Arrival, item!!.stage)
        assertEquals(DueWindow.Arrival, item.due)
        assertEquals(ChecklistAction.OpenAirport, item.action)
        // 공항을 고르지 않았으면 코드 없이(나라 화면이 여행 공항·첫 공항을 고른다), 출처는 첫 공항 안내
        val d = item.detail as ItemDetail.AirportGuide
        assertEquals("TH", d.country)
        assertNull(d.code)
        val first = pack("TH").airports.first()
        assertEquals(pack("TH").source(first.source)!!.name, item.source!!.name)
        assertEquals(first.lastVerified, item.source!!.lastVerified)
        // 공항 안내가 없는 팩에는 항목이 없다
        listOf("TH", "JP", "SG", "MY", "ID", "TW", "CN", "PH", "VN").filter { pack(it).airports.isEmpty() }.forEach { cc ->
            assertNull(cc, build(trip(cc)).item("airport_steps"))
        }
    }

    @Test
    fun airportItemCarriesChosenAirport() {
        val dmk = build(trip("TH").copy(arrivalAirport = "DMK")).item("airport_steps")!!
        val d = dmk.detail as ItemDetail.AirportGuide
        assertEquals("DMK", d.code)
        assertEquals("돈므앙 공항", d.name)
        // 팩에 없는 코드(나라를 바꾼 뒤 남은 값 등)는 고르지 않은 것으로
        val unknown = build(trip("TH").copy(arrivalAirport = "NRT")).item("airport_steps")!!.detail as ItemDetail.AirportGuide
        assertNull(unknown.code)
    }

    @Test
    fun arrivalAirportIsOptionalInStoredTrip() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        // 예전 저장본(공항 없음)은 null로 읽힌다
        val old = json.decodeFromString(Trip.serializer(), """{"country":"TH","startDate":"2026-11-03","endDate":"2026-11-07","id":"x"}""")
        assertNull(old.arrivalAirport)
        assertFalse(json.encodeToString(Trip.serializer(), old).contains("arrivalAirport"))
        // 공항 코드만 저장한다(날짜·나라·코드 — 개인정보 없음)
        val withAirport = old.copy(arrivalAirport = "BKK")
        val stored = json.encodeToString(Trip.serializer(), withAirport)
        assertTrue(stored.contains("\"arrivalAirport\":\"BKK\""))
        assertEquals(withAirport, json.decodeFromString(Trip.serializer(), stored))
    }

    // ---------------- 같은 나라 두 여행 ----------------

    @Test
    fun sameCountryTwiceGivesIndependentChecklists() {
        val a = trip("TH", id = "a")
        val b = trip("TH", "2027-02-10", "2027-02-14", id = "b")
        val checksA = TripChecks(marks = mapOf("booking" to true, "essential.payment" to true), custom = listOf(CustomItem("custom.1", "우산")))
        val dataA = build(a, checks = checksA)
        val dataB = build(b, checks = TripChecks())
        // 앱이 확인하는 '나라 안내 받아 두기'(내장 팩)는 두 여행 모두 체크, 나머지는 여행 A만
        assertEquals(3, dataA.done)
        assertEquals(1, dataB.done)
        assertFalse(dataB.item("booking")!!.checked)
        assertTrue(dataB.custom.isEmpty())
        assertEquals(28 + airportItem("TH"), dataA.total)
        assertEquals(27 + airportItem("TH"), dataB.total)
        // 입국 카드 기간도 각 여행 날짜로
        assertEquals(d("2026-11-01"), dataA.item("entry_form")!!.opensOn)
        assertEquals(d("2027-02-08"), dataB.item("entry_form")!!.opensOn)
    }

    // ---------------- 지금 챙길 것 ----------------

    @Test
    fun nowItemsFollowCurrentDueWindow() {
        val t = trip("TH")
        assertEquals(DueWindow.Month, Checklist.currentDue(t, d("2026-10-02")))
        assertEquals(DueWindow.Week, Checklist.currentDue(t, d("2026-10-28")))
        assertEquals(DueWindow.ThreeDays, Checklist.currentDue(t, d("2026-11-01")))
        assertEquals(DueWindow.DepartureDay, Checklist.currentDue(t, d("2026-11-03")))
        assertEquals(DueWindow.Arrival, Checklist.currentDue(t.copy(arrivedAt = 1L), d("2026-11-03")))
        assertEquals(DueWindow.During, Checklist.currentDue(t, d("2026-11-05")))
        assertEquals(DueWindow.BeforeReturn, Checklist.currentDue(t, d("2026-11-06")))
        assertEquals(DueWindow.Back, Checklist.currentDue(t, d("2026-11-08")))

        val now = Checklist.nowItems(build(t), t, d("2026-10-02"))
        assertEquals(3, now.size)
        assertTrue(now.all { it.due == DueWindow.Month })
        // 출발 당일: 아직 안 낸 입국 카드가 맨 앞(급함)
        val dep = Checklist.nowItems(build(t, today = "2026-11-03"), t, d("2026-11-03"))
        assertEquals("entry_form", dep.first().id)
        // 다 했으면 다음 단계에서 미리 할 것
        val allMonth = build(t).due(DueWindow.Month).associate { it.id to true }
        val ahead = Checklist.nowItems(build(t, checks = TripChecks(marks = allMonth)), t, d("2026-10-02"))
        assertTrue(ahead.isNotEmpty())
        assertTrue(ahead.all { it.due!! > DueWindow.Month })
        // 아직 열리지 않은 입국 카드는 '지금 챙길 것'에 없다
        assertTrue(ahead.none { it.id == "entry_form" })
    }

    @Test
    fun markForReturnsToAppJudgement() {
        val auto = ChecklistItem("x", JourneyStage.Plan, DueWindow.Month, ItemKind.Auto, "passport", "x", auto = AutoState.Done, checked = true)
        assertNull(markFor(auto, true))
        assertEquals(false, markFor(auto, false))
        val manual = ChecklistItem("y", JourneyStage.Plan, DueWindow.Month, ItemKind.Generic, "flight", "y")
        assertEquals(true, markFor(manual, true))
        assertNull(markFor(manual, false))
    }
}
