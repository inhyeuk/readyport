package com.readyport.ui.stay

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.R
import com.readyport.security.SecureScreen
import com.readyport.stay.StayType
import com.readyport.stay.Stays
import com.readyport.transport.PlacesRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.ButtonPlacement
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LockedState
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SelectTile
import com.readyport.ui.components.TileGrid
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.localText
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.sectionGap
import com.readyport.ui.nav.StayEditRoute
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.trip.DateDigitsTransformation
import com.readyport.ui.trip.dateDigits
import com.readyport.ui.trip.parseDigits
import com.readyport.ui.wallet.rememberDeviceAuth
import com.readyport.vault.StayRecord
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

// =====================================================================================
// 숙소 넣기·고치기 (2026-10-03)
//
// 주소는 개인정보에 가까워 **암호화 보관함**에만 저장하고(예약 서류와 같은 파일), 화면 캡처를 막는다(FLAG_SECURE).
// 저장하면 '가는 곳'(기사님께 보여 주기·차 부르기)도 같은 id로 맞춰 둔다 — 같은 호텔을 두 번 적지 않는다.
// =====================================================================================

/** 화면에서 고치는 값 (저장 전 메모리에만) */
data class StayDraft(
    val name: String = "",
    val address: String = "",
    val addressKo: String = "",
    /** 숫자 8자리 (화면에서는 `2026-11-03` 모양) */
    val checkIn: String = "",
    val checkOut: String = "",
    val reference: String = "",
    val type: String? = null,
    val phone: String = "",
    val memo: String = "",
) {
    private val from get() = parseDigits(checkIn)
    private val to get() = parseDigits(checkOut)

    /** 반쯤 적은 날짜를 저장하며 버리지 않는다 */
    val datesValid: Boolean get() = (checkIn.isEmpty() || from != null) && (checkOut.isEmpty() || to != null)

    /** 체크아웃이 체크인보다 빠르면 알려 준다(막지는 않는다 — 저장 버튼만 막는다) */
    val datesOrdered: Boolean get() = from == null || to == null || !to!!.isBefore(from)

    val canSave: Boolean get() = name.isNotBlank() && datesValid && datesOrdered

    override fun toString() = "StayDraft(hasAddress=${address.isNotBlank()})"

    fun toRecord(existing: StayRecord?, tripId: String, now: LocalDateTime = LocalDateTime.now()) = StayRecord(
        id = existing?.id ?: Stays.newId(),
        // 이 여행에서 고쳤으면 이 여행 숙소가 된다(예전 예약 서류에서 옮겨 온 숙소도 여기서 여행에 붙는다)
        tripId = tripId,
        name = name.trim(),
        addressLocal = address.trim(),
        addressKo = addressKo.trim().ifEmpty { null },
        checkIn = parseDigits(checkIn)?.toString(),
        checkOut = parseDigits(checkOut)?.toString(),
        reference = reference.trim().ifEmpty { null },
        type = type,
        // 좌표는 화면에서 적지 않는다(시니어가 숫자를 받아 적을 일이 아니다) — 있던 값은 그대로 지킨다
        lat = existing?.lat,
        lng = existing?.lng,
        phone = phone.trim().ifEmpty { null },
        memo = memo.trim().ifEmpty { null },
        savedAt = now.toString(),
    )

    companion object {
        fun from(stay: StayRecord?): StayDraft = if (stay == null) {
            StayDraft()
        } else {
            StayDraft(
                name = stay.name,
                address = stay.addressLocal,
                addressKo = stay.addressKo.orEmpty(),
                checkIn = dateDigits(stay.checkIn),
                checkOut = dateDigits(stay.checkOut),
                reference = stay.reference.orEmpty(),
                type = stay.type,
                phone = stay.phone.orEmpty(),
                memo = stay.memo.orEmpty(),
            )
        }
    }
}

data class StayEditUi(
    val loaded: Boolean = false,
    /** 보관함이 잠겨 있으면 먼저 잠금을 푼다 */
    val locked: Boolean = true,
    val existing: StayRecord? = null,
    val saveFailed: Boolean = false,
)

