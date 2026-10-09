package com.readyport.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.readyport.BuildConfig
import com.readyport.R
import com.readyport.trip.ChecklistReminders
import com.readyport.trip.TripNotifications
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.ChoiceSegments
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.InfoChip
import com.readyport.ui.components.ExpandableDetail
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LinkRow
import com.readyport.ui.components.ListDivider
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.PhotoCredit
import com.readyport.ui.components.Photos
import com.readyport.ui.components.RowTrailing
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SecurityBanner
import com.readyport.ui.components.breakAfter
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.keepTogether
import com.readyport.ui.components.keepWords
import com.readyport.ui.components.loadPhotoCredits
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.rememberPhotoLift
import com.readyport.ui.components.rememberThumbnail
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.textIconSize
import com.readyport.ui.components.firstLineIconOffset
import com.readyport.ui.notice.longDate
import com.readyport.ui.board.BoardSettingsBinding
import com.readyport.ui.board.BoardSettingsGroup
import androidx.compose.material.icons.outlined.Public
import java.time.LocalDate
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.trip.alertHourLabel

/**
 * 22 설정 (DESIGN_SPEC 6-22): 맨 위 '내 정보는 이 휴대폰에만'(SecurityBanner 모양) → 묶음 4개(내 정보 · 화면·사용 · 데이터 · 안내·출처).
 * 행은 배지 + 제목·설명 + 끝 요소 — 설명은 끝 요소(스위치·셰브론) 아래까지 넓어지고, 글자를 크게 키우면 제목·설명이 폭 전체를 쓴다.
 * 켬·끔은 줄 전체가 Role.Switch.
 */
