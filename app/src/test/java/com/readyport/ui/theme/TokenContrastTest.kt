package com.readyport.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.readyport.ui.components.BadgeTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 명도 대비 (PRD 8.4 접근성, DESIGN_SPEC 3.8). 실제로 쓰는 글자-바탕·조작 요소-바탕 조합만 검사한다.
 * 글자 4.5:1, 비텍스트(테두리·아이콘·선택 채움) 3:1.
 *
 * Color.luminance()는 알파를 무시하므로(White85를 불투명 흰색으로 계산) 반투명 색은 **먼저 바탕 위에 합성한 뒤** 계산한다.
 */
class TokenContrastTest {

    private val black = Color.Black
    private val white = Color.White

    /** [fg]를 [bg] 위에 합성한 뒤 대비. bg가 반투명이면 호출하는 쪽이 최악 바탕 위에 먼저 합성해 넘긴다 */
    private fun contrast(fg: Color, bg: Color): Float {
        val opaqueBg = if (bg.alpha < 1f) bg.compositeOver(white) else bg
        val a = fg.compositeOver(opaqueBg).luminance() + 0.05f
        val b = opaqueBg.luminance() + 0.05f
        return maxOf(a, b) / minOf(a, b)
    }

    private val textPairs = mapOf(
        // 기존
        "ink on ground" to (Tokens.Ink to Tokens.Ground),
        "ink on surface" to (Tokens.Ink to Tokens.Surface),
        "ink-secondary on surface" to (Tokens.InkSecondary to Tokens.Surface),
        "ink-secondary on ground" to (Tokens.InkSecondary to Tokens.Ground),
        "ink on accent-soft" to (Tokens.Ink to Tokens.AccentSoft),
        "surface on accent (주 버튼·파란 카드)" to (Tokens.Surface to Tokens.Accent),
        "accent on surface (선택된 탭·흰 버튼)" to (Tokens.Accent to Tokens.Surface),
        "accent on ground" to (Tokens.Accent to Tokens.Ground),
        "caution" to (Tokens.CautionText to Tokens.CautionBg),
        "danger" to (Tokens.DangerText to Tokens.DangerBg),
        "success" to (Tokens.SuccessText to Tokens.SuccessBg),
        "help on surface (도움 탭)" to (Tokens.Help to Tokens.Surface),
        "help on ground" to (Tokens.Help to Tokens.Ground),
        "surface on navy" to (Tokens.Surface to Tokens.Navy),
        // DESIGN_SPEC 3.8 추가
        "ink-tertiary on surface (출처)" to (Tokens.InkTertiary to Tokens.Surface),
        "ink-tertiary on ground" to (Tokens.InkTertiary to Tokens.Ground),
        "ink-tertiary on accent-soft" to (Tokens.InkTertiary to Tokens.AccentSoft),
        "ink-tertiary on caution-bg" to (Tokens.InkTertiary to Tokens.CautionBg),
        "ink-secondary on surface-sunken" to (Tokens.InkSecondary to Tokens.SurfaceSunken),
        "ink-secondary on surface-highest" to (Tokens.InkSecondary to Tokens.SurfaceHighest),
        "ink-secondary on help-soft" to (Tokens.InkSecondary to Tokens.HelpSoft),
        "ink-secondary on success-bg" to (Tokens.InkSecondary to Tokens.SuccessBg),
        "ink-secondary on danger-bg" to (Tokens.InkSecondary to Tokens.DangerBg),
        "accent on accent-soft (tonal 버튼)" to (Tokens.Accent to Tokens.AccentSoft),
        "accent on surface-sunken" to (Tokens.Accent to Tokens.SurfaceSunken),
        "accent-deep on accent-soft" to (Tokens.AccentDeep to Tokens.AccentSoft),
        "help on help-soft (긴급 타일)" to (Tokens.Help to Tokens.HelpSoft),
        "violet" to (Tokens.VioletText to Tokens.VioletSoft),
        "teal" to (Tokens.TealText to Tokens.TealSoft),
        "gold on navy (여권 eyebrow)" to (Tokens.Gold to Tokens.Navy),
        "gold on accent-deep" to (Tokens.Gold to Tokens.AccentDeep),
        "surface on accent-deep" to (Tokens.Surface to Tokens.AccentDeep),
        "surface on brand-blue" to (Tokens.Surface to Tokens.BrandBlue),
        "surface on help" to (Tokens.Surface to Tokens.Help),
        "surface on success-text" to (Tokens.Surface to Tokens.SuccessText),
        "surface on danger-text" to (Tokens.Surface to Tokens.DangerText),
        "비활성 버튼 (ink-tertiary on surface-highest)" to (Tokens.InkTertiary to Tokens.SurfaceHighest),
        "ink on surface-highest" to (Tokens.Ink to Tokens.SurfaceHighest),
        "ink on help-soft" to (Tokens.Ink to Tokens.HelpSoft),
    )

