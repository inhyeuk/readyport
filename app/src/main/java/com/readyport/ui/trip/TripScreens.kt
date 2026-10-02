package com.readyport.ui.trip

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.Airport
import com.readyport.pack.IndexCountry
import com.readyport.pack.PackRepository
import com.readyport.pack.PackSync
import com.readyport.trip.Trip
import com.readyport.trip.TripRepository
import com.readyport.ui.components.AirportRadioList
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.ButtonPlacement
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.Fact
import com.readyport.ui.components.FactChip
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LocalTileColumns
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SelectableCard
import com.readyport.ui.components.TileGrid
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.rememberPhotoLift
import com.readyport.ui.components.rememberThumbnail
import com.readyport.ui.components.sectionGap
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

/** [airports]: 나라 코드 → 그 나라 팩의 도착 공항(공항 안내가 있는 나라만) — 여행 고치기의 `내리는 공항` 선택지 */
data class TripFormUi(
    val countries: List<IndexCountry> = emptyList(),
    val existing: Trip? = null,
    val loaded: Boolean = false,
    val airports: Map<String, List<Airport>> = emptyMap(),
)

@HiltViewModel
class TripViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trips: TripRepository,
    private val packs: PackRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(TripFormUi())
    val ui: StateFlow<TripFormUi> = _ui.asStateFlow()

    /** [tripId]가 있으면 그 여행 고치기, 없으면 새 여행 만들기 (여행은 id로 가린다 — 같은 나라라도 다른 여행) */
    fun load(tripId: String?) = viewModelScope.launch {
        val countries = packs.index()?.value?.countries.orEmpty().filter { it.pack }
        val airports = countries.mapNotNull { c -> packs.pack(c.code)?.value?.airports?.takeIf { it.isNotEmpty() }?.let { c.code to it } }.toMap()
        _ui.value = TripFormUi(countries, tripId?.let { trips.get(it) }, loaded = true, airports = airports)
    }

    /**
     * 여행 저장 + 나라 찜(안내 받아 두기) + 입국 카드 알림 다시 맞추기. 새 여행이면 새 id로 더한다. 저장한 여행 id를 [onSaved]로.
     * 고칠 때 나라·출발일이 그대로면 도착 기록을 지킨다. 체크 상태는 여행 id에 붙어 있어 날짜를 고쳐도 남는다.
     */
    fun save(country: String, start: LocalDate, end: LocalDate, airport: String? = null, onSaved: (String) -> Unit = {}) = viewModelScope.launch {
        val old = _ui.value.existing
        val keep = old?.takeIf { it.country == country && it.startDate == start.toString() }
        val saved = trips.save(
            (old ?: Trip(country = country, startDate = start.toString(), endDate = end.toString())).copy(
                country = country,
                startDate = start.toString(),
                endDate = end.toString(),
                arrivedAt = keep?.arrivedAt,
                arrivalDismissed = keep?.arrivalDismissed ?: false,
                // 그 나라 팩에 있는 공항만(나라를 바꾸면 이전 나라 공항은 지워진다)
                arrivalAirport = airport?.takeIf { code -> _ui.value.airports[country].orEmpty().any { it.code == code } },
            ),
        )
        settings.setFavorite(country, true)
        PackSync.requestNow(context, settings.current().wifiOnly)
        rescheduleFormReminder(context, trips, packs)
        onSaved(saved.id)
    }

    fun delete() = viewModelScope.launch {
        _ui.value.existing?.let { trips.delete(it.id) }
        rescheduleFormReminder(context, trips, packs)
    }
}

