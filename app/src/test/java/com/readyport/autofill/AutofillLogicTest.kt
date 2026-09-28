package com.readyport.autofill

import com.readyport.pack.PackOrigin
import com.readyport.ui.TestPacks
import com.readyport.ui.form.EngineResult
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.VaultContents
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** M4: 레시피 → 값 → 엔진 계획. 실제 서명된 TDAC 레시피를 운영 공개키로 읽어 쓴다 */
class AutofillLogicTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val recipe = TestPacks.tdacRecipe.value

    // ICAO 9303 표본(가상 국가) — 실제 여권 아님
    private val passport = PassportRecord(
        surname = "ERIKSSON", givenNames = "ANNA MARIA", documentNumber = "L898902C3",
        nationality = "KOR", issuingState = "KOR", birthDate = "1974-08-12", sex = "F",
        expiryDate = "2036-04-15", source = "mrz", mrzVerified = true, savedAt = "2026-09-28T10:00",
    )
    private val outbound = BookingRecord(id = "1", kind = "flight", title = "가는 편", flightNumbers = listOf("KE651"), dates = listOf("2026-11-03"), savedAt = "x")
    private val inbound = BookingRecord(id = "2", kind = "flight", title = "오는 편", flightNumbers = listOf("ke652"), dates = listOf("2026-11-07"), savedAt = "x")
    private val vault = VaultContents(passport = passport, bookings = listOf(inbound, outbound))

    @Test
    fun recipeIsSignedAndBundled() {
        val loaded = TestPacks.tdacRecipe
        assertEquals(PackOrigin.Bundled, loaded.origin)
        assertEquals("TH_TDAC", recipe.formId)
        assertTrue(recipe.officialUrlPatterns.all { it.startsWith("https://tdac.immigration.go.th/") })
        // 선언형 데이터만: 레시피 어디에도 스크립트 조각이 없다 (PRD 7.5)
        val raw = java.io.File("src/main/assets/packs/recipes/TH_TDAC.json").readText()
        listOf("<script", "javascript:", "eval(", "function(", "=>").forEach { assertFalse(it, raw.contains(it)) }
    }

    @Test
    fun valuesComeFromPassportAndBookings() {
        val v = FormValues.build(recipe, vault, emptyMap())
        assertEquals("ERIKSSON", v.getValue("passport.surname").value)
        assertEquals(ValueOrigin.Passport, v.getValue("passport.surname").origin)
        assertEquals("대한민국 (KOR)", v.getValue("passport.nationality").display)
        assertEquals("여 (Female)", v.getValue("passport.gender").display)
        assertEquals("2026-11-03", v.getValue("trip.arrival_date").display)
        assertEquals("KE651", v.getValue("trip.flight_no").value)
        assertEquals("KE652", v.getValue("trip.departure_flight_no").value) // 대문자로
        assertEquals("2026-11-07", v.getValue("trip.departure_date").display)
        assertEquals(ValueOrigin.Flight, v.getValue("trip.flight_no").origin)
    }

    @Test
    fun individualValuesAndRequiredCheck() {
        val empty = FormValues.build(recipe, vault, emptyMap())
        val missing = FormValues.missingRequired(recipe, empty).map { it.key }
        assertTrue(missing.containsAll(listOf("trip.purpose", "profile.occupation", "stay.address", "stay.type")))
        assertFalse(missing.contains("passport.surname"))

        val saved = mapOf(
            "trip.purpose" to "tourism", "stay.type" to "hotel", "profile.occupation" to "office worker",
            "profile.country_res" to "대한민국", "profile.city_res" to "seoul", "profile.phone" to "1012345678",
            "stay.province" to "BANGKOK", "stay.address" to "1 sample road",
        )
        val full = FormValues.build(recipe, vault, saved)
        assertEquals(emptyList<RecipeField>(), FormValues.missingRequired(recipe, full))
        assertEquals("관광 · Tourism · ท่องเที่ยว", full.getValue("trip.purpose").display)
        assertEquals("OFFICE WORKER", full.getValue("profile.occupation").value) // transform upper
        // 사용자가 고친 값이 서류 값보다 먼저
        val fixed = FormValues.build(recipe, vault, saved + ("passport.given" to "anna"))
        assertEquals("ANNA", fixed.getValue("passport.given").value)
        assertEquals(ValueOrigin.User, fixed.getValue("passport.given").origin)
    }

    @Test
    fun planUsesValueForTextAndDisplayForAssist() {
        val values = FormValues.build(recipe, vault, mapOf("trip.purpose" to "tourism", "stay.address" to "1 \"QUOTE\" RD </script>"))
        val plan = FormValues.plan(recipe, values, setOf("personal", "stay", "trip"))
        val fields = plan["fields"]!!.jsonArray.associateBy { it.jsonObject["key"]!!.jsonPrimitive.content }
        assertEquals("ERIKSSON", fields.getValue("passport.surname").jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("관광 · Tourism · ท่องเที่ยว", fields.getValue("trip.purpose").jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, fields.getValue("profile.country_res").jsonObject["selector"])
        // 따옴표·태그가 든 값도 JSON 문자열로 안전하게 들어간다
        assertEquals("1 \"QUOTE\" RD </script>", fields.getValue("stay.address").jsonObject["value"]!!.jsonPrimitive.content)
        // 보이지 않는 단계의 칸은 넣지 않는다
        val onlyPersonal = FormValues.plan(recipe, values, setOf("personal"))["fields"]!!.jsonArray
        assertTrue(onlyPersonal.all { it.jsonObject["key"]!!.jsonPrimitive.content.startsWith("passport.") || it.jsonObject["key"]!!.jsonPrimitive.content.startsWith("profile.") })
        // 사람이 할 곳은 모두 계획에 들어간다(표시용)
        assertEquals(setOf("captcha", "health", "declaration", "email", "final_submit"),
            plan["checkpoints"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet())
    }

    @Test
    fun urlPolicy() {
        val p = recipe.officialUrlPatterns
        assertTrue(UrlPolicy.matches("https://tdac.immigration.go.th/arrival-card/#/add", p))
        assertTrue(UrlPolicy.matches("https://TDAC.immigration.go.th/arrival-card/", p))
        assertFalse(UrlPolicy.matches("http://tdac.immigration.go.th/arrival-card/", p))
        assertFalse(UrlPolicy.matches("https://tdac.immigration.go.th.evil.example/arrival-card/", p))
        assertFalse(UrlPolicy.matches("https://tdac.immigration.go.th@evil.example/arrival-card/", p))
        assertFalse(UrlPolicy.matches("https://tdac.immigration.go.th:8443/arrival-card/", p))
        assertFalse(UrlPolicy.matches("https://tdac.immigration.go.th/other/", p))
        assertFalse(UrlPolicy.matches("https://evil.example/?u=https://tdac.immigration.go.th/arrival-card/", p))
        assertFalse(UrlPolicy.matches(null, p))
        assertTrue(UrlPolicy.allowedNavigation("https://tdac.immigration.go.th/manual/", p))
        assertFalse(UrlPolicy.allowedNavigation("https://example.com/", p))
    }

    @Test
    fun engineResultParsing() {
        // evaluateJavascript 는 JS 문자열을 JSON 으로 한 번 더 감싸서 준다
        val raw = "\"{\\\"filled\\\":[\\\"passport.surname\\\"],\\\"assist\\\":[],\\\"missing\\\":[\\\"x.y\\\"],\\\"absent\\\":[],\\\"checkpoints\\\":[\\\"captcha\\\"]}\""
        val r = EngineResult.fill(raw)!!
        assertEquals(listOf("passport.surname"), r.filled)
        assertEquals(listOf("x.y"), r.missing)
        assertEquals(listOf(true, false), EngineResult.probe("\"[true,false]\""))
        assertNull(EngineResult.fill("null"))
        assertNull(EngineResult.fill("garbage"))
    }

    @Test
    fun fieldReportsCarryNoPersonalData() {
        val reporter = QueuedFieldReporter(tmp.root.resolve("r/field_reports.jsonl"))
        repeat(205) { reporter.report(FieldReport("TH_TDAC", "2026.09.28-1", "personal", "selector_missing", "0.1.0", it.toLong(), "2026.09.00-0543")) }
        val pending = reporter.pending()
        assertEquals(200, pending.size)
        val fields = FieldReport::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") || it == "Companion" }.toSet()
        assertEquals(setOf("formId", "packVersion", "stepId", "errorCode", "appVersion", "ts", "siteVersion"), fields)
    }
}
