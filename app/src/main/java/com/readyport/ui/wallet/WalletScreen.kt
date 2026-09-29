package com.readyport.ui.wallet

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.security.DeviceAuth
import com.readyport.security.SecureScreen
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.TopicCard
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
    AppScreen(
        title = stringResource(R.string.wallet_title),
        subtitle = stringResource(R.string.wallet_subtitle),
        speech = stringResource(R.string.wallet_speech),
    ) {
        // 이 정보가 휴대폰 밖으로 나가지 않는다는 약속을 맨 위에 크게 보여 준다
        item(key = "privacy") { com.readyport.ui.settings.LocalOnlyBanner() }
        if (!deviceSecure) {
            item(key = "no-lock") {
                TopicCard(
                    stringResource(R.string.wallet_no_lock_title),
                    stringResource(R.string.wallet_no_lock_body),
                    tone = CardTone.Caution,
                )
            }
        }
        when (state) {
            is WalletState.Locked -> item(key = "locked") {
                InfoCard {
                    Text(stringResource(R.string.wallet_locked_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.wallet_locked_body), style = MaterialTheme.typography.bodyMedium)
                    PrimaryButton(stringResource(R.string.wallet_unlock), onClick = onUnlock)
                }
            }
            is WalletState.Failed -> item(key = "failed") {
                InfoCard(tone = CardTone.Caution) {
                    when (state.reason) {
                        WalletState.Failed.Reason.NeedsAuth -> {
                            Text(stringResource(R.string.wallet_needs_auth), style = MaterialTheme.typography.bodyLarge)
                            PrimaryButton(stringResource(R.string.wallet_unlock), onClick = onUnlock)
                        }
                        WalletState.Failed.Reason.KeyLost, WalletState.Failed.Reason.Corrupted -> {
                            Text(
                                stringResource(
                                    if (state.reason == WalletState.Failed.Reason.KeyLost) R.string.wallet_key_lost else R.string.wallet_corrupted,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            PrimaryButton(stringResource(R.string.wallet_reset), onClick = onReset)
                        }
                    }
                }
            }
            is WalletState.Unlocked -> {
                val passport = state.contents.passport
                item(key = "passport") {
                    if (passport == null) {
                        InfoCard {
                            Text(stringResource(R.string.wallet_passport_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.wallet_passport_body), style = MaterialTheme.typography.bodyMedium)
                            PrimaryButton(stringResource(R.string.wallet_passport_add), onClick = onAddPassport)
                        }
                    } else {
                        PassportCard(passport, onDelete = onDeletePassport)
                    }
                }
                if (passport != null) {
                    val expiry = runCatching { LocalDate.parse(passport.expiryDate) }.getOrNull()
                    if (expiry != null && expiry.isBefore(today.plusMonths(6))) {
                        item(key = "expiry-warning") {
                            val expired = expiry.isBefore(today)
                            TopicCard(
                                title = stringResource(if (expired) R.string.wallet_passport_expired else R.string.wallet_passport_expiring),
                                body = null,
                                tone = CardTone.Caution,
                            )
                        }
                    }
                }
                item(key = "bookings-title") {
                    Text(stringResource(R.string.wallet_bookings_title), style = MaterialTheme.typography.titleLarge)
                }
                if (state.contents.bookings.isEmpty()) {
                    item(key = "bookings-empty") {
                        Text(
                            stringResource(R.string.wallet_bookings_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.contents.bookings.forEach { booking ->
                    item(key = "booking-${booking.id}") { BookingCard(booking, onDelete = { onDeleteBooking(booking.id) }) }
                }
                item(key = "booking-add") {
                    OutlinedButton(
                        onClick = onAddBooking,
                        modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight),
                    ) { Text(stringResource(R.string.wallet_booking_add), style = MaterialTheme.typography.labelLarge) }
                }
                item(key = "auto-destroy") {
                    InfoCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.toggleable(autoDestroy, role = Role.Switch, onValueChange = onAutoDestroyChange),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.wallet_auto_destroy), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.wallet_auto_destroy_desc), style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(checked = autoDestroy, onCheckedChange = null)
                        }
                    }
                }
                item(key = "lock") {
                    TextButton(onClick = onLock, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.wallet_lock), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item(key = "profile") { TopicCard(stringResource(R.string.wallet_profile_title), null, comingSoon = true) }
        item(key = "companions") {
            InfoCard {
                Text(stringResource(R.string.wallet_companions_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.companions_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onOpenCompanions, modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight)) {
                    Text(stringResource(R.string.companions_title), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item(key = "documents") { TopicCard(stringResource(R.string.wallet_documents_title), null, comingSoon = true) }
    }
}

/** 남색 여권 카드. 이름·번호는 기본으로 가린다 (PRD 5.5) */
@Composable
private fun PassportCard(passport: PassportRecord, onDelete: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    InfoCard(tone = CardTone.Navy) {
        Text(stringResource(R.string.wallet_passport_card_label), style = MaterialTheme.typography.labelLarge)
        LabeledValue(
            stringResource(R.string.wallet_passport_name),
            if (revealed) "${passport.surname} ${passport.givenNames}" else maskName(passport.surname, passport.givenNames),
        )
        LabeledValue(
            stringResource(R.string.wallet_passport_number),
            if (revealed) passport.documentNumber else maskNumber(passport.documentNumber),
        )
        LabeledValue(stringResource(R.string.wallet_passport_expiry), passport.expiryDate)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (passport.mrzVerified) {
                StatusChip(stringResource(R.string.wallet_passport_verified), container = Tokens.SuccessBg, content = Tokens.SuccessText)
            } else {
                StatusChip(stringResource(R.string.wallet_passport_manual), container = Tokens.CautionBg, content = Tokens.CautionText)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { revealed = !revealed }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    stringResource(if (revealed) R.string.wallet_passport_hide else R.string.wallet_passport_show),
                    color = Tokens.Surface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.wallet_passport_delete), color = Tokens.Surface, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun BookingCard(booking: BookingRecord, onDelete: () -> Unit) {
    InfoCard {
        val kind = when (booking.kind) {
            "flight" -> R.string.booking_kind_flight
            "lodging" -> R.string.booking_kind_lodging
            else -> R.string.booking_kind_other
        }
        StatusChip(stringResource(kind))
        Text(booking.title, style = MaterialTheme.typography.titleMedium)
        booking.reference?.let { LabeledValue(stringResource(R.string.booking_field_reference), it) }
        if (booking.flightNumbers.isNotEmpty()) {
            LabeledValue(stringResource(R.string.booking_field_flights), booking.flightNumbers.joinToString(", "))
        }
        if (booking.checkIn != null || booking.checkOut != null) {
            Text("${booking.checkIn.orEmpty()} → ${booking.checkOut.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
        } else if (booking.dates.isNotEmpty()) {
            Text(booking.dates.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.wallet_booking_delete), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

internal fun maskName(surname: String, given: String): String {
    fun mask(s: String) = if (s.isEmpty()) "" else s.first() + "•".repeat((s.length - 1).coerceIn(2, 6))
    return listOf(mask(surname), mask(given.substringBefore(' '))).filter { it.isNotEmpty() }.joinToString(" ")
}

internal fun maskNumber(number: String): String =
    if (number.length <= 3) "•".repeat(number.length) else number.first() + "•".repeat(number.length - 3) + number.takeLast(2)
