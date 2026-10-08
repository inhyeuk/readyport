package com.readyport.board

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/** [BoardLocalStore]의 운영 판 — DataStore `board` (백업 제외 규칙이 앱 데이터 전체를 뺀다) */
class DataStoreBoardLocalStore(private val store: DataStore<Preferences>) : BoardLocalStore {
    private val rulesKey = booleanPreferencesKey("rules_agreed")
    private val nickKey = stringPreferencesKey("nickname")
    private val blockedKey = stringSetPreferencesKey("blocked")
    private val lastCheckKey = longPreferencesKey("last_reply_check")
    private val unreadKey = intPreferencesKey("unread_replies")

    override val blocked: Flow<Set<String>> = store.data.map { it[blockedKey].orEmpty() }
    override val unread: Flow<Int> = store.data.map { it[unreadKey] ?: 0 }

    override suspend fun rulesAgreed(): Boolean = store.data.first()[rulesKey] == true
    override suspend fun setRulesAgreed(agreed: Boolean) {
        store.edit { it[rulesKey] = agreed }
    }

    override suspend fun nickname(): String? = store.data.first()[nickKey]
    override suspend fun setNickname(nickname: String?) {
        store.edit { if (nickname == null) it.remove(nickKey) else it[nickKey] = nickname }
    }

    override suspend fun block(uid: String) {
        store.edit { it[blockedKey] = it[blockedKey].orEmpty() + uid }
    }

    override suspend fun unblockAll() {
        store.edit { it.remove(blockedKey) }
    }

    override suspend fun lastReplyCheck(): Instant? = store.data.first()[lastCheckKey]?.let(Instant::ofEpochMilli)
    override suspend fun setLastReplyCheck(at: Instant) {
        store.edit { it[lastCheckKey] = at.toEpochMilli() }
    }

    override suspend fun setUnread(count: Int) {
        store.edit { it[unreadKey] = count.coerceAtLeast(0) }
    }

    override suspend fun clearAccount() {
        store.edit {
            it.remove(rulesKey)
            it.remove(nickKey)
            it.remove(lastCheckKey)
            it.remove(unreadKey)
        }
    }
}
