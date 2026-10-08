package com.readyport.ui.board

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.ExpandCircleDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.board.BoardCountries
import com.readyport.board.BoardError
import com.readyport.board.BoardKind
import com.readyport.board.BoardPost
import com.readyport.board.BoardQuery
import com.readyport.board.BoardRepository
import com.readyport.board.BoardShow
import com.readyport.board.BoardSort
import com.readyport.board.Shown
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.ChoiceSegments
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.ListDivider
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.RowTrailing
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionArt
import com.readyport.ui.components.SectionCards
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

// ======================= 게시판 첫 화면 (탭) — DESIGN_SPEC 부록 L.4 =======================

/** 게시판 첫 화면 상태 */
@Immutable
data class BoardHomeUi(
    val kind: BoardKind = BoardKind.Qna,
    val sort: BoardSort = BoardSort.Latest,
    val country: String? = null,
    val search: String? = null,
    val pinned: List<BoardPost> = emptyList(),
    val posts: List<BoardPost> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val offline: Boolean = false,
    /** 인터넷은 되는데 서버가 거절·준비 중(색인 생성 등) — `인터넷이 없어요`로 잘못 안내하지 않게 따로 둔다 */
    val serverError: Boolean = false,
    val blocked: Set<String> = emptySet(),
    val revealed: Set<String> = emptySet(),
    /** 운영자 게시판 ID (글 카드의 `운영자` 표시) */
    val admins: Set<String> = emptySet(),
    val now: Instant = Instant.now(),
    /** 규칙에 동의하고 게시판 이름을 정했는지 — 아니면 쓰기 버튼이 규칙·이름 화면으로 */
    val ready: Boolean = false,
)

/** 게시판 첫 화면에서 나가는 길 */
data class BoardNav(
    val openPost: (String) -> Unit = {},
    val write: (BoardKind) -> Unit = {},
    /** 처음 쓰기 전: 규칙 동의 → 게시판 이름 → 그 게시판 글쓰기 */
    val join: (BoardKind) -> Unit = {},
    val openRules: () -> Unit = {},
)

@HiltViewModel
class BoardViewModel @Inject constructor(private val repo: BoardRepository) : ViewModel() {
    private val _ui = MutableStateFlow(BoardHomeUi())
    val ui: StateFlow<BoardHomeUi> = _ui.asStateFlow()
    private var cursor: Any? = null

    init {
        viewModelScope.launch { repo.blocked.collect { b -> _ui.update { it.copy(blocked = b) } } }
        viewModelScope.launch {
            runCatching { repo.refreshConfig() }
            _ui.update { it.copy(admins = runCatching { repo.adminSet() }.getOrDefault(emptySet())) }
        }
        viewModelScope.launch { repo.markRepliesRead() }
        viewModelScope.launch {
            // 처음 한 번 + 내가 쓰거나 지울 때마다(글 올리기·댓글·지우기) — 그때만 목록을 다시 불러온다
            repo.revision.collect { rev ->
                _ui.update { it.copy(ready = runCatching { repo.ready() }.getOrDefault(false)) }
                if (rev > 0) reload()
            }
        }
        reload()
    }

    private fun query(s: BoardHomeUi) = BoardQuery(s.kind, s.sort, s.country, s.search)

    private var loadedAt = 0L

    /** 게시판을 다시 볼 때(탭을 다시 열거나 앱으로 돌아올 때) 1분이 지났으면 새로 읽는다 — 읽기 한도를 아끼면서 남의 새 글도 보이게 */
    fun refreshIfStale(nowMillis: Long = System.currentTimeMillis()) {
        if (nowMillis - loadedAt > STALE_MS) reload()
    }

