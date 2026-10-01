package com.readyport.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Payments
import com.readyport.ui.TestPacks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** 타일 값 만들기 (DESIGN_SPEC 4.6): 팩 문장에서 짧은 값만 꺼내고, 쉼표에서는 자르지 않는다 */
class FactValuesTest {

    private fun pack(code: String) = runBlocking { TestPacks.repo.pack(code)!!.value }

    @Test
    fun shortValueOnEveryBundledFee() {
        assertEquals("무료", shortValue(pack("TH").forms.single().feeKo))
        assertEquals("무료", shortValue(pack("JP").forms.single().feeKo))
        assertEquals("무료", shortValue(pack("SG").forms.single().feeKo))
        val id = pack("ID")
        assertEquals("IDR 500,000", shortValue(id.requirements.mapNotNull { it.apply }.single().feeKo))
        assertEquals("무료", shortValue(id.forms.single().feeKo))
        // 첫 문장이 14자를 넘으면 타일 대신 글 행
        assertNull(shortValue(pack("MY").forms.single().feeKo))
    }

    @Test
    fun shortValueRules() {
        assertEquals("짧은 값", shortValue("  짧은 값  "))
        assertEquals("IDR 500,000", shortValue("IDR 500,000 · 카드(Mastercard·Visa·JCB)로 결제, 환불 안 됨"))
        // 쉼표에서는 절대 자르지 않는다
        assertNull(shortValue("IDR 500,000, 카드로만 결제하고 환불은 안 돼요"))
        assertNull(shortValue("이 문장은 끊을 곳이 없어서 아주 길게 이어집니다"))
        assertNull(shortValue(""))
    }

    @Test
    fun feeIconOnlyForExactlyFree() {
        assertSame(Icons.Outlined.MoneyOff, feeIcon("무료"))
        assertSame(Icons.Outlined.Payments, feeIcon("IDR 500,000"))
        assertSame(Icons.Outlined.Payments, feeIcon("무료. 돈을 받는 사이트는 가짜"))
        assertSame(Icons.Outlined.Payments, feeIcon("유료"))
    }
}
