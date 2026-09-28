package com.readyport.ui.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.readyport.R
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.TopicCard
import com.readyport.ui.theme.Tokens

// M1: 탭별 정보 구조(PRD 4.1)만 잡는다. 각 기능은 ROADMAP 마일스톤에서 채운다.

@Composable
fun PrepareScreen() {
    AppScreen(
        title = stringResource(R.string.prepare_title),
        subtitle = stringResource(R.string.prepare_subtitle),
        speech = stringResource(R.string.prepare_speech),
    ) {
        // 정부 비제휴 고지는 '입국 준비' 탭 맨 위에 둔다 (PRD 8.1)
        item(key = "disclaimer") {
            TopicCard(title = stringResource(R.string.prepare_disclaimer), body = null, tone = CardTone.Notice)
        }
        item(key = "forms") {
            TopicCard(stringResource(R.string.prepare_forms_title), stringResource(R.string.prepare_forms_body), comingSoon = true)
        }
        item(key = "items") {
            TopicCard(stringResource(R.string.prepare_items_title), stringResource(R.string.prepare_items_body), comingSoon = true)
        }
        item(key = "apps") {
            TopicCard(stringResource(R.string.prepare_apps_title), stringResource(R.string.prepare_apps_body), comingSoon = true)
        }
        item(key = "bookings") {
            TopicCard(stringResource(R.string.prepare_bookings_title), stringResource(R.string.prepare_bookings_body), comingSoon = true)
        }
    }
}

@Composable
fun ExploreScreen() {
    AppScreen(
        title = stringResource(R.string.explore_title),
        subtitle = stringResource(R.string.explore_subtitle),
        speech = stringResource(R.string.explore_speech),
    ) {
        item(key = "popular") {
            TopicCard(stringResource(R.string.explore_popular_title), stringResource(R.string.explore_popular_body), comingSoon = true)
        }
        item(key = "saved") {
            TopicCard(stringResource(R.string.explore_saved_title), stringResource(R.string.explore_saved_body), comingSoon = true)
        }
    }
}

@Composable
fun WalletScreen() {
    AppScreen(
        title = stringResource(R.string.wallet_title),
        subtitle = stringResource(R.string.wallet_subtitle),
        speech = stringResource(R.string.wallet_speech),
    ) {
        item(key = "privacy") {
            TopicCard(title = stringResource(R.string.wallet_privacy), body = null, tone = CardTone.Notice)
        }
        item(key = "passport") {
            TopicCard(stringResource(R.string.wallet_passport_title), stringResource(R.string.wallet_passport_body), comingSoon = true)
        }
        item(key = "profile") {
            TopicCard(stringResource(R.string.wallet_profile_title), null, comingSoon = true)
        }
        item(key = "companions") {
            TopicCard(stringResource(R.string.wallet_companions_title), null, comingSoon = true)
        }
        item(key = "documents") {
            TopicCard(stringResource(R.string.wallet_documents_title), null, comingSoon = true)
        }
    }
}

@Composable
fun HelpScreen() {
    AppScreen(
        title = stringResource(R.string.help_title),
        speech = stringResource(R.string.help_speech),
        headerActions = {
            StatusChip(stringResource(R.string.help_offline_badge), container = Tokens.SuccessBg, content = Tokens.SuccessText)
        },
    ) {
        item(key = "phrases") {
            TopicCard(stringResource(R.string.help_phrases_title), stringResource(R.string.help_phrases_body), comingSoon = true)
        }
        item(key = "emergency") {
            InfoCard(tone = CardTone.Caution) {
                Text(stringResource(R.string.help_emergency_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.help_emergency_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
