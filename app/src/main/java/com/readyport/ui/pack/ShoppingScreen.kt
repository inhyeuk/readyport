package com.readyport.ui.pack

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.readyport.ui.components.ImportVerdictBadge
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.ReturnCheckCard
import com.readyport.ui.components.SourceFooter
import com.readyport.ui.components.TopicCard
import com.readyport.ui.components.displayDate
import com.readyport.ui.nav.ShoppingRoute
import com.readyport.ui.theme.LocalDimens
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

@Composable
fun ShoppingContent(ui: ShoppingUi, onToggle: (String, Boolean) -> Unit, onOpenLink: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var showing by remember { mutableStateOf<ShoppingItem?>(null) }
    val gap = LocalDimens.current.gap

    AppScreen(
        title = stringResource(R.string.shopping_title, ui.countryKo),
        subtitle = stringResource(R.string.shopping_subtitle),
        speech = stringResource(R.string.shopping_speech),
    ) {
        item(key = "chips") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(category == null, { category = null }, label = { Text(stringResource(R.string.shopping_cat_all)) }, modifier = Modifier.heightIn(min = 48.dp))
                CATEGORIES.filter { (code, _) -> ui.items.any { it.category == code } }.forEach { (code, label) ->
                    FilterChip(category == code, { category = code }, label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
        if (ui.items.isEmpty()) {
            item(key = "empty") { TopicCard(stringResource(R.string.shopping_empty), null, comingSoon = true) }
        }
        ui.items.filter { category == null || it.category == category }.forEach { item ->
            item(key = "shop-${item.id}") {
                val inCart = CartKey.of(ui.country, item.id) in ui.cart
                InfoCard {
                    Text(item.names.ko, style = MaterialTheme.typography.titleLarge)
                    Text(item.names.local, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ImportVerdictBadge(item.import)
                    item.importNoteKo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    item.whereKo?.let { Text(stringResource(R.string.shopping_where, it), style = MaterialTheme.typography.bodyMedium) }
                    Text(item.whyKo, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { onToggle(item.id, !inCart) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(if (inCart) R.string.shopping_in_cart else R.string.shopping_add))
                        }
                        OutlinedButton(onClick = { showing = item }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.shopping_show_staff))
                        }
                    }
                    SourceFooter(ui.sourceNames[item.source] ?: item.source, displayDate(item.lastVerified))
                }
            }
        }
        item(key = "return") { ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, onOpenLink) }
        item(key = "space") { Column(Modifier.padding(bottom = gap)) {} }
    }

    showing?.let { item ->
        Dialog(onDismissRequest = { showing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier.fillMaxSize().background(Tokens.Surface).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(item.names.local, fontSize = 44.sp, lineHeight = 56.sp, textAlign = TextAlign.Center, color = Tokens.Ink)
                Text(item.names.en, fontSize = 24.sp, textAlign = TextAlign.Center, color = Tokens.InkSecondary)
                Text(item.names.ko, fontSize = 20.sp, textAlign = TextAlign.Center, color = Tokens.InkSecondary)
                PrimaryButton(stringResource(R.string.help_close), onClick = { showing = null })
            }
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