@HiltViewModel
class StayEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val wallet: WalletRepository,
    private val places: PlacesRepository,
) : ViewModel() {
    private val route = handle.toRoute<StayEditRoute>()
    private val _ui = MutableStateFlow(StayEditUi())
    val ui: StateFlow<StayEditUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            if (wallet.state.value !is WalletState.Unlocked) wallet.unlock()
            wallet.state.collect { state ->
                val contents = (state as? WalletState.Unlocked)?.contents
                _ui.update {
                    it.copy(
                        loaded = true,
                        locked = contents == null,
                        existing = route.stayId?.let { id -> contents?.stays?.firstOrNull { s -> s.id == id } },
                    )
                }
            }
        }
    }

    fun unlock() = viewModelScope.launch { wallet.unlock() }

    /** 저장하고 '가는 곳'도 같은 id로 맞춘다(주소를 비우면 가는 곳에서 지운다) */
    suspend fun save(draft: StayDraft): WalletRepository.SaveResult {
        if (wallet.state.value !is WalletState.Unlocked) wallet.unlock()
        val record = draft.toRecord(_ui.value.existing, route.tripId)
        val result = wallet.update { c ->
            val list = if (c.stays.any { it.id == record.id }) c.stays.map { if (it.id == record.id) record else it } else c.stays + record
            c.copy(stays = list)
        }
        if (result == WalletRepository.SaveResult.Saved) places.syncStay(record.id, Stays.place(record))
        _ui.update { it.copy(saveFailed = result != WalletRepository.SaveResult.Saved) }
        return result
    }

    suspend fun delete(): WalletRepository.SaveResult {
        val id = _ui.value.existing?.id ?: return WalletRepository.SaveResult.Saved
        if (wallet.state.value !is WalletState.Unlocked) wallet.unlock()
        val result = wallet.update { c -> c.copy(stays = c.stays.filterNot { it.id == id }) }
        if (result == WalletRepository.SaveResult.Saved) places.syncStay(id, null)
        _ui.update { it.copy(saveFailed = result != WalletRepository.SaveResult.Saved) }
        return result
    }
}

@Composable
fun StayEditScreen(onDone: () -> Unit, viewModel: StayEditViewModel = hiltViewModel()) {
    SecureScreen()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val auth = rememberDeviceAuth()
    if (!ui.loaded) return
    StayEditContent(
        ui = ui,
        onUnlock = { auth { viewModel.unlock() } },
        onSave = { draft ->
            scope.launch {
                when (viewModel.save(draft)) {
                    WalletRepository.SaveResult.Saved -> onDone()
                    WalletRepository.SaveResult.Failed -> Unit
                    else -> auth { scope.launch { if (viewModel.save(draft) == WalletRepository.SaveResult.Saved) onDone() } }
                }
            }
        },
        onDelete = {
            scope.launch {
                when (viewModel.delete()) {
                    WalletRepository.SaveResult.Saved -> onDone()
                    WalletRepository.SaveResult.Failed -> Unit
                    else -> auth { scope.launch { if (viewModel.delete() == WalletRepository.SaveResult.Saved) onDone() } }
                }
            }
        },
    )
}

/**
 * 숙소 넣기·고치기: 이름·주소(현지 글자)·한국어 주소 메모 → 묵는 날짜(숫자 자판, 하이픈은 칸이 그린다) →
 * 숙소 종류(입국 카드 선택지와 같은 값) → 예약번호·전화·메모 → 기기 안 저장 한 줄 → 저장 → (고칠 때) 지우기.
 */
