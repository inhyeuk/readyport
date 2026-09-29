package com.readyport.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.pack.EssentialRule
import com.readyport.prep.Essentials
import com.readyport.transport.Place
import com.readyport.trip.StageInfo
import com.readyport.trip.Trip
import com.readyport.trip.TripStage
import com.readyport.ui.onboarding.FirstRunScreen
import com.readyport.ui.country.CountryActions
import com.readyport.ui.country.CountryContent
import com.readyport.ui.country.CountrySection
import com.readyport.ui.home.HomeActions
import com.readyport.ui.home.HomeContent
import com.readyport.ui.settings.PhotoCreditsContent
import com.readyport.ui.settings.SettingsScreen
import com.readyport.ui.components.PhotoCredit
import com.readyport.ui.pack.HelpContent
import com.readyport.ui.pack.ShoppingContent
import com.readyport.ui.pack.ShoppingUi
import com.readyport.ui.prep.EssentialRow
import com.readyport.ui.prep.EssentialsContent
import com.readyport.ui.prep.EssentialsUi
import com.readyport.ui.present.PresentContent
import com.readyport.ui.present.PresentUi
import com.readyport.ui.tabs.PrepareContent
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.today.TodayActions
import com.readyport.ui.today.TodayContent
import com.readyport.ui.today.TodayUi
import com.readyport.ui.transport.RideAppRow
import com.readyport.ui.transport.TransportContent
import com.readyport.ui.transport.TransportUi
import com.readyport.ui.trip.TripContent
import com.readyport.ui.trip.TripFormUi
import com.readyport.ui.wallet.WalletContent
import com.readyport.vault.WalletState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * M10 접근성 점검: 누를 수 있는 모든 요소가
 *  ① 터치 영역 48dp 이상 (PRD 3.2 — 쉬운 모드는 56dp 이상은 PrimaryButton 테스트에서)
 *  ② TalkBack이 읽을 이름(글자 또는 설명)이 있다.
 * 화면을 아주 길게 잡아(h8000dp) 목록 항목이 모두 그려지게 한다.
 */
abstract class A11yAuditBase {

    @get:Rule
    val rule = createComposeRule()

    private val index get() = TestPacks.index.value
    private val th get() = TestPacks.thailand
    private val trip = Trip("TH", "2026-11-03", "2026-11-07")

