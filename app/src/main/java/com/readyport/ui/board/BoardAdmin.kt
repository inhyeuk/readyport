package com.readyport.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.board.BoardRepository
import com.readyport.board.ReportedItem
import com.readyport.board.boardPreview
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.ButtonPlacement
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ======================= 운영자: 신고·가림 관리 — DESIGN_SPEC 부록 L.9 =======================

@Immutable
data class BoardAdminUi(val loading: Boolean = true, val items: List<ReportedItem> = emptyList(), val error: UiText? = null)

data class BoardAdminActions(
    val open: (postId: String) -> Unit = {},
    val setHidden: (ReportedItem, Boolean) -> Unit = { _, _ -> },
    val setPinned: (ReportedItem, Boolean) -> Unit = { _, _ -> },
    val clear: (ReportedItem) -> Unit = {},
    val delete: (ReportedItem) -> Unit = {},
)

@HiltViewModel
class BoardAdminViewModel @Inject constructor(private val repo: BoardRepository) : ViewModel() {
    private val _ui = MutableStateFlow(BoardAdminUi())
    val ui: StateFlow<BoardAdminUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                _ui.update { it.copy(loading = false, items = repo.moderationQueue(), error = null) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.toUiText()) }
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                load()
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.toUiText()) }
            }
        }
    }

    fun setHidden(i: ReportedItem, hidden: Boolean) = act { repo.setHidden(i.post.id, i.comment?.id, hidden) }
    fun setPinned(i: ReportedItem, pinned: Boolean) = act { repo.setPinned(i.post.id, pinned) }
    fun clear(i: ReportedItem) = act { repo.clearReports(i.post.id, i.comment?.id) }
    fun delete(i: ReportedItem) = act { repo.hardDelete(i.post.id, i.comment?.id) }
}

@Composable
fun BoardAdminScreen(onOpen: (String) -> Unit, viewModel: BoardAdminViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    BoardAdminContent(
        ui,
        BoardAdminActions(onOpen, viewModel::setHidden, viewModel::setPinned, viewModel::clear, viewModel::delete),
    )
}

/**
 * 운영자 화면: 임시조치 안내 → 신고·가림 목록(신고 수 많은 차례). 줄마다 가리기/다시 보이기 · 고정 · 신고 지우기 · 문서째 지우기(확인).
 */
@Composable
fun BoardAdminContent(ui: BoardAdminUi, actions: BoardAdminActions = BoardAdminActions()) {
    var confirm by remember { mutableStateOf<ReportedItem?>(null) }
    confirm?.let { item ->
        DestructiveConfirm(
            stringResource(if (item.comment == null) R.string.board_delete_post_title else R.string.board_delete_comment_title),
            stringResource(R.string.board_admin_note),
            stringResource(R.string.board_admin_delete),
            onConfirm = { actions.delete(item); confirm = null },
            onDismiss = { confirm = null },
        )
    }
    AppScreen(title = stringResource(R.string.board_admin_title), speech = stringResource(R.string.board_admin_speech), icon = Icons.Outlined.AdminPanelSettings) {
        item(key = "note") { IconBullet(stringResource(R.string.board_admin_note), Icons.Outlined.Info, tone = BadgeTone.Neutral) }
        ui.error?.let { e -> item(key = "error") { ErrorLine(e.text()) } }
        if (!ui.loading && ui.items.isEmpty()) {
            item(key = "empty") { BoardEmpty(stringResource(R.string.board_admin_empty), null) }
        }
        ui.items.forEach { i ->
            item(key = "q-${i.post.id}-${i.comment?.id.orEmpty()}") { QueueCard(i, actions, onDelete = { confirm = i }) }
        }
    }
}

@Composable
private fun QueueCard(i: ReportedItem, actions: BoardAdminActions, onDelete: () -> Unit) {
    val dimens = LocalDimens.current
    BoardCard(onClick = { actions.open(i.post.id) }, onClickLabel = stringResource(R.string.board_open_post_cd, i.post.title)) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (i.reportCount > 0) StatusTag(stringResource(R.string.board_admin_reports, i.reportCount), StatusKind.Caution, icon = Icons.Outlined.Flag)
                if (i.hidden) HiddenTag()
                if (i.comment == null && i.post.pinned) PinnedTag()
            }
            val c = i.comment
            if (c != null) {
                KoText(stringResource(R.string.board_admin_comment_of, i.post.title), MaterialTheme.typography.labelMedium, color = Tokens.InkSecondary)
                KoText(boardPreview(c.body), MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
                AuthorLine(c.authorUid, c.nickname, c.createdAt.toString().take(10))
            } else {
                KoText(i.post.title, MaterialTheme.typography.titleMedium, color = Tokens.Ink)
                KoText(boardPreview(i.post.body), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                AuthorLine(i.post.authorUid, i.post.nickname, i.post.createdAt.toString().take(10))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    stringResource(if (i.hidden) R.string.board_admin_unhide else R.string.board_admin_hide),
                    onClick = { actions.setHidden(i, !i.hidden) },
                    icon = if (i.hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    fillWidth = false,
                )
                if (c == null) {
                    SecondaryButton(
                        stringResource(if (i.post.pinned) R.string.board_admin_unpin else R.string.board_admin_pin),
                        onClick = { actions.setPinned(i, !i.post.pinned) },
                        icon = Icons.Outlined.PushPin,
                        fillWidth = false,
                    )
                }
                if (i.reportCount > 0) {
                    SecondaryButton(stringResource(R.string.board_admin_clear), onClick = { actions.clear(i) }, icon = Icons.Outlined.CleaningServices, fillWidth = false, tone = BadgeTone.Neutral)
                }
            }
            DangerButton(stringResource(R.string.board_admin_delete), onClick = onDelete, placement = ButtonPlacement.ItemAction, icon = Icons.Outlined.DeleteForever)
        }
    }
}
