package com.readyport.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 목록 행 (DESIGN_SPEC 4.10) =======================

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
 * Switch 행은 줄 전체 toggleable(Role.Switch), onClick 행은 clickable(Role.Button).
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
) {
    val dimens = LocalDimens.current
    val interaction = when {
        trailing is RowTrailing.Switch -> Modifier.toggleable(value = trailing.checked, role = Role.Switch, onValueChange = trailing.onChange)
        onClick != null -> Modifier.clickable(role = Role.Button, onClick = onClick)
        else -> Modifier.semantics(mergeDescendants = true) {}
    }
    val texts: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Tokens.Ink)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.listRowMinHeight)
            .then(interaction)
            .padding(16.dp),
        // 여러 줄 글(설명이 있는 행) 옆의 배지·끝 요소는 위를 맞춘다 (4.2)
        verticalAlignment = if (body != null) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) IconBadge(icon, tone = tone)
        if (trailing is RowTrailing.Custom) {
            // 배지·칩(ImportVerdictBadge 등)이 제목을 쪼갤 만큼 폭이 모자라면 글 아래 줄로 (4.10, 7장 7번)
            TrailingFlow(
                trailing = trailing.content,
                modifier = Modifier.weight(1f),
                gap = 16.dp,
                centerVertically = body == null,
                main = texts,
            )
        } else {
            Box(Modifier.weight(1f)) { texts() }
            when (trailing) {
                RowTrailing.Chevron -> if (onClick != null) {
                    Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null, tint = Tokens.InkTertiary)
                }
                RowTrailing.External -> Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = Tokens.Accent,
                    modifier = Modifier.size(20.dp),
                )
                RowTrailing.None -> Unit
                is RowTrailing.Switch -> Switch(checked = trailing.checked, onCheckedChange = null, colors = appSwitchColors())
                is RowTrailing.Custom -> Unit // 위에서 TrailingFlow로 그렸다
            }
        }
    }
}

/** ListGroup 안 행 사이 구분선: 아이콘 뒤부터 (start = 16 + iconBadge + 16) */
@Composable
fun ListDivider(indent: Boolean = true) {
    val start = if (indent) 16.dp + LocalDimens.current.iconBadge + 16.dp else 0.dp
    HorizontalDivider(Modifier.padding(start = start), thickness = 1.dp, color = Tokens.Line)
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
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Tokens.InkSecondary,
                modifier = Modifier.padding(start = 4.dp).semantics { heading() },
            )
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
 * 라벨-값 한 덩어리 (17·24·25 공유). 라벨과 값을 한 번에 읽는다(mergeDescendants).
 * 값을 오른쪽 좁은 칸에 두지 않아 200%에서도 겹치지 않는다. label·subLabel·value는 각각 단독 Text 노드
 * (테스트가 `ERIKSSON`, `Family Name · นามสกุล`을 그대로 찾는다).
 * [onDark]: 여권 카드(Navy·AccentDeep) 안 — 라벨 White80, 값 Surface.
 */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    badge: (@Composable () -> Unit)? = null,
    onDark: Boolean = false,
) {
    val labelColor = if (onDark) OnDark.secondary else Tokens.InkSecondary
    val valueColor = if (onDark) OnDark.content else Tokens.Ink
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val labelText: @Composable () -> Unit = {
            Text(label, style = MaterialTheme.typography.titleSmall, color = labelColor)
        }
        if (badge != null) {
            // 배지가 라벨을 쪼갤 만큼 폭이 모자라면 라벨 아래 줄로 (200%·쉬운 모드)
            TrailingFlow(trailing = badge, gap = 8.dp, belowGap = 4.dp, centerVertically = true, main = labelText)
        } else {
            labelText()
        }
        if (subLabel != null) Text(subLabel, style = MaterialTheme.typography.bodySmall, color = labelColor)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = valueColor,
        )
    }
}
