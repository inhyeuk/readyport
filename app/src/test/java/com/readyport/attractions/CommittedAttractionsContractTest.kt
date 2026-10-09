package com.readyport.attractions

import com.readyport.attractions.search.AttractionSearchIndex
import com.readyport.pack.PackKeys
import com.readyport.pack.PackVerifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

/**
 * 커밋된 관광지 서명본 계약 (SPEC_v5 §12, CI 게이트): app/src/main/assets/packs/<CC>/attractions.json 마다
 * 관광지 키(rp-att-*) 서명 · 매퍼가 숨기는 '모르는 값' 0건 · 종류·태그 문자열 키 · 지역 묶음·검색 색인 · 지역 이름으로 그 지역 관광지가 나옴 ·
 * 0곳 지역 없음 · NFC. 서명본이 아직 없으면(첫 나라 공개 전) debug 샘플로 같은 검사(서명 빼고)를 돌려 검사 코드가 살아 있게 한다.
 */
class CommittedAttractionsContractTest {
    private val enums = Json.parseToJsonElement(File("../packs/schema/attractions.enums.json").readText()).jsonObject
    private fun list(key: String) = enums.getValue(key).jsonArray.map { it.jsonPrimitive.content }.toSet()
    private val stringKeys = Regex("""<string name="([^"]+)">""").findAll(File("src/main/res/values/strings_attractions.xml").readText())
        .map { it.groupValues[1] }.toSet()

    private val committed: List<File> = File("src/main/assets/packs").listFiles().orEmpty()
        .map { File(it, "attractions.json") }.filter { it.isFile }.sortedBy { it.path }

    @Test fun committedFilesAreSignedWithAttractionsKey() {
        val verifier = PackVerifier(PackKeys.ATTRACTIONS)
        committed.forEach { f ->
            val sig = File(f.path + ".sig")
            assertTrue("${f.path}: .sig 없음", sig.isFile)
            // 서명 파일은 json.dumps 기본 모양({"kid": "rp-att-…"} — 국가 팩 .sig 와 같다). 띄어쓰기와 상관없이 kid 를 본다
            assertTrue(f.path, Regex(""""kid"\s*:\s*"rp-att-""").containsMatchIn(sig.readText()))
            assertEquals(f.path, PackVerifier.Result.Valid, verifier.verify(f.readBytes(), sig.readBytes()))
        }
    }

    @Test fun committedFilesHonourTheContract() {
        committed.forEach { f -> checkContract(f.readText(), f.parentFile!!.name, signed = true) }
    }

    @Test fun debugSampleHonoursTheContract() {
        checkContract(AttTestData.debugSampleBytes().decodeToString(), "JP", signed = false)
    }

    private fun checkContract(text: String, country: String, signed: Boolean) {
        val label = "$country(${if (signed) "서명본" else "debug 샘플"})"
        assertTrue("$label: NFC", nonNfc(Json.parseToJsonElement(text)).isEmpty())
        val doc = AttractionsJson.decodeFromString(AttractionsDoc.serializer(), text)
        assertEquals(label, country, doc.country)
        if (signed) {
            assertEquals("$label: published", "published", doc.release)
            assertTrue("$label: sample 금지", !doc.sample)
        }
        // 앱이 모르는 값 0건 (모르는 값이 있으면 매퍼가 숨겨 '앱 업데이트 필요'가 된다)
        val unknown = buildList {
            doc.regions.forEach { if (it.kind !in list("region_kinds")) add("${it.id}.kind=${it.kind}") }
            doc.attractions.forEach { a ->
                if (a.category !in list("categories")) add("${a.id}.category=${a.category}")
                a.tags.forEach { if (it.id !in list("tags")) add("${a.id}.tag=${it.id}") }
                a.access?.modes.orEmpty().forEach { if (it !in list("access_modes")) add("${a.id}.mode=$it") }
                a.facts?.regularClosed.orEmpty().forEach { if (it !in list("regular_closed")) add("${a.id}.day=$it") }
                a.risk.forEach { if (it !in list("risk")) add("${a.id}.risk=$it") }
                if (a.status?.value !in list("status")) add("${a.id}.status=${a.status?.value}")
                if (a.advisory?.level !in list("advisory_levels")) add("${a.id}.level=${a.advisory?.level}")
                if (a.facts?.kind !in list("facts_kinds")) add("${a.id}.facts.kind=${a.facts?.kind}")
            }
        }
        assertEquals(label, emptyList<String>(), unknown)
        val catalog = AttractionsMapper.map(doc)!!
        assertTrue("$label: 숨김 0", catalog.hidden.isEmpty())
        catalog.attractions.forEach { a ->
            assertTrue("attractions_cat_${a.category.key}" in stringKeys)
            a.tags.forEach { t -> assertTrue("attractions_tag_${t.key}" in stringKeys) }
        }
        val groups = RegionGrouping.group(catalog, catalog.attractions)
        val used = groups.map { it.region.id }.toSet()
        assertEquals("$label: 0곳 지역 없음", catalog.regions.map { it.id }.toSet(), used)
        val index = AttractionSearchIndex(catalog)
        groups.forEach { g ->
            val found = index.search(g.region.nameKo).hits.map { it.attraction.id }.toSet()
            assertTrue("$label: '${g.region.nameKo}' 검색에 그 지역 관광지", g.places.all { it.id in found })
        }
    }

    private fun nonNfc(e: JsonElement): List<String> = when (e) {
        is JsonPrimitive -> if (e.isString && Normalizer.normalize(e.content, Normalizer.Form.NFC) != e.content) listOf(e.content) else emptyList()
        is JsonArray -> e.flatMap { nonNfc(it) }
        is JsonObject -> e.entries.flatMap { (k, v) -> (if (Normalizer.normalize(k, Normalizer.Form.NFC) != k) listOf(k) else emptyList()) + nonNfc(v) }
        else -> emptyList()
    }
}
