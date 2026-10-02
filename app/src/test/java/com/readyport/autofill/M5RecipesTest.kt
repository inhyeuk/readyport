package com.readyport.autofill

import com.readyport.pack.PackOrigin
import com.readyport.ui.TestPacks
import com.readyport.vault.BookingRecord
import com.readyport.vault.PassportRecord
import com.readyport.vault.VaultContents
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5: 말레이시아·싱가포르·인도네시아 레시피와 5개국 팩 (서명된 내장본, 운영 공개키) */
class M5RecipesTest {

    // ICAO 9303 표본 이름(가상 인물)에 대한민국 국적을 붙인 가짜 여권
    private val passport = PassportRecord(
        surname = "ERIKSSON", givenNames = "ANNA MARIA", documentNumber = "M12345678",
        nationality = "KOR", issuingState = "KOR", birthDate = "1974-08-12", sex = "F",
        expiryDate = "2036-04-15", source = "mrz", mrzVerified = true, savedAt = "x",
    )
    private val flights = listOf(
        BookingRecord(id = "1", kind = "flight", title = "가는 편", flightNumbers = listOf("KE651"), dates = listOf("2026-11-03"), savedAt = "x"),
        BookingRecord(id = "2", kind = "flight", title = "오는 편", flightNumbers = listOf("KE652"), dates = listOf("2026-11-07"), savedAt = "x"),
    )
    private val vault = VaultContents(passport = passport, bookings = flights)

    private fun recipe(id: String) = runBlocking { TestPacks.repo.recipe(id) }!!.also { assertEquals(PackOrigin.Bundled, it.origin) }.value

    private fun planValues(r: Recipe, saved: Map<String, String>, steps: Set<String>) =
        FormValues.plan(r, FormValues.build(r, vault, FormValues.defaults(r) + saved), steps)["fields"]!!.jsonArray
            .associate { it.jsonObject["key"]!!.jsonPrimitive.content to it.jsonObject["value"]!!.jsonPrimitive.content }

    @Test
    fun allFivePacksAndFourRecipesVerify() {
        for (cc in listOf("TH", "MY", "SG", "ID", "JP", "TW", "CN")) {
            val p = runBlocking { TestPacks.repo.pack(cc) }
            assertNotNull(cc, p)
            assertTrue(cc, p!!.value.emergency.isNotEmpty() && p.value.embassy != null && p.value.forms.isNotEmpty())
        }
        listOf("TH_TDAC", "MY_MDAC", "SG_SGAC", "ID_ALL_INDONESIA", "TW_TWAC").forEach { recipe(it) }
        // Visit Japan Web은 계정 로그인이 필요해 레시피 없이 수동 모드
        assertEquals(null, runBlocking { TestPacks.repo.recipe("JP_VJW") })
        // 중국 온라인 입국 카드는 여권 사진 올리기·서명이 있어 레시피 없이 수동 모드(값 복사해서 넣기)
        assertEquals(null, runBlocking { TestPacks.repo.recipe("CN_ARRIVAL") })
        // 필리핀 eTravel도 이메일 계정·확인 코드 뒤에 칸이 있어 레시피 없이 값 복사(수동) 모드
        val ph = runBlocking { TestPacks.repo.pack("PH") }!!.value
        assertTrue(ph.emergency.isNotEmpty() && ph.embassy != null && ph.forms.single().id == "PH_ETRAVEL")
        assertEquals(null, runBlocking { TestPacks.repo.recipe("PH_ETRAVEL") })
        // 베트남은 꼭 내야 하는 입국 신고 양식이 없다(무비자 45일). 사전 입국 정보(PAI)는 의무가 아닌 양식이고,
        // 확인 글자 창이 먼저 떠서 레시피를 두지 않는다. 전자비자도 안내만 — 레시피 없음
        val vn = runBlocking { TestPacks.repo.pack("VN") }!!.value
        assertTrue(vn.emergency.isNotEmpty() && vn.embassy != null && vn.requiredForms.isEmpty())
        assertEquals("VN_PAI", vn.forms.single().id)
        assertEquals(null, runBlocking { TestPacks.repo.recipe("VN_PAI") })
        assertEquals(null, runBlocking { TestPacks.repo.recipe("VN_EVISA") })
    }

