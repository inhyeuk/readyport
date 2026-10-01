package com.readyport.ui.form

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AirplaneTicket
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.autofill.FieldValue
import com.readyport.autofill.RecipeField
import com.readyport.autofill.RecipeStep
import com.readyport.security.SecureScreen
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LockedState
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SecurityBanner
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.largeFont
import com.readyport.ui.components.minTouch
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.wallet.rememberDeviceAuth

/** 수동 모드 (PRD 6.6): 자동 입력이 안 될 때 값 복사 + 공식 사이트 */
@Composable
fun ManualModeScreen(viewModel: AutofillViewModel = hiltViewModel()) {
    SecureScreen()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val auth = rememberDeviceAuth()
    val url = ui.context?.recipe?.startUrl ?: ui.context?.form?.officialUrl
    ManualModeContent(
        ui = ui,
        onUnlock = { auth { viewModel.unlock() } },
        onCopy = { label, text -> viewModel.clipboard.copy(label, text) },
        onOpenSite = {
            url?.takeIf { it.startsWith("https://") }?.let { context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri())) }
        },
    )
}

/**
 * 수동 모드 (DESIGN_SPEC 6-17 공통 치환표): 정부 비제휴(첫 항목) → 보안 한 줄 → 공식 사이트 열기 → 직접 할 일(주의) →
 * 사이트 단계마다 카드뉴스 카드(`1단계` + 단계 이름) 안에 ① 앱이 가진 값(칸 이름·값·복사) ② `사이트에서 직접 적을 칸`(값 없는 칸, 필수 표시)
 * → 맨 아래에 공식 사이트 열기를 한 번 더(긴 목록을 다 본 자리에서 바로 갈 수 있게).
 */
@Composable
fun ManualModeContent(
    ui: AutofillUi,
    onUnlock: () -> Unit,
    onCopy: (label: String, text: String) -> Unit,
    onOpenSite: () -> Unit,
) {
    val recipe = ui.context?.recipe
    var copied by remember { mutableStateOf<String?>(null) }
    // 보통 글자 크기면 복사 버튼을 값 옆에, 글자를 크게 키웠으면 값 아래 줄에 (버튼이 값 글자를 음절 단위로 쪼개지 않게)
    val roomy = !largeFont()
    val openLabel = stringResource(R.string.manual_open_site)
    AppScreen(
        title = stringResource(R.string.manual_title),
        subtitle = stringResource(R.string.manual_body),
        speech = stringResource(R.string.manual_body),
    ) {
        item(key = "not-affiliated") {
            NoticeBanner(stringResource(R.string.guide_not_affiliated), icon = Icons.Outlined.Policy)
        }
        item(key = "security") { SecurityBanner(compact = true) }
        item(key = "open") {
            PrimaryButton(
                openLabel,
                onClick = onOpenSite,
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
            )
        }
        item(key = "human") {
            NoticeBanner(stringResource(R.string.autofill_human_banner), icon = Icons.Outlined.TouchApp, tone = BannerTone.Caution)
        }
        if (ui.locked) {
            item(key = "locked") {
                LockedState(
                    title = stringResource(R.string.wallet_locked_title),
                    body = stringResource(R.string.form_locked_body),
                    buttonLabel = stringResource(R.string.wallet_unlock),
                    onUnlock = onUnlock,
                )
            }
            return@AppScreen
        }
        val steps = recipe?.steps.orEmpty()
        steps.forEachIndexed { i, step ->
            item(key = "step-${step.id}") {
                StepCard(i, step, ui.values, copied, roomy) { f, text ->
                    onCopy(f.labels.en, text)
                    copied = f.key
                }
            }
        }
        if (steps.isNotEmpty()) {
            // 긴 목록 끝에서도 바로 사이트로 (주 버튼은 맨 위 하나 — 여기는 보조)
            item(key = "open-bottom") {
                SecondaryButton(
                    openLabel,
                    onClick = onOpenSite,
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                )
            }
        }
    }
}

/** 사이트 단계 ID → 아이콘 (앱 쪽 매핑, D11). 모르는 ID는 EditNote */
private fun stepIcon(id: String): ImageVector = when (id) {
    "personal" -> Icons.Outlined.Badge
    "trip" -> Icons.AutoMirrored.Outlined.AirplaneTicket
    "health" -> Icons.Outlined.HealthAndSafety
    else -> Icons.Outlined.EditNote
}

