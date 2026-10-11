package com.readyport.ui.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.board.BoardAge
import com.readyport.plan.BudgetBand
import com.readyport.plan.PlanError
import com.readyport.plan.PlanFailure
import com.readyport.plan.PlanMobility
import com.readyport.plan.PlanPurpose
import com.readyport.plan.PlanStatus
import com.readyport.plan.TimeHint
import com.readyport.ui.board.BoardCard
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.TileSpec
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 여행 계획 요청 — 공통 조각 =======================

@StringRes
fun PlanPurpose.labelRes(): Int = when (this) {
    PlanPurpose.Sightseeing -> R.string.plan_purpose_sightseeing
    PlanPurpose.Food -> R.string.plan_purpose_food
    PlanPurpose.Shopping -> R.string.plan_purpose_shopping
    PlanPurpose.Nature -> R.string.plan_purpose_nature
    PlanPurpose.HistoryCulture -> R.string.plan_purpose_history_culture
    PlanPurpose.Relaxation -> R.string.plan_purpose_relaxation
    PlanPurpose.KidsFamily -> R.string.plan_purpose_kids_family
    PlanPurpose.Activity -> R.string.plan_purpose_activity
    PlanPurpose.Other -> R.string.plan_purpose_other
}

@StringRes
fun PlanMobility.labelRes(): Int = when (this) {
    PlanMobility.LongWalkHard -> R.string.plan_mobility_long_walk_hard
    PlanMobility.Wheelchair -> R.string.plan_mobility_wheelchair
    PlanMobility.StairsHard -> R.string.plan_mobility_stairs_hard
    PlanMobility.WithInfant -> R.string.plan_mobility_with_infant
}

@StringRes
fun BudgetBand.labelRes(): Int = when (this) {
    BudgetBand.Budget -> R.string.plan_budget_budget
    BudgetBand.Standard -> R.string.plan_budget_standard
    BudgetBand.Comfort -> R.string.plan_budget_comfort
    BudgetBand.Premium -> R.string.plan_budget_premium
}

@StringRes
fun BudgetBand.descRes(): Int = when (this) {
    BudgetBand.Budget -> R.string.plan_budget_budget_desc
    BudgetBand.Standard -> R.string.plan_budget_standard_desc
    BudgetBand.Comfort -> R.string.plan_budget_comfort_desc
    BudgetBand.Premium -> R.string.plan_budget_premium_desc
}

@StringRes
fun TimeHint.labelRes(): Int = when (this) {
    TimeHint.Morning -> R.string.plan_time_morning
    TimeHint.LateMorning -> R.string.plan_time_late_morning
    TimeHint.Lunch -> R.string.plan_time_lunch
    TimeHint.Afternoon -> R.string.plan_time_afternoon
    TimeHint.Evening -> R.string.plan_time_evening
    TimeHint.Night -> R.string.plan_time_night
}

@StringRes
fun PlanStatus.labelRes(): Int = when (this) {
    PlanStatus.Queued -> R.string.plan_status_queued
    PlanStatus.Processing -> R.string.plan_status_processing
    PlanStatus.Done -> R.string.plan_status_done
    PlanStatus.Failed -> R.string.plan_status_failed
    PlanStatus.Cancelled -> R.string.plan_status_cancelled
    PlanStatus.Unknown -> R.string.plan_status_unknown
}

/** 상태 = 색 + 아이콘 + 글자 (색만으로 전하지 않는다) */
fun PlanStatus.kind(): StatusKind = when (this) {
    PlanStatus.Queued, PlanStatus.Cancelled -> StatusKind.Soon
    PlanStatus.Processing, PlanStatus.Unknown -> StatusKind.Info
    PlanStatus.Done -> StatusKind.Allowed
    PlanStatus.Failed -> StatusKind.Prohibited
}

fun PlanStatus.icon() = when (this) {
    PlanStatus.Queued -> Icons.Outlined.Schedule
    PlanStatus.Processing -> Icons.Outlined.HourglassTop
    PlanStatus.Done -> Icons.Outlined.CheckCircle
    PlanStatus.Failed -> Icons.Outlined.ErrorOutline
    PlanStatus.Cancelled -> Icons.Outlined.Block
    PlanStatus.Unknown -> Icons.AutoMirrored.Outlined.HelpOutline
}

@Composable
fun PlanStatusTag(status: PlanStatus, modifier: Modifier = Modifier) {
    StatusTag(stringResource(status.labelRes()), status.kind(), modifier, icon = status.icon())
}

@StringRes
fun PlanFailure?.textRes(): Int = when (this) {
    PlanFailure.QuotaExceeded -> R.string.plan_fail_quota
    PlanFailure.InvalidRequest -> R.string.plan_fail_invalid
    PlanFailure.CountryUnavailable -> R.string.plan_fail_country
    else -> R.string.plan_fail_engine
}

/** 실패 → 쉬운 문구 (횟수를 다 쓴 경우는 화면이 날짜를 넣어 따로 보인다) */
@StringRes
fun Throwable.planErrorRes(): Int = when (this) {
    PlanError.Offline -> R.string.plan_err_offline
    PlanError.AuthUnavailable -> R.string.plan_err_auth
    PlanError.NotFound -> R.string.plan_err_not_found
    is PlanError.AgeRestricted -> R.string.plan_err_age
    PlanError.AgeCheckNeeded -> R.string.plan_err_age_check
    PlanError.Invalid -> R.string.plan_err_invalid
    is PlanError.QuotaUsed -> R.string.plan_quota_used_nodate
    else -> R.string.plan_err_denied
}

