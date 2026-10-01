package com.readyport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Outlet
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.pack.CountryPack
import com.readyport.pack.EssentialRule
import com.readyport.pack.PackIndex
import com.readyport.pack.PowerInfo
import com.readyport.prep.Essentials
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.Tokens

// ======================= 꼭 챙길 물건 요약 (다듬기 S — 홈 01·02와 여행 준비 18 공통) =======================
// 화면 라운드 S2가 여행 준비(TabScreens)에 두고 홈이 import해 쓰던 칩·진행 줄을 공용으로 옮겼다(모양은 그대로).
// 꼭 챙길 물건 화면(19)의 여행지 전기 카드는 자기 화면 안 카드(PowerValuesCard)다.

/**
 * 홈·여행 준비의 '꼭 챙길 물건' 요약: 전체 n개 중 m개 + 값이 있는 정보 칩의 재료(재검토2 ①#15·②#9·③#10).
 * [power]: 여행 나라의 전기(팩 power — 여행이 있을 때만), [carryOnOnly]: 기내 반입만 되는 물건(팩 essentials rule_badge = carry_on_only),
 * [sources]: 칩 값의 출처(이름을 못 찾으면 빈 이름 → 화면이 `공식 안내`).
 */
data class EssentialsSummary(
    val total: Int = 0,
    val done: Int = 0,
    val power: PowerInfo? = null,
    val carryOnOnly: List<EssentialRule> = emptyList(),
    val sources: List<SourceRef> = emptyList(),
)

/** 팩 값만으로 꼭 챙길 물건 요약을 만든다 — 홈·여행 준비가 같은 계산(여행 나라 [tripPack]이 없으면 전기 칩 없음) */
fun essentialsSummary(index: PackIndex?, tripPack: CountryPack?, have: Set<String>): EssentialsSummary {
    val rules = Essentials.select(index?.essentials.orEmpty(), index?.homePower, tripPack?.power)
    val carry = rules.filter { it.ruleBadge == CARRY_ON_ONLY }
    val names = index?.sources.orEmpty().associate { it.id to it.name }
    val power = tripPack?.power
    val sources = buildList {
        if (power != null) add(SourceRef(tripPack.source(power.source)?.name.orEmpty(), displayDate(power.lastVerified)))
        carry.forEach { r -> r.lastVerified?.let { d -> add(SourceRef(r.source?.let { names[it] }.orEmpty(), displayDate(d))) } }
    }
    return EssentialsSummary(rules.size, rules.count { it.id in have }, power, carry, sources)
}

private const val CARRY_ON_ONLY = "carry_on_only"

/** 칩 값의 출처 (이름을 못 찾으면 `공식 안내`) */
@Composable
fun essentialsSources(summary: EssentialsSummary): List<SourceRef> {
    val fallback = stringResource(R.string.source_official_fallback)
    return summary.sources.map { if (it.name.isBlank()) it.copy(name = fallback) else it }
}

/**
 * 꼭 챙길 물건 정보 칩 — **값이 있는 것만**(재검토2 ①#15·②#9·③#10): 주제 이름만 늘어놓던 `플러그 · 전압 · 보조배터리` 대신
 * 여행 나라 팩 전기(`한국 플러그 그대로 써요` 또는 `변환 어댑터 챙기세요`, `220 V 전압`) + 기내 반입만 되는 물건(`보조배터리 기내 반입만 가능`).
 * 값이 하나도 없으면 칩 줄을 그리지 않는다. 누를 수 없는 InfoChip(값 굵게 + 말).
 */
@Composable
fun EssentialsChips(summary: EssentialsSummary) {
    val power = summary.power
    if (power == null && summary.carryOnOnly.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (power != null) {
            // 팩 kr_plug_fits가 true일 때만 '그대로' — 확인되지 않은 나라는 어댑터를 권한다(Essentials.plugDiffers와 같은 규칙)
            if (power.krPlugFits == true) {
                InfoChip(stringResource(R.string.items_chip_plug_fits), Icons.Outlined.Power, value = stringResource(R.string.items_chip_plug_value), tone = BadgeTone.Accent)
            } else {
                InfoChip(stringResource(R.string.items_chip_adapter), Icons.Outlined.Outlet, value = stringResource(R.string.items_chip_adapter_value), tone = BadgeTone.Accent)
            }
            InfoChip(stringResource(R.string.home_items_voltage), Icons.Outlined.ElectricBolt, value = power.voltage, tone = BadgeTone.Accent)
        }
        summary.carryOnOnly.forEach { r ->
            InfoChip(stringResource(R.string.essentials_badge_carry_on), IconKeys.essential(r.id), value = r.nameKo, tone = BadgeTone.Accent)
        }
    }
}

/**
 * 진행 줄: 큰 숫자 `n / 5` + 설명을 글자 기준선에 맞춰 한 줄로(큰 글자 배치에서는 큰 숫자를 설명 위로) + 폭 전체 막대.
 * 홈(01·02)과 여행 준비(18)가 같은 모양(재검토2 ③#10). 진행 데이터가 없으면 그리지 않는다. TalkBack은 한 덩어리.
 */
@Composable
fun EssentialsProgress(summary: EssentialsSummary) {
    if (summary.total <= 0) return
    val dimens = LocalDimens.current
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
        val stat = stringResource(R.string.essentials_progress_stat, summary.done, summary.total)
        val label = keepWords(stringResource(R.string.essentials_progress, summary.total, summary.done))
        val statStyle = LocalTypeExtras.current.statSmall
        val labelStyle = MaterialTheme.typography.bodyMedium
        if (isStackedLayout()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stat, style = statStyle, color = Tokens.Accent)
                Text(label, style = labelStyle, color = Tokens.InkSecondary)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stat, style = statStyle, color = Tokens.Accent, modifier = Modifier.alignByBaseline())
                Text(label, style = labelStyle, color = Tokens.InkSecondary, modifier = Modifier.alignByBaseline().weight(1f))
            }
        }
        LinearProgressIndicator(
            progress = { summary.done.toFloat() / summary.total },
            modifier = Modifier.fillMaxWidth().height(dimens.inner),
            color = Tokens.Accent,
            trackColor = Tokens.SurfaceSunken,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}
