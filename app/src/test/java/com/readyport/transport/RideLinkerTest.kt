package com.readyport.transport

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.URLDecoder

/** M7 완료 기준: 미설치·설치·연결 실패 3경우 모두 동작 (ARCHITECTURE 9.7 3단계 연결) */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class RideLinkerTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val dest = Place("p1", "방콕 숙소", "123 ถนนสุขุมวิท กรุงเทพ")
    private val destWithCoords = dest.copy(lat = 13.7367, lng = 100.5609)
    private val copied = mutableListOf<String>()

    @Before
    fun strict() {
        // 받을 앱이 없으면 startActivity 가 실패하도록 (실제 기기처럼)
        shadowOf(app).checkActivities(true)
    }

    private fun install(pkg: String) {
        val pm = shadowOf(app.packageManager)
        val launcher = ComponentName(pkg, "$pkg.Main")
        pm.addActivityIfNotPresent(launcher)
        pm.addIntentFilterForActivity(launcher, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    private fun handleViewUrls(pkg: String, scheme: String) {
        val pm = shadowOf(app.packageManager)
        val c = ComponentName(pkg, "$pkg.Links")
        pm.addActivityIfNotPresent(c)
        pm.addIntentFilterForActivity(c, IntentFilter(Intent.ACTION_VIEW).apply { addCategory(Intent.CATEGORY_DEFAULT); addDataScheme(scheme) })
    }

    private fun handleMarket() {
        val pm = shadowOf(app.packageManager)
        val c = ComponentName("com.android.vending", "com.android.vending.Details")
        pm.addActivityIfNotPresent(c)
        pm.addIntentFilterForActivity(c, IntentFilter(Intent.ACTION_VIEW).apply { addCategory(Intent.CATEGORY_DEFAULT); addDataScheme("market") })
    }

    @Test
    fun planOrder() {
        assertEquals(listOf(Attempt.PlayStore("x")), RideLinker.plan("x", LinkType.MapsUrl, installed = false, dest = dest))
        val maps = RideLinker.plan("m", LinkType.MapsUrl, installed = true, dest = dest)
        assertTrue(maps[0] is Attempt.OpenUri && maps[1] is Attempt.LaunchApp && maps[2] is Attempt.PlayStore)
        assertEquals(listOf(Attempt.LaunchApp("g", dest.addressLocal), Attempt.PlayStore("g")), RideLinker.plan("g", LinkType.OpenAndCopy, true, dest))
        // Uber 딥링크는 좌표가 있어야 — 없으면 2단계부터
        assertTrue(RideLinker.plan("u", LinkType.UberUrl, true, dest)[0] is Attempt.LaunchApp)
        assertTrue(RideLinker.plan("u", LinkType.UberUrl, true, destWithCoords)[0] is Attempt.OpenUri)
    }

    @Test
    fun mapsUrlCarriesLocalAddress() {
        val url = RideLinker.mapsUrl(dest)
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1&destination="))
        assertTrue(url.endsWith("&travelmode=transit"))
        val d = Uri.parse(url).getQueryParameter("destination")
        assertEquals(dest.addressLocal, d)
        assertEquals("13.7367,100.5609", Uri.parse(RideLinker.mapsUrl(destWithCoords)).getQueryParameter("destination"))
        val uber = RideLinker.uberUrl(destWithCoords)!!
        assertTrue(uber.startsWith("uber://riderequest?pickup=my_location"))
        assertTrue(URLDecoder.decode(uber, "UTF-8").contains("dropoff[latitude]=13.7367"))
    }

    @Test
    fun case1_notInstalled_goesToPlayStore() {
        handleMarket()
        assertEquals(false, RideLinker.isInstalled(app, "com.example.ride"))
        val plan = RideLinker.plan("com.example.ride", LinkType.OpenAndCopy, installed = false, dest = dest)
        val done = RideLinker.execute(app, plan) { copied += it }
        assertEquals(Attempt.PlayStore("com.example.ride"), done)
        val started = shadowOf(app).nextStartedActivity
        assertEquals("market://details?id=com.example.ride", started.data.toString())
    }

    @Test
    fun case2_installed_opensWithDestination() {
        install("com.google.android.apps.maps")
        handleViewUrls("com.google.android.apps.maps", "https")
        assertTrue(RideLinker.isInstalled(app, "com.google.android.apps.maps"))
        val plan = RideLinker.plan("com.google.android.apps.maps", LinkType.MapsUrl, installed = true, dest = dest)
        val done = RideLinker.execute(app, plan) { copied += it }
        assertTrue(done is Attempt.OpenUri)
        val started = shadowOf(app).nextStartedActivity
        assertEquals("com.google.android.apps.maps", started.`package`)
        assertEquals(dest.addressLocal, started.data!!.getQueryParameter("destination"))
        assertTrue(copied.isEmpty())
    }

    @Test
    fun case3_linkFails_fallsBackToOpenAndCopy() {
        // 설치는 됐지만 목적지 주소 열기를 못 받는 앱 → 2단계: 앱 열고 주소 복사
        install("com.example.ride")
        val plan = RideLinker.plan("com.example.ride", LinkType.MapsUrl, installed = true, dest = dest)
        val done = RideLinker.execute(app, plan) { copied += it }
        assertTrue(done is Attempt.LaunchApp)
        assertEquals(listOf(dest.addressLocal), copied)
        assertEquals("com.example.ride", shadowOf(app).nextStartedActivity.component!!.packageName)
    }
}