/**
 * 게시판 탭 맨 위 카드: `여행 계획 요청` + `나만 보는 비공개 요청` + 한 줄 설명 + 계획 요청하기(보조 버튼 — 화면의 주 버튼은 글쓰기) · 내 계획 요청 보기.
 * 미성년이면 버튼 없이 안내만(읽기 전용).
 */
@Composable
fun PlanEntryCard(age: BoardAge.Status, onRequest: () -> Unit, onMine: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    BoardCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.inner + 4.dp)) {
            val texts: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KoText(stringResource(R.string.plan_entry_title), MaterialTheme.typography.titleMedium, color = Tokens.Ink, heading = true)
                    StatusTag(stringResource(R.string.plan_entry_badge), StatusKind.Info, icon = Icons.Outlined.Lock)
                    KoText(stringResource(R.string.plan_entry_body), MaterialTheme.typography.bodyLarge, color = Tokens.InkSecondary)
                }
            }
            if (isStackedLayout()) {
                texts()
            } else {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconBadge(Icons.Outlined.AutoAwesome, tone = BadgeTone.Violet)
                    Box(Modifier.weight(1f)) { texts() }
                }
            }
            if (age is BoardAge.Status.Minor) {
                IconBullet(stringResource(R.string.plan_entry_minor), Icons.Outlined.ChildCare, tone = BadgeTone.Caution)
            } else {
                // 게시판 화면의 주 버튼은 글쓰기 하나 — 이 카드는 보조 버튼 + 글자 버튼
                SecondaryButton(stringResource(R.string.plan_entry_request), onClick = onRequest, icon = Icons.Outlined.TravelExplore, tone = BadgeTone.Violet)
                QuietButton(stringResource(R.string.plan_entry_mine), onClick = onMine, icon = Icons.AutoMirrored.Outlined.EventNote)
            }
        }
    }
}

/**
 * 나라 화면 '여행 정보' 맨 위 AI 일정 설계 카드 (2026-10-11 사장님 결정: 게시판이 아니라 여행지를 알아보는 곳 맨 위).
 * 횟수는 **1인 7일에 2번**(나라 합산)이고 눈에 띄게 적는다. 미성년 안내는 요청 화면이 한다(여기서는 나이를 읽지 않는다).
 */
@Composable
fun PlanCountryCard(countryName: String, onRequest: () -> Unit, onMine: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    BoardCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.inner + 4.dp)) {
            val texts: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KoText(stringResource(R.string.plan_country_title, countryName), MaterialTheme.typography.titleMedium, color = Tokens.Ink, heading = true)
                    StatusTag(stringResource(R.string.plan_entry_badge), StatusKind.Info, icon = Icons.Outlined.Lock)
                    KoText(stringResource(R.string.plan_country_body), MaterialTheme.typography.bodyLarge, color = Tokens.InkSecondary)
                }
            }
            if (isStackedLayout()) {
                texts()
            } else {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconBadge(Icons.Outlined.AutoAwesome, tone = BadgeTone.Violet)
                    Box(Modifier.weight(1f)) { texts() }
                }
            }
            IconBullet(stringResource(R.string.plan_country_limit), Icons.Outlined.Lock, tone = BadgeTone.Caution)
            SecondaryButton(stringResource(R.string.plan_entry_request), onClick = onRequest, icon = Icons.Outlined.TravelExplore, tone = BadgeTone.Violet)
            QuietButton(stringResource(R.string.plan_entry_mine), onClick = onMine, icon = Icons.AutoMirrored.Outlined.EventNote)
        }
    }
}

/** 여행 계획 단계 타일 (그림 모자이크 — 관광 일정 타일 옆) */
@Composable
fun planTile(onClick: () -> Unit): TileSpec = TileSpec(
    stringResource(R.string.plan_tile),
    Icons.Outlined.AutoAwesome,
    onClick,
    supporting = stringResource(R.string.plan_tile_body),
    tone = BadgeTone.Violet,
    illustration = com.readyport.ui.components.Illus.Plan,
)

/**
 * 인원 고르기 줄: 이름 · [−] n명 [+]. 큰 글자 배치에서는 이름을 위 줄에. 버튼은 48dp 이상, 이름을 말한다(`어른 한 명 더하기`).
 * 바뀐 숫자는 TalkBack이 알린다(liveRegion).
 */
@Composable
fun CountStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 20,
    /** 가운데 숫자 글 (기본 `n명`) */
    valueText: String? = null,
) {
    val style = MaterialTheme.typography.bodyLarge
    val controls: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { onChange(value - 1) }, enabled = value > min, modifier = Modifier.minTouchSize()) {
                Icon(
                    Icons.Outlined.RemoveCircleOutline,
                    contentDescription = stringResource(R.string.plan_count_minus, label),
                    tint = if (value > min) Tokens.Accent else Tokens.LineStrong,
                )
            }
            KoText(
                valueText ?: stringResource(R.string.plan_count_value, value),
                MaterialTheme.typography.titleMedium,
                Modifier.widthIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = Tokens.Ink,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onChange(value + 1) }, enabled = value < max, modifier = Modifier.minTouchSize()) {
                Icon(
                    Icons.Outlined.AddCircleOutline,
                    contentDescription = stringResource(R.string.plan_count_plus, label),
                    tint = if (value < max) Tokens.Accent else Tokens.LineStrong,
                )
            }
        }
    }
    if (isStackedLayout()) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KoText(label, style, color = Tokens.Ink)
            controls()
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KoText(label, style, Modifier.weight(1f), color = Tokens.Ink)
            controls()
        }
    }
}
