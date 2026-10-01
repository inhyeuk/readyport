package com.readyport.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.readyport.R
import java.time.LocalDate

// ======================= 입국 카드 한 장 (다듬기 D0 — 재검토2 ④#1·②#5) =======================
// 나라 입국·비자(03·04)와 여행 준비(18)가 따로 그리던 같은 양식 카드(CountryScreen.FormCard · TabScreens.FormCard)를 하나로.
// 말도 하나로: 이 서류는 앱 어디서나 **입국 카드**다(공식 이름에 '신고'가 든 All Indonesia·MDAC 같은 고유 이름만 팩 원문 그대로).
// - eyebrow: 나라 화면 `온라인 입국 카드`(인도네시아처럼 순서가 있으면 `1단계 · 온라인 입국 카드`), 여행 준비 `태국 · 도착 전에 내요`
// - 비용: 짧은 값이면 정보 칩 `무료 입국 카드 비용`(fact_label_form_fee), 아니면 `비용: …` 글 행
// - 버튼: 화면과 상관없이 `입국 카드 준비하기`(prepare_form_open) — 같은 양식이면 03·18의 비용 라벨·버튼 라벨이 같다

/**
 * 입국 카드(온라인 입국 신고 양식) 카드: eyebrow + 제목(양식 이름) → [body](있으면) → 비용(짧은 값이면 정보 칩 + 나머지 원문 글 행) →
 * 내는 때(팩 문장 그대로 — 기간 칩·타일은 D11에 따라 만들지 않는다, `5월 4일`의 달·일은 보이는 자리에서만 묶음) → 버튼 → 출처.
 * [primary]: 화면의 주 버튼이면 Accent 채움, 아니면 보조 버튼(원칙 7 — 화면당 주 버튼 하나).
 */
