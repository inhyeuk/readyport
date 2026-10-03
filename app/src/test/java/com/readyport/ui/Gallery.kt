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
import com.readyport.stay.Stays
import com.readyport.transport.Place
import com.readyport.trip.Trip
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
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.transport.RideAppRow
import com.readyport.ui.transport.TransportContent
import com.readyport.ui.transport.TransportUi
import com.readyport.trip.ChecklistData
import com.readyport.trip.JourneyStage
import com.readyport.trip.StageInfo
import com.readyport.trip.TripStage
import com.readyport.trip.TripStages
import com.readyport.ui.home.HomeTrip
import com.readyport.ui.trip.ChecklistActions
import com.readyport.ui.trip.JourneyUi
import com.readyport.ui.trip.TripJourneyContent
import com.readyport.ui.trip.TripContent
import com.readyport.ui.trip.TripFormUi
import com.readyport.ui.trip.TripListContent
import com.readyport.ui.trip.TripListUi
import com.readyport.ui.trip.TripRow
import com.readyport.trip.Checklist
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
import com.readyport.ui.stay.StayEditContent
import com.readyport.ui.stay.StayEditUi
import com.readyport.vault.BookingRecord
import com.readyport.vault.EntryDoc
import com.readyport.vault.PassportRecord
import com.readyport.vault.StayRecord
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

    /** 일본 여행(날짜가 태국과 겹친다) — 내 정보의 묵는 곳 목록이 **두 여행**을 보이게 하는 두 번째 여행 */
    private val ckJp = Trip("JP", "2026-11-06", "2026-11-09", id = "g-jp")

    /** 중국 여행 출발 당일: 앞 단계는 감기약 성분 확인 하나만 남김(늦음), 입국 카드 안 냄(급함), 여권 기준은 공식 안내에 없음 */
    private val ckCn = Trip("CN", "2026-10-30", "2026-11-03", id = "g-cn")
    private val ckCnData: ChecklistData
        get() {
            val today = ckCn.start
            val first = checklist(ckCn, today, TripChecks())
            val before = first.items.filter {
                it.stage!! < JourneyStage.Departure && it.id != "country.medicine_cold" && it.id != "entry_form"
            }
            return checklist(
                ckCn, today,
                TripChecks(
                    marks = before.associate { it.id to true },
                    passport = PassportValidity.check(LocalDate.of(2031, 4, 15), ckCn, null),
                    custom = listOf(CustomItem("custom.c1", "보조배터리 용량 표시 확인")),
                ),
            )
        }

    /**
     * 묵는 곳 두 곳 — **날짜별로 다른 호텔**(11월 3일~5일 방콕, 5일~7일 아유타야).
     * 이름·주소는 모두 지어낸 가짜다(실제 사람·실제 예약 정보 없음).
     */
    private val stays = listOf(
        StayRecord(
            id = "stay-1", tripId = "g-th", name = "리버뷰 방콕 호텔",
            addressLocal = "123 Soi Sukhumvit 11, Khlong Toei Nuea, Watthana, Bangkok 10110",
            addressKo = "BTS 나나역에서 걸어서 7분", checkIn = "2026-11-03", checkOut = "2026-11-05",
            reference = "RV-0000-0000", type = "hotel", phone = "+66-2-000-0000", savedAt = "2026-10-02T10:00",
        ),
        StayRecord(
            id = "stay-2", tripId = "g-th", name = "아유타야 리버 게스트하우스",
            addressLocal = "45 Naresuan Road, Pratu Chai, Phra Nakhon Si Ayutthaya 13000",
            addressKo = "아유타야 역에서 툭툭으로 10분", checkIn = "2026-11-05", checkOut = "2026-11-07",
            type = "guest_house", savedAt = "2026-10-02T10:05",
        ),
    )

    /**
     * 다른 여행(일본)의 숙소와 **아직 어느 여행에도 붙지 않은 숙소** — 설정 › 내 정보의 묵는 곳 목록이
     * 두 여행 묶음 + `여행이 없는 숙소` 묶음을 보이게 한다 (다듬기 S2). 모두 지어낸 값이다.
     */
    private val otherStays = listOf(
        StayRecord(
            id = "stay-3", tripId = "g-jp", name = "교토 마치야 게스트하우스",
            addressLocal = "12-3 Fake-cho, Nakagyo-ku, Kyoto 604-0000",
            addressKo = "시조역에서 걸어서 10분", checkIn = "2026-11-06", checkOut = "2026-11-09",
            type = "guest_house", lat = 35.0116, lng = 135.7681, savedAt = "2026-10-02T11:00",
        ),
        // 예전 `lodging` 예약 서류에서 옮겨 온 숙소: 여행도 주소도 없다 — 내 정보에서만 보인다
        StayRecord(id = "stay-4", tripId = null, name = "예전 예약 호텔", reference = "OLD-0000", savedAt = "2026-09-20T09:00"),
    )

    /** 보관함에 든 숙소 전부 (여행 화면은 그 여행 숙소만 본다 — [stays]) */
    private val allStays = stays + otherStays

    /** 한 여행 화면 값 한 벌 — 운영 JourneyViewModel과 같은 계산(단계·공항·쇼핑·귀국 사실) */
    private fun journeyUi(
        t: Trip,
        today: LocalDate,
        data: ChecklistData,
        cart: List<com.readyport.pack.ShoppingItem> = emptyList(),
        nowHour: Int = 7,
        muted: Boolean = false,
        overlaps: Boolean = false,
    ): JourneyUi {
        val pack = packOf(t.country)
        val form = pack.requiredForms.firstOrNull()
        return JourneyUi(
            loaded = true,
            trip = t,
            countryName = pack.names.ko,
            data = data,
            today = today,
            overlaps = overlaps,
            muted = muted,
            nowHour = nowHour,
            stage = TripStages.compute(t, today, today.atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), form?.windowDaysIncludingArrival),
            form = form,
            cart = cart,
            returnLinks = index.returnLinks,
            returnFacts = index.returnFacts,
            indexSources = indexSources,
            sourceNames = pack.sources.associate { it.id to it.name },
            airport = pack.airport(t.arrivalAirport) ?: pack.airports.singleOrNull(),
            hasAirports = pack.airports.isNotEmpty(),
            hasShopping = pack.shopping.isNotEmpty(),
            essentialsTotal = 5,
            essentialsDone = 2,
            // 묵는 곳 — 운영 JourneyViewModel과 같은 계산(그 여행 숙소를 날짜 순으로 + 부드러운 알림)
            stays = Stays.forTrip(stays, t),
            stayNotes = Stays.notes(stays, t),
        )
    }

    /** 그 단계가 '지금 단계'가 되도록 앞 단계를 모두 체크한 체크리스트 (떠나기 전 단계는 한 일로 나아간다) */
    private fun through(t: Trip, today: LocalDate, upTo: JourneyStage, extra: TripChecks = TripChecks()): ChecklistData {
        val first = checklist(t, today, extra)
        val marks = first.items.filter { it.stage!! < upTo }.associate { it.id to true }
        return checklist(t, today, extra.copy(marks = extra.marks + marks))
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
                row(ckJp, TripTiming.Upcoming, "일본", today, overlaps = true),
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
    // 숙소는 예약 서류가 아니라 `묵는 곳`으로 둔다 (2026-10-03 — 예전 lodging 예약 서류는 보관함을 열 때 옮겨진다)
    private val contents = VaultContents(
        passport = passport,
        bookings = listOf(
            BookingRecord(id = "1", kind = "flight", title = "방콕 왕복", flightNumbers = listOf("KE651", "KE652"),
                dates = listOf("2026-11-03", "2026-11-07"), savedAt = "x"),
        ),
        stays = allStays,
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
        // 둘러보기: 여행이 없으면 `여행 만들기` 하나 — 여행 흐름 조각(출국 순서·꼭 챙길 물건·귀국 전 확인·여권)은 내 여행 탭으로 옮겼다
        "explore" to { HomeContent(TestPacks.homeUi(), HomeActions(), today = LocalDate.of(2026, 9, 28)) },
        // 둘러보기(여행이 있을 때): 그 여행으로 가는 한 줄 + 나라 고르기
        "explore-with-trip" to {
            HomeContent(
                TestPacks.homeUi().copy(
                    trip = HomeTrip("태국", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 7), code = "TH", id = "g-th",
                        checklistDone = 12, checklistTotal = 28, tripCount = 3),
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
        // ---- 한 여행 화면(여행 과정 8단계, 2026-10-03 부록 H) ----
        // 계획 단계: 아무것도 안 한 새 여행 — 단계 막대 첫 칸이 `지금`, 계획 단계에 나라 안내·날짜 고치기 모자이크
        "trip-plan" to {
            TripJourneyContent(journeyUi(ckTh, ckThToday, checklist(ckTh, ckThToday, TripChecks())), ChecklistActions())
        },
        // 예약 단계: 계획을 다 했다 — 예약 서류 가져오기 카드(예약의 집)
        "trip-book" to {
            TripJourneyContent(journeyUi(ckTh, ckThToday, through(ckTh, ckThToday, JourneyStage.Book)), ChecklistActions())
        },
        // 서류 단계: 입국 카드(11월 1일부터)·여권 정보 — 기간 전 잠김 태그
        "trip-docs" to {
            TripJourneyContent(journeyUi(ckTh, ckThToday, through(ckTh, ckThToday, JourneyStage.Docs, ckThChecks)), ChecklistActions())
        },
        // 짐 단계: 꼭 챙길 물건 묶음 + 꼭 챙길 물건 자세히 보기
        "trip-pack" to {
            TripJourneyContent(journeyUi(ckTh, ckThToday, through(ckTh, ckThToday, JourneyStage.Pack, ckThChecks)), ChecklistActions())
        },
        // 출국 단계(출발 당일): 출국 순서 카드 + 도착 공항 짧은 카드 + 도착했어요
        "trip-departure" to {
            val t = ckTh.copy(arrivalAirport = "BKK")
            val today = t.start
            TripJourneyContent(journeyUi(t, today, through(t, today, JourneyStage.Departure, ckThChecks)), ChecklistActions())
        },
        // 입국 단계(도착했어요를 누른 뒤): 도착한 날 묵는 곳(주소·지도) + 보여 주기 + 공항 순서 + 유심·환전·숙소 + 다 했어요
        "trip-arrival" to {
            val t = ckTh.copy(arrivalAirport = "BKK", arrivedAt = 1L)
            val today = t.start
            TripJourneyContent(journeyUi(t, today, through(t, today, JourneyStage.Arrival, ckThChecks)), ChecklistActions())
        },
        // 여행 중 단계(11월 5일 — 호텔을 옮기는 날): `오늘 묵는 곳`이 두 번째 숙소(아유타야)로 바뀐다
        "trip-during" to {
            val t = ckTh.copy(arrivalAirport = "BKK", arrivedAt = 1L)
            val today = LocalDate.of(2026, 11, 5)
            TripJourneyContent(journeyUi(t, today, through(t, today, JourneyStage.During, ckThChecks)), ChecklistActions())
        },
        // 숙소 고치기: 이름·주소(현지 글자)·한국어 메모 → 묵는 날짜 → 숙소 종류 → 예약번호·전화·메모 → 저장·지우기.
        // 입력칸 값은 사람이 적은 글자라 앱이 줄바꿈을 보정하지 않는다 — 예약 확인서에 흔한 영문 이름으로 둔다(가짜)
        // 좌표를 적어 둔 숙소라 **좌표 묶음이 펼쳐진 채로** 보인다(다듬기 S2) — 날짜 칸은 달력 단추가 붙은 공용 칸
        "stay-edit" to {
            val stay = stays.first().copy(
                name = "Riverview Hotel Bangkok", addressKo = "나나역 근처",
                lat = 13.7461, lng = 100.5349,
            )
            StayEditContent(StayEditUi(loaded = true, locked = false, existing = stay, trip = ckTh), {}, {}, {})
        },
        // 복귀 단계(돌아온 뒤): 담아 둔 물건 + 귀국 전 확인 전체 + 여권 정보 지우기
        "trip-return" to {
            val today = LocalDate.of(2026, 11, 8)
            TripJourneyContent(
                journeyUi(ckTh, today, through(ckTh, today, JourneyStage.Return, ckThChecks), cart = th.value.shopping.take(4)),
                ChecklistActions(),
            )
        },
        // 중국 출발 당일 — 입국 카드 급함(빨강), 여권 기준 없음(공식 안내 링크), 이 여행만 알림 꺼 둠
        "trip-cn" to {
            TripJourneyContent(journeyUi(ckCn, ckCn.start, ckCnData, nowHour = 10, muted = true), ChecklistActions())
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
        // 태국 TDAC(의무 — 주 버튼, 내 여행 날짜) + 베트남 PAI(의무 아님 — 알약·보조 버튼)
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
        // 숙소 주소는 `묵는 곳에서` 묶음으로 들어오고, 사이트에서 골라야 하는 주·구·동·우편번호는 안내 카드가 말한다 (2026-10-03)
        "form-confirm" to {
            val recipe = TestPacks.tdacRecipe
            // 운영 FormConfirmViewModel과 같은 초안: 레시피 제안값 + 보관함에서 온 고르는 값(숙소 종류) + 사람이 고친 값
            val draft = FormValues.defaults(recipe.value) + FormValues.suggest(recipe.value, contents) +
                mapOf("trip.purpose" to "tourism", "profile.country_res" to "대한민국")
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
        "settings" to { SettingsScreen(easyMode = LocalDimens.current.easyMode, onEasyModeChange = {}, notifGranted = true) },
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
        // 열린 내 정보: 여권 → 예약 서류 → **묵는 곳(태국 2곳 · 일본 1곳 · 여행 없는 숙소 1곳)** — 다듬기 S2
        "wallet-unlocked" to {
            WalletContent(
                state = WalletState.Unlocked(contents), deviceSecure = true, autoDestroy = true, today = LocalDate.of(2026, 9, 29),
                onUnlock = {}, onLock = {}, onReset = {}, onAddPassport = {}, onDeletePassport = {},
                onAddBooking = {}, onDeleteBooking = {}, onAutoDestroyChange = {},
                stayGroups = Stays.group(allStays, listOf(ckTh, ckJp)),
                countryNames = index.countries.associate { it.code to it.nameKo },
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
        // 설정 › 알림: 휴대폰 알림 권한이 없을 때 (안내 줄 + `휴대폰 알림 설정 열기`)
        "settings-alerts-blocked" to {
            SettingsScreen(easyMode = LocalDimens.current.easyMode, onEasyModeChange = {}, alertHour = 20, notifGranted = false)
        },
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
