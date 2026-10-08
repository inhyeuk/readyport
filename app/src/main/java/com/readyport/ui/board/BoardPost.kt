package com.readyport.ui.board

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.R
import com.readyport.board.BoardComment
import com.readyport.board.BoardError
import com.readyport.board.BoardKind
import com.readyport.board.BoardLimits
import com.readyport.board.BoardMe
import com.readyport.board.BoardMediaItem
import com.readyport.board.BoardPost
import com.readyport.board.BoardRepository
import com.readyport.board.BoardShow
import com.readyport.board.BoardThreads
import com.readyport.board.CommentThread
import com.readyport.board.DraftCheck
import com.readyport.board.ReportReason
import com.readyport.board.Shown
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.koDisplay
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.components.startBar
import com.readyport.ui.components.textIconSize
import com.readyport.ui.nav.BoardPostRoute
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.video.LocalThumbnailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

// ======================= 글 화면 — DESIGN_SPEC 부록 L.5 =======================

@Immutable
data class BoardPostUi(
    val loading: Boolean = true,
    val gone: Boolean = false,
    val offline: Boolean = false,
    val post: BoardPost? = null,
    val comments: List<BoardComment> = emptyList(),
    val liked: Boolean = false,
    val likedComments: Set<String> = emptySet(),
    val reported: Boolean = false,
    val me: BoardMe = BoardMe(null, null, false),
    val admins: Set<String> = emptySet(),
    val blocked: Set<String> = emptySet(),
    val revealed: Set<String> = emptySet(),
    val composer: String = "",
    val replyTo: BoardComment? = null,
    val editing: BoardComment? = null,
    val check: DraftCheck = DraftCheck(),
    val allowWarnings: Boolean = false,
    val sending: Boolean = false,
    val error: UiText? = null,
    val now: Instant = Instant.now(),
) {
    val threads: List<CommentThread> get() = BoardThreads.build(comments, post?.acceptedId)
    val isAuthor: Boolean get() = post != null && me.uid == post.authorUid
}

data class BoardPostActions(
    val like: () -> Unit = {},
    val share: () -> Unit = {},
    val report: (comment: BoardComment?, ReportReason) -> Unit = { _, _ -> },
    val block: (uid: String) -> Unit = {},
    val edit: () -> Unit = {},
    val delete: () -> Unit = {},
    val accept: (BoardComment?) -> Unit = {},
    val reply: (BoardComment) -> Unit = {},
    val editComment: (BoardComment) -> Unit = {},
    val cancelCompose: () -> Unit = {},
    val composerChange: (String) -> Unit = {},
    val allowWarnings: () -> Unit = {},
    val send: () -> Unit = {},
    val likeComment: (BoardComment) -> Unit = {},
    val deleteComment: (BoardComment) -> Unit = {},
    val reveal: (String) -> Unit = {},
    val join: () -> Unit = {},
    val retry: () -> Unit = {},
    val openMedia: (BoardMediaItem) -> Unit = {},
    // 운영자
    val setHidden: (BoardComment?, Boolean) -> Unit = { _, _ -> },
    val setPinned: (Boolean) -> Unit = {},
    val hardDelete: (BoardComment?) -> Unit = {},
)

@HiltViewModel
class BoardPostViewModel @Inject constructor(handle: SavedStateHandle, private val repo: BoardRepository) : ViewModel() {
    private val postId = handle.toRoute<BoardPostRoute>().postId
    private val _ui = MutableStateFlow(BoardPostUi())
    val ui: StateFlow<BoardPostUi> = _ui.asStateFlow()
    private val _events = Channel<UiText>(Channel.BUFFERED)

    /** 잠깐 보일 결과 문구(토스트) */
    val events: Flow<UiText> = _events.receiveAsFlow()

