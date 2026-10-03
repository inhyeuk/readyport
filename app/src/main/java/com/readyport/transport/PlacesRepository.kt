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
 * 숙소 하나를 가는 곳 목록에 **한 방향으로** 맞춘다(숙소 → 가는 곳, 순수 함수라 테스트가 본다).
 * 같은 id가 있으면 그 자리에서 바꾸고(순서 유지), 없으면 끝에 더한다. [place]가 null이면 그 id를 지운다(주소를 비웠거나 숙소를 지웠을 때).
 * 사람이 직접 넣은 가는 곳은 id가 달라서 건드리지 않는다.
 */
fun syncStayPlace(places: List<Place>, stayId: String, place: Place?): List<Place> = when {
    place == null -> places.filterNot { it.id == stayId }
    places.any { it.id == stayId } -> places.map { if (it.id == stayId) place else it }
    else -> places + place
}

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

    /**
     * 숙소 하나를 가는 곳으로 맞춘다 ([syncStayPlace]). 숙소를 저장·지울 때 저장소가 부른다 —
     * 같은 호텔을 두 번 적지 않게. [place]가 null이면 그 가는 곳을 지운다.
     */
    suspend fun syncStay(stayId: String, place: Place?) = context.placesStore.edit { p ->
        val now = p[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
        p[key] = json.encodeToString(serializer, syncStayPlace(now, stayId, place))
        if (place == null && p[selectedKey] == stayId) p.remove(selectedKey)
    }

    suspend fun delete(id: String) = context.placesStore.edit { p ->
        val now = p[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
        p[key] = json.encodeToString(serializer, now.filterNot { it.id == id })
        if (p[selectedKey] == id) p.remove(selectedKey)
    }
}
