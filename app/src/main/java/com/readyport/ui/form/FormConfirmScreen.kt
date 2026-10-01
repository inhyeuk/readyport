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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.KeyValueRow
import com.readyport.ui.components.LockedState
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.SecurityBanner
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
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
    val formName = ctx?.form?.nameKo ?: ctx?.formId.orEmpty()
    val listState = rememberLazyListState()
    val keys = rememberKeyIndex()
    val scope = rememberCoroutineScope()
    val focus = remember { FieldFocus() }
    // 빈칸 태그·'첫 빈칸으로 가기': 그 칸으로 스크롤한 뒤 그려진 것을 확인하고 초점을 준다 (4.1)
    val goToField: (String) -> Unit = { key ->
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
    val notAffiliated = stringResource(R.string.guide_not_affiliated)
    val submitSelf = stringResource(R.string.country_submit_self)
    val missingSentence = recipe?.let { r ->
        FormValues.missingRequired(r, ui.values).takeIf { it.isNotEmpty() }?.joinToString(", ") { it.labels.ko }
    }?.let { stringResource(R.string.form_need_required, it) }

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
                    PrimaryButton(stringResource(R.string.form_manual_mode), onClick = onManual, icon = Icons.AutoMirrored.Outlined.OpenInNew)
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
                CardSegment(SegmentPosition.Top) { IndividualHead(missing.size) }
            }
            individual.forEachIndexed { i, f ->
                item(key = fieldItemKey(f.key)) {
                    CardSegment(if (i == individual.lastIndex) SegmentPosition.Bottom else SegmentPosition.Middle) {
                        FieldInput(f, recipe, ui, localLarge, f.key in missingKeys, focus[f.key], onSetValue)
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
                    Text(stringResource(R.string.form_labels_unreviewed), style = MaterialTheme.typography.bodySmall, color = Tokens.InkTertiary)
                }
            }
        }
        // ⑦ 보안 확인과 마지막 '제출'은 직접 — 버튼 바로 위 (누를 수 없는 흰 띠)
        item(key = "notice") {
            NoticeBanner(stringResource(R.string.form_confirm_notice), icon = Icons.Outlined.TouchApp)
        }
        if (missing.isNotEmpty() && missingSentence != null) {
            item(key = "missing") { MissingPanel(missing, missingSentence, goToField) }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                if (ui.saveFailed) {
                    NoticeBanner(stringResource(R.string.form_save_failed), icon = Icons.Outlined.ErrorOutline, tone = BannerTone.Danger)
                }
                if (ctx.autofillAvailable) {
                    PrimaryButton(
                        stringResource(R.string.form_confirm_yes),
                        onClick = onConfirm,
                        enabled = missing.isEmpty(),
                        icon = Icons.Outlined.EditNote,
                    )
                }
                SecondaryButton(
                    stringResource(if (editing) R.string.form_fix_done else R.string.form_confirm_fix),
                    onClick = { editing = !editing },
                    icon = if (editing) Icons.Outlined.Check else Icons.Outlined.Edit,
                )
                // 자동 입력을 쉴 때는 위 안내 카드의 주 버튼이 수동 모드다
                if (ctx.autofillAvailable) {
                    QuietButton(stringResource(R.string.form_manual_mode), onClick = onManual, icon = Icons.AutoMirrored.Outlined.OpenInNew)
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

/** 여권 = 보안(Navy 배지), 항공권 = 이동(Violet), 숙소 = 일반 안내(Accent), 직접 입력 = 기타(Neutral) */
private fun originTone(origin: ValueOrigin): BadgeTone = when (origin) {
    ValueOrigin.Passport -> BadgeTone.Navy
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
                    Text(
                        stringResource(originLabel(origin)),
                        style = MaterialTheme.typography.titleMedium,
                        color = Tokens.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                StatusTag(
                    stringResource(R.string.form_group_count, fields.size),
                    StatusKind.Info,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            fields.forEach { f ->
                HorizontalDivider(thickness = 1.dp, color = Tokens.LineSoft)
                val v = ui.values.getValue(f.key)
                if (editing && v.origin != ValueOrigin.None) {
                    Box(Modifier.padding(vertical = 8.dp)) {
                        FieldTextField(f, ui.draft[f.key] ?: v.display.orEmpty(), localLarge, isError = false, focusRequester = null, onSetValue = onSetValue)
                    }
                } else {
                    ValueRow(f, v, localLarge)
                }
            }
        }
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

@Composable
private fun IndividualHead(missingCount: Int) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(Icons.Outlined.EditNote, tone = BadgeTone.Caution)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.form_choose_yourself),
                style = MaterialTheme.typography.titleLarge,
                color = Tokens.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Box(Modifier.padding(top = 2.dp)) {
                if (missingCount > 0) {
                    StatusTag(stringResource(R.string.form_missing_count, missingCount), StatusKind.Required)
                } else {
                    StatusTag(stringResource(R.string.form_all_filled), StatusKind.Allowed)
                }
            }
        }
    }
}

