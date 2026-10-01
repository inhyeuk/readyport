package com.readyport.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readyport.R
import com.readyport.autofill.FieldValue
import com.readyport.autofill.FormValues
import com.readyport.autofill.Recipe
import com.readyport.autofill.RecipeField
import com.readyport.autofill.RecipeOption
import com.readyport.autofill.ValueOrigin
import com.readyport.security.SecureScreen
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTitleLayout
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.ExpandableDetail
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.KeyValueRow
import com.readyport.ui.components.SelectableCard
import com.readyport.ui.components.selectionIconTint
import com.readyport.ui.components.KoText
import com.readyport.ui.components.requiredMarkColor
import com.readyport.ui.components.RequiredIcon
import com.readyport.ui.components.RequiredMark
import com.readyport.ui.components.KoreanBreak
import com.readyport.ui.components.LockedState
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SecurityBanner
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.firstLineIconOffset
import com.readyport.ui.components.keepTogether
import com.readyport.ui.components.keepWords
import com.readyport.ui.components.koDisplay
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.rememberKeyIndex
import com.readyport.ui.components.scrollToKey
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.startBar
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.wallet.rememberDeviceAuth
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun FormConfirmScreen(
    onAutofill: () -> Unit,
    onManual: () -> Unit,
    onRegisterPassport: () -> Unit,
    viewModel: FormConfirmViewModel = hiltViewModel(),
) {
    SecureScreen()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val auth = rememberDeviceAuth()
    FormConfirmContent(
        ui = ui,
        onSetValue = viewModel::setValue,
        onUnlock = { auth { viewModel.unlock() } },
        onRegisterPassport = onRegisterPassport,
        onConfirm = {
            scope.launch {
                when (viewModel.confirm()) {
                    WalletRepository.SaveResult.Saved -> onAutofill()
                    WalletRepository.SaveResult.Failed -> Unit
                    else -> auth { scope.launch { if (viewModel.confirm() == WalletRepository.SaveResult.Saved) onAutofill() } }
                }
            }
        },
        onManual = onManual,
    )
}

/**
 * 17 입국 카드 확인 (DESIGN_SPEC 6-17, 재검토 R16).
 * ① 정부 비제휴(첫 정보 항목) ② 보안 한 줄 ③ 현지어 크게(토글) ④ **빈칸 요약 하나**(`빈칸 N개 남았어요` + 첫 빈칸으로 가기 + 빈칸 이름 펼침)
 * ⑤ 서류에서 가져온 값 — 출처별 그룹 카드, 기본은 접힘(값만 한 줄로 보이고 펼치면 칸 이름 3개 국어 + 값)
 * ⑥ 직접 고를 칸 — 흰 한 장, 칸마다 lazy item(`field-<key>`)이라 빈칸으로 바로 갈 수 있다
 * ⑦ 원어민 검수 전 ⑧ 제출은 직접 ⑨ 맞아요(빈칸이 있으면 비활성 + 바로 위 이유 한 줄 — D9) ⑩ 고치기 ⑪ 값 복사해서 넣기.
 *
 * 노란 신호는 실제 빈칸에만(R16 — 예전엔 머리 태그·칸마다 `꼭 채워요`·아래 빈칸 칩으로 같은 경고가 세 겹, 노랑 17회):
 * 빈 필수 칸에는 작은 느낌표 표시 하나(TalkBack 상태 `빈칸`), 요약은 맨 위 한 곳. 처음에는 '할 일'로 차분하게(주의 색 느낌표),
 * '첫 빈칸으로 가기'·빈칸 이름을 누르거나 칸을 한 번 거쳐 나가면 그때 오류(빨간 테두리·느낌표)로 바뀐다.
 */
