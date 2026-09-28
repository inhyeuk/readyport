package com.readyport.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readyport.R
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.TopicCard
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/** 여행 단계 표시줄 6칸 (PRD 5.1). 여행 단계 엔진은 M6에서 붙인다. */
private val StageLabels = listOf(
    R.string.stage_prepare, R.string.stage_departure, R.string.stage_arrival,
    R.string.stage_traveling, R.string.stage_return, R.string.stage_wrapup,
)

@Composable
fun TodayScreen(
    onOpenSettings: () -> Unit,
    onPickDestination: () -> Unit,
) {
    AppScreen(
        title = stringResource(R.string.today_title),
        subtitle = stringResource(R.string.today_no_trip),
        speech = stringResource(R.string.today_speech),
        headerActions = {
            IconButton(onClick = onOpenSettings, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                    modifier = Modifier.size(if (LocalDimens.current.easyMode) 32.dp else 24.dp),
                )
            }
        },
    ) {
        item(key = "stages") { StageBar(current = 0) }
        item(key = "next") {
            InfoCard(tone = CardTone.Accent) {
                Text(stringResource(R.string.today_next_label), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.today_next_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.today_next_body), style = MaterialTheme.typography.bodyMedium)
                PrimaryButton(
                    text = stringResource(R.string.today_next_button),
                    onClick = onPickDestination,
                    colors = ButtonDefaults.buttonColors(containerColor = Tokens.Surface, contentColor = Tokens.Accent),
                )
            }
        }
        item(key = "help") {
            TopicCard(
                title = stringResource(R.string.today_help_title),
                body = stringResource(R.string.today_help_body),
            )
        }
    }
}

@Composable
private fun StageBar(current: Int) {
    val labels = StageLabels.map { stringResource(it) }
    val description = stringResource(R.string.today_stage_desc, labels[current], current + 1, labels.size)
    // TalkBack에는 칸 6개 대신 한 문장으로 읽어 준다
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, _ ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(
                            color = if (index <= current) Tokens.Accent else Tokens.Line,
                            shape = MaterialTheme.shapes.small,
                        ),
                )
            }
        }
        if (LocalDimens.current.easyMode) {
            // 쉬운 모드: 작은 글자 6개 대신 지금 단계만 한 줄로 크게
            Text(
                text = stringResource(R.string.today_stage_now, labels[current], current + 1, labels.size),
                style = MaterialTheme.typography.bodyLarge,
                color = Tokens.Accent,
            )
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                // 글자를 키워도 칸 안에 한 줄로 들어가도록 줄여 맞춘다
                val style = MaterialTheme.typography.labelSmall
                BasicText(
                    text = label,
                    style = style.copy(
                        color = if (index == current) Tokens.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}
