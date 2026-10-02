package com.readyport.data.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 챙길 일 알림 설정 저장 (2026-10-03): 기본값(켬·아침 9시), 여행별 조용히 두기, '오늘 알렸다' 기록과 오래된 기록 지우기.
 * 모두 기기 안 설정 DataStore에만 — 서버로 보내는 것은 없다.
 * 설정 DataStore는 한 번 만들면 테스트들이 함께 쓰므로(위임 속성이 들고 있다) 한 테스트에서 순서대로 본다.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class AlertSettingsStoreTest {

    private val settings = SettingsRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun alertSettingsRoundTrip() = runBlocking {
        // ① 기본값: 켬, 아침 9시, 꺼 둔 여행 없음
        val first = settings.current()
        assertTrue(first.alertsOn)
        assertEquals(9, first.alertHour)
        assertTrue(first.alertMutedTrips.isEmpty())
        assertTrue(first.alertLastNotified.isEmpty())

        // ② 켬·끔과 시각
        settings.setAlertHour(20)
        settings.setAlertsOn(false)
        settings.current().let {
            assertEquals(20, it.alertHour)
            assertFalse(it.alertsOn)
        }
        settings.setAlertsOn(true)
        settings.setAlertHour(9)

        // ③ 여행 하나만 조용히 두기 — 다른 여행은 그대로
        settings.setTripAlertMuted("trip-a", true)
        assertEquals(setOf("trip-a"), settings.current().alertMutedTrips)
        settings.setTripAlertMuted("trip-b", true)
        assertEquals(setOf("trip-a", "trip-b"), settings.current().alertMutedTrips)
        settings.setTripAlertMuted("trip-a", false)
        assertEquals(setOf("trip-b"), settings.current().alertMutedTrips)

        // ④ '오늘 알렸다' 기록: 여행마다 한 줄, 30일 지난 것은 지운다
        val today = LocalDate.of(2026, 10, 31)
        settings.markAlerted(listOf("trip-a"), today.minusDays(40))
        settings.markAlerted(listOf("trip-b"), today.minusDays(2))
        settings.markAlerted(listOf("trip-c"), today)
        val saved = settings.current().alertLastNotified
        assertEquals(setOf("trip-b", "trip-c"), saved.keys)
        assertEquals(today.toString(), saved["trip-c"])
        assertEquals(today.minusDays(2).toString(), saved["trip-b"])

        // 같은 여행을 다시 적으면 날짜만 바뀐다(두 줄이 되지 않는다)
        settings.markAlerted(listOf("trip-c"), today.plusDays(1))
        val again = settings.current().alertLastNotified
        assertEquals(today.plusDays(1).toString(), again["trip-c"])
        assertEquals(2, again.size)
    }
}