@Composable
fun SettingsScreen(
    easyMode: Boolean,
    onEasyModeChange: (Boolean) -> Unit,
    childMode: Boolean = false,
    onChildModeChange: (Boolean) -> Unit = {},
    wifiOnly: Boolean = true,
    onWifiOnlyChange: (Boolean) -> Unit = {},
    onOpenMyInfo: () -> Unit = {},
    onOpenFamily: () -> Unit = {},
    onOpenPhotos: () -> Unit = {},
    onOpenPrivacy: ((String) -> Unit)? = null,
    alertsOn: Boolean = true,
    onAlertsOnChange: (Boolean) -> Unit = {},
    alertHour: Int = ChecklistReminders.DEFAULT_HOUR,
    onAlertHourChange: (Int) -> Unit = {},
    /** 휴대폰 알림 권한이 있는지. null이면 이 화면이 직접 본다(테스트·갤러리는 값을 넣는다) */
    notifGranted: Boolean? = null,
    /** 휴대폰 알림 설정 열기. null이면 시스템 설정을 연다 */
    onOpenNotifSettings: (() -> Unit)? = null,
    /** 공지·소식 (docs/NOTICES_PUSH.md): 공지 알림(기본 켬) · 광고성 소식(기본 끔, 동의한 날) · 밤 광고 알림(따로 동의) */
    notices: NoticeSettings = NoticeSettings(),
    onNoticePushChange: (Boolean) -> Unit = {},
    onPromoPushChange: (Boolean) -> Unit = {},
    onPromoNightChange: (Boolean) -> Unit = {},
    onOpenNotices: () -> Unit = {},
    /** 설정 › 게시판 (docs/BOARD.md) — null이면 묶음을 그리지 않는다(자녀 폰 모드 등) */
    board: BoardSettingsBinding? = null,
    boardReplies: Boolean = true,
    onBoardRepliesChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val openPrivacy = onOpenPrivacy ?: { url: String -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    val granted = notifGranted ?: rememberNotificationsGranted()
    val openNotifSettings = onOpenNotifSettings ?: { openSystemNotificationSettings(context) }
    val large = isStackedLayout()
    val linkStart = if (large) LocalDimens.current.listRowPadding - 4.dp else rowTextStart() - 4.dp
    /** 카드 안 행과 같은 가로 여백 (행 아래 세그먼트·버튼이 카드 폭을 그대로 쓰게) */
    val rowPadding = LocalDimens.current.listRowPadding
    // 광고성 소식 동의·철회 결과 알림 (정보통신망법 제50조 제8항 — 보내는 곳·처리한 날·결과를 바로 보여 준다)
    var consentResult by rememberSaveable { mutableStateOf<ConsentResult?>(null) }
    consentResult?.let { r -> ConsentResultDialog(r, LocalDate.now()) { consentResult = null } }
    AppScreen(
        title = stringResource(R.string.settings_title),
        speech = stringResource(R.string.settings_speech),
    ) {
        // 개인정보가 기기 밖으로 나가지 않는다는 약속을 설정 맨 위에 항상 보여 준다
        item(key = "local-only") { SecurityBanner() }
        // '여권·예약 서류 관리'는 배너 바로 아래 첫 행 (320×470에서도 스크롤 없이 누를 수 있게)
        item(key = "group-myinfo") {
            ListGroup(stringResource(R.string.settings_group_myinfo)) {
                SettingRow(
                    stringResource(R.string.settings_myinfo_open),
                    icon = Icons.Outlined.Badge,
                    body = stringResource(R.string.settings_myinfo_body),
                    onClick = onOpenMyInfo,
                )
                ListDivider()
                SettingRow(
                    stringResource(R.string.wallet_companions_title),
                    icon = Icons.Outlined.FamilyRestroom,
                    body = stringResource(R.string.settings_family_mode_desc),
                    onClick = onOpenFamily,
                )
            }
        }
        sectionGap("gap-display")
        item(key = "group-display") {
            ListGroup(stringResource(R.string.settings_group_display)) {
                SettingRow(
                    stringResource(R.string.settings_easy_mode),
                    icon = Icons.Outlined.TextIncrease,
                    body = stringResource(R.string.settings_easy_mode_desc),
                    trailing = RowTrailing.Switch(easyMode, onEasyModeChange),
                    easyPreview = true,
                )
                ListDivider()
                SettingRow(
                    stringResource(R.string.settings_child_mode),
                    icon = Icons.Outlined.ChildCare,
                    body = stringResource(R.string.settings_child_mode_desc),
                    trailing = RowTrailing.Switch(childMode, onChildModeChange),
                )
            }
        }
        sectionGap("gap-alerts")
        // 알림 (PRD 6.1): 켬·끔 → 알려 줄 시각 → (권한이 없으면) 휴대폰 설정 열기 → 이 휴대폰 안에서만 읽는다는 한 줄
        item(key = "group-alerts") {
            ListGroup(stringResource(R.string.settings_group_alerts)) {
                SettingRow(
                    stringResource(R.string.settings_alerts),
                    icon = Icons.Outlined.NotificationsActive,
                    body = stringResource(R.string.settings_alerts_desc),
                    trailing = RowTrailing.Switch(alertsOn, onAlertsOnChange),
                )
                if (alertsOn) {
                    ListDivider()
                    SettingRow(
                        stringResource(R.string.settings_alert_hour),
                        icon = Icons.Outlined.Schedule,
                        body = stringResource(R.string.settings_alert_hour_desc),
                        trailing = RowTrailing.None,
                    )
                    // 세그먼트·버튼은 카드 안쪽 여백에 맞춰 폭을 다 쓴다(글 시작선에 맞추면 `아침 9시`가 두 줄로 접힌다)
                    Box(Modifier.padding(horizontal = rowPadding).padding(bottom = 16.dp)) {
                        ChoiceSegments(
                            options = ChecklistReminders.HOURS,
                            selected = ChecklistReminders.hourOrDefault(alertHour),
                            onSelect = onAlertHourChange,
                            label = { alertHourLabel(it) },
                            icon = { null },
                        )
                    }
                    if (!granted) {
                        ListDivider()
                        SettingRow(
                            stringResource(R.string.settings_alerts_blocked),
                            icon = Icons.Outlined.NotificationsOff,
                            body = stringResource(R.string.settings_alerts_blocked_desc),
                            trailing = RowTrailing.None,
                        )
                        Box(Modifier.padding(horizontal = rowPadding).padding(bottom = 16.dp)) {
                            SecondaryButton(
                                stringResource(R.string.settings_alerts_open),
                                onClick = openNotifSettings,
                                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                            )
                        }
                    }
                }
            }
        }
        item(key = "alerts-local") { IconBullet(stringResource(R.string.settings_alerts_local), Icons.Outlined.Lock) }
        sectionGap("gap-notices")
        // 공지·소식: 공지사항 → 공지 알림 → 광고성 소식 받기(동의한 날) → (켰으면) 밤에도 받기 → 토픽만 쓴다는 한 줄
        item(key = "group-notices") {
            NoticeSettingsGroup(
                notices,
                onOpenNotices = onOpenNotices,
                onNoticePushChange = onNoticePushChange,
                onPromoPushChange = { on ->
                    onPromoPushChange(on)
                    consentResult = if (on) ConsentResult.PromoOn else ConsentResult.PromoOff
                },
                onPromoNightChange = { on ->
                    onPromoNightChange(on)
                    consentResult = if (on) ConsentResult.NightOn else ConsentResult.NightOff
                },
            )
        }
        item(key = "notices-local") { IconBullet(stringResource(R.string.settings_notices_local), Icons.Outlined.Lock) }
        // 게시판: 답글 알림 · 내 게시판 ID · 규칙 · 차단 · (운영자) 사진·동영상 · 신고 관리 · 내 기록 지우기
        if (board != null && !childMode) {
            sectionGap("gap-board")
            item(key = "group-board") { BoardSettingsGroup(board, boardReplies, onBoardRepliesChange) }
            item(key = "board-local") { IconBullet(stringResource(R.string.settings_board_local), Icons.Outlined.Public) }
        }
        sectionGap("gap-data")
        item(key = "group-data") {
            ListGroup(stringResource(R.string.settings_group_data)) {
                SettingRow(
                    stringResource(R.string.explore_wifi_only),
                    icon = Icons.Outlined.Wifi,
                    body = stringResource(R.string.explore_wifi_only_desc),
                    trailing = RowTrailing.Switch(wifiOnly, onWifiOnlyChange),
                )
            }
        }
        sectionGap("gap-about")
        item(key = "group-about") {
            ListGroup(stringResource(R.string.settings_group_about)) {
                SettingRow(
                    stringResource(R.string.settings_privacy),
                    icon = Icons.Outlined.PrivacyTip,
                    // '기기' 대신 맨 위 약속과 같은 '휴대폰' (재검토 R18)
                    body = stringResource(R.string.settings_privacy_body_v2),
                    trailing = RowTrailing.None,
                )
                // Play 정책: 개인정보처리방침은 스토어와 앱 안 모두에서 볼 수 있어야 한다 — 무엇이 열리는지 글자로 보이는 링크 줄
                LinkRow(
                    stringResource(R.string.settings_privacy_open),
                    onClick = { openPrivacy(PRIVACY_URL) },
                    modifier = Modifier.padding(start = linkStart, end = 12.dp, bottom = 8.dp),
                )
                ListDivider()
                SettingRow(
                    stringResource(R.string.settings_disclaimer),
                    icon = Icons.Outlined.Policy,
                    body = stringResource(R.string.settings_disclaimer_body),
                    trailing = RowTrailing.None,
                )
                ListDivider()
                SettingRow(
                    stringResource(R.string.settings_credits),
                    icon = Icons.Outlined.PhotoLibrary,
                    body = stringResource(R.string.settings_credits_body),
                    onClick = onOpenPhotos,
                )
                ListDivider()
                SettingRow(
                    stringResource(R.string.settings_about),
                    icon = Icons.Outlined.Info,
                    body = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    trailing = RowTrailing.None,
                )
            }
        }
    }
}

/**
 * 휴대폰 알림 권한(POST_NOTIFICATIONS)이 있는지 — 설정 화면이 다시 보일 때마다 새로 본다
 * (휴대폰 설정에서 켜고 돌아오면 안내 줄이 저절로 사라진다).
 */
@Composable
private fun rememberNotificationsGranted(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var granted by remember { mutableStateOf(TripNotifications.canNotify(context)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = TripNotifications.canNotify(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return granted
}

/** 이 앱의 휴대폰 알림 설정 화면 (못 열면 앱 정보 화면) */
private fun openSystemNotificationSettings(context: Context) {
    val app = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(app) }.isSuccess) return
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(details) }
}

/** 행 글자가 시작하는 자리 = 행 가로 여백(listRowPadding) + 배지 + 간격 16 */
@Composable
private fun rowTextStart(): Dp = LocalDimens.current.listRowPadding + LocalDimens.current.iconBadge + 16.dp

/**
 * 설정 한 줄 = 공용 ListRow (재검토 R3·R4 — 같은 행 부품 하나, 여백 토큰 listRowPadding). [easyPreview]면 설명 아래 `가 → 가` 미리보기(장식).
 * 큰 글자 배치에서는 ListRow가 배지·끝 요소를 윗줄로 올리고 제목·설명에 폭 전체를 준다.
 */
@Composable
private fun SettingRow(
    title: String,
    icon: ImageVector,
    body: String? = null,
    trailing: RowTrailing = RowTrailing.Chevron,
    onClick: (() -> Unit)? = null,
    easyPreview: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
) {
    ListRow(
        title = title,
        icon = icon,
        body = body,
        trailing = trailing,
        onClick = onClick,
        extra = if (easyPreview) {
            { EasyModePreview() }
        } else {
            extra
        },
    )
}

/** 설정 › 공지·소식 의 값 (AppSettings에서 그대로) */
@Immutable
data class NoticeSettings(
    val noticePush: Boolean = true,
    val promoPush: Boolean = false,
    /** 광고성 소식 받기를 켜거나 끈 날 yyyy-MM-dd */
    val promoDate: String? = null,
    val promoNight: Boolean = false,
    val promoNightDate: String? = null,
)

/**
 * 공지·소식 묶음. 광고성 소식은 기본 끔이고 사람이 직접 켠다 — 켜거나 끈 날을 그 줄 아래에 보인다(이 휴대폰에만 적어 둔 값).
 * 밤 광고 알림 줄은 광고성 소식을 켰을 때만 보인다(따로 동의).
 */
@Composable
private fun NoticeSettingsGroup(
    s: NoticeSettings,
    onOpenNotices: () -> Unit,
    onNoticePushChange: (Boolean) -> Unit,
    onPromoPushChange: (Boolean) -> Unit,
    onPromoNightChange: (Boolean) -> Unit,
) {
    ListGroup(stringResource(R.string.settings_group_notices)) {
        SettingRow(
            stringResource(R.string.notices_title),
            icon = Icons.Outlined.Campaign,
            body = stringResource(R.string.settings_notices_open_body),
            onClick = onOpenNotices,
        )
        ListDivider()
        SettingRow(
            stringResource(R.string.settings_notice_push),
            icon = Icons.Outlined.NotificationAdd,
            body = stringResource(R.string.settings_notice_push_desc),
            trailing = RowTrailing.Switch(s.noticePush, onNoticePushChange),
        )
        ListDivider()
        val promoDay = s.promoDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        SettingRow(
            stringResource(R.string.settings_promo_push),
            icon = Icons.Outlined.Sell,
            body = stringResource(R.string.settings_promo_push_desc),
            trailing = RowTrailing.Switch(s.promoPush, onPromoPushChange),
            extra = promoDay?.let { day ->
                {
                    val date = longDate(day)
                    ConsentLine(
                        stringResource(if (s.promoPush) R.string.settings_promo_agreed else R.string.settings_promo_declined, date),
                        agreed = s.promoPush,
                    )
                }
            },
        )
        if (s.promoPush) {
            ListDivider()
            val nightDay = s.promoNightDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            SettingRow(
                stringResource(R.string.settings_promo_night),
                icon = Icons.Outlined.Bedtime,
                body = stringResource(R.string.settings_promo_night_desc),
                trailing = RowTrailing.Switch(s.promoNight, onPromoNightChange),
                extra = nightDay?.takeIf { s.promoNight }?.let { day ->
                    { ConsentLine(stringResource(R.string.settings_promo_night_agreed, longDate(day)), agreed = true) }
                },
            )
        }
    }
}

/** 동의한 날 한 줄 (달력 아이콘 + 글, 누를 수 없음) */
@Composable
private fun ConsentLine(text: String, agreed: Boolean) {
    val style = MaterialTheme.typography.bodyMedium
    val size = textIconSize(LocalDimens.current.iconSmall + 4.dp, style)
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            if (agreed) Icons.Outlined.EventAvailable else Icons.Outlined.EventBusy,
            contentDescription = null,
            tint = if (agreed) Tokens.SuccessText else Tokens.InkSecondary,
            modifier = Modifier.padding(top = firstLineIconOffset(style, size)).size(size),
        )
        KoText(text, style, color = Tokens.Ink)
    }
}

