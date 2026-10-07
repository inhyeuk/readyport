package com.readyport.notice

import com.readyport.pack.PackKeys
import com.readyport.pack.PackVerifier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

// ======================= 공지사항 (docs/NOTICES_PUSH.md, ARCHITECTURE '공지·알림') =======================
// 공지 글·그림 주소는 **서명된 데이터에서만** 온다(국가 팩·영상 목록과 같은 Ed25519 키). Firestore 콘솔에서 고친 글은 서명이 맞지 않아 보이지 않는다.
// 무엇을 띄울지는 모두 이 휴대폰 안에서 정한다(찜·여행 나라·버전·'다시 보지 않기') — 서버로 보내는 것은 없다.

/** 공지 종류 — 색과 버튼이 이것으로 정해진다(D.well 공지와 같은 뜻) */
@Serializable
enum class NoticeType {
    /** 긴급 공지: 빨강, 마지막 쪽까지 봐야 닫힌다, 다시 보지 않기·오늘 하루 보지 않기 있음 */
    @SerialName("urgent") Urgent,

    /** 일반 공지: 파랑, 언제든 닫힘, 확인 + 다시 보지 않기·오늘 하루 보지 않기 */
    @SerialName("normal") Normal,

    /** 이벤트: 주황, 일반 공지와 같은 버튼 */
    @SerialName("event") Event,

    /** 이용 안내: 초록, 언제든 닫힘, 다시 보지 않기 없음 — 한 번 보면 앱을 켤 때 다시 뜨지 않는다(목록에서 다시 본다) */
    @SerialName("guide") Guide,
}

/** 정보통신망법 제50조: 광고성 정보(promo)는 받기에 동의한 사람에게만, 제목은 `(광고)`로 시작 */
@Serializable
enum class NoticeCategory {
    @SerialName("service") Service,
    @SerialName("promo") Promo,
}

/** 공지 그림 — 우리 Hosting 주소만. [altKo]는 TalkBack이 읽고, 그림이 안 열리면 그 자리에 보인다 */
@Serializable
data class NoticeImage(val url: String, @SerialName("alt_ko") val altKo: String)

/** 공지 아래 링크 버튼 (우리 Hosting · Play 스토어 · 이 앱의 GitHub 저장소만) */
@Serializable
data class NoticeLink(@SerialName("label_ko") val labelKo: String, val url: String)

/** 쪽 하나 = 그림(있으면) + 글(있으면) */
data class NoticePage(val text: String?, val image: NoticeImage?)

@Serializable
data class Notice(
    val id: String,
    val version: Int,
    val type: NoticeType,
    val category: NoticeCategory = NoticeCategory.Service,
    @SerialName("title_ko") val titleKo: String,
    @SerialName("body_ko") val bodyKo: String,
    @SerialName("more_pages_ko") val morePagesKo: List<String> = emptyList(),
    val images: List<NoticeImage> = emptyList(),
    val link: NoticeLink? = null,
    val start: String,
    val end: String? = null,
    val priority: Int = 0,
    @SerialName("min_version_code") val minVersionCode: Int? = null,
    @SerialName("max_version_code") val maxVersionCode: Int? = null,
    val audience: List<String> = listOf(NoticeRules.AUDIENCE_ALL),
) {
    /** '다시 보지 않기'를 기억하는 이름 — 운영자가 version을 올리면 다시 보인다 */
    val key: String get() = "$id@$version"

    val promo: Boolean get() = category == NoticeCategory.Promo

    fun startAt(): Instant? = NoticeRules.parseTime(start)

    fun endAt(): Instant? = end?.let(NoticeRules::parseTime)

    /** 쪽 수 = max(본문 + 더 있는 쪽, 그림 수). 그림 k는 k번째 쪽 맨 위 */
    fun pages(): List<NoticePage> {
        val n = maxOf(1 + morePagesKo.size, images.size)
        return List(n) { k ->
            NoticePage(text = if (k == 0) bodyKo else morePagesKo.getOrNull(k - 1), image = images.getOrNull(k))
        }
    }
}

