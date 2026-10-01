package com.readyport.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import com.readyport.R
import com.readyport.autofill.FormValues
import com.readyport.doc.booking.BookingExtractor
import com.readyport.doc.mrz.MrzParser
import com.readyport.pack.EssentialRule
import com.readyport.prep.Essentials
import com.readyport.transport.Place
import com.readyport.trip.StageInfo
import com.readyport.trip.Trip
import com.readyport.trip.TripStage
import com.readyport.ui.components.PhotoCredit
import com.readyport.ui.country.CountryActions
import com.readyport.ui.country.CountryContent
import com.readyport.ui.country.CountrySection
import com.readyport.ui.form.AutofillUi
import com.readyport.ui.form.ConfirmUi
import com.readyport.ui.form.FormConfirmContent
import com.readyport.ui.form.FormContext
import com.readyport.ui.form.ManualModeContent
import com.readyport.ui.home.HomeActions
import com.readyport.ui.home.HomeContent
import com.readyport.ui.home.HomeTrip
import com.readyport.ui.onboarding.FirstRunScreen
import com.readyport.ui.pack.HelpContent
import com.readyport.ui.pack.ShoppingContent
import com.readyport.ui.pack.ShoppingUi
import com.readyport.ui.prep.EssentialRow
import com.readyport.ui.prep.EssentialsContent
import com.readyport.ui.prep.EssentialsUi
import com.readyport.ui.present.CompanionsContent
import com.readyport.ui.present.DocView
import com.readyport.ui.present.PresentContent
import com.readyport.ui.present.PresentUi
import com.readyport.ui.present.Traveler
import com.readyport.ui.settings.PhotoCreditsContent
import com.readyport.ui.settings.SettingsScreen
import com.readyport.ui.tabs.essentialsSummary
import com.readyport.ui.tabs.PrepareContent
import com.readyport.ui.today.TodayActions
import com.readyport.ui.today.TodayContent
import com.readyport.ui.today.TodayUi
import com.readyport.ui.transport.RideAppRow
import com.readyport.ui.transport.TransportContent
import com.readyport.ui.transport.TransportUi
import com.readyport.ui.trip.TripContent
import com.readyport.ui.trip.TripFormUi
import com.readyport.ui.video.LocalThumbnailLoader
import com.readyport.ui.video.VideosContent
import com.readyport.ui.video.VideosState
import com.readyport.ui.wallet.BookingImportContent
import com.readyport.ui.wallet.ImportState
import com.readyport.ui.wallet.PassportConfirmContent
import com.readyport.ui.wallet.PassportIntroContent
import com.readyport.ui.wallet.ScanState
import com.readyport.ui.wallet.WalletContent
import com.readyport.vault.BookingRecord
import com.readyport.vault.EntryDoc
import com.readyport.vault.PassportRecord
import com.readyport.vault.VaultContents
import com.readyport.vault.WalletState
import com.readyport.video.Video
import java.time.LocalDate

/**
 * 앱의 모든 화면(상태 없는 Content)을 가짜 값으로 모은 목록.
 * - A11yAuditTest: 터치 영역·이름 점검
 * - GalleryCaptureTest: 디자인 검토용 전체 길이 캡처 (build/gallery)
 * 여권은 ICAO 표본 값만 쓴다.
 */
object Gallery {
    private val index get() = TestPacks.index.value
    private val th get() = TestPacks.thailand
    private val trip = Trip("TH", "2026-11-03", "2026-11-07")

    /** 꼭 챙길 물건 중 챙긴 것(진행 2 / 5) */
    private val gotItems = setOf("passport", "medicine")

    /** 출처 id → 이름 (운영 ViewModel과 같은 방식). 픽스처에서 내부 ID가 화면에 보이지 않게 한다 */
    private val indexSources get() = index.sources.associate { it.id to it.name }
    private val thSources get() = th.value.sources.associate { it.id to it.name }

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

    val videos = listOf(
        Video(id = "AAAAAAAAAA1", title = "방콕 3박 4일 여행 브이로그 | 왓아룬 야경", channelTitle = "여행채널", publishedAt = "2026-09-01T00:00:00Z",
            viewCount = 1234567, subscriberCount = 89000, durationSeconds = 754, thumbnail = "https://i.ytimg.com/vi/AAAAAAAAAA1/mqdefault.jpg"),
        Video(id = "AAAAAAAAAA2", title = "태국 여행 준비물 총정리", channelTitle = "채널2", publishedAt = "2026-08-11T00:00:00Z",
            viewCount = 45210, subscriberCount = null, durationSeconds = 3725, thumbnail = "https://i.ytimg.com/vi/AAAAAAAAAA2/mqdefault.jpg"),
    )