    fun reload() {
        loadedAt = System.currentTimeMillis()
        _ui.update { it.copy(loading = true, offline = false, serverError = false, now = Instant.now()) }
        cursor = null
        viewModelScope.launch {
            val s = _ui.value
            try {
                val pinned = if (s.search == null) runCatching { repo.pinned(s.kind) }.getOrDefault(emptyList()) else emptyList()
                val page = repo.page(query(s), null)
                cursor = page.next
                _ui.update { it.copy(loading = false, pinned = pinned, posts = page.posts, canLoadMore = page.next != null) }
            } catch (e: BoardError) {
                val offline = e is BoardError.Offline
                _ui.update {
                    it.copy(loading = false, offline = offline, serverError = !offline, posts = emptyList(), pinned = emptyList(), canLoadMore = false)
                }
            }
        }
    }

    fun loadMore() {
        val after = cursor ?: return
        if (_ui.value.loadingMore) return
        _ui.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = repo.page(query(_ui.value), after)
                cursor = page.next
                _ui.update { s -> s.copy(loadingMore = false, posts = (s.posts + page.posts).distinctBy { it.id }, canLoadMore = page.next != null) }
            } catch (e: BoardError) {
                _ui.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun selectKind(kind: BoardKind) {
        if (kind == _ui.value.kind) return
        // 자유 토론에는 `답변 기다려요`가 없다
        _ui.update { it.copy(kind = kind, sort = if (kind == BoardKind.Talk && it.sort == BoardSort.Waiting) BoardSort.Latest else it.sort) }
        reload()
    }

    fun selectSort(sort: BoardSort) {
        if (sort == _ui.value.sort) return
        _ui.update { it.copy(sort = sort) }
        reload()
    }

    fun selectCountry(country: String?) {
        if (country == _ui.value.country) return
        _ui.update { it.copy(country = country) }
        reload()
    }

    fun search(text: String?) {
        val q = text?.trim()?.takeIf { it.length >= 2 }
        if (q == _ui.value.search) return
        _ui.update { it.copy(search = q) }
        reload()
    }

    fun reveal(id: String) = _ui.update { it.copy(revealed = it.revealed + id) }

    private companion object {
        const val STALE_MS = 60_000L
    }
}

/** 게시판 탭 (Hilt 화면) */
@Composable
fun BoardScreen(nav: BoardNav, viewModel: BoardViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfStale() }
    BoardHomeContent(
        ui = ui,
        nav = nav.copy(write = { kind -> if (ui.ready) nav.write(kind) else nav.join(kind) }),
        actions = BoardHomeActions(
            selectKind = viewModel::selectKind,
            selectSort = viewModel::selectSort,
            selectCountry = viewModel::selectCountry,
            search = viewModel::search,
            loadMore = viewModel::loadMore,
            retry = viewModel::reload,
            reveal = viewModel::reveal,
            contact = { contactOperator(context) },
        ),
    )
}

/** 운영자에게 알리기 — 메일 앱(받는 곳: 개인정보 처리방침의 연락처). 개인정보를 미리 채우지 않는다 */
fun contactOperator(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$OPERATOR_EMAIL".toUri())
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.board_contact_subject))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** 운영자 연락처 (개인정보 처리방침 8절과 같은 주소) */
const val OPERATOR_EMAIL = "inhyeuk@gmail.com"

data class BoardHomeActions(
    val selectKind: (BoardKind) -> Unit = {},
    val selectSort: (BoardSort) -> Unit = {},
    val selectCountry: (String?) -> Unit = {},
    val search: (String?) -> Unit = {},
    val loadMore: () -> Unit = {},
    val retry: () -> Unit = {},
    val reveal: (String) -> Unit = {},
    val contact: () -> Unit = {},
)

/**
 * 게시판 첫 화면 (상태 없는 Content — 갤러리·테스트):
 * 게시판 고르기(그림 카드 둘) → 게시판 머리(그림 + 한 줄 목적 + 쓰기 버튼 = 화면의 주 버튼 하나) → 정렬·나라·찾기 →
 * 고정 글 → 글 카드 → 더 보기 → 빈 상태 / 공개 안내·규칙·운영자 연락.
 */
