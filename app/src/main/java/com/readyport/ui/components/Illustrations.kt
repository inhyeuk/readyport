package com.readyport.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.readyport.ui.theme.Tokens

// ======================= 그림 메뉴 일러스트 (DESIGN_SPEC 부록 E.6 — 나라 화면 길잡이 v4) =======================
// 운영자 지적(2026-10-02): "나라별 메뉴 컨셉은 좋았어. 다만 그 메뉴가 디자인적으로 볼품이 없으니 세련되게 이미지화해서 구성해줘."
// 섹션 메뉴(입국·비자 / 여행 정보 / 쇼핑)와 길 안내 모자이크가 같은 그림 언어를 쓴다.
// - 직접 그린 원본 그림(외부 자산·네트워크 없음). 64×64 격자, 외곽선 2.4(디테일 1.2~2.2) 둥근 끝·둥근 이음 — 앱 아이콘(Outlined)과 같은 둥근 선.
// - 색은 토큰만: 그림 하나에 **진한 외곽선 + 주색 + 옅은 중간색**(+ 금색 포인트). 갈래마다 한 계열 —
//   입국·비자 파랑(AccentDeep·BrandBlue·IllusBlueMid) / 여행 정보 청록(TealText·IllusTeal·IllusTealMid, 핀만 주황) /
//   쇼핑·도움 따뜻한 주황(Help·IllusWarm·IllusWarmMid) / 이동 보라(VioletText·IllusViolet·IllusVioletMid).
// - 그림은 꾸밈이다: 언제나 contentDescription = null(이름은 옆 글자가 말한다).
// - 이 파일의 `Illus` 경로 문자열은 scratchpad 생성기(nav4/illus.py)가 만든다 — 손으로 고칠 때는 미리보기 HTML로 함께 확인할 것.

/** 일러스트 한 계열의 색 (패널 그라데이션·썸네일 바탕) */
@Immutable
data class IllusTone(val soft: Color, val mid: Color, val deep: Color)

/** 갈래별 일러스트 색 계열 */
object IllusTones {
    val Blue = IllusTone(Tokens.AccentSoft, Tokens.IllusBlueMid, Tokens.AccentDeep)
    val Teal = IllusTone(Tokens.TealSoft, Tokens.IllusTealMid, Tokens.TealText)
    val Warm = IllusTone(Tokens.HelpSoft, Tokens.IllusWarmMid, Tokens.Help)
    val Violet = IllusTone(Tokens.VioletSoft, Tokens.IllusVioletMid, Tokens.VioletText)
}

/** 배지 톤 → 일러스트 계열 (모자이크 타일이 메뉴와 같은 색 언어를 쓰게) */
fun BadgeTone.illusTone(): IllusTone = when (this) {
    BadgeTone.Help, BadgeTone.Caution, BadgeTone.Danger -> IllusTones.Warm
    BadgeTone.Violet -> IllusTones.Violet
    BadgeTone.Teal, BadgeTone.Success -> IllusTones.Teal
    else -> IllusTones.Blue
}

/** 그림 + 색 계열 한 벌 (섹션 메뉴 칸·고정 줄 썸네일) */
@Immutable
data class SectionArt(val image: ImageVector, val tone: IllusTone)

/** 고른 칸 패널이 중간색 쪽으로 얼마나 짙어지는지 */
private const val RICH_MID = 0.55f

/** 고르지 않은 칸 패널의 옅은 정도(흰색 → soft 사이) */
private const val CALM_SOFT = 0.7f

/** 고르지 않은 칸 그림의 채도(1 = 원래 색) — 조용하지만 여전히 무엇인지 보이게 */
private const val CALM_SATURATION = 0.72f

/**
 * 고르지 않은 칸 그림을 흰색 쪽으로 씻어 내는 정도. 채도만 낮추면 주황이 흙빛(갈색)으로 탁해져서,
 * 채도는 조금만 낮추고 흰색 쪽으로 옅게 만들어 '파스텔'로 조용하게 한다.
 */
private const val CALM_WASH = 0.2f

/** 색 행렬의 더하기 칸(0~255 단위) */
private const val COLOR_MATRIX_MAX = 255f