    /** [thumb]: 영상 썸네일 대역(null 이면 빈 칸) */
    fun screens(thumb: ImageBitmap? = null): List<Pair<String, @Composable () -> Unit>> = listOf(
        "first-run" to { FirstRunScreen {} },
        // 준비물 진행 줄(2 / 5)까지 보이게 (BUNDLE_A_NOTES 요청 7)
        // 꼭 챙길 물건 값 칩(기내 반입만 보조배터리)·진행 2 / 5 — 운영 ViewModel과 같은 계산(essentialsSummary)
        "home" to { HomeContent(TestPacks.homeUi().copy(essentials = essentialsSummary(index, null, gotItems)), HomeActions(), today = LocalDate.of(2026, 9, 28)) },
        "home-with-trip" to {
            HomeContent(
                TestPacks.homeUi().copy(
                    trip = HomeTrip("태국", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 7), code = "TH"),
                    essentials = essentialsSummary(index, th.value, gotItems),
                ),
                HomeActions(), today = LocalDate.of(2026, 10, 31),
            )
        },
        // 내 여행(태국 11월 3일)이 있으면 입국 카드 '내는 때'가 일반 예시 대신 내 날짜
        "country-entry-TH" to { CountryContent(TestPacks.countryUi("TH").copy(tripArrival = trip.start), CountryActions()) },
        "country-entry-ID-visa" to { CountryContent(TestPacks.countryUi("ID"), CountryActions()) },
        "country-travel" to { CountryContent(TestPacks.countryUi("TH", favorite = true), CountryActions(), CountrySection.Travel) },
        "country-shopping" to { CountryContent(TestPacks.countryUi("JP"), CountryActions(), CountrySection.Shopping) },
        "videos" to {
            CompositionLocalProvider(LocalThumbnailLoader provides { thumb }) {
                VideosContent("태국", VideosState.Ready(videos), {})
            }
        },
        "videos-offline" to { VideosContent("태국", VideosState.Unavailable, {}) },
        "today-none" to { TodayContent(TodayUi(), TodayActions(), {}, {}, {}, {}, {}) },
        "today-preparing" to {
            TodayContent(TodayUi(trip, StageInfo(TripStage.Preparing, daysLeft = 3, formWindowOpen = true), "태국", th.value.forms.first(), true),
                TodayActions(), {}, {}, {}, {}, {})
        },
        "today-departure" to {
            TodayContent(TodayUi(trip, StageInfo(TripStage.Departure, dayOfTrip = 1), "태국", th.value.forms.first(), true),
                TodayActions(), {}, {}, {}, {}, {})
        },
        // 출국일에 입국 카드 기간이 열린 상태(태국 TDAC는 보통 이 상태) — 주 버튼은 입국 카드 하나, `도착했어요`는 보조 (C 묶음 캡처를 공용 갤러리로)
        "today-departure-form" to {
            TodayContent(
                TodayUi(trip, StageInfo(TripStage.Departure, dayOfTrip = 1, formWindowOpen = true), "태국", th.value.forms.first(), true),
                TodayActions(), {}, {}, {}, {}, {},
            )
        },
        "today-arrival" to {
            TodayContent(TodayUi(trip, StageInfo(TripStage.Arrival, dayOfTrip = 1), "태국", th.value.forms.first(), true),
                TodayActions(), {}, {}, {}, {}, {})
        },
        "today-traveling" to {
            TodayContent(TodayUi(trip, StageInfo(TripStage.Traveling, dayOfTrip = 2), "태국", th.value.forms.first(), true),
                TodayActions(), {}, {}, {}, {}, {})
        },
        "today-return" to {
            TodayContent(
                TodayUi(trip, StageInfo(TripStage.Return, askDestroy = true), "태국", null, true,
                    cart = th.value.shopping, returnLinks = index.returnLinks, returnFacts = index.returnFacts,
                    indexSources = indexSources, sourceNames = thSources),
                TodayActions(), {}, {}, {}, {}, {},
            )
        },
        "today-wrapup" to {
            TodayContent(TodayUi(trip, StageInfo(TripStage.WrapUp), "태국", null, true), TodayActions(), {}, {}, {}, {}, {})
        },
        "trip-edit" to { TripContent(TripFormUi(index.countries.filter { it.pack }, trip, loaded = true), { _, _, _ -> }, {}) },
        "prepare" to {
            val days = th.value.forms.associate { it.id to it.windowDaysIncludingArrival }
            PrepareContent(
                TestPacks.formEntries().map { it.copy(windowDays = days[it.formId], tripArrival = trip.start) }, {},
                essentialsSummary(index, th.value, gotItems),
            )
        },
        "essentials" to {
            val rules: List<EssentialRule> = Essentials.select(index.essentials, index.homePower, th.value.power)
            EssentialsContent(
                EssentialsUi("태국", 4, 11, rules.mapIndexed { i, r -> EssentialRow(r, i < 2, r.source?.let { indexSources[it] }) }),
                { _, _ -> }, {},
            )
        },
        "form-confirm" to {
            val recipe = TestPacks.tdacRecipe
            val draft = mapOf("trip.purpose" to "tourism", "profile.country_res" to "대한민국")
            val ctx = FormContext("TH_TDAC", th.value.forms.first(), recipe.value, recipe.version, false)
            FormConfirmContent(ConfirmUi(ctx, WalletState.Unlocked(contents), FormValues.build(recipe.value, contents, draft), draft),
                { _, _ -> }, {}, {}, {}, {})
        },
        "manual-mode" to {
            val recipe = TestPacks.tdacRecipe
            val ctx = FormContext("TH_TDAC", th.value.forms.first(), recipe.value, recipe.version, false)
            ManualModeContent(AutofillUi(context = ctx, values = FormValues.build(recipe.value, contents, emptyMap())), {}, { _, _ -> }, {})
        },
        "shopping" to {
            ShoppingContent(
                ShoppingUi(
                    "TH", "태국", th.value.shopping, sourceNames = thSources,
                    returnLinks = index.returnLinks, returnFacts = index.returnFacts, indexSources = indexSources,
                ),
                { _, _ -> }, {},
            )
        },
        "transport" to {
            val place = Place("p1", "방콕 숙소", "สุขุมวิท ซอย 11 กรุงเทพฯ")
            TransportContent(
                TransportUi(listOf(place), place, th.value.transportApps.map { RideAppRow(it, installed = false) }, false, "กรุณาพาไปที่นี่"),
                null, { _, _ -> }, {}, {}, {},
            )
        },
        "help" to { HelpContent(TestPacks.helpUi(), {}, {}, {}) },
        "present" to { PresentContent(PresentUi(locked = true), {}, {}, {}, {}) },
        "present-unlocked" to {
            val doc = EntryDoc(
                id = "d1", formId = "TH_TDAC", travelerId = "self", blobId = "b1", confirmationNo = "TDAC-0000",
                arrivalDate = "2026-11-03", flightNo = "KE651", source = "capture", savedAt = "2026-11-01T10:00",
            )
            PresentContent(
                PresentUi(
                    locked = false,
                    travelers = listOf(Traveler("self", stringResource(R.string.present_self))),
                    docs = listOf(DocView(doc, th.value.forms.first().nameKo, null, "E•••••• A•••", "L••••••C3")),
                ),
                {}, {}, {}, {},
            )
        },
        "settings" to { SettingsScreen(easyMode = false, onEasyModeChange = {}) },
        "wallet-locked" to {
            WalletContent(
                state = WalletState.Locked(hasData = true), deviceSecure = true, autoDestroy = true, today = LocalDate.of(2026, 9, 29),
                onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
            )
        },
        "wallet-key-lost" to {
            WalletContent(
                state = WalletState.Failed(WalletState.Failed.Reason.KeyLost), deviceSecure = true, autoDestroy = true,
                today = LocalDate.of(2026, 9, 29),
                onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
            )
        },
        "wallet-unlocked" to {
            WalletContent(
                state = WalletState.Unlocked(contents), deviceSecure = true, autoDestroy = true, today = LocalDate.of(2026, 9, 29),
                onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
            )
        },
        "passport-intro" to { PassportIntroContent(ScanState.Idle, {}, {}, {}) },
        "passport-confirm" to {
            PassportConfirmContent(
                mrz = MrzParser.parse("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", "L898902C36UTO7408122F1204159ZE184226B<<<<<10"),
                saveFailed = false, onSave = {}, onRescan = {}, onManual = {},
            )
        },
        "booking-review" to {
            BookingImportContent(
                state = ImportState.Review(BookingExtractor.extract("예약번호: ABC123\n편명 KE651 2026년 11월 3일")),
                saveFailed = false, onPickPhoto = {}, onPickPdf = {}, onText = {}, onSave = {}, onRestart = {}, onDone = {},
            )
        },
        "companions" to { CompanionsContent(WalletState.Unlocked(contents), {}, {}, {}, {}) },
        "photo-credits" to {
            PhotoCreditsContent(listOf(PhotoCredit("th", "Wat Arun Sunset.jpg", "miketnorton", "CC BY 2.0", sourceUrl = "https://commons.wikimedia.org/")), {})
        },
        // 0단계 공용 부품 전부 (DESIGN_SPEC 4장) — 접근성 점검·캡처가 새 부품까지 본다
        "components-1" to { ComponentsPage(1) },
        "components-2" to { ComponentsPage(2) },
        "components-3" to { ComponentsPage(3) },
        "components-4" to { ComponentsPage(4) },
        // 흰 단색 사진 최악 경우: PhotoTextArea 스크림·PhotoChip·사진 위 버튼 (DESIGN_SPEC 3.7)
        "photo-worst-white" to { PhotoWorstWhitePage() },
    )
}
