package com.readyport.ui.prep

import android.content.Intent
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.TaskAlt
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
import com.readyport.pack.PowerInfo
import com.readyport.prep.Essentials
import com.readyport.trip.TripRepository
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTitleLayout
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.CardBorderWidth
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.ExpandableDetail
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.PowerChips
import com.readyport.ui.components.IconKeys
import com.readyport.ui.components.KoText
import com.readyport.ui.components.NumberText
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
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.components.isStackedListRow
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.sectionGap
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
    /** 여행지 전기(팩 power 값) — 있으면 값 칩(`220 V 전압`·`한국 플러그 그대로 써요`)을 보인다. 여행이 없으면 null(칩 줄 없음) */
    val power: PowerInfo? = null,
    /** [power] 출처 이름(팩 sources). 못 찾으면 null — 화면은 '공식 안내' */
    val powerSource: String? = null,
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
            power = pack?.power,
            powerSource = pack?.power?.let { p -> pack.source(p.source)?.name },
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
 * 16 꼭 챙길 물건 (DESIGN_SPEC 6-16, 재검토 R15): 수수료 고지(목록 위) → 짐 사진 머리 카드(챙긴 수·막대) →
 * (여행이 있으면) 여행지 전기 값 칩 카드(재검토2 ①#15·②#9·③#10 — 주제 이름이 아니라 값) →
 * **아직 안 챙긴 물건**(위, 흰 그림자 카드 — 강조) → **챙긴 물건**(아래, 그림자 없는 낮은 카드 — 체크 아이콘 + 흐린 글자).
 * 초록 채움 카드는 쓰지 않는다: 이미 챙긴 물건이 화면에서 가장 큰 색 덩어리가 되어 아직 안 챙긴 물건을 묻었다.
 * 체크 카드는 줄 전체가 Role.Checkbox 토글. 챙기면 그 카드가 아래 묶음으로 옮겨 간다(같은 key라 자리 이동 애니메이션).
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
        // 수수료 고지는 목록 맨 위 (PRD 5.10). 수수료 링크가 아직 없어도 원칙은 늘 보여 준다. 누를 수 없는 흰 띠 (D21).
        // 낱말은 '제휴'가 아니라 '수수료' — 입국 화면의 '정부 기관과 제휴하지 않았어요'와 겹쳐 "그럼 제휴한 거야?"로 읽혔다(재검토2 ⑤#9)
        item(key = "disclosure") {
            NoticeBanner(stringResource(R.string.essentials_fee_disclosure), icon = Icons.Outlined.Handshake)
        }
        if (ui.rows.isNotEmpty()) {
            item(key = "progress") { ProgressHero(ui) }
        }
        ui.power?.let { power ->
            item(key = "power") { PowerValuesCard(ui.countryKo, power, ui.powerSource) }
        }
        val todo = ui.rows.filter { !it.have }
        val done = ui.rows.filter { it.have }
        if (todo.isNotEmpty()) {
            // 아직 안 챙긴 물건은 머리 없이 사진 머리 카드(`준비한 물건 5개 중 2개`) 바로 아래 — 첫 화면에 한 장이라도 더 보이게
            todo.forEach { row ->
                item(key = "item-${row.rule.id}") { CheckRowCard(row, onHave, onOpenLink, Modifier.animateItem()) }
            }
        } else if (done.isNotEmpty()) {
            item(key = "all-done") { IconBullet(stringResource(R.string.essentials_all_done), Icons.Outlined.TaskAlt, tone = BadgeTone.Success) }
        }
        if (done.isNotEmpty()) {
            if (todo.isNotEmpty()) sectionGap("done-gap")
            item(key = "done-title") { GroupTitle(stringResource(R.string.essentials_done_title, done.size)) }
            done.forEach { row ->
                item(key = "item-${row.rule.id}") { CheckRowCard(row, onHave, onOpenLink, Modifier.animateItem()) }
            }
        }
    }
}