    private fun screens(): List<Pair<String, @Composable () -> Unit>> = listOf(
        "first-run" to { FirstRunScreen {} },
        "today-none" to { TodayContent(TodayUi(), TodayActions(), {}, {}, {}, {}, {}) },
        "today-preparing" to { TodayContent(TodayUi(trip, StageInfo(TripStage.Preparing, daysLeft = 3, formWindowOpen = true), "태국", th.value.forms.first(), true), TodayActions(), {}, {}, {}, {}, {}) },
        "today-arrival" to { TodayContent(TodayUi(trip, StageInfo(TripStage.Arrival, dayOfTrip = 1), "태국", th.value.forms.first(), true), TodayActions(), {}, {}, {}, {}, {}) },
        "today-return" to {
            TodayContent(
                TodayUi(trip, StageInfo(TripStage.Return, askDestroy = true), "태국", null, true,
                    cart = th.value.shopping, returnLinks = index.returnLinks, returnFacts = index.returnFacts),
                TodayActions(), {}, {}, {}, {}, {},
            )
        },
        "prepare" to { PrepareContent(TestPacks.formEntries(), {}) },
        "essentials" to {
            val rules: List<EssentialRule> = Essentials.select(index.essentials, index.homePower, th.value.power)
            EssentialsContent(EssentialsUi("태국", 4, 11, rules.map { EssentialRow(it, false, "출처") }), { _, _ -> }, {})
        },
        "home" to { HomeContent(TestPacks.homeUi(), HomeActions()) },
        "country-entry" to { CountryContent(TestPacks.countryUi("ID"), CountryActions()) },
        "country-travel" to { CountryContent(TestPacks.countryUi("TH", favorite = true), CountryActions(), CountrySection.Travel) },
        "country-shopping" to { CountryContent(TestPacks.countryUi("JP"), CountryActions(), CountrySection.Shopping) },
        "settings" to { SettingsScreen(easyMode = true, onEasyModeChange = {}) },
        "videos" to {
            androidx.compose.runtime.CompositionLocalProvider(com.readyport.ui.video.LocalThumbnailLoader provides { null }) {
                com.readyport.ui.video.VideosContent("태국", com.readyport.ui.video.VideosState.Ready(listOf(
            com.readyport.video.Video(id = "AAAAAAAAAA1", title = "방콕 3박 4일 여행 브이로그 | 왓아룬 야경", channelTitle = "여행채널", publishedAt = "2026-09-01T00:00:00Z", viewCount = 1234567, subscriberCount = 89000, durationSeconds = 754, thumbnail = "https://i.ytimg.com/vi/AAAAAAAAAA1/mqdefault.jpg"),
            com.readyport.video.Video(id = "AAAAAAAAAA2", title = "태국 여행 준비물 총정리", channelTitle = "채널2", publishedAt = "2026-08-11T00:00:00Z", viewCount = 45210, subscriberCount = null, durationSeconds = 3725, thumbnail = "https://i.ytimg.com/vi/AAAAAAAAAA2/mqdefault.jpg")
                )), {})
            }
        },
        "videos-offline" to { com.readyport.ui.video.VideosContent("태국", com.readyport.ui.video.VideosState.Unavailable, {}) },
        "photo-credits" to { PhotoCreditsContent(listOf(PhotoCredit("th", "Wat Arun Sunset.jpg", "x", "CC BY 2.0", sourceUrl = "https://commons.wikimedia.org/")), {}) },
        "shopping" to { ShoppingContent(ShoppingUi("TH", "태국", th.value.shopping, returnLinks = index.returnLinks, returnFacts = index.returnFacts), { _, _ -> }, {}) },
        "help" to { HelpContent(TestPacks.helpUi(), {}, {}, {}) },
        "wallet-locked" to {
            WalletContent(
                state = WalletState.Locked(hasData = true), deviceSecure = true, autoDestroy = true, today = LocalDate.of(2026, 9, 29),
                onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
            )
        },
        "present" to { PresentContent(PresentUi(locked = true), {}, {}, {}, {}) },
        "trip" to { TripContent(TripFormUi(index.countries.filter { it.pack }, trip, loaded = true), { _, _, _ -> }, {}) },
        "transport" to {
            val place = Place("p1", "호텔", "123 ถนนสุขุมวิท กรุงเทพฯ")
            TransportContent(
                TransportUi(listOf(place), place, th.value.transportApps.map { RideAppRow(it, installed = false) }, false, "กรุณาพาไปที่นี่"),
                null, { _, _ -> }, {}, {}, {},
            )
        },
    )

    private fun label(n: SemanticsNode): String? {
        val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val desc = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        return listOfNotNull(text, desc).joinToString(" ").takeIf { it.isNotBlank() }
    }

    protected fun audit(easyMode: Boolean) {
        val problems = mutableListOf<String>()
        var audited = 0
        var current by androidx.compose.runtime.mutableStateOf(0)
        val list = screens()
        rule.setContent {
            ReadyPortTheme(easyMode = easyMode) { list[current].second() }
        }
        list.forEachIndexed { i, (name, _) ->
            rule.runOnIdle { current = i }
            rule.waitForIdle()
            val density = rule.density.density
            val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            audited += nodes.size
            nodes.forEach { n ->
                val b = n.touchBoundsInRoot
                val minPx = 48 * density - 1
                val lbl = label(n)
                if (b.width < minPx || b.height < minPx) {
                    problems += "$name: 터치 영역 ${(b.width / density).toInt()}x${(b.height / density).toInt()}dp < 48dp ($lbl)"
                }
                if (lbl == null) problems += "$name: 이름 없는 버튼 (${b})"
            }
        }
        println("A11Y audited=$audited")
        assertTrue("점검한 버튼이 너무 적음: $audited", audited > 60)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

private operator fun <T> androidx.compose.runtime.MutableState<T>.getValue(thisObj: Any?, p: kotlin.reflect.KProperty<*>) = value
private operator fun <T> androidx.compose.runtime.MutableState<T>.setValue(thisObj: Any?, p: kotlin.reflect.KProperty<*>, v: T) { value = v }

@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h8000dp")
class A11yAuditTest : A11yAuditBase() {
    @Test fun normalMode() = audit(easyMode = false)
    @Test fun easyMode() = audit(easyMode = true)
}

/** 글자 크기 200% (시스템 설정 최대)에서도 모든 화면이 그려지고 같은 규칙을 지킨다 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h8000dp", fontScale = 2.0f)
class A11yAuditLargeFontTest : A11yAuditBase() {
    @Test fun easyModeLargeFont() = audit(easyMode = true)
}
