package com.readyport.ui.prep

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.pack.EssentialRule
import com.readyport.pack.PackRepository
import com.readyport.prep.Essentials
import com.readyport.trip.TripRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTitleLayout
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.ExpandableDetail
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.KoText
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.PhotoBox
import com.readyport.ui.components.PhotoTextArea
import com.readyport.ui.components.Photos
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SourceFooter
import com.readyport.ui.components.SourceRef
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.cardShadow
import com.readyport.ui.components.displayDate
import com.readyport.ui.components.largeFont
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.startBar
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EssentialRow(val rule: EssentialRule, val have: Boolean, val sourceName: String?)

data class EssentialsUi(
    val countryKo: String? = null,
    val nights: Int? = null,
    val month: Int? = null,
    val rows: List<EssentialRow> = emptyList(),
) {
    val done get() = rows.count { it.have }
    val hasAffiliate get() = rows.any { Essentials.isAffiliate(it.rule) }
}

@HiltViewModel
class EssentialsViewModel @Inject constructor(
    private val packs: PackRepository,
    private val settings: SettingsRepository,
    trips: TripRepository,
) : ViewModel() {

    val ui: StateFlow<EssentialsUi> = combine(settings.settings, trips.trip, packs.revision) { s, trip, _ ->
        val index = packs.index()?.value
        val pack = trip?.let { packs.pack(it.country)?.value }
        val rules = Essentials.select(index?.essentials.orEmpty(), index?.homePower, pack?.power)
        EssentialsUi(
            countryKo = pack?.names?.ko,
            nights = trip?.let { ChronoUnit.DAYS.between(it.start, it.end).toInt() },
            month = trip?.start?.monthValue,
            rows = rules.map { r ->
                // 이름을 못 찾으면 null — 화면이 '공식 안내'로 보인다. 내부 ID를 화면에 넘기지 않는다 (DESIGN_SPEC 4.5)
                EssentialRow(r, r.id in s.haveItems, r.source?.let { id -> index?.sources?.firstOrNull { it.id == id }?.name })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EssentialsUi())

    fun setHave(id: String, have: Boolean) = viewModelScope.launch { settings.setHave(id, have) }
}

@Composable
fun EssentialsScreen(viewModel: EssentialsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    EssentialsContent(
        ui = ui,
        onHave = viewModel::setHave,
        onOpenLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
    )
}

/**
 * 16 꼭 챙길 물건 (DESIGN_SPEC 6-16): 제휴 고지(목록 위) → 짐 사진 머리 카드(챙긴 수·막대) → 물건마다 체크 카드.
 * 체크 카드는 줄 전체가 Role.Checkbox 토글이고, 챙기면 상태 카드(연한 초록 + 왼쪽 막대)로 바뀐다.
 */
@Composable
fun EssentialsContent(ui: EssentialsUi, onHave: (String, Boolean) -> Unit, onOpenLink: (String) -> Unit) {
    val subtitle = if (ui.countryKo != null && ui.nights != null && ui.month != null) {
        stringResource(R.string.essentials_for_trip, ui.countryKo, ui.nights, ui.month)
    } else {
        stringResource(R.string.essentials_no_trip)
    }
    AppScreen(
        title = stringResource(R.string.prepare_items_title),
        subtitle = subtitle,
        speech = stringResource(R.string.essentials_speech),
    ) {
        // 제휴 고지는 목록 맨 위 (PRD 5.10). 제휴 링크가 아직 없어도 원칙은 늘 보여 준다. 누를 수 없는 흰 띠 (D21)
        item(key = "disclosure") {
            NoticeBanner(stringResource(R.string.essentials_disclosure), icon = Icons.Outlined.Handshake)
        }
        if (ui.rows.isNotEmpty()) {
            item(key = "progress") { ProgressHero(ui) }
        }
        ui.rows.forEach { row -> item(key = "item-${row.rule.id}") { CheckRowCard(row, onHave, onOpenLink) } }
    }
}

/**
 * 짐 사진 머리(섹션 표지, DESIGN_SPEC 3.7 ①): 스크림 영역 안에 큰 숫자 `2 / 5` + 문장 + 막대.
 * 사진 카드라 그림자·테두리 없음(3.5). 목록이 첫 화면에 들어오게 낮은 띠(최소 112dp)로 둔다.
 * 쉬운 모드·큰 글자(1열)는 숫자 아래에 문장을 둔다 — 숫자 옆 좁은 칸에서 `5개 중 / 2개`로 쪼개지지 않게.
 */
@Composable
private fun ProgressHero(ui: EssentialsUi) {
    val dimens = LocalDimens.current
    val extras = LocalTypeExtras.current
    val total = ui.rows.size
    val done = ui.done
    val stacked = rememberGridColumns() == 1
    val sentence = stringResource(R.string.essentials_progress, total, done)
    PhotoBox(Photos.Packing, minHeight = 112.dp) {
        // 사진 위 글자·막대는 모두 스크림 영역 안 (3.7 ③)
        PhotoTextArea {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Luggage, contentDescription = null, tint = OnDark.content, modifier = Modifier.size(dimens.icon))
                // 큰 숫자는 보는 사람용 — TalkBack은 문장(준비한 물건 5개 중 2개)을 읽는다
                Text(
                    stringResource(R.string.essentials_progress_stat, done, total),
                    style = extras.stat,
                    color = OnDark.content,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                if (!stacked) {
                    KoText(sentence, MaterialTheme.typography.bodyMedium, Modifier.weight(1f), color = OnDark.content, glueShort = true)
                }
            }
            // `중 2개`가 함께 다음 줄로 가게(한 음절 `중`을 뒤 낱말에 묶음) — `2개`만 홀로 남지 않게
            if (stacked) KoText(sentence, MaterialTheme.typography.bodyLarge, color = OnDark.content, glueShort = true)
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().padding(top = if (stacked) 4.dp else 0.dp).height(8.dp),
                color = OnDark.content,
                trackColor = Tokens.White12,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

/**
 * 물건 한 장 (CheckRowCard, 6-16). 머리 줄 전체가 Role.Checkbox 토글 — 이름 + stateDescription(챙겼어요/아직이에요).
 * 아직 = 흰 정보 카드(그림자) / 챙김 = 상태 카드(그림자 없음 + SuccessBg + 왼쪽 SuccessText 막대). 두 규칙을 섞지 않는다.
 * 큰 글자(130%↑)에서 이름이 배지와 체크 상자 사이 한 줄에 안 들어가면 배지·체크 상자를 윗줄에 두고 이름에 폭 전체를 준다.
 */
@Composable
private fun CheckRowCard(row: EssentialRow, onHave: (String, Boolean) -> Unit, onOpenLink: (String) -> Unit) {
    val r = row.rule
    val dimens = LocalDimens.current
    val large = largeFont()
    val shape = MaterialTheme.shapes.large
    val container by animateColorAsState(if (row.have) Tokens.SuccessBg else Tokens.Surface, label = "essentialCard")
    val state = stringResource(if (row.have) R.string.essentials_have_yes else R.string.essentials_have_no)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = Tokens.Ink),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().then(if (row.have) Modifier else Modifier.cardShadow(shape)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (row.have) Modifier.startBar(Tokens.SuccessText) else Modifier)
                .padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.inner),
        ) {
            BadgeTitleLayout(
                modifier = Modifier
                    .fillMaxWidth()
                    .minTouch()
                    .clip(MaterialTheme.shapes.small)
                    .toggleable(value = row.have, role = Role.Checkbox, onValueChange = { onHave(r.id, it) })
                    .semantics { stateDescription = state },
                badge = {
                    IconBadge(
                        essentialIcon(r.id),
                        tone = if (row.have) BadgeTone.Success else BadgeTone.Accent,
                        // 초록 카드 위에서는 배지 바탕이 카드와 같아 사라지므로 흰 바탕으로 띄운다
                        containerColor = if (row.have) Tokens.Surface else BadgeTone.Accent.container,
                    )
                },
                trailing = {
                    Checkbox(
                        checked = row.have,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Tokens.SuccessText,
                            uncheckedColor = Tokens.LineStrong,
                            checkmarkColor = Tokens.Surface,
                        ),
                    )
                },
                stack = large,
                gap = 12.dp,
                title = { NameAndState(r.nameKo, row.have, state) },
            )
            if (r.ruleBadge == "carry_on_only") {
                StatusTag(stringResource(R.string.essentials_badge_carry_on), StatusKind.Caution)
            }
            // 규정 배지가 있는 물건은 규정 문장(숫자·금지)을 먼저 보인다 — 출처 줄이 가리키는 내용이 접힌 곳에 숨지 않게 (원칙 1)
            val (lead, rest) = splitLead(r.reasonKo, preferRule = r.ruleBadge != null)
            KoText(lead, MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
            if (rest != null) {
                ExpandableDetail {
                    KoText(rest, MaterialTheme.typography.bodyMedium, color = Tokens.Ink)
                }
            }
            r.link?.let { link ->
                val affiliate = link.type == "affiliate"
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        link.labelKo,
                        onClick = { onOpenLink(link.url) },
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        // 큰 글자는 폭 전체 — 글자 폭만큼이면 라벨이 `열/기`처럼 쪼개진다
                        fillWidth = large,
                    )
                    // '제휴' 라벨은 제휴 링크에만. 보험·금융 안내(official_info)에는 붙이지 않는다
                    if (affiliate) {
                        Column(Modifier.align(Alignment.CenterVertically)) {
                            StatusChip(
                                stringResource(R.string.essentials_affiliate_label),
                                container = Tokens.SurfaceSunken,
                                content = Tokens.InkSecondary,
                                icon = Icons.Outlined.Handshake,
                            )
                        }
                    }
                }
            }
            if (r.lastVerified != null && (r.source != null || row.sourceName != null)) {
                val name = row.sourceName ?: stringResource(R.string.source_official_fallback)
                val ref = SourceRef(name, displayDate(r.lastVerified))
                Column(Modifier.padding(top = 4.dp)) {
                    SourceFooter(ref)
                }
            }
        }
    }
}