/** 챙긴 물건 묶음 머리 — 낮게(titleSmall InkSecondary). TalkBack 제목 */
@Composable
private fun GroupTitle(text: String) {
    KoText(text, MaterialTheme.typography.titleSmall, Modifier.padding(top = 4.dp), color = Tokens.InkSecondary, heading = true)
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
    // 사진은 위쪽(가방 지퍼·벽)을 보인다 — 가운데의 아이 운동화가 낮은 띠의 스크림 아래로 내려가게(재검토2 ①#7, 사진 교체는 운영자 결정 전까지)
    PhotoBox(Photos.Packing, minHeight = 112.dp, alignment = Alignment.TopCenter) {
        // 사진 위 글자·막대는 모두 스크림 영역 안 (3.7 ③)
        PhotoTextArea {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // '꼭 챙길 물건' 개념 아이콘은 하나(홈·여행 준비와 같은 Checklist, 재검토 R11)
                Icon(IconKeys.essentials, contentDescription = null, tint = OnDark.content, modifier = Modifier.size(dimens.icon))
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
 * 여행지 전기 값 칩 카드 (재검토2 ①#15·②#9·③#10 — `플러그·전압`처럼 주제 이름만 보이던 칩에 **값**을 붙인다):
 * 칩은 공용 [PowerChips] 한 벌(판정 → `220 V 전압`) — 홈 01·02·여행 준비 18과 같은 순서·색·말(v3에서 두 벌을 하나로).
 * 누를 수 없는 InfoChip(채움·테두리 없음). 출처·확인 날짜는 카드 맨 아래.
 * 목록에 어댑터·전압 확인 물건이 왜 있거나 없는지가 이 값들로 읽힌다.
 */
@Composable
private fun PowerValuesCard(countryKo: String?, power: PowerInfo, sourceName: String?) {
    val title = countryKo?.let { stringResource(R.string.essentials_power_title, it) } ?: stringResource(R.string.guide_power_title)
    val source = SourceRef(sourceName ?: stringResource(R.string.source_official_fallback), displayDate(power.lastVerified))
    CardNewsCard(title = title, icon = Icons.Outlined.Power, sources = listOf(source)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PowerChips(power)
        }
    }
}

/**
 * 물건 한 장 (CheckRowCard, 6-16 · 재검토 R15). 머리 줄 전체가 Role.Checkbox 토글 — 이름 + stateDescription(챙겼어요/아직이에요).
 * 이름은 카드 제목 규칙(titleMedium SemiBold — 섹션 머리보다 한 단계 작게, 재검토2 ①#2 연장).
 * - 아직: 흰 정보 카드(그림자) + Accent 배지 + 이름 Ink — 화면의 주인공.
 * - 챙김: 그림자 없는 흰 카드(낮게) + 회색 배지 + 이름 InkSecondary + 체크 아이콘·`챙겼어요`(앱이 확인한 상태라 체크) — 초록 채움 없음.
 * 상태 글자는 언제나 이름 **아래 줄**(이름 길이에 따라 옆·아래를 오가지 않게).
 * 큰 글자(Stacked)에서는 이름 길이와 관계없이 모든 카드가 배지·체크 상자를 윗줄에 두고 이름에 폭 전체를 준다(isStackedListRow — 재검토2 ④#6).
 */
@Composable
private fun CheckRowCard(row: EssentialRow, onHave: (String, Boolean) -> Unit, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val r = row.rule
    val dimens = LocalDimens.current
    val large = isStackedLayout()
    val shape = MaterialTheme.shapes.large
    val state = stringResource(if (row.have) R.string.essentials_have_yes else R.string.essentials_have_no)
    val bodyColor = if (row.have) Tokens.InkSecondary else Tokens.Ink
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        elevation = CardDefaults.cardElevation(0.dp),
        // 챙긴 물건은 그림자 없이 낮게 — 흰 카드 경계(옅은 1dp 테두리, 운영자 결정 6)만 남긴다
        modifier = modifier.fillMaxWidth().then(
            if (row.have) Modifier.border(CardBorderWidth, Tokens.LineSoft, shape) else Modifier.cardShadow(shape),
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
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
                badge = { IconBadge(essentialIcon(r.id), tone = if (row.have) BadgeTone.Neutral else BadgeTone.Accent) },
                trailing = {
                    Checkbox(
                        checked = row.have,
                        onCheckedChange = null,
                        // 체크 상자 색은 앱 전체 Accent 하나(동의 체크와 같게 — 재검토2 ④#8). '챙겼어요' 완료 뜻은 이름 아래 상태 글자가 맡는다
                        colors = CheckboxDefaults.colors(
                            checkedColor = Tokens.Accent,
                            uncheckedColor = Tokens.LineStrong,
                            checkmarkColor = Tokens.Surface,
                        ),
                    )
                },
                stack = large,
                gap = 12.dp,
                // 큰 글자: 물건 카드 모두 배지·체크 상자 윗줄 + 이름 폭 전체(이름 길이로 카드마다 모양이 갈리지 않게 — 재검토2 ④#6)
                forceStack = isStackedListRow(),
                title = { NameAndState(r.nameKo, row.have, state) },
            )
            if (r.ruleBadge == "carry_on_only") {
                StatusTag(stringResource(R.string.essentials_badge_carry_on), StatusKind.Caution)
            }
            // 규정 배지가 있는 물건은 규정 문장(숫자·금지)을 먼저 보인다 — 출처 줄이 가리키는 내용이 접힌 곳에 숨지 않게 (원칙 1)
            val (lead, rest) = splitLead(r.reasonKo, preferRule = r.ruleBadge != null)
            // 팩 문장 — 숫자 토큰 굵게(`1인당 2개(160Wh 이하)`, 재검토2 ③#1)
            NumberText(lead, MaterialTheme.typography.bodyMedium, color = bodyColor)
            if (rest != null) {
                // 무엇을 펼치는지 이름에 담는다 — `자세히 보기`만 여러 번 읽히지 않게 (재검토 R18), 접기에도 (재검토2 ②#2)
                ExpandableDetail(
                    label = stringResource(R.string.essentials_more, r.nameKo),
                    target = stringResource(R.string.fold_target_item, r.nameKo),
                ) {
                    NumberText(rest, MaterialTheme.typography.bodyMedium, color = bodyColor)
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
                    // '수수료 링크' 라벨은 제휴(affiliate) 링크에만. 보험·금융 안내(official_info)에는 붙이지 않는다
                    if (affiliate) {
                        Column(Modifier.align(Alignment.CenterVertically)) {
                            StatusChip(
                                stringResource(R.string.essentials_fee_link_label),
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

/**
 * 이름 + (챙겼으면) 그 아래 줄의 상태 글자 — 언제나 이름 아래 같은 자리(재검토 R15: 이름 길이에 따라 옆·아래를 오가지 않게).
 * 이름은 두 경우 모두 카드 제목 글자(titleMedium SemiBold — 섹션 머리 headlineSmall보다 한 단계 작게, 재검토2 ①#2 연장).
 * 챙김: 이름을 흐리게(InkSecondary), 상태는 체크 아이콘 + `챙겼어요`(SuccessText, 채움 없음 — 앱이 확인한 상태라 체크).
 * 아직: 이름 Ink만. 아직 안 챙긴 상태는 빈 체크 상자와 위 묶음 자리가 말해 준다(카드마다 `아직이에요`를 되풀이하지 않는다).
 * TalkBack은 두 경우 모두 줄의 stateDescription(`챙겼어요`/`아직이에요`)으로 한 번만 읽는다.
 */
@Composable
private fun NameAndState(name: String, have: Boolean, state: String) {
    val dimens = LocalDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KoText(
            name,
            MaterialTheme.typography.titleMedium,
            color = if (have) Tokens.InkSecondary else Tokens.Ink,
            glueShort = true,
        )
        if (have) {
            val style = MaterialTheme.typography.labelMedium
            Row(
                Modifier.clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Tokens.SuccessText, modifier = Modifier.size(textIconSize(dimens.iconSmall, style)))
                Text(state, style = style, color = Tokens.SuccessText)
            }
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
