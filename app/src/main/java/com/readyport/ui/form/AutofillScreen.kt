package com.readyport.ui.form

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.autofill.RecipeStep
import com.readyport.autofill.UrlPolicy
import com.readyport.security.SecureScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.KoreanBreak
import com.readyport.ui.components.LockedState
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SecurityBanner
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.TextCircle
import com.readyport.ui.components.TrailingFlow
import com.readyport.ui.components.breakAfterDots
import com.readyport.ui.components.keepWords
import com.readyport.ui.components.largeFont
import com.readyport.ui.components.startBar
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.wallet.rememberDeviceAuth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 엔진이 돌려준 결과 (engine.js fill) */
data class FillReport(val filled: List<String>, val assist: List<String>, val missing: List<String>, val checkpoints: List<String>)

internal object EngineResult {
    /** evaluateJavascript 결과는 JS 값을 JSON으로 한 번 더 감싼다. 엔진은 JSON 문자열을 돌려주므로 두 번 푼다 */
    fun unwrap(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return runCatching { Json.decodeFromString<String>(raw) }.getOrNull()
    }

    fun fill(raw: String?): FillReport? = unwrap(raw)?.let { s ->
        runCatching {
            val o = Json.parseToJsonElement(s).jsonObject
            fun list(k: String) = o[k]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            FillReport(list("filled"), list("assist"), list("missing"), list("checkpoints"))
        }.getOrNull()
    }

    fun probe(raw: String?): List<Boolean> = unwrap(raw)?.let { s ->
        runCatching { Json.parseToJsonElement(s).jsonArray.map { it.jsonPrimitive.boolean } }.getOrNull()
    }.orEmpty()

    fun read(raw: String?): Map<String, String?> = unwrap(raw)?.let { s ->
        runCatching {
            (Json.parseToJsonElement(s) as JsonObject).mapValues { (_, v) -> runCatching { v.jsonPrimitive.content }.getOrNull() }
        }.getOrNull()
    }.orEmpty()
}

@Composable
fun AutofillScreen(onManual: () -> Unit, viewModel: AutofillViewModel = hiltViewModel()) {
    SecureScreen()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val auth = rememberDeviceAuth()
    val recipe = ui.context?.recipe
    val engine = ui.engine
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var visibleSteps by remember { mutableStateOf<List<Boolean>>(emptyList()) }
    var report by remember { mutableStateOf<FillReport?>(null) }
    var compare by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var nothingVisible by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (recipe == null || engine == null) return
    val official = UrlPolicy.matches(currentUrl, recipe.officialUrlPatterns)

    fun fillNow() {
        val wv = webView ?: return
        if (!official) return
        wv.evaluateJavascript(engine, null)
        wv.evaluateJavascript(viewModel.probeScript() ?: return) { raw ->
            val probes = EngineResult.probe(raw)
            visibleSteps = probes
            val steps = recipe.steps.filterIndexed { i, _ -> probes.getOrNull(i) == true }.map { it.id }.toSet()
            nothingVisible = steps.isEmpty()
            if (steps.isEmpty()) return@evaluateJavascript
            wv.evaluateJavascript(viewModel.fillScript(steps) ?: return@evaluateJavascript) { fillRaw ->
                val r = EngineResult.fill(fillRaw)
                report = r
                if (r == null) {
                    viewModel.report(steps.joinToString("+"), "engine_error")
                    return@evaluateJavascript
                }
                if (r.missing.isNotEmpty()) {
                    wv.evaluateJavascript("window.__readyport ? JSON.stringify(window.__readyport.siteVersion()) : null") { v ->
                        viewModel.report(steps.joinToString("+"), "selector_missing", EngineResult.unwrap(v)?.trim('"'))
                    }
                }
                // 입력값 대조: 앱이 채운 칸을 다시 읽어 원래 값과 비교 (PRD 5.3)
                wv.evaluateJavascript(viewModel.readScript(r.filled) ?: return@evaluateJavascript) { readRaw ->
                    val read = EngineResult.read(readRaw)
                    compare = r.filled.associateWith { key -> read[key] == ui.values[key]?.value }
                }
            }
        }
    }

    AutofillContent(
        ui = ui,
        site = SiteState(official, currentUrl?.toUri()?.host.orEmpty(), visibleSteps, nothingVisible),
        fill = FillState(report, compare, captured),
        onFill = ::fillNow,
        onManual = {
            viewModel.report("-", "manual_mode_chosen")
            onManual()
        },
        onManualAfterFail = onManual,
        onSaveCapture = {
            webView?.let { wv ->
                val png = captureWebView(wv)
                scope.launch { captured = viewModel.saveCapture(png) }
            }
        },
        onUnlock = { auth { viewModel.unlock() } },
    ) { siteModifier ->
        OfficialSiteWebView(
            startUrl = recipe.startUrl,
            patterns = recipe.officialUrlPatterns,
            engine = engine,
            onCreated = { webView = it },
            onUrl = { url ->
                currentUrl = url
                val marker = recipe.submittedUrlContains
                if (marker != null && url.contains(marker)) viewModel.onSubmittedPage()
            },
            modifier = siteModifier,
        )
    }
}