/** 광고성 소식 동의·철회 결과 */
enum class ConsentResult { PromoOn, PromoOff, NightOn, NightOff }

/** 결과 알림: 보내는 곳·처리한 날·바뀐 것 (확인 하나) */
@Composable
private fun ConsentResultDialog(result: ConsentResult, today: LocalDate, onDismiss: () -> Unit) {
    val title = when (result) {
        ConsentResult.PromoOn -> R.string.promo_result_on_title
        ConsentResult.PromoOff -> R.string.promo_result_off_title
        ConsentResult.NightOn -> R.string.promo_night_result_on_title
        ConsentResult.NightOff -> R.string.promo_night_result_off_title
    }
    val tail = when (result) {
        ConsentResult.PromoOn -> R.string.promo_result_on_tail
        ConsentResult.PromoOff -> R.string.promo_result_off_tail
        ConsentResult.NightOn -> R.string.promo_night_result_on_tail
        ConsentResult.NightOff -> R.string.promo_night_result_off_tail
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Accent), modifier = Modifier.minTouch()) {
                KoText(stringResource(R.string.notice_confirm), MaterialTheme.typography.labelLarge)
            }
        },
        icon = { Icon(Icons.Outlined.Sell, contentDescription = null, tint = Tokens.Accent) },
        title = { KoText(stringResource(title), MaterialTheme.typography.titleLarge, glueShort = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                KoText(stringResource(R.string.promo_result_body, longDate(today), stringResource(tail)), MaterialTheme.typography.bodyLarge)
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = Tokens.Surface,
        titleContentColor = Tokens.Ink,
        textContentColor = Tokens.Ink,
    )
}

