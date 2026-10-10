package com.readyport.attractions

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.readyport.pack.PackVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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

    /**
     * 이 나라 찜의 id를 **사람이 정한 순서**로 (2026-10-09 사장님 요청 — 찜 순서를 관광 순서로).
     * 찜 목록 자체가 순서다(저장된 JSON 배열 순서 = 찜한 순서 = 예전 화면 순서). 그래서 예전 저장본은 옮길 것 없이 그 순서 그대로다.
     * 지도 핀 번호처럼 다른 화면도 이 Flow로 같은 번호를 매긴다.
     */
    fun orderOf(country: String): Flow<List<String>> =
        saved.map { items -> items.filter { it.country == country }.map { it.id } }.distinctUntilChanged()

    /** 찜하기·찜 취소. 이미 찜한 곳을 다시 찜하면 **자리와 찜한 날을 그대로** 둔다(맨 뒤로 밀리지 않게). 새 찜은 맨 뒤 */
    suspend fun setSaved(key: String, saved: Boolean, today: String) {
        store.edit { prefs ->
            val items = decode(prefs[itemsKey])
            val next = when {
                saved && items.any { it.key == key } -> return@edit
                saved -> items + SavedAttraction(key, today)
                else -> items.filterNot { it.key == key }
            }
            prefs[itemsKey] = encode(next)
        }
    }

    /**
     * 여러 곳을 한 번에 찜한다(여행 계획의 관광지 모두 찜하기). [ids]의 순서대로 맨 뒤에 붙이고, 이미 찜한 곳은 자리·날짜를 그대로 둔다.
     * 한 번의 edit 이라 중간에 끊겨도 반만 들어가지 않는다. @return (새로 찜한 수, 이미 찜해 둔 수)
     */
    suspend fun addAll(country: String, ids: List<String>, today: String): Pair<Int, Int> {
        var added = 0
        var existing = 0
        store.edit { prefs ->
            val items = decode(prefs[itemsKey])
            val have = items.map { it.key }.toSet()
            val wanted = ids.map { "$country/$it" }.distinct()
            val fresh = wanted.filter { it !in have }
            existing = wanted.size - fresh.size
            added = fresh.size
            if (fresh.isNotEmpty()) prefs[itemsKey] = encode(items + fresh.map { SavedAttraction(it, today) })
        }
        return added to existing
    }

    /** 같은 나라 찜 안에서 [by]칸 옮긴다(-1 = 한 칸 위로). 다른 나라 찜의 자리는 그대로 */
    suspend fun move(key: String, by: Int) {
        store.edit { prefs -> prefs[itemsKey] = encode(SavedOrder.move(decode(prefs[itemsKey]), key, by)) }
    }

    /** 같은 나라 찜 안에서 [index]번째(0부터) 자리로 옮긴다 — 꾹 눌러 끌기 */
    suspend fun moveTo(key: String, index: Int) {
        store.edit { prefs -> prefs[itemsKey] = encode(SavedOrder.moveTo(decode(prefs[itemsKey]), key, index)) }
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

/**
 * 찜 순서 바꾸기(순수 함수). 찜 목록은 여러 나라가 섞인 한 줄이라 **같은 나라 찜끼리만** 자리를 바꾸고,
 * 다른 나라 찜이 있던 칸은 그대로 둔다 — 그 나라 목록에서 본 순서와 저장된 순서가 늘 같다.
 */
object SavedOrder {
    fun move(items: List<SavedAttraction>, key: String, by: Int): List<SavedAttraction> {
        val country = key.substringBefore('/')
        val mine = items.filter { it.country == country }
        val at = mine.indexOfFirst { it.key == key }
        if (at < 0) return items
        val target = (at + by).coerceIn(0, mine.lastIndex)
        return if (target == at) items else moveTo(items, key, target)
    }

    fun moveTo(items: List<SavedAttraction>, key: String, index: Int): List<SavedAttraction> {
        val country = key.substringBefore('/')
        val mine = items.filter { it.country == country }.toMutableList()
        val at = mine.indexOfFirst { it.key == key }
        if (at < 0) return items
        val item = mine.removeAt(at)
        mine.add(index.coerceIn(0, mine.size), item)
        val next = mine.iterator()
        return items.map { if (it.country == country) next.next() else it }
    }
}
