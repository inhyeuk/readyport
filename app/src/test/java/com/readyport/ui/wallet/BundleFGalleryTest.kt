package com.readyport.ui.wallet

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.doc.booking.BookingExtractor
import com.readyport.doc.mrz.MrzParser
import com.readyport.ui.present.CompanionsContent
import com.readyport.ui.present.DocView
import com.readyport.ui.present.PresentContent
import com.readyport.ui.present.PresentUi
import com.readyport.ui.present.Traveler
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import com.readyport.vault.BookingRecord
import com.readyport.vault.EntryDoc
import com.readyport.vault.PassportRecord
import com.readyport.vault.TravelCompanion
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
import java.util.Random

/**
 * F 묶음(지갑·여권·보여 주기)의 공유 갤러리에 없는 상태 (공유 Gallery.kt에 없는 상태를 여기서 찍는다):
 * 21 QR 그림이 있는 보여 주기(사람 둘), 27 사람이 있는 같이 가는 사람(아바타·여권 등록/됨·지우기),
 * 25 카메라 권한·직접 입력·못 찾음·만료 안 된 값 확인, 26 고르기·숙소 검토·저장됨, 24 만료 임박·만료 여권 + 예약 날짜 행.
 * 캡처는 build/gallery/bundle_f/{basic|easy|sdk31_font200/basic|sdk31_font200/easy}/, 같은 루프에서 접근성 점검 규칙
 * (터치·보이는 크기 48/56dp, 이름, 화면 잘림)을 그대로 지킨다 — 실패하면 테스트가 실패한다.
 */
internal object BundleFScreens {
    private val today = LocalDate.of(2026, 9, 29)

    // ICAO 9303 표본 값만 쓴다
    private val passport = PassportRecord(
        surname = "ERIKSSON", givenNames = "ANNA MARIA", documentNumber = "L898902C3",
        nationality = "KOR", issuingState = "KOR", birthDate = "1974-08-12", sex = "F",
        expiryDate = "2027-01-15", source = "manual", mrzVerified = false, savedAt = "2026-09-29T10:00",
    )
    private val bookings = listOf(
        BookingRecord(
            id = "1", kind = "flight", title = "방콕 왕복 (경유)", reference = "ABC123",
            flightNumbers = listOf("KE651", "TG628", "KE652"), dates = listOf("2026-11-03", "2026-11-05", "2026-11-07"), savedAt = "x",
        ),
        BookingRecord(id = "2", kind = "lodging", title = "방콕 숙소", reference = "0000-0000", checkIn = "2026-11-03", checkOut = "2026-11-07", savedAt = "x"),
    )

    private fun wallet(contents: VaultContents, deviceSecure: Boolean = true): @Composable () -> Unit = {
        WalletContent(
            state = WalletState.Unlocked(contents), deviceSecure = deviceSecure, autoDestroy = true, today = today,
            onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
            onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
        )
    }

    private fun booking(state: ImportState): @Composable () -> Unit = {
        BookingImportContent(
            state = state, saveFailed = false, onPickPhoto = {}, onPickPdf = {}, onText = {}, onSave = {}, onRestart = {}, onDone = {},
        )
    }

    /** QR처럼 생긴 표본 그림(찾기 무늬 3개 + 고정 난수 칸, 흰 조용한 영역) — 실제 QR이 아니다 */
    fun sampleQr(): ImageBitmap {
        val n = 33
        val cell = 12
        val quiet = 4
        val size = (n + quiet * 2) * cell
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        val rnd = Random(651)
        fun finder(r: Int, c: Int) = listOf(0 to 0, 0 to n - 7, n - 7 to 0).any { (fr, fc) -> r in fr until fr + 8 && c in fc until fc + 8 }
        fun finderDark(r: Int, c: Int): Boolean {
            val (fr, fc) = listOf(0 to 0, 0 to n - 7, n - 7 to 0).first { (fr, fc) -> r in fr until fr + 8 && c in fc until fc + 8 }
            val y = r - fr
            val x = c - fc
            if (y == 7 || x == 7) return false
            return y == 0 || y == 6 || x == 0 || x == 6 || (y in 2..4 && x in 2..4)
        }
        for (r in 0 until n) for (c in 0 until n) {
            val dark = if (finder(r, c)) finderDark(r, c) else rnd.nextInt(100) < 48
            if (!dark) continue
            for (dy in 0 until cell) for (dx in 0 until cell) {
                bmp.setPixel((c + quiet) * cell + dx, (r + quiet) * cell + dy, android.graphics.Color.BLACK)
            }
        }
        return bmp.asImageBitmap()
    }

