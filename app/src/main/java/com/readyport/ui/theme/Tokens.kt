package com.readyport.ui.theme

import androidx.compose.ui.graphics.Color

// 디자인 토큰 (PRD 8.4). 값을 바꾸면 TokenContrastTest가 명도 대비 4.5:1을 다시 검사한다.
// 2026-10 개편(DESIGN_SPEC_2026-10-01 3.1): 기존 값은 그대로 두고 아래에 추가만 한다.
object Tokens {
    val Accent = Color(0xFF1F4FD1)
    val AccentSoft = Color(0xFFE8EEFC)
    val Ink = Color(0xFF16213A)
    val InkSecondary = Color(0xFF4A5468)
    val Ground = Color(0xFFF4F5F7)
    val Surface = Color(0xFFFFFFFF)
    val Line = Color(0xFFD9DDE4)
    val LineSoft = Color(0xFFE3E6EB)
    // 항목 테두리(2026-10-09 사장님 요청 — 여러 항목이 섞여 보이지 않게). LineSoft 는 바탕(Ground)과 너무 비슷해 안 보였다.
    // 옅은 회청색: 흰 카드·연한 채움 위에서 테두리가 보이되 글자보다 훨씬 약하다(장식 — 대비 기준 대상 아님)
    val CardEdge = Color(0xFFCDD4DE)

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

    // ---------------- 추가 (DESIGN_SPEC 3.1) ----------------

    /** 히어로 그라데이션 끝, 여권 카드 그라데이션, AccentSoft 위 강조 글자 (흰 글자 10.36) */
    val AccentDeep = Color(0xFF1E3A8A)

    /** 아이콘 히어로 그라데이션 시작 — 런처 아이콘과 같은 색 (흰 글자 5.36) */
    val BrandBlue = Color(0xFF2B5CF2)

    /** 출처·메타 캡션 전용 (Surface 5.88, Ground 5.39) */
    val InkTertiary = Color(0xFF5B6577)

    /** 세그먼트 트랙, 칩 바탕, 설정 그룹 사이 */
    val SurfaceSunken = Color(0xFFEEF1F6)

    /** 꺼진 스위치 트랙, 비활성 버튼 바탕 */
    val SurfaceHighest = Color(0xFFE6EAF0)

    /** ColorScheme.surfaceContainerLow */
    val SurfaceLow = Color(0xFFFAFBFC)

    /** 조작 요소 경계(입력칸, 꺼진 스위치, 선택 안 된 칩) — 비텍스트 Surface 4.63 / Ground 4.25 */
    val LineStrong = Color(0xFF6B7589)

    /** 긴급 타일·도움 배지 바탕 */
    val HelpSoft = Color(0xFFFBEDE6)

    /** 카테고리: 이동 */
    val VioletSoft = Color(0xFFEFEAFE)
    val VioletText = Color(0xFF5B3CC4)

    /** 카테고리: 지도·오프라인 */
    val TealSoft = Color(0xFFE0F4F2)
    val TealText = Color(0xFF0B6B66)

    /** Navy·AccentDeep 위 여권 eyebrow 전용(런처 아이콘 금색). BrandBlue 위에는 쓰지 않는다 */
    val Gold = Color(0xFFFFDC8F)

    /** Accent·Navy·AccentDeep 위 출처·eyebrow 글자 (합성 대비: Accent 위 5.37) */
    val White85 = Color.White.copy(alpha = 0.85f)

    /** Navy·AccentDeep 위 보조 글자, 어두운 타일의 셰브론 (합성 대비: Navy 위 10.85) */
    val White80 = Color.White.copy(alpha = 0.80f)

    /** BadgeTone.OnDark 바탕 — 어두운 채움 위의 장식 배지 */
    val White12 = Color.White.copy(alpha = 0.12f)

    /** 사진 위 칩 바탕 (검정 위 합성 최악에도 Ink 13.4) */
    val PhotoChipBg = Color.White.copy(alpha = 0.92f)

    /** 사진 위 원형 버튼(뒤로·찜) 바탕 */
    val PhotoButtonBg = Color.Black.copy(alpha = 0.35f)

    /** 사진 전체에 까는 옅은 틴트 (DESIGN_SPEC 3.7 ①) */
    val PhotoTint = Color.Black.copy(alpha = 0.18f)

    // ---------------- 그림 메뉴 일러스트 (DESIGN_SPEC 부록 E.6 — 길잡이 v4) ----------------
    // 일러스트 전용 주색·중간색. 글자색으로 쓰지 않는다(그림 안 채움·선과 패널 그라데이션 끝에만).
    // 진한 외곽선은 기존 토큰(AccentDeep·TealText·Help·VioletText), 옅은 바탕은 기존 *Soft 토큰을 그대로 쓴다.

    /** 파랑 계열 중간색 (사증 면 글줄·패널 그라데이션 끝) */
    val IllusBlueMid = Color(0xFFB4C7FA)

    /** 청록 계열 주색 (지도 땅·물) */
    val IllusTeal = Color(0xFF2FA79B)

    /** 청록 계열 중간색 (지도 면) */
    val IllusTealMid = Color(0xFFA8E2DA)

    /** 따뜻한 계열 주색 (쇼핑백·핀·말풍선) */
    val IllusWarm = Color(0xFFF2804F)

    /** 따뜻한 계열 중간색 */
    val IllusWarmMid = Color(0xFFF9C7AE)

    /** 보라 계열 주색 (택시) */
    val IllusViolet = Color(0xFF8B6DF3)

    /** 보라 계열 중간색 */
    val IllusVioletMid = Color(0xFFCFC3FD)

    // ---------------- 흰 정보 카드 그림자 (DESIGN_SPEC D2·3.5) ----------------
    // 스펙의 'Ink 8%/12%'는 화면에 보이는 진하기다. Android는 그림자 색 알파에 테마의 ambientShadowAlpha(0.039)·
    // spotShadowAlpha(0.19)를 한 번 더 곱하므로, 8%/12%를 그대로 넣으면 실제로는 0.3%/2.3%라 Ground 위에서 거의 안 보였다
    // (build/gallery/shadow_check.txt: 1단계 차이). 플랫폼 알파로 나눠 보정한다(앰비언트는 1을 넘지 못해 Ink 100%).
    // 운영자 확인 항목(3단계): 이 보정 그대로 / 더 옅게 / LineSoft 1dp 병행 — STAGE0_REPORT 검증 반영 참고.

    /** 스펙이 정한 화면상 그림자 진하기 */
    const val SHADOW_AMBIENT_TARGET = 0.08f
    const val SHADOW_SPOT_TARGET = 0.12f

    /** Android 기본 테마(Material·DeviceDefault)의 그림자 광원 알파 */
    const val PLATFORM_AMBIENT_SHADOW_ALPHA = 0.039f
    const val PLATFORM_SPOT_SHADOW_ALPHA = 0.19f

    val ShadowAmbient = Ink.copy(alpha = (SHADOW_AMBIENT_TARGET / PLATFORM_AMBIENT_SHADOW_ALPHA).coerceAtMost(1f))
    val ShadowSpot = Ink.copy(alpha = (SHADOW_SPOT_TARGET / PLATFORM_SPOT_SHADOW_ALPHA).coerceAtMost(1f))
}
