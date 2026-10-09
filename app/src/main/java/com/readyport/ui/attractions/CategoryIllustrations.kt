package com.readyport.ui.attractions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Attractions
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.attractions.Category
import com.readyport.ui.theme.Tokens

// ======================= 관광지 종류 그림 (SPEC_v5 §2.2·§6.11) =======================
// 나라 화면 그림 메뉴(Illustrations.kt)와 같은 그림 언어: 64×64 격자, 외곽선 2.4(디테일 1.2~2), 둥근 끝·둥근 이음,
// 여행 정보 갈래 색(청록: TealText 외곽선 · IllusTeal 주색 · IllusTealMid 중간색) + 금색 포인트. 종류는 색이 아니라 그림·글자가 구분한다.
// 직접 그린 원본 그림(외부 자산·네트워크 없음). 그림은 꾸밈 — 언제나 contentDescription = null.

private const val GRID = 64f

private inline fun art(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 64.dp, 64.dp, GRID, GRID).apply(block).build()

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

private val Line = Tokens.TealText
private val Main = Tokens.IllusTeal
private val Mid = Tokens.IllusTealMid

private fun ImageVector.Builder.ground() =
    p("M9,59A23,2.4 0 1 0 55,59A23,2.4 0 1 0 9,59Z", fill = Line, fillAlpha = 0.14f)

private fun ImageVector.Builder.sparkle(x: Float, y: Float) {
    p("M$x,${y - 3.6f}Q${x + 0.8f},${y - 0.8f} ${x + 3.6f},${y}Q${x + 0.8f},${y + 0.8f} $x,${y + 3.6f}Q${x - 0.8f},${y + 0.8f} ${x - 3.6f},${y}Q${x - 0.8f},${y - 0.8f} $x,${y - 3.6f}Z", fill = Tokens.Gold, stroke = Line, width = 1.2f)
}

object CategoryArt {
    /** 역사·유적: 기와지붕 문루 */
    val Heritage: ImageVector by lazy {
        art("att_heritage") {
            ground()
            p("M12,50H52V56H12Z", fill = Mid, stroke = Line, width = 2.4f)
            p("M17,34H24V50H17Z", fill = Color.White, stroke = Line, width = 2f)
            p("M40,34H47V50H40Z", fill = Color.White, stroke = Line, width = 2f)
            p("M27,50V41Q32,35 37,41V50Z", fill = Line, fillAlpha = 0.22f, stroke = Line, width = 1.6f)
            p("M13,30H51V35H13Z", fill = Mid, stroke = Line, width = 2f)
            p("M7,29Q19,25 32,14Q45,25 57,29L52,31H12Z", fill = Main, stroke = Line, width = 2.4f)
            p("M15,27Q24,24 32,17Q40,24 49,27", stroke = Color.White, width = 1.4f, strokeAlpha = 0.7f)
            p("M30,12.5A2,2 0 1 0 34,12.5A2,2 0 1 0 30,12.5Z", fill = Tokens.Gold, stroke = Line, width = 1.2f)
            sparkle(54f, 9f)
        }
    }

    /** 산·자연: 봉우리 + 폭포 + 해 */
    val Nature: ImageVector by lazy {
        art("att_nature") {
            ground()
            p("M5,56L23,22L33,36L41,26L59,56Z", fill = Mid, stroke = Line, width = 2.4f)
            p("M23,22L18.5,30.5L22,29L25,32L28,28.5Z", fill = Color.White, stroke = Line, width = 1.4f)
            p("M41,26L37.5,31.5L40.5,30.5L43,33L45,30Z", fill = Color.White, stroke = Line, width = 1.4f)
            p("M35,36H41V53H35Z", fill = Color.White, stroke = Line, width = 1.6f)
            p("M37,39V50", stroke = Main, width = 1.2f)
            p("M39,41V51", stroke = Main, width = 1.2f)
            p("M28,55A10,2.6 0 1 0 48,55A10,2.6 0 1 0 28,55Z", fill = Main, stroke = Line, width = 1.6f)
            p("M47,13A5,5 0 1 0 57,13A5,5 0 1 0 47,13Z", fill = Tokens.Gold, stroke = Line, width = 1.4f)
        }
    }

