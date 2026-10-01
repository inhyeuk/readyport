package com.readyport.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.autofill.FormValues
import com.readyport.transport.Place
import com.readyport.trip.StageInfo
import com.readyport.trip.Trip
import com.readyport.trip.TripStage
import com.readyport.ui.components.KoText
import com.readyport.ui.components.keepWords
import com.readyport.ui.components.Photos
import com.readyport.ui.country.CountryActions
import com.readyport.ui.country.CountryContent
import com.readyport.ui.form.ConfirmUi
import com.readyport.ui.form.FormConfirmContent
import com.readyport.ui.form.FormContext
import com.readyport.ui.home.HomeActions
import com.readyport.ui.home.HomeContent
import com.readyport.ui.pack.HelpContent
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Play 스토어 등록 이미지 (M10, 재검토 R20). 결과: app/build/store/ (커밋은 docs/play/store/ 로 **같은 이름** 복사)
 * - 스크린숏: 1215×2160 = 정확히 9:16 (Play 콘솔 규칙: 16:9 또는 9:16, 320~3840px)
 * - 순서(브랜드 검토 추천): 홈 → 입국 카드 확인(TDAC) → 나라 입국·비자(태국) → 출국 날 할 일 → 도움 → 내 정보(여권, 가림)
 *   → 이동하기(기사님께 보여 주기) → 쉬운 모드 여행 중. 영상 화면(YouTube 썸네일)은 쓰지 않는다.
 * - 각 장 = 위쪽 띠의 앱 밖 캡션(한 줄 + `정부 기관과 제휴하지 않은 앱이에요`) + 아래 실제 화면 첫 부분(축소).
 */
private fun Bitmap.saveTo(name: String): File {
    val dir = File("build/store").apply { mkdirs() }
    return File(dir, name).also { f -> f.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) } }
}

/**
 * 스토어 전용 가짜 값 (Gallery의 ICAO 표본과 따로 — 브랜드 검토 5).
 * 누가 봐도 견본인 한국 이름(HONG GILDONG)·국적 KOR·아직 유효한 만료일, 오류·만료·경고 상태 없음.
 * 정책 문장은 지어내지 않는다 — 나라·도움·입국 카드 화면은 저장소의 서명된 실제 팩(TestPacks)을 그대로 읽는다.
 */
internal object StoreFixture {
    /** 화면 기준 날짜: 출발(11월 3일) 3일 전 */
    val today: LocalDate = LocalDate.of(2026, 10, 31)
    val trip = Trip("TH", "2026-11-03", "2026-11-07")

    val passport = PassportRecord(
        surname = "HONG", givenNames = "GILDONG", documentNumber = "M12345678",
        nationality = "KOR", issuingState = "KOR", birthDate = "1985-03-15", sex = "M",
        expiryDate = "2034-05-20", source = "mrz", mrzVerified = true, savedAt = "2026-10-01T10:00",
    )

    val contents = VaultContents(
        passport = passport,
        bookings = listOf(
            BookingRecord(
                id = "f1", kind = "flight", title = "방콕 왕복", flightNumbers = listOf("KE651", "KE652"),
                dates = listOf("2026-11-03", "2026-11-07"), savedAt = "2026-10-01T10:00",
            ),
            BookingRecord(
                id = "l1", kind = "lodging", title = "방콕 숙소", reference = "SAMPLE01",
                checkIn = "2026-11-03", checkOut = "2026-11-07", savedAt = "2026-10-01T10:00",
            ),
        ),
    )

    /** 입국 카드에서 사용자가 고르거나 적는 칸 — 견본 값. 필수 칸을 모두 채워 둔다(빈칸 요약이 첫 화면을 덮어 값이 안 보이지 않게, R20) */
    val formDraft = mapOf(
        "trip.purpose" to "tourism",
        "profile.occupation" to "OFFICE WORKER",
        "profile.country_res" to "대한민국",
        "profile.city_res" to "SEOUL",
        // 견본 휴대폰 번호(010-1234-5678 — 안내서에 흔히 쓰는 예시 번호). 나라 번호는 레시피 기본값(82)
        "profile.phone_code" to "82",
        "profile.phone" to "1012345678",
        "trip.country_board" to "대한민국",
        "stay.type" to "hotel",
        "stay.province" to "Bangkok",
        "stay.address" to "SAMPLE HOTEL, SUKHUMVIT SOI 11",
    )