/** 공식 사이트 연결 상태 (WebView가 알려 준 것): 공식 호스트인지, 호스트 이름, 지금 보이는 사이트 단계, 채울 칸이 하나도 안 보임 */
@Immutable
data class SiteState(
    val official: Boolean = false,
    val host: String = "",
    val visibleSteps: List<Boolean> = emptyList(),
    val nothingVisible: Boolean = false,
)

/** 채우기 결과: 엔진 보고, 칸마다 입력값 대조(같아요/달라요), 제출 확인 화면을 저장했는지 */
@Immutable
data class FillState(
    val report: FillReport? = null,
    val compare: Map<String, Boolean> = emptyMap(),
    val captured: Boolean = false,
)

/**
 * 위 안내 영역의 최대 높이(화면 높이 비율). 넘치면 그 안에서 스크롤 — 공식 사이트에 높이를 남긴다.
 * 보통 글자는 안내 전체가 거의 다 보이게(38%), 큰 글자는 28%, 채우기를 시작한 뒤(결과가 있으면)는 이미 읽은 안내라 22%.
 */
private const val GUIDE_MAX_FRACTION = 0.38f
private const val GUIDE_MAX_FRACTION_LARGE = 0.28f
private const val GUIDE_MAX_FRACTION_AFTER = 0.22f

/** 아래 결과 글(대조 태그 등)의 최대 높이. 버튼은 그 아래 늘 보인다 */
private const val RESULT_MAX_FRACTION = 0.12f

/**
 * 자동 입력 (PRD 5.3, DESIGN_SPEC 6-17 공통): 위 안내(정부 비제휴 첫 항목 → 보안 한 줄 → 공식 사이트 연결 상태·단계 → 직접 할 곳)
 * → 공식 사이트([content], WebView) → 아래 결과·버튼.
 * 위 안내(38%·큰 글자 28%·채우기 뒤 22%)와 아래 결과 글(12%)은 정해진 높이까지만 차지하고 넘치면 그 안에서 스크롤한다
 * (더 있으면 아래 가장자리가 흐려진다) — 쉬운 모드·글자 200%에서도 공식 사이트가 화면의 절반 가까이를 쓴다(BundleECaptureTest가 확인).
 * 상태 없는 화면(테스트·캡처는 [content]에 자리표시를 넣는다).
 */
