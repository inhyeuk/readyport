package com.readyport.trip

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 입국 카드 알림 작업은 **여행마다 따로** (2026-10-03 고침): 예전에는 작업 이름이 `form-window` 하나라
 * 두 번째 여행을 저장하면 첫 여행 알림이 사라졌다. 이름·태그가 여행·양식마다 다른지, 여행 하나만 지울 수 있는지 본다.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class ChecklistAlertWorkTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val wm: WorkManager get() = WorkManager.getInstance(context)
    private val opens: LocalDate = LocalDate.now().plusDays(30)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun states(tripId: String, formId: String): List<WorkInfo.State> =
        wm.getWorkInfosForUniqueWork(TripNotifications.formWorkName(tripId, formId)).get().map { it.state }

    @Test
    fun formWindowWorkNameIsPerTripAndPerForm() {
        val a = TripNotifications.formWorkName("trip-a", "TH_TDAC")
        val b = TripNotifications.formWorkName("trip-b", "TH_TDAC")
        val c = TripNotifications.formWorkName("trip-a", "SG_SGAC")
        assertNotEquals(a, b)
        assertNotEquals(a, c)
        assertTrue(a.contains("trip-a") && a.contains("TH_TDAC"))
    }

    @Test
    fun twoTripsKeepBothFormRemindersAndCancelOneByOne() {
        TripNotifications.scheduleFormWindow(context, "trip-a", "TH_TDAC", opens, 9)
        TripNotifications.scheduleFormWindow(context, "trip-b", "SG_SGAC", opens.plusDays(10), 9)
        assertEquals(listOf(WorkInfo.State.ENQUEUED), states("trip-a", "TH_TDAC"))
        assertEquals(listOf(WorkInfo.State.ENQUEUED), states("trip-b", "SG_SGAC"))

        // 여행 하나만 지운다 → 다른 여행 알림은 그대로 (예전에는 작업 이름이 하나라 서로를 밀어냈다)
        TripNotifications.cancelFormWindow(context, "trip-a")
        assertEquals(listOf(WorkInfo.State.CANCELLED), states("trip-a", "TH_TDAC"))
        assertEquals(listOf(WorkInfo.State.ENQUEUED), states("trip-b", "SG_SGAC"))

        // 다시 맞출 때는 모두 지운다
        TripNotifications.cancelFormWindows(context)
        assertEquals(listOf(WorkInfo.State.CANCELLED), states("trip-b", "SG_SGAC"))
    }

    /** 알림 번호도 여행마다 달라야 서로의 알림을 지우지 않는다 */
    @Test
    fun notificationIdIsPerTrip() {
        val ids = List(50) { "trip-$it" }.map { TripNotifications.notificationId(it) }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.toString(), ids.all { it >= 1_000 })
        assertEquals(TripNotifications.notificationId("trip-7"), TripNotifications.notificationId("trip-7"))
    }
}
