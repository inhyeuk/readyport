package com.readyport.ui.today

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.CurrencyExchange
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
 * 화면에 그리는 앱 글자는 keepWords()를 거친다(API 33 미만 낱말 안 줄바꿈 방지). 말하기·TalkBack 설명은 원문.
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
    // 글자 180% 이상: 도움 행의 설명은 배지·셰브론 사이 좁은 칸에서 5~6줄 글 벽이 되므로 제목(행 이름)만 둔다
    val largeText = LocalDensity.current.fontScale >= 1.8f

    AppScreen(
        title = keepWords(title),
        subtitle = keepWords(subtitle),
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
                        // 배지(EditCalendar)와 같은 아이콘을 버튼에 되풀이하지 않는다 — 버튼은 '다음 화면으로'
                        buttonIcon = Icons.AutoMirrored.Outlined.NavigateNext,
                        onClick = actions.makeTrip,
                    )
                }
                item(key = "explore") {
                    SecondaryButton(
                        text = keepWords(stringResource(R.string.today_next_button)),
                        onClick = actions.explore,
                        icon = Icons.Outlined.TravelExplore,
                    )
                }
            }
            TripStage.Preparing -> item(key = "next") {
                // 한 화면에 할 일 하나 (PRD 1.1): 입국 카드 > 여권 > 준비물. 요약 타일 그리드는 두지 않는다(6-10)
                when {
                    stage.formWindowOpen && ui.form != null && ui.hasPassport != false -> FormTaskCard(ui.form.nameKo) { actions.openForm(ui.form.id) }
                    ui.hasPassport == false -> NextCard(
                        icon = Icons.Outlined.Badge,
                        eyebrow = nextLabel,
                        title = stringResource(R.string.today_task_passport_title),
                        body = stringResource(R.string.today_task_passport_body),
                        button = stringResource(R.string.wallet_passport_add),
                        buttonIcon = Icons.AutoMirrored.Outlined.NavigateNext,
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
            TripStage.Departure -> {
                // 태국처럼 입국 카드 기간에 출국일이 들어 있으면 입국 카드가 지금 할 일(흰 주 버튼)이고 '도착했어요'는 보조 버튼 (원칙 7)
                val form = ui.form?.takeIf { stage.formWindowOpen }
                val formTask = form != null
                if (form != null) {
                    item(key = "form") { FormTaskCard(form.nameKo) { actions.openForm(form.id) } }
                }
                item(key = "departure") {
                    // 섹션 표지 사진(공항 = 출국 순서, DESIGN_SPEC 3.7 ①) + 아이콘 단계 목록
                    PhotoHeaderCard(
                        photo = Photos.Airport,
                        title = keepTitle(stringResource(R.string.today_departure_steps_title)),
                        icon = Icons.Outlined.FlightTakeoff,
                    ) {
                        StepList(
                            listOf(
                                step(stringResource(R.string.today_departure_step1), Icons.Outlined.LocalAirport, stringResource(R.string.today_departure_step1_detail)),
                                step(stringResource(R.string.today_departure_step2), Icons.Outlined.Luggage),
                                step(stringResource(R.string.today_departure_step3), Icons.Outlined.Security),
                                step(stringResource(R.string.today_departure_step4), Icons.Outlined.HowToReg),
                                step(stringResource(R.string.today_departure_step5), Icons.Outlined.MeetingRoom),
                            ),
                        )
                    }
                }
                item(key = "arrived") {
                    val label = keepWords(stringResource(R.string.today_arrived_button))
                    if (formTask) {
                        SecondaryButton(label, onClick = onArrived, icon = Icons.Outlined.FlightLand)
                    } else {
                        PrimaryButton(label, onClick = onArrived, icon = Icons.Outlined.FlightLand)
                    }
                }
            }
            TripStage.Arrival -> {
                item(key = "qr") {
                    NextCard(
                        // 배지 = 입국 심사(HowToReg), 버튼 = QR 보기(QrCode2) — 같은 아이콘을 두 번 쓰지 않는다
                        icon = Icons.Outlined.HowToReg,
                        eyebrow = nextLabel,
                        title = stringResource(R.string.today_arrival_qr_title),
                        body = null,
                        button = stringResource(R.string.today_show_qr),
                        buttonIcon = Icons.Outlined.QrCode2,
                        onClick = actions.present,
                    )
                }
                item(key = "arrival") {
                    CardNewsCard(title = keepTitle(stringResource(R.string.today_arrival_title)), icon = Icons.Outlined.FlightLand) {
                        StepList(
                            listOf(
                                step(stringResource(R.string.today_arrival_step1), Icons.Outlined.HowToReg),
                                step(stringResource(R.string.today_arrival_step2), Icons.Outlined.Luggage),
                                step(stringResource(R.string.today_arrival_step3), Icons.Outlined.SimCard),
                                step(stringResource(R.string.today_arrival_step4), Icons.Outlined.CurrencyExchange),
                                step(stringResource(R.string.today_arrival_step5), Icons.Outlined.Hotel),
                            ),
                        )
                        // 체크 저장 없이 '다 했어요'만 (D10)
                        SecondaryButton(
                            text = keepWords(stringResource(R.string.today_arrival_done)),
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
                        TileSpec(keepWords(stringResource(R.string.today_go_stay)), Icons.Outlined.Hotel, actions.goStay, emphasized = true),
                        TileSpec(keepWords(stringResource(R.string.today_phrases)), Icons.Outlined.Translate, actions.help, tone = BadgeTone.Help),
                        TileSpec(keepWords(stringResource(R.string.today_show_qr)), Icons.Outlined.QrCode2, actions.present),
                        TileSpec(keepWords(stringResource(R.string.today_expense)), Icons.AutoMirrored.Outlined.ReceiptLong, actions.expense),
                    ),
                )
            }
            TripStage.Return -> {
                item(key = "return") {
                    // 결론(여행이 끝났어요) → 담아 둔 물건 → 귀국 전 확인(규정) 순서. 안내 문장은 ReturnCheckCard가 한 번만 말한다
                    CardNewsCard(title = keepWords(stringResource(R.string.today_return_title)), icon = Icons.Outlined.Cottage)
                }
                // 담아 둔 쇼핑 목록의 반입 가능 여부를 다시 확인 (PRD 11.3) — 불가 → 주의 → 가능 순, 판정 출처를 카드 맨 아래에
                if (ui.cart.isNotEmpty()) {
                    item(key = "cart") { CartCard(ui) }
                }
                item(key = "return-links") {
                    ReturnCheckCard(
                        links = ui.returnLinks.map { it.copy(labelKo = keepWords(it.labelKo)) },
                        facts = ui.returnFacts.map { it.copy(textKo = keepWords(it.textKo), lastVerified = breakableDate(it.lastVerified)) },
                        sourceNames = ui.indexSources.mapValues { keepWords(it.value) },
                        onOpenLink = actions.openLink,
                    )
                }
                if (stage.askDestroy) {
                    item(key = "destroy") {
                        // 카드 자체가 확인 단계라 대화상자 없음(D8). 7일 미루기가 먼저(위), 지우기는 빨간 테두리 버튼
                        CardNewsCard(
                            title = keepWords(stringResource(R.string.today_destroy_title)),
                            icon = Icons.Outlined.Lock,
                            tone = BadgeTone.Neutral,
                            body = keepWords(stringResource(R.string.today_destroy_body)),
                        ) {
                            Column(
                                Modifier.padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner),
                            ) {
                                SecondaryButton(keepWords(stringResource(R.string.today_destroy_later)), onClick = onPostpone, icon = Icons.Outlined.Schedule)
                                DangerButton(keepWords(stringResource(R.string.today_destroy_now)), onClick = onDestroy, fillWidth = true)
                            }
                        }
                    }
                }
            }
            TripStage.WrapUp -> item(key = "wrap") {
                NextCard(
                    icon = Icons.Outlined.TaskAlt,
                    // eyebrow는 다른 단계와 같은 '지금 할 일' — '정리'는 단계 표시·제목과 겹친다
                    eyebrow = nextLabel,
                    title = stringResource(R.string.today_wrapup_title_lines),
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
                    title = keepWords(stringResource(R.string.help_shortcut_title)),
                    icon = Icons.Outlined.Sos,
                    tone = BadgeTone.Help,
                    body = if (largeText) null else keepWords(stringResource(R.string.today_help_body)),
                    onClick = actions.help,
                )
            }
        }
        if (stage.stage != TripStage.NoTrip && stage.stage != TripStage.WrapUp) {
            item(key = "edit") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    QuietButton(keepWords(stringResource(R.string.trip_edit_title)), onClick = actions.editTrip, icon = Icons.Outlined.EditCalendar)
                }
            }
        }
    }
}

