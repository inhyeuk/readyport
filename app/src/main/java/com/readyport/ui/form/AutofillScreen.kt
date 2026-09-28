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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.autofill.UrlPolicy
import com.readyport.security.SecureScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.StatusChip
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    if (recipe == null || engine == null) return
    if (ui.locked) {
        Column(Modifier.fillMaxSize().padding(LocalDimens.current.screenPadding), verticalArrangement = Arrangement.Center) {
            InfoCard {
                Text(stringResource(R.string.wallet_locked_title), style = MaterialTheme.typography.titleMedium)
                PrimaryButton(stringResource(R.string.wallet_unlock), onClick = { auth { viewModel.unlock() } })
            }
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

    Column(Modifier.fillMaxSize().background(Tokens.Ground)) {
        // 상단: 공식 사이트 표시 (PRD 5.3)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().background(if (official) Tokens.SuccessBg else Tokens.DangerBg).padding(12.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = if (official) Tokens.SuccessText else Tokens.DangerText, modifier = Modifier.size(20.dp))
            Text(
                if (official) stringResource(R.string.autofill_connected, currentUrl?.toUri()?.host.orEmpty())
                else stringResource(R.string.autofill_not_official),
                color = if (official) Tokens.SuccessText else Tokens.DangerText,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.report("-", "manual_mode_chosen"); onManual() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.form_manual_mode))
            }
        }
        // 단계 표시
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            recipe.steps.forEachIndexed { i, s ->
                val on = visibleSteps.getOrNull(i) == true
                StatusChip(s.titleKo, if (on) Tokens.Accent else Tokens.AccentSoft, if (on) Tokens.Surface else Tokens.Ink)
            }
        }
        // 사람이 할 곳 안내 (주황 배너)
        Text(
            stringResource(R.string.autofill_human_banner),
            color = Tokens.CautionText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().background(Tokens.CautionBg).padding(horizontal = 12.dp, vertical = 8.dp),
        )
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
        // 하단: 결과·버튼
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().background(Tokens.Surface).padding(12.dp),
        ) {
            if (ui.submitted) {
                InfoCard(tone = CardTone.Accent) {
                    Text(stringResource(R.string.autofill_submitted), style = MaterialTheme.typography.bodyLarge)
                    if (captured) {
                        Text(stringResource(R.string.autofill_saved_capture), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        PrimaryButton(stringResource(R.string.autofill_save_capture), onClick = {
                            val wv = webView ?: return@PrimaryButton
                            val png = captureWebView(wv)
                            scope.launch { captured = viewModel.saveCapture(png) }
                        })
                    }
                }
            }
            if (nothingVisible) Text(stringResource(R.string.autofill_nothing_visible), style = MaterialTheme.typography.bodyMedium)
            report?.let { r ->
                Text(stringResource(R.string.autofill_result, r.filled.size, r.assist.size), style = MaterialTheme.typography.titleMedium)
                if (r.missing.isNotEmpty()) {
                    Text(stringResource(R.string.autofill_failed), color = Tokens.DangerText, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.autofill_next_hint), style = MaterialTheme.typography.bodySmall)
                }
                if (compare.isNotEmpty()) {
                    val labels = recipe.fields.associate { it.key to it.labels.ko }
                    Text(
                        stringResource(R.string.autofill_compare_title) + ": " + compare.entries.joinToString(" · ") { (k, ok) ->
                            "${labels[k] ?: k} " + if (ok) "✓" else "✗"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    stringResource(if (report == null) R.string.autofill_fill_now else R.string.autofill_refill),
                    onClick = ::fillNow,
                    enabled = official,
                    modifier = Modifier.weight(1f),
                )
                if (report?.missing?.isNotEmpty() == true) {
                    OutlinedButton(onClick = onManual, modifier = Modifier.heightIn(min = LocalDimens.current.buttonHeight)) {
                        Text(stringResource(R.string.form_manual_mode))
                    }
                }
            }
        }
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