@Composable
fun FormConfirmContent(
    ui: ConfirmUi,
    onSetValue: (String, String) -> Unit,
    onUnlock: () -> Unit,
    onRegisterPassport: () -> Unit,
    onConfirm: () -> Unit,
    onManual: () -> Unit,
) {
    val ctx = ui.context
    val recipe = ctx?.recipe
    var localLarge by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    // 빈칸으로 가기를 눌렀는지 / 거쳐 나간 칸 — 이때부터 빈 필수 칸을 오류로 보인다
    var attempted by rememberSaveable { mutableStateOf(false) }
    var touched by rememberSaveable { mutableStateOf(listOf<String>()) }
    val formName = ctx?.form?.nameKo ?: ctx?.formId.orEmpty()
    val listState = rememberLazyListState()
    val keys = rememberKeyIndex()
    val scope = rememberCoroutineScope()
    val focus = remember { FieldFocus() }
    // 빈칸 태그·'첫 빈칸으로 가기': 그 칸으로 스크롤한 뒤 그려진 것을 확인하고 초점을 준다 (4.1)
    val goToField: (String) -> Unit = { key ->
        attempted = true
        scope.launch {
            val itemKey = fieldItemKey(key)
            if (listState.scrollToKey(keys, itemKey)) {
                withTimeoutOrNull(2_000) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == itemKey } }.first { it }
                }
                runCatching { focus[key].requestFocus() }
            }
        }
    }
    val onLeave: (String) -> Unit = { key -> if (key !in touched) touched = touched + key }
    val notAffiliated = stringResource(R.string.guide_not_affiliated)
    val submitSelf = stringResource(R.string.country_submit_self)
    val missingSentence = recipe?.let { r ->
        FormValues.missingRequired(r, ui.values).takeIf { it.isNotEmpty() }?.joinToString(", ") { it.labels.ko }
    }?.let { stringResource(R.string.form_need_required, it) }
    val confirmYes = stringResource(R.string.form_confirm_yes)
    val fixLabel = stringResource(if (editing) R.string.form_fix_done else R.string.form_confirm_fix)
    // '수동 모드' 대신 쉬운 말(도착 화면 제목과 같은 `값 복사해서 넣기`, R18)
    val manualLabel = stringResource(R.string.form_manual_open)

    AppScreen(
        title = stringResource(R.string.form_confirm_title, formName),
        subtitle = stringResource(R.string.form_confirm_body),
        speech = stringResource(R.string.form_confirm_body),
        state = listState,
        keyIndex = keys,
    ) {
        // 정부 비제휴 + 제출은 직접 — 입국 화면의 첫 정보 항목 (원칙 5, 4.4)
        item(key = "not-affiliated") {
            NoticeBanner(notAffiliated, icon = Icons.Outlined.Policy, secondLine = submitSelf, secondIcon = Icons.Outlined.TouchApp)
        }
        // 보안 화면(SecureScreen): 비제휴 고지 바로 다음에 '이 휴대폰에만' 한 줄 (6장 공통 보안)
        item(key = "security") { SecurityBanner(compact = true) }
        if (ctx == null) return@AppScreen
        if (!ctx.autofillAvailable) {
            item(key = "no-autofill") {
                Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                    NoticeBanner(
                        stringResource(if (ctx.killed) R.string.form_killed else R.string.form_no_recipe),
                        icon = if (ctx.killed) Icons.Outlined.PauseCircle else Icons.Outlined.Schedule,
                        tone = BannerTone.Caution,
                    )
                    PrimaryButton(
                        manualLabel,
                        onClick = onManual,
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    )
                }
            }
            if (recipe == null) return@AppScreen
        }
        when (val w = ui.wallet) {
            is WalletState.Unlocked -> if (w.contents.passport == null) {
                item(key = "need-passport") {
                    CardNewsCard(
                        title = stringResource(R.string.form_need_passport),
                        icon = Icons.Outlined.Badge,
                        body = stringResource(R.string.form_need_passport_body),
                    ) {
                        PrimaryButton(stringResource(R.string.wallet_passport_add), onClick = onRegisterPassport)
                    }
                }
                return@AppScreen
            }
            else -> {
                item(key = "locked") {
                    LockedState(
                        title = stringResource(R.string.wallet_locked_title),
                        body = stringResource(R.string.form_locked_body),
                        buttonLabel = stringResource(R.string.wallet_unlock),
                        onUnlock = onUnlock,
                    )
                }
                return@AppScreen
            }
        }
        recipe!!

        // ③ 현지어 크게: 켬·끔 상태가 있는 토글 (SelectChip singleChoice = false → Checkbox 역할)
        item(key = "local-large") {
            SelectChip(
                selected = localLarge,
                onClick = { localLarge = !localLarge },
                label = stringResource(R.string.form_local_large),
                leadingIcon = Icons.Outlined.Translate,
                singleChoice = false,
            )
        }

        // 서류에서 온 값: 출처별로 묶어 한 번에 확인 (PRD 6.2)
        val bulk = recipe.fields.filter { it.confirm == "bulk" && ui.values[it.key]?.isEmpty == false }
        // 고르거나 적는 값 + 서류에 없던 값: 하나씩
        val individual = recipe.fields.filter { it.confirm == "individual" || ui.values[it.key]?.isEmpty != false }
        val missing = FormValues.missingRequired(recipe, ui.values)
        val missingKeys = missing.mapTo(HashSet()) { it.key }
        val touchedKeys = touched.toHashSet()

        // ④ 빈칸 요약은 맨 위 한 곳 (R16): 개수 + 느낌표 표시 설명 + 첫 빈칸으로 가기 + 빈칸 이름(펼침, 누르면 그 칸으로)
        item(key = "blank-summary") { BlankSummary(missing, missingSentence, attempted, goToField) }

        val groups = OriginOrder.mapNotNull { origin ->
            bulk.filter { groupOf(ui.values.getValue(it.key).origin) == origin }.takeIf { it.isNotEmpty() }?.let { origin to it }
        }
        if (groups.isNotEmpty()) {
            item(key = "documents-title") {
                SectionHeader(
                    stringResource(R.string.form_from_documents),
                    icon = Icons.Outlined.Description,
                    subtitle = stringResource(R.string.form_documents_check),
                )
            }
            groups.forEach { (origin, fields) ->
                item(key = "group-${origin.name.lowercase()}") {
                    OriginGroupCard(origin, fields, ui, editing, localLarge, onSetValue)
                }
            }
        }

        if (individual.isNotEmpty()) {
            sectionGap("gap-individual")
            item(key = "individual") {
                CardSegment(SegmentPosition.Top) { IndividualHead(individual.size) }
            }
            individual.forEachIndexed { i, f ->
                item(key = fieldItemKey(f.key)) {
                    val required = f.key in missingKeys
                    val state = FieldState(required = required, error = required && (attempted || f.key in touchedKeys))
                    CardSegment(if (i == individual.lastIndex) SegmentPosition.Bottom else SegmentPosition.Middle) {
                        FieldInput(f, recipe, ui, localLarge, state, focus[f.key], { onLeave(f.key) }, onSetValue)
                    }
                }
            }
        }

        // ⑦ 보안 확인과 마지막 '제출'은 직접 — 버튼 바로 위 (누를 수 없는 흰 띠)
        item(key = "notice") {
            NoticeBanner(stringResource(R.string.form_confirm_notice), icon = Icons.Outlined.TouchApp)
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                if (ui.saveFailed) {
                    NoticeBanner(stringResource(R.string.form_save_failed), icon = Icons.Outlined.ErrorOutline, tone = BannerTone.Danger)
                }
                // 비활성 버튼 바로 위에 이유 한 줄(D9) — 빈칸 개수·이름은 맨 위 요약 한 곳에만 (R16: 같은 경고를 되풀이하지 않는다)
                if (ctx.autofillAvailable && missing.isNotEmpty()) {
                    IconBullet(stringResource(R.string.form_confirm_disabled_reason), Icons.Outlined.Info)
                }
                if (ctx.autofillAvailable) {
                    PrimaryButton(
                        confirmYes,
                        onClick = onConfirm,
                        enabled = missing.isEmpty(),
                        icon = Icons.Outlined.EditNote,
                    )
                }
                SecondaryButton(
                    fixLabel,
                    onClick = { editing = !editing },
                    icon = if (editing) Icons.Outlined.Check else Icons.Outlined.Edit,
                )
                // 자동 입력을 쉴 때는 위 안내 카드의 주 버튼이 수동 모드다
                if (ctx.autofillAvailable) {
                    QuietButton(
                        manualLabel,
                        onClick = onManual,
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    )
                }
            }
        }
    }
}