@Composable
fun EntryFormCard(
    name: String,
    feeKo: String,
    windowKo: String,
    source: SourceRef,
    eyebrow: String,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    primary: Boolean = true,
) {
    CardNewsCard(
        title = name,
        icon = Icons.Outlined.AssignmentInd,
        modifier = modifier,
        eyebrow = eyebrow,
        body = body,
        sources = listOf(source),
    ) {
        val fee = shortValue(feeKo)
        if (fee != null) {
            InfoChip(stringResource(R.string.fact_label_form_fee), feeIcon(fee), value = fee, tone = feeTone(fee))
            feeRest(feeKo, fee)?.let { IconBullet(it, Icons.Outlined.Payments) }
        } else {
            IconBullet(stringResource(R.string.guide_form_fee, feeKo), Icons.Outlined.Payments)
        }
        val window = stringResource(R.string.guide_form_window, windowKo)
        IconBullet(window, Icons.Outlined.Schedule, display = koDisplay(keepMonthDay(window)))
        val label = stringResource(R.string.prepare_form_open)
        if (primary) {
            PrimaryButton(label, onClick = onStart, icon = Icons.Outlined.EditNote, modifier = Modifier.padding(top = 4.dp))
        } else {
            SecondaryButton(label, onClick = onStart, icon = Icons.Outlined.EditNote, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ======================= 내 여행 날짜로 '내는 때' (다듬기 S — 03·18 공통, 재검토2 ③#5) =======================

/** 팩 '내는 때' 문장에서 일반 예시(`예: 5월 4일 도착이면 …` 문장, `(예: …)` 괄호)를 뺀 규칙 문장. 예시가 없으면 그대로 */
fun windowRuleOnly(windowKo: String): String {
    val t = windowKo.trim().replace(ExampleParen, "")
    return t.split(SentenceBreak).filterNot { ExampleLead.containsMatchIn(it) }.joinToString(" ").trim()
}

private val ExampleParen = Regex("""\s*\(\s*예\s*:[^)]*\)""")
private val ExampleLead = Regex("""^\s*예\s*:""")
private val SentenceBreak = Regex("""(?<=\.)\s+""")

/**
 * 도착일 [arrival]을 포함해 [days]일 동안 낼 수 있는 기간 (첫날, 도착일) — 오늘 단계(TripStages)·알림 예약·내 여행 할 일 칩과 같은 계산.
 * 값은 팩 window_days_including_arrival 그대로, 앱은 날짜만 센다.
 */
fun formWindowRange(arrival: LocalDate, days: Int): Pair<LocalDate, LocalDate> =
    arrival.minusDays((days - 1).coerceAtLeast(0).toLong()) to arrival

/**
 * 입국 카드 '내는 때' 글 (나라 입국 03·04와 여행 준비 18이 함께 쓴다): 내 여행([arrival])과 팩 기간 일수([days])가 있으면
 * 일반 예시 대신 내 날짜 — `태국에 도착하는 날을 포함해 3일 안에 내요.⏎내 여행: 11월 3일에 도착하면 11월 1일~3일`.
 * 날짜는 앱이 계산한다(값을 지어내지 않음 — 팩 일수 + 내 여행 날짜). 둘 중 하나라도 없으면 팩 문장 그대로.
 *
 * **가정: 도착일 = 여행 출발일.** 앱은 비행 시간·도착 날짜를 모른다(내 여행에는 출발일·귀국일만 있다). 그래서 [arrival]에는
 * 여행 출발일을 넘기고(TripStages·알림 예약과 같은 기준), 문구는 단정하지 않고 `…에 도착하면`(form_window_mine)으로 조건을 밝힌다 —
 * 밤 비행기로 다음 날 도착하는 사람도 잘못 읽지 않게. 이 낱말을 `도착해요`처럼 단정형으로 바꾸지 않는다.
 * 내 날짜는 규칙 문장 뒤 제 줄에 둔다(문장 끝에 이어 붙으면 날짜가 문장 속에 묻힌다).
 */
@Composable
fun personalWindowKo(windowKo: String, days: Int?, arrival: LocalDate?): String {
    if (days == null || days < 1 || arrival == null) return windowKo
    val (from, to) = formWindowRange(arrival, days)
    val range = formWindowKo(from, to)
    val mine = stringResource(R.string.form_window_mine, stringResource(R.string.date_month_day, arrival.monthValue, arrival.dayOfMonth), range)
    val rule = windowRuleOnly(windowKo)
    return if (rule.isEmpty()) mine else "$rule\n$mine"
}

/**
 * 입국 카드를 **낼 수 있는 기간** 한 줄: 같은 달이면 `11월 1일~3일`, 달이 바뀌면 `10월 31일~11월 2일`, 하루면 `11월 3일`.
 * 앱 전체에서 이 한 가지 모양만 쓴다(나라 입국 03·04 · 여행 준비 18 · 내 여행 10 할 일 칩) —
 * 전에는 같은 기간이 화면에 따라 `11월 1일~3일`과 `11월 1일 ~ 3일` 두 모양으로 보였다(v3에서 좁은 쪽으로 통일).
 * 여행 날짜 줄(`11월 3일 ~ 7일`, today_trip_dates)은 '여행 기간'이라는 다른 역할이라 그대로 둔다.
 */
@Composable
fun formWindowKo(from: LocalDate, to: LocalDate): String = when {
    from == to -> stringResource(R.string.date_month_day, to.monthValue, to.dayOfMonth)
    from.monthValue == to.monthValue -> stringResource(R.string.date_range_same_month, from.monthValue, from.dayOfMonth, to.dayOfMonth)
    else -> stringResource(R.string.date_range_two_months, from.monthValue, from.dayOfMonth, to.monthValue, to.dayOfMonth)
}

/** 비용 값의 색: 정확히 `무료`면 Success, 나머지 Accent */
fun feeTone(value: String): BadgeTone = if (value == "무료") BadgeTone.Success else BadgeTone.Accent

/** 팩 비용 원문에서 짧은 값([short]) 뒤에 이어지는 나머지 문장 (`무료. 돈을 받는 …` → `돈을 받는 …`). 없으면 null — 글자는 하나도 빼지 않는다 */
fun feeRest(full: String, short: String): String? {
    val t = full.trim()
    if (!t.startsWith(short)) return null
    return t.substring(short.length).trimStart('.', '·', ' ').trim().takeIf { it.isNotEmpty() }
}
