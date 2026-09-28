package com.readyport.ui.wallet

import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.doc.booking.BookingExtractor
import com.readyport.doc.mrz.MrzParser
import com.readyport.security.SecureScreen
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.vault.PassportRecord
import com.readyport.vault.VaultContents
import com.readyport.vault.WalletState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** M2: 지갑 잠금·가림 표시, 여권 확인 화면의 저장 차단, 예약 서류 확인, 민감 화면 캡처 차단 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class WalletUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int) = context.getString(id)
    private val today = LocalDate.of(2026, 9, 28)

    /** 긴 목록 아래쪽 항목은 스크롤해서 화면에 보이게 한 뒤 확인한다 */
    private fun shown(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    // ICAO 9303 표본 (가상 국가)
    private val passport = PassportRecord(
        surname = "ERIKSSON", givenNames = "ANNA MARIA", documentNumber = "L898902C3",
        nationality = "UTO", issuingState = "UTO", birthDate = "1974-08-12", sex = "F",
        expiryDate = "2036-04-15", source = "mrz", mrzVerified = true, savedAt = "2026-09-28T10:00",
    )

    private fun wallet(state: WalletState, deviceSecure: Boolean = true, onUnlock: () -> Unit = {}) {
        rule.setContent {
            ReadyPortTheme {
                WalletContent(
                    state = state, deviceSecure = deviceSecure, autoDestroy = true, today = today,
                    onUnlock = onUnlock, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                    onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
                )
            }
        }
    }

    @Test
    fun lockedWalletShowsOnlyUnlockButton() {
        var unlocked = false
        wallet(WalletState.Locked(hasData = true), onUnlock = { unlocked = true })
        rule.onNodeWithText(s(R.string.wallet_locked_title)).assertIsDisplayed()
        rule.onAllNodesWithText("L898902C3", substring = true).assertCountEquals(0)
        rule.onNodeWithText(s(R.string.wallet_unlock)).performClick()
        assertTrue(unlocked)
    }

    @Test
    fun unlockedPassportIsMaskedUntilRevealed() {
        wallet(WalletState.Unlocked(VaultContents(passport = passport)))
        shown(s(R.string.wallet_passport_verified))
        shown(maskNumber("L898902C3"))
        rule.onAllNodesWithText("L898902C3").assertCountEquals(0)
        rule.onAllNodesWithText("ERIKSSON", substring = true).assertCountEquals(0)

        rule.onNodeWithText(s(R.string.wallet_passport_show)).performClick()
        rule.onNodeWithText("L898902C3").assertIsDisplayed()
    }

    @Test
    fun expiringPassportWarns() {
        wallet(WalletState.Unlocked(VaultContents(passport = passport.copy(expiryDate = "2027-01-10"))))
        shown(s(R.string.wallet_passport_expiring))
    }

    @Test
    fun noLockScreenWarning() {
        wallet(WalletState.Locked(hasData = false), deviceSecure = false)
        rule.onNodeWithText(s(R.string.wallet_no_lock_title)).assertIsDisplayed()
    }

    @Test
    fun keyLostOffersReset() {
        wallet(WalletState.Failed(WalletState.Failed.Reason.KeyLost))
        rule.onNodeWithText(s(R.string.wallet_key_lost)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.wallet_reset)).assertIsDisplayed()
    }

    @Test
    fun confirmScreenBlocksSaveWhenCheckDigitFails() {
        val bad = MrzParser.parse(
            "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
            "L898902C36UTO7408132F1204159ZE184226B<<<<<10", // 생년월일 한 글자 틀림
            today,
        )!!
        rule.setContent {
            ReadyPortTheme { PassportConfirmContent(mrz = bad, saveFailed = false, onSave = {}, onRescan = {}, onManual = {}) }
        }
        shown(s(R.string.passport_check_warning))
        rule.onAllNodesWithText(s(R.string.passport_save)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.passport_check_fail)).assertCountEquals(1)
    }

    @Test
    fun confirmScreenSavesVerifiedPassport() {
        val good = MrzParser.parse(
            "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
            "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
            today,
        )!!
        var saved = false
        rule.setContent {
            ReadyPortTheme { PassportConfirmContent(mrz = good, saveFailed = false, onSave = { saved = true }, onRescan = {}, onManual = {}, today = today) }
        }
        rule.onAllNodesWithText(s(R.string.passport_check_ok)).assertCountEquals(3)
        // 표본 여권의 만료일(2012-04-15)은 지났으므로 경고한다
        shown(s(R.string.wallet_passport_expired))
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.passport_save)))
        rule.onNodeWithText(s(R.string.passport_save)).performClick()
        assertTrue(saved)
        // NFC 칩 확인은 2차 — 누를 수 없다
        rule.onNodeWithText(s(R.string.passport_chip_soon)).assertIsNotEnabled()
    }

    @Test
    fun bookingReviewSavesEditedValues() {
        val fields = BookingExtractor.extract("예약번호: ABC123\n편명 KE651 2026년 11월 3일")
        var saved: BookingDraft? = null
        rule.setContent {
            ReadyPortTheme {
                BookingImportContent(
                    state = ImportState.Review(fields), saveFailed = false,
                    onPickPhoto = {}, onPickPdf = {}, onText = {}, onSave = { saved = it }, onRestart = {}, onDone = {},
                )
            }
        }
        rule.onNodeWithText("ABC123").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.booking_save)))
        rule.onNodeWithText(s(R.string.booking_save)).performClick()
        val record = saved!!.toRecord()
        assertEquals("flight", record.kind)
        assertEquals("ABC123", record.reference)
        assertEquals(listOf("KE651"), record.flightNumbers)
        assertEquals(listOf("2026-11-03"), record.dates)
    }

    @Test
    fun secureScreenSetsAndClearsFlagSecure() {
        var show by androidx.compose.runtime.mutableStateOf(true)
        rule.setContent { if (show) SecureScreen() }
        val flags = { rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE }
        rule.waitForIdle()
        assertEquals(WindowManager.LayoutParams.FLAG_SECURE, flags())
        show = false
        rule.waitForIdle()
        assertEquals(0, flags())
    }

    @Test
    fun masking() {
        assertEquals("L••••••C3", maskNumber("L898902C3"))
        assertEquals("E•••••• A•••", maskName("ERIKSSON", "ANNA MARIA"))
    }
}