@Composable
fun BoardHomeContent(ui: BoardHomeUi, nav: BoardNav = BoardNav(), actions: BoardHomeActions = BoardHomeActions()) {
    val dimens = LocalDimens.current
    var pickCountry by rememberSaveable { mutableStateOf(false) }
    if (pickCountry) {
        CountryPickerDialog(ui.country, onPick = { actions.selectCountry(it); pickCountry = false }, onDismiss = { pickCountry = false })
    }
    AppScreen(title = stringResource(R.string.board_title), speech = stringResource(R.string.board_speech)) {
        item(key = "boards") {
            SectionCards(
                options = BoardKind.entries,
                selected = ui.kind,
                onSelect = actions.selectKind,
                label = { stringResource(it.label()) },
                art = { SectionArt(it.art(), it.tone()) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item(key = "hero-${ui.kind.id}") { BoardHero(ui.kind, onWrite = { nav.write(ui.kind) }) }
        item(key = "filters") {
            BoardFilters(ui, actions, onPickCountry = { pickCountry = true })
        }
        if (ui.search != null) {
            item(key = "search-head") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KoText(stringResource(R.string.board_search_result, ui.search), MaterialTheme.typography.titleMedium, color = Tokens.Ink, heading = true)
                    KoText(stringResource(R.string.board_search_note), MaterialTheme.typography.bodySmall, color = Tokens.InkTertiary)
                }
            }
        }
        when {
            ui.loading -> item(key = "loading") { LoadingRow() }
            ui.offline -> item(key = "offline") {
                BoardEmpty(
                    title = stringResource(R.string.board_offline_title),
                    body = stringResource(R.string.board_offline_body),
                    icon = Icons.Outlined.CloudOff,
                ) {
                    SecondaryButton(stringResource(R.string.board_retry), onClick = actions.retry, icon = Icons.Outlined.Refresh, fillWidth = false)
                }
            }
            ui.serverError -> item(key = "server-error") {
                BoardEmpty(
                    title = stringResource(R.string.board_server_error_title),
                    body = stringResource(R.string.board_server_error_body),
                    icon = Icons.Outlined.Refresh,
                ) {
                    SecondaryButton(stringResource(R.string.board_retry), onClick = actions.retry, icon = Icons.Outlined.Refresh, fillWidth = false)
                }
            }
            else -> {
                val pinned = ui.pinned.filter { BoardShow.post(it, ui.blocked, ui.revealed) != Shown.Blocked }
                pinned.forEach { p ->
                    item(key = "pin-${p.id}") {
                        PostCard(p, BoardShow.post(p, ui.blocked, ui.revealed), ui.now, { nav.openPost(p.id) }, { actions.reveal(p.id) }, operator = p.authorUid in ui.admins)
                    }
                }
                val posts = ui.posts.filter { BoardShow.post(it, ui.blocked, ui.revealed) != Shown.Blocked }
                posts.forEach { p ->
                    item(key = "post-${p.id}") {
                        PostCard(p, BoardShow.post(p, ui.blocked, ui.revealed), ui.now, { nav.openPost(p.id) }, { actions.reveal(p.id) }, operator = p.authorUid in ui.admins)
                    }
                }
                if (posts.isEmpty() && pinned.isEmpty()) {
                    item(key = "empty") { EmptyFor(ui) }
                }
                if (ui.canLoadMore) {
                    item(key = "more") {
                        if (ui.loadingMore) {
                            LoadingRow()
                        } else {
                            SecondaryButton(stringResource(R.string.board_load_more), onClick = actions.loadMore, icon = Icons.Outlined.ExpandCircleDown)
                        }
                    }
                }
            }
        }
        sectionGap("gap-footer")
        item(key = "footer") {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.gap)) {
                IconBullet(stringResource(R.string.board_public_note), Icons.Outlined.Lock)
                ListGroup {
                    ListRow(
                        stringResource(R.string.board_rules_title),
                        icon = Icons.Outlined.Gavel,
                        body = stringResource(R.string.settings_board_rules_body),
                        onClick = nav.openRules,
                    )
                    ListDivider()
                    ListRow(
                        stringResource(R.string.board_contact),
                        icon = Icons.Outlined.MailOutline,
                        trailing = RowTrailing.External,
                        onClick = actions.contact,
                    )
                }
            }
        }
    }
}

