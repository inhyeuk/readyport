package com.readyport.ui.stay

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.R
import com.readyport.security.SecureScreen
import com.readyport.stay.Coordinates
import com.readyport.stay.LatLng
import com.readyport.stay.StayLinks
import com.readyport.stay.StayType
import com.readyport.stay.Stays
import com.readyport.transport.PlacesRepository
import com.readyport.trip.Trip
import com.readyport.trip.TripRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.ButtonPlacement
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DatePickField
import com.readyport.ui.components.DateRules
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.ExpandableDetail
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LockedState
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SelectTile
import com.readyport.ui.components.TileGrid
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.dateFieldColors
import com.readyport.ui.components.dateDigits
import com.readyport.ui.components.localText
import com.readyport.ui.components.parseDateDigits
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.sectionGap
import com.readyport.ui.nav.StayEditRoute
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.trip.tripDateRange
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
//
// 다듬기 S2 (운영자 요청 *"좌표 입력도 넣고 … 날짜를 입력할 때, 달력에서 선택할 수 있도록"*):
//  ① 묵는 날짜는 공용 날짜 칸([DatePickField]) — 달력이 주 입력, 숫자로 적는 길도 그대로.
//     달력은 **이 여행 날짜가 있는 달**에서 열리고, 체크아웃 달력은 체크인 앞 날짜를 막는다(있을 수 없는 날).
//  ② 좌표는 **접어 둔 묶음** 하나 — 대부분은 건너뛴다. 숫자 두 개도, 구글 지도 링크도 받고 읽은 값을 그대로 보여 준다.
//     앱 안에 지도는 없다(지도 SDK·API 키·네트워크 없음).
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
    /** 사람이 적거나 붙여 넣은 좌표 글자 (`37.5665, 126.978` 또는 구글 지도 링크) */
    val coords: String = "",
) {
    private val from get() = parseDateDigits(checkIn)
    private val to get() = parseDateDigits(checkOut)

    /** 반쯤 적은 날짜를 저장하며 버리지 않는다 */
    val datesValid: Boolean get() = (checkIn.isEmpty() || from != null) && (checkOut.isEmpty() || to != null)

    /** 체크아웃이 체크인보다 빠르면 알려 준다(막지는 않는다 — 저장 버튼만 막는다) */
    val datesOrdered: Boolean get() = from == null || to == null || !to!!.isBefore(from)

    /** 적어 둔 좌표에서 읽어 낸 값. 못 읽으면 null */
    val coordsParsed get() = Coordinates.parse(coords)

    /** 좌표 칸이 비었거나 읽을 수 있는지 — 못 읽은 글자를 조용히 버리지 않게 저장 버튼을 막는다(날짜와 같은 규칙) */
    val coordsValid: Boolean get() = coords.isBlank() || coordsParsed != null

    val canSave: Boolean get() = name.isNotBlank() && datesValid && datesOrdered && coordsValid

    override fun toString() = "StayDraft(hasAddress=${address.isNotBlank()}, hasCoords=${coordsParsed != null})"

    /** [tripId]가 null이면(설정 › 내 정보에서 고칠 때) 원래 붙어 있던 여행을 그대로 둔다 */
    fun toRecord(existing: StayRecord?, tripId: String?, now: LocalDateTime = LocalDateTime.now()): StayRecord {
        val coords = coordsParsed
        return StayRecord(
            id = existing?.id ?: Stays.newId(),
            // 이 여행에서 고쳤으면 이 여행 숙소가 된다(예전 예약 서류에서 옮겨 온 숙소도 여기서 여행에 붙는다)
            tripId = tripId ?: existing?.tripId,
            name = name.trim(),
            addressLocal = address.trim(),
            addressKo = addressKo.trim().ifEmpty { null },
            checkIn = parseDateDigits(checkIn)?.toString(),
            checkOut = parseDateDigits(checkOut)?.toString(),
            reference = reference.trim().ifEmpty { null },
            type = type,
            // 좌표 칸을 비우면 저장된 좌표도 지워진다(사람이 지운 것이다)
            lat = coords?.lat,
            lng = coords?.lng,
            phone = phone.trim().ifEmpty { null },
            memo = memo.trim().ifEmpty { null },
            savedAt = now.toString(),
        )
    }

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
                coords = Coordinates.text(stay.lat, stay.lng),
            )
        }
    }
}