/** 쉬운 모드 미리보기 글자 (장식, TalkBack에서 숨김) */
private const val PREVIEW_GLYPH = "가"

/** 쉬운 모드 행의 '가 → 가' 미리보기. clearAndSetSemantics로 숨긴다(TalkBack 잡음) */
@Composable
private fun EasyModePreview() {
    Row(
        Modifier.padding(top = 6.dp).clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(PREVIEW_GLYPH, style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        // 화살표도 옆 글자를 따라 커진다(최대 1.5배) — 200%에서 점처럼 작아 보이지 않게 (재검토2 ①#14)
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = Tokens.InkTertiary,
            modifier = Modifier.size(textIconSize(LocalDimens.current.icon, MaterialTheme.typography.bodyMedium)),
        )
        Text(PREVIEW_GLYPH, style = MaterialTheme.typography.headlineMedium, color = Tokens.Accent)
    }
}

// ======================= 28 사진·글꼴 출처 =======================

/** 앱에 넣은 글꼴의 출처 (D1 — Pretendard Std 1.3.9, 배포 원본 OTF 그대로) */
@Immutable
data class FontCredit(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val license: String,
    /** assets 안 라이선스 전문 (네트워크 없이 앱 안에서 보인다) */
    val licenseAsset: String,
    val sourceUrl: String,
)

