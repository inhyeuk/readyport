package com.readyport.ui.pack

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.readyport.R
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.TopicCard

/**
 * '순위는 이렇게 정해요' (PRD 5.6·11.1).
 * 공공 여객 통계 키를 받기 전에는 순위를 만들지 않는다 — 지어낸 순위를 보여 주지 않기 위해서.
 */
@Composable
fun RankingsInfoScreen() {
    AppScreen(
        title = stringResource(R.string.rankings_info_title),
        speech = stringResource(R.string.rankings_info_speech),
    ) {
        item(key = "status") { TopicCard(stringResource(R.string.rankings_not_yet), null, tone = CardTone.Notice) }
        item(key = "weights") {
            InfoCard {
                Text(stringResource(R.string.rankings_weights_title), style = MaterialTheme.typography.titleMedium)
                listOf(R.string.rankings_weight_air, R.string.rankings_weight_search, R.string.rankings_weight_fav).forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item(key = "sources") {
            InfoCard {
                Text(stringResource(R.string.rankings_sources_title), style = MaterialTheme.typography.titleMedium)
                listOf(R.string.rankings_source_icn, R.string.rankings_source_kac, R.string.rankings_source_datalab).forEach {
                    Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item(key = "rules") {
            InfoCard {
                Text(stringResource(R.string.rankings_rules_title), style = MaterialTheme.typography.titleMedium)
                listOf(R.string.rankings_rule_basis, R.string.rankings_rule_offline, R.string.rankings_rule_no_ads).forEach {
                    Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
