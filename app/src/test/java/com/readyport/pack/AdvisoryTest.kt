package com.readyport.pack

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** 경보 문단 판정·해시 — packs/schema 의 단일 출처 파일과 앱 상수가 같은지, Python 과 같은 해시인지 (SPEC_v5 §5.6·§5.7) */
class AdvisoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun constantsMatchRulesFile() {
        val rules = json.parseToJsonElement(File("../packs/schema/advisory_rules.json").readText()).jsonObject
        assertEquals(rules.getValue("advisory_paragraph_regex").jsonPrimitive.content, Advisory.PARAGRAPH_REGEX)
        assertEquals(rules.getValue("high_words").jsonArray.map { it.jsonPrimitive.content }, Advisory.HighAdvisoryWords)
    }

    @Test fun sharedVectorsMatchPython() {
        val vectors = json.parseToJsonElement(File("../packs/schema/advisory_hash_vectors.json").readText()).jsonObject
            .getValue("vectors").jsonArray
        assertEquals(3, vectors.size)
        vectors.forEach { v ->
            val o = v.jsonObject
            val sections = o.getValue("pack").jsonObject.getValue("sections").jsonArray.map { s ->
                val so = s.jsonObject
                Section(
                    id = so.getValue("id").jsonPrimitive.content,
                    titleKo = "",
                    bodyKo = so.getValue("body_ko").jsonArray.map { it.jsonPrimitive.content },
                    source = "",
                    lastVerified = "",
                )
            }
            val pack = CountryPack(1, "XX", "2026.10.01-1", "2026-10-01", Names("가짜", "Fake", "Fake"), sources = emptyList(), sections = sections)
            assertEquals(o.getValue("name").jsonPrimitive.content, o.getValue("advisory_sha256").jsonPrimitive.content, Advisory.advisoryHash(pack))
        }
    }

    @Test fun realPackParagraphCounts() {
        val expected = mapOf("TW" to 1, "SG" to 1, "VN" to 1, "JP" to 1, "CN" to 2, "TH" to 3, "MY" to 2, "ID" to 2, "PH" to 4)
        expected.forEach { (cc, n) ->
            val pack = json.decodeFromJsonElement<CountryPack>(
                json.parseToJsonElement(File("src/main/assets/packs/$cc/pack.json").readText()) as JsonObject,
            )
            assertEquals(cc, n, Advisory.paragraphs(pack)!!.size)
        }
    }

    @Test fun noSafetySectionNoHash() {
        val pack = CountryPack(1, "XX", "v", "d", Names("가", "a", "a"), sources = emptyList())
        assertNull(Advisory.advisoryHash(pack))
    }
}
