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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.autofill.FormValues
import com.readyport.board.BoardKind
import com.readyport.board.BoardPost
import com.readyport.stay.Stays
import com.readyport.trip.Checklist
import com.readyport.trip.ChecklistData
import com.readyport.trip.CustomItem
import com.readyport.trip.PassportValidity
import com.readyport.trip.Trip
import com.readyport.trip.TripChecks
import com.readyport.ui.board.BoardHomeContent
import com.readyport.ui.board.BoardHomeUi
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
import com.readyport.ui.home.HomeTrip
import com.readyport.ui.home.HomeUi
import com.readyport.ui.pack.HelpContent
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import com.readyport.trip.TripStages
import com.readyport.ui.trip.ChecklistActions
import com.readyport.ui.trip.JOURNEY_ALL_FOLDED
import com.readyport.ui.trip.JourneyUi
import com.readyport.ui.trip.TripJourneyContent
import com.readyport.ui.wallet.WalletContent
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.StayRecord
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
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Play 스토어 등록 이미지 (M10, 재검토 R20, 운영자 결정 '+' — 나라 섞기). 결과: app/build/store/ (커밋은 docs/play/store/ 로 **같은 이름** 복사)
 * - 스크린숏: 1215×2160 = 정확히 9:16 (Play 콘솔 규칙: 16:9 또는 9:16, 320~3840px)
 * - 0.6.0 순서: 둘러보기(내 여행 흰 박스 ①②·아홉 나라 타일) → 한 여행 화면(**태국** — 번호 붙은 여행 과정 8단계)
 *   → 입국 카드 확인(**태국** TDAC) → 공항에 도착하면(**싱가포르** 창이 — 자동 심사대 ✅) → 여행자 게시판 Q&A(**말레이시아**·**베트남** 태그 질문)
 *   → 나라 화면(**인도네시아** — 그림 메뉴·도착비자 타일) → 내 정보(여권, 가림) → 도움(**일본** 긴급 번호).
 * - 0.6.0에서 바뀐 것: 05 묵는 곳(말레이시아 숙소 두 곳) → 05 게시판 Q&A. 묵는 곳 장은 주소·링크 글자뿐이라 여덟 장 중 가장 약했고
 *   (숙소 주소를 입국 카드에 채워 주는 가치는 03 입국 카드 장이 이미 보여 준다), 말레이시아는 게시판 질문의 나라 태그가 이어받는다.
 * - 0.4.0에서 바뀐 것: 02 체크리스트 → 여행 과정 8단계(01도 히어로가 바뀌었다), 공항·묵는 곳 두 장을 넣고
 *   이동하기(말레이시아 기사님 카드)·쉬운 모드(싱가포르 여행 중) 두 장을 뺐다 — 말레이시아는 묵는 곳 장이,
 *   싱가포르는 공항 장이 이어받아 나라는 그대로 다섯이다(재검토2 ⑤#1). 일본은 자동 입력이 없어 입국 카드 장에 쓰지 않는다.
 *   영상 화면(YouTube 썸네일)은 쓰지 않는다.
 * - 각 장 = 위쪽 띠의 앱 밖 캡션(두 줄 + `정부 기관과 제휴하지 않은 앱이에요`) + 아래 실제 화면(축소).
 * - 정책 문장·공항 순서·번호·현지어 문장은 모두 저장소의 서명된 실제 팩 값 — 스크린숏용으로 지어낸 문장이 없다.
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
    /** 모든 장이 같은 날 본 화면이다: 태국 여행 출발(11월 3일) 3일 전 */
    val today: LocalDate = LocalDate.of(2026, 10, 31)

    /** 태국 여행(02 여행 과정·03 입국 카드) */
    val trip = Trip("TH", "2026-11-03", "2026-11-07", id = "store-th")

    /** 말레이시아 여행(01 둘러보기의 둘째 여행 박스) — 날짜가 이어지는 숙소 두 곳이 있는 다음 여행 */
    val tripMy = Trip("MY", "2026-11-20", "2026-11-24", id = "store-my")

    val passport = PassportRecord(
        surname = "HONG", givenNames = "GILDONG", documentNumber = "M12345678",
        nationality = "KOR", issuingState = "KOR", birthDate = "1985-03-15", sex = "M",
        expiryDate = "2034-05-20", source = "mrz", mrzVerified = true, savedAt = "2026-10-01T10:00",
    )

    /**
     * 묵는 곳 두 곳(0.5.0의 05 장 — 0.6.0에서 게시판 장으로 바꿨고, 내 정보 보관함 견본에는 그대로 둔다) — 말레이시아 여행 11월 20일~24일을 **날짜별로 나눈** 견본 숙소.
     * 이름은 누가 봐도 견본이고 주소는 거리·도시 이름만이다(실제 숙소 주소·예약 정보가 아니다).
     */
    val stays = listOf(
        StayRecord(
            id = "store-stay-1", tripId = "store-my", name = "쿠알라룸푸르 숙소",
            addressLocal = "Jalan Bukit Bintang, Kuala Lumpur", addressKo = "부킷빈탕 거리",
            checkIn = "2026-11-20", checkOut = "2026-11-22", type = "hotel", savedAt = "2026-10-01T10:00",
        ),
        StayRecord(
            id = "store-stay-2", tripId = "store-my", name = "말라카 숙소",
            addressLocal = "Jalan Hang Jebat, Melaka", addressKo = "말라카 구도심",
            checkIn = "2026-11-22", checkOut = "2026-11-24", type = "guest_house", savedAt = "2026-10-01T10:05",
        ),
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
        stays = stays,
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

    /**
     * 한 일(01·02): 비자·예약은 체크, 여권 남은 기간·여권 등록은 앱이 확인(견본 만료일로 실제 계산),
     * 여행자 보험·여행경보·데이터는 아직 — 늦은 항목·오류 없이 한 일과 남은 일이 섞인 모습. 내 항목 하나(견본).
     */
    val marks = listOf("visa", "booking").associateWith { true }
    val custom = listOf(CustomItem("custom.s1", "우산 챙기기"))

    /**
     * 게시판 Q&A(05) — **모두 지어낸 글·닉네임**(실제 이용자·실제 글이 아니다). 고정 글은 운영자가 실제로 올린 이용 안내의
     * 제목·첫 문단(tools/board/guide_posts.py)이고 운영자 게시판 ID는 견본이다. 질문은 묻는 말뿐 — 정책을 단정하는 문장이 없다.
     */
    val boardNow: Instant = Instant.parse("2026-10-31T03:00:00Z")
    private const val BOARD_OPERATOR = "store-operator"
    val boardUi: BoardHomeUi
        get() {
            fun ago(minutes: Long) = boardNow.minusSeconds(minutes * 60)
            val guide = BoardPost(
                id = "store-guide", kind = BoardKind.Qna, title = "Q&A 이용 안내 · 좋은 질문 쓰는 법",
                body = "레디포트 Q&A는 입국 서류·비자·공항이 궁금할 때 먼저 다녀온 여행자에게 묻는 곳이에요.",
                country = null, authorUid = BOARD_OPERATOR, nickname = "레디포트 운영자",
                createdAt = Instant.parse("2026-10-09T00:00:00Z"), likeCount = 14, pinned = true,
            )
            val solved = BoardPost(
                id = "store-q1", kind = BoardKind.Qna, title = "MDAC, 아이 것도 따로 내야 하나요?",
                body = "다음 달에 아이와 쿠알라룸푸르에 가요. 먼저 다녀오신 분들은 어떻게 내셨어요?",
                country = "MY", authorUid = "store-uid-1", nickname = "말라카 가는 길",
                createdAt = ago(60 * 5), commentCount = 4, likeCount = 6, solved = true, acceptedId = "store-c1",
            )
            val waiting = BoardPost(
                id = "store-q2", kind = BoardKind.Qna, title = "다낭 공항에서 시내까지 밤에 어떻게 가셨어요?",
                body = "밤 11시 도착이라 차편이 걱정돼요.",
                country = "VN", authorUid = "store-uid-2", nickname = "느긋한 여행자",
                createdAt = ago(40), commentCount = 0, likeCount = 1,
            )
            return BoardHomeUi(
                loading = false, pinned = listOf(guide), posts = listOf(solved, waiting),
                admins = setOf(BOARD_OPERATOR), now = boardNow, ready = true,
            )
        }

    /** 그 여행의 체크리스트 — 둘러보기 흰 박스의 진행과 여행 화면 머리가 **같은 값**을 쓰도록 한 번만 센다 */
    fun checklist(t: Trip): ChecklistData {
        val pack = runBlocking { TestPacks.repo.pack(t.country)!! }.value
        val checks = TripChecks(
            marks = marks,
            custom = custom,
            passport = PassportValidity.check(
                LocalDate.parse(passport.expiryDate), t, pack.requirements.firstOrNull()?.passportValidity,
            ),
        )
        return Checklist.build(
            Checklist.Input(t, TestPacks.index.value, pack, checks, passportSaved = true, today = today),
        )
    }

    /** 둘러보기 히어로의 내 여행 흰 박스 둘(01) — 번호 ① 태국(사흘 뒤), ② 말레이시아. 진행은 실제 체크리스트 값 */
    fun homeUi(): HomeUi {
        val th = checklist(trip)
        val my = checklist(tripMy)
        return TestPacks.homeUi().copy(
            trips = listOf(
                HomeTrip("태국", trip.start, trip.end, code = trip.country, id = trip.id, checklistDone = th.done, checklistTotal = th.total),
                HomeTrip("말레이시아", tripMy.start, tripMy.end, code = tripMy.country, id = tripMy.id, checklistDone = my.done, checklistTotal = my.total),
            ),
            activeTrips = 2,
        )
    }

    /** 한 여행 화면의 값 — 02(태국) */
    fun journeyUi(t: Trip): JourneyUi {
        val pack = runBlocking { TestPacks.repo.pack(t.country)!! }.value
        return JourneyUi(
            loaded = true,
            trip = t,
            countryName = pack.names.ko,
            data = checklist(t),
            today = today,
            // 찍는 시각에 따라 `오늘·내일 아침 9시`가 바뀌지 않게 — 모든 장이 10월 31일 낮 12시(게시판 장의 boardNow와 같은 때)에 본 화면
            nowHour = 12,
            stage = TripStages.compute(t, today, 0L, pack.requiredForms.firstOrNull()?.windowDaysIncludingArrival),
            form = pack.requiredForms.firstOrNull(),
            indexSources = TestPacks.index.value.sources.associate { it.id to it.name },
            sourceNames = pack.sources.associate { it.id to it.name },
            airport = pack.airports.firstOrNull(),
            hasAirports = pack.airports.isNotEmpty(),
            hasShopping = pack.shopping.isNotEmpty(),
            stays = Stays.forTrip(stays, t),
            stayNotes = Stays.notes(stays, t),
        )
    }
}

/**
 * 스크린숏 한 장: 파일 이름, 캡션(줄은 뜻 단위로 직접 나눈다), 쉬운 모드인지, 화면.
 * [scrollKey]: 찍기 전에 화면 목록을 그 항목까지 내린다(맨 위가 아닌 장면을 보여 줄 때 — 화면 자체는 그대로).
 * [scrollMoreDp]: 그 항목으로 내린 뒤 더 움직이는 양 — 양수는 더 내리고(카드 한 장이 길어 보여 줄 부분이 아래쪽에 있을 때),
 *   음수는 되돌린다(나라 화면의 고정 줄이 카드 머리를 가리지 않게).
 */
private class StoreShot(
    val name: String,
    val caption: String,
    val easy: Boolean = false,
    val scrollKey: String? = null,
    val scrollMoreDp: Int = 0,
    val content: @Composable () -> Unit,
)

/** 모든 장 아래 줄 (스토어 등록 정보·그래픽 이미지와 같은 뜻의 비제휴 문구) */
private const val NOT_AFFILIATED = "정부 기관과 제휴하지 않은 앱이에요"

/** 게시판 장(05): 고정 글로 내린 뒤 조금 더 — 찾기 칸 테두리가 위 모서리에 걸리지 않게 */
private const val BOARD_SCROLL = 4

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
        // 둘러보기 히어로(0.5.0): 소개 한 줄 → 내 여행 흰 박스 둘(둥근 번호 ①②, 나라 이름·출발까지·날짜·진행 막대)
        // → `새 여행 만들기` → 아홉 나라 사진 타일. 캡션은 이 화면이 하는 일 그대로(차별점 한 줄은 그래픽 이미지가 맡는다)
        StoreShot("01_home", "여행을 만들면\n할 일을 차례로 알려 줘요") {
            HomeContent(StoreFixture.homeUi(), HomeActions(), today = StoreFixture.today)
        },
        // 태국 한 여행 화면(0.5.0의 가장 큰 변화) 맨 위: 여행 이름·날짜 → 사진 머리의 전체 진행 → `지금 할 일`(그 단계를 바로 열어 준다)
        // → `못한 일 알림` → 번호 배지가 붙은 단계 카드(1단계 계획, `지금` 태그). 단계는 모두 접어 두어 여덟 칸이 차례로 이어진다
        StoreShot("02_journey", "계획부터 복귀까지\n여행 과정 8단계로") {
            TripJourneyContent(StoreFixture.journeyUi(StoreFixture.trip), ChecklistActions(), openAtFirst = JOURNEY_ALL_FOLDED)
        },
        StoreShot("03_form_confirm", "입국 카드에 들어갈 값을\n한국어로 미리 확인해요") {
            val recipe = TestPacks.tdacRecipe
            val ctx = FormContext("TH_TDAC", th.value.forms.first(), recipe.value, recipe.version, false)
            val vault = StoreFixture.contents
            FormConfirmContent(
                ConfirmUi(ctx, WalletState.Unlocked(vault), FormValues.build(recipe.value, vault, StoreFixture.formDraft), StoreFixture.formDraft),
                { _, _ -> }, {}, {}, {}, {},
            )
        },
        // 싱가포르 창이(0.5.0 신규): 나라 › 입국·비자의 `공항에 도착하면` 카드 — 공항 머리 → 번호 단계(팩 문장 그대로)
        // → 자동 심사대 ✅ 줄과 공식 메모. 아홉 나라 중 자동 심사대가 **조건 없이 ✅**인 곳이라 이 장에 썼다(ICA 안내)
        StoreShot("04_airport", "공항에 도착하면\n어디서 무엇을 할지", scrollKey = "airports", scrollMoreDp = -55) {
            CountryContent(TestPacks.countryUi("SG"), CountryActions())
        },
        // 여행자 게시판 Q&A(0.6.0 신규): 운영자 고정 이용 안내 → `해결됨` 질문(말레이시아) → `답변 기다려요` 질문(베트남).
        // 글·닉네임은 모두 지어낸 견본(StoreFixture.boardUi). 고정 글까지 내려 글 세 장(고정·해결됨·답변 기다려요)이 한 화면에 들어오게 한다
        StoreShot("05_board", "먼저 다녀온 여행자에게\n묻고 답해요", scrollKey = "pin-store-guide", scrollMoreDp = BOARD_SCROLL) {
            BoardHomeContent(StoreFixture.boardUi)
        },
        // 인도네시아: 발리 사원 히어로(나라 이름·최종 확인 날짜) → 그림 메뉴 세 장(입국·비자·여행 정보·쇼핑) → 안심 카드 → 도착비자 카드.
        // 30일·IDR 500,000 타일이 캡션의 '비자·비용'을 그대로 보여 준다(재검토2 ①#11·⑤#7)
        StoreShot("06_country_entry", "비자·비용은 한눈에,\n공식 출처와 확인 날짜까지") {
            CountryContent(TestPacks.countryUi("ID"), CountryActions())
        },
        StoreShot("07_my_info", "여권 정보는 암호화해서\n이 휴대폰 안에만") {
            WalletContent(
                state = WalletState.Unlocked(StoreFixture.contents), deviceSecure = true, autoDestroy = true, today = StoreFixture.today,
                onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
            )
        },
        // 일본(한국인 출국 1위): 도움 탭의 긴급 번호 묶음(맨 위 `긴급 번호 바로 보기`로 가는 곳) — 110·119·118, 한국어 24시간 전화, 대사관
        StoreShot("08_help", "인터넷 없이도\n긴급 번호와 대사관 연락처", scrollKey = "emergency") {
            HelpContent(TestPacks.helpUi().copy(selected = pack("JP")), {}, {}, {})
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
            if (shot.scrollMoreDp != 0) {
                val px = with(rule.density) { shot.scrollMoreDp.dp.toPx() }
                rule.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, px) }
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