@Composable
private fun FieldInput(
    f: RecipeField,
    recipe: Recipe,
    ui: ConfirmUi,
    localLarge: Boolean,
    missing: Boolean,
    focusRequester: FocusRequester,
    onSetValue: (String, String) -> Unit,
) {
    val options = f.optionsRef?.let { recipe.options[it] }
    if (options != null) {
        ChoiceField(f, options, ui.draft[f.key], localLarge, missing, focusRequester) { onSetValue(f.key, it) }
    } else {
        FieldTextField(f, ui.draft[f.key].orEmpty(), localLarge, isError = missing, focusRequester = focusRequester, onSetValue = onSetValue)
    }
}

/**
 * 직접 적는 칸: 이름은 입력칸 label(TalkBack 이름), 도움말·영어·현지어 라벨은 supportingText(6-17).
 * '현지어 크게'를 켜면 supportingText가 커진다. 필수인데 비었으면 테두리·아이콘만 빨강(도움말 글자는 읽기 쉬운 색 그대로).
 */
@Composable
private fun FieldTextField(
    f: RecipeField,
    value: String,
    localLarge: Boolean,
    isError: Boolean,
    focusRequester: FocusRequester?,
    onSetValue: (String, String) -> Unit,
) {
    val support = listOfNotNull(f.hintKo, f.labels.en, f.labels.local).joinToString(" · ")
    val requiredLabel = stringResource(R.string.form_field_required)
    OutlinedTextField(
        value = value,
        onValueChange = { onSetValue(f.key, it) },
        singleLine = true,
        label = { Text(f.labels.ko) },
        supportingText = support.takeIf { it.isNotEmpty() }?.let {
            {
                Text(
                    it,
                    style = if (localLarge) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodySmall,
                )
            }
        },
        isError = isError,
        trailingIcon = if (isError) {
            { Icon(Icons.Outlined.ErrorOutline, contentDescription = requiredLabel) }
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
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
    )
}

/** 고르는 칸(여행 목적·숙소 종류): 폭 전체 라디오 행. 그룹은 selectableGroup, 행은 Role.RadioButton, RadioButton 콜백은 null */
@Composable
private fun ChoiceField(
    f: RecipeField,
    options: List<RecipeOption>,
    selected: String?,
    localLarge: Boolean,
    missing: Boolean,
    focusRequester: FocusRequester,
    onSelect: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(f.labels.ko, style = MaterialTheme.typography.titleMedium, color = Tokens.Ink, modifier = Modifier.align(Alignment.CenterVertically))
                if (missing) {
                    StatusTag(stringResource(R.string.form_field_required), StatusKind.Required, Modifier.align(Alignment.CenterVertically))
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
                    IconKeys.option(o.value)?.let {
                        Icon(it, contentDescription = null, tint = if (sel) Tokens.Accent else Tokens.InkSecondary, modifier = Modifier.size(dimens.icon))
                    }
                    Text(optionText(o, localLarge), style = MaterialTheme.typography.bodyLarge, color = Tokens.Ink, modifier = Modifier.weight(1f))
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

/** "관광 · Tourism · ท่องเที่ยว" 한 덩어리 그대로. '현지어 크게'면 현지어 부분만 크게 (글자는 같아 한 Text로 읽힌다) */
@Composable
private fun optionText(o: RecipeOption, localLarge: Boolean) = buildAnnotatedString {
    val head = listOfNotNull(o.ko, o.en).joinToString(" · ")
    append(head)
    if (o.local != null) {
        append(" · ")
        if (localLarge) {
            withStyle(SpanStyle(fontSize = MaterialTheme.typography.headlineMedium.fontSize, fontWeight = FontWeight.SemiBold)) { append(o.local) }
        } else {
            append(o.local)
        }
    }
}

// ---------------- ⑧ 남은 빈칸 ----------------

/**
 * 남은 빈칸: 개수 태그 + 칸 이름 태그(누르면 그 칸으로) + '첫 빈칸으로 가기'.
 * TalkBack에는 `빈칸을 채워 주세요: …` 문장을 liveRegion(Polite)으로 — 빈칸 목록이 바뀔 때만 다시 알린다.
 */
@Composable
private fun MissingPanel(missing: List<RecipeField>, sentence: String, onGo: (String) -> Unit) {
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
            StatusTag(stringResource(R.string.form_missing_count, missing.size), StatusKind.Required)
        }
        // 칸 이름 태그는 넓은 화면에서만. 쉬운 모드·큰 글자(1열)는 태그가 한 줄에 하나씩 쌓여 버튼을 밀어내므로
        // 개수 + '첫 빈칸으로 가기' 하나로 (한 화면 할 일 하나, PRD 3.2). 칸마다 '꼭 채워요' 표시는 카드에 그대로 있다
        if (rememberGridColumns() == 2) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                missing.forEach { f -> MissingFieldChip(f.labels.ko) { onGo(f.key) } }
            }
        }
        SecondaryButton(
            stringResource(R.string.form_go_first_missing),
            onClick = { onGo(missing.first().key) },
            icon = Icons.AutoMirrored.Outlined.NavigateNext,
        )
    }
}

/** 누를 수 있는 빈칸 이름 태그 (StatusTag는 누를 수 없으므로 쓰지 않는다): minTouch, Role.Button, `직업 (영문) 칸으로 가기` */
@Composable
private fun MissingFieldChip(label: String, onClick: () -> Unit) {
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
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Tokens.DangerText, modifier = Modifier.size(LocalDimens.current.iconSmall))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Tokens.Ink)
    }
}
