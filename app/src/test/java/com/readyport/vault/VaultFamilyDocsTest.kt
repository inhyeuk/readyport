package com.readyport.vault

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

/** M6: 입국 서류 그림 암호화 저장, 동행자, 여행 후 여권 정보만 파기 */
class VaultFamilyDocsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun repo() = WalletRepository(AesGcmCipher { key }, tmp.root.resolve("vault/vault.bin"), Dispatchers.Unconfined)

    // ICAO 9303 표본(가상 국가)
    private val passport = PassportRecord("ERIKSSON", "ANNA MARIA", "L898902C3", "UTO", "UTO", "1974-08-12", "F", "2036-04-15", "mrz", true, "x")

    @Test
    fun blobsAreEncryptedAndReadable() = runBlocking {
        val r = repo()
        val png = ByteArray(2048) { (it % 251).toByte() } + "QR-SECRET-CONFIRMATION".encodeToByteArray()
        val id = r.writeBlob(png)!!
        val onDisk = tmp.root.resolve("vault/blobs/$id.bin").readBytes()
        assertFalse(String(onDisk, Charsets.ISO_8859_1).contains("QR-SECRET-CONFIRMATION"))
        assertArrayEquals(png, r.readBlob(id))
        // 경로 조작 이름은 거부
        assertNull(r.readBlob("../vault"))
        r.deleteBlob(id)
        assertNull(r.readBlob(id))
    }

    @Test
    fun destroyKeepsDocsAndBookingsButDropsPassports() = runBlocking {
        val r = repo()
        r.unlock()
        val blob = r.writeBlob(byteArrayOf(1, 2, 3))!!
        r.update {
            it.copy(
                passport = passport,
                companions = listOf(TravelCompanion("c1", "첫째", passport, consentConfirmed = true, addedAt = "x")),
                forms = mapOf("TH_TDAC" to FormRecord("TH_TDAC", mapOf("profile.phone" to "1012345678"), "submitted", "x")),
                bookings = listOf(BookingRecord(id = "b", kind = "flight", title = "t", savedAt = "x")),
                entryDocs = listOf(EntryDoc("d", "TH_TDAC", "self", blob, source = "capture", savedAt = "x")),
            )
        }
        r.update { it.withoutPassportInfo() }
        val c = (r.unlock() as WalletState.Unlocked).contents
        assertNull(c.passport)
        assertNull(c.companions.single().passport)
        assertTrue(c.forms.isEmpty())
        assertEquals(1, c.bookings.size)
        assertEquals(1, c.entryDocs.size)
        assertNotNull(r.readBlob(blob))
    }

    @Test
    fun wipeRemovesBlobs() = runBlocking {
        val r = repo()
        val id = r.writeBlob(byteArrayOf(9))!!
        r.wipe()
        assertFalse(tmp.root.resolve("vault/blobs/$id.bin").exists())
    }
}