/** 이름 + 보이는 상태 글자. 폭이 모자라면(긴 이름·큰 글자) 상태 글자는 이름 아래 줄로 내려간다. TalkBack은 stateDescription으로 한 번만 읽는다 */
@Composable
private fun NameAndState(name: String, have: Boolean, state: String) {
    val dimens = LocalDimens.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KoText(name, MaterialTheme.typography.titleLarge, Modifier.align(Alignment.CenterVertically), color = Tokens.Ink, glueShort = true)
        if (have) {
            Row(
                Modifier.align(Alignment.CenterVertically).clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Tokens.SuccessText, modifier = Modifier.size(textIconSize(dimens.iconSmall)))
                Text(state, style = MaterialTheme.typography.labelLarge, color = Tokens.SuccessText)
            }
        } else {
            // 아직이면 아이콘 없이 글자만 — 오른쪽 Checkbox와 두 개의 컨트롤처럼 보이지 않게
            Text(
                state,
                style = MaterialTheme.typography.bodyMedium,
                color = Tokens.InkSecondary,
                modifier = Modifier.align(Alignment.CenterVertically).clearAndSetSemantics {},
            )
        }
    }
}

/** 준비물 ID → 아이콘 (IconKeys.essential, 5.7) */
private fun essentialIcon(id: String) = IconKeys.essential(id)

