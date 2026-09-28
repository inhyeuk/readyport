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
/** traveler = "self" 또는 동행자 id (가족 모드) */
@Serializable data class PassportGraph(val traveler: String = "self")
@Serializable data object PassportIntroRoute
@Serializable data object PassportScanRoute
@Serializable data object PassportConfirmRoute
@Serializable data object PassportManualRoute

/** 지갑 › 예약 서류 추가. 다른 앱의 '공유하기'로도 들어온다 */
@Serializable data object BookingImportRoute

/** 여행지 › 국가 가이드. 인터넷 없이 저장해 둔 팩으로 보여 준다 */
@Serializable data class GuideRoute(val country: String)

/** 준비 › 입국 카드 3개 국어 확인 (PRD 5.2) */
@Serializable data class FormConfirmRoute(val formId: String)

/** 준비 › 공식 사이트 자동 입력 (PRD 5.3) */
@Serializable data class AutofillRoute(val formId: String)

/** 준비 › 수동 모드: 값 복사 + 공식 사이트 */
@Serializable data class FormManualRoute(val formId: String)

/** 오늘 › 여행 만들기·고치기 */
@Serializable data object TripRoute

/** 입국 때 보여 주기 (PRD 5.4). 자녀 폰 모드의 첫 화면 */
@Serializable data object PresentRoute

/** 지갑 › 같이 가는 사람 (가족 모드) */
@Serializable data object CompanionsRoute