/** 앱에 번들한 글꼴 (res/font, Theme.kt Pretendard) */
val BundledFonts: List<FontCredit> = listOf(
    FontCredit(
        id = "pretendard",
        name = "Pretendard Std",
        version = "1.3.9",
        author = "Kil Hyung-jin",
        license = "SIL OFL 1.1",
        licenseAsset = "licenses/pretendard_std_OFL.txt",
        sourceUrl = "https://github.com/orioncactus/pretendard",
    ),
)

/** 설정 › 사진·글꼴 출처: 자유 라이선스 사진의 찍은 사람·라이선스·원본(CC BY-SA 출처 표기) + 번들 글꼴과 라이선스 전문 */
@Composable
fun PhotoCreditsScreen(onOpenLink: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val credits = remember { loadPhotoCredits(context) }
    val open: (String) -> Unit = onOpenLink ?: { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    PhotoCreditsContent(credits, open)
}

/**
 * 28 사진·글꼴 출처 (DESIGN_SPEC 6-28): 사진마다 72dp 썸네일(장식) + 제목·찍은 사람·라이선스 태그 + 원본 링크,
 * 글꼴은 견본 + 이름·만든 사람·라이선스 + 라이선스 전문 펼침(assets, 네트워크 없음).
 * 글자 150%↑는 썸네일·견본을 글 위로 올려 글에 폭 전체를 준다.
 */
@Composable
fun PhotoCreditsContent(credits: List<PhotoCredit>, onOpenLink: (String) -> Unit, fonts: List<FontCredit> = BundledFonts) {
    AppScreen(
        title = stringResource(R.string.settings_credits),
        speech = stringResource(R.string.credits_speech),
    ) {
        item(key = "photos-title") {
            SectionHeader(
                stringResource(R.string.credits_photos_title),
                icon = Icons.Outlined.PhotoLibrary,
                subtitle = stringResource(R.string.photo_credits_body_c),
            )
        }
        credits.forEach { c -> item(key = "credit-${c.id}") { PhotoCreditCard(c, onOpenLink) } }
        if (fonts.isNotEmpty()) {
            sectionGap("fonts-gap")
            item(key = "fonts-title") {
                SectionHeader(stringResource(R.string.credits_fonts_title), icon = Icons.Outlined.TextFields)
            }
            fonts.forEach { f -> item(key = "font-${f.id}") { FontCreditCard(f, onOpenLink) } }
        }
        sectionGap("data-gap")
        item(key = "data-title") {
            SectionHeader(stringResource(R.string.credits_data_title), icon = Icons.Outlined.PhotoLibrary)
        }
        item(key = "data-credits") {
            // 출처가 30곳 넘어 접어 둔다(쉬운 모드에서 화면이 지나치게 길어지지 않게). 펼치면 전부 보인다
            CreditCard {
                KoText(stringResource(R.string.credits_data_body), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                ExpandableDetail(
                    label = stringResource(R.string.credits_data_expand, AttractionDataCredits.size),
                    target = stringResource(R.string.credits_data_title),
                ) {
                    AttractionDataCredits.forEach { id -> KoText(stringResource(id), MaterialTheme.typography.bodyMedium, color = Tokens.Ink) }
                }
            }
        }
    }
}

/** 관광지 데이터 가운데 출처 표시가 조건인 것 (packs 의 sources[].attribution_required — settings_credit_<출처 id>) */
private val AttractionDataCredits = listOf(
    R.string.settings_credit_bunka_osakajo,
    R.string.settings_credit_bunka_narapark,
    R.string.settings_credit_unesco_870,
    R.string.settings_credit_osm,
    R.string.settings_credit_unesco_mow_watpho,
    R.string.settings_credit_unesco_576,
    R.string.settings_credit_cnx_unesco_tl,
    R.string.settings_credit_unesco_1483,
    R.string.settings_credit_unesco_1223,
    R.string.settings_credit_unesco_592,
    R.string.settings_credit_unesco_642,
    R.string.settings_credit_unesco_1671,
    R.string.settings_credit_boch_npm,
    R.string.settings_credit_boch_cksmh,
    R.string.settings_credit_boch_longshan,
    R.string.settings_credit_boch_ximending_redhouse,
    R.string.settings_credit_boch_fort_san_domingo,
    R.string.settings_credit_boch_beitou_bathhouse,
    R.string.settings_credit_boch_gold_taizi,
    R.string.settings_credit_boch_chihkan,
    R.string.settings_credit_boch_anping,
    R.string.settings_credit_boch_tainan_confucius,
    R.string.settings_credit_boch_eternal_golden_castle,
    R.string.settings_credit_boch_cihou_fort,
    R.string.settings_credit_unesco_439,
    R.string.settings_credit_unesco_881,
    R.string.settings_credit_unesco_880,
    R.string.settings_credit_unesco_438,
    R.string.settings_credit_unesco_813,
    R.string.settings_credit_unesco_677,
    R.string.settings_credit_unesco_bohol_geopark,
    R.string.settings_credit_unesco_948,
    R.string.settings_credit_unesco_949,
    R.string.settings_credit_dsvh_special,
    R.string.settings_credit_unesco_1328,
    R.string.settings_credit_unesco_672,
    R.string.settings_credit_unesco_1438,
    R.string.settings_credit_unesco_688,
    R.string.settings_credit_bunka_arashiyama,
    R.string.settings_credit_bunka_tsutenkaku,
    R.string.settings_credit_bunka_tokyotower,
    R.string.settings_credit_bunka_edojo,
    R.string.settings_credit_bunka_tnm_honkan,
    R.string.settings_credit_bunka_hyokeikan,
    R.string.settings_credit_bunka_tsurugaoka,
    R.string.settings_credit_bunka_daibutsuden,
    R.string.settings_credit_unesco_1418,
    R.string.settings_credit_bunka_fujisan,
    R.string.settings_credit_bunka_fujisan_wh,
    R.string.settings_credit_bunka_fujigoko,
    R.string.settings_credit_bunka_dazaifu_honden,
    R.string.settings_credit_bunka_ohori,
    R.string.settings_credit_bunka_beppu_jigoku,
    R.string.settings_credit_bunka_takegawara,
    R.string.settings_credit_bunka_tokeidai,
    R.string.settings_credit_bunka_moiwa,
)

@Composable
private fun CreditCard(content: @Composable () -> Unit) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.large
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().cardShadow(shape),
    ) {
        Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.inner)) { content() }
    }
}

