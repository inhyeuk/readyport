package com.readyport.trip

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.itinerary.Itinerary
import com.readyport.itinerary.ItineraryStop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 관광 일정 저장(2026-10-09): 여행 장부 안에 키·날·순서만, 여행을 지우면 같이 지운다 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class ItineraryStorageTest {

    private val trips = TripRepository(ApplicationProvider.getApplicationContext())

    @Before
    fun reset() = runBlocking { trips.clear() }

    @After
    fun cleanup() = runBlocking { trips.clear() }

    @Test
    fun itineraryIsStoredPerTripAndDeletedWithIt() = runBlocking {
        val jp = trips.save(Trip("JP", "2026-10-08", "2026-10-12"))
        val other = trips.save(Trip("JP", "2026-12-01", "2026-12-03"))
        trips.editItinerary(jp.id) { Itinerary.add(it, listOf(ItineraryStop("JP/sensoji", 0), ItineraryStop("JP/dotonbori", 1))) }
        assertEquals(listOf("JP/sensoji", "JP/dotonbori"), trips.itinerary(jp.id).first().stops.map { it.key })
        assertTrue(trips.itinerary(other.id).first().stops.isEmpty()) // 다른 여행과 섞이지 않는다

        trips.editItinerary(jp.id) { Itinerary.moveToDay(it, "JP/sensoji", 3) }
        assertEquals(ItineraryStop("JP/sensoji", 3), trips.itinerary(jp.id).first().stops.last())

        trips.delete(jp.id)
        assertFalse(jp.id in trips.book.first().itineraries)
        assertEquals(listOf(other.id), trips.all().map { it.id })
    }

    @Test
    fun unknownTripAndEmptyPlanAreNotStored() = runBlocking {
        trips.editItinerary("nope") { Itinerary.add(it, listOf(ItineraryStop("JP/a", 0))) }
        assertTrue(trips.book.first().itineraries.isEmpty())
        val t = trips.save(Trip("JP", "2026-10-08", "2026-10-12"))
        trips.editItinerary(t.id) { Itinerary.add(it, listOf(ItineraryStop("JP/a", 0))) }
        trips.editItinerary(t.id) { Itinerary.remove(it, "JP/a") }
        assertTrue(trips.book.first().itineraries.isEmpty())
    }

    @Test
    fun changingTheCountryDropsThePlanButDatesKeepIt() = runBlocking {
        val t = trips.save(Trip("JP", "2026-10-08", "2026-10-12"))
        trips.editItinerary(t.id) { Itinerary.add(it, listOf(ItineraryStop("JP/a", 4))) }
        trips.save(t.copy(endDate = "2026-10-10")) // 날짜를 줄여도 일정은 남는다(마지막 날로 보인다)
        assertEquals(1, trips.itinerary(t.id).first().stops.size)
        trips.save(t.copy(country = "TH"))
        assertTrue(trips.itinerary(t.id).first().stops.isEmpty())
    }
}
