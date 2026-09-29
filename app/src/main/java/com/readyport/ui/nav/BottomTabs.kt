package com.readyport.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

enum class Tab(@StringRes val label: Int, val icon: ImageVector, val route: Any) {
    Home(R.string.tab_home, Icons.Outlined.Home, HomeRoute),
    Trip(R.string.tab_trip, Icons.Outlined.Luggage, TodayRoute),
    Help(R.string.tab_help, Icons.Outlined.SupportAgent, HelpRoute),
    Settings(R.string.tab_settings, Icons.Outlined.Settings, SettingsRoute),

    /** 자녀 폰 모드에서만 쓰는 탭 */
    Present(R.string.tab_present, Icons.Outlined.QrCode2, PresentRoute);

    companion object {
        val Main = listOf(Home, Trip, Help, Settings)
        /** 자녀 폰: 자기 QR과 도움만 (PRD 3.3) */
        val Child = listOf(Present, Help)
    }
}

/**
 * 하단 탭 4개: 홈 · 내 여행 · 도움 · 설정.
 * Material NavigationBar는 높이가 고정이라 글자를 크게 키우면 라벨이 잘린다.
 * 그래서 높이가 내용에 맞춰 늘어나고, 라벨이 칸보다 넓으면 글자를 줄여 맞추는 탭 막대를 직접 그린다.
 */
@Composable
fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit, tabs: List<Tab> = Tab.Main) {
    val dimens = LocalDimens.current
    val labelStyle = MaterialTheme.typography.labelMedium
    Surface(color = Tokens.Surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = Tokens.LineSoft)
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                tabs.forEach { tab ->
                    val isSelected = tab == selected
                    // 도움 탭은 선택 여부와 상관없이 따뜻한 색으로 항상 구분한다
                    val color: Color = when {
                        tab == Tab.Help -> Tokens.Help
                        isSelected -> Tokens.Accent
                        else -> Tokens.InkSecondary
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = dimens.buttonHeight + 8.dp)
                            .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                    ) {
                        Icon(tab.icon, contentDescription = null, tint = color)
                        BasicText(
                            text = stringResource(tab.label),
                            style = labelStyle.copy(
                                color = color,
                                textAlign = TextAlign.Center,
                                fontWeight = if (isSelected || tab == Tab.Help) FontWeight.Bold else FontWeight.Medium,
                            ),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = labelStyle.fontSize),
                        )
                    }
                }
            }
        }
    }
}