    /** 글이 없어졌을 때(지움) 뒤로 */
    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    init {
        viewModelScope.launch { repo.blocked.collect { b -> _ui.update { it.copy(blocked = b) } } }
        // 내가 쓰거나 지우면(댓글·채택·게시판 이름 정하기·운영자 가림) 다시 불러온다
        viewModelScope.launch { repo.revision.drop(1).collect { load() } }
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val me = repo.me()
                val admins = repo.adminSet()
                val d = repo.detail(postId, includeHidden = me.admin)
                if (d == null) {
                    _ui.update { it.copy(loading = false, gone = true) }
                } else {
                    _ui.update {
                        it.copy(
                            loading = false, offline = false, post = d.post, comments = d.comments, liked = d.liked,
                            likedComments = d.likedComments, reported = d.reported, me = me, admins = admins, now = Instant.now(),
                        )
                    }
                }
            } catch (e: BoardError) {
                _ui.update { it.copy(loading = false, offline = true) }
            }
        }
    }

    private fun act(comment: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _events.send(e.toUiText(comment))
            }
        }
    }

    fun like() {
        val post = _ui.value.post ?: return
        val on = !_ui.value.liked
        _ui.update { it.copy(liked = on, post = post.copy(likeCount = post.likeCount + if (on) 1 else -1)) }
        act {
            try {
                repo.setLike(post, on)
            } catch (e: Exception) {
                _ui.update { it.copy(liked = !on, post = post) }
                throw e
            }
        }
    }

    fun likeComment(c: BoardComment) {
        val on = c.id !in _ui.value.likedComments
        _ui.update { s ->
            s.copy(
                likedComments = if (on) s.likedComments + c.id else s.likedComments - c.id,
                comments = s.comments.map { if (it.id == c.id) it.copy(likeCount = it.likeCount + if (on) 1 else -1) else it },
            )
        }
        act {
            try {
                repo.setCommentLike(c, on)
            } catch (e: Exception) {
                load()
                throw e
            }
        }
    }

    fun report(c: BoardComment?, reason: ReportReason) = act {
        val post = _ui.value.post ?: return@act
        repo.report(post.id, c?.id, reason)
        if (c == null) _ui.update { it.copy(reported = true) }
        _events.send(UiText(R.string.board_done_reported))
    }

    fun block(uid: String) = act {
        repo.block(uid)
        _events.send(UiText(R.string.board_done_blocked))
    }

    fun delete() = act {
        val post = _ui.value.post ?: return@act
        repo.delete(post)
        _events.send(UiText(R.string.board_done_deleted))
        _closed.value = true
    }

    fun accept(c: BoardComment?) = act {
        val post = _ui.value.post ?: return@act
        val target = if (c != null && post.acceptedId == c.id) null else c
        repo.accept(post, target)
        _ui.update { it.copy(post = post.copy(solved = target != null, acceptedId = target?.id)) }
    }

    fun reply(c: BoardComment) = _ui.update { it.copy(replyTo = c, editing = null, error = null) }

    fun editComment(c: BoardComment) =
        _ui.update { it.copy(editing = c, replyTo = null, composer = c.body, check = repo.checkComment(c.body), error = null) }

    fun cancelCompose() = _ui.update { it.copy(replyTo = null, editing = null, composer = if (it.editing != null) "" else it.composer, error = null) }

    fun composerChange(text: String) = _ui.update { it.copy(composer = text, check = repo.checkComment(text), allowWarnings = false, error = null) }

    fun allowWarnings() = _ui.update { it.copy(allowWarnings = true) }

    fun send() {
        val s = _ui.value
        val post = s.post ?: return
        if (s.sending || s.composer.isBlank()) return
        _ui.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            try {
                val editing = s.editing
                if (editing != null) repo.editComment(editing, s.composer) else repo.comment(post, s.composer, s.replyTo, s.allowWarnings)
                _ui.update { it.copy(sending = false, composer = "", replyTo = null, editing = null, check = DraftCheck(), allowWarnings = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(sending = false, error = e.toUiText(comment = true)) }
            }
        }
    }

    fun deleteComment(c: BoardComment) = act { repo.deleteComment(c) }

    fun reveal(id: String) = _ui.update { it.copy(revealed = it.revealed + id) }

    fun setHidden(c: BoardComment?, hidden: Boolean) = act { repo.setHidden(postId, c?.id, hidden) }

    fun setPinned(pinned: Boolean) = act { repo.setPinned(postId, pinned) }

    fun hardDelete(c: BoardComment?) = act {
        if (c == null) _closed.value = true
        repo.hardDelete(postId, c?.id)
    }
}

