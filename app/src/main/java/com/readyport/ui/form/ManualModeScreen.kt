package com.readyport.ui.form

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.LockedState
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SecurityBanner
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
 * 사이트 단계마다 카드뉴스 카드(`1단계` + 단계 이름) 안에 칸 이름·값·복사 버튼.
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
    val roomy = LocalDensity.current.fontScale < LARGE_FONT_SCALE
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
            PrimaryButton(stringResource(R.string.manual_open_site), onClick = onOpenSite, icon = Icons.AutoMirrored.Outlined.OpenInNew)
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
        recipe?.steps?.forEachIndexed { i, step ->
            item(key = "step-${step.id}") {
                StepCard(i, step, ui.values, copied, roomy) { f, text ->
                    onCopy(f.labels.en, text)
                    copied = f.key
                }
            }
        }
    }
}

/** 이 글자 배율부터는 복사 버튼을 값 아래 줄로 */
private const val LARGE_FONT_SCALE = 1.3f

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
    CardNewsCard(
        title = step.titleKo,
        icon = stepIcon(step.id),
        eyebrow = stringResource(R.string.manual_step_eyebrow, index + 1),
    ) {
        step.noteKo?.let { IconBullet(it, Icons.Outlined.Info) }
        step.fields.forEachIndexed { j, f ->
            if (j > 0 || step.noteKo != null) HorizontalDivider(thickness = 1.dp, color = Tokens.LineSoft)
            val v: FieldValue? = values[f.key]
            CopyRow(f, (v?.value ?: v?.display).orEmpty(), copied == f.key, roomy) { onCopy(f, it) }
        }
    }
}

/** 칸 이름(한국어·영어) + 값 + 복사 버튼. TalkBack: `성 (영문) 복사` */
@Composable
private fun CopyRow(f: RecipeField, text: String, copied: Boolean, roomy: Boolean, onCopy: (String) -> Unit) {
    val texts: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // 한국어·영어 칸 이름: 한 줄에 들어가면 나란히, 아니면 다음 줄로
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(f.labels.ko, style = MaterialTheme.typography.titleSmall, color = Tokens.InkSecondary, modifier = Modifier.alignByBaseline())
                Text(f.labels.en, style = MaterialTheme.typography.bodySmall, color = Tokens.InkSecondary, modifier = Modifier.alignByBaseline())
            }
            Text(
                text.ifEmpty { stringResource(R.string.form_empty_value) },
                // 빈 값은 값처럼 굵게 보이지 않게 보통 굵기 + 메타 색
                style = if (text.isEmpty()) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
                },
                color = if (text.isEmpty()) Tokens.InkTertiary else Tokens.Ink,
            )
            f.hintKo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tokens.InkSecondary) }
        }
    }
    val button: @Composable () -> Unit = {
        if (text.isNotEmpty()) {
            val label = stringResource(if (copied) R.string.manual_copied else R.string.manual_copy)
            val description = "${f.labels.ko} $label"
            SecondaryButton(
                label,
                onClick = { onCopy(text) },
                icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                tone = if (copied) BadgeTone.Success else BadgeTone.Accent,
                fillWidth = false,
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
    if (roomy) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { texts() }
            button()
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            texts()
            button()
        }
    }
}
