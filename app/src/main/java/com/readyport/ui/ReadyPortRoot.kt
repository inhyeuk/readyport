package com.readyport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.readyport.data.settings.AppSettings
import com.readyport.ui.components.AppActions
import com.readyport.ui.components.LocalAppActions
import com.readyport.ui.components.LocalShowBack
import com.readyport.ui.components.OfflineBanner
import com.readyport.ui.nav.BottomTabs
import com.readyport.ui.nav.HomeRoute
import com.readyport.ui.nav.CountryRoute
import com.readyport.ui.nav.PhotoCreditsRoute
import com.readyport.ui.nav.VideosRoute
import com.readyport.ui.video.VideosScreen
import com.readyport.ui.home.HomeActions
import com.readyport.ui.home.HomeScreen
import com.readyport.ui.country.CountryActions
import com.readyport.ui.country.CountryScreen
import com.readyport.ui.settings.PhotoCreditsScreen
import com.readyport.ui.nav.HelpRoute
import com.readyport.ui.nav.SettingsRoute
import com.readyport.ui.nav.Tab
import com.readyport.ui.nav.WalletRoute
import com.readyport.ui.onboarding.FirstRunScreen
import com.readyport.ui.settings.SettingsScreen
import com.readyport.ui.form.AutofillScreen
import com.readyport.ui.form.FormConfirmScreen
import com.readyport.ui.form.ManualModeScreen
import com.readyport.ui.nav.AutofillRoute
import com.readyport.ui.nav.FormConfirmRoute
import com.readyport.ui.nav.FormManualRoute
import com.readyport.ui.pack.HelpScreen
import androidx.navigation.toRoute
import com.readyport.ui.wallet.BookingImportScreen
import com.readyport.ui.wallet.PassportConfirmScreen
import com.readyport.ui.wallet.PassportFlowViewModel
import com.readyport.ui.wallet.PassportIntroScreen
import com.readyport.ui.wallet.PassportManualScreen
import com.readyport.ui.wallet.PassportScanScreen
import com.readyport.ui.wallet.WalletScreen
import com.readyport.ui.nav.BookingImportRoute
import com.readyport.ui.nav.PassportConfirmRoute
import com.readyport.ui.nav.PassportGraph
import com.readyport.ui.nav.PassportIntroRoute
import com.readyport.ui.nav.PassportManualRoute
import com.readyport.ui.nav.PassportScanRoute
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.navigation
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import com.readyport.ui.trip.TripScreen
import com.readyport.ui.present.PresentScreen
import com.readyport.ui.present.CompanionsScreen
import com.readyport.ui.nav.TripRoute
import com.readyport.ui.nav.StayEditRoute
import com.readyport.ui.nav.TransportRoute
import com.readyport.ui.stay.StayEditScreen
import com.readyport.ui.nav.EssentialsRoute
import com.readyport.ui.nav.ShoppingRoute
import com.readyport.ui.prep.EssentialsScreen
import com.readyport.ui.pack.ShoppingScreen
import com.readyport.ui.transport.TransportScreen
import com.readyport.ui.nav.PresentRoute
import com.readyport.ui.nav.CompanionsRoute
import com.readyport.ui.nav.TripChecklistRoute
import com.readyport.ui.nav.TripsRoute
import com.readyport.ui.trip.ChecklistActions
import com.readyport.ui.trip.TripJourneyScreen
import com.readyport.ui.trip.TripListScreen
import com.readyport.notice.Notice
import com.readyport.notice.NoticeChoice
import com.readyport.notice.NoticeMode
import com.readyport.ui.nav.NoticesRoute
import com.readyport.ui.notice.NoticeDialog
import com.readyport.ui.notice.NoticesScreen
import com.readyport.ui.notice.rememberOpenLink
import com.readyport.ui.settings.NoticeSettings

/**
 * 앱 최상위 화면. 상태를 직접 들고 있지 않아서 테스트에서 그대로 띄울 수 있다.
 * @param settings null이면 설정을 읽는 중
 */
