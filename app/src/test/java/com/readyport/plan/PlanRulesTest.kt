package com.readyport.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * 계획 요청 검사·문서 모양이 Firestore 규칙(newPlanOk·plan_quota)과 **같은지** — 가짜 자료만(개인정보 없음).
 * 규칙 쪽은 tools/firestore/rules.test.mjs 가 같은 모양을 에뮬레이터로 검사한다.
 */
class PlanRulesTest {
    private val ok = PlanDraft(
        country = "JP",
        dates = PlanDates.Days(4),
        purposes = listOf(PlanPurpose.Food, PlanPurpose.Sightseeing),
        travelers = PlanTravelers(adults = 2),
        budget = BudgetBand.Standard,
    )
    private val server = Any()

    // ---------------- 문서 모양 ----------------

    @Test fun minimalPayloadHasExactlyTheRequiredKeys() {
        val p = PlanRules.payload(ok, "uid-1", server)
        assertEquals(setOf("uid", "country", "purposes", "travelers", "days", "budget_band", "currency", "status", "createdAt"), p.keys)
        assertEquals("uid-1", p["uid"])
        assertEquals("JP", p["country"])
        // 목적은 고른 순서가 아니라 정해진 순서
        assertEquals(listOf("sightseeing", "food"), p["purposes"])
        assertEquals(mapOf("adults" to 2, "seniors" to 0, "teens" to 0, "children" to 0), p["travelers"])
        assertEquals(4, p["days"])
        assertEquals("standard", p["budget_band"])
        assertEquals("KRW", p["currency"])
        assertEquals("queued", p["status"])
        assertSame(server, p["createdAt"])
        // 이동 조건이 없으면 동의 칸 자체가 없다(규칙: mobility 가 비면 sensitive_consent 가 있으면 안 된다)
        assertFalse("sensitive_consent" in p)
        assertFalse("mobility" in p)
        assertFalse("purpose_note" in p)
    }

    @Test fun mobilityNeedsSeparateConsentAndIsSentWithIt() {
        val noConsent = ok.copy(mobility = setOf(PlanMobility.Wheelchair, PlanMobility.LongWalkHard))
        assertEquals(setOf(PlanProblem.Consent), PlanRules.check(noConsent).problems)
        try {
            PlanRules.payload(noConsent, "u", server)
            fail("동의 없이 이동 조건을 보내면 안 된다")
        } catch (e: PlanError.Invalid) {
            // 기대한 대로
        }
        val p = PlanRules.payload(noConsent.copy(sensitiveConsent = true), "u", server)
        assertEquals(listOf("long_walk_hard", "wheelchair"), p["mobility"])
        assertEquals(true, p["sensitive_consent"])
    }

    @Test fun consentWithoutMobilityIsNotSent() {
        val p = PlanRules.payload(ok.copy(sensitiveConsent = true), "u", server)
        assertFalse("sensitive_consent" in p)
    }

    @Test fun tripDatesAreSentInsteadOfDays() {
        val p = PlanRules.payload(ok.copy(dates = PlanDates.Range("2026-11-02", "2026-11-06")), "u", server)
        assertEquals("2026-11-02", p["start_date"])
        assertEquals("2026-11-06", p["end_date"])
        assertFalse("days" in p)
    }

    @Test fun noteIsTrimmedAndGendersOnlyWhenGiven() {
        val p = PlanRules.payload(
            ok.copy(note = "  라멘 좋아해요  ", travelers = PlanTravelers(adults = 1, seniors = 1, female = 1)),
            "u",
            server,
        )
        assertEquals("라멘 좋아해요", p["purpose_note"])
        @Suppress("UNCHECKED_CAST")
        val t = p["travelers"] as Map<String, Any>
        assertEquals(mapOf("female" to 1), t["genders"])
        // 공백만이면 보내지 않는다
        assertFalse("purpose_note" in PlanRules.payload(ok.copy(note = "   "), "u", server))
    }

    @Test fun quotaPayloadKeepsPreviousLastAsIs() {
        val first = PlanRules.quotaPayload(null, "req1", server)
        assertEquals(setOf("last", "prev", "lastRequestId"), first.keys)
        assertSame(server, first["last"])
        assertNull(first["prev"])
        assertEquals("req1", first["lastRequestId"])
        val stamp = Any()
        assertSame(stamp, PlanRules.quotaPayload(stamp, "req2", server)["prev"])
    }

    // ---------------- 검사 ----------------

    @Test fun requiredChoices() {
        val empty = PlanRules.check(PlanDraft())
        assertTrue(PlanProblem.Country in empty.problems)
        assertTrue(PlanProblem.Purposes in empty.problems)
        assertTrue(PlanProblem.Budget in empty.problems)
        assertTrue(PlanRules.check(ok).problems.isEmpty())
        assertTrue(PlanProblem.Country in PlanRules.check(ok.copy(country = "KR")).problems)
    }

    @Test fun purposesOneToFiveWithoutRepeats() {
        assertTrue(PlanProblem.Purposes in PlanRules.check(ok.copy(purposes = PlanPurpose.entries.take(6))).problems)
        assertTrue(PlanProblem.Purposes in PlanRules.check(ok.copy(purposes = listOf(PlanPurpose.Food, PlanPurpose.Food))).problems)
        assertTrue(PlanRules.check(ok.copy(purposes = PlanPurpose.entries.take(5))).problems.isEmpty())
    }

