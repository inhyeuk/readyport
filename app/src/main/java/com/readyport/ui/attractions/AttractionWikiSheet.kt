package com.readyport.ui.attractions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.attractions.WikiPage
import com.readyport.attractions.wiki.WikiHosts
import com.readyport.attractions.wiki.WikiSummary
import com.readyport.attractions.wiki.WikiSummaryResult
import com.readyport.attractions.wiki.WikipediaSummaryClient
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/**
 * 위키백과 요약 불러오기 — 실제 앱은 [WikipediaSummaryClient.Default](세션 메모리 캐시), 테스트는 네트워크 없는 가짜.
 * ([com.readyport.ui.video.LocalThumbnailLoader]와 같은 방식)
 */
val LocalWikiSummaryLoader = staticCompositionLocalOf<suspend (WikiPage) -> WikiSummaryResult> {
    { page -> WikipediaSummaryClient.Default.summary(page) }
}

/**
 * 상세 '위키백과에서 보기' 버튼 + 요약 팝업(아래에서 올라오는 시트). 사용자가 누를 때만 위키백과에 요청한다.
 * 한국어 문서가 있으면 한국어, 없으면 영어('영어 위키백과' 표시).
 */
@Composable
fun AttractionWikiEntry(page: WikiPage, openLink: (String) -> Unit) {
    var open by rememberSaveable(page) { mutableStateOf(false) }
    SecondaryButton(stringResource(R.string.attractions_wiki_open), onClick = { open = true }, icon = Icons.AutoMirrored.Outlined.MenuBook, tone = BadgeTone.Teal)
    if (open) AttractionWikiSheet(page, onDismiss = { open = false }, openLink = openLink)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttractionWikiSheet(page: WikiPage, onDismiss: () -> Unit, openLink: (String) -> Unit) {
    val load = LocalWikiSummaryLoader.current
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember(page) { mutableStateOf<WikiSummaryResult?>(null) }
    LaunchedEffect(page, attempt) {
        result = null
        result = load(page)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tokens.Surface,
        contentColor = Tokens.Ink,
    ) {
        WikiSheetContent(page, result, onRetry = { attempt++ }, openLink = openLink, onClose = onDismiss)
    }
}

/**
 * 팝업 내용 (테스트가 시트 창 없이 그대로 캡처한다): 머리 안내 → (영어 위키백과) → 상태별 내용 → 닫기.
 * [result] null = 불러오는 중.
 */
@Composable
fun WikiSheetContent(
    page: WikiPage,
    result: WikiSummaryResult?,
    onRetry: () -> Unit,
    openLink: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = dimens.screenPadding, end = dimens.screenPadding, bottom = dimens.gap),
        verticalArrangement = Arrangement.spacedBy(dimens.gap),
    ) {
        NoticeBanner(stringResource(R.string.attractions_wiki_header), icon = Icons.Outlined.Info)
        if (page.lang == "en") StatusTag(stringResource(R.string.attractions_wiki_english), StatusKind.Info, icon = Icons.Outlined.Translate)
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
            when (result) {
                null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Tokens.Accent, strokeWidth = 3.dp)
                    KoText(stringResource(R.string.attractions_wiki_loading), MaterialTheme.typography.bodyLarge, color = Tokens.InkSecondary)
                }
                is WikiSummaryResult.Ready -> WikiSummaryBody(result.summary, openLink)
                WikiSummaryResult.Offline -> {
                    IconBullet(stringResource(R.string.attractions_wiki_offline), Icons.Outlined.CloudOff)
                    SecondaryButton(stringResource(R.string.attractions_wiki_retry), onClick = onRetry, icon = Icons.Outlined.Refresh)
                }
                WikiSummaryResult.NotFound -> IconBullet(stringResource(R.string.attractions_wiki_not_found), Icons.Outlined.SearchOff)
                WikiSummaryResult.Failed -> {
                    IconBullet(stringResource(R.string.attractions_wiki_failed), Icons.Outlined.ReportProblem)
                    SecondaryButton(stringResource(R.string.attractions_wiki_retry), onClick = onRetry, icon = Icons.Outlined.Refresh)
                }
            }
        }
        QuietButton(stringResource(R.string.attractions_wiki_close), onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/** 제목 · 글(이미지 없음) · 출처 줄(문서·라이선스 링크) · '위키백과에서 전체 보기' */
@Composable
private fun WikiSummaryBody(summary: WikiSummary, openLink: (String) -> Unit) {
    KoText(summary.title, MaterialTheme.typography.titleLarge, color = Tokens.Ink, heading = true, glueShort = true)
    KoText(summary.extract, MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
    WikiAttribution(summary, openLink)
    PrimaryButton(stringResource(R.string.attractions_wiki_full), onClick = { openLink(summary.pageUrl) }, icon = Icons.AutoMirrored.Outlined.OpenInNew)
}

/** '출처: 위키백과 «제목» · CC BY-SA 4.0' — 제목은 문서로, CC BY-SA 4.0은 이용 조건으로 가는 링크 (CLAUDE.md 7: CC BY-SA 출처 표기) */
@Composable
private fun WikiAttribution(summary: WikiSummary, openLink: (String) -> Unit) {
    val line = stringResource(R.string.attractions_wiki_attribution, summary.title)
    val styles = TextLinkStyles(SpanStyle(color = Tokens.Accent, textDecoration = TextDecoration.Underline))
    val text = remember(line, summary.pageUrl) {
        buildAnnotatedString {
            append(line)
            val t = line.indexOf(summary.title)
            if (t >= 0) {
                addLink(LinkAnnotation.Url(summary.pageUrl, styles) { openLink(summary.pageUrl) }, t, t + summary.title.length)
            }
            val l = line.lastIndexOf(LICENSE_LABEL)
            if (l >= 0) {
                addLink(LinkAnnotation.Url(WikiHosts.LICENSE_URL, styles) { openLink(WikiHosts.LICENSE_URL) }, l, l + LICENSE_LABEL.length)
            }
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
}

private const val LICENSE_LABEL = "CC BY-SA 4.0"