/** 입국 카드 할 일 (준비 중·출국일에 입국 카드 기간이면) */
@Composable
private fun FormTaskCard(formName: String, onOpen: () -> Unit) {
    NextCard(
        icon = Icons.Outlined.AssignmentInd,
        eyebrow = stringResource(R.string.today_next_label),
        title = stringResource(R.string.today_task_form_title, formName),
        body = stringResource(R.string.today_task_form_body),
        button = stringResource(R.string.prepare_form_open),
        buttonIcon = Icons.Outlined.EditNote,
        onClick = onOpen,
    )
}

/**
 * 지금 할 일 카드 (Accent 채움, onDark 내용 세트만): 아이콘 배지 + eyebrow(지금 할 일) + 제목 + 설명 + 흰 주 버튼.
 * 글자는 여기서 keepWords()를 거친다.
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
    CardNewsCard(
        title = keepTitle(title),
        icon = icon,
        eyebrow = eyebrow?.let(::keepWords),
        body = body?.let(::keepWords),
        style = NewsStyle.Accent,
    ) {
        PrimaryButton(
            text = keepWords(button),
            onClick = onClick,
            icon = buttonIcon,
            colors = ButtonStyles.onDark(Tokens.Accent),
        )
    }
}

/** 단계 한 줄 (글자는 keepWords) */
private fun step(text: String, icon: ImageVector, detail: String? = null) = Step(keepWords(text), icon, detail?.let(::keepWords))

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

