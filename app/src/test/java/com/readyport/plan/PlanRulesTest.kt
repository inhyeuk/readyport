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

    @Test fun quotaPayloadCountsPerCountryAndKeepsExtra() {
        val first = PlanRules.quotaPayload(null, "JP", "req1", server)
        assertEquals(setOf("last", "lastRequestId", "counts"), first.keys)
        assertSame(server, first["last"])
        assertEquals("req1", first["lastRequestId"])
        assertEquals(mapOf("JP" to 1), first["counts"])
        val prev = PlanQuota(null, null, counts = mapOf("JP" to 1, "TH" to 2), extra = mapOf("TH" to 1))
        val next = PlanRules.quotaPayload(prev, "JP", "req2", server)
        assertEquals(mapOf("JP" to 2, "TH" to 2), next["counts"])
        assertEquals(mapOf("TH" to 1), next["extra"])
        assertEquals(setOf("last", "lastRequestId", "counts", "extra"), next.keys)
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

    // ---------------- 나라별 2번 ----------------

    private val now = Instant.parse("2026-10-09T03:00:00Z")
    private fun daysAgo(d: Long) = now.minus(Duration.ofDays(d))

    @Test fun remainingFollowsTheRule() {
        assertEquals(PlanRules.Remaining(2, null), PlanRules.remaining(null, "JP"))
        assertEquals(PlanRules.Remaining(2, null), PlanRules.remaining(PlanQuota(null, null), "JP"))
        val q = PlanQuota(daysAgo(3), null, counts = mapOf("JP" to 1, "TH" to 2))
        assertEquals(1, PlanRules.remaining(q, "JP").count)
        assertEquals(0, PlanRules.remaining(q, "TH").count)
        assertEquals(2, PlanRules.remaining(q, "VN").count)
        // 기간 제한 없음: 오래된 기록이어도 누적 그대로. 예전(7일 2회) 기록(prev 만 있고 counts 없음)은 나라별로 새로 센다
        assertEquals(0, PlanRules.remaining(PlanQuota(daysAgo(400), null, counts = mapOf("JP" to 2)), "JP").count)
        assertEquals(2, PlanRules.remaining(PlanQuota(daysAgo(1), daysAgo(2)), "JP").count)
        // 서버가 더해 준 추가 횟수
        assertEquals(1, PlanRules.remaining(PlanQuota(null, null, counts = mapOf("JP" to 2), extra = mapOf("JP" to 1)), "JP").count)
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
        assertEquals(setOf(PlanStatus.Cancelled, PlanStatus.Done, PlanStatus.Failed), PlanStatus.entries.filter { it.deletable }.toSet())
        // 취소한 요청은 자동으로 지우지 않는다(사장님 결정) — 끝난 것만
        assertEquals(setOf(PlanStatus.Done, PlanStatus.Failed), PlanStatus.entries.filter { it.autoDeleted }.toSet())
        assertEquals(PlanFailure.QuotaExceeded, PlanFailure.of("quota_exceeded"))
        assertNull(PlanFailure.of("unknown_code"))
    }

    // ---------------- AI 계획 신고 (plan_flags) ----------------

    @Test fun flagPayloadMatchesRules() {
        assertEquals(mapOf("uid" to "u1", "reason" to "inaccurate", "at" to server), PlanRules.flagPayload(PlanFlagReason.Inaccurate, "   ", "u1", server))
        val withNote = PlanRules.flagPayload(PlanFlagReason.Unsafe, " 밤길 안내가 걱정돼요 ", "u1", server)
        assertEquals("밤길 안내가 걱정돼요", withNote["note"])
        assertEquals("unsafe", withNote["reason"])
        // 200자까지 (다듬은 뒤 길이)
        assertEquals(200, (PlanRules.flagPayload(PlanFlagReason.Other, "가".repeat(200) + "  ", "u1", server)["note"] as String).length)
        assertEquals(listOf("inaccurate", "inappropriate", "unsafe", "other"), PlanFlagReason.entries.map { it.id })
        assertEquals(PlanFlagReason.Unsafe, PlanFlagReason.of("unsafe"))
        assertNull(PlanFlagReason.of("spam"))
    }

    @Test fun flagNoteBlocksPassportLikeTextAndLongNotesButOnlyWarnsForContacts() {
        assertFalse(PlanRules.checkFlagNote("").blocked)
        assertTrue(PlanRules.checkFlagNote("가".repeat(201)).tooLong)
        // 가짜 값: 여권 번호 모양 · 주민번호 모양 · 여권 아래 두 줄 모양
        for (bad in listOf("M00000000", "번호 900101-1000000", "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<")) {
            val c = PlanRules.checkFlagNote(bad)
            assertTrue(bad, c.blocked)
            try {
                PlanRules.flagPayload(PlanFlagReason.Other, bad, "u1", server)
                fail("막혀야 한다: $bad")
            } catch (e: PlanError.Invalid) {
                // 기대한 대로
            }
        }
        // 전화·이메일은 경고만(공개된 번호일 수 있다) — 보낼 수는 있다
        val warn = PlanRules.checkFlagNote("대사관 02-0000-0000 이 맞나요")
        assertTrue(warn.pii.isNotEmpty())
        assertFalse(warn.blocked)
    }
}