    /** 가는 곳: 지역 이름만 있는 견본 주소(실제 숙소 주소가 아님) */
    val place = Place("p1", "방콕 숙소", "สุขุมวิท ซอย 11 กรุงเทพฯ")
}

/** 스크린숏 한 장: 파일 이름, 캡션(줄은 뜻 단위로 직접 나눈다), 쉬운 모드인지, 화면 */
private class StoreShot(val name: String, val caption: String, val easy: Boolean = false, val content: @Composable () -> Unit)

/** 모든 장 아래 줄 (스토어 등록 정보·그래픽 이미지와 같은 뜻의 비제휴 문구) */
private const val NOT_AFFILIATED = "정부 기관과 제휴하지 않은 앱이에요"

/** 실제 화면을 그리는 폭 — 휴대폰 화면 폭(405dp) 그대로 재고 그린 뒤 캡션 아래 칸에 맞춰 줄인다 */
private val PhoneWidth = 405.dp

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w405dp-h720dp-xxhdpi")
class StoreScreenshotsTest {

    @get:Rule
    val rule = createComposeRule()

    private val th get() = TestPacks.thailand

    private fun shots(): List<StoreShot> = listOf(
        // 히어로 가치 문장(`입국 카드 칸은 앱이 채우고, 제출만 직접 눌러요`)을 캡션에서 되풀이하지 않는다 — 캡션은 홈이 하는 일
        StoreShot("01_home", "나라만 고르면\n입국 준비가 한곳에") {
            HomeContent(TestPacks.homeUi(), HomeActions(), today = StoreFixture.today)
        },
        StoreShot("02_form_confirm", "입국 카드에 들어갈 값을\n한국어로 미리 확인해요") {
            val recipe = TestPacks.tdacRecipe
            val ctx = FormContext("TH_TDAC", th.value.forms.first(), recipe.value, recipe.version, false)
            val vault = StoreFixture.contents
            FormConfirmContent(
                ConfirmUi(ctx, WalletState.Unlocked(vault), FormValues.build(recipe.value, vault, StoreFixture.formDraft), StoreFixture.formDraft),
                { _, _ -> }, {}, {}, {}, {},
            )
        },
        StoreShot("03_country_entry", "비자·비용은 한눈에,\n공식 출처와 확인 날짜까지") {
            CountryContent(TestPacks.countryUi("TH"), CountryActions())
        },
        StoreShot("04_departure", "출발부터 귀국까지,\n오늘 할 일만 차례로") {
            TodayContent(
                TodayUi(
                    StoreFixture.trip, StageInfo(TripStage.Departure, dayOfTrip = 1, formWindowOpen = true), "태국",
                    th.value.forms.first(), hasPassport = true,
                ),
                TodayActions(), {}, {}, {}, {}, {},
            )
        },
        StoreShot("05_help", "인터넷 없이도\n긴급 번호와 현지어 문장") {
            HelpContent(TestPacks.helpUi(), {}, {}, {})
        },
        StoreShot("06_my_info", "여권 정보는 암호화해서\n이 휴대폰 안에만") {
            WalletContent(
                state = WalletState.Unlocked(StoreFixture.contents), deviceSecure = true, autoDestroy = true, today = StoreFixture.today,
                onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
            )
        },
        StoreShot("07_transport", "기사님께는 현지어 주소를\n크게 보여 주세요") {
            val place = StoreFixture.place
            TransportContent(
                TransportUi(
                    places = listOf(place),
                    selected = place,
                    // 운영 화면과 같이 지도 앱은 차 부르기 목록에서 뺀다(TransportViewModel)
                    apps = th.value.transportApps.filter { it.linkType != "maps_url" }.map { RideAppRow(it, installed = true) },
                    mapsInstalled = true,
                    // 부탁 문장도 팩의 실제 문장(`이 주소로 가 주세요`)
                    driverPhrase = th.value.phrases.firstOrNull { it.id == "address" }?.local,
                ),
                null, { _, _ -> }, {}, {}, {},
            )
        },
        StoreShot("08_easy_traveling", "글자와 버튼을 크게,\n쉬운 모드", easy = true) {
            TodayContent(
                TodayUi(StoreFixture.trip, StageInfo(TripStage.Traveling, dayOfTrip = 2), "태국", th.value.forms.first(), hasPassport = true),
                TodayActions(), {}, {}, {}, {}, {},
            )
        },
    )

