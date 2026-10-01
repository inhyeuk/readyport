package com.readyport.ui.today

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Celebration
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
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
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
import com.readyport.ui.components.FactChip
import com.readyport.ui.components.FactGrid
import com.readyport.ui.components.HelpShortcutRow
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.ImportVerdictBadge
import com.readyport.ui.components.InfoChip
import com.readyport.ui.components.InfoTileGrid
import com.readyport.ui.components.JourneyStepper
import com.readyport.ui.components.KoText
import com.readyport.ui.components.KoreanBreak
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
import com.readyport.ui.components.isNarrowWindow
import com.readyport.ui.components.keepWords
import com.readyport.ui.components.rememberKeyIndex
import com.readyport.ui.components.resolveSourceName
import com.readyport.ui.components.scrollToKey
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.wallet.rememberDeviceAuth
import com.readyport.vault.WalletRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
        onUndoArrived = viewModel::undoArrived,
    )
}

/**
 * 내 여행 (DESIGN_SPEC 6-09~13, Departure·WrapUp).
 * 위에서부터: 여행 6단계(JourneyStepper) → 지금 할 일 카드(Accent, 한 화면 할 일 하나) → 단계별 카드뉴스 →
 * 급할 때는 도움(행 전체가 버튼) → 여행 고치기(글자 버튼, 맨 아래).
 * 한국어 줄바꿈(API 33 미만 낱말 보호)은 공용 부품이 한다. 말하기·TalkBack 설명은 원문.
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
    /** '도착했어요'를 잘못 눌렀을 때 되돌리기 (재검토 R18) */
    onUndoArrived: () -> Unit = {},
) {
    val stage = ui.stage
    // 귀국 단계의 '지금 할 일' 버튼이 같은 화면 아래 카드(담아 둔 물건·귀국 전 확인)로 데려간다 (4.1 scrollToKey)
    val listState = rememberLazyListState()
    val keys = rememberKeyIndex()
    val scope = rememberCoroutineScope()
    val goTo: (String) -> Unit = { key -> scope.launch { listState.scrollToKey(keys, key) } }
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

    AppScreen(
        title = title,
        subtitle = subtitle,
        speech = stringResource(R.string.today_speech_trip, title, stageName),
        state = listState,
        keyIndex = keys,
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
                        text = stringResource(R.string.today_next_button),
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
                    val label = stringResource(R.string.today_arrived_button)
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
                            text = stringResource(R.string.today_arrival_done),
                            onClick = onArrivalDone,
                            icon = Icons.Outlined.TaskAlt,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                // '도착했어요'를 잘못 눌렀으면 되돌린다(재검토 R18) — 출발 당일에만: 그 뒤에는 되돌려도 '여행 중'이라 뜻이 없다
                if (stage.dayOfTrip == null || stage.dayOfTrip == 1L) {
                    item(key = "undo-arrived") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            QuietButton(
                                stringResource(R.string.today_arrived_undo),
                                onClick = onUndoArrived,
                                icon = Icons.AutoMirrored.Outlined.Undo,
                            )
                        }
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
                    // 사진 머리 카드(나라 사진 + `태국 여행이 끝났어요`) + 한 줄(기간·담아 둔 물건) + 지금 할 일 (재검토 R14).
                    // 지금 할 일은 같은 화면 아래 카드로 데려간다: 담아 둔 물건이 있으면 그 카드, 없으면 귀국 전 확인
                    val target = if (ui.cart.isNotEmpty()) "cart" else "return-links"
                    ReturnHeroCard(ui, onGo = { goTo(target) })
                }
                // 담아 둔 쇼핑 목록의 반입 가능 여부를 다시 확인 (PRD 11.3) — 불가 → 주의 → 가능 순, 판정 출처를 카드 맨 아래에
                if (ui.cart.isNotEmpty()) {
                    item(key = "cart") { CartCard(ui) }
                }
                item(key = "return-links") {
                    // 귀국 전 확인은 공용 접힌 요약: 문장마다 첫 문장만, 숫자는 팩 문장 그대로 굵게 (R14 — 홈·나라·쇼핑과 같은 카드)
                    ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, actions.openLink)
                }
                if (stage.askDestroy) {
                    item(key = "destroy") {
                        // 카드 자체가 확인 단계라 대화상자 없음(D8). 7일 미루기가 먼저(위), 지우기는 빨간 테두리 버튼.
                        // 버튼 이름에 무엇을 지우는지 붙인다 — TalkBack이 `지우기`만 읽지 않게(R18)
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
                                DangerButton(stringResource(R.string.today_destroy_now_target), onClick = onDestroy, fillWidth = true)
                            }
                        }
                    }
                }
            }
            TripStage.WrapUp -> item(key = "wrap") {
                // 다녀온 여행을 축하하는 사진 카드 (R14·R19): 나라 사진 + 한 줄 + 앱 안 값으로 만든 숫자 타일 + 새 여행 만들기
                WrapUpCard(ui, onNewTrip)
            }
        }

        // 급할 때는 도움 — 홈과 같은 공용 줄(재검토 R4). 큰 글자에서도 설명을 숨기지 않고 배지·셰브론을 윗줄로 올린다(R5)
        item(key = "help") { HelpShortcutRow(actions.help) }
        if (stage.stage != TripStage.NoTrip && stage.stage != TripStage.WrapUp) {
            item(key = "edit") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    QuietButton(stringResource(R.string.trip_edit_title), onClick = actions.editTrip, icon = Icons.Outlined.EditCalendar)
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
 * 제목은 keepTitle(`! ` 뒤 줄바꿈, API 33 미만), 낱말 보호는 CardNewsCard가 한다.
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
        eyebrow = eyebrow,
        body = body,
        style = NewsStyle.Accent,
    ) {
        PrimaryButton(
            text = button,
            onClick = onClick,
            icon = buttonIcon,
            colors = ButtonStyles.onDark(Tokens.Accent),
        )
    }
}

