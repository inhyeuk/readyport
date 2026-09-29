package com.readyport.video

import com.google.firebase.firestore.FirebaseFirestore
import com.readyport.doc.ocr.await
import com.readyport.pack.PackKeys
import com.readyport.pack.PackVerifier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** YouTube 여행 영상 하나. 값은 YouTube Data API가 준 그대로 (tools/videos/fetch_videos.py) */
@Serializable
data class Video(
    val id: String,
    val title: String,
    @SerialName("channel_id") val channelId: String = "",
    @SerialName("channel_title") val channelTitle: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("view_count") val viewCount: Long? = null,
    /** 채널이 구독자 수를 숨기면 null */
    @SerialName("subscriber_count") val subscriberCount: Long? = null,
    @SerialName("duration_s") val durationSeconds: Int = 0,
    val thumbnail: String,
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$id"
}

@Serializable
data class VideoList(
    @SerialName("schema_version") val schemaVersion: Int,
    val country: String,
    val query: String = "",
    @SerialName("generated_at") val generatedAt: String,
    val items: List<Video>,
)

/** 정렬 기준. 모두 YouTube가 준 값 그대로 비교한다(새 지표를 만들지 않는다) */
enum class VideoSort { Views, Recent, Subscribers }

fun List<Video>.sortedBy(sort: VideoSort): List<Video> = when (sort) {
    VideoSort.Views -> sortedByDescending { it.viewCount ?: -1 }
    VideoSort.Recent -> sortedByDescending { it.publishedAt }
    VideoSort.Subscribers -> sortedByDescending { it.subscriberCount ?: -1 }
}

/**
 * 서명 확인 → 해석 → 안전 검사. 하나라도 어긋나면 목록을 쓰지 않는다.
 * - YouTube 개발자 정책: 저장한 API 데이터는 30일 넘게 쓰지 않는다 → 30일 지난 목록은 버린다
 * - 영상 id·썸네일 주소 형식이 다르면 그 항목만 뺀다 (앱은 youtube.com 과 i.ytimg.com 만 연다)
 */
class VideoListParser(private val verifier: PackVerifier = PackVerifier(PackKeys.TRUSTED)) {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String, signature: String, country: String, now: Instant): VideoList? {
        val bytes = payload.encodeToByteArray()
        if (verifier.verify(bytes, signature.encodeToByteArray()) != PackVerifier.Result.Valid) return null
        val list = runCatching { json.decodeFromString(VideoList.serializer(), payload) }.getOrNull() ?: return null
        if (list.schemaVersion != 1 || list.country != country) return null
        val generated = runCatching { Instant.parse(list.generatedAt) }.getOrNull() ?: return null
        if (Duration.between(generated, now) > MAX_AGE || generated.isAfter(now.plus(Duration.ofDays(1)))) return null
        return list.copy(items = list.items.filter { VIDEO_ID.matches(it.id) && it.thumbnail.startsWith(THUMB_PREFIX) }.take(50))
    }

    companion object {
        val MAX_AGE: Duration = Duration.ofDays(30)
        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
        const val THUMB_PREFIX = "https://i.ytimg.com/"
    }
}

/** Firestore videos/{나라} (공개 읽기 전용). 인터넷이 없으면 기기에 남은 사본을 쓰되 30일 규칙은 같다 */
@Singleton
class VideoRepository @Inject constructor() {
    private val parser = VideoListParser()

    suspend fun list(country: String): VideoList? = runCatching {
        val doc = FirebaseFirestore.getInstance().collection("videos").document(country).get().await()
        val payload = doc.getString("payload") ?: return null
        val sig = doc.getString("sig") ?: return null
        parser.parse(payload, sig, country, Instant.now())
    }.getOrNull()
}
