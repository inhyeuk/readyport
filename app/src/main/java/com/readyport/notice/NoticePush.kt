package com.readyport.notice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.readyport.BuildConfig
import com.readyport.MainActivity
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.trip.TripNotifications
import com.readyport.trip.TripRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ======================= 공지 알림 (FCM 토픽, docs/NOTICES_PUSH.md) =======================
// 메시지에는 글이 없다: {type:"notice", id, v, cat}. 알림 제목·본문은 **서명된 공지**에서만 꺼낸다(메시지 글은 읽지 않는다).
// 서명본을 못 받거나 그 공지가 없으면 앱에 든 문구 `새 소식이 있어요`로 알리고, 누르면 공지사항 목록이 열린다.
// 토큰은 어디에도 보내거나 저장하지 않는다 — 토픽 구독(notice_all · notice_promo · country_{ISO2})만.

/** 이 휴대폰의 알림 설정 */
data class PushPrefs(
    val noticePush: Boolean = true,
    val promoPush: Boolean = false,
    val promoNight: Boolean = false,
    val childMode: Boolean = false,
)

/** 받은 공지 알림을 어떻게 할지 */
sealed interface PushOutcome {
    /** 알린다. [notice]가 null이면 앱에 든 문구(`새 소식이 있어요`) */
    data class Show(val notice: Notice?) : PushOutcome

    /** 광고성 공지가 밤(21시~8시)에 왔다 — [until]까지 미뤘다가 그때 다시 판단한다 */
    data class Hold(val until: Instant, val id: String) : PushOutcome

    data class Ignore(val reason: String) : PushOutcome
}

/** 알림에 쓸 글 (앱 문구 또는 서명된 공지) */
data class PushText(val title: String, val body: String, val noticeId: String?)

/**
 * 공지 알림 판단 (순수 함수 — 단위 테스트가 그대로 돌린다).
 * 정보통신망법 제50조: 광고성 정보는 ① 받기에 동의한 사람에게만 ② 제목 `(광고)`(서명본 검사) ③ 받지 않는 방법을 함께
 * ④ 21시~다음 날 8시에는 따로 동의한 사람에게만 — 그 밖의 사람에게는 **버리지 않고 아침 8시까지 미룬다**.
 * 밤은 한국 시각과 이 휴대폰 시각 **둘 다**로 본다(법의 기준 시각 + 해외에서 자는 시간).
 */
object NoticePushRules {
    val KST: ZoneId = ZoneId.of("Asia/Seoul")
    const val NIGHT_START = 21
    const val NIGHT_END = 8
    const val BODY_MAX = 160

    private val ID = Regex("^[a-z0-9][a-z0-9_-]{2,40}$")

    fun isNight(t: Instant, zone: ZoneId): Boolean {
        val h = t.atZone(zone).hour
        return h >= NIGHT_START || h < NIGHT_END
    }

    fun isNightAnywhere(t: Instant, local: ZoneId) = isNight(t, KST) || isNight(t, local)

    /**
     * 한국 시각과 이 휴대폰 시각이 모두 낮인 가장 이른 때. 두 낮(13시간씩)은 시차가 얼마든 1시간 이상 겹치고,
     * 겹침은 어느 한쪽의 아침 8시에 시작하므로 앞으로 사흘의 두 '아침 8시' 중에서 찾으면 된다.
     */
    fun releaseAt(now: Instant, local: ZoneId): Instant {
        val mornings = (0L..2L).flatMap { day ->
            listOf(KST, local).map { zone -> now.atZone(zone).toLocalDate().plusDays(day).atTime(NIGHT_END, 0).atZone(zone).toInstant() }
        }.filter { it.isAfter(now) }.sorted()
        return mornings.firstOrNull { !isNightAnywhere(it, local) } ?: mornings.first { !isNight(it, KST) }
    }

    fun decide(
        data: Map<String, String>,
        doc: NoticeDoc?,
        prefs: PushPrefs,
        ctx: NoticeContext,
        dismissed: Set<String>,
        local: ZoneId,
    ): PushOutcome {
        if (data["type"] != "notice") return PushOutcome.Ignore("not a notice")
        val id = data["id"]?.takeIf { ID.matches(it) } ?: return PushOutcome.Ignore("bad id")
        if (prefs.childMode) return PushOutcome.Ignore("child mode")
        val notice = doc?.notices?.firstOrNull { it.id == id }
        if (notice != null) {
            if (!NoticeSelector.eligible(notice, ctx)) return PushOutcome.Ignore("not live or not for this phone")
            if (notice.key in dismissed) return PushOutcome.Ignore("dismissed")
        }
        // 메시지의 cat 은 더 엄격하게만 쓴다(광고 표시가 있으면 광고 규칙). 보이는 글로는 쓰지 않는다
        val promo = notice?.promo == true || data["cat"] == "promo"
        if (promo) {
            if (!prefs.promoPush) return PushOutcome.Ignore("promo not agreed")
            if (!prefs.promoNight && isNightAnywhere(ctx.now, local)) return PushOutcome.Hold(releaseAt(ctx.now, local), id)
        } else if (!prefs.noticePush) {
            return PushOutcome.Ignore("notice push off")
        }
        return PushOutcome.Show(notice)
    }

