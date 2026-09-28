package com.readyport.vault

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

/** M2 완료 기준: 암호화 저장. 앱은 Keystore 키, 테스트는 같은 AES-GCM 코드에 메모리 키를 넣는다 */
class WalletRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmCipher { key }

    // 가짜 여권 (ICAO 9303 표본 가상 국가)
    private val passport = PassportRecord(
        surname = "ERIKSSON", givenNames = "ANNA MARIA", documentNumber = "L898902C3",
        nationality = "UTO", issuingState = "UTO", birthDate = "1974-08-12", sex = "F",
        expiryDate = "2036-04-15", source = "mrz", mrzVerified = true, savedAt = "2026-09-28T10:00:00",
    )

    private fun repo(file: java.io.File = tmp.root.resolve("vault/vault.bin")) =
        WalletRepository(cipher, file, Dispatchers.Unconfined)

    @Test
    fun startsLockedAndEmpty() = runBlocking {
        val r = repo()
        assertEquals(WalletState.Locked(hasData = false), r.state.value)
        assertEquals(WalletRepository.SaveResult.Locked, r.update { it.copy(passport = passport) })
        val unlocked = r.unlock() as WalletState.Unlocked
        assertNull(unlocked.contents.passport)
    }

    @Test
    fun savesEncryptedAndReadsBack() = runBlocking {
        val file = tmp.root.resolve("vault/vault.bin")
        val r = repo(file)
        r.unlock()
        assertEquals(WalletRepository.SaveResult.Saved, r.update { it.copy(passport = passport) })

        // 파일에는 여권번호·이름이 평문으로 없다
        val raw = file.readBytes().decodeToString(throwOnInvalidSequence = false)
        assertFalse(raw.contains("L898902C3"))
        assertFalse(raw.contains("ERIKSSON"))

        // 새로 열면 잠겨 있고, 풀면 같은 값
        val again = repo(file)
        assertEquals(WalletState.Locked(hasData = true), again.state.value)
        val contents = (again.unlock() as WalletState.Unlocked).contents
        assertEquals(passport, contents.passport)
    }

    @Test
    fun lockClearsMemory() = runBlocking {
        val r = repo()
        r.unlock()
        r.update { it.copy(passport = passport) }
        r.lock()
        assertNull(r.contentsOrNull)
        assertEquals(WalletState.Locked(hasData = true), r.state.value)
    }

    @Test
    fun tamperedFileIsRejected() = runBlocking {
        val file = tmp.root.resolve("vault/vault.bin")
        val r = repo(file)
        r.unlock()
        r.update { it.copy(passport = passport) }
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        val state = repo(file).unlock()
        assertEquals(WalletState.Failed(WalletState.Failed.Reason.Corrupted), state)
    }

    @Test
    fun wrongKeyCannotRead() = runBlocking {
        val file = tmp.root.resolve("vault/vault.bin")
        repo(file).apply { unlock(); update { it.copy(passport = passport) } }
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val other = WalletRepository(AesGcmCipher { otherKey }, file, Dispatchers.Unconfined)
        assertTrue(other.unlock() is WalletState.Failed)
    }

    @Test
    fun wipeRemovesEverything() = runBlocking {
        val file = tmp.root.resolve("vault/vault.bin")
        val r = repo(file)
        r.unlock()
        r.update { it.copy(passport = passport) }
        r.wipe()
        assertFalse(file.exists())
        assertEquals(WalletState.Locked(hasData = false), r.state.value)
    }

    @Test
    fun toStringNeverLeaksPersonalData() {
        val c = VaultContents(passport = passport)
        assertFalse(c.toString().contains("L898902C3"))
        assertFalse(passport.toString().contains("ERIKSSON"))
    }
}
