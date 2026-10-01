package com.readyport.ui.pack

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.OfficialLink
import com.readyport.pack.PackRepository
import com.readyport.pack.ShoppingItem
import com.readyport.pack.SourcedText
import com.readyport.prep.CartKey
import com.readyport.prep.ImportStatus
import com.readyport.prep.import
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.EmptyState
import com.readyport.ui.components.ExpandableDetail
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.ReturnCheckCard
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.SourceList
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.importKind
import com.readyport.ui.components.importLabel
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.resolveSourceName
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.startBar
import com.readyport.ui.nav.ShoppingRoute
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingUi(
    val country: String = "",
    val countryKo: String = "",
    val items: List<ShoppingItem> = emptyList(),
    val cart: Set<String> = emptySet(),
    val sourceNames: Map<String, String> = emptyMap(),
    val returnLinks: List<OfficialLink> = emptyList(),
    val returnFacts: List<SourcedText> = emptyList(),
    /** index 출처 id → 이름 (귀국 안내용) */
    val indexSources: Map<String, String> = emptyMap(),
)

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    savedState: SavedStateHandle,
    packs: PackRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val country = savedState.toRoute<ShoppingRoute>().country

    val ui: StateFlow<ShoppingUi> = combine(settings.settings, packs.revision) { s, _ ->
        val pack = packs.pack(country)?.value
        ShoppingUi(
            country = country,
            countryKo = pack?.names?.ko.orEmpty(),
            items = pack?.shopping.orEmpty(),
            cart = s.cart,
            sourceNames = pack?.sources.orEmpty().associate { it.id to it.name },
            returnLinks = packs.index()?.value?.returnLinks.orEmpty(),
            returnFacts = packs.index()?.value?.returnFacts.orEmpty(),
            indexSources = packs.index()?.value?.sources.orEmpty().associate { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUi())

    fun toggle(itemId: String, inCart: Boolean) = viewModelScope.launch { settings.setInCart(CartKey.of(country, itemId), inCart) }
}

// ---- 옛 위치 (DESIGN_SPEC 4.0 이동 규칙): components(Status.kt·ReturnCheck.kt)로 옮겼다.
// 1단계 묶음은 이 위임 함수를 지우거나 시그니처를 바꾸지 않는다. 2단계에서 사용처가 0이면 지운다.

/** 한국 반입 태그 색: 가능 초록 / 주의 주황 / 불가 빨강 (PRD 5.8) */
@Deprecated("components.importColors 사용", ReplaceWith("importColors(status)", "com.readyport.ui.components.importColors"))
fun importColors(status: ImportStatus): Pair<Color, Color> = com.readyport.ui.components.importColors(status)

@Deprecated("components.importLabel 사용", ReplaceWith("importLabel(status)", "com.readyport.ui.components.importLabel"))
fun importLabel(status: ImportStatus): Int = com.readyport.ui.components.importLabel(status)

@Deprecated("components.ImportVerdictBadge 사용", ReplaceWith("ImportVerdictBadge(status)", "com.readyport.ui.components.ImportVerdictBadge"))
@Composable
fun ImportTag(status: ImportStatus) {
    com.readyport.ui.components.ImportVerdictBadge(status)
}

@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ShoppingContent(ui, viewModel::toggle) { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
}

private val CATEGORIES = listOf("food" to R.string.shopping_cat_food, "daily" to R.string.shopping_cat_daily, "souvenir" to R.string.shopping_cat_souvenir)

/** 글이 이 글자 수를 넘으면 첫 문장만 보이고 나머지는 '자세히 보기'로 (원칙 6 — 줄 수로 자르지 않는다) */
private const val LONG_TEXT_CHARS = 60

/** 긴 글 → (첫 문장, 나머지). 짧거나 첫 문장 경계(". ")를 못 찾으면 (전체, null) */
internal fun splitLongText(text: String): Pair<String, String?> {
    if (text.length <= LONG_TEXT_CHARS) return text to null
    val cut = text.indexOf(". ")
    if (cut <= 0) return text to null
    val rest = text.substring(cut + 2).trim()
    return if (rest.isEmpty()) text to null else text.substring(0, cut + 1) to rest
}

@Composable
fun ShoppingContent(ui: ShoppingUi, onToggle: (String, Boolean) -> Unit, onOpenLink: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var showing by remember { mutableStateOf<ShoppingItem?>(null) }
    val fallback = stringResource(R.string.source_official_fallback)
    // 품목 출처는 나라 팩, 반입 판정 출처는 나라 팩 또는 index(관세청·검역본부)에 있다. 못 찾으면 '공식 안내' — ID는 보이지 않는다
    fun sourceName(id: String) = resolveSourceName(id, ui.sourceNames, ui.indexSources[id] ?: fallback)

    AppScreen(
        title = stringResource(R.string.shopping_title, ui.countryKo),
        subtitle = stringResource(R.string.shopping_subtitle_v2),
        speech = stringResource(R.string.shopping_speech),
    ) {
        if (ui.items.isNotEmpty()) {
            item(key = "chips") {
                // 분류는 한 개만 고른다 (Role.RadioButton + selectableGroup). 품목 없는 분류 칩은 그리지 않는다
                FlowRow(
                    Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectChip(
                        selected = category == null,
                        onClick = { category = null },
                        label = stringResource(R.string.shopping_cat_all),
                        leadingIcon = IconKeys.shoppingCategory(null),
                    )
                    CATEGORIES.filter { (code, _) -> ui.items.any { it.category == code } }.forEach { (code, label) ->
                        SelectChip(
                            selected = category == code,
                            onClick = { category = code },
                            label = stringResource(label),
                            leadingIcon = IconKeys.shoppingCategory(code),
                        )
                    }
                }
            }
        } else {
            item(key = "empty") {
                EmptyState(icon = Icons.Outlined.ShoppingBag, title = stringResource(R.string.shopping_empty), body = null)
            }
        }
        ui.items.filter { category == null || it.category == category }.forEach { item ->
            item(key = "shop-${item.id}") {
                ShopItemCard(
                    item = item,
                    inCart = CartKey.of(ui.country, item.id) in ui.cart,
                    sources = listOf(
                        SourceRef(sourceName(item.source), displayDate(item.lastVerified)),
                        SourceRef(sourceName(item.importSource), displayDate(item.lastVerified)),
                    ),
                    onToggle = { onToggle(item.id, it) },
                    onShowStaff = { showing = item },
                )
            }
        }
        sectionGap("gap-return")
        item(key = "return") { ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, onOpenLink) }
    }

    showing?.let { item -> ShowStaffScreen(item, onClose = { showing = null }) }
}

