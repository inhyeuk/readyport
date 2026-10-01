package com.readyport.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.components.ChoiceCard
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.PhotoBox
import com.readyport.ui.components.PhotoTextArea
import com.readyport.ui.components.Photos
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/**
 * 첫 실행 질문 (PRD 3.2, DESIGN_SPEC 6-00). '네'면 쉬운 모드를 켠다. 누가 쓸지 모르므로 앱은 이 화면을 쉬운 모드 테마로 그린다.
 * 순서: 사진 히어로(앱 심볼 + 앱 이름 + 질문 — 모두 스크림 글자 영역 안) → 설명 → 큰 선택 카드 2장.
 * 선택 카드는 카드 전체가 버튼이고 이름은 제목(`first_run_yes`/`first_run_no`) + 한 줄 설명이다.
 */
@Composable
fun FirstRunScreen(onAnswer: (firstTimeAbroad: Boolean) -> Unit) {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.gap),
    ) {
        PhotoBox(Photos.Home, minHeight = heroMinHeight(), shape = MaterialTheme.shapes.extraLarge) {
            PhotoTextArea {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppSymbol(size = if (dimens.easyMode) 48.dp else 40.dp)
                    Text(
                        stringResource(R.string.home_brand),
                        style = MaterialTheme.typography.labelLarge,
                        color = OnDark.content,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.first_run_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = OnDark.content,
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                )
            }
        }
        Text(
            stringResource(R.string.first_run_body),
            style = MaterialTheme.typography.bodyLarge,
            color = Tokens.Ink,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        ChoiceCard(
            title = stringResource(R.string.first_run_yes),
            body = stringResource(R.string.first_run_easy_preview),
            icon = Icons.Outlined.TextIncrease,
            onClick = { onAnswer(true) },
            emphasized = true,
            preview = { SizePreview() },
        )
        ChoiceCard(
            title = stringResource(R.string.first_run_no),
            body = stringResource(R.string.first_run_basic_preview),
            icon = Icons.Outlined.TravelExplore,
            onClick = { onAnswer(false) },
        )
    }
}

/**
 * 히어로 최소 높이: 기본 220dp, 글자 200%면 160dp(6-00). 창이 아주 낮으면(640dp 미만) 140dp로 줄여
 * 두 선택 카드가 스크롤 없이 첫 화면에 들어오게 한다. 글자가 많아지면 사진 칸은 내용에 맞춰 커진다.
 */
@Composable
private fun heroMinHeight(): Dp {
    val density = LocalDensity.current
    val windowHeightDp = LocalWindowInfo.current.containerSize.height / density.density
    return when {
        density.fontScale >= 1.5f -> 160.dp
        windowHeightDp > 0f && windowHeightDp < 640f -> 140.dp
        else -> 220.dp
    }
}

/** '처음이에요' 카드의 미리보기: 작은 `가` → 큰 `가` (글자가 커진다는 뜻). ChoiceCard가 TalkBack에서 숨긴다 */
@Composable
private fun SizePreview() {
    val glyph = stringResource(R.string.first_run_preview_glyph)
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(glyph, style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = Tokens.Accent,
            modifier = Modifier.size(LocalDimens.current.iconSmall),
        )
        Text(glyph, style = MaterialTheme.typography.headlineMedium, color = Tokens.Accent)
    }
}

/**
 * 앱 심볼: 런처 아이콘과 같은 모양(BrandBlue→Navy 원 + 런처 전경 그림). 장식이라 TalkBack에서 숨긴다.
 * 런처 전경은 108dp 캔버스의 가운데 72dp만 원으로 보이므로(적응형 아이콘 규칙) 원 지름의 1.5배로 그려 가장자리를 잘라 낸다.
 * 홈 히어로의 앱 이름 줄에서도 쓴다.
 */
@Composable
internal fun AppSymbol(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Tokens.BrandBlue, Tokens.Navy)))
            .border(1.dp, Tokens.White80, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}
