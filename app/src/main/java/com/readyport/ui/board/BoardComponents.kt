package com.readyport.ui.board

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.board.BoardKind
import com.readyport.board.BoardPost
import com.readyport.board.BoardTime
import com.readyport.board.RelativeTime
import com.readyport.board.Shown
import com.readyport.board.avatarInitial
import com.readyport.board.avatarSlot
import com.readyport.board.boardPreview
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.KoText
import com.readyport.ui.components.Photos
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.TextCircle
import com.readyport.ui.components.TrailingFlow
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.rememberPhotoLift
import com.readyport.ui.components.rememberThumbnail
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import java.time.Instant
import java.time.ZoneId

// ======================= 게시판 공용 부품 (DESIGN_SPEC 부록 L.3) =======================

/** 게시판 종류 아이콘·톤 */
fun BoardKind.icon(): ImageVector = if (this == BoardKind.Qna) Icons.Outlined.QuestionAnswer else Icons.Outlined.Forum

fun BoardKind.badgeTone(): BadgeTone = if (this == BoardKind.Qna) BadgeTone.Accent else BadgeTone.Teal

@StringRes
fun BoardKind.label(): Int = if (this == BoardKind.Qna) R.string.board_kind_qna else R.string.board_kind_talk

@StringRes
fun BoardKind.longLabel(): Int = if (this == BoardKind.Qna) R.string.board_kind_qna_long else R.string.board_kind_talk_long

/** 나라 코드 → 이름 (게시판 9개 나라) */
@Composable
fun boardCountryName(code: String): String = stringResource(
    when (code) {
        "TH" -> R.string.board_cc_TH
        "JP" -> R.string.board_cc_JP
        "VN" -> R.string.board_cc_VN
        "PH" -> R.string.board_cc_PH
        "TW" -> R.string.board_cc_TW
        "SG" -> R.string.board_cc_SG
        "MY" -> R.string.board_cc_MY
        "ID" -> R.string.board_cc_ID
        else -> R.string.board_cc_CN
    },
)

