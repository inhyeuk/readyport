package com.readyport.ui.board

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.R
import com.readyport.board.BoardCountries
import com.readyport.board.BoardKind
import com.readyport.board.BoardLimits
import com.readyport.board.BoardMediaPrep
import com.readyport.board.BoardPost
import com.readyport.board.BoardRepository
import com.readyport.board.DraftCheck
import com.readyport.board.PreparedMedia
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.ChoiceSegments
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.nav.BoardWriteRoute
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ======================= 글쓰기·글 고치기 — DESIGN_SPEC 부록 L.7 =======================

/** 붙인 사진·동영상 하나 (올릴 바이트는 이미 줄이고 EXIF를 뺀 것) */
@Immutable
data class Attachment(val key: Int, val media: PreparedMedia, val preview: ImageBitmap?)

@Immutable
data class BoardWriteUi(
    val kind: BoardKind = BoardKind.Qna,
    val editId: String? = null,
    val country: String? = null,
    val title: String = "",
    val body: String = "",
    val check: DraftCheck = DraftCheck(),
    val allowWarnings: Boolean = false,
    /** 운영자가 켠 사진·동영상 올리기 (기본 꺼짐 — 꺼져 있으면 붙이는 버튼이 아예 없다) */
    val mediaEnabled: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val submitting: Boolean = false,
    val error: UiText? = null,
) {
    val photos: Int get() = attachments.count { !it.media.video }
    val hasVideo: Boolean get() = attachments.any { it.media.video }

    /** 올리기 버튼을 누를 수 있는지 */
    val ready: Boolean get() = !submitting && !check.blocking && (!check.warnOnly || allowWarnings)
}

data class BoardWriteActions(
    val setKind: (BoardKind) -> Unit = {},
    val setCountry: (String?) -> Unit = {},
    val setTitle: (String) -> Unit = {},
    val setBody: (String) -> Unit = {},
    val allowWarnings: () -> Unit = {},
    val pickPhotos: () -> Unit = {},
    val pickVideo: () -> Unit = {},
    val remove: (Int) -> Unit = {},
    val submit: () -> Unit = {},
)

@HiltViewModel
class BoardWriteViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: BoardRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val route = handle.toRoute<BoardWriteRoute>()
    private val _ui = MutableStateFlow(BoardWriteUi(kind = BoardKind.of(route.kind) ?: BoardKind.Qna, editId = route.postId))
    val ui: StateFlow<BoardWriteUi> = _ui.asStateFlow()

    /** 올린 글 id (새 글) 또는 고친 글 id — 화면이 그 글로 간다 */
    private val _done = MutableStateFlow<String?>(null)
    val done: StateFlow<String?> = _done.asStateFlow()
    private var editing: BoardPost? = null
    private var nextKey = 0

    init {
        viewModelScope.launch {
            val enabled = runCatching { repo.refreshConfig().mediaEnabled }.getOrDefault(false)
            _ui.update { it.copy(mediaEnabled = enabled) }
        }
        route.postId?.let { id ->
            viewModelScope.launch {
                val post = runCatching { repo.detail(id)?.post }.getOrNull() ?: return@launch
                editing = post
                _ui.update { it.copy(kind = post.kind, country = post.country, title = post.title, body = post.body, check = repo.check(post.title, post.body)) }
            }
        }
    }

    private fun recheck() = _ui.update { it.copy(check = repo.check(it.title, it.body), allowWarnings = false, error = null) }

    fun setKind(kind: BoardKind) = _ui.update { it.copy(kind = kind) }
    fun setCountry(c: String?) = _ui.update { it.copy(country = c) }
    fun setTitle(t: String) {
        _ui.update { it.copy(title = t) }
        recheck()
    }

    fun setBody(b: String) {
        _ui.update { it.copy(body = b) }
        recheck()
    }

    fun allowWarnings() = _ui.update { it.copy(allowWarnings = true) }

    /** 고른 사진(최대 4장까지 채운다) → 긴 변 1600px·EXIF 없는 JPEG */
    fun addPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            val room = BoardLimits.MAX_IMAGES - _ui.value.photos
            val prepared = withContext(Dispatchers.IO) {
                uris.take(room.coerceAtLeast(0)).mapNotNull { uri ->
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                        ?.let { BoardMediaPrep.prepareImage(it) }
                }
            }
            if (prepared.size < uris.take(room.coerceAtLeast(0)).size) _ui.update { it.copy(error = UiText(R.string.board_media_unreadable)) }
            _ui.update { s -> s.copy(attachments = s.attachments + prepared.map { Attachment(nextKey++, it, previewOf(it)) }) }
        }
    }

    fun addVideo(uri: Uri) {
        viewModelScope.launch {
            when (val r = withContext(Dispatchers.IO) { BoardMediaPrep.prepareVideo(context, uri) }) {
                is BoardMediaPrep.VideoCheck.Ok -> _ui.update { s -> s.copy(attachments = s.attachments.filterNot { it.media.video } + Attachment(nextKey++, r.media, null)) }
                BoardMediaPrep.VideoCheck.TooLong -> _ui.update { it.copy(error = UiText(R.string.board_video_too_long)) }
                BoardMediaPrep.VideoCheck.TooBig -> _ui.update { it.copy(error = UiText(R.string.board_video_too_big)) }
                BoardMediaPrep.VideoCheck.Unreadable -> _ui.update { it.copy(error = UiText(R.string.board_media_unreadable)) }
            }
        }
    }

    fun remove(key: Int) = _ui.update { s -> s.copy(attachments = s.attachments.filterNot { it.key == key }) }

    fun submit() {
        val s = _ui.value
        if (!s.ready) return
        _ui.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val post = editing
                val id = if (post != null) {
                    repo.edit(post, s.title, s.body, s.country, s.allowWarnings)
                    post.id
                } else {
                    repo.submit(s.kind, s.title, s.body, s.country, s.allowWarnings, s.attachments.map { it.media })
                }
                _ui.update { it.copy(submitting = false) }
                _done.value = id
            } catch (e: Exception) {
                _ui.update { it.copy(submitting = false, error = e.toUiText()) }
            }
        }
    }

    private fun previewOf(m: PreparedMedia): ImageBitmap? =
        runCatching { BitmapFactory.decodeByteArray(m.bytes, 0, m.bytes.size, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap() }.getOrNull()
}

