package com.readyport.pack

import com.readyport.autofill.Recipe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException

/** 설치 파일에 내장한 기본 팩 (assets/packs/) */
fun interface BundledPacks {
    fun read(path: String): ByteArray?
}

/** 서버(Firebase Hosting)에서 팩 파일 받기 */
fun interface PackRemote {
    @Throws(IOException::class)
    suspend fun fetch(path: String): ByteArray
}

enum class PackOrigin { Bundled, Downloaded }

data class Loaded<T>(val value: T, val version: String, val origin: PackOrigin, val sizeBytes: Int)

/**
 * 받기 결과. [NotFound] = 서버에 그 파일이 없음(HTTP 404 — 다시 해도 같다), [Stale] = 받은 버전이 Remote Config가 가리키는 버전보다 낮음
 * (Hosting 캐시 — 나중에 다시 받는다). 두 값은 관광지(SPEC_v5 §4.1)에서 더했다.
 */
enum class UpdateResult { Updated, UpToDate, NetworkError, SignatureInvalid, UnsupportedSchema, Malformed, NotFound, Stale }

/** 서버에 파일이 없다(HTTP 404). 네트워크 오류([IOException])의 한 종류라 기존 코드는 그대로 NetworkError로 다룬다 */
class PackNotFoundException(message: String) : IOException(message)

/**
 * 국가 팩 저장소 — "앱은 엔진, 정책·콘텐츠는 데이터" (ARCHITECTURE 9.2).
 * - 읽기: 내장본과 받은 본 중 **서명이 맞고 스키마를 아는** 가장 새 버전. 네트워크를 쓰지 않는다(오프라인 동작).
 * - 갱신: 받은 파일은 서명·스키마·버전을 모두 통과해야 저장한다. 하나라도 틀리면 기존 것을 그대로 쓴다.
 */
class PackRepository(
    private val bundled: BundledPacks,
    private val localDir: File,
    private val remote: PackRemote,
    private val verifier: PackVerifier,
    private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Loaded<*>?>()

    /** 팩이 바뀔 때마다 올라간다. 화면은 이 값을 보고 다시 읽는다 */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    suspend fun index(): Loaded<PackIndex>? = load(INDEX, PackIndex.serializer())

    suspend fun pack(country: String): Loaded<CountryPack>? =
        load(packPath(country), CountryPack.serializer())?.takeIf { it.value.country == country }

    suspend fun recipe(formId: String): Loaded<Recipe>? =
        load(recipePath(formId), Recipe.serializer())?.takeIf { it.value.formId == formId }

    suspend fun updateRecipe(formId: String, remoteVersion: String): UpdateResult =
        update(recipePath(formId), remoteVersion, Recipe.serializer()) { it.formId == formId }

    suspend fun updateIndex(remoteVersion: String): UpdateResult =
        update(INDEX, remoteVersion, PackIndex.serializer()) { true }

    suspend fun updatePack(country: String, remoteVersion: String): UpdateResult =
        update(packPath(country), remoteVersion, CountryPack.serializer()) { it.country == country }

    // ---------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> load(path: String, serializer: KSerializer<T>): Loaded<T>? = mutex.withLock {
        if (cache.containsKey(path)) return cache[path] as Loaded<T>?
        val result = withContext(io) {
            val local = readLocal(path)?.let { (data, sig) -> decodeVerified(data, sig, serializer, PackOrigin.Downloaded) }
            val builtIn = bundled.read(path)?.let { data ->
                bundled.read("$path.sig")?.let { sig -> decodeVerified(data, sig, serializer, PackOrigin.Bundled) }
            }
            listOfNotNull(local, builtIn).maxWithOrNull { a, b -> PackVersion.compare(a.version, b.version) }
        }
        cache[path] = result
        result
    }

    private suspend fun <T> update(
        path: String,
        remoteVersion: String,
        serializer: KSerializer<T>,
        accept: (T) -> Boolean,
    ): UpdateResult {
        val current = load(path, serializer)?.version
        if (!PackVersion.isNewer(remoteVersion, current)) return UpdateResult.UpToDate

        val (data, sig) = try {
            remote.fetch(path) to remote.fetch("$path.sig")
        } catch (e: IOException) {
            return UpdateResult.NetworkError
        }
        if (verifier.verify(data, sig) !is PackVerifier.Result.Valid) return UpdateResult.SignatureInvalid
        val schema = schemaVersion(data) ?: return UpdateResult.Malformed
        // 모르는 스키마는 적용하지 않는다. 기존 팩으로 계속 동작한다 (ARCHITECTURE 9.2)
        if (schema > SUPPORTED_SCHEMA) return UpdateResult.UnsupportedSchema
        val decoded = runCatching { json.decodeFromString(serializer, data.decodeToString()) }.getOrNull()
            ?: return UpdateResult.Malformed
        if (!accept(decoded)) return UpdateResult.Malformed
        val version = versionOf(data) ?: return UpdateResult.Malformed
        if (!PackVersion.isNewer(version, current)) return UpdateResult.UpToDate

        mutex.withLock {
            withContext(io) { writeLocal(path, data, sig) }
            cache.remove(path)
        }
        _revision.value++
        return UpdateResult.Updated
    }

    private fun <T> decodeVerified(data: ByteArray, sig: ByteArray, serializer: KSerializer<T>, origin: PackOrigin): Loaded<T>? {
        if (verifier.verify(data, sig) !is PackVerifier.Result.Valid) return null
        if ((schemaVersion(data) ?: return null) > SUPPORTED_SCHEMA) return null
        val value = runCatching { json.decodeFromString(serializer, data.decodeToString()) }.getOrNull() ?: return null
        val version = versionOf(data) ?: return null
        return Loaded(value, version, origin, data.size)
    }

    private fun schemaVersion(data: ByteArray): Int? = runCatching {
        json.parseToJsonElement(data.decodeToString()).jsonObject["schema_version"]!!.jsonPrimitive.int
    }.getOrNull()

    private fun versionOf(data: ByteArray): String? = runCatching {
        json.parseToJsonElement(data.decodeToString()).jsonObject["version"]!!.jsonPrimitive.content
    }.getOrNull()

    private fun readLocal(path: String): Pair<ByteArray, ByteArray>? {
        val f = File(localDir, path)
        val s = File(localDir, "$path.sig")
        return if (f.isFile && s.isFile) f.readBytes() to s.readBytes() else null
    }

    /** 서명 파일을 먼저, 본문을 나중에 원자적으로 바꾼다. 중간에 꺼져도 검증에서 걸러진다 */
    private fun writeLocal(path: String, data: ByteArray, sig: ByteArray) {
        val f = File(localDir, path)
        f.parentFile?.mkdirs()
        atomicWrite(File(localDir, "$path.sig"), sig)
        atomicWrite(f, data)
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    companion object {
        const val INDEX = "index.json"
        fun packPath(country: String) = "$country/pack.json"
        fun recipePath(formId: String) = "recipes/$formId.json"
    }
}
