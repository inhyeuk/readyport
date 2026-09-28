package com.readyport

import org.junit.Assert.assertEquals
import org.junit.Test

// M0: 단위 테스트 파이프라인이 도는지만 확인한다. M2부터 MRZ 체크디지트 등 실제 테스트로 대체.
class SmokeTest {
    @Test
    fun pipelineRuns() {
        assertEquals(4, 2 + 2)
    }
}
