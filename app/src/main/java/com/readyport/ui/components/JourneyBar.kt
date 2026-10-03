package com.readyport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.trip.JourneyStage
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 여행 과정 단계 막대 (DESIGN_SPEC 부록 H — 2026-10-03) =======================
// 나라 화면의 그림 메뉴(부록 E.6)와 **같은 그림 언어**를 여행 과정 8단계에 쓴다:
// 그라데이션 패널 위 일러스트 + 아래 라벨, 고른 칸은 떠오름 + 짙은 그라데이션 + 가운데 알약 막대 + Accent 굵은 글자.
// 다른 점은 ① 칸이 여덟이라 한 줄이 아니라 **4칸 두 줄**(좁거나 큰 글자면 2칸 네 줄)이고
// ② 다 한 단계는 패널 오른쪽 아래에 초록 체크 배지가 붙는다(진행이 한눈에 보이게)는 것뿐이다.
// 가로 스크롤은 쓰지 않는다 — 어떤 모드에서도 여덟 단계가 **모두 보인다**(나라 화면 탭 줄과 같은 원칙).

/** 단계 하나의 그림·색 계열 (나라 화면 갈래와 같은 [SectionArt]) */
fun journeyStageArt(stage: JourneyStage): SectionArt = when (stage) {
    JourneyStage.Plan -> SectionArt(Illus.Plan, IllusTones.Blue)
    JourneyStage.Book -> SectionArt(Illus.Book, IllusTones.Teal)
    // 서류 = 입국·비자와 같은 일 — 나라 화면 `입국·비자` 갈래와 같은 그림을 쓴다(같은 일에 같은 그림)
    JourneyStage.Docs -> SectionArt(Illus.Entry, IllusTones.Blue)
    JourneyStage.Pack -> SectionArt(Illus.Pack, IllusTones.Violet)
    JourneyStage.Departure -> SectionArt(Illus.Departure, IllusTones.Blue)
    JourneyStage.Arrival -> SectionArt(Illus.Arrival, IllusTones.Teal)
    JourneyStage.During -> SectionArt(Illus.During, IllusTones.Warm)
    JourneyStage.Return -> SectionArt(Illus.Return, IllusTones.Violet)
}

/** 단계 이름 (묶는 축) */
@Composable
fun journeyStageName(stage: JourneyStage): String = stringResource(
    when (stage) {
        JourneyStage.Plan -> R.string.journey_plan
        JourneyStage.Book -> R.string.journey_book
        JourneyStage.Docs -> R.string.journey_docs
        JourneyStage.Pack -> R.string.journey_pack
        JourneyStage.Departure -> R.string.journey_departure
        JourneyStage.Arrival -> R.string.journey_arrival
        JourneyStage.During -> R.string.journey_during
        JourneyStage.Return -> R.string.journey_return
    },
)

/** 단계가 무엇을 하는 때인지 한 줄 */
@Composable
fun journeyStageBody(stage: JourneyStage): String = stringResource(
    when (stage) {
        JourneyStage.Plan -> R.string.journey_plan_body
        JourneyStage.Book -> R.string.journey_book_body
        JourneyStage.Docs -> R.string.journey_docs_body
        JourneyStage.Pack -> R.string.journey_pack_body
        JourneyStage.Departure -> R.string.journey_departure_body
        JourneyStage.Arrival -> R.string.journey_arrival_body
        JourneyStage.During -> R.string.journey_during_body
        JourneyStage.Return -> R.string.journey_return_body
    },
)

/** 막대 한 칸 */
@Immutable
data class JourneyStageCell(
    val stage: JourneyStage,
    val done: Int,
    val total: Int,
    /** 오늘 기준 지금 단계인지 */
    val now: Boolean = false,
) {
    val complete: Boolean get() = total > 0 && done >= total
}

/** 칸 사이 간격 */
private val CellGap = 8.dp

/** 패널 테두리 안쪽 여백 */
private val CellInset = 5.dp

/** 고른 칸 아래 알약 막대 */
private val PillHeight = 4.dp
private val PillBottom = 5.dp
private const val PILL_FRACTION = 0.42f

/** 고른 칸의 떠오름 */
private val CellLift = 6.dp

/** 패널 대비 그림 크기 */
private const val PANEL_ART = 0.88f

/** 다 한 단계의 체크 배지 지름 */
private val DoneBadge = 20.dp

