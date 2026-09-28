package com.readyport.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.TopicCard

@Composable
fun SettingsScreen(
    easyMode: Boolean,
    onEasyModeChange: (Boolean) -> Unit,
) {
    AppScreen(
        title = stringResource(R.string.settings_title),
        speech = stringResource(R.string.settings_speech),
    ) {
        item(key = "easy") {
            InfoCard {
                // 줄 전체가 하나의 스위치로 읽히고 눌리도록 toggleable을 행에 건다
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.toggleable(
                        value = easyMode,
                        role = Role.Switch,
                        onValueChange = onEasyModeChange,
                    ),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.settings_easy_mode), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_easy_mode_desc), style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = easyMode, onCheckedChange = null)
                }
            }
        }
        item(key = "family") { TopicCard(stringResource(R.string.settings_family_mode), null, comingSoon = true) }
        item(key = "packs") { TopicCard(stringResource(R.string.settings_offline_packs), null, comingSoon = true) }
        item(key = "notifications") { TopicCard(stringResource(R.string.settings_notifications), null, comingSoon = true) }
        item(key = "privacy") {
            TopicCard(stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_body), tone = CardTone.Notice)
        }
        item(key = "disclaimer") {
            TopicCard(stringResource(R.string.settings_disclaimer), stringResource(R.string.settings_disclaimer_body), tone = CardTone.Notice)
        }
        item(key = "report") { TopicCard(stringResource(R.string.settings_report), null, comingSoon = true) }
        item(key = "about") { TopicCard(stringResource(R.string.settings_about), null, comingSoon = true) }
    }
}
