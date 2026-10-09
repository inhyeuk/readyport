package com.readyport.board

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * 게시판 쓰기 나이 확인 (2026-10-09 사장님 결정 — docs/design/attractions/DECISIONS_2026-10-09.md).
 *
 * - 근거는 **이 휴대폰 보관함에 저장된 본인 여권의 생년월일**. 생년월일 입력 화면은 두지 않는다.
 * - 기준은 **만 19세**. 미만이면 글·댓글·추천·신고·채택을 막고 읽기는 그대로.
 * - 여권이 없으면 성인으로 본다("해외여행은 성인이 하는 것으로 기본 가정").
 * - 생년월일은 저장·전송하지 않는다. 기기에 남기는 값은 [stamp] 하나 — `adult`, `assumed`, 또는 `minor:YYYY-MM`
 *   (쓰기가 풀리는 달. 19번째 생일이 있는 달의 **다음 달** 1일로 올려 잡아 날짜까지는 남기지 않는다).
 */
object BoardAge {
    const val MIN_AGE = 19

    const val ADULT = "adult"

    /** 보관함을 열어 봤지만 본인 여권이 없음 — 성인으로 본다. 나중에 여권을 넣으면 다시 계산한다 */
    const val ASSUMED = "assumed"
    private const val MINOR = "minor:"

    sealed interface Status {
        data object Allowed : Status

        /** [from] 달 1일부터 쓸 수 있다 */
        data class Minor(val from: YearMonth) : Status

        /** 보관함에 데이터가 있는데 아직 한 번도 열어 보지 않아 나이를 모름 — 기기 인증 후 보관함을 열면 정해진다 */
        data object NeedsCheck : Status
    }

    /**
     * 보관함 여권의 생년월일(yyyy-MM-dd) → 기기에 남길 값. 여권이 없거나 날짜를 읽을 수 없으면 [ASSUMED].
     */
    fun stamp(birthDate: String?, today: LocalDate): String {
        val birth = birthDate?.let {
            try {
                LocalDate.parse(it.trim())
            } catch (e: DateTimeParseException) {
                null
            }
        } ?: return ASSUMED
        // 2월 29일생은 plusYears가 2월 28일로 맞춘다 — 하루 이르지만 아래에서 다음 달로 올려 잡으므로 영향 없음
        val adultOn = birth.plusYears(MIN_AGE.toLong())
        if (!today.isBefore(adultOn)) return ADULT
        return MINOR + YearMonth.from(adultOn).plusMonths(1)
    }

    /** 남긴 값 + 보관함에 데이터가 있는지 → 지금 쓸 수 있는지 */
    fun status(stamp: String?, walletHasData: Boolean, today: LocalDate): Status = when {
        stamp == ADULT || stamp == ASSUMED -> Status.Allowed
        stamp != null && stamp.startsWith(MINOR) -> {
            val from = runCatching { YearMonth.parse(stamp.removePrefix(MINOR)) }.getOrNull()
            when {
                from == null -> Status.NeedsCheck
                YearMonth.from(today) >= from -> Status.Allowed
                else -> Status.Minor(from)
            }
        }
        walletHasData -> Status.NeedsCheck
        else -> Status.Allowed
    }
}