/** 서명이 맞은 공지 묶음. [generatedAt]이 더 새 것만 받는다(예전 서명본을 다시 보내도 되돌아가지 않게) */
data class NoticeDoc(val generatedAt: Instant, val notices: List<Notice>)

/** 기기 안 안전 검사 (서명 다음 두 번째 그물 — tools/notices/build_notices.py 와 같은 규칙) */
object NoticeRules {
    const val AUDIENCE_ALL = "all"
    const val IMAGE_PREFIX = "https://readyport-app.web.app/notices/"
    const val PROMO_PREFIX = "(광고)"
    const val MAX_TITLE = 40
    const val MAX_BODY = 500
    const val MAX_PAGES = 6
    const val MAX_NOTICES = 30

    private val ID = Regex("^[a-z0-9][a-z0-9_-]{2,40}$")
    private val IMAGE_EXT = listOf(".png", ".webp", ".jpg")

    /** 링크로 열 수 있는 곳: 호스트 → 경로 앞머리 */
    private val LINK_HOSTS = mapOf(
        "readyport-app.web.app" to "/",
        "play.google.com" to "/",
        "github.com" to "/inhyeuk/readyport",
    )

    fun parseTime(value: String): Instant? = runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()

    fun imageOk(url: String): Boolean {
        if (!url.startsWith(IMAGE_PREFIX) || IMAGE_EXT.none { url.lowercase().endsWith(it) }) return false
        val rest = url.removePrefix(IMAGE_PREFIX)
        return rest.isNotEmpty() && ".." !in rest && '?' !in rest && '#' !in rest && "//" !in rest
    }

    fun linkOk(url: String): Boolean {
        val u = runCatching { URI(url) }.getOrNull() ?: return false
        if (u.scheme != "https" || u.rawUserInfo != null || u.port != -1) return false
        val prefix = LINK_HOSTS[u.host?.lowercase()] ?: return false
        val path = u.path.orEmpty().ifEmpty { "/" }
        return prefix == "/" || path == prefix || path.startsWith("$prefix/")
    }

    /** 규칙에 어긋난 공지는 버리고, 어긋난 그림·링크만 뺀다. 글 길이·광고 표시가 틀리면 공지 전체를 버린다 */
    fun sanitize(n: Notice): Notice? {
        if (!ID.matches(n.id) || n.version < 1) return null
        if (n.titleKo.isBlank() || n.titleKo.length > MAX_TITLE) return null
        if (n.bodyKo.isBlank() || n.bodyKo.length > MAX_BODY || n.morePagesKo.any { it.isBlank() || it.length > MAX_BODY }) return null
        if (n.startAt() == null || (n.end != null && n.endAt() == null)) return null
        if (n.promo && (!n.titleKo.startsWith(PROMO_PREFIX) || n.type == NoticeType.Urgent || n.type == NoticeType.Guide)) return null
        if (!n.promo && PROMO_PREFIX in n.titleKo) return null
        val clean = n.copy(
            images = n.images.filter { imageOk(it.url) && it.altKo.isNotBlank() },
            link = n.link?.takeIf { linkOk(it.url) && it.labelKo.isNotBlank() },
            audience = n.audience.filter { it == AUDIENCE_ALL || Regex("^[A-Z]{2}$").matches(it) }.ifEmpty { return null },
        )
        return clean.takeIf { it.pages().size <= MAX_PAGES }
    }
}

/**
 * 서명 확인 → 해석 → 안전 검사 (영상 목록 [com.readyport.video.VideoListParser]와 같은 틀).
 * 서명이 틀리면 묶음 전체를 버린다. 앱이 모르는 종류(나중에 생길 새 type)는 그 공지만 건너뛴다.
 */
