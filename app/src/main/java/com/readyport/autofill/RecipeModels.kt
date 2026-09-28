package com.readyport.autofill

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// packs/schema/recipe.schema.json 과 1:1

@Serializable
data class Recipe(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("form_id") val formId: String,
    val version: String,
    @SerialName("last_verified") val lastVerified: String,
    @SerialName("site_version_seen") val siteVersionSeen: String,
    val sources: List<RecipeSource>,
    val source: String,
    @SerialName("official_url_patterns") val officialUrlPatterns: List<String>,
    @SerialName("start_url") val startUrl: String,
    @SerialName("submitted_url_contains") val submittedUrlContains: String? = null,
    @SerialName("labels_reviewed") val labelsReviewed: Boolean = false,
    val steps: List<RecipeStep>,
    val checkpoints: List<Checkpoint>,
    val options: Map<String, List<RecipeOption>> = emptyMap(),
) {
    val fields: List<RecipeField> get() = steps.flatMap { it.fields }
}

@Serializable data class RecipeSource(val id: String, val name: String, val url: String)

@Serializable
data class RecipeStep(
    val id: String,
    @SerialName("title_ko") val titleKo: String,
    val probe: String? = null,
    @SerialName("note_ko") val noteKo: String? = null,
    val fields: List<RecipeField> = emptyList(),
)

@Serializable
data class RecipeField(
    val key: String,
    val selector: String? = null,
    /** "text" = 앱이 채움, "assist" = 값만 보여 주고 사람이 입력 */
    val widget: String,
    val transform: String? = null,
    val labels: FieldLabels,
    /** passport / booking / profile / user_choice / user_input */
    val source: String,
    /** bulk = 서류에서 온 값(묶어서 확인), individual = 고르거나 적는 값 */
    val confirm: String,
    val required: Boolean = false,
    @SerialName("options_ref") val optionsRef: String? = null,
    @SerialName("hint_ko") val hintKo: String? = null,
    /** 확인 화면에 미리 넣는 제안 값 (사람이 고칠 수 있음) */
    @SerialName("default_value") val defaultValue: String? = null,
    /** assist 칸: 사이트에서 골라야 할 선택지 글자 (실기기에서 확인한 것) */
    @SerialName("site_value") val siteValue: String? = null,
)

@Serializable data class FieldLabels(val ko: String, val en: String, val local: String? = null)

@Serializable
data class Checkpoint(val id: String, val kind: String, val selector: String? = null, val ko: String)

@Serializable
data class RecipeOption(
    val value: String,
    val ko: String,
    val en: String,
    val local: String? = null,
    /** 공식 사이트 선택지 글자 그대로 */
    val site: String? = null,
)

/** 레시피를 실행해도 되는 주소인지 (PRD 7.5: official_url_patterns 의 HTTPS 페이지에서만) */
object UrlPolicy {
    fun matches(url: String?, patterns: List<String>): Boolean {
        if (url == null) return false
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (u.scheme != "https" || u.host == null || u.userInfo != null) return false
        val path = u.rawPath.orEmpty().ifEmpty { "/" }
        return patterns.any { pattern ->
            val p = runCatching { java.net.URI(pattern.removeSuffix("*")) }.getOrNull() ?: return@any false
            val prefix = p.rawPath.orEmpty().ifEmpty { "/" }
            p.scheme == "https" && p.host.equals(u.host, ignoreCase = true) && (u.port == -1 || u.port == 443) &&
                (if (pattern.endsWith("*")) path.startsWith(prefix) else path == prefix)
        }
    }

    /** WebView 안에서 이동해도 되는 곳: 공식 사이트와 같은 호스트만. 나머지는 밖의 브라우저로 */
    fun allowedNavigation(url: String?, patterns: List<String>): Boolean {
        if (url == null) return false
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (u.scheme != "https") return false
        return patterns.mapNotNull { runCatching { java.net.URI(it.removeSuffix("*")).host }.getOrNull() }
            .any { it.equals(u.host, ignoreCase = true) }
    }
}
