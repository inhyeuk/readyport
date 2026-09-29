package com.readyport.ui.home

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.OfficialLink
import com.readyport.pack.PackRepository
import com.readyport.pack.Requirement
import com.readyport.pack.SourcedText
import com.readyport.prep.Essentials
import com.readyport.trip.TripRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PhotoBox
import com.readyport.ui.components.PhotoChip
import com.readyport.ui.components.Photos
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.TopicCard
import com.readyport.ui.pack.ReturnCheckCard
import com.readyport.ui.tabs.EssentialsSummary
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** 홈의 나라 사진 카드 하나 */
data class HomeCountry(
    val code: String,
    val nameKo: String,
    val nameEn: String,
    /** 한국 여권·관광 목적 입국 조건 (없으면 안내 준비 중) */
    val visa: Requirement? = null,
    val hasForm: Boolean = false,
    val ready: Boolean = false,
)

/** 홈 위쪽 '내 여행' 요약 */
data class HomeTrip(val countryKo: String, val startDate: LocalDate, val endDate: LocalDate)

data class HomeUi(
    val countries: List<HomeCountry> = emptyList(),
    val trip: HomeTrip? = null,
    val essentials: EssentialsSummary = EssentialsSummary(),
    val returnLinks: List<OfficialLink> = emptyList(),
    val returnFacts: List<SourcedText> = emptyList(),
    val indexSources: Map<String, String> = emptyMap(),
)

data class HomeActions(
    val openCountry: (String) -> Unit = {},
    val openTrip: () -> Unit = {},
    val openEssentials: () -> Unit = {},
    val openMyInfo: () -> Unit = {},
    val openLink: (String) -> Unit = {},
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    packs: PackRepository,
    settings: SettingsRepository,
    trips: TripRepository,
) : ViewModel() {
    val ui: StateFlow<HomeUi> = combine(settings.settings, packs.revision, trips.trip) { s, _, trip ->
        val index = packs.index()?.value
        val countries = index?.countries.orEmpty().map { c ->
            val pack = if (c.pack) packs.pack(c.code)?.value else null
            HomeCountry(
                code = c.code, nameKo = c.nameKo, nameEn = c.nameEn,
                visa = pack?.requirements?.firstOrNull { it.nationality == "KR" && it.purpose == "tourism" },
                hasForm = pack?.forms?.isNotEmpty() == true,
                ready = pack != null,
            )
        }
        val tripPack = trip?.let { packs.pack(it.country)?.value }
        val homeTrip = trip?.let { t ->
            runCatching { HomeTrip(tripPack?.names?.ko ?: t.country, LocalDate.parse(t.startDate), LocalDate.parse(t.endDate)) }.getOrNull()
        }
        val rules = Essentials.select(index?.essentials.orEmpty(), index?.homePower, tripPack?.power)
        HomeUi(
            countries = countries,
            trip = homeTrip,
            essentials = EssentialsSummary(rules.size, rules.count { it.id in s.haveItems }),
            returnLinks = index?.returnLinks.orEmpty(),
            returnFacts = index?.returnFacts.orEmpty(),
            indexSources = index?.sources.orEmpty().associate { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}

@Composable
fun HomeScreen(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    HomeContent(
        ui = ui,
        actions = actions.copy(openLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }),
    )
}

@Composable
fun HomeContent(ui: HomeUi, actions: HomeActions, today: LocalDate = LocalDate.now()) {
    val dimens = LocalDimens.current
    AppScreen(
        title = stringResource(R.string.home_title),
        speech = stringResource(R.string.home_speech),
        header = { HomeHero() },
    ) {
        ui.trip?.let { trip -> item(key = "trip") { TripSummaryCard(trip, today, actions.openTrip) } }

        item(key = "countries-title") {
            SectionTitle(stringResource(R.string.home_countries_title), stringResource(R.string.home_countries_body))
        }
        ui.countries.forEach { c ->
            item(key = "country-${c.code}") { CountryPhotoCard(c, onClick = { actions.openCountry(c.code) }) }
        }

        item(key = "basics-title") { SectionTitle(stringResource(R.string.home_basics_title), null) }
        item(key = "departure") {
            PhotoTopCard(Photos.Airport, stringResource(R.string.home_departure_title)) {
                listOf(
                    R.string.today_departure_step1, R.string.today_departure_step2, R.string.today_departure_step3,
                    R.string.today_departure_step4, R.string.today_departure_step5,
                ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
            }
        }
        item(key = "essentials") {
            PhotoTopCard(Photos.Packing, stringResource(R.string.home_essentials_title)) {
                Text(stringResource(R.string.home_essentials_body), style = MaterialTheme.typography.bodyLarge)
                if (ui.essentials.total > 0) {
                    Text(
                        stringResource(R.string.essentials_progress, ui.essentials.total, ui.essentials.done),
                        style = MaterialTheme.typography.titleMedium,
                        color = Tokens.Accent,
                    )
                }
                PrimaryButton(stringResource(R.string.home_essentials_open), onClick = actions.openEssentials)
            }
        }
        if (ui.returnFacts.isNotEmpty() || ui.returnLinks.isNotEmpty()) {
            item(key = "return-photo") {
                PhotoBox(Photos.Market, minHeight = 150.dp) {
                    Text(
                        stringResource(R.string.home_return_photo),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomStart).padding(dimens.cardPadding),
                    )
                }
            }
            item(key = "return") { ReturnCheckCard(ui.returnLinks, ui.returnFacts, ui.indexSources, actions.openLink) }
        }
        item(key = "passport") {
            InfoCard(tone = CardTone.Navy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.home_passport_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Text(stringResource(R.string.home_passport_body), style = MaterialTheme.typography.bodyLarge)
                PrimaryButton(
                    stringResource(R.string.home_passport_open),
                    onClick = actions.openMyInfo,
                    colors = ButtonDefaults.buttonColors(containerColor = Tokens.Surface, contentColor = Tokens.Navy),
                )
            }
        }
        item(key = "help") {
            TopicCard(stringResource(R.string.today_help_title), stringResource(R.string.today_help_body))
        }
    }
}

/** 맨 위 사진 머리글: 앱 이름 + "어디로 떠나세요?" */
@Composable
private fun HomeHero() {
    PhotoBox(Photos.Home, minHeight = 250.dp) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.home_brand), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.92f))
            Text(
                stringResource(R.string.home_title),
                color = Color.White,
                fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
    }
}

