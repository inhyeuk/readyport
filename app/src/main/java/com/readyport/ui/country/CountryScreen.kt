package com.readyport.ui.country

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.CountryPack
import com.readyport.pack.Loaded
import com.readyport.pack.OfficialLink
import com.readyport.pack.PackOrigin
import com.readyport.pack.PackRepository
import com.readyport.pack.PackSync
import com.readyport.pack.SourcedText
import com.readyport.prep.import
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PhotoBox
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.SourceFooter
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.TopicCard
import com.readyport.ui.home.PhotoTopCard
import com.readyport.ui.nav.CountryRoute
import com.readyport.ui.pack.ImportTag
import com.readyport.ui.pack.ReturnCheckCard
import com.readyport.ui.pack.displayDate
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CountryUi(
    val loaded: Loaded<CountryPack>? = null,
    val favorite: Boolean = false,
    /** 앱이 공식 사이트 입력을 채워 줄 수 있는 양식(레시피가 있는 것) */
    val autofillForms: Set<String> = emptySet(),
    val returnLinks: List<OfficialLink> = emptyList(),
    val returnFacts: List<SourcedText> = emptyList(),
    val indexSources: Map<String, String> = emptyMap(),
)

/** 나라 화면에서 다른 곳으로 가는 길 */
data class CountryActions(
    val back: () -> Unit = {},
    val openForm: (String) -> Unit = {},
    val planTrip: (String) -> Unit = {},
    val openHelp: (String) -> Unit = {},
    val openMove: () -> Unit = {},
    val openShopping: (String) -> Unit = {},
    val openLink: (String) -> Unit = {},
    val toggleFavorite: () -> Unit = {},
)

