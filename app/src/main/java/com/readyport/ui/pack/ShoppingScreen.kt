package com.readyport.ui.pack

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.readyport.ui.components.EqualWidthPair
import com.readyport.ui.components.ExpandableDetail
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.ImportVerdictNote
import com.readyport.ui.components.KoText
import com.readyport.ui.components.NumberText
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.ReturnCheckCard
import com.readyport.ui.components.ReturnCheckMode
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.ShowLocalBody
import com.readyport.ui.components.SourceList
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.localText
import com.readyport.ui.components.resolveSourceName
import com.readyport.ui.components.sectionGap
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

/** 목록 순서: 한국 반입 가능 → 반입 주의 → 반입 불가 (sortedBy는 같은 값끼리 원래 순서를 지킨다) */
internal fun verdictOrder(status: ImportStatus): Int = when (status) {
    ImportStatus.Allowed -> 0
    ImportStatus.Caution -> 1
    ImportStatus.Prohibited -> 2
}

/** 글이 이 글자 수를 넘으면 첫 문장만 보이고 나머지는 '설명 더 보기'로 (원칙 6 — 줄 수로 자르지 않는다) */
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
    // 가져올 수 있는 것 → 주의 → 가져올 수 없는 것 순서(같은 판정 안에서는 팩 순서) — 반입 불가 품목이 목록을 이끌지 않게 (재검토 22)
    val visible = ui.items.filter { category == null || it.category == category }.sortedBy { verdictOrder(it.import) }

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
            ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, onOpenLink, ReturnCheckMode.Summary)
        }
    }

    showing?.let { item -> ShowStaffScreen(item, onClose = { showing = null }) }
}

/**
 * 품목 카드 (6-18): 분류 배지 + 한국어 이름·현지어 이름 → 반입 판정(StatusTag 알약 + 보통 본문 이유 — 공용 ImportVerdictNote) → 소개 →
 * 파는 곳 → 담기·직원에게 보여 주기(한 규칙: 같은 폭 한 줄, 모자라면 둘 다 폭 전체로 쌓기) → 출처(품목 출처 + 반입 판정 출처).
 * 현지어 이름은 카드에 한 번만.
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
                // 품목 아이콘은 품목마다(재검토 R11), 배지 톤은 모든 화면에서 Neutral — 판정 색은 판정 알약(ImportVerdictNote)만 맡는다
                IconBadge(IconKeys.item(item.id, item.category), tone = BadgeTone.Neutral)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // 품목 이름 = 카드 제목 글자(titleMedium SemiBold — CardNewsCard 제목과 같은 단, 섹션 머리보다 한 단계 아래, 부록 D.2)
                    KoText(
                        item.names.ko,
                        style = MaterialTheme.typography.titleMedium,
                        color = Tokens.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    // 현지어 이름: 위아래로 쌓이는 태국어 부호가 한국어 제목에 붙거나 겹치지 않게 행간 1.5배 (3.2)
                    Text(item.names.local, style = localText(MaterialTheme.typography.bodyMedium), color = Tokens.InkSecondary)
                }
            }
            // 카드 안 판정 = 공용 판정 묶음(StatusTag 알약 + 보통 본문 이유) — 연한 채움 + 막대 블록은 화면 단위 경고에만 (재검토2 ①#4)
            ImportVerdictNote(item.import, item.importNoteKo)
            val (why, whyMore) = splitLongText(item.whyKo)
            NumberText(why, MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
            if (whyMore != null) {
                // 펼침 줄 이름에 무엇을 펼치는지(품목 이름) — TalkBack에서 `자세히 보기`만 되풀이되지 않게 (재검토 R18)
                ExpandableDetail(
                    label = stringResource(R.string.shopping_more, item.names.ko),
                    target = stringResource(R.string.fold_target_item, item.names.ko),
                ) {
                    NumberText(whyMore, MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
                }
            }
            item.whereKo?.let { IconBullet(stringResource(R.string.shopping_where, it), Icons.Outlined.Place) }
            val action = cartAction(item, inCart)
            val addLabel = stringResource(action.label)
            // TalkBack: 보이는 글자 그대로, 누를 때 읽는 동작 이름에 상품명을 붙인다 (6-18)
            val addAction = stringResource(action.actionLabel, item.names.ko)
            val staffAction = stringResource(R.string.shopping_show_staff_cd, item.names.ko)
            // 버튼 줄은 모든 품목이 한 규칙(재검토2 ①#16): `[담기(테두리 버튼)] [직원에게 보여 주기(글자 버튼)]`를 같은 폭으로 한 줄에,
            // 둘 중 하나라도 반 폭에 한 줄로 안 들어가면(긴 `현지에서 먹기로 담기`·휴대폰 폭·큰 글자) 둘 다 폭 전체로 위아래에 쌓는다.
            // 카드의 결론은 반입 판정이라 담기만 테두리 버튼, 직원에게 보여 주기는 글자 버튼으로 가볍게
            EqualWidthPair(
                gap = dimens.inner,
                first = { m ->
                    SecondaryButton(
                        text = addLabel,
                        onClick = { onToggle(!inCart) },
                        icon = action.icon,
                        tone = action.tone,
                        modifier = m.semantics {
                            onClick(label = addAction) {
                                onToggle(!inCart)
                                true
                            }
                        },
                    )
                },
                second = { m ->
                    QuietButton(
                        text = stringResource(R.string.shopping_show_staff),
                        onClick = onShowStaff,
                        icon = Icons.Outlined.Translate,
                        modifier = m.semantics {
                            onClick(label = staffAction) {
                                onShowStaff()
                                true
                            }
                        },
                    )
                },
            )
            Column(Modifier.padding(top = 4.dp)) { SourceList(sources) }
        }
    }
}

/** 담기 버튼 모양: 보이는 글자, TalkBack 동작 이름(%1$s = 품목 이름), 아이콘, 톤 */
internal data class CartAction(@StringRes val label: Int, @StringRes val actionLabel: Int, val icon: ImageVector, val tone: BadgeTone)