private fun fieldItemKey(key: String) = "field-$key"

/** 직접 입력 칸마다 초점 (빈칸으로 가기) */
private class FieldFocus {
    private val map = HashMap<String, FocusRequester>()
    operator fun get(key: String): FocusRequester = map.getOrPut(key) { FocusRequester() }
}

/** 칸 상태: [required] = 필수인데 비었음, [error] = 그 빈칸을 오류로 보일 때(빈칸으로 가기를 눌렀거나 칸을 거쳐 나감) */
private data class FieldState(val required: Boolean, val error: Boolean)

/** 빈칸 표시 색: 손대기 전에는 할 일(주의 글자색), 그 뒤에는 오류(위험 글자색) — 공용 RequiredMark와 같은 규칙 */
private fun blankColor(error: Boolean) = requiredMarkColor(error)

/** 빈칸 느낌표 (요약 설명·칸 끝 공용 RequiredMark와 같은 그림) */
private val BlankIcon = RequiredIcon

/**
 * '목록에서 골라 주세요'처럼 공식 사이트 칸의 동작을 설명하는 도움말이면 앞에 `공식 사이트에서는`을 붙인다 (R16) —
 * 앱 칸에는 목록이 없어 사용자가 멈췄다(UX 검토 5번). 팩 문장은 그대로 두고 앞에만 붙인다.
 */
