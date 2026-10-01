package com.readyport.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

// ======================= 숫자 먼저 보기 (재검토 R14·재검토2 ③#1) =======================
// 팩 문장 속 숫자 + 단위 토큰(`800달러`, `1,000밧`, `미화 1만 5천 달러`, `160Wh`, `3단계`)을 굵게만 바꾼다 — 값을 만들거나 바꾸지 않는다(D11).
// 팩 문장을 그리는 공용 부품(DotBullet·IconBullet·CardNewsCard 본문·StatTile 라벨·StepList 문장·ReturnCheckCard·판정 메모)이
// 안에서 [NumberText]를 쓰므로, 화면은 원문 문자열을 그대로 넘기면 된다. 단위 목록은 이 한 곳에서만 관리한다.

/** 숫자 (`1,000`, `2026.09.29`, `1.5`) */
private const val NUM = """\d+(?:[,.]\d+)*"""

/** 큰 수 자리 (`1만 5천`, `1억`) */
private const val MULT = """(?:만|천|억|백)"""

/** 통화 (띄어 써도 단위로 — `1,000만 원`, `5천 달러`). 긴 것 먼저 */
private val CurrencyUnits = listOf("싱가포르 달러", "루피아", "달러", "링깃", "위안", "페소", "유로", "바트", "밧", "엔", "원")

/** 한국어 단위 (붙여 쓸 때만 — `3 인도네시아`의 `인`처럼 다음 낱말 첫 글자를 단위로 읽지 않게). 긴 것 먼저 */
private val KoreanUnits = listOf("단계", "개비", "개월", "시간", "개", "세", "일", "월", "년", "박", "분", "주", "명", "인", "회", "번", "칸", "곳", "%", "％")

/** 영문 단위 (띄어 써도 되지만 뒤에 영문자가 이어지지 않을 때만 — `220 V`, `160Wh`). 긴 것 먼저 */
private val LatinUnits = listOf("mAh", "Wh", "ml", "mL", "kg", "km", "cm", "mm", "Hz", "kW", "V", "W", "L", "g", "m")

private fun alternatives(units: List<String>): String = units.joinToString("|") { Regex.escape(it) }

/**
 * 숫자 토큰: [통화 코드 ]숫자[큰 수 자리[ 숫자 큰 수 자리]…][단위]. 앞에 숫자가 붙은 자리(숫자 가운데)에서는 시작하지 않는다.
 * 예: `800달러`, `2L`, `200개비`, `100ml`, `1,000만 원`, `미화 1만 5천 달러`의 `1만 5천 달러`, `IDR 500,000`, `220 V`, `3단계`, `5월`·`4일`.
 */
private val NumberToken = Regex(
    "(?<![\\d,.])(?:[A-Z]{3} )?$NUM(?:$MULT(?:\\s?$NUM$MULT)*)?" +
        "(?:\\s?(?:${alternatives(CurrencyUnits)})|(?:${alternatives(KoreanUnits)})|\\s?(?:${alternatives(LatinUnits)})(?![A-Za-z]))?",
)

/** 단위·자리·통화 코드 없는 맨 숫자 */
private val BareNumber = Regex(NUM)

/**
 * [text]에서 숫자 토큰이 차지하는 자리. 단위 없는 맨 숫자(주소 번지 `23`, 우편번호 `10310`, 날짜 `2026.09.29`, 전화번호)는 굵게 하지 않는다 —
 * 값의 뜻이 단위에 있기 때문이다. 다만 `20·50·100·500·1,000밧`처럼 가운뎃점으로 이어진 숫자 목록의 앞 숫자들은 함께 굵게.
 */
fun numberRanges(text: String): List<IntRange> = NumberToken.findAll(text).filter { m ->
    !BareNumber.matches(m.value) || text.getOrNull(m.range.last + 1) == '·' && text.getOrNull(m.range.last + 2)?.isDigit() == true
}.map { it.range }.toList()

/**
 * 보이는 글자([shown] = koDisplay 보정본 — 원문에 보이지 않는 줄바꿈 문자만 끼워 넣고 띄어쓰기를 NBSP로 바꾼 것)에
 * 원문 [text]의 숫자 토큰 자리를 굵게 입힌다. 글자는 바꾸지 않는다.
 * [color]: 굵은 숫자의 색(기본은 글자색 그대로 — 어두운 카드 위에서도 보이게). 귀국 전 확인처럼 보조 글 안 숫자를 Ink로 올릴 때만 준다.
 */
fun emphasizeNumbers(text: String, shown: String, color: Color = Color.Unspecified): AnnotatedString {
    val ranges = numberRanges(text)
    if (ranges.isEmpty()) return AnnotatedString(shown)
    // 보이는 글자 i → 원문 j (끼워 넣은 글자는 앞 원문 글자에 붙인다)
    val origin = IntArray(shown.length)
    var j = 0
    shown.forEachIndexed { i, c ->
        val same = j < text.length && (c == text[j] || (c == KoreanBreak.NBSP && text[j] == ' '))
        origin[i] = if (same) j++ else (j - 1).coerceAtLeast(0)
    }
    val bold = SpanStyle(fontWeight = FontWeight.Bold, color = color)
    return buildAnnotatedString {
        append(shown)
        var start = -1
        for (i in shown.indices) {
            val on = ranges.any { origin[i] in it }
            if (on && start < 0) start = i
            if (!on && start >= 0) {
                addStyle(bold, start, i)
                start = -1
            }
        }
        if (start >= 0) addStyle(bold, start, shown.length)
    }
}

/**
 * 팩 문장 글자: [KoText]와 같은 한국어 줄바꿈 보정 + 숫자 토큰 굵게([emphasizeNumbers]). 의미 글자(TalkBack·테스트)는 원문 [text].
 * [display]: 보일 글자를 직접 준다(날짜 묶음 등 — 원문과 글자는 같고 띄어쓰기·보이지 않는 문자만 다른 것).
 */
@Suppress("ModifierParameter")
@Composable
fun NumberText(
    text: String,
    style: TextStyle = LocalTextStyle.current,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    display: String? = null,
    emphasisColor: Color = Color.Unspecified,
) {
    val shown = remember(text, display) { display ?: koDisplay(text) }
    val styled = remember(text, shown, emphasisColor) { emphasizeNumbers(text, shown, emphasisColor) }
    Text(
        styled,
        modifier = if (shown != text) modifier.semantics { this.text = AnnotatedString(text) } else modifier,
        color = color,
        style = style,
        textAlign = textAlign,
    )
}