@Composable
fun TripScreen(
    onDone: () -> Unit,
    initialCountry: String? = null,
    tripId: String? = null,
    /** 새 여행을 만들었을 때(그 여행 체크리스트로 가기) — 없으면 [onDone] */
    onCreated: ((String) -> Unit)? = null,
    viewModel: TripViewModel = hiltViewModel(),
) {
    LaunchedEffect(tripId) { viewModel.load(tripId) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Android 13+ 알림 권한 (입국 카드 제출 가능일 알림)
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (!ui.loaded) return
    val creating = ui.existing == null
    TripContent(
        ui = ui,
        onSave = { c, s, e, a ->
            viewModel.save(c, s, e, a) { id -> if (creating && onCreated != null) onCreated(id) else onDone() }
            if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onDelete = { viewModel.delete(); onDone() },
        initialCountry = initialCountry,
    )
}

/**
 * 여행 만들기·고치기 (DESIGN_SPEC 6-14).
 * 나라 = 사진 썸네일 라디오 카드 2열(쉬운 모드·큰 글자 1열), 날짜 = 숫자 자판 입력(하이픈은 칸이 그림) + `11월 3일 (화)` 확인 글(D20 —
 * 가로 스와이프 달력을 쓰지 않는다), 기간 칩(4박 5일), 저장 버튼 하나, 지우기는 빨간 버튼 + 확인 대화상자(D8).
 */
@Composable
fun TripContent(
    ui: TripFormUi,
    /** 나라, 출발일, 돌아오는 날, 내리는 공항(모르면 null) */
    onSave: (String, LocalDate, LocalDate, String?) -> Unit,
    onDelete: () -> Unit,
    /** 나라 화면의 '이 나라로 여행 계획 만들기'로 오면 그 나라를 미리 골라 둔다 */
    initialCountry: String? = null,
) {
    var country by remember(ui.existing) {
        mutableStateOf(initialCountry?.takeIf { c -> ui.countries.any { it.code == c } } ?: ui.existing?.country ?: ui.countries.singleOrNull()?.code)
    }
    // 날짜 칸은 숫자만 받는다(숫자 자판, 하이픈은 보이는 글자에만 — 재검토 R18). 저장값 형식(YYYY-MM-DD)은 그대로
    var start by remember(ui.existing) { mutableStateOf(dateDigits(ui.existing?.startDate)) }
    var end by remember(ui.existing) { mutableStateOf(dateDigits(ui.existing?.endDate)) }
    var airport by remember(ui.existing) { mutableStateOf(ui.existing?.arrivalAirport) }
    var invalid by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(start, end) { invalid = false }
    val columns = rememberGridColumns()
    val startDate = parseDigits(start)
    val endDate = parseDigits(end)
    val nights = if (startDate != null && endDate != null) ChronoUnit.DAYS.between(startDate, endDate).toInt().takeIf { it >= 0 } else null
    // 고른 나라 팩의 공항만 — 나라를 바꾸면 그 나라에 없는 공항은 `아직 몰라요`로 보인다
    val airportOptions = ui.airports[country].orEmpty()
    val pickedAirport = airport?.takeIf { code -> airportOptions.any { it.code == code } }

    AppScreen(
        title = stringResource(if (ui.existing == null) R.string.trip_create_title else R.string.trip_edit_title),
        subtitle = stringResource(R.string.trip_create_lead),
        speech = stringResource(R.string.trip_create_body),
    ) {
        if (ui.countries.isEmpty()) {
            item(key = "none") { NoticeBanner(stringResource(R.string.trip_no_country), icon = Icons.Outlined.TravelExplore) }
            return@AppScreen
        }
        item(key = "country-title") {
            SectionHeader(stringResource(R.string.trip_country), icon = Icons.Outlined.TravelExplore)
        }
        item(key = "countries") {
            TileGrid(ui.countries, Modifier.selectableGroup(), columns) { c, cell ->
                CountryRadioCard(c, selected = country == c.code, onClick = { country = c.code }, modifier = cell)
            }
        }
        sectionGap("dates-gap")
        item(key = "dates-title") {
            // 날짜 칸 아이콘(CalendarMonth, 5.1) — EditCalendar는 '여행 만들기·고치기' 동작에만
            SectionHeader(stringResource(R.string.trip_dates_title), icon = Icons.Outlined.CalendarMonth)
        }
        item(key = "dates") {
            // 날짜 칸을 흰 카드 안에 둔다: OutlinedTextField의 라벨 홈(notch)이 뒤 바탕을 비추므로, 회색 Ground 위에 흰 칸을 두면
            // 라벨 뒤에 회색 조각이 칸 안까지 내려와 보인다. 카드 바탕(Surface) = 칸 바탕이라 홈이 이어져 보인다.
            val dimens = LocalDimens.current
            val shape = MaterialTheme.shapes.large
            Surface(color = Tokens.Surface, shape = shape, modifier = Modifier.fillMaxWidth().cardShadow(shape)) {
                Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.gap)) {
                    DateField(R.string.trip_start, start, startDate, error = invalid && startDate == null) { start = it }
                    DateField(R.string.trip_end, end, endDate, error = invalid && (endDate == null || nights == null)) { end = it }
                    if (nights != null) {
                        FactChip(
                            Fact(
                                icon = Icons.Outlined.DateRange,
                                value = stringResource(R.string.trip_nights, nights, nights + 1),
                                label = stringResource(R.string.trip_length_label),
                            ),
                        )
                    }
                }
            }
        }
        // 내리는 공항 (2026-10-03): 그 나라 팩에 공항 안내가 있을 때만. 고르면 출국·도착 단계에 그 공항 순서를 보인다
        if (airportOptions.isNotEmpty()) {
            sectionGap("airport-gap")
            item(key = "airport-title") {
                SectionHeader(stringResource(R.string.trip_airport_title), icon = Icons.Outlined.FlightLand)
            }
            item(key = "airport") {
                Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner)) {
                    AirportRadioList(
                        airports = airportOptions,
                        selected = pickedAirport,
                        onSelect = { airport = it },
                        unknownLabel = stringResource(R.string.trip_airport_unknown),
                    )
                    KoText(stringResource(R.string.trip_airport_hint), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                }
            }
        }
        if (invalid) {
            item(key = "invalid") {
                NoticeBanner(stringResource(R.string.trip_invalid), icon = Icons.Outlined.ErrorOutline, tone = BannerTone.Caution)
            }
        }
        item(key = "local") { IconBullet(stringResource(R.string.trip_local_only), Icons.Outlined.Lock) }
        item(key = "save") {
            PrimaryButton(
                text = stringResource(if (ui.existing == null) R.string.trip_save else R.string.trip_save_edit),
                enabled = country != null,
                // 만들기 = 여행 만들기(EditCalendar), 고치기 = 저장하기(Check) — 날짜 머리·여행 고치기 버튼과 아이콘이 겹치지 않게
                icon = if (ui.existing == null) Icons.Outlined.EditCalendar else Icons.Outlined.Check,
                onClick = {
                    val s = parseDigits(start)
                    val e = parseDigits(end)
                    if (country == null || s == null || e == null || e.isBefore(s)) invalid = true else onSave(country!!, s, e, pickedAirport)
                },
            )
        }
        if (ui.existing != null) {
            sectionGap("delete-gap")
            item(key = "delete") {
                DangerButton(stringResource(R.string.trip_delete), onClick = { confirmDelete = true }, placement = ButtonPlacement.CardAction)
            }
        }
    }

    if (confirmDelete) {
        DestructiveConfirm(
            title = stringResource(R.string.trip_delete_confirm_title),
            body = stringResource(R.string.ck_delete_body),
            confirmLabel = stringResource(R.string.trip_delete_confirm),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** 저장된 날짜(YYYY-MM-DD)에서 숫자 8자리만 (날짜 칸이 다루는 값) */
internal fun dateDigits(text: String?): String = text.orEmpty().filter(Char::isDigit).take(DATE_DIGITS)

/** 숫자 8자리(YYYYMMDD) → 날짜. 자리가 모자라거나 없는 날이면 null */
internal fun parseDigits(digits: String): LocalDate? {
    if (digits.length != DATE_DIGITS) return null
    return runCatching { LocalDate.parse("${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}") }.getOrNull()
}

private const val DATE_DIGITS = 8

/**
 * 숫자만 적는 날짜 칸에 하이픈을 그려 준다(`20261103` → `2026-11-03`). 커서 자리도 하이픈을 건너뛰게 맞춘다.
 * 숫자 자판만으로 적을 수 있어 시니어가 숫자·하이픈 자판을 오가지 않는다 (재검토 R18).
 */
internal object DateDigitsTransformation : VisualTransformation {
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
                (offset - (if (offset > 4) 1 else 0) - (if (offset > 7) 1 else 0)).coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

/** 11월 3일 (화) */
@Composable
private fun datePreview(date: LocalDate): String =
    stringResource(R.string.trip_date_preview, date.monthValue, date.dayOfMonth, date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN))

/**
 * 나라 선택 카드 = 공용 SelectableCard(재검토 R2 규칙 ②): 원형 사진 썸네일(장식) + 한국어·영어 이름.
 * 선택 = AccentSoft 바탕 + 2dp Accent 테두리 + 채운 CheckCircle, 아님 = 흰 바탕 + 1dp LineStrong(조작 요소 경계) + 빈 원.
 * 놓인 그리드가 1열이면 가로, 여러 칸이면 세로.
 */
@Composable
private fun CountryRadioCard(c: IndexCountry, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    val horizontal = (LocalTileColumns.current ?: 1) == 1
    val thumbSize = if (dimens.easyMode) 56.dp else 48.dp
    SelectableCard(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        leading = { CountryThumb(c.code, thumbSize) },
        vertical = !horizontal,
        minHeight = if (horizontal) dimens.tileRowMinHeight else dimens.tileMinHeight,
        contentPadding = PaddingValues(12.dp),
    ) {
        CountryNames(c, if (horizontal) TextAlign.Start else TextAlign.Center)
    }
}

@Composable
private fun CountryThumb(code: String, size: Dp) {
    val thumb = rememberThumbnail(Photos.country(code), size)
    Box(Modifier.size(size).clip(CircleShape).background(Tokens.Navy)) {
        if (thumb != null) {
            Image(
                thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberPhotoLift(thumb),
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun CountryNames(c: IndexCountry, align: TextAlign, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KoText(c.nameKo, style = MaterialTheme.typography.titleMedium, color = Tokens.Ink, textAlign = align, modifier = Modifier.fillMaxWidth())
        Text(c.nameEn, style = MaterialTheme.typography.bodySmall, color = Tokens.InkSecondary, textAlign = align, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * 날짜 칸 (D20 + 재검토 R18): 숫자 자판으로 8자리만 적으면 하이픈은 칸이 그려 준다(`2026-11-03`).
 * 값이 올바르면 `11월 3일 (화)` 확인 글(보조 글이라 InkSecondary — 파란 글자는 링크처럼 보였다).
 * 앞 달력 아이콘은 누르면 달력이 열릴 것처럼 보여 뺐다(섹션 머리에 같은 아이콘이 있다).
 */
@Composable
private fun DateField(@StringRes label: Int, value: String, parsed: LocalDate?, error: Boolean, onChange: (String) -> Unit) {
    val preview: (@Composable () -> Unit)? = if (parsed != null) {
        { KoText(datePreview(parsed)) }
    } else {
        null
    }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(dateDigits(it)) },
        label = { KoText(stringResource(label)) },
        placeholder = { KoText(stringResource(R.string.trip_date_hint)) },
        supportingText = preview,
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = DateDigitsTransformation,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Tokens.Surface,
            unfocusedContainerColor = Tokens.Surface,
            errorContainerColor = Tokens.Surface,
            unfocusedBorderColor = Tokens.LineStrong,
            focusedBorderColor = Tokens.Accent,
            errorBorderColor = Tokens.DangerText,
            focusedSupportingTextColor = Tokens.InkSecondary,
            unfocusedSupportingTextColor = Tokens.InkSecondary,
            errorSupportingTextColor = Tokens.DangerText,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