/** 단계 한 줄 (글자는 keepWords) */
private fun step(text: String, icon: ImageVector, detail: String? = null) = Step(text, icon, detail?.let(::keepWords))

/** 반입 판정 순서: 불가 → 주의 → 가능 (6-13) */
private fun importOrder(status: ImportStatus): Int = when (status) {
    ImportStatus.Prohibited -> 0
    ImportStatus.Caution -> 1
    ImportStatus.Allowed -> 2
}

/**
 * 담아 둔 물건 카드 (6-13): 행마다 분류 아이콘 + 이름 + 반입 판정 배지 + 판정 설명.
 * 카드 맨 아래 출처 = 반입 판정 출처(importSource) 먼저, 그다음 품목 출처 — 판정과 설명이 출처 없이 보이지 않게.
 * 공용 SourceList가 기관별로 한 줄씩 묶고 날짜가 같으면 끝에 한 번만 쓴다(재검토 R9). 이름은 하나도 빠뜨리지 않는다.
 * 출처 이름을 못 찾으면 `공식 안내`(내부 ID를 보이지 않는다). 품목 배지는 모든 화면에서 Neutral + 품목 아이콘(R11).
 */
@Composable
private fun CartCard(ui: TodayUi) {
    val fallback = stringResource(R.string.source_official_fallback)
    val names = ui.indexSources + ui.sourceNames
    val items = ui.cart.sortedBy { importOrder(it.import) }
    val ordered = items.map { it.importSource to it.lastVerified } + items.map { it.source to it.lastVerified }
    val refs = ordered.map { (id, date) -> SourceRef(resolveSourceName(id, names, fallback), displayDate(date)) }
    CardNewsCard(
        title = stringResource(R.string.today_cart_title),
        icon = Icons.Outlined.ShoppingBag,
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
        IconBadge(IconKeys.item(item.id, item.category), tone = BadgeTone.Neutral, size = dimens.iconBadgeSmall)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KoText(item.names.ko, style = MaterialTheme.typography.titleMedium, color = Tokens.Ink)
            ImportVerdictBadge(item.import)
            item.importNoteKo?.let { KoText(it, style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary) }
        }
    }
}

/** 여행 박 수 (출발일 ~ 돌아오는 날). 날짜가 없거나 틀리면 null */
private fun nightsOf(trip: Trip?): Int? {
    val start = parseDate(trip?.startDate) ?: return null
    val end = parseDate(trip?.endDate) ?: return null
    return ChronoUnit.DAYS.between(start, end).toInt().takeIf { it >= 0 }
}

/**
 * 귀국 머리 카드 (재검토 R14): 나라 사진 머리(`태국 여행이 끝났어요`) → 한 줄(여행 기간·담아 둔 물건 — 앱 안 값만, 누를 수 없는 InfoChip)
 * → 지금 할 일(eyebrow + 할 일 한 문장 + 주 버튼 — 같은 화면 아래 카드로 이동, 화면에 주 버튼은 이것 하나).
 * 다른 단계의 Accent '지금 할 일' 카드와 같은 eyebrow·문장 구조를 흰 사진 카드 안에 둔다(감정의 순간은 사진이 맡는다).
 */