@Composable
fun ReadyPortRoot(
    settings: AppSettings?,
    onSetEasyMode: (Boolean) -> Unit,
    onSpeak: (String) -> Unit,
    hasPendingShare: Boolean = false,
    online: Boolean = true,
    slots: ScreenSlots = ScreenSlots(),
    onSetChildMode: (Boolean) -> Unit = {},
    /** 위젯에서 열면 바로 '입국 때 보여 주기' */
    openPresent: Boolean = false,
    onSetWifiOnly: (Boolean) -> Unit = {},
    /** 챙길 일 알림에서 열면 바로 그 여행 체크리스트 */
    openChecklistTripId: String? = null,
    /** 그 여행으로 간 뒤 — 같은 알림을 또 눌러도 다시 열리게 비워 둔다 */
    onChecklistOpened: () -> Unit = {},
    onSetAlertsOn: (Boolean) -> Unit = {},
    onSetAlertHour: (Int) -> Unit = {},
    /** 공지·소식 (docs/NOTICES_PUSH.md) — 앱을 켤 때 띄울 공지, 알림에서 열 공지, 설정 켬·끔 */
    notices: NoticeHooks = NoticeHooks(),
) {
    when {
        settings == null -> Box(Modifier.fillMaxSize().background(Tokens.Ground))
        settings.easyMode == null -> ReadyPortTheme(easyMode = true) {
            Box(Modifier.fillMaxSize().background(Tokens.Ground)) {
                FirstRunScreen(onAnswer = onSetEasyMode)
            }
        }
        else -> ReadyPortTheme(easyMode = settings.easyMode) {
            MainScaffold(
                settings, onSetEasyMode, onSetChildMode, onSetWifiOnly, onSpeak, hasPendingShare, online, slots, openPresent,
                openChecklistTripId, onChecklistOpened, onSetAlertsOn, onSetAlertHour, notices,
            )
        }
    }
}

