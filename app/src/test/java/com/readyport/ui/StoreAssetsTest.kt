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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.autofill.FormValues
import com.readyport.transport.Place
import com.readyport.trip.Checklist
import com.readyport.trip.CustomItem
import com.readyport.trip.PassportValidity
import com.readyport.trip.StageInfo
import com.readyport.trip.Trip
import com.readyport.trip.TripChecks
import com.readyport.trip.TripStage
import com.readyport.ui.components.KoText
import com.readyport.ui.components.Photos
import com.readyport.ui.components.keepWords
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
import com.readyport.ui.transport.RideAppRow
import com.readyport.ui.transport.TransportContent
import com.readyport.ui.transport.TransportUi
import com.readyport.trip.JourneyStage
import com.readyport.trip.TripStages
import com.readyport.ui.trip.ChecklistActions
import com.readyport.ui.trip.JourneyUi
import com.readyport.ui.trip.TripJourneyContent
import com.readyport.ui.wallet.WalletContent
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.VaultContents
import com.readyport.vault.WalletState
import kotlinx.coroutines.runBlocking
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
 * Play 스토어 등록 이미지 (M10, 재검토 R20, 운영자 결정 '+' — 나라 섞기·1번 캡션). 결과: app/build/store/ (커밋은 docs/play/store/ 로 **같은 이름** 복사)
 * - 스크린숏: 1215×2160 = 정확히 9:16 (Play 콘솔 규칙: 16:9 또는 9:16, 320~3840px)
 * - 순서: 홈 → 입국 카드 확인(태국 TDAC) → 나라 화면(**인도네시아** — 히어로 아래 그림 메뉴 세 장, 입국·비자) → 여행 체크리스트(태국, 0.4.0)
 *   → 도움(**일본**) → 내 정보(여권, 가림) → 이동하기(**말레이시아** 기사님 카드) → 쉬운 모드 여행 중(**싱가포르**).
 *   한 나라(태국)가 8장 중 7장이던 것을 다섯 나라로 나눴다(재검토2 ⑤#1). 일본은 자동 입력이 없어 02에 쓰지 않는다.
 *   영상 화면(YouTube 썸네일)은 쓰지 않는다.
 * - 각 장 = 위쪽 띠의 앱 밖 캡션(두 줄 + `정부 기관과 제휴하지 않은 앱이에요`) + 아래 실제 화면(축소).
 * - 정책 문장·번호·현지어 문장은 모두 저장소의 서명된 실제 팩 값 — 스크린숏용으로 지어낸 문장이 없다.
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

    /** 가는 곳(말레이시아 — 07): 거리·도시 이름만 있는 견본 주소(실제 숙소 주소가 아님) */
    val placeMy = Place("p1", "쿠알라룸푸르 숙소", "Jalan Bukit Bintang, Kuala Lumpur")

    /** 쉬운 모드 여행 중(싱가포르 — 08): 같은 날짜의 견본 여행 */
    val tripSg = Trip("SG", "2026-11-03", "2026-11-07")

    /**
     * 여행 체크리스트(태국 — 04): 같은 태국 여행, 오늘 10월 20일(출발 14일 전 = '떠나기 한 달 전쯤' 단계).
     * 여권 남은 기간·여권 등록은 앱이 확인(견본 여권 만료일로 실제 계산), 비자·예약은 체크, 여행자 보험·여행경보·데이터는 아직 —
     * 늦은 항목·오류 없이 한 일과 남은 일이 섞인 모습. 내 항목 하나(견본).
     */
    val checklistToday: LocalDate = LocalDate.of(2026, 10, 20)
    val checklistTrip = Trip("TH", "2026-11-03", "2026-11-07", id = "store-th")
    val checklistMarks = listOf("visa", "booking").associateWith { true }
    val checklistCustom = listOf(CustomItem("custom.s1", "우산 챙기기"))
}

/**
 * 스크린숏 한 장: 파일 이름, 캡션(줄은 뜻 단위로 직접 나눈다), 쉬운 모드인지, 화면.
 * [scrollKey]: 찍기 전에 화면 목록을 그 항목까지 내린다(맨 위가 아닌 장면을 보여 줄 때 — 화면 자체는 그대로).
 */