    @Test
    fun allTextPairsMeetAA() {
        val failures = textPairs.mapNotNull { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            if (ratio < 4.5) "%s = %.2f".format(name, ratio) else null
        }
        assertTrue("명도 대비 4.5 미만: $failures", failures.isEmpty())
    }

    /** 반투명 글자·바탕: 합성한 값으로 계산한다. 기대값은 DESIGN_SPEC 3.1·3.8에 적은 직접 계산값(±0.1) */
    private val compositePairs = listOf(
        Triple("White85 on Accent", Tokens.White85 to Tokens.Accent, 5.37f),
        Triple("White85 on AccentDeep", Tokens.White85 to Tokens.AccentDeep, 7.93f),
        Triple("White85 on Navy", Tokens.White85 to Tokens.Navy, 12.17f),
        Triple("White80 on Navy", Tokens.White80 to Tokens.Navy, 10.85f),
        Triple("White80 on AccentDeep", Tokens.White80 to Tokens.AccentDeep, 7.26f),
        // PhotoChip 최악: 흰 92% 칩이 검정 사진 위
        Triple("Ink on PhotoChip(흰 92% over 검정)", Tokens.Ink to Tokens.PhotoChipBg.compositeOver(black), 13.42f),
        // PhotoTextArea 스크림 최악: 흰 사진 위 검정 0.60
        Triple("흰 글자 on 스크림(검정 0.60 over 흰색)", white to black.copy(alpha = 0.60f).compositeOver(white), 5.74f),
    )

    @Test
    fun compositedPairsAreComputedWithAlpha() {
        val failures = compositePairs.mapNotNull { (name, pair, expected) ->
            val ratio = contrast(pair.first, pair.second)
            println("CONTRAST %s = %.2f (기대 %.2f)".format(name, ratio, expected))
            when {
                ratio < 4.5f -> "%s = %.2f < 4.5".format(name, ratio)
                kotlin.math.abs(ratio - expected) > 0.1f -> "%s = %.2f (기대 %.2f)".format(name, ratio, expected)
                else -> null
            }
        }
        assertTrue("합성 대비 실패: $failures", failures.isEmpty())
        // 알파를 무시하던 옛 계산이면 White85 on Accent가 6.78로 나온다 — 합성 계산인지 확인
        assertTrue(contrast(Tokens.White85, Tokens.Accent) < contrast(Tokens.Surface, Tokens.Accent) - 1f)
    }

    /** 비텍스트 3:1 — 조작 요소 경계, 선택 채움, 아이콘 */
    private val nonTextPairs = mapOf(
        "LineStrong on Surface (입력칸·꺼진 스위치)" to (Tokens.LineStrong to Tokens.Surface),
        "LineStrong on Ground (세그먼트 트랙 테두리 4.25)" to (Tokens.LineStrong to Tokens.Ground),
        "Accent 선택 채움 vs SurfaceSunken" to (Tokens.Accent to Tokens.SurfaceSunken),
        "Accent 선택 채움 vs Ground" to (Tokens.Accent to Tokens.Ground),
        "tonal 버튼 테두리 Accent vs Ground (6.22)" to (Tokens.Accent to Tokens.Ground),
        "onDark 보조 버튼 테두리 Surface vs Accent (6.78)" to (Tokens.Surface to Tokens.Accent),
        "onDark 보조 버튼 테두리 Surface vs Navy (16.6)" to (Tokens.Surface to Tokens.Navy),
        // 사진 위 원형 버튼(뒤로·찜): 흰 아이콘 / 검정 0.35 over (검정 0.18 틴트 over 흰 사진)
        "사진 위 버튼 흰 아이콘 (3.54)" to (white to Tokens.PhotoButtonBg.compositeOver(Tokens.PhotoTint.compositeOver(white))),
    )

    @Test
    fun nonTextPairsMeet3to1() {
        val failures = nonTextPairs.mapNotNull { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            if (ratio < 3f) "%s = %.2f".format(name, ratio) else null
        }
        assertTrue("비텍스트 대비 3 미만: $failures", failures.isEmpty())
        assertEquals(4.25f, contrast(Tokens.LineStrong, Tokens.Ground), 0.05f)
        assertEquals(3.54f, contrast(white, Tokens.PhotoButtonBg.compositeOver(Tokens.PhotoTint.compositeOver(white))), 0.05f)
    }

    /** 배지·태그의 content vs container. OnDark(흰 12%)는 가장 밝은 어두운 채움(Accent) 위에 합성해 검사 */
    @Test
    fun everyBadgeToneIsReadable() {
        val failures = BadgeTone.entries.mapNotNull { tone ->
            val container = if (tone.container.alpha < 1f) tone.container.compositeOver(Tokens.Accent) else tone.container
            val ratio = contrast(tone.content, container)
            // 아이콘(비텍스트) 3:1 이상. 태그 글자로도 쓰는 톤은 4.5 이상이어야 한다
            val min = if (tone == BadgeTone.OnDark) 3f else 4.5f
            if (ratio < min) "${tone.name} = %.2f".format(ratio) else null
        }
        assertTrue("배지 톤 대비 부족: $failures", failures.isEmpty())
    }
}
