package com.readyport.ui.notice

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.notice.Notice
import com.readyport.notice.NoticeChoice
import com.readyport.notice.NoticeMode
import com.readyport.notice.NoticeSelector
import com.readyport.notice.NoticeContext
import com.readyport.notice.NoticeTestData.doc
import com.readyport.notice.NoticeTestData.notice
import com.readyport.ui.settings.NoticeSettings
import com.readyport.ui.settings.SettingsScreen
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.video.LocalThumbnailLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 공지 대화상자 버튼(종류별)·쪽 넘기기·대체 글·광고 끄는 방법, 공지사항 목록, 설정 › 공지·소식 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class NoticeUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int) = context.getString(id)

    private val img = { n: Int -> """{"url":"https://readyport-app.web.app/notices/$n.webp","alt_ko":"그림 $n 설명"}""" }
    private val urgent = doc(notice("urgent", type = "urgent", extra = """"images":[${img(1)},${img(2)},${img(3)}]""")).notices.single()

    private fun show(n: Notice, mode: NoticeMode = NoticeMode.Launch, start: Int = 0, image: ImageBitmap? = null): MutableList<NoticeChoice> {
        val choices = mutableListOf<NoticeChoice>()
        rule.setContent {
            var page by androidx.compose.runtime.remember { mutableIntStateOf(start) }
            ReadyPortTheme {
                CompositionLocalProvider(LocalThumbnailLoader provides { image }) {
                    NoticeCard(n, mode, page, onPage = { page = it }, onChoice = { choices += it }, onOpenLink = {})
                }
            }
        }
        return choices
    }

    @Test fun urgentCannotCloseUntilTheLastPage() {
        val choices = show(urgent)
        rule.onAllNodesWithContentDescription(s(R.string.notice_close_cd)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.notice_never)).assertCountEquals(0)
        rule.onNodeWithText(s(R.string.notice_urgent_hint)).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.notice_page_cd, 1, 3)).assertExists()
        rule.onNodeWithText(s(R.string.notice_next)).performClick()
        rule.onNodeWithText(s(R.string.notice_next)).performClick()
        // 마지막 쪽: 닫기·확인·오늘 하루·다시 보지 않기
        rule.onNodeWithContentDescription(context.getString(R.string.notice_page_cd, 3, 3)).assertExists()
        rule.onNodeWithContentDescription(s(R.string.notice_close_cd)).assertExists()
        rule.onNodeWithText(s(R.string.notice_never)).performClick()
        assertEquals(listOf(NoticeChoice.Never), choices)
    }

    @Test fun normalNoticeButtons() {
        val choices = show(doc(notice("svc")).notices.single())
        rule.onNodeWithContentDescription(s(R.string.notice_close_cd)).assertExists()
        rule.onNodeWithText(s(R.string.notice_today)).performClick()
        rule.onNodeWithText(s(R.string.notice_confirm)).performClick()
        assertEquals(listOf(NoticeChoice.Today, NoticeChoice.Close), choices)
    }

    @Test fun guideHasNoDismissButtons() {
        show(doc(notice("guide", type = "guide", extra = """"more_pages_ko":["둘째 쪽 이에요"]""")).notices.single())
        rule.onNodeWithContentDescription(s(R.string.notice_close_cd)).assertExists()
        rule.onAllNodesWithText(s(R.string.notice_never)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.notice_today)).assertCountEquals(0)
        rule.onNodeWithText(s(R.string.notice_next)).performClick()
        rule.onNodeWithText("둘째 쪽 이에요").assertIsDisplayed()
        rule.onNodeWithText(s(R.string.notice_prev)).assertExists()
    }

    @Test fun readerModeIsAlwaysClosable() {
        show(urgent, mode = NoticeMode.Reader)
        rule.onNodeWithContentDescription(s(R.string.notice_close_cd)).assertExists()
        rule.onAllNodesWithText(s(R.string.notice_never)).assertCountEquals(0)
    }

    @Test fun imageAltTextIsReadOrShown() {
        // 그림을 못 불러오면 대체 글이 그 자리에 보인다
        show(urgent, mode = NoticeMode.Reader)
        rule.onNodeWithText("그림 1 설명").assertIsDisplayed()
    }

    @Test fun loadedImageCarriesAltTextForTalkBack() {
        show(urgent, mode = NoticeMode.Reader, image = ImageBitmap(40, 50))
        rule.waitForIdle()
        rule.onNodeWithContentDescription("그림 1 설명").assertExists()
    }

    @Test fun promoShowsHowToOptOut() {
        show(doc(notice("promo", type = "event", category = "promo")).notices.single())
        rule.onNodeWithText(s(R.string.notice_promo_optout)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.notice_promo_tag)).assertIsDisplayed()
    }

    @Test fun dialogAnnouncesItsTitle() {
        show(doc(notice("svc", title = "서비스 점검 안내")).notices.single())
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, context.getString(R.string.notice_pane, "서비스 점검 안내")))
            .assertExists()
    }

    @Test fun listGroupsCurrentAndPast() {
        val d = doc(
            notice("now", title = "지금 공지 하나"),
            notice("past", title = "지난 공지 하나", start = "2026-09-01T09:00:00+09:00", end = "2026-10-01T09:00:00+09:00"),
        )
        val items = NoticeSelector.listed(d, NoticeContext(Instant.parse("2026-10-08T03:00:00Z"), LocalDate.of(2026, 10, 8), 8, emptySet()))
        val opened = mutableListOf<String>()
        rule.setContent { ReadyPortTheme { NoticesContent(NoticesUi(false, items), onOpen = { opened += it.id }, zone = ZoneId.of("Asia/Seoul")) } }
        rule.onNodeWithText(s(R.string.notices_current)).assertExists()
        rule.onNodeWithText("지금 공지 하나").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("지난 공지 하나"))
        rule.onNodeWithText("공지 · 2026. 10. 1. 끝남").assertExists()
        assertEquals(listOf("now"), opened)
    }

    @Test fun emptyListShowsEmptyState() {
        rule.setContent { ReadyPortTheme { NoticesContent(NoticesUi(false, emptyList()), onOpen = {}) } }
        rule.onNodeWithText(s(R.string.notices_empty_title)).assertIsDisplayed()
    }

    @Test fun settingsShowNoticeRowsAndConsentDate() {
        val promoChanges = mutableListOf<Boolean>()
        rule.setContent {
            ReadyPortTheme {
                SettingsScreen(
                    easyMode = false, onEasyModeChange = {}, notifGranted = true,
                    notices = NoticeSettings(noticePush = true, promoPush = true, promoDate = "2026-10-08"),
                    onPromoPushChange = { promoChanges += it },
                )
            }
        }
        val list = rule.onNode(hasScrollAction())
        list.performScrollToNode(hasText(s(R.string.settings_promo_night)))
        rule.onNodeWithText(context.getString(R.string.settings_promo_agreed, "2026년 10월 8일")).assertExists()
        rule.onNodeWithText(s(R.string.settings_notice_push)).assertExists()
        rule.onNodeWithText(s(R.string.settings_promo_push)).performClick()
        assertEquals(listOf(false), promoChanges)
        // 결과 알림(보내는 곳·처리한 날)
        rule.onNodeWithText(s(R.string.promo_result_off_title)).assertExists()
    }

    @Test fun nightRowOnlyWhenPromoIsOn() {
        rule.setContent {
            ReadyPortTheme { SettingsScreen(easyMode = false, onEasyModeChange = {}, notifGranted = true, notices = NoticeSettings()) }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.settings_promo_push)))
        rule.onAllNodesWithText(s(R.string.settings_promo_night)).assertCountEquals(0)
    }
}
