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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
    /** 길 안내 모자이크([NavMosaic])에서 배지 대신 그릴 일러스트(꾸밈, Illustrations.kt). 다른 타일은 쓰지 않는다 */
    val illustration: ImageVector? = null,
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
                // 가로형(1열) 타일은 목록 행과 같은 안쪽 여백 — 배지가 카드 내용 시작선에 선다 (재검토 R4)
                Modifier.padding(horizontal = dimens.listRowPadding, vertical = dimens.listRowPaddingVertical),
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
        KoText(spec.label, MaterialTheme.typography.titleMedium, color = c.label)
        if (spec.supporting != null) KoText(spec.supporting, MaterialTheme.typography.bodySmall, color = c.supporting)
    }
}

/** IconTile 그리드 (2열 → 쉬운 모드·큰 글자는 1열 가로형) */
@Composable
fun InfoTileGrid(tiles: List<TileSpec>, modifier: Modifier = Modifier, columns: Int = rememberGridColumns()) {
    TileGrid(tiles, modifier, columns) { spec, cell ->
        IconTile(spec, cell, if (columns == 1) TileLayout.Horizontal else TileLayout.Vertical)
    }
}

// ======================= 길 안내 모자이크 (DESIGN_SPEC 4.19 — 메뉴와 내용 분리 v3) =======================
// 규칙: **카드가 방금 설명한 일을 그 자리에서 하는 버튼은 카드 안에 남는다**(입국 카드 채우기·비자 신청).
// **다른 화면으로 가는 것만이 목적인 타일·버튼은 그 갈래 내용의 맨 끝 [NavMosaic] 한 묶음으로 모은다.**
// 길 안내 타일을 읽는 카드 사이에 끼워 넣지 않는다 — 흰 카드 사이의 흰 타일은 '읽을 카드'와 구분되지 않았다(운영자 지적 2026-10-02).

/**
 * 길 안내 타일 색: **연한 톤 채움**(tone.container) + Ink 글자. 읽는 카드는 흰 바탕이라 한눈에 갈린다.
 * 배지는 흰 바탕으로 띄운다(배지 바탕이 타일 바탕과 같은 색이면 사라진다 — EmergencyCallTile과 같은 처리).
 */
@Immutable
data class NavTileColors(
    val container: Color,
    val label: Color,
    val supporting: Color,
    val badge: BadgeTone,
    val badgeContainer: Color,
    val chevron: Color,
)

fun navTileColors(tone: BadgeTone): NavTileColors =
    NavTileColors(tone.container, Tokens.Ink, Tokens.InkSecondary, tone, Tokens.Surface, Tokens.InkSecondary)

/**
 * 한 갈래(탭) 내용 맨 끝에 모아 두는 **길 안내 모자이크**: 머리([title], 기본 `여기서 더 볼 수 있어요`) +
 * 첫 타일은 **크게**(폭 전체·큰 배지·큰 글자), 나머지는 그 아래 2열 그리드 — 일부러 크기를 다르게 해서
 * 같은 크기로 늘어선 읽는 카드와 혼동되지 않게 한다.
 * - 1열(쉬운 모드·큰 글자)에서는 폭 전체 가로 행으로 내려가지만 **머리 아래 한 묶음·연한 채움**은 그대로다(내용을 숨기지 않는다).
 * - 2열 칸 라벨은 넘겨받은 줄바꿈(`현지어와\n긴급 번호`)을 그대로 쓰고, 폭 전체 타일에서는 한 줄로 편다.
 * - 타일이 하나면 큰 타일 한 장이다(한 칸짜리 그리드를 만들지 않는다).
 */
@Composable
fun NavMosaic(
    tiles: List<TileSpec>,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.nav_more_here),
    icon: ImageVector? = Icons.Outlined.GridView,
    columns: Int = rememberGridColumns(),
) {
    if (tiles.isEmpty()) return
    val dimens = LocalDimens.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.gap)) {
        SectionHeader(title, icon = icon)
        NavTile(tiles.first(), large = true)
        val rest = tiles.drop(1)
        when {
            rest.isEmpty() -> Unit
            columns == 1 -> rest.forEach { NavTile(it, row = true) }
            else -> TileGrid(rest, columns = columns) { spec, cell -> NavTile(spec, modifier = cell) }
        }
    }
}

