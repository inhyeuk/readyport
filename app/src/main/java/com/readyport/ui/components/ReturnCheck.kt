package com.readyport.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.pack.OfficialLink
import com.readyport.pack.SourcedText
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/**
 * 귀국 전 확인 카드의 두 모양 (운영자 결정 10, 2026-10-01).
 * - [Full]: 내 여행 '귀국' 단계에만 — 안내 한 줄 → 사실 행 4개(첫 문장, 펼치면 문장 전체) → 출처 → 공식 링크 3행
 * - [Summary]: 홈·나라 쇼핑·쇼핑 리스트 — 사실 **한 줄**(첫 사실의 첫 문장) + `면세 한도·반입 금지 문장 전체 보기`.
 *   펼치면 사실 행 전체(문장 전체)·안내·공식 링크가 나온다. 출처는 접혀 있어도 늘 보인다(출처 규칙, 원칙 5).
 */
enum class ReturnCheckMode { Full, Summary }

/**
 * '귀국 전 확인 — 면세 한도·반입 금지 품목(관세청·검역본부)' (DESIGN_SPEC 4.15, 재검토 R14). 홈·나라 쇼핑·쇼핑 리스트는 [ReturnCheckMode.Summary],
 * 내 여행 귀국 단계는 [ReturnCheckMode.Full] — 부르는 쪽이 꼭 고른다.
 * 흰 카드뉴스 카드: 사실 행(주제 아이콘 + 팩 문장의 **첫 문장**, 숫자 토큰 굵게 — 채움 없는 행, 재검토 R1) → 펼침(같은 행이 팩 문장 전체로) → 출처(기관별 묶음, 접힘 밖).
 * 글은 **팩 원문 그대로** — 앱은 문장을 자르기만 하고(첫 문장 경계) 값을 만들거나 바꾸지 않는다(D11). TalkBack·테스트 글자는 원문.
 * 펼치면 바뀐 사실 행을 TalkBack이 읽는다(liveRegion — 바뀐 글이 버튼 위에 있어도, 재검토2 ②#2).
 * 출처 이름을 못 찾으면 `공식 안내` — 내부 ID를 보이지 않는다.
 */
@Composable
fun ReturnCheckCard(
    links: List<OfficialLink>,
    facts: List<SourcedText>,
    sourceNames: Map<String, String>,
    onOpenLink: (String) -> Unit,
    mode: ReturnCheckMode,
    modifier: Modifier = Modifier,
) {
    val fallback = stringResource(R.string.source_official_fallback)
    val refs = facts.map { SourceRef(resolveSourceName(it.source, sourceNames, fallback), displayDate(it.lastVerified)) }
    var open by rememberSaveable { mutableStateOf(false) }
    val split = remember(facts) { facts.map { firstSentence(it.textKo) } }
    // 같은 출처의 몇 번째 문장인지 — 관세청 둘째 문장(별도 면세)은 다른 아이콘
    val occurrences = remember(facts) { facts.mapIndexed { i, f -> facts.take(i).count { it.source == f.source } } }
    val summary = mode == ReturnCheckMode.Summary
    // 요약 모양: 접힘 = 첫 사실 한 줄, 펼침 = 사실 전체. 전체 모양: 언제나 사실 전체(접힘 = 첫 문장씩)
    val shownFacts = if (summary && !open) facts.take(1).indices else facts.indices
    val foldable = facts.isNotEmpty() && (summary && (facts.size > 1 || split[0].second != null) || split.any { it.second != null })
    CardNewsCard(
        title = stringResource(R.string.shopping_return_title),
        icon = Icons.Outlined.Inventory2,
        modifier = modifier,
        tone = BadgeTone.Accent,
        style = NewsStyle.Surface,
    ) {
        if (!summary) Lead()
        if (facts.isNotEmpty()) {
            Column(Modifier.foldLiveRegion(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shownFacts.forEach { i ->
                    val (lead, rest) = split[i]
                    ReturnFactRow(facts[i].source, occurrences[i], if (open || rest == null) facts[i].textKo.trim() else lead)
                }
            }
        }
        if (foldable) {
            // 펼침 내용은 위 행이 맡는다(문장 전체로 바뀜). 이름에 무엇을 펼치는지 담는다(재검토 R18·재검토2 ②#2)
            ExpandToggle(
                open = open,
                onOpenChange = { open = it },
                label = stringResource(R.string.return_check_full),
                target = stringResource(R.string.fold_target_return),
            )
        }
        if (summary) {
            // 요약 모양의 안내·공식 링크는 펼친 안에 (출처는 아래 접힘 밖)
            AnimatedVisibility(visible = open || !foldable) {
                Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner)) {
                    Lead()
                    links.forEach { link -> LinkRow(link.labelKo, onClick = { onOpenLink(link.url) }) }
                }
            }
        }
        if (refs.isNotEmpty()) Column(Modifier.padding(top = 4.dp)) { SourceList(refs) }
        if (!summary) links.forEach { link -> LinkRow(link.labelKo, onClick = { onOpenLink(link.url) }) }
    }
}

/** 안내 한 줄 (규정은 바뀔 수 있어요…) */
@Composable
private fun Lead() {
    KoText(stringResource(R.string.return_check_lead), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
}

/** 사실 한 행: 주제 아이콘(톤 색, 채움 없음) + 팩 문장(숫자 토큰 굵게). TalkBack·테스트 글자는 원문 */
@Composable
private fun ReturnFactRow(source: String, occurrence: Int, text: String) {
    val dimens = LocalDimens.current
    val (icon, tone) = IconKeys.returnFact(source, occurrence)
    val style = MaterialTheme.typography.bodyLarge
    val size = textIconSize(if (dimens.easyMode) 24.dp else 20.dp, style)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tone.onLight, modifier = Modifier.padding(top = firstLineIconOffset(style, size)).size(size))
        Spacer(Modifier.width(12.dp))
        NumberText(text, style, Modifier.weight(1f), color = Tokens.Ink, emphasisColor = Tokens.Ink)
    }
}

/** 문장 끝(. ! ?) 뒤 띄어쓰기 */
private val SentenceEnd = Regex("""(?<=[.!?])\s+""")

/** 팩 문장을 (첫 문장, 나머지)로 나눈다. 문장 경계가 없으면 (전체, null) — 글을 지어내지 않고 자르기만 한다 */
fun firstSentence(text: String): Pair<String, String?> {
    val t = text.trim()
    val m = SentenceEnd.find(t) ?: return t to null
    val rest = t.substring(m.range.last + 1).trim()
    return t.substring(0, m.range.first).trim() to rest.ifEmpty { null }
}