@Composable
private fun appHint(hint: String?): String? = when {
    hint == null -> null
    hint.contains(SITE_LIST_WORD) -> stringResource(R.string.form_hint_on_site, hint)
    else -> hint
}

private const val SITE_LIST_WORD = "목록"

// ---------------- ④ 서류에서 가져온 값: 출처별 그룹 ----------------

/** 그룹 순서: 여권 → 항공권 → 예약 확인서 → 직접 입력 (그룹 안은 레시피 순서) */
private val OriginOrder = listOf(ValueOrigin.Passport, ValueOrigin.Flight, ValueOrigin.Lodging, ValueOrigin.User)

private fun groupOf(origin: ValueOrigin): ValueOrigin = if (origin == ValueOrigin.None) ValueOrigin.User else origin

private fun originLabel(origin: ValueOrigin): Int = when (origin) {
    ValueOrigin.Passport -> R.string.form_origin_passport
    ValueOrigin.Flight -> R.string.form_origin_flight
    ValueOrigin.Lodging -> R.string.form_origin_lodging
    ValueOrigin.User, ValueOrigin.None -> R.string.form_origin_user
}

/**
 * 그룹 배지는 모두 연한 톤 — 누를 수 없는 머리라 화면에서 가장 무거운 요소가 되지 않게(Navy는 보안 띠에만).
 * 여권 = Teal, 항공권 = 이동(Violet), 숙소 = 일반 안내(Accent), 직접 입력 = 기타(Neutral)
 */
private fun originTone(origin: ValueOrigin): BadgeTone = when (origin) {
    ValueOrigin.Passport -> BadgeTone.Teal
    ValueOrigin.Flight -> BadgeTone.Violet
    ValueOrigin.Lodging -> BadgeTone.Accent
    ValueOrigin.User, ValueOrigin.None -> BadgeTone.Neutral
}

/**
 * 그룹 카드: 머리(배지 + '여권에서' + `6칸`) → 접힘(기본): 값만 한 줄로(`ERIKSSON · ANNA MARIA · …` — 훑어 확인) + `여권에서 가져온 6칸 펼쳐 보기`
 * → 펼침: 라벨-값 행(칸 이름 3개 국어 + 값). 고치는 중이면 늘 펼친다. 행마다 붙던 출처 칩은 머리 한 곳으로 모았다 (R16 — 화면 길이 줄이기).
 */
