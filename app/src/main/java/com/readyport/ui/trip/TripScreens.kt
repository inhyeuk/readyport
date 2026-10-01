package com.readyport.ui.trip

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.IndexCountry
import com.readyport.pack.PackRepository
import com.readyport.pack.PackSync
import com.readyport.trip.Trip
import com.readyport.trip.TripNotifications
import com.readyport.trip.TripRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.Fact
import com.readyport.ui.components.FactChip
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.LocalTileColumns
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.TileGrid
import com.readyport.ui.components.rememberGridColumns
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

data class TripFormUi(val countries: List<IndexCountry> = emptyList(), val existing: Trip? = null, val loaded: Boolean = false)

@HiltViewModel
class TripViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trips: TripRepository,
    private val packs: PackRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(TripFormUi())
    val ui: StateFlow<TripFormUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val countries = packs.index()?.value?.countries.orEmpty().filter { it.pack }
            _ui.value = TripFormUi(countries, trips.current(), loaded = true)
        }
    }

    /** 여행 저장 + 나라 찜(안내 받아 두기) + 입국 카드 알림 예약 */
    fun save(country: String, start: LocalDate, end: LocalDate) = viewModelScope.launch {
        val old = trips.current()
        val keep = old?.takeIf { it.country == country && it.startDate == start.toString() }
        trips.save(
            Trip(
                country = country, startDate = start.toString(), endDate = end.toString(),
                arrivedAt = keep?.arrivedAt, arrivalDismissed = keep?.arrivalDismissed ?: false,
            ),
        )
        // 다른 여행으로 바뀌면 지난 준비물 체크·장바구니를 비운다
        if (old != null && keep == null && old.country != country) settings.clearTripLists()
        settings.setFavorite(country, true)
        PackSync.requestNow(context, settings.current().wifiOnly)
        val form = packs.pack(country)?.value?.forms?.firstOrNull()
        val days = form?.windowDaysIncludingArrival
        if (form != null && days != null) {
            TripNotifications.scheduleFormWindow(context, start.minusDays((days - 1).toLong()), form.nameKo)
        } else {
            TripNotifications.cancelFormWindow(context)
        }
    }

    fun delete() = viewModelScope.launch {
        trips.clear()
        TripNotifications.cancelFormWindow(context)
    }
}

@Composable
fun TripScreen(onDone: () -> Unit, initialCountry: String? = null, viewModel: TripViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Android 13+ 알림 권한 (입국 카드 제출 가능일 알림)
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (!ui.loaded) return
    TripContent(
        ui = ui,
        onSave = { c, s, e ->
            viewModel.save(c, s, e)
            if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
            onDone()
        },
        onDelete = { viewModel.delete(); onDone() },
        initialCountry = initialCountry,
    )
}

/**
 * 여행 만들기·고치기 (DESIGN_SPEC 6-14).
 * 나라 = 사진 썸네일 라디오 카드 2열(쉬운 모드·큰 글자 1열), 날짜 = 글자 입력(YYYY-MM-DD) 유지 + 달력 아이콘 + `11월 3일 (화)` 확인 글(D20 —
 * 가로 스와이프 달력을 쓰지 않는다), 기간 칩(4박 5일), 저장 버튼 하나, 지우기는 빨간 버튼 + 확인 대화상자(D8).
 */