/** 글쓰기 화면 (Hilt). [onDone]: 새 글이면 그 글로, 고친 글이면 뒤로 */
@Composable
fun BoardWriteScreen(onDone: (postId: String, edited: Boolean) -> Unit, viewModel: BoardWriteViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { done?.let { onDone(it, ui.editId != null) } }
    // 안드로이드 사진 선택기(권한 없이, 고른 것만 앱에 온다)
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(BoardLimits.MAX_IMAGES)) { uris ->
        if (uris.isNotEmpty()) viewModel.addPhotos(uris)
    }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::addVideo) }
    BoardWriteContent(
        ui,
        BoardWriteActions(
            setKind = viewModel::setKind,
            setCountry = viewModel::setCountry,
            setTitle = viewModel::setTitle,
            setBody = viewModel::setBody,
            allowWarnings = viewModel::allowWarnings,
            pickPhotos = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            pickVideo = { video.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            remove = viewModel::remove,
            submit = viewModel::submit,
        ),
    )
}

/**
 * 글쓰기 (상태 없는 Content): (Q&A) 좋은 질문 쓰는 법 → 게시판 → 나라(사진 칩) → 제목·내용(글자 수) →
 * 개인정보·욕설 경고 → (켜졌을 때만) 사진·동영상 → 올리기(주 버튼) → 공개 안내.
 */
