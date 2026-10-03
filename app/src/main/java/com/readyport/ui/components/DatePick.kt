package com.readyport.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

// =====================================================================================
// 날짜 칸 하나 (2026-10-03, 다듬기 S2)
//
// 운영자 요청: *"그리고 날짜를 입력할 때, 달력에서 선택할 수 있도록 해줘"*
//
// 예전에는 날짜를 **숫자 자판으로만** 적었다(재검토 R18 — 숫자·기호 자판을 오가지 않게, D20 — 가로 스와이프 달력 금지).
// 이제 **달력이 주 입력**이고 숫자로 적는 길은 그대로 남는다(빠른 사람·TalkBack·200% 글자).
//  ① 칸을 누르거나 끝 달력 단추를 누르면 Material 3 달력 대화상자가 열린다
//  ② 대화상자의 `숫자로 적기`를 누르면 그 칸이 숫자 자판 칸으로 바뀐다(값은 그대로 이어진다)
//  ③ 저장 값은 예전과 같은 숫자 8자리(YYYYMMDD) — 화면에서는 `2026-11-03`, 아래 확인 글은 `11월 3일 (화)`
//
// **앱 전체의 모든 날짜 입력이 이 파일 하나를 쓴다**: 여행 떠나는 날·돌아오는 날, 숙소 체크인·체크아웃,
// 예약 서류 체크인·체크아웃, 여권 생년월일·만료일. 날짜 글자를 다루는 순수 함수도 여기 한 곳에 모았다
// (예전에는 ui/trip과 ui/wallet에 같은 함수가 두 벌 있었다).
// =====================================================================================

/** 날짜 칸이 다루는 숫자 자리수 (YYYYMMDD) */
private const val DATE_DIGITS = 8

/** 달력이 기본으로 보여 주는 해 범위 (Material 기본과 같다) */
private val DEFAULT_YEARS = 1900..2100

private const val MILLIS_PER_DAY = 86_400_000L

/** 날짜 칸에 받는 글자: 숫자만, 8자리까지 (붙여 넣은 `1974-08-12`도 숫자만 남긴다) */
fun dateDigits(input: String?): String = input.orEmpty().filter { it in '0'..'9' }.take(DATE_DIGITS)

/** 숫자 8자리(`19740812`) → 날짜. 자리가 모자라거나 없는 날짜면 null */
fun parseDateDigits(digits: String): LocalDate? {
    if (digits.length != DATE_DIGITS || digits.any { it !in '0'..'9' }) return null
    return runCatching {
        LocalDate.of(digits.take(4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6).toInt())
    }.getOrNull()
}

/** 날짜(`2026-11-03`) → 날짜 칸 값(`20261103`). 날짜 모양이 아니면 빈 값 */
fun digitsOf(date: String?): String =
    date?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.toString()?.let(::dateDigits).orEmpty()

/** 날짜 → 날짜 칸 값 */
fun digitsOf(date: LocalDate?): String = date?.let { dateDigits(it.toString()) }.orEmpty()

/**
 * 숫자만 저장한 날짜 칸을 `YYYY-MM-DD`로 보이게 한다 — 넷째·여섯째 숫자 뒤에 하이픈(그 뒤 숫자가 있을 때만).
 * 커서 위치는 숫자 자리에 맞춰 옮긴다(하이픈 자리에서 지워도 숫자가 지워진다).
 */
object DateDigitsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = buildString {
            digits.forEachIndexed { i, c ->
                if (i == 4 || i == 6) append('-')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + (if (offset > 4) 1 else 0) + (if (offset > 6) 1 else 0)

            override fun transformedToOriginal(offset: Int): Int =
                (offset - (if (offset >= 5) 1 else 0) - (if (offset >= 8) 1 else 0)).coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

/** `11월 3일 (화)` — 칸 아래 확인 글·달력 단추 이름이 쓰는 짧은 한국어 날짜 */
@Composable
fun datePreview(date: LocalDate): String = stringResource(
    R.string.trip_date_preview,
    date.monthValue,
    date.dayOfMonth,
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
)

/**
 * 달력에 주는 테두리. **막지 않고 길잡이만 한다**가 앱의 습관이라([notBefore]만 예외),
 * 날짜가 어긋나면 화면이 띠로 알려 주고 저장 버튼이 막는다.
 *
 * @property openOn 달력을 처음 열 때 보여 줄 달 (고른 값이 있으면 그 값이 먼저다)
 * @property notBefore 이 날 **앞은 고를 수 없다** — 정말 있을 수 없는 날에만 쓴다(체크아웃이 체크인보다 빠른 경우)
 * @property years 달력에서 넘길 수 있는 해 (스크롤 범위만 줄인다)
 */
@Immutable
data class DateRules(
    val openOn: LocalDate? = null,
    val notBefore: LocalDate? = null,
    val years: IntRange = DEFAULT_YEARS,
)

/** 날짜 → 달력이 쓰는 UTC 밀리초 (달력은 시간대 없는 날짜를 UTC 자정으로 다룬다) */
internal fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** 달력이 돌려준 UTC 밀리초 → 날짜 */
internal fun pickerDateOf(millis: Long?): LocalDate? =
    millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

/**
 * 날짜 칸 하나 — **앱의 모든 날짜 입력이 이 부품을 쓴다**.
 *
 * - 기본은 달력: 칸을 누르거나 끝 달력 단추를 누르면 대화상자가 열린다. TalkBack도 칸에서 바로 `달력에서 고르기`를 실행한다.
 * - 숫자로 적기: 대화상자의 `숫자로 적기`를 누르면 그 칸이 숫자 자판 칸이 된다(하이픈은 칸이 그려 준다).
 *   한 번 숫자로 적기로 바꾸면 그 칸은 계속 숫자 칸이다 — 누를 때마다 달력이 다시 튀어나오지 않게.
 * - 값이 올바르면 칸 아래 `11월 3일 (화)` 확인 글(보조 글이라 InkSecondary).
 *
 * @param label 짧은 라벨 (`떠나는 날`) — 테두리 홈에 한 줄로 들어가는 길이
 * @param value 숫자 8자리 (YYYYMMDD). 반쯤 적은 값도 그대로 쥔다
 * @param note 칸 아래 설명 — 올바른 날짜를 고르면 `11월 3일 (화)`가 이 자리를 대신한다
 */
@Composable
fun DatePickField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    note: String? = null,
    error: Boolean = false,
    rules: DateRules = DateRules(),
    imeAction: ImeAction = ImeAction.Next,
) {
    val parsed = parseDateDigits(value)
    var open by remember { mutableStateOf(false) }
    // 숫자로 적기로 바꾼 칸인지 (기본은 달력)
    var typing by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var wantFocus by remember { mutableStateOf(false) }
    LaunchedEffect(wantFocus) {
        if (wantFocus) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
            wantFocus = false
        }
    }
    val openName = parsed?.let { stringResource(R.string.date_pick_open_cd, label, datePreview(it)) }
        ?: stringResource(R.string.date_pick_open_empty_cd, label)
    val typeName = stringResource(R.string.date_pick_type_cd, label)
    val support: (@Composable () -> Unit)? = when {
        parsed != null -> {
            { KoText(datePreview(parsed)) }
        }
        note != null -> {
            { KoText(note) }
        }
        else -> null
    }
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(dateDigits(it)) },
            label = { KoText(label) },
            placeholder = if (typing) {
                { KoText(stringResource(R.string.trip_date_hint)) }
            } else {
                null
            },
            leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
            trailingIcon = {
                IconButton(
                    onClick = { open = true },
                    modifier = Modifier.minTouchSize(),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Tokens.Accent),
                ) {
                    // 글자를 키우면 달력 그림도 함께 커진다(최대 1.5배) — 200%에서 점처럼 작아 보이지 않게
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = openName,
                        modifier = Modifier.size(textIconSize(LocalDimens.current.icon)),
                    )
                }
            },
            supportingText = support,
            isError = error,
            singleLine = true,
            // 달력이 주 입력인 동안은 읽기 전용 — 눌러도 자판이 올라오지 않고 커서가 움직이지 않는다
            readOnly = !typing,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
            visualTransformation = DateDigitsTransformation,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.small,
            colors = dateFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .openCalendarOnTap(enabled = !typing) { open = true }
                .then(
                    if (typing) {
                        Modifier
                    } else {
                        Modifier.semantics {
                            onClick(label = openName) {
                                open = true
                                true
                            }
                        }
                    },
                ),
        )
        // 달력 대신 숫자로 적는 길 — 칸 바로 아래 조용한 글자 단추(대화상자 안이 아니라 **화면에** 둔다:
        // 달력을 열지 않아도 보이고, 접근성 점검·갤러리 캡처도 이 단추를 본다)
        if (!typing) {
            QuietButton(
                stringResource(R.string.date_pick_type),
                onClick = {
                    typing = true
                    wantFocus = true
                },
                icon = Icons.Outlined.Dialpad,
                modifier = Modifier.semantics { contentDescription = typeName },
            )
        }
    }
    if (open) {
        DatePickDialog(
            label = label,
            selected = parsed,
            rules = rules,
            onPick = {
                onChange(digitsOf(it))
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
internal fun dateFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Tokens.Surface,
    unfocusedContainerColor = Tokens.Surface,
    errorContainerColor = Tokens.Surface,
    unfocusedBorderColor = Tokens.LineStrong,
    focusedBorderColor = Tokens.Accent,
    errorBorderColor = Tokens.DangerText,
    focusedSupportingTextColor = Tokens.InkSecondary,
    unfocusedSupportingTextColor = Tokens.InkSecondary,
    errorSupportingTextColor = Tokens.DangerText,
    focusedTextColor = Tokens.Ink,
    unfocusedTextColor = Tokens.Ink,
)

/**
 * 칸을 눌러 달력을 여는 손짓. **처음(Initial) 단계**에서만 본다 — 글자 칸·끝 단추가 눌림을 가져가도(소비해도)
 * 달력은 열린다. 누름만 소비해 글자 칸이 커서를 옮기지 않게 하고, 움직임·뗌은 그대로 흘려보내 목록 스크롤을 막지 않는다.
 * 손가락이 터치 범위(touchSlop)를 넘게 움직였으면 = 스크롤이므로 달력을 열지 않는다.
 */
private fun Modifier.openCalendarOnTap(enabled: Boolean, onOpen: () -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        this.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down.consume()
                var moved = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.positionChange().getDistance() > viewConfiguration.touchSlop) moved = true
                    if (change.changedToUpIgnoreConsumed()) {
                        if (!moved) onOpen()
                        break
                    }
                }
            }
        }
    }

