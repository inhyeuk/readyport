package com.readyport.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.Tokens

// ======================= 목록 행 (DESIGN_SPEC 4.10, 재검토 R3·R4) =======================

/** 행 오른쪽 끝 */
sealed interface RowTrailing {
    /** 다음 화면으로 (onClick이 있을 때만 보인다) */
    data object Chevron : RowTrailing

    /** 바깥 앱·사이트 열기 */
    data object External : RowTrailing

    data object None : RowTrailing

    /** 켬·끔. 줄 전체가 Role.Switch 토글이고 Switch 자체는 콜백 null(초점 한 번) */
    data class Switch(val checked: Boolean, val onChange: (Boolean) -> Unit) : RowTrailing

    /** 예: ImportVerdictBadge(06), OfflinePin 칩(01) */
    data class Custom(val content: @Composable () -> Unit) : RowTrailing
}

/**
 * 설정·목록 한 줄: 아이콘 배지 + 제목·설명 + 오른쪽 끝. 글자와 스위치 사이 16dp 보장.
 * 안쪽 여백은 토큰 listRowPadding(가로 20/24 = cardPadding)·listRowPaddingVertical(16) — ListGroup 행의 배지가
 * 다른 카드 내용과 같은 시작선에 선다(재검토 R4).
 * Switch 행은 줄 전체 toggleable(Role.Switch), onClick 행은 clickable(Role.Button).
 * 설명은 제목 시작선에서 끝 요소 아래까지 넓힌다. 큰 글자 배치(LayoutClass.Stacked)에서 배지가 있는 행은 **제목 길이와 관계없이**
 * 배지·끝 요소만 윗줄에 두고 제목·설명은 폭 전체로 내린다([isStackedListRow] — 한 묶음 안 행마다 모양이 섞이지 않게, 재검토2 ④#6).
 * 배지가 없는 행은 제목이 한 줄에 다 들어가지 않을 때만. 설명을 숨기지 않는다([BadgeTitleLayout]).
 * [extra]: 설명 아래 덧붙이는 장식(예: 쉬운 모드 `가 → 가` 미리보기 — 부르는 쪽이 clearAndSetSemantics로 숨긴다).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    body: String? = null,
    tone: BadgeTone = BadgeTone.Accent,
    trailing: RowTrailing = RowTrailing.Chevron,
    onClick: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val dimens = LocalDimens.current
    val interaction = when {
        trailing is RowTrailing.Switch -> Modifier.toggleable(value = trailing.checked, role = Role.Switch, onValueChange = trailing.onChange)
        onClick != null -> Modifier.clickable(role = Role.Button, onClick = onClick)
        else -> Modifier.semantics(mergeDescendants = true) {}
    }
    val below: (@Composable () -> Unit)? = if (body != null || extra != null) {
        {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (body != null) KoText(body, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                extra?.invoke()
            }
        }
    } else {
        null
    }
    val texts: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KoText(title, MaterialTheme.typography.titleMedium, color = Tokens.Ink, glueShort = true)
            below?.invoke()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.listRowMinHeight)
            .then(interaction)
            .padding(horizontal = dimens.listRowPadding, vertical = dimens.listRowPaddingVertical),
        // 여러 줄 글(설명이 있는 행) 옆의 배지·끝 요소는 위를 맞춘다 (4.2)
        verticalAlignment = if (below != null) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 큰 글자 배치에서 배지가 있는 행은 묶음 안 모든 행이 같은 모양(배지·끝 요소 윗줄, 글 폭 전체 — 재검토2 ④#6)
        val wholeStack = isStackedListRow(hasBadge = icon != null)
        if (trailing is RowTrailing.Custom && wholeStack) {
            BadgeTitleLayout(
                title = { KoText(title, MaterialTheme.typography.titleMedium, color = Tokens.Ink, glueShort = true) },
                modifier = Modifier.weight(1f),
                badge = icon?.let { { IconBadge(it, tone = tone) } },
                trailing = trailing.content,
                below = below,
                stack = true,
                gap = 16.dp,
                forceStack = true,
            )
        } else if (trailing is RowTrailing.Custom) {
            if (icon != null) IconBadge(icon, tone = tone)
            // 배지·칩(ImportVerdictBadge 등)이 제목을 쪼갤 만큼 폭이 모자라면 글 아래 줄로 (4.10, 7장 7번)
            TrailingFlow(
                trailing = trailing.content,
                modifier = Modifier.weight(1f),
                gap = 16.dp,
                centerVertically = below == null,
                main = texts,
            )
        } else {
            val end: (@Composable () -> Unit)? = when (trailing) {
                RowTrailing.Chevron -> if (onClick != null) {
                    { Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null, tint = Tokens.InkTertiary) }
                } else {
                    null
                }
                RowTrailing.External -> {
                    {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = Tokens.Accent,
                            modifier = Modifier.size(textIconSize(20.dp, MaterialTheme.typography.titleMedium)),
                        )
                    }
                }
                RowTrailing.None -> null
                is RowTrailing.Switch -> {
                    { Switch(checked = trailing.checked, onCheckedChange = null, colors = appSwitchColors()) }
                }
                is RowTrailing.Custom -> null // 위에서 TrailingFlow로 그렸다
            }
            BadgeTitleLayout(
                title = { KoText(title, MaterialTheme.typography.titleMedium, color = Tokens.Ink, glueShort = true) },
                modifier = Modifier.weight(1f),
                badge = icon?.let { { IconBadge(it, tone = tone) } },
                trailing = end,
                below = below,
                stack = isStackedLayout(),
                gap = 16.dp,
                forceStack = wholeStack,
            )
        }
    }
}

/**
 * ListGroup 안 행 사이 구분선: 아이콘 뒤부터 (start = listRowPadding + iconBadge + 16).
 * [indent] 기본값: 큰 글자 배치(Stacked)면 글이 폭 전체를 쓰므로 들여쓰지 않는다.
 */
