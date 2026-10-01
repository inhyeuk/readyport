package com.readyport.ui.pack

import com.readyport.R
import com.readyport.pack.EmergencyContact
import com.readyport.pack.Names
import com.readyport.pack.ShoppingItem
import com.readyport.prep.ImportStatus
import com.readyport.prep.import
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.TestPacks
import com.readyport.ui.components.Step
import com.readyport.ui.components.phoneGroups
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 도움·쇼핑 화면의 순수 로직 (DESIGN_SPEC 4.5 출처 이름, 6-18 긴 글, 6-20 번호 길이 규칙) */
class HelpShoppingLogicTest {

    private fun contact(id: String, number: String, source: String = "mofa") =
        EmergencyContact(id, "라벨-$id", number, source = source, lastVerified = "2026-09-28")

    // ---------------- 어느 나라에서나: 출처 이름 (commonSourceName 버그 수정) ----------------

    @Test
    fun commonSourceNameResolvesTheItemsOwnSourceId() {
        val index = TestPacks.index.value
        val names = index.sources.associate { it.id to it.name }
        val first = index.commonEmergency.first()
        // index 출처 목록의 '첫 이름'이 아니라 첫 공통 항목의 출처 ID를 푼 이름
        assertEquals(names.getValue(first.source), commonSourceName(index.commonEmergency, names))
    }

    @Test
    fun commonSourceNameIsNotTheFirstListedSourceWhenIdsDiffer() {
        val names = linkedMapOf("visitkorea_power" to "한국관광공사 (전기)", "mofa_0404" to "외교부 해외안전여행")
        val common = listOf(contact("consular_call_center", "+82-2-3210-0404", source = "mofa_0404"))
        assertEquals("외교부 해외안전여행", commonSourceName(common, names))
    }

    @Test
    fun commonSourceNameNeverReturnsAnId() {
        val common = listOf(contact("consular_call_center", "+82-2-3210-0404", source = "unknown_src"))
        assertNull(commonSourceName(common, mapOf("mofa_0404" to "외교부 해외안전여행")))
        assertNull(commonSourceName(emptyList(), mapOf("mofa_0404" to "외교부 해외안전여행")))
    }

    // ---------------- 긴급 번호 줄 나누기 ----------------

    @Test
    fun shortNumbersPairUpAndLongNumbersTakeTheFullWidth() {
        val jp = runBlocking { TestPacks.repo.pack("JP")!!.value }.emergency.drop(1)
        // 119, 118 (짧음) → 한 줄 2칸 / 050-3816-2787 (긴 번호) → 혼자 한 줄
        val rows = emergencyRows(jp, columns = 2)
        assertEquals(listOf(2, 1), rows.map { it.size })
        assertTrue(rows.last().single().number.length > 6)
    }

    @Test
    fun thailandRestFillsTwoByTwo() {
        val th = TestPacks.thailand.value.emergency.drop(1)
        assertEquals(listOf(listOf("191", "1669"), listOf("1691", "199")), emergencyRows(th, 2).map { r -> r.map { it.number } })
    }

    @Test
    fun leftoverShortNumberIsNotLeftHalfEmptyAndOneColumnStacks() {
        val list = listOf(contact("a", "113"), contact("b", "118"), contact("c", "119"))
        assertEquals(listOf(2, 1), emergencyRows(list, 2).map { it.size })
        assertEquals(listOf(1, 1, 1), emergencyRows(list, 1).map { it.size })
        // 순서는 팩 순서 그대로
        assertEquals(list, emergencyRows(list, 2).flatten())
    }

    // ---------------- 단계 문장 / 긴 글 ----------------

    @Test
    fun stepSplitsFirstSentenceWithoutChangingText() {
        assertEquals(
            Step("대사관 영사과에서 긴급여권을 신청해요.", detail = "수수료 1,700밧(현금)을 챙겨요."),
            stepOf("대사관 영사과에서 긴급여권을 신청해요. 수수료 1,700밧(현금)을 챙겨요."),
        )
        assertEquals(Step("경찰서에 신고하고, 접수증을 받아요."), stepOf("경찰서에 신고하고, 접수증을 받아요."))
        // 모든 번들 팩의 절차 문장이 글자 하나 빠짐없이 보존된다
        for (code in listOf("TH", "JP", "SG", "MY", "ID")) {
            val pack = runBlocking { TestPacks.repo.pack(code)!!.value }
            pack.procedures.flatMap { it.stepsKo }.forEach { s ->
                val step = stepOf(s)
                assertEquals(s, listOfNotNull(step.text, step.detail).joinToString(" "))
            }
        }
    }