/** 글 화면 (Hilt). [onEdit]: 글 고치기, [onJoin]: 게시판 이름 정하기 */
@Composable
fun BoardPostScreen(onEdit: (BoardPost) -> Unit, onJoin: () -> Unit, onClosed: () -> Unit, viewModel: BoardPostViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(closed) { if (closed) onClosed() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { t ->
            val text = if (t.arg == null) resources.getString(t.id) else resources.getString(t.id, t.arg)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    BoardPostContent(
        ui,
        BoardPostActions(
            like = viewModel::like,
            share = { ui.post?.let { sharePost(context, it) } },
            report = viewModel::report,
            block = viewModel::block,
            edit = { ui.post?.let(onEdit) },
            delete = viewModel::delete,
            accept = viewModel::accept,
            reply = viewModel::reply,
            editComment = viewModel::editComment,
            cancelCompose = viewModel::cancelCompose,
            composerChange = viewModel::composerChange,
            allowWarnings = viewModel::allowWarnings,
            send = viewModel::send,
            likeComment = viewModel::likeComment,
            deleteComment = viewModel::deleteComment,
            reveal = viewModel::reveal,
            join = onJoin,
            retry = viewModel::load,
            openMedia = { m ->
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(m.url.toUri(), if (m.video) "video/mp4" else "image/jpeg")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
            setHidden = viewModel::setHidden,
            setPinned = viewModel::setPinned,
            hardDelete = viewModel::hardDelete,
        ),
    )
}

/** 공유: 휴대폰 공유 창에 제목 + 본문 앞부분 + `레디포트 게시판에서` (링크 없이 글만) */
fun sharePost(context: android.content.Context, post: BoardPost) {
    val text = buildString {
        append(post.title)
        append("\n\n")
        append(if (post.body.length > SHARE_BODY_MAX) post.body.take(SHARE_BODY_MAX).trimEnd() + "…" else post.body)
        append("\n\n— ")
        append(context.getString(R.string.board_share_footer))
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.board_share_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private const val SHARE_BODY_MAX = 300

/** 대화상자 대상 */
private sealed interface PostDialog {
    data class Report(val comment: BoardComment?) : PostDialog
    data class Block(val uid: String) : PostDialog
    data object DeletePost : PostDialog
    data class DeleteComment(val comment: BoardComment) : PostDialog
}

/**
 * 글 화면 (상태 없는 Content): 글 카드(상태·나라 → 제목 → 글쓴이 → 본문 → 사진 → 추천·공유·더 보기) →
 * (Q&A) 공식 안내 아님 한 줄 → 답변·댓글 묶음(채택한 답 맨 위) → 댓글 쓰기(화면의 주 버튼 하나).
 */
@Composable
fun BoardPostContent(ui: BoardPostUi, actions: BoardPostActions = BoardPostActions()) {
    val post = ui.post
    var dialog by remember { mutableStateOf<PostDialog?>(null) }
    when (val d = dialog) {
        is PostDialog.Report -> ReportDialog(
            onSend = { reason -> actions.report(d.comment, reason); dialog = null },
            onDismiss = { dialog = null },
        )
        is PostDialog.Block -> DestructiveConfirm(
            stringResource(R.string.board_block_confirm_title), stringResource(R.string.board_block_confirm_body),
            stringResource(R.string.board_block_confirm), onConfirm = { actions.block(d.uid); dialog = null }, onDismiss = { dialog = null },
        )
        PostDialog.DeletePost -> DestructiveConfirm(
            stringResource(R.string.board_delete_post_title), stringResource(R.string.board_delete_post_body),
            stringResource(R.string.board_delete), onConfirm = { actions.delete(); dialog = null }, onDismiss = { dialog = null },
        )
        is PostDialog.DeleteComment -> DestructiveConfirm(
            stringResource(R.string.board_delete_comment_title), stringResource(R.string.board_delete_comment_body),
            stringResource(R.string.board_delete), onConfirm = { actions.deleteComment(d.comment); dialog = null }, onDismiss = { dialog = null },
        )
        null -> Unit
    }
    val kind = post?.kind ?: BoardKind.Qna
    AppScreen(
        title = stringResource(kind.label()),
        speech = stringResource(R.string.board_post_speech),
        icon = kind.icon(),
    ) {
        when {
            ui.loading -> item(key = "loading") { BoardEmpty(stringResource(R.string.board_loading), null) }
            ui.offline -> item(key = "offline") {
                BoardEmpty(stringResource(R.string.board_offline_title), stringResource(R.string.board_offline_body), icon = Icons.Outlined.GppMaybe) {
                    SecondaryButton(stringResource(R.string.board_retry), onClick = actions.retry, fillWidth = false)
                }
            }
            ui.gone || post == null -> item(key = "gone") { BoardEmpty(stringResource(R.string.board_post_gone), null) }
            else -> {
                item(key = "post") {
                    PostBody(ui, post, actions, onMenu = { dialog = it })
                }
                if (post.kind == BoardKind.Qna) {
                    item(key = "not-official") {
                        IconBullet(stringResource(R.string.board_qna_not_official), Icons.Outlined.Policy, tone = BadgeTone.Neutral)
                    }
                }
                item(key = "comments-head") { CommentsHead(ui, post) }
                ui.threads.forEach { t ->
                    item(key = "thread-${t.root.id}") {
                        ThreadCard(ui, post, t, actions, onMenu = { dialog = it })
                    }
                }
                item(key = "composer") { Composer(ui, post, actions) }
            }
        }
    }
}

@Composable
private fun PostBody(ui: BoardPostUi, post: BoardPost, actions: BoardPostActions, onMenu: (PostDialog) -> Unit) {
    val dimens = LocalDimens.current
    val shown = BoardShow.post(post, ui.blocked, ui.revealed)
    BoardCard {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.inner + 4.dp)) {
            if (shown == Shown.Reported) {
                PlaceholderLine(stringResource(R.string.board_reported_post), icon = Icons.Outlined.GppMaybe)
                QuietButton(stringResource(R.string.board_reveal), onClick = { actions.reveal(post.id) })
                return@Column
            }
            if (shown == Shown.Hidden) PlaceholderLine(stringResource(R.string.board_hidden_post), icon = Icons.Outlined.VisibilityOff)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (post.pinned) PinnedTag()
                if (post.authorUid in ui.admins) OperatorTag()
                if (post.kind == BoardKind.Qna && !post.deleted && !post.pinned) QnaStatusTag(post.solved)
                post.country?.let { CountryTag(it) }
            }
            if (post.deleted) {
                PlaceholderLine(stringResource(R.string.board_deleted_post), icon = Icons.Outlined.DeleteOutline)
            } else {
                KoText(post.title, MaterialTheme.typography.titleLarge, color = Tokens.Ink, heading = true, glueShort = true)
                AuthorLine(post.authorUid, post.nickname, relativeTime(ui.now, post.createdAt), edited = post.edited)
                SelectionContainer { KoText(post.body, MaterialTheme.typography.bodyLarge, color = Tokens.Ink) }
                if (post.media.isNotEmpty()) MediaGrid(post.media, actions.openMedia)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (!post.deleted) {
                    LikeToggle(ui.liked, post.likeCount, enabled = !ui.isAuthor, onToggle = actions.like)
                    SecondaryButton(stringResource(R.string.board_share), onClick = actions.share, icon = Icons.Outlined.Share, fillWidth = false)
                }
                PostMenu(ui, post, actions, onMenu)
            }
        }
    }
}

/** 추천 켬·끔: 켜면 AccentSoft(선택됨) + 채운 엄지, 끄면 흰 바탕 + 테두리. 자기 글은 누를 수 없다(숫자만) */
@Composable
private fun LikeToggle(liked: Boolean, count: Int, enabled: Boolean, onToggle: () -> Unit) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.medium
    val style = MaterialTheme.typography.labelLarge
    val state = stringResource(R.string.board_like_on_cd)
    Row(
        Modifier
            .clip(shape)
            .background(if (liked) Tokens.AccentSoft else Tokens.Surface, shape)
            .border(if (liked) 1.5.dp else 1.dp, if (liked) Tokens.Accent else Tokens.LineStrong, shape)
            .toggleable(value = liked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .then(if (liked) Modifier.semantics { stateDescription = state } else Modifier)
            .minTouch()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (liked) Icons.Outlined.ThumbUp else Icons.Outlined.ThumbUpOffAlt,
            contentDescription = null,
            tint = if (liked) Tokens.Accent else Tokens.InkSecondary,
            modifier = Modifier.size(textIconSize(dimens.icon, style)),
        )
        KoText(stringResource(R.string.board_like_count, count), style, color = if (liked) Tokens.AccentDeep else Tokens.Ink)
    }
}

/** 더 보기(⋯) 메뉴: 남의 글 = 신고·차단, 내 글 = 고치기·지우기, 운영자 = 고정·가림·문서째 지우기 */
@Composable
private fun PostMenu(ui: BoardPostUi, post: BoardPost, actions: BoardPostActions, onMenu: (PostDialog) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.minTouchSize()) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.board_more_cd), tint = Tokens.InkSecondary, modifier = Modifier.size(textIconSize(LocalDimens.current.icon, MaterialTheme.typography.labelLarge)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Tokens.Surface) {
            if (ui.isAuthor && !post.deleted) {
                MenuRow(stringResource(R.string.board_edit), Icons.Outlined.Edit) { open = false; actions.edit() }
                MenuRow(stringResource(R.string.board_delete), Icons.Outlined.DeleteOutline, danger = true) { open = false; onMenu(PostDialog.DeletePost) }
            }
            if (!ui.isAuthor) {
                if (!ui.reported) MenuRow(stringResource(R.string.board_report), Icons.Outlined.Flag) { open = false; onMenu(PostDialog.Report(null)) }
                if (!post.deleted) MenuRow(stringResource(R.string.board_block), Icons.Outlined.Block) { open = false; onMenu(PostDialog.Block(post.authorUid)) }
            }
            if (ui.me.admin) {
                MenuRow(stringResource(if (post.pinned) R.string.board_admin_unpin else R.string.board_admin_pin), Icons.Outlined.PushPin) {
                    open = false
                    actions.setPinned(!post.pinned)
                }
                MenuRow(stringResource(if (post.hidden) R.string.board_admin_unhide else R.string.board_admin_hide), if (post.hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff) {
                    open = false
                    actions.setHidden(null, !post.hidden)
                }
                MenuRow(stringResource(R.string.board_admin_delete), Icons.Outlined.DeleteForever, danger = true) { open = false; actions.hardDelete(null) }
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) Tokens.DangerText else Tokens.Ink
    DropdownMenuItem(
        text = { KoText(text, MaterialTheme.typography.bodyLarge, color = color) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        modifier = Modifier.minTouch(),
    )
}

/** 사진 2열 격자 + 동영상 줄 (사진·동영상 올리기가 켜졌을 때 올린 글만) */
@Composable
private fun MediaGrid(media: List<BoardMediaItem>, onOpen: (BoardMediaItem) -> Unit) {
    val loader = LocalThumbnailLoader.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        media.filter { !it.video }.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { i, m ->
                    val n = row * 2 + i + 1
                    val cd = stringResource(R.string.board_photo_cd, n)
                    val bitmap by produceState<ImageBitmap?>(null, m.url) { value = runCatching { loader(m.url) }.getOrNull() }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(Tokens.SurfaceSunken)
                            .clickable(role = Role.Image, onClickLabel = cd) { onOpen(m) }
                            .semantics { contentDescription = cd },
                        contentAlignment = Alignment.Center,
                    ) {
                        val b = bitmap
                        if (b != null) {
                            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                        } else {
                            Icon(Icons.Outlined.Image, null, tint = Tokens.InkTertiary)
                        }
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        media.filter { it.video }.forEach { m ->
            SecondaryButton(stringResource(R.string.board_video_open), onClick = { onOpen(m) }, icon = Icons.Outlined.PlayCircle)
        }
    }
}

@Composable
private fun CommentsHead(ui: BoardPostUi, post: BoardPost) {
    val visible = ui.comments.size
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KoText(
            stringResource(if (post.kind == BoardKind.Qna) R.string.board_answers else R.string.board_comments, visible),
            MaterialTheme.typography.titleLarge,
            color = Tokens.Ink,
            heading = true,
        )
        when {
            visible == 0 -> KoText(
                stringResource(if (post.kind == BoardKind.Qna) R.string.board_no_answers else R.string.board_no_comments),
                MaterialTheme.typography.bodyMedium,
                color = Tokens.InkSecondary,
            )
            post.kind == BoardKind.Qna && ui.isAuthor && !post.solved ->
                KoText(stringResource(R.string.board_accept_hint), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        }
    }
}

/** 댓글 묶음 하나 (맨 위 댓글 + 들여 쓴 답글). 채택한 답은 초록 2dp 테두리 */
@Composable
private fun ThreadCard(ui: BoardPostUi, post: BoardPost, t: CommentThread, actions: BoardPostActions, onMenu: (PostDialog) -> Unit) {
    BoardCard(border = if (t.accepted) Tokens.SuccessText else null) {
        Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
            if (t.accepted) StatusTag(stringResource(R.string.board_accepted), StatusKind.Allowed, icon = Icons.Outlined.Verified)
            CommentView(ui, post, t.root, reply = false, accepted = t.accepted, actions = actions, onMenu = onMenu)
            t.replies.forEach { r ->
                Box(Modifier.padding(start = 12.dp).startBar(Tokens.LineSoft, 2.dp).padding(start = 14.dp)) {
                    CommentView(ui, post, r, reply = true, accepted = false, actions = actions, onMenu = onMenu)
                }
            }
        }
    }
}

@Composable
private fun CommentView(
    ui: BoardPostUi,
    post: BoardPost,
    c: BoardComment,
    reply: Boolean,
    accepted: Boolean,
    actions: BoardPostActions,
    onMenu: (PostDialog) -> Unit,
) {
    val shown = BoardShow.comment(c, ui.blocked, ui.revealed)
    when (shown) {
        Shown.Deleted -> {
            PlaceholderLine(stringResource(R.string.board_deleted_comment), icon = Icons.Outlined.DeleteOutline)
            return
        }
        Shown.Blocked -> {
            PlaceholderLine(stringResource(R.string.board_blocked_comment), icon = Icons.Outlined.Block)
            return
        }
        Shown.Reported -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlaceholderLine(stringResource(R.string.board_reported_comment), icon = Icons.Outlined.GppMaybe)
                QuietButton(stringResource(R.string.board_reveal), onClick = { actions.reveal(c.id) })
            }
            return
        }
        else -> Unit
    }
    val mine = ui.me.uid == c.authorUid
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (shown == Shown.Hidden) PlaceholderLine(stringResource(R.string.board_hidden_comment), icon = Icons.Outlined.VisibilityOff)
        AuthorLine(c.authorUid, c.nickname, relativeTime(ui.now, c.createdAt), edited = c.edited) {
            if (c.authorUid == post.authorUid) StatusTag(stringResource(R.string.board_author_tag), StatusKind.Info, icon = Icons.Outlined.EditNote)
            if (c.authorUid in ui.admins) OperatorTag()
        }
        val mention = if (reply && c.replyToNick != null) "@${c.replyToNick} " else ""
        val body = buildAnnotatedString {
            if (mention.isNotEmpty()) withStyle(SpanStyle(color = Tokens.Accent, fontWeight = FontWeight.Bold)) { append(mention) }
            append(koDisplay(c.body))
        }
        val original = mention + c.body
        SelectionContainer {
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = Tokens.Ink,
                modifier = Modifier.semantics { text = androidx.compose.ui.text.AnnotatedString(original) },
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (ui.me.joined && !post.deleted) {
                CommentAction(stringResource(R.string.board_reply), Icons.AutoMirrored.Outlined.Reply, stringResource(R.string.board_reply_cd, c.nickname)) {
                    actions.reply(c)
                }
            }
            val liked = c.id in ui.likedComments
            CommentAction(
                stringResource(R.string.board_like_count, c.likeCount),
                if (liked) Icons.Outlined.ThumbUp else Icons.Outlined.ThumbUpOffAlt,
                tint = if (liked) Tokens.Accent else Tokens.InkSecondary,
                enabled = !mine,
            ) { actions.likeComment(c) }
            val canAccept = post.kind == BoardKind.Qna && ui.isAuthor && !reply && !mine
            if (canAccept) {
                CommentAction(
                    stringResource(if (accepted) R.string.board_unaccept else R.string.board_accept),
                    Icons.Outlined.TaskAlt,
                    stringResource(R.string.board_accept_cd, c.nickname).takeIf { !accepted },
                    tint = Tokens.SuccessText,
                ) { actions.accept(c) }
            }
            CommentMenu(ui, c, mine, actions, onMenu)
        }
    }
}

