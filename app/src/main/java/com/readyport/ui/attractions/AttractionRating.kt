package com.readyport.ui.attractions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.attractions.rating.GoogleRating
import com.readyport.attractions.rating.MyVote
import com.readyport.attractions.rating.RatingStats
import com.readyport.attractions.rating.koreanCount
import com.readyport.board.BoardAge
import com.readyport.ui.board.ErrorLine
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LinkRow
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import java.util.Locale

// ======================= 관광지 상세 — 평점(Google · 레디포트 이용자)·확인 중 띠 =======================

/** 평점 칸 상태 (null이면 칸 자체가 없다 — 이 관광지에 별점 키를 만들 수 없을 때) */
@Immutable
data class RatingUi(
    /** 레디포트 이용자 공개 평점 (5명 미만·없음이면 null — 숨김) */
    val stats: RatingStats? = null,
    /** Google 별점 (키가 없는 빌드·place ID 없음·실패면 null — 숨김) */
    val google: GoogleRating? = null,
    val mine: MyVote = MyVote.Unknown,
    val age: BoardAge.Status = BoardAge.Status.Allowed,
    val busy: Boolean = false,
    /** 결과 한 줄 (문자열 id) */
    val message: Int? = null,
    val messageError: Boolean = false,
)

/** 소수 한 자리 (`4.3`) — 기기 언어와 관계없이 점 */
internal fun oneDecimal(v: Double): String = String.format(Locale.ROOT, "%.1f", v)

/** '공식 안내가 바뀌었어요 — 확인 중이에요' (attraction_flags, ARIA 가 공식 출처의 휴관·공사 표현 증가를 감지) */
@Composable
fun AttractionFlagBand(modifier: Modifier = Modifier) {
    NoticeBanner(
        stringResource(R.string.attraction_flag_body),
        modifier,
        icon = Icons.Outlined.ReportProblem,
        tone = BannerTone.Caution,
        title = stringResource(R.string.attraction_flag_title),
    )
}

/**
 * 평점 카드: Google 별점(`★ 4.5 · 리뷰 1.2만 · Google 제공` + 지도 링크) → 레디포트 이용자 평점(5명 이상일 때만) →
 * 내 별점(`다녀왔어요? 별점 남기기` → 별 다섯 개 고르기 · 바꾸기 · 지우기). 미성년은 안내만, 나이를 모르면 `나이 확인하기`.
 */
@Composable
fun RatingSection(
    rating: RatingUi,
    vote: (Int) -> Unit,
    remove: () -> Unit,
    checkAge: () -> Unit,
    openLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    CardNewsCard(title = stringResource(R.string.rating_title), icon = Icons.Outlined.StarOutline, tone = BadgeTone.Teal, modifier = modifier) {
        rating.google?.let { g -> GoogleRatingLine(g, openLink) }
        rating.stats?.let { s ->
            IconBullet(stringResource(R.string.rating_own_stats, oneDecimal(s.avg), s.n), Icons.Outlined.Groups, tone = BadgeTone.Teal)
        }
        when (rating.age) {
            is BoardAge.Status.Minor -> IconBullet(stringResource(R.string.rating_minor), Icons.Outlined.ChildCare, tone = BadgeTone.Caution)
            BoardAge.Status.NeedsCheck -> {
                KoText(stringResource(R.string.rating_age_check), MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
                SecondaryButton(stringResource(R.string.board_age_check_button), onClick = checkAge, icon = Icons.Outlined.VerifiedUser, tone = BadgeTone.Teal)
            }
            BoardAge.Status.Allowed -> {
                val mine = rating.mine
                when {
                    picking -> StarPicker(
                        current = (mine as? MyVote.Given)?.stars,
                        enabled = !rating.busy,
                        onPick = { n ->
                            picking = false
                            vote(n)
                        },
                        onCancel = { picking = false },
                    )
                    mine is MyVote.Given -> {
                        MyStars(mine.stars)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(stringResource(R.string.rating_change), onClick = { picking = true }, icon = Icons.Outlined.Edit, tone = BadgeTone.Teal, fillWidth = false, enabled = !rating.busy)
                            QuietButton(stringResource(R.string.rating_remove), onClick = remove)
                        }
                    }
                    else -> SecondaryButton(stringResource(R.string.rating_prompt), onClick = { picking = true }, icon = Icons.Outlined.StarOutline, tone = BadgeTone.Teal, enabled = !rating.busy)
                }
            }
        }
        rating.message?.let { m ->
            if (rating.messageError) {
                ErrorLine(stringResource(m))
            } else {
                KoText(stringResource(m), MaterialTheme.typography.bodyMedium, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = Tokens.SuccessText)
            }
        }
        KoText(stringResource(R.string.rating_note), MaterialTheme.typography.bodySmall, color = Tokens.InkTertiary)
    }
}

/** `★ 4.5 · 리뷰 1.2만 · Google 제공` (TalkBack은 `Google 별점 4.5점, 리뷰 1.2만개, Google 제공`) + Google 지도 링크 */
@Composable
private fun GoogleRatingLine(g: GoogleRating, openLink: (String) -> Unit) {
    val score = oneDecimal(g.rating)
    val count = koreanCount(g.count)
    val cd = stringResource(R.string.rating_google_cd, score, count)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KoText(
            stringResource(R.string.rating_google, score, count),
            MaterialTheme.typography.titleMedium,
            Modifier.clearAndSetSemantics { contentDescription = cd },
            color = Tokens.Ink,
        )
        g.mapsUri?.let { url -> LinkRow(stringResource(R.string.rating_google_open), { openLink(url) }) }
    }
}

/** 내 별점 (별 그림 + `내 별점 4점`) */
@Composable
private fun MyStars(stars: Int) {
    val label = stringResource(R.string.rating_mine, stars)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row {
            (1..5).forEach { i ->
                Icon(if (i <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline, contentDescription = null, tint = if (i <= stars) Tokens.TealText else Tokens.LineStrong)
            }
        }
        KoText(label, MaterialTheme.typography.titleSmall, color = Tokens.Ink)
    }
}

/** 별 다섯 개 고르기 — 누르면 바로 남긴다(별 하나 = 48dp 이상, `별 3개`). `그만두기` */
@Composable
private fun StarPicker(current: Int?, enabled: Boolean, onPick: (Int) -> Unit, onCancel: () -> Unit) {
    val dimens = LocalDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
        KoText(stringResource(R.string.rating_pick_title), MaterialTheme.typography.titleSmall, color = Tokens.Ink, heading = true)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { i ->
                val cd = stringResource(R.string.rating_star_cd, i)
                val on = current != null && i <= current
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .minTouchSize()
                        .selectable(selected = current == i, enabled = enabled, role = Role.RadioButton, onClick = { onPick(i) })
                        .semantics { contentDescription = cd },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (on) Icons.Filled.Star else Icons.Outlined.StarOutline, contentDescription = null, tint = if (on) Tokens.TealText else Tokens.LineStrong, modifier = Modifier.size(32.dp))
                }
            }
        }
        QuietButton(stringResource(R.string.rating_cancel), onClick = onCancel)
    }
}