@HiltViewModel
class CountryViewModel @Inject constructor(
    handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val packs: PackRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val country = handle.toRoute<CountryRoute>().country

    val ui: StateFlow<CountryUi> = combine(settings.settings, packs.revision) { s, _ ->
        val loaded = packs.pack(country)
        val index = packs.index()?.value
        CountryUi(
            loaded = loaded,
            favorite = country in s.favorites,
            autofillForms = loaded?.value?.forms.orEmpty().filter { packs.recipe(it.id) != null }.map { it.id }.toSet(),
            returnLinks = index?.returnLinks.orEmpty(),
            returnFacts = index?.returnFacts.orEmpty(),
            indexSources = index?.sources.orEmpty().associate { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CountryUi())

    fun toggleFavorite() = viewModelScope.launch {
        val now = settings.current()
        settings.setFavorite(country, country !in now.favorites)
        PackSync.requestNow(context, now.wifiOnly)
    }

    /** 도움 탭이 이 나라 문장·연락처를 먼저 보여 주게 */
    fun chooseForHelp() = viewModelScope.launch { settings.setHelpCountry(country) }
}

@Composable
fun CountryScreen(actions: CountryActions, viewModel: CountryViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CountryContent(
        ui = ui,
        actions = actions.copy(
            openLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
            openHelp = { code -> viewModel.chooseForHelp(); actions.openHelp(code) },
            toggleFavorite = { viewModel.toggleFavorite() },
        ),
    )
}

/** 나라 화면 안의 세 갈래. 스와이프 없이 버튼으로만 바꾼다 */
enum class CountrySection(val label: Int) {
    Entry(R.string.country_tab_entry),
    Travel(R.string.country_tab_travel),
    Shopping(R.string.country_tab_shopping),
}

@Composable
fun CountryContent(ui: CountryUi, actions: CountryActions, initialSection: CountrySection = CountrySection.Entry) {
    val loaded = ui.loaded ?: return
    val pack = loaded.value
    var section by rememberSaveable(pack.country) { mutableIntStateOf(initialSection.ordinal) }
    fun sourceName(id: String) = pack.source(id)?.name ?: id

    AppScreen(
        title = pack.names.ko,
        speech = stringResource(R.string.country_speech, pack.names.ko),
        header = { CountryHero(loaded, ui.favorite, actions) },
    ) {
        item(key = "sections") { SectionPicker(pack.names.ko, CountrySection.entries[section]) { section = it.ordinal } }

        when (CountrySection.entries[section]) {
            CountrySection.Entry -> {
                item(key = "not-affiliated") { TopicCard(stringResource(R.string.guide_not_affiliated), null, tone = CardTone.Notice) }
                pack.requirements.filter { it.nationality == "KR" }.forEach { req ->
                    item(key = "req-${req.purpose}") {
                        InfoCard(tone = CardTone.Accent) {
                            Text(stringResource(R.string.country_visa_title), style = MaterialTheme.typography.labelLarge)
                            Text(req.summaryKo, style = MaterialTheme.typography.titleMedium)
                            if (req.visa != "not_required") {
                                pack.source(req.source)?.let { src ->
                                    OutlinedButton(onClick = { actions.openLink(src.url) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(stringResource(R.string.country_visa_link), color = Tokens.Surface)
                                    }
                                }
                            }
                            Text(
                                stringResource(R.string.source_footer, sourceName(req.source), displayDate(req.lastVerified)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                pack.forms.forEach { form ->
                    val autofill = form.id in ui.autofillForms
                    item(key = "form-${form.id}") {
                        InfoCard {
                            StatusChip(stringResource(R.string.country_form_label))
                            Text(form.nameKo, style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(if (autofill) R.string.country_form_autofill_body else R.string.country_form_manual_body),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(stringResource(R.string.guide_form_fee, form.feeKo), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.guide_form_window, form.windowKo), style = MaterialTheme.typography.bodyMedium)
                            PrimaryButton(
                                stringResource(if (autofill) R.string.country_form_start else R.string.country_form_manual_start),
                                onClick = { actions.openForm(form.id) },
                            )
                            SourceFooter(sourceName(form.source), displayDate(form.lastVerified))
                        }
                    }
                }
                pack.sections.filter { it.id == "entry" }.forEach { s -> item(key = "section-${s.id}") { SectionCard(s.titleKo, s.bodyKo, sourceName(s.source), s.lastVerified) } }
                item(key = "plan") {
                    OutlinedButton(
                        onClick = { actions.planTrip(pack.country) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight),
                    ) { Text(stringResource(R.string.country_plan_trip), style = MaterialTheme.typography.labelLarge) }
                }
            }

            CountrySection.Travel -> {
                pack.power?.let { power ->
                    item(key = "power") {
                        InfoCard {
                            Text(stringResource(R.string.guide_power_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            Text(stringResource(R.string.guide_power_body, power.plugKo, power.voltage, power.frequency), style = MaterialTheme.typography.bodyLarge)
                            power.krPlugFits?.let { fits ->
                                Text(
                                    stringResource(if (fits) R.string.guide_power_kr_fits else R.string.guide_power_kr_adapter),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            SourceFooter(sourceName(power.source), displayDate(power.lastVerified))
                        }
                    }
                }
                pack.sections.filter { it.id != "entry" }.forEach { s ->
                    item(key = "section-${s.id}") { SectionCard(s.titleKo, s.bodyKo, sourceName(s.source), s.lastVerified) }
                }
                item(key = "help") {
                    InfoCard(tone = CardTone.Navy) {
                        Text(stringResource(R.string.country_help_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.country_help_body), style = MaterialTheme.typography.bodyLarge)
                        PrimaryButton(
                            stringResource(R.string.country_help_open),
                            onClick = { actions.openHelp(pack.country) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Tokens.Surface, contentColor = Tokens.Navy),
                        )
                    }
                }
                item(key = "move") {
                    InfoCard {
                        Text(stringResource(R.string.move_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.move_speech), style = MaterialTheme.typography.bodyMedium)
                        PrimaryButton(stringResource(R.string.move_title), onClick = actions.openMove)
                    }
                }
                item(key = "maps") {
                    InfoCard {
                        Text(stringResource(R.string.explore_maps_title), style = MaterialTheme.typography.titleMedium)
                        listOf(R.string.explore_maps_step1, R.string.explore_maps_step2, R.string.explore_maps_step3).forEach {
                            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(stringResource(R.string.explore_maps_note), style = MaterialTheme.typography.bodySmall)
                        SourceFooter(stringResource(R.string.explore_maps_source), "2026.09.28")
                    }
                }
            }

            CountrySection.Shopping -> {
                if (pack.shopping.isNotEmpty()) {
                    item(key = "shopping") {
                        PhotoTopCard(Photos.Market, stringResource(R.string.shopping_title, pack.names.ko)) {
                            Text(stringResource(R.string.shopping_subtitle), style = MaterialTheme.typography.bodyMedium)
                            pack.shopping.take(3).forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.names.ko, style = MaterialTheme.typography.titleMedium)
                                    Text(item.whyKo, style = MaterialTheme.typography.bodyMedium)
                                    ImportTag(item.import)
                                }
                            }
                            if (pack.shopping.size > 3) {
                                Text(stringResource(R.string.country_shopping_more, pack.shopping.size - 3), style = MaterialTheme.typography.bodyMedium)
                            }
                            PrimaryButton(stringResource(R.string.shopping_open), onClick = { actions.openShopping(pack.country) })
                        }
                    }
                }
                item(key = "return") { ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, actions.openLink) }
            }
        }
    }
}

/** 나라 대표 경치 머리글: 뒤로 · 찜 · 나라 이름 */
@Composable
private fun CountryHero(loaded: Loaded<CountryPack>, favorite: Boolean, actions: CountryActions) {
    val pack = loaded.value
    PhotoBox(Photos.country(pack.country), minHeight = 0.dp) {
        // 버튼 줄과 나라 이름을 세로로 쌓는다 — 글자를 키워도 서로 겹치지 않는다
        Column(Modifier.fillMaxWidth().heightIn(min = 280.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(
                    onClick = actions.back,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.country_back), tint = Color.White) }
                val label = stringResource(if (favorite) R.string.explore_favorite_remove else R.string.explore_favorite_add, pack.names.ko)
                IconToggleButton(
                    checked = favorite,
                    onCheckedChange = { actions.toggleFavorite() },
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).semantics { contentDescription = label },
                ) {
                    Icon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = null, tint = Color.White)
                }
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    pack.names.ko,
                    color = Color.White, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    listOf(pack.names.en, pack.names.local).distinct().joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge, color = Color.White,
                )
                Text(
                    stringResource(R.string.guide_last_verified, displayDate(pack.lastVerified)) + " · " +
                        stringResource(if (loaded.origin == PackOrigin.Bundled) R.string.guide_origin_bundled else R.string.guide_origin_downloaded),
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f),
                )
            }
        }
    }
}

@Composable
private fun SectionPicker(countryKo: String, selected: CountrySection, onSelect: (CountrySection) -> Unit) {
    val description = stringResource(R.string.country_sections, countryKo)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = description }) {
        CountrySection.entries.forEachIndexed { i, s ->
            SegmentedButton(
                selected = s == selected,
                onClick = { onSelect(s) },
                shape = SegmentedButtonDefaults.itemShape(i, CountrySection.entries.size),
                icon = {},
                modifier = Modifier.heightIn(min = LocalDimens.current.buttonHeight),
            ) { Text(stringResource(s.label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun SectionCard(title: String, body: List<String>, source: String, lastVerified: String) {
    InfoCard {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        body.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        SourceFooter(source, displayDate(lastVerified))
    }
}