/** 댓글 아래 작은 글자 버튼 (아이콘 + 글, 누르는 칸은 minTouch) */
@Composable
private fun CommentAction(
    text: String,
    icon: ImageVector,
    contentDescription: String? = null,
    tint: androidx.compose.ui.graphics.Color = Tokens.InkSecondary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val style = MaterialTheme.typography.labelLarge
    Row(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .minTouch()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(textIconSize(LocalDimens.current.iconSmall + 4.dp, style)))
        KoText(text, style, color = if (enabled) Tokens.InkSecondary else Tokens.InkTertiary)
    }
}

@Composable
private fun CommentMenu(ui: BoardPostUi, c: BoardComment, mine: Boolean, actions: BoardPostActions, onMenu: (PostDialog) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.minTouchSize()) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.board_comment_more_cd), tint = Tokens.InkSecondary, modifier = Modifier.size(textIconSize(LocalDimens.current.icon, MaterialTheme.typography.labelLarge)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Tokens.Surface) {
            if (mine) {
                MenuRow(stringResource(R.string.board_edit), Icons.Outlined.Edit) { open = false; actions.editComment(c) }
                MenuRow(stringResource(R.string.board_delete), Icons.Outlined.DeleteOutline, danger = true) { open = false; onMenu(PostDialog.DeleteComment(c)) }
            } else {
                MenuRow(stringResource(R.string.board_report), Icons.Outlined.Flag) { open = false; onMenu(PostDialog.Report(c)) }
                MenuRow(stringResource(R.string.board_block), Icons.Outlined.Block) { open = false; onMenu(PostDialog.Block(c.authorUid)) }
            }
            if (ui.me.admin) {
                MenuRow(stringResource(if (c.hidden) R.string.board_admin_unhide else R.string.board_admin_hide), Icons.Outlined.VisibilityOff) {
                    open = false
                    actions.setHidden(c, !c.hidden)
                }
                MenuRow(stringResource(R.string.board_admin_delete), Icons.Outlined.DeleteForever, danger = true) { open = false; actions.hardDelete(c) }
            }
        }
    }
}

