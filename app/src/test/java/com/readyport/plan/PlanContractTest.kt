package com.readyport.plan

import com.readyport.attractions.rating.RatingRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 앱 값 = Firestore 규칙(firebase/firestore.rules) = ARIA(ops/aria/jobs/plan_requests.py) — 한쪽만 바꾸면 여기서 깨진다.
 * (값 목록을 글에서 읽어 비교한다. 규칙의 동작 자체는 tools/firestore/rules.test.mjs 에뮬레이터 테스트.)
 */
class PlanContractTest {
    private val rules = File("../firebase/firestore.rules").readText()
    private val aria = File("../ops/aria/jobs/plan_requests.py").readText()

    /** 규칙 글에서 `name(...) { return v in [...] }` 이나 `[...].toSet()` 목록 하나를 읽는다 */
    private fun quoted(block: String): List<String> = Regex("'([a-z_A-Z]+)'").findAll(block).map { it.groupValues[1] }.toList()

    private fun rulesBlock(start: String, end: String): String {
        val i = rules.indexOf(start)
        check(i >= 0) { "규칙에 $start 없음" }
        return rules.substring(i, rules.indexOf(end, i))
    }

    private fun ariaTuple(name: String): List<String> {
        val m = Regex("""$name = \(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL).find(aria) ?: error("ARIA 에 $name 없음")
        return Regex("\"([a-z_A-Z]+)\"").findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
    }

    @Test fun purposes() {
        val inRules = quoted(rulesBlock("function purposesOk", ".toSet()).size() == 0").substringAfter("difference(["))
        assertEquals(PlanPurpose.entries.map { it.id }, inRules)
        assertEquals(PlanPurpose.entries.map { it.id }, ariaTuple("PURPOSES"))
    }

    @Test fun mobilityIsASubsetOfRules() {
        val inRules = quoted(rulesBlock("function mobilityOk", ".toSet()).size() == 0").substringAfter("difference(["))
        assertTrue(PlanMobility.entries.map { it.id }.all { it in inRules })
        assertEquals(inRules, ariaTuple("MOBILITY"))
    }

    @Test fun budgetCountriesStatusesAndTimeHints() {
        val newPlan = rulesBlock("function newPlanOk", "match /plan_requests/")
        assertEquals(BudgetBand.entries.map { it.id }, quoted(newPlan.substringAfter("d.budget_band in [").substringBefore("]")))
        assertEquals(BudgetBand.entries.map { it.id }, ariaTuple("BUDGET_BANDS"))
        assertEquals(PlanRules.COUNTRIES, quoted(rulesBlock("function planCountry", "\n")))
        assertEquals(PlanRules.COUNTRIES, ariaTuple("COUNTRIES"))
        assertEquals(TimeHint.entries.map { it.id }, ariaTuple("TIME_HINTS"))
        assertEquals(PlanFailure.entries.map { it.id }, ariaTuple("ERROR_CODES"))
    }

    @Test fun payloadKeysAreAllowedByRules() {
        val newPlan = rulesBlock("function newPlanOk", "match /plan_requests/")
        val allowed = quoted(newPlan.substringAfter("d.keys().hasOnly([").substringBefore("])")).toSet()
        val required = quoted(newPlan.substringAfter("d.keys().hasAll([").substringBefore("])")).toSet()
        val full = PlanDraft(
            country = "TH", dates = PlanDates.Range("2026-11-01", "2026-11-03"), purposes = listOf(PlanPurpose.Food),
            note = "메모", travelers = PlanTravelers(adults = 1, female = 1, male = 0), mobility = setOf(PlanMobility.StairsHard),
            sensitiveConsent = true, budget = BudgetBand.Budget,
        )
        val keys = PlanRules.payload(full, "u", Any()).keys
        assertTrue("규칙이 받지 않는 칸: ${keys - allowed}", allowed.containsAll(keys))
        val minimal = PlanRules.payload(full.copy(note = "", mobility = emptySet(), sensitiveConsent = false, dates = PlanDates.Days(2)), "u", Any()).keys
        assertTrue("빠진 필수 칸: ${required - minimal}", minimal.containsAll(required))
        // ARIA 가 받는 칸과도 같다
        val ariaKeys = Regex("\"([a-z_A-Z]+)\"").findAll(aria.substringAfter("REQUEST_KEYS = {").substringBefore("}")).map { it.groupValues[1] }.toSet()
        assertEquals(allowed, ariaKeys)
    }

    @Test fun travelerKeysAndQuotaKeys() {
        val travelers = rulesBlock("function travelersOk", "function purposesOk")
        assertEquals(listOf("adults", "seniors", "teens", "children", "genders"), quoted(travelers.substringAfter("hasOnly([").substringBefore("])")))
        val quota = rulesBlock("match /plan_quota/", "match /plan_results/")
        assertEquals(setOf("last", "prev", "lastRequestId"), quoted(quota.substringAfter("hasOnly([").substringBefore("])")).toSet())
        assertEquals(setOf("last", "prev", "lastRequestId"), PlanRules.quotaPayload(null, "x", Any()).keys)
    }

    @Test fun voteKeysAndRatingKeyPattern() {
        val vote = rulesBlock("function voteOk", "match /attraction_ratings/")
        assertEquals(setOf("stars", "at", "visited"), quoted(vote.substringAfter("hasOnly([").substringBefore("])")).toSet())
        assertEquals(setOf("stars", "at", "visited"), RatingRules.votePayload(3, Any()).keys)
        assertTrue(rules.contains("^(TH|JP|VN|PH|TW|SG|MY|ID|CN)_[a-z0-9]+(-[a-z0-9]+)*$"))
    }
}