    @Test
    fun sgacNameOrderDatesAndFlightSplit() {
        val v = planValues(recipe("SG_SGAC"), mapOf("profile.email" to "a@example.org", "profile.phone" to "1012345678"), setOf("traveller", "trip"))
        assertEquals("ANNA MARIA ERIKSSON", v["passport.full_name_given_first"]) // 'Given Name followed by Surname'
        assertEquals("15/04/2036", v["passport.expiry_date"])
        assertEquals("12/08/1974", v["passport.birth_date"])
        assertEquals("82", v["profile.phone_code"])
        assertEquals("651", v["trip.flight_digits"])
        assertEquals("KE", v["trip.flight_prefix"])
        assertEquals("07/11/2026", v["trip.departure_date"])
    }

    @Test
    fun mdacSelectValuesAreExactSiteTexts() {
        val v = planValues(
            recipe("MY_MDAC"),
            mapOf("stay.type" to "hotel", "stay.state" to "kl", "profile.email" to "a@example.org"),
            setOf("all"),
        )
        assertEquals("KOR - REPUBLIC OF KOREA", v["passport.nationality"])
        assertEquals("KOR - REPUBLIC OF KOREA", v["profile.country_birth"])
        assertEquals("KOR - REPUBLIC OF KOREA", v["trip.country_board"])
        assertEquals("FEMALE", v["passport.gender"])
        assertEquals("( 82 ) SOUTH KOREA", v["profile.phone_code"])
        assertEquals("AIR", v["trip.arrival_mode"])
        assertEquals("HOTEL/MOTEL/REST HOUSE", v["stay.type"])
        assertEquals("WP KUALA LUMPUR", v["stay.state"])
        assertEquals("a@example.org", v["profile.email_confirm"])
        assertEquals("KE651", v["trip.flight_no"])
    }

    @Test
    fun allIndonesiaFlightPrefixAndDigits() {
        val v = planValues(recipe("ID_ALL_INDONESIA"), emptyMap(), setOf("personal", "transport"))
        assertEquals("KE", v["trip.flight_prefix"])
        assertEquals("651", v["trip.flight_digits"])
        assertEquals("M12345678", v["passport.number"])
    }

    @Test
    fun twacValuesUseExactSiteTexts() {
        val r = recipe("TW_TWAC")
        val v = planValues(
            r,
            mapOf("profile.occupation" to "employee", "trip.purpose" to "tourism", "profile.email" to "a@example.org"),
            setOf("email", "traveler", "trip"),
        )
        assertEquals("ANNA MARIA ERIKSSON", v["passport.full_name_given_first"]) // 'Given Name followed by Surname'
        assertEquals("M12345678", v["passport.number"])
        assertEquals("15/04/2036", v["passport.expiry_date"])
        assertEquals("03/11/2026", v["trip.arrival_date"])
        // 기본 <select>는 사이트 선택지 글자 그대로여야 엔진이 고른다
        assertEquals("職員/CLERK/EMPLOYEE/STAFF", v["profile.occupation"])
        assertEquals("3.觀光 Sightseeing / Travel / Leisure", v["trip.purpose"])
        // 무비자는 기본 제안값 — 버튼은 사람이 누른다(assist)
        assertTrue(v["trip.visa_type"]!!.endsWith("Visa-Exempt(include TAC)"))
        assertEquals("82", v["profile.phone_code"])
        assertEquals("651", v["trip.flight_digits"])
        assertEquals("KE", v["trip.flight_prefix"])
        assertEquals("KE652", v["trip.departure_flight_no"])
        // 사람이 직접 하는 곳(인증 코드·서약·제출)은 엔진이 채우지 않고 표시만
        assertEquals(setOf("email", "declaration", "final_submit"), r.checkpoints.map { it.kind }.toSet())
    }

    @Test
    fun flightSplitRules() {
        assertEquals("KE" to "651", FormValues.splitFlight("KE651"))
        assertEquals("7C" to "2201", FormValues.splitFlight("7c 2201"))
        assertEquals(null, FormValues.splitFlight("KE"))
    }
}