/** 상대 시각 글자 (`3시간 전`, 7일 넘으면 날짜) */
@Composable
fun relativeTime(now: Instant, then: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    when (val r = BoardTime.relative(now, then, zone)) {
        RelativeTime.JustNow -> stringResource(R.string.board_time_now)
        is RelativeTime.Minutes -> stringResource(R.string.board_time_minutes, r.n.toInt())
        is RelativeTime.Hours -> stringResource(R.string.board_time_hours, r.n.toInt())
        is RelativeTime.Days -> stringResource(R.string.board_time_days, r.n.toInt())
        is RelativeTime.Date -> r.text
    }

/**
 * 아바타 색 — 사진 없이 사람을 구분한다(게시판 ID 해시 → 여섯 색 중 하나). 모두 흰 글자 대비 4.5 이상인 토큰.
 */
private val AvatarColors = listOf(Tokens.Accent, Tokens.TealText, Tokens.VioletText, Tokens.Help, Tokens.SuccessText, Tokens.AccentDeep)

/** 닉네임 첫 글자 원 (꾸밈 — 옆 닉네임이 이름을 말한다). 글자가 크면 원도 커진다(TextCircle) */
@Composable
fun BoardAvatar(uid: String, nickname: String, modifier: Modifier = Modifier, size: Dp = LocalDimens.current.stepBadge + 4.dp) {
    TextCircle(
        avatarInitial(nickname),
        modifier = modifier.clearAndSetSemantics {},
        minSize = size,
        container = if (nickname.isBlank()) Tokens.SurfaceHighest else AvatarColors[avatarSlot(uid, AvatarColors.size)],
        content = if (nickname.isBlank()) Tokens.InkTertiary else Tokens.Surface,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
    )
}

/** 나라 태그 (누를 수 없음): 둥근 나라 사진 + 이름 */
@Composable
fun CountryTag(code: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelMedium
    val photoSize = textIconSize(LocalDimens.current.iconSmall + 4.dp, style)
    Surface(color = Tokens.Surface, shape = MaterialTheme.shapes.extraSmall, modifier = modifier.heightIn(min = 28.dp)) {
        Row(
            Modifier
                .border(1.dp, Tokens.LineSoft, MaterialTheme.shapes.extraSmall)
                .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CountryPhoto(code, photoSize)
            KoText(boardCountryName(code), style, color = Tokens.Ink)
        }
    }
}

/** 둥근 나라 사진 (꾸밈) */
@Composable
fun CountryPhoto(code: String, size: Dp) {
    val thumb = rememberThumbnail(Photos.country(code), size)
    Box(Modifier.size(size).clip(CircleShape).background(Tokens.SurfaceSunken)) {
        if (thumb != null) {
            Image(
                thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberPhotoLift(thumb),
                modifier = Modifier.size(size),
            )
        }
    }
}

/** Q&A 상태: 해결됨(초록) / 답변 기다려요(호박색) — 색 + 아이콘 + 글자 */
@Composable
fun QnaStatusTag(solved: Boolean, modifier: Modifier = Modifier) {
    if (solved) {
        StatusTag(stringResource(R.string.board_status_solved), StatusKind.Allowed, modifier, icon = Icons.Outlined.TaskAlt)
    } else {
        StatusTag(stringResource(R.string.board_status_waiting), StatusKind.Caution, modifier, icon = Icons.Outlined.HourglassTop)
    }
}

@Composable
fun PinnedTag(modifier: Modifier = Modifier) =
    StatusTag(stringResource(R.string.board_pinned), StatusKind.Info, modifier, icon = Icons.Outlined.PushPin)

@Composable
fun OperatorTag(modifier: Modifier = Modifier) =
    StatusTag(stringResource(R.string.board_operator), StatusKind.Self, modifier, icon = Icons.Outlined.VerifiedUser)

@Composable
fun HiddenTag(modifier: Modifier = Modifier) =
    StatusTag(stringResource(R.string.board_admin_hidden_tag), StatusKind.Soon, modifier, icon = Icons.Outlined.VisibilityOff)

/** 댓글·추천 수 (TalkBack은 `댓글 4개, 추천 12개` 한 번) */
@Composable
fun CountsRow(comments: Int, likes: Int, modifier: Modifier = Modifier) {
    val cd = stringResource(R.string.board_counts_cd, comments, likes)
    val style = MaterialTheme.typography.labelLarge
    val size = textIconSize(LocalDimens.current.iconSmall + 2.dp, style)
    Row(
        modifier.clearAndSetSemantics { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Tokens.InkSecondary, modifier = Modifier.size(size))
            Text(comments.toString(), style = style, color = Tokens.InkSecondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.ThumbUpOffAlt, null, tint = Tokens.InkSecondary, modifier = Modifier.size(size))
            Text(likes.toString(), style = style, color = Tokens.InkSecondary)
        }
    }
}

/** 글쓴이 줄: 아바타 + 닉네임 + 시각(+ 고침) */
@Composable
fun AuthorLine(uid: String, nickname: String, time: String, modifier: Modifier = Modifier, edited: Boolean = false, extra: (@Composable () -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BoardAvatar(uid, nickname)
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            // 닉네임의 한 글자 낱말(`짐 싸는 중`)이 줄 머리에 혼자 남지 않게
            KoText(nickname, MaterialTheme.typography.labelLarge, color = Tokens.Ink, glueShort = true)
            val meta = if (edited) "$time · ${stringResource(R.string.board_edited)}" else time
            KoText(meta, MaterialTheme.typography.bodySmall, color = Tokens.InkTertiary)
            extra?.invoke()
        }
    }
}

/** 흰 카드 (그림자 + LineSoft 1dp, 모서리 20) — [onClick]이 있으면 카드 전체가 버튼 */
@Composable
fun BoardCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    border: Color? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val dimens = LocalDimens.current
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .cardShadow(shape)
            .then(if (border != null) Modifier.border(2.dp, border, shape) else Modifier),
    ) {
        val click = if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else Modifier
        Box(click.fillMaxWidth().padding(dimens.cardPadding)) { content() }
    }
}

