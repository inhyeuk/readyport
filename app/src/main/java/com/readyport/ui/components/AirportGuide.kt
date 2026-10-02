package com.readyport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.LocalAirport
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.pack.Airport
import com.readyport.pack.AirportStep
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 공항에 도착하면 (2026-10-03) =======================
// 팩 airports[] 를 카드뉴스로: 공항 고르기(칩) → 번호 단계(단계마다 kind 아이콘 · 위치 칩 · 짧은 설명) → 공식 안내도 → 출처.
// 입국 카드를 보여 주는 줄은 form_check(없으면 입국 심사) 단계 안에 노란 강조 줄로, 자동 심사대 줄은 입국 심사 단계 안에(모르면 숨김).
// 문장·위치는 팩 원문 그대로 — 앱은 층·홀을 짐작해 덧붙이지 않는다(작업 규칙 6).

/** 공항 안내의 출처 줄 — 공항·단계·입국 카드 줄의 출처 id를 화면 출처로 (날짜는 공항 확인일) */
fun airportSources(airport: Airport, sourceOf: (id: String, lastVerified: String) -> SourceRef): List<SourceRef> =
    airport.sourceIds.map { sourceOf(it, airport.lastVerified) }

/**
 * 나라 화면의 공항 고르기: 공항 이름 칩(한 개만 고름 — selectableGroup + RadioButton 역할). 칩은 줄이 모자라면 아래 줄로 흐른다(FlowRow).
 */
@Composable
fun AirportPicker(
    airports: List<Airport>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(R.string.airport_picker_desc)
    FlowRow(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = desc }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        airports.forEach { ap ->
            SelectChip(
                selected = ap.code == selected,
                onClick = { onSelect(ap.code) },
                label = ap.nameKo,
                leadingIcon = Icons.Outlined.LocalAirport,
            )
        }
    }
}

/**
 * 여행 고치기의 내리는 공항: 폭 전체 선택 카드(SelectableCard — 큰 선택 규칙 ②) 한 줄씩 — 공항 이름(titleMedium) + `방콕 · BKK`(보조),
 * 맨 끝 `아직 몰라요`([selected] = null). 공항 이름만으로 도시를 모르는 사람도 고를 수 있게 도시·코드를 함께 보인다.
 */
@Composable
fun AirportRadioList(
    airports: List<Airport>,
    selected: String?,
    onSelect: (String?) -> Unit,
    unknownLabel: String,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(R.string.airport_picker_desc)
    val iconSize = LocalDimens.current.icon
    Column(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = desc }
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        airports.forEach { ap ->
            val sel = ap.code == selected
            SelectableCard(
                selected = sel,
                onClick = { onSelect(ap.code) },
                leading = { Icon(Icons.Outlined.LocalAirport, contentDescription = null, tint = selectionIconTint(sel), modifier = Modifier.size(iconSize)) },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    KoText(ap.nameKo, MaterialTheme.typography.titleMedium, color = Tokens.Ink)
                    KoText(stringResource(R.string.airport_city_code, ap.cityKo, ap.code), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                }
            }
        }
        SelectableCard(
            selected = selected == null,
            onClick = { onSelect(null) },
            leading = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = selectionIconTint(selected == null), modifier = Modifier.size(iconSize)) },
        ) {
            KoText(unknownLabel, MaterialTheme.typography.titleMedium, color = Tokens.Ink)
        }
    }
}

/** 고른 공항 머리: 이름(제목) + `방콕 · BKK · Suvarnabhumi Airport`(보조) */
@Composable
fun AirportHead(airport: Airport, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KoText(airport.nameKo, MaterialTheme.typography.titleLarge, color = Tokens.Ink, heading = true)
        KoText(
            stringResource(R.string.airport_city_code, airport.cityKo, airport.code) + " · " + airport.nameEn,
            MaterialTheme.typography.bodyMedium,
            color = Tokens.InkSecondary,
        )
    }
}

