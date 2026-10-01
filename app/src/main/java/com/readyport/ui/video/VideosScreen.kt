package com.readyport.ui.video

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.R
import com.readyport.pack.PackRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.ChoiceSegments
import com.readyport.ui.components.EmptyState
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LinkRow
import com.readyport.ui.components.ListDivider
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.LocalAppActions
import com.readyport.ui.components.LocalShowBack
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.isNarrowWindow
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.components.textIconSize
import com.readyport.ui.nav.VideosRoute
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.video.Video
import com.readyport.video.VideoRepository
import com.readyport.video.VideoSort
import com.readyport.video.sortedBy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

// ---------------- 썸네일 ----------------

/**
 * YouTube가 준 썸네일 주소(i.ytimg.com)에서 그대로 불러온다. 파일로 저장하지 않고 메모리에만 잠깐 둔다.
 * 테스트에서는 네트워크 없이 null 을 돌려주는 로더로 바꿔 끼운다.
 */
val LocalThumbnailLoader = staticCompositionLocalOf<suspend (String) -> ImageBitmap?> { NetworkThumbnails::load }

object NetworkThumbnails {
    private val cache = LruCache<String, ImageBitmap>(60)

    suspend fun load(url: String): ImageBitmap? {
        if (!url.startsWith("https://i.ytimg.com/")) return null
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                try {
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }?.also { cache.put(url, it) }
    }
}

// ---------------- 화면 상태 ----------------

sealed interface VideosState {
    data object Loading : VideosState
    /** 인터넷이 없거나 목록이 없거나 30일이 지났을 때 */
    data object Unavailable : VideosState
    data class Ready(val items: List<Video>) : VideosState
}

@HiltViewModel
class VideosViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val videos: VideoRepository,
    private val packs: PackRepository,
) : ViewModel() {
    val country = handle.toRoute<VideosRoute>().country
    private val _state = MutableStateFlow<VideosState>(VideosState.Loading)
    val state: StateFlow<VideosState> = _state.asStateFlow()
    private val _countryKo = MutableStateFlow(country)
    val countryKo: StateFlow<String> = _countryKo.asStateFlow()

    init {
        viewModelScope.launch {
            packs.pack(country)?.value?.names?.ko?.let { _countryKo.value = it }
            val list = videos.list(country)
            _state.value = if (list == null || list.items.isEmpty()) VideosState.Unavailable else VideosState.Ready(list.items)
        }
    }
}

@Composable
fun VideosScreen(viewModel: VideosViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val countryKo by viewModel.countryKo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    VideosContent(countryKo, state, onOpen = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } })
}

// ---------------- 화면 ----------------

/** 정렬 칸: 글자 + 아이콘 (DESIGN_SPEC 5.4 — 조회수 Visibility / 최신 CalendarMonth(재검토 R11: NewReleases는 경고처럼 읽힘) / 구독자 Groups) */
private val SortOptions = listOf(
    Triple(VideoSort.Views, R.string.videos_sort_views, Icons.Outlined.Visibility),
    Triple(VideoSort.Recent, R.string.videos_sort_recent, IconKeys.sortRecent),
    Triple(VideoSort.Subscribers, R.string.videos_sort_subscribers, Icons.Outlined.Groups),
)

