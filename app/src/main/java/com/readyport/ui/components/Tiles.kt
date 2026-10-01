package com.readyport.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.Tokens

// ======================= 타일·상태 화면 (DESIGN_SPEC 4.9, 4.16, 6-20) =======================

enum class TileLayout { Auto, Vertical, Horizontal }

/**
 * 누르는 요약 타일 하나. [emphasized]: Navy 채움(화면당 1개, 예: 여행 중 `숙소로 돌아가기`).
 * 2열 라벨은 한 줄 7자 이내(DESIGN_SPEC 3.2) — 넘는 라벨은 부록 B의 짧은 `tile_*` 키를 쓴다.
 */
@Immutable
data class TileSpec(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val supporting: String? = null,
    val tone: BadgeTone = BadgeTone.Accent,
    val emphasized: Boolean = false,
)

/** IconTile 색 선택 (OnDarkPairsTest가 검사). emphasized는 onDark 내용 세트만 — 셰브론 White80(InkTertiary on Navy 2.82 금지) */
@Immutable
data class TileColors(
    val container: Color,
    val label: Color,
    val supporting: Color,
    val badge: BadgeTone,
    val chevron: Color,
    val shadow: Boolean,
)

fun tileColors(emphasized: Boolean, tone: BadgeTone = BadgeTone.Accent): TileColors =
    if (emphasized) {
        TileColors(Tokens.Navy, OnDark.content, OnDark.secondary, BadgeTone.OnDark, OnDark.secondary, shadow = false)
    } else {
        TileColors(Tokens.Surface, Tokens.Ink, Tokens.InkSecondary, tone, Tokens.InkTertiary, shadow = true)
    }

/**
 * 아이콘 타일. Auto면 놓인 그리드가 1열일 때(그리드 밖이면 rememberGridColumns()가 1 — 쉬운 모드·큰 글자) 가로형.
 * 세로형 셰브론은 오른쪽 위(배지 줄 끝): 스펙 4.9의 '오른쪽 아래'는 2열 칸(약 138dp)에서 7자 라벨과 겹치거나
 * 라벨 폭을 줄여 줄바꿈을 만들어서 바꿨다(STAGE0_REPORT 4절 8 — 운영자 확인 항목).
 * a11y: Role.Button, 이름 = 라벨(+보조 글). 쉬운 모드 높이 128(세로)·88(가로) ≥ 64dp.
 */
