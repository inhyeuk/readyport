package com.readyport.plan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.readyport.board.BoardAge
import com.readyport.board.BoardError
import com.readyport.board.BoardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant

/**
 * 이 휴대폰에만 두는 계획 요청 기록 — **아직 끝나지 않은 내 요청 id**와 마지막 확인 시각만(내용은 두지 않는다).
 * '여행 계획이 도착했어요' 알림을 서버 토큰 없이 앱이 스스로 확인하려고 쓴다(끝나지 않은 요청이 없으면 서버를 읽지 않는다).
 */
interface PlanLocalStore {
    suspend fun pending(): Set<String>
    suspend fun setPending(ids: Set<String>)
    suspend fun lastCheck(): Instant?
    suspend fun setLastCheck(at: Instant)

    /** 이 휴대폰에서 신고한 계획(요청 id) — 신고는 서버에서 다시 읽을 수 없어서(운영자만) '신고함' 표시는 여기로 */
    suspend fun flagged(): Set<String>
    suspend fun setFlagged(ids: Set<String>)
}

class MemoryPlanLocalStore(pending: Set<String> = emptySet()) : PlanLocalStore {
    private var ids = pending
    private var last: Instant? = null
    private var flags = emptySet<String>()
    override suspend fun pending() = ids
    override suspend fun setPending(ids: Set<String>) { this.ids = ids }
    override suspend fun lastCheck() = last
    override suspend fun setLastCheck(at: Instant) { last = at }
    override suspend fun flagged() = flags
    override suspend fun setFlagged(ids: Set<String>) { flags = ids }
}

/** 운영 판 — DataStore `plan` (백업 제외 규칙이 앱 데이터 전체를 뺀다) */
class DataStorePlanLocalStore(private val store: DataStore<Preferences>) : PlanLocalStore {
    private val pendingKey = stringSetPreferencesKey("pending")
    private val lastKey = longPreferencesKey("last_check")
    private val flaggedKey = stringSetPreferencesKey("flagged")
    override suspend fun pending(): Set<String> = store.data.first()[pendingKey].orEmpty()
    override suspend fun setPending(ids: Set<String>) {
        store.edit { if (ids.isEmpty()) it.remove(pendingKey) else it[pendingKey] = ids }
    }
    override suspend fun lastCheck(): Instant? = store.data.first()[lastKey]?.let(Instant::ofEpochMilli)
    override suspend fun setLastCheck(at: Instant) {
        store.edit { it[lastKey] = at.toEpochMilli() }
    }
    override suspend fun flagged(): Set<String> = store.data.first()[flaggedKey].orEmpty()
    override suspend fun setFlagged(ids: Set<String>) {
        store.edit { if (ids.isEmpty()) it.remove(flaggedKey) else it[flaggedKey] = ids }
    }
}

/**
 * 여행 계획 요청 살림살이: 서버([PlanBackend]) + 이 휴대폰 기록([PlanLocalStore]) + 나이 확인·익명 로그인(게시판과 같은 [BoardRepository]).
 * 로그인은 **처음 요청을 보낼 때만** — 목록·남은 횟수는 로그인한 적이 없으면 서버에 묻지 않는다.
 */
