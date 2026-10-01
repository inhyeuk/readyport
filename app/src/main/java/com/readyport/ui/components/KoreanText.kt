package com.readyport.ui.components

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

// ======================= 한국어 줄바꿈 (DESIGN_SPEC 3.2 보완 — 2단계 통합) =======================
// 1단계 묶음이 따로 만들었던 보정(A·C·F keepWords, B koreanPhraseWrap, D keepAll, E KoBreak)을 하나로 합친 것.
// 공용 부품(제목·버튼·태그·배너·출처 줄 등)이 안에서 이 보정을 하므로, 화면은 원문 문자열을 그대로 넘긴다.
//
// 원칙
// - API 33 미만(테스트 폰 S10 = Android 12)은 WordBreak.Phrase가 없어 한글을 음절 사이 어디서나 끊는다(`처음이에/요`).
//   그곳에서만 [keepWords]가 낱말(띄어쓰기 사이) 안 글자 사이에 WORD JOINER(U+2060)를 넣어 띄어쓰기에서만 줄이 바뀌게 한다.
//   낱말 하나가 한 줄보다 길면 렌더러가 그 안에서 끊는다(잘리지는 않는다).
// - API 33 이상은 테마의 LineBreak(WordBreak.Phrase)가 맡으므로 [keepWords]는 글자를 그대로 돌려준다 —
//   테스트(sdk 36)가 getString으로 찾는 글자와 TalkBack 글자가 문자열 리소스와 똑같다.
// - [KoText]는 보정한 글자를 그리되 의미 글자(TalkBack·테스트)는 언제나 원문으로 둔다. 그래서 KoText에서만 쓰는
//   보정([koDisplay]: 띄어 쓴 구분 기호 붙이기, 여는 괄호 앞·긴 낱말의 가운뎃점 뒤·주소 점 뒤 줄바꿈 자리)은 모든 API에서 해도 된다.
// - 괄호·따옴표 묶음은 NBSP로 묶지 않는다: 쉬운 모드 200%의 좁은 줄(약 8자)에서는 묶음이 한 줄보다 길어져 오히려
//   음절 사이에서 억지로 끊겼다(`(둥/근 핀 2개)`, `'오프라/인 지도'` — 2단계 줄바꿈 보고). 안쪽 띄어쓰기에서 바뀌는 편이 낫다.
// - 전화번호에는 보이지 않는 문자를 넣지 않는다(한글이 없는 글은 손대지 않는다, 3.2).

/** 보이지 않는 줄바꿈 제어 문자 */
object KoreanBreak {
    /** 이 자리에서 줄을 바꾸지 않는다 (폭 0) */
    const val WORD_JOINER = '\u2060'

    /** 줄을 바꾸지 않는 띄어쓰기 */
    const val NBSP = '\u00A0'

    /** 이 자리에서 줄을 바꿀 수 있다 (폭 0) */
    const val ZERO_WIDTH_SPACE = '\u200B'

