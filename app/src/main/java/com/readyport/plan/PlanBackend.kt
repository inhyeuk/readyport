package com.readyport.plan

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.readyport.doc.ocr.await
import java.time.Instant

/**
 * 계획 요청 서버 호출을 한곳에 (운영: [FirestorePlanBackend], 테스트: 메모리 가짜 — 네트워크 없음).
 * 로그인(익명 게시판 ID)은 [PlanRepository]가 게시판 저장소로 먼저 한다. 실패는 [PlanError]로 바꿔 던진다.
 */
interface PlanBackend {
    fun newRequestId(): String

    /** 내 횟수 기록 (없으면 null) */
    suspend fun quota(uid: String): PlanQuota?

    /** 요청 + plan_quota 를 **한 묶음**(batch)으로. 규칙이 둘을 짝으로 본다 */
    suspend fun create(uid: String, id: String, draft: PlanDraft)

    /** 내 요청 전부 (최근 것 먼저) */
    suspend fun myRequests(uid: String): List<PlanRequest>

    /** 요청 하나 (없거나 볼 수 없으면 null) */
    suspend fun request(id: String): PlanRequest?

    /** 취소 (queued·processing → cancelled, finishedAt = 서버 시각) */
    suspend fun cancel(id: String)

    /** 지우기 (취소한 요청만 — 규칙) */
    suspend fun delete(id: String)

    /** 결과 (아직 없으면 null) */
    suspend fun result(id: String): PlanResult?
}

/** 운영 백엔드 — 칸 이름·모양은 firebase/firestore.rules 와 짝이다(PlanRules.payload·quotaPayload) */
class FirestorePlanBackend(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : PlanBackend {
    private val requests get() = db.collection(REQUESTS)

    override fun newRequestId(): String = requests.document().id

    override suspend fun quota(uid: String): PlanQuota? = guard {
        val d = db.collection(QUOTA).document(uid).get().await()
        if (!d.exists()) null else PlanQuota(d.instant("last"), d.instant("prev"))
    }

    override suspend fun create(uid: String, id: String, draft: PlanDraft) = guard {
        val quotaRef = db.collection(QUOTA).document(uid)
        val current = quotaRef.get().await()
        // 직전 last 는 서버에서 읽은 Timestamp 그대로 prev 에 넣는다(규칙이 같은 값인지 본다)
        val previousLast: Timestamp? = if (current.exists()) current.getTimestamp("last") else null
        val now = FieldValue.serverTimestamp()
        val batch = db.batch()
        batch.set(requests.document(id), PlanRules.payload(draft, uid, now))
        batch.set(quotaRef, PlanRules.quotaPayload(previousLast, id, now))
        batch.commit().await()
        Unit
    }

    override suspend fun myRequests(uid: String): List<PlanRequest> = guard {
        // 규칙: 내 것만 목록으로 읽을 수 있다(uid == 나). 정렬은 여기서(복합 색인 없이)
        requests.whereEqualTo("uid", uid).limit(LIST_MAX).get().await().documents.mapNotNull(::toRequest)
            .sortedByDescending { it.createdAt ?: Instant.EPOCH }
    }

    override suspend fun request(id: String): PlanRequest? = guard {
        try {
            toRequest(requests.document(id).get().await())
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
        }
    }

    override suspend fun cancel(id: String) = guard {
        requests.document(id).update(mapOf("status" to PlanStatus.Cancelled.id, "finishedAt" to FieldValue.serverTimestamp())).await()
        Unit
    }

    override suspend fun delete(id: String) = guard {
        // 결과(있으면)부터 지운다 — 결과는 내 것만 지울 수 있다(규칙). 없으면 그냥 지나간다
        db.collection(RESULTS).document(id).delete().await()
        requests.document(id).delete().await()
        Unit
    }

    override suspend fun result(id: String): PlanResult? = guard {
        val d = db.collection(RESULTS).document(id).get().await()
        if (!d.exists()) {
            null
        } else {
            val data = d.data.orEmpty().toMutableMap()
            data["createdAt"] = d.instant("createdAt")
            PlanResultParser.parse(id, data)
        }
    }

    private fun DocumentSnapshot.instant(field: String): Instant? =
        runCatching { getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.toInstant() }.getOrNull()

    private fun toRequest(d: DocumentSnapshot): PlanRequest? {
        if (!d.exists()) return null
        return PlanRequest(
            id = d.id,
            country = d.getString("country").orEmpty(),
            status = PlanStatus.of(d.getString("status")),
            createdAt = d.instant("createdAt"),
            finishedAt = d.instant("finishedAt"),
            days = (d.get("days") as? Number)?.toInt(),
            startDate = d.get("start_date") as? String,
            endDate = d.get("end_date") as? String,
            failure = PlanFailure.of(d.get("error_code") as? String),
        )
    }

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: PlanError) {
        throw e
    } catch (e: FirebaseNetworkException) {
        throw PlanError.Offline
    } catch (e: FirebaseFirestoreException) {
        throw when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> PlanError.Offline
            FirebaseFirestoreException.Code.NOT_FOUND -> PlanError.NotFound
            else -> PlanError.Denied
        }
    }

    companion object {
        const val REQUESTS = "plan_requests"
        const val QUOTA = "plan_quota"
        const val RESULTS = "plan_results"
        private const val LIST_MAX = 50L
    }
}