@Composable
private fun MainScaffold(
    settings: AppSettings,
    onSetEasyMode: (Boolean) -> Unit,
    onSetChildMode: (Boolean) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSpeak: (String) -> Unit,
    hasPendingShare: Boolean,
    online: Boolean,
    slots: ScreenSlots,
    openPresent: Boolean,
    openChecklistTripId: String?,
    onChecklistOpened: () -> Unit,
    onSetAlertsOn: (Boolean) -> Unit,
    onSetAlertHour: (Int) -> Unit,
    notices: NoticeHooks,
) {
    val easyMode = settings.easyMode == true
    val tabs = if (settings.childMode) Tab.Child else Tab.Main
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    // 탭이 아닌 화면(설정, 여권 등록 등)에서는 들어온 탭을 선택된 채로 둔다
    var lastTab by remember(tabs) { mutableStateOf(tabs.first()) }
    // 둘러보기 히어로의 `예전 여행지 다시보기` → 내 여행 목록의 `지난 여행` 묶음을 펼친 채로 (목록이 한 번 쓰고 끈다)
    var showPastTrips by remember { mutableStateOf(false) }
    val matched = tabs.firstOrNull { tab -> destination?.hasRoute(tab.route::class) == true }
    // 탭이 아닌 화면은 **들어온 탭**을 켠 채로 둔다([lastTab]) — 같은 화면(나라 안내·예약 서류·여권)에 길이 여럿이라
    // 화면만 보고 탭을 정하면 내 여행에서 들어가도 둘러보기가 켜졌다. 여행 줄기(알림 딥링크 포함)만 내 여행으로 못 박는다.
    val selectedTab = when {
        matched != null -> matched
        settings.childMode -> lastTab
        destination?.hierarchy?.any {
            it.hasRoute(TripRoute::class) || it.hasRoute(TripsRoute::class) || it.hasRoute(TripChecklistRoute::class)
        } == true -> Tab.Trip
        else -> lastTab
    }
    LaunchedEffect(selectedTab) { lastTab = selectedTab }

    // 다른 앱에서 '공유하기'로 예약 서류를 보내면 바로 가져오기 화면으로
    LaunchedEffect(openPresent) {
        if (openPresent) navController.navigate(PresentRoute) { launchSingleTop = true }
    }

    LaunchedEffect(hasPendingShare) {
        if (hasPendingShare) navController.navigate(BookingImportRoute()) { launchSingleTop = true }
    }

    // 챙길 일 알림을 누르면 그 여행 체크리스트로 (PRD 6.1). 열고 나면 비워 둔다 — 같은 알림을 또 눌러도 열리게
    LaunchedEffect(openChecklistTripId) {
        if (openChecklistTripId != null) {
            navController.navigate(TripChecklistRoute(openChecklistTripId)) { launchSingleTop = true }
            onChecklistOpened()
        }
    }

    // 공지 알림을 누르면 공지사항(그 공지를 연 채로, 못 찾으면 목록). 열고 나면 비워 둔다 — 같은 알림을 또 눌러도 열리게
    LaunchedEffect(notices.openNoticeId) {
        val id = notices.openNoticeId
        if (id != null && !settings.childMode) {
            navController.navigate(NoticesRoute(openId = id.ifEmpty { null })) { launchSingleTop = true }
        }
        if (id != null) notices.onNoticeOpened()
    }

    // 앱을 켤 때의 공지 (첫 실행 질문을 마친 뒤, 자녀 폰 모드가 아닐 때만). 긴급 공지는 닫으면 다음 긴급 공지가 이어서 뜬다
    val openLink = rememberOpenLink()
    val launch = notices.launchNotice
    if (launch != null && !settings.childMode) {
        NoticeDialog(launch, NoticeMode.Launch, onChoice = { notices.onLaunchNoticeDone(launch, it) }, onOpenLink = openLink)
    }

    val actions = remember(navController, onSpeak) {
        AppActions(
            goHome = { navController.goHome() },
            speak = onSpeak,
            goBack = { navController.popBackStack() },
        )
    }

    // 탭 첫 화면이 아니면(쇼핑 리스트·영상·내 정보 등) 제목 옆에 뒤로 버튼
    CompositionLocalProvider(LocalAppActions provides actions, LocalShowBack provides (matched == null && destination != null)) {
        Scaffold(
            containerColor = Tokens.Ground,
            topBar = { if (!online) OfflineBanner() },
            bottomBar = { BottomTabs(selected = selectedTab, onSelect = { navController.switchTab(it) }, tabs = tabs) },
        ) { inner ->
            NavHost(
                navController = navController,
                startDestination = if (settings.childMode) PresentRoute else HomeRoute,
                modifier = Modifier.padding(inner),
            ) {
                composable<HomeRoute> {
                    slots.home(
                        HomeActions(
                            openCountry = { code -> navController.navigate(CountryRoute(code)) },
                            // 여행 흐름은 모두 내 여행 탭이 맡는다 — 둘러보기는 길만 가리킨다
                            openTrip = { id -> navController.navigate(TripChecklistRoute(id)) },
                            openTrips = { navController.switchTab(Tab.Trip) },
                            // 지난 여행만 보는 화면을 새로 만들지 않는다 — 여행 목록의 `지난 여행` 묶음을 펼쳐 준다
                            openPastTrips = {
                                showPastTrips = true
                                navController.switchTab(Tab.Trip)
                            },
                            makeTrip = { navController.navigate(TripRoute()) },
                        ),
                    )
                }
                composable<CountryRoute> { entry ->
                    val route = entry.toRoute<CountryRoute>()
                    val popToTrip: () -> Unit = { navController.popBackStack() }
                    slots.country(
                        route.country,
                        CountryActions(
                            back = { navController.popBackStack() },
                            openForm = { formId -> navController.navigate(FormConfirmRoute(formId)) },
                            planTrip = { code -> navController.navigate(TripRoute(code)) },
                            openTrip = { id -> navController.navigate(TripChecklistRoute(id)) },
                            openHelp = { navController.switchTab(Tab.Help) },
                            openMove = { navController.navigate(TransportRoute) },
                            openShopping = { code -> navController.navigate(ShoppingRoute(code)) },
                            openVideos = { code -> navController.navigate(VideosRoute(code)) },
                            // 이 여행에서 열었으면 머리 띠에 여행으로 돌아가는 길
                            backToTrip = route.tripId?.let { popToTrip },
                        ),
                    )
                }
                composable<TripRoute> { entry ->
                    val route = entry.toRoute<TripRoute>()
                    TripScreen(
                        onDone = { navController.popBackStack() },
                        initialCountry = route.country,
                        tripId = route.tripId,
                        // 새 여행을 만들면 바로 그 여행 체크리스트로(만들기 화면은 뒤로 가기에서 빠진다)
                        onCreated = { id ->
                            navController.navigate(TripChecklistRoute(id)) { popUpTo<TripRoute> { inclusive = true } }
                        },
                    )
                }
                composable<TripsRoute> {
                    slots.trips(
                        { id -> navController.navigate(TripChecklistRoute(id)) },
                        { navController.navigate(TripRoute()) },
                        showPastTrips,
                    )
                    // 목록을 떠날 때 표시를 끈다 — 다음에 내 여행 탭을 그냥 눌렀을 때 또 펼쳐지지 않게
                    DisposableEffect(Unit) { onDispose { showPastTrips = false } }
                }
                composable<TripChecklistRoute> { entry ->
                    val tripId = entry.toRoute<TripChecklistRoute>().tripId
                    TripJourneyScreen(
                        tripId = tripId,
                        actions = ChecklistActions(
                            openPassport = { navController.navigate(PassportGraph()) },
                            openWallet = { navController.navigate(WalletRoute) },
                            openForm = { formId -> navController.navigate(FormConfirmRoute(formId)) },
                            openHelp = { navController.switchTab(Tab.Help) },
                            openPresent = { navController.navigate(PresentRoute) },
                            openTransport = { navController.navigate(TransportRoute) },
                            openShopping = { code -> navController.navigate(ShoppingRoute(code)) },
                            openEssentials = { navController.navigate(EssentialsRoute) },
                            editTrip = { id -> navController.navigate(TripRoute(tripId = id)) },
                            openAirport = { code, airport ->
                                navController.navigate(CountryRoute(code, focusAirports = true, airport = airport, tripId = tripId))
                            },
                            openCountry = { code -> navController.navigate(CountryRoute(code, tripId = tripId)) },
                            // 예약 서류의 집은 이 여행의 예약 단계다 (설정 내 정보에서도 갈 수 있다).
                            // 이 여행에서 들어가면 숙소 서류는 이 여행 `묵는 곳`으로 저장된다
                            openBooking = { navController.navigate(BookingImportRoute(tripId = tripId)) },
                            openStayEdit = { stayId -> navController.navigate(StayEditRoute(tripId = tripId, stayId = stayId)) },
                        ),
                        onDeleted = { navController.popBackStack() },
                    )
                }
                composable<StayEditRoute> { StayEditScreen(onDone = { navController.popBackStack() }) }
                composable<TransportRoute> { TransportScreen() }
                composable<PresentRoute> { slots.present() }
                composable<CompanionsRoute> {
                    CompanionsScreen(onRegisterPassport = { id -> navController.navigate(PassportGraph(traveler = id)) })
                }
                composable<EssentialsRoute> { EssentialsScreen(onOpenChecklist = { id -> navController.navigate(TripChecklistRoute(id)) }) }
                composable<ShoppingRoute> { ShoppingScreen() }
                composable<VideosRoute> { VideosScreen() }
                composable<FormConfirmRoute> { entry ->
                    val formId = entry.toRoute<FormConfirmRoute>().formId
                    FormConfirmScreen(
                        onAutofill = { navController.navigate(AutofillRoute(formId)) },
                        onManual = { navController.navigate(FormManualRoute(formId)) },
                        onRegisterPassport = { navController.navigate(PassportGraph()) },
                    )
                }
                composable<AutofillRoute> { entry ->
                    val formId = entry.toRoute<AutofillRoute>().formId
                    AutofillScreen(onManual = { navController.navigate(FormManualRoute(formId)) })
                }
                composable<FormManualRoute> { ManualModeScreen() }
                composable<WalletRoute> {
                    slots.wallet(
                        { navController.navigate(PassportGraph()) },
                        { navController.navigate(BookingImportRoute()) },
                        { navController.navigate(CompanionsRoute) },
                        // 내 정보의 묵는 곳 목록에서 숙소 고치기 — 여행 id 없이 열어 그 숙소가 붙어 있던 여행을 그대로 둔다 (다듬기 S2)
                        { stayId -> navController.navigate(StayEditRoute(stayId = stayId)) },
                        { tripId -> navController.navigate(TripChecklistRoute(tripId)) },
                    )
                }
                navigation<PassportGraph>(startDestination = PassportIntroRoute) {
                    composable<PassportIntroRoute> { entry ->
                        val vm = navController.passportViewModel(entry)
                        PassportIntroScreen(
                            viewModel = vm,
                            onCamera = { navController.navigate(PassportScanRoute) },
                            onManual = { navController.navigate(PassportManualRoute) },
                            onFound = { navController.navigate(PassportConfirmRoute) { launchSingleTop = true } },
                        )
                    }
                    composable<PassportScanRoute> { entry ->
                        val vm = navController.passportViewModel(entry)
                        PassportScanScreen(vm, onFound = {
                            navController.navigate(PassportConfirmRoute) {
                                popUpTo(PassportScanRoute) { inclusive = true }
                                launchSingleTop = true
                            }
                        })
                    }
                    composable<PassportConfirmRoute> { entry ->
                        val vm = navController.passportViewModel(entry)
                        PassportConfirmScreen(
                            viewModel = vm,
                            onRescan = { navController.navigate(PassportScanRoute) { popUpTo(PassportIntroRoute) } },
                            onManual = { navController.navigate(PassportManualRoute) { popUpTo(PassportIntroRoute) } },
                            onSaved = { navController.popBackStack<PassportGraph>(inclusive = true) },
                        )
                    }
                    composable<PassportManualRoute> { entry ->
                        val vm = navController.passportViewModel(entry)
                        PassportManualScreen(vm, onSaved = { navController.popBackStack<PassportGraph>(inclusive = true) })
                    }
                }
                composable<BookingImportRoute> {
                    BookingImportScreen(onDone = {
                        if (!navController.popBackStack()) navController.navigate(WalletRoute)
                    })
                }
                composable<HelpRoute> { slots.help() }
                composable<SettingsRoute> {
                    SettingsScreen(
                        easyMode = easyMode, onEasyModeChange = onSetEasyMode,
                        childMode = settings.childMode, onChildModeChange = onSetChildMode,
                        wifiOnly = settings.wifiOnly, onWifiOnlyChange = onSetWifiOnly,
                        alertsOn = settings.alertsOn, onAlertsOnChange = onSetAlertsOn,
                        alertHour = settings.alertHour, onAlertHourChange = onSetAlertHour,
                        onOpenMyInfo = { navController.navigate(WalletRoute) },
                        onOpenFamily = { navController.navigate(CompanionsRoute) },
                        onOpenPhotos = { navController.navigate(PhotoCreditsRoute) },
                        notices = NoticeSettings(
                            noticePush = settings.noticePush,
                            promoPush = settings.promoPush,
                            promoDate = settings.promoDate,
                            promoNight = settings.promoNight,
                            promoNightDate = settings.promoNightDate,
                        ),
                        onNoticePushChange = notices.onSetNoticePush,
                        onPromoPushChange = notices.onSetPromoPush,
                        onPromoNightChange = notices.onSetPromoNight,
                        onOpenNotices = { navController.navigate(NoticesRoute()) },
                    )
                }
                composable<PhotoCreditsRoute> { PhotoCreditsScreen() }
                composable<NoticesRoute> { NoticesScreen() }
            }
        }
    }
}