@Composable
fun VideosContent(countryKo: String, state: VideosState, onOpen: (String) -> Unit, initialSort: VideoSort = VideoSort.Views) {
    var sort by rememberSaveable { mutableStateOf(initialSort) }
    var query by rememberSaveable { mutableStateOf("") }
    val showBack = LocalShowBack.current
    val goBack = LocalAppActions.current.goBack
    // 320×470 화면 예산(DESIGN_SPEC 6-07): 좁은 창에서는 제목 앞 아이콘 배지를 뺀다 — 첫 영상 카드가 스크롤 없이 보이게
    val narrow = isNarrowWindow()
    AppScreen(
        title = stringResource(R.string.videos_title, countryKo),
        subtitle = stringResource(R.string.videos_subtitle),
        speech = stringResource(R.string.videos_speech, countryKo),
        icon = if (narrow) null else Icons.Outlined.SmartDisplay,
    ) {
        // YouTube API 정책: 출처가 YouTube라는 것을 분명히 보여 준다 (누를 수 없는 고지 띠)
        item(key = "notice") { NoticeBanner(stringResource(R.string.videos_notice)) }
        when (state) {
            VideosState.Loading -> item(key = "loading") { LoadingState() }
            VideosState.Unavailable -> item(key = "none") {
                EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.videos_unavailable_title),
                    body = stringResource(R.string.videos_unavailable_body),
                )
            }
            is VideosState.Ready -> {
                item(key = "search") { SearchField(query) { query = it } }
                item(key = "sort") {
                    ChoiceSegments(
                        options = SortOptions,
                        selected = SortOptions.first { it.first == sort },
                        onSelect = { sort = it.first },
                        label = { stringResource(it.second) },
                        icon = { it.third },
                    )
                }
                val shown = state.items.matching(query).sortedBy(sort)
                item(key = "count") {
                    KoText(
                        stringResource(R.string.videos_count, shown.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = Tokens.InkSecondary,
                    )
                }
                if (shown.isEmpty()) {
                    item(key = "no-match") {
                        NoticeBanner(stringResource(R.string.videos_search_empty, query.trim()), icon = Icons.Outlined.SearchOff)
                    }
                }
                shown.forEach { v ->
                    item(key = "v-${v.id}") { VideoCard(v, onOpen) }
                }
            }
        }
        // 목록이 길어서 맨 아래에도 이전 화면으로 가는 버튼을 둔다
        if (showBack) {
            item(key = "back") {
                SecondaryButton(stringResource(R.string.action_back), onClick = goBack, icon = Icons.AutoMirrored.Outlined.ArrowBack)
            }
        }
        item(key = "terms") { TermsGroup(onOpen) }
    }
}