/**
 * 그림 패널 바탕: 흰색에서 갈래 색으로 흐르는 대각선 그라데이션(왼쪽 위 → 오른쪽 아래).
 * [rich] = 고른 칸: soft를 지나 중간색 쪽으로 더 짙게. 아니면 흰색 → 옅은 soft(조용한 칸).
 * AccentSoft **단색 채움**은 '고르기 선택됨'(SelectableCard) 전용이라 쓰지 않는다 — 언제나 그라데이션이다(부록 E.1).
 */
fun illusPanelBrush(tone: IllusTone, rich: Boolean): Brush = if (rich) {
    Brush.linearGradient(
        0f to lerp(Tokens.Surface, tone.soft, 0.35f),
        0.5f to tone.soft,
        1f to lerp(tone.soft, tone.mid, RICH_MID),
    )
} else {
    Brush.linearGradient(0f to Tokens.Surface, 1f to lerp(Tokens.Surface, tone.soft, CALM_SOFT))
}

/** 조용한(고르지 않은) 칸 그림의 색 거르개 */
private val CalmFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(CALM_SATURATION)
        val keep = 1f - CALM_WASH
        for (row in 0..2) {
            for (col in 0..2) this[row, col] = this[row, col] * keep
            this[row, 4] = CALM_WASH * COLOR_MATRIX_MAX
        }
    },
)

/**
 * 고정 줄 썸네일 원 바탕: 고른 칸은 패널과 같은 짙은 그라데이션, 아니면 옅은 soft 그라데이션
 * (흰 줄 위에서 원이 사라지지 않게 패널보다 조금 짙게 시작한다).
 */
fun illusThumbBrush(tone: IllusTone, selected: Boolean): Brush = if (selected) {
    illusPanelBrush(tone, rich = true)
} else {
    Brush.linearGradient(0f to lerp(Tokens.Surface, tone.soft, 0.5f), 1f to tone.soft)
}

/**
 * 일러스트 그림 하나 (꾸밈 — 접근성 이름 없음). [calm] = 고르지 않은 칸: 채도를 낮춰 조용하게.
 */
@Composable
fun IllusImage(image: ImageVector, modifier: Modifier = Modifier, calm: Boolean = false) {
    Image(
        painter = rememberVectorPainter(image),
        contentDescription = null,
        modifier = modifier,
        colorFilter = if (calm) CalmFilter else null,
    )
}

/** 일러스트 격자(64×64)의 기본 그리기 크기 — 실제 크기는 쓰는 곳의 Modifier.size가 정한다 */
private val IllusDefaultSize = 64.dp

private inline fun illus(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, IllusDefaultSize, IllusDefaultSize, ILLUS_GRID, ILLUS_GRID).apply(block).build()

private const val ILLUS_GRID = 64f

/** 경로 하나: 채움·선(둥근 끝·둥근 이음) */
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

/** 묶음 하나: pivot 기준 회전·배율 + 이동 (VectorDrawable group과 같은 순서) */
private inline fun ImageVector.Builder.group(
    rotate: Float,
    pivotX: Float,
    pivotY: Float,
    translationX: Float,
    translationY: Float,
    scale: Float,
    block: ImageVector.Builder.() -> Unit,
) {
    addGroup(
        rotate = rotate, pivotX = pivotX, pivotY = pivotY,
        scaleX = scale, scaleY = scale, translationX = translationX, translationY = translationY,
    )
    block()
    clearGroup()
}