@Composable
fun StayEditContent(
    ui: StayEditUi,
    onUnlock: () -> Unit,
    onSave: (StayDraft) -> Unit,
    onDelete: () -> Unit,
) {
    var draft by remember(ui.existing) { mutableStateOf(StayDraft.from(ui.existing)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val editing = ui.existing != null
    AppScreen(
        title = stringResource(if (editing) R.string.stay_edit_title else R.string.stay_add_title),
        subtitle = stringResource(R.string.stay_form_lead),
        speech = stringResource(R.string.stay_form_speech),
    ) {
        if (ui.locked) {
            item(key = "locked") {
                LockedState(
                    title = stringResource(R.string.wallet_locked_title),
                    body = stringResource(R.string.stay_local_only),
                    buttonLabel = stringResource(R.string.wallet_unlock),
                    onUnlock = onUnlock,
                )
            }
            return@AppScreen
        }
        item(key = "place-title") { SectionHeader(stringResource(R.string.stays_title), icon = Icons.Outlined.Hotel) }
        item(key = "place") {
            FieldCard {
                // 이름 칸 앞 아이콘은 두지 않는다 — 섹션 머리에 같은 그림이 있다(여행 만들기 날짜 칸과 같은 규칙)
                StayField(R.string.stay_field_name, draft.name, icon = null, hint = R.string.stay_hint_name) {
                    draft = draft.copy(name = it)
                }
                StayField(
                    R.string.stay_field_address,
                    draft.address,
                    Icons.Outlined.Place,
                    hint = R.string.stay_hint_address,
                    local = true,
                ) {
                    draft = draft.copy(address = it)
                }
                StayField(R.string.stay_field_address_ko, draft.addressKo, Icons.Outlined.Translate, hint = R.string.stay_hint_address_ko) {
                    draft = draft.copy(addressKo = it)
                }
            }
        }
        sectionGap("dates-gap")
        item(key = "dates-title") { SectionHeader(stringResource(R.string.stay_dates_title), icon = Icons.Outlined.CalendarMonth) }
        item(key = "dates") {
            FieldCard {
                // 날짜 칸 앞에는 달력 아이콘을 두지 않는다(누르면 달력이 열릴 것처럼 보인다 — 여행 만들기와 같은 규칙)
                StayField(R.string.stay_field_checkin, draft.checkIn, icon = null, date = true) { draft = draft.copy(checkIn = it) }
                StayField(R.string.stay_field_checkout, draft.checkOut, icon = null, date = true) { draft = draft.copy(checkOut = it) }
                if (!draft.datesOrdered) {
                    NoticeBanner(stringResource(R.string.stay_date_invalid), icon = Icons.Outlined.ErrorOutline, tone = BannerTone.Caution)
                }
            }
        }
        sectionGap("type-gap")
        // 머리 아이콘은 종류 타일(호텔·게스트하우스…)과 겹치지 않는 '분류' 그림
        item(key = "type-title") { SectionHeader(stringResource(R.string.stay_type_title), icon = Icons.Outlined.Category) }
        item(key = "type") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner)) {
                TileGrid(StayType.entries.toList(), Modifier.selectableGroup(), rememberGridColumns()) { type, cell ->
                    SelectTile(
                        label = stayTypeLabel(type.key).orEmpty(),
                        icon = stayTypeIcon(type.key),
                        selected = draft.type == type.key,
                        onClick = { draft = draft.copy(type = type.key) },
                        modifier = cell,
                    )
                }
                KoText(stringResource(R.string.stay_type_hint), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
            }
        }
        sectionGap("more-gap")
        item(key = "more-title") { SectionHeader(stringResource(R.string.stay_more_title), icon = Icons.Outlined.EditNote) }
        item(key = "more") {
            FieldCard {
                StayField(R.string.stay_field_reference, draft.reference, Icons.Outlined.ConfirmationNumber) { draft = draft.copy(reference = it) }
                StayField(R.string.stay_field_phone, draft.phone, Icons.Outlined.PhoneInTalk, hint = R.string.stay_hint_phone) {
                    draft = draft.copy(phone = it)
                }
                // 메모 칸 앞 아이콘은 두지 않는다 — 섹션 머리에 같은 그림이 있다
                StayField(R.string.stay_field_memo, draft.memo, icon = null) { draft = draft.copy(memo = it) }
            }
        }
        if (ui.saveFailed) {
            item(key = "save-failed") {
                NoticeBanner(stringResource(R.string.stay_save_failed), icon = Icons.Outlined.ErrorOutline, tone = BannerTone.Caution)
            }
        }
        item(key = "local") { IconBullet(stringResource(R.string.stay_local_only), Icons.Outlined.Lock) }
        item(key = "save") {
            PrimaryButton(
                stringResource(R.string.stay_save),
                onClick = { onSave(draft) },
                enabled = draft.canSave,
                icon = Icons.Outlined.Check,
            )
        }
        if (editing) {
            sectionGap("delete-gap")
            item(key = "delete") {
                DangerButton(stringResource(R.string.stay_delete), onClick = { confirmDelete = true }, placement = ButtonPlacement.CardAction)
            }
        }
    }
    if (confirmDelete) {
        DestructiveConfirm(
            title = stringResource(R.string.stay_delete_confirm_title),
            body = stringResource(R.string.stay_delete_confirm_body),
            confirmLabel = stringResource(R.string.stay_delete_confirm),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** 흰 카드 안의 입력칸 묶음 — 회색 바탕 위 흰 칸의 라벨 홈이 비치지 않게 (여행 만들기와 같은 규칙) */
@Composable
private fun FieldCard(content: @Composable () -> Unit) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.large
    Surface(color = Tokens.Surface, shape = shape, modifier = Modifier.fillMaxWidth().cardShadow(shape)) {
        Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.gap)) { content() }
    }
}

/**
 * 입력칸 하나: 짧은 라벨 + 앞 아이콘 + 설명(supportingText).
 * [date]면 숫자 자판으로 8자리만 받고 하이픈은 칸이 그려 준다(`2026-11-03`), [local]이면 현지 글자가 잘 보이게 행간을 넓힌다.
 */
@Composable
private fun StayField(
    @StringRes label: Int,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    @StringRes hint: Int? = null,
    date: Boolean = false,
    local: Boolean = false,
    onChange: (String) -> Unit,
) {
    val parsed = if (date) parseDigits(value) else null
    val error = date && value.isNotEmpty() && parsed == null
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (date) dateDigits(it) else it.replace("\n", "")) },
        label = { KoText(stringResource(label)) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        supportingText = hint?.let { { KoText(stringResource(it)) } },
        isError = error,
        singleLine = date,
        textStyle = if (local) localText(MaterialTheme.typography.bodyLarge) else MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        visualTransformation = if (date) DateDigitsTransformation else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (date) KeyboardType.Number else KeyboardType.Text,
            imeAction = ImeAction.Next,
        ),
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