/**
 * 여행 과정 단계 막대 — 누르면 아래 그 단계로 간다(화면 틀, 값을 고르는 칩이 아니다).
 * - 4칸 두 줄. 쉬운 모드·큰 글자·좁은 창에서는 2칸 네 줄로 내려가고 **여덟 칸이 모두 보인다**(숨기거나 스크롤하지 않는다).
 * - 고른 칸 = 떠오름 + 짙은 그라데이션 + 알약 막대 + Accent 굵은 글자. 다 한 단계 = 패널에 초록 체크 배지.
 * - a11y: `selectableGroup()` + 칸마다 `selectable(role = Role.Tab)`, 이름 한 문장(`예약, 2번째 단계, 3개 중 3개 했어요`).
 *   그림은 꾸밈이라 이름이 없다.
 */
@Composable
fun JourneyStageBar(
    cells: List<JourneyStageCell>,
    selected: JourneyStage,
    onSelect: (JourneyStage) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cells.isEmpty()) return
    val dimens = LocalDimens.current
    val columns = if (dimens.easyMode || isStackedLayout() || isNarrowWindow()) 2 else 4
    TileGrid(cells, modifier, columns) { cell, cellModifier ->
        StageCell(cell, selected == cell.stage, { onSelect(cell.stage) }, cellModifier)
    }
}

/** 칸 바깥 모양 — 나라 화면 그림 메뉴 카드와 같은 틀(부록 E.6) */
private fun Modifier.stageCellFrame(shape: Shape, selected: Boolean, onClick: () -> Unit): Modifier = this
    .then(
        if (selected) {
            Modifier.shadow(CellLift, shape, clip = false, ambientColor = Tokens.ShadowAmbient, spotColor = Tokens.ShadowSpot)
        } else {
            Modifier
        },
    )
    .clip(shape)
    .background(Tokens.Surface)
    .then(if (selected) Modifier else Modifier.border(1.dp, Tokens.LineSoft, shape))
    .selectable(selected = selected, role = Role.Tab, onClick = onClick)
    .drawWithContent {
        drawContent()
        if (selected) {
            val h = PillHeight.toPx()
            val w = size.width * PILL_FRACTION
            drawRoundRect(
                Tokens.Accent,
                topLeft = Offset((size.width - w) / 2f, size.height - PillBottom.toPx() - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f),
            )
        }
    }

@Composable
private fun StageCell(cell: JourneyStageCell, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val dimens = LocalDimens.current
    val name = journeyStageName(cell.stage)
    val art = journeyStageArt(cell.stage)
    val nowLabel = stringResource(R.string.journey_now_stage)
    val description = stringResource(R.string.journey_bar_cd, name, cell.stage.barIndex + 1, cell.total, cell.done)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    Column(
        modifier
            .heightIn(min = dimens.minTouch)
            .stageCellFrame(MaterialTheme.shapes.large, selected, onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(CellInset).fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(illusPanelBrush(art.tone, rich = selected)),
                contentAlignment = Alignment.Center,
            ) {
                IllusImage(art.image, Modifier.fillMaxSize(PANEL_ART), calm = !selected)
            }
            if (cell.complete) DoneCheck(Modifier.align(Alignment.BottomEnd))
        }
        KoText(
            name,
            labelStyle,
            color = if (selected) Tokens.Accent else Tokens.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        // 칸이 좁아 둘 다 두면 글자가 겹친다 — 지금 단계는 `지금`, 아니면 진행 숫자(전체는 이름이 읽어 준다)
        KoText(
            if (cell.now) nowLabel else "${cell.done} / ${cell.total}",
            MaterialTheme.typography.labelMedium.copy(fontWeight = if (cell.now) FontWeight.Bold else FontWeight.Medium),
            color = when {
                cell.now -> Tokens.Accent
                cell.complete -> Tokens.SuccessText
                else -> Tokens.InkTertiary
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 1.dp, bottom = PillBottom + PillHeight + 4.dp),
        )
    }
}

/** 다 한 단계 표시 — 초록 원 + 흰 체크 (패널 오른쪽 아래). 꾸밈이라 이름이 없다(칸 이름이 숫자를 읽는다) */
@Composable
private fun DoneCheck(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(DoneBadge)
            .background(Tokens.SuccessText, CircleShape)
            .border(2.dp, Tokens.Surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = Tokens.Surface, modifier = Modifier.size(DoneBadge - 8.dp))
    }
}

/** 단계 머리 묶음: 그림 패널 + 이름 + 설명 한 줄 (단계 카드 안에서 쓰는 작은 모양) */
@Composable
fun JourneyStageHeader(stage: JourneyStage, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val art = journeyStageArt(stage)
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(illusPanelBrush(art.tone, rich = true)),
        contentAlignment = Alignment.Center,
    ) {
        IllusImage(art.image, Modifier.fillMaxSize(PANEL_ART))
    }
}