/** 길잡이 그림 모음 (모두 64×64, 꾸밈) */
object Illus {
    /** 입국·비자: 여권 + 입국 도장 찍힌 사증 면 + 작은 비행기 (파랑) */
    val Entry: ImageVector by lazy {
        illus("illus_entry") {
            p("M11,57A21,2.6 0 1 0 53,57A21,2.6 0 1 0 11,57Z", fill = Tokens.AccentDeep, fillAlpha = 0.14f)
            group(rotate = 10f, pivotX = 40f, pivotY = 34f, translationX = 0f, translationY = 0f, scale = 1f) {
                p("M32,16H48A4,4 0 0 1 52,20V48A4,4 0 0 1 48,52H32A4,4 0 0 1 28,48V20A4,4 0 0 1 32,16Z", fill = Color.White, stroke = Tokens.AccentDeep, width = 2.4f)
                p("M33,23L46,23", stroke = Tokens.IllusBlueMid, width = 2.2f)
                p("M33,28L41,28", stroke = Tokens.IllusBlueMid, width = 2.2f)
                p("M33.8,40.5A7.2,7.2 0 1 0 48.2,40.5A7.2,7.2 0 1 0 33.8,40.5Z", stroke = Tokens.BrandBlue, width = 2.1f)
                p("M36.4,40.5A4.6,4.6 0 1 0 45.6,40.5A4.6,4.6 0 1 0 36.4,40.5Z", stroke = Tokens.BrandBlue, width = 1.2f, strokeAlpha = 0.6f)
                p("M38.3,40.7L40.3,42.7L43.9,38.5", stroke = Tokens.BrandBlue, width = 2f)
            }
            group(rotate = -8f, pivotX = 22f, pivotY = 37.5f, translationX = 0f, translationY = 0f, scale = 1f) {
                p("M13.2,19.5H30.8A4.2,4.2 0 0 1 35,23.7V51.3A4.2,4.2 0 0 1 30.8,55.5H13.2A4.2,4.2 0 0 1 9,51.3V23.7A4.2,4.2 0 0 1 13.2,19.5Z", fill = Tokens.BrandBlue, stroke = Tokens.AccentDeep, width = 2.4f)
                p("M14,20.1V54.9", stroke = Tokens.AccentDeep, width = 1.4f, strokeAlpha = 0.35f)
                p("M27.5,21.6L32.6,21.6L18.6,53.4L13.5,53.4Z", fill = Color.White, fillAlpha = 0.1f)
                p("M16.1,33A6.4,6.4 0 1 0 28.9,33A6.4,6.4 0 1 0 16.1,33Z", stroke = Tokens.Gold, width = 1.9f)
                p("M19.8,33A2.7,6.4 0 1 0 25.2,33A2.7,6.4 0 1 0 19.8,33Z", stroke = Tokens.Gold, width = 1.4f)
                p("M16.1,33L28.9,33", stroke = Tokens.Gold, width = 1.4f)
                p("M17.5,46L27.5,46", stroke = Tokens.Gold, width = 2.2f)
                p("M19.5,50L25.5,50", stroke = Tokens.Gold, width = 1.6f, strokeAlpha = 0.7f)
            }
            p("M3.6,14.4A1,1 0 1 0 5.6,14.4A1,1 0 1 0 3.6,14.4Z", fill = Tokens.AccentDeep, fillAlpha = 0.4f)
            p("M7.2,12.2A1,1 0 1 0 9.2,12.2A1,1 0 1 0 7.2,12.2Z", fill = Tokens.AccentDeep, fillAlpha = 0.4f)
            p("M11,10.6A1,1 0 1 0 13,10.6A1,1 0 1 0 11,10.6Z", fill = Tokens.AccentDeep, fillAlpha = 0.4f)
            group(rotate = -16f, pivotX = 0f, pivotY = 0f, translationX = 21.5f, translationY = 7.6f, scale = 0.8f) {
                p("M9.6,0Q9.6,1.6 6.4,1.6L2.6,1.6L-2.2,8.2L-4.6,8.2L-2.4,1.6L-6.2,1.6L-8.2,4.4L-9.8,4.4L-8.8,0L-9.8,-4.4L-8.2,-4.4L-6.2,-1.6L-2.4,-1.6L-4.6,-8.2L-2.2,-8.2L2.6,-1.6L6.4,-1.6Q9.6,-1.6 9.6,0Z", fill = Color.White, stroke = Tokens.AccentDeep, width = 1.5f)
            }
            p("M56,5.4Q56.79,8.21 59.6,9Q56.79,9.79 56,12.6Q55.21,9.79 52.4,9Q55.21,8.21 56,5.4Z", fill = Tokens.Gold, stroke = Tokens.AccentDeep, width = 1.2f)
        }
    }

