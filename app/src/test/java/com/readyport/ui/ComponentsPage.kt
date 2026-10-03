package com.readyport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.trip.JourneyStage
import com.readyport.prep.ImportStatus
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.ButtonStyles
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.ImportVerdictNote
import com.readyport.ui.components.RequiredMark
import com.readyport.ui.components.RequiredSummary
import com.readyport.ui.components.EntryFormCard
import com.readyport.ui.components.EssentialsChips
import com.readyport.ui.components.EssentialsProgress
import com.readyport.ui.components.StepHead
import com.readyport.ui.components.essentialsSources
import com.readyport.ui.components.essentialsSummary
import com.readyport.ui.components.personalWindowKo
import com.readyport.ui.components.AssuranceCard
import com.readyport.ui.components.ReturnCheckMode
import com.readyport.ui.components.ButtonPlacement
import com.readyport.ui.components.ChipSpec
import com.readyport.ui.components.ChoiceCard
import com.readyport.ui.components.ChoiceSegments
import com.readyport.ui.components.ComingSoonGroup
import com.readyport.ui.components.CountryPhotoTile
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.EmergencyCallTile
import com.readyport.ui.components.DotBullet
import com.readyport.ui.components.HelpShortcutRow
import com.readyport.ui.components.InfoChip
import com.readyport.ui.components.SelectableCard
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Power
import com.readyport.ui.components.EmptyState
import com.readyport.ui.components.ExpandableDetail
import com.readyport.ui.components.Fact
import com.readyport.ui.components.FactChip
import com.readyport.ui.components.FactGrid
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.JourneyStageBar
import com.readyport.ui.components.JourneyStageCell
import com.readyport.ui.components.IconTile
import com.readyport.ui.components.ImportVerdictBadge
import com.readyport.ui.components.InfoTileGrid
import com.readyport.ui.components.KeyValueRow
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LinkRow
import com.readyport.ui.components.ListDivider
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.LockedState
import com.readyport.ui.components.NewsStyle
import com.readyport.ui.components.NavMosaic
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.OfflineBanner
import com.readyport.ui.components.PhotoBox
import com.readyport.ui.components.PhotoChip
import com.readyport.ui.components.PhotoHeaderCard
import com.readyport.ui.components.PhotoTextArea
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.ReturnCheckCard
import com.readyport.ui.components.RowTrailing
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SectionTabs
import com.readyport.ui.components.SecurityBanner
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.SelectTile
import com.readyport.ui.components.SourceFooter
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.Step
import com.readyport.ui.components.StepList
import com.readyport.ui.components.TextCircle
import com.readyport.ui.components.TileGrid
import com.readyport.ui.components.TileLayout
import com.readyport.ui.components.TileSpec
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.feeIcon
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.rememberKeyIndex
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.shortValue
import com.readyport.ui.components.sourceRefs
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import java.time.LocalDate

/**
 * 0단계 공용 부품 전부를 네 화면에 나눠 (Gallery `components-1~4` — sdk 31·200% 쉬운 모드에서도 한 장이 캡처·감사 높이 안에 들어가게.
 * 넘치면 A11yAudit·GalleryCapture가 '화면이 잘림'으로 실패한다). 값은 번들 팩·ICAO 표본 같은 가짜 값만.
 * 확인 대화상자(DestructiveConfirm)는 별도 창이라 캡처(onRoot)가 깨지므로 버튼으로만 연다.
 */
