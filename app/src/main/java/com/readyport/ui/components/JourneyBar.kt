package com.readyport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.trip.JourneyStage
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

// ======================= 여행 과정 단계 (DESIGN_SPEC 부록 H — 2026-10-03) =======================
// 단계마다 그림(나라 화면 그림 메뉴 부록 E.6과 같은 그림 언어) + 이름 + 한 줄 설명 + **번호**를 가진다.
//
// 2026-10-03 다듬기(부록 H.7): 예전의 **단계 막대**(4칸 두 줄 그림 격자)는 없앴다.
// 운영자 지적: *"내 여행에서 계획, 예약, 등을 클릭하면 하단으로 이동한 뒤 상단으로 바로 이동할 수 있는 방법이 없어."*
// 막대를 누르면 아래 그 단계 카드로 **긴 스크롤**로 내려갔고, 올라오는 길이 없었다. 이제 단계 카드 자신이
// 접혔다 펴지는 아코디언([StageSectionCard]의 `open`)이라, 막대는 같은 일을 하는 두 번째 자리였다 — 그래서 지웠다.
// 막대가 들고 있던 것은 모두 카드 머리로 들어갔다: 그림 · 이름 · 진행(`3 / 7` + 막대) · `지금` 태그 · 그리고 **번호 배지**.

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

/** 단계 이름 (묶는 축) — 번호는 [StageStepBadge]가 따로 그린다(D17: 번호를 문자열에 넣지 않는다) */
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

/**
 * 번호까지 읽는 단계 이름 (`2단계 예약`) — TalkBack 이름·상태 태그처럼 **배지를 그릴 수 없는 자리**에서 쓴다.
 * 배지를 그릴 수 있는 자리(카드 머리)에서는 [StageStepBadge] + [journeyStageName]이다.
 */
@Composable
fun journeyStageStepName(stage: JourneyStage): String =
    stringResource(R.string.journey_stage_step_cd, stage.step, journeyStageName(stage))

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

/** 패널 대비 그림 크기 */
private const val PANEL_ART = 0.88f

/**
 * **단계 번호 배지** (운영자 2026-10-03: *"각 단계에 번호가 붙으면 좋겠어"*).
 * - 모양: 모서리 12dp **네모**(이 디자인의 '배지 모서리'). 둘러보기 히어로의 **여행 번호는 둥근 원**이다 —
 *   네모 = 몇 번째 **단계**, 원 = 몇 번째 **여행**. 같은 화면에 섞여 나오지 않지만 모양을 갈라 둔다(부록 H.7).
 * - 색: 지금 단계는 Accent 채움 + 흰 숫자(무엇을 할 때인지 한눈에), 나머지는 SurfaceSunken 채움 + Accent 숫자.
 * - 크기는 글자가 정한다([TextCircle]) — 200%에서도 숫자가 잘리지 않는다.
 * - TalkBack: 카드 머리 이름이 이미 `2단계 예약, …`을 읽으므로 배지는 숨긴다(같은 말 두 번 금지).
 */
@Composable
fun StageStepBadge(step: Int, now: Boolean, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    TextCircle(
        step.toString(),
        modifier = modifier.clearAndSetSemantics {},
        minSize = dimens.iconBadge,
        container = if (now) Tokens.Accent else Tokens.SurfaceSunken,
        content = if (now) Tokens.Surface else Tokens.Accent,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        shape = MaterialTheme.shapes.medium,
    )
}

/**
 * 단계 그림 패널 — 펼친 단계 카드의 설명 한 줄 옆에 둔다(그림 메뉴 부록 E.6과 같은 그림 언어).
 * 꾸밈이라 TalkBack에서 숨긴다(설명 글이 뜻을 말한다).
 */
@Composable
fun JourneyStageHeader(stage: JourneyStage, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val art = journeyStageArt(stage)
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(illusPanelBrush(art.tone, rich = true))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        IllusImage(art.image, Modifier.fillMaxSize(PANEL_ART))
    }
}