    /** 이 기기가 한글을 음절마다 끊는지 (API 33 미만) */
    fun syllableBreaks(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk < Build.VERSION_CODES.TIRAMISU

    /**
     * 완성형 한글·호환 자모. 조합형 첫가끝 자모(U+1100–11FF)는 사이에 글자를 넣으면 음절이 깨지므로 넣지 않는다.
     */
    fun isHangul(c: Char): Boolean = c in '가'..'힣' || c in '㄰'..'㆏'
}

private val Invisible = setOf(KoreanBreak.WORD_JOINER, KoreanBreak.ZERO_WIDTH_SPACE)

/** 긴 낱말([LONG_WORD] 초과) 안이라도 이 글자 **뒤**에서는 줄을 바꿔도 된다 (`관세청·` 뒤, `고기·햄·소시지·` 뒤) */
private const val BREAK_AFTER = "·・‧⋅/~—–"

/** 낱말 안이라도 한글 뒤 이 글자 **앞**에서는 줄을 바꿔도 된다 — `800달러 / (과세가격`, `칩 확인 / (선택)` */
private const val BREAK_BEFORE = "([{「『"

/** 가운뎃점 (`입국·세관`, `수카르노하타·주안다`) */
private const val MIDDLE_DOTS = "·・‧⋅"

/** 관형사: 뒤 낱말과 떨어져 줄 끝에 홀로 남으면 어색하다 (`도착했어요! 이 / 순서대로`) */
private val Determiners = setOf("이", "그", "저", "새", "첫", "각", "몇", "온", "한", "두", "세", "네", "어느", "무슨", "모든")

/** 의존 명사: 앞 낱말과 떨어져 줄 머리에 오면 어색하다 (`낼 / 수 있어요`) */
private val BoundNouns = setOf("수", "것", "줄", "데", "뿐", "듯", "중")

/**
 * 낱말(띄어쓰기 사이)이 이 길이를 넘을 때만 가운뎃점·물결·빗금 뒤와 주소의 점 뒤에서 줄을 바꿀 수 있게 한다.
 * 짧은 낱말(`과일·식물`, `2일~4일`)은 한 덩어리로 둔다.
 */
private const val LONG_WORD = 6

/** 앞 낱말에 붙여 둘 구분 기호: 줄 머리에 오지 않게 앞 띄어쓰기를 NBSP로 (`일본정부관광국 / · 기념품`, `신고하세요 / — 관세의`) */
private val Separators = Regex(" ([·—–]) ")

/**
 * 화면에 그릴 한국어 글(제목·버튼·라벨·본문·팩 문장)의 낱말 보호. **API 33 미만에서만** 글자를 바꾼다:
 * - 낱말 안에서 한글이 낀 두 글자 사이에 WORD JOINER (`숙소로 돌아가기` → `숙\u2060소\u2060로 돌\u2060아\u2060가\u2060기`, `QR\u2060을`, `(6\u2060단\u2060계`)
 * - 한글 뒤 여는 괄호 **앞**, 긴 낱말(6자 초과)의 가운뎃점·빗금·물결·대시 **뒤**는 줄을 바꿀 수 있는 자리로 둔다
 * - 띄어 쓴 구분 기호(` · `, ` — `)는 앞 낱말에 붙인다(줄 머리에 오지 않게)
 * - 관형사(`이`, `새`…) 뒤·의존 명사(`수`, `것`…) 앞 띄어쓰기는 NBSP — 이웃 낱말과 함께 넘어간다
 * 한글이 없는 글(영문·숫자·전화번호·태국어)과 API 33 이상은 그대로 돌려준다. 이미 보정한 글에 다시 써도 결과가 같다.
 * 보이지 않는 문자는 TalkBack이 읽지 않는다. Text에 직접 그릴 때는 의미 글자를 원문으로 두는 [KoText]를 쓴다.
 */
fun keepWords(text: String, sdk: Int = Build.VERSION.SDK_INT): String {
    if (!KoreanBreak.syllableBreaks(sdk) || text.none(KoreanBreak::isHangul)) return text
    return joinKoreanWords(glueGroups(text))
}

/** 낱말 안 글자 사이 WORD JOINER + 관형사·의존 명사 NBSP (API 구분 없이 — [keepWords]가 API를 본다) */
internal fun joinKoreanWords(text: String): String {
    if (text.none(KoreanBreak::isHangul)) return text
    val out = StringBuilder(text.length * 2)
    var wordStart = 0
    var longWord = isLongWord(text, 0)
    text.forEachIndexed { i, c ->
        if (i > 0) {
            val p = text[i - 1]
            // 한글 뒤 여는 괄호 앞, 긴 낱말의 구분 기호 뒤는 줄바꿈 자리로 남긴다
            val soft = (c in BREAK_BEFORE && KoreanBreak.isHangul(p)) || (longWord && p in BREAK_AFTER)
            val joinable = !p.isWhitespace() && !c.isWhitespace() && p !in Invisible && c !in Invisible &&
                !soft && (KoreanBreak.isHangul(p) || KoreanBreak.isHangul(c))
            if (joinable) out.append(KoreanBreak.WORD_JOINER)
        }
        val glue = c == ' ' && i > 0 && !text[i - 1].isWhitespace() && i + 1 < text.length && !text[i + 1].isWhitespace() &&
            (text.substring(wordStart, i) in Determiners || text.substring(i + 1).takeWhile { !it.isWhitespace() } in BoundNouns)
        out.append(if (glue) KoreanBreak.NBSP else c)
        if (c.isWhitespace()) {
            wordStart = i + 1
            longWord = isLongWord(text, i + 1)
        }
    }
    return out.toString()
}

/** [start]에서 시작하는 낱말(띄어쓰기 전까지)의 보이는 글자 수가 [LONG_WORD]를 넘는지 */
private fun isLongWord(text: String, start: Int): Boolean {
    var n = 0
    var i = start
    while (i < text.length && !text[i].isWhitespace()) {
        if (text[i] !in Invisible) n++
        i++
    }
    return n > LONG_WORD
}

/** 띄어 쓴 구분 기호(` · `, ` — `) 앞 띄어쓰기를 NBSP로 — 구분 기호가 줄 머리에 오지 않게 */
internal fun glueGroups(text: String): String =
    text.replace(Separators) { "${KoreanBreak.NBSP}${it.groupValues[1]} " }

private val ShortWord = Regex("""(?<=^|[ \u00A0\n])([가-힣]) (?=\S)""")

/** 한 음절 낱말 뒤 띄어쓰기를 NBSP로 — 제목의 `이 휴대폰`, `두 가지`, `꼭 채워요`에서 한 음절이 줄 끝에 홀로 남지 않게 */
fun glueShortWords(text: String): String = ShortWord.replace(text) { "${it.groupValues[1]}${KoreanBreak.NBSP}" }

/** 글(또는 줄) 끝의 한 음절 낱말 앞 띄어쓰기 — `숙소가 있는 주`, `사이트에서 직접 적을 칸`, `서류에서 가져온 값` */
private val LastShortWord = Regex("""(?<=\S) (?=[가-힣][.!?)\]」』'"’”]*(?:\n|$))""")

/**
 * 글 끝의 한 음절 낱말을 앞 낱말에 붙인다(NBSP) — 좁은 줄(360dp·200%)에서 `칸`·`주`·`값` 한 음절만 다음 줄에 홀로 남지 않게
 * (다듬기 D0 — 줄바꿈 감사를 실패로 올리며 찾은 것). 보이는 글자만 바꾸고 의미 글자는 원문.
 */
fun glueLastShortWord(text: String): String = LastShortWord.replace(text, KoreanBreak.NBSP.toString())

/**
 * 긴 낱말의 줄바꿈 자리(ZERO WIDTH SPACE): 가운뎃점 뒤(`관세청·\u200B농림축산검역본부`, `수카르노하타·주안다`),
 * 여는 괄호 앞(`1단계(여행유의)예요` → `1단계 / (여행유의)예요`), 주소의 점 뒤(`imigrasi.go.id` — 글자 사이 점만, 날짜·숫자는 아님).
 * API 33+의 어절 단위 줄바꿈은 낱말 안에서 끊을 자리가 없어 한 줄보다 긴 낱말을 음절 사이에서 억지로 끊으므로(`1단/계`),
 * 뜻이 나뉘는 자리를 미리 열어 둔다.
 */
internal fun breakLongWords(text: String): String {
    if (text.none { it in MIDDLE_DOTS || it in BREAK_BEFORE || it == '.' }) return text
    val out = StringBuilder(text.length + 8)
    var start = 0
    while (start < text.length) {
        var end = start
        while (end < text.length && !text[end].isWhitespace()) end++
        val word = text.substring(start, end)
        val visibleLength = word.count { it !in Invisible }
        val long = visibleLength > LONG_WORD
        if (word.any { it in BREAK_BEFORE } || (long && word.any { it in MIDDLE_DOTS || it == '.' })) {
            word.forEachIndexed { i, c ->
                val prev = word.getOrNull(i - 1)
                // 여는 괄호 앞 (낱말 첫 글자가 아니고, 앞 글자가 한글일 때 — 낱말 길이와 상관없이)
                if (c in BREAK_BEFORE && prev != null && KoreanBreak.isHangul(prev)) out.append(KoreanBreak.ZERO_WIDTH_SPACE)
                out.append(c)
                val next = word.getOrNull(i + 1)
                if (long && c in MIDDLE_DOTS && next != null && next !in Invisible) out.append(KoreanBreak.ZERO_WIDTH_SPACE)
                // 주소(도메인)의 점 뒤: 앞뒤가 모두 영문자일 때만 (2026.09.29·1.5 같은 숫자는 끊지 않는다)
                if (long && c == '.' && prev != null && next != null && prev.isAsciiLetter() && next.isAsciiLetter()) {
                    out.append(KoreanBreak.ZERO_WIDTH_SPACE)
                }
            }
        } else {
            out.append(word)
        }
        // 띄어쓰기는 그대로
        while (end < text.length && text[end].isWhitespace()) out.append(text[end++])
        start = end
    }
    return out.toString()
}

/**
 * [KoText]가 그리는 글자: 모든 API — 띄어 쓴 구분 기호 붙이기, 글 끝 한 음절 낱말 붙이기, 줄바꿈 자리(여는 괄호 앞·긴 낱말의 가운뎃점 뒤·주소 점 뒤),
 * ([glueShort]면) 한 음절 낱말 묶기.
 * API 33 미만 — 여기에 [keepWords]. 의미 글자는 KoText가 원문으로 둔다.
 */
fun koDisplay(text: String, glueShort: Boolean = false, sdk: Int = Build.VERSION.SDK_INT): String {
    if (text.none(KoreanBreak::isHangul)) return text
    var s = glueLastShortWord(glueGroups(text))
    if (glueShort) s = glueShortWords(s)
    s = breakLongWords(s)
    return keepWords(s, sdk)
}

/** 날짜 같은 짧은 덩어리(`11월 3일 (화)`)를 어디서도 끊지 않는다: 띄어쓰기는 NBSP, 글자 사이에는 WORD JOINER (모든 API) */
fun noBreak(text: String): String = text.replace(' ', KoreanBreak.NBSP).toList().joinToString(KoreanBreak.WORD_JOINER.toString())

private val MonthDay = Regex("""(\d{1,2}월) (\d{1,2}일)""")

/**
 * `N월 N일` 사이 띄어쓰기를 NBSP로 — 날짜가 달과 일로 갈라져 줄을 바꾸지 않게(`5월 / 2일~4일` 방지, 재검토 ①16).
 * 보이는 글자에만 쓴다(의미 글자는 원문). 다른 글자는 그대로. (나라 화면·여행 준비가 따로 갖던 두 벌을 하나로 — 재검토2 ④#1)
 */
fun keepMonthDay(text: String): String = MonthDay.replace(text) { "${it.groupValues[1]}${KoreanBreak.NBSP}${it.groupValues[2]}" }

/** 이 길이 이하만 한 덩어리로 묶는다 — 긴 이름(`Familydestinationsguide.com Images`)은 띄어쓰기에서 끊는 편이 낫다 */
private const val KEEP_TOGETHER_MAX = 20

/** 한 덩어리로 읽어야 하는 짧은 이름·라이선스(`CC BY 2.0`, `Kil Hyung-jin`): 띄어쓰기·하이픈에서 끊지 않는다 */
fun keepTogether(text: String): String =
    if (text.length > KEEP_TOGETHER_MAX) text else text.replace(' ', KoreanBreak.NBSP).replace("-", "-${KoreanBreak.WORD_JOINER}")

/** [marks] 글자 뒤에 줄을 바꿀 수 있는 자리(ZERO WIDTH SPACE)를 둔다 — 띄어쓰기 없는 긴 파일 이름·주소 */
fun breakAfter(text: String, marks: String): String =
    buildString(text.length + 8) {
        text.forEach { c ->
            append(c)
            if (c in marks) append(KoreanBreak.ZERO_WIDTH_SPACE)
        }
    }

/** 주소·호스트(`tdac.immigration.go.th`): 한 줄에 안 들어가면 점 뒤에서 끊는다 */
fun breakAfterDots(text: String): String = breakAfter(text, ".")

/**
 * 한국어 줄바꿈을 보정한 Text ([koDisplay]). 보정이 없으면 보통 Text와 같다.
 * 보이는 글자에만 보이지 않는 문자가 들어가고, TalkBack·테스트가 읽는 의미 글자는 언제나 [text] 원문이다.
 * [display]: 보일 글자를 직접 준다(이름·라이선스를 한 덩어리로 묶은 문장 등). [heading]: TalkBack 제목.
 * 인자 순서는 1단계 E 묶음의 KoText(text, style, modifier, …)를 그대로 따른다(위치 인자로 부르는 곳이 많다) —
 * 그래서 Compose lint ModifierParameter(modifier가 첫 선택 인자)는 이 함수에서만 끈다.
 */
@Suppress("ModifierParameter")
@Composable
fun KoText(
    text: String,
    style: TextStyle = LocalTextStyle.current,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    heading: Boolean = false,
    glueShort: Boolean = false,
    display: String? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val shown = remember(text, glueShort, display) { display ?: koDisplay(text, glueShort) }
    val semantic = when {
        shown != text && heading -> Modifier.semantics {
            this.text = AnnotatedString(text)
            heading()
        }
        shown != text -> Modifier.semantics { this.text = AnnotatedString(text) }
        heading -> Modifier.semantics { heading() }
        else -> Modifier
    }
    Text(
        shown,
        modifier = modifier.then(semantic),
        color = color,
        style = style,
        textAlign = textAlign,
        onTextLayout = onTextLayout ?: {},
    )
}

/** 버튼(누를 수 있는 노드) 라벨을 직접 보정했으면 이름은 원문으로 — 버튼 동작 semantics는 그대로 둔다 */
fun Modifier.koDescription(original: String, shown: String): Modifier =
    if (shown == original) this else semantics { contentDescription = original }

// ---------------- 큰 글자 ----------------
// 큰 글자 배치 판정(예전 largeFont()·hugeFont())은 Layout.kt의 rememberLayoutInfo() 한 곳으로 옮겼다 (재검토 R5).

/**
 * 글 첫 줄 맨 앞에 아이콘을 글자처럼 넣은 Text (큰 글자 배치에서 아이콘 열이 글 폭을 뺏지 않게 — 아이콘을 빼지 않고 자리만 바꾼다).
 * 보이는 글자는 [koDisplay] 보정, 의미 글자(TalkBack·테스트)는 [text] 원문. 아이콘은 장식(설명 없음).
 */
@Composable
fun LeadIconText(
    text: String,
    icon: ImageVector,
    style: TextStyle,
    color: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    val shown = remember(text) { koDisplay(text) }
    Text(
        buildAnnotatedString {
            appendInlineContent(LEAD_ICON, "[i]")
            append(' ')
            append(shown)
        },
        modifier = modifier.semantics { this.text = AnnotatedString(text) },
        style = style,
        color = color,
        inlineContent = mapOf(
            LEAD_ICON to InlineTextContent(Placeholder(1.1.em, 1.1.em, PlaceholderVerticalAlign.TextCenter)) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.fillMaxSize())
            },
        ),
    )
}