    @Test
    fun longTextShowsFirstSentenceThenRest() {
        assertEquals("짧은 글이에요." to null, splitLongText("짧은 글이에요."))
        val long = "관광청이 소개하는 과자예요. " + "이어지는 설명이 아주 길게 계속돼서 육십 자를 훌쩍 넘기는 긴 문장이 됩니다. 마지막 문장도 하나 더 있어요."
        assertTrue(long.length > 60)
        val (first, rest) = splitLongText(long)
        assertEquals("관광청이 소개하는 과자예요.", first)
        assertEquals(long, "$first $rest")
        // 첫 문장 경계를 못 찾으면 전체를 그대로 보인다 (말줄임 없음)
        val noBreak = "가".repeat(80)
        assertEquals(noBreak to null, splitLongText(noBreak))
    }

    // ---------------- 전화번호 묶음 (공용 PhoneNumberText의 아주 좁은 창 줄바꿈 자리 — 한국어 줄바꿈은 components/KoreanTextTest) ----------------

    @Test
    fun phoneGroupsSplitOnlyAfterHyphens() {
        assertEquals(listOf("+66-", "81-", "914-", "5803"), phoneGroups("+66-81-914-5803"))
        assertEquals(listOf("+82-", "2-", "3210-", "0404"), phoneGroups("+82-2-3210-0404"))
        assertEquals(listOf("1155"), phoneGroups("1155"))
        for (n in listOf("+66-2-481-6000", "050-3816-2787", "1669")) assertEquals(n, phoneGroups(n).joinToString(""))
    }

    // ---------------- 쇼핑: 반입 불가 품목의 담기 (재검토 22) ----------------

    private fun shop(id: String, category: String, status: String) = ShoppingItem(
        id, category, Names("이름-$id", "Name-$id", "local-$id"), whyKo = "이유", importStatus = status,
        source = "tat", importSource = "customs", lastVerified = "2026-09-29",
    )

    @Test
    fun shoppingListGoesAllowedThenCautionThenProhibitedKeepingPackOrder() {
        val th = TestPacks.thailand.value.shopping
        val sorted = th.sortedBy { verdictOrder(it.import) }
        assertEquals(th.toSet(), sorted.toSet())
        assertEquals(listOf(0, 0, 1, 2), sorted.map { verdictOrder(it.import) })
        // 같은 판정 안에서는 팩 순서 그대로
        val allowed = th.filter { it.import == ImportStatus.Allowed }
        assertEquals(allowed, sorted.filter { it.import == ImportStatus.Allowed })
        assertEquals(ImportStatus.Prohibited, sorted.last().import)
    }

    @Test
    fun prohibitedItemsDoNotGetTheSameAddButton() {
        val allowed = cartAction(shop("a", "food", "allowed"), inCart = false)
        val caution = cartAction(shop("c", "food", "caution"), inCart = false)
        val food = cartAction(shop("p", "food", "prohibited"), inCart = false)
        val other = cartAction(shop("q", "souvenir", "prohibited"), inCart = false)
        assertEquals(R.string.shopping_add, allowed.label)
        assertEquals(allowed, caution)
        // 다른 말 + 다른 모양(흰 바탕 Neutral 테두리, 다른 아이콘) — 같은 Accent `담기`가 '사 와도 된다'로 읽히지 않게
        assertEquals(R.string.shopping_add_local_food, food.label)
        assertEquals(R.string.shopping_add_local, other.label)
        assertEquals(BadgeTone.Neutral, food.tone)
        assertNotEquals(allowed.icon, food.icon)
        assertNotEquals(allowed.tone, food.tone)
        // 담은 상태도 '담았어요 ✓'(초록) 대신 현지에서 먹기로
        assertEquals(R.string.shopping_in_cart_local_food, cartAction(shop("p", "food", "prohibited"), inCart = true).label)
        assertEquals(R.string.shopping_in_cart, cartAction(shop("a", "food", "allowed"), inCart = true).label)
        // TalkBack 동작 이름: 담기 전에는 품목 이름 + 보이는 말, 담은 뒤에는 빼기
        assertEquals(R.string.shopping_add_local_food_cd, food.actionLabel)
        assertEquals(R.string.shopping_remove_cd, cartAction(shop("p", "food", "prohibited"), inCart = true).actionLabel)
    }
}
