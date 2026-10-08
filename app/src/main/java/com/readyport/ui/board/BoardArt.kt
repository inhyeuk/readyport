package com.readyport.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readyport.board.BoardKind
import com.readyport.ui.components.IllusImage
import com.readyport.ui.components.IllusTone
import com.readyport.ui.components.IllusTones
import com.readyport.ui.components.illusPanelBrush
import com.readyport.ui.theme.Tokens

// ======================= 게시판 그림 (DESIGN_SPEC 부록 L.2 — 그림 언어는 부록 E.6 `Illus`와 같다) =======================
// 64×64 격자, 외곽선 2.4(디테일 1.2~2.2) 둥근 끝·둥근 이음, 색은 토큰만(진한 외곽선 + 주색 + 옅은 중간색 + 금색 포인트 + 바닥 그림자 14%).
// 직접 그린 원본 벡터 — 외부 자산·네트워크 없음. 언제나 꾸밈(contentDescription = null).

private val ArtSize = 64.dp
private const val GRID = 64f

private inline fun art(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, ArtSize, ArtSize, GRID, GRID).apply(block).build()

private fun ImageVector.Builder.p(
    d: String,
    fill: Color? = null,
    fillAlpha: Float = 1f,
    stroke: Color? = null,
    width: Float = 0f,
    strokeAlpha: Float = 1f,
) {
    addPath(
        pathData = addPathNodes(d),
        fill = fill?.let { SolidColor(it) },
        fillAlpha = fillAlpha,
        stroke = stroke?.let { SolidColor(it) },
        strokeAlpha = strokeAlpha,
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

/** 반짝이 (금색 별 하나) */
private fun ImageVector.Builder.sparkle(cx: Float, cy: Float, r: Float, outline: Color) {
    val k = r * 0.28f
    p(
        "M$cx,${cy - r}Q${cx + k},${cy - k} ${cx + r},${cy}Q${cx + k},${cy + k} $cx,${cy + r}Q${cx - k},${cy + k} ${cx - r},${cy}Q${cx - k},${cy - k} $cx,${cy - r}Z",
        fill = Tokens.Gold, stroke = outline, width = 1.2f,
    )
}

object BoardIllus {
    /** Q&A: 물음표 말풍선 + 확인 표시 말풍선 (파랑) */
    val Qna: ImageVector by lazy {
        art("board_qna") {
            p("M11,58.4A21,2.4 0 1 0 53,58.4A21,2.4 0 1 0 11,58.4Z", fill = Tokens.AccentDeep, fillAlpha = 0.14f)
            p("M15,9H35A7,7 0 0 1 42,16V29A7,7 0 0 1 35,36H22L13.5,43L15.8,36H15A7,7 0 0 1 8,29V16A7,7 0 0 1 15,9Z", fill = Tokens.BrandBlue, stroke = Tokens.AccentDeep, width = 2.4f)
            p("M16,13H24L18,32H15A3,3 0 0 1 12,29V16A3,3 0 0 1 15,13Z", fill = Color.White, fillAlpha = 0.1f)
            p("M20.6,18.6Q21.2,14.4 25,14.4Q29.4,14.4 29.4,18.4Q29.4,21.2 26.6,22.6Q25,23.4 25,25.8", stroke = Color.White, width = 3f)
            p("M23.2,30.4A1.8,1.8 0 1 0 26.8,30.4A1.8,1.8 0 1 0 23.2,30.4Z", fill = Color.White)
            p("M36,29H50A7,7 0 0 1 57,36V43A7,7 0 0 1 50,50H48.4L50.2,56.6L42.2,50H36A7,7 0 0 1 29,43V36A7,7 0 0 1 36,29Z", fill = Color.White, stroke = Tokens.AccentDeep, width = 2.4f)
            p("M36.8,39.4L41.2,43.6L49.4,35.4", stroke = Tokens.BrandBlue, width = 2.8f)
            p("M45,9.5L51,9.5", stroke = Tokens.IllusBlueMid, width = 2.2f)
            p("M47,15L55,15", stroke = Tokens.IllusBlueMid, width = 2.2f)
            sparkle(55.5f, 6.5f, 3.4f, Tokens.AccentDeep)
        }
    }

    /** 자유 토론: 이야기 말풍선 둘 + 하트 (청록) */
    val Talk: ImageVector by lazy {
        art("board_talk") {
            p("M10,58.4A22,2.4 0 1 0 54,58.4A22,2.4 0 1 0 10,58.4Z", fill = Tokens.TealText, fillAlpha = 0.14f)
            p("M30,8H50A7,7 0 0 1 57,15V26A7,7 0 0 1 50,33H48.2L49.8,39.6L42,33H30A7,7 0 0 1 23,26V15A7,7 0 0 1 30,8Z", fill = Tokens.IllusTealMid, stroke = Tokens.TealText, width = 2.4f)
            p("M30.5,17H49", stroke = Tokens.TealText, width = 2.2f, strokeAlpha = 0.55f)
            p("M30.5,23.6H42", stroke = Tokens.TealText, width = 2.2f, strokeAlpha = 0.55f)
            p("M14,24H36A7,7 0 0 1 43,31V43A7,7 0 0 1 36,50H21L12.6,56.8L14.8,50H14A7,7 0 0 1 7,43V31A7,7 0 0 1 14,24Z", fill = Tokens.IllusTeal, stroke = Tokens.TealText, width = 2.4f)
            p("M25,44.4C20.2,40.9 18,38.4 18,35.6C18,33.3 19.8,31.6 21.9,31.6C23.2,31.6 24.4,32.3 25,33.4C25.6,32.3 26.8,31.6 28.1,31.6C30.2,31.6 32,33.3 32,35.6C32,38.4 29.8,40.9 25,44.4Z", fill = Color.White)
            p("M36.6,28.6A1.6,1.6 0 1 0 39.8,28.6A1.6,1.6 0 1 0 36.6,28.6Z", fill = Tokens.IllusWarm)
            sparkle(9.5f, 11.5f, 3.4f, Tokens.TealText)
        }
    }

    /** 빈 목록: 빈 공책 + 연필 (보라) */
    val Empty: ImageVector by lazy {
        art("board_empty") {
            p("M10,58.4A20,2.4 0 1 0 50,58.4A20,2.4 0 1 0 10,58.4Z", fill = Tokens.VioletText, fillAlpha = 0.14f)
            p("M15,9H40A5,5 0 0 1 45,14V50A5,5 0 0 1 40,55H15A5,5 0 0 1 10,50V14A5,5 0 0 1 15,9Z", fill = Color.White, stroke = Tokens.VioletText, width = 2.4f)
            p("M6.5,17H13M6.5,27H13M6.5,37H13M6.5,47H13", stroke = Tokens.VioletText, width = 2.4f)
            p("M18.5,20H37", stroke = Tokens.IllusVioletMid, width = 2.4f)
            p("M18.5,28H37", stroke = Tokens.IllusVioletMid, width = 2.4f)
            p("M18.5,36H29", stroke = Tokens.IllusVioletMid, width = 2.4f)
            p("M56.6,26.2L59.4,29L42.6,45.8L37.8,47.6L39.6,42.8Z", fill = Tokens.IllusViolet, stroke = Tokens.VioletText, width = 2.2f)
            p("M53.8,29L56.6,31.8", stroke = Tokens.VioletText, width = 1.8f)
            p("M39.6,42.8L42.6,45.8L37.8,47.6Z", fill = Tokens.Gold, stroke = Tokens.VioletText, width = 1.6f)
            sparkle(54f, 10f, 3.4f, Tokens.VioletText)
            p("M58.4,17.6A1.5,1.5 0 1 0 61.4,17.6A1.5,1.5 0 1 0 58.4,17.6Z", fill = Tokens.IllusViolet, fillAlpha = 0.5f)
        }
    }

    /** 커뮤니티 규칙: 방패 + 체크 (청록) */
    val Rules: ImageVector by lazy {
        art("board_rules") {
            p("M13,58.6A19,2.3 0 1 0 51,58.6A19,2.3 0 1 0 13,58.6Z", fill = Tokens.TealText, fillAlpha = 0.14f)
            p("M32,6L52,13.4V29.6C52,42.4 43.6,51.6 32,56C20.4,51.6 12,42.4 12,29.6V13.4Z", fill = Tokens.IllusTeal, stroke = Tokens.TealText, width = 2.4f)
            p("M32,11.6L47,17.2V29.6C47,39.6 40.8,46.8 32,50.6Z", fill = Color.White, fillAlpha = 0.18f)
            p("M22.4,31.2L29.2,38L42.4,24.4", stroke = Color.White, width = 3.8f)
            sparkle(54.5f, 8f, 3.4f, Tokens.TealText)
            p("M6.6,20.6A1.6,1.6 0 1 0 9.8,20.6A1.6,1.6 0 1 0 6.6,20.6Z", fill = Tokens.IllusTealMid)
        }
    }

    /** 게시판 이름: 이름표 + 얼굴 원 (따뜻한 주황) */
    val Nickname: ImageVector by lazy {
        art("board_nickname") {
            p("M10,58.4A22,2.4 0 1 0 54,58.4A22,2.4 0 1 0 10,58.4Z", fill = Tokens.Help, fillAlpha = 0.14f)
            p("M14,15H50A6,6 0 0 1 56,21V47A6,6 0 0 1 50,53H14A6,6 0 0 1 8,47V21A6,6 0 0 1 14,15Z", fill = Color.White, stroke = Tokens.Help, width = 2.4f)
            p("M14,15H50A6,6 0 0 1 56,21V26.5H8V21A6,6 0 0 1 14,15Z", fill = Tokens.IllusWarm, stroke = Tokens.Help, width = 1.8f)
            p("M26.5,8H37.5A2.5,2.5 0 0 1 40,10.5V20A2.5,2.5 0 0 1 37.5,22.5H26.5A2.5,2.5 0 0 1 24,20V10.5A2.5,2.5 0 0 1 26.5,8Z", fill = Tokens.Gold, stroke = Tokens.Help, width = 2f)
            p("M28.5,17.6H35.5", stroke = Tokens.Help, width = 1.8f)
            p("M13.6,39.6A6.8,6.8 0 1 0 27.2,39.6A6.8,6.8 0 1 0 13.6,39.6Z", fill = Tokens.IllusWarmMid, stroke = Tokens.Help, width = 2f)
            p("M17.2,37.6A1.2,1.2 0 1 0 19.6,37.6A1.2,1.2 0 1 0 17.2,37.6ZM21.2,37.6A1.2,1.2 0 1 0 23.6,37.6A1.2,1.2 0 1 0 21.2,37.6Z", fill = Tokens.Help)
            p("M17.6,41.6Q20.4,44 23.2,41.6", stroke = Tokens.Help, width = 1.6f)
            p("M31.5,36.5H48", stroke = Tokens.Help, width = 2.6f, strokeAlpha = 0.85f)
            p("M31.5,43.5H43", stroke = Tokens.IllusWarmMid, width = 2.6f)
            sparkle(55.5f, 7f, 3.4f, Tokens.Help)
        }
    }
}

/** 게시판 종류 → 그림·색 계열 */
fun BoardKind.art(): ImageVector = if (this == BoardKind.Qna) BoardIllus.Qna else BoardIllus.Talk

fun BoardKind.tone(): IllusTone = if (this == BoardKind.Qna) IllusTones.Blue else IllusTones.Teal

/**
 * 그림 패널(흰색 → 색 계열 그라데이션 + 그림) — 그림 메뉴(부록 E.6)·단계 머리(JourneyStageHeader)와 같은 모양. 꾸밈이라 TalkBack에서 숨긴다.
 */
@Composable
fun ArtPanel(image: ImageVector, tone: IllusTone, size: Dp, modifier: Modifier = Modifier, rich: Boolean = true) {
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(illusPanelBrush(tone, rich))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        IllusImage(image, Modifier.fillMaxSize(PANEL_ART))
    }
}

/** 패널 안 그림이 차지하는 비율 */
private const val PANEL_ART = 0.78f