data class StayEditUi(
    val loaded: Boolean = false,
    /** 보관함이 잠겨 있으면 먼저 잠금을 푼다 */
    val locked: Boolean = true,
    val existing: StayRecord? = null,
    /** 어느 여행의 숙소인지 — 날짜 달력이 이 여행 달에서 열린다 (설정 › 내 정보에서 고칠 때는 null) */
    val trip: Trip? = null,
    val saveFailed: Boolean = false,
)

@HiltViewModel
class StayEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val wallet: WalletRepository,
    private val places: PlacesRepository,
    private val trips: TripRepository,
) : ViewModel() {
    private val route = handle.toRoute<StayEditRoute>()
    private val _ui = MutableStateFlow(StayEditUi())
    val ui: StateFlow<StayEditUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            // 날짜 달력이 열릴 달을 알려면 이 여행 날짜가 필요하다 (여행 장부에는 주소 글자가 없다)
            val trip = route.tripId?.let { trips.get(it) }
            if (wallet.state.value !is WalletState.Unlocked) wallet.unlock()
            wallet.state.collect { state ->
                val contents = (state as? WalletState.Unlocked)?.contents
                _ui.update {
                    it.copy(
                        loaded = true,
                        locked = contents == null,
                        existing = route.stayId?.let { id -> contents?.stays?.firstOrNull { s -> s.id == id } },
                        trip = trip,
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
 * 숙소 넣기·고치기: 이름·주소(현지 글자)·한국어 주소 메모 → 묵는 날짜(달력 또는 숫자) →
 * 숙소 종류(입국 카드 선택지와 같은 값) → 좌표(접어 둠, 안 넣어도 됨) → 예약번호·전화·메모 →
 * 기기 안 저장 한 줄 → 저장 → (고칠 때) 지우기.
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
    // 저장해 둔 좌표가 있으면 좌표 묶음을 펼친 채로 — 적어 둔 값이 접힌 채 숨지 않게
    var coordsOpen by remember(ui.existing) { mutableStateOf(StayDraft.from(ui.existing).coordsParsed != null) }
    val editing = ui.existing != null
    val checkIn = parseDateDigits(draft.checkIn)
    val tripRange = ui.trip?.takeIf { it.datesValid }?.let { tripDateRange(it.start, it.end) }
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
                // 달력은 이 여행 날짜가 있는 달에서 열린다(여행을 모르면 오늘 달)
                DatePickField(
                    label = stringResource(R.string.stay_field_checkin),
                    value = draft.checkIn,
                    onChange = { draft = draft.copy(checkIn = it) },
                    note = tripRange?.let { stringResource(R.string.stay_dates_trip_note, it) } ?: stringResource(R.string.date_pick_note),
                    rules = DateRules(openOn = ui.trip?.takeIf { it.datesValid }?.start),
                )
                DatePickField(
                    label = stringResource(R.string.stay_field_checkout),
                    value = draft.checkOut,
                    onChange = { draft = draft.copy(checkOut = it) },
                    note = stringResource(R.string.date_pick_note),
                    // 나가는 날이 들어가는 날보다 빠를 수는 없다 — 달력에서 그 앞은 고를 수 없다(정말 불가능한 날)
                    rules = DateRules(
                        openOn = checkIn ?: ui.trip?.takeIf { it.datesValid }?.start,
                        notBefore = checkIn,
                    ),
                )
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
        sectionGap("coords-gap")
        item(key = "coords-title") { SectionHeader(stringResource(R.string.stay_coords_title), icon = Icons.Outlined.MyLocation) }
        item(key = "coords") {
            StayCoordsSection(
                draft = draft,
                open = coordsOpen,
                onOpenChange = { coordsOpen = it },
                onChange = { draft = draft.copy(coords = it) },
            )
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

/**
 * 좌표 묶음 (다듬기 S2) — **접어 둔다**. 대부분은 건너뛰고, 지도에서 정확한 자리를 찾고 싶은 사람만 펼친다.
 * 숫자 두 개(`37.5665, 126.978`)도, 구글 지도 링크도 받는다. 읽은 값은 위도·경도로 **그대로 보여 주고**,
 * 못 읽으면 모르겠다고 말한다(지어내지 않는다). 앱 안에 지도는 없다 — 그 사실도 한 줄로 적는다.
 */
@Composable
private fun StayCoordsSection(draft: StayDraft, open: Boolean, onOpenChange: (Boolean) -> Unit, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val coords = draft.coordsParsed
    val label = stringResource(R.string.stay_coords_toggle)
    FieldCard {
        KoText(stringResource(R.string.stay_coords_help), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        // 접기 이름은 짧게 (`좌표 접기`) — 긴 라벨을 그대로 쓰면 TalkBack이 괄호 설명까지 다시 읽는다
        ExpandableDetail(open = open, onOpenChange = onOpenChange, label = label, target = stringResource(R.string.stay_coords_title)) {
            // 앱 안에 지도가 없다는 사실을 칸 바로 위에 — 지도에서 집고 싶으면 링크를 복사해 붙여 넣는다
            IconBullet(stringResource(R.string.stay_coords_no_map), Icons.Outlined.Info)
            OutlinedTextField(
                value = draft.coords,
                onValueChange = { onChange(it.replace("\n", "")) },
                label = { KoText(stringResource(R.string.stay_coords_field)) },
                leadingIcon = { Icon(Icons.Outlined.MyLocation, contentDescription = null) },
                supportingText = { KoText(stringResource(R.string.stay_coords_hint)) },
                isError = !draft.coordsValid,
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                colors = dateFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                coords != null -> {
                    IconBullet(
                        stringResource(R.string.stay_coords_read, Coordinates.number(coords.lat), Coordinates.number(coords.lng)),
                        Icons.Outlined.Check,
                        tone = BadgeTone.Success,
                    )
                    val openCd = stringResource(R.string.stay_coords_open_cd, Coordinates.format(coords))
                    QuietButton(
                        stringResource(R.string.stay_coords_open),
                        // 좌표만 들고 지도를 연다 — Stays.searchQuery가 좌표를 주소보다 먼저 쓴다
                        onClick = { StayLinks.openSearch(context, coordsOnly(coords)) },
                        icon = Icons.Outlined.Map,
                        modifier = Modifier.semantics { contentDescription = openCd },
                    )
                    QuietButton(stringResource(R.string.stay_coords_clear), onClick = { onChange("") }, icon = Icons.AutoMirrored.Outlined.Backspace)
                    KoText(stringResource(R.string.stay_coords_prefer), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                }
                draft.coords.isNotBlank() -> IconBullet(
                    stringResource(R.string.stay_coords_unknown),
                    Icons.Outlined.ErrorOutline,
                    tone = BadgeTone.Caution,
                )
            }
        }
    }
}

/** 좌표만 든 숙소 한 줄 — `지도에서 좌표 확인`이 쓰는 임시 값(저장하지 않는다. 이름·주소는 넣지 않아 좌표로만 열린다) */
private fun coordsOnly(coords: LatLng) =
    StayRecord(id = "coords-check", name = "", addressLocal = "", lat = coords.lat, lng = coords.lng, savedAt = "")

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
 * [local]이면 현지 글자가 잘 보이게 행간을 넓힌다. 날짜 칸은 이 부품이 아니라 공용 [DatePickField]다.
 */
@Composable
private fun StayField(
    @StringRes label: Int,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    @StringRes hint: Int? = null,
    local: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.replace("\n", "")) },
        label = { KoText(stringResource(label)) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        supportingText = hint?.let { { KoText(stringResource(it)) } },
        textStyle = if (local) localText(MaterialTheme.typography.bodyLarge) else MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        colors = dateFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}
