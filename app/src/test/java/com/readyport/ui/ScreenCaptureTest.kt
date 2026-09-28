package com.readyport.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.data.settings.AppSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 디자인 확인용 화면 캡처. 검증(assert)은 하지 않고 build/screenshots/에 PNG를 남긴다.
 * 기기 없이 쉬운 모드·글자 확대 모습을 눈으로 확인하는 용도.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
class ScreenCaptureTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun capture(name: String) {
        // 누름 효과(ripple)가 끝난 뒤 찍는다
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun openTab(label: Int) {
        rule.onNode(
            hasText(context.getString(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).performClick()
    }

    private fun captureAll(prefix: String, settings: AppSettings) {
        rule.setContent { ReadyPortRoot(settings = settings, onSetEasyMode = {}, onSpeak = {}, slots = FakeSlots) }
        capture("${prefix}_1_today")
        openTab(R.string.tab_prepare); capture("${prefix}_2_prepare")
        openTab(R.string.tab_explore); capture("${prefix}_3_explore")
        openTab(R.string.tab_wallet); capture("${prefix}_4_wallet")
        openTab(R.string.tab_help); capture("${prefix}_5_help")
    }

    @Test fun basicMode() = captureAll("basic", AppSettings(easyMode = false))

    @Test fun easyMode() = captureAll("easy", AppSettings(easyMode = true))

    @Test fun easyModeDoubleFont() {
        RuntimeEnvironment.setFontScale(2.0f)
        captureAll("easy_font200", AppSettings(easyMode = true))
    }

    @Test fun firstRun() {
        rule.setContent { ReadyPortRoot(settings = AppSettings(easyMode = null), onSetEasyMode = {}, onSpeak = {}, slots = FakeSlots) }
        capture("first_run")
    }

    @Test fun guideAndOffline() {
        rule.setContent {
            ReadyPortRoot(
                settings = AppSettings(easyMode = true), onSetEasyMode = {}, onSpeak = {},
                online = false, slots = FakeSlots,
            )
        }
        capture("m3_1_today_offline")
        openTab(R.string.tab_explore); capture("m3_2_explore")
        rule.onNodeWithText(context.getString(R.string.explore_open_guide)).performClick(); capture("m3_3_guide")
        openTab(R.string.tab_help); capture("m3_4_help")
    }

    @Test fun formConfirm() {
        val recipe = TestPacks.tdacRecipe
        val contents = com.readyport.vault.VaultContents(
            passport = com.readyport.vault.PassportRecord(
                surname = "ERIKSSON", givenNames = "ANNA MARIA", documentNumber = "L898902C3",
                nationality = "KOR", issuingState = "KOR", birthDate = "1974-08-12", sex = "F",
                expiryDate = "2036-04-15", source = "mrz", mrzVerified = true, savedAt = "x",
            ),
            bookings = listOf(
                com.readyport.vault.BookingRecord(id = "1", kind = "flight", title = "t", flightNumbers = listOf("KE651", "KE652"),
                    dates = listOf("2026-11-03", "2026-11-07"), savedAt = "x"),
            ),
        )
        val draft = mapOf("trip.purpose" to "tourism", "profile.country_res" to "대한민국")
        val ctx = com.readyport.ui.form.FormContext("TH_TDAC", TestPacks.thailand.value.forms.first(), recipe.value, recipe.version, false)
        rule.setContent {
            com.readyport.ui.theme.ReadyPortTheme(easyMode = false) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(com.readyport.ui.theme.Tokens.Ground)) {
                    com.readyport.ui.form.FormConfirmContent(
                        com.readyport.ui.form.ConfirmUi(ctx, com.readyport.vault.WalletState.Unlocked(contents),
                            com.readyport.autofill.FormValues.build(recipe.value, contents, draft), draft),
                        { _, _ -> }, {}, {}, {}, {},
                    )
                }
            }
        }
        capture("m4_1_confirm_top")
        rule.onNode(androidx.compose.ui.test.hasScrollAction())
            .performScrollToNode(androidx.compose.ui.test.hasText(context.getString(R.string.form_choose_yourself)))
        capture("m4_2_confirm_choose")
    }

    @Test fun walletScreens() {
        val passport = com.readyport.vault.PassportRecord(
            surname = "ERIKSSON", givenNames = "ANNA MARIA", documentNumber = "L898902C3",
            nationality = "UTO", issuingState = "UTO", birthDate = "1974-08-12", sex = "F",
            expiryDate = "2027-01-10", source = "mrz", mrzVerified = true, savedAt = "2026-09-28T10:00",
        )
        val booking = com.readyport.vault.BookingRecord(
            id = "1", kind = "lodging", title = "방콕 숙소", reference = "4417-2290-12",
            checkIn = "2026-11-03", checkOut = "2026-11-07", savedAt = "2026-09-28T10:00",
        )
        var screen by androidx.compose.runtime.mutableStateOf(0)
        rule.setContent {
            com.readyport.ui.theme.ReadyPortTheme(easyMode = true) {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.background(com.readyport.ui.theme.Tokens.Ground),
                ) {
                    when (screen) {
                        0 -> com.readyport.ui.wallet.WalletContent(
                            state = com.readyport.vault.WalletState.Unlocked(
                                com.readyport.vault.VaultContents(passport = passport, bookings = listOf(booking)),
                            ),
                            deviceSecure = true, autoDestroy = true, today = java.time.LocalDate.of(2026, 9, 28),
                            onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                            onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
                        )
                        1 -> com.readyport.ui.wallet.PassportConfirmContent(
                            mrz = com.readyport.doc.mrz.MrzParser.parse(
                                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                                "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
                            ),
                            saveFailed = false, onSave = {}, onRescan = {}, onManual = {},
                        )
                        else -> com.readyport.ui.wallet.BookingImportContent(
                            state = com.readyport.ui.wallet.ImportState.Review(
                                com.readyport.doc.booking.BookingExtractor.extract("예약번호: ABC123\n편명 KE651 2026년 11월 3일"),
                            ),
                            saveFailed = false, onPickPhoto = {}, onPickPdf = {}, onText = {}, onSave = {}, onRestart = {}, onDone = {},
                        )
                    }
                }
            }
        }
        capture("m2_1_wallet")
        screen = 1; capture("m2_2_passport_confirm")
        screen = 2; capture("m2_3_booking_review")
    }
}