@Composable
fun IconTile(spec: TileSpec, modifier: Modifier = Modifier, layout: TileLayout = TileLayout.Auto) {
    val dimens = LocalDimens.current
    val horizontal = when (layout) {
        // 그리드 안이면 그 그리드의 열 수로 (쉬운 모드라도 2·3칸 행에 놓이면 세로형)
        TileLayout.Auto -> rememberSingleColumnCell()
        TileLayout.Vertical -> false
        TileLayout.Horizontal -> true
    }
    val c = tileColors(spec.emphasized, spec.tone)
    val shape = MaterialTheme.shapes.medium
    Surface(
        onClick = spec.onClick,
        shape = shape,
        color = c.container,
        contentColor = c.label,
        modifier = modifier
            .fillMaxWidth()
            .then(if (c.shadow) Modifier.cardShadow(shape) else Modifier)
            .heightIn(min = if (horizontal) dimens.tileRowMinHeight else dimens.tileMinHeight)
            .semantics { role = Role.Button },
    ) {
        if (horizontal) {
            Row(
                Modifier.padding(16.dp),
                // 보조 글이 있으면 여러 줄 글 옆이라 위를 맞춘다 (4.2)
                verticalAlignment = if (spec.supporting != null) Alignment.Top else Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconBadge(spec.icon, tone = c.badge)
                TileTexts(spec, c, Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null, tint = c.chevron)
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    IconBadge(spec.icon, tone = c.badge)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null, tint = c.chevron)
                }
                Spacer(Modifier.height(12.dp))
                TileTexts(spec, c, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TileTexts(spec: TileSpec, c: TileColors, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(spec.label, style = MaterialTheme.typography.titleMedium, color = c.label)
        if (spec.supporting != null) Text(spec.supporting, style = MaterialTheme.typography.bodySmall, color = c.supporting)
    }
}

/** IconTile 그리드 (2열 → 쉬운 모드·큰 글자는 1열 가로형) */
@Composable
fun InfoTileGrid(tiles: List<TileSpec>, modifier: Modifier = Modifier, columns: Int = rememberGridColumns()) {
    TileGrid(tiles, modifier, columns) { spec, cell ->
        IconTile(spec, cell, if (columns == 1) TileLayout.Horizontal else TileLayout.Vertical)
    }
}

/**
 * 큰 선택 카드 (첫 실행 등). 카드 전체가 버튼, 이름 = title + body.
 * [preview](예: `가 가`)는 TalkBack 잡음이라 숨긴다. [emphasized]: 2dp Accent 테두리 + AccentSoft 바탕.
 */
@Composable
fun ChoiceCard(
    title: String,
    body: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    preview: (@Composable () -> Unit)? = null,
) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.large
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (emphasized) Tokens.AccentSoft else Tokens.Surface, contentColor = Tokens.Ink),
        border = if (emphasized) BorderStroke(2.dp, Tokens.Accent) else null,
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (emphasized) Modifier else Modifier.cardShadow(shape))
            .heightIn(min = 96.dp)
            .semantics { role = Role.Button },
    ) {
        Row(
            Modifier.padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 강조 카드는 바탕이 AccentSoft라 배지 바탕(AccentSoft)이 사라진다 → 흰 바탕으로 띄운다
            IconBadge(icon, size = 52.dp, containerColor = if (emphasized) Tokens.Surface else BadgeTone.Accent.container)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Tokens.Ink)
                if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                if (preview != null) Box(Modifier.clearAndSetSemantics {}) { preview() }
            }
            Icon(
                Icons.AutoMirrored.Outlined.NavigateNext,
                contentDescription = null,
                tint = if (emphasized) Tokens.Accent else Tokens.InkTertiary,
            )
        }
    }
}

/**
 * 작은 단일 선택 타일 (26 예약 종류). 부모에 selectableGroup()을 둔다.
 * 선택 = Accent 채움 + 흰 글자 + Check. 놓인 그리드가 여러 칸이면(3칸 약 109dp) 세로, 1열이면 가로.
 * 화면은 `TileGrid(columns = if (rememberGridColumns() == 1) 1 else 3)`처럼 큰 글자에서 1열로 내린다.
 */
@Composable
fun SelectTile(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    // 칸 폭 기준: 그리드 안이면 그 그리드의 열 수(3칸 행이면 쉬운 모드·큰 글자여도 세로), 1열일 때만 가로
    val horizontal = rememberSingleColumnCell()
    val shape = MaterialTheme.shapes.medium
    val container = if (selected) Tokens.Accent else Tokens.Surface
    val content = if (selected) Tokens.Surface else Tokens.Ink
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (horizontal) dimens.tileRowMinHeight else dimens.tileMinHeight)
            .clip(shape)
            .background(container)
            .then(if (selected) Modifier else Modifier.border(1.dp, Tokens.LineStrong, shape))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
    ) {
        if (horizontal) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(dimens.icon))
                Text(label, style = MaterialTheme.typography.titleMedium, color = content, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = content)
            }
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(dimens.icon))
                Text(label, style = MaterialTheme.typography.titleMedium, color = content, textAlign = TextAlign.Center)
            }
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = content, modifier = Modifier.align(Alignment.TopEnd).size(dimens.iconSmall))
            }
        }
    }
}

/** 빈 상태 (오류가 아니면 Neutral — Caution 금지). 원 96dp + 아이콘 40dp */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(96.dp).background(tone.container, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tone.onLight, modifier = Modifier.size(40.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Tokens.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyLarge, color = Tokens.InkSecondary, textAlign = TextAlign.Center)
        }
        if (action != null) action()
    }
}

