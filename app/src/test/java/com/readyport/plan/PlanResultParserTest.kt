package com.readyport.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan_results 문서 → 화면 모델: 관대하게 읽는다(모르는 칸 무시, 틀린 항목 빼기, 긴 글 자르기). 가짜 계획만 */
class PlanResultParserTest {
    private fun item(title: Any?, hint: Any? = "morning", place: Any? = null, note: Any? = "쉬엄쉬엄 걸어요") =
        mapOf("time_hint" to hint, "place_id" to place, "title" to title, "note" to note)

    private val doc: Map<String, Any?> = mapOf(
        "uid" to "u1",
        "request_id" to "req1",
        "country" to "JP",
        "ai_generated" to true,
        "notice_ko" to "AI가 만든 여행 계획이에요.",
        "engine" to "claude_code_headless",
        "unknown_top" to 1,
        "plan" to mapOf(
            "days" to listOf(
                mapOf("day" to 1, "title" to "도착하고 쉬기", "items" to listOf(item("숙소 근처 산책", hint = "evening"))),
                mapOf("day" to 2, "title" to "절 구경", "items" to listOf(item("센소지 둘러보기", place = "sensoji"), item("점심 먹기", hint = "lunch", place = null, note = null))),
            ),
            "tips" to listOf("교통카드를 미리 충전해요", 3, ""),
            "budget_notes" to listOf("보통 식당 위주예요"),
            "caveats" to listOf("출발 전에 공식 안내(영업·휴무·예약)를 꼭 확인하세요."),
            "extra" to "무시",
        ),
    )

    @Test fun parsesTheAriaShape() {
        val r = PlanResultParser.parse("req1", doc)!!
        assertEquals("req1", r.requestId)
        assertEquals("JP", r.country)
        assertEquals(2, r.days.size)
        assertEquals(PlanItem(TimeHint.Morning, "sensoji", "센소지 둘러보기", "쉬엄쉬엄 걸어요"), r.days[1].items[0])
        assertEquals(PlanItem(TimeHint.Lunch, null, "점심 먹기", ""), r.days[1].items[1])
        // 글이 아닌 것·빈 글은 뺀다
        assertEquals(listOf("교통카드를 미리 충전해요"), r.tips)
        assertEquals("AI가 만든 여행 계획이에요.", r.noticeKo)
    }

    @Test fun lenientWithBrokenItemsAndUnknownHints() {
        val broken = doc + ("plan" to mapOf(
            "days" to listOf(
                "하루가 아님",
                mapOf("title" to "빈 날", "items" to listOf(item(null), "x")),
                mapOf("title" to 7, "items" to listOf(item("밤 시장", hint = "midnight", place = "Bad Id!"))),
            ),
        ))
        val r = PlanResultParser.parse("req9", broken)!!
        // 쓸 수 있는 날만, 1일째부터 다시 센다
        assertEquals(1, r.days.size)
        assertEquals(1, r.days[0].day)
        assertEquals("", r.days[0].title)
        assertNull(r.days[0].items[0].timeHint)
        assertNull(r.days[0].items[0].placeId)
        assertTrue(r.tips.isEmpty() && r.caveats.isEmpty())
    }

    @Test fun longTextIsCutAndControlCharsRemoved() {
        val long = "가".repeat(1000)
        val r = PlanResultParser.parse("x", doc + ("plan" to mapOf("days" to listOf(mapOf("items" to listOf(item("제목\u0007", note = long)))))))!!
        assertEquals("제목", r.days[0].items[0].title)
        assertEquals(PlanResultParser.TEXT_MAX, r.days[0].items[0].note.length)
        assertTrue(r.days[0].items[0].note.endsWith("…"))
    }

    @Test fun nothingToShowIsNull() {
        assertNull(PlanResultParser.parse("x", null))
        assertNull(PlanResultParser.parse("x", mapOf("plan" to "글")))
        assertNull(PlanResultParser.parse("x", mapOf("plan" to mapOf("days" to emptyList<Any>()))))
    }

    @Test fun requestIdFallsBackToDocumentId() {
        assertEquals("doc7", PlanResultParser.parse("doc7", doc - "request_id")!!.requestId)
    }
}