@Composable
private fun StepCard(
    index: Int,
    step: RecipeStep,
    values: Map<String, FieldValue>,
    copied: String?,
    roomy: Boolean,
    onCopy: (RecipeField, String) -> Unit,
) {
    fun textOf(f: RecipeField): String = values[f.key].let { v -> (v?.value ?: v?.display).orEmpty() }
    val (filled, empty) = step.fields.partition { textOf(it).isNotEmpty() }
    CardNewsCard(
        title = step.titleKo,
        icon = stepIcon(step.id),
        eyebrow = stringResource(R.string.manual_step_eyebrow, index + 1),
    ) {
        step.noteKo?.let { IconBullet(it, Icons.Outlined.Info) }
        filled.forEachIndexed { j, f ->
            if (j > 0 || step.noteKo != null) HorizontalDivider(thickness = 1.dp, color = Tokens.LineSoft)
            CopyRow(f, textOf(f), copied == f.key, roomy) { onCopy(f, it) }
        }
        if (empty.isNotEmpty()) {
            if (filled.isNotEmpty() || step.noteKo != null) HorizontalDivider(thickness = 1.dp, color = Tokens.LineSoft)
            OnSiteHeader()
            empty.forEach { f -> OnSiteField(f) }
        }
    }
}

/** 칸 이름(한국어·영어): 한 줄에 들어가면 나란히, 아니면 다음 줄로 */
@Composable
private fun FieldNames(f: RecipeField, required: Boolean = false) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KoText(f.labels.ko, MaterialTheme.typography.titleSmall, Modifier.align(Alignment.CenterVertically), color = Tokens.InkSecondary)
        Text(f.labels.en, style = MaterialTheme.typography.bodySmall, color = Tokens.InkSecondary, modifier = Modifier.align(Alignment.CenterVertically))
        if (required) {
            StatusTag(stringResource(R.string.form_field_required), StatusKind.Caution, Modifier.align(Alignment.CenterVertically))
        }
    }
}

/** 칸 이름(한국어·영어) + 값 + 복사. TalkBack: `성 (영문) 복사` */
@Composable
private fun CopyRow(f: RecipeField, text: String, copied: Boolean, roomy: Boolean, onCopy: (String) -> Unit) {
    val texts: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FieldNames(f)
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = Tokens.Ink,
            )
            f.hintKo?.let { KoText(it, MaterialTheme.typography.bodySmall, color = Tokens.InkSecondary) }
        }
    }
    val button: @Composable () -> Unit = { CopyButton(f.labels.ko, copied) { onCopy(text) } }
    if (roomy) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { texts() }
            button()
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            texts()
            button()
        }
    }
}

/**
 * 가벼운 복사 버튼(글자 버튼 모양: 아이콘 + `복사`, 테두리·채움 없음) — 칸마다 같은 알약 버튼이 세로로 쌓여 무거워 보이지 않게.
 * 누르면 초록 Check + `복사됨`으로 바뀐다. 터치 영역 48/56(minTouch), TalkBack 이름 `성 (영문) 복사`.
 */
@Composable
private fun CopyButton(fieldName: String, copied: Boolean, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val label = stringResource(if (copied) R.string.manual_copied else R.string.manual_copy)
    val color = if (copied) Tokens.SuccessText else Tokens.Accent
    val description = "$fieldName $label"
    Row(
        Modifier
            .minTouch()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, contentDescription = null, tint = color, modifier = Modifier.size(dimens.icon))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** `사이트에서 직접 적을 칸` 소제목 — 앱에 값이 없어 사이트에서 사람이 적는 칸들 */
@Composable
private fun OnSiteHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.EditNote, contentDescription = null, tint = Tokens.CautionText, modifier = Modifier.size(LocalDimens.current.icon))
        KoText(stringResource(R.string.manual_fill_on_site), MaterialTheme.typography.titleSmall, color = Tokens.Ink, heading = true)
    }
}

/** 값 없는 칸: 칸 이름 + 필수면 `꼭 채워요`(주의 — 오류가 아니라 할 일) + 도움말 */
@Composable
private fun OnSiteField(f: RecipeField) {
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FieldNames(f, required = f.required)
        f.hintKo?.let { KoText(it, MaterialTheme.typography.bodySmall, color = Tokens.InkSecondary) }
    }
}
