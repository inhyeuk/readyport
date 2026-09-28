package com.readyport.doc.mrz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 테스트 데이터는 ICAO 9303 문서의 가상 국가(UTOPIA) 표본과, 체크디지트를 계산해 만든 가짜 값만 쓴다 (작업 규칙 4).
 */
class MrzParserTest {

    // ICAO 9303 Part 4 표본 여권 (가상 국가 UTO)
    private val icaoLine1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
    private val icaoLine2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
    private val today = LocalDate.of(2026, 9, 28)

    @Test
    fun checkDigitMatchesIcaoSpecimen() {
        assertEquals(6, MrzParser.checkDigit("L898902C3"))
        assertEquals(2, MrzParser.checkDigit("740812"))
        assertEquals(9, MrzParser.checkDigit("120415"))
        assertEquals(1, MrzParser.checkDigit("ZE184226B<<<<<"))
        assertEquals(0, MrzParser.checkDigit("L898902C3674081221204159ZE184226B<<<<<1"))
    }

    @Test
    fun parsesIcaoSpecimen() {
        val r = MrzParser.parse(icaoLine1, icaoLine2, today)
        assertNotNull(r)
        r!!
        assertTrue(r.checks.toString(), r.allChecksPass)
        assertEquals("P", r.documentCode)
        assertEquals("UTO", r.issuingState)
        assertEquals("ERIKSSON", r.surname)
        assertEquals("ANNA MARIA", r.givenNames)
        assertEquals("L898902C3", r.documentNumber)
        assertEquals("UTO", r.nationality)
        assertEquals(LocalDate.of(1974, 8, 12), r.birthDate)
        assertEquals('F', r.sex)
        assertEquals(LocalDate.of(2012, 4, 15), r.expiryDate)
        assertEquals("ZE184226B", r.personalNumber)
    }

    @Test
    fun toleratesTypicalOcrNoise() {
        // 공백, 끝의 '<' 누락, 날짜·체크디지트 자리의 O(알파벳), '«' 기호, 소문자
        val noisy1 = "p<uto ERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<"
        val noisy2 = "L898902C36UTO74O8122F12O4159ZE184226B««<<<1O"
        val r = MrzParser.parse(noisy1, noisy2, today)
        assertNotNull(r)
        assertTrue(r!!.checks.toString(), r.allChecksPass)
        assertEquals(LocalDate.of(1974, 8, 12), r.birthDate)
    }

    @Test
    fun findsMrzInsideFullOcrText() {
        val ocr = """
            PASSPORT
            UTOPIA
            ERIKSSON
            ANNA MARIA
            $icaoLine1
            $icaoLine2
        """.trimIndent()
        val r = MrzParser.findInText(ocr, today)
        assertNotNull(r)
        assertTrue(r!!.allChecksPass)
        assertEquals("L898902C3", r.documentNumber)
    }

    @Test
    fun tamperedDigitFailsChecks() {
        val tampered = icaoLine2.replaceRange(18, 19, "3") // 생년월일 740812 → 740813
        val r = MrzParser.parse(icaoLine1, tampered, today)!!
        assertFalse(r.allChecksPass)
        assertFalse(r.checks.getValue(MrzCheck.BirthDate))
        assertFalse(r.checks.getValue(MrzCheck.Composite))
        assertTrue(r.checks.getValue(MrzCheck.DocumentNumber))
    }

    @Test
    fun recoversLetterODigitZeroConfusionInDocumentNumber() {
        // 여권번호 "AB0123O45"(0과 O가 섞인 가짜 번호)를 OCR이 전부 O로 읽은 경우
        val line2 = fakeLine2(docNumber = "AB0123O45")
        val misread = line2.replaceRange(2, 3, "O")
        val r = MrzParser.parse(fakeLine1(), misread, today)!!
        assertTrue(r.checks.toString(), r.allChecksPass)
        assertEquals("AB0123O45", r.documentNumber)
    }

    @Test
    fun emptyPersonalNumberAcceptsFillerCheckDigit() {
        val r = MrzParser.parse(fakeLine1(), fakeLine2(docNumber = "X12345678", personal = ""), today)!!
        assertTrue(r.checks.toString(), r.allChecksPass)
        assertEquals("", r.personalNumber)
    }

    @Test
    fun birthCenturyNeverInFuture() {
        val young = MrzParser.parse(fakeLine1(), fakeLine2(birth = "100101"), today)!!
        assertEquals(2010, young.birthDate.year)
        val old = MrzParser.parse(fakeLine1(), fakeLine2(birth = "850101"), today)!!
        assertEquals(1985, old.birthDate.year)
    }

    @Test
    fun rejectsNonPassportOrGarbage() {
        assertNull(MrzParser.parse("I<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", icaoLine2, today))
        assertNull(MrzParser.parse(icaoLine1, "short<<line", today))
        assertNull(MrzParser.findInText("hello\nworld", today))
    }

    @Test
    fun toStringHidesPersonalData() {
        val r = MrzParser.parse(icaoLine1, icaoLine2, today)!!
        assertFalse(r.toString().contains("L898902C3"))
        assertFalse(r.toString().contains("ERIKSSON"))
    }

    // --- 가짜 MRZ 생성 (체크디지트 계산은 위 ICAO 표본 테스트로 검증된 함수를 쓴다) ---

    private fun fakeLine1() = "P<UTOTESTER<<SAMPLE".padEnd(44, '<')

    private fun fakeLine2(
        docNumber: String = "X12345678",
        birth: String = "900101",
        expiry: String = "300101",
        personal: String = "",
    ): String {
        val cd = MrzParser::checkDigit
        val doc = docNumber.padEnd(9, '<')
        val pers = personal.padEnd(14, '<')
        val persCd = if (personal.isEmpty()) "<" else cd(pers).toString()
        val body = doc + cd(doc) + "UTO" + birth + cd(birth) + "M" + expiry + cd(expiry) + pers + persCd
        val composite = body.substring(0, 10) + body.substring(13, 20) + body.substring(21, 43)
        return body + cd(composite)
    }
}
