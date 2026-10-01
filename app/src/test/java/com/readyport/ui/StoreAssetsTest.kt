package com.readyport.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.readyport.ui.components.Photos
import com.readyport.ui.country.CountryActions
import com.readyport.ui.country.CountryContent
import com.readyport.ui.country.CountrySection
import com.readyport.ui.home.HomeActions
import com.readyport.ui.home.HomeContent
import com.readyport.ui.home.HomeTrip
import com.readyport.ui.settings.SettingsScreen
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.autofill.FormValues
import com.readyport.prep.Essentials
import com.readyport.transport.Place
import com.readyport.trip.StageInfo
import com.readyport.trip.Trip
import com.readyport.trip.TripStage
import com.readyport.ui.form.ConfirmUi
import com.readyport.ui.form.FormConfirmContent
import com.readyport.ui.form.FormContext
import com.readyport.ui.pack.HelpContent
import com.readyport.ui.prep.EssentialRow
import com.readyport.ui.prep.EssentialsContent
import com.readyport.ui.prep.EssentialsUi
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import com.readyport.ui.today.TodayActions
import com.readyport.ui.today.TodayContent
import com.readyport.ui.today.TodayUi
import com.readyport.ui.transport.RideAppRow
import com.readyport.ui.transport.TransportContent
import com.readyport.ui.transport.TransportUi
import com.readyport.ui.wallet.WalletContent
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.VaultContents
import com.readyport.vault.WalletState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Play 스토어 등록 이미지 (M10). **가짜 값만** 쓴다 — 여권은 ICAO 표본(ERIKSSON ANNA MARIA).
 * 결과: app/build/store/ (커밋은 docs/play/store/ 로 복사)
 * - 스크린숏: 1215×2160 = 정확히 9:16 (Play 콘솔 규칙: 16:9 또는 9:16, 320~3840px)
 */
private fun Bitmap.saveTo(name: String): File {
    val dir = File("build/store").apply { mkdirs() }
    return File(dir, name).also { f -> f.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) } }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w405dp-h720dp-xxhdpi")
class StoreScreenshotsTest {

    @get:Rule
    val rule = createComposeRule()

    private val th get() = TestPacks.thailand
    private val index get() = TestPacks.index.value
    private val trip = Trip("TH", "2026-11-03", "2026-11-07")
    private val passport = PassportRecord(
        surname = "ERIKSSON", givenNames = "ANNA MARIA", documentNumber = "L898902C3",
        nationality = "KOR", issuingState = "KOR", birthDate = "1974-08-12", sex = "F",
        expiryDate = "2031-04-15", source = "mrz", mrzVerified = true, savedAt = "2026-09-29T10:00",
    )
    private val contents = VaultContents(
        passport = passport,
        bookings = listOf(
            BookingRecord(id = "1", kind = "flight", title = "방콕 왕복", flightNumbers = listOf("KE651", "KE652"),
                dates = listOf("2026-11-03", "2026-11-07"), savedAt = "x"),
            BookingRecord(id = "2", kind = "lodging", title = "방콕 숙소", reference = "0000-0000",
                checkIn = "2026-11-03", checkOut = "2026-11-07", savedAt = "x"),
        ),
    )

    private fun screens(): List<Pair<String, @Composable () -> Unit>> = listOf(
        "01_home" to {
            HomeContent(TestPacks.homeUi().copy(trip = HomeTrip("태국", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 7), code = "TH")),
                HomeActions(), today = LocalDate.of(2026, 10, 31))
        },
        "02_country" to { CountryContent(TestPacks.countryUi("TH"), CountryActions()) },
        "03_form_confirm" to {
            val recipe = TestPacks.tdacRecipe
            val draft = mapOf("trip.purpose" to "tourism", "profile.country_res" to "대한민국")
            val ctx = FormContext("TH_TDAC", th.value.forms.first(), recipe.value, recipe.version, false)
            FormConfirmContent(ConfirmUi(ctx, WalletState.Unlocked(contents), FormValues.build(recipe.value, contents, draft), draft),
                { _, _ -> }, {}, {}, {}, {})
        },
        "04_country_shopping" to { CountryContent(TestPacks.countryUi("JP"), CountryActions(), CountrySection.Shopping) },
        "05_settings" to { SettingsScreen(easyMode = false, onEasyModeChange = {}) },
        "06_help" to { HelpContent(TestPacks.helpUi(), {}, {}, {}) },
    )

    @Test fun phoneScreenshots() {
        val list = screens()
        var current by androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent {
            ReadyPortTheme(easyMode = false) {
                Box(Modifier.fillMaxSize().background(Tokens.Ground)) { list[current].second() }
            }
        }
        list.forEachIndexed { i, (name, _) ->
            rule.runOnIdle { current = i }
            rule.mainClock.advanceTimeBy(2_000)
            rule.waitForIdle()
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            val long = maxOf(bmp.width, bmp.height).toDouble()
            val short = minOf(bmp.width, bmp.height).toDouble()
            assertTrue("Play 비율 규칙(9:16) 위반: ${bmp.width}x${bmp.height}", bmp.width * 16 == bmp.height * 9 && short >= 320 && long <= 3840)
            bmp.saveTo("screenshot_$name.png")
        }
    }
}

/** 그래픽 이미지 1024×500 (Play 필수). 정부 연상 요소 없음, 비제휴 문구 포함 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w1024dp-h500dp-mdpi")
class StoreFeatureGraphicTest {

    @get:Rule
    val rule = createComposeRule()

    @Test fun featureGraphic() {
        val icon = BitmapFactory.decodeFile(File("../design/icons/play-store/readyport_play_512.png").path)!!.asImageBitmap()
        rule.setContent {
            Image(painterResource(Photos.Home), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Tokens.Navy.copy(alpha = 0.72f)).padding(horizontal = 72.dp), contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(200.dp).clip(RoundedCornerShape(44.dp)))
                    Spacer(Modifier.width(56.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("레디포트", color = Tokens.Surface, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                        Text("입국 서류, 한국어로 확인하고\n공식 사이트 입력은 쉽게", color = Tokens.Surface, fontSize = 32.sp, lineHeight = 42.sp)
                        Text("여권 정보는 내 폰 안에만 · 인터넷 없이도 열려요", color = Tokens.AccentSoft, fontSize = 20.sp)
                        Text("정부 기관과 제휴하지 않은 민간 앱입니다", color = Tokens.AccentSoft, fontSize = 16.sp)
                    }
                }
            }
        }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("${bmp.width}x${bmp.height}", bmp.width == 1024 && bmp.height == 500)
        bmp.saveTo("feature_graphic_1024x500.png")
    }
}
