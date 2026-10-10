package com.readyport.plan

import com.readyport.board.TestClock
import java.time.Instant

/**
 * 메모리 안 가짜 계획 서버 (네트워크 없음). 규칙(firebase/firestore.rules newPlanOk·plan_quota)이 막는 것 중 앱이 기대는 것을 같은 뜻으로 흉내 낸다:
 * 요청 모양은 [PlanRules.payload]로 만든 것만 받고, 7일 2회는 직전 prev 로 막고, 취소는 queued·processing 만, 삭제는 cancelled 만,
 * 신고는 내 결과에 한 번만(두 번째는 [PlanError.AlreadyFlagged]).
 * 규칙 자체는 tools/firestore/rules.test.mjs 가 에뮬레이터로 따로 검사한다.
 */
class FakePlanBackend(private val clock: TestClock = TestClock()) : PlanBackend {
    var offline = false
    var seq = 0
    var reads = 0
    val docs = linkedMapOf<String, Map<String, Any>>()
    val owners = mutableMapOf<String, String>()
    val statuses = mutableMapOf<String, PlanStatus>()
    val quotas = mutableMapOf<String, PlanQuota>()
    val results = mutableMapOf<String, PlanResult>()
    val createdAt = mutableMapOf<String, Instant>()

    private fun online() {
        if (offline) throw PlanError.Offline
    }

    override fun newRequestId(): String = "req${++seq}"

    override suspend fun quota(uid: String): PlanQuota? {
        online()
        reads++
        return quotas[uid]
    }

    override suspend fun create(uid: String, id: String, draft: PlanDraft) {
        online()
        val now = clock.instant()
        val payload = PlanRules.payload(draft, uid, SERVER_TIME)
        val q = quotas[uid]
        // 규칙: 이 나라 누적 횟수가 한도(2 + extra) 이상이면 거절
        val country = draft.country!!
        if (PlanRules.remaining(q, country).count <= 0) throw PlanError.Denied
        docs[id] = payload
        owners[id] = uid
        statuses[id] = PlanStatus.Queued
        createdAt[id] = now
        quotas[uid] = PlanQuota(last = now, prev = null, counts = (q?.counts ?: emptyMap()) + (country to ((q?.counts?.get(country) ?: 0) + 1)), extra = q?.extra ?: emptyMap())
    }

    private fun toRequest(id: String): PlanRequest {
        val d = docs.getValue(id)
        return PlanRequest(
            id = id,
            country = d["country"] as String,
            status = statuses.getValue(id),
            createdAt = createdAt[id],
            finishedAt = null,
            days = d["days"] as? Int,
            startDate = d["start_date"] as? String,
            endDate = d["end_date"] as? String,
        )
    }

    override suspend fun myRequests(uid: String): List<PlanRequest> {
        online()
        reads++
        return owners.filterValues { it == uid }.keys.map(::toRequest).sortedByDescending { it.createdAt }
    }

    override suspend fun request(id: String): PlanRequest? {
        online()
        reads++
        return if (id in docs) toRequest(id) else null
    }

    override suspend fun cancel(id: String) {
        online()
        if (statuses[id]?.cancellable != true) throw PlanError.Denied
        statuses[id] = PlanStatus.Cancelled
    }

    override suspend fun delete(id: String) {
        online()
        if (statuses[id] != PlanStatus.Cancelled) throw PlanError.Denied
        docs.remove(id)
        statuses.remove(id)
        owners.remove(id)
    }

    override suspend fun result(id: String): PlanResult? {
        online()
        return results[id]
    }

    /** plan_flags/{id} (규칙: 내 결과에만, 한 번 — 고치기·지우기 불가). 값은 [PlanRules.flagPayload] 모양 그대로 */
    val flags = linkedMapOf<String, Map<String, Any>>()

    /** 결과 주인 (규칙이 plan_results/{id}.uid 를 본다) — 테스트가 정한다 */
    val resultOwners = mutableMapOf<String, String>()

    override suspend fun flag(uid: String, id: String, reason: PlanFlagReason, note: String) {
        online()
        val payload = PlanRules.flagPayload(reason, note, uid, SERVER_TIME)
        if (resultOwners[id] != uid) throw PlanError.Denied
        if (id in flags) throw PlanError.AlreadyFlagged
        flags[id] = payload
    }

    companion object {
        /** 서버 시각 자리 표시 */
        const val SERVER_TIME = "<serverTimestamp>"
    }
}
