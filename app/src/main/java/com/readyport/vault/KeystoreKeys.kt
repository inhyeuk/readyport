package com.readyport.vault

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 보관함 키. Android Keystore 안에서 만들어지고 밖으로 나오지 않는다 (PRD 7.3).
 * 화면 잠금이 있는 기기에서는 "최근 5분 안에 지문·얼굴·화면 잠금으로 인증"해야 풀리도록 묶는다.
 */
class KeystoreKeys(private val context: Context) {

    /** 키가 사용자 인증에 묶였는지. 화면 잠금이 없던 기기에서 만든 키는 false */
    var authBound: Boolean = false
        private set

    @Volatile private var cached: SecretKey? = null

    fun getOrCreate(): SecretKey = cached ?: synchronized(this) { cached ?: load().also { cached = it } }

    private fun load(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { entry ->
            authBound = runCatching {
                val info = javax.crypto.SecretKeyFactory.getInstance(entry.secretKey.algorithm, ANDROID_KEYSTORE)
                    .getKeySpec(entry.secretKey, android.security.keystore.KeyInfo::class.java) as android.security.keystore.KeyInfo
                info.isUserAuthenticationRequired
            }.getOrDefault(false)
            return entry.secretKey
        }
        return create()
    }

    fun deleteKey() {
        cached = null
        runCatching { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun create(): SecretKey {
        val secure = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                if (secure) {
                    setUserAuthenticationRequired(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(
                            AUTH_VALIDITY_SECONDS,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
                    }
                    // 지문을 새로 등록해도 여권 정보를 잃지 않게 한다(인증 요구는 그대로)
                    setInvalidatedByBiometricEnrollment(false)
                }
            }
            .build()
        val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
        authBound = secure
        return key
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "readyport_vault_v1"
        const val AUTH_VALIDITY_SECONDS = 300
    }
}