/**
 * 목록 글 카드: 종류 배지 · 상태 · 나라 · 고정·운영자 → 제목 → 미리보기(70자, 줄 수로 자르지 않는다) → 글쓴이·시각 · 댓글·추천 수.
 * [shown]: 신고가 쌓이면 접어 두고 `그래도 보기`, 지운 글은 `글쓴이가 지운 글이에요`(댓글 묶음은 남아 있어 열 수 있다).
 */
@Composable
fun PostCard(
    post: BoardPost,
    shown: Shown,
    now: Instant,
    onOpen: () -> Unit,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
    operator: Boolean = false,
) {
    val dimens = LocalDimens.current
    if (shown == Shown.Reported) {
        BoardCard(modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Icons.Outlined.GppMaybe, tone = BadgeTone.Neutral)
                    KoText(stringResource(R.string.board_reported_post), MaterialTheme.typography.titleMedium, Modifier.weight(1f), color = Tokens.InkSecondary)
                }
                QuietButton(stringResource(R.string.board_reveal), onClick = onReveal)
            }
        }
        return
    }
    val deleted = shown == Shown.Deleted
    val title = if (deleted) stringResource(R.string.board_deleted_post) else post.title
    BoardCard(modifier, onClick = onOpen, onClickLabel = stringResource(R.string.board_open_post_cd, title)) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
            val stacked = isStackedLayout()
            // Q&A 상태(해결됨·답변 기다려요)는 질문에만 — 운영자 고정 안내 글에는 붙이지 않는다
            val showStatus = post.kind == BoardKind.Qna && !deleted && !post.pinned
            val tags: @Composable () -> Unit = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (post.pinned) PinnedTag()
                    if (operator) OperatorTag()
                    if (showStatus) QnaStatusTag(post.solved)
                    post.country?.let { CountryTag(it) }
                    if (shown == Shown.Hidden) HiddenTag()
                }
            }
            val hasTags = post.pinned || operator || showStatus || post.country != null || shown == Shown.Hidden
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!stacked) IconBadge(if (deleted) Icons.Outlined.DeleteOutline else post.kind.icon(), tone = if (deleted) BadgeTone.Neutral else post.kind.badgeTone())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hasTags) tags()
                    KoText(title, MaterialTheme.typography.titleMedium, color = if (deleted) Tokens.InkSecondary else Tokens.Ink, glueShort = true)
                    if (!deleted && post.body.isNotBlank()) {
                        KoText(boardPreview(post.body), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                    }
                }
            }
            TrailingFlow(trailing = { CountsRow(post.commentCount, post.likeCount) }, gap = 12.dp, centerVertically = true) {
                if (!deleted) {
                    AuthorLine(post.authorUid, post.nickname, relativeTime(now, post.createdAt))
                } else {
                    KoText(relativeTime(now, post.createdAt), MaterialTheme.typography.bodySmall, color = Tokens.InkTertiary)
                }
            }
        }
    }
}

/** 둥근 모서리로 자르고 바탕·1dp 테두리 (누를 수 있는 흰 줄 — 테두리 LineStrong) */
fun Modifier.clipBorder(shape: androidx.compose.ui.graphics.Shape, fill: Color = Tokens.Surface, border: Color? = Tokens.LineStrong): Modifier =
    clip(shape).background(fill, shape).then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)

/** 자리만 남은 글·댓글 (지움·차단·가림) — 옅은 상자 안 한 줄 */
@Composable
fun PlaceholderLine(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.AutoMirrored.Outlined.Chat) {
    val style = MaterialTheme.typography.bodyMedium
    Row(
        modifier
            .fillMaxWidth()
            .background(Tokens.SurfaceSunken, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Tokens.InkSecondary, modifier = Modifier.size(textIconSize(LocalDimens.current.iconSmall + 2.dp, style)))
        KoText(text, style, Modifier.weight(1f), color = Tokens.InkSecondary)
    }
}
