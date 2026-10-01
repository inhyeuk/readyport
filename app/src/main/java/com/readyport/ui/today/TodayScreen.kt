package com.readyport.ui.today

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.LocalAirport
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.pack.ShoppingItem
import com.readyport.prep.ImportStatus
import com.readyport.prep.import
import com.readyport.trip.Trip
import com.readyport.trip.TripStage
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.ButtonStyles
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.Fact
import com.readyport.ui.components.FactGrid
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.ImportVerdictBadge
import com.readyport.ui.components.InfoTileGrid
import com.readyport.ui.components.JourneyStepper
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.NewsStyle
import com.readyport.ui.components.PhotoHeaderCard
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.ReturnCheckCard
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.Step
import com.readyport.ui.components.StepList
import com.readyport.ui.components.TileSpec
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.resolveSourceName
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.wallet.rememberDeviceAuth
import com.readyport.vault.WalletRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 여행 단계 6칸 이름 (PRD 5.1) — JourneyStepper 설명·말하기에 쓴다 */
private val StageLabels = listOf(
    R.string.stage_prepare, R.string.stage_departure, R.string.stage_arrival,
    R.string.stage_traveling, R.string.stage_return, R.string.stage_wrapup,
)

/** '오늘' 화면에서 다른 곳으로 가는 길 */
data class TodayActions(
    val makeTrip: () -> Unit = {},
    val editTrip: () -> Unit = {},
    val explore: () -> Unit = {},
    val prepare: () -> Unit = {},
    val openForm: (String) -> Unit = {},
    val registerPassport: () -> Unit = {},
    val present: () -> Unit = {},
    val help: () -> Unit = {},
    val goStay: () -> Unit = {},
    val expense: () -> Unit = {},
    /** 공식 안내 링크(관세청·검역본부)를 브라우저로 연다 */
    val openLink: (String) -> Unit = {},
)

@Composable
fun TodayScreen(actions: TodayActions, viewModel: TodayViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val auth = rememberDeviceAuth()
    val context = LocalContext.current
    TodayContent(
        ui = ui,
        actions = actions.copy(openLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }),
        onArrived = viewModel::markArrived,
        onArrivalDone = viewModel::dismissArrival,
        onDestroy = {
            scope.launch {
                if (viewModel.destroyPassportInfo() != WalletRepository.SaveResult.Saved) {
                    auth { scope.launch { viewModel.destroyPassportInfo() } }
                }
            }
        },
        onPostpone = viewModel::postponeDestroy,
        onNewTrip = { viewModel.newTrip(); actions.makeTrip() },
    )
}

/**
 * 내 여행 (DESIGN_SPEC 6-09~13, Departure·WrapUp).
 * 위에서부터: 여행 6단계(JourneyStepper) → 지금 할 일 카드(Accent, 한 화면 할 일 하나) → 단계별 카드뉴스 →
 * 급할 때는 도움(행 전체가 버튼) → 여행 고치기(글자 버튼, 맨 아래).
 */
