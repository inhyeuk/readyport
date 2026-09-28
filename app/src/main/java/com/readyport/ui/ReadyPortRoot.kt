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
import com.readyport.ui.nav.ExploreRoute
import com.readyport.ui.nav.HelpRoute
import com.readyport.ui.nav.PrepareRoute
import com.readyport.ui.nav.SettingsRoute
import com.readyport.ui.nav.Tab
import com.readyport.ui.nav.TodayRoute
import com.readyport.ui.nav.WalletRoute
import com.readyport.ui.onboarding.FirstRunScreen
import com.readyport.ui.settings.SettingsScreen
import com.readyport.ui.tabs.ExploreScreen
import com.readyport.ui.tabs.HelpScreen
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
import com.readyport.ui.today.TodayScreen

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
    walletTab: WalletTabSlot = DefaultWalletTab,
) {
    when {
        settings == null -> Box(Modifier.fillMaxSize().background(Tokens.Ground))
        settings.easyMode == null -> ReadyPortTheme(easyMode = true) {
            Box(Modifier.fillMaxSize().background(Tokens.Ground)) {
                FirstRunScreen(onAnswer = onSetEasyMode)
            }
        }
        else -> ReadyPortTheme(easyMode = settings.easyMode) {
            MainScaffold(settings.easyMode, onSetEasyMode, onSpeak, hasPendingShare, walletTab)
        }
    }
}

@Composable
private fun MainScaffold(
    easyMode: Boolean,
    onSetEasyMode: (Boolean) -> Unit,
    onSpeak: (String) -> Unit,
    hasPendingShare: Boolean,
    walletTab: WalletTabSlot,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    // 탭이 아닌 화면(설정, 여권 등록 등)에서는 들어온 탭을 선택된 채로 둔다
    var lastTab by remember { mutableStateOf(Tab.Today) }
    val matched = Tab.entries.firstOrNull { tab -> destination?.hasRoute(tab.route::class) == true }
    val selectedTab = when {
        matched != null -> matched
        destination?.hierarchy?.any { it.hasRoute(PassportGraph::class) || it.hasRoute(BookingImportRoute::class) } == true -> Tab.Wallet
        else -> lastTab
    }
    LaunchedEffect(selectedTab) { lastTab = selectedTab }

    // 다른 앱에서 '공유하기'로 예약 서류를 보내면 바로 가져오기 화면으로
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
            bottomBar = { BottomTabs(selected = selectedTab, onSelect = { navController.switchTab(it) }) },
        ) { inner ->
            NavHost(
                navController = navController,
                startDestination = TodayRoute,
                modifier = Modifier.padding(inner),
            ) {
                composable<TodayRoute> {
                    TodayScreen(
                        onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                        onPickDestination = { navController.switchTab(Tab.Explore) },
                    )
                }
                composable<PrepareRoute> { PrepareScreen() }
                composable<ExploreRoute> { ExploreScreen() }
                composable<WalletRoute> {
                    walletTab(
                        { navController.navigate(PassportGraph) },
                        { navController.navigate(BookingImportRoute) },
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
                            onSaved = { navController.popBackStack(PassportGraph, inclusive = true) },
                        )
                    }
                    composable<PassportManualRoute> { entry ->
                        val vm = navController.passportViewModel(entry)
                        PassportManualScreen(vm, onSaved = { navController.popBackStack(PassportGraph, inclusive = true) })
                    }
                }
                composable<BookingImportRoute> {
                    BookingImportScreen(onDone = {
                        if (!navController.popBackStack()) navController.switchTab(Tab.Wallet)
                    })
                }
                composable<HelpRoute> { HelpScreen() }
                composable<SettingsRoute> { SettingsScreen(easyMode = easyMode, onEasyModeChange = onSetEasyMode) }
            }
        }
    }
}

/** 지갑 탭 자리. 테스트에서는 Hilt 없이 상태 없는 화면으로 바꿔 끼운다 */
typealias WalletTabSlot = @Composable (onAddPassport: () -> Unit, onAddBooking: () -> Unit) -> Unit

private val DefaultWalletTab: WalletTabSlot = { onAddPassport, onAddBooking ->
    WalletScreen(onAddPassport = onAddPassport, onAddBooking = onAddBooking)
}

/** 여권 등록 흐름의 화면들이 같은 ViewModel(촬영 결과)을 나눠 쓴다. 흐름을 벗어나면 함께 사라진다 */
@Composable
private fun NavHostController.passportViewModel(entry: NavBackStackEntry): PassportFlowViewModel {
    val parent = remember(entry) { getBackStackEntry(PassportGraph) }
    return hiltViewModel(parent)
}

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** '처음으로': 쌓인 화면을 모두 닫고 '오늘' 첫 화면으로 */
private fun NavHostController.goHome() {
    navigate(TodayRoute) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
