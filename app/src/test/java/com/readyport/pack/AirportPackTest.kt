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

    /**
     * 아홉 나라 공항 안내(2026-10-03 합침): 단계 수·kind, 출처가 팩 sources 에 있는지, 입국 카드 줄에 그 나라 양식 이름이 있는지,
     * 자동 심사대 판정이 조사해 둔 값과 같은지 — 다음에 팩을 고칠 때 값이 조용히 바뀌지 않게 못을 박는다.
     */
    @Test
    fun everyCountryAirportGuideIsWellFormed() {
        val forms = mapOf(
            "TH" to "TDAC", "JP" to "Visit Japan Web", "SG" to "SG Arrival Card", "MY" to "MDAC",
            "ID" to "All Indonesia", "TW" to "TWAC", "CN" to "입국 카드", "PH" to "eTravel", "VN" to "PAI",
        )
        val codes = mapOf(
            "TH" to listOf("BKK", "DMK", "HKT"), "JP" to listOf("NRT", "HND", "KIX", "FUK"), "SG" to listOf("SIN"),
            "MY" to listOf("KUL", "BKI"), "ID" to listOf("DPS", "CGK"), "TW" to listOf("TPE", "TSA", "KHH"),
            "CN" to listOf("PVG", "PEK", "PKX"), "PH" to listOf("MNL", "CEB"), "VN" to listOf("SGN", "HAN", "DAD", "CXR"),
        )
        val kinds = setOf("deplane", "health", "immigration", "egate", "form_check", "baggage", "customs", "transfer", "exit")
        codes.forEach { (cc, expected) ->
            val p = pack(cc)
            assertEquals(cc, expected, p.airports.map { it.code })
            p.airports.forEach { ap ->
                val where = "$cc ${ap.code}"
                assertTrue(where, ap.steps.size in 3..7)
                ap.steps.forEach { s -> assertTrue("$where ${s.kind}", s.kind in kinds) }
                assertTrue(where, ap.mapUrl.startsWith("https://"))
                ap.sourceIds.forEach { id -> assertNotNull("$where $id", p.source(id)) }
                // 입국 카드 줄은 그 나라 양식 이름을 말하고, form_check(없으면 입국 심사) 단계에 붙는다.
                // 두 단계가 다 없으면(싱가포르 = 자동 심사대 한 단계) 줄은 단계 목록 위에 그려진다
                assertTrue("$where ${ap.formCheckKo}", ap.formCheckKo!!.contains(forms.getValue(cc)))
                ap.formStepIndex?.let { at ->
                    val formKind = ap.steps[at].kind
                    assertTrue("$where $formKind", formKind == AirportStep.FORM_CHECK || formKind == AirportStep.IMMIGRATION)
                }
            }
        }
        // 한국 여권 자동 심사대: 공식 안내가 한국 여권을 콕 집어 밝힌 곳만 true/false (나머지는 모름)
        assertEquals(true, pack("SG").airport("SIN")!!.egateKr)
        assertEquals(true, pack("MY").airport("KUL")!!.egateKr)
        assertNull(pack("MY").airport("BKI")!!.egateKr)
        assertEquals(false, pack("PH").airport("MNL")!!.egateKr)
        assertNull(pack("PH").airport("CEB")!!.egateKr)
        listOf("TPE", "TSA", "KHH").forEach { code ->
            val ap = pack("TW").airport(code)!!
            assertEquals(code, true, ap.egateKr)
            // 대만은 처음 쓰기 전 유인 카운터 등록이 조건 — 메모 첫 문장에서 밝히고, egate 단계 제목도 등록을 말한다
            assertTrue(code, ap.egateNoteKo!!.startsWith("처음 쓰기 전에 반드시"))
            assertEquals(code, AirportStep.EGATE, ap.steps[ap.egateStepIndex!!].kind)
        }
        pack("VN").airports.forEach { ap ->
            assertEquals(ap.code, false, ap.egateKr)
            assertEquals(ap.code, AirportStep.EGATE, ap.steps[ap.egateStepIndex!!].kind)
        }
        // 2023년 정부 발표만 있는 세 곳은 그 사실을 메모에 밝힌다(다낭만 공항 공식 안내)
        listOf("SGN", "HAN", "CXR").forEach { code ->
            assertTrue(code, pack("VN").airport(code)!!.egateNoteKo!!.contains("2023년"))
        }
        assertTrue(pack("VN").airport("DAD")!!.egateNoteKo!!.contains("다낭 공항 공식 안내"))
        // 모름(null)이어도 공식 안내가 갈리는 인도네시아는 메모를 둔다 — 앱이 `분명하지 않아요` 줄로 보인다
        pack("ID").airports.forEach { ap ->
            assertNull(ap.code, ap.egateKr)
            assertNotNull(ap.code, ap.egateNoteKo)
        }
        assertTrue(pack("ID").airport("CGK")!!.egateNoteKo!!.contains("공식 안내 두 곳이 서로 달라요"))
        // 모름 + 메모도 없는 나라는 줄 자체가 없다
        (pack("JP").airports + pack("CN").airports + pack("TH").airports).forEach { ap ->
            assertNull(ap.code, ap.egateKr)
            assertNull(ap.code, ap.egateNoteKo)
        }
    }

    /** 베트남 사전 입국 정보(PAI)는 의무가 아닌 양식 — 팩이 그렇게 적고, 자동 입력 레시피가 없다 */
    @Test
    fun vietnamPreArrivalIsAnOptionalForm() {
        val vn = pack("VN")
        val pai = vn.forms.single()
        assertEquals("VN_PAI", pai.id)
        assertTrue(pai.optional)
        assertEquals("https://prearrival.immigration.gov.vn/", pai.officialUrl)
        assertTrue(pai.windowKo, pai.windowKo.contains("3일 전") && pai.windowKo.contains("도착한 뒤"))
        // 기한을 만들지 않는다(도착일 기준 기간 일수 없음) → 오늘 단계·알림이 급한 할 일로 만들지 않는다
        assertNull(pai.windowDaysIncludingArrival)
        assertNotNull(vn.source(pai.source))
        assertTrue(vn.requiredForms.isEmpty())
        assertNull(runBlocking { TestPacks.repo.recipe("VN_PAI") })
        // 들어갈 때 섹션에는 같은 말을 되풀이하지 않는다(양식 카드가 맡는다)
        assertTrue(vn.sections.none { s -> s.bodyKo.any { it.contains("prearrival.immigration.gov.vn") } })
        // 다른 나라 양식은 그대로 의무
        listOf("TH", "JP", "SG", "MY", "ID", "TW", "CN", "PH").forEach { cc ->
            assertEquals(cc, pack(cc).forms.size, pack(cc).requiredForms.size)
        }
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
        // egate·입국 심사 단계가 모두 없으면 자동 심사대 줄은 단계 목록 위에(붙일 단계가 없다)
        assertNull(ap.egateStepIndex)
    }
}
