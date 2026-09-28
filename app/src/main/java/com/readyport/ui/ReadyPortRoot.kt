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
import com.readyport.R
import com.readyport.ui.form.AutofillScreen
import com.readyport.ui.form.FormConfirmScreen
import com.readyport.ui.form.ManualModeScreen
import com.readyport.ui.nav.AutofillRoute
import com.readyport.ui.nav.FormConfirmRoute
import com.readyport.ui.nav.FormManualRoute
import com.readyport.ui.nav.GuideRoute
import com.readyport.ui.pack.ExploreScreen
import com.readyport.ui.pack.GuideScreen
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
    online: Boolean = true,
    slots: ScreenSlots = ScreenSlots(),
) {
    when {
        settings == null -> Box(Modifier.fillMaxSize().background(Tokens.Ground))
        settings.easyMode == null -> ReadyPortTheme(easyMode = true) {
            Box(Modifier.fillMaxSize().background(Tokens.Ground)) {
                FirstRunScreen(onAnswer = onSetEasyMode)
            }
        }
        else -> ReadyPortTheme(easyMode = settings.easyMode) {
            MainScaffold(settings.easyMode, onSetEasyMode, onSpeak, hasPendingShare, online, slots)
        }
    }
}

@Composable
private fun MainScaffold(
    easyMode: Boolean,
    onSetEasyMode: (Boolean) -> Unit,
    onSpeak: (String) -> Unit,
    hasPendingShare: Boolean,
    online: Boolean,
    slots: ScreenSlots,
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
        destination?.hierarchy?.any {
            it.hasRoute(FormConfirmRoute::class) || it.hasRoute(AutofillRoute::class) || it.hasRoute(FormManualRoute::class)
        } == true -> Tab.Prepare
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
            topBar = { if (!online) OfflineBanner() },
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
                composable<PrepareRoute> { slots.prepare { formId -> navController.navigate(FormConfirmRoute(formId)) } }
                composable<FormConfirmRoute> { entry ->
                    val formId = entry.toRoute<FormConfirmRoute>().formId
                    FormConfirmScreen(
                        onAutofill = { navController.navigate(AutofillRoute(formId)) },
                        onManual = { navController.navigate(FormManualRoute(formId)) },
                        onRegisterPassport = { navController.navigate(PassportGraph) },
                    )
                }
                composable<AutofillRoute> { entry ->
                    val formId = entry.toRoute<AutofillRoute>().formId
                    AutofillScreen(onManual = { navController.navigate(FormManualRoute(formId)) })
                }
                composable<FormManualRoute> { ManualModeScreen() }
                composable<ExploreRoute> { slots.explore { country -> navController.navigate(GuideRoute(country)) } }
                composable<GuideRoute> { entry -> slots.guide(entry.toRoute<GuideRoute>().country) }
                composable<WalletRoute> {
                    slots.wallet(
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
                composable<HelpRoute> { slots.help() }
                composable<SettingsRoute> { SettingsScreen(easyMode = easyMode, onEasyModeChange = onSetEasyMode) }
            }
        }
    }
}

/**
 * Hilt ViewModel을 쓰는 화면 자리. 테스트에서는 상태 없는 Content 화면으로 바꿔 끼운다.
 */
data class ScreenSlots(
    val wallet: @Composable (onAddPassport: () -> Unit, onAddBooking: () -> Unit) -> Unit = { onAddPassport, onAddBooking ->
        WalletScreen(onAddPassport = onAddPassport, onAddBooking = onAddBooking)
    },
    val explore: @Composable (onOpenGuide: (String) -> Unit) -> Unit = { ExploreScreen(onOpenGuide = it) },
    val guide: @Composable (country: String) -> Unit = { GuideScreen() },
    val help: @Composable () -> Unit = { HelpScreen() },
    val prepare: @Composable (onOpenForm: (String) -> Unit) -> Unit = { PrepareScreen(onOpenForm = it) },
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
