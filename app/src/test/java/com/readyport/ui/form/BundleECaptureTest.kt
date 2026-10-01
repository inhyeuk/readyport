package com.readyport.ui.form

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.CorporateFare
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.autofill.FormValues
import com.readyport.ui.Gallery
import com.readyport.ui.TestPacks
import com.readyport.ui.components.loadPhotoCredits
import com.readyport.ui.settings.PhotoCreditsContent
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.VaultContents
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * E 묶음(16·17·22·28·자동 입력·수동 모드) 검토 캡처 — 공용 GalleryCaptureTest(동결)와 따로 찍는다.
 * ① 기기 높이(393×760dp, 테스트 폰 S10과 비슷) 화면을 한 쪽씩 스크롤하며 찍는다: 아주 긴 한 장 캡처가 16,384px 렌더 한계에서
 *    잘리던 문제(17 쉬운 모드 200%의 아래쪽 버튼·빈칸 띠) 없이 끝까지 보이고, 카드 그림자도 실제 화면 높이에서 본다.
 * ② 자동 입력(공식 사이트 WebView 자리표시)을 세 상태로 찍고 **공식 사이트 자리가 화면 높이의 45% 이상**(채운 뒤 40%,
 *    칸을 못 찾음 38%)인지 단언한다.
 * ③ 사진·글꼴 출처는 앱에 든 photo_credits.json 전부.
 * 결과: build/gallery_e/{basic|easy|sdk31_font200/basic|sdk31_font200/easy}/이름_p01.png …
 */
abstract class BundleECaptureBase {

    @get:Rule
    val rule = createComposeRule()

    protected open val folder: String = ""

    private val passport = PassportRecord(
        surname = "ERIKSSON", givenNames = "ANNA MARIA", documentNumber = "L898902C3",
        nationality = "KOR", issuingState = "KOR", birthDate = "1974-08-12", sex = "F",
        expiryDate = "2031-04-15", source = "mrz", mrzVerified = true, savedAt = "2026-09-29T10:00",
    )
    private val contents = VaultContents(
        passport = passport,
        bookings = listOf(
            BookingRecord(id = "1", kind = "flight", title = "방콕 왕복", flightNumbers = listOf("KE651"), dates = listOf("2026-11-03"), savedAt = "x"),
        ),
    )

    private val autofillUi: AutofillUi
        get() {
            val recipe = TestPacks.tdacRecipe
            val ctx = FormContext("TH_TDAC", TestPacks.thailand.value.forms.first(), recipe.value, recipe.version, false)
            return AutofillUi(context = ctx, values = FormValues.build(recipe.value, contents, emptyMap()), engine = "")
        }

    /** 공식 사이트 자리표시 (WebView 대신) */
    @Composable
    private fun SitePlaceholder(modifier: Modifier) {
        Box(
            modifier.testTag(SITE_TAG).background(Tokens.Surface).border(1.dp, Tokens.Line),
            contentAlignment = Alignment.Center,
        ) {
            Text("tdac.immigration.go.th (WebView)", style = MaterialTheme.typography.bodyMedium, color = Tokens.InkTertiary)
        }
    }

    private fun autofillScreens(): List<Pair<String, @Composable () -> Unit>> {
        val ui = autofillUi
        val recipe = TestPacks.tdacRecipe.value
        val site = SiteState(official = true, host = "tdac.immigration.go.th", visibleSteps = recipe.steps.mapIndexed { i, _ -> i == 0 })
        val filled = recipe.fields.filter { it.selector != null && ui.values[it.key]?.isEmpty == false }.take(6).map { it.key }
        val report = FillReport(filled, listOf("profile.occupation", "trip.purpose"), emptyList(), emptyList())
        val failed = FillReport(filled.take(2), emptyList(), listOf("stay.address"), emptyList())
        return listOf(
            "autofill-start" to {
                AutofillContent(ui, site, FillState(), {}, {}, {}, {}, {}) { SitePlaceholder(it) }
            },
            "autofill-filled" to {
                AutofillContent(ui, site, FillState(report, filled.associateWith { it != filled.last() }), {}, {}, {}, {}, {}) { SitePlaceholder(it) }
            },
            "autofill-failed" to {
                AutofillContent(ui, site.copy(official = false, host = "example.com"), FillState(failed), {}, {}, {}, {}, {}) { SitePlaceholder(it) }
            },
        )
    }

    private fun pagedScreens(): List<Pair<String, @Composable () -> Unit>> {
        val names = listOf("essentials", "form-confirm", "manual-mode", "settings")
        val gallery = Gallery.screens().filter { it.first in names }
        return gallery + listOf(
            "photo-credits-all" to {
                val context = LocalContext.current
                val credits = remember { loadPhotoCredits(context) }
                PhotoCreditsContent(credits, {})
            },
        )
    }

