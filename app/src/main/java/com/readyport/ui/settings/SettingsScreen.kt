package com.readyport.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.readyport.BuildConfig
import com.readyport.R
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PhotoCredit
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.TopicCard
import com.readyport.ui.components.loadPhotoCredits
import com.readyport.ui.theme.LocalDimens

@Composable
fun SettingsScreen(
    easyMode: Boolean,
    onEasyModeChange: (Boolean) -> Unit,
    childMode: Boolean = false,
    onChildModeChange: (Boolean) -> Unit = {},
    wifiOnly: Boolean = true,
    onWifiOnlyChange: (Boolean) -> Unit = {},
    onOpenMyInfo: () -> Unit = {},
    onOpenFamily: () -> Unit = {},
    onOpenPhotos: () -> Unit = {},
    onOpenPrivacy: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val openPrivacy = onOpenPrivacy ?: { url: String -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    AppScreen(
        title = stringResource(R.string.settings_title),
        speech = stringResource(R.string.settings_speech),
    ) {
        // 개인정보가 기기 밖으로 나가지 않는다는 약속을 설정 맨 위에 항상 보여 준다
        item(key = "local-only") { LocalOnlyBanner() }
        item(key = "my-info") {
            InfoCard {
                Text(stringResource(R.string.settings_myinfo_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.settings_myinfo_body), style = MaterialTheme.typography.bodyLarge)
                PrimaryButton(stringResource(R.string.settings_myinfo_open), onClick = onOpenMyInfo)
                Text(stringResource(R.string.settings_family_mode_desc), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = onOpenFamily,
                    modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight),
                ) { Text(stringResource(R.string.companions_title), style = MaterialTheme.typography.labelLarge) }
            }
        }
        item(key = "easy") {
            SwitchCard(stringResource(R.string.settings_easy_mode), stringResource(R.string.settings_easy_mode_desc), easyMode, onEasyModeChange)
        }
        item(key = "wifi") {
            SwitchCard(stringResource(R.string.explore_wifi_only), stringResource(R.string.explore_wifi_only_desc), wifiOnly, onWifiOnlyChange)
        }
        item(key = "child") {
            SwitchCard(stringResource(R.string.settings_child_mode), stringResource(R.string.settings_child_mode_desc), childMode, onChildModeChange)
        }
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
        item(key = "photos") {
            InfoCard {
                Text(stringResource(R.string.settings_photos), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_photos_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onOpenPhotos, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.settings_photos_open))
                }
            }
        }
        item(key = "about") {
            TopicCard(stringResource(R.string.settings_about), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
        }
    }
}

/** "내 정보는 이 휴대폰에만 저장돼요" — 설정과 내 정보 화면 맨 위 */
@Composable
fun LocalOnlyBanner() {
    InfoCard(tone = CardTone.Navy) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.settings_local_only_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Text(stringResource(R.string.settings_local_only_body), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchCard(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    InfoCard {
        // 줄 전체가 하나의 스위치로 읽히고 눌리도록 toggleable을 행에 건다
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** 설정 › 사진 출처: 자유 라이선스 사진의 찍은 사람·라이선스·원본 (CC BY-SA 출처 표기) */
@Composable
fun PhotoCreditsScreen(onOpenLink: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val credits = remember { loadPhotoCredits(context) }
    val open: (String) -> Unit = onOpenLink ?: { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    PhotoCreditsContent(credits, open)
}

@Composable
fun PhotoCreditsContent(credits: List<PhotoCredit>, onOpenLink: (String) -> Unit) {
    AppScreen(
        title = stringResource(R.string.photo_credits_title),
        subtitle = stringResource(R.string.photo_credits_body),
        speech = stringResource(R.string.photo_credits_speech),
    ) {
        credits.forEach { c ->
            item(key = "credit-${c.id}") {
                InfoCard {
                    Text(c.title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.photo_credit_author, c.author), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.photo_credit_license, c.license), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { onOpenLink(c.sourceUrl) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.photo_credit_open))
                    }
                }
            }
        }
    }
}

/** 개인정보 처리방침 (Firebase Hosting, readyport-app) */
const val PRIVACY_URL = "https://readyport-app.web.app/privacy/"
