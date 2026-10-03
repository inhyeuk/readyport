package com.readyport.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// 탭 넷: 둘러보기 · 내 여행 · 도움 · 설정 (2026-10-03 — 예전 '홈'은 여행 흐름 조각을 다 들고 있어서 내 여행 탭과 겹쳤다)

/** [selectedIcon]: 선택된 탭에서만 쓰는 채운 아이콘 (Filled는 '켜짐·선택' 상태에만, D12) */
enum class Tab(@StringRes val label: Int, val icon: ImageVector, val route: Any, val selectedIcon: ImageVector = icon) {
    /** 둘러보기 = 어디 갈까(생각 단계). 여행 흐름은 내 여행 탭이 맡는다 (2026-10-03 부록 H) */
    Home(R.string.tab_explore, Icons.Outlined.TravelExplore, HomeRoute, Icons.Filled.TravelExplore),
    Trip(R.string.tab_trip, Icons.Outlined.Luggage, TripsRoute, Icons.Filled.Luggage),
    Help(R.string.tab_help, Icons.Outlined.SupportAgent, HelpRoute, Icons.Filled.SupportAgent),
    Settings(R.string.tab_settings, Icons.Outlined.Settings, SettingsRoute, Icons.Filled.Settings),

    /** 자녀 폰 모드에서만 쓰는 탭 */
    Present(R.string.tab_present, Icons.Outlined.QrCode2, PresentRoute, Icons.Filled.QrCode2);

    companion object {
        val Main = listOf(Home, Trip, Help, Settings)
        /** 자녀 폰: 자기 QR과 도움만 (PRD 3.3) */
        val Child = listOf(Present, Help)
    }
}

/** 선택 탭 아이콘 뒤 알약 인디케이터 크기 (DESIGN_SPEC 6장 공통 틀) — 글자가 아니라 아이콘만 품는다 */
private val IndicatorWidth = 64.dp
private val IndicatorHeight = 32.dp

/** 기본 모드 탭 라벨 autoSize 하한 — 기존 예외(DESIGN_SPEC 7장 7번)는 기본 모드에서만. 쉬운 모드는 줄이지 않고 2줄 */
private val BasicLabelMinSize = 10.sp

/**
 * 하단 탭 4개: 둘러보기 · 내 여행 · 도움 · 설정 (DESIGN_SPEC 6장 공통 틀, 부록 H).
 * Material NavigationBar는 높이가 고정이라 글자를 크게 키우면 라벨이 잘린다.
 * 그래서 높이가 내용에 맞춰 늘어나는 탭 막대를 직접 그린다.
 * - 선택 탭: 아이콘 뒤 64×32dp 알약(AccentSoft, 도움 탭은 HelpSoft) + 채운 아이콘 + 굵은 라벨 — 색 말고도 모양·굵기로 구분
 * - 도움 탭은 선택 여부와 상관없이 따뜻한 색(Help)으로 항상 구분한다(PRD). 비선택이면 굵기만 Medium
 * - 라벨: 기본 모드는 칸보다 넓으면 10sp까지 줄여 한 줄, 쉬운 모드(18sp)는 줄이지 않고 2줄로 넘긴다(D19)
 * - 눌림 물결(ripple)은 칸 전체 사각형이 아니라 알약 안에만 그린다 — 16dp·원형 모서리 체계와 맞게(칸 전체가 누르는 영역인 것은 그대로)
 */
@Composable
fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit, tabs: List<Tab> = Tab.Main) {
    Surface(color = Tokens.Surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = Tokens.LineSoft)
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).selectableGroup()) {
                tabs.forEach { tab -> TabItem(tab, selected = tab == selected, onClick = { onSelect(tab) }) }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val labelStyle = MaterialTheme.typography.labelSmall
    val help = tab == Tab.Help
    val color: Color = when {
        help -> Tokens.Help
        selected -> Tokens.Accent
        else -> Tokens.InkSecondary
    }
    val interaction = remember { MutableInteractionSource() }
    val style = labelStyle.copy(
        color = color,
        textAlign = TextAlign.Center,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = dimens.buttonHeight + 8.dp)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            // 위아래 4dp: 인디케이터(32dp)가 들어와도 막대 높이가 예전(아이콘 24 + 위아래 8)과 같다 — 다른 탭 첫 화면 예산 유지
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(IndicatorWidth, IndicatorHeight)
                .clip(CircleShape)
                .indication(interaction, ripple())
                .background(
                    color = when {
                        !selected -> Color.Transparent
                        help -> Tokens.HelpSoft
                        else -> Tokens.AccentSoft
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) tab.selectedIcon else tab.icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(dimens.icon),
            )
        }
        if (dimens.easyMode) {
            Text(stringResource(tab.label), style = style)
        } else {
            BasicText(
                text = stringResource(tab.label),
                style = style,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = BasicLabelMinSize, maxFontSize = labelStyle.fontSize),
            )
        }
    }
}