/**
 * 달력 대화상자 (Material 3 [DatePicker]). 기기 언어를 따르므로 한국어 폰에서는 한국어 달력이다.
 * 단추는 `그만두기`·`이 날로 하기` 둘 — 숫자로 적는 길은 칸 아래 단추다(대화상자를 비좁게 하지 않는다).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(
    label: String,
    selected: LocalDate?,
    rules: DateRules,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.date_pick_title, label)
    val state = rememberDatePickState(selected, rules)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { pickerDateOf(state.selectedDateMillis)?.let(onPick) ?: onDismiss() },
                enabled = state.selectedDateMillis != null,
                colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Accent),
                modifier = Modifier.minTouch(),
            ) { KoText(stringResource(R.string.date_pick_confirm), MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Tokens.InkSecondary),
                modifier = Modifier.minTouch(),
            ) { KoText(stringResource(R.string.action_cancel_keep), MaterialTheme.typography.labelLarge) }
        },
        shape = MaterialTheme.shapes.extraLarge,
        colors = DatePickerDefaults.colors(containerColor = Tokens.Surface),
    ) {
        DatePickCalendar(state, title)
    }
}

/** 고른 날짜·[DateRules]로 Material 달력 상태를 만든다 (대화상자와 검토용 캡처가 함께 쓴다) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberDatePickState(selected: LocalDate?, rules: DateRules): DatePickerState = rememberDatePickerState(
    initialSelectedDateMillis = selected?.toPickerMillis(),
    initialDisplayedMonthMillis = (selected ?: rules.openOn ?: LocalDate.now()).toPickerMillis(),
    yearRange = rules.years,
    selectableDates = rememberSelectableDates(rules),
)

/**
 * 대화상자 안에 들어가는 것: 앱 색을 입힌 Material 달력 하나.
 * 내용은 스크롤로 감싼다(200% 글자·쉬운 모드에서 달력 머리글이 커져 잘리지 않게).
 * 대화상자 창을 캡처하지 못하는 테스트 환경에서도 이 부품만 따로 띄워 검토 캡처를 만들 수 있다.
 * 숫자로 적는 길은 대화상자가 아니라 **칸 아래 단추**다([DatePickField]) — 달력을 열지 않아도 보이게.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickCalendar(state: DatePickerState, title: String) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .semantics { paneTitle = title },
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner),
    ) {
        DatePicker(
            state = state,
            title = {
                KoText(
                    title,
                    MaterialTheme.typography.titleMedium,
                    Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    color = Tokens.InkSecondary,
                )
            },
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = Tokens.Surface,
                titleContentColor = Tokens.InkSecondary,
                headlineContentColor = Tokens.Ink,
                weekdayContentColor = Tokens.InkSecondary,
                selectedDayContainerColor = Tokens.Accent,
                selectedDayContentColor = Tokens.Surface,
                todayDateBorderColor = Tokens.Accent,
                todayContentColor = Tokens.Accent,
            ),
        )
    }
}

/** [DateRules.notBefore] 앞 날짜만 막는다(있을 수 없는 날). 그 밖에는 모두 고를 수 있다 — 어긋난 날짜는 화면이 띠로 알려 준다 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberSelectableDates(rules: DateRules): SelectableDates = remember(rules) {
    val floor = rules.notBefore
    object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
            floor == null || utcTimeMillis >= floor.toPickerMillis() - MILLIS_PER_DAY / 2

        override fun isSelectableYear(year: Int): Boolean = floor == null || year >= floor.year
    }
}