private val ThumbSize = 72.dp

/** 썸네일·견본(고정 폭) + 글. 큰 글자 배치는 위아래로 — 72dp 옆 좁은 칸에서 `CC BY / 2.0`처럼 쪼개지지 않게 */
@Composable
private fun MediaAndTexts(media: @Composable () -> Unit, texts: @Composable () -> Unit) {
    if (isStackedLayout()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            media()
            texts()
        }
    } else {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            media()
            Box(Modifier.weight(1f)) { texts() }
        }
    }
}

/**
 * 찍은 사람·라이선스 줄: 이름(`Kil Hyung-jin`)은 한 덩어리로 줄을 바꾼다. TalkBack·테스트는 원문.
 * 라이선스는 누를 수 없는 정보라 채움 없는 [InfoChip](`CC BY 2.0 라이선스`) — AccentSoft 채움 알약은 누를 수 있는 선택 칩처럼 보였다
 * (재검토2 ②#12·④#9, 'AccentSoft 채움은 선택됨에만'). 누르는 것은 아래 `원본 보기`·`라이선스 보기` 링크 줄.
 */
@Composable
private fun CreditTexts(title: String, @androidx.annotation.StringRes authorRes: Int, author: String, license: String) {
    val authorLine = stringResource(authorRes, author)
    val authorShown = keepWords(stringResource(authorRes, keepTogether(author)))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 띄어쓰기 없는 긴 파일 이름은 하이픈·밑줄 뒤에서 줄을 바꾼다
        KoText(title, MaterialTheme.typography.titleMedium, color = Tokens.Ink, display = keepWords(breakAfter(title, "-_")))
        KoText(authorLine, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary, display = authorShown)
        InfoChip(stringResource(R.string.credit_license_label), Icons.Outlined.Copyright, value = license)
    }
}

