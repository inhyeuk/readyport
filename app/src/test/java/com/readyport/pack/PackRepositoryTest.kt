package com.readyport.pack

import com.google.crypto.tink.subtle.Ed25519Sign
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
import java.util.Base64

/** M3 완료 기준: 서명 불일치 팩 거부, 인터넷 없이 내장 팩으로 동작 */
class PackRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val keys = Ed25519Sign.KeyPair.newKeyPair()
    private val otherKeys = Ed25519Sign.KeyPair.newKeyPair()
    private val verifier = PackVerifier(mapOf("test-1" to keys.publicKey))

    private fun sign(data: ByteArray, kp: Ed25519Sign.KeyPair = keys, kid: String = "test-1"): ByteArray {
        val sig = Base64.getEncoder().encodeToString(Ed25519Sign(kp.privateKey).sign(data))
        return """{"kid":"$kid","alg":"Ed25519","sig":"$sig"}""".encodeToByteArray()
    }

    private fun pack(version: String, schema: Int = 1, country: String = "TH", title: String = "기본") = """
        {"schema_version":$schema,"country":"$country","version":"$version","last_verified":"2026-09-27",
         "names":{"ko":"태국","en":"Thailand","local":"ประเทศไทย"},
         "sources":[{"id":"s","name":"테스트 출처","url":"https://example.org"}],
         "sections":[{"id":"basic","title_ko":"$title","body_ko":["본문"],"source":"s","last_verified":"2026-09-27"}],
         "future_field_from_newer_app":{"ignored":true}}
    """.trimIndent().encodeToByteArray()

    private class Files(val map: MutableMap<String, ByteArray> = mutableMapOf()) : BundledPacks, PackRemote {
        var offline = false
        var fetches = 0
        override fun read(path: String) = map[path]
        override suspend fun fetch(path: String): ByteArray {
            fetches++
            if (offline) throw IOException("offline")
            return map[path] ?: throw IOException("404")
        }
    }

    private val bundled = Files()
    private val server = Files()

    private fun repo() = PackRepository(bundled, tmp.root.resolve("packs"), server, verifier, Dispatchers.Unconfined)

    private fun bundle(files: Files, data: ByteArray, sig: ByteArray = sign(data)) {
        files.map["TH/pack.json"] = data
        files.map["TH/pack.json.sig"] = sig
    }

    @Test
    fun verifierAcceptsValidAndRejectsOthers() {
        val data = pack("2026.09.27-1")
        assertEquals(PackVerifier.Result.Valid, verifier.verify(data, sign(data)))
        val tampered = data.copyOf().also { it[10] = (it[10] + 1).toByte() }
        assertTrue(verifier.verify(tampered, sign(data)) is PackVerifier.Result.Invalid)
        assertTrue(verifier.verify(data, sign(data, otherKeys)) is PackVerifier.Result.Invalid)
        assertTrue(verifier.verify(data, sign(data, kid = "unknown")) is PackVerifier.Result.Invalid)
        assertTrue(verifier.verify(data, "garbage".encodeToByteArray()) is PackVerifier.Result.Invalid)
    }

    @Test
    fun bundledPackWorksWithoutNetwork() = runBlocking {
        bundle(bundled, pack("2026.09.27-1"))
        server.offline = true
        val loaded = repo().pack("TH")
        assertNotNull(loaded)
        assertEquals(PackOrigin.Bundled, loaded!!.origin)
        assertEquals("태국", loaded.value.names.ko)
        assertEquals(0, server.fetches) // 읽기는 네트워크를 쓰지 않는다
    }

    @Test
    fun bundledPackWithBadSignatureIsIgnored() = runBlocking {
        bundle(bundled, pack("2026.09.27-1"), sig = sign(pack("2026.09.27-1"), otherKeys))
        assertNull(repo().pack("TH"))
    }

    @Test
    fun newerSignedPackIsDownloadedAndPreferred() = runBlocking {
        bundle(bundled, pack("2026.09.27-1"))
        bundle(server, pack("2026.10.01-1", title = "새 안내"))
        val r = repo()
        assertEquals(UpdateResult.Updated, r.updatePack("TH", "2026.10.01-1"))
        val loaded = r.pack("TH")!!
        assertEquals(PackOrigin.Downloaded, loaded.origin)
        assertEquals("새 안내", loaded.value.sections.first().titleKo)
        assertEquals(1, r.revision.value)
        // 다시 열어도(오프라인) 받은 본을 쓴다
        server.offline = true
        assertEquals("2026.10.01-1", repo().pack("TH")!!.version)
    }

    @Test
    fun badSignatureDownloadIsRejectedAndOldPackKept() = runBlocking {
        bundle(bundled, pack("2026.09.27-1"))
        val evil = pack("2026.10.01-1", title = "변조")
        bundle(server, evil, sig = sign(evil, otherKeys))
        val r = repo()
        assertEquals(UpdateResult.SignatureInvalid, r.updatePack("TH", "2026.10.01-1"))
        assertEquals("2026.09.27-1", r.pack("TH")!!.version)
        assertTrue(!tmp.root.resolve("packs/TH/pack.json").exists())
    }

    @Test
    fun unknownSchemaIsNotApplied() = runBlocking {
        bundle(bundled, pack("2026.09.27-1"))
        bundle(server, pack("2026.10.01-1", schema = 2))
        val r = repo()
        assertEquals(UpdateResult.UnsupportedSchema, r.updatePack("TH", "2026.10.01-1"))
        assertEquals("2026.09.27-1", r.pack("TH")!!.version)
    }

    @Test
    fun wrongCountryOrOlderVersionIsRejected() = runBlocking {
        bundle(bundled, pack("2026.09.27-1"))
        bundle(server, pack("2026.10.01-1", country = "JP"))
        assertEquals(UpdateResult.Malformed, repo().updatePack("TH", "2026.10.01-1"))
        bundle(server, pack("2026.09.01-1"))
        assertEquals(UpdateResult.UpToDate, repo().updatePack("TH", "2026.09.01-1"))
    }

    @Test
    fun networkErrorKeepsWorking() = runBlocking {
        bundle(bundled, pack("2026.09.27-1"))
        server.offline = true
        val r = repo()
        assertEquals(UpdateResult.NetworkError, r.updatePack("TH", "2026.10.01-1"))
        assertEquals("2026.09.27-1", r.pack("TH")!!.version)
    }

    @Test
    fun tamperedLocalFileFallsBackToBundled() = runBlocking {
        bundle(bundled, pack("2026.09.27-1"))
        bundle(server, pack("2026.10.01-1"))
        repo().updatePack("TH", "2026.10.01-1")
        val f = tmp.root.resolve("packs/TH/pack.json")
        f.writeBytes(f.readBytes().decodeToString().replace("새 안내", "변조됨").replace("기본", "변조됨").encodeToByteArray())
        val loaded = repo().pack("TH")!!
        assertEquals(PackOrigin.Bundled, loaded.origin)
    }

    @Test
    fun versionOrdering() {
        assertTrue(PackVersion.compare("2026.10.01-1", "2026.09.30-9") > 0)
        assertTrue(PackVersion.compare("2026.10.01-10", "2026.10.01-9") > 0)
        assertTrue(PackVersion.isNewer("2026.10.01-1", null))
        assertTrue(PackVersion.compare("garbage", "2026.01.01-1") < 0)
    }
}