/**
 * 담기 버튼 (재검토 22): 한국에 가져올 수 있거나 주의인 품목 = Accent `담기`(카트), 담았으면 Success `담았어요`(✓ 글자 없이 체크 아이콘만 — 운영자 결정 13).
 * 반입 불가 품목은 같은 `담기`가 '사 와도 된다'로 읽히지 않게 **다른 말·다른 모양** — 흰 바탕 Neutral 테두리 버튼
 * `현지에서 먹기로 담기`(먹거리, 식사 아이콘) / `현지에서 쓰기로 담기`(그 밖, 장소 아이콘). 담은 목록은 귀국 단계가
 * 판정과 함께 다시 보여 준다(같은 카트 — 기능은 그대로).
 */
internal fun cartAction(item: ShoppingItem, inCart: Boolean): CartAction {
    if (item.import != ImportStatus.Prohibited) {
        return if (inCart) {
            CartAction(R.string.shopping_in_cart, R.string.shopping_remove_cd, Icons.Outlined.CheckCircle, BadgeTone.Success)
        } else {
            CartAction(R.string.shopping_add, R.string.shopping_add_cd, Icons.Outlined.AddShoppingCart, BadgeTone.Accent)
        }
    }
    val food = item.category == "food"
    return when {
        inCart -> CartAction(
            if (food) R.string.shopping_in_cart_local_food else R.string.shopping_in_cart_local,
            R.string.shopping_remove_cd,
            Icons.Outlined.CheckCircle,
            BadgeTone.Neutral,
        )
        else -> CartAction(
            if (food) R.string.shopping_add_local_food else R.string.shopping_add_local,
            if (food) R.string.shopping_add_local_food_cd else R.string.shopping_add_local_cd,
            if (food) Icons.Outlined.Restaurant else Icons.Outlined.Place,
            BadgeTone.Neutral,
        )
    }
}

/** 직원에게 보여 주기 전체 화면 내용: 현지어 localLarge(행간 1.5배) + 영어·한국어 (고정 sp 없음), 닫기는 아래 고정 */
@Composable
internal fun ShowStaffBody(item: ShoppingItem, onClose: () -> Unit) {
    val extras = LocalTypeExtras.current
    ShowLocalBody(onClose) {
        Text(item.names.local, style = extras.localLarge, textAlign = TextAlign.Center, color = Tokens.Ink)
        Text(item.names.en, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = Tokens.InkSecondary)
        KoText(item.names.ko, MaterialTheme.typography.titleLarge, color = Tokens.InkSecondary, textAlign = TextAlign.Center)
    }
}

/** 직원에게 보여 주기 전체 화면 */
@Composable
private fun ShowStaffScreen(item: ShoppingItem, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ShowStaffBody(item, onClose)
    }
}