private const val LEAD_ICON = "lead-icon"

/** 글자 옆 아이콘이 글자를 따라 커지는 최대 배율 */
private const val MAX_TEXT_ICON_SCALE = 1.5f

/**
 * 글자 옆 아이콘 크기 (DESIGN_SPEC 3.6 보완): 시스템 글자 크기를 따라 커진다(최대 1.5배) — 200%에서 버튼·태그·단계 아이콘만
 * 작게 남지 않게. [style]의 실제 글자 크기(dp ÷ sp)로 재므로 API 34+의 비선형 확대도 그대로 따른다.
 * IconBadge(배지)는 dp 고정이라 쓰지 않는다(4.2).
 */
@Composable
@ReadOnlyComposable
fun textIconSize(base: Dp, style: TextStyle = MaterialTheme.typography.bodyLarge): Dp {
    val fontSize = style.fontSize
    if (!fontSize.isSp || fontSize.value <= 0f) return base
    val actual = with(LocalDensity.current) { fontSize.toDp() }.value
    return base * (actual / fontSize.value).coerceIn(1f, MAX_TEXT_ICON_SCALE)
}

/** 여러 줄 글 옆 아이콘을 **첫 줄 가운데**에 맞추는 위 여백 (Row(Alignment.Top) 안에서) */
@Composable
@ReadOnlyComposable
fun firstLineIconOffset(style: TextStyle, iconSize: Dp): Dp {
    val line = style.lineHeight
    if (!line.isSp) return 0.dp
    val lineDp = with(LocalDensity.current) { line.toDp() }
    return ((lineDp - iconSize) / 2).coerceAtLeast(0.dp)
}