    /** 바다·섬: 야자수 + 모래섬 + 물결 */
    val SeaIsland: ImageVector by lazy {
        art("att_sea_island") {
            ground()
            p("M4,51Q10,47.5 16,51T28,51T40,51T52,51T60,51V57H4Z", fill = Mid, stroke = Line, width = 2f)
            p("M13,51Q32,37 51,51Z", fill = Tokens.Gold, stroke = Line, width = 2.2f)
            p("M32,45Q29.5,33 34,21", stroke = Line, width = 2.8f)
            p("M34,21Q24,15.5 17,22Q25.5,19.5 33.5,23Z", fill = Main, stroke = Line, width = 1.4f)
            p("M34,21Q44,14 51,20.5Q42.5,18.5 34.5,23Z", fill = Main, stroke = Line, width = 1.4f)
            p("M34,21Q30.5,10.5 23.5,9.5Q29.5,14 33,22Z", fill = Main, stroke = Line, width = 1.4f)
            p("M34,21Q40.5,10 47.5,11Q40.5,14 35,22Z", fill = Main, stroke = Line, width = 1.4f)
            p("M8,55Q12,53.5 16,55", stroke = Color.White, width = 1.2f, strokeAlpha = 0.8f)
            p("M44,55Q48,53.5 52,55", stroke = Color.White, width = 1.2f, strokeAlpha = 0.8f)
            sparkle(10f, 12f)
        }
    }

    /** 도시·전망: 스카이라인 + 전망대 */
    val CityView: ImageVector by lazy {
        art("att_city_view") {
            ground()
            p("M7,56V37H17V56Z", fill = Mid, stroke = Line, width = 2f)
            p("M17,56V29H27V56Z", fill = Color.White, stroke = Line, width = 2f)
            p("M41,56V33H51V56Z", fill = Color.White, stroke = Line, width = 2f)
            p("M51,56V41H58V56Z", fill = Mid, stroke = Line, width = 2f)
            p("M20,34H24M20,39H24M20,44H24M44,38H48M44,43H48M44,48H48", stroke = Main, width = 1.6f)
            p("M31,56L33.2,26H36.8L39,56Z", fill = Main, stroke = Line, width = 2f)
            p("M28.5,26H41.5L39.5,20H30.5Z", fill = Color.White, stroke = Line, width = 2f)
            p("M35,20V10", stroke = Line, width = 1.8f)
            p("M33.4,9.2A1.6,1.6 0 1 0 36.6,9.2A1.6,1.6 0 1 0 33.4,9.2Z", fill = Tokens.Gold, stroke = Line, width = 1f)
            p("M5,56H59", stroke = Line, width = 2.4f)
            sparkle(54f, 13f)
        }
    }

    /** 시장·쇼핑거리: 노점 차양 + 줄에 단 등 */
    val MarketStreet: ImageVector by lazy {
        art("att_market_street") {
            ground()
            p("M7,13Q32,22 57,13", stroke = Line, width = 1.4f)
            p("M17.5,18.5A2.6,3.4 0 1 0 22.7,18.5A2.6,3.4 0 1 0 17.5,18.5Z", fill = Tokens.Gold, stroke = Line, width = 1.2f)
            p("M29.4,20.6A2.6,3.4 0 1 0 34.6,20.6A2.6,3.4 0 1 0 29.4,20.6Z", fill = Tokens.IllusWarm, stroke = Line, width = 1.2f)
            p("M41.3,18.5A2.6,3.4 0 1 0 46.5,18.5A2.6,3.4 0 1 0 41.3,18.5Z", fill = Tokens.Gold, stroke = Line, width = 1.2f)
            p("M12,38H52V56H12Z", fill = Color.White, stroke = Line, width = 2.4f)
            p("M9,27H55V35Q51.2,39 47.3,35Q43.5,39 39.7,35Q35.8,39 32,35Q28.2,39 24.3,35Q20.5,39 16.7,35Q12.8,39 9,35Z", fill = Main, stroke = Line, width = 2f)
            p("M16.7,27V35M24.3,27V35M32,27V35M39.7,27V35M47.3,27V35", stroke = Color.White, width = 1.4f, strokeAlpha = 0.75f)
            p("M12,47H52", stroke = Line, width = 1.8f)
            p("M17,44.6A2.4,2.4 0 1 0 21.8,44.6A2.4,2.4 0 1 0 17,44.6Z", fill = Tokens.Gold, stroke = Line, width = 1.1f)
            p("M23.5,44.6A2.4,2.4 0 1 0 28.3,44.6A2.4,2.4 0 1 0 23.5,44.6Z", fill = Tokens.IllusWarm, stroke = Line, width = 1.1f)
            p("M36,42H46V47H36Z", fill = Mid, stroke = Line, width = 1.2f)
        }
    }

