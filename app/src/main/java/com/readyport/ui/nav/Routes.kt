package com.readyport.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object TodayRoute
@Serializable data object PrepareRoute
@Serializable data object ExploreRoute
@Serializable data object WalletRoute
@Serializable data object HelpRoute

/** 설정은 '오늘' 화면 오른쪽 위 톱니 버튼으로 들어간다 (PRD 4.1) */
@Serializable data object SettingsRoute
