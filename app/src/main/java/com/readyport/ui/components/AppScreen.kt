package com.readyport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.LocalDimens

/** 어느 화면에서나 쓰는 동작. 화면마다 콜백을 넘기지 않도록 CompositionLocal로 전달한다. */
@Immutable
data class AppActions(
    val goHome: () -> Unit = {},
    val speak: (String) -> Unit = {},
    /** 이전 화면으로 (탭 첫 화면이 아닐 때만 제목 옆 뒤로 버튼이 보인다) */
    val goBack: () -> Unit = {},
)

val LocalAppActions = staticCompositionLocalOf { AppActions() }

/** 지금 화면이 탭 첫 화면이 아니라서 뒤로 버튼을 보여야 하는지 */
val LocalShowBack = staticCompositionLocalOf { false }

/**
 * 모든 탭 화면의 공통 틀.
 * - 제목은 TalkBack 제목(heading)으로 표시한다. [icon]이 있으면 제목 앞에 아이콘 배지.
 * - 쉬운 모드에서는 '처음으로'와 '소리로 듣기' 버튼을 모든 화면에 둔다 (PRD 3.2).
 * - 스와이프 동작은 쓰지 않는다. 세로 스크롤만 있다.
 * - [state]를 넘기면 화면이 스크롤 위치를 다룰 수 있고, [keyIndex]를 넘기면 AppScreen이 넣는 header·easy-actions와
 *   화면이 넣는 모든 item의 key가 순서대로 기록된다 → `state.scrollToKey(keyIndex, "key")` (DESIGN_SPEC 4.1)
 */
@Composable
fun AppScreen(
    title: String,
    speech: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    /** 사진 머리글처럼 기본 제목 줄 대신 쓸 머리글. 제목(heading) 표시는 머리글이 맡는다 */
    header: (@Composable () -> Unit)? = null,
    icon: ImageVector? = null,
    state: LazyListState = rememberLazyListState(),
    keyIndex: KeyIndex? = null,
    content: LazyListScope.() -> Unit,
) {
    val dimens = LocalDimens.current
    val actions = LocalAppActions.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = dimens.gap),
        verticalArrangement = Arrangement.spacedBy(dimens.gap),
    ) {
        val scope = keyIndex?.track(this) ?: this
        if (header != null) {
            scope.item(key = "header") { header() }
        } else {
            scope.item(key = "header") { TitleBlock(title, subtitle, icon, headerActions) }
        }
        if (dimens.easyMode) {
            scope.item(key = "easy-actions") {
                // 글자를 키워 한 줄에 둘이 안 들어가면 버튼이 아래 줄로 내려간다(글자가 쪼개지지 않게)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(dimens.gap),
                    verticalArrangement = Arrangement.spacedBy(dimens.gap / 2),
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.action_home),
                        onClick = actions.goHome,
                        icon = Icons.Outlined.Home,
                        fillWidth = false,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.action_listen),
                        onClick = { actions.speak(speech) },
                        icon = Icons.AutoMirrored.Outlined.VolumeUp,
                        fillWidth = false,
                    )
                }
            }
        }
        scope.content()
    }
}

@Composable
private fun TitleBlock(title: String, subtitle: String?, icon: ImageVector?, headerActions: @Composable RowScope.() -> Unit) {
    val dimens = LocalDimens.current
    val actions = LocalAppActions.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (LocalShowBack.current) {
                IconButton(onClick = actions.goBack, modifier = Modifier.minTouchSize()) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        modifier = Modifier.size(if (dimens.easyMode) 32.dp else 24.dp),
                    )
                }
            }
            if (icon != null) IconBadge(icon)
            // 오른쪽 칩·버튼(headerActions)이 제목을 쪼갤 만큼 폭이 모자라면 제목 아래 줄로 (F 묶음 지적 — 제목이 한 글자씩 세로로 쪼개짐)
            TrailingFlow(
                trailing = { Row(verticalAlignment = Alignment.CenterVertically) { headerActions() } },
                modifier = Modifier.weight(1f),
                gap = 8.dp,
                centerVertically = true,
            ) {
                KoText(title, MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, heading = true, glueShort = true)
            }
        }
        if (subtitle != null) {
            KoText(subtitle, MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