    @Test fun travelersOneToTwentyAndGendersWithinTotal() {
        assertTrue(PlanProblem.Travelers in PlanRules.check(ok.copy(travelers = PlanTravelers(adults = 0))).problems)
        assertTrue(PlanProblem.Travelers in PlanRules.check(ok.copy(travelers = PlanTravelers(adults = 11, children = 10))).problems)
        assertTrue(PlanProblem.Travelers in PlanRules.check(ok.copy(travelers = PlanTravelers(adults = 21))).problems)
        assertTrue(PlanRules.check(ok.copy(travelers = PlanTravelers(adults = 10, children = 10))).problems.isEmpty())
        assertTrue(PlanProblem.Genders in PlanRules.check(ok.copy(travelers = PlanTravelers(adults = 2, female = 2, male = 1))).problems)
    }

    @Test fun datesOneToThirtyDays() {
        assertEquals(30, PlanRules.days(PlanDates.Range("2026-11-01", "2026-11-30")))
        assertNull(PlanRules.days(PlanDates.Range("2026-11-01", "2026-12-01")))
        assertNull(PlanRules.days(PlanDates.Range("2026-11-05", "2026-11-04")))
        assertNull(PlanRules.days(PlanDates.Range("2026-02-30", "2026-03-02")))
        assertNull(PlanRules.days(PlanDates.Range("26-11-01", "2026-11-02")))
        assertEquals(1, PlanRules.days(PlanDates.Days(1)))
        assertNull(PlanRules.days(PlanDates.Days(0)))
        assertNull(PlanRules.days(PlanDates.Days(31)))
        assertTrue(PlanProblem.Dates in PlanRules.check(ok.copy(dates = PlanDates.Days(31))).problems)
    }

    @Test fun noteLengthAndPersonalInfo() {
        assertTrue(PlanProblem.NoteTooLong in PlanRules.check(ok.copy(note = "가".repeat(201))).problems)
        assertTrue(PlanRules.check(ok.copy(note = "가".repeat(200))).problems.isEmpty())
        // 여권 번호처럼 보이면 막는다
        val passport = PlanRules.check(ok.copy(note = "제 여권 M12345678 이에요"))
        assertTrue(PlanProblem.NotePii in passport.problems)
        assertFalse(passport.ready(allowWarnings = true))
        // 전화번호는 경고만 — `이대로 보내기`로 넘길 수 있다
        val phone = PlanRules.check(ok.copy(note = "숙소 번호 010-1234-5678"))
        assertTrue(phone.problems.isEmpty())
        assertTrue(phone.warnOnly)
        assertFalse(phone.ready(allowWarnings = false))
        assertTrue(phone.ready(allowWarnings = true))
    }

    // ---------------- 7일 2회 ----------------

    private val now = Instant.parse("2026-10-09T03:00:00Z")
    private fun daysAgo(d: Long) = now.minus(Duration.ofDays(d))

    @Test fun remainingFollowsTheRule() {
        assertEquals(PlanRules.Remaining(2, null), PlanRules.remaining(null, now))
        assertEquals(PlanRules.Remaining(2, null), PlanRules.remaining(PlanQuota(null, null), now))
        assertEquals(1, PlanRules.remaining(PlanQuota(daysAgo(3), null), now).count)
        assertEquals(PlanRules.Remaining(0, daysAgo(5).plus(PlanRules.WINDOW)), PlanRules.remaining(PlanQuota(daysAgo(3), daysAgo(5)), now))
        // 규칙은 prev < 지금 − 7일 일 때만 받는다 — 딱 7일이면 아직 안 된다
        assertEquals(0, PlanRules.remaining(PlanQuota(daysAgo(1), daysAgo(7)), now).count)
        assertEquals(1, PlanRules.remaining(PlanQuota(daysAgo(1), daysAgo(8)), now).count)
        assertEquals(2, PlanRules.remaining(PlanQuota(daysAgo(8), daysAgo(9)), now).count)
    }

    // ---------------- 상태 ----------------

    @Test fun statusMappingAndActions() {
        assertEquals(PlanStatus.Queued, PlanStatus.of("queued"))
        assertEquals(PlanStatus.Processing, PlanStatus.of("processing"))
        assertEquals(PlanStatus.Done, PlanStatus.of("done"))
        assertEquals(PlanStatus.Failed, PlanStatus.of("failed"))
        assertEquals(PlanStatus.Cancelled, PlanStatus.of("cancelled"))
        assertEquals(PlanStatus.Unknown, PlanStatus.of("archived"))
        assertEquals(PlanStatus.Unknown, PlanStatus.of(null))
        assertEquals(PlanStatus.Unknown, PlanStatus.of(""))
        assertEquals(setOf(PlanStatus.Queued, PlanStatus.Processing), PlanStatus.entries.filter { it.cancellable }.toSet())
        assertEquals(setOf(PlanStatus.Cancelled), PlanStatus.entries.filter { it.deletable }.toSet())
        // 취소한 요청은 자동으로 지우지 않는다(사장님 결정) — 끝난 것만
        assertEquals(setOf(PlanStatus.Done, PlanStatus.Failed), PlanStatus.entries.filter { it.autoDeleted }.toSet())
        assertEquals(PlanFailure.QuotaExceeded, PlanFailure.of("quota_exceeded"))
        assertNull(PlanFailure.of("unknown_code"))
    }
}