    /** 여행 정보: 접힌 지도 + 핀 + 점선 길 (청록 + 주황 핀) */
    val Travel: ImageVector by lazy {
        illus("illus_travel") {
            p("M9,59A23,2.4 0 1 0 55,59A23,2.4 0 1 0 9,59Z", fill = Tokens.TealText, fillAlpha = 0.14f)
            p("M7,21L23,15.5V52L7,57.5Z", fill = Tokens.IllusTealMid)
            p("M23,15.5L40,21.5V57.5L23,52Z", fill = Color.White)
            p("M40,21.5L57,16V52L40,57.5Z", fill = Tokens.IllusTealMid)
            p("M44,30Q49,26.5 53,29.5Q55,33 51.5,35.5Q47,37.5 44.5,34.5Q42.8,32.4 44,30Z", fill = Tokens.IllusTeal, fillAlpha = 0.9f)
            p("M10.5,40Q14,37 18,39.5Q20.5,42.5 17.5,45.5Q13.5,47.5 11,45Q9.2,42.6 10.5,40Z", fill = Tokens.IllusTeal, fillAlpha = 0.9f)
            p("M7,21L23,15.5L40,21.5L57,16V52L40,57.5L23,52L7,57.5Z", stroke = Tokens.TealText, width = 2.4f)
            p("M23,15.5L23,52", stroke = Tokens.TealText, width = 1.8f)
            p("M40,21.5L40,57.5", stroke = Tokens.TealText, width = 1.8f)
            p("M13.35,50.5A1.15,1.15 0 1 0 15.65,50.5A1.15,1.15 0 1 0 13.35,50.5Z", fill = Tokens.TealText)
            p("M17.45,48.2A1.15,1.15 0 1 0 19.75,48.2A1.15,1.15 0 1 0 17.45,48.2Z", fill = Tokens.TealText)
            p("M21.45,46.6A1.15,1.15 0 1 0 23.75,46.6A1.15,1.15 0 1 0 21.45,46.6Z", fill = Tokens.TealText)
            p("M25.45,45.6A1.15,1.15 0 1 0 27.75,45.6A1.15,1.15 0 1 0 25.45,45.6Z", fill = Tokens.TealText)
            p("M29.35,44.2A1.15,1.15 0 1 0 31.65,44.2A1.15,1.15 0 1 0 29.35,44.2Z", fill = Tokens.TealText)
            p("M32.45,41.6A1.15,1.15 0 1 0 34.75,41.6A1.15,1.15 0 1 0 32.45,41.6Z", fill = Tokens.TealText)
            p("M31.4,40.2A4.6,1.5 0 1 0 40.6,40.2A4.6,1.5 0 1 0 31.4,40.2Z", fill = Tokens.TealText, fillAlpha = 0.25f)
            p("M36,40C36,40 27.2,30.6 27.2,22.6A8.8,8.8 0 1 1 44.8,22.6C44.8,30.6 36,40 36,40Z", fill = Tokens.IllusWarm, stroke = Tokens.TealText, width = 2.4f)
            p("M32.7,22.6A3.3,3.3 0 1 0 39.3,22.6A3.3,3.3 0 1 0 32.7,22.6Z", fill = Color.White, stroke = Tokens.TealText, width = 1.6f)
            p("M30.6,19.4Q31.8,16.4 34.6,15.4", stroke = Color.White, width = 1.6f, strokeAlpha = 0.85f)
            p("M53.5,4.9Q54.29,7.71 57.1,8.5Q54.29,9.29 53.5,12.1Q52.71,9.29 49.9,8.5Q52.71,7.71 53.5,4.9Z", fill = Tokens.Gold, stroke = Tokens.TealText, width = 1.2f)
        }
    }