class NoticeParser(private val verifier: PackVerifier = PackVerifier(PackKeys.TRUSTED)) {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String, signature: String): NoticeDoc? {
        if (verifier.verify(payload.encodeToByteArray(), signature.encodeToByteArray()) != PackVerifier.Result.Valid) return null
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        if (root["schema_version"]?.jsonPrimitive?.intOrNull != 1) return null
        val generated = root["generated_at"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val items = root["notices"] as? JsonArray ?: return null
        val notices = items.mapNotNull { el ->
            runCatching { json.decodeFromJsonElement(Notice.serializer(), el) }.getOrNull()?.let(NoticeRules::sanitize)
        }.distinctBy { it.id }.take(NoticeRules.MAX_NOTICES)
        return NoticeDoc(generated, notices)
    }
}

/** 띄울지 정하는 데 쓰는 이 휴대폰의 값 (모두 기기 안 — 서버로 보내지 않는다) */
data class NoticeContext(
    val now: Instant,
    val today: LocalDate,
    val versionCode: Int,
    /** 찜한 나라 + 여행 나라 (audience 판단) */
    val countries: Set<String>,
    val childMode: Boolean = false,
    /** 광고성 소식 받기에 동의했는지 — 앱을 켤 때 광고 공지를 띄우는 것도 동의한 사람에게만 */
    val promoOn: Boolean = false,
)

/** '다시 보지 않기'(id@version) · '오늘 하루 보지 않기'(그날만) · 돌아가며 보이기(이번 차례에 이미 보인 것) */
data class NoticeMarks(
    val dismissed: Set<String> = emptySet(),
    val snoozed: Map<String, LocalDate> = emptyMap(),
    val round: Set<String> = emptySet(),
) {
    fun snoozedOn(key: String, today: LocalDate) = snoozed[key] == today
}

/** 대화상자를 닫은 방법 */
enum class NoticeChoice {
    /** 확인·닫기·뒤로 */
    Close,

    /** 오늘 하루 보지 않기 */
    Today,

    /** 다시 보지 않기 */
    Never,
}

/** 공지사항 목록의 한 줄 */
data class ListedNotice(val notice: Notice, val current: Boolean)

/**
 * 무엇을 언제 띄울지 (순수 함수 — 단위 테스트가 그대로 돌린다).
 * - 앱을 켤 때 **하나만**. 여럿이면 우선순위 차례로 다음에 켤 때 하나씩(돌아가며).
 * - 긴급 공지만 예외로 **이어서** 모두 띄운다(그때는 다른 공지는 띄우지 않는다).
 * - 자녀 폰 모드에서는 띄우지 않는다. 광고성 공지는 받기에 동의한 사람에게만.
 */
object NoticeSelector {
    private val order = compareByDescending<Notice> { it.priority }
        .thenByDescending { it.startAt() ?: Instant.EPOCH }
        .thenBy { it.id }

    fun started(n: Notice, now: Instant) = n.startAt()?.let { !it.isAfter(now) } == true

    fun ended(n: Notice, now: Instant) = n.endAt()?.let { !it.isAfter(now) } == true

    fun forThisApp(n: Notice, versionCode: Int) =
        (n.minVersionCode == null || versionCode >= n.minVersionCode) && (n.maxVersionCode == null || versionCode <= n.maxVersionCode)

    fun forThisUser(n: Notice, countries: Set<String>) = NoticeRules.AUDIENCE_ALL in n.audience || n.audience.any { it in countries }

    fun eligible(n: Notice, ctx: NoticeContext) =
        started(n, ctx.now) && !ended(n, ctx.now) && forThisApp(n, ctx.versionCode) && forThisUser(n, ctx.countries)

    /** 앱을 켤 때 띄울 후보 (긴급 포함, 차례 무시) */
    fun candidates(doc: NoticeDoc?, ctx: NoticeContext, marks: NoticeMarks): List<Notice> {
        if (doc == null || ctx.childMode) return emptyList()
        return doc.notices.filter {
            eligible(it, ctx) && (!it.promo || ctx.promoOn) && it.key !in marks.dismissed && !marks.snoozedOn(it.key, ctx.today)
        }.sortedWith(order)
    }