/** 잠긴 화면: Navy 원 + 흰 [icon] + 오른쪽 아래 작은 [badgeIcon] 배지, 제목, 설명, 여는 버튼(지문) */
@Composable
fun LockedState(
    title: String,
    body: String?,
    buttonLabel: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Lock,
    badgeIcon: ImageVector = Icons.Outlined.Fingerprint,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(104.dp)) {
            Box(Modifier.size(96.dp).background(Tokens.Navy, CircleShape).align(Alignment.Center), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = OnDark.content, modifier = Modifier.size(40.dp))
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(32.dp).background(Tokens.AccentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(badgeIcon, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Tokens.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyLarge, color = Tokens.InkSecondary, textAlign = TextAlign.Center)
        }
        PrimaryButton(buttonLabel, onClick = onUnlock, icon = Icons.Outlined.Fingerprint)
    }
}

/**
 * '준비 중' 기능을 화면 맨 아래 한 장으로 (D15). 누를 수 없다.
 * 항목마다 disabled() semantics — TalkBack이 '사용 안 함'으로 읽고 assertIsNotEnabled()가 통과한다.
 */
@Composable
fun ComingSoonGroup(items: List<Pair<ImageVector, String>>, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    Surface(color = Tokens.SurfaceSunken, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.coming_soon_group),
                    style = MaterialTheme.typography.titleSmall,
                    color = Tokens.InkSecondary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                StatusTag(stringResource(R.string.coming_soon), StatusKind.Soon)
            }
            items.forEach { (icon, text) ->
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) { disabled() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = Tokens.InkTertiary, modifier = Modifier.size(dimens.icon))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 긴급 전화 타일 색 선택 (OnDarkPairsTest가 검사). large = Navy 채움, onDark 내용 세트만 — Call 아이콘도 Surface(Help on Navy 2.74 금지) */
@Immutable
data class EmergencyColors(
    val container: Color,
    val number: Color,
    val label: Color,
    val note: Color,
    val call: Color,
    val badge: BadgeTone,
    val bar: Color?,
)

fun emergencyColors(large: Boolean): EmergencyColors =
    if (large) {
        EmergencyColors(Tokens.Navy, OnDark.content, OnDark.content, OnDark.secondary, OnDark.content, BadgeTone.OnDark, bar = null)
    } else {
        EmergencyColors(Tokens.HelpSoft, Tokens.Ink, Tokens.Ink, Tokens.InkSecondary, Tokens.Help, BadgeTone.Help, bar = Tokens.Help)
    }

/**
 * 긴급 번호 타일 (6-20). 누르면 전화 앱의 다이얼 화면만 연다(자동 발신 없음 — [onCall]이 처리).
 * 번호 길이 규칙: 8자를 넘으면 statSmall. 번호 글자에는 보이지 않는 문자를 넣지 않는다(테스트가 그대로 찾음).
 * 2열 그리드에는 6자 이하 번호만 두고, 긴 번호는 폭 전체로 놓는 것은 호출하는 쪽이 정한다.
 * TalkBack: 기존 CallButton 설명 형식(`help_call` + 번호) + 보이는 note.
 */
@Composable
fun EmergencyCallTile(
    label: String,
    number: String,
    icon: ImageVector,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    large: Boolean = false,
) {
    val dimens = LocalDimens.current
    val extras = LocalTypeExtras.current
    val c = emergencyColors(large)
    val shape = MaterialTheme.shapes.medium
    val description = stringResource(R.string.help_call, label) + " " + number + (note?.let { ", $it" } ?: "")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.tileMinHeight)
            .clip(shape)
            .background(c.container)
            .then(if (c.bar != null) Modifier.startBar(c.bar) else Modifier)
            .clickable(role = Role.Button, onClick = onCall)
            .semantics { contentDescription = description },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, tone = c.badge, size = dimens.iconBadgeSmall)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.Call, contentDescription = null, tint = c.call, modifier = Modifier.size(dimens.icon))
            }
            Text(number, style = if (number.length > 8) extras.statSmall else extras.stat, color = c.number)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.label)
            if (note != null) {
                if (large) StatusTag(note, StatusKind.Info) else Text(note, style = MaterialTheme.typography.bodySmall, color = c.note)
            }
        }
    }
}
