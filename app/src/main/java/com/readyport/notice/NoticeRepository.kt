package com.readyport.notice

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.firebase.firestore.FirebaseFirestore
import com.readyport.doc.ocr.await
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDate

/** 서명된 공지 한 벌 (Firestore notices/current 문서, 기기 안 사본 모두 이 모양) */
data class SignedNotices(val payload: String, val sig: String)

/** 서버에서 공지 받기 (테스트에서는 가짜로 바꾼다) */
fun interface NoticeRemote {
    suspend fun fetch(): SignedNotices?
}

/** Firestore notices/current (공개 읽기 전용, 규칙: get 만). 쓰기는 GitHub Actions(notices.yml)만 */
class FirestoreNoticeRemote : NoticeRemote {
    override suspend fun fetch(): SignedNotices? {
        val doc = FirebaseFirestore.getInstance().collection("notices").document("current").get().await()
        val payload = doc.getString("payload") ?: return null
        val sig = doc.getString("sig") ?: return null
        return SignedNotices(payload, sig)
    }
}

/**
 * 공지 받기·보관. 인터넷이 되면 받아서(시간 제한) 서명을 확인하고 기기 안 사본(noBackupFilesDir/notices/)을 바꾼다.
 * 인터넷이 없으면 사본을 쓴다 — 읽을 때마다 서명을 다시 확인한다(기기 안 변조 대비). 사본보다 예전 서명본은 받지 않는다.
 */
class NoticeRepository(
    private val remote: NoticeRemote,
    private val dir: File,
    private val parser: NoticeParser,
    private val io: CoroutineDispatcher,
) {
    private val lock = Mutex()
    private val payloadFile get() = File(dir, "notices.json")
    private val sigFile get() = File(dir, "notices.json.sig")

    /** 기기 안 사본 (없거나 서명이 틀리면 null) */
    suspend fun cached(): NoticeDoc? = withContext(io) {
        runCatching { parser.parse(payloadFile.readText(), sigFile.readText()) }.getOrNull()
    }

    /** 받을 수 있으면 새로 받고, 아니면 사본. [timeoutMs] 안에 못 받으면 사본으로 (앱 시작을 붙잡지 않게) */
    suspend fun refresh(timeoutMs: Long): NoticeDoc? = lock.withLock {
        val local = cached()
        val signed = withTimeoutOrNull(timeoutMs) { runCatching { remote.fetch() }.getOrNull() }
        val fresh = signed?.let { parser.parse(it.payload, it.sig) }
        if (fresh == null || (local != null && fresh.generatedAt.isBefore(local.generatedAt))) return@withLock local
        if (local == null || fresh.generatedAt.isAfter(local.generatedAt)) {
            withContext(io) {
                runCatching {
                    dir.mkdirs()
                    // 서명을 먼저 지우고 본문 → 서명 순으로 쓴다(중간에 끊기면 둘이 맞지 않아 읽을 때 버려진다)
                    sigFile.delete()
                    payloadFile.writeText(signed.payload)
                    sigFile.writeText(signed.sig)
                }
            }
        }
        fresh
    }
}

/**
 * '다시 보지 않기'·'오늘 하루 보지 않기'·돌아가며 보이기 기록 (DataStore `notices`, 백업 제외 규칙 그대로).
 * 공지 id@version 글자와 날짜만 — 개인정보 없음, 서버로 보내지 않는다.
 */
class NoticeStore(private val store: DataStore<Preferences>) {
    private val dismissedKey = stringSetPreferencesKey("dismissed")
    private val snoozedKey = stringSetPreferencesKey("snoozed")
    private val roundKey = stringSetPreferencesKey("round")

    suspend fun marks(): NoticeMarks {
        val prefs = store.data.first()
        return NoticeMarks(
            dismissed = prefs[dismissedKey].orEmpty(),
            snoozed = prefs[snoozedKey].orEmpty().mapNotNull { entry ->
                val key = entry.substringBeforeLast('|', "")
                val day = runCatching { LocalDate.parse(entry.substringAfterLast('|')) }.getOrNull()
                if (key.isEmpty() || day == null) null else key to day
            }.toMap(),
            round = prefs[roundKey].orEmpty(),
        )
    }

    /** 대화상자를 닫은 결과를 적는다 ([NoticeSelector.after]) */
    suspend fun record(shown: Notice, choice: NoticeChoice, today: LocalDate, others: List<Notice>) {
        val next = NoticeSelector.after(marks(), shown, choice, today, others)
        store.edit {
            it[dismissedKey] = next.dismissed
            it[snoozedKey] = next.snoozed.map { (k, d) -> "$k|$d" }.toSet()
            it[roundKey] = next.round
        }
    }
}