@Composable
fun TripContent(
    ui: TripFormUi,
    onSave: (String, LocalDate, LocalDate) -> Unit,
    onDelete: () -> Unit,
    /** 나라 화면의 '이 나라로 여행 계획 만들기'로 오면 그 나라를 미리 골라 둔다 */
    initialCountry: String? = null,
) {
    var country by remember(ui.existing) {
        mutableStateOf(initialCountry?.takeIf { c -> ui.countries.any { it.code == c } } ?: ui.existing?.country ?: ui.countries.singleOrNull()?.code)
    }
    var start by remember(ui.existing) { mutableStateOf(ui.existing?.startDate.orEmpty()) }
    var end by remember(ui.existing) { mutableStateOf(ui.existing?.endDate.orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(start, end) { invalid = false }
    val columns = rememberGridColumns()
    val startDate = parseDate(start)
    val endDate = parseDate(end)
    val nights = if (startDate != null && endDate != null) ChronoUnit.DAYS.between(startDate, endDate).toInt().takeIf { it >= 0 } else null

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
            SectionHeader(stringResource(R.string.trip_dates_title), icon = Icons.Outlined.EditCalendar)
        }
        item(key = "dates") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
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
                icon = Icons.Outlined.EditCalendar,
                onClick = {
                    val s = parseDate(start)
                    val e = parseDate(end)
                    if (country == null || s == null || e == null || e.isBefore(s)) invalid = true else onSave(country!!, s, e)
                },
            )
        }
        if (ui.existing != null) {
            sectionGap("delete-gap")
            item(key = "delete") {
                DangerButton(stringResource(R.string.trip_delete), onClick = { confirmDelete = true }, fillWidth = true)
            }
        }
    }

    if (confirmDelete) {
        DestructiveConfirm(
            title = stringResource(R.string.trip_delete_confirm_title),
            body = stringResource(R.string.trip_delete_confirm_body),
            confirmLabel = stringResource(R.string.trip_delete_confirm),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

/** 11월 3일 (화) */
@Composable
private fun datePreview(date: LocalDate): String =
    stringResource(R.string.trip_date_preview, date.monthValue, date.dayOfMonth, date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN))

/**
 * 나라 라디오 카드: 원형 사진 썸네일(장식) + 한국어·영어 이름. 선택 = AccentSoft 바탕 + 2dp Accent 테두리 + 채운 CheckCircle,
 * 아님 = 흰 바탕 + 1dp LineStrong(조작 요소 경계) + 빈 원. 놓인 그리드가 1열이면 가로, 여러 칸이면 세로.
 */
@Composable
private fun CountryRadioCard(c: IndexCountry, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    val horizontal = (LocalTileColumns.current ?: 1) == 1
    val shape = MaterialTheme.shapes.medium
    val thumbSize = if (dimens.easyMode) 56.dp else 48.dp
    val mark: @Composable (Modifier) -> Unit = { m ->
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) Tokens.Accent else Tokens.LineStrong,
            modifier = m.size(dimens.icon),
        )
    }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (horizontal) dimens.tileRowMinHeight else dimens.tileMinHeight)
            .clip(shape)
            .background(if (selected) Tokens.AccentSoft else Tokens.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) Tokens.Accent else Tokens.LineStrong, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
    ) {
        if (horizontal) {
            Row(
                Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CountryThumb(c.code, thumbSize)
                CountryNames(c, TextAlign.Start, Modifier.weight(1f))
                mark(Modifier)
            }
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CountryThumb(c.code, thumbSize)
                CountryNames(c, TextAlign.Center)
            }
            mark(Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun CountryThumb(code: String, size: Dp) {
    val thumb = rememberThumbnail(Photos.country(code), size)
    Box(Modifier.size(size).clip(CircleShape).background(Tokens.Navy)) {
        if (thumb != null) {
            Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun CountryNames(c: IndexCountry, align: TextAlign, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(c.nameKo, style = MaterialTheme.typography.titleMedium, color = Tokens.Ink, textAlign = align, modifier = Modifier.fillMaxWidth())
        Text(c.nameEn, style = MaterialTheme.typography.bodySmall, color = Tokens.InkSecondary, textAlign = align, modifier = Modifier.fillMaxWidth())
    }
}

/** 날짜 칸: 글자 입력(YYYY-MM-DD) 그대로 + 달력 아이콘 + 값이 올바르면 `11월 3일 (화)` 확인 글 (D20) */
@Composable
private fun DateField(@StringRes label: Int, value: String, parsed: LocalDate?, error: Boolean, onChange: (String) -> Unit) {
    val preview: (@Composable () -> Unit)? = if (parsed != null) {
        { Text(datePreview(parsed)) }
    } else {
        null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(R.string.trip_date_hint)) },
        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
        supportingText = preview,
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Tokens.Surface,
            unfocusedContainerColor = Tokens.Surface,
            errorContainerColor = Tokens.Surface,
            unfocusedBorderColor = Tokens.LineStrong,
            focusedBorderColor = Tokens.Accent,
            errorBorderColor = Tokens.DangerText,
            focusedLeadingIconColor = Tokens.Accent,
            unfocusedLeadingIconColor = Tokens.InkSecondary,
            errorLeadingIconColor = Tokens.DangerText,
            focusedSupportingTextColor = Tokens.Accent,
            unfocusedSupportingTextColor = Tokens.Accent,
            errorSupportingTextColor = Tokens.DangerText,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
