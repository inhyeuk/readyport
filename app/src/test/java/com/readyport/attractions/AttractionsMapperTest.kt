package com.readyport.attractions

import com.readyport.attractions.AttTestData.place
import com.readyport.attractions.AttTestData.region
import com.readyport.ui.attractions.AttractionsEntryUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 관대하게 읽기 (SPEC_v5 §4.8): 모르는 값이 섞여도 깨지지 않고 안전한 쪽으로 */
class AttractionsMapperTest {

    private fun decode(json: String) = AttractionsJson.decodeFromString(AttractionsDoc.serializer(), json)

    @Test fun minimalDocumentDecodes() {
        val doc = decode("""{"doc_type":"attractions","regions":[],"attractions":[],"future":{"x":1}}""")
        val c = AttractionsMapper.map(doc)!!
        assertTrue(c.attractions.isEmpty())
        assertEquals(AttractionsEntryUi.NotYet, AttractionsEntryUi.of(c, 0))
    }

    @Test fun docTypeMismatchIsMalformed() {
        assertNull(AttractionsMapper.map(decode("""{"doc_type":"pack","regions":[],"attractions":[]}""")))
    }

    @Test fun unknownValuesAreHiddenOrSafe() {
        val json = """
        {"doc_type":"attractions","country":"XX","version":"2026.10.09-1","release":"published",
         "regions":[{"id":"xx_a","order":1,"name_ko":"가","kind":"floating","airports":[],"advisory":{"level":"1"}}],
         "attractions":[
           {"id":"ok","names":{"ko":"좋은 곳","en":"Ok"},"region":"xx_a","category":"heritage",
            "tags":[{"id":"unesco"},{"id":"romantic"}],"access":{"modes":["metro","teleport"]},
            "facts":{"kind":"mystery","regular_closed":["mon","funday"],"entry":"maybe"},
            "status":{"value":"half_open"},"advisory":{"level":"5"},"risk":["volcano","aliens"],"new_field":true},
           {"id":"new-cat","names":{"ko":"새 종류"},"region":"xx_a","category":"space_port","advisory":{"level":"1"}},
           {"id":"lost","names":{"ko":"모르는 지역"},"region":"xx_zz","category":"heritage","advisory":{"level":"1"}},
           {"id":"danger","names":{"ko":"위험한 곳"},"region":"xx_a","category":"nature","advisory":{"level":"3"}}
         ],
         "retired":[{"id":"old","reason":"vanished"}]}
        """.trimIndent()
        val c = AttractionsMapper.map(decode(json))!!
        val ok = c.attraction("ok")!!
        assertEquals(listOf(Tag.Unesco), ok.tags)
        assertEquals(listOf(AccessMode.Metro), ok.accessModes)
        assertEquals(listOf(ClosedDay.Mon), ok.facts!!.closedDays)
        assertEquals(false, ok.facts!!.publicSpace) // 모르는 facts.kind → facility
        assertEquals(Entry.Unknown, ok.facts!!.entry)
        assertEquals(OpenStatus.Unknown, ok.status) // 모르는 status → 열려 있지 않은 것으로
        assertEquals(AdvisoryLevel.Unknown, ok.advisory.level) // 모르는 level → 보이되 주의 띠
        assertEquals(listOf(Risk.Volcano), ok.risks)
        assertEquals(RegionKind.Base, c.region("xx_a")!!.kind) // 모르는 region.kind → base
        assertEquals(HiddenReason.NeedsUpdate, c.hidden["new-cat"])
        assertEquals(HiddenReason.NeedsUpdate, c.hidden["lost"])
        assertEquals(HiddenReason.Safety, c.hidden["danger"])
        assertEquals("위험한 곳", c.hiddenNames["danger"])
        assertEquals(listOf("ok"), c.attractions.map { it.id })
        assertEquals("editorial", c.retired.getValue("old").reason) // 모르는 retired.reason → editorial
    }

    @Test fun daytripWithoutBaseBecomesBase() {
        val c = AttTestData.catalog(AttTestData.doc(listOf(region("xx_d", 1, kind = "daytrip")), listOf(place("p", "xx_d", "가"))))
        assertEquals(RegionKind.Base, c.region("xx_d")!!.kind)
    }

