package com.readyport.trip

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tripStore: DataStore<Preferences> by preferencesDataStore(name = "trip")

/** 지금 여행 하나를 기기에만 저장한다 (백업 제외 규칙 적용 폴더) */
@Singleton
class TripRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("current_trip")
    private val json = Json { ignoreUnknownKeys = true }

    val trip: Flow<Trip?> = context.tripStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString(Trip.serializer(), it) }.getOrNull() }
    }

    suspend fun current(): Trip? = trip.first()

    suspend fun save(trip: Trip) {
        context.tripStore.edit { it[key] = json.encodeToString(Trip.serializer(), trip) }
    }

    suspend fun update(transform: (Trip) -> Trip) {
        context.tripStore.edit { prefs ->
            val now = prefs[key]?.let { runCatching { json.decodeFromString(Trip.serializer(), it) }.getOrNull() } ?: return@edit
            prefs[key] = json.encodeToString(Trip.serializer(), transform(now))
        }
    }

    suspend fun clear() {
        context.tripStore.edit { it.remove(key) }
    }
}
