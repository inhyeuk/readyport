package com.readyport.ui.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.readyport.R
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.TopicCard

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
