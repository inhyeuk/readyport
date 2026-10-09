package com.readyport.attractions

import com.readyport.pack.BundledPacks
import com.readyport.pack.PackNotFoundException
import com.readyport.pack.PackRemote
import com.readyport.pack.PackVerifier
import com.readyport.pack.PackVersion
import com.readyport.pack.UpdateResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** 관광지 파일을 어디서 읽었는지 */
enum class AttractionsOrigin { Bundled, Downloaded, Sample }

data class LoadedAttractions(val doc: AttractionsDoc, val version: String, val origin: AttractionsOrigin)

/**
 * 관광지 저장소 (SPEC_v5 §4.1·§6.3) — 국가 팩과 같은 길: 내장본(assets/packs/<CC>/attractions.json)과 받은 본
 * (noBackupFilesDir/packs/<CC>/attractions.json, Firebase Hosting packs/<CC>/attractions.json에서 받음) 가운데
 * **관광지 키(rp-att-*)로 서명이 맞고, 스키마를 알고, published이고, 나라가 맞는** 가장 새 버전을 쓴다. 네트워크를 쓰지 않는다(오프라인).
 * - 서명본이 하나도 없으면 [fallback](debug 빌드의 샘플, release는 언제나 null)을 쓴다 — sample=true 문서만.
 * - 디코딩한 모델은 최근 3개국만 들고 있다(LRU, 메모리).
 * - [revisionOf]: 나라마다 따로 올라간다. 국가 팩 [com.readyport.pack.PackRepository.revision]과 섞이지 않는다.
 */
class AttractionsRepository(
    private val bundled: BundledPacks,
    private val localDir: File,
    private val remote: PackRemote,
    private val verifier: PackVerifier,
    private val fallback: (String) -> ByteArray?,
    private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val held = object : LinkedHashMap<String, Held>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Held>?): Boolean = size > MAX_HELD
    }
    private val revisions = ConcurrentHashMap<String, MutableStateFlow<Int>>()

    private class Held(val loaded: LoadedAttractions?, val catalog: AttractionsCatalog?)

    fun revisionOf(country: String): StateFlow<Int> = revisions.getOrPut(country) { MutableStateFlow(0) }.asStateFlow()

    /** 지금 들고 있는 나라 수 (테스트: 넓혀 찾기 뒤에도 3개 이하) */
    fun heldCount(): Int = held.size

    suspend fun load(country: String): LoadedAttractions? = entry(country).loaded

    suspend fun catalog(country: String): AttractionsCatalog? = entry(country).catalog

    private suspend fun entry(country: String): Held = mutex.withLock {
        held[country]?.let { return it }
        val loaded = withContext(io) { readBest(country) }
        val catalog = loaded?.let { AttractionsMapper.map(it.doc) }
        Held(loaded, catalog).also { held[country] = it }
    }

    private fun readBest(country: String): LoadedAttractions? {
        val path = path(country)
        val local = readLocal(path)?.let { (data, sig) -> decodeSigned(country, data, sig, AttractionsOrigin.Downloaded) }
        val builtIn = bundled.read(path)?.let { data ->
            bundled.read("$path.sig")?.let { sig -> decodeSigned(country, data, sig, AttractionsOrigin.Bundled) }
        }
        val best = listOfNotNull(local, builtIn).maxWithOrNull { a, b -> PackVersion.compare(a.version, b.version) }
        if (best != null) return best
        // 서명본이 없을 때만 debug 샘플 (release 소스셋의 AttractionsFallback은 언제나 null)
        return fallback(country)?.let { decodeSample(country, it) }
    }

    /**
     * 받은 파일 적용. 서명(관광지 키)·스키마·doc_type·published·나라·버전을 모두 통과해야 저장한다.
     * 받은 version이 Remote Config 값보다 낮으면(Hosting 캐시) 저장은 하되 [UpdateResult.Stale].
     */
    suspend fun update(country: String, remoteVersion: String): UpdateResult {
        val current = load(country)?.takeIf { it.origin != AttractionsOrigin.Sample }?.version
        if (!PackVersion.isNewer(remoteVersion, current)) return UpdateResult.UpToDate
        val path = path(country)
        val (data, sig) = try {
            remote.fetch(path) to remote.fetch("$path.sig")
        } catch (e: PackNotFoundException) {
            return UpdateResult.NotFound
        } catch (e: IOException) {
            return UpdateResult.NetworkError
        }
        if (verifier.verify(data, sig) !is PackVerifier.Result.Valid) return UpdateResult.SignatureInvalid
        val doc = parse(data) ?: return UpdateResult.Malformed
        if (doc.schemaVersion > SUPPORTED_ATTRACTIONS_SCHEMA) return UpdateResult.UnsupportedSchema
        if (!acceptable(country, doc)) return UpdateResult.Malformed
        if (!PackVersion.isNewer(doc.version, current)) {
            return if (PackVersion.compare(doc.version, remoteVersion) < 0) UpdateResult.Stale else UpdateResult.UpToDate
        }
        mutex.withLock {
            withContext(io) { writeLocal(path, data, sig) }
            held.remove(country)
        }
        revisions.getOrPut(country) { MutableStateFlow(0) }.value++
        return if (PackVersion.compare(doc.version, remoteVersion) < 0) UpdateResult.Stale else UpdateResult.Updated
    }

    private fun decodeSigned(country: String, data: ByteArray, sig: ByteArray, origin: AttractionsOrigin): LoadedAttractions? {
        if (verifier.verify(data, sig) !is PackVerifier.Result.Valid) return null
        val doc = parse(data) ?: return null
        if (doc.schemaVersion > SUPPORTED_ATTRACTIONS_SCHEMA || !acceptable(country, doc)) return null
        return LoadedAttractions(doc, doc.version, origin)
    }

    private fun decodeSample(country: String, data: ByteArray): LoadedAttractions? {
        val doc = parse(data) ?: return null
        if (!doc.sample || doc.country != country || doc.docType != DOC_TYPE) return null
        return LoadedAttractions(doc, doc.version, AttractionsOrigin.Sample)
    }

    /** 서명본으로 쓸 수 있는 문서: doc_type·published·나라·sample 아님 (draft·다른 나라·샘플은 무시) */
    private fun acceptable(country: String, doc: AttractionsDoc): Boolean =
        doc.docType == DOC_TYPE && doc.release == "published" && doc.country == country && !doc.sample

    private fun parse(data: ByteArray): AttractionsDoc? =
        runCatching { AttractionsJson.decodeFromString(AttractionsDoc.serializer(), data.decodeToString()) }.getOrNull()

    private fun readLocal(path: String): Pair<ByteArray, ByteArray>? {
        val f = File(localDir, path)
        val s = File(localDir, "$path.sig")
        return if (f.isFile && s.isFile) f.readBytes() to s.readBytes() else null
    }

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
        const val DOC_TYPE = "attractions"
        private const val MAX_HELD = 3
        fun path(country: String) = "$country/attractions.json"
    }
}
