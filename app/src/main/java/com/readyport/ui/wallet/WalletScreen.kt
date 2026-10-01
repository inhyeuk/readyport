package com.readyport.ui.wallet

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.security.DeviceAuth
import com.readyport.security.SecureScreen
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.ComingSoonGroup
import com.readyport.ui.components.ButtonPlacement
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.KeyValueRow
import com.readyport.ui.components.KoText
import com.readyport.ui.components.ValueStyle
import com.readyport.ui.components.ListDivider
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.LockedState
import com.readyport.ui.components.NewsStyle
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.RowTrailing
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SecurityBanner
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.TileGrid
import com.readyport.ui.components.TrailingFlow
import com.readyport.ui.components.passportCardColors
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.WalletState
import java.time.LocalDate

/** 버튼이 비활성인 이유 한 줄 (Info + bodyMedium InkSecondary) — 27 companion_add_hint와 같은 모양 */
@Composable
internal fun DisabledReason(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = Tokens.InkSecondary,
            modifier = Modifier.padding(top = 2.dp).size(textIconSize(LocalDimens.current.iconSmall)),
        )
        KoText(text, style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary, modifier = Modifier.weight(1f))
    }
}

/**
 * 기기 인증(지문·얼굴·화면 잠금)을 거쳐 [onSuccess]를 부른다.
 * 화면 잠금이 없는 기기(또는 테스트)에서는 바로 부른다 — 이때 보관함 키도 인증에 묶이지 않는다.
 */
@Composable
fun rememberDeviceAuth(): (onSuccess: () -> Unit) -> Unit {
    val activity = LocalActivity.current as? FragmentActivity
    val title = stringResource(R.string.wallet_auth_title)
    val subtitle = stringResource(R.string.wallet_auth_subtitle)
    return remember(activity, title, subtitle) {
        { onSuccess ->
            if (activity == null || !DeviceAuth.isAvailable(activity)) {
                onSuccess()
            } else {
                DeviceAuth.prompt(activity, title, subtitle) { if (it == DeviceAuth.Result.Success) onSuccess() }
            }
        }
    }
}

@Composable
fun WalletScreen(
    onAddPassport: () -> Unit,
    onAddBooking: () -> Unit,
    onOpenCompanions: () -> Unit = {},
    viewModel: WalletViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val autoDestroy by viewModel.autoDestroy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val deviceSecure = remember { DeviceAuth.isAvailable(context) }
    val auth = rememberDeviceAuth()
    WalletContent(
        state = state,
        deviceSecure = deviceSecure,
        autoDestroy = autoDestroy,
        today = LocalDate.now(),
        onUnlock = { auth { viewModel.unlock() } },
        onLock = viewModel::lock,
        onReset = viewModel::reset,
        onAddPassport = onAddPassport,
        onDeletePassport = viewModel::deletePassport,
        onAddBooking = onAddBooking,
        onDeleteBooking = viewModel::deleteBooking,
        onAutoDestroyChange = viewModel::setAutoDestroy,
        onOpenCompanions = onOpenCompanions,
    )
}

/**
 * 23 내 정보(잠김) / 24 내 정보(열림) (DESIGN_SPEC 6-23·24).
 * 맨 위 SecurityBanner(전체) — 이 정보가 휴대폰 밖으로 나가지 않는다는 약속. 되돌릴 수 없는 지우기는 모두
 * DangerButton + DestructiveConfirm(secure = true, 본문에 개인정보 없음 — D8).
 */
