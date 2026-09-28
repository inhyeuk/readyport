package com.readyport.trip

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * M6 완료 기준 "시간대 변경으로 도착 모드 전환": 실제 저장소(DataStore)에 여행을 두고,
 * 시간대 변경 처리 → 단계 계산까지 이어서 확인한다.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class ArrivalDetectionTest {

    private val trips = TripRepository(ApplicationProvider.getApplicationContext())

    private fun millis(local: String, zone: String) = LocalDateTime.parse(local).atZone(ZoneId.of(zone)).toInstant().toEpochMilli()

    @Test
    fun zoneChangeDuringTripSwitchesToArrivalMode() = runBlocking {
        trips.save(Trip("TH", "2026-11-03", "2026-11-07"))
        val landed = millis("2026-11-03T22:30", "Asia/Bangkok")

        // 한국 시간대로 바뀐 것은 무시
        assertFalse(onZoneChanged(trips, "Asia/Seoul", landed))
        // 방콕 시간대로 바뀌면 도착
        assertTrue(onZoneChanged(trips, "Asia/Bangkok", landed))
        val trip = trips.current()!!
        assertEquals(landed, trip.arrivedAt)

        val stage = TripStages.compute(trip, java.time.LocalDate.parse("2026-11-03"), landed + 10 * 60_000, 3)
        assertEquals(TripStage.Arrival, stage.stage)
        // 한 번 도착한 뒤 다시 바뀌어도 덮어쓰지 않는다
        assertFalse(onZoneChanged(trips, "Asia/Bangkok", landed + 3_600_000))
        trips.clear()
    }

    @Test
    fun zoneChangeOutsideTripIsIgnored() = runBlocking {
        trips.save(Trip("TH", "2026-11-03", "2026-11-07"))
        assertFalse(onZoneChanged(trips, "Asia/Bangkok", millis("2026-10-01T10:00", "Asia/Bangkok")))
        assertEquals(null, trips.current()!!.arrivedAt)
        trips.clear()
    }
}