/** TalkBack 이름에 쓸 사진·글꼴 이름 — 파일 확장자(`.jpg`)는 뺀다 (`Wat Arun Sunset 원본 보기`) */
internal fun creditName(title: String): String =
    title.substringBeforeLast('.').takeIf { it.isNotBlank() && title.substringAfterLast('.').lowercase() in PhotoExtensions } ?: title

private val PhotoExtensions = setOf("jpg", "jpeg", "png", "webp")

@Composable
private fun PhotoCreditCard(c: PhotoCredit, onOpenLink: (String) -> Unit) {
    CreditCard {
        MediaAndTexts(
            media = {
                // 썸네일은 장식 — 옆 제목과 이중으로 읽히지 않게 설명 없음
                val thumb = rememberThumbnail(Photos.byId(c.id), ThumbSize)
                Box(
                    Modifier.size(ThumbSize).clip(MaterialTheme.shapes.small).background(Tokens.SurfaceSunken),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumb != null) {
                        Image(
                            thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = rememberPhotoLift(thumb),
                            modifier = Modifier.matchParentSize(),
                        )
                    } else {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = Tokens.InkSecondary)
                    }
                }
            },
            texts = { CreditTexts(c.title, R.string.photo_credit_author, c.author, c.license) },
        )
        // 같은 글자 `원본 보기`가 사진마다 되풀이돼 TalkBack에서 구분되지 않던 것 — 이름은 `{사진 이름} 원본 보기` (재검토2 ②#12)
        val name = creditName(c.title)
        val openName = stringResource(R.string.photo_credit_open_cd, name)
        val licenseName = stringResource(R.string.credit_license_open_cd, name)
        Column {
            LinkRow(
                stringResource(R.string.photo_credit_open),
                onClick = { onOpenLink(c.sourceUrl) },
                modifier = Modifier.semantics { contentDescription = openName },
            )
            if (c.licenseUrl.isNotBlank()) {
                LinkRow(
                    stringResource(R.string.credit_license_open),
                    onClick = { onOpenLink(c.licenseUrl) },
                    modifier = Modifier.semantics { contentDescription = licenseName },
                )
            }
        }
    }
}