@Composable
internal fun AutofillContent(
    ui: AutofillUi,
    site: SiteState,
    fill: FillState,
    onFill: () -> Unit,
    onManual: () -> Unit,
    onManualAfterFail: () -> Unit,
    onSaveCapture: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val recipe = ui.context?.recipe ?: return
    val dimens = LocalDimens.current
    if (ui.locked) {
        Column(
            modifier
                .fillMaxSize()
                .background(Tokens.Ground)
                .verticalScroll(rememberScrollState())
                .padding(dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.gap),
        ) {
            TopNotices()
            LockedState(
                title = stringResource(R.string.wallet_locked_title),
                body = stringResource(R.string.form_locked_body),
                buttonLabel = stringResource(R.string.wallet_unlock),
                onUnlock = onUnlock,
            )
        }
        return
    }
    val labels = recipe.fields.associate { it.key to it.labels.ko }
    val started = fill.report != null || ui.submitted
    val guideFraction = when {
        started -> GUIDE_MAX_FRACTION_AFTER
        largeFont() -> GUIDE_MAX_FRACTION_LARGE
        else -> GUIDE_MAX_FRACTION
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Tokens.Ground)) {
        val screenH = maxHeight
        Column(Modifier.fillMaxSize()) {
            GuideHeader(site, recipe.steps, onManual, Modifier.heightIn(max = screenH * guideFraction))
            HorizontalDivider(thickness = 1.dp, color = Tokens.Line)
            content(Modifier.weight(1f).fillMaxWidth())
            HorizontalDivider(thickness = 1.dp, color = Tokens.Line)
            ResultPanel(ui, site, fill, labels, onFill, onManualAfterFail, onSaveCapture, screenH * RESULT_MAX_FRACTION)
        }
    }
}

/** 위 안내: 정해진 높이 안에서 스크롤. 아래로 더 있으면 아래 가장자리를 바탕색으로 흐려 '더 있음'을 보인다 */
@Composable
private fun GuideHeader(site: SiteState, steps: List<RecipeStep>, onManual: () -> Unit, modifier: Modifier = Modifier) {
    // 순서: 정부 비제휴 → 보안 → 공식 사이트 연결 상태 → 직접 할 곳 → 사이트 단계(덜 급한 정보라 맨 아래)
    FadingScrollColumn(Tokens.Ground, modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        TopNotices()
        SiteStatus(site, onManual)
        HumanStrip()
        SiteSteps(site, steps)
    }
}

/**
 * [modifier]의 높이 제한 안에서 세로로 스크롤하는 칸. 아래로 더 있으면 아래 가장자리를 [fade] 색으로 흐려 '더 있음'을 보인다.
 */
@Composable
private fun FadingScrollColumn(fade: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
        if (scroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Brush.verticalGradient(listOf(fade.copy(alpha = 0f), fade))),
            )
        }
    }
}

/**
 * 정부 비제휴(첫 항목) + 보안 한 줄 (입국 카드 화면 공통, DESIGN_SPEC 6장 머리말).
 * 큰 글자(130%↑)는 두 줄을 한 띠로 묶어 높이를 아낀다(정부 비제휴가 여전히 첫 줄).
 */
@Composable
private fun TopNotices() {
    if (largeFont()) {
        NoticeBanner(
            stringResource(R.string.guide_not_affiliated),
            icon = Icons.Outlined.Policy,
            secondLine = stringResource(R.string.settings_local_only_title),
            secondIcon = Icons.Outlined.Lock,
        )
    } else {
        NoticeBanner(stringResource(R.string.guide_not_affiliated), icon = Icons.Outlined.Policy)
        SecurityBanner(compact = true)
    }
}

/** 공식 사이트 연결 상태(초록 = 공식 / 빨강 = 아님, 색 + 아이콘 + 글자) + 수동 모드 */
@Composable
private fun SiteStatus(site: SiteState, onManual: () -> Unit) {
    val dimens = LocalDimens.current
    val bg = if (site.official) Tokens.SuccessBg else Tokens.DangerBg
    val fg = if (site.official) Tokens.SuccessText else Tokens.DangerText
    val manual = stringResource(R.string.form_manual_mode)
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().startBar(fg).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 수동 모드 버튼이 상태 글을 쪼갤 만큼 폭이 모자라면(큰 글자) 글 아래 줄로
            TrailingFlow(
                trailing = { QuietButton(manual, onClick = onManual, modifier = Modifier) },
                gap = 8.dp,
                belowGap = 0.dp,
                centerVertically = true,
            ) {
                Row(
                    Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (site.official) Icons.Outlined.VerifiedUser else Icons.Outlined.GppMaybe,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(dimens.icon),
                    )
                    val status = if (site.official) stringResource(R.string.autofill_connected, site.host) else stringResource(R.string.autofill_not_official)
                    // `연결됨 ·`이 한 덩어리로, 긴 호스트는 점 뒤에서 줄을 바꾼다 (`· / tdac.immigration.g / o.th` 방지)
                    val statusShown = if (site.official) {
                        keepWords(
                            stringResource(R.string.autofill_connected, breakAfterDots(site.host)).replace(" · ", "${KoreanBreak.NBSP}· "),
                        )
                    } else {
                        keepWords(status)
                    }
                    KoText(status, MaterialTheme.typography.labelLarge, color = fg, display = statusShown)
                }
            }
        }
    }
}

