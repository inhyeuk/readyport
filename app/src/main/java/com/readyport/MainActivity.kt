package com.readyport

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
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
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shareInbox.offer(intent)
    }
}
