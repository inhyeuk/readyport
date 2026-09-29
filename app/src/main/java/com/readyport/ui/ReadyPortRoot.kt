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
import com.readyport.ui.nav.BottomTabs
import com.readyport.ui.nav.HomeRoute
import com.readyport.ui.nav.CountryRoute
import com.readyport.ui.nav.PhotoCreditsRoute
import com.readyport.ui.home.HomeActions
import com.readyport.ui.home.HomeScreen
import com.readyport.ui.country.CountryActions
import com.readyport.ui.country.CountryScreen
import com.readyport.ui.settings.PhotoCreditsScreen
import com.readyport.ui.nav.HelpRoute
import com.readyport.ui.nav.PrepareRoute
import com.readyport.ui.nav.SettingsRoute
import com.readyport.ui.nav.Tab
import com.readyport.ui.nav.TodayRoute
import com.readyport.ui.nav.WalletRoute
import com.readyport.ui.onboarding.FirstRunScreen
import com.readyport.ui.settings.SettingsScreen
import com.readyport.R
import com.readyport.ui.form.AutofillScreen
import com.readyport.ui.form.FormConfirmScreen
import com.readyport.ui.form.ManualModeScreen
import com.readyport.ui.nav.AutofillRoute
import com.readyport.ui.nav.FormConfirmRoute
import com.readyport.ui.nav.FormManualRoute
import com.readyport.ui.pack.HelpScreen
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.toRoute
import com.readyport.ui.tabs.PrepareScreen
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.navigation
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import com.readyport.ui.today.TodayActions
import com.readyport.ui.today.TodayScreen
import com.readyport.ui.trip.TripScreen
import com.readyport.ui.present.PresentScreen
import com.readyport.ui.present.CompanionsScreen
import com.readyport.ui.nav.TripRoute
import com.readyport.ui.nav.TransportRoute
import com.readyport.ui.nav.EssentialsRoute
import com.readyport.ui.nav.ShoppingRoute
import com.readyport.ui.prep.EssentialsScreen
import com.readyport.ui.pack.ShoppingScreen
import com.readyport.ui.transport.TransportScreen
import com.readyport.ui.nav.PresentRoute
import com.readyport.ui.nav.CompanionsRoute

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
) {
    when {
        settings == null -> Box(Modifier.fillMaxSize().background(Tokens.Ground))
        settings.easyMode == null -> ReadyPortTheme(easyMode = true) {
            Box(Modifier.fillMaxSize().background(Tokens.Ground)) {
                FirstRunScreen(onAnswer = onSetEasyMode)
            }
        }
        else -> ReadyPortTheme(easyMode = settings.easyMode) {
            MainScaffold(settings, onSetEasyMode, onSetChildMode, onSetWifiOnly, onSpeak, hasPendingShare, online, slots, openPresent)
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
) {
    val easyMode = settings.easyMode == true
    val tabs = if (settings.childMode) Tab.Child else Tab.Main
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    // 탭이 아닌 화면(설정, 여권 등록 등)에서는 들어온 탭을 선택된 채로 둔다
    var lastTab by remember(tabs) { mutableStateOf(tabs.first()) }
    val matched = tabs.firstOrNull { tab -> destination?.hasRoute(tab.route::class) == true }
    val selectedTab = when {
        matched != null -> matched
        settings.childMode -> lastTab
        destination?.hierarchy?.any {
            it.hasRoute(PassportGraph::class) || it.hasRoute(BookingImportRoute::class) || it.hasRoute(CompanionsRoute::class) ||
                it.hasRoute(WalletRoute::class) || it.hasRoute(PhotoCreditsRoute::class)
        } == true -> Tab.Settings
        destination?.hierarchy?.any {
            it.hasRoute(TripRoute::class) || it.hasRoute(PresentRoute::class) || it.hasRoute(PrepareRoute::class)
        } == true -> Tab.Trip
        destination?.hierarchy?.any {
            it.hasRoute(CountryRoute::class) || it.hasRoute(TransportRoute::class) || it.hasRoute(ShoppingRoute::class) ||
                it.hasRoute(FormConfirmRoute::class) || it.hasRoute(AutofillRoute::class) || it.hasRoute(FormManualRoute::class) ||
                it.hasRoute(EssentialsRoute::class)
        } == true -> Tab.Home
        else -> lastTab
    }
    LaunchedEffect(selectedTab) { lastTab = selectedTab }

    // 다른 앱에서 '공유하기'로 예약 서류를 보내면 바로 가져오기 화면으로
    LaunchedEffect(openPresent) {
        if (openPresent) navController.navigate(PresentRoute) { launchSingleTop = true }
    }

    LaunchedEffect(hasPendingShare) {
        if (hasPendingShare) navController.navigate(BookingImportRoute) { launchSingleTop = true }
    }

    val actions = remember(navController, onSpeak) {
        AppActions(
            goHome = { navController.goHome() },
            speak = onSpeak,
        )
    }

    CompositionLocalProvider(LocalAppActions provides actions) {
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
                            openTrip = { navController.switchTab(Tab.Trip) },
                            openEssentials = { navController.navigate(EssentialsRoute) },
                            openMyInfo = { navController.navigate(WalletRoute) },
                        ),
                    )
                }
                composable<CountryRoute> { entry ->
                    slots.country(
                        entry.toRoute<CountryRoute>().country,
                        CountryActions(
                            back = { navController.popBackStack() },
                            openForm = { formId -> navController.navigate(FormConfirmRoute(formId)) },
                            planTrip = { code -> navController.navigate(TripRoute(code)) },
                            openHelp = { navController.switchTab(Tab.Help) },
                            openMove = { navController.navigate(TransportRoute) },
                            openShopping = { code -> navController.navigate(ShoppingRoute(code)) },
                        ),
                    )
                }
                composable<TodayRoute> {
                    slots.today(
                        TodayActions(
                            makeTrip = { navController.navigate(TripRoute()) },
                            editTrip = { navController.navigate(TripRoute()) },
                            explore = { navController.switchTab(Tab.Home) },
                            prepare = { navController.navigate(PrepareRoute) },
                            openForm = { formId -> navController.navigate(FormConfirmRoute(formId)) },
                            registerPassport = { navController.navigate(PassportGraph()) },
                            present = { navController.navigate(PresentRoute) },
                            help = { navController.switchTab(Tab.Help) },
                            goStay = { navController.navigate(TransportRoute) },
                            expense = { navController.navigate(PrepareRoute) },
                        ),
                    )
                }
                composable<TripRoute> { entry ->
                    TripScreen(onDone = { navController.popBackStack() }, initialCountry = entry.toRoute<TripRoute>().country)
                }
                composable<TransportRoute> { TransportScreen() }
                composable<PresentRoute> { slots.present() }
                composable<CompanionsRoute> {
                    CompanionsScreen(onRegisterPassport = { id -> navController.navigate(PassportGraph(traveler = id)) })
                }
                composable<PrepareRoute> {
                    slots.prepare({ formId -> navController.navigate(FormConfirmRoute(formId)) }, { navController.navigate(EssentialsRoute) })
                }
                composable<EssentialsRoute> { EssentialsScreen() }
                composable<ShoppingRoute> { ShoppingScreen() }
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
                        { navController.navigate(BookingImportRoute) },
                        { navController.navigate(CompanionsRoute) },
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
                        onOpenMyInfo = { navController.navigate(WalletRoute) },
                        onOpenFamily = { navController.navigate(CompanionsRoute) },
                        onOpenPhotos = { navController.navigate(PhotoCreditsRoute) },
                    )
                }
                composable<PhotoCreditsRoute> { PhotoCreditsScreen() }
            }
        }
    }
}