@Composable
fun WalletContent(
    state: WalletState,
    deviceSecure: Boolean,
    autoDestroy: Boolean,
    today: LocalDate,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onReset: () -> Unit,
    onAddPassport: () -> Unit,
    onDeletePassport: () -> Unit,
    onAddBooking: () -> Unit,
    onDeleteBooking: (String) -> Unit,
    onAutoDestroyChange: (Boolean) -> Unit,
    onOpenCompanions: () -> Unit = {},
) {
    var confirmReset by remember { mutableStateOf(false) }
    var confirmPassportDelete by remember { mutableStateOf(false) }
    var pendingBookingDelete by remember { mutableStateOf<String?>(null) }
    val unlocked = state as? WalletState.Unlocked
    AppScreen(
        title = stringResource(R.string.wallet_title),
        subtitle = stringResource(R.string.wallet_subtitle),
        speech = stringResource(R.string.wallet_speech),
    ) {
        // 이 정보가 휴대폰 밖으로 나가지 않는다는 약속을 맨 위에 보여 준다 (원칙 5). 열린 상태에서는 한 줄로 줄여
        // Navy 띠와 Navy 여권 카드가 붙어 여권 카드가 묻히지 않게 한다 — 여권 카드가 첫 주인공 (재검토 30)
        item(key = "privacy") { SecurityBanner(compact = unlocked != null) }
        if (!deviceSecure) {
            item(key = "no-lock") {
                NoticeBanner(
                    text = stringResource(R.string.wallet_no_lock_body),
                    title = stringResource(R.string.wallet_no_lock_title),
                    icon = Icons.Outlined.PhonelinkLock,
                    tone = BannerTone.Caution,
                )
            }
        }
        when (state) {
            is WalletState.Locked -> item(key = "locked") {
                LockedState(
                    title = stringResource(R.string.wallet_locked_title),
                    body = stringResource(R.string.wallet_locked_body),
                    buttonLabel = stringResource(R.string.wallet_unlock),
                    onUnlock = onUnlock,
                )
            }
            is WalletState.Failed -> item(key = "failed") {
                FailedState(state.reason, onUnlock = onUnlock, onReset = { confirmReset = true })
            }
            is WalletState.Unlocked -> {
                val passport = state.contents.passport
                item(key = "passport") {
                    if (passport == null) {
                        CardNewsCard(
                            title = stringResource(R.string.wallet_passport_title),
                            icon = Icons.Outlined.Badge,
                            body = stringResource(R.string.wallet_passport_body),
                        ) {
                            PrimaryButton(
                                stringResource(R.string.wallet_passport_add),
                                onClick = onAddPassport,
                                icon = Icons.Outlined.PhotoCamera,
                            )
                        }
                    } else {
                        // 지우기는 어두운 카드 안(D18)도, 카드 바로 아래 반폭(주인 없는 버튼처럼 떠 보임)도 아닌 화면 아래 관리 줄에 둔다
                        PassportCard(passport, today)
                    }
                }
                sectionGap("bookings-gap")
                item(key = "bookings-title") {
                    SectionHeader(
                        title = stringResource(R.string.wallet_bookings_title),
                        icon = Icons.Outlined.Description,
                        subtitle = if (state.contents.bookings.isEmpty()) stringResource(R.string.wallet_bookings_empty) else null,
                    )
                }
                state.contents.bookings.forEach { booking ->
                    item(key = "booking-${booking.id}") { BookingCard(booking, onDelete = { pendingBookingDelete = booking.id }) }
                }
                item(key = "booking-add") {
                    SecondaryButton(stringResource(R.string.wallet_booking_add), onClick = onAddBooking, icon = Icons.Outlined.Add)
                }
            }
        }
        sectionGap("settings-gap")
        item(key = "rows") {
            ListGroup {
                // 맨 위 SecurityBanner가 이미 '이 휴대폰에만'을 말하므로 행 설명은 한 문장만 (같은 약속 두 번 금지)
                ListRow(
                    title = stringResource(R.string.wallet_companions_title),
                    icon = Icons.Outlined.FamilyRestroom,
                    body = stringResource(R.string.wallet_companions_body),
                    trailing = RowTrailing.Chevron,
                    onClick = onOpenCompanions,
                )
                // 지우기 설정은 맨 아래 — 바로 아래 '여권 정보 지우기' 버튼과 한 자리에 모인다
                if (unlocked != null) {
                    ListDivider()
                    ListRow(
                        title = stringResource(R.string.wallet_auto_destroy),
                        icon = Icons.Outlined.AutoDelete,
                        body = stringResource(R.string.wallet_auto_destroy_desc),
                        trailing = RowTrailing.Switch(autoDestroy, onAutoDestroyChange),
                    )
                }
            }
        }
        // 여권 지우기 = 여권 카드의 단독 파괴 동작 → 관리 줄('여행이 끝나면 여권 정보 지우기' 바로 아래) 폭 전체 (재검토 26·30)
        if (unlocked?.contents?.passport != null) {
            item(key = "passport-delete") {
                DangerButton(stringResource(R.string.wallet_passport_delete), onClick = { confirmPassportDelete = true }, placement = ButtonPlacement.CardAction)
            }
        }
        if (unlocked != null) {
            item(key = "lock") { QuietButton(stringResource(R.string.wallet_lock), onClick = onLock, icon = Icons.Outlined.Lock) }
        }
        // 아직 만들지 않은 기능은 맨 아래 한 장으로 (D15)
        item(key = "coming-soon") {
            ComingSoonGroup(
                listOf(
                    Icons.Outlined.ContactPage to stringResource(R.string.wallet_profile_title),
                    Icons.Outlined.QrCode2 to stringResource(R.string.wallet_documents_title),
                ),
            )
        }
    }

    if (confirmReset) {
        DestructiveConfirm(
            title = stringResource(R.string.wallet_reset_confirm_title),
            body = stringResource(R.string.wallet_reset_confirm_body),
            confirmLabel = stringResource(R.string.wallet_reset),
            onConfirm = { confirmReset = false; onReset() },
            onDismiss = { confirmReset = false },
            secure = true,
        )
    }
    if (confirmPassportDelete) {
        DestructiveConfirm(
            title = stringResource(R.string.today_destroy_title),
            body = stringResource(R.string.today_destroy_body),
            confirmLabel = stringResource(R.string.wallet_passport_delete),
            onConfirm = { confirmPassportDelete = false; onDeletePassport() },
            onDismiss = { confirmPassportDelete = false },
            secure = true,
        )
    }
    pendingBookingDelete?.let { id ->
        DestructiveConfirm(
            title = stringResource(R.string.booking_delete_confirm_title),
            body = stringResource(R.string.booking_delete_confirm_body),
            confirmLabel = stringResource(R.string.wallet_booking_delete),
            onConfirm = { pendingBookingDelete = null; onDeleteBooking(id) },
            onDismiss = { pendingBookingDelete = null },
            secure = true,
        )
    }
}