    /** 쇼핑: 쇼핑백 + 금색 꼬리표 (따뜻한 주황) */
    val Shopping: ImageVector by lazy {
        illus("illus_shopping") {
            p("M10,58.6A21,2.4 0 1 0 52,58.6A21,2.4 0 1 0 10,58.6Z", fill = Tokens.Help, fillAlpha = 0.14f)
            p("M21.5,29V21.5A8.5,8.5 0 0 1 38.5,21.5V29", stroke = Tokens.Help, width = 2.6f)
            p("M12.6,26H47.4L49.4,53.5Q49.6,57 46.2,57H13.8Q10.4,57 10.6,53.5Z", fill = Tokens.IllusWarm, stroke = Tokens.Help, width = 2.4f)
            p("M40.5,32.5H47.9L49.4,53.5Q49.6,57 46.2,57H41.6Z", fill = Tokens.Help, fillAlpha = 0.16f)
            p("M12.6,26H47.4L47.9,32.5H12.1Z", fill = Tokens.IllusWarmMid, stroke = Tokens.Help, width = 1.8f)
            p("M20,29.3A1.5,1.5 0 1 0 23,29.3A1.5,1.5 0 1 0 20,29.3Z", fill = Tokens.Help)
            p("M37,29.3A1.5,1.5 0 1 0 40,29.3A1.5,1.5 0 1 0 37,29.3Z", fill = Tokens.Help)
            p("M30,49C23.4,44.6 23.8,38.6 27.4,38.6Q29.2,38.6 30,40.6Q30.8,38.6 32.6,38.6C36.2,38.6 36.6,44.6 30,49Z", fill = Color.White)
            p("M38.5,29.3Q42.6,31.6 44.9,37.9", stroke = Tokens.Help, width = 1.4f)
            group(rotate = 16f, pivotX = 49f, pivotY = 39.5f, translationX = 0f, translationY = 0f, scale = 1f) {
                p("M44.2,32H55.4Q57.2,32 57.2,33.8V45.2Q57.2,47 55.4,47H44.2L40.4,39.5Z", fill = Tokens.Gold, stroke = Tokens.Help, width = 2f)
                p("M43.1,39.5A1.5,1.5 0 1 0 46.1,39.5A1.5,1.5 0 1 0 43.1,39.5Z", fill = Color.White, stroke = Tokens.Help, width = 1.2f)
                p("M48.6,37.4L53.6,37.4", stroke = Tokens.Help, width = 1.5f, strokeAlpha = 0.55f)
                p("M48.6,41.6L52.2,41.6", stroke = Tokens.Help, width = 1.5f, strokeAlpha = 0.55f)
            }
            p("M9,9.7Q9.84,12.66 12.8,13.5Q9.84,14.34 9,17.3Q8.16,14.34 5.2,13.5Q8.16,12.66 9,9.7Z", fill = Tokens.Gold, stroke = Tokens.Help, width = 1.2f)
            p("M51.5,10.6Q52.03,12.47 53.9,13Q52.03,13.53 51.5,15.4Q50.97,13.53 49.1,13Q50.97,12.47 51.5,10.6Z", fill = Tokens.IllusWarmMid, stroke = Tokens.Help, width = 1f)
        }
    }

    /** 현지어와 긴급 번호: 말풍선 + 전화 말풍선 (따뜻한 주황) */
    val Phrases: ImageVector by lazy {
        illus("illus_phrases") {
            p("M12,58.6A20,2.3 0 1 0 52,58.6A20,2.3 0 1 0 12,58.6Z", fill = Tokens.Help, fillAlpha = 0.14f)
            p("M16,11H32A9,9 0 0 1 41,20V26A9,9 0 0 1 32,35H23L14,42L16.4,35H16A9,9 0 0 1 7,26V20A9,9 0 0 1 16,11Z", fill = Color.White, stroke = Tokens.Help, width = 2.4f)
            p("M15,23A2,2 0 1 0 19,23A2,2 0 1 0 15,23Z", fill = Tokens.IllusWarm)
            p("M22,23A2,2 0 1 0 26,23A2,2 0 1 0 22,23Z", fill = Tokens.IllusWarm)
            p("M29,23A2,2 0 1 0 33,23A2,2 0 1 0 29,23Z", fill = Tokens.IllusWarm)
            p("M37,29H47A10,10 0 0 1 57,39V43A10,10 0 0 1 52,51.66L53.6,58.2L44.2,53H37A10,10 0 0 1 27,43V39A10,10 0 0 1 37,29Z", fill = Tokens.IllusWarm, stroke = Tokens.Help, width = 2.4f)
            group(rotate = 0f, pivotX = 0f, pivotY = 0f, translationX = 41.6f, translationY = 40.6f, scale = 1f) {
                p("M-4.6,-6.2Q-7,-3.5 -5.2,0.6Q-2.6,5.8 3.2,7.2Q6.4,7.8 7.3,5.4L5.3,2.8Q4.4,2.1 3.3,2.8L2,3.8Q-1.6,2.8 -3,-1.6L-2,-2.6Q-1.2,-3.6 -2.1,-4.6Z", fill = Color.White)
            }
            p("M53.5,10.6Q54.25,13.25 56.9,14Q54.25,14.75 53.5,17.4Q52.75,14.75 50.1,14Q52.75,13.25 53.5,10.6Z", fill = Tokens.Gold, stroke = Tokens.Help, width = 1.2f)
        }
    }