    fun screens(qr: ImageBitmap): List<Pair<String, @Composable () -> Unit>> {
        val doc = EntryDoc(
            id = "d1", formId = "TH_TDAC", travelerId = "self", blobId = "b1", confirmationNo = "TDAC-0000",
            arrivalDate = "2026-11-03", flightNo = "KE651", source = "capture", savedAt = "2026-11-01T10:00",
        )
        val mrz = MrzParser.parse("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", "L898902C36UTO7408122F1204159ZE184226B<<<<<10", today)!!
        val companions = listOf(
            TravelCompanion("c1", "첫째", null, consentConfirmed = true, addedAt = "x"),
            TravelCompanion("c2", "어머니", passport.copy(mrzVerified = true), consentConfirmed = true, addedAt = "x"),
        )
        return listOf(
            "present-qr" to {
                PresentContent(
                    PresentUi(
                        locked = false,
                        travelers = listOf(Traveler("self", "나"), Traveler("c1", "첫째")),
                        docs = listOf(DocView(doc, "태국 입국 카드 (TDAC)", qr, "E•••••• A•••", "L••••••C3")),
                    ),
                    {}, {}, {}, {},
                )
            },
            "companions-filled" to { CompanionsContent(WalletState.Unlocked(VaultContents(companions = companions)), {}, {}, {}, {}) },
            "wallet-passport-expiring" to wallet(VaultContents(passport = passport, bookings = bookings), deviceSecure = false),
            "wallet-passport-expired" to wallet(VaultContents(passport = passport.copy(expiryDate = "2026-05-01", mrzVerified = true))),
            "wallet-empty" to wallet(VaultContents()),
            "passport-intro-not-found" to { PassportIntroContent(ScanState.NotFound, {}, {}, {}) },
            "passport-scan-permission" to { PassportScanContent(granted = false, onAllowCamera = {}) {} },
            "passport-manual" to { PassportManualContent {} },
            "passport-confirm-valid" to {
                PassportConfirmContent(
                    mrz = mrz.copy(expiryDate = LocalDate.of(2031, 4, 15)),
                    saveFailed = false, onSave = {}, onRescan = {}, onManual = {}, today = today,
                )
            },
            "booking-choose" to booking(ImportState.Choose),
            "booking-review-lodging" to booking(
                ImportState.Review(BookingExtractor.extract("호텔 예약 확인\n예약번호: HTL88123\n체크인 2026-11-03\n체크아웃 2026-11-07")),
            ),
            "booking-saved" to booking(ImportState.Saved),
        )
    }
}

abstract class BundleFGalleryBase {

    @get:Rule
    val rule = createComposeRule()

    /** build/gallery/bundle_f/ 아래 폴더 이름 앞부분 (끝에 / 포함) */
    protected open val folder: String = ""

    private val groundArgb = android.graphics.Color.argb(
        255, (Tokens.Ground.red * 255).toInt(), (Tokens.Ground.green * 255).toInt(), (Tokens.Ground.blue * 255).toInt(),
    )

    private fun trimBottom(bmp: Bitmap): Bitmap? {
        var bottom = bmp.height - 1
        loop@ while (bottom > 0) {
            for (x in 0 until bmp.width step 4) if (bmp.getPixel(x, bottom) != groundArgb) break@loop
            bottom--
        }
        if (bottom >= bmp.height - 1) return null
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, minOf(bmp.height, bottom + 24))
    }

    private fun label(n: SemanticsNode): String? {
        val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val desc = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        return listOfNotNull(text, desc).joinToString(" ").takeIf { it.isNotBlank() }
    }

    protected fun captureAndAudit(easy: Boolean) {
        val list = BundleFScreens.screens(BundleFScreens.sampleQr())
        val dir = File("build/gallery/bundle_f/" + folder + if (easy) "easy" else "basic").apply { deleteRecursively(); mkdirs() }
        val problems = mutableListOf<String>()
        val minDp = if (easy) 56 else 48
        var current by mutableIntStateOf(0)
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                Box(Modifier.fillMaxSize().background(Tokens.Ground)) { list[current].second() }
            }
        }
        list.forEachIndexed { i, (name, _) ->
            rule.runOnIdle { current = i }
            rule.mainClock.advanceTimeBy(2_000)
            rule.waitForIdle()
            val density = rule.density.density
            val minPx = minDp * density - 1
            rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { n ->
                val lbl = label(n)
                val t = n.touchBoundsInRoot
                val v = n.boundsInRoot
                if (t.width < minPx || t.height < minPx) problems += "$name: 터치 영역 ${(t.width / density).toInt()}x${(t.height / density).toInt()}dp ($lbl)"
                if (v.width < minPx || v.height < minPx) problems += "$name: 보이는 크기 ${(v.width / density).toInt()}x${(v.height / density).toInt()}dp ($lbl)"
                if (lbl == null) problems += "$name: 이름 없는 버튼"
            }
            val cutList = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes()
                .any { n -> n.config[SemanticsProperties.VerticalScrollAxisRange].let { it.maxValue() > it.value() + 0.5f } }
            if (cutList) problems += "$name: 화면이 높이보다 길어 아래쪽을 점검하지 못함"
            val full = rule.onRoot().captureToImage().asAndroidBitmap()
            val bmp = trimBottom(full) ?: full.also { problems += "$name: 캡처 맨 아래까지 내용이 참(잘림)" }
            File(dir, "%02d_%s.png".format(i, name)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h6000dp-xhdpi")
class BundleFGalleryTest : BundleFGalleryBase() {
    @Test fun basic() = captureAndAudit(easy = false)

    @Test fun easy() = captureAndAudit(easy = true)
}

/** S10(Android 12)과 같은 sdk 31·글자 200% — 음절 단위 줄바꿈(API 33 미만)에서 keepWords가 어절을 지키는지 본다 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h12000dp-xhdpi", fontScale = 2.0f)
class BundleFGallerySdk31Test : BundleFGalleryBase() {
    override val folder = "sdk31_font200/"

    @Test fun basic() = captureAndAudit(easy = false)

    @Test fun easy() = captureAndAudit(easy = true)
}
