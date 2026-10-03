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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.PhotoCamera
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
import com.readyport.stay.StayGrouping
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SecurityBanner
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.TileGrid
import com.readyport.ui.components.TrailingFlow
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.firstLineIconOffset
import com.readyport.ui.components.keepMonthDay
import com.readyport.ui.components.keepWords
import com.readyport.ui.components.passportCardColors
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.sectionGap
import com.readyport.ui.stay.StayGroupCard
import com.readyport.ui.stay.StayManageActions
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.WalletState
import java.time.LocalDate
import java.util.Locale

/** 버튼이 비활성인 이유 한 줄 (Info + bodyMedium InkSecondary) — 27 companion_add_hint와 같은 모양 */
@Composable
internal fun DisabledReason(text: String, modifier: Modifier = Modifier) {
    NoteLine(text, modifier)
}

/**
 * 버튼에 딸린 보조 한 줄 (아이콘 + bodyMedium InkSecondary): 비활성 이유, 지우기 버튼 **위**에서 무엇이 지워지는지(재검토2 ②#4).
 * 설명을 버튼 위에 두면 TalkBack도 설명을 먼저 읽고 버튼을 만난다(②#11).
 */
@Composable
internal fun NoteLine(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Info) {
    val style = MaterialTheme.typography.bodyMedium
    val iconSize = textIconSize(LocalDimens.current.iconSmall, style)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = Tokens.InkSecondary,
            modifier = Modifier.padding(top = firstLineIconOffset(style, iconSize)).size(iconSize),
        )
        KoText(text, style = style, color = Tokens.InkSecondary, modifier = Modifier.weight(1f))
    }
}

/**
 * 보이는 날짜 (재검토2 ①#13): `2026-11-03` → `2026년 11월 3일 (화)`, [weekday] = false면 `2031년 4월 15일`.
 * 저장 값·입력칸 값·사이트에 넣는 값은 그대로 두고 **보이는 글자만** 바꾼다. 날짜 모양이 아니면 받은 글자 그대로.
 * KeyValueRow 값(보통 Text)에 넣을 때는 [keepWords]로 감싸 API 33 미만에서 `2031/년`처럼 숫자와 단위가 갈라지지 않게 한다.
 */
@Composable
internal fun koreanDate(iso: String, weekday: Boolean = true): String {
    val date = runCatching { LocalDate.parse(iso.trim()) }.getOrNull() ?: return iso
    return koreanDate(date, weekday)
}

@Composable
internal fun koreanDate(date: LocalDate, weekday: Boolean = true): String = if (weekday) {
    stringResource(
        R.string.date_ymd_dow_s3, date.year, date.monthValue, date.dayOfMonth,
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.KOREAN),
    )
} else {
    stringResource(R.string.date_ymd_s3, date.year, date.monthValue, date.dayOfMonth)
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
    onEditStay: (String) -> Unit = {},
    onOpenTrip: (String) -> Unit = {},
    viewModel: WalletViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val autoDestroy by viewModel.autoDestroy.collectAsStateWithLifecycle()
    val stayGroups by viewModel.stayGroups.collectAsStateWithLifecycle()
    val countryNames by viewModel.countryNames.collectAsStateWithLifecycle()
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
        // 키 분실·손상 카드의 `비우고 여권 다시 등록하기`: 비운 뒤 바로 여권 등록으로 (재검토2 ②#4)
        onReset = { viewModel.reset(then = onAddPassport) },
        onAddPassport = onAddPassport,
        onDeletePassport = viewModel::deletePassport,
        onAddBooking = onAddBooking,
        onDeleteBooking = viewModel::deleteBooking,
        onAutoDestroyChange = viewModel::setAutoDestroy,
        onOpenCompanions = onOpenCompanions,
        stayGroups = stayGroups,
        countryNames = countryNames,
        onEditStay = onEditStay,
        onDeleteStay = viewModel::deleteStay,
        onOpenTrip = onOpenTrip,
    )
}