/** 보관함을 열지 못한 상태: 본인 확인 시간 지남 → 다시 열기 / 키 분실·손상 → 비우고 다시 시작(확인 대화상자) */
@Composable
private fun FailedState(reason: WalletState.Failed.Reason, onUnlock: () -> Unit, onReset: () -> Unit) {
    when (reason) {
        WalletState.Failed.Reason.NeedsAuth -> LockedState(
            title = stringResource(R.string.wallet_locked_title),
            body = stringResource(R.string.wallet_needs_auth),
            buttonLabel = stringResource(R.string.wallet_unlock),
            onUnlock = onUnlock,
        )
        WalletState.Failed.Reason.KeyLost, WalletState.Failed.Reason.Corrupted -> {
            val keyLost = reason == WalletState.Failed.Reason.KeyLost
            CardNewsCard(
                title = stringResource(R.string.wallet_failed_title),
                icon = if (keyLost) Icons.Outlined.KeyOff else Icons.Outlined.ReportProblem,
                body = stringResource(if (keyLost) R.string.wallet_key_lost else R.string.wallet_corrupted),
                tone = BadgeTone.Caution,
                style = NewsStyle.Caution,
            ) {
                // 이 카드의 하나뿐인 행동 = 카드 단위 동작이라 폭 전체 (재검토2 ④#5 — DangerButton 폭 규칙을 부품이 정한다)
                DangerButton(
                    stringResource(R.string.wallet_reset),
                    onClick = onReset,
                    placement = ButtonPlacement.CardAction,
                    icon = Icons.Outlined.RestartAlt,
                )
            }
        }
    }
}