/** 출처 이름이 이보다 많으면 한 줄에 하나씩 쌓는다 (`, `로 이으면 다섯 이름이 한 문단이 된다) */
private const val STACK_SOURCES_OVER = 3

/**
 * 담아 둔 물건 카드 (6-13): 행마다 분류 아이콘 + 이름 + 반입 판정 배지 + 판정 설명.
 * 카드 맨 아래 출처 = 반입 판정 출처(importSource) 먼저, 그다음 품목 출처 — 판정과 설명이 출처 없이 보이지 않게.
 * 같은 날짜의 이름이 4개 이상이면 `출처 이름1⏎이름2⏎… · 최종 확인 날짜`로 한 줄에 하나씩(형식은 source_footer 그대로).
 * 출처 이름을 못 찾으면 `공식 안내`(내부 ID를 보이지 않는다).
 */
@Composable
private fun CartCard(ui: TodayUi) {
    val fallback = stringResource(R.string.source_official_fallback)
    val names = ui.indexSources + ui.sourceNames
    val items = ui.cart.sortedBy { importOrder(it.import) }
    val ordered = items.map { it.importSource to it.lastVerified } + items.map { it.source to it.lastVerified }
    val refs = ordered
        .groupBy({ (_, date) -> displayDate(date) }, { (id, _) -> keepWords(resolveSourceName(id, names, fallback)) })
        .map { (date, group) ->
            val distinct = group.distinct()
            SourceRef(distinct.joinToString(if (distinct.size > STACK_SOURCES_OVER) "\n" else ", "), breakableDate(date))
        }
    CardNewsCard(
        title = keepWords(stringResource(R.string.today_cart_title)),
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
            Text(keepWords(item.names.ko), style = MaterialTheme.typography.titleMedium, color = Tokens.Ink)
            ImportVerdictBadge(item.import)
            item.importNoteKo?.let { Text(keepWords(it), style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary) }
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

// ======================= API 33 미만 줄바꿈 보정 (DESIGN_SPEC 3.2) =======================
// C 묶음(내 여행·여행 고치기·여행 준비) 화면이 함께 쓴다. 공용 부품이 동결이라 화면이 넘기는 글자에서 보정한다 —
// 2단계에서 부품(IconTile·버튼·CardNewsCard·SectionHeader 등)이 같은 보정을 하게 되면 여기 호출을 지운다.

private const val WORD_JOINER = '⁠'
private const val NO_BREAK_SPACE = ' '

/** 한 음절·짧은 관형사: 뒤 낱말과 떨어져 줄 끝에 홀로 남으면 어색하다(`도착했어요! 이 / 순서대로`) */
private val Determiners = setOf("이", "그", "저", "새", "첫", "각", "몇", "온", "어느", "무슨", "모든")

/** 의존 명사: 앞 낱말과 떨어져 줄 머리에 오면 어색하다(`낼 / 수 있어요`) */
private val BoundNouns = setOf("수", "것", "줄", "데", "뿐", "듯")

private fun isHangul(c: Char): Boolean = c in '가'..'힣' || c in 'ᄀ'..'ᇿ' || c in '㄰'..'㆏'

/** 이 글자 **뒤**에서는 줄을 바꿔도 된다 (`유심·인터넷` → `유심·` 뒤, `2일~4일` → `~` 뒤) */
private fun isSoftBreak(c: Char): Boolean = c == '·' || c == '/' || c == '—' || c == '–' || c == '~'

/**
 * 한국어 낱말 안에서 줄이 바뀌지 않게 한다: 공백 없이 붙은 두 글자 중 하나라도 한글이면 사이에 WORD JOINER(U+2060)를 넣고,
 * 관형사(`이`, `새`…) 뒤·의존 명사(`수`, `것`…) 앞 공백은 NBSP로 바꿔 이웃 낱말과 함께 넘긴다.
 * 보이는 글자는 그대로(U+2060은 폭 0, TalkBack도 읽지 않음). 낱말 하나가 줄보다 길면 플랫폼이 그 낱말 안에서 끊는다(지금과 같음).
 * 한글이 없는 글(영문·숫자·태국어)은 건드리지 않는다.
 */
internal fun joinKoreanWords(text: String): String {
    if (text.none(::isHangul)) return text
    val out = StringBuilder(text.length * 2)
    var wordStart = 0
    text.forEachIndexed { i, c ->
        if (i > 0) {
            val p = text[i - 1]
            val joinable = !p.isWhitespace() && !c.isWhitespace() && !isSoftBreak(p) &&
                p != WORD_JOINER && c != WORD_JOINER && (isHangul(p) || isHangul(c))
            if (joinable) out.append(WORD_JOINER)
        }
        val glue = c == ' ' && i > 0 && !text[i - 1].isWhitespace() && i + 1 < text.length && !text[i + 1].isWhitespace() &&
            (text.substring(wordStart, i) in Determiners || text.substring(i + 1).takeWhile { !it.isWhitespace() } in BoundNouns)
        out.append(if (glue) NO_BREAK_SPACE else c)
        if (c.isWhitespace()) wordStart = i + 1
    }
    return out.toString()
}

/**
 * 화면에 그릴 앱 글자(제목·버튼·타일 라벨·eyebrow·부제·앱 문장)의 낱말 보호. **API 33 미만에서만** 글자를 바꾼다 —
 * 33 이상은 테마의 WordBreak.Phrase(어절 단위)가 같은 일을 하고, 테스트·TalkBack이 찾는 원문이 그대로 남는다.
 */
internal fun keepWords(text: String): String = if (Build.VERSION.SDK_INT >= 33) text else joinKoreanWords(text)

/** 제목용 keepWords: 문장 중간의 `! `·`? ` 뒤에서 줄을 바꾼다(`도착했어요!⏎이 순서대로 해요`) — API 33 미만에서만 */
internal fun keepTitle(text: String): String =
    if (Build.VERSION.SDK_INT >= 33) text else joinKoreanWords(text.replace("! ", "!\n").replace("? ", "?\n"))

/** `최종 확인 2026.09.29` 덩어리 폭(약 10em)이 카드 안 출처 줄(360dp 폭 기기에서 약 246dp)을 넘기 시작하는 글자 크기 */
private val SourceUnitBreakSize = 24.dp

/**
 * 출처 줄 날짜. `source_footer`는 `최종 확인`과 날짜를 NBSP로 한 덩어리로 묶어서, 글자가 커져(쉬운 모드 150% 이상·200%)
 * 그 덩어리가 줄보다 길면 날짜 한가운데서 끊긴다(`2026.09.2 / 8`). 그 크기에서만 날짜 앞에 ZERO WIDTH SPACE(U+200B)를 넣어
 * 날짜가 통째로 다음 줄로 가게 한다(보통 크기에서는 `최종 확인`과 날짜가 함께 넘어가는 지금 모양 그대로).
 * 보이는 글자·TalkBack은 그대로. (SourceFooter는 0단계 동결 부품 — 부품이 날짜 앞 줄바꿈을 허용하게 되면 지운다)
 */
@Composable
internal fun breakableDate(date: String): String {
    // sp → dp는 API 34+의 비선형 글자 확대까지 반영한다
    val size = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.fontSize.toDp() }
    return if (size > SourceUnitBreakSize) "​$date" else date
}
