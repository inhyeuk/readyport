package com.readyport.ui.wallet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AirplaneTicket
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.doc.booking.BookingFields
import com.readyport.doc.booking.BookingKind
import com.readyport.security.SecureScreen
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.EmptyState
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconTile
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.KoText
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SecurityBanner
import com.readyport.ui.components.SelectTile
import com.readyport.ui.components.TileGrid
import com.readyport.ui.components.TileLayout
import com.readyport.ui.components.TileSpec
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.vault.BookingRecord
import com.readyport.vault.WalletRepository
import kotlinx.coroutines.launch

@Composable
fun BookingImportScreen(
    onDone: () -> Unit,
    viewModel: BookingImportViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val auth = rememberDeviceAuth()
    var saveFailed by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.fromImage(uri)
    }
    // PDF는 사진 선택기로 못 고른다. 문서 선택기(권한 불필요)를 쓴다
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.fromPdf(uri)
    }

    fun save(record: BookingRecord) {
        scope.launch {
            when (viewModel.save(record)) {
                WalletRepository.SaveResult.Saved -> Unit
                WalletRepository.SaveResult.Failed -> saveFailed = true
                else -> auth { scope.launch { if (viewModel.save(record) != WalletRepository.SaveResult.Saved) saveFailed = true } }
            }
        }
    }

    BookingImportContent(
        state = state,
        saveFailed = saveFailed,
        onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onPickPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
        onText = viewModel::fromText,
        onSave = { save(it.toRecord()) },
        onRestart = viewModel::restart,
        onDone = onDone,
    )
}

/** 26 예약 서류 추가 (DESIGN_SPEC 6-26). 맨 위 compact SecurityBanner — 고르기 → 읽는 중 → 검토 → 저장됨 */
@Composable
fun BookingImportContent(
    state: ImportState,
    saveFailed: Boolean,
    onPickPhoto: () -> Unit,
    onPickPdf: () -> Unit,
    onText: (String) -> Unit,
    onSave: (BookingDraft) -> Unit,
    onRestart: () -> Unit,
    onDone: () -> Unit,
) {
    AppScreen(
        title = stringResource(R.string.booking_title),
        speech = stringResource(R.string.booking_speech),
    ) {
        item(key = "security") { SecurityBanner(compact = true) }
        when (state) {
            ImportState.Choose -> {
                item(key = "tip") { NoticeBanner(stringResource(R.string.booking_tip), icon = Icons.Outlined.Share) }
                item(key = "pickers") {
                    Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                        IconTile(
                            TileSpec(stringResource(R.string.booking_pick_photo), Icons.Outlined.Screenshot, onPickPhoto),
                            layout = TileLayout.Horizontal,
                        )
                        IconTile(
                            TileSpec(stringResource(R.string.booking_pick_pdf), Icons.Outlined.PictureAsPdf, onPickPdf),
                            layout = TileLayout.Horizontal,
                        )
                    }
                }
                item(key = "paste") { PasteBox(onText) }
            }
            ImportState.Reading -> item(key = "reading") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = Tokens.Accent)
                    KoText(stringResource(R.string.booking_reading), style = MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
                }
            }
            is ImportState.Review -> item(key = "review") {
                ReviewForm(state.fields, saveFailed, onSave)
            }
            ImportState.Saved -> item(key = "saved") {
                EmptyState(
                    icon = Icons.Outlined.CheckCircle,
                    title = stringResource(R.string.booking_saved),
                    body = null,
                    tone = BadgeTone.Success,
                    action = {
                        Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                            PrimaryButton(stringResource(R.string.wallet_title), onClick = onDone, icon = Icons.Outlined.Badge)
                            SecondaryButton(stringResource(R.string.wallet_booking_add), onClick = onRestart, icon = Icons.Outlined.Add)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PasteBox(onText: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                // 짧은 라벨(테두리 홈에 한 줄) + 무엇을 붙여넣는지는 칸 아래
                label = { KoText(stringResource(R.string.booking_paste_label_short)) },
                supportingText = { KoText(stringResource(R.string.booking_paste_hint)) },
                minLines = 3,
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                stringResource(R.string.booking_read_text),
                onClick = { onText(text) },
                icon = Icons.AutoMirrored.Outlined.ManageSearch,
                enabled = text.isNotBlank(),
            )
        }
    }
}