/**
 * 여권 카드 (24): Navy → AccentDeep 세로 그라데이션 + onDark 내용 세트만(passportCardColors — Gold eyebrow, White80 라벨, Surface 값).
 * 이름·번호는 기본으로 가린다 (PRD 5.5). 지우기 버튼은 이 카드 밖에 둔다(D18).
 */
@Composable
private fun PassportCard(passport: PassportRecord, today: LocalDate) {
    var revealed by remember { mutableStateOf(false) }
    val dimens = LocalDimens.current
    val colors = passportCardColors()
    val shape = MaterialTheme.shapes.large
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent, contentColor = colors.value),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(colors.gradientTop, colors.gradientBottom)))
                .padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.inner + 4.dp),
        ) {
            // 머리: PASSPORT · 여권 (Gold) + 확인 상태(자체 바탕 태그) — 폭이 모자라면 태그가 아래 줄로
            TrailingFlow(
                trailing = {
                    // 'MRZ' 대신 쉬운 말 (재검토 R18)
                    val tag = stringResource(if (passport.mrzVerified) R.string.wallet_passport_verified_v2 else R.string.wallet_passport_manual)
                    StatusTag(tag, if (passport.mrzVerified) StatusKind.Verified else StatusKind.Caution)
                },
                centerVertically = true,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Badge, contentDescription = null, tint = colors.icon, modifier = Modifier.size(dimens.icon))
                    KoText(stringResource(R.string.wallet_passport_eyebrow), MaterialTheme.typography.labelMedium, Modifier.weight(1f, fill = false), color = colors.eyebrow)
                }
            }
            PassportField(
                stringResource(R.string.wallet_passport_name),
                if (revealed) "${passport.surname} ${passport.givenNames}" else maskName(passport.surname, passport.givenNames),
                masked = !revealed,
            )
            val pairs = listOf(
                Triple(
                    stringResource(R.string.wallet_passport_number),
                    if (revealed) passport.documentNumber else maskNumber(passport.documentNumber),
                    !revealed,
                ),
                Triple(stringResource(R.string.wallet_passport_expiry), passport.expiryDate, false),
            )
            TileGrid(pairs, columns = rememberGridColumns()) { (label, value, masked), cell -> PassportField(label, value, cell, masked) }
            val expiry = runCatching { LocalDate.parse(passport.expiryDate) }.getOrNull()
            if (expiry != null && expiry.isBefore(today.plusMonths(6))) {
                val expired = expiry.isBefore(today)
                NoticeBanner(
                    text = stringResource(if (expired) R.string.wallet_passport_expired else R.string.wallet_passport_expiring),
                    icon = Icons.Outlined.EventBusy,
                    tone = if (expired) BannerTone.Danger else BannerTone.Caution,
                )
            }
            // `자세히 보기`는 다른 화면에서 '펼치기'라 같은 글자에 다른 동작 — 이 버튼은 가린 글자를 보이는 일이다 (재검토 R18)
            SecondaryButton(
                text = stringResource(if (revealed) R.string.wallet_passport_hide else R.string.wallet_passport_reveal),
                onClick = { revealed = !revealed },
                icon = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                onDark = true,
            )
        }
    }
}

/**
 * 여권 카드 안 라벨(White80) + 값(Surface titleLarge, tnum) = 공용 KeyValueRow(onDark, 재검토 R3). 라벨과 값을 한 번에 읽는다.
 * [masked]: 가린 값 — TalkBack은 점 대신 `가려 둔 값`으로 읽는다.
 */
@Composable
private fun PassportField(label: String, value: String, modifier: Modifier = Modifier, masked: Boolean = false) {
    KeyValueRow(label = label, value = value, modifier = modifier, onDark = true, valueStyle = ValueStyle.Large, masked = masked, verticalPadding = 0.dp)
}

/**
 * 예약 서류 한 장: 종류 아이콘 + 종류 eyebrow + 이름, 예약 번호·편명 사실 행, 날짜는 한 줄에 하나씩
 * (항공권 FlightTakeoff 가는 날 → FlightLand 오는 날, 숙소 체크인 → 체크아웃 — 글 안에 `·`·`→` 구분 기호를 두지 않아
 * 큰 글자에서 기호만 줄 끝에 매달리지 않는다), 오른쪽 아래 지우기.
 */
