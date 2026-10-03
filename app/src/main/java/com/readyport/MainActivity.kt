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
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
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
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shareInbox.offer(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PRESENT, false)) openPresent = true
        intent.getStringExtra(EXTRA_OPEN_CHECKLIST)?.let { openChecklist = it }
    }

    private var openPresent by androidx.compose.runtime.mutableStateOf(false)

    /** 챙길 일 알림에서 열 때의 여행 id */
    private var openChecklist by androidx.compose.runtime.mutableStateOf<String?>(null)

    companion object {
        /** 홈 화면 위젯에서 열 때 */
        const val EXTRA_OPEN_PRESENT = "readyport.open_present"

        /** 챙길 일 알림에서 열 때 — 그 여행 체크리스트로 (여행 id만, 개인정보 없음) */
        const val EXTRA_OPEN_CHECKLIST = "readyport.open_checklist"
    }
}