/** 사이트 단계: 지금 사이트 화면에 보이는 단계는 채운 번호 원 + 눈 아이콘 + 굵은 글자 (색만으로 전하지 않음) */
@Composable
private fun SiteSteps(site: SiteState, steps: List<RecipeStep>) {
    val dimens = LocalDimens.current
    FlowRow(
        Modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        steps.forEachIndexed { i, s ->
            val on = site.visibleSteps.getOrNull(i) == true
            Row(
                Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextCircle(
                    "${i + 1}",
                    minSize = dimens.stepBadge,
                    container = if (on) Tokens.Accent else Tokens.Surface,
                    content = if (on) Tokens.Surface else Tokens.InkSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                KoText(
                    s.titleKo,
                    MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                    color = Tokens.Ink,
                )
                if (on) Icon(Icons.Outlined.Visibility, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(textIconSize(dimens.iconSmall)))
            }
        }
    }
}

/** 사람이 직접 할 곳 안내 — 사이트 바로 위의 주의 띠 (누를 수 없음) */
@Composable
private fun HumanStrip() {
    val text = stringResource(R.string.autofill_human_banner)
    Surface(color = Tokens.CautionBg, contentColor = Tokens.CautionText, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().startBar(Tokens.CautionBorder).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.TouchApp,
                contentDescription = null,
                tint = Tokens.CautionText,
                modifier = Modifier.size(LocalDimens.current.icon),
            )
            KoText(text, MaterialTheme.typography.bodyMedium, color = Tokens.CautionText)
        }
    }
}

/**
 * 아래 결과·버튼: 결과 글(공식 사이트 아님·제출됨·보이는 칸 없음·채운 개수·대조 태그)은 [resultMax] 높이 안에서 스크롤,
 * 버튼은 그 아래 늘 보인다. 주 버튼은 하나: 보통은 '지금 화면 채우기', 칸을 못 찾았으면 '수동 모드', 제출 뒤에는 '확인 화면 저장'.
 */
