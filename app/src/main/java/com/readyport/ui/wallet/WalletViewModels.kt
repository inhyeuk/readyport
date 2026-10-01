package com.readyport.ui.wallet

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.text.TextRecognizer
import com.readyport.data.settings.SettingsRepository
import com.readyport.doc.booking.BookingExtractor
import com.readyport.doc.booking.BookingFields
import com.readyport.doc.booking.BookingKind
import com.readyport.doc.mrz.MrzData
import com.readyport.doc.mrz.MrzParser
import com.readyport.doc.ocr.OcrEngine
import com.readyport.share.SharedPayload
import com.readyport.share.ShareInbox
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val repo: WalletRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<WalletState> = repo.state
    val autoDestroy: StateFlow<Boolean> = settings.settings.map { it.autoDestroyPassport }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun unlock() = viewModelScope.launch { repo.unlock() }
    fun lock() = repo.lock()
    fun reset() = viewModelScope.launch { repo.wipe() }
    fun deletePassport() = viewModelScope.launch { repo.update { it.copy(passport = null) } }
    fun deleteBooking(id: String) = viewModelScope.launch { repo.update { c -> c.copy(bookings = c.bookings.filterNot { it.id == id }) } }
    fun setAutoDestroy(enabled: Boolean) = viewModelScope.launch { settings.setAutoDestroyPassport(enabled) }
}

// ---------------- 여권 등록 ----------------

sealed interface ScanState {
    data object Idle : ScanState
    data object Reading : ScanState
    data object NotFound : ScanState
    data class Found(val mrz: MrzData) : ScanState
}

@HiltViewModel
class PassportFlowViewModel @Inject constructor(
    handle: androidx.lifecycle.SavedStateHandle,
    private val repo: WalletRepository,
    private val ocr: OcrEngine,
) : ViewModel() {
    /** "self" 또는 동행자 id (PassportGraph 인자) */
    private val traveler: String = handle["traveler"] ?: "self"

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    val recognizer: TextRecognizer get() = ocr.latin

    /** 카메라 프레임마다 부른다. 체크디지트를 모두 통과한 결과만 받아들인다 */
    fun onFrameText(text: String) {
        if (_scan.value is ScanState.Found) return
        MrzParser.findInText(text)?.takeIf { it.allChecksPass }?.let { _scan.value = ScanState.Found(it) }
    }

    /** 갤러리 사진 한 장. 일부만 통과해도 보여 주고, 확인 화면에서 저장을 막는다 */
    fun readPhoto(uri: Uri) = viewModelScope.launch {
        _scan.value = ScanState.Reading
        val text = runCatching { ocr.recognizeImage(uri, koreanText = false) }.getOrDefault("")
        _scan.value = MrzParser.findInText(text)?.let { ScanState.Found(it) } ?: ScanState.NotFound
    }

    fun rescan() {
        _scan.value = ScanState.Idle
    }

    suspend fun save(record: PassportRecord): WalletRepository.SaveResult {
        if (repo.state.value !is WalletState.Unlocked) repo.unlock()
        val result = repo.update { c ->
            if (traveler == "self") c.copy(passport = record)
            else c.copy(companions = c.companions.map { if (it.id == traveler) it.copy(passport = record) else it })
        }
        if (result == WalletRepository.SaveResult.Saved) _scan.value = ScanState.Idle
        return result
    }
}

fun MrzData.toRecord(now: LocalDateTime = LocalDateTime.now()) = PassportRecord(
    surname = surname,
    givenNames = givenNames,
    documentNumber = documentNumber,
    nationality = nationality,
    issuingState = issuingState,
    birthDate = birthDate.toString(),
    sex = sex.toString(),
    expiryDate = expiryDate.toString(),
    source = "mrz",
    mrzVerified = allChecksPass,
    savedAt = now.toString(),
)

// ---------------- 예약 서류 ----------------

