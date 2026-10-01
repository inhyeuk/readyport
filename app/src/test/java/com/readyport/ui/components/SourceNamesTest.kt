package com.readyport.ui.components

import com.readyport.pack.CountryPack
import com.readyport.ui.TestPacks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면에 보이는 글자에 내부 ID가 새지 않는지 (DESIGN_SPEC 1.2 #1, 4.5).
 * 앵커 없는 정규식이라 "출처 tat_chanthaburi · …"처럼 문장 안의 ID도 잡는다.
 */
object SourceTextCheck {
    val internalId = Regex("""\b[a-z]+(?:_[a-z0-9]+)+\b""")

    /** 내부 ID 또는 "출처 출처"가 들어 있는 글자들 */
    fun leaks(texts: Collection<String>): List<String> =
        texts.filter { internalId.containsMatchIn(it) || "출처 출처" in it }.distinct()
}

class SourceNamesTest {

    private val index get() = TestPacks.index.value
    private val codes = listOf("TH", "JP", "SG", "MY", "ID", "PH", "VN")

    private fun CountryPack.policySourceIds(): List<Pair<String, String>> = buildList {
        requirements.forEach { r ->
            add("requirement ${r.purpose}" to r.source)
            r.apply?.let { add("apply ${it.form}" to it.source) }
        }
        forms.forEach { add("form ${it.id}" to it.source) }
        sections.forEach { add("section ${it.id}" to it.source) }
        emergency.forEach { add("emergency ${it.id}" to it.source) }
        embassy?.let { add("embassy" to it.source) }
        procedures.forEach { add("procedure ${it.id}" to it.source) }
        power?.let { add("power" to it.source) }
        shopping.forEach {
            add("shopping ${it.id}" to it.source)
            add("shopping ${it.id} import" to it.importSource)
        }
    }

    @Test
    fun everyBundledPolicySourceResolvesToAName() = runBlocking {
        val problems = mutableListOf<String>()
        for (code in codes) {
            val pack = TestPacks.repo.pack(code)!!.value
            val names = pack.sources.associate { it.id to it.name }
            pack.policySourceIds().forEach { (what, id) ->
                val name = resolveSourceName(id, names, "?")
                if (name == "?" || name == id) problems += "$code $what: $id"
            }
        }
        val idx = index
        val indexNames = idx.sources.associate { it.id to it.name }
        (idx.commonEmergency.map { "common ${it.id}" to it.source } +
            idx.essentials.mapNotNull { e -> e.source?.let { "essential ${e.id}" to it } } +
            idx.returnFacts.map { "return_fact" to it.source }).forEach { (what, id) ->
            val name = resolveSourceName(id, indexNames, "?")
            if (name == "?" || name == id) problems += "index $what: $id"
        }
        assertTrue("이름으로 풀리지 않는 출처 ID:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun resolveNeverReturnsTheId() {
        assertEquals("공식 안내", resolveSourceName("tat_chanthaburi", emptyMap(), "공식 안내"))
        assertEquals("공식 안내", resolveSourceName("x", mapOf("x" to " "), "공식 안내"))
        assertEquals("태국관광청", resolveSourceName("tat", mapOf("tat" to "태국관광청"), "공식 안내"))
    }

    @Test
    fun sourceLinesGroupSameDateAndDropDuplicates() {
        val lines = sourceLines(
            listOf(
                SourceRef("외교부 해외안전여행 · 인도네시아", "2026.09.28"),
                SourceRef("인도네시아 이민국 · All Indonesia", "2026.09.28"),
                SourceRef("주인도네시아 대한민국 대사관", "2026.09.30"),
                SourceRef("외교부 해외안전여행 · 인도네시아", "2026.09.28"),
            ),
        )
        // 재검토 R9: 기관이 다르면 기관마다 한 줄(같은 날짜끼리는 SourceList가 한 덩어리로 날짜를 끝에 한 번), 같은 이름은 한 번만
        assertEquals(
            listOf(
                SourceRef("외교부 해외안전여행 · 인도네시아", "2026.09.28"),
                SourceRef("인도네시아 이민국 · All Indonesia", "2026.09.28"),
                SourceRef("주인도네시아 대한민국 대사관", "2026.09.30"),
            ),
            lines,
        )
        assertEquals(
            listOf(
                SourceRef("외교부 해외안전여행 · 인도네시아\n인도네시아 이민국 · All Indonesia", "2026.09.28"),
                SourceRef("주인도네시아 대한민국 대사관", "2026.09.30"),
            ),
            sourceBlocks(lines),
        )
        // 출처가 하나면 그대로 한 줄
        assertEquals(listOf(SourceRef("a", "d")), sourceLines(listOf(SourceRef("a", "d"))))
    }

    @Test
    fun idPatternCatchesIdsInsideSentences() {
        val leaks = SourceTextCheck.leaks(
            listOf(
                "출처 tat_chanthaburi · 최종 확인 2026.09.29",
                "출처 출처 · 최종 확인 2026.09.29",
                "customs_allowance",
                "출처 외교부 해외안전여행 · 태국 · 최종 확인 2026.09.28",
                "+66-81-914-5803",
                "TH_TDAC 없이 대문자",
            ),
        )
        assertEquals(listOf("출처 tat_chanthaburi · 최종 확인 2026.09.29", "출처 출처 · 최종 확인 2026.09.29", "customs_allowance"), leaks)
    }
}
