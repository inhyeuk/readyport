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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.components.ChoiceCard
import com.readyport.ui.components.KoText
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.PhotoBox
import com.readyport.ui.components.PhotoTextArea
import com.readyport.ui.components.Photos
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.koDisplay
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/**
 * 첫 실행 질문 (PRD 3.2, DESIGN_SPEC 6-00). '네'면 쉬운 모드를 켠다. 누가 쓸지 모르므로 앱은 이 화면을 쉬운 모드 테마로 그린다.
 * 순서: 사진 히어로(브랜드 묶음 — 앱 심볼 + 앱 이름 + 핵심 가치 한 줄, 그리고 질문 — 모두 스크림 글자 영역 안) → 설명 → 큰 선택 카드 2장.
 * 브랜드 묶음은 앱을 처음 여는 순간 '무엇을 해 주는 앱인지'를 한 줄로 보여 주는 브랜드 순간이다(재검토 R19) — 홈 히어로와 같은 약속 문장.
 * 선택 카드는 카드 전체가 버튼이고 이름은 제목(`first_run_yes`/`first_run_no`) + 한 줄 설명이다.
 * 큰 글자 배치(LayoutClass.Stacked — Layout.kt 한 곳의 판정)에서는 히어로 질문을 headlineMedium으로 한 단계 낮추고, 가치 문장을
 * 심볼 옆 좁은 칸 대신 그 아래 폭 전체로 옮긴다. 선택 카드는 공용 ChoiceCard가 큰 글자에서 배지를
 * 제목 위 줄로 올려 제목이 카드 폭 전체를 쓰게 한다(`처음이에/요` 방지). 글은 어절 단위로만 줄을 바꾼다(KoText).
 */
@Composable
fun FirstRunScreen(onAnswer: (firstTimeAbroad: Boolean) -> Unit) {
    val dimens = LocalDimens.current
    val stacked = isStackedLayout()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.gap),
    ) {
        PhotoBox(Photos.Home, minHeight = heroMinHeight(stacked), shape = MaterialTheme.shapes.extraLarge) {
            PhotoTextArea {
                BrandLockup(stacked)
                KoText(
                    text = stringResource(R.string.first_run_title),
                    style = if (stacked) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    color = OnDark.content,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
        }
        // 가로 여백 없음 — 본문 왼쪽 끝이 히어로·카드 가장자리와 맞는다
        KoText(
            stringResource(R.string.first_run_body),
            style = MaterialTheme.typography.bodyLarge,
            color = Tokens.Ink,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        // 큰 글자에서는 공용 ChoiceCard가 배지·셰브론을 윗줄에, 제목·설명을 카드 폭 전체에 둔다 (BUNDLE_A_NOTES ⑥, 2단계 통합)
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
 * 브랜드 묶음 (재검토 R19): 앱 심볼(런처 아이콘과 같은 모양) + `레디포트` + 핵심 가치 한 줄(`home_value_prop`).
 * 사진 위 글자라 모두 Surface(onDark — 스크림 영역 안). 큰 글자 배치에서는 가치 문장을 심볼 옆 좁은 칸에서 꺾지 않고 아래 폭 전체로.
 */
@Composable
private fun BrandLockup(stacked: Boolean) {
    val dimens = LocalDimens.current
    val symbol = if (dimens.easyMode) 64.dp else 56.dp
    val name: @Composable () -> Unit = {
        Text(stringResource(R.string.home_brand), style = MaterialTheme.typography.titleLarge, color = OnDark.content)
    }
    val value: @Composable () -> Unit = { ValuePropText(MaterialTheme.typography.bodyMedium) }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppSymbol(size = symbol)
                name()
            }
            value()
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppSymbol(size = symbol)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                name()
                value()
            }
        }
    }
}

/**
 * 히어로 최소 높이: 기본 220dp, 큰 글자 배치면 160dp(6-00). 창이 아주 낮으면(640dp 미만) 140dp로 줄여
 * 두 선택 카드가 스크롤 없이 첫 화면에 들어오게 한다. 글자가 많아지면 사진 칸은 내용에 맞춰 커진다.
 */
@Composable
private fun heroMinHeight(stacked: Boolean): Dp {
    val density = LocalDensity.current
    val windowHeightDp = LocalWindowInfo.current.containerSize.height / density.density
    return when {
        stacked -> 160.dp
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
        // 화살표도 옆 글자를 따라 커진다(최대 1.5배) — 큰 글자에서 점처럼 작아지지 않게 (재검토2 ①#14)
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = Tokens.Accent,
            modifier = Modifier.size(textIconSize(LocalDimens.current.iconSmall, MaterialTheme.typography.bodyMedium)),
        )
        Text(glyph, style = MaterialTheme.typography.headlineMedium, color = Tokens.Accent)
    }
}

/**
 * 핵심 가치 문장(`home_value_prop`) — 첫 실행 브랜드 묶음과 홈 히어로가 같이 쓴다(사진 스크림 위 흰 글자).
 * 쉼표 뒤에서 줄을 바꿔 `입국 카드 칸은 앱이 채우고,` / `제출만 직접 눌러요` 두 뜻 덩어리로 보인다
 * (균형·채움 줄바꿈은 `앱이 / 채우고`, `제출만 / 직접`처럼 덩어리 가운데서 꺾었다). 그렇게 해서 두 줄을 넘으면
 * (좁은 창·큰 글자) 줄이 더 늘지 않게 보통 줄바꿈으로 되돌린다. 의미 글자(TalkBack·테스트)는 원문 그대로다(KoText `display`).
 */
@Composable
internal fun ValuePropText(style: TextStyle, modifier: Modifier = Modifier) {
    val text = stringResource(R.string.home_value_prop)
    var split by remember(text) { mutableStateOf(true) }
    KoText(
        text,
        style,
        modifier,
        color = OnDark.content,
        display = if (split) koDisplay(text).replaceFirst(", ", ",\n") else null,
        onTextLayout = { if (split && it.lineCount > 2) split = false },
    )
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