@Composable
fun ComponentsPage(part: Int) {
    val th = TestPacks.thailand.value
    val index = TestPacks.index.value
    val indexSources = index.sources.associate { it.id to it.name }
    val req = th.requirements.first { it.nationality == "KR" }
    val form = th.forms.first()
    val reqRef = SourceRef(th.source(req.source)!!.name, displayDate(req.lastVerified))
    val formRef = SourceRef(th.source(form.source)!!.name, displayDate(form.lastVerified))
    val fee = shortValue(form.feeKo)!!
    val facts = listOf(
        Fact(Icons.Outlined.EventAvailable, stringResource(R.string.fact_days, req.stayLimitDays!!), stringResource(R.string.fact_label_visa_free), source = reqRef),
        Fact(feeIcon(fee), fee, stringResource(R.string.fact_label_form_fee), BadgeTone.Success, source = formRef),
    )
    var segment by remember { mutableStateOf(0) }
    var chip by remember { mutableStateOf("TH") }
    var toggle by remember { mutableStateOf(false) }
    var switch by remember { mutableStateOf(true) }
    var kind by remember { mutableStateOf("flight") }
    var confirm by remember { mutableStateOf(false) }
    val keys = rememberKeyIndex()

    if (confirm) {
        DestructiveConfirm(
            title = stringResource(R.string.booking_delete_confirm_title),
            body = stringResource(R.string.booking_delete_confirm_body),
            confirmLabel = stringResource(R.string.wallet_booking_delete),
            onConfirm = { confirm = false },
            onDismiss = { confirm = false },
            secure = true,
        )
    }

    AppScreen(title = "공용 부품 $part", speech = "공용 부품", icon = Icons.Outlined.Explore, keyIndex = keys) {
        if (part == 1) {
        item(key = "offline") { OfflineBanner() }
        item(key = "security") { SecurityBanner() }
        item(key = "security-compact") { SecurityBanner(compact = true) }
        item(key = "notice") {
            NoticeBanner(
                stringResource(R.string.guide_not_affiliated), icon = Icons.Outlined.Policy,
                secondLine = stringResource(R.string.country_submit_self), secondIcon = Icons.Outlined.TouchApp,
            )
        }
        // 안심 카드: 쌓이던 띠 셋(비제휴·이 휴대폰에만·제출은 직접)을 한 장으로 (다듬기 D0)
        item(key = "assurance") { AssuranceCard() }
        item(key = "notice-tones") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NoticeBanner(stringResource(R.string.essentials_fee_disclosure), icon = Icons.Outlined.Handshake, title = stringResource(R.string.essentials_fee_link_label))
                NoticeBanner(stringResource(R.string.guide_power_kr_adapter), tone = BannerTone.Caution)
                NoticeBanner(stringResource(R.string.wallet_passport_expired), icon = Icons.Outlined.EventBusy, tone = BannerTone.Danger)
                NoticeBanner(stringResource(R.string.guide_power_kr_fits), tone = BannerTone.Success)
            }
        }
        sectionGap("gap-1")
        item(key = "section-header") {
            SectionHeader(
                stringResource(R.string.country_travel_tools_title), icon = Icons.Outlined.Explore,
                eyebrow = th.names.ko, subtitle = stringResource(R.string.home_countries_body),
                action = { QuietButton(stringResource(R.string.action_more), onClick = {}, icon = Icons.AutoMirrored.Outlined.NavigateNext) },
            )
        }
        // 아래 내용을 바꾸는 **탭 줄**(밑줄 표시) — 값을 고르는 세그먼트(Accent 채움)와 모양이 다르다 (v3)
        item(key = "tabs") {
            Box(Modifier.background(Tokens.Surface)) {
                SectionTabs(
                    options = listOf(0, 1, 2),
                    selected = segment,
                    onSelect = { segment = it },
                    label = { stringResource(listOf(R.string.country_tab_entry, R.string.country_tab_travel, R.string.country_tab_shopping)[it]) },
                    shortLabel = {
                        stringResource(listOf(R.string.country_tab_entry_short, R.string.country_tab_travel_short, R.string.country_tab_shopping_short)[it])
                    },
                    icon = { listOf(Icons.Outlined.Approval, Icons.Outlined.Explore, Icons.Outlined.ShoppingBag)[it] },
                )
            }
        }
        item(key = "segments") {
            ChoiceSegments(
                options = listOf(0, 1, 2),
                selected = segment,
                onSelect = { segment = it },
                label = { stringResource(listOf(R.string.videos_sort_views, R.string.videos_sort_recent, R.string.videos_sort_subscribers)[it]) },
                icon = { listOf(Icons.Outlined.Visibility, Icons.Outlined.NewReleases, Icons.Outlined.Groups)[it] },
            )
        }
        item(key = "chips") {
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip(chip == "TH", { chip = "TH" }, "태국", avatar = { CountryAvatar("TH") })
                SelectChip(chip == "JP", { chip = "JP" }, "일본", avatar = { CountryAvatar("JP") })
                SelectChip(toggle, { toggle = !toggle }, stringResource(R.string.form_local_large), leadingIcon = Icons.Outlined.Translate, singleChoice = false)
            }
        }
        sectionGap("gap-s")
        // 다듬기 S에서 공용으로 옮긴 부품: 순서 머리(04·21) + 내 여행 날짜로 '내는 때'(03·18 — 출발일 = 도착일 가정, `도착하면`)
        item(key = "step-head") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.inner)) {
                StepHead(1, stringResource(R.string.country_step_head_form))
                EntryFormCard(
                    name = form.nameKo, feeKo = form.feeKo,
                    windowKo = personalWindowKo(form.windowKo, form.windowDaysIncludingArrival, LocalDate.of(2026, 11, 3)),
                    source = formRef, eyebrow = stringResource(R.string.entry_form_label), onStart = {},
                )
                StepHead(2, stringResource(R.string.country_step_head_visa))
            }
        }
        sectionGap("gap-s2")
        // 꼭 챙길 물건 요약(홈 01·02 · 여행 준비 18): 값이 있는 정보 칩 + `n / 5` 진행 줄 + 칩 값 출처 — 팩 값 그대로
        item(key = "essentials-summary") {
            val summary = essentialsSummary(index, th, setOf("passport", "medicine"))
            CardNewsCard(
                title = stringResource(R.string.prepare_items_title), icon = IconKeys.essentials,
                sources = essentialsSources(summary),
            ) {
                EssentialsChips(summary)
                EssentialsProgress(summary)
            }
        }
        }
        if (part == 2) {
        item(key = "visa-card") {
            CardNewsCard(
                title = req.summaryKo, icon = Icons.Outlined.Approval, eyebrow = stringResource(R.string.country_visa_title),
                style = NewsStyle.Accent, sources = listOf(reqRef) + facts.sourceRefs(),
            ) {
                FactGrid(facts)
                SecondaryButton(stringResource(R.string.country_visa_link), onClick = {}, onDark = true)
                PrimaryButton(stringResource(R.string.home_trip_open), onClick = {}, colors = ButtonStyles.onDark(Tokens.Accent))
            }
        }
        // 입국 카드 한 장 — 나라 입국·비자와 여행 준비가 같은 부품·같은 말 (다듬기 D0)
        item(key = "form-card") {
            EntryFormCard(
                name = form.nameKo, feeKo = form.feeKo, windowKo = form.windowKo, source = formRef,
                eyebrow = stringResource(R.string.entry_form_label), onStart = {},
                body = stringResource(R.string.country_form_autofill_body),
            )
        }
        item(key = "steps") {
            CardNewsCard(title = stringResource(R.string.today_departure_steps_title), icon = Icons.Outlined.TravelExplore) {
                StepList(
                    listOf(
                        Step(stringResource(R.string.today_departure_step1), IconKeys.stage(1), stringResource(R.string.today_departure_step1_detail)),
                        Step(stringResource(R.string.today_departure_step2)),
                        Step(stringResource(R.string.today_departure_step3)),
                    ),
                )
                StepList(listOf(Step(stringResource(R.string.explore_maps_step1)), Step(stringResource(R.string.explore_maps_step2))), numbered = false)
            }
        }
        item(key = "bullets") {
            CardNewsCard(title = stringResource(R.string.shopping_return_title), icon = Icons.Outlined.GppMaybe, style = NewsStyle.Caution) {
                IconBullet(stringResource(R.string.guide_power_kr_adapter), Icons.Outlined.Lock)
                IconBullet(stringResource(R.string.wallet_passport_expiring), Icons.Outlined.EventBusy, tone = BadgeTone.Caution)
                IconBullet(stringResource(R.string.wallet_passport_expired), Icons.Outlined.EventBusy, tone = BadgeTone.Danger)
                // 팩 문장 불릿(재검토 R8): 뜻 없는 점 하나 — 금지 문장에도 체크·대시를 두지 않는다
                th.sections.first { it.id == "entry" }.bodyKo.take(2).forEach { DotBullet(it) }
                ExpandableDetail { Text(stringResource(R.string.settings_local_only_body), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        item(key = "navy-card") {
            CardNewsCard(title = stringResource(R.string.home_passport_title), icon = Icons.Outlined.Lock, body = stringResource(R.string.home_passport_body), style = NewsStyle.Navy) {
                PrimaryButton(stringResource(R.string.home_passport_open), onClick = {}, colors = ButtonStyles.onDark())
            }
        }
        item(key = "danger-card") {
            CardNewsCard(
                title = stringResource(R.string.form_choose_yourself), icon = Icons.Outlined.EditNote, style = NewsStyle.SurfaceCaution,
                trailing = { StatusTag(stringResource(R.string.form_missing_count, 2), StatusKind.Required) },
            ) {
                KeyValueRow("Family Name", "ERIKSSON", subLabel = "Family Name · นามสกุล", badge = { StatusTag(stringResource(R.string.passport_check_ok), StatusKind.Verified) })
                // 재검토 R3 슬롯: 앞 아이콘 · 이름 나란히 + 끝 버튼 · 가린 값
                KeyValueRow(stringResource(R.string.booking_field_reference), "ABC123", leading = Icons.Outlined.ConfirmationNumber)
                KeyValueRow(
                    "성", "ERIKSSON", subLabel = "Family Name", subLabelInline = true, supporting = stringResource(R.string.form_empty_value),
                    trailing = { QuietButton(stringResource(R.string.manual_copy), onClick = {}, icon = Icons.Outlined.ContentCopy) },
                )
                KeyValueRow(stringResource(R.string.wallet_passport_number), "L••••••C3", masked = true)
            }
        }
        item(key = "danger-style") {
            CardNewsCard(title = stringResource(R.string.today_destroy_title), icon = Icons.Outlined.Lock, style = NewsStyle.Danger, body = stringResource(R.string.today_destroy_body)) {
                SecondaryButton(stringResource(R.string.today_destroy_later), onClick = {}, icon = Icons.Outlined.Schedule)
                DangerButton(stringResource(R.string.today_destroy_now_target), onClick = { confirm = true }, placement = ButtonPlacement.CardAction)
            }
        }
        }
        if (part == 3) {
        sectionGap("gap-2")
        item(key = "tiles") {
            InfoTileGrid(
                listOf(
                    TileSpec(stringResource(R.string.today_go_stay), Icons.Outlined.Hotel, {}, emphasized = true),
                    TileSpec(stringResource(R.string.tile_phrases_emergency), Icons.Outlined.Translate, {}, tone = BadgeTone.Help),
                    TileSpec(stringResource(R.string.tile_videos), Icons.Outlined.SmartDisplay, {}),
                    TileSpec(stringResource(R.string.tile_maps), Icons.Outlined.Map, {}, tone = BadgeTone.Teal),
                ),
            )
        }
        // 길 안내 모자이크(v3): 연한 톤 채움 + 큰 타일 하나 + 2열 — 흰 읽는 카드와 색·크기로 갈린다
        item(key = "nav-mosaic") {
            NavMosaic(
                listOf(
                    TileSpec(stringResource(R.string.nav_tile_plan_trip), Icons.Outlined.EditCalendar, {}),
                    TileSpec(stringResource(R.string.tile_phrases_emergency), Icons.Outlined.Translate, {}, tone = BadgeTone.Help),
                    TileSpec(stringResource(R.string.tile_videos), Icons.Outlined.SmartDisplay, {}),
                    TileSpec(stringResource(R.string.move_title), Icons.Outlined.LocalTaxi, {}, tone = BadgeTone.Violet),
                ),
            )
        }
        item(key = "tile-horizontal") {
            IconTile(TileSpec(stringResource(R.string.today_expense), Icons.AutoMirrored.Outlined.ReceiptLong, {}, supporting = stringResource(R.string.today_help_body)), layout = TileLayout.Horizontal)
        }
        item(key = "select-tiles") {
            TileGrid(
                listOf("flight" to "항공권", "lodging" to "숙소", "other" to "기타"),
                Modifier.selectableGroup(),
                columns = if (rememberGridColumns() == 1) 1 else 3,
            ) { (k, label), cell ->
                SelectTile(label, IconKeys.bookingKind(k), kind == k, { kind = k }, cell)
            }
        }
        item(key = "selectable") {
            // 큰 선택 카드(재검토 R2 규칙 ②): 선택 AccentSoft + 2dp Accent + CheckCircle / 비선택 흰 바탕 + 1dp LineStrong + 빈 원
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TH" to th.names.ko, "JP" to "일본").forEach { (code, name) ->
                    SelectableCard(selected = chip == code, onClick = { chip = code }, leading = { CountryAvatar(code) }) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item(key = "choice") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceCard(
                    stringResource(R.string.first_run_yes), stringResource(R.string.first_run_easy_preview), Icons.Outlined.TextIncrease, {}, emphasized = true,
                    preview = { Row(verticalAlignment = Alignment.Bottom) { Text("가 ", style = MaterialTheme.typography.bodyMedium); Text("가", style = MaterialTheme.typography.headlineMedium) } },
                )
                ChoiceCard(stringResource(R.string.first_run_no), stringResource(R.string.first_run_basic_preview), Icons.Outlined.TravelExplore, {})
            }
        }
        item(key = "emergency") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val tp = th.emergency.first()
                EmergencyCallTile(tp.labelKo, tp.number, IconKeys.emergency(tp.id), onCall = {}, large = true, note = stringResource(R.string.help_offline_badge))
                TileGrid(th.emergency.drop(1).filter { it.number.length <= 6 }.take(2)) { c, cell ->
                    EmergencyCallTile(c.labelKo, c.number, IconKeys.emergency(c.id), onCall = {}, modifier = cell)
                }
                th.embassy?.emergencyPhone?.let { EmergencyCallTile(stringResource(R.string.help_embassy), it, Icons.Outlined.AccountBalance, onCall = {}, note = stringResource(R.string.help_offline_badge)) }
            }
        }
        item(key = "list-group") {
            ListGroup(title = stringResource(R.string.settings_group_display)) {
                ListRow(stringResource(R.string.settings_myinfo_open), icon = Icons.Outlined.Lock, body = stringResource(R.string.settings_myinfo_body), onClick = {})
                ListDivider()
                ListRow(stringResource(R.string.settings_easy_mode), icon = Icons.Outlined.TextIncrease, body = stringResource(R.string.settings_easy_mode_desc), trailing = RowTrailing.Switch(switch) { switch = it })
                ListDivider()
                ListRow(stringResource(R.string.explore_wifi_only), icon = Icons.Outlined.Wifi, trailing = RowTrailing.Switch(!switch) { switch = !it })
                ListDivider()
                ListRow(stringResource(R.string.settings_privacy), icon = Icons.Outlined.Policy, trailing = RowTrailing.External, onClick = {})
                ListDivider()
                ListRow(stringResource(R.string.wallet_companions_title), icon = Icons.Outlined.FamilyRestroom, tone = BadgeTone.Neutral, trailing = RowTrailing.Custom { ImportVerdictBadge(ImportStatus.Allowed) })
                ListDivider()
                ListRow(
                    stringResource(R.string.help_shortcut_title), icon = Icons.Outlined.Sos, tone = BadgeTone.Help, body = stringResource(R.string.today_help_body),
                    trailing = RowTrailing.Custom { StatusTag(stringResource(R.string.help_offline_badge), StatusKind.Info) }, onClick = {},
                )
                ListDivider()
                ListRow(stringResource(R.string.settings_disclaimer), icon = Icons.Outlined.Policy, body = stringResource(R.string.settings_disclaimer_body), trailing = RowTrailing.None)
            }
        }
        item(key = "status") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusKind.entries.forEach { StatusTag(it.name, it) }
                ImportStatus.entries.forEach { ImportVerdictBadge(it) }
                StatusChip(stringResource(R.string.help_offline_badge), icon = Icons.Outlined.OfflinePin)
                TextCircle("김")
                TextCircle("12")
            }
        }
        }
        if (part == 4) {
        item(key = "photos") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CountryPhotoTile(
                    th.names.ko, th.names.en, Photos.country("TH"),
                    listOf(ChipSpec(Icons.Outlined.EventAvailable, stringResource(R.string.home_chip_visa_free, 90))),
                    onClick = {}, openLabel = stringResource(R.string.home_country_open, th.names.ko), large = true,
                )
                TileGrid(listOf("JP" to "일본", "SG" to "싱가포르")) { (code, name), cell ->
                    CountryPhotoTile(name, code, Photos.country(code), listOf(ChipSpec(Icons.Outlined.EventAvailable, stringResource(R.string.home_chip_visa_free, 90))), onClick = {}, openLabel = stringResource(R.string.home_country_open, name), modifier = cell)
                }
                PhotoHeaderCard(Photos.Airport, stringResource(R.string.home_departure_title), icon = IconKeys.stage(1)) {
                    KoText(stringResource(R.string.home_value_prop), MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item(key = "journey") {
            // 여행 과정 8단계 막대 (부록 H) — 예전 6칸 JourneyStepper를 대신한다
            JourneyStageBar(
                JourneyStage.entries.mapIndexed { i, s -> JourneyStageCell(s, done = if (i < 2) 3 else 0, total = 3, now = i == 2) },
                selected = JourneyStage.Docs,
                onSelect = {},
            )
        }
        item(key = "info-chips") {
            // 누를 수 없는 정보 칩(재검토 R1): 채움·테두리 없는 아이콘 + 글자
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip(stringResource(R.string.home_items_plug), Icons.Outlined.Power, tone = BadgeTone.Accent)
                InfoChip(stringResource(R.string.home_items_voltage), Icons.Outlined.ElectricBolt, tone = BadgeTone.Accent)
                FactChip(Fact(feeIcon(fee), fee, stringResource(R.string.fact_label_form_fee), BadgeTone.Success))
            }
        }
        item(key = "help-row") { HelpShortcutRow(onClick = {}) }
        item(key = "return-check") { ReturnCheckCard(index.returnLinks, index.returnFacts, indexSources, {}, ReturnCheckMode.Full) }
        // 홈·나라 쇼핑·쇼핑 리스트의 한 줄 요약 모양 (운영자 결정 10)
        item(key = "return-summary") { ReturnCheckCard(index.returnLinks, index.returnFacts, indexSources, {}, ReturnCheckMode.Summary) }
        // 출처 이름을 못 찾을 때(`공식 안내` — 내부 ID를 보이지 않음)
        item(key = "return-fallback") { ReturnCheckCard(index.returnLinks, index.returnFacts, emptyMap(), {}, ReturnCheckMode.Full) }
        // 꼭 채울 칸: 묶음 머리 요약 한 줄 + 칸 이름 뒤 느낌표 (다듬기 D0 — 칸마다 `꼭 채워요` 태그 대신)
        item(key = "required") {
            CardNewsCard(title = stringResource(R.string.manual_fill_on_site), icon = Icons.Outlined.EditNote) {
                RequiredSummary(total = 9, required = 7)
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KoText(stringResource(R.string.trip_country), MaterialTheme.typography.titleSmall, color = Tokens.InkSecondary)
                    RequiredMark()
                }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KoText(stringResource(R.string.trip_dates_title), MaterialTheme.typography.titleSmall, color = Tokens.InkSecondary)
                    RequiredMark(error = true)
                }
            }
        }
        // 카드 안 판정: StatusTag 알약 + 보통 본문 이유 (다듬기 D0 — 연한 채움 + 막대 블록 대신)
        item(key = "verdict") {
            val item = th.shopping.first()
            CardNewsCard(title = item.names.ko, icon = IconKeys.item(item.id, item.category), tone = BadgeTone.Neutral) {
                ImportVerdictNote(ImportStatus.of(item.importStatus), item.importNoteKo)
            }
        }
        item(key = "states") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EmptyState(Icons.Outlined.FamilyRestroom, stringResource(R.string.companion_empty_title), stringResource(R.string.companion_add_hint), action = {
                    SecondaryButton(stringResource(R.string.companion_add), onClick = {}, fillWidth = false)
                })
                LockedState(stringResource(R.string.wallet_locked_title), stringResource(R.string.wallet_locked_body), stringResource(R.string.wallet_unlock), {}, icon = Icons.Outlined.QrCode2, badgeIcon = Icons.Outlined.Lock)
                ComingSoonGroup(listOf(Icons.Outlined.ContactPage to stringResource(R.string.wallet_profile_title), Icons.Outlined.Nfc to stringResource(R.string.passport_chip_soon_v2)))
            }
        }
        item(key = "buttons") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(stringResource(R.string.form_confirm_yes), onClick = {}, icon = Icons.Outlined.EditNote)
                PrimaryButton(stringResource(R.string.form_confirm_yes), onClick = {}, enabled = false)
                SecondaryButton(stringResource(R.string.form_go_first_missing), onClick = {}, icon = Icons.Outlined.ArrowDownward)
                // 꺾쇠는 라벨 뒤에만(재검토 R11)
                SecondaryButton(stringResource(R.string.today_make_trip), onClick = {}, icon = Icons.AutoMirrored.Outlined.NavigateNext)
                SecondaryButton(stringResource(R.string.wallet_passport_reveal), onClick = {}, icon = Icons.Outlined.Visibility, enabled = false)
                SecondaryButton(stringResource(R.string.wallet_lock), onClick = {}, icon = Icons.Outlined.Lock, tone = BadgeTone.Neutral)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(stringResource(R.string.help_full_screen), onClick = {}, icon = Icons.Outlined.Fullscreen, fillWidth = false)
                    DangerButton(stringResource(R.string.wallet_booking_delete), onClick = { confirm = true }, placement = ButtonPlacement.ItemAction)
                    QuietButton(stringResource(R.string.wallet_lock), onClick = {}, icon = Icons.Outlined.Lock)
                }
                LinkRow(index.returnLinks.first().labelKo, onClick = {}, icon = Icons.Outlined.CalendarMonth)
                SourceFooter(reqRef)
            }
        }
        }
    }
}

