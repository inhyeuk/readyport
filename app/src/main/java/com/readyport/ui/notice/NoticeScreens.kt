package com.readyport.ui.notice

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.BuildConfig
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.notice.ListedNotice
import com.readyport.notice.Notice
import com.readyport.notice.NoticeButtons
import com.readyport.notice.NoticeChoice
import com.readyport.notice.NoticeContext
import com.readyport.notice.NoticeDoc
import com.readyport.notice.NoticeImage
import com.readyport.notice.NoticeMode
import com.readyport.notice.NoticePrimary
import com.readyport.notice.NoticeRepository
import com.readyport.notice.NoticeSelector
import com.readyport.notice.NoticeType
import com.readyport.trip.TripRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.EmptyState
import com.readyport.ui.components.EqualWidthPair
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.ListDivider
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.fullBleed
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.components.onLight
import com.readyport.ui.components.sectionGap
import com.readyport.ui.nav.NoticesRoute
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.video.LocalThumbnailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

// ======================= 공지 대화상자·공지사항 목록 (DESIGN_SPEC 부록 K) =======================
// 카드뉴스 모양: 흰 대화상자(모서리 28) + 맨 위 종류 색 띠 + 종류 배지(색·아이콘·글자) + 제목 + 쪽(그림·글).
// 쪽 넘기기는 `이전`·`다음` 버튼과 쪽 표시(점 + `1 / 3`) — 가로 스와이프 없음(스펙 7장 1번).

/** 종류별 색·아이콘·이름 (색만으로 전하지 않는다 — 배지에 아이콘과 글자를 함께) */
data class NoticeLook(val tone: BadgeTone, val kind: StatusKind, val icon: ImageVector, val label: Int)

fun noticeLook(type: NoticeType): NoticeLook = when (type) {
    NoticeType.Urgent -> NoticeLook(BadgeTone.Danger, StatusKind.Required, Icons.Outlined.NotificationImportant, R.string.notice_type_urgent)
    NoticeType.Normal -> NoticeLook(BadgeTone.Accent, StatusKind.Info, Icons.Outlined.Campaign, R.string.notice_type_normal)
    NoticeType.Event -> NoticeLook(BadgeTone.Help, StatusKind.Self, Icons.Outlined.Celebration, R.string.notice_type_event)
    NoticeType.Guide -> NoticeLook(BadgeTone.Success, StatusKind.Allowed, Icons.Outlined.TipsAndUpdates, R.string.notice_type_guide)
}

/** 대화상자 바깥(갤러리 캡처용 — 실제 대화상자는 시스템이 화면을 어둡게 한다) */
val NoticeScrim: Color = Color.Black.copy(alpha = 0.45f)

/** 대화상자 카드 최대 폭 (태블릿·가로 화면에서 글줄이 너무 길어지지 않게) */
private val NoticeMaxWidth = 520.dp

/** 맨 위 종류 색 띠 */
private val AccentStripHeight = 6.dp

/**
 * 공지 카드 (대화상자 안 내용 — 상태 없음. 갤러리·접근성 점검은 이것을 그대로 그린다).
 * 순서: 종류 색 띠 → 배지(종류·광고) + 쪽 수 + 닫기(X) → 제목 → 그림 → 글(길게 눌러 복사) → 링크(마지막 쪽) → 광고 끄는 방법 →
 * 쪽 점 → (긴급) `끝까지 읽으면 닫을 수 있어요` → `이전`·`다음/확인` → `오늘 하루 보지 않기`·`다시 보지 않기`.
 */
