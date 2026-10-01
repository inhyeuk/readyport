package com.readyport.ui.pack

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.CheckCircle
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
import com.readyport.ui.components.KoText
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.ReturnCheckCard
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.ShowLocalBody
import com.readyport.ui.components.SourceList
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.importKind
import com.readyport.ui.components.importLabel
import com.readyport.ui.components.localText
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.resolveSourceName
import com.readyport.ui.components.sectionGap
import com.readyport.ui.components.startBar
import com.readyport.ui.components.textIconSize
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
    // 출처 이름도 API 33 미만에서는 어절 단위로 줄을 바꾼다 (`태국관광청 · 찬타 / 부리` 방지)
    fun sourceName(id: String) = resolveSourceName(id, ui.sourceNames, ui.indexSources[id] ?: fallback)
    val visible = ui.items.filter { category == null || it.category == category }

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
        visible.forEach { item ->
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
        item(key = "return") {
            // 줄바꿈(어절 단위·출처 날짜)은 공용 ReturnCheckCard가 한다
            ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, onOpenLink)
        }
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
                // 품목 아이콘은 품목마다(재검토 R11), 배지 톤은 모든 화면에서 Neutral — 판정 색은 ImportVerdictPanel만 맡는다
                IconBadge(IconKeys.item(item.id, item.category), tone = BadgeTone.Neutral)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    KoText(
                        item.names.ko,
                        style = MaterialTheme.typography.titleLarge,
                        color = Tokens.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    // 현지어 이름: 위아래로 쌓이는 태국어 부호가 한국어 제목에 붙거나 겹치지 않게 행간 1.5배 (3.2)
                    Text(item.names.local, style = localText(MaterialTheme.typography.bodyMedium), color = Tokens.InkSecondary)
                }
            }
            ImportVerdictPanel(item.import, item.importNoteKo)
            val (why, whyMore) = splitLongText(item.whyKo)
            KoText(why, MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
            if (whyMore != null) {
                ExpandableDetail {
                    KoText(whyMore, MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
                }
            }
            item.whereKo?.let { IconBullet(stringResource(R.string.shopping_where, it), Icons.Outlined.Place) }
            val addLabel = stringResource(if (inCart) R.string.shopping_in_cart else R.string.shopping_add)
            // TalkBack: 보이는 글자는 '담기' 그대로, 누를 때 읽는 동작 이름에 상품명을 붙인다 (6-18)
            val addAction = stringResource(if (inCart) R.string.shopping_remove_cd else R.string.shopping_add_cd, item.names.ko)
            val staffAction = stringResource(R.string.shopping_show_staff_cd, item.names.ko)
            // 쉬운 모드·큰 글자(1열)에서는 담기를 폭 전체로, 직원에게 보여주기는 그 아래 줄에
            val stacked = rememberGridColumns() == 1
            // 카드의 결론은 반입 판정이다 — 담기만 테두리 버튼, 직원에게 보여주기는 글자 버튼으로 가볍게
            // (카드마다 같은 무게의 버튼 두 개가 판정보다 눈에 띄지 않게)
            val add: @Composable (Modifier) -> Unit = { m ->
                SecondaryButton(
                    text = addLabel,
                    onClick = { onToggle(!inCart) },
                    icon = if (inCart) Icons.Outlined.CheckCircle else Icons.Outlined.AddShoppingCart,
                    tone = if (inCart) BadgeTone.Success else BadgeTone.Accent,
                    fillWidth = stacked,
                    modifier = m.semantics {
                        onClick(label = addAction) {
                            onToggle(!inCart)
                            true
                        }
                    },
                )
            }
            val staff: @Composable (String, Modifier) -> Unit = { label, m ->
                QuietButton(
                    text = label,
                    onClick = onShowStaff,
                    icon = Icons.Outlined.Translate,
                    modifier = m.semantics {
                        onClick(label = staffAction) {
                            onShowStaff()
                            true
                        }
                    },
                )
            }
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    add(Modifier)
                    // 1열 폭에서는 한 줄에 다 들어가지 않아 어절 사이에서 미리 두 줄로 나눈 라벨을 쓴다 — 글자 폭만큼만 차지해
                    // 아이콘 옆에 붙는다. 글자 버튼 안쪽 여백만큼 당겨 담기 버튼·출처와 같은 왼쪽 선에
                    staff(stringResource(R.string.shopping_show_staff_short), Modifier.offset(x = -TextButtonInset))
                }
            } else {
                // 옆에 나란히 — 폭이 모자라면 아래 줄로 넘어가지 않고 글자 버튼 라벨이 제 칸 안에서 줄을 바꾼다
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    add(Modifier)
                    staff(stringResource(R.string.shopping_show_staff), Modifier.weight(1f, fill = false))
                }
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
            Icon(
                kind.icon,
                contentDescription = null,
                tint = tone.content,
                modifier = Modifier.size(textIconSize(LocalDimens.current.icon, MaterialTheme.typography.titleSmall)),
            )
            KoText(stringResource(importLabel(status)), MaterialTheme.typography.titleSmall, color = tone.content)
        }
        note?.let { KoText(it, MaterialTheme.typography.bodyMedium, color = tone.content) }
    }
}

/** 직원에게 보여주기 전체 화면 내용: 현지어 localLarge(행간 1.5배) + 영어·한국어 (고정 sp 없음), 닫기는 아래 고정 */
@Composable
internal fun ShowStaffBody(item: ShoppingItem, onClose: () -> Unit) {
    val extras = LocalTypeExtras.current
    ShowLocalBody(onClose) {
        Text(item.names.local, style = extras.localLarge, textAlign = TextAlign.Center, color = Tokens.Ink)
        Text(item.names.en, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = Tokens.InkSecondary)
        KoText(item.names.ko, MaterialTheme.typography.titleLarge, color = Tokens.InkSecondary, textAlign = TextAlign.Center)
    }
}

/** 직원에게 보여주기 전체 화면 */
@Composable
private fun ShowStaffScreen(item: ShoppingItem, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ShowStaffBody(item, onClose)
    }
}

