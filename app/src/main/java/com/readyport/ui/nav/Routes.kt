package com.readyport.ui.nav

import kotlinx.serialization.Serializable

/** 둘러보기: 나라 사진 카드로 어디 갈지 고르고 여행을 만든다 (여행 흐름이 아닌 '생각' 단계) */
@Serializable data object HomeRoute
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

/**
 * 지갑 › 예약 서류 추가. 다른 앱의 '공유하기'로도 들어온다.
 * [tripId]: 여행의 `묵는 곳`에서 들어왔으면 그 여행 — 숙소 서류를 저장할 때 그 여행 숙소로 붙인다.
 */
@Serializable data class BookingImportRoute(val tripId: String? = null)

/**
 * 내 여행 › 묵는 곳 하나 넣기·고치기 (2026-10-03). [stayId]가 없으면 새 숙소.
 * [tripId]: 그 여행의 예약 단계에서 들어왔으면 그 여행 — 설정 › 내 정보의 묵는 곳 목록에서 들어오면 null이고,
 * 그때는 숙소가 원래 붙어 있던 여행을 그대로 둔다 (다듬기 S2).
 * 주소가 있어 암호화 보관함에만 저장하고 화면 캡처를 막는다(FLAG_SECURE).
 */
@Serializable data class StayEditRoute(val tripId: String? = null, val stayId: String? = null)

/**
 * 둘러보기 › 나라 화면(입국·여행·쇼핑). 인터넷 없이 저장해 둔 팩으로 보여 준다.
 * [focusAirports]: 입국·비자의 `공항에 도착하면` 묶음으로 바로 내려간다(체크리스트의 `공항 순서 보기`), [airport]: 처음 고를 공항(IATA).
 * [tripId]: 어느 여행에서 열었는지 — 있으면 머리에 `이 여행` 띠와 돌아가는 길을 보인다(2026-10-03).
 */
@Serializable data class CountryRoute(
    val country: String,
    val focusAirports: Boolean = false,
    val airport: String? = null,
    val tripId: String? = null,
)

/** 준비 › 입국 카드 3개 국어 확인 (PRD 5.2) */
@Serializable data class FormConfirmRoute(val formId: String)

/** 준비 › 공식 사이트 자동 입력 (PRD 5.3) */
@Serializable data class AutofillRoute(val formId: String)

/** 준비 › 수동 모드: 값 복사 + 공식 사이트 */
@Serializable data class FormManualRoute(val formId: String)

/**
 * 여행 만들기·고치기. 나라 화면에서 오면 그 나라를 미리 골라 둔다.
 * [tripId]가 있으면 그 여행 고치기, 없으면 새 여행(여행은 id로 가린다 — 같은 나라라도 날짜가 다르면 다른 여행)
 */
@Serializable data class TripRoute(val country: String? = null, val tripId: String? = null)

/** 내 여행 탭의 첫 화면 — 여행 목록 (여행 중·다가오는 여행·지난 여행) */
@Serializable data object TripsRoute

/**
 * 내 여행 › **한 여행 화면** — 여행 과정 8단계 막대 + 지금 할 일 + 단계마다 할 일·안내.
 * 이 여행의 모든 것이 여기서 닿는다. 챙길 일 알림이 여는 곳이기도 하다(딥링크, 이름 그대로 둔다).
 */
@Serializable data class TripChecklistRoute(val tripId: String)

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

/** 나라 화면 › YouTube 여행 영상 (최대 50개) */
@Serializable data class VideosRoute(val country: String)

/**
 * 설정 › 공지·소식 › 공지사항 (지금 공지·지난 공지 다시 보기). [openId]: 공지 알림을 눌러 왔을 때 바로 열 공지 id
 * (없거나 목록에 없으면 목록만 — 서명본을 못 받았을 때의 `새 소식이 있어요` 알림도 여기로 온다)
 */
@Serializable data class NoticesRoute(val openId: String? = null)