@Composable
private fun SectionTitle(title: String, body: String?) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 나라 사진 카드: 대표 경치 + 나라 이름 + 입국 요약. 카드 전체가 하나의 버튼 */
@Composable
fun CountryPhotoCard(c: HomeCountry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.home_country_open, c.nameKo)
    val chips = buildList {
        c.visa?.let { v ->
            add(
                when {
                    v.visa == "not_required" && v.stayLimitDays != null -> stringResource(R.string.home_chip_visa_free, v.stayLimitDays)
                    v.visa == "on_arrival" && v.stayLimitDays != null -> stringResource(R.string.home_chip_visa_arrival, v.stayLimitDays)
                    else -> stringResource(R.string.home_chip_visa_check)
                },
            )
        }
        if (c.hasForm) add(stringResource(R.string.home_chip_form))
        if (!c.ready) add(stringResource(R.string.home_chip_not_ready))
    }
    PhotoBox(
        Photos.country(c.code),
        minHeight = if (LocalDimens.current.easyMode) 220.dp else 200.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = c.ready, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(c.nameKo, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                    Text(c.nameEn, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
                }
                if (c.ready) Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Color.White)
            }
            if (chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { PhotoChip(it) }
                }
            }
        }
    }
}

/** 위에 사진, 아래에 설명이 있는 카드 */
@Composable
fun PhotoTopCard(
    @DrawableRes photo: Int,
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val dimens = LocalDimens.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        border = androidx.compose.foundation.BorderStroke(1.dp, Tokens.LineSoft),
    ) {
        PhotoBox(photo, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp), minHeight = 140.dp) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(dimens.cardPadding).semantics { heading() },
            )
        }
        Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.gap / 2), content = content)
    }
}

@Composable
private fun TripSummaryCard(trip: HomeTrip, today: LocalDate, onOpen: () -> Unit) {
    val days = ChronoUnit.DAYS.between(today, trip.startDate).toInt()
    val status = when {
        days > 0 -> stringResource(R.string.home_trip_days, days)
        days == 0 -> stringResource(R.string.home_trip_today)
        !today.isAfter(trip.endDate) -> stringResource(R.string.home_trip_during)
        else -> stringResource(R.string.home_trip_after)
    }
    InfoCard(tone = CardTone.Accent) {
        Text(stringResource(R.string.home_trip_label, trip.countryKo), style = MaterialTheme.typography.labelLarge)
        Text(status, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(2.dp))
        PrimaryButton(
            stringResource(R.string.home_trip_open),
            onClick = onOpen,
            colors = ButtonDefaults.buttonColors(containerColor = Tokens.Surface, contentColor = Tokens.Accent),
        )
    }
}