@Composable
private fun ReturnHeroCard(ui: TodayUi, onGo: () -> Unit) {
    val country = ui.countryName
    val title = if (country != null) stringResource(R.string.today_return_photo_title, country) else stringResource(R.string.today_return_title)
    val nights = nightsOf(ui.trip)
    val hasCart = ui.cart.isNotEmpty()
    PhotoHeaderCard(
        photo = ui.trip?.country?.let(Photos::country),
        title = title,
        icon = Icons.Outlined.Cottage,
    ) {
        val chips = listOfNotNull(
            nights?.let { Icons.Outlined.DateRange to stringResource(R.string.today_fact_nights, it, it + 1) },
            ui.cart.size.takeIf { hasCart }?.let { Icons.Outlined.ShoppingBag to stringResource(R.string.today_fact_cart, it) },
        )
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { (icon, text) -> InfoChip(text, icon) }
            }
            HorizontalDivider(thickness = 1.dp, color = Tokens.LineSoft, modifier = Modifier.padding(vertical = 4.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KoText(stringResource(R.string.today_next_label), MaterialTheme.typography.labelMedium, color = Tokens.Accent)
            KoText(
                stringResource(if (hasCart) R.string.today_return_task_cart else R.string.today_return_task_rules),
                MaterialTheme.typography.titleMedium,
                color = Tokens.Ink,
                glueShort = true,
            )
        }
        // 같은 화면 아래로 이동 = ArrowDownward (버튼 앞 꺾쇠 금지, R11)
        PrimaryButton(
            text = stringResource(if (hasCart) R.string.today_return_task_cart_button else R.string.today_return_task_rules_button),
            onClick = onGo,
            icon = Icons.Outlined.ArrowDownward,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * 정리 단계 축하 카드 (재검토 R14·R19 — 정리 단계의 브랜드 순간): 나라 사진 머리(`태국 여행, 잘 다녀오셨어요`) →
 * 한 줄(정리 끝! 다음 여행도 함께해요) → 숫자 타일(다녀온 나라·여행 기간·담아 온 물건 — 앱 안 값만) → 새 여행 만들기.
 * 장식은 사진과 아이콘 하나로 그친다(과한 장식 금지).
 */
@Composable
private fun WrapUpCard(ui: TodayUi, onNewTrip: () -> Unit) {
    val country = ui.countryName
    val nights = nightsOf(ui.trip)
    val title = if (country != null) {
        stringResource(R.string.today_wrapup_photo_title, country)
    } else {
        stringResource(R.string.today_wrapup_photo_title_plain)
    }
    val facts = listOfNotNull(
        country?.let { Fact(Icons.Outlined.Public, it, stringResource(R.string.today_wrapup_fact_country), tone = BadgeTone.Teal) },
        nights?.let { Fact(Icons.Outlined.DateRange, stringResource(R.string.trip_nights, it, it + 1), stringResource(R.string.trip_length_label)) },
        ui.cart.size.takeIf { it > 0 }?.let {
            Fact(Icons.Outlined.ShoppingBag, stringResource(R.string.today_wrapup_fact_cart_value, it), stringResource(R.string.today_wrapup_fact_cart))
        },
    )
    PhotoHeaderCard(
        photo = ui.trip?.country?.let(Photos::country),
        title = title,
        icon = Icons.Outlined.Celebration,
    ) {
        KoText(stringResource(R.string.today_wrapup_title_lines), MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
        if (facts.size >= 2) {
            FactGrid(facts, Modifier.padding(vertical = 4.dp))
        } else {
            facts.forEach { FactChip(it) }
        }
        PrimaryButton(
            text = stringResource(R.string.today_new_trip),
            onClick = onNewTrip,
            icon = Icons.Outlined.EditCalendar,
            modifier = Modifier.padding(top = 4.dp),
        )
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

/**
 * 제목용 줄바꿈: 문장 중간의 `! `·`? ` 뒤에서 줄을 바꾼다(`도착했어요!⏎이 순서대로 해요`) — API 33 미만에서만.
 * 낱말 보호(keepWords)는 제목을 그리는 공용 부품(CardNewsCard 등)이 한다.
 */
internal fun keepTitle(text: String): String =
    if (!KoreanBreak.syllableBreaks()) text else text.replace("! ", "!\n").replace("? ", "?\n")
