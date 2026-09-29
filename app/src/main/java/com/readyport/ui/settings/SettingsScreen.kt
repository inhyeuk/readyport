package com.readyport.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.readyport.BuildConfig
import com.readyport.R
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.TopicCard

@Composable
fun SettingsScreen(
    easyMode: Boolean,
    onEasyModeChange: (Boolean) -> Unit,
    childMode: Boolean = false,
    onChildModeChange: (Boolean) -> Unit = {},
    onOpenFamily: () -> Unit = {},
    onOpenPrivacy: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val openPrivacy = onOpenPrivacy ?: { url: String -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
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
        item(key = "family") {
            InfoCard {
                Text(stringResource(R.string.settings_family_mode), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_family_mode_desc), style = MaterialTheme.typography.bodyMedium)
                com.readyport.ui.components.PrimaryButton(stringResource(R.string.companions_title), onClick = onOpenFamily)
            }
        }
        item(key = "child") {
            InfoCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.toggleable(value = childMode, role = Role.Switch, onValueChange = onChildModeChange),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.settings_child_mode), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_child_mode_desc), style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = childMode, onCheckedChange = null)
                }
            }
        }
        item(key = "packs") { TopicCard(stringResource(R.string.settings_offline_packs), null, comingSoon = true) }
        item(key = "notifications") { TopicCard(stringResource(R.string.settings_notifications), null, comingSoon = true) }
        item(key = "privacy") {
            InfoCard(tone = CardTone.Notice) {
                Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium)
                // Play 정책: 개인정보처리방침은 스토어와 앱 안 모두에서 볼 수 있어야 한다
                OutlinedButton(onClick = { openPrivacy(PRIVACY_URL) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.settings_privacy_open))
                }
            }
        }
        item(key = "disclaimer") {
            TopicCard(stringResource(R.string.settings_disclaimer), stringResource(R.string.settings_disclaimer_body), tone = CardTone.Notice)
        }
        item(key = "report") { TopicCard(stringResource(R.string.settings_report), null, comingSoon = true) }
        item(key = "about") {
            TopicCard(stringResource(R.string.settings_about), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
        }
    }
}

/** 개인정보 처리방침 (Firebase Hosting, readyport-app) */
const val PRIVACY_URL = "https://readyport-app.web.app/privacy/"
