package com.readyport.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object TodayRoute
@Serializable data object PrepareRoute
@Serializable data object ExploreRoute
@Serializable data object WalletRoute
@Serializable data object HelpRoute

/** 설정은 '오늘' 화면 오른쪽 위 톱니 버튼으로 들어간다 (PRD 4.1) */
@Serializable data object SettingsRoute

// 지갑 › 여권 등록 흐름 (중첩 그래프: 흐름 안의 화면이 같은 ViewModel을 쓴다)
@Serializable data object PassportGraph
@Serializable data object PassportIntroRoute
@Serializable data object PassportScanRoute
@Serializable data object PassportConfirmRoute
@Serializable data object PassportManualRoute

/** 지갑 › 예약 서류 추가. 다른 앱의 '공유하기'로도 들어온다 */
@Serializable data object BookingImportRoute
