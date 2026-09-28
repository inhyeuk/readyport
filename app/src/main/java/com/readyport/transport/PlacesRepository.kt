package com.readyport.transport

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.placesStore: DataStore<Preferences> by preferencesDataStore(name = "places")

/**
 * 가는 곳(숙소 주소 등). 여행지에서 인터넷 없이 '기사님께 보여주기'를 해야 해서
 * 잠금 없이 바로 열리는 기기 저장소에 둔다(백업 제외). 여권 같은 개인정보는 넣지 않는다.
 */
@Singleton
class PlacesRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("places")
    private val selectedKey = stringPreferencesKey("selected_place")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Place.serializer())

    val places: Flow<List<Place>> = context.placesStore.data.map { p ->
        p[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
    }
    val selectedId: Flow<String?> = context.placesStore.data.map { it[selectedKey] }

    suspend fun add(name: String, addressLocal: String): Place {
        val place = Place(UUID.randomUUID().toString(), name.trim(), addressLocal.trim())
        context.placesStore.edit { p ->
            val now = p[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
            p[key] = json.encodeToString(serializer, now + place)
            p[selectedKey] = place.id
        }
        return place
    }

    suspend fun select(id: String) = context.placesStore.edit { it[selectedKey] = id }

    suspend fun delete(id: String) = context.placesStore.edit { p ->
        val now = p[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
        p[key] = json.encodeToString(serializer, now.filterNot { it.id == id })
        if (p[selectedKey] == id) p.remove(selectedKey)
    }
}
