package com.readyport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 배너 (DESIGN_SPEC 4.4) =======================

/**
 * 누를 수 없는 폭 전체 띠의 색. D21: Notice는 AccentSoft 채움을 쓰지 않는다 — 흰 바탕 + 4dp Accent 막대
 * (누를 수 있는 tonal 버튼과 헷갈리지 않게).
 */
enum class BannerTone(val bg: Color, val bar: Color, val icon: Color, val text: Color) {
    Notice(Tokens.Surface, Tokens.Accent, Tokens.Accent, Tokens.Ink),
    Caution(Tokens.CautionBg, Tokens.CautionBorder, Tokens.CautionText, Tokens.CautionText),
    Danger(Tokens.DangerBg, Tokens.DangerText, Tokens.DangerText, Tokens.DangerText),
    Success(Tokens.SuccessBg, Tokens.SuccessText, Tokens.SuccessText, Tokens.SuccessText),
}

/**
 * 고지·안내 띠 (정부 비제휴, 제휴 고지, YouTube 고지, 대행 사이트 경고 등).
 * 누를 수 없다(clickable 없음, 알약 모양 아님, 테두리 없음). 글자 수 제한 없음, 높이 자동.
 * [text]는 기존 문자열을 그대로 넘긴다 — `onNodeWithText(guide_not_affiliated)` 등이 한 Text 노드로 찾는다.
 */
@Composable
fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    tone: BannerTone = BannerTone.Notice,
    title: String? = null,
    secondLine: String? = null,
    secondIcon: ImageVector? = null,
) {
    val dimens = LocalDimens.current
    Surface(color = tone.bg, contentColor = tone.text, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .startBar(tone.bar)
                .padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null, tint = tone.icon, modifier = Modifier.size(dimens.icon))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = tone.text)
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = tone.text,
                )
                if (secondLine != null) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (secondIcon != null) {
                            Icon(secondIcon, contentDescription = null, tint = tone.icon, modifier = Modifier.padding(top = 1.dp).size(20.dp))
                        }
                        Text(secondLine, style = MaterialTheme.typography.bodyMedium, color = tone.text)
                    }
                }
            }
        }
    }
}

/**
 * "내 정보는 이 휴대폰에만 저장돼요" — 보안 화면 맨 위 (기존 LocalOnlyBanner를 옮겨 다시 꾸밈, 문구는 그대로).
 * [compact]: Lock 아이콘 + `settings_local_only_title` 한 줄 (21·25·26·27, 입국 카드 화면).
 */
@Composable
fun SecurityBanner(modifier: Modifier = Modifier, compact: Boolean = false) {
    val dimens = LocalDimens.current
    val title = stringResource(R.string.settings_local_only_title)
    if (compact) {
        Surface(color = Tokens.Navy, contentColor = Tokens.Surface, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Tokens.Surface, modifier = Modifier.size(dimens.icon))
                Text(title, style = MaterialTheme.typography.titleSmall, color = Tokens.Surface, modifier = Modifier.weight(1f))
            }
        }
    } else {
        Surface(color = Tokens.Navy, contentColor = Tokens.Surface, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Icons.Outlined.Lock, tone = BadgeTone.OnDark, shape = CircleShape)
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Tokens.Surface, modifier = Modifier.weight(1f))
                }
                Text(stringResource(R.string.settings_local_only_body), style = MaterialTheme.typography.bodyLarge, color = Tokens.Surface)
            }
        }
    }
}

/** 오프라인 배너 (PRD 5.1): 남색 띠, 화면 맨 위. 누를 수 없다 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Tokens.Navy)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Tokens.Surface, modifier = Modifier.size(20.dp))
        Text(
            text = stringResource(R.string.offline_banner),
            color = Tokens.Surface,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
    }
}
