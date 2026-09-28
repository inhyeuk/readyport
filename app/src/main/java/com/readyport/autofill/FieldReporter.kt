package com.readyport.autofill

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 익명 실패 리포트 (ARCHITECTURE 9.4 field_reports). 개인정보를 담지 않는다:
 * 양식·레시피 버전·단계·오류 코드·앱 버전·시각·사이트 버전만.
 * M4에서는 기기에 모아 두고, M9에서 Firestore(생성만 허용 규칙)로 보낸다.
 */
@Serializable
data class FieldReport(
    val formId: String,
    val packVersion: String,
    val stepId: String,
    /** selector_missing / site_version_changed / engine_error / manual_mode_chosen / kill_switch */
    val errorCode: String,
    val appVersion: String,
    val ts: Long,
    val siteVersion: String? = null,
)

fun interface FieldReporter {
    fun report(r: FieldReport)
}

/** 대기열 파일에 한 줄씩(JSON Lines). 최대 200줄만 유지 */
class QueuedFieldReporter(private val file: File, private val onQueued: () -> Unit = {}) : FieldReporter {
    private val json = Json

    @Synchronized
    override fun report(r: FieldReport) {
        runCatching {
            file.parentFile?.mkdirs()
            val lines = (if (file.exists()) file.readLines() else emptyList()) + json.encodeToString(FieldReport.serializer(), r)
            file.writeText(lines.takeLast(200).joinToString("\n") + "\n")
        }
        onQueued()
    }

    /** 보낸 앞쪽 [count]개를 지운다 (보내는 사이 새로 쌓인 리포트는 남긴다) */
    @Synchronized
    fun drop(count: Int) {
        runCatching {
            if (!file.exists()) return
            val rest = file.readLines().filter { it.isNotBlank() }.drop(count)
            if (rest.isEmpty()) file.delete() else file.writeText(rest.joinToString("\n") + "\n")
        }
    }

    fun pending(): List<FieldReport> = runCatching {
        file.readLines().filter { it.isNotBlank() }.map { json.decodeFromString(FieldReport.serializer(), it) }
    }.getOrDefault(emptyList())
}
