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
import androidx.compose.material.icons.automirrored.outlined.AirplaneTicket
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FamilyRestroom
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconKeys
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
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.WalletState
import java.time.LocalDate

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
        // 이 정보가 휴대폰 밖으로 나가지 않는다는 약속을 맨 위에 크게 보여 준다 (원칙 5)
        item(key = "privacy") { SecurityBanner() }
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
                            PrimaryButton(stringResource(R.string.wallet_passport_add), onClick = onAddPassport, icon = Icons.Outlined.PhotoCamera)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner)) {
                            PassportCard(passport, today)
                            // 어두운 카드 안에는 파괴 버튼을 두지 않는다 — 카드 바로 아래 별도 줄 오른쪽 (D18)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                DangerButton(stringResource(R.string.wallet_passport_delete), onClick = { confirmPassportDelete = true })
                            }
                        }
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
                if (unlocked != null) {
                    ListRow(
                        title = stringResource(R.string.wallet_auto_destroy),
                        icon = Icons.Outlined.AutoDelete,
                        body = stringResource(R.string.wallet_auto_destroy_desc),
                        trailing = RowTrailing.Switch(autoDestroy, onAutoDestroyChange),
                    )
                    ListDivider()
                }
                ListRow(
                    title = stringResource(R.string.wallet_companions_title),
                    icon = Icons.Outlined.FamilyRestroom,
                    body = stringResource(R.string.companions_body),
                    trailing = RowTrailing.Chevron,
                    onClick = onOpenCompanions,
                )
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
                DangerButton(
                    stringResource(R.string.wallet_reset),
                    onClick = onReset,
                    icon = Icons.Outlined.RestartAlt,
                    fillWidth = true,
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
                    if (passport.mrzVerified) {
                        StatusTag(stringResource(R.string.wallet_passport_verified), StatusKind.Verified)
                    } else {
                        StatusTag(stringResource(R.string.wallet_passport_manual), StatusKind.Caution)
                    }
                },
                centerVertically = true,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Badge, contentDescription = null, tint = colors.icon, modifier = Modifier.size(dimens.icon))
                    Text(stringResource(R.string.wallet_passport_eyebrow), style = MaterialTheme.typography.labelMedium, color = colors.eyebrow)
                }
            }
            PassportField(
                stringResource(R.string.wallet_passport_name),
                if (revealed) "${passport.surname} ${passport.givenNames}" else maskName(passport.surname, passport.givenNames),
            )
            val pairs = listOf(
                stringResource(R.string.wallet_passport_number) to
                    if (revealed) passport.documentNumber else maskNumber(passport.documentNumber),
                stringResource(R.string.wallet_passport_expiry) to passport.expiryDate,
            )
            TileGrid(pairs, columns = rememberGridColumns()) { (label, value), cell -> PassportField(label, value, cell) }
            val expiry = runCatching { LocalDate.parse(passport.expiryDate) }.getOrNull()
            if (expiry != null && expiry.isBefore(today.plusMonths(6))) {
                val expired = expiry.isBefore(today)
                NoticeBanner(
                    text = stringResource(if (expired) R.string.wallet_passport_expired else R.string.wallet_passport_expiring),
                    icon = Icons.Outlined.EventBusy,
                    tone = if (expired) BannerTone.Danger else BannerTone.Caution,
                )
            }
            SecondaryButton(
                text = stringResource(if (revealed) R.string.wallet_passport_hide else R.string.wallet_passport_show),
                onClick = { revealed = !revealed },
                icon = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                onDark = true,
            )
        }
    }
}

/** 여권 카드 안 라벨(White80 bodySmall) + 값(Surface titleLarge, tnum). 라벨과 값을 한 번에 읽는다 */
@Composable
private fun PassportField(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = passportCardColors()
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.label)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = colors.value,
        )
    }
}

/** 예약 서류 한 장: 종류 아이콘 + 종류 eyebrow + 이름, 예약 번호·편명·날짜 사실 행, 오른쪽 아래 지우기 */
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
            BookingFact(
                Icons.AutoMirrored.Outlined.AirplaneTicket,
                stringResource(R.string.wallet_booking_flights),
                booking.flightNumbers.joinToString(", "),
            )
        }
        val dates = when {
            booking.checkIn != null || booking.checkOut != null -> "${booking.checkIn.orEmpty()} → ${booking.checkOut.orEmpty()}"
            booking.dates.isNotEmpty() -> booking.dates.joinToString(" · ")
            else -> null
        }
        dates?.let { BookingFact(Icons.Outlined.CalendarMonth, stringResource(R.string.wallet_booking_dates), it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DangerButton(stringResource(R.string.wallet_booking_delete), onClick = onDelete)
        }
    }
}

/** 사실 한 줄: 작은 배지 + 라벨(titleSmall) + 값(굵게, tnum). 라벨과 값을 한 번에 읽는다 */
@Composable
private fun BookingFact(icon: ImageVector, label: String, value: String) {
    val dimens = LocalDimens.current
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, tone = BadgeTone.Neutral, size = dimens.iconBadgeSmall)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = Tokens.InkSecondary)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = Tokens.Ink,
            )
        }
    }
}

internal fun maskName(surname: String, given: String): String {
    fun mask(s: String) = if (s.isEmpty()) "" else s.first() + "•".repeat((s.length - 1).coerceIn(2, 6))
    return listOf(mask(surname), mask(given.substringBefore(' '))).filter { it.isNotEmpty() }.joinToString(" ")
}

internal fun maskNumber(number: String): String =
    if (number.length <= 3) "•".repeat(number.length) else number.first() + "•".repeat(number.length - 3) + number.takeLast(2)
