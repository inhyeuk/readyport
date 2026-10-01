package com.readyport.ui.components

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
            val textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            val firstStyle = if (title != null) MaterialTheme.typography.titleSmall else textStyle
            val iconSize = textIconSize(dimens.icon, textStyle)
            // 아이콘은 첫 줄 가운데에 (여러 줄 글 옆 — 4.2)
            Icon(icon, contentDescription = null, tint = tone.icon, modifier = Modifier.padding(top = firstLineIconOffset(firstStyle, iconSize)).size(iconSize))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) KoText(title, MaterialTheme.typography.titleSmall, color = tone.text)
                KoText(text, textStyle, color = tone.text)
                if (secondLine != null) {
                    val secondStyle = MaterialTheme.typography.bodyMedium
                    val secondSize = textIconSize(20.dp, secondStyle)
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (secondIcon != null) {
                            Icon(
                                secondIcon,
                                contentDescription = null,
                                tint = tone.icon,
                                modifier = Modifier.padding(top = firstLineIconOffset(secondStyle, secondSize)).size(secondSize),
                            )
                        }
                        KoText(secondLine, secondStyle, color = tone.text)
                    }
                }
            }
        }
    }
}

/** 안심 카드의 약속 한 줄 — 아이콘 + 기존 문구(문구·순서 그대로, 테스트가 찾는 한 Text 노드) */
enum class Assurance(val icon: ImageVector, @StringRes val text: Int) {
    /** 정부 기관과 제휴하지 않았어요 */
    NotAffiliated(Icons.Outlined.Policy, R.string.guide_not_affiliated),

    /** 내 정보는 이 휴대폰에만 저장돼요 */
    LocalOnly(Icons.Outlined.Lock, R.string.settings_local_only_title),

    /** 마지막 제출은 직접 눌러요 */
    SubmitSelf(Icons.Outlined.TouchApp, R.string.country_submit_self),
}

/**
 * 안심 카드 (다듬기 D0 — 재검토2 ①#3·⑤#8): 화면 위에 쌓이던 띠 셋(정부 비제휴 띠 · Navy 보안 띠 · `제출은 직접` 줄)을
 * **한 장**으로 합친다. 흰 바탕 + 왼쪽 4dp Accent 막대(NoticeBanner Notice와 같은 '고지' 모양 — 누를 수 없음, 그림자 없음) 안에
 * 줄마다 아이콘 + 약속 한 문장. 문구·순서는 원래 띠 그대로(원칙 5): ① Policy 비제휴 ② Lock 이 휴대폰에만 ③ TouchApp 제출은 직접.
 * 입국 카드 확인(20)·값 복사해서 넣기(21)·입국 화면처럼 띠가 둘 이상 쌓이는 곳이 쓴다 — Navy 보안 띠(SecurityBanner)는 지갑·여권 화면에만 남긴다.
 * [items]: 그 화면에 필요한 약속만(기본 셋 다).
 */
@Composable
fun AssuranceCard(modifier: Modifier = Modifier, items: List<Assurance> = Assurance.entries) {
    val dimens = LocalDimens.current
    Surface(color = Tokens.Surface, contentColor = Tokens.Ink, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .startBar(Tokens.Accent)
                .padding(start = 20.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val style = MaterialTheme.typography.bodyMedium
            val iconSize = textIconSize(dimens.icon - 4.dp, style)
            items.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = Tokens.Accent,
                        modifier = Modifier.padding(top = firstLineIconOffset(style, iconSize)).size(iconSize),
                    )
                    // 첫 약속만 조금 굵게 — 띠 셋이 모두 굵던 무게를 한 줄로 줄인다(재검토2 ①#12)
                    KoText(
                        stringResource(item.text),
                        if (i == 0) style.copy(fontWeight = FontWeight.SemiBold) else style,
                        Modifier.weight(1f),
                        color = Tokens.Ink,
                    )
                }
            }
        }
    }
}

/**
 * "내 정보는 이 휴대폰에만 저장돼요" — 보안 화면 맨 위 (기존 LocalOnlyBanner를 옮겨 다시 꾸밈, 문구는 그대로).
 * [compact]: Lock 아이콘 + `settings_local_only_title` 한 줄 (21·25·26·27, 입국 카드 화면).
 * 제목은 `이`가 줄 끝에 홀로 남지 않게 묶고(`이 휴대폰`), 큰 글자에서는 전체형의 배지를 제목 위로 올려 제목에 폭 전체를 준다
 * (E 묶음 LocalOnlyCard·CompactSecurityLine 통합).
 */
@Composable
fun SecurityBanner(modifier: Modifier = Modifier, compact: Boolean = false) {
    val dimens = LocalDimens.current
    val title = stringResource(R.string.settings_local_only_title)
    if (compact) {
        Surface(color = Tokens.Navy, contentColor = Tokens.Surface, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
            val style = MaterialTheme.typography.titleSmall
            val iconSize = textIconSize(dimens.icon, style)
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                // 큰 글자로 여러 줄이 되면 자물쇠를 첫 줄에 맞춘다
                verticalAlignment = if (isStackedLayout()) Alignment.Top else Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = Tokens.Surface,
                    modifier = Modifier.padding(top = if (isStackedLayout()) firstLineIconOffset(style, iconSize) else 0.dp).size(iconSize),
                )
                KoText(title, style, Modifier.weight(1f), color = Tokens.Surface, glueShort = true)
            }
        }
    } else {
        Surface(color = Tokens.Navy, contentColor = Tokens.Surface, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                BadgeTitleLayout(
                    badge = { IconBadge(Icons.Outlined.Lock, tone = BadgeTone.OnDark, shape = CircleShape) },
                    stack = isStackedLayout(),
                    gap = 12.dp,
                    title = { KoText(title, MaterialTheme.typography.titleLarge, color = Tokens.Surface, glueShort = true) },
                )
                KoText(stringResource(R.string.settings_local_only_body), MaterialTheme.typography.bodyLarge, color = Tokens.Surface)
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
        // 여러 줄(큰 글자)이 되면 아이콘을 첫 줄에 맞춘다
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val style = MaterialTheme.typography.labelLarge
        val iconSize = textIconSize(20.dp, style)
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = Tokens.Surface,
            modifier = Modifier.padding(top = firstLineIconOffset(style, iconSize)).size(iconSize),
        )
        KoText(stringResource(R.string.offline_banner), style, Modifier.weight(1f), color = Tokens.Surface)
    }
}
