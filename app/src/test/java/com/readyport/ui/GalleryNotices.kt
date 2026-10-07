package com.readyport.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.readyport.notice.Notice
import com.readyport.notice.NoticeCategory
import com.readyport.notice.NoticeContext
import com.readyport.notice.NoticeDoc
import com.readyport.notice.NoticeImage
import com.readyport.notice.NoticeLink
import com.readyport.notice.NoticeMode
import com.readyport.notice.NoticeSelector
import com.readyport.notice.NoticeType
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.notice.NoticeOnScrim
import com.readyport.ui.notice.NoticesContent
import com.readyport.ui.notice.NoticesUi
import com.readyport.ui.settings.NoticeSettings
import com.readyport.ui.settings.SettingsScreen
import com.readyport.ui.video.LocalThumbnailLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 갤러리·접근성 점검용 공지 화면 (docs/NOTICES_PUSH.md, DESIGN_SPEC 부록 K).
 * - 대화상자 카드는 실제 대화상자 창 없이 어두운 바탕 위에 그린다(NoticeOnScrim) — 점검이 버튼 크기·이름·줄바꿈을 본다.
 * - 첫 공지 `about-readyport`는 저장소의 notices/notices.json 원본 그대로, 그림은 커밋한 Hosting 배포본(hosting/public/notices/).
 * - 나머지 견본(긴급·일반·광고 이벤트)은 화면 모양 확인용이다 — 실제 공지가 아니다.
 */
object GalleryNotices {
    private val json = Json { ignoreUnknownKeys = true }

    /** 저장소의 공지 원본에서 하나 (active 같은 원본 전용 칸은 무시) */
    fun source(id: String): Notice {
        val root = Json.parseToJsonElement(File("../notices/notices.json").readText()) as JsonObject
        val item = (root["notices"] as JsonArray).first { ((it as JsonObject)["id"] as JsonPrimitive).content == id }
        return json.decodeFromJsonElement(Notice.serializer(), item)
    }

    val about: Notice by lazy { source("about-readyport") }

    /** 긴급 공지 견본: 글만 세 쪽 (마지막 쪽까지 봐야 닫힌다) */
    val urgent = Notice(
        id = "service-check", version = 1, type = NoticeType.Urgent,
        titleKo = "10월 12일 새벽에 잠깐 쉬어요",
        bodyKo = "10월 12일(월) 새벽 2시부터 4시까지 새 나라 안내를 받을 수 없어요. 이미 받아 둔 안내와 입국 QR은 그대로 열려요.",
        morePagesKo = listOf(
            "점검 중에도 여권 정보는 이 휴대폰 안에 그대로 있어요. 따로 할 일은 없어요.",
            "점검이 끝나면 앱을 한 번 다시 열어 주세요. 새 안내를 받아 와요.",
        ),
        start = "2026-10-08T09:00:00+09:00", end = "2026-10-12T06:00:00+09:00", priority = 100,
    )

    /** 일반 공지 견본: 글 한 쪽 */
    val normal = Notice(
        id = "holiday-airport", version = 1, type = NoticeType.Normal,
        titleKo = "연휴에는 공항에 일찍 가요",
        bodyKo = "연휴에는 출국장이 붐벼요. 비행기 떠나기 3시간 전에는 공항에 도착해 주세요.",
        start = "2026-09-20T09:00:00+09:00", end = "2026-10-12T23:59:00+09:00", priority = 10,
    )

    /** 광고성 이벤트 견본: 그림 + 링크 + 받지 않는 방법 */
    val promo = Notice(
        id = "autumn-event", version = 1, type = NoticeType.Event, category = NoticeCategory.Promo,
        titleKo = "(광고) 가을 여행 준비 이벤트",
        bodyKo = "가을에 태국으로 떠나는 분께 여행 준비 소식을 모아 드려요.",
        images = listOf(NoticeImage("https://readyport-app.web.app/notices/gallery-event.webp", "방콕 왓 아룬 사원 사진")),
        link = NoticeLink("이벤트 자세히 보기", "https://readyport-app.web.app/"),
        start = "2026-10-01T09:00:00+09:00", end = "2026-10-31T23:59:00+09:00", priority = 5,
    )

    private val ended = normal.copy(
        id = "chuseok-guide", titleKo = "추석 연휴 출국 안내를 마쳤어요",
        start = "2026-09-01T09:00:00+09:00", end = "2026-09-30T23:59:00+09:00",
    )

    /** 공지사항 목록 (오늘 10월 8일): 지금 공지 셋 + 지난 공지 하나 */
    val listed by lazy {
        val doc = NoticeDoc(Instant.parse("2026-10-08T00:00:00Z"), listOf(about, urgent, normal, promo, ended))
        NoticeSelector.listed(doc, NoticeContext(Instant.parse("2026-10-08T03:00:00Z"), LocalDate.of(2026, 10, 8), 8, emptySet()))
    }

    private val cache = HashMap<String, ImageBitmap?>()

    /** 공지 그림 대역: 소개 카드 두 장은 커밋한 PNG, 견본 이벤트는 [thumb](태국 사진) */
    fun loader(thumb: ImageBitmap?): suspend (String) -> ImageBitmap? = { url ->
        val name = url.substringAfterLast('/')
        if (name.startsWith("about-readyport")) {
            cache.getOrPut(name) {
                listOf(File("../hosting/public/notices/$name"), File("build/notice-cards/$name")).firstOrNull { it.isFile }
                    ?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
            }
        } else {
            thumb
        }
    }

    fun screens(thumb: ImageBitmap?): List<Pair<String, @Composable () -> Unit>> {
        val load = loader(thumb)
        val withImages: (@Composable () -> Unit) -> @Composable () -> Unit = { content ->
            { CompositionLocalProvider(LocalThumbnailLoader provides load) { content() } }
        }
        return listOf(
            // 첫 공지(운영자 요청): 이용 안내 — 카드뉴스 두 장 + 개인정보 처리방침 링크(마지막 쪽)
            "notice-about-1" to withImages { NoticeOnScrim(about, NoticeMode.Launch, 0) },
            "notice-about-2" to withImages { NoticeOnScrim(about, NoticeMode.Launch, 1) },
            // 긴급 공지: 첫 쪽(닫기 없음, `끝까지 읽으면 닫을 수 있어요`) / 마지막 쪽(닫기·확인·오늘 하루·다시 보지 않기)
            "notice-urgent-first" to { NoticeOnScrim(urgent, NoticeMode.Launch, 0) },
            "notice-urgent-last" to { NoticeOnScrim(urgent, NoticeMode.Launch, 2) },
            "notice-normal" to { NoticeOnScrim(normal, NoticeMode.Launch, 0) },
            "notice-event-promo" to withImages { NoticeOnScrim(promo, NoticeMode.Launch, 0) },
            "notices-list" to { NoticesContent(NoticesUi(false, listed), onOpen = {}, zone = ZoneId.of("Asia/Seoul")) },
            // 설정 › 공지·소식: 광고성 소식에 동의(날짜)·밤에도 받기(날짜)
            "settings-notices-promo" to {
                SettingsScreen(
                    easyMode = LocalDimens.current.easyMode, onEasyModeChange = {}, notifGranted = true,
                    notices = NoticeSettings(
                        noticePush = true, promoPush = true, promoDate = "2026-10-08", promoNight = true, promoNightDate = "2026-10-08",
                    ),
                )
            },
        )
    }
}
