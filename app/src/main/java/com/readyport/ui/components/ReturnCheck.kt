package com.readyport.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.pack.OfficialLink
import com.readyport.pack.SourcedText
import com.readyport.ui.theme.Tokens

/** 귀국 전 확인의 주제 이름(값 없음) — 출처 ID 기준 */
private fun returnTopic(source: String): Int? = when (source) {
    "customs_allowance" -> R.string.return_topic_allowance
    "apqa_plant" -> R.string.return_topic_plant
    "apqa_livestock" -> R.string.return_topic_livestock
    else -> null
}

/**
 * '귀국 전 확인 — 면세 한도·반입 금지 품목(관세청·검역본부)' v2 (DESIGN_SPEC 4.15). 홈·나라·내 여행·쇼핑이 함께 쓴다.
 * 노란 카드 전체 채움 대신 흰 카드뉴스 카드: 안내 한 줄 → 사실 행(아이콘, **팩 원문 그대로** — 값 하드코딩 없음, D11)
 * → 출처(날짜 같은 출처는 한 줄) → 공식 링크 행.
 * [compact](홈): 사실 행은 '자세히 보기' 안에 접고, 접혀 있을 때만 주제 이름 요약 줄을 보인다(펼치면 숨김). 출처·링크는 접힘 밖.
 * 출처 이름을 못 찾으면 `공식 안내` — 내부 ID를 보이지 않는다.
 */
@Composable
fun ReturnCheckCard(
    links: List<OfficialLink>,
    facts: List<SourcedText>,
    sourceNames: Map<String, String>,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val fallback = stringResource(R.string.source_official_fallback)
    val refs = facts.map { SourceRef(resolveSourceName(it.source, sourceNames, fallback), displayDate(it.lastVerified)) }
    CardNewsCard(
        title = stringResource(R.string.shopping_return_title),
        icon = Icons.Outlined.Inventory2,
        modifier = modifier,
        tone = BadgeTone.Accent,
        style = NewsStyle.Surface,
    ) {
        // `관세청·농림축산검역본부`는 긴 가운뎃점 낱말이라 가운뎃점 뒤에서만 줄을 바꾼다(koDisplay)
        KoText(stringResource(R.string.shopping_return_body), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        if (compact) {
            var open by rememberSaveable { mutableStateOf(false) }
            val topics = facts.mapNotNull { f -> returnTopic(f.source)?.let { f.source to it } }.distinctBy { it.first }
            // 주제 요약 줄은 접혀 있을 때만 (4.15) — 펼치면 같은 주제의 문장이 아래에 다 보인다
            if (topics.isNotEmpty()) {
                AnimatedVisibility(visible = !open) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        topics.forEach { (source, label) ->
                            val (icon, tone) = IconKeys.returnFact(source)
                            FactChip(Fact(icon = icon, value = stringResource(label), label = "", tone = tone))
                        }
                    }
                }
            }
            if (facts.isNotEmpty()) {
                ExpandableDetail(open = open, onOpenChange = { open = it }, label = stringResource(R.string.return_check_more)) {
                    ReturnFacts(facts)
                }
            }
        } else {
            ReturnFacts(facts)
        }
        if (refs.isNotEmpty()) Column(Modifier.padding(top = 4.dp)) { SourceList(refs) }
        links.forEach { link -> LinkRow(link.labelKo, onClick = { onOpenLink(link.url) }) }
    }
}

@Composable
private fun ReturnFacts(facts: List<SourcedText>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.forEach { f ->
            val (icon, tone) = IconKeys.returnFact(f.source)
            IconBullet(f.textKo, icon, tone = tone)
        }
    }
}