    /** 박물관·미술관: 기둥 건물 + 금색 액자 */
    val Museum: ImageVector by lazy {
        art("att_museum") {
            ground()
            p("M8,24L32,10L56,24Z", fill = Main, stroke = Line, width = 2.4f)
            p("M29.5,18.6A2.5,2.5 0 1 0 34.5,18.6A2.5,2.5 0 1 0 29.5,18.6Z", fill = Tokens.Gold, stroke = Line, width = 1.2f)
            p("M10,24H54V29H10Z", fill = Mid, stroke = Line, width = 2f)
            p("M14,29H19V50H14Z", fill = Color.White, stroke = Line, width = 1.8f)
            p("M24,29H29V50H24Z", fill = Color.White, stroke = Line, width = 1.8f)
            p("M35,29H40V50H35Z", fill = Color.White, stroke = Line, width = 1.8f)
            p("M8,50H56V56H8Z", fill = Mid, stroke = Line, width = 2.4f)
            p("M43,35H59V51H43Z", fill = Tokens.Gold, stroke = Line, width = 2f)
            p("M46.5,38.5H55.5V47.5H46.5Z", fill = Color.White, stroke = Line, width = 1.2f)
            p("M47.5,46.5L50.5,42.5L52.5,45L53.5,44L55,46.5Z", fill = Main)
        }
    }

    /** 테마파크·체험: 관람차 */
    val ThemePark: ImageVector by lazy {
        art("att_theme_park") {
            ground()
            p("M23,57L32,30L41,57", stroke = Line, width = 2.4f)
            p("M17,57H47", stroke = Line, width = 2.4f)
            p("M14,30A18,18 0 1 0 50,30A18,18 0 1 0 14,30Z", fill = Mid, fillAlpha = 0.35f, stroke = Line, width = 2.4f)
            p("M32,12V48M14,30H50M19.3,17.3L44.7,42.7M44.7,17.3L19.3,42.7", stroke = Line, width = 1.3f, strokeAlpha = 0.8f)
            p("M28.8,12A3.2,3.2 0 1 0 35.2,12A3.2,3.2 0 1 0 28.8,12Z", fill = Main, stroke = Line, width = 1.4f)
            p("M41.5,17.3A3.2,3.2 0 1 0 47.9,17.3A3.2,3.2 0 1 0 41.5,17.3Z", fill = Tokens.Gold, stroke = Line, width = 1.4f)
            p("M46.8,30A3.2,3.2 0 1 0 53.2,30A3.2,3.2 0 1 0 46.8,30Z", fill = Main, stroke = Line, width = 1.4f)
            p("M41.5,42.7A3.2,3.2 0 1 0 47.9,42.7A3.2,3.2 0 1 0 41.5,42.7Z", fill = Tokens.IllusWarm, stroke = Line, width = 1.4f)
            p("M28.8,48A3.2,3.2 0 1 0 35.2,48A3.2,3.2 0 1 0 28.8,48Z", fill = Main, stroke = Line, width = 1.4f)
            p("M16.1,42.7A3.2,3.2 0 1 0 22.5,42.7A3.2,3.2 0 1 0 16.1,42.7Z", fill = Tokens.Gold, stroke = Line, width = 1.4f)
            p("M10.8,30A3.2,3.2 0 1 0 17.2,30A3.2,3.2 0 1 0 10.8,30Z", fill = Main, stroke = Line, width = 1.4f)
            p("M16.1,17.3A3.2,3.2 0 1 0 22.5,17.3A3.2,3.2 0 1 0 16.1,17.3Z", fill = Tokens.IllusWarm, stroke = Line, width = 1.4f)
            p("M29.5,30A2.5,2.5 0 1 0 34.5,30A2.5,2.5 0 1 0 29.5,30Z", fill = Tokens.Gold, stroke = Line, width = 1.2f)
        }
    }

    fun of(category: Category): ImageVector = when (category) {
        Category.Heritage -> Heritage
        Category.Nature -> Nature
        Category.SeaIsland -> SeaIsland
        Category.CityView -> CityView
        Category.MarketStreet -> MarketStreet
        Category.Museum -> Museum
        Category.ThemePark -> ThemePark
    }
}

/** 종류 → 목록 아이콘 (§2.2) */
fun Category.icon(): ImageVector = when (this) {
    Category.Heritage -> Icons.Outlined.AccountBalance
    Category.Nature -> Icons.Outlined.Landscape
    Category.SeaIsland -> Icons.Outlined.BeachAccess
    Category.CityView -> Icons.Outlined.LocationCity
    Category.MarketStreet -> Icons.Outlined.Storefront
    Category.Museum -> Icons.Outlined.Museum
    Category.ThemePark -> Icons.Outlined.Attractions
}

/** 종류 → 화면 라벨 문자열 키 */
fun Category.labelRes(): Int = when (this) {
    Category.Heritage -> R.string.attractions_cat_heritage
    Category.Nature -> R.string.attractions_cat_nature
    Category.SeaIsland -> R.string.attractions_cat_sea_island
    Category.CityView -> R.string.attractions_cat_city_view
    Category.MarketStreet -> R.string.attractions_cat_market_street
    Category.Museum -> R.string.attractions_cat_museum
    Category.ThemePark -> R.string.attractions_cat_theme_park
}