@Composable
private fun OriginGroupCard(
    origin: ValueOrigin,
    fields: List<RecipeField>,
    ui: ConfirmUi,
    editing: Boolean,
    localLarge: Boolean,
    onSetValue: (String, String) -> Unit,
) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.large
    var open by rememberSaveable(origin) { mutableStateOf(false) }
    val originName = stringResource(originLabel(origin))
    val rows: @Composable () -> Unit = {
        Column {
            fields.forEach { f ->
                HorizontalDivider(thickness = 1.dp, color = Tokens.LineSoft)
                val v = ui.values.getValue(f.key)
                if (editing && v.origin != ValueOrigin.None) {
                    Box(Modifier.padding(vertical = 8.dp)) {
                        FieldTextField(
                            f, ui.draft[f.key] ?: v.display.orEmpty(), localLarge, FieldState(required = false, error = false),
                            focusRequester = null, onLeave = {}, onSetValue = onSetValue,
                        )
                    }
                } else {
                    ValueRow(f, v, localLarge)
                }
            }
        }
    }
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().cardShadow(shape),
    ) {
        Column(Modifier.padding(dimens.cardPadding)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(IconKeys.formOrigin(origin), tone = originTone(origin))
                    // 그룹 머리는 아래 값(titleMedium Bold)보다 한 단계 위 — titleLarge
                    Text(
                        originName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Tokens.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                CountPill(stringResource(R.string.form_group_count, fields.size), Modifier.align(Alignment.CenterVertically))
            }
            if (editing) {
                rows()
            } else {
                if (!open) {
                    // 접힌 동안 값만 한 줄로 — 칸 이름 없이도 훑어 확인할 수 있게(이름·번호·날짜). 펼치면 숨긴다.
                    // 값 하나(`여 → FEMALE`, `1974-08-12`)는 줄 사이에서 쪼개지 않는다(보이는 글자에서만, TalkBack은 원문)
                    val values = fields.map { ui.values.getValue(it.key).display ?: "—" }
                    KoText(
                        values.joinToString(" · "),
                        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        Modifier.padding(bottom = 4.dp),
                        color = Tokens.Ink,
                        display = koDisplay(values.joinToString(" · ") { keepTogether(it) }),
                    )
                }
                ExpandableDetail(
                    open = open,
                    onOpenChange = { open = it },
                    label = if (origin == ValueOrigin.User) {
                        stringResource(R.string.form_group_more_user, fields.size)
                    } else {
                        stringResource(R.string.form_group_more, originName, fields.size)
                    },
                    target = if (origin == ValueOrigin.User) {
                        stringResource(R.string.fold_target_group_user)
                    } else {
                        stringResource(R.string.fold_target_group, originName)
                    },
                ) { rows() }
            }
        }
    }
}

/** 칸 개수(`6칸`): 상태가 아니라 셈이라 아이콘 없는 중립 태그 */
@Composable
private fun CountPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 28.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(Tokens.SurfaceSunken)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Tokens.InkSecondary)
    }
}

/** 영어·현지어 칸 이름 ("Family Name · นามสกุล") */
private fun RecipeField.otherLabels(): String? = listOfNotNull(labels.en, labels.local).joinToString(" · ").takeIf { it.isNotEmpty() }

/** 라벨-값 한 덩어리 = 공용 KeyValueRow(재검토 R3). '현지어 크게'를 켜면 영어·현지어 칸 이름을 크게(subLabelStyle — 기존 기능 유지) */
@Composable
private fun ValueRow(f: RecipeField, v: FieldValue, localLarge: Boolean) {
    val value = v.display ?: stringResource(R.string.form_empty_value)
    KeyValueRow(
        label = f.labels.ko,
        value = value,
        subLabel = f.otherLabels(),
        subLabelStyle = if (localLarge) MaterialTheme.typography.headlineMedium else null,
    )
}

// ---------------- ⑤ 직접 고를 칸: 이어진 한 장의 카드 ----------------

private enum class SegmentPosition { Top, Middle, Bottom }

/**
 * 칸마다 lazy item으로 나눈 카드 조각. 흰 바탕(그림자 없음 — 조각마다 그림자를 그리면 이음매에 줄이 생긴다).
 * 왼쪽 주의 막대는 뺐다: 노란 신호는 실제 빈칸에만 (R16).
 * 위·가운데 조각은 아래쪽 [gap]만큼을 목록 간격 자리에 겹쳐 그려(높이는 gap만큼 작게 알림) 다음 조각과 이음매 없이 한 장으로 보인다.
 * 겹치는 자리는 조각의 아래 여백이라 누르는 요소는 언제나 조각 안에 있다.
 */
@Composable
private fun CardSegment(position: SegmentPosition, content: @Composable ColumnScope.() -> Unit) {
    val dimens = LocalDimens.current
    val large = MaterialTheme.shapes.large
    val shape = when (position) {
        SegmentPosition.Top -> large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
        SegmentPosition.Middle -> RectangleShape
        SegmentPosition.Bottom -> large.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)
    }
    val joinsNext = position != SegmentPosition.Bottom
    Column(
        Modifier
            .then(if (joinsNext) Modifier.overlapNextGap(dimens.gap) else Modifier)
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.Surface)
            .padding(
                start = dimens.cardPadding,
                end = dimens.cardPadding,
                top = if (position == SegmentPosition.Top) dimens.cardPadding else 0.dp,
                bottom = if (joinsNext) dimens.gap else dimens.cardPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(dimens.inner),
        content = content,
    )
}

