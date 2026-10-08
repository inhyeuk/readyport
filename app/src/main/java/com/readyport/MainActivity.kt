package com.readyport

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.net.onlineFlow
import com.readyport.share.ShareInbox
import androidx.compose.runtime.remember
import com.readyport.ui.BoardHooks
import com.readyport.ui.NoticeHooks
import com.readyport.ui.ReadyPortRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// 생체 인증(BiometricPrompt)이 FragmentActivity를 요구한다
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var shareInbox: ShareInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) shareInbox.offer(intent)
        openPresent = intent?.getBooleanExtra(EXTRA_OPEN_PRESENT, false) == true
        openChecklist = intent?.getStringExtra(EXTRA_OPEN_CHECKLIST)
        openNotice = intent?.getStringExtra(EXTRA_OPEN_NOTICE)
        openBoard = intent?.getStringExtra(EXTRA_OPEN_BOARD)
        // 앱을 켤 때의 공지: 다시 만들어진 화면(회전 등)이나 위젯·알림·공유로 연 실행에서는 띄우지 않는다(할 일이 따로 있다)
        viewModel.startNotices(
            skip = savedInstanceState != null || openPresent || openChecklist != null || openNotice != null || openBoard != null ||
                intent?.action == Intent.ACTION_SEND,
        )
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val launchNotices by viewModel.launchNotices.collectAsStateWithLifecycle()
            val boardUnread by viewModel.boardUnread.collectAsStateWithLifecycle()
            val pendingShare by shareInbox.pending.collectAsStateWithLifecycle()
            val online by remember { applicationContext.onlineFlow() }.collectAsStateWithLifecycle(initialValue = true)
            ReadyPortRoot(
                settings = settings,
                onSetEasyMode = viewModel::setEasyMode,
                onSpeak = viewModel::speak,
                hasPendingShare = pendingShare != null,
                online = online,
                onSetChildMode = viewModel::setChildMode,
                onSetWifiOnly = viewModel::setWifiOnly,
                openPresent = openPresent,
                openChecklistTripId = openChecklist,
                onChecklistOpened = { openChecklist = null },
                onSetAlertsOn = viewModel::setAlertsOn,
                onSetAlertHour = viewModel::setAlertHour,
                notices = NoticeHooks(
                    launchNotice = launchNotices.firstOrNull(),
                    onLaunchNoticeDone = viewModel::onLaunchNoticeDone,
                    openNoticeId = openNotice,
                    onNoticeOpened = { openNotice = null },
                    onSetNoticePush = viewModel::setNoticePush,
                    onSetPromoPush = viewModel::setPromoPush,
                    onSetPromoNight = viewModel::setPromoNight,
                ),
                board = BoardHooks(
                    unread = boardUnread,
                    openPostId = openBoard,
                    onOpened = { openBoard = null },
                    onSetReplies = viewModel::setBoardReplies,
                ),
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shareInbox.offer(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PRESENT, false)) openPresent = true
        intent.getStringExtra(EXTRA_OPEN_CHECKLIST)?.let { openChecklist = it }
        intent.getStringExtra(EXTRA_OPEN_NOTICE)?.let { openNotice = it }
        intent.getStringExtra(EXTRA_OPEN_BOARD)?.let { openBoard = it }
    }

    private var openPresent by androidx.compose.runtime.mutableStateOf(false)

    /** 챙길 일 알림에서 열 때의 여행 id */
    private var openChecklist by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** 공지 알림에서 열 때의 공지 id (`""` = 공지사항 목록) */
    private var openNotice by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** 게시판 답글 알림에서 열 때의 글 id (`""` = 게시판) */
    private var openBoard by androidx.compose.runtime.mutableStateOf<String?>(null)

    companion object {
        /** 홈 화면 위젯에서 열 때 */
        const val EXTRA_OPEN_PRESENT = "readyport.open_present"

        /** 챙길 일 알림에서 열 때 — 그 여행 체크리스트로 (여행 id만, 개인정보 없음) */
        const val EXTRA_OPEN_CHECKLIST = "readyport.open_checklist"

        /** 공지 알림에서 열 때 — 그 공지(공지 id만, `""`면 공지사항 목록) */
        const val EXTRA_OPEN_NOTICE = "readyport.open_notice"

        /** 게시판 답글 알림에서 열 때 — 그 글(글 id만, `""`면 게시판) */
        const val EXTRA_OPEN_BOARD = "readyport.open_board"
    }
}