@Composable
fun NoticeCard(
    notice: Notice,
    mode: NoticeMode,
    page: Int,
    onPage: (Int) -> Unit,
    onChoice: (NoticeChoice) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val look = noticeLook(notice.type)
    val pages = notice.pages()
    val current = page.coerceIn(0, pages.lastIndex)
    val last = current == pages.lastIndex
    val buttons = NoticeButtons.of(notice.type, mode, current, pages.size)
    val pane = stringResource(R.string.notice_pane, notice.titleKo)
    val shape = MaterialTheme.shapes.extraLarge
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = modifier.widthIn(max = NoticeMaxWidth).fillMaxWidth().semantics { paneTitle = pane },
    ) {
        // 종류 색 띠 (장식 — 종류는 아래 배지 글자로도 알린다)
        Box(Modifier.fillMaxWidth().height(AccentStripHeight).background(look.tone.onLight).clearAndSetSemantics {})
        Column(
            Modifier.fillMaxWidth().padding(start = dimens.cardPadding, end = dimens.cardPadding, top = 12.dp, bottom = dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NoticeHeader(notice, look, current, pages.size, buttons.closable) { onChoice(NoticeChoice.Close) }
            KoText(notice.titleKo, MaterialTheme.typography.headlineSmall, color = Tokens.Ink, heading = true, glueShort = true)
            val p = pages[current]
            p.image?.let { NoticeImageBox(it) }
            p.text?.let { text ->
                // 글은 길게 눌러 복사할 수 있다(주소·날짜를 옮겨 적지 않게)
                SelectionContainer { KoText(text, MaterialTheme.typography.bodyLarge, color = Tokens.Ink) }
            }
            if (last) notice.link?.let { link ->
                val cd = stringResource(R.string.notice_link_cd, link.labelKo)
                SecondaryButton(
                    link.labelKo,
                    onClick = { onOpenLink(link.url) },
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    modifier = Modifier.semantics { contentDescription = cd },
                )
            }
            if (notice.promo) IconBullet(stringResource(R.string.notice_promo_optout), Icons.Outlined.NotificationsOff)
            if (pages.size > 1) PageDots(current, pages.size, look.tone)
            if (!buttons.closable) {
                KoText(
                    stringResource(R.string.notice_urgent_hint),
                    MaterialTheme.typography.bodyMedium,
                    color = Tokens.InkSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                val primaryText = stringResource(if (buttons.primary == NoticePrimary.Next) R.string.notice_next else R.string.notice_confirm)
                val primaryIcon = if (buttons.primary == NoticePrimary.Next) Icons.AutoMirrored.Outlined.NavigateNext else Icons.Outlined.Check
                val onPrimary = { if (buttons.primary == NoticePrimary.Next) onPage(current + 1) else onChoice(NoticeChoice.Close) }
                if (buttons.previous) {
                    EqualWidthPair(
                        gap = dimens.gap,
                        first = { m ->
                            SecondaryButton(
                                stringResource(R.string.notice_prev),
                                onClick = { onPage(current - 1) },
                                icon = Icons.AutoMirrored.Outlined.NavigateBefore,
                                tone = BadgeTone.Neutral,
                                modifier = m,
                            )
                        },
                        second = { m -> PrimaryButton(primaryText, onClick = onPrimary, icon = primaryIcon, modifier = m) },
                    )
                } else {
                    PrimaryButton(primaryText, onClick = onPrimary, icon = primaryIcon)
                }
                if (buttons.dismissRow) {
                    EqualWidthPair(
                        gap = dimens.inner,
                        first = { m -> QuietButton(stringResource(R.string.notice_today), { onChoice(NoticeChoice.Today) }, m.fillMaxWidth()) },
                        second = { m -> QuietButton(stringResource(R.string.notice_never), { onChoice(NoticeChoice.Never) }, m.fillMaxWidth()) },
                    )
                }
            }
        }
    }
}

/** 배지(종류·광고) + 쪽 수 + 닫기(X). 닫기를 숨길 때(긴급 공지 중간 쪽)도 자리를 비워 두지 않는다 */
@Composable
private fun NoticeHeader(notice: Notice, look: NoticeLook, page: Int, count: Int, closable: Boolean, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            StatusTag(stringResource(look.label), look.kind, icon = look.icon)
            if (notice.promo) StatusTag(stringResource(R.string.notice_promo_tag), StatusKind.Soon, icon = Icons.Outlined.Sell)
            if (count > 1) {
                val cd = stringResource(R.string.notice_page_cd, page + 1, count)
                KoText(
                    stringResource(R.string.notice_page, page + 1, count),
                    MaterialTheme.typography.labelLarge,
                    color = Tokens.InkSecondary,
                    // 쪽을 넘기면 TalkBack이 `3쪽 중 2쪽`을 읽는다
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = cd
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
        }
        if (closable) {
            IconButton(onClick = onClose, modifier = Modifier.minTouchSize()) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.notice_close_cd), tint = Tokens.InkSecondary)
            }
        } else {
            // 닫기가 아직 없을 때(긴급 공지 중간 쪽)도 같은 높이를 둔다 — 마지막 쪽에서 X가 나타날 때 제목이 밀리지 않게
            Box(Modifier.minTouchSize().clearAndSetSemantics {})
        }
    }
}

/**
 * 쪽 표시 점 (장식 — 누를 수 없다. 쪽 수 글자 `1 / 3`이 같은 것을 말한다). 지금 쪽은 종류 색 긴 점(인디케이터 — 알약 허용 D3).
 * 누르는 점은 쉬운 모드 56dp 칸이 여섯 개면 360dp 폭에 들어가지 않아 두지 않았다 — 쪽은 `이전`·`다음`으로 넘긴다.
 */
