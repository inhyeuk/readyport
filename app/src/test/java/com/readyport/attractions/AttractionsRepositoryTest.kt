package com.readyport.attractions

import com.readyport.pack.BundledPacks
import com.readyport.pack.DocKind
import com.readyport.pack.PackKeys
import com.readyport.pack.PackNotFoundException
import com.readyport.pack.PackRemote
import com.readyport.pack.PackVerifier
import com.readyport.pack.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** 관광지 저장소: 관광지 키로만 검증, draft·샘플·다른 나라 무시, debug 샘플은 서명본이 없을 때만, 받기 결과 (SPEC_v5 §4.1·§12) */
class AttractionsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val attVerifier = PackVerifier(mapOf("rp-att-test-1" to AttTestData.attKeys.publicKey))

    private class Files(val map: MutableMap<String, ByteArray> = mutableMapOf()) : BundledPacks, PackRemote {
        var missing = false
        override fun read(path: String) = map[path]
        override suspend fun fetch(path: String): ByteArray {
            if (missing) throw PackNotFoundException("404")
            return map[path] ?: throw IOException("offline")
        }
    }

    private val bundled = Files()
    private val server = Files()
    private var fallback: (String) -> ByteArray? = { null }

    private fun repo() = AttractionsRepository(bundled, tmp.root.resolve("packs"), server, attVerifier, { fallback(it) }, Dispatchers.Unconfined)

    private fun put(files: Files, doc: AttractionsDoc, kid: String = "rp-att-test-1", keys: com.google.crypto.tink.subtle.Ed25519Sign.KeyPair = AttTestData.attKeys) {
        val data = AttTestData.bytes(doc)
        files.map["${doc.country}/attractions.json"] = data
        files.map["${doc.country}/attractions.json.sig"] = AttTestData.sign(data, keys, kid)
    }

    @Test fun loadsSignedBundled() = runBlocking {
        put(bundled, AttTestData.basic())
        val loaded = repo().load("XX")!!
        assertEquals(AttractionsOrigin.Bundled, loaded.origin)
        assertEquals(5, repo().catalog("XX")!!.attractions.size)
    }

    @Test fun packKeyIsNotTrustedForAttractions() = runBlocking {
        put(bundled, AttTestData.basic(), kid = "rp-2026-1", keys = AttTestData.packKeys)
        assertNull(repo().load("XX"))
    }

    @Test fun appKeyMapsAreSeparate() {
        val att = PackKeys.TRUSTED_FOR.getValue(DocKind.Attractions)
        val pack = PackKeys.TRUSTED_FOR.getValue(DocKind.Pack)
        assertTrue(att.keys.all { it.startsWith("rp-att-") })
        assertTrue(pack.keys.none { it.startsWith("rp-att-") })
        assertTrue(att.keys.none { it in pack.keys })
    }

    @Test fun draftSampleWrongCountryAndNewSchemaIgnored() = runBlocking {
        put(bundled, AttTestData.basic().copy(release = "draft"))
        assertNull(repo().load("XX"))
        put(bundled, AttTestData.basic().copy(sample = true))
        assertNull(repo().load("XX"))
        put(bundled, AttTestData.basic().copy(schemaVersion = SUPPORTED_ATTRACTIONS_SCHEMA + 1))
        assertNull(repo().load("XX"))
        val other = AttTestData.basic().copy(country = "YY")
        val data = AttTestData.bytes(other)
        bundled.map["XX/attractions.json"] = data
        bundled.map["XX/attractions.json.sig"] = AttTestData.sign(data)
        assertNull(repo().load("XX"))
    }

    @Test fun fallbackOnlyWhenNoSignedFile() = runBlocking {
        fallback = { AttTestData.bytes(AttTestData.basic().copy(sample = true, release = "draft", version = "2026.01.01-1")) }
        assertEquals(AttractionsOrigin.Sample, repo().load("XX")!!.origin)
        put(bundled, AttTestData.basic())
        assertEquals(AttractionsOrigin.Bundled, repo().load("XX")!!.origin)
    }

    @Test fun fallbackMustBeMarkedSample() = runBlocking {
        fallback = { AttTestData.bytes(AttTestData.basic()) }
        assertNull(repo().load("XX"))
    }

    @Test fun updateSavesAndBumpsRevision() = runBlocking {
        put(bundled, AttTestData.basic().copy(version = "2026.10.01-1"))
        put(server, AttTestData.basic().copy(version = "2026.10.09-1"))
        val r = repo()
        assertEquals("2026.10.01-1", r.load("XX")!!.version)
        assertEquals(UpdateResult.Updated, r.update("XX", "2026.10.09-1"))
        assertEquals(1, r.revisionOf("XX").value)
        assertEquals(AttractionsOrigin.Downloaded, r.load("XX")!!.origin)
        assertEquals(UpdateResult.UpToDate, r.update("XX", "2026.10.09-1"))
    }

    @Test fun staleWhenServerOlderThanRemoteConfig() = runBlocking {
        put(bundled, AttTestData.basic().copy(version = "2026.10.01-1"))
        put(server, AttTestData.basic().copy(version = "2026.10.05-1"))
        val r = repo()
        assertEquals(UpdateResult.Stale, r.update("XX", "2026.10.09-1"))
        // 받은 것이 기기 본보다 새것이면 저장은 한다
        assertEquals("2026.10.05-1", r.load("XX")!!.version)
    }

    @Test fun notFoundAndBadSignature() = runBlocking {
        server.missing = true
        assertEquals(UpdateResult.NotFound, repo().update("XX", "2026.10.09-1"))
        server.missing = false
        put(server, AttTestData.basic(), kid = "rp-att-test-1", keys = AttTestData.packKeys)
        assertEquals(UpdateResult.SignatureInvalid, repo().update("XX", "2026.10.09-1"))
        put(server, AttTestData.basic().copy(schemaVersion = 2))
        assertEquals(UpdateResult.UnsupportedSchema, repo().update("XX", "2026.10.09-1"))
    }

    @Test fun holdsAtMostThreeCountries() = runBlocking {
        val r = repo()
        for (cc in listOf("AA", "BB", "CC", "DD", "EE")) {
            put(bundled, AttTestData.basic().copy(country = cc))
            assertNotNull(r.catalog(cc))
        }
        assertTrue(r.heldCount() <= 3)
    }
}