/** 글이 이 길이를 넘으면 첫 문장만 보이고 나머지는 '자세히 보기'로 (디자인 원칙 6 — 줄 수가 아니라 글자 수 기준) */
internal const val LEAD_LIMIT = 60

private val SentenceEnd = Regex("""(?<=[.!?])\s+""")

/**
 * 긴 글을 (앞에 보일 문장, 나머지)로 나눈다. [limit]자 이하이거나 문장 경계를 못 찾으면 (전체, null) — 전체를 보인다.
 * [preferRule]이면(규정 배지가 있는 물건) 숫자가 든 규정 문장(`1인당 2개(160Wh 이하)…`)을 앞에 보이고 나머지는 원래 순서로 접는다.
 * 출처·고지에는 쓰지 않는다(항상 전체).
 */
internal fun splitLead(text: String, limit: Int = LEAD_LIMIT, preferRule: Boolean = false): Pair<String, String?> {
    val t = text.trim()
    if (t.length <= limit) return t to null
    val sentences = t.split(SentenceEnd).map { it.trim() }.filter { it.isNotEmpty() }
    if (sentences.size < 2) return t to null
    val leadIndex = if (preferRule) sentences.indexOfFirst(::isRuleSentence).coerceAtLeast(0) else 0
    val rest = sentences.filterIndexed { i, _ -> i != leadIndex }.joinToString(" ")
    return sentences[leadIndex] to rest
}

/** 규정 문장: 숫자(개수·용량·금액)가 든 문장 */
private fun isRuleSentence(sentence: String): Boolean = sentence.any { it.isDigit() }
