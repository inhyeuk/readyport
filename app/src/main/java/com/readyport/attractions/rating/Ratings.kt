package com.readyport.attractions.rating

import com.readyport.attractions.Attraction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ======================= 관광지 평점·확인 중 표시 (사장님 결정 2026-10-09 ①, docs/ARIA_OPS.md 12.9·12.10) =======================
// - 레디포트 자체 평점: 한 사람 한 표(attraction_ratings/{CC}_{id}/votes/{uid} {stars, at, visited}) — 만 19세 이상(게시판 기준),
//   집계는 ARIA가 주 1회(attraction_rating_stats/{CC}). 평가가 5명보다 적으면 숨긴다.
// - 확인 중 표시: attraction_flags/{CC} {ids, kind: check_in_progress, at} — 공식 안내가 바뀐 곳에 주의 띠.

/** 관광지 하나의 레디포트 이용자 평점 (공개 집계 — 평균·인원 숫자만) */
data class RatingStats(val avg: Double, val n: Int)

object RatingRules {
    /** 이만큼보다 적은 평가는 보이지 않는다(ARIA RATINGS_MIN_N 기본값과 같다 — 앱이 한 번 더 숨긴다) */
    const val MIN_N = 5

    private val Countries = setOf("TH", "JP", "VN", "PH", "TW", "SG", "MY", "ID", "CN")
    private val KeyPattern = Regex("^(TH|JP|VN|PH|TW|SG|MY|ID|CN)_[a-z0-9]+(-[a-z0-9]+)*$")

    /** 표 문서의 부모 키 `{CC}_{관광지 id}` (규칙 ratingKey — 90자 이하). 맞지 않으면 null(별점 칸을 보이지 않는다) */
    fun key(country: String, id: String): String? = "${country}_$id".takeIf { country in Countries && it.length <= 90 && KeyPattern.matches(it) }

    /** 한 표 문서 (규칙 voteOk 의 칸 그대로). [serverTime] = 서버 시각 자리 */
    fun votePayload(stars: Int, serverTime: Any): Map<String, Any> {
        require(stars in 1..5) { "stars" }
        return mapOf("stars" to stars, "at" to serverTime, "visited" to true)
    }

    /**
     * 집계 문서 attraction_rating_stats/{CC} 에서 이 관광지 값. **관대하게**: 모양이 틀리거나 평균이 1~5 밖이거나
     * 인원이 [MIN_N]보다 적으면 null(숨김). 평균은 소수 한 자리로.
     */
    fun stats(doc: Map<String, Any?>?, id: String, minN: Int = MIN_N): RatingStats? {
        val v = doc?.get(id) as? Map<*, *> ?: return null
        val avg = (v["avg"] as? Number)?.toDouble() ?: return null
        val n = (v["n"] as? Number)?.toLong() ?: return null
        if (avg.isNaN() || avg < 1.0 || avg > 5.0 || n < minN || n > Int.MAX_VALUE) return null
        return RatingStats(Math.round(avg * 10) / 10.0, n.toInt())
    }
}

/** 확인 중 표시 문서 (attraction_flags/{CC}) */
data class AttractionFlags(val ids: Set<String>, val kind: String, val at: Instant?) {
    companion object {
        const val CHECK_IN_PROGRESS = "check_in_progress"

        /** 관대하게 읽기: ids 가 글 목록이 아니면 빈 목록 */
        fun parse(doc: Map<String, Any?>?, at: Instant?): AttractionFlags? {
            if (doc == null) return null
            val ids = (doc["ids"] as? List<*>).orEmpty().filterIsInstance<String>().toSet()
            return AttractionFlags(ids, doc["kind"] as? String ?: "", at)
        }
    }

    /**
     * 이 관광지에 '공식 안내가 바뀌었어요 — 확인 중이에요' 띠를 보일지. 받은 관광지 파일의 운영 상태 확인일이
     * 표시를 단 날 **다음 날 이후**면 이미 반영된 것으로 보고 숨긴다(같은 날이면 띠를 남긴다 — 안전한 쪽).
     */
    fun shows(a: Attraction): Boolean {
        if (kind != CHECK_IN_PROGRESS || a.id !in ids) return false
        val flaggedOn = at?.atZone(ZoneOffset.UTC)?.toLocalDate() ?: return true
        val verified = a.statusVerified?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return true
        return !verified.isAfter(flaggedOn)
    }
}

/** 상세 화면의 내 별점 상태 */
sealed interface MyVote {
    /** 아직 모름(불러오는 중·로그인한 적 없음) — 별점 남기기 버튼만 */
    data object Unknown : MyVote
    data object None : MyVote
    data class Given(val stars: Int) : MyVote
}