sealed interface ImportState {
    data object Choose : ImportState
    data object Reading : ImportState
    data class Review(val fields: BookingFields) : ImportState
    data object Saved : ImportState
}

@HiltViewModel
class BookingImportViewModel @Inject constructor(
    private val repo: WalletRepository,
    private val ocr: OcrEngine,
    private val inbox: ShareInbox,
    private val contentResolver: android.content.ContentResolver,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportState>(ImportState.Choose)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    init {
        // 이 화면에 있는 동안 새로 공유받은 것도 바로 처리한다
        viewModelScope.launch {
            inbox.pending.filterNotNull().collect { inbox.take()?.let(::fromPayload) }
        }
    }

    fun fromText(text: String) {
        _state.value = ImportState.Review(BookingExtractor.extract(text))
    }

    fun fromImage(uri: Uri) = read { ocr.recognizeImage(uri, koreanText = true) }

    fun fromPdf(uri: Uri) = read { ocr.recognizePdf(uri) }

    fun fromPayload(payload: SharedPayload) {
        val uri = payload.uri
        when {
            payload.text != null && uri == null -> fromText(payload.text)
            uri == null -> Unit
            payload.mimeType == "application/pdf" -> fromPdf(uri)
            payload.mimeType?.startsWith("image/") == true -> fromImage(uri)
            else -> read { readTextUri(uri) }
        }
    }

    private fun read(block: suspend () -> String) = viewModelScope.launch {
        _state.value = ImportState.Reading
        val text = runCatching { block() }.getOrDefault("")
        _state.value = ImportState.Review(BookingExtractor.extract(text))
    }

    private fun readTextUri(uri: Uri): String =
        contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(200_000).decodeToString() }.orEmpty()

    suspend fun save(record: BookingRecord): WalletRepository.SaveResult {
        if (repo.state.value !is WalletState.Unlocked) repo.unlock()
        val result = repo.update { it.copy(bookings = it.bookings + record) }
        if (result == WalletRepository.SaveResult.Saved) _state.value = ImportState.Saved
        return result
    }

    fun restart() {
        _state.value = ImportState.Choose
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/**
 * 확인 화면에서 사용자가 고친 값. 체크인·체크아웃은 날짜 칸 값(숫자 8자리, 화면에서만 `2026-11-03` 모양 — 재검토 R18 숫자 자판)
 */
data class BookingDraft(
    val kind: BookingKind,
    val title: String,
    val reference: String,
    val flights: String,
    val checkIn: String,
    val checkOut: String,
    val dates: List<LocalDate>,
) {
    /** 날짜 칸이 비었거나 올바른 날짜인지 — 반쯤 적은 날짜를 저장하며 버리지 않게, 아니면 저장 버튼을 막는다 */
    val datesValid: Boolean
        get() = listOf(checkIn, checkOut).all { it.isEmpty() || parseDateDigits(it) != null }

    fun toRecord(now: LocalDateTime = LocalDateTime.now()) = BookingRecord(
        id = UUID.randomUUID().toString(),
        kind = when (kind) {
            BookingKind.Flight -> "flight"
            BookingKind.Lodging -> "lodging"
            BookingKind.Unknown -> "other"
        },
        title = title.trim(),
        reference = reference.trim().ifEmpty { null },
        flightNumbers = flights.split(',', ' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() },
        dates = dates.map { it.toString() },
        checkIn = parseDateDigits(checkIn)?.toString(),
        checkOut = parseDateDigits(checkOut)?.toString(),
        savedAt = now.toString(),
    )

    override fun toString() = "BookingDraft(kind=$kind)"

    companion object {
        fun from(fields: BookingFields, defaultTitle: String) = BookingDraft(
            kind = fields.kind,
            title = defaultTitle,
            reference = fields.reference.orEmpty(),
            flights = fields.flightNumbers.joinToString(", "),
            checkIn = digitsOf(fields.checkIn?.toString()),
            checkOut = digitsOf(fields.checkOut?.toString()),
            dates = fields.dates,
        )
    }
}
