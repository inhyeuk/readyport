package com.readyport.trip

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 여러 여행 저장소 (2026-10-02): 예전 한 여행 저장본 옮기기, 여행 id로 가리기, 여행별 체크가 서로 섞이지 않는지.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class TripRepositoryTest {

    private val trips = TripRepository(ApplicationProvider.getApplicationContext())

    @Before
    fun reset() = runBlocking { trips.clear() }

    @After
    fun cleanup() = runBlocking { trips.clear() }

    @Test
    fun legacySingleTripIsMigratedIntoListWithStableId() = runBlocking {
        val legacy = """{"country":"TH","startDate":"2026-11-03","endDate":"2026-11-07","arrivedAt":123}"""
        trips.writeLegacyForTest(legacy)

        // 옮기기 전에 읽어도 목록 모양이고 id가 매번 같다
        val first = trips.all()
        assertEquals(1, first.size)
        val id = first.single().id
        assertTrue(id.isNotBlank())
        assertEquals(id, trips.all().single().id)
        assertEquals(123L, first.single().arrivedAt)

        // 앱 시작 때 옮기기: 예전 꼭 챙길 물건 체크는 그 여행 체크리스트로
        assertEquals(id, trips.migrateLegacy(setOf("power_bank", "medicine")))
        val book = trips.book.first()
        assertEquals(listOf(id), book.trips.map { it.id })
        assertEquals(true, book.checks[id]?.marks?.get("essential.power_bank"))
        assertEquals(true, book.checks[id]?.marks?.get("essential.medicine"))
        // 두 번 옮기지 않는다
        assertNull(trips.migrateLegacy(setOf("payment")))
        assertEquals(1, trips.all().size)
        assertNull(trips.book.first().checks[id]?.marks?.get("essential.payment"))
    }

    @Test
    fun writingBeforeMigrationKeepsTheSameTrip() = runBlocking {
        trips.writeLegacyForTest("""{"country":"JP","startDate":"2026-12-01","endDate":"2026-12-04"}""")
        val id = trips.all().single().id
        trips.setMark(id, "booking", true)
        // 새 장부를 쓰면 예전 저장본은 지워지고 같은 여행이 두 번 생기지 않는다
        assertNull(trips.migrateLegacy())
        assertEquals(listOf(id), trips.all().map { it.id })
        assertEquals(true, trips.checks(id).first().marks["booking"])
    }

    @Test
    fun brokenLegacyIsDropped() = runBlocking {
        trips.writeLegacyForTest("not json")
        assertEquals(emptyList<Trip>(), trips.all())
        assertNull(trips.migrateLegacy())
        assertEquals(emptyList<Trip>(), trips.all())
    }

    @Test
    fun sameCountryTwiceIsTwoTripsWithSeparateChecklists() = runBlocking {
        val a = trips.save(Trip("TH", "2026-11-03", "2026-11-07"))
        val b = trips.save(Trip("TH", "2027-02-10", "2027-02-14"))
        assertNotEquals(a.id, b.id)
        assertEquals(2, trips.all().size)

        trips.setMark(a.id, "booking", true)
        trips.addCustom(a.id, "우산 챙기기")
        assertEquals(true, trips.checks(a.id).first().marks["booking"])
        assertEquals(1, trips.checks(a.id).first().custom.size)
        assertTrue(trips.checks(b.id).first().marks.isEmpty())
        assertTrue(trips.checks(b.id).first().custom.isEmpty())

        // 날짜를 고쳐도 체크는 여행 id에 붙어 남는다
        trips.update(a.id) { it.copy(endDate = "2026-11-08") }
        assertEquals(true, trips.checks(a.id).first().marks["booking"])

        // 하나를 지워도 다른 여행은 그대로
        trips.delete(a.id)
        assertEquals(listOf(b.id), trips.all().map { it.id })
        assertTrue(trips.book.first().checks[a.id] == null)
    }

    @Test
    fun customItemsAreTrimmedAndBounded() = runBlocking {
        val t = trips.save(Trip("SG", "2026-11-03", "2026-11-05"))
        assertNull(trips.addCustom(t.id, "   "))
        val id = trips.addCustom(t.id, "  " + "가".repeat(200) + "  ")!!
        assertEquals(TripRepository.CUSTOM_MAX, trips.checks(t.id).first().custom.single().text.length)
        trips.editCustom(t.id, id, "환전 영수증 챙기기")
        assertEquals("환전 영수증 챙기기", trips.checks(t.id).first().custom.single().text)
        trips.setMark(t.id, id, true)
        trips.removeCustom(t.id, id)
        val c = trips.checks(t.id).first()
        assertTrue(c.custom.isEmpty())
        assertFalse(id in c.marks)
    }

    @Test
    fun signalsKeepOnlyResults() = runBlocking {
        val t = trips.save(Trip("TH", "2026-11-03", "2026-11-07"))
        val check = PassportCheck(PassportStatus.Short, 6, "arrival", "TH|x")
        trips.recordSignals(true, mapOf(t.id to TripSignal(check, formSubmitted = true)))
        val book = trips.book.first()
        assertEquals(true, book.passportSaved)
        assertEquals(check, book.checks[t.id]?.passport)
        assertEquals(true, book.checks[t.id]?.formSubmitted)
        // 여권을 지우면 결과도 지운다(입국 카드 기록은 모르면 그대로)
        trips.recordSignals(false, emptyMap())
        val after = trips.book.first()
        assertEquals(false, after.passportSaved)
        assertNull(after.checks[t.id]?.passport)
        assertEquals(true, after.checks[t.id]?.formSubmitted)
    }
}