/**
 * 공항 순서 번호 단계 — StepList와 같은 번호 원·세로선(StepRow). 단계마다 한 덩어리(TalkBack mergeDescendants).
 * - 제목: kind 아이콘 + 팩 title_ko (titleMedium). 큰 글자 배치에서는 아이콘을 글 첫 줄 안으로(StepList와 같은 규칙)
 * - 위치: where_ko가 있을 때만 위치 칩(Place) — 팩 원문 그대로
 * - 설명: [showBody]일 때만 body_ko(보조 글) — 오늘 화면의 짧은 모양은 제목·위치만
 * - 입국 카드 줄(form_check_ko): form_check 단계(없으면 입국 심사)에 노란 강조 줄 — 이 줄은 짧은 모양에서도 늘 보인다
 * - 자동 심사대 줄: egate 단계(없으면 입국 심사) 안에 — 그 단계 제목이 조건을 먼저 말한다(대만 `등록부터`, 베트남 `베트남 국민용`)
 * [extra]: 팩 단계 뒤에 이어 붙일 앱 안내 단계(오늘 화면 도착 단계의 유심·환전·숙소) — 번호가 이어진다.
 */
@Composable
fun AirportSteps(airport: Airport, showBody: Boolean, modifier: Modifier = Modifier, extra: List<Step> = emptyList()) {
    val dimens = LocalDimens.current
    val formAt = airport.formStepIndex
    val egateAt = airport.egateStepIndex
    val total = airport.steps.size + extra.size
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
        // 붙일 단계가 없으면 단계 목록 위에
        if (formAt == null) airport.formCheckKo?.let { FormCheckLine(it) }
        if (egateAt == null) EgateLine(airport)
        Column(Modifier.fillMaxWidth()) {
            airport.steps.forEachIndexed { i, step ->
                AirportStepRow(
                    number = i + 1,
                    last = i == total - 1,
                    title = step.titleKo,
                    icon = IconKeys.airportStep(step.kind),
                ) {
                    step.whereKo?.takeIf { it.isNotBlank() }?.let { where ->
                        val desc = stringResource(R.string.airport_where_desc, where)
                        InfoChip(where, Icons.Outlined.Place, Modifier.semantics { contentDescription = desc }, tone = BadgeTone.Accent)
                    }
                    if (showBody) NumberText(step.bodyKo, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                    if (i == egateAt) EgateLine(airport)
                    if (i == formAt) airport.formCheckKo?.let { FormCheckLine(it) }
                }
            }
            extra.forEachIndexed { k, step ->
                val n = airport.steps.size + k
                AirportStepRow(number = n + 1, last = n == total - 1, title = step.text, icon = step.icon) {
                    step.detail?.let { NumberText(it, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary) }
                }
            }
        }
    }
}

/** 단계 한 줄: 번호 원 + 세로선 + (아이콘 제목 · 아래 내용) */
@Composable
private fun AirportStepRow(
    number: Int,
    last: Boolean,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = LocalDimens.current
    val style = MaterialTheme.typography.titleMedium
    val iconSize = textIconSize(if (dimens.easyMode) 24.dp else 20.dp, style)
    val inline = isStackedLayout()
    StepRow(
        badge = { TextCircle("$number") },
        showLine = !last,
        minBadge = dimens.stepBadge,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        // 위치·설명·입국 카드 줄은 아이콘 열이 아니라 단계 제목 시작선에 맞춘다(StepList 보조 글과 같은 규칙, BUNDLE_A_NOTES 요청 3)
        Row(
            Modifier.padding(top = 2.dp, bottom = if (last) 0.dp else dimens.gap),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null && !inline) {
                Icon(icon, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.padding(top = firstLineIconOffset(style, iconSize)).size(iconSize))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (icon != null && inline) {
                    LeadIconText(title, icon, style, Tokens.Ink, Tokens.Accent)
                } else {
                    KoText(title, style, color = Tokens.Ink, glueShort = true)
                }
                content()
            }
        }
    }
}

/** 입국 카드(QR·확인 메일)를 보여 주는 곳 — 노란 강조 줄(IconBullet Caution: 연한 바탕), 팩 문장 그대로 */
@Composable
private fun FormCheckLine(text: String) {
    IconBullet(text, Icons.Outlined.QrCode2, tone = BadgeTone.Caution)
}