class PlanRepository(
    private val backend: PlanBackend,
    private val board: BoardRepository,
    private val local: PlanLocalStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lock = Mutex()
    private val _revision = MutableStateFlow(0L)

    /** 요청을 보내거나 취소·삭제할 때마다 오른다 — 목록 화면이 이때 다시 읽는다 */
    val revision: StateFlow<Long> = _revision.asStateFlow()
    private fun bump() = _revision.update { it + 1 }

    /** 푸시로 도착을 알았다 — 끝나지 않은 목록에서 빼서(앱을 켤 때 같은 알림이 또 뜨지 않게) 화면 목록을 새로 읽게 한다 */
    suspend fun markArrived(id: String) {
        val pending = local.pending()
        if (id in pending) local.setPending(pending - id)
        bump()
    }

    fun signedIn(): Boolean = board.uid() != null

    /** 계획 도착 푸시를 받도록 내 토픽을 구독한다(전에 낸 요청이 있는 기기도). 로그인한 적이 없으면 아무것도 하지 않는다 */
    fun ensurePush() {
        board.uid()?.let { backend.subscribePush(it) }
    }

    suspend fun ageStatus(): BoardAge.Status = board.ageStatus()

    /** 내 나라별 횟수 기록 (로그인한 적이 없으면 null — 서버에 묻지 않는다) */
    suspend fun quota(): PlanQuota? {
        val uid = board.uid() ?: return null
        return mapped { backend.quota(uid) }
    }

    /** 이 나라에 더 보낼 수 있는 횟수 (로그인한 적이 없으면 한도 그대로) */
    suspend fun remaining(country: String): PlanRules.Remaining = PlanRules.remaining(quota(), country)

    /**
     * 요청 보내기: 양식 검사 → 만 19세 확인 + 익명 로그인 → 남은 횟수(규칙과 같은 셈) → 요청 + 횟수 기록 한 묶음.
     * @return 새 요청 id
     */
    suspend fun submit(draft: PlanDraft, allowWarnings: Boolean): String = lock.withLock {
        if (!PlanRules.check(draft).ready(allowWarnings)) throw PlanError.Invalid
        val uid = mapped { board.adultUid() }
        val country = draft.country ?: throw PlanError.Invalid
        val left = PlanRules.remaining(mapped { backend.quota(uid) }, country)
        if (left.count <= 0) throw PlanError.QuotaUsed(country)
        val id = backend.newRequestId()
        mapped { backend.create(uid, id, draft) }
        local.setPending(local.pending() + id)
        bump()
        id
    }

    /** 내 요청 (로그인한 적이 없으면 빈 목록). 끝나지 않은 요청 기록을 서버 목록에 맞춘다 — 이 화면에서 본 도착은 알리지 않는다 */
    suspend fun myRequests(): List<PlanRequest> {
        val uid = board.uid() ?: return emptyList()
        val list = mapped { backend.myRequests(uid) }
        local.setPending(list.filter { it.status.cancellable }.map { it.id }.toSet())
        return list
    }

    suspend fun request(id: String): PlanRequest? = if (board.uid() == null) null else mapped { backend.request(id) }

    suspend fun cancel(req: PlanRequest) {
        if (!req.status.cancellable) throw PlanError.Denied
        mapped { backend.cancel(req.id) }
        local.setPending(local.pending() - req.id)
        bump()
    }

    suspend fun delete(req: PlanRequest) {
        if (!req.status.deletable) throw PlanError.Denied
        mapped { backend.delete(req.id) }
        // 신고 기록(서버)은 이용자가 지울 수 없다 — 다음 ARIA 정리 때 요청과 함께 지워진다. 이 휴대폰 표시만 뺀다
        if (req.id in local.flagged()) local.setFlagged(local.flagged() - req.id)
        bump()
    }

    suspend fun result(id: String): PlanResult? = if (board.uid() == null) null else mapped { backend.result(id) }

    /** 이 휴대폰에서 이 계획을 신고했는지 */
    suspend fun isFlagged(id: String): Boolean = id in local.flagged()

    /**
     * AI 계획 신고 (Play 'AI 생성 콘텐츠' 정책 — 앱 안에서). 계획 하나에 한 번, 운영자만 본다.
     * 메모 검사(200자·여권/주민번호 같은 글자) → plan_flags/{id} 만들기 → 이 휴대폰에 '신고함' 기억.
     * 서버에 이미 있으면([PlanError.AlreadyFlagged]) 신고한 것으로 본다(앱을 지웠다 깔았거나 다른 화면에서 이미 보냄).
     */
    suspend fun flag(id: String, reason: PlanFlagReason, note: String) {
        val uid = board.uid() ?: throw PlanError.Denied
        if (PlanRules.checkFlagNote(note).blocked) throw PlanError.Invalid
        try {
            mapped { backend.flag(uid, id, reason, note) }
        } catch (e: PlanError.AlreadyFlagged) {
            // 이미 신고됨 — 아래에서 '신고함'으로 기억한다
        }
        local.setFlagged(local.flagged() + id)
    }

    /**
     * 끝나지 않은 내 요청 가운데 **새로 도착한(done) 것** (앱을 켤·돌아올 때와 하루 한 번). 끝나지 않은 요청이 없으면 서버를 읽지 않는다.
     * [minGapSeconds] 안에 또 부르면 건너뛴다(화면을 오가며 여러 번 읽지 않게). 실패·취소·없어진 요청은 기록에서 뺀다.
     */
    suspend fun arrivals(minGapSeconds: Long = 0): List<String> {
        if (board.uid() == null) return emptyList()
        val pending = local.pending()
        if (pending.isEmpty()) return emptyList()
        val now = clock.instant()
        val last = local.lastCheck()
        if (minGapSeconds > 0 && last != null && last.plusSeconds(minGapSeconds).isAfter(now)) return emptyList()
        local.setLastCheck(now)
        val done = mutableListOf<String>()
        val still = mutableSetOf<String>()
        for (id in pending) {
            val r = runCatching { backend.request(id) }.getOrElse {
                // 인터넷이 없으면 다음에 다시
                still += id
                null
            } ?: continue
            when {
                r.status == PlanStatus.Done -> done += id
                r.status.cancellable -> still += id
                else -> Unit
            }
        }
        local.setPending(still)
        if (done.isNotEmpty()) bump()
        return done
    }

    private suspend fun <T> mapped(block: suspend () -> T): T = try {
        block()
    } catch (e: BoardError) {
        throw when (e) {
            BoardError.Offline -> PlanError.Offline
            BoardError.AuthUnavailable -> PlanError.AuthUnavailable
            is BoardError.AgeRestricted -> PlanError.AgeRestricted(e.from)
            BoardError.AgeCheckNeeded -> PlanError.AgeCheckNeeded
            BoardError.NotFound -> PlanError.NotFound
            else -> PlanError.Denied
        }
    }
}