@Composable
private fun ResultPanel(
    ui: AutofillUi,
    site: SiteState,
    fill: FillState,
    labels: Map<String, String>,
    onFill: () -> Unit,
    onManualAfterFail: () -> Unit,
    onSaveCapture: () -> Unit,
    resultMax: Dp,
) {
    val report = fill.report
    // 공식 사이트가 아닌 것이 확실할 때(주소를 알 때)만 — 첫 페이지를 읽는 중에는 띄우지 않는다
    val notOfficial = !site.official && site.host.isNotEmpty()
    val failed = report?.missing?.isNotEmpty() == true
    val hasResult = ui.submitted || site.nothingVisible || report != null || notOfficial
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().background(Tokens.Surface).padding(12.dp),
    ) {
        if (hasResult) {
            FadingScrollColumn(Tokens.Surface, Modifier.heightIn(max = resultMax)) {
                // 채우기 버튼이 왜 꺼졌는지 버튼 바로 위에서 (D9 '비활성 + 이유')
                if (notOfficial) {
                    IconBullet(stringResource(R.string.autofill_not_official), Icons.Outlined.GppMaybe, tone = BadgeTone.Danger)
                }
                if (ui.submitted) {
                    NoticeBanner(stringResource(R.string.autofill_submitted), icon = Icons.Outlined.TaskAlt, tone = BannerTone.Success)
                    if (fill.captured) {
                        IconBullet(stringResource(R.string.autofill_saved_capture), Icons.Outlined.CheckCircle, tone = BadgeTone.Success)
                    }
                }
                if (site.nothingVisible) IconBullet(stringResource(R.string.autofill_nothing_visible), Icons.Outlined.Info)
                report?.let { r ->
                    KoText(stringResource(R.string.autofill_result, r.filled.size, r.assist.size), MaterialTheme.typography.titleMedium, color = Tokens.Ink)
                    if (r.missing.isNotEmpty()) {
                        NoticeBanner(stringResource(R.string.autofill_failed), icon = Icons.Outlined.ReportProblem, tone = BannerTone.Danger)
                    } else {
                        KoText(stringResource(R.string.autofill_next_hint), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                    }
                    if (fill.compare.isNotEmpty()) {
                        // 입력값 대조 (PRD 5.3): 칸마다 같아요/달라요 — 색 + 아이콘 + 글자
                        Text(stringResource(R.string.autofill_compare_title), style = MaterialTheme.typography.titleSmall, color = Tokens.InkSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            fill.compare.forEach { (k, ok) ->
                                val label = labels[k] ?: return@forEach
                                StatusTag(
                                    stringResource(if (ok) R.string.autofill_compare_same else R.string.autofill_compare_diff, label),
                                    if (ok) StatusKind.Allowed else StatusKind.Caution,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (ui.submitted && !fill.captured) {
            val save = stringResource(R.string.autofill_save_capture)
            PrimaryButton(save, onClick = onSaveCapture, icon = Icons.Outlined.SaveAlt, modifier = Modifier)
        }
        val manual = stringResource(R.string.form_manual_mode)
        // 칸을 못 찾았으면 다음 할 일은 수동 모드 — 주 버튼으로 먼저 (화면당 주 버튼 하나)
        if (failed && !ui.submitted) {
            PrimaryButton(
                manual,
                onClick = onManualAfterFail,
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
            )
        } else if (failed) {
            SecondaryButton(
                manual,
                onClick = onManualAfterFail,
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
            )
        }
        val fillLabel = stringResource(if (report == null) R.string.autofill_fill_now else R.string.autofill_refill)
        if (ui.submitted || failed) {
            // 제출 뒤에는 확인 화면 저장이, 칸을 못 찾았으면 수동 모드가 주 버튼 (화면당 주 버튼 하나)
            SecondaryButton(
                fillLabel,
                onClick = onFill,
                icon = Icons.Outlined.EditNote,
                enabled = site.official,
            )
        } else {
            PrimaryButton(
                fillLabel,
                onClick = onFill,
                enabled = site.official,
                icon = Icons.Outlined.EditNote,
            )
        }
    }
}

/** 지금 보이는 WebView 화면을 PNG로 (메모리에서만, 파일로 남기지 않음) */
private fun captureWebView(wv: WebView): ByteArray {
    val bmp = createBitmap(wv.width.coerceAtLeast(1), wv.height.coerceAtLeast(1))
    wv.draw(android.graphics.Canvas(bmp))
    return java.io.ByteArrayOutputStream().use { out ->
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        out.toByteArray()
    }
}

/**
 * 공식 사이트 WebView.
 * - 자바스크립트 브리지(addJavascriptInterface)를 두지 않는다: 사이트 스크립트가 앱·보관함에 닿을 길이 없다
 * - 공식 호스트 밖으로 가는 이동은 막고 바깥 브라우저로 연다
 * - 파일·콘텐츠 접근, 혼합 콘텐츠, 새 창 금지
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OfficialSiteWebView(
    startUrl: String,
    patterns: List<String>,
    engine: String,
    onCreated: (WebView) -> Unit,
    onUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true // 공식 사이트(Angular)와 보안 확인이 자바스크립트를 쓴다
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.safeBrowsingEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        val url = request.url.toString()
                        if (UrlPolicy.allowedNavigation(url, patterns)) return false
                        if (request.url.scheme == "https") {
                            runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        }
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        onUrl(url)
                        if (UrlPolicy.matches(url, patterns)) view.evaluateJavascript(engine, null)
                    }

                    // 해시(#/...) 이동처럼 페이지를 다시 읽지 않는 이동도 잡는다
                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        onUrl(url)
                    }
                }
                onCreated(this)
                loadUrl(startUrl)
            }
        },
    )
}
