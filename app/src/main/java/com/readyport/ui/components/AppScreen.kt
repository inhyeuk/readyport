package com.readyport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.LocalDimens

/** 어느 화면에서나 쓰는 동작. 화면마다 콜백을 넘기지 않도록 CompositionLocal로 전달한다. */
@Immutable
data class AppActions(
    val goHome: () -> Unit = {},
    val speak: (String) -> Unit = {},
)

val LocalAppActions = staticCompositionLocalOf { AppActions() }

/**
 * 모든 탭 화면의 공통 틀.
 * - 제목은 TalkBack 제목(heading)으로 표시한다.
 * - 쉬운 모드에서는 '처음으로'와 '소리로 듣기' 버튼을 모든 화면에 둔다 (PRD 3.2).
 * - 스와이프 동작은 쓰지 않는다. 세로 스크롤만 있다.
 */
@Composable
fun AppScreen(
    title: String,
    speech: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val dimens = LocalDimens.current
    val actions = LocalAppActions.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = dimens.gap),
        verticalArrangement = Arrangement.spacedBy(dimens.gap),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    headerActions()
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (dimens.easyMode) {
            item(key = "easy-actions") {
                // 글자를 키워 한 줄에 둘이 안 들어가면 버튼이 아래 줄로 내려간다(글자가 쪼개지지 않게)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(dimens.gap),
                    verticalArrangement = Arrangement.spacedBy(dimens.gap / 2),
                ) {
                    EasyActionButton(
                        text = stringResource(R.string.action_home),
                        onClick = actions.goHome,
                        icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    )
                    EasyActionButton(
                        text = stringResource(R.string.action_listen),
                        onClick = { actions.speak(speech) },
                        icon = { Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null) },
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun EasyActionButton(
    text: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = LocalDimens.current.buttonHeight),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.size(24.dp)) { icon() }
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, softWrap = false)
        }
    }
}

/** 화면 가득 너비의 주 버튼. 쉬운 모드에서는 높이 64dp (PRD 3.2: 56dp 이상). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.ButtonColors = androidx.compose.material3.ButtonDefaults.buttonColors(),
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