@Composable
private fun BookingCard(booking: BookingRecord, onDelete: () -> Unit) {
    val kind = when (booking.kind) {
        "flight" -> R.string.booking_kind_flight
        "lodging" -> R.string.booking_kind_lodging
        else -> R.string.booking_kind_other
    }
    CardNewsCard(
        title = booking.title,
        icon = IconKeys.bookingKind(booking.kind),
        eyebrow = stringResource(kind),
    ) {
        booking.reference?.let {
            BookingFact(Icons.Outlined.ConfirmationNumber, stringResource(R.string.booking_field_reference), it)
        }
        if (booking.flightNumbers.isNotEmpty()) {
            // 머리의 AirplaneTicket(항공권)과 겹치지 않게 편명은 Flight
            BookingFact(Icons.Outlined.Flight, stringResource(R.string.wallet_booking_flights), booking.flightNumbers.joinToString(", "))
        }
        bookingDates(booking).forEach { (icon, label, value) -> BookingFact(icon, stringResource(label), value) }
        // 목록 항목마다의 지우기 = 끝 정렬. TalkBack은 무엇을 지우는지(화면에 보이는 서류 이름) 함께 읽는다 (재검토 R18)
        val deleteName = stringResource(R.string.delete_named_cd, booking.title)
        DangerButton(
            stringResource(R.string.wallet_booking_delete),
            onClick = onDelete,
            placement = ButtonPlacement.ItemAction,
            contentDescription = deleteName,
        )
    }
}

/**
 * 날짜 행 (아이콘, 라벨, 날짜 하나). 숙소면 체크인·체크아웃, 항공권은 첫 날짜 = 가는 날(FlightTakeoff),
 * 둘 이상이면 마지막 = 오는 날(FlightLand — 입국 카드도 마지막 날짜를 돌아오는 편으로 본다), 가운데·그 밖의 날짜는 '날짜'.
 */
private fun bookingDates(booking: BookingRecord): List<Triple<ImageVector, Int, String>> {
    if (booking.checkIn != null || booking.checkOut != null) {
        return listOfNotNull(
            booking.checkIn?.let { Triple(Icons.AutoMirrored.Outlined.Login, R.string.booking_label_checkin, it) },
            booking.checkOut?.let { Triple(Icons.AutoMirrored.Outlined.Logout, R.string.booking_label_checkout, it) },
        )
    }
    val dates = booking.dates
    if (dates.isEmpty()) return emptyList()
    if (booking.kind != "flight") return dates.map { Triple(Icons.Outlined.CalendarMonth, R.string.wallet_booking_dates, it) }
    val out = Triple(Icons.Outlined.FlightTakeoff, R.string.wallet_booking_date_out, dates.first())
    if (dates.size == 1) return listOf(out)
    val middle = dates.subList(1, dates.lastIndex).map { Triple(Icons.Outlined.CalendarMonth, R.string.wallet_booking_dates, it) }
    return listOf(out) + middle + Triple(Icons.Outlined.FlightLand, R.string.wallet_booking_date_back, dates.last())
}

/** 사실 한 줄: 작은 배지 + 라벨 + 값(굵게, tnum) = 공용 KeyValueRow(leading, 재검토 R3). 라벨과 값을 한 번에 읽는다 */
@Composable
private fun BookingFact(icon: ImageVector, label: String, value: String) {
    KeyValueRow(label = label, value = value, leading = icon, verticalPadding = 0.dp)
}

internal fun maskName(surname: String, given: String): String {
    fun mask(s: String) = if (s.isEmpty()) "" else s.first() + "•".repeat((s.length - 1).coerceIn(2, 6))
    return listOf(mask(surname), mask(given.substringBefore(' '))).filter { it.isNotEmpty() }.joinToString(" ")
}

internal fun maskNumber(number: String): String =
    if (number.length <= 3) "•".repeat(number.length) else number.first() + "•".repeat(number.length - 3) + number.takeLast(2)