/** 댓글 쓰기 카드: 게시판 이름이 없으면 이름 정하기로 안내, 있으면 칸 + 경고 + 올리기(주 버튼) */
@Composable
private fun Composer(ui: BoardPostUi, post: BoardPost, actions: BoardPostActions) {
    if (post.deleted || post.hidden) return
    val dimens = LocalDimens.current
    BoardCard {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.gap)) {
            KoText(
                stringResource(if (post.kind == BoardKind.Qna) R.string.board_write_answer else R.string.board_write_comment),
                MaterialTheme.typography.titleMedium,
                color = Tokens.Ink,
                heading = true,
            )
            if (!ui.me.joined) {
                KoText(stringResource(R.string.board_join_to_comment), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                SecondaryButton(stringResource(R.string.board_join_start), onClick = actions.join, icon = Icons.Outlined.Edit)
                return@Column
            }
            val target = ui.replyTo ?: ui.editing
            if (target != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val style = MaterialTheme.typography.labelLarge
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (ui.replyTo != null) Icons.AutoMirrored.Outlined.Reply else Icons.Outlined.Edit,
                            null,
                            tint = Tokens.Accent,
                            modifier = Modifier.size(textIconSize(dimens.icon, style)),
                        )
                        KoText(
                            if (ui.replyTo != null) stringResource(R.string.board_replying_to, ui.replyTo.nickname) else stringResource(R.string.board_edit),
                            style,
                            Modifier.weight(1f),
                            color = Tokens.Accent,
                        )
                    }
                    QuietButton(stringResource(R.string.board_reply_cancel), onClick = actions.cancelCompose)
                }
            }
            BoardField(
                label = stringResource(R.string.board_field_body),
                value = ui.composer,
                onChange = actions.composerChange,
                max = BoardLimits.COMMENT.last,
                placeholder = stringResource(R.string.board_comment_hint),
                problem = if (ui.check.body == com.readyport.board.FieldProblem.TooLong) stringResource(R.string.board_comment_long) else null,
                minLines = 3,
            )
            PiiWarningCard(ui.composer, ui.check.pii, onAllow = if (ui.allowWarnings) null else actions.allowWarnings)
            if (ui.check.profanity) ProfanityNote()
            ui.error?.let { ErrorLine(it.text()) }
            val ready = ui.composer.isNotBlank() && !ui.check.blocking && (!ui.check.warnOnly || ui.allowWarnings) && !ui.sending
            PrimaryButton(stringResource(R.string.board_send), onClick = actions.send, enabled = ready, icon = Icons.AutoMirrored.Outlined.Reply)
        }
    }
}