    private fun extraScreens(): List<Pair<String, @Composable () -> Unit>> = listOf("option-icons" to { OptionIconsPage() })

    private fun save(bmp: Bitmap, file: File) {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val verticalScroller = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    protected fun captureAll(easy: Boolean) {
        val dir = File("build/gallery_e/" + folder + if (easy) "easy" else "basic").apply { deleteRecursively(); mkdirs() }
        val paged = pagedScreens()
        val autofill = autofillScreens()
        val all = paged + autofill + extraScreens()
        var current by mutableIntStateOf(0)
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                Box(Modifier.fillMaxSize().background(Tokens.Ground)) { key(current) { all[current].second() } }
            }
        }
        val small = mutableListOf<String>()
        all.forEachIndexed { i, (name, _) ->
            rule.runOnIdle { current = i }
            rule.mainClock.advanceTimeBy(2_000)
            rule.waitForIdle()
            var page = 1
            while (true) {
                save(rule.onRoot().captureToImage().asAndroidBitmap(), File(dir, "%s_p%02d.png".format(name, page)))
                val scroller = rule.onAllNodes(verticalScroller).fetchSemanticsNodes()
                    .filter { it.config.contains(SemanticsActions.ScrollBy) }
                    .maxByOrNull { it.boundsInRoot.height } ?: break
                val range = scroller.config[SemanticsProperties.VerticalScrollAxisRange]
                if (range.value() >= range.maxValue() - 0.5f || page >= MAX_PAGES) break
                val step = scroller.boundsInRoot.height * 0.85f
                rule.onAllNodes(verticalScroller)
                    .fetchSemanticsNodes()
                    .indexOfFirst { it.id == scroller.id }
                    .let { idx -> rule.onAllNodes(verticalScroller)[idx] }
                    .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, step) }
                rule.mainClock.advanceTimeBy(500)
                rule.waitForIdle()
                page++
            }
            // ② 자동 입력: 공식 사이트 자리가 화면의 45% 이상 (결과가 있으면 40%)
            if (name.startsWith("autofill")) {
                val root = rule.onRoot().getBoundsInRoot()
                val siteBounds = rule.onNodeWithTag(SITE_TAG).getBoundsInRoot()
                val share = (siteBounds.bottom - siteBounds.top) / (root.bottom - root.top)
                // 처음(채우기 전) 45%, 채운 뒤 40%. 칸을 못 찾음·공식 사이트 아님은 다음 할 일이 수동 모드라 38%
                val min = when (name) {
                    "autofill-start" -> 0.45f
                    "autofill-filled" -> 0.40f
                    else -> 0.38f
                }
                println("AUTOFILL $folder${if (easy) "easy" else "basic"} $name site=${"%.2f".format(share)}")
                if (share < min) small += "$name: ${"%.2f".format(share)} < $min"
            }
        }
        assertTrue("공식 사이트 자리가 너무 작음: $small", small.isEmpty())
    }

    /** 라디오 행 아이콘 굵기 비교(17): 지금 쓰는 아이콘 + 후보 — 선 아이콘끼리 굵기가 맞는지 눈으로 본다 */
    @Composable
    private fun OptionIconsPage() {
        val rows = listOf(
            "now" to listOf(Icons.Outlined.BeachAccess, Icons.Outlined.LocalHospital, Icons.Outlined.School, Icons.Outlined.Hotel, Icons.Outlined.House, Icons.Outlined.Bed, Icons.Outlined.People),
            "old" to listOf(Icons.Outlined.Work, Icons.Outlined.Groups, Icons.Outlined.Apartment),
            "business" to listOf(Icons.Outlined.BusinessCenter, Icons.Outlined.WorkOutline),
            "meeting" to listOf(Icons.Outlined.Forum, Icons.Outlined.PeopleOutline, Icons.Outlined.MeetingRoom),
            "apartment" to listOf(Icons.Outlined.CorporateFare, Icons.Outlined.Domain, Icons.Outlined.LocationCity, Icons.Outlined.HomeWork),
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            rows.forEach { (label, icons) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = Tokens.Ink)
                    icons.forEach { Icon(it, contentDescription = null, tint = Tokens.InkSecondary, modifier = Modifier.size(32.dp)) }
                }
            }
        }
    }

    private companion object {
        const val SITE_TAG = "official-site"
        const val MAX_PAGES = 40
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h760dp-xhdpi")
class BundleECaptureTest : BundleECaptureBase() {

    @Test fun basic() = captureAll(easy = false)

    @Test fun easy() = captureAll(easy = true)
}

/** 테스트 폰(S10, Android 12)과 같은 sdk 31·글자 200% */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h760dp-xhdpi", fontScale = 2.0f)
class BundleECaptureSdk31Test : BundleECaptureBase() {

    override val folder = "sdk31_font200/"

    @Test fun basic() = captureAll(easy = false)

    @Test fun easy() = captureAll(easy = true)
}
