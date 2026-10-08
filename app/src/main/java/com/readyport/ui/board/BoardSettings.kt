package com.readyport.ui.board

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.board.BoardRepository
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.ButtonPlacement
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.KoText
import com.readyport.ui.components.ListDivider
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.RowTrailing
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.KoreanBreak
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ======================= 설정 › 게시판 — DESIGN_SPEC 부록 L.10 =======================

/** 설정 › 게시판 줄들의 값 */
@Immutable
data class BoardSettingsUi(
    /** 익명 게시판 ID (아직 없으면 null — 처음 쓸 때 만들어진다) */
    val uid: String? = null,
    val nickname: String? = null,
    /** 운영자(config/admins)면 사진·동영상 스위치와 신고 관리가 보인다 */
    val admin: Boolean = false,
    val mediaEnabled: Boolean = false,
    val blockedCount: Int = 0,
    val deleting: Boolean = false,
)

data class BoardSettingsActions(
    val openRules: () -> Unit = {},
    val unblockAll: () -> Unit = {},
    val deleteAll: () -> Unit = {},
    val setMedia: (Boolean) -> Unit = {},
    val openAdmin: () -> Unit = {},
)

/** 설정 화면이 받는 게시판 묶음 (값 + 할 일) */
data class BoardSettingsBinding(val ui: BoardSettingsUi = BoardSettingsUi(), val actions: BoardSettingsActions = BoardSettingsActions())

@HiltViewModel
class BoardSettingsViewModel @Inject constructor(private val repo: BoardRepository) : ViewModel() {
    private val _ui = MutableStateFlow(BoardSettingsUi())
    val ui: StateFlow<BoardSettingsUi> = _ui.asStateFlow()
    private val _done = MutableStateFlow<UiText?>(null)

    /** 지운 결과 (토스트) */
    val done: StateFlow<UiText?> = _done.asStateFlow()

    init {
        viewModelScope.launch { repo.blocked.collect { b -> _ui.update { it.copy(blockedCount = b.size) } } }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val me = runCatching { repo.me() }.getOrNull()
            val media = runCatching { repo.refreshConfig().mediaEnabled }.getOrDefault(false)
            _ui.update { it.copy(uid = me?.uid, nickname = me?.nickname, admin = me?.admin == true, mediaEnabled = media) }
        }
    }

    fun unblockAll() = viewModelScope.launch { repo.unblockAll() }

    fun deleteAll() {
        _ui.update { it.copy(deleting = true) }
        viewModelScope.launch {
            val r = runCatching { repo.deleteEverything() }
            _ui.update { it.copy(deleting = false) }
            _done.value = r.fold({ UiText(R.string.settings_board_delete_done) }, { it.toUiText() })
            refresh()
        }
    }

    fun setMedia(on: Boolean) {
        _ui.update { it.copy(mediaEnabled = on) }
        viewModelScope.launch { runCatching { repo.setMediaEnabled(on) }.onFailure { _ui.update { s -> s.copy(mediaEnabled = !on) } } }
    }

    fun consumeDone() {
        _done.value = null
    }
}

/** 운영 앱의 설정 › 게시판 연결 (Hilt). 테스트·갤러리는 값을 바로 넣는다 */
@Composable
fun rememberBoardSettings(onOpenRules: () -> Unit, onOpenAdmin: () -> Unit, viewModel: BoardSettingsViewModel = hiltViewModel()): BoardSettingsBinding {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(done) {
        done?.let { t ->
            Toast.makeText(context, if (t.arg == null) resources.getString(t.id) else resources.getString(t.id, t.arg), Toast.LENGTH_SHORT).show()
            viewModel.consumeDone()
        }
    }
    return BoardSettingsBinding(
        ui,
        BoardSettingsActions(
            openRules = onOpenRules,
            unblockAll = viewModel::unblockAll,
            deleteAll = viewModel::deleteAll,
            setMedia = viewModel::setMedia,
            openAdmin = onOpenAdmin,
        ),
    )
}

/** 게시판 ID를 복사한다 (비밀값이 아니다 — 운영자가 config/admins 에 넣을 때 쓴다) */
private fun copyBoardId(context: Context, uid: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.settings_board_id), uid))
    Toast.makeText(context, context.getString(R.string.settings_board_id_copied), Toast.LENGTH_SHORT).show()
}

