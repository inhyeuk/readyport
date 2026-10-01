package com.readyport.ui.video

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.video.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 여행 영상 카드뉴스 개편 (DESIGN_SPEC 6-07·08): 카드 하나에 이름 하나, YouTube 표시·약관 설명문 유지, 오프라인 빈 상태 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class VideosDesignTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private val videos = listOf(
        Video(
            id = "AAAAAAAAAA1", title = "방콕 3박 4일 여행 브이로그 | 왓아룬 야경과 짜뚜짝 시장까지 하루에 다 보는 코스",
            channelTitle = "여행채널", publishedAt = "2026-09-01T00:00:00Z", viewCount = 1_234_567, subscriberCount = 89_000,
            durationSeconds = 754, thumbnail = "https://i.ytimg.com/vi/AAAAAAAAAA1/mqdefault.jpg",
        ),
    )

    @Test
    fun cardHasOneNameAndShowsWholeTitleAndYouTube() {
        val opened = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalThumbnailLoader provides { null }) {
                ReadyPortTheme { VideosContent("태국", VideosState.Ready(videos), onOpen = { opened += it }) }
            }
        }
        val label = s(R.string.videos_open, videos[0].title)
        val cards = rule.onAllNodesWithContentDescription(label)
        assertEquals("영상 카드 이름은 클릭 노드 하나에만", 1, cards.fetchSemanticsNodes().size)
        cards[0].performClick()
        assertEquals(videos[0].watchUrl, opened.single())
        // 제목은 줄 수 제한 없이 전부, YouTube 출처 표시
        assertTrue(rule.onAllNodesWithText(videos[0].title, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText(s(R.string.videos_source_youtube), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun offlineShowsEmptyStateAndKeepsTermsText() {
        val opened = mutableListOf<String>()
        rule.setContent { ReadyPortTheme { VideosContent("태국", VideosState.Unavailable, onOpen = { opened += it }) } }
        rule.onNodeWithText(s(R.string.videos_notice)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.videos_unavailable_title)).assertIsDisplayed()
        // YouTube API 약관: 설명문을 그대로 보이고 그 아래 공식 링크
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.videos_terms)))
        rule.onNodeWithText(s(R.string.videos_terms)).assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.videos_google_privacy)))
        rule.onNodeWithText(s(R.string.videos_google_privacy)).performClick()
        assertEquals(GOOGLE_PRIVACY, opened.single())
    }

    @Test
    fun loadingShowsProgressText() {
        rule.setContent { ReadyPortTheme { VideosContent("태국", VideosState.Loading, onOpen = {}) } }
        rule.onNodeWithText(s(R.string.videos_loading)).assertIsDisplayed()
    }
}