/**
 * 길 안내 타일 하나. [large](모자이크 첫 타일)·[row](1열)는 폭 전체 가로형(라벨 한 줄), 그 밖에는 2열 칸 세로형.
 * 큰 글자 배치에서는 배지·셰브론을 윗줄로 올리고 글에 폭 전체를 준다(ChoiceCard·IconTile과 같은 처리).
 * a11y: Role.Button, 이름 = 라벨(+보조 글).
 */
@Composable
private fun NavTile(spec: TileSpec, modifier: Modifier = Modifier, large: Boolean = false, row: Boolean = false) {
    val dimens = LocalDimens.current
    val c = navTileColors(spec.tone)
    val shape = MaterialTheme.shapes.medium
    val wide = large || row
    val stacked = isStackedLayout()
    val labelStyle = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
    val art = spec.illustration
    val badge: @Composable () -> Unit = {
        if (art != null) {
            // 그림 메뉴(위 SectionCards)와 같은 그림 언어: 흰색 → 갈래 색 그라데이션 패널 위 일러스트(꾸밈)
            Box(
                Modifier
                    .size(if (large) NavLargeArt else NavSmallArt)
                    .clip(MaterialTheme.shapes.medium)
                    .background(illusPanelBrush(spec.tone.illusTone(), rich = false)),
                contentAlignment = Alignment.Center,
            ) {
                IllusImage(art, Modifier.fillMaxSize(NAV_ART_FILL))
            }
        } else {
            IconBadge(
                spec.icon,
                tone = c.badge,
                size = if (large) dimens.iconBadge + NavLargeBadgeExtra else dimens.iconBadge,
                containerColor = c.badgeContainer,
            )
        }
    }
    val chevron: @Composable () -> Unit = {
        Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null, tint = c.chevron)
    }
    val texts: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KoText(if (wide) spec.label.replace('\n', ' ') else spec.label, labelStyle, color = c.label, glueShort = true)
            if (spec.supporting != null) KoText(spec.supporting, MaterialTheme.typography.bodyMedium, color = c.supporting)
        }
    }
    Surface(
        onClick = spec.onClick,
        shape = shape,
        color = c.container,
        contentColor = c.label,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (row && !stacked) dimens.tileRowMinHeight else dimens.tileMinHeight)
            .semantics { role = Role.Button },
    ) {
        if (wide && !stacked) {
            Row(
                Modifier.padding(horizontal = dimens.listRowPadding, vertical = dimens.listRowPaddingVertical),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                badge()
                texts(Modifier.weight(1f))
                chevron()
            }
        } else {
            Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    badge()
                    Spacer(Modifier.weight(1f))
                    chevron()
                }
                texts(Modifier.fillMaxWidth())
            }
        }
    }
}

/** 모자이크 큰 타일의 배지가 2열 칸 타일보다 커지는 만큼 */
private val NavLargeBadgeExtra = 12.dp

/** 모자이크 큰 타일의 그림 패널 한 변 (그림 메뉴 카드 패널과 비슷한 크기 — 같은 그림 언어) */
private val NavLargeArt = 76.dp

/** 모자이크 작은 타일(2열 칸·1열 행)의 그림 패널 한 변 */
private val NavSmallArt = 56.dp

/** 패널 대비 그림 크기 */
private const val NAV_ART_FILL = 0.9f

