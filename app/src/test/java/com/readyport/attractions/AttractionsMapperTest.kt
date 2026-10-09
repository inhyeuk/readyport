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
        assertEquals(5, c.attractions.size)
        assertTrue(c.hidden.isEmpty())
        assertNotNull(c.attraction("senso-ji")!!.photoLink)
    }
}