@Composable
fun ListDivider(indent: Boolean = false) {
    // 2026-10-09 사장님 요청: 항목이 섞여 보이지 않게 — 기본은 폭 전체 구분선, 항목 테두리와 같은 색
    val dimens = LocalDimens.current
    val start = if (indent) dimens.listRowPadding + dimens.iconBadge + 16.dp else 0.dp
    HorizontalDivider(Modifier.padding(start = start), thickness = 1.dp, color = Tokens.CardEdge)
}

/**
 * 제목(titleSmall InkSecondary) + 흰 그림자 카드 안의 행들. 행 사이에는 ListDivider()를 넣는다.
 * 행이 아닌 설명 Text(bodyMedium)도 넣을 수 있다(07 videos_terms).
 * 인자 순서는 스펙 4.10 그대로(title, modifier) — `ListGroup("제목") { }`처럼 위치로도 부를 수 있게.
 * (Compose lint ModifierParameter는 modifier가 첫 선택 인자이길 권하지만 스펙 시그니처를 우선해 이 함수에서만 끈다)
 */
@Suppress("ModifierParameter")
@Composable
fun ListGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            // 제목은 카드 가장자리(화면 여백)와 같은 시작선 — 4dp 들여쓰기 없음 (F 묶음 지적)
            KoText(title, MaterialTheme.typography.titleSmall, color = Tokens.InkSecondary, heading = true)
        }
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier.fillMaxWidth().cardShadow(shape),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/**
 * '급할 때는 도움' 바로가기 한 줄 (홈 6-01 ⑩ · 내 여행 6-09~13 공통, 재검토 R4): 흰 그림자 카드 안 ListRow 하나 —
 * SOS 배지(Help) + 제목 + 설명(인터넷 없이도 보여요) + 셰브론, 행 전체가 버튼. 이름은 제목 + 설명.
 * 큰 글자·쉬운 모드에서도 설명을 숨기지 않는다 — 배치만 바뀐다(배지·셰브론 윗줄, 글 폭 전체).
 */
@Composable
fun HelpShortcutRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListGroup(modifier = modifier) {
        ListRow(
            title = stringResource(R.string.help_shortcut_title),
            icon = Icons.Outlined.Sos,
            tone = BadgeTone.Help,
            body = stringResource(R.string.today_help_body),
            onClick = onClick,
        )
    }
}

/** [KeyValueRow] 값의 글자 크기 */
enum class ValueStyle {
    /** titleMedium Bold (기본) */
    Default,

    /** titleLarge — 여권 카드처럼 값이 주인공인 곳 */
    Large,

    /** statSmall — 심사관이 한눈에 읽는 확인 번호 */
    Stat,
}

