package com.readyport.ui.board

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.board.BoardComment
import com.readyport.board.BoardKind
import com.readyport.board.BoardMe
import com.readyport.board.BoardPost
import com.readyport.board.DraftCheck
import com.readyport.board.PiiGuard
import com.readyport.board.ReportReason
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** 게시판 화면(상태 없는 Content): 사진 꺼짐이면 붙이기 버튼 없음 · 개인정보 경고 · 채택 버튼은 질문한 사람만 · 신고 고르기 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h3000dp")
class BoardUiTest {
    @get:Rule
    val rule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    private val now = Instant.parse("2026-10-08T03:00:00Z")

    @Test
    fun mediaOffShowsNoAttachButtons() {
        rule.setContent { ReadyPortTheme(easyMode = false) { BoardWriteContent(BoardWriteUi(kind = BoardKind.Talk, mediaEnabled = false)) } }
        rule.onAllNodesWithText(s(R.string.board_attach_photo, 0, 4)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.board_attach_video)).assertCountEquals(0)
    }

    @Test
    fun mediaOnShowsAttachButtonsAndLocationNote() {
        rule.setContent { ReadyPortTheme(easyMode = false) { BoardWriteContent(BoardWriteUi(kind = BoardKind.Talk, mediaEnabled = true)) } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.board_attach_note)))
        rule.onNodeWithText(s(R.string.board_attach_photo, 0, 4)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.board_attach_note)).assertIsDisplayed()
    }

    @Test
    fun passportNumberShowsWarningAndBlocksSubmit() {
        val title = "여권 칸 질문"
        val body = "제 여권이 M12345678 인데 이렇게 쓰나요?"
        val ui = BoardWriteUi(kind = BoardKind.Qna, title = title, body = body, check = DraftCheck(pii = PiiGuard.scan("$title\n$body")))
        rule.setContent { ReadyPortTheme(easyMode = false) { BoardWriteContent(ui) } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.board_pii_title)))
        rule.onNodeWithText(s(R.string.board_pii_title)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.board_pii_found, s(R.string.board_pii_passport))).assertIsDisplayed()
        // 막히는 경우에는 `이대로 올리기`가 없다
        rule.onAllNodesWithText(s(R.string.board_pii_post_anyway)).assertCountEquals(0)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.board_submit)))
        rule.onNodeWithText(s(R.string.board_submit)).assertIsNotEnabled()
    }

    private val post = BoardPost("p1", BoardKind.Qna, "태국 입국 카드 질문", "도착 며칠 전부터 낼 수 있나요?", "TH", "asker", "여행자 4821", now.minusSeconds(3600))
    private val answer = BoardComment("c1", "p1", "asker", "helper", "바다 고양이", "3일 전부터예요.", now.minusSeconds(600))

    @Test
    fun onlyTheAskerSeesAcceptButton() {
        var accepted: BoardComment? = null
        val asker = BoardPostUi(loading = false, post = post, comments = listOf(answer), me = BoardMe("asker", "여행자 4821", false), now = now)
        rule.setContent { ReadyPortTheme(easyMode = false) { BoardPostContent(asker, BoardPostActions(accept = { accepted = it })) } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.board_accept)))
        rule.onNodeWithText(s(R.string.board_accept)).performClick()
        assertEquals("c1", accepted?.id)
    }

    @Test
    fun othersDoNotSeeAcceptButton() {
        val other = BoardPostUi(loading = false, post = post, comments = listOf(answer), me = BoardMe("bob", "밥 여행", false), now = now)
        rule.setContent { ReadyPortTheme(easyMode = false) { BoardPostContent(other) } }
        rule.onAllNodesWithText(s(R.string.board_accept)).assertCountEquals(0)
    }

    @Test
    fun notJoinedSeesJoinInsteadOfComposer() {
        var joined = false
        val guest = BoardPostUi(loading = false, post = post, comments = emptyList(), now = now)
        rule.setContent { ReadyPortTheme(easyMode = false) { BoardPostContent(guest, BoardPostActions(join = { joined = true })) } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.board_join_start)))
        rule.onNodeWithText(s(R.string.board_join_start)).performClick()
        assertEquals(true, joined)
    }

    @Test
    fun reportCardSendsTheChosenReasonOnlyAfterPicking() {
        var sent: ReportReason? = null
        rule.setContent { ReadyPortTheme(easyMode = false) { ReportCard(onSend = { sent = it }, onDismiss = {}) } }
        rule.onNodeWithText(s(R.string.board_report_send)).assertIsNotEnabled()
        rule.onNodeWithText(s(R.string.board_reason_personal)).performClick()
        rule.onNodeWithText(s(R.string.board_report_send)).performClick()
        assertEquals(ReportReason.Personal, sent)
    }
}