private class StoreShot(
    val name: String,
    val caption: String,
    val easy: Boolean = false,
    val scrollKey: String? = null,
    val content: @Composable () -> Unit,
)

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
    private fun pack(code: String) = runBlocking { TestPacks.repo.pack(code)!! }

    private fun shots(): List<StoreShot> = listOf(
        // 1번 캡션 = 앱의 차별점 그대로(운영자 결정 '+') — 스토어 사용자는 화면 안 글보다 캡션을 읽는다
        StoreShot("01_home", "칸은 앱이 채워요\n제출만 직접") {
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
        // 인도네시아: 발리 사원 히어로(나라 이름·최종 확인 날짜) → 그림 메뉴 세 장(입국·비자·여행 정보·쇼핑) → 안심 카드 → 도착비자 카드.
        // 30일·IDR 500,000 타일이 캡션의 '비자·비용'을 그대로 보여 준다(재검토2 ①#11·⑤#7)
        StoreShot("03_country_entry", "비자·비용은 한눈에,\n공식 출처와 확인 날짜까지") {
            CountryContent(TestPacks.countryUi("ID"), CountryActions())
        },
        // 태국 한 여행 화면(0.5.0): 사진 머리(전체 진행) → 여행 과정 8단계 막대 → 지금 할 일 → 단계 카드
        StoreShot("04_checklist", "계획부터 복귀까지\n여행 과정 그대로 안내") {
            val t = StoreFixture.checklistTrip
            val today = StoreFixture.checklistToday
            val pack = th.value
            val checks = TripChecks(
                marks = StoreFixture.checklistMarks,
                custom = StoreFixture.checklistCustom,
                passport = PassportValidity.check(LocalDate.parse(StoreFixture.passport.expiryDate), t, pack.requirements.first().passportValidity),
            )
            val data = Checklist.build(Checklist.Input(t, TestPacks.index.value, pack, checks, passportSaved = true, today = today))
            val pack2 = th.value
            TripJourneyContent(
                JourneyUi(
                    loaded = true, trip = t, countryName = "태국", data = data, today = today,
                    stage = TripStages.compute(t, today, 0L, pack2.requiredForms.firstOrNull()?.windowDaysIncludingArrival),
                    form = pack2.requiredForms.firstOrNull(),
                    indexSources = TestPacks.index.value.sources.associate { it.id to it.name },
                    sourceNames = pack2.sources.associate { it.id to it.name },
                    airport = pack2.airports.firstOrNull(),
                    hasAirports = pack2.airports.isNotEmpty(),
                    hasShopping = pack2.shopping.isNotEmpty(),
                ),
                ChecklistActions(),
            )
        },
        // 일본(한국인 출국 1위): 도움 탭의 긴급 번호 묶음(맨 위 `긴급 번호 바로 보기`로 가는 곳) — 110·119·118, 한국어 24시간 전화, 대사관.
        // 현지어 문장을 크게 보여 주는 장면은 07 기사님 카드가 맡는다
        StoreShot("05_help", "인터넷 없이도\n긴급 번호와 대사관 연락처", scrollKey = "emergency") {
            HelpContent(TestPacks.helpUi().copy(selected = pack("JP")), {}, {}, {})
        },
        StoreShot("06_my_info", "여권 정보는 암호화해서\n이 휴대폰 안에만") {
            WalletContent(
                state = WalletState.Unlocked(StoreFixture.contents), deviceSecure = true, autoDestroy = true, today = StoreFixture.today,
                onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
            )
        },
        // 말레이시아: 말레이어 부탁 문장 + 한국어 뜻 + 견본 주소, 차 부르기(Grab·Bolt)
        StoreShot("07_transport", "기사님께는 현지어 주소를\n크게 보여 주세요") {
            val place = StoreFixture.placeMy
            val my = pack("MY").value
            val phrase = my.phrases.firstOrNull { it.id == "address" }
            TransportContent(
                TransportUi(
                    places = listOf(place),
                    selected = place,
                    // 운영 화면과 같이 지도 앱은 차 부르기 목록에서 뺀다(TransportViewModel)
                    apps = my.transportApps.filter { it.linkType != "maps_url" }.map { RideAppRow(it, installed = true) },
                    mapsInstalled = true,
                    // 부탁 문장도 팩의 실제 문장(`이 주소로 가 주세요`)과 그 한국어
                    driverPhrase = phrase?.local,
                    driverPhraseKo = phrase?.ko,
                ),
                null, { _, _ -> }, {}, {}, {},
            )
        },
        // 싱가포르 여행 중, 쉬운 모드. 캡션은 기능 이름 대신 누구를 위한 것인지(재검토2 ⑤#12)
        StoreShot("08_easy_traveling", "해외여행이 처음이라면\n글자와 버튼을 크게", easy = true) {
            val sg = pack("SG").value
            val t2 = StoreFixture.tripSg
            // 여행 중(싱가포르 2일째) — 여행 중 단계의 큰 타일 넷
            val today2 = LocalDate.parse(t2.startDate).plusDays(1)
            val data2 = Checklist.build(
                Checklist.Input(t2, TestPacks.index.value, sg, TripChecks(marks = emptyMap()), passportSaved = true, today = today2),
            )
            TripJourneyContent(
                JourneyUi(
                    loaded = true, trip = t2, countryName = sg.names.ko, data = data2, today = today2,
                    stage = StageInfo(TripStage.Traveling, dayOfTrip = 2),
                    form = sg.requiredForms.firstOrNull(),
                    indexSources = TestPacks.index.value.sources.associate { it.id to it.name },
                    sourceNames = sg.sources.associate { it.id to it.name },
                    airport = sg.airports.singleOrNull(),
                    hasAirports = sg.airports.isNotEmpty(),
                    hasShopping = sg.shopping.isNotEmpty(),
                ),
                ChecklistActions(),
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
            shot.scrollKey?.let { key ->
                rule.onNode(hasScrollAction()).performScrollToKey(key)
                rule.mainClock.advanceTimeBy(2_000)
                rule.waitForIdle()
            }
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

/** 그래픽 이미지 문구 — 앱 히어로의 가치 문장과 같은 말, 해요체 (재검토2 ①#11·③#6·⑤#4) */
internal object FeatureGraphicText {
    const val VALUE = "입국 카드 칸은 앱이 채우고,\n제출만 직접 눌러요"
    const val PROMISE = "여권 정보는 이 휴대폰에만 · 인터넷 없이도 열려요"
    const val NOT_AFFILIATED = "정부 기관과 제휴하지 않은 앱이에요"
}

/** 그래픽 이미지 오른쪽 나라 사진 줄 (앱에 든 사진 — 출처는 앱 설정 › 사진·글꼴 출처, photo_credits.json) */
private val FeatureCountries = listOf(
    "TH" to "태국", "JP" to "일본", "SG" to "싱가포르",
    "MY" to "말레이시아", "ID" to "인도네시아", "TW" to "대만",
    "CN" to "중국", "PH" to "필리핀", "VN" to "베트남",
)

/**
 * 그래픽 이미지 1024×500 (Play 필수). 정부 연상 요소 없음, 비제휴 문구 포함.
 * 왼쪽 = 아이콘·이름 + 앱 히어로와 같은 가치 문장(해요체) + 약속 한 줄 + 비제휴 한 줄(20px 이상 — 폰 폭으로 줄여도 읽히게),
 * 오른쪽 = 앱에 든 아홉 나라 사진 타일(3 × 3, 이름은 사진 아래 스크림 위 흰 글자) — 빈 하늘이던 오른쪽 3분의 1을 채운다(재검토2 ⑤#4, 0.4.0에서 5 → 9).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w1024dp-h500dp-mdpi")
class StoreFeatureGraphicTest {

    @get:Rule
    val rule = createComposeRule()

    @Test fun featureGraphic() {
        val icon = BitmapFactory.decodeFile(File("../design/icons/play-store/readyport_play_512.png").path)!!.asImageBitmap()
        rule.setContent {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Tokens.Navy, Tokens.AccentDeep)))
                    .padding(start = 60.dp, end = 52.dp, top = 44.dp, bottom = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
                        Spacer(Modifier.width(18.dp))
                        Text("레디포트", color = Tokens.Surface, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(FeatureGraphicText.VALUE, color = Tokens.Surface, fontSize = 36.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(FeatureGraphicText.PROMISE, color = Tokens.Surface, fontSize = 21.sp)
                    Text(FeatureGraphicText.NOT_AFFILIATED, color = Tokens.White85, fontSize = 21.sp)
                }
                Spacer(Modifier.width(36.dp))
                CountryPhotoStrip()
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("${bmp.width}x${bmp.height}", bmp.width == 1024 && bmp.height == 500)
        bmp.saveTo("feature_graphic_1024x500.png")
    }
}

/** 나라 사진 타일 아홉 장(앱에 든 나라 전부): 3 × 3. 타일마다 아래쪽 어둡게 덮고 나라 이름 흰 글자 */
@Composable
private fun CountryPhotoStrip() {
    val tile = 120.dp
    val gap = 10.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
        FeatureCountries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { (code, name) ->
                    Box(Modifier.width(tile).height(tile * 1.05f).clip(RoundedCornerShape(16.dp)).background(Tokens.Navy)) {
                        Image(
                            painterResource(Photos.country(code)!!),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            // 태국 사진은 사원(왼쪽 아래)이 타일 안에 들어오게 — 나머지는 가운데
                            alignment = if (code == "TH") BiasAlignment(-1f, 0.4f) else Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Tokens.Navy.copy(alpha = 0.88f))),
                        )
                        Text(
                            name,
                            color = Tokens.Surface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