// ---------------- 배지·제목·끝 요소 배치 ----------------

/**
 * [badge] [title] [trailing] 한 줄 + 그 아래 [below].
 * - [stack](큰 글자)이면 제목이 그 사이 한 줄에 다 들어가지 않을 때 배지·끝 요소만 윗줄에 두고 제목을 아래 줄 **폭 전체**로 내린다 —
 *   좁은 칸에서 제목이 음절 단위로 쪼개지지 않게. [below]도 폭 전체(배지 아래까지).
 * - 아니면 목록 행처럼 배지 옆에 제목, [below]는 제목 자리에서 시작해 **끝 요소 아래까지** 넓힌다(오른쪽 끝이 들쭉날쭉하지 않게).
 * 설명이 없는 한 줄 행은 세로 가운데, 설명이 있으면 위 맞춤 (4.2).
 */
@Composable
fun BadgeTitleLayout(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    stack: Boolean = false,
    gap: Dp = 16.dp,
) {
    Layout(
        contents = listOf<@Composable () -> Unit>(badge ?: {}, title, trailing ?: {}, below ?: {}),
        modifier = modifier,
    ) { (badgeMs, titleMs, trailMs, belowMs), constraints ->
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else titleMs.first().maxIntrinsicWidth(Constraints.Infinity)
        val g = gap.roundToPx()
        val loose = Constraints(maxWidth = w)
        val b = badgeMs.firstOrNull()?.measure(loose)
        val t = trailMs.firstOrNull()?.measure(loose)
        val bw = b?.let { it.width + g } ?: 0
        val tw = t?.let { it.width + g } ?: 0
        val besideW = (w - bw - tw).coerceAtLeast(0)
        val titleM = titleMs.first()
        val stacked = stack && (b != null || t != null) && titleM.maxIntrinsicWidth(Constraints.Infinity) > besideW
        val small = 2.dp.roundToPx()
        if (stacked) {
            val topH = maxOf(b?.height ?: 0, t?.height ?: 0)
            val tp = titleM.measure(Constraints(maxWidth = w))
            val titleY = topH + 8.dp.roundToPx()
            val bp = belowMs.firstOrNull()?.measure(Constraints(maxWidth = w))
            val belowY = titleY + tp.height + small
            val h = maxOf(bp?.let { belowY + it.height } ?: (titleY + tp.height), constraints.minHeight)
            layout(w, h) {
                b?.placeRelative(0, (topH - b.height) / 2)
                t?.placeRelative(w - t.width, (topH - t.height) / 2)
                tp.placeRelative(0, titleY)
                bp?.placeRelative(0, belowY)
            }
        } else {
            val tp = titleM.measure(Constraints(maxWidth = besideW))
            val sideH = maxOf(b?.height ?: 0, t?.height ?: 0)
            val headH = maxOf(sideH, tp.height)
            // 한 줄 행(설명 없음)은 세로 가운데. 설명이 있으면 배지·제목·끝 요소의 위를 맞춘다
            val center = belowMs.isEmpty()
            fun y(h: Int) = if (center) (headH - h) / 2 else 0
            val belowX = if (stack) 0 else bw
            val bp = belowMs.firstOrNull()?.measure(Constraints(maxWidth = (w - belowX).coerceAtLeast(0)))
            val belowY = when {
                bp == null -> 0
                // 큰 글자: 배지 아래까지 폭 전체로
                stack -> headH + 8.dp.roundToPx()
                // 끝 요소(스위치 등) 아래부터는 오른쪽 끝까지 넓힌다
                else -> maxOf(tp.height + small, (t?.height ?: 0) + small)
            }
            val h = maxOf(headH, bp?.let { belowY + it.height } ?: 0, constraints.minHeight)
            layout(w, h) {
                b?.placeRelative(0, y(b.height))
                tp.placeRelative(bw, y(tp.height))
                t?.placeRelative(w - t.width, y(t.height))
                bp?.placeRelative(belowX, belowY)
            }
        }
    }
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
