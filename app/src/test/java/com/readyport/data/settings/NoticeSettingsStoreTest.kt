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
 * 공지·소식 설정 (docs/NOTICES_PUSH.md): 공지 알림 기본 켬, 광고성 소식 기본 끔 + 켜고 끈 날(이 휴대폰에만),
 * 밤 광고 알림은 광고성 소식이 켜져 있을 때만 켤 수 있고, 광고를 끄면 함께 꺼진다.
 * 설정 DataStore는 테스트들이 함께 쓰므로 값을 직접 정해 놓고 본다(순서와 무관하게).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class NoticeSettingsStoreTest {

    private val settings = SettingsRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun promoConsentRoundTrip() = runBlocking {
        val day = LocalDate.of(2026, 10, 8)
        settings.setNoticePush(true)
        settings.setPromoPush(false, day)
        settings.setPromoNight(false, day)
        settings.current().let {
            assertTrue(it.noticePush)
            assertFalse(it.promoPush)
            assertFalse(it.promoNight)
        }

        // 광고가 꺼져 있으면 밤 광고는 켜지지 않는다
        settings.setPromoNight(true, day)
        assertFalse(settings.current().promoNight)

        // 켜면 그 날을 적는다
        settings.setPromoPush(true, day)
        settings.setPromoNight(true, day.plusDays(1))
        settings.current().let {
            assertTrue(it.promoPush)
            assertEquals("2026-10-08", it.promoDate)
            assertTrue(it.promoNight)
            assertEquals("2026-10-09", it.promoNightDate)
        }

        // 광고를 끄면 밤 광고도 함께 꺼지고, 끈 날이 남는다
        settings.setPromoPush(false, day.plusDays(2))
        settings.current().let {
            assertFalse(it.promoPush)
            assertFalse(it.promoNight)
            assertEquals("2026-10-10", it.promoDate)
            assertEquals("2026-10-10", it.promoNightDate)
        }

        settings.setNoticePush(false)
        assertFalse(settings.current().noticePush)
        settings.setNoticePush(true)
    }
}