/**
 * 설정 › 게시판 묶음: 답글 알림(스위치) · 내 게시판 ID(복사) · 커뮤니티 규칙 · 차단한 사람(모두 풀기) ·
 * (운영자) 사진·동영상 올리기 스위치 · 신고·가림 관리 · 내 게시판 기록 모두 지우기(빨강, 확인).
 */
@Composable
fun BoardSettingsGroup(
    binding: BoardSettingsBinding,
    replies: Boolean,
    onRepliesChange: (Boolean) -> Unit,
    onCopyId: ((String) -> Unit)? = null,
) {
    val ui = binding.ui
    val actions = binding.actions
    val context = LocalContext.current
    val copy = onCopyId ?: { id -> copyBoardId(context, id) }
    val rowPadding = LocalDimens.current.listRowPadding
    var confirm by rememberSaveable { mutableStateOf(false) }
    if (confirm) {
        DestructiveConfirm(
            stringResource(R.string.settings_board_delete_confirm_title),
            stringResource(R.string.settings_board_delete_confirm_body),
            stringResource(R.string.board_delete),
            onConfirm = { confirm = false; actions.deleteAll() },
            onDismiss = { confirm = false },
        )
    }
    ListGroup(stringResource(R.string.settings_group_board)) {
        ListRow(
            stringResource(R.string.settings_board_replies),
            icon = Icons.Outlined.MarkChatUnread,
            body = stringResource(R.string.settings_board_replies_desc),
            trailing = RowTrailing.Switch(replies, onRepliesChange),
        )
        ListDivider()
        val uid = ui.uid
        ListRow(
            stringResource(R.string.settings_board_id),
            icon = Icons.Outlined.Badge,
            body = if (uid == null) stringResource(R.string.settings_board_id_none) else null,
            trailing = RowTrailing.None,
            extra = if (uid != null) {
                {
                    KoText(uid, MaterialTheme.typography.bodyMedium, color = Tokens.Ink, display = uid.chunked(4).joinToString(KoreanBreak.ZERO_WIDTH_SPACE.toString()))
                    ui.nickname?.let { KoText(stringResource(R.string.settings_board_nick, it), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary) }
                }
            } else {
                null
            },
        )
        if (uid != null) {
            Box(Modifier.padding(horizontal = rowPadding).padding(bottom = 16.dp)) {
                SecondaryButton(stringResource(R.string.settings_board_id_copy), onClick = { copy(uid) }, icon = Icons.Outlined.ContentCopy)
            }
        }
        ListDivider()
        ListRow(
            stringResource(R.string.board_rules_title),
            icon = Icons.Outlined.Gavel,
            body = stringResource(R.string.settings_board_rules_body),
            onClick = actions.openRules,
        )
        if (ui.blockedCount > 0) {
            ListDivider()
            ListRow(
                stringResource(R.string.settings_board_blocked, ui.blockedCount),
                icon = Icons.Outlined.Block,
                body = stringResource(R.string.settings_board_blocked_desc),
                trailing = RowTrailing.None,
            )
            Box(Modifier.padding(horizontal = rowPadding - 8.dp).padding(bottom = 8.dp)) {
                QuietButton(stringResource(R.string.settings_board_unblock), onClick = actions.unblockAll)
            }
        }
        if (ui.admin) {
            ListDivider()
            ListRow(
                stringResource(R.string.settings_board_media),
                icon = Icons.Outlined.PermMedia,
                tone = BadgeTone.Help,
                body = stringResource(R.string.settings_board_media_desc),
                trailing = RowTrailing.Switch(ui.mediaEnabled, actions.setMedia),
            )
            ListDivider()
            ListRow(
                stringResource(R.string.settings_board_admin),
                icon = Icons.Outlined.AdminPanelSettings,
                tone = BadgeTone.Help,
                body = stringResource(R.string.settings_board_admin_desc),
                onClick = actions.openAdmin,
            )
        }
        ListDivider()
        ListRow(
            stringResource(R.string.settings_board_delete),
            icon = Icons.Outlined.DeleteSweep,
            tone = BadgeTone.Danger,
            body = stringResource(R.string.settings_board_delete_desc),
            trailing = RowTrailing.None,
        )
        Box(Modifier.padding(horizontal = rowPadding).padding(bottom = 16.dp)) {
            DangerButton(
                stringResource(R.string.board_delete),
                onClick = { if (!ui.deleting) confirm = true },
                placement = ButtonPlacement.CardAction,
                icon = Icons.Outlined.DeleteSweep,
                contentDescription = stringResource(R.string.settings_board_delete),
            )
        }
    }
}
