package com.readyport.ui.nav

import kotlinx.serialization.Serializable

/** 메인 홈: 여행 기본 정보 + 나라 사진 카드 */
@Serializable data object HomeRoute
/** 내 여행: 여행 단계별 할 일 */
@Serializable data object TodayRoute
/** 내 여행 › 준비 목록 */
@Serializable data object PrepareRoute
/** 설정 › 내 정보(여권·예약 서류·같이 가는 사람). 기기 안에만 저장 */
@Serializable data object WalletRoute
@Serializable data object HelpRoute
@Serializable data object SettingsRoute
/** 설정 › 사진 출처 */
@Serializable data object PhotoCreditsRoute

// 지갑 › 여권 등록 흐름 (중첩 그래프: 흐름 안의 화면이 같은 ViewModel을 쓴다)
/** traveler = "self" 또는 동행자 id (가족 모드) */
@Serializable data class PassportGraph(val traveler: String = "self")
@Serializable data object PassportIntroRoute
@Serializable data object PassportScanRoute
@Serializable data object PassportConfirmRoute
@Serializable data object PassportManualRoute

/** 지갑 › 예약 서류 추가. 다른 앱의 '공유하기'로도 들어온다 */
@Serializable data object BookingImportRoute

/** 홈 › 나라 화면(입국·여행·쇼핑). 인터넷 없이 저장해 둔 팩으로 보여 준다 */
@Serializable data class CountryRoute(val country: String)

/** 준비 › 입국 카드 3개 국어 확인 (PRD 5.2) */
@Serializable data class FormConfirmRoute(val formId: String)

/** 준비 › 공식 사이트 자동 입력 (PRD 5.3) */
@Serializable data class AutofillRoute(val formId: String)

/** 준비 › 수동 모드: 값 복사 + 공식 사이트 */
@Serializable data class FormManualRoute(val formId: String)

/** 여행 만들기·고치기. 나라 화면에서 오면 그 나라를 미리 골라 둔다 */
@Serializable data class TripRoute(val country: String? = null)

/** 입국 때 보여 주기 (PRD 5.4). 자녀 폰 모드의 첫 화면 */
@Serializable data object PresentRoute

/** 지갑 › 같이 가는 사람 (가족 모드) */
@Serializable data object CompanionsRoute

/** 나라 화면 › 이동하기 (PRD 5.9) */
@Serializable data object TransportRoute

/** 준비 › 꼭 챙길 물건 (PRD 5.10) */
@Serializable data object EssentialsRoute

/** 나라 화면 › 쇼핑 리스트 (PRD 5.8) */
@Serializable data class ShoppingRoute(val country: String)