@Composable
private fun PageDots(page: Int, count: Int, tone: BadgeTone) {
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == page
            Box(
                Modifier
                    .size(width = if (active) 24.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (active) tone.onLight else Tokens.LineStrong),
            )
        }
    }
}

/**
 * 공지 그림 (우리 Hosting 주소만 — [com.readyport.ui.video.NetworkThumbnails] 허용 목록). 메모리에만 둔다.
 * 그림이 오면 TalkBack은 대체 글을 읽는다. 아직 안 왔거나 못 불러오면 그 자리에 대체 글을 그대로 보인다(뜻을 잃지 않게).
 */
@Composable
private fun NoticeImageBox(image: NoticeImage) {
    val loader = LocalThumbnailLoader.current
    val bitmap by produceState<ImageBitmap?>(null, image.url) { value = loader(image.url) }
    val ratio = bitmap?.let { (it.width.toFloat() / it.height.coerceAtLeast(1)).coerceIn(0.75f, 1.8f) } ?: (4f / 3f)
    val shape = MaterialTheme.shapes.medium
    val b = bitmap
    if (b != null) {
        // 카드뉴스 그림은 카드 양 끝까지(안쪽 여백 밖으로) — 좁은 화면에서도 그림 속 글자가 조금이라도 크게 보이게
        Image(
            b,
            contentDescription = image.altKo,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fullBleed(LocalDimens.current.cardPadding).fillMaxWidth().aspectRatio(ratio).background(Tokens.SurfaceSunken),
        )
    } else {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(shape).background(Tokens.SurfaceSunken).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = Tokens.InkTertiary)
            KoText(image.altKo, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary, textAlign = TextAlign.Center)
        }
    }
}

/**
 * 공지 대화상자. [mode] Launch = 앱을 켤 때(종류별 버튼 규칙), Reader = 목록·알림에서 다시 보기(언제든 닫힘).
 * 바깥을 눌러도 닫히지 않는다(실수로 닫지 않게). 뒤로 가기: 닫을 수 있으면 닫고, 긴급 공지 중간 쪽이면 앞 쪽으로.
 * 카드 전체가 세로로 스크롤된다 — 글자 200%·쉬운 모드에서도 버튼까지 닿는다.
 */
@Composable
fun NoticeDialog(notice: Notice, mode: NoticeMode, onChoice: (NoticeChoice) -> Unit, onOpenLink: (String) -> Unit) {
    var page by rememberSaveable(notice.key, mode) { mutableIntStateOf(0) }
    val count = notice.pages().size
    val buttons = NoticeButtons.of(notice.type, mode, page, count)
    val back = {
        when {
            buttons.closable -> onChoice(NoticeChoice.Close)
            page > 0 -> page -= 1
        }
    }
    Dialog(
        onDismissRequest = back,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, dismissOnBackPress = true),
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(page) { scroll.scrollTo(0) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                NoticeCard(notice, mode, page, onPage = { page = it.coerceIn(0, count - 1) }, onChoice = onChoice, onOpenLink = onOpenLink)
            }
        }
    }
}

/** 링크 열기(브라우저·Play 스토어). 서명본 검사(NoticeRules.linkOk)를 지난 주소만 여기 온다 */
@Composable
fun rememberOpenLink(): (String) -> Unit {
    val context = LocalContext.current
    return { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
}

// ======================= 공지사항 목록 (설정 › 공지·소식 › 공지사항) =======================

data class NoticesUi(val loading: Boolean = true, val items: List<ListedNotice> = emptyList())

@HiltViewModel
class NoticesViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: NoticeRepository,
    private val settings: SettingsRepository,
    private val trips: TripRepository,
) : ViewModel() {
    private val openId = handle.toRoute<NoticesRoute>().openId
    private val _ui = MutableStateFlow(NoticesUi())
    val ui: StateFlow<NoticesUi> = _ui.asStateFlow()
    private val _opened = MutableStateFlow<Notice?>(null)
    val opened: StateFlow<Notice?> = _opened.asStateFlow()

    init {
        viewModelScope.launch {
            val ctx = context()
            repo.cached()?.let { show(it, ctx, loading = true) }
            val doc = repo.refresh(REFRESH_TIMEOUT_MS)
            show(doc, ctx, loading = false)
            // 알림을 눌러 들어왔으면 그 공지를 바로 연다(목록에 없으면 목록만)
            if (!openId.isNullOrEmpty()) _opened.value = _ui.value.items.firstOrNull { it.notice.id == openId }?.notice
        }
    }

    private suspend fun context(): NoticeContext {
        val s = settings.current()
        return NoticeContext(
            now = Instant.now(),
            today = LocalDate.now(),
            versionCode = BuildConfig.VERSION_CODE,
            countries = s.favorites + trips.all().map { it.country },
            promoOn = s.promoPush,
        )
    }

    private fun show(doc: NoticeDoc?, ctx: NoticeContext, loading: Boolean) {
        _ui.value = NoticesUi(loading = loading, items = NoticeSelector.listed(doc, ctx))
    }

    fun open(notice: Notice?) {
        _opened.value = notice
    }

    private companion object {
        const val REFRESH_TIMEOUT_MS = 6_000L
    }
}