@Composable
private fun FontCreditCard(f: FontCredit, onOpenLink: (String) -> Unit) {
    CreditCard {
        MediaAndTexts(
            media = { FontSpecimen() },
            texts = { CreditTexts("${f.name} ${f.version}", R.string.credit_font_author, f.author, f.license) },
        )
        KoText(stringResource(R.string.credit_font_note), MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
        val openName = stringResource(R.string.photo_credit_open_cd, f.name)
        LinkRow(
            stringResource(R.string.photo_credit_open),
            onClick = { onOpenLink(f.sourceUrl) },
            modifier = Modifier.semantics { contentDescription = openName },
        )
        // 라이선스 전문: 앱에 든 파일을 그 자리에서 펼친다(새 화면·네트워크 없음). 화면 목록과 함께 세로로 스크롤된다
        LicenseToggle(stringResource(R.string.credit_license_full)) {
            val context = LocalContext.current
            val text = remember(f.licenseAsset) { loadLicenseText(context, f.licenseAsset)?.let(::reflowLicense) }
            Surface(color = Tokens.SurfaceSunken, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text ?: stringResource(R.string.credit_license_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = Tokens.Ink,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/**
 * 라이선스 전문 펼침 — components.ExpandableDetail과 같은 모양·동작(Role.Button 토글, 펼침/접힘 상태, 펼치면 `접기`)에
 * 라벨 줄바꿈만 보정(`라이선스 전문 보/기` 방지).
 */
@Composable
private fun LicenseToggle(label: String, content: @Composable () -> Unit) {
    val dimens = LocalDimens.current
    var open by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (open) R.string.state_expanded else R.string.state_collapsed)
    val shown = if (open) stringResource(R.string.action_less) else label
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .minTouch()
                .clip(MaterialTheme.shapes.small)
                .toggleable(value = open, role = Role.Button, onValueChange = { open = it })
                .semantics { stateDescription = state }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KoText(shown, MaterialTheme.typography.labelLarge, Modifier.weight(1f), color = Tokens.Accent)
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = Tokens.Accent,
                modifier = Modifier.size(dimens.icon),
            )
        }
        AnimatedVisibility(visible = open) {
            Box(Modifier.padding(top = dimens.inner)) { content() }
        }
    }
}

/**
 * 글꼴 견본 '가 Aa' — 장식. 글자를 품으므로 고정 크기 대신 최소 크기(72dp)만 두고 글자가 크면 함께 커진다.
 * 바탕은 사진 썸네일 자리와 같은 SurfaceSunken — AccentSoft 채움은 '선택됨'에만(부록 D.1).
 */
@Composable
private fun FontSpecimen() {
    Column(
        Modifier
            .sizeIn(minWidth = ThumbSize, minHeight = ThumbSize)
            .clip(MaterialTheme.shapes.small)
            .background(Tokens.SurfaceSunken)
            .padding(8.dp)
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(PREVIEW_GLYPH, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Tokens.AccentDeep)
        Text(SPECIMEN_LATIN, style = MaterialTheme.typography.labelMedium, color = Tokens.Accent)
    }
}

private const val SPECIMEN_LATIN = "Aa"

/** assets 안 라이선스 글. 못 읽으면 null */
internal fun loadLicenseText(context: Context, path: String): String? =
    runCatching { context.assets.open(path).use { it.readBytes().decodeToString() } }.getOrNull()

/**
 * 고정 폭(약 70자)으로 줄을 바꾼 라이선스 글을 폰 폭에 맞게 다시 흘린다 — 글자는 그대로, 줄바꿈만 바꾼다.
 * 빈 줄 = 문단 경계, 대문자 짧은 줄(PREAMBLE 등) = 제목 줄로 그대로 두고, `-----` 장식 줄은 뺀다.
 */
internal fun reflowLicense(text: String): String {
    val paragraphs = mutableListOf<String>()
    val current = StringBuilder()
    fun flush() {
        if (current.isNotEmpty()) {
            paragraphs += current.toString()
            current.clear()
        }
    }
    for (raw in text.replace("\r\n", "\n").lines()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> flush()
            line.all { it == '-' } -> flush()
            // 문단 첫 줄이면서 짧은 대문자 줄(마침표·쉼표로 끝나지 않음) = 제목
            current.isEmpty() && line.length <= 40 && line.any { it.isLetter() } && line == line.uppercase() &&
                line.last() != '.' && line.last() != ',' -> {
                flush()
                paragraphs += line
            }
            else -> {
                if (current.isNotEmpty()) current.append(' ')
                current.append(line)
            }
        }
    }
    flush()
    return paragraphs.joinToString("\n\n")
}

/** 개인정보 처리방침 (Firebase Hosting, readyport-app) */
const val PRIVACY_URL = "https://readyport-app.web.app/privacy/"
