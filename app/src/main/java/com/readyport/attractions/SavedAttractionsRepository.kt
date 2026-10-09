package com.readyport.attractions

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.readyport.pack.PackVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** 찜 하나: 전역 키 "<CC>/<id>" + 찜한 날(yyyy-MM-dd). 개인정보 없음 */
@Serializable
data class SavedAttraction(val key: String, val savedAt: String) {
    val country: String get() = key.substringBefore('/')
    val id: String get() = key.substringAfter('/')
}

/**
 * 관광지 찜 (SPEC_v5 §6.5) — **이 휴대폰에만**(⟦결정 D16⟧ A). DataStore `saved_attractions`(백업 규칙이 전체 제외).
 * CloudSync·Firestore·알림 구독·나라 찜([com.readyport.data.settings.SettingsRepository.favorites])과 이어지지 않는다 —
 * 관광지를 찜해도 나라 알림이 켜지지 않는다.
 * 팩이 바뀌어도 찜을 조용히 지우지 않는다: 합쳐진 항목(retired merged)은 새 id로 옮기고([migrate]), 나머지 사라진 항목은 화면이 이유와 함께 보여 준다.
 */
class SavedAttractionsRepository(private val store: DataStore<Preferences>) {
    private val itemsKey = stringPreferencesKey("items")
    private val noticeKey = booleanPreferencesKey("first_notice_done")
    private val mergedKey = stringSetPreferencesKey("merged_notice")
    private fun migratedKey(country: String) = stringPreferencesKey("migrated_version_$country")
    private val listSerializer = ListSerializer(SavedAttraction.serializer())

    val saved: Flow<List<SavedAttraction>> = store.data.map { decode(it[itemsKey]) }

    /** 처음 찜 안내를 이미 보였는지 */
    val firstNoticeDone: Flow<Boolean> = store.data.map { it[noticeKey] == true }

    /** 합쳐서 옮긴 찜 키 — 찜 목록에 '다른 항목으로 합쳤어요'를 한 번 보인다 */
    val mergedNotice: Flow<Set<String>> = store.data.map { it[mergedKey].orEmpty() }

    suspend fun current(): List<SavedAttraction> = saved.first()

    suspend fun setSaved(key: String, saved: Boolean, today: String) {
        store.edit { prefs ->
            val items = decode(prefs[itemsKey]).filterNot { it.key == key }
            prefs[itemsKey] = encode(if (saved) items + SavedAttraction(key, today) else items)
        }
    }

    suspend fun markFirstNoticeDone() {
        store.edit { it[noticeKey] = true }
    }

    suspend fun clearMergedNotice() {
        store.edit { it.remove(mergedKey) }
    }

    /**
     * 합쳐진 항목의 찜을 새 항목으로 옮긴다 — published 파일을 **성공적으로 로드했을 때만**, 그 version이
     * `migrated_version_<CC>`보다 높을 때만 부른다(그 판단도 여기서 한다). 단일 edit 트랜잭션이고 멱등이다.
     * 같은 키가 둘이 되면 하나로 합치고 찜한 날은 이른 값을 남긴다.
     * @return 옮긴 찜 수
     */
    suspend fun migrate(country: String, version: String, retired: Collection<Retired>): Int {
        var moved = 0
        store.edit { prefs ->
            val done = prefs[migratedKey(country)]
            if (done != null && !PackVersion.isNewer(version, done)) return@edit
            val result = SavedMigration.apply(decode(prefs[itemsKey]), country, retired)
            moved = result.moved.size
            prefs[itemsKey] = encode(result.items)
            if (result.moved.isNotEmpty()) prefs[mergedKey] = prefs[mergedKey].orEmpty() + result.moved
            prefs[migratedKey(country)] = version
        }
        return moved
    }

    private fun decode(raw: String?): List<SavedAttraction> =
        raw?.let { runCatching { AttractionsJson.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()

    private fun encode(items: List<SavedAttraction>): String = AttractionsJson.encodeToString(listSerializer, items)
}

/** 찜 옮기기 계산(순수 함수, 테스트용으로 따로) */
object SavedMigration {
    data class Result(val items: List<SavedAttraction>, val moved: Set<String>)

    fun apply(items: List<SavedAttraction>, country: String, retired: Collection<Retired>): Result {
        val merged = retired.filter { it.reason == "merged" && !it.replacedBy.isNullOrBlank() }.associate { it.id to it.replacedBy!! }
        val moved = mutableSetOf<String>()
        val renamed = items.map { item ->
            val target = merged[item.id]
            if (item.country == country && target != null) {
                val key = "$country/$target"
                moved += key
                item.copy(key = key)
            } else {
                item
            }
        }
        // 같은 키는 하나로 — 찜한 날은 이른 값, 순서는 처음 나온 자리
        val out = LinkedHashMap<String, SavedAttraction>()
        renamed.forEach { item ->
            val prev = out[item.key]
            out[item.key] = if (prev == null || item.savedAt < prev.savedAt) item else prev
        }
        return Result(out.values.toList(), moved)
    }
}
