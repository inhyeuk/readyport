package com.readyport.ui.wallet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.doc.booking.BookingFields
import com.readyport.doc.booking.BookingKind
import com.readyport.security.SecureScreen
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.TopicCard
import com.readyport.ui.theme.LocalDimens
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
    val dimens = LocalDimens.current
    AppScreen(
        title = stringResource(R.string.booking_title),
        speech = stringResource(R.string.booking_speech),
    ) {
        when (state) {
            ImportState.Choose -> {
                item(key = "tip") { TopicCard(stringResource(R.string.booking_tip), null, tone = CardTone.Notice) }
                item(key = "pickers") {
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.gap)) {
                        PrimaryButton(stringResource(R.string.booking_pick_photo), onClick = onPickPhoto)
                        OutlinedButton(onClick = onPickPdf, modifier = Modifier.fillMaxWidth().heightIn(min = dimens.buttonHeight)) {
                            Text(stringResource(R.string.booking_pick_pdf), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                item(key = "paste") { PasteBox(onText) }
            }
            ImportState.Reading -> item(key = "reading") {
                Text(stringResource(R.string.booking_reading), style = MaterialTheme.typography.bodyLarge)
            }
            is ImportState.Review -> item(key = "review") {
                ReviewForm(state.fields, saveFailed, onSave)
            }
            ImportState.Saved -> item(key = "saved") {
                InfoCard {
                    Text(stringResource(R.string.booking_saved), style = MaterialTheme.typography.titleLarge)
                    PrimaryButton(stringResource(R.string.wallet_title), onClick = onDone)
                    OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth().heightIn(min = dimens.buttonHeight)) {
                        Text(stringResource(R.string.wallet_booking_add), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun PasteBox(onText: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    InfoCard {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.booking_paste_label)) },
            minLines = 3,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { onText(text) },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight),
        ) { Text(stringResource(R.string.booking_read_text), style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun ReviewForm(fields: BookingFields, saveFailed: Boolean, onSave: (BookingDraft) -> Unit) {
    val kindLabels = mapOf(
        BookingKind.Flight to stringResource(R.string.booking_kind_flight),
        BookingKind.Lodging to stringResource(R.string.booking_kind_lodging),
        BookingKind.Unknown to stringResource(R.string.booking_kind_other),
    )
    val defaultTitle = kindLabels.getValue(fields.kind) + (fields.dates.firstOrNull()?.let { " $it" } ?: "")
    var draft by remember(fields) { mutableStateOf(BookingDraft.from(fields, defaultTitle)) }
    val nothingFound = fields.reference == null && fields.flightNumbers.isEmpty() && fields.dates.isEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
        Text(
            stringResource(if (nothingFound) R.string.booking_nothing_found else R.string.booking_review_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(stringResource(R.string.booking_field_kind), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kindLabels.forEach { (kind, label) ->
                FilterChip(
                    selected = draft.kind == kind,
                    onClick = { draft = draft.copy(kind = kind) },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Field(R.string.booking_field_title, draft.title) { draft = draft.copy(title = it) }
        Field(R.string.booking_field_reference, draft.reference) { draft = draft.copy(reference = it) }
        if (draft.kind != BookingKind.Lodging) {
            Field(R.string.booking_field_flights, draft.flights) { draft = draft.copy(flights = it) }
        }
        if (draft.kind == BookingKind.Lodging) {
            Field(R.string.booking_field_checkin, draft.checkIn) { draft = draft.copy(checkIn = it) }
            Field(R.string.booking_field_checkout, draft.checkOut) { draft = draft.copy(checkOut = it) }
        }
        if (draft.dates.isNotEmpty()) {
            Text(
                stringResource(R.string.booking_field_dates) + ": " + draft.dates.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (saveFailed) TopicCard(stringResource(R.string.booking_save_failed), null, tone = CardTone.Caution)
        PrimaryButton(stringResource(R.string.booking_save), onClick = { onSave(draft) }, enabled = draft.title.isNotBlank())
    }
}

@Composable
private fun Field(label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}
