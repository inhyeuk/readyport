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
import com.readyport.ui.tabs.WalletScreen
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
) {
    when {
        settings == null -> Box(Modifier.fillMaxSize().background(Tokens.Ground))
        settings.easyMode == null -> ReadyPortTheme(easyMode = true) {
            Box(Modifier.fillMaxSize().background(Tokens.Ground)) {
                FirstRunScreen(onAnswer = onSetEasyMode)
            }
        }
        else -> ReadyPortTheme(easyMode = settings.easyMode) {
            MainScaffold(easyMode = settings.easyMode, onSetEasyMode = onSetEasyMode, onSpeak = onSpeak)
        }
    }
}

@Composable
private fun MainScaffold(
    easyMode: Boolean,
    onSetEasyMode: (Boolean) -> Unit,
    onSpeak: (String) -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    // 설정은 '오늘' 안에 있는 화면이므로 '오늘' 탭을 선택된 것으로 보여 준다
    val selectedTab = Tab.entries.firstOrNull { tab -> destination?.hasRoute(tab.route::class) == true } ?: Tab.Today

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
                composable<WalletRoute> { WalletScreen() }
                composable<HelpRoute> { HelpScreen() }
                composable<SettingsRoute> { SettingsScreen(easyMode = easyMode, onEasyModeChange = onSetEasyMode) }
            }
        }
    }
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
