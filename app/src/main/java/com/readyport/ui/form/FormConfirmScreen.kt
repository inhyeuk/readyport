package com.readyport.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CorporateFare
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Forum
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.ExpandableDetail
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.KeyValueRow
import com.readyport.ui.components.LockedState
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.rememberKeyIndex
import com.readyport.ui.components.scrollToKey
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.startBar
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
 * 17 입국 카드 확인 (DESIGN_SPEC 6-17).
 * ① 정부 비제휴(첫 정보 항목) ② 보안 한 줄 ③ 현지어 크게(토글) ④ 서류에서 가져온 값 — 출처별 그룹 카드(KeyValueRow)
 * ⑤ 직접 고를 칸 — 흰 바탕 + 주의 막대 한 장, 칸마다 lazy item(`field-<key>`)이라 빈칸으로 바로 갈 수 있다
 * ⑥ 원어민 검수 전 ⑦ 제출은 직접 ⑧ 남은 빈칸(누르면 그 칸으로) ⑨ 맞아요(빈칸이 있으면 비활성 — D9) ⑩ 고치기 ⑪ 수동 모드.
 *
 * 빈 필수 칸 표시(E 검토 반영 — 스펙 6-17 `isError = 필수이고 비었음`의 보완 제안):
 * 처음 열었을 때는 '할 일'로 차분하게(주의 색 `꼭 채워요` 태그 + 보통 테두리) 보이고, 빈칸 태그·'첫 빈칸으로 가기'를 누르거나
 * 칸을 한 번 거쳐 나가면 그때 오류(빨간 테두리·아이콘·태그)로 바뀐다 — 손대기 전부터 화면이 빨간 오류로 덮이지 않게.
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
    val singleColumn = rememberGridColumns() == 1
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
    val manualLabel = stringResource(R.string.form_manual_mode)

    AppScreen(
        title = KoBreak.display(stringResource(R.string.form_confirm_title, formName)),
        // 공용 머리(AppScreen)가 그리는 제목·부제는 보이는 글자만 줄바꿈 보정(API 33 미만)
        subtitle = KoBreak.display(stringResource(R.string.form_confirm_body)),
        speech = stringResource(R.string.form_confirm_body),
        state = listState,
        keyIndex = keys,
    ) {
        // 정부 비제휴 + 제출은 직접 — 입국 화면의 첫 정보 항목 (원칙 5, 4.4)
        item(key = "not-affiliated") {
            KoNotice(notAffiliated, Icons.Outlined.Policy, secondLine = submitSelf, secondIcon = Icons.Outlined.TouchApp)
        }
        // 보안 화면(SecureScreen): 비제휴 고지 바로 다음에 '이 휴대폰에만' 한 줄 (6장 공통 보안)
        item(key = "security") { CompactSecurityLine() }
        if (ctx == null) return@AppScreen
        if (!ctx.autofillAvailable) {
            item(key = "no-autofill") {
                Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                    KoNotice(
                        stringResource(if (ctx.killed) R.string.form_killed else R.string.form_no_recipe),
                        if (ctx.killed) Icons.Outlined.PauseCircle else Icons.Outlined.Schedule,
                        tone = BannerTone.Caution,
                    )
                    val shown = KoBreak.display(manualLabel)
                    PrimaryButton(
                        shown,
                        onClick = onManual,
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        modifier = Modifier.koDescription(manualLabel, shown),
                    )
                }
            }
            if (recipe == null) return@AppScreen
        }
        when (val w = ui.wallet) {
            is WalletState.Unlocked -> if (w.contents.passport == null) {
                item(key = "need-passport") {
                    CardNewsCard(
                        title = KoBreak.display(stringResource(R.string.form_need_passport)),
                        icon = Icons.Outlined.Badge,
                        body = KoBreak.display(stringResource(R.string.form_need_passport_body)),
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

        val groups = OriginOrder.mapNotNull { origin ->
            bulk.filter { groupOf(ui.values.getValue(it.key).origin) == origin }.takeIf { it.isNotEmpty() }?.let { origin to it }
        }
        if (groups.isNotEmpty()) {
            item(key = "documents-title") {
                SectionHeader(stringResource(R.string.form_from_documents), icon = Icons.Outlined.Description)
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
                CardSegment(SegmentPosition.Top) { IndividualHead(missing.size, attempted) }
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

        if (!recipe.labelsReviewed) {
            item(key = "unreviewed") {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Outlined.Translate,
                        contentDescription = null,
                        tint = Tokens.InkTertiary,
                        modifier = Modifier.padding(top = 1.dp).size(LocalDimens.current.iconSmall),
                    )
                    KoText(stringResource(R.string.form_labels_unreviewed), MaterialTheme.typography.bodySmall, color = Tokens.InkTertiary)
                }
            }
        }
        // ⑦ 보안 확인과 마지막 '제출'은 직접 — 버튼 바로 위 (누를 수 없는 흰 띠)
        item(key = "notice") {
            KoNotice(stringResource(R.string.form_confirm_notice), Icons.Outlined.TouchApp)
        }
        if (missing.isNotEmpty() && missingSentence != null) {
            item(key = "missing") { MissingPanel(missing, missingSentence, attempted, singleColumn, goToField) }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                if (ui.saveFailed) {
                    KoNotice(stringResource(R.string.form_save_failed), Icons.Outlined.ErrorOutline, tone = BannerTone.Danger)
                }
                if (ctx.autofillAvailable) {
                    val shown = KoBreak.display(confirmYes)
                    PrimaryButton(
                        shown,
                        onClick = onConfirm,
                        enabled = missing.isEmpty(),
                        icon = Icons.Outlined.EditNote,
                        modifier = Modifier.koDescription(confirmYes, shown),
                    )
                }
                val fixShown = KoBreak.display(fixLabel)
                SecondaryButton(
                    fixShown,
                    onClick = { editing = !editing },
                    icon = if (editing) Icons.Outlined.Check else Icons.Outlined.Edit,
                    modifier = Modifier.koDescription(fixLabel, fixShown),
                )
                // 자동 입력을 쉴 때는 위 안내 카드의 주 버튼이 수동 모드다
                if (ctx.autofillAvailable) {
                    val manualShown = KoBreak.display(manualLabel)
                    QuietButton(
                        manualShown,
                        onClick = onManual,
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        modifier = Modifier.koDescription(manualLabel, manualShown),
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

/** 빈 필수 칸 표시 종류: 손대기 전에는 할 일(주의), 그 뒤에는 오류(위험) */
private fun requiredKind(error: Boolean): StatusKind = if (error) StatusKind.Required else StatusKind.Caution

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

/** 그룹 카드: 머리(배지 + '여권에서' + `6칸`) 아래 라벨-값 행. 행마다 붙던 출처 칩은 머리 한 곳으로 모았다 */
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
                        stringResource(originLabel(origin)),
                        style = MaterialTheme.typography.titleLarge,
                        color = Tokens.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                CountPill(stringResource(R.string.form_group_count, fields.size), Modifier.align(Alignment.CenterVertically))
            }
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

/** 라벨-값 한 덩어리. '현지어 크게'를 켜면 영어·현지어 칸 이름을 크게 (기존 기능 유지) */
@Composable
private fun ValueRow(f: RecipeField, v: FieldValue, localLarge: Boolean) {
    val value = v.display ?: stringResource(R.string.form_empty_value)
    if (!localLarge) {
        KeyValueRow(label = f.labels.ko, value = value, subLabel = f.otherLabels())
    } else {
        // KeyValueRow와 같은 구조(한 번에 읽힘)에 보조 라벨만 크게
        Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(f.labels.ko, style = MaterialTheme.typography.titleSmall, color = Tokens.InkSecondary)
            f.otherLabels()?.let { Text(it, style = MaterialTheme.typography.headlineMedium, color = Tokens.Ink) }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = Tokens.Ink,
            )
        }
    }
}

// ---------------- ⑤ 직접 고를 칸: 이어진 한 장의 카드 ----------------

private enum class SegmentPosition { Top, Middle, Bottom }

/**
 * 칸마다 lazy item으로 나눈 카드 조각. 흰 바탕 + 왼쪽 주의 막대(SurfaceCaution 모양, 그림자 없음).
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
            .startBar(Tokens.CautionBorder)
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

/** 직접 고를 칸 머리: 배지 + `직접 골라 주세요` + 남은 빈칸 태그. 큰 글자는 배지를 위로 올려 제목·태그에 폭 전체를 준다 */
@Composable
private fun IndividualHead(missingCount: Int, attempted: Boolean) {
    BadgeTitleLayout(
        badge = { IconBadge(Icons.Outlined.EditNote, tone = BadgeTone.Caution) },
        below = {
            Box(Modifier.padding(top = 4.dp)) {
                if (missingCount > 0) {
                    KoStatusTag(stringResource(R.string.form_missing_count, missingCount), requiredKind(attempted))
                } else {
                    KoStatusTag(stringResource(R.string.form_all_filled), StatusKind.Allowed)
                }
            }
        },
        stack = largeFont(),
        gap = 12.dp,
        title = {
            KoText(
                stringResource(R.string.form_choose_yourself),
                MaterialTheme.typography.titleLarge,
                color = Tokens.Ink,
                glueShort = true,
                heading = true,
            )
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
 * 필수인데 비었으면 supportingText 앞에 `꼭 채워요` 태그 — 손대기 전에는 주의 색, 빈칸으로 가기를 눌렀거나 칸을 거쳐 나간 뒤에는
 * 오류(빨간 테두리·아이콘·태그). '현지어 크게'를 켜면 supportingText가 커진다.
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
    val parts = listOfNotNull(f.hintKo, f.labels.en, f.labels.local)
    val support = parts.joinToString(" · ")
    val supportShown = KoBreak.display(parts.joinToString("${KoBreak.NBSP}· "))
    val requiredLabel = stringResource(R.string.form_field_required)
    var hadFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onSetValue(f.key, it) },
        singleLine = true,
        label = { KoText(f.labels.ko, LocalTextStyle.current) },
        supportingText = if (support.isNotEmpty() || state.required) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.required) KoStatusTag(requiredLabel, requiredKind(state.error))
                    if (support.isNotEmpty()) {
                        KoText(
                            support,
                            if (localLarge) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodySmall,
                            display = supportShown,
                        )
                    }
                }
            }
        } else {
            null
        },
        isError = state.error,
        trailingIcon = if (state.error) {
            { Icon(Icons.Outlined.ErrorOutline, contentDescription = null) }
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
 * 라디오 행 아이콘 (5.7 IconKeys.option). 선 아이콘 목록 안에서 채운 모양으로 보이던 Work·Groups·Apartment만
 * 선 모양 아이콘(BusinessCenter·Forum·CorporateFare)으로 바꿔 굵기를 맞춘다.
 */
private fun optionIcon(value: String): ImageVector? = when (value) {
    "business" -> Icons.Outlined.BusinessCenter
    "meeting" -> Icons.Outlined.Forum
    "apartment" -> Icons.Outlined.CorporateFare
    else -> IconKeys.option(value)
}

/** 고르는 칸(여행 목적·숙소 종류): 폭 전체 라디오 행. 그룹은 selectableGroup, 행은 Role.RadioButton, RadioButton 콜백은 null */
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KoText(f.labels.ko, MaterialTheme.typography.titleMedium, Modifier.align(Alignment.CenterVertically), color = Tokens.Ink)
                if (state.required) {
                    KoStatusTag(stringResource(R.string.form_field_required), requiredKind(state.error), Modifier.align(Alignment.CenterVertically))
                }
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
                val shape = MaterialTheme.shapes.small
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minTouch()
                        .then(if (i == 0) Modifier.focusRequester(focusRequester) else Modifier)
                        .clip(shape)
                        .background(if (sel) Tokens.AccentSoft else Tokens.Surface)
                        .border(if (sel) 2.dp else 1.dp, if (sel) Tokens.Accent else Tokens.LineStrong, shape)
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { onSelect(o.value) })
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 매핑 없는 값은 아이콘 없음 (옆 RadioButton과 원이 둘로 보이지 않게, 5.7)
                    optionIcon(o.value)?.let {
                        Icon(it, contentDescription = null, tint = if (sel) Tokens.Accent else Tokens.InkSecondary, modifier = Modifier.size(dimens.icon))
                    }
                    OptionText(o, localLarge, localOwnLine, Modifier.weight(1f))
                    RadioButton(
                        selected = sel,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = Tokens.Accent, unselectedColor = Tokens.LineStrong),
                    )
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
    val shownHead = KoBreak.display(head)
    val split = ownLine && o.local != null
    val shown = buildAnnotatedString {
        append(shownHead)
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

// ---------------- ⑧ 남은 빈칸 ----------------

/** 1열에서 바로 보이는 빈칸 줄 수 — 나머지는 '나머지 빈칸 N개 보기' 안 */
private const val MISSING_LIST_CAP = 4

/**
 * 남은 빈칸: 개수 태그 + 칸 이름(누르면 그 칸으로) + '첫 빈칸으로 가기'.
 * 넓은 화면은 칸 이름 태그(FlowRow), 1열(쉬운 모드·큰 글자)은 폭 전체 목록(앞 4개 + 나머지 펼침) — 어느 칸이 비었는지 늘 보인다(⑧).
 * TalkBack에는 `빈칸을 채워 주세요: …` 문장을 liveRegion(Polite)으로 — 빈칸 목록이 바뀔 때만 다시 알린다.
 */
@Composable
private fun MissingPanel(missing: List<RecipeField>, sentence: String, attempted: Boolean, singleColumn: Boolean, onGo: (String) -> Unit) {
    val dimens = LocalDimens.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Tokens.Surface)
            .startBar(Tokens.CautionBorder)
            .padding(dimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.inner + 4.dp),
    ) {
        Box(
            Modifier.clearAndSetSemantics {
                contentDescription = sentence
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            StatusTagShown(stringResource(R.string.form_missing_count, missing.size), requiredKind(attempted))
        }
        if (singleColumn) {
            Column {
                missing.take(MISSING_LIST_CAP).forEachIndexed { i, f ->
                    if (i > 0) HorizontalDivider(thickness = 1.dp, color = Tokens.LineSoft)
                    MissingFieldRow(f.labels.ko, attempted) { onGo(f.key) }
                }
                val rest = missing.drop(MISSING_LIST_CAP)
                if (rest.isNotEmpty()) {
                    ExpandableDetail(label = stringResource(R.string.form_missing_more, rest.size)) {
                        Column {
                            rest.forEachIndexed { i, f ->
                                if (i > 0) HorizontalDivider(thickness = 1.dp, color = Tokens.LineSoft)
                                MissingFieldRow(f.labels.ko, attempted) { onGo(f.key) }
                            }
                        }
                    }
                }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                missing.forEach { f -> MissingFieldChip(f.labels.ko, attempted) { onGo(f.key) } }
            }
        }
        val goFirst = stringResource(R.string.form_go_first_missing)
        val goFirstShown = KoBreak.display(goFirst)
        SecondaryButton(
            goFirstShown,
            onClick = { onGo(missing.first().key) },
            icon = Icons.AutoMirrored.Outlined.NavigateNext,
            modifier = Modifier.koDescription(goFirst, goFirstShown),
        )
    }
}

/** 개수 태그 — 부모(liveRegion 문장)가 글자를 덮으므로 보이는 글자만 보정한다 */
@Composable
private fun StatusTagShown(text: String, kind: StatusKind) {
    com.readyport.ui.components.StatusTag(KoBreak.display(text), kind)
}

/** 누를 수 있는 빈칸 이름 태그 (StatusTag는 누를 수 없으므로 쓰지 않는다): minTouch, Role.Button, `직업 (영문) 칸으로 가기` */
@Composable
private fun MissingFieldChip(label: String, attempted: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    val goLabel = stringResource(R.string.form_go_field_cd, label)
    Row(
        modifier = Modifier
            .minTouch()
            .clip(shape)
            .background(Tokens.Surface)
            .border(1.dp, Tokens.LineStrong, shape)
            .clickable(role = Role.Button, onClickLabel = goLabel, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (attempted) Tokens.DangerText else Tokens.CautionText,
            modifier = Modifier.size(LocalDimens.current.iconSmall),
        )
        KoText(label, MaterialTheme.typography.labelLarge, color = Tokens.Ink)
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
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (attempted) Tokens.DangerText else Tokens.CautionText,
            modifier = Modifier.size(dimens.iconSmall),
        )
        KoText(label, MaterialTheme.typography.labelLarge, Modifier.weight(1f), color = Tokens.Ink)
        Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null, tint = Tokens.Accent, modifier = Modifier.size(dimens.icon))
    }
}
