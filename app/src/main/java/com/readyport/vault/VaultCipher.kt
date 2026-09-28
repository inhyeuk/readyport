package com.readyport.vault

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface VaultCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * AES-256-GCM. 앱에서는 Android Keystore 키([KeystoreKeys])를, 테스트에서는 메모리 키를 넣는다.
 * 저장 형식: [버전 1바이트][IV 길이 1바이트][IV][암호문+태그]
 */
class AesGcmCipher(private val key: () -> SecretKey) : VaultCipher {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Keystore 키는 IV를 직접 넣을 수 없다. 기기가 만든 무작위 IV를 그대로 저장한다
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return ByteBuffer.allocate(2 + iv.size + body.size)
            .put(FORMAT_VERSION).put(iv.size.toByte()).put(iv).put(body)
            .array()
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        val buf = ByteBuffer.wrap(blob)
        require(buf.get() == FORMAT_VERSION) { "unknown vault format" }
        val iv = ByteArray(buf.get().toInt()).also { buf.get(it) }
        val body = ByteArray(buf.remaining()).also { buf.get(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
    }
}