/**
 * 공지·소식 연결 (MainActivity ↔ 화면). 기본값은 아무 일도 하지 않는다(테스트·갤러리).
 * [openNoticeId]: 공지 알림에서 열 때의 공지 id (`""` = 공지사항 목록, null = 없음).
 */
data class NoticeHooks(
    val launchNotice: Notice? = null,
    val onLaunchNoticeDone: (Notice, NoticeChoice) -> Unit = { _, _ -> },
    val openNoticeId: String? = null,
    val onNoticeOpened: () -> Unit = {},
    val onSetNoticePush: (Boolean) -> Unit = {},
    val onSetPromoPush: (Boolean) -> Unit = {},
    val onSetPromoNight: (Boolean) -> Unit = {},
)

/**
 * Hilt ViewModel을 쓰는 화면 자리. 테스트에서는 상태 없는 Content 화면으로 바꿔 끼운다.
 */
data class ScreenSlots(
    val home: @Composable (actions: HomeActions) -> Unit = { HomeScreen(actions = it) },
    val country: @Composable (country: String, actions: CountryActions) -> Unit = { _, a -> CountryScreen(actions = a) },
    val wallet: @Composable (
        onAddPassport: () -> Unit,
        onAddBooking: () -> Unit,
        onOpenCompanions: () -> Unit,
        onEditStay: (String) -> Unit,
        onOpenTrip: (String) -> Unit,
    ) -> Unit = { onAddPassport, onAddBooking, onOpenCompanions, onEditStay, onOpenTrip ->
        WalletScreen(
            onAddPassport = onAddPassport,
            onAddBooking = onAddBooking,
            onOpenCompanions = onOpenCompanions,
            onEditStay = onEditStay,
            onOpenTrip = onOpenTrip,
        )
    },
    val help: @Composable () -> Unit = { HelpScreen() },
    val trips: @Composable (onOpen: (String) -> Unit, onAdd: () -> Unit, openPast: Boolean) -> Unit =
        { onOpen, onAdd, openPast -> TripListScreen(onOpen = onOpen, onAdd = onAdd, openPast = openPast) },
    val present: @Composable () -> Unit = { PresentScreen(defaultFormId = null) },
)

/** 여권 등록 흐름의 화면들이 같은 ViewModel(촬영 결과)을 나눠 쓴다. 흐름을 벗어나면 함께 사라진다 */
@Composable
private fun NavHostController.passportViewModel(entry: NavBackStackEntry): PassportFlowViewModel {
    val parent = remember(entry) { getBackStackEntry<PassportGraph>() }
    return hiltViewModel(parent)
}

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** '처음으로': 쌓인 화면을 모두 닫고 홈으로 */
private fun NavHostController.goHome() {
    navigate(HomeRoute) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