/** 실제 높이보다 [gap]만큼 작게 알린다 — LazyColumn이 붙이는 간격(gap) 자리에 이 item의 아래 여백이 그려진다 */
private fun Modifier.overlapNextGap(gap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val overlap = gap.roundToPx().coerceIn(0, placeable.height)
    layout(placeable.width, placeable.height - overlap) { placeable.placeRelative(0, 0) }
}

/**
 * 직접 고를 칸 머리: 배지 + `직접 골라 주세요` + 칸 수(서류 그룹 머리와 같은 중립 셈 태그, 같은 자리 — 제목 옆, 모자라면 아래 줄).
 * 빈칸 개수 태그는 맨 위 요약 한 곳에만 둔다(R16). 큰 글자는 배지를 위로 올려 제목에 폭 전체를 준다.
 */
@Composable
private fun IndividualHead(fieldCount: Int) {
    BadgeTitleLayout(
        badge = { IconBadge(Icons.Outlined.EditNote, tone = BadgeTone.Accent) },
        stack = isStackedLayout(),
        gap = 12.dp,
        title = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KoText(
                    stringResource(R.string.form_choose_yourself),
                    MaterialTheme.typography.titleLarge,
                    Modifier.align(Alignment.CenterVertically),
                    color = Tokens.Ink,
                    glueShort = true,
                    heading = true,
                )
                CountPill(stringResource(R.string.form_group_count, fieldCount), Modifier.align(Alignment.CenterVertically))
            }
        },
    )
}

@Composable
private fun FieldInput(
    f: RecipeField,
    recipe: Recipe,
    ui: ConfirmUi,
    localLarge: Boolean,
    state: FieldState,
    focusRequester: FocusRequester,
    onLeave: () -> Unit,
    onSetValue: (String, String) -> Unit,
) {
    val options = f.optionsRef?.let { recipe.options[it] }
    if (options != null) {
        ChoiceField(f, options, ui.draft[f.key], localLarge, state, focusRequester) { onSetValue(f.key, it) }
    } else {
        FieldTextField(f, ui.draft[f.key].orEmpty(), localLarge, state, focusRequester, onLeave, onSetValue)
    }
}

/**
 * 직접 적는 칸: 이름은 입력칸 label(TalkBack 이름), 도움말·영어·현지어 라벨은 supportingText(6-17).
 * 필수인데 비었으면 칸 오른쪽에 작은 느낌표 하나(R16 — `꼭 채워요` 태그를 칸마다 되풀이하지 않는다) + TalkBack 상태 `빈칸`.
 * 손대기 전에는 주의 색, 빈칸으로 가기를 눌렀거나 칸을 거쳐 나간 뒤에는 오류(빨간 테두리·느낌표).
 * 공식 사이트 목록을 설명하는 도움말은 `공식 사이트에서는 …`으로(앱 칸에는 목록이 없다). '현지어 크게'를 켜면 supportingText가 커진다.
 * 도움말 조각은 ` · `로 잇되 구분점이 줄 맨 앞에 오지 않게 앞 낱말에 붙여 보인다(TalkBack은 원문).
 */
