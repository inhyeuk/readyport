package com.readyport.ui.attractions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.readyport.BuildConfig
import com.readyport.attractions.Attraction
import com.readyport.attractions.Region
import com.readyport.net.onlineFlow

/**
 * 관광지 지도 (Google 지도 SDK, 사장님 결정 2026-10-09) — 보일지 말지와 표시(핀) 계산. 순수 계산이라 테스트한다.
 *
 * 지도를 그리는 조건(모두 만족): 빌드에 API 키가 있음([BuildConfig.MAPS_ENABLED]) · Google Play 서비스 있음 · 중국(CN)이 아님
 * (중국 본토에서는 Google 지도가 열리지 않는다) · 좌표 있음. 그중 하나라도 아니면 지도를 숨기고 기존 '구글 지도에서 열기' 링크만 둔다.
 * 인터넷이 없으면 지도 대신 안내 카드(+ 구글 지도 앱 링크 — 저장해 둔 지도는 앱에서 열린다).
 * 위치 권한·내 위치 표시는 쓰지 않는다. 지도 그림(타일)을 따로 저장하지 않는다.
 */
@Immutable
data class MapEnv(val keyPresent: Boolean, val online: Boolean, val playServices: Boolean)

enum class MapMode {
    /** 앱 안 지도 */
    InApp,

    /** 지도를 그릴 수 있는 곳이지만 지금 인터넷이 없음 → 안내 카드 */
    Offline,

    /** 키 없음·Play 서비스 없음·중국·좌표 없음 → 지도 칸을 그리지 않는다(가는 법 카드의 '구글 지도에서 열기'만) */
    Hidden,
}

/** 지도 위 표시 하나. [number]: 찜한 곳만 볼 때 찜 목록 순서(1부터), 아니면 null. [hue]: 지역 색(0~360) */
@Immutable
data class MapPin(
    val id: String,
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double,
    val number: Int? = null,
    val hue: Float = PIN_HUES.first(),
    val regionId: String = "",
)

/** 지역 색 범례 한 줄 */
@Immutable
data class MapLegend(val name: String, val hue: Float)

/** Google 지도 기본 표시 색(BitmapDescriptorFactory.HUE_*) 가운데 서로 잘 갈리는 것 — 지역 순서대로 돌려 쓴다 */
val PIN_HUES = listOf(210f, 30f, 120f, 270f, 330f, 180f, 0f, 60f)

object AttractionsMapPolicy {
    /** 중국 본토에서는 Google 지도가 열리지 않는다 — 지도 칸을 숨긴다(홍콩·마카오·대만은 각자 나라 코드) */
    fun countrySupported(country: String): Boolean = country != "CN"

    fun hasCoords(a: Attraction): Boolean {
        val lat = a.lat ?: return false
        val lng = a.lng ?: return false
        return lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)
    }

    fun mode(env: MapEnv, country: String, hasCoords: Boolean): MapMode = when {
        !env.keyPresent || !env.playServices || !countrySupported(country) || !hasCoords -> MapMode.Hidden
        !env.online -> MapMode.Offline
        else -> MapMode.InApp
    }

    /** 목록 '지도로 보기': 좌표 있는 곳이 하나라도 있어야 */
    fun listMode(env: MapEnv, country: String, places: List<Attraction>): MapMode = mode(env, country, places.any { hasCoords(it) })

    /**
     * 목록에 보이는 곳 → 표시. [savedOrder]가 있으면(찜한 곳만 보기) 이 나라 찜 키의 순서대로 번호를 단다 —
     * 번호는 찜 목록(저장소가 주는 순서)의 자리라서, 목록에서 빠진 찜(문 닫음 등)이 있으면 번호가 건너뛸 수 있다.
     * 지역 색은 [regions] 순서(지역 order)로 정한다.
     */
    fun pins(places: List<Attraction>, regions: List<Region>, regionName: (String) -> String, categoryName: (Attraction) -> String, savedOrder: List<String>? = null): List<MapPin> {
        val hueOf = regionHues(regions)
        val numberOf: Map<String, Int> = savedOrder.orEmpty().withIndex().associate { (i, key) -> key to i + 1 }
        return places.filter { hasCoords(it) }.map { a ->
            MapPin(
                id = a.id,
                title = a.title,
                subtitle = listOf(regionName(a.regionId), categoryName(a)).filter { it.isNotBlank() }.joinToString(" · "),
                lat = a.lat!!,
                lng = a.lng!!,
                number = if (savedOrder != null) numberOf[a.key] else null,
                hue = hueOf[a.regionId] ?: PIN_HUES.first(),
                regionId = a.regionId,
            )
        }.let { list -> if (savedOrder != null) list.sortedBy { it.number ?: Int.MAX_VALUE } else list }
    }

    /** 상세의 표시 하나 (좌표가 없으면 null) */
    fun detailPin(a: Attraction, regionName: String, categoryName: String): MapPin? {
        if (!hasCoords(a)) return null
        val subtitle = listOf(regionName, categoryName).filter { it.isNotBlank() }.joinToString(" · ")
        return MapPin(a.id, a.title, subtitle, a.lat!!, a.lng!!, regionId = a.regionId)
    }

    /** 표시가 있는 지역만, 지역 순서대로 */
    fun legend(pins: List<MapPin>, regions: List<Region>): List<MapLegend> {
        val used = pins.map { it.regionId }.toSet()
        val hueOf = regionHues(regions)
        return regions.sortedBy { it.order }.filter { it.id in used }.map { MapLegend(it.nameKo, hueOf.getValue(it.id)) }
    }

    private fun regionHues(regions: List<Region>): Map<String, Float> =
        regions.sortedBy { it.order }.withIndex().associate { (i, r) -> r.id to PIN_HUES[i % PIN_HUES.size] }

    /** 찜 키(저장소 순서) 가운데 이 나라 것만 — '찜한 곳만 보기' 번호의 기준 */
    fun savedOrder(savedKeys: Collection<String>, country: String): List<String> = savedKeys.filter { it.startsWith("$country/") }

    /** '구글 지도 앱에서 길찾기': 공식 Maps URLs(길찾기). 출발지는 넘기지 않는다(앱이 현재 위치를 쓰거나 묻는다) */
    fun directionsUrl(a: Attraction): String? {
        if (!countrySupported(a.country) || !hasCoords(a)) return null
        return "https://www.google.com/maps/dir/?api=1&destination=${a.lat},${a.lng}"
    }
}

/** 테스트·미리보기에서 지도 환경을 정한다(null = 실제 기기 환경) */
val LocalMapEnv = staticCompositionLocalOf<MapEnv?> { null }

/** 지금 지도 환경 — 키(빌드)·인터넷(실시간)·Play 서비스 */
@Composable
fun rememberMapEnv(): MapEnv = LocalMapEnv.current ?: deviceMapEnv()

@Composable
private fun deviceMapEnv(): MapEnv {
    val context = LocalContext.current
    val play = remember { playServicesAvailable(context) }
    val online by remember { context.applicationContext.onlineFlow() }.collectAsStateWithLifecycle(initialValue = true)
    return MapEnv(keyPresent = BuildConfig.MAPS_ENABLED, online = online, playServices = play)
}

private fun playServicesAvailable(context: Context): Boolean = BuildConfig.MAPS_ENABLED && runCatching {
    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}.getOrDefault(false)
