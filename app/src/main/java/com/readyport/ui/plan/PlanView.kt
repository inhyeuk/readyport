package com.readyport.ui.plan

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.R
import com.readyport.attractions.AttractionsRepository
import com.readyport.plan.PlanError
import com.readyport.plan.PlanItem
import com.readyport.plan.PlanPdfRenderer
import com.readyport.plan.PlanPdfText
import com.readyport.plan.PlanRepository
import com.readyport.plan.PlanResult
import com.readyport.plan.PlanRules
import com.readyport.plan.TimeHint
import com.readyport.ui.board.BoardEmpty
import com.readyport.ui.board.ErrorLine
import com.readyport.ui.board.boardCountryName
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.DotBullet
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LinkRow
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.nav.PlanViewRoute
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

// ======================= 받은 계획 보기 (AI 생성 표시 · PDF는 휴대폰 안에서) =======================

@Immutable
data class PlanViewUi(
    val loading: Boolean = true,
    val offline: Boolean = false,
    val result: PlanResult? = null,
    /** 관광지 id → 이름 (이 휴대폰의 관광지 파일에 있는 곳만 — 있으면 '안내 보기' 링크) */
    val places: Map<String, String> = emptyMap(),
    val message: Int? = null,
    val messageIsError: Boolean = false,
)

data class PlanViewActions(
    val openPlace: (country: String, id: String) -> Unit = { _, _ -> },
    val savePdf: () -> Unit = {},
    val sharePdf: () -> Unit = {},
    val openMine: () -> Unit = {},
)

@HiltViewModel
class PlanViewViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val plans: PlanRepository,
    private val attractions: AttractionsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val id = handle.toRoute<PlanViewRoute>().requestId
    private val _ui = MutableStateFlow(PlanViewUi())
    val ui: StateFlow<PlanViewUi> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val result = plans.result(id)
                val places = result?.let { r ->
                    val catalog = runCatching { attractions.catalog(r.country) }.getOrNull()
                    r.days.flatMap { it.items }.mapNotNull { it.placeId }.distinct()
                        .mapNotNull { pid -> catalog?.attraction(pid)?.let { pid to it.title } }.toMap()
                }.orEmpty()
                _ui.update { it.copy(loading = false, offline = false, result = result, places = places) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, offline = e is PlanError.Offline) }
            }
        }
    }

    private suspend fun pdf(text: PlanPdfText): ByteArray? {
        val r = _ui.value.result ?: return null
        return withContext(Dispatchers.Default) { runCatching { PlanPdfRenderer.render(context, r, text) }.getOrNull() }
    }

    private fun say(res: Int, error: Boolean) = _ui.update { it.copy(message = res, messageIsError = error) }

    /** 고른 곳(ACTION_CREATE_DOCUMENT)에 저장 — 앱이 정한 서버로는 아무것도 보내지 않는다 */
    fun writePdf(uri: Uri, text: PlanPdfText) {
        viewModelScope.launch {
            val bytes = pdf(text)
            val ok = bytes != null && withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null }.getOrDefault(false)
            }
            say(if (ok) R.string.plan_pdf_saved else R.string.plan_pdf_failed, !ok)
        }
    }

    /** 시스템 공유: 캐시(share/)에 잠깐 두었다가 2분 뒤 지운다(다른 폰으로 보내기와 같은 방식) */
    fun sharePdf(text: PlanPdfText, fileName: String, launch: (Intent) -> Unit) {
        viewModelScope.launch {
            val bytes = pdf(text) ?: return@launch say(R.string.plan_pdf_failed, true)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, fileName)
            withContext(Dispatchers.IO) { f.writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
            launch(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    null,
                ),
            )
            delay(120_000)
            f.delete()
        }
    }
}

/** PDF 파일 이름에 쓸 수 없는 글자를 뺀다 */
internal fun safeFileName(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|]"), "_")

/** 화면 문구로 PDF 글을 만든다 (문구는 strings_plan.xml) */
@Composable
fun planPdfText(r: PlanResult, places: Map<String, String>): PlanPdfText {
    val country = if (r.country in PlanRules.COUNTRIES) boardCountryName(r.country) else r.country
    val hints = TimeHint.entries.associateWith { stringResource(it.labelRes()) }
    val dayLabels = r.days.associate { it.day to stringResource(R.string.plan_day, it.day) }
    val made = r.createdAt?.let { shortDate(it) }.orEmpty()
    return PlanPdfText(
        title = stringResource(R.string.plan_pdf_title, country, r.days.size),
        subtitle = stringResource(R.string.plan_pdf_subtitle, made).trim(),
        notice = (r.noticeKo ?: stringResource(R.string.plan_view_notice_default)) + " " + stringResource(R.string.plan_view_not_verified),
        dayTitle = { d -> listOfNotNull(dayLabels[d.day], d.title.takeIf { it.isNotBlank() }).joinToString(" · ") },
        timeHint = { h -> h?.let { hints[it] } },
        placeName = { places[it] },
        tipsTitle = stringResource(R.string.plan_tips),
        budgetTitle = stringResource(R.string.plan_budget_notes),
        caveatsTitle = stringResource(R.string.plan_caveats),
        footer = stringResource(R.string.plan_pdf_footer),
    )
}