/**
 * Hilt ViewModel을 쓰는 화면 자리. 테스트에서는 상태 없는 Content 화면으로 바꿔 끼운다.
 */
data class ScreenSlots(
    val home: @Composable (actions: HomeActions) -> Unit = { HomeScreen(actions = it) },
    val country: @Composable (country: String, actions: CountryActions) -> Unit = { _, a -> CountryScreen(actions = a) },
    val wallet: @Composable (onAddPassport: () -> Unit, onAddBooking: () -> Unit, onOpenCompanions: () -> Unit) -> Unit =
        { onAddPassport, onAddBooking, onOpenCompanions ->
            WalletScreen(onAddPassport = onAddPassport, onAddBooking = onAddBooking, onOpenCompanions = onOpenCompanions)
        },
    val help: @Composable () -> Unit = { HelpScreen() },
    val prepare: @Composable (onOpenForm: (String) -> Unit, onOpenEssentials: () -> Unit) -> Unit =
        { f, e -> PrepareScreen(onOpenForm = f, onOpenEssentials = e) },
    val today: @Composable (actions: TodayActions) -> Unit = { TodayScreen(actions = it) },
    val present: @Composable () -> Unit = { PresentScreen(defaultFormId = null) },
)

/** 오프라인 배너 (PRD 5.1): 남색, 화면 맨 위 */
@Composable
private fun OfflineBanner() {
    Text(
        text = stringResource(R.string.offline_banner),
        color = Tokens.Surface,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .fillMaxWidth()
            .background(Tokens.Navy)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

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