/**
 * 품목 카드 (6-18): 분류 배지 + 한국어 이름·현지어 이름 → 반입 판정(색 + 아이콘 + 글자)과 이유 → 소개 → 파는 곳 →
 * 담기·직원에게 보여주기 → 출처(품목 출처 + 반입 판정 출처). 현지어 이름은 카드에 한 번만.
 */
@Composable
private fun ShopItemCard(
    item: ShoppingItem,
    inCart: Boolean,
    sources: List<SourceRef>,
    onToggle: (Boolean) -> Unit,
    onShowStaff: () -> Unit,
) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.large
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().cardShadow(shape),
    ) {
        Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(IconKeys.shoppingCategory(item.category), tone = BadgeTone.Help)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.names.ko,
                        style = MaterialTheme.typography.titleLarge,
                        color = Tokens.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(item.names.local, style = MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                }
            }
            ImportVerdictPanel(item.import, item.importNoteKo)
            val (why, whyMore) = splitLongText(item.whyKo)
            Text(why, style = MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
            if (whyMore != null) {
                ExpandableDetail {
                    Text(whyMore, style = MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
                }
            }
            item.whereKo?.let { IconBullet(stringResource(R.string.shopping_where, it), Icons.Outlined.Place) }
            val addLabel = stringResource(if (inCart) R.string.shopping_in_cart else R.string.shopping_add)
            // TalkBack: 보이는 글자는 '담기' 그대로, 누를 때 읽는 동작 이름에 상품명을 붙인다 (6-18)
            val addAction = stringResource(if (inCart) R.string.shopping_remove_cd else R.string.shopping_add_cd, item.names.ko)
            val staffAction = stringResource(R.string.shopping_show_staff_cd, item.names.ko)
            // 쉬운 모드·큰 글자(1열)에서는 두 버튼을 폭 전체로 쌓는다 — 글자 폭만큼이면 라벨이 음절 단위로 꺾인다
            val stacked = rememberGridColumns() == 1
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = addLabel,
                    onClick = { onToggle(!inCart) },
                    icon = if (inCart) Icons.Outlined.CheckCircle else Icons.Outlined.AddShoppingCart,
                    tone = if (inCart) BadgeTone.Success else BadgeTone.Accent,
                    fillWidth = stacked,
                    modifier = Modifier.semantics {
                        onClick(label = addAction) {
                            onToggle(!inCart)
                            true
                        }
                    },
                )
                SecondaryButton(
                    text = stringResource(R.string.shopping_show_staff),
                    onClick = onShowStaff,
                    icon = Icons.Outlined.Translate,
                    fillWidth = stacked,
                    modifier = Modifier.semantics {
                        onClick(label = staffAction) {
                            onShowStaff()
                            true
                        }
                    },
                )
            }
            Column(Modifier.padding(top = 4.dp)) { SourceList(sources) }
        }
    }
}