/**
 * 자동 출입국 심사대: true = 쓸 수 있어요(초록 체크), false = 쓸 수 없어요(막힘), null이면
 * 팩 메모가 있을 때만 `분명하지 않아요`(물음표 — 인도네시아처럼 공식 안내 두 곳이 다를 때). 메모도 없으면 아무것도 그리지 않는다.
 * 조건·갈린 안내는 팩 메모(egate_note_ko) 그대로 바로 아래 보조 글로 — 앱이 조건을 지어내거나 요약하지 않는다.
 */
@Composable
private fun EgateLine(airport: Airport) {
    val ok = airport.egateKr
    val note = airport.egateNoteKo?.takeIf { it.isNotBlank() }
    if (ok == null && note == null) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        IconBullet(
            stringResource(
                when (ok) {
                    true -> R.string.airport_egate_yes
                    false -> R.string.airport_egate_no
                    null -> R.string.airport_egate_unknown
                },
            ),
            when (ok) {
                true -> Icons.Outlined.CheckCircle
                false -> Icons.Outlined.Block
                null -> Icons.AutoMirrored.Outlined.HelpOutline
            },
            tone = if (ok == true) BadgeTone.Success else BadgeTone.Neutral,
        )
        note?.let {
            NumberText(it, MaterialTheme.typography.bodySmall, Modifier.padding(start = 32.dp), color = Tokens.InkSecondary)
        }
    }
}

/**
 * 나라 화면 입국·비자의 `공항에 도착하면` 카드(전체 모양). 공항이 둘 이상이면 고르기 칩, 하나면 칩 없이 머리만.
 * 공식 안내도는 링크 줄(브라우저로 연다), 출처는 카드 맨 아래.
 */
@Composable
fun AirportGuideCard(
    airports: List<Airport>,
    selected: Airport,
    onSelect: (String) -> Unit,
    sources: List<SourceRef>,
    onOpenMap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CardNewsCard(
        title = stringResource(R.string.airport_guide_title),
        // 바로 아래 `들어갈 때` 섹션 배지가 FlightLand라 공항 묶음은 LocalAirport(단계 1 `비행기에서 내려요`가 FlightLand)
        icon = Icons.Outlined.LocalAirport,
        modifier = modifier,
        sources = sources,
    ) {
        if (airports.size > 1) {
            KoText(stringResource(R.string.airport_guide_pick), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
            AirportPicker(airports, selected.code, onSelect)
        }
        AirportHead(selected, Modifier.padding(top = 4.dp))
        AirportSteps(selected, showBody = true)
        LinkRow(stringResource(R.string.airport_map_open), onClick = { onOpenMap(selected.mapUrl) }, icon = Icons.Outlined.Map)
    }
}

/**
 * 오늘 화면용 짧은 공항 순서 카드: 제목(단계 문구) + eyebrow(공항 이름 · 도시) → 단계 제목·위치·입국 카드 줄 → [extra] 앱 안내 단계 →
 * `공항 순서 자세히 보기`(나라 화면 공항 묶음으로, 글자 버튼) → [footer](예: 다 했어요) → 출처.
 */
@Composable
fun AirportCompactCard(
    title: String,
    airport: Airport,
    sources: List<SourceRef>,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier,
    extra: List<Step> = emptyList(),
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    CardNewsCard(
        title = title,
        icon = Icons.Outlined.FlightLand,
        modifier = modifier,
        eyebrow = stringResource(R.string.airport_city_code, airport.nameKo, airport.cityKo),
        sources = sources,
    ) {
        AirportSteps(airport, showBody = false, extra = extra)
        // 다른 화면으로 가는 길은 글자 버튼(아래 `다 했어요` 같은 이 카드의 할 일보다 약하게)
        QuietButton(stringResource(R.string.airport_open_guide), onClick = onOpenGuide, icon = Icons.AutoMirrored.Outlined.NavigateNext)
        footer()
    }
}