    /** 이동하기: 택시 (보라) */
    val Move: ImageVector by lazy {
        illus("illus_move") {
            p("M7,53.5A25,2.4 0 1 0 57,53.5A25,2.4 0 1 0 7,53.5Z", fill = Tokens.VioletText, fillAlpha = 0.14f)
            p("M28,15.5H36A2,2 0 0 1 38,17.5V20A2,2 0 0 1 36,22H28A2,2 0 0 1 26,20V17.5A2,2 0 0 1 28,15.5Z", fill = Tokens.Gold, stroke = Tokens.VioletText, width = 1.9f)
            p("M8.5,41.5Q8.5,35 14,33.8L19.6,24.8Q21.4,22 25,22H39Q42.6,22 44.4,24.8L50,33.8Q55.5,35 55.5,41.5V45Q55.5,48 52.5,48H11.5Q8.5,48 8.5,45Z", fill = Tokens.IllusViolet, stroke = Tokens.VioletText, width = 2.4f)
            p("M21,33.4L24.4,27.4Q25.2,26 26.8,26H30.6V33.4Z", fill = Color.White, stroke = Tokens.VioletText, width = 1.7f)
            p("M33.4,33.4V26H37.2Q38.8,26 39.6,27.4L43,33.4Z", fill = Color.White, stroke = Tokens.VioletText, width = 1.7f)
            p("M12.1,37.5H13.9A1.6,1.6 0 0 1 15.5,39.1V39.1A1.6,1.6 0 0 1 13.9,40.7H12.1A1.6,1.6 0 0 1 10.5,39.1V39.1A1.6,1.6 0 0 1 12.1,37.5Z", fill = Tokens.Gold, stroke = Tokens.VioletText, width = 1.3f)
            p("M50.1,37.5H51.9A1.6,1.6 0 0 1 53.5,39.1V39.1A1.6,1.6 0 0 1 51.9,40.7H50.1A1.6,1.6 0 0 1 48.5,39.1V39.1A1.6,1.6 0 0 1 50.1,37.5Z", fill = Color.White, stroke = Tokens.VioletText, width = 1.3f)
            p("M24,39.5L40,39.5", stroke = Tokens.VioletText, width = 1.6f, strokeAlpha = 0.45f)
            p("M13.9,48A5.6,5.6 0 1 0 25.1,48A5.6,5.6 0 1 0 13.9,48Z", fill = Tokens.VioletText)
            p("M17.3,48A2.2,2.2 0 1 0 21.7,48A2.2,2.2 0 1 0 17.3,48Z", fill = Tokens.IllusVioletMid)
            p("M38.9,48A5.6,5.6 0 1 0 50.1,48A5.6,5.6 0 1 0 38.9,48Z", fill = Tokens.VioletText)
            p("M42.3,48A2.2,2.2 0 1 0 46.7,48A2.2,2.2 0 1 0 42.3,48Z", fill = Tokens.IllusVioletMid)
            p("M4,22L11,22", stroke = Tokens.IllusVioletMid, width = 2.2f)
            p("M2,28L8,28", stroke = Tokens.IllusVioletMid, width = 2.2f)
        }
    }