    @Test fun phoneScreenshots() {
        val list = shots()
        var current by mutableIntStateOf(0)
        rule.setContent { list[current].let { StoreFrame(it.caption, it.easy, it.content) } }
        val dir = File("build/store").apply { mkdirs() }
        // 예전 이름(screenshot_02_country 등)이 남아 Play 콘솔에 잘못 올라가지 않게 지난 스크린숏을 지운다
        dir.listFiles { f -> f.name.startsWith("screenshot_") }?.forEach { it.delete() }
        val context = ApplicationProvider.getApplicationContext<Application>()
        // 스토어 픽스처에 없어야 하는 것: 표본 외국 이름·번호, 만료·오류 상태 (브랜드 검토 5)
        val forbidden = listOf("ERIKSSON", "L898902C3", context.getString(R.string.wallet_passport_expired), context.getString(R.string.present_image_missing))
        list.forEachIndexed { i, shot ->
            rule.runOnIdle { current = i }
            rule.mainClock.advanceTimeBy(2_000)
            rule.waitForIdle()
            forbidden.forEach { bad ->
                assertEquals("${shot.name}: $bad", 0, rule.onAllNodesWithText(bad, substring = true).fetchSemanticsNodes().size)
            }
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            val long = maxOf(bmp.width, bmp.height).toDouble()
            val short = minOf(bmp.width, bmp.height).toDouble()
            assertTrue("Play 비율 규칙(9:16) 위반: ${bmp.width}x${bmp.height}", bmp.width * 16 == bmp.height * 9 && short >= 320 && long <= 3840)
            assertEquals("스크린숏 크기", "1215x2160", "${bmp.width}x${bmp.height}")
            bmp.saveTo("screenshot_${shot.name}.png")
        }
        assertEquals(list.size, dir.listFiles { f -> f.name.startsWith("screenshot_") }?.size)
    }
}

/**
 * 스크린숏 한 장의 틀: 위쪽 Navy→AccentDeep 띠에 캡션(앱 밖 글 — 사진 위 문구가 아니다) + 비제휴 한 줄,
 * 아래는 실제 화면을 휴대폰 폭(405dp) 그대로 그려 띠 아래 칸에 맞춰 줄인 것(위 모서리만 둥글게, 아래는 화면이 이어지듯 잘림).
 */
@Composable
private fun StoreFrame(caption: String, easy: Boolean, content: @Composable () -> Unit) {
    ReadyPortTheme(easyMode = false) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Tokens.Navy, Tokens.AccentDeep))),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 30.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 어절 단위 줄바꿈(`채/우고`처럼 낱말 가운데서 꺾이지 않게). 캡처 환경(Robolectric)의 어절 줄바꿈(WordBreak.Phrase)에 기대지 않고
                // API 33 미만과 같은 보정(낱말 안 WORD JOINER)을 언제나 넣는다 — 그림 파일이라 의미 글자 문제는 없다
                KoText(
                    caption,
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Tokens.Surface,
                    display = keepWords(caption, sdk = Build.VERSION_CODES.S),
                )
                KoText(NOT_AFFILIATED, MaterialTheme.typography.labelLarge, color = Tokens.White85, display = keepWords(NOT_AFFILIATED, sdk = Build.VERSION_CODES.S))
            }
            ScaledPhone(Modifier.padding(horizontal = 26.dp).weight(1f)) {
                ReadyPortTheme(easyMode = easy) {
                    Box(Modifier.fillMaxSize().background(Tokens.Ground)) { content() }
                }
            }
        }
    }
}

/**
 * 실제 화면을 [PhoneWidth] 폭으로 재고 그린 뒤, 받은 칸에 맞게 같은 비율로 줄인다 —
 * 화면 안 반응형 판정·줄바꿈은 휴대폰 폭 그대로이고, 캡션 띠만큼 줄어든 그림이 된다.
 */
@Composable
private fun ScaledPhone(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .layout { measurable, constraints ->
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val innerW = PhoneWidth.roundToPx()
                val scale = w.toFloat() / innerW
                val innerH = (h / scale).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(innerW, innerH))
                layout(w, h) {
                    placeable.placeWithLayer(0, 0) {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            },
    ) { content() }
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