/** 게시판 머리: 그림 패널 + 게시판 이름 + 한 줄 목적 + 쓰기(주 버튼) — Q&A는 `공식 안내 아님` 한 줄 */
@Composable
private fun BoardHero(kind: BoardKind, onWrite: () -> Unit) {
    val dimens = LocalDimens.current
    BoardCard {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.inner + 4.dp)) {
            val texts: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KoText(stringResource(kind.longLabel()), MaterialTheme.typography.labelMedium, color = kind.badgeTone().content, heading = true)
                    KoText(
                        stringResource(if (kind == BoardKind.Qna) R.string.board_qna_purpose else R.string.board_talk_purpose),
                        MaterialTheme.typography.bodyLarge,
                        color = Tokens.Ink,
                    )
                }
            }
            if (isStackedLayout()) {
                texts()
            } else {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconBadge(kind.icon(), tone = kind.badgeTone())
                    Box(Modifier.weight(1f)) { texts() }
                }
            }
            PrimaryButton(
                stringResource(if (kind == BoardKind.Qna) R.string.board_write_qna else R.string.board_write_talk),
                onClick = onWrite,
                icon = Icons.Outlined.Edit,
            )
            if (kind == BoardKind.Qna) {
                IconBullet(stringResource(R.string.board_qna_not_official), Icons.Outlined.Policy, tone = BadgeTone.Neutral)
            }
        }
    }
}

/** 정렬(최신·인기·답변 기다려요) · 나라 · 찾기 */
@Composable
private fun BoardFilters(ui: BoardHomeUi, actions: BoardHomeActions, onPickCountry: () -> Unit) {
    var draft by rememberSaveable(ui.search) { mutableStateOf(ui.search.orEmpty()) }
    val sorts = if (ui.kind == BoardKind.Qna) BoardSort.entries else listOf(BoardSort.Latest, BoardSort.Popular)
    Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
        ChoiceSegments(
            options = sorts,
            selected = ui.sort,
            onSelect = actions.selectSort,
            label = {
                stringResource(
                    when (it) {
                        BoardSort.Latest -> R.string.board_sort_latest
                        BoardSort.Popular -> R.string.board_sort_popular
                        BoardSort.Waiting -> R.string.board_sort_waiting
                    },
                )
            },
            icon = {
                when (it) {
                    BoardSort.Latest -> Icons.Outlined.NewReleases
                    BoardSort.Popular -> Icons.AutoMirrored.Outlined.TrendingUp
                    BoardSort.Waiting -> Icons.Outlined.HourglassTop
                }
            },
        )
        val countryLabel = ui.country?.let { boardCountryName(it) } ?: stringResource(R.string.board_country_all)
        CountryFilterButton(ui.country, stringResource(R.string.board_country_filter, countryLabel), onPickCountry)
        val searchName = stringResource(R.string.board_search_hint)
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchName },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { KoText(stringResource(R.string.board_search_hint), MaterialTheme.typography.bodyLarge, color = Tokens.InkTertiary) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Tokens.InkSecondary) },
            trailingIcon = if (draft.isNotEmpty() || ui.search != null) {
                {
                    IconButton(onClick = { draft = ""; actions.search(null) }, modifier = Modifier.minTouchSize()) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.board_search_clear))
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { actions.search(draft) }),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Tokens.Surface,
                unfocusedContainerColor = Tokens.Surface,
                unfocusedBorderColor = Tokens.LineStrong,
                focusedBorderColor = Tokens.Accent,
            ),
        )
    }
}