    @Test fun oldestVerifiedAndSources() {
        val d = AttTestData.basic()
        val p = d.attractions.first().copy(status = StatusDto("open", null, "official", "2025-12-01"))
        val c = AttTestData.catalog(d.copy(attractions = listOf(p)))
        assertEquals("2025-12-01", c.attractions.first().oldestVerified)
        assertTrue("official" in c.attractions.first().sourceIds)
    }

    @Test fun urlsAreFiltered() {
        val p = AttTestData.basic().attractions.first().copy(officialUrl = "http://insecure.example", photoLink = "https://evil.example/x")
        val a = AttTestData.catalog(AttTestData.basic().copy(attractions = listOf(p))).attractions.first()
        assertNull(a.officialUrl)
        assertNull(a.photoLink)
    }

    @Test fun debugSampleMapsWithoutUnknownValues() {
        val c = AttTestData.catalog(AttTestData.debugSample())
        assertTrue(c.sample)
        assertEquals(37, c.attractions.size) // 일본 1차(간토·간사이·규슈·홋카이도) 시범 자료
        assertTrue(c.hidden.isEmpty())
        assertNotNull(c.attraction("sensoji")!!.photoLink)
        // wiki-fill이 채운 위키백과 제목 — 위키백과 문서가 없는 곳(다케가와라 온천)은 버튼 없이
        c.attractions.filter { it.id != "takegawara-onsen" }.forEach { a -> assertNotNull(a.id, a.wiki?.ko ?: a.wiki?.en) }
        assertNull(c.attraction("takegawara-onsen")!!.wiki)
        assertEquals(WikiPage("ko", "센소지"), c.attraction("sensoji")!!.wiki!!.preferred)
    }

    // ---------------- 위키백과 제목 (선택 필드, 관대하게) ----------------

    private fun wikiOf(json: String): WikiTitles? {
        val doc = decode(
            """{"doc_type":"attractions","country":"XX","regions":[{"id":"xx_a","name_ko":"가","advisory":{"level":"1"}}],
               "attractions":[{"id":"p","names":{"ko":"가짜"},"region":"xx_a","category":"heritage","advisory":{"level":"1"}$json}]}""",
        )
        return AttractionsMapper.map(doc)!!.attraction("p")!!.wiki
    }

    @Test fun wikiMissingOrNullIsNull() {
        assertNull(wikiOf(""))
        assertNull(wikiOf(""","wiki":null"""))
        assertNull(wikiOf(""","wiki":{}"""))
    }

    @Test fun wikiPrefersKoreanThenEnglish() {
        assertEquals(WikiPage("ko", "가짜 절"), wikiOf(""","wiki":{"ko":"가짜 절","en":"Fake Temple"}""")!!.preferred)
        assertEquals(WikiPage("en", "Fake Temple"), wikiOf(""","wiki":{"en":"Fake Temple"}""")!!.preferred)
    }

    @Test fun wikiBadTitlesAreDropped() {
        // 쓸 수 없는 글자·앞뒤 공백·빈 글자·너무 긴 제목은 버린다 → 영어만 남거나 통째로 null
        assertEquals(WikiTitles(null, "Ok"), wikiOf(""","wiki":{"ko":"나쁜|제목","en":"Ok"}"""))
        assertNull(wikiOf(""","wiki":{"ko":" 앞공백","en":""}"""))
        assertNull(wikiOf(""","wiki":{"en":"${"x".repeat(256)}"}"""))
        assertNull(wikiOf(""","wiki":{"ko":"a#b"}"""))
        // 모르는 언어 키·잘못된 모양은 무시(깨지지 않음)
        assertNull(wikiOf(""","wiki":{"fr":"Faux"}"""))
    }

    @Test fun wikiTitleRule() {
        assertEquals("Sensō-ji", AttractionsMapper.wikiTitle("Sensō-ji"))
        assertEquals("오사카성 (가짜)", AttractionsMapper.wikiTitle("오사카성 (가짜)"))
        listOf("", " a", "a ", "a[b]", "a{b}", "a<b>", "a\nb", "a\u007fb").forEach { assertNull(it, AttractionsMapper.wikiTitle(it)) }
    }
}