/**
 * 23 내 정보(잠김) / 24 내 정보(열림) (DESIGN_SPEC 6-23·24).
 * 맨 위 SecurityBanner(전체) — 이 정보가 휴대폰 밖으로 나가지 않는다는 약속. 되돌릴 수 없는 지우기는 모두
 * DangerButton + DestructiveConfirm(secure = true, 본문에 개인정보 없음 — D8).
 * 열린 화면 순서(다듬기 S3 + S2): 여권 카드 → 예약 서류 → **묵는 곳(모든 여행)** → 관리 줄(같이 가는 사람·여행이 끝나면
 * 여권 정보 지우기) → 잠그기 → (32dp) 지워지는 범위 한 줄 + 여권 정보 지우기 → (32dp) 곧 추가돼요.
 *
 * 묵는 곳 목록(다듬기 S2)은 **모든 여행의 숙소를 여행별로 모아 보는 곳**이다 — 숙소를 넣는 자리는 그 여행의 예약 단계이고
 * (부록 H 한 길 규칙), 여기서는 찾고 고치고 지운다. 여행이 사라진 숙소(예전 예약 서류에서 옮겨 온 것)도 여기서만 보인다.
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
    /** 모든 여행의 숙소 — 여행별 묶음 (Stays.group) */
    stayGroups: List<StayGrouping> = emptyList(),
    /** 나라 코드 → 한국어 이름 (팩에서 — 화면에 나라 코드를 보이지 않게) */
    countryNames: Map<String, String> = emptyMap(),
    onEditStay: (String) -> Unit = {},
    onDeleteStay: (String) -> Unit = {},
    onOpenTrip: (String) -> Unit = {},
) {
    var confirmReset by remember { mutableStateOf(false) }
    var confirmPassportDelete by remember { mutableStateOf(false) }
    var pendingBookingDelete by remember { mutableStateOf<String?>(null) }
    var pendingStayDelete by remember { mutableStateOf<String?>(null) }
    val unlocked = state as? WalletState.Unlocked
    val stayActions = StayManageActions(edit = onEditStay, delete = { pendingStayDelete = it }, openTrip = onOpenTrip)
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
                // 묵는 곳 (다듬기 S2): 모든 여행의 숙소를 여행별로. 넣는 자리는 그 여행의 예약 단계라 여기에 `숙소 추가`를 두지 않는다
                sectionGap("stays-gap")
                item(key = "stays-title") {
                    SectionHeader(
                        title = stringResource(R.string.wallet_stays_title),
                        icon = Icons.Outlined.Hotel,
                        subtitle = stringResource(
                            if (stayGroups.isEmpty()) R.string.wallet_stays_empty else R.string.wallet_stays_lead,
                        ),
                    )
                }
                stayGroups.forEachIndexed { i, group ->
                    item(key = "stay-group-${group.trip?.id ?: "none"}-$i") {
                        StayGroupCard(group, group.trip?.let { countryNames[it.country] }, stayActions)
                    }
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
        if (unlocked != null) {
            item(key = "lock") { QuietButton(stringResource(R.string.wallet_lock), onClick = onLock, icon = Icons.Outlined.Lock) }
        }
        // 여권 지우기 = 화면의 단독 파괴 동작 → 맨 아래 따로, 위 간격 32dp(sectionGap) — 같은 글자의 설정
        // `여행이 끝나면 여권 정보 지우기`·`내 정보 잠그기` 바로 밑에서 설정 설명이나 잠그기로 착각해 누르지 않게(재검토2 ②#4).
        // 버튼 위 한 줄이 무엇이 지워지고 무엇이 남는지 말한다(TalkBack도 설명 → 버튼 순서)
        if (unlocked?.contents?.passport != null) {
            sectionGap("passport-delete-gap")
            item(key = "passport-delete") {
                Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner)) {
                    NoteLine(stringResource(R.string.wallet_passport_delete_scope))
                    DangerButton(
                        stringResource(R.string.wallet_passport_delete),
                        onClick = { confirmPassportDelete = true },
                        placement = ButtonPlacement.CardAction,
                    )
                }
            }
        }
        // 아직 없는 기능은 열린 내 정보 맨 아래 한 장으로 모은다(D15, 재검토2 ⑤#11): 여권 칩 확인도 값 확인 화면 대신 여기에.
        // `받은 서류 (QR·확인서)`는 이미 `입국 때 보여 주기`에 있는 기능이라 '곧 추가'에서 뺐다. 잠김·키 분실 화면에는 두지 않는다
        if (unlocked != null) {
            sectionGap("coming-soon-gap")
            item(key = "coming-soon") {
                ComingSoonGroup(
                    listOf(
                        Icons.Outlined.ContactPage to stringResource(R.string.wallet_profile_title),
                        Icons.Outlined.Nfc to stringResource(R.string.passport_chip_soon_v2),
                    ),
                )
            }
        }
    }

    if (confirmReset) {
        DestructiveConfirm(
            title = stringResource(R.string.wallet_reset_confirm_title),
            body = stringResource(R.string.wallet_reset_confirm_body),
            // 확인 버튼도 카드 버튼과 같은 말 — 누르면 비우고 여권 등록으로 간다
            confirmLabel = stringResource(R.string.wallet_reset_reregister),
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
    // 숙소 지우기도 되돌릴 수 없다 — 숙소 고치기 화면과 같은 확인 대화상자(본문에 개인정보 없음, D8)
    pendingStayDelete?.let { id ->
        DestructiveConfirm(
            title = stringResource(R.string.stay_delete_confirm_title),
            body = stringResource(R.string.stay_delete_confirm_body),
            confirmLabel = stringResource(R.string.stay_delete_confirm),
            onConfirm = { pendingStayDelete = null; onDeleteStay(id) },
            onDismiss = { pendingStayDelete = null },
            secure = true,
        )
    }
}

/**
 * 보관함을 열지 못한 상태: 본인 확인 시간 지남 → 다시 열기 / 키 분실·손상 → 비우고 여권 다시 등록(확인 대화상자 → 여권 등록 화면).
 * 키 분실 카드 = 카드 안 판정 규칙(재검토2 ①#4·②#4): 호박색 채움 카드 대신 **흰 카드 + 왼쪽 4dp Caution 막대**(다른 흰 카드와 같은 옅은
 * 테두리·그림자 — 운영자 결정 6) — 빨간 테두리 버튼이 흰 바탕 위에 놓인다. 본문 아래 한 줄이 무엇이 지워지고 무엇이 남는지 말한다.
 */
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
                style = NewsStyle.SurfaceCaution,
                // SurfaceCaution은 그림자가 없다 — 흰 카드 경계(옅은 1dp 테두리 + 그림자)는 다른 흰 카드와 같게 (화면 쪽 보정)
                modifier = Modifier.cardShadow(MaterialTheme.shapes.large),
            ) {
                NoteLine(stringResource(R.string.wallet_reset_scope), icon = Icons.Outlined.DeleteSweep)
                // 이 카드의 하나뿐인 행동 = 카드 단위 동작이라 폭 전체 (재검토2 ④#5). 무엇을 하는지(비우고 → 여권 다시 등록) 이름에
                DangerButton(
                    stringResource(R.string.wallet_reset_reregister),
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
                // 만료일은 `2031년 4월 15일`로 보인다(재검토2 ①#13 — 저장 값은 그대로). `4월 15일`은 한 덩어리로 줄을 바꾼다
                Triple(stringResource(R.string.wallet_passport_expiry), keepWords(keepMonthDay(koreanDate(passport.expiryDate, weekday = false))), false),
            )
            TileGrid(pairs, columns = rememberGridColumns()) { (label, value, masked), cell -> PassportField(label, value, cell, masked) }
            val expiry = runCatching { LocalDate.parse(passport.expiryDate) }.getOrNull()
            if (expiry != null && expiry.isBefore(today.plusMonths(6))) {
                // 카드 안 판정 = 상태 알약 + 보통 본문(재검토2 ①#4) — 여권 카드 안에 채움 + 막대 띠를 두지 않는다
                val expired = expiry.isBefore(today)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusTag(
                        stringResource(if (expired) R.string.passport_expired_tag else R.string.wallet_passport_expiring_tag),
                        if (expired) StatusKind.Prohibited else StatusKind.Caution,
                        icon = Icons.Outlined.EventBusy,
                    )
                    KoText(
                        stringResource(if (expired) R.string.wallet_passport_expired else R.string.wallet_passport_expiring),
                        MaterialTheme.typography.bodyMedium,
                        color = colors.value,
                    )
                }
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
        // 날짜는 `2026년 11월 3일 (화)`로 보인다(재검토2 ①#13 — 저장 값·입국 카드에 넣는 값은 그대로)
        bookingDates(booking).forEach { (icon, label, value) -> BookingFact(icon, stringResource(label), keepWords(koreanDate(value))) }
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