/**
 * 큰 선택 카드 (첫 실행 등). 카드 전체가 버튼, 이름 = title + body.
 * [preview](예: `가 가`)는 TalkBack 잡음이라 숨긴다. [emphasized](추천): 흰 바탕 + 2dp Accent 테두리 + Accent 셰브론 —
 * AccentSoft 채움은 '선택됨'(SelectableCard)에만 쓰므로 추천 카드가 이미 골라진 것처럼 보이지 않게 한다(재검토 R2).
 * 큰 글자 배치(LayoutClass.Stacked)에서는 배지·셰브론을 맨 윗줄에, 제목·설명·미리보기를 그 아래 카드 폭 전체에 둔다 —
 * 52dp 배지와 셰브론 열 사이 좁은 칸에서 제목이 `처음이에/요`처럼 꺾이지 않게 (BUNDLE_A_NOTES ⑥, IconTile 세로형과 같은 배치).
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
    val stacked = isStackedLayout()
    val badge: @Composable () -> Unit = { IconBadge(icon, size = 52.dp) }
    val chevron: @Composable () -> Unit = {
        Icon(
            Icons.AutoMirrored.Outlined.NavigateNext,
            contentDescription = null,
            tint = if (emphasized) Tokens.Accent else Tokens.InkTertiary,
        )
    }
    val texts: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KoText(title, MaterialTheme.typography.titleLarge, color = Tokens.Ink)
            if (body != null) KoText(body, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
            if (preview != null) Box(Modifier.clearAndSetSemantics {}) { preview() }
        }
    }
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        border = if (emphasized) BorderStroke(2.dp, Tokens.Accent) else null,
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = modifier
            .fillMaxWidth()
            // 추천 카드는 자기 2dp Accent 테두리가 있다 — 옅은 카드 테두리를 겹쳐 그리지 않는다
            .cardShadow(shape, border = !emphasized)
            .heightIn(min = 96.dp)
            .semantics { role = Role.Button },
    ) {
        if (stacked) {
            Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    badge()
                    Spacer(Modifier.weight(1f))
                    chevron()
                }
                texts(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier.padding(dimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                badge()
                texts(Modifier.weight(1f))
                chevron()
            }
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
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(textIconSize(dimens.icon, MaterialTheme.typography.titleMedium)))
                KoText(label, MaterialTheme.typography.titleMedium, Modifier.weight(1f), color = content)
                if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = content)
            }
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(textIconSize(dimens.icon, MaterialTheme.typography.titleMedium)))
                KoText(label, MaterialTheme.typography.titleMedium, color = content, textAlign = TextAlign.Center)
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
        KoText(title, MaterialTheme.typography.titleLarge, color = Tokens.Ink, textAlign = TextAlign.Center, heading = true, glueShort = true)
        if (body != null) {
            KoText(body, MaterialTheme.typography.bodyLarge, color = Tokens.InkSecondary, textAlign = TextAlign.Center)
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
        KoText(title, MaterialTheme.typography.titleLarge, color = Tokens.Ink, textAlign = TextAlign.Center, heading = true, glueShort = true)
        if (body != null) {
            KoText(body, MaterialTheme.typography.bodyLarge, color = Tokens.InkSecondary, textAlign = TextAlign.Center)
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
            // 머리 줄: `준비 중이에요` 태그가 제목을 쪼갤 만큼 폭이 모자라면 제목 아래 줄로 (`곧 추/가돼/요` 방지)
            TrailingFlow(
                trailing = { StatusTag(stringResource(R.string.coming_soon), StatusKind.Soon) },
                modifier = Modifier.fillMaxWidth(),
                gap = 8.dp,
                centerVertically = true,
            ) {
                KoText(
                    stringResource(R.string.coming_soon_group),
                    MaterialTheme.typography.titleSmall,
                    color = Tokens.InkSecondary,
                    heading = true,
                )
            }
            val style = MaterialTheme.typography.bodyMedium
            val iconSize = textIconSize(dimens.icon, style)
            items.forEach { (icon, text) ->
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) { disabled() },
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Tokens.InkTertiary,
                        modifier = Modifier.padding(top = firstLineIconOffset(style, iconSize)).size(iconSize),
                    )
                    KoText(text, style, Modifier.weight(1f), color = Tokens.InkSecondary)
                }
            }
        }
    }
}

/**
 * 긴급 전화 타일 색 선택 (OnDarkPairsTest가 검사). 긴급 = Help(주황) 계열 하나로 (재검토 R7 — Navy는 보안·현지인에게 보여 주기·오프라인에만):
 * - large(대표 번호) = Help 채움 + onDark 내용 세트(Surface 6.03·White85 4.81)
 * - 기본 = HelpSoft 바탕 + 왼쪽 Help 막대 + Ink 글자
 */
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
        EmergencyColors(Tokens.Help, OnDark.content, OnDark.content, OnDark.eyebrow, OnDark.content, BadgeTone.OnDark, bar = null)
    } else {
        EmergencyColors(Tokens.HelpSoft, Tokens.Ink, Tokens.Ink, Tokens.InkSecondary, Tokens.Help, BadgeTone.Help, bar = Tokens.Help)
    }

