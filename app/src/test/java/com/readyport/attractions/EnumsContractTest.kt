package com.readyport.attractions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * packs/schema/attractions.enums.json(단일 출처) = 앱 상수 (SPEC_v5 §4.8). 모든 종류·태그에 화면 문자열이 있고,
 * 검색 사전(CategorySynonyms)의 라벨이 strings_attractions.xml 과 같은 글자인지.
 */
class EnumsContractTest {
    private val enums = Json.parseToJsonElement(File("../packs/schema/attractions.enums.json").readText()).jsonObject
    private fun list(key: String) = enums.getValue(key).jsonArray.map { it.jsonPrimitive.content }
    private val strings: Map<String, String> = Regex("""<string name="([^"]+)">([^<]*)</string>""")
        .findAll(File("src/main/res/values/strings_attractions.xml").readText())
        .associate { it.groupValues[1] to it.groupValues[2] }

    @Test fun categories() {
        assertEquals(list("categories"), Category.entries.map { it.key })
        Category.entries.forEach { c ->
            val label = strings["attractions_cat_${c.key}"]
            assertEquals(c.key, CategorySynonyms.label.getValue(c), label)
            assertTrue("${c.key} 라벨 7자 이내", label!!.length <= 7)
        }
    }

    @Test fun tags() {
        assertEquals(list("tags"), Tag.entries.map { it.key })
        Tag.entries.forEach { t -> assertEquals(t.key, CategorySynonyms.tagLabel.getValue(t), strings["attractions_tag_${t.key}"]) }
    }

    @Test fun otherValues() {
        assertEquals(list("access_modes"), AccessMode.entries.map { it.key })
        assertEquals(list("regular_closed") - "unknown", ClosedDay.entries.map { it.key })
        assertEquals(list("risk"), Risk.entries.map { it.key })
        assertEquals(setOf("none", "1", "2", "special", "3", "4"), list("advisory_levels").toSet())
        list("advisory_hidden_levels").forEach { assertEquals(AdvisoryLevel.Hidden, AdvisoryLevel.of(it)) }
        assertEquals(listOf("open", "partial", "temp_closed"), list("status"))
        assertEquals(setOf("closed", "long_closure", "safety", "editorial", "merged"), list("retired_reasons").toSet())
        // 위키백과 판: 도구(wiki-fill)가 채우는 언어 = 앱이 여는 언어
        assertEquals(list("wiki_langs").toSet(), com.readyport.attractions.wiki.WikiHosts.LANGS)
    }

    @Test fun everyCategoryHasSynonymsAndExcludedTopicsPointToRealCategories() {
        Category.entries.forEach { assertTrue(CategorySynonyms.synonyms[it].orEmpty().isNotEmpty()) }
        ExcludedTopics.words.values.filterNotNull().forEach { assertTrue(it in Category.entries) }
    }
}