    /** 여행 영상: 재생 화면 (파랑) */
    val Videos: ImageVector by lazy {
        illus("illus_videos") {
            p("M10,58A22,2.4 0 1 0 54,58A22,2.4 0 1 0 10,58Z", fill = Tokens.AccentDeep, fillAlpha = 0.14f)
            p("M14,13H50A7,7 0 0 1 57,20V42A7,7 0 0 1 50,49H14A7,7 0 0 1 7,42V20A7,7 0 0 1 14,13Z", fill = Tokens.BrandBlue, stroke = Tokens.AccentDeep, width = 2.4f)
            p("M15.5,17.5H48.5A4,4 0 0 1 52.5,21.5V34.5A4,4 0 0 1 48.5,38.5H15.5A4,4 0 0 1 11.5,34.5V21.5A4,4 0 0 1 15.5,17.5Z", fill = Tokens.AccentDeep, fillAlpha = 0.22f)
            p("M16,17.5H30L22,38.5H15.5A4,4 0 0 1 11.5,34.5V21.5A4,4 0 0 1 15.5,17.5Z", fill = Color.White, fillAlpha = 0.08f)
            p("M23.6,27A8.4,8.4 0 1 0 40.4,27A8.4,8.4 0 1 0 23.6,27Z", fill = Color.White, stroke = Tokens.AccentDeep, width = 2f)
            p("M29.6,22.6L36.6,27L29.6,31.4Z", fill = Tokens.BrandBlue, stroke = Tokens.BrandBlue, width = 1.6f)
            p("M13,43.2L51,43.2", stroke = Tokens.IllusBlueMid, width = 2.2f, strokeAlpha = 0.8f)
            p("M13,43.2L27,43.2", stroke = Tokens.Gold, width = 2.4f)
            p("M24.6,43.2A2.4,2.4 0 1 0 29.4,43.2A2.4,2.4 0 1 0 24.6,43.2Z", fill = Color.White, stroke = Tokens.AccentDeep, width = 1.2f)
            p("M26,49V53.5M38,49V53.5", stroke = Tokens.AccentDeep, width = 2.2f)
            p("M20,54L44,54", stroke = Tokens.AccentDeep, width = 2.4f)
            p("M55.5,4.9Q56.29,7.71 59.1,8.5Q56.29,9.29 55.5,12.1Q54.71,9.29 51.9,8.5Q54.71,7.71 55.5,4.9Z", fill = Tokens.Gold, stroke = Tokens.AccentDeep, width = 1.2f)
        }
    }

    /** 내 여행에 넣기: 여행 가방 + 더하기 배지 (파랑) */
    val PlanTrip: ImageVector by lazy {
        illus("illus_plantrip") {
            p("M9,58.6A21,2.4 0 1 0 51,58.6A21,2.4 0 1 0 9,58.6Z", fill = Tokens.AccentDeep, fillAlpha = 0.14f)
            p("M23,21.5V16.5Q23,14 25.5,14H34.5Q37,14 37,16.5V21.5", stroke = Tokens.AccentDeep, width = 2.6f)
            p("M17,21H43A7,7 0 0 1 50,28V47A7,7 0 0 1 43,54H17A7,7 0 0 1 10,47V28A7,7 0 0 1 17,21Z", fill = Tokens.BrandBlue, stroke = Tokens.AccentDeep, width = 2.4f)
            p("M41,22.2H43A5.8,5.8 0 0 1 48.8,28V47A5.8,5.8 0 0 1 43,52.8H41Z", fill = Tokens.AccentDeep, fillAlpha = 0.18f)
            p("M19,21.8L19,53.2", stroke = Tokens.AccentDeep, width = 1.8f, strokeAlpha = 0.5f)
            p("M41,21.8L41,53.2", stroke = Tokens.AccentDeep, width = 1.8f, strokeAlpha = 0.5f)
            p("M17.5,27H20.5A2,2 0 0 1 22.5,29V30A2,2 0 0 1 20.5,32H17.5A2,2 0 0 1 15.5,30V29A2,2 0 0 1 17.5,27Z", fill = Tokens.IllusBlueMid)
            p("M14.8,56.6A2.2,2.2 0 1 0 19.2,56.6A2.2,2.2 0 1 0 14.8,56.6Z", fill = Tokens.AccentDeep)
            p("M40.8,56.6A2.2,2.2 0 1 0 45.2,56.6A2.2,2.2 0 1 0 40.8,56.6Z", fill = Tokens.AccentDeep)
            p("M39.3,46A9.2,9.2 0 1 0 57.7,46A9.2,9.2 0 1 0 39.3,46Z", fill = Tokens.Gold, stroke = Tokens.AccentDeep, width = 2.4f)
            p("M48.5,41.2L48.5,50.8", stroke = Tokens.AccentDeep, width = 2.4f)
            p("M43.7,46L53.3,46", stroke = Tokens.AccentDeep, width = 2.4f)
            p("M8,8.6Q8.75,11.25 11.4,12Q8.75,12.75 8,15.4Q7.25,12.75 4.6,12Q7.25,11.25 8,8.6Z", fill = Tokens.Gold, stroke = Tokens.AccentDeep, width = 1.2f)
        }
    }