@Composable
fun NoticesScreen(viewModel: NoticesViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val opened by viewModel.opened.collectAsStateWithLifecycle()
    val openLink = rememberOpenLink()
    NoticesContent(ui, onOpen = viewModel::open)
    opened?.let { n -> NoticeDialog(n, NoticeMode.Reader, onChoice = { viewModel.open(null) }, onOpenLink = openLink) }
}

/**
 * 공지사항 목록 (상태 없음). `지금 공지`(우선순위 순) → `지난 공지`(최근 끝난 순). 줄 = 종류 배지 + 제목 + `종류 · 날짜` + 셰브론,
 * 누르면 같은 카드 대화상자(다시 보기 — 언제든 닫힘). 맨 아래 `서명한 공지만` 한 줄.
 */
@Composable
fun NoticesContent(ui: NoticesUi, onOpen: (Notice) -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    AppScreen(
        title = stringResource(R.string.notices_title),
        subtitle = stringResource(R.string.notices_subtitle),
        speech = stringResource(R.string.notices_speech),
        icon = Icons.Outlined.Campaign,
    ) {
        val current = ui.items.filter { it.current }
        val past = ui.items.filterNot { it.current }
        when {
            ui.loading && ui.items.isEmpty() -> item(key = "loading") { NoticesLoading() }
            ui.items.isEmpty() -> item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.Campaign,
                    title = stringResource(R.string.notices_empty_title),
                    body = stringResource(R.string.notices_empty_body),
                )
            }
            else -> {
                if (current.isNotEmpty()) item(key = "current") { NoticeGroup(stringResource(R.string.notices_current), current, onOpen, zone) }
                if (current.isNotEmpty() && past.isNotEmpty()) sectionGap("gap-past")
                if (past.isNotEmpty()) item(key = "past") { NoticeGroup(stringResource(R.string.notices_past), past, onOpen, zone) }
            }
        }
        item(key = "signed") { IconBullet(stringResource(R.string.notices_signed_note), Icons.Outlined.Verified) }
    }
}

@Composable
private fun NoticesLoading() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = Tokens.Accent)
        KoText(stringResource(R.string.notices_loading), MaterialTheme.typography.bodyLarge, color = Tokens.InkSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NoticeGroup(title: String, items: List<ListedNotice>, onOpen: (Notice) -> Unit, zone: ZoneId) {
    ListGroup(title) {
        items.forEachIndexed { i, item ->
            if (i > 0) ListDivider()
            val n = item.notice
            val look = noticeLook(n.type)
            val type = stringResource(look.label)
            val meta = if (item.current) {
                stringResource(R.string.notices_row_meta, type, shortDate(n.startAt(), zone))
            } else {
                stringResource(R.string.notices_row_ended, type, shortDate(n.endAt(), zone))
            }
            // TalkBack: 제목 + `긴급 공지 · 2026. 10. 8.` + 버튼 (줄 전체가 하나)
            ListRow(title = n.titleKo, icon = look.icon, tone = look.tone, body = meta, onClick = { onOpen(n) })
        }
    }
}

/** `2026. 10. 8.` (기기 시간대) */
@Composable
private fun shortDate(t: Instant?, zone: ZoneId): String {
    val d = t?.atZone(zone)?.toLocalDate() ?: return ""
    return stringResource(R.string.notice_date_short, d.year, d.monthValue, d.dayOfMonth)
}

/** `2026년 10월 8일` — 설정의 동의한 날 */
@Composable
fun longDate(date: LocalDate): String = stringResource(R.string.notice_date_ymd, date.year, date.monthValue, date.dayOfMonth)

/** 갤러리·테스트용: 대화상자를 어두운 바탕 위에 그대로 (실제 대화상자 창 없이 — 접근성 점검이 볼 수 있게) */
@Composable
fun NoticeOnScrim(notice: Notice, mode: NoticeMode, page: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 700.dp).background(Tokens.Ground).background(NoticeScrim).padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        NoticeCard(notice, mode, page, onPage = {}, onChoice = {}, onOpenLink = {})
    }
}
