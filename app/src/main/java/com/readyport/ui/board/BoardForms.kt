package com.readyport.ui.board

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.readyport.ui.components.KoreanBreak
import com.readyport.ui.components.keepWords
import com.readyport.ui.components.koDisplay
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.board.BoardError
import com.readyport.board.FieldProblem
import com.readyport.board.PiiGuard
import com.readyport.board.PiiHit
import com.readyport.board.PiiKind
import com.readyport.ui.components.KoText
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.startBar
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 쓰기 칸·경고 (글쓰기·댓글이 함께 쓴다) =======================

/** 화면에 보일 문구 하나 (문자열 id + 숫자 하나) */
@Immutable
data class UiText(@StringRes val id: Int, val arg: Long? = null)

@Composable
fun UiText.text(): String = if (arg == null) stringResource(id) else stringResource(id, arg)

/** 게시판 실패 → 쉬운 문구 ([comment] = 댓글 간격 문구) */
fun BoardError.toUiText(comment: Boolean = false): UiText = when (this) {
    BoardError.Offline -> UiText(R.string.board_err_offline)
    BoardError.AuthUnavailable -> UiText(R.string.board_err_auth)
    BoardError.Denied -> UiText(R.string.board_err_denied)
    is BoardError.TooFast -> UiText(if (comment) R.string.board_err_too_fast_comment else R.string.board_err_too_fast_post, waitSeconds)
    BoardError.MediaUnavailable -> UiText(R.string.board_media_unavailable)
    BoardError.NotFound -> UiText(R.string.board_err_not_found)
    is BoardError.AgeRestricted -> UiText(R.string.board_err_age)
    BoardError.AgeCheckNeeded -> UiText(R.string.board_err_age_check)
}

/** 아무 예외 → 쉬운 문구 */
fun Throwable.toUiText(comment: Boolean = false): UiText = (this as? BoardError)?.toUiText(comment) ?: UiText(R.string.board_err_denied)

/**
 * 게시판 입력칸: 위 라벨 + 칸 + 아래 `12 / 60`(넘치면 빨강) + 문제 문구. 글자 수 제한으로 자르지 않는다(넘친 것을 보여 주고 고치게).
 */
@Composable
fun BoardField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    max: Int,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    problem: String? = null,
    minLines: Int = 1,
    singleLine: Boolean = false,
) {
    val count = value.trim().length
    val over = count > max
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KoText(label, MaterialTheme.typography.titleSmall, color = Tokens.Ink)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            textStyle = MaterialTheme.typography.bodyLarge,
            visualTransformation = KeepWordsTransformation,
            placeholder = placeholder?.let { { KoText(it, MaterialTheme.typography.bodyLarge, color = Tokens.InkTertiary) } },
            minLines = minLines,
            singleLine = singleLine,
            isError = problem != null || over,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Tokens.Surface,
                unfocusedContainerColor = Tokens.Surface,
                errorContainerColor = Tokens.Surface,
                unfocusedBorderColor = Tokens.LineStrong,
                focusedBorderColor = Tokens.Accent,
                errorBorderColor = Tokens.DangerText,
            ),
        )
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                if (problem != null) {
                    val style = MaterialTheme.typography.bodyMedium
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = Tokens.DangerText, modifier = Modifier.size(textIconSize(LocalDimens.current.iconSmall + 2.dp, style)))
                        KoText(problem, style, color = Tokens.DangerText)
                    }
                }
            }
            Text(
                stringResource(R.string.board_count, count, max),
                style = MaterialTheme.typography.bodySmall,
                color = if (over) Tokens.DangerText else Tokens.InkTertiary,
            )
        }
    }
}

/**
 * 입력칸에 **보이는** 글만 낱말 보호([keepWords] — API 33 미만에서 한글 낱말 안 글자 사이에 WORD JOINER, 일부 띄어쓰기는 NBSP).
 * 값(저장·검사·서버로 가는 글)은 그대로다. 넣는 글자는 WORD JOINER뿐이라 자리 맞추기는 그 글자만 건너뛴다.
 */
object KeepWordsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        val shown = keepWords(src)
        if (shown == src) return TransformedText(text, OffsetMapping.Identity)
        val toShown = IntArray(src.length + 1)
        val toSrc = IntArray(shown.length + 1)
        var i = 0
        var j = 0
        while (j < shown.length) {
            val inserted = shown[j] == KoreanBreak.WORD_JOINER && (i >= src.length || src[i] != KoreanBreak.WORD_JOINER)
            toSrc[j] = i
            if (!inserted) {
                if (i < src.length) toShown[i] = j
                i++
            }
            j++
        }
        toShown[src.length] = shown.length
        toSrc[shown.length] = src.length
        return TransformedText(
            AnnotatedString(shown),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = toShown[offset.coerceIn(0, src.length)]
                override fun transformedToOriginal(offset: Int): Int = toSrc[offset.coerceIn(0, shown.length)]
            },
        )
    }
}

/** 제목 길이 문제 문구 */
@Composable
fun titleProblem(p: FieldProblem?): String? = when (p) {
    FieldProblem.TooShort -> stringResource(R.string.board_title_short)
    FieldProblem.TooLong -> stringResource(R.string.board_title_long)
    null -> null
}

@Composable
fun bodyProblem(p: FieldProblem?): String? = when (p) {
    FieldProblem.TooShort -> stringResource(R.string.board_body_short)
    FieldProblem.TooLong -> stringResource(R.string.board_body_long)
    null -> null
}

