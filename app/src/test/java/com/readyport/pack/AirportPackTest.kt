package com.readyport.pack

import com.readyport.ui.TestPacks
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 도착 공항 순서 (팩 airports[], 2026-10-03): 서명된 내장 태국 팩 그대로 읽고, 예전 팩(공항 없음)도 읽히는지 */
class AirportPackTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun pack(cc: String) = runBlocking { TestPacks.repo.pack(cc)!!.value }

    @Test
    fun thailandAirportsFromSignedPack() {
        val th = pack("TH")
        assertEquals(listOf("BKK", "DMK", "HKT"), th.airports.map { it.code })
        th.airports.forEach { ap ->
            assertTrue(ap.code, ap.steps.size in 3..7)
            assertTrue(ap.code, ap.mapUrl.startsWith("https://"))
            // 모든 출처 id가 팩 sources에 있다(화면에 내부 ID가 보이지 않게)
            ap.sourceIds.forEach { id -> assertNotNull("${ap.code} $id", th.source(id)) }
            // 한국 여권 자동 심사대는 공식 안내가 한국을 콕 집어 밝히지 않아 모름(null) — 앱은 줄을 숨긴다
            assertNull(ap.egateKr)
            // 입국 카드 줄은 입국 심사 단계에 붙는다
            assertEquals(AirportStep.IMMIGRATION, ap.steps[ap.formStepIndex!!].kind)
            assertTrue(ap.formCheckKo!!.contains("TDAC"))
            assertEquals("mofa_th", ap.formCheckSource)
        }
        val bkk = th.airport("BKK")!!
        assertEquals("수완나품 공항", bkk.nameKo)
        assertEquals("2층 도착 홀", bkk.steps.first { it.kind == "baggage" }.whereKo)
        assertNull(th.airport(null))
        assertNull(th.airport("NRT"))
    }

    @Test
    fun oldPackWithoutAirportsStillParses() {
        val old = """
            {"schema_version":1,"country":"JP","version":"2026.09.27-1","last_verified":"2026-09-27",
             "names":{"ko":"일본","en":"Japan","local":"日本"},
             "sources":[{"id":"s","name":"출처","url":"https://example.org"}]}
        """.trimIndent()
        val p = json.decodeFromString(CountryPack.serializer(), old)
        assertTrue(p.airports.isEmpty())
    }

    @Test
    fun stepSourceAndFormSourceDefaultToAirportSource() {
        val ap = json.decodeFromString(
            Airport.serializer(),
            """
            {"code":"XXX","name_ko":"가 공항","name_en":"X","city_ko":"가","egate_kr":true,"form_check_ko":"QR을 보여 줘요.",
             "steps":[{"kind":"deplane","title_ko":"내려요","body_ko":"내려요."},
                      {"kind":"form_check","title_ko":"QR","body_ko":"보여 줘요.","source":"b"},
                      {"kind":"exit","title_ko":"나가요","body_ko":"나가요.","where_ko":"1층"}],
             "map_url":"https://example.org/map","source":"a","last_verified":"2026-10-03"}
            """.trimIndent(),
        )
        assertEquals(listOf("a", "b"), ap.sourceIds)
        // form_check 단계가 있으면 입국 심사보다 먼저 그 단계에
        assertEquals(1, ap.formStepIndex)
        assertEquals(true, ap.egateKr)
    }
}