@Composable
fun TodayContent(
    ui: TodayUi,
    actions: TodayActions,
    onArrived: () -> Unit,
    onArrivalDone: () -> Unit,
    onDestroy: () -> Unit,
    onPostpone: () -> Unit,
    onNewTrip: () -> Unit,
) {
    val stage = ui.stage
    val country = ui.countryName.orEmpty()
    val title = when (stage.stage) {
        TripStage.NoTrip -> stringResource(R.string.today_title)
        TripStage.Traveling, TripStage.Arrival -> stringResource(R.string.today_day_n, country, (stage.dayOfTrip ?: 1).toInt())
        else -> stringResource(R.string.today_trip_title, country)
    }
    // 부제가 '내 여행'이면 제목과 겹치므로 여행 날짜(11월 3일 ~ 7일)를 보인다 (6-09~13 공통)
    val dates = tripDates(ui.trip)
    val subtitle = when (stage.stage) {
        TripStage.NoTrip -> stringResource(R.string.today_no_trip)
        TripStage.Preparing -> stringResource(R.string.today_d_day, (stage.daysLeft ?: 0).toInt())
        else -> dates ?: stringResource(R.string.today_title)
    }
    val labels = StageLabels.map { stringResource(it) }
    val now = stage.stage.barIndex
    val stageName = labels[now]
    val stageDescription = stringResource(R.string.today_stage_desc, stageName, now + 1, labels.size)
    val nextLabel = stringResource(R.string.today_next_label)
    val tripFacts = tripFacts(ui.trip)

    AppScreen(
        title = title,
        subtitle = subtitle,
        speech = stringResource(R.string.today_speech_trip, title, stageName),
    ) {
        item(key = "stages") {
            JourneyStepper(current = now, description = stageDescription, preview = stage.stage == TripStage.NoTrip)
        }

        when (stage.stage) {
            TripStage.NoTrip -> {
                item(key = "next") {
                    // 320×470 화면 예산(6장 머리말): 폭 340dp 미만이면 eyebrow·설명을 줄여 '나라 사진 보며 고르기'까지 첫 화면에 둔다
                    val narrow = isNarrowWindow()
                    NextCard(
                        icon = Icons.Outlined.EditCalendar,
                        eyebrow = if (narrow) null else nextLabel,
                        title = stringResource(R.string.today_next_title),
                        body = if (narrow) null else stringResource(R.string.today_next_body),
                        button = stringResource(R.string.today_make_trip),
                        buttonIcon = Icons.Outlined.EditCalendar,
                        onClick = actions.makeTrip,
                    )
                }
                item(key = "explore") {
                    SecondaryButton(
                        text = stringResource(R.string.today_next_button),
                        onClick = actions.explore,
                        icon = Icons.Outlined.TravelExplore,
                    )
                }
            }
            TripStage.Preparing -> {
                item(key = "next") {
                    // 한 화면에 할 일 하나 (PRD 1.1): 입국 카드 > 여권 > 준비물
                    when {
                        stage.formWindowOpen && ui.form != null && ui.hasPassport != false -> NextCard(
                            icon = Icons.Outlined.AssignmentInd,
                            eyebrow = nextLabel,
                            title = stringResource(R.string.today_task_form_title, ui.form.nameKo),
                            body = stringResource(R.string.today_task_form_body),
                            button = stringResource(R.string.prepare_form_open),
                            buttonIcon = Icons.Outlined.EditNote,
                            onClick = { actions.openForm(ui.form.id) },
                        )
                        ui.hasPassport == false -> NextCard(
                            icon = Icons.Outlined.Badge,
                            eyebrow = nextLabel,
                            title = stringResource(R.string.today_task_passport_title),
                            body = stringResource(R.string.today_task_passport_body),
                            button = stringResource(R.string.wallet_passport_add),
                            buttonIcon = Icons.Outlined.Badge,
                            onClick = actions.registerPassport,
                        )
                        else -> NextCard(
                            icon = Icons.Outlined.TaskAlt,
                            eyebrow = nextLabel,
                            title = stringResource(R.string.today_task_ready_title),
                            body = stringResource(R.string.today_task_ready_body),
                            button = stringResource(R.string.today_open_prepare),
                            buttonIcon = Icons.Outlined.Checklist,
                            onClick = actions.prepare,
                        )
                    }
                }
                // 여행 요약 숫자 타일(떠나는 날·여행 기간) — 사용자가 적은 일정에서만 만든다(누를 수 없음, 할 일 아님)
                if (tripFacts.size >= 2) item(key = "facts") { FactGrid(tripFacts) }
            }
            TripStage.Departure -> {
                if (stage.formWindowOpen && ui.form != null) {
                    item(key = "form") {
                        NextCard(
                            icon = Icons.Outlined.AssignmentInd,
                            eyebrow = nextLabel,
                            title = stringResource(R.string.today_task_form_title, ui.form.nameKo),
                            body = stringResource(R.string.today_task_form_body),
                            button = stringResource(R.string.prepare_form_open),
                            buttonIcon = Icons.Outlined.EditNote,
                            onClick = { actions.openForm(ui.form.id) },
                        )
                    }
                }
                item(key = "departure") {
                    // 섹션 표지 사진(공항 = 출국 순서, DESIGN_SPEC 3.7 ①) + 아이콘 단계 목록
                    PhotoHeaderCard(
                        photo = Photos.Airport,
                        title = stringResource(R.string.today_departure_steps_title),
                        icon = Icons.Outlined.FlightTakeoff,
                    ) {
                        StepList(
                            listOf(
                                Step(stringResource(R.string.today_departure_step1), Icons.Outlined.LocalAirport, stringResource(R.string.today_departure_step1_detail)),
                                Step(stringResource(R.string.today_departure_step2), Icons.Outlined.Luggage),
                                Step(stringResource(R.string.today_departure_step3), Icons.Outlined.Security),
                                Step(stringResource(R.string.today_departure_step4), Icons.Outlined.HowToReg),
                                Step(stringResource(R.string.today_departure_step5), Icons.Outlined.MeetingRoom),
                            ),
                        )
                    }
                }
                item(key = "arrived") {
                    PrimaryButton(stringResource(R.string.today_arrived_button), onClick = onArrived, icon = Icons.Outlined.FlightLand)
                }
            }
            TripStage.Arrival -> {
                item(key = "qr") {
                    NextCard(
                        icon = Icons.Outlined.QrCode2,
                        eyebrow = nextLabel,
                        title = stringResource(R.string.today_arrival_qr_title),
                        body = null,
                        button = stringResource(R.string.today_show_qr),
                        buttonIcon = Icons.Outlined.QrCode2,
                        onClick = actions.present,
                    )
                }
                item(key = "arrival") {
                    CardNewsCard(title = stringResource(R.string.today_arrival_title), icon = Icons.Outlined.FlightLand) {
                        StepList(
                            listOf(
                                Step(stringResource(R.string.today_arrival_step1), Icons.Outlined.HowToReg),
                                Step(stringResource(R.string.today_arrival_step2), Icons.Outlined.Luggage),
                                Step(stringResource(R.string.today_arrival_step3), Icons.Outlined.SimCard),
                                Step(stringResource(R.string.today_arrival_step4), Icons.Outlined.CurrencyExchange),
                                Step(stringResource(R.string.today_arrival_step5), Icons.Outlined.Hotel),
                            ),
                        )
                        // 체크 저장 없이 '다 했어요'만 (D10)
                        SecondaryButton(
                            text = stringResource(R.string.today_arrival_done),
                            onClick = onArrivalDone,
                            icon = Icons.Outlined.TaskAlt,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            TripStage.Traveling -> item(key = "grid") {
                // 2×2 큰 타일 (PRD 5.1). 숙소로 돌아가기만 Navy 강조. 쉬운 모드·큰 글자는 1열 가로형(D4)
                InfoTileGrid(
                    listOf(
                        TileSpec(stringResource(R.string.today_go_stay), Icons.Outlined.Hotel, actions.goStay, emphasized = true),
                        TileSpec(stringResource(R.string.today_phrases), Icons.Outlined.Translate, actions.help, tone = BadgeTone.Help),
                        TileSpec(stringResource(R.string.today_show_qr), Icons.Outlined.QrCode2, actions.present),
                        TileSpec(stringResource(R.string.today_expense), Icons.AutoMirrored.Outlined.ReceiptLong, actions.expense),
                    ),
                )
            }
            TripStage.Return -> {
                item(key = "return") {
                    CardNewsCard(
                        title = stringResource(R.string.today_return_title),
                        icon = Icons.Outlined.Cottage,
                        body = stringResource(R.string.today_return_customs),
                    )
                }
                // 담아 둔 쇼핑 목록의 반입 가능 여부를 다시 확인 (PRD 11.3) — 불가 → 주의 → 가능 순, 판정 출처를 카드 맨 아래에
                if (ui.cart.isNotEmpty()) {
                    item(key = "cart") { CartCard(ui) }
                }
                item(key = "return-links") { ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, actions.openLink) }
                if (stage.askDestroy) {
                    item(key = "destroy") {
                        // 카드 자체가 확인 단계라 대화상자 없음(D8). 7일 미루기가 먼저(위), 지우기는 빨간 테두리 버튼
                        CardNewsCard(
                            title = stringResource(R.string.today_destroy_title),
                            icon = Icons.Outlined.Lock,
                            tone = BadgeTone.Neutral,
                            body = stringResource(R.string.today_destroy_body),
                        ) {
                            Column(
                                Modifier.padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner),
                            ) {
                                SecondaryButton(stringResource(R.string.today_destroy_later), onClick = onPostpone, icon = Icons.Outlined.Schedule)
                                DangerButton(stringResource(R.string.today_destroy_now), onClick = onDestroy, fillWidth = true)
                            }
                        }
                    }
                }
            }
            TripStage.WrapUp -> item(key = "wrap") {
                NextCard(
                    icon = Icons.Outlined.TaskAlt,
                    eyebrow = stringResource(R.string.stage_wrapup),
                    title = stringResource(R.string.today_wrapup_title),
                    body = null,
                    button = stringResource(R.string.today_new_trip),
                    buttonIcon = Icons.Outlined.EditCalendar,
                    onClick = onNewTrip,
                )
            }
        }

        // 급할 때는 도움 — "누르세요"라고 쓰고 누를 수 없던 카드를 행 전체가 눌리는 줄로 (6-09~13 공통)
        item(key = "help") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.help_shortcut_title),
                    icon = Icons.Outlined.Sos,
                    tone = BadgeTone.Help,
                    body = stringResource(R.string.today_help_body),
                    onClick = actions.help,
                )
            }
        }
        if (stage.stage != TripStage.NoTrip && stage.stage != TripStage.WrapUp) {
            item(key = "edit") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    QuietButton(stringResource(R.string.trip_edit_title), onClick = actions.editTrip, icon = Icons.Outlined.EditCalendar)
                }
            }
        }
    }
}