/**
 * 반입 판정 칸: 연한 상태 바탕 + 왼쪽 4dp 막대(3.5) 안에 판정(아이콘 + 글자)과 이유(같은 색 bodyMedium) —
 * 판정이 이유 글보다 약해 보이던 위계를 바로잡는다(6-18). 글자는 import_allowed/caution/prohibited 그대로.
 */
@Composable
private fun ImportVerdictPanel(status: ImportStatus, note: String?) {
    val kind = importKind(status)
    val tone = kind.tone
    val bar = if (tone == BadgeTone.Caution) Tokens.CautionBorder else tone.content
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(tone.container)
            .startBar(bar)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(kind.icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(LocalDimens.current.icon))
            Text(stringResource(importLabel(status)), style = MaterialTheme.typography.titleSmall, color = tone.content)
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = tone.content) }
    }
}

/** 직원에게 보여주기 전체 화면: 현지어 localLarge(행간 1.5배) + 영어·한국어 (고정 sp 없음) */
@Composable
private fun ShowStaffScreen(item: ShoppingItem, onClose: () -> Unit) {
    val extras = LocalTypeExtras.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Tokens.Surface).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(item.names.local, style = extras.localLarge, textAlign = TextAlign.Center, color = Tokens.Ink)
            Text(item.names.en, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = Tokens.InkSecondary)
            Text(item.names.ko, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = Tokens.InkSecondary)
            PrimaryButton(stringResource(R.string.help_close), onClick = onClose, icon = Icons.Outlined.Close)
        }
    }
}

/** '귀국 전 확인 — 면세 한도·반입 금지 품목(관세청·검역본부)' — components.ReturnCheckCard(v2)로 옮겼다 */
@Deprecated(
    "components.ReturnCheckCard 사용 (DESIGN_SPEC 4.0 이동 규칙)",
    ReplaceWith("ReturnCheckCard(links, facts, sourceNames, onOpenLink)", "com.readyport.ui.components.ReturnCheckCard"),
)
@Composable
fun ReturnCheckCard(
    links: List<OfficialLink>,
    facts: List<SourcedText>,
    sourceNames: Map<String, String>,
    onOpenLink: (String) -> Unit,
) {
    com.readyport.ui.components.ReturnCheckCard(links, facts, sourceNames, onOpenLink)
}
