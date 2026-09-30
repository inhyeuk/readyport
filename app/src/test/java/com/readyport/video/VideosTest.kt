package com.readyport.video

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.crypto.tink.subtle.Ed25519Sign
import com.readyport.R
import com.readyport.pack.PackVerifier
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.video.LocalThumbnailLoader
import com.readyport.ui.video.VideosContent
import com.readyport.ui.video.VideosState
import com.readyport.ui.video.matching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Base64

/** YouTube 여행 영상: 서명·30일 규칙·안전 검사·정렬·썸네일 버튼 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class VideosTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keys = Ed25519Sign.KeyPair.newKeyPair()
    private val parser = VideoListParser(PackVerifier(mapOf("test-1" to keys.publicKey)))
    private val now = Instant.parse("2026-10-01T00:00:00Z")

    private fun sign(data: String): String {
        val sig = Base64.getEncoder().encodeToString(Ed25519Sign(keys.privateKey).sign(data.encodeToByteArray()))
        return """{"kid":"test-1","alg":"Ed25519","sig":"$sig"}"""
    }

    private fun item(id: String, views: Long?, subs: Long?, date: String, thumb: String = "https://i.ytimg.com/vi/$id/mqdefault.jpg") =
        """{"id":"$id","title":"태국 여행 $id","channel_id":"UC","channel_title":"채널$id","published_at":"$date",""" +
            """"view_count":${views ?: "null"},"subscriber_count":${subs ?: "null"},"duration_s":600,"thumbnail":"$thumb"}"""

    private fun payload(generated: String = "2026-09-30T18:30:00Z", items: List<String> = listOf(
        item("AAAAAAAAAA1", 100, 50, "2026-01-01T00:00:00Z"),
        item("AAAAAAAAAA2", 300, null, "2025-05-01T00:00:00Z"),
        item("AAAAAAAAAA3", 200, 900, "2026-09-01T00:00:00Z"),
    )) = """{"schema_version":1,"country":"TH","query":"태국 여행","generated_at":"$generated","items":[${items.joinToString(",")}]}"""

    @Test
    fun validListParses() {
        val p = payload()
        val list = parser.parse(p, sign(p), "TH", now)!!
        assertEquals(3, list.items.size)
    }

    @Test
    fun tamperedOrWrongCountryIsRejected() {
        val p = payload()
        assertNull(parser.parse(p.replace("채널AAAAAAAAAA1", "가짜"), sign(p), "TH", now))
        assertNull(parser.parse(p, sign(p), "JP", now))
    }

    @Test
    fun olderThan30DaysIsNotShown() {
        val p = payload(generated = "2026-08-31T00:00:00Z")
        assertNull(parser.parse(p, sign(p), "TH", now))
        val ok = payload(generated = "2026-09-02T00:00:00Z")
        assertEquals(3, parser.parse(ok, sign(ok), "TH", now)!!.items.size)
    }

    @Test
    fun badIdsAndThumbnailHostsAreDropped() {
        val p = payload(items = listOf(
            item("AAAAAAAAAA1", 1, 1, "2026-01-01T00:00:00Z"),
            item("short", 1, 1, "2026-01-01T00:00:00Z"),
            item("AAAAAAAAAA3", 1, 1, "2026-01-01T00:00:00Z", thumb = "https://evil.example/x.jpg"),
        ))
        assertEquals(listOf("AAAAAAAAAA1"), parser.parse(p, sign(p), "TH", now)!!.items.map { it.id })
    }

    @Test
    fun sortsUseYouTubeValuesAsIs() {
        val p = payload()
        val items = parser.parse(p, sign(p), "TH", now)!!.items
        assertEquals(listOf("AAAAAAAAAA2", "AAAAAAAAAA3", "AAAAAAAAAA1"), items.sortedBy(VideoSort.Views).map { it.id })
        assertEquals(listOf("AAAAAAAAAA3", "AAAAAAAAAA1", "AAAAAAAAAA2"), items.sortedBy(VideoSort.Recent).map { it.id })
        // 구독자 수를 숨긴 채널은 맨 뒤
        assertEquals(listOf("AAAAAAAAAA3", "AAAAAAAAAA1", "AAAAAAAAAA2"), items.sortedBy(VideoSort.Subscribers).map { it.id })
    }

    @Test
    fun thumbnailOpensYouTubeAndTermsAreLinked() {
        val p = payload()
        val items = parser.parse(p, sign(p), "TH", now)!!.items
        val opened = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalThumbnailLoader provides { null }) {
                ReadyPortTheme { VideosContent("태국", VideosState.Ready(items), onOpen = { opened += it }) }
            }
        }
        // 기본 정렬은 조회수순 → 첫 영상은 조회수 300
        val label = context.getString(R.string.videos_open, "태국 여행 AAAAAAAAAA2")
        rule.onNodeWithContentDescription(label).assertIsDisplayed().performClick()
        assertEquals("https://www.youtube.com/watch?v=AAAAAAAAAA2", opened.last())
        rule.onNodeWithText(context.getString(R.string.videos_sort_recent)).performClick()
        rule.onNode(hasContentDescription(context.getString(R.string.videos_open, "태국 여행 AAAAAAAAAA3"))).assertIsDisplayed()
        val terms = context.getString(R.string.videos_youtube_terms)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(terms))
        rule.onNodeWithText(terms).performClick()
        assertEquals("https://www.youtube.com/t/terms", opened.last())
    }

    @Test
    fun searchMatchesTitleOrChannelIgnoringSpaces() {
        val p = payload()
        val items = parser.parse(p, sign(p), "TH", now)!!.items
        assertEquals(listOf("AAAAAAAAAA3"), items.matching("aaaaaaaaaa3").map { it.id })
        assertEquals(3, items.matching("태국여행").size)          // 띄어쓰기 없이 써도
        assertEquals(listOf("AAAAAAAAAA1"), items.matching("채널AAAAAAAAAA1").map { it.id })
        assertEquals(3, items.matching("  ").size)
        assertEquals(0, items.matching("일본").size)
    }

    @Test
    fun searchFieldFiltersTheList() {
        val p = payload()
        val items = parser.parse(p, sign(p), "TH", now)!!.items
        rule.setContent {
            CompositionLocalProvider(LocalThumbnailLoader provides { null }) {
                ReadyPortTheme { VideosContent("태국", VideosState.Ready(items), onOpen = {}) }
            }
        }
        rule.onNodeWithText(context.getString(R.string.videos_count, 3)).assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("AAAAAAAAAA1")
        rule.onNodeWithText(context.getString(R.string.videos_count, 1)).assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextReplacement("없는말")
        rule.onNodeWithText(context.getString(R.string.videos_search_empty, "없는말")).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.videos_search_clear)).performClick()
        rule.onNodeWithText(context.getString(R.string.videos_count, 3)).assertIsDisplayed()
    }
}