@Composable
fun BoardWriteContent(ui: BoardWriteUi, actions: BoardWriteActions = BoardWriteActions()) {
    val dimens = LocalDimens.current
    val title = when {
        ui.editId != null -> R.string.board_write_title_edit
        ui.kind == BoardKind.Qna -> R.string.board_write_title_new_qna
        else -> R.string.board_write_title_new_talk
    }
    AppScreen(title = stringResource(title), speech = stringResource(R.string.board_write_speech), icon = Icons.Outlined.Edit) {
        if (ui.kind == BoardKind.Qna && ui.editId == null) {
            item(key = "tips") {
                CardNewsCard(
                    title = stringResource(R.string.board_tips_title),
                    icon = Icons.Outlined.TipsAndUpdates,
                    tone = BadgeTone.Accent,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconBullet(stringResource(R.string.board_tip_when), Icons.AutoMirrored.Outlined.EventNote, tone = BadgeTone.Accent)
                        IconBullet(stringResource(R.string.board_tip_tried), Icons.Outlined.Science, tone = BadgeTone.Accent)
                        IconBullet(stringResource(R.string.board_tip_private), Icons.Outlined.NoAccounts, tone = BadgeTone.Danger)
                    }
                }
            }
        }
        if (ui.editId == null) {
            item(key = "board") {
                FormBlock {
                    KoText(stringResource(R.string.board_write_board), MaterialTheme.typography.titleSmall, color = Tokens.Ink)
                    ChoiceSegments(
                        options = BoardKind.entries,
                        selected = ui.kind,
                        onSelect = actions.setKind,
                        label = { stringResource(it.label()) },
                        icon = { it.icon() },
                    )
                }
            }
        }
        item(key = "country") {
            FormBlock {
                KoText(stringResource(R.string.board_country_optional), MaterialTheme.typography.titleSmall, color = Tokens.Ink)
                FlowRow(
                    Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectChip(
                        selected = ui.country == null,
                        onClick = { actions.setCountry(null) },
                        label = stringResource(R.string.board_country_none),
                        leadingIcon = Icons.Outlined.Public,
                    )
                    BoardCountries.codes.forEach { c ->
                        SelectChip(
                            selected = ui.country == c,
                            onClick = { actions.setCountry(c) },
                            label = boardCountryName(c),
                            avatar = { CountryPhoto(c, 24.dp) },
                        )
                    }
                }
            }
        }
        item(key = "fields") {
            FormBlock {
                BoardField(
                    label = stringResource(R.string.board_field_title),
                    value = ui.title,
                    onChange = actions.setTitle,
                    max = BoardLimits.TITLE.last,
                    placeholder = stringResource(R.string.board_field_title_hint),
                    problem = if (ui.title.isNotEmpty()) titleProblem(ui.check.title) else null,
                    singleLine = true,
                )
                BoardField(
                    label = stringResource(R.string.board_field_body),
                    value = ui.body,
                    onChange = actions.setBody,
                    max = BoardLimits.BODY.last,
                    placeholder = stringResource(if (ui.kind == BoardKind.Qna) R.string.board_field_body_hint_qna else R.string.board_field_body_hint_talk),
                    problem = if (ui.body.isNotEmpty()) bodyProblem(ui.check.body) else null,
                    minLines = 6,
                )
            }
        }
        if (ui.check.pii.isNotEmpty()) {
            item(key = "pii") {
                PiiWarningCard("${ui.title}\n${ui.body}", ui.check.pii, onAllow = if (ui.allowWarnings) null else actions.allowWarnings)
            }
        }
        if (ui.check.profanity) item(key = "profanity") { ProfanityNote() }
        if (ui.mediaEnabled && ui.editId == null) {
            item(key = "media") { MediaPicker(ui, actions) }
        }
        ui.error?.let { e -> item(key = "error") { ErrorLine(e.text()) } }
        item(key = "submit") {
            PrimaryButton(
                stringResource(if (ui.editId != null) R.string.board_save_edit else R.string.board_submit),
                onClick = actions.submit,
                enabled = ui.ready && ui.title.isNotBlank() && ui.body.isNotBlank(),
                icon = Icons.AutoMirrored.Outlined.Send,
            )
        }
        item(key = "public") {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                IconBullet(stringResource(R.string.board_privacy_public), Icons.Outlined.Public)
                IconBullet(stringResource(R.string.board_privacy_local), Icons.Outlined.Lock)
            }
        }
    }
}

/** 사진·동영상 붙이기 (운영자가 켰을 때만): 사진 넣기 n/4 · 동영상 넣기 · 붙인 것 썸네일(빼기 버튼) · 위치 정보 지운다는 한 줄 */
@Composable
private fun MediaPicker(ui: BoardWriteUi, actions: BoardWriteActions) {
    FormBlock {
        KoText(stringResource(R.string.board_attach_title), MaterialTheme.typography.titleSmall, color = Tokens.Ink)
        SecondaryButton(
            stringResource(R.string.board_attach_photo, ui.photos, BoardLimits.MAX_IMAGES),
            onClick = actions.pickPhotos,
            icon = Icons.Outlined.AddPhotoAlternate,
            enabled = ui.photos < BoardLimits.MAX_IMAGES,
        )
        SecondaryButton(stringResource(R.string.board_attach_video), onClick = actions.pickVideo, icon = Icons.Outlined.Videocam, enabled = !ui.hasVideo)
        if (ui.attachments.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.attachments.forEachIndexed { i, a ->
                    Box(
                        Modifier.size(ThumbSize).clip(MaterialTheme.shapes.small).background(Tokens.SurfaceSunken),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (a.preview != null) {
                            Image(a.preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(ThumbSize))
                        } else {
                            Icon(Icons.Outlined.Movie, null, tint = Tokens.InkSecondary)
                        }
                        IconButton(onClick = { actions.remove(a.key) }, modifier = Modifier.align(Alignment.TopEnd).minTouchSize()) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.board_attach_remove_cd, i + 1),
                                tint = Tokens.Surface,
                                modifier = Modifier.background(Tokens.PhotoButtonBg, MaterialTheme.shapes.extraLarge),
                            )
                        }
                    }
                }
            }
        }
        IconBullet(stringResource(R.string.board_attach_note), Icons.Outlined.Lock)
    }
}

private val ThumbSize = 96.dp