    /** 알림 글: 서명된 공지의 제목·첫 쪽(광고면 받지 않는 방법을 덧붙인다), 공지가 없으면 앱 문구 */
    fun text(show: PushOutcome.Show, genericTitle: String, genericBody: String, promoOptOut: String): PushText {
        val n = show.notice ?: return PushText(genericTitle, genericBody, null)
        val body = n.bodyKo.let { if (it.length > BODY_MAX) it.take(BODY_MAX - 1).trimEnd() + "…" else it }
        return PushText(n.titleKo, if (n.promo) "$body\n$promoOptOut" else body, n.id)
    }
}

/** 공지 알림 통로 `공지·소식` — 챙길 일 알림(checklist)·여행 알림(trip)과 따로 켜고 끈다 */
object NoticeNotifications {
    const val CHANNEL = "notice"

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notice_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** 공지마다 알림 번호 하나 (다른 알림 1·2·4, 여행별 1000~900999와 겹치지 않게) */
    fun notificationId(noticeId: String?): Int = 2_000_000 + (noticeId ?: "").hashCode().mod(900_000)

    /** 알렸으면 true (권한이 없으면 아무것도 하지 않는다) */
    fun post(context: Context, text: PushText): Boolean {
        if (!TripNotifications.canNotify(context)) return false
        ensureChannel(context)
        // 누르면 그 공지(없으면 공지사항 목록). 공지 id만 넘긴다 — 개인정보 없음
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_NOTICE, text.noticeId.orEmpty())
        val id = notificationId(text.noticeId)
        val open = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, n)
        return true
    }
}

/**
 * 받은 공지 알림 처리 (FCM 서비스 → 여기). 서명본을 새로 받아(못 받으면 기기 안 사본) 판단하고 알리거나 미룬다.
 * 판단에 쓰는 값(찜·여행 나라·설정·다시 보지 않기)은 모두 기기 안 — 서버로 보내지 않는다.
 */
@Singleton
class NoticePushHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: NoticeRepository,
    private val settings: SettingsRepository,
    private val trips: TripRepository,
    private val store: NoticeStore,
) {
    suspend fun handle(data: Map<String, String>): PushOutcome {
        val s = settings.current()
        val prefs = PushPrefs(s.noticePush, s.promoPush, s.promoNight, s.childMode)
        // 받을 일이 없으면 인터넷도 쓰지 않는다
        if (data["type"] != "notice" || prefs.childMode || (!prefs.noticePush && !prefs.promoPush)) {
            return PushOutcome.Ignore("off")
        }
        val doc = repo.refresh(FETCH_TIMEOUT_MS)
        val ctx = NoticeContext(
            now = Instant.now(),
            today = LocalDate.now(),
            versionCode = BuildConfig.VERSION_CODE,
            countries = s.favorites + trips.all().map { it.country },
            childMode = s.childMode,
            promoOn = s.promoPush,
        )
        val outcome = NoticePushRules.decide(data, doc, prefs, ctx, store.marks().dismissed, ZoneId.systemDefault())
        when (outcome) {
            is PushOutcome.Show -> NoticeNotifications.post(
                context,
                NoticePushRules.text(
                    outcome,
                    genericTitle = context.getString(R.string.notice_push_generic_title),
                    genericBody = context.getString(R.string.notice_push_generic_body),
                    promoOptOut = context.getString(R.string.notice_push_promo_optout),
                ),
            )
            is PushOutcome.Hold -> NoticeHold.schedule(context, outcome, data["cat"])
            is PushOutcome.Ignore -> Unit
        }
        return outcome
    }

    companion object {
        /** FCM 처리 시간(약 20초) 안에 끝나게 */
        const val FETCH_TIMEOUT_MS = 10_000L
    }
}

/** 밤에 온 광고성 공지를 아침까지 미루기 (WorkManager — 재부팅을 스스로 넘긴다). 그때 동의·기간을 다시 본다 */
object NoticeHold {
    const val KEY_ID = "id"
    const val KEY_CAT = "cat"

    fun workName(id: String) = "notice-hold:$id"

    fun schedule(context: Context, hold: PushOutcome.Hold, cat: String?) {
        val delay = Duration.between(Instant.now(), hold.until).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<NoticeHoldWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_ID to hold.id, KEY_CAT to (cat ?: "promo")))
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(workName(hold.id), ExistingWorkPolicy.REPLACE, request) }
    }
}

@HiltWorker
class NoticeHoldWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val handler: NoticePushHandler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(NoticeHold.KEY_ID) ?: return Result.success()
        handler.handle(mapOf("type" to "notice", "id" to id, "cat" to (inputData.getString(NoticeHold.KEY_CAT) ?: "promo")))
        return Result.success()
    }
}
