package com.readyport.ui.attractions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 테스트용 지도: 네트워크·Play 서비스 없이 표시 위치만 흉내 낸다(실제 Google 지도 그림이 아니다 — 모서리에 '테스트 지도' 글자).
 * 좌표를 상자 안 비율로 놓아 표시 사이의 위치 관계만 대략 맞는다.
 */
object FakeMapRenderer : PlaceMapRenderer {
    @Composable
    override fun Render(spec: MapSpec, onPinClick: (String) -> Unit, onMapClick: () -> Unit, modifier: Modifier) {
        BoxWithConstraints(modifier.background(Color(0xFFE9EFE6)).clickable { onMapClick() }.semantics { contentDescription = spec.contentDescription }) {
            Canvas(Modifier.fillMaxSize()) {
                val road = Color(0xFFFFFFFF)
                for (i in 1..5) {
                    drawLine(road, Offset(0f, size.height * i / 6f), Offset(size.width, size.height * (i + 0.6f) / 6f), strokeWidth = 6f)
                    drawLine(road, Offset(size.width * i / 6f, 0f), Offset(size.width * (i - 0.4f) / 6f, size.height), strokeWidth = 4f)
                }
                drawCircle(Color(0xFFBFD9F2), radius = size.minDimension / 5f, center = Offset(size.width * 0.82f, size.height * 0.25f))
            }
            val pins = spec.pins
            val minLat = pins.minOf { it.lat }
            val maxLat = pins.maxOf { it.lat }
            val minLng = pins.minOf { it.lng }
            val maxLng = pins.maxOf { it.lng }
            val w = maxWidth - 64.dp
            val h = maxHeight - 72.dp
            pins.forEach { p ->
                val fx = if (maxLng > minLng) ((p.lng - minLng) / (maxLng - minLng)).toFloat() else 0.5f
                val fy = if (maxLat > minLat) (1 - (p.lat - minLat) / (maxLat - minLat)).toFloat() else 0.5f
                Box(
                    Modifier
                        .offset(x = 32.dp + w * fx - 20.dp, y = 40.dp + h * fy - 20.dp)
                        .size(40.dp)
                        .clickable { onPinClick(p.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    val number = p.number
                    if (number != null) {
                        NumberPin(number, selected = p.id == spec.selectedId)
                    } else {
                        Icon(Icons.Filled.Place, contentDescription = p.title, tint = hueColor(p.hue), modifier = Modifier.size(if (p.id == spec.selectedId) 40.dp else 32.dp))
                    }
                }
            }
            Text("테스트 지도", fontSize = 10.sp, color = Color(0xFF5B6577), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
    }
}