@Composable
fun PlanViewScreen(openPlace: (String, String) -> Unit, openMine: () -> Unit, viewModel: PlanViewViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val r = ui.result
    val text = r?.let { planPdfText(it, ui.places) }
    val fileName = r?.let { safeFileName(stringResource(R.string.plan_pdf_filename, boardCountryNameOrCode(it.country), it.days.size)) } ?: "plan.pdf"
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && text != null) viewModel.writePdf(uri, text)
    }
    PlanViewContent(
        ui,
        PlanViewActions(
            openPlace = openPlace,
            savePdf = { if (text != null) runCatching { create.launch(fileName) } },
            sharePdf = { if (text != null) viewModel.sharePdf(text, fileName) { runCatching { context.startActivity(it) } } },
            openMine = openMine,
        ),
    )
}

@Composable
private fun boardCountryNameOrCode(code: String): String = if (code in PlanRules.COUNTRIES) boardCountryName(code) else code

/**
 * 받은 계획 (상태 없는 Content): 머리(나라 · n일 일정) → AI가 만든 계획 표시 + 고지 → 날마다 카드(때 · 할 일 · 설명 · 관광지 안내 링크) →
 * 알아 두면 좋아요 · 예산 · 꼭 확인할 것 → PDF로 저장(주 버튼) · PDF 보내기 → 휴대폰 안에서 만든다는 한 줄.
 */
@Composable
fun PlanViewContent(ui: PlanViewUi, actions: PlanViewActions = PlanViewActions()) {
    val r = ui.result
    val dimens = LocalDimens.current
    if (r == null) {
        AppScreen(title = stringResource(R.string.plan_list_title), speech = stringResource(R.string.plan_view_speech)) {
            if (ui.loading) {
                item(key = "loading") { KoText(stringResource(R.string.plan_loading), MaterialTheme.typography.bodyLarge, color = Tokens.InkSecondary) }
            } else {
                item(key = "missing") {
                    BoardEmpty(
                        stringResource(if (ui.offline) R.string.plan_list_offline else R.string.plan_view_missing_title),
                        if (ui.offline) null else stringResource(R.string.plan_view_missing_body),
                    ) {
                        SecondaryButton(stringResource(R.string.plan_view_mine), onClick = actions.openMine, icon = Icons.AutoMirrored.Outlined.EventNote, fillWidth = false)
                    }
                }
            }
        }
        return
    }
    val country = boardCountryNameOrCode(r.country)
    AppScreen(
        title = stringResource(R.string.plan_view_title, country),
        subtitle = stringResource(R.string.plan_view_days, r.days.size),
        speech = stringResource(R.string.plan_view_speech),
        icon = Icons.Outlined.AutoAwesome,
    ) {
        item(key = "ai") {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                StatusTag(stringResource(R.string.plan_ai_label), StatusKind.Info, icon = Icons.Outlined.AutoAwesome)
                NoticeBanner(
                    r.noticeKo ?: stringResource(R.string.plan_view_notice_default),
                    icon = Icons.Outlined.ReportProblem,
                    tone = BannerTone.Caution,
                    secondLine = stringResource(R.string.plan_view_not_verified),
                    secondIcon = Icons.Outlined.Policy,
                )
            }
        }
        r.days.forEach { day ->
            item(key = "day-${day.day}") {
                CardNewsCard(
                    title = day.title.ifBlank { stringResource(R.string.plan_day, day.day) },
                    icon = Icons.Outlined.CalendarMonth,
                    eyebrow = stringResource(R.string.plan_day, day.day),
                    tone = BadgeTone.Accent,
                ) {
                    Column {
                        day.items.forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(thickness = 1.dp, color = Tokens.Line)
                            PlanItemRow(item, ui.places, onOpen = { id -> actions.openPlace(r.country, id) })
                        }
                    }
                }
            }
        }
        listOf(
            Triple(R.string.plan_tips, r.tips, Icons.Outlined.Lightbulb),
            Triple(R.string.plan_budget_notes, r.budgetNotes, Icons.Outlined.Payments),
            Triple(R.string.plan_caveats, r.caveats, Icons.Outlined.ReportProblem),
        ).filter { it.second.isNotEmpty() }.forEach { (title, lines, icon) ->
            item(key = "sec-$title") {
                CardNewsCard(title = stringResource(title), icon = icon, tone = if (title == R.string.plan_caveats) BadgeTone.Caution else BadgeTone.Teal) {
                    lines.forEach { DotBullet(it) }
                }
            }
        }
        item(key = "pdf") {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                PrimaryButton(stringResource(R.string.plan_pdf_save), onClick = actions.savePdf, icon = Icons.Outlined.PictureAsPdf)
                SecondaryButton(stringResource(R.string.plan_pdf_share), onClick = actions.sharePdf, icon = Icons.Outlined.Share)
                ui.message?.let { m ->
                    if (ui.messageIsError) ErrorLine(stringResource(m)) else KoText(stringResource(m), MaterialTheme.typography.bodyMedium, color = Tokens.SuccessText)
                }
                IconBullet(stringResource(R.string.plan_pdf_note), Icons.Outlined.Lock)
            }
        }
    }
}

/** 할 일 한 줄: [때] 할 일 → 설명 → (이 휴대폰 관광지 파일에 있으면) `센소지 안내 보기` */
@Composable
private fun PlanItemRow(item: PlanItem, places: Map<String, String>, onOpen: (String) -> Unit) {
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item.timeHint?.let { StatusTag(stringResource(it.labelRes()), StatusKind.Soon) }
        KoText(item.title, MaterialTheme.typography.titleMedium, color = Tokens.Ink)
        if (item.note.isNotBlank()) KoText(item.note, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        val place = item.placeId?.let { id -> places[id]?.let { id to it } }
        place?.let { (id, name) -> LinkRow(stringResource(R.string.plan_place_open, name), { onOpen(id) }) }
    }
}
