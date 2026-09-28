package com.readyport.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** 명도 대비 4.5:1 이상 (PRD 8.4 접근성). 실제로 글자-배경으로 쓰는 조합만 검사한다. */
class TokenContrastTest {

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    private val textPairs = mapOf(
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
    )

    @Test
    fun allTextPairsMeetAA() {
        val failures = textPairs.mapNotNull { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            if (ratio < 4.5) "$name = %.2f".format(ratio) else null
        }
        assertTrue("명도 대비 4.5 미만: $failures", failures.isEmpty())
    }
}