/**
 * 라벨-값 한 덩어리 (재검토 R3 — 17·20·21·24·26·30·32가 함께 쓴다). 라벨과 값을 한 번에 읽는다(mergeDescendants).
 * 값을 오른쪽 좁은 칸에 두지 않아 200%에서도 겹치지 않는다. label·subLabel·value는 각각 단독 Text 노드
 * (테스트가 `ERIKSSON`, `Family Name · นามสกุล`을 그대로 찾는다).
 *
 * 슬롯
 * - [leading]: 앞 아이콘 배지(작은 Neutral 배지 — 예약 서류 사실 행).
 * - [trailing]: 끝 요소(복사 버튼 등). 옆에 두면 라벨·값이 더 꺾일 만큼 폭이 모자라면 값 아래 줄로 내려간다(실제 폭 기준).
 *   큰 글자 배치(Stacked)에서는 행마다 따지지 않고 언제나 값 아래 줄(한 목록 안 같은 모양 — 재검토2 ④#6).
 *   누를 수 있는 끝 요소는 자기 이름을 가진다(이 행에 합쳐 읽히지 않음).
 * - [badge]: 라벨 옆 작은 상태 태그(폭이 모자라면 라벨 아래 줄).
 * - [subLabel]: 영어·현지어 칸 이름. [subLabelInline]이면 라벨 옆에 나란히(들어가지 않으면 다음 줄), [subLabelStyle]로 크게(`현지어 크게`).
 * - [valueStyle]: 값 크기. [supporting]: 값 아래 도움말.
 * - [masked]: 가린 값(`L••••••C3`). 화면 글자는 그대로 두고 TalkBack은 점 대신 `가려 둔 값`으로 읽는다.
 * - [onDark]: 여권 카드(Navy·AccentDeep)·보여 주기 카드 안 — 라벨 White80, 값 Surface.
 */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    badge: (@Composable () -> Unit)? = null,
    onDark: Boolean = false,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    subLabelInline: Boolean = false,
    subLabelStyle: TextStyle? = null,
    valueStyle: ValueStyle = ValueStyle.Default,
    masked: Boolean = false,
    supporting: String? = null,
    verticalPadding: Dp = 8.dp,
) {
    val dimens = LocalDimens.current
    val labelColor = if (onDark) OnDark.secondary else Tokens.InkSecondary
    val valueColor = if (onDark) OnDark.content else Tokens.Ink
    val typography = MaterialTheme.typography
    val valueTextStyle = when (valueStyle) {
        ValueStyle.Default -> typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
        ValueStyle.Large -> typography.titleLarge.copy(fontFeatureSettings = "tnum")
        ValueStyle.Stat -> LocalTypeExtras.current.statSmall
    }
    val maskedLabel = stringResource(R.string.kv_masked_cd)
    val subStyle = subLabelStyle ?: typography.bodySmall
    val subColor = if (subLabelStyle != null && !onDark) Tokens.Ink else labelColor
    val labelText: @Composable () -> Unit = {
        if (subLabelInline && subLabel != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KoText(label, typography.titleSmall, Modifier.align(Alignment.CenterVertically), color = labelColor)
                Text(subLabel, style = subStyle, color = subColor, modifier = Modifier.align(Alignment.CenterVertically))
            }
        } else {
            KoText(label, typography.titleSmall, color = labelColor)
        }
    }
    val texts: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (badge != null) {
                // 배지가 라벨을 쪼갤 만큼 폭이 모자라면 라벨 아래 줄로 (200%·쉬운 모드)
                TrailingFlow(trailing = badge, gap = 8.dp, belowGap = 4.dp, centerVertically = true, main = labelText)
            } else {
                labelText()
            }
            if (subLabel != null && !subLabelInline) Text(subLabel, style = subStyle, color = subColor)
            Text(
                value,
                style = valueTextStyle,
                color = valueColor,
                modifier = if (masked) Modifier.semantics { contentDescription = maskedLabel } else Modifier,
            )
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) IconBadge(leading, tone = BadgeTone.Neutral, size = dimens.iconBadgeSmall)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // 끝 요소를 옆에 둘지는 라벨·값만 보고 정한다 — 긴 도움말 때문에 버튼이 행마다 들쭉날쭉 내려가지 않게.
            // 큰 글자 배치(Stacked)에서는 모든 행의 끝 요소(복사 버튼)를 값 아래 줄에 — 라벨 길이에 따라 한 카드 안에서
            // 옆·아래가 갈리지 않게(재검토2 ④#6, 21 `국적 Nationality/Citizenship`만 아래로 내려가던 문제)
            if (trailing != null && isStackedLayout()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    texts()
                    trailing()
                }
            } else if (trailing != null) {
                TrailingFlow(trailing = trailing, gap = 8.dp, belowGap = 4.dp, centerVertically = true, main = texts)
            } else {
                texts()
            }
            // 도움말은 그 아래 폭 전체
            if (supporting != null) KoText(supporting, typography.bodySmall, color = labelColor)
        }
    }
}
