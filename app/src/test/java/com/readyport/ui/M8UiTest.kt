package com.readyport.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.pack.EssentialLink
import com.readyport.pack.EssentialRule
import com.readyport.pack.Names
import com.readyport.pack.OfficialLink
import com.readyport.pack.PowerInfo
import com.readyport.pack.ShoppingItem
import com.readyport.prep.CartKey
import com.readyport.prep.Essentials
import com.readyport.ui.pack.ShoppingContent
import com.readyport.ui.pack.ShoppingUi
import com.readyport.ui.prep.EssentialRow
import com.readyport.ui.prep.EssentialsContent
import com.readyport.ui.prep.EssentialsUi
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** M8: 꼭 챙길 물건(제휴 고지·표시 규칙), 쇼핑 리스트(반입 태그) */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class M8UiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int, vararg a: Any) = context.getString(id, *a)

    private val kr = PowerInfo("둥근 구멍 2개", null, "220 V", "60 Hz", "src", "2026-09-29")
    private val jp = PowerInfo("설명", null, "100 V", "50/60 Hz", "src", "2026-09-29")
    private val th = PowerInfo("설명", true, "220 V", "50 Hz", "src", "2026-09-29")
    private val sg = PowerInfo("설명", false, "230 V", "50 Hz", "src", "2026-09-29")

    private val rules = listOf(
        EssentialRule("passport", "여권", "이유", "always"),
        EssentialRule("adapter", "변환 어댑터", "이유", "plug_differs", link = EssentialLink("affiliate", "https://example.com/a", "사러 가기", "partner")),
        EssentialRule("voltage", "전압 확인", "이유", "voltage_differs"),
        EssentialRule("insurance", "여행자 보험", "이유", "always", link = EssentialLink("official_info", "https://example.com/b", "공식 비교 사이트 열기")),
        EssentialRule("powerbank", "보조배터리", "이유", "always", ruleBadge = "carry_on_only"),
    )

    // ---------------- 순수 로직 ----------------

    @Test fun adapterUnlessKoreanPlugConfirmedToFit() {
        assertEquals(true, Essentials.plugDiffers(sg))
        // 확인되지 않은 나라는 안전하게 어댑터 권장
        assertEquals(true, Essentials.plugDiffers(jp))
        assertEquals(false, Essentials.plugDiffers(th))
        assertNull(Essentials.plugDiffers(null))
    }

    @Test fun voltageCompareByNumber() {
        assertEquals(true, Essentials.voltageDiffers(kr, jp))
        // 220 ↔ 230은 같은 계열
        assertEquals(false, Essentials.voltageDiffers(kr, sg))
        assertEquals(false, Essentials.voltageDiffers(kr, kr.copy(voltage = "220V")))
    }

    @Test fun selectKeepsPackOrder() {
        // 추천 순서는 팩에 적힌 순서 그대로 — 제휴 링크가 있어도 앞으로 가지 않는다 (PRD 11.2)
        assertEquals(listOf("passport", "adapter", "voltage", "insurance", "powerbank"), Essentials.select(rules, kr, jp).map { it.id })
        assertEquals(listOf("passport", "insurance", "powerbank"), Essentials.select(rules, kr, th).map { it.id })
        assertEquals(listOf("passport", "adapter", "insurance", "powerbank"), Essentials.select(rules, kr, sg).map { it.id })
        // 여행지를 모르면 조건 항목은 뺀다
        assertEquals(listOf("passport", "insurance", "powerbank"), Essentials.select(rules, kr, null).map { it.id })
    }

    @Test fun cartKeyRoundTrip() {
        val k = CartKey.of("TH", "dried-mango")
        assertEquals("TH", CartKey.country(k))
        assertEquals("dried-mango", CartKey.item(k))
    }

    // ---------------- 꼭 챙길 물건 화면 ----------------

    private fun essentials(have: Set<String> = emptySet()) {
        val ui = EssentialsUi("일본", 4, 11, Essentials.select(rules, kr, jp).map { EssentialRow(it, it.id in have, null) })
        rule.setContent { ReadyPortTheme { EssentialsContent(ui, { _, _ -> }, {}) } }
    }

    @Test fun disclosureIsAtTopAboveItems() {
        essentials()
        val disclosure = rule.onNodeWithText(s(R.string.essentials_disclosure)).assertIsDisplayed().getBoundsInRoot()
        val firstItem = rule.onNodeWithText("여권").getBoundsInRoot()
        assertTrue(disclosure.top < firstItem.top)
        rule.onNodeWithText(s(R.string.essentials_for_trip, "일본", 4, 11)).assertIsDisplayed()
    }

    @Test fun affiliateLabelOnlyOnAffiliateLinks() {
        essentials()
        // 제휴 링크 1개(어댑터)에만 '제휴' 라벨. 보험(official_info)에는 없다
        rule.onAllNodesWithText(s(R.string.essentials_affiliate_label)).assertCountEquals(1)
        rule.onNodeWithText("공식 비교 사이트 열기").assertExists()
    }

    @Test fun progressAndBadge() {
        essentials(have = setOf("passport", "adapter"))
        rule.onNodeWithText(s(R.string.essentials_progress, 5, 2)).assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.essentials_badge_carry_on)))
        rule.onNodeWithText(s(R.string.essentials_badge_carry_on)).assertIsDisplayed()
    }

    // ---------------- 쇼핑 리스트 ----------------

    private fun item(id: String, cat: String, status: String) = ShoppingItem(
        id, cat, Names("이름-$id", "Name-$id", "โลคอล-$id"), whyKo = "이유", importStatus = status,
        source = "tat", importSource = "customs", lastVerified = "2026-09-29",
    )

    @Test fun shoppingTagsCartAndShowStaff() {
        var toggled: Pair<String, Boolean>? = null
        val ui = ShoppingUi(
            country = "TH", countryKo = "태국",
            items = listOf(item("a", "food", "allowed"), item("b", "food", "prohibited"), item("c", "souvenir", "caution")),
            cart = setOf(CartKey.of("TH", "a")),
            returnLinks = listOf(OfficialLink("customs", "관세청 여행자 휴대품 안내", "https://example.com")),
        )
        rule.setContent { ReadyPortTheme { ShoppingContent(ui, { id, v -> toggled = id to v }, {}) } }
        rule.onNodeWithText(s(R.string.import_allowed)).assertExists()
        rule.onNodeWithText(s(R.string.import_prohibited)).assertExists()
        rule.onNodeWithText(s(R.string.import_caution)).assertExists()
        rule.onNodeWithText(s(R.string.shopping_in_cart)).assertExists()

        // 분류 칩: 기념품만
        rule.onNodeWithText(s(R.string.shopping_cat_souvenir)).performClick()
        rule.onNodeWithText("이름-a").assertDoesNotExist()
        rule.onNodeWithText(s(R.string.shopping_add)).performClick()
        assertEquals("c" to true, toggled)

        // 직원에게 보여주기: 현지어 크게
        rule.onNodeWithText(s(R.string.shopping_show_staff)).performClick()
        // 카드의 현지어 이름 + 전체 화면 (2개)
        rule.onAllNodesWithText("โลคอล-c").assertCountEquals(2)
        rule.onNodeWithText("Name-c").assertIsDisplayed()
    }

    @Test fun noDailyChipWhenNoDailyItems() {
        val ui = ShoppingUi(country = "TH", countryKo = "태국", items = listOf(item("a", "food", "allowed")))
        rule.setContent { ReadyPortTheme { ShoppingContent(ui, { _, _ -> }, {}) } }
        rule.onNodeWithText(s(R.string.shopping_cat_daily)).assertDoesNotExist()
        assertFalse(ui.items.isEmpty())
    }
}