/** 전화번호를 '-'(또는 띄어쓰기) 바로 뒤에서 나눈 묶음 (`+66-81-914-5803` → `+66-`, `81-`, `914-`, `5803`). 이어 붙이면 원래 번호 */
fun phoneGroups(number: String): List<String> {
    val groups = mutableListOf<String>()
    val cur = StringBuilder()
    number.forEach { c ->
        cur.append(c)
        if (c == '-' || c == ' ') {
            groups += cur.toString()
            cur.clear()
        }
    }
    if (cur.isNotEmpty()) groups += cur.toString()
    return groups
}

/**
 * 긴급 전화번호 글자 크기 단계 (큰 것부터, 재검토 R6): stat → statSmall → titleLarge → titleMedium → bodyLarge → bodySmall 크기(모두 굵게, 고정폭 숫자).
 * 마지막 값이 최소 — 쉬운 모드 18sp(PRD 3.2 하한), 기본 모드 13sp.
 */
@Composable
fun phoneNumberStyles(): List<TextStyle> {
    val extras = LocalTypeExtras.current
    val t = MaterialTheme.typography
    val base = extras.statSmall
    return remember(extras, t) {
        listOf(extras.stat, extras.statSmall) +
            listOf(t.titleLarge, t.titleMedium, t.bodyLarge, t.bodySmall).map { base.copy(fontSize = it.fontSize, lineHeight = it.lineHeight) }
    }
}

/**
 * 긴급 전화번호 (6-20 + 재검토 R6). 실제 칸 폭을 재서 **한 줄에 들어가는 가장 큰 크기**로 그린다([FitText]) —
 * `+66-81-914-⏎5803`처럼 번호가 두 줄로 쪼개지지 않게. 가장 작은 크기로도 안 들어가는 아주 좁은 창에서만 '-' 뒤에서 줄을 바꾼다.
 * 번호 글자에는 보이지 않는 문자를 넣지 않고(3.2), 의미 글자는 번호 전체 한 노드(테스트·TalkBack이 그대로 찾는다).
 * 큰 글자에서는 긴 번호 타일을 폭 전체로 놓는 것이 부르는 쪽 몫이다(도움 화면: 대사관·영사콜센터 타일을 카드 밖 폭 전체로).
 */
@Composable
fun PhoneNumberText(number: String, color: Color, modifier: Modifier = Modifier, styles: List<TextStyle> = phoneNumberStyles()) {
    FitText(number, styles, color, modifier, breakChars = "- ")
}

/**
 * 긴급 번호 타일 (6-20). 누르면 전화 앱의 다이얼 화면만 연다(자동 발신 없음 — [onCall]이 처리).
 * 번호는 칸 폭에 맞춰 한 줄로([PhoneNumberText]). 2열 그리드에는 6자 이하 번호만 두고, 긴 번호를 폭 전체로 놓는 것은 부르는 쪽이 정한다.
 * [large](대표 번호): Help 채움(onDark 내용 세트), 기본: HelpSoft + Help 막대 — 같은 '긴급' 색 하나(재검토 R7).
 * 연한 Help 타일의 배지는 흰 바탕으로 띄운다(배지 바탕 HelpSoft가 타일 바탕과 같아 사라지지 않게).
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
                IconBadge(
                    icon,
                    tone = c.badge,
                    size = dimens.iconBadgeSmall,
                    containerColor = if (large) c.badge.container else Tokens.Surface,
                )
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.Call, contentDescription = null, tint = c.call, modifier = Modifier.size(dimens.icon))
            }
            PhoneNumberText(number, c.number, Modifier.fillMaxWidth())
            KoText(label, MaterialTheme.typography.bodyMedium, color = c.label)
            if (note != null) {
                if (large) StatusTag(note, StatusKind.Info) else KoText(note, MaterialTheme.typography.bodySmall, color = c.note)
            }
        }
    }
}