@StringRes
private fun PiiKind.label(): Int = when (this) {
    PiiKind.Passport -> R.string.board_pii_passport
    PiiKind.Mrz -> R.string.board_pii_mrz
    PiiKind.ResidentId -> R.string.board_pii_resident
    PiiKind.Phone -> R.string.board_pii_phone
    PiiKind.Email -> R.string.board_pii_email
}

/** 찾은 곳 앞뒤로 보여 줄 글자 수 */
private const val PII_CONTEXT = 14

/** 발췌의 보통 조각: 줄바꿈은 띄어쓰기로, 낱말 보호(API 33 미만) */
private fun shownPart(s: String): String = keepWords(s.replace('\n', ' '))

/**
 * 찾은 개인정보 부분을 빨간 바탕·밑줄로 표시한 발췌(찾은 곳 앞뒤 14자). 한 발췌에 여러 곳이 있어도 모두 표시한다.
 */
fun piiExcerpt(text: String, hits: List<PiiHit>): AnnotatedString = buildAnnotatedString {
    // 겹치거나 가까운 발췌는 합친다
    val windows = mutableListOf<IntRange>()
    hits.sortedBy { it.range.first }.forEach { h ->
        val w = (h.range.first - PII_CONTEXT).coerceAtLeast(0)..(h.range.last + PII_CONTEXT).coerceAtMost(text.lastIndex)
        val last = windows.lastOrNull()
        if (last != null && w.first <= last.last + 1) windows[windows.lastIndex] = last.first..maxOf(last.last, w.last) else windows += w
    }
    windows.forEachIndexed { i, w ->
        if (i > 0) append("\n")
        if (w.first > 0) append("…")
        var at = w.first
        hits.filter { it.range.first >= w.first && it.range.last <= w.last }.sortedBy { it.range.first }.forEach { h ->
            append(shownPart(text.substring(at, h.range.first)))
            pushStyle(SpanStyle(background = Tokens.DangerBg, color = Tokens.DangerText, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
            append(text.substring(h.range.first, h.range.last + 1))
            pop()
            at = h.range.last + 1
        }
        append(shownPart(text.substring(at, w.last + 1)))
        if (w.last < text.lastIndex) append("…")
    }
}

/**
 * `개인정보가 들어 있는 것 같아요` — 빨간 왼쪽 막대 카드: 찾은 종류 · 표시한 발췌 · 무엇을 해야 하는지.
 * 여권·MRZ·주민번호는 고쳐야 올라가고, 전화·이메일만이면 `이대로 올리기`(공개된 번호일 수 있다)를 둔다.
 * 바뀌면 TalkBack이 알린다(liveRegion).
 */
@Composable
fun PiiWarningCard(text: String, hits: List<PiiHit>, onAllow: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (hits.isEmpty()) return
    val blocking = PiiGuard.blocking(hits)
    val kinds = hits.map { it.kind }.distinct().map { stringResource(it.label()) }.joinToString(", ")
    val shape = MaterialTheme.shapes.small
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.DangerBg, shape)
            .startBar(Tokens.DangerText)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val titleStyle = MaterialTheme.typography.titleSmall
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.GppMaybe, null, tint = Tokens.DangerText, modifier = Modifier.size(textIconSize(LocalDimens.current.icon, titleStyle)))
            KoText(stringResource(R.string.board_pii_title), titleStyle, Modifier.weight(1f), color = Tokens.DangerText, heading = true)
        }
        KoText(stringResource(R.string.board_pii_found, kinds), MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
        val excerpt = piiExcerpt(text, hits)
        val plain = excerpt.text.filterNot { it == KoreanBreak.WORD_JOINER }.replace(KoreanBreak.NBSP, ' ')
        Text(
            excerpt,
            style = MaterialTheme.typography.bodyMedium,
            color = Tokens.Ink,
            modifier = Modifier
                .fillMaxWidth()
                .background(Tokens.Surface, shape)
                .padding(12.dp)
                .semantics { this.text = AnnotatedString(plain) },
        )
        KoText(
            stringResource(if (blocking) R.string.board_pii_block else R.string.board_pii_warn),
            MaterialTheme.typography.bodyMedium,
            color = Tokens.Ink,
        )
        if (!blocking && onAllow != null) QuietButton(stringResource(R.string.board_pii_post_anyway), onClick = onAllow)
    }
}

/** 욕설이 있을 때 한 줄 (주의 — 올리면 ＊로 가려진다) */
@Composable
fun ProfanityNote(modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Tokens.CautionBg, MaterialTheme.shapes.small)
            .startBar(Tokens.CautionBorder)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.SentimentDissatisfied, null, tint = Tokens.CautionText, modifier = Modifier.size(textIconSize(LocalDimens.current.icon, style)))
        KoText(stringResource(R.string.board_profanity), style, Modifier.weight(1f), color = Tokens.CautionText)
    }
}

/** 실패 한 줄 (빨강) */
@Composable
fun ErrorLine(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    Row(
        modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = Tokens.DangerText, modifier = Modifier.size(textIconSize(LocalDimens.current.iconSmall + 4.dp, style)))
        KoText(text, style, Modifier.weight(1f), color = Tokens.DangerText)
    }
}

/** 흰 그림자 덩어리 안 묶음 (쓰기 화면 섹션) */
@Composable
fun FormBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .fillMaxWidth()
            .cardShadow(shape)
            .background(Tokens.Surface, shape)
            .padding(LocalDimens.current.cardPadding),
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
    ) { content() }
}
