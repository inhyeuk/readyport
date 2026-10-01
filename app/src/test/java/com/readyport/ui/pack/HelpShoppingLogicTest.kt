package com.readyport.ui.pack

import com.readyport.pack.EmergencyContact
import com.readyport.ui.TestPacks
import com.readyport.ui.components.Step
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    // ---------------- 줄바꿈 도우미 (API 33 미만 keep-all, 출처 날짜, 전화번호 묶음) ----------------

    @Test
    fun joinWordsGluesHangulInsideWordsOnly() {
        val wj = "\u2060"
        assertEquals("주${wj}세${wj}요", joinWords("주세요"))
        // 공백은 그대로 줄바꿈 자리, 괄호·쉼표는 붙은 어절과 함께
        assertEquals("무${wj}료${wj})", joinWords("무료)"))
        assertEquals("천${wj}천${wj}히 말${wj}해 주${wj}세${wj}요", joinWords("천천히 말해 주세요"))
        // 한글이 없는 글(라틴·숫자·태국어)은 손대지 않는다
        assertEquals("Play Store 123", joinWords("Play Store 123"))
        assertEquals("กรุณาเรียกตำรวจ", joinWords("กรุณาเรียกตำรวจ"))
        // 섞인 어절은 한글 쪽 경계만: Play는 그대로, '스토어에서'는 묶음
        assertEquals("Play 스${wj}토${wj}어${wj}에${wj}서 받${wj}기", joinWords("Play 스토어에서 받기"))
        // 보이지 않는 문자만 더해진다 — 빼면 원문
        listOf("관광경찰 (영어·한국어, 무료)", "여권을 잃어버렸어요", "직원에게 보여주기").forEach {
            assertEquals(it, joinWords(it).replace(wj, ""))
        }
        // 여는 괄호 앞과 가운뎃점 뒤는 줄을 바꿔도 된다 (긴 덩어리 앞에 `미화`만 홀로 남지 않게)
        assertEquals("달${wj}러(${wj}과${wj}세", joinWords("달러(과세"))
        assertEquals("고${wj}기${wj}·햄${wj}·소", joinWords("고기·햄·소"))
        // 두 번 해도 같다
        assertEquals(joinWords("주세요"), joinWords(joinWords("주세요")))
    }

    @Test
    fun sourceDateKeepsTheDateButLetsItMoveToTheNextLine() {
        assertEquals("\u200B2026.09.29", sourceDate("2026-09-29"))
        assertEquals("2026.09.29", sourceDate("2026-09-29").removePrefix(SOURCE_DATE_BREAK))
    }

    @Test
    fun phoneGroupsSplitOnlyAfterHyphens() {
        assertEquals(listOf("+66-", "81-", "914-", "5803"), phoneGroups("+66-81-914-5803"))
        assertEquals(listOf("+82-", "2-", "3210-", "0404"), phoneGroups("+82-2-3210-0404"))
        assertEquals(listOf("1155"), phoneGroups("1155"))
        for (n in listOf("+66-2-481-6000", "050-3816-2787", "1669")) assertEquals(n, phoneGroups(n).joinToString(""))
    }
}