/**
 * 지금 할 일 카드 (Accent 채움, onDark 내용 세트만): 아이콘 배지 + eyebrow(지금 할 일) + 제목 + 설명 + 흰 주 버튼.
 */
@Composable
private fun NextCard(
    icon: ImageVector,
    eyebrow: String?,
    title: String,
    body: String?,
    button: String,
    buttonIcon: ImageVector?,
    onClick: () -> Unit,
) {
    CardNewsCard(title = title, icon = icon, eyebrow = eyebrow, body = body, style = NewsStyle.Accent) {
        PrimaryButton(
            text = button,
            onClick = onClick,
            icon = buttonIcon,
            colors = ButtonStyles.onDark(Tokens.Accent),
        )
    }
}

/** 320×470 화면 예산을 지켜야 하는 좁은 창 (폭 340dp 미만, DESIGN_SPEC 6장 머리말) */
@Composable
private fun isNarrowWindow(): Boolean {
    val width = LocalWindowInfo.current.containerSize.width / LocalDensity.current.density
    return width > 0f && width < 340f
}

/** 반입 판정 순서: 불가 → 주의 → 가능 (6-13) */
private fun importOrder(status: ImportStatus): Int = when (status) {
    ImportStatus.Prohibited -> 0
    ImportStatus.Caution -> 1
    ImportStatus.Allowed -> 2
}