@Composable
private fun CountryAvatar(code: String) {
    val thumb = com.readyport.ui.components.rememberThumbnail(Photos.country(code), 24.dp)
    if (thumb != null) {
        androidx.compose.foundation.Image(
            thumb, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(24.dp).clip(CircleShape).background(Tokens.Navy),
        )
    }
}

/** 흰 단색 사진 최악 경우 (DESIGN_SPEC 3.7 ③): 0.18 틴트만 있는 윗부분에는 글자가 없고, 글자는 스크림·자체 바탕 위 */
@Composable
fun PhotoWorstWhitePage() {
    AppScreen(title = "흰 사진 최악", speech = "흰 사진 최악") {
        item(key = "white") {
            PhotoBox(ColorPainter(Color.White), minHeight = 240.dp) {
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .minTouchSize()
                        .clip(CircleShape)
                        .background(Tokens.PhotoButtonBg),
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.country_back), tint = Color.White) }
                PhotoTextArea {
                    Text(stringResource(R.string.home_brand), style = MaterialTheme.typography.labelLarge, color = Color.White)
                    KoText(stringResource(R.string.home_title), MaterialTheme.typography.displaySmall, color = Color.White)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoChip(stringResource(R.string.trust_official), IconKeys.source, onDark = true)
                        InfoChip(stringResource(R.string.trust_local), Icons.Outlined.Lock, onDark = true)
                        InfoChip(stringResource(R.string.trust_offline), Icons.Outlined.OfflinePin, onDark = true)
                    }
                    PhotoChip(stringResource(R.string.guide_origin_bundled), Icons.Outlined.OfflinePin)
                }
            }
        }
        item(key = "white-header") {
            Box {
                PhotoHeaderCard(ColorPainter(Color.White), stringResource(R.string.home_departure_title), icon = IconKeys.stage(1)) {
                    KoText(stringResource(R.string.home_value_prop))
                }
            }
        }
    }
}