@Composable
private fun ReviewForm(fields: BookingFields, saveFailed: Boolean, onSave: (BookingDraft) -> Unit) {
    val kindLabels = mapOf(
        BookingKind.Flight to stringResource(R.string.booking_kind_flight),
        BookingKind.Lodging to stringResource(R.string.booking_kind_lodging),
        BookingKind.Unknown to stringResource(R.string.booking_kind_other),
    )
    val kindIcons = mapOf(
        BookingKind.Flight to Icons.AutoMirrored.Outlined.AirplaneTicket,
        BookingKind.Lodging to Icons.Outlined.Hotel,
        BookingKind.Unknown to Icons.Outlined.Description,
    )
    // 이름 기본값은 입력칸 값이라 원형 그대로(`항공권 2026-11-03` — 재검토2 ①#13 '입력칸 값은 원형'). 보이는 날짜(찾은 날짜)만 한국어 모양
    val defaultTitle = kindLabels.getValue(fields.kind) + (fields.dates.firstOrNull()?.let { " $it" } ?: "")
    var draft by remember(fields) { mutableStateOf(BookingDraft.from(fields, defaultTitle)) }
    val nothingFound = fields.reference == null && fields.flightNumbers.isEmpty() && fields.dates.isEmpty()
    val dimens = LocalDimens.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.gap)) {
        if (nothingFound) {
            NoticeBanner(stringResource(R.string.booking_nothing_found), icon = Icons.Outlined.SearchOff, tone = BannerTone.Caution)
        } else {
            NoticeBanner(stringResource(R.string.booking_review_body), icon = Icons.AutoMirrored.Outlined.FactCheck)
        }
        // 종류: 세로 아이콘 + 라벨 타일 3칸 (큰 글자·쉬운 모드에서 칸이 좁아지면 1열 가로형)
        KoText(stringResource(R.string.booking_field_kind), style = MaterialTheme.typography.titleSmall, color = Tokens.InkSecondary)
        TileGrid(
            items = kindLabels.keys.toList(),
            modifier = Modifier.selectableGroup(),
            columns = if (rememberGridColumns() == 1) 1 else 3,
        ) { kind, cell ->
            SelectTile(
                label = kindLabels.getValue(kind),
                icon = kindIcons.getValue(kind),
                selected = draft.kind == kind,
                onClick = { draft = draft.copy(kind = kind) },
                modifier = cell,
            )
        }
        // 입력칸 라벨은 짧게(테두리 홈에 한 줄로 들어가게), 예시·설명은 칸 아래. 값은 칸 안에서 줄바꿈해 끝까지 보인다
        InfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.gap)) {
                Field(R.string.booking_label_title, draft.title, Icons.Outlined.Description, hint = R.string.booking_hint_title) {
                    draft = draft.copy(title = it)
                }
                Field(R.string.booking_field_reference, draft.reference, Icons.Outlined.ConfirmationNumber) { draft = draft.copy(reference = it) }
                if (draft.kind != BookingKind.Lodging) {
                    Field(R.string.wallet_booking_flights, draft.flights, Icons.AutoMirrored.Outlined.AirplaneTicket, hint = R.string.booking_hint_flights) {
                        draft = draft.copy(flights = it)
                    }
                }
                if (draft.kind == BookingKind.Lodging) {
                    // 날짜는 숫자 자판 + 숫자만 (하이픈은 앱이 넣어 보인다 — 재검토 R18)
                    Field(R.string.booking_label_checkin, draft.checkIn, Icons.Outlined.CalendarMonth, hint = R.string.booking_hint_checkin, date = true) {
                        draft = draft.copy(checkIn = it)
                    }
                    Field(R.string.booking_label_checkout, draft.checkOut, Icons.Outlined.CalendarMonth, hint = R.string.booking_hint_checkout, date = true) {
                        draft = draft.copy(checkOut = it)
                    }
                }
                if (draft.dates.isNotEmpty()) {
                    // 찾은 날짜는 `2026년 11월 3일 (화)`로 보인다(재검토2 ①#13). 저장 값은 그대로(YYYY-MM-DD)
                    val shownDates = draft.dates.map { koreanDate(it) }
                    IconBullet(
                        stringResource(R.string.booking_field_dates) + ": " + shownDates.joinToString(", "),
                        Icons.Outlined.EventAvailable,
                        tone = BadgeTone.Accent,
                    )
                }
            }
        }
        if (saveFailed) {
            NoticeBanner(stringResource(R.string.booking_save_failed), icon = Icons.Outlined.ErrorOutline, tone = BannerTone.Caution)
        }
        PrimaryButton(
            stringResource(R.string.booking_save),
            onClick = { onSave(draft) },
            // 반쯤 적은 날짜는 저장하며 버리지 않는다 — 날짜 칸이 빨간 테두리로 알려 준다
            enabled = draft.title.isNotBlank() && draft.datesValid,
            icon = Icons.Outlined.Check,
        )
    }
}

/**
 * 검토 입력칸: 짧은 라벨 + 앞 아이콘 + 예시·설명(supportingText, 늘 보임). 줄바꿈 입력은 받지 않지만
 * 긴 값(예: `항공권 2026-11-03`)은 칸 안에서 여러 줄로 보여 준다 — 확인하라는 값이 잘리지 않게.
 * [date]: 숫자 자판 + 숫자 8자리만, 화면에서는 `2026-11-03` 모양(여권 직접 입력과 같은 칸). 올바른 날짜가 아니면 빨간 테두리.
 */
@Composable
private fun Field(label: Int, value: String, icon: ImageVector, hint: Int? = null, date: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (date) dateDigits(it) else it.replace("\n", "")) },
        label = { KoText(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        supportingText = hint?.let { { KoText(stringResource(it)) } },
        isError = date && value.isNotEmpty() && parseDateDigits(value) == null,
        singleLine = date,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        visualTransformation = if (date) DateDigitsTransformation else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (date) KeyboardType.Number else KeyboardType.Text,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
