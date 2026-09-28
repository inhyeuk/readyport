package com.readyport.ui.theme

import androidx.compose.ui.graphics.Color

// 디자인 토큰 (PRD 8.4). 값을 바꾸면 TokenContrastTest가 명도 대비 4.5:1을 다시 검사한다.
object Tokens {
    val Accent = Color(0xFF1F4FD1)
    val AccentSoft = Color(0xFFE8EEFC)
    val Ink = Color(0xFF16213A)
    val InkSecondary = Color(0xFF4A5468)
    val Ground = Color(0xFFF4F5F7)
    val Surface = Color(0xFFFFFFFF)
    val Line = Color(0xFFD9DDE4)
    val LineSoft = Color(0xFFE3E6EB)

    val CautionBg = Color(0xFFFFF4DC)
    val CautionText = Color(0xFF7A4100)
    val CautionBorder = Color(0xFFE6B566)

    val DangerBg = Color(0xFFFDECEA)
    val DangerText = Color(0xFFA12A22)

    val SuccessBg = Color(0xFFE3F4EC)
    val SuccessText = Color(0xFF0F6B45)

    // 도움 탭: 다른 탭과 구분되는 따뜻한 색 (PRD 4.1)
    val Help = Color(0xFFA8431A)

    // 오프라인 배너 등 남색 카드 (PRD 5.1, 5.9)
    val Navy = Color(0xFF0B1A4D)
}
