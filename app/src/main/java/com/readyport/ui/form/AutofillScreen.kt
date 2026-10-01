package com.readyport.ui.form

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.readyport.ui.components.LockedState
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SecurityBanner
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.TextCircle
import com.readyport.ui.components.startBar
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
    if (ui.locked) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Tokens.Ground)
                .verticalScroll(rememberScrollState())
                .padding(LocalDimens.current.screenPadding),
            verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
        ) {
            TopNotices()
            LockedState(
                title = stringResource(R.string.wallet_locked_title),
                body = stringResource(R.string.form_locked_body),
                buttonLabel = stringResource(R.string.wallet_unlock),
                onUnlock = { auth { viewModel.unlock() } },
            )
        }
        return
    }
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

    val onManualChosen = {
        viewModel.report("-", "manual_mode_chosen")
        onManual()
    }
    val labels = recipe.fields.associate { it.key to it.labels.ko }

    Column(Modifier.fillMaxSize().background(Tokens.Ground)) {
        // 위: 정부 비제휴(첫 항목) → 보안 한 줄 → 공식 사이트 연결 상태와 단계 (PRD 5.3, DESIGN_SPEC 6-17 공통)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TopNotices()
            SiteStatus(
                official = official,
                host = currentUrl?.toUri()?.host.orEmpty(),
                steps = recipe.steps,
                visibleSteps = visibleSteps,
                onManual = onManualChosen,
            )
        }
        // 사람이 할 곳 안내 (주의 띠) — 사이트 바로 위
        HumanStrip()
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
            modifier = Modifier.weight(1f),
        )
        HorizontalDivider(thickness = 1.dp, color = Tokens.Line)
        // 아래: 결과·버튼
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().background(Tokens.Surface).padding(12.dp),
        ) {
            if (ui.submitted) {
                NoticeBanner(stringResource(R.string.autofill_submitted), icon = Icons.Outlined.TaskAlt, tone = BannerTone.Success)
                if (captured) {
                    IconBullet(stringResource(R.string.autofill_saved_capture), Icons.Outlined.CheckCircle, tone = BadgeTone.Success)
                } else {
                    PrimaryButton(
                        stringResource(R.string.autofill_save_capture),
                        onClick = {
                            val wv = webView ?: return@PrimaryButton
                            val png = captureWebView(wv)
                            scope.launch { captured = viewModel.saveCapture(png) }
                        },
                        icon = Icons.Outlined.SaveAlt,
                    )
                }
            }
            if (nothingVisible) IconBullet(stringResource(R.string.autofill_nothing_visible), Icons.Outlined.Info)
            report?.let { r ->
                Text(stringResource(R.string.autofill_result, r.filled.size, r.assist.size), style = MaterialTheme.typography.titleMedium, color = Tokens.Ink)
                if (r.missing.isNotEmpty()) {
                    NoticeBanner(stringResource(R.string.autofill_failed), icon = Icons.Outlined.ReportProblem, tone = BannerTone.Danger)
                } else {
                    Text(stringResource(R.string.autofill_next_hint), style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                }
                if (compare.isNotEmpty()) {
                    // 입력값 대조 (PRD 5.3): 칸마다 같아요/달라요 — 색 + 아이콘 + 글자
                    Text(stringResource(R.string.autofill_compare_title), style = MaterialTheme.typography.titleSmall, color = Tokens.InkSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        compare.forEach { (k, ok) ->
                            val label = labels[k] ?: return@forEach
                            StatusTag(
                                stringResource(if (ok) R.string.autofill_compare_same else R.string.autofill_compare_diff, label),
                                if (ok) StatusKind.Allowed else StatusKind.Caution,
                            )
                        }
                    }
                }
            }
            val fillLabel = stringResource(if (report == null) R.string.autofill_fill_now else R.string.autofill_refill)
            if (ui.submitted) {
                // 제출 뒤에는 확인 화면 저장이 주 버튼 (화면당 주 버튼 하나)
                SecondaryButton(fillLabel, onClick = ::fillNow, icon = Icons.Outlined.EditNote, enabled = official)
            } else {
                PrimaryButton(fillLabel, onClick = ::fillNow, enabled = official, icon = Icons.Outlined.EditNote)
            }
            if (report?.missing?.isNotEmpty() == true) {
                SecondaryButton(stringResource(R.string.form_manual_mode), onClick = onManual, icon = Icons.AutoMirrored.Outlined.OpenInNew)
            }
        }
    }
}

/** 정부 비제휴(첫 항목) + 보안 한 줄 (입국 카드 화면 공통, DESIGN_SPEC 6장 머리말) */
@Composable
private fun TopNotices() {
    NoticeBanner(stringResource(R.string.guide_not_affiliated), icon = Icons.Outlined.Policy)
    SecurityBanner(compact = true)
}

/** 공식 사이트 연결 상태(초록 = 공식 / 빨강 = 아님, 색 + 아이콘 + 글자) + 수동 모드 + 사이트 단계 */
@Composable
private fun SiteStatus(official: Boolean, host: String, steps: List<RecipeStep>, visibleSteps: List<Boolean>, onManual: () -> Unit) {
    val dimens = LocalDimens.current
    val bg = if (official) Tokens.SuccessBg else Tokens.DangerBg
    val fg = if (official) Tokens.SuccessText else Tokens.DangerText
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().startBar(fg).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (official) Icons.Outlined.VerifiedUser else Icons.Outlined.GppMaybe,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(dimens.icon),
                )
                Text(
                    if (official) stringResource(R.string.autofill_connected, host) else stringResource(R.string.autofill_not_official),
                    color = fg,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                QuietButton(stringResource(R.string.form_manual_mode), onClick = onManual)
            }
            // 단계 표시: 지금 사이트 화면에 보이는 단계는 채운 번호 원 + 눈 아이콘 + 굵은 글자
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.forEachIndexed { i, s ->
                    val on = visibleSteps.getOrNull(i) == true
                    Row(
                        Modifier.semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TextCircle(
                            "${i + 1}",
                            minSize = 24.dp,
                            container = if (on) Tokens.Accent else Tokens.Surface,
                            content = if (on) Tokens.Surface else Tokens.InkSecondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            s.titleKo,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                            color = Tokens.Ink,
                        )
                        if (on) Icon(Icons.Outlined.Visibility, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(dimens.iconSmall))
                    }
                }
            }
        }
    }
}

/** 사람이 직접 할 곳 안내 — 사이트 바로 위의 얇은 주의 띠 (누를 수 없음) */
@Composable
private fun HumanStrip() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Tokens.CautionBg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.TouchApp,
            contentDescription = null,
            tint = Tokens.CautionText,
            modifier = Modifier.padding(top = 1.dp).size(LocalDimens.current.iconSmall + 4.dp),
        )
        Text(stringResource(R.string.autofill_human_banner), color = Tokens.CautionText, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 지금 보이는 WebView 화면을 PNG로 (메모리에서만, 파일로 남기지 않음) */
private fun captureWebView(wv: WebView): ByteArray {
    val bmp = android.graphics.Bitmap.createBitmap(wv.width.coerceAtLeast(1), wv.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
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
