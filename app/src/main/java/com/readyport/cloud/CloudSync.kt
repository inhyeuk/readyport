package com.readyport.cloud

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.readyport.autofill.FieldReport
import com.readyport.autofill.QueuedFieldReporter
import com.readyport.data.settings.SettingsRepository
import com.readyport.trip.TripRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 서버와 주고받는 것은 이 셋뿐이다 (ARCHITECTURE 9.4·9.6). 개인정보·여행 일정은 보내지 않는다.
 * 1) 익명 실패 리포트 → Firestore field_reports (생성만 허용 규칙)
 * 2) 찜 수 +1 → Firestore favorite_counts/{ISO2} (나라마다 기기당 한 번)
 * 3) FCM 토픽 country_{ISO2} 구독 (찜했거나 여행 가는 나라)
 */
data class SyncPlan(
    val countFavorites: Set<String>,
    val subscribe: Set<String>,
    val unsubscribe: Set<String>,
)

object CloudSyncPlan {
    private val ISO2 = Regex("^[A-Z]{2}$")

    fun topic(country: String) = "country_$country"

    /** 순수 함수: 지금 상태와 이미 한 일을 비교해 할 일을 정한다 */
    fun plan(favorites: Set<String>, tripCountry: String?, counted: Set<String>, subscribed: Set<String>): SyncPlan {
        val wanted = (favorites + listOfNotNull(tripCountry)).filter { ISO2.matches(it) }.map(::topic).toSet()
        return SyncPlan(
            countFavorites = favorites.filter { ISO2.matches(it) }.toSet() - counted,
            subscribe = wanted - subscribed,
            unsubscribe = subscribed - wanted,
        )
    }

    /** 실패 리포트 보관 기간 — 지나면 매월 purge-reports 작업이 지운다 (개인정보처리방침) */
    const val REPORT_RETENTION_DAYS = 365L

    fun expiryMillis(nowMillis: Long) = nowMillis + REPORT_RETENTION_DAYS * 86_400_000L

    private val ID = Regex("^[A-Za-z0-9_.-]+$")
    private val CODES = setOf("selector_missing", "site_version_changed", "engine_error", "manual_mode_chosen", "kill_switch")

    private fun ok(v: String?, max: Int) = v != null && v.length in 1..max && ID.matches(v)

    /**
     * Firestore 규칙(firebase/firestore.rules)과 같은 검사. 통과하지 못하면 보내지 않고 버린다.
     * ts는 서버 시각으로 채운다(규칙이 request.time과 같은지 본다).
     */
    fun toFirestore(r: FieldReport): Map<String, Any>? {
        if (!ok(r.formId, 40) || !ok(r.packVersion, 40) || !ok(r.stepId, 60) || !ok(r.appVersion, 30)) return null
        if (r.errorCode !in CODES) return null
        if (r.siteVersion != null && !ok(r.siteVersion, 60)) return null
        return buildMap {
            put("form_id", r.formId)
            put("pack_version", r.packVersion)
            put("step_id", r.stepId)
            put("error_code", r.errorCode)
            put("app_version", r.appVersion)
            r.siteVersion?.let { put("site_version", it) }
        }
    }
}

/** 서버 호출을 한곳에 모은다 (테스트에서는 가짜로 바꾼다) */
interface CloudBackend {
    fun addReport(fields: Map<String, Any>)
    fun incrementFavorite(country: String)
    fun subscribe(topic: String)
    fun unsubscribe(topic: String)
}

class FirebaseCloudBackend : CloudBackend {
    private fun <T> Task<T>.block(): T = Tasks.await(this, 30, TimeUnit.SECONDS)

    override fun addReport(fields: Map<String, Any>) {
        val expire = Timestamp(java.util.Date(CloudSyncPlan.expiryMillis(System.currentTimeMillis())))
        FirebaseFirestore.getInstance().collection("field_reports")
            .add(fields + mapOf("ts" to FieldValue.serverTimestamp(), "expire_at" to expire)).block()
    }

    override fun incrementFavorite(country: String) {
        // 문서가 없으면 1로 만들어지고, 있으면 +1 (규칙: create는 1, update는 +1만)
        FirebaseFirestore.getInstance().collection("favorite_counts").document(country)
            .set(mapOf("count" to FieldValue.increment(1)), SetOptions.merge()).block()
    }

    override fun subscribe(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic).block()
    }

    override fun unsubscribe(topic: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).block()
    }
}

private val Context.cloudStore: DataStore<Preferences> by preferencesDataStore(name = "cloud")

/** 이미 한 일(찜 수를 올린 나라, 구독한 토픽)을 기억한다 */
@Singleton
class CloudState @Inject constructor(@ApplicationContext private val context: Context) {
    private val countedKey = stringSetPreferencesKey("counted_favorites")
    private val topicsKey = stringSetPreferencesKey("subscribed_topics")

    suspend fun counted(): Set<String> = context.cloudStore.data.map { it[countedKey].orEmpty() }.first()
    suspend fun topics(): Set<String> = context.cloudStore.data.map { it[topicsKey].orEmpty() }.first()

    suspend fun addCounted(country: String) = context.cloudStore.edit { it[countedKey] = it[countedKey].orEmpty() + country }
    suspend fun setTopic(topic: String, on: Boolean) = context.cloudStore.edit {
        val now = it[topicsKey].orEmpty()
        it[topicsKey] = if (on) now + topic else now - topic
    }
}

/** 한 번의 동기화. 하나가 실패해도 나머지는 계속하고, 실패가 있으면 나중에 다시 한다 */
class CloudSyncRunner(
    private val backend: CloudBackend,
    private val queue: QueuedFieldReporter,
    private val state: CloudState,
) {
    suspend fun run(favorites: Set<String>, tripCountry: String?): Boolean {
        var allOk = true
        val plan = CloudSyncPlan.plan(favorites, tripCountry, state.counted(), state.topics())
        plan.countFavorites.forEach { c ->
            runCatching { backend.incrementFavorite(c) }.onSuccess { state.addCounted(c) }.onFailure { allOk = false }
        }
        plan.subscribe.forEach { t ->
            runCatching { backend.subscribe(t) }.onSuccess { state.setTopic(t, true) }.onFailure { allOk = false }
        }
        plan.unsubscribe.forEach { t ->
            runCatching { backend.unsubscribe(t) }.onSuccess { state.setTopic(t, false) }.onFailure { allOk = false }
        }
        // 리포트: 앞에서부터 차례로 보내고, 보낸 만큼(규칙에 안 맞아 버린 것 포함) 지운다
        val pending = queue.pending()
        var sent = 0
        for (r in pending) {
            val fields = CloudSyncPlan.toFirestore(r)
            if (fields != null && runCatching { backend.addReport(fields) }.isFailure) {
                allOk = false
                break
            }
            sent++
        }
        if (sent > 0) queue.drop(sent)
        return allOk
    }
}

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val trips: TripRepository,
    private val queue: QueuedFieldReporter,
    private val state: CloudState,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ok = CloudSyncRunner(FirebaseCloudBackend(), queue, state).run(settings.current().favorites, trips.current()?.country)
        return if (ok || runAttemptCount >= 5) Result.success() else Result.retry()
    }
}

object CloudSync {
    private const val NAME = "cloud-sync"

    /** 인터넷이 연결되면 한 번 동기화 (중복 요청은 하나로 합친다) */
    fun request(context: Context) {
        val work = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, work) }
    }
}
