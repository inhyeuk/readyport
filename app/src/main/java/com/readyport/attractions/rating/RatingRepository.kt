package com.readyport.attractions.rating

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.readyport.board.BoardAge
import com.readyport.board.BoardError
import com.readyport.board.BoardRepository
import com.readyport.doc.ocr.await
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** 평점·확인 중 표시 서버 호출 (운영: [FirestoreRatingBackend], 테스트: 메모리 가짜) */
interface RatingBackend {
    /** 내 한 표 (없으면 null) */
    suspend fun myVote(key: String, uid: String): Int?
    suspend fun setVote(key: String, uid: String, stars: Int)
    suspend fun deleteVote(key: String, uid: String)

    /** 나라 집계 문서 그대로(없으면 null) */
    suspend fun stats(country: String): Map<String, Any?>?

    suspend fun flags(country: String): AttractionFlags?
}

class FirestoreRatingBackend(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : RatingBackend {
    private fun voteRef(key: String, uid: String) = db.collection(RATINGS).document(key).collection(VOTES).document(uid)

    override suspend fun myVote(key: String, uid: String): Int? = guard {
        val d = voteRef(key, uid).get().await()
        (d.get("stars") as? Number)?.toInt()?.takeIf { d.exists() && it in 1..5 }
    }

    override suspend fun setVote(key: String, uid: String, stars: Int) = guard {
        // 만들기·고치기 모두 칸 전체를 다시 쓴다(규칙: stars·at·visited 만, at = 서버 시각)
        voteRef(key, uid).set(RatingRules.votePayload(stars, FieldValue.serverTimestamp())).await()
        Unit
    }

    override suspend fun deleteVote(key: String, uid: String) = guard {
        voteRef(key, uid).delete().await()
        Unit
    }

    override suspend fun stats(country: String): Map<String, Any?>? = guard {
        val d = db.collection(STATS).document(country).get().await()
        if (d.exists()) d.data else null
    }

    override suspend fun flags(country: String): AttractionFlags? = guard {
        val d = db.collection(FLAGS).document(country).get().await()
        if (!d.exists()) null else AttractionFlags.parse(d.data, d.instant("at"))
    }

    private fun DocumentSnapshot.instant(field: String): Instant? = runCatching { getTimestamp(field)?.toDate()?.toInstant() }.getOrNull()

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: FirebaseNetworkException) {
        throw BoardError.Offline
    } catch (e: FirebaseFirestoreException) {
        throw when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> BoardError.Offline
            else -> BoardError.Denied
        }
    }

    companion object {
        const val RATINGS = "attraction_ratings"
        const val VOTES = "votes"
        const val STATS = "attraction_rating_stats"
        const val FLAGS = "attraction_flags"
    }
}

/**
 * 관광지 평점·확인 중 표시. 집계·표시는 나라마다 한 문서라 **이번 앱 실행 동안 메모리에** 잠깐 둔다(10분) — 상세를 열 때마다 읽지 않게.
 * 내 별점 쓰기는 만 19세 확인 + 익명 로그인(게시판과 같은 [BoardRepository.adultUid]). 읽기는 로그인하지 않는다.
 */
class RatingRepository(
    private val backend: RatingBackend,
    private val board: BoardRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private data class Cached<T>(val at: Instant, val value: T?)

    private val statsCache = mutableMapOf<String, Cached<Map<String, Any?>>>()
    private val flagsCache = mutableMapOf<String, Cached<AttractionFlags>>()

    suspend fun ageStatus(): BoardAge.Status = board.ageStatus()

    /** 이 관광지의 공개 평점 (평가가 적거나 없거나 못 읽으면 null — 화면에서 숨긴다) */
    suspend fun stats(country: String, id: String): RatingStats? {
        val doc = cached(statsCache, country) { backend.stats(country) }
        return RatingRules.stats(doc, id)
    }

    /** 확인 중 표시 (못 읽으면 null — 띠 없음, 오프라인 우선) */
    suspend fun flags(country: String): AttractionFlags? = cached(flagsCache, country) { backend.flags(country) }

    /** 내 별점: 로그인한 적이 없으면 [MyVote.None](계정을 만들지 않는다) */
    suspend fun myVote(country: String, id: String): MyVote {
        val key = RatingRules.key(country, id) ?: return MyVote.None
        val uid = board.uid() ?: return MyVote.None
        return runCatching { backend.myVote(key, uid) }.fold({ it?.let(MyVote::Given) ?: MyVote.None }, { MyVote.Unknown })
    }

    /** 별점 남기기·바꾸기 (1~5). 미성년·나이 모름이면 [BoardError.AgeRestricted]·[BoardError.AgeCheckNeeded] */
    suspend fun vote(country: String, id: String, stars: Int) {
        val key = RatingRules.key(country, id) ?: throw BoardError.Denied
        require(stars in 1..5) { "stars" }
        val uid = board.adultUid()
        backend.setVote(key, uid, stars)
    }

    /** 내 별점 지우기 (로그인한 적이 없으면 지울 것도 없다) */
    suspend fun removeVote(country: String, id: String) {
        val key = RatingRules.key(country, id) ?: return
        val uid = board.uid() ?: return
        backend.deleteVote(key, uid)
    }

    private suspend fun <T> cached(map: MutableMap<String, Cached<T>>, country: String, load: suspend () -> T?): T? {
        val now = clock.instant()
        synchronized(map) { map[country] }?.takeIf { Duration.between(it.at, now) < TTL }?.let { return it.value }
        val value = runCatching { load() }.getOrElse { return null }
        synchronized(map) { map[country] = Cached(now, value) }
        return value
    }

    private companion object {
        val TTL: Duration = Duration.ofMinutes(10)
    }
}