/** 제목·채널 이름에 검색어가 들어 있는 영상 (띄어쓰기·대소문자 무시) */
fun List<Video>.matching(query: String): List<Video> {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return this
    return filter { v ->
        val text = (v.title + " " + v.channelTitle).lowercase()
        val compact = text.replace(" ", "")
        words.all { w -> w in text || w in compact }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        label = { KoText(stringResource(R.string.videos_search_label)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onChange("") }, modifier = Modifier.minTouchSize()) {
                    Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.videos_search_clear))
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        // 조작 요소 경계는 LineStrong(3:1 이상), 포커스는 Accent. 흰 바탕으로 회색 화면 위에서 입력칸이 또렷하게
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Tokens.Surface,
            unfocusedContainerColor = Tokens.Surface,
            focusedBorderColor = Tokens.Accent,
            unfocusedBorderColor = Tokens.LineStrong,
            focusedLeadingIconColor = Tokens.Accent,
            unfocusedLeadingIconColor = Tokens.InkSecondary,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 불러오는 중: 가운데 진행 표시 + 기존 문구 */
@Composable
private fun LoadingState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = Tokens.Accent)
        KoText(
            stringResource(R.string.videos_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = Tokens.InkSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 영상 카드: 카드 전체가 버튼(누르면 YouTube 앱, 없으면 브라우저). 썸네일 16:9 + 가운데 재생 표시 + 길이,
 * 제목은 줄 수 제한 없이 전부, 채널·구독자, 조회수·날짜·YouTube.
 * TalkBack 이름 `videos_open(제목)`은 이 클릭 노드 하나에만 둔다.
 */
@Composable
private fun VideoCard(v: Video, onOpen: (String) -> Unit) {
    val loader = LocalThumbnailLoader.current
    val image by produceState<ImageBitmap?>(null, v.thumbnail) { value = loader(v.thumbnail) }
    val label = stringResource(R.string.videos_open, v.title)
    val number = NumberFormat.getIntegerInstance(Locale.KOREA)
    val shape = MaterialTheme.shapes.large
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(shape)
            .clip(shape)
            .clickable(role = Role.Button, onClickLabel = label) { onOpen(v.watchUrl) }
            .semantics { contentDescription = label },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Thumbnail(image, v.durationSeconds)
            Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KoText(v.title, style = MaterialTheme.typography.titleMedium, color = Tokens.Ink)
                KoText(
                    listOfNotNull(
                        v.channelTitle,
                        v.subscriberCount?.let { stringResource(R.string.videos_subscribers, number.format(it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tokens.InkSecondary,
                )
                FlowRow(
                    Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    v.viewCount?.let { MetaItem(Icons.Outlined.Visibility, stringResource(R.string.videos_views, number.format(it))) }
                    v.publishedAt.take(10).replace('-', '.').takeIf { it.length == 10 }?.let { MetaItem(Icons.Outlined.CalendarMonth, it) }
                    Text(stringResource(R.string.videos_source_youtube), style = MaterialTheme.typography.bodySmall, color = Tokens.InkTertiary)
                }
            }
        }
    }
}

/** 썸네일(남색 바탕 위 사진) + 가운데 흰 재생 원 + 오른쪽 아래 길이 배지 — 사진 위 표시는 모두 자체 바탕 위 */
@Composable
private fun Thumbnail(image: ImageBitmap?, durationSeconds: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(Tokens.Navy),
    ) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        Surface(
            color = Tokens.Surface,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.Center).size(PlayCircleSize),
        ) {
            // 재생 기호는 관례대로 채움 아이콘 (상태 표시가 아님)
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Tokens.Navy, modifier = Modifier.padding(10.dp))
        }
        if (durationSeconds > 0) {
            Surface(
                color = DurationBg,
                contentColor = OnDark.content,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
                Text(
                    duration(durationSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** 가운데 재생 표시(흰 원) 크기 — 장식이라 누르는 자리가 아니다(카드 전체가 버튼) */
private val PlayCircleSize = 48.dp

/** 사진 위 길이 배지 바탕 (흰 사진 위에서도 흰 글자가 읽히게 진한 반투명 검정) */
private val DurationBg = Color.Black.copy(alpha = 0.75f)

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = Tokens.InkTertiary, modifier = Modifier.size(textIconSize(LocalDimens.current.iconSmall)))
        KoText(text, style = MaterialTheme.typography.bodySmall, color = Tokens.InkTertiary)
    }
}

/**
 * YouTube API 약관 묶음: 설명문(videos_terms, 빼지 않음)을 먼저 보이고 그 아래 공식 링크 2개(LinkRow — 06 귀국 링크와 같은 모양).
 * 안쪽 여백은 목록 행 토큰(listRowPadding — 다른 ListGroup 행·카드 내용과 같은 시작선, 재검토 R4).
 * 링크는 큰 글자에서도 폭을 다 쓰도록 설명문 글 줄이 아니라 정책 배지와 같은 선에서 시작한다
 * (LinkRow 자체 안쪽 4dp + 바깥 listRowPadding − 4dp). 그래서 설명문과 링크 사이 구분선도 들여 쓰지 않는다.
 */
@Composable
private fun TermsGroup(onOpen: (String) -> Unit) {
    val dimens = LocalDimens.current
    ListGroup {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = dimens.listRowPadding, vertical = dimens.listRowPaddingVertical),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconBadge(Icons.Outlined.Policy, tone = BadgeTone.Neutral)
            KoText(
                stringResource(R.string.videos_terms),
                style = MaterialTheme.typography.bodyMedium,
                color = Tokens.InkSecondary,
                modifier = Modifier.weight(1f),
            )
        }
        ListDivider(indent = false)
        Column(Modifier.padding(horizontal = dimens.listRowPadding - 4.dp, vertical = 4.dp)) {
            LinkRow(stringResource(R.string.videos_youtube_terms), onClick = { onOpen(YOUTUBE_TERMS) })
            LinkRow(stringResource(R.string.videos_google_privacy), onClick = { onOpen(GOOGLE_PRIVACY) })
        }
    }
}

private fun duration(s: Int): String {
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

const val YOUTUBE_TERMS = "https://www.youtube.com/t/terms"
const val GOOGLE_PRIVACY = "https://policies.google.com/privacy"