@Composable
private fun FieldTextField(
    f: RecipeField,
    value: String,
    localLarge: Boolean,
    state: FieldState,
    focusRequester: FocusRequester?,
    onLeave: () -> Unit,
    onSetValue: (String, String) -> Unit,
) {
    val parts = listOfNotNull(appHint(f.hintKo), f.labels.en, f.labels.local)
    val support = parts.joinToString(" · ")
    val supportShown = keepWords(parts.joinToString("${KoreanBreak.NBSP}· "))
    val blank = stringResource(R.string.form_blank_cd)
    var hadFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onSetValue(f.key, it) },
        singleLine = true,
        label = { KoText(f.labels.ko, LocalTextStyle.current) },
        supportingText = if (support.isNotEmpty()) {
            {
                KoText(
                    support,
                    if (localLarge) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodySmall,
                    display = supportShown,
                )
            }
        } else {
            null
        },
        isError = state.error,
        trailingIcon = if (state.required) {
            { Icon(BlankIcon, contentDescription = null, tint = blankColor(state.error), modifier = Modifier.size(textIconSize(LocalDimens.current.icon))) }
        } else {
            null
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Tokens.Surface,
            unfocusedContainerColor = Tokens.Surface,
            errorContainerColor = Tokens.Surface,
            focusedBorderColor = Tokens.Accent,
            unfocusedBorderColor = Tokens.LineStrong,
            errorBorderColor = Tokens.DangerText,
            focusedLabelColor = Tokens.Accent,
            unfocusedLabelColor = Tokens.InkSecondary,
            errorLabelColor = Tokens.Ink,
            focusedSupportingTextColor = Tokens.InkSecondary,
            unfocusedSupportingTextColor = Tokens.InkSecondary,
            errorSupportingTextColor = Tokens.InkSecondary,
            errorTrailingIconColor = Tokens.DangerText,
            cursorColor = Tokens.Accent,
            errorCursorColor = Tokens.DangerText,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (state.required) Modifier.semantics { stateDescription = blank } else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                // 칸을 한 번 거쳐 나가면(초점을 얻었다 잃음) 그 칸의 빈칸을 오류로 보인다
                if (it.isFocused) {
                    hadFocus = true
                } else if (hadFocus) {
                    hadFocus = false
                    onLeave()
                }
            },
    )
}

/**
 * 고르는 칸(여행 목적·숙소 종류): 폭 전체 선택 행 = 공용 SelectableCard(재검토 R2 규칙 ② — 선택 AccentSoft + 2dp Accent + CheckCircle,
 * 비선택 흰 바탕 + 1dp LineStrong + 빈 원). 그룹은 selectableGroup, 행은 Role.RadioButton.
 */
@Composable
private fun ChoiceField(
    f: RecipeField,
    options: List<RecipeOption>,
    selected: String?,
    localLarge: Boolean,
    state: FieldState,
    focusRequester: FocusRequester,
    onSelect: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    // 1열(쉬운 모드·큰 글자)은 현지어를 다음 줄로 — `ท่อง/เที่ยว`처럼 현지어 낱말 안에서 꺾이지 않게
    val localOwnLine = rememberGridColumns() == 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val labelStyle = MaterialTheme.typography.titleMedium
                KoText(f.labels.ko, labelStyle, Modifier.weight(1f, fill = false), color = Tokens.Ink)
                // 빈 필수 칸 = 이름 뒤 작은 느낌표 하나 (R16 — 글 칸의 칸 안 느낌표와 같은 그림·같은 색 규칙)
                if (state.required) RequiredMark(style = labelStyle, error = state.error, base = LocalDimens.current.icon)
            }
            f.otherLabels()?.let {
                Text(
                    it,
                    style = if (localLarge) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodySmall,
                    color = if (localLarge) Tokens.Ink else Tokens.InkSecondary,
                )
            }
        }
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o ->
                val sel = selected == o.value
                // 매핑 없는 값은 아이콘 없음 (옆 선택 표시와 원이 둘로 보이지 않게, 5.7)
                val optionIcon = IconKeys.option(o.value)
                SelectableCard(
                    selected = sel,
                    onClick = { onSelect(o.value) },
                    modifier = if (i == 0) Modifier.focusRequester(focusRequester) else Modifier,
                    leading = optionIcon?.let { icon ->
                        { Icon(icon, contentDescription = null, tint = selectionIconTint(sel), modifier = Modifier.size(dimens.icon)) }
                    },
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    OptionText(o, localLarge, localOwnLine)
                }
            }
        }
    }
}

/**
 * "관광 · Tourism · ท่องเที่ยว" 한 덩어리 그대로(한 Text 노드로 읽힘, 누르면 선택 — 테스트가 찾는 글자).
 * '현지어 크게'면 현지어 부분만 크게. [ownLine]이면 현지어를 다음 줄에 보인다(TalkBack은 원래 한 줄 문장).
 */