/**
 * 담아 둔 물건 카드 (6-13): 행마다 분류 아이콘 + 이름 + 반입 판정 배지 + 판정 설명.
 * 카드 맨 아래 SourceList = 품목 출처 + 반입 판정 출처(importSource) — 판정과 설명이 출처 없이 보이지 않게.
 * 출처 이름을 못 찾으면 `공식 안내`(내부 ID를 보이지 않는다).
 */
@Composable
private fun CartCard(ui: TodayUi) {
    val fallback = stringResource(R.string.source_official_fallback)
    val names = ui.indexSources + ui.sourceNames
    val items = ui.cart.sortedBy { importOrder(it.import) }
    val refs = items.flatMap { item ->
        listOf(item.source, item.importSource).map { id -> SourceRef(resolveSourceName(id, names, fallback), displayDate(item.lastVerified)) }
    }
    CardNewsCard(
        title = stringResource(R.string.today_cart_title),
        icon = Icons.Outlined.ShoppingBag,
        tone = BadgeTone.Help,
        sources = refs,
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = Tokens.Line)
            CartRow(item)
        }
    }
}

@Composable
private fun CartRow(item: ShoppingItem) {
    val dimens = LocalDimens.current
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(IconKeys.shoppingCategory(item.category), tone = BadgeTone.Neutral, size = dimens.iconBadgeSmall)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.names.ko, style = MaterialTheme.typography.titleMedium, color = Tokens.Ink)
            ImportVerdictBadge(item.import)
            item.importNoteKo?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary) }
        }
    }
}

private fun parseDate(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

/** 여행 날짜 한 줄: 11월 3일 ~ 7일 (달이 바뀌면 11월 30일 ~ 12월 2일). 날짜가 없거나 틀리면 null */
@Composable
private fun tripDates(trip: Trip?): String? {
    val start = parseDate(trip?.startDate) ?: return null
    val end = parseDate(trip?.endDate) ?: return null
    val from = stringResource(R.string.today_date_md, start.monthValue, start.dayOfMonth)
    val to = if (start.year == end.year && start.month == end.month) {
        stringResource(R.string.today_date_d, end.dayOfMonth)
    } else {
        stringResource(R.string.today_date_md, end.monthValue, end.dayOfMonth)
    }
    return stringResource(R.string.today_trip_dates, from, to)
}

/** 여행 요약 타일: 떠나는 날(11월 3일 · 화요일에 떠나요) · 여행 기간(4박 5일). 사용자가 적은 일정에서만 만든다 */
@Composable
private fun tripFacts(trip: Trip?): List<Fact> {
    val start = parseDate(trip?.startDate) ?: return emptyList()
    val end = parseDate(trip?.endDate) ?: return emptyList()
    val nights = ChronoUnit.DAYS.between(start, end).toInt()
    if (nights < 0) return emptyList()
    val weekday = start.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    return listOf(
        Fact(
            icon = Icons.Outlined.FlightTakeoff,
            value = stringResource(R.string.today_date_md, start.monthValue, start.dayOfMonth),
            label = stringResource(R.string.trip_start_weekday, weekday),
        ),
        Fact(
            icon = Icons.Outlined.DateRange,
            value = stringResource(R.string.trip_nights, nights, nights + 1),
            label = stringResource(R.string.trip_length_label),
        ),
    )
}
