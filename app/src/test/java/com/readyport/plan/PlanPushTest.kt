package com.readyport.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlanPushTest {
    @Test
    fun topicUsesPlanPrefixAndSafeIdOnly() {
        assertEquals("plan_AbC123xyz", PlanPush.topic("AbC123xyz"))
        assertThrows(IllegalArgumentException::class.java) { PlanPush.topic("bad id!") }
        assertThrows(IllegalArgumentException::class.java) { PlanPush.topic("a/b") }
    }

    @Test
    fun requestIdOnlyFromPlanTypeWithSafeId() {
        assertEquals("qiygtJxfxwUh2yhdBcS7", PlanPush.requestId(mapOf("type" to "plan", "request" to "qiygtJxfxwUh2yhdBcS7")))
        assertNull(PlanPush.requestId(mapOf("type" to "notice", "request" to "qiygtJxfxwUh2yhdBcS7")))
        assertNull(PlanPush.requestId(mapOf("type" to "plan")))
        assertNull(PlanPush.requestId(mapOf("type" to "plan", "request" to "../etc")))
        assertNull(PlanPush.requestId(mapOf("type" to "plan", "request" to "short")))
    }
}