/** 나라 고르기 버튼(흰 바탕 + 테두리): 고른 나라 사진 + `나라: 태국` + 펼침 표시 */
@Composable
private fun CountryFilterButton(country: String?, label: String, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val style = MaterialTheme.typography.labelLarge
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .minTouch()
            .clipBorder(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val iconSize = textIconSize(dimens.icon, style)
        if (country != null) {
            CountryPhoto(country, iconSize)
        } else {
            Icon(Icons.Outlined.Public, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(iconSize))
        }
        KoText(label, style, Modifier.weight(1f), color = Tokens.Ink)
        Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = Tokens.InkSecondary, modifier = Modifier.size(iconSize))
    }
}

/** 나라 고르기 대화상자: `모든 나라` + 9개 나라(사진·이름) 라디오 목록 */
@Composable
fun CountryPickerDialog(selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit, allLabel: String = stringResource(R.string.board_country_all)) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minTouch()) {
                KoText(stringResource(R.string.board_dismiss), MaterialTheme.typography.labelLarge, color = Tokens.Accent)
            }
        },
        title = { KoText(stringResource(R.string.board_country_pick), MaterialTheme.typography.titleLarge, glueShort = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                CountryRadioRow(null, allLabel, selected == null) { onPick(null) }
                BoardCountries.codes.forEach { c ->
                    CountryRadioRow(c, boardCountryName(c), selected == c) { onPick(c) }
                }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = Tokens.Surface,
        titleContentColor = Tokens.Ink,
        textContentColor = Tokens.Ink,
    )
}

@Composable
private fun CountryRadioRow(code: String?, label: String, selected: Boolean, onClick: () -> Unit) {
    val style = MaterialTheme.typography.bodyLarge
    Row(
        Modifier
            .fillMaxWidth()
            .minTouch()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Tokens.Accent, unselectedColor = Tokens.LineStrong))
        val size = textIconSize(LocalDimens.current.icon, style)
        if (code != null) CountryPhoto(code, size) else Icon(Icons.Outlined.Public, null, tint = Tokens.Accent, modifier = Modifier.size(size))
        KoText(label, style, Modifier.weight(1f), color = Tokens.Ink)
    }
}

@Composable
private fun LoadingRow() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), color = Tokens.Accent, strokeWidth = 3.dp)
        KoText(stringResource(R.string.board_loading), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
    }
}

/** 빈 상태: 그림 패널(빈 공책) + 제목 + 한 줄 (+ 버튼) */
@Composable
fun BoardEmpty(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon == null) {
            ArtPanel(BoardIllus.Empty, com.readyport.ui.components.IllusTones.Violet, EmptyArtSize)
        } else {
            Box(
                Modifier.size(EmptyArtSize).clipBorder(MaterialTheme.shapes.extraLarge, fill = Tokens.SurfaceSunken, border = null),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Tokens.InkSecondary, modifier = Modifier.size(48.dp))
            }
        }
        KoText(title, MaterialTheme.typography.titleLarge, color = Tokens.Ink, textAlign = TextAlign.Center, heading = true, glueShort = true)
        if (body != null) KoText(body, MaterialTheme.typography.bodyLarge, color = Tokens.InkSecondary, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

private val EmptyArtSize = 112.dp

@Composable
private fun EmptyFor(ui: BoardHomeUi) {
    val (title, body) = when {
        ui.search != null -> R.string.board_empty_search_title to R.string.board_empty_search_body
        ui.sort == BoardSort.Waiting -> R.string.board_empty_waiting_title to R.string.board_empty_waiting_body
        ui.kind == BoardKind.Qna -> R.string.board_empty_qna_title to R.string.board_empty_qna_body
        else -> R.string.board_empty_talk_title to R.string.board_empty_talk_body
    }
    BoardEmpty(stringResource(title), stringResource(body))
}
