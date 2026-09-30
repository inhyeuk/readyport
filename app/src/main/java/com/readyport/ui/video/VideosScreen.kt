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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.readyport.ui.components.LocalAppActions
import com.readyport.ui.components.LocalShowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.TopicCard
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

@Composable
fun VideosContent(countryKo: String, state: VideosState, onOpen: (String) -> Unit, initialSort: VideoSort = VideoSort.Views) {
    var sort by rememberSaveable { mutableStateOf(initialSort) }
    var query by rememberSaveable { mutableStateOf("") }
    val showBack = LocalShowBack.current
    val goBack = LocalAppActions.current.goBack
    AppScreen(
        title = stringResource(R.string.videos_title, countryKo),
        subtitle = stringResource(R.string.videos_subtitle),
        speech = stringResource(R.string.videos_speech, countryKo),
    ) {
        // YouTube API 정책: 출처가 YouTube라는 것을 분명히 보여 준다
        item(key = "notice") { TopicCard(stringResource(R.string.videos_notice), null, tone = CardTone.Notice) }
        when (state) {
            VideosState.Loading -> item(key = "loading") { Text(stringResource(R.string.videos_loading), style = MaterialTheme.typography.bodyLarge) }
            VideosState.Unavailable -> item(key = "none") {
                TopicCard(stringResource(R.string.videos_unavailable_title), stringResource(R.string.videos_unavailable_body), tone = CardTone.Caution)
            }
            is VideosState.Ready -> {
                item(key = "search") { SearchField(query) { query = it } }
                item(key = "sort") { SortPicker(sort) { sort = it } }
                val shown = state.items.matching(query).sortedBy(sort)
                item(key = "count") {
                    Text(
                        stringResource(R.string.videos_count, shown.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (shown.isEmpty()) {
                    item(key = "no-match") {
                        TopicCard(stringResource(R.string.videos_search_empty, query.trim()), null, tone = CardTone.Notice)
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
                OutlinedButton(onClick = goBack, modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight)) {
                    Text(stringResource(R.string.action_back), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item(key = "terms") {
            InfoCard(tone = CardTone.Notice) {
                Text(stringResource(R.string.videos_terms), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onOpen(YOUTUBE_TERMS) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.videos_youtube_terms))
                    }
                    OutlinedButton(onClick = { onOpen(GOOGLE_PRIVACY) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.videos_google_privacy))
                    }
                }
            }
        }
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
        label = { Text(stringResource(R.string.videos_search_label)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.videos_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SortPicker(selected: VideoSort, onSelect: (VideoSort) -> Unit) {
    val labels = listOf(
        VideoSort.Views to R.string.videos_sort_views,
        VideoSort.Recent to R.string.videos_sort_recent,
        VideoSort.Subscribers to R.string.videos_sort_subscribers,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, (s, label) ->
            SegmentedButton(
                selected = s == selected,
                onClick = { onSelect(s) },
                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                icon = {},
                modifier = Modifier.heightIn(min = LocalDimens.current.buttonHeight),
            ) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** 썸네일 전체가 버튼: 누르면 YouTube 앱(없으면 브라우저)에서 연다 */
@Composable
private fun VideoCard(v: Video, onOpen: (String) -> Unit) {
    val loader = LocalThumbnailLoader.current
    val image by produceState<ImageBitmap?>(null, v.thumbnail) { value = loader(v.thumbnail) }
    val label = stringResource(R.string.videos_open, v.title)
    val number = NumberFormat.getIntegerInstance(Locale.KOREA)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.large)
                .background(Tokens.Navy)
                .clickable(role = Role.Button, onClickLabel = label) { onOpen(v.watchUrl) }
                .semantics { contentDescription = label },
        ) {
            image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            Surface(
                color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = CircleShape,
                modifier = Modifier.align(Alignment.Center).size(56.dp),
            ) { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(12.dp)) }
            if (v.durationSeconds > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f), contentColor = Color.White, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                ) { Text(duration(v.durationSeconds), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            }
        }
        Text(v.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(
                v.channelTitle,
                v.subscriberCount?.let { stringResource(R.string.videos_subscribers, number.format(it)) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            listOfNotNull(
                v.viewCount?.let { stringResource(R.string.videos_views, number.format(it)) },
                v.publishedAt.take(10).replace('-', '.').takeIf { it.length == 10 },
                stringResource(R.string.videos_source_youtube),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
