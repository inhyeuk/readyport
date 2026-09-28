package com.readyport.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** 지갑 잠금 해제: 지문·얼굴 또는 화면 잠금(PIN·패턴) (PRD 5.5, 7.3) */
object DeviceAuth {

    enum class Result { Success, Canceled, NoLockScreen, Error }

    // API 28~29는 STRONG|DEVICE_CREDENTIAL 조합을 지원하지 않는다 (androidx.biometric 문서)
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** 화면 잠금이 설정돼 있어 인증할 수 있는지 */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (Result) -> Unit,
    ) {
        if (!isAvailable(activity)) {
            onResult(Result.NoLockScreen)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onResult(Result.Success)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> Result.Canceled
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> Result.NoLockScreen
                        else -> Result.Error
                    },
                )
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}
