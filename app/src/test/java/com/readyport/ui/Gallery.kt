package com.readyport.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.readyport.ui.components.essentialsSummary
import com.readyport.ui.components.loadPhotoCredits
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
import com.readyport.ui.tabs.PrepareContent
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.today.TodayActions
import com.readyport.ui.today.TodayContent
import com.readyport.ui.today.TodayUi
import com.readyport.ui.transport.RideAppRow
import com.readyport.ui.transport.TransportContent
import com.readyport.ui.transport.TransportUi
import com.readyport.ui.trip.ChecklistActions
import com.readyport.ui.trip.ChecklistUi
import com.readyport.ui.trip.TripChecklistContent
import com.readyport.ui.trip.TripContent
import com.readyport.ui.trip.TripFormUi
import com.readyport.ui.trip.TripListContent
import com.readyport.ui.trip.TripListUi
import com.readyport.ui.trip.TripRow
import com.readyport.trip.Checklist
import com.readyport.trip.ChecklistPhase
import com.readyport.trip.CustomItem
import com.readyport.trip.PassportValidity
import com.readyport.trip.TripChecks
import com.readyport.trip.TripTiming
import kotlinx.coroutines.runBlocking
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

    // ---------------- 여러 여행·체크리스트 (2026-10-02) ----------------
    private fun packOf(cc: String) = runBlocking { TestPacks.repo.pack(cc)!!.value }
    private fun checklist(t: Trip, today: LocalDate, checks: TripChecks, saved: Boolean? = true) =
        Checklist.build(Checklist.Input(t, index, packOf(t.country), checks, saved, today))

    /** 태국 11월 여행, 오늘 10월 30일(일주일 전 단계): 한 달 전 항목 하나 늦음, 입국 카드는 11월 1일부터, 여권 괜찮음 */
    private val ckTh = Trip("TH", "2026-11-03", "2026-11-07", id = "g-th")
    private val ckThToday = LocalDate.of(2026, 10, 30)
    private val ckThChecks
        get() = TripChecks(
            marks = listOf("visa", "booking", "essential.travel_insurance", "data", "essential.payment", "essential.power_bank")
                .associateWith { true },
            custom = listOf(CustomItem("custom.g1", "우산 챙기기"), CustomItem("custom.g2", "아이 간식 챙기기")),
            passport = PassportValidity.check(LocalDate.of(2031, 4, 15), ckTh, packOf("TH").requirements.first().passportValidity),
        )
    private val ckThData get() = checklist(ckTh, ckThToday, ckThChecks)

    /** 중국 여행 출발 당일: 앞 단계는 감기약 성분 확인 하나만 남김(늦음), 입국 카드 안 냄(급함), 여권 기준은 공식 안내에 없음 */
    private val ckCn = Trip("CN", "2026-10-30", "2026-11-03", id = "g-cn")
    private val ckCnData: com.readyport.trip.ChecklistData
        get() {
            val today = ckCn.start
            val first = checklist(ckCn, today, TripChecks())
            val before = first.items.filter { it.phase!! < ChecklistPhase.DepartureDay && it.id != "country.medicine_cold" && it.id != "entry_form" }
            return checklist(
                ckCn, today,
                TripChecks(
                    marks = before.associate { it.id to true },
                    passport = PassportValidity.check(LocalDate.of(2031, 4, 15), ckCn, null),
                    custom = listOf(CustomItem("custom.c1", "보조배터리 용량 표시 확인")),
                ),
            )
        }

    private fun row(t: Trip, timing: TripTiming, name: String, today: LocalDate, checks: TripChecks = TripChecks(), overlaps: Boolean = false): TripRow {
        val data = checklist(t, today, checks)
        return TripRow(t, timing, name, data.done, data.total, overlaps)
    }

    private val tripRows: List<TripRow>
        get() {
            val today = LocalDate.of(2026, 10, 2)
            return listOf(
                row(ckTh, TripTiming.Upcoming, "태국", today, ckThChecks, overlaps = true),
                row(Trip("JP", "2026-11-06", "2026-11-09", id = "g-jp"), TripTiming.Upcoming, "일본", today, overlaps = true),
                row(Trip("TH", "2027-02-10", "2027-02-14", id = "g-th2"), TripTiming.Upcoming, "태국", today),
                row(Trip("SG", "2026-08-10", "2026-08-13", wrappedUp = true, id = "g-sg"), TripTiming.Past, "싱가포르", today),
            )
        }

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
        // 대만: TWAC 자동 입력 양식 카드(도착 7일 전부터) / 중국: 한시 무비자(2026-12-31까지)·온라인 입국 카드는 미리 안 내도 됨(수동 모드)
        "country-entry-TW" to { CountryContent(TestPacks.countryUi("TW"), CountryActions()) },
        "country-entry-CN" to { CountryContent(TestPacks.countryUi("CN"), CountryActions()) },
        // 중국 여행 정보: 특별여행주의보(티베트·신장) 문장이 맨 위 위험 배너로 올라간다
        "country-travel-CN" to { CountryContent(TestPacks.countryUi("CN"), CountryActions(), CountrySection.Travel) },
        // 필리핀: 무비자 30일 + eTravel(값 복사 모드). 여행 정보 맨 위 위험 배너(3·4단계 지역)는 country-travel-PH
        "country-entry-PH" to { CountryContent(TestPacks.countryUi("PH"), CountryActions()) },
        "country-travel-PH" to { CountryContent(TestPacks.countryUi("PH"), CountryActions(), CountrySection.Travel) },
        // 베트남: 무비자 45일. 꼭 내야 하는 입국 카드는 없고 사전 입국 정보(PAI)는 의무가 아닌 신고 카드 —
        // 공항 묶음에는 `자동 심사대는 베트남 국민용`(쓸 수 없어요) 줄
        "country-entry-VN" to { CountryContent(TestPacks.countryUi("VN"), CountryActions()) },
        // 싱가포르: 자동 심사대를 국적과 관계없이 쓸 수 있어요(초록 줄) · 창이 공항 하나라 고르기 칩 없음
        "country-entry-SG" to { CountryContent(TestPacks.countryUi("SG"), CountryActions()) },
        // 일본: 자동 심사대 판정이 없어 줄을 그리지 않는다(모름) · 공동 키오스크(VJW) 단계가 있는 공항 넷
        "country-entry-JP" to { CountryContent(TestPacks.countryUi("JP"), CountryActions()) },
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
        // 출국일 + 도착 공항(수완나품)을 골라 둔 여행 — `도착하면 이 순서예요` 짧은 공항 카드
        "today-departure" to {
            TodayContent(
                TodayUi(trip, StageInfo(TripStage.Departure, dayOfTrip = 1), "태국", th.value.forms.first(), true,
                    indexSources = indexSources, sourceNames = thSources, airport = th.value.airport("BKK"), hasAirports = true),
                TodayActions(), {}, {}, {}, {}, {},
            )
        },
        // 출국일에 입국 카드 기간이 열린 상태(태국 TDAC는 보통 이 상태) — 주 버튼은 입국 카드 하나, `도착했어요`는 보조 (C 묶음 캡처를 공용 갤러리로)
        "today-departure-form" to {
            TodayContent(
                TodayUi(trip, StageInfo(TripStage.Departure, dayOfTrip = 1, formWindowOpen = true), "태국", th.value.forms.first(), true),
                TodayActions(), {}, {}, {}, {}, {},
            )
        },
        // 도착 단계 + 수완나품: 공항 순서(팩 — 위치·입국 카드 줄·출처) 뒤에 유심·환전·숙소가 번호를 이어 간다
        "today-arrival" to {
            TodayContent(
                TodayUi(trip, StageInfo(TripStage.Arrival, dayOfTrip = 1), "태국", th.value.forms.first(), true,
                    indexSources = indexSources, sourceNames = thSources, airport = th.value.airport("BKK"), hasAirports = true),
                TodayActions(), {}, {}, {}, {}, {},
            )
        },
        // 도착 단계, 공항을 아직 고르지 않음(태국은 공항이 셋) — 일반 순서 + `공항별 도착 순서 보기`
        "today-arrival-no-airport" to {
            TodayContent(TodayUi(trip, StageInfo(TripStage.Arrival, dayOfTrip = 1), "태국", th.value.forms.first(), true, hasAirports = true),
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
        // 여행 고치기: 내리는 공항(태국 팩 공항 셋 + 아직 몰라요) — 수완나품을 골라 둔 여행
        "trip-edit" to {
            TripContent(
                TripFormUi(index.countries.filter { it.pack }, trip.copy(arrivalAirport = "BKK"), loaded = true, airports = mapOf("TH" to th.value.airports)),
                { _, _, _, _ -> }, {},
            )
        },
        // 내 여행 목록: 다가오는 여행 셋(태국 둘 = 다른 여행·다른 체크리스트, 일본은 날짜 겹침) + 지난 여행(접힘)
        "trips-list" to { TripListContent(TripListUi(loaded = true, rows = tripRows, today = LocalDate.of(2026, 10, 2)), {}, {}) },
        "trips-empty" to { TripListContent(TripListUi(loaded = true), {}, {}) },
        // 한 여행 체크리스트(태국, 일주일 전 단계) — 단계 카드·앱이 확인·늦음·기간 전 잠김·출처·내 항목
        "trip-checklist" to {
            TripChecklistContent(ChecklistUi(loaded = true, trip = ckTh, countryName = "태국", data = ckThData, today = ckThToday, overlaps = true), ChecklistActions())
        },
        // 중국 출발 당일 — 입국 카드 급함(빨강), 여권 기준 없음(공식 안내 링크), 지난 단계 접힘
        "trip-checklist-cn" to {
            TripChecklistContent(ChecklistUi(loaded = true, trip = ckCn, countryName = "중국", data = ckCnData, today = ckCn.start), ChecklistActions())
        },
        // 태국 여행 도착 다음 날(도착하면 단계) — `도착 공항 순서 보기`(내리는 공항 수완나품 칩 + 공항 순서 보기 + 출처). 앞 단계는 모두 했음
        "trip-checklist-arrival" to {
            val t = ckTh.copy(arrivalAirport = "BKK")
            val today = LocalDate.of(2026, 11, 4)
            val first = checklist(t, today, TripChecks())
            val before = first.items.filter { it.phase!! < ChecklistPhase.Arrival }
            val data = checklist(t, today, TripChecks(marks = before.associate { it.id to true }))
            TripChecklistContent(ChecklistUi(loaded = true, trip = t, countryName = "태국", data = data, today = today), ChecklistActions())
        },
        // 오늘 화면 '지금 챙길 것'(입국 카드·여권 할 일이 없을 때 지금 할 일 = 체크리스트)
        "today-checklist" to {
            val data = ckThData
            TodayContent(
                TodayUi(
                    ckTh, StageInfo(TripStage.Preparing, daysLeft = 4), "태국", th.value.forms.first(), true,
                    checklistNow = Checklist.nowItems(data, ckTh, ckThToday), checklistDone = data.done, checklistTotal = data.total,
                    tripCount = 3, today = ckThToday,
                ),
                TodayActions(), {}, {}, {}, {}, {},
            )
        },
        // 태국 TDAC(의무 — 주 버튼, 내 여행 날짜) + 베트남 PAI(의무 아님 — 알약·보조 버튼)
        "prepare" to {
            PrepareContent(
                TestPacks.formEntries(listOf("TH", "VN"))
                    .map { f -> f.copy(tripArrival = trip.start.takeIf { f.formId.startsWith("TH") }) },
                {},
                essentialsSummary(index, th.value, gotItems),
            )
        },
        // 여행지 전기 값 칩 카드(220 V·한국 플러그)까지 — 운영 EssentialsViewModel과 같은 팩 값(power·출처 이름)
        "essentials" to {
            val power = th.value.power
            val rules: List<EssentialRule> = Essentials.select(index.essentials, index.homePower, power)
            EssentialsContent(
                EssentialsUi(
                    "태국", 4, 11, rules.mapIndexed { i, r -> EssentialRow(r, i < 2, r.source?.let { indexSources[it] }) },
                    power = power, powerSource = power?.let { thSources[it.source] },
                ),
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
        // 기사님 카드 문장 = 팩 phrases(id=address)의 현지어 + 한국어 뜻, 차량 앱 = 팩 transport_apps(지도 링크 제외) — 운영 TransportViewModel과 같은 값
        "transport" to {
            val place = Place("p1", "방콕 숙소", "สุขุมวิท ซอย 11 กรุงเทพฯ")
            val phrase = th.value.phrases.first { it.id == "address" }
            TransportContent(
                TransportUi(
                    listOf(place), place,
                    th.value.transportApps.filter { it.linkType != "maps_url" }.map { RideAppRow(it, installed = false) }, false,
                    driverPhrase = phrase.local, driverPhraseKo = phrase.ko,
                ),
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
        // 쉬운 모드 캡처(easy/)에서는 쉬운 모드 스위치가 켜진 모습 — 테마의 쉬운 모드 값을 그대로 넘긴다
        "settings" to { SettingsScreen(easyMode = LocalDimens.current.easyMode, onEasyModeChange = {}) },
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
        // 번들 사진 전부 — 운영 PhotoCreditsScreen과 같은 출처(assets/photo_credits.json)
        "photo-credits" to {
            val context = LocalContext.current
            val credits = remember { loadPhotoCredits(context) }
            PhotoCreditsContent(credits, {})
        },
        // 0단계 공용 부품 전부 (DESIGN_SPEC 4장) — 접근성 점검·캡처가 새 부품까지 본다
        "components-1" to { ComponentsPage(1) },
        "components-2" to { ComponentsPage(2) },
        "components-3" to { ComponentsPage(3) },
        "components-4" to { ComponentsPage(4) },
        // 흰 단색 사진 최악 경우: PhotoTextArea 스크림·PhotoChip·사진 위 버튼 (DESIGN_SPEC 3.7)
        "photo-worst-white" to { PhotoWorstWhitePage() },
        // 길잡이 v4: 그림 메뉴가 위로 지나간 뒤의 **접힌 고정 줄**(썸네일 + 라벨 + 밑줄). 실기기 높이 창에서 내용 몇 칸 아래로 내려 둔 상태
        // (다른 캡처는 아주 긴 칸에 한 번에 그려 스크롤이 없어서 고정 줄이 나타나지 않는다). 번호가 밀리지 않게 맨 끝에 둔다.
        "country-compact-bar" to {
            Box(Modifier.fillMaxWidth().height(COMPACT_CAPTURE_DP.dp)) {
                CountryContent(
                    TestPacks.countryUi("TH", favorite = true),
                    CountryActions(),
                    CountrySection.Travel,
                    listState = rememberLazyListState(initialFirstVisibleItemIndex = COMPACT_CAPTURE_ITEM),
                )
            }
        },
    )

    /** 접힌 고정 줄 캡처 창 높이(dp) — 기본 갤러리 창(h700dp)과 같은 실기기 화면 높이 */
    private const val COMPACT_CAPTURE_DP = 700

    /** 접힌 고정 줄 캡처의 첫 항목(머리·그림 메뉴를 지나 여행 정보 내용 몇 칸 아래) */
    private const val COMPACT_CAPTURE_ITEM = 3
}
