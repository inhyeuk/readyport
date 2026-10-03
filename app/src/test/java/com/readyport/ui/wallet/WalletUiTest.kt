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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.text.AnnotatedString
import com.readyport.ui.components.DateDigitsTransformation
import com.readyport.ui.components.dateDigits
import com.readyport.ui.components.digitsOf
import com.readyport.ui.components.parseDateDigits
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.doc.booking.BookingExtractor
import com.readyport.doc.mrz.MrzParser
import com.readyport.security.SecureScreen
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.VaultContents
import com.readyport.vault.WalletState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private fun s(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)
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
        // 'MRZ' 대신 쉬운 말 (재검토 R18)
        shown(s(R.string.wallet_passport_verified_v2))
        rule.onAllNodesWithText("MRZ", substring = true).assertCountEquals(0)
        shown(maskNumber("L898902C3"))
        rule.onAllNodesWithText("L898902C3").assertCountEquals(0)
        rule.onAllNodesWithText("ERIKSSON", substring = true).assertCountEquals(0)

        // 가린 값을 보이는 버튼은 '자세히 보기'(다른 화면의 펼치기)가 아니라 '가린 글자 보기'
        rule.onAllNodesWithText("자세히 보기").assertCountEquals(0)
        rule.onNodeWithText(s(R.string.wallet_passport_reveal)).performClick()
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
        // 무엇이 지워지고 무엇이 남는지 한 줄, 버튼은 하는 일 그대로(`비우고 여권 다시 등록하기`) — 재검토2 ②#4
        shown(s(R.string.wallet_reset_scope))
        shown(s(R.string.wallet_reset_reregister))
        rule.onAllNodesWithText(s(R.string.wallet_reset)).assertCountEquals(0)
        // 잠김·키 분실 화면에는 '곧 추가돼요' 묶음을 두지 않는다(재검토2 ⑤#11)
        rule.onAllNodesWithText(s(R.string.coming_soon_group)).assertCountEquals(0)
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
        // 표본 여권의 만료일(2012-04-15)은 지났으므로 경고한다. 만료일 행은 초록 `확인 완료` 대신 빨강 `만료됨` +
        // 확인 숫자가 맞았다는 보조 글 — 여권 번호·생년월일만 `확인 완료` (재검토 32)
        shown(s(R.string.wallet_passport_expired))
        rule.onAllNodesWithText(s(R.string.passport_check_ok)).assertCountEquals(2)
        shown(s(R.string.passport_expired_tag))
        shown(s(R.string.passport_expiry_read_ok))
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.passport_save)))
        rule.onNodeWithText(s(R.string.passport_save)).performClick()
        assertTrue(saved)
        // 전자여권 칩 확인은 2차 — 값 확인 화면에는 그리지 않는다('곧 추가돼요'는 내 정보 맨 아래 한 곳, 재검토2 ⑤#11). 'NFC' 글자도 없음
        rule.onAllNodesWithText(s(R.string.passport_chip_soon_v2)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.coming_soon_group)).assertCountEquals(0)
        rule.onAllNodesWithText("NFC", substring = true).assertCountEquals(0)
        // 날짜는 `1974년 8월 12일`로 보인다(재검토2 ①#13)
        shown("1974년 8월 12일")
        rule.onAllNodesWithText("1974-08-12").assertCountEquals(0)
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

    /** 대화상자 안의 버튼 (D8 확인 대화상자는 별도 창) */
    private fun inDialog(text: String) = rule.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test
    fun bookingDeleteAsksBeforeDeleting() {
        var deleted: String? = null
        val booking = BookingRecord(id = "b1", kind = "flight", title = "방콕 왕복", flightNumbers = listOf("KE651"), savedAt = "x")
        rule.setContent {
            ReadyPortTheme {
                WalletContent(
                    state = WalletState.Unlocked(VaultContents(bookings = listOf(booking))), deviceSecure = true, autoDestroy = true,
                    today = today, onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                    onAddBooking = {}, onDeleteBooking = { deleted = it }, onAutoDestroyChange = {},
                )
            }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.wallet_booking_delete)))
        rule.onNodeWithText(s(R.string.wallet_booking_delete)).performClick()
        // 바로 지우지 않고 먼저 묻는다. 대화상자에는 예약 이름·번호를 넣지 않는다
        assertEquals(null, deleted)
        rule.onNodeWithText(s(R.string.booking_delete_confirm_title)).assertIsDisplayed()
        rule.onAllNodesWithText("방콕 왕복").assertCountEquals(1)
        inDialog(s(R.string.action_cancel_keep)).performClick()
        assertEquals(null, deleted)
        rule.onAllNodesWithText(s(R.string.booking_delete_confirm_title)).assertCountEquals(0)

        rule.onNodeWithText(s(R.string.wallet_booking_delete)).performClick()
        inDialog(s(R.string.wallet_booking_delete)).performClick()
        assertEquals("b1", deleted)
    }

    @Test
    fun passportDeleteAndResetAskFirst() {
        var passportDeleted = false
        var reset = false
        var state by androidx.compose.runtime.mutableStateOf<WalletState>(WalletState.Unlocked(VaultContents(passport = passport)))
        rule.setContent {
            ReadyPortTheme {
                WalletContent(
                    state = state, deviceSecure = true, autoDestroy = true, today = today,
                    onUnlock = {}, onLock = {}, onReset = { reset = true }, onAddPassport = {}, onDeletePassport = { passportDeleted = true },
                    onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
                )
            }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.wallet_passport_delete)))
        rule.onNodeWithText(s(R.string.wallet_passport_delete)).performClick()
        assertTrue(!passportDeleted)
        rule.onNodeWithText(s(R.string.today_destroy_title)).assertIsDisplayed()
        inDialog(s(R.string.wallet_passport_delete)).performClick()
        assertTrue(passportDeleted)

        state = WalletState.Failed(WalletState.Failed.Reason.Corrupted)
        rule.onNodeWithText(s(R.string.wallet_corrupted)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.wallet_reset_reregister)).performClick()
        assertTrue(!reset)
        rule.onNodeWithText(s(R.string.wallet_reset_confirm_title)).assertIsDisplayed()
        inDialog(s(R.string.wallet_reset_reregister)).performClick()
        assertTrue(reset)
    }

    @Test
    fun walletRowsOpenCompanionsAndToggleAutoDestroy() {
        var opened = false
        var auto: Boolean? = null
        rule.setContent {
            ReadyPortTheme {
                WalletContent(
                    state = WalletState.Unlocked(VaultContents()), deviceSecure = true, autoDestroy = true, today = today,
                    onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                    onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = { auto = it }, onOpenCompanions = { opened = true },
                )
            }
        }
        // 여권이 없으면 등록 카드, 예약이 없으면 빈 안내
        shown(s(R.string.wallet_passport_add))
        shown(s(R.string.wallet_bookings_empty))
        shown(s(R.string.wallet_auto_destroy))
        rule.onNodeWithText(s(R.string.wallet_auto_destroy)).performClick()
        assertEquals(false, auto)
        shown(s(R.string.wallet_companions_title))
        rule.onNodeWithText(s(R.string.wallet_companions_title)).performClick()
        assertTrue(opened)
        // 준비 중 기능은 누를 수 없는 묶음으로 — 열린 내 정보 맨 아래 한 곳(여권 칩 확인도 여기로)
        shown(s(R.string.wallet_profile_title))
        rule.onNodeWithText(s(R.string.wallet_profile_title)).assertIsNotEnabled()
        shown(s(R.string.passport_chip_soon_v2))
        rule.onNodeWithText(s(R.string.passport_chip_soon_v2)).assertIsNotEnabled()
        // `받은 서류(QR)`는 이미 `입국 때 보여 주기`에 있는 기능 — '곧 추가'라고 하지 않는다(재검토2 ⑤#11)
        rule.onAllNodesWithText(s(R.string.wallet_documents_title)).assertCountEquals(0)
    }

    /**
     * 여권 정보 지우기는 같은 글자의 설정(`여행이 끝나면 여권 정보 지우기`)·`내 정보 잠그기`와 떨어진 맨 아래(위 32dp),
     * 바로 위 한 줄이 무엇이 지워지고 무엇이 남는지 말한다 (재검토2 ②#4)
     */
    @Test
    @Config(qualifiers = "w393dp-h4000dp")
    fun passportDeleteSitsApartWithScopeLine() {
        wallet(WalletState.Unlocked(VaultContents(passport = passport)))
        shown(s(R.string.wallet_passport_delete))
        shown(s(R.string.wallet_passport_delete_scope))
        fun node(id: Int) = rule.onNodeWithText(s(id)).fetchSemanticsNode()
        val toggle = node(R.string.wallet_auto_destroy).boundsInRoot
        val lock = node(R.string.wallet_lock).boundsInRoot
        val scope = node(R.string.wallet_passport_delete_scope).boundsInRoot
        val delete = node(R.string.wallet_passport_delete).boundsInRoot
        val soon = node(R.string.coming_soon_group).boundsInRoot
        assertTrue("설정 → 잠그기 → 범위 한 줄 → 지우기 → 곧 추가 순서", toggle.bottom < lock.top && lock.bottom < scope.top && scope.bottom < delete.top && delete.bottom < soon.top)
        val gapDp = (scope.top - lock.bottom) / rule.density.density
        assertTrue("잠그기와 지우기 묶음 사이 24dp 이상: $gapDp", gapDp >= 24f)
    }

    @Test
    fun masking() {
        assertEquals("L••••••C3", maskNumber("L898902C3"))
        assertEquals("E•••••• A•••", maskName("ERIKSSON", "ANNA MARIA"))
    }

    /** 항공권 날짜는 가는 날·(가운데)·오는 날 한 줄씩, 숙소는 체크인·체크아웃 — 글 안에 구분 기호를 넣지 않는다 */
    @Test
    fun bookingDatesAreOneLabeledRowEach() {
        rule.setContent {
            ReadyPortTheme {
                WalletContent(
                    state = WalletState.Unlocked(
                        VaultContents(
                            bookings = listOf(
                                BookingRecord(id = "f", kind = "flight", title = "방콕 왕복", dates = listOf("2026-11-03", "2026-11-07"), savedAt = "x"),
                                BookingRecord(id = "l", kind = "lodging", title = "방콕 숙소", checkIn = "2026-11-04", checkOut = "2026-11-06", savedAt = "x"),
                            ),
                        ),
                    ),
                    deviceSecure = true, autoDestroy = true, today = today,
                    onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                    onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
                )
            }
        }
        shown(s(R.string.wallet_booking_date_out))
        shown(s(R.string.wallet_booking_date_back))
        shown(s(R.string.booking_label_checkin))
        shown(s(R.string.booking_label_checkout))
        // 보이는 날짜는 `2026년 11월 3일 (화)` — ISO 모양은 화면에 없다(재검토2 ①#13, 저장 값은 그대로)
        listOf("2026년 11월 3일 (화)", "2026년 11월 7일 (토)", "2026년 11월 4일 (수)", "2026년 11월 6일 (금)")
            .forEach { rule.onAllNodesWithText(it).assertCountEquals(1) }
        listOf("2026-11-03", "2026-11-07", "2026-11-04", "2026-11-06").forEach { rule.onAllNodesWithText(it).assertCountEquals(0) }
        rule.onAllNodesWithText("→", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText(" · ", substring = true).assertCountEquals(0)
    }

    // ---------------- 재검토 R18 ----------------

    /** 여권 단계 표시는 지금 쓸 수 있는 두 단계(촬영 → 값 확인)만 — 아직 없는 칩 확인(NFC)을 단계로 약속하지 않는다 */
    @Test
    fun passportStepperShowsOnlyAvailableSteps() {
        rule.setContent { ReadyPortTheme { PassportIntroContent(ScanState.Idle, {}, {}, {}) } }
        rule.onNodeWithContentDescription(s(R.string.passport_steps_desc, s(R.string.passport_step_scan), 1, 2)).assertExists()
        rule.onAllNodesWithText("칩 확인", substring = true).assertCountEquals(0)
        // '기기' 대신 약속 문구와 같은 '휴대폰'
        rule.onAllNodesWithText("이 기기", substring = true).assertCountEquals(0)
        shown(s(R.string.passport_privacy_1_v2))
        shown(s(R.string.passport_privacy_2_v2))
    }

    /** 날짜 칸은 숫자만 받고(붙여 넣은 하이픈은 버림), 화면에서만 YYYY-MM-DD 모양 — 커서 위치가 숫자 자리와 오간다 */
    @Test
    fun dateDigitsFieldLogic() {
        assertEquals("19740812", dateDigits("1974-08-12"))
        assertEquals("20261103", dateDigits("2026-11-03 "))
        assertEquals("12345678", dateDigits("1234567890"))
        assertEquals(LocalDate.of(1974, 8, 12), parseDateDigits("19740812"))
        assertNull(parseDateDigits("19741312"))
        assertNull(parseDateDigits("197408"))
        assertEquals("20261103", digitsOf("2026-11-03"))
        assertEquals("", digitsOf("11월 3일"))
        listOf("", "1", "1974", "19740", "197408", "1974081", "19740812").forEach { d ->
            val t = DateDigitsTransformation.filter(AnnotatedString(d))
            assertEquals(d, t.text.text.replace("-", ""))
            for (o in 0..d.length) {
                val shown = t.offsetMapping.originalToTransformed(o)
                assertTrue("$d $o", shown in 0..t.text.length)
                assertEquals("$d $o", o, t.offsetMapping.transformedToOriginal(shown))
            }
            for (o in 0..t.text.length) assertTrue("$d $o", t.offsetMapping.transformedToOriginal(o) in 0..d.length)
        }
        assertEquals("1974-08-12", DateDigitsTransformation.filter(AnnotatedString("19740812")).text.text)
    }

    @Test
    fun manualPassportTakesDigitOnlyDates() {
        var saved: PassportRecord? = null
        rule.setContent { ReadyPortTheme { PassportManualContent { saved = it } } }
        fun field(label: Int) = rule.onNode(hasSetTextAction() and hasText(s(label)))
        // 날짜 칸은 달력이 주 입력이다(다듬기 S2) — `달력 대신 숫자로 적기`를 거쳐 숫자 칸으로 바꾼 뒤 적는다
        fun typeDate(label: Int, digits: String) {
            val cd = s(R.string.date_pick_type_cd, s(label))
            // 단추를 누르려면 화면에 보여야 한다(목록이 길다)
            rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescriptionExactly(cd))
            rule.onNodeWithContentDescription(cd).performClick()
            rule.mainClock.advanceTimeBy(1_000)
            rule.waitForIdle()
            field(label).performTextInput(digits)
        }
        field(R.string.passport_field_surname).performTextInput("HONG")
        field(R.string.passport_field_given).performTextInput("GILDONG")
        field(R.string.passport_field_number).performTextInput("M12345678")
        typeDate(R.string.passport_field_birth, "19850315")
        // 붙여 넣은 하이픈은 버리고 숫자만 남는다
        typeDate(R.string.passport_field_expiry, "2034-05-20")
        rule.onNodeWithText("1985-03-15").assertExists()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.passport_save)))
        rule.onNodeWithText(s(R.string.passport_save)).performClick()
        assertEquals("1985-03-15", saved?.birthDate)
        assertEquals("2034-05-20", saved?.expiryDate)
        assertEquals("manual", saved?.source)
    }

    /** 숙소 체크인·체크아웃도 숫자 칸: 읽은 날짜가 그대로 들어가고, 반쯤 적은 날짜면 저장을 막는다(버리며 저장하지 않게) */
    @Test
    fun bookingLodgingDatesAreDigitFields() {
        var saved: BookingDraft? = null
        val fields = BookingExtractor.extract("호텔 예약 확인\n예약번호: HTL88123\n체크인 2026-11-03\n체크아웃 2026-11-07")
        rule.setContent {
            ReadyPortTheme {
                BookingImportContent(
                    state = ImportState.Review(fields), saveFailed = false,
                    onPickPhoto = {}, onPickPdf = {}, onText = {}, onSave = { saved = it }, onRestart = {}, onDone = {},
                )
            }
        }
        rule.onNodeWithText("2026-11-03").assertExists()
        // 달력이 주 입력 — 숫자로 적으려면 칸 아래 `숫자로 적기` (다듬기 S2)
        val typeCd = s(R.string.date_pick_type_cd, s(R.string.booking_label_checkin))
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescriptionExactly(typeCd))
        rule.onNodeWithContentDescription(typeCd).performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val checkIn = rule.onNode(hasSetTextAction() and hasText(s(R.string.booking_label_checkin)))
        checkIn.performTextClearance()
        checkIn.performTextInput("202611")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.booking_save)))
        rule.onNodeWithText(s(R.string.booking_save)).assertIsNotEnabled()
        checkIn.performTextInput("04")
        rule.onNodeWithText(s(R.string.booking_save)).assertIsEnabled().performClick()
        val record = saved!!.toRecord()
        assertEquals("2026-11-04", record.checkIn)
        assertEquals("2026-11-07", record.checkOut)
    }

    /** 예약 서류의 `지우기`는 TalkBack에서 무엇을 지우는지(보이는 서류 이름) 함께 읽는다 */
    @Test
    fun bookingDeleteNamesItsTarget() {
        var deleted: String? = null
        val booking = BookingRecord(id = "b1", kind = "flight", title = "방콕 왕복", flightNumbers = listOf("KE651"), savedAt = "x")
        rule.setContent {
            ReadyPortTheme {
                WalletContent(
                    state = WalletState.Unlocked(VaultContents(bookings = listOf(booking))), deviceSecure = true, autoDestroy = true,
                    today = today, onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                    onAddBooking = {}, onDeleteBooking = { deleted = it }, onAutoDestroyChange = {},
                )
            }
        }
        val name = s(R.string.delete_named_cd, "방콕 왕복")
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(name))
        rule.onNodeWithContentDescription(name).performClick()
        inDialog(s(R.string.wallet_booking_delete)).performClick()
        assertEquals("b1", deleted)
    }

    /** 열린 지갑: 보안 띠는 한 줄(여권 카드가 첫 주인공), 잠긴 지갑은 설명까지 */
    @Test
    fun unlockedWalletUsesCompactSecurityLine() {
        var state by androidx.compose.runtime.mutableStateOf<WalletState>(WalletState.Unlocked(VaultContents(passport = passport)))
        rule.setContent {
            ReadyPortTheme {
                WalletContent(
                    state = state, deviceSecure = true, autoDestroy = true, today = today,
                    onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                    onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
                )
            }
        }
        rule.onNodeWithText(s(R.string.settings_local_only_title)).assertIsDisplayed()
        rule.onAllNodesWithText(s(R.string.settings_local_only_body)).assertCountEquals(0)
        state = WalletState.Locked(hasData = true)
        rule.onNodeWithText(s(R.string.settings_local_only_body)).assertIsDisplayed()
    }
}
