package com.readyport.plan

import java.time.Instant

// ======================= 여행 계획 요청 (사장님 결정 2026-10-09 ②, docs/ARIA_OPS.md 12.11) =======================
// **비공개**: 요청과 결과는 본인과 운영자만 읽는다(firebase/firestore.rules plan_requests·plan_quota·plan_results).
// 게시판 글이 아니다 — 게시판 탭 맨 위 카드와 여행 계획 단계 타일에서 들어온다. 만 19세 이상만(게시판과 같은 BoardAge).
// 값 이름은 규칙·ARIA(ops/aria/jobs/plan_requests.py)와 같다 — 한쪽을 바꾸면 PlanContractTest 가 깨진다.

/** 여행 목적 (1~5개, 규칙 purposesOk) */
enum class PlanPurpose(val id: String) {
    Sightseeing("sightseeing"),
    Food("food"),
    Shopping("shopping"),
    Nature("nature"),
    HistoryCulture("history_culture"),
    Relaxation("relaxation"),
    KidsFamily("kids_family"),
    Activity("activity"),
    Other("other"),
    ;

    companion object {
        fun of(id: String?): PlanPurpose? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 이동할 때 도움이 필요한 점 (선택) — **민감정보**(개인정보 보호법 제23조). 진단명 없이 고르기만,
 * 하나라도 고르면 별도 동의(sensitive_consent == true)가 있어야 규칙이 받는다. 규칙에 있는 `other_none`은 화면에서 쓰지 않는다.
 */
enum class PlanMobility(val id: String) {
    LongWalkHard("long_walk_hard"),
    Wheelchair("wheelchair"),
    StairsHard("stairs_hard"),
    WithInfant("with_infant"),
    ;

    companion object {
        fun of(id: String?): PlanMobility? = entries.firstOrNull { it.id == id }
    }
}

/** 예산 등급 (원화 기준, 금액은 화면 안내용 — AI에는 등급만 간다) */
enum class BudgetBand(val id: String) {
    Budget("budget"),
    Standard("standard"),
    Comfort("comfort"),
    Premium("premium"),
    ;

    companion object {
        fun of(id: String?): BudgetBand? = entries.firstOrNull { it.id == id }
    }
}

/** 요청 상태. 이용자는 [Queued]·[Processing]일 때 [Cancelled]로만 바꿀 수 있다(규칙) */
enum class PlanStatus(val id: String) {
    Queued("queued"),
    Processing("processing"),
    Done("done"),
    Failed("failed"),
    Cancelled("cancelled"),

    /** 모르는 값(앞으로 생길 상태) — 취소·삭제를 보이지 않는다 */
    Unknown(""),
    ;

    /** 취소할 수 있는지 (규칙: queued·processing → cancelled) */
    val cancellable: Boolean get() = this == Queued || this == Processing

    /** 지울 수 있는지 (규칙: 끝난 내 요청 — 취소·완료·실패. 사장님 결정 2026-10-09) */
    val deletable: Boolean get() = this == Cancelled || this == Done || this == Failed

    /** 끝났는지 (끝난 날부터 30일 뒤 ARIA 정리 작업이 지운다 — 취소는 빼고) */
    val autoDeleted: Boolean get() = this == Done || this == Failed

    companion object {
        fun of(raw: String?): PlanStatus = entries.firstOrNull { it != Unknown && it.id == raw } ?: Unknown
    }
}

/** 실패 이유 (ARIA error_code) — 화면 문구로 바꾼다 */
enum class PlanFailure(val id: String) {
    InvalidRequest("invalid_request"),
    QuotaExceeded("quota_exceeded"),
    CountryUnavailable("country_unavailable"),
    EngineError("engine_error"),
    EngineTimeout("engine_timeout"),
    InvalidOutput("invalid_output"),
    ;

    companion object {
        fun of(raw: String?): PlanFailure? = entries.firstOrNull { it.id == raw }
    }
}

/**
 * AI 계획 신고 이유 (Play 'AI 생성 콘텐츠' 정책 — 앱 안에서 신고). 규칙 flagReason 과 같은 값·순서.
 * 신고는 plan_flags/{요청 id} 에 계획 하나당 한 번, 운영자만 읽는다.
 */
enum class PlanFlagReason(val id: String) {
    Inaccurate("inaccurate"),
    Inappropriate("inappropriate"),
    Unsafe("unsafe"),
    Other("other"),
    ;

    companion object {
        fun of(id: String?): PlanFlagReason? = entries.firstOrNull { it.id == id }
    }
}

/** 여행 기간: 여행 날짜 그대로 또는 며칠인지만 (규칙 datesOk — 둘 중 하나만) */
sealed interface PlanDates {
    data class Range(val start: String, val end: String) : PlanDates
    data class Days(val count: Int) : PlanDates
}

/** 함께 가는 사람 (0~20명씩, 합 1~20). 성별 인원은 선택 */
data class PlanTravelers(
    val adults: Int = 1,
    val seniors: Int = 0,
    val teens: Int = 0,
    val children: Int = 0,
    val female: Int? = null,
    val male: Int? = null,
) {
    val total: Int get() = adults + seniors + teens + children
}

/** 양식에서 고른 값 (아직 보내지 않은 것) */
data class PlanDraft(
    val country: String? = null,
    val dates: PlanDates = PlanDates.Days(3),
    val purposes: List<PlanPurpose> = emptyList(),
    val note: String = "",
    val travelers: PlanTravelers = PlanTravelers(),
    val mobility: Set<PlanMobility> = emptySet(),
    /** 민감정보 별도 동의 — [mobility]가 비어 있으면 보내지 않는다 */
    val sensitiveConsent: Boolean = false,
    val budget: BudgetBand? = null,
)

/** 내 요청 한 건 (목록) */
data class PlanRequest(
    val id: String,
    val country: String,
    val status: PlanStatus,
    val createdAt: Instant?,
    val finishedAt: Instant?,
    val days: Int?,
    val startDate: String?,
    val endDate: String?,
    val failure: PlanFailure? = null,
)

/**
 * 계획 요청 횟수 기록 (plan_quota/{uid}) — **나라별 누적 횟수**(counts)와 마지막 요청 시각뿐이다.
 * [extra]는 나중에 유료로 늘린 나라별 추가 횟수(서버만 쓴다 — 앱은 읽어서 그대로 되돌려 쓸 뿐). [prev]는 예전(7일 2회) 기록이라 더는 쓰지 않는다.
 */
data class PlanQuota(
    val last: Instant?,
    val prev: Instant?,
    val counts: Map<String, Int> = emptyMap(),
    val extra: Map<String, Int> = emptyMap(),
)

/** 계획 요청 실패 이유 — 화면이 쉬운 문구로 바꾼다 */
sealed class PlanError(message: String) : Exception(message) {
    data object Offline : PlanError("offline")
    data object AuthUnavailable : PlanError("auth")
    data object Denied : PlanError("denied")

    /** 이 나라는 2번을 다 씀(나라별 한도) */
    data class QuotaUsed(val country: String?) : PlanError("quota")
    data object NotFound : PlanError("not_found")
    data class AgeRestricted(val from: java.time.YearMonth) : PlanError("age")
    data object AgeCheckNeeded : PlanError("age_check")

    /** 양식이 아직 덜 됨(화면이 보내기 버튼을 막으므로 보통은 오지 않는다) */
    data object Invalid : PlanError("invalid")

    /** 이 계획은 이미 신고함(서버에 신고 문서가 있다) — 화면은 '신고함'으로 본다 */
    data object AlreadyFlagged : PlanError("already_flagged")
}
