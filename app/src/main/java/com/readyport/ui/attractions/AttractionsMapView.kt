package com.readyport.ui.attractions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.readyport.R
import com.readyport.ui.theme.Tokens

/** 지도 한 장에 그릴 것. [lite]: 상세 카드의 작은 정지 지도(라이트 모드, 손으로 움직이지 않음) */
@Immutable
data class MapSpec(
    val pins: List<MapPin>,
    val lite: Boolean,
    val contentDescription: String,
    val selectedId: String? = null,
)

/**
 * 지도 그리기 — 실제 앱은 [GooglePlaceMapRenderer], 테스트는 네트워크·Play 서비스 없이 그리는 가짜로 바꿔 끼운다
 * ([com.readyport.ui.video.LocalThumbnailLoader]와 같은 방식).
 */
interface PlaceMapRenderer {
    @Composable
    fun Render(spec: MapSpec, onPinClick: (String) -> Unit, onMapClick: () -> Unit, modifier: Modifier)
}

val LocalPlaceMapRenderer = staticCompositionLocalOf<PlaceMapRenderer> { GooglePlaceMapRenderer }

/** Google 지도 SDK(maps-compose). 지도 그림은 SDK가 받아 그리고, 앱은 따로 저장하지 않는다 */
object GooglePlaceMapRenderer : PlaceMapRenderer {
    private const val SINGLE_ZOOM = 15f
    private const val START_ZOOM = 9f

    @Composable
    override fun Render(spec: MapSpec, onPinClick: (String) -> Unit, onMapClick: () -> Unit, modifier: Modifier) {
        val pins = spec.pins
        if (pins.isEmpty()) return
        val center = remember(pins) { LatLng(pins.map { it.lat }.average(), pins.map { it.lng }.average()) }
        val camera = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(center, if (pins.size == 1) SINGLE_ZOOM else START_ZOOM)
        }
        val paddingPx = with(LocalDensity.current) { 56.dp.roundToPx() }
        GoogleMap(
            modifier = modifier,
            cameraPositionState = camera,
            contentDescription = spec.contentDescription,
            googleMapOptionsFactory = { GoogleMapOptions().liteMode(spec.lite).mapToolbarEnabled(false) },
            // 위치 권한을 쓰지 않는다 — 내 위치 표시 없음
            properties = MapProperties(isMyLocationEnabled = false, isBuildingEnabled = false, isIndoorEnabled = false),
            uiSettings = if (spec.lite) {
                MapUiSettings(
                    compassEnabled = false, indoorLevelPickerEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false,
                    rotationGesturesEnabled = false, scrollGesturesEnabled = false, tiltGesturesEnabled = false,
                    zoomControlsEnabled = false, zoomGesturesEnabled = false,
                )
            } else {
                MapUiSettings(
                    compassEnabled = true, indoorLevelPickerEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false,
                    rotationGesturesEnabled = false, tiltGesturesEnabled = false, zoomControlsEnabled = true,
                )
            },
            // 라이트 모드 지도를 누르면 SDK가 Google 지도 앱을 바로 연다 — 대신 앱의 큰 지도로 간다
            onMapClick = { onMapClick() },
            onMapLoaded = {
                if (pins.size > 1) {
                    val bounds = LatLngBounds.builder().apply { pins.forEach { include(LatLng(it.lat, it.lng)) } }.build()
                    runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)) }
                }
            },
            mapColorScheme = ComposeMapColorScheme.LIGHT,
        ) {
            pins.forEach { pin ->
                val position = LatLng(pin.lat, pin.lng)
                val selected = pin.id == spec.selectedId
                val number = pin.number
                if (number != null) {
                    MarkerComposable(
                        number, selected,
                        state = rememberUpdatedMarkerState(position),
                        contentDescription = stringResource(R.string.attractions_map_pin_numbered_cd, number, pin.title),
                        title = pin.title,
                        zIndex = if (selected) 2f else 1f,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        onClick = { onPinClick(pin.id); true },
                    ) { NumberPin(number, selected) }
                } else {
                    Marker(
                        state = rememberUpdatedMarkerState(position),
                        contentDescription = pin.title,
                        title = pin.title,
                        icon = BitmapDescriptorFactory.defaultMarker(pin.hue),
                        alpha = if (spec.selectedId == null || selected) 1f else 0.75f,
                        zIndex = if (selected) 2f else 1f,
                        onClick = { onPinClick(pin.id); true },
                    )
                }
            }
        }
    }
}

/** 찜 순서 번호 표시 — 파란 원 + 흰 숫자(선택되면 크게) */
@Composable
internal fun NumberPin(number: Int, selected: Boolean) {
    val size = if (selected) 40.dp else 32.dp
    Box(
        Modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .background(if (selected) Tokens.AccentDeep else Tokens.Accent, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 지도 위 그림(비트맵). sp라 글자 크기 설정을 따라 커진다. TalkBack은 표시 이름(contentDescription)을 읽는다
        Text(
            number.toString(),
            color = Color.White,
            style = TextStyle(fontSize = if (selected) 18.sp else 15.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** 지역 색(hue) → 화면 색 (범례 점). Google 기본 표시와 비슷한 채도 */
fun hueColor(hue: Float): Color = Color.hsv(hue % 360f, 0.85f, 0.85f)