    /** 이번에 띄울 공지들: 긴급이 있으면 긴급 모두(이어서), 없으면 이번 차례 하나 */
    fun launchQueue(doc: NoticeDoc?, ctx: NoticeContext, marks: NoticeMarks): List<Notice> {
        val all = candidates(doc, ctx, marks)
        val urgent = all.filter { it.type == NoticeType.Urgent }
        if (urgent.isNotEmpty()) return urgent
        return listOfNotNull(all.firstOrNull { it.key !in marks.round } ?: all.firstOrNull())
    }

    /**
     * 대화상자를 닫은 뒤의 기록. [others] = 그때의 긴급 아닌 후보(차례 계산용).
     * 이용 안내는 한 번 보면 끝(다시 보지 않기 버튼이 없어서 — 목록에서 다시 본다).
     */
    fun after(marks: NoticeMarks, shown: Notice, choice: NoticeChoice, today: LocalDate, others: List<Notice>): NoticeMarks {
        val dismissed = when {
            choice == NoticeChoice.Never -> marks.dismissed + shown.key
            shown.type == NoticeType.Guide -> marks.dismissed + shown.key
            else -> marks.dismissed
        }
        // 오늘 하루: 지난 날 기록은 지운다(끝없이 쌓이지 않게)
        val snoozed = marks.snoozed.filterValues { !it.isBefore(today) }
            .let { if (choice == NoticeChoice.Today) it + (shown.key to today) else it }
        val round = if (shown.type == NoticeType.Urgent) {
            marks.round
        } else {
            val live = others.map { it.key }.toSet() + shown.key
            if (shown.key in marks.round) setOf(shown.key) else (marks.round intersect live) + shown.key
        }
        return NoticeMarks(dismissed, snoozed, round)
    }

    /** 공지사항 목록: 이 앱·이 사람에게 해당하고 이미 시작한 것. 지금 공지(우선순위 순) → 지난 공지(최근 끝난 순) */
    fun listed(doc: NoticeDoc?, ctx: NoticeContext): List<ListedNotice> {
        if (doc == null) return emptyList()
        val shown = doc.notices.filter { started(it, ctx.now) && forThisApp(it, ctx.versionCode) && forThisUser(it, ctx.countries) }
        val (past, current) = shown.partition { ended(it, ctx.now) }
        return current.sortedWith(order).map { ListedNotice(it, true) } +
            past.sortedByDescending { it.endAt() }.map { ListedNotice(it, false) }
    }
}

/** 대화상자를 어디서 열었는지 — 앱을 켤 때(종류별 버튼 규칙) / 목록·알림에서 다시 보기(언제든 닫힘, 보지 않기 버튼 없음) */
enum class NoticeMode { Launch, Reader }

/** 맨 아래 주 버튼 */
enum class NoticePrimary { Next, Confirm }

/** 대화상자 버튼 규칙 (순수 함수 — D.well 공지와 같은 뜻) */
data class NoticeButtons(
    /** 오른쪽 위 닫기(X)와 뒤로 가기로 닫히는지 */
    val closable: Boolean,
    val primary: NoticePrimary,
    /** `이전` 버튼 */
    val previous: Boolean,
    /** `오늘 하루 보지 않기` · `다시 보지 않기` */
    val dismissRow: Boolean,
) {
    companion object {
        fun of(type: NoticeType, mode: NoticeMode, page: Int, pageCount: Int): NoticeButtons {
            val last = page >= pageCount - 1
            val reader = mode == NoticeMode.Reader
            return NoticeButtons(
                // 긴급 공지는 마지막 쪽까지 봐야 닫힌다(앱을 켤 때만)
                closable = reader || type != NoticeType.Urgent || last,
                primary = if (last) NoticePrimary.Confirm else NoticePrimary.Next,
                previous = page > 0,
                dismissRow = !reader && when (type) {
                    NoticeType.Urgent -> last
                    NoticeType.Normal, NoticeType.Event -> true
                    NoticeType.Guide -> false
                },
            )
        }
    }
}