    /** 쇼핑 리스트 보기: 체크리스트 + 작은 쇼핑백 (따뜻한 주황) */
    val ShoppingList: ImageVector by lazy {
        illus("illus_shoppinglist") {
            p("M10,58.6A21,2.4 0 1 0 52,58.6A21,2.4 0 1 0 10,58.6Z", fill = Tokens.Help, fillAlpha = 0.14f)
            p("M13.5,10H34.5A4.5,4.5 0 0 1 39,14.5V48.5A4.5,4.5 0 0 1 34.5,53H13.5A4.5,4.5 0 0 1 9,48.5V14.5A4.5,4.5 0 0 1 13.5,10Z", fill = Color.White, stroke = Tokens.Help, width = 2.4f)
            p("M19.6,7H28.4A2.6,2.6 0 0 1 31,9.6V10.4A2.6,2.6 0 0 1 28.4,13H19.6A2.6,2.6 0 0 1 17,10.4V9.6A2.6,2.6 0 0 1 19.6,7Z", fill = Tokens.IllusWarmMid, stroke = Tokens.Help, width = 1.8f)
            p("M16.3,18.8H19.1A1.8,1.8 0 0 1 20.9,20.6V23.4A1.8,1.8 0 0 1 19.1,25.2H16.3A1.8,1.8 0 0 1 14.5,23.4V20.6A1.8,1.8 0 0 1 16.3,18.8Z", fill = Tokens.IllusWarm, stroke = Tokens.Help, width = 1.5f)
            p("M16,22L17.4,23.4L19.6,20.8", stroke = Color.White, width = 1.5f)
            p("M24,22L34,22", stroke = Tokens.IllusWarmMid, width = 2.2f)
            p("M16.3,27.8H19.1A1.8,1.8 0 0 1 20.9,29.6V32.4A1.8,1.8 0 0 1 19.1,34.2H16.3A1.8,1.8 0 0 1 14.5,32.4V29.6A1.8,1.8 0 0 1 16.3,27.8Z", fill = Tokens.IllusWarm, stroke = Tokens.Help, width = 1.5f)
            p("M16,31L17.4,32.4L19.6,29.8", stroke = Color.White, width = 1.5f)
            p("M24,31L31,31", stroke = Tokens.IllusWarmMid, width = 2.2f)
            p("M16.3,36.8H19.1A1.8,1.8 0 0 1 20.9,38.6V41.4A1.8,1.8 0 0 1 19.1,43.2H16.3A1.8,1.8 0 0 1 14.5,41.4V38.6A1.8,1.8 0 0 1 16.3,36.8Z", fill = Color.White, stroke = Tokens.Help, width = 1.5f)
            p("M24,40L34,40", stroke = Tokens.IllusWarmMid, width = 2.2f)
            p("M38.5,33.5V30.5A5.5,5.5 0 0 1 49.5,30.5V33.5", stroke = Tokens.Help, width = 2.2f)
            p("M33.5,33H54.5L55.6,51.4Q55.8,54.4 52.8,54.4H35.2Q32.2,54.4 32.4,51.4Z", fill = Tokens.IllusWarm, stroke = Tokens.Help, width = 2.4f)
            p("M49.5,34.2H53.3L54.4,51.3Q54.5,53.2 52.6,53.2H50.2Z", fill = Tokens.Help, fillAlpha = 0.16f)
            p("M44,49.4C39.6,46.4 39.9,42.4 42.3,42.4Q43.5,42.4 44,43.7Q44.5,42.4 45.7,42.4C48.1,42.4 48.4,46.4 44,49.4Z", fill = Color.White)
            p("M54,9.6Q54.75,12.25 57.4,13Q54.75,13.75 54,16.4Q53.25,13.75 50.6,13Q53.25,12.25 54,9.6Z", fill = Tokens.Gold, stroke = Tokens.Help, width = 1.2f)
        }
    }
}