@Composable
private fun OptionText(o: RecipeOption, localLarge: Boolean, ownLine: Boolean, modifier: Modifier = Modifier) {
    val head = listOfNotNull(o.ko, o.en).joinToString(" · ")
    val original = if (o.local != null) "$head · ${o.local}" else head
    val split = ownLine && o.local != null
    val shown = buildAnnotatedString {
        // 한국어 부분만 줄바꿈 보정(어절 단위 + 긴 낱말의 가운뎃점 뒤, `아파트·레/지던스` 방지) — 의미 글자는 아래에서 원문
        append(koDisplay(head))
        if (o.local != null) {
            append(if (split) "\n" else " · ")
            if (localLarge) {
                withStyle(SpanStyle(fontSize = MaterialTheme.typography.headlineMedium.fontSize, fontWeight = FontWeight.SemiBold)) { append(o.local) }
            } else {
                append(o.local)
            }
        }
    }
    Text(
        shown,
        style = MaterialTheme.typography.bodyLarge,
        color = Tokens.Ink,
        modifier = modifier.then(if (shown.text == original) Modifier else Modifier.clearAndSetSemantics { text = AnnotatedString(original) }),
    )
}

// ---------------- ④ 빈칸 요약 (맨 위 한 곳) ----------------

/**
 * 빈칸 요약 (R16): 맨 위 한 곳에만. 흰 바탕 + 왼쪽 주의 막대 — 노란 신호는 실제 빈칸이 있을 때만.
 * 느낌표 + `빈칸 8개 남았어요` → 느낌표 표시 설명 한 줄 → `첫 빈칸으로 가기` → `빈칸 8곳 이름 보기`(펼치면 칸 이름 줄, 누르면 그 칸으로).
 * TalkBack: 제목은 `빈칸을 채워 주세요: …` 문장을 liveRegion(Polite)으로 — 빈칸 목록이 바뀔 때만 다시 알린다.
 * 빈칸이 없으면 초록 체크 한 줄.
 */
@Composable
private fun BlankSummary(missing: List<RecipeField>, sentence: String?, attempted: Boolean, onGo: (String) -> Unit) {
    val dimens = LocalDimens.current
    if (missing.isEmpty() || sentence == null) {
        IconBullet(stringResource(R.string.form_all_filled), Icons.Outlined.CheckCircle, tone = BadgeTone.Success)
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Tokens.Surface)
            .startBar(Tokens.CautionBorder)
            .padding(dimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.inner),
    ) {
        val titleStyle = MaterialTheme.typography.titleLarge
        val iconSize = textIconSize(dimens.icon, titleStyle)
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                BlankIcon,
                contentDescription = null,
                tint = blankColor(attempted),
                modifier = Modifier.padding(top = firstLineIconOffset(titleStyle, iconSize)).size(iconSize),
            )
            KoText(
                stringResource(R.string.form_missing_count, missing.size),
                titleStyle,
                Modifier.semantics {
                    contentDescription = sentence
                    liveRegion = LiveRegionMode.Polite
                },
                color = Tokens.Ink,
                glueShort = true,
            )
        }
        KoText(stringResource(R.string.form_blank_summary_body), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
        // 이 화면 안 아래로 이동 = ArrowDownward (버튼 앞 꺾쇠 금지, 재검토 R11)
        SecondaryButton(
            stringResource(R.string.form_go_first_missing),
            onClick = { onGo(missing.first().key) },
            icon = Icons.Outlined.ArrowDownward,
            modifier = Modifier.padding(top = 4.dp),
        )
        ExpandableDetail(label = stringResource(R.string.form_blank_names, missing.size), target = stringResource(R.string.fold_target_blank_names)) {
            Column {
                missing.forEachIndexed { i, f ->
                    if (i > 0) HorizontalDivider(thickness = 1.dp, color = Tokens.LineSoft)
                    MissingFieldRow(f.labels.ko, attempted) { onGo(f.key) }
                }
            }
        }
    }
}

/** 1열 빈칸 목록 한 줄: 폭 전체, minTouch, Role.Button, `직업 (영문) 칸으로 가기` — 칸 이름 + 오른쪽 셰브론 */
@Composable
private fun MissingFieldRow(label: String, attempted: Boolean, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val goLabel = stringResource(R.string.form_go_field_cd, label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minTouch()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = goLabel, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            BlankIcon,
            contentDescription = null,
            tint = blankColor(attempted),
            modifier = Modifier.size(textIconSize(dimens.iconSmall)),
        )
        KoText(label, MaterialTheme.typography.labelLarge, Modifier.weight(1f), color = Tokens.Ink)
        Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(dimens.icon))
    }
}
